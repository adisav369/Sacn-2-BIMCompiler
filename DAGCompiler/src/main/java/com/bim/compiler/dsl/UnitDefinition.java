package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingDefinition.StoreyDef;
import java.util.ArrayList;
import java.util.List;

/**
 * Dwelling unit definition within a multi-unit building.
 * Phase 46: Multi-unit residential support.
 *
 * A unit is an independent dwelling with:
 * - Own rooms across one or more storeys
 * - Independent entry (DIRECT or SHARED)
 * - Independent utility metering
 * - Fire separation from other units (party walls)
 *
 * DSL Syntax:
 *   UNIT "A" type:RESIDENTIAL entry:DIRECT {
 *       STOREY "Ground" level:0 height:2.8m {
 *           LIVING "living_a" size:4x5m { ... }
 *       }
 *       METER electrical at:living_a
 *       METER water at:bath_a
 *   }
 */
public record UnitDefinition(
    String name,                    // Unit identifier (A, B, 1, 2, etc.)
    UnitType type,                  // RESIDENTIAL or COMMERCIAL
    EntryType entry,                // DIRECT or SHARED
    List<StoreyDef> storeys,        // Storeys belonging to this unit
    List<MeterDef> meters,          // Utility metering points
    List<String> adjacentUnits      // Units sharing a party wall (for validation)
) {
    /**
     * Simple constructor for parsing.
     */
    public UnitDefinition(String name, UnitType type, EntryType entry) {
        this(name, type, entry, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Constructor without adjacentUnits (computed during compilation).
     */
    public UnitDefinition(String name, UnitType type, EntryType entry,
                          List<StoreyDef> storeys, List<MeterDef> meters) {
        this(name, type, entry, storeys, meters, new ArrayList<>());
    }

    /**
     * Get all rooms across all storeys in this unit.
     */
    public List<BuildingDefinition.RoomDef> getAllRooms() {
        List<BuildingDefinition.RoomDef> rooms = new ArrayList<>();
        for (StoreyDef storey : storeys) {
            rooms.addAll(storey.rooms());
        }
        return rooms;
    }

    /**
     * Get rooms for a specific storey level.
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
     * Check if unit has rooms at a specific level.
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
     * Find room by name across all storeys.
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
     * Check if a room belongs to this unit.
     */
    public boolean containsRoom(String roomName) {
        return findRoom(roomName) != null;
    }

    /**
     * Get electrical meter location (for distribution board placement).
     */
    public MeterDef getElectricalMeter() {
        return meters.stream()
            .filter(m -> m.type() == MeterDef.MeterType.ELECTRICAL)
            .findFirst()
            .orElse(null);
    }

    /**
     * Get water meter location.
     */
    public MeterDef getWaterMeter() {
        return meters.stream()
            .filter(m -> m.type() == MeterDef.MeterType.WATER)
            .findFirst()
            .orElse(null);
    }

    /**
     * Get total floor area across all storeys.
     */
    public double getTotalArea() {
        return getAllRooms().stream()
            .mapToDouble(r -> r.width() * r.depth())
            .sum();
    }

    /**
     * Get count of habitable rooms (bedrooms + living).
     */
    public int getHabitableRoomCount() {
        return (int) getAllRooms().stream()
            .filter(r -> isHabitableType(r.type()))
            .count();
    }

    private boolean isHabitableType(String type) {
        return switch (type.toUpperCase()) {
            case "BEDROOM", "MASTER_BEDROOM", "LIVING", "STUDY", "OFFICE" -> true;
            default -> false;
        };
    }

    /**
     * Builder for UnitDefinition.
     */
    public static class Builder {
        private String name;
        private UnitType type = UnitType.RESIDENTIAL;
        private EntryType entry = EntryType.DIRECT;
        private List<StoreyDef> storeys = new ArrayList<>();
        private List<MeterDef> meters = new ArrayList<>();
        private List<String> adjacentUnits = new ArrayList<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(UnitType type) {
            this.type = type;
            return this;
        }

        public Builder entry(EntryType entry) {
            this.entry = entry;
            return this;
        }

        public Builder addStorey(StoreyDef storey) {
            this.storeys.add(storey);
            return this;
        }

        public Builder addMeter(MeterDef meter) {
            this.meters.add(meter);
            return this;
        }

        public Builder addAdjacentUnit(String unitName) {
            this.adjacentUnits.add(unitName);
            return this;
        }

        public UnitDefinition build() {
            return new UnitDefinition(name, type, entry, storeys, meters, adjacentUnits);
        }
    }
}
