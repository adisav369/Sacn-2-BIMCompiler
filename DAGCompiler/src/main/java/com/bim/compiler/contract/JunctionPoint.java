package com.bim.compiler.contract;

import com.bim.compiler.geometry.Point3D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared junction point where multiple elements connect.
 *
 * <p>Junction points are the key to solving the "boundary ownership" problem.
 * Instead of each wall creating its own corner stud, walls reference
 * shared junction points that own the corner elements.
 *
 * <p>Maps to IfcRelConnectsPathElements relationship pattern.
 * <p>Theory: Mereotopology boundary sharing (Casati & Varzi 1999)
 */
public final class JunctionPoint {

    private final String id;
    private final Point3D location;
    private final JunctionType type;
    private final List<String> connectedElements;

    /**
     * Creates a new junction point.
     *
     * @param id Unique identifier for this junction
     * @param location 3D coordinates in world space
     * @param type Type of junction (corner, T, cross, etc.)
     */
    public JunctionPoint(String id, Point3D location, JunctionType type) {
        this.id = id;
        this.location = location;
        this.type = type;
        this.connectedElements = new ArrayList<>();
    }

    /**
     * Unique identifier for this junction.
     * Used as key in SharedElementRegistry.
     */
    public String id() {
        return id;
    }

    /**
     * 3D location of this junction in world coordinates.
     */
    public Point3D location() {
        return location;
    }

    /**
     * Type of junction (determines structural element needed).
     */
    public JunctionType type() {
        return type;
    }

    /**
     * List of element GUIDs that connect to this junction.
     * Returns unmodifiable view.
     */
    public List<String> connectedElements() {
        return Collections.unmodifiableList(connectedElements);
    }

    /**
     * Register an element as connecting to this junction.
     *
     * @param elementGuid GUID of the connecting element
     */
    public void addConnection(String elementGuid) {
        if (!connectedElements.contains(elementGuid)) {
            connectedElements.add(elementGuid);
        }
    }

    /**
     * Check if this junction has expected connectivity for its type.
     *
     * @return true if connection count matches type expectation
     */
    public boolean hasExpectedConnectivity() {
        int count = connectedElements.size();
        return switch (type) {
            case CORNER -> count == 2;
            case T_JUNCTION -> count == 3;
            case CROSS -> count == 4;
            case TERMINATION -> count == 1;
            case SPLICE -> count == 2;
            case INTERSECTION -> count >= 2;
        };
    }

    /**
     * Generate location-based key for this junction.
     * Used for deduplication in registry.
     */
    public String locationKey() {
        return String.format("%.3f_%.3f_%.3f",
            location.x(), location.y(), location.z());
    }

    @Override
    public String toString() {
        return String.format("Junction[%s at %s, %d connections]",
            type, location, connectedElements.size());
    }
}
