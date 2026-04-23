package com.bim.compiler.drawing2d.model;

import java.util.List;
import java.util.Set;

/**
 * Auto-detected scope of a building's 2D drawing capability.
 *
 * ScopeDao queries the building DB to determine what this building
 * can produce: how many habitable storeys, which MEP disciplines
 * have elements, whether mesh data exists for section cuts and
 * roof plans.
 *
 * This replaces the hand-crafted {PREFIX}_2D.json manifests.
 * Implementing 2D_ARCHITECTURAL_LAYOUT.md §2 Step 1-2.
 */
public record DrawingScope(
    List<StoreyInfo> storeys,
    Set<String> disciplines,
    boolean hasMeshData,
    boolean hasRoofElements
) {

    /**
     * One habitable storey detected in the building.
     *
     * @param name  storey name from spatial_structure ("Level 1", "Ground Floor")
     * @param floorZ  floor elevation in metres (min Z of walls/doors/windows on that storey)
     * @param code  short code for sheet numbering ("GF", "FF", "2F", "3F"...)
     */
    public record StoreyInfo(String name, double floorZ, String code) {}

    public int storeyCount() { return storeys.size(); }

    /** Can produce at least one floor plan. */
    public boolean canProducePlan() { return !storeys.isEmpty(); }

    /** Can produce 4 elevation views. */
    public boolean canProduceElevation() { return !storeys.isEmpty(); }

    /** Can produce vertical section cuts (requires triangle mesh data). */
    public boolean canProduceSection() { return hasMeshData; }

    /** Can produce a roof plan (requires roof-level elements with mesh). */
    public boolean canProduceRoofPlan() { return hasRoofElements; }

    /** Can produce an MEP plan for the given discipline. */
    public boolean canProduceMep(String disc) { return disciplines.contains(disc); }

    /** Can produce an opening schedule (always true if storeys exist). */
    public boolean canProduceSchedule() { return !storeys.isEmpty(); }
}
