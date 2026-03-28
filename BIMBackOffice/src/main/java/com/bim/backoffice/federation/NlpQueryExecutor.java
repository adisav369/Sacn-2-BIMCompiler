package com.bim.backoffice.federation;

import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.*;

/**
 * NLP Query Executor — ported from dataintelligence/nlp/query_executor.py.
 *
 * <p>Executes parsed SQL queries against a BOM database and formats results
 * with friendly labels. Provides the same result structure as the Python
 * QueryExecutor: rows as dicts, column names, timing, row count.
 *
 * // Porting query_executor.py — Witness: W-NLP-EXEC-1
 */
public class NlpQueryExecutor {

    private static final String TAG = "NlpQueryExecutor";

    // ── Result record ───────────────────────────────────────────────

    public record QueryResult(boolean success, List<Map<String, Object>> rows,
                              int rowCount, List<String> columns,
                              double elapsedMs, String error) {
        static QueryResult fail(String error) {
            return new QueryResult(false, List.of(), 0, List.of(), 0, error);
        }
    }

    // ── Formatted result (for display) ──────────────────────────────

    public record FormattedResult(boolean success, String category, String description,
                                  String sql, int rowCount, double elapsedMs,
                                  List<Map<String, Object>> rows, String formatted) {}

    // ── Execute ─────────────────────────────────────────────────────

    /**
     * Execute a SQL query against a BOM database connection.
     * Ported from QueryExecutor.execute().
     */
    public QueryResult execute(Connection conn, String sql) {
        long start = System.nanoTime();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                columns.add(meta.getColumnLabel(i));
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(columns.get(i - 1), rs.getObject(i));
                }
                rows.add(row);
            }

            double elapsed = (System.nanoTime() - start) / 1_000_000.0;
            return new QueryResult(true, rows, rows.size(), columns, elapsed, null);

        } catch (SQLException e) {
            double elapsed = (System.nanoTime() - start) / 1_000_000.0;
            BIMLogger.warn(TAG, "Query failed: {}", e.getMessage());
            return new QueryResult(false, List.of(), 0, List.of(), elapsed, e.getMessage());
        }
    }

    /**
     * Execute with automatic LIMIT.
     * Ported from QueryExecutor.execute_with_limit().
     */
    public QueryResult executeWithLimit(Connection conn, String sql, int limit) {
        if (!sql.toUpperCase().contains("LIMIT")) {
            sql = sql + " LIMIT " + limit;
        }
        return execute(conn, sql);
    }

    // ── Full NLP pipeline: parse → execute → format ─────────────────

    /**
     * Run full NLP pipeline: parse query, execute SQL, format results.
     * Combines NLPQueryParser + QueryExecutor + result formatting.
     */
    public FormattedResult query(Connection conn, String naturalLanguageQuery) {
        // Parse
        NlpQueryParser parser = new NlpQueryParser();
        NlpQueryParser.ParseResult parsed = parser.parse(naturalLanguageQuery);

        if (!parsed.success()) {
            return new FormattedResult(false, null, null, null, 0, 0,
                    List.of(), "No matching pattern for query: " + naturalLanguageQuery);
        }

        // Execute
        QueryResult result = executeWithLimit(conn, parsed.sql(), 100);

        if (!result.success()) {
            return new FormattedResult(false, parsed.category(), parsed.description(),
                    parsed.sql(), 0, result.elapsedMs(), List.of(),
                    "SQL Error: " + result.error());
        }

        // Format
        String formatted = formatResults(result, parsed);

        return new FormattedResult(true, parsed.category(), parsed.description(),
                parsed.sql(), result.rowCount(), result.elapsedMs(),
                result.rows(), formatted);
    }

    // ── Result formatting (from _format_results in operator.py) ─────

    /**
     * Format query results with friendly labels.
     * Ported from BIM_OT_execute_nlp_query._format_results().
     */
    String formatResults(QueryResult result, NlpQueryParser.ParseResult parsed) {
        StringBuilder sb = new StringBuilder();
        sb.append(parsed.description()).append('\n');
        sb.append(String.format("%.2fms | %d rows%n%n", result.elapsedMs(), result.rowCount()));

        if (result.rowCount() == 0) {
            sb.append("No results found.");
            return sb.toString();
        }

        String category = parsed.category();
        List<Map<String, Object>> rows = result.rows();
        int maxRows = Math.min(rows.size(), 15);

        switch (category) {
            case "element_count" -> {
                for (int i = 0; i < maxRows; i++) {
                    var row = rows.get(i);
                    Object ifcClass = row.get("ifc_class");
                    Object count = row.getOrDefault("count", row.get("total"));
                    if (ifcClass != null) {
                        sb.append("  ").append(IfcLabelMapper.getFriendlyLabel(ifcClass.toString()))
                          .append(": ").append(count).append('\n');
                    } else {
                        sb.append("  Count: ").append(count).append('\n');
                    }
                }
            }
            case "discipline" -> {
                for (int i = 0; i < maxRows; i++) {
                    var row = rows.get(i);
                    Object disc = row.getOrDefault("discipline", row.get("Discipline"));
                    Object count = row.getOrDefault("bom_count",
                            row.getOrDefault("count", row.get("element_count")));
                    if (disc != null) {
                        sb.append("  ").append(IfcLabelMapper.getDisciplineLabel(disc.toString()))
                          .append(": ").append(count).append('\n');
                    }
                }
            }
            case "cost" -> {
                for (int i = 0; i < maxRows; i++) {
                    var row = rows.get(i);
                    Object totalCost = row.get("total_cost_rm");
                    Object totalQty = row.get("total_qty");
                    if (totalCost != null) {
                        sb.append(String.format("  Total Cost: RM %,.2f%n",
                                ((Number) totalCost).doubleValue()));
                    } else if (totalQty != null) {
                        sb.append(String.format("  Total Qty: %s%n", totalQty));
                    }
                    Object lineCount = row.get("line_count");
                    if (lineCount != null) {
                        sb.append(String.format("  Lines: %s%n", lineCount));
                    }
                }
            }
            case "freetext" -> {
                for (int i = 0; i < maxRows; i++) {
                    var row = rows.get(i);
                    Object pid = row.get("child_product_id");
                    Object qty = row.get("qty");
                    Object bomName = row.get("bom_name");
                    sb.append("  • ").append(pid);
                    if (qty != null) sb.append(" (qty: ").append(qty).append(")");
                    if (bomName != null) sb.append(" in ").append(bomName);
                    sb.append('\n');
                }
            }
            default -> {
                // Generic table
                for (int i = 0; i < maxRows; i++) {
                    var row = rows.get(i);
                    sb.append("  ");
                    row.forEach((k, v) -> sb.append(k).append(": ").append(v).append(" | "));
                    sb.append('\n');
                }
            }
        }

        if (result.rowCount() > 15) {
            sb.append(String.format("%n... and %d more rows%n", result.rowCount() - 15));
        }

        return sb.toString();
    }
}
