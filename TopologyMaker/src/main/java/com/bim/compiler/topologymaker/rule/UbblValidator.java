package com.bim.compiler.topologymaker.rule;

import com.bim.compiler.topologymaker.RoomCell;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a list of RoomCells against UBBL spatial rules.
 *
 * <p>Rules are loaded from ad_spatial_rule by TopologyAccessLayer and passed
 * at construction time — no DB dependency here, fully unit-testable.
 *
 * <p>Checks implemented:
 * <ul>
 *   <li>AREA — room floor area (mm²) must be >= minValueMm (treated as mm²)</li>
 *   <li>MIN_DIM — shortest plan dimension must be >= minValueMm (mm)</li>
 *   <li>CEILING_MM — not evaluated here (ceiling height is a 3D concern); skipped</li>
 *   <li>NOT_ADJACENT — not yet implemented; skipped</li>
 * </ul>
 *
 * <p>A null roomType on a rule means it applies to all room types.
 */
public final class UbblValidator {

    /**
     * A single UBBL spatial constraint row.
     *
     * @param ruleId       PK from ad_spatial_rule
     * @param roomType     Target room type (null = all types)
     * @param constraint   One of: AREA, MIN_DIM, CEILING_MM, NOT_ADJACENT
     * @param minValueMm   Threshold value (mm or mm² depending on constraint)
     * @param ubblRef      Reference clause, for violation messages
     */
    public record UbblRule(
            String ruleId,
            String roomType,
            String constraint,
            double minValueMm,
            String ubblRef
    ) {}

    private final List<UbblRule> rules;

    public UbblValidator(List<UbblRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * Validate all cells against all rules.
     *
     * @return Empty list if fully compliant; one entry per violation otherwise.
     */
    public List<String> validate(List<RoomCell> cells) {
        List<String> violations = new ArrayList<>();

        for (RoomCell cell : cells) {
            for (UbblRule rule : rules) {
                if (!appliesToType(rule, cell.roomType())) continue;

                switch (rule.constraint()) {
                    case "AREA" -> {
                        long areaMm2 = cell.areaMm2();
                        if (areaMm2 < (long) rule.minValueMm()) {
                            violations.add(String.format(
                                "[%s] %s %s: area %.0f mm² < required %.0f mm² (%s)",
                                rule.ruleId(), cell.roomType(), cell.zoneName(),
                                (double) areaMm2, rule.minValueMm(),
                                rule.ubblRef() != null ? rule.ubblRef() : "UBBL"));
                        }
                    }
                    case "MIN_DIM" -> {
                        int dim = cell.minDimMm();
                        if (dim < rule.minValueMm()) {
                            violations.add(String.format(
                                "[%s] %s %s: min dim %d mm < required %.0f mm (%s)",
                                rule.ruleId(), cell.roomType(), cell.zoneName(),
                                dim, rule.minValueMm(),
                                rule.ubblRef() != null ? rule.ubblRef() : "UBBL"));
                        }
                    }
                    // CEILING_MM and NOT_ADJACENT skipped (3D / adjacency concerns)
                    default -> { /* intentionally ignored */ }
                }
            }
        }

        return violations;
    }

    private boolean appliesToType(UbblRule rule, String cellType) {
        return rule.roomType() == null || rule.roomType().equals(cellType);
    }
}
