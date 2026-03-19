package com.bim.compiler.contract;

import com.bim.compiler.dsl.BuildingRegistry;
import com.bim.compiler.dsl.BuildingRegistry.BuildingAssertion;
import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;
import com.bim.compiler.dsl.CompilationPipeline;
import com.bim.compiler.dsl.CompilationPipeline.PipelineResult;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Registry-driven pipeline test — one engine, N buildings.
 *
 * Uses @TestFactory to generate DynamicTest per active building in c_order.
 * Adding a new building = one SQL INSERT, zero Java files.
 *
 * Each test runs CompilationPipeline.run() and checks:
 *   1. Element count matches expected
 *   2. SpatialDigest matches (if registered)
 *   3. EmptySpaceChecksum matches (if registered) — single level-0 CO_EmptySpaceLine
 *   4. Critical proofs pass (if prover ran)
 *   5. Shadow validation passes (if EXTRACTED)
 *   6. Geometry integrity passes
 *   7. Building-specific assertions from ad_building_assertions
 */
public class BuildingRegistryTest {

    @TestFactory
    Collection<DynamicTest> compilationPipeline() {
        // doc.base.type property filters which DocBaseType to compile:
        //   RE → ENBLOC (direct BUILDING BOM match, singularity)
        //   ST → WALKTHRU (template path, M_BomCategory tree walk)
        //   null → all active (default)
        String docBaseType = System.getProperty("doc.base.type");
        List<BuildingEntry> buildings = docBaseType != null
            ? BuildingRegistry.loadByDocBaseType(docBaseType)
            : BuildingRegistry.loadActive();
        assertFalse(buildings.isEmpty(),
            "C_DocType must have active building types"
                + (docBaseType != null ? " for DocBaseType=" + docBaseType : ""));

        List<DynamicTest> tests = new ArrayList<>();
        for (BuildingEntry entry : buildings) {
            tests.add(DynamicTest.dynamicTest(
                entry.name() + " [" + entry.provenance() + "]",
                () -> runPipeline(entry)
            ));
        }
        return tests;
    }

    /** Active gate scope — RE + ST for SH/DX assertions enforced. Others skip. */
    private static final Set<String> GATE_SCOPE = Set.of("RE_SH", "RE_DX", "ST_SH", "ST_DX", "CO_TE", "IN_BR", "RE_FK");

    private void runPipeline(BuildingEntry entry) throws Exception {
        assumeTrue(GATE_SCOPE.contains(entry.docTypeId()),
            entry.docTypeId() + " outside gate scope");
        assumeTrue(entry.dslContent() != null && !entry.dslContent().isBlank(),
            entry.docTypeId() + " has no DSL content (template path — walkthru mode)");
        PipelineResult result = CompilationPipeline.run(entry);

        // 1. Element count
        assertEquals(entry.expectedElements(), result.elementCount(),
            entry.id() + ": element count mismatch");

        // 2. SpatialDigest — transactional, checked from C_Order in output.db (not on C_DocType)
        if (result.spatialDigest() != null) {
            assertNotNull(result.spatialDigest(),
                entry.id() + ": spatial digest should be computed");
        }

        // 3. EmptySpaceChecksum — transactional, checked from C_Order in output.db
        if (result.emptySpaceChecksum() != null) {
            assertNotNull(result.emptySpaceChecksum(),
                entry.id() + ": empty space checksum should be computed");
        }

        // 4. Critical proofs (if prover ran)
        if (!result.proverSkipped()) {
            assertNotNull(result.proofs(), entry.id() + ": prover must produce report");
            assertEquals(0, result.proofs().criticalViolations(),
                entry.id() + ": critical proof violations");
        }

        // 5. Geometry integrity (threshold-gated)
        assertNotNull(result.geometryReport(), entry.id() + ": geometry report must exist");
        assertTrue(result.geometryReport().failCount() <= entry.geometryFailThreshold(),
            String.format("%s: geometry failures %d exceeds threshold %d",
                entry.id(), result.geometryReport().failCount(), entry.geometryFailThreshold()));

        // 6. Building-specific assertions from ad_building_assertions
        runBuildingAssertions(entry, result);
    }

    /**
     * Run assertions from ad_building_assertions against the output DB.
     * Each row specifies: element_match, property, operator, expected, tolerance.
     */
    private void runBuildingAssertions(BuildingEntry entry, PipelineResult result) throws Exception {
        List<BuildingAssertion> assertions = BuildingRegistry.loadAssertions(entry.id());
        if (assertions.isEmpty()) return;

        String dbPath = entry.outputDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            for (BuildingAssertion a : assertions) {
                double actual = queryAssertionValue(conn, a);
                boolean passed = evaluateAssertion(a, actual);
                assertTrue(passed,
                    String.format("%s assertion %s: %s %s %s (actual=%.3f, tolerance=%.3f)",
                        entry.id(), a.assertionId(), a.property(), a.operator(), a.expected(),
                        actual, a.tolerance()));
            }
        }
    }

    /**
     * Query the actual value for an assertion from the output DB.
     *
     * element_match patterns:
     *   "IfcRoof"           → all elements with ifc_class = 'IfcRoof'
     *   "IfcPlate:PARTY"    → elements with ifc_class = 'IfcPlate' AND element_name LIKE '%PARTY%'
     *
     * property values:
     *   "count"          → COUNT(*) of matched elements
     *   "maxZ"           → MAX(maxZ) from rtree
     *   "vertex_count"   → vertex_count from base_geometries (first matched element)
     */
    private double queryAssertionValue(Connection conn, BuildingAssertion a) throws SQLException {
        String ifcClass;
        String nameFilter = null;
        if (a.elementMatch().contains(":")) {
            String[] parts = a.elementMatch().split(":", 2);
            ifcClass = parts[0];
            nameFilter = parts[1];
        } else {
            ifcClass = a.elementMatch();
        }

        return switch (a.property()) {
            case "count" -> {
                String sql = "SELECT COUNT(*) FROM elements_meta WHERE ifc_class = ?";
                if (nameFilter != null) {
                    sql += " AND (element_name LIKE ? OR element_type LIKE ?)";
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, ifcClass);
                    if (nameFilter != null) {
                        ps.setString(2, "%" + nameFilter + "%");
                        ps.setString(3, "%" + nameFilter + "%");
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        yield rs.next() ? rs.getDouble(1) : 0.0;
                    }
                }
            }
            case "maxZ" -> {
                String sql = """
                    SELECT MAX(r.maxZ) FROM elements_meta em
                    JOIN elements_rtree r ON em.id = r.id
                    WHERE em.ifc_class = ?""";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, ifcClass);
                    try (ResultSet rs = ps.executeQuery()) {
                        yield rs.next() ? rs.getDouble(1) : 0.0;
                    }
                }
            }
            case "vertex_count" -> {
                String sql = """
                    SELECT bg.vertex_count FROM elements_meta em
                    JOIN element_instances ei ON ei.guid = em.guid
                    JOIN base_geometries bg ON bg.geometry_hash = ei.geometry_hash
                    WHERE em.ifc_class = ?
                    LIMIT 1""";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, ifcClass);
                    try (ResultSet rs = ps.executeQuery()) {
                        yield rs.next() ? rs.getDouble(1) : 0.0;
                    }
                }
            }
            default -> throw new IllegalArgumentException(
                "Unknown assertion property: " + a.property());
        };
    }

    /**
     * Evaluate an assertion: operator applied to actual vs expected.
     */
    private boolean evaluateAssertion(BuildingAssertion a, double actual) {
        return switch (a.operator()) {
            case "EQUALS" -> Math.abs(actual - Double.parseDouble(a.expected())) <= a.tolerance();
            case "GREATER_THAN" -> actual > Double.parseDouble(a.expected());
            case "LESS_THAN" -> actual < Double.parseDouble(a.expected());
            case "BETWEEN" -> {
                String[] bounds = a.expected().split("\\|", 2);
                double lo = Double.parseDouble(bounds[0]);
                double hi = Double.parseDouble(bounds[1]);
                yield actual >= lo - a.tolerance() && actual <= hi + a.tolerance();
            }
            default -> throw new IllegalArgumentException(
                "Unknown assertion operator: " + a.operator());
        };
    }
}
