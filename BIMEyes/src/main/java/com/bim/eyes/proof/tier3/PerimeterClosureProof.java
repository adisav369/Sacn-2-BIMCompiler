package com.bim.eyes.proof.tier3;

import com.bim.eyes.proof.ProofResult;
import com.bim.eyes.proof.RelationalData.WallFaceData;
import java.util.*;

/** P10b: Exterior wall segments form a closed polygon (2D XY). */
public final class PerimeterClosureProof {
    private PerimeterClosureProof() {}

    public static List<ProofResult> prove(Map<String, WallFaceData> wallFaces) {
        List<ProofResult> results = new ArrayList<>();
        List<WallFaceData> exterior = wallFaces.values().stream()
            .filter(WallFaceData::isExterior).toList();

        if (exterior.isEmpty()) {
            results.add(new ProofResult("P10_PERIMETER_CLOSURE", ProofResult.Status.SKIPPED,
                null, "no exterior walls found", 0));
            return results;
        }

        Map<String, Integer> vertexDegree = new HashMap<>();
        int edgeCount = 0;

        for (WallFaceData w : exterior) {
            String start, end;
            if (w.runsNS()) {
                start = coordKey(w.wallX(), w.minY());
                end = coordKey(w.wallX(), w.maxY());
            } else {
                start = coordKey(w.minX(), w.wallY());
                end = coordKey(w.maxX(), w.wallY());
            }
            vertexDegree.merge(start, 1, Integer::sum);
            vertexDegree.merge(end, 1, Integer::sum);
            edgeCount++;
        }

        long oddDegreeVertices = vertexDegree.values().stream()
            .filter(d -> d % 2 != 0).count();

        if (oddDegreeVertices == 0 && edgeCount >= 4) {
            results.add(new ProofResult("P10_PERIMETER_CLOSURE", ProofResult.Status.PROVEN,
                null, "%d exterior walls, %d vertices, all even degree".formatted(
                    edgeCount, vertexDegree.size()), 0));
        } else if (edgeCount < 4) {
            results.add(new ProofResult("P10_PERIMETER_CLOSURE", ProofResult.Status.VIOLATED,
                null, "only %d exterior walls (need >= 4 for closure)".formatted(edgeCount),
                edgeCount));
        } else {
            results.add(new ProofResult("P10_PERIMETER_CLOSURE", ProofResult.Status.VIOLATED,
                null, "%d odd-degree vertices (gaps in perimeter)".formatted(oddDegreeVertices),
                oddDegreeVertices));
        }
        return results;
    }

    private static String coordKey(double x, double y) {
        return "%.3f,%.3f".formatted(x, y);
    }
}
