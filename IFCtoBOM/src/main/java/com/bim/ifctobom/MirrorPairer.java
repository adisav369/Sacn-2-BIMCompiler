package com.bim.ifctobom;

import com.bim.ifctobom.ExtractionReader.ExtractionElement;

import java.util.List;

/**
 * Pairs A-side elements with their B-side counterparts across a mirror plane.
 *
 * <p>Given two groups of the same product type, returns matched pairs
 * ordered by spatial proximity. The pairing strategy is pluggable —
 * implementations may use sort-and-zip, proximity matching, or other
 * algorithms depending on the building's symmetry model.
 *
 * @see ProximityMirrorPairer
 */
@FunctionalInterface
public interface MirrorPairer {

    /** An A↔B element pair. */
    record ElementPair(ExtractionElement a, ExtractionElement b) {}

    /**
     * Pair A-side elements with B-side counterparts.
     *
     * @param aGroup  elements entirely on the A-side (below/left of mirror)
     * @param bGroup  elements entirely on the B-side (above/right of mirror)
     * @return paired list of size {@code min(aGroup, bGroup)}; excess elements are not included
     */
    List<ElementPair> pair(List<ExtractionElement> aGroup, List<ExtractionElement> bGroup);
}
