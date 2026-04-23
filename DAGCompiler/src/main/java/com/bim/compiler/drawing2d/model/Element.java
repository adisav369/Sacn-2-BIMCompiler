package com.bim.compiler.drawing2d.model;

/**
 * A building element projected for 2D drawing: wall, door, window, roof, slab, furniture.
 *
 * Coordinates are in metres (world space). The drawing engine scales to paper.
 * Mirrors the Python Element dataclass in drawing_writer.py.
 */
public class Element {

    private final String guid;
    private final String ifcClass;
    private final String name;
    private final double minX, maxX, minY, maxY, minZ, maxZ;

    public Element(String guid, String ifcClass, String name,
                   double minX, double maxX, double minY, double maxY,
                   double minZ, double maxZ) {
        this.guid = guid;
        this.ifcClass = ifcClass;
        this.name = name;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    // Derived properties
    public double centerX() { return (minX + maxX) / 2.0; }
    public double centerY() { return (minY + maxY) / 2.0; }
    public double centerZ() { return (minZ + maxZ) / 2.0; }
    public double widthX()  { return maxX - minX; }
    public double widthY()  { return maxY - minY; }
    public double height()  { return maxZ - minZ; }

    /** True if the element is oriented East-West (wider in X than Y). */
    public boolean isEwWall() { return widthX() > widthY(); }

    // Accessors
    public String guid()     { return guid; }
    public String ifcClass() { return ifcClass; }
    public String name()     { return name; }
    public double minX()     { return minX; }
    public double maxX()     { return maxX; }
    public double minY()     { return minY; }
    public double maxY()     { return maxY; }
    public double minZ()     { return minZ; }
    public double maxZ()     { return maxZ; }
}
