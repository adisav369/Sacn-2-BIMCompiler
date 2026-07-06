# DONE — implemented 2026-04-17
# Scope: 4D/5D township — multi-building sandbox schedule and costing
# STATUS: DONE — township auto-detect, archetype dedup, per-archetype Excel, 28/28 tests PASS
# §PROOF: §TOWNSHIP_AUTO, §TOWNSHIP_ARCH (30 archetypes), §EXCEL_DONE (33 sheets), test_nD_engine 28/28 PASS

## Problem

`sandbox_1M_extracted.db` has 1,061,736 elements across 13 CBD buildings + 43
suburb tiles. Running `schedule_generator.py` or `comprehensive_boq_export.py`
against it produces many empty sheets. The scripts were designed for a single
building DB (e.g. LTU A-House, 125K elements, one discipline set). They have no
concept of:

- Multiple buildings in one DB (the `building` column in `elements_meta`)
- Tile-based suburb rows (prefix `S\d+_\d+_` in building names)
- Cross-building phase sequencing (township infrastructure before houses)
- Per-building vs aggregate BOQ

**Reference:** `docs/4D5DAnalysis.md` — single-building proof (LTU, Terminal).

---

## Why Sheets Are Empty

`comprehensive_boq_export.py` queries `elements_meta` and groups by `discipline`.
In a single-building DB every element belongs to one project. In `sandbox_1M`:

- `elements_meta.building` has values like `Hospital`, `Terminal`,
  `S0_0_HospitalGarage`, `S1_0_HospitalGarage`, etc.
- The SQL has no `WHERE building = ?` or `GROUP BY building` — it aggregates
  everything, but rate lookups by `ifc_class + discipline` match only classes
  present in the current sheet's discipline filter.
- Suburb tiles duplicate the same buildings 43 times. Their elements hit the
  same rate rows as the CBD copy, inflating totals or causing primary-key
  clashes that silently zero out sheets.
- The schedule sequencer assigns phases by `ifc_class`. With 13 building types
  mixed together, phase precedence (Substructure before Superstructure) has no
  per-building anchor — tasks float or collapse.

---

## Data Model — What the Sandbox Has

```sql
-- elements_meta in sandbox_1M_extracted.db
SELECT building, COUNT(*) n, COUNT(DISTINCT ifc_class) classes
FROM elements_meta
GROUP BY building
ORDER BY n DESC;
```

Expected shape:
- `Hospital` — 63,917 elements, 8 disciplines (CBD)
- `Terminal` — 48,428 elements (CBD)
- `Clinic`, `LTU_AHouse`, `Ifc4_Revit`, … (CBD)
- `S0_0_HospitalGarage` … `S42_0_HospitalGarage` (suburb tiles, same geometry)

Key columns available: `guid`, `discipline`, `ifc_class`, `element_name`,
`element_type`, `storey`, `material_name`, `material_rgba`, `building`.

---

## Design — Township 4D/5D Structure

### Sheet Organisation Principle

One Excel workbook = one township run. Structure:

```
Cover Sheet          — township metadata, total cost, date range
TOC                  — building index with hyperlinks
Executive Summary    — aggregate across all buildings (discipline totals)

[Per unique building type — not per tile]
BOQ - Hospital       — ARC/STR/MEP/ELEC/FP/ACMV breakdown
BOQ - Terminal       — …
BOQ - Clinic         — …
BOQ - LTU_AHouse     — …
… (one sheet per unique building archetype, NOT per suburb tile)

Schedule - Township  — phased Gantt, buildings as swim lanes
S-Curve - Township   — aggregate spend over time
```

### Building Deduplication Rule

Suburb tiles `S{row}_{col}_{BuildingType}` are **instances** of the archetype
`BuildingType`. For BOQ and scheduling:

- Compute BOQ for the archetype **once**
- Multiply quantities by instance count
- **Do not create 43 identical sheets** — one `BOQ - HospitalGarage` row with
  `qty × 44` (1 CBD + 43 suburb)

SQL to extract archetype and instance count:

```sql
SELECT
    CASE
        WHEN building LIKE 'S%\_%\_%' ESCAPE '\'
             THEN SUBSTR(building, INSTR(building, '_', INSTR(building,'_')+1)+1)
        ELSE building
    END AS archetype,
    COUNT(DISTINCT building) AS instances,
    COUNT(*) AS total_elements
FROM elements_meta
GROUP BY archetype
ORDER BY total_elements DESC;
```

### 4D Phase Sequencing — Township Logic

Single-building phase order:
`Substructure → Superstructure → MEP Rough-in → Architecture → MEP Final → Finishes`

Township adds two outer phases:

```
Phase 0 — INFRASTRUCTURE   Roads, drainage, utilities (IfcCivilElement, IfcPipeSegment trunks)
Phase 1 — SUBSTRUCTURE     All buildings: footings, piles, ground slabs
Phase 2 — SUPERSTRUCTURE   All buildings: columns, beams, slabs
Phase 3 — ENVELOPE         Walls, roofs, curtain walls
Phase 4 — MEP ROUGH-IN     All disciplines first-fix
Phase 5 — ARCHITECTURE     Doors, windows, finishes
Phase 6 — MEP FINAL        Fixtures, terminals, commissioning
Phase 7 — HANDOVER         Inspections, snagging
```

Each phase spans **all buildings in parallel** within a phase band. Buildings
within a phase are sequenced by size (largest first = critical path).

### 5D Cost Roll-up — Township

| Level | Scope | Formula |
|-------|-------|---------|
| Element | single ifc_class row | `qty × unit_rate` |
| Discipline | sum of elements in disc | `Σ element costs` |
| Building archetype | sum of disciplines | `Σ discipline costs` |
| Building instance | archetype × instance count | `archetype_cost × N` |
| Township total | sum of all instances | `Σ instance costs` |

---

## Investigation Steps Before Coding

### Step 1 — Verify building column in sandbox
```sql
SELECT building, COUNT(*) FROM elements_meta
GROUP BY building ORDER BY COUNT(*) DESC LIMIT 20;
```
Confirm archetype naming pattern (prefix vs suffix for suburb tiles).

### Step 2 — Count archetypes and instances
Run the archetype SQL above. Record: how many archetypes, how many total
instances. This drives the sheet count.

### Step 3 — Check discipline coverage per archetype
```sql
SELECT archetype, discipline, COUNT(*) FROM (
    SELECT
        CASE WHEN building LIKE 'S%' THEN
            SUBSTR(building, INSTR(building, '_', INSTR(building,'_')+1)+1)
        ELSE building END AS archetype,
        discipline
    FROM elements_meta
) GROUP BY archetype, discipline ORDER BY archetype, discipline;
```
Sheets are empty when an archetype has no elements in that discipline.
This is correct — suppress empty discipline sheets silently.

### Step 4 — Verify rate table coverage
Confirm that `CONSTRUCTION_SEQUENCE_RULES` in `schedule_generator.py` and
the rate dict in `comprehensive_boq_export.py` cover the IFC classes
present in each archetype. Log any unmapped classes.

### Step 5 — Run against sandbox, measure output
Run the updated scripts with `--township` flag against
`sandbox_1M_extracted.db`. Verify:
- Sheet count = archetypes (not tiles)
- No empty sheets (suppressed if discipline absent)
- Executive Summary totals = sum of per-building totals (cross-check)
- S-Curve is smooth (no spike from 43× suburb duplication)

---

## What NOT to Change

- `schedule_generator.py` and `comprehensive_boq_export.py` single-building
  paths — add `--township` mode, do not break existing single-building usage
- Rate tables — township uses the same CIDB rates, just with qty × instances
- `sandbox_1M_extracted.db` — read-only; no writes from reporting scripts
- Terminal, Hospital, LTU individual DBs — unaffected

## Files

| File | Role |
|------|------|
| `docs/4D5DAnalysis.md` | Single-building proof and industry value |
| `scripts/schedule_generator.py` | 4D — needs `--township` mode |
| `scripts/comprehensive_boq_export.py` (dataintelligence) | 5D — needs `--township` mode |
| `DAGCompiler/lib/input/sandbox_1M_extracted.db` | Target DB — read only |
| `scripts/schedule_database_schema.py` | Schema reference |
