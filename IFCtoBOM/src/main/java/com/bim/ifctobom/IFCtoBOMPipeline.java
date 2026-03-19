package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.BuildingConfig;
import com.bim.ifctobom.CompositionBomBuilder.CompositionResult;
import com.bim.ifctobom.ExtractionReader.ExtractionElement;
import com.bim.ifctobom.ScopeBomBuilder.ScopeResult;
import com.bim.ifctobom.StructuralBomBuilder.BuildResult;
import com.bim.orm.BIMLogger;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * IFC-to-BOM pipeline orchestrator.
 *
 * <p>See {@code docs/YAMLGuide.md} §"How to Add a New Building" for the full
 * pipeline step table, and §"Drift Prevention" for the enforced guard list.
 *
 * <h3>LESSON LEARNED (2026-03-15): QA must gate commit, not follow it</h3>
 * <p>BomValidator originally ran post-commit (read-only). This allowed broken
 * BOM data (NULL child_product_id on leaf lines) to persist on disk. The
 * compiler then silently produced 0 placements. Fix: QA now runs BEFORE
 * commit. Any FAIL causes rollback + exception. Broken data never reaches disk.
 *
 * <h3>LESSON LEARNED: The full data chain must be self-verifying</h3>
 * <p>The pipeline reads from component_library.db and writes to *_BOM.db.
 * If any link in the chain is NULL (M_Product_ID, geometry_hash, etc.),
 * downstream code fails silently. Every step must check its inputs and
 * FAIL LOUD rather than pass NULL forward.
 *
 * <p>Single-transaction pipeline (component_library.db is read-only here —
 * populated separately via {@code IFCtoBOMMain --populate}):
 * <ol>
 *   <li>Load classification YAML</li>
 *   <li>Open connections (output BOM DB + component_library.db read-only)</li>
 *   <li>Create schema if needed</li>
 *   <li>Read extraction data from component_library.db</li>
 *   <li>Pre-flight: geometry completeness guard</li>
 *   <li>Register products in output BOM DB</li>
 *   <li>Build structural BOMs (BUILDING + FLOOR STR)</li>
 *   <li>Build room BOMs + static children</li>
 *   <li>Pre-commit QA validation (FAIL = rollback)</li>
 *   <li>Integrity hash + Commit</li>
 * </ol>
 *
 * <p>Output: a clean per-building BOM DB ({@code *_BOM.db} only — e.g. SH_BOM.db,
 * DX_BOM.db, TE_BOM.db). Never produce or reference a monolithic {@code BOM.db}.
 * The temporary {@code library/BOM.db} used during compilation is created and
 * cleaned up exclusively by {@code run_RosettaStones.sh}.
 */
public class IFCtoBOMPipeline {

    /**
     * Pipeline execution result.
     */
    public record PipelineResult(
            String buildingType,
            int productsRegistered,
            int structuralLines,
            int setLines,
            int roomLines,
            int halfUnitLines,
            int pairLines,
            double aabbWidthMm, double aabbDepthMm, double aabbHeightMm,
            String integrityHash,
            List<String> floorBomIds,
            List<String> setBomIds
    ) {
        public int totalLines() { return structuralLines + setLines + roomLines + halfUnitLines + pairLines; }
    }

    /**
     * Run the full pipeline.
     *
     * <p>DETERMINISTIC — the only human-crafted input is the classification YAML.
     * Everything else is extracted from the reference DB or computed by Java code.
     * No Python scripts, no manual SQL migrations between steps.
     *
     * @param yamlPath  path to classification YAML
     * @param bomDbPath path to output BOM DB (created fresh if not exists)
     * @param compDbPath path to component_library.db (read-only — populated by {@code --populate})
     * @param schemaPath path to schema_snapshot_bom.sql (for creating fresh DB)
     * @return pipeline result
     */
    public static PipelineResult run(Path yamlPath, Path bomDbPath,
                                     Path compDbPath, Path schemaPath)
            throws IOException, SQLException {

        // Implementing BIMLogger.md §Wiring Status — IFCtoBOMPipeline initForRun() + stage()
        // 1. Load YAML (the only human-crafted artifact)
        ClassificationYaml yaml = ClassificationYaml.load(yamlPath);
        BuildingConfig config = yaml.getBuilding();
        BIMLogger.initForRun(config.buildingType() + "_ifctobom");
        BIMLogger.stage(1, "LoadYAML", config.name() + " (" + config.buildingType() + ")");
        System.out.printf("[IFCtoBOM] Building: %s (%s)%n",
                config.name(), config.buildingType());

        // 2. Open connections
        // Delete existing output DB for clean build
        Files.deleteIfExists(bomDbPath);

        Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath);
        Connection compConn = DriverManager.getConnection("jdbc:sqlite:" + compDbPath);

        try {
            bomConn.setAutoCommit(false);

            // 3. Create schema
            BIMLogger.stage(2, "CreateSchema", "from " + (schemaPath != null ? schemaPath.getFileName() : "inline"));
            if (schemaPath != null && Files.exists(schemaPath)) {
                createSchema(bomConn, schemaPath);
                System.out.println("[IFCtoBOM] Schema created from " + schemaPath.getFileName());
            }

            // 3b. Read extraction in-memory (R13: no persistent I_Element_Extraction table)
            BIMLogger.stage(3, "ReadExtraction", "in-memory from component_library.db");
            Map<String, List<ExtractionElement>> storeyElements =
                    ExtractionPopulator.populate(compConn, config.buildingType());

            List<ExtractionElement> allElements = new ArrayList<>();
            storeyElements.values().forEach(allElements::addAll);
            int extractionCount = allElements.size();
            System.out.printf("[IFCtoBOM] Read %d elements across %d storeys%n",
                    extractionCount, storeyElements.size());

            // ── PRE-FLIGHT: Storey mapping validation ─────────────────────────
            // GUARD: Every storey in the extraction DB MUST have a matching key
            // in the YAML storeys section. Unmapped storeys = silent element loss.
            // This catches YAML/extraction divergence before builders run.
            // Also applies to future infrastructure IFC4X3 buildings
            // (reference/infrastructure/) where spatial containers may differ.
            {
                Set<String> yamlStoreys = config.storeys().keySet();
                List<String> unmapped = new ArrayList<>();
                int unmappedCount = 0;
                for (Map.Entry<String, List<ExtractionElement>> entry : storeyElements.entrySet()) {
                    if (!yamlStoreys.contains(entry.getKey())) {
                        unmapped.add(entry.getKey() + " (" + entry.getValue().size() + " elements)");
                        unmappedCount += entry.getValue().size();
                    }
                }
                if (!unmapped.isEmpty()) {
                    String msg = String.format(
                            "[FAIL] Extraction has %d storey(s) not in YAML: %s "
                            + "(%d elements would be silently dropped). "
                            + "Add these storeys to %s or verify extraction storey names.",
                            unmapped.size(), String.join(", ", unmapped),
                            unmappedCount, yamlPath.getFileName());
                    System.err.println(msg);
                    bomConn.rollback();
                    throw new SQLException(msg);
                }
            }

            // ── PRE-FLIGHT: Geometry completeness ─────────────────────────────
            // GUARD: Every product must have a geometry_hash in M_Product_Image.
            // Products without geometry produce 0 placements at compile time.
            // Common for MEP elements (FP, ELEC) and infrastructure IFC4X3 entities.
            int unlinked = ProductRegistrar.countUnlinkedProducts(compConn, config.buildingType());
            if (unlinked > 0) {
                String msg = String.format(
                        "[FAIL] %d product(s) have no geometry_hash in M_Product_Image "
                        + "for %s — these would produce 0 placements at compile time. "
                        + "Check reference DB geometry extraction.",
                        unlinked, config.buildingType());
                System.err.println(msg);
                bomConn.rollback();
                throw new SQLException(msg);
            }

            // 5c. REMOVED (R7): BOMWalker reads M_Product from compConn (component_library.db).
            // No M_Product copy to BOM DB needed. Count from catalog for reporting only.
            int products;
            try (Statement pStmt = compConn.createStatement();
                 ResultSet pRs = pStmt.executeQuery(
                         "SELECT COUNT(*) FROM M_Product WHERE extracted_from = '"
                                 + config.buildingType() + "'")) {
                products = pRs.next() ? pRs.getInt(1) : 0;
            }

            // 6-8. Build BOMs — dispatch based on doc_base_type
            BIMLogger.stage(4, "BuildBOMs", config.docBaseType() + " path");
            //   RE → Scope + Composition + Structural + FloorRoom
            //   CO → DisciplineBomBuilder (BUILDING → FLOOR → DISCIPLINE → LEAF)
            BuildResult structural;
            ScopeResult scope;
            CompositionResult composition;
            int roomLines;

            if ("CO".equals(config.docBaseType())) {
                // CO path: discipline-stratified hierarchy
                structural = DisciplineBomBuilder.build(bomConn, config, storeyElements);
                scope = new ScopeResult(Map.of(), 0, List.of(), Map.of());
                composition = new CompositionResult(Map.of(), 0, 0);
                roomLines = 0;
                System.out.printf("[IFCtoBOM] Discipline: %d lines, AABB=%.0fx%.0fx%.0f mm%n",
                        structural.totalLines(),
                        structural.aabbWidthMm(), structural.aabbDepthMm(), structural.aabbHeightMm());
            } else {
                // RE path: scope + composition + structural + rooms
                scope = ScopeBomBuilder.build(bomConn, config, storeyElements);
                System.out.printf("[IFCtoBOM] Scope spaces: %d SET BOMs, %d lines%n",
                        scope.setBomIds().size(), scope.totalSetLines());

                composition = CompositionBomBuilder.build(
                        bomConn, config, storeyElements);
                if (composition.halfUnitLines() > 0) {
                    System.out.printf("[IFCtoBOM] Composition: %d half-unit lines, %d pair children%n",
                            composition.halfUnitLines(), composition.pairLines());
                }

                Map<String, Set<String>> allExclude = mergeExcludes(
                        scope.excludeByStorey(), composition.excludeByStorey());
                structural = StructuralBomBuilder.build(
                        bomConn, config, storeyElements, allExclude);
                System.out.printf("[IFCtoBOM] Structural: %d lines, AABB=%.0fx%.0fx%.0f mm%n",
                        structural.totalLines(),
                        structural.aabbWidthMm(), structural.aabbDepthMm(), structural.aabbHeightMm());

                // Compute floor LBD data for FloorRoomBomBuilder (§4 tack convention)
                // Building LBD = minimum corner of all elements (world coords)
                double bldgMinX = allElements.stream().mapToDouble(ExtractionElement::minX).min().orElse(0);
                double bldgMinY = allElements.stream().mapToDouble(ExtractionElement::minY).min().orElse(0);
                double bldgMinZ = allElements.stream().mapToDouble(ExtractionElement::minZ).min().orElse(0);

                // floorLbdWorld: storey → [worldMinX, worldMinY, worldMinZ] (world coords)
                // Used to compute: BUILDING→FLOOR offset = floorWorld - bldgWorld
                //                  FLOOR→SET offset     = setWorld - floorWorld
                Map<String, double[]> floorLbdWorld = new LinkedHashMap<>();
                for (var floorEntry : config.floorRooms().entrySet()) {
                    String storeyName = floorEntry.getKey();
                    List<ExtractionElement> elems = storeyElements.get(storeyName);
                    if (elems != null && !elems.isEmpty()) {
                        double fMinX = elems.stream().mapToDouble(ExtractionElement::minX).min().orElse(0);
                        double fMinY = elems.stream().mapToDouble(ExtractionElement::minY).min().orElse(0);
                        double fMinZ = elems.stream().mapToDouble(ExtractionElement::minZ).min().orElse(0);
                        floorLbdWorld.put(storeyName, new double[]{fMinX, fMinY, fMinZ});
                    }
                }

                roomLines = FloorRoomBomBuilder.build(bomConn, config,
                        floorLbdWorld, scope.setLbdPositions(),
                        bldgMinX, bldgMinY, bldgMinZ);
                System.out.printf("[IFCtoBOM] Room BOMs: %d lines%n", roomLines);
            }

            // 9. Pre-commit QA validation — FAIL = rollback, do not produce broken BOM
            BIMLogger.stage(5, "QAValidation", "pre-commit BomValidator");
            // GUARD: This runs BEFORE commit so broken data never reaches disk.
            // Previously ran post-commit (read-only) which let broken BOM.db persist
            // and silently produce 0 placements at compile time.
            int qaFails = BomValidator.validateAndReport(bomConn,
                    extractionCount, composition.halfUnitLines());
            if (qaFails > 0) {
                System.err.printf("[IFCtoBOM] ABORTING — %d QA check(s) FAILED. "
                        + "Fix the data source and re-run.%n", qaFails);
                bomConn.rollback();
                throw new SQLException(qaFails + " BOM QA check(s) FAILED — see report above");
            }

            // 9b. Verb expansion fidelity check — gates on exact verbs (TILE, FRAME)
            int fidelityFails = BomValidator.checkVerbExpansionFidelity(bomConn, allElements, config.buildingType());
            if (fidelityFails > 0) {
                System.err.printf("[IFCtoBOM] ABORTING — %d verb fidelity FAIL(s). "
                        + "Exact verbs (TILE, FRAME) exceeded 5mm threshold.%n", fidelityFails);
                bomConn.rollback();
                throw new SQLException(fidelityFails + " verb fidelity FAIL(s) — see report above");
            }

            // 10. Integrity hash (only reached if QA clean)
            BIMLogger.stage(6, "IntegrityHash", "computing SHA256");
            String hash = IntegrityHash.computeAndStore(bomConn);
            System.out.printf("[IFCtoBOM] Integrity hash: %s%n", hash.substring(0, 16));

            // 10b. Write C_DocType row (R27: spec says it belongs in {PREFIX}_BOM.db)
            // Implementing LAST_MILE_PROBLEM.md R27 — Witness: W-R27-DOCTYPE
            {
                String docTypeId = config.docBaseType() + "_" + config.docSubType();
                String outputPath = "DAGCompiler/lib/output/"
                        + config.buildingType().toLowerCase() + ".db";
                String refPath = "DAGCompiler/lib/input/"
                        + config.buildingType() + "_extracted.db";

                // Read DSL content from file if specified in YAML
                String dslContent = null;
                if (config.dslFile() != null) {
                    Path dslPath = yamlPath.getParent().resolve(config.dslFile());
                    if (Files.exists(dslPath)) {
                        dslContent = Files.readString(dslPath);
                    }
                }

                try (PreparedStatement ps = bomConn.prepareStatement("""
                        INSERT OR REPLACE INTO C_DocType (
                            C_DocType_ID, Name, DocBaseType, DocSubType, IsActive,
                            ProjectName, OutputDbPath, ReferenceDbPath,
                            ExpectedElements, Provenance, SeqNo,
                            AabbWidthMm, AabbDepthMm, AabbHeightMm,
                            GeometryFailThreshold, DSLContent
                        ) VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, 'EXTRACTED', 10, ?, ?, ?, ?, ?)
                        """)) {
                    ps.setString(1, docTypeId);
                    ps.setString(2, config.name());
                    ps.setString(3, config.docBaseType());
                    ps.setString(4, config.docSubType());
                    ps.setString(5, config.buildingType());
                    ps.setString(6, outputPath);
                    ps.setString(7, refPath);
                    ps.setInt(8, extractionCount);
                    ps.setDouble(9, structural.aabbWidthMm());
                    ps.setDouble(10, structural.aabbDepthMm());
                    ps.setDouble(11, structural.aabbHeightMm());
                    ps.setInt(12, config.geometryFailThreshold());
                    if (dslContent != null) {
                        ps.setString(13, dslContent);
                    } else {
                        ps.setNull(13, java.sql.Types.VARCHAR);
                    }
                    ps.executeUpdate();
                }
                BIMLogger.info("PIPELINE", "C_DocType: {} written (R27)", docTypeId);
            }

            // 10c. Store expected element count (R13: no I_Element_Extraction — BOM carries the count)
            try (PreparedStatement ps = bomConn.prepareStatement(
                    "INSERT OR REPLACE INTO ad_sysconfig (config_key, config_value, description) " +
                    "VALUES ('EXPECTED_ELEMENTS', ?, 'Active extraction element count for compilation')")) {
                ps.setString(1, String.valueOf(extractionCount));
                ps.executeUpdate();
            }

            // 11. Commit
            BIMLogger.stage(7, "Commit", bomDbPath.getFileName().toString());
            bomConn.commit();
            BIMLogger.info("PIPELINE", "IFCtoBOM COMPLETE: {} — {} products, {} lines",
                    config.buildingType(), products,
                    structural.totalLines() + scope.totalSetLines() + roomLines
                    + composition.halfUnitLines() + composition.pairLines());
            System.out.println("[IFCtoBOM] Committed to " + bomDbPath.getFileName());

            return new PipelineResult(
                    config.buildingType(),
                    products,
                    structural.totalLines(),
                    scope.totalSetLines(),
                    roomLines,
                    composition.halfUnitLines(),
                    composition.pairLines(),
                    structural.aabbWidthMm(), structural.aabbDepthMm(), structural.aabbHeightMm(),
                    hash,
                    structural.floorBomIds(),
                    scope.setBomIds()
            );

        } catch (Exception e) {
            BIMLogger.error("PIPELINE", "IFCtoBOM ABORT: {}", e.getMessage());
            bomConn.rollback();
            throw e;
        } finally {
            BIMLogger.close();
            bomConn.close();
            compConn.close();
        }
    }

    /**
     * Merge two exclude-by-storey maps (union of sets per storey).
     */
    private static Map<String, Set<String>> mergeExcludes(
            Map<String, Set<String>> a, Map<String, Set<String>> b) {
        if (b.isEmpty()) return a;
        if (a.isEmpty()) return b;
        Map<String, Set<String>> merged = new LinkedHashMap<>(a);
        for (var entry : b.entrySet()) {
            merged.merge(entry.getKey(), entry.getValue(), (s1, s2) -> {
                Set<String> union = new LinkedHashSet<>(s1);
                union.addAll(s2);
                return union;
            });
        }
        return merged;
    }

    /**
     * Create BOM DB schema for IFCtoBOM output.
     * 4 tables: m_bom, m_bom_line (spatial recipe), ad_sysconfig (integrity hash),
     * M_Product (assembly stubs only — BUILDING/FLOOR/SET placeholders).
     * Real product data lives in component_library.db — BOMWalker reads via compConn (R7).
     *
     * <p>ASSUMPTION: The schemaPath parameter is accepted but NOT read.
     * Schema DDL is inlined below. If the schema evolves (e.g. new columns
     * for infrastructure or discipline-level BOMs), update the DDL here —
     * the external .sql file is for documentation only, not runtime use.
     */
    @SuppressWarnings("unused")
    private static void createSchema(Connection conn, Path schemaPath)
            throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // M_Product: assembly stubs (BUILDING, FLOOR, SET placeholders) still
            // written here by ensureAssemblyStub(). Real product data lives in
            // component_library.db — BOMWalker reads via compConn (R7).
            // TODO: migrate assembly stubs to component_library.db, then remove.
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS M_Product (
                    product_id        TEXT PRIMARY KEY,
                    product_type      TEXT NOT NULL,
                    width             REAL NOT NULL,
                    depth             REAL NOT NULL,
                    height            REAL NOT NULL,
                    clear_front       REAL DEFAULT 0,
                    clear_back        REAL DEFAULT 0,
                    clear_left        REAL DEFAULT 0,
                    clear_right       REAL DEFAULT 0,
                    clear_above       REAL DEFAULT 0,
                    clear_below       REAL DEFAULT 0,
                    fits_in           TEXT,
                    requires_host     TEXT,
                    host_min_width    REAL,
                    host_min_height   REAL,
                    qty_per_area      REAL,
                    qty_per_room      INTEGER,
                    qty_per_person    REAL,
                    max_spacing       REAL,
                    conn_points       TEXT,
                    code_ref          TEXT,
                    is_active         INTEGER DEFAULT 1,
                    extracted_from    TEXT NOT NULL DEFAULT 'PENDING',
                    material_name     TEXT,
                    material_rgba     TEXT,
                    component_id      INTEGER,
                    bom_id            TEXT,
                    ifc_class         TEXT,
                    M_Product_Category_ID TEXT
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS m_bom (
                    bom_id            TEXT PRIMARY KEY,
                    bom_name          TEXT NOT NULL,
                    description       TEXT,
                    target_ifc_class  TEXT DEFAULT 'IfcElementAssembly',
                    group_by          TEXT NOT NULL,
                    is_active         INTEGER DEFAULT 1,
                    bom_level         TEXT DEFAULT 'SET',
                    bom_type          TEXT NOT NULL,
                    bom_category      TEXT,
                    doc_base_type     TEXT,
                    doc_sub_type      TEXT,
                    seq_no            INTEGER DEFAULT 10,
                    origin_x          REAL DEFAULT 0.0,
                    origin_y          REAL DEFAULT 0.0,
                    origin_z          REAL DEFAULT 0.0,
                    entity_type       TEXT DEFAULT 'D',
                    aabb_width_mm     INTEGER DEFAULT 0,
                    aabb_depth_mm     INTEGER DEFAULT 0,
                    aabb_height_mm    INTEGER DEFAULT 0
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS m_bom_line (
                    bom_child_id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    bom_id              TEXT NOT NULL REFERENCES m_bom(bom_id),
                    child_product_id    TEXT,
                    child_element_type  TEXT,
                    child_name_pattern  TEXT,
                    role                TEXT NOT NULL,
                    qty_type            TEXT DEFAULT 'VARIABLE',
                    qty                 INTEGER NOT NULL DEFAULT 1,
                    sequence            INTEGER DEFAULT 100,
                    is_active           INTEGER DEFAULT 1,
                    z_rule              TEXT,
                    dx                  REAL DEFAULT 0.0,
                    dy                  REAL DEFAULT 0.0,
                    dz                  REAL DEFAULT 0.0,
                    rotation_rule       TEXT DEFAULT '0',
                    fit_priority        INTEGER DEFAULT 20,
                    min_space_mm        INTEGER DEFAULT 0,
                    locator_ref         TEXT DEFAULT 'FLOAT',
                    is_variance         INTEGER DEFAULT 0,
                    anchor_face         TEXT DEFAULT 'BACK',
                    layout_strategy     TEXT DEFAULT 'LINEAR',
                    allocated_width_mm  INTEGER DEFAULT 0,
                    allocated_depth_mm  INTEGER DEFAULT 0,
                    allocated_height_mm INTEGER DEFAULT 0,
                    component_type      TEXT NOT NULL DEFAULT 'MAKE',
                    storey              TEXT,
                    element_ref         TEXT,
                    ordinal             INTEGER DEFAULT 0,
                    orientation         TEXT,
                    material_name       TEXT,
                    material_rgba       TEXT,
                    entity_type         TEXT DEFAULT 'D',
                    verb_ref            TEXT DEFAULT NULL
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ad_sysconfig (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    config_key   TEXT NOT NULL,
                    config_value TEXT NOT NULL,
                    description  TEXT,
                    is_active    INTEGER DEFAULT 1,
                    UNIQUE(config_key, config_value)
                )
                """);

            // R27: C_DocType belongs in {PREFIX}_BOM.db (was shell-injected)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS C_DocType (
                    C_DocType_ID         TEXT PRIMARY KEY,
                    Name                 TEXT NOT NULL,
                    DocBaseType          TEXT NOT NULL CHECK(DocBaseType IN ('RE','CO','IN','ST')),
                    DocSubType           TEXT,
                    IsDefault            INTEGER DEFAULT 0,
                    IsActive             INTEGER DEFAULT 1,
                    Description          TEXT,
                    ProjectName          TEXT,
                    DSLContent           TEXT,
                    OutputDbPath         TEXT,
                    ReferenceDbPath      TEXT,
                    ExpectedElements     INTEGER,
                    Provenance           TEXT DEFAULT 'EXTRACTED',
                    GeometryFailThreshold INTEGER DEFAULT 0,
                    SeqNo                INTEGER DEFAULT 10,
                    AabbWidthMm          REAL,
                    AabbDepthMm          REAL,
                    AabbHeightMm         REAL,
                    C_Campaign_ID        TEXT,
                    SalesRep_ID          INTEGER
                )
                """);
        }
    }
}
