package com.bim.tools.sanity.model;

/**
 * Represents a building element read from the database.
 * Immutable value object.
 */
public record Element(
    String guid,
    String ifcClass,
    String name,
    String discipline,
    BoundingBox bbox
) {
    public boolean isFoundation() {
        return "IfcSlab".equals(ifcClass) &&
               name != null && name.toLowerCase().contains("foundation");
    }

    public boolean isDoor() {
        return "IfcDoor".equals(ifcClass);
    }

    public boolean isWindow() {
        return "IfcWindow".equals(ifcClass);
    }

    public boolean isWall() {
        // Traditional IFC walls
        if ("IfcWall".equals(ifcClass) || "IfcWallStandardCase".equals(ifcClass)) {
            return true;
        }
        // Steel frame construction: cladding plates that act as wall skin
        if ("IfcPlate".equals(ifcClass) && guid != null &&
            guid.toUpperCase().contains("WALL") && guid.toUpperCase().contains("CLADDING")) {
            return true;
        }
        return false;
    }

    /**
     * Check if this element represents a wall cladding (exterior surface).
     */
    public boolean isWallCladding() {
        return "IfcPlate".equals(ifcClass) && guid != null &&
               guid.toUpperCase().contains("CLADDING") && guid.toUpperCase().contains("WALL");
    }

    /**
     * Check if this is an exterior-facing wall element.
     * Looks for cardinal direction indicators in the guid.
     * Phase 64: Support both "NORTH_WALL" and "WALL_NORTH" patterns.
     */
    public boolean isExteriorWall() {
        if (guid == null) return false;
        String upper = guid.toUpperCase();
        // Must not be interior wall
        if (upper.contains("INTERIOR")) return false;
        // Check both naming patterns
        return upper.contains("NORTH_WALL") || upper.contains("SOUTH_WALL") ||
               upper.contains("EAST_WALL") || upper.contains("WEST_WALL") ||
               upper.contains("WALL_NORTH") || upper.contains("WALL_SOUTH") ||
               upper.contains("WALL_EAST") || upper.contains("WALL_WEST");
    }

    /**
     * Check if this is an interior wall element.
     */
    public boolean isInteriorWall() {
        if (guid == null) return false;
        return guid.toUpperCase().contains("INTERIOR") && guid.toUpperCase().contains("WALL");
    }

    public boolean isSpace() {
        return "IfcSpace".equals(ifcClass);
    }

    public boolean isRoof() {
        return "IfcRoof".equals(ifcClass) || "IfcSlab".equals(ifcClass) &&
               name != null && name.toLowerCase().contains("roof");
    }

    public boolean isSlab() {
        return "IfcSlab".equals(ifcClass);
    }

    public boolean isStair() {
        return "IfcStair".equals(ifcClass);
    }

    public boolean isStairFlight() {
        return "IfcStairFlight".equals(ifcClass);
    }

    public boolean isLanding() {
        return "IfcSlab".equals(ifcClass) && name != null &&
               name.toLowerCase().contains("landing");
    }

    public boolean isRailing() {
        return "IfcRailing".equals(ifcClass);
    }

    public double width() {
        return bbox != null ? bbox.width() : 0;
    }

    public double depth() {
        return bbox != null ? bbox.depth() : 0;
    }

    public double height() {
        return bbox != null ? bbox.height() : 0;
    }

    @Override
    public String toString() {
        return String.format("%s[%s] %s", ifcClass, guid, name != null ? name : "");
    }
}
