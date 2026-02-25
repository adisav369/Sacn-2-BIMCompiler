package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.library.ComponentLibrary.*;
import com.bim.compiler.util.OutlierLogger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 59: Places furniture from LOD400 library based on room type.
 *
 * Room type -> furniture BOM:
 * - LOBBY/WAITING: Waiting_Room_Seat (4-seat bench with table)
 * - CANTEEN/CAFETERIA: Canteen Table (with implied chairs)
 * - OFFICE/WORKSTATION: Desk_with_return + Chair
 *
 * Placement uses grid layout within room bounds.
 */
public class FurniturePlacer {

    private final ComponentLibrary library;
    private BOMTierResolver bomResolver;  // Phase 93: lazy-init cached

    // Clearances for furniture placement
    private static final double WALL_OFFSET = 0.5;       // 500mm from walls
    private static final double AISLE_WIDTH = 1.2;       // 1200mm main aisle
    private static final double CHAIR_CLEARANCE = 0.8;   // 800mm for chair pullback

    public FurniturePlacer(ComponentLibrary library) {
        this.library = library;
    }

    /**
     * Place lobby/waiting room seating (auto-calculate quantity from room size).
     * Uses Waiting_Room_Seat (4-seat bench with table) in rows.
     */
    public List<FurnitureInstance> placeLobbyFurniture(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, String roomName) throws SQLException {
        return placeLobbyFurniture(roomMinX, roomMinY, roomMaxX, roomMaxY, floorZ, roomName, -1);
    }

    /**
     * Place lobby/waiting room seating with BOM-resolved quantity.
     * Uses Waiting_Room_Seat (4-seat bench with table) in rows.
     *
     * @param targetQty BOM-resolved quantity (-1 = auto-calculate from room size)
     */
    public List<FurnitureInstance> placeLobbyFurniture(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, String roomName, int targetQty) throws SQLException {

        List<FurnitureInstance> furniture = new ArrayList<>();

        double roomWidth = roomMaxX - roomMinX;
        double roomDepth = roomMaxY - roomMinY;
        String roomContext = String.format("LOBBY \"%s\" (%.1fm x %.1fm)", roomName, roomWidth, roomDepth);

        // Get waiting room seat: 0.6m x 2.95m x 0.75m
        ComponentDefinition seatDef = library.findByName("Waiting_Room_Seat");
        if (seatDef == null) {
            OutlierLogger.logGeometryImpossible("lobby_seating", roomContext,
                "component not found", "Library missing Waiting_Room_Seat");
            return furniture;
        }

        double seatWidth = seatDef.localBounds().width();   // ~0.6m
        double seatDepth = seatDef.localBounds().depth();   // ~2.95m

        // Calculate how many rows fit (along Y axis)
        double usableWidth = roomWidth - 2 * WALL_OFFSET;
        double usableDepth = roomDepth - 2 * WALL_OFFSET - AISLE_WIDTH;

        if (usableDepth < seatDepth) {
            OutlierLogger.logGeometryImpossible("lobby_seating", roomContext,
                "room too shallow", String.format("Need %.1fm, have %.1fm", seatDepth + AISLE_WIDTH, roomDepth));
            return furniture;
        }

        // Calculate max capacity
        int maxFit = (int) Math.floor(usableWidth / seatDepth);
        int toPlace = (targetQty > 0) ? Math.min(targetQty, maxFit) : Math.min(maxFit, 4);

        if (targetQty > 0 && targetQty > maxFit) {
            OutlierLogger.logGeometryImpossible("lobby_seating", roomContext,
                "BOM exceeds capacity",
                String.format("BOM wants %d seats but only %d fit", targetQty, maxFit));
        }

        // Place seats in rows along back wall
        double startX = roomMinX + WALL_OFFSET + seatDepth / 2;
        double startY = roomMaxY - WALL_OFFSET - seatWidth / 2;  // Against back wall

        for (int i = 0; i < toPlace; i++) {
            double x = startX + i * (seatDepth + 0.3);  // 300mm gap between rows
            if (x + seatDepth / 2 > roomMaxX - WALL_OFFSET) break;

            furniture.add(new FurnitureInstance(
                seatDef,
                new Point3D(x, startY, floorZ),
                Math.PI / 2,  // Rotated to face into room
                FurnitureType.LOBBY_SEATING
            ));
        }

        return furniture;
    }

    /**
     * Place canteen/cafeteria tables (auto-calculate quantity from room size).
     * Uses Canteen Table (1.5m x 1.25m) in grid layout.
     */
    public List<FurnitureInstance> placeCanteenFurniture(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, String roomName) throws SQLException {
        return placeCanteenFurniture(roomMinX, roomMinY, roomMaxX, roomMaxY, floorZ, roomName, -1);
    }

    /**
     * Place canteen/cafeteria tables with BOM-resolved quantity.
     * Uses Canteen Table (1.5m x 1.25m) in grid layout.
     *
     * @param targetQty BOM-resolved quantity (-1 = auto-calculate from room size)
     */
    public List<FurnitureInstance> placeCanteenFurniture(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, String roomName, int targetQty) throws SQLException {

        List<FurnitureInstance> furniture = new ArrayList<>();

        double roomWidth = roomMaxX - roomMinX;
        double roomDepth = roomMaxY - roomMinY;
        String roomContext = String.format("CANTEEN \"%s\" (%.1fm x %.1fm)", roomName, roomWidth, roomDepth);

        // Get canteen table: 1.5m x 1.25m x 0.75m
        ComponentDefinition tableDef = library.findByName("Canteen Table");
        if (tableDef == null) {
            OutlierLogger.logGeometryImpossible("canteen_table", roomContext,
                "component not found", "Library missing Canteen Table");
            return furniture;
        }

        double tableWidth = tableDef.localBounds().width();   // ~1.5m
        double tableDepth = tableDef.localBounds().depth();   // ~1.25m

        // Grid spacing: table + chair clearance on both sides
        double spacingX = tableWidth + 2 * CHAIR_CLEARANCE;
        double spacingY = tableDepth + 2 * CHAIR_CLEARANCE;

        // Calculate grid dimensions
        double usableWidth = roomWidth - 2 * WALL_OFFSET - AISLE_WIDTH;
        double usableDepth = roomDepth - 2 * WALL_OFFSET;

        int tablesX = (int) Math.floor(usableWidth / spacingX);
        int tablesY = (int) Math.floor(usableDepth / spacingY);

        if (tablesX < 1 || tablesY < 1) {
            OutlierLogger.logGeometryImpossible("canteen_table", roomContext,
                "room too small", String.format("Need %.1fm x %.1fm for one table + clearance",
                    spacingX + AISLE_WIDTH, spacingY));
            return furniture;
        }

        // Determine how many tables to place
        int maxFit = tablesX * tablesY;
        int toPlace = (targetQty > 0) ? Math.min(targetQty, maxFit) : Math.min(maxFit, 25);  // Cap at 25

        if (targetQty > 0 && targetQty > maxFit) {
            OutlierLogger.logGeometryImpossible("canteen_table", roomContext,
                "BOM exceeds capacity",
                String.format("BOM wants %d tables but only %d fit (grid %dx%d)", targetQty, maxFit, tablesX, tablesY));
        }

        // Place tables in grid
        double startX = roomMinX + WALL_OFFSET + AISLE_WIDTH + spacingX / 2;
        double startY = roomMinY + WALL_OFFSET + spacingY / 2;

        int placed = 0;
        outerLoop:
        for (int ix = 0; ix < tablesX; ix++) {
            for (int iy = 0; iy < tablesY; iy++) {
                if (placed >= toPlace) break outerLoop;

                double x = startX + ix * spacingX;
                double y = startY + iy * spacingY;

                if (x + tableWidth / 2 > roomMaxX - WALL_OFFSET) continue;
                if (y + tableDepth / 2 > roomMaxY - WALL_OFFSET) continue;

                furniture.add(new FurnitureInstance(
                    tableDef,
                    new Point3D(x, y, floorZ),
                    0,
                    FurnitureType.CANTEEN_TABLE
                ));
                placed++;
            }
        }

        return furniture;
    }

    /**
     * Place office workstation (auto-calculate quantity from room size).
     * Uses Desk_with_return and Chair - Desk components.
     */
    public List<FurnitureInstance> placeOfficeFurniture(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, String roomName) throws SQLException {
        return placeOfficeFurniture(roomMinX, roomMinY, roomMaxX, roomMaxY, floorZ, roomName, -1);
    }

    /**
     * Place office workstations with BOM-resolved quantity.
     * Uses Desk_with_return and Chair - Desk components.
     *
     * @param targetQty BOM-resolved number of workstations (-1 = auto-calculate)
     */
    public List<FurnitureInstance> placeOfficeFurniture(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, String roomName, int targetQty) throws SQLException {

        List<FurnitureInstance> furniture = new ArrayList<>();

        double roomWidth = roomMaxX - roomMinX;
        double roomDepth = roomMaxY - roomMinY;
        String roomContext = String.format("OFFICE \"%s\" (%.1fm x %.1fm)", roomName, roomWidth, roomDepth);

        // Get desk and chair
        ComponentDefinition deskDef = library.findByName("Desk_with_return");
        ComponentDefinition chairDef = library.findByName("Chair - Desk");

        if (deskDef == null) {
            OutlierLogger.logGeometryImpossible("office_desk", roomContext,
                "component not found", "Library missing Desk_with_return");
            return furniture;
        }

        double deskWidth = deskDef.localBounds().width();   // ~2.66m
        double deskDepth = deskDef.localBounds().depth();   // ~1.97m
        double workstationSpacing = deskWidth + 1.0;  // 1m between workstations

        // Check if desk fits
        if (deskWidth > roomWidth - 2 * WALL_OFFSET || deskDepth > roomDepth - WALL_OFFSET - CHAIR_CLEARANCE) {
            OutlierLogger.logGeometryImpossible("office_desk", roomContext,
                "desk too large", String.format("Desk %.1fm x %.1fm doesn't fit room with clearance",
                    deskWidth, deskDepth));
            return furniture;
        }

        // Calculate how many workstations fit
        double usableWidth = roomWidth - 2 * WALL_OFFSET;
        int maxFit = Math.max(1, (int) Math.floor(usableWidth / workstationSpacing));
        int toPlace = (targetQty > 0) ? Math.min(targetQty, maxFit) : 1;  // Default to 1 workstation

        if (targetQty > 0 && targetQty > maxFit) {
            OutlierLogger.logGeometryImpossible("office_desk", roomContext,
                "BOM exceeds capacity",
                String.format("BOM wants %d workstations but only %d fit", targetQty, maxFit));
        }

        // Place workstations
        double startX = roomMinX + WALL_OFFSET + deskWidth / 2;
        double deskY = roomMaxY - WALL_OFFSET - deskDepth / 2;

        for (int i = 0; i < toPlace; i++) {
            double deskX = startX + i * workstationSpacing;
            if (deskX + deskWidth / 2 > roomMaxX - WALL_OFFSET) break;

            furniture.add(new FurnitureInstance(
                deskDef,
                new Point3D(deskX, deskY, floorZ),
                0,
                FurnitureType.WORKSTATION_DESK
            ));

            // Place chair in front of desk
            if (chairDef != null) {
                double chairY = deskY - deskDepth / 2 - 0.3;  // 300mm in front of desk

                furniture.add(new FurnitureInstance(
                    chairDef,
                    new Point3D(deskX, chairY, floorZ),
                    Math.PI,  // Facing desk
                    FurnitureType.WORKSTATION_CHAIR
                ));
            }
        }

        return furniture;
    }

    /**
     * Phase 90: Place corridor bench seating along one wall.
     * Uses Waiting_Room_Seat placed against the longer wall.
     */
    public List<FurnitureInstance> placeCorridorFurniture(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, String roomName) throws SQLException {

        List<FurnitureInstance> furniture = new ArrayList<>();
        double roomWidth = roomMaxX - roomMinX;
        double roomDepth = roomMaxY - roomMinY;

        // Only place if corridor is wide enough (>= 2.4m)
        double minDim = Math.min(roomWidth, roomDepth);
        if (minDim < 2.4) return furniture;

        ComponentDefinition seatDef = library.findByName("Waiting_Room_Seat");
        if (seatDef == null) return furniture;

        double seatWidth = seatDef.localBounds().width();
        double seatDepth = seatDef.localBounds().depth();

        // Place 1 bench against the longer wall midpoint
        double cx = (roomMinX + roomMaxX) / 2;
        double cy;
        double rotation;
        if (roomWidth >= roomDepth) {
            // Long corridor along X — bench against north wall
            cy = roomMaxY - WALL_OFFSET - seatWidth / 2;
            rotation = Math.PI / 2;
        } else {
            // Long corridor along Y — bench against east wall
            cy = (roomMinY + roomMaxY) / 2;
            cx = roomMaxX - WALL_OFFSET - seatWidth / 2;
            rotation = 0;
        }

        furniture.add(new FurnitureInstance(
            seatDef, new Point3D(cx, cy, floorZ), rotation, FurnitureType.CORRIDOR_BENCH
        ));
        return furniture;
    }

    /**
     * Phase 90: Place management office furniture (desk + chair).
     * Delegates to placeOfficeFurniture with default qty=1.
     */
    public List<FurnitureInstance> placeManagementFurniture(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, String roomName) throws SQLException {
        return placeOfficeFurniture(roomMinX, roomMinY, roomMaxX, roomMaxY, floorZ, roomName, 1);
    }

    /**
     * Phase 90: Generic furniture fallback for rooms > 6m² with no specific type.
     * Places a single Waiting_Room_Seat centered in the room.
     */
    public List<FurnitureInstance> placeGenericFurniture(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, String roomName) throws SQLException {

        List<FurnitureInstance> furniture = new ArrayList<>();
        double roomWidth = roomMaxX - roomMinX;
        double roomDepth = roomMaxY - roomMinY;
        double area = roomWidth * roomDepth;

        if (area < 6.0) return furniture;

        ComponentDefinition seatDef = library.findByName("Waiting_Room_Seat");
        if (seatDef == null) return furniture;

        double cx = (roomMinX + roomMaxX) / 2;
        double cy = (roomMinY + roomMaxY) / 2;

        furniture.add(new FurnitureInstance(
            seatDef, new Point3D(cx, cy, floorZ), 0, FurnitureType.GENERIC_SEATING
        ));
        return furniture;
    }

    /**
     * Phase 93: BOM-driven furniture placement with opening avoidance.
     * Delegates to BOMTierResolver for wall scoring and recursive BOM expansion.
     * Falls back to hardcoded method if BOM resolver returns empty.
     */
    public List<FurnitureInstance> placeUniversalFurniture(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, String roomName, String roomType,
            List<BOMTierResolver.OpeningInfo> openings) throws SQLException {
        return placeUniversalFurniture(roomMinX, roomMinY, roomMaxX, roomMaxY,
            floorZ, roomName, roomType, openings, "ROOM_FURNITURE");
    }

    /**
     * Phase 109: BOM-driven furniture placement with specific BOM ID.
     * Defaults ceilingZ to floorZ + 3.0m.
     */
    public List<FurnitureInstance> placeUniversalFurniture(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, String roomName, String roomType,
            List<BOMTierResolver.OpeningInfo> openings,
            String bomId) throws SQLException {
        return placeUniversalFurniture(roomMinX, roomMinY, roomMaxX, roomMaxY,
            floorZ, roomName, roomType, openings, bomId, floorZ + 3.0);
    }

    /**
     * Phase G-1: BOM-driven furniture placement with ceilingZ for fixture support.
     */
    public List<FurnitureInstance> placeUniversalFurniture(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, String roomName, String roomType,
            List<BOMTierResolver.OpeningInfo> openings,
            String bomId, double ceilingZ) throws SQLException {

        if (bomResolver == null) bomResolver = new BOMTierResolver();
        List<BOMTierResolver.PlacedFurniture> bomResult = bomResolver.resolveForRoom(
            roomMinX, roomMinY, roomMaxX, roomMaxY, floorZ, roomName, roomType, openings, bomId,
            ceilingZ);
        if (!bomResult.isEmpty()) {
            List<FurnitureInstance> furniture = new ArrayList<>();
            for (var pf : bomResult) {
                FurnitureInstance fi = toFurnitureInstance(pf);
                if (fi != null) furniture.add(fi);
            }
            if (!furniture.isEmpty()) return furniture;
        }

        // Fallback to existing hardcoded method (only for ROOM_FURNITURE)
        if ("ROOM_FURNITURE".equals(bomId)) {
            return placeUniversalFurniture(roomMinX, roomMinY, roomMaxX, roomMaxY,
                floorZ, roomName, roomType);
        }
        return List.of();
    }

    /**
     * Convert BOM PlacedFurniture to FurnitureInstance by loading component from library.
     */
    private FurnitureInstance toFurnitureInstance(BOMTierResolver.PlacedFurniture pf) throws SQLException {
        if (pf.namePattern() == null) return null;

        // Use getByName directly — namePattern already has SQL LIKE wildcards
        ComponentDefinition def = library.getByName(pf.namePattern());
        if (def == null) return null;

        FurnitureType type = switch (pf.role()) {
            case "DESK" -> FurnitureType.WORKSTATION_DESK;
            case "USER_CHAIR" -> FurnitureType.WORKSTATION_CHAIR;
            case "MONITOR" -> FurnitureType.WORKSTATION_MONITOR;
            case "TABLE" -> FurnitureType.DINING_TABLE;
            case "CHAIR_A", "CHAIR_B", "CHAIR_C", "CHAIR_D", "CHAIR_E", "CHAIR_F",
                 "VISITOR_CHAIR_A", "VISITOR_CHAIR_B" -> FurnitureType.DINING_CHAIR;
            case "GUEST_SEAT" -> FurnitureType.GUEST_SEAT;
            // Phase 109: Residential roles
            case "BED" -> FurnitureType.BED;
            case "SIDE_TABLE", "SIDE_TABLE_L", "SIDE_TABLE_R",
                 "SIDE_TABLE_A", "SIDE_TABLE_B" -> FurnitureType.SIDE_TABLE;
            case "WARDROBE" -> FurnitureType.WARDROBE;
            case "SOFA", "SOFA_B" -> FurnitureType.SOFA;
            case "COFFEE_TABLE" -> FurnitureType.COFFEE_TABLE;
            case "TV" -> FurnitureType.TV;
            case "LOUNGE_CHAIR" -> FurnitureType.LOUNGE_CHAIR;
            case "PIANO" -> FurnitureType.GENERIC_SEATING;
            // Phase 122C: Kitchen and bathroom roles
            case "BASE_CABINET", "BASE_CABINET_2", "BASE_CABINET_3", "BASE_CABINET_4",
                 "BASE_CABINET_5", "BASE_CABINET_6", "BASE_CABINET_7",
                 "UPPER_CABINET", "UPPER_CABINET_2", "UPPER_CABINET_3",
                 "VANITY_A", "VANITY_B" -> FurnitureType.CABINET;
            case "COUNTER" -> FurnitureType.COUNTER_TOP;
            case "SINK" -> FurnitureType.GENERIC_SEATING;
            default -> FurnitureType.GENERIC_SEATING;
        };

        return new FurnitureInstance(
            def,
            new Point3D(pf.x(), pf.y(), pf.z()),
            pf.rotation(),
            type
        );
    }

    /**
     * Phase 91: Universal workstation furniture for all habitable rooms (legacy fallback).
     *
     * Big rooms (> 20m²): 2 desk+chair+monitor sets facing center, 3 visitor benches facing center
     * Small rooms (6-20m²): 1 desk+chair+monitor set facing center
     * Narrow rooms (desk won't fit): visitor bench only
     */
    public List<FurnitureInstance> placeUniversalFurniture(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, String roomName, String roomType) throws SQLException {

        List<FurnitureInstance> furniture = new ArrayList<>();
        double roomWidth = roomMaxX - roomMinX;
        double roomDepth = roomMaxY - roomMinY;
        double area = roomWidth * roomDepth;

        if (area < 6.0) return furniture;

        // Get components
        ComponentDefinition deskDef = library.findByName("Desk_with_return");
        ComponentDefinition chairDef = library.findByName("Chair - Desk");
        ComponentDefinition monitorDef = library.findByName("Apple_iMac_27");
        ComponentDefinition seatDef = library.findByName("Waiting_Room_Seat");

        // Phase 92C: Zone-based unified BOM bbox sets
        // Big rooms (≥80m², w≥3, d≥3) get 2 zones; small rooms get 1
        int setCount = (area >= 80.0 && roomWidth >= 3.0 && roomDepth >= 3.0) ? 2 : 1;

        for (int setIdx = 0; setIdx < setCount; setIdx++) {
            // Compute zone bounds
            double zoneMinX, zoneMaxX, zoneMinY, zoneMaxY;
            if (setCount == 1) {
                zoneMinX = roomMinX; zoneMaxX = roomMaxX;
                zoneMinY = roomMinY; zoneMaxY = roomMaxY;
            } else if (roomDepth >= roomWidth) {
                // Split along Y (longer axis)
                double mid = (roomMinY + roomMaxY) / 2;
                zoneMinX = roomMinX; zoneMaxX = roomMaxX;
                zoneMinY = (setIdx == 0) ? roomMinY : mid;
                zoneMaxY = (setIdx == 0) ? mid : roomMaxY;
            } else {
                // Split along X (longer axis)
                double mid = (roomMinX + roomMaxX) / 2;
                zoneMinX = (setIdx == 0) ? roomMinX : mid;
                zoneMaxX = (setIdx == 0) ? mid : roomMaxX;
                zoneMinY = roomMinY; zoneMaxY = roomMaxY;
            }
            double zoneW = zoneMaxX - zoneMinX;
            double zoneD = zoneMaxY - zoneMinY;

            // Zone 0: workstation at NW corner (desk faces south)
            // Zone 1: mirrored — workstation at SE corner (desk faces north, rotation=PI)
            boolean mirrored = (setIdx == 1);

            // --- Workstation (desk + chair + iMac) ---
            if (deskDef != null) {
                double deskW = deskDef.localBounds().width();   // ~2.66m
                double deskD = deskDef.localBounds().depth();   // ~1.97m
                boolean deskFits = deskW <= zoneW - 2 * WALL_OFFSET
                        && deskD <= zoneD - WALL_OFFSET - CHAIR_CLEARANCE;

                if (deskFits) {
                    double deskHeight = deskDef.localBounds().height(); // ~0.70m
                    double deskX, deskY, chairX, chairY, rotation;

                    if (!mirrored) {
                        // NW corner: desk against north wall, left side
                        deskX = zoneMinX + WALL_OFFSET + deskW / 2;
                        deskY = zoneMaxY - WALL_OFFSET - deskD / 2;
                        chairX = deskX;
                        chairY = deskY - deskD / 2 - 0.3;
                        rotation = 0;
                    } else {
                        // SE corner (mirrored): desk against south wall, right side
                        deskX = zoneMaxX - WALL_OFFSET - deskW / 2;
                        deskY = zoneMinY + WALL_OFFSET + deskD / 2;
                        chairX = deskX;
                        chairY = deskY + deskD / 2 + 0.3;
                        rotation = Math.PI;
                    }

                    furniture.add(new FurnitureInstance(
                        deskDef, new Point3D(deskX, deskY, floorZ), rotation, FurnitureType.WORKSTATION_DESK
                    ));

                    // Chair facing desk
                    if (chairDef != null) {
                        double chairRotation = (rotation == 0) ? Math.PI : 0;
                        furniture.add(new FurnitureInstance(
                            chairDef, new Point3D(chairX, chairY, floorZ), chairRotation, FurnitureType.WORKSTATION_CHAIR
                        ));
                    }

                    // iMac on desk surface, offset -0.59m lateral from chair position
                    // Pass raw desk-top Z — BuildingWriter.writeFixture() handles ON_FLOOR correction
                    if (monitorDef != null) {
                        double monitorZ = floorZ + deskHeight;
                        // Lateral offset: toward the L-corner of the desk
                        double monitorX = deskX + (mirrored ? 0.59 : -0.59);
                        furniture.add(new FurnitureInstance(
                            monitorDef, new Point3D(monitorX, deskY, monitorZ), rotation, FurnitureType.WORKSTATION_MONITOR
                        ));
                    }
                }
            }

            // --- Visitor seating: centered in zone, 2m from desk, facing desk ---
            if (seatDef != null) {
                double seatWidth = seatDef.localBounds().width();
                double seatDepth = seatDef.localBounds().depth();
                double benchLong = Math.max(seatWidth, seatDepth);   // ~2.96m
                double benchShort = Math.min(seatWidth, seatDepth);  // ~0.60m

                if (benchShort <= zoneW - 2 * WALL_OFFSET
                        && benchLong <= zoneD - 2 * WALL_OFFSET) {
                    double zoneCX = (zoneMinX + zoneMaxX) / 2;
                    double benchY;
                    double seatRotation;

                    if (!mirrored) {
                        // Bench in southern half of zone, facing north (toward desk)
                        benchY = zoneMinY + WALL_OFFSET + benchLong / 2 + 0.5;
                        seatRotation = (seatWidth > seatDepth) ? -Math.PI / 2 : 0;
                    } else {
                        // Bench in northern half of zone, facing south (toward desk)
                        benchY = zoneMaxY - WALL_OFFSET - benchLong / 2 - 0.5;
                        seatRotation = (seatWidth > seatDepth) ? Math.PI / 2 : Math.PI;
                    }

                    furniture.add(new FurnitureInstance(
                        seatDef, new Point3D(zoneCX, benchY, floorZ), seatRotation, FurnitureType.LOBBY_SEATING
                    ));
                }
            }
        }

        return furniture;
    }

    // =========================================================================
    // Output types
    // =========================================================================

    public enum FurnitureType {
        LOBBY_SEATING,
        CANTEEN_TABLE,
        WORKSTATION_DESK,
        WORKSTATION_CHAIR,
        WORKSTATION_MONITOR,
        CORRIDOR_BENCH,
        GENERIC_SEATING,
        VISITOR_CHAIR,
        VISITOR_TABLE,
        GUEST_SEAT,
        // Phase 109: Residential furniture types
        BED,
        SIDE_TABLE,
        WARDROBE,
        SOFA,
        COFFEE_TABLE,
        TV,
        LOUNGE_CHAIR,
        DINING_TABLE,
        DINING_CHAIR,
        PIANO,
        CABINET,
        COUNTER_TOP,
        OTTOMAN
    }

    public record FurnitureInstance(
        ComponentDefinition definition,
        Point3D worldPosition,
        double rotation,
        FurnitureType type
    ) {
        public String name() {
            return definition.name();
        }

        public String geometryHash() {
            return definition.geometryHash();
        }

        public BoundingBox localBounds() {
            return definition.localBounds();
        }
    }
}
