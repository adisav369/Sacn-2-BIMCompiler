# DONE — [7237a737](https://github.com/red1oon/BIMCompiler/commit/7237a737)
# T2.1–T2.4 — Wire All 6 MEP Disciplines Through CrawlRouter

**Spec:** DISC_VALIDATION_DB_SRS §10.4.11 T2.1–T2.4, BBC §3.6.3
**Prereq commits:**
- `5303dfa1` P97: service room seed + discipline categories (T0.1)
- `28386845` P98: callout + parasitic qty walk (T0.2+T0.3)
- `1bc9840a` P99: FollowVerb (T0.4)
- `809fe526` P100: CrawlRouter + BuildingGeometry + 5 ops (T1.1–T1.4)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Each discipline gets a RouteBuilder that reads
BuildingGeometry and builds a CrawlOp list. Products from component_library.db.
Rules from AD_DocEvent_Rule in ERP.db. No invention.

## Read first

1. `PROGRESS.md` §Current State
2. `docs/BOMBasedCompilation.md` §3.6.3 (ALL 6 discipline traces — read every one)
3. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.10 (movement verbs per discipline table)
4. `prompts/100_phase1_route_ops.md` §FINDINGS — CrawlRouter API, CrawlOp interface, BuildingGeometry
5. CrawlRouter source + CrawlOp implementations from P100
6. `BIM_COBOL/src/main/java/com/bim/cobol/verb/RouteSprinklersVerb.java` — existing FP verb
7. `BIM_COBOL/src/main/java/com/bim/cobol/geometry/ComplianceChecker.java` — FP rules
8. `library/ERP.db` — `SELECT * FROM ad_fp_coverage;` + `SELECT * FROM AD_DocEvent_Rule;`

## Route patterns from BBC §3.6.3

### FP — Fire Protection (T2.1)
```
FROM PUMP_ROOM: FOLLOW vertical → PENETRATE slab × floors → per floor:
  FOLLOW ceiling (header) → BRANCH tee × rooms → SprinklerGrid per room
```
Ops: FOLLOW, PENETRATE, BRANCH. Rules: NFPA 13 (ad_fp_coverage, already active).

### ELEC — Electrical (T2.2)
```
FROM DB_ROOM: FOLLOW vertical (cable tray) → PENETRATE slab × floors → per floor:
  FOLLOW ceiling (tray) → BRANCH × rooms → LightFixture grid per room
```
Ops: FOLLOW, PENETRATE, BRANCH. Rules: MS 1525 lighting density.

### CW — Cold Water (T2.3a)
```
FROM TANK_ROOM: FOLLOW vertical (rising main) → PENETRATE slab × floors → per floor:
  FOLLOW wall/ceiling → BRANCH to wet rooms → REDUCE at branches → FOLLOW to fixtures
```
Ops: FOLLOW, PENETRATE, BRANCH, REDUCE. Rules: MS 1228 fixture unit sizing.

### SP — Sanitary Plumbing (T2.3b — REVERSED, top-down)
```
FROM ROOF downward: per floor (top→bottom):
  fixture positions → FOLLOW gradient to stack → BRANCH wye → DROP full height → FOLLOW to manhole
```
Ops: FOLLOW (with gradient), BRANCH (wye), DROP. Direction = (0,0,-1). Rules: MS 1228 min gradient 1:40.

### ACMV — Air Conditioning (T2.4)
```
FROM AHU_ROOM: FOLLOW ceiling void → BEND at turns → BRANCH × rooms →
  REDUCE (main→branch) → AirTerminal grid per room
```
Ops: ALL 5 (FOLLOW, BEND, BRANCH, REDUCE, PENETRATE). Rules: ASHRAE 62.1 ACH.

### LPG — Gas (T2.4b)
```
FROM METER_ROOM: FOLLOW ext wall → BRANCH × kitchens → REDUCE → FOLLOW to gas points
```
Ops: FOLLOW, BRANCH, REDUCE. Rules: MS 830 isolation. Smallest system.

## Deliverables

### 1. Six RouteBuilder classes

One per discipline in `BIM_COBOL/src/main/java/com/bim/cobol/route/`:
- `FPRouteBuilder.java` — uses ComplianceChecker + ad_fp_coverage
- `ELECRouteBuilder.java` — light fixture grid by lux
- `CWRouteBuilder.java` — pipe sizing by fixture unit
- `SPRouteBuilder.java` — reversed crawl + gradient
- `ACMVRouteBuilder.java` — all 5 ops, duct sizing by airflow
- `LPGRouteBuilder.java` — simplest, follow + branch + reduce

Each reads BuildingGeometry, builds CrawlOp list, calls CrawlRouter.

### 2. Activate DocEvent rule stubs

Set IsActive=1 and fill parameter values for 4 inactive stubs:
- ASHRAE_62_1_AIR_CHANGES → min_ach per occupancy
- MS1525_LIGHTING_DENSITY → max_w_per_m2 per occupancy
- MS1228_FIXTURE_UNIT → min_fixture_units per room type
- MS1228_PIPE_GRADIENT → min_gradient (1:40 = 0.025)

MS830_GAS_ISOLATION stays inactive (LPG is smallest, defer full rules).

### 3. DV migration — seed BOM children for 5 empty disciplines

ACMV/ELEC/CW/SP/LPG shared BOMs need M_BOM_Line children so CrawlRouter has
products to lay. Seed from TE extraction data (BBC §3.6.3 element inventory):
- ACMV: DuctSegment, DuctFitting, AirTerminal
- ELEC: CableTray, LightFixture, PowerOutlet
- CW: PipeSegment, PipeFitting, FlowTerminal, Valve
- SP: PipeSegment, PipeFitting, FlowTerminal
- LPG: PipeSegment, PipeFitting, Valve, GasPoint

### 4. FINE logging (watchdog requirement)

Every RouteBuilder must log via `BIMLogger.fine("CRAWL", ...)`:
- Discipline, start position, floor count
- Per floor: room count, segments produced, fittings inserted
- Total: segments, fittings, length, compliance result

### 5. CONNECTS_TO edges (watchdog requirement)

All 6 RouteBuilders must emit connection edges via CrawlRouter. BIMEyes proofs:
- **P15 PipeInHostProof** — MEP elements within host room
- **P16 WasteGradientProof** — SP waste slopes downward (must fire, not SKIP)
- **P17 SystemConnectedProof** — each discipline's graph is connected (must fire, not SKIP)

### 6. Wire into RouteVerb

`ROUTE <discipline> <building>` dispatches to the correct RouteBuilder.
`ROUTE SPRINKLERS` stays as alias for FP backward compat.

## Execution order

1. Seed BOM children (DV migration) — without children, nothing routes
2. FP first (ad_fp_coverage already active, ComplianceChecker exists) → verify SH
3. ELEC + CW next (similar pattern to FP) → verify SH
4. SP (reversed direction — test gradient) → verify SH
5. ACMV (all 5 ops — integration stress test) → verify SH
6. LPG last (simplest) → verify SH
7. Run TE with all 6 → verify discipline distribution

Test after EACH discipline before proceeding. If one breaks SH, fix before moving on.

## Gate

- SH 7/7 after each discipline (no ARC/STR regression)
- W-FP-ROUTE-1, W-ELEC-ROUTE-1, W-CW-ROUTE-1, W-SP-ROUTE-1, W-ACMV-ROUTE-1, W-LPG-ROUTE-1
- P16 fires on SP (not SKIP) — gradient verified
- P17 fires on all 6 disciplines (not SKIP) — connectivity verified
- ComplianceChecker PASS on FP (NFPA 13)

## What NOT to do

- Do NOT modify CrawlRouter or CrawlOp implementations — those are proven (P100)
- Do NOT modify ARC/STR compilation path
- Do NOT modify existing migration files (sacred — append only)
- Do NOT skip per-discipline SH verification
- Do NOT proceed to next discipline if current one regresses SH

## Spec citations

- `// Implementing BBC.md §3.6.3 FP — Witness: W-FP-ROUTE-1`
- `// Implementing BBC.md §3.6.3 ELEC — Witness: W-ELEC-ROUTE-1`
- `// Implementing BBC.md §3.6.3 CW — Witness: W-CW-ROUTE-1`
- `// Implementing BBC.md §3.6.3 SP — Witness: W-SP-ROUTE-1`
- `// Implementing BBC.md §3.6.3 ACMV — Witness: W-ACMV-ROUTE-1`
- `// Implementing BBC.md §3.6.3 LPG — Witness: W-LPG-ROUTE-1`
- `// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T2.1–T2.4`

## When Done

Prepend `# DONE` to this file's first line.

Append findings:
- DV migration: BOM children seeded per discipline
- Per-discipline: route stats (segments, fittings, length), compliance, SH gate
- DocEvent rules activated + parameter values
- P15/P16/P17 proof status per discipline
- TE full run: discipline distribution table (actual vs §3.6.3 expected)
