# 5D/nD Backend Readiness — 4D Unknown Categories + Backend Triage

## Status

- **5D BOQ: DONE** — `scripts/simple_qto_extract.py` works. LTU: 133 lines RM47M, Clinic: 41 lines RM11M.
  Writes `simple_qto` table into the extracted DB. Copied from Federation `boq/simple_qto_extract.py`.
  See `~/Pictures/Screenshots/5DCosting.png` — Executive Summary, Cost Breakdown pie, Cost Components bar.
- **4D scheduling: WORKS but UNKNOWN phase dominates** — see below.
  See `~/Pictures/Screenshots/4DSchedule.png` — 224 tasks, phase distribution, discipline breakdown.
  See `~/Pictures/Screenshots/4DDashBoard.png` — **the problem**: Unknown = 8,703 days, 17,407 man-days,
  dwarfs all other phases and distorts S-Curve + milestone timeline.
- **6D/7D: LOW PRIORITY** — triage only, no implementation.

## Task 1: 4D Unknown Categories (HIGH PRIORITY — Python fix)

The Federation `schedule/schedule_generator.py` has `CONSTRUCTION_SEQUENCE_RULES` that map
IFC classes to construction phases (Substructure → Superstructure → MEP Rough-in → Architecture
→ MEP Final → Finishes). But LTU A-House has **80,788 elements (64%)** with IFC classes NOT
in these rules:

| IFC class (NOT in rules) | Count | What it actually is |
|---|---|---|
| IfcFlowSegment | 42,071 | Generic MEP pipe/duct runs (IFC2x3) |
| IfcFlowFitting | 32,139 | Generic MEP elbows/tees/reducers |
| IfcFlowController | 3,802 | Valves, dampers, controllers |
| IfcOpeningElement | 3,368 | Wall/slab openings (VOID discipline) |
| IfcMember | 2,349 | Structural members (timber, steel) |
| IfcBuildingElementProxy | 1,642 | Generic proxy elements |
| IfcEnergyConversionDevice | 616 | Boilers, heat exchangers |
| IfcFlowTreatmentDevice | 295 | Filters, silencers |
| IfcFurnishingElement | 242 | Furniture |
| IfcPlate | 145 | Steel plates (structural) |
| IfcReinforcingBar | 73 | Rebar |
| IfcStair | 48 | Stairs (whole, not flights) |

### What to do

1. **Read** `schedule/schedule_generator.py` (lines 32-66) for existing rules
2. **Add missing mappings** — copy the script to `scripts/schedule_generator.py` (don't touch original):

   ```python
   # Generic MEP (IFC2x3) — same phase as typed equivalents
   'IfcFlowSegment': {'phase': 'MEP Rough-in', 'sequence': 5, ...},
   'IfcFlowFitting': {'phase': 'MEP Rough-in', 'sequence': 5, ...},
   'IfcFlowController': {'phase': 'MEP Final', 'sequence': 9, ...},
   'IfcEnergyConversionDevice': {'phase': 'MEP Rough-in', 'sequence': 5, ...},
   'IfcFlowTreatmentDevice': {'phase': 'MEP Rough-in', 'sequence': 5, ...},
   'IfcFlowMovingDevice': {'phase': 'MEP Rough-in', 'sequence': 5, ...},
   'IfcFlowStorageDevice': {'phase': 'MEP Rough-in', 'sequence': 5, ...},
   # Structural
   'IfcMember': {'phase': 'Superstructure', 'sequence': 3, ...},
   'IfcPlate': {'phase': 'Superstructure', 'sequence': 3, ...},
   'IfcReinforcingBar': {'phase': 'Substructure', 'sequence': 1, ...},
   # Architectural
   'IfcOpeningElement': {'phase': 'Architecture', 'sequence': 6, ...},
   'IfcStair': {'phase': 'Architecture', 'sequence': 7, ...},
   'IfcFurnishingElement': {'phase': 'Finishes', 'sequence': 11, ...},
   'IfcBuildingElementProxy': {'phase': 'Architecture', 'sequence': 6, ...},
   'IfcRailing': {'phase': 'Architecture', 'sequence': 8, ...},
   'IfcBuildingElementPart': {'phase': 'Architecture', 'sequence': 6, ...},
   ```

3. **Storey ordering problem** — LTU has 19 storey names from different authoring tools:
   - ARC/STR: `Plan 1`, `Plan 2`, `Plan 3`, `Plan 4`
   - Some MEP: `VÅN 1`–`VÅN 5`, `VÅNING 1`–`VÅNING 4`
   - Others: `Storey 1`–`Storey 3`, `Ref.`, `TAKPLAN`

   The schedule generator needs a storey normalisation map to merge these into a single
   construction sequence. Read `schedule/schedule_generator.py` to see how it currently
   handles storey ordering and fix for multi-name case.

4. **Run on Clinic first** (simpler — 5 disciplines, consistent storey names), then LTU.
5. **Also copy** `schedule/database_schema.py` — the generator imports it.

### Files to read
- `federation/schedule/schedule_generator.py` — main generator
- `federation/schedule/database_schema.py` — schema for `construction_schedule` table
- `federation/schedule/excel_export.py` — exports to Excel
- `federation/boq/comprehensive_boq_export.py` — has LABOR_RATES the generator imports

## Task 2: Backend Triage (RESEARCH ONLY — no implementation)

Review what the Java BIM Designer server can already do, and what it would take for it to
serve nD data. **Spec review only — do not implement.**

### Read these files:
- `BonsaiBIMDesigner/src/main/java/com/bim/designer/api/DesignerServer.java` — existing endpoints
- `BonsaiBIMDesigner/src/main/java/com/bim/designer/api/CompileRequest.java` — request model
- `docs/BIM_Designer.md` — architecture overview
- `docs/Enterprise.md` §HTML UI — existing web UI capabilities

### Answer these questions (write findings into `docs/Enterprise.md` §Backend Readiness):
1. What REST endpoints does the server currently expose?
2. Does it already read from extracted DBs or only from ERP.db/component_library.db?
3. Could a `/api/boq/{building}` endpoint serve the `simple_qto` table as JSON?
4. Could a `/api/schedule/{building}` endpoint serve the `construction_schedule` table?
5. What framework does it use (Javalin? Spark? raw HttpServer?)
6. Is there WebSocket support (needed for 7D IoT streaming)?

### Triage table to produce:

| Dimension | Python (Bonsai viewport) | Java (backend server) | Status |
|---|---|---|---|
| 4D Schedule | Animation + timeline | REST endpoint + Excel export | WORKS on LTU+Clinic |
| 5D BOQ | simple_qto_extract.py | REST endpoint + **editable Excel** | WORKS on LTU+Clinic |
| 6D Asset register | — | GUID→FM/CMMS REST query | LOW |
| 7D IoT | — | WebSocket sensor stream | LOW |

### 5D Excel Backend Spec (KEY DELIVERABLE)

The Python `comprehensive_boq_export.py` generates an Excel with static values.
The Java backend version must generate **editable Excel with live formulas**:

- Cell `unit_rate` is editable — QS changes it, `total = qty × unit_rate` recalculates
- Cell `contingency_%` is editable — applies to discipline subtotals
- Template-driven layout: Executive Summary, Cost Breakdown pie, Cost Components bar
- Served via REST: `GET /api/boq/{building}/excel` → downloads .xlsx
- Uses Apache POI or similar for formula-aware Excel generation
- Data source: `simple_qto` table in the extracted DB
- Currency: MYR (Malaysian Ringgit) with locale formatting

The current Python export proves the data and layout work (see screenshot:
`~/Pictures/Screenshots/Screenshot from 2026-04-06 21-36-51.png`).
The Java version adds: formulas, editability, REST serving, template reuse.

## Reference

- LTU A-House: `docs/LTUAHouseAnalysis.md` — 125,997 elements, 8 disciplines, 232MB DB
- Clinic: `DAGCompiler/lib/input/Clinic_extracted.db` — 16,481 elements, 5 disciplines
- QTO script: `scripts/simple_qto_extract.py` (working, tested)
- Federation schedule: `federation/schedule/schedule_generator.py`
- Federation BOQ: `federation/boq/simple_qto_extract.py` (original, don't modify)
- Java server: `BonsaiBIMDesigner/src/main/java/com/bim/designer/api/DesignerServer.java`
- Extraction README: `scripts/README_extraction.md`

## Why This Matters

The 4D/5D pipeline already produces bankable project analytics from an IFC in 2 seconds:
- 5DCosting: RM 67M costed, discipline-level material/labour/equipment breakdown
- 4DSchedule: 224 tasks, phase precedence, resource allocation
- 4DDashboard: S-Curve, milestones, resource workload — all from one extracted DB

No commercial tool does this at this speed from open-source at zero licence cost.
But the **Unknown phase at 8,703 days / 17,407 man-days** destroys the credibility
of the entire analysis. Fixing the unmapped IFC classes turns this from a demo
into a truthful analysis engine. That's the priority.

## Deliverables

1. `scripts/schedule_generator.py` — copy with ALL IFC classes mapped (zero Unknown),
   tested on Clinic + LTU. The 4DDashBoard must show no Unknown bar.
2. Storey normalisation for LTU's 19 storey names → single construction sequence
3. Re-run 4D on LTU, verify S-Curve and milestones are realistic
4. Backend triage findings written into `docs/Enterprise.md` §Backend Readiness
5. No Java implementation — research and spec only
