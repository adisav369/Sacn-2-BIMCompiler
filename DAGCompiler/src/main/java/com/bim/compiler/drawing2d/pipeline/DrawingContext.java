package com.bim.compiler.drawing2d.pipeline;

import com.bim.compiler.drawing2d.model.*;

import java.nio.file.Path;
import java.util.*;

/**
 * Shared mutable state carrier for the 2D drawing pipeline.
 *
 * Mirrors {@link com.bim.compiler.dsl.CompilationContext} — immutable
 * inputs set at construction, mutable stage outputs set by each stage.
 *
 * The forensic log accumulates §STAGE_NAME lines proving every decision.
 * Implementing 2D_ARCHITECTURAL_LAYOUT.md §1 R7 — code logs forensically.
 */
public class DrawingContext {

    // ── Immutable input ─────────────────────────────────────────

    private final Path dbPath;
    private final Path metaDbPath;
    private final Path templatePath;
    private final String profileId;

    // ── Stage 1 output: ScopeStage ──────────────────────────────

    private DrawingScope scope;
    private PageManifest manifest;

    // ── Stage 2 output: LoadStage ───────────────────────────────

    private Map<String, List<Element>> elementsByStorey;
    private List<Element> allElements;

    // ── Stage 3 output: GridStage ───────────────────────────────

    private List<GridLine> grids;
    private List<DimString> dimensions;

    // ── Stage 4 output: LevelStage ──────────────────────────────

    private List<Level> levels;

    // ── Stage 6 output: RenderStage ─────────────────────────────

    private final Map<String, Path> renderedFiles = new LinkedHashMap<>();

    // ── Forensic log ────────────────────────────────────────────

    private final List<String> logLines = new ArrayList<>();
    private final List<String> verdicts = new ArrayList<>();

    // ── Construction ────────────────────────────────────────────

    public DrawingContext(Path dbPath, Path metaDbPath, Path templatePath,
                          String profileId) {
        this.dbPath = Objects.requireNonNull(dbPath, "dbPath");
        this.metaDbPath = Objects.requireNonNull(metaDbPath, "metaDbPath");
        this.templatePath = Objects.requireNonNull(templatePath, "templatePath");
        this.profileId = Objects.requireNonNull(profileId, "profileId");
    }

    // ── Logging ─────────────────────────────────────────────────

    /** Append a forensic log line: §stageName message */
    public void log(String stageName, String message) {
        String line = "§" + stageName + " " + message;
        logLines.add(line);
    }

    /** Record a pass/fail verdict for the summary. */
    public void verdict(String pageKey, String result) {
        verdicts.add("[" + result + "] " + pageKey);
    }

    // ── Immutable input accessors ───────────────────────────────

    public Path dbPath()       { return dbPath; }
    public Path metaDbPath()   { return metaDbPath; }
    public Path templatePath() { return templatePath; }
    public String profileId()  { return profileId; }

    // ── Stage output accessors ──────────────────────────────────

    public DrawingScope scope()       { return scope; }
    public PageManifest manifest()    { return manifest; }

    public void setScope(DrawingScope scope)       { this.scope = scope; }
    public void setManifest(PageManifest manifest) { this.manifest = manifest; }

    public Map<String, List<Element>> elementsByStorey() { return elementsByStorey; }
    public List<Element> allElements()                   { return allElements; }

    public void setElementsByStorey(Map<String, List<Element>> m) { this.elementsByStorey = m; }
    public void setAllElements(List<Element> all)                 { this.allElements = all; }

    public List<GridLine> grids()       { return grids; }
    public List<DimString> dimensions() { return dimensions; }

    public void setGrids(List<GridLine> grids)          { this.grids = grids; }
    public void setDimensions(List<DimString> dims)     { this.dimensions = dims; }

    public List<Level> levels()                         { return levels; }
    public void setLevels(List<Level> levels)           { this.levels = levels; }

    public Map<String, Path> renderedFiles()            { return renderedFiles; }

    // ── Log output ──────────────────────────────────────────────

    public List<String> logLines()  { return Collections.unmodifiableList(logLines); }
    public List<String> verdicts()  { return Collections.unmodifiableList(verdicts); }

    /** Pipeline result summary. */
    public record PipelineResult(
        String dbName,
        int pageCount,
        int passCount,
        int failCount,
        List<String> verdicts,
        List<String> logLines
    ) {}

    /** Build the result after all stages complete. */
    public PipelineResult toResult() {
        long pass = verdicts.stream().filter(v -> v.startsWith("[PASS]")).count();
        long fail = verdicts.stream().filter(v -> v.startsWith("[FAIL]")).count();
        return new PipelineResult(
            dbPath.getFileName().toString(),
            manifest != null ? manifest.size() : 0,
            (int) pass, (int) fail,
            List.copyOf(verdicts),
            List.copyOf(logLines)
        );
    }
}
