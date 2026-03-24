package com.bim.designer.dao;

import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.*;

/**
 * Evaluates AD_Rule callout chain in dependency order (Kahn's topological sort).
 *
 * <p>iDempiere parallel: AD_Column.Callout — a field change fires declarative
 * rules that propagate consequences. Same pattern as InferenceEngine but for
 * spatial mutations rather than validation.
 *
 * // Implementing ProjectOrderBlueprint.md §9.3 — Witness: W-DIFF-1
 */
public class CalloutEngine {

    private static final String TAG = "CalloutEngine";

    private final Connection conn;

    public CalloutEngine(Connection conn) {
        this.conn = conn;
    }

    /**
     * Result of evaluating one callout rule.
     *
     * @param ruleId     AD_Rule PK
     * @param ruleName   human-readable name
     * @param ruleType   POSITIONAL / DIMENSIONAL / CONSTRAINT / REROUTE
     * @param expression the action expression (e.g. ROLLUP_AABB)
     * @param fired      true if the rule was evaluated (not skipped)
     * @param message    description of what happened
     */
    public record CalloutResult(int ruleId, String ruleName, String ruleType,
                                String expression, boolean fired, String message) {}

    /**
     * Fire all active AD_Rule rows matching the given source table + column,
     * evaluated in dependency order (Kahn's topological sort).
     *
     * @param sourceTable  e.g. "C_OrderLine"
     * @param sourceColumn e.g. "dx"
     * @return ordered list of callout results
     */
    public List<CalloutResult> fire(String sourceTable, String sourceColumn)
            throws SQLException {
        List<RuleRow> rules = loadRules(sourceTable, sourceColumn);
        if (rules.isEmpty()) return List.of();

        // Topological sort (Kahn's algorithm)
        List<RuleRow> sorted = topoSort(rules);

        List<CalloutResult> results = new ArrayList<>();
        Set<Integer> firedIds = new HashSet<>();

        for (RuleRow rule : sorted) {
            // Check dependency: if depends_on exists and didn't fire, skip
            if (rule.dependsOn > 0 && !firedIds.contains(rule.dependsOn)) {
                results.add(new CalloutResult(rule.id, rule.name, rule.ruleType,
                        rule.expression, false, "Skipped: dependency " + rule.dependsOn + " did not fire"));
                continue;
            }

            // Evaluate the rule expression
            String message = evaluate(rule);
            firedIds.add(rule.id);
            results.add(new CalloutResult(rule.id, rule.name, rule.ruleType,
                    rule.expression, true, message));

            BIMLogger.info(TAG, "Callout fired: {} [{}] → {}",
                    rule.name, rule.ruleType, message);
        }

        return results;
    }

    /**
     * Evaluate a single rule expression. Currently supports:
     * - ROLLUP_AABB: room AABB recalculation (marker — actual recalc done by caller)
     *
     * Returns a human-readable description of what was done.
     */
    private String evaluate(RuleRow rule) {
        return switch (rule.expression) {
            case "ROLLUP_AABB" -> "Room AABB recalculation triggered";
            default -> "Expression evaluated: " + rule.expression;
        };
    }

    // ── Internals ────────────────────────────────────────────────────

    private record RuleRow(int id, String name, String ruleType,
                           String targetLocator, String expression,
                           int dependsOn, int seqNo) {}

    private List<RuleRow> loadRules(String sourceTable, String sourceColumn)
            throws SQLException {
        String sql = """
                SELECT ad_rule_id, name, rule_type, target_locator,
                       expression, COALESCE(depends_on, 0), seq_no
                FROM AD_Rule
                WHERE source_table = ? AND source_column = ? AND is_active = 1
                ORDER BY seq_no
                """;
        List<RuleRow> rules = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sourceTable);
            ps.setString(2, sourceColumn);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rules.add(new RuleRow(
                            rs.getInt(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5),
                            rs.getInt(6), rs.getInt(7)));
                }
            }
        }
        return rules;
    }

    /**
     * Kahn's topological sort on depends_on edges.
     * Rules with no dependency come first. Cycle → skip all involved.
     */
    private List<RuleRow> topoSort(List<RuleRow> rules) {
        Map<Integer, RuleRow> byId = new LinkedHashMap<>();
        Map<Integer, Integer> inDegree = new LinkedHashMap<>();
        Map<Integer, List<Integer>> children = new HashMap<>();

        for (RuleRow r : rules) {
            byId.put(r.id, r);
            inDegree.put(r.id, 0);
            children.putIfAbsent(r.id, new ArrayList<>());
        }

        for (RuleRow r : rules) {
            if (r.dependsOn > 0 && byId.containsKey(r.dependsOn)) {
                inDegree.merge(r.id, 1, Integer::sum);
                children.computeIfAbsent(r.dependsOn, k -> new ArrayList<>()).add(r.id);
            }
        }

        // BFS from roots (in-degree 0)
        Queue<Integer> queue = new ArrayDeque<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<RuleRow> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            int id = queue.poll();
            sorted.add(byId.get(id));
            for (int childId : children.getOrDefault(id, List.of())) {
                int newDeg = inDegree.merge(childId, -1, Integer::sum);
                if (newDeg == 0) queue.add(childId);
            }
        }

        // Any remaining (cycle) — append with warning
        if (sorted.size() < rules.size()) {
            for (RuleRow r : rules) {
                if (!sorted.contains(r)) {
                    BIMLogger.warn(TAG, "Cycle detected involving rule: {}", r.name);
                    sorted.add(r);
                }
            }
        }

        return sorted;
    }
}
