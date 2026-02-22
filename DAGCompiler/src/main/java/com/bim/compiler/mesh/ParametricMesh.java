package com.bim.compiler.mesh;

/**
 * Sealed interface for all parametric mesh generators.
 *
 * <p><strong>Contract (Mesh2Library.txt):</strong>
 * <ul>
 *   <li>No Python mesh scripts in the build pipeline</li>
 *   <li>No hardcoded vertex lists in Java (parameters come from
 *       {@code ad_parametric_mesh_param})</li>
 *   <li>Every new mesh shape requires a new {@code permits} entry here
 *       AND a new {@code ad_parametric_mesh} row — two gates for one change</li>
 *   <li>All parameters trace back to RESEARCHED_* or EXTRACTED_* provenance</li>
 * </ul>
 *
 * <p>iDempiere analogy: like {@code M_Product}'s product type determines the
 * production order template. The sealed interface IS the permitted type list —
 * adding a shape without updating this file is a compile-time error.
 *
 * <p>Usage:
 * <pre>{@code
 *   ParametricMesh generator = new GableRoofMesh();
 *   MeshParameters params = loadFromDB("GABLE_ROOF_MY");
 *   MeshResult result = generator.generate(params);
 * }</pre>
 */
public sealed interface ParametricMesh
        permits GableRoofMesh, HipRoofMesh, HalfRoundDrainMesh {

    /**
     * Generate a mesh from the given parameters.
     *
     * @param params loaded from {@code ad_parametric_mesh_param}
     * @return validated mesh result with provenance
     * @throws IllegalArgumentException if required params are missing
     *         or result is dimensionally invalid (zero volume)
     */
    MeshResult generate(MeshParameters params);
}
