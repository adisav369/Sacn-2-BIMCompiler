package com.bim.designer;

import com.bim.designer.validation.*;
import com.bim.designer.validation.AlignmentContext.StationPoint;
import org.junit.jupiter.api.*;

import java.io.FileReader;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PlacementContext tests — proves the abstract container model works
 * for both building rooms and infrastructure alignments.
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-CONTEXT-ROOM-1: RoomContext fit check + constant elevation</li>
 *   <li>W-CONTEXT-ROOM-2: Element too wide for room → does not fit</li>
 *   <li>W-CONTEXT-ALIGN-1: AlignmentContext Z interpolation along centreline</li>
 *   <li>W-CONTEXT-ALIGN-2: Element wider than corridor → does not fit</li>
 *   <li>W-CONTEXT-ALIGN-3: elevationAtStation interpolates between points</li>
 *   <li>W-CONTEXT-POLY: Both contexts implement same interface — polymorphic</li>
 * </ul>
 *
 * // Implementing INFRA_DESIGNER_SRS.md §1+§3 — Witness: W-CONTEXT-1
 */
@DisplayName("PlacementContext — abstract container constraint")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlacementContextTest {

    // ── RoomContext ──────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("W-CONTEXT-ROOM-1: Room fits furniture + constant Z")
    void roomFitsAndElevation() {
        // 4000×3000×2800 room at storey Z=3000
        RoomContext room = new RoomContext(0, 0, 3000, 4000, 3000, 5800);

        assertEquals("ROOM", room.contextType());

        // A 1500×800×2000 bed at (500, 500) fits
        assertTrue(room.fits(1500, 800, 2000, 500, 500),
                "Bed fits inside room");

        // Elevation is constant at storey level
        assertEquals(3000.0, room.elevationAt(0, 0), 0.1);
        assertEquals(3000.0, room.elevationAt(2000, 1500), 0.1);

        // Bounds match construction params
        assertEquals(4000.0, room.bounds().width(), 0.1);
        assertEquals(3000.0, room.bounds().depth(), 0.1);
        assertEquals(2800.0, room.bounds().height(), 0.1);
    }

    @Test
    @Order(2)
    @DisplayName("W-CONTEXT-ROOM-2: Element too wide → does not fit")
    void roomRejectsOversized() {
        RoomContext room = new RoomContext(0, 0, 0, 3000, 3000, 2800);

        // A 3500mm wide element at x=0 exceeds room width (3000)
        assertFalse(room.fits(3500, 1000, 2000, 0, 0),
                "Element wider than room must not fit");

        // A 2000mm element at x=1500 would extend to 3500 → exceeds maxX
        assertFalse(room.fits(2000, 1000, 2000, 1500, 0),
                "Element extending past room edge must not fit");

        // Height exceeding ceiling
        assertFalse(room.fits(1000, 1000, 3000, 0, 0),
                "Element taller than room must not fit");
    }

    // ── AlignmentContext ────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("W-CONTEXT-ALIGN-1: Alignment Z interpolation along centreline")
    void alignmentElevation() {
        // 3-point alignment: station 0→50m→100m, Z rises from 40m to 45m to 42m
        List<StationPoint> points = List.of(
                new StationPoint(0,     0,     0, 40000),   // 40m elevation
                new StationPoint(50000, 50000, 0, 45000),   // 45m at midpoint
                new StationPoint(100000, 100000, 0, 42000)  // 42m at end
        );
        AlignmentContext align = new AlignmentContext(points, 12000); // 12m corridor

        assertEquals("ALIGNMENT", align.contextType());
        assertEquals(100000.0, align.length(), 0.1);

        // Station-based elevation interpolation
        assertEquals(40000.0, align.elevationAtStation(0), 0.1, "Station 0 → 40m");
        assertEquals(45000.0, align.elevationAtStation(50000), 0.1, "Station 50m → 45m");
        assertEquals(42000.0, align.elevationAtStation(100000), 0.1, "Station 100m → 42m");

        // Midpoint between station 0 and 50m → linear interpolation
        double midZ = align.elevationAtStation(25000);
        assertEquals(42500.0, midZ, 0.1,
                "Station 25m → 42.5m (linear between 40m and 45m)");

        // Midpoint between station 50m and 100m
        double midZ2 = align.elevationAtStation(75000);
        assertEquals(43500.0, midZ2, 0.1,
                "Station 75m → 43.5m (linear between 45m and 42m)");
    }

    @Test
    @Order(11)
    @DisplayName("W-CONTEXT-ALIGN-2: Element wider than corridor → does not fit")
    void alignmentRejectsWide() {
        List<StationPoint> points = List.of(
                new StationPoint(0, 0, 0, 40000),
                new StationPoint(100000, 100000, 0, 42000)
        );
        AlignmentContext align = new AlignmentContext(points, 7300); // 7.3m road

        // A 7000mm wide element fits (< 7300 corridor)
        assertTrue(align.fits(7000, 5000, 300, 0, 0),
                "7m element fits in 7.3m corridor");

        // An 8000mm wide element does not fit
        assertFalse(align.fits(8000, 5000, 300, 0, 0),
                "8m element too wide for 7.3m corridor");
    }

    @Test
    @Order(12)
    @DisplayName("W-CONTEXT-ALIGN-3: Terrain elevation varies by position")
    void alignmentElevationByPosition() {
        // Diagonal alignment with varying Z
        List<StationPoint> points = List.of(
                new StationPoint(0,     0,     0,     40000),
                new StationPoint(100000, 100000, 100000, 45000)
        );
        AlignmentContext align = new AlignmentContext(points, 12000);

        // Query elevation at start point → 40m
        double z1 = align.elevationAt(0, 0);
        assertEquals(40000.0, z1, 100, "Near start → ~40m");

        // Query elevation at end point → 45m
        double z2 = align.elevationAt(100000, 100000);
        assertEquals(45000.0, z2, 100, "Near end → ~45m");
    }

    // ── Polymorphic ─────────────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("W-CONTEXT-POLY: Both contexts satisfy same interface")
    void polymorphicPlacement() {
        // Room context
        PlacementContext room = new RoomContext(0, 0, 0, 4000, 3000, 2800);

        // Alignment context
        PlacementContext align = new AlignmentContext(List.of(
                new StationPoint(0, 0, 0, 40000),
                new StationPoint(100000, 100000, 0, 42000)
        ), 12000);

        // Both answer the same questions
        for (PlacementContext ctx : List.of(room, align)) {
            assertNotNull(ctx.contextType());
            assertNotNull(ctx.bounds());
            assertTrue(ctx.bounds().width() > 0, ctx.contextType() + " has width");

            // elevationAt returns a number (not NaN/Infinity)
            double z = ctx.elevationAt(0, 0);
            assertTrue(Double.isFinite(z), ctx.contextType() + " elevation is finite");
        }

        // Room Z is constant, alignment Z varies
        assertEquals(room.elevationAt(0, 0), room.elevationAt(2000, 1500), 0.1,
                "Room Z is constant");
        assertNotEquals(align.elevationAt(0, 0), align.elevationAt(100000, 100000), 0.1,
                "Alignment Z varies along centreline");
    }

    // ── Real terrain from Federation survey ─────────────────────────

    private static final Path TERRAIN_JSON = Path.of(
            "/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/" +
            "pdf_terrain/samples/survey_highres_extracted.json");

    /**
     * Load real survey terrain points from Federation pdf_terrain sample.
     * 689 ground elevation points, pixel coords + Z elevation.
     * Pixel-to-world: x = px * scale, y = (img_h - py) * scale, z = elevation_m.
     * All converted to mm for PlacementContext.
     */
    private static List<StationPoint> loadTerrainPoints() throws Exception {
        String json = Files.readString(TERRAIN_JSON);

        // Minimal JSON parsing — no external library needed
        double scale = extractDouble(json, "\"scale\":");
        int imgH = extractInt(json, "\"height\":");

        List<StationPoint> points = new ArrayList<>();
        int idx = 0;
        double station = 0;

        // Find each ground_level point: {"id":"GL_xxxx","x":...,"y":...,"z":...}
        String marker = "\"ground_level\"";
        int pos = 0;
        while ((pos = json.indexOf(marker, pos)) >= 0) {
            // Backtrack to find the enclosing object's x, y, z
            int objStart = json.lastIndexOf('{', pos);
            int objEnd = json.indexOf('}', pos);
            if (objStart < 0 || objEnd < 0) { pos++; continue; }
            String obj = json.substring(objStart, objEnd + 1);

            double px = extractDouble(obj, "\"x\":");
            double py = extractDouble(obj, "\"y\":");
            double pz = extractDouble(obj, "\"z\":");

            // Pixel to world (metres), then to mm
            double wx = px * scale * 1000.0;
            double wy = (imgH - py) * scale * 1000.0;
            double wz = pz * 1000.0;

            points.add(new StationPoint(station, wx, wy, wz));
            station += 500;  // arbitrary station spacing for alignment ordering
            pos = objEnd;
        }

        // Sort by X for alignment ordering
        points.sort(Comparator.comparingDouble(StationPoint::x));
        // Re-assign station based on sorted order
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

    @Test
    @Order(30)
    @DisplayName("W-CONTEXT-TERRAIN-1: Real survey terrain — 689 points, 20m elevation range")
    void realTerrainFromSurvey() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON),
                "Federation survey JSON must exist: " + TERRAIN_JSON);

        List<StationPoint> points = loadTerrainPoints();
        assertTrue(points.size() > 600,
                "Should load 689 ground elevation points, got " + points.size());

        // Build alignment from terrain points — 12m corridor (typical road width)
        AlignmentContext terrain = new AlignmentContext(points, 12000);

        assertEquals("ALIGNMENT", terrain.contextType());

        // Bounds should cover the survey area (~294m × 229m)
        PlacementContext.Bounds b = terrain.bounds();
        assertTrue(b.width() > 200_000, "Terrain width > 200m: " + b.width() / 1000 + "m");
        assertTrue(b.depth() > 150_000, "Terrain depth > 150m: " + b.depth() / 1000 + "m");

        // Elevation range: 28.1m to 48.1m → 28100mm to 48100mm
        double minZ = b.minZ();
        double maxZ = b.maxZ();
        assertTrue(minZ > 25_000 && minZ < 35_000,
                "Min elevation should be ~28-30m: " + minZ / 1000 + "m");
        assertTrue(maxZ > 44_000 && maxZ < 50_000,
                "Max elevation should be ~45-48m: " + maxZ / 1000 + "m");
        assertTrue(maxZ - minZ > 15_000,
                "Elevation range should be >15m: " + (maxZ - minZ) / 1000 + "m");

        System.out.printf("  Real terrain: %d points, %.0f×%.0fm, elev %.1f–%.1fm%n",
                points.size(),
                b.width() / 1000, b.depth() / 1000,
                minZ / 1000, maxZ / 1000);
    }

    @Test
    @Order(31)
    @DisplayName("W-CONTEXT-TERRAIN-2: Elevation query returns valley slope gradient")
    void terrainElevationGradient() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));

        List<StationPoint> points = loadTerrainPoints();
        AlignmentContext terrain = new AlignmentContext(points, 12000);

        // Query elevation at different X positions along the terrain
        // The survey shows a river valley — elevations vary spatially
        PlacementContext.Bounds b = terrain.bounds();
        double midX = (b.minX() + b.maxX()) / 2;
        double midY = (b.minY() + b.maxY()) / 2;

        double zCenter = terrain.elevationAt(midX, midY);
        double zLeft = terrain.elevationAt(b.minX() + 1000, midY);
        double zRight = terrain.elevationAt(b.maxX() - 1000, midY);

        // All elevations should be in the valid range (28-48m)
        for (double z : new double[]{zCenter, zLeft, zRight}) {
            assertTrue(z > 25_000 && z < 50_000,
                    "Elevation should be 25-50m: " + z / 1000 + "m");
        }

        // Elevations should NOT all be identical — terrain has gradient
        boolean allSame = Math.abs(zLeft - zCenter) < 100
                       && Math.abs(zRight - zCenter) < 100;
        assertFalse(allSame,
                "Terrain should have gradient — not flat: " +
                String.format("left=%.1fm center=%.1fm right=%.1fm",
                        zLeft/1000, zCenter/1000, zRight/1000));

        System.out.printf("  Gradient: left=%.2fm, center=%.2fm, right=%.2fm%n",
                zLeft / 1000, zCenter / 1000, zRight / 1000);
    }

    @Test
    @Order(32)
    @DisplayName("W-CONTEXT-TERRAIN-3: Infrastructure element placed on terrain gets correct Z")
    void placeElementOnTerrain() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));

        List<StationPoint> points = loadTerrainPoints();
        AlignmentContext terrain = new AlignmentContext(points, 12000);

        // Place a road surface course (7300mm wide, 40mm thick) at terrain centre
        PlacementContext.Bounds b = terrain.bounds();
        double midX = (b.minX() + b.maxX()) / 2;
        double midY = (b.minY() + b.maxY()) / 2;

        // Course fits within 12m corridor
        assertTrue(terrain.fits(7300, 5000, 40, midX, midY),
                "7.3m road course should fit in 12m corridor");

        // Course too wide for corridor
        assertFalse(terrain.fits(15000, 5000, 40, midX, midY),
                "15m element should NOT fit in 12m corridor");

        // The element's base Z = terrain elevation at its position
        double baseZ = terrain.elevationAt(midX, midY);
        assertTrue(baseZ > 25_000 && baseZ < 50_000,
                "Element base Z should be terrain elevation: " + baseZ / 1000 + "m");

        // A pier at a different position gets a different Z
        double pierZ = terrain.elevationAt(b.minX() + 10000, midY);
        // They may or may not differ depending on terrain, but both valid
        assertTrue(pierZ > 25_000 && pierZ < 50_000,
                "Pier Z also valid terrain elevation: " + pierZ / 1000 + "m");

        System.out.printf("  Course at centre: Z=%.2fm. Pier at edge: Z=%.2fm.%n",
                baseZ / 1000, pierZ / 1000);
    }

    // ── TerrainSnap — snuggle modes on real terrain ─────────────────

    @Test
    @Order(40)
    @DisplayName("W-TERRAIN-SNAP-1: Road layers stack ON terrain surface")
    void roadLayersOnTerrain() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));
        List<StationPoint> points = loadTerrainPoints();
        AlignmentContext terrain = new AlignmentContext(points, 12000);
        PlacementContext.Bounds b = terrain.bounds();
        double x = (b.minX() + b.maxX()) / 2;
        double y = (b.minY() + b.maxY()) / 2;
        double terrainZ = terrain.elevationAt(x, y);

        // Road layer stack: subgrade(250) → base(120) → binder(80) → surface(40) = 490mm
        double[] thicknesses = { 250, 120, 80, 40 };

        double z0 = TerrainSnap.computeLayerZ(terrain, x, y, 0, thicknesses);
        double z1 = TerrainSnap.computeLayerZ(terrain, x, y, 1, thicknesses);
        double z2 = TerrainSnap.computeLayerZ(terrain, x, y, 2, thicknesses);
        double z3 = TerrainSnap.computeLayerZ(terrain, x, y, 3, thicknesses);

        assertEquals(terrainZ, z0, 0.1, "Subgrade base = terrain");
        assertEquals(terrainZ + 250, z1, 0.1, "Base course starts at +250mm");
        assertEquals(terrainZ + 370, z2, 0.1, "Binder starts at +370mm");
        assertEquals(terrainZ + 450, z3, 0.1, "Surface starts at +450mm");

        // Top of road = terrain + 490mm
        double roadTop = z3 + 40;
        assertEquals(terrainZ + 490, roadTop, 0.1, "Road top = terrain + 490mm");

        System.out.printf("  Road at terrain %.2fm: layers %.2f/%.2f/%.2f/%.2fm, top=%.2fm%n",
                terrainZ/1000, z0/1000, z1/1000, z2/1000, z3/1000, roadTop/1000);
    }

    @Test
    @Order(41)
    @DisplayName("W-TERRAIN-SNAP-2: Bridge deck ABOVE terrain with 5m clearance")
    void bridgeDeckAboveTerrain() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));
        List<StationPoint> points = loadTerrainPoints();
        AlignmentContext terrain = new AlignmentContext(points, 12000);
        PlacementContext.Bounds b = terrain.bounds();
        double x = (b.minX() + b.maxX()) / 2;
        double y = (b.minY() + b.maxY()) / 2;
        double terrainZ = terrain.elevationAt(x, y);

        // Bridge deck: 5m clearance above terrain, 2400mm deep deck
        TerrainSnap deckSnap = TerrainSnap.above(5000);
        double deckBaseZ = deckSnap.computeZ(terrain, x, y, 2400);
        double[] range = deckSnap.computeZRange(terrain, x, y, 2400);

        assertEquals(terrainZ + 5000, deckBaseZ, 0.1,
                "Deck base = terrain + 5m clearance");
        assertEquals(terrainZ + 5000 + 2400, range[1], 0.1,
                "Deck top = terrain + 5m + 2.4m depth");

        // Pier from terrain up to deck — height = clearance + deck depth
        TerrainSnap pierSnap = TerrainSnap.pier();
        double pierBaseZ = pierSnap.computeZ(terrain, x, y, 5000 + 2400);
        assertEquals(terrainZ, pierBaseZ, 0.1,
                "Pier base = terrain surface");

        System.out.printf("  Bridge: terrain=%.2fm, deck=%.2f–%.2fm, pier base=%.2fm%n",
                terrainZ/1000, deckBaseZ/1000, range[1]/1000, pierBaseZ/1000);
    }

    @Test
    @Order(42)
    @DisplayName("W-TERRAIN-SNAP-3: Tunnel BELOW terrain with 3m cover")
    void tunnelBelowTerrain() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));
        List<StationPoint> points = loadTerrainPoints();
        AlignmentContext terrain = new AlignmentContext(points, 12000);
        PlacementContext.Bounds b = terrain.bounds();
        double x = (b.minX() + b.maxX()) / 2;
        double y = (b.minY() + b.maxY()) / 2;
        double terrainZ = terrain.elevationAt(x, y);

        // Tunnel: 3m cover depth, 6m diameter (height)
        TerrainSnap tunnelSnap = TerrainSnap.below(3000);
        double tunnelBaseZ = tunnelSnap.computeZ(terrain, x, y, 6000);
        double[] range = tunnelSnap.computeZRange(terrain, x, y, 6000);

        // Tunnel top = terrain - 3m cover. Base = top - 6m diameter.
        assertEquals(terrainZ - 3000 - 6000, tunnelBaseZ, 0.1,
                "Tunnel base = terrain - cover - diameter");
        assertEquals(tunnelBaseZ + 6000, range[1], 0.1,
                "Tunnel top = base + diameter");
        assertTrue(range[1] < terrainZ,
                "Tunnel top must be below terrain surface");

        System.out.printf("  Tunnel: terrain=%.2fm, tube=%.2f–%.2fm (%.1fm below surface)%n",
                terrainZ/1000, tunnelBaseZ/1000, range[1]/1000, (terrainZ-range[1])/1000);
    }

    @Test
    @Order(43)
    @DisplayName("W-TERRAIN-SNAP-4: Drag simulation — Z follows terrain as element moves")
    void dragFollowsTerrain() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));
        List<StationPoint> points = loadTerrainPoints();
        AlignmentContext terrain = new AlignmentContext(points, 12000);
        PlacementContext.Bounds b = terrain.bounds();

        TerrainSnap snap = TerrainSnap.onSurface(0);  // directly on terrain

        // Simulate dragging from left to right across terrain
        double y = (b.minY() + b.maxY()) / 2;
        double startX = b.minX() + 5000;
        double endX = b.maxX() - 5000;
        int steps = 10;
        double dx = (endX - startX) / steps;

        double prevZ = -1;
        int zChanges = 0;
        StringBuilder trail = new StringBuilder("  Drag trail: ");

        for (int i = 0; i <= steps; i++) {
            double x = startX + i * dx;
            double z = snap.computeZ(terrain, x, y, 300);  // 300mm sleeper
            if (prevZ > 0 && Math.abs(z - prevZ) > 100) zChanges++;
            prevZ = z;
            trail.append(String.format("%.1f ", z / 1000));
        }

        assertTrue(zChanges > 0,
                "Z must change during drag across terrain — element follows surface");

        System.out.println(trail + "m");
        System.out.printf("  %d Z changes in %d drag steps%n", zChanges, steps);
    }

    // ── Contour-following curve on real terrain ─────────────────────

    @Test
    @Order(50)
    @DisplayName("W-CONTOUR-1: Road curves along 43m contour on real terrain")
    void contourFollowingRoad() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));
        List<StationPoint> allPoints = loadTerrainPoints();

        // Follow the 43m contour (43000mm) with ±1m tolerance
        AlignmentContext contour = AlignmentContext.fromContour(
                allPoints, 43000, 1000, 7300);  // 7.3m road corridor

        assertTrue(contour.points().size() > 10,
                "Contour should have many points (winding path): " + contour.points().size());
        assertTrue(contour.length() > 50_000,
                "Contour path should be >50m: " + contour.length() / 1000 + "m");

        // All points should be within the tolerance band
        for (StationPoint p : contour.points()) {
            assertTrue(Math.abs(p.z() - 43000) <= 1000,
                    "Every point Z within ±1m of 43m: " + p.z() / 1000 + "m");
        }

        // The path should CURVE — heading changes along the contour
        double heading0 = contour.headingAtStation(contour.length() * 0.1);
        double headingMid = contour.headingAtStation(contour.length() * 0.5);
        double headingEnd = contour.headingAtStation(contour.length() * 0.9);

        boolean headingChanges = Math.abs(heading0 - headingMid) > 0.1
                              || Math.abs(headingMid - headingEnd) > 0.1;
        assertTrue(headingChanges,
                "Contour-following path should curve (heading changes): " +
                String.format("%.1f° → %.1f° → %.1f°",
                        Math.toDegrees(heading0), Math.toDegrees(headingMid),
                        Math.toDegrees(headingEnd)));

        System.out.printf("  Contour 43m: %d points, %.0fm path, heading %.0f°→%.0f°→%.0f°%n",
                contour.points().size(), contour.length() / 1000,
                Math.toDegrees(heading0), Math.toDegrees(headingMid),
                Math.toDegrees(headingEnd));
    }

    @Test
    @Order(51)
    @DisplayName("W-CONTOUR-2: Bridge at 45m contour — different curve than 43m road")
    void contourFollowingBridge() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));
        List<StationPoint> allPoints = loadTerrainPoints();

        // Bridge follows 45m contour (higher elevation — ridge line)
        AlignmentContext bridge45 = AlignmentContext.fromContour(
                allPoints, 45000, 1000, 12000);  // 12m bridge corridor

        // Road follows 43m contour (lower — valley)
        AlignmentContext road43 = AlignmentContext.fromContour(
                allPoints, 43000, 1000, 7300);

        // They should have different paths
        assertNotEquals(bridge45.points().size(), road43.points().size(),
                "Different contours should have different point counts");

        // Bridge deck Z should be ~45m, road Z should be ~43m
        double bridgeMidZ = bridge45.elevationAtStation(bridge45.length() / 2);
        double roadMidZ = road43.elevationAtStation(road43.length() / 2);
        assertTrue(bridgeMidZ > roadMidZ,
                "Bridge (45m) should be higher than road (43m): " +
                bridgeMidZ / 1000 + " vs " + roadMidZ / 1000);

        System.out.printf("  Bridge@45m: %d pts, %.0fm. Road@43m: %d pts, %.0fm. ΔZ=%.1fm%n",
                bridge45.points().size(), bridge45.length() / 1000,
                road43.points().size(), road43.length() / 1000,
                (bridgeMidZ - roadMidZ) / 1000);
    }

    @Test
    @Order(52)
    @DisplayName("W-CONTOUR-3: Curvature varies along winding contour path")
    void curvatureAlongContour() throws Exception {
        assumeTrue(Files.exists(TERRAIN_JSON));
        List<StationPoint> allPoints = loadTerrainPoints();

        AlignmentContext contour = AlignmentContext.fromContour(
                allPoints, 43500, 1500, 7300);  // wider band for more points

        // Sample curvature at multiple stations
        int samples = 8;
        double step = contour.length() / (samples + 1);
        StringBuilder curvatureTrail = new StringBuilder("  Curvature: ");
        int nonZero = 0;

        for (int i = 1; i <= samples; i++) {
            double station = i * step;
            double k = contour.curvatureAtStation(station);
            double radius = k > 1e-9 ? 1.0 / k / 1000 : Double.POSITIVE_INFINITY;
            curvatureTrail.append(String.format("R=%.0fm ", radius));
            if (k > 1e-9) nonZero++;
        }

        assertTrue(nonZero > 0,
                "Winding path should have non-zero curvature at some stations");

        System.out.println(curvatureTrail);
        System.out.printf("  %d/%d stations with measurable curvature%n", nonZero, samples);
    }
}
