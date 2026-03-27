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
 * <p>See {@code docs/WorkOrderGuide.md} §"How to Add a New Building" for the full
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
        Connection discConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db");

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
            BIMLogger.fine("EXTRACTION", "{}: {} elements across {} storeys",
                    config.buildingType(), extractionCount, storeyElements.size());
            System.out.printf("[IFCtoBOM] Read %d elements across %d storeys%n",
                    extractionCount, storeyElements.size());

            // ── PRE-FLIGHT: Dimension range validation (DV010) ──────────────────
            // Advisory: check element dimensions against mined typical ranges
            // from ERP.db. Logs outliers but never blocks the pipeline.
            {
                // Reuse outer discConn (already connected to ERP.db)
                try {
                    // Layer 1: Dimension range check (DV010)
                    DimensionRangeValidator drv = DimensionRangeValidator.load(discConn);
                    if (drv.hasRules()) {
                        DimensionRangeValidator.Report report =
                                drv.validate(allElements, config.buildingType());
                        report.print();
                        // FL-2: write advisories to W_Validation_Advisory (DV012)
                        // Implementing BIM_Designer_SRS.md §27 — Witness: W-FL-ADVISORY-4
                        try {
                            DimensionRangeValidator.writeAdvisories(discConn, report);
                        } catch (SQLException e) {
                            BIMLogger.warn("IFCtoBOM", "Dimension advisory write skipped: {}", e.getMessage());
                        }
                    }

                    // Layer 2: Building profile check (DV011)
                    BuildingProfileValidator bpv = BuildingProfileValidator.load(discConn);
                    if (bpv.hasProfiles()) {
                        BuildingProfileValidator.Report profileReport =
                                bpv.validate(allElements, config.buildingType());
                        profileReport.print();
                        // FL-2: write advisories to W_Validation_Advisory (DV012)
                        // Implementing BIM_Designer_SRS.md §27 — Witness: W-FL-ADVISORY-5
                        try {
                            BuildingProfileValidator.writeAdvisories(discConn, profileReport);
                        } catch (SQLException e) {
                            BIMLogger.warn("IFCtoBOM", "Profile advisory write skipped: {}", e.getMessage());
                        }
                    }

                    // Layer 3: Shape-aware advisories (FL-5/EYES)
                    // Implementing ACTION_ROADMAP.md §FL-5 — Witness: W-FL-SHAPE-1
                    try {
                        ShapeAdvisoryWriter.Report shapeReport =
                                ShapeAdvisoryWriter.analyze(discConn, allElements, config.buildingType());
                        shapeReport.print();
                    } catch (SQLException e) {
                        BIMLogger.warn("IFCtoBOM", "Shape advisory write skipped: {}", e.getMessage());
                    }
                } catch (Exception e) {
                    BIMLogger.warn("IFCtoBOM", "Dimension range check skipped: {}", e.getMessage());
                }
            }

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

            // ── Ensure product catalog + geometry links (self-contained) ─────
            // Previously these only ran in the separate --populate invocation.
            // Running them here makes the pipeline self-contained — it cannot
            // fail due to a skipped or partial populate step. Both methods are
            // idempotent (INSERT OR IGNORE), so running them twice is harmless.
            int cataloged = ProductRegistrar.ensureProductCatalog(
                    compConn, discConn, allElements, config.buildingType());
            int images = ProductRegistrar.ensureProductImages(compConn, config.buildingType());
            if (cataloged > 0 || images > 0) {
                System.out.printf("[IFCtoBOM] Product catalog: %d new products, %d new image links%n",
                        cataloged, images);
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

            // 5c. Copy leaf products from component_library.db to BOM DB.
            // BOMWalker reads from compConn (R7), but the BOM DB needs a local
            // M_Product catalog so QA validation and downstream tools can verify
            // product coverage without requiring component_library.db access.
            // Implementing BBC.md §2.2 — Witness: W-R7-CATALOG
            int products = ProductRegistrar.ensureProducts(bomConn, compConn, allElements);
            BIMLogger.fine("PIPELINE", "M_Product catalog: {} leaf products copied to BOM DB from component_library.db", products);

            // 6-8. Build BOMs — dispatch based on m_product_category_id (was doc_base_type)
            BIMLogger.stage(4, "BuildBOMs", config.docBaseType() + " path");
            //   RE → Scope + Composition + Structural + FloorRoom
            //   CO → DisciplineBomBuilder (BUILDING → FLOOR → DISCIPLINE → LEAF)
            BuildResult structural;
            ScopeResult scope;
            CompositionResult composition;
            int roomLines;

            if ("CO".equals(config.docBaseType()) || "IN".equals(config.docBaseType())) {
                // CO/IN path: discipline-stratified hierarchy (IN = infrastructure)
                structural = DisciplineBomBuilder.build(bomConn, config, storeyElements);
                scope = new ScopeResult(Map.of(), 0, List.of(), Map.of(), 0);
                composition = new CompositionResult(Map.of(), 0, 0);
                roomLines = 0;
                BIMLogger.fine("EXTRACTION", "{}: {} BOMs (discipline path), AABB={}x{}x{}mm",
                        config.buildingType(), structural.totalLines(),
                        (int) structural.aabbWidthMm(), (int) structural.aabbDepthMm(),
                        (int) structural.aabbHeightMm());
                System.out.printf("[IFCtoBOM] Discipline: %d lines, AABB=%.0fx%.0fx%.0f mm%n",
                        structural.totalLines(),
                        structural.aabbWidthMm(), structural.aabbDepthMm(), structural.aabbHeightMm());
            } else {
                // RE path: scope + composition + structural + rooms
                scope = ScopeBomBuilder.build(bomConn, config, storeyElements);
                System.out.printf("[IFCtoBOM] Scope spaces: %d SET BOMs, %d lines (%d PHANTOM)%n",
                        scope.setBomIds().size(), scope.totalSetLines(), scope.phantomLines());

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
                BIMLogger.fine("EXTRACTION", "{}: {} structural + {} scope + {} composition lines, AABB={}x{}x{}mm",
                        config.buildingType(), structural.totalLines(),
                        scope.totalSetLines(), composition.halfUnitLines() + composition.pairLines(),
                        (int) structural.aabbWidthMm(), (int) structural.aabbDepthMm(),
                        (int) structural.aabbHeightMm());
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

                // Tier 2: C_DocType_ID is INTEGER PK AUTOINCREMENT. Old text key → Value.
                // W018: DocBaseType dropped from C_DocType. doc_sub_type is the single FK to m_bom.
                try (PreparedStatement ps = bomConn.prepareStatement("""
                        INSERT OR REPLACE INTO C_DocType (
                            Value, Name, doc_sub_type, IsActive,
                            ProjectName, OutputDbPath, ReferenceDbPath,
                            ExpectedElements, Provenance, SeqNo,
                            AabbWidthMm, AabbDepthMm, AabbHeightMm,
                            GeometryFailThreshold, DSLContent
                        ) VALUES (?, ?, ?, 1, ?, ?, ?, ?, 'EXTRACTED', 10, ?, ?, ?, ?, ?)
                        """)) {
                    ps.setString(1, docTypeId);
                    ps.setString(2, config.name());
                    ps.setString(3, config.docSubType());
                    ps.setString(4, config.buildingType());
                    ps.setString(5, outputPath);
                    ps.setString(6, refPath);
                    ps.setInt(7, extractionCount);
                    ps.setDouble(8, structural.aabbWidthMm());
                    ps.setDouble(9, structural.aabbDepthMm());
                    ps.setDouble(10, structural.aabbHeightMm());
                    ps.setInt(11, config.geometryFailThreshold());
                    if (dslContent != null) {
                        ps.setString(12, dslContent);
                    } else {
                        ps.setNull(12, java.sql.Types.VARCHAR);
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

            // 10d. Tier 2 backfill: populate Value/Name/M_BOM_ID on INTEGER PK tables.
            // Implementing BBC.md §14.3 IDV-1 Phase C — Witness: W-TIER2-BACKFILL
            // These columns are in the DDL but not written by individual builders.
            // One-time backfill is simpler and safer than modifying every INSERT.
            try (Statement backfill = bomConn.createStatement()) {
                backfill.execute("UPDATE M_Product SET Value = product_id WHERE Value IS NULL");
                backfill.execute("UPDATE M_Product SET Name = product_id WHERE Name IS NULL");
                backfill.execute("UPDATE m_bom SET Value = bom_id WHERE Value IS NULL");
                backfill.execute("UPDATE m_bom SET Name = bom_name WHERE Name IS NULL");
                backfill.execute("""
                    UPDATE m_bom_line SET M_BOM_ID = (
                        SELECT mb.M_BOM_ID FROM m_bom mb WHERE mb.bom_id = m_bom_line.bom_id
                    ) WHERE M_BOM_ID IS NULL
                    """);
                backfill.execute("""
                    UPDATE m_bom_line_ma SET M_BOM_ID = (
                        SELECT mb.M_BOM_ID FROM m_bom mb WHERE mb.bom_id = m_bom_line_ma.bom_id
                    ) WHERE M_BOM_ID IS NULL
                    """);
                // C_DocType: Value written natively by INSERT (no backfill needed)
                BIMLogger.info("PIPELINE", "Tier 2 backfill: Value/Name/M_BOM_ID populated");
            }

            // 11. Commit
            BIMLogger.stage(7, "Commit", bomDbPath.getFileName().toString());
            bomConn.commit();
            BIMLogger.info("PIPELINE", "IFCtoBOM COMPLETE: {} — {} products, {} lines",
                    config.buildingType(), products,
                    structural.totalLines() + scope.totalSetLines() + roomLines
                    + composition.halfUnitLines() + composition.pairLines());
            System.out.println("[IFCtoBOM] Committed to " + bomDbPath.getFileName());

            // ── POST-COMMIT: Mine this building's profile back into the flywheel ──
            // DV011: Each building both uses and enriches the validation pool.
            // This makes the pipeline self-improving — no separate script needed.
            {
                // Reuse outer discConn (already connected to ERP.db)
                try {
                    mineProfile(discConn, allElements, config.buildingType());
                } catch (Exception e) {
                    BIMLogger.warn("IFCtoBOM", "Profile mining skipped: {}", e.getMessage());
                }
            }

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
            discConn.close();
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
            // Implementing BBC.md §14.3 IDV-1 — Witness: W-TIER2-DDL
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS M_Product (
                    M_Product_ID      INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_id        TEXT NOT NULL UNIQUE,
                    Value             TEXT,
                    Name              TEXT,
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
                    M_BOM_ID          INTEGER PRIMARY KEY AUTOINCREMENT,
                    bom_id            TEXT NOT NULL UNIQUE,
                    Value             TEXT,
                    Name              TEXT,
                    bom_name          TEXT NOT NULL,
                    description       TEXT,
                    target_ifc_class  TEXT DEFAULT 'IfcElementAssembly',
                    group_by          TEXT NOT NULL,
                    is_active         INTEGER DEFAULT 1,
                    bom_level         TEXT DEFAULT 'SET',
                    bom_type          TEXT NOT NULL,
                    m_product_category_id TEXT,
                    doc_sub_type      TEXT,
                    seq_no            INTEGER DEFAULT 10,
                    origin_x          REAL DEFAULT 0.0,
                    origin_y          REAL DEFAULT 0.0,
                    origin_z          REAL DEFAULT 0.0,
                    entity_type       TEXT DEFAULT 'D',
                    aabb_width_mm     INTEGER DEFAULT 0,
                    aabb_depth_mm     INTEGER DEFAULT 0,
                    aabb_height_mm    INTEGER DEFAULT 0,
                    aabb_qualifier    TEXT DEFAULT 'OUTER'
                        CHECK(aabb_qualifier IN ('INNER','STRUCTURAL','OUTER','OPENING'))
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS m_bom_line (
                    bom_child_id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    bom_id              TEXT NOT NULL REFERENCES m_bom(bom_id),
                    M_BOM_ID            INTEGER,
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
                    verb_ref            TEXT DEFAULT NULL,
                    shape_archetype     TEXT DEFAULT NULL,
                    scale_band          TEXT DEFAULT NULL,
                    host_element_ref    TEXT DEFAULT NULL
                )
                """);

            // CP-1: Material Allocation — per-instance identity (iDempiere M_InOutLineMA pattern)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS m_bom_line_ma (
                    bom_id      TEXT NOT NULL,
                    M_BOM_ID    INTEGER,
                    sequence    INTEGER NOT NULL,
                    qi          INTEGER NOT NULL,
                    guid        TEXT NOT NULL,
                    PRIMARY KEY (bom_id, sequence, qi),
                    FOREIGN KEY (bom_id) REFERENCES m_bom(bom_id)
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
            // Tier 2: C_DocType_ID INTEGER PK (iDempiere convention). Old TEXT key → Value.
            // W018: DocBaseType dropped. doc_sub_type is FK to m_bom.doc_sub_type.
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS C_DocType (
                    C_DocType_ID         INTEGER PRIMARY KEY AUTOINCREMENT,
                    Value                TEXT NOT NULL UNIQUE,
                    Name                 TEXT NOT NULL,
                    doc_sub_type         TEXT,
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

    /**
     * Mine this building's element profile back into ERP.db.
     * Part of the data flywheel — each compiled building enriches the
     * validation pool for the next one. Idempotent (INSERT OR REPLACE).
     *
     * // Implementing BBC.md §9.1 — Witness: W-DV-PROFILE-MINE
     */
    private static void mineProfile(Connection discConn,
                                     List<ExtractionReader.ExtractionElement> elements,
                                     String buildingType) throws SQLException {
        int total = elements.size();
        if (total == 0) return;

        // Count per IFC class
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (var e : elements) {
            counts.merge(e.ifcClass(), 1, Integer::sum);
        }
        int classCount = counts.size();

        // Check if table exists
        try (ResultSet rs = discConn.getMetaData().getTables(null, null, "ad_building_profile", null)) {
            if (!rs.next()) return;  // table doesn't exist yet
        }

        String sql = "INSERT OR REPLACE INTO ad_building_profile "
                   + "(building_type, ifc_class, element_count, element_ratio, total_elements, class_count) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = discConn.prepareStatement(sql)) {
            for (var entry : counts.entrySet()) {
                ps.setString(1, buildingType);
                ps.setString(2, entry.getKey());
                ps.setInt(3, entry.getValue());
                ps.setDouble(4, 100.0 * entry.getValue() / total);
                ps.setInt(5, total);
                ps.setInt(6, classCount);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        BIMLogger.info("PIPELINE", "Mined profile: {} — {} classes, {} elements → ERP.db",
                buildingType, classCount, total);
    }
}
