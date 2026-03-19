package com.bim.designer.validation;

import com.bim.designer.validation.InferenceEngine.*;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * check_method dispatch tests — DocAction_SRS §3.1
 *
 * <p>Tests the check_method dispatch mechanism in PlacementValidatorImpl
 * and InferenceEngine. Verifies backward compatibility with threshold-only
 * rules, and correct dispatch to named evaluators.
 *
 * <p>Batch spatial predicates (NN, clearance, Z consistency) tested
 * against synthetic data — no DB required for math checks.
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-DV-CHECK-DISPATCH: check_method param triggers dispatch instead of threshold</li>
 *   <li>W-DV-WARN-1: Rules with verdict=WARN never return BLOCK</li>
 *   <li>W-DV-NN-1: NN distance correctly detects spacing violations</li>
 *   <li>W-DV-CLR-1: Centre clearance matches M12 formula</li>
 *   <li>W-DV-ZSTD-1: Z consistency detects per-storey deviation</li>
 *   <li>W-DV-BACKWARD-1: Existing threshold rules still work unchanged</li>
 *   <li>W-DV-BATCH-1: validateBatch runs spatial checks across element groups</li>
 * </ul>
 */
@DisplayName("check_method dispatch — DocAction_SRS §3.1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CheckMethodDispatchTest {

    // ── SpatialPredicates unit tests ─────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("W-DV-NN-1: NN distance for 3 sprinkler heads on same storey")
    void nnDistance_threeSprinklers() {
        // Three heads at (0,0), (3000,0), (6000,0) — NN should be 3000mm
        var heads = List.of(
            sprinklerAt(0, 0),
            sprinklerAt(3000, 0),
            sprinklerAt(6000, 0)
        );

        assertEquals(3000.0, SpatialPredicates.nnDistance(heads, heads.get(0)), 0.1,
                "Head[0] NN to Head[1] = 3000");
        assertEquals(3000.0, SpatialPredicates.nnDistance(heads, heads.get(1)), 0.1,
                "Head[1] NN to Head[0] or Head[2] = 3000");
        assertEquals(3000.0, SpatialPredicates.nnDistance(heads, heads.get(2)), 0.1,
                "Head[2] NN to Head[1] = 3000");
    }

    @Test
    @Order(2)
    @DisplayName("W-DV-NN-1: NN distance detects violation > max_spacing")
    void nnDistance_violatesMaxSpacing() {
        // Heads at (0,0) and (5000,0) — NN=5000 > NFPA 13 max 4600
        var heads = List.of(sprinklerAt(0, 0), sprinklerAt(5000, 0));

        double nn = SpatialPredicates.nnDistance(heads, heads.get(0));
        assertEquals(5000.0, nn, 0.1);
        assertTrue(nn > 4600, "Should exceed NFPA 13 max spacing");
    }

    @Test
    @Order(3)
    @DisplayName("W-DV-CLR-1: Centre clearance matches M12 formula")
    void centreClearance_m12Formula() {
        // Pipe A: 50mm diameter at (1000, 1000), Pipe B: 30mm diameter at (1100, 1000)
        // clearance = dist(100mm) - 25 - 15 = 60mm
        PlacementRequest a = pipeAt(1000, 1000, 50);
        PlacementRequest b = pipeAt(1100, 1000, 30);

        double clearance = SpatialPredicates.centreClearance(a, b);
        assertEquals(60.0, clearance, 0.1, "clearance = 100 - 25 - 15 = 60mm");
    }

    @Test
    @Order(4)
    @DisplayName("W-DV-CLR-1: Negative clearance = overlap")
    void centreClearance_overlap() {
        // Two 100mm pipes at same position
        PlacementRequest a = pipeAt(1000, 1000, 100);
        PlacementRequest b = pipeAt(1050, 1000, 100);

        double clearance = SpatialPredicates.centreClearance(a, b);
        assertTrue(clearance < 0, "Should be negative (overlap): " + clearance);
    }

    @Test
    @Order(5)
    @DisplayName("W-DV-ZSTD-1: Z consistency detects storey deviation")
    void zStdDev_detectsDeviation() {
        // Lights at z=2700, 2700, 2700, 3500 — stddev should be high
        var lights = List.of(
            lightAt(0, 0, 2700),
            lightAt(1000, 0, 2700),
            lightAt(2000, 0, 2700),
            lightAt(3000, 0, 3500)  // outlier
        );

        double stddev = SpatialPredicates.zStdDev(lights);
        assertTrue(stddev > 300, "Should detect large Z deviation: " + stddev);
    }

    @Test
    @Order(6)
    @DisplayName("W-DV-ZSTD-1: Consistent Z → low stddev")
    void zStdDev_consistent() {
        var lights = List.of(
            lightAt(0, 0, 2700),
            lightAt(1000, 0, 2705),
            lightAt(2000, 0, 2695),
            lightAt(3000, 0, 2700)
        );

        double stddev = SpatialPredicates.zStdDev(lights);
        assertTrue(stddev < 10, "Consistent Z should have low stddev: " + stddev);
    }

    // ── check_method dispatch in PlacementValidatorImpl ──────────────

    @Test
    @Order(10)
    @DisplayName("W-DV-CHECK-DISPATCH: M_PRODUCT_CROSS_SECTION blocks undersized pipe")
    void checkMethodDispatch_crossSectionBlock() {
        PlacementValidatorImpl validator = validatorWithRules(List.of(
            crossSectionRule(100, "min_main_diameter_mm", 50.0)
        ));

        // 30mm pipe → cross_section = MIN(30, 30) = 30 < 50 → BLOCK
        PlacementRequest req = pipeAt(0, 0, 30);
        ValidationVerdict v = validator.validate(req);

        assertTrue(v.isBlocked(), "30mm pipe should be blocked by 50mm minimum");
        assertTrue(v.message().contains("min_main_diameter_mm"));
    }

    @Test
    @Order(11)
    @DisplayName("W-DV-CHECK-DISPATCH: M_PRODUCT_CROSS_SECTION passes adequate pipe")
    void checkMethodDispatch_crossSectionPass() {
        PlacementValidatorImpl validator = validatorWithRules(List.of(
            crossSectionRule(100, "min_main_diameter_mm", 50.0)
        ));

        // 80mm pipe → cross_section = 80 >= 50 → PASS
        PlacementRequest req = pipeAt(0, 0, 80);
        ValidationVerdict v = validator.validate(req);

        assertFalse(v.isBlocked(), "80mm pipe should pass 50mm minimum");
    }

    @Test
    @Order(12)
    @DisplayName("W-DV-CHECK-DISPATCH: Unknown check_method → PASS (skip, DV-E-02)")
    void checkMethodDispatch_unknownMethod() {
        PlacementValidatorImpl validator = validatorWithRules(List.of(
            ruleWithCheckMethod(200, "UNKNOWN_METHOD", Map.of(), Map.of())
        ));

        ValidationVerdict v = validator.validate(sprinklerAt(0, 0));
        assertFalse(v.isBlocked(), "Unknown check_method should not block");
    }

    // ── DV-F-11: WARN verdict ────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("W-DV-WARN-1: Rule with verdict=WARN returns WARN, not BLOCK")
    void verdictOverride_warnNotBlock() {
        PlacementValidatorImpl validator = validatorWithRules(List.of(
            warnRule(300, "min_dim_mm", 5000.0)
        ));

        // Room 3000x3000 → min_dim=3000 < 5000 → but verdict=WARN, not BLOCK
        PlacementRequest req = roomAt("BEDROOM", 3000, 3000, 2800);
        ValidationVerdict v = validator.validate(req);

        assertTrue(v.isWarning(), "Should return WARN, not BLOCK");
        assertFalse(v.isBlocked(), "WARN verdict must not be BLOCK");
        assertEquals(ValidationVerdict.Result.WARN, v.result());
    }

    @Test
    @Order(21)
    @DisplayName("W-DV-WARN-1: InferenceEngine mirrors WARN verdict")
    void inferenceEngine_warnVerdict() {
        InferenceEngine engine = new InferenceEngine();
        CachedRuleExt rule = new CachedRuleExt(
                300, "Advisory_MinDim", "ADVISORY",
                Map.of("min_dim_mm", 5000.0),
                Map.of("verdict", "WARN"),
                0);

        PlacementRequest req = roomAt("BEDROOM", 3000, 3000, 2800);
        List<RuleResult> results = engine.evaluate(List.of(rule), req);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isWarning(), "InferenceEngine should return WARN");
        assertFalse(results.get(0).isBlocked(), "WARN must not be BLOCK");
    }

    // ── W-DV-BACKWARD-1: Backward compatibility ─────────────────────

    @Test
    @Order(30)
    @DisplayName("W-DV-BACKWARD-1: Existing threshold rules still work")
    void backwardCompat_thresholdRulesUnchanged() {
        PlacementValidatorImpl validator = validatorWithRules(List.of(
            thresholdRule(1, "BEDROOM", "min_area_m2", 9.0),
            thresholdRule(2, "BEDROOM", "min_dim_mm", 3000.0)
        ));

        // Passing room: 4000x4000 = 16m², min_dim = 4000
        PlacementRequest pass = roomAt("BEDROOM", 4000, 4000, 2800);
        assertFalse(validator.validate(pass).isBlocked(), "Large room should pass");

        // Failing room: 2000x2000 = 4m² < 9m²
        PlacementRequest fail = roomAt("BEDROOM", 2000, 2000, 2800);
        assertTrue(validator.validate(fail).isBlocked(), "Small room should be blocked");
    }

    @Test
    @Order(31)
    @DisplayName("W-DV-BACKWARD-1: InferenceEngine backward compat with old CachedRuleExt")
    void backwardCompat_oldCachedRuleExt() {
        // Old constructor (no stringParams)
        CachedRuleExt rule = new CachedRuleExt(
                1, "OldRule", "TEST",
                Map.of("min_dim_mm", 3000.0),
                0);

        InferenceEngine engine = new InferenceEngine();
        PlacementRequest req = roomAt("BEDROOM", 4000, 4000, 2800);
        List<RuleResult> results = engine.evaluate(List.of(rule), req);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isPassed(), "Old-style rule should still evaluate");
    }

    // ── W-DV-BATCH-1: Batch validation ──────────────────────────────

    @Test
    @Order(40)
    @DisplayName("W-DV-BATCH-1: validateBatch detects NN spacing violation")
    void validateBatch_nnSpacingViolation() {
        // Rule: NN_CENTROID_DISTANCE, max_spacing=4600mm for IfcFireSuppressionTerminal
        PlacementValidatorImpl validator = validatorWithRules(List.of(
            ruleWithCheckMethod(601, "NN_CENTROID_DISTANCE",
                Map.of("max_spacing_mm", 4600.0),
                Map.of("ifc_class", "IfcFireSuppressionTerminal"))
        ));

        // 3 heads: (0,0), (3000,0), (8000,0) — head[2] NN=5000 > 4600
        var requests = List.of(
            sprinklerAt(0, 0),
            sprinklerAt(3000, 0),
            sprinklerAt(8000, 0)
        );

        List<ValidationVerdict> results = validator.validateBatch(requests);
        assertTrue(results.stream().anyMatch(v -> v.isBlocked() || v.isWarning()),
                "Should detect NN violation for head at 8000");
    }

    @Test
    @Order(41)
    @DisplayName("W-DV-BATCH-1: validateBatch detects clearance violation")
    void validateBatch_clearanceViolation() {
        // Rule: CENTRELINE_CLEARANCE, min_clearance=150mm between ELEC and SP
        PlacementValidatorImpl validator = validatorWithRules(List.of(
            ruleWithCheckMethod(603, "CENTRELINE_CLEARANCE",
                Map.of("min_clearance_mm", 150.0),
                Map.of("discipline_a", "ELEC", "discipline_b", "SP"))
        ));

        // ELEC conduit at (1000,1000), SP pipe at (1080,1000)
        // clearance = 80 - 25 - 25 = 30mm < 150mm
        var requests = List.of(
            new PlacementRequest("CONDUIT", "IfcFlowSegment", "ELEC",
                50, 50, 3000, 1000, 1000, 2500, "ROUTE", 0, "GF"),
            new PlacementRequest("PIPE", "IfcPipeSegment", "SP",
                50, 50, 3000, 1080, 1000, 2500, "ROUTE", 0, "GF")
        );

        List<ValidationVerdict> results = validator.validateBatch(requests);
        assertTrue(results.stream().anyMatch(v -> v.isBlocked() || v.isWarning()),
                "Should detect clearance violation between ELEC and SP");
    }

    @Test
    @Order(42)
    @DisplayName("W-DV-BATCH-1: validateBatch with compliant spacing → empty results")
    void validateBatch_compliantSpacing() {
        PlacementValidatorImpl validator = validatorWithRules(List.of(
            ruleWithCheckMethod(601, "NN_CENTROID_DISTANCE",
                Map.of("min_spacing_mm", 1800.0, "max_spacing_mm", 4600.0),
                Map.of("ifc_class", "IfcFireSuppressionTerminal"))
        ));

        // 3 heads at 3500mm spacing — within [1800, 4600]
        var requests = List.of(
            sprinklerAt(0, 0),
            sprinklerAt(3500, 0),
            sprinklerAt(7000, 0)
        );

        List<ValidationVerdict> results = validator.validateBatch(requests);
        assertTrue(results.stream().noneMatch(v -> v.isBlocked() || v.isWarning()),
                "Compliant spacing should have no violations");
    }

    // ── Non-Disturbance: RosettaStones use {prefix}_BOM, not DocEvent ─

    @Test
    @Order(50)
    @DisplayName("Batch spatial checks only fire for DocEvent rules, not threshold rules")
    void batchSkipsThresholdRules() {
        // Mix of threshold rule (no check_method) and batch rule
        PlacementValidatorImpl validator = validatorWithRules(List.of(
            thresholdRule(101, "BEDROOM", "min_area_m2", 9.0),
            ruleWithCheckMethod(601, "NN_CENTROID_DISTANCE",
                Map.of("max_spacing_mm", 4600.0),
                Map.of("ifc_class", "IfcFireSuppressionTerminal"))
        ));

        // Only rooms, no sprinklers → batch should find no spatial rules to check
        var rooms = List.of(roomAt("BEDROOM", 4000, 4000, 2800));
        List<ValidationVerdict> batchResults = validator.validateBatch(rooms);
        assertTrue(batchResults.isEmpty(),
                "Batch should not fire threshold rules (rooms have no IfcFireSuppressionTerminal)");
    }

    // ── InferenceEngine: check_method dispatch for batch methods ─────

    @Test
    @Order(60)
    @DisplayName("InferenceEngine: Batch check_methods PASS in single-element evaluation")
    void inferenceEngine_batchMethodsPassSingleElement() {
        InferenceEngine engine = new InferenceEngine();
        CachedRuleExt batchRule = new CachedRuleExt(
                601, "SprinklerSpacing", "NFPA 13 §8.6",
                Map.of("max_spacing_mm", 4600.0),
                Map.of("check_method", "NN_CENTROID_DISTANCE",
                       "ifc_class", "IfcFireSuppressionTerminal"),
                0);

        PlacementRequest req = sprinklerAt(0, 0);
        List<RuleResult> results = engine.evaluate(List.of(batchRule), req);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isPassed(),
                "Batch method should PASS in single-element evaluation");
    }

    @Test
    @Order(61)
    @DisplayName("InferenceEngine: M_PRODUCT_CROSS_SECTION dispatches correctly")
    void inferenceEngine_crossSectionDispatch() {
        InferenceEngine engine = new InferenceEngine();
        CachedRuleExt rule = new CachedRuleExt(
                100, "PipeDiameter", "M3",
                Map.of("min_main_diameter_mm", 50.0),
                Map.of("check_method", "M_PRODUCT_CROSS_SECTION"),
                0);

        // 30mm pipe → BLOCK
        PlacementRequest small = pipeAt(0, 0, 30);
        List<RuleResult> results = engine.evaluate(List.of(rule), small);
        assertEquals(1, results.size());
        assertTrue(results.get(0).isBlocked(), "30mm should be blocked by 50mm min");

        // 80mm pipe → PASS
        PlacementRequest big = pipeAt(0, 0, 80);
        results = engine.evaluate(List.of(rule), big);
        assertEquals(1, results.size());
        assertTrue(results.get(0).isPassed(), "80mm should pass 50mm min");
    }

    // ── Helpers: synthetic PlacementRequest builders ─────────────────

    private static PlacementRequest sprinklerAt(double x, double y) {
        return new PlacementRequest(
                "FP", "IfcFireSuppressionTerminal", "FP",
                30, 30, 50,
                x, y, 2800,
                "PLACE", 0, "GF");
    }

    private static PlacementRequest pipeAt(double x, double y, double diameter) {
        return new PlacementRequest(
                "PIPE", "IfcPipeSegment", "CW",
                diameter, diameter, 3000,
                x, y, 2500,
                "ROUTE", 0, "GF");
    }

    private static PlacementRequest lightAt(double x, double y, double z) {
        return new PlacementRequest(
                "ELEC", "IfcLightFixture", "ELEC",
                300, 300, 50,
                x, y, z,
                "PLACE", 0, "GF");
    }

    private static PlacementRequest roomAt(String category, double w, double d, double h) {
        return new PlacementRequest(
                category, "IfcSpace", "ARC",
                w, d, h,
                0, 0, 0,
                "SNAP", 0, "GF");
    }

    // ── Helpers: rule builders ───────────────────────────────────────

    /** Threshold-only rule (no check_method) — backward compatible. */
    private static PlacementValidatorImpl.CachedRule thresholdRule(
            int id, String category, String paramName, double value) {
        return new PlacementValidatorImpl.CachedRule(
                id, "Rule_" + paramName, "TEST",
                Map.of(paramName, value),
                Map.of("bom_category", category),
                0);
    }

    /** Rule with check_method dispatch. */
    private static PlacementValidatorImpl.CachedRule ruleWithCheckMethod(
            int id, String checkMethod,
            Map<String, Double> thresholds,
            Map<String, String> extraParams) {
        Map<String, String> params = new HashMap<>(extraParams);
        params.put("check_method", checkMethod);
        return new PlacementValidatorImpl.CachedRule(
                id, "Rule_" + checkMethod, "TEST",
                thresholds, params, 0);
    }

    /** M_PRODUCT_CROSS_SECTION rule. */
    private static PlacementValidatorImpl.CachedRule crossSectionRule(
            int id, String paramName, double minDiameter) {
        return ruleWithCheckMethod(id, "M_PRODUCT_CROSS_SECTION",
                Map.of(paramName, minDiameter), Map.of());
    }

    /** Rule with verdict=WARN override. */
    private static PlacementValidatorImpl.CachedRule warnRule(
            int id, String paramName, double value) {
        return new PlacementValidatorImpl.CachedRule(
                id, "Advisory_" + paramName, "ADVISORY",
                Map.of(paramName, value),
                Map.of("verdict", "WARN"),
                0);
    }

    /**
     * Create a validator pre-loaded with test rules (no DB needed).
     * Uses reflection-free approach: rules go into the "*" wildcard bucket
     * and category-specific bucket based on stringParams.
     */
    private static PlacementValidatorImpl validatorWithRules(
            List<PlacementValidatorImpl.CachedRule> rules) {
        PlacementValidatorImpl v = new PlacementValidatorImpl();

        // Build rulesByCategory map manually for testing
        Map<String, List<PlacementValidatorImpl.CachedRule>> map = new java.util.HashMap<>();
        for (PlacementValidatorImpl.CachedRule rule : rules) {
            String category = rule.stringParams().getOrDefault("bom_category", "*");
            map.computeIfAbsent(category, k -> new java.util.ArrayList<>()).add(rule);
        }

        // Inject via package-private setter
        v.setRulesForTest(map);
        return v;
    }
}
