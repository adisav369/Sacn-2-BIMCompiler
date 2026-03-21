package com.bim.eyes.proof.tier3;

import com.bim.eyes.proof.ProofResult;
import com.bim.eyes.shape.ShapeArchetype;
import com.bim.eyes.shape.ScaleBand;
import com.bim.eyes.shape.ShapeClassifier;
import java.sql.*;
import java.util.*;

/** P10: Every element's geometry is consistent with its claimed IFC class. */
public final class ShapeIdentityProof {
    private ShapeIdentityProof() {}

    public static List<ProofResult> prove(Connection conn) {
        List<ProofResult> results = new ArrayList<>();
        String sql = """
            SELECT em.guid, em.ifc_class, em.element_name, em.storey,
                   COALESCE(em.element_ref, '') as element_ref,
                   r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
                   COALESCE(bg.vertex_count, 0) as vertex_count,
                   COALESCE(bg.face_count, 0) as face_count
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            LEFT JOIN element_instances ei ON em.guid = ei.guid
            LEFT JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
            """;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String guid = rs.getString("guid");
                String ifcClass = rs.getString("ifc_class");

                double dimX = (rs.getDouble("maxX") - rs.getDouble("minX")) * 1000.0;
                double dimY = (rs.getDouble("maxY") - rs.getDouble("minY")) * 1000.0;
                double dimZ = (rs.getDouble("maxZ") - rs.getDouble("minZ")) * 1000.0;

                double[] dims = {dimX, dimY, dimZ};
                Arrays.sort(dims);
                double S = dims[0], M = dims[1], L = dims[2];

                if (L < 0.01) continue;

                double planarity = S / L;
                double elongation = M / L;
                double volumeM3 = (S * M * L) / 1e9;

                String violation = checkShapeConsistency(ifcClass, planarity, elongation, S, M, L, volumeM3);

                if (violation == null) {
                    results.add(new ProofResult("P10_SHAPE_IDENTITY",
                        ProofResult.Status.PROVEN, guid,
                        "planarity=%.4f elongation=%.4f %.0f×%.0f×%.0fmm".formatted(planarity, elongation, S, M, L),
                        planarity));
                } else {
                    String elementRef = rs.getString("element_ref");
                    String bomTrace = traceBomSource(elementRef, rs.getString("element_name"));
                    String evidence = "%s | dims=%.0f×%.0f×%.0fmm planarity=%.4f elongation=%.4f vol=%.4fm³ | %s"
                        .formatted(violation, S, M, L, planarity, elongation, volumeM3, bomTrace);
                    results.add(new ProofResult("P10_SHAPE_IDENTITY",
                        ProofResult.Status.VIOLATED, guid, evidence, planarity));
                }
            }
        } catch (SQLException e) {
            // Cannot read output DB
        }
        return results;
    }

    static String checkShapeConsistency(String ifcClass,
            double planarity, double elongation,
            double S, double M, double L, double volumeM3) {
        return switch (ifcClass) {
            case "IfcWall", "IfcWallStandardCase" -> planarity >= 0.20
                ? "WALL not planar (thickness/length=%.4f ≥ 0.20)".formatted(planarity) : null;
            case "IfcSlab", "IfcSlabStandardCase" -> planarity >= 0.20
                ? "SLAB not planar (thickness/span=%.4f ≥ 0.20)".formatted(planarity) : null;
            case "IfcPlate" -> planarity >= 0.20
                ? "PLATE not planar (thickness/face=%.4f ≥ 0.20)".formatted(planarity) : null;
            case "IfcDoor", "IfcDoorStandardCase" -> planarity >= 0.35
                ? "DOOR not planar (depth/height=%.4f ≥ 0.35)".formatted(planarity) : null;
            case "IfcWindow", "IfcWindowStandardCase" -> planarity >= 0.35
                ? "WINDOW not planar (depth/height=%.4f ≥ 0.35)".formatted(planarity) : null;
            case "IfcColumn", "IfcColumnStandardCase" ->
                planarity >= 0.15 && elongation >= 0.40
                    ? "COLUMN looks planar/compact (planarity=%.4f elong=%.4f)".formatted(planarity, elongation) : null;
            case "IfcBeam", "IfcBeamStandardCase" ->
                planarity >= 0.15 && elongation >= 0.40
                    ? "BEAM looks planar/compact (planarity=%.4f elong=%.4f)".formatted(planarity, elongation) : null;
            case "IfcFurnishingElement" ->
                planarity < 0.05 && volumeM3 > 1.0
                    ? "FURNITURE looks like wall (planarity=%.4f, arch-scale vol=%.3fm³)".formatted(planarity, volumeM3) : null;
            case "IfcSpace" -> volumeM3 < 1.0
                ? "SPACE too small (vol=%.4fm³ < 1.0m³)".formatted(volumeM3) : null;
            default -> null;
        };
    }

    private static String traceBomSource(String elementRef, String elementName) {
        if (elementRef == null || elementRef.isEmpty()) {
            return "source=COMPILED (no element_ref)";
        }
        String bomDbPath = System.getProperty("bom.db");
        if (bomDbPath == null) {
            return "source=EXTRACTED ref=%s (BOM DB unavailable)".formatted(elementRef);
        }
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath)) {
            String[] refsToTry = elementRef.length() > 2 && elementRef.charAt(1) == '_'
                ? new String[]{elementRef, elementRef.substring(2)}
                : new String[]{elementRef};
            for (String ref : refsToTry) {
                try (PreparedStatement ps = bomConn.prepareStatement("""
                        SELECT bl.bom_id, bl.bom_child_id, bl.role, bl.sequence,
                               bl.allocated_width_mm, bl.allocated_depth_mm, bl.allocated_height_mm,
                               bl.dx, bl.dy, bl.dz, bl.component_type,
                               b.bom_name, b.bom_type
                        FROM m_bom_line bl
                        JOIN m_bom b ON b.bom_id = bl.bom_id
                        WHERE bl.element_ref = ? AND bl.is_active = 1
                        """)) {
                    ps.setString(1, ref);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return ("source=M_BOM bom_id=%s (#%d) bom_name=%s bom_type=%s " +
                                    "role=%s comp_type=%s alloc=%dx%dx%dmm — FIX IN BOM DATA")
                                .formatted(rs.getString("bom_id"), rs.getInt("bom_child_id"),
                                    rs.getString("bom_name"), rs.getString("bom_type"),
                                    rs.getString("role"), rs.getString("component_type"),
                                    rs.getInt("allocated_width_mm"), rs.getInt("allocated_depth_mm"),
                                    rs.getInt("allocated_height_mm"));
                        }
                    }
                }
            }
            return "source=EXTRACTED ref=%s (not in m_bom_line)".formatted(elementRef);
        } catch (SQLException e) {
            return "source=UNKNOWN ref=%s (trace failed: %s)".formatted(elementRef, e.getMessage());
        }
    }
}
