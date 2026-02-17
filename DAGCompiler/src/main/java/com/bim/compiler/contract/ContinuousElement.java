package com.bim.compiler.contract;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Element that spans multiple storeys as a single continuous entity.
 *
 * <p>Continuous elements solve the "vertical discontinuity" problem where
 * each storey creates its own column/riser segment. Instead, a single
 * ContinuousElement spans all storeys and is referenced by each.
 *
 * <p>Maps to IfcColumn/IfcWall with spanning representation.
 * <p>Theory: Identity Map pattern (Fowler), genidentity
 *
 * <p>Example: Column at grid position A-1 spans Ground through Roof.
 * Rather than 3 separate column objects, one ContinuousElement with
 * continuityId "COL_A1" exists, referenced by all storeys.
 */
public final class ContinuousElement {

    private final String continuityId;
    private final String ifcClass;
    private final Point3D basePosition;
    private final List<String> storeys;
    private double totalHeight;
    private BoundingBox combinedBounds;

    /**
     * Creates a continuous spanning element.
     *
     * @param continuityId Stable ID across storeys (e.g., "COL_A1")
     * @param ifcClass IFC classification (e.g., "IfcColumn")
     * @param basePosition XY position at base (Z will vary)
     */
    public ContinuousElement(String continuityId, String ifcClass, Point3D basePosition) {
        this.continuityId = continuityId;
        this.ifcClass = ifcClass;
        this.basePosition = basePosition;
        this.storeys = new ArrayList<>();
        this.totalHeight = 0;
        this.combinedBounds = null;
    }

    /**
     * Stable identifier that persists across storeys.
     */
    public String continuityId() {
        return continuityId;
    }

    /**
     * IFC classification for this element.
     */
    public String ifcClass() {
        return ifcClass;
    }

    /**
     * Base position (XY at ground level).
     */
    public Point3D basePosition() {
        return basePosition;
    }

    /**
     * Storeys this element spans.
     * Returns unmodifiable view.
     */
    public List<String> storeys() {
        return Collections.unmodifiableList(storeys);
    }

    /**
     * Total height from base to top.
     */
    public double totalHeight() {
        return totalHeight;
    }

    /**
     * Combined bounding box across all storeys.
     */
    public BoundingBox combinedBounds() {
        return combinedBounds;
    }

    /**
     * Extend this element through another storey.
     *
     * @param storeyName Name of storey to add
     * @param storeyHeight Height of this storey segment
     * @param segmentBounds Bounds of segment in this storey
     */
    public void extendThrough(String storeyName, double storeyHeight, BoundingBox segmentBounds) {
        if (!storeys.contains(storeyName)) {
            storeys.add(storeyName);
            totalHeight += storeyHeight;

            if (combinedBounds == null) {
                combinedBounds = segmentBounds;
            } else {
                // Union with existing bounds
                combinedBounds = new BoundingBox(
                    Math.min(combinedBounds.minX(), segmentBounds.minX()),
                    Math.max(combinedBounds.maxX(), segmentBounds.maxX()),
                    Math.min(combinedBounds.minY(), segmentBounds.minY()),
                    Math.max(combinedBounds.maxY(), segmentBounds.maxY()),
                    Math.min(combinedBounds.minZ(), segmentBounds.minZ()),
                    Math.max(combinedBounds.maxZ(), segmentBounds.maxZ())
                );
            }
        }
    }

    /**
     * Check if this element spans multiple storeys.
     */
    public boolean isMultiStorey() {
        return storeys.size() > 1;
    }

    /**
     * Get number of storeys spanned.
     */
    public int storeyCount() {
        return storeys.size();
    }

    @Override
    public String toString() {
        return String.format("Continuous[%s %s, spans %d storeys, %.2fm tall]",
            ifcClass, continuityId, storeys.size(), totalHeight);
    }
}
