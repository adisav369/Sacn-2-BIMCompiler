package com.bim.cobol.forge;

import com.bim.cobol.VerbContext;
import java.util.*;

/**
 * BARREL_VAULT — compute rib positions on a cylindrical vault.
 *
 * Circular arc cross-section: span + rise → radius of curvature →
 * theta per arc segment → (x, z) positions. Ribs spaced along length.
 *
 * Precedent: TrimWallsToRoofVerb tent model (linear slope) generalised
 * to circular arc. Same idea: a function maps position → height.
 * Tent model: roofZ = linear. Barrel vault: roofZ = circular.
 *
 * // Implementing GEOMETRY_FORGE_SRS.md §4 (barrel vault variant)
 */
public class BarrelVaultForge implements ForgeEngine {

    @Override
    public String pieceType() { return "BARREL_VAULT"; }

    @Override
    public ForgeResult compute(VerbContext ctx, Map<String, String> params) {
        Double span   = parseDouble(params, "span");
        Double length = parseDouble(params, "length");
        Integer ribs  = parseInt(params, "ribs");
        Double rise   = parseDouble(params, "rise");

        if (span == null || length == null || ribs == null || rise == null)
            return ForgeResult.fail(pieceType(),
                    "required params: span, length, ribs, rise");

        // Optional
        Integer arcSegments = parseInt(params, "arc_segments");
        if (arcSegments == null) arcSegments = 8;

        // Compliance checks
        List<String> compliance = new ArrayList<>();

        if (rise <= 0 || rise > span) {
            compliance.add("FAIL rise " + rise + "mm invalid (must be 0 < rise <= span)");
            return ForgeResult.fail(pieceType(),
                    "rise " + rise + "mm invalid (must be > 0 and <= span " + span + "mm)");
        }
        compliance.add("PASS rise " + rise + "mm in valid range");

        if (ribs < 2) {
            compliance.add("FAIL ribs " + ribs + " < 2");
            return ForgeResult.fail(pieceType(), "ribs must be >= 2");
        }
        compliance.add("PASS ribs " + ribs + " >= 2");

        // Radius of curvature from chord (span) and sagitta (rise)
        // R = (span²/4 + rise²) / (2 × rise)
        double R = (span * span / 4.0 + rise * rise) / (2.0 * rise);
        if (R <= 0) {
            compliance.add("FAIL R <= 0 (degenerate arc)");
            return ForgeResult.fail(pieceType(), "degenerate arc (R <= 0)");
        }
        compliance.add("PASS R=" + String.format("%.1f", R) + "mm > 0");

        double thetaMax = 2 * Math.asin(span / (2 * R));
        double arcLengthPerRib = R * thetaMax;

        List<GeometryRecord> records = new ArrayList<>();

        // Rib member records (one per rib)
        for (int i = 0; i < ribs; i++) {
            double y = length * i / (ribs - 1);

            Map<String, Double> ribFab = new LinkedHashMap<>();
            ribFab.put("radius_of_curvature", R);
            ribFab.put("arc_angle", thetaMax * 180.0 / Math.PI);
            ribFab.put("rib_index", (double) i);

            records.add(new GeometryRecord(
                    null, null, arcLengthPerRib, span, rise,
                    ribFab,
                    0, y / 1000.0, 0, 0));
        }

        // Arc segment position records (ribs × arc_segments)
        for (int i = 0; i < ribs; i++) {
            double y = length * i / (ribs - 1);
            for (int j = 0; j < arcSegments; j++) {
                double theta = -thetaMax / 2.0
                        + thetaMax * j / (arcSegments - 1);
                double x = R * Math.sin(theta);
                double z = rise - R * (1 - Math.cos(theta));

                Map<String, Double> segFab = new LinkedHashMap<>();
                segFab.put("rib_index", (double) i);
                segFab.put("arc_segment", (double) j);
                segFab.put("theta_degrees", theta * 180.0 / Math.PI);

                records.add(new GeometryRecord(
                        null, null, 0, 0, 0,
                        segFab,
                        x / 1000.0, y / 1000.0, z / 1000.0,
                        theta * 180.0 / Math.PI));
            }
        }

        return ForgeResult.ok(pieceType(),
                String.format("BARREL_VAULT %.0fmm span, %.0fmm rise, R=%.0fmm, "
                        + "%d ribs, arc=%.0fmm/rib",
                        span, rise, R, ribs, arcLengthPerRib),
                records, compliance);
    }

    private static Double parseDouble(Map<String, String> params, String key) {
        String v = params.get(key);
        if (v == null) return null;
        try { return Double.parseDouble(v); }
        catch (NumberFormatException e) { return null; }
    }

    private static Integer parseInt(Map<String, String> params, String key) {
        String v = params.get(key);
        if (v == null) return null;
        try { return Integer.parseInt(v); }
        catch (NumberFormatException e) { return null; }
    }
}
