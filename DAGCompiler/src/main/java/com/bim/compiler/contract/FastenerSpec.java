package com.bim.compiler.contract;

/**
 * Specification for fasteners used in component connections.
 *
 * <p>Theory: AS 1684 timber framing, IEC standards for fixings
 *
 * @param type Fastener type (e.g., "FRAMING_NAIL", "COACH_SCREW")
 * @param quantity Number of fasteners required
 * @param length Fastener length in meters (e.g., 0.075 for 75mm)
 * @param pattern Nailing/fastening pattern (e.g., "SKEW", "FACE", "END")
 */
public record FastenerSpec(
    String type,
    int quantity,
    double length,
    String pattern
) {
    /**
     * Standard stud-to-plate nailing: 2x 75mm framing nails, skew-nailed.
     * From AS 1684 Table 9.18.
     */
    public static final FastenerSpec STUD_TO_PLATE = new FastenerSpec(
        "FRAMING_NAIL", 2, 0.075, "SKEW"
    );

    /**
     * Standard nogging-to-stud nailing: 2x 75mm framing nails, end-nailed.
     * From AS 1684 Table 9.18.
     */
    public static final FastenerSpec NOGGING_TO_STUD = new FastenerSpec(
        "FRAMING_NAIL", 2, 0.075, "END"
    );

    /**
     * Standard plate-to-slab connection: M12 anchor bolt.
     * From AS 1684 clause 9.2.
     */
    public static final FastenerSpec PLATE_TO_SLAB = new FastenerSpec(
        "ANCHOR_BOLT", 1, 0.100, "THROUGH"
    );

    @Override
    public String toString() {
        return String.format("%dx %s %.0fmm (%s)",
            quantity, type, length * 1000, pattern);
    }
}
