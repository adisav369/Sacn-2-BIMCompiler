package com.bim.compiler.contract;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Permanent gate — BOM chain structure invariants that must never regress.
 *
 * <p>Class name: {@code BomChainIntegrityTest} (camelCase Bom) — distinct from
 * {@code BOMChainIntegrityTest} (all-caps BOM, T1-T7 flat-data tests).
 *
 * <p>These tests verify the structural integrity of the BOM DAG in
 * {@code component_library.db}:
 * <ul>
 *   <li><b>R1</b> UNIT root BOMs exist for every residential building</li>
 *   <li><b>R2</b> FLOOR Orderlines are wired (host_type='UNIT') for every building</li>
 *   <li><b>R3</b> Duplex Level 2 FLOOR Orderline declares storey Z = 3000mm</li>
 *   <li><b>R4</b> No dangling FURN family_ref (active FURN anchors must join to ad_bom)</li>
 *   <li><b>R5</b> DAG walk to leaf: no dangling child_bom_id FK; every SET BOM has ≥1 leaf child</li>
 *   <li><b>R6</b> Active ROOM anchors have non-zero dimensions (width_mm, depth_mm)</li>
 *   <li><b>R7</b> ROOM anchors do not hardcode storey Z (position_value_3 = 0 at ROOM level)</li>
 * </ul>
 */
@DisplayName("BOM Chain Integrity — Permanent Structural Gate (R1-R7)")
class BomChainIntegrityTest {

    private static final String LIB = "library/component_library.db";
    private static Connection conn;

    @BeforeAll static void open()  throws SQLException { conn = DriverManager.getConnection("jdbc:sqlite:" + LIB); }
    @AfterAll  static void close() throws SQLException { if (conn != null) conn.close(); }

    // ─────────────────────────────────────────────────────────────────────────
    // R1: UNIT root BOMs — UNIT_SH_STD and UNIT_DUPLEX_STD must exist in ad_bom
    // [EXTRACTED: migration_BOM2c_unit_orderlines.sql — UNIT BOM definitions]
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("R1: UNIT root BOMs for SH and DX exist in ad_bom (chain entrypoints)")
    void unitRootExists() throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM ad_bom
            WHERE bom_id IN ('UNIT_SH_STD', 'UNIT_DUPLEX_STD')
              AND bom_level = 'UNIT' AND is_active = 1
            """;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int count = rs.next() ? rs.getInt(1) : 0;
            assertEquals(2, count,
                "[R1] Expected 2 UNIT root BOMs (UNIT_SH_STD, UNIT_DUPLEX_STD) in ad_bom. "
                + "Found: " + count + ". [EXTRACTED: migration_BOM2c_unit_orderlines.sql]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // R2: FLOOR Orderlines — must exist with host_type='UNIT' (wired to UNIT layer)
    // [EXTRACTED: migration_BOM2c_unit_orderlines.sql — FLOOR Orderlines host_type='UNIT']
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("R2: FLOOR Orderlines (host_type='UNIT') exist — count ≥ 3")
    void floorOrderlinesExist() throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM ad_element_rule
            WHERE discipline = 'FURN' AND host_type = 'UNIT'
              AND position_rule = 'FRACTION' AND is_active = 1
            """;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int count = rs.next() ? rs.getInt(1) : 0;
            assertTrue(count >= 3,
                "[R2] Expected ≥3 FLOOR Orderlines with host_type='UNIT' (SH, DX-L1, DX-L2, TB-LKTN). "
                + "Found: " + count + ". [EXTRACTED: migration_BOM2c_unit_orderlines.sql §FLOOR Orderlines]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // R3: Duplex Level 2 storey Z = 3000mm
    // [EXTRACTED: Ifc2x3_Duplex Rosetta Stone — Level 2 elevation = 3000mm above Level 1]
    // [EXTRACTED: migration_BOM2c_floor_dz.sql]
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("R3: FLOOR_DX_L2_STD.position_value_3 = 3000mm ±1 (Duplex Level 2 storey elevation)")
    void floorDzDeclared() throws SQLException {
        String sql = """
            SELECT position_value_3 FROM ad_element_rule
            WHERE family_ref = 'FLOOR_DX_L2_STD' AND is_active = 1
            LIMIT 1
            """;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(),
                "[R3] No active FLOOR_DX_L2_STD Orderline found. "
                + "Run migration_BOM2c_unit_orderlines.sql. [EXTRACTED: Ifc2x3_Duplex Rosetta Stone]");
            double dz = rs.getDouble("position_value_3");
            assertEquals(3000.0, dz, 1.0,
                "[R3] FLOOR_DX_L2_STD.position_value_3 = " + dz
                + " — expected 3000.0 ±1mm (Duplex Level 2 = 3m above Level 1). "
                + "[EXTRACTED: migration_BOM2c_floor_dz.sql]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // R4: No dangling FURN family_ref — every active FURN anchor must join to ad_bom
    // Exclude discipline != 'FURN' (ARC family_refs = Revit family names, not BOM IDs)
    // [EXTRACTED: MEMORY.md §Critical Traps — ad_element_rule.family_ref is NOT an FK to ad_opening_family]
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("R4: No active FURN anchor has a family_ref absent from ad_bom")
    void noDanglingFamilyRef() throws SQLException {
        String sql = """
            SELECT element_ref, building_type, family_ref
            FROM ad_element_rule
            WHERE discipline = 'FURN' AND is_active = 1
              AND family_ref IS NOT NULL
              AND family_ref NOT IN (SELECT bom_id FROM ad_bom WHERE is_active = 1)
            """;
        List<String> dangling = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) dangling.add(
                rs.getString("element_ref") + " [" + rs.getString("building_type")
                + "] → family_ref='" + rs.getString("family_ref") + "' missing from ad_bom");
        }
        assertTrue(dangling.isEmpty(),
            "[R4] Active FURN Orderlines with family_ref absent from ad_bom (dangling reference): "
            + dangling + ". [EXTRACTED: PREFAB_ARCHITECTURE.md §BOM Drop Positional Chain]");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // R5: Full DAG walk to leaf
    //   (a) No dangling child_bom_id FK in ad_bom_child
    //   (b) Every active SET BOM has ≥1 leaf child (child_ifc_class IS NOT NULL)
    //       Exemption: none currently (WARDROBE_SET deactivated or has children added)
    // [EXTRACTED: BOMChild.isLeaf() — childIfcClass != null means leaf element]
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("R5a: No dangling child_bom_id FK in ad_bom_child")
    void noDanglingChildBomFK() throws SQLException {
        String sql = """
            SELECT bom_child_id, bom_id, child_bom_id
            FROM ad_bom_child
            WHERE is_active = 1
              AND child_bom_id IS NOT NULL
              AND child_bom_id NOT IN (SELECT bom_id FROM ad_bom)
            """;
        List<String> dangling = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) dangling.add(
                "bom_child_id=" + rs.getInt("bom_child_id")
                + " bom=" + rs.getString("bom_id")
                + " → child_bom_id='" + rs.getString("child_bom_id") + "' missing from ad_bom");
        }
        assertTrue(dangling.isEmpty(),
            "[R5a] Dangling child_bom_id FK in ad_bom_child (nested BOM reference broken): "
            + dangling + ". [EXTRACTED: BOMAssemblerAD — child_bom_id FK to ad_bom]");
    }

    @Test
    @DisplayName("R5b: Every active SET BOM has ≥1 leaf child (child_ifc_class IS NOT NULL)")
    void setBomHasLeafChild() throws SQLException {
        String sql = """
            SELECT b.bom_id
            FROM ad_bom b
            WHERE b.bom_level = 'SET' AND b.is_active = 1
              AND (SELECT COUNT(*) FROM ad_bom_child bc
                    WHERE bc.bom_id = b.bom_id AND bc.is_active = 1
                      AND bc.child_ifc_class IS NOT NULL) = 0
            """;
        List<String> empty = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) empty.add(rs.getString("bom_id") + " (0 leaf children)");
        }
        assertTrue(empty.isEmpty(),
            "[R5b] SET BOMs with no leaf children (DAG terminates before reaching element): "
            + empty + ". Add ad_bom_child rows with child_ifc_class. "
            + "[EXTRACTED: BOMChild.isLeaf() — child_ifc_class IS NOT NULL]");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // R6: Active ROOM anchors (if any) must have non-zero width_mm and depth_mm
    // (deactivated ROOM anchors OK to have zeros — they are the pre-BOM-2c rows)
    // [EXTRACTED: RelationalResolver.computeBomAnchor() — uses width_mm, depth_mm for placement bbox]
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("R6: Active ROOM anchors have non-zero width_mm and depth_mm")
    void roomOrderlinesHaveDimensions() throws SQLException {
        String sql = """
            SELECT element_ref, building_type, width_mm, depth_mm
            FROM ad_element_rule
            WHERE discipline = 'FURN' AND host_type = 'ROOM' AND is_active = 1
              AND (width_mm IS NULL OR depth_mm IS NULL
                OR width_mm = 0.0 OR depth_mm = 0.0)
            """;
        List<String> bad = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(String.format(
                "%s [%s] width_mm=%.1f depth_mm=%.1f",
                rs.getString("element_ref"), rs.getString("building_type"),
                rs.getDouble("width_mm"), rs.getDouble("depth_mm")));
        }
        assertTrue(bad.isEmpty(),
            "[R6] Active ROOM anchors with zero dimensions (placement bbox degenerate): "
            + bad + ". [EXTRACTED: RelationalResolver.computeBomAnchor() — dims used for bbox]");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // R7: ROOM anchors must not hardcode storey Z (position_value_3 = 0)
    // Storey Z belongs in FLOOR Orderline (position_value_3 = storey elevation in mm)
    // A non-zero value here means the FLOOR chain layer was bypassed.
    // [EXTRACTED: PREFAB_ARCHITECTURE.md §Three-Table Authority Rule — no flat coords at room level]
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("R7: ROOM anchors have position_value_3 = 0 (storey Z relational, from FLOOR chain)")
    void worldZIsRelational() throws SQLException {
        String sql = """
            SELECT element_ref, building_type, position_value_3
            FROM ad_element_rule
            WHERE discipline = 'FURN' AND host_type = 'ROOM' AND is_active = 1
              AND ABS(position_value_3) > 0.001
            """;
        List<String> bad = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(String.format(
                "%s [%s] position_value_3=%.1f (storey Z hardcoded at room level)",
                rs.getString("element_ref"), rs.getString("building_type"),
                rs.getDouble("position_value_3")));
        }
        assertTrue(bad.isEmpty(),
            "[R7] ROOM anchors with non-zero storey Z (worldZ must come from FLOOR chain, not hardcoded): "
            + bad + ". [EXTRACTED: PREFAB_ARCHITECTURE.md §Relational Placement — FLOOR chain = storey authority]");
    }
}
