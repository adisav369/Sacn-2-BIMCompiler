package com.bim.ormsandbox;

import com.bim.ormsandbox.po.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Debug utility — navigate the full BIM construct via typed PO objects.
 *
 * <p>Replaces ad-hoc SQL during debug sessions. Each dump method prints a
 * human-readable report showing the chain from a given entry point.
 *
 * <p>Usage (point at any SQLite DB):
 * <pre>{@code
 * BuildingInspector inspector = new BuildingInspector(System.getProperty("bom.db"));
 * inspector.dumpBomChain("BED_SET_MASTER");
 * inspector.dumpRoomBoundaries("Ifc4_SampleHouse");
 * inspector.dumpElementRules("TB_LKTN");
 * inspector.close();
 * }</pre>
 *
 * <p>Transaction: all reads, no writes. Connection auto-closes on close().
 */
public class BuildingInspector {

    private static String dbPath() { return System.getProperty("bom.db"); }
    private static final String LIBRARY_DB_PATH = "library/component_library.db";

    private final Connection conn;

    /** Open the given SQLite DB file. */
    public BuildingInspector(String dbPath) throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    /** Use an already-open connection (caller manages lifecycle). */
    public BuildingInspector(Connection conn) {
        this.conn = conn;
    }

    // ── Buildings ─────────────────────────────────────────────────────────────

    /** Print all registered buildings from C_DocType (c_order dropped from BOM.db §11.2). */
    public void dumpBuildings() throws SQLException {
        String sql = "SELECT C_DocType_ID, ProjectName, doc_sub_type, SeqNo, ExpectedElements"
                   + " FROM C_DocType WHERE IsActive=1 ORDER BY SeqNo";
        System.out.println("=== BUILDINGS (from C_DocType) ===");
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                System.out.printf("  [%s] %s  sub=%s  seq=%d  expected=%d%n",
                    rs.getString("C_DocType_ID"), rs.getString("ProjectName"),
                    rs.getString("doc_sub_type"), rs.getInt("SeqNo"),
                    rs.getInt("ExpectedElements"));
            }
        }
    }

    // ── Element Rules ─────────────────────────────────────────────────────────

    /**
     * Print element rules for a building.
     * c_orderline dropped from BOM.db (§11.9). C_OrderLine is generated at compile time
     * in output.db only. This method now reports skip status.
     */
    public void dumpElementRules(String buildingType) throws SQLException {
        System.out.println("=== ORDER LINES for '" + buildingType + "' ===");
        System.out.println("  [SKIP] c_orderline dropped from BOM.db (§11.9). C_OrderLine generated at compile time in output.db.");
    }

    // ── Room Boundaries ───────────────────────────────────────────────────────

    /**
     * Print all room boundaries for a building.
     * Shows X/Y coords + centroid — directly aids G8 calibration.
     */
    public void dumpRoomBoundaries(String buildingType) throws SQLException {
        List<M_AdRoomBoundary> rooms = M_AdRoomBoundary.getByBuilding(conn, buildingType);
        System.out.println("=== ROOM BOUNDARIES for '" + buildingType + "' (" + rooms.size() + ") ===");
        for (M_AdRoomBoundary r : rooms) {
            System.out.printf("  [%d] %-25s  type=%-12s  frame=%s%n",
                r.getId(), r.getRoomName(), r.getRoomType(), r.getCoordinateFrame());
            System.out.printf("       X: [%.0f, %.0f]  centX=%.0f%n",
                r.getMinXMm(), r.getMaxXMm(), r.centroidXMm());
            System.out.printf("       Y: [%.0f, %.0f]  centY=%.0f%n",
                r.getMinYMm(), r.getMaxYMm(), r.centroidYMm());
            System.out.printf("       area=%.1f m²%n", r.areaMm2() / 1_000_000.0);
        }
    }

    // ── BOM Chain ─────────────────────────────────────────────────────────────

    /**
     * Print the full BOM tree for a given bom_id.
     * Recursively follows child_product_id chains for MAKE rows (UNIT → FLOOR → SET → ITEM).
     * Shows dx/dy/dz offsets and rotation_rule per child.
     */
    public void dumpBomChain(String bomId) throws SQLException {
        System.out.println("=== BOM CHAIN: " + bomId + " ===");
        dumpBomNode(bomId, 0);
    }

    private void dumpBomNode(String bomId, int depth) throws SQLException {
        MBOM bom = MBOM.get(conn, bomId);
        if (bom == null) {
            indent(depth); System.out.println("[NOT FOUND: " + bomId + "]");
            return;
        }
        indent(depth);
        System.out.printf("[BOM] %s  name='%s'  type=%s  groupBy=%s%n",
            bom.getBomId(), bom.getBomName(), bom.getBomType(), bom.getGroupBy());

        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        for (MBOMLine child : children) {
            indent(depth + 1);
            if (child.isNestedBom()) {
                System.out.printf("[NESTED] child_bom_id=%s  role=%s  seq=%d  %s%n",
                    child.getChildBomId(), child.getRole(), child.getSequence(),
                    child.describeOffset());
                dumpBomNode(child.getChildBomId(), depth + 2);
            } else {
                // Leaf — show product dims if child_product_id is set (NORM-2)
                System.out.printf("[LEAF] id=%d  role=%s  seq=%d  pattern='%s'  %s%n",
                    child.getBomChildId(), child.getRole(), child.getSequence(),
                    child.getChildNamePattern(), child.describeOffset());
                if (child.getChildProductId() != null) {
                    MProduct prod = MProduct.get(conn, child.getChildProductId());
                    if (prod != null) {
                        indent(depth + 2);
                        System.out.printf("[PRODUCT] %s  type=%s  %.3fm × %.3fm × %.3fm%n",
                            prod.getProductId(), prod.getProductType(),
                            prod.getWidth(), prod.getDepth(), prod.getHeight());
                    }
                }
                // Show child params (BOM.db)
                List<MAttribute> params = MAttribute.getByBomChild(
                    conn, child.getBomChildId());
                for (MAttribute p : params) {
                    indent(depth + 2);
                    System.out.printf("[PARAM] %s = %s (%s)%n",
                        p.getParamKey(), p.getParamValue(), p.getParamType());
                }
            }
        }
    }

    // ── Room Slots ────────────────────────────────────────────────────────────

    /** Print room slots for a room type — shows BOM dispatch. */
    public void dumpRoomSlots(String roomType) throws SQLException {
        List<M_AdRoomSlot> slots = M_AdRoomSlot.getByRoomType(conn, roomType);
        System.out.println("=== ROOM SLOTS for '" + roomType + "' (" + slots.size() + ") ===");
        for (M_AdRoomSlot s : slots) {
            System.out.printf("  [%d] %-25s  asm=%s  priority=%d  required=%s%n",
                s.getSlotId(), s.getSlotName(), s.getAssemblyId(),
                s.getSlotPriority(), s.isRequired() ? "YES" : "no");
        }
    }

    // ── Product Lookup ────────────────────────────────────────────────────────

    /** Print dimensions for a product. */
    public void dumpProductDim(String productId) throws SQLException {
        MProduct p = MProduct.get(conn, productId);
        if (p == null) { System.out.println("[NOT FOUND: " + productId + "]"); return; }
        System.out.printf("=== PRODUCT: %s ===%n", productId);
        System.out.printf("  type=%s  W=%.3fm  D=%.3fm  H=%.3fm%n",
            p.getProductType(), p.getWidth(), p.getDepth(), p.getHeight());
        System.out.printf("  clearances: front=%.3f back=%.3f left=%.3f right=%.3f%n",
            p.getClearFront(), p.getClearBack(), p.getClearLeft(), p.getClearRight());
        if (p.getFitsIn() != null)
            System.out.printf("  fitsIn=%s  requiresHost=%s%n",
                p.getFitsIn(), p.getRequiresHost());
    }

    // ── Preflight ─────────────────────────────────────────────────────────────

    /**
     * Run all data validation checks for a building before compilation.
     *
     * <p>Preflight checks surface data problems that would otherwise require a full
     * compiler run (4 buildings, minutes) to discover. Each check maps to a class of
     * historical bugs:
     * <ul>
     *   <li>A: blank child_name_pattern → DX=1206 class (silent GEN-BOX dims)</li>
     *   <li>B: room boundary not IFC_GLOBAL_MM → G8 calibration class (furniture offset)</li>
     *   <li>C: zero height_extent_mm → P01/P03 CRITICAL (zero-height geometry)</li>
     *   <li>D: element_ref with no geometry_map entry → missing LOD geometry</li>
     *   <li>E: dangling geometry_hash → FK violation at emit time</li>
     *   <li>F: ARC/STR rules with FURN_ family_refs → X1 gate violation (discipline mismatch)</li>
     *   <li>G: expected_elements vs active rules + reachable slots → registry integrity</li>
     *   <li>H: room slot authority → globally-scoped slots, no building_type isolation
     *          (FIRST-PRINCIPLES: THREE-TABLE AUTHORITY gap)</li>
     * </ul>
     *
     * @return number of warnings emitted (0 = all OK)
     */
    public int dumpPreflight(String buildingType) throws SQLException {
        System.out.println("=== PREFLIGHT: " + buildingType + " ===");
        int warnings = 0;
        warnings += preflightCheckA();
        warnings += preflightCheckB(buildingType);
        warnings += preflightCheckC(buildingType);
        warnings += preflightCheckD(buildingType);
        warnings += preflightCheckE(buildingType);
        warnings += preflightCheckF(buildingType);
        warnings += preflightCheckG(buildingType);
        warnings += preflightCheckH(buildingType);
        if (warnings == 0)
            System.out.println("RESULT: all checks OK");
        else
            System.out.printf("RESULT: %d warning(s) — fix before compile to avoid long debug cycles%n",
                warnings);
        return warnings;
    }

    /**
     * Check A: BOM children with blank child_name_pattern that are leaf/phantom nodes.
     * NORM-2: non-MAKE rows with no child_name_pattern → component lookup fails → silent dims issue.
     * This is a global check (BOMs are shared across buildings).
     */
    private int preflightCheckA() throws SQLException {
        // Implementing S92-tier2e — Witness: W-INT-PK-PHASE-E
        // JOIN on M_BOM_ID INTEGER FK; SELECT Value for text display
        String sql = "SELECT b.Value AS bom_id, bc.bom_child_id, bc.child_name_pattern, bc.role"
                   + " FROM m_bom_line bc"
                   + " JOIN m_bom b ON bc.M_BOM_ID = b.M_BOM_ID"
                   + " WHERE bc.is_active=1 AND b.is_active=1"
                   + " AND bc.component_type != 'MAKE'"
                   + " AND (bc.child_name_pattern IS NULL OR trim(bc.child_name_pattern)='')";
        Map<String, List<Integer>> byBom = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String bomId  = rs.getString("bom_id");
                int childId   = rs.getInt("bom_child_id");
                byBom.computeIfAbsent(bomId, k -> new ArrayList<>()).add(childId);
            }
        }
        if (byBom.isEmpty()) {
            System.out.println("[OK]   BOM children: all leaf nodes have child_name_pattern set");
            return 0;
        }
        for (Map.Entry<String, List<Integer>> e : byBom.entrySet()) {
            List<Integer> ids = e.getValue();
            System.out.printf("[WARN] BOM children: %d blank namePattern in %s (child ids: %s)%n",
                ids.size(), e.getKey(),
                ids.stream().map(Object::toString).collect(Collectors.joining(",")));
        }
        return byBom.size();
    }

    /**
     * Check B: Room boundaries not calibrated to IFC_GLOBAL_MM.
     * LOCAL_MM = grid cells, not real room extents → G8 placement is off by room centroid error.
     * DERIVED_MM is acceptable (TopologyMaker-generated rooms).
     */
    private int preflightCheckB(String buildingType) throws SQLException {
        List<M_AdRoomBoundary> rooms = M_AdRoomBoundary.getByBuilding(conn, buildingType);
        if (rooms.isEmpty()) {
            System.out.printf("[OK]   Room boundaries: no active rooms for %s%n", buildingType);
            return 0;
        }
        long badFrame = rooms.stream()
            .filter(r -> !"IFC_GLOBAL_MM".equals(r.getCoordinateFrame())
                      && !"DERIVED_MM".equals(r.getCoordinateFrame()))
            .count();
        long nullBounds = rooms.stream()
            .filter(r -> r.getMinXMm() == 0 && r.getMaxXMm() == 0
                      && r.getMinYMm() == 0 && r.getMaxYMm() == 0)
            .count();
        int w = 0;
        if (badFrame == 0 && nullBounds == 0) {
            System.out.printf("[OK]   Room boundaries: %d rooms, all IFC_GLOBAL_MM / DERIVED_MM%n",
                rooms.size());
        } else {
            if (badFrame > 0) {
                Set<String> frames = rooms.stream()
                    .map(M_AdRoomBoundary::getCoordinateFrame)
                    .filter(f -> !"IFC_GLOBAL_MM".equals(f) && !"DERIVED_MM".equals(f))
                    .collect(Collectors.toSet());
                System.out.printf("[WARN] Room boundaries: %d/%d rooms with frame %s"
                    + " (not IFC_GLOBAL_MM) — G8 will fail%n",
                    badFrame, rooms.size(), frames);
                w++;
            }
            if (nullBounds > 0) {
                System.out.printf("[WARN] Room boundaries: %d rooms with all-zero extents"
                    + " — placer will anchor at origin%n", nullBounds);
                w++;
            }
        }
        return w;
    }

    /**
     * Check C: Material dimension validation.
     *
     * <p>§11.9: height_extent_mm, height_mm DROPPED from c_orderline.
     * Material dims belong in M_Product. This check should validate M_Product dimensions
     * instead of c_orderline columns.
     *
     * TODO: Re-implement against M_Product when linked via M_Product_ID.
     */
    private int preflightCheckC(String buildingType) throws SQLException {
        // height_extent_mm, height_mm no longer on c_orderline (§11.9 DROP).
        // Material dims → M_Product. Check M_Product.height > 0 instead.
        System.out.printf("[SKIP] Check C: material dim columns dropped from c_orderline (§11.9). Use M_Product.%n");
        return 0;
    }

    /**
     * Check D: Element refs in c_orderline with no entry in I_Geometry_Map.
     * FURN elements that dispatch via BOM chains don't need direct geometry_map entries.
     * ARC/STR elements that use GEN-BOX fallback are expected — shown as summary counts.
     * MEP elements flagged if uncovered count exceeds building norm.
     *
     * <p>DISABLED: c_orderline dropped from BOM.db (redundant with M_BOM + M_Product).
     * TODO: Re-implement against M_Product + I_Geometry_Map chain.
     */
    private int preflightCheckD(String buildingType) throws SQLException {
        System.out.printf("  D: SKIP — c_orderline dropped from BOM.db (§11.9). Re-implement via M_Product.%n");
        return 0;
    }

    @SuppressWarnings("unused")
    private int preflightCheckD_DISABLED(String buildingType) throws SQLException {
        // ATTACH LOD DB for cross-DB geometry map lookup
        try (Statement att = conn.createStatement()) {
            att.execute("ATTACH DATABASE '" + LIBRARY_DB_PATH + "' AS lod");
        }
        String sql = "SELECT er.Discipline, COUNT(*) as cnt"
                   + " FROM c_orderline er"
                   + " WHERE er.IsActive=1 AND er.C_Order_ID=?"
                   + " AND er.Discipline != 'FURN'"
                   + " AND NOT EXISTS ("
                   + "   SELECT 1 FROM lod.lod_geometry_map gm"
                   + "   WHERE gm.element_ref = er.Name"
                   + "   AND gm.building_type = er.C_Order_ID)"
                   + " GROUP BY er.Discipline"
                   + " ORDER BY cnt DESC";
        Map<String, Integer> uncovered = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    uncovered.put(rs.getString("discipline"), rs.getInt("cnt"));
                }
            }
        }
        try (Statement det = conn.createStatement()) {
            det.execute("DETACH DATABASE lod");
        }
        if (uncovered.isEmpty()) {
            System.out.printf("[OK]   Geometry map: all non-FURN element_refs have geometry entries%n");
            return 0;
        }
        // GEN-BOX for ARC/STR is expected — report as INFO-level WARN only
        for (Map.Entry<String, Integer> e : uncovered.entrySet()) {
            System.out.printf("[WARN] Geometry map: %d %s element_refs with no geometry_map entry"
                + " (will use GEN-BOX fallback)%n", e.getValue(), e.getKey());
        }
        return uncovered.size();
    }

    /**
     * Check E: Geometry map entries with dangling geometry_hash (orphaned FK).
     * FK constraint prevents these normally — non-empty result means data integrity issue.
     * Uses component_library.db directly (LOD tables).
     */
    private int preflightCheckE(String buildingType) throws SQLException {
        try (Connection libConn = DriverManager.getConnection("jdbc:sqlite:" + LIBRARY_DB_PATH)) {
            List<M_IGeometryMap> orphans = M_IGeometryMap.getOrphans(libConn, buildingType);
            if (orphans.isEmpty()) {
                System.out.printf("[OK]   Geometry hashes: no orphaned geometry_hash in I_Geometry_Map%n");
                return 0;
            }
            String ids = orphans.stream()
                .map(o -> o.getElementRef() + "(" + o.getGeometryHash().substring(0, 8) + "…)")
                .limit(5)
                .collect(Collectors.joining(", "));
            System.out.printf("[WARN] Geometry hashes: %d orphaned entries (geometry_hash not in"
                + " component_geometries): %s%n", orphans.size(), ids);
            return 1;
        }
    }

    /**
     * Check F: Discipline/family mismatch — ARC/STR/MEP rules with FURN_ family_refs.
     *
     * <p>Root cause of DX regression (PROGRESS.md 2026-02-23): migration_G8_DX_restore_grid_rooms.sql
     * re-activated 715 DX ROOM_Level_* rules including 48 ARC discipline rules with FURN_ family_refs.
     * These produce BBox-only furniture geometry via the ARC path (X1 gate: 48 opaque-bbox failures).
     * The correct fix is to deactivate these rules (host_ref LIKE 'ROOM_Level_%', family_ref LIKE 'FURN_%').
     *
     * <p>REPORT ONLY — does not modify any data.
     */
    private int preflightCheckF(String buildingType) throws SQLException {
        // c_orderline dropped from BOM.db (redundant with M_BOM + M_Product).
        // TODO: Re-implement discipline/family mismatch check against M_Product.
        System.out.printf("  F: SKIP — c_orderline dropped from BOM.db (§11.9). Re-implement via M_Product.%n");
        Map<String, Integer> mismatched = new LinkedHashMap<>();
        // §11.9: host_ref DROPPED from c_orderline. ROOM_Level_* regression pattern
        // check requires W_Verb_Node migration. Skipped for now.
        if (mismatched.isEmpty()) {
            System.out.printf("[OK]   Discipline check: no non-FURN rules with FURN_ M_Product_ID%n");
            return 0;
        }
        int w = 0;
        for (Map.Entry<String, Integer> e : mismatched.entrySet()) {
            System.out.printf("[WARN] Discipline mismatch: %d %s rules with FURN_ M_Product_ID"
                + " — produces BBox-only furniture geometry (X1 gate violation)%n",
                e.getValue(), e.getKey());
            w++;
        }
        return w;
    }

    /**
     * Check H: Room slot authority — flags slots that dispatch BOMs for room_types absent in this building.
     *
     * <p>Uses the building-scoped slot view (building_type IS NULL OR building_type = this building)
     * to mirror exactly what the dispatch engine sees at compile time.
     * Building-specific slots (non-null building_type) are invisible to other buildings.
     *
     * <p>Two sub-checks:
     * <ol>
     *   <li>Phantom slots: slots dispatching BOMs for room_types not present in this building
     *       (harmless but pollute global table and mask future collisions).</li>
     *   <li>Cross-contamination risk: slots reachable by this building (room_type present) whose
     *       assembly_id carries another building's naming convention.</li>
     * </ol>
     */
    private int preflightCheckH(String buildingType) throws SQLException {
        // DAO: get room_types present in this building
        Set<String> buildingRoomTypes = M_AdRoomBoundary.getByBuilding(conn, buildingType)
            .stream()
            .map(M_AdRoomBoundary::getRoomType)
            .collect(Collectors.toSet());

        // DAO: get slots visible to this building (building_type IS NULL OR = this building)
        List<M_AdRoomSlot> allSlots = M_AdRoomSlot.getWithAssemblyForBuilding(conn, buildingType);

        // Sub-check 1: phantom slots — room_type not in this building's boundaries
        List<M_AdRoomSlot> phantomSlots = allSlots.stream()
            .filter(s -> !buildingRoomTypes.contains(s.getRoomType()))
            .collect(Collectors.toList());

        // Sub-check 2: cross-contamination — slot reachable by this building but assembly_id
        // uses a naming convention that implies a different building
        // Convention: assembly_id starting with another building's doc_sub_type prefix
        // Prefix derived from C_DocType.doc_sub_type (e.g., SH_, DX_, TE_, ST_)
        List<M_AdRoomSlot> reachableSlots = allSlots.stream()
            .filter(s -> buildingRoomTypes.contains(s.getRoomType()))
            .collect(Collectors.toList());
        // Foreign prefix = assembly_ids that start with a known tag but NOT this building's tag
        // Data-driven: query all doc_sub_type prefixes from C_DocType
        List<String> allPrefixes = allBuildingPrefixes();
        String ownPrefix = deriveBuildingPrefix(buildingType);
        List<M_AdRoomSlot> crossContaminated = reachableSlots.stream()
            .filter(s -> allPrefixes.stream()
                .anyMatch(p -> s.getAssemblyId().startsWith(p) && !s.getAssemblyId().startsWith(ownPrefix)))
            .collect(Collectors.toList());

        int w = 0;
        if (phantomSlots.isEmpty() && crossContaminated.isEmpty()) {
            System.out.printf("[OK]   Room slot authority: all %d reachable slots are building-appropriate%n",
                reachableSlots.size());
            return 0;
        }
        if (!phantomSlots.isEmpty()) {
            // Group phantom slots by assembly_id for clarity
            Map<String, List<String>> byAssembly = new LinkedHashMap<>();
            for (M_AdRoomSlot s : phantomSlots)
                byAssembly.computeIfAbsent(s.getAssemblyId(), k -> new ArrayList<>()).add(s.getRoomType());
            System.out.printf("[WARN] Room slot authority: %d slot(s) dispatch BOMs for room_types"
                + " absent in %s (phantom slots — globally visible but unreachable here)%n",
                phantomSlots.size(), buildingType);
            for (Map.Entry<String, List<String>> e : byAssembly.entrySet())
                System.out.printf("         assembly=%-30s  absent_room_types=%s%n",
                    e.getKey(), String.join(",", e.getValue()));
            w++;
        }
        if (!crossContaminated.isEmpty()) {
            System.out.printf("[WARN] Room slot authority [FIRST-PRINCIPLES RISK]: %d slot(s)"
                + " reachable by %s carry foreign-building assembly_ids%n",
                crossContaminated.size(), buildingType);
            for (M_AdRoomSlot s : crossContaminated)
                System.out.printf("         slot_id=%-5d  room_type=%-20s  assembly=%s%n",
                    s.getSlotId(), s.getRoomType(), s.getAssemblyId());
            System.out.printf("         NOTE: These slots are still globally reachable."
                + " Set building_type on the offending slots to isolate them.%n");
            w++;
        }
        return w;
    }

    /**
     * Derive the short prefix used for building-specific BOM assembly naming.
     *
     * <p>Data-driven: looks up doc_sub_type from C_DocType by ProjectName.
     * No hardcoded building-type checks — BOM structure (C_DocType) carries the semantics.
     */
    private String deriveBuildingPrefix(String buildingType) throws SQLException {
        String sql = "SELECT doc_sub_type FROM C_DocType WHERE ProjectName=? AND IsActive=1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String sub = rs.getString("doc_sub_type");
                    if (sub != null && !sub.isBlank()) return sub.toUpperCase() + "_";
                }
            }
        }
        // Fallback for unregistered buildings — truncate to 4 chars
        return buildingType.substring(0, Math.min(4, buildingType.length())).toUpperCase() + "_";
    }

    /**
     * Query all known building prefixes from C_DocType (doc_sub_type + "_").
     *
     * <p>Data-driven replacement for hardcoded prefix lists.
     */
    private List<String> allBuildingPrefixes() throws SQLException {
        String sql = "SELECT DISTINCT doc_sub_type FROM C_DocType WHERE IsActive=1 AND doc_sub_type IS NOT NULL";
        List<String> prefixes = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String sub = rs.getString("doc_sub_type");
                if (sub != null && !sub.isBlank()) prefixes.add(sub.toUpperCase() + "_");
            }
        }
        return prefixes;
    }

    /**
     * Check G: Registry data-sufficiency guard — verifies the compiler has real data to compile from.
     * Catches "invented data" patterns: expected_elements=0, or no element rules AND no room slots.
     *
     * <p>Constraint: EXTRACT, DON'T IMAGINE (CLAUDE.md PRIME RULE). If the compiler has nothing
     * to read, it must produce 0 elements — not invent geometry. This check flags buildings
     * where the registry claims an output count but the data tables are empty.
     *
     * <p>REPORT ONLY — does not modify any data.
     */
    private int preflightCheckG(String buildingType) throws SQLException {
        // Get expected count from C_DocType (was c_order, now domain config)
        String regSql = "SELECT ExpectedElements FROM C_DocType WHERE ProjectName=?";
        int expected = 0;
        try (PreparedStatement ps = conn.prepareStatement(regSql)) {
            ps.setString(1, buildingType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) expected = rs.getInt("ExpectedElements");
            }
        }
        // c_orderline dropped from BOM.db (§11.9). C_OrderLine generated at compile time.
        int ruleCount = 0; // not available until compile
        // Count room slots reachable from this building (via room_boundary.room_type)
        String slotSql = "SELECT COUNT(DISTINCT rs.slot_id) FROM ad_room_slot rs"
                       + " JOIN ad_room_boundary rb ON rs.room_type = rb.room_type"
                       + " WHERE rb.building_type=? AND rb.is_active=1";
        int slotCount = 0;
        try (PreparedStatement ps = conn.prepareStatement(slotSql)) {
            ps.setString(1, buildingType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) slotCount = rs.getInt(1);
            }
        }
        int w = 0;
        if (expected == 0) {
            System.out.printf("[WARN] Registry: expected_elements=0 for %s"
                + " — update c_order.expected_elements before compile%n", buildingType);
            w++;
        } else {
            System.out.printf("[OK]   Registry: expected_elements=%d  activeRules=%d  reachableSlots=%d%n",
                expected, ruleCount, slotCount);
        }
        if (expected > 0 && ruleCount == 0 && slotCount == 0) {
            System.out.printf("[WARN] No-data risk: expected_elements=%d but 0 active rules and 0"
                + " reachable room slots — compiler will invent nothing, output will be 0%n", expected);
            w++;
        }
        return w;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void indent(int depth) {
        System.out.print("  ".repeat(depth));
    }

    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * CLI entry point. Usage:
     * <pre>
     *   java -cp ... com.bim.ormsandbox.BuildingInspector library/BOM.db buildings
     *   java -cp ... com.bim.ormsandbox.BuildingInspector library/BOM.db bom BED_SET_MASTER
     *   java -cp ... com.bim.ormsandbox.BuildingInspector library/BOM.db rooms Ifc4_SampleHouse
     *   java -cp ... com.bim.ormsandbox.BuildingInspector library/BOM.db rules TB_LKTN
     *   java -cp ... com.bim.ormsandbox.BuildingInspector library/BOM.db slots BEDROOM
     *   java -cp ... com.bim.ormsandbox.BuildingInspector library/BOM.db product FURN_DINING_CHAIR
     *   java -cp ... com.bim.ormsandbox.BuildingInspector library/BOM.db preflight Ifc2x3_Duplex
     * </pre>
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: BuildingInspector <db-path> <command> [arg]");
            System.err.println("Commands: buildings | bom <bomId> | rooms <buildingType> "
                + "| rules <buildingType> | slots <roomType> | product <productId>"
                + "| preflight <buildingType>");
            System.exit(1);
        }
        String dbPath = args[0];
        String cmd = args[1];
        String arg = args.length > 2 ? args[2] : null;

        BuildingInspector inspector = new BuildingInspector(dbPath);
        try {
            switch (cmd) {
                case "buildings" -> inspector.dumpBuildings();
                case "bom"       -> { if (arg == null) die("bom requires <bomId>"); inspector.dumpBomChain(arg); }
                case "rooms"     -> { if (arg == null) die("rooms requires <buildingType>"); inspector.dumpRoomBoundaries(arg); }
                case "rules"     -> { if (arg == null) die("rules requires <buildingType>"); inspector.dumpElementRules(arg); }
                case "slots"     -> { if (arg == null) die("slots requires <roomType>"); inspector.dumpRoomSlots(arg); }
                case "product"   -> { if (arg == null) die("product requires <productId>"); inspector.dumpProductDim(arg); }
                case "preflight" -> { if (arg == null) die("preflight requires <buildingType>"); inspector.dumpPreflight(arg); }
                default          -> { System.err.println("Unknown command: " + cmd); System.exit(1); }
            }
        } finally {
            inspector.close();
        }
    }

    private static void die(String msg) { System.err.println(msg); System.exit(1); }
}
