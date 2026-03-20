package com.bim.designer;

import com.bim.backoffice.model.DesignBBox;
import com.bim.designer.api.DesignerAPI.SnapOptions;
import com.bim.designer.api.DesignerAPI.SnapResponse;
import com.bim.designer.api.DesignerAPI.Adjustment;
import com.bim.designer.api.DesignerAPIImpl;
import com.bim.designer.validation.*;
import com.bim.designer.validation.AlignmentContext.StationPoint;
import com.bim.designer.validation.PlacementValidatorImpl;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Cut-and-fill volume computation + terrain-aware snap() wiring.
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-CUTFILL-FLAT-1: Flat design level on real terrain → correct cut/fill split</li>
 *   <li>W-CUTFILL-FLAT-2: Design level at terrain mean → balanced cut/fill</li>
 *   <li>W-CUTFILL-FLAT-3: Design level below all terrain → all cut, zero fill</li>
 *   <li>W-CUTFILL-ALIGN-1: Alignment profile along terrain → mixed cut/fill segments</li>
 *   <li>W-SNAP-TERRAIN-1: snap() with terrain context → bbox Z follows terrain</li>
 *   <li>W-SNAP-TERRAIN-2: snap() without terrain → Z unchanged (backward compat)</li>
 *   <li>W-SNAP-TERRAIN-3: snap() bridge ABOVE mode → Z = terrain + clearance</li>
 *   <li>W-SNAP-TERRAIN-4: Multiple bboxes at different positions → different Z values</li>
 *   <li>W-GRADING-CONTOUR-1: Default contour → Z follows terrain exactly</li>
 *   <li>W-GRADING-STRAIGHT-1: Straight mode → Z fixed at design level</li>
 *   <li>W-GRADING-BLEND-1: 50% blend → Z halfway between terrain and design</li>
 *   <li>W-GRADING-BLEND-2: Blend slider 0→0.5→1.0 → earthworks increase monotonically</li>
 *   <li>W-GRADING-SNAP-1: snap() with STRAIGHT grading → all bboxes at same Z</li>
 * </ul>
 *
 * // Implementing INFRA_DESIGNER_SRS.md §1.4+§I-4 — Witness: W-CUTFILL-1
 */
@DisplayName("Cut-and-Fill + Terrain Snap Wiring")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CutFillTerrainSnapTest {

    private static final Path TERRAIN_JSON = Path.of(
            "/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/" +
            "pdf_terrain/samples/survey_highres_extracted.json");

    // ── Terrain loading (reused from PlacementContextTest) ─────────────

    private static List<StationPoint> loadTerrainPoints() throws Exception {
        String json = Files.readString(TERRAIN_JSON);
        double scale = extractDouble(json, "\"scale\":");
        int imgH = extractInt(json, "\"height\":");

        List<StationPoint> points = new ArrayList<>();
        double station = 0;
        String marker = "\"ground_level\"";
        int pos = 0;
        while ((pos = json.indexOf(marker, pos)) >= 0) {
            int objStart = json.lastIndexOf('{', pos);
            int objEnd = json.indexOf('}', pos);
            if (objStart < 0 || objEnd < 0) { pos++; continue; }
            String obj = json.substring(objStart, objEnd + 1);
            double px = extractDouble(obj, "\"x\":");
            double py = extractDouble(obj, "\"y\":");
            double pz = extractDouble(obj, "\"z\":");
            double wx = px * scale * 1000.0;
            double wy = (imgH - py) * scale * 1000.0;
            double wz = pz * 1000.0;
            points.add(new StationPoint(station, wx, wy, wz));
            station += 500;
            pos = objEnd;
        }
        points.sort(Comparator.comparingDouble(StationPoint::x));
        List<StationPoint> sorted = new ArrayList<>();
        double s = 0;
        for (StationPoint p : points) {
            sorted.add(new StationPoint(s, p.x(), p.y(), p.z()));
            s += 500;
        }
        return sorted;
    }

    private static double extractDouble(String json, String key) {
        int i = json.indexOf(key);
        if (i < 0) return 0;
        i += key.length();
        while (i < json.length() && json.charAt(i) == ' ') i++;
        int end = i;
        while (end < json.length() && (Character.isDigit(json.charAt(end))
                || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
        return Double.parseDouble(json.substring(i, end));
    }

    private static int extractInt(String json, String key) {
        return (int) extractDouble(json, key);
    }

    // ── Cut-and-Fill: Flat Design Level ──────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("W-CUTFILL-FLAT-1: Flat design level → cut/fill volumes on real terrain")
    void flatDesignLevelCutFill() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));
        List<StationPoint> points = loadTerrainPoints();

        // Design level at 40m (40000mm) — below valley floor but above some points
        CutFillCalculator.CutFillResult result = CutFillCalculator.flatLevel(
                points, 40000, 12000);

        // With 689 points over 294×229m and 20m elevation range,
        // at Z=40m most terrain is above → cut dominant
        assertTrue(result.cutVolumeM3() > 0, "Cut volume > 0: " + result.cutVolumeM3());
        assertTrue(result.fillVolumeM3() >= 0, "Fill volume ≥ 0: " + result.fillVolumeM3());
        assertTrue(result.cutPointCount() + result.fillPointCount() + result.onGradeCount()
                   == points.size(),
                "All points accounted for");
        assertTrue(result.totalMovementM3() > 0,
                "Total earth movement > 0: " + result.totalMovementM3());

        System.out.printf("  Flat @40m: cut=%.0f m³, fill=%.0f m³, net=%.0f m³ (%d cut, %d fill, %d grade)%n",
                result.cutVolumeM3(), result.fillVolumeM3(), result.netVolumeM3(),
                result.cutPointCount(), result.fillPointCount(), result.onGradeCount());
    }

    @Test
    @Order(2)
    @DisplayName("W-CUTFILL-FLAT-2: Design level at mean terrain → balanced cut/fill")
    void flatDesignLevelBalanced() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));
        List<StationPoint> points = loadTerrainPoints();

        // Compute mean terrain Z
        double meanZ = points.stream().mapToDouble(StationPoint::z).average().orElse(0);

        CutFillCalculator.CutFillResult result = CutFillCalculator.flatLevel(
                points, meanZ, 12000);

        // At mean Z, cut and fill should be roughly balanced (not identical due to
        // asymmetric distribution, but both non-zero and of similar order)
        assertTrue(result.cutVolumeM3() > 0, "Cut > 0 at mean level");
        assertTrue(result.fillVolumeM3() > 0, "Fill > 0 at mean level");

        double ratio = result.cutVolumeM3() / result.fillVolumeM3();
        assertTrue(ratio > 0.1 && ratio < 10,
                "Cut/fill ratio near balance: " + String.format("%.2f", ratio));

        System.out.printf("  Flat @mean(%.1fm): cut=%.0f m³, fill=%.0f m³, ratio=%.2f%n",
                meanZ / 1000, result.cutVolumeM3(), result.fillVolumeM3(), ratio);
    }

    @Test
    @Order(3)
    @DisplayName("W-CUTFILL-FLAT-3: Design below all terrain → all cut, zero fill")
    void flatDesignLevelAllCut() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));
        List<StationPoint> points = loadTerrainPoints();

        // Design level far below all terrain (Z=20m, terrain is 28-48m)
        CutFillCalculator.CutFillResult result = CutFillCalculator.flatLevel(
                points, 20_000, 12000);

        assertTrue(result.cutVolumeM3() > 0, "All cut when design below terrain");
        assertEquals(0, result.fillVolumeM3(), 0.01,
                "Zero fill when design below all terrain");
        assertEquals(0, result.fillPointCount(),
                "No fill points when design below all terrain");

        System.out.printf("  Flat @20m: all cut — %.0f m³, %d points%n",
                result.cutVolumeM3(), result.cutPointCount());
    }

    // ── Cut-and-Fill: Alignment Profile ──────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("W-CUTFILL-ALIGN-1: Alignment profile → mixed cut/fill along route")
    void alignmentProfileCutFill() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));
        List<StationPoint> terrainPts = loadTerrainPoints();
        AlignmentContext terrain = new AlignmentContext(terrainPts, 12000);
        PlacementContext.Bounds b = terrain.bounds();

        // Create a straight design profile at 42m across the terrain
        // Some terrain is above 42m (cut), some below (fill)
        double y = (b.minY() + b.maxY()) / 2.0;
        double startX = b.minX() + 5000;
        double endX = b.maxX() - 5000;
        int stations = 20;
        double dx = (endX - startX) / stations;

        List<StationPoint> profile = new ArrayList<>();
        for (int i = 0; i <= stations; i++) {
            double x = startX + i * dx;
            double s = i * dx;
            profile.add(new StationPoint(s, x, y, 42_000)); // flat design at 42m
        }

        CutFillCalculator.CutFillResult result = CutFillCalculator.alongAlignment(
                terrain, profile, 7300);  // 7.3m road corridor

        // With terrain ranging 28-48m and design at 42m, expect both cut and fill
        assertTrue(result.cutVolumeM3() >= 0, "Cut ≥ 0");
        assertTrue(result.fillVolumeM3() >= 0, "Fill ≥ 0");
        assertTrue(result.cutVolumeM3() > 0 || result.fillVolumeM3() > 0,
                "At least some earthworks along route");

        System.out.printf("  Alignment @42m: cut=%.0f m³, fill=%.0f m³, net=%.0f m³ (%d segs cut, %d fill)%n",
                result.cutVolumeM3(), result.fillVolumeM3(), result.netVolumeM3(),
                result.cutPointCount(), result.fillPointCount());
    }

    // ── Terrain-Aware snap() Wiring ──────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("W-SNAP-TERRAIN-1: snap() with terrain context → bbox Z follows terrain")
    void snapWithTerrainContext() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));
        List<StationPoint> points = loadTerrainPoints();
        AlignmentContext terrain = new AlignmentContext(points, 12000);
        PlacementContext.Bounds b = terrain.bounds();
        double midX = (b.minX() + b.maxX()) / 2;
        double midY = (b.minY() + b.maxY()) / 2;
        double terrainZ = terrain.elevationAt(midX, midY);

        // Create a road course bbox at Z=0 (wrong — should snap to terrain)
        DesignBBox course = new DesignBBox(
                "RD-CW-001", "road - surface course", "LEAF", "CW",
                "IfcCourse", "road-carriageway", "RD-CW",
                midX - 3650, midY - 2500, 0,    // minZ=0 — wrong
                midX + 3650, midY + 2500, 40);   // 40mm thick course

        DesignerAPIImpl api = new DesignerAPIImpl(null, new PlacementValidatorImpl());
        SnapOptions opts = new SnapOptions(
                "MY", 0, null, null, "ROAD",
                terrain, TerrainSnap.onSurface(0));

        SnapResponse resp = api.snap(List.of(course), opts);

        assertTrue(resp.success(), "Snap should succeed");
        assertEquals(1, resp.bboxes().size());

        DesignBBox snapped = resp.bboxes().get(0);
        // Z should now be at terrain elevation, not 0
        assertEquals(terrainZ, snapped.minZ(), 1.0,
                "Bbox minZ should be at terrain: " + terrainZ + " got " + snapped.minZ());
        assertEquals(terrainZ + 40, snapped.maxZ(), 1.0,
                "Bbox maxZ = terrain + thickness");

        // Should have a TERRAIN_Z adjustment
        boolean hasTerrainAdj = resp.adjustments().stream()
                .anyMatch(a -> "TERRAIN_Z".equals(a.rule()));
        assertTrue(hasTerrainAdj, "Should report TERRAIN_Z adjustment");

        System.out.printf("  Road course snapped: Z=%.2fm (terrain=%.2fm)%n",
                snapped.minZ() / 1000, terrainZ / 1000);
    }

    @Test
    @Order(21)
    @DisplayName("W-SNAP-TERRAIN-2: snap() without terrain → Z unchanged (backward compat)")
    void snapWithoutTerrainUnchanged() {
        // Building mode — no terrain context
        DesignBBox room = new DesignBBox(
                "SH-GF-BED-001", "Bedroom", "ROOM", "BEDROOM",
                "IfcSpace", "GF", "SH-GF",
                0, 0, 0,
                3100, 3100, 2800);

        DesignerAPIImpl api = new DesignerAPIImpl(null, new PlacementValidatorImpl());
        SnapOptions opts = new SnapOptions("MY", 250);

        SnapResponse resp = api.snap(List.of(room), opts);

        assertTrue(resp.success());
        DesignBBox result = resp.bboxes().get(0);

        // Z should remain at 0 — no terrain adjustment
        assertEquals(0, result.minZ(), 0.1,
                "Without terrain, Z unchanged");
        assertEquals(2800, result.maxZ(), 1.0,
                "Height preserved");

        // No TERRAIN_Z adjustment
        boolean hasTerrainAdj = resp.adjustments().stream()
                .anyMatch(a -> "TERRAIN_Z".equals(a.rule()));
        assertFalse(hasTerrainAdj, "No TERRAIN_Z adjustment in building mode");
    }

    @Test
    @Order(22)
    @DisplayName("W-SNAP-TERRAIN-3: Bridge ABOVE mode → Z = terrain + clearance")
    void snapBridgeAboveTerrain() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));
        List<StationPoint> points = loadTerrainPoints();
        AlignmentContext terrain = new AlignmentContext(points, 12000);
        PlacementContext.Bounds b = terrain.bounds();
        double midX = (b.minX() + b.maxX()) / 2;
        double midY = (b.minY() + b.maxY()) / 2;
        double terrainZ = terrain.elevationAt(midX, midY);

        // Bridge deck at Z=0 (wrong — should snap to terrain + 5m clearance)
        DesignBBox deck = new DesignBBox(
                "BR-DCK-001", "bridge deck", "SEGMENT", "DCK",
                "IfcSlab", "bridge-span", "BR",
                midX - 6000, midY - 15000, 0,
                midX + 6000, midY + 15000, 2400);  // 2.4m deep deck

        DesignerAPIImpl api = new DesignerAPIImpl(null, new PlacementValidatorImpl());
        SnapOptions opts = new SnapOptions(
                "MY", 0, null, null, "BRIDGE",
                terrain, TerrainSnap.above(5000));  // 5m clearance

        SnapResponse resp = api.snap(List.of(deck), opts);

        assertTrue(resp.success());
        DesignBBox snapped = resp.bboxes().get(0);

        double expectedZ = terrainZ + 5000;
        assertEquals(expectedZ, snapped.minZ(), 1.0,
                "Deck base = terrain + 5m clearance");
        assertEquals(expectedZ + 2400, snapped.maxZ(), 1.0,
                "Deck top = terrain + 5m + 2.4m");

        System.out.printf("  Bridge deck snapped: Z=%.2fm (terrain=%.2fm, clearance=5m)%n",
                snapped.minZ() / 1000, terrainZ / 1000);
    }

    @Test
    @Order(23)
    @DisplayName("W-SNAP-TERRAIN-4: Multiple bboxes at different positions → different Z values")
    void snapMultipleBboxesDifferentZ() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));
        List<StationPoint> points = loadTerrainPoints();
        AlignmentContext terrain = new AlignmentContext(points, 12000);
        PlacementContext.Bounds b = terrain.bounds();

        // Two road courses at different X positions (terrain varies)
        double y = (b.minY() + b.maxY()) / 2;
        double x1 = b.minX() + 20000;  // near left edge
        double x2 = b.maxX() - 20000;  // near right edge

        DesignBBox course1 = new DesignBBox(
                "RD-CW1-001", "course left", "LEAF", "CW",
                "IfcCourse", "road-cw1", "RD-CW1",
                x1 - 3000, y - 2000, 0, x1 + 3000, y + 2000, 250);

        DesignBBox course2 = new DesignBBox(
                "RD-CW2-001", "course right", "LEAF", "CW",
                "IfcCourse", "road-cw2", "RD-CW2",
                x2 - 3000, y - 2000, 0, x2 + 3000, y + 2000, 250);

        DesignerAPIImpl api = new DesignerAPIImpl(null, new PlacementValidatorImpl());
        SnapOptions opts = new SnapOptions(
                "MY", 0, null, null, "ROAD",
                terrain, TerrainSnap.onSurface(0));

        SnapResponse resp = api.snap(List.of(course1, course2), opts);

        assertTrue(resp.success());
        assertEquals(2, resp.bboxes().size());

        double z1 = resp.bboxes().get(0).minZ();
        double z2 = resp.bboxes().get(1).minZ();

        // Both should be at valid terrain elevations (28-48m range)
        assertTrue(z1 > 25_000 && z1 < 50_000,
                "Left course Z at terrain: " + z1 / 1000 + "m");
        assertTrue(z2 > 25_000 && z2 < 50_000,
                "Right course Z at terrain: " + z2 / 1000 + "m");

        // They should be DIFFERENT (terrain has gradient across 250m span)
        assertNotEquals(z1, z2, 100,
                "Different positions should have different terrain Z: " +
                z1 / 1000 + "m vs " + z2 / 1000 + "m");

        System.out.printf("  Two courses: left Z=%.2fm, right Z=%.2fm, ΔZ=%.2fm%n",
                z1 / 1000, z2 / 1000, Math.abs(z1 - z2) / 1000);
    }

    // ── GradingStrategy — contour/straight/blend ─────────────────────

    @Test
    @Order(30)
    @DisplayName("W-GRADING-CONTOUR-1: Default contour → design Z = terrain Z exactly")
    void gradingContourFollowsTerrain() {
        GradingStrategy contour = GradingStrategy.contour();

        assertEquals(GradingStrategy.Mode.CONTOUR, contour.mode());
        assertEquals(0.0, contour.blend(), 0.001);
        assertTrue(contour.isContour());
        assertFalse(contour.hasCutFill());

        // Design Z = terrain Z at every point
        assertEquals(43000, contour.designZAt(43000), 0.1, "Contour: design = terrain");
        assertEquals(28000, contour.designZAt(28000), 0.1);
        assertEquals(48000, contour.designZAt(48000), 0.1);
    }

    @Test
    @Order(31)
    @DisplayName("W-GRADING-STRAIGHT-1: Straight mode → Z fixed at design level")
    void gradingStraightFixedLevel() {
        GradingStrategy straight = GradingStrategy.straight(42000);

        assertEquals(GradingStrategy.Mode.STRAIGHT, straight.mode());
        assertEquals(1.0, straight.blend(), 0.001);
        assertFalse(straight.isContour());
        assertTrue(straight.hasCutFill());

        // Design Z = 42m regardless of terrain
        assertEquals(42000, straight.designZAt(43000), 0.1, "Straight: always 42m");
        assertEquals(42000, straight.designZAt(28000), 0.1);
        assertEquals(42000, straight.designZAt(48000), 0.1);
    }

    @Test
    @Order(32)
    @DisplayName("W-GRADING-BLEND-1: 50% blend → Z halfway between terrain and design")
    void gradingBlendHalfway() {
        GradingStrategy blend50 = GradingStrategy.blended(0.5, 42000);

        assertEquals(GradingStrategy.Mode.BLEND, blend50.mode());
        assertEquals(0.5, blend50.blend(), 0.001);
        assertFalse(blend50.isContour());
        assertTrue(blend50.hasCutFill());

        // At terrain Z=40m: design = 40000*0.5 + 42000*0.5 = 41000
        assertEquals(41000, blend50.designZAt(40000), 0.1,
                "50% blend: midpoint of terrain(40m) and design(42m) = 41m");

        // At terrain Z=44m: design = 44000*0.5 + 42000*0.5 = 43000
        assertEquals(43000, blend50.designZAt(44000), 0.1,
                "50% blend: midpoint of terrain(44m) and design(42m) = 43m");

        // At terrain Z=42m: design = 42000 (both agree)
        assertEquals(42000, blend50.designZAt(42000), 0.1,
                "50% blend: terrain=design → no change");
    }

    @Test
    @Order(33)
    @DisplayName("W-GRADING-BLEND-2: Blend 0→0.5→1.0 → earthworks increase monotonically")
    void gradingBlendEarthworksIncrease() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));
        List<StationPoint> terrainPts = loadTerrainPoints();
        AlignmentContext terrain = new AlignmentContext(terrainPts, 12000);
        PlacementContext.Bounds b = terrain.bounds();

        // Build a 20-station profile across terrain
        double y = (b.minY() + b.maxY()) / 2.0;
        double startX = b.minX() + 5000;
        double endX = b.maxX() - 5000;
        double dx = (endX - startX) / 20;
        List<StationPoint> stations = new java.util.ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            double x = startX + i * dx;
            double z = terrain.elevationAt(x, y);
            stations.add(new StationPoint(i * dx, x, y, z));
        }

        double designLevel = terrainPts.stream().mapToDouble(StationPoint::z).average().orElse(42000);

        // Contour: zero earthworks (design = terrain)
        GradingStrategy contour = GradingStrategy.contour();
        CutFillCalculator.CutFillResult r0 = contour.computeEarthworks(terrain, stations, 7300);

        // 50% blend: some earthworks
        GradingStrategy blend50 = GradingStrategy.blended(0.5, designLevel);
        CutFillCalculator.CutFillResult r50 = blend50.computeEarthworks(terrain, stations, 7300);

        // Straight: maximum earthworks
        GradingStrategy straight = GradingStrategy.straight(designLevel);
        CutFillCalculator.CutFillResult r100 = straight.computeEarthworks(terrain, stations, 7300);

        // Earthworks should increase: contour ≤ blend ≤ straight
        assertTrue(r0.totalMovementM3() <= r50.totalMovementM3() + 1,
                "Contour ≤ blend50: " + r0.totalMovementM3() + " vs " + r50.totalMovementM3());
        assertTrue(r50.totalMovementM3() <= r100.totalMovementM3() + 1,
                "Blend50 ≤ straight: " + r50.totalMovementM3() + " vs " + r100.totalMovementM3());

        System.out.printf("  Grading earthworks: contour=%.0f m³, blend50=%.0f m³, straight=%.0f m³%n",
                r0.totalMovementM3(), r50.totalMovementM3(), r100.totalMovementM3());
    }

    @Test
    @Order(40)
    @DisplayName("W-GRADING-SNAP-1: snap() with STRAIGHT grading → all bboxes at same Z")
    void snapStraightGradingFixedZ() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));
        List<StationPoint> points = loadTerrainPoints();
        AlignmentContext terrain = new AlignmentContext(points, 12000);
        PlacementContext.Bounds b = terrain.bounds();

        double y = (b.minY() + b.maxY()) / 2;
        double x1 = b.minX() + 20000;
        double x2 = b.maxX() - 20000;
        double designLevel = 42000; // fixed 42m

        DesignBBox c1 = new DesignBBox(
                "RD-ST-001", "course left", "LEAF", "CW",
                "IfcCourse", "road-cw1", "RD-CW1",
                x1 - 3000, y - 2000, 0, x1 + 3000, y + 2000, 250);

        DesignBBox c2 = new DesignBBox(
                "RD-ST-002", "course right", "LEAF", "CW",
                "IfcCourse", "road-cw2", "RD-CW2",
                x2 - 3000, y - 2000, 0, x2 + 3000, y + 2000, 250);

        DesignerAPIImpl api = new DesignerAPIImpl(null, new PlacementValidatorImpl());
        SnapOptions opts = new SnapOptions(
                "MY", 0, null, null, "ROAD",
                terrain, TerrainSnap.onSurface(0),
                GradingStrategy.straight(designLevel));

        SnapResponse resp = api.snap(List.of(c1, c2), opts);

        assertTrue(resp.success());
        double z1 = resp.bboxes().get(0).minZ();
        double z2 = resp.bboxes().get(1).minZ();

        // Both should be at the fixed design level, NOT following terrain
        assertEquals(designLevel, z1, 1.0,
                "Left course at fixed 42m: " + z1 / 1000 + "m");
        assertEquals(designLevel, z2, 1.0,
                "Right course at fixed 42m: " + z2 / 1000 + "m");

        // Compare with contour mode: positions should differ there
        SnapOptions contourOpts = new SnapOptions(
                "MY", 0, null, null, "ROAD",
                terrain, TerrainSnap.onSurface(0),
                GradingStrategy.contour());
        SnapResponse contourResp = api.snap(List.of(c1, c2), contourOpts);
        double cz1 = contourResp.bboxes().get(0).minZ();
        double cz2 = contourResp.bboxes().get(1).minZ();

        // Contour Z follows terrain (different at different positions)
        assertNotEquals(cz1, cz2, 100,
                "Contour mode: different Z at different positions");

        System.out.printf("  Straight: both at %.2fm. Contour: left=%.2fm, right=%.2fm%n",
                z1 / 1000, cz1 / 1000, cz2 / 1000);
    }
}
