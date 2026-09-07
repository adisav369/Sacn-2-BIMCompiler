# Production Readiness Backlog

> Scoping session, 2026-09-07. This is the itemized answer to "when is this a production-ready
> tool" — not a calendar estimate (this project's own Prime Rule is "never invent a number
> without a real source," and a date here would be exactly that). What it is instead: every
> known gap, sized by what's actually knowable about it today, plus the decisions only the
> product owner can make that determine how big several of these really are.
>
> Cross-reference: `CLAUDE.md`'s `## NEXT DEDICATED SESSIONS` section carries the two headline
> items (openings, `IfcColumn`) with their full investigation history. This file is the
> complete picture across all four dimensions of "production-ready," not just those two.

## Sizing rubric (grounded in this project's own history, not abstract T-shirt sizes)

| Size | Meaning | Real examples from this project |
|---|---|---|
| **S** | Single focused session, mechanism already understood, low rejection risk | The M_Product read/write-split fix, the UTF-8 console guard, the confidence partition — each landed in one session because the fix was concrete once found |
| **M** | Multiple sessions, bounded scope, needs a design menu + validation but the mechanism is known | The Phase 6 wall-recovery fixes (wall-starvation, round-budget exhaustion, multi-candidate accept) — several sessions, each measurable, none required inventing a new kind of approach |
| **L** | Investigation-required — the mechanism itself is contested or unknown; real risk some attempts fail | Openings and `IfcColumn` as they stand right now: **0-for-3 combined** across this session's honest attempts. Sizing "L" here is itself an estimate with real uncertainty, not a promise |
| **XL** | Not sizeable yet — requirements haven't been written down | Multi-storey detection, cross-floor stitching, generalization beyond the 4 tested scenes, all deployment/tooling items — none of these has ever had a design menu, so there's nothing to size |

---

## A. Core model accuracy (does the output represent the real building)

| # | Item | Size | Status |
|---|---|---|---|
| A1 | Openings (doors/windows), 0% recall on real scans | **L** | 2 RGB extraction designs rejected on measured false-positive rate (~2% both). 2 untried ideas logged (gradient detrending, the dataset's own `.npy` labels), neither scoped. See `CLAUDE.md` NEXT DEDICATED SESSIONS. |
| A2 | `IfcColumn`, complete class gap (0/9, 0/7) | **L** | Investigated 2026-09-07; found to be an extraction problem (no coherent segment exists to classify), not the classification problem it was picked as. 3 unimplemented options logged. See `CLAUDE.md`. |
| A3 | Wall-face reunification (a wall's inner/outer face treated as 2 elements) | **L** | One design (distance-based merge) implemented, measured, and **rejected** — harmful fusions were the majority outcome at every threshold tested. 2 untried signals logged: in-plane footprint match, empty-cavity check between faces. |
| A4 | Multi-storey detection (currently hardcoded to 1 storey per building) | **XL** | Never attempted. No design menu exists. Real buildings aren't single-storey; this is a genuine capability gap, not a polish item. |
| A5 | Cross-floor stitching (a wall spanning 2 independently-segmented floors can't unify) | **XL** | Known gap (Building A's last unmatched wall traced to exactly this). Logged as "a real, separate, larger design question" — never scoped. |
| A6 | Output-volume growth from `MULTI_CANDIDATE_K=5` (~4.8x more predicted elements at Building A's scale) | **M** | Real, honestly-reported trade-off from the round-budget fix. Candidate approaches named (consolidation in `merge_instances.py`, or a smaller K for cluttered scenes) but not designed. |
| A7 | Wall recovery ceiling (87–97%, not 100%, across 3 real buildings) | **not recommended for the backlog** | Remaining gaps individually traced to scan-coverage occlusion and compound/aggregate-GT-entity scoring edge cases — not segmentation defects. Likely diminishing returns; flag rather than schedule. |
| A8 | Phase 2 cluster-purity gap (30% of raw DBSCAN clusters mix points from >1 real element) | **M/L** | Real, unfixed, discovered while validating a later phase. Doesn't currently block anything measured, but it's real debt in the earliest stage of the pipeline. |

## B. Generalization (untested outside this project's own 4 scenes)

| # | Item | Size | Status |
|---|---|---|---|
| B1 | Every accuracy number in this project comes from 4 real scenes (B_ICU, Building A ×2 floors, Building C) + 1 synthetic house | **XL** | No 5th building has ever been run. Different architectural styles, scan densities, point qualities are completely unexplored. This is the single biggest hidden unknown behind any "production-ready" claim. |
| B2 | No documented fallback when a scan has no RGB/intensity | **S/M** | The pipeline never required color before this session; nothing breaks without it today, but there's no explicit tested policy either. |
| B3 | Tilted scans not handled (`rotation_x`/`rotation_y` assumed ≈0) | **M** | Documented open item in the LAS/LAZ ingestion section. Not attempted. |
| B4 | Material thickness/depth not measurable from a single-sided scan | **not a bug — a physics limit** | Flagged explicitly so no future session tries to "fix" this with a geometric heuristic. Real fix needs either a second scan pass or a catalog-supplied nominal thickness (extract-or-compile-only still applies — never invent one). |

## C. Already decided — NOT gaps, don't re-open these

Confirmed via source, not assumption, back in the original schema-spec phase — listing them here so the scoping session doesn't accidentally treat settled decisions as open items:

- **Multi-layer wall/slab slicing** — zero Java consumers confirmed; v1 ships single-envelope geometry per element, same LOD tradeoff the IFC pipeline already makes.
- **Room/space segmentation** — optional for v1 per spec; confirmed live (not just in theory) that `ScopeBomBuilder` degrades gracefully without it.
- **MEP element classification** — explicitly deferred per the original roadmap; embedded/occluded MEP scans poorly by nature.
- **`rel_aggregates`, `rel_adjacency`, `datum_plane`, `rel_anchored`, `rel_spans`, `port_elements`, `port_connections`** — zero Java consumers; don't build a point-cloud equivalent until something in the kept pipeline actually asks for one.

## D. Infrastructure / pipeline debt

| # | Item | Size | Status |
|---|---|---|---|
| D1 | **No real DeKH scene has been through compile/gates since the M_Product fix** — only Sample House has | **flag as highest-priority infra item** | This is the biggest gap in the "9/9 green" claim: the proof compile/gates work is real, but it's on IFC-authored/synthetic-derived data, not a real scan's output. Blocked on D4. |
| D2 | `extractIFCtoDB.py --library` mode's stale pre-S168 `M_Product` assumption | **M** | Used by `bake_all_sandbox.sh` and `pipeline_library.sh` across many buildings; changing it needs its own session per `CLAUDE.md`. |
| D3 | `library/ERP.db` / `disc_patterns.db` rename half-applied on Windows | **S/M** | Two independent files exist on this machine; code reads `ERP.db`. Untangling is a contained task. |
| D4 | Licensed-data isolation approach for running DeKH through the Java chain | **M** | Required before D1 can be closed — the Java chain writes into the tracked LFS library, and licensed third-party data must not touch it without isolation. |
| D5 | `library/schema_snapshot_component_library.sql` is stale | **S** | Declares `M_Product`, predates `Value`/`source_element_ref`. Regenerate or annotate. |

## E. Tooling / deployment maturity — every item here is XL until the decision below is made

| # | Item |
|---|---|
| E1 | No end-user CLI/UX — this is a developer running Python scripts by hand |
| E2 | No CI — only ad-hoc validation scripts (`validate_*.py`), which are real regression coverage but not automated |
| E3 | No packaging or deployment story |
| E4 | No documented scale ceiling beyond "the biggest thing tested was 437M raw points" |
| E5 | No systematic error-handling/graceful-degradation policy for pathological inputs |

---

## Decisions only the product owner can make (these determine real sizing, not preference)

1. **What does "production" mean operationally?** An internal tool you run yourself vs. something deployed for other users (single-tenant vs. multi-tenant) changes essentially every item in section E from "maybe later" to "required," or vice versa.
2. **What building types/verticals must this generalize to?** Hospitals only (matching DeKH), or arbitrary building types? This sizes B1, currently the largest unknown in the whole backlog.
3. **Is 100% wall recovery a real goal**, or is the current 87–97% (with every remaining gap individually traced to occlusion or GT-scoring edge cases) acceptable? This determines whether A7 is even in scope.
4. **Priority among A1 (openings), A2 (`IfcColumn`), A4 (multi-storey)** — which capability gap actually matters most for your intended use, since all three are real and none is small.

## Suggested next step

Not a date — a dependency-ordered starting point: **D4 (licensed-data isolation) → D1 (prove compile/gates on a real scan)** is the cheapest way to close the biggest hole in the current "proven" claim, and it's an **S/M**, not an L or XL — no open design question, just an isolation mechanism (scratch copy of the library, or a mandatory post-run restore + verification, both already named in `CLAUDE.md`). Doing that first converts today's "9/9 green on Sample House" into "9/9 green on a real terrestrial scan," which is a materially stronger claim for very little additional risk.
