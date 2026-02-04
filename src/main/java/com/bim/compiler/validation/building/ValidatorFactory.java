package com.bim.compiler.validation.building;

import com.bim.compiler.dsl.BuildingDefinition;
import com.bim.compiler.dsl.BuildingDefinition.*;
import com.bim.compiler.dsl.RoomType;

import java.util.*;

/**
 * Phase 28: Factory for building ValidatorChain based on building metadata.
 *
 * Reads profile, protocol, and LOD from BuildingDefinition to dynamically
 * compose the appropriate validators. This makes validation extensible
 * without code changes - new profiles/protocols just need registry entries.
 *
 * Composition order:
 * 1. GeometryValidator (always - structural correctness)
 * 2. SpaceType validators (based on room types used)
 * 3. Profile validator (regional code compliance)
 * 4. Protocol validator (building type requirements)
 * 5. LOD validator (detail level requirements)
 */
public class ValidatorFactory {

    /**
     * Create a ValidatorChain appropriate for the given building definition.
     */
    public static ValidatorChain create(BuildingDefinition def) {
        List<BuildingValidator> validators = new ArrayList<>();

        // 1. Always include base geometry validator
        validators.add(new GeometryValidator());

        // 2. Always include habitability validator (has SpaceType-aware logic)
        validators.add(new HabitabilityValidator());

        // 3. Orphaned element check (advisory - detects strays and clusters)
        validators.add(new OrphanedElementValidator());

        // 5. Add Profile validator if specified
        if (def.profile() != null && !def.profile().isEmpty()) {
            BuildingValidator profileValidator = ProfileValidatorRegistry.get(def.profile());
            if (profileValidator != null) {
                validators.add(profileValidator);
            }
        }

        // 6. Add Protocol validator if specified
        if (def.protocol() != null && !def.protocol().isEmpty()) {
            BuildingValidator protocolValidator = ProtocolValidatorRegistry.get(def.protocol());
            if (protocolValidator != null) {
                validators.add(protocolValidator);
            }
        }

        // 7. Add LOD validator based on detail level
        if (def.lod() > 0) {
            BuildingValidator lodValidator = LODValidatorRegistry.get(def.lod());
            if (lodValidator != null) {
                validators.add(lodValidator);
            }
        }

        return new ValidatorChain(true, validators.toArray(new BuildingValidator[0]));
    }

    /**
     * Create a minimal ValidatorChain (geometry only).
     * Useful for quick validation without full compliance checks.
     */
    public static ValidatorChain createMinimal() {
        return new ValidatorChain(true, new GeometryValidator());
    }

    /**
     * Create a full ValidatorChain with all available validators.
     * Useful for comprehensive validation regardless of building metadata.
     */
    public static ValidatorChain createFull(BuildingDefinition def) {
        List<BuildingValidator> validators = new ArrayList<>();

        validators.add(new GeometryValidator());
        validators.add(new HabitabilityValidator());

        // Add all profile validators that apply to any room type in the building
        Set<String> usedTypes = getUsedSpaceTypes(def);
        for (String profileName : ProfileValidatorRegistry.getProfileNames()) {
            validators.add(ProfileValidatorRegistry.get(profileName));
        }

        return new ValidatorChain(false, validators.toArray(new BuildingValidator[0]));
    }

    /**
     * Get all space types used in a building definition.
     */
    private static Set<String> getUsedSpaceTypes(BuildingDefinition def) {
        Set<String> types = new HashSet<>();
        for (StoreyDef storey : def.storeys()) {
            for (RoomDef room : storey.rooms()) {
                types.add(room.type().toUpperCase());
            }
        }
        return types;
    }
}
