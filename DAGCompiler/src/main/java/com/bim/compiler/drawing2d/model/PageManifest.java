/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.drawing2d.model;

import com.bim.compiler.drawing2d.model.DrawingScope.StoreyInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Auto-generated list of pages a building should produce.
 *
 * Built by ScopeStage from {@link DrawingScope} — replaces the hand-crafted
 * {PREFIX}_2D.json manifests. The generation rules encode standard
 * architectural drawing set conventions (TB-LKTN / JKR).
 *
 * Sheet numbering convention:
 *   A-01 through A-nn  = Architectural (plans, elevations, sections, schedules)
 *   E-01 through E-nn  = Electrical
 *   M-01 through M-nn  = Mechanical / Plumbing
 *   S-01 through S-nn  = Structural
 */
public record PageManifest(List<PageEntry> pages) {

    public int size() { return pages.size(); }

    /** Storey title prefixes (ordinal → display prefix). */
    private static final Map<String, String> STOREY_TITLE_PREFIX = Map.of(
        "GF", "GROUND FL",
        "FF", "FIRST FL",
        "2F", "SECOND FL",
        "3F", "THIRD FL"
    );

    /**
     * Generate the page manifest from detected scope.
     *
     * Rules:
     * - FLOOR_PLAN: one per habitable storey (multi-storey gets GF/FF/2F suffix)
     * - 4 elevations: always (FRONT, REAR, LEFT, RIGHT)
     * - ROOF_PLAN: if roof elements detected
     * - PLUMBING_PLAN: one per storey if PLMB discipline present
     * - ELECT_PLAN: one per storey if ELEC discipline present
     * - SECTION: if mesh data available
     * - OPENING_SCHEDULE: always (pure DB query)
     * - REFL_CEILING: always (inverse of floor plan)
     */
    public static PageManifest generate(DrawingScope scope) {
        List<PageEntry> pages = new ArrayList<>();
        int archSheet = 1;
        boolean multiStorey = scope.storeyCount() > 1;

        // Floor plans — one per storey
        for (StoreyInfo si : scope.storeys()) {
            String suffix = multiStorey ? "-" + si.code() : "";
            String sheetNo = "A-" + pad(archSheet) + suffix;
            String title = multiStorey
                ? storeyPrefix(si.code()) + " FLOOR PLAN"
                : "FLOOR PLAN";
            pages.add(new PageEntry("FLOOR_PLAN", "PLAN", "ARC",
                si.name(), si.floorZ(), sheetNo, title));
        }
        archSheet++;

        // Elevations — always 4
        for (String face : List.of("FRONT", "REAR", "LEFT", "RIGHT")) {
            String sheetNo = "A-" + pad(archSheet++);
            pages.add(new PageEntry(face + "_ELEV", "ELEVATION", "ARC",
                null, Double.NaN, sheetNo, face + " ELEVATION"));
        }

        // Roof plan
        if (scope.canProduceRoofPlan()) {
            String sheetNo = "A-" + pad(archSheet++);
            pages.add(new PageEntry("ROOF_PLAN", "ROOF_PLAN", "ARC",
                null, Double.NaN, sheetNo, "ROOF PLAN"));
        }

        // Section
        if (scope.canProduceSection()) {
            String sheetNo = "A-" + pad(archSheet++);
            pages.add(new PageEntry("SECTION_AA", "SECTION", "ARC",
                null, Double.NaN, sheetNo, "SECTION A-A"));
        }

        // Opening schedule
        if (scope.canProduceSchedule()) {
            String sheetNo = "A-" + pad(archSheet++);
            pages.add(new PageEntry("OPENING_SCHED", "SCHEDULE", "ARC",
                null, Double.NaN, sheetNo, "OPENING SCHEDULE"));
        }

        // Reflected ceiling — one per storey
        for (StoreyInfo si : scope.storeys()) {
            String suffix = multiStorey ? "-" + si.code() : "";
            String sheetNo = "A-" + pad(archSheet++) + suffix;
            String title = multiStorey
                ? storeyPrefix(si.code()) + " REFLECTED CEILING PLAN"
                : "REFLECTED CEILING PLAN";
            pages.add(new PageEntry("REFL_CEILING", "PLAN", "ARC",
                si.name(), si.floorZ(), sheetNo, title));
        }

        // Plumbing plans — one per storey if discipline detected
        if (scope.canProduceMep("PLMB")) {
            int mepSheet = 1;
            for (StoreyInfo si : scope.storeys()) {
                String suffix = multiStorey ? "-" + si.code() : "";
                String sheetNo = "M-" + pad(mepSheet++) + suffix;
                String title = multiStorey
                    ? storeyPrefix(si.code()) + " PLUMBING PLAN"
                    : "PLUMBING PLAN";
                pages.add(new PageEntry("PLUMBING_PLAN", "PLAN", "PLMB",
                    si.name(), si.floorZ(), sheetNo, title));
            }
        }

        // Electrical plans — one per storey if discipline detected
        if (scope.canProduceMep("ELEC")) {
            int elecSheet = 1;
            for (StoreyInfo si : scope.storeys()) {
                String suffix = multiStorey ? "-" + si.code() : "";
                String sheetNo = "E-" + pad(elecSheet++) + suffix;
                String title = multiStorey
                    ? storeyPrefix(si.code()) + " ELECTRICAL PLAN"
                    : "ELECTRICAL PLAN";
                pages.add(new PageEntry("ELECT_PLAN", "PLAN", "ELEC",
                    si.name(), si.floorZ(), sheetNo, title));
            }
        }

        return new PageManifest(List.copyOf(pages));
    }

    private static String pad(int n) {
        return String.format("%02d", n);
    }

    private static String storeyPrefix(String code) {
        return STOREY_TITLE_PREFIX.getOrDefault(code, code);
    }
}
