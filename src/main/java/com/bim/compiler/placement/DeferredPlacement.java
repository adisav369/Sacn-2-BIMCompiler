package com.bim.compiler.placement;

import com.bim.compiler.dsl.BuildingCompiler.RoomSpec;
import com.bim.compiler.dsl.BuildingCompiler.FixtureSpec;
import com.bim.compiler.dsl.BuildingCompiler.LightSpec;
import com.bim.compiler.dsl.BuildingCompiler.SprinklerSpec;
import com.bim.compiler.dsl.BuildingCompiler.ElectricalSpec;
import com.bim.compiler.dsl.BuildingCompiler.DiffuserSpec;

import java.util.*;
import java.util.function.Function;

/**
 * Phase 63: Deferred placement - executes placement after room bounds are finalized.
 *
 * Solves ROOM_RESIZED by capturing placement intent as a function of final bounds,
 * not a snapshot of preliminary bounds.
 *
 * Usage:
 *   // During parse phase - register intent
 *   placements.register("bathroom_1", PlacementType.FIXTURES,
 *       room -> fixturePlacer.placeBathroomFixtures(room.minX(), ...));
 *
 *   // After RoomSizingResolver - execute with final bounds
 *   for (RoomSpec room : finalRooms) {
 *       fixtures.addAll(placements.execute(room.name(), PlacementType.FIXTURES, room));
 *   }
 */
public class DeferredPlacement {

    public enum PlacementType {
        FIXTURES,
        LIGHTS,
        SPRINKLERS,
        ELECTRICAL,
        DIFFUSERS,
        FURNITURE
    }

    /**
     * Result of a deferred placement execution.
     */
    public record PlacementResult(
        List<FixtureSpec> fixtures,
        List<LightSpec> lights,
        List<SprinklerSpec> sprinklers,
        List<ElectricalSpec> electricals,
        List<DiffuserSpec> diffusers
    ) {
        public static PlacementResult empty() {
            return new PlacementResult(List.of(), List.of(), List.of(), List.of(), List.of());
        }

        public static PlacementResult fixtures(List<FixtureSpec> fixtures) {
            return new PlacementResult(fixtures, List.of(), List.of(), List.of(), List.of());
        }

        public static PlacementResult lights(List<LightSpec> lights) {
            return new PlacementResult(List.of(), lights, List.of(), List.of(), List.of());
        }

        public static PlacementResult sprinklers(List<SprinklerSpec> sprinklers) {
            return new PlacementResult(List.of(), List.of(), sprinklers, List.of(), List.of());
        }

        public static PlacementResult electricals(List<ElectricalSpec> electricals) {
            return new PlacementResult(List.of(), List.of(), List.of(), electricals, List.of());
        }

        public static PlacementResult diffusers(List<DiffuserSpec> diffusers) {
            return new PlacementResult(List.of(), List.of(), List.of(), List.of(), diffusers);
        }

        public PlacementResult merge(PlacementResult other) {
            List<FixtureSpec> f = new ArrayList<>(fixtures);
            f.addAll(other.fixtures);
            List<LightSpec> l = new ArrayList<>(lights);
            l.addAll(other.lights);
            List<SprinklerSpec> s = new ArrayList<>(sprinklers);
            s.addAll(other.sprinklers);
            List<ElectricalSpec> e = new ArrayList<>(electricals);
            e.addAll(other.electricals);
            List<DiffuserSpec> d = new ArrayList<>(diffusers);
            d.addAll(other.diffusers);
            return new PlacementResult(f, l, s, e, d);
        }
    }

    /**
     * A placement function that takes final room bounds and returns elements.
     */
    @FunctionalInterface
    public interface PlacementFunction {
        PlacementResult place(RoomSpec finalRoom) throws Exception;
    }

    // Storage: roomName -> list of placement functions
    private final Map<String, List<PlacementFunction>> pending = new HashMap<>();

    /**
     * Register a deferred placement for a room.
     */
    public void register(String roomName, PlacementFunction placer) {
        pending.computeIfAbsent(roomName, k -> new ArrayList<>()).add(placer);
    }

    /**
     * Execute all pending placements for a room with final bounds.
     */
    public PlacementResult execute(RoomSpec finalRoom) {
        List<PlacementFunction> placers = pending.get(finalRoom.name());
        if (placers == null || placers.isEmpty()) {
            return PlacementResult.empty();
        }

        PlacementResult result = PlacementResult.empty();
        for (PlacementFunction placer : placers) {
            try {
                result = result.merge(placer.place(finalRoom));
            } catch (Exception e) {
                System.err.printf("[DeferredPlacement] Error placing in room '%s': %s%n",
                                  finalRoom.name(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * Execute all pending placements for multiple rooms.
     */
    public PlacementResult executeAll(List<RoomSpec> finalRooms) {
        PlacementResult result = PlacementResult.empty();
        for (RoomSpec room : finalRooms) {
            result = result.merge(execute(room));
        }
        return result;
    }

    /**
     * Check if there are pending placements for a room.
     */
    public boolean hasPending(String roomName) {
        List<PlacementFunction> placers = pending.get(roomName);
        return placers != null && !placers.isEmpty();
    }

    /**
     * Clear all pending placements.
     */
    public void clear() {
        pending.clear();
    }

    /**
     * Get count of rooms with pending placements.
     */
    public int pendingCount() {
        return pending.size();
    }
}
