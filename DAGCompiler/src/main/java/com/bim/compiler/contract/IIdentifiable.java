package com.bim.compiler.contract;

/**
 * Layer 2: Identity Contract - Determines when elements are "the same".
 * Provides keys for deduplication within contexts and continuity across contexts.
 *
 * <p>Maps to: IfcRoot.GlobalId + custom continuity semantics
 * <p>Theory: Identity Map pattern (Fowler 2002), Genidentity (philosophy)
 *
 * <p>Identity has two aspects in BIM:
 * <ol>
 *   <li><b>Uniqueness within context</b>: Two studs at same corner are duplicates</li>
 *   <li><b>Continuity across contexts</b>: Column on ground floor is "same" column on upper floor</li>
 * </ol>
 *
 * @see <a href="https://martinfowler.com/eaaCatalog/identityMap.html">Identity Map Pattern</a>
 */
public interface IIdentifiable extends IBIMEntity {

    /**
     * Key for deduplication within same context (storey/unit).
     * Elements with same uniqueKey at same location are duplicates.
     *
     * <p>Contract: Must be deterministic and stable across compilations.
     * Should encode enough information to identify duplicates without
     * requiring coordinate comparison.
     *
     * <p>Example format: "{ROLE}_{X}_{Y}_{STOREY}"
     * <ul>
     *   <li>"CORNER_STUD_0.0_8.5_Ground"</li>
     *   <li>"COLUMN_A1_Ground"</li>
     *   <li>"OUTLET_bilik_tidur_1_N_0"</li>
     * </ul>
     *
     * @return non-null unique key for deduplication
     */
    String uniqueKey();

    /**
     * ID that persists across storeys/contexts.
     * Non-null for elements that span (columns, risers, shafts).
     * Null for elements that exist only in single context.
     *
     * <p>Contract: If non-null, must be consistent across all storeys.
     * Elements with same continuityId represent the same physical element
     * at different vertical levels.
     *
     * <p>Example: "COL_A1" for column at grid A-1 across all storeys
     *
     * @return continuity ID if element spans contexts, null otherwise
     */
    String continuityId();

    /**
     * Type definition this element is an instance of.
     * Maps to IfcRelDefinesByType.
     *
     * <p>Contract: Must reference a valid type definition in the library.
     * Null for elements without explicit type (e.g., custom assemblies).
     *
     * <p>Example: "D1_900x2100" for door type, "W1_1200x1200" for window type
     *
     * @return type reference if defined, null otherwise
     */
    String typeRef();
}
