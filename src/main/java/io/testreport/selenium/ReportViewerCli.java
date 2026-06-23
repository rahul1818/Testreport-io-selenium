package io.testreport.selenium;

import java.io.IOException;
import java.nio.file.Path;

/**
 * CLI entry point — same as {@code npx playwright-viewer serve}.
 *
 * <pre>
 *   mvn exec:java
 * </pre>
 */
public final class ReportViewerCli {

  private ReportViewerCli() {}

  public static void main(String[] args) throws IOException {
    int port = 4173;
    Path reportDir = ReportViewerLauncher.resolveReportDir("custom-report");
    boolean openBrowser = true;

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];

      if (("--port".equals(arg) || "-p".equals(arg)) && i + 1 < args.length) {
        port = Integer.parseInt(args[++i]);
      } else if (("--report-dir".equals(arg) || "-d".equals(arg)) && i + 1 < args.length) {
        reportDir = ReportViewerLauncher.resolveReportDir(args[++i]);
      } else if ("--no-open".equals(arg)) {
        openBrowser = false;
      } else if ("serve".equals(arg)) {
        continue;
      } else if ("help".equals(arg) || "--help".equals(arg) || "-h".equals(arg)) {
        printUsage();
        return;
      }
    }

    ReportViewerServer viewer = new ReportViewerServer(reportDir, port);
    int boundPort = viewer.start();

    long runs = viewer.countRuns();
    String url = "http://localhost:" + boundPort;

    System.out.println();
    System.out.println("Testreport viewer running at " + url);
    System.out.println("Report folder: " + reportDir);
    System.out.println(
        "Runs found: " + runs + (runs > 0 ? "" : " (no results.json yet — run tests first)"));
    System.out.println();
    System.out.println("Press Ctrl+C to stop.");

    if (openBrowser) {
      ReportViewerBrowser.open(url);
    }

    Runtime.getRuntime().addShutdownHook(new Thread(viewer::stop));

    try {
      Thread.currentThread().join();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }

  private static void printUsage() {
    System.out.println(
        """
        Usage: ReportViewerCli serve [options]

        Same as: npx playwright-viewer serve

        Options:
          --port, -p <port>         Viewer port (default: 4173)
          --report-dir, -d <path>   Report folder (default: ./custom-report)
          --no-open                 Do not open browser automatically
        """);
  }
}
