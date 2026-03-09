package com.bim.compiler.contract;

import com.bim.compiler.dsl.BuildingRegistry;
import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;
import com.bim.compiler.dsl.CompilationPipeline;
import com.bim.compiler.dsl.CompilationPipeline.PipelineResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase ST-1c witness tests — template-driven compilation for ST_SH.
 *
 * <h2>W-ST-1 — Template selects SH owner at GF level</h2>
 * <p>BomTemplateComposer must select a GF-level BOM with owner='SH' for the
 * ST_SH AABB (16867 × 8667 × 3945 mm, num_units=1). This proves the template
 * tree correctly routes single-unit buildings to the SH path.
 * Note: SpatialDigest(ST_SH) != SpatialDigest(Ifc4_SampleHouse) — the EXTRACTED
 * IFC build and the GENERATIVE DSL build produce different element geometries.
 * The template proof is completeness of selection, not digest equality.
 *
 * <h2>W-ST-2 — Composition completeness + expected element count</h2>
 * <p>BomTemplateComposer must return a complete report (0 gaps) and ST_SH must
 * compile to the expected element count (same DSL as SH generative path).
 *
 * <h2>W-ST-3 — CO_EmptySpace reaches CO via template proof</h2>
 * <p>When composition is complete, WriteStage marks co_empty_space CO directly
 * (ProveStage is skipped for ST mode — no relational placement rules exist).
 * This verifies the ST-mode BUILDING BOM lookup via GF owner succeeded.
 *
 * <h2>W-ST-4 — L2 lines present from template selections</h2>
 * <p>ST_SH output DB must have at least one co_empty_space_line with bom_level=2,
 * confirming the CompositionReport leaf nodes were written by populateCoEmptySpace.
 */
@DisplayName("ST-1c Template Pipeline — W-ST witnesses")
class StTemplatePipelineTest {

    private static final String ST_SH_ID  = "ST_SH";
    private static final String ST_SH_DB  = "DAGCompiler/lib/output/st_sh.db";

    private static PipelineResult stShResult;

    @BeforeAll
    static void compileStSh() throws Exception {
        BuildingEntry entry = BuildingRegistry.loadById(ST_SH_ID);
        assertNotNull(entry, "ST_SH must be present in c_order");
        assertTrue(entry.isActive(), "ST_SH must be active (migration_ST1c applied?)");
        stShResult = CompilationPipeline.run(entry);
    }

    @Test
    @DisplayName("W-ST-1: Template selects SH-owner GF BOM for single-unit AABB")
    void st1_templateSelectsSHOwnerAtGF() throws Exception {
        // The CO_EmptySpace L0 line must reference a BUILDING BOM from the SH owner.
        // WriteStage derives the BUILDING BOM using the GF selection's owner from the template.
        // BUILDING_SH_STD has doc_sub_type='SH', bom_category='RE'.
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + ST_SH_DB);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT bom_id FROM co_empty_space_line WHERE bom_level = 0")) {
            assertTrue(rs.next(), "ST_SH must have a bom_level=0 (BUILDING) co_empty_space_line");
            String bomId = rs.getString("bom_id");
            assertNotNull(bomId, "Level-0 bom_id must not be null");
            assertTrue(bomId.startsWith("BUILDING_SH"),
                "Level-0 bom_id must be an SH BUILDING BOM (GF owner = SH), got: " + bomId);
        }
    }

    @Test
    @DisplayName("W-ST-2: Template composition is complete — no gaps for ST_SH AABB")
    void st2_compositionComplete() throws Exception {
        // ST_SH is GENERATIVE (DSL compiler produces more elements than IFC-extracted SH=56).
        // expected_elements=123 is the DSL-compiled count registered in c_order.
        assertEquals(123, stShResult.elementCount(),
            "ST_SH must compile to 123 elements (GENERATIVE DSL-based build, not IFC-extracted)");
        // If TemplateStage had gaps it would log warnings but still continue.
        // Strict gap check: query co_empty_space_line for L2 rows (only written if report.isComplete())
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + ST_SH_DB);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT COUNT(*) FROM co_empty_space_line WHERE bom_level = 2")) {
            assertTrue(rs.next() && rs.getInt(1) > 0,
                "ST_SH must have at least 1 L2 co_empty_space_line (composition must be non-empty)");
        }
    }

    @Test
    @DisplayName("W-ST-3: ST_SH co_empty_space reaches CO status (quality gate passed)")
    void st3_coEmptySpaceQualityGate() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + ST_SH_DB);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT co_emptyspace_id, is_available, doc_status FROM co_empty_space " +
                 "WHERE doc_status = 'CO' AND is_available = 0")) {
            assertTrue(rs.next(),
                "ST_SH co_empty_space must have a proven row (doc_status='CO', is_available=0). " +
                "Check: did ST-mode UN BOM fallback find BUILDING_SH_STD?");
        }
    }

    @Test
    @DisplayName("W-ST-4: ST_SH co_empty_space_line has bom_level=2 rows from template")
    void st4_l2LinesFromTemplate() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + ST_SH_DB);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT bom_id, bom_line_role, capacity_mm FROM co_empty_space_line " +
                 "WHERE bom_level = 2")) {
            int count = 0;
            while (rs.next()) {
                count++;
                assertNotNull(rs.getString("bom_id"), "L2 bom_id must not be null");
                assertNotNull(rs.getString("bom_line_role"), "L2 role (category) must not be null");
            }
            assertTrue(count > 0,
                "ST_SH must have ≥1 bom_level=2 line (template leaf selections). " +
                "Check: did TemplateStage store CompositionReport in ctx?");
        }
    }
}
