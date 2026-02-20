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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Registry-driven pipeline test — one engine, N buildings.
 *
 * Uses @TestFactory to generate DynamicTest per active building in ad_building_registry.
 * Adding a new building = one SQL INSERT, zero Java files.
 *
 * Each test runs CompilationPipeline.run() and checks:
 *   1. Element count matches expected
 *   2. SpatialDigest matches (if registered)
 *   3. Critical proofs pass (if prover ran)
 *   4. Shadow validation passes (if EXTRACTED)
 *   5. Geometry integrity passes
 *   6. Building-specific assertions from ad_building_assertions
 */
public class BuildingRegistryTest {

    @TestFactory
    Collection<DynamicTest> compilationPipeline() {
        List<BuildingEntry> buildings = BuildingRegistry.loadActive();
        assertFalse(buildings.isEmpty(), "ad_building_registry must have active buildings");

        List<DynamicTest> tests = new ArrayList<>();
        for (BuildingEntry entry : buildings) {
            tests.add(DynamicTest.dynamicTest(
                entry.name() + " [" + entry.provenance() + "]",
                () -> runPipeline(entry)
            ));
        }
        return tests;
    }

    private void runPipeline(BuildingEntry entry) throws Exception {
        PipelineResult result = CompilationPipeline.run(entry);

        // 1. Element count
        assertEquals(entry.expectedElements(), result.elementCount(),
            entry.id() + ": element count mismatch");

        // 2. SpatialDigest (if registered)
        if (entry.spatialDigest() != null) {
            assertEquals(entry.spatialDigest(), result.spatialDigest(),
                entry.id() + ": spatial digest mismatch");
        }

        // 3. Critical proofs (if prover ran)
        if (!result.proverSkipped()) {
            assertNotNull(result.proofs(), entry.id() + ": prover must produce report");
            assertEquals(0, result.proofs().criticalViolations(),
                entry.id() + ": critical proof violations");
        }

        // 4. Shadow validation (EXTRACTED only, when relational data exists)
        if (!entry.isGenerative() && result.shadowMismatches() >= 0) {
            assertEquals(0, result.shadowMismatches(),
                entry.id() + ": shadow validation mismatches");
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
