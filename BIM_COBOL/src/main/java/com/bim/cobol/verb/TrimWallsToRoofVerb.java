package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * TRIM WALLS TO ROOF — clip wall heights to the roof surface profile.
 * // Implementing BIM_COBOL.md §17.3 — Witness: W-TRIM-*
 *
 * <p>For each wall whose AABB overlaps a roof footprint in XY, estimates the
 * roof surface Z at the wall's centroid position using a tent model (ridge
 * along the longer AABB dimension, linear slope from ridge maxZ to eave minZ).
 *
 * <p>Walls exceeding the estimated roof surface Z are flagged for trimming.
 * The verb returns trim instructions; it does not modify geometry directly.
 *
 * <p>Handles flat roofs (dz &lt; 0.1m → surface = maxZ, no slope), pitched
 * gable roofs, hip roofs, and any mixed-slope roof represented as AABB.
 */
public class TrimWallsToRoofVerb implements Verb<TrimWallsToRoofVerb.TrimPayload> {

    /** Tolerance for considering a wall as exceeding the roof surface (50mm). */
    static final double TRIM_TOL_M = 0.050;

    @Override
    public String keyword() { return "TRIM WALLS TO ROOF"; }

    @Override
    public VerbResult<TrimPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        // The verb reads element placements from outputConn (or any DB with
        // elements_meta + elements_rtree tables).
        Connection conn = ctx.outputConn();
        if (conn == null) conn = ctx.componentConn();
        if (conn == null)
            return VerbResult.fail(keyword(),
                    "outputConn or componentConn required (elements_meta + elements_rtree)",
                    null);

        // Parse optional pitch argument: "pitch:0" → flat, "pitch:25" → 25°
        // If not provided, defaults to -1 (auto-detect via tent model).
        double pitchDeg = -1;
        for (String arg : args) {
            if (arg.startsWith("pitch:")) {
                pitchDeg = Double.parseDouble(arg.substring(6));
            }
        }

        // 1. Load all roof elements
        List<Element> roofs = new ArrayList<>();
        List<Element> walls = new ArrayList<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                 SELECT em.guid, em.ifc_class, em.element_name,
                        r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
                 FROM elements_meta em
                 JOIN elements_rtree r ON em.id = r.id
                 WHERE em.ifc_class IN ('IfcWall','IfcWallStandardCase','IfcCurtainWall',
                                        'IfcRoof','IfcRoofSlab')
                 """)) {
            while (rs.next()) {
                Element e = new Element(
                    rs.getString("guid"),
                    rs.getString("ifc_class"),
                    rs.getString("element_name"),
                    rs.getDouble("minX"), rs.getDouble("maxX"),
                    rs.getDouble("minY"), rs.getDouble("maxY"),
                    rs.getDouble("minZ"), rs.getDouble("maxZ")
                );
                if (e.ifcClass.contains("Roof")) {
                    roofs.add(e);
                } else {
                    walls.add(e);
                }
            }
        }

        if (roofs.isEmpty()) {
            return VerbResult.ok(keyword(), "no roof elements found — nothing to trim",
                    new TrimPayload(List.of(), 0, 0));
        }
        if (walls.isEmpty()) {
            return VerbResult.ok(keyword(), "no wall elements found — nothing to trim",
                    new TrimPayload(List.of(), 0, 0));
        }

        // 2. For each wall, check against each roof
        List<TrimEntry> entries = new ArrayList<>();
        int trimmedCount = 0;

        final double finalPitch = pitchDeg;
        for (Element wall : walls) {
            double worstExceedance = 0;
            double bestRoofZ = Double.MAX_VALUE;

            for (Element roof : roofs) {
                if (!overlapsXY(wall, roof)) continue;

                double roofZ = estimateRoofZ(roof, wallCx(wall), wallCy(wall), finalPitch);
                if (roofZ < bestRoofZ) bestRoofZ = roofZ;
                double exceedance = wall.maxZ - roofZ;
                if (exceedance > worstExceedance) worstExceedance = exceedance;
            }

            if (bestRoofZ == Double.MAX_VALUE) continue; // no overlapping roof

            boolean needsTrim = worstExceedance > TRIM_TOL_M;
            entries.add(new TrimEntry(
                wall.guid, wall.ifcClass, wall.elementName,
                wall.maxZ, bestRoofZ,
                needsTrim ? wall.maxZ - bestRoofZ : 0,
                needsTrim
            ));
            if (needsTrim) trimmedCount++;
        }

        String summary = "%d walls checked, %d need trimming (of %d under roof)"
                .formatted(walls.size(), trimmedCount, entries.size());

        return VerbResult.ok(keyword(), summary,
                new TrimPayload(entries, entries.size(), trimmedCount));
    }

    // ── Geometry helpers (tent-model roof surface estimation) ──────────

    /** Check if two elements overlap in XY (plan view). */
    static boolean overlapsXY(Element a, Element b) {
        return a.minX < b.maxX && a.maxX > b.minX
            && a.minY < b.maxY && a.maxY > b.minY;
    }

    /**
     * Estimate roof surface Z at position (x, y).
     *
     * <p>If pitchDeg == 0 → flat roof, surface = maxZ everywhere.
     * <br>If pitchDeg &gt; 0 → use actual pitch: eave = maxZ - tan(pitch)*halfSpan,
     *     ridge = maxZ, linear interpolation by distance from ridge.
     * <br>If pitchDeg &lt; 0 → auto-detect via tent model on AABB.
     */
    static double estimateRoofZ(Element roof, double x, double y, double pitchDeg) {
        // Explicit flat roof
        if (pitchDeg == 0) return roof.maxZ;

        double roofDz = roof.maxZ - roof.minZ;

        // Auto-detect: flat if very thin
        if (pitchDeg < 0 && roofDz < 0.1) return roof.maxZ;

        double roofDx = roof.maxX - roof.minX;
        double roofDy = roof.maxY - roof.minY;
        double roofCx = (roof.minX + roof.maxX) / 2;
        double roofCy = (roof.minY + roof.maxY) / 2;

        // Determine ridge height and eave height
        double ridgeZ, eaveZ;
        if (pitchDeg > 0) {
            // Explicit pitch: ridge at maxZ, eave computed from pitch geometry.
            // Rise = tan(pitch) × halfSpan. Eave = ridge - rise.
            ridgeZ = roof.maxZ;
            double halfSpan = (roofDx >= roofDy) ? roofDy / 2.0 : roofDx / 2.0;
            double rise = Math.tan(Math.toRadians(pitchDeg)) * halfSpan;
            eaveZ = ridgeZ - rise;
        } else {
            // Auto-detect: assume full AABB Z-range is the slope
            ridgeZ = roof.maxZ;
            eaveZ = roof.minZ;
        }

        double ridgeFraction;
        if (roofDx >= roofDy) {
            // Ridge runs along X — slope in Y direction
            double halfSpan = roofDy / 2.0;
            double distFromRidge = Math.abs(y - roofCy);
            ridgeFraction = halfSpan > 0 ? 1.0 - (distFromRidge / halfSpan) : 1.0;
        } else {
            // Ridge runs along Y — slope in X direction
            double halfSpan = roofDx / 2.0;
            double distFromRidge = Math.abs(x - roofCx);
            ridgeFraction = halfSpan > 0 ? 1.0 - (distFromRidge / halfSpan) : 1.0;
        }
        ridgeFraction = Math.max(0, Math.min(1, ridgeFraction));

        return eaveZ + (ridgeZ - eaveZ) * ridgeFraction;
    }

    static double wallCx(Element e) { return (e.minX + e.maxX) / 2; }
    static double wallCy(Element e) { return (e.minY + e.maxY) / 2; }

    // ── Payload types ─────────────────────────────────────────────────

    /** One wall's trim assessment. */
    public record TrimEntry(
        String wallGuid,
        String ifcClass,
        String elementName,
        double originalMaxZ,
        double roofSurfaceZ,
        double trimAmount,
        boolean needsTrim
    ) {}

    /** Full verb payload. */
    public record TrimPayload(
        List<TrimEntry> entries,
        int wallsUnderRoof,
        int wallsTrimmed
    ) {}

    /** Internal element holder (not exposed). */
    record Element(
        String guid, String ifcClass, String elementName,
        double minX, double maxX, double minY, double maxY,
        double minZ, double maxZ
    ) {}
}
