package com.bim.cobol;

import com.bim.ormsandbox.po.M_W_Verb_Node;
import com.bim.ormsandbox.po.M_W_Verb_NodeProduct;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Persists BIM COBOL verb results to W_Verb_Node + W_Verb_NodeProduct rows.
 *
 * <p>FIRST PRINCIPLE (§11.9): W_Verb_Node = HOW. Each verb invocation becomes
 * one W_Verb_Node row (the operation), and its structured parameters become
 * W_Verb_NodeProduct child rows (the material/parameter inputs).
 *
 * <p>Typical flow:
 * <pre>
 *   ScriptRunner runner = new ScriptRunner(VerbRegistry.createDefault());
 *   ScriptReport report = runner.run(ctx, script);
 *   VerbNodePersister persister = new VerbNodePersister(outputConn, buildingId);
 *   persister.persist(scriptLines, report);
 * </pre>
 *
 * <p>Transaction ownership: caller must commit. This class does not call commit().
 */
public class VerbNodePersister {

    private final Connection conn;
    private final String buildingId;

    public VerbNodePersister(Connection conn, String buildingId) {
        this.conn = conn;
        this.buildingId = buildingId;
    }

    /**
     * Persist all verb results from a script execution.
     *
     * @param scriptLines the raw verb lines (in execution order, comments stripped)
     * @param report      the ScriptRunner report containing per-line VerbResults
     * @return number of W_Verb_Node rows created
     */
    public int persist(List<String> scriptLines, ScriptRunner.ScriptReport report)
            throws SQLException {
        List<VerbResult<?>> results = report.results();
        int count = 0;
        int seqNo = 10;

        for (int i = 0; i < results.size(); i++) {
            VerbResult<?> result = results.get(i);
            String line = i < scriptLines.size() ? scriptLines.get(i) : result.verb();

            M_W_Verb_Node node = new M_W_Verb_Node(conn);
            node.setC_Order_ID(buildingId);
            node.setSeqNo(seqNo);
            node.setName(result.verb());
            node.setDescription(line);
            node.setDocStatus(result.pass() ? "CO" : "VO");
            node.setLastResult(result.toJson());
            node.setElementCount(extractElementCount(result));
            node.save();

            seqNo += 10;
            count++;
        }
        return count;
    }

    /**
     * Persist a single verb result with structured parameters.
     *
     * @param seqNo  sequence number for this verb invocation
     * @param line   the raw verb line
     * @param result the verb execution result
     * @param params structured parameters as name→value pairs (nullable)
     * @return the W_Verb_Node_ID of the created row
     */
    public int persistOne(int seqNo, String line, VerbResult<?> result,
                          Map<String, String> params) throws SQLException {
        M_W_Verb_Node node = new M_W_Verb_Node(conn);
        node.setC_Order_ID(buildingId);
        node.setSeqNo(seqNo);
        node.setName(result.verb());
        node.setDescription(line);
        node.setDocStatus(result.pass() ? "CO" : "VO");
        node.setLastResult(result.toJson());
        node.setElementCount(extractElementCount(result));
        node.save();

        int nodeId = node.getW_Verb_Node_ID();

        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                M_W_Verb_NodeProduct param = new M_W_Verb_NodeProduct(conn);
                param.setW_Verb_Node_ID(nodeId);
                param.setName(entry.getKey());
                param.setValue(entry.getValue());
                param.setValueType(inferType(entry.getValue()));
                param.save();
            }
        }

        return nodeId;
    }

    /**
     * Infer ValueType from a string value.
     * INTEGER if parseable as int, REAL if parseable as double, TEXT otherwise.
     */
    static String inferType(String value) {
        if (value == null) return "TEXT";
        try {
            Integer.parseInt(value);
            return "INTEGER";
        } catch (NumberFormatException e1) {
            try {
                Double.parseDouble(value);
                return "REAL";
            } catch (NumberFormatException e2) {
                return "TEXT";
            }
        }
    }

    /**
     * Extract element_count from a VerbResult payload.
     * Looks for common count patterns in known payload types.
     */
    private static int extractElementCount(VerbResult<?> result) {
        Object payload = result.payload();
        if (payload == null) return 0;

        // Use reflection-free approach: check known payload types by their toString/JSON
        // For now, return 0 — individual verbs can override via persistOne + params
        try {
            // Try common field names via Gson serialization
            String json = result.toJson();
            // Quick parse for "totalCount":N or "count":N or "fixtureCount":N
            int count = extractIntField(json, "totalCount");
            if (count > 0) return count;
            count = extractIntField(json, "fixtureCount");
            if (count > 0) return count;
            count = extractIntField(json, "count");
            if (count > 0) return count;
        } catch (Exception e) {
            System.err.println("[WARN] VerbNodePersister: " + e.getMessage());
        }
        return 0;
    }

    /** Quick JSON field extraction: "fieldName":123 → 123. */
    private static int extractIntField(String json, String fieldName) {
        String key = "\"" + fieldName + "\":";
        int idx = json.indexOf(key);
        if (idx < 0) return 0;
        int start = idx + key.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end == start) return 0;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
