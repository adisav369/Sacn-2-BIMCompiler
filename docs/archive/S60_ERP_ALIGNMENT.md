# S60 — ERP Model Alignment (iDempiere BOM Pattern)

> **Foundation:** [BBC](../BOMBasedCompilation.md) §1 · [ConstructionAsERP](ConstructionAsERP.md) §1

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
| 6 | Replace M_BomCategory references with M_Product_Category | ASSESSED — see §M_BomCategory Assessment below |
| 7 | ~~OrderLineHydrationDAO~~ | SUPERSEDED by OrderLineWalker |
| 8 | Wire AD_Val_Rule validation with exception override | DONE — X_AD_Val_Rule_Exception + MValRuleException DAO |
| 9 | Visual diff report: per-element TSV under `--diff` flag | DONE — `SpatialDiff.toTsv()`, `--diff` flag on run_RosettaStones.sh |
| 10 | Script accepts building prefixes as arguments | TODO |

## Proven

- `WorkOrderCompileTest` (W-WO-1: bomDrop → compile → 60 elements)
- `BomDropConfigureTest` (TC-4: roof swap → 95 elements)
- S60 Rosetta Stone: SH (55), FK (82), DM (60) all GREEN through OrderLine path

## Component Library Setup

### IFC Sources

| Source | Building | Download |
|--------|----------|----------|
| buildingSMART samples | SampleHouse, Duplex | https://github.com/buildingSMART/Sample-Test-Files |
| KIT ifcwiki (Karlsruhe) | FZKHaus | https://www.ifcwiki.org/index.php?title=KIT_IFC_Examples |
| opensourceBIM | AC11 Institute | https://github.com/opensourceBIM |
| SJTII Terminal (Malaysia) | Terminal (7 discipline IFCs) | Local: `DAGCompiler/lib/input/IFC/SJTII-*.ifc` |
| PCERT Infrastructure | Bridge, Road, Rail (IFC4X3) | Local: `DAGCompiler/lib/input/IFC/PCERT_Infra_*.ifc` |
| DemoHouse | Generative (no IFC source) | Created by `migration/seed_dm_bom.sql` |

All IFC files stored in `DAGCompiler/lib/input/IFC/`. 34 extracted + 1 generative = 35 buildings.

### The Formula

```
IFC files (DAGCompiler/lib/input/IFC/*.ifc)
  ↓ tools/extract.py (ifcopenshell)
Extracted reference DBs (DAGCompiler/lib/input/*_extracted.db)
  ↓ classify_*.yaml (IFCtoBOM/src/main/resources/) — ONLY human invention point
run_RosettaStones.sh
  ├─ [populate] IFCtoBOMMain --populate
  │   ├─ ExtractionPopulator → read reference DB, extract elements
  │   ├─ ProductRegistrar.ensureProductCatalog() → M_Product (2472 products)
  │   ├─ ExtractionPopulator.fillGeometryGaps() → I_Geometry_Map (167K mesh entries, 358MB blobs)
  │   └─ ProductRegistrar.ensureProductImages() → M_Product_Image (2472 links)
  │
  └─ [IFCtoBOM pipeline] IFCtoBOMMain --classify
      ├─ Build m_bom + m_bom_line (tack offsets, verb refs)
      ├─ Write {PREFIX}_BOM.db
      └─ [DAGCompiler] BomDropper → OrderLineWalker → compile → output.db
```

### Scripts

| Script | Purpose |
|--------|---------|
| `scripts/onboard_ifc.sh` | Full 8-step onboarding for new buildings |
| `scripts/run_RosettaStones.sh` | Master orchestrator (populate + compile + gate) |
| `tools/extract.py` | IFC → extracted.db (ifcopenshell) |

### Key Java Entry Points

| Class | Purpose |
|-------|---------|
| `IFCtoBOM/.../IFCtoBOMMain.java` | CLI: `--populate` and `--classify` |
| `IFCtoBOM/.../ExtractionPopulator.java` | Element extraction + geometry gap fill |
| `IFCtoBOM/.../ProductRegistrar.java` | M_Product catalog + M_Product_Image links |
| `IFCtoBOM/.../IFCtoBOMPipeline.java` | 11-stage pipeline orchestrator |

### Database Size

component_library.db: 433MB total (358MB geometry blobs). Too large for git — schema changes go to `migration/`, pristine binary committed only when finished.

## Database Backup

`backup/db_snapshot_20260323_014819/` (1.5GB — library, output, input DBs).
**WARNING:** `backup/` is .gitignored. NEVER commit database snapshots to git — GitHub rejects files >100MB. The backup dir stays local-only.

## R21 Implementation (host_element_ref)

**IFC chain:** Wall ←(IfcRelVoidsElement)← IfcOpeningElement ←(IfcRelFillsElement)← Door/Window.

**Data flow:**
1. `tools/extract.py` extracts chain → `rel_fills_host(element_guid, host_guid)` table in reference DB
2. `DAGCompiler/python/reference_schema.sql` defines the table
3. `ExtractionPopulator.readFillsHost()` reads the table (backward-compat: empty map if table absent)
4. `ExtractionPopulator.deriveRows()` resolves host GUID → host element_ref via GUID→elementRef map
5. `ExtractionReader.ExtractionElement.hostElementRef()` carries the value
6. All BOM builders + VerbFactorizer write `host_element_ref` to m_bom_line
7. `migration/R21_host_element_ref.sql` adds the column to existing DBs

**IMPORTANT:** Reference DBs must be re-extracted with `tools/extract.py` to populate `rel_fills_host`. Pre-R21 reference DBs have no `rel_fills_host` table — ExtractionPopulator handles this gracefully (returns empty map).

**Consumers:** M16/M17 validation rules (DocValidate) can now use FK join on `host_element_ref` instead of AABB proximity. See LAST_MILE_PROBLEM.md §Check 9.

## M_BomCategory Assessment (#6)

**Finding (S60-S2):** NOT a simple rename. M_BomCategory and M_Product_Category are **orthogonal axes**:

| Axis | M_BomCategory | M_Product_Category |
|------|---------------|-------------------|
| Purpose | Room/zone template types | IFC discipline classification |
| Codes | LI, BD, KT, BT, DN, FR, ST... (19 codes) | STR, ARC, MEP, IFC_WALL... (tree) |
| Structure | Junction table (M_BomCategoryLine) | Self-referencing (Parent_Category_ID) |
| Used by | Template grammar, BIM_COBOL verbs | Discipline assignment, product catalog |

**Impact:** 77 files across 6 layers (PO, BIM_COBOL verbs, pipeline, tests, DAOs, migrations).

**What works already:** DisciplineBomBuilder uses discipline codes (ARC/STR/FP) as bom_category — these ARE M_Product_Category root IDs. The CO path is already aligned.

**What needs dedicated session:**
- BIM_COBOL verbs: DEFINE CATEGORY, ADD TEMPLATE RULE, REGISTER BOM (3 files)
- Template grammar: M_BomCategoryLine → M_Product_Category self-referencing tree
- AABB template matching: currently on M_BomCategory, needs PlacementContext
- 10+ test files need verb/query refactoring
- Schema migration: backfill 19 functional codes → M_Product_Category entries
