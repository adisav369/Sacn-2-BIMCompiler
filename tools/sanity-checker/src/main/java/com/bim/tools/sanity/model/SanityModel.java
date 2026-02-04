package com.bim.tools.sanity.model;

import java.sql.*;
import java.util.*;

/**
 * Lightweight read-only model that loads building data from a .db file.
 * This class does NOT import any compiler code - it reads the database schema directly.
 */
public class SanityModel implements AutoCloseable {
    private final Connection conn;
    private final String dbPath;

    private List<Element> allElements;
    private List<Element> walls;
    private List<Element> doors;
    private List<Element> windows;
    private List<Element> spaces;
    private List<Element> foundations;
    private List<Element> roofs;
    private BoundingBox buildingEnvelope;

    public SanityModel(String dbPath) throws SQLException {
        this.dbPath = dbPath;
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        loadElements();
    }

    private void loadElements() throws SQLException {
        allElements = new ArrayList<>();
        walls = new ArrayList<>();
        doors = new ArrayList<>();
        windows = new ArrayList<>();
        spaces = new ArrayList<>();
        foundations = new ArrayList<>();
        roofs = new ArrayList<>();

        // Note: elements_rtree columns are: id, minX, maxX, minY, maxY, minZ, maxZ
        String sql = """
            SELECT m.guid, m.ifc_class, m.element_name, m.discipline,
                   r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta m
            LEFT JOIN elements_rtree r ON m.id = r.id
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                BoundingBox bbox = null;
                if (rs.getObject("minX") != null) {
                    bbox = new BoundingBox(
                        rs.getDouble("minX"), rs.getDouble("minY"), rs.getDouble("minZ"),
                        rs.getDouble("maxX"), rs.getDouble("maxY"), rs.getDouble("maxZ")
                    );
                }

                Element elem = new Element(
                    rs.getString("guid"),
                    rs.getString("ifc_class"),
                    rs.getString("element_name"),
                    rs.getString("discipline"),
                    bbox
                );

                allElements.add(elem);

                if (elem.isWall()) walls.add(elem);
                if (elem.isDoor()) doors.add(elem);
                if (elem.isWindow()) windows.add(elem);
                if (elem.isSpace()) spaces.add(elem);
                if (elem.isFoundation()) foundations.add(elem);
                if (elem.isRoof()) roofs.add(elem);
            }
        }

        computeBuildingEnvelope();
    }

    private void computeBuildingEnvelope() {
        BoundingBox envelope = null;
        // Use exterior walls for envelope
        for (Element wall : walls) {
            if (wall.bbox() != null && wall.isExteriorWall()) {
                envelope = envelope == null ? wall.bbox() : envelope.union(wall.bbox());
            }
        }
        // If no exterior walls found, use all walls
        if (envelope == null) {
            for (Element wall : walls) {
                if (wall.bbox() != null) {
                    envelope = envelope == null ? wall.bbox() : envelope.union(wall.bbox());
                }
            }
        }
        this.buildingEnvelope = envelope;
    }

    public String getDbPath() { return dbPath; }

    /**
     * Get actual storey count from spatial_structure table.
     * This is authoritative - counts IfcBuildingStorey records only.
     */
    public int getStoreyCount() {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COUNT(*) FROM spatial_structure WHERE type = 'IfcBuildingStorey'")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            // Fall back to 0
        }
        return 0;
    }

    /**
     * Get storey names from spatial_structure table.
     */
    public List<String> getStoreyNames() {
        List<String> names = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT name FROM spatial_structure WHERE type = 'IfcBuildingStorey' ORDER BY name")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        } catch (SQLException e) {
            // Return empty list
        }
        return names;
    }

    public List<Element> getAllElements() { return Collections.unmodifiableList(allElements); }
    public List<Element> getWalls() { return Collections.unmodifiableList(walls); }
    public List<Element> getDoors() { return Collections.unmodifiableList(doors); }
    public List<Element> getWindows() { return Collections.unmodifiableList(windows); }
    public List<Element> getSpaces() { return Collections.unmodifiableList(spaces); }
    public List<Element> getFoundations() { return Collections.unmodifiableList(foundations); }
    public List<Element> getRoofs() { return Collections.unmodifiableList(roofs); }
    public BoundingBox getBuildingEnvelope() { return buildingEnvelope; }

    /**
     * Find exterior walls (walls on the building perimeter).
     * Uses both element guid patterns and geometric position.
     */
    public List<Element> getExteriorWalls() {
        List<Element> exterior = new ArrayList<>();

        for (Element wall : walls) {
            // First check by guid pattern (more reliable)
            if (wall.isExteriorWall()) {
                exterior.add(wall);
                continue;
            }

            // Fallback to geometric check
            if (buildingEnvelope != null && wall.bbox() != null && !wall.isInteriorWall()) {
                double tolerance = 0.05; // 50mm
                boolean onMinX = Math.abs(wall.bbox().minX() - buildingEnvelope.minX()) <= tolerance;
                boolean onMaxX = Math.abs(wall.bbox().maxX() - buildingEnvelope.maxX()) <= tolerance;
                boolean onMinY = Math.abs(wall.bbox().minY() - buildingEnvelope.minY()) <= tolerance;
                boolean onMaxY = Math.abs(wall.bbox().maxY() - buildingEnvelope.maxY()) <= tolerance;

                if (onMinX || onMaxX || onMinY || onMaxY) {
                    exterior.add(wall);
                }
            }
        }
        return exterior;
    }

    /**
     * Find interior walls (walls not on the building perimeter).
     */
    public List<Element> getInteriorWalls() {
        List<Element> interior = new ArrayList<>();
        Set<String> exteriorGuids = new HashSet<>();
        for (Element e : getExteriorWalls()) {
            exteriorGuids.add(e.guid());
        }

        for (Element wall : walls) {
            // Check by guid pattern first
            if (wall.isInteriorWall()) {
                interior.add(wall);
            } else if (!exteriorGuids.contains(wall.guid())) {
                interior.add(wall);
            }
        }
        return interior;
    }

    /**
     * Extract implicit space names from interior wall guids.
     * Pattern: CLADDING_INTERIOR_{room1}_{room2}_WALL_ASSEMBLY_*
     * Returns set of unique room names.
     */
    public Set<String> getImplicitSpaceNames() {
        Set<String> roomNames = new HashSet<>();

        for (Element wall : walls) {
            if (wall.guid() == null || !wall.isInteriorWall()) continue;

            // Parse guid like: CLADDING_INTERIOR_anjung_common_WALL_ASSEMBLY_Ground
            String guid = wall.guid();
            int interiorIdx = guid.indexOf("INTERIOR_");
            int wallIdx = guid.indexOf("_WALL_ASSEMBLY");

            if (interiorIdx >= 0 && wallIdx > interiorIdx) {
                String roomPart = guid.substring(interiorIdx + 9, wallIdx);
                // roomPart is like "anjung_common" or "bilik_utama_bilik_mandi"
                String[] parts = roomPart.split("_");
                // Typically two room names separated by underscore
                // But room names can contain underscores too (e.g., bilik_utama)
                // Heuristic: look for common room name patterns
                roomNames.addAll(extractRoomNames(roomPart));
            }
        }

        // Also add "exterior" as implicit space for entry door checks
        if (!roomNames.isEmpty()) {
            roomNames.add("exterior");
        }

        return roomNames;
    }

    private Set<String> extractRoomNames(String roomPart) {
        Set<String> names = new HashSet<>();
        // Known room patterns (sorted by length descending to match longest first)
        String[] knownRooms = {
            // Multi-word room names (longest first)
            "bilik_utama", "bilik_mandi", "bilik_air", "bilik_2", "bilik_3",
            "bilik_4", "ruang_tamu", "bath_g", "bath_u",
            // Single-word room names
            "anjung", "common", "dapur", "stor", "living", "kitchen",
            "master", "child", "landing", "bathroom", "bedroom",
            "corridor", "hall", "porch", "garage", "laundry"
        };

        String remaining = roomPart;
        for (String room : knownRooms) {
            if (remaining.contains(room)) {
                names.add(room);
                remaining = remaining.replace(room, "").replace("__", "_");
            }
        }

        // If no known rooms found, try smarter splitting
        // Assume room names are separated by underscore and typically short (1-2 words)
        if (names.isEmpty()) {
            String[] parts = roomPart.split("_");
            // Only add parts that look like room names (length > 1)
            for (String part : parts) {
                if (!part.isEmpty() && part.length() > 1) {
                    names.add(part);
                }
            }
        }

        return names;
    }

    /**
     * Get room adjacency information from interior walls.
     * Returns map of room name to set of adjacent room names.
     */
    public Map<String, Set<String>> getRoomAdjacency() {
        Map<String, Set<String>> adjacency = new HashMap<>();

        for (Element wall : walls) {
            if (wall.guid() == null || !wall.isInteriorWall()) continue;

            String guid = wall.guid();
            int interiorIdx = guid.indexOf("INTERIOR_");
            int wallIdx = guid.indexOf("_WALL_ASSEMBLY");

            if (interiorIdx >= 0 && wallIdx > interiorIdx) {
                String roomPart = guid.substring(interiorIdx + 9, wallIdx);
                Set<String> rooms = extractRoomNames(roomPart);

                // Connect all extracted rooms to each other
                List<String> roomList = new ArrayList<>(rooms);
                for (int i = 0; i < roomList.size(); i++) {
                    for (int j = i + 1; j < roomList.size(); j++) {
                        String r1 = roomList.get(i);
                        String r2 = roomList.get(j);
                        adjacency.computeIfAbsent(r1, k -> new HashSet<>()).add(r2);
                        adjacency.computeIfAbsent(r2, k -> new HashSet<>()).add(r1);
                    }
                }
            }
        }

        return adjacency;
    }

    /**
     * Find the wall that contains a given element (door/window).
     */
    public Element findContainingWall(Element opening) {
        if (opening.bbox() == null) return null;

        for (Element wall : walls) {
            if (wall.bbox() == null) continue;

            // Check if opening bbox intersects wall bbox
            if (wall.bbox().intersects(opening.bbox())) {
                return wall;
            }
        }
        return null;
    }

    /**
     * Find spaces (rooms) that a door connects.
     * Returns list of 0, 1, or 2 spaces.
     */
    public List<Element> findSpacesForDoor(Element door) {
        if (door.bbox() == null) return Collections.emptyList();

        List<Element> connected = new ArrayList<>();
        BoundingBox expanded = door.bbox().expand(0.1); // 100mm tolerance

        for (Element space : spaces) {
            if (space.bbox() == null) continue;
            if (expanded.intersectsXY(space.bbox())) {
                connected.add(space);
            }
        }
        return connected;
    }

    /**
     * Check if a door is on the building perimeter (exterior door).
     */
    public boolean isDoorOnPerimeter(Element door) {
        if (door.bbox() == null || buildingEnvelope == null) return false;

        double tolerance = 0.15; // 150mm for door-to-wall tolerance
        double cx = door.bbox().centerX();
        double cy = door.bbox().centerY();

        return buildingEnvelope.isOnPerimeterXY(cx, cy, tolerance);
    }

    /**
     * Get the space type from a space name (heuristic).
     */
    public String inferSpaceType(Element space) {
        if (space.name() == null) return "UNKNOWN";

        String name = space.name().toLowerCase();
        if (name.contains("corridor") || name.contains("hall") || name.contains("lorong") || name.contains("koridor")) {
            return "CORRIDOR";
        }
        if (name.contains("bath") || name.contains("toilet") || name.contains("wc") || name.contains("bilik_air")) {
            return "BATHROOM";
        }
        if (name.contains("bed") || name.contains("bilik_tidur") || name.contains("bilik_utama")) {
            return "BEDROOM";
        }
        if (name.contains("living") || name.contains("ruang_tamu")) {
            return "LIVING";
        }
        if (name.contains("kitchen") || name.contains("dapur")) {
            return "KITCHEN";
        }
        if (name.contains("storage") || name.contains("stor")) {
            return "STORAGE";
        }
        if (name.contains("porch") || name.contains("anjung")) {
            return "PORCH";
        }
        return "ROOM";
    }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}
