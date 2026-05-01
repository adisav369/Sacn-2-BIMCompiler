/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import java.util.List;
import java.util.Set;

/**
 * Contract for elements belonging to zones.
 * Core pattern for compartmentalization and system design.
 *
 * <p>Zone Types:
 * <ul>
 *   <li>Fire compartments - fire-rated boundaries</li>
 *   <li>HVAC zones - thermal control areas</li>
 *   <li>Acoustic zones - noise isolation areas</li>
 *   <li>Security zones - access control areas</li>
 *   <li>Ownership zones - unit boundaries (strata)</li>
 *   <li>Lighting zones - daylight/artificial control</li>
 * </ul>
 *
 * <p>Pattern: Define zone boundaries, elements inherit zone membership.
 * <pre>
 * FIRE_ZONE "FZ_01" {
 *     rating: "60min"
 *     rooms: [unit_501, unit_502, corridor_5]
 *     max_area: 1000m²
 * }
 *
 * HVAC_ZONE "AHU_01" {
 *     served_by: "AHU_Level5"
 *     rooms: [unit_501, unit_502]
 *     design_temp: 24°C
 * }
 * </pre>
 *
 * <p>80/20: Most building systems are zone-based.
 * Correct zoning → correct MEP sizing automatically.
 */
public interface IZoned extends IBIMEntity {

    /**
     * All zones this element belongs to.
     * An element can belong to multiple zone types.
     */
    Set<ZoneMembership> zones();

    /**
     * Primary zone for ownership/billing purposes.
     * E.g., which unit does this element belong to.
     */
    default String primaryZone() {
        return zones().stream()
            .filter(z -> z.zoneType() == ZoneType.OWNERSHIP)
            .map(ZoneMembership::zoneId)
            .findFirst()
            .orElse(null);
    }

    /**
     * Fire compartment this element belongs to.
     */
    default String fireCompartment() {
        return zones().stream()
            .filter(z -> z.zoneType() == ZoneType.FIRE)
            .map(ZoneMembership::zoneId)
            .findFirst()
            .orElse(null);
    }

    /**
     * Zone membership record.
     */
    record ZoneMembership(
        ZoneType zoneType,
        String zoneId,
        String zoneName,
        ZoneRole role    // INTERNAL, BOUNDARY, SHARED
    ) {}

    /**
     * Types of zones.
     */
    enum ZoneType {
        FIRE,           // Fire compartments (UBBL, BS 9999)
        SMOKE,          // Smoke control zones
        HVAC,           // HVAC serving zones
        ACOUSTIC,       // Acoustic isolation zones
        SECURITY,       // Access control zones
        OWNERSHIP,      // Strata/unit boundaries
        LIGHTING,       // Lighting control zones
        ELECTRICAL,     // Electrical distribution zones
        WATER_METER,    // Water metering zones
        GAS_METER       // Gas metering zones
    }

    /**
     * Element's role within zone.
     */
    enum ZoneRole {
        INTERNAL,   // Fully within zone
        BOUNDARY,   // At zone boundary (may be rated)
        SHARED,     // Shared between zones (common areas)
        PENETRATION // Passes through (needs firestopping)
    }
}
