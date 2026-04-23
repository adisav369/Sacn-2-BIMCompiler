package com.bim.compiler.dsl;

import com.bim.compiler.dsl.PlacementLoader.Placement;

import java.util.List;

/**
 * Post-placement correction pass that detects and fixes spatial anomalies
 * in the compiled output without referencing IFC input data.
 *
 * <p>The compiler applies a single transform (rot=π) to all B-side elements,
 * but some elements (static envelope walls) shouldn't be rotated. Instead of
 * per-element classification at extraction time, this corrector reasons about
 * the output geometry: if a wall covers openings that should be visible,
 * the wall is shifted and needs correction.
 *
 * <p>Pluggable — implementations can add domain-specific corrections.
 */
@FunctionalInterface
public interface LastInchCorrector {

    /**
     * Correct spatial anomalies in the placement list.
     *
     * @param placements  all placements for a building (mutable list — may be modified in place)
     * @return corrected placements (may be the same list with modified entries)
     */
    List<Placement> correct(List<Placement> placements);
}
