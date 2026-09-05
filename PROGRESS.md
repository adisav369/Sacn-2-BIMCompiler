# PROGRESS — Current Development State

> **Rule:** thin status file. Detail lives in `DAGCompiler/python/scan_to_bom/README.md` (the
> point-cloud pipeline's own validated-results doc) and `CLAUDE.md` (project rules + known
> gaps). Keep this file short — a pointer to where the real detail lives, not the detail itself.

## Chain status (2026-09-05): running end-to-end again

`./scripts/run_RosettaStones.sh classify_sh.yaml` is **9/9 gates ALL GREEN** — populate →
BOM.db → all 18 `BomValidator` QA gates → compile → output.db → integrity/clash/C8/C9
fidelity — from a byte-clean HEAD `library/component_library.db`, in one run. "The architecture
bet is proven" is a present-tense statement again.

This supersedes the 2026-09-04 warning that the chain did not run. Two defects were blocking it,
both now fixed in code, and **neither was the missing-migration problem previously assumed** —
see `CLAUDE.md`'s "M_PRODUCT — ROOT CAUSE" section, which corrects a diagnosis that was wrong.
One caveat for a genuinely fresh checkout: the reference extraction has to be regenerated once
from the committed IFC (single command, in that same section) before the chain is green.

## Current state

**Point-cloud front end** (`DAGCompiler/python/scan_to_bom/`) — Phases 2 through 6 built and
blind-validated against held-out ground truth (never the pipeline's own numbers):
segmentation, coplanar-fragment reunification, geometry-only IFC-class classification,
furniture instance-merging, reference-DB writing, and (Phase 5) running that DB through the
real, unmodified `IFCtoBOMMain` Java pipeline. Real, measured
results (not aspirational) are in that README. Phase 6 took it to real terrestrial LiDAR scans
(DeKH, 3 real buildings, 359M-621M raw points each) and fixed four real segmentation defects
found there — wall-starvation, an any-overlap scoring criterion, round-budget exhaustion, and
single-winner-per-round candidate selection. Current wall recovery: 29/31 (B_ICU), 11/16
(Building C), 69/72 (Building A). B_ICU and Building C's numbers predate the last two fixes and
are pending a re-run.

**Compile back end** (`DAGCompiler`, `BIM_COBOL`, `orm-core`, `IFCtoBOM`'s BOM-building side) —
kept as-is per `CLAUDE.md`, and **verified running again 2026-09-05**: Sample House compiles
clean through `./scripts/run_RosettaStones.sh classify_sh.yaml`, all 18 `BomValidator` QA checks
passing, 9/9 gates, 78 elements across 3 storeys, C8 geometry-diversity and C9 per-axis
dimensional fidelity both green. (This claim was correctly retracted on 2026-09-04 when it had
stopped reproducing; it is restored here because it was re-measured, not because it was
re-assumed.)

## Open

- **`M_Product` — RESOLVED 2026-09-05.** Root cause was not a missing migration: change S168
  moved every `M_Product` write to `ERP.db` but left `ProductRegistrar.ensureProductImages` and
  `countUnlinkedProducts` still *reading* it from `component_library.db`. Both now `ATTACH` the
  ERP database and read `erp.M_Product`. The previously-catalogued "~13 migration files, order
  unknown" investigation turned out to be the wrong question — `M_Product` must NOT be put back
  into `component_library.db` (doing so breaks `ensureProducts` with `no such column: Value`,
  tested). Full reasoning and the three independent sources that settle it: `CLAUDE.md`.
- **Dangling geometry refs — RESOLVED 2026-09-05.** A second, independent pre-existing blocker
  behind the first: all 90 Sample House `I_Geometry_Map` rows pointed at `component_geometries`
  entries that were never imported, and `MetadataValidator` rejected the whole compile.
  `ExtractionPopulator.repairDanglingGeometry` now imports those blobs from the building's own
  reference extraction (exact hash match, never synthesised; unresolvable hashes are reported).
- **Smaller items left open by that session** — `extractIFCtoDB.py --library` mode still holds
  the pre-S168 assumption; the library repair (+51 blobs, +50 image rows) is regenerable and
  deliberately uncommitted; `library/ERP.db` is gitignored yet hardcoded, and the de-ERP rename
  to `disc_patterns.db` is half-applied on Windows. All catalogued in `CLAUDE.md`'s
  "STILL OPEN" section. None of them block the chain.
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
