package com.bim.compiler.dsl;

import java.util.List;

/**
 * Phase 107: Resolves interior room layout for residential unit zones.
 *
 * UNIT rooms from FloorPlateBOMResolver are zone containers — their interior
 * rooms (living, bedroom, kitchen, bathroom) are declared in DSL but never
 * compiled because UNIT is in skipKeywords.
 *
 * This resolver will read ad_unit_type for data-driven room layouts.
 * Currently a stub that returns empty — enabled in a future phase.
 */
class UnitInteriorResolver {

    record UnitRoom(String name, String type,
                    double minX, double minY, double maxX, double maxY) {}

    /**
     * Resolve interior rooms for a unit zone.
     *
     * @param unitType the unit type name (e.g. "unit_W1", "unit_E2")
     * @param zoneMinX zone bounds from FloorPlateBOMResolver
     * @param zoneMinY zone bounds
     * @param zoneMaxX zone bounds
     * @param zoneMaxY zone bounds
     * @return list of interior rooms with absolute coordinates (empty = stub)
     */
    List<UnitRoom> resolveInterior(String unitType,
                                    double zoneMinX, double zoneMinY,
                                    double zoneMaxX, double zoneMaxY) {
        // Phase 107: Stub — returns empty. Future phases will:
        // 1. Read ad_unit_type table for room layout templates
        // 2. Partition the zone into interior rooms
        // 3. Return rooms with absolute coordinates for compilation
        return List.of();
    }
}
