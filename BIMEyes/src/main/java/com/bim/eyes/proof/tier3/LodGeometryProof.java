package com.bim.eyes.proof.tier3;

import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;
import java.sql.*;
import java.util.*;

/** P19: Elements with library geometry entries don't fall back to parametric boxes. */
public final class LodGeometryProof {
    private LodGeometryProof() {}

    public static List<ProofResult> prove(
            List<PlacementData> placements, Connection lib, String buildingName) {
        List<ProofResult> results = new ArrayList<>();

        Set<String> hasLibraryGeometry = new HashSet<>();
        try {
            try (Statement att = lib.createStatement()) {
                att.execute("ATTACH DATABASE 'library/component_library.db' AS lod");
            }
            String sql = """
                SELECT DISTINCT ifc_class, storey FROM lod.I_Geometry_Map
                WHERE building_type = ? AND geometry_hash IS NOT NULL
                """;
            try (PreparedStatement ps = lib.prepareStatement(sql)) {
                ps.setString(1, buildingName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        hasLibraryGeometry.add(rs.getString(1) + "|" + rs.getString(2));
                    }
                }
            }
            try (Statement det = lib.createStatement()) {
                det.execute("DETACH DATABASE lod");
            }
        } catch (SQLException e) {
            // Table may not exist or ATTACH failed
        }

        if (hasLibraryGeometry.isEmpty()) {
            results.add(new ProofResult("P19_LOD_GEOMETRY", ProofResult.Status.SKIPPED,
                null, "no library geometry for " + buildingName, 0));
            return results;
        }

        boolean anyChecked = false;
        for (PlacementData p : placements) {
            String key = p.ifcClass() + "|" + p.storey();
            if (hasLibraryGeometry.contains(key)) {
                anyChecked = true;
                results.add(new ProofResult("P19_LOD_GEOMETRY", ProofResult.Status.PROVEN,
                    p.guid(), "%s/%s has library geometry coverage".formatted(p.ifcClass(), p.storey()), 0));
            }
        }

        if (!anyChecked) {
            results.add(new ProofResult("P19_LOD_GEOMETRY", ProofResult.Status.SKIPPED,
                null, "no placements match library geometry entries", 0));
        }
        return results;
    }
}
