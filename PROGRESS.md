# PROGRESS — Current Development State

> **Rule:** thin status file. Detail lives in `DAGCompiler/python/scan_to_bom/README.md` (the
> point-cloud pipeline's own validated-results doc) and `CLAUDE.md` (project rules + known
> gaps). Keep this file short — a pointer to where the real detail lives, not the detail itself.

## Current state

**Point-cloud front end** (`DAGCompiler/python/scan_to_bom/`) — Phases 2 through 5 built and
blind-validated against held-out ground truth (never the pipeline's own numbers):
segmentation, coplanar-fragment reunification, geometry-only IFC-class classification,
furniture instance-merging, reference-DB writing, and running that DB through the real,
unmodified `IFCtoBOMMain` Java pipeline. Real, measured results (not aspirational) are in that
README — most recently: cluster-geometry window detection (precision 0.68 / recall 0.70 on
held-out ground truth) and a full audit that found and closed out ~2,900 files of uncommitted,
invisible local state (see git log around `e7025c22f`..`c1cf0d64c`).

**Compile back end** (`DAGCompiler`, `BIM_COBOL`, `orm-core`, `IFCtoBOM`'s BOM-building side) —
kept as-is per `CLAUDE.md`; Sample House (`classify_sh.yaml`) compiles clean through
`./scripts/run_RosettaStones.sh`, all `BomValidator` QA checks passing.

## Open

- **`library/component_library.db` is missing `M_Product`** (real, confirmed, not stale — see
  `CLAUDE.md`'s "KNOWN PRE-EXISTING GAP"). ~13 candidate migration files, order unknown.
  Deliberately not guessed through — needs its own dedicated session.
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
