package com.bim.compiler.dsl;

import com.bim.compiler.library.BOMTierResolver;
import com.bim.orm.ModelQuery;
import com.bim.ormsandbox.po.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase RM-2: Computes element coordinates from relational metadata.
 * Reads: ad_building_grid, ad_room_boundary, ad_wall_face, ad_element_rule
 * Produces: PlacementAD.Placement records identical to lod_element_placement.
 *
 * Shadow mode: compute placements, validate against stored oracle.
 * Does NOT replace the compiler's input — that's Phase RM-3.
 *
 * Pattern: stateless resolver, lazy singleton (same as PlacementAD).
 */
class RelationalResolver {

    private static final String DB_PATH = "library/BOM.db";

    // Internal records for intermediate computation
    record RoomExtent(String name, String storey, String type,
                      double minXmm, double maxXmm, double minYmm, double maxYmm) {}

    record WallSegment(String key, String roomName, String storey, String face,
                       double x1mm, double y1mm, double x2mm, double y2mm,
                       boolean exterior) {}

    /** Phase BOM-2c: UNIT→FLOOR and FLOOR→SET link in m_bom_line (via child_bom_id). */
    record BomLink(String parentBomId, String childBomId, String role) {}

    /** Phase BOM-2c: ad_room_slot entry — which SET BOM fits which room type and at what min area. */
    record RoomSlotEntry(String assemblyId, String roomType, String slotName, double minAreaM2) {}

    record ResolutionContext(String buildingType,
                             Map<String, RoomExtent> rooms,
                             Map<String, WallSegment> walls,
                             Map<String, String> connPointsByProduct,
                             Set<String> bomIds,
                             Map<String, double[]> productDims,
                             Map<String, List<BomLink>> bomChain,
                             Map<String, String> floorStoreys,
                             Map<String, Double> floorZOffsets,
                             Map<String, Double> floorOrientations,
                             Map<String, List<RoomSlotEntry>> slotsByAssembly) {
        WallSegment wallOrThrow(String wallRef, String elementRef) {
            WallSegment wall = walls.get(wallRef);
            if (wall == null) throw new IllegalStateException(
                "Element " + elementRef + " references wall '" + wallRef
                + "' not found for " + buildingType);
            return wall;
        }
        RoomExtent roomOrThrow(String roomRef, String elementRef) {
            RoomExtent room = rooms.get(roomRef);
            if (room == null) throw new IllegalStateException(
                "Element " + elementRef + " references room '" + roomRef
                + "' not found for " + buildingType);
            return room;
        }
    }

    record ElementRule(String elementRef, String ifcClass, String storey, String discipline,
                       Double heightMm, String familyRef,
                       Double widthMm, Double heightExtentMm, Double depthMm,
                       String orientation, String materialName, String materialRgba,
                       PositionRule positionMode) {}

    /**
     * Resolve all elements for a building into Placement records.
     * Returns list matching lod_element_placement format.
     */
    List<PlacementAD.Placement> resolve(String buildingType) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            Map<String, RoomExtent> rooms = loadRooms(conn, buildingType);
            Map<String, WallSegment> walls = loadWalls(conn, buildingType, rooms);
            List<ElementRule> rules = loadRules(conn, buildingType);
            List<M_AdProductDim> products = M_AdProductDim.getAll(conn);
            Map<String, String> connPoints = loadConnPoints(products);
            Set<String> bomIds = loadBomIds(conn);
            Map<String, double[]> productDims = loadProductDims(products);
            Map<String, List<BomLink>> bomChain = loadBomChain(conn);
            FloorMaps floors = loadFloorMaps(conn, buildingType);
            Map<String, List<RoomSlotEntry>> slotsByAssembly = loadSlotsByAssembly(conn, buildingType);
            var ctx = new ResolutionContext(buildingType, rooms, walls, connPoints, bomIds,
                                           productDims, bomChain, floors.storeys(),
                                           floors.zOffsets(), floors.orientations(), slotsByAssembly);
            return computeAll(ctx, rules);
        } catch (SQLException e) {
            System.err.println("[RelationalResolver] Failed: " + e.getMessage());
            return List.of();
        }
    }

    // ── Load methods ──────────────────────────────────────────────

    private Map<String, RoomExtent> loadRooms(Connection conn, String buildingType) throws SQLException {
        Map<String, RoomExtent> rooms = new HashMap<>();

        // Grid fallback (raw JDBC — secondary path, no PO, single consumer)
        Map<String, Double> xGrid = new HashMap<>(), yGrid = new HashMap<>();
        try (Statement gs = conn.createStatement();
             ResultSet grs = gs.executeQuery(
                 "SELECT axis, grid_label, position_mm FROM ad_building_grid WHERE building_type = '" + buildingType + "'")) {
            while (grs.next()) {
                if ("X".equals(grs.getString(1))) xGrid.put(grs.getString(2), grs.getDouble(3));
                else yGrid.put(grs.getString(2), grs.getDouble(3));
            }
        }

        for (M_AdRoomBoundary rb : M_AdRoomBoundary.getByBuilding(conn, buildingType)) {
            String name = rb.getRoomName();
            // Prefer exact coords; fall back to grid-resolved
            Double rawMinX = rb.getMinXMmOrNull(), rawMaxX = rb.getMaxXMmOrNull();
            Double rawMinY = rb.getMinYMmOrNull(), rawMaxY = rb.getMaxYMmOrNull();
            double minX = rawMinX != null ? rawMinX : xGrid.getOrDefault(rb.getGridMinX(), 0.0);
            double maxX = rawMaxX != null ? rawMaxX : xGrid.getOrDefault(rb.getGridMaxX(), 0.0);
            double minY = rawMinY != null ? rawMinY : yGrid.getOrDefault(rb.getGridMinY(), 0.0);
            double maxY = rawMaxY != null ? rawMaxY : yGrid.getOrDefault(rb.getGridMaxY(), 0.0);

            rooms.put(name, new RoomExtent(name, rb.getStorey(), rb.getRoomType(),
                minX, maxX, minY, maxY));
        }
        return rooms;
    }

    private Map<String, WallSegment> loadWalls(Connection conn, String buildingType,
                                                Map<String, RoomExtent> rooms) throws SQLException {
        Map<String, WallSegment> walls = new HashMap<>();
        String sql = """
            SELECT room_name, storey, face, wall_type_id, is_exterior
            FROM ad_wall_face WHERE building_type = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingType);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String roomName = rs.getString("room_name");
                String face = rs.getString("face");
                RoomExtent room = rooms.get(roomName);
                if (room == null) continue;

                double x1, y1, x2, y2;
                switch (face) {
                    case "NORTH" -> { x1 = room.minXmm; y1 = room.maxYmm; x2 = room.maxXmm; y2 = room.maxYmm; }
                    case "SOUTH" -> { x1 = room.minXmm; y1 = room.minYmm; x2 = room.maxXmm; y2 = room.minYmm; }
                    case "EAST"  -> { x1 = room.maxXmm; y1 = room.minYmm; x2 = room.maxXmm; y2 = room.maxYmm; }
                    default      -> { x1 = room.minXmm; y1 = room.minYmm; x2 = room.minXmm; y2 = room.maxYmm; } // WEST
                }

                String key = "WALL_" + roomName + "_" + face;
                walls.put(key, new WallSegment(key, roomName, rs.getString("storey"), face,
                    x1, y1, x2, y2, rs.getInt("is_exterior") == 1));
            }
        }
        return walls;
    }

    private List<ElementRule> loadRules(Connection conn, String buildingType) throws SQLException {
        return M_AdElementRule.getByBuilding(conn, buildingType).stream().map(r ->
            new ElementRule(
                r.getElementRef(), r.getIfcClass(), r.getStorey(), r.getDiscipline(),
                r.getHeightMmOrNull(), r.getFamilyRef(),
                r.getWidthMmOrNull(), r.getHeightExtentMmOrNull(), r.getDepthMmOrNull(),
                r.getOrientation(), r.getMaterialName(), r.getMaterialRgba(),
                PositionRule.from(r.getPositionRule(), r.getHostType(), r.getHostRef(),
                                  r.getPositionValueOrNull(), r.getPositionValue2OrNull())
            )).toList();
    }

    /** Phase RM-11 Step 3: Load conn_points JSON keyed by product_id. */
    private Map<String, String> loadConnPoints(List<M_AdProductDim> products) {
        Map<String, String> map = new HashMap<>();
        for (M_AdProductDim p : products) {
            if (p.getConnPoints() != null) map.put(p.getProductId(), p.getConnPoints());
        }
        return map;
    }

    /** Phase RM-6: Load active BOM IDs for ROOM-level assemblies. */
    private Set<String> loadBomIds(Connection conn) throws SQLException {
        return new ModelQuery<>(conn, MBOM::new, X_M_BOM.Table_Name)
            .where(X_M_BOM.COLUMNNAME_is_active + " = ?", 1)
            .andWhere(X_M_BOM.COLUMNNAME_group_by + " = ?", "ROOM")
            .list().stream()
            .map(MBOM::getBomId)
            .collect(Collectors.toSet());
    }

    /**
     * Phase RM-6: Load product dimensions keyed by product_id.
     * Dimensions are stored in METERS in ad_product_dim (verified: FURN_DINING_CHAIR=0.45m).
     * Returns double[]{width, depth, height} in meters.
     */
    private Map<String, double[]> loadProductDims(List<M_AdProductDim> products) {
        Map<String, double[]> dims = new HashMap<>();
        for (M_AdProductDim p : products) {
            dims.put(p.getProductId(), new double[]{p.getWidth(), p.getDepth(), p.getHeight()});
        }
        return dims;
    }

    /** Phase BOM-2c: UNIT→FLOOR and FLOOR→SET links, via child_bom_id column. */
    private Map<String, List<BomLink>> loadBomChain(Connection conn) throws SQLException {
        // Load UNIT and FLOOR parent BOMs
        Set<String> chainParents = new ModelQuery<>(conn, MBOM::new, X_M_BOM.Table_Name)
            .where(X_M_BOM.COLUMNNAME_bom_level + " IN ('UNIT','FLOOR')")
            .andWhere(X_M_BOM.COLUMNNAME_is_active + " = ?", 1)
            .list().stream()
            .map(MBOM::getBomId)
            .collect(Collectors.toSet());

        Map<String, List<BomLink>> chain = new HashMap<>();
        for (String parentId : chainParents) {
            for (MBOMLine line : MBOMLine.getByBom(conn, parentId)) {
                if (line.getChildBomId() != null) {
                    chain.computeIfAbsent(parentId, k -> new ArrayList<>())
                         .add(new BomLink(parentId, line.getChildBomId(), line.getRole()));
                }
            }
        }
        return chain;
    }

    /**
     * Load floor-level metadata from a single DAO query on ad_element_rule
     * (discipline=FURN, host_type=UNIT). Populates three maps:
     *   - floorStoreys:      family_ref → storey name
     *   - floorZOffsets:     family_ref → Z offset in metres (position_value_3 mm / 1000)
     *   - floorOrientations: family_ref → orientation in radians (numeric only)
     */
    private record FloorMaps(Map<String, String> storeys, Map<String, Double> zOffsets,
                             Map<String, Double> orientations) {}

    private FloorMaps loadFloorMaps(Connection conn, String buildingType) throws SQLException {
        List<M_AdElementRule> floorRules = M_AdElementRule.getFloorRules(conn, buildingType);

        Map<String, String> storeys = new HashMap<>();
        Map<String, Double> zOffsets = new HashMap<>();
        Map<String, Double> orientations = new HashMap<>();

        for (M_AdElementRule r : floorRules) {
            String familyRef = r.getFamilyRef();
            if (familyRef == null) continue;

            storeys.put(familyRef, r.getStorey());
            zOffsets.put(familyRef, r.getPositionValue3() / 1000.0);

            String orient = r.getOrientation();
            if (orient != null) {
                try {
                    orientations.put(familyRef, Double.parseDouble(orient));
                } catch (NumberFormatException ignored) {
                    // semantic orientation — skip for floor cascade
                }
            }
        }
        return new FloorMaps(storeys, zOffsets, orientations);
    }

    /** Phase BOM-2c/Check-H: Room slot entries keyed by assembly_id (SET BOM → room types it fits).
     *  Filters by building_type IS NULL OR building_type = buildingType for isolation. */
    private Map<String, List<RoomSlotEntry>> loadSlotsByAssembly(Connection conn, String buildingType)
            throws SQLException {
        Map<String, List<RoomSlotEntry>> map = new HashMap<>();
        String sql = """
            SELECT assembly_id, room_type, slot_name, COALESCE(min_area, 0.0) AS min_area
            FROM ad_room_slot
            WHERE assembly_id IS NOT NULL
              AND (building_type IS NULL OR building_type = ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String asmId = rs.getString("assembly_id");
                    map.computeIfAbsent(asmId, k -> new ArrayList<>())
                       .add(new RoomSlotEntry(asmId, rs.getString("room_type"),
                            rs.getString("slot_name"), rs.getDouble("min_area")));
                }
            }
        }
        return map;
    }

    /**
     * Phase RM-11 Step 3: Derive concrete orientation from conn_points.
     *
     * <p>For ROOM-hosted fixture/furniture elements with NS/EW or null orientation:
     * reads the BACK or WALL conn_point face of the product and infers which host
     * wall the element is placed against (from position fraction), then returns
     * the rotation radians string so the element faces INTO the room.
     *
     * <p>Convention: north wall → element faces south → π (Math.PI).
     *                south wall → element faces north → 0.
     *                east  wall → element faces west  → π/2.
     *                west  wall → element faces east  → -π/2.
     *
     * @return concrete radians string (e.g. "3.141592653589793") or original
     *         orientation if conn_points doesn't apply
     */
    private String resolveOrientation(ElementRule rule, ResolutionContext ctx,
                                       PositionRule pos) {
        if (rule.familyRef == null) return rule.orientation;
        // Only apply to ROOM-hosted fixture/furniture
        if (!(pos instanceof PositionRule.RoomFraction rf)) return rule.orientation;
        String orient = rule.orientation;
        if (orient != null && !orient.equals("NS") && !orient.equals("EW"))
            return orient;  // already concrete (e.g. radians or semantic)

        String connJson = ctx.connPointsByProduct().get(rule.familyRef);
        if (connJson == null) return orient;

        // Look for BACK or WALL face type — these require the element to be
        // placed against a wall (back/wall face touches the wall surface).
        boolean hasWallConnection = connJson.contains("\"BACK\"")
            || connJson.contains("\"WALL\"");
        if (!hasWallConnection) return orient;

        // Infer host wall from position fraction:
        //   EW orientation → element back is against EAST or WEST wall → use X fraction
        //   NS orientation (or null) → element back is against NORTH or SOUTH → use Y fraction
        String hostWall;
        if ("EW".equals(orient)) {
            hostWall = rf.fractionX() >= 0.5 ? "east" : "west";
        } else {
            // NS or null
            hostWall = rf.fractionY() >= 0.5 ? "north" : "south";
        }

        // rotationFacingInto: north→π, south→0, east→π/2, west→-π/2
        double rotation = switch (hostWall) {
            case "north" -> Math.PI;
            case "south" -> 0.0;
            case "east"  -> Math.PI / 2.0;
            case "west"  -> -Math.PI / 2.0;
            default      -> 0.0;
        };
        return String.valueOf(rotation);
    }

    // ── Computation ──────────────────────────────────────────────

    private BOMTierResolver bomResolver;  // Phase RM-6: lazy-init

    private BOMTierResolver getBomResolver() {
        if (bomResolver == null) bomResolver = new BOMTierResolver();
        return bomResolver;
    }

    private List<PlacementAD.Placement> computeAll(ResolutionContext ctx, List<ElementRule> rules) {
        List<PlacementAD.Placement> result = new ArrayList<>();
        for (ElementRule rule : rules) {
            result.addAll(computeOne(ctx, rule));
        }
        return result;
    }

    private List<PlacementAD.Placement> computeOne(ResolutionContext ctx, ElementRule rule) {
        double widthM  = nn(rule.widthMm) / 1000.0;
        double heightM = nn(rule.heightExtentMm) / 1000.0;
        double depthM  = nn(rule.depthMm) / 1000.0;
        double minZ    = nn(rule.heightMm) / 1000.0;
        double maxZ    = minZ + heightM;

        double minX, maxX, minY, maxY;

        PositionRule pos = rule.positionMode;

        if (pos instanceof PositionRule.DirectCoordinate dc) {
            double cx = dc.cxMm() / 1000.0;
            double cy = dc.cyMm() / 1000.0;
            minX = cx - widthM / 2.0;
            maxX = cx + widthM / 2.0;
            minY = cy - depthM / 2.0;
            maxY = cy + depthM / 2.0;

        } else if (pos instanceof PositionRule.WallFraction wf) {
            WallSegment wall = ctx.wallOrThrow(wf.wallRef(), rule.elementRef);

            double t = wf.fraction();
            double wx = wall.x1mm + (wall.x2mm - wall.x1mm) * t;
            double wy = wall.y1mm + (wall.y2mm - wall.y1mm) * t;

            double perpMm = wf.perpOffsetMm();
            if ("NORTH".equals(wall.face) || "SOUTH".equals(wall.face)) {
                wy = wall.y1mm + perpMm;
            } else {
                wx = wall.x1mm + perpMm;
            }

            double cx = wx / 1000.0;
            double cy = wy / 1000.0;
            minX = cx - widthM / 2.0;
            maxX = cx + widthM / 2.0;
            minY = cy - depthM / 2.0;
            maxY = cy + depthM / 2.0;

        } else if (pos instanceof PositionRule.RoomFraction rf) {
            // Phase RM-6: BOM anchor detection — before computing position
            if (rule.familyRef != null && ctx.bomIds().contains(rule.familyRef)) {
                return computeBomAnchor(ctx, rule, minZ);
            }

            RoomExtent room = ctx.roomOrThrow(rf.roomRef(), rule.elementRef);

            double rx = rf.fractionX();
            double ry = rf.fractionY();
            double roomW = room.maxXmm - room.minXmm;
            double roomD = room.maxYmm - room.minYmm;
            double cx = (room.minXmm + rx * roomW) / 1000.0;
            double cy = (room.minYmm + ry * roomD) / 1000.0;
            minX = cx - widthM / 2.0;
            maxX = cx + widthM / 2.0;
            minY = cy - depthM / 2.0;
            maxY = cy + depthM / 2.0;

        } else if (pos instanceof PositionRule.BomAnchor ba) {
            // Phase BOM-2c: UNIT anchor → full UNIT→FLOOR→SET dispatch
            if ("BUILDING".equals(ba.hostType())) return computeUnitAnchor(ctx, rule);
            // UNIT-level FLOOR Orderlines are metadata-only; placements come from UNIT dispatch
            return List.of();

        } else {
            throw new AssertionError("Unreachable: " + pos.getClass());
        }

        int placementId = extractPlacementId(rule.elementRef);

        // Phase RM-11 Step 3: derive concrete facing rotation from conn_points
        String orientation = resolveOrientation(rule, ctx, pos);

        return List.of(new PlacementAD.Placement(
            ctx.buildingType(), rule.storey, rule.ifcClass, rule.elementRef,
            placementId,
            minX, maxX, minY, maxY, minZ, maxZ,
            orientation, rule.discipline,
            rule.materialName, rule.materialRgba,
            rule.familyRef
        ));
    }

    /**
     * Phase RM-6: Expand a BOM anchor rule into N child Placement records.
     *
     * <p>The anchor row carries family_ref=bomId and is FRACTION/ROOM at (0.5, 0.5).
     * BOMTierResolver computes actual child positions from room bounds.
     * Product dims are in meters (ad_product_dim verified: FURN_DINING_CHAIR=0.45m).
     */
    private List<PlacementAD.Placement> computeBomAnchor(
            ResolutionContext ctx, ElementRule rule, double floorZ) {
        PositionRule pos = rule.positionMode;
        if (!(pos instanceof PositionRule.RoomFraction rf)) {
            System.err.printf("[RelationalResolver] BOM anchor %s is not FRACTION/ROOM — skipping%n",
                rule.elementRef);
            return List.of();
        }
        RoomExtent room = ctx.rooms().get(rf.roomRef());
        if (room == null) {
            System.err.printf("[RelationalResolver] BOM anchor %s references unknown room %s%n",
                rule.elementRef, rf.roomRef());
            return List.of();
        }

        List<BOMTierResolver.PlacedFurniture> placed = getBomResolver().resolveForRoom(
            room.minXmm() / 1000.0, room.minYmm() / 1000.0,
            room.maxXmm() / 1000.0, room.maxYmm() / 1000.0,
            floorZ, room.name(), room.type(), null, rule.familyRef);

        if (placed.isEmpty()) {
            System.err.printf("[RelationalResolver] BOM %s yielded 0 children for room %s%n",
                rule.familyRef, rf.roomRef());
            return List.of();
        }

        List<PlacementAD.Placement> result = new ArrayList<>();
        int childIdx = 0;
        int baseId = extractPlacementId(rule.elementRef);
        for (BOMTierResolver.PlacedFurniture pf : placed) {
            if (pf.namePattern() == null) { childIdx++; continue; }
            // Dims: prefer product_ref FK (exact match) over namePattern LIKE key (fragile)
            String dimKey = pf.productRef() != null ? pf.productRef() : pf.namePattern();
            double[] dims = ctx.productDims().get(dimKey);
            double w = (dims != null) ? dims[0] : 0.5;
            double d = (dims != null) ? dims[1] : 0.5;
            double h = (dims != null) ? dims[2] : 1.0;
            double elMinX = pf.x() - w / 2.0;
            double elMaxX = pf.x() + w / 2.0;
            double elMinY = pf.y() - d / 2.0;
            double elMaxY = pf.y() + d / 2.0;
            double elMinZ = pf.z();
            double elMaxZ = elMinZ + h;
            String childRef = rule.elementRef + "_" + pf.role() + "_" + childIdx;
            // Phase RM-6: ordinal must be unique across all buildings/rooms/children.
            // childRef is globally unique (includes room name). Use stable positive hash.
            int ordinal = childRef.hashCode() & 0x7FFFFFFF;
            result.add(new PlacementAD.Placement(
                ctx.buildingType(), rule.storey, rule.ifcClass, childRef,
                ordinal, elMinX, elMaxX, elMinY, elMaxY, elMinZ, elMaxZ,
                String.valueOf(pf.rotation()), rule.discipline,
                pf.materialName(), pf.materialRgba(), pf.namePattern()
            ));
            childIdx++;
        }
        System.out.printf("[RelationalResolver] BOM %s in %s → %d children%n",
            rule.familyRef, rf.roomRef(), result.size());
        return result;
    }

    /**
     * Phase BOM-2c: Dispatch a UNIT anchor — walk UNIT→FLOOR→SET chain, cascade-select
     * SET BOMs for each room, and emit child placements for all floors of this unit.
     */
    private List<PlacementAD.Placement> computeUnitAnchor(ResolutionContext ctx, ElementRule rule) {
        String unitBomId = rule.familyRef;
        if (unitBomId == null) return List.of();

        List<PlacementAD.Placement> result = new ArrayList<>();

        // Walk UNIT → FLOOR
        for (BomLink floorLink : ctx.bomChain().getOrDefault(unitBomId, List.of())) {
            String floorBomId = floorLink.childBomId();
            String storeyName = ctx.floorStoreys().getOrDefault(floorBomId, rule.storey);
            // Cascade floor Z: upper floors carry non-zero position_value_3 (mm → m)
            double floorZ = ctx.floorZOffsets().getOrDefault(floorBomId, 0.0);
            // Cascade floor orientation: absolute facing direction in radians (e.g. π for DX L2)
            double floorOrientation = ctx.floorOrientations().getOrDefault(floorBomId, 0.0);

            // Walk FLOOR → SET
            List<BomLink> setLinks = ctx.bomChain().getOrDefault(floorBomId, List.of());

            // For each room on this storey, cascade-select which SET BOMs to dispatch
            for (RoomExtent room : ctx.rooms().values()) {
                if (!storeyName.equals(room.storey())) continue;
                double areaM2 = (room.maxXmm() - room.minXmm())
                              * (room.maxYmm() - room.minYmm()) / 1_000_000.0;

                // Cascade: per slot_name, pick SET BOM with highest min_area that still fits
                Map<String, double[]> winners = new HashMap<>(); // slotName → [minArea]
                Map<String, String>   winnerBom = new HashMap<>();

                for (BomLink setLink : setLinks) {
                    String setBomId = setLink.childBomId();
                    for (RoomSlotEntry slot : ctx.slotsByAssembly()
                                                 .getOrDefault(setBomId, List.of())) {
                        if (!slot.roomType().equals(room.type())) continue;
                        if (areaM2 < slot.minAreaM2()) continue;
                        // Cascade: keep candidate with the highest min_area per slot_name
                        double[] cur = winners.get(slot.slotName());
                        if (cur == null || slot.minAreaM2() > cur[0]) {
                            winners.put(slot.slotName(), new double[]{slot.minAreaM2()});
                            winnerBom.put(slot.slotName(), setBomId);
                        }
                    }
                }

                for (String winBomId : winnerBom.values()) {
                    result.addAll(computeBomAnchorForRoom(ctx, rule, room, winBomId,
                                                          floorZ, storeyName, floorOrientation));
                }
            }
        }

        System.out.printf("[RelationalResolver] UNIT %s → %d total furniture placements%n",
            unitBomId, result.size());
        return result;
    }

    /**
     * Phase BOM-2c: Expand a specific SET BOM for a specific room into child Placement records.
     * Same logic as computeBomAnchor() but takes explicit room + bomId + storeyName.
     *
     * <p>Phase 4b: floorOrientation is the absolute cardinal orientation of the parent floor
     * in radians (e.g. π for DX Level 2 — mirrored duplex storey). All child furniture
     * positions are rotated by this angle around the room centroid so the assembly faces
     * the correct direction. 0.0 = no rotation (ground floor, global north).
     */
    private List<PlacementAD.Placement> computeBomAnchorForRoom(
            ResolutionContext ctx, ElementRule baseRule, RoomExtent room,
            String bomId, double floorZ, String storeyName, double floorOrientation) {

        List<BOMTierResolver.PlacedFurniture> placed = getBomResolver().resolveForRoom(
            room.minXmm() / 1000.0, room.minYmm() / 1000.0,
            room.maxXmm() / 1000.0, room.maxYmm() / 1000.0,
            floorZ, room.name(), room.type(), null, bomId);

        if (placed.isEmpty()) {
            System.err.printf("[RelationalResolver] BOM %s → 0 children for room %s%n",
                bomId, room.name());
            return List.of();
        }

        // Phase 4b: room centroid used as pivot for floor orientation cascade
        double roomCx = (room.minXmm() + room.maxXmm()) / 2.0 / 1000.0;
        double roomCy = (room.minYmm() + room.maxYmm()) / 2.0 / 1000.0;
        double cosTheta = Math.cos(floorOrientation);
        double sinTheta = Math.sin(floorOrientation);

        List<PlacementAD.Placement> result = new ArrayList<>();
        int childIdx = 0;
        for (BOMTierResolver.PlacedFurniture pf : placed) {
            if (pf.namePattern() == null) { childIdx++; continue; }
            // Dims: prefer product_ref FK (exact match) over namePattern LIKE key (fragile)
            String dimKey = pf.productRef() != null ? pf.productRef() : pf.namePattern();
            double[] dims = ctx.productDims().get(dimKey);
            double w = (dims != null) ? dims[0] : 0.5;
            double d = (dims != null) ? dims[1] : 0.5;
            double h = (dims != null) ? dims[2] : 1.0;

            // Phase 4b: apply floor orientation — rotate furniture position around room centroid.
            // orientation = absolute cardinal offset from North (radians). 0.0 = no transform.
            double dx = pf.x() - roomCx;
            double dy = pf.y() - roomCy;
            double px = roomCx + dx * cosTheta - dy * sinTheta;
            double py = roomCy + dx * sinTheta + dy * cosTheta;
            double childRot = pf.rotation() + floorOrientation;

            // Globally unique childRef: BOM id + room name + role + index
            String childRef = "BOM_" + bomId + "_" + room.name() + "_" + pf.role() + "_" + childIdx;
            int ordinal = childRef.hashCode() & 0x7FFFFFFF;
            result.add(new PlacementAD.Placement(
                ctx.buildingType(), storeyName, "IfcFurnishingElement", childRef,
                ordinal, px - w / 2.0, px + w / 2.0,
                         py - d / 2.0, py + d / 2.0,
                         pf.z(), pf.z() + h,
                String.valueOf(childRot), baseRule.discipline,
                pf.materialName(), pf.materialRgba(), pf.namePattern()
            ));
            System.out.printf("[RESOLVE] %s %s %s → type=IfcFurnishingElement bbox=%b component=%s orient=%.3f%n",
                ctx.buildingType(), room.name(), childRef,
                dims == null,  // bbox=true: missing dims → geometry may be degenerate
                pf.namePattern() != null ? pf.namePattern() : "MISSING",
                floorOrientation);
            childIdx++;
        }
        System.out.printf("[RelationalResolver] BOM %s in %s → %d children%n",
            bomId, room.name(), result.size());
        return result;
    }

    private static double nn(Double v) { return v != null ? v : 0.0; }

    private static int extractPlacementId(String elementRef) {
        int idx = elementRef.lastIndexOf('_');
        if (idx >= 0) {
            try { return Integer.parseInt(elementRef.substring(idx + 1)); }
            catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    // ── Singleton ────────────────────────────────────────────────

    private static RelationalResolver instance;
    static synchronized RelationalResolver getInstance() {
        if (instance == null) instance = new RelationalResolver();
        return instance;
    }
}
