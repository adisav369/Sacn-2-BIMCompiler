package com.bim.compiler.bom;

import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;
import com.bim.compiler.topology.Discipline;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import com.bim.orm.BIMLogger;

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
 * <p>Each C_OrderLine stores {@code M_BOM_Line_ID} — the FK back to
 * {@code m_bom_line.M_BOM_Line_ID} — so that {@code OrderLineWalker}
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
        return drop(compileDb, entry, entry.docTypeId(), Map.of(), 0);
    }

    public static int drop(Connection compileDb, BuildingEntry entry, String orderId) throws SQLException {
        return drop(compileDb, entry, orderId, Map.of(), 0);
    }

    /** Full overload with C_BPartner_ID — sets it on C_Order header. */
    public static int drop(Connection compileDb, BuildingEntry entry, String orderId, int bpartnerId) throws SQLException {
        return drop(compileDb, entry, orderId, Map.of(), bpartnerId);
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
            BIMLogger.fine("BOMDROP", "Inheritance chain resolved: {} exception(s) for order {}",
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
        return drop(compileDb, entry, orderId, exceptions, 0);
    }

    /** Core drop — bpartnerId is set on C_Order header (0 = unknown). */
    private static int drop(Connection compileDb, BuildingEntry entry, String orderId,
                           Map<String, ExceptionLine> exceptions, int bpartnerId) throws SQLException {
        // Find the BUILDING BOM for this entry via m_product_category_id + doc_sub_type
        String buildingBomId = findBuildingBom(compileDb, entry);
        if (buildingBomId == null) {
            BIMLogger.fine("BOMDROP", "No BUILDING BOM for {} ({}/{}) — skipping",
                    entry.id(), entry.mProductCategoryId(), entry.docSubType());
            return 0;
        }

        BIMLogger.fine("BOMDROP", "{}: root BOM={}, category={}, doc_sub_type={}",
                entry.id(), buildingBomId, entry.mProductCategoryId(), entry.docSubType());

        // Create C_Order (iDempiere: header carries C_BPartner_ID)
        createOrder(compileDb, orderId, entry, bpartnerId);

        // Explode BOM tree → C_OrderLine (with locator_ref path building)
        int[] leafCount = {0};
        int[] lineSeq = {0};
        explode(compileDb, orderId, buildingBomId, 0, "BUILDING",
                null, 0, leafCount, lineSeq, "", exceptions);

        // Implementing ProjectOrderBlueprint.md §1.1 + DISC_VALIDATION_DB_SRS.md §10.4.6 — Witness: W-ADD-1
        // Add mutations: append discipline recipe C_OrderLines as siblings of root BUILDING line.
        // Each Add line's replacementProductId holds the shared recipe product (e.g., FP_SYSTEM).
        // Generative verb execution (ROUTE/FRAME/TILE/WIRE) is future work — the lines exist
        // in the order tree but produce 0 compiled elements until verb Strategy is wired.
        for (Map.Entry<String, ExceptionLine> ex : exceptions.entrySet()) {
            if (ex.getValue().type() == MutationType.ADD && ex.getValue().replacementProductId() != null) {
                String recipeProductId = ex.getValue().replacementProductId();
                String addLocator = ex.getKey();
                Discipline disc = resolveDiscipline(addLocator);  // locator key = discipline code
                lineSeq[0] += 10;
                insertLine(compileDb, orderId, null, lineSeq[0],
                        recipeProductId, "DISCIPLINE", addLocator, 0,
                        0, 0, 0, 0, 0, 0, 1, addLocator, false);
                BIMLogger.fine("BOMDROP", "ADD: {} (discipline={}, AD_Org={}) at locator_ref={}",
                        recipeProductId, disc.name(), disc.getAD_Org_ID(), addLocator);
            }
        }

        // Mutation summary
        int removeCount = 0, replaceCount = 0, addCount = 0, compressCount = 0;
        for (ExceptionLine ex : exceptions.values()) {
            switch (ex.type()) {
                case REMOVE   -> removeCount++;
                case REPLACE  -> replaceCount++;
                case ADD      -> addCount++;
                case COMPRESS -> compressCount++;
            }
        }
        BIMLogger.fine("BOMDROP", "{}: {} leaves, {} lines, {} exceptions applied",
                entry.id(), leafCount[0], lineSeq[0] / 10, exceptions.size());
        BIMLogger.fine("BOMDROP", "{}: mutations: {} REMOVE, {} REPLACE, {} ADD, {} COMPRESS",
                entry.id(), removeCount, replaceCount, addCount, compressCount);
        return leafCount[0];
    }

    /** Mutation type for the four-operation exception algebra (ProjectOrderBlueprint §1.1). */
    public enum MutationType { REMOVE, COMPRESS, REPLACE, ADD }

    /**
     * Exception line for Remove/Compress/Replace/Add mutations on a specific locator_ref.
     *
     * @param qty                  0 = Remove (skip subtree), N = override quantity
     * @param isReferenceClass     true = Compress (instantiate qty copies at computed offsets)
     * @param replacementProductId non-null for Replace (swap) and Add (recipe product)
     * @param type                 which of the four mutations this represents
     */
    // Implementing ProjectOrderBlueprint.md §1.1 — Witness: W-REPLACE-1, W-ADD-1
    public record ExceptionLine(int qty, boolean isReferenceClass,
                                String replacementProductId, MutationType type) {
        /** Remove mutation: qty=0 skips the entire subtree at the targeted locator_ref. */
        public static ExceptionLine remove() {
            return new ExceptionLine(0, false, null, MutationType.REMOVE);
        }

        /** Compress mutation: is_reference_class + qty=N instantiates N copies at computed offsets. */
        public static ExceptionLine compress(int qty) {
            return new ExceptionLine(qty, true, null, MutationType.COMPRESS);
        }

        /** Replace mutation: swap product at locator_ref. Category-constrained (same shelf). */
        // Implementing ProjectOrderBlueprint.md §1.1 — Witness: W-REPLACE-1
        public static ExceptionLine replace(String replacementProductId) {
            return new ExceptionLine(1, false, replacementProductId, MutationType.REPLACE);
        }

        /** Add mutation: append discipline recipe C_OrderLine (sibling of root BUILDING line). */
        // Implementing ProjectOrderBlueprint.md §1.1 + DISC_VALIDATION_DB_SRS.md §10.4.6 — Witness: W-ADD-1
        public static ExceptionLine add(String recipeProductId) {
            return new ExceptionLine(1, false, recipeProductId, MutationType.ADD);
        }
    }

    /**
     * Find the root BOM matching this entry's M_Product_Category + doc_sub_type.
     * // Implementing DISC_VALIDATION_DB_SRS.md §10.4.5 — Witness: W-TREE-1
     * Root = BOM with no parent m_bom_line (replaces bom_type = 'BUILDING' query).
     */
    private static String findBuildingBom(Connection conn, BuildingEntry entry) throws SQLException {
        // Implementing BBC.md §14.3 IDV-1 — Witness: W-PK-MBOM
        String sql = "SELECT Value FROM m_bom "
                   + "WHERE Value NOT IN (SELECT child_product_id FROM m_bom_line WHERE is_active = 1) "
                   + "AND m_product_category_id = (SELECT M_Product_Category_ID FROM M_Product_Category WHERE Value = ?) "
                   + "AND doc_sub_type = ? "
                   + "AND is_active = 1 ORDER BY seq_no LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.mProductCategoryId());
            ps.setString(2, entry.docSubType());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * Create C_Order row for this compilation.
     */
    private static void createOrder(Connection conn, String orderId, BuildingEntry entry, int bpartnerId)
            throws SQLException {
        // Ensure C_Order + C_OrderLine tables exist (test path may skip schema init)
        // Delete any existing order (re-drop is idempotent)
        // Tier 2: C_Order_ID is INTEGER PK. Value holds old text key.
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS C_Order ("
                    + "C_Order_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "Value TEXT NOT NULL UNIQUE, C_DocType_ID TEXT, Name TEXT, DocStatus TEXT, "
                    + "aabb_width_mm REAL, aabb_depth_mm REAL, aabb_height_mm REAL, "
                    + "C_BPartner_ID INTEGER, Ref_Order_ID TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS C_OrderLine ("
                    + "C_OrderLine_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "C_Order_ID TEXT, Parent_OrderLine_ID INTEGER, Line INTEGER, "
                    + "family_ref TEXT, host_type TEXT, m_product_category_id TEXT, "
                    + "M_BOM_Line_ID INTEGER, dx REAL DEFAULT 0, dy REAL DEFAULT 0, dz REAL DEFAULT 0, "
                    + "aabb_width_mm REAL DEFAULT 0, aabb_depth_mm REAL DEFAULT 0, aabb_height_mm REAL DEFAULT 0, "
                    + "M_Product_ID TEXT, Discipline TEXT DEFAULT 'ARC', AD_Org_ID INTEGER DEFAULT 0, "
                    + "Qty INTEGER DEFAULT 1, locator_ref TEXT, is_reference_class INTEGER DEFAULT 0, "
                    + "IsActive TEXT DEFAULT 'Y')");
            stmt.execute("DELETE FROM C_OrderLine WHERE C_Order_ID = '" + orderId + "'");
            stmt.execute("DELETE FROM C_Order WHERE Value = '" + orderId + "'");
        }

        String sql = "INSERT INTO C_Order (Value, C_DocType_ID, Name, DocStatus, "
                   + "aabb_width_mm, aabb_depth_mm, aabb_height_mm, C_BPartner_ID) "
                   + "VALUES (?, ?, ?, 'DR', ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            ps.setString(2, entry.docTypeId());
            ps.setString(3, entry.name());
            ps.setDouble(4, entry.aabbWidthMm());
            ps.setDouble(5, entry.aabbDepthMm());
            ps.setDouble(6, entry.aabbHeightMm());
            if (bpartnerId > 0) ps.setInt(7, bpartnerId); else ps.setNull(7, java.sql.Types.INTEGER);
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
            BIMLogger.fine("BOMDROP", "MAX_DEPTH exceeded at BOM {}", bomId);
            return 0;
        }

        // Load BOM for AABB — loadByValue: M_BOM_ID is INTEGER PK, Value is business key
        MBOM bom = new MBOM(conn);
        if (!bom.loadByValue(bomId)) return 0;

        if (productCategory == null) productCategory = bom.getProductCategory();

        // Build locator_ref for this node
        String locatorRef = buildLocatorRef(parentLocator, productCategory, bomId);

        // Check for Remove exception (qty=0 → skip entire subtree)
        ExceptionLine exception = exceptions.get(locatorRef);
        if (exception != null && exception.qty() == 0) {
            BIMLogger.fine("BOMDROP", "REMOVE: skipping subtree at locator_ref={}", locatorRef);
            return 0;
        }

        // Check for Compress exception (is_reference_class + qty=N)
        boolean isRefClass = exception != null && exception.isReferenceClass();
        int overrideQty = exception != null ? exception.qty() : 1;

        // Insert C_OrderLine for this assembly node (root BOM stores world origin)
        lineSeq[0] += 10;
        int lineId = insertLine(conn, orderId, parentLineId == 0 ? null : parentLineId,
                lineSeq[0], bomId, hostType, productCategory, 0,
                bom.getOriginX(), bom.getOriginY(), bom.getOriginZ(),
                bom.getAabbWidthMm(), bom.getAabbDepthMm(), bom.getAabbHeightMm(),
                isRefClass ? overrideQty : 1, locatorRef, isRefClass);

        // If reference class, don't explode children — they'll be instantiated at walk time
        if (isRefClass) {
            BIMLogger.fine("BOMDROP", "COMPRESS: locator_ref={}, qty={} (reference class)",
                    locatorRef, overrideQty);
            return lineId;
        }

        // Walk children
        List<MBOMLine> lines = MBOMLine.getByBom(conn, bomId);
        for (MBOMLine line : lines) {
            String childProductId = line.getChildProductId();
            if (childProductId == null) continue;

            // Check if child is a sub-assembly BOM — loadByValue: Value is business key
            MBOM childBom = new MBOM(conn);
            boolean isBom = childBom.loadByValue(childProductId);

            if (isBom) {
                // Build locator_ref for this child to check Replace exception
                String childLocator = buildLocatorRef(locatorRef, childBom.getProductCategory(), childProductId);
                ExceptionLine childEx = exceptions.get(childLocator);

                // Implementing ProjectOrderBlueprint.md §1.1 — Witness: W-REPLACE-1
                String actualChildId = childProductId;
                MBOM actualChildBom = childBom;
                if (childEx != null && childEx.type() == MutationType.REPLACE
                        && childEx.replacementProductId() != null) {
                    MBOM replacementBom = new MBOM(conn);
                    if (replacementBom.loadByValue(childEx.replacementProductId())) {
                        // Category constraint: replacement must be same shelf
                        String origCat = childBom.getProductCategory();
                        String replCat = replacementBom.getProductCategory();
                        if (origCat != null && origCat.equals(replCat)) {
                            actualChildId = childEx.replacementProductId();
                            actualChildBom = replacementBom;
                            BIMLogger.fine("BOMDROP", "REPLACE: {} → {} at locator_ref={} (category={})",
                                    childProductId, actualChildId, childLocator, origCat);
                        } else {
                            BIMLogger.fine("BOMDROP", "REPLACE REJECTED: category mismatch at {} ({} ≠ {})",
                                    childLocator, origCat, replCat);
                        }
                    }
                }

                // Sub-assembly — recurse (into original or replacement)
                String childHostType = deriveHostType(depth + 1);
                explodeAssembly(conn, orderId, actualChildId,
                        lineId, childHostType, actualChildBom.getProductCategory(),
                        depth + 1, leafCount, lineSeq, line.getBomLineId(),
                        line.getDx(), line.getDy(), line.getDz(),
                        locatorRef, exceptions);

            } else {
                // Leaf product — structural detection: no matching bom_id = leaf.
                String leafLocator = buildLocatorRef(locatorRef, null, childProductId);

                // Check for Remove on leaf
                ExceptionLine leafEx = exceptions.get(leafLocator);
                if (leafEx != null && leafEx.qty() == 0) {
                    BIMLogger.fine("BOMDROP", "REMOVE: skipping leaf at locator_ref={}", leafLocator);
                    continue;
                }

                // Check for Replace on leaf (swap product ID)
                // Implementing ProjectOrderBlueprint.md §1.1 — Witness: W-REPLACE-1
                String actualLeafId = childProductId;
                if (leafEx != null && leafEx.type() == MutationType.REPLACE
                        && leafEx.replacementProductId() != null) {
                    actualLeafId = leafEx.replacementProductId();
                    BIMLogger.fine("BOMDROP", "REPLACE: {} → {} at locator_ref={}",
                            childProductId, actualLeafId, leafLocator);
                }

                int qty = line.getQty();
                lineSeq[0] += 10;
                insertLine(conn, orderId, lineId, lineSeq[0],
                        actualLeafId, "LEAF", productCategory, line.getBomLineId(),
                        line.getDx(), line.getDy(), line.getDz(),
                        line.getAllocatedWidthMm(), line.getAllocatedDepthMm(),
                        line.getAllocatedHeightMm(), qty, leafLocator, false,
                        line.getAdOrgId());
                leafCount[0] += qty;
            }
        }

        return lineId;
    }

    /**
     * Explode a sub-assembly BOM, storing the parent MAKE line's M_BOM_Line_ID
     * on the assembly's C_OrderLine.
     *
     * @param makeDx  MAKE line dx offset (parent-relative, metres) for container origin
     * @param makeDy  MAKE line dy offset
     * @param makeDz  MAKE line dz offset
     */
    private static int explodeAssembly(Connection conn, String orderId, String bomId,
                                        int parentLineId, String hostType, String productCategory,
                                        int depth, int[] leafCount, int[] lineSeq,
                                        int makeBomChildId,
                                        double makeDx, double makeDy, double makeDz,
                                        String parentLocator, Map<String, ExceptionLine> exceptions)
            throws SQLException {
        if (depth > MAX_DEPTH) return 0;

        MBOM bom = new MBOM(conn);
        if (!bom.loadByValue(bomId)) return 0;

        if (productCategory == null) productCategory = bom.getProductCategory();

        // Build locator_ref for this node
        String locatorRef = buildLocatorRef(parentLocator, productCategory, bomId);

        // Check for Remove exception
        ExceptionLine exception = exceptions.get(locatorRef);
        if (exception != null && exception.qty() == 0) {
            BIMLogger.fine("BOMDROP", "REMOVE: skipping subtree at locator_ref={}", locatorRef);
            return 0;
        }

        // Check for Compress exception
        boolean isRefClass = exception != null && exception.isReferenceClass();
        int overrideQty = exception != null ? exception.qty() : 1;

        // Insert assembly C_OrderLine with the MAKE line's offset + child BOM origin
        // P91 follow-up: persist container origin for BomTreeProver P-PARENT/P-TACK
        double containerDx = makeDx + bom.getOriginX();
        double containerDy = makeDy + bom.getOriginY();
        double containerDz = makeDz + bom.getOriginZ();
        lineSeq[0] += 10;
        int lineId = insertLine(conn, orderId, parentLineId, lineSeq[0],
                bomId, hostType, productCategory, makeBomChildId,
                containerDx, containerDy, containerDz,
                bom.getAabbWidthMm(), bom.getAabbDepthMm(), bom.getAabbHeightMm(),
                isRefClass ? overrideQty : 1, locatorRef, isRefClass);

        // If reference class, don't explode children
        if (isRefClass) {
            BIMLogger.fine("BOMDROP", "COMPRESS: locator_ref={}, qty={} (reference class)",
                    locatorRef, overrideQty);
            return lineId;
        }

        // Walk children (same logic as explode, with Replace support)
        List<MBOMLine> lines = MBOMLine.getByBom(conn, bomId);
        for (MBOMLine line : lines) {
            String childProductId = line.getChildProductId();
            if (childProductId == null) continue;

            MBOM childBom = new MBOM(conn);
            boolean isBom = childBom.loadByValue(childProductId);

            if (isBom) {
                // Build locator_ref for this child to check Replace exception
                String childLocator = buildLocatorRef(locatorRef, childBom.getProductCategory(), childProductId);
                ExceptionLine childEx = exceptions.get(childLocator);

                // Implementing ProjectOrderBlueprint.md §1.1 — Witness: W-REPLACE-1
                String actualChildId = childProductId;
                MBOM actualChildBom = childBom;
                if (childEx != null && childEx.type() == MutationType.REPLACE
                        && childEx.replacementProductId() != null) {
                    MBOM replacementBom = new MBOM(conn);
                    if (replacementBom.loadByValue(childEx.replacementProductId())) {
                        String origCat = childBom.getProductCategory();
                        String replCat = replacementBom.getProductCategory();
                        if (origCat != null && origCat.equals(replCat)) {
                            actualChildId = childEx.replacementProductId();
                            actualChildBom = replacementBom;
                            BIMLogger.fine("BOMDROP", "REPLACE: {} → {} at locator_ref={} (category={})",
                                    childProductId, actualChildId, childLocator, origCat);
                        } else {
                            BIMLogger.fine("BOMDROP", "REPLACE REJECTED: category mismatch at {} ({} ≠ {})",
                                    childLocator, origCat, replCat);
                        }
                    }
                }

                String childHostType = deriveHostType(depth + 1);
                explodeAssembly(conn, orderId, actualChildId, lineId,
                        childHostType, actualChildBom.getProductCategory(),
                        depth + 1, leafCount, lineSeq, line.getBomLineId(),
                        line.getDx(), line.getDy(), line.getDz(),
                        locatorRef, exceptions);

            } else {
                String leafLocator = buildLocatorRef(locatorRef, null, childProductId);

                ExceptionLine leafEx = exceptions.get(leafLocator);
                if (leafEx != null && leafEx.qty() == 0) {
                    BIMLogger.fine("BOMDROP", "REMOVE: skipping leaf at locator_ref={}", leafLocator);
                    continue;
                }

                // Check for Replace on leaf (swap product ID)
                // Implementing ProjectOrderBlueprint.md §1.1 — Witness: W-REPLACE-1
                String actualLeafId = childProductId;
                if (leafEx != null && leafEx.type() == MutationType.REPLACE
                        && leafEx.replacementProductId() != null) {
                    actualLeafId = leafEx.replacementProductId();
                    BIMLogger.fine("BOMDROP", "REPLACE: {} → {} at locator_ref={}",
                            childProductId, actualLeafId, leafLocator);
                }

                int qty = line.getQty();
                lineSeq[0] += 10;
                insertLine(conn, orderId, lineId, lineSeq[0],
                        actualLeafId, "LEAF", productCategory, line.getBomLineId(),
                        line.getDx(), line.getDy(), line.getDz(),
                        line.getAllocatedWidthMm(), line.getAllocatedDepthMm(),
                        line.getAllocatedHeightMm(), qty, leafLocator, false,
                        line.getAdOrgId());
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
        return insertLine(conn, orderId, parentLineId, lineSeq, familyRef, hostType,
                productCategory, bomChildId, dx, dy, dz, widthMm, depthMm, heightMm,
                qty, locatorRef, isReferenceClass, 0);
    }

    /**
     * Insert a single C_OrderLine row with explicit AD_Org_ID override.
     * // Implementing DISC_VALIDATION_DB_SRS.md §10.4.4 — Witness: W-DV-DISC-ORG
     *
     * @param lineAdOrgId  AD_Org_ID from m_bom_line (extraction). If > 0, used directly;
     *                     otherwise falls back to category-based resolveDiscipline().
     */
    private static int insertLine(Connection conn, String orderId, Integer parentLineId,
                                   int lineSeq, String familyRef, String hostType,
                                   String productCategory, int bomChildId,
                                   double dx, double dy, double dz,
                                   double widthMm, double depthMm, double heightMm,
                                   int qty, String locatorRef, boolean isReferenceClass,
                                   int lineAdOrgId)
            throws SQLException {
        // Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 5-6 — Witness: W-DV-DISC-ORG
        // Priority: (1) m_bom_line.AD_Org_ID if > 0, (2) category-based fallback, (3) ARC
        Discipline disc;
        if (lineAdOrgId > 0) {
            disc = Discipline.fromAD_Org_ID(lineAdOrgId);
            if (disc == null) disc = resolveDiscipline(productCategory);
        } else {
            disc = resolveDiscipline(productCategory);
        }
        String discipline = disc.name();
        int adOrgId = disc.getAD_Org_ID();
        String sql = "INSERT INTO C_OrderLine "
                   + "(C_Order_ID, Parent_OrderLine_ID, Line, family_ref, host_type, "
                   + " m_product_category_id, M_BOM_Line_ID, dx, dy, dz, "
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
     * Resolve m_product_category_id → Discipline enum directly.
     * RF/STR/SL → STR, FP → FP, ELEC → ELEC, ACMV → ACMV, else ARC.
     * <p>Replaces deriveDiscipline() + deriveAD_Org_ID() two-step chain.
     */
    // Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 5-6 — Witness: W-DV-DISC-ORG
    static Discipline resolveDiscipline(String productCategory) {
        if (productCategory == null) return Discipline.ARC;
        return switch (productCategory) {
            case "RF", "STR", "SL" -> Discipline.STR;
            case "FP" -> Discipline.FP;
            case "ELEC" -> Discipline.ELEC;
            case "ACMV" -> Discipline.ACMV;
            case "SP", "PLB" -> Discipline.SP;
            case "CW" -> Discipline.CW;
            case "LPG" -> Discipline.LPG;
            case "REB" -> Discipline.REB;
            default -> Discipline.ARC;
        };
    }

    /**
     * @deprecated Use {@link #resolveDiscipline(String)} instead. Kept for extraction fallback only.
     */
    @Deprecated
    static String deriveDiscipline(String productCategory) {
        return resolveDiscipline(productCategory).name();
    }

    /**
     * @deprecated Use {@code resolveDiscipline(cat).getAD_Org_ID()} instead.
     */
    @Deprecated
    static int deriveAD_Org_ID(String discipline) {
        Discipline d = Discipline.fromString(discipline);
        return d != null ? d.getAD_Org_ID() : 0;
    }
}
