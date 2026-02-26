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
 * BuildingInspector inspector = new BuildingInspector("library/BOM.db");
 * inspector.dumpBomChain("BED_SET_MASTER");
 * inspector.dumpRoomBoundaries("Ifc4_SampleHouse");
 * inspector.dumpElementRules("TB_LKTN");
 * inspector.close();
 * }</pre>
 *
 * <p>Transaction: all reads, no writes. Connection auto-closes on close().
 */
public class BuildingInspector {

    private static final String DB_PATH = "library/BOM.db";
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

    /** Print all registered buildings. */
    public void dumpBuildings() throws SQLException {
        List<M_AdBuildingRegistry> buildings = M_AdBuildingRegistry.getAll(conn);
        System.out.println("=== BUILDINGS (" + buildings.size() + ") ===");
        for (M_AdBuildingRegistry b : buildings) {
            System.out.printf("  [%s] %s  type=%s  seq=%d  status=%s  expected=%d%n",
                b.getBuildingId(), b.getBuildingName(), b.getBuildingType(),
                b.getSeqNo(), b.getDocStatus(), b.getExpectedElements());
        }
    }

    // ── Element Rules ─────────────────────────────────────────────────────────

    /**
     * Print all element rules for a building.
     * Directly aids G8 debug — shows host_ref, position_rule, family_ref, height_extent_mm.
     */
    public void dumpElementRules(String buildingType) throws SQLException {
        List<M_AdElementRule> rules = M_AdElementRule.getByBuilding(conn, buildingType);
        System.out.println("=== ELEMENT RULES for '" + buildingType + "' (" + rules.size() + ") ===");
        for (M_AdElementRule r : rules) {
            System.out.printf("  [%d] %s  ifc=%s  disc=%s  host=%s/%s%n",
                r.getId(), r.getElementRef(), r.getIfcClass(),
                r.getDiscipline(), r.getHostType(), r.getHostRef());
            System.out.printf("       posRule=%s  posVal=%.1f  heightMm=%.0f  extentMm=%.0f%n",
                r.getPositionRule(), r.getPositionValue(), r.getHeightMm(), r.getHeightExtentMm());
            if (r.getFamilyRef() != null)
                System.out.printf("       familyRef=%s  orient=%s%n",
                    r.getFamilyRef(), r.getOrientation());
        }
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
     * Recursively follows child_bom_id chains (UNIT → FLOOR → SET → ITEM).
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
                // Leaf — show product dims if product_ref is set
                System.out.printf("[LEAF] id=%d  role=%s  seq=%d  pattern='%s'  %s%n",
                    child.getBomChildId(), child.getRole(), child.getSequence(),
                    child.getChildNamePattern(), child.describeOffset());
                if (child.getProductRef() != null) {
                    M_AdProductDim prod = M_AdProductDim.get(conn, child.getProductRef());
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
        M_AdProductDim p = M_AdProductDim.get(conn, productId);
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
     * Check A: BOM children with blank child_name_pattern that are leaf nodes.
     * These have child_bom_id IS NULL but no name pattern → component lookup fails → silent dims issue.
     * This is a global check (BOMs are shared across buildings).
     */
    private int preflightCheckA() throws SQLException {
        String sql = "SELECT bc.bom_id, bc.bom_child_id, bc.child_name_pattern, bc.role"
                   + " FROM m_bom_line bc"
                   + " JOIN m_bom b ON bc.bom_id = b.bom_id"
                   + " WHERE bc.is_active=1 AND b.is_active=1"
                   + " AND bc.child_bom_id IS NULL"
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
     * Check C: Element rules with zero or null height_extent_mm.
     * These produce zero-height geometry → P01/P03 CRITICAL violations at compile time.
     */
    private int preflightCheckC(String buildingType) throws SQLException {
        List<M_AdElementRule> rules = M_AdElementRule.getByBuilding(conn, buildingType);
        List<String> zeroHeight = rules.stream()
            .filter(r -> r.getHeightExtentMm() == 0.0)
            .map(r -> r.getElementRef() + "(id=" + r.getId() + ")")
            .collect(Collectors.toList());
        List<String> negHeight = rules.stream()
            .filter(r -> r.getHeightMm() < 0)
            .map(r -> r.getElementRef() + "(id=" + r.getId() + ")")
            .collect(Collectors.toList());
        int w = 0;
        if (zeroHeight.isEmpty() && negHeight.isEmpty()) {
            System.out.printf("[OK]   Element rules: %d rules, all height_extent_mm > 0%n",
                rules.size());
        } else {
            if (!zeroHeight.isEmpty()) {
                String sample = zeroHeight.stream().limit(5).collect(Collectors.joining(", "));
                String suffix = zeroHeight.size() > 5 ? " and " + (zeroHeight.size()-5) + " more" : "";
                System.out.printf("[WARN] Element rules: %d with zero height_extent_mm: %s%s%n",
                    zeroHeight.size(), sample, suffix);
                w++;
            }
            if (!negHeight.isEmpty()) {
                System.out.printf("[WARN] Element rules: %d with negative height_mm%n",
                    negHeight.size());
                w++;
            }
        }
        return w;
    }

    /**
     * Check D: Element refs in ad_element_rule with no entry in lod_geometry_map.
     * FURN elements that dispatch via BOM chains don't need direct geometry_map entries.
     * ARC/STR elements that use GEN-BOX fallback are expected — shown as summary counts.
     * MEP elements flagged if uncovered count exceeds building norm.
     *
     * <p>Cross-DB: ad_element_rule in BOM.db, lod_geometry_map in component_library.db.
     * Uses ATTACH to join across databases.
     */
    private int preflightCheckD(String buildingType) throws SQLException {
        // ATTACH LOD DB for cross-DB geometry map lookup
        try (Statement att = conn.createStatement()) {
            att.execute("ATTACH DATABASE '" + LIBRARY_DB_PATH + "' AS lod");
        }
        String sql = "SELECT er.discipline, COUNT(*) as cnt"
                   + " FROM ad_element_rule er"
                   + " WHERE er.is_active=1 AND er.building_type=?"
                   + " AND er.discipline != 'FURN'"
                   + " AND NOT EXISTS ("
                   + "   SELECT 1 FROM lod.lod_geometry_map gm"
                   + "   WHERE gm.element_ref = er.element_ref"
                   + "   AND gm.building_type = er.building_type)"
                   + " GROUP BY er.discipline"
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
            List<M_AdGeometryMap> orphans = M_AdGeometryMap.getOrphans(libConn, buildingType);
            if (orphans.isEmpty()) {
                System.out.printf("[OK]   Geometry hashes: no orphaned geometry_hash in lod_geometry_map%n");
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
        String sql = "SELECT COUNT(*) as cnt, discipline"
                   + " FROM ad_element_rule"
                   + " WHERE building_type=? AND is_active=1"
                   + " AND discipline NOT IN ('FURN')"
                   + " AND family_ref LIKE 'FURN_%'"
                   + " GROUP BY discipline"
                   + " ORDER BY cnt DESC";
        Map<String, Integer> mismatched = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mismatched.put(rs.getString("discipline"), rs.getInt("cnt"));
                }
            }
        }
        // Also count specifically ROOM_Level_* host_ref (the DX restore regression pattern)
        int gridRoomFurnCount = 0;
        String gridSql = "SELECT COUNT(*) FROM ad_element_rule"
                       + " WHERE building_type=? AND is_active=1"
                       + " AND discipline='ARC' AND family_ref LIKE 'FURN_%'"
                       + " AND host_ref LIKE 'ROOM_Level_%'";
        try (PreparedStatement ps = conn.prepareStatement(gridSql)) {
            ps.setString(1, buildingType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) gridRoomFurnCount = rs.getInt(1);
            }
        }
        if (mismatched.isEmpty()) {
            System.out.printf("[OK]   Discipline check: no non-FURN rules with FURN_ family_refs%n");
            return 0;
        }
        int w = 0;
        for (Map.Entry<String, Integer> e : mismatched.entrySet()) {
            System.out.printf("[WARN] Discipline mismatch: %d %s rules with FURN_ family_ref"
                + " — produces BBox-only furniture geometry (X1 gate violation)%n",
                e.getValue(), e.getKey());
            w++;
        }
        if (gridRoomFurnCount > 0) {
            System.out.printf("[WARN] Regression pattern: %d ROOM_Level_* ARC rules with FURN_ family_ref"
                + " are ACTIVE — retired by migration_G8_DX_retire_stale_room_rules.sql but"
                + " re-activated by migration_G8_DX_restore_grid_rooms.sql (root cause: X1 gate)."
                + " FIX: UPDATE ad_element_rule SET is_active=0"
                + " WHERE building_type='%s' AND discipline='ARC'"
                + " AND family_ref LIKE 'FURN_%%%%' AND host_ref LIKE 'ROOM_Level_%%%%'%n",
                gridRoomFurnCount, buildingType);
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
        // Convention: assembly_id starting with another known building prefix
        // Extract prefix from buildingType (e.g., "Ifc4_SampleHouse" → "SH",
        //                                          "Ifc2x3_Duplex" → "DX")
        List<M_AdRoomSlot> reachableSlots = allSlots.stream()
            .filter(s -> buildingRoomTypes.contains(s.getRoomType()))
            .collect(Collectors.toList());
        // Foreign prefix = assembly_ids that start with a known tag but NOT this building's tag
        // Check simple prefix mismatch: SH_ prefix in DX, DX_ prefix in SH, TB_ prefix in either
        List<String> foreignPrefixes = List.of("SH_", "DX_", "TB_", "TERM_");
        String ownPrefix = deriveBuildingPrefix(buildingType);
        List<M_AdRoomSlot> crossContaminated = reachableSlots.stream()
            .filter(s -> foreignPrefixes.stream()
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

    /** Derive the short prefix used for building-specific BOM assembly naming. */
    private static String deriveBuildingPrefix(String buildingType) {
        if (buildingType.contains("SampleHouse")) return "SH_";
        if (buildingType.contains("Duplex"))      return "DX_";
        if (buildingType.contains("TB"))          return "TB_";
        if (buildingType.contains("Terminal"))    return "TERM_";
        return buildingType.substring(0, Math.min(4, buildingType.length())).toUpperCase() + "_";
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
        // Get expected count from registry
        String regSql = "SELECT expected_elements FROM ad_building_registry WHERE building_id=?";
        int expected = 0;
        try (PreparedStatement ps = conn.prepareStatement(regSql)) {
            ps.setString(1, buildingType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) expected = rs.getInt("expected_elements");
            }
        }
        // Count active element rules
        int ruleCount = M_AdElementRule.getByBuilding(conn, buildingType).size();
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
                + " — update ad_building_registry.expected_elements before compile%n", buildingType);
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
