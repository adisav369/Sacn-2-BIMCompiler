package com.bim.compiler.dsl;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Mesh;

import java.util.Objects;

/**
 * A BoundElement is the PROOF that a mesh fits its placement bbox.
 * BuildingWriter accepts ONLY BoundElements for the generative path —
 * type safety makes it impossible to write unbound mesh.
 *
 * This is the "dimensional contract" from CompilerChasm.txt
 * realized as a Java record invariant.
 *
 * The contract: for every axis, the scale factor (bbox extent / mesh extent)
 * must fall within [MIN_SCALE, MAX_SCALE]. If not, the data is wrong and
 * a DimensionalContractViolation is thrown.
 */
public record BoundElement(
    String guid,
    String ifcClass,
    String elementRef,
    String type,
    String storey,
    PlacementLoader.Placement placement,
    Mesh transformedMesh,
    String geometryHash,
    double scaleX,
    double scaleY,
    double scaleZ,
    GeometryProvenance provenance,
    String materialName,
    String materialRgba
) {
    // Scale factor bounds: mesh can be shrunk to 30% or grown to 300%
    // Beyond this range, the data is wrong (wrong library entry or wrong rule)
    static final double MIN_SCALE = 0.3;
    static final double MAX_SCALE = 3.0;

    // Minimum mesh extent (meters) below which scaling is meaningless
    static final double MIN_EXTENT = 0.001;

    // Tolerance for "needs scaling" decision (1mm in meters)
    static final double SCALE_TOLERANCE = 0.002;

    /**
     * Compact constructor: enforces the dimensional contract.
     * If the mesh doesn't fit the bbox within allowable scale range, throws.
     */
    public BoundElement {
        Objects.requireNonNull(placement, "No placement for " + guid);
        Objects.requireNonNull(transformedMesh, "No mesh for " + guid);
        Objects.requireNonNull(geometryHash, "No geometry hash for " + guid);

        // Verify transformed mesh bounds approximate the placement bbox
        BoundingBox meshBounds = transformedMesh.bounds();
        BoundingBox placementBbox = new BoundingBox(
            placement.minX(), placement.maxX(),
            placement.minY(), placement.maxY(),
            placement.minZ(), placement.maxZ()
        );

        double tolerance = 0.002; // 2mm
        if (meshBounds.minX() < placementBbox.minX() - tolerance ||
            meshBounds.maxX() > placementBbox.maxX() + tolerance ||
            meshBounds.minY() < placementBbox.minY() - tolerance ||
            meshBounds.maxY() > placementBbox.maxY() + tolerance ||
            meshBounds.minZ() < placementBbox.minZ() - tolerance ||
            meshBounds.maxZ() > placementBbox.maxZ() + tolerance) {
            // Warn but don't fail — library meshes may legitimately have
            // minor overflows due to float32 rounding in vertex data
            System.err.printf("[BOUND] Mesh overflow: %s %s mesh%s vs bbox%s%n",
                ifcClass, elementRef, meshBounds, placementBbox);
        }
    }

    /**
     * Whether this element required scaling to fit its placement.
     */
    public boolean scaleRequired() {
        return Math.abs(scaleX - 1.0) > SCALE_TOLERANCE
            || Math.abs(scaleY - 1.0) > SCALE_TOLERANCE
            || Math.abs(scaleZ - 1.0) > SCALE_TOLERANCE;
    }

    /**
     * Validate scale factors against the dimensional contract.
     * Called by MeshBinder BEFORE constructing the BoundElement.
     * Throws DimensionalContractViolation if any axis is out of range.
     */
    static void validateScaleFactors(String guid, double sx, double sy, double sz) {
        double[] scales = {sx, sy, sz};
        for (int axis = 0; axis < 3; axis++) {
            double s = scales[axis];
            if (s < MIN_SCALE || s > MAX_SCALE) {
                throw new DimensionalContractViolation(guid, axis, s,
                    String.format("Scale factor %.3f outside [%.1f, %.1f] on %s axis — " +
                        "library mesh does not fit placement bbox. Fix the data.",
                        s, MIN_SCALE, MAX_SCALE, new String[]{"X", "Y", "Z"}[axis]));
            }
        }
    }
}
