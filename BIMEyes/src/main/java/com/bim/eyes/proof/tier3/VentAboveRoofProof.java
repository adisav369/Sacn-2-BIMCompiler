package com.bim.eyes.proof.tier3;

import com.bim.eyes.ProductCategory;
import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;
import java.sql.*;
import java.util.*;

/** P18: Vent pipe extends above roof level. */
public final class VentAboveRoofProof {
    private VentAboveRoofProof() {}

    public static List<ProofResult> prove(
            List<PlacementData> placements, Connection lib, String buildingName) {
        List<ProofResult> results = new ArrayList<>();

        PlacementData vent = null;
        PlacementData roof = null;
        for (PlacementData p : placements) {
            if (ProductCategory.ENVELOPE.equals(p.productCategory())
                    && "IfcRoof".equals(p.ifcClass())) {
                roof = p;
            }
            if (ProductCategory.MEP_ROUTING.equals(p.productCategory()) && p.elementRef() != null) {
                try {
                    String sql = "SELECT M_Product_ID FROM c_orderline WHERE C_Order_ID = ? AND Name = ? AND IsActive = 1";
                    try (PreparedStatement ps = lib.prepareStatement(sql)) {
                        ps.setString(1, buildingName);
                        ps.setString(2, p.elementRef());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String family = rs.getString(1);
                                if (family != null && p.maxZ() > 3.0) {
                                    vent = p;
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    // skip
                }
            }
        }

        if (vent == null || roof == null) {
            results.add(new ProofResult("P18_VENT_ABOVE_ROOF", ProofResult.Status.SKIPPED,
                null, "no vent pipe or roof found", 0));
            return results;
        }

        if (vent.maxZ() > roof.maxZ()) {
            results.add(new ProofResult("P18_VENT_ABOVE_ROOF", ProofResult.Status.PROVEN,
                vent.guid(), "vent.maxZ=%.3f > roof.maxZ=%.3f".formatted(vent.maxZ(), roof.maxZ()),
                vent.maxZ() - roof.maxZ()));
        } else {
            results.add(new ProofResult("P18_VENT_ABOVE_ROOF", ProofResult.Status.VIOLATED,
                vent.guid(), "vent.maxZ=%.3f ≤ roof.maxZ=%.3f".formatted(vent.maxZ(), roof.maxZ()),
                roof.maxZ() - vent.maxZ()));
        }
        return results;
    }
}
