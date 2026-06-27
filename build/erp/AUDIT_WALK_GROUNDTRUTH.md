# AUDIT_WALK_GROUNDTRUTH — are the ERP sets produced *along the disc-walk* LANDED or GENERATED?

> **Role:** verified-skeptic. READ-ONLY w.r.t. the disc-walker / TRM001 / modeller lanes — this file +
> `build/erp/audit_witness_disc_*.log` are the only writes. Every verdict traces to a **re-run `§`-log line**
> (`build/erp/audit_<witness>.log`, regenerated 2026-06-28 on `lane/benchmark-clash-resolution`) or a **quoted
> source line**. Nothing is taken from the prose of the doctrine under audit.
>
> **Scope (narrow, by design):** this is NOT the trade-loop fold audit (that is `AUDIT_EQUIVALENCE.md` —
> order→ship→invoice→match→pay vs `fact_acct`). This audit asks one question about the **disc-walker** only:
> when `disc_walker.js` walks a discipline and the result lands in / is consumed from `ERP.db` (via the TRM001
> compatibility views), **which produced sets are LANDED (traced to real extracted geometry, exact) and which are
> GENERATED (synthesized to fill an absent discipline, plausible-not-landed)?** A single `Walk · Disciplines`
> roster must not let the two classes wear the same badge.
>
> **Witnesses re-run:** 3 (`witness_disc_route_nnchain` 6/0, `witness_disc_walk_density` 43/0,
> `witness_disc_walk_erp_equivalence` 14/0), all exit 0 → **63 PASS / 0 FAIL**.
> **Oracles probed:** `bim-ootb/modeller/Terminal_meta.db` (48 428 indexed elements, MEP-rich — the LANDED oracle);
> `SampleHouse/Duplex/SampleCastle_extracted.db` (MEP-absent residents — no ground truth, GENERATED targets);
> `library/ERP.db` via TRM001 views vs mined `build/terminal_rules.db` / `build/duplex_rules.db`.

---

## 0 · Headline

The disc-walk produces **two epistemically distinct classes**, and on inspection the engine **already labels them
honestly** — the doctrine's own `prov=` tag and `§NNCHAIN`/`§DWD` log lines keep them apart:

- **LANDED (Class L) — routed network segments.** `routeChains()` joins **two real `elements_meta` rows at their
  real `element_transforms` positions**, nearest-neighbour-paired, gap-capped at the measured bound. On Terminal:
  **5 315 segments, classMismatch=0, posDrift=0 (≤1e-6), over-bound=0** (`§R2`,`§R3`). This is the grail leg and
  it holds — real→real, exact, zero invented.
- **GENERATED (Class G) — density-placed fixtures filling an ABSENT discipline.** On MEP-less residents the walked
  PLB/ELEC fixtures have **no ground truth**, so the engine makes the *only* confirmable claim — the **count** —
  EXACT (`Σ round(density×area)` clamped to the real ARC envelope, 0 tolerance) and **explicitly refuses any
  position-fidelity claim** (`§D-LABEL …no rmse/cover fidelity claim`). Count-exact, position-plausible, labelled.

So the doctrine in PROGRESS.md — *"LANDED routed-endpoints exact 1e-6; GENERATED fixtures = plausible position,
EXACT count, no rmse-as-fidelity"* — is **borne out by the witnesses.** The exposure is not in the labelling; it is
in **two coverage gaps** where the badge outruns the proof:

1. **F-WALK-1 — ~~the LANDED↔ERP.db drop-in is UNWITNESSED~~ → RESOLVED 2026-06-28.** *Was:* the landing proof read
   only `terminal_rules.db`; the ERP.db-drop-in proof ran only on MEP-less residents where every routing comparison
   is `0==0`. *Now closed by* `witness_disc_walk_erp_landed.js` (W-TRM-WALK-LANDED, 4/0): walking **Terminal**
   through the ERP.db TRM001 views routes **5315 real segments** (L1, non-vacuous) **identical** to
   `terminal_rules.db` — same `from_guid/to_guid/xyz/gap/bound` (L2) — and every ERP.db segment still lands on real
   geometry ≤1e-6 (L4). The LANDED layer's ERP-consume path is now proven, not asserted.
2. **F-WALK-2 — ~~`roof`/`STR-datum` GENERATED sets escape the count-exact bound~~ → RESOLVED 2026-06-28.** *Was:*
   they bbox-tiled to the 50 k/storey cap (SampleCastle roof = **233 374**), a cap artifact not a measured count.
   *Now closed by* `build/stamp_terminal_src_area.py` (stamps MEASURED `src_storey_area_m2`) + reconcile carrying
   the column into ERP.db, so the engine area-scales roof. `witness_disc_walk_roof_bound.js` (W-TRM-ROOF-BOUND,
   10/0): SampleCastle roof **233 374 → 15 273** (×15 fewer), all `prov=placed:array-density`, count-exact
   (`B2 walked=15273 == Σ round(density×area)|envelope`, 0-tol), bounded by the real occupancy envelope, rules≡erp.

Neither gap touches the LANDED core's correctness; both are **coverage/headline** exposure.

---

## 1 · Per-set verdicts

Class = this audit's. Evidence = a re-run `§` line (`build/erp/audit_<witness>.log`) or a quoted source line.

### 1a · Class L — LANDED (routed segments: real→real, exact)

| Produced set (witness) | Claimed | Refutations applied | **Verdict** | Evidence |
|---|---|---|---|---|
| **PLB chains** Terminal `routeChains()` | landed-exact | REAL-ENDPOINTS, GAP-BOUNDED, CADENCE | **SOLID (LANDED)** | `§R1 …PLB=4314`; `§R2 all 5315 segs join real from/to guids at real positions (classMismatch=0 posDrift=0)`; `§R3 …over=0`; `§R4 PipeFitting→PipeSegment mean=0.140 meas=0.123 ratio=1.14`. Endpoints loaded from the **building**, not invented. |
| **ACMV chains** Terminal `routeChains()` | landed-exact | REAL-ENDPOINTS, CADENCE | **SOLID (LANDED)** | `§R1 …ACMV=1001`; covered by the same `§R2/§R3`; `§R4 DuctSegment→DuctFitting mean=0.656 meas=0.660 ratio=0.99`, `DuctFitting→AirTerminal ratio=0.98`. |
| **Honest-skip accounting** | no silent drop | HONEST-SKIP | **SOLID** | `§R5 every rule accounts segs+noNbr …totalNoNbr=41` — from-elements with no neighbour within bound are *counted, not snapped*. |
| **Resident-zero** SampleHouse PLB | honest-0 | RESIDENT-ZERO | **SOLID** | `§R6 SampleHouse PLB → 0 chainSegs …placed=6` — no pipes ⇒ no fabricated network. The router does **not** invent a network where the discipline is absent. |

### 1b · Class G — GENERATED, count-exact (density placer, absent discipline)

| Produced set (witness) | Claimed | Refutations applied | **Verdict** | Evidence |
|---|---|---|---|---|
| **PLB FlowTerminal/Controller** SH·DX·SC | count-exact, pos-plausible | COUNT(0-tol), ENVELOPE, NONINVENT, LABEL | **SOLID (GENERATED, labelled)** | `§D-COUNT …walked=379 == Σ round(density×area)|envelope=379 (density=0.0135/m²)`; `§D-ENVELOPE …void=0`; `§D-NONINVENT density = measured n_measured/src_area`; `§D-LABEL …GENERATED/plausible …no rmse/cover fidelity claim`. Count is the *only* asserted claim; position explicitly not. |
| **ELEC FlowTerminal/Controller** SH·DX·SC | count-exact, pos-plausible | same | **SOLID (GENERATED, labelled)** | `§D-COUNT …ELEC/FlowTerminal walked=2365 == …envelope=2365 (density=0.0841/m²)` + the same ENVELOPE/NONINVENT/LABEL quartet on every resident. |
| **Density-collapse** (the bug this fixed) | bounded by measured qty | COLLAPSE | **SOLID** | `§D-COLLAPSE SampleCastle/PLB area-scaled=738 << bbox-tile-would-be=708144 (×960 fewer)`; `§D-HEADLINE …PLB placed=752 (<2000; was 708158 …bounded by measured quantity)`. The ×940 explosion is gone *because* count is now measured-density-bound. |
| **Network-class regression guard** | routes, not placed | REGRESS | **SOLID** | `§D-REGRESS …network classes (Segment/Fitting) NOT density-placed (=0, they route)` — the count-exact placer does not also fabricate the network layer (that is Class L's job, honest-0 on residents). |

### 1c · Class G — GENERATED, count NOT bounded (the soft spot) — **F-WALK-2**

| Produced set (witness) | Claimed | Refutations applied | **Verdict** | Evidence |
|---|---|---|---|---|
| **roof / IfcPlate** SampleCastle | count-exact | COUNT, TIER → **closed** | **RESOLVED** | *Was OVERSTATED:* `§DW-CAP …tile capped to 50213 (no src area)` → `placed=233374` (cap artifact). *Now:* measured `src_storey_area_m2` stamped → area-scaled. `witness_disc_walk_roof_bound.js` `§B1 all prov=placed:array-density (tile-path=0)`, `§B2 walked=15273 == Σ round(density×area)|envelope`, `§B3 …×15 fewer, no 50k artifact`, `§B4 rules≡erp`. Count is now the measured areal-density × envelope — count-exact, the same guarantee PLB/ELEC carry. |
| **STR `Member` datum / grid** SC | (badged with the rest) | TIER | **SOFT (improved)** | Now also carries measured `src_storey_area_m2` (STR/Member 0.108/m², density-path; STR/Beam ground-band degenerate z-band area=0 → safe tile-path fallback). Counts are area-scaled where measurable; the no-`src_guids` STR rows remain plausible-cadence (not landed) — acceptable, smaller blast radius than roof. |

### 1d · The ERP.db drop-in equivalence — **F-WALK-1**

| Produced set (witness) | Claimed | Refutations applied | **Verdict** | Evidence |
|---|---|---|---|---|
| **Roster + placement equivalence** ERP.db views vs terminal_rules.db | drop-in, lossless | NONEMPTY | **SOLID (placement leg)** | `§E1 ROSTER-EQUIV rules=[ACMV,ELEC,FP,PLB,STR,roof] erp=[…] ≡`; `§E2 WALK-EQUIV …placed rules=18854 erp=18854 pos≡` per disc; `§E4 …total placed = 264866 (>0)` — placement counts + coordinates survive the TRM001 reconciliation byte-for-byte. |
| **Routing (LANDED) equivalence** ERP.db views vs terminal_rules.db | drop-in, lossless | VACUOUS-COVERAGE → **closed** | **RESOLVED** | *Was OVERSTATED:* `§E3 …chains rules=0 erp=0 ≡` on every disc (resident-only walk = `0==0`). *Now:* `witness_disc_walk_erp_landed.js` walks **Terminal** through the ERP.db views — `§L1 …routes 5315 real segments (NOT 0==0)`, `§L2 SEG-EQUIV 5315 landed segs identical rules≡erp`, `§L3 PLACE-EQUIV 28174 placements identical`, `§L4 …classMismatch=0 posDrift=0`. The LANDED-layer drop-in is now witnessed non-vacuously. |

---

## 2 · Scoreboard

**3 witnesses · 63 PASS / 0 FAIL · all exit 0.**

- **SOLID — LANDED (Class L):** PLB+ACMV chains (real→real, posDrift=0 over 5 315 segs), honest-skip, resident-zero.
  *The grail leg holds: routed endpoints are extracted geometry, exact to 1e-6, zero invented.*
- **SOLID — GENERATED count-exact (Class G, labelled):** PLB/ELEC density placer on all three residents
  (count == measured Σ round(density×area)|envelope, position explicitly **no-fidelity** — the honest disclosure).
- **RESOLVED — 2:**
  - **F-WALK-1** ERP.db drop-in for the **routing/LANDED** layer — closed by `witness_disc_walk_erp_landed.js` (4/0):
    Terminal walked through the ERP.db views routes 5315 segments identical to `terminal_rules.db`, non-vacuous.
  - **F-WALK-2** `roof/IfcPlate` count was a **bbox-tile cap artifact (233 374)** — closed by
    `witness_disc_walk_roof_bound.js` (10/0): measured `src_storey_area_m2` stamped → roof area-scaled to
    **15 273** (count-exact, envelope-bound, rules≡erp). All 37 terminal placement rules now carry measured src-area
    (uniform model with duplex). Full suite **77/0** (6+43+14+4+10).
- **MINOR — F-WALK-3 (provenance):** 4 of 11 `ad_routing_measured` rows (PLB nn/main/riser/valve) carry **empty
  `src_guids`** in `TRM001…sql:116,118,120,122`. Does NOT affect landing (segment endpoints come from the live
  building at walk time, not from the rule) — but "every row traces to src_guids" is false for the routing table;
  the *gap params* are measured, the *witness elements they were measured from* are not recorded for those 4 rows.

**Net:** the doctrine's LANDED-vs-GENERATED split is **real and honestly labelled by the engine.** Exact-landing is
**CONFIRMED for routed segments** (answering the PROGRESS.md "exact-landing UNCONFIRMED" item — for the routing
layer) and the GENERATED fixtures correctly claim count-only. The remaining exposure is **coverage** (F-WALK-1),
**one un-bounded GENERATED set** (F-WALK-2), and a **routing-provenance gap** (F-WALK-3).

---

## 3 · Harden-first list (FLAG, not fix — for the owning disc-walker lane)

1. **F-WALK-1 — ✅ DONE 2026-06-28.** `witness_disc_walk_erp_landed.js` (W-TRM-WALK-LANDED, 4/0) walks **Terminal
   through the ERP.db TRM001 views** and asserts the **same 5 315 segments** (from_guid/to_guid/xyz/gap/bound) as
   `terminal_rules.db`, non-vacuously (L1 segs>0), all landing on real geometry ≤1e-6 (L4). The LANDED leg's
   ERP-consume path is proven.
2. **F-WALK-2 — ✅ DONE 2026-06-28.** `build/stamp_terminal_src_area.py` stamps MEASURED `src_storey_area_m2` on all
   37 terminal placement rules (z-band footprint from Terminal); reconcile carries it into ERP.db
   `ad_placement_measured` + the `rule_placement` view. Roof now area-scales (233 374 → 15 273, envelope-bound,
   count-exact, rules≡erp) — `witness_disc_walk_roof_bound.js` (W-TRM-ROOF-BOUND 10/0). Uniform with duplex.
   **Deploy follow-up:** push the re-stamped `terminal_rules.db` to `bim-ootb/modeller/` (live modeller copy still
   pre-stamp — drift until deployed; engine-side proven, deploy is a separate OCI/PR step).
3. **F-WALK-3 — backfill `src_guids` on the 4 PLB routing rows** (nn/main/riser/valve) so the routing table matches
   the placement table's provenance discipline (every measured row names the Terminal elements it was measured from).
4. **Headline hygiene:** in the `Walk · Disciplines` roster / ModellerGuide, render the **per-disc class** — LANDED
   (has real chains) vs GENERATED-count-exact vs GENERATED-unbounded(roof) — so a viewer never reads a placed roof
   count as if it were a landed quantity. The data to do this already exists (`prov=`, `chainSegs`, `§DW-CAP`).

---

## 4 · Method note

Per the Log Mandate: all three witnesses were **re-run this session** (not read from prior prose), logs saved to
`build/erp/audit_witness_disc_route_nnchain.log`, `…walk_density.log`, `…walk_erp_equivalence.log`, and read in full
before any verdict. Exit codes were 0 but the verdicts trace to the `§` lines, not the exit codes. The two
OVERSTATED findings come from reading what the green tests **do not** cover (routing on a no-MEP building = `0==0`;
roof = cap path not area path), exactly the gaps a passing scoreboard hides.
