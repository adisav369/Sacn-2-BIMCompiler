package com.bim.tools.sanity.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Aggregates all check results and formats output.
 */
public class SanityReport {
    public enum Verdict { PASS, FAIL }

    private final String inputPath;
    private final LocalDateTime timestamp;
    private final List<CheckResult> results;

    public SanityReport(String inputPath) {
        this.inputPath = inputPath;
        this.timestamp = LocalDateTime.now();
        this.results = new ArrayList<>();
    }

    public void addResult(CheckResult result) {
        results.add(result);
    }

    public List<CheckResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    public Verdict getVerdict() {
        for (CheckResult r : results) {
            if (r.isFailed()) return Verdict.FAIL;
        }
        return Verdict.PASS;
    }

    public int getPassCount() {
        return (int) results.stream().filter(CheckResult::isPassed).count();
    }

    public int getWarningCount() {
        return (int) results.stream().filter(CheckResult::isWarning).count();
    }

    public int getFailCount() {
        return (int) results.stream().filter(CheckResult::isFailed).count();
    }

    public int getTotalCount() {
        return results.size();
    }

    /**
     * Generate console output.
     */
    public String toConsoleString(boolean verbose) {
        StringBuilder sb = new StringBuilder();
        String line = "═".repeat(64);
        String thinLine = "─".repeat(64);

        // Header
        sb.append("╔").append(line).append("╗\n");
        sb.append("║").append(center("HOUSE SANITY CHECK - Phase 0 Probe", 64)).append("║\n");
        sb.append("╠").append(line).append("╣\n");
        sb.append("║").append(padRight(" Input: " + truncate(inputPath, 54), 64)).append("║\n");
        sb.append("║").append(padRight(" Date:  " + timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 64)).append("║\n");
        sb.append("╚").append(line).append("╝\n\n");

        // Results
        int checkNum = 0;
        for (CheckResult result : results) {
            checkNum++;
            String statusIcon = switch (result.getStatus()) {
                case PASS -> "✓";
                case WARNING -> "⚠";
                case FAIL -> "✗";
            };

            sb.append(String.format("[%d/%d] %s\n", checkNum, results.size(), result.getCheckName()));
            sb.append(String.format("      %s %s: %s\n",
                statusIcon,
                result.getStatus(),
                result.getSummary()));

            if (result.isFailed() || result.isWarning() || verbose) {
                if (!result.getDetails().isEmpty()) {
                    sb.append("\n      Details:\n");
                    for (String detail : result.getDetails()) {
                        sb.append("      - ").append(detail).append("\n");
                    }
                }
                if (result.getGuidance() != null && !result.getGuidance().isEmpty()) {
                    sb.append("\n      Guidance: ").append(result.getGuidance()).append("\n");
                }
            }
            sb.append("\n");
        }

        // Footer
        sb.append(line).append("\n");
        if (getVerdict() == Verdict.PASS) {
            sb.append(String.format("VERDICT: ✓ This is recognizably a house (%d/%d checks passed)\n",
                getPassCount(), getTotalCount()));
        } else {
            sb.append("VERDICT: ✗ NOT a valid house\n\n");
            sb.append("Summary:\n");
            sb.append(String.format("- FAIL: %d critical issues\n", getFailCount()));
            sb.append(String.format("- WARNING: %d issues\n", getWarningCount()));
            sb.append(String.format("- PASS: %d checks\n", getPassCount()));
            sb.append("\nThe building has fundamental problems that must be resolved\n");
            sb.append("before fine-grained geometric verification.\n");
        }
        sb.append(line).append("\n");

        return sb.toString();
    }

    /**
     * Generate JSON output.
     */
    public String toJsonString() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("input", inputPath);
        json.put("timestamp", timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        json.put("verdict", getVerdict().name());

        List<Map<String, Object>> checks = new ArrayList<>();
        for (CheckResult r : results) {
            Map<String, Object> check = new LinkedHashMap<>();
            check.put("id", r.getCheckId());
            check.put("name", r.getCheckName());
            check.put("status", r.getStatus().name());
            check.put("summary", r.getSummary());
            if (!r.getDetails().isEmpty()) {
                check.put("details", r.getDetails());
            }
            if (r.getGuidance() != null && !r.getGuidance().isEmpty()) {
                check.put("guidance", r.getGuidance());
            }
            if (!r.getData().isEmpty()) {
                check.putAll(r.getData());
            }
            checks.add(check);
        }
        json.put("checks", checks);

        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("total", getTotalCount());
        summary.put("pass", getPassCount());
        summary.put("warning", getWarningCount());
        summary.put("fail", getFailCount());
        json.put("summary", summary);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(json);
    }

    private static String center(String s, int width) {
        if (s.length() >= width) return s;
        int pad = (width - s.length()) / 2;
        return " ".repeat(pad) + s + " ".repeat(width - pad - s.length());
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return "..." + s.substring(s.length() - maxLen + 3);
    }
}
