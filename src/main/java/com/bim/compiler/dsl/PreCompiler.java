package com.bim.compiler.dsl;

import java.util.*;

/**
 * PreCompiler - Expands manifest to full DSL.
 *
 * Two-phase process:
 * 1. MANIFEST: User declares high-level intent
 * 2. PRECOMPILE: System fills BOM structure from AD
 * 3. SUB-DSL: Generated detailed DSL (user can amend)
 * 4. COMPILE: Normal compilation proceeds
 *
 * Example:
 * <pre>
 * MANIFEST:
 *   BUILDING template:"LANDED_2S" {
 *     rooms: [living, kitchen, 2×bedroom, bathroom]
 *   }
 *
 * PRECOMPILED (generated sub-DSL):
 *   BUILDING "house" template:"LANDED_2S" {
 *     STOREY "Ground" {
 *       ROOM "living" type:LIVING bounds:A1-C3 { ... auto-fitted products }
 *       ROOM "kitchen" type:KITCHEN bounds:C1-D2 { ... }
 *       ROOM "bathroom" type:BATHROOM bounds:D1-D2 { ... }
 *     }
 *     STOREY "Upper" {
 *       ROOM "master" type:MASTER_BEDROOM bounds:A1-B2 { ... }
 *       ROOM "bedroom2" type:BEDROOM bounds:C1-D2 { ... }
 *     }
 *   }
 * </pre>
 */
public class PreCompiler {

    // =========================================================================
    // Public Records
    // =========================================================================

    /**
     * Manifest - minimal user intent.
     */
    public record Manifest(
        String buildingName,
        String templateId,
        Integer floors,          // Override default floors
        List<RoomRequest> rooms, // Requested rooms
        Map<String, Object> options
    ) {}

    /**
     * Room request from manifest.
     */
    public record RoomRequest(
        String name,        // User-chosen name or null for auto
        String type,        // Room type (BEDROOM, BATHROOM, etc.)
        int quantity,       // How many (e.g., 2×bedroom)
        String preferredStorey,  // Ground, Upper, or null for auto
        Map<String, Object> options
    ) {}

    /**
     * Generated sub-DSL structure.
     */
    public record GeneratedDSL(
        String buildingName,
        String templateId,
        List<GeneratedStorey> storeys,
        List<String> notes
    ) {}

    /**
     * Generated storey.
     */
    public record GeneratedStorey(
        String name,
        String floorType,
        double baseZ,
        double height,
        List<GeneratedRoom> rooms
    ) {}

    /**
     * Generated room with auto-fitted products.
     */
    public record GeneratedRoom(
        String name,
        String type,
        double x, double y,         // Position
        double width, double depth,
        List<AutoFitter.ProductPlacement> products
    ) {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Precompile manifest to full DSL structure.
     */
    public static GeneratedDSL precompile(Manifest manifest) {
        List<GeneratedStorey> storeys = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        // 1. Expand building template to get storeys
        List<BuildingBOM.FloorInstance> floors = BuildingBOM.expandBOM(
            manifest.templateId(),
            manifest.floors(),
            null
        );

        if (floors.isEmpty()) {
            notes.add("Could not expand template: " + manifest.templateId());
            return new GeneratedDSL(manifest.buildingName(), manifest.templateId(), storeys, notes);
        }

        // 2. Separate rooms by storey preference
        List<RoomRequest> groundRooms = new ArrayList<>();
        List<RoomRequest> upperRooms = new ArrayList<>();

        for (RoomRequest req : manifest.rooms()) {
            for (int i = 0; i < req.quantity(); i++) {
                RoomRequest single = new RoomRequest(
                    req.name() != null ? (req.quantity() > 1 ? req.name() + "_" + (i+1) : req.name()) : null,
                    req.type(),
                    1,
                    req.preferredStorey(),
                    req.options()
                );

                if ("Upper".equalsIgnoreCase(req.preferredStorey())) {
                    upperRooms.add(single);
                } else if ("Ground".equalsIgnoreCase(req.preferredStorey())) {
                    groundRooms.add(single);
                } else {
                    // Auto-assign: bedrooms upstairs, living/kitchen/bathroom ground
                    String type = req.type().toUpperCase();
                    if (type.contains("BEDROOM") || type.contains("MASTER")) {
                        upperRooms.add(single);
                    } else {
                        groundRooms.add(single);
                    }
                }
            }
        }

        // 3. Generate storeys with rooms
        for (BuildingBOM.FloorInstance floor : floors) {
            List<GeneratedRoom> roomsForStorey = new ArrayList<>();

            List<RoomRequest> roomsToPlace = floor.levelName().equalsIgnoreCase("Ground")
                ? groundRooms : upperRooms;

            // Simple layout: place rooms in a row
            double currentX = 0;
            for (RoomRequest req : roomsToPlace) {
                AutoFitter.SpaceDim spaceDim = AutoFitter.getSpaceDim(req.type());

                double roomWidth = spaceDim != null ? Math.max(spaceDim.minWidth(), 3.0) : 3.0;
                double roomDepth = spaceDim != null ? Math.max(spaceDim.minDepth(), 3.0) : 3.0;

                // Auto-fit products
                AutoFitter.FittedSpace fitted = AutoFitter.fitSpace(
                    req.type(), roomWidth, roomDepth, floor.floorHeight()
                );

                String roomName = req.name() != null ? req.name() : req.type().toLowerCase() + "_" + (roomsForStorey.size() + 1);

                roomsForStorey.add(new GeneratedRoom(
                    roomName,
                    req.type(),
                    currentX, 0,
                    roomWidth, roomDepth,
                    fitted.placements()
                ));

                currentX += roomWidth;

                if (!fitted.warnings().isEmpty()) {
                    notes.addAll(fitted.warnings().stream()
                        .map(w -> roomName + ": " + w)
                        .toList());
                }
            }

            storeys.add(new GeneratedStorey(
                floor.levelName(),
                floor.floorTypeId(),
                floor.baseZ(),
                floor.floorHeight(),
                roomsForStorey
            ));
        }

        return new GeneratedDSL(manifest.buildingName(), manifest.templateId(), storeys, notes);
    }

    /**
     * Generate DSL text from precompiled structure.
     */
    public static String toDSL(GeneratedDSL gen) {
        StringBuilder sb = new StringBuilder();

        sb.append("// Auto-generated from template: ").append(gen.templateId()).append("\n");
        sb.append("// Amend as needed, then compile\n\n");

        sb.append("BUILDING \"").append(gen.buildingName()).append("\" {\n");

        for (GeneratedStorey storey : gen.storeys()) {
            if (storey.rooms().isEmpty()) continue;

            sb.append("\n  STOREY \"").append(storey.name()).append("\" {\n");
            sb.append("    // Z=").append(String.format("%.1f", storey.baseZ()));
            sb.append("m, H=").append(String.format("%.1f", storey.height())).append("m\n");

            for (GeneratedRoom room : storey.rooms()) {
                sb.append("\n    ROOM \"").append(room.name()).append("\" type:").append(room.type());
                sb.append(String.format(" bounds:(%.1f,%.1f)-(%.1f,%.1f)",
                    room.x(), room.y(),
                    room.x() + room.width(), room.y() + room.depth()));
                sb.append(" {\n");

                // Group products by type
                Map<String, List<AutoFitter.ProductPlacement>> byType = new LinkedHashMap<>();
                for (AutoFitter.ProductPlacement p : room.products()) {
                    byType.computeIfAbsent(p.productType(), k -> new ArrayList<>()).add(p);
                }

                for (var entry : byType.entrySet()) {
                    for (AutoFitter.ProductPlacement p : entry.getValue()) {
                        sb.append(String.format("      %s \"%s\" at:(%.2f,%.2f,%.2f) on:%s\n",
                            p.productType(), p.productId(),
                            p.x(), p.y(), p.z(), p.hostFace()));
                    }
                }

                sb.append("    }\n");
            }

            sb.append("  }\n");
        }

        sb.append("}\n");

        if (!gen.notes().isEmpty()) {
            sb.append("\n// Notes:\n");
            for (String note : gen.notes()) {
                sb.append("// - ").append(note).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Quick helper to create room requests.
     */
    public static RoomRequest room(String type) {
        return new RoomRequest(null, type, 1, null, Map.of());
    }

    public static RoomRequest room(String type, int qty) {
        return new RoomRequest(null, type, qty, null, Map.of());
    }

    public static RoomRequest room(String name, String type) {
        return new RoomRequest(name, type, 1, null, Map.of());
    }

    public static RoomRequest room(String name, String type, String storey) {
        return new RoomRequest(name, type, 1, storey, Map.of());
    }

    // =========================================================================
    // Test
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=== PreCompiler Test ===\n");

        // Create manifest for 2-storey house
        Manifest manifest = new Manifest(
            "MyHouse",
            "LANDED_2S",
            null,  // Use default floors
            List.of(
                room("living", "LIVING"),
                room("kitchen", "KITCHEN"),
                room("BATHROOM"),
                room("BEDROOM", 2),  // 2× bedroom
                room("master", "MASTER_BEDROOM", "Upper")
            ),
            Map.of()
        );

        System.out.println("--- MANIFEST ---");
        System.out.println("Building: " + manifest.buildingName());
        System.out.println("Template: " + manifest.templateId());
        System.out.println("Rooms requested: " + manifest.rooms().size());

        // Precompile
        GeneratedDSL gen = precompile(manifest);

        System.out.println("\n--- PRECOMPILED STRUCTURE ---");
        System.out.println("Storeys: " + gen.storeys().size());
        for (GeneratedStorey s : gen.storeys()) {
            System.out.printf("  %s (Z=%.1f): %d rooms%n",
                s.name(), s.baseZ(), s.rooms().size());
            for (GeneratedRoom r : s.rooms()) {
                System.out.printf("    - %s (%s): %.1fx%.1fm, %d products%n",
                    r.name(), r.type(), r.width(), r.depth(), r.products().size());
            }
        }

        // Generate DSL
        System.out.println("\n--- GENERATED DSL ---");
        System.out.println(toDSL(gen));

        BuildingBOM.close();
        AutoFitter.close();
    }
}
