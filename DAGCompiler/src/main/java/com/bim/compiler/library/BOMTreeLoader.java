package com.bim.compiler.library;

import com.bim.orm.ModelQuery;
import com.bim.ormsandbox.po.MBOMLine;
import com.bim.ormsandbox.po.MAttribute;

import java.sql.*;
import java.util.*;

/**
 * Shared BOM tree loader — the AD (Application Dictionary) layer for BOM tree infrastructure.
 *
 * <p>Loads {@code m_bom_line} + {@code m_attribute} from BOM.db into canonical
 * {@link BOMNode}/{@link BOMChild} records. Both {@code BOMTierResolver} (furniture/fixtures)
 * and {@code FloorPlateBOMResolver} (floor plate zones) delegate tree loading here,
 * then apply their own Model-layer resolution strategies.
 *
 * <p>Analogous to iDempiere's AD column model: the tree structure, param accessors,
 * and column enrichment (3-table authority: param overrides column) are common factors
 * extracted here. Resolver-specific dispatch (fixture/GPD/FLOAT vs band/side/fill) stays
 * in concrete Model classes.
 *
 * <p>Phase G-1 Step 2.
 */
public final class BOMTreeLoader {

    private BOMTreeLoader() {} // utility class — static methods only

    // ── Canonical records ────────────────────────────────────────────────────

    /** One node in the BOM tree — a BOM header with its child lines and tack origin (§3.4). */
    public record BOMNode(String bomId, List<BOMChild> children,
                          double originX, double originY, double originZ) {
        /** Convenience constructor with zero origin (structured BOMs after shift, or new BOMs). */
        public BOMNode(String bomId, List<BOMChild> children) {
            this(bomId, children, 0.0, 0.0, 0.0);
        }
    }

    /**
     * One child in a BOM assembly — canonical record combining {@code m_bom_line} columns
     * with {@code m_attribute} params.
     *
     * <p>Column enrichment follows the 3-table authority rule:
     * <ul>
     *   <li>{@code namePattern} — param {@code name_pattern} overrides column {@code child_name_pattern}</li>
     *   <li>{@code dx/dy/dz} — params {@code dx}/{@code x_offset} override column {@code dx}, etc.</li>
     * </ul>
     *
     * <p>Resolver-specific fields (wallRule, zone, backToWall) are NOT pre-computed here —
     * resolvers derive them from {@link #param(String)} at usage time.
     */
    /**
     * One child in a BOM assembly — canonical record combining {@code m_bom_line} columns
     * with {@code m_attribute} params.
     *
     * <p>NORM-2: three-way split (child_bom_id, product_ref, child_ifc_class) replaced by
     * single {@code child_product_id} FK into M_Product. Backward-compat accessors
     * {@link #childBomId()} and {@link #productRef()} are computed from {@code childProductId}
     * + {@code componentType} so callers need no changes.
     */
    public record BOMChild(
        int id, String bomId, String role, String childProductId,
        String namePattern, String componentType, String locatorRef,
        double dx, double dy, double dz,
        int sequence, boolean isVariance,
        String layoutStrategy,
        Map<String, String> params
    ) {
        /**
         * Returns childProductId as a candidate sub-BOM reference.  Callers check
         * {@code bomTree.get(childBomId())} — returns non-null only when the child
         * is itself a BOM (has children).  Recursion is determined by BOM tree
         * structure, not by component_type.
         *
         * <p>Everything is BUY (full LOD in component_library.db).  MAKE only applies
         * when LOD does not exist in library — see {@code docs/archive/Mesh2Library.txt}.
         */
        public String childBomId() { return childProductId; }

        /**
         * Backward compat: returns childProductId when component is a catalog BUY leaf
         * (active M_Product, product_type != STRUCTURAL).
         * Returns null for structural IFC-class-only BUY rows (childProductId starts with "Ifc"),
         * so dimension lookup falls through to namePattern (which carries the element name from params).
         */
        public String productRef() {
            if (!"BUY".equals(componentType)) return null;
            // Structural stubs use IFC class names — not real catalog product IDs
            if (childProductId != null && childProductId.startsWith("Ifc")) return null;
            return childProductId;
        }

        /** Get a param value, or null if absent. */
        public String param(String key) { return params.getOrDefault(key, null); }

        /** Get a param value with default. */
        public String param(String key, String def) { return params.getOrDefault(key, def); }

        /** Parse an int param with default. */
        public int paramInt(String key, int def) {
            String v = params.get(key);
            if (v == null) return def;
            try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; }
        }

        /** Parse a double param with default. */
        public double paramDouble(String key, double def) {
            String v = params.get(key);
            if (v == null) return def;
            try { return Double.parseDouble(v); } catch (NumberFormatException e) { return def; }
        }

        /** True if this child should be routed through the fixture placement path. */
        public boolean hasFixtureParams() {
            return params != null
                && (params.containsKey("placement_wall") || params.containsKey("position"));
        }
    }

    // ── Tree loading ─────────────────────────────────────────────────────────

    /**
     * Load BOM tree from BOM.db. If {@code bomIds} is empty, loads ALL active BOMs.
     * If specified, loads only the named BOMs.
     *
     * <p>Joins {@code m_bom} to ensure only active parent BOMs are included.
     * Loads all active {@code m_attribute} params and groups by {@code M_BOM_Line_ID}.
     *
     * @param bomDbPath path to BOM.db
     * @param bomIds    optional BOM IDs to filter (empty = all active)
     * @return tree map: bomId → BOMNode with enriched children
     */
    public static Map<String, BOMNode> load(String bomDbPath, String... bomIds)
            throws SQLException {

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath)) {

            // ① Load BOM children — JOIN m_bom for is_active gate
            ModelQuery<MBOMLine> query = new ModelQuery<>(
                    conn, MBOMLine::new, MBOMLine.Table_Name + " bc")
                .addJoin("m_bom b", "bc.M_BOM_ID = b.M_BOM_ID")
                .where("b.is_active = ?", 1)
                .andWhere("bc.is_active = ?", 1);

            if (bomIds.length > 0) {
                String placeholders = String.join(",",
                    Collections.nCopies(bomIds.length, "?"));
                query.andWhere("bc.bom_id IN (" + placeholders + ")",
                    (Object[]) bomIds);
            }

            List<MBOMLine> rawChildren = query
                .orderBy("bc.bom_id, bc.sequence")
                .list();

            // ② Load ALL active params — single bulk query, group by M_BOM_Line_ID
            List<MAttribute> allParams = new ModelQuery<>(
                    conn, MAttribute::new, MAttribute.Table_Name)
                .where("is_active = ?", 1)
                .list();

            Map<Integer, Map<String, String>> paramsByChildId = new HashMap<>();
            for (MAttribute p : allParams) {
                paramsByChildId
                    .computeIfAbsent(p.getBomLineId(), k -> new HashMap<>())
                    .put(p.getParamKey(), p.getParamValue());
            }

            // ③ Build tree with enriched BOMChild records
            Map<String, BOMNode> tree = new HashMap<>();
            for (MBOMLine raw : rawChildren) {
                Map<String, String> params = paramsByChildId.getOrDefault(
                    raw.getBomLineId(), Map.of());

                // 3-table authority: param overrides column
                String nameOverride = params.get("name_pattern");
                String effectiveName = nameOverride != null
                    ? nameOverride : raw.getChildNamePattern();

                BOMChild child = new BOMChild(
                    raw.getBomLineId(),
                    raw.getBomId(),
                    raw.getRole(),
                    raw.getChildProductId(),
                    effectiveName,
                    raw.getComponentType(),
                    raw.getLocatorRef(),
                    paramDouble(params, "dx",
                        paramDouble(params, "x_offset", raw.getDx())),
                    paramDouble(params, "dy",
                        paramDouble(params, "y_offset", raw.getDy())),
                    paramDouble(params, "dz",
                        paramDouble(params, "z_offset", raw.getDz())),
                    raw.getSequence(),
                    raw.isVariance(),
                    raw.getLayoutStrategy(),
                    params
                );

                tree.computeIfAbsent(raw.getBomId(),
                    k -> new BOMNode(k, new ArrayList<>()))
                    .children().add(child);
            }

            // ④ Load BOM origins (tack convention §3.4)
            Map<String, double[]> origins = new HashMap<>();
            // Tier 2: m_bom.bom_id TEXT → Value column for text lookups
            String originSql = "SELECT Value, origin_x, origin_y, origin_z FROM m_bom WHERE is_active = 1"
                + " AND (origin_x != 0 OR origin_y != 0 OR origin_z != 0)";
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(originSql)) {
                while (rs.next()) {
                    origins.put(rs.getString("Value"),
                        new double[]{ rs.getDouble("origin_x"),
                                      rs.getDouble("origin_y"),
                                      rs.getDouble("origin_z") });
                }
            }

            // ⑤ Rebuild tree with origin data
            if (!origins.isEmpty()) {
                Map<String, BOMNode> treeWithOrigins = new HashMap<>();
                for (var entry : tree.entrySet()) {
                    double[] o = origins.getOrDefault(entry.getKey(), new double[]{0, 0, 0});
                    treeWithOrigins.put(entry.getKey(),
                        new BOMNode(entry.getKey(), entry.getValue().children(),
                                    o[0], o[1], o[2]));
                }
                tree = treeWithOrigins;
            }

            int totalChildren = rawChildren.size();
            int originsLoaded = origins.size();
            System.out.printf("[BOM-TREE] Loaded %d BOM nodes, %d children, %d origins%n",
                tree.size(), totalChildren, originsLoaded);

            return tree;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static double paramDouble(Map<String, String> params, String key, double def) {
        String val = params.get(key);
        if (val == null) return def;
        try { return Double.parseDouble(val); } catch (NumberFormatException e) { return def; }
    }
}
