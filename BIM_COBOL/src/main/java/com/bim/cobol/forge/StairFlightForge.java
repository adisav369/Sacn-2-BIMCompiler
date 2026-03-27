package com.bim.cobol.forge;

import com.bim.cobol.VerbContext;
import java.util.*;

/**
 * STAIR_FLIGHT — compute stair geometry from storey height + step dimensions.
 *
 * Pythagoras + integer ceiling + Blondel's comfort formula (2R+G, 1675).
 * Precedent: StackFloorsVerb cumulative Z offset.
 *
 * // Implementing GEOMETRY_FORGE_SRS.md §4.2
 */
public class StairFlightForge implements ForgeEngine {

    @Override
    public String pieceType() { return "STAIR_FLIGHT"; }

    @Override
    public ForgeResult compute(VerbContext ctx, Map<String, String> params) {
        Double height = parseDouble(params, "height");
        Double tread  = parseDouble(params, "tread");
        Double riser  = parseDouble(params, "riser");
        Double width  = parseDouble(params, "width");

        if (height == null || tread == null || riser == null || width == null)
            return ForgeResult.fail(pieceType(),
                    "required params: height, tread, riser, width");

        // Compliance checks
        List<String> compliance = new ArrayList<>();

        if (riser < 150 || riser > 220) {
            compliance.add("FAIL riser " + riser + "mm outside 150-220mm range");
            return ForgeResult.fail(pieceType(),
                    "riser " + riser + "mm outside code range (150-220mm)");
        }
        compliance.add("PASS riser " + riser + "mm in 150-220mm range");

        if (tread < 220 || tread > 350) {
            compliance.add("FAIL tread " + tread + "mm outside 220-350mm range");
            return ForgeResult.fail(pieceType(),
                    "tread " + tread + "mm outside code range (220-350mm)");
        }
        compliance.add("PASS tread " + tread + "mm in 220-350mm range");

        // Blondel's formula: 2R + G must be 550-700mm
        double blondel = 2 * riser + tread;
        if (blondel < 550 || blondel > 700) {
            compliance.add("FAIL Blondel 2R+G=" + blondel + "mm outside 550-700mm");
            return ForgeResult.fail(pieceType(),
                    "Blondel 2R+G=" + blondel + "mm outside comfort range (550-700mm)");
        }
        compliance.add("PASS Blondel 2R+G=" + blondel + "mm in 550-700mm range");

        // Formulas
        int stepCount = (int) Math.ceil(height / riser);
        double actualRiser = height / stepCount;
        double totalRun = stepCount * tread;
        double stringerLength = Math.sqrt(totalRun * totalRun + height * height);
        double goingAngle = Math.atan2(height, totalRun) * 180.0 / Math.PI;

        if (goingAngle > 42) {
            compliance.add("FAIL going angle " + String.format("%.1f", goingAngle)
                    + "° exceeds 42°");
            return ForgeResult.fail(pieceType(),
                    "going angle " + String.format("%.1f", goingAngle) + "° exceeds 42° max");
        }
        compliance.add("PASS going angle " + String.format("%.1f", goingAngle) + "° <= 42°");

        List<GeometryRecord> records = new ArrayList<>();

        // Stringer record
        Map<String, Double> stringerFab = new LinkedHashMap<>();
        stringerFab.put("step_count", (double) stepCount);
        stringerFab.put("actual_riser", actualRiser);
        stringerFab.put("tread", tread);
        stringerFab.put("going_angle", goingAngle);

        // Stringer depth = assume 50mm standard (carrier, not a param)
        double stringerDepth = 50.0;
        records.add(new GeometryRecord(
                null, null, stringerLength, width, stringerDepth,
                stringerFab, 0, 0, 0, 0));

        // Landing at top
        if (stepCount > 0) {
            Map<String, Double> landingFab = new LinkedHashMap<>();
            landingFab.put("step_count", (double) stepCount);
            records.add(new GeometryRecord(
                    null, null, width, width, stringerDepth,
                    landingFab, 0, 0, height / 1000.0, 0));
        }

        return ForgeResult.ok(pieceType(),
                String.format("STAIR_FLIGHT %d steps, stringer %.0fmm, angle %.1f°",
                        stepCount, stringerLength, goingAngle),
                records, compliance);
    }

    private static Double parseDouble(Map<String, String> params, String key) {
        String v = params.get(key);
        if (v == null) return null;
        try { return Double.parseDouble(v); }
        catch (NumberFormatException e) { return null; }
    }
}
