# S60 — ERP Model Alignment (iDempiere BOM Pattern)

> **Foundation:** [BBC](BOMBasedCompilation.md) §1 · [ConstructionAsERP](ConstructionAsERP.md) §1

## Principles

1. **ONE C_DocType:** "Construction Order" — metadata only, not a compilation driver
2. **No M_BomCategory** — use M_Product_Category instead (product IS its category)
3. **Products with IsBOM=Y ARE BOMs** — no separate bom_type hierarchy
4. **Compiler walks C_OrderLine tree**, not m_bom directly
5. **YAML is the test script** (creates C_Order + OrderLines), not a pipeline driver

## Schema Gaps (UI session review)

| ID | Table | Column/Feature | Status |
|----|-------|---------------|--------|
| U2 | C_OrderLine | `Discipline TEXT DEFAULT 'ARC'` | DONE (S60_schema.sql) |
| U3 | C_Order | `Jurisdiction TEXT` | DONE (S60_schema.sql) |
| U4 | C_Order | `OccupancyClass TEXT` | DONE (S60_schema.sql) |
| U5 | ~~DAO~~ | ~~OrderLineHydrationDAO~~ | SUPERSEDED — OrderLineWalker + bom_child_id join-back IS the bridge |
| U6 | Table+DAO | `AD_Val_Rule_Exception` | Table DONE; wiring TODO |

## Code Changes

| # | Change | Status |
|---|--------|--------|
| 1 | Schema migration (U2-U4, U6) | DONE — `S60_schema.sql` |
| 2 | Compiler walks C_OrderLine tree (BomDropper + OrderLineWalker) | DONE — `BomDropper.java`, `OrderLineWalker.java` |
| 3 | BuildingRegistryTest → bomDrop per building | DONE |
| 4 | run_RosettaStones.sh → same path (schema auto-applied) | DONE |
| 5 | C_DocType as metadata only (not compilation driver) | DONE — PlacementLoader reads C_OrderLine when available |
| 6 | Replace M_BomCategory references with M_Product_Category | TODO |
| 7 | ~~OrderLineHydrationDAO~~ | SUPERSEDED by OrderLineWalker |
| 8 | Wire AD_Val_Rule validation with exception override | TODO |
| 9 | Visual diff report: per-element TSV under `--diff` flag | TODO |
| 10 | Script accepts building prefixes as arguments | TODO |

## Proven

- `WorkOrderCompileTest` (W-WO-1: bomDrop → compile → 60 elements)
- `BomDropConfigureTest` (TC-4: roof swap → 95 elements)
- S60 Rosetta Stone: SH (55), FK (82), DM (60) all GREEN through OrderLine path

## Database Backup

`backup/db_snapshot_20260323_014819/` (1.5GB — library, output, input DBs)
