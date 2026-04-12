package com.bim.compiler.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extracted Geometry Truth Test — the moment-of-truth fidelity gate.
 *
 * <p>Class-agnostic, name-agnostic. Compares compiled output against IFC reference
 * using only bounding boxes. No names, no IFC class matching — pure geometry.
 *
 * <p>Three tiers, each stricter than the last:
 * <ol>
 *   <li><b>T1 — Element Count:</b> total element count must match (class-agnostic).</li>
 *   <li><b>T2 — Volume Conservation:</b> sum of all AABB volumes must match within 0.1%.
 *       Same amount of material, even if positions differ.</li>
 *   <li><b>T3-ARC — Placement Match (ARC/STC only):</b> 1:1 AABB matching (1mm tolerance)
 *       for ARC/STC elements only. Every reference bbox has a compiled partner and vice versa.
 *       If T3-ARC passes, visual output for structural/architectural elements is identical.
 *       Rendering is redundant.</li>
 *   <li><b>T3-DISC-COUNT — DISC device count:</b> count-only check for MEP/discipline devices.
 *       Position is governed by validation rules, not IFC survey. Route coverage is confirmed
 *       by GEO forensic logs (bim.geo.debug=true), not by this test.</li>
 * </ol>
 *
 * <p>Hash total arithmetic: T2 sums volumes as integers (mm³), T3-ARC matches AABBs as
 * 6-tuples of integer mm. No SHA256 — the numbers ARE the proof.
 *
 * <p>Standalone: no BuildingRegistry, no SpatialDigest, no PlacementProver.
 * Two SQLite DBs in, truth out.
 *
 * @Traces AUDIT_20260402.txt §3 — discipline-split fidelity
 */
// Implementing AUDIT_20260402.txt §3 — Witness: W-BB-ARC-T3, W-BB-DISC-T1, W-BB-DISC-T3-SKIP
@DisplayName("Extracted Geometry Truth — discipline-split placement fidelity")
class ExtractedGeometryTruthTest {

    private static final String[][] BUILDINGS = {
        {"SH", "DAGCompiler/lib/output/samplehouse.db",  "DAGCompiler/lib/input/SampleHouse_extracted.db"},
        {"DX", "DAGCompiler/lib/output/duplex.db",      "DAGCompiler/lib/input/Duplex_extracted.db"},
    };

    /**
     * ARC/STC IFC classes — position fidelity required (T3-ARC).
     * These elements obey IFC survey coordinates. Their AABBs must match the reference exactly.
     *
     * <p>W-BB-ARC-T3: ARC/STC elements in reference match output AABB ±1mm (T3 position fidelity)
     */
    private static final Set<String> ARC_STC_CLASSES = Set.of(
        "IfcWall", "IfcWallStandardCase", "IfcSlab", "IfcRoof", "IfcColumn", "IfcBeam",
        "IfcMember", "IfcPlate", "IfcWindow", "IfcDoor", "IfcStair", "IfcStairFlight",
        "IfcRamp", "IfcRampFlight", "IfcCovering", "IfcFurnishingElement",
        "IfcSpace", "IfcBuildingElementProxy", "IfcFooting", "IfcPile"
    );

    /**
     * DISC IFC classes — count-only check (T3-DISC-COUNT), position not required.
     * These devices obey validation rules and routing topology, not IFC survey positions.
     * A sprinkler head may be placed lengthwise instead of breadthwise across a hall ceiling.
     *
     * <p>W-BB-DISC-T1: DISC elements in reference match output COUNT only (position not required)
     * <p>W-BB-DISC-T3-SKIP: DISC T3 is explicitly skipped with explanation comment
     */
    private static final Set<String> DISC_CLASSES = Set.of(
        "IfcPipeSegment", "IfcPipeFitting", "IfcDuctSegment", "IfcDuctFitting",
        "IfcCableSegment", "IfcCableFitting", "IfcFlowTerminal", "IfcFlowController",
        "IfcAirTerminal", "IfcFireSuppressionTerminal", "IfcLightFixture",
        "IfcElectricDistributionBoard", "IfcSensor", "IfcAlarm"
    );

    // =====================================================================
    // T1 — Element Count (class-agnostic)
    // =====================================================================

    @TestFactory
    @DisplayName("T1: Element count — reference = compiled")
    Collection<DynamicTest> t1_elementCount() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String[] b : BUILDINGS) {
            tests.add(DynamicTest.dynamicTest(b[0] + ": element count", () -> {
                int ref = totalCount(b[2]);
                int compiled = totalCount(b[1]);
                assertEquals(ref, compiled,
                    String.format("[%s] T1 element count: ref=%d compiled=%d (delta=%+d)",
                        b[0], ref, compiled, compiled - ref));
            }));
        }
        return tests;
    }

    // =====================================================================
    // T2 — Volume Conservation (hash total of AABB volumes)
    // =====================================================================

    @TestFactory
    @DisplayName("T2: Volume conservation — total AABB volume within 0.1%")
    Collection<DynamicTest> t2_volumeConservation() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String[] b : BUILDINGS) {
            tests.add(DynamicTest.dynamicTest(b[0] + ": volume conservation", () -> {
                long refVol = totalVolumeMm3(b[2]);
                long compiledVol = totalVolumeMm3(b[1]);
                double pctDiff = Math.abs(compiledVol - refVol) * 100.0 / Math.max(refVol, 1);
                assertTrue(pctDiff < 0.1,
                    String.format("[%s] T2 volume: ref=%,d mm³ compiled=%,d mm³ (%.3f%% diff)",
                        b[0], refVol, compiledVol, pctDiff));
            }));
        }
        return tests;
    }

    // =====================================================================
    // T3-ARC — Placement Match for ARC/STC classes only (1:1 AABB, 1mm tolerance)
    // W-BB-ARC-T3: ARC/STC elements in reference match output AABB ±1mm
    // =====================================================================

    @TestFactory
    @DisplayName("T3-ARC: ARC/STC placement match — every bbox has a partner (1mm)")
    Collection<DynamicTest> t3arc_placementMatch() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String[] b : BUILDINGS) {
            tests.add(DynamicTest.dynamicTest(b[0] + ": ARC/STC placement match", () -> {
                // Implementing AUDIT_20260402.txt §3 — Witness: W-BB-ARC-T3
                Map<String, Integer> refBoxes = bboxMultisetArcStc(b[2]);
                Map<String, Integer> compiledBoxes = bboxMultisetArcStc(b[1]);

                List<String> missing = new ArrayList<>();   // in ref but not compiled
                List<String> phantom = new ArrayList<>();   // in compiled but not ref
                int matched = 0;

                Set<String> allKeys = new TreeSet<>(refBoxes.keySet());
                allKeys.addAll(compiledBoxes.keySet());

                for (String key : allKeys) {
                    int rc = refBoxes.getOrDefault(key, 0);
                    int cc = compiledBoxes.getOrDefault(key, 0);
                    int common = Math.min(rc, cc);
                    matched += common;
                    for (int i = 0; i < rc - common; i++) missing.add(key);
                    for (int i = 0; i < cc - common; i++) phantom.add(key);
                }

                int refTotal = refBoxes.values().stream().mapToInt(Integer::intValue).sum();
                int compiledTotal = compiledBoxes.values().stream().mapToInt(Integer::intValue).sum();

                // Build failure report
                StringBuilder report = new StringBuilder();
                report.append(String.format("[%s] T3-ARC placement: %d/%d matched, %d missing, %d phantom%n",
                    b[0], matched, refTotal, missing.size(), phantom.size()));

                if (!missing.isEmpty()) {
                    report.append("  MISSING (in reference, not in compiled):\n");
                    for (String m : missing.subList(0, Math.min(20, missing.size()))) {
                        report.append("    ").append(m).append('\n');
                    }
                    if (missing.size() > 20) report.append("    ... and ")
                        .append(missing.size() - 20).append(" more\n");
                }
                if (!phantom.isEmpty()) {
                    report.append("  PHANTOM (in compiled, not in reference):\n");
                    for (String p : phantom.subList(0, Math.min(20, phantom.size()))) {
                        report.append("    ").append(p).append('\n');
                    }
                    if (phantom.size() > 20) report.append("    ... and ")
                        .append(phantom.size() - 20).append(" more\n");
                }

                assertTrue(missing.isEmpty() && phantom.isEmpty(), report.toString());
            }));
        }
        return tests;
    }

    // =====================================================================
    // T3-DISC-COUNT — DISC device count match (position NOT checked)
    // W-BB-DISC-T1: DISC elements in reference match output COUNT only
    // W-BB-DISC-T3-SKIP: DISC T3 position check is explicitly skipped
    // =====================================================================

    @TestFactory
    @DisplayName("T3-DISC: DISC device count match — no position check")
    Collection<DynamicTest> t3disc_countMatch() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String[] b : BUILDINGS) {
            tests.add(DynamicTest.dynamicTest(b[0] + ": DISC device count", () -> {
                // Implementing AUDIT_20260402.txt §3 — Witness: W-BB-DISC-T1, W-BB-DISC-T3-SKIP
                // Position is governed by validation rules, not IFC survey. Route coverage
                // is confirmed by GEO forensic logs (bim.geo.debug=true), not by this test.
                int refCount = discCount(b[2]);
                int compiledCount = discCount(b[1]);
                assertEquals(refCount, compiledCount,
                    String.format("[%s] T3-DISC count: ref=%d compiled=%d (delta=%+d)",
                        b[0], refCount, compiledCount, compiledCount - refCount));
            }));
        }
        return tests;
    }

    // =====================================================================
    // DB queries — pure geometry, no names
    // =====================================================================

    /** Total element count (class-agnostic). */
    private static int totalCount(String dbPath) {
        String sql = "SELECT COUNT(*) FROM elements_meta em JOIN elements_rtree r ON em.id = r.id";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("totalCount failed: " + dbPath, e);
        }
    }

    /** Hash total: sum of all AABB volumes in mm³. */
    private static long totalVolumeMm3(String dbPath) {
        String sql = """
            SELECT SUM(
                CAST(ROUND((r.maxX - r.minX) * 1000) AS INTEGER)
              * CAST(ROUND((r.maxY - r.minY) * 1000) AS INTEGER)
              * CAST(ROUND((r.maxZ - r.minZ) * 1000) AS INTEGER)
            )
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            """;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("totalVolumeMm3 failed: " + dbPath, e);
        }
    }

    /**
     * All AABBs as a multiset of "minX|maxX|minY|maxY|minZ|maxZ" keys (integer mm),
     * filtered to ARC/STC classes only.
     *
     * <p>Only includes elements whose ifc_class is in {@link #ARC_STC_CLASSES}.
     * DISC devices are excluded — they are checked by count only in T3-DISC-COUNT.
     */
    private static Map<String, Integer> bboxMultisetArcStc(String dbPath) {
        String inClause = buildInClause(ARC_STC_CLASSES);
        String sql = """
            SELECT r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            WHERE em.ifc_class IN (""" + inClause + ")";
        Map<String, Integer> multiset = new HashMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String key = String.format("%d|%d|%d|%d|%d|%d",
                    Math.round(rs.getDouble(1) * 1000),
                    Math.round(rs.getDouble(2) * 1000),
                    Math.round(rs.getDouble(3) * 1000),
                    Math.round(rs.getDouble(4) * 1000),
                    Math.round(rs.getDouble(5) * 1000),
                    Math.round(rs.getDouble(6) * 1000));
                multiset.merge(key, 1, Integer::sum);
            }
        } catch (SQLException e) {
            throw new RuntimeException("bboxMultisetArcStc failed: " + dbPath, e);
        }
        return multiset;
    }

    /**
     * Count of DISC elements (class-agnostic position — count only).
     *
     * <p>Only includes elements whose ifc_class is in {@link #DISC_CLASSES}.
     */
    private static int discCount(String dbPath) {
        String inClause = buildInClause(DISC_CLASSES);
        String sql = "SELECT COUNT(*) FROM elements_meta em JOIN elements_rtree r ON em.id = r.id"
            + " WHERE em.ifc_class IN (" + inClause + ")";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("discCount failed: " + dbPath, e);
        }
    }

    /** Build a SQL IN clause from a Set of strings (single-quoted, comma-separated). */
    private static String buildInClause(Set<String> classes) {
        StringBuilder sb = new StringBuilder();
        for (String cls : classes) {
            if (sb.length() > 0) sb.append(',');
            sb.append('\'').append(cls).append('\'');
        }
        return sb.toString();
    }
}
