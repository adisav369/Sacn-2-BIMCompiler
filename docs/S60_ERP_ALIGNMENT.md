# S60 — ERP Model Alignment (iDempiere BOM Pattern)

> **Foundation:** [BBC](BOMBasedCompilation.md) §1 · [ConstructionAsERP](ConstructionAsERP.md) §1

## Principles

1. **ONE C_DocType:** "Construction Order" — metadata only, not a compilation driver
2. **No M_BomCategory** — use M_Product_Category instead (product IS its category)
3. **Products with IsBOM=Y ARE BOMs** — no separate bom_type hierarchy
4. **Compiler walks C_OrderLine tree**, not m_bom directly
5. **YAML is the test script** (creates C_Order + OrderLines), not a pipeline driver

## Schema Gaps (UI session review)

| ID | Table | Column/Feature | Purpose |
|----|-------|---------------|---------|
| U2 | C_OrderLine | `Discipline TEXT DEFAULT 'ARC'` | Add FP/MEP/ELEC discipline lines to an order |
| U3 | C_Order | `Jurisdiction TEXT` | MY/US/UK — determines which building code rules apply |
| U4 | C_Order | `OccupancyClass TEXT` | LH/OH1 — NFPA 13 occupancy for FP calculations |
| U5 | DAO | `OrderLineHydrationDAO` | Convert validated C_OrderLine → PlacementRequest for compiler |
| U6 | Table+DAO | `AD_Val_Rule_Exception` | User override of WARN validation (accept known deviation) |

## Code Changes

1. Schema migration: add Discipline/Jurisdiction/OccupancyClass columns + AD_Val_Rule_Exception table
2. `CompilationPipeline.run()` → accept C_Order ID, walk C_OrderLine tree (not C_DocType lookup)
3. `BuildingRegistryTest` → create C_Order + bomDrop per building, then compile via OrderLine path
4. `run_RosettaStones.sh` → use bomDrop + completeIt (same path as WorkOrderCompileTest)
5. Remove C_DocType as compilation entry point (keep as order metadata)
6. Replace M_BomCategory references with M_Product_Category
7. OrderLineHydrationDAO: C_OrderLine → PlacementRequest (bridge between order and compiler)
8. Wire AD_Val_Rule validation with exception override (U6)
9. Add visual diff report: per-element TSV under `--diff` flag
10. Script accepts building prefixes as arguments, small files default, large files explicit

## Already Proven

- `WorkOrderCompileTest` (W-WO-1: bomDrop → compile → 60 elements)
- `BomDropConfigureTest` (TC-4: roof swap → 95 elements)
- The OrderLine compilation path works. S60 wires it into the Rosetta Stone pipeline.

## Database Backup

`backup/db_snapshot_20260323_014819/` (1.5GB — library, output, input DBs)
