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

## CURRENT STATUS CORRECTION (2026-09-04) — the full chain does NOT run at HEAD
The point-cloud front end is real and measured (see `DAGCompiler/python/scan_to_bom/README.md`),
but **the `PIPELINE` line above is not currently reproducible end-to-end from a fresh checkout.**
Everything from `BOM.db` rightward is blocked by the `M_Product` gap below. "The architecture bet
is proven" is a HISTORICAL statement — true once (Phase 5, when `component_library.db` still had
`M_Product`), not true of HEAD today. Verified 2026-09-04 by running the known-good 135-element
`classify_shpc.yaml` case and a 5,543-element DeKH one: both abort at the same place, so this is
not a scale or point-cloud-input problem. Do not read the DeKH accuracy numbers and assume a
working scan→BOM→compile→gates chain right now.

## KNOWN PRE-EXISTING GAP — component_library.db (HIGHEST-PRIORITY OPEN ITEM; do not rush a fix; resolve deliberately in its own session)
**Reprioritized 2026-09-04.** This was previously described here as independent of Scan-to-BIM
work and safe to pick up "anytime, in parallel." That was wrong and is corrected: it blocks the
entire back half of this project's own stated pipeline, for every building, at every scale.
Everything past `ProductRegistrar`'s pre-flight (`IFCtoBOMPipeline.java:239-266`) — BOM
assembly, `BomValidator`'s 18 QA gates, compile, output.db, gates — cannot be exercised or
verified until it is fixed. It remains a "own dedicated session" item (the ~13-migration
ordering problem below is exactly the kind of thing that must not be guessed through under time
pressure) — but it should now get that session SOON, ahead of further front-end accuracy work,
because no amount of segmentation improvement can be validated end-to-end while it stands.

ALSO (found 2026-09-04): running the Java chain WRITES into this tracked, LFS-stored file — a
single `--classify` run on DeKH input added 5,531 DeKH-derived rows to `I_Geometry_Map`
(reversed with `git checkout --`, after first confirming HEAD's LFS object still carried the
documented `I_Geometry_Map` rename fix). Licensed third-party data must not be put through the
Java chain again without an isolation approach (scratch copy of the library, or a mandatory
post-run restore + verification).

`library/component_library.db` (checked into git, ~230MB) is missing table `M_Product` at
HEAD. This is real, not stale — confirmed 2026-09-03 by reverting the file to HEAD
(`git checkout -- library/component_library.db`) and re-running
`mvn exec:java -pl IFCtoBOM -Dexec.mainClass=com.bim.ifctobom.IFCtoBOMMain -Dexec.args="--populate --classify IFCtoBOM/src/main/resources/classify_sh.yaml"`,
which fails with `[SQLITE_ERROR] ... no such table: M_Product` (thrown from
`ProductRegistrar.ensureProductImages`, called via `IFCtoBOMMain`'s `--populate` path after
`ProductRegistrar.ensureProductCatalog` — both in `IFCtoBOM/src/main/java/com/bim/ifctobom/`).
Earlier in that same session this had looked already-fixed because a prior, never-committed
local session had already patched the file — that local-only state was silently lost on the
`git checkout --` above (see the project's Claude memory: "local uncommitted fixes to shared
tracked files are invisible and get wiped by discard/checkout" — flag and commit/document such
state before relying on it again).

A SEPARATE, narrower gap in the same file — `I_Geometry_Map` present only under its old name
`ad_geometry_map` — was found and fixed the same session via the existing, documented
migration `migration/migration_rename_geometry_map.sql` (`ALTER TABLE ad_geometry_map RENAME
TO I_Geometry_Map`; the table's columns already matched, just needed the rename). That fix is
applied to the local `library/component_library.db` working copy. `M_Product` is a distinct,
larger problem — do not conflate the two.

`M_Product_Category` deliberately does NOT belong in `component_library.db` — confirmed via
`ProductRegistrar.java`'s own comment ("component_library.db has no category column") and
`ExtractionPopulator.java` never referencing it against `compConn`. Its real home is
`library/ERP.db` (127 rows, real hierarchy), copied per-run into each `*_BOM.db` by
`IFCtoBOMPipeline.copyCategoryLookup()`. Do not try to add it to component_library.db.

`M_Product` itself likely does belong in component_library.db (its `M_Product_ID` column
elsewhere references it, and `migration/DV015_move_m_product.sql`'s own comment says it only
COPIES `M_Product` out to ERP.db, explicitly "component_library.db is NOT modified" — implying
component_library.db is still the source of truth, DV015 assumes it already has the table).
~13 migration files touch `M_Product` + component_library.db, order and supersession unknown:
`DV015_move_m_product.sql`, `CL001_drop_dead_tables.sql`, `CL003_m_product_category_int_pk.sql`,
`CL004_m_product_int_pk.sql`, `CL005_drop_int_sidecar.sql`,
`CL_001_generative_product_images.sql`, `DV001_disc_validation_schema.sql`,
`DV032_uom_correction.sql`, `DV033_product_element_ref.sql`,
`DV046_generative_product_lod_bridge.sql`, `DV048_unmapped_product_lod.sql`,
`DV049_fridge_kitchen_schedule.sql`, `J4_002_product_forward_axis.sql`,
`S62_001_product_category_fp.sql`, `migration_P02_SH_product_link.sql`. Working out the
correct order (and which are superseded, e.g. by checking `migration/archive/` for anything
these supersede, the way `migration_phase_DE2/DE3_*` were superseded by the single
`migration_rename_geometry_map.sql` above) needs a dedicated investigation — do not guess
through them under time pressure, and do not apply any of them to the shared, tracked
`library/component_library.db` without that investigation first. Until resolved,
`./scripts/run_RosettaStones.sh classify_sh.yaml` (and any `--populate` call) will fail on a
freshly-checked-out `library/component_library.db`.
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
