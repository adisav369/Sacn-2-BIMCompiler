package com.bim.cobol.forge;

import com.bim.cobol.VerbContext;
import java.util.*;

/**
 * DOME_SECTION — compute panel positions on a spherical dome.
 *
 * Spherical coordinates: phi (latitude) x theta (longitude) → (x, y, z).
 * Each ring is a horizontal circle; panels are curved quads approximated
 * as flat panels (same principle as ShipYard: curvature is in placement,
 * panels are flat library LODs).
 *
 * Precedent: TileGrid flat panel fill → DomeSectionForge spherical fill.
 *
 * // Implementing GEOMETRY_FORGE_SRS.md §4 (dome variant)
 */
public class DomeSectionForge implements ForgeEngine {

    @Override
    public String pieceType() { return "DOME_SECTION"; }

    @Override
    public ForgeResult compute(VerbContext ctx, Map<String, String> params) {
        Double radius   = parseDouble(params, "radius");
        Integer rings   = parseInt(params, "rings");
        Integer segments = parseInt(params, "segments");
        Double baseZ    = parseDouble(params, "base_z");

        if (radius == null || rings == null || segments == null || baseZ == null)
            return ForgeResult.fail(pieceType(),
                    "required params: radius, rings, segments, base_z");

        // Compliance checks
        List<String> compliance = new ArrayList<>();

        if (radius <= 0) {
            compliance.add("FAIL radius " + radius + "mm <= 0");
            return ForgeResult.fail(pieceType(), "radius must be > 0");
        }
        compliance.add("PASS radius " + radius + "mm > 0");

        if (rings < 2 || rings > 50) {
            compliance.add("FAIL rings " + rings + " outside 2-50 range");
            return ForgeResult.fail(pieceType(),
                    "rings " + rings + " outside valid range (2-50)");
        }
        compliance.add("PASS rings " + rings + " in 2-50 range");

        if (segments < 4 || segments > 64) {
            compliance.add("FAIL segments " + segments + " outside 4-64 range");
            return ForgeResult.fail(pieceType(),
                    "segments " + segments + " outside valid range (4-64)");
        }
        compliance.add("PASS segments " + segments + " in 4-64 range");

        List<GeometryRecord> records = new ArrayList<>();
        double panelHeight = radius * (Math.PI / 2.0) / (rings + 1);

        for (int i = 0; i < rings; i++) {
            double phi = (Math.PI / 2.0) * (i + 1) / (rings + 1);
            double ringRadius = radius * Math.sin(phi);
            double ringZ = baseZ + radius * (1 - Math.cos(phi));
            double panelWidth = 2 * ringRadius * Math.sin(Math.PI / segments);

            // Check practical minimum panel width
            if (panelWidth < 100) {
                compliance.add("FAIL panel_width " + String.format("%.1f", panelWidth)
                        + "mm < 100mm at ring " + i);
                return ForgeResult.fail(pieceType(),
                        "panel_width " + String.format("%.1f", panelWidth)
                        + "mm below 100mm practical minimum at ring " + i);
            }

            for (int j = 0; j < segments; j++) {
                double theta = 2 * Math.PI * j / segments;
                double x = ringRadius * Math.cos(theta);
                double y = ringRadius * Math.sin(theta);

                Map<String, Double> fabrication = new LinkedHashMap<>();
                fabrication.put("ring_index", (double) i);
                fabrication.put("segment_index", (double) j);
                fabrication.put("phi_degrees", phi * 180.0 / Math.PI);
                fabrication.put("theta_degrees", theta * 180.0 / Math.PI);

                records.add(new GeometryRecord(
                        null, null, panelWidth, panelWidth, panelHeight,
                        fabrication,
                        x / 1000.0, y / 1000.0, ringZ / 1000.0,
                        theta * 180.0 / Math.PI));
            }
        }

        compliance.add("PASS panel_width >= 100mm all rings");

        return ForgeResult.ok(pieceType(),
                String.format("DOME_SECTION r=%.0fmm %d rings × %d segments = %d panels",
                        radius, rings, segments, records.size()),
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
