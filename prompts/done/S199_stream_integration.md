# ⚠ DO NOT REMOVE
# Scope: S199 — Direct Stream Integration: live feedback loops across the BIM suite
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: TODO

## Context

S195–S198 proved that SQLite + `from_pydata()` streams 1M BIM elements at city
scale. The viewer reads any database with the 4-table schema:

```
elements_meta · element_instances · element_transforms · elements_rtree
```

This prompt connects the viewer to every pipeline stage — making compilation
results, cost changes, schedule updates, and design actions visible in real
time. The DB is the shared contract. Direct Stream is the universal viewer.

## Roadmap link

See `docs/ACTION_ROADMAP.md` §Phase 1 — Direct Stream Integration.
See `docs/RTree.md` §How It Works — The Technology.

## Tasks

### T1: DAGCompiler output → streamable
Add element_transforms and elements_rtree to DAGCompiler's output.db.
The compiler already writes elements_meta and element_instances. Two
additional tables make every compiled building instantly viewable.

### T2: nD phase tagging
Add columns to elements_meta: `phase_4d`, `cost_band_5d`, `carbon_tier_6d`.
The nD engine already assigns these — currently written to Excel only.
Write them to the DB so Direct Stream can color-code.

### T3: Color-by-dimension in Direct Stream
When elements have phase/cost/carbon tags, `apply_material()` can use
them instead of IFC RGBA. A dropdown in N-panel: "Color by: Material |
Phase | Cost | Carbon". Each mode applies a different color ramp.

### T4: Excel ↔ DB listener
File watcher on nD Excel output. When QS edits a cost cell, watcher
updates the DB tag, Direct Stream picks up the change on next tick.
Bidirectional: click element in viewport → highlight row in Excel.

### T5: Click-to-BOQ
Click a streamed element → N-panel shows: GUID, ifc_class, discipline,
BOM parent chain, unit cost, total cost, 4D phase, material. All from
one SQL query joining elements_meta → M_BOM_Line → M_Product → M_BOM.

## Exit criteria

1. `./scripts/rosetta_compile.sh SH` → output.db streams in Direct Stream
2. Elements colored by 4D phase — early construction red, late blue
3. Click element → BOQ line item visible in N-panel
4. Change cost in Excel → viewport color updates within 5 seconds
