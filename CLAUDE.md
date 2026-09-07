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
- ~~`scripts/restore_generative_meshes.py` crashes on a non-UTF-8 console.~~ **FIXED
  2026-09-05 (`b3fa701a8`), repo-wide.** Two layers: every `.py` under `scripts/`, `tools/`
  and `DAGCompiler/python/` that prints a non-ASCII literal (88 files) now reconfigures its
  own stdout/stderr to UTF-8 with `errors="replace"` before anything else, so it holds however
  the script is launched; and the 9 shell entry points export `PYTHONUTF8=1` +
  `PYTHONIOENCODING=utf-8`, which also covers non-ASCII arriving at RUNTIME (a material name,
  an IFC family, a path) that the literal scan cannot see. Verified by dropping the
  `ad_geometry_map` view to force the fatal branch and re-running on a real cp1252 stdout with
  no env override: completes, exit 0. **If you add a script that prints, you get this for free
  through the shell entry points, but add the guard block too if it can be run directly.**
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

## NEXT SESSION PLAN (decided 2026-09-07) — sequenced, not just an open backlog
Production bar, target category, wall-recovery ceiling, and priority order were all decided by
the product owner on 2026-09-07 (see `PRODUCTION_READINESS_BACKLOG.md`'s `## Decisions` and
`## SEQUENCED SESSION PLAN` sections for full reasoning — this is the condensed version to open
a session with). **Production bar = pilot-ready for a real client's building** (mostly-correct,
human review/touch-up expected on known-weak parts, not full autonomy). **Target category =
institutional/commercial only** (hospitals, offices — DeKH's own category; not generalizing
beyond it yet). **87–97% wall recovery is accepted, closed, not pursued further.**

**Do these in order. Don't skip ahead, don't resume a rejected design mid-idea.**

1. **D4 → D1 — prove a real DeKH scan through the full Java chain (S/M).** Isolation mechanism
   for the tracked LFS library (scratch copy, or mandatory post-run restore + verification —
   both already named above), then one real `--classify` run through `compile`/`gates` on a
   DeKH scene. No open design question. Converts "9/9 green on Sample House" into "9/9 green
   on a real scan" — do this first, it's verification, not new capability, and everything else
   is worth more once it's confirmed rather than assumed.
2. **Multi-storey (re-scoped from XL to a real first step 2026-09-07) — now outranks openings.**
   Reasoning: it's a structural gap (most real institutional buildings are multi-floor; the
   pipeline currently can't represent that at all), not a usability gap. Investigated, not just
   labeled unscoped: the compile back end already has real multi-storey support, proven live
   (IFC-authored SampleHouse carries 2 real `IfcBuildingStorey` rows and compiles clean) — the
   gap is entirely in the point-cloud front end. Three chokepoints found: `segment.py`'s
   `_finish_segmentation` labels only the single lowest horizontal plane "floor" (a 2nd floor's
   real floor would be mislabeled "ceiling"); `floor_z` is the MEAN of every floor-segment
   centroid across `classify.py`/`run_scan_to_bom.py`/`run_dekh_staged.py` (would silently
   average two real floor heights into a meaningless number on a genuine multi-floor scan — a
   live landmine, not just a missing feature); `write_reference_db.py` hardcodes exactly one
   storey row and one storey string for every element. Real precedent already in hand: Building
   A genuinely is 2 floors, scanned as 2 *separate* `.laz` files — today those are only combined
   for scoring, never through `write_reference_db` as one real multi-storey model. **First
   concrete action:** formalize that into a real multi-storey write (2 `IfcBuildingStorey` rows,
   correct per-element tagging, using Building A's already-segmented floors — no new scan
   needed), fixing the `floor_z`-averaging landmine as part of it. Auto-detecting floors from
   one continuous multi-floor scan is a separate, larger, still-unscoped follow-on — don't
   conflate it with this step. Full detail: `PRODUCTION_READINESS_BACKLOG.md` §SEQUENCED
   SESSION PLAN, item 2.
3. **Openings — ONE more bounded attempt, stop condition agreed before starting.** RGB
   investigated 2026-09-07, both designs REJECTED: real signal (real B_ICU doors vs. host wall,
   median Δ19.1 vs. a same-wall noise floor of 0.2/0.8), but neither a global per-wall
   color-anomaly detector (1.9% precision, 2,787 false positives, shape-inseparable) nor a
   local-contrast refinement at two radii (still ~2% both) clears a usable rate — root cause is
   a whole-wall-height lighting gradient spatially coincident with real doors at floor level.
   Full numbers in `DAGCompiler/python/scan_to_bom/README.md`'s "RGB-based color-anomaly
   opening detection" section — read before re-attempting. **Next attempt, sequenced by cost:**
   (a) cheap pre-check — does the dataset's own `.npy` semantic label actually distinguish
   door-vs-wall, or only group by surface identity (never checked); (b) if not, per-wall
   vertical-gradient detrending (regress RGB against height per wall, run the same tested
   anomaly detector on the residuals instead of raw color — targets the diagnosed confound
   directly, rather than a third variation on what's already failed twice). **Stop condition,
   fixed now:** re-run the same false-positive check (all 321 real `IfcWall` segments, B_ICU).
   Continue only if precision clears a real order-of-magnitude bar (~20%+, or a TP/FP
   population that's finally shape-separable) — not merely "better than 2%." Landing back in
   the same ~1–5%, shape-inseparable range is a 3rd honest rejection in this family — close it
   for the pilot bar (which explicitly allows a human touch-up pass here) and don't start a 4th
   design without a genuinely new data source.
4. **`IfcColumn` — deferred behind 1–3.** Investigated 2026-09-07: found to be an EXTRACTION
   problem, not the classification problem it looked like. Columns are wall-adjacent (0.00–
   0.20m gap for 15/16 real columns, not "free-standing" as earlier assumed), and no predicted
   segment claims a column-scale majority of any real column's points — unlike doors, there's
   no coherent segment for a classifier to label at all. Likely mechanism: thin (0.25–0.40m)
   columns flush against a wall don't survive RANSAC as their own plane — same coplanar-
   absorption family as doors, different geometry. Full detail in
   `DAGCompiler/python/scan_to_bom/README.md`'s `IfcColumn` bullet. Real, but decided to be
   lower-value than 1–3 — don't start until those are done or explicitly reprioritized.

Full backlog across all four production-readiness dimensions (model accuracy, generalization,
infrastructure, tooling maturity) — including the items NOT in this ordered list and why —
lives in `PRODUCTION_READINESS_BACKLOG.md` (repo root). Update that file, not just this
section, when any item's status changes.

## STANDING RULE — verify bulk/automated edits against the diff, not against the tool
Applies to any change applied mechanically across many files (a script that rewrites imports,
inserts a guard, renames a symbol, reformats). The script reporting "APPLIED: 88 files" is not
evidence the edit was correct. Two checks are mandatory before committing:

1. **`git diff --numstat`, and require deletions == 0 for anything claimed "purely additive".**
   A single non-zero deletion count is the whole signal. This caught a bulk insert that wrote
   `newline="
"` unconditionally and silently converted 9 CRLF files wholesale — **10,785
   phantom deleted lines burying the 16 real added ones**, which would have made the diff
   unreviewable and the change impossible to audit later. Read each file with `newline=""` and
   write back its own endings.
2. **Read the actual diff of at least one file the edit touched, ideally the hardest case.**
   Doing that caught the other bug in the same change: the inserter skipped `import sys` when a
   file imported `sys` further down, which is a `NameError` in every file whose own import sits
   BELOW the insertion point — including the very script being fixed. Both bugs compiled fine
   at the script level and would only have failed at runtime.

Corollary for mechanical fixes generally: **reproduce the original failure before trusting the
fix.** A full-chain green run is not proof the specific defect is gone — it may simply not be
exercising that path any more. For the cp1252 crash the real proof was dropping the
`ad_geometry_map` view to force the fatal branch and re-running on a genuine cp1252 stdout with
no env override. "The suite passes" and "the bug is fixed" are different claims.

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
