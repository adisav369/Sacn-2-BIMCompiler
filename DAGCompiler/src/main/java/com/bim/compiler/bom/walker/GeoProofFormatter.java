/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.bom.walker;

import com.bim.orm.BIMLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * Formats {@link GeoProofRecord} lists into structured log output.
 * No compilation logic — consumes proof records and emits structured lines.
 *
 * <p>Two output modes:
 * <ol>
 *   <li>Per-element proof lines (INFO level) — one per LEAF</li>
 *   <li>Summary block (INFO level) — counts, round-trip, envelope violations</li>
 * </ol>
 *
 * Implementing S144_geo_white_box_logging.md §Task 3 — Witness: W-GEO-PROOF
 */
public class GeoProofFormatter {

    private static final String TAG = "GEO";

    /**
     * Emit per-element proof lines and summary to the BIMLogger.
     *
     * @param records proof records from the walker
     * @param buildingId building identifier for the summary header
     */
    public static void emit(List<GeoProofRecord> records, String buildingId) {
        if (records == null || records.isEmpty()) return;

        int lmpOk = 0, lmpFail = 0;
        int envOk = 0, envOvershoot = 0, envUnknown = 0;
        int roundTripCount = 0;
        List<String> overshootDetails = new ArrayList<>();

        for (GeoProofRecord r : records) {
            // Per-element proof line
            emitElementProof(r);

            // Tally
            if (r.lmpContained()) lmpOk++; else lmpFail++;

            if (r.parentAABB() == null || (r.parentAABB()[0] == 0 && r.parentAABB()[1] == 0 && r.parentAABB()[2] == 0)) {
                envUnknown++;
            } else if (r.envelopeContained()) {
                envOk++;
            } else {
                envOvershoot++;
                double mmOver = r.maxOvershootMm();
                String axis = r.overshootAxis();
                overshootDetails.add(String.format("%s(+%.0fmm %s)", r.productId(), mmOver, axis));
            }

            if (r.roundTrip()) roundTripCount++;
        }

        // Summary block
        BIMLogger.info(TAG, "PROOF SUMMARY: {} — {} elements", buildingId, records.size());
        BIMLogger.info(TAG, "  LMP: {} OK, {} FAIL", lmpOk, lmpFail);
        BIMLogger.info(TAG, "  ENVELOPE: {} OK, {} OVERSHOOT, {} UNKNOWN (no parent dims)",
                envOk, envOvershoot, envUnknown);
        BIMLogger.info(TAG, "  ROUND_TRIP: {}/{} (codec — no spatial reasoning applied)",
                roundTripCount, records.size());

        if (!overshootDetails.isEmpty()) {
            // Cap at 10 to avoid log flood
            int shown = Math.min(overshootDetails.size(), 10);
            BIMLogger.info(TAG, "  ENVELOPE_OVERSHOOT: {}{}",
                    String.join(", ", overshootDetails.subList(0, shown)),
                    overshootDetails.size() > shown
                            ? String.format(" ... and %d more", overshootDetails.size() - shown)
                            : "");
        }
    }

    private static void emitElementProof(GeoProofRecord r) {
        String lmpVerdict = r.lmpContained() ? "OK" : "FAIL";

        String envVerdict;
        if (r.parentAABB() == null || (r.parentAABB()[0] == 0 && r.parentAABB()[1] == 0 && r.parentAABB()[2] == 0)) {
            envVerdict = "UNKNOWN";
        } else if (r.envelopeContained()) {
            envVerdict = "OK";
        } else {
            double mmOver = r.maxOvershootMm();
            String axis = r.overshootAxis();
            envVerdict = String.format("OVERSHOOT(%s+%.0fmm vs parent %s=%.0fmm)",
                    axis, mmOver, axis,
                    axis.equals("X") ? r.parentAABB()[0] * 1000
                  : axis.equals("Y") ? r.parentAABB()[1] * 1000
                  : r.parentAABB()[2] * 1000);
        }

        BIMLogger.info(TAG, "PROOF {} guid={} chain={}",
                r.productId(), r.guid(), r.bomChain());
        BIMLogger.info(TAG, "  offset=({:.4f},{:.4f},{:.4f}) rot={:.2f} → output=({:.4f},{:.4f},{:.4f})",
                r.rotatedOffset()[0], r.rotatedOffset()[1], r.rotatedOffset()[2],
                r.cumRotation(),
                r.outputLBD()[0], r.outputLBD()[1], r.outputLBD()[2]);
        BIMLogger.info(TAG, "  LMP={} envelope={}", lmpVerdict, envVerdict);
    }
}
