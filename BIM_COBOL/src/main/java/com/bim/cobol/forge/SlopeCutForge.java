package com.bim.cobol.forge;

import com.bim.cobol.VerbContext;
import java.util.*;

/**
 * SLOPE_CUT — compute a member cut to a pitch angle.
 *
 * Rafter, hip, valley, truss web — any member where length and cut
 * angles derive from pitch + span. Pure trigonometry.
 *
 * Precedent: TrimWallsToRoofVerb.slopeAngle() uses atan2(rise, run).
 * This is the inverse: given angle, compute length and cuts.
 *
 * // Implementing GEOMETRY_FORGE_SRS.md §4.1
 */
public class SlopeCutForge implements ForgeEngine {

    @Override
    public String pieceType() { return "SLOPE_CUT"; }

    @Override
    public ForgeResult compute(VerbContext ctx, Map<String, String> params) {
        // Required params
        Double pitch = parseDouble(params, "pitch");
        Double span  = parseDouble(params, "span");
        Double width = parseDouble(params, "width");
        Double depth = parseDouble(params, "depth");

        if (pitch == null || span == null || width == null || depth == null)
            return ForgeResult.fail(pieceType(),
                    "required params: pitch, span, width, depth");

        // Compliance checks
        List<String> compliance = new ArrayList<>();
        if (pitch < 5 || pitch > 60) {
            compliance.add("FAIL pitch " + pitch + "° outside 5-60° range");
            return ForgeResult.fail(pieceType(),
                    "pitch " + pitch + "° outside practical range (5-60°)");
        }
        compliance.add("PASS pitch " + pitch + "° in 5-60° range");

        if (span > 12000) {
            compliance.add("FAIL span " + span + "mm exceeds 12000mm limit");
            return ForgeResult.fail(pieceType(),
                    "span " + span + "mm exceeds unsupported timber span limit");
        }
        compliance.add("PASS span " + span + "mm <= 12000mm");

        // Formulas
        double pitchRad = pitch * Math.PI / 180.0;
        double length = span / Math.cos(pitchRad);
        double cutAngleTop = 90.0 - pitch;
        double cutAngleBottom = pitch;

        // Birdsmouth: optional override, default 1/3 seat cut
        Double birdsmouthOverride = parseDouble(params, "birdsmouth");
        double birdsmouthDepth = (birdsmouthOverride != null)
                ? birdsmouthOverride : depth * 0.33;

        if (length <= 0) {
            compliance.add("FAIL computed length <= 0");
            return ForgeResult.fail(pieceType(), "computed length <= 0");
        }
        compliance.add("PASS length " + String.format("%.1f", length) + "mm > 0");

        Map<String, Double> fabrication = new LinkedHashMap<>();
        fabrication.put("cut_angle_top", cutAngleTop);
        fabrication.put("cut_angle_bottom", cutAngleBottom);
        fabrication.put("birdsmouth_depth", birdsmouthDepth);
        fabrication.put("pitch_degrees", pitch);

        GeometryRecord rec = new GeometryRecord(
                null, null, length, width, depth,
                fabrication, 0, 0, 0, 0);

        return ForgeResult.ok(pieceType(),
                String.format("SLOPE_CUT %.0f° %.0fmm span → %.1fmm length",
                        pitch, span, length),
                List.of(rec), compliance);
    }

    private static Double parseDouble(Map<String, String> params, String key) {
        String v = params.get(key);
        if (v == null) return null;
        try { return Double.parseDouble(v); }
        catch (NumberFormatException e) { return null; }
    }
}
