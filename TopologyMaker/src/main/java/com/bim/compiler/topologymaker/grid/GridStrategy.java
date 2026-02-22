package com.bim.compiler.topologymaker.grid;

import com.bim.compiler.topologymaker.RoomCell;
import com.bim.compiler.topologymaker.SiteEnvelope;

import java.util.List;

/**
 * Strategy that subdivides a SiteEnvelope into an ordered list of RoomCells.
 * Implementations are keyed by grid_strategy in ad_typology_pattern.
 *
 * Open/Closed: new strategies are added to the STRATEGIES map in
 * TopologyBatchProcess without changing existing code.
 */
public interface GridStrategy {

    /**
     * Subdivide the site into room cells according to this strategy.
     * The returned list is ordered: service zones first, sleeping zones last.
     *
     * @param site       Site dimensions and programme brief
     * @param zoneJson   Raw JSON string from ad_typology_pattern.zone_json
     * @return           Non-empty ordered list of RoomCell entries
     */
    List<RoomCell> subdivide(SiteEnvelope site, String zoneJson);

    /** Matches ad_typology_pattern.grid_strategy column value. */
    String strategyId();
}
