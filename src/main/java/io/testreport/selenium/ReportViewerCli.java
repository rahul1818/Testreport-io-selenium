package io.testreport.selenium;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CLI entry point to serve the HTML dashboard from a Maven project.
 *
 * <pre>
 *   mvn exec:java -Dexec.mainClass=io.testreport.selenium.ReportViewerCli
 * </pre>
 */
public final class ReportViewerCli {

  private ReportViewerCli() {}

  public static void main(String[] args) throws IOException {
    int port = 4173;
    Path reportDir = Paths.get("custom-report").toAbsolutePath();

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];

      if (("--port".equals(arg) || "-p".equals(arg)) && i + 1 < args.length) {
        port = Integer.parseInt(args[++i]);
      } else if (("--report-dir".equals(arg) || "-d".equals(arg)) && i + 1 < args.length) {
        reportDir = Paths.get(args[++i]).toAbsolutePath();
      } else if ("serve".equals(arg)) {
        continue;
      } else if ("help".equals(arg) || "--help".equals(arg) || "-h".equals(arg)) {
        printUsage();
        return;
      }
    }

    ReportViewerServer viewer = new ReportViewerServer(reportDir, port);
    viewer.start();

    long runs = viewer.countRuns();
    System.out.println();
    System.out.println("Selenium Viewer running at http://localhost:" + port);
    System.out.println("Report folder: " + reportDir);
    System.out.println(
        "Runs found: " + runs + (runs > 0 ? "" : " (no results.json yet — run tests first)"));
    System.out.println();
    System.out.println("Press Ctrl+C to stop.");

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

        Options:
          --port, -p <port>         Viewer port (default: 4173)
          --report-dir, -d <path>   Report folder (default: ./custom-report)
        """);
  }
}
