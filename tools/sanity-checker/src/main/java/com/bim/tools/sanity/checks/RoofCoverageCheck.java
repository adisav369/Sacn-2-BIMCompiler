package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.BoundingBox;
import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.List;

/**
 * Check 6: Roof Coverage
 * Verifies the roof covers the entire building footprint.
 */
public class RoofCoverageCheck implements SanityCheck {

    private static final double PASS_COVERAGE = 0.98;  // 98%
    private static final double WARN_COVERAGE = 0.90;  // 90%

    @Override
    public String getId() { return "roof_coverage"; }

    @Override
    public String getName() { return "Roof Coverage"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        List<Element> roofs = model.getRoofs();

        // For setback buildings (narrow tower on wide podium), use
        // top-storey walls only so the roof covers the topmost floor.
        BoundingBox buildingEnvelope = computeTopStoreyEnvelope(model);
        if (buildingEnvelope == null) {
            buildingEnvelope = model.getBuildingEnvelope();
        }

        if (buildingEnvelope == null) {
            return CheckResult.fail(getId(), getName())
                .summary("Cannot determine building footprint")
                .guidance("Building needs walls to establish footprint")
                .build();
        }

        if (roofs.isEmpty()) {
            return CheckResult.fail(getId(), getName())
                .summary("No roof found")
                .detail("Building has no IfcRoof or roof slab elements")
                .guidance("Add roof to cover building")
                .build();
        }

        // Compute roof envelope (union of all roof bboxes)
        BoundingBox roofEnvelope = null;
        for (Element roof : roofs) {
            if (roof.bbox() != null) {
                roofEnvelope = roofEnvelope == null ? roof.bbox() : roofEnvelope.union(roof.bbox());
            }
        }

        if (roofEnvelope == null) {
            return CheckResult.fail(getId(), getName())
                .summary("Roof has no geometry")
                .guidance("Roof elements need bounding boxes")
                .build();
        }

        // Calculate coverage (simplified: bbox intersection in XY)
        double buildingArea = buildingEnvelope.areaXY();
        double coverageArea = buildingEnvelope.intersectionAreaXY(roofEnvelope);
        double coverage = buildingArea > 0 ? coverageArea / buildingArea : 0;

        // Check roof is above walls
        double roofMinZ = roofEnvelope.minZ();
        double wallMaxZ = buildingEnvelope.maxZ();

        // Calculate overhang
        double overhangMinX = buildingEnvelope.minX() - roofEnvelope.minX();
        double overhangMaxX = roofEnvelope.maxX() - buildingEnvelope.maxX();
        double overhangMinY = buildingEnvelope.minY() - roofEnvelope.minY();
        double overhangMaxY = roofEnvelope.maxY() - buildingEnvelope.maxY();
        double minOverhang = Math.min(Math.min(overhangMinX, overhangMaxX),
                                      Math.min(overhangMinY, overhangMaxY));

        if (coverage >= PASS_COVERAGE) {
            CheckResult.Builder result = CheckResult.pass(getId(), getName())
                .summary(String.format("Roof covers building footprint (%.0f%% coverage)",
                    coverage * 100));

            if (minOverhang > 0) {
                result.detail(String.format("Roof overhang: %.0fmm minimum", minOverhang * 1000));
            }

            if (roofMinZ < wallMaxZ) {
                result.detail(String.format("Note: Roof starts at Z=%.2fm, walls extend to Z=%.2fm",
                    roofMinZ, wallMaxZ));
            }

            result.data("coverage", coverage);
            result.data("overhang", minOverhang);
            return result.build();
        }

        if (coverage >= WARN_COVERAGE) {
            return CheckResult.warning(getId(), getName())
                .summary(String.format("Roof covers %.0f%% of building footprint", coverage * 100))
                .detail(String.format("Building footprint: %.1fm × %.1fm",
                    buildingEnvelope.width(), buildingEnvelope.depth()))
                .detail(String.format("Roof footprint: %.1fm × %.1fm",
                    roofEnvelope.width(), roofEnvelope.depth()))
                .detail(findUncoveredCorners(buildingEnvelope, roofEnvelope))
                .guidance("Extend roof or check for modeling error")
                .data("coverage", coverage)
                .build();
        }

        return CheckResult.fail(getId(), getName())
            .summary(String.format("Roof covers only %.0f%% of building", coverage * 100))
            .detail(String.format("Building footprint: %.1fm × %.1fm",
                buildingEnvelope.width(), buildingEnvelope.depth()))
            .detail(String.format("Roof footprint: %.1fm × %.1fm",
                roofEnvelope.width(), roofEnvelope.depth()))
            .detail(findUncoveredCorners(buildingEnvelope, roofEnvelope))
            .guidance("Extend roof to cover entire building footprint")
            .data("coverage", coverage)
            .build();
    }

    /**
     * For multi-storey buildings, compute envelope from top-storey walls only.
     * This handles setback towers where the roof covers a narrower top floor.
     */
    private BoundingBox computeTopStoreyEnvelope(SanityModel model) {
        List<String> storeys = model.getStoreyNames();
        if (storeys.size() <= 2) return null; // single-storey or 2-storey: use full envelope

        try {
            // Find the highest non-Roof storey by max Z
            // Include IfcPlate (cladding-as-wall) alongside traditional IfcWall
            String sql = """
                SELECT m.storey, MAX(r.maxZ) as topZ
                FROM elements_meta m
                JOIN elements_rtree r ON m.id = r.id
                WHERE (m.ifc_class IN ('IfcWall', 'IfcWallStandardCase')
                       OR (m.ifc_class = 'IfcPlate' AND m.guid LIKE '%WALL%' AND m.guid LIKE '%CLADDING%'))
                  AND m.storey IS NOT NULL AND m.storey != ''
                GROUP BY m.storey
                ORDER BY topZ DESC
                LIMIT 1
                """;
            String topStorey = null;
            try (Statement stmt = model.getConnection().createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) topStorey = rs.getString("storey");
            }
            if (topStorey == null) return null;

            // Compute envelope from walls of that storey
            String wallSql = """
                SELECT MIN(r.minX) as mnX, MIN(r.minY) as mnY, MIN(r.minZ) as mnZ,
                       MAX(r.maxX) as mxX, MAX(r.maxY) as mxY, MAX(r.maxZ) as mxZ
                FROM elements_meta m
                JOIN elements_rtree r ON m.id = r.id
                WHERE (m.ifc_class IN ('IfcWall', 'IfcWallStandardCase')
                       OR (m.ifc_class = 'IfcPlate' AND m.guid LIKE '%WALL%' AND m.guid LIKE '%CLADDING%'))
                  AND m.storey = ?
                """;
            try (PreparedStatement ps = model.getConnection().prepareStatement(wallSql)) {
                ps.setString(1, topStorey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getObject("mnX") != null) {
                        return new BoundingBox(
                            rs.getDouble("mnX"), rs.getDouble("mnY"), rs.getDouble("mnZ"),
                            rs.getDouble("mxX"), rs.getDouble("mxY"), rs.getDouble("mxZ"));
                    }
                }
            }
        } catch (SQLException e) {
            // Fall back to full envelope
        }
        return null;
    }

    private String findUncoveredCorners(BoundingBox building, BoundingBox roof) {
        StringBuilder sb = new StringBuilder("Exposed areas: ");

        if (roof.minX() > building.minX()) {
            sb.append(String.format("west (%.1fm gap), ", roof.minX() - building.minX()));
        }
        if (roof.maxX() < building.maxX()) {
            sb.append(String.format("east (%.1fm gap), ", building.maxX() - roof.maxX()));
        }
        if (roof.minY() > building.minY()) {
            sb.append(String.format("south (%.1fm gap), ", roof.minY() - building.minY()));
        }
        if (roof.maxY() < building.maxY()) {
            sb.append(String.format("north (%.1fm gap), ", building.maxY() - roof.maxY()));
        }

        String result = sb.toString();
        if (result.endsWith(", ")) {
            result = result.substring(0, result.length() - 2);
        }
        return result.equals("Exposed areas: ") ? "Roof appears offset from building" : result;
    }
}
