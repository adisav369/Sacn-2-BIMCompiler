# RESUME — Last Mile: STR & ELEC round-trip REDs (the grid-lock crux)

```
# ⚠ DO NOT REMOVE
SCOPE: Close the two remaining RED disciplines in the Terminal rule round-trip (STR, ELEC). The mining +
disc-walker + Router(nn) arc is DONE (see RESUME_TERMINAL_RULE_MINING.md §STATUS). This is the LAST MILE.
PRIME GUARD: do NOT tune thresholds to pass. The fix is to REPLACE A WRONG MODEL with the MEASURED RIGHT
model and EARN the green by re-measuring — exactly as pipes/ducts (routed) and FP IfcAlarm (host-attach) were
earned. Read the §-log after every run; verdicts are proven by the witness log, never by inspection.
ENGINE source-of-truth = bim-compiler/build/disc_walker.js (deployed copy bim-ootb/modeller/disc_walker.js).
Round-trip witness = build/witness_terminal_rules.py (the verdict oracle: §TRM-RT / §TRM-RT-DISC).
```

## THE STATE (measured, `python3 build/witness_terminal_rules.py`)
```
§TRM-RT-DISC ACMV GREEN | FP GREEN | PLB GREEN | roof GREEN | ELEC RED | STR RED
```
Per-class (mode=placed means scored by the z-band Placer; routed means scored by the chain rule):
```
STR  IfcColumn  placed cover=0.91 cnt_err=0.08  GREEN   ← point-like, sits on a grid → Placer FITS
STR  IfcBeam    placed cover=0.75 cnt_err=0.05  RED     ← count is fine; cover<0.85 only
STR  IfcMember  placed cover=0.64 cnt_err=0.36  RED     ← roof space-frame truss
ELEC IfcLightFixture     placed bands=4 cover=0.81 cnt_err=0.22  RED   ← but §TRM-ARRAY ratio 1.00–1.11 GREEN
ELEC IfcElectricAppliance placed bands=1 cover=0.05 cnt_err=0.58  RED  ← single band, 19 elems at many heights
```

## THE DIAGNOSIS (model-mismatch, not data/cadence — high confidence)
The Placer model is **"measured cadence (array) on a z-band datum"**. It fits things that sit on a horizontal
plane at a storey (FP sprinklers, ACMV terminals, STR columns, roof plates — all GREEN). It is the **WRONG
LENS** for two shapes, and the round-trip currently forces both through it:

1. **SPANNING members** (STR `IfcBeam`, `IfcMember`). A beam is a RUN between two column-grid nodes; a roof
   member is a chord of a 3D truss. Their centres scatter in z (rmse, low cover) because they are not points
   on a datum — they are edges of a frame. **The proof is in the SAME discipline:** `IfcColumn` (point-like)
   is GREEN, `IfcBeam`/`IfcMember` (span-like) are RED, under the identical Placer. This is precisely the
   "Gap 3" already solved for pipes/ducts — see `witness_terminal_rules.py:81` `NETWORK_CLASSES`:
   *"a pipe/duct run threads the whole building; a z-band can't reproduce it… score by ROUTING instead."*
   STR beams/members have routing rules ALREADY (`rule_routing`: `IfcBeam→IfcColumn` grid,
   `IfcMember→IfcMember` nn, `IfcColumn→IfcColumn` grid) but are NOT in `NETWORK_CLASSES`, so they fall
   through to the z-band Placer and go RED.

2. **MULTI-HEIGHT host-mounted devices** (ELEC `IfcElectricAppliance`, cover=0.05). 19 appliances live at
   wall/floor/ceiling heights at once — a single (or few) z-band can NEVER cover a class spread across the
   section. This is the shape FP `IfcAlarm` had, now GREEN via the **SHIM host-attach** model (`hostWalls`,
   `ref_kind='host'`, pos=wall, z=floor+measured dz, yaw=wall normal). Appliances are host-mounted too.

3. **ELEC `IfcLightFixture`** is the subtle one: its **array cadence already reproduces GREEN**
   (`§TRM-ARRAY` ratio 1.00–1.11) — the XY ceiling grid is right. It is RED only on the z-band's second lens
   (cover 0.81<0.85, cnt 0.22>0.15) because suspended lights hang at slightly varied drops. For a class whose
   measured array cadence already re-measures, the raw z-band count is a strictly-weaker, redundant proxy.

## THE THREE MOVES (each anchored to an ALREADY-GREEN analogue — NOT new invention)
Order by confidence. Each must EARN its green by re-measuring, not by loosening a bound.

### Move 1 — STR spans are ROUTED, not placed (highest confidence; mirrors PLB/ACMV)
- Recognize span-shaped structural classes (`IfcBeam`, `IfcMember`) as **routed**, scored by their span rule,
  not the z-band. The mechanism already exists (the `NETWORK_CLASSES` branch).
- ⚠ ANTI-TUNING TRAP: today the routed verdict for STR grid prints `structural=not-distance-scored` — adding
  beams to `NETWORK_CLASSES` as-is would be a **rubber stamp, not a proof**. DON'T. Instead give spans a REAL
  **span round-trip**: do beam endpoints land on the measured column-grid bays (12/6/8 m)? does the roof
  member set reproduce the measured chord lengths / truss footprint? Earn GREEN by the span re-measuring,
  exactly as the nn-chain gap re-measures for pipes (see `witness_disc_route_nnchain.js` R4 cadence ±25%).
- The `routeChains()` engine (just shipped, §ROUTER-NNCHAIN) already produces real nn segments for
  `IfcMember→IfcMember`; extend the same measured-span scoring to the grid patterns (`Beam→Column`,
  `Column→Column`) — a grid span is two real nodes + the measured bay, the analogue of an nn gap.

### Move 2 — ELEC IfcElectricAppliance via SHIM host-attach (mirrors FP IfcAlarm, already GREEN)
- The SHIM model (`disc_walker.js` `hostWalls`/`ref_kind='host'`) tacks a device onto its real host surface at
  the measured dz. Re-measure appliance→host: are the 19 appliances wall/floor mounted at a measured offset?
  Reclassify the appliance placement rule `ref_kind='host'` if the measurement supports it. Score by host
  attachment (on-host fraction, measured dz), like W-TRM-SHIM, NOT by a section-wide z-band.
- ⚠ If the measurement shows appliances are NOT host-mounted (free-standing equipment), then per-ROLE z-bands
  (one band per appliance role) is the honest fallback — still measured, not a widened tolerance.

### Move 3 — ELEC IfcLightFixture: the GREEN array cadence IS the reproduction (most tuning-risk; do last)
- For a device class with a GREEN measured `§TRM-ARRAY` cadence, the placement IS reproduced by that cadence;
  the z-band raw-count is a strictly weaker proxy double-penalizing the same fact. The principled verdict for a
  regular-grid ceiling device = its array re-measure (XY spacing ratio in-band) + z-band COVER as a sanity
  floor — not the count-error.
- ⚠ ANTI-TUNING TRAP: this is the move most easily mistaken for threshold-fudging. Frame + defend it as
  "the array is the RIGHT placement model for a ceiling grid; z-band-count was the wrong/weaker lens" and SHOW
  the array genuinely covers (it does: 4 storeys, ratio 1.00/1.84/1.00/1.11 — note L01=1.84 needs a look).
  If you cannot defend it as a model correction without moving a number, DO NOT do it — leave lights RED and
  say so. A defensible WEAK is better than a tuned GREEN.

## WHAT "DONE" LOOKS LIKE (and what is NOT done)
- DONE = STR and ELEC reach GREEN (or an honest, defended WEAK) in `§TRM-RT-DISC`, each via a model that
  RE-MEASURES on the Terminal, with a witness that names the issue it proves. No threshold was loosened.
- NOT done / explicitly OK to leave RED: any class where the right model genuinely does not re-measure —
  report it RED with the measured reason. The PRIME directive (non-invent, earn-don't-tune) outranks a green.
- Regression gate: `witness_disc_walk_generalize.js` 49/49, `_shim.js` 6/6, `_erp_equivalence.js` 14/14,
  `witness_disc_route_nnchain.js` 6/6 must all stay green. Any disc_walker change re-syncs the modeller copy.

## IF YOU RUN OUT OF IDEAS → DISCUSS (user's standing offer)
The genuinely open question behind STR is the **grid-lock crux**: ~71.5% of beams/members sit on the measured
grid, ~28.5% are off-grid by design (cantilevers, bracing, irregular bays). A measured-grid model will never
hit 100% because the building isn't 100% gridded. The open design question to bring to the user: *is the
target "reproduce the regular frame + honestly flag the irregular remainder", or "represent the irregular
members too" (a different, richer model)?* That is a scope decision, not an extraction — surface it rather
than grind.

## POINTERS
- Verdict oracle: `build/witness_terminal_rules.py` (§TRM-RT / §TRM-ARRAY / §TRM-ROUTE / §TRM-RT-DISC).
  `NETWORK_CLASSES` (line 85) + the `mode=routed` branch (line 143) = the existing right-model precedent.
- Engine: `build/disc_walker.js` — Placer `place()`, Router `route()`/`routeChains()`, SHIM `hostWalls()`.
- Rules: `build/terminal_rules.db` (`rule_placement` z-bands, `rule_routing` grid/nn, `rule_space_bom`).
- Arc context + all prior DONE moves: `prompts/RESUME_TERMINAL_RULE_MINING.md` (§ROUTER-NNCHAIN, §SHIM,
  §WALKER-EQUIVALENCE, §PRIOR-ART RECONCILIATION). Memory: `project_terminal_rule_mining.md`.
- Standing rule across this arc + memory: **measure-don't-whitelist, earn-don't-tune**. An honest RED is a
  correct answer.
```
