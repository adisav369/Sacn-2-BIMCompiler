package com.bim.compiler.bom.walker;

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
 * <p>Walks {@code m_bom} / {@code m_bom_line} / {@code M_Product} from BOM.db and
 * fires {@link BOMVisitor} events for each node. Multiple visitors can be registered
 * to accumulate independent results (assembly structure, spatial placement) in a single
 * pass.
 *
 * <h3>Dispatch logic — structural, not by component_type</h3>
 * <p>Sub-assembly detection is structural: if {@code child_product_id} matches a
 * {@code bom_id} in {@code m_bom}, the walker recurses. This is independent of
 * {@code component_type}. The BOM hierarchy itself defines traversal.
 *
 * <p>For each {@code m_bom_line} child:
 * <ol>
 *   <li>Try {@code loadBom(child_product_id)}</li>
 *   <li>If child BOM exists → sub-assembly → {@link BOMVisitor#onMake}, recurse,
 *       {@link BOMVisitor#onMakeComplete}</li>
 *   <li>If PHANTOM → {@link BOMVisitor#onPhantom}</li>
 *   <li>Otherwise → leaf (BUY) → {@link BOMVisitor#onBuy}</li>
 * </ol>
 *
 * <h3>BUY vs MAKE (MRP semantics)</h3>
 * <p>All BOM leaves are BUY — their geometry exists in the library
 * ({@code M_Product_Image} → {@code LOD_Object} in component_library.db).
 * MAKE is a separate, pre-compilation process: when a component doesn't yet
 * exist in the library, the MAKE process (Mesh2Library, parametric fabrication)
 * creates it there first. By the time the walker runs, every leaf is BUY.
 *
 * <p>Usage:
 * <pre>{@code
 * try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db")) {
 *     BOMWalker walker = new BOMWalker(bomConn);
 *     walker.walk("BED_SET", List.of(myVisitor), null);
 * }
 * }</pre>
 */
public class BOMWalker {

    private final Connection bomConn;
    private static final int MAX_DEPTH = 20; // guard against circular BOM references

    public BOMWalker(Connection bomConn) {
        this.bomConn = bomConn;
    }

    // ── NodeContext ───────────────────────────────────────────────────────────

    /**
     * Context passed to each {@link BOMVisitor} event.
     *
     * <p>Provides the full hierarchy context at the point of dispatch:
     * the current product, the BOM line that introduced it, the owning BOM,
     * depth level, and the building/resolution context for spatial visitors.
     */
    public record NodeContext(
        MProduct product,          // current M_Product row
        MBOMLine line,             // current m_bom_line (null for root BOM entry)
        MBOM bom,                  // owning BOM of this node
        int level,                 // depth (0 = root BOM children)
        String buildingType        // from walk entrypoint
    ) {
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
     * <p>Does NOT fire {@code onMake}/{@code onMakeComplete} for the root BOM itself —
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
     * {@code onMake}/{@code onMakeComplete} events (level = -1, line = null).
     *
     * <p>This allows {@link BOMVisitor} implementations that accumulate state within
     * MAKE/MAKE_COMPLETE pairs to correctly handle the root BOM as an assembly.
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
        for (BOMVisitor v : visitors) v.onMake(rootCtx);
        walkChildren(bom, visitors, buildingType, 0);
        for (BOMVisitor v : visitors) v.onMakeComplete(rootCtx);
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

            // Structural sub-assembly detection: does child_product_id match a bom_id?
            MBOM childBom = loadBom(childProductId);

            // Load M_Product — use getAssembly() for sub-assemblies (stubs may be is_active=0)
            MProduct product = (childBom != null)
                ? MProduct.getAssembly(bomConn, childProductId)
                : MProduct.get(bomConn, childProductId);

            NodeContext ctx = new NodeContext(product, line, bom, level, buildingType);

            if (childBom != null) {
                // Sub-assembly: child_product_id has its own BOM → recurse
                for (BOMVisitor v : visitors) v.onMake(ctx);
                walkChildren(childBom, visitors, buildingType, level + 1);
                for (BOMVisitor v : visitors) v.onMakeComplete(ctx);
            } else if ("PHANTOM".equals(line.getComponentType())) {
                for (BOMVisitor v : visitors) v.onPhantom(ctx);
            } else {
                // Leaf: BUY — product exists in library. All leaves are BUY by the time
                // the walker runs. MAKE (fabrication) is a pre-compilation process that
                // creates library entries before compilation starts.
                for (BOMVisitor v : visitors) v.onBuy(ctx);
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
     * Create a BOMWalker connected to the standard BOM.db path.
     */
    public static BOMWalker forDefaultDb() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db");
        return new BOMWalker(conn);
    }
}
