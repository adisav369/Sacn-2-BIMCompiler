package com.bim.compiler.bom.walker;

import com.bim.compiler.topology.Discipline;
import com.bim.orm.ModelQuery;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;
import com.bim.ormsandbox.po.MProduct;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/**
 * NORM-3a: Single BOM tree traversal engine.
 *
 * <p>Walks {@code m_bom} / {@code m_bom_line} from {@code {PREFIX}_BOM.db} and resolves
 * {@code M_Product} from {@code ERP.db} (master product catalog, moved S65 Step 3).
 * Fires {@link BOMVisitor} events for each node. Multiple visitors can be registered
 * to accumulate independent results (assembly structure, spatial placement) in a single
 * pass.
 *
 * <h3>Two-connection architecture</h3>
 * <p>{@code bomConn} reads BOM structure (m_bom, m_bom_line — spatial arrangement).
 * {@code compConn} reads M_Product (master product catalog in ERP.db).
 * The deprecated single-arg constructor bridges tests that use one in-memory DB.
 *
 * <h3>Dispatch logic — structural, not by component_type</h3>
 * <p>Sub-assembly detection is structural: if {@code child_product_id} matches a
 * {@code bom_id} in {@code m_bom}, the walker recurses. This is independent of
 * {@code component_type}. The BOM hierarchy itself defines traversal.
 *
 * <p>For each {@code m_bom_line} child:
 * <ol>
 *   <li>Try {@code loadBom(child_product_id)}</li>
 *   <li>If child BOM exists → sub-assembly → {@link BOMVisitor#onSubAssembly}, recurse,
 *       {@link BOMVisitor#onSubAssemblyComplete}</li>
 *   <li>If PHANTOM → {@link BOMVisitor#onPhantom}</li>
 *   <li>Otherwise → leaf → {@link BOMVisitor#onLeaf}</li>
 * </ol>
 *
 * <h3>No component_type checks</h3>
 * <p>The walker ignores component_type for traversal decisions. Sub-assemblies
 * are detected structurally (child_product_id matches a bom_id). PHANTOM is
 * the only component_type that matters (skipped at output). Everything else
 * goes through as a leaf with geometry from component_library.db.
 *
 * <p>Usage:
 * <pre>{@code
 * try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));
 *      Connection compConn = DriverManager.getConnection("jdbc:sqlite:library/component_library.db")) {
 *     BOMWalker walker = new BOMWalker(bomConn, compConn);
 *     walker.walk("BED_SET", List.of(myVisitor), null);
 * }
 * }</pre>
 */
public class BOMWalker {

    private final Connection bomConn;
    private final Connection compConn;
    private static final int MAX_DEPTH = 20; // guard against circular BOM references

    /**
     * Primary constructor — reads BOM structure from bomConn, M_Product from compConn.
     *
     * @param bomConn  connection to {PREFIX}_BOM.db (m_bom, m_bom_line)
     * @param compConn connection to ERP.db (M_Product master catalog)
     */
    public BOMWalker(Connection bomConn, Connection compConn) {
        this.bomConn = bomConn;
        this.compConn = compConn;
    }

    /**
     * @deprecated Use {@link #BOMWalker(Connection, Connection)} to read M_Product from library.
     *             This constructor reads M_Product from bomConn (transitional BOM DB copy).
     *             Retained for tests that use a single in-memory DB.
     */
    @Deprecated
    public BOMWalker(Connection bomConn) {
        this(bomConn, bomConn);
    }

    // ── NodeContext ───────────────────────────────────────────────────────────

    /**
     * Context passed to each {@link BOMVisitor} event.
     *
     * <p>Provides the full hierarchy context at the point of dispatch:
     * the current product, the BOM line that introduced it, the owning BOM,
     * depth level, and the building/resolution context for spatial visitors.
     */
    // Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 5-6 — Witness: W-DV-DISC-ORG
    public record NodeContext(
        MProduct product,          // current M_Product row
        MBOMLine line,             // current m_bom_line (null for root BOM entry)
        MBOM bom,                  // owning BOM of this node
        int level,                 // depth (0 = root BOM children)
        String buildingType,       // from walk entrypoint
        Discipline discipline      // from C_OrderLine → Discipline enum (null when using BOMWalker path)
    ) {
        /** Backward-compatible constructor for BOMWalker path (discipline=null). */
        public NodeContext(MProduct product, MBOMLine line, MBOM bom, int level, String buildingType) {
            this(product, line, bom, level, buildingType, null);
        }
        /** Convenience: bom_id of the owning BOM, or null if bom is null. */
        public String bomId() { return bom != null ? bom.getBomId() : null; }

        /** Convenience: child_product_id of the BOM line, or null if line is null. */
        public String childProductId() { return line != null ? line.getChildProductId() : null; }

        /** Convenience: component_type (BUY/MAKE/PHANTOM), or null if line is null. */
        public String componentType() { return line != null ? line.getComponentType() : null; }

        /** Convenience: role on the BOM line, or null if line is null. */
        public String role() { return line != null ? line.getRole() : null; }
    }

    // ── Walk entrypoints ─────────────────────────────────────────────────────

    /**
     * Walk a BOM tree rooted at {@code rootBomId}, firing visitor events for each node.
     *
     * <p>Does NOT fire {@code onSubAssembly}/{@code onSubAssemblyComplete} for the root BOM itself —
     * only for sub-assembly children. Use {@link #walkSelf} when the root BOM should
     * also be treated as an assembly (e.g. BED_SET walked as a standalone assembly target).
     *
     * @param rootBomId   the BOM to walk (e.g. "BED_SET", "TYPICAL_CONDO_FLOOR")
     * @param visitors    list of visitors to receive events (in order)
     * @param buildingType building type context string (may be null for structural walks)
     */
    public void walk(String rootBomId, List<BOMVisitor> visitors, String buildingType)
            throws SQLException {
        MBOM bom = loadBom(rootBomId);
        if (bom == null) {
            System.err.printf("[BOMWalker] BOM not found or inactive: %s%n", rootBomId);
            return;
        }
        walkChildren(bom, visitors, buildingType, 0);
    }

    /**
     * Walk a BOM tree, wrapping the root BOM itself in synthetic
     * {@code onSubAssembly}/{@code onSubAssemblyComplete} events (level = -1, line = null).
     *
     * <p>This allows {@link BOMVisitor} implementations that accumulate state within
     * onSubAssembly/onSubAssemblyComplete pairs to correctly handle the root BOM as an assembly.
     * Use this when every BOM in the DB should produce assemblies, including top-level ones.
     *
     * @param rootBomId   the BOM to walk as-self
     * @param visitors    list of visitors to receive events
     * @param buildingType building type context string (may be null)
     */
    public void walkSelf(String rootBomId, List<BOMVisitor> visitors, String buildingType)
            throws SQLException {
        MBOM bom = loadBom(rootBomId);
        if (bom == null) {
            System.err.printf("[BOMWalker] walkSelf: BOM not found: %s%n", rootBomId);
            return;
        }
        // Synthetic root context: level=-1, line=null (no parent BOM line)
        NodeContext rootCtx = new NodeContext(null, null, bom, -1, buildingType);
        for (BOMVisitor v : visitors) v.onSubAssembly(rootCtx);
        walkChildren(bom, visitors, buildingType, 0);
        for (BOMVisitor v : visitors) v.onSubAssemblyComplete(rootCtx);
    }

    // ── Private traversal ────────────────────────────────────────────────────

    private void walkChildren(MBOM bom, List<BOMVisitor> visitors,
                               String buildingType, int level) throws SQLException {
        if (level > MAX_DEPTH) {
            System.err.printf("[BOMWalker] MAX_DEPTH exceeded at BOM %s — possible circular reference%n",
                bom.getBomId());
            return;
        }

        List<MBOMLine> lines = MBOMLine.getByBom(bomConn, bom.getBomId());

        for (MBOMLine line : lines) {
            String childProductId = line.getChildProductId();
            if (childProductId == null) {
                System.err.printf("[BOMWalker] m_bom_line bom_child_id=%d has null child_product_id — skipping%n",
                    line.getBomChildId());
                continue;
            }

            // ── Three-way dispatch: sub-assembly / PHANTOM / leaf ─────
            // Detection is structural — does child_product_id match a bom_id?
            // PHANTOM is the only component_type that matters (skipped at output).
            MBOM childBom = loadBom(childProductId);

            // Load M_Product from ERP.db (compConn) — master product catalog.
            // Use getAssembly() for sub-assemblies (stubs may be is_active=0).
            MProduct product = (childBom != null)
                ? MProduct.getAssembly(compConn, childProductId)
                : MProduct.get(compConn, childProductId);

            NodeContext ctx = new NodeContext(product, line, bom, level, buildingType);

            if (childBom != null) {
                // Sub-assembly — enter this BOM and walk its children
                for (BOMVisitor v : visitors) v.onSubAssembly(ctx);
                walkChildren(childBom, visitors, buildingType, level + 1);
                for (BOMVisitor v : visitors) v.onSubAssemblyComplete(ctx);
            } else if (product == null) {
                // Dangling reference — child_product_id is neither a BOM nor a known M_Product.
                // Likely a template BOM reference not yet populated (e.g. SH_LIVING_SET in
                // IFCtoBOM-generated *_BOM.db). Warn and skip — don't crash the walker.
                System.err.printf("[BOMWalker] Dangling reference: %s in BOM %s — "
                    + "no m_bom entry, no M_Product. Skipping.%n",
                    childProductId, bom.getBomId());
            } else if ("PHANTOM".equals(line.getComponentType())) {
                // PHANTOM: filler — no output. Tack coordinates consumed, element skipped.
                for (BOMVisitor v : visitors) v.onPhantom(ctx);
            } else {
                // Leaf — produces output element with geometry + placement.
                // TODO [FACTORIZE-v1 2026-03-17] Currently one onLeaf() call per
                // m_bom_line row. After factorization, a line with qty>1 should
                // still produce one onLeaf() call — the visitor (not the walker)
                // is responsible for expanding qty via verb formulas. The walker
                // stays 1:1 with lines; the visitor loops line.getQty() times
                // in onLeaf(). This keeps the walker verb-agnostic.
                for (BOMVisitor v : visitors) v.onLeaf(ctx);
            }
        }
    }

    private MBOM loadBom(String bomId) throws SQLException {
        MBOM bom = new MBOM(bomConn);
        // Use load() without active check — caller decides; assembly stubs may be inactive
        if (!bom.load(bomId)) return null;
        return bom;
    }

    // ── Static factory ───────────────────────────────────────────────────────

    /**
     * Create a BOMWalker connected to the standard BOM.db + ERP.db paths.
     */
    public static BOMWalker forDefaultDb() throws SQLException {
        Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));
        Connection compConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db");
        return new BOMWalker(bomConn, compConn);
    }
}
