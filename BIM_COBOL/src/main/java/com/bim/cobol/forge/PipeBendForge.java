package com.bim.cobol.forge;

import com.bim.cobol.VerbContext;
import java.util.*;

/**
 * PIPE_BEND — compute pipe bend geometry from angle + radius + diameter.
 *
 * Arc geometry. Compliance: min bend radius per plumbing code.
 * Precedent: PipeRouter segment generation in RouteSprinklersVerb.
 *
 * // Implementing GEOMETRY_FORGE_SRS.md §4.3
 */
public class PipeBendForge implements ForgeEngine {

    @Override
    public String pieceType() { return "PIPE_BEND"; }

    @Override
    public ForgeResult compute(VerbContext ctx, Map<String, String> params) {
        Double angle    = parseDouble(params, "angle");
        Double radius   = parseDouble(params, "radius");
        Double diameter = parseDouble(params, "diameter");

        if (angle == null || radius == null || diameter == null)
            return ForgeResult.fail(pieceType(),
                    "required params: angle, radius, diameter");

        // Compliance checks
        List<String> compliance = new ArrayList<>();

        if (angle <= 0 || angle > 180) {
            compliance.add("FAIL angle " + angle + "° outside 0-180° range");
            return ForgeResult.fail(pieceType(),
                    "angle " + angle + "° outside valid range (0-180°)");
        }
        compliance.add("PASS angle " + angle + "° in 0-180° range");

        if (diameter <= 0) {
            compliance.add("FAIL diameter " + diameter + "mm <= 0");
            return ForgeResult.fail(pieceType(), "diameter must be > 0");
        }
        compliance.add("PASS diameter " + diameter + "mm > 0");

        double minRadius = 3 * diameter;
        if (radius < minRadius) {
            compliance.add("FAIL radius " + radius + "mm < 3×diameter ("
                    + minRadius + "mm)");
            return ForgeResult.fail(pieceType(),
                    "radius " + radius + "mm below minimum bend radius ("
                    + minRadius + "mm = 3×diameter)");
        }
        compliance.add("PASS radius " + radius + "mm >= 3×diameter ("
                + minRadius + "mm)");

        // Formulas
        double angleRad = angle * Math.PI / 180.0;
        double arcLength = radius * angleRad;
        double chordLength = 2 * radius * Math.sin(angleRad / 2);

        List<GeometryRecord> records = new ArrayList<>();

        // Pipe run (arc)
        Map<String, Double> pipeFab = new LinkedHashMap<>();
        pipeFab.put("bend_angle", angle);
        pipeFab.put("bend_radius", radius);
        pipeFab.put("chord_length", chordLength);
        records.add(new GeometryRecord(
                null, null, arcLength, diameter, diameter,
                pipeFab, 0, 0, 0, 0));

        // Fitting (elbow at bend point)
        Map<String, Double> fittingFab = new LinkedHashMap<>();
        fittingFab.put("angle", angle);
        records.add(new GeometryRecord(
                null, null, diameter, diameter, diameter,
                fittingFab, 0, 0, 0, angle));

        return ForgeResult.ok(pieceType(),
                String.format("PIPE_BEND %.0f° r=%.0fmm → arc %.1fmm",
                        angle, radius, arcLength),
                records, compliance);
    }

    private static Double parseDouble(Map<String, String> params, String key) {
        String v = params.get(key);
        if (v == null) return null;
        try { return Double.parseDouble(v); }
        catch (NumberFormatException e) { return null; }
    }
}
