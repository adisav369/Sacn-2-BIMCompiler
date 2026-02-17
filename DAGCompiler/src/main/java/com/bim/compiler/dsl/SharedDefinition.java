package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingDefinition.StoreyDef;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared spaces definition for multi-unit buildings.
 * Phase 46: Multi-unit residential support.
 *
 * Shared spaces include:
 * - Circulation: Corridors, lobbies, stairwells
 * - Services: Plant rooms, refuse areas
 * - Risers: Shared vertical service shafts
 *
 * DSL Syntax:
 *   SHARED {
 *       STOREY "Ground" level:0 height:2.8m {
 *           CORRIDOR "main_corridor" size:1.2x8m { ... }
 *           LOBBY "entry" size:3x3m { ... }
 *       }
 *       RISER "electrical_main" type:electrical at:lobby
 *       RISER "plumbing_main" type:plumbing at:corridor
 *   }
 */
public record SharedDefinition(
    List<StoreyDef> storeys,    // Shared storeys (circulation, plant rooms)
    List<RiserDef> risers       // Vertical service risers
) {
    /**
     * Empty shared definition (for buildings with no shared spaces).
     */
    public static final SharedDefinition EMPTY = new SharedDefinition(List.of(), List.of());

    /**
     * Create shared definition with storeys only.
     */
    public SharedDefinition(List<StoreyDef> storeys) {
        this(storeys, List.of());
    }

    /**
     * Get all rooms across all shared storeys.
     */
    public List<BuildingDefinition.RoomDef> getAllRooms() {
        List<BuildingDefinition.RoomDef> rooms = new ArrayList<>();
        for (StoreyDef storey : storeys) {
            rooms.addAll(storey.rooms());
        }
        return rooms;
    }

    /**
     * Get shared rooms at a specific level.
     */
    public List<BuildingDefinition.RoomDef> getRoomsAtLevel(int level) {
        for (StoreyDef storey : storeys) {
            if (storey.level() == level) {
                return storey.rooms();
            }
        }
        return List.of();
    }

    /**
     * Check if there are shared spaces at a level.
     */
    public boolean hasStoreyAtLevel(int level) {
        return storeys.stream().anyMatch(s -> s.level() == level);
    }

    /**
     * Get storey by level.
     */
    public StoreyDef getStoreyAtLevel(int level) {
        return storeys.stream()
            .filter(s -> s.level() == level)
            .findFirst()
            .orElse(null);
    }

    /**
     * Find a room by name in shared spaces.
     */
    public BuildingDefinition.RoomDef findRoom(String roomName) {
        for (StoreyDef storey : storeys) {
            for (BuildingDefinition.RoomDef room : storey.rooms()) {
                if (room.name().equals(roomName)) {
                    return room;
                }
            }
        }
        return null;
    }

    /**
     * Find a riser by name.
     */
    public RiserDef findRiser(String riserName) {
        return risers.stream()
            .filter(r -> r.name().equals(riserName))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get risers of a specific type.
     */
    public List<RiserDef> getRisersByType(RiserDef.RiserType type) {
        return risers.stream()
            .filter(r -> r.type() == type)
            .toList();
    }

    /**
     * Check if shared definition is empty (no shared spaces).
     */
    public boolean isEmpty() {
        return storeys.isEmpty() && risers.isEmpty();
    }

    /**
     * Get total shared area.
     */
    public double getTotalArea() {
        return getAllRooms().stream()
            .mapToDouble(r -> r.width() * r.depth())
            .sum();
    }

    /**
     * Builder for SharedDefinition.
     */
    public static class Builder {
        private List<StoreyDef> storeys = new ArrayList<>();
        private List<RiserDef> risers = new ArrayList<>();

        public Builder addStorey(StoreyDef storey) {
            this.storeys.add(storey);
            return this;
        }

        public Builder addRiser(RiserDef riser) {
            this.risers.add(riser);
            return this;
        }

        public SharedDefinition build() {
            if (storeys.isEmpty() && risers.isEmpty()) {
                return SharedDefinition.EMPTY;
            }
            return new SharedDefinition(storeys, risers);
        }
    }
}
