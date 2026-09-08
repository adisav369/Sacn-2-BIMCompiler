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

## NEXT SESSION PLAN (updated 2026-09-08) — sequenced, not just an open backlog
Production bar, target category, wall-recovery ceiling, and priority order were decided by the
product owner on 2026-09-07 (see `PRODUCTION_READINESS_BACKLOG.md`'s `## Decisions` and
`## SEQUENCED SESSION PLAN` sections for full reasoning — this is the condensed version to open
a session with). **Production bar = pilot-ready for a real client's building** (mostly-correct,
human review/touch-up expected on known-weak parts, not full autonomy). **Target category =
institutional/commercial only** (hospitals, offices — DeKH's own category; not generalizing
beyond it yet). **87–97% wall recovery is accepted, closed, not pursued further.**

**Do these in order. Don't skip ahead, don't resume a rejected design mid-idea.**

0. ~~D4 → D1 — prove a real DeKH scan through the full Java chain.~~ **DONE 2026-09-08.**
   Added `--erp-db` (mirroring `--comp-db`) to `IFCtoBOMMain`/`IFCtoBOMPipeline` — closes the
   isolation gap for real. A real DeKH scan (Building A 1st floor, 498 elements) verified
   genuinely clean through populate → classify → all 18 QA gates, fully isolated (scratch
   `--comp-db`/`--erp-db`, real tracked library byte-verified untouched throughout — including
   after two permission-classifier blocks, both resolved by asking rather than routing around).
   Compile itself also now genuinely runs — **fixed a real bug found along the way:**
   `BuildingRegistryTest`'s `GATE_SCOPE` allowlist didn't include `RE_DKAP` or `RE_SHPC`, so
   compile had been silently SKIPPING (assumeTrue → mvn exit 0, indistinguishable from a real
   pass), not passing. **This retroactively corrects an earlier claim in this file/session:**
   "SampleHousePC 9/9 green" almost certainly never actually verified compile. `GATE_SCOPE` now
   includes both. Once genuinely running, compile surfaced a real, systematic defect —
   promoted to its own item, priority 1, superseding this one.
1. ~~A9 — compiled placement is systematically wrong on every point-cloud building.~~ **FIXED
   2026-09-08, verified on all 3 real buildings.** The flat-BOM-tree hypothesis this file
   floated when A9 was first found was investigated and **falsified**: compiling IFC-authored
   Sample House directly showed the identical `P-PARENT 0/59` failure despite real assembly
   nesting, proving it wasn't a point-cloud-specific or nesting-depth problem at all.

   **Real root cause, traced through the actual write path, not guessed:** `c_orderline`'s
   `dx/dy/dz` is never updated after BOM Drop (`grep`-confirmed zero `UPDATE` statements touch
   those columns anywhere), and comparing it against the real walked positions in
   `element_transforms` (same building, same elements) showed both live in one consistent
   MIN-CORNER-relative local frame — never offset by the root `BUILDING`'s own `dx/dy/dz`.
   `StructuralBomBuilder.java`'s own code confirms this convention explicitly: the building
   root's origin is commented **"building origin (LBD corner) from all elements"**, and a
   child's own placement offset is commented **"always >= 0"**. `BomTreeProver`'s old
   `computeWorldPosition` accumulated dx/dy/dz up through EVERY ancestor including the root —
   summing two incompatible coordinate frames (the root's real-world placement seed, used
   elsewhere in `CompilationPipeline` to seed the walk, vs. the tree's own internally-consistent
   local frame) — and additionally checked containment as center-relative
   (`|child − parent| ≤ extent/2`) when the confirmed convention is corner-relative
   (`0 ≤ child ≤ extent`).

   **Fix:** `LEAF` `dx/dy/dz` is corner-relative to its own IMMEDIATE parent only — no
   accumulation up the tree at all. `computeWorldPosition` (now provably wrong AND unnecessary)
   removed; `proveParent` checks `child ∈ [0, parent_extent]` directly. Verified two ways
   before calling it done: (1) simulated the fix offline in Python against the real
   `c_orderline` rows of all three buildings — 498/498, 70/70, 59/59, all 100% — **before**
   touching any Java; (2) then ran the actual JUnit test against all three for real, matching
   the simulation exactly: **DeKH Building A 498/498, SampleHousePC 70/70, Sample House
   59/59** — up from 0/498, 0/70, 0/59. `P-SIBLING` confirmed unregressed (100% throughout).

   Two things found along the way, explicitly NOT part of this fix: Sample House's own
   `SampleHouse.bimcobol` script independently fails on an unrelated pre-existing bug (`no such
   column: ProjectName`) — confirmed present before this fix too, untouched. And
   `BuildingRegistryTest`'s `-Dbom.db` is a JVM-global property while its `@TestFactory` runs a
   dynamic sub-test per `GATE_SCOPE` building — pointing it at one building's compile-db and
   then a *different* registered building's dynamic test also runs against that same file,
   producing an unrelated element-count assertion failure. Pre-existing (this is exactly how
   `run_RosettaStones.sh` has always invoked this test), not caused by or related to A9.
2. **Multi-storey (re-scoped from XL to a real first step 2026-09-07) — write-side DONE
   2026-09-08, real-data run still pending.** Reasoning: it's a structural gap (most real
   institutional buildings are multi-floor; the pipeline couldn't represent that at all), not a
   usability gap. The compile back end already had real multi-storey support, proven live
   (IFC-authored SampleHouse carries 2 real `IfcBuildingStorey` rows and compiles clean) — the
   gap was entirely in the point-cloud front end's WRITE side, and Building A already has real
   precedent for it: 2 *separate* `.laz` floor scans, each independently segmented/classified/
   normalized (own tack point), today only combined for SCORING, never through
   `write_reference_db` as one real multi-storey model.

   **Built:** `normalize.combine_floors_to_shared_frame()` — reconciles N independently-
   normalized floors into ONE shared coordinate frame (shared tack point = bbox-center of the
   UNION of every floor's reconstructed RAW extent, the same bbox-center rule
   `compute_tack_point()` already used for one floor, not a new invented convention) and
   renumbers each floor's `Segment.id`s into disjoint ranges so `_guid()`'s `(ifc_class,
   segment.id)` key can't collide across floors that each started counting from 0
   independently (a real, guaranteed collision case — every floor's own `segment_pointcloud()`
   run does exactly that). `write_reference_db.write_multistorey_reference_db()` — writes N
   real `IfcBuildingStorey` rows, each with its own real elevation and correctly-tagged
   elements; `write_reference_db()` (the existing single-storey entry point every other caller
   uses — Sample House, B_ICU, Building C, DKAP, SHPC) now delegates to it with a length-1
   storey list, unchanged signature and behavior. New `run_dekh_staged.py --stage
   combine-storeys` stage: takes 2+ floors that each already completed `--stage segment`,
   recomputes classify+merge fresh from each floor's own `stage2_segments.pkl` (cheap, no new
   checkpoint format, no re-touch of the raw point cloud), reconciles frames, writes one
   multi-storey reference DB.

   **Verified two ways, neither of which needed real DeKH data:** (1) a synthetic 2-floor test
   (scratch script, not committed) built `ClassifiedSegment` data directly with DELIBERATELY
   colliding per-floor segment ids (both floors numbered 0/1, the real collision case) and a
   DELIBERATELY negative storey elevation (Level 1 at -3.1m, stress-testing past Sample House's
   own always-non-negative real elevations) — confirmed zero guid collisions, correct 2-storey
   `spatial_structure`, correct per-element storey tagging, correct elevation deltas. (2) Read
   `StructuralBomBuilder.java` (the real, unmodified — per this file's own "KEEP AS-IS" rule —
   Java consumer) directly: its per-storey floor AABB (`fMinX/Y/Z` etc., line ~166-171) is
   derived purely from that storey's own elements' real positions, never from the `elevation`
   field's sign or any single-storey assumption — consistent with Sample House's already-proven
   real 3-storey compile. Also re-ran the EXISTING Phase 4 harness
   (`validate_reference_db.py` against the real `samplehouse_synthetic.ply` +
   `SampleHouse_extracted.db`) to confirm zero regression to the single-storey path after the
   refactor: schema integrity still reports "exactly 1 Building + 1 Storey", identical to
   before.

   **NOT done, and why, honestly:** running this against REAL DeKH Building A point-cloud data.
   Checked this session — the actual `.laz` scan files aren't present in this environment (only
   a HuggingFace dataset ref stub with no downloaded blobs; DeKH is licensed third-party data
   that's never committed or cached in the repo, so per-session availability isn't guaranteed).
   Natural next step once the real files are available: `--stage segment` on both floors (if a
   prior session's checkpoints didn't survive — they live outside the repo by design, same
   licensing rule), then `--stage combine-storeys`, then the resulting DB through the actual
   Java `--populate --classify --compile` chain with scratch `--comp-db`/`--erp-db` isolation,
   same discipline as D1.

   **Also NOT done, and NOT the same problem:** the `floor_z`-averaging landmine (`floor_z` =
   MEAN of every floor-segment centroid, in `classify.py`/`run_scan_to_bom.py`/
   `run_dekh_staged.py`) is unchanged and still real, but it only bites the genuinely different,
   harder, still-unscoped problem — auto-detecting multiple floors from ONE continuous scan.
   Everything built this session is the two-SEPARATE-scans case (Building A's real precedent),
   where each floor's own local `floor_z` is already computed correctly from only its own
   single real floor plane. Don't conflate the two when picking this back up.
3. **Openings — CLOSED for the pilot bar, 2026-09-08.** Three designs, three honest measured
   rejections. 2026-09-07: neither a global per-wall color-anomaly detector (1.9% precision,
   2,787 false positives, shape-inseparable) nor a local-contrast refinement at two radii
   (still ~2% both) cleared a usable rate — root cause: a whole-wall-height lighting gradient
   spatially coincident with real doors at floor level. 2026-09-08, both logged next ideas
   tried for real against a fresh re-segmentation of the real B_ICU point cloud (reproduced
   the documented baseline exactly — 4,661 segments, 321 `IfcWall`, GT match 30/82 — before
   trusting the run): **(a) the `.npy` semantic labels — clean non-starter, eliminated cheaply
   as planned.** Real GT doors and walls extracted from `DeKH_B_ICU.ifc`; both point
   populations dominated by the SAME label value (75.6% of door points, 79.1% of wall points),
   and 13/13 doors' label values are a strict subset of their host wall's — no door-exclusive
   value exists. Looks like a coarse material-class map (a door leaf reads as similar solid
   material to its wall), not door-vs-wall semantics or per-surface identity. **(b) per-wall
   vertical-gradient detrending — implemented, measured, REJECTED.** The isolated door-vs-host
   residual signal actually held up post-detrend (median Δ21.97, comparable to the original
   19.1), but the full 321-wall detector did WORSE: precision 0.6% (down from 1.9%), only
   6/15 real doors covered (down from 12/15), and TP/FP shape stats now essentially identical
   (0.58m vs 0.60m width). Traced, not just reported: tested and FALSIFIED the obvious
   hypothesis (the door's own points pulling its host wall's regression fit off-baseline —
   an oracle re-fit excluding door points changed the residual by a ratio of 0.98–1.10 across
   all 13 doors, i.e. essentially not at all). Real mechanism, consistent with the original
   diagnosed confound: the lighting gradient and the door signal are spatially COINCIDENT at
   floor level, not merely correlated — a linear per-wall detrend removes the smooth global
   trend but leaves untouched both real 10cm-cell-scale noise and any OTHER near-floor color
   variation (baseboards, floor-material bleed) a real detector can't tell apart from a door.
   **Per the pre-agreed stop condition** (continue only past ~20%+ precision or a
   shape-separable population — landing back in ~1–5%, shape-inseparable is a 3rd honest
   rejection, close it): closed for the pilot bar, which explicitly allows human touch-up
   here. Full numbers and reasoning in `DAGCompiler/python/scan_to_bom/README.md`'s "RGB-based
   color-anomaly opening detection" section — read before ever reopening this. A future
   attempt needs a genuinely different idea (geometric void/hole detection in the host wall,
   named but untried, doesn't depend on color at all) or a new data source, not a 4th
   variation on color-anomaly detection.
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
