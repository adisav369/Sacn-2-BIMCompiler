package com.bim.compiler.bom;

import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;
import com.bim.compiler.topology.Discipline;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import java.sql.*;
import java.util.List;
import java.util.Map;

/**
 * BOM Drop utility for the compilation pipeline.
 *
 * <p>Walks {@code m_bom}/{@code m_bom_line} and creates a matching
 * {@code C_Order} + {@code C_OrderLine} tree in the compile DB.
 * Same logic as {@code DesignerAPIImpl.explodeBomTree()} but writes
 * directly to the compile DB (no WorkOutputDAO dependency).
 *
 * <p>Each C_OrderLine stores {@code bom_child_id} — the FK back to
 * {@code m_bom_line.bom_child_id} — so that {@code OrderLineWalker}
 * can load the full MBOMLine attributes (verb_ref, rotation, material,
 * storey, element_ref) during compilation.
 *
 * <p>Session D additions: {@code locator_ref} (dot-separated BOM path)
 * and {@code is_reference_class} support exception-based ordering.
 *
 * <p>// Implementing S60_ERP_ALIGNMENT.md §2 — Witness: W-S60-DROP
 *
 * @see com.bim.compiler.bom.walker.OrderLineWalker
 */
public class BomDropper {

    private static final int MAX_DEPTH = 20;

    /**
     * Explode a building's BOM tree into C_Order + C_OrderLine in the compile DB.
     * Uses entry.docTypeId() as the C_Order_ID (backward-compatible default).
     *
     * @param compileDb  connection to the compile DB (has m_bom, m_bom_line, C_Order, C_OrderLine)
     * @param entry      building entry from C_DocType registry
     * @return number of leaf elements (sum of qty across all LEAF C_OrderLines)
     */
    // Implementing ProjectOrderBlueprint.md §14.3 Session 0 — Witness: W-PROJ-ID-1
    public static int drop(Connection compileDb, BuildingEntry entry) throws SQLException {
        return drop(compileDb, entry, entry.docTypeId());
    }

    /**
     * Explode a building's BOM tree into C_Order + C_OrderLine in the compile DB.
     *
     * @param compileDb  connection to the compile DB (has m_bom, m_bom_line, C_Order, C_OrderLine)
     * @param entry      building entry from C_DocType registry
     * @param orderId    explicit C_Order_ID (allows multiple orders of the same DocType)
     * @return number of leaf elements (sum of qty across all LEAF C_OrderLines)
     */
    // Implementing ProjectOrderBlueprint.md §14.3 Session 0 — Witness: W-PROJ-ID-1
    public static int drop(Connection compileDb, BuildingEntry entry, String orderId) throws SQLException {
        return drop(compileDb, entry, orderId, Map.of());
    }

    /**
     * Explode a building's BOM tree with inherited exceptions resolved from the
     * Ref_Order_ID chain. If the order has a parent, the inheritance chain is
     * walked and all ancestor exceptions are collected before explosion.
     *
     * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session E — Witness: W-INHERIT-1
     *
     * @param compileDb   connection to the compile DB
     * @param entry       building entry from C_DocType registry
     * @param orderId     explicit C_Order_ID (may have Ref_Order_ID set)
     * @return number of leaf elements (sum of qty across all LEAF C_OrderLines)
     */
    // Implementing ProjectOrderBlueprint.md §14.3 Session E — Witness: W-INHERIT-1
    public static int dropWithInheritance(Connection compileDb, BuildingEntry entry, String orderId)
            throws SQLException {
        Map<String, ExceptionLine> inherited = InheritanceResolver.resolveExceptions(compileDb, orderId);
        if (!inherited.isEmpty()) {
            System.out.printf("[BomDropper] Inheritance chain resolved: %d exception(s) for order %s%n",
                    inherited.size(), orderId);
        }
        return drop(compileDb, entry, orderId, inherited);
    }

    /**
     * Explode a building's BOM tree with exception-based mutations.
     *
     * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session D — Witness: W-EXCEPTION-1
     *
     * @param compileDb   connection to the compile DB
     * @param entry       building entry from C_DocType registry
     * @param orderId     explicit C_Order_ID
     * @param exceptions  map of locator_ref → ExceptionLine (qty=0 for Remove, is_reference_class for Compress)
     * @return number of leaf elements (sum of qty across all LEAF C_OrderLines)
     */
    // Implementing ProjectOrderBlueprint.md §14.3 Session D — Witness: W-EXCEPTION-1
    public static int drop(Connection compileDb, BuildingEntry entry, String orderId,
                           Map<String, ExceptionLine> exceptions) throws SQLException {
        // Find the BUILDING BOM for this entry via m_product_category_id + doc_sub_type
        String buildingBomId = findBuildingBom(compileDb, entry);
        if (buildingBomId == null) {
            System.err.printf("[BomDropper] No BUILDING BOM for %s (%s/%s) — skipping%n",
                    entry.id(), entry.docBaseType(), entry.docSubType());
            return 0;
        }

        // Create C_Order
        createOrder(compileDb, orderId, entry);

        // Explode BOM tree → C_OrderLine (with locator_ref path building)
        int[] leafCount = {0};
        int[] lineSeq = {0};
        explode(compileDb, orderId, buildingBomId, 0, "BUILDING",
                null, 0, leafCount, lineSeq, "", exceptions);

        System.out.printf("[BomDropper] %s → %d leaves (order=%s, bom=%s)%n",
                entry.id(), leafCount[0], orderId, buildingBomId);
        return leafCount[0];
    }

    /**
     * Exception line for Remove/Compress mutations on a specific locator_ref.
     *
     * @param qty              0 = Remove (skip subtree), N = override quantity
     * @param isReferenceClass true = Compress (instantiate qty copies at computed offsets)
     */
    // Implementing ProjectOrderBlueprint.md §14.3 Session D — Witness: W-EXCEPTION-1
    public record ExceptionLine(int qty, boolean isReferenceClass) {
        /** Remove mutation: qty=0 skips the entire subtree at the targeted locator_ref. */
        public static ExceptionLine remove() { return new ExceptionLine(0, false); }

        /** Compress mutation: is_reference_class + qty=N instantiates N copies at computed offsets. */
        public static ExceptionLine compress(int qty) { return new ExceptionLine(qty, true); }
    }

    /**
     * Find the BUILDING BOM matching this entry's M_Product_Category + DocSubType.
     * // Implementing DATA_MODEL.md §7 — DocBaseType → M_Product_Category alignment
     */
    private static String findBuildingBom(Connection conn, BuildingEntry entry) throws SQLException {
        String sql = "SELECT bom_id FROM m_bom "
                   + "WHERE bom_type = 'BUILDING' AND m_product_category_id = ? AND doc_sub_type = ? "
                   + "AND is_active = 1 ORDER BY seq_no LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.docBaseType());
            ps.setString(2, entry.docSubType());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * Create C_Order row for this compilation.
     */
    private static void createOrder(Connection conn, String orderId, BuildingEntry entry)
            throws SQLException {
        // Delete any existing order (re-drop is idempotent)
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM C_OrderLine WHERE C_Order_ID = '" + orderId + "'");
            stmt.execute("DELETE FROM C_Order WHERE C_Order_ID = '" + orderId + "'");
        }

        String sql = "INSERT INTO C_Order (C_Order_ID, C_DocType_ID, Name, DocStatus, "
                   + "aabb_width_mm, aabb_depth_mm, aabb_height_mm) "
                   + "VALUES (?, ?, ?, 'DR', ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            ps.setString(2, entry.docTypeId());
            ps.setString(3, entry.name());
            ps.setDouble(4, entry.aabbWidthMm());
            ps.setDouble(5, entry.aabbDepthMm());
            ps.setDouble(6, entry.aabbHeightMm());
            ps.executeUpdate();
        }
    }

    /**
     * Recursively explode a BOM into C_OrderLine rows, building locator_ref paths.
     *
     * <p>Structural dispatch (same as BOMWalker — component_type NOT checked):
     * <ol>
     *   <li>Sub-assembly (child_product_id matches a bom_id) → recurse</li>
     *   <li>PHANTOM → skip (only component_type that matters)</li>
     *   <li>Otherwise → leaf with geometry</li>
     * </ol>
     *
     * <p>locator_ref is built as a dot-separated path using the M_Product_Category
     * at each level. Root gets no prefix; children append their category code.
     * Example: "" → "GF" → "GF.LI" → "GF.LI.SOFA_001"
     *
     * @return the C_OrderLine_ID of the inserted node (auto-generated)
     */
    // Implementing ProjectOrderBlueprint.md §14.3 Session D — Witness: W-EXCEPTION-1
    private static int explode(Connection conn, String orderId, String bomId,
                                int parentLineId, String hostType, String productCategory,
                                int depth, int[] leafCount, int[] lineSeq,
                                String parentLocator, Map<String, ExceptionLine> exceptions)
            throws SQLException {
        if (depth > MAX_DEPTH) {
            System.err.printf("[BomDropper] MAX_DEPTH exceeded at BOM %s%n", bomId);
            return 0;
        }

        // Load BOM for AABB
        MBOM bom = new MBOM(conn);
        if (!bom.load(bomId)) return 0;

        if (productCategory == null) productCategory = bom.getProductCategory();

        // Build locator_ref for this node
        String locatorRef = buildLocatorRef(parentLocator, productCategory, bomId);

        // Check for Remove exception (qty=0 → skip entire subtree)
        ExceptionLine exception = exceptions.get(locatorRef);
        if (exception != null && exception.qty() == 0) {
            System.out.printf("[BomDropper] REMOVE: skipping subtree at locator_ref=%s%n", locatorRef);
            return 0;
        }

        // Check for Compress exception (is_reference_class + qty=N)
        boolean isRefClass = exception != null && exception.isReferenceClass();
        int overrideQty = exception != null ? exception.qty() : 1;

        // Insert C_OrderLine for this assembly node
        lineSeq[0] += 10;
        int lineId = insertLine(conn, orderId, parentLineId == 0 ? null : parentLineId,
                lineSeq[0], bomId, hostType, productCategory, 0,
                0, 0, 0,
                bom.getAabbWidthMm(), bom.getAabbDepthMm(), bom.getAabbHeightMm(),
                isRefClass ? overrideQty : 1, locatorRef, isRefClass);

        // If reference class, don't explode children — they'll be instantiated at walk time
        if (isRefClass) {
            System.out.printf("[BomDropper] COMPRESS: locator_ref=%s, qty=%d (reference class)%n",
                    locatorRef, overrideQty);
            return lineId;
        }

        // Walk children
        List<MBOMLine> lines = MBOMLine.getByBom(conn, bomId);
        for (MBOMLine line : lines) {
            String childProductId = line.getChildProductId();
            if (childProductId == null) continue;

            // Check if child is a sub-assembly BOM
            MBOM childBom = new MBOM(conn);
            boolean isBom = childBom.load(childProductId);

            if (isBom) {
                // Sub-assembly — recurse
                String childHostType = deriveHostType(depth + 1);
                explodeAssembly(conn, orderId, childProductId,
                        lineId, childHostType, childBom.getProductCategory(),
                        depth + 1, leafCount, lineSeq, line.getBomChildId(),
                        locatorRef, exceptions);

            } else if ("PHANTOM".equals(line.getComponentType())) {
                // PHANTOM — skip (only component_type that matters)
                continue;

            } else {
                // Leaf product — structural detection: no matching bom_id = leaf.
                String leafLocator = buildLocatorRef(locatorRef, null, childProductId);

                // Check for Remove on leaf
                ExceptionLine leafEx = exceptions.get(leafLocator);
                if (leafEx != null && leafEx.qty() == 0) {
                    System.out.printf("[BomDropper] REMOVE: skipping leaf at locator_ref=%s%n", leafLocator);
                    continue;
                }

                int qty = line.getQty();
                lineSeq[0] += 10;
                insertLine(conn, orderId, lineId, lineSeq[0],
                        childProductId, "LEAF", productCategory, line.getBomChildId(),
                        line.getDx(), line.getDy(), line.getDz(),
                        line.getAllocatedWidthMm(), line.getAllocatedDepthMm(),
                        line.getAllocatedHeightMm(), qty, leafLocator, false);
                leafCount[0] += qty;
            }
        }

        return lineId;
    }

    /**
     * Explode a sub-assembly BOM, storing the parent MAKE line's bom_child_id
     * on the assembly's C_OrderLine.
     */
    private static int explodeAssembly(Connection conn, String orderId, String bomId,
                                        int parentLineId, String hostType, String productCategory,
                                        int depth, int[] leafCount, int[] lineSeq,
                                        int makeBomChildId,
                                        String parentLocator, Map<String, ExceptionLine> exceptions)
            throws SQLException {
        if (depth > MAX_DEPTH) return 0;

        MBOM bom = new MBOM(conn);
        if (!bom.load(bomId)) return 0;

        if (productCategory == null) productCategory = bom.getProductCategory();

        // Build locator_ref for this node
        String locatorRef = buildLocatorRef(parentLocator, productCategory, bomId);

        // Check for Remove exception
        ExceptionLine exception = exceptions.get(locatorRef);
        if (exception != null && exception.qty() == 0) {
            System.out.printf("[BomDropper] REMOVE: skipping subtree at locator_ref=%s%n", locatorRef);
            return 0;
        }

        // Check for Compress exception
        boolean isRefClass = exception != null && exception.isReferenceClass();
        int overrideQty = exception != null ? exception.qty() : 1;

        // Insert assembly C_OrderLine with the MAKE line's bom_child_id
        lineSeq[0] += 10;
        int lineId = insertLine(conn, orderId, parentLineId, lineSeq[0],
                bomId, hostType, productCategory, makeBomChildId,
                0, 0, 0,
                bom.getAabbWidthMm(), bom.getAabbDepthMm(), bom.getAabbHeightMm(),
                isRefClass ? overrideQty : 1, locatorRef, isRefClass);

        // If reference class, don't explode children
        if (isRefClass) {
            System.out.printf("[BomDropper] COMPRESS: locator_ref=%s, qty=%d (reference class)%n",
                    locatorRef, overrideQty);
            return lineId;
        }

        // Walk children (same logic as explode)
        List<MBOMLine> lines = MBOMLine.getByBom(conn, bomId);
        for (MBOMLine line : lines) {
            String childProductId = line.getChildProductId();
            if (childProductId == null) continue;

            MBOM childBom = new MBOM(conn);
            boolean isBom = childBom.load(childProductId);

            if (isBom) {
                String childHostType = deriveHostType(depth + 1);
                explodeAssembly(conn, orderId, childProductId, lineId,
                        childHostType, childBom.getProductCategory(),
                        depth + 1, leafCount, lineSeq, line.getBomChildId(),
                        locatorRef, exceptions);

            } else if ("PHANTOM".equals(line.getComponentType())) {
                continue;

            } else {
                String leafLocator = buildLocatorRef(locatorRef, null, childProductId);

                ExceptionLine leafEx = exceptions.get(leafLocator);
                if (leafEx != null && leafEx.qty() == 0) {
                    System.out.printf("[BomDropper] REMOVE: skipping leaf at locator_ref=%s%n", leafLocator);
                    continue;
                }

                int qty = line.getQty();
                lineSeq[0] += 10;
                insertLine(conn, orderId, lineId, lineSeq[0],
                        childProductId, "LEAF", productCategory, line.getBomChildId(),
                        line.getDx(), line.getDy(), line.getDz(),
                        line.getAllocatedWidthMm(), line.getAllocatedDepthMm(),
                        line.getAllocatedHeightMm(), qty, leafLocator, false);
                leafCount[0] += qty;
            }
        }

        return lineId;
    }

    /**
     * Build a locator_ref path segment. Uses M_Product_Category if available,
     * otherwise falls back to bom_id/product_id as the segment name.
     *
     * <p>Syntax: dot-separated from root. Example: "GF.LI.SOFA_001"
     * (floor category . room category . leaf product).
     * See BBC.md §3.6 for the full locator_ref addressing spec.
     */
    static String buildLocatorRef(String parentLocator, String productCategory, String nodeId) {
        String segment = (productCategory != null && !productCategory.isEmpty())
                ? productCategory : nodeId;
        if (parentLocator == null || parentLocator.isEmpty()) return segment;
        return parentLocator + "." + segment;
    }

    /**
     * Insert a single C_OrderLine row and return its auto-generated ID.
     */
    // Implementing ProjectOrderBlueprint.md §14.3 Session D — Witness: W-EXCEPTION-1
    private static int insertLine(Connection conn, String orderId, Integer parentLineId,
                                   int lineSeq, String familyRef, String hostType,
                                   String productCategory, int bomChildId,
                                   double dx, double dy, double dz,
                                   double widthMm, double depthMm, double heightMm,
                                   int qty, String locatorRef, boolean isReferenceClass)
            throws SQLException {
        // Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 5 — Witness: W-DV-DISC-ORG
        String discipline = deriveDiscipline(productCategory);
        int adOrgId = deriveAD_Org_ID(discipline);
        String sql = "INSERT INTO C_OrderLine "
                   + "(C_Order_ID, Parent_OrderLine_ID, Line, family_ref, host_type, "
                   + " m_product_category_id, bom_child_id, dx, dy, dz, "
                   + " aabb_width_mm, aabb_depth_mm, aabb_height_mm, M_Product_ID, Discipline, AD_Org_ID, Qty, "
                   + " locator_ref, is_reference_class) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, orderId);
            if (parentLineId != null) ps.setInt(2, parentLineId);
            else ps.setNull(2, Types.INTEGER);
            ps.setInt(3, lineSeq);
            ps.setString(4, familyRef);
            ps.setString(5, hostType);
            ps.setString(6, productCategory);
            if (bomChildId > 0) ps.setInt(7, bomChildId);
            else ps.setNull(7, Types.INTEGER);
            ps.setDouble(8, dx);
            ps.setDouble(9, dy);
            ps.setDouble(10, dz);
            ps.setDouble(11, widthMm);
            ps.setDouble(12, depthMm);
            ps.setDouble(13, heightMm);
            ps.setString(14, familyRef);  // M_Product_ID = family_ref
            ps.setString(15, discipline);
            ps.setInt(16, adOrgId);
            ps.setInt(17, qty);
            ps.setString(18, locatorRef);
            ps.setInt(19, isReferenceClass ? 1 : 0);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    private static String deriveHostType(int depth) {
        return switch (depth) {
            case 0 -> "BUILDING";
            case 1 -> "FLOOR";
            default -> "ROOM";
        };
    }

    /**
     * Derive Discipline from m_product_category_id — same logic as W003 backfill.
     * RF/STR/SL → STR, FP → FPR, MEP/ELEC/PLB/ACMV → pass-through, else ARC.
     */
    static String deriveDiscipline(String productCategory) {
        if (productCategory == null) return "ARC";
        return switch (productCategory) {
            case "RF", "STR", "SL" -> "STR";
            case "FP" -> "FPR";
            case "MEP", "ELEC", "PLB", "ACMV" -> productCategory;
            default -> "ARC";
        };
    }

    /**
     * Resolve TEXT discipline code → AD_Org_ID via Discipline enum.
     * Handles legacy "FPR" → FP mapping (W003 artifact).
     * Falls back to 0 (Shared/*) for unknown codes.
     */
    static int deriveAD_Org_ID(String discipline) {
        if ("FPR".equals(discipline)) discipline = "FP";  // W003 legacy
        Discipline d = Discipline.fromString(discipline);
        return d != null ? d.getAD_Org_ID() : 0;
    }
}
