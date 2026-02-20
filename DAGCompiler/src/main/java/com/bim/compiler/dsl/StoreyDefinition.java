package com.bim.compiler.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Storey definition from DSL.
 *
 * Example:
 *   STOREY "Ground" height:2.8m {
 *       BEDROOM "master" ...
 *       BATHROOM "bath1" ...
 *   }
 */
public record StoreyDefinition(
    String name,
    double height,       // floor-to-ceiling height in meters
    List<RoomDefinition> rooms
) {
    /**
     * Find room by name.
     */
    public Optional<RoomDefinition> findRoom(String name) {
        return rooms.stream()
            .filter(r -> r.name().equals(name))
            .findFirst();
    }

    /**
     * Get all room names.
     */
    public List<String> getRoomNames() {
        return rooms.stream()
            .map(RoomDefinition::name)
            .toList();
    }

    /**
     * Builder for parsing.
     */
    public static class Builder {
        private String name;
        private double height = Double.NaN;
        private final List<RoomDefinition> rooms = new ArrayList<>();

        public Builder name(String name) { this.name = name; return this; }
        public Builder height(double height) { this.height = height; return this; }
        public Builder addRoom(RoomDefinition room) { rooms.add(room); return this; }

        public StoreyDefinition build() {
            if (Double.isNaN(height))
                throw new IllegalStateException("Storey height must be set explicitly via .height()");
            return new StoreyDefinition(name, height, List.copyOf(rooms));
        }
    }
}
