package com.bim.compiler.library;

import com.bim.compiler.coordinate.LocalCoord;
import com.bim.compiler.coordinate.StoreyCoord;
import com.bim.compiler.coordinate.WorldCoord;
import com.bim.compiler.dsl.PhantomLayout;
import com.bim.compiler.dsl.Place;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.Vector3D;
import com.bim.orm.ModelQuery;
import com.bim.ormsandbox.po.X_M_BOMLine;
import com.bim.ormsandbox.po.X_M_Attribute;
import com.bim.ormsandbox.po.X_AdProductDim;

import java.sql.*;
import java.util.*;

/**
 * Phase 93: Data-driven furniture resolver using m_bom / m_bom_line / m_attribute.
 *
 * Loads ROOM_FURNITURE BOM tree from component_library.db, resolves per-room:
 * - Scores walls for opening avoidance (work zone against fewest-opening wall)
 * - Guest seat on end wall, back to wall
 * - Big rooms (≥80m², dims≥3m): 2 mirrored zones
 *
 * Federation-extracted offsets: officer chair in L-curve (dy=+0.36),
 * visitor chairs along desk arm (dx=+0.95, +1.76, dy=+0.17).
 *
 * <p>Phase 4c: ORM migration of loadBOMTree(). Contrast with remaining raw-JDBC
 * paths (expandBOMNode, computeZoneAnchor) makes future migration candidates obvious.
 * New GPD dispatch (resolveWithGPD) handles NORTH_WALL/SOUTH_WALL/EAST_WALL/WEST_WALL
 * children via PhantomLayout. FLOAT children continue through the existing dx/dy path.
 */
public class FurnitureBOMResolver {

    private static final String LIB_PATH = "library/component_library.db";
    private static final double BIG_ROOM_AREA = 80.0;
    private static final double BIG_ROOM_MIN_DIM = 3.0;
    private static final double WALL_OFFSET = 0.5;

    // BOM tree loaded from library
    private final Map<String, BOMNode> bomTree = new HashMap<>();

    // Phase 4c: product dims cache — keyed by product_id (meters, from ad_product_dim)
    private final Map<String, double[]> productDimCache = new HashMap<>();

    public record BOMNode(String bomId, List<BOMChild> children) {}

    /**
     * One child in a BOM assembly.
     *
     * <p>Phase 4c additions (last four fields):
     * <ul>
     *   <li>{@code locatorRef} — M_Locator zone (NORTH_WALL, SOUTH_WALL, …, FLOAT).
     *       FLOAT → existing dx/dy expandBOMNode path. Any wall locator → GPD walk.</li>
     *   <li>{@code isVariance} — true for SPACER_VAR: bbox is null, extentMm()=0,
     *       absorbs remainingMm at locator completion.</li>
     *   <li>{@code layoutStrategy} — LINEAR (GPD walk) or FLOAT (explicit dx/dy).</li>
     *   <li>{@code sequence} — GPD placement order within the locator (ascending).</li>
     * </ul>
     */
    public record BOMChild(int id, String role, String childBomId, String namePattern,
                           double xOffset, double yOffset, double zOffset, double rotation,
                           String zone, String wallRule, double wallOffset,
                           boolean backToWall, String productRef,
                           String locatorRef, boolean isVariance, String layoutStrategy,
                           int sequence) {}

    public record PlacedFurniture(String role, double x, double y, double z,
                                  double rotation, String namePattern, String productRef) {}

    public FurnitureBOMResolver() {
        loadBOMTree();
    }

    // ── Phase 4c: ORM-backed BOM tree loader ──────────────────────────────────

    private static final String BOM_PATH = "library/BOM.db";

    private void loadBOMTree() {
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + BOM_PATH);
             Connection libConn = DriverManager.getConnection("jdbc:sqlite:" + LIB_PATH)) {

            // ① Load all active BOM children from all active BOMs — ORM path.
            // No group_by filter: loads ROOM BOMs and sub-BOMs (SOFA_AREA, etc.)
            // so that child_bom_id references resolve in expandBOMNode.
            List<X_M_BOMLine> rawChildren = new ModelQuery<>(
                    bomConn, X_M_BOMLine::new, X_M_BOMLine.Table_Name + " bc")
                .addJoin("m_bom b", "bc.bom_id = b.bom_id")
                .where("b.is_active = ?", 1)
                .andWhere("bc.is_active = ?", 1)
                .orderBy("bc.bom_id, bc.sequence")
                .list();

            // ② Load ALL params at once — single query, group by bom_child_id
            List<X_M_Attribute> allParams = new ModelQuery<>(
                    bomConn, X_M_Attribute::new, X_M_Attribute.Table_Name)
                .where("is_active = ?", 1)
                .list();

            Map<Integer, Map<String, String>> paramsByChildId = new HashMap<>();
            for (X_M_Attribute p : allParams) {
                paramsByChildId
                    .computeIfAbsent(p.getBomChildId(), k -> new HashMap<>())
                    .put(p.getParamKey(), p.getParamValue());
            }

            // ③ Load all product dims — for GPD extentMm() calculation (stays on component_library.db)
            List<X_AdProductDim> allDims = new ModelQuery<>(
                    libConn, X_AdProductDim::new, X_AdProductDim.Table_Name)
                .where("is_active = ?", 1)
                .list();

            for (X_AdProductDim dim : allDims) {
                productDimCache.put(dim.getProductId(),
                    new double[]{dim.getWidth(), dim.getDepth(), dim.getHeight()});
            }

            // ④ Assemble BOMChild records — typed getters from X_M_BOMLine
            for (X_M_BOMLine raw : rawChildren) {
                Map<String, String> params = paramsByChildId.getOrDefault(
                    raw.getBomChildId(), Map.of());

                // Resolve wall_rule from params (legacy opposite_wall support)
                String wallRule = params.get("wall_rule");
                if (wallRule == null && "true".equalsIgnoreCase(params.get("opposite_wall"))) {
                    wallRule = "OPPOSITE_WORK";
                }

                // name_pattern param overrides child_name_pattern column
                String nameOverride = params.get("name_pattern");
                String effectiveName = nameOverride != null
                    ? nameOverride : raw.getChildNamePattern();

                // THREE-TABLE AUTHORITY: dx/dy/dz come from m_bom_line columns (ORM).
                // Params may override (legacy m_attribute dx/x_offset support).
                BOMChild child = new BOMChild(
                    raw.getBomChildId(),
                    raw.getRole(),
                    raw.getChildBomId(),
                    effectiveName,
                    parseDouble(params, "dx",           parseDouble(params, "x_offset", raw.getDx())),
                    parseDouble(params, "dy",           parseDouble(params, "y_offset", raw.getDy())),
                    parseDouble(params, "dz",           parseDouble(params, "z_offset", raw.getDz())),
                    parseDouble(params, "rotation_rule", 0),
                    params.get("zone"),
                    wallRule,
                    parseDouble(params, "wall_offset",  WALL_OFFSET),
                    "true".equalsIgnoreCase(params.get("back_to_wall")),
                    raw.getProductRef(),
                    raw.getLocatorRef(),       // Phase 4c — ORM getter
                    raw.isVariance(),           // Phase 4c — ORM getter
                    raw.getLayoutStrategy(),    // Phase 4c — ORM getter
                    raw.getSequence()           // Phase 4c — ORM getter
                );

                bomTree.computeIfAbsent(raw.getBomId(),
                    k -> new BOMNode(k, new ArrayList<>()))
                    .children().add(child);
            }

            System.out.printf("[FURNITURE-BOM] Loaded %d BOM nodes, %d product dims%n",
                bomTree.size(), productDimCache.size());

        } catch (SQLException e) {
            System.err.println("[FURNITURE-BOM] Failed to load: " + e.getMessage());
        }
    }

    private static double parseDouble(Map<String, String> params, String key, double def) {
        String val = params.get(key);
        if (val == null) return def;
        try { return Double.parseDouble(val); } catch (NumberFormatException e) { return def; }
    }

    /**
     * Resolve furniture placement for a room using the default ROOM_FURNITURE BOM.
     */
    public List<PlacedFurniture> resolveForRoom(
            double roomMinX, double roomMinY, double roomMaxX, double roomMaxY,
            double floorZ, String roomName, String roomType,
            List<OpeningInfo> openings) {
        return resolveForRoom(roomMinX, roomMinY, roomMaxX, roomMaxY,
            floorZ, roomName, roomType, openings, "ROOM_FURNITURE");
    }

    /**
     * Phase 109: Resolve furniture placement for a room using a specific BOM ID.
     *
     * <p>Phase 4c dispatch: if any child has a wall locatorRef (non-FLOAT),
     * those children are routed through {@link #resolveWithGPD}. FLOAT children
     * continue through the existing expandBOMNode dx/dy path.
     */
    public List<PlacedFurniture> resolveForRoom(
            double roomMinX, double roomMinY, double roomMaxX, double roomMaxY,
            double floorZ, String roomName, String roomType,
            List<OpeningInfo> openings, String bomId) {

        double roomW = roomMaxX - roomMinX;
        double roomD = roomMaxY - roomMinY;
        double area = roomW * roomD;

        // Phase 122C: Lower threshold from 6.0 to 2.0 — bathrooms need vanity cabinets
        if (area < 2.0) return List.of();

        BOMNode roomFurniture = bomTree.get(bomId);
        if (roomFurniture == null || roomFurniture.children().isEmpty()) {
            return List.of();
        }

        // Phase 4c: partition children by locatorRef
        // Wall-locator children (NORTH_WALL etc.) go to resolveWithGPD.
        // FLOAT children stay on the existing expandBOMNode path.
        Map<String, List<BOMChild>> byLocator = new LinkedHashMap<>();
        List<BOMChild> floatChildren = new ArrayList<>();
        for (BOMChild c : roomFurniture.children()) {
            if ("FLOAT".equals(c.locatorRef())) {
                floatChildren.add(c);
            } else {
                byLocator.computeIfAbsent(c.locatorRef(), k -> new ArrayList<>()).add(c);
            }
        }

        List<PlacedFurniture> result = new ArrayList<>();

        // ── GPD walk for wall-locator children ────────────────────────────────
        for (var entry : byLocator.entrySet()) {
            List<BOMChild> wallChildren = entry.getValue();
            wallChildren.sort(Comparator.comparingInt(BOMChild::sequence));
            result.addAll(resolveWithGPD(
                wallChildren, entry.getKey(),
                roomMinX, roomMinY, roomMaxX, roomMaxY, floorZ, roomName));
        }

        // ── Legacy dx/dy path for FLOAT children ──────────────────────────────
        if (!floatChildren.isEmpty()) {
            BOMNode floatNode = new BOMNode(roomFurniture.bomId(), floatChildren);
            result.addAll(resolveFloatChildren(
                floatNode, roomMinX, roomMinY, roomMaxX, roomMaxY,
                floorZ, roomName, roomType, openings, area));
        }

        return result;
    }

    // ── Phase 4c: GPD walk ────────────────────────────────────────────────────

    /**
     * Place children against a named wall locator using PhantomLayout GPD walk.
     *
     * <p>The PhantomLayout origin (NW corner for NORTH_WALL) is in meters.
     * Capacity is room width/height in mm.
     * Each non-variance child is placed as a {@link Place.fixed}; SPACER_VAR
     * as {@link Place#variance}.
     * PlacedFurniture world position = item centroid in world coordinates.
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
                // SPACER_VAR: zero extent, anchored at current GPD position
                Place spacer = Place.variance(
                    child.namePattern(), locatorRef, child.sequence(),
                    ph.nextAnchor(), front, hostAxis);
                ph.placeNext(spacer);
                // No PlacedFurniture for variance — it's a capacity absorber only
                continue;
            }

            // Look up product dims — productRef first, then namePattern
            String dimKey = (child.productRef() != null && !child.productRef().isBlank())
                ? child.productRef() : child.namePattern();
            double[] dims = productDimCache.get(dimKey);
            if (dims == null) {
                System.err.printf("[GPD] No dims for '%s' in %s — skipped%n",
                    dimKey, roomName);
                continue;
            }

            double widthM  = dims[0];   // meters — along hostAxis
            double depthM  = dims[1];   // meters — into room (perpendicular to wall)
            double heightM = dims[2];   // meters

            BoundingBox bbox = new BoundingBox(0, widthM, 0, depthM, 0, heightM);
            Place place = Place.fixed(child.namePattern(), locatorRef, child.sequence(),
                bbox, ph.nextAnchor(), front, hostAxis);
            ph.placeNext(place);

            // Centroid: advance half the item's extent along hostAxis + half depth into room
            Point3D anchor = place.anchor();
            double halfW = widthM / 2.0;
            double halfD = depthM / 2.0;

            double cx, cy;
            if ("NORTH_WALL".equals(locatorRef) || "SOUTH_WALL".equals(locatorRef)) {
                cx = anchor.x() + halfW;
                cy = "NORTH_WALL".equals(locatorRef)
                   ? anchor.y() - halfD    // into room from max_y
                   : anchor.y() + halfD;   // into room from min_y
            } else {
                // EAST_WALL / WEST_WALL — advancing along Y
                cy = anchor.y() + halfW;   // items placed along Y
                cx = "EAST_WALL".equals(locatorRef)
                   ? anchor.x() - halfD    // into room from max_x
                   : anchor.x() + halfD;   // into room from min_x
            }

            result.add(new PlacedFurniture(
                child.role(), cx, cy, floorZ, rotation,
                child.namePattern(), child.productRef()));

            // BOMCascadeResolver Step 1: expand sub-BOM at this item's placed centroid
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

    /**
     * Handle FLOAT children using the existing expandBOMNode dx/dy logic.
     * Extracted from resolveForRoom to keep the dispatch clean.
     */
    private List<PlacedFurniture> resolveFloatChildren(
            BOMNode floatNode,
            double roomMinX, double roomMinY, double roomMaxX, double roomMaxY,
            double floorZ, String roomName, String roomType,
            List<OpeningInfo> openings, double area) {

        double roomW = roomMaxX - roomMinX;
        double roomD = roomMaxY - roomMinY;

        // Phase 122F: Check for CENTER grid placement (canteen-style area-based replication)
        BOMChild primaryChild = floatNode.children().get(0);
        boolean hasOffsets = floatNode.children().stream()
            .anyMatch(c -> c.xOffset() != 0 || c.yOffset() != 0);
        boolean isCenterGrid = hasOffsets && "CENTER".equals(primaryChild.wallRule()) && area >= 20.0;

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
            System.out.printf("[FURNITURE] %s: CENTER grid %dx%d = %d sets (%.0fm² / %.0f = %d target)%n",
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
                String wall = resolveWall(primary.wallRule(), workWall);
                if (mirrored) wall = oppositeWall(wall);

                StoreyCoord anchor = computeZoneAnchor(
                    wall, primary.wallOffset(),
                    zoneMinX, zoneMinY, zoneMaxX, zoneMaxY, floorZ);

                // Strip wall_rule from primary to prevent double-negation in expandBOMNode
                BOMChild primaryStripped = new BOMChild(
                    primary.id(), primary.role(), primary.childBomId(),
                    primary.namePattern(), primary.xOffset(), primary.yOffset(),
                    primary.zOffset(), primary.rotation(), primary.zone(), null,
                    primary.wallOffset(), primary.backToWall(), primary.productRef(),
                    primary.locatorRef(), primary.isVariance(), primary.layoutStrategy(),
                    primary.sequence());

                List<BOMChild> expandChildren = new ArrayList<>(floatNode.children());
                expandChildren.set(0, primaryStripped);
                BOMNode expandNode = new BOMNode(floatNode.bomId(), expandChildren);

                result.addAll(expandBOMNode(
                    expandNode, anchor,
                    zoneMinX, zoneMinY, zoneMaxX, zoneMaxY));
            } else {
                for (BOMChild zoneChild : floatNode.children()) {
                    String wall = resolveWall(zoneChild.wallRule(), workWall);
                    if (mirrored) wall = oppositeWall(wall);

                    StoreyCoord anchor = computeZoneAnchor(
                        wall, zoneChild.wallOffset(),
                        zoneMinX, zoneMinY, zoneMaxX, zoneMaxY, floorZ);

                    if (zoneChild.childBomId() != null) {
                        BOMNode subNode = bomTree.get(zoneChild.childBomId());
                        if (subNode != null) {
                            result.addAll(expandBOMNode(
                                subNode, anchor,
                                zoneMinX, zoneMinY, zoneMaxX, zoneMaxY));
                        }
                    } else {
                        result.add(new PlacedFurniture(
                            zoneChild.role(),
                            anchor.x(), anchor.y(), anchor.z(),
                            anchor.rotation(),
                            zoneChild.namePattern(), zoneChild.productRef()));
                    }
                }
            }
        }

        return result;
    }

    // ── Existing geometry methods (unchanged — raw JDBC debt acknowledged) ────

    /**
     * Recursively expand a BOM node, accumulating offsets relative to a typed anchor.
     *
     * <p>Each child's {@link LocalCoord} (dx, dy, dz, rotation from m_attribute)
     * is accumulated into a {@link WorldCoord} via {@link LocalCoord#toWorld(StoreyCoord)}.
     * For nested sub-BOMs, the child's world position becomes the new anchor via
     * {@link StoreyCoord#fromWorld(WorldCoord)}.
     *
     * <p>Children with dx=0/dy=0 are placed directly at the anchor — two such children
     * produce the same world position (bunching). Fix: ensure distinct dx/dy in
     * m_attribute for siblings that must be at different positions.
     *
     * @param anchor {@link StoreyCoord} carrying both position (x,y,z) and wall orientation
     *               (rotation) — eliminates the separate parentRotation parameter
     */
    private List<PlacedFurniture> expandBOMNode(
            BOMNode node, StoreyCoord anchor,
            double zoneMinX, double zoneMinY, double zoneMaxX, double zoneMaxY) {

        List<PlacedFurniture> result = new ArrayList<>();
        double tol = 0.5;

        for (BOMChild child : node.children()) {
            // Variance children (SPACER_VAR) are capacity absorbers only — no geometry
            if (child.isVariance()) continue;

            // If child declares OPPOSITE_WORK, re-anchor to the wall opposite to the primary anchor.
            StoreyCoord childAnchor = anchor;
            if ("OPPOSITE_WORK".equals(child.wallRule())) {
                String oppWall = oppositeWall(rotationToWall(anchor.rotation()));
                childAnchor = computeZoneAnchor(
                    oppWall, child.wallOffset(),
                    zoneMinX, zoneMinY, zoneMaxX, zoneMaxY, anchor.z());
            }
            LocalCoord offset = new LocalCoord(
                child.xOffset(), child.yOffset(), child.zOffset(), child.rotation());
            WorldCoord childWorld = offset.toWorld(childAnchor);

            // [TRANSLATE] witness — one line per child: anchor + offset → world
            System.out.printf("[TRANSLATE] %s: anchor=(%.3f,%.3f,%.3f,rot=%.3f) + offset=(%.3f,%.3f,%.3f,rot=%.3f) = world(%.3f,%.3f,%.3f)%n",
                child.namePattern() != null ? child.namePattern() : child.role(),
                childAnchor.x(), childAnchor.y(), childAnchor.z(), childAnchor.rotation(),
                child.xOffset(), child.yOffset(), child.zOffset(), child.rotation(),
                childWorld.x(), childWorld.y(), childWorld.z());

            // Bounds check — skip if outside room (BOM children may extend slightly past room edge)
            if (childWorld.x() < zoneMinX - tol || childWorld.x() > zoneMaxX + tol
                || childWorld.y() < zoneMinY - tol || childWorld.y() > zoneMaxY + tol) {
                continue;
            }

            // Always place the item itself, then expand any sub-BOM at its centroid
            result.add(new PlacedFurniture(
                child.role(),
                childWorld.x(), childWorld.y(), childWorld.z(), childWorld.rotation(),
                child.namePattern(), child.productRef()));

            if (child.childBomId() != null) {
                BOMNode subNode = bomTree.get(child.childBomId());
                if (subNode != null) {
                    result.addAll(expandBOMNode(subNode,
                        StoreyCoord.fromWorld(childWorld),
                        zoneMinX, zoneMinY, zoneMaxX, zoneMaxY));
                }
            }
        }

        return result;
    }

    /**
     * Convert wall name to rotation angle.
     * Convention: rotation=0 means furniture faces +Y (north).
     * "back to wall" means the furniture's back is against the named wall.
     *
     * south wall → back to south, face north → rotation = 0
     * north wall → back to north, face south → rotation = π
     * west wall  → back to west, face east   → rotation = -π/2
     * east wall  → back to east, face west   → rotation = π/2
     */
    private double wallToRotation(String wall) {
        return switch (wall) {
            case "south" -> 0;
            case "north" -> Math.PI;
            case "west"  -> -Math.PI / 2;
            case "east"  -> Math.PI / 2;
            default      -> 0;
        };
    }

    /** Reverse of wallToRotation — maps rotation back to the wall name. */
    private String rotationToWall(double rotation) {
        if (Math.abs(rotation) < 0.01) return "south";
        if (Math.abs(rotation - Math.PI) < 0.01) return "north";
        if (Math.abs(rotation + Math.PI / 2) < 0.01) return "west";
        if (Math.abs(rotation - Math.PI / 2) < 0.01) return "east";
        return "south";
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
        // Explicit wall overrides — ignore workWall entirely
        if (wallRule.equals("NORTH_WALL")) return "north";
        if (wallRule.equals("SOUTH_WALL")) return "south";
        if (wallRule.equals("EAST_WALL"))  return "east";
        if (wallRule.equals("WEST_WALL"))  return "west";
        if (wallRule.equals("OPPOSITE_WORK")) {
            return oppositeWall(workWall);
        }
        if (wallRule.equals("END_WALL")) {
            return (workWall.equals("north") || workWall.equals("south")) ? "east" : "south";
        }
        if (wallRule.equals("CENTER")) {
            return "center";
        }
        return workWall;
    }

    private String oppositeWall(String wall) {
        return switch (wall) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            case "west" -> "east";
            default -> "south";
        };
    }

    /**
     * Compute wall anchor as a typed {@link StoreyCoord}.
     *
     * <p>The returned anchor carries both position (x, y, z) and the wall-facing
     * orientation (rotation), so callers no longer need a separate {@code wallRotation}
     * variable. The rotation is consumed by {@link LocalCoord#toWorld(StoreyCoord)} when
     * children are accumulated.
     */
    private StoreyCoord computeZoneAnchor(
            String wall, double wallOffset,
            double minX, double minY, double maxX, double maxY, double floorZ) {
        double cx = (minX + maxX) / 2;
        double cy = (minY + maxY) / 2;

        return switch (wall) {
            case "north"  -> new StoreyCoord(cx,               maxY - wallOffset, floorZ, wallToRotation("north"));
            case "south"  -> new StoreyCoord(cx,               minY + wallOffset, floorZ, wallToRotation("south"));
            case "east"   -> new StoreyCoord(maxX - wallOffset, cy,               floorZ, wallToRotation("east"));
            case "west"   -> new StoreyCoord(minX + wallOffset, cy,               floorZ, wallToRotation("west"));
            case "center" -> new StoreyCoord(cx,               cy,               floorZ, 0.0);
            default       -> new StoreyCoord(cx,               maxY - wallOffset, floorZ, wallToRotation("north"));
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
}
