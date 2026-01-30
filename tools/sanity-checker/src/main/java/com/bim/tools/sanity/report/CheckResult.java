package com.bim.tools.sanity.report;

import java.util.*;

/**
 * Result of a single sanity check.
 */
public class CheckResult {
    public enum Status { PASS, WARNING, FAIL }

    private final String checkId;
    private final String checkName;
    private final Status status;
    private final String summary;
    private final List<String> details;
    private final String guidance;
    private final Map<String, Object> data;

    private CheckResult(Builder builder) {
        this.checkId = builder.checkId;
        this.checkName = builder.checkName;
        this.status = builder.status;
        this.summary = builder.summary;
        this.details = Collections.unmodifiableList(new ArrayList<>(builder.details));
        this.guidance = builder.guidance;
        this.data = Collections.unmodifiableMap(new HashMap<>(builder.data));
    }

    public String getCheckId() { return checkId; }
    public String getCheckName() { return checkName; }
    public Status getStatus() { return status; }
    public String getSummary() { return summary; }
    public List<String> getDetails() { return details; }
    public String getGuidance() { return guidance; }
    public Map<String, Object> getData() { return data; }

    public boolean isPassed() { return status == Status.PASS; }
    public boolean isWarning() { return status == Status.WARNING; }
    public boolean isFailed() { return status == Status.FAIL; }

    public static Builder pass(String checkId, String checkName) {
        return new Builder(checkId, checkName, Status.PASS);
    }

    public static Builder warning(String checkId, String checkName) {
        return new Builder(checkId, checkName, Status.WARNING);
    }

    public static Builder fail(String checkId, String checkName) {
        return new Builder(checkId, checkName, Status.FAIL);
    }

    public static class Builder {
        private final String checkId;
        private final String checkName;
        private final Status status;
        private String summary = "";
        private List<String> details = new ArrayList<>();
        private String guidance = "";
        private Map<String, Object> data = new HashMap<>();

        private Builder(String checkId, String checkName, Status status) {
            this.checkId = checkId;
            this.checkName = checkName;
            this.status = status;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder detail(String detail) {
            this.details.add(detail);
            return this;
        }

        public Builder details(List<String> details) {
            this.details.addAll(details);
            return this;
        }

        public Builder guidance(String guidance) {
            this.guidance = guidance;
            return this;
        }

        public Builder data(String key, Object value) {
            this.data.put(key, value);
            return this;
        }

        public CheckResult build() {
            return new CheckResult(this);
        }
    }
}
