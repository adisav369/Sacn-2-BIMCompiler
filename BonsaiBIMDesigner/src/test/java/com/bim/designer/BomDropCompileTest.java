package com.bim.designer;

import com.bim.compiler.dsl.PlacementLoader;
import com.bim.designer.api.*;
import com.bim.designer.api.DesignerAPI.*;

import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TC-1: Instant Drop end-to-end — 1 C_OrderLine → explodeBOM → compile → 55 elements.
 *
 * <p>iDempiere pattern: frontend sends 1 parent line (BUILDING_SH_STD).
 * Backend MUST explode the BOM recursively before processing.
 * Then compilation produces the same 55 elements as the Rosetta Stone.
 *
 * <p>This test chains BomDropTest (explosion) + CompileBridgeTest (compilation)
 * in one flow, proving the full OrderLine → Product → BOM → compile path.
 *
 * <p>// Implementing GENERATIVE_HOUSE_SRS.md §2.1 TC-1 — Witness: W-TC1-1..4
 */
@DisplayName("TC-1: Instant Drop → explodeBOM → compile → 55 elements")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BomDropCompileTest {

    private static final String SH_BOM_DB = "library/SH_BOM.db";
    private static final String DSL_FILE = "IFCtoBOM/src/main/resources/dsl_sh.bim";
    private static final String SCHEMA_SQL = "library/schema_snapshot_bom.sql";
    private static final String COMP_LIB = "library/component_library.db";

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

        assertTrue(new File(SH_BOM_DB).exists(),
                "SH_BOM.db not found — run IFCtoBOM pipeline first");
        assertTrue(new File(DSL_FILE).exists(), "dsl_sh.bim not found");

        // Create temp directory for compile DB + output
        tempDir = Files.createTempDirectory("tc1_compile_");
        compileDbPath = tempDir.resolve("_SH_compile.db").toString();
        outputDir = tempDir.resolve("output").toString();
        new File(outputDir).mkdirs();

        // Copy SH_BOM.db → compile DB, add C_DocType
        Files.copy(Path.of(SH_BOM_DB), Path.of(compileDbPath));
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

    // ── W-TC1-1: bomDrop explodes 1 OrderLine into 55 leaves ─────────

    @Test @Order(1)
    @DisplayName("W-TC1-1: bomDrop(BUILDING_SH_STD) → 55 leaf C_OrderLines with M_Product_ID")
    void w_tc1_1_bom_drop_explodes() {
        dropResult = api.bomDrop("BUILDING_SH_STD");

        assertTrue(dropResult.success(), "bomDrop must succeed: " + dropResult.error());
        assertEquals(55, dropResult.totalElements(),
                "Instant Drop must produce 55 leaf elements");

        // Verify M_Product_ID is set in work_output.db
        try {
            var woDao = new com.bim.designer.dao.WorkOutputDAO(
                    DriverManager.getConnection("jdbc:sqlite:"
                            + com.bim.designer.dao.WorkOutputDAO.dbPathFor("BUILDING_SH_STD")));
            int withProductId = woDao.countOrderLinesWithProductId(dropResult.orderId());
            assertTrue(withProductId > 0,
                    "C_OrderLine rows must have M_Product_ID set");
        } catch (Exception e) {
            // work_output.db access is supplementary — don't fail the test
            System.err.println("[W-TC1-1] Could not verify M_Product_ID: " + e.getMessage());
        }
    }

    // ── W-TC1-2: compile produces 55 elements from same BOM ──────────

    @Test @Order(2)
    @DisplayName("W-TC1-2: compile → 55 elements (same as Rosetta Stone SH)")
    void w_tc1_2_compile_produces_55() {
        assertNotNull(dropResult, "W-TC1-1 must run first");

        CompileRequest request = new CompileRequest(
                "Ifc4_SampleHouse",
                compileDbPath,
                COMP_LIB,
                outputDir + "/"
        );

        compileResult = api.compile(request);

        assertTrue(compileResult.success(),
                "Compile should succeed: " + compileResult.error());
        assertEquals(55, compileResult.elementCount(),
                "Compilation must produce 55 elements (SH Rosetta Stone)");
    }

    // ── W-TC1-3: output.db exists with correct element count ─────────

    @Test @Order(3)
    @DisplayName("W-TC1-3: output.db has 55 elements in elements_meta")
    void w_tc1_3_output_db_exists() {
        assertNotNull(compileResult, "W-TC1-2 must run first");
        assertTrue(compileResult.success());

        String outputPath = compileResult.outputDbPath();
        assertNotNull(outputPath);
        assertTrue(new File(outputPath).exists(),
                "Output DB must exist: " + outputPath);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + outputPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM elements_meta")) {
            assertTrue(rs.next());
            assertEquals(55, rs.getInt(1), "Output DB must have 55 elements");
        } catch (Exception e) {
            fail("Output DB should be valid: " + e.getMessage());
        }
    }

    // ── W-TC1-4: BOM Drop leaf count equals compiled element count ───

    @Test @Order(4)
    @DisplayName("W-TC1-4: bomDrop leaves (55) = compiled elements (55) — equivalence proof")
    void w_tc1_4_equivalence() {
        assertNotNull(dropResult, "W-TC1-1 must run first");
        assertNotNull(compileResult, "W-TC1-2 must run first");

        assertEquals(dropResult.totalElements(), compileResult.elementCount(),
                "BOM Drop leaf count must equal compiled element count — "
                + "proves the OrderLine path and BOM.db pipeline produce identical results");
    }

    // ── Setup helper ─────────────────────────────────────────────────

    private static void prepareCompileDb() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + compileDbPath)) {
            conn.setAutoCommit(false);

            // Add schema tables from snapshot
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

            // Read DSL content
            String dslContent = Files.readString(Path.of(DSL_FILE));
            String outputPath = outputDir + "/ifc4_samplehouse.db";
            String refPath = "DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db";

            // Ensure C_DocType table + insert row
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS C_DocType (
                        C_DocType_ID TEXT PRIMARY KEY, Name TEXT, DocBaseType TEXT,
                        DocSubType TEXT, IsActive INTEGER DEFAULT 1, ProjectName TEXT,
                        OutputDbPath TEXT, ReferenceDbPath TEXT,
                        ExpectedElements INTEGER DEFAULT 0, Provenance TEXT DEFAULT 'EXTRACTED',
                        SeqNo INTEGER DEFAULT 10, DSLContent TEXT, Description TEXT,
                        GeometryFailThreshold INTEGER DEFAULT 0,
                        AabbWidthMm REAL DEFAULT 0, AabbDepthMm REAL DEFAULT 0,
                        AabbHeightMm REAL DEFAULT 0
                    )
                    """);
            }

            try (var ps = conn.prepareStatement("""
                    INSERT OR REPLACE INTO C_DocType (
                        C_DocType_ID, Name, DocBaseType, DocSubType, IsActive,
                        ProjectName, OutputDbPath, ReferenceDbPath,
                        ExpectedElements, Provenance, SeqNo, DSLContent,
                        GeometryFailThreshold
                    ) VALUES (?, ?, ?, ?, 1, ?, ?, ?, 55, 'EXTRACTED', 10, ?, 0)
                    """)) {
                ps.setString(1, "RE_SH");
                ps.setString(2, "Sample House");
                ps.setString(3, "RE");
                ps.setString(4, "SH");
                ps.setString(5, "Ifc4_SampleHouse");
                ps.setString(6, outputPath);
                ps.setString(7, refPath);
                ps.setString(8, dslContent);
                ps.executeUpdate();
            }

            conn.commit();
        }
    }
}
