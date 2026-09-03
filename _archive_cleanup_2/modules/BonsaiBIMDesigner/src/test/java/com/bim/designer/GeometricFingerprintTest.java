package com.bim.designer;

import com.bim.compiler.dsl.PlacementLoader;
import com.bim.designer.api.*;
import com.bim.designer.api.DesignerAPI.*;

import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §11 Geometric Fingerprint — BOM-predicted vs compiled verification.
 *
 * <p>General-purpose test: walks the BOM tree to predict IFC class distribution
 * and structural categories, then compiles and verifies the output matches.
 * Works for any building combination (pure SH, SH+FK roof swap, DM, etc.).
 *
 * <p>The BOM fingerprint IS the contract: what the BOM tree promises,
 * the compiler must deliver. No reference DB needed.
 *
 * // Implementing GENERATIVE_HOUSE_SRS.md §11 — Witness: W-FP-1..7
 */
@DisplayName("§11 Geometric Fingerprint — BOM vs compiled verification")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GeometricFingerprintTest {

    private static final String DM_BOM_DB = "library/DM_BOM.db";
    private static final String COMP_LIB  = "library/component_library.db";
    private static final String SCHEMA_SQL = "library/schema_snapshot_bom.sql";

    private static Path tempDir;
    private static String compileDbPath;
    private static String outputDir;
    private static DesignerAPIImpl api;
    private static Connection bomConn;

    // BOM fingerprint — predicted from BOM walk
    private static int predictedLeafCount;
    private static Map<String, Integer> predictedClassDist;
    private static Set<String> predictedCategories;

    // Compiled fingerprint — extracted from output.db
    private static CompileResponse compileResult;

    @BeforeAll
    static void setUp() throws Exception {
        PlacementLoader.resetInstance();

        assertTrue(new File(DM_BOM_DB).exists(),
                "DM_BOM.db not found — run: sqlite3 library/DM_BOM.db < migration/seed_dm_bom.sql");

        tempDir = Files.createTempDirectory("fingerprint_");
        compileDbPath = tempDir.resolve("_DM_fp.db").toString();
        outputDir = tempDir.resolve("output").toString();
        new File(outputDir).mkdirs();

        // Sandbox copy — does not create a new _BOM.db, just isolates for test
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

    // ═══════════════════════════════════════════════════════════════════
    //  Phase 1: BOM Fingerprint — predict from BOM walk
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("W-FP-1a: BOM fingerprint — walk BOM tree, predict IFC class distribution")
    void w_fp_1a_bom_fingerprint() throws Exception {
        predictedClassDist = new LinkedHashMap<>();
        predictedCategories = new TreeSet<>();

        // Walk the BOM tree and resolve IFC class for each leaf via component_library.db
        try (Connection libConn = DriverManager.getConnection("jdbc:sqlite:" + COMP_LIB)) {
            predictedLeafCount = walkBomTree(bomConn, libConn,
                    "BUILDING_DEMO_2BR", predictedClassDist, predictedCategories);
        }

        assertTrue(predictedLeafCount > 0, "BOM must have leaf elements");
        assertFalse(predictedClassDist.isEmpty(), "BOM must predict IFC classes");
        assertFalse(predictedCategories.isEmpty(), "BOM must have structural categories");

        System.out.printf("  BOM fingerprint: %d leaves, %d IFC classes, categories=%s%n",
                predictedLeafCount, predictedClassDist.size(), predictedCategories);
        predictedClassDist.forEach((cls, cnt) ->
                System.out.printf("    %-30s %d%n", cls, cnt));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Phase 2: Compile + extract compiled fingerprint
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(2)
    @DisplayName("W-FP-1b: Compile building and extract compiled fingerprint")
    void w_fp_1b_compile() {
        CompileRequest request = new CompileRequest(
                "DemoHouse_2BR",
                compileDbPath,
                COMP_LIB,
                outputDir + "/"
        );

        compileResult = api.compile(request);
        assertTrue(compileResult.success(),
                "Compile must succeed: " + compileResult.error());
        assertTrue(compileResult.elementCount() > 0,
                "Must produce elements");

        System.out.printf("  Compiled: %d elements in %dms%n",
                compileResult.elementCount(), compileResult.compileTimeMs());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Phase 3: Fingerprint comparison (§11.4 rules)
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(3)
    @DisplayName("W-FP-1: Leaf count = compiled element count")
    void w_fp_1_count_match() {
        assertNotNull(compileResult, "Compile must run first");
        assertEquals(predictedLeafCount, compileResult.elementCount(),
                "BOM leaf count (" + predictedLeafCount + ") must equal "
                + "compiled element count (" + compileResult.elementCount() + ")");
    }

    @Test @Order(4)
    @DisplayName("W-FP-2: IFC class distribution matches BOM prediction")
    void w_fp_2_class_distribution() throws Exception {
        assertNotNull(compileResult, "Compile must run first");

        String outputPath = compileResult.outputDbPath();
        Map<String, Integer> compiledClassDist = new LinkedHashMap<>();

        try (Connection outConn = DriverManager.getConnection("jdbc:sqlite:" + outputPath);
             Statement stmt = outConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT ifc_class, COUNT(*) FROM elements_meta GROUP BY ifc_class")) {
            while (rs.next()) {
                compiledClassDist.put(rs.getString(1), rs.getInt(2));
            }
        }

        // Compare each predicted class
        for (Map.Entry<String, Integer> entry : predictedClassDist.entrySet()) {
            String cls = entry.getKey();
            int predicted = entry.getValue();
            int compiled = compiledClassDist.getOrDefault(cls, 0);
            assertEquals(predicted, compiled,
                    "IFC class " + cls + ": predicted=" + predicted + " compiled=" + compiled);
        }

        // No unexpected classes in output
        for (String cls : compiledClassDist.keySet()) {
            assertTrue(predictedClassDist.containsKey(cls),
                    "Unexpected IFC class in output: " + cls
                    + " (count=" + compiledClassDist.get(cls) + ")");
        }
    }

    @Test @Order(5)
    @DisplayName("W-FP-3: All BOM structural categories present in compiled output")
    void w_fp_3_structural_completeness() throws Exception {
        assertNotNull(compileResult, "Compile must run first");

        String outputPath = compileResult.outputDbPath();
        Set<String> compiledCategories = new TreeSet<>();

        try (Connection outConn = DriverManager.getConnection("jdbc:sqlite:" + outputPath);
             Statement stmt = outConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT DISTINCT ifc_class FROM elements_meta")) {
            while (rs.next()) {
                compiledCategories.add(toCategory(rs.getString(1)));
            }
        }

        for (String cat : predictedCategories) {
            assertTrue(compiledCategories.contains(cat),
                    "Structural category missing from output: " + cat
                    + " (predicted categories: " + predictedCategories
                    + ", compiled: " + compiledCategories + ")");
        }
    }

    @Test @Order(6)
    @DisplayName("W-FP-4: No zero-extent elements in compiled output")
    void w_fp_4_no_zero_extent() throws Exception {
        assertNotNull(compileResult, "Compile must run first");

        String outputPath = compileResult.outputDbPath();
        try (Connection outConn = DriverManager.getConnection("jdbc:sqlite:" + outputPath);
             Statement stmt = outConn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                     SELECT COUNT(*) FROM elements_rtree
                     WHERE (maxX - minX) <= 0 OR (maxY - minY) <= 0 OR (maxZ - minZ) <= 0""")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1),
                    "No elements with zero or negative extent allowed");
        }
    }

    @Test @Order(7)
    @DisplayName("W-FP-5: No GEO_ fallback elements (G5 provenance)")
    void w_fp_5_provenance() throws Exception {
        assertNotNull(compileResult, "Compile must run first");

        String outputPath = compileResult.outputDbPath();
        try (Connection outConn = DriverManager.getConnection("jdbc:sqlite:" + outputPath);
             Statement stmt = outConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM elements_meta WHERE guid LIKE 'GEO_%'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1),
                    "G5: no GEO_ fallback — all geometry from library");
        }
    }

    @Test @Order(8)
    @DisplayName("W-FP-7: Compiled envelope is finite and non-degenerate")
    void w_fp_7_envelope() throws Exception {
        assertNotNull(compileResult, "Compile must run first");

        // Compiled envelope from R*Tree (meters)
        String outputPath = compileResult.outputDbPath();
        try (Connection outConn = DriverManager.getConnection("jdbc:sqlite:" + outputPath);
             Statement stmt = outConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT MIN(minX), MAX(maxX), MIN(minY), MAX(maxY), MIN(minZ), MAX(maxZ) "
                     + "FROM elements_rtree")) {
            assertTrue(rs.next());
            double minX = rs.getDouble(1), maxX = rs.getDouble(2);
            double minY = rs.getDouble(3), maxY = rs.getDouble(4);
            double minZ = rs.getDouble(5), maxZ = rs.getDouble(6);

            double compW = (maxX - minX) * 1000; // m → mm
            double compD = (maxY - minY) * 1000;
            double compH = (maxZ - minZ) * 1000;

            // Envelope must be non-degenerate (all axes > 0)
            assertTrue(compW > 0, "Compiled width must be positive, got " + compW);
            assertTrue(compD > 0, "Compiled depth must be positive, got " + compD);
            assertTrue(compH > 0, "Compiled height must be positive, got " + compH);

            // Envelope must be finite (no runaway coordinates)
            assertTrue(compW < 200_000, "Width < 200m sanity check, got " + compW);
            assertTrue(compD < 200_000, "Depth < 200m sanity check, got " + compD);
            assertTrue(compH < 50_000, "Height < 50m sanity check, got " + compH);

            // Coordinates must be finite
            assertTrue(Double.isFinite(minX) && Double.isFinite(maxX),
                    "X coordinates must be finite");
            assertTrue(Double.isFinite(minY) && Double.isFinite(maxY),
                    "Y coordinates must be finite");
            assertTrue(Double.isFinite(minZ) && Double.isFinite(maxZ),
                    "Z coordinates must be finite");

            System.out.printf("  Envelope: %.0f×%.0f×%.0fmm, origin=(%.1f,%.1f,%.1f)m%n",
                    compW, compD, compH, minX, minY, minZ);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BOM Walk — general-purpose recursive BOM tree traversal
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Walk a BOM tree recursively, counting leaves and resolving IFC classes.
     * General-purpose: works for any building (SH, FK, DM, configured).
     *
     * @param bomConn     connection to BOM DB (m_bom, m_bom_line)
     * @param libConn     connection to component_library.db (M_Product)
     * @param bomId       root BOM ID to walk
     * @param classDist   output: IFC class → leaf count
     * @param categories  output: structural categories found
     * @return total leaf count
     */
    static int walkBomTree(Connection bomConn, Connection libConn, String bomId,
                           Map<String, Integer> classDist, Set<String> categories)
            throws SQLException {
        int leaves = 0;

        try (PreparedStatement ps = bomConn.prepareStatement(
                "SELECT child_product_id, role, component_type, entity_type "
                + "FROM m_bom_line WHERE bom_id = ? AND is_active = 1 ORDER BY sequence")) {
            ps.setString(1, bomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String childId = rs.getString("child_product_id");
                    String role = rs.getString("role");
                    String entityType = rs.getString("entity_type");

                    // Skip PHANTOM elements
                    if ("PH".equals(entityType)) continue;
                    if (childId != null && childId.startsWith("PHANTOM")) continue;

                    // Check if child is a sub-assembly (has its own m_bom entry)
                    boolean isSubAssembly = false;
                    try (PreparedStatement ps2 = bomConn.prepareStatement(
                            "SELECT 1 FROM m_bom WHERE bom_id = ?")) {
                        ps2.setString(1, childId);
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            isSubAssembly = rs2.next();
                        }
                    }

                    if (isSubAssembly) {
                        // Recurse into sub-assembly
                        leaves += walkBomTree(bomConn, libConn, childId,
                                classDist, categories);
                    } else {
                        // Leaf — resolve IFC class
                        String ifcClass = resolveIfcClass(role, childId, libConn);
                        classDist.merge(ifcClass, 1, Integer::sum);
                        categories.add(toCategory(ifcClass));
                        leaves++;
                    }
                }
            }
        }
        return leaves;
    }

    /**
     * Resolve IFC class for a leaf element. Same priority as PlacementCollectorVisitor:
     * 1. role starting with "Ifc" (EXTRACTED convention)
     * 2. M_Product.ifc_class from component_library.db
     * 3. child_product_id starting with "Ifc"
     * 4. "Unknown"
     */
    private static String resolveIfcClass(String role, String productId,
                                          Connection libConn) throws SQLException {
        if (role != null && role.startsWith("Ifc")) return role;

        if (productId != null) {
            try (PreparedStatement ps = libConn.prepareStatement(
                    "SELECT ifc_class FROM M_Product WHERE product_id = ?")) {
                ps.setString(1, productId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getString(1) != null) {
                        return rs.getString(1);
                    }
                }
            }
        }

        if (productId != null && productId.startsWith("Ifc")) return productId;

        return "Unknown";
    }

    /**
     * Map an IFC class to a structural category for completeness checks.
     */
    static String toCategory(String ifcClass) {
        if (ifcClass == null) return "UNKNOWN";
        return switch (ifcClass) {
            case "IfcWall", "IfcCurtainWall" -> "WALL";
            case "IfcSlab" -> "SLAB";
            case "IfcRoof" -> "ROOF";
            case "IfcDoor" -> "DOOR";
            case "IfcWindow" -> "WINDOW";
            case "IfcBeam" -> "BEAM";
            case "IfcColumn" -> "COLUMN";
            case "IfcMember" -> "MEMBER";
            case "IfcPlate" -> "PLATE";
            case "IfcRailing" -> "RAILING";
            case "IfcFurnishingElement", "IfcFurniture" -> "FURNISHING";
            case "IfcFlowTerminal", "IfcLightFixture" -> "MEP";
            default -> ifcClass.replace("Ifc", "").toUpperCase();
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Setup helper
    // ═══════════════════════════════════════════════════════════════════

    private static void prepareCompileDb() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + compileDbPath)) {
            conn.setAutoCommit(false);

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

            // Update output path to sandbox
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
