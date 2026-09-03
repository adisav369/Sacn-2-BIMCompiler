# ⚠ DO NOT REMOVE — SESSION CARD: GRAND LANE S1 / J5 PROCESS (the signed Complete)
# Scope: make iDempiere's **Complete REAL** — re-point the form DocAction/Process bar + grid gear-batch
#   OFF the in-memory FSM PREVIEW onto the SHARED signed lane (commitProcess → completeFanout → signed
#   commitGroup → persist + fan-out). The ring stays Glass-only (doctrine §0). R→E→V, ONE bounded leg.
# Read FIRST: prompts/GRAND_LANE_STRATEGY.md §0 doctrine + §3 S1 + prompts/ERP_CRITIC_UX_LANE.md (J5).
# Log Mandate: read the witness §-log before any conclusion — exit code is NOT evidence.
# Worktree ready: /tmp/wt-critic-j5 (branch feat/critic-j5-signed-complete). Edit bim-ootb ONLY via /tmp/wt-*.

## R — VERIFIED 2026-06-16 (design holds against live code; do NOT re-derive)
- **GAP:** `idempiere.html` `buildDocActionBar` click (~:1460) dispatches via `AdDocFsm.dispatchOrder/dispatchFor`
  = in-memory PREVIEW (mutates `fsmRec` only — no persist / sign / fan-out). Complete is **display-only**.
- **SIGNED LANE EXISTS + PROVEN (W-SO-COMPLETE-UI):** `crud_overlay.applyOp` (:1618) routes `DOC_ACTION` →
  `commitProcess` (:1171): `_gateForOwnedWrite` (owner/CAS) → `completeFanout` (ship/invoice consequence set from
  the proven engine) → `CORE.buildDocActionGroup` → `K.commitGroup` (signed) → `verifyChain` + `_sidePersist`
  + `setDocStatus` + `docDot`. §-logs `§SO-COMPLETE … sealed=Y gid=` + `§CRUD process committed … verifyChain=ok`.
- `__crud.applyOp` ALREADY EXPOSED (`crud_overlay` :1828). `overlay:committed` listener exists
  (`idempiere.html` :1839) — currently AD-only.

## E — THE RE-POINT (locked, sourced)
1. `crud_overlay.js`: add host-callable `__crud.process(table, id, action, {from, doctypeId, ownerGated})`
   = a parameterized `doProcess` — builds the `DOC_ACTION` op WITHOUT a ring STORE entry → `applyOp` → `commitProcess`.
2. `idempiere.html` :1460 — swap the preview `AdDocFsm.dispatch*` for `__crud.process(...)`. **KEEP** the FSM for
   deciding WHICH actions are legal (it picks the set; the signed lane EXECUTES the chosen one). The ring NEVER opens.
3. grid gear-batch `_runBatch` — fan `__crud.process` over N selected rows (the dispatch-over-N-rows seam, same verb).
4. extend `overlay:committed` (:1839) to re-open/refold the active window on a `DOC_ACTION` commit so the persisted
   CO + fan-out (M_InOut / C_Invoice per doctype policy, else honest gate logged) show + survive reload.

## V — WITNESS W-CRITIC-PROCESS-LIVE (erp/tests/poc_critic_*_live.js, §-tagged, 0 pageerrors)
- ASSERT: signed op + persisted CO **survives reload** + fan-out drillable + oracle-diff to the cent.
- REGRESSIONS GREEN: recinfo / draft / blue / grid-batch / form-pill / gridstatus.
- SHIP: clean /tmp/wt-* off fresh origin/main · sw CACHE_VERSION clean line + `internal/SW_CHANGELOG.md` entry ·
  auto-merge · VERIFY it lands (squash + late push orphans — start follow-ups off fresh origin/main).

## AFTER J5 → J4 CREATE (S2: New via iDempiere's OWN New, NOT the ring) → J6 POST. Then P2 → P3 → P3.5 → P4.

## STANDING (from the 2026-06-16 doc/architecture session — already DEPLOYED)
- The 4-fold status panel + paper + ERP guide are revised & live (iDempiere-lead, plain-ERP green fold, RED reframed
  as data-blocked/architectural, Blue Future §15, Holy Grail capstone diagram). Grand Lane §0 carries the **engine
  north star + common automation law** (DERIVE/VALIDATE/ACT by op-log effect; foreign imperative code = user plugin,
  never auto-import; op-only invariant; future `behavior` ErpDescriptor facet). Forethought banked — do NOT rebuild.
