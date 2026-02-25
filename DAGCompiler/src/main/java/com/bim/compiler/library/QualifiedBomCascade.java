package com.bim.compiler.library;

import java.util.ArrayList;
import java.util.List;

/**
 * QualifiedBomCascade — stateful cascade for BOM tier placement.
 * Implements VIEW_CONTRACTS.md §6 Compiler Caller Contract.
 *
 * Anti-drift rules (Phase 4c Watchdog):
 * - Tiers are always queried in order: ROOM → SET → ITEM.
 * - bom_type bind is mandatory — never query all tiers in one shot.
 * - IF placed_any on current tier: STOP. Do not drop to next tier.
 * - ORDER BY fit_priority ASC, width_mm DESC is enforced in the view query (§7).
 * - available_space_mm = MIN(widthMm, depthMm) — computed once from envelope.
 *
 * <p>Phase G-1: Renamed from BomTierResolver to free the BOMTierResolver name
 * for the unified BOM resolution engine (formerly FurnitureBOMResolver).
 */
public final class QualifiedBomCascade {

    private static final String[] TIERS = { "UNIT", "FLOOR", "ROOM", "SET", "ITEM" };

    private final ViewAccessLayer view;

    public QualifiedBomCascade(ViewAccessLayer view) {
        this.view = view;
    }

    /**
     * Resolve placement for a room envelope.
     *
     * @param widthMm  room width in mm (from v_verified_room_boundary.width_mm)
     * @param depthMm  room depth in mm (from v_verified_room_boundary.depth_mm)
     * @return ordered list of placed QualifiedBom rows (Placement Orderlines)
     */
    public List<QualifiedBom> resolve(int widthMm, int depthMm) {
        int availableSpaceMm = Math.min(widthMm, depthMm);
        return cascade(availableSpaceMm, availableSpaceMm);
    }

    private List<QualifiedBom> cascade(int availableSpaceMm, int remainingSpaceMm) {
        List<QualifiedBom> placed = new ArrayList<>();

        for (String tier : TIERS) {
            List<QualifiedBom> rows = view.getQualifiedBoms(tier, availableSpaceMm);
            boolean placedAny = false;

            for (QualifiedBom row : rows) {
                if (row.minSpaceMm() <= remainingSpaceMm) {
                    placed.add(row);
                    remainingSpaceMm -= (int) row.widthMm();
                    placedAny = true;
                    // recurse: row interior as envelope
                    List<QualifiedBom> inner = cascade(
                        (int) row.widthMm(), (int) row.depthMm());
                    placed.addAll(inner);
                }
                if (remainingSpaceMm <= 0) break;
            }

            if (placedAny) break;  // IF placed_any: STOP — do not drop tier
        }

        return placed;
    }
}
