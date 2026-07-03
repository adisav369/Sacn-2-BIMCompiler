# MEP / Building-Services Spatial Coordination Ruleset

**Purpose:** the rules that decide how services sharing ceiling/riser/floor space are routed and
deconflicted — "which goes first, which avoids, how." Drives `CoordinationHandler` (the new RouteWalker
sub-handler). The Rosetta Stone proves geometry round-trips; it cannot supply these rules — they are
external building-services engineering knowledge, so **every rule traces to a cited source** (non-invent).

## Provenance legend (re-verified deep-research run wsog87r4b, 2026-06-22 — 25 claims, 10 confirmed / 15 killed)
- ✅ **VERIFIED** — ≥2-0 adversarial vote, cited. Safe for `CoordinationHandler` to hard-act on (ENFORCED).
- 🟡 **PENDING** — claim gathered from a real source but not confirmed / the specific claim abstained. NOT
  disproven, NOT yet confirmed. Advisory only (logged, never moves geometry).
- ❌ **REFUTED** — voted down. Do **not** use (kept as a tombstone so it is not re-added).

> ⚠ RE-VERIFY RESULT (the 🟡 rows from run wf_105ef846 are now resolved):
> - ✅ **DRAIN holds at a clash** — gravity fixed-fall (1°–5°) cannot be re-pitched (RQ2, 3-0). **STRUCT holds**
>   — immovable (physical). These two are the ONLY citable who-holds decisions → ENFORCED.
> - 🟡→❌ the **full largest/rigid priority LADDER** (gravity→duct→pressure→tray) stays unencoded: every
>   specific-sequence claim was REFUTED (1-2 / 0-3, single-blog, non-normative). BG6 is the right *framework*
>   (✅) but does not publish the ordering. So ACMV/FP/DWATER/ELEC relative order = advisory only.
> - ❌ ceiling-void **vertical STACKING** order — sprinkler-highest AND duct-highest BOTH REFUTED 0-3 → no
>   universally-citable order exists → §4 dropped (no `stackZ`).
> - ✅ **AS/NZS 3000 separations PROMOTED**: elec↔water 25mm, elec↔gas 25mm, elec↔telecom 50mm (3-0).
>   ❌ elec/tray↔hot 300mm AS/NZS attribution REFUTED 0-3 → advisory only.

---

## §1 Routing priority / sequence — who is laid first, gets the preferred path
❌ **REFUTED (the FULL ladder) → not encoded** (run wsog87r4b: specific-sequence claims voted down 1-2 / 0-3,
single-blog/non-normative). Only DRAIN-holds (§2) + STRUCT-holds survive as citable. Original claim kept below:
🟡 *canonical MEP overhead routing priority:* gravity-fed (sanitary waste, storm) **first**,
then ductwork (largest), then pressurized pipe, then conduit / cable tray (most flexible) **last**.
Governing principle: **gravity-before-pressure, largest-before-smallest, rigid/fixed-fall-before-flexible.**
Source: projul.com/blog/construction-mep-coordination-guide; cccengineering.com.au services-coordination-ceiling-voids.

✅ **VERIFIED context:** SMACNA (the duct standard) **does NOT prescribe** inter-service priority or stacking —
it "defers undefined arrangements to the contractor." → the priority order is **industry coordination convention
(BSRIA BG 6 / coordination drawings), not a single code clause.** Treat §1 as convention, cite BG6.
Source: law.resource.org SMACNA 1995.

✅ **VERIFIED:** Coordination drawings (to scale, per-trade, showing duct top/bottom elevations + penetrations)
are the required mechanism to deconflict congested voids. Source: Northwestern NU 23 3114 Ductwork.

## §2 Avoidance / yield — at a clash, who reroutes
✅ **VERIFIED (run wsog87r4b, 3-0):** *gravity drainage holds its path; pressurized/flexible services reroute* —
gravity fixed-fall (1°–5°) cannot be re-pitched without breaking self-cleaning/seal. Sources: pinnacleiit MEP
coordination ("gravity-driven systems have no flexibility"); CIBSE/Geberit **BS EN 12056** drainage-design.
✅ **VERIFIED:** ductwork must be **routed to avoid** transformer vaults / electrical equipment rooms (duct yields
to electrical *service spaces*). Source: Northwestern NU 23 3114.
❌ **REFUTED (do not use):** "the duct is always the service that jogs around deep beams" (1-2). — But see §3:
SMACNA *does* allow the duct to depress ≤30% as one legitimate avoidance move; it is not the *mandated* yielder.

## §3 The "how" — clearances, separations, offset mechanics (machine-usable)
| pair / item | min separation | status | source |
|---|---|---|---|
| data/ELV ↔ power (<600 V) | **50 mm** | ✅ VERIFIED | BS 6701 / NEC 800.52 (CommScope TP-106296) |
| sprinkler pipe ↔ structural member (not supporting it) | **50 mm** | ✅ VERIFIED | NFPA 13 §18.4.9 |
| sprinkler pipe penetration hole oversize | **+50 mm** (25–90 mm pipe), **+100 mm** (≥100 mm pipe) | ✅ VERIFIED | NFPA 13 §18.4.2 |
| electrical ↔ water pipe | 25 mm | ✅ VERIFIED | AS/NZS 3000:2018 — LV ≥25mm from above-ground water (sparkycalc, 3-0) |
| electrical ↔ gas pipe | 25 mm | ✅ VERIFIED | AS/NZS 3000:2018 — LV ≥25mm from above-ground gas (sparkycalc, 3-0) |
| electrical ↔ telecom/data | 50 mm | ✅ VERIFIED | AS/NZS 3000:2018 §3.8 (sparkycalc, 3-0); corrob. BS 6701/NEC 800.52 |
| cable tray ↔ hot pipe | 300 mm | ❌ REFUTED | AS/NZS attribution voted down 0-3 (ccceng) — advisory only |

✅ **VERIFIED offset mechanic:** a duct may be **depressed (flattened) up to 30%** of its height **without
increasing width** to pass under a beam/pipe; fitting loss coeff ≈ 0.24–0.35. This is the canonical duct
jog/offset. Source: SMACNA Duct Design.
✅ **VERIFIED space reservation:** duct sizing must reserve clearance beyond bare sheet metal for joints,
insulation/liner, **plus piping, conduit, light fixtures, and ceiling-tile removal.** Source: SMACNA Duct Design.

## §4 Vertical stacking order in the ceiling void
❌ **REFUTED / UNRESOLVABLE → NOT encoded (no `stackZ`).** The conflict resolved by re-verify (run wsog87r4b):
- (a) "duct closest to slab, sprinkler below, tray lowest" → **REFUTED 0-3** (single-firm blog, non-normative).
- (b) "sprinkler highest at 75 mm below soffit, duct below" → **REFUTED 0-3** (same).
Neither is a universally-citable convention — ceiling-void layering is a per-project coordination-drawing output,
not a fixed rule. So `CoordinationHandler` deliberately encodes **no vertical stacking order** (non-invent).
🟡 install sequence: structure (fixed) → sprinklers (head positions) → ductwork (largest) → cable tray. (ccceng)

## §5 Local codes (project recipes cite these)
🟡 Malaysia **UBBL 1984** + **MS** standards and **MS 830** (LPG storage/handling) sources captured but unverified;
US **IPC 2018 §603.2** (water supply) captured. Re-verify for region-specific clearances (esp. LPG/gas).

## §6 ❌ Refuted — do NOT use
- HVAC duct fixed "1 inch (25 mm) + insulation" all-round clearance (1-2).
- Duct is always the service that reroutes/jogs around deep beams (1-2).
- Feeder/large-group power ↔ data 600 mm "halvable" rule (1-2).

## §7 TODO (after session limit resets 4:20pm Asia/KL)
Re-run verification on every 🟡 row above — especially §1 priority order, §2 gravity-holds, §4 stacking conflict,
and the AS/NZS electrical separations. Promote to ✅ (cited) or drop. Until then `CoordinationHandler` acts only
on ✅ rows and treats 🟡 as advisory warnings (logged, not enforced).

## §8 → CoordinationHandler mapping
- §1/§2 → `priorityRank(discipline)` + `yields(a,b)` decision.
- §3 → `minSeparation(a,b)` mm lookup (the clash gate's tolerance, per pair, not one global tol).
- §3 duct-depress → the **resolution move** when a yielding service is a duct (flatten ≤30% before rerouting).
- §4 → `stackZ(discipline)` target band (BLOCKED until conflict resolved).
