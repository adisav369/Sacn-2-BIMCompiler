package com.bim.eyes.proof.tier3;

import com.bim.eyes.EyesConstants;
import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;
import com.bim.eyes.proof.RelationalData.RoomData;
import java.util.*;

/** P14: Sum of room floor areas matches slab area ±10%. */
public final class FloorAreaProof {
    private FloorAreaProof() {}

    public static List<ProofResult> prove(
            List<PlacementData> placements, Map<String, RoomData> rooms) {
        List<ProofResult> results = new ArrayList<>();

        if (rooms.isEmpty()) {
            results.add(new ProofResult("P14_FLOOR_AREA", ProofResult.Status.SKIPPED,
                null, "no room data", 0));
            return results;
        }

        double totalRoomArea = 0;
        for (RoomData room : rooms.values()) {
            totalRoomArea += (room.maxX() - room.minX()) * (room.maxY() - room.minY());
        }

        double totalSlabArea = 0;
        int slabCount = 0;
        for (PlacementData p : placements) {
            if ("IfcSlab".equals(p.ifcClass()) && p.minZ() < 0.5) {
                totalSlabArea += p.dx() * p.dy();
                slabCount++;
            }
        }

        if (slabCount == 0) {
            results.add(new ProofResult("P14_FLOOR_AREA", ProofResult.Status.SKIPPED,
                null, "no ground-level slabs found", 0));
            return results;
        }

        double ratio = totalSlabArea > 0
            ? Math.abs(totalRoomArea - totalSlabArea) / totalSlabArea
            : 1.0;

        if (ratio <= EyesConstants.AREA_CONSERVATION_TOL) {
            results.add(new ProofResult("P14_FLOOR_AREA", ProofResult.Status.PROVEN,
                null, "rooms=%.2fm² slabs=%.2fm² ratio=%.2f%%".formatted(
                    totalRoomArea, totalSlabArea, ratio * 100), ratio));
        } else {
            results.add(new ProofResult("P14_FLOOR_AREA", ProofResult.Status.VIOLATED,
                null, "rooms=%.2fm² slabs=%.2fm² ratio=%.1f%% (>%.0f%%)".formatted(
                    totalRoomArea, totalSlabArea, ratio * 100,
                    EyesConstants.AREA_CONSERVATION_TOL * 100), ratio));
        }
        return results;
    }
}
