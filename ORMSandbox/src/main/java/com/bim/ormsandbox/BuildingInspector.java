package com.bim.ormsandbox;

import com.bim.ormsandbox.po.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/**
 * Debug utility — navigate the full BIM construct via typed PO objects.
 *
 * <p>Replaces ad-hoc SQL during debug sessions. Each dump method prints a
 * human-readable report showing the chain from a given entry point.
 *
 * <p>Usage (point at any SQLite DB):
 * <pre>{@code
 * BuildingInspector inspector = new BuildingInspector("library/component_library.db");
 * inspector.dumpBomChain("BED_SET_MASTER");
 * inspector.dumpRoomBoundaries("Ifc4_SampleHouse");
 * inspector.dumpElementRules("TB_LKTN");
 * inspector.close();
 * }</pre>
 *
 * <p>Transaction: all reads, no writes. Connection auto-closes on close().
 */
public class BuildingInspector {

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
        M_AdBom bom = M_AdBom.get(conn, bomId);
        if (bom == null) {
            indent(depth); System.out.println("[NOT FOUND: " + bomId + "]");
            return;
        }
        indent(depth);
        System.out.printf("[BOM] %s  name='%s'  type=%s  groupBy=%s%n",
            bom.getBomId(), bom.getBomName(), bom.getBomType(), bom.getGroupBy());

        List<M_AdBomChild> children = M_AdBomChild.getByBom(conn, bomId);
        for (M_AdBomChild child : children) {
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
                // Show child params
                List<M_AdBomChildParam> params = M_AdBomChildParam.getByBomChild(
                    conn, child.getBomChildId());
                for (M_AdBomChildParam p : params) {
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
     *   java -cp ... com.bim.ormsandbox.BuildingInspector library/component_library.db buildings
     *   java -cp ... com.bim.ormsandbox.BuildingInspector library/component_library.db bom BED_SET_MASTER
     *   java -cp ... com.bim.ormsandbox.BuildingInspector library/component_library.db rooms Ifc4_SampleHouse
     *   java -cp ... com.bim.ormsandbox.BuildingInspector library/component_library.db rules TB_LKTN
     *   java -cp ... com.bim.ormsandbox.BuildingInspector library/component_library.db slots BEDROOM
     *   java -cp ... com.bim.ormsandbox.BuildingInspector library/component_library.db product FURN_DINING_CHAIR
     * </pre>
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: BuildingInspector <db-path> <command> [arg]");
            System.err.println("Commands: buildings | bom <bomId> | rooms <buildingType> " +
                               "| rules <buildingType> | slots <roomType> | product <productId>");
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
                default          -> { System.err.println("Unknown command: " + cmd); System.exit(1); }
            }
        } finally {
            inspector.close();
        }
    }

    private static void die(String msg) { System.err.println(msg); System.exit(1); }
}
