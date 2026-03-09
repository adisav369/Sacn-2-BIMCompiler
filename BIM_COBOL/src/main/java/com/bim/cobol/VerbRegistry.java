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
     * Static factory that registers all 14 built-in verbs.
     */
    public static VerbRegistry createDefault() {
        VerbRegistry reg = new VerbRegistry();
        reg.register(new EnBlocVerb());
        reg.register(new WalkThruVerb());
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
        reg.register(new TileSurfaceVerb());
        reg.register(new ArrayVerb());
        reg.register(new PlaceBomVerb());
        // Phase F0.x: data handling verbs
        reg.register(new SelectBomVerb());
        reg.register(new CloneBomVerb());
        reg.register(new ExportBomVerb());
        reg.register(new AggregateBomVerb());
        reg.register(new SummarizeBuildingVerb());
        reg.register(new ListBomVerb());
        reg.register(new DescribeBomVerb());
        reg.register(new CountBomVerb());
        // Phase F0.2 P0: synthetic BOM primitives (§18.4)
        reg.register(new CreateBomVerb());
        reg.register(new AddLineVerb());
        reg.register(new SetTackVerb());
        reg.register(new SetRotationVerb());
        reg.register(new SetDimensionsVerb());
        reg.register(new RemoveLineVerb());
        reg.register(new DeleteBomVerb());
        reg.register(new SetLinePropertyVerb());
        // Phase F0.2 P0: utility verbs (§18.5)
        reg.register(new ExtractAabbVerb());
        reg.register(new RollupAabbVerb());
        reg.register(new SnapToGridVerb());
        reg.register(new ValidateAabbVerb());
        // Phase F0.2 P1: Level 1 convenience verbs (§18.6)
        reg.register(new CreateRoomVerb());
        reg.register(new FurnishRoomVerb());
        reg.register(new ResizeRoomVerb());
        reg.register(new StripRoomVerb());
        // Phase H0: report verbs (§19) — XLSX output via Apache POI
        reg.register(new ReportBomCatalogVerb());
        reg.register(new ReportProductCatalogVerb());
        reg.register(new ReportBomStructureVerb());
        // Phase L2: utility + floor-level verbs (§18.7)
        reg.register(new PartitionAabbVerb());
        reg.register(new CreateFloorVerb());
        reg.register(new AddRoomVerb());
        reg.register(new RemoveRoomVerb());
        reg.register(new SwapRoomVerb());
        // Phase L3: building-level verbs (§18.8)
        reg.register(new ComposeBuildingVerb());
        reg.register(new AddFloorVerb());
        reg.register(new StackFloorsVerb());
        // Phase L4: catalog-level verbs (§18.9)
        reg.register(new DefineCategoryVerb());
        reg.register(new AddTemplateRuleVerb());
        reg.register(new RegisterBomVerb());
        // HELLO WORLD — permanent dual-path proof
        reg.register(new HelloWorldVerb());
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
