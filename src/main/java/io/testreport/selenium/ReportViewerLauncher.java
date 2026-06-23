package io.testreport.selenium;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts the HTML dashboard after a test run (same UX as Playwright opening the report).
 */
final class ReportViewerLauncher {

  private static final AtomicBoolean LAUNCHED = new AtomicBoolean(false);

  private ReportViewerLauncher() {}

  static void openAfterRun(Path reportDir, String runId, boolean failed) {
    if (!shouldOpen(failed) || !LAUNCHED.compareAndSet(false, true)) {
      return;
    }

    int port = parseInt(System.getProperty("testreport.port"), 4173);
    Path root = resolveReportDir(reportDir.toString());

    Thread launcher =
        new Thread(
            () -> {
              try {
                int viewerPort = startViewerAndWait(root, port);
                String url = "http://localhost:" + viewerPort;
                ReportViewerBrowser.open(url);

                System.out.println();
                System.out.println("Testreport dashboard: " + url);
                System.out.println("Report folder: " + root);
                System.out.println("Viewer runs in the background — close that terminal to stop it.");
                System.out.println();
              } catch (Exception exception) {
                System.err.println("Unable to open Testreport dashboard: " + exception.getMessage());
                System.err.println("Run manually: mvn exec:java  (same as: npx playwright-viewer serve)");
              }
            },
            "testreport-viewer");

    launcher.setDaemon(true);
    launcher.start();
  }

  static int startViewerAndWait(Path reportDir, int port) throws Exception {
    startDetachedViewer(reportDir, port);

    for (int attempt = 0; attempt < 40; attempt++) {
      if (isViewerServing(port, reportDir)) {
        return port;
      }
      Thread.sleep(250);
    }

    throw new IllegalStateException(
        "Viewer did not start on port " + port + ". Stop other viewers and run: mvn exec:java");
  }

  private static boolean isViewerServing(int port, Path expectedReportDir) {
    try {
      HttpURLConnection connection =
          (HttpURLConnection) new URL("http://localhost:" + port + "/api/info").openConnection();
      connection.setConnectTimeout(500);
      connection.setReadTimeout(500);
      connection.setRequestMethod("GET");

      if (connection.getResponseCode() != 200) {
        return false;
      }

      String body = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      String expected = expectedReportDir.toAbsolutePath().normalize().toString();
      return body.contains(expected);
    } catch (Exception exception) {
      return false;
    }
  }

  private static void startDetachedViewer(Path reportDir, int port) throws Exception {
    Path java =
        Paths.get(
            System.getProperty("java.home"),
            "bin",
            System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");

    Path codeSource = resolveCodeSource();
    ProcessBuilder builder;

    if (Files.isRegularFile(codeSource) && codeSource.toString().endsWith(".jar")) {
      builder =
          new ProcessBuilder(
              java.toString(),
              "-jar",
              codeSource.toString(),
              "serve",
              "--port",
              String.valueOf(port),
              "--report-dir",
              reportDir.toString(),
              "--no-open");
    } else {
      String classpath = System.getProperty("java.class.path");
      builder =
          new ProcessBuilder(
              java.toString(),
              "-cp",
              classpath,
              ReportViewerCli.class.getName(),
              "serve",
              "--port",
              String.valueOf(port),
              "--report-dir",
              reportDir.toString(),
              "--no-open");
    }

    Path workingDir = reportDir.getParent();
    if (workingDir != null && Files.isDirectory(workingDir)) {
      builder.directory(workingDir.toFile());
    }

    builder.redirectErrorStream(true);
    builder.start();
  }

  private static Path resolveCodeSource() throws Exception {
    return Paths.get(ReportViewerCli.class.getProtectionDomain().getCodeSource().getLocation().toURI())
        .toAbsolutePath()
        .normalize();
  }

  static Path resolveReportDir(String value) {
    Path path = Paths.get(value);
    if (!path.isAbsolute()) {
      path = Paths.get(System.getProperty("user.dir")).resolve(path);
    }
    return path.toAbsolutePath().normalize();
  }

  private static boolean shouldOpen(boolean failed) {
    String mode = firstNonBlank(System.getProperty("testreport.open"), "always").toLowerCase();

    return switch (mode) {
      case "never", "false", "0", "off" -> false;
      case "on-failure", "onfailure", "failure" -> failed;
      default -> !isCi();
    };
  }

  private static boolean isCi() {
    for (String key : new String[] {"CI", "CONTINUOUS_INTEGRATION", "BUILD_NUMBER", "GITHUB_ACTIONS"}) {
      String value = System.getenv(key);
      if (value != null && !value.isBlank() && !"false".equalsIgnoreCase(value)) {
        return true;
      }
    }
    return false;
  }

  private static int parseInt(String value, int fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }

    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }
}
