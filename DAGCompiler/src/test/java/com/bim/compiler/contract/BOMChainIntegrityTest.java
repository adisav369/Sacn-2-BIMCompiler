package com.bim.compiler.contract;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Detects flat-data cheating in the BOM Orderline chain.
 *
 * <h2>The Anti-Pattern This Test Guards Against</h2>
 * <p>"Flat data at the top level" means storing absolute world coordinates directly in
 * {@code ad_element_rule.position_value}, bypassing the relational chain. The correct
 * pattern is:<br>
 * <pre>
 *   UNIT Orderline    → position_rule='FRACTION', host_type='BUILDING' (top, dynamic)
 *     FLOOR Orderline → position_rule='FRACTION', host_type='UNIT', position_value_3 = storey Z
 *       ROOM anchor   → position_rule='FRACTION', host_type='ROOM', value in [0,1]
 *         BOM child   → dx/dy/dz relative to room centre (ad_bom_child typed columns)
 *           Leaf item → world position COMPUTED from chain, never stored
 * </pre>
 *
 * <p>The failing pattern looks like:<br>
 * <pre>
 *   element_rule ABSOLUTE 5229.8 4559.8   ← raw world coord in Orderline
 *   element_rule FRACTION 0.52  -21501.0  ← second axis is mm, not a fraction
 *   ad_bom_child dx=5229.8               ← world coord leaked into BOM child
 * </pre>
 *
 * <p>These all break the dynamic chain: the compiler can no longer derive world position
 * from the hierarchy — it reads flat numbers and trusts them. Flat numbers are wrong the
 * moment a building is repositioned, mirrored, or composed into a larger assembly.
 *
 * <h2>Chain Depth</h2>
 * <p>The chain is currently live at ROOM level only (UNIT + FLOOR Orderlines are Phase BOM-2c
 * deferred — tracked as known gap). Chain depth = 1 (ROOM only). Target = 3 (UNIT→FLOOR→ROOM).
 * Tests T3a/T3b are ADVISORY until UNIT/FLOOR Orderlines are added.
 *
 * <h2>Assertions</h2>
 * <ul>
 *   <li><b>T1</b> Zero FURN Orderlines use {@code position_rule='ABSOLUTE'} — all relational</li>
 *   <li><b>T2</b> All FURN FRACTION Orderlines: position_value, position_value_2 ∈ [0.0, 1.0]</li>
 *   <li><b>T3</b> Chain depth ≥ 1 (ROOM anchors live) for every building with active FURN</li>
 *   <li><b>T4</b> No ROOM-level FURN anchor has position_value_3 != 0 (storey Z must come from
 *       FLOOR chain, not hardcoded at room level)</li>
 *   <li><b>T5</b> Every BOM referenced by a FURN anchor has ≥ 1 active child (chain continues
 *       past anchor — dead-end BOM = broken chain)</li>
 *   <li><b>T6</b> All active FURN anchors have host_type='ROOM' — no anchor bypasses room layer</li>
 *   <li><b>T7</b> Missing UNIT/FLOOR Orderlines — reported as advisory chain-depth gap
 *       (will become a hard failure once Phase BOM-2c data insertion completes)</li>
 * </ul>
 */
@DisplayName("BOM Chain Integrity — No Flat Data Cheating at Top Level")
class BOMChainIntegrityTest {

    private static final String LIB = "library/component_library.db";
    private static Connection conn;

    @BeforeAll static void open()  throws SQLException { conn = DriverManager.getConnection("jdbc:sqlite:" + LIB); }
    @AfterAll  static void close() throws SQLException { if (conn != null) conn.close(); }

    // ─────────────────────────────────────────────────────────────────────────
    // T1: No FURN Orderline uses ABSOLUTE positioning
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T1: Zero FURN Orderlines use ABSOLUTE position_rule (all must be relational FRACTION)")
    void t1_noFurnAbsolute() throws SQLException {
        String sql = """
            SELECT element_ref, building_type, position_value, position_value_2
            FROM ad_element_rule
            WHERE discipline = 'FURN' AND is_active = 1
              AND position_rule = 'ABSOLUTE'
            """;
        List<String> bad = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(String.format(
                "%s [%s] ABSOLUTE(%.1f, %.1f)",
                rs.getString("element_ref"), rs.getString("building_type"),
                rs.getDouble("position_value"), rs.getDouble("position_value_2")));
        }
        assertTrue(bad.isEmpty(),
            "FURN Orderlines with ABSOLUTE positioning (flat data — bypasses chain): " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T2: All FURN FRACTION Orderlines have position_value and position_value_2 in [0, 1]
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T2: FURN FRACTION Orderlines: position_value and position_value_2 ∈ [0.0, 1.0]")
    void t2_furnFractionInRange() throws SQLException {
        String sql = """
            SELECT element_ref, building_type, position_value, position_value_2
            FROM ad_element_rule
            WHERE discipline = 'FURN' AND is_active = 1
              AND position_rule = 'FRACTION'
              AND (position_value   < 0.0 OR position_value   > 1.0
                OR position_value_2 < 0.0 OR position_value_2 > 1.0)
            """;
        List<String> bad = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(String.format(
                "%s [%s] fractions=(%.4f, %.4f) — not in [0,1], looks like absolute coord",
                rs.getString("element_ref"), rs.getString("building_type"),
                rs.getDouble("position_value"), rs.getDouble("position_value_2")));
        }
        assertTrue(bad.isEmpty(),
            "FURN FRACTION Orderlines with out-of-range position values (flat world coord leaked): " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T3: Chain depth ≥ 1 (ROOM anchors live) for every building with FURN rules
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T3: Every building with active FURN rules has ≥ 1 UNIT Orderline (chain live at UNIT level)")
    void t3_chainDepthRoom() throws SQLException {
        // Phase BOM-2c: ROOM anchors deactivated — check UNIT Orderlines (host_type='BUILDING') instead
        String buildingsSql = "SELECT building_id FROM ad_building_registry WHERE is_active=1";
        String anchorSql = """
            SELECT COUNT(*) FROM ad_element_rule
            WHERE building_type = ? AND discipline = 'FURN'
              AND host_type = 'BUILDING' AND is_active = 1
            """;
        List<String> missing = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet buildings = st.executeQuery(buildingsSql)) {
            while (buildings.next()) {
                String bId = buildings.getString("building_id");
                try (PreparedStatement ps = conn.prepareStatement(anchorSql)) {
                    ps.setString(1, bId);
                    try (ResultSet rs = ps.executeQuery()) {
                        int count = rs.next() ? rs.getInt(1) : 0;
                        // Terminal has no FURN — exempt
                        if (count == 0 && !bId.contains("Terminal") && !bId.contains("terminal")) {
                            missing.add(bId + " (0 UNIT Orderlines)");
                        }
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(),
            "Buildings with zero FURN UNIT Orderlines — BOM-2c migration not applied or chain broken: " + missing);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T4: No ROOM-level FURN anchor has position_value_3 != 0 (storey Z hardcoded)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T4: ROOM-level FURN anchors have position_value_3 = 0 (storey Z must come from FLOOR chain)")
    void t4_noStoreyHardcodedAtRoom() throws SQLException {
        // position_value_3 = storey Z offset. On a ROOM anchor it must be 0 — the storey
        // elevation is declared by the FLOOR Orderline (which is the parent of the room).
        // A non-zero value here means someone hardcoded the storey Z at room level,
        // bypassing the FLOOR chain layer entirely.
        String sql = """
            SELECT element_ref, building_type, position_value_3
            FROM ad_element_rule
            WHERE discipline = 'FURN' AND host_type = 'ROOM' AND is_active = 1
              AND ABS(position_value_3) > 0.001
            """;
        List<String> bad = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(String.format(
                "%s [%s] position_value_3=%.1f (storey offset hardcoded at room level — use FLOOR Orderline)",
                rs.getString("element_ref"), rs.getString("building_type"),
                rs.getDouble("position_value_3")));
        }
        assertTrue(bad.isEmpty(),
            "FURN ROOM anchors with non-zero position_value_3: " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T5: Every BOM referenced by a FURN anchor has ≥ 1 active child
    //     (except known zero-child BOMs — declared in Known Debt)
    // ─────────────────────────────────────────────────────────────────────────

    /** Known-debt BOM IDs with zero children — explicitly accepted, tracked in MEMORY.md. */
    private static final Set<String> ZERO_CHILD_EXEMPTIONS = Set.of("WARDROBE_SET");

    @Test
    @DisplayName("T5: Every BOM referenced by a FURN anchor has ≥ 1 active child (chain continues past anchor)")
    void t5_bomHasChildren() throws SQLException {
        String sql = """
            SELECT family_ref, child_count FROM (
                SELECT DISTINCT er.family_ref,
                       (SELECT COUNT(*) FROM ad_bom_child bc
                         WHERE bc.bom_id = er.family_ref AND bc.is_active = 1) AS child_count
                FROM ad_element_rule er
                WHERE er.discipline = 'FURN' AND er.is_active = 1
                  AND er.family_ref IS NOT NULL
            ) WHERE child_count = 0
            """;
        List<String> bad = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String bomId = rs.getString("family_ref");
                if (!ZERO_CHILD_EXEMPTIONS.contains(bomId)) {
                    bad.add(bomId + " (0 active children — chain terminates at anchor)");
                }
            }
        }
        assertTrue(bad.isEmpty(),
            "FURN anchors pointing to zero-child BOMs (chain dead-ends — add ad_bom_child rows): " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T6: All active FURN anchors have host_type='ROOM' — no anchor bypasses room layer
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T6: All active FURN Orderlines use valid chain host_types (ROOM, BUILDING, or UNIT)")
    void t6_furnHostTypeRoom() throws SQLException {
        // Phase BOM-2c: valid host_types are BUILDING (UNIT Orderline), UNIT (FLOOR Orderline),
        // and ROOM (legacy ROOM anchors — currently deactivated but still valid if present).
        // Any other host_type means a discipline has leaked or a chain layer was bypassed.
        String sql = """
            SELECT element_ref, building_type, host_type
            FROM ad_element_rule
            WHERE discipline = 'FURN' AND is_active = 1
              AND host_type NOT IN ('ROOM', 'BUILDING', 'UNIT')
            """;
        List<String> bad = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(String.format(
                "%s [%s] host_type='%s' (expected ROOM, BUILDING, or UNIT)",
                rs.getString("element_ref"), rs.getString("building_type"),
                rs.getString("host_type")));
        }
        assertTrue(bad.isEmpty(),
            "FURN Orderlines with unexpected host_type — invalid chain layer: " + bad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T7: Advisory — UNIT and FLOOR Orderlines missing (chain depth gap)
    //     Reports the gap without failing. Becomes a hard test when BOM-2c data is inserted.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T7: BOM chain depth = 3 for all buildings (UNIT + FLOOR Orderlines live — Phase BOM-2c complete)")
    void t7_chainDepthAdvisory() throws SQLException {
        // Compute chain depth for each building:
        //   depth=1 → ROOM anchors only (pre-BOM-2c state)
        //   depth=2 → FLOOR Orderlines also present
        //   depth=3 → UNIT Orderlines also present (target state — Phase BOM-2c)
        // Phase BOM-2c: ROOM anchors deactivated; UNIT and FLOOR Orderlines now live.
        // unit_live: family_ref joins to a UNIT BOM (bom_level='UNIT')
        // floor_live: family_ref joins to a FLOOR BOM (bom_level='FLOOR')
        String sql = """
            SELECT er.building_type,
                   SUM(CASE WHEN er.host_type='ROOM'     THEN 1 ELSE 0 END) AS room_live,
                   SUM(CASE WHEN b.bom_level='UNIT'      THEN 1 ELSE 0 END) AS unit_live,
                   SUM(CASE WHEN b.bom_level='FLOOR'     THEN 1 ELSE 0 END) AS floor_live
            FROM ad_element_rule er
            LEFT JOIN ad_bom b ON b.bom_id = er.family_ref
            WHERE er.discipline = 'FURN' AND er.is_active = 1
            GROUP BY er.building_type
            """;
        List<String> gaps = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String bld    = rs.getString("building_type");
                int roomLive  = rs.getInt("room_live");
                int unitLive  = rs.getInt("unit_live");
                int floorLive = rs.getInt("floor_live");
                int depth = 1 + (floorLive > 0 ? 1 : 0) + (unitLive > 0 ? 1 : 0);
                if (depth < 3) {
                    gaps.add(String.format("%s depth=%d/3 (ROOM=%d FLOOR=%d UNIT=%d)",
                        bld, depth, roomLive, floorLive, unitLive));
                }
            }
        }
        assertTrue(gaps.isEmpty(),
            "BOM chain depth < 3 — UNIT/FLOOR Orderlines missing (run migration_BOM2c_unit_orderlines.sql): "
            + gaps);
    }
}
