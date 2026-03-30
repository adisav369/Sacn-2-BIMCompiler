package com.bim.cobol.geometry;

/**
 * A single movement operation in a crawl route.
 *
 * <p>Each op reads {@link CrawlState}, produces segment/fitting specs,
 * and returns an updated state. Ops are composed in sequence by {@link CrawlRouter}.
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.10 — CrawlOp model
public sealed interface CrawlOp
        permits FollowOp, BendOp, BranchOp, ReduceOp, PenetrateOp {

    /** Execute this op against the given state, appending results to the builder. */
    CrawlState apply(CrawlState state, CrawlRouter.ResultBuilder builder);

    /** Declarative verb line for audit logging — the auditor reads these. */
    String toVerbLine();
}
