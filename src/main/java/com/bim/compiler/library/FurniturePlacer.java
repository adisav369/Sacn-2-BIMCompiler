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

    // =========================================================================
    // Output types
    // =========================================================================

    public enum FurnitureType {
        LOBBY_SEATING,
        CANTEEN_TABLE,
        WORKSTATION_DESK,
        WORKSTATION_CHAIR
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
