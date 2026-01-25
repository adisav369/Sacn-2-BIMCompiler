package com.bim.compiler.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects validation messages without throwing exceptions.
 * From CODE PATTERN 5: "On error: warn & skip, don't abort"
 */
public class ValidationResult {

    public enum Severity {
        INFO,     // Informational (e.g., multi-story element detected)
        WARNING,  // Potential issue (e.g., tight clearance)
        ERROR     // Definite violation (e.g., invalid pipe diameter)
    }

    public record Message(Severity severity, String elementGuid, String text) {
        @Override
        public String toString() {
            return String.format("[%s] %s: %s", severity, elementGuid, text);
        }
    }

    private final List<Message> messages = new ArrayList<>();
    private int infoCount = 0;
    private int warningCount = 0;
    private int errorCount = 0;

    public void addInfo(String elementGuid, String format, Object... args) {
        messages.add(new Message(Severity.INFO, elementGuid, String.format(format, args)));
        infoCount++;
    }

    public void addWarning(String elementGuid, String format, Object... args) {
        messages.add(new Message(Severity.WARNING, elementGuid, String.format(format, args)));
        warningCount++;
    }

    public void addError(String elementGuid, String format, Object... args) {
        messages.add(new Message(Severity.ERROR, elementGuid, String.format(format, args)));
        errorCount++;
    }

    public void merge(ValidationResult other) {
        messages.addAll(other.messages);
        infoCount += other.infoCount;
        warningCount += other.warningCount;
        errorCount += other.errorCount;
    }

    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public List<Message> getInfos() {
        return messages.stream().filter(m -> m.severity == Severity.INFO).toList();
    }

    public List<Message> getWarnings() {
        return messages.stream().filter(m -> m.severity == Severity.WARNING).toList();
    }

    public List<Message> getErrors() {
        return messages.stream().filter(m -> m.severity == Severity.ERROR).toList();
    }

    public int getInfoCount() {
        return infoCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public boolean hasErrors() {
        return errorCount > 0;
    }

    public boolean hasWarnings() {
        return warningCount > 0;
    }

    public boolean isClean() {
        return errorCount == 0 && warningCount == 0;
    }

    @Override
    public String toString() {
        return String.format("ValidationResult[info=%d, warnings=%d, errors=%d]",
            infoCount, warningCount, errorCount);
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Validation: %d info, %d warnings, %d errors%n",
            infoCount, warningCount, errorCount));

        if (errorCount > 0) {
            sb.append("\nErrors:\n");
            getErrors().stream().limit(10).forEach(m -> sb.append("  ").append(m).append("\n"));
            if (errorCount > 10) {
                sb.append(String.format("  ... and %d more errors%n", errorCount - 10));
            }
        }

        if (warningCount > 0) {
            sb.append("\nWarnings:\n");
            getWarnings().stream().limit(10).forEach(m -> sb.append("  ").append(m).append("\n"));
            if (warningCount > 10) {
                sb.append(String.format("  ... and %d more warnings%n", warningCount - 10));
            }
        }

        return sb.toString();
    }
}
