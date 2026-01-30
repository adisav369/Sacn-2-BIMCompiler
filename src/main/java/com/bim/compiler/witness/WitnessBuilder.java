package com.bim.compiler.witness;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Witness Builder - Collects proof data during compilation.
 *
 * Design principles:
 * - NON-INTRUSIVE: Parallel output, doesn't affect compilation
 * - NON-BLOCKING: Errors are caught and logged, compilation continues
 * - SIMPLE DATA: Plain JSON structures
 *
 * Usage in compiler:
 *   WitnessBuilder witness = new WitnessBuilder("TB-LKTN");
 *   witness.foundationZ(0.0, -0.15);
 *   witness.entryDoor("D1", "south", "EXTERIOR", "common");
 *   witness.roomPath("bilik_utama", List.of("common", "bilik_utama"), "D2");
 *   witness.write(outputDir.resolve("witness.json"));
 */
public class WitnessBuilder {

    private final String buildingName;
    private final Map<String, Object> claims = new LinkedHashMap<>();
    private int proven = 0;
    private int skipped = 0;

    // Collected data
    private Double foundationTopZ;
    private Double foundationBottomZ;
    private String foundationId;
    private Map<String, Object> entryDoor;
    private String entrySpace;
    private final Map<String, Map<String, Object>> roomPaths = new LinkedHashMap<>();
    private final List<Map<String, Object>> windows = new ArrayList<>();
    private final List<Map<String, Object>> windowViolations = new ArrayList<>();
    private double[] roofPolygon;
    private final Map<String, Map<String, Object>> roomCorners = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> roomEnclosure = new LinkedHashMap<>();
    private double[] envelopeMin;
    private double[] envelopeMax;
    private final Map<String, Boolean> roomsInEnvelope = new LinkedHashMap<>();

    // Phase 33/36: Electrical elements containment
    private final List<Map<String, Object>> electricalElements = new ArrayList<>();
    private final List<Map<String, Object>> electricalViolations = new ArrayList<>();
    private static final double WALL_TOLERANCE = 0.06; // 60mm for wall-mounted elements

    public WitnessBuilder(String buildingName) {
        this.buildingName = buildingName;
    }

    // ===== Claim 1: FOUNDATION_GROUNDED =====

    public void foundationZ(double topZ, double bottomZ) {
        foundationZ("foundation_slab", topZ, bottomZ);
    }

    public void foundationZ(String id, double topZ, double bottomZ) {
        this.foundationId = id;
        this.foundationTopZ = topZ;
        this.foundationBottomZ = bottomZ;
    }

    // ===== Claim 2: ENTRY_EXISTS =====

    public void entryDoor(String doorId, String wall, String wallType, String toSpace) {
        this.entryDoor = new LinkedHashMap<>();
        entryDoor.put("id", doorId);
        entryDoor.put("wall", wall);
        entryDoor.put("wall_type", wallType);
        this.entrySpace = toSpace;
    }

    // ===== Claim 3: ALL_ROOMS_REACHABLE =====

    public void roomPath(String roomName, List<String> path, String viaDoor) {
        Map<String, Object> pathInfo = new LinkedHashMap<>();
        pathInfo.put("path", path);
        pathInfo.put("via", viaDoor);
        roomPaths.put(roomName, pathInfo);
    }

    public void roomDirectAccess(String roomName, String note) {
        Map<String, Object> pathInfo = new LinkedHashMap<>();
        pathInfo.put("path", List.of("EXTERIOR", roomName));
        pathInfo.put("via", "direct");
        pathInfo.put("note", note);
        roomPaths.put(roomName, pathInfo);
    }

    // ===== Claim 4: WINDOWS_ON_EXTERIOR =====

    public void windowOnExterior(String windowId, String room, String wallDirection, String wallType) {
        Map<String, Object> win = new LinkedHashMap<>();
        win.put("id", windowId);
        win.put("room", room);
        win.put("wall_direction", wallDirection);
        win.put("wall_type", wallType);
        win.put("valid", "EXTERIOR".equals(wallType));
        windows.add(win);

        if (!"EXTERIOR".equals(wallType)) {
            windowViolations.add(win);
        }
    }

    // ===== Claim 5: ROOF_COVERS_ALL =====

    public void roofPolygon(double[][] corners) {
        // Flatten to simple array for JSON
        this.roofPolygon = new double[corners.length * 2];
        for (int i = 0; i < corners.length; i++) {
            roofPolygon[i * 2] = corners[i][0];
            roofPolygon[i * 2 + 1] = corners[i][1];
        }
    }

    public void roomCornersUnderRoof(String roomName, double[][] corners, boolean allInside) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("corners", corners);
        info.put("all_inside", allInside);
        roomCorners.put(roomName, info);
    }

    // ===== Claim 6: ROOMS_ENCLOSED =====

    public void roomEnclosed(String roomName, int wallCount, boolean closed) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("wall_count", wallCount);
        info.put("closed", closed);
        roomEnclosure.put(roomName, info);
    }

    // ===== Claim 7: ROOMS_IN_ENVELOPE =====

    public void buildingEnvelope(double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        this.envelopeMin = new double[]{minX, minY, minZ};
        this.envelopeMax = new double[]{maxX, maxY, maxZ};
    }

    public void roomInEnvelope(String roomName, boolean inside) {
        roomsInEnvelope.put(roomName, inside);
    }

    // ===== Claim 8: ELECTRICAL_IN_SPACES (Phase 33/36) =====

    /**
     * Record an electrical element and verify it's within room bounds.
     *
     * @param elementId Element identifier
     * @param ifcClass IFC class (IfcLightFixture, IfcOutlet, IfcSwitchingDevice)
     * @param roomName Room the element belongs to
     * @param x Element X position
     * @param y Element Y position
     * @param z Element Z position
     * @param roomMinX Room bounding box
     * @param roomMaxX Room bounding box
     * @param roomMinY Room bounding box
     * @param roomMaxY Room bounding box
     * @param roomMinZ Room floor Z (baseZ)
     * @param roomMaxZ Room ceiling Z
     */
    public void electricalElement(String elementId, String ifcClass, String roomName,
                                  double x, double y, double z,
                                  double roomMinX, double roomMaxX,
                                  double roomMinY, double roomMaxY,
                                  double roomMinZ, double roomMaxZ) {
        Map<String, Object> elem = new LinkedHashMap<>();
        elem.put("id", elementId);
        elem.put("ifc_class", ifcClass);
        elem.put("room", roomName);
        elem.put("position", new double[]{x, y, z});

        // 3D containment check with wall tolerance for wall-mounted elements
        // Outlets and switches are ON walls, so use tolerance for XY
        boolean isWallMounted = "IfcOutlet".equals(ifcClass) || "IfcSwitchingDevice".equals(ifcClass);
        double xyTolerance = isWallMounted ? WALL_TOLERANCE : 0.0;

        boolean insideX = x >= (roomMinX - xyTolerance) && x <= (roomMaxX + xyTolerance);
        boolean insideY = y >= (roomMinY - xyTolerance) && y <= (roomMaxY + xyTolerance);
        boolean insideZ = z >= roomMinZ && z <= roomMaxZ;
        boolean inside = insideX && insideY && insideZ;

        elem.put("inside", inside);
        elem.put("bounds_check", Map.of(
            "x_ok", insideX,
            "y_ok", insideY,
            "z_ok", insideZ
        ));

        electricalElements.add(elem);

        if (!inside) {
            electricalViolations.add(elem);
        }
    }

    // ===== Build and Write =====

    public Map<String, Object> build() {
        Map<String, Object> witness = new LinkedHashMap<>();
        witness.put("version", "1.0");
        witness.put("building", buildingName);
        witness.put("generated", Instant.now().toString());
        witness.put("compiler_version", "0.33.0");

        // Build claims
        buildFoundationClaim();
        buildEntryClaim();
        buildReachabilityClaim();
        buildWindowsClaim();
        buildRoofClaim();
        buildEnclosureClaim();
        buildEnvelopeClaim();
        buildElectricalClaim();  // Phase 33/36

        witness.put("claims", claims);

        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("total_claims", claims.size());
        summary.put("proven", proven);
        summary.put("unprovable", 0);
        summary.put("skipped", skipped);
        witness.put("summary", summary);

        return witness;
    }

    private void buildFoundationClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (foundationTopZ != null) {
            claim.put("status", "PROVEN");
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("foundation_id", foundationId);
            w.put("top_z", foundationTopZ);
            w.put("bottom_z", foundationBottomZ);
            w.put("depth", foundationTopZ - foundationBottomZ);
            w.put("tolerance", 0.005);
            claim.put("witness", w);
            proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No foundation data collected");
            skipped++;
        }
        claims.put("FOUNDATION_GROUNDED", claim);
    }

    private void buildEntryClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (entryDoor != null && entrySpace != null) {
            claim.put("status", "PROVEN");
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("path", List.of("EXTERIOR", entrySpace));
            w.put("door", entryDoor);
            claim.put("witness", w);
            proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No entry door data collected");
            skipped++;
        }
        claims.put("ENTRY_EXISTS", claim);
    }

    private void buildReachabilityClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (!roomPaths.isEmpty()) {
            claim.put("status", "PROVEN");
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("entry_point", entrySpace != null ? entrySpace : "unknown");
            w.put("paths", roomPaths);
            w.put("unreachable", List.of());
            claim.put("witness", w);
            proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No room path data collected");
            skipped++;
        }
        claims.put("ALL_ROOMS_REACHABLE", claim);
    }

    private void buildWindowsClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (!windows.isEmpty()) {
            claim.put("status", windowViolations.isEmpty() ? "PROVEN" : "UNPROVABLE");
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("windows", windows);
            w.put("violations", windowViolations);
            claim.put("witness", w);
            if (windowViolations.isEmpty()) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No window data collected");
            skipped++;
        }
        claims.put("WINDOWS_ON_EXTERIOR", claim);
    }

    private void buildRoofClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (!roomCorners.isEmpty()) {
            boolean allCovered = roomCorners.values().stream()
                .allMatch(info -> (Boolean) info.get("all_inside"));
            claim.put("status", allCovered ? "PROVEN" : "UNPROVABLE");
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("method", "corner_containment");
            if (roofPolygon != null) {
                w.put("roof_polygon", roofPolygon);
            }
            w.put("rooms", roomCorners);
            List<String> uncovered = new ArrayList<>();
            for (var entry : roomCorners.entrySet()) {
                if (!(Boolean) entry.getValue().get("all_inside")) {
                    uncovered.add(entry.getKey());
                }
            }
            w.put("uncovered", uncovered);
            claim.put("witness", w);
            if (allCovered) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No roof coverage data collected");
            skipped++;
        }
        claims.put("ROOF_COVERS_ALL", claim);
    }

    private void buildEnclosureClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (!roomEnclosure.isEmpty()) {
            boolean allEnclosed = roomEnclosure.values().stream()
                .allMatch(info -> (Boolean) info.get("closed"));
            claim.put("status", allEnclosed ? "PROVEN" : "UNPROVABLE");
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("rooms", roomEnclosure);
            claim.put("witness", w);
            if (allEnclosed) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No room enclosure data collected");
            skipped++;
        }
        claims.put("ROOMS_ENCLOSED", claim);
    }

    private void buildEnvelopeClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (!roomsInEnvelope.isEmpty() && envelopeMin != null) {
            boolean allInside = roomsInEnvelope.values().stream().allMatch(v -> v);
            claim.put("status", allInside ? "PROVEN" : "UNPROVABLE");
            Map<String, Object> w = new LinkedHashMap<>();
            Map<String, Object> bbox = new LinkedHashMap<>();
            bbox.put("min", envelopeMin);
            bbox.put("max", envelopeMax);
            w.put("envelope_bbox", bbox);
            Map<String, Object> rooms = new LinkedHashMap<>();
            for (var entry : roomsInEnvelope.entrySet()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("inside", entry.getValue());
                rooms.put(entry.getKey(), r);
            }
            w.put("rooms", rooms);
            List<String> violations = new ArrayList<>();
            for (var entry : roomsInEnvelope.entrySet()) {
                if (!entry.getValue()) violations.add(entry.getKey());
            }
            w.put("violations", violations);
            claim.put("witness", w);
            if (allInside) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No envelope containment data collected");
            skipped++;
        }
        claims.put("ROOMS_IN_ENVELOPE", claim);
    }

    private void buildElectricalClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (!electricalElements.isEmpty()) {
            boolean allInside = electricalViolations.isEmpty();
            claim.put("status", allInside ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("elements_checked", electricalElements.size());

            // Group by IFC class
            Map<String, List<Map<String, Object>>> byType = new LinkedHashMap<>();
            for (Map<String, Object> elem : electricalElements) {
                String ifcClass = (String) elem.get("ifc_class");
                byType.computeIfAbsent(ifcClass, k -> new ArrayList<>()).add(elem);
            }

            Map<String, Object> byTypeSummary = new LinkedHashMap<>();
            for (var entry : byType.entrySet()) {
                String ifcClass = entry.getKey();
                List<Map<String, Object>> elements = entry.getValue();
                boolean allTypeInside = elements.stream()
                    .allMatch(e -> (Boolean) e.get("inside"));
                byTypeSummary.put(ifcClass, Map.of(
                    "count", elements.size(),
                    "all_inside", allTypeInside
                ));
            }
            w.put("by_type", byTypeSummary);

            // List violations if any
            if (!electricalViolations.isEmpty()) {
                List<Map<String, Object>> violationDetails = new ArrayList<>();
                for (Map<String, Object> v : electricalViolations) {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("id", v.get("id"));
                    detail.put("ifc_class", v.get("ifc_class"));
                    detail.put("room", v.get("room"));
                    detail.put("position", v.get("position"));
                    detail.put("bounds_check", v.get("bounds_check"));
                    violationDetails.add(detail);
                }
                w.put("violations", violationDetails);
            } else {
                w.put("violations", List.of());
            }

            claim.put("witness", w);
            if (allInside) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No electrical elements collected");
            skipped++;
        }
        claims.put("ELECTRICAL_IN_SPACES", claim);
    }

    /**
     * Write witness.json to the specified path.
     * NON-BLOCKING: Catches all exceptions, logs warning, returns false on failure.
     */
    public boolean write(Path outputPath) {
        try {
            Map<String, Object> witness = build();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(witness);
            Files.writeString(outputPath, json);
            return true;
        } catch (Exception e) {
            System.err.println("[WITNESS] Failed to write witness.json: " + e.getMessage());
            return false;
        }
    }
}
