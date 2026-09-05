# Scan-to-BIM Compiler — Project Rules

## GOAL
Build a Scan-to-BIM compiler by extending the existing BIMCompiler codebase.
Replace the IFC-parsing front end (IFCtoBOM) with point-cloud ingestion.
Keep the BOM/verb/compile/gate back end unchanged.

## PIPELINE
scan → extract → classify → scan-to-BOM → BOM.db → compile → output.db → gates

## PRIME RULE
Extract or compile only. Never invent. Every number traces to a real source
(scan measurement or catalog), never a guessed default.

## MODULES — KEEP AS-IS (do not rewrite)
DAGCompiler, BIM_COBOL, orm-core — the compile core and verb engine.

## MODULES — BEING REPLACED
IFCtoBOM's IFC-parsing front end → new point-cloud ingestion (our new work).

## RESOLVED 2026-09-05 — the full chain runs end-to-end again (was: "does NOT run at HEAD")
`./scripts/run_RosettaStones.sh classify_sh.yaml` is **9/9 gates ALL GREEN** from a
byte-clean HEAD `library/component_library.db`, in a single run: populate → BOM.db →
all 18 `BomValidator` QA gates → compile → output.db → integrity/clash/C8/C9 fidelity.
The `PIPELINE` line above is reproducible again. Two independent defects were blocking it;
both are fixed in code, neither was the missing-migration problem this file previously
assumed. See "M_PRODUCT — ROOT CAUSE" below before touching any migration.

## M_PRODUCT — ROOT CAUSE FOUND AND FIXED (2026-09-05); the earlier diagnosis here was WRONG
**Do not replay migrations to "restore" `M_Product` into `component_library.db`. It does not
belong there, and adding it actively breaks the pipeline — measured, not argued.**

This file previously said `M_Product` "likely does belong in component_library.db" and that
fixing it meant working out the order of ~13 migrations. That was a guess, and it was wrong.
The real defect was a two-line inconsistency left behind by change S168:

- S168 moved all `M_Product` **writes** to `ERP.db` (`ProductRegistrar.ensureProductCatalog`
  writes to `discConn`; its own class javadoc states "M_Product writes go ONLY to ERP.db").
- But `ProductRegistrar.ensureProductImages` and `countUnlinkedProducts` were left
  **reading** `M_Product` from `compConn` (component_library.db), where the table correctly
  no longer exists. Every `--populate`/`--classify` run aborted there with
  `no such table: M_Product` — at `ProductRegistrar.java:303`, called from
  `IFCtoBOMMain.java:143` / `IFCtoBOMPipeline.java:239`.
- `bridgeSourceElementRef`, sitting between them, already used `discConn` correctly — which is
  exactly why the inconsistency survived: the surrounding code looked right.

**Fix:** both readers now `ATTACH` the ERP database (path read off `discConn` itself via
`PRAGMA database_list`, never hardcoded a second time) and select from `erp.M_Product`. The
SQL is otherwise untouched **on purpose** — 35 of Sample House's 40 products match more than
one `I_Geometry_Map` row (up to 16 distinct `geometry_hash` values), so `GROUP BY p.Value`
picks one arbitrarily; reimplementing the join as a two-phase Java loop would have silently
changed which geometry each product compiles to.

**Evidence that `M_Product` belongs only in ERP.db** — three independent sources agree:
1. `scripts/rebuild_erp.sh` (the authoritative from-scratch rebuild) creates `M_Product`
   directly in the ERP database at its Phase 6, explicitly **skips DV015**, and filters the
   `M_Product` statements out of S62 with the comment "M_Product doesn't exist yet".
2. `ProductRegistrar.ensureProducts`'s own javadoc documents its catalog parameter as
   "read connection to ERP.db (master product catalog)", and its SQL selects `Value` —
   a column the extraction-side `M_Product` has never had.
3. Tested directly: creating an extraction-side `M_Product` in component_library.db (from the
   tracked schema snapshot + DV033's `source_element_ref`) makes `ensureProducts` find a table
   it then cannot read, and the pipeline fails with `no such column: Value`. It only worked
   before because `tableExists(compConn, "M_Product")` silently degraded to the no-catalog path.

`DV015_move_m_product.sql` does presuppose a `comp.M_Product` (it `ATTACH`es
component_library.db and copies from it), so such a table existed historically. It is
superseded: `rebuild_erp.sh` skips DV015 outright. **`library/schema_snapshot_component_library.sql`
still declares `M_Product` and is stale on this point** — it also predates both `Value`
(CL004) and `source_element_ref` (DV033). Trust `rebuild_erp.sh` over the snapshot.

Also checked and NOT applied: `CL001_drop_dead_tables.sql` drops `ad_product_dim` calling it a
"duplicate of M_Product (same schema, same purpose)". That comment is false — `ad_product_dim`
is a 52-row authored design-rules table (clearances, fitting rules, quantity-per-area/room,
connection points) sharing only `product_id`/`product_type`/`w`/`d`/`h` with `M_Product`.
Applying CL001 would destroy real data. Leave it alone.

## SECOND BLOCKER, ALSO FIXED (2026-09-05) — dangling geometry refs
With M_Product fixed, the compile still failed:
`MetadataValidator` → "I_Geometry_Map.geometry_hash: 90 dangling refs to component_geometries"
(all 90 Sample House rows, 51 distinct hashes). Pre-existing at HEAD, not caused by any of
this work — verified by diffing the HEAD library against the post-run one (identical counts).

Cause: `ExtractionPopulator.fillGeometryGaps` is a **gap** fill — it only considers
`element_ref`s ABSENT from `I_Geometry_Map`. A row that is already present but whose
`geometry_hash` has no `component_geometries` entry is revisited by nothing, so it stayed
dangling forever and the compiler rejected the whole building.

**Fix:** new `ExtractionPopulator.repairDanglingGeometry`, run right after the gap fill. It
finds present-but-dangling hashes for the building and imports each blob through the existing,
already-tested `ensureGeometryBlob` helper. Extract-only, never invent: blobs are copied
verbatim from the building's own reference extraction on an exact hash match; a hash the
reference DB does not carry is left dangling and **reported**, never synthesised (it re-checks
after importing rather than trusting the import count, because `ensureGeometryBlob` is
deliberately silent when a blob is NULL).

One prerequisite, worth knowing before re-running any building: the extractor's `--library`
mode (S168) writes mesh BLOBs into the library and leaves `base_geometries` **hash-only**
(NULL vertices/faces). `DAGCompiler/lib/input/SampleHouse_extracted.db` had been produced that
way, so its 51 blobs existed in neither place and the repair had nothing to import. Re-extracting
WITHOUT `--library` puts the real meshes in the reference DB, and the repair then imports all
of them (`Repaired 50 dangling geometry ref(s) ... 0 still unresolved`):

```
python DAGCompiler/python/extractIFCtoDB.py --ifc DAGCompiler/lib/input/IFC/Ifc4_SampleHouse.ifc \
    -o DAGCompiler/lib/input/SampleHouse_extracted.db
```

`*_extracted.db` is gitignored, so this is a free, repeatable regeneration from the committed
IFC. After it, one `./scripts/run_RosettaStones.sh classify_sh.yaml` self-heals the library and
goes 9/9 green.

## STILL OPEN after 2026-09-05 (smaller, precisely characterised — none block the chain)
- **`extractIFCtoDB.py --library` mode is broken against a HEAD library.** `_open_library`
  hard-requires `M_Product` in component_library.db and the extractor writes `M_Product` rows
  there — the last code still holding the pre-S168 assumption. Since M_Product must NOT be
  added to that DB (see above), the requirement and those writes are what should go. Not done
  here: `--library` is used by `scripts/bake_all_sandbox.sh` and `scripts/pipeline_library.sh`
  across many buildings, so changing it needs its own session. Workaround: extract without
  `--library` and let `repairDanglingGeometry` move the blobs.
- ~~The library repair is not committed.~~ **Committed 2026-09-05 (`a81f66ded`).** A fresh
  checkout now runs 9/9 green with no manual step. The committed change is purely additive and
  was audited row-by-row: `component_geometries` +51 real mesh blobs (0 NULL), `M_Product_Image`
  +50 link rows, `I_Geometry_Map` +10 (generative-fixture restores + SampleHouse guid entries),
  plus the pure-alias view `ad_geometry_map AS SELECT * FROM I_Geometry_Map`. Zero DeKH-derived
  rows — verified explicitly, the 2026-09-04 contamination is not present and was not
  reintroduced. It is the CONVERGED state, not a mid-repair snapshot: the chain was run three
  times and compared on a content fingerprint (schema + per-table sorted-row hash, ignoring
  SQLite page churn); runs 2 and 3 are logically identical, so the file is a fixed point and
  does not keep growing. The re-extract command above is therefore only needed if the reference
  DB is ever regenerated in `--library` (hash-only) mode again.
- **`scripts/restore_generative_meshes.py` crashes on a non-UTF-8 console, and the crash is
  silent-ish.** It creates its back-compat `ad_geometry_map` view and then dies on the very next
  line printing "§SELF-HEAL ... →" — a Windows `cp1252` encoding error — aborting before the
  generative-mesh restore actually runs. That is why the repair took two passes to converge:
  run 1 created the view and died, run 2 restored the 10 fixture meshes. Now latent (with the
  view committed that branch is skipped), but it will bite anyone whose library lacks the view.
  Not fixed here. Same class as the `PYTHONIOENCODING=utf-8` need for the Python validators.
- **`library/ERP.db` is gitignored and NOT in a fresh checkout**, yet the Java chain hardcodes
  it (`IFCtoBOMMain.java:122`, `IFCtoBOMPipeline.java:109`) and `M_Product` now lives there.
  `scripts/rebuild_erp.sh` regenerates it — but builds `library/disc_patterns.db` and then
  `ln -sf`s `ERP.db` to it, which does not produce a symlink on Windows. This machine has two
  independent files (ERP.db 5,753 products; disc_patterns.db 17), and the code reads ERP.db.
  Untangling the de-ERP rename is its own task.
- `library/schema_snapshot_component_library.sql` is stale (declares `M_Product`, predates
  `Value` and `source_element_ref`). Regenerate or annotate it.
- The extractor's own `§PROOF` gate reports `LOD400_ENVELOPE 1/8 multi-layer elements shipped
  as an envelope solid` for Sample House. Pre-existing IFC-authoring content issue, unrelated
  to the above; the chain is green regardless.

## STANDING METHODOLOGY RULE — validate on predicted→GT attribution, never GT-to-GT
Applies to ALL measurement-driven design work in this project, not just the one finding that
produced it. When measuring whether some proposed rule (a merge threshold, a match criterion,
a geometric guard) will behave correctly, the measurement must be run over **predicted
segments, each attributed to the ground-truth element it actually covers** — never over
ground-truth-to-ground-truth relationships.

Reason: ground truth in these models has properties real predicted segments do not share —
it is axis-aligned, watertight, one-element-per-object, and noise-free. Any proxy or heuristic
validated against GT alone is being tested on the easy case only, and will look sound while
being invalid on the data it will actually run on.

Confirmed twice, both times where the flawed measurement looked *more* convincing than the
correct one:
1. **Wall-face merge (2026-09-04)** — used the AABB minimum-extent axis as a proxy for wall
   thickness direction. Valid only for axis-aligned walls; validated against GT walls, which
   are axis-aligned, so it reported *zero* wrong fusions at 0.10m. Predicted segments include
   diagonal-in-plan walls where AABB-min-extent is a projection artifact (median 0.766m, max
   3.703m). Re-measuring with actual plane normals reversed the conclusion entirely: harmful
   fusions were the majority outcome at every threshold. See the rejected-merge section in
   `DAGCompiler/python/scan_to_bom/README.md`.
2. **Spatial match criterion** — "any AABB overlap counts" scored fine against GT-shaped
   boxes but credited fragmented predictions that barely touched the real element; replaced
   with true volume-coverage (`_union_coverage_fraction`) measured predicted-against-GT.

Practical form: attribute each prediction to its dominant GT element first, then evaluate the
rule on those attributed pairs. If a measurement can only be expressed GT-to-GT, that is a
signal the proposed rule has not yet been stated in terms of what the pipeline actually sees.
