package com.bim.eyes.proof.tier3;

import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;
import com.bim.eyes.proof.RelationalData.*;
import java.util.*;

/** P12: Every room (except utility/porch) has at least 1 door. */
public final class RoomHasDoorProof {
    private RoomHasDoorProof() {}

    private static final Set<String> EXEMPT_ROOMS = Set.of("porch", "anjung", "utility", "store", "corridor");

    public static List<ProofResult> prove(
            List<PlacementData> placements, Map<String, RoomData> rooms,
            Map<String, WallFaceData> wallFaces, List<ElementRule> rules) {
        List<ProofResult> results = new ArrayList<>();

        if (rooms.isEmpty()) {
            results.add(new ProofResult("P12_ROOM_HAS_DOOR", ProofResult.Status.SKIPPED,
                null, "no room data", 0));
            return results;
        }

        Map<String, Integer> doorsPerRoom = new HashMap<>();
        for (String roomName : rooms.keySet()) {
            doorsPerRoom.put(roomName, 0);
        }

        for (ElementRule rule : rules) {
            if (!"WALL".equals(rule.hostType()) || !"IfcDoor".equals(rule.ifcClass())) continue;
            WallFaceData wall = wallFaces.get(rule.hostRef());
            if (wall != null) {
                doorsPerRoom.merge(wall.roomName(), 1, Integer::sum);
            }
        }

        for (var entry : doorsPerRoom.entrySet()) {
            String roomName = entry.getKey();
            boolean exempt = EXEMPT_ROOMS.stream()
                .anyMatch(e -> roomName.toLowerCase().contains(e));
            if (exempt) continue;

            if (entry.getValue() >= 1) {
                results.add(new ProofResult("P12_ROOM_HAS_DOOR", ProofResult.Status.PROVEN,
                    null, "room %s has %d door(s)".formatted(roomName, entry.getValue()), 0));
            } else {
                results.add(new ProofResult("P12_ROOM_HAS_DOOR", ProofResult.Status.VIOLATED,
                    null, "room %s has no doors".formatted(roomName), 0));
            }
        }
        return results;
    }
}
