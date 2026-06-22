package io.testreport.selenium;

import java.awt.Desktop;
import java.net.URI;
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
    Path root = reportDir.toAbsolutePath().normalize();
    String url = "http://localhost:" + port;

    Thread launcher =
        new Thread(
            () -> {
              try {
                startDetachedViewer(root, port);
                Thread.sleep(800);
                openBrowser(url);

                System.out.println();
                System.out.println("Testreport dashboard: " + url);
                System.out.println("Report folder: " + root);
                System.out.println("Viewer runs in the background — close that terminal to stop it.");
                System.out.println();
              } catch (Exception exception) {
                System.err.println(
                    "Unable to open Testreport dashboard: " + exception.getMessage());
              }
            },
            "testreport-viewer");

    launcher.setDaemon(true);
    launcher.start();
  }

  private static void startDetachedViewer(Path reportDir, int port) throws Exception {
    Path java =
        Paths.get(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
    Path jar =
        Paths.get(SeleniumTestReporter.class.getProtectionDomain().getCodeSource().getLocation().toURI());

    ProcessBuilder builder =
        new ProcessBuilder(
            java.toString(),
            "-cp",
            jar.toString(),
            ReportViewerCli.class.getName(),
            "serve",
            "--port",
            String.valueOf(port),
            "--report-dir",
            reportDir.toString());

    builder.inheritIO();
    builder.start();
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

  private static void openBrowser(String url) {
    if (!Desktop.isDesktopSupported()) {
      return;
    }

    try {
      Desktop desktop = Desktop.getDesktop();
      if (desktop.isSupported(Desktop.Action.BROWSE)) {
        desktop.browse(new URI(url));
      }
    } catch (Exception ignored) {
      // Opening a browser is best-effort (headless CI, Docker, etc.).
    }
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
