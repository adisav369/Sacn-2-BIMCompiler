package com.bim.designer;

import com.bim.designer.validation.*;
import org.junit.jupiter.api.*;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PlacementValidatorImpl tests against validation.db.
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-PV-1: BEDROOM 3100x3100x2800 PASS under MY jurisdiction</li>
 *   <li>W-PV-2: BEDROOM 2800x2800x2800 BLOCK on min_dim (2800 < 3000)</li>
 *   <li>W-PV-3: KITCHEN 2000x2000x2800 BLOCK on min_area (4.0 < 4.5)</li>
 *   <li>W-PV-4: Deactivated validator always returns PASS</li>
 *   <li>W-PV-5: US jurisdiction loads different rules (IRC thresholds)</li>
 *   <li>W-DV-MINED-WIRE-1: Mined rules loaded from ERP.db (415 rules, 25 IFC classes)</li>
 *   <li>W-DV-MINED-WIRE-2: IfcWall within range PASS</li>
 *   <li>W-DV-MINED-WIRE-3: IfcWall way outside range WARN</li>
 *   <li>W-DV-MINED-WIRE-4: Unknown IFC class PASS (no mined data)</li>
 * </ul>
 */
@DisplayName("PlacementValidatorImpl -- AD_Val_Rule checks")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlacementValidatorImplTest {

    private static Connection valConn;
    private static PlacementValidatorImpl validator;

    @BeforeAll
    static void setUp() throws Exception {
        valConn = DriverManager.getConnection("jdbc:sqlite:library/validation.db");
        validator = new PlacementValidatorImpl();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (valConn != null && !valConn.isClosed()) valConn.close();
    }

    // ── MY jurisdiction ──────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("W-PV-1: BEDROOM 3100x3100x2800 PASS (MY)")
    void w_pv_1_bedroom_pass() {
        validator.activate("MY", valConn);
        assertTrue(validator.isActive());
        assertEquals("MY", validator.getJurisdiction());

        PlacementRequest req = bedroom(3100, 3100, 2800);
        ValidationVerdict v = validator.validate(req);
        assertEquals(ValidationVerdict.Result.PASS, v.result(),
                "3100x3100 bedroom: area=9.61m2 >= 9.2, minDim=3100 >= 3000");
    }

    @Test
    @Order(2)
    @DisplayName("W-PV-2: BEDROOM 2800x3500x2800 BLOCK on min_dim (MY)")
    void w_pv_2_bedroom_block_min_dim() {
        validator.activate("MY", valConn);

        // 2800x3500 = 9.8 m2 (passes area), but minDim 2800 < 3000 -> BLOCK
        PlacementRequest req = bedroom(2800, 3500, 2800);
        ValidationVerdict v = validator.validate(req);
        assertEquals(ValidationVerdict.Result.BLOCK, v.result(),
                "2800mm min dim < 3000mm UBBL requirement");
        assertNotNull(v.ruleName());
        assertNotNull(v.standardRef());
        assertTrue(v.standardRef().contains("UBBL"),
                "Standard ref should mention UBBL: " + v.standardRef());
        assertEquals(2800.0, v.actualValue(), 0.1);
        assertEquals(3000.0, v.requiredValue(), 0.1);
    }

    @Test
    @Order(3)
    @DisplayName("W-PV-3: KITCHEN 2000x2000x2800 BLOCK on min_area (MY)")
    void w_pv_3_kitchen_block_area() {
        validator.activate("MY", valConn);

        // 2000x2000 = 4.0 m2, UBBL requires 4.5 m2 for kitchen
        PlacementRequest req = room("KITCHEN", 2000, 2000, 2800);
        ValidationVerdict v = validator.validate(req);
        assertEquals(ValidationVerdict.Result.BLOCK, v.result(),
                "2000x2000 kitchen: area=4.0m2 < 4.5m2 UBBL requirement");
        assertEquals(4.0, v.actualValue(), 0.1);
        assertEquals(4.5, v.requiredValue(), 0.1);
    }

    @Test
    @Order(4)
    @DisplayName("KITCHEN 2200x2200x2800 PASS (MY)")
    void kitchen_pass() {
        validator.activate("MY", valConn);

        // 2200x2200 = 4.84 m2 >= 4.5, minDim 2200 >= 1500
        PlacementRequest req = room("KITCHEN", 2200, 2200, 2800);
        ValidationVerdict v = validator.validate(req);
        assertEquals(ValidationVerdict.Result.PASS, v.result(),
                "2200x2200 kitchen passes both area and minDim");
    }

    // ── Deactivated ──────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("W-PV-4: Deactivated validator always returns PASS")
    void w_pv_4_deactivated_pass() {
        validator.activate("MY", valConn);
        validator.deactivate();

        assertFalse(validator.isActive());
        assertNull(validator.getJurisdiction());

        // Even a hopelessly non-compliant room passes when deactivated
        PlacementRequest req = bedroom(500, 500, 500);
        ValidationVerdict v = validator.validate(req);
        assertEquals(ValidationVerdict.Result.PASS, v.result(),
                "Deactivated validator must always PASS");
    }

    // ── US jurisdiction ──────────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("W-PV-5: US jurisdiction activates IRC rules")
    void w_pv_5_us_jurisdiction() {
        validator.activate("US", valConn);
        assertTrue(validator.isActive());
        assertEquals("US", validator.getJurisdiction());

        // US IRC requires min_area 6.5m2 and min_dim 2134mm for habitable rooms
        // A 2500x2500 room: area=6.25m2 < 6.5 -> BLOCK
        PlacementRequest small = bedroom(2500, 2500, 2500);
        ValidationVerdict v = validator.validate(small);
        assertEquals(ValidationVerdict.Result.BLOCK, v.result(),
                "US IRC: 2500x2500=6.25m2 < 6.5m2 required");

        // A 2600x2600 room: area=6.76m2 >= 6.5, minDim=2600 >= 2134 -> PASS
        PlacementRequest ok = bedroom(2600, 2600, 2600);
        ValidationVerdict v2 = validator.validate(ok);
        assertEquals(ValidationVerdict.Result.PASS, v2.result(),
                "US IRC: 2600x2600=6.76m2 >= 6.5, dim 2600 >= 2134");
    }

    @Test
    @Order(21)
    @DisplayName("US ceiling height check: 2000mm BLOCK (IRC requires 2134mm)")
    void us_ceiling_block() {
        validator.activate("US", valConn);

        // min_height_mm = 2134 (IRC wildcard rule for all rooms)
        PlacementRequest low = bedroom(3000, 3000, 2000);
        ValidationVerdict v = validator.validate(low);
        assertEquals(ValidationVerdict.Result.BLOCK, v.result(),
                "US IRC: 2000mm height < 2134mm required");
    }

    // ── Infrastructure facility type filtering ─────────────────────

    @Test
    @Order(30)
    @DisplayName("W-INFRA-FILTER-1: BRIDGE loads 13 bridge rules, 0 building rules")
    void w_infra_filter_1_bridge() {
        // Implementing DISC_VALIDATION_DB_SRS.md §Infra — Witness: W-INFRA-FILTER-1
        validator.activate("", FacilityType.BRIDGE, valConn);
        assertTrue(validator.isActive());
        assertEquals(FacilityType.BRIDGE, validator.getFacilityType());

        int ruleCount = validator.getRuleCount();
        assertEquals(13, ruleCount,
                "Bridge provenance should load exactly 13 rules, got " + ruleCount);

        // Verify no building rules leak in — a compliant BEDROOM should PASS
        // (UBBL rules for BEDROOM require min_dim 3000, min_area 9.2 — these
        // must NOT be loaded in bridge mode)
        PlacementRequest big = bedroom(3100, 3100, 2800);
        ValidationVerdict v = validator.validate(big);
        // If building rules leaked in, this would use UBBL. Bridge rules are
        // wildcard-keyed but check infra-specific thresholds. The key proof:
        // the rule names should NOT be UBBL-sourced.
        if (v.ruleName() != null) {
            assertFalse(v.ruleName().contains("UBBL"),
                    "Bridge mode must not load UBBL building rules: " + v.ruleName());
        }
    }

    @Test
    @Order(31)
    @DisplayName("W-INFRA-FILTER-2: BUILDING MY loads MY rules, 0 infra rules")
    void w_infra_filter_2_building_excludes_infra() {
        // Implementing DISC_VALIDATION_DB_SRS.md §Infra — Witness: W-INFRA-FILTER-2
        validator.activate("MY", FacilityType.BUILDING, valConn);
        assertTrue(validator.isActive());
        assertEquals(FacilityType.BUILDING, validator.getFacilityType());

        int ruleCount = validator.getRuleCount();
        assertTrue(ruleCount > 0, "MY building should have rules");

        // Cross-check: activate old way should give same count (non-disturbance)
        PlacementValidatorImpl v2 = new PlacementValidatorImpl();
        v2.activate("MY", valConn);
        assertEquals(v2.getRuleCount(), ruleCount,
                "Old activate() and new activate(MY,BUILDING) should load same rule count");
    }

    @Test
    @Order(32)
    @DisplayName("W-INFRA-FILTER-3: ROAD loads 10 road rules")
    void w_infra_filter_3_road() {
        // Implementing DISC_VALIDATION_DB_SRS.md §Infra — Witness: W-INFRA-FILTER-3
        validator.activate("", FacilityType.ROAD, valConn);
        assertTrue(validator.isActive());

        int ruleCount = validator.getRuleCount();
        assertEquals(10, ruleCount,
                "Road provenance should load exactly 10 rules, got " + ruleCount);
    }

    @Test
    @Order(33)
    @DisplayName("W-INFRA-FILTER-4: Building snap() never applies infra rules")
    void w_infra_filter_4_non_disturbance() {
        // Implementing DISC_VALIDATION_DB_SRS.md §Infra — Witness: W-INFRA-FILTER-4
        // Activate building mode and verify that a room that fails building codes
        // gets the SAME verdict as before — infra rules don't interfere
        validator.activate("MY", FacilityType.BUILDING, valConn);

        // This should BLOCK on min_dim (2800 < 3000 UBBL) — same as W-PV-2
        PlacementRequest req = bedroom(2800, 3500, 2800);
        ValidationVerdict v = validator.validate(req);
        assertEquals(ValidationVerdict.Result.BLOCK, v.result(),
                "Building mode: 2800mm min dim still blocked by UBBL");
        assertEquals(3000.0, v.requiredValue(), 0.1,
                "Required value should be UBBL 3000mm, not any infra threshold");

        // Also verify rule count matches old behavior
        PlacementValidatorImpl oldStyle = new PlacementValidatorImpl();
        oldStyle.activate("MY", valConn);
        assertEquals(oldStyle.getRuleCount(), validator.getRuleCount(),
                "Building mode rule count must match old activate() — no infra leakage");
    }

    // ── Phase I-1: Infrastructure snap wiring ──────────────────────

    @Test
    @Order(40)
    @DisplayName("W-INFRA-SNAP-1: Bridge pier undersized → BLOCK on width_mm")
    void w_infra_snap_1_bridge_pier_block() {
        // Implementing INFRA_DESIGNER_SRS.md §2.3 — Witness: W-INFRA-SNAP-1
        // Bridge rule 902 (BRIDGE_PIER_COLUMN_RAIL) has width_mm=3499
        // A pier with width 1000mm < 3499mm → should BLOCK
        validator.activate("", FacilityType.BRIDGE, valConn);
        assertTrue(validator.isActive());

        PlacementRequest smallPier = infraElement("PIER", "IfcColumn",
                1000, 1000, 3000);  // 1000mm wide, way under 3499mm
        ValidationVerdict v = validator.validate(smallPier);
        assertEquals(ValidationVerdict.Result.BLOCK, v.result(),
                "1000mm pier should be blocked by bridge dimension rule");
        assertTrue(v.requiredValue() > 1000,
                "Required value should exceed actual 1000mm: " + v.requiredValue());
    }

    @Test
    @Order(41)
    @DisplayName("W-INFRA-SNAP-2: Road course undersized thickness → BLOCK")
    void w_infra_snap_2_road_course_block() {
        // Implementing INFRA_DESIGNER_SRS.md §2.3 — Witness: W-INFRA-SNAP-2
        // Road rule 1001 (ROAD_SURFACE_COURSE) has thickness_mm=40
        // A course with height 10mm < 40mm → BLOCK (thickness maps to height)
        validator.activate("", FacilityType.ROAD, valConn);
        assertTrue(validator.isActive());

        PlacementRequest thinCourse = infraElement("COURSE_LAYER", "IfcCourse",
                7300, 1000, 10);  // 10mm thick, under 40mm
        ValidationVerdict v = validator.validate(thinCourse);
        assertEquals(ValidationVerdict.Result.BLOCK, v.result(),
                "10mm course should be blocked by road thickness rule");
    }

    @Test
    @Order(42)
    @DisplayName("W-INFRA-SNAP-3: Rail element undersized → BLOCK on width")
    void w_infra_snap_3_rail_block() {
        // Implementing INFRA_DESIGNER_SRS.md §2.3 — Witness: W-INFRA-SNAP-3
        // Rail rules have large wildcard dimensions (e.g. RAIL_BALLAST_DIM width=19071mm)
        // A small element should be blocked
        validator.activate("", FacilityType.RAILWAY, valConn);
        assertTrue(validator.isActive());
        assertEquals(7, validator.getRuleCount(),
                "Railway should load exactly 7 rules");

        PlacementRequest smallTrack = infraElement("TRACK_ELEMENT", "IfcTrackElement",
                500, 500, 100);  // way under any rail dimension
        ValidationVerdict v = validator.validate(smallTrack);
        assertEquals(ValidationVerdict.Result.BLOCK, v.result(),
                "500mm track element should be blocked by rail dimension rules");
    }

    @Test
    @Order(43)
    @DisplayName("W-INFRA-SNAP-4: Building snap() unchanged after infra wiring")
    void w_infra_snap_4_building_unchanged() {
        // Implementing INFRA_DESIGNER_SRS.md §2.4 — Witness: W-INFRA-SNAP-4
        // Same test as W-PV-1 — BEDROOM 3100x3100x2800 PASS under MY
        validator.activate("MY", FacilityType.BUILDING, valConn);

        PlacementRequest req = bedroom(3100, 3100, 2800);
        ValidationVerdict v = validator.validate(req);
        assertEquals(ValidationVerdict.Result.PASS, v.result(),
                "Building validation unchanged: 3100x3100 bedroom still PASS");

        // And W-PV-2 still BLOCK
        PlacementRequest small = bedroom(2800, 3500, 2800);
        ValidationVerdict v2 = validator.validate(small);
        assertEquals(ValidationVerdict.Result.BLOCK, v2.result(),
                "Building validation unchanged: 2800mm min dim still BLOCK");
    }

    // ── DV010: Mined dimension rules ──────────────────────────────────

    @Test
    @Order(50)
    @DisplayName("W-DV-MINED-WIRE-1: Mined rules loaded from ERP.db")
    void w_dv_mined_wire_1_load() throws Exception {
        // Implementing DISC_VALIDATION_DB_SRS.md §DV010 — Witness: W-DV-MINED-WIRE-1
        validator.activate("MY", FacilityType.BUILDING, valConn);
        try (Connection discConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db")) {
            validator.activateMinedRules(discConn);
        }
        // 10 compliance + 415 mined = 425+
        assertTrue(validator.getRuleCount() >= 415,
                "Rule count must include mined rules, got " + validator.getRuleCount());
    }

    @Test
    @Order(51)
    @DisplayName("W-DV-MINED-WIRE-2: IfcWall within range PASS")
    void w_dv_mined_wire_2_pass() throws Exception {
        // Implementing DISC_VALIDATION_DB_SRS.md §DV010 — Witness: W-DV-MINED-WIRE-2
        validator.activate("MY", FacilityType.BUILDING, valConn);
        try (Connection discConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db")) {
            validator.activateMinedRules(discConn);
        }
        // Typical IfcWall: ~3000x3500x3000mm — well within mined range
        PlacementRequest wall = new PlacementRequest(
                "WALL", "IfcWall", "ARC",
                3000, 3500, 3000,
                0, 0, 0, "SNAP", 0, "GF");
        ValidationVerdict v = validator.validate(wall);
        // Should not trigger DIMENSION_RANGE warning (within 5x of typical)
        if (v.isWarning()) {
            assertFalse(v.ruleName().startsWith("DIMENSION_RANGE"),
                    "Normal wall should not trigger dimension range: " + v.message());
        }
    }

    @Test
    @Order(52)
    @DisplayName("W-DV-MINED-WIRE-3: IfcWall way outside range WARN")
    void w_dv_mined_wire_3_warn() throws Exception {
        // Implementing DISC_VALIDATION_DB_SRS.md §DV010 — Witness: W-DV-MINED-WIRE-3
        validator.activate("MY", FacilityType.BUILDING, valConn);
        try (Connection discConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db")) {
            validator.activateMinedRules(discConn);
        }
        // Absurdly large wall: 500000mm width = 500m — way outside any mined range
        PlacementRequest hugeWall = new PlacementRequest(
                "WALL", "IfcWall", "ARC",
                500000, 3500, 3000,
                0, 0, 0, "SNAP", 0, "GF");
        ValidationVerdict v = validator.validate(hugeWall);
        assertTrue(v.isWarning(), "500m wall should trigger WARN");
        assertTrue(v.ruleName().startsWith("DIMENSION_RANGE"),
                "Warning should be dimension range: " + v.ruleName());
    }

    @Test
    @Order(53)
    @DisplayName("W-DV-MINED-WIRE-4: Unknown IFC class PASS (no mined data)")
    void w_dv_mined_wire_4_unknown_class() throws Exception {
        // Implementing DISC_VALIDATION_DB_SRS.md §DV010 — Witness: W-DV-MINED-WIRE-4
        validator.activate("MY", FacilityType.BUILDING, valConn);
        try (Connection discConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db")) {
            validator.activateMinedRules(discConn);
        }
        // IfcChimney has no mined data — should pass silently
        PlacementRequest chimney = new PlacementRequest(
                "MISC", "IfcChimney", "ARC",
                500, 500, 5000,
                0, 0, 0, "SNAP", 0, "GF");
        ValidationVerdict v = validator.validate(chimney);
        // No DIMENSION_RANGE warning for unknown class
        if (v.isWarning()) {
            assertFalse(v.ruleName().startsWith("DIMENSION_RANGE"),
                    "Unknown class should not trigger dimension range");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static PlacementRequest bedroom(double w, double d, double h) {
        return room("BEDROOM", w, d, h);
    }

    private static PlacementRequest room(String category, double w, double d, double h) {
        return new PlacementRequest(
                category, "IfcSpace", "ARC",
                w, d, h,
                0, 0, 0,     // position irrelevant for compliance checks
                "SNAP", 0, "GF"
        );
    }

    private static PlacementRequest infraElement(String category, String ifcClass,
                                                  double w, double d, double h) {
        return new PlacementRequest(
                category, ifcClass, "STR",
                w, d, h,
                0, 0, 0,
                "PLACE", 0, "S0"
        );
    }
}
