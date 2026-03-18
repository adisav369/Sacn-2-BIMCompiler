package com.bim.designer.api;

import com.bim.designer.compile.ChangeSet;
import com.bim.designer.compile.IncrementalCompiler;
import com.bim.designer.compile.RoomLayoutGenerator;
import com.bim.designer.dao.DesignerDAO;
import com.bim.designer.dao.DesignerDAO.BuildingTypeRow;
import com.bim.designer.dao.DesignerDAO.CategoryRow;

import java.sql.Connection;
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

    private final Connection bomConn;
    private final DesignerDAO dao;
    private final IncrementalCompiler incrementalCompiler;

    /**
     * @param bomConn  JDBC connection to BOM.db (caller owns lifecycle)
     */
    public DesignerAPIImpl(Connection bomConn) {
        this.bomConn = bomConn;
        this.dao = new DesignerDAO(bomConn);
        this.incrementalCompiler = new IncrementalCompiler();
    }

    // ── Compilation ─────────────────────────────────────────────────

    @Override
    public CompileResponse compile(CompileRequest request) {
        long t0 = System.currentTimeMillis();
        try {
            // TODO: Wire to CompilationPipeline.run(BuildingRegistry.loadById(request.buildingId()))
            // For now, return a structured stub that proves the protocol works.

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
            LOG.log(Level.WARNING, "Compile failed", e);
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

    @Override
    public List<BuildingTypeInfo> listBuildingTypes() {
        try {
            return dao.listBuildingTypes().stream()
                    .map(r -> new BuildingTypeInfo(
                            r.docTypeId(), r.name(), r.docSubType(),
                            r.expectedElements(),
                            r.aabbWidthMm(), r.aabbDepthMm(), r.aabbHeightMm()
                    ))
                    .toList();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "listBuildingTypes failed", e);
            return List.of();
        }
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

    // ── Design Mode persistence (§17.10 stubs) ─────────────────────

    @Override
    public SnapResponse snap(java.util.List<DesignBBox> bboxes, String jurisdiction, int gridMm) {
        long t0 = System.currentTimeMillis();
        try {
            // TODO: Wire to PlacementValidator via BlenderBridge.
            // For now, return bboxes unchanged with empty adjustments.
            LOG.info(() -> String.format("SNAP %d bboxes, jurisdiction=%s, grid=%dmm (stub)",
                    bboxes.size(), jurisdiction, gridMm));

            long elapsed = System.currentTimeMillis() - t0;
            return new SnapResponse(true, bboxes, java.util.List.of(), null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Snap failed", e);
            return new SnapResponse(false, java.util.List.of(), java.util.List.of(), e.getMessage());
        }
    }

    @Override
    public SaveResponse save(String buildingId, java.util.List<DesignBBox> bboxes, String variantLabel) {
        long t0 = System.currentTimeMillis();
        try {
            // TODO: Write OrderLine + ASI to work_output.db.
            // Init: YAML/Order/ASI definitions written into output.db header.
            // For now, return a stub variant ID.
            String variantId = "v-" + System.currentTimeMillis();
            String outputPath = "library/work_" + buildingId.toLowerCase() + ".db";

            LOG.info(() -> String.format("SAVE %s → %s (%d bboxes, label=%s) (stub)",
                    buildingId, variantId, bboxes.size(), variantLabel));

            return new SaveResponse(true, variantId, outputPath, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Save failed", e);
            return new SaveResponse(false, null, null, e.getMessage());
        }
    }

    @Override
    public RecallResponse recall(String buildingId, String variantId) {
        try {
            // TODO: Read OrderLine + ASI from work_output.db variant.
            // For now, return empty — no variants exist yet.
            LOG.info(() -> String.format("RECALL %s variant=%s (stub)", buildingId, variantId));

            return new RecallResponse(false, java.util.List.of(), null,
                    "No variants saved yet (stub)");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Recall failed", e);
            return new RecallResponse(false, java.util.List.of(), null, e.getMessage());
        }
    }

    @Override
    public java.util.List<VariantInfo> listVariants(String buildingId) {
        try {
            // TODO: Query work_output.db for saved variants.
            LOG.info(() -> String.format("LIST_VARIANTS %s (stub)", buildingId));
            return java.util.List.of();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "listVariants failed", e);
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
