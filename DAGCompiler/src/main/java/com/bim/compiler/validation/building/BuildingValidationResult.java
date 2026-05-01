/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.validation.building;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Phase 21A: Building-level validation result.
 *
 * Collects validation issues with element context.
 * Critical issues block building generation.
 * Warnings are logged but don't block.
 */
public class BuildingValidationResult {

    public enum Severity {
        INFO,      // Informational note
        WARNING,   // Potential issue, doesn't block
        CRITICAL   // Must fix, blocks building generation
    }

    /**
     * A single validation issue with context.
     */
    public record Issue(
        Severity severity,
        String element,      // Room name, wall name, etc.
        String checkName,    // Which check found this
        String message
    ) {
        @Override
        public String toString() {
            return String.format("[%s] %s: %s (%s)", severity, element, message, checkName);
        }
    }

    private final String validatorName;
    private final List<Issue> issues = new ArrayList<>();

    public BuildingValidationResult(String validatorName) {
        this.validatorName = validatorName;
    }

    // =========================================================================
    // Adding issues
    // =========================================================================

    public void addInfo(String element, String checkName, String message) {
        issues.add(new Issue(Severity.INFO, element, checkName, message));
    }

    public void addWarning(String element, String checkName, String message) {
        issues.add(new Issue(Severity.WARNING, element, checkName, message));
    }

    public void addCritical(String element, String checkName, String message) {
        issues.add(new Issue(Severity.CRITICAL, element, checkName, message));
    }

    public void addInfo(String element, String checkName, String format, Object... args) {
        addInfo(element, checkName, String.format(format, args));
    }

    public void addWarning(String element, String checkName, String format, Object... args) {
        addWarning(element, checkName, String.format(format, args));
    }

    public void addCritical(String element, String checkName, String format, Object... args) {
        addCritical(element, checkName, String.format(format, args));
    }

    // =========================================================================
    // Querying results
    // =========================================================================

    public String getValidatorName() {
        return validatorName;
    }

    public List<Issue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public List<Issue> getCritical() {
        return issues.stream().filter(i -> i.severity == Severity.CRITICAL).toList();
    }

    public List<Issue> getWarnings() {
        return issues.stream().filter(i -> i.severity == Severity.WARNING).toList();
    }

    public List<Issue> getInfo() {
        return issues.stream().filter(i -> i.severity == Severity.INFO).toList();
    }

    public boolean hasCriticalFailures() {
        return issues.stream().anyMatch(i -> i.severity == Severity.CRITICAL);
    }

    public boolean hasWarnings() {
        return issues.stream().anyMatch(i -> i.severity == Severity.WARNING);
    }

    public boolean isClean() {
        return issues.isEmpty();
    }

    public int getCriticalCount() {
        return (int) issues.stream().filter(i -> i.severity == Severity.CRITICAL).count();
    }

    public int getWarningCount() {
        return (int) issues.stream().filter(i -> i.severity == Severity.WARNING).count();
    }

    public int getInfoCount() {
        return (int) issues.stream().filter(i -> i.severity == Severity.INFO).count();
    }

    // =========================================================================
    // Merging results
    // =========================================================================

    public void merge(BuildingValidationResult other) {
        issues.addAll(other.issues);
    }

    // =========================================================================
    // Summary
    // =========================================================================

    @Override
    public String toString() {
        return String.format("%s: %d critical, %d warnings, %d info",
            validatorName, getCriticalCount(), getWarningCount(), getInfoCount());
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== %s ===%n", validatorName));
        sb.append(String.format("Critical: %d, Warnings: %d, Info: %d%n",
            getCriticalCount(), getWarningCount(), getInfoCount()));

        if (hasCriticalFailures()) {
            sb.append("\nCritical Issues:\n");
            getCritical().forEach(i -> sb.append("  ").append(i).append("\n"));
        }

        if (hasWarnings()) {
            sb.append("\nWarnings:\n");
            getWarnings().stream().limit(10).forEach(i -> sb.append("  ").append(i).append("\n"));
            if (getWarningCount() > 10) {
                sb.append(String.format("  ... and %d more warnings%n", getWarningCount() - 10));
            }
        }

        return sb.toString();
    }
}
