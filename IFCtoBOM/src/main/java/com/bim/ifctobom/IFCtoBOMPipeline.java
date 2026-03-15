package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.BuildingConfig;
import com.bim.ifctobom.CompositionBomBuilder.CompositionResult;
import com.bim.ifctobom.ExtractionReader.ExtractionElement;
import com.bim.ifctobom.ScopeBomBuilder.ScopeResult;
import com.bim.ifctobom.StructuralBomBuilder.BuildResult;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * IFC-to-BOM pipeline orchestrator.
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
 * <p>Single-transaction pipeline:
 * <ol>
 *   <li>Load classification YAML</li>
 *   <li>Open connections (output BOM DB + component_library.db)</li>
 *   <li>Create schema if needed</li>
 *   <li>Read extraction data</li>
 *   <li>Ensure M_Product_Image (geometry link) in component_library.db</li>
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
     * @param compDbPath path to component_library.db (read-write for extraction)
     * @param schemaPath path to schema_snapshot_bom.sql (for creating fresh DB)
     * @return pipeline result
     */
    public static PipelineResult run(Path yamlPath, Path bomDbPath,
                                     Path compDbPath, Path schemaPath)
            throws IOException, SQLException {

        // 1. Load YAML (the only human-crafted artifact)
        ClassificationYaml yaml = ClassificationYaml.load(yamlPath);
        BuildingConfig config = yaml.getBuilding();
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
            if (schemaPath != null && Files.exists(schemaPath)) {
                createSchema(bomConn, schemaPath);
                System.out.println("[IFCtoBOM] Schema created from " + schemaPath.getFileName());
            }

            // 3b. Populate I_Element_Extraction from reference DB (deterministic)
            // Replaces Python placement_extractor.py and manual migration_P02.
            // M_Product_ID = element_ref — no invention, pure data derivation.
            int extracted = ExtractionPopulator.populate(compConn, config.buildingType());
            if (extracted > 0) {
                System.out.printf("[IFCtoBOM] Extracted %d elements from reference DB%n", extracted);
            }

            // 4. Read extraction
            Map<String, List<ExtractionElement>> storeyElements =
                    ExtractionReader.readByStorey(compConn, config.buildingType());

            List<ExtractionElement> allElements = new ArrayList<>();
            storeyElements.values().forEach(allElements::addAll);
            System.out.printf("[IFCtoBOM] Read %d elements across %d storeys%n",
                    allElements.size(), storeyElements.size());

            // 5a. Ensure M_Product_Image in component_library.db (one-time extraction guarantee)
            // Links M_Product_ID → geometry_hash via I_Geometry_Map join.
            // The compiler's resolveByProduct() path depends on this table.
            int images = ProductRegistrar.ensureProductImages(compConn, config.buildingType());
            if (images > 0) {
                System.out.printf("[IFCtoBOM] Created %d M_Product_Image rows in component_library.db%n", images);
            }

            // 5b. Register products in output BOM DB
            int products = ProductRegistrar.ensureProducts(bomConn, allElements);
            System.out.printf("[IFCtoBOM] Registered %d products%n", products);

            // 6. Scope space assignment (SET BOMs)
            ScopeResult scope = ScopeBomBuilder.build(bomConn, config, storeyElements);
            System.out.printf("[IFCtoBOM] Scope spaces: %d SET BOMs, %d lines%n",
                    scope.setBomIds().size(), scope.totalSetLines());

            // 6b. Composition (MIRRORED_PAIR → half-unit + pair)
            CompositionResult composition = CompositionBomBuilder.build(
                    bomConn, config, storeyElements);
            if (composition.halfUnitLines() > 0) {
                System.out.printf("[IFCtoBOM] Composition: %d half-unit lines, %d pair children%n",
                        composition.halfUnitLines(), composition.pairLines());
            }

            // 7. Build structural BOMs (excluding scope + composition elements)
            Map<String, Set<String>> allExclude = mergeExcludes(
                    scope.excludeByStorey(), composition.excludeByStorey());
            BuildResult structural = StructuralBomBuilder.build(
                    bomConn, config, storeyElements, allExclude);
            System.out.printf("[IFCtoBOM] Structural: %d lines, AABB=%.0fx%.0fx%.0f mm%n",
                    structural.totalLines(),
                    structural.aabbWidthMm(), structural.aabbDepthMm(), structural.aabbHeightMm());

            // 8. Build room BOMs + static children
            int roomLines = FloorRoomBomBuilder.build(bomConn, config);
            System.out.printf("[IFCtoBOM] Room BOMs: %d lines%n", roomLines);

            // 9. Pre-commit QA validation — FAIL = rollback, do not produce broken BOM
            // GUARD: This runs BEFORE commit so broken data never reaches disk.
            // Previously ran post-commit (read-only) which let broken BOM.db persist
            // and silently produce 0 placements at compile time.
            int qaFails = BomValidator.validateAndReport(bomConn);
            if (qaFails > 0) {
                System.err.printf("[IFCtoBOM] ABORTING — %d QA check(s) FAILED. "
                        + "Fix the data source and re-run.%n", qaFails);
                bomConn.rollback();
                throw new SQLException(qaFails + " BOM QA check(s) FAILED — see report above");
            }

            // 10. Integrity hash (only reached if QA clean)
            String hash = IntegrityHash.computeAndStore(bomConn);
            System.out.printf("[IFCtoBOM] Integrity hash: %s%n", hash.substring(0, 16));

            // 11. Commit
            bomConn.commit();
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
            bomConn.rollback();
            throw e;
        } finally {
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
     * Create minimal BOM DB schema for IFCtoBOM output.
     * Only the 4 tables needed: m_bom, m_bom_line, M_Product, ad_sysconfig.
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
                    entity_type         TEXT DEFAULT 'D'
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
        }
    }
}
