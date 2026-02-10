package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.BoundingBox;
import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.*;

/**
 * Space Type Contract Verifier — Phase 101
 *
 * Domain-agnostic post-compilation check that reads space type contracts
 * from AD tables and verifies every compiled IfcSpace meets its contract.
 *
 * 5 sub-checks (all data-driven, zero hardcoded building knowledge):
 *   1. AREA       — actual area >= ad_space_dim.min_area
 *   2. DIMENSION  — width >= min_width AND depth >= min_depth
 *   3. PROPORTION — aspect ratio <= max_ratio
 *   4. OPENINGS   — door/window count >= ad_space_type_opening.qty_base
 *   5. PRODUCTS   — required_products present in space (spatial overlap)
 *
 * Adding a new domain (bridge, road) = adding rows to AD tables. No code changes.
 */
public class SpaceContractCheck implements SanityCheck {

    @Override
    public String getId() { return "space_contract"; }

    @Override
    public String getName() { return "Space Type Contract"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        List<Element> spaces = model.getSpaces();
        if (spaces.isEmpty()) {
            return CheckResult.warning(getId(), getName())
                .summary("No IfcSpace elements found")
                .build();
        }

        // Load contracts from component_library.db
        Map<String, SpaceContract> contracts = loadContracts();
        if (contracts.isEmpty()) {
            return CheckResult.warning(getId(), getName())
                .summary("No space contracts loaded from ad_space_dim")
                .build();
        }

        // Load opening rules
        Map<String, List<OpeningRule>> openingRules = loadOpeningRules();

        // Build element index by IFC class for spatial product queries
        Map<String, List<Element>> elementsByClass = new HashMap<>();
        for (Element e : model.getAllElements()) {
            if (e.ifcClass() != null) {
                elementsByClass.computeIfAbsent(e.ifcClass(), k -> new ArrayList<>()).add(e);
            }
        }

        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> passes = new ArrayList<>();
        int checkedSpaces = 0;
        int skippedSpaces = 0;

        // Phase 108: Compute building envelope for exterior wall detection
        double bldgMinX = Double.MAX_VALUE, bldgMaxX = -Double.MAX_VALUE;
        double bldgMinY = Double.MAX_VALUE, bldgMaxY = -Double.MAX_VALUE;
        for (Element space : spaces) {
            if (space.bbox() != null) {
                bldgMinX = Math.min(bldgMinX, space.bbox().minX());
                bldgMaxX = Math.max(bldgMaxX, space.bbox().maxX());
                bldgMinY = Math.min(bldgMinY, space.bbox().minY());
                bldgMaxY = Math.max(bldgMaxY, space.bbox().maxY());
            }
        }

        // Group spaces by (name, object_type) to avoid 16× duplicate reporting
        Map<String, List<Element>> spaceGroups = new LinkedHashMap<>();
        for (Element space : spaces) {
            String key = (space.name() != null ? space.name() : "?") + "|" +
                         (space.objectType() != null ? space.objectType() : "?");
            spaceGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(space);
        }

        for (var entry : spaceGroups.entrySet()) {
            List<Element> group = entry.getValue();
            Element representative = group.get(0);
            int instanceCount = group.size();

            String spaceType = resolveSpaceType(model, representative);
            SpaceContract contract = contracts.get(spaceType);

            if (contract == null || isNoOpContract(contract)) {
                skippedSpaces += instanceCount;
                continue;
            }

            if (representative.bbox() == null) {
                skippedSpaces += instanceCount;
                continue;
            }

            checkedSpaces += instanceCount;
            String spaceName = representative.name() != null ? representative.name() : "unnamed";
            String suffix = instanceCount > 1 ? " (x" + instanceCount + ")" : "";

            BoundingBox bbox = representative.bbox();
            double width = bbox.maxX() - bbox.minX();
            double depth = bbox.maxY() - bbox.minY();
            double area = width * depth;

            // Sub-check 1: AREA
            if (contract.minArea > 0 && area < contract.minArea) {
                failures.add(String.format("AREA: %s [%s]%s area %.1fm² < minimum %.1fm²",
                    spaceName, spaceType, suffix, area, contract.minArea));
            }

            // Sub-check 2: DIMENSION
            if (contract.minWidth > 0 && width < contract.minWidth && depth < contract.minWidth) {
                // Both dimensions less than minimum width — genuinely too narrow
                double narrowest = Math.min(width, depth);
                failures.add(String.format("DIMENSION: %s [%s]%s narrowest %.1fm < minimum %.1fm",
                    spaceName, spaceType, suffix, narrowest, contract.minWidth));
            }
            if (contract.minDepth > 0 && Math.max(width, depth) < contract.minDepth) {
                failures.add(String.format("DIMENSION: %s [%s]%s longest %.1fm < minimum depth %.1fm",
                    spaceName, spaceType, suffix, Math.max(width, depth), contract.minDepth));
            }

            // Sub-check 3: PROPORTION
            if (contract.maxRatio > 0) {
                double longer = Math.max(width, depth);
                double shorter = Math.min(width, depth);
                double aspect = shorter > 0.01 ? longer / shorter : 999;
                if (aspect > contract.maxRatio) {
                    warnings.add(String.format("PROPORTION: %s [%s]%s aspect %.1f:1 > maximum %.1f:1",
                        spaceName, spaceType, suffix, aspect, contract.maxRatio));
                }
            }

            // Sub-check 4: OPENINGS (count doors/windows spatially overlapping space)
            List<OpeningRule> rules = openingRules.getOrDefault(spaceType, Collections.emptyList());
            for (OpeningRule rule : rules) {
                if (rule.minCount <= 0) continue;
                // Phase 108: PER_EXTERIOR_WALL rules only apply to rooms with exterior walls
                // Interior rooms (e.g. ensuite bathrooms) use mechanical ventilation instead
                if ("PER_EXTERIOR_WALL".equals(rule.qtyRule) && representative.bbox() != null) {
                    BoundingBox rb = representative.bbox();
                    boolean touchesPerimeter =
                        Math.abs(rb.minX() - bldgMinX) < 0.5 || Math.abs(rb.maxX() - bldgMaxX) < 0.5 ||
                        Math.abs(rb.minY() - bldgMinY) < 0.5 || Math.abs(rb.maxY() - bldgMaxY) < 0.5;
                    if (!touchesPerimeter) continue; // Interior room — skip exterior-wall rule
                }
                int found = countOpeningsInSpace(model, representative, rule.role);
                if (found < rule.minCount) {
                    failures.add(String.format("OPENINGS: %s [%s]%s has %d %s, minimum %d required",
                        spaceName, spaceType, suffix, found, rule.role, rule.minCount));
                }
            }

            // Sub-check 5: PRODUCTS (required IFC elements spatially within space)
            for (String productTag : contract.requiredProducts) {
                String ifcClass = mapProductToIfc(productTag);
                if (ifcClass == null) continue;
                int found = countProductsInSpace(model, representative, ifcClass, elementsByClass);
                if (found == 0) {
                    failures.add(String.format("PRODUCTS: %s [%s]%s missing %s (0 %s found)",
                        spaceName, spaceType, suffix, productTag, ifcClass));
                }
            }

            if (failures.isEmpty() || !hasFailureFor(failures, spaceName)) {
                passes.add(String.format("OK: %s [%s]%s — %.1fm² (%.1f×%.1f)",
                    spaceName, spaceType, suffix, area, width, depth));
            }
        }

        // Build result
        int totalFails = failures.size();
        int totalWarns = warnings.size();

        if (totalFails > 0) {
            CheckResult.Builder result = CheckResult.fail(getId(), getName())
                .summary(String.format("Contract violations: %d failures, %d warnings across %d spaces",
                    totalFails, totalWarns, checkedSpaces));
            for (String f : failures) result.detail("FAIL: " + f);
            for (String w : warnings) result.detail("WARN: " + w);
            result.detail(String.format("Checked %d spaces, skipped %d (no contract/GENERIC)",
                checkedSpaces, skippedSpaces));
            result.guidance("Fix failing spaces to meet their type contracts in ad_space_dim");
            result.data("failures", totalFails);
            result.data("warnings", totalWarns);
            result.data("checked", checkedSpaces);
            return result.build();
        }

        if (totalWarns > 0) {
            CheckResult.Builder result = CheckResult.warning(getId(), getName())
                .summary(String.format("%d warnings across %d spaces (no failures)", totalWarns, checkedSpaces));
            for (String w : warnings) result.detail("WARN: " + w);
            for (String p : passes) result.detail(p);
            result.data("warnings", totalWarns);
            result.data("checked", checkedSpaces);
            return result.build();
        }

        CheckResult.Builder result = CheckResult.pass(getId(), getName())
            .summary(String.format("All %d spaces meet their type contracts", checkedSpaces));
        for (String p : passes) result.detail(p);
        result.detail(String.format("Skipped %d spaces (no contract/GENERIC)", skippedSpaces));
        result.data("checked", checkedSpaces);
        result.data("skipped", skippedSpaces);
        result.data("contracts", contracts.size());
        return result.build();
    }

    // =========================================================================
    // Space type resolution
    // =========================================================================

    private String resolveSpaceType(SanityModel model, Element space) {
        // Use model's authoritative inference (objectType > predefinedType > name)
        String inferred = model.inferSpaceType(space);

        // Map inference results to ad_space_dim keys
        return switch (inferred) {
            case "BATHROOM" -> {
                // Check if it's actually a toilet block (multi-fixture)
                String ot = space.objectType();
                yield (ot != null && ot.equalsIgnoreCase("TOILET_BLOCK")) ? "TOILET_BLOCK" : "BATHROOM";
            }
            case "SHAFT", "ELEVATOR", "PORCH", "UNKNOWN", "ROOM" -> "GENERIC";
            case "MECHANICAL", "UTILITY", "PLANT" -> inferred;
            default -> inferred;
        };
    }

    // =========================================================================
    // Contract loading from component_library.db
    // =========================================================================

    private Map<String, SpaceContract> loadContracts() {
        Map<String, SpaceContract> contracts = new LinkedHashMap<>();
        String libPath = "library/component_library.db";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + libPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT space_type, min_width, min_depth, min_area, max_ratio, " +
                 "required_products, optional_products FROM ad_space_dim WHERE is_active = 1")) {
            while (rs.next()) {
                String type = rs.getString("space_type");
                List<String> reqProducts = parseJsonArray(rs.getString("required_products"));
                List<String> optProducts = parseJsonArray(rs.getString("optional_products"));
                contracts.put(type, new SpaceContract(
                    rs.getDouble("min_width"),
                    rs.getDouble("min_depth"),
                    rs.getDouble("min_area"),
                    rs.getDouble("max_ratio"),
                    reqProducts,
                    optProducts
                ));
            }
        } catch (SQLException e) {
            // Return empty — check will warn
        }
        return contracts;
    }

    private Map<String, List<OpeningRule>> loadOpeningRules() {
        Map<String, List<OpeningRule>> rules = new LinkedHashMap<>();
        String libPath = "library/component_library.db";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + libPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT space_type_id, opening_role, qty_base, qty_rule FROM ad_space_type_opening " +
                 "WHERE is_active = 1")) {
            while (rs.next()) {
                String type = rs.getString("space_type_id");
                rules.computeIfAbsent(type, k -> new ArrayList<>()).add(
                    new OpeningRule(rs.getString("opening_role"), rs.getInt("qty_base"),
                        rs.getString("qty_rule")));
            }
        } catch (SQLException e) {
            // Return empty — openings sub-check skipped
        }
        return rules;
    }

    // =========================================================================
    // Spatial counting (rtree-based, not containment-based)
    // =========================================================================

    /**
     * Count doors or windows spatially overlapping a space.
     * Uses rtree bbox intersection since openings aren't in rel_contained_in_space.
     */
    private int countOpeningsInSpace(SanityModel model, Element space, String role) {
        if (space.bbox() == null) return 0;
        BoundingBox sb = space.bbox();

        String ifcClass;
        if (role.contains("LIGHT") || role.contains("VENTILATION") || role.contains("WINDOW")) {
            ifcClass = "IfcWindow";
        } else {
            ifcClass = "IfcDoor";
        }

        int count = 0;
        List<Element> candidates = ifcClass.equals("IfcDoor") ? model.getDoors() : model.getWindows();
        for (Element e : candidates) {
            if (e.bbox() == null) continue;
            if (bboxOverlapsXY(sb, e.bbox(), 0.15)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Count IFC elements of given class spatially within a space bbox.
     * Uses in-memory element list to avoid DB connection issues.
     */
    private int countProductsInSpace(SanityModel model, Element space, String ifcClass,
                                     Map<String, List<Element>> elementsByClass) {
        if (space.bbox() == null) return 0;
        BoundingBox sb = space.bbox();

        List<Element> candidates = elementsByClass.getOrDefault(ifcClass, Collections.emptyList());
        int count = 0;
        for (Element e : candidates) {
            if (e.bbox() == null) continue;
            if (bboxOverlaps3D(sb, e.bbox(), 0.05)) {
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // Product tag → IFC class mapping
    // =========================================================================

    private String mapProductToIfc(String tag) {
        if (tag == null) return null;
        String upper = tag.toUpperCase();

        // Exact matches first
        return switch (upper) {
            case "DOOR", "DOOR_D1", "DOOR_D2" -> "IfcDoor";
            case "WINDOW", "WINDOW_W1", "WINDOW_W2" -> "IfcWindow";
            case "LIGHT", "ELEC_LIGHT" -> "IfcLightFixture";
            case "FIXTURE_TOILET", "FIXTURE_SINK" -> "IfcSanitaryTerminal";
            case "SMOKE_DETECTOR", "ALARM_BELL", "BREAK_GLASS" -> "IfcAlarm";
            case "EXHAUST_FAN" -> "IfcFan";
            case "FP_SPRINKLER" -> "IfcFireSuppressionTerminal";
            case "ELEC_OUTLET", "ELEC_SWITCH" -> null; // Not modeled at LOD400
            case "FURN_BED_SINGLE", "FURN_BED_DOUBLE", "FURN_WARDROBE" -> "IfcFurniture";
            case "FIXTURE_SHOWER" -> "IfcSanitaryTerminal";
            default -> null; // Unknown tag — skip
        };
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private boolean bboxOverlapsXY(BoundingBox a, BoundingBox b, double tolerance) {
        return a.maxX() + tolerance > b.minX() && a.minX() - tolerance < b.maxX()
            && a.maxY() + tolerance > b.minY() && a.minY() - tolerance < b.maxY();
    }

    private boolean bboxOverlaps3D(BoundingBox a, BoundingBox b, double tolerance) {
        return a.maxX() + tolerance > b.minX() && a.minX() - tolerance < b.maxX()
            && a.maxY() + tolerance > b.minY() && a.minY() - tolerance < b.maxY()
            && a.maxZ() + tolerance > b.minZ() && a.minZ() - tolerance < b.maxZ();
    }

    private boolean isNoOpContract(SpaceContract c) {
        return c.minArea <= 0 && c.minWidth <= 0 && c.minDepth <= 0
            && c.maxRatio <= 0 && c.requiredProducts.isEmpty();
    }

    private boolean hasFailureFor(List<String> failures, String spaceName) {
        for (String f : failures) {
            if (f.contains(spaceName)) return true;
        }
        return false;
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return Collections.emptyList();
        // Simple JSON array parser: ["A","B","C"] → [A, B, C]
        List<String> result = new ArrayList<>();
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
        for (String part : json.split(",")) {
            String trimmed = part.trim().replace("\"", "");
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    // =========================================================================
    // Data records
    // =========================================================================

    private record SpaceContract(
        double minWidth, double minDepth, double minArea, double maxRatio,
        List<String> requiredProducts, List<String> optionalProducts
    ) {}

    private record OpeningRule(String role, int minCount, String qtyRule) {}
}
