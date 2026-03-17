package com.bim.designer.validation;

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

    /** Cached rule with its threshold parameters. */
    record CachedRule(
            int ruleId,
            String name,
            String standardRef,
            Map<String, Double> thresholds   // param_name -> numeric value
    ) {}

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
        ValidationVerdict v = checkCategory(request, request.bomCategory());
        if (v.isBlocked()) return v;

        v = checkCategory(request, WILDCARD);
        if (v.isBlocked()) return v;

        return ValidationVerdict.pass();
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
                "SELECT ad_val_rule_id, name, standard_ref "
              + "FROM AD_Val_Rule "
              + "WHERE jurisdiction = ? AND is_active = 1 AND rule_type = 'COMPLIANCE'";

        try (PreparedStatement ps = conn.prepareStatement(ruleSQL)) {
            ps.setString(1, jurisdiction);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int ruleId = rs.getInt(1);
                    String name = rs.getString(2);
                    String stdRef = rs.getString(3);

                    // Step 2: load params for this rule
                    Map<String, String> allParams = loadParams(conn, ruleId);

                    // Step 3: separate bom_category from thresholds
                    String category = allParams.getOrDefault("bom_category", WILDCARD);
                    Map<String, Double> thresholds = new HashMap<>();
                    for (var e : allParams.entrySet()) {
                        if (!"bom_category".equals(e.getKey()) && isNumeric(e.getValue())) {
                            thresholds.put(e.getKey(), Double.parseDouble(e.getValue()));
                        }
                    }

                    if (!thresholds.isEmpty()) {
                        CachedRule rule = new CachedRule(ruleId, name, stdRef, thresholds);
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
            for (var entry : rule.thresholds.entrySet()) {
                String paramName = entry.getKey();
                double required = entry.getValue();

                Double actual = extractActual(req, paramName);
                if (actual == null) continue; // param not applicable to this request type

                if (actual < required) {
                    return ValidationVerdict.block(
                            rule.name, rule.standardRef, actual, required,
                            String.format("%s: %.1f < required %.1f (%s)",
                                    paramName, actual, required, rule.standardRef));
                }
            }
        }
        return ValidationVerdict.pass();
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

    private static boolean isNumeric(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
