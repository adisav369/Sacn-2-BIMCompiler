package com.bim.designer.validation;

import com.bim.orm.BIMLogger;

import java.util.*;

/**
 * Inference engine for dependency-ordered rule evaluation.
 * Stage 1 (G-6): topological sort + SKIP on failed dependency + proof trace.
 *
 * <p>Uses Kahn's algorithm for topological sort. Rules with circular
 * dependencies are detected and rejected with SKIP.
 *
 * // Implementing BIM_Designer_SRS.md §19 — Witness: W-INF-DEP-1
 */
public class InferenceEngine {

    private static final String TAG = "InferenceEngine";

    // ── Result types ──────────────────────────────────────────────────

    /** Outcome for a single rule evaluation. */
    public record RuleResult(
            int ruleId,
            String ruleName,
            String standardRef,
            Result result,
            double actualValue,
            double requiredValue,
            int dependsOn,
            String skipReason
    ) {
        public enum Result { PASS, WARN, BLOCK, SKIP }

        public boolean isSkipped() { return result == Result.SKIP; }
        public boolean isBlocked() { return result == Result.BLOCK; }
        public boolean isWarning() { return result == Result.WARN; }
        public boolean isPassed()  { return result == Result.PASS; }
    }

    /** Proof tree: conclusion + ordered proof nodes. */
    public record ProofTree(String conclusion, List<ProofNode> nodes) {}

    /** A node in the proof tree — mirrors RuleResult with children for display. */
    public record ProofNode(
            int ruleId,
            String ruleName,
            RuleResult.Result result,
            String citation,
            List<ProofNode> children
    ) {}

    /** Extended cached rule with dependency information. */
    public record CachedRuleExt(
            int ruleId,
            String name,
            String standardRef,
            Map<String, Double> thresholds,
            Map<String, String> stringParams,
            int dependsOn   // 0 = root (no dependency)
    ) {
        /** Backward-compatible constructor — no stringParams. */
        public CachedRuleExt(int ruleId, String name, String standardRef,
                      Map<String, Double> thresholds, int dependsOn) {
            this(ruleId, name, standardRef, thresholds, Map.of(), dependsOn);
        }

        /** @return the check_method if present, else null */
        public String checkMethod() {
            return stringParams.get("check_method");
        }

        /** @return the verdict override if present (WARN vs BLOCK), else null */
        public String verdictOverride() {
            return stringParams.get("verdict");
        }
    }

    // ── Evaluation ────────────────────────────────────────────────────

    /**
     * Evaluate rules in dependency order using Kahn's topological sort.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Build adjacency list from depends_on edges</li>
     *   <li>Find roots (rules with no depends_on)</li>
     *   <li>BFS: process roots first, then dependents</li>
     *   <li>For each rule: if depends_on rule FAILED/SKIPPED → SKIP;
     *       else evaluate against request thresholds</li>
     *   <li>Circular dependencies → all involved rules SKIP</li>
     * </ol>
     *
     * @param rules   loaded rules with dependency info
     * @param request the placement to validate
     * @return all rule results in evaluation order
     */
    public List<RuleResult> evaluate(List<CachedRuleExt> rules, PlacementRequest request) {
        if (rules == null || rules.isEmpty()) return List.of();

        BIMLogger.fine(TAG, "Evaluating {} rules for category={}", rules.size(),
                request != null ? request.bomCategory() : "null");

        // Index rules by ID for quick lookup
        Map<Integer, CachedRuleExt> ruleById = new LinkedHashMap<>();
        for (CachedRuleExt r : rules) {
            ruleById.put(r.ruleId(), r);
        }

        // Build adjacency: parent → children
        Map<Integer, List<Integer>> children = new HashMap<>();
        // In-degree count for Kahn's
        Map<Integer, Integer> inDegree = new HashMap<>();

        for (CachedRuleExt r : rules) {
            inDegree.putIfAbsent(r.ruleId(), 0);
            children.putIfAbsent(r.ruleId(), new ArrayList<>());

            if (r.dependsOn() > 0 && ruleById.containsKey(r.dependsOn())) {
                children.computeIfAbsent(r.dependsOn(), k -> new ArrayList<>()).add(r.ruleId());
                inDegree.merge(r.ruleId(), 1, Integer::sum);
            }
        }

        // Kahn's: seed queue with roots (in-degree 0)
        Queue<Integer> queue = new ArrayDeque<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        // Results cache
        Map<Integer, RuleResult> resultCache = new LinkedHashMap<>();
        List<RuleResult> ordered = new ArrayList<>();
        int processed = 0;

        while (!queue.isEmpty()) {
            int ruleId = queue.poll();
            processed++;
            CachedRuleExt rule = ruleById.get(ruleId);

            RuleResult result;
            if (rule.dependsOn() > 0) {
                RuleResult parentResult = resultCache.get(rule.dependsOn());
                if (parentResult != null && (parentResult.isBlocked() || parentResult.isSkipped())) {
                    // Dependency failed or was skipped → SKIP this rule
                    String reason = String.format("Dependency rule %d (%s) %s",
                            rule.dependsOn(),
                            parentResult.ruleName(),
                            parentResult.isBlocked() ? "BLOCKED" : "SKIPPED");
                    result = new RuleResult(
                            rule.ruleId(), rule.name(), rule.standardRef(),
                            RuleResult.Result.SKIP, 0, 0,
                            rule.dependsOn(), reason);
                    BIMLogger.fine(TAG, "SKIP rule {} ({}): {}", rule.ruleId(), rule.name(), reason);
                } else {
                    // Dependency passed → evaluate normally
                    result = evaluateRule(rule, request);
                }
            } else {
                // Root rule — evaluate directly
                result = evaluateRule(rule, request);
            }

            resultCache.put(ruleId, result);
            ordered.add(result);

            // Enqueue children (decrease in-degree)
            for (int childId : children.getOrDefault(ruleId, List.of())) {
                int newDegree = inDegree.merge(childId, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(childId);
                }
            }
        }

        // Detect cycles: any rule not processed has a circular dependency
        if (processed < rules.size()) {
            BIMLogger.warn(TAG, "Circular dependency detected: {} rules unreachable",
                    rules.size() - processed);
            for (CachedRuleExt r : rules) {
                if (!resultCache.containsKey(r.ruleId())) {
                    RuleResult cycleResult = new RuleResult(
                            r.ruleId(), r.name(), r.standardRef(),
                            RuleResult.Result.SKIP, 0, 0,
                            r.dependsOn(),
                            "Circular dependency detected — rule unreachable");
                    resultCache.put(r.ruleId(), cycleResult);
                    ordered.add(cycleResult);
                }
            }
        }

        BIMLogger.info(TAG, "Evaluation complete: {} rules, {} PASS, {} WARN, {} BLOCK, {} SKIP",
                ordered.size(),
                ordered.stream().filter(RuleResult::isPassed).count(),
                ordered.stream().filter(RuleResult::isWarning).count(),
                ordered.stream().filter(RuleResult::isBlocked).count(),
                ordered.stream().filter(RuleResult::isSkipped).count());

        return ordered;
    }

    /**
     * Evaluate a single rule against the placement request.
     * DV-F-01: check_method dispatch first, then threshold fallback.
     * DV-F-11: verdict override (WARN vs BLOCK).
     */
    private RuleResult evaluateRule(CachedRuleExt rule, PlacementRequest request) {
        // DV-F-01: check_method dispatch
        String checkMethod = rule.checkMethod();
        if (checkMethod != null) {
            // Batch methods (NN_CENTROID_DISTANCE, etc.) → PASS in single-element mode
            // Single-element methods evaluated here
            RuleResult methodResult = dispatchCheckMethod(rule, request);
            if (methodResult != null) return methodResult;
            // null = not applicable to single-element → PASS
            return passResult(rule);
        }

        // Fallback: threshold comparison
        for (var entry : rule.thresholds().entrySet()) {
            String paramName = entry.getKey();
            double required = entry.getValue();

            Double actual = extractActual(request, paramName);
            if (actual == null) continue;

            if (actual < required) {
                return failResult(rule, paramName, actual, required);
            }
        }

        return passResult(rule);
    }

    /**
     * Dispatch check_method for single-element evaluation.
     * Returns null for batch methods (deferred to PlacementValidatorImpl.validateBatch).
     */
    private RuleResult dispatchCheckMethod(CachedRuleExt rule, PlacementRequest request) {
        String method = rule.checkMethod();
        return switch (method) {
            case "M_PRODUCT_CROSS_SECTION" -> {
                double crossSection = Math.min(request.widthMm(), request.depthMm());
                Double minDiam = rule.thresholds().get("min_main_diameter_mm");
                if (minDiam != null && crossSection < minDiam) {
                    yield failResult(rule, "min_main_diameter_mm", crossSection, minDiam);
                }
                Double minBranch = rule.thresholds().get("min_branch_diameter_mm");
                if (minBranch != null && crossSection < minBranch) {
                    yield failResult(rule, "min_branch_diameter_mm", crossSection, minBranch);
                }
                yield null;  // PASS
            }
            // Batch methods — PASS in single-element context
            case "NN_CENTROID_DISTANCE", "CENTRELINE_CLEARANCE",
                 "ROUTE_SEGMENT_SUM", "PER_STOREY_Z_CONSISTENCY",
                 "BEAM_LENGTH_VS_BAY", "CENTROID_DEPTH_VS_HOST_CENTER",
                 "AABB_PROXIMITY", "TILE_VERB_FIDELITY" -> null;
            default -> {
                BIMLogger.warn(TAG, "Unknown check_method '{}' in rule {} — skipping",
                        method, rule.name());
                yield null;
            }
        };
    }

    private RuleResult passResult(CachedRuleExt rule) {
        BIMLogger.fine(TAG, "PASS rule {} ({})", rule.ruleId(), rule.name());
        return new RuleResult(
                rule.ruleId(), rule.name(), rule.standardRef(),
                RuleResult.Result.PASS, 0, 0,
                rule.dependsOn(), null);
    }

    private RuleResult failResult(CachedRuleExt rule, String paramName,
                                   double actual, double required) {
        // DV-F-11: verdict override
        RuleResult.Result verdict = "WARN".equals(rule.verdictOverride())
                ? RuleResult.Result.WARN
                : RuleResult.Result.BLOCK;
        String label = verdict == RuleResult.Result.WARN ? "WARN" : "BLOCK";
        BIMLogger.fine(TAG, "{} rule {} ({}): {}={:.1f} < {:.1f}",
                label, rule.ruleId(), rule.name(), paramName, actual, required);
        return new RuleResult(
                rule.ruleId(), rule.name(), rule.standardRef(),
                verdict, actual, required,
                rule.dependsOn(),
                String.format("%s: %.1f < required %.1f (%s)",
                        paramName, actual, required, rule.standardRef()));
    }

    /**
     * Maps a rule param name to the corresponding request value.
     * Mirrors PlacementValidatorImpl.extractActual().
     */
    private Double extractActual(PlacementRequest req, String paramName) {
        return switch (paramName) {
            case "min_area_m2"   -> req.areaSqM();
            case "min_dim_mm"    -> req.minDimMm();
            case "min_height_mm" -> req.heightMm();
            case "min_width_mm"  -> req.widthMm();
            default -> null;
        };
    }

    // ── Proof tree ────────────────────────────────────────────────────

    /**
     * Build proof tree from evaluation results.
     * Root nodes are rules with no dependency; children hang off their parent.
     */
    public ProofTree buildProofTree(List<RuleResult> results) {
        if (results == null || results.isEmpty()) {
            return new ProofTree("No rules evaluated", List.of());
        }

        // Index results by ruleId
        Map<Integer, RuleResult> byId = new LinkedHashMap<>();
        for (RuleResult r : results) {
            byId.put(r.ruleId(), r);
        }

        // Build parent → children map
        Map<Integer, List<RuleResult>> childrenOf = new HashMap<>();
        List<RuleResult> roots = new ArrayList<>();

        for (RuleResult r : results) {
            if (r.dependsOn() <= 0 || !byId.containsKey(r.dependsOn())) {
                roots.add(r);
            } else {
                childrenOf.computeIfAbsent(r.dependsOn(), k -> new ArrayList<>()).add(r);
            }
        }

        // Recursively build nodes
        List<ProofNode> rootNodes = new ArrayList<>();
        for (RuleResult root : roots) {
            rootNodes.add(buildNode(root, childrenOf));
        }

        // Conclusion: WARN is not a failure, only BLOCK counts
        long blockCount = results.stream().filter(RuleResult::isBlocked).count();
        long warnCount = results.stream().filter(RuleResult::isWarning).count();
        String conclusion;
        if (blockCount > 0) {
            conclusion = blockCount + " rule(s) blocked";
        } else if (warnCount > 0) {
            conclusion = "All rules passed (" + warnCount + " warning(s))";
        } else {
            conclusion = "All rules passed";
        }

        return new ProofTree(conclusion, rootNodes);
    }

    private ProofNode buildNode(RuleResult result, Map<Integer, List<RuleResult>> childrenOf) {
        List<ProofNode> kids = new ArrayList<>();
        for (RuleResult child : childrenOf.getOrDefault(result.ruleId(), List.of())) {
            kids.add(buildNode(child, childrenOf));
        }

        String citation = result.standardRef() != null
                ? result.standardRef()
                : "no-citation";

        return new ProofNode(result.ruleId(), result.ruleName(), result.result(), citation, kids);
    }
}
