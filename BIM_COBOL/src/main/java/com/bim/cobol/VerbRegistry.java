package com.bim.cobol;

import com.bim.cobol.verb.*;

import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central dispatch map: keyword → Verb.
 *
 * <p>Keywords are multi-word (e.g. "CHECK BOM", "COVER WITH COMPOUND_ROOF").
 * Dispatch uses longest-prefix match: sort keywords by word count descending,
 * first match wins. Remaining tokens become verb args.
 *
 * <p>Quoted strings in the line are preserved as single tokens:
 * {@code WIRE LIGHTING TB_LKTN "Ground Floor" bilik_utama}
 * → keyword="WIRE LIGHTING", args=["TB_LKTN", "Ground Floor", "bilik_utama"]
 */
public class VerbRegistry {

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("\"([^\"]*)\"|\\S+");

    private final Map<String, Verb<?>> verbs = new LinkedHashMap<>();

    /** Sorted keyword list — longest (most words) first for greedy match. */
    private List<String> sortedKeywords = List.of();

    /** Register a verb by its keyword(). */
    public void register(Verb<?> verb) {
        verbs.put(verb.keyword(), verb);
        rebuildSortedKeywords();
    }

    /** All registered keywords, sorted alphabetically. */
    public List<String> keywords() {
        List<String> keys = new ArrayList<>(verbs.keySet());
        Collections.sort(keys);
        return Collections.unmodifiableList(keys);
    }

    /** Number of registered verbs. */
    public int size() { return verbs.size(); }

    /**
     * Parse a single line and dispatch to the matching verb.
     *
     * @return VerbResult from the matched verb, or a fail result if no match / error
     */
    public VerbResult<?> dispatch(VerbContext ctx, String line) {
        if (line == null || line.isBlank())
            return VerbResult.fail("DISPATCH", "empty line", null);

        String trimmed = line.trim();
        String upper = trimmed.toUpperCase();

        // Find longest keyword prefix
        for (String kw : sortedKeywords) {
            if (upper.startsWith(kw)) {
                // Verify word boundary: line must be exactly the keyword or keyword + space
                if (upper.length() == kw.length()
                        || upper.charAt(kw.length()) == ' ') {
                    Verb<?> verb = verbs.get(kw);
                    String rest = trimmed.substring(kw.length()).trim();
                    String[] args = tokenize(rest);
                    try {
                        return verb.execute(ctx, args);
                    } catch (SQLException e) {
                        return VerbResult.fail(kw,
                                "SQL error: " + e.getMessage(), null);
                    }
                }
            }
        }

        return VerbResult.fail("DISPATCH",
                "unknown keyword: " + trimmed, null);
    }

    /**
     * Static factory that registers all 10 built-in verbs.
     */
    public static VerbRegistry createDefault() {
        VerbRegistry reg = new VerbRegistry();
        reg.register(new CheckBomVerb());
        reg.register(new CoverWithRoofVerb());
        reg.register(new RouteSprinklersVerb());
        reg.register(new ConnectFittingsVerb());
        reg.register(new CheckPlacementVerb());
        reg.register(new CheckClashVerb());
        reg.register(new CheckRoomVerb());
        reg.register(new CheckComplianceVerb());
        reg.register(new WireLightingVerb());
        reg.register(new VerifyPlacementVerb());
        return reg;
    }

    // ── internals ────────────────────────────────────────────────

    private void rebuildSortedKeywords() {
        sortedKeywords = new ArrayList<>(verbs.keySet());
        // Sort by word count descending → longest prefix wins
        sortedKeywords.sort((a, b) -> {
            int wa = a.split("\\s+").length;
            int wb = b.split("\\s+").length;
            return Integer.compare(wb, wa);
        });
        sortedKeywords = Collections.unmodifiableList(sortedKeywords);
    }

    /**
     * Tokenize args: split on whitespace, preserve "quoted strings".
     */
    static String[] tokenize(String input) {
        if (input == null || input.isBlank()) return new String[0];
        List<String> tokens = new ArrayList<>();
        Matcher m = TOKEN_PATTERN.matcher(input);
        while (m.find()) {
            if (m.group(1) != null) {
                tokens.add(m.group(1));  // quoted content without quotes
            } else {
                tokens.add(m.group());
            }
        }
        return tokens.toArray(new String[0]);
    }
}
