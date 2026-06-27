# RESUME — Terminal Rule-Mining (en masse, multi-agent) → terminal_rules.db

```
# ⚠ DO NOT REMOVE
SCOPE: MINE measured placement + routing + place-order/avoidance RULES from the Terminal building (the richest
fully-coordinated oracle — all disciplines present) so the modeller's discipline WALKERS reconstruct/route on ANY
building from rules MEASURED from a real building — replacing the thin synthetic mep_rw.db recipes.
NON-INVENT: every rule MUST trace to real measured Terminal elements (guid/coord). No fabricated recipe, no
class whitelist — MEASURE the cadence/offset/band. Acceptance is POSITIONAL (right location, not metres off);
small count error OK (user: 1.3% roof plates fine if located right). Read the §-log after every run.
USER OPT-IN: multi-agent workflow APPROVED 2026-06-27, scope = ALL disciplines en masse (PLB/ACMV/FP/ELEC/STR/
roof) + avoidance manager + adversarial verify + bake. Next session may launch via the Workflow tool directly.
ISOLATION: rules DB is a SOURCE artifact (bake in bim-compiler); the modeller consumes a hosted copy like mep_rw.db.
```

## WHY (user intent, 2026-06-27, verbatim-ish)
> "Extract MEP rules from Terminal that has it all — in fact all its rich disciplines. Plan for it, en masse,
> use agents. One [agent] to manage place order and avoidance, as in Terminal." + "i think u are best at such
> algorithmic patterning challenges."

Terminal is a real, fully-coordinated multi-discipline building. Mining MEASURED rules from it (how a real
project actually placed + routed + stacked each discipline, and how disciplines avoided each other) beats the
synthetic `mep_rw.db` recipes. The mined rules feed every discipline walker (the modeller's disc=walker), so MEP
(and ACMV/FP/ELEC/STR/roof) walk on ANY opened building — not just the few with pre-mined anchors.

## ⚠ §PRIOR-ART RECONCILIATION — READ FIRST, DON'T REINVENT (user hunch 2026-06-27, CONFIRMED)
**The prior Java/ERP work ALREADY built the shim/anchor/offset vocabulary for MEP-as-abstract-BOM-sets in ERP.db.**
`terminal_rules.db` is essentially a MEASURED re-derivation of it. The next session must MAP onto this schema +
vocabulary (and ideally let measured rules FLOW INTO the existing tables tagged `provenance='measured:terminal'`),
NOT fork a parallel one. The mined population is COMPLEMENTARY to the prior hand-curated code standards + extracted
anchors — same tables, two provenances. Canonical refresher: `docs/DISC_VALIDATION_DB_SRS.md §6.12` (shim model /
space-identity / capability layers) + `migration/DV042_placement_offsets.sql` + `migration/W019_mep_anchor_tables.sql`.

**The prior vocabulary (REUSE these terms + tables):**
- **ANCHOR** (`ad_mep_anchor`, W019) — a known building's MEASURED MEP placement point (extraction XYZ + anchor_type
  METER/FIXTURE/VALVE/GENERIC + storey + ifc_guid). = exactly what my `rule_placement.src_guids` point at.
- **PLACEMENT-OFFSET** (`ad_placement_offset`, DV042) — room-edge-relative install rule, RICHER than my flat dx/dy/dz:
  `from_edge_x/y` + `z_rule`(FLOOR|CEILING|MID datum) + `z_offset` + `x_ref/y_ref`(CENTER|MIN|MAX edge) + `standard`
  (building code: IPC 2021, NEC 2020, **NFPA 13 §8.5 = sprinkler grid**, MS 1228). Doctrine match: "no hardcoded
  offsets in code — modellers edit per code; the code READS them, never hardcodes" = our measure-don't-whitelist.
- **SHIM** (`_shim_attributes` + `ShimMatcher.java` + `PlacementCollectorVisitor.java`) — a PHANTOM product = the host
  surface's coordinate frame; a host-attached device tacks off it (mount BOTTOM|SIDE|TOP, offset_mm≈0, carries the
  WALL-NORMAL for facing). **TACK MODEL: parent tack + dx/dy/dz = child tack** (SRS §6.12, lines ~1225-1250) — the
  recursive placement the BOM principle already uses. This is the SOLVED host-attachment; reuse it.
- **SPACE-TYPE BOM** (`ad_space_type_mep_bom`, DV001/DV043) — WHICH fixtures per room type (KITCHEN→{SINK,FRIDGE},
  qty_min/normal/max + per_area + placement_rule FK + host_surface). = my `rule_space_bom`, but space-type-keyed.
- **PATTERN** (`ad_mep_pattern`, W019) — routing topology: from_node→to_node + piece_type + gradient + offset_rule.
  = my `rule_routing`. **NOTE Terminal has REAL JUNCTION/STACK topology to mine into from_node/to_node + gradient.**
- **DISCIPLINE = AD_Org + CAPABILITY** (DV036) — CW=6/PLUMBABLE · SP=7 · FP=3/FIRE_PROTECTED · ACMV=5/VENTILATED ·
  ELEC=4/ELECTRIFIED · LPG=8/GAS_SERVED. THIS is the "abstract BOM sets in ERP.db" — MEP = a discipline-keyed set of
  abstract BOMs each claiming a capability, dispatched at compile by space-type→capability match. Reuse this model.

**Reconciliation map (terminal_rules.db ↔ prior schema):**
| terminal_rules.db | prior ERP.db/mep_rw.db | reconcile action |
|---|---|---|
| `rule_placement(disc,class,ref_kind,dx,dy,dz,z_band)` | `ad_placement_offset` (DV042) | ADOPT z_rule(FLOOR/CEILING/MID)+x_ref/y_ref+`standard` (room-edge-relative + code-tagged), not flat dz |
| `rule_space_bom(scope,class,count_per,spacing)` | `ad_space_type_mep_bom` (DV001) | key by space_type + qty_min/normal/max + per_area |
| `rule_routing(from,to,pattern,params)` | `ad_mep_pattern` (W019) | adopt from_node/to_node/piece_type/gradient/offset_rule |
| `rule_placement.src_guids` | `ad_mep_anchor` (W019) | measured anchors = the provenance of an offset rule |
| `rule_place_order`+`rule_avoidance` | (NEW — prior art had no cross-disc gate) | genuinely new; keep |

**GAPS in the current disc_walker.js vs prior art (the "don't reinvent" fixes for next session):**
1. **Adopt the SHIM for host-attached devices** — my Placer's "single placement" path drops at storey centroid; the
   prior `_shim_attributes` (host_ifc_class + mount + wall-normal facing + tack) is the RIGHT host-attach for alarms-
   on-walls / sprinklers-off-ceiling-coverings. Reuse `ShimMatcher`, don't re-solve host attachment.
2. **Adopt z_rule datum semantics** — replace flat `dz` with FLOOR/CEILING/MID datum + edge-relative x_ref/y_ref so
   placement is room-edge-relative + code-comparable (DV042), not absolute-frame.
3. **Tack-chain emit, not markers** — emit placements as tack offsets into the signed op-log/BOM (parent tack +
   dx/dy/dz), so a walk is undoable + folds to the enterprise (ties the "fold into op-log" next step + RouteWalker's
   GEOM_SWEEP path). The recursive tack model is already the BOM principle.
4. **Space-type keying needs room qualification** — Terminal meta has IfcSpace=0 (LANDMINE below); space-type BOM
   keying must come from `bom_tree.seedFromDb` door+AABB room qualification, not IfcSpace.
5. **Mine Terminal's REAL routing topology into PATTERN** — Terminal has JUNCTION/STACK/gradient signal (the thing
   that BLOCKED the old W-WALKBACK-MEP RouteWalker: 0 segs, no JUNCTION anchors). Mining it fills `ad_mep_pattern`.

## GROUNDED INVENTORY (measured from modeller/Terminal_meta.db — the minable signal)
| Disc | count | real topology to mine |
|---|---|---|
| ARC | 35552 | envelope, 43 IfcSpace rooms, hosts |
| PLB | 8175 | pipe networks: IfcPipeSegment 3821 + IfcPipeFitting 4243 + IfcValve 111 |
| ACMV | 1570 | duct networks: IfcDuctSegment 568 + IfcDuctFitting 713 + IfcAirTerminal 289 |
| FP | 989 | sprinkler grid: IfcFireSuppressionTerminal 909 |
| STR | 1032 | beams 432 + members 442 + columns 158 |
| ELEC | 833 | IfcLightFixture 814 |
| roof | IfcPlate 33324 | the patterned plates (per-element on the pattern; see roof note in RESUME_MODELLER_UX) |
(meta.db has no port_connections rows — real MEP port topology may live in Terminal_geo.db (LFS); check there for connectivity, else infer topology by nearest-neighbour segment→fitting→terminal chains.)

## RULE TAXONOMY (what each discipline agent mines — all MEASURED)
1. **PLACEMENT** — per-class offset to room / grid / host (reuse the SDG: rel_anchored signed offsets, rel_fills_host,
   datum cadence); per-space-type fixture BOM (which fixtures per room type, counts, spacing); Z-elevation bands.
2. **ROUTING / TOPOLOGY** — segment→fitting→terminal chains; mains/risers; nearest-neighbour pairing; bend cadence
   (mined from real IfcPipeSegment/Fitting + DuctSegment/Fitting runs). Port connectivity if Terminal_geo has it.
3. **PLACE-ORDER + AVOIDANCE** (the manager agent — user's key ask) — the SEQUENCE disciplines occupy space and
   the CLEARANCE bands measured from how Terminal actually stacked them (e.g. ACMV high in plenum, PLB below, FP,
   ELEC) → a deterministic place-order + clash-gate every walker obeys (yield/refuse, never overlap). "as in Terminal".

## APPROVED MULTI-AGENT WORKFLOW (scope = all disciplines en masse; ~14 agents, ~0.5–1.5M tokens)
- **Phase 1 inventory** — disciplines × classes × rooms (table above; re-confirm inline).
- **Phase 2 MINE (fan-out, 6 parallel)** — one agent per discipline {PLB, ACMV, FP, ELEC, STR, roof}: measure
  placement + routing rules from Terminal → a schema'd rule set (StructuredOutput). Each rule carries srcGuids it
  was measured from.
- **Phase 3 AVOIDANCE MANAGER (1 agent)** — measure cross-discipline Z-bands, clearances, place-order across ALL
  disciplines → the coordination/clash matrix.
- **Phase 4 ADVERSARIAL VERIFY (6 parallel)** — each re-measures/refutes its discipline's rules vs Terminal:
  non-invent gate (every rule traces to real elements; zero fabricated recipe); drop any rule that can't be re-measured.
- **Phase 5 SYNTHESIZE + BAKE** — write `terminal_rules.db` (measured tables, provenance='measured:terminal') +
  a ROUND-TRIP witness: replay rules → reproduce Terminal's per-discipline layout within tol (POSITIONS not
  metres-off; count within a few %). This is the §-proof.

## terminal_rules.db (schema sketch — refine in Phase 5)
- `rule_placement(disc, ifc_class, ref_kind{room|grid|host}, dx,dy,dz, z_band_lo, z_band_hi, provenance, src_guids)`
- `rule_space_bom(space_type, disc, ifc_class, count_per, spacing_m, provenance, src_guids)`
- `rule_routing(disc, from_kind, to_kind, pattern{nn|main|riser|bend}, params_json, provenance, src_guids)`
- `rule_place_order(disc, order_index)` + `rule_avoidance(disc_a, disc_b, min_clear_m, yields{a|b}, z_band, provenance)`

## §CONVERGENCE — IT ALL COMES TOGETHER AT THE OUTLINER DISC TABS (the end-point, architect's organizing layer)
**The whole program exists to make ONE gesture true: a user clicks a Discipline node in the modeller's Outliner →
that discipline WALKS the open building from rules MEASURED off Terminal → the walked result renders back under
that disc node + mirrors in 3D.** Everything above (mine → verify → bake) is the SOURCE half; this is the SINK the
source must plug into. Design the rule schema and the bake so the consume side is a drop-in, not a re-interpretation.

### The end-point as it already exists (DO NOT rebuild — wire into it)
- `viewer/bonsai_outliner.js`: a DISC node carries `data-disc` + a ▶ glyph; click → `cat.onWalk(disc)` (line ~256).
  Today `onWalk` (in `bom_tree_outliner.js` / `str_walker_outliner.js`) dispatches: **STR → `swbInit`** (real walk),
  **MEP/etc → `rwWalk(buildingDb,name)`** when anchored, **else HONEST REFUSAL** (`§W-UX-4`, no fabrication).
- `viewer/routewalker.js`: `rwInit(SQL, baseUrl)` opens the hosted recipe DB; `rwWalk(...)` →
  `{fixtures, pipes, clashSkipped, total}`, writes `elements_meta`+`element_transforms` + op-log `GEOM_SWEEP`.
  **Path A = pre-mined anchors, Path B = room-recipe.** Both read the THIN synthetic `mep_rw.db` today.
- Substrate the walk binds to (already derived on Open, NON-INVENT): rooms via `bom_tree.seedFromDb`
  (door+AABB-qualified), datums/anchors via `cross_edges.deriveAll` → `window.swXEdges` (rel_anchored signed
  offsets, rel_fills_host hosts, datum_plane cadence). **The rules reference the SAME edge kinds the SDG derives.**

### The seam: `terminal_rules.db` IS a richer, drop-in `mep_rw.db` (schema must MAP, not merely resemble)
Bake so `rwInit`/`rwWalk` reads `terminal_rules.db` with minimal branching — each mined table is a measured
superset of an existing recipe table the walker already consumes:
| terminal_rules.db (mined)            | maps onto walker input (today)        | walker use |
|---|---|---|
| `rule_placement(disc,class,ref_kind,dx,dy,dz,z_band)` | `ad_placement_offset` | per-class offset to room/grid/host |
| `rule_space_bom(space_type,disc,class,count_per,spacing_m)` | `ad_space_type_mep_bom` | which/how-many fixtures per room |
| `rule_routing(disc,from_kind,to_kind,pattern,params_json)` | (new) drives `_rwConnectFixtures`/sweep chains | seg→fitting→terminal runs |
| `rule_place_order` + `rule_avoidance` | (NEW layer — see gate) | cross-disc ordering + clash yield |
The **`disc` column is the unifier** — `mep_rw.db` was MEP-only; `terminal_rules.db` carries PLB/ACMV/FP/ELEC/STR/
roof, so EVERY DISC node in the Outliner draws from ONE measured source. STR keeps `swbInit` but its cadence/grid
rules now also live here (ties grid-lock crux); roof walks per-element on its mined plate pattern (not one piece).

### THE ELEGANT SHARED ABSTRACTION (user 2026-06-27: "good abstract components pattern that all can share")
The whole point of mining ALL disciplines into ONE schema is that **every discipline walker is the SAME engine —
disciplines differ only in DATA (rule rows), never in code.** Three abstract components, shared by PLB/ACMV/FP/
ELEC/STR/roof alike:
- **Placer** — reads `rule_placement`/`rule_space_bom`, emits positions from `(storey|grid|host|datum anchor) +
  measured offset/spacing/array`. A sprinkler grid, a light array, a roof-plate array, a column cadence are ALL
  "array on a datum with measured spacing" — one Placer, different rule rows. (This is why roof and FP and ELEC
  collapse to the same code path: a measured cadence on a datum.)
- **Router** — reads `rule_routing`, chains `from_kind→to_kind` by the measured pattern (nn / main / riser / bend).
  Pipe runs and duct runs are the SAME chain walk over different `disc` rows.
- **Gate** — the AvoidanceGate below: one cross-disc ordering+clash component every walker passes through.
The disc node's `onWalk(disc)` just selects `WHERE disc=?` and runs Placer→Router→Gate. **No per-class/per-disc
branch in the engine (grep-clean, like the SDG builders) — the discipline is a data filter, not a code fork.**
STR already has `swbInit`; the elegance is that its grid cadence is just another Placer rule, so it reconciles
with the same datum substrate the other disciplines anchor to (ties the grid-lock crux to one shared mechanism).

### The NEW runtime piece the user asked for: the AvoidanceGate (place-order manager, "as in Terminal")
`rule_place_order` + `rule_avoidance` become a runtime `AvoidanceGate` every `onWalk` passes through:
- **Order:** when a disc walks, the gate replays it in measured place-order against already-walked discs on this
  building (ACMV high in plenum → PLB below → FP → ELEC, per Terminal's measured z-bands).
- **Clash:** each placed element is gated vs occupied bands (`min_clear_m`, `yields{a|b}`); on conflict the lower-
  priority disc YIELDS (shifts within its band) or REFUSES (never overlaps). This is the `clashSkipped` counter
  made measured + cross-disc, surfaced honestly in the disc tab.

### The DISC tab render contract (honest states, mirror the STR walker's RED/ORANGE/GREEN)
After `onWalk(disc)` the node re-renders its walk under itself in the Outliner + 3D mirror:
- **GREEN** — rules matched, placed N (count within a few %, positions sub-bay). Show N + signal counts.
- **ORANGE** — AvoidanceGate yielded/shifted an element to clear another disc (a resolved clash). Surface the count.
- **RED / honest refusal** — no rule covers this disc on this building's substrate → "no walk (no measured rule)",
  ZERO fabricated placements (the existing no-anchor refusal, now keyed on rule coverage not just anchors).

### Phase 6 (ADD to the workflow) — WIRE the bake to the Outliner DISC end-point
After Phase 5 bakes + round-trips on Terminal, the program is only HALF done until a DISC click consumes it:
1. **Place LOCALLY — NO OCI, NO deploy ceremony (user 2026-06-27: "don't drift into OCI; local bim-ootb meta.db
   is sufficient as done").** For this POC just copy the baked `build/terminal_rules.db` into the LOCAL
   `~/bim-ootb/modeller/` next to the resident meta DBs (SampleHouse/Duplex/SampleCastle/Terminal `*_extracted.db`)
   and load it the same local way (`../modeller/terminal_rules.db`). No OCI upload, no GH-pages publish — the local
   modeller serving the local meta DBs is the whole loop. (Hosting/`?v=` cache-bust is a LATER concern, not now.)
2. **Consume** — point `rwInit` at `terminal_rules.db`; map Path B to read `rule_*` (table above) via the shared
   Placer/Router/Gate components (one engine, `WHERE disc=?` is the only per-disc selector). Add the
   `AvoidanceGate` between `onWalk` and placement-commit. Register PLB/ACMV/FP/ELEC/roof in the disc-walker
   dispatch so each `data-disc` node walks (STR already does).
3. **GENERALIZE gate (the real proof of the program):** the rules are MINED on Terminal but must WALK on ANY
   building. End-to-end witness: open SampleHouse/Duplex/SampleCastle in the modeller → click each DISC node →
   `onWalk` walks from `terminal_rules.db` → §-log placements + AvoidanceGate decisions → render under the disc.
   A disc with no matching rule for that building → honest RED refusal (non-invent), NOT a fabricated run.
   (Round-trip on Terminal = Phase 5 = rules are faithful; walk on SH/DX/SC = Phase 6 = rules GENERALIZE.)

## ACCEPTANCE / GATES
- CONVERGENCE: schema maps to the walker seam (table above); a DISC click in the Outliner walks from
  `terminal_rules.db` and renders GREEN/ORANGE/RED honestly; AvoidanceGate ordering+clash is measured, not coded.
- NON-INVENT: every rule row has real src_guids; adversarial verify drops un-re-measurable rules.
- ROUND-TRIP: rules replayed onto Terminal reproduce its layout — per-discipline position-RMSE small (sub-bay /
  sub-metre), count within a few % (roof 1.3% OK). Positions never metres-off.
- MEASURE, don't whitelist: no Ifc-class/role string drives a rule branch (grep-clean), like the SDG builders.

## CONSUMERS / CROSS-LINKS
- **END-POINT = the Outliner DISC tabs** (see §CONVERGENCE above) — `RESUME_MODELLER_UX_OUTLINER_PILL.md §W-UX-4`:
  a `data-disc` node → `cat.onWalk(disc)` → walks the open building from these rules → renders GREEN/ORANGE/RED.
  This is the SINK the whole program plugs into; design schema + bake for the walker seam (don't re-interpret).
- Feeds the modeller disc=walker: MEP/ACMV/FP/ELEC/STR/roof walk from these rules on ANY building (supersedes the
  narrow "wire RouteWalker Path B" step — Path B exists: rwWalk room-recipe, no anchors, but its recipes are
  thin/synthetic; replace with terminal_rules.db read by `rwInit`, gated by the new AvoidanceGate).
- The avoidance manager + place-order = the clash layer for the forward fold (RESUME_MODELLER_UX §3).
- Grid-lock crux (RESUME_MODELLER_UX headline): the mined STR/cadence rules tie into datum emergence.
- Existing: mep_rw.db already has a 'Terminal' MEP anchor key + RouteWalker(routewalker.js, port of RouteWalker.java)
  Path A(anchors)/Path B(room-recipe). This program mines RICHER, all-discipline rules to replace those recipes.

## STATUS
**POC MINED + BAKED + ROUND-TRIPPED 2026-06-27** (15-agent workflow, 644K tok). Source artifacts (LOCAL, no OCI):
`build/terminal_rules.db` (5 tables, **124 rows**, every row n_measured + provenance='measured:terminal*'),
`build/bake_terminal_rules.py` (re-runnable), `build/witness_terminal_rules.py` (+ `.log`).
Rows: rule_placement 35 · rule_space_bom 3 · rule_routing 11 · rule_place_order 28 · rule_avoidance 47.
Coverage: all 6 disciplines (PLB/ACMV/FP/ELEC/STR/roof). Adversarial verify dropped weak rules (ACMV term-array
1.0m pitch unverifiable; ELEC/STR wrong-storey src_guid rows; STR 'bracing' catch-all) — non-invent held.
ROUND-TRIP (`§TRM-RT-DISC`, AFTER gap-fix 2026-06-27): **roof GREEN · FP GREEN · PLB GREEN · ACMV GREEN · ELEC RED
· STR RED** (placement-rollup green=9/weak=0/red=4 of 13 classes; was green=6/weak=2/red=5 before the network-
scoring fix). KEY WINS (positional, the gate that matters): roof IfcPlate
33324/33324 count + 0.15m spacing EXACT; FP sprinkler grid 3.0/3.2m + 909 count EXACT; STR column 6.0m grid EXACT
(ties grid-lock crux); PLB valve 111 EXACT; routing chains GREEN (PLB fitting→seg 0.15m, valve→fitting, ACMV
seg→fitting nn). KEY LEARNING — **validates the shared abstraction**: the **Placer** nails ARRAYS (roof/FP/ELEC-
grid/STR-columns all measured-cadence-on-datum, GREEN), the **Router** nails NETWORKS (PLB/ACMV chains, GREEN); the
REDs are disciplines forced through the WRONG component — pipe segments (3821) & duct runs are ROUTED not band-
PLACED (z-band count_err 0.81/0.34 = wrong lens), STR beams/members are off-grid by design (~71.5% on-grid =
the grid-lock crux, not a bug). ELEC array IS GREEN (1.25–2.2m grid); its RED is only the harsh z-band-count metric.
### GAPS 1–3 ✅ DONE 2026-06-27 (artifact now fully clean):
1. ✅ **PLB placement src_guids** — re-queried real Terminal guids by storey+z-band for all 5 PLB placement rows;
   0 blank src_guids remain (all 35 placement rows carry a trace). NON-INVENT check: **157/157 src_guids resolve
   to real elements, 0 missing, 0 class-mismatch**.
2. ✅ **Wrong-storey src_guids** — STR L01/L02 columns (cited Aras-Tanah `12OO…`) + ACMV upper air-terminals
   (cited Aras-Tanah `3OCW…`) re-pulled from the correct storey+z-band. (ELEC Aras-02 appliance was already
   dropped by the verifier — not in the baked payload.)
3. ✅ **Networks routed, not band-placed** — witness now scores NETWORK_CLASSES (pipe/duct seg+fitting) by their
   measured chain rule (`mode=routed`), not the z-band count. PLB pipes + ACMV ducts → GREEN via the Router.
   Rollup green 6→9, red 5→4; PLB+ACMV disciplines flipped to GREEN.
### PHASE 6 ENGINE ✅ DONE 2026-06-27 (the GENERALIZE proof — the real proof of the program):
- **Shared engine `build/disc_walker.js`** (source copy; deploy target = `bim-ootb/viewer/disc_walker.js`) =
  the ELEGANT SHARED ABSTRACTION realized: **Placer** (rule_placement→array-on-a-datum, tiles the target
  building's real storey footprint at the measured spacing+dz), **Router** (rule_routing→nn chains, only when
  the target has real from/to elements), **Gate** (rule_place_order+rule_avoidance→cross-disc order + clash-yield).
  Discipline = a DATA filter (`WHERE disc=?`), zero per-class code fork. RELATIVE dz+spacing transfer; absolute
  Terminal z-bands are NOT used for placement (they don't transfer). `terminal_rules.db` copied to LOCAL
  `~/bim-ootb/modeller/` (no OCI).
- **Witness `build/witness_disc_walk_generalize.js` = 49/49 PASS** (`NODE_PATH=~/bim-ootb/tests/node_modules`):
  rules MINED on Terminal WALK on SampleHouse/Duplex/SampleCastle (never mined from them):
  - G1 placements land IN-FOOTPRINT, z finite (FP/ELEC/STR on all 3); G2 **cadence TRANSFERS** — placed nn_xy ≈
    rule spacing (FP 3.0→2.83-3.18, ELEC 1.98→1.89-1.96, STR 6.0→5.66-6.35; ratios 0.94-1.06);
  - G3 PLB router HONEST = 0 chains on pipe-less residents (no fabrication); G4 AvoidanceGate runs (measured
    min_clear 2.43m; yields 0/137/1414 by building); G5 unknown disc → REFUSED 0 placed; G6 every placed class ∈ rules.
### PHASE 6 UI WIRING + DEPLOY ✅ DONE 2026-06-27 — bim-ootb PR #549 (auto-merge armed SQUASH off fresh main):
- `viewer/disc_walker.js` deployed; `modeller.html` `discWalk()` MEP-family branch → `DiscWalker.dwWalk`; placements
  render as 3D markers (ORANGE where the Gate yielded); NEW **"Walk · Disciplines"** Outliner roster lists the
  measured disciplines (DERIVED `disciplines()`) so a discipline ABSENT from the building (FP on a house) is
  walkable; honest REFUSE where no rule. `str_walker_outliner.js` stashes the building buffer + ensures engine/roster.
  `modeller/terminal_rules.db` shipped (whitelisted in .gitignore like residents, local Pages blob, NO OCI). sw v736→v737.
- WITNESSED: **W-TERM-WALK 9/9** (headless: roster shows FP absent-from-house, click→53 placed + 3D markers, ELEC
  co-resident, generic MEP honest-refuse, no LOAD_FAIL) + **W-UX-DISC 8/8** (updated) + audit_specs 0 new.
- ⚠ ORPHAN-TRAP honored: all commits pushed BEFORE arming auto-merge; do NOT push again to feat/disc-walker.
### §PRIOR-ART RECONCILIATION ✅ DONE 2026-06-27 (W-TRM-RECONCILE 11/11) — measured rules FLOW INTO ERP.db vocab:
- **Generator `build/reconcile_terminal_rules.py`** (re-runnable) reads `build/terminal_rules.db` + resolves every
  src_guid's XYZ from `Terminal_meta.db` (NON-INVENT linchpin) → emits the append-only migration
  **`migration/TRM001_terminal_measured_rules.sql`** (wired into `scripts/rebuild_erp.sh` Phase 8c, after DV042/W019).
- **FLOW-IN (no fork):** `rule_routing`→**existing `ad_mep_pattern`** (W019; source_building='Terminal', abstract node
  vocab SEGMENT/JUNCTION/VALVE/FIXTURE, n_measured+params in notes); `src_guids`→**existing `ad_mep_anchor`** (162 real
  anchors, anchor_id `TRM:<guid>`, XYZ from meta — prior 340 Terminal anchors UNTOUCHED).
- **ADOPT vocab:** `rule_placement`→`ad_placement_measured` reuses DV042's `z_rule/z_offset/x_ref/y_ref/standard` +
  FKs the named `ad_placement_offset` rule (sprinkler→CEILING_GRID/NFPA 13, light→CEILING_CENTER). z_offset is
  FLOOR-relative = what `disc_walker.js` already transfers; the FK carries the prior-art datum INTENT = two provenances.
- **NEW layer:** `ad_place_order` (28) + `ad_clash_avoidance` (47, = the SRS AD_Clash_Rule clearance/yield layer).
  `rule_space_bom`→`ad_space_bom_measured` (CANNOT key `ad_space_type_mep_bom`: Terminal IfcSpace=0 — gap documented).
- **Witness `build/witness_reconcile_terminal_rules.py` = 11/11** (`.log`): G1 row-parity (35/11/3/28/47 all match),
  G2 162 anchors 0 fabricated XYZ, G3 z_offset==dz lossless, G4 no-fork (prior 340 preserved, routing in existing
  table), G5 FK valid (11 FK'd, 0 dangling), G6 every row provenance='measured:terminal*'. Applied to live ERP.db.
- ⏭ NOT YET (prior-art §3 follow-on): tack-chain emit into the signed op-log (parent tack+dx/dy/dz) — disc_walker
  still renders markers. (§1 SHIM host-attach is now DONE in the engine — see §SHIM below.)

### §SHIM host-attach ✅ DONE 2026-06-27 (W-TRM-SHIM 6/6) — engine adopts prior-art ShimMatcher model (gap #1):
- `build/disc_walker.js` Placer: a `ref_kind='host'` rule (FP IfcAlarm) no longer drops at the storey centroid — it
  TACKS onto a REAL host wall in the target storey (`hostWalls`): position=wall centre, z=floor+measured dz,
  yaw=wall `rotation_z` (the host normal = the SHIM facing), prov='shim:host-wall', stamps `host` guid. Count bounded
  by the measured `rule_space_bom.count_per` (`countPer`, 16/storey) and #real walls. NON-INVENT: every position is a
  real wall; height+count measured; honest skip where a storey has no walls.
- **Witness `build/witness_disc_walk_shim.js` = 6/6** (`.log`, SampleCastle): S1 96 placements ALL on real walls (0
  off-host), S2 85 distinct host positions (not centroid), S3 96/96 inherit host yaw, S4 measured height, S5 ≤
  measured/host bound per storey, S6 host rule engaged. NO regression: equivalence 14/14, generalize 49/49 still green.
- ⏭ DEPLOY pending: port to `bim-ootb/viewer/disc_walker.js` + render the yaw/host-wall marker (deploy session).

### §WALKER-EQUIVALENCE ✅ DONE 2026-06-27 (W-TRM-WALK-EQUIV 14/14) — ERP.db is a DROP-IN rule source:
- TRM001 now also emits `ad_routing_measured` (measured routing sibling, symmetric to `ad_placement_measured`,
  retains the precise IFC classes the disc_walker Router needs — `ad_mep_pattern` keeps node tokens for the prior-art
  RouteWalker = one mine, two consumers) + 5 compatibility VIEWS (`rule_placement`/`rule_routing`/`rule_place_order`/
  `rule_avoidance`/`rule_space_bom`) projecting the reconciled tables back to the engine's exact contract (z_offset→dz).
- **Witness `build/witness_disc_walk_erp_equivalence.js` = 14/14** (`.log`): the REAL `build/disc_walker.js` engine,
  `dwOpen`'d on `library/ERP.db` (views) vs `build/terminal_rules.db`, walks SampleCastle (409,188 placements)
  POSITION-IDENTICAL across all 6 disciplines (E2 pos≡), same roster (E1), same router chains incl STR IfcMember
  nn chain (E3 — IFC classes survived the reconciliation). Proves §CONVERGENCE "drop-in, not re-interpretation".
- Reconcile witness now 12/12 (added ad_routing_measured parity).

### §ROUTER-NNCHAIN — make the Router half LIVE on a real-MEP building (SPEC 2026-06-27)
**Problem:** the residents (SH/DX/SC) carry no MEP network, so the Router honestly returns 0 chains — the Router half
has never been *seen* producing geometry. The Terminal (already a modeller resident, `Terminal_meta.db`) IS MEP-rich
(3821 IfcPipeSegment, 4243 IfcPipeFitting, 111 IfcValve, 568 IfcDuctSegment, 713 IfcDuctFitting, 289 IfcAirTerminal),
with real `element_transforms` positions. **Demo the Router live there.**

**Current `route()` only COUNTS** (`{from,to,pattern,n_from,n_to}` — does the building have both endpoint classes?).
Make it PRODUCE real nearest-neighbour-3d **chain segments**: for each `nn` rule, pair every `from` element to its
nearest `to` element in 3D, bounded by the measured `max_m` gap.

**WHITEBOX-PROVEN VIABLE (2026-06-27, before any code):** spatial-hash nn-3d on the real Terminal PLB
IfcPipeFitting→IfcPipeSegment: 4207/4243 paired, **mean gap 0.1397m vs measured avg_gap_m=0.123** (~14%), all ≤
measured max 0.88m, 36 honest no-neighbour, **20ms** (grid bucketing, NOT brute force — 4243×3821 would be 16M pairs).

**Design (NON-INVENT):**
- `routeChains(disc, bdb)` in `disc_walker.js`: per `nn` rule, load real `from`/`to` positions from `element_transforms`;
  build a spatial hash of `to` at cell=`max_m`; for each `from` find nearest `to` within `max_m`; emit a segment
  `{disc, rule:'nn', from_guid, to_guid, from:[x,y,z], to:[x,y,z], gap, max_m}`. **Every segment connects TWO REAL
  elements at their REAL measured positions** — the only derived thing is the pairing, and it is bounded by the
  measured gap so no implausibly long run is fabricated. **Honest:** count + log `from` elements with no neighbour.
- Keep the old `route()` count API (consumed by status/log); `dwWalk` adds `chainSegs` from `routeChains` (cheap; only
  for `nn` rules; `main`/`riser` patterns stay descriptive — they need orientation fits, a later piece).
- **Wire to modeller** (extends the tack-chain §DW-OPLOG): render each segment as a 3D run AND commit a signed
  **`GEOM_SWEEP`** (path=[from,to], `parameters._dw={disc,rule,from_guid,to_guid,gap}`) so the routed network is
  undoable + enterprise-foldable, exactly like the placement tacks.

**ACCEPTANCE / witnesses:**
- Node values `build/witness_disc_route_nnchain.js`: Terminal PLB + ACMV → every segment's two guids are real
  `elements_meta` rows; `gap ≤ max_m` for ALL; `mean(gap)` within ±25% of measured `avg_gap_m`; no-neighbour count
  logged (honest); zero fabricated endpoint. SH/DX (no pipes) → 0 segments (Router honest-0 preserved).
- Headless `modeller/tests/witness_modeller_router_nnchain.js`: open Terminal, walk PLB → `chainSegs>0`, N signed
  `GEOM_SWEEP` ops with `_dw.from_guid/_dw.to_guid` in the op-log, undo/redo clean, no LOAD_FAIL.

### NEXT-SESSION (genuinely remaining):
- **Tack-chain op-log emit** ✅ DONE 2026-06-27 (W-DW-OPLOG 6/6, bim-ootb PR #553 MERGED) — disc-walk placements
  (incl shim:host-wall tacks) commit as signed `GEOM_INSERT` w/ `parameters._dw`, undo/redo clean, markers restored.
- **DEPLOY** — port the SHIM host-attach + ERP.db drop-in to `bim-ootb/viewer/disc_walker.js`, render the host-wall
  yaw marker, verify headless, sw bump (its own deploy session).
- ✅ PR #549 MERGED on origin/main (verified: disc_walker.js + terminal_rules.db + sw v737 present); `/tmp/wt-discwalk` removed.
- Op-log/signed integration: disc-walk currently renders markers; fold placements into the signed op-log (GEOM ops)
  so a walk is undoable + folds to the enterprise like the RouteWalker GEOM_SWEEP path.
- Router on real-MEP buildings (residents have none → 0 chains honest); demo chains on a building WITH pipes/ducts.
- ELEC/STR round-trip REDs (off-grid-by-design / grid-lock crux) remain the hard open problem (separate prompt).
- **ELEC/STR REDs are HONEST, left as-is** — ELEC light-fixtures: array spacing is GREEN but the multi-band z-band
  metric is harsh (placed-on-ceiling-datum, not networked); STR beams/members: off-grid by design (~71.5% on-grid
  = the grid-lock crux, a known hard problem, NOT a bake bug). Don't tune these to pass.
- **Phase 6** — copy `terminal_rules.db` into LOCAL `~/bim-ootb/modeller/`, wire `rwInit`→Placer/Router/Gate,
  register disc walkers, GENERALIZE-walk on SH/DX/SC from the Outliner DISC nodes (the convergence end-point).
- ORIGINAL: NOT STARTED — planned + scoped + approved 2026-06-27 (user said "close, leave prompt"). **Organized 2026-06-27
around the Outliner DISC-tab END-POINT (§CONVERGENCE + Phase 6 added):** mining is the source half; the program is
only done when a DISC click in `bonsai_outliner.js` walks the open building from `terminal_rules.db` through the new
AvoidanceGate and renders honestly. Schema must map to the `rwInit`/`rwWalk` walker seam (the table in §CONVERGENCE).
Launch next session via the Workflow tool (opt-in given): confirm Terminal_geo port connectivity → Phase 2 fan-out
(6 disc agents) → Phase 3 avoidance manager → Phase 4 adversarial verify → Phase 5 bake+round-trip on Terminal →
Phase 6 wire to the Outliner DISC end-point + GENERALIZE-walk on SH/DX/SC.
```
