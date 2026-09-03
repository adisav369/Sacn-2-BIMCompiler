# Scan-to-BIM Compiler

A fork of [red1oon/BIMCompiler](https://github.com/red1oon/BIMCompiler) narrowed to one goal:
**compile a real point-cloud scan into a verified BIM database**, using the original project's
deterministic compile/BOM/gate back end unchanged.

The upstream project is a browser-native BIM viewer + ERP kernel + IFC compiler (see its own
README for that scope). This fork keeps only the compiler core and replaces its IFC-parsing
front end with point-cloud ingestion — everything specific to the browser viewer and the
Op-Log ERP kernel has been moved out of this checkout (`_archive_unwired_modules/`,
`_archive_cleanup_2/`) rather than deleted, since it isn't this project's concern but may still
be useful reference.

## Goal and prime rule

Extract or compile only. **Never invent** — every number in a compiled building traces to a
real source: a scan measurement, or a catalog entry. See [CLAUDE.md](CLAUDE.md) for the full
project rules this repo is built against.

```
scan → extract → classify → scan-to-BOM → BOM.db → compile → output.db → gates
```

- **Being replaced**: `IFCtoBOM`'s IFC-parsing front end. The new point-cloud front end lives
  in [`DAGCompiler/python/scan_to_bom/`](DAGCompiler/python/scan_to_bom/README.md) — point-cloud
  segmentation (RANSAC + DBSCAN), geometry-only IFC-class classification, instance merging, and
  a reference-DB writer that feeds the existing Java pipeline unmodified. See that directory's
  own README for the full, validated pipeline (Phases 2 through 5).
- **Kept as-is**: `DAGCompiler` (the compile core, gates, verb engine), `BIM_COBOL` (the domain
  verb language), `orm-core` (shared persistence layer) — none of this needed to change for a
  point cloud to become a valid input, which is the point of keeping the extraction front end
  and the compile back end cleanly separated.

## Modules

| Module | What it is |
|---|---|
| `DAGCompiler` | The compile pipeline: DSL/BOM → gated `output.db`. Also houses `python/scan_to_bom/`, this fork's own point-cloud front end. |
| `BIM_COBOL` | Construction programming language — declarative BOM verbs compiled to IFC. |
| `orm-core` | iDempiere-style `BasePO` + `ModelQuery` — shared persistence layer for every module below. |
| `IFCtoBOM` | IFC extraction → BOM dictionary, classification-YAML driven. The module whose front end this fork is replacing. |
| `BIMEyes` | Geometric comprehension engine — shape classification, spatial proofs, cross-mode comparison. |
| `TopologyMaker` | Standalone batch module — site brief → compilable room boundaries + wall/room prefab BOMs. |
| `2D_Layout` | Generates 2D architectural drawings (floor plans, elevations, roof plan) from a compiled database. |
| `ORMSandbox` | Debug sandbox — PO classes for every `DAGCompiler` table, plus a building inspector utility. |

Each module's own `pom.xml` `<description>` is the source for the one-liners above — read there
directly for anything more specific.

## Quick start

```bash
# Prerequisites: Java 17+, Maven 3.8+, SQLite3, Python 3.10+ (for the point-cloud front end)
mvn compile -q                                    # compile all modules
./scripts/run_RosettaStones.sh classify_sh.yaml   # compile Sample House (real IFC) + verify gates
```

To run a point cloud through the same back end instead of an IFC file, see
[`DAGCompiler/python/scan_to_bom/README.md`](DAGCompiler/python/scan_to_bom/README.md)'s
"Running it" section — it ends with the same `IFCtoBOMMain`/`run_RosettaStones.sh` commands
above, just pointed at a point-cloud-derived reference DB instead of an IFC extraction.

## Known gaps

See [CLAUDE.md](CLAUDE.md)'s "KNOWN PRE-EXISTING GAP" section for the current, precise state of
`library/component_library.db`'s `M_Product` table — a real, unresolved gap, deliberately not
guessed through. See `DAGCompiler/python/scan_to_bom/README.md`'s own "What's still not done"
section for the point-cloud pipeline's own open items (plane-fragmentation merging, room/space
segmentation, MEP classification, and others), each with the real evidence behind it.

## License

MIT. Copyright (c) 2025-2026 Redhuan D. Oon (original project). See [LICENSE](LICENSE).
