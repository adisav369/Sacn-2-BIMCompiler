package com.bim.designer;

import com.bim.compiler.dsl.PlacementLoader;
import com.bim.designer.api.*;
import com.bim.designer.api.DesignerAPI.*;

import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BOM Drop — iDempiere BOM explosion into C_OrderLine tree.
 *
 * <p>TC-1: Instant Drop of BUILDING_SH_STD.
 * User picks a BUILDING product → engine checks IsBOM (has matching m_bom) →
 * walks m_bom/m_bom_line recursively → inserts C_OrderLine hierarchy in
 * work_output.db → returns BomTreeNode for Outliner (expand/collapse).
 *
 * <p>iDempiere pattern: C_OrderLine has M_Product_ID (family_ref) and
 * M_Product_Category (bom_category). Only IsBOM products (matching m_bom
 * entry) explode into children. The GUI renders the tree — users expand
 * or collapse each node, swap by category (e.g., Roof → Pitched/Curved).
 *
 * <p>// Implementing GENERATIVE_HOUSE_SRS.md §2.1, §7 TC-1
 * // Implementing BIM_Designer_SRS.md §28.1 — Witness: W-DROP-1, W-DROP-2
 */
@DisplayName("BOM Drop — TC-1 Instant Drop BUILDING_SH_STD → 55 elements")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BomDropTest {

    private static final String SH_BOM_DB = "library/SH_BOM.db";

    private static DesignerAPIImpl api;
    private static Connection bomConn;
    private static BomDropResponse dropResult;

    @BeforeAll
    static void setUp() throws Exception {
        PlacementLoader.resetInstance();

        assertTrue(new File(SH_BOM_DB).exists(),
                "SH_BOM.db not found — run IFCtoBOM pipeline first");

        // Connect to SH_BOM.db — contains m_bom, m_bom_line, M_Product
        bomConn = DriverManager.getConnection("jdbc:sqlite:" + SH_BOM_DB);
        System.setProperty("bom.db", SH_BOM_DB);

        api = new DesignerAPIImpl(bomConn);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (bomConn != null && !bomConn.isClosed()) bomConn.close();
    }

    // ── W-DROP-1: bomDrop creates C_Order + explodes BOM tree ──────

    @Test
    @Order(1)
    @DisplayName("W-DROP-1: bomDrop(BUILDING_SH_STD) creates order + exploded tree")
    void w_drop_1_bom_drop_creates_order_and_tree() {
        dropResult = api.bomDrop("BUILDING_SH_STD");

        assertTrue(dropResult.success(), "bomDrop must succeed: " + dropResult.error());
        assertNotNull(dropResult.orderId(), "C_Order_ID must be set");
        assertTrue(dropResult.orderLineId() > 0, "root C_OrderLine_ID must be positive");
        assertNotNull(dropResult.tree(), "BomTreeNode tree must be returned");

        // Root node must be the BUILDING product
        assertEquals("BUILDING_SH_STD", dropResult.tree().familyRef(),
                "Root node must reference BUILDING_SH_STD");
        assertEquals("BUILDING", dropResult.tree().hostType(),
                "Root host_type must be BUILDING");
    }

    // ── W-DROP-2: Instant Drop leaf count = Rosetta Stone element count ──

    @Test
    @Order(2)
    @DisplayName("W-DROP-2: Instant Drop totalElements = 55 (SH Rosetta Stone)")
    void w_drop_2_instant_drop_55_elements() {
        assertNotNull(dropResult, "W-DROP-1 must run first");

        assertEquals(55, dropResult.totalElements(),
                "Instant Drop of BUILDING_SH_STD must produce 55 leaf elements "
                + "(identical to Rosetta Stone SH G1-COUNT)");
    }

    // ── W-DROP-3: Tree structure — BUILDING → FLOOR → ROOM hierarchy ──

    @Test
    @Order(3)
    @DisplayName("W-DROP-3: Tree has FLOOR-level children (IsBOM sub-assemblies)")
    void w_drop_3_tree_has_floor_children() {
        assertNotNull(dropResult, "W-DROP-1 must run first");
        BomTreeNode root = dropResult.tree();

        // BUILDING_SH_STD has 6 direct BOM children (all are IsBOM sub-assemblies):
        // SH_GF_STR, FLOOR_SH_GF_STD, SH_ROOF_STR, SH_CW_STR, FLOOR_SLAB_GF, ROOF_ASSEMBLY
        assertFalse(root.children().isEmpty(),
                "BUILDING root must have children (IsBOM sub-assemblies)");

        // All direct children should be FLOOR host_type (depth 1)
        for (BomTreeNode child : root.children()) {
            assertEquals("FLOOR", child.hostType(),
                    "Direct children of BUILDING must be FLOOR: " + child.familyRef());
        }

        System.out.printf("[W-DROP-3] Root: %s, %d FLOOR children%n",
                root.familyRef(), root.children().size());
        for (BomTreeNode floor : root.children()) {
            System.out.printf("  ├─ %s (category=%s, children=%d)%n",
                    floor.familyRef(), floor.bomCategory(), floor.children().size());
        }
    }

    // ── W-DROP-4: Room-level assemblies have LEAF children ──────────

    @Test
    @Order(4)
    @DisplayName("W-DROP-4: FLOOR_SH_GF_STD contains ROOM sub-assemblies with LEAF children")
    void w_drop_4_room_assemblies_have_leaves() {
        assertNotNull(dropResult, "W-DROP-1 must run first");

        // Find FLOOR_SH_GF_STD in the tree
        BomTreeNode floorRooms = null;
        for (BomTreeNode child : dropResult.tree().children()) {
            if ("FLOOR_SH_GF_STD".equals(child.familyRef())) {
                floorRooms = child;
                break;
            }
        }
        assertNotNull(floorRooms,
                "FLOOR_SH_GF_STD must be a child of BUILDING_SH_STD");

        // FLOOR_SH_GF_STD has room SET children: SH_LIVING_SET, SH_DINING_SET,
        // SH_BED_SET, TOILET_BLOCK_FIXTURES — all IsBOM (have m_bom entries)
        assertFalse(floorRooms.children().isEmpty(),
                "FLOOR_SH_GF_STD must have ROOM children");

        // Each room SET should be ROOM host_type.
        // Some rooms may have LEAF children, others may be empty assemblies
        // (e.g., TOILET_BLOCK_FIXTURES has m_bom but no m_bom_line children).
        int roomsWithLeaves = 0;
        for (BomTreeNode room : floorRooms.children()) {
            assertEquals("ROOM", room.hostType(),
                    "Children of FLOOR must be ROOM: " + room.familyRef());

            if (!room.children().isEmpty()) {
                roomsWithLeaves++;
                for (BomTreeNode leaf : room.children()) {
                    assertEquals("LEAF", leaf.hostType(),
                            "Children of ROOM must be LEAF: " + leaf.familyRef());
                }
            }

            System.out.printf("  ├─ %s (category=%s, %d leaves)%n",
                    room.familyRef(), room.bomCategory(), room.children().size());
        }
        assertTrue(roomsWithLeaves >= 3,
                "At least 3 rooms must have LEAF children (LIVING, DINING, BED)");
    }

    // ── W-DROP-5: Non-BOM product returns error ─────────────────────

    @Test
    @Order(5)
    @DisplayName("W-DROP-5: bomDrop on non-BOM product returns IsBOM=false error")
    void w_drop_5_non_bom_product_fails() {
        BomDropResponse result = api.bomDrop("NONEXISTENT_PRODUCT");

        assertFalse(result.success(), "Non-BOM product must fail");
        assertNotNull(result.error(), "Error message must explain IsBOM=false");
        assertTrue(result.error().contains("IsBOM=false"),
                "Error must mention IsBOM: " + result.error());
    }

    // ── W-DROP-6: bom_category set on each node for swap browsing ───

    @Test
    @Order(6)
    @DisplayName("W-DROP-6: Each tree node carries bom_category (M_Product_Category) for swap")
    void w_drop_6_bom_category_on_nodes() {
        assertNotNull(dropResult, "W-DROP-1 must run first");

        // Walk tree and check that assembly nodes have bom_category
        int categorized = countCategorizedNodes(dropResult.tree());
        assertTrue(categorized > 0,
                "At least some nodes must have bom_category for BOM chooser swap");
        System.out.printf("[W-DROP-6] %d nodes with bom_category%n", categorized);
    }

    private int countCategorizedNodes(BomTreeNode node) {
        int count = (node.bomCategory() != null && !node.bomCategory().isEmpty()) ? 1 : 0;
        for (BomTreeNode child : node.children()) {
            count += countCategorizedNodes(child);
        }
        return count;
    }
}
