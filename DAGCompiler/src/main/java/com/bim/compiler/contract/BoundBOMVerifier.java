package com.bim.compiler.contract;

import java.util.List;

/**
 * Math-based truth testing for the BOM positional chain.
 *
 * <p>Every assertion is a numerical check — no visual inspection, no Blender.
 * The chain is correct if and only if the maths holds at every node:
 * <ul>
 *   <li>Bbox consistency: {@code node.maxX() - node.worldX == node.widthMm / 1000} to ε</li>
 *   <li>Chain accumulation: {@code child.worldX == parent.worldX + child.dx} to ε</li>
 *   <li>Room boundary agreement: ROOM-level node dimensions match {@code ad_room_boundary}</li>
 *   <li>No sibling overlap: no two children of the same parent share XY space</li>
 *   <li>Area conservation: sum of room areas ≤ floor bbox area (rooms fit in floor)</li>
 * </ul>
 *
 * <p>If all assertions pass, placement cannot be wrong. The maths is the proof.
 * There is no "looks right" — there is only "the chain accumulates correctly".
 *
 * <p>Applied to SH and DX first (the Rosetta Stones). Once SH/DX pass cleanly,
 * the same verifier runs on every new building automatically. A new building with
 * a broken chain is caught before it reaches the viewer.
 */
public interface BoundBOMVerifier {

    /** Epsilon for floating-point comparisons — 1mm in meters. */
    double EPSILON_M = 0.001;

    /**
     * Verify the full BoundBOM tree rooted at a building unit.
     * Walks every node recursively and collects all violations.
     *
     * @param root the UNIT-level BoundBOM (top of the chain)
     * @return result containing all violations found; empty = chain is mathematically sound
     */
    VerificationResult verify(BoundBOM root);

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The result of verifying a BoundBOM tree.
     *
     * @param violations all math violations found in the tree
     */
    record VerificationResult(List<Violation> violations) {

        /** True if no violations were found — chain is sound. */
        public boolean passed() { return violations.isEmpty(); }

        /** Count of critical violations — failures that block emit. */
        public long criticalCount() {
            return violations.stream().filter(Violation::critical).count();
        }

        /** Count of advisory violations — logged but not blocking. */
        public long advisoryCount() {
            return violations.stream().filter(v -> !v.critical()).count();
        }
    }

    /**
     * A single math violation in the BoundBOM tree.
     *
     * @param nodeId    BOM ID of the node where the violation was found
     * @param assertion name of the math assertion that failed
     * @param actual    the computed/stored value
     * @param expected  the mathematically correct value
     * @param deltaMm   |actual - expected| in mm — how far off it is
     * @param critical  true = blocks emit; false = advisory (logged, not blocking)
     */
    record Violation(
            String  nodeId,
            String  assertion,
            double  actual,
            double  expected,
            double  deltaMm,
            boolean critical
    ) {
        @Override public String toString() {
            return String.format("[%s] %s: actual=%.3f expected=%.3f delta=%.1fmm %s",
                nodeId, assertion, actual, expected, deltaMm,
                critical ? "CRITICAL" : "advisory");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Named math assertions — each maps to a specific numerical check.
     */
    enum Assertion {

        /**
         * Bbox width consistency.
         * {@code |node.maxX() - node.worldX - node.widthMm/1000| < ε}
         * Critical: a node with inconsistent bbox emits wrong geometry.
         */
        BBOX_WIDTH_CONSISTENT,

        /**
         * Bbox depth consistency.
         * {@code |node.maxY() - node.worldY - node.depthMm/1000| < ε}
         * Critical.
         */
        BBOX_DEPTH_CONSISTENT,

        /**
         * Bbox height consistency.
         * {@code |node.maxZ() - node.worldZ - node.heightMm/1000| < ε}
         * Critical.
         */
        BBOX_HEIGHT_CONSISTENT,

        /**
         * Chain X accumulation.
         * {@code |child.worldX - (parent.worldX + childDx)| < ε}
         * Critical: broken chain means wrong world position.
         */
        CHAIN_X_ACCUMULATION,

        /**
         * Chain Y accumulation.
         * {@code |child.worldY - (parent.worldY + childDy)| < ε}
         * Critical.
         */
        CHAIN_Y_ACCUMULATION,

        /**
         * Chain Z accumulation — storey elevation correctness.
         * {@code |child.worldZ - (parent.worldZ + childDz)| < ε}
         * Critical: broken Z chain collapses floors onto one level.
         */
        CHAIN_Z_ACCUMULATION,

        /**
         * Room boundary dimension agreement.
         * For ROOM-level nodes: {@code |node.widthMm - rb_width| < 1mm}
         * Advisory: room boundary is the reference truth; mismatch means
         * spacing facts were not correctly extracted.
         */
        ROOM_WIDTH_MATCHES_BOUNDARY,

        /**
         * Room boundary depth agreement.
         * Advisory.
         */
        ROOM_DEPTH_MATCHES_BOUNDARY,

        /**
         * No sibling overlap.
         * For each pair of children under the same parent, their XY bboxes
         * must not intersect. {@code overlapArea == 0}
         * Advisory: overlap means two rooms or two furniture sets share space.
         */
        NO_SIBLING_OVERLAP,

        /**
         * Area conservation at floor level.
         * Sum of child room areas ≤ parent floor bbox area.
         * {@code Σ(child.widthMm * child.depthMm) ≤ parent.widthMm * parent.depthMm}
         * Advisory: rooms cannot be larger than the floor that contains them.
         */
        FLOOR_AREA_CONSERVATION
    }
}
