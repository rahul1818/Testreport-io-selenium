package io.testreport.selenium;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.IConfigurationListener;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.annotations.Test;

/**
 * TestNG listener that writes {@code custom-report/<runId>/results.json} in the same format
 * as the Playwright reporter, so the shared HTML dashboard can render Selenium runs.
 */
public class SeleniumTestReporter
    implements ITestListener, IConfigurationListener, IInvokedMethodListener {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final String outputDir;
  private final String fileName;
  private final String projectName;

  private ReportModels.RunReport run;
  private Path baseDir;
  private final Map<String, ReportModels.AttemptBuffer> attemptGroups = new ConcurrentHashMap<>();
  private final Map<String, List<String>> testStdout = new ConcurrentHashMap<>();
  private final Map<String, List<String>> testStderr = new ConcurrentHashMap<>();
  private final List<String> runStdout = new ArrayList<>();
  private final List<String> runStderr = new ArrayList<>();
  private final Set<String> browsers = new LinkedHashSet<>();

  public SeleniumTestReporter() {
    this(new ReporterConfig());
  }

  public SeleniumTestReporter(ReporterConfig config) {
    ReporterConfig resolved = config == null ? new ReporterConfig() : config;
    this.outputDir = firstNonBlank(resolved.outputDir, System.getProperty("testreport.outputDir"), "custom-report");
    this.fileName = firstNonBlank(resolved.fileName, System.getProperty("testreport.fileName"), "results.json");
    this.projectName = firstNonBlank(resolved.projectName, System.getProperty("testreport.projectName"), "");
  }

  @Override
  public void onStart(ITestContext context) {
    if (run != null) {
      return;
    }

    run = new ReportModels.RunReport();
    run.runId = String.valueOf(System.currentTimeMillis());
    run.startedAt = Instant.now().toString();
    run.projectName = projectName;
    run.environment.os = System.getProperty("os.name") + " " + System.getProperty("os.version");
    run.environment.selenium = org.openqa.selenium.BuildInfo.class.getPackage().getImplementationVersion();
    run.environment.java = System.getProperty("java.version");

    baseDir = Paths.get(outputDir, run.runId).toAbsolutePath();

    try {
      Files.createDirectories(baseDir);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to create report directory: " + baseDir, exception);
    }
  }

  @Override
  public void onFinish(ITestContext context) {
    if (run == null) {
      return;
    }

    run.finishedAt = Instant.now().toString();
    finalizeTests();
    writeRun();
    boolean failed = run.totals.failed > 0 || run.totals.timedOut > 0;
    ReportViewerLauncher.openAfterRun(Paths.get(outputDir), run.runId, failed);
  }

  @Override
  public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
    ensureStarted(testResult.getTestContext());
  }

  @Override
  public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
    if (!method.isTestMethod() || run == null) {
      return;
    }

  recordTestAttempt(testResult);
  }

  @Override
  public void onTestStart(ITestResult result) {
    ensureStarted(result.getTestContext());
  }

  @Override
  public void onConfigurationFailure(ITestResult result) {
    appendStderr(result, "Configuration failed: " + safeMessage(result.getThrowable()));
    recordTestAttempt(result, "failed");
  }

  @Override
  public void onConfigurationSkip(ITestResult result) {
    recordTestAttempt(result, "skipped");
  }

  public void logStdout(ITestResult result, String text) {
    if (text == null || text.isBlank()) {
      return;
    }

    runStdout.add(text);
    testStdout.computeIfAbsent(testKey(result), key -> new ArrayList<>()).add(text);
  }

  public void logStderr(ITestResult result, String text) {
    if (text == null || text.isBlank()) {
      return;
    }

    runStderr.add(text);
    testStderr.computeIfAbsent(testKey(result), key -> new ArrayList<>()).add(text);
  }

  private void ensureStarted(ITestContext context) {
    if (run == null) {
      onStart(context);
    }
  }

  private void recordTestAttempt(ITestResult result) {
    recordTestAttempt(result, mapStatus(result));
  }

  private void recordTestAttempt(ITestResult result, String status) {
    ReportModels.TestEntry entry = buildEntry(result, status);
    String groupKey = ReportModels.attemptGroupKey(entry);
    attemptGroups.computeIfAbsent(groupKey, key -> new ReportModels.AttemptBuffer()).attempts.add(entry);

    if (entry.browserName != null && !entry.browserName.isBlank()) {
      browsers.add(entry.browserName);
    }
  }

  private ReportModels.TestEntry buildEntry(ITestResult result, String status) {
    ReportModels.TestEntry entry = new ReportModels.TestEntry();
    entry.id = testKey(result);
    entry.title = resolveTitle(result);
    entry.fullTitle = resolveFullTitle(result);
    entry.projectName = resolveProjectName(result);
    entry.browserName = resolveBrowserName(result);
    entry.file = resolveFile(result);
    entry.line = resolveLine(result);
    entry.column = 0;
    entry.annotations = resolveAnnotations(result);
    entry.retries = Math.max(0, result.getMethod().getCurrentInvocationCount() - 1);
    entry.status = status;
    entry.expectedStatus = "passed";
    entry.durationMs = Math.max(0L, result.getEndMillis() - result.getStartMillis());
    entry.startedAt = Instant.ofEpochMilli(result.getStartMillis()).toString();
    entry.finishedAt = Instant.ofEpochMilli(result.getEndMillis()).toString();
    entry.error = toErrorInfo(result.getThrowable());
    entry.stdout = new ArrayList<>(testStdout.getOrDefault(entry.id, List.of()));
    entry.stderr = new ArrayList<>(testStderr.getOrDefault(entry.id, List.of()));
    entry.attachments = captureAttachments(result);
    entry.steps = List.of();

    if (result.getThrowable() != null) {
      appendStderr(result, safeMessage(result.getThrowable()));
    }

    return entry;
  }

  private List<ReportModels.AttachmentEntry> captureAttachments(ITestResult result) {
    List<ReportModels.AttachmentEntry> attachments = new ArrayList<>();

    Object driver = result.getAttribute("driver");
    if (!(driver instanceof TakesScreenshot)) {
      driver = result.getTestContext().getAttribute("driver");
    }

    if (driver instanceof TakesScreenshot screenshotDriver && shouldCaptureScreenshot(result)) {
      try {
        byte[] png = screenshotDriver.getScreenshotAs(OutputType.BYTES);
        String fileName = sanitizeFileName(entryBaseName(result) + ".png");
        Path target = baseDir.resolve(fileName);
        Files.write(target, png);
        ReportModels.AttachmentEntry attachment = new ReportModels.AttachmentEntry();
        attachment.name = "screenshot";
        attachment.contentType = "image/png";
        attachment.path = target.toString();
        attachments.add(attachment);
      } catch (IOException | RuntimeException ignored) {
        // Screenshot capture is best-effort.
      }
    }

    return attachments;
  }

  private boolean shouldCaptureScreenshot(ITestResult result) {
    return result.getStatus() == ITestResult.FAILURE || result.getStatus() == ITestResult.SKIP;
  }

  private void finalizeTests() {
    List<ReportModels.TestEntry> tests = new ArrayList<>();
    ReportModels.Totals totals = new ReportModels.Totals();

    for (ReportModels.AttemptBuffer buffer : attemptGroups.values()) {
      ReportModels.TestEntry entry = ReportModels.finalizeAttempts(buffer.attempts);
      tests.add(entry);
      totals.tests += 1;
      totals.durationMs += entry.durationMs;

      switch (entry.status) {
        case "flaky" -> totals.flaky += 1;
        case "passed" -> totals.passed += 1;
        case "skipped" -> totals.skipped += 1;
        case "timedOut" -> totals.timedOut += 1;
        default -> {
          if ("failed".equals(entry.status)) {
            totals.failed += 1;
          } else {
            totals.failed += 1;
          }
        }
      }
    }

    run.tests = tests;
    run.totals = totals;
    run.stdout = new ArrayList<>(runStdout);
    run.stderr = new ArrayList<>(runStderr);
    run.projects = tests.stream()
        .map(test -> test.projectName)
        .filter(value -> value != null && !value.isBlank())
        .distinct()
        .toList();

    if (!browsers.isEmpty()) {
      run.environment.browser = String.join(", ", browsers);
    }
  }

  private void writeRun() {
    Path filePath = baseDir.resolve(fileName);

    try {
      Files.writeString(filePath, GSON.toJson(run), StandardCharsets.UTF_8);
      System.out.println("Custom report written to " + filePath);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to write report: " + filePath, exception);
    }
  }

  private static String mapStatus(ITestResult result) {
    return switch (result.getStatus()) {
      case ITestResult.SUCCESS -> "passed";
      case ITestResult.FAILURE -> "failed";
      case ITestResult.SKIP -> "skipped";
      case ITestResult.SUCCESS_PERCENTAGE_FAILURE -> "failed";
      case ITestResult.STARTED -> "started";
      default -> "failed";
    };
  }

  private static String resolveTitle(ITestResult result) {
    Test test = result.getMethod().getConstructorOrMethod().getMethod().getAnnotation(Test.class);
    if (test != null && test.description() != null && !test.description().isBlank()) {
      return test.description().trim();
    }

    return result.getMethod().getMethodName();
  }

  private static List<String> resolveFullTitle(ITestResult result) {
    List<String> parts = new ArrayList<>();
    parts.add(result.getTestClass().getName());
    parts.add(resolveTitle(result));
    return parts;
  }

  private static String resolveProjectName(ITestResult result) {
    String suiteName = result.getTestContext().getSuite().getName();
    if (suiteName != null && !suiteName.isBlank()) {
      return suiteName;
    }

    return result.getTestClass().getRealClass().getSimpleName();
  }

  private static String resolveBrowserName(ITestResult result) {
    String[] candidates = {
      result.getTestContext().getCurrentXmlTest().getParameter("browser"),
      result.getTestContext().getCurrentXmlTest().getParameter("browserName"),
      System.getProperty("browser"),
      System.getProperty("browserName")
    };

    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return normalizeBrowser(candidate.trim());
      }
    }

    Object driver = result.getAttribute("driver");
    if (driver == null) {
      driver = result.getTestContext().getAttribute("driver");
    }

    if (driver instanceof WebDriver webDriver) {
      try {
        Capabilities capabilities =
            ((org.openqa.selenium.HasCapabilities) webDriver).getCapabilities();
        String browser = capabilities.getBrowserName();
        if (browser != null && !browser.isBlank()) {
          return normalizeBrowser(browser);
        }
      } catch (RuntimeException ignored) {
        return "chrome";
      }
    }

    return "";
  }

  private static String normalizeBrowser(String value) {
    String lower = value.toLowerCase();
    if ("msedge".equals(lower)) {
      return "edge";
    }
    return lower;
  }

  private static String resolveFile(ITestResult result) {
    var method = result.getMethod().getConstructorOrMethod().getMethod();
    var source = method.getDeclaringClass().getProtectionDomain().getCodeSource();
    if (source != null && source.getLocation() != null) {
      return method.getDeclaringClass().getName().replace('.', '/') + ".java";
    }
    return method.getDeclaringClass().getName().replace('.', '/') + ".java";
  }

  private static Integer resolveLine(ITestResult result) {
    if (result.getThrowable() == null) {
      return null;
    }

    for (StackTraceElement element : result.getThrowable().getStackTrace()) {
      if (element.getClassName().equals(result.getTestClass().getName()) && element.getLineNumber() > 0) {
        return element.getLineNumber();
      }
    }

    return null;
  }

  private static List<ReportModels.AnnotationEntry> resolveAnnotations(ITestResult result) {
    List<ReportModels.AnnotationEntry> annotations = new ArrayList<>();
    Test test = result.getMethod().getConstructorOrMethod().getMethod().getAnnotation(Test.class);

    if (test != null) {
      for (String group : test.groups()) {
        annotations.add(new ReportModels.AnnotationEntry("group", group));
      }
    }

    return annotations;
  }

  private static ReportModels.ErrorInfo toErrorInfo(Throwable throwable) {
    if (throwable == null) {
      return null;
    }

    return new ReportModels.ErrorInfo(safeMessage(throwable), stackTrace(throwable));
  }

  private static String safeMessage(Throwable throwable) {
    if (throwable == null) {
      return "";
    }

    String message = throwable.getMessage();
    return message == null || message.isBlank() ? throwable.toString() : message;
  }

  private static String stackTrace(Throwable throwable) {
    StringWriter writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    return writer.toString().trim();
  }

  private void appendStderr(ITestResult result, String text) {
    logStderr(result, text);
  }

  private static String testKey(ITestResult result) {
    return result.getTestClass().getName()
        + "#"
        + result.getMethod().getMethodName()
        + "@"
        + System.identityHashCode(result);
  }

  private static String entryBaseName(ITestResult result) {
    return sanitizeFileName(result.getTestClass().getRealClass().getSimpleName()
        + "-"
        + result.getMethod().getMethodName()
        + "-"
        + UUID.randomUUID());
  }

  private static String sanitizeFileName(String value) {
    return value.replaceAll("[^a-zA-Z0-9._-]+", "-");
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  public static final class ReporterConfig {
    private String outputDir;
    private String fileName;
    private String projectName;

    public ReporterConfig outputDir(String outputDir) {
      this.outputDir = outputDir;
      return this;
    }

    public ReporterConfig fileName(String fileName) {
      this.fileName = fileName;
      return this;
    }

    public ReporterConfig projectName(String projectName) {
      this.projectName = projectName;
      return this;
    }
  }
}
