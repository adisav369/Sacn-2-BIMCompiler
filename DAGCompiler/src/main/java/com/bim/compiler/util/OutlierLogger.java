/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.util;

import java.io.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 25: Outlier Handling Framework.
 *
 * Logs unknown inputs and graceful degradation events with actionable developer guidance.
 * Consistent format: [OUTLIER:<category>] <component> | <context> | <action>
 *                    → GUIDANCE: <developer action>
 *
 * Categories:
 * - UNKNOWN_SPACETYPE: SpaceType not in enum
 * - MISSING_COMPONENT: Component not in library
 * - UNSATISFIABLE: Solver constraints can't be met
 * - GEOMETRY_IMPOSSIBLE: Fixture doesn't fit in space
 * - VALIDATION_UNKNOWN: No validation rules for SpaceType
 * - VOCABULARY_GAP: Intent parser doesn't recognize term
 */
public class OutlierLogger {

    public enum OutlierCategory {
        UNKNOWN_SPACETYPE,
        MISSING_COMPONENT,
        UNSATISFIABLE,
        GEOMETRY_IMPOSSIBLE,
        VALIDATION_UNKNOWN,
        VOCABULARY_GAP
    }

    /**
     * Single outlier entry for tracking and reporting.
     */
    public record OutlierEntry(
        OutlierCategory category,
        String component,
        String context,
        String actionTaken,
        String guidance,
        LocalDateTime timestamp
    ) {}

    // Thread-safe storage for outliers
    private static final List<OutlierEntry> outliers = Collections.synchronizedList(new ArrayList<>());

    // Count by category for metrics
    private static final Map<OutlierCategory, Integer> categoryCounts = new ConcurrentHashMap<>();

    // Count by component name (for identifying frequent outliers)
    private static final Map<String, Integer> componentCounts = new ConcurrentHashMap<>();

    // Total elements processed (for outlier rate calculation)
    private static int totalElements = 0;

    // Compilation name for summary
    private static String compilationName = "unknown";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Reset outlier tracking for a new compilation.
     */
    public static void reset() {
        outliers.clear();
        categoryCounts.clear();
        componentCounts.clear();
        totalElements = 0;
        compilationName = "unknown";
    }

    /**
     * Set the compilation name for summary reports.
     */
    public static void setCompilationName(String name) {
        compilationName = name;
    }

    /**
     * Increment total elements counter (for rate calculation).
     */
    public static void incrementTotalElements() {
        totalElements++;
    }

    /**
     * Increment total elements by a count.
     */
    public static void incrementTotalElements(int count) {
        totalElements += count;
    }

    /**
     * Log an outlier with full details.
     *
     * @param category Type of outlier
     * @param component The element/component that caused the outlier
     * @param context Where in the pipeline this occurred
     * @param actionTaken What the system did to handle it
     * @param guidance What a developer should do to fix permanently
     */
    public static void log(OutlierCategory category,
                          String component,
                          String context,
                          String actionTaken,
                          String guidance) {

        OutlierEntry entry = new OutlierEntry(
            category, component, context, actionTaken, guidance,
            LocalDateTime.now()
        );

        outliers.add(entry);
        categoryCounts.merge(category, 1, Integer::sum);
        componentCounts.merge(component, 1, Integer::sum);

        // Log to console in standard format
        System.err.printf("[OUTLIER:%s] %s | %s | %s%n",
            category, component, context, actionTaken);
        System.err.printf("  → GUIDANCE: %s%n", guidance);
    }

    /**
     * Convenience method for UNKNOWN_SPACETYPE.
     */
    public static void logUnknownSpaceType(String requested, String fallbackUsed) {
        log(OutlierCategory.UNKNOWN_SPACETYPE,
            requested,
            "DSL parsing",
            "Using " + fallbackUsed + " fallback",
            "Add " + requested + " to RoomType enum with fixture/MEP/validation rules");
    }

    /**
     * Convenience method for MISSING_COMPONENT.
     */
    public static void logMissingComponent(String componentName, String roomContext, String action) {
        log(OutlierCategory.MISSING_COMPONENT,
            componentName,
            roomContext,
            action,
            "Add " + componentName + " to component_library.db or create parametric fallback");
    }

    /**
     * Convenience method for UNSATISFIABLE.
     */
    public static void logUnsatisfiable(String conflicts, String context, String action) {
        log(OutlierCategory.UNSATISFIABLE,
            conflicts,
            context,
            action,
            "Review constraints for logical conflicts or relax requirements");
    }

    /**
     * Convenience method for GEOMETRY_IMPOSSIBLE.
     */
    public static void logGeometryImpossible(String fixture, String room, String reason, String dimensions) {
        log(OutlierCategory.GEOMETRY_IMPOSSIBLE,
            fixture,
            "Space: " + room,
            "Skipped - " + reason,
            dimensions);
    }

    /**
     * Convenience method for VALIDATION_UNKNOWN.
     */
    public static void logValidationUnknown(String spaceType, String spaceName) {
        log(OutlierCategory.VALIDATION_UNKNOWN,
            spaceType,
            "Space: " + spaceName,
            "Using GENERIC validation rules",
            "Define validation rules for " + spaceType + " in HabitabilityValidator");
    }

    /**
     * Convenience method for VOCABULARY_GAP.
     */
    public static void logVocabularyGap(String term, String context, String mapped) {
        log(OutlierCategory.VOCABULARY_GAP,
            term,
            context,
            "Mapped to " + mapped,
            "Consider adding \"" + term + "\" as alias or new SpaceType");
    }

    /**
     * Get total count of all outliers.
     */
    public static int getTotalCount() {
        return outliers.size();
    }

    /**
     * Get count for a specific category.
     */
    public static int getCount(OutlierCategory category) {
        return categoryCounts.getOrDefault(category, 0);
    }

    /**
     * Get all outlier entries (for testing).
     */
    public static List<OutlierEntry> getOutliers() {
        return new ArrayList<>(outliers);
    }

    /**
     * Calculate outlier rate as percentage.
     */
    public static double getOutlierRate() {
        if (totalElements == 0) return 0.0;
        return (outliers.size() * 100.0) / totalElements;
    }

    /**
     * Get summary text for console output.
     */
    public static String getSummary() {
        if (outliers.isEmpty()) {
            return "No outliers detected.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== OUTLIER SUMMARY ===\n");
        sb.append(String.format("Compilation: %s%n", compilationName));
        sb.append(String.format("Date: %s%n", LocalDateTime.now().format(TIME_FMT)));
        sb.append("\n");

        // Group by category
        for (OutlierCategory cat : OutlierCategory.values()) {
            int count = categoryCounts.getOrDefault(cat, 0);
            if (count > 0) {
                sb.append(String.format("%s: %d%n", cat, count));

                // List unique components in this category
                Map<String, Integer> componentInCategory = new LinkedHashMap<>();
                for (OutlierEntry entry : outliers) {
                    if (entry.category() == cat) {
                        componentInCategory.merge(entry.component(), 1, Integer::sum);
                    }
                }

                for (Map.Entry<String, Integer> e : componentInCategory.entrySet()) {
                    sb.append(String.format("  - %s (%d occurrence%s)%n",
                        e.getKey(), e.getValue(), e.getValue() > 1 ? "s" : ""));

                    // Find first guidance for this component
                    for (OutlierEntry entry : outliers) {
                        if (entry.category() == cat && entry.component().equals(e.getKey())) {
                            sb.append(String.format("    → GUIDANCE: %s%n", entry.guidance()));
                            break;
                        }
                    }
                }
                sb.append("\n");
            }
        }

        sb.append(String.format("Total: %d outliers%n", outliers.size()));
        if (totalElements > 0) {
            sb.append(String.format("Outlier rate: %d/%d elements = %.1f%%%n",
                outliers.size(), totalElements, getOutlierRate()));
        }

        return sb.toString();
    }

    /**
     * Write full outlier log to file.
     */
    public static void summarize(Path outputPath) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath.toFile()))) {
            writer.println("=== OUTLIER LOG ===");
            writer.printf("Compilation: %s%n", compilationName);
            writer.printf("Generated: %s%n", LocalDateTime.now().format(TIME_FMT));
            writer.println();

            // Write summary first
            writer.println(getSummary());
            writer.println();

            // Write detailed entries
            writer.println("=== DETAILED ENTRIES ===");
            writer.println();

            for (OutlierEntry entry : outliers) {
                writer.printf("[%s] [OUTLIER:%s] %s | %s | %s%n",
                    entry.timestamp().format(TIME_FMT),
                    entry.category(),
                    entry.component(),
                    entry.context(),
                    entry.actionTaken());
                writer.printf("  → GUIDANCE: %s%n", entry.guidance());
                writer.println();
            }

            writer.println("=== END OF LOG ===");

            System.out.println("[OUTLIER] Summary written to: " + outputPath);

        } catch (IOException e) {
            System.err.println("[OUTLIER] Failed to write summary: " + e.getMessage());
        }
    }

    /**
     * Print summary to console.
     */
    public static void printSummary() {
        if (!outliers.isEmpty()) {
            System.out.println();
            System.out.println(getSummary());
        }
    }
}
