package com.bim.compiler.contract;

import java.util.Optional;

/**
 * Enforces that every emitted element has a backing catalog entry.
 *
 * <p>TB-LKTN produced super-thin walls because the compiler invented wall thickness —
 * no catalog entry was consulted, no Stone geometry was referenced. The enforcer
 * prevents this: before any element is written to IFC, it must resolve to a catalog
 * product in {@code ad_product_dim}. If none is found, the element is either remeshed
 * from the closest Stone assembly or rejected — never silently given an invented size.
 *
 * <p><b>Remesh from Stone:</b> catalog assemblies from the proven buildings (SH, DX,
 * Terminal) are the authoritative source. A TB-LKTN internal wall does not need its
 * own catalog entry — it takes the closest SH internal wall (150mm, extracted) and
 * remeshes it to the TB-LKTN room dimension via {@code transformAndWriteGeometryScaled()}.
 * The thickness is never changed; only the length and height are scaled. The material
 * and profile come from the catalog entry, not from the new building.
 *
 * <p>The same applies to doors and windows. TB-LKTN looks poor in finishing because its
 * doors and windows were emitted without catalog backing — invented geometry with no
 * material, no frame profile, no glazing detail. A TB-LKTN door takes the closest SH
 * door assembly (same swing, closest width) and remeshes to fit the opening. The door
 * frame material, handle position, and threshold detail come from the Stone entry.
 *
 * <p><b>The enforcer applies to ALL buildings — SH and DX included.</b>
 * SH and DX are the Rosetta Stones: they are the examples of the correct process,
 * not exceptions to it. Every wall, door, window, and fixture in SH and DX must
 * also resolve through the enforcer. This proves the catalog is complete and the
 * process is sound before TB-LKTN or any new building relies on it. If SH passes
 * the enforcer cleanly, the catalog is verified. If it fails, the catalog has a gap
 * that must be filled from the Stone geometry — extracted and processed through
 * the contracts, not invented. Every entry in {@code ad_product_dim} traces back
 * to a dimension measured from a real building in the reference input DB.
 *
 * <p><b>The enforcer sits at the emit boundary:</b> it is called once per element,
 * just before geometry is written. It is the last gate. Anything that passes the
 * enforcer has a proven, extracted basis. Anything that fails is flagged — not silently
 * emitted with invented dimensions.
 *
 * <p><b>Mesh2Library is the only mesh creation path.</b>
 * When geometry does not yet exist in the catalog, it must enter via Mesh2Library —
 * defined in {@code lod_parametric_mesh} + {@code lod_parametric_mesh_param}, generated
 * by the sealed {@code ParametricMesh} interface, and loaded into
 * {@code component_library.db} before compilation runs.
 *
 * <pre>
 * Need a new mesh?
 *   1. Define in lod_parametric_mesh + lod_parametric_mesh_param
 *   2. ParametricMesh sealed interface generates the vertices
 *   3. Result enters component_library.db
 *   4. IBOMCatalogEnforcer resolves it from catalog
 *   5. transformAndWriteGeometryScaled() emits it
 *   NEVER: invent vertices inline in Java
 *   NEVER: writeBoxGeometry() as fallback
 *   NEVER: hardcoded vertex arrays
 * </pre>
 *
 * <p>iDempiere analogy: a Purchase Order line cannot reference a product that does not
 * exist in {@code M_Product}. The enforcer is the FK constraint at the emit boundary.
 *
 * @see IBOMContractor  — selects the closest catalog assembly
 * @see BoundBOM        — the resolved position chain that the enforcer validates
 */
public interface IBOMCatalogEnforcer {

    /**
     * Resolve a catalog entry for the given element before it is emitted.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Exact match in {@code ad_product_dim} by {@code productRef}</li>
     *   <li>Closest match via {@link IBOMContractor} — remesh from Stone assembly</li>
     *   <li>Empty — element must not be emitted; compiler logs a gap violation</li>
     * </ol>
     *
     * @param productRef  the product or BOM ID this element claims to be
     *                    (e.g. "WALL_INTERNAL_150", "BED_SET_MASTER", "FLOOR_SH_GF_STD")
     * @param widthMm     required width in mm — used for remesh scaling
     * @param depthMm     required depth (thickness for walls) in mm
     * @param heightMm    required height in mm
     * @return resolved catalog entry, or empty if no catalog basis exists
     */
    Optional<CatalogEntry> resolve(String productRef, double widthMm, double depthMm, double heightMm);

    /**
     * A resolved catalog entry — the basis for geometry emission.
     *
     * @param productId    catalog product ID from {@code ad_product_dim}
     * @param geometryHash LOD geometry hash from the component library
     * @param catalogW     catalog product width in meters (intrinsic, never invented)
     * @param catalogD     catalog product depth in meters
     * @param catalogH     catalog product height in meters
     * @param materialName material from catalog (not from the target building)
     * @param isExactMatch true if productRef matched directly; false if remeshed from Stone
     */
    record CatalogEntry(
            String  productId,
            String  geometryHash,
            double  catalogW,
            double  catalogD,
            double  catalogH,
            String  materialName,
            boolean isExactMatch
    ) {
        /**
         * Scale factor to apply to geometry to reach the target dimension.
         * For walls: scaleLength = targetWidth / catalogW, scaleHeight = targetHeight / catalogH.
         * Thickness (depth) is NEVER scaled — it is a property of the wall assembly, not the room.
         */
        public double scaleLength(double targetMm) { return targetMm / (catalogW * 1000.0); }
        public double scaleHeight(double targetMm)  { return targetMm / (catalogH * 1000.0); }
    }
}
