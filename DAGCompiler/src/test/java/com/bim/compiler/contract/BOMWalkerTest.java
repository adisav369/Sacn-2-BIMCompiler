/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import com.bim.compiler.bom.walker.BOMVisitor;
import com.bim.compiler.bom.walker.BOMWalker;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NORM-3a Phase A witness: BOMWalker fires correct events for known BOMs.
 *
 * <h2>Witness claims</h2>
 * <ul>
 *   <li>W-WALKER-1: BED_SET walk fires exactly 5 onLeaf + 1 onPhantom + 0 onSubAssembly</li>
 *   <li>W-WALKER-2: DINING_SET walk fires exactly 7 onLeaf + 1 onPhantom</li>
 *   <li>W-WALKER-3: FLOOR_SH_GF_STD walk fires at least 1 onSubAssembly (recursive FLOOR→SET)</li>
 *   <li>W-WALKER-4: onSubAssembly + onSubAssemblyComplete counts are always equal (tree is balanced)</li>
 * </ul>
 *
 * Gate: 207 PASS unchanged. BOMWalker is read-only — no output DB changes.
 */
class BOMWalkerTest {

    private static String bomDbPath() { return System.getProperty("bom.db"); }

    /** Simple counting visitor for test assertions. */
    static class CountingVisitor implements BOMVisitor {
        int subAssemblyCount = 0;
        int subAssemblyCompleteCount = 0;
        int leafCount = 0;
        int phantomCount = 0;
        final List<String> leafRoles = new ArrayList<>();
        final List<String> subAssemblyRoles = new ArrayList<>();

        @Override public void onSubAssembly(BOMWalker.NodeContext ctx) {
            subAssemblyCount++;
            if (ctx.role() != null) subAssemblyRoles.add(ctx.role());
        }
        @Override public void onSubAssemblyComplete(BOMWalker.NodeContext ctx) { subAssemblyCompleteCount++; }
        @Override public void onLeaf(BOMWalker.NodeContext ctx) {
            leafCount++;
            if (ctx.role() != null) leafRoles.add(ctx.role());
        }
        @Override public void onPhantom(BOMWalker.NodeContext ctx) { phantomCount++; }

        int totalEvents() { return subAssemblyCount + leafCount + phantomCount; }
    }

    // ── W-WALKER-1: BED_SET ───────────────────────────────────────────────

    @Test
    @DisplayName("W-WALKER-1: BED_SET fires 5 leaf + 1 PHANTOM events")
    void w_walker_1_bed_set() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath())) {
            BOMWalker walker = new BOMWalker(conn);
            CountingVisitor v = new CountingVisitor();
            walker.walk("BED_SET", List.of(v), "SH");

            assertEquals(5, v.leafCount,
                "BED_SET must have 5 leaf children (bed + pillow + mattress + nightstand ×2)");
            assertEquals(1, v.phantomCount,
                "BED_SET must have 1 PHANTOM (buffer filler)");
            assertEquals(0, v.subAssemblyCount,
                "BED_SET is a flat SET — no nested sub-assembly BOMs");
        }
    }

    // ── W-WALKER-2: DINING_SET ────────────────────────────────────────────

    @Test
    @DisplayName("W-WALKER-2: DINING_SET fires 7 leaf + 1 PHANTOM events")
    void w_walker_2_dining_set() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath())) {
            BOMWalker walker = new BOMWalker(conn);
            CountingVisitor v = new CountingVisitor();
            walker.walk("DINING_SET", List.of(v), "SH");

            assertEquals(7, v.leafCount,
                "DINING_SET must have 7 leaf children");
            assertEquals(1, v.phantomCount,
                "DINING_SET must have 1 PHANTOM buffer");
            assertEquals(0, v.subAssemblyCount,
                "DINING_SET is flat — no nested sub-assemblies");
        }
    }

    // ── W-WALKER-3: FLOOR_SH_GF_STD has nested MAKE ──────────────────────

    @Test
    @DisplayName("W-WALKER-3: FLOOR_SH_GF_STD fires at least 1 onSubAssembly (recursive FLOOR→SET)")
    void w_walker_3_floor_has_subassembly() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath())) {
            BOMWalker walker = new BOMWalker(conn);
            CountingVisitor v = new CountingVisitor();
            walker.walk("FLOOR_SH_GF_STD", List.of(v), "SH");

            assertTrue(v.subAssemblyCount > 0,
                "FLOOR_SH_GF_STD must fire onSubAssembly for each SET child");
            assertTrue(v.leafCount > 0,
                "FLOOR_SH_GF_STD must recursively reach leaf nodes through SET children");
        }
    }

    // ── W-WALKER-4: make/makeComplete balanced ────────────────────────────

    @Test
    @DisplayName("W-WALKER-4: onSubAssembly and onSubAssemblyComplete counts always equal")
    void w_walker_4_subassembly_balanced() throws Exception {
        String[] bomsToCheck = {"FLOOR_SH_GF_STD", "FLOOR_DX_L1_STD", "DINING_SET", "BED_SET"};

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath())) {
            BOMWalker walker = new BOMWalker(conn);
            for (String bomId : bomsToCheck) {
                CountingVisitor v = new CountingVisitor();
                walker.walk(bomId, List.of(v), "SH");
                assertEquals(v.subAssemblyCount, v.subAssemblyCompleteCount,
                    bomId + ": onSubAssembly (" + v.subAssemblyCount + ") != onSubAssemblyComplete (" +
                    v.subAssemblyCompleteCount + ") — unbalanced tree walk");
            }
        }
    }

    // ── W-WALKER-5: multiple visitors receive identical event counts ───────

    @Test
    @DisplayName("W-WALKER-5: two visitors on BED_SET receive identical event counts")
    void w_walker_5_multi_visitor() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath())) {
            BOMWalker walker = new BOMWalker(conn);
            CountingVisitor v1 = new CountingVisitor();
            CountingVisitor v2 = new CountingVisitor();
            walker.walk("BED_SET", List.of(v1, v2), "SH");

            assertEquals(v1.leafCount, v2.leafCount, "Both visitors must see same leaf count");
            assertEquals(v1.phantomCount, v2.phantomCount, "Both visitors must see same PHANTOM count");
            assertEquals(v1.subAssemblyCount, v2.subAssemblyCount, "Both visitors must see same sub-assembly count");
        }
    }
}
