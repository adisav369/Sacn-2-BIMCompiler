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
}
