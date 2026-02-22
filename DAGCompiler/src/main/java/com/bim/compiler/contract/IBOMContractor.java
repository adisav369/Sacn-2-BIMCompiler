package com.bim.compiler.contract;

import java.util.List;
import java.util.Optional;

/**
 * A contractor that assigns the closest-fitting BOM assembly to a new room or space.
 *
 * <p>When a new room topology is introduced (e.g. {@code AA_LIVING_STD} for a new
 * building type), the contractor searches the catalog for the assembly that best
 * satisfies the room's spatial and MEP requirements — without inventing anything new.
 * Everything it selects is already in {@code ad_bom}, extracted from proven buildings.
 *
 * <p>The contractor does not design. It assigns. Like a construction contractor
 * who finds the best qualified subcontractor from a roster: the room states what it
 * needs; the contractor finds who can deliver it.
 *
 * <p>Fit scoring:
 * <ol>
 *   <li><b>Hard constraints</b> — must pass or candidate is rejected:
 *       minimum area, required face interfaces, required MEP connectors.</li>
 *   <li><b>Soft constraints</b> — scored for proximity: dimension match,
 *       version preference, clearance surplus.</li>
 * </ol>
 *
 * <p>iDempiere analogy: {@code M_Product} selection by attribute set — find the
 * product in the catalog whose attribute values best match the specification.
 * OSGi analogy: find the bundle that provides the required capability within
 * the declared version range.
 *
 * <p>If no candidate passes the hard constraints, the contractor returns empty —
 * the room remains unassigned and the compiler flags it as a gap, not a guess.
 *
 * <p><b>Later validators (deferred — after Stones are complete):</b>
 * Once the positional chain is proven on SH and DX, specialist validators join in
 * to enforce cross-assembly constraints across the full BoundBOM tree:
 * <ul>
 *   <li>Duct alignment validator — MEP duct runs connect between floors without gaps</li>
 *   <li>Stair continuity validator — stair assemblies align storey-to-storey</li>
 *   <li>Wet-area stack validator — sanitary drains align vertically to the riser</li>
 *   <li>Clearance validator — no two BoundBOM bboxes overlap after placement</li>
 * </ul>
 * These validators operate on the fully accumulated BoundBOM tree — they cannot run
 * until every node in the chain has {@code resolved=true}. Get the chain right first.
 *
 * <p><b>Note — joins already exist as extracted facts in the Stones:</b>
 * The Duplex contains duct alignments, stair connections, and wet-area stack positions
 * as extracted geometry. The Terminal (~51 000 elements) is the richest Stone: MEP and
 * FP assemblies carry explicit connector facts — pipe diameters, duct sizes, tee
 * positions, sprinkler drop offsets — all extracted from the reference input DB into
 * {@code element_instances} and {@code elements_rtree}. The contractor reads these
 * facts when assigning MEP/FP assemblies; it does not infer connection points.
 * When the cross-assembly validators are activated, they verify against those same
 * extracted facts, not against rules derived from scratch.
 *
 * @see IBOMChildLine — declares the local position once an assembly is assigned
 * @see BoundBOM     — the resolved world position after the chain is accumulated
 */
public interface IBOMContractor {

    /**
     * Find the best-fitting assembly for the given room requirement.
     *
     * <p>Returns empty if no catalog entry satisfies the hard constraints.
     * Never returns a partial fit that violates a hard constraint — the contractor
     * would rather leave the slot unfilled than place something wrong.
     *
     * @param requirement the room's spatial and interface requirements
     * @return best-fit candidate, or empty if catalog has no satisfying assembly
     */
    Optional<Candidate> assign(RoomRequirement requirement);

    /**
     * Rank all catalog assemblies against the requirement, hard failures excluded.
     * Useful for diagnostics: shows which assembly came closest and why it was chosen.
     *
     * @param requirement the room's requirements
     * @return all passing candidates ordered by descending fit score
     */
    List<Candidate> rank(RoomRequirement requirement);

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The spatial and interface requirements of a room or space.
     * Extracted from building metadata — never invented.
     *
     * @param roomType      type identifier (BEDROOM, BATHROOM, KITCHEN, LIVING, COMMON, …)
     * @param widthMm       actual room width in mm (from {@code ad_room_boundary})
     * @param depthMm       actual room depth in mm
     * @param heightMm      clear height in mm (storey height less slab thickness)
     * @param constraints    boolean conditions the assignment must satisfy — both positive
     *                       (must have) and negative (must not have). Examples:
     *                       {@code Constraint("WALL_BACK", true)} — must have a back wall;
     *                       {@code Constraint("WASTE_SHAFT", false)} — must NOT be near a waste shaft;
     *                       {@code Constraint("EXTERIOR", false)} — must NOT be on an exterior face
     *                       (a petrol station toilet may invert this to {@code true}).
     *                       The contractor evaluates all constraints; one failure = candidate rejected.
     * @param versionRange   OSGi-style version range, e.g. {@code [1.0,2.0)} — null = any
     */
    record RoomRequirement(
            String             roomType,
            double             widthMm,
            double             depthMm,
            double             heightMm,
            List<Constraint>   constraints,
            String             versionRange
    ) {
        /** Area of this room in m². Used for min_area hard-constraint check. */
        public double areaSqm() { return (widthMm / 1000.0) * (depthMm / 1000.0); }
    }

    /**
     * A single condition in a room requirement — positive, negative, or quantity-capped.
     *
     * <p><b>AD window mapping (master-detail-detail-N):</b>
     * In iDempiere, the Product window is Main → BOM sub-tab → BOM Line sub-sub-tab.
     * Constraints are a further detail level — a Validation sub-tab of the BOM Line:
     * <pre>
     * ad_bom              (M_BOM)      — main
     *   ad_bom_child      (M_BOM_Line) — sub-tab
     *     ad_bom_constraint            — validation sub-sub-tab (this Constraint)
     * </pre>
     * Each BOM child line may carry N constraint rows. The contractor evaluates all of
     * them. One failed constraint rejects the candidate. This is the standard AD
     * master-detail-detail(N) pattern: the main record governs, the sub-tabs qualify.
     *
     * <p>All spatial conditions are expressed in this one form:
     * <ul>
     *   <li>{@code mustHave("WALL_BACK")} — assembly must back against a wall</li>
     *   <li>{@code mustNotHave("FUEL_STORAGE")} — must not be adjacent to fuel storage</li>
     *   <li>{@code mustNotHave("EXTERIOR")} — must not be on an exterior face
     *       (a petrol station toilet inverts this: {@code mustHave("EXTERIOR")})</li>
     *   <li>{@code limitTo("WORKSTATION", 4)} — not above 4 workstations in a shared
     *       open-plan space; open areas with no walls may share occupants freely
     *       but fire egress and density codes impose a maximum count</li>
     * </ul>
     *
     * @param target   face type, MEP connector, assembly ID, or spatial category
     * @param required true = must have / must be near; false = must NOT exceed maxQty
     * @param maxQty   maximum count allowed; {@code Integer.MAX_VALUE} = uncapped
     */
    record Constraint(String target, boolean required, int maxQty) {

        /** Must have at least one — no quantity cap. */
        static Constraint mustHave(String target) {
            return new Constraint(target, true, Integer.MAX_VALUE);
        }

        /** Strictly prohibited — zero allowed. */
        static Constraint mustNotHave(String target) {
            return new Constraint(target, false, 0);
        }

        /** Allowed but capped: not more than {@code max} in this space. */
        static Constraint limitTo(String target, int max) {
            return new Constraint(target, false, max);
        }
    }

    /**
     * A catalog assembly evaluated against a {@link RoomRequirement}.
     * Only candidates that pass all hard constraints appear in {@link #rank(RoomRequirement)}.
     *
     * @param bomId            the assembly ID from {@code ad_bom.bom_id}
     * @param score            fit score in [0.0, 1.0] — 1.0 = exact match on all soft
     *                         constraints, 0.0 = passes hard constraints only
     * @param widthFitMm       surplus width in mm (room width − assembly width); always ≥ 0
     * @param depthFitMm       surplus depth in mm; always ≥ 0
     * @param failedConstraints constraints not satisfied by this candidate (empty = perfect fit)
     */
    record Candidate(
            String             bomId,
            double             score,
            double             widthFitMm,
            double             depthFitMm,
            List<Constraint>   failedConstraints
    ) {
        /** True if every boolean condition in the requirement is satisfied. */
        public boolean fullySatisfies() { return failedConstraints.isEmpty(); }
    }
}
