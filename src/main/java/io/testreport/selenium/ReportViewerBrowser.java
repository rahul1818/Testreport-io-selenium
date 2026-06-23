package io.testreport.selenium;

import java.awt.Desktop;
import java.net.URI;

final class ReportViewerBrowser {

  private ReportViewerBrowser() {}

  static void open(String url) {
    if (!Desktop.isDesktopSupported()) {
      return;
    }

    try {
      Desktop desktop = Desktop.getDesktop();
      if (desktop.isSupported(Desktop.Action.BROWSE)) {
        desktop.browse(new URI(url));
      }
    } catch (Exception ignored) {
      // Opening a browser is best-effort.
    }
  }
}
