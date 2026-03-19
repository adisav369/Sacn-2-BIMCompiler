package com.bim.designer.validation;

import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * PlacementValidatorImpl -- loads AD_Val_Rule + AD_Val_Rule_Param per jurisdiction,
 * caches rules in memory, validates PlacementRequest against them.
 *
 * <p>Rules are indexed by bom_category for O(1) lookup. Rules without a
 * bom_category param apply to ALL categories (stored under key "*").
 *
 * <p>Only COMPLIANCE rules with min/max threshold params are evaluated here.
 * CLASH and CLEARANCE rules require spatial queries and are out of scope.
 */
public class PlacementValidatorImpl implements PlacementValidator {

    private static final String TAG = "PlacementValidator";

    /** Cached rule with its threshold parameters. */
    record CachedRule(
            int ruleId,
            String name,
            String standardRef,
            Map<String, Double> thresholds,   // param_name -> numeric value
            Map<String, String> stringParams,  // non-numeric params (check_method, ifc_class, verdict)
            int dependsOn                     // FK to parent rule, 0 = root
    ) {
        /** Backward-compatible constructor — no stringParams. */
        CachedRule(int ruleId, String name, String standardRef,
                   Map<String, Double> thresholds, int dependsOn) {
            this(ruleId, name, standardRef, thresholds, Map.of(), dependsOn);
        }

        /** @return the check_method if present, else null */
        String checkMethod() {
            return stringParams.get("check_method");
        }

        /** @return the verdict override if present (WARN vs BLOCK), else null */
        String verdictOverride() {
            return stringParams.get("verdict");
        }
    }

    /** Wildcard key for rules that apply to all categories. */
    private static final String WILDCARD = "*";

    /** Rules keyed by bom_category (or "*" for any-category rules). */
    private Map<String, List<CachedRule>> rulesByCategory = Map.of();

    private boolean active;
    private String jurisdiction;

    // ── Interface implementation ─────────────────────────────────────

    @Override
    public void activate(String jurisdiction, Connection valConn) {
        Objects.requireNonNull(jurisdiction, "jurisdiction");
        Objects.requireNonNull(valConn, "valConn");

        this.jurisdiction = jurisdiction;
        this.rulesByCategory = loadRules(jurisdiction, valConn);
        this.active = true;
    }

    @Override
    public void deactivate() {
        this.active = false;
        this.jurisdiction = null;
        this.rulesByCategory = Map.of();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public String getJurisdiction() {
        return jurisdiction;
    }

    @Override
    public ValidationVerdict validate(PlacementRequest request) {
        if (!active) {
            return ValidationVerdict.pass();
        }

        // Check category-specific rules first, then wildcard rules
        // Return first BLOCK immediately; collect worst WARN otherwise
        ValidationVerdict worst = ValidationVerdict.pass();

        ValidationVerdict v = checkCategory(request, request.bomCategory());
        if (v.isBlocked()) return v;
        if (v.isWarning()) worst = v;

        v = checkCategory(request, WILDCARD);
        if (v.isBlocked()) return v;
        if (v.isWarning() && !worst.isWarning()) worst = v;

        return worst;
    }

    // ── Rule loading ─────────────────────────────────────────────────

    /**
     * Loads all active COMPLIANCE rules for a jurisdiction.
     * Groups them by bom_category (or "*" if no category filter).
     */
    private Map<String, List<CachedRule>> loadRules(String jurisdiction, Connection conn) {
        Map<String, List<CachedRule>> result = new HashMap<>();

        // Step 1: load all active COMPLIANCE rules for this jurisdiction
        String ruleSQL =
                "SELECT ad_val_rule_id, name, standard_ref, "
              + "COALESCE(depends_on, 0) "
              + "FROM AD_Val_Rule "
              + "WHERE jurisdiction = ? AND is_active = 1 AND rule_type = 'COMPLIANCE'";

        try (PreparedStatement ps = conn.prepareStatement(ruleSQL)) {
            ps.setString(1, jurisdiction);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int ruleId = rs.getInt(1);
                    String name = rs.getString(2);
                    String stdRef = rs.getString(3);
                    int dependsOn = rs.getInt(4);

                    // Step 2: load params for this rule
                    Map<String, String> allParams = loadParams(conn, ruleId);

                    // Step 3: separate bom_category, thresholds, and string params
                    String category = allParams.getOrDefault("bom_category", WILDCARD);
                    Map<String, Double> thresholds = new HashMap<>();
                    Map<String, String> stringParams = new HashMap<>();
                    for (var e : allParams.entrySet()) {
                        if ("bom_category".equals(e.getKey())) continue;
                        if (isNumeric(e.getValue())) {
                            thresholds.put(e.getKey(), Double.parseDouble(e.getValue()));
                        } else {
                            stringParams.put(e.getKey(), e.getValue());
                        }
                    }

                    if (!thresholds.isEmpty() || !stringParams.isEmpty()) {
                        CachedRule rule = new CachedRule(ruleId, name, stdRef,
                                thresholds, stringParams, dependsOn);
                        result.computeIfAbsent(category, k -> new ArrayList<>()).add(rule);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load validation rules for " + jurisdiction, e);
        }

        return result;
    }

    private Map<String, String> loadParams(Connection conn, int ruleId) throws SQLException {
        Map<String, String> params = new LinkedHashMap<>();
        String sql = "SELECT name, value FROM AD_Val_Rule_Param WHERE ad_val_rule_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ruleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    params.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return params;
    }

    // ── Validation logic ─────────────────────────────────────────────

    private ValidationVerdict checkCategory(PlacementRequest req, String categoryKey) {
        List<CachedRule> rules = rulesByCategory.get(categoryKey);
        if (rules == null) return ValidationVerdict.pass();

        for (CachedRule rule : rules) {
            // DV-F-01: check_method dispatch — if present, use named evaluator
            String checkMethod = rule.checkMethod();
            if (checkMethod != null) {
                ValidationVerdict v = dispatchCheckMethod(checkMethod, rule, req);
                if (v != null && (v.isBlocked() || v.isWarning())) return v;
                continue;  // check_method handled this rule entirely
            }

            // Fallback: threshold comparison (existing logic)
            for (var entry : rule.thresholds.entrySet()) {
                String paramName = entry.getKey();
                double required = entry.getValue();

                Double actual = extractActual(req, paramName);
                if (actual == null) continue; // param not applicable to this request type

                if (actual < required) {
                    return makeVerdict(rule, paramName, actual, required);
                }
            }
        }
        return ValidationVerdict.pass();
    }

    // ── check_method dispatch (DV-F-01) ──────────────────────────────

    /**
     * Dispatch to named check_method evaluator.
     * Returns null if the check_method is not applicable to single-element validation
     * (batch methods like NN_CENTROID_DISTANCE are handled by validateBatch).
     *
     * // Implementing DocAction_SRS §3.1 — Witness: W-DV-CHECK-DISPATCH
     */
    private ValidationVerdict dispatchCheckMethod(String method, CachedRule rule, PlacementRequest req) {
        return switch (method) {
            // P0: Single-element check methods
            case "M_PRODUCT_CROSS_SECTION" -> checkProductCrossSection(rule, req);

            // P1: Batch methods — skip in single-element validation (handled in validateBatch)
            case "NN_CENTROID_DISTANCE",
                 "CENTRELINE_CLEARANCE",
                 "ROUTE_SEGMENT_SUM",
                 "PER_STOREY_Z_CONSISTENCY",
                 "BEAM_LENGTH_VS_BAY",
                 "CENTROID_DEPTH_VS_HOST_CENTER",
                 "AABB_PROXIMITY",
                 "TILE_VERB_FIDELITY" -> null;  // deferred to batch validation

            default -> {
                // DV-E-02: unknown check_method → log WARN, skip
                BIMLogger.warn(TAG, "Unknown check_method '{}' in rule {} — skipping",
                        method, rule.name);
                yield null;
            }
        };
    }

    /**
     * DV-F-05: M_PRODUCT_CROSS_SECTION — validate pipe/conduit diameter.
     * cross_section = MIN(width, depth) from PlacementRequest product dimensions.
     */
    private ValidationVerdict checkProductCrossSection(CachedRule rule, PlacementRequest req) {
        double crossSection = Math.min(req.widthMm(), req.depthMm());
        Double minDiameter = rule.thresholds.get("min_main_diameter_mm");
        if (minDiameter != null && crossSection < minDiameter) {
            return makeVerdict(rule, "min_main_diameter_mm", crossSection, minDiameter);
        }
        Double minBranch = rule.thresholds.get("min_branch_diameter_mm");
        if (minBranch != null && crossSection < minBranch) {
            return makeVerdict(rule, "min_branch_diameter_mm", crossSection, minBranch);
        }
        return null;  // PASS (no violation)
    }

    /**
     * Make verdict respecting verdict override (WARN vs BLOCK).
     * DV-F-11: Rules with verdict=WARN param never return BLOCK.
     */
    private ValidationVerdict makeVerdict(CachedRule rule, String paramName,
                                           double actual, double required) {
        String message = String.format("%s: %.1f < required %.1f (%s)",
                paramName, actual, required, rule.standardRef);
        if ("WARN".equals(rule.verdictOverride())) {
            return ValidationVerdict.warn(rule.name, rule.standardRef, actual, required, message);
        }
        return ValidationVerdict.block(rule.name, rule.standardRef, actual, required, message);
    }

    /**
     * Maps a rule param name to the corresponding request value.
     * Returns null if the param is not a dimension check (e.g. ifc_class filter).
     */
    private Double extractActual(PlacementRequest req, String paramName) {
        return switch (paramName) {
            case "min_area_m2"   -> req.areaSqM();
            case "min_dim_mm"    -> req.minDimMm();
            case "min_height_mm" -> req.heightMm();
            case "min_width_mm"  -> req.widthMm();
            default -> null;   // ifc_class, max_spacing_mm, etc. not checked here
        };
    }

    @Override
    public int getRuleCount() {
        return rulesByCategory.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    @Override
    public double getMinimumForRule(String category, String paramName) {
        // Check category-specific rules, then wildcard
        double val = findMinimum(category, paramName);
        if (val >= 0) return val;
        return findMinimum(WILDCARD, paramName);
    }

    private double findMinimum(String categoryKey, String paramName) {
        List<CachedRule> rules = rulesByCategory.get(categoryKey);
        if (rules == null) return -1;
        for (CachedRule rule : rules) {
            Double threshold = rule.thresholds.get(paramName);
            if (threshold != null) return threshold;
        }
        return -1;
    }

    private static boolean isNumeric(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ── Inference Engine integration ──────────────────────────────────

    /**
     * Validate all requests using the InferenceEngine (dependency-ordered).
     * Returns full rule results including SKIP for downstream failures.
     *
     * // Implementing BIM_Designer_SRS.md §19 — Witness: W-INF-DEP-1
     */
    public List<InferenceEngine.RuleResult> validateAll(List<PlacementRequest> requests) {
        if (!active || requests == null || requests.isEmpty()) {
            return List.of();
        }

        InferenceEngine engine = new InferenceEngine();
        List<InferenceEngine.RuleResult> allResults = new ArrayList<>();

        for (PlacementRequest req : requests) {
            // Gather applicable rules for this request's category
            List<InferenceEngine.CachedRuleExt> extRules = new ArrayList<>();

            // Category-specific rules
            List<CachedRule> catRules = rulesByCategory.get(req.bomCategory());
            if (catRules != null) {
                for (CachedRule r : catRules) {
                    extRules.add(toExt(r));
                }
            }

            // Wildcard rules
            List<CachedRule> wildRules = rulesByCategory.get(WILDCARD);
            if (wildRules != null) {
                for (CachedRule r : wildRules) {
                    extRules.add(toExt(r));
                }
            }

            if (!extRules.isEmpty()) {
                List<InferenceEngine.RuleResult> results = engine.evaluate(extRules, req);
                allResults.addAll(results);
            }
        }

        BIMLogger.info(TAG, "validateAll: {} requests, {} total rule results",
                requests.size(), allResults.size());
        return allResults;
    }

    // ── Batch validation (spatial check_methods) ─────────────────────

    /**
     * Validate a batch of placements using spatial check_methods (DV-F-02, DV-F-03).
     * Single-element checks are NOT re-run here — use validate() for those.
     *
     * <p>Spatial checks require multiple elements on the same storey:
     * <ul>
     *   <li>NN_CENTROID_DISTANCE — nearest-neighbour for same-class elements</li>
     *   <li>CENTRELINE_CLEARANCE — cross-discipline clearance</li>
     *   <li>PER_STOREY_Z_CONSISTENCY — Z deviation for same-class per storey</li>
     * </ul>
     *
     * @param requests all elements to check (grouped internally by storey/class)
     * @return list of validation results for spatial violations only
     *
     * // Implementing DocAction_SRS §3.1 — Witness: W-DV-BATCH-1
     */
    public List<ValidationVerdict> validateBatch(List<PlacementRequest> requests) {
        if (!active || requests == null || requests.isEmpty()) {
            return List.of();
        }

        List<ValidationVerdict> results = new ArrayList<>();

        // Collect spatial rules (those with check_method params)
        List<CachedRule> spatialRules = new ArrayList<>();
        for (var entry : rulesByCategory.entrySet()) {
            for (CachedRule rule : entry.getValue()) {
                if (rule.checkMethod() != null) {
                    spatialRules.add(rule);
                }
            }
        }

        for (CachedRule rule : spatialRules) {
            String method = rule.checkMethod();
            switch (method) {
                case "NN_CENTROID_DISTANCE" -> {
                    // DV-F-02: Group by (storey, ifc_class), check NN spacing
                    String targetClass = rule.stringParams.get("ifc_class");
                    results.addAll(checkNNDistance(rule, requests, targetClass));
                }
                case "CENTRELINE_CLEARANCE" -> {
                    // DV-F-03: Cross-discipline clearance
                    String discA = rule.stringParams.get("discipline_a");
                    String discB = rule.stringParams.get("discipline_b");
                    results.addAll(checkClearance(rule, requests, discA, discB));
                }
                case "PER_STOREY_Z_CONSISTENCY" -> {
                    // DV-F-06: Z deviation per storey
                    String targetClass = rule.stringParams.get("ifc_class");
                    results.addAll(checkZConsistency(rule, requests, targetClass));
                }
                default -> {
                    // Other batch methods deferred to Phase 2+
                }
            }
        }

        BIMLogger.info(TAG, "validateBatch: {} elements, {} spatial violations",
                requests.size(), results.stream().filter(v -> !v.result().equals(ValidationVerdict.Result.PASS)).count());
        return results;
    }

    // ── Spatial check_method implementations ─────────────────────────

    /**
     * DV-F-02: NN_CENTROID_DISTANCE — nearest-neighbour distance check.
     * Groups elements by (storey, ifc_class), computes planar XY NN distance.
     */
    private List<ValidationVerdict> checkNNDistance(CachedRule rule,
            List<PlacementRequest> requests, String targetClass) {

        List<ValidationVerdict> results = new ArrayList<>();
        Double minSpacing = rule.thresholds.get("min_spacing_mm");
        Double maxSpacing = rule.thresholds.get("max_spacing_mm");

        // Group by storey
        Map<String, List<PlacementRequest>> byStorey = new HashMap<>();
        for (PlacementRequest req : requests) {
            if (targetClass != null && !targetClass.equals(req.ifcClass())) continue;
            byStorey.computeIfAbsent(req.storey(), k -> new ArrayList<>()).add(req);
        }

        for (var entry : byStorey.entrySet()) {
            List<PlacementRequest> group = entry.getValue();
            if (group.size() < 2) continue;

            for (int i = 0; i < group.size(); i++) {
                double nn = SpatialPredicates.nnDistance(group, group.get(i));
                if (minSpacing != null && nn < minSpacing) {
                    results.add(makeVerdict(rule, "min_spacing_mm", nn, minSpacing));
                } else if (maxSpacing != null && nn > maxSpacing) {
                    results.add(makeVerdict(rule, "max_spacing_mm", nn, maxSpacing));
                }
            }
        }
        return results;
    }

    /**
     * DV-F-03: CENTRELINE_CLEARANCE — cross-discipline clearance.
     * clearance = centroid_2D_dist - radius_a - radius_b.
     */
    private List<ValidationVerdict> checkClearance(CachedRule rule,
            List<PlacementRequest> requests, String discA, String discB) {

        List<ValidationVerdict> results = new ArrayList<>();
        Double minClearance = rule.thresholds.get("min_clearance_mm");
        if (minClearance == null) return results;

        // Group by storey, then filter by discipline
        Map<String, List<PlacementRequest>> byStorey = new HashMap<>();
        for (PlacementRequest req : requests) {
            if (discA != null && discB != null) {
                if (!discA.equals(req.discipline()) && !discB.equals(req.discipline())) continue;
            }
            byStorey.computeIfAbsent(req.storey(), k -> new ArrayList<>()).add(req);
        }

        for (var group : byStorey.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    PlacementRequest a = group.get(i);
                    PlacementRequest b = group.get(j);
                    // Cross-discipline check: only check pairs across different disciplines
                    if (a.discipline() != null && a.discipline().equals(b.discipline())) continue;

                    double clearance = SpatialPredicates.centreClearance(a, b);
                    if (clearance < minClearance) {
                        results.add(makeVerdict(rule, "min_clearance_mm", clearance, minClearance));
                    }
                }
            }
        }
        return results;
    }

    /**
     * DV-F-06: PER_STOREY_Z_CONSISTENCY — Z deviation per storey for same-class elements.
     */
    private List<ValidationVerdict> checkZConsistency(CachedRule rule,
            List<PlacementRequest> requests, String targetClass) {

        List<ValidationVerdict> results = new ArrayList<>();
        Double maxDeviation = rule.thresholds.get("max_z_deviation_mm");
        if (maxDeviation == null) return results;

        // Group by (storey, ifc_class)
        Map<String, List<PlacementRequest>> byGroup = new HashMap<>();
        for (PlacementRequest req : requests) {
            if (targetClass != null && !targetClass.equals(req.ifcClass())) continue;
            String key = req.storey() + "|" + req.ifcClass();
            byGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(req);
        }

        for (var group : byGroup.values()) {
            if (group.size() < 2) continue;
            double stddev = SpatialPredicates.zStdDev(group);
            if (stddev > maxDeviation) {
                results.add(makeVerdict(rule, "max_z_deviation_mm", stddev, maxDeviation));
            }
        }
        return results;
    }

    // ── CachedRule → CachedRuleExt conversion ────────────────────────

    private static InferenceEngine.CachedRuleExt toExt(CachedRule r) {
        return new InferenceEngine.CachedRuleExt(
                r.ruleId(), r.name(), r.standardRef(),
                r.thresholds(), r.stringParams(), r.dependsOn());
    }

    /**
     * Build proof tree from validateAll results.
     */
    public InferenceEngine.ProofTree buildProofTree(List<InferenceEngine.RuleResult> results) {
        return new InferenceEngine().buildProofTree(results);
    }

    /**
     * Inject pre-built rules for testing (no DB needed).
     * Package-private — used only by CheckMethodDispatchTest.
     */
    void setRulesForTest(Map<String, List<CachedRule>> rules) {
        this.rulesByCategory = rules;
        this.active = true;
        this.jurisdiction = "TEST";
    }

    /**
     * Get all cached rules as flat list (for testing/inspection).
     */
    public List<CachedRule> getAllRulesFlat() {
        List<CachedRule> all = new ArrayList<>();
        for (var entry : rulesByCategory.entrySet()) {
            all.addAll(entry.getValue());
        }
        return all;
    }
}
