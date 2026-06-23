package io.testreport.selenium;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Embedded HTTP server for the Selenium HTML dashboard (same UI as the Playwright viewer).
 */
public final class ReportViewerServer {

  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
  private static final String RESOURCE_PREFIX = "/testreport/";

  private final Path reportRoot;
  private final int port;
  private HttpServer server;

  private int boundPort;

  public ReportViewerServer(Path reportRoot, int port) {
    this.reportRoot = reportRoot.toAbsolutePath().normalize();
    this.port = port;
  }

  public int start() throws IOException {
    try {
      server = HttpServer.create(new InetSocketAddress(port), 0);
      server.createContext("/", new RootHandler());
      server.createContext("/api/runs", new RunsHandler());
      server.createContext("/api/info", new InfoHandler());
      server.createContext("/api/run/", new RunHandler());
      server.createContext("/attachment", new AttachmentHandler());
      server.createContext("/assets/", new AssetHandler());
      server.start();
      boundPort = port;
      return boundPort;
    } catch (IOException exception) {
      throw new IOException(
          "Port "
              + port
              + " is already in use. Stop the other viewer first or use: --port 4300",
          exception);
    }
  }

  public void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  public int getPort() {
    return boundPort > 0 ? boundPort : port;
  }

  public Path getReportRoot() {
    return reportRoot;
  }

  public long countRuns() throws IOException {
    if (!Files.isDirectory(reportRoot)) {
      return 0;
    }

    try (Stream<Path> paths = Files.list(reportRoot)) {
      return paths
          .filter(Files::isDirectory)
          .filter(path -> Files.isRegularFile(path.resolve("results.json")))
          .count();
    }
  }

  private List<String> listRunIds() throws IOException {
    if (!Files.isDirectory(reportRoot)) {
      return List.of();
    }

    try (Stream<Path> paths = Files.list(reportRoot)) {
      return paths
          .filter(Files::isDirectory)
          .map(path -> path.getFileName().toString())
          .filter(id -> Files.isRegularFile(reportRoot.resolve(id).resolve("results.json")))
          .sorted(Comparator.reverseOrder())
          .collect(Collectors.toList());
    }
  }

  private JsonObject loadRun(String runId) throws IOException {
    Path file = reportRoot.resolve(runId).resolve("results.json");

    if (!Files.isRegularFile(file)) {
      return null;
    }

    String json = Files.readString(file, StandardCharsets.UTF_8);
    JsonObject data = JsonParser.parseString(json).getAsJsonObject();
    enrichRunData(data);
    return data;
  }

  private void enrichRunData(JsonObject data) {
    if (!data.has("environment") || data.get("environment").isJsonNull()) {
      JsonObject environment = new JsonObject();
      environment.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
      environment.addProperty("browser", "");
      environment.addProperty("selenium", "");
      environment.addProperty("java", System.getProperty("java.version"));
      data.add("environment", environment);
    }

    if (!data.has("projectName") || data.get("projectName").getAsString().isBlank()) {
      String artifactId = readArtifactId(reportRoot.getParent());
      if (!artifactId.isBlank()) {
        data.addProperty("projectName", artifactId);
      }
    }
  }

  private static String readArtifactId(Path projectRoot) {
    if (projectRoot == null) {
      return "";
    }

    Path pom = projectRoot.resolve("pom.xml");
    if (!Files.isRegularFile(pom)) {
      return "";
    }

    try {
      String content = Files.readString(pom, StandardCharsets.UTF_8);
      int start = content.indexOf("<artifactId>");
      if (start < 0) {
        return "";
      }
      start += "<artifactId>".length();
      int end = content.indexOf("</artifactId>", start);
      return end > start ? content.substring(start, end).trim() : "";
    } catch (IOException exception) {
      return "";
    }
  }

  private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
    byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
    Headers headers = exchange.getResponseHeaders();
    headers.set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  private void sendBytes(HttpExchange exchange, int status, byte[] bytes, String contentType) throws IOException {
    Headers headers = exchange.getResponseHeaders();
    headers.set("Content-Type", contentType);
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  private byte[] readResource(String resourcePath) throws IOException {
    try (InputStream input = ReportViewerServer.class.getResourceAsStream(resourcePath)) {
      if (input == null) {
        throw new IOException("Missing resource: " + resourcePath);
      }
      return input.readAllBytes();
    }
  }

  private String contentTypeFor(String fileName) {
    String lower = fileName.toLowerCase();
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
    if (lower.endsWith(".webp")) return "image/webp";
    if (lower.endsWith(".webm")) return "video/webm";
    if (lower.endsWith(".mp4")) return "video/mp4";
    if (lower.endsWith(".svg")) return "image/svg+xml";
    if (lower.endsWith(".js")) return "application/javascript";
    if (lower.endsWith(".html")) return "text/html; charset=utf-8";
    return "application/octet-stream";
  }

  private final class RootHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      if (!"/".equals(exchange.getRequestURI().getPath())) {
        sendJson(exchange, 404, Map.of("error", "Not found"));
        return;
      }

      byte[] html = readResource(RESOURCE_PREFIX + "dashboard.html");
      sendBytes(exchange, 200, html, "text/html; charset=utf-8");
    }
  }

  private final class InfoHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      sendJson(
          exchange,
          200,
          Map.of(
              "reportRoot", reportRoot.toString(),
              "port", getPort(),
              "runs", countRuns()));
    }
  }

  private final class RunsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      List<Map<String, Object>> runs = new ArrayList<>();

      for (String runId : listRunIds()) {
        JsonObject data = loadRun(runId);
        Map<String, Object> summary = new HashMap<>();
        summary.put("runId", runId);

        if (data != null) {
          JsonObject totals = data.has("totals") ? data.getAsJsonObject("totals") : null;
          summary.put("tests", jsonInt(totals, "tests"));
          summary.put("passed", jsonInt(totals, "passed"));
          summary.put("failed", jsonInt(totals, "failed"));
          summary.put("skipped", jsonInt(totals, "skipped"));
          summary.put("timedOut", jsonInt(totals, "timedOut"));
          summary.put("flaky", jsonInt(totals, "flaky"));
          summary.put("startedAt", jsonString(data, "startedAt"));
          summary.put("finishedAt", jsonString(data, "finishedAt"));
          summary.put("failedTests", failedTestTitles(data));
        }

        runs.add(summary);
      }

      sendJson(exchange, 200, runs);
    }
  }

  private final class RunHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      String path = exchange.getRequestURI().getPath();
      String runId = path.substring(path.lastIndexOf('/') + 1);
      JsonObject data = loadRun(runId);

      if (data == null) {
        sendJson(exchange, 404, Map.of("error", "Run not found"));
        return;
      }

      sendJson(exchange, 200, GSON.fromJson(data, Object.class));
    }
  }

  private final class AttachmentHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      String query = exchange.getRequestURI().getRawQuery();
      String filePath = null;

      if (query != null) {
        for (String part : query.split("&")) {
          String[] pair = part.split("=", 2);
          if (pair.length == 2 && "p".equals(pair[0])) {
            filePath = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            break;
          }
        }
      }

      if (filePath == null || filePath.isBlank()) {
        sendJson(exchange, 400, Map.of("error", "Missing path"));
        return;
      }

      Path resolved = Paths.get(filePath).toAbsolutePath().normalize();
      if (!Files.isRegularFile(resolved)) {
        sendJson(exchange, 404, Map.of("error", "Attachment not found"));
        return;
      }

      byte[] bytes = Files.readAllBytes(resolved);
      sendBytes(exchange, 200, bytes, contentTypeFor(resolved.getFileName().toString()));
    }
  }

  private final class AssetHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      String path = exchange.getRequestURI().getPath();
      String assetName = path.substring("/assets/".length());
      String resourcePath = RESOURCE_PREFIX + "assets/" + assetName;

      try {
        byte[] bytes = readResource(resourcePath);
        sendBytes(exchange, 200, bytes, contentTypeFor(assetName));
      } catch (IOException exception) {
        sendJson(exchange, 404, Map.of("error", "Asset not found"));
      }
    }
  }

  private static int jsonInt(JsonObject object, String field) {
    if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
      return 0;
    }
    return object.get(field).getAsInt();
  }

  private static String jsonString(JsonObject object, String field) {
    if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
      return null;
    }
    return object.get(field).getAsString();
  }

  private static List<String> failedTestTitles(JsonObject data) {
    if (!data.has("tests") || !data.get("tests").isJsonArray()) {
      return List.of();
    }

    Set<String> titles = new LinkedHashSet<>();
    for (JsonElement element : data.getAsJsonArray("tests")) {
      if (!element.isJsonObject()) {
        continue;
      }

      JsonObject test = element.getAsJsonObject();
      String status = test.has("status") ? test.get("status").getAsString() : "";
      if ("failed".equals(status) || "timedOut".equals(status)) {
        String title = test.has("title") ? test.get("title").getAsString() : "Untitled test";
        titles.add(title);
      }
    }

    return new ArrayList<>(titles);
  }
}
