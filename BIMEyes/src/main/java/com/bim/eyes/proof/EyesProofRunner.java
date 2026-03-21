package com.bim.eyes.proof;

import com.bim.eyes.EyesConstants;
import com.bim.eyes.ProductCategory;
import com.bim.eyes.proof.RelationalData.*;
import com.bim.eyes.proof.tier1.*;
import com.bim.eyes.proof.tier2.*;
import com.bim.eyes.proof.tier3.*;

import java.sql.*;
import java.util.*;

/**
 * Orchestrator for all geometric proofs.
 * // Implementing EYES_SRS.md §10 Phase 2 — Witness: W-EYES-NONDISTURB
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #prove(List, String, RelationalContext)} — pre-write, from placement list</li>
 *   <li>{@link #proveFromDB(String, String, RelationalContext)} — post-write, from output DB</li>
 * </ul>
 */
public class EyesProofRunner {

    /** Pre-loaded relational data for tier 2-3 proofs. */
    public record RelationalContext(
        Map<String, WallFaceData> wallFaces,
        Map<String, RoomData> rooms,
        List<ElementRule> elementRules,
        List<String[]> connectEdges,
        Map<String, String> orientationByRef,
        double expectedPerimeter
    ) {
        public static final RelationalContext EMPTY = new RelationalContext(
            Map.of(), Map.of(), List.of(), List.of(), Map.of(), 0);
    }

    /**
     * Prove placement correctness from a list of placement data.
     * Used pre-write in BuildingWriter.
     */
    public static ProofReport prove(List<PlacementData> placements, String buildingName,
            RelationalContext ctx) {
        List<ProofResult> results = new ArrayList<>();

        // Tier 1: Per-element arithmetic
        for (PlacementData p : placements) {
            results.add(PositiveExtentProof.prove(p));
            results.add(FiniteCoordsProof.prove(p));
            results.add(MinDimensionProof.prove(p));
            results.add(StoreyZBandProof.prove(p));
        }

        // Tier 2: Pairwise relations
        results.addAll(DuplicatePositionProof.prove(placements));
        results.addAll(SameClassOverlapProof.prove(placements));

        // Tier 2: Roof-wall validation
        results.addAll(WallRoofIntersectionProof.prove(placements));
        results.addAll(RoofCoverageProof.prove(placements));

        // Tier 2-3: Relational proofs (require library metadata)
        if (buildingName != null && !buildingName.isEmpty()
                && (!ctx.wallFaces().isEmpty() || !ctx.rooms().isEmpty())) {
            // Tier 2 relational
            results.addAll(OpeningContainmentProof.prove(placements, ctx.wallFaces(), ctx.elementRules()));
            results.addAll(FurnitureInRoomProof.prove(placements, ctx.rooms(), ctx.elementRules()));
            results.addAll(FixtureOnSurfaceProof.prove(placements, ctx.wallFaces(), ctx.elementRules()));

            // Tier 3 topological
            results.addAll(PerimeterClosureProof.prove(ctx.wallFaces()));
            results.addAll(WallCoverageProof.prove(ctx.wallFaces(), ctx.rooms()));
            results.addAll(RoomHasDoorProof.prove(placements, ctx.rooms(), ctx.wallFaces(), ctx.elementRules()));

            // Tier 3 conservation
            results.addAll(PerimeterLengthProof.prove(ctx.wallFaces(), ctx.expectedPerimeter()));
            results.addAll(FloorAreaProof.prove(placements, ctx.rooms()));

            // Tier 2-3 MEP
            results.addAll(PipeInHostProof.prove(placements, ctx.rooms(), ctx.elementRules()));
            results.addAll(WasteGradientProof.prove(placements, ctx.connectEdges()));
            results.addAll(SystemConnectedProof.prove(ctx.connectEdges()));

            // Tier 2 wall orientation
            results.addAll(WallOrientationProof.prove(placements, ctx.orientationByRef()));
            results.addAll(ElementInRoomProof.prove(placements, ctx.rooms(), ctx.elementRules()));
        } else if (buildingName == null || buildingName.isEmpty()) {
            results.add(new ProofResult("P07_OPENING_CONTAINED", ProofResult.Status.SKIPPED,
                null, "no building name — skipping relational proofs", 0));
        } else {
            results.add(new ProofResult("P07_OPENING_CONTAINED", ProofResult.Status.SKIPPED,
                null, "no relational data for " + buildingName, 0));
        }

        return buildReport(results);
    }

    /**
     * Prove placement correctness by reading an output DB.
     * Used post-write in E2E tests.
     */
    public static ProofReport proveFromDB(String dbPath, String buildingName,
            RelationalContext ctx) {
        List<PlacementData> placements = new ArrayList<>();
        List<ProofResult> dbLevelResults = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT em.guid, em.ifc_class, em.element_name, em.storey,
                            r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
                     FROM elements_meta em
                     JOIN elements_rtree r ON em.id = r.id
                     """)) {
                while (rs.next()) {
                    String ifc = rs.getString("ifc_class");
                    placements.add(new PlacementData(
                        rs.getString("guid"),
                        ifc,
                        ProductCategory.resolve(ifc),
                        rs.getString("element_name"),
                        rs.getString("storey"),
                        rs.getDouble("minX"), rs.getDouble("maxX"),
                        rs.getDouble("minY"), rs.getDouble("maxY"),
                        rs.getDouble("minZ"), rs.getDouble("maxZ")
                    ));
                }
            }
            // DB-level proofs
            dbLevelResults.addAll(ShapeIdentityProof.prove(conn));
            dbLevelResults.addAll(OpeningMeshInBboxProof.prove(conn));
            dbLevelResults.addAll(DrainCornerAlignmentProof.prove(conn));
            // P25/P26: aggregate building-level proofs (Phase 3)
            dbLevelResults.addAll(RoomValidityProof.prove(conn));
            dbLevelResults.addAll(BuildingCompletenessProof.prove(conn));
        } catch (SQLException e) {
            System.err.printf("[PROOF] Cannot read DB %s: %s%n", dbPath, e.getMessage());
            return buildReport(List.of());
        }

        ProofReport baseReport = prove(placements, buildingName, ctx);
        List<ProofResult> allResults = new ArrayList<>(baseReport.results());
        allResults.addAll(dbLevelResults);
        return buildReport(allResults);
    }

    /** Build aggregate report from results list. */
    public static ProofReport buildReport(List<ProofResult> results) {
        int proven = 0, violated = 0, skipped = 0;
        for (ProofResult r : results) {
            switch (r.status()) {
                case PROVEN -> proven++;
                case VIOLATED -> violated++;
                case SKIPPED -> skipped++;
            }
        }
        return new ProofReport(results, proven, violated, skipped);
    }
}
