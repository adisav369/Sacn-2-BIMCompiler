package com.bim.compiler.contract;

import com.bim.compiler.dsl.BuildingRegistry;
import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;
import com.bim.compiler.dsl.CompilationPipeline;
import com.bim.compiler.dsl.CompilationPipeline.PipelineResult;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * HW-6: WALK THRU (_e) compilation gate — catches WT_ BOM regressions.
 *
 * <p>Sets {@code bom.mode=WALKTHRU}, compiles SH via the structured BOM
 * hierarchy (WT_SH → storey sub-BOMs → BUY), and verifies the output
 * matches the reference. DX is deferred (needs MIRROR verb).
 *
 * <p>Runs in surefire stage 2 alongside other contract tests. For SH,
 * _s and _e produce identical output (same elements, same positions)
 * because WT_SH storey sub-BOMs were populated from EB_SH data.
 *
 * <h2>Witness claims</h2>
 * <ul>
 *   <li>W-WT-1: WALKTHRU compilation of SH produces 55 elements</li>
 *   <li>W-WT-2: WALKTHRU element count matches reference</li>
 *   <li>W-WT-3: WALKTHRU AABB volume matches reference (±0.1%)</li>
 * </ul>
 */
class WalkThruCompilationTest {

    private String previousMode;

    @BeforeEach
    void setWalkThruMode() {
        previousMode = System.getProperty("bom.mode");
        System.setProperty("bom.mode", "WALKTHRU");
    }

    @AfterEach
    void restoreMode() {
        if (previousMode != null) {
            System.setProperty("bom.mode", previousMode);
        } else {
            System.clearProperty("bom.mode");
        }
    }

    private BuildingEntry findSH() {
        return BuildingRegistry.loadActive().stream()
            .filter(b -> "RE_SH".equals(b.docTypeId()))
            .findFirst()
            .orElse(null);
    }

    // ── W-WT-1: WALKTHRU SH = 55 elements ───────────────────────────

    @Test
    @DisplayName("W-WT-1: WALKTHRU compilation of SH produces 55 elements")
    void w_wt_1_sh_count() throws Exception {
        BuildingEntry sh = findSH();
        assumeTrue(sh != null, "RE_SH not found in C_DocType");

        PipelineResult result = CompilationPipeline.run(sh);

        assertEquals(55, result.elementCount(),
            "WALKTHRU SH must produce exactly 55 elements");
    }

    // ── W-WT-2: WALKTHRU count matches reference ────────────────────

    @Test
    @DisplayName("W-WT-2: WALKTHRU element count matches reference")
    void w_wt_2_reference_count() throws Exception {
        BuildingEntry sh = findSH();
        assumeTrue(sh != null, "RE_SH not found in C_DocType");
        assumeTrue(sh.hasReference(), "RE_SH has no reference DB");

        PipelineResult result = CompilationPipeline.run(sh);

        int refCount;
        try (Connection conn = DriverManager.getConnection(
                "jdbc:sqlite:" + sh.referenceDbPath());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM elements_meta")) {
            refCount = rs.next() ? rs.getInt(1) : 0;
        }

        assertEquals(refCount, result.elementCount(),
            "WALKTHRU SH element count must match reference");
    }

    // ── W-WT-3: WALKTHRU volume matches reference ───────────────────

    @Test
    @DisplayName("W-WT-3: WALKTHRU AABB volume matches reference (±0.1%)")
    void w_wt_3_volume() throws Exception {
        BuildingEntry sh = findSH();
        assumeTrue(sh != null, "RE_SH not found in C_DocType");
        assumeTrue(sh.hasReference(), "RE_SH has no reference DB");

        CompilationPipeline.run(sh);

        double refVol = totalVolume(sh.referenceDbPath());
        double outVol = totalVolume(sh.outputDbPath());
        double deltaPct = (refVol > 0) ? ((outVol - refVol) / refVol) * 100.0 : 0.0;

        assertTrue(Math.abs(deltaPct) <= 0.1,
            String.format("WALKTHRU SH volume drift %.2f%% exceeds ±0.1%% (ref=%.2f out=%.2f)",
                deltaPct, refVol, outVol));
    }

    private static double totalVolume(String dbPath) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT COALESCE(SUM((r.maxX-r.minX)*(r.maxY-r.minY)*(r.maxZ-r.minZ)), 0) "
                 + "FROM elements_rtree r JOIN elements_meta m ON r.id = m.id")) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        }
    }
}
