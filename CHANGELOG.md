# Changelog

Changes to this fork's own work — the Scan-to-BIM compiler (see [README.md](README.md)).
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). The upstream project's
own changelog (browser viewer, Op-Log ERP kernel) isn't this repo's concern anymore; that
history lives in `_archive_unwired_modules/` and the upstream project directly, not here.

## [Unreleased] — 2026-09-03

### Added
- Point-cloud ingestion front end (`DAGCompiler/python/scan_to_bom/`), Phases 2 through 5:
  RANSAC/DBSCAN segmentation, coplanar-fragment reunification, geometry-only IFC-class
  classification, furniture instance-merging, and a reference-DB writer that runs through the
  real, unmodified `IFCtoBOMMain` Java pipeline. Every phase blind-validated against held-out
  ground truth — see that directory's own README for the full, honest results (including what
  doesn't work yet).
- Cluster-geometry window detection, precision 0.68 / recall 0.70 on held-out ground truth
  (previously 0% — no path to `IfcWindow` existed for non-planar segments at all).
- `docs/ScanToBOM_ReferenceDB_Spec.md` — the Phase 1 reference-DB schema contract every later
  phase is built and validated against.
- `remove_disciplines` YAML override, wired up end-to-end (`ClassificationYaml.java`,
  `IFCtoBOMPipeline.java`, `CompilationPipeline.java`) — lets a building opt out of a
  category-default MEP discipline it has no real geometry for, rather than fabricate
  placements for it. First real use: `SampleHouse` opting out of `FP` (no real sprinkler
  source geometry in this checkout).

### Fixed
- `library/component_library.db`: `ad_geometry_map` renamed to `I_Geometry_Map`, closing a
  real schema-naming gap that broke `IFCtoBOMMain --populate` outright.

### Changed
- `CLAUDE.md`, `README.md`, `CONTRIBUTING.md`, `PROGRESS.md`, `PROJECT_STRUCTURE.md` rewritten
  to describe this fork's actual, narrower scope rather than the upstream project's full scope
  — some of this had been sitting as uncommitted local-only state since a cleanup session weeks
  before this one and was committed for the first time in this batch (see git log around
  `e7025c22f`..`c1cf0d64c` for the full audit trail, including a separate root-cause of why that
  state was invisible).
- `pom.xml`, `mkdocs.yml`: drop `BIMBackOffice`/`BonsaiBIMDesigner` as Maven modules and trim
  docs nav to match, since those modules moved to `_archive_cleanup_2/`.
- ~2,900 files (`_archive_cleanup_2/`, `_archive_unwired_modules/`) — modules and content not
  part of this fork's scope, archived rather than deleted, committed for the first time.
