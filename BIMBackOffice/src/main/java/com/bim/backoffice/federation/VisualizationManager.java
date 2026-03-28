package com.bim.backoffice.federation;

import com.bim.orm.BIMLogger;

import java.util.*;

/**
 * Visualization Manager — ported from visualization_manager.py.
 *
 * <p>The Python addon manages three Blender collection layers (BBOXES,
 * SEMANTICS, MATERIALS) with instant mode switching. This Java port
 * tracks mode state and provides color maps per mode for the WebUI.
 *
 * <p>Architecture (matching Python):
 * <pre>
 *   Federation_Viewport
 *     ├── Federation_BBoxes     (Layer 1 — wireframe)
 *     │   ├── Discipline_ARC
 *     │   ├── Discipline_STR
 *     │   └── ... (per discipline)
 *     ├── Federation_Semantics  (Layer 2 — shapes)
 *     │   └── ... (per discipline)
 *     └── Federation_Materials  (Layer 3 — detailed)
 *         └── ... (per discipline)
 * </pre>
 *
 * // Porting visualization_manager.py — Witness: W-VIS-MGR-1
 */
public class VisualizationManager {

    private static final String TAG = "VisualizationManager";

    private VisualizationMode currentMode = VisualizationMode.BBOXES;
    private final Set<VisualizationMode> loadedLayers = EnumSet.noneOf(VisualizationMode.class);
    private final Set<String> visibleDisciplines = new LinkedHashSet<>();

    // ── Record types ────────────────────────────────────────────────

    public record ModeState(VisualizationMode mode, Set<VisualizationMode> loaded,
                            Set<String> visibleDisciplines) {}

    public record SwitchResult(VisualizationMode from, VisualizationMode to,
                               long switchTimeMs) {}

    // ── Mode management ─────────────────────────────────────────────

    /** Get current visualization mode. */
    public VisualizationMode currentMode() { return currentMode; }

    /** Get full state for serialization to WebUI. */
    public ModeState state() {
        return new ModeState(currentMode, EnumSet.copyOf(loadedLayers),
                Set.copyOf(visibleDisciplines));
    }

    /**
     * Switch visualization mode — instant on the WebUI side.
     * Returns timing info matching Python's switch_mode() contract.
     */
    public SwitchResult switchMode(VisualizationMode mode) {
        long start = System.nanoTime();
        VisualizationMode from = currentMode;
        currentMode = mode;
        if (!loadedLayers.contains(mode)) {
            loadedLayers.add(mode);
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        BIMLogger.info(TAG, "Switched {} → {} in {}ms", from, mode, elapsed);
        return new SwitchResult(from, mode, elapsed);
    }

    /**
     * Mark all layers as loaded — called after initial data load.
     * Matches Python's load_all_layers().
     */
    public void markAllLoaded() {
        loadedLayers.addAll(EnumSet.allOf(VisualizationMode.class));
    }

    /** Check if a mode is loaded. */
    public boolean isLayerLoaded(VisualizationMode mode) {
        return mode == VisualizationMode.BBOXES || loadedLayers.contains(mode);
    }

    /** Check if all layers are loaded. */
    public boolean areAllLayersLoaded() {
        return loadedLayers.containsAll(EnumSet.allOf(VisualizationMode.class));
    }

    /** Unload all layers — matches Python's unload_all_layers(). */
    public void unloadAllLayers() {
        loadedLayers.clear();
        visibleDisciplines.clear();
        currentMode = VisualizationMode.BBOXES;
        BIMLogger.info(TAG, "Unloaded all visualization layers");
    }

    // ── Discipline visibility (matches Python's _discipline_visibility) ──

    /** Toggle discipline visibility (for legend panel). */
    public void setDisciplineVisible(String discipline, boolean visible) {
        if (visible) visibleDisciplines.add(discipline);
        else visibleDisciplines.remove(discipline);
    }

    /** Show all disciplines. */
    public void showAllDisciplines() {
        visibleDisciplines.addAll(ColorSchemeEngine.getDisciplineColors().keySet());
    }

    /** Get currently visible disciplines. */
    public Set<String> visibleDisciplines() {
        return Collections.unmodifiableSet(visibleDisciplines);
    }
}
