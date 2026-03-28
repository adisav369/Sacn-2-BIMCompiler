package com.bim.backoffice.federation;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NLP Query Parser — ported from dataintelligence/nlp/query_parser.py +
 * query_patterns.py.
 *
 * <p>Converts natural language queries about building elements into SQL
 * against the BOM database. Pattern-based: regex extracts parameters,
 * templates generate SQL.
 *
 * <p>Categories (from query_patterns.py):
 * element_count, manufacturer, property_search, quantity, cost,
 * material, discipline, freetext
 *
 * // Porting query_parser.py + query_patterns.py — Witness: W-NLP-PARSE-1
 */
public class NlpQueryParser {

    // ── Result record ───────────────────────────────────────────────

    public record ParseResult(boolean success, String sql, Map<String, String> params,
                              String category, String description, String error) {
        static ParseResult fail(String error) {
            return new ParseResult(false, null, Map.of(), null, null, error);
        }
    }

    // ── Pattern definition ──────────────────────────────────────────

    private record QueryPattern(String category, Pattern regex, String sqlTemplate,
                                String descTemplate, String[] paramNames) {}

    private final List<QueryPattern> patterns = new ArrayList<>();

    // ── Element type synonyms (from _generate_sql singularization) ──

    private static final Map<String, List<String>> ELEMENT_SYNONYMS = Map.ofEntries(
            Map.entry("beam",      List.of("beam", "member")),
            Map.entry("frame",     List.of("member", "frame")),
            Map.entry("lintel",    List.of("member", "lintel")),
            Map.entry("sprinkler", List.of("firesuppressionterminal", "sprinkler")),
            Map.entry("diffuser",  List.of("airterminal", "diffuser")),
            Map.entry("light",     List.of("lightfixture", "light")),
            Map.entry("switch",    List.of("switchingdevice", "switch")),
            Map.entry("outlet",    List.of("outlet")),
            Map.entry("furniture", List.of("furniture", "furnishing"))
    );

    // ── Storey mappings (from STOREY_MAPPINGS) ──────────────────────

    private static final Map<String, List<String>> STOREY_MAPPINGS = Map.ofEntries(
            Map.entry("first",    List.of("01", "1", "first")),
            Map.entry("second",   List.of("02", "2", "second")),
            Map.entry("third",    List.of("03", "3", "third")),
            Map.entry("fourth",   List.of("04", "4", "fourth")),
            Map.entry("ground",   List.of("00", "0", "ground", "tanah", "jalan")),
            Map.entry("roof",     List.of("roof", "bumbung")),
            Map.entry("basement", List.of("basement", "b1", "b01")),
            Map.entry("tanah",    List.of("tanah", "ground", "0", "00")),
            Map.entry("bumbung",  List.of("bumbung", "roof")),
            Map.entry("jalan",    List.of("jalan", "ground", "street")),
            Map.entry("kedai",    List.of("kedai", "shop", "retail")),
            Map.entry("0",        List.of("0", "00", "ground")),
            Map.entry("1",        List.of("1", "01", "first")),
            Map.entry("2",        List.of("2", "02", "second")),
            Map.entry("3",        List.of("3", "03", "third"))
    );

    // ── Constructor — register all patterns ─────────────────────────

    public NlpQueryParser() {
        registerPatterns();
    }

    private void registerPatterns() {
        // ── Element count patterns (highest priority) ──────────
        // "How many beams on level 1?"
        addPattern("element_count",
                "(?:how many|count|total|number of) (\\w+) (?:on|in|at) (?:the )?(?:level|storey|floor|aras) ([\\w\\s]+)",
                "SELECT COUNT(*) AS count FROM m_bom_line bl " +
                "JOIN m_bom b ON bl.M_BOM_ID = b.M_BOM_ID " +
                "WHERE bl.is_active = 1 AND {element_filter} AND {storey_filter}",
                "Count of {element_type} on {storey_name}",
                new String[]{"element_type", "storey_name"});

        // "How many doors?"
        addPattern("element_count",
                "(?:how many|count|total|number of) (\\w+)",
                "SELECT COUNT(*) AS count FROM m_bom_line bl " +
                "JOIN m_bom b ON bl.M_BOM_ID = b.M_BOM_ID " +
                "WHERE bl.is_active = 1 AND {element_filter}",
                "Count of {element_type}",
                new String[]{"element_type"});

        // ── Cost patterns ──────────────────────────────────────
        // "Total building cost"
        addPattern("cost",
                "(?:total|what is|what's).{0,30}(?:cost|price|budget)",
                "SELECT SUM(bl.qty) AS total_qty, COUNT(*) AS line_count " +
                "FROM m_bom_line bl WHERE bl.is_active = 1",
                "Total building cost summary",
                new String[]{});

        // "Cost of ARC discipline"
        addPattern("cost",
                "(?:cost|price) (?:of |for )?(\\w+) (?:discipline|system|work)",
                "SELECT b.m_product_category_id AS discipline, COUNT(*) AS count, SUM(bl.qty) AS total_qty " +
                "FROM m_bom_line bl JOIN m_bom b ON bl.M_BOM_ID = b.M_BOM_ID " +
                "WHERE bl.is_active = 1 AND UPPER(b.m_product_category_id) = UPPER('{discipline}') " +
                "GROUP BY discipline",
                "Cost of {discipline} discipline",
                new String[]{"discipline"});

        // ── Discipline patterns ────────────────────────────────
        // "Show ACMV elements"
        addPattern("discipline",
                "(?:show|list) (\\w+) (?:elements|items)",
                "SELECT bl.child_product_id, bl.qty FROM m_bom_line bl " +
                "JOIN m_bom b ON bl.M_BOM_ID = b.M_BOM_ID " +
                "WHERE bl.is_active = 1 AND UPPER(b.m_product_category_id) = UPPER('{discipline}')",
                "Elements in {discipline} discipline",
                new String[]{"discipline"});

        // "What disciplines are there?"
        addPattern("discipline",
                "(?:what|which) disciplines (?:are|exist)",
                "SELECT m_product_category_id AS discipline, COUNT(*) AS bom_count " +
                "FROM m_bom WHERE is_active = 1 GROUP BY m_product_category_id",
                "Discipline breakdown",
                new String[]{});

        // ── Quantity patterns ──────────────────────────────────
        // "Total area of slabs"
        addPattern("quantity",
                "(?:total|sum) (?:area|volume|length) of (\\w+)",
                "SELECT SUM(bl.qty) AS total, COUNT(*) AS count FROM m_bom_line bl " +
                "JOIN m_bom b ON bl.M_BOM_ID = b.M_BOM_ID " +
                "WHERE bl.is_active = 1 AND {element_filter}",
                "Total quantity of {element_type}",
                new String[]{"element_type"});

        // ── Freetext patterns (lowest priority) ────────────────
        // "Search for beam"
        addPattern("freetext",
                "(?:search for|find) ([\\w\\s]+)",
                "SELECT bl.child_product_id, bl.qty, b.bom_name FROM m_bom_line bl " +
                "JOIN m_bom b ON bl.M_BOM_ID = b.M_BOM_ID " +
                "WHERE bl.is_active = 1 AND LOWER(bl.child_product_id) LIKE '%{search_term}%'",
                "Search results for '{search_term}'",
                new String[]{"search_term"});
    }

    private void addPattern(String category, String regex, String sqlTemplate,
                            String descTemplate, String[] paramNames) {
        patterns.add(new QueryPattern(category,
                Pattern.compile(regex, Pattern.CASE_INSENSITIVE),
                sqlTemplate, descTemplate, paramNames));
    }

    // ── Parse ───────────────────────────────────────────────────────

    /**
     * Parse a natural language query into SQL.
     * Ported from NLPQueryParser.parse().
     */
    public ParseResult parse(String query) {
        if (query == null || query.isBlank()) {
            return ParseResult.fail("Empty query");
        }

        // Clean query: trim, remove trailing punctuation
        String cleaned = query.strip().replaceAll("[?!.]+$", "").strip();

        for (QueryPattern pattern : patterns) {
            Matcher m = pattern.regex.matcher(cleaned);
            if (m.find()) {
                // Extract parameters
                Map<String, String> params = new LinkedHashMap<>();
                for (int i = 0; i < pattern.paramNames.length && i < m.groupCount(); i++) {
                    params.put(pattern.paramNames[i], sanitize(m.group(i + 1).strip()));
                }

                // Generate SQL from template
                String sql = generateSql(pattern.sqlTemplate, params);
                String desc = generateDesc(pattern.descTemplate, params);

                return new ParseResult(true, sql, params, pattern.category, desc, null);
            }
        }

        return ParseResult.fail("No matching pattern for: " + cleaned);
    }

    // ── SQL generation (from _generate_sql) ─────────────────────────

    private String generateSql(String template, Map<String, String> params) {
        String sql = template;

        for (var entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if ("element_type".equals(key)) {
                sql = sql.replace("{element_filter}", buildElementFilter(value));
            } else if ("storey_name".equals(key)) {
                sql = sql.replace("{storey_filter}", buildStoreyFilter(value));
            } else {
                sql = sql.replace("{" + key + "}", value);
            }
        }

        // Clean up any unreplaced placeholders
        sql = sql.replace("{element_filter}", "1=1");
        sql = sql.replace("{storey_filter}", "1=1");

        return sql;
    }

    private String generateDesc(String template, Map<String, String> params) {
        String desc = template;
        for (var entry : params.entrySet()) {
            desc = desc.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return desc;
    }

    /**
     * Build element type filter with synonym expansion.
     * Ported from _generate_sql element type handling.
     */
    private String buildElementFilter(String elementType) {
        // Singularize: remove trailing 's'
        String singular = elementType.toLowerCase();
        if (singular.endsWith("s") && singular.length() > 3) {
            singular = singular.substring(0, singular.length() - 1);
        }

        // Look up synonyms
        List<String> synonyms = ELEMENT_SYNONYMS.get(singular);
        if (synonyms == null) synonyms = List.of(singular);

        // Build OR clause
        List<String> clauses = new ArrayList<>();
        for (String syn : synonyms) {
            clauses.add("LOWER(bl.child_product_id) LIKE '%" + syn + "%'");
        }
        return "(" + String.join(" OR ", clauses) + ")";
    }

    /**
     * Build storey filter with multi-language normalization.
     * Ported from normalize_storey_name().
     */
    private String buildStoreyFilter(String storeyName) {
        // Remove ordinal suffixes
        String normalized = storeyName.toLowerCase().strip()
                .replaceAll("(\\d+)(?:st|nd|rd|th)\\b", "$1");

        List<String> alternatives = null;
        for (var entry : STOREY_MAPPINGS.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                alternatives = entry.getValue();
                break;
            }
        }

        if (alternatives != null && !alternatives.isEmpty()) {
            List<String> clauses = new ArrayList<>();
            for (String alt : alternatives) {
                clauses.add("LOWER(b.bom_name) LIKE '%" + alt + "%'");
            }
            return "(" + String.join(" OR ", clauses) + ")";
        }

        return "LOWER(b.bom_name) LIKE '%" + normalized + "%'";
    }

    // ── Sanitization (from _sanitize_value) ─────────────────────────

    /** Remove non-alphanumeric chars except space, dash, underscore, period. */
    private static String sanitize(String value) {
        return value.replaceAll("[^\\w\\s\\-.]", "");
    }
}
