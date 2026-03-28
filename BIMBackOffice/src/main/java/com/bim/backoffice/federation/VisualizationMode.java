package com.bim.backoffice.federation;

/**
 * Visualization modes — ported from visualization_manager.py.
 *
 * <p>The Python addon manages three Blender collection layers with instant
 * switching (&lt;0.1s). The Java equivalent tracks mode state for WebUI rendering:
 * the WebUI applies the mode via CSS/WebGL, not Blender collections.
 *
 * <p>Modes:
 * <ul>
 *   <li>{@link #BBOXES} — fast wireframe boxes (&lt;1s load, GPU batch)</li>
 *   <li>{@link #SEMANTICS} — simple 3D shapes with basic materials</li>
 *   <li>{@link #MATERIALS} — detailed materials with full texturing</li>
 * </ul>
 *
 * // Porting visualization_manager.py — Witness: W-VIS-MODE-1
 */
public enum VisualizationMode {
    BBOXES("Federation_BBoxes", "Fast wireframe boxes"),
    SEMANTICS("Federation_Semantics", "Simple 3D shapes"),
    MATERIALS("Federation_Materials", "Detailed materials");

    private final String collectionName;
    private final String description;

    VisualizationMode(String collectionName, String description) {
        this.collectionName = collectionName;
        this.description = description;
    }

    public String collectionName() { return collectionName; }
    public String description() { return description; }
}
