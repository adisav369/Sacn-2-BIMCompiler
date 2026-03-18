package com.bim.designer.api;

import java.util.List;

/**
 * Response from {@code createNew} — extends compile response with design-mode
 * bounding boxes for viewport rendering.
 *
 * <p>When {@code success} is true, {@code bboxes} contains the generated room
 * layout as wireframe boxes with IFC/BOM metadata. The Python addon renders
 * these as vivid category-colored wireframes (Design Mode).
 *
 * <p>{@code outputDbPath} is null because nothing is committed yet — the
 * bboxes are draft layout only. See BIM_Designer.md §17.11.
 *
 * @param success        whether layout generation succeeded
 * @param elementCount   estimated element count
 * @param compileTimeMs  generation time in milliseconds
 * @param outputDbPath   null (uncommitted); set after commit
 * @param spatialDigest  layout digest for change detection
 * @param error          error message if success is false
 * @param bboxes         design-mode bounding boxes with metadata
 */
public record CreateNewResponse(
        boolean success,
        int elementCount,
        long compileTimeMs,
        String outputDbPath,
        String spatialDigest,
        String error,
        List<DesignBBox> bboxes
) {
    public static CreateNewResponse success(int elementCount, long compileTimeMs,
                                            String spatialDigest, List<DesignBBox> bboxes) {
        return new CreateNewResponse(true, elementCount, compileTimeMs,
                null, spatialDigest, null, bboxes);
    }

    public static CreateNewResponse failure(String error) {
        return new CreateNewResponse(false, 0, 0, null, null, error, List.of());
    }
}
