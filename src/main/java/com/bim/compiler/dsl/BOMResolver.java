package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BOMRuleAD.*;
import java.sql.SQLException;
import java.util.*;

/**
 * Phase 60: BOM Resolution Stage in the DAG Compiler.
 *
 * Position in pipeline:
 *   Parse → Resolve → [BOM Resolve] → Compile → Place → Write
 *
 * Takes room definitions and resolves variable BOM quantities using AD rules.
 * The resolved BOM becomes input to the placement stage.
 *
 * Output is a RoomBOM per room, containing:
 * - Resolved element quantities (sprinklers, lights, diffusers, furniture)
 * - Calculation audit trail for witness
 * - Code references for compliance
 */
public class BOMResolver {

    private final BOMRuleAD bomRules;

    /**
     * Resolved BOM for a single room.
     */
    public record RoomBOM(
        String roomName,
        String spaceType,
        double areaM2,
        int occupancy,
        List<ResolvedBOMItem> items,
        Map<String, Integer> quantityByElement  // Quick lookup: SPRINKLER -> 4
    ) {
        /**
         * Get quantity for an element type.
         */
        public int getQuantity(String elementType) {
            return quantityByElement.getOrDefault(elementType.toUpperCase(), 0);
        }

        /**
         * Get quantity for element type and subtype.
         */
        public int getQuantity(String elementType, String subtype) {
            return items.stream()
                .filter(i -> i.rule().elementType().equalsIgnoreCase(elementType))
                .filter(i -> subtype == null || i.rule().elementSubtype().equalsIgnoreCase(subtype))
                .mapToInt(ResolvedBOMItem::quantity)
                .sum();
        }

        /**
         * Get items for an element type.
         */
        public List<ResolvedBOMItem> getItems(String elementType) {
            return items.stream()
                .filter(i -> i.rule().elementType().equalsIgnoreCase(elementType))
                .toList();
        }

        /**
         * Get component name for an element type (first match).
         */
        public String getComponentName(String elementType) {
            return items.stream()
                .filter(i -> i.rule().elementType().equalsIgnoreCase(elementType))
                .map(i -> i.rule().componentName())
                .findFirst()
                .orElse(null);
        }

        /**
         * Format as audit log for witness.
         */
        public String toAuditLog() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("ROOM \"%s\" [%s] %.1fm²\n", roomName, spaceType, areaM2));

            for (ResolvedBOMItem item : items) {
                BOMRule r = item.rule();
                String code = r.codeId() != null ? " (" + r.codeId() + " " + r.clause() + ")" : "";
                sb.append(String.format("  %3d× %-25s %s%s\n",
                    item.quantity(),
                    r.componentName(),
                    item.calculation(),
                    code));
            }

            return sb.toString();
        }
    }

    /**
     * Complete resolved BOM for a building.
     */
    public record BuildingBOM(
        String buildingName,
        List<RoomBOM> roomBOMs,
        Map<String, Integer> totalsByComponent  // Canteen Table -> 14
    ) {
        /**
         * Get total quantity for a component across all rooms.
         */
        public int getTotal(String componentName) {
            return totalsByComponent.getOrDefault(componentName, 0);
        }

        /**
         * Format as summary table.
         */
        public String toSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== BUILDING BOM SUMMARY: ").append(buildingName).append(" ===\n\n");

            // Sort by quantity descending
            List<Map.Entry<String, Integer>> sorted = totalsByComponent.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .toList();

            sb.append(String.format("%-30s %8s\n", "Component", "Quantity"));
            sb.append("-".repeat(40)).append("\n");

            for (var entry : sorted) {
                sb.append(String.format("%-30s %8d\n", entry.getKey(), entry.getValue()));
            }

            return sb.toString();
        }
    }

    public BOMResolver() throws SQLException {
        this.bomRules = new BOMRuleAD();
    }

    public BOMResolver(String dbPath) throws SQLException {
        this.bomRules = new BOMRuleAD(dbPath);
    }

    /**
     * Resolve BOM for a single room.
     *
     * @param roomName Room name
     * @param spaceType Room type (CANTEEN, OFFICE, etc.)
     * @param areaM2 Room area in square meters
     * @param occupancy Specified occupancy (0 = derive from area)
     * @return Resolved BOM for the room
     */
    public RoomBOM resolveRoom(String roomName, String spaceType, double areaM2, int occupancy) {
        List<ResolvedBOMItem> items = bomRules.resolveRoom(spaceType, roomName, areaM2, occupancy);

        // Build quantity lookup by element type
        Map<String, Integer> quantityByElement = new HashMap<>();
        for (ResolvedBOMItem item : items) {
            String key = item.rule().elementType().toUpperCase();
            quantityByElement.merge(key, item.quantity(), Integer::sum);
        }

        return new RoomBOM(roomName, spaceType, areaM2, occupancy, items, quantityByElement);
    }

    /**
     * Resolve BOM for all rooms in a building.
     *
     * @param buildingName Building name
     * @param rooms List of (roomName, spaceType, areaM2, occupancy)
     * @return Complete building BOM
     */
    public BuildingBOM resolveBuilding(String buildingName, List<RoomInput> rooms) {
        List<RoomBOM> roomBOMs = new ArrayList<>();
        Map<String, Integer> totalsByComponent = new LinkedHashMap<>();

        for (RoomInput room : rooms) {
            RoomBOM bom = resolveRoom(room.name, room.spaceType, room.areaM2, room.occupancy);
            roomBOMs.add(bom);

            // Aggregate totals by component
            for (ResolvedBOMItem item : bom.items()) {
                String component = item.rule().componentName();
                totalsByComponent.merge(component, item.quantity(), Integer::sum);
            }
        }

        return new BuildingBOM(buildingName, roomBOMs, totalsByComponent);
    }

    /**
     * Input record for room BOM resolution.
     */
    public record RoomInput(
        String name,
        String spaceType,
        double areaM2,
        int occupancy
    ) {}

    public void close() throws SQLException {
        bomRules.close();
    }

    /**
     * Test BOM resolution for school building.
     */
    public static void main(String[] args) throws SQLException {
        BOMResolver resolver = new BOMResolver();

        // Simulate school rooms
        List<RoomInput> rooms = List.of(
            new RoomInput("kantin", "CANTEEN", 84.0, 0),
            new RoomInput("class_1", "CLASSROOM", 56.0, 30),
            new RoomInput("class_2", "CLASSROOM", 56.0, 30),
            new RoomInput("class_3", "CLASSROOM", 56.0, 30),
            new RoomInput("bilik_guru", "OFFICE", 36.0, 8),
            new RoomInput("toilet_1", "BATHROOM", 6.0, 0),
            new RoomInput("toilet_2", "BATHROOM", 6.0, 0),
            new RoomInput("koridor", "CORRIDOR", 120.0, 0)
        );

        BuildingBOM bom = resolver.resolveBuilding("Sekolah Kebangsaan", rooms);

        // Print per-room details
        System.out.println("=== ROOM-LEVEL BOM ===\n");
        for (RoomBOM room : bom.roomBOMs()) {
            System.out.print(room.toAuditLog());
            System.out.println();
        }

        // Print summary
        System.out.println(bom.toSummary());

        resolver.close();
    }
}
