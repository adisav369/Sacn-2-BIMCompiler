package com.bim.cobol.geometry;

import com.bim.orm.BIMLogger;

import java.util.List;

/**
 * BRANCH — split path at current position. Produces 1 × tee/wye fitting
 * and spawns a sub-crawl for each branch target.
 *
 * <p>The main crawl state is unchanged — branching only records the fitting
 * and stores sub-route ops for the caller to execute.
 *
 * @param branchOps ops for each branch route (executed as sub-crawls)
 * @param branchDiameterMm diameter for branch routes (may differ from main)
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T1.2 — Witness: W-BRANCH-1
public record BranchOp(List<List<CrawlOp>> branchOps,
                        double branchDiameterMm) implements CrawlOp {

    private static final String TAG = "CRAWL";

    @Override
    public CrawlState apply(CrawlState state, CrawlRouter.ResultBuilder builder) {
        String fittingType = branchOps.size() > 1 ? "WYE" : "TEE";
        BIMLogger.fine(TAG, "BRANCH: pos={} fitting={} mainDia={:.0f}mm branchDia={:.0f}mm branches={}",
                state.position(), fittingType, state.diameterMm(), branchDiameterMm, branchOps.size());
        builder.addFitting(new CrawlRouter.FittingSpec(
                state.position(), fittingType, state.diameterMm(),
                branchDiameterMm, 0));

        // Execute each branch sub-route
        for (int b = 0; b < branchOps.size(); b++) {
            List<CrawlOp> branch = branchOps.get(b);
            CrawlState branchState = new CrawlState(
                    state.position(), state.direction(),
                    branchDiameterMm, state.product(), state.discipline());
            BIMLogger.fine(TAG, "BRANCH: sub-route {} start pos={} ops={}", b, branchState.position(), branch.size());
            for (CrawlOp op : branch) {
                branchState = op.apply(branchState, builder);
            }
            BIMLogger.fine(TAG, "BRANCH: sub-route {} done pos={}", b, branchState.position());
        }

        // Main crawl continues unchanged
        return state;
    }
}
