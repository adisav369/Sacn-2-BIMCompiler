# PROGRESS — Current Development State

> **Rule:** thin status file. Detail lives in `DAGCompiler/python/scan_to_bom/README.md` (the
> point-cloud pipeline's own validated-results doc) and `CLAUDE.md` (project rules + known
> gaps). Keep this file short — a pointer to where the real detail lives, not the detail itself.

## ⚠️ Read this before citing any status below

**The full chain does NOT currently run end-to-end from a fresh checkout.** The Java BOM back
end aborts on `no such table: M_Product` (see "Open" below) — for *every* building, at *every*
scale, not just point-cloud input. Verified 2026-09-04 by running the known-good 135-element
`classify_shpc.yaml` case, which fails at the same place as a 5,543-element DeKH one.

So: the front end's measured results below are real and current, but **"the architecture bet is
proven" is a historical statement, not a present-tense one** — it was proven once (Phase 5, when
`component_library.db` still had `M_Product`), and is not reproducible today at HEAD. Don't read
the point-cloud accuracy numbers and assume a working scan→BOM→compile→gates chain right now.

## Current state

**Point-cloud front end** (`DAGCompiler/python/scan_to_bom/`) — Phases 2 through 6 built and
blind-validated against held-out ground truth (never the pipeline's own numbers):
segmentation, coplanar-fragment reunification, geometry-only IFC-class classification,
furniture instance-merging, reference-DB writing, and (Phase 5, historically — see the warning
above) running that DB through the real, unmodified `IFCtoBOMMain` Java pipeline. Real, measured
results (not aspirational) are in that README. Phase 6 took it to real terrestrial LiDAR scans
(DeKH, 3 real buildings, 359M-621M raw points each) and fixed four real segmentation defects
found there — wall-starvation, an any-overlap scoring criterion, round-budget exhaustion, and
single-winner-per-round candidate selection. Current wall recovery: 29/31 (B_ICU), 11/16
(Building C), 69/72 (Building A). B_ICU and Building C's numbers predate the last two fixes and
are pending a re-run.

**Compile back end** (`DAGCompiler`, `BIM_COBOL`, `orm-core`, `IFCtoBOM`'s BOM-building side) —
kept as-is per `CLAUDE.md`. **Currently blocked at HEAD by the missing `M_Product` table** — the
prior claim here that Sample House "compiles clean through `./scripts/run_RosettaStones.sh`,
all `BomValidator` QA checks passing" was true when written but does NOT reproduce today; it has
been corrected rather than carried forward stale.

## Open

- **`library/component_library.db` is missing `M_Product` — now the single highest-priority
  open item in the project.** (Real, confirmed, not stale — see `CLAUDE.md`'s "KNOWN
  PRE-EXISTING GAP".) **Reprioritized 2026-09-04**: this was previously catalogued as an
  independent, low-priority side issue that "doesn't block anything Scan-to-BIM-specific." That
  is no longer true and was wrong to leave standing — it blocks the entire back half of the
  project's own stated pipeline (`scan → ... → BOM.db → compile → output.db → gates`), for
  every building, at every scale. Everything downstream of `ProductRegistrar`'s pre-flight
  (`IFCtoBOMPipeline.java:239-266`) — BOM assembly, `BomValidator`'s 18 QA gates, compile,
  output.db, gates — is unverifiable until it's fixed. ~13 candidate migration files, order and
  supersession unknown. Still deliberately not guessed through: it needs its own dedicated
  session, which is precisely why it should get one soon rather than being squeezed into the
  end of another task.
- **Running the Java chain writes into the tracked, LFS-stored `library/component_library.db`.**
  Found 2026-09-04: a single `--classify` run on DeKH input wrote 5,531 DeKH-derived rows into
  `I_Geometry_Map` in that tracked file (reversed via `git checkout --`, after first verifying
  HEAD's LFS object still carried the documented `I_Geometry_Map` rename fix). Any future run of
  licensed third-party data through the Java chain will contaminate a tracked file the same way
  — needs an isolation approach (scratch copy of the library, or a mandatory post-run restore)
  before DeKH input can be put through the BOM back end again.
- **Point-cloud pipeline's own open items** — see `DAGCompiler/python/scan_to_bom/README.md`'s
  "What's still not done" section for the current, honest list (plane-fragmentation merging
  across occluded gaps, room/space segmentation, tilted-scan handling, MEP classification, door
  detection for cluster-geometry segments) — each with the ground-truth evidence behind it, not
  just a TODO.
- **`docs/` still carries a lot of the upstream project's broader scope** (ERP, offline
  install, browser viewer guides) that this fork's `mkdocs.yml` nav no longer links to but
  hasn't been archived out the way the top-level modules were (see `_archive_unwired_modules/`,
  `_archive_cleanup_2/`). Not yet cleaned up.

## Fleet gate status

Not currently re-verified against the full building fleet — the prior status line here
(building pass/fail counts) predated the meta-docs rewrite and wasn't re-checked before being
replaced, so it's been removed rather than carried forward stale. Re-run
`./scripts/run_RosettaStones.sh` (no args, all `classify_*.yaml`) for a current read before
citing a number here.
