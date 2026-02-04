package com.bim.compiler.geometry;

/**
 * Phase 63: Scoped coordinate context for unit-local to building-global transforms.
 *
 * Solves COORD_MISMATCH by ensuring all placements use consistent coordinate system.
 * Uses ThreadLocal for automatic scope management - no explicit passing required.
 *
 * Usage:
 *   try (var scope = CoordinateScope.enter(unitOriginX, unitOriginY, storeyBaseZ)) {
 *       Point3D world = CoordinateScope.toWorld(localX, localY, localZ);
 *       // world coords are now correctly transformed
 *   }
 *   // scope automatically restored to parent (or global)
 */
public class CoordinateScope implements AutoCloseable {

    private static final ThreadLocal<CoordinateScope> current = new ThreadLocal<>();

    private final double originX;
    private final double originY;
    private final double originZ;
    private final CoordinateScope parent;
    private final String name;  // For debugging

    private CoordinateScope(String name, double originX, double originY, double originZ,
                            CoordinateScope parent) {
        this.name = name;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.parent = parent;
    }

    /**
     * Enter a new coordinate scope (e.g., for a unit or storey).
     * All toWorld() calls will apply this transform until scope is closed.
     */
    public static CoordinateScope enter(String name, double originX, double originY, double originZ) {
        CoordinateScope scope = new CoordinateScope(name, originX, originY, originZ, current.get());
        current.set(scope);
        return scope;
    }

    /**
     * Enter scope with just Z offset (common for storeys).
     */
    public static CoordinateScope enterStorey(String storeyName, double baseZ) {
        return enter("storey:" + storeyName, 0, 0, baseZ);
    }

    /**
     * Enter scope for a unit with XY offset.
     */
    public static CoordinateScope enterUnit(String unitName, double originX, double originY, double originZ) {
        return enter("unit:" + unitName, originX, originY, originZ);
    }

    /**
     * Transform local coordinates to world coordinates.
     * Applies all nested scopes (unit inside storey, etc).
     */
    public static Point3D toWorld(double localX, double localY, double localZ) {
        CoordinateScope scope = current.get();
        if (scope == null) {
            return new Point3D(localX, localY, localZ);
        }
        return scope.transform(localX, localY, localZ);
    }

    /**
     * Transform local point to world coordinates.
     */
    public static Point3D toWorld(Point3D local) {
        return toWorld(local.x(), local.y(), local.z());
    }

    /**
     * Transform world coordinates to local (inverse).
     */
    public static Point3D toLocal(double worldX, double worldY, double worldZ) {
        CoordinateScope scope = current.get();
        if (scope == null) {
            return new Point3D(worldX, worldY, worldZ);
        }
        return scope.inverseTransform(worldX, worldY, worldZ);
    }

    /**
     * Get cumulative origin (sum of all nested scopes).
     */
    public static Point3D getOrigin() {
        CoordinateScope scope = current.get();
        if (scope == null) {
            return new Point3D(0, 0, 0);
        }
        return scope.getCumulativeOrigin();
    }

    /**
     * Check if we're inside any coordinate scope.
     */
    public static boolean isInScope() {
        return current.get() != null;
    }

    /**
     * Get current scope name for debugging.
     */
    public static String currentScopeName() {
        CoordinateScope scope = current.get();
        return scope != null ? scope.getFullName() : "(global)";
    }

    @Override
    public void close() {
        current.set(parent);
    }

    // =========================================================================
    // Private implementation
    // =========================================================================

    private Point3D transform(double localX, double localY, double localZ) {
        // Apply this scope's transform
        double worldX = localX + originX;
        double worldY = localY + originY;
        double worldZ = localZ + originZ;

        // Apply parent transforms (if nested)
        if (parent != null) {
            return parent.transform(worldX, worldY, worldZ);
        }

        return new Point3D(worldX, worldY, worldZ);
    }

    private Point3D inverseTransform(double worldX, double worldY, double worldZ) {
        // First apply parent inverse (if nested)
        if (parent != null) {
            Point3D parentLocal = parent.inverseTransform(worldX, worldY, worldZ);
            worldX = parentLocal.x();
            worldY = parentLocal.y();
            worldZ = parentLocal.z();
        }

        // Then apply this scope's inverse
        return new Point3D(worldX - originX, worldY - originY, worldZ - originZ);
    }

    private Point3D getCumulativeOrigin() {
        double cumX = originX;
        double cumY = originY;
        double cumZ = originZ;

        if (parent != null) {
            Point3D parentOrigin = parent.getCumulativeOrigin();
            cumX += parentOrigin.x();
            cumY += parentOrigin.y();
            cumZ += parentOrigin.z();
        }

        return new Point3D(cumX, cumY, cumZ);
    }

    private String getFullName() {
        if (parent != null) {
            return parent.getFullName() + " > " + name;
        }
        return name;
    }

    @Override
    public String toString() {
        return String.format("CoordinateScope[%s, origin=(%.2f,%.2f,%.2f)]",
                             name, originX, originY, originZ);
    }
}
