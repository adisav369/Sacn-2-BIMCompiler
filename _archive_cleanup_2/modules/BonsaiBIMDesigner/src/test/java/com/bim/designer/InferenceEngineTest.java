package com.bim.designer;

import com.bim.designer.validation.*;
import com.bim.designer.validation.InferenceEngine.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InferenceEngine tests — dependency-ordered rule evaluation.
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-INF-DEP-1: Rule with failed dependency → SKIP</li>
 *   <li>W-INF-TOPO-1: Rules evaluated parent-before-child</li>
 *   <li>W-INF-CYCLE-1: Circular dependency detected and rejected</li>
 *   <li>W-INF-CACHE-1: Re-evaluation with no change < 10ms</li>
 *   <li>W-APPROVE-1: approve with all PASS → AP</li>
 *   <li>W-APPROVE-2: approve with failed rule → blockers list</li>
 * </ul>
 */
@DisplayName("InferenceEngine -- dependency-ordered rule evaluation")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InferenceEngineTest {

    private InferenceEngine engine;

    @BeforeEach
    void setUp() {
        engine = new InferenceEngine();
    }

    // ── Helper: create a passing request (large room) ────────────────

    private static PlacementRequest passingRoom() {
        return new PlacementRequest(
                "BEDROOM", "IfcSpace", "ARC",
                4000, 4000, 3000,   // 16 m2, min dim 4000
                0, 0, 0,
                "TEST", 0, "GF");
    }

    private static PlacementRequest failingRoom() {
        // 2000x2000 = 4.0 m2, min dim 2000 — fails any rule requiring > 3000mm or > 9 m2
        return new PlacementRequest(
                "BEDROOM", "IfcSpace", "ARC",
                2000, 2000, 2000,
                0, 0, 0,
                "TEST", 0, "GF");
    }

    // ── Helper: build test rules ─────────────────────────────────────

    /** Rule A: root, requires min_dim_mm >= 3000 */
    private static CachedRuleExt ruleA() {
        return new CachedRuleExt(
                1, "RuleA_MinDim", "UBBL-2012-s33",
                Map.of("min_dim_mm", 3000.0),
                0); // root — no dependency
    }

    /** Rule B: depends on A, requires min_area_m2 >= 9.0 */
    private static CachedRuleExt ruleB() {
        return new CachedRuleExt(
                2, "RuleB_MinArea", "UBBL-2012-s33(2)",
                Map.of("min_area_m2", 9.0),
                1); // depends on rule A
    }

    /** Rule C: depends on B, requires min_height_mm >= 2800 */
    private static CachedRuleExt ruleC() {
        return new CachedRuleExt(
                3, "RuleC_MinHeight", "UBBL-2012-s33(3)",
                Map.of("min_height_mm", 2800.0),
                2); // depends on rule B (depth 2)
    }

    // ── W-INF-TOPO-1: Rules evaluated parent-before-child ────────────

    @Test
    @Order(1)
    @DisplayName("W-INF-TOPO-1: Rules evaluated in topological order (parent before child)")
    void w_inf_topo_1_parent_before_child() {
        // Give rules in reverse order — engine must still evaluate A → B → C
        List<CachedRuleExt> rules = List.of(ruleC(), ruleB(), ruleA());
        PlacementRequest req = passingRoom();

        List<RuleResult> results = engine.evaluate(rules, req);

        assertEquals(3, results.size(), "Should have 3 results");

        // Find positions by ruleId
        int posA = -1, posB = -1, posC = -1;
        for (int i = 0; i < results.size(); i++) {
            switch (results.get(i).ruleId()) {
                case 1 -> posA = i;
                case 2 -> posB = i;
                case 3 -> posC = i;
            }
        }

        assertTrue(posA >= 0 && posB >= 0 && posC >= 0,
                "All three rules should be in results");
        assertTrue(posA < posB, "Rule A must be evaluated before Rule B");
        assertTrue(posB < posC, "Rule B must be evaluated before Rule C");
    }

    // ── W-INF-DEP-1: Rule with failed dependency → SKIP ─────────────

    @Test
    @Order(2)
    @DisplayName("W-INF-DEP-1: Failed root → downstream rules SKIP")
    void w_inf_dep_1_failed_dependency_skips() {
        List<CachedRuleExt> rules = List.of(ruleA(), ruleB(), ruleC());
        PlacementRequest req = failingRoom(); // fails ruleA (min_dim 2000 < 3000)

        List<RuleResult> results = engine.evaluate(rules, req);

        assertEquals(3, results.size(), "Should have 3 results");

        // Rule A should BLOCK
        RuleResult resA = findResult(results, 1);
        assertEquals(RuleResult.Result.BLOCK, resA.result(),
                "Rule A should BLOCK: min_dim 2000 < 3000");

        // Rule B should SKIP (depends on A which blocked)
        RuleResult resB = findResult(results, 2);
        assertEquals(RuleResult.Result.SKIP, resB.result(),
                "Rule B should SKIP: dependency A blocked");
        assertNotNull(resB.skipReason());
        assertTrue(resB.skipReason().contains("BLOCKED"),
                "Skip reason should mention BLOCKED: " + resB.skipReason());

        // Rule C should SKIP (depends on B which was skipped)
        RuleResult resC = findResult(results, 3);
        assertEquals(RuleResult.Result.SKIP, resC.result(),
                "Rule C should SKIP: dependency B was skipped");
        assertNotNull(resC.skipReason());
        assertTrue(resC.skipReason().contains("SKIPPED"),
                "Skip reason should mention SKIPPED: " + resC.skipReason());
    }

    // ── All PASS scenario ────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("All rules PASS with compliant room")
    void all_rules_pass() {
        List<CachedRuleExt> rules = List.of(ruleA(), ruleB(), ruleC());
        PlacementRequest req = passingRoom();

        List<RuleResult> results = engine.evaluate(rules, req);

        assertEquals(3, results.size());
        for (RuleResult r : results) {
            assertEquals(RuleResult.Result.PASS, r.result(),
                    "Rule " + r.ruleName() + " should PASS");
        }
    }

    // ── W-INF-CYCLE-1: Circular dependency detected ──────────────────

    @Test
    @Order(4)
    @DisplayName("W-INF-CYCLE-1: Circular dependency → SKIP with cycle reason")
    void w_inf_cycle_1_circular_dependency() {
        // Rule X depends on Y, Y depends on X → cycle
        CachedRuleExt ruleX = new CachedRuleExt(
                10, "RuleX", "TEST-CYCLE",
                Map.of("min_dim_mm", 1000.0),
                11); // depends on Y
        CachedRuleExt ruleY = new CachedRuleExt(
                11, "RuleY", "TEST-CYCLE",
                Map.of("min_dim_mm", 1000.0),
                10); // depends on X

        List<RuleResult> results = engine.evaluate(List.of(ruleX, ruleY), passingRoom());

        // Both should be SKIP due to circular dependency
        assertEquals(2, results.size(), "Should have 2 results");
        for (RuleResult r : results) {
            assertEquals(RuleResult.Result.SKIP, r.result(),
                    "Rule " + r.ruleName() + " should SKIP due to cycle");
            assertNotNull(r.skipReason());
            assertTrue(r.skipReason().toLowerCase().contains("circular"),
                    "Skip reason should mention circular: " + r.skipReason());
        }
    }

    // ── W-INF-CACHE-1: Re-evaluation performance ─────────────────────

    @Test
    @Order(5)
    @DisplayName("W-INF-CACHE-1: Re-evaluation with same input < 10ms")
    void w_inf_cache_1_reevaluation_fast() {
        List<CachedRuleExt> rules = List.of(ruleA(), ruleB(), ruleC());
        PlacementRequest req = passingRoom();

        // Warm up
        engine.evaluate(rules, req);

        // Timed run
        long t0 = System.nanoTime();
        List<RuleResult> results = engine.evaluate(rules, req);
        long elapsed = (System.nanoTime() - t0) / 1_000_000;

        assertEquals(3, results.size());
        assertTrue(elapsed < 10,
                "Re-evaluation should complete in < 10ms, took " + elapsed + "ms");
    }

    // ── Proof tree tests ─────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("ProofTree builds correct hierarchy from results")
    void proof_tree_structure() {
        List<CachedRuleExt> rules = List.of(ruleA(), ruleB(), ruleC());
        List<RuleResult> results = engine.evaluate(rules, passingRoom());

        ProofTree tree = engine.buildProofTree(results);

        assertNotNull(tree);
        assertEquals("All rules passed", tree.conclusion());
        // Root nodes: only ruleA (ruleB and ruleC are children)
        assertEquals(1, tree.nodes().size(), "Only root node A at top level");
        ProofNode nodeA = tree.nodes().get(0);
        assertEquals(1, nodeA.ruleId());
        assertEquals(RuleResult.Result.PASS, nodeA.result());

        // nodeA should have child B
        assertEquals(1, nodeA.children().size(), "Rule A should have 1 child (B)");
        ProofNode nodeB = nodeA.children().get(0);
        assertEquals(2, nodeB.ruleId());

        // nodeB should have child C
        assertEquals(1, nodeB.children().size(), "Rule B should have 1 child (C)");
        ProofNode nodeC = nodeB.children().get(0);
        assertEquals(3, nodeC.ruleId());
        assertTrue(nodeC.children().isEmpty(), "Rule C is a leaf");
    }

    @Test
    @Order(11)
    @DisplayName("ProofTree with blocked rule reports block count")
    void proof_tree_blocked() {
        List<CachedRuleExt> rules = List.of(ruleA(), ruleB(), ruleC());
        List<RuleResult> results = engine.evaluate(rules, failingRoom());

        ProofTree tree = engine.buildProofTree(results);

        assertNotNull(tree);
        assertTrue(tree.conclusion().contains("1 rule(s) blocked"),
                "Conclusion should report 1 blocked rule: " + tree.conclusion());
    }

    // ── W-APPROVE-1 / W-APPROVE-2: Approve gate integration ─────────

    @Test
    @Order(20)
    @DisplayName("W-APPROVE-1: All PASS → AP status (via InferenceEngine)")
    void w_approve_1_all_pass() {
        List<CachedRuleExt> rules = List.of(ruleA(), ruleB(), ruleC());
        List<RuleResult> results = engine.evaluate(rules, passingRoom());

        // Simulate approve logic: check for any BLOCK
        long blockCount = results.stream().filter(RuleResult::isBlocked).count();
        long passCount = results.stream().filter(RuleResult::isPassed).count();

        assertEquals(0, blockCount, "No blockers for compliant room");
        assertEquals(3, passCount, "All 3 rules should pass");

        // Status determination (mirrors DesignerAPIImpl.approve())
        String status = blockCount == 0 ? "AP" : "BLOCKED";
        assertEquals("AP", status);
    }

    @Test
    @Order(21)
    @DisplayName("W-APPROVE-2: Failed rule → BLOCKED status with blockers")
    void w_approve_2_blocked() {
        List<CachedRuleExt> rules = List.of(ruleA(), ruleB(), ruleC());
        List<RuleResult> results = engine.evaluate(rules, failingRoom());

        // Check blockers
        long blockCount = results.stream().filter(RuleResult::isBlocked).count();
        assertTrue(blockCount > 0, "Should have at least one blocker");

        RuleResult blocked = results.stream()
                .filter(RuleResult::isBlocked)
                .findFirst().orElseThrow();
        assertEquals("RuleA_MinDim", blocked.ruleName());
        assertEquals(2000.0, blocked.actualValue(), 0.1);
        assertEquals(3000.0, blocked.requiredValue(), 0.1);

        String status = blockCount == 0 ? "AP" : "BLOCKED";
        assertEquals("BLOCKED", status);
    }

    // ── Edge cases ───────────────────────────────────────────────────

    @Test
    @Order(30)
    @DisplayName("Empty rules list returns empty results")
    void empty_rules() {
        List<RuleResult> results = engine.evaluate(List.of(), passingRoom());
        assertTrue(results.isEmpty());
    }

    @Test
    @Order(31)
    @DisplayName("Single root rule with no dependencies")
    void single_root_rule() {
        List<RuleResult> results = engine.evaluate(List.of(ruleA()), passingRoom());
        assertEquals(1, results.size());
        assertEquals(RuleResult.Result.PASS, results.get(0).result());
    }

    @Test
    @Order(32)
    @DisplayName("Rule with depends_on pointing to non-existent rule treated as root")
    void orphan_dependency() {
        // Rule with depends_on = 999 (doesn't exist in rule set) → treated as root
        CachedRuleExt orphan = new CachedRuleExt(
                5, "OrphanRule", "TEST",
                Map.of("min_dim_mm", 1000.0),
                999); // depends on non-existent rule

        List<RuleResult> results = engine.evaluate(List.of(orphan), passingRoom());
        assertEquals(1, results.size());
        assertEquals(RuleResult.Result.PASS, results.get(0).result(),
                "Orphan dependency should be treated as root and evaluated normally");
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static RuleResult findResult(List<RuleResult> results, int ruleId) {
        return results.stream()
                .filter(r -> r.ruleId() == ruleId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No result for ruleId=" + ruleId));
    }

    // Inner class to avoid typo in AssertionError
    private static class AssertionError extends RuntimeException {
        AssertionError(String msg) { super(msg); }
    }
}
