package com.bim.compiler.validation.building;

import com.bim.compiler.dsl.BuildingCompiler.BuildingSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 21A: Orchestrates multiple building validators.
 *
 * Runs validators in order, collecting results.
 * Can stop on first critical failure if configured.
 */
public class ValidatorChain {

    private final List<BuildingValidator> validators;
    private final boolean stopOnCritical;

    /**
     * Create a validator chain.
     * @param stopOnCritical if true, stop after first critical failure from required validator
     * @param validators validators to run in order
     */
    public ValidatorChain(boolean stopOnCritical, BuildingValidator... validators) {
        this.stopOnCritical = stopOnCritical;
        this.validators = List.of(validators);
    }

    /**
     * Create a validator chain that runs all validators.
     */
    public ValidatorChain(BuildingValidator... validators) {
        this(false, validators);
    }

    /**
     * Validate a building with all validators.
     * @param building the compiled building spec
     * @return combined validation report
     */
    public ValidationReport validate(BuildingSpec building) {
        ValidationReport report = new ValidationReport(building.name());

        for (BuildingValidator validator : validators) {
            BuildingValidationResult result = validator.validate(building);
            report.addResult(validator, result);

            if (stopOnCritical && validator.isRequired() && result.hasCriticalFailures()) {
                report.markFailed(validator.getName());
                break;
            }
        }

        return report;
    }

    /**
     * Get the validators in this chain.
     */
    public List<BuildingValidator> getValidators() {
        return validators;
    }

    /**
     * Combined validation report from all validators.
     */
    public static class ValidationReport {

        private final String buildingName;
        private final List<ValidatorEntry> entries = new ArrayList<>();
        private boolean failed = false;
        private String failedAt = null;

        public ValidationReport(String buildingName) {
            this.buildingName = buildingName;
        }

        void addResult(BuildingValidator validator, BuildingValidationResult result) {
            entries.add(new ValidatorEntry(validator.getName(), validator.isRequired(), result));
        }

        void markFailed(String validatorName) {
            this.failed = true;
            this.failedAt = validatorName;
        }

        public String getBuildingName() {
            return buildingName;
        }

        public boolean hasCriticalFailures() {
            return entries.stream().anyMatch(e -> e.result.hasCriticalFailures());
        }

        public boolean hasWarnings() {
            return entries.stream().anyMatch(e -> e.result.hasWarnings());
        }

        public boolean isFailed() {
            return failed;
        }

        public String getFailedAt() {
            return failedAt;
        }

        public List<BuildingValidationResult.Issue> getAllCritical() {
            return entries.stream()
                .flatMap(e -> e.result.getCritical().stream())
                .toList();
        }

        public List<BuildingValidationResult.Issue> getAllWarnings() {
            return entries.stream()
                .flatMap(e -> e.result.getWarnings().stream())
                .toList();
        }

        public int getTotalCritical() {
            return entries.stream().mapToInt(e -> e.result.getCriticalCount()).sum();
        }

        public int getTotalWarnings() {
            return entries.stream().mapToInt(e -> e.result.getWarningCount()).sum();
        }

        public int getTotalInfo() {
            return entries.stream().mapToInt(e -> e.result.getInfoCount()).sum();
        }

        public BuildingValidationResult getResultFor(String validatorName) {
            return entries.stream()
                .filter(e -> e.name.equals(validatorName))
                .map(e -> e.result)
                .findFirst()
                .orElse(null);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=".repeat(70) + "%n"));
            sb.append(String.format("BUILDING VALIDATION REPORT: %s%n", buildingName));
            sb.append(String.format("=".repeat(70) + "%n"));
            sb.append(String.format("%n"));

            // Summary table
            sb.append(String.format("| %-25s | %-8s | %-8s | %-8s | %-6s |%n",
                "Validator", "Required", "Critical", "Warnings", "Status"));
            sb.append("|" + "-".repeat(27) + "|" + "-".repeat(10) + "|" + "-".repeat(10) +
                      "|" + "-".repeat(10) + "|" + "-".repeat(8) + "|" + "\n");

            for (ValidatorEntry entry : entries) {
                String status = entry.result.hasCriticalFailures() ? "FAIL" :
                               entry.result.hasWarnings() ? "WARN" : "PASS";
                sb.append(String.format("| %-25s | %-8s | %8d | %8d | %-6s |%n",
                    entry.name,
                    entry.required ? "YES" : "no",
                    entry.result.getCriticalCount(),
                    entry.result.getWarningCount(),
                    status));
            }

            sb.append(String.format("%n"));

            // Overall result
            sb.append(String.format("Total: %d critical, %d warnings, %d info%n",
                getTotalCritical(), getTotalWarnings(), getTotalInfo()));

            if (failed) {
                sb.append(String.format("%nBUILD FAILED at: %s%n", failedAt));
            } else if (hasCriticalFailures()) {
                sb.append(String.format("%nSTATUS: FAILED (critical issues found)%n"));
            } else if (hasWarnings()) {
                sb.append(String.format("%nSTATUS: PASSED with warnings%n"));
            } else {
                sb.append(String.format("%nSTATUS: PASSED%n"));
            }

            // Detail critical issues
            if (hasCriticalFailures()) {
                sb.append(String.format("%n%sCRITICAL ISSUES:%n", ""));
                for (var issue : getAllCritical()) {
                    sb.append(String.format("  - %s: %s%n", issue.element(), issue.message()));
                }
            }

            sb.append(String.format("=".repeat(70) + "%n"));

            return sb.toString();
        }

        private record ValidatorEntry(String name, boolean required, BuildingValidationResult result) {}
    }
}
