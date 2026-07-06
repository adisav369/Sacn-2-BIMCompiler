# ▶ ABSORBED 2026-06-20 → ACT FROM `prompts/TM_4D5D_VARIANCE_LANE.md` (the single lane). This file = deep detail/provenance only.
# ⚠ DO NOT REMOVE — SPEC: "GW Hospital showcase — prepared sample data for cross-tab correlation + Time-Machine variance"
# Scope: a PREPARED GardenWorld (GW) sample dataset built from the REAL Hospital building extraction, that
#   showcases (1) the loosely-coupled cross-tab correlation [zoom-link + warm broadcast] and (2) the Time
#   Machine budget-vs-actual schedule VARIANCE function (BIM_ERP_ROUNDTRIP_RETHINK §TM-VARIANCE). Spec FIRST;
#   no implementation until each § below has a witness claim. PRIME RULE: EXTRACT/COMPILE ONLY — the ONLY
#   generated layer is "actual progress", and it is a DOCUMENTED DETERMINISTIC generator (no random, no
#   hand-typed dates, fixed seed + fixed "today" anchor). Read the log after every run.
# Parent design: prompts/BIM_ERP_ROUNDTRIP_RETHINK.md (§FINAL-DESIGN, §SCENARIO-MAP, §LIFETIME, §TM-VARIANCE).

## §GOAL
Ship a deterministic, re-buildable GW sample set containing ONE Hospital BIM Project Order so the new framework
demos without any live OPFS push (which is the source of the round-trip cache bug — sidestepped entirely: the
data is PREPARED, not pushed). The same set drives BOTH demos:
  · cross-tab correlation — click a Hospital project line in iDempiere → the Viewer Hospital model zooms/lights
    that IFC class (and back), by GUID / product-Value==ifc_class.
  · Time-Machine variance — a budget gantt/resource block vs an actual one, scene animates the ACTUAL, variance
    in a bottom frame (or all-3 / mash — §TM-VARIANCE layout TBD).

## §DATA SOURCES (all EXTRACTED — traceable, no invention; verified to exist 2026-06-19)
| What | Source (real, on disk) | Notes |
|---|---|---|
| Element identity (GUID, ifc_class, storey, discipline) | `viewer/buildings/Hospital_meta.db` → `elements_meta` (top classes: IfcPipeSegment 14452, IfcPipeFitting 13621, IfcMember 7127, …) | the cross-tab correlation key |
| Quantities + cost | `Hospital_meta.db` → `qto_cache` (218 rows: qty, uom, material/labour/equipment_cost, rate_template=`cidb2024_my`) | the BUDGET money + qty, already priced |
| Phase order + planned dates | `viewer/rates/sequence_rules.json` (the 4D phase sequence used by proj_fold) | SeqNo + StartDate/EndDate baseline |
| Rate pack (if re-pricing) | `viewer/rates/*.json` (cidb2024_my is the one qto_cache used) | use the SAME pack qto_cache was built with |
| The fold engine (budget tree) | `viewer/proj_fold.js foldProjectOrder` + `viewer/analysis_sidecar.js apply5DRates` | the REAL pipeline; witness `tests/poc_proj_push.js` |

## §BUDGET / BASELINE (extracted — run the real fold)
Drive `foldProjectOrder(db, 'Hospital', priced, opts)` with `priced` = the apply5DRates rows derived from
`qto_cache` (group by disc,cls,storey → count/qty/rate/cost). Output (proj_fold shape, PK band 990000):
  C_Project(Value='Hospital', PlannedAmt/Qty) → C_ProjectPhase(SeqNo,StartDate,EndDate,IsComplete='N',PlannedAmt,
  Qty) → C_ProjectTask(per resource) → C_ProjectLine(per ifc_class) + M_Product(Value=ifc_class)+M_Product_Category(BIM-<disc>).
This is 100% extracted: identity+qty+cost from qto_cache, phase dates from sequence_rules. NO invention. The
M_Product.Value==ifc_class rows are exactly what the cross-tab correlation matches on.

## §CORRECTION (user 2026-06-19 — "isn't the timeline already there? take a variant ON it, don't overshoot")
DO NOT fold a separate schedule or bake a `tasks`/`schedules` table into the building db — that was overshoot.
Time Machine ALREADY generates Hospital's construction timeline itself (`time_machine.js` injectGantt →
`ELEMENT_PLACE` kernel_ops, durations weighted by LABOR_RATES, cost donut = rate_per_day×duration, cached
`gantt:{building}` JSON in IDB) and animates it. That generated timeline IS the PLANNED baseline. The
12-year span seen earlier was MY parallel fold, NOT TM's own timeline — irrelevant now.
THE LEAN APPROACH: the ACTUAL is a deterministic VARIANT computed live ON TM's existing planned ops (slip late
+ scale cost; Superstructure = marquee blow-out). Variance = planned vs variant. Scene animates the variant.
NO shipped schedule data, NO building-db schema, NO fold — a pure time_machine.js feature that MATCHES the
timeline already there. The GW-seed Project Order (#415) stays as the ERP record for the cross-tab correlation
demo — a SEPARATE concern; it does not drive the TM timeline. §BUDGET/§WHERE-IT-LANDS (schedule parts) and
§E1/E2 below are SUPERSEDED by this for the timeline; keep them only as the (now-unused) fold reference.

## §ACTUAL LAYER (THE invention boundary — a DOCUMENTED DETERMINISTIC variant ON TM's existing timeline)
Actual progress exists in NO source (schedules/tasks/task_elements in Hospital_meta.db are EMPTY — schema only).
So it is GENERATED, but under strict rules so it is reproducible and honest-as-a-mock:
  · NATURAL HOME = populate the EMPTY `Hospital_meta.db` tables they were built for: `schedules` (one 'Planned',
    one 'Actual'), `tasks` (task_id, schedule_id, name, start_date, finish_date, duration_days, status),
    `task_elements` (task_id ↔ guid, from elements_meta by phase/discipline). Planned tasks mirror the proj_fold
    phases/tasks; actual tasks are the generated layer.
  · GENERATOR RULE (must be written + fixed before coding — NO Date.now/Math.random):
      - fixed ANCHOR_DATE (the demo "today") passed in as a constant, e.g. project mid-point.
      - per task, actual_start/finish = planned ± a DETERMINISTIC delta keyed on a stable hash of the task name/
        seqno (e.g. delta_days = (hash % 11) - 5), so re-runs are identical.
      - status derived: finish<anchor→'Complete'; start<anchor≤finish→'InProgress'; else 'Planned'.
      - actual qty/cost = planned × a deterministic factor from the same hash (e.g. 0.9–1.15) → gives a non-trivial
        but reproducible variance. Money via BigDecimal (never raw Number), same basis as proj_fold.
  · DECLARE IT LOUDLY: the generated rows carry a marker (schedule name 'Actual (generated demo)' / a flag) so no
    one mistakes the mock for extracted truth. This is the ONLY non-extracted data in the whole set.

## §VARIANCE (computed, never stored)
variance(task) = actual − planned on (start, finish, duration, cost). Computed at render time by Time Machine
from the two schedules. The bottom frame / mash overlay folds these diffs. No stored variance rows.

## §WHERE IT LANDS (the "prepared GW sample set")
Two prepared artifacts, both shippable, NEITHER an OPFS push:
  1. ERP side — the C_Project tree baked into the GW seed as SAMPLE data (client 11), PK band 990000 (clearly
     BIM-sourced + removable). Built by a documented prep step (run the fold against the GW seed once, commit the
     result) — NOT the boot-time OPFS overlay. This is what retires the cache-bug path for the showcase.
  2. Viewer side — the populated `Hospital_meta.db` (planned+actual schedules + task_elements) shipped in
     `viewer/buildings/`. Time Machine reads it.
OPEN DECISION (confirm w/ user, see §OPEN): bake into `ad_seed.db` directly vs a side sample-overlay shipped as
prepared (not pushed). Recommend: a committed prep script that emits the rows, applied at seed-build time, so the
GW seed simply CONTAINS the Hospital project — deterministic, no runtime overlay.

## §WHAT IT SHOWCASES + WITNESSES (whitebox §-log first; Playwright only for wiring)
  · W-GW-HOSP-FOLD — fold from qto_cache reproduces a stable C_Project tree (row counts, PlannedAmt to-the-cent
    vs qto_cache sum); idempotent (+0 on re-fold).
  · W-GW-HOSP-ACTUAL — the generator is deterministic: two runs byte-identical; every actual row traces to its
    planned row by rule; the 'generated demo' marker present; ≥1 task each of Complete/InProgress/Planned.
  · W-GW-HOSP-CORRELATE — a Hospital C_ProjectLine's product Value resolves to an ifc_class present in
    elements_meta (the correlation key holds end-to-end), and the reverse (a model GUID → owning line) resolves.
  · W-GW-HOSP-VARIANCE — planned vs actual diff is non-zero and stable; the three layout folds (side-by-side /
    all-3 / mash) each render from the two schedules with 0 pageerror.

## §OPEN DECISIONS (real choices — bring to user, ONE at a time, no menu dump)
  1. ACTUAL generator parameters: the ANCHOR_DATE (demo "today") + the delta/factor ranges — pick values that
     make a VISUALLY legible variance (some ahead, some behind, some over/under cost). Needs a quick user eyeball.
  2. Bake target: GW seed client 11 @ band 990000 (recommended) vs a separate sample tenant.
  3. TM-VARIANCE layout to build first (a) side-by-side+bottom variance [recommended, simplest] (b) all-3 (c) mash.
  4. RESOLVED (verified 2026-06-19): Time Machine DOES read these tables — `time_machine.js:2288` T3 "captured
     native 4D schedule" probe: `SELECT task_id, name, schedule_start, schedule_finish FROM tasks WHERE
     schedule_start IS NOT NULL ...` + `SELECT task_id, guid FROM task_elements`. TWO wrinkles to handle in the
     build (not blockers): (a) COLUMN MISMATCH — TM reads `schedule_start/schedule_finish`; the meta `tasks`
     schema has `start_date/finish_date`. The prep must write the columns TM actually reads (or the variance
     build adds the reader). (b) TM reads ONE schedule only (single captured timeline) — the planned-vs-actual
     comparison is NET-NEW reader work in the TM-variance build; this data prep just lands BOTH schedules for it
     to consume. So §ACTUAL "natural home" stands, but the exact column/shape = what `time_machine.js` reads,
     reconciled in E2/E3 — NOT assumed.

## §PLAN (R→E→V — spec is R; do NOT code until claims above are agreed)
  R (done here) — sources verified, fold shape known, invention boundary isolated to the actual generator.
  E1 — prep script: qto_cache → priced → foldProjectOrder → GW seed sample rows (W-GW-HOSP-FOLD).
  E2 — actual generator: populate Hospital_meta.db schedules/tasks/task_elements, planned + generated-actual
       (W-GW-HOSP-ACTUAL). Verify TM reader (§OPEN 4).
  E3 — (separate card / STRIP-2) the cross-tab correlation + the TM-variance icon CONSUME this set.
  V — the four witnesses above, logs read, 0 pageerror; then the showcase is demoable deterministically.

# ─────────────────────────────────────────────────────────────────────────────
# ADDENDUM 2026-06-20 — PLANNED/COMMITTED DOCTRINE + the EXACT discovery flow (user)
# ─────────────────────────────────────────────────────────────────────────────

## §PC-DOCTRINE — variance = the Compiere/iDempiere Planned↔Committed pair (the canonical model)
The variance is NOT a bolted-on concept — it is iDempiere's native project cost-control pair, inherited from
Compiere: **`PlannedAmt` (budget) vs `CommittedAmt` (actual spend)** carried on `C_Project` / `C_ProjectPhase` /
`C_ProjectTask` / `C_ProjectLine`. This is the single source of truth for COST variance. Verified IN THE SEED
(GW client 11, C_Project 990000 'Hospital'):
  · Project: PlannedAmt **64.7M** → CommittedAmt **87.4M**  (+35% / +22.6M over)
  · Superstructure phase: 36.9M → **59.0M** (+60% — the marquee blow-out); two phases UNDER (MEP Rough-in −3%,
    Finishes −11%) → a realistic mixed picture, not a uniform overrun.
TWO GRAINS, ONE TRUTH: LINE grain (C_ProjectLine planned/committed per IFC class = the BoQ line) and PHASE grain
(C_ProjectPhase = the 4D bucket). The line numbers roll up to the phase numbers — same money, two zoom levels
(mirrors ERP recordInfo↔fieldLineage grain split).
COST = real-stored, fully extracted. DATE-actual = the ONE projected piece: iDempiere has **no actual-completion
date column** (only PlannedAmt/CommittedAmt + planned StartDate/EndDate + IsComplete). So the 4D *slip* stays a
labelled viewer-side projection until real completion-ops exist (then it folds from op-log commit timestamps).

## §PC-COLLAPSE — implemented reality + the one honest move owed
SHIPPED (feat/tm-variance, WIP 6718146 + bake_gw_hospital_variance.js): (a) the bake PERSISTED CommittedAmt into
the records via `committed = round(PlannedAmt × factor)` (factor: Superstructure 1.60, else 1.04–1.40 by name-hash);
(b) `viewer/time_machine.js` has the ⚖ variance drawer but RE-COMPUTES both sides at runtime (planned from
`LABOR_RATES × days`, actual from the SAME `_phaseVariant` rule) — so it merely *correlates* with the records.
THE MOVE OWED (the "re-point at qto_cache" fix, now precise): **DELETE TM's recompute; READ the twin's stored
`PlannedAmt`/`CommittedAmt`** (the path `viewer/proj_claim.js` already uses; the folded ERP db is in OPFS via
`bim_orders_overlay.js`). Then the drawer and the ERP record are the SAME figure, not two copies of one rule.
DOWNSTREAM (earn the actual): make CommittedAmt a fold of REAL commitments (POs/invoices via the F-lane) instead
of `×factor` — then EVM (PV/EV/AC → SPI/CPI) falls out and nothing is mocked. Banked; not this card.

## §PC-FLOW — the EXACT depiction (user decree 2026-06-20; supersedes §OPEN DECISIONS #3 and the §16 "all-3/mash" TBD)
A USER-DRIVEN DISCOVERY loop — follow a cost number to the moment in time it happened. NOT an auto-detect lamp
(earlier idea retracted by user). Four steps, each reusing a shipped seam:
  1. **ERP → red pill "BIM Zoom".** On a Project Order line (or header), the ⋯ pill "Zoom Across" carries the
     scope to the Viewer (Zoom-Across v2, LIVE: `&find=<ifc_class>`). Identity-only travels — the cost is NOT
     stuffed in the URL.
  2. **Viewer highlights + IFC INFO PANEL with cost.** Find runs on the class → yellow-silhouette highlight
     (shipped applyFindScope). NEW: a compact **IFC info panel** anchored to the selection shows the element's
     IFC facts (class, count, storey spread) AND **Planned RMxx → Committed RMyy (+z%)** — FOLDED from the twin
     for that building+class (C_ProjectLine grain; whole-building for the header scope). Non-invent: read the
     stored pair, never recompute.
  3. **TM "play to that juncture".** User presses Time Machine. Instead of starting at t0, TM **jumps the cursor
     to the scope's PHASE window** — the IFC class → its C_ProjectLine → `c_projectphase_id` → that phase's
     StartDate (proj_fold already links line→phase, phases carry dates). The user sees the whole building exactly
     as it stood at that construction moment (free, from TM's existing timeline), then scrubs ◀▶ at will.
  4. **Variance DRAWER beside the 5D dashboard.** A new ⚖ drawer sits next to TM's existing 5D `drawDashboard`
     (NOT side-by-side gantts / not a mash). It shows the Planned/Committed phase table (from the twin). THE
     COUPLING: as the cursor scrubs, the drawer **highlights the phase currently under the cursor** + shows its
     Δcost; tapping a phase row jumps the cursor to it (reciprocal of step 3). Scrubbing the 4D timeline = walking
     the 5D cost story; the marquee (Superstructure) lights red as you enter its long window.

## §PC-REFINEMENTS (my proposals on the depiction — confirm/trim before build)
  R1. SCOPE TOKEN carries phase too (`&find=IfcColumn&phase=Superstructure`) so TM can jump even when a class
      spans phases; fallback = derive phase from the line. Identity-only still (no money in the URL).
  R2. The IFC info panel cost is the LINE grain; the drawer is the PHASE grain — one consistent number at two
      zoom levels. A "see in ledger ↗" on the panel deep-links BACK to the ERP line (round-trip).
  R3. SCRUB-TIME 3D DEFICIT (optional, off by default): at the cursor, elements that planned-to-be-placed but
      lag (committed-late) glow red-deficit, ahead glow green — "where we are vs where we should be" per frame.
      Keep it a toggle; it's the 4D face of the 5D Δ.
  R4. HONEST LABELS: cost Δ tagged "from records"; any date Δ tagged "projected (no ERP actual-date)". The drawer
      never blurs extracted vs projected.
  R5. ENTRY is symmetric: the same drawer opens from the ERP side too (the project's BIM Zoom can deep-link
      straight to TM-at-juncture), so the loop closes either direction.

## §OPEN DECISIONS — UPDATE
  · #3 (TM-VARIANCE layout) → **RESOLVED 2026-06-20**: a single ⚖ variance DRAWER beside the 5D dashboard, coupled
    to the scrub cursor (§PC-FLOW step 4). The "all-3 / mash" options are dropped.

## §PC-SYNC-NOTE (verified 2026-06-20 — what the scrub already drives)
`renderAtTime()` (the scrub handler) ALREADY redraws per tick: the 3D scene ✅, the gantt ✅ (with an orange
hairline `tm-gantt-hair`), the 5D dashboard ✅. It does NOT call `drawVariance()` — the ⚖ drawer is drawn only on
open/toggle (≈L2039) + Watch-Actual (≈L2689), no hairline, no current-phase highlight. So §PC-FLOW step 4's
"coupling" is a GAP-CLOSE (make variance a scrub citizen like the others), not net-new TM behaviour: add
`if (_varVisible) drawVariance()` to renderAtTime + a cursor hairline + highlight-phase-under-cursor + tap-to-jump.

## §NEXT-STAGE (banked 2026-06-20 — NOT this build; Primavera-class depth, real-life need)
1. **WHAT-IF via Blue Future (blue dot) on SCHEDULE/COST.** Editing a phase/item (slip a date, change a committed
   amount) lands as a BLUE branch op → the whole downstream RE-FOLDS in blue (dependent phases shift, finish +
   totals re-roll) beside the official baseline; accept = re-baseline, discard = drop the scenario; forward
   variance = planned vs blue. Reuses Blue Future (branch_id / foldBackGroup / acceptBranchUpTo) — no new branch
   machinery. PREREQUISITE (the ONE piece of CPM-like logic owed): a minimal finish-to-start DEPENDENCY model
   (phase N+1 starts at phase N finish; later lag/lead/float) so an edit RIPPLES — today phases are independent
   date ranges and a slip wouldn't propagate. Scope it to F-S only; do NOT build a full P6 network.
2. **EARN THE ACTUAL via Project Issues + Mfg costing.** CommittedAmt becomes a FOLD of real `C_ProjectIssue`
   rows (material issued at cost + labor/resource logged) + iDempiere cost elements `M_CostElement`
   (Material/Labor/Burden/Overhead/Outside-processing) + batch/activity via `PP_Order` cost collectors. Standard
   Compiere/iDempiere PP + project accounting; REPLACES the ×factor bake (§PC-COLLAPSE). Issues/overhead post to
   the project ledger (F-lane). Then EVM (PV/EV/AC → SPI/CPI) is honest, not derived from a seed.
   ▶ FULLY SPEC'D — see **`prompts/TM_SHOPFLOOR_COSTING_SPEC.md`** (TM as shopfloor simulator: cost-element +
   setup/batch on resources; the e-Evolution Mfg substrate M_CostElement/S_Resource/PP_Order is REAL in the seed;
   PP_Order even carries planned↔actual DATES that close the §PC-DOCTRINE date gap; banks the WH-routing-scene future).
3. **POSITIONING (honest, no overclaim).** This unifies what is normally FOUR tools — P6 (schedule/EVM) + Unifier
   (cost) + Synchro (4D) + CostX (5D) — onto ONE signed op-log (no drift between BIM/schedule/cost, free what-if).
   It is NOT a CPM engine (no resource leveling / calendars / critical-path depth); the value is UNIFICATION +
   the log-native what-if, not out-scheduling P6. Claim the integration, never the scheduling horsepower.

## §PC-WITNESSES (the downstream build proves these; §-log first, Playwright for wiring only)
  · W-PC-PANEL — viewer IFC info panel folds the twin's Planned→Committed for the zoomed class == the stored
    C_ProjectLine pair to the cent (Superstructure marquee +60%); whole-building scope == C_Project rollup.
  · W-PC-JUNCTURE — TM opened with a scope jumps the cursor into that class's phase window (cursor date within
    [StartDate,EndDate]); the scene renders partially-built (frontier > 0, future invisible) at that instant.
  · W-PC-DRAWER — the ⚖ drawer reads PlannedAmt/CommittedAmt from the twin (NOT recomputed); the phase row under
    the cursor is highlighted as the cursor scrubs; tapping a row repositions the cursor (reciprocal).
  · W-PC-TWIN-SOURCE (the §PC-COLLAPSE proof) — §FALSIFIER: with TM's labor-rate recompute removed, the drawer
    total equals `SELECT CommittedAmt FROM C_Project WHERE Value='Hospital'` exactly (one number, not correlated).
  · W-PC-HONEST — cost Δ labelled "from records"; date Δ labelled "projected"; no extracted/projected blur.
