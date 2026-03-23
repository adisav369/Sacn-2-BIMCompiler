package com.bim.eyes.proof.tier3;

import com.bim.eyes.EyesConstants;
import com.bim.eyes.proof.ProofResult;
import com.bim.eyes.proof.RelationalData.*;
import java.util.*;

/** P11: Wall faces cover room boundaries. */
public final class WallCoverageProof {
    private WallCoverageProof() {}

    public static List<ProofResult> prove(
            Map<String, WallFaceData> wallFaces, Map<String, RoomData> rooms) {
        List<ProofResult> results = new ArrayList<>();

        if (rooms.isEmpty() || wallFaces.isEmpty()) {
            results.add(new ProofResult("P11_WALL_COVERAGE", ProofResult.Status.SKIPPED,
                null, "no room/wall data", 0));
            return results;
        }

        int checkedFaces = 0;

        for (var roomEntry : rooms.entrySet()) {
            RoomData room = roomEntry.getValue();
            for (WallFaceData wall : wallFaces.values()) {
                if (!wall.roomName().equals(roomEntry.getKey())) continue;
                checkedFaces++;
                double wallLength = wall.runsNS() ? (wall.maxY() - wall.minY()) : (wall.maxX() - wall.minX());
                double expectedLength = wall.runsNS()
                    ? (room.maxY() - room.minY())
                    : (room.maxX() - room.minX());

                String faceId = "%s_%s".formatted(roomEntry.getKey(), wall.face());
                double gap = Math.abs(wallLength - expectedLength);

                if (gap > EyesConstants.COVERAGE_TOLERANCE_M + EyesConstants.CONTAINMENT_TOLERANCE_M) {
                    results.add(new ProofResult("P11_WALL_COVERAGE", ProofResult.Status.VIOLATED,
                        faceId, "room %s face %s: wall=%.3f expected=%.3f".formatted(
                            roomEntry.getKey(), wall.face(), wallLength, expectedLength),
                        gap));
                } else {
                    results.add(new ProofResult("P11_WALL_COVERAGE", ProofResult.Status.PROVEN,
                        faceId, "room %s face %s: wall=%.3f covers expected=%.3f".formatted(
                            roomEntry.getKey(), wall.face(), wallLength, expectedLength),
                        gap));
                }
            }
        }

        if (checkedFaces == 0) {
            results.add(new ProofResult("P11_WALL_COVERAGE", ProofResult.Status.SKIPPED,
                null, "no wall-to-room face mappings found", 0));
        }
        return results;
    }
}
