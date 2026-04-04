package com.bim.compiler.library;

import com.bim.compiler.coordinate.LocalCoord;
import com.bim.compiler.coordinate.StoreyCoord;
import com.bim.compiler.coordinate.WorldCoord;
import com.bim.compiler.dsl.PhantomLayout;
import com.bim.compiler.dsl.Place;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.Vector3D;
import com.bim.compiler.library.BOMTreeLoader.BOMChild;
import com.bim.compiler.library.BOMTreeLoader.BOMNode;
import com.bim.orm.ModelQuery;
import com.bim.ormsandbox.po.MProduct;

import java.sql.*;
import java.util.*;

/**
 * Phase G-1: Unified BOM tier resolver — handles furniture, fixtures, and structural BOMs.
 *
 * <p>Renamed from FurnitureBOMResolver (Phase 93). Now resolves ALL BOM assembly types
 * through a single data-driven dispatch:
 * <ol>
 *   <li><b>Fixture children</b> — m_attribute has {@code placement_wall} or {@code position}:
 *       wall-relative placement with qty_rule, spacing, rotation_rule.</li>
 *   <li><b>Wall-locator children</b> — locatorRef = NORTH_WALL etc: GPD walk via PhantomLayout.</li>
 *   <li><b>FLOAT children</b> — locatorRef = FLOAT: dx/dy expandBOMNode path.</li>
 * </ol>
 *
 * <p>Phase 4c: ORM migration of loadBOMTree(). GPD dispatch for wall-locator children.
 */
public class BOMTierResolver {

    private static String bomDbPath() { return System.getProperty("bom.db"); }
    private static final double BIG_ROOM_AREA = 80.0;
    private static final double BIG_ROOM_MIN_DIM = 3.0;
    private static final double WALL_OFFSET = 0.5;
    private static final double DEFAULT_CEILING_HEIGHT = 3.0; // meters above floorZ

    // BOM tree loaded via BOMTreeLoader (AD layer)
    private Map<String, BOMNode> bomTree = Map.of();

    // Phase 4c: product dims cache — keyed by product_id (meters, from ad_product_dim)
    private final Map<String, double[]> productDimCache = new HashMap<>();
    // Phase C: product material cache — keyed by product_id
    private final Map<String, String[]> materialCache = new HashMap<>();

    public record PlacedFurniture(String role, double x, double y, double z,
                                  double rotation, String namePattern, String productRef,
                                  String materialName, String materialRgba) {}

    /** Phase G-1 Step 5: stall divider geometry read from m_attribute (not hardcoded). */
    public record StallDividerParams(double spacing, double dividerDepth, double stallHeight) {}

    /**
     * Return stall divider geometry params for the TOILET-role child of the given BOM.
     * Falls back to design defaults if the BOM or role is absent.
     */
    public StallDividerParams getStallDividerParams(String bomId) {
        BOMNode node = bomTree.get(bomId);
        if (node != null) {
            for (BOMChild c : node.children()) {
                if ("TOILET".equals(c.role())) {
                    return new StallDividerParams(
                        c.paramDouble("spacing",              1.3),
                        c.paramDouble("stall_divider_depth",  1.2),
                        c.paramDouble("stall_divider_height", 1.8));
                }
            }
        }
        return new StallDividerParams(1.3, 1.2, 1.8);
    }

    public BOMTierResolver() {
        loadBOMTree();
    }

    // ── Tree loading via BOMTreeLoader (AD layer) + product data ────────────

    private void loadBOMTree() {
        try {
            // ① BOM tree — delegated to shared AD loader
            this.bomTree = BOMTreeLoader.load(bomDbPath());

            // ② Product dims + materials — BOMTierResolver-specific
            try (Connection libConn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath())) {
                List<MProduct> allDims = new ModelQuery<>(
                        libConn, MProduct::new, MProduct.Table_Name)
                    .where("is_active = ?", 1)
                    .list();

                for (MProduct dim : allDims) {
                    productDimCache.put(dim.getProductId(),
                        new double[]{dim.getWidth(), dim.getDepth(), dim.getHeight()});
                    if (dim.getMaterialName() != null) {
                        materialCache.put(dim.getProductId(),
                            new String[]{dim.getMaterialName(), dim.getMaterialRgba()});
                    }
                }
            }

            System.out.printf("[BOM-TIER] %d BOM nodes, %d product dims%n",
                bomTree.size(), productDimCache.size());

        } catch (SQLException e) {
            System.err.println("[BOM-TIER] Failed to load: " + e.getMessage());
        }
    }

    /** Resolve wall_rule from BOM child params (legacy opposite_wall support). */
    private static String resolvedWallRule(BOMChild child) {
        String wallRule = child.param("wall_rule");
        if (wallRule == null && "true".equalsIgnoreCase(child.param("opposite_wall"))) {
            wallRule = "OPPOSITE_WORK";
        }
        return wallRule;
    }

    /** Look up material by productRef (exact FK) first, then namePattern (fallback key). */
    private String[] lookupMaterial(String productRef, String namePattern) {
        if (productRef != null && !productRef.isBlank()) {
            String[] m = materialCache.get(productRef);
            if (m != null) return m;
        }
        if (namePattern != null) return materialCache.get(namePattern);
        return null;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resolve placement for a room using the default ROOM_FURNITURE BOM.
     */
    public List<PlacedFurniture> resolveForRoom(
            double roomMinX, double roomMinY, double roomMaxX, double roomMaxY,
            double floorZ, String roomName, String roomType,
            List<OpeningInfo> openings) {
        return resolveForRoom(roomMinX, roomMinY, roomMaxX, roomMaxY,
            floorZ, roomName, roomType, openings, "ROOM_FURNITURE",
            floorZ + DEFAULT_CEILING_HEIGHT);
    }

    /**
     * Phase 109: Resolve placement for a room using a specific BOM ID.
     * Defaults ceilingZ to floorZ + 3.0m.
     */
    public List<PlacedFurniture> resolveForRoom(
            double roomMinX, double roomMinY, double roomMaxX, double roomMaxY,
            double floorZ, String roomName, String roomType,
            List<OpeningInfo> openings, String bomId) {
        return resolveForRoom(roomMinX, roomMinY, roomMaxX, roomMaxY,
            floorZ, roomName, roomType, openings, bomId,
            floorZ + DEFAULT_CEILING_HEIGHT);
    }

    /**
     * Phase G-1: Resolve placement for a room using a specific BOM ID and ceilingZ.
     *
     * <p>Three-way dispatch:
     * <ol>
     *   <li>Children with fixture params (placement_wall, position) → {@link #resolveFixtureChildren}</li>
     *   <li>Children with wall locatorRef (NORTH_WALL, etc.) → {@link #resolveWithGPD}</li>
     *   <li>FLOAT children → {@link #resolveFloatChildren} (dx/dy expandBOMNode path)</li>
     * </ol>
     */
    public List<PlacedFurniture> resolveForRoom(
            double roomMinX, double roomMinY, double roomMaxX, double roomMaxY,
            double floorZ, String roomName, String roomType,
            List<OpeningInfo> openings, String bomId,
            double ceilingZ) {

        double roomW = roomMaxX - roomMinX;
        double roomD = roomMaxY - roomMinY;
        double area = roomW * roomD;

        // Phase 122C: Lower threshold from 6.0 to 2.0 — bathrooms need vanity cabinets
        if (area < 2.0) return List.of();

        BOMNode roomFurniture = bomTree.get(bomId);
        if (roomFurniture == null || roomFurniture.children().isEmpty()) {
            return List.of();
        }

        // Phase G-1: three-way partition of children
        Map<String, List<BOMChild>> byLocator = new LinkedHashMap<>();
        List<BOMChild> floatChildren = new ArrayList<>();
        List<BOMChild> fixtureChildren = new ArrayList<>();

        for (BOMChild c : roomFurniture.children()) {
            if (c.hasFixtureParams()) {
                fixtureChildren.add(c);
            } else if ("FLOAT".equals(c.locatorRef())) {
                floatChildren.add(c);
            } else {
                byLocator.computeIfAbsent(c.locatorRef(), k -> new ArrayList<>()).add(c);
            }
        }

        List<PlacedFurniture> result = new ArrayList<>();

        // ── 1. Fixture children (Phase G-1) ───────────────────────────────────
        if (!fixtureChildren.isEmpty()) {
            result.addAll(resolveFixtureChildren(
                fixtureChildren, roomMinX, roomMinY, roomMaxX, roomMaxY,
                floorZ, ceilingZ, roomName, openings));
        }

        // ── 2. GPD walk for wall-locator children ─────────────────────────────
        for (var entry : byLocator.entrySet()) {
            List<BOMChild> wallChildren = entry.getValue();
            wallChildren.sort(Comparator.comparingInt(BOMChild::sequence));
            result.addAll(resolveWithGPD(
                wallChildren, entry.getKey(),
                roomMinX, roomMinY, roomMaxX, roomMaxY, floorZ, roomName));
        }

        // ── 3. Legacy dx/dy path for FLOAT children ──────────────────────────
        if (!floatChildren.isEmpty()) {
            BOMNode floatNode = new BOMNode(roomFurniture.bomId(), floatChildren,
                roomFurniture.originX(), roomFurniture.originY(), roomFurniture.originZ());
            result.addAll(resolveFloatChildren(
                floatNode, roomMinX, roomMinY, roomMaxX, roomMaxY,
                floorZ, roomName, roomType, openings, area));
        }

        return result;
    }

    // ── Phase G-1: Fixture-param resolution ───────────────────────────────────

    /**
     * Resolve BOM children that have fixture-style m_attribute params
     * (placement_wall, position, qty_rule, spacing, rotation_rule, z_rule).
     *
     * <p>Ported from FixturePlacer Phase 96B BOM-driven toilet block logic.
     * Now handles ANY BOM with fixture params (TOILET_BLOCK_FIXTURES,
     * DUPLEX_BATHROOM_SET, future fixture BOMs).
     */
    private List<PlacedFurniture> resolveFixtureChildren(
            List<BOMChild> children,
            double roomMinX, double roomMinY, double roomMaxX, double roomMaxY,
            double floorZ, double ceilingZ, String roomName,
            List<OpeningInfo> openings) {

        List<PlacedFurniture> result = new ArrayList<>();
        double roomWidth = roomMaxX - roomMinX;
        double roomDepth = roomMaxY - roomMinY;

        // Extract door wall and exterior walls from openings
        String doorWall = findDoorWall(openings);
        String dw = (doorWall != null) ? doorWall.toLowerCase() : "west";
        List<String> exteriorWalls = findExteriorWalls(openings);

        // Track role counts for match_role references (e.g. match_role:TOILET)
        Map<String, Integer> roleCounts = new HashMap<>();

        for (BOMChild child : children) {
            // Variance children (BUFFER) — skip, they're capacity absorbers
            if (child.isVariance()) continue;

            Map<String, String> p = child.params();
            if (p == null) p = Map.of();

            String placementWall = p.getOrDefault("placement_wall", "");
            String position = p.getOrDefault("position", "");
            String qtyRule = p.getOrDefault("qty_rule", "");
            double wallOffset = parseDoubleValue(p.get("wall_offset"), 0.05);
            double zOffset = parseDoubleValue(p.get("z_offset"), 0);
            String zRule = p.getOrDefault("z_rule", "");
            String rotRule = p.getOrDefault("rotation_rule", "0");

            // Resolve absolute wall from relative placement
            String absWall = resolveFixtureWall(placementWall, dw, exteriorWalls);
            double wallLength = isHorizontalWall(absWall) ? roomWidth : roomDepth;

            // Determine Z
            double z = floorZ + zOffset;
            if ("ceiling".equals(zRule)) {
                z = ceilingZ;
            }

            // Look up product dims for fixture depth
            String dimKey = (child.productRef() != null && !child.productRef().isBlank())
                ? child.productRef() : child.namePattern();
            double[] dims = productDimCache.get(dimKey);
            double fixtureDepth = (dims != null) ? dims[1] : 0.3;

            int qty;
            if ("floor_center".equals(position) || "ceiling_center".equals(position)) {
                // Center placement — single item at room center
                qty = (int) parseDoubleValue(p.get("qty"), 1);
                double cx = (roomMinX + roomMaxX) / 2;
                double cy = (roomMinY + roomMaxY) / 2;
                double rotation = LocalCoord.resolveRotation(rotRule,null);

                String[] mat = lookupMaterial(child.productRef(), child.namePattern());
                result.add(new PlacedFurniture(
                    child.role(), cx, cy, z, rotation,
                    child.namePattern(), child.productRef(),
                    mat != null ? mat[0] : null, mat != null ? mat[1] : null));
            } else {
                // Wall-based placement
                double spacing = parseDoubleValue(p.get("spacing"), 1.3);

                if (qtyRule.startsWith("match_role:")) {
                    String matchRole = qtyRule.substring("match_role:".length());
                    qty = roleCounts.getOrDefault(matchRole, 1);
                } else if ("per_wall_length".equals(qtyRule)) {
                    qty = Math.max(1, (int) Math.floor(wallLength / spacing));
                } else {
                    qty = 1;
                }

                double rotation = LocalCoord.resolveRotation(rotRule,absWall);
                double lateralOffset = parseDoubleValue(p.get("lateral_offset"), 0);

                for (int i = 0; i < qty; i++) {
                    double stallCenter = getFixtureCenter(i, qty, wallLength, spacing);
                    double[] pos = positionFixtureAgainstWall(absWall,
                        roomMinX, roomMinY, roomMaxX, roomMaxY,
                        stallCenter, fixtureDepth, wallOffset);

                    // Apply lateral offset (along the wall, perpendicular to depth)
                    if (lateralOffset != 0) {
                        if (isHorizontalWall(absWall)) {
                            pos[0] += lateralOffset;
                        } else {
                            pos[1] += lateralOffset;
                        }
                    }

                    String[] mat = lookupMaterial(child.productRef(), child.namePattern());
                    result.add(new PlacedFurniture(
                        child.role(), pos[0], pos[1], z, rotation,
                        child.namePattern(), child.productRef(),
                        mat != null ? mat[0] : null, mat != null ? mat[1] : null));
                }
            }

            roleCounts.put(child.role(), qty);
        }

        System.out.printf("[BOM-TIER] %s: %d fixture children → %d placed%n",
            roomName, children.size(), result.size());
        return result;
    }

    // ── Fixture helper methods (ported from FixturePlacer Phase 96B) ─────────

    /**
     * Resolve relative fixture wall to absolute direction.
     * "back" = opposite of door, "side_interior" = perpendicular non-exterior wall.
     */
    private String resolveFixtureWall(String placement, String doorWall,
                                       List<String> exteriorWalls) {
        if (placement == null || placement.isEmpty()) return doorWall;
        return switch (placement) {
            case "back" -> LocalCoord.oppositeCardinal(doorWall);
            case "door" -> doorWall;
            case "side_interior" -> {
                List<String> perp = perpendicularWalls(doorWall);
                List<String> extLower = (exteriorWalls != null)
                    ? exteriorWalls.stream().map(String::toLowerCase).toList()
                    : List.of();
                for (String w : perp) {
                    if (!extLower.contains(w)) yield w;
                }
                yield perp.get(0);
            }
            default -> placement.toLowerCase(); // literal wall name
        };
    }

    /** Position a fixture against a wall with custom offset. Returns [x, y]. */
    private double[] positionFixtureAgainstWall(String wall,
            double minX, double minY, double maxX, double maxY,
            double centerAlong, double fixtureDepth, double wallOffset) {
        return switch (wall.toLowerCase()) {
            case "north" -> new double[]{ minX + centerAlong, maxY - wallOffset - fixtureDepth / 2 };
            case "south" -> new double[]{ minX + centerAlong, minY + wallOffset + fixtureDepth / 2 };
            case "east"  -> new double[]{ maxX - wallOffset - fixtureDepth / 2, minY + centerAlong };
            case "west"  -> new double[]{ minX + wallOffset + fixtureDepth / 2, minY + centerAlong };
            default -> new double[]{ (minX + maxX) / 2, (minY + maxY) / 2 };
        };
    }

    /** Center fixture i out of n along wall, using actual spacing. */
    private double getFixtureCenter(int i, int n, double wallLength, double spacing) {
        double totalWidth = n * spacing;
        double startOffset = (wallLength - totalWidth) / 2.0 + spacing / 2.0;
        return startOffset + i * spacing;
    }


    /** Walls perpendicular to the given wall. */
    private List<String> perpendicularWalls(String wall) {
        return switch (wall.toLowerCase()) {
            case "north", "south" -> List.of("east", "west");
            case "east", "west" -> List.of("south", "north");
            default -> List.of("east", "west");
        };
    }

    /** True if wall is north or south (X-aligned). */
    private boolean isHorizontalWall(String wall) {
        return "north".equalsIgnoreCase(wall) || "south".equalsIgnoreCase(wall);
    }

    /** Extract the door wall from openings (first DOOR or D-type opening). */
    private static String findDoorWall(List<OpeningInfo> openings) {
        if (openings == null) return null;
        for (OpeningInfo o : openings) {
            String t = o.type();
            if (t != null && ("DOOR".equalsIgnoreCase(t) || t.toUpperCase().startsWith("D"))
                    && o.wall() != null) {
                return o.wall();
            }
        }
        return null;
    }

    /** Extract exterior walls from openings marked as EXTERIOR. */
    private static List<String> findExteriorWalls(List<OpeningInfo> openings) {
        List<String> walls = new ArrayList<>();
        if (openings == null) return walls;
        for (OpeningInfo o : openings) {
            if ("EXTERIOR".equalsIgnoreCase(o.type())) {
                walls.add(o.wall());
            }
        }
        return walls;
    }

    private static double parseDoubleValue(String s, double fallback) {
        if (s == null || s.isEmpty()) return fallback;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return fallback; }
    }

    // ── Phase 4c: GPD walk ────────────────────────────────────────────────────

    /**
     * Place children against a named wall locator using PhantomLayout GPD walk.
     */
    private List<PlacedFurniture> resolveWithGPD(
            List<BOMChild> wallChildren, String locatorRef,
            double roomMinX, double roomMinY, double roomMaxX, double roomMaxY,
            double floorZ, String roomName) {

        double capacityMm;
        Point3D origin;
        Vector3D hostAxis, front;
        double rotation;

        switch (locatorRef) {
            case "NORTH_WALL" -> {
                capacityMm = (roomMaxX - roomMinX) * 1000.0;
                origin = new Point3D(roomMinX, roomMaxY, floorZ);
                hostAxis = Vector3D.X_AXIS;
                front    = Vector3D.NEG_Y;
                rotation = Math.PI;       // back to north, facing south
            }
            case "SOUTH_WALL" -> {
                capacityMm = (roomMaxX - roomMinX) * 1000.0;
                origin = new Point3D(roomMinX, roomMinY, floorZ);
                hostAxis = Vector3D.X_AXIS;
                front    = Vector3D.Y_AXIS;
                rotation = 0.0;           // back to south, facing north
            }
            case "EAST_WALL" -> {
                capacityMm = (roomMaxY - roomMinY) * 1000.0;
                origin = new Point3D(roomMaxX, roomMinY, floorZ);
                hostAxis = Vector3D.Y_AXIS;
                front    = Vector3D.NEG_X;
                rotation = Math.PI / 2;   // back to east, facing west
            }
            case "WEST_WALL" -> {
                capacityMm = (roomMaxY - roomMinY) * 1000.0;
                origin = new Point3D(roomMinX, roomMinY, floorZ);
                hostAxis = Vector3D.Y_AXIS;
                front    = Vector3D.X_AXIS;
                rotation = -Math.PI / 2;  // back to west, facing east
            }
            default -> {
                System.err.printf("[GPD] Unknown locatorRef '%s' for %s — skipped%n",
                    locatorRef, roomName);
                return List.of();
            }
        }

        PhantomLayout ph = PhantomLayout.forLocator(
            "RESOLVER", locatorRef, capacityMm, origin, hostAxis);

        List<PlacedFurniture> result = new ArrayList<>();

        for (BOMChild child : wallChildren) {
            if (child.isVariance()) {
                Place spacer = Place.variance(
                    child.namePattern(), locatorRef, child.sequence(),
                    ph.nextAnchor(), front, hostAxis);
                ph.placeNext(spacer);
                continue;
            }

            String dimKey = (child.productRef() != null && !child.productRef().isBlank())
                ? child.productRef() : child.namePattern();
            double[] dims = productDimCache.get(dimKey);
            if (dims == null) {
                System.err.printf("[GPD] No dims for '%s' in %s — skipped%n",
                    dimKey, roomName);
                continue;
            }

            double widthM  = dims[0];
            double depthM  = dims[1];
            double heightM = dims[2];

            BoundingBox bbox = new BoundingBox(0, widthM, 0, depthM, 0, heightM);
            Place place = Place.fixed(child.namePattern(), locatorRef, child.sequence(),
                bbox, ph.nextAnchor(), front, hostAxis);
            ph.placeNext(place);

            Point3D anchor = place.anchor();
            double halfW = widthM / 2.0;
            double halfD = depthM / 2.0;

            double cx, cy;
            if ("NORTH_WALL".equals(locatorRef) || "SOUTH_WALL".equals(locatorRef)) {
                cx = anchor.x() + halfW;
                cy = "NORTH_WALL".equals(locatorRef)
                   ? anchor.y() - halfD
                   : anchor.y() + halfD;
            } else {
                cy = anchor.y() + halfW;
                cx = "EAST_WALL".equals(locatorRef)
                   ? anchor.x() - halfD
                   : anchor.x() + halfD;
            }

            String[] mat = lookupMaterial(child.productRef(), child.namePattern());
            result.add(new PlacedFurniture(
                child.role(), cx, cy, floorZ, rotation,
                child.namePattern(), child.productRef(),
                mat != null ? mat[0] : null, mat != null ? mat[1] : null));

            if (child.childBomId() != null) {
                BOMNode subNode = bomTree.get(child.childBomId());
                if (subNode != null) {
                    StoreyCoord subAnchor = new StoreyCoord(cx, cy, floorZ, rotation);
                    result.addAll(expandBOMNode(subNode, subAnchor, roomMinX, roomMinY, roomMaxX, roomMaxY));
                }
            }
        }

        if (ph.isOverflow()) {
            System.err.printf("[GPD] GIC VIOLATION: %s %s filled=%.0fmm > capacity=%.0fmm%n",
                roomName, locatorRef, ph.filledMm(), ph.filledMm() - ph.remainingMm());
        } else {
            System.out.printf("[GPD] %s %s: placed=%d filled=%.0fmm remaining=%.0fmm%n",
                roomName, locatorRef, ph.places().size(),
                ph.filledMm(), ph.remainingMm());
        }

        return result;
    }

    // ── Legacy FLOAT children path ────────────────────────────────────────────

    private List<PlacedFurniture> resolveFloatChildren(
            BOMNode floatNode,
            double roomMinX, double roomMinY, double roomMaxX, double roomMaxY,
            double floorZ, String roomName, String roomType,
            List<OpeningInfo> openings, double area) {

        double roomW = roomMaxX - roomMinX;
        double roomD = roomMaxY - roomMinY;

        BOMChild primaryChild = floatNode.children().get(0);
        boolean hasOffsets = floatNode.children().stream()
            .anyMatch(c -> c.dx() != 0 || c.dy() != 0);
        boolean isCenterGrid = hasOffsets && "CENTER".equals(resolvedWallRule(primaryChild)) && area >= 20.0;

        if (isCenterGrid) {
            double areaPerSet = 13.0;
            int gridTotal = Math.max(1, (int)(area / areaPerSet));
            int cols = Math.max(1, (int) Math.ceil(Math.sqrt(gridTotal * roomW / roomD)));
            int rows = Math.max(1, (int) Math.ceil((double) gridTotal / cols));

            double spacingX = roomW / (cols + 1);
            double spacingY = roomD / (rows + 1);

            List<PlacedFurniture> result = new ArrayList<>();
            int placed = 0;
            for (int r = 0; r < rows && placed < gridTotal; r++) {
                for (int c = 0; c < cols && placed < gridTotal; c++) {
                    double anchorX = roomMinX + spacingX * (c + 1);
                    double anchorY = roomMinY + spacingY * (r + 1);
                    result.addAll(expandBOMNode(
                        floatNode,
                        new StoreyCoord(anchorX, anchorY, floorZ, 0.0),
                        roomMinX, roomMinY, roomMaxX, roomMaxY));
                    placed++;
                }
            }
            System.out.printf("[BOM-TIER] %s: CENTER grid %dx%d = %d sets (%.0fm² / %.0f = %d target)%n",
                roomName, cols, rows, placed, area, areaPerSet, gridTotal);
            return result;
        }

        int setCount = (area >= BIG_ROOM_AREA && roomW >= BIG_ROOM_MIN_DIM
                        && roomD >= BIG_ROOM_MIN_DIM) ? 2 : 1;

        List<PlacedFurniture> result = new ArrayList<>();
        String workWall = selectWorkWall(roomW, roomD, openings);

        for (int setIdx = 0; setIdx < setCount; setIdx++) {
            double zoneMinX, zoneMaxX, zoneMinY, zoneMaxY;
            if (setCount == 1) {
                zoneMinX = roomMinX; zoneMaxX = roomMaxX;
                zoneMinY = roomMinY; zoneMaxY = roomMaxY;
            } else if (roomD >= roomW) {
                double mid = (roomMinY + roomMaxY) / 2;
                zoneMinX = roomMinX; zoneMaxX = roomMaxX;
                zoneMinY = (setIdx == 0) ? roomMinY : mid;
                zoneMaxY = (setIdx == 0) ? mid : roomMaxY;
            } else {
                double mid = (roomMinX + roomMaxX) / 2;
                zoneMinX = (setIdx == 0) ? roomMinX : mid;
                zoneMaxX = (setIdx == 0) ? mid : roomMaxX;
                zoneMinY = roomMinY; zoneMaxY = roomMaxY;
            }

            boolean mirrored = (setIdx == 1);

            if (hasOffsets) {
                BOMChild primary = floatNode.children().get(0);
                String wall = resolveWall(resolvedWallRule(primary), workWall);
                if (mirrored) wall = LocalCoord.oppositeCardinal(wall);

                StoreyCoord anchor = computeZoneAnchor(
                    wall, primary.paramDouble("wall_offset", WALL_OFFSET),
                    zoneMinX, zoneMinY, zoneMaxX, zoneMaxY, floorZ);

                // Strip wall_rule from params to prevent double-negation in expandBOMNode
                Map<String, String> strippedParams = new HashMap<>(primary.params());
                strippedParams.remove("wall_rule");
                strippedParams.remove("opposite_wall");
                BOMChild primaryStripped = new BOMChild(
                    primary.id(), primary.bomId(), primary.role(), primary.childProductId(),
                    primary.namePattern(), primary.locatorRef(),
                    primary.dx(), primary.dy(), primary.dz(),
                    primary.sequence(), primary.isVariance(), primary.layoutStrategy(),
                    strippedParams);

                List<BOMChild> expandChildren = new ArrayList<>(floatNode.children());
                expandChildren.set(0, primaryStripped);
                BOMNode expandNode = new BOMNode(floatNode.bomId(), expandChildren,
                    floatNode.originX(), floatNode.originY(), floatNode.originZ());

                result.addAll(expandBOMNode(
                    expandNode, anchor,
                    zoneMinX, zoneMinY, zoneMaxX, zoneMaxY));
            } else {
                for (BOMChild zoneChild : floatNode.children()) {
                    String wall = resolveWall(resolvedWallRule(zoneChild), workWall);
                    if (mirrored) wall = LocalCoord.oppositeCardinal(wall);

                    StoreyCoord anchor = computeZoneAnchor(
                        wall, zoneChild.paramDouble("wall_offset", WALL_OFFSET),
                        zoneMinX, zoneMinY, zoneMaxX, zoneMaxY, floorZ);

                    if (zoneChild.childBomId() != null) {
                        BOMNode subNode = bomTree.get(zoneChild.childBomId());
                        if (subNode != null) {
                            result.addAll(expandBOMNode(
                                subNode, anchor,
                                zoneMinX, zoneMinY, zoneMaxX, zoneMaxY));
                        }
                    } else {
                        String[] zMat = lookupMaterial(zoneChild.productRef(), zoneChild.namePattern());
                        result.add(new PlacedFurniture(
                            zoneChild.role(),
                            anchor.x(), anchor.y(), anchor.z(),
                            anchor.rotation(),
                            zoneChild.namePattern(), zoneChild.productRef(),
                            zMat != null ? zMat[0] : null, zMat != null ? zMat[1] : null));
                    }
                }
            }
        }

        return result;
    }

    // ── Existing geometry methods ─────────────────────────────────────────────

    /**
     * Recursively expand a BOM node, accumulating offsets relative to a typed anchor.
     */
    private List<PlacedFurniture> expandBOMNode(
            BOMNode node, StoreyCoord anchor,
            double zoneMinX, double zoneMinY, double zoneMaxX, double zoneMaxY) {

        List<PlacedFurniture> result = new ArrayList<>();
        double tol = 0.5;
        int skippedOutOfBounds = 0;

        for (BOMChild child : node.children()) {
            if (child.isVariance()) continue;

            StoreyCoord childAnchor = anchor;
            if ("OPPOSITE_WORK".equals(resolvedWallRule(child))) {
                String oppWall = LocalCoord.oppositeCardinal(LocalCoord.radiansToCardinal(anchor.rotation()));
                childAnchor = computeZoneAnchor(
                    oppWall, child.paramDouble("wall_offset", WALL_OFFSET),
                    zoneMinX, zoneMinY, zoneMaxX, zoneMaxY, anchor.z());
            }
            String wallCtx = LocalCoord.radiansToCardinal(childAnchor.rotation());
            double effectiveDx = node.originX() + child.dx();
            double effectiveDy = node.originY() + child.dy();
            double effectiveDz = node.originZ() + child.dz();
            LocalCoord offset = LocalCoord.fromBOMChild(
                effectiveDx, effectiveDy, effectiveDz,
                child.param("rotation_rule"), wallCtx);
            WorldCoord childWorld = offset.toWorld(childAnchor);

            System.out.printf("[TRANSLATE] %s: anchor=(%.3f,%.3f,%.3f,rot=%.3f) + offset=(%.3f,%.3f,%.3f,rot=%.3f) = world(%.3f,%.3f,%.3f)%n",
                child.namePattern() != null ? child.namePattern() : child.role(),
                childAnchor.x(), childAnchor.y(), childAnchor.z(), childAnchor.rotation(),
                offset.dx(), offset.dy(), offset.dz(), offset.rotation(),
                childWorld.x(), childWorld.y(), childWorld.z());

            if (childWorld.x() < zoneMinX - tol || childWorld.x() > zoneMaxX + tol
                || childWorld.y() < zoneMinY - tol || childWorld.y() > zoneMaxY + tol) {
                skippedOutOfBounds++;
                continue;
            }

            String[] eMat = lookupMaterial(child.productRef(), child.namePattern());
            result.add(new PlacedFurniture(
                child.role(),
                childWorld.x(), childWorld.y(), childWorld.z(), childWorld.rotation(),
                child.namePattern(), child.productRef(),
                eMat != null ? eMat[0] : null, eMat != null ? eMat[1] : null));

            if (child.childBomId() != null) {
                BOMNode subNode = bomTree.get(child.childBomId());
                if (subNode != null) {
                    result.addAll(expandBOMNode(subNode,
                        StoreyCoord.fromWorld(childWorld),
                        zoneMinX, zoneMinY, zoneMaxX, zoneMaxY));
                }
            }
        }

        if (skippedOutOfBounds > 0) {
            System.out.printf("[WARN] expandBOMNode: %d/%d children skipped (out of zone bounds)%n",
                skippedOutOfBounds, node.children().size());
        }

        return result;
    }



    /**
     * Score walls by opening count and length. Pick wall with fewest openings;
     * break ties by longest wall.
     */
    String selectWorkWall(double roomW, double roomD, List<OpeningInfo> openings) {
        Map<String, Integer> openingCount = new HashMap<>();
        for (String w : List.of("north", "south", "east", "west")) {
            openingCount.put(w, 0);
        }
        if (openings != null) {
            for (OpeningInfo o : openings) {
                if (o.wall() != null) {
                    openingCount.merge(o.wall().toLowerCase(), 1, Integer::sum);
                }
            }
        }

        String bestWall = "north";
        double bestScore = Double.NEGATIVE_INFINITY;

        for (var entry : openingCount.entrySet()) {
            String wall = entry.getKey();
            int count = entry.getValue();
            double length = (wall.equals("north") || wall.equals("south")) ? roomW : roomD;
            double score = -10.0 * count + length;
            if (score > bestScore) {
                bestScore = score;
                bestWall = wall;
            }
        }

        return bestWall;
    }

    private String resolveWall(String wallRule, String workWall) {
        if (wallRule == null || wallRule.equals("NO_OPENINGS")) {
            return workWall;
        }
        if (wallRule.equals("NORTH_WALL")) return "north";
        if (wallRule.equals("SOUTH_WALL")) return "south";
        if (wallRule.equals("EAST_WALL"))  return "east";
        if (wallRule.equals("WEST_WALL"))  return "west";
        if (wallRule.equals("OPPOSITE_WORK")) {
            return LocalCoord.oppositeCardinal(workWall);
        }
        if (wallRule.equals("END_WALL")) {
            return (workWall.equals("north") || workWall.equals("south")) ? "east" : "south";
        }
        if (wallRule.equals("CENTER")) {
            return "center";
        }
        return workWall;
    }


    /**
     * Compute wall anchor as a typed {@link StoreyCoord}.
     */
    StoreyCoord computeZoneAnchor(
            String wall, double wallOffset,
            double minX, double minY, double maxX, double maxY, double floorZ) {
        double cx = (minX + maxX) / 2;
        double cy = (minY + maxY) / 2;

        return switch (wall) {
            case "north"  -> new StoreyCoord(cx,               maxY - wallOffset, floorZ, LocalCoord.cardinalToRadians("north"));
            case "south"  -> new StoreyCoord(cx,               minY + wallOffset, floorZ, LocalCoord.cardinalToRadians("south"));
            case "east"   -> new StoreyCoord(maxX - wallOffset, cy,               floorZ, LocalCoord.cardinalToRadians("east"));
            case "west"   -> new StoreyCoord(minX + wallOffset, cy,               floorZ, LocalCoord.cardinalToRadians("west"));
            case "center" -> new StoreyCoord(cx,               cy,               floorZ, 0.0);
            default       -> new StoreyCoord(cx,               maxY - wallOffset, floorZ, LocalCoord.cardinalToRadians("north"));
        };
    }

    /**
     * Lightweight opening info — decoupled from BuildingCompiler.OpeningSpec.
     */
    public record OpeningInfo(String type, String wall, double width) {
        public OpeningInfo(String type, String wall) {
            this(type, wall, 0);
        }
    }

    /** Expose product dims cache for workers that need geometry lookup. */
    public double[] getProductDims(String productRef) {
        return productDimCache.get(productRef);
    }
}
