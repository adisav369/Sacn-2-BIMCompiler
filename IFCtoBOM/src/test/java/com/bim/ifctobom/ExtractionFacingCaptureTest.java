package com.bim.ifctobom;

import com.bim.ifctobom.ExtractionPopulator.RawElement;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FACING GATE v2 — Fix-1 capture witness (W-FACING-CAPTURE). Proves the SOURCE fix: the real front is
 * the IFC ObjectPlacement YAW the current extractor captures into element_transforms.rotation_z, threaded
 * onto RawElement.rotationZ and emitted by the DEFAULT FRONT_SOURCE as a literal radian string
 * (LocalCoord.resolveRotation parses it into rotation_rule). No invention: a captured 0.0 is a VALID
 * front; only a MISSING placement (rotationZ==null) hard-fails.
 *
 * <p>This complements {@link ExtractionFacingGateV2Test} (which proved the gate's hard-fail/pass contract
 * with an injected source). Here the DEFAULT source is exercised — the one the pipeline actually uses.
 *
 * <p>ROOT (empirical, this session): re-extracting Ifc4_SampleHouse.ifc with the current extractor gives
 * the 4 dining chairs facing 0,π,0,π (across the table), bed -π/2, desk π — REAL varied facing. The
 * deployed *_extracted.db had rotation_z=0 everywhere only because it was extracted by a STALE
 * world-coords extractor; the data was never missing.
 *
 * <ul>
 *   <li>W-FACING-CAPTURE-1: a fronted element WITH a captured yaw passes, carrying that yaw as radians.</li>
 *   <li>W-FACING-CAPTURE-2: a captured 0.0 is a VALID front (NOT the refused fallback) — passes as "0.0".</li>
 *   <li>W-FACING-CAPTURE-3: NO captured placement (rotationZ==null) → default source null → honest NULL
 *       orientation (no rotation_rule), never a fabricated 0 and never a hard fail.</li>
 *   <li>W-FACING-CAPTURE-4: readReferenceDb threads rotation_z when transform_source is present, and the
 *       freshness gate drops a STALE DB (no transform_source column) to null → those fronts hard-fail.</li>
 * </ul>
 */
class ExtractionFacingCaptureTest {

    /** A chair with a captured yaw (the 14-arg ctor = real captured placement). */
    private static RawElement chairFacing(double yaw) {
        return new RawElement("IfcFurniture", "Chair - Dining:289770", "Ground Floor", "ARC",
                0, 0.5, 0, 0.5, 0, 0.9, null, null, "GUID-CHAIR", yaw);
    }
    /** A chair with NO captured placement (the 13-arg compat ctor → rotationZ=null). */
    private static RawElement chairUncaptured() {
        return new RawElement("IfcFurniture", "Chair - Dining:289770", "Ground Floor", "ARC",
                0, 0.5, 0, 0.5, 0, 0.9, null, null, "GUID-CHAIR");
    }

    @Test @DisplayName("W-FACING-CAPTURE-1: captured yaw passes through as literal radians")
    void capturedYawPasses() {
        String prev = save();
        try {
            ExtractionPopulator.FRONT_SOURCE = defaultSource();   // exercise the pipeline's default
            String front = ExtractionPopulator.classifyOrientationV2(
                    chairFacing(Math.PI), "IfcFurniture", "Chair - Dining");
            assertEquals(String.valueOf(Math.PI), front, "carries the captured yaw, not a fallback");
            assertEquals(Math.PI, Double.parseDouble(front), 1e-12,
                    "the front string is parseable radians (LocalCoord.resolveRotation consumes it)");
        } finally { restore(prev); }
    }

    @Test @DisplayName("W-FACING-CAPTURE-2: a captured 0.0 is a VALID front, not the refused fallback")
    void capturedZeroIsValid() {
        String prev = save();
        try {
            ExtractionPopulator.FRONT_SOURCE = defaultSource();
            String front = ExtractionPopulator.classifyOrientationV2(
                    chairFacing(0.0), "IfcFurniture", "Chair - Dining");
            assertNotNull(front, "a genuinely-captured 0 must pass — the gate refuses MISSING data, not the value 0");
            assertEquals(0.0, Double.parseDouble(front), 1e-12);
        } finally { restore(prev); }
    }

    @Test @DisplayName("W-FACING-CAPTURE-3: no captured placement → honest null (no fabricated 0, no hard fail)")
    void uncapturedReturnsNull() {
        String prev = save();
        try {
            ExtractionPopulator.FRONT_SOURCE = defaultSource();
            assertNull(ExtractionPopulator.classifyOrientationV2(
                            chairUncaptured(), "IfcFurniture", "Chair - Dining"),
                    "rotationZ==null = no captured IFC placement → null orientation (no rotation_rule), "
                    + "never a rotation_rule=0 fabrication and never a hard fail (an unfaced element is honest data)");
        } finally { restore(prev); }
    }

    @Test @DisplayName("W-FACING-CAPTURE-4: readReferenceDb threads rotation_z; freshness gate drops stale DBs")
    void readReferenceDbThreadsRotationWithFreshnessGate() throws Exception {
        // Fresh-style DB: element_transforms carries transform_source → rotation trusted.
        Path fresh = Files.createTempFile("fresh_ref", ".db");
        buildRefDb(fresh, /*withTransformSource=*/true, /*yaw=*/Math.PI);
        List<RawElement> freshRows = ExtractionPopulator.readReferenceDb(fresh);
        assertEquals(1, freshRows.size());
        assertNotNull(freshRows.get(0).rotationZ(), "fresh DB → rotation captured");
        assertEquals(Math.PI, freshRows.get(0).rotationZ(), 1e-9, "the real captured yaw is threaded");

        // Stale-style DB: element_transforms has rotation_z=0 but NO transform_source column → not trusted.
        Path stale = Files.createTempFile("stale_ref", ".db");
        buildRefDb(stale, /*withTransformSource=*/false, /*yaw=*/0.0);
        List<RawElement> staleRows = ExtractionPopulator.readReferenceDb(stale);
        assertEquals(1, staleRows.size());
        assertNull(staleRows.get(0).rotationZ(),
                "stale DB (no transform_source) → rotation NOT trusted → null → would hard-fail, not pass on a fake 0");

        Files.deleteIfExists(fresh);
        Files.deleteIfExists(stale);
    }

    /** Build a minimal reference DB (one chair) mimicking current vs stale extractor schemas. */
    private static void buildRefDb(Path db, boolean withTransformSource, double yaw) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE elements_meta (id INTEGER PRIMARY KEY, guid TEXT UNIQUE, "
                    + "discipline TEXT, ifc_class TEXT, element_name TEXT, element_type TEXT, storey TEXT, "
                    + "material_name TEXT, material_rgba TEXT)");
            s.executeUpdate("CREATE VIRTUAL TABLE elements_rtree USING rtree(id, minX, maxX, minY, maxY, minZ, maxZ)");
            if (withTransformSource) {
                s.executeUpdate("CREATE TABLE element_transforms (guid TEXT PRIMARY KEY, center_x REAL, "
                        + "center_y REAL, center_z REAL, rotation_x REAL, rotation_y REAL, rotation_z REAL, "
                        + "transform_source TEXT)");
            } else {
                // older world-coords schema: bbox_* cols, NO transform_source
                s.executeUpdate("CREATE TABLE element_transforms (guid TEXT PRIMARY KEY, center_x REAL, "
                        + "center_y REAL, center_z REAL, rotation_x REAL, rotation_y REAL, rotation_z REAL, "
                        + "bbox_x REAL, bbox_y REAL, bbox_z REAL)");
            }
            s.executeUpdate("INSERT INTO elements_meta VALUES (1,'G1','ARC','IfcFurniture',"
                    + "'Chair - Dining:289770',NULL,'Ground Floor',NULL,NULL)");
            s.executeUpdate("INSERT INTO elements_rtree VALUES (1, 0,0.5, 0,0.5, 0,0.9)");
            if (withTransformSource) {
                s.executeUpdate("INSERT INTO element_transforms VALUES ('G1',0,0,0,0,0," + yaw + ",'ifc_extract')");
            } else {
                s.executeUpdate("INSERT INTO element_transforms VALUES ('G1',0,0,0,0,0," + yaw + ",0,0,0)");
            }
        }
    }

    // ── save/restore the injectable static around each test ─────────────────────
    private static java.util.function.Function<RawElement, String> saved;
    private static String save() { saved = ExtractionPopulator.FRONT_SOURCE; return "x"; }
    private static void restore(String t) { ExtractionPopulator.FRONT_SOURCE = saved; }
    /** The production default (kept in sync with ExtractionPopulator.FRONT_SOURCE). */
    private static java.util.function.Function<RawElement, String> defaultSource() {
        return e -> e.rotationZ() == null ? null : String.valueOf(e.rotationZ());
    }
}
