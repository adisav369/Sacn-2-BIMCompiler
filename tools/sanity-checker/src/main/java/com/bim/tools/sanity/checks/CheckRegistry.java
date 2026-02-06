package com.bim.tools.sanity.checks;

import java.util.*;

/**
 * Registry of all available sanity checks.
 *
 * Maps check IDs (from ad_check_applicability) to check instances.
 * Follows the Registry pattern for extensibility.
 *
 * Phase 78: Metadata-driven check registration.
 */
public class CheckRegistry {

    private static final Map<String, SanityCheck> CHECKS = new LinkedHashMap<>();

    static {
        // Register all checks by ID (must match ad_check_applicability.check_id)
        register(new FoundationCheck());
        register(new EntryDoorCheck());
        register(new WindowPlacementCheck());
        register(new RoomConnectivityCheck());
        register(new RoomProportionCheck());
        register(new RoofCoverageCheck());
        register(new EnvelopeContainmentCheck());
        register(new StoreyCountCheck());
        register(new ElectricalElementsCheck());
        register(new PlumbingElementsCheck());
        register(new WitnessVerificationCheck());
        register(new PatternBCheck());
        register(new FireProtectionCheck());
        register(new CompartmentCheck());
        register(new WallContinuityCheck());
        register(new EscapeRouteCheck());
        register(new DeadEndCorridorCheck());
        register(new StairwellCheck());
        register(new DoorClearanceCheck());
        register(new WindowAreaCheck());
        register(new CeilingHeightCheck());
        register(new FloorAreaCheck());
        register(new StructuralGridCheck());
        register(new MEPPositioningCheck());  // Phase 81: Detect origin bunching bug
        register(new OpeningOrientationCheck());  // Phase 81: Verify door/window orientation
        register(new BOMCoverageCheck());  // Phase 83: BOM assembly coverage
        register(new BOMSpatialCheck());  // Phase 84: BOM spatial overlap detection
        register(new GroundFloorAccessCheck());  // Phase 90: Ground floor entry doors + windows
        register(new RoomDoorCheck());           // Phase 90: Every room has door access
        register(new MEPSpacingCheck());         // Phase 90: Sprinkler-light min separation
        register(new FurniturePresenceCheck());  // Phase 90: Rooms have furniture
        register(new CeilingFanCheck());         // Phase 90: Every room has fan/diffuser
    }

    private static void register(SanityCheck check) {
        CHECKS.put(check.getId(), check);
    }

    /**
     * Get check by ID.
     */
    public static SanityCheck get(String checkId) {
        return CHECKS.get(checkId);
    }

    /**
     * Get all registered checks.
     */
    public static Collection<SanityCheck> getAll() {
        return Collections.unmodifiableCollection(CHECKS.values());
    }

    /**
     * Get checks by IDs (preserving order).
     */
    public static List<SanityCheck> getByIds(List<String> checkIds) {
        List<SanityCheck> result = new ArrayList<>();
        for (String id : checkIds) {
            SanityCheck check = CHECKS.get(id);
            if (check != null) {
                result.add(check);
            }
        }
        return result;
    }

    /**
     * Check if a check ID is registered.
     */
    public static boolean contains(String checkId) {
        return CHECKS.containsKey(checkId);
    }

    /**
     * Get all registered check IDs.
     */
    public static Set<String> getCheckIds() {
        return Collections.unmodifiableSet(CHECKS.keySet());
    }
}
