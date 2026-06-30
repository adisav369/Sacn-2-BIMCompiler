# Walker Maturity Matrix — discipline × capability, graded by EVIDENCE class

> Companion to `docs/internal/WalkerDoctrine.md` (the LOCKED fundamentals). This matrix tracks HOW MATURE each
> walker capability is, by the strength of its evidence — NOT by lines of code. Every cell traces to a witness;
> a claim with no witness is L0. Regenerate the numbers by running the named witness (Log Mandate). Last graded 2026-06-30.

## The maturity ladder (the y-axis you graph)
Maturity = the HONESTY of the evidence, not the feature count. A walker only climbs when a harder test passes.

| Level | Name | What it means | The bar |
|-------|------|---------------|---------|
| **L0** | none | not attempted, or no witness | — |
| **L1** | EMITS + NON-INVENT | engine produces N>0; every output is a REAL guid at its REAL position, gap ≤ MEASURED bound, COUNT exact; honest REFUSE when absent | real, bounded, count-exact |
| **L2** | SELF-CONSISTENCY | scored vs the SAME building the rules were mined from (mine→apply→score-on-self). Precision reported. Can flatter. | precision on mined building |
| **L3** | HELD-OUT | mined from building A, applied to building B, scored vs **B's OWN** geometry. The honesty bar — self-consistency can't reach here. | precision on a building never mined |
| **L4** | DEPLOYED LIVE | the capability runs in the modeller (rendered, §-logged) on real building data | live + rendered |

LANDED (routed endpoints ARE real elements → 1e-6) may be trusted at L1. GENERATED (fills an ABSENT discipline) can
only ever confirm COUNT — it NEVER reports rmse/cover as fidelity (doctrine §4). So a GENERATED cell tops out at "L1
count-exact + L4 rendered"; it cannot reach L2/L3 *as fidelity* because there is no ground truth to score against.

## The matrix (rows = discipline × capability)

| Discipline | Capability | Maturity | Evidence class | Witness | Number (self → held-out) |
|-----------|-----------|:--------:|----------------|---------|--------------------------|
| **PLB** | ROUTE (networked nn) | **L4** | LANDED | W-WALKBACK-MEP 8/8, W-GENERALIZE-XBUILD 7/7, §DW-TUBE live | self DX 0.969 / TE 0.896 → **held-out LTU 0.839** (centre); surface 0.995 (FS6) @0.15m |
| **ACMV** | ROUTE (ducts) | **L2** | LANDED | W-WALKBACK-MEP, **W-FACE-SURFACE 6/6** | self TE 0.269 centre → **0.996 surface** (the artifact fix); NO held-out duct target |
| **ACMV/VENT** | PLACE (grilles, host-bound) | **L2** | GENERATED+host | W-HOSTBIND-AGNOSTIC 6/6 | SC 13 grilles → window-top, self-consistency 7/13 bound (|Δz|=0.000m); held-out = none |
| **ELEC** | PLACE (outlets/lights, host-bind) | **L4** | GENERATED+host | W-ELEC-HOSTBIND 5/5, W-SHIM-SELECT 6/6, W-DWWALK-HOSTBIND 6/6, #576 live | self SH float 26/38→2, wall/ceiling split mined; **held-out = none (blocked)** |
| **FP** | PLACE (sprinklers, BORROW) | **L4** | GENERATED+borrow | W-BORROW-FP 6/6, #578 live | SC 151 sprinklers host-bound to real ceilings; count bounded; self-consistency only |
| **STR** | WALK-BACK (girders) | **L3** | LANDED | W-WALKBACK-STR 5/5, W-CONFIDENCE-CALIBRATED, W-GUARD-ROTATED 5/5 | precision=don't-fabricate gate; calibrated conf ECE 0.034; rotate-degrades guard holds |
| **any** | ASSEMBLE (parts at nodes) | **L2** | LANDED | W-ASSEMBLE 10/10, W-ASSEMBLE-CONNECT 6/6, W-RULE-CONNECTOR 4/4 | Duplex-MEP: 661 parts, posDrift 0m, Ø re-measured; self/landed (no held-out assemble) |
| **PLB curve** | ROUTE generalization | **L3** | HELD-OUT | W-GENERALIZE-CURVE 7/7 | LTU 0.839 > WBDG_Office 0.749 > Clinic 0.705 > HHS_Office 0.620 @0.15m (graceful decay) |
| **ELEC** | SEED-TRUNK (entry→trunk) | **L1** | GENERATED+seed | **W-SEED-TRUNK 6/6** | human assigns a real entry IfcDoor → trunk (MST) rooted there over the host-bound fixtures; seed drives entry+branching (T5); count preserved |
| **SAN / HEAT** | ROUTE | **L0** | — | — | LTU carries SAN 12k / HEAT 22k — never walked (no mined rule rows yet) |

## The two structural gaps the matrix exposes (the honest L0/L2 ceilings)
1. **No held-out for GENERATED placement.** ELEC/FP/grille PLACE are L4-deployed but stuck at L2 *as fidelity*: every
   "does the rule reproduce reality" test is self-consistency (SH walls, SC's 13 grilles, mined-then-applied-to-same).
   To reach L3 you need a building that HAS the device on a host AND was never mined. SH/DX/SC don't (SH ARC-only, DX
   mined-from, SC rainwater). This is the substrate block, not an engine gap.
2. **Rich vs empty trade-off (your question).** LTU is RICH (PLB 31k/HEAT 22k/HVAC 20k/VENT 20k/SAN 12k) → great
   held-out ROUTING oracle, nothing to FILL. SH/SC are EMPTY → the GENERATE job shines but there is no answer-key. You
   cannot get a held-out *placement* number from an empty building and you cannot get a *fill* demo from a full one.
   The matrix says: PLB/STR routing reached L3 because LTU gave a rich held-out network; placement is stuck at L2
   because no building gives both emptiness-to-fill AND a hidden answer-key for the same discipline.

## What climbs a cell next (the actionable reads)
- **ACMV ROUTE L2→L3:** find/extract a held-out building with a real DUCT network (LTU's is generic IfcFlow* = PLB-rule,
  not ducts). Until then ACMV is honest-L2 with the surface-fair number.
- **SAN/HEAT L0→L1:** LTU carries them richly → mine SAN/HEAT routing rows + walk-back (LTU is the ready oracle).
- **Placement L2→L3:** needs a building with named devices on hosts, never mined — the substrate block (a NEW building,
  not more engine).
- **SEED-TRUNK L1→L2→L3 (the human-in-the-loop seed, user idea 2026-06-30):** `W-SEED-TRUNK` proved the mechanism — an
  engineer assigns a REAL entry element (SampleHouse front `IfcDoor`), the code roots a trunk there over the host-bound
  fixtures, and a different seed reshapes the whole trunk (verified, not assumed). The trunk is a straight-line MST today
  (GENERATED/plausible). NEXT: (1) corridor-aware route (through circulation, not through walls) — turns plausible into
  engineer-grade; (2) wire the seed as an engine entry-point + modeller click (production UX); (3) a held-out check.
  This is the one placement lever the matrix says we CAN buy with engine, because the seed makes the START non-invent.
