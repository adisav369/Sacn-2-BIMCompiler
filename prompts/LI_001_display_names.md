# LI_001 — Library Display Names: Product Naming Housekeeping

**Spec:** `docs/ProjectOrderBlueprint.md §10` (Product Chooser Taxonomy)
**Prereq:** HO_001 DONE — Hospital products seeded into component_library.db
**Scope:** `library/component_library.db` → `component_definitions.name` column only

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

Read before writing. Query the library first. No new schema — name column already exists.

## Context

`component_library.db` has **23,888 products** in `component_definitions.name`.
Current naming falls into three tiers of quality:

| Tier | Pattern | Example | Action |
|------|---------|---------|--------|
| **Good** | Human-readable Revit/JKR family name | `"jkrME18_spr_sprinkler head_pendent"`, `"Canteen Table"` | Keep as-is |
| **Acceptable** | Cleaned IFC class + descriptor | `"Non-Monolithic Run"`, `"Ceiling Mounted Return Air Grille"` | Keep, may trim |
| **Poor** | Auto-generated hash suffix | `"IfcPlate_SampleHouse_254339e1"`, `"IfcCovering_Terminal_b5186629"` | Replace |

The chooser (ProjectOrderBlueprint.md §10.2) needs display names users can scan:
`"Wall Toilet 450×350×820 (HTM-63)"` not `"IfcBuildingElementProxy_Hospital_a3f7c..."`.

## Read First

1. `PROGRESS.md` §Current State
2. `docs/ProjectOrderBlueprint.md §10` — chooser taxonomy + naming convention
3. `docs/DATA_MODEL.md §3` — component_library.db schema
4. `library/component_library.db` — current state (queries below)

## Task 1 — Audit Current Naming Quality

```sql
-- Count by naming tier
SELECT
  CASE
    WHEN name LIKE 'Ifc%\_%\_%' ESCAPE '\' THEN 'POOR (hash suffix)'
    WHEN name LIKE 'Ifc%' THEN 'ACCEPTABLE (bare class)'
    ELSE 'GOOD (descriptive)'
  END as tier,
  ct.discipline,
  COUNT(*) as cnt
FROM component_definitions cd
JOIN component_types ct ON cd.type_id = ct.id
GROUP BY tier, ct.discipline
ORDER BY tier, ct.discipline;

-- Sample poor names to understand the pattern
SELECT cd.name, ct.ifc_class, ct.discipline, ct.category
FROM component_definitions cd
JOIN component_types ct ON cd.type_id = ct.id
WHERE cd.name LIKE 'Ifc%\_%\_%' ESCAPE '\'
LIMIT 20;
```

Report: how many POOR names exist and which buildings/disciplines they come from.

## Task 2 — Define Naming Convention

Per `ProjectOrderBlueprint.md §10.2`, the naming formula is:

```
{Category_Label} {Descriptor} {DimSignature} ({Standard_Ref})
```

Examples:
- `IfcPlate_SampleHouse_254339e1` → `Plate 600×600×25mm`
- `IfcWall_SampleHouse_e03daebb` → `Wall 200mm`
- `IfcCovering_Terminal_b5186629` → `Ceiling Covering 1200×600mm`
- `Toilet-Wall-Mounted_:Toilet-Wall-Mounted:454944` → `Wall Toilet 450×350×820mm`
- `M_Sink - Island - Single:455 mmx455 mm - Public:625957` → `Island Sink 455×455mm (clinical)`

Dimension signature comes from `component_definitions`:
`ROUND(local_max_x-local_min_x)*1000 mm × ROUND(local_max_y-local_min_y)*1000 mm × ROUND(local_max_z-local_min_z)*1000 mm`

Standard refs:
- Sprinkler heads → `(NFPA 13 LIGHT/ORD)`
- Clinical fixtures → `(HTM-63)` for toilets/sinks in hospital context
- Structural members → `(MS EN 1993)` for steel

## Task 3 — Batch Rename Poor Names

For POOR names only (`name LIKE 'Ifc%_%_%'`):

```python
# Pseudocode — implement in tools/ as a standalone script
for row in db.execute("SELECT cd.rowid, cd.name, cd.local_min_x, cd.local_max_x, ...
                        cd.local_min_y, cd.local_max_y, cd.local_min_z, cd.local_max_z,
                        ct.category, ct.ifc_class
                        FROM component_definitions cd JOIN component_types ct ON cd.type_id=ct.id
                        WHERE cd.name LIKE 'Ifc%_%_%'"):
    dx_mm = round((row.local_max_x - row.local_min_x) * 1000)
    dy_mm = round((row.local_max_y - row.local_min_y) * 1000)
    dz_mm = round((row.local_max_z - row.local_min_z) * 1000)
    label = CATEGORY_LABELS[row.category]  # e.g. WALL → "Wall", PLATE → "Plate"
    new_name = f"{label} {dx_mm}×{dy_mm}×{dz_mm}mm"
    db.execute("UPDATE component_definitions SET name=? WHERE rowid=?", (new_name, row.rowid))
```

Write the script as `tools/rename_products.py`. It must be:
- **Idempotent**: running twice produces no change
- **Selective**: only rewrites POOR names (hash suffix), never GOOD names
- **Dry-run first**: `--dry-run` flag prints proposed changes without writing

## Task 4 — Hospital Products (after HO_001 seeds them)

Hospital products seeded by HO_001 will have Revit element names:
`Toilet-Wall-Mounted_:Toilet-Wall-Mounted:454944`

These are in the ACCEPTABLE tier — Revit family names are descriptive but verbose.
Clean them:
- Strip `:NNNNNN` (Revit element ID suffix)
- Capitalise category word
- Append dim signature
- Add standard ref for clinical fixtures

Write a separate pass: `tools/rename_products.py --building HOSP`

## Gate (this session)

- `tools/rename_products.py --dry-run` runs without crash
- Dry-run output shows proposed renames for at least 100 POOR names
- After `--apply`: zero `component_definitions.name LIKE 'Ifc%_%_%'` rows remain
- All GOOD names untouched (spot-check 10 manually)
- `library/component_library.db` passes `DataIntegrityTest` (no orphan products)
- SH 8/8 PASS (regression — no BOM pipeline impact from name changes)

## What NOT to Do

- Do NOT change `component_definitions.geometry_hash` or `type_id`
- Do NOT change `M_Product_ID` values in BOM.db files
- Do NOT rename GOOD names (any name without `Ifc` prefix or hash suffix)
- Do NOT add new columns — `name` is the only target

## Commit

```bash
git add tools/rename_products.py library/component_library.db
git commit -m "[LI_001] Display names: replace hash-suffix product names with dim signatures"
```

## Sequence Map

```
LI_001 (this)  — Rename POOR names → dim signature format
LI_002         — Hospital product names: clean Revit family names for HO_ products
LI_003         — Product search index: FTS5 virtual table on name+category+discipline
LI_004         — Chooser UI: Designer panel browses by domain→discipline→category→size
```

## When Done

Prepend `# DONE — [commit_hash]` to this file's first line.

Append findings:
- Count of POOR names before/after
- Any names that couldn't be auto-resolved (manual review list)
- Hospital product name quality after HO_001 seeding

---
