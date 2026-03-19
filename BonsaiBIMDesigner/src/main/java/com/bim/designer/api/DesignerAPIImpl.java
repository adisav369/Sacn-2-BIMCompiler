package com.bim.designer.api;

import com.bim.compiler.dsl.BuildingRegistry;
import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;
import com.bim.compiler.dsl.CompilationPipeline;
import com.bim.designer.assembly.AssemblyBuilderService;
import com.bim.designer.compile.ChangeSet;
import com.bim.designer.compile.IncrementalCompiler;
import com.bim.designer.compile.RoomLayoutGenerator;
import com.bim.designer.dao.DesignerDAO;
import com.bim.designer.dao.DesignerDAO.BuildingTypeRow;
import com.bim.designer.dao.DesignerDAO.CategoryRow;
import com.bim.designer.dao.WorkOutputDAO;
import com.bim.designer.validation.FacilityType;
import com.bim.designer.validation.GradingStrategy;
import com.bim.designer.validation.PlacementContext;
import com.bim.designer.validation.PlacementRequest;
import com.bim.designer.validation.PlacementValidator;
import com.bim.designer.validation.PlacementValidatorImpl;
import com.bim.designer.validation.TerrainSnap;
import com.bim.designer.validation.ValidationVerdict;
import com.bim.orm.BIMLogger;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of {@link DesignerAPI}.
 *
 * <p>Three-layer separation:
 * <ol>
 *   <li><b>API layer</b> (this class) — orchestrates requests, maps DAO rows
 *       to API records, catches exceptions, returns typed responses</li>
 *   <li><b>DAO layer</b> ({@link DesignerDAO}) — SQL queries against BOM.db,
 *       returns raw row records, never closes connections</li>
 *   <li><b>Compile layer</b> ({@link IncrementalCompiler}) — scope detection
 *       and pipeline delegation</li>
 * </ol>
 *
 * <p>This class never reads YAML content. It delegates building discovery
 * to the DAO (which reads C_DocType) and verb dispatch to the compile layer
 * (which uses VerbRegistry). YAML restructuring cannot break this class.
 *
 * <p><b>Stub mode:</b> When the underlying pipeline is not yet wired,
 * {@link #compile} and {@link #executeVerb} return structured stub responses
 * for demo/POC purposes. The DAO queries work against any valid BOM.db —
 * including the POC schema created by {@code DesignerServerTest}.
 */
public class DesignerAPIImpl implements DesignerAPI {

    private static final Logger LOG = Logger.getLogger(DesignerAPIImpl.class.getName());
    private static final String TAG = "DesignerAPI";

    private final Connection bomConn;
    private final DesignerDAO dao;
    private final IncrementalCompiler incrementalCompiler;
    private final PlacementValidator validator;

    /** Lazily opened work_output.db connections, keyed by buildingId. */
    private final java.util.Map<String, Connection> workOutputConns = new java.util.concurrent.ConcurrentHashMap<>();

    /** Lazily initialised assembly builder (reads component_library.db). */
    private volatile AssemblyBuilderService assemblyService;

    /**
     * @param bomConn  JDBC connection to BOM.db (caller owns lifecycle)
     */
    public DesignerAPIImpl(Connection bomConn) {
        this(bomConn, new PlacementValidatorImpl());
    }

    /**
     * @param bomConn   JDBC connection to BOM.db (caller owns lifecycle)
     * @param validator PlacementValidator instance (injectable for testing)
     */
    public DesignerAPIImpl(Connection bomConn, PlacementValidator validator) {
        this.bomConn = bomConn;
        this.dao = new DesignerDAO(bomConn);
        this.incrementalCompiler = new IncrementalCompiler();
        this.validator = validator;
    }

    /**
     * Get or create a work_output.db connection for a building.
     * Lazily initialises schema on first access.
     */
    private WorkOutputDAO getWorkOutputDAO(String buildingId) throws Exception {
        Connection woConn = workOutputConns.computeIfAbsent(buildingId, id -> {
            try {
                String dbPath = WorkOutputDAO.dbPathFor(id);
                Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                WorkOutputDAO initDao = new WorkOutputDAO(c);
                initDao.initSchema();
                BIMLogger.info(TAG, "Opened work_output.db: {}", dbPath);
                return c;
            } catch (Exception e) {
                throw new RuntimeException("Failed to open work_output.db for " + id, e);
            }
        });
        return new WorkOutputDAO(woConn);
    }

    // ── Compilation ─────────────────────────────────────────────────

    // Implementing BIM_Designer_SRS.md §22 — Witness: W-COMPILE-1
    @Override
    public CompileResponse compile(CompileRequest request) {
        long t0 = System.currentTimeMillis();
        String previousBomDb = System.getProperty("bom.db");
        try {
            String bomDbPath = request.bomDbPath();

            // If BOM DB file doesn't exist or is empty, fall back to DAO-based stub
            // (supports in-memory test mode where bomDbPath is synthetic)
            File bomDbFile = new File(bomDbPath);
            if (!bomDbFile.exists() || bomDbFile.length() == 0) {
                return compileStub(request, t0);
            }

            // Set system property — pipeline internals read bom.db for connections
            System.setProperty("bom.db", bomDbPath);

            // Load BuildingEntry from the BOM DB's C_DocType
            BuildingEntry entry = loadBuildingEntry(bomDbPath, request.buildingId());
            if (entry == null) {
                return CompileResponse.failure("Unknown building in BOM DB: " + request.buildingId());
            }

            // Ensure output directory exists
            String outputDir = request.outputDir();
            if (outputDir != null && !outputDir.isBlank()) {
                new File(outputDir).mkdirs();
            }

            LOG.info(() -> String.format("COMPILE %s from %s [%s/%s] ...",
                    request.buildingId(), bomDbPath,
                    entry.docBaseType(), entry.docSubType()));

            // Run the real 9-stage compilation pipeline
            CompilationPipeline.PipelineResult result = CompilationPipeline.run(entry);

            long elapsed = System.currentTimeMillis() - t0;
            LOG.info(() -> String.format("COMPILE %s → %d elements in %dms, digest=%s",
                    request.buildingId(), result.elementCount(), elapsed,
                    result.spatialDigest() != null
                            ? result.spatialDigest().substring(0, Math.min(16, result.spatialDigest().length())) + "..."
                            : "null"));

            return CompileResponse.success(
                    result.elementCount(),
                    elapsed,
                    entry.outputDbPath(),
                    result.spatialDigest()
            );
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Compile failed for " + request.buildingId(), e);
            return CompileResponse.failure(e.getMessage());
        } finally {
            // Restore previous bom.db property
            if (previousBomDb != null) {
                System.setProperty("bom.db", previousBomDb);
            } else {
                System.clearProperty("bom.db");
            }
        }
    }

    /**
     * Load a BuildingEntry directly from a BOM DB file, bypassing BuildingRegistry's
     * cached static DB_PATH. This allows compile() to work with any BOM DB path
     * without depending on when BuildingRegistry was class-loaded.
     */
    private static BuildingEntry loadBuildingEntry(String bomDbPath, String buildingId) {
        String sql = """
                SELECT d.C_DocType_ID, d.ProjectName, d.Name, d.DocBaseType, d.DocSubType,
                       d.DSLContent, d.OutputDbPath, d.ReferenceDbPath, d.IsActive, d.SeqNo,
                       d.ExpectedElements, d.Provenance, d.Description,
                       d.GeometryFailThreshold,
                       COALESCE(b.aabb_width_mm, 0) AS AabbWidthMm,
                       COALESCE(b.aabb_depth_mm, 0) AS AabbDepthMm,
                       COALESCE(b.aabb_height_mm, 0) AS AabbHeightMm
                FROM C_DocType d
                LEFT JOIN m_bom b ON b.doc_sub_type = d.DocSubType
                  AND b.doc_base_type = d.DocBaseType
                  AND b.bom_type = 'BUILDING' AND b.is_active = 1
                WHERE d.ProjectName = ?
                """;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new BuildingEntry(
                            rs.getString("C_DocType_ID"),
                            rs.getString("ProjectName"),
                            rs.getString("Name"),
                            rs.getString("DocBaseType"),
                            rs.getString("DocSubType"),
                            rs.getString("DSLContent"),
                            rs.getString("OutputDbPath"),
                            rs.getString("ReferenceDbPath"),
                            rs.getInt("IsActive") == 1,
                            rs.getInt("SeqNo"),
                            rs.getInt("ExpectedElements"),
                            rs.getString("Provenance"),
                            rs.getString("Description"),
                            rs.getInt("GeometryFailThreshold"),
                            rs.getDouble("AabbWidthMm"),
                            rs.getDouble("AabbDepthMm"),
                            rs.getDouble("AabbHeightMm")
                    );
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load BuildingEntry from " + bomDbPath, e);
        }
        return null;
    }

    /**
     * Stub compile for test mode — returns DAO-based response when BOM DB file
     * doesn't exist on disk (e.g., in-memory test with StubDataSeeder).
     */
    private CompileResponse compileStub(CompileRequest request, long t0) {
        try {
            BuildingTypeRow bt = dao.getBuildingType(request.buildingId());
            if (bt == null) {
                return CompileResponse.failure("Unknown building: " + request.buildingId());
            }
            long elapsed = System.currentTimeMillis() - t0;
            LOG.info(() -> String.format("COMPILE %s → %d elements (stub) in %dms",
                    request.buildingId(), bt.expectedElements(), elapsed));
            return CompileResponse.success(
                    bt.expectedElements(),
                    elapsed,
                    request.outputDir() + bt.projectName().toLowerCase() + ".db",
                    "stub-digest-" + bt.projectName()
            );
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Compile stub failed", e);
            return CompileResponse.failure(e.getMessage());
        }
    }

    @Override
    public CompileResponse compileIncremental(CompileRequest request, ChangeSet changes) {
        CompileResponse scoped = incrementalCompiler.compile(request, changes);
        if (scoped != null) return scoped;
        // Sentinel null = fall back to full compile
        return compile(request);
    }

    // ── Create new building ───────────────────────────────────────────

    @Override
    public CreateNewResponse createNew(CreateNewRequest request) {
        long t0 = System.currentTimeMillis();
        try {
            LOG.info(() -> String.format(
                    "CREATE_NEW name=%s type=%s jurisdiction=%s site=%.0fx%.0f rooms=%dBR+%dBT storeys=%d",
                    request.buildingName(), request.buildingType(), request.jurisdiction(),
                    request.siteWidthMm(), request.siteDepthMm(),
                    request.numBedrooms(), request.numBathrooms(), request.storeys()));

            var bboxes = RoomLayoutGenerator.generate(request);

            // Estimate element count: per room = ~7 elements (4 walls + slab + door + window)
            long roomCount = bboxes.stream().filter(b -> "ROOM".equals(b.bomType())).count();
            int estimatedElements = (int) roomCount * 7;

            long elapsed = System.currentTimeMillis() - t0;

            LOG.info(() -> String.format(
                    "CREATE_NEW → %d bboxes (%d rooms, ~%d elements) in %dms",
                    bboxes.size(), roomCount, estimatedElements, elapsed));

            return CreateNewResponse.success(
                    estimatedElements,
                    elapsed,
                    "layout-" + request.buildingName().replaceAll("\\s+", "_"),
                    bboxes
            );
        } catch (Exception e) {
            LOG.log(Level.WARNING, "createNew failed", e);
            return CreateNewResponse.failure(e.getMessage());
        }
    }

    // ── Building discovery (YAML-opaque — reads C_DocType via DAO) ──

    // Implementing INFRA_DESIGNER_SRS.md §0.1 — Witness: W-INFRA-LIST-1
    @Override
    public List<BuildingTypeInfo> listBuildingTypes() {
        try {
            return dao.listBuildingTypes().stream()
                    .map(r -> new BuildingTypeInfo(
                            r.docTypeId(), r.name(), r.docSubType(),
                            r.expectedElements(),
                            r.aabbWidthMm(), r.aabbDepthMm(), r.aabbHeightMm(),
                            deriveFacilityType(r.docBaseType(), r.docSubType())
                    ))
                    .toList();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "listBuildingTypes failed", e);
            return List.of();
        }
    }

    /**
     * Map DocBaseType + DocSubType to FacilityType string for the API.
     * IN = infrastructure — derive specific type from DocSubType (BR/RD/RL).
     * All others = null (BUILDING default).
     *
     * // Implementing INFRA_DESIGNER_SRS.md §0.1
     */
    static String deriveFacilityType(String docBaseType, String docSubType) {
        if (!"IN".equals(docBaseType)) return null;  // null = BUILDING
        if (docSubType == null) return "BRIDGE";      // safe default for infra
        return switch (docSubType) {
            case "BR" -> "BRIDGE";
            case "RD" -> "ROAD";
            case "RL" -> "RAILWAY";
            default   -> "BRIDGE";  // unknown infra subtype → default
        };
    }

    @Override
    public List<CategoryInfo> listCategories(String docSubType) {
        try {
            return dao.listCategories(docSubType).stream()
                    .map(r -> new CategoryInfo(r.categoryName(), r.bomType(), r.bomCount()))
                    .toList();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "listCategories failed", e);
            return List.of();
        }
    }

    // Implementing INFRA_DESIGNER_SRS.md §0.2 — Witness: W-INFRA-SEG-1
    @Override
    public List<SegmentInfo> listSegments(String buildingBomId) {
        try {
            return dao.listSegments(buildingBomId).stream()
                    .map(r -> new SegmentInfo(
                            r.bomId(), r.name(), r.bomCategory(),
                            r.elementCount(), r.disciplines()))
                    .toList();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "listSegments failed for " + buildingBomId, e);
            return List.of();
        }
    }

    @Override
    public List<FacilityTypeInfo> listFacilityTypes() {
        List<FacilityTypeInfo> result = new ArrayList<>();
        for (FacilityType ft : FacilityType.values()) {
            result.add(new FacilityTypeInfo(ft.name(), ft.isInfrastructure(), ft.provenance()));
        }
        return result;
    }

    // ── Design Mode — Snap + Validate (§17.10) ────────────────────

    // Implementing BBC.md §4, BIM_Designer_SRS.md §17, CORE_SRS.md §3.1
    // Witness: W-SNAP-1, W-SNAP-FIX-1, W-INFRA-UI-FILTER
    @Override
    public SnapResponse snap(java.util.List<DesignBBox> bboxes, SnapOptions options) {
        long t0 = System.currentTimeMillis();
        try {
            String jurisdiction = options.jurisdiction();
            int gridMm = options.gridMm();
            String fixRule = options.fixRule();
            String fixBomId = options.fixBomId();
            String facilityType = options.facilityType();
            PlacementContext terrainCtx = options.terrainContext();
            TerrainSnap terrainSnap = options.terrainSnap();
            GradingStrategy grading = options.gradingStrategy();

            BIMLogger.info(TAG, "SNAP {} bboxes, jurisdiction={}, grid={}mm, fixRule={}, fixBomId={}, facility={}, terrain={}, grading={}",
                    bboxes.size(), jurisdiction, gridMm, fixRule, fixBomId, facilityType,
                    terrainCtx != null ? terrainCtx.contextType() : "none",
                    grading != null ? grading : "contour(default)");

            // Activate facility-type-scoped rules if specified
            if (facilityType != null) {
                activateForFacilityType(jurisdiction, facilityType);
            }

            // Click-to-fix: pre-process target bbox if fixRule and fixBomId are set
            List<DesignBBox> inputBboxes = bboxes;
            if (fixRule != null && fixBomId != null && validator.isActive()) {
                inputBboxes = applyClickToFix(bboxes, fixRule, fixBomId, gridMm);
            }

            List<DesignBBox> adjusted = new ArrayList<>(inputBboxes.size());
            List<Adjustment> adjustments = new ArrayList<>();

            for (DesignBBox bb : inputBboxes) {
                // Validate ROOM (building), SEGMENT, LEAF (infrastructure) bomTypes
                // Implementing INFRA_DESIGNER_SRS.md §2.2 — Witness: W-INFRA-SNAP-1
                if (!"ROOM".equals(bb.bomType())
                        && !"SEGMENT".equals(bb.bomType())
                        && !"LEAF".equals(bb.bomType())) {
                    adjusted.add(bb);
                    continue;
                }

                double w = bb.maxX() - bb.minX();
                double d = bb.maxY() - bb.minY();
                double h = bb.maxZ() - bb.minZ();

                // Grid snap
                if (gridMm > 0) {
                    double snappedW = Math.round(w / gridMm) * gridMm;
                    double snappedD = Math.round(d / gridMm) * gridMm;
                    if (snappedW != w) {
                        adjustments.add(new Adjustment(bb.bomId(), "GRID_SNAP", "width",
                                w, snappedW));
                        w = snappedW;
                    }
                    if (snappedD != d) {
                        adjustments.add(new Adjustment(bb.bomId(), "GRID_SNAP", "depth",
                                d, snappedD));
                        d = snappedD;
                    }
                }

                // Terrain Z adjustment — snap bbox to terrain surface
                // GradingStrategy controls design Z: CONTOUR follows terrain,
                // STRAIGHT uses fixed level, BLEND interpolates between them.
                // Default (null grading) = CONTOUR (follow terrain exactly).
                // Implementing INFRA_DESIGNER_SRS.md §I-4+§6 — Witness: W-SNAP-TERRAIN-1
                double minZ = bb.minZ();
                if (terrainCtx != null && terrainSnap != null) {
                    double centreX = (bb.minX() + bb.minX() + w) / 2.0;
                    double centreY = (bb.minY() + bb.minY() + d) / 2.0;

                    // Apply grading strategy to modify the terrain context's Z
                    double terrainZ = terrainCtx.elevationAt(centreX, centreY);
                    double designZ = (grading != null)
                            ? grading.designZAt(terrainZ)
                            : terrainZ;  // null grading = pure contour

                    // TerrainSnap computes final Z from design elevation
                    // (ON_SURFACE adds offset, ABOVE adds clearance, etc.)
                    double newBaseZ = switch (terrainSnap.mode()) {
                        case ON_SURFACE -> designZ + terrainSnap.offsetMm();
                        case ABOVE      -> designZ + terrainSnap.offsetMm();
                        case BELOW      -> designZ - terrainSnap.offsetMm() - h;
                        case PIER       -> designZ;
                    };

                    if (Math.abs(newBaseZ - minZ) > 0.1) {
                        adjustments.add(new Adjustment(bb.bomId(), "TERRAIN_Z", "minZ",
                                minZ, newBaseZ));
                    }
                    minZ = newBaseZ;
                }

                // PlacementValidator check
                if (validator.isActive()) {
                    PlacementRequest req = new PlacementRequest(
                            bb.category(), bb.ifcClass(), null,
                            w, d, h,
                            bb.minX(), bb.minY(), minZ,
                            "SNAP", 0, bb.storey());

                    ValidationVerdict verdict = validator.validate(req);
                    if (verdict.isBlocked()) {
                        // Report the violation as an adjustment (delta display: UX-F-19)
                        adjustments.add(new Adjustment(bb.bomId(),
                                verdict.ruleName(), "min_dim",
                                verdict.actualValue(), verdict.requiredValue()));

                        BIMLogger.warn(TAG, "SNAP BLOCK {}: {} ({:.0f} < {:.0f})",
                                bb.bomId(), verdict.ruleName(),
                                verdict.actualValue(), verdict.requiredValue());
                    }
                }

                adjusted.add(new DesignBBox(
                        bb.bomId(), bb.name(), bb.bomType(), bb.category(),
                        bb.ifcClass(), bb.storey(), bb.parentBomId(),
                        bb.minX(), bb.minY(), minZ,
                        bb.minX() + w, bb.minY() + d, minZ + h));
            }

            long elapsed = System.currentTimeMillis() - t0;
            BIMLogger.info(TAG, "SNAP complete: {} adjustments in {}ms",
                    adjustments.size(), elapsed);

            return new SnapResponse(true, adjusted, adjustments, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Snap failed", e);
            BIMLogger.error(TAG, "SNAP failed: {}", e.getMessage());
            return new SnapResponse(false, java.util.List.of(), java.util.List.of(), e.getMessage());
        }
    }

    /**
     * Click-to-fix: force the target bbox to meet the rule's minimum.
     * Returns a new list with the fixed bbox replacing the original.
     */
    private List<DesignBBox> applyClickToFix(List<DesignBBox> bboxes,
                                              String fixRule, String fixBomId, int gridMm) {
        List<DesignBBox> result = new ArrayList<>(bboxes.size());
        for (DesignBBox bb : bboxes) {
            if (!bb.bomId().equals(fixBomId) || !"ROOM".equals(bb.bomType())) {
                result.add(bb);
                continue;
            }

            double w = bb.maxX() - bb.minX();
            double d = bb.maxY() - bb.minY();
            double h = bb.maxZ() - bb.minZ();

            double required = validator.getMinimumForRule(bb.category(), fixRule);
            if (required < 0) {
                BIMLogger.warn(TAG, "CLICK_TO_FIX no rule found: {} for {}", fixRule, bb.category());
                result.add(bb);
                continue;
            }

            // Apply fix based on rule parameter type
            switch (fixRule) {
                case "min_dim_mm" -> {
                    // Expand the smaller dimension to meet minimum
                    if (w <= d && w < required) {
                        w = required;
                    } else if (d < required) {
                        d = required;
                    }
                }
                case "min_width_mm" -> {
                    if (w < required) w = required;
                }
                case "min_height_mm" -> {
                    if (h < required) h = required;
                }
                case "min_area_m2" -> {
                    // Expand width proportionally to meet area
                    double currentArea = (w * d) / 1_000_000.0;
                    if (currentArea < required) {
                        double factor = Math.sqrt(required / currentArea);
                        w *= factor;
                        d *= factor;
                    }
                }
                default -> {
                    BIMLogger.warn(TAG, "CLICK_TO_FIX unknown param: {}", fixRule);
                    result.add(bb);
                    continue;
                }
            }

            // Round to grid after fix
            if (gridMm > 0) {
                w = Math.ceil(w / gridMm) * gridMm;
                d = Math.ceil(d / gridMm) * gridMm;
            }

            BIMLogger.info(TAG, "CLICK_TO_FIX {} rule={} → w={:.0f} d={:.0f}",
                    fixBomId, fixRule, w, d);

            result.add(new DesignBBox(
                    bb.bomId(), bb.name(), bb.bomType(), bb.category(),
                    bb.ifcClass(), bb.storey(), bb.parentBomId(),
                    bb.minX(), bb.minY(), bb.minZ(),
                    bb.minX() + w, bb.minY() + d, bb.minZ() + h));
        }
        return result;
    }

    // Implementing BIM_Designer_SRS.md §17, CORE_SRS.md §3.1
    // Witness: W-JURISDICTION-1, W-INFRA-UI-FILTER
    @Override
    public JurisdictionResponse setJurisdiction(String jurisdiction, List<DesignBBox> bboxes,
                                                 String facilityType) {
        long t0 = System.currentTimeMillis();
        try {
            BIMLogger.info(TAG, "SET_JURISDICTION {} facility={} ({} bboxes)",
                    jurisdiction, facilityType, bboxes.size());

            // Activate validator with facility type if specified
            if (facilityType != null) {
                activateForFacilityType(jurisdiction, facilityType);
            }

            // Re-validate all ROOM/SEGMENT/LEAF bboxes against current rules
            List<RuleVerdict> verdicts = new ArrayList<>();

            if (validator.isActive()) {
                for (DesignBBox bb : bboxes) {
                    if (!"ROOM".equals(bb.bomType())
                            && !"SEGMENT".equals(bb.bomType())
                            && !"LEAF".equals(bb.bomType())) continue;

                    double w = bb.maxX() - bb.minX();
                    double d = bb.maxY() - bb.minY();
                    double h = bb.maxZ() - bb.minZ();

                    PlacementRequest req = new PlacementRequest(
                            bb.category(), bb.ifcClass(), null,
                            w, d, h,
                            bb.minX(), bb.minY(), bb.minZ(),
                            "SNAP", 0, bb.storey());

                    ValidationVerdict verdict = validator.validate(req);
                    verdicts.add(new RuleVerdict(
                            bb.bomId(),
                            verdict.ruleName() != null ? verdict.ruleName() : "ALL_RULES",
                            verdict.result().name(),
                            verdict.actualValue(),
                            verdict.requiredValue()));
                }
            }

            int ruleCount = validator.isActive() ? validator.getRuleCount() : 0;
            long elapsed = System.currentTimeMillis() - t0;
            BIMLogger.info(TAG, "SET_JURISDICTION {} → {} verdicts, {} rules in {}ms",
                    jurisdiction, verdicts.size(), ruleCount, elapsed);

            return new JurisdictionResponse(true, jurisdiction, ruleCount, verdicts, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "setJurisdiction failed", e);
            BIMLogger.error(TAG, "SET_JURISDICTION failed: {}", e.getMessage());
            return new JurisdictionResponse(false, jurisdiction, 0, List.of(), e.getMessage());
        }
    }

    /**
     * Activate the PlacementValidator for a given facility type.
     * Opens a connection to validation.db, activates with the correct FacilityType,
     * and closes the connection (rules are cached in memory).
     *
     * // Implementing CORE_SRS.md §3.1 — Witness: W-INFRA-UI-FILTER
     */
    private void activateForFacilityType(String jurisdiction, String facilityTypeStr) {
        FacilityType ft = FacilityType.BUILDING;
        if (facilityTypeStr != null) {
            try {
                ft = FacilityType.valueOf(facilityTypeStr);
            } catch (IllegalArgumentException e) {
                BIMLogger.warn(TAG, "Unknown facilityType '{}', defaulting to BUILDING", facilityTypeStr);
            }
        }

        try (Connection valConn = DriverManager.getConnection("jdbc:sqlite:library/validation.db")) {
            validator.activate(jurisdiction, ft, valConn);
            BIMLogger.info(TAG, "Activated validator: jurisdiction={}, facilityType={}, rules={}",
                    jurisdiction, ft, validator.getRuleCount());
        } catch (Exception e) {
            BIMLogger.error(TAG, "Failed to activate validator for facilityType {}: {}",
                    facilityTypeStr, e.getMessage());
        }
    }

    // Implementing G4_SRS §2.2 — Witness: W-SAVE-1
    @Override
    public SaveResponse save(String buildingId, java.util.List<DesignBBox> bboxes, String variantLabel) {
        long t0 = System.currentTimeMillis();
        try {
            WorkOutputDAO woDao = getWorkOutputDAO(buildingId);

            // Ensure master order exists (idempotent)
            // Find site dims from the BUILDING bbox
            double siteW = 0, siteD = 0, siteH = 0;
            for (DesignBBox bb : bboxes) {
                if ("BUILDING".equals(bb.bomType())) {
                    siteW = bb.maxX() - bb.minX();
                    siteD = bb.maxY() - bb.minY();
                    siteH = bb.maxZ() - bb.minZ();
                    break;
                }
            }
            woDao.ensureMasterOrder(buildingId, null, siteW, siteD, siteH);

            // Save as new sub-order + W_Variant
            String variantId = woDao.save(buildingId, bboxes, variantLabel);
            String outputPath = WorkOutputDAO.dbPathFor(buildingId);

            long elapsed = System.currentTimeMillis() - t0;
            BIMLogger.info(TAG, "SAVE {} → {} ({} bboxes, label={}) in {}ms",
                    buildingId, variantId, bboxes.size(), variantLabel, elapsed);

            return new SaveResponse(true, variantId, outputPath, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Save failed", e);
            BIMLogger.error(TAG, "SAVE failed for {}: {}", buildingId, e.getMessage());
            return new SaveResponse(false, null, null, e.getMessage());
        }
    }

    // Implementing G4_SRS §2.3 — Witness: W-RECALL-1
    @Override
    public RecallResponse recall(String buildingId, String variantId) {
        try {
            WorkOutputDAO woDao = getWorkOutputDAO(buildingId);
            List<DesignBBox> bboxes = woDao.recall(variantId);

            if (bboxes.isEmpty()) {
                BIMLogger.warn(TAG, "RECALL {} variant={} — no lines found",
                        buildingId, variantId);
                return new RecallResponse(false, java.util.List.of(), null,
                        "Variant not found or empty: " + variantId);
            }

            // Find label from variants list
            String label = null;
            for (VariantInfo vi : woDao.listVariants(buildingId)) {
                if (vi.variantId().equals(variantId)) {
                    label = vi.label();
                    break;
                }
            }

            BIMLogger.info(TAG, "RECALL {} variant={} → {} bboxes (label={})",
                    buildingId, variantId, bboxes.size(), label);

            return new RecallResponse(true, bboxes, label, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Recall failed", e);
            BIMLogger.error(TAG, "RECALL failed for {}: {}", buildingId, e.getMessage());
            return new RecallResponse(false, java.util.List.of(), null, e.getMessage());
        }
    }

    @Override
    public java.util.List<VariantInfo> listVariants(String buildingId) {
        try {
            WorkOutputDAO woDao = getWorkOutputDAO(buildingId);
            List<VariantInfo> variants = woDao.listVariants(buildingId);
            BIMLogger.info(TAG, "LIST_VARIANTS {} → {} variants", buildingId, variants.size());
            return variants;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "listVariants failed", e);
            BIMLogger.error(TAG, "LIST_VARIANTS failed for {}: {}", buildingId, e.getMessage());
            return java.util.List.of();
        }
    }

    @Override
    public PromoteResponse promote(PromoteRequest request) {
        try {
            // TODO: Governance gate — check dangles, validate, write m_bom + m_bom_line.
            // For now, return stub with dangle detection.
            LOG.info(() -> String.format("PROMOTE %s by %s (%d bboxes) (stub)",
                    request.buildingId(), request.owner(), request.bboxes().size()));

            // Stub: no dangles, report success
            return new PromoteResponse(true, request.buildingId(),
                    request.bboxes().size(), java.util.List.of(), null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Promote failed", e);
            return new PromoteResponse(false, null, 0, java.util.List.of(), e.getMessage());
        }
    }

    // ── BOM Chooser (§17.18) ──────────────────────────────────────────

    // Implementing BIM_Designer.md §17.18 — Witness: W-BROWSE-1
    @Override
    public BrowseItemsResponse browseItems(BrowseItemsRequest request) {
        long t0 = System.currentTimeMillis();
        try {
            int limit = request.limit() > 0 ? request.limit() : 20;
            int offset = Math.max(0, request.offset());

            BIMLogger.info(TAG, "BROWSE search={} category={} building={} container={}x{}x{} offset={} limit={}",
                    request.search(), request.category(), request.buildingType(),
                    request.containerWidthMm(), request.containerDepthMm(), request.containerHeightMm(),
                    offset, limit);

            // Query products
            var productRows = dao.browseProducts(
                    request.search(), request.category(), request.buildingType(),
                    offset, limit);

            // Total count for pagination
            int totalCount = dao.countProducts(
                    request.search(), request.category(), request.buildingType());

            // Compute fit status per item
            List<BrowseItem> items = new ArrayList<>(productRows.size());
            for (var row : productRows) {
                String fitStatus = computeFitStatus(
                        row.widthMm(), row.depthMm(), row.heightMm(),
                        request.containerWidthMm(), request.containerDepthMm(),
                        request.containerHeightMm());

                items.add(new BrowseItem(
                        row.productId(),
                        row.productId(),  // name = productId for now
                        row.productType(),
                        row.widthMm(), row.depthMm(), row.heightMm(),
                        row.ifcClass(),
                        fitStatus
                ));
            }

            // Category counts with fits breakdown
            var catRows = dao.categoryCounts(request.search(), request.buildingType());
            List<CategoryCount> categories = new ArrayList<>(catRows.size());
            for (var cat : catRows) {
                // Count how many items in this category fit the container
                int fitsCount = countFitting(request.search(), cat.productType(),
                        request.buildingType(),
                        request.containerWidthMm(), request.containerDepthMm(),
                        request.containerHeightMm());
                categories.add(new CategoryCount(cat.productType(), cat.count(), fitsCount));
            }

            long elapsed = System.currentTimeMillis() - t0;
            BIMLogger.info(TAG, "BROWSE → {} items (total={}, {} categories) in {}ms",
                    items.size(), totalCount, categories.size(), elapsed);

            return new BrowseItemsResponse(true, items, totalCount, categories, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "browseItems failed", e);
            BIMLogger.error(TAG, "BROWSE failed: {}", e.getMessage());
            return new BrowseItemsResponse(false, List.of(), 0, List.of(), e.getMessage());
        }
    }

    /**
     * Container fit check per §17.18.3.
     * Items that don't fit are shown (not hidden) — fit status is information, not a gate.
     */
    public static String computeFitStatus(double itemW, double itemD, double itemH,
            double contW, double contD, double contH) {
        if (contW <= 0 || contD <= 0 || contH <= 0) return "FITS"; // no container = no check
        if (itemW > contW) return "TOO_WIDE";
        if (itemD > contD) return "TOO_DEEP";
        if (itemH > contH) return "TOO_TALL";
        double minClearance = Math.min(contW - itemW, Math.min(contD - itemD, contH - itemH));
        if (minClearance < 100) return "TIGHT";
        return "FITS";
    }

    /** Count fitting products in a category — queries all items and checks AABB. */
    private int countFitting(String search, String category, String buildingType,
            double contW, double contD, double contH) throws Exception {
        if (contW <= 0 || contD <= 0 || contH <= 0) {
            // No container specified — all items "fit"
            return dao.countProducts(search, category, buildingType);
        }
        // Query all items in category (no pagination) to check fit
        var rows = dao.browseProducts(search, category, buildingType, 0, Integer.MAX_VALUE);
        int fits = 0;
        for (var row : rows) {
            if (row.widthMm() <= contW && row.depthMm() <= contD && row.heightMm() <= contH) {
                fits++;
            }
        }
        return fits;
    }

    // ── Find Similar (§25.3 — JEPA-inspired embedding similarity) ──

    // Implementing BIM_Designer_SRS.md §25.3 — Witness: W-EMB-SIM-1
    @Override
    public FindSimilarResponse findSimilar(FindSimilarRequest request) {
        long t0 = System.currentTimeMillis();
        try {
            int limit = request.limit() > 0 ? request.limit() : 10;

            BIMLogger.info(TAG, "FIND_SIMILAR product={} container={}x{}x{} limit={}",
                    request.productId(),
                    request.containerWidthMm(), request.containerDepthMm(),
                    request.containerHeightMm(), limit);

            var similarRows = dao.findSimilar(request.productId(), limit,
                    request.containerWidthMm(), request.containerDepthMm(),
                    request.containerHeightMm());

            List<SimilarItem> items = new ArrayList<>(similarRows.size());
            for (var row : similarRows) {
                var p = row.product();
                String fitStatus = computeFitStatus(
                        p.widthMm(), p.depthMm(), p.heightMm(),
                        request.containerWidthMm(), request.containerDepthMm(),
                        request.containerHeightMm());

                items.add(new SimilarItem(
                        p.productId(), p.productId(), p.productType(),
                        p.widthMm(), p.depthMm(), p.heightMm(),
                        p.ifcClass(), fitStatus,
                        row.similarity()));
            }

            long elapsed = System.currentTimeMillis() - t0;
            BIMLogger.info(TAG, "FIND_SIMILAR → {} items in {}ms",
                    items.size(), elapsed);

            return new FindSimilarResponse(true, items, request.productId(), null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "findSimilar failed", e);
            BIMLogger.error(TAG, "FIND_SIMILAR failed: {}", e.getMessage());
            return new FindSimilarResponse(false, List.of(), request.productId(), e.getMessage());
        }
    }

    // ── Place Item + Layout Editing (§15, §16) ─────────────────────

    // Implementing BIM_Designer_SRS.md §15 — Witness: W-PLACE-1
    @Override
    public PlaceItemResponse placeItem(PlaceItemRequest request) {
        long t0 = System.currentTimeMillis();
        try {
            BIMLogger.info(TAG, "PLACE_ITEM product={} room={} offset=({},{},{})",
                    request.productId(), request.roomBomId(),
                    request.offsetXMm(), request.offsetYMm(), request.offsetZMm());

            // 1. Look up product dimensions
            var product = dao.getProduct(request.productId());
            if (product == null) {
                BIMLogger.warn(TAG, "PLACE_ITEM product not found: {}", request.productId());
                return new PlaceItemResponse(false, 0, null,
                        "Product not found: " + request.productId());
            }

            // 2. Find the target room bbox
            DesignBBox roomBbox = null;
            if (request.currentBboxes() != null) {
                for (DesignBBox bb : request.currentBboxes()) {
                    if (bb.bomId().equals(request.roomBomId())) {
                        roomBbox = bb;
                        break;
                    }
                }
            }
            if (roomBbox == null) {
                BIMLogger.warn(TAG, "PLACE_ITEM room not found: {}", request.roomBomId());
                return new PlaceItemResponse(false, 0, null,
                        "Room not found in bboxes: " + request.roomBomId());
            }

            // 3. Compute item position: room origin + offset
            double itemMinX = roomBbox.minX() + request.offsetXMm();
            double itemMinY = roomBbox.minY() + request.offsetYMm();
            double itemMinZ = roomBbox.minZ() + request.offsetZMm();
            double itemMaxX = itemMinX + product.widthMm();
            double itemMaxY = itemMinY + product.depthMm();
            double itemMaxZ = itemMinZ + product.heightMm();

            // 4. Create new DesignBBox for the placed item
            String itemBomId = "ITEM_" + request.productId() + "_" +
                    System.currentTimeMillis() % 100000;
            DesignBBox itemBbox = new DesignBBox(
                    itemBomId,
                    request.productId(),
                    "ITEM",
                    product.productType(),
                    product.ifcClass(),
                    roomBbox.storey(),
                    request.roomBomId(),
                    itemMinX, itemMinY, itemMinZ,
                    itemMaxX, itemMaxY, itemMaxZ);

            long elapsed = System.currentTimeMillis() - t0;
            BIMLogger.info(TAG, "PLACE_ITEM → {} at ({},{},{}) size {}x{}x{} in {}ms",
                    itemBomId, itemMinX, itemMinY, itemMinZ,
                    product.widthMm(), product.depthMm(), product.heightMm(), elapsed);

            // orderLineId = 0 for now (not persisted to work_output.db yet)
            return new PlaceItemResponse(true, 0, itemBbox, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "placeItem failed", e);
            BIMLogger.error(TAG, "PLACE_ITEM failed: {}", e.getMessage());
            return new PlaceItemResponse(false, 0, null, e.getMessage());
        }
    }

    // Implementing BIM_Designer_SRS.md §16 — Witness: W-LAYOUT-1
    @Override
    public LayoutResponse addRoom(String buildingId, String category, String storey) {
        long t0 = System.currentTimeMillis();
        try {
            BIMLogger.info(TAG, "ADD_ROOM building={} category={} storey={}",
                    buildingId, category, storey);

            // Retrieve current bboxes from work_output.db or create minimal set
            List<DesignBBox> currentBboxes = getCurrentBboxes(buildingId);
            if (currentBboxes.isEmpty()) {
                BIMLogger.warn(TAG, "ADD_ROOM no current bboxes for {}", buildingId);
                return new LayoutResponse(false, List.of(), 0,
                        "No current layout for building: " + buildingId);
            }

            // Count current rooms by category to build CreateNewRequest params
            int numBed = 0, numBath = 0;
            boolean hasLiving = false, hasKitchen = false;
            String targetStorey = storey != null ? storey : "GF";
            double siteW = 0, siteD = 0;
            int storeyCount = 0;
            String buildingName = buildingId;

            for (DesignBBox bb : currentBboxes) {
                if ("BUILDING".equals(bb.bomType())) {
                    siteW = bb.maxX() - bb.minX();
                    siteD = bb.maxY() - bb.minY();
                    buildingName = bb.name();
                } else if ("FLOOR".equals(bb.bomType())) {
                    storeyCount++;
                } else if ("ROOM".equals(bb.bomType())) {
                    switch (bb.category()) {
                        case "BEDROOM"  -> numBed++;
                        case "BATHROOM" -> numBath++;
                        case "LIVING"   -> hasLiving = true;
                        case "KITCHEN"  -> hasKitchen = true;
                    }
                }
            }

            // Increment the target category
            switch (category) {
                case "BEDROOM"  -> numBed++;
                case "BATHROOM" -> numBath++;
                // LIVING and KITCHEN are fixed at 1 each in the layout generator
            }

            // Divide counts by storey count to get per-storey values
            int effectiveStoreys = Math.max(1, storeyCount);
            int perStoreyBed = (int) Math.ceil((double) numBed / effectiveStoreys);
            int perStoreyBath = (int) Math.ceil((double) numBath / effectiveStoreys);

            // Regenerate layout
            CreateNewRequest regen = new CreateNewRequest(
                    buildingName, "REGEN", "MY",
                    siteW, siteD,
                    perStoreyBed, perStoreyBath, effectiveStoreys);
            var newBboxes = RoomLayoutGenerator.generate(regen);

            int roomCount = (int) newBboxes.stream()
                    .filter(b -> "ROOM".equals(b.bomType())).count();

            long elapsed = System.currentTimeMillis() - t0;
            BIMLogger.info(TAG, "ADD_ROOM → {} bboxes ({} rooms) in {}ms",
                    newBboxes.size(), roomCount, elapsed);

            return new LayoutResponse(true, newBboxes, roomCount, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "addRoom failed", e);
            BIMLogger.error(TAG, "ADD_ROOM failed: {}", e.getMessage());
            return new LayoutResponse(false, List.of(), 0, e.getMessage());
        }
    }

    // Implementing BIM_Designer_SRS.md §16 — Witness: W-LAYOUT-2
    @Override
    public LayoutResponse removeRoom(String buildingId, String roomBomId) {
        long t0 = System.currentTimeMillis();
        try {
            BIMLogger.info(TAG, "REMOVE_ROOM building={} room={}", buildingId, roomBomId);

            List<DesignBBox> currentBboxes = getCurrentBboxes(buildingId);
            if (currentBboxes.isEmpty()) {
                BIMLogger.warn(TAG, "REMOVE_ROOM no current bboxes for {}", buildingId);
                return new LayoutResponse(false, List.of(), 0,
                        "No current layout for building: " + buildingId);
            }

            // Find the room to remove and verify it exists
            DesignBBox targetRoom = null;
            for (DesignBBox bb : currentBboxes) {
                if (bb.bomId().equals(roomBomId) && "ROOM".equals(bb.bomType())) {
                    targetRoom = bb;
                    break;
                }
            }
            if (targetRoom == null) {
                BIMLogger.warn(TAG, "REMOVE_ROOM room not found: {}", roomBomId);
                return new LayoutResponse(false, currentBboxes,
                        (int) currentBboxes.stream().filter(b -> "ROOM".equals(b.bomType())).count(),
                        "Room not found: " + roomBomId);
            }

            // Recount rooms without the target
            int numBed = 0, numBath = 0;
            double siteW = 0, siteD = 0;
            int storeyCount = 0;
            String buildingName = buildingId;

            for (DesignBBox bb : currentBboxes) {
                if ("BUILDING".equals(bb.bomType())) {
                    siteW = bb.maxX() - bb.minX();
                    siteD = bb.maxY() - bb.minY();
                    buildingName = bb.name();
                } else if ("FLOOR".equals(bb.bomType())) {
                    storeyCount++;
                } else if ("ROOM".equals(bb.bomType()) && !bb.bomId().equals(roomBomId)) {
                    switch (bb.category()) {
                        case "BEDROOM"  -> numBed++;
                        case "BATHROOM" -> numBath++;
                    }
                }
            }

            int effectiveStoreys = Math.max(1, storeyCount);
            int perStoreyBed = (int) Math.ceil((double) numBed / effectiveStoreys);
            int perStoreyBath = (int) Math.ceil((double) numBath / effectiveStoreys);

            // Regenerate layout with decremented counts
            CreateNewRequest regen = new CreateNewRequest(
                    buildingName, "REGEN", "MY",
                    siteW, siteD,
                    perStoreyBed, perStoreyBath, effectiveStoreys);
            var newBboxes = RoomLayoutGenerator.generate(regen);

            int roomCount = (int) newBboxes.stream()
                    .filter(b -> "ROOM".equals(b.bomType())).count();

            long elapsed = System.currentTimeMillis() - t0;
            BIMLogger.info(TAG, "REMOVE_ROOM → {} bboxes ({} rooms) in {}ms",
                    newBboxes.size(), roomCount, elapsed);

            return new LayoutResponse(true, newBboxes, roomCount, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "removeRoom failed", e);
            BIMLogger.error(TAG, "REMOVE_ROOM failed: {}", e.getMessage());
            return new LayoutResponse(false, List.of(), 0, e.getMessage());
        }
    }

    // Implementing BIM_Designer_SRS.md §16 — Witness: W-LAYOUT-3
    @Override
    public LayoutResponse addStorey(String buildingId) {
        long t0 = System.currentTimeMillis();
        try {
            BIMLogger.info(TAG, "ADD_STOREY building={}", buildingId);

            List<DesignBBox> currentBboxes = getCurrentBboxes(buildingId);
            if (currentBboxes.isEmpty()) {
                BIMLogger.warn(TAG, "ADD_STOREY no current bboxes for {}", buildingId);
                return new LayoutResponse(false, List.of(), 0,
                        "No current layout for building: " + buildingId);
            }

            // Parse current layout state
            int numBed = 0, numBath = 0;
            double siteW = 0, siteD = 0;
            int storeyCount = 0;
            String buildingName = buildingId;

            for (DesignBBox bb : currentBboxes) {
                if ("BUILDING".equals(bb.bomType())) {
                    siteW = bb.maxX() - bb.minX();
                    siteD = bb.maxY() - bb.minY();
                    buildingName = bb.name();
                } else if ("FLOOR".equals(bb.bomType())) {
                    storeyCount++;
                } else if ("ROOM".equals(bb.bomType()) && "GF".equals(bb.storey())) {
                    // Count GF rooms only (will be cloned for new storey)
                    switch (bb.category()) {
                        case "BEDROOM"  -> numBed++;
                        case "BATHROOM" -> numBath++;
                    }
                }
            }

            int newStoreyCount = storeyCount + 1;

            // Regenerate with one more storey
            CreateNewRequest regen = new CreateNewRequest(
                    buildingName, "REGEN", "MY",
                    siteW, siteD,
                    numBed, numBath, newStoreyCount);
            var newBboxes = RoomLayoutGenerator.generate(regen);

            int roomCount = (int) newBboxes.stream()
                    .filter(b -> "ROOM".equals(b.bomType())).count();

            long elapsed = System.currentTimeMillis() - t0;
            BIMLogger.info(TAG, "ADD_STOREY → {} storeys, {} bboxes ({} rooms) in {}ms",
                    newStoreyCount, newBboxes.size(), roomCount, elapsed);

            return new LayoutResponse(true, newBboxes, roomCount, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "addStorey failed", e);
            BIMLogger.error(TAG, "ADD_STOREY failed: {}", e.getMessage());
            return new LayoutResponse(false, List.of(), 0, e.getMessage());
        }
    }

    /**
     * Get current bboxes for a building — tries work_output.db latest variant,
     * falls back to empty list. Used by layout editing operations.
     */
    private List<DesignBBox> getCurrentBboxes(String buildingId) {
        try {
            WorkOutputDAO woDao = getWorkOutputDAO(buildingId);
            var variants = woDao.listVariants(buildingId);
            if (!variants.isEmpty()) {
                String latestVariantId = variants.get(0).variantId();
                return woDao.recall(latestVariantId);
            }
        } catch (Exception e) {
            BIMLogger.warn(TAG, "getCurrentBboxes fallback for {}: {}", buildingId, e.getMessage());
        }
        return List.of();
    }

    // ── Approve gate (§18) ───────────────────────────────────────────

    // Implementing BIM_Designer_SRS.md §18.1 — Witness: W-APPROVE-1
    @Override
    public ApproveResponse approve(String buildingId) {
        long t0 = System.currentTimeMillis();
        try {
            BIMLogger.info(TAG, "APPROVE building={}", buildingId);

            // 1. Get latest bboxes from work_output.db
            List<DesignBBox> bboxes = getCurrentBboxes(buildingId);
            if (bboxes.isEmpty()) {
                BIMLogger.warn(TAG, "APPROVE no bboxes for {}", buildingId);
                return new ApproveResponse(false, "NO_DATA", 0, 0,
                        List.of(), List.of(), "No saved design for building: " + buildingId);
            }

            // 2. For each ROOM bbox: create PlacementRequest
            List<PlacementRequest> requests = new ArrayList<>();
            List<DesignBBox> roomBboxes = new ArrayList<>();
            for (DesignBBox bb : bboxes) {
                if (!"ROOM".equals(bb.bomType())) continue;
                double w = bb.maxX() - bb.minX();
                double d = bb.maxY() - bb.minY();
                double h = bb.maxZ() - bb.minZ();
                requests.add(new PlacementRequest(
                        bb.category(), bb.ifcClass(), null,
                        w, d, h,
                        bb.minX(), bb.minY(), bb.minZ(),
                        "APPROVE", 0, bb.storey()));
                roomBboxes.add(bb);
            }

            // 3. Collect all RuleResults via InferenceEngine
            List<com.bim.designer.validation.InferenceEngine.RuleResult> ruleResults;
            if (validator instanceof PlacementValidatorImpl pvi && validator.isActive()) {
                ruleResults = pvi.validateAll(requests);
            } else {
                // No active validator — all pass trivially
                ruleResults = List.of();
            }

            // 4. Tally results and collect blockers
            int passed = 0;
            int total = ruleResults.size();
            List<Blocker> blockers = new ArrayList<>();

            for (var result : ruleResults) {
                if (result.isPassed()) {
                    passed++;
                } else if (result.isBlocked()) {
                    // Find the corresponding room bbox by scanning roomBboxes
                    String bomId = roomBboxes.isEmpty() ? "unknown" : roomBboxes.get(0).bomId();
                    blockers.add(new Blocker(
                            bomId, result.ruleName(),
                            result.actualValue(), result.requiredValue(),
                            String.format("%s: %.1f < %.1f (%s)",
                                    result.ruleName(), result.actualValue(),
                                    result.requiredValue(), result.standardRef())));
                }
                // SKIP results don't count as passed or blocked
            }

            // 5. Check for dangles: ITEM bboxes referencing non-existent products
            List<String> dangles = new ArrayList<>();
            for (DesignBBox bb : bboxes) {
                if ("ITEM".equals(bb.bomType())) {
                    try {
                        var product = dao.getProduct(bb.name());
                        if (product == null) {
                            dangles.add(bb.bomId() + " -> " + bb.name());
                        }
                    } catch (Exception e) {
                        dangles.add(bb.bomId() + " -> " + bb.name());
                    }
                }
            }

            // 6. Determine status
            String status;
            boolean success;
            if (blockers.isEmpty() && dangles.isEmpty()) {
                status = "AP";
                success = true;
            } else {
                status = "BLOCKED";
                success = false;
            }

            long elapsed = System.currentTimeMillis() - t0;
            BIMLogger.info(TAG, "APPROVE {} → status={}, passed={}/{}, dangles={}, blockers={} in {}ms",
                    buildingId, status, passed, total, dangles.size(), blockers.size(), elapsed);

            return new ApproveResponse(success, status, passed, total,
                    dangles, blockers, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Approve failed", e);
            BIMLogger.error(TAG, "APPROVE failed for {}: {}", buildingId, e.getMessage());
            return new ApproveResponse(false, "ERROR", 0, 0,
                    List.of(), List.of(), e.getMessage());
        }
    }

    // ── Assembly Builder (§18.2 Principle 4, G-7) ────────────────────

    /**
     * Lazily open component_library.db and create AssemblyBuilderService.
     */
    private AssemblyBuilderService getAssemblyService() throws Exception {
        if (assemblyService == null) {
            synchronized (this) {
                if (assemblyService == null) {
                    String libPath = "library/component_library.db";
                    Connection libConn = DriverManager.getConnection("jdbc:sqlite:" + libPath);
                    assemblyService = new AssemblyBuilderService(libConn);
                    BIMLogger.info(TAG, "Opened component_library.db for assembly builder");
                }
            }
        }
        return assemblyService;
    }

    // Implementing ASSEMBLY_BUILDER_SRS.md §3.1 — Witness: W-ASM-LIST-1
    @Override
    public AssemblyListResponse listAssemblyTemplates(String category) {
        try {
            return getAssemblyService().listAssemblyTemplates(category);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "listAssemblyTemplates failed", e);
            return new AssemblyListResponse(false, List.of(), e.getMessage());
        }
    }

    // Implementing ASSEMBLY_BUILDER_SRS.md §3.1 — Witness: W-ASM-DETAIL-1
    @Override
    public AssemblyDetailResponse getAssemblyDetail(String layerSetName) {
        try {
            return getAssemblyService().getAssemblyDetail(layerSetName);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "getAssemblyDetail failed", e);
            return new AssemblyDetailResponse(false, layerSetName, null,
                    List.of(), 0, 0, null, e.getMessage());
        }
    }

    // Implementing ASSEMBLY_BUILDER_SRS.md §3.1 — Witness: W-ASM-BROWSE-1
    @Override
    public BrowseAssemblyLayersResponse browseAssemblyLayers(BrowseAssemblyLayersRequest request) {
        try {
            return getAssemblyService().browseAssemblyLayers(request);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "browseAssemblyLayers failed", e);
            return new BrowseAssemblyLayersResponse(false, request.layerSetName(),
                    request.layerSequence(), List.of(), e.getMessage());
        }
    }

    // Implementing ASSEMBLY_BUILDER_SRS.md §3.1 — Witness: W-ASM-SWAP-1
    @Override
    public AssemblyDetailResponse swapLayer(SwapLayerRequest request) {
        try {
            return getAssemblyService().swapLayer(request);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "swapLayer failed", e);
            return new AssemblyDetailResponse(false, request.layerSetName(), null,
                    List.of(), 0, 0, null, e.getMessage());
        }
    }

    // ── Variant comparison (§19) ─────────────────────────────────────

    // Implementing BIM_Designer_SRS.md §19 — Witness: W-COMPARE-1
    @Override
    public CompareVariantsResponse compareVariants(String buildingId, List<String> variantIds) {
        long t0 = System.currentTimeMillis();
        try {
            BIMLogger.info(TAG, "COMPARE_VARIANTS building={} variants={}",
                    buildingId, variantIds.size());

            if (variantIds == null || variantIds.size() < 2) {
                return new CompareVariantsResponse(false, List.of(), List.of(),
                        "At least 2 variant IDs required for comparison");
            }

            WorkOutputDAO woDao = getWorkOutputDAO(buildingId);
            List<VariantStats> stats = new ArrayList<>();
            List<List<DesignBBox>> allBboxes = new ArrayList<>();

            for (String variantId : variantIds) {
                List<DesignBBox> vBboxes = woDao.recall(variantId);
                allBboxes.add(vBboxes);

                // Compute stats
                int roomCount = 0;
                double totalArea = 0;
                for (DesignBBox bb : vBboxes) {
                    if ("ROOM".equals(bb.bomType())) {
                        roomCount++;
                        double w = bb.maxX() - bb.minX();
                        double d = bb.maxY() - bb.minY();
                        totalArea += (w * d) / 1_000_000.0;
                    }
                }

                // Find label
                String label = null;
                for (VariantInfo vi : woDao.listVariants(buildingId)) {
                    if (vi.variantId().equals(variantId)) {
                        label = vi.label();
                        break;
                    }
                }

                // Quick compliance check (if validator is active)
                int rulesPassed = 0;
                String compliance = "UNKNOWN";
                if (validator.isActive() && validator instanceof PlacementValidatorImpl pvi) {
                    List<PlacementRequest> requests = new ArrayList<>();
                    for (DesignBBox bb : vBboxes) {
                        if (!"ROOM".equals(bb.bomType())) continue;
                        double w = bb.maxX() - bb.minX();
                        double d = bb.maxY() - bb.minY();
                        double h = bb.maxZ() - bb.minZ();
                        requests.add(new PlacementRequest(
                                bb.category(), bb.ifcClass(), null,
                                w, d, h, bb.minX(), bb.minY(), bb.minZ(),
                                "COMPARE", 0, bb.storey()));
                    }
                    var results = pvi.validateAll(requests);
                    rulesPassed = (int) results.stream()
                            .filter(com.bim.designer.validation.InferenceEngine.RuleResult::isPassed)
                            .count();
                    boolean anyBlock = results.stream()
                            .anyMatch(com.bim.designer.validation.InferenceEngine.RuleResult::isBlocked);
                    compliance = anyBlock ? "BLOCKED" : "AP";
                }

                stats.add(new VariantStats(variantId, label, roomCount,
                        totalArea, compliance, rulesPassed));
            }

            // Compute field-level diffs between first two variants
            List<FieldDiff> diffs = new ArrayList<>();
            if (allBboxes.size() >= 2) {
                List<DesignBBox> v1 = allBboxes.get(0);
                List<DesignBBox> v2 = allBboxes.get(1);

                // Match rooms by bomId
                java.util.Map<String, DesignBBox> v1Map = new java.util.LinkedHashMap<>();
                for (DesignBBox bb : v1) {
                    if ("ROOM".equals(bb.bomType())) v1Map.put(bb.bomId(), bb);
                }

                for (DesignBBox bb2 : v2) {
                    if (!"ROOM".equals(bb2.bomType())) continue;
                    DesignBBox bb1 = v1Map.get(bb2.bomId());
                    if (bb1 == null) continue;

                    double w1 = bb1.maxX() - bb1.minX();
                    double w2 = bb2.maxX() - bb2.minX();
                    if (Math.abs(w1 - w2) > 0.1) {
                        diffs.add(new FieldDiff(bb1.bomId(), "width", w1, w2));
                    }

                    double d1 = bb1.maxY() - bb1.minY();
                    double d2 = bb2.maxY() - bb2.minY();
                    if (Math.abs(d1 - d2) > 0.1) {
                        diffs.add(new FieldDiff(bb1.bomId(), "depth", d1, d2));
                    }

                    double h1 = bb1.maxZ() - bb1.minZ();
                    double h2 = bb2.maxZ() - bb2.minZ();
                    if (Math.abs(h1 - h2) > 0.1) {
                        diffs.add(new FieldDiff(bb1.bomId(), "height", h1, h2));
                    }
                }
            }

            long elapsed = System.currentTimeMillis() - t0;
            BIMLogger.info(TAG, "COMPARE_VARIANTS → {} variants, {} diffs in {}ms",
                    stats.size(), diffs.size(), elapsed);

            return new CompareVariantsResponse(true, stats, diffs, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "compareVariants failed", e);
            BIMLogger.error(TAG, "COMPARE_VARIANTS failed: {}", e.getMessage());
            return new CompareVariantsResponse(false, List.of(), List.of(), e.getMessage());
        }
    }

    // ── Verb dispatch ───────────────────────────────────────────────

    @Override
    public VerbResponse executeVerb(String buildingId, String verbLine) {
        try {
            // TODO: Wire to VerbRegistry.createDefault().dispatch(VerbContext.ofBom(bomConn), verbLine)
            // For now, return a structured stub.

            // Parse verb keyword (first 1-2 words) from the line
            String verb = extractVerbKeyword(verbLine);

            LOG.info(() -> String.format("VERB %s on %s (stub)", verb, buildingId));

            return new VerbResponse(true, verb,
                    "Stub execution of " + verb + " on " + buildingId, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Verb execution failed", e);
            return new VerbResponse(false, null, null, e.getMessage());
        }
    }

    /**
     * Extracts the BIM COBOL verb keyword from a verb line.
     *
     * <p>Convention: verbs are 1-3 uppercase words (CHECK BOM, SNAP TO GRID).
     * Arguments follow and may also be uppercase (BUILDING_SH, 10000).
     *
     * <p>Known multi-word verb prefixes (from VerbRegistry):
     * CHECK BOM, SNAP TO, COVER WITH, COMPOSE BUILDING, ADD ROOM, etc.
     * This stub uses a known-prefix table; the real dispatch goes through
     * VerbRegistry.dispatch() which does longest-prefix match.
     */
    private static final java.util.Set<String> KNOWN_VERB_PREFIXES = java.util.Set.of(
            "CHECK BOM", "SNAP TO GRID", "SNAP TO", "COVER WITH",
            "COMPOSE BUILDING", "ADD ROOM", "REMOVE ROOM", "RESIZE ROOM",
            "FURNISH ROOM", "STRIP ROOM", "CREATE ROOM", "SET ROTATION",
            "SET TACK", "VARY BUILDING", "CLONE BOM", "EXTRACT AABB",
            "VALIDATE AABB", "PARTITION AABB", "PLACE AT", "PLACE BOM",
            "ROUTE SPRINKLERS", "HELLO WORLD"
    );

    private String extractVerbKeyword(String line) {
        if (line == null || line.isBlank()) return "UNKNOWN";
        String trimmed = line.trim();

        // Try longest match first (3 words, then 2, then 1)
        String[] tokens = trimmed.split("\\s+");
        for (int n = Math.min(3, tokens.length); n >= 1; n--) {
            String candidate = String.join(" ", java.util.Arrays.copyOf(tokens, n));
            if (KNOWN_VERB_PREFIXES.contains(candidate)) return candidate;
        }
        // Fallback: first word
        return tokens[0];
    }
}
