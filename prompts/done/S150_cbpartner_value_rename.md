# S150 — Replace buildingType with C_BPartner

**Prior work:** S149 partial (C_BPartner Values corrected, M_BOM recipes tagged,
expandDisciplineLines filter started, C_Order schema has C_BPartner_ID column)
**Blocking:** S149 Task 1 (system BOMs need C_BPartner_ID per building)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## Problem

`buildingType` / `projectName` is an IFC filename artefact (`Duplex`, `HospitalAuckland`,
`Terminal`) used as a string key across 73 files. It must be replaced by the
iDempiere C_BPartner model: Value is the search key, _ID is the FK.

## Design Decisions (already agreed)

1. **C_BPartner.Value IS the building identity.** `Duplex`, `HospitalAuckland`, `Terminal`.
2. **No NULL C_BPartner_ID anywhere.** System BOMs get one row per building (not abstract).
   Same Value (e.g. `CW_SYSTEM`), different C_BPartner_ID → different children per building.
3. **C_Order.C_BPartner_ID** — set at order creation (iDempiere: header owns the BPartner).
   C_OrderLine inherits from its parent order. No C_BPartner_ID on the line.
4. **YAML carries `c_bpartner`** — the Value. BuildingEntry resolves _ID from ERP.db at pipeline start.
5. **Extracted DB filenames change** — `Duplex_extracted.db`, `HospitalAuckland_extracted.db`, `Terminal_extracted.db`.
6. **`source_building` on M_BOM is replaced** by C_BPartner_ID. Drop or leave dead.
7. **expandDisciplineLines()** reads C_BPartner_ID from C_Order (not parameter).
   Filter: `WHERE b.Value = ? AND b.C_BPartner_ID = ?` — clean, no `OR IS NULL`.

## C_BPartner rows (already in ERP.db)

| C_BPartner_ID | Value | Name | Location |
|---|---|---|---|
| 1 | Duplex | Autodesk Revit Duplex | IFC2x3 Duplex Sample (Autodesk) |
| 2 | HospitalAuckland | University of Auckland Hospital | HospitalAuckland Hospital (University of Auckland) |
| 3 | Terminal | SJTII Terminal | Terminal IFC4 (KLIA Malaysia) |

## S149 Already Done (do not redo)

- C_BPartner Values updated in ERP.db (Duplex, HospitalAuckland, Terminal)
- DV038 migration: seed INSERTs added
- 162 DX MEP_RECIPE BOMs: C_BPartner_ID = 1 (was NULL)
- 4 RM MEP_RECIPE BOMs: C_BPartner_ID = 2 (already set)
- IFCtoERP.resolveBPartnerId() — resolves buildingType → C_BPartner_ID via M_BOM bridge
- IFCtoERP.buildMepBomRecipes() — sets C_BPartner_ID on both INSERT paths
- CompilationPipeline: lookupBPartnerId() + lookupBPartnerValue() helpers
- BomDropper: C_Order schema has C_BPartner_ID column (not yet populated)
- OrderLineProductCallout.expandDisciplineLines() has bpartnerId param (interim, replace with C_Order read)

## Task List

### A. YAML + BuildingEntry rename

1. YAML: `building_type: Duplex` → `c_bpartner: Duplex` (all 3 YAMLs + fleet)
2. BuildingEntry record: `projectName` → carries C_BPartner.Value directly
3. BuildingRegistry: reads `c_bpartner` from YAML instead of `building_type`
4. All code referencing `entry.projectName()` or `buildingType` → now returns C_BPartner.Value

### B. Extracted DB filenames

5. `mv` files: `Duplex_extracted.db` → `Duplex_extracted.db` (all 3 + any *_BOM.db)
6. All `Path.of("DAGCompiler/lib/input", buildingType + "_extracted.db")` already works
   because buildingType is now the C_BPartner.Value

### C. C_Order.C_BPartner_ID

7. BomDropper.drop() — add ERP.db connection parameter
8. BomDropper.createOrder() — resolve C_BPartner.Value → _ID from ERP.db, set on INSERT
9. Test callers of drop() (3-4 test classes) — update signatures

### D. expandDisciplineLines() — read from C_Order

10. Remove bpartnerId parameter
11. Read C_BPartner_ID from C_Order WHERE Value = orderId
12. Filter: `WHERE b.Value = ? AND b.C_BPartner_ID = ?`
13. Remove lookupBPartnerId/lookupBPartnerValue from CompilationPipeline (no longer needed)

### E. Fleet rename (73 files)

14. `Duplex` → `Duplex` everywhere
15. `HospitalAuckland` → `HospitalAuckland` everywhere
16. `Terminal` → `Terminal` everywhere
17. Includes: Java constants, test fixtures, scripts, migration SQL, docs, log patterns

### F. Cleanup

18. M_BOM.source_building — leave as forensic trace or drop
19. IFCtoERP.resolveBPartnerId() — simplify (buildingType IS the Value now, just do
    `SELECT C_BPartner_ID FROM C_BPartner WHERE Value = ?`)
20. Remove any `OR IS NULL` fallback in queries

## Read First

1. `CLAUDE.md` + `PROGRESS.md` §Current State
2. `migration/DV038_c_bpartner.sql` (schema + seed)
3. `docs/PREFAB_ARCHITECTURE.md` §Three Orthogonal Dimensions
4. `IFCtoBOM/src/main/resources/classify_dx.yaml` (YAML structure)
5. `DAGCompiler/src/main/java/com/bim/compiler/dsl/BuildingRegistry.java` (BuildingEntry record)
6. `DAGCompiler/src/main/java/com/bim/compiler/bom/BomDropper.java` (createOrder)
7. `DAGCompiler/src/main/java/com/bim/compiler/callout/OrderLineProductCallout.java` (expandDisciplineLines)
8. Grep `Duplex` — 73 files at time of writing

## Gate

- DX: 8/8 PASS
- SH: 8/8 PASS
- `SELECT COUNT(*) FROM C_BPartner` = 3
- `SELECT COUNT(*) FROM M_BOM WHERE C_BPartner_ID IS NULL` = 0
- `SELECT C_BPartner_ID FROM C_Order` returns valid _ID for every order
- No occurrence of `Duplex`, `HospitalAuckland`, or `Terminal` in codebase
- No `source_building` in any WHERE clause
