package com.bim.compiler.dsl;

/**
 * Thrown when a mesh cannot be contractually bound to its placement bbox.
 * This is the enforcement mechanism for the Pre-Placement Dimensional Contract
 * (see docs/CompilerChasm.txt).
 *
 * A DimensionalContractViolation means the library mesh is so far from the
 * target bbox that scaling would produce degenerate geometry. The fix is
 * always a DATA fix (wrong element_rule, wrong geometry_map, wrong library entry)
 * — never a code workaround.
 */
public class DimensionalContractViolation extends RuntimeException {

    private final String guid;
    private final int axis;       // 0=X, 1=Y, 2=Z
    private final double scaleFactor;

    public DimensionalContractViolation(String guid, int axis, double scaleFactor,
                                         String message) {
        super(message);
        this.guid = guid;
        this.axis = axis;
        this.scaleFactor = scaleFactor;
    }

    public String getGuid() { return guid; }
    public int getAxis() { return axis; }
    public double getScaleFactor() { return scaleFactor; }

    private static final String[] AXIS_NAMES = {"X", "Y", "Z"};

    @Override
    public String toString() {
        return String.format("DimensionalContractViolation[%s axis=%s scale=%.3f]: %s",
            guid, AXIS_NAMES[axis], scaleFactor, getMessage());
    }
}
