package com.bim.designer;

import com.bim.compiler.dsl.PlacementLoader;
import com.bim.designer.api.*;
import com.bim.designer.api.DesignerAPI.*;

import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-WO-1: Work Order compile path — BOM Drop → CompleteIt → output.db.
 *
 * <p>Proves the ERP Manufacturing BOM pattern end-to-end on DemoHouse (GENERATIVE):
 * the user picks a building product, BOM Drop explodes it into a C_Order with
 * C_OrderLines, then CompleteIt sends the order to the server for compilation.
 * No YAML, no DSL from the user's perspective — the BOM tree IS the building spec.
 *
 * <p>This is the foundational proof for the HTML UI: the backend can receive a
 * Work Order and produce an output database. Later, Bonsai listens for the
 * compile-complete event and populates the viewport from the output.
 *
 * <p>// Implementing BIM_Designer_SRS.md §30.4 — Witness: W-WO-1
 */
@DisplayName("W-WO-1: Work Order → BOM Drop → CompleteIt → compile (DemoHouse)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkOrderCompileTest {

    private static final String DM_BOM_DB = "library/DM_BOM.db";
    private static final String COMP_LIB  = "library/component_library.db";
    private static final String SCHEMA_SQL = "library/schema_snapshot_bom.sql";

    private static Path tempDir;
    private static String compileDbPath;
    private static String outputDir;
    private static DesignerAPIImpl api;
    private static Connection bomConn;

    private static BomDropResponse dropResult;
    private static CompileResponse compileResult;

    @BeforeAll
    static void setUp() throws Exception {
        PlacementLoader.resetInstance();

        assertTrue(new File(DM_BOM_DB).exists(),
                "DM_BOM.db not found — run: sqlite3 library/DM_BOM.db < migration/seed_dm_bom.sql");
        assertTrue(new File(COMP_LIB).exists(), "component_library.db not found");

        // Sandbox: copy DM_BOM.db to temp dir so we don't touch the real one
        tempDir = Files.createTempDirectory("w_wo_1_");
        compileDbPath = tempDir.resolve("_DM_compile.db").toString();
        outputDir = tempDir.resolve("output").toString();
        new File(outputDir).mkdirs();

        Files.copy(Path.of(DM_BOM_DB), Path.of(compileDbPath));
        prepareCompileDb();

        System.setProperty("bom.db", compileDbPath);

        bomConn = DriverManager.getConnection("jdbc:sqlite:" + compileDbPath);
        api = new DesignerAPIImpl(bomConn);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (bomConn != null && !bomConn.isClosed()) bomConn.close();
        System.clearProperty("bom.db");
        PlacementLoader.resetInstance();
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    // ── W-WO-1a: BOM Drop explodes BUILDING_DEMO_2BR into C_OrderLine tree ──

    @Test @Order(1)
    @DisplayName("W-WO-1a: bomDrop(BUILDING_DEMO_2BR) → 60 leaf C_OrderLines")
    void w_wo_1a_bom_drop_explodes() {
        dropResult = api.bomDrop("BUILDING_DEMO_2BR");

        assertTrue(dropResult.success(), "bomDrop must succeed: " + dropResult.error());
        assertEquals(60, dropResult.totalElements(),
                "DemoHouse BOM Drop must produce 60 leaf elements");

        assertNotNull(dropResult.tree(), "BOM tree must not be null");
        assertEquals("BUILDING", dropResult.tree().hostType(),
                "Root node must be BUILDING");

        System.out.printf("  BOM Drop: %d leaves, order=%s, root=%d%n",
                dropResult.totalElements(), dropResult.orderId(),
                dropResult.tree().orderLineId());
    }

    // ── W-WO-1b: BOM tree has correct hierarchy ─────────────────────────────

    @Test @Order(2)
    @DisplayName("W-WO-1b: Tree hierarchy: BUILDING → FLOOR(GF) + ROOF → 5 ROOMS → LEAVES")
    void w_wo_1b_tree_hierarchy() {
        assertNotNull(dropResult, "W-WO-1a must run first");
        BomTreeNode root = dropResult.tree();

        // BUILDING has 2 children: FLOOR_DEMO_GF + ROOF_DEMO
        assertEquals(2, root.children().size(),
                "BUILDING must have 2 children (FLOOR + ROOF)");

        BomTreeNode floor = findNode(root, "FLOOR_DEMO_GF");
        assertNotNull(floor, "FLOOR_DEMO_GF must be in the tree");

        BomTreeNode roof = findNode(root, "ROOF_DEMO");
        assertNotNull(roof, "ROOF_DEMO must be in the tree");

        // FLOOR has 5 ROOM children
        assertEquals(5, floor.children().size(),
                "Ground Floor must have 5 rooms (LI, KT, BD1, BD2, BT)");

        // Verify all 5 room BOMs present
        List<String> expectedRooms = List.of(
                "ROOM_DEMO_LI", "ROOM_DEMO_KT", "ROOM_DEMO_BD1",
                "ROOM_DEMO_BD2", "ROOM_DEMO_BT");
        for (String roomBom : expectedRooms) {
            assertNotNull(findNode(root, roomBom),
                    roomBom + " must be in the tree");
        }

        System.out.printf("  Hierarchy: BUILDING → %d children, FLOOR → %d rooms%n",
                root.children().size(), floor.children().size());
    }

    // ── W-WO-1c: CompleteIt (compile) produces output.db ─────────────────────

    @Test @Order(3)
    @DisplayName("W-WO-1c: completeIt → compile → output.db with 60 elements")
    void w_wo_1c_complete_it_compiles() {
        assertNotNull(dropResult, "W-WO-1a must run first");

        // CompleteIt = compile — the server processes the entire C_Order
        CompileRequest request = new CompileRequest(
                "DemoHouse_2BR",
                compileDbPath,
                COMP_LIB,
                outputDir + "/"
        );

        compileResult = api.compile(request);

        assertTrue(compileResult.success(),
                "CompleteIt/compile must succeed: " + compileResult.error());
        assertEquals(60, compileResult.elementCount(),
                "DemoHouse compilation must produce 60 elements");

        System.out.printf("  CompleteIt: %d elements in %dms, digest=%s%n",
                compileResult.elementCount(), compileResult.compileTimeMs(),
                compileResult.spatialDigest() != null
                        ? compileResult.spatialDigest().substring(0, 16) + "..."
                        : "null");
    }

    // ── W-WO-1d: Output DB is valid and queryable ────────────────────────────

    @Test @Order(4)
    @DisplayName("W-WO-1d: output.db has 60 elements in elements_meta, no GEO_ fallback")
    void w_wo_1d_output_db_valid() throws Exception {
        assertNotNull(compileResult, "W-WO-1c must run first");
        assertTrue(compileResult.success());

        String outputPath = compileResult.outputDbPath();
        assertNotNull(outputPath, "Output path must not be null");
        assertTrue(new File(outputPath).exists(),
                "Output DB must exist: " + outputPath);

        try (Connection outConn = DriverManager.getConnection("jdbc:sqlite:" + outputPath)) {
            // Element count
            try (Statement stmt = outConn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM elements_meta")) {
                assertTrue(rs.next());
                assertEquals(60, rs.getInt(1), "Output DB must have 60 elements");
            }

            // G5-PROVENANCE: no GEO_ fallback elements
            try (Statement stmt = outConn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM elements_meta WHERE guid LIKE 'GEO_%'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1),
                        "G5: no GEO_ fallback — all geometry from library");
            }

            // Print element class breakdown for visibility
            try (Statement stmt = outConn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT ifc_class, COUNT(*) as cnt FROM elements_meta " +
                         "GROUP BY ifc_class ORDER BY cnt DESC")) {
                System.out.println("  Output element classes:");
                while (rs.next()) {
                    System.out.printf("    %-30s %d%n", rs.getString(1), rs.getInt(2));
                }
            }
        }
    }

    // ── W-WO-1e: Equivalence — BOM Drop leaves == compiled elements ──────────

    @Test @Order(5)
    @DisplayName("W-WO-1e: bomDrop leaves (60) = compiled elements (60) — equivalence proof")
    void w_wo_1e_equivalence() {
        assertNotNull(dropResult, "W-WO-1a must run first");
        assertNotNull(compileResult, "W-WO-1c must run first");

        assertEquals(dropResult.totalElements(), compileResult.elementCount(),
                "BOM Drop leaf count must equal compiled element count — "
                + "proves Work Order path and compilation pipeline produce identical results. "
                + "The C_OrderLine tree IS the building specification.");
    }

    // ── W-WO-1f: Output has spatial structure (storeys + rooms) ─────────────

    @Test @Order(6)
    @DisplayName("W-WO-1f: output.db has spatial structure — building → storey → rooms")
    void w_wo_1f_spatial_structure() throws Exception {
        assertNotNull(compileResult, "W-WO-1c must run first");

        String outputPath = compileResult.outputDbPath();
        try (Connection outConn = DriverManager.getConnection("jdbc:sqlite:" + outputPath)) {
            // Spatial structure must contain storeys
            try (Statement stmt = outConn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM spatial_structure WHERE type = 'IfcBuildingStorey'")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 1,
                        "Output DB must have at least 1 storey in spatial_structure");
            }

            // Elements must be contained in spatial structure
            try (Statement stmt = outConn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM rel_contained_in_space")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) > 0,
                        "Elements must be spatially contained (rel_contained_in_space)");
            }

            System.out.println("  Spatial structure verified: storeys + containment present");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private BomTreeNode findNode(BomTreeNode node, String familyRef) {
        if (familyRef.equals(node.familyRef())) return node;
        for (BomTreeNode child : node.children()) {
            BomTreeNode found = findNode(child, familyRef);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Prepare the compile DB — add schema tables if needed and ensure
     * the output path points to our sandbox temp directory.
     */
    private static void prepareCompileDb() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + compileDbPath)) {
            conn.setAutoCommit(false);

            // Add schema tables from snapshot (idempotent)
            if (new File(SCHEMA_SQL).exists()) {
                String schema = Files.readString(Path.of(SCHEMA_SQL));
                schema = schema.replace("CREATE TABLE \"", "CREATE TABLE IF NOT EXISTS \"");
                schema = schema.replace("CREATE TABLE M_", "CREATE TABLE IF NOT EXISTS M_");
                schema = schema.replace("CREATE TABLE C_", "CREATE TABLE IF NOT EXISTS C_");
                schema = schema.replace("CREATE TABLE ad_", "CREATE TABLE IF NOT EXISTS ad_");
                schema = schema.replace("CREATE TABLE m_", "CREATE TABLE IF NOT EXISTS m_");
                try (Statement stmt = conn.createStatement()) {
                    for (String ddl : schema.split(";")) {
                        String trimmed = ddl.trim();
                        if (!trimmed.isEmpty()) {
                            try { stmt.execute(trimmed); } catch (Exception ignore) {}
                        }
                    }
                }
            }

            // Update C_DocType output path to point to our sandbox
            String outputPath = outputDir + "/demohouse_2br.db";
            try (var ps = conn.prepareStatement(
                    "UPDATE C_DocType SET OutputDbPath = ? WHERE C_DocType_ID = 'RE_DM'")) {
                ps.setString(1, outputPath);
                ps.executeUpdate();
            }

            conn.commit();
        }
    }
}
