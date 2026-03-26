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
 * <p>For each wall whose AABB overlaps a roof footprint in XY, computes the
 * roof underside Z at the wall's centroid position. Ridge runs along the
 * longer AABB dimension; slope interpolates linearly from eave (minZ) to
 * ridge (maxZ). Walls exceeding the roof surface are flagged for trimming.
 *
 * <p>No pitch parameter — the verb measures the roof AABB directly. Flat
 * roofs (minZ ~ maxZ) naturally produce slope ~ 0 and no trims. Barrel
 * vaults are approximated as gable (linear slope); true curved profiles
 * require mesh sampling (future, when component_library LODs are available).
 *
 * <p>The verb returns trim instructions; it does not modify geometry directly.
 *
 * <h3>Sub-verb family (AbstractSpatialVerb pattern — future)</h3>
 * <p>TRIM is one instance of the detect-act spatial pattern. The detect phase
 * (find walls under roof, measure exceedance) is shared; each sub-verb
 * implements a different action:
 * <pre>
 *   TRIM WALLS TO ROOF          — 4 words — detect + report (this class)
 *   TRIM WALLS TO ROOF CUT      — 5 words — modify wall maxZ in elements_meta
 *   TRIM WALLS TO ROOF FILL     — 5 words — insert filler between wall and roof
 *   TRIM WALLS TO ROOF CUT FILL — 6 words — cut then fill in one pass
 * </pre>
 * <p>VerbRegistry dispatches by longest keyword match. When sub-verbs are
 * implemented, extract detect() into AbstractTrimVerb base class; each sub-verb
 * overrides act(). Same pattern applies to JOIN, MEET, CONNECT families.
 *
 * <h3>Callout trigger path (future, see DocValidate.md §1.5)</h3>
 * <p>When a C_OrderLine placement changes (dx/dy/dz), the CalloutEngine
 * evaluates AD_Rule rows. A DIMENSIONAL rule matching the wall's product
 * category would fire this verb automatically. ASI on the order line carries
 * per-instance overrides (trim_action, trim_tolerance_mm). This replaces
 * the static .bimcobol script dispatch with data-driven reactive wiring.
 */
public class TrimWallsToRoofVerb implements Verb<TrimWallsToRoofVerb.TrimPayload> {

    /** Tolerance for considering a wall as exceeding the roof surface (50mm). */
    static final double TRIM_TOL_M = 0.050;

    @Override
    public String keyword() { return "TRIM WALLS TO ROOF"; }

    @Override
    public VerbResult<TrimPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        Connection conn = ctx.outputConn();
        if (conn == null) conn = ctx.componentConn();
        if (conn == null)
            return VerbResult.fail(keyword(),
                    "outputConn or componentConn required (elements_meta + elements_rtree)",
                    null);

        // 1. Load all roof and wall elements
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

        for (Element wall : walls) {
            double worstExceedance = 0;
            double bestRoofZ = Double.MAX_VALUE;
            double bestSlopeAngle = 0;
            String bestSlopeDir = "ALONG_Y";

            for (Element roof : roofs) {
                if (!overlapsXY(wall, roof)) continue;

                double roofZ = roofSurfaceZ(roof, wallCx(wall), wallCy(wall));
                if (roofZ < bestRoofZ) {
                    bestRoofZ = roofZ;
                    bestSlopeAngle = slopeAngle(roof);
                    bestSlopeDir = slopeDirection(roof);
                }
                double exceedance = wall.maxZ - roofZ;
                if (exceedance > worstExceedance) worstExceedance = exceedance;
            }

            if (bestRoofZ == Double.MAX_VALUE) continue; // no overlapping roof

            boolean needsTrim = worstExceedance > TRIM_TOL_M;
            String profileType = bestSlopeAngle < 1.0 ? "HORIZONTAL"
                    : "ANGLED";
            // AABB gives linear slope approximation. When mesh vertices become
            // available (component_library LODs), sample the actual roof underside
            // at the wall's face plane to compute the true curved trim profile.

            entries.add(new TrimEntry(
                wall.guid, wall.ifcClass, wall.elementName,
                wall.maxZ, bestRoofZ,
                needsTrim ? wall.maxZ - bestRoofZ : 0,
                needsTrim,
                bestSlopeAngle, bestSlopeDir, profileType
            ));
            if (needsTrim) trimmedCount++;
        }

        String summary = "%d walls checked, %d need trimming (of %d under roof)"
                .formatted(walls.size(), trimmedCount, entries.size());

        return VerbResult.ok(keyword(), summary,
                new TrimPayload(entries, entries.size(), trimmedCount));
    }

    // ── Geometry helpers ──────────────────────────────────────────────

    /** Check if two elements overlap in XY (plan view). */
    static boolean overlapsXY(Element a, Element b) {
        return a.minX < b.maxX && a.maxX > b.minX
            && a.minY < b.maxY && a.maxY > b.minY;
    }

    /**
     * Compute the roof underside Z at position (x, y).
     *
     * <p>Uses the roof AABB to determine slope direction (ridge along longer
     * axis) and interpolates Z from eave (minZ at perimeter) to crown
     * (maxZ at centre). The wall should not exceed this surface.
     *
     * <p>For flat roofs (minZ ~ maxZ), this naturally returns ~maxZ.
     * For gable/hip roofs, returns the linear slope at (x, y).
     * For barrel vaults, AABB gives a linear approximation — future mesh
     * sampling would follow the actual curve.
     */
    static double roofSurfaceZ(Element roof, double x, double y) {
        double ridgeZ = roof.maxZ;
        double eaveZ = roof.minZ;
        double roofDx = roof.maxX - roof.minX;
        double roofDy = roof.maxY - roof.minY;
        double roofCx = (roof.minX + roof.maxX) / 2;
        double roofCy = (roof.minY + roof.maxY) / 2;

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

    /** Slope angle in degrees, derived from AABB rise/run. */
    static double slopeAngle(Element roof) {
        double rise = roof.maxZ - roof.minZ;
        double roofDx = roof.maxX - roof.minX;
        double roofDy = roof.maxY - roof.minY;
        double run = (roofDx >= roofDy) ? roofDy / 2.0 : roofDx / 2.0;
        return run > 0 ? Math.toDegrees(Math.atan2(rise, run)) : 0;
    }

    /** Slope direction: perpendicular to ridge axis. */
    static String slopeDirection(Element roof) {
        double roofDx = roof.maxX - roof.minX;
        double roofDy = roof.maxY - roof.minY;
        return (roofDx >= roofDy) ? "ALONG_Y" : "ALONG_X";
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
        boolean needsTrim,
        double roofSlopeAngle,
        String roofSlopeDirection,
        String trimProfileType
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
