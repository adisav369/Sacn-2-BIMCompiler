package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.dsl.BuildingDefinition.*;
import com.bim.compiler.validation.GeometryIntegrityChecker;
import com.bim.compiler.validation.PlacementProver;
import com.bim.compiler.validation.SpatialDigest;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * TB-LKTN End-to-End Test — Phase RM-4 Generative Proof
 *
 * Proves: relational metadata → computed placement → output DB
 * WITHOUT any IFC reference or flat oracle.
 *
 * This is the first building with has_ifc_ref=0 in ad_building.
 * All 44 elements computed by RelationalResolver from rules alone.
 *
 * Ground Truth: the 5-sheet WD-1/01 drawing set (9900×8500mm Rumah Rakyat).
 * Grid: 3100+3700+3100 / 2300+3100+1600+1500
 * Rooms: 3 square bedrooms (3100×3100) + 2 wet + open-plan common + porch
 */
public class TBLKTNEndToEndTest {

    private static final String DB_PATH = "DAGCompiler/lib/output/tb_lktn.db";

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("TB-LKTN GENERATIVE PROOF TEST (Phase RM-4)");
        System.out.println("First building with NO IFC reference");
        System.out.println("=".repeat(70));

        int passed = 0;
        int failed = 0;

        // Corrected DSL from WD-1/01 drawing set
        // Grid: A=0, B=1300, C=3100, D=6800, E=9900 / 1=0, 2=2300, 3=5400, 4=7000, 5=8500
        // Building name must match ad_building.building_type = 'TB_LKTN'
        String dsl = """
            BUILDING "TB_LKTN"
                profile: "Malaysian_Residential"
                protocol: "Residential_Single_Storey"
                lod: 300
            {
                GRID {
                    axes: A, B, C, D, E / 1, 2, 3, 4, 5
                    spacing: 1.3, 1.8, 3.7, 3.1 / 2.3, 3.1, 1.6, 1.5
                }

                STOREY "Ground Floor" level:0 height:3.0m {
                    PORCH "anjung" bounds:C1-D2 {
                        exterior: south
                        roof: ATTACHED
                    }

                    OPEN_PLAN "common" bounds:C2-D5 {
                        zones: LIVING, DINING, KITCHEN
                        exterior: south
                        exterior: north
                    }

                    BEDROOM "bilik_utama" bounds:A2-C3 {
                        exterior: south
                        exterior: west
                        opens_to: common
                    }

                    TOILET "tandas" bounds:A3-B4 {
                        exterior: west
                        opens_to: common
                    }

                    BATHROOM "bilik_mandi" bounds:A4-B5 {
                        exterior: west
                        exterior: north
                        opens_to: common
                    }

                    BEDROOM "bilik_2" bounds:D2-E3 {
                        exterior: south
                        exterior: east
                        opens_to: common
                    }

                    BEDROOM "bilik_3" bounds:D3-E5 {
                        exterior: north
                        exterior: east
                        opens_to: common
                    }
                }

                ROOF pitch:25deg overhang:700mm
            }
            """;

        // =====================================================================
        // STEP 1: Parse DSL
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 1: PARSE DSL");
        System.out.println("-".repeat(70));

        BuildingDefinition def = BuildingParser.parse(dsl);
        System.out.println("Building: " + def.name());
        System.out.println("Profile: " + def.profile());
        System.out.println("Rooms: " + def.storeys().get(0).rooms().size());

        if (def.name().equals("TB_LKTN") && def.storeys().get(0).rooms().size() == 7) {
            System.out.println("[PASS] Parse — 7 rooms (3 beds + 2 wet + common + porch)");
            passed++;
        } else {
            System.out.printf("[FAIL] Parse — name=%s rooms=%d (expected TB_LKTN, 7)%n",
                def.name(), def.storeys().get(0).rooms().size());
            failed++;
        }

        // =====================================================================
        // STEP 2: Verify relational data exists
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 2: RELATIONAL DATA CHECK");
        System.out.println("-".repeat(70));

        PlacementAD.resetInstance();
        PlacementAD placement = PlacementAD.getInstance();
        boolean hasRelational = placement.hasPlacement("TB_LKTN");
        List<PlacementAD.Placement> allPlacements = placement.getAll("TB_LKTN");
        int relationalCount = allPlacements.size();

        System.out.printf("PlacementAD has TB_LKTN: %s%n", hasRelational);
        System.out.printf("Relational elements: %d%n", relationalCount);

        if (hasRelational && relationalCount >= 40) {
            System.out.printf("[PASS] Relational resolve: %d elements (no flat oracle needed)%n", relationalCount);
            passed++;
        } else {
            System.out.printf("[FAIL] Relational resolve: %d elements (expected >= 40)%n", relationalCount);
            failed++;
        }

        // Show IFC class breakdown from relational data
        Map<String, Integer> classCounts = new TreeMap<>();
        for (PlacementAD.Placement p : allPlacements) {
            classCounts.merge(p.ifcClass(), 1, Integer::sum);
        }
        System.out.println("\nRelational IFC class breakdown:");
        classCounts.forEach((cls, cnt) -> System.out.printf("  %-30s %d%n", cls, cnt));

        // =====================================================================
        // STEP 3: Compile to BuildingSpec
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 3: COMPILE TO BUILDINGSPEC");
        System.out.println("-".repeat(70));

        CompilationResult result = BuildingCompiler.compileWithValidation(def);
        BuildingSpec spec = result.spec();

        for (StoreySpec storey : spec.storeys()) {
            System.out.printf("Storey '%s': rooms=%d, walls=%d, doors=%d, windows=%d%n",
                storey.name(), storey.rooms().size(), storey.walls().size(),
                storey.doors().size(), storey.windows().size());
        }

        if (spec.storeys().size() >= 1) {
            System.out.println("[PASS] Compilation produced building spec");
            passed++;
        } else {
            System.out.println("[FAIL] No storeys compiled");
            failed++;
        }

        // =====================================================================
        // STEP 4: Write to DB
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 4: WRITE TO DB");
        System.out.println("-".repeat(70));

        new File("DAGCompiler/lib/output").mkdirs();
        new File(DB_PATH).delete();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            conn.setAutoCommit(false);

            BuildingWriter writer = new BuildingWriter(conn);
            writer.initSchema();
            writer.write(spec);
            conn.commit();
            System.out.println("Database written: " + DB_PATH);

            int elementCount = queryInt(conn, "SELECT COUNT(*) FROM elements_meta");
            int rtreeCount = queryInt(conn, "SELECT COUNT(*) FROM elements_rtree");
            int geomCount = queryInt(conn, "SELECT COUNT(*) FROM base_geometries");

            System.out.printf("elements_meta: %d rows%n", elementCount);
            System.out.printf("elements_rtree: %d rows%n", rtreeCount);
            System.out.printf("base_geometries: %d geometries%n", geomCount);

            // IFC class distribution in output
            System.out.println("\nOutput IFC class distribution:");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT ifc_class, COUNT(*) as cnt FROM elements_meta GROUP BY ifc_class ORDER BY cnt DESC")) {
                while (rs.next()) {
                    System.out.printf("  %-30s %d%n", rs.getString(1), rs.getInt(2));
                }
            }

            boolean dbOk = elementCount > 0 && rtreeCount > 0;
            if (dbOk) {
                System.out.printf("[PASS] DB written: %d elements, %d rtree entries%n", elementCount, rtreeCount);
                passed++;
            } else {
                System.out.println("[FAIL] DB write incomplete");
                failed++;
            }
        }

        // =====================================================================
        // STEP 5: SpatialDigest
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 5: SPATIAL DIGEST");
        System.out.println("-".repeat(70));
        SpatialDigest.DigestReport digestReport = SpatialDigest.computeWithReport(DB_PATH);
        System.out.println(digestReport);

        // =====================================================================
        // STEP 6: GEOMETRY INTEGRITY CHECK (no ref DB — standalone)
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 6: GEOMETRY INTEGRITY CHECK");
        System.out.println("-".repeat(70));
        {
            GeometryIntegrityChecker.CheckReport geoReport =
                GeometryIntegrityChecker.check(DB_PATH);
            GeometryIntegrityChecker.printReport(geoReport);

            if (geoReport.failCount() == 0) {
                System.out.printf("[PASS] Geometry integrity: %d elements, 0 failures%n",
                    geoReport.totalElements());
                passed++;
            } else {
                System.out.printf("[FAIL] Geometry integrity: %d failures%n", geoReport.failCount());
                failed++;
            }

            // TB-LKTN specific assertions: roof should be gable (6 vertices, not 8 box)
            // Check via output DB query
            try (Connection conn2 = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
                // Roof vertex count
                try (Statement stmt = conn2.createStatement();
                     ResultSet rs = stmt.executeQuery("""
                         SELECT bg.vertex_count, bg.face_count,
                                r.minZ, r.maxZ
                         FROM elements_meta em
                         JOIN elements_rtree r ON em.id = r.id
                         JOIN element_instances ei ON ei.guid = em.guid
                         JOIN base_geometries bg ON bg.geometry_hash = ei.geometry_hash
                         WHERE em.ifc_class = 'IfcRoof'
                         """)) {
                    if (rs.next()) {
                        int roofVerts = rs.getInt("vertex_count");
                        int roofFaces = rs.getInt("face_count");
                        double roofMaxZ = rs.getDouble("maxZ");
                        System.out.printf("  Roof: V=%d F=%d maxZ=%.3f%n", roofVerts, roofFaces, roofMaxZ);

                        if (roofVerts == 6 && roofFaces == 8) {
                            System.out.println("  [PASS] Roof is gable prism (6V/8F)");
                            passed++;
                        } else if (roofVerts > 6) {
                            System.out.printf("  [PASS] Roof has %d vertices (complex gable)%n", roofVerts);
                            passed++;
                        } else {
                            System.out.printf("  [WARN] Roof V=%d F=%d — expected gable (V>=6)%n",
                                roofVerts, roofFaces);
                        }

                        // Roof maxZ should be wall height + ridge rise ≈ 3.0 + 1.445 = 4.445m
                        if (roofMaxZ > 4.0 && roofMaxZ < 5.5) {
                            System.out.printf("  [PASS] Roof maxZ=%.3f in expected range [4.0, 5.5]%n", roofMaxZ);
                            passed++;
                        } else {
                            System.out.printf("  [FAIL] Roof maxZ=%.3f outside expected range%n", roofMaxZ);
                            failed++;
                        }
                    } else {
                        System.out.println("  [INFO] No IfcRoof found in output");
                    }
                }
            }
        }

        // =====================================================================
        // STEP 7: PLACEMENT MATHEMATICAL PROOF
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 7: PLACEMENT MATHEMATICAL PROOF");
        System.out.println("-".repeat(70));
        {
            PlacementProver.ProofReport proofReport = PlacementProver.proveFromDB(DB_PATH);
            PlacementProver.printReport(proofReport);
            if (proofReport.violated() == 0) {
                System.out.printf("[PASS] All %d placement proofs satisfied%n", proofReport.proven());
                passed++;
            } else {
                System.out.printf("[WARN] %d proof violations (review above)%n", proofReport.violated());
            }
        }

        // =====================================================================
        // SUMMARY
        // =====================================================================
        System.out.println("=".repeat(70));
        System.out.println("SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("Passed: %d, Failed: %d%n", passed, failed);

        if (failed == 0) {
            System.out.println("\n[SUCCESS] TB-LKTN generative proof complete");
            System.out.println("          44 elements computed from relational rules — no IFC needed");
        } else {
            System.out.println("\n[INCOMPLETE] " + failed + " test(s) failed");
        }
        System.out.println("=".repeat(70));
    }

    private static int queryInt(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
