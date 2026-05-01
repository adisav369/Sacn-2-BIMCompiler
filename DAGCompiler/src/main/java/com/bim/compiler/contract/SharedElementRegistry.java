/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import com.bim.compiler.geometry.Point3D;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Central registry for shared elements.
 * Implements Repository pattern (DDD) + Registry pattern (GoF).
 *
 * <p>Elements register here BEFORE being added to assemblies.
 * Assemblies then REFERENCE shared elements, not own them.
 *
 * <p>This is the key data structure for preventing the "boundary ownership"
 * anti-pattern. Instead of:
 * <pre>
 * // WRONG: Each wall creates its own corner stud
 * Wall1: creates STUD_R at corner
 * Wall2: creates STUD_L at same corner -> DUPLICATE
 * </pre>
 *
 * <p>We have:
 * <pre>
 * // RIGHT: Registry owns corner, walls reference it
 * Registry: owns CORNER_STUD at corner position
 * Wall1: connectsTo(registry.getJunction(...))
 * Wall2: connectsTo(registry.getJunction(...)) -> SHARED
 * </pre>
 *
 * <p>The registry manages three categories of shared elements:
 * <ul>
 *   <li><b>Junctions:</b> Points where linear elements meet (corners, T-junctions)</li>
 *   <li><b>Spanning:</b> Elements that continue across storeys (columns, risers)</li>
 *   <li><b>Infrastructure:</b> MEP elements serving multiple units (stacks, mains)</li>
 * </ul>
 *
 * @see <a href="https://martinfowler.com/eaaCatalog/repository.html">Repository Pattern</a>
 */
public class SharedElementRegistry {

    /** Tolerance for location matching (1mm = 0.001m) */
    private static final double LOCATION_TOLERANCE = 0.001;

    /** Shared junction points (corners, T-junctions, intersections) */
    private final Map<String, JunctionPoint> junctions = new ConcurrentHashMap<>();

    /** Elements spanning multiple storeys (columns, risers, shafts) */
    private final Map<String, ContinuousElement> spanning = new ConcurrentHashMap<>();

    /** Shared infrastructure (stacks, mains, risers) */
    private final Map<String, SharedInfrastructure> infrastructure = new ConcurrentHashMap<>();

    /**
     * Creates an empty registry.
     */
    public SharedElementRegistry() {
    }

    // ========== Junction Point Methods ==========

    /**
     * Get or create a junction point at a location.
     * If junction exists within tolerance, returns existing.
     * Otherwise creates new junction and registers it.
     *
     * <p>This is the primary method for boundary sharing. When a wall
     * needs a corner element, it calls this method. If another wall
     * has already created the junction, the same junction is returned.
     *
     * @param location 3D coordinates
     * @param type Junction type (CORNER, T_JUNCTION, CROSS, etc.)
     * @return Existing or new junction
     */
    public JunctionPoint getOrCreateJunction(Point3D location, JunctionType type) {
        String key = locationKey(location);
        return junctions.computeIfAbsent(key, k ->
            new JunctionPoint(generateJunctionId(location, type), location, type)
        );
    }

    /**
     * Get junction at location if it exists.
     *
     * @param location 3D coordinates
     * @return Junction if found, null otherwise
     */
    public JunctionPoint getJunction(Point3D location) {
        return junctions.get(locationKey(location));
    }

    /**
     * Get junction by ID.
     *
     * @param junctionId Junction identifier
     * @return Junction if found, null otherwise
     */
    public JunctionPoint getJunctionById(String junctionId) {
        return junctions.values().stream()
            .filter(j -> j.id().equals(junctionId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get all registered junctions.
     *
     * @return Unmodifiable collection of junctions
     */
    public Collection<JunctionPoint> allJunctions() {
        return junctions.values();
    }

    // ========== Continuous Element Methods ==========

    /**
     * Get or create a continuous element spanning storeys.
     *
     * @param continuityId Stable ID across storeys (e.g., "COL_A1")
     * @param creator Supplier for new element if not exists
     * @return Existing or new continuous element
     */
    public ContinuousElement getOrCreateContinuous(
            String continuityId,
            Supplier<ContinuousElement> creator) {
        return spanning.computeIfAbsent(continuityId, k -> creator.get());
    }

    /**
     * Get continuous element by ID.
     *
     * @param continuityId Continuity identifier
     * @return Element if found, null otherwise
     */
    public ContinuousElement getContinuous(String continuityId) {
        return spanning.get(continuityId);
    }

    /**
     * Get all registered continuous elements.
     *
     * @return Unmodifiable collection of continuous elements
     */
    public Collection<ContinuousElement> allContinuous() {
        return spanning.values();
    }

    // ========== Shared Infrastructure Methods ==========

    /**
     * Register shared infrastructure (called once, referenced many).
     *
     * @param id Infrastructure identifier
     * @param infra Infrastructure element
     */
    public void registerInfrastructure(String id, SharedInfrastructure infra) {
        infrastructure.put(id, infra);
    }

    /**
     * Get shared infrastructure by ID.
     *
     * @param id Infrastructure identifier
     * @return Infrastructure if found, null otherwise
     */
    public SharedInfrastructure getInfrastructure(String id) {
        return infrastructure.get(id);
    }

    /**
     * Get all registered infrastructure.
     *
     * @return Unmodifiable collection of infrastructure
     */
    public Collection<SharedInfrastructure> allInfrastructure() {
        return infrastructure.values();
    }

    // ========== Utility Methods ==========

    /**
     * Generate location key with tolerance.
     * Rounds to nearest millimeter to handle floating point.
     */
    private String locationKey(Point3D p) {
        // Round to nearest mm for consistent key generation
        double x = Math.round(p.x() * 1000) / 1000.0;
        double y = Math.round(p.y() * 1000) / 1000.0;
        double z = Math.round(p.z() * 1000) / 1000.0;
        return String.format("%.3f_%.3f_%.3f", x, y, z);
    }

    /**
     * Generate unique ID for a junction.
     */
    private String generateJunctionId(Point3D location, JunctionType type) {
        return String.format("%s_%.3f_%.3f_%.3f",
            type.name(), location.x(), location.y(), location.z());
    }

    /**
     * Get count of all registered elements.
     *
     * @return Total count across all categories
     */
    public int totalCount() {
        return junctions.size() + spanning.size() + infrastructure.size();
    }

    /**
     * Clear all registered elements.
     * Typically called at start of compilation.
     */
    public void clear() {
        junctions.clear();
        spanning.clear();
        infrastructure.clear();
    }

    /**
     * Get summary statistics for debugging.
     */
    public String summary() {
        return String.format("Registry[%d junctions, %d spanning, %d infrastructure]",
            junctions.size(), spanning.size(), infrastructure.size());
    }
}
