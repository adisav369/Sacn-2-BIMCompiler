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

## KNOWN PRE-EXISTING GAP — component_library.db (do not rush a fix; resolve deliberately in its own session)
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