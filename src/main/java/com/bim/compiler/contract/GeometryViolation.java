package com.bim.compiler.contract;

/**
 * Represents a geometry validation violation from Layer 0 checking.
 *
 * <p>Geometry violations are distinct from semantic Violations (Layer 5)
 * because they operate at the mesh primitive level, not the domain level.
 *
 * <p>Standard codes:
 * <ul>
 *   <li>FACE_INDEX_OUT_OF_BOUNDS - Face references non-existent vertex</li>
 *   <li>DEGENERATE_FACE - Face has zero or near-zero area</li>
 *   <li>INCONSISTENT_WINDING - Face normals point different directions</li>
 *   <li>NON_MANIFOLD_MESH - Edge shared by more than 2 faces</li>
 *   <li>EULER_MISMATCH - Euler characteristic doesn't match expected topology</li>
 * </ul>
 *
 * @param severity ERROR (blocks compilation) or WARNING (informational)
 * @param code Standard violation code from the list above
 * @param message Human-readable description
 */
public record GeometryViolation(
    GeometrySeverity severity,
    String code,
    String message
) {
    /**
     * Severity levels for geometry violations.
     */
    public enum GeometrySeverity {
        /** Blocks compilation - mesh is invalid */
        ERROR,
        /** Warning only - mesh may have issues */
        WARNING
    }

    /**
     * Creates an ERROR severity violation.
     *
     * @param code Violation code
     * @param message Description
     * @return Error violation
     */
    public static GeometryViolation error(String code, String message) {
        return new GeometryViolation(GeometrySeverity.ERROR, code, message);
    }

    /**
     * Creates a WARNING severity violation.
     *
     * @param code Violation code
     * @param message Description
     * @return Warning violation
     */
    public static GeometryViolation warning(String code, String message) {
        return new GeometryViolation(GeometrySeverity.WARNING, code, message);
    }

    /**
     * Check if this violation blocks compilation.
     *
     * @return true if severity is ERROR
     */
    public boolean isError() {
        return severity == GeometrySeverity.ERROR;
    }

    @Override
    public String toString() {
        return String.format("[GEOMETRY %s] %s: %s", severity, code, message);
    }
}
