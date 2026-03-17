package com.bim.designer.api;

import com.bim.designer.compile.ChangeSet;
import com.bim.designer.compile.IncrementalCompiler;
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
    public CompileResponse createNew(CreateNewRequest request) {
        long t0 = System.currentTimeMillis();
        try {
            // TODO: Wire to real BOM generation pipeline.
            // For now, return a structured stub that lets the UI wire up immediately.

            LOG.info(() -> String.format(
                    "CREATE_NEW name=%s type=%s jurisdiction=%s site=%.0fx%.0f rooms=%dBR+%dBT storeys=%d",
                    request.buildingName(), request.buildingType(), request.jurisdiction(),
                    request.siteWidthMm(), request.siteDepthMm(),
                    request.numBedrooms(), request.numBathrooms(), request.storeys()));

            // Estimate element count: per room = 4 walls + 1 floor + 1 door + 1 window = 7
            int totalRooms = (request.numBedrooms() + request.numBathrooms()) * request.storeys();
            // Add living + kitchen per storey
            totalRooms += 2 * request.storeys();
            int estimatedElements = totalRooms * 7;

            long elapsed = System.currentTimeMillis() - t0;
            String outputPath = "library/DM_BOM.db";

            return CompileResponse.success(
                    estimatedElements,
                    elapsed,
                    outputPath,
                    "stub-digest-" + request.buildingName().replaceAll("\\s+", "_")
            );
        } catch (Exception e) {
            LOG.log(Level.WARNING, "createNew failed", e);
            return CompileResponse.failure(e.getMessage());
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
