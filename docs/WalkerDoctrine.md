# Walker Doctrine — disc_walker fundamentals (ANTI-DRIFT CORE DOC)

> ⚠ **CORE / LOAD-BEARING.** These are SETTLED fundamentals (established 2026-06-27 → 2026-06-30, user-confirmed),
> hardened here because they were scattered across memory + `prompts/RESUME_DISC_WALKER_ENVELOPE_BOUND.md` and that
> invited cross-session drift. Read this BEFORE any disc-walker / MEP-walk / rules-DB work. If a session's behaviour
> contradicts a LOCKED rule below, the session has drifted — stop and reconcile, do not "fix" by overriding the rule.
> Companion (the working spec, mutable): `prompts/RESUME_DISC_WALKER_ENVELOPE_BOUND.md`.

## §1 — LOCKED: building-class → rules-DB mapping
**The walk axis is BUILDING-CLASS, never per-building. Discipline is a `WHERE` column, never a file.**

| Building | Class | Rules DB | Why |
|----------|-------|----------|-----|
| SampleHouse, Duplex, **SampleCastle (SC)** | residential | **`duplex_rules.db`** | small/residential — DX cadence + clearances |
| Terminal, Clinic, Hospital, large/complex | complex | `terminal_rules.db` | LOD400, tightest trades |

- **SMALL/RESIDENTIAL BUILDINGS USE `duplex_rules.db`. They do NOT walk Terminal rules in production.** (prompt §SC L253:
  "SC … uses DX rules, no new file"; memory `project_dx_mep_residential_standard`: deployed `window._dwRules` auto-select
  `house/SH/DX/SC → duplex_rules.db, else terminal_rules.db`.)
- **NEVER make a per-building rules file** (no `SC_disc.db`). SC vent cadence, if mined, goes in as **ACMV rows in
  `duplex_rules.db`** with `provenance=measured:samplecastle/*` + src_guids. Fragmenting the discipline axis is the wrong cut.
- Cross-disc tables (`rule_avoidance` = disc-PAIRS, `rule_place_order`) stay WHOLE — never split by discipline.

### ⚠ Known fragility (the drift trap — guard it)
`disc_walker.dwInit(SQL, baseUrl, rulesFile)` **defaults `rulesFile='terminal_rules.db'`** (back-compat). "Small→DX" is
therefore enforced only by the CALLER (`window._dwRules` in bim-ootb). **Any new caller MUST pass `'duplex_rules.db'` for a
residential building** — relying on the default silently walks a house with Terminal LOD400 cadence (wrong, dense). Candidate
hardening (not yet done; flag before relying on the default): make the engine require an explicit class or auto-select.

## §2 — Terminal's role: LOD400 reference + BORROW source (not a small-building ruleset)
Terminal is the **densest/tightest LOD400 model** (measured cross-disc p05 FP|ELEC = 0.155m = the reference clearance;
memory `project_dx_mep_class_boundary`). Its job for small buildings is **NOT** to be their ruleset. It is:
1. The **LOD400 clearance/mesh reference**.
2. A **BORROW source for disciplines ABSENT from the residential set.** `duplex_rules.db` covers ELEC/ACMV/PLB only — it has
   **no FP** (fire/sprinkler) and no rich STR. When a small-building demo wants FP/sprinkler devices, **borrow the Terminal FP
   rules** (`IfcFireSuppressionTerminal`→IfcCovering ceiling, `IfcAlarm`→IfcWall — both MEASURED, see §SHIM-SELECT) as a
   **SEPARATE class**, rendered with **LOD400 mesh priority** (a fine sprinkler/alarm component), not lumped into the residential
   walk. (User-established, re-confirmed 2026-06-30.)
3. The mining ground for measured cadence/clearance/host rules (it has the real fixtures).

**Borrow = reuse the measured Terminal rule rows for that one discipline; it is NOT "switch the building to Terminal rules".**

**Implemented (`W-BORROW-FP` 6/6, `scripts/witness_borrow_fp.js`):** `disc_walker.dwBorrow(disc, db)` registers a
per-discipline source. Per-discipline reads (`repRules`/`countPer`/`route`/`routeChains`/`_loadRuleShims`) route to
`_dbFor(disc) = _borrow[disc] || _db`; cross-disc tables (`clearance`/`order`/`gate`) stay on the PRIMARY `_db`. Proven on
SampleCastle (residential, primary=duplex_rules): FP absent from duplex → `dwBorrow('FP', terminalDb)` → 247 FP placed
(151 sprinklers host-bound to real `IfcCovering` ceilings), classes from Terminal while ELEC stays on duplex, count
bounded by `density×area∩envelope` (not an explosion), Terminal MEASURED bbox carried, gate clearance still the duplex
pair-set (3, not Terminal's 10). Each placement carries a `prim` semantic-kind tag (`_primFor`, §5 LOD seam).

## §3 — MEP relationship taxonomy (route MEP work by which class a device is)
1. **Networked** (route→join→shim at PORTS): supply/waste plumbing, ducted HVAC. Needs `IfcPort`/connectors.
   Oracle = Duplex-MEP (`W-WALKBACK-MEP`).
2. **Host-bound standalone** (host-bind + size, **NO join**): vent grilles (`host=IfcWindow, mount=TOP`), ELEC outlets
   (`host=IfcWall, SIDE`), ceiling lights / sprinklers / air terminals (`host=IfcCovering, BOTTOM`), wall alarms (`IfcWall`).
   Governed by host (`rule_shim`) + count/size rule. Oracle = SC's 13 grilles + Terminal fixtures (`§SHIM-SELECT`).
3. **Run without recorded joins** (segments, no ports): reconstructed by PROXIMITY (nn route). E.g. SC's 60 `hwa afvoer`
   rainwater downpipes.

## §4 — Non-invent boundaries (the prime rule, walker edition)
- **LANDED** (real→real, e.g. routed endpoints on real elements): land EXACTLY (1e-6, guid-matched). May be trusted.
- **GENERATED** (fills an ABSENT discipline): position is PLAUSIBLE, never landed → the ONE confirmable property is COUNT →
  make COUNT exact; NEVER print rmse/cover as fidelity. SIZE is measurable (per-class median bbox, §PRIM) — size only.
- **Host-bind** snaps a floating fixture onto its MEASURED host **only when one exists in reach, else REFUSE (stays floating,
  counted)** — never fabricated onto a host. Count is always preserved (bound ∪ refused = input).
- **No fabricated networks.** If the IFC has no ports/connectors, do not synthesise a duct/pipe network (SC vent-extraction
  was REFUTED at source and dropped for exactly this reason — prompt §ADDENDUM).

## §5 — LOD / mesh (POC vs finish state)
- **POC (now):** GENERATED fixtures render as a per-ifc_class representative primitive BOX sized to the class's MEASURED median
  bbox (`§PRIM`, `W-DW-PRIM`). Goal = visually impressive + working, **without cheating** (size measured, count exact, position
  plausible/host-bound).
- **Finish state (future):** the modeller swaps a high/fine **component library** (LOD400 sprinkler/grille/outlet meshes) for the
  primitive — same placement, richer mesh. Keep the seam: a borrowed device is a SEPARATE class so its mesh can be upgraded
  independently.

## §6 — What the witnesses test (so a TEST is not mistaken for production)
- `witness_disc_walk_generalize.js` (**§DWG**) walks **terminal_rules on SH/DX/SC** — this is a **GENERALIZATION TEST** (proves
  Terminal-mined density transfers to a building it was never mined from). It is **NOT the production path** (production = §1
  mapping). Do not read §DWG as "we walk Terminal on small buildings."
- `witness_disc_walk_duplex_generalize.js` (**§DXG**) = the residential-rules clash-gate generalization.
- `witness_shim_select.js` (**W-SHIM-SELECT**), `witness_dwwalk_hostbind.js` (**W-DWWALK-HOSTBIND**),
  `witness_hostbind_agnostic.js`, `witness_elec_hostbind.js`, `witness_walkback_mep.js` = the host-bind / selection-key /
  walk-back layers. Host-bind is **DEFAULT-ON** since §SHIM-SELECT (2026-06-30); `{noHostBind:true}` restores raw floating.
