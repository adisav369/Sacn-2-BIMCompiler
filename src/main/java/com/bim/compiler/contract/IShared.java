package com.bim.compiler.contract;

import java.util.Set;

/**
 * Contract for elements shared between contexts.
 * Core pattern for multi-unit buildings.
 *
 * <p>Shared Elements:
 * <ul>
 *   <li>Party walls - between units (fire + acoustic rated)</li>
 *   <li>Common MEP - shared risers, mains, panels</li>
 *   <li>Common areas - lobbies, corridors, stairs</li>
 *   <li>Shared structure - columns, beams at unit boundaries</li>
 *   <li>Shared facades - curtain wall between units</li>
 * </ul>
 *
 * <p>Pattern: Element declares what it's shared between.
 * <pre>
 * PARTY_WALL between:[unit_501, unit_502] {
 *     fire_rating: "60min"
 *     acoustic_rating: "STC 55"
 * }
 *
 * COMMON_AREA "corridor_5" serves:[unit_501..unit_510] {
 *     ownership: "strata_common"
 * }
 * </pre>
 *
 * <p>80/20: Party walls and common areas are 90% of sharing.
 * Get these right, strata plans fall out automatically.
 */
public interface IShared extends IBIMEntity {

    /**
     * Contexts (units, zones) this element is shared between.
     * Minimum 2 for truly shared elements.
     */
    Set<String> sharedBetween();

    /**
     * Type of sharing relationship.
     */
    SharingType sharingType();

    /**
     * Ownership category for strata/maintenance.
     */
    OwnershipCategory ownership();

    /**
     * Whether costs are split equally between shared contexts.
     */
    default boolean equalCostSplit() {
        return true;
    }

    /**
     * Rating requirements due to sharing.
     * E.g., fire rating for party walls.
     */
    default String requiredRating() {
        return null;
    }

    /**
     * Types of sharing relationships.
     */
    enum SharingType {
        PARTY_WALL,     // Wall between units
        PARTY_FLOOR,    // Floor/ceiling between units
        COMMON_AREA,    // Shared circulation/amenity
        SHARED_MEP,     // Common services (risers, mains)
        SHARED_STRUCTURE, // Columns, beams at boundaries
        SHARED_FACADE,  // Curtain wall spanning units
        EASEMENT        // Access easement through unit
    }

    /**
     * Ownership categories for strata.
     */
    enum OwnershipCategory {
        LOT,            // Individual unit ownership
        COMMON_PROPERTY,// Strata common property
        LIMITED_COMMON, // Limited to specific lots
        EXCLUSIVE_USE,  // Exclusive use of common
        UTILITY         // Utility company owned
    }

    /**
     * Check if element is shared with specific context.
     */
    default boolean isSharedWith(String contextId) {
        return sharedBetween().contains(contextId);
    }

    /**
     * Get number of sharing contexts.
     */
    default int shareCount() {
        return sharedBetween().size();
    }
}
