package io.testreport.selenium;

import java.util.ArrayList;
import java.util.List;

final class ReportModels {
  private ReportModels() {}

  static final class RunReport {
    String runId;
    String startedAt;
    String finishedAt;
    String projectName;
    List<String> projects = new ArrayList<>();
    Environment environment = new Environment();
    Totals totals = new Totals();
    List<TestEntry> tests = new ArrayList<>();
    List<String> stdout = new ArrayList<>();
    List<String> stderr = new ArrayList<>();
  }

  static final class Environment {
    String os;
    String browser;
    String selenium;
    String java;
  }

  static final class Totals {
    int tests;
    int passed;
    int failed;
    int skipped;
    int timedOut;
    int flaky;
    long durationMs;
  }

  static final class TestEntry {
    String id;
    String title;
    List<String> fullTitle = new ArrayList<>();
    String projectName;
    String browserName;
    String file;
    Integer line;
    Integer column;
    List<AnnotationEntry> annotations = new ArrayList<>();
    int retries;
    String status;
    String expectedStatus;
    long durationMs;
    String startedAt;
    String finishedAt;
    ErrorInfo error;
    List<String> stdout = new ArrayList<>();
    List<String> stderr = new ArrayList<>();
    List<AttachmentEntry> attachments = new ArrayList<>();
    List<StepEntry> steps = new ArrayList<>();
    List<TestEntry> attempts = new ArrayList<>();
  }

  static final class AnnotationEntry {
    String type;
    String description;

    AnnotationEntry(String type, String description) {
      this.type = type;
      this.description = description;
    }
  }

  static final class ErrorInfo {
    String message;
    String stack;

    ErrorInfo(String message, String stack) {
      this.message = message;
      this.stack = stack;
    }
  }

  static final class AttachmentEntry {
    String name;
    String contentType;
    String path;
    String text;
    String body;
    Integer attempt;
  }

  static final class StepEntry {
    String title;
    String category;
    String startTime;
    long durationMs;
    ErrorInfo error;
  }

  static final class AttemptBuffer {
    final List<TestEntry> attempts = new ArrayList<>();
  }

  static String attemptGroupKey(TestEntry entry) {
  return String.join(
      "\0",
      safe(entry.projectName),
      safe(entry.file).replace('\\', '/'),
      String.valueOf(entry.line == null ? "" : entry.line),
      String.valueOf(entry.column == null ? "" : entry.column),
      safe(entry.title).replaceAll("\\s+", " ").trim());
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  static TestEntry finalizeAttempts(List<TestEntry> attempts) {
    List<TestEntry> sorted = new ArrayList<>(attempts);
    sorted.sort((a, b) -> Integer.compare(a.retries, b.retries));

    TestEntry finalAttempt = sorted.get(sorted.size() - 1);
    boolean hadEarlierFailure =
        sorted.subList(0, sorted.size() - 1).stream().anyMatch(ReportModels::isFailedStatus);
    boolean passedAfterRetry = "passed".equals(finalAttempt.status) && finalAttempt.retries > 0;
    boolean failedBeforePass =
        "passed".equals(finalAttempt.status) && (hadEarlierFailure || passedAfterRetry);

    String status = failedBeforePass ? "flaky" : finalAttempt.status;
    long durationMs = sorted.stream().mapToLong(entry -> entry.durationMs).sum();

    List<String> stdout = new ArrayList<>();
    List<String> stderr = new ArrayList<>();
    List<AttachmentEntry> attachments = new ArrayList<>();

    for (int i = 0; i < sorted.size(); i++) {
      TestEntry attempt = sorted.get(i);
      stdout.addAll(attempt.stdout);
      stderr.addAll(attempt.stderr);

      for (AttachmentEntry attachment : attempt.attachments) {
        AttachmentEntry copy = copyAttachment(attachment);
        copy.attempt = attempt.retries > 0 ? attempt.retries : i;
        attachments.add(copy);
      }
    }

    TestEntry result = copyEntry(finalAttempt);
    result.status = status;
    result.retries = sorted.stream().mapToInt(entry -> entry.retries).max().orElse(0);
    result.durationMs = durationMs;
    result.startedAt = sorted.get(0).startedAt;
    result.finishedAt = finalAttempt.finishedAt;
    result.stdout = stdout;
    result.stderr = stderr;
    result.attachments = attachments;
    result.attempts = sorted;
    result.error = finalAttempt.error;

    if (result.error == null) {
      for (int i = sorted.size() - 1; i >= 0; i--) {
        if (sorted.get(i).error != null) {
          result.error = sorted.get(i).error;
          break;
        }
      }
    }

    return result;
  }

  static boolean isFailedStatus(TestEntry entry) {
    return "failed".equals(entry.status)
        || "timedOut".equals(entry.status)
        || "interrupted".equals(entry.status);
  }

  private static AttachmentEntry copyAttachment(AttachmentEntry source) {
    AttachmentEntry copy = new AttachmentEntry();
    copy.name = source.name;
    copy.contentType = source.contentType;
    copy.path = source.path;
    copy.text = source.text;
    copy.body = source.body;
    copy.attempt = source.attempt;
    return copy;
  }

  private static TestEntry copyEntry(TestEntry source) {
    TestEntry copy = new TestEntry();
    copy.id = source.id;
    copy.title = source.title;
    copy.fullTitle = new ArrayList<>(source.fullTitle);
    copy.projectName = source.projectName;
    copy.browserName = source.browserName;
    copy.file = source.file;
    copy.line = source.line;
    copy.column = source.column;
    copy.annotations = new ArrayList<>(source.annotations);
    copy.retries = source.retries;
    copy.status = source.status;
    copy.expectedStatus = source.expectedStatus;
    copy.durationMs = source.durationMs;
    copy.startedAt = source.startedAt;
    copy.finishedAt = source.finishedAt;
    copy.error = source.error;
    copy.stdout = new ArrayList<>(source.stdout);
    copy.stderr = new ArrayList<>(source.stderr);
    copy.attachments = new ArrayList<>(source.attachments);
    copy.steps = new ArrayList<>(source.steps);
    return copy;
  }
}
