package com.bim.eyes.proof.tier1;

import com.bim.eyes.EyesConstants;
import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;
import com.bim.eyes.shape.ShapeArchetype;
import com.bim.eyes.shape.ScaleBand;
import com.bim.eyes.shape.ShapeClassifier;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** P04: Element Z range falls within expected storey Z band. */
public final class StoreyZBandProof {
    private StoreyZBandProof() {}

    private static final double DEFAULT_GROUND_FLOOR_Z = 0.0;
    private static final double DEFAULT_STOREY_HEIGHT = 3.5;
    private static final double STOREY_Z_TOLERANCE = 0.5;

    public static ProofResult prove(PlacementData p) {
        double floorZ, ceilingZ;
        String storey = p.storey() != null ? p.storey().toLowerCase().trim() : "";

        int levelNum = -1;
        Matcher m = Pattern.compile("level\\s*(\\d+)").matcher(storey);
        if (m.find()) {
            levelNum = Integer.parseInt(m.group(1));
        }

        if (storey.contains("fdn") || storey.contains("foundation")) {
            floorZ = -5.0;
            ceilingZ = DEFAULT_STOREY_HEIGHT;
        } else if (storey.contains("ground") || levelNum == 1 || storey.contains("0")
                || storey.isEmpty()) {
            floorZ = DEFAULT_GROUND_FLOOR_Z;
            ceilingZ = DEFAULT_STOREY_HEIGHT;
        } else if (storey.contains("first") || levelNum == 2 || storey.contains("upper")) {
            floorZ = DEFAULT_STOREY_HEIGHT;
            ceilingZ = DEFAULT_STOREY_HEIGHT * 2;
        } else if (storey.contains("second") || levelNum == 3) {
            floorZ = DEFAULT_STOREY_HEIGHT * 2;
            ceilingZ = DEFAULT_STOREY_HEIGHT * 3;
        } else if (storey.contains("roof") || storey.contains("top")) {
            floorZ = 0;
            ceilingZ = DEFAULT_STOREY_HEIGHT * 3;
        } else {
            floorZ = DEFAULT_GROUND_FLOOR_Z;
            ceilingZ = DEFAULT_STOREY_HEIGHT * 3;
        }

        // CP-4 §4b: Z-band extensions based on archetype
        double wMm = (p.maxX() - p.minX()) * 1000;
        double dMm = (p.maxY() - p.minY()) * 1000;
        double hMm = (p.maxZ() - p.minZ()) * 1000;
        ShapeArchetype arch = ShapeClassifier.classifyArchetype(wMm, dMm, hMm);
        ScaleBand band = ShapeClassifier.classifyScaleBand(wMm, dMm, hMm);

        if (band == ScaleBand.ARCHITECTURAL && hMm > wMm * 0.8) {
            ceilingZ = Math.max(ceilingZ, DEFAULT_STOREY_HEIGHT * 3);
        }

        if (arch == ShapeArchetype.PLANAR && band == ScaleBand.ARCHITECTURAL) {
            floorZ -= DEFAULT_STOREY_HEIGHT;
            ceilingZ += DEFAULT_STOREY_HEIGHT;
        }

        if (arch == ShapeArchetype.ELONGATED && band != ScaleBand.ARCHITECTURAL) {
            floorZ = Math.min(floorZ, -2.0);
            ceilingZ += DEFAULT_STOREY_HEIGHT;
        }

        double elementHeight = p.maxZ() - p.minZ();
        if (elementHeight > DEFAULT_STOREY_HEIGHT * 0.9) {
            ceilingZ += DEFAULT_STOREY_HEIGHT;
        }

        if (p.minZ() >= floorZ - STOREY_Z_TOLERANCE
                && p.maxZ() <= ceilingZ + STOREY_Z_TOLERANCE) {
            return new ProofResult("P04_STOREY_Z_BAND", ProofResult.Status.PROVEN,
                p.guid(), "Z[%.3f,%.3f] within [%.1f,%.1f]±%.1f".formatted(
                    p.minZ(), p.maxZ(), floorZ, ceilingZ, STOREY_Z_TOLERANCE),
                p.maxZ() - p.minZ());
        }
        return new ProofResult("P04_STOREY_Z_BAND", ProofResult.Status.VIOLATED,
            p.guid(), "Z[%.3f,%.3f] outside [%.1f,%.1f]±%.1f".formatted(
                p.minZ(), p.maxZ(), floorZ, ceilingZ, STOREY_Z_TOLERANCE),
            Math.max(p.maxZ() - ceilingZ, floorZ - p.minZ()));
    }
}
