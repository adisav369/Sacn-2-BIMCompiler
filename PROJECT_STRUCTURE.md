# Project Structure

Reflects this fork's actual current layout — verified against the real directory tree and each
module's own `pom.xml`, not carried over from an earlier project state. See
[README.md](README.md) for what each module is for and [CLAUDE.md](CLAUDE.md) for the rules
this all works under.

```
Sacn-2-BIMCompiler/
├── DAGCompiler/            # The compile core: DSL/BOM -> gated output.db (kept as-is)
│   ├── src/                # Java compile pipeline
│   ├── lib/
│   │   ├── input/           # Reference/extracted DBs (real IFC extractions AND point-cloud
│   │   │                    # reference DBs, side by side — same schema, same convention path)
│   │   └── output/          # Compiled output.db per building
│   └── python/
│       └── scan_to_bom/     # This fork's own work: point-cloud ingestion front end
│                            # (Phases 2-5 — see its own README for the validated pipeline)
├── IFCtoBOM/                # IFC extraction -> BOM dictionary. Its front end is what
│                            # DAGCompiler/python/scan_to_bom/ is replacing; its BOM-building
│                            # back end (ExtractionPopulator, StructuralBomBuilder,
│                            # ScopeBomBuilder, BomValidator) is kept as-is.
├── BIM_COBOL/               # Construction programming language — declarative BOM verbs
│                            # compiled to IFC (kept as-is)
├── orm-core/                # iDempiere-style BasePO + ModelQuery — shared persistence layer
│                            # for every module above (kept as-is)
├── BIMEyes/                 # Geometric comprehension engine — shape classification, spatial
│                            # proofs, cross-mode comparison
├── TopologyMaker/           # Standalone batch module — site brief -> compilable room
│                            # boundaries + wall/room prefab BOMs
├── 2D_Layout/               # 2D architectural drawing generator (floor plans, elevations,
│                            # roof plan) from a compiled database
├── ORMSandbox/              # Debug sandbox — PO classes for every DAGCompiler table, plus a
│                            # BuildingInspector utility
│
├── library/                 # Shared SQLite databases: component_library.db (product/geometry
│                            # catalog, has a known gap — see CLAUDE.md), ERP.db, per-building
│                            # *_BOM.db files, schema snapshots
├── migration/                # SQL migration scripts (append-only) against the databases above
├── config/                  # Building taxonomy, space-type, and assembly YAML config
├── examples/                 # DSL (.bim) example buildings for TopologyMaker/generative flows
├── reference/                # Original IFC corpus — ground truth the compiler's metadata
│                             # grammar must trace back to (see its own README; largely
│                             # superseded in day-to-day use by DAGCompiler/lib/input/)
├── database/                 # Schema documentation and architecture snapshots
├── deploy/
│   └── buildings/            # Per-building SQLite database fixtures
├── output/                   # Build artifacts from ad-hoc/manual compiles
├── tests/                    # tests/canonical/ — regression-anchor end-to-end tests (the gold
│                             # standard, see tests/README.md); tests/archive/ — historical
├── tools/                    # Python utility scripts (extraction, geometry, mining helpers)
│                             # + tools/sanity-checker/ (Java Phase-0 DB validator — see
│                             # TestGuide.txt)
├── scripts/                  # Build/test/run entry points, incl. run_RosettaStones.sh
│                             # (the real end-to-end driver: extract -> *_BOM.db -> compile ->
│                             # output.db -> gates) and rebuild_erp.sh (migration runner)
├── docs/                     # mkdocs site source (specs, guides — mkdocs.yml is the nav)
├── logs/                     # Pipeline run logs
│
├── _archive_cleanup_2/       # Archived, not deleted: modules/docs/scripts from the upstream
├── _archive_unwired_modules/ # project not part of this fork's scope (browser viewer, Op-Log
│                             # ERP kernel, and related surface) — see git history for how/why
│
├── CLAUDE.md                 # The actual project rules this fork works under
├── pom.xml                   # Maven aggregator — the 8 modules above
└── mkdocs.yml                # docs/ site navigation
```

## Real entry points

- **Compile an IFC building**: `./scripts/run_RosettaStones.sh classify_sh.yaml` — the real
  end-to-end driver (`scan → extract → classify → scan-to-BOM → BOM.db → compile → output.db →
  gates`, minus the point-cloud step for a real IFC file).
- **Compile a point cloud**: `DAGCompiler/python/scan_to_bom/run_scan_to_bom.py`, then the same
  `IFCtoBOMMain`/`run_RosettaStones.sh` commands — see that directory's README, "BOM assembly
  (Phase 5)" section, for the exact commands and what they've been validated against.
- **Regression tests**: `tests/canonical/` via the module's own test runner; `tests/README.md`
  documents which end-to-end scenario each canonical test anchors.
