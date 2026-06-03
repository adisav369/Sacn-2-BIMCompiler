# ⚠ DO NOT REMOVE — Accts-Posted panel (FRONTEND_LANE_MASTER §2 Item C)
# SCOPE: the READ-ONLY "Accts Posted" panel that renders `window.ERPPostings.readPostings(...)` verbatim.
#   Decision-free · read-only · NO new verb · NO deploy (mount + pill + deploy are GO-gated, see §7).
#   Witness-led: §POSTED-READ / §POSTED-GATE rendered VERBATIM. READ the log before any conclusion.
#   Consume the seam — NEVER fork readPostings (it is FROZEN in bim-compiler/scripts, UMD-copied to erp/).
#   Honour until the # DONE appendix is written with a §-log line proving every claim.

---

## 1. ISSUE + WITNESS (each test NAMES its issue)
The Posted tab in iDempiere is a privileged accounting view. We must surface it from the engine's
role-gated read-fold **without** the UI re-deciding visibility, re-balancing, or inventing history.

| Witness | Issue it proves |
|---------|-----------------|
| **§POSTED-READ** (rendered) | an accounting role's balanced fold renders VERBATIM — every `line`, `balanced`, `source`, `coverage`, `note` shown exactly as `readPostings` returned them; nothing re-computed UI-side. |
| **§POSTED-GATE** (rendered) | a non-accounting role (and an out-of-scope record) renders an honest refusal with **ZERO account rows in the DOM** — the gate's zero-leak holds at the UI layer too, not just the engine. |
| **§POSTED-COVERAGE** (rendered) | the degrade ladder `absent → partial → complete` is shown verbatim (note rendered, never hidden); the Posted tab is **never** gated away on `partial`/`absent`. |
| **§POSTED-CTX** | `buildCtx(session)` maps a real `idmp_session` to `{ role:{id}, allowOrgs }`, including org-0 ⇒ `'*'`. |

## 2. CONTRACT CONSUMED (FROZEN — do NOT rebuild)
`erp_postings.js` → `readPostings(recordRef, ctx, dbs)` returns EXACTLY:
```
{ visible, posted, lines[], balanced, source, coverage, note, reason }
  source   ∈ { fact_acct, oplog, none }      coverage ∈ { complete, partial, absent }
  lines[]  = { account_id, value, name, amtacctdr, amtacctcr }
```
The gate (`ad_role.isshowacct`), the org-scope, the fold, and the fact_acct cent-degrade are ALL engine-side
and already witnessed (`scripts/poc_postings.js` ALL PASS). The panel is a pure RENDERER of this object.

## 3. PANEL BEHAVIOUR (render verbatim; non-gating; honest empty)
`renderPosted(result)` → a DOM node, driven ONLY by the `result` fields:
- **`visible && posted && lines.length`** → a table of `lines` (value · name · DR · CR), a balanced badge
  reflecting `result.balanced` VERBATIM (no UI re-sum to decide it), and a coverage strip showing
  `source` + `coverage` + `note` (note rendered when present). This is §POSTED-READ.
- **`visible===false`** (`reason: role-not-accounting | out-of-scope`) → an honest one-line refusal that
  states the reason; **renders ZERO `tr.posted-line` rows / ZERO account values** (UI zero-leak). §POSTED-GATE.
- **`visible && !posted`** (`coverage: absent`) → the engine's `note` ("install local data first…") shown as
  the empty state; tab still present, never hidden. §POSTED-COVERAGE absent.
- **`partial`** → identical to READ but the coverage strip carries the partial `note`; **never** suppress the
  panel for not being `complete`.

INVARIANT (§4 of the master): the panel **must not** re-gate, re-balance, or re-fold. `balanced`, `source`,
`coverage`, `note`, `reason` are displayed as returned. The only UI logic is formatting cents → display.

## 4. buildCtx CONTRACT
`buildCtx(session)` where `session = idmp_session.buildContext(...)` → `{ role, orgs:[{id,name}], ... }`:
```
{ role: { id: session.role.id },
  allowOrgs: orgs-includes-0 ? '*' : session.orgs.map(o => o.id) }
```
Org 0 ("* All accessible") ⇒ `allowOrgs:'*'` (engine treats `'*'` as unscoped). Read-only: does NOT mutate
`idmp_session` (the master's Item D will augment session; Item C only reads it).

## 5. FILES (NEW ONLY this session — no chrome edit, no deploy)
- `bim-ootb/erp/accts_posted.js` — `buildCtx` + `renderPosted` + `openPosted(recordRef, session, dbs)`;
  UMD tail (`module.exports` for the witness, `window.AcctsPosted` for the live host). Consumes
  `window.ERPPostings` / requires `../erp_postings.js`. NO fork.
- `bim-ootb/erp/tests/poc_accts_posted.js` (+ `.log`) — the render witness (§6).
- **NOT touched:** `idempiere.html` (entangled, wrong base) — mount/pill is §7, GO-gated.

## 6. WITNESS DESIGN (`poc_accts_posted.js` → `tests/poc_accts_posted.log`)
Node + sql.js (mirror `tests/poc_kanban_chrome.js`). A minimal DOM shim (hand-rolled `document` stub like the
tour/host witnesses) so `renderPosted` builds real nodes asserted by text content.
- **adQ** over real `erp/ad_seed.db` (roles 102 `isshowacct=Y` / 103 `=N`; `c_validcombination` / `c_elementvalue`).
- **projQ** over a fresh `erp_kernel` projection. For the POSTED branch, arrange ONE real balanced POST via
  `K.apply` using a REAL invoice (`C_Invoice#101`, GrandTotal 100.7, org 11) and REAL `c_validcombination`
  account ids — the SAME technique as `scripts/poc_postings.js`. (Account-RESOLUTION fidelity is proven
  there via the resolver; THIS witness's issue is the RENDERER, stated honestly in the log — no overclaim.)
- Drive the REAL local `readPostings` for all four branches, render each, assert the DOM:
  1. §POSTED-READ: role 102, posted record → DOM has N `.posted-line` rows == `result.lines.length`,
     balanced badge text == (`result.balanced?'Balanced':'Unbalanced'`), strip shows `source`+`coverage`+`note`.
     Assert the rendered numbers equal `result` cents VERBATIM.
  2. §POSTED-GATE: role 103 (isshowacct=N) → DOM `.posted-line` count === 0, refusal text names the reason;
     repeat for `allowOrgs:[50000]` out-of-scope (role 102) → 0 rows, reason out-of-scope.
  3. §POSTED-COVERAGE: unposted record (role 102) → 0 rows, the engine `note` is the empty-state text.
  4. §POSTED-CTX: `buildCtx` over a real `idmp_session.buildContext` → asserts `{role.id, allowOrgs}`, org-0⇒'*'.
- Log `§POSTED-READ … rows=N balanced=Y source=oplog coverage=partial`, `§POSTED-GATE role=103 …
  dom-rows=0 leak=N`, `§POSTED-COVERAGE … note=…`, `§POSTED-CTX allowOrgs=…`. Exit non-zero on any fail.

## 7. DEFERRED TO EXPLICIT GO (NOT this session)
Mount `openPosted` into `idempiere.html` (`#idmp-content`, near `#idmp-tabstrip`) on record select; register
the pill in `pills.json`/`erp_pills.js`; the bim-ootb deploy (branch off `origin/main` first — current base
`idmp-host-conformance` is WRONG; bundle per master §8). These wait for GO + the base-branch fix.

## 8. INVARIANTS HONORED
- **Alias every read column** — sql.js returns DECLARED case (`C_Invoice_ID`, `GrandTotal` vs lowercase
  `ad_role_id`); every SELECT in the arrange aliases (`… AS id`) or risks `undefined→NaN→silent unbalanced`.
- **No `Date.now`/`Math.random`** in any op path (none needed; the panel is pure render).
- **Never gate the Posted tab** — `readPostings` honesty is engine-enforced; the panel renders it, full stop.
- **Tour untouched** — no `help_*` edits, no `data-ad-table` tag changes (this session adds no chrome).

## 9. MOBILE LENS — the accordion card (same fold, phone makeup) — Witness §POSTED-MOBILE
The phone does NOT shrink `idempiere.html` (that's the big-screen lens). On mobile the Posted view is a
**collapsible accordion card**, busy-app modern — `LENS_FAMILY.md:21` *"Phone = act"*. It renders the SAME
`buildPostedVM(result)` — a second render of the one fold, NO new data path, NO fork (`DATA_ACQUISITION_ORCHESTRATION.md §6`).
**REUSE existing familiar controls (user: "reuse existing familiar controls" / "double-click drill, phone back = go back"):**
- **Accordion = the proven `ad_ui.js` idiom** — emit the SAME classes `.acc > .hd(.lbl > .chv ▶) + .bd`,
  open = `.hd.open`+`.bd.open` (chv rotates, bd max-height 0→40vh). Inherits its look + toggle (`ad_ui.js:572-651`).
- **Drill = double-tap** (the existing `ad_ui.js §1` double-tap/long-press → cascading drill); **phone BACK =
  go back** — reuse the `ad_graph.js:1557 _onPopState` trap (pushState on open → `popstate` closes/goes back).
  Exposed as `bindBack(closeFn)` (no-op when no `window`, for the node witness).
- **Pills = the existing `icons.js`/`window.ICONS` registry** (NOT new icons). `pillControls(vm)` (PURE) →
  the control set, coverage-gated: `install` (when coverage∈{partial,absent} — lift toward complete, §3.3) ·
  `share` (when posted — OS share sheet, `navigator.share` honest fallback) · `verify` (when posted — chain
  trust via `window.ERP.verify`). Refused → NO data pills, just the honest refusal. Witness asserts this VERBATIM.
- `mountAccordion(vm, opts)` → `.acc` card: header (ledger icon · "Accts Posted" · balanced badge VERBATIM ·
  coverage chip · ▶ chv) · body (`.bd` line rows / empty-note / refusal) · pill row (`pillControls`, handlers
  injected `opts.onInstall/onShare/onVerify`, honest no-op default). Collapsed authorised lines stay in DOM
  (CSS-hidden, NOT a leak); a GATE renders ZERO `.posted-line` regardless of open (engine zero-leak holds).
- **Witness §POSTED-MOBILE** (extend `poc_accts_posted.js`): posted → `.acc` present, header badge=="Balanced",
  coverage chip=="partial", `.bd .posted-line`==vm.rows.length, pill set==`[install,share,verify]`, `bindBack`
  callable; gate → 0 lines + refusal + NO data pills (leak=N); absent → empty note + `install` pill present.

## # DONE (2026-06-03 — Item C built + witnessed; NOT deployed. Log: bim-ootb/erp/tests/poc_accts_posted.log)
Files (NEW only, no chrome edit): `bim-ootb/erp/accts_posted.js` (`buildCtx`+`buildPostedVM`+`mount`+`openPosted`,
UMD `window.AcctsPosted`) · `bim-ootb/erp/tests/poc_accts_posted.js`(+`.log`). `erp_postings.js` consumed
BYTE-IDENTICAL (no fork — `diff -q` vs `bim-compiler/scripts/erp_postings.js` = identical). `idempiere.html` UNTOUCHED.
Witness **✅ POC-ACCTS-POSTED ALL PASS**, every claim → a `§` line:
- **§POSTED-READ** `record=C_Invoice#101 role=102 rows=2 balanced=Y source=oplog coverage=partial rendered=verbatim`
  — DOM `.posted-line`==engine lines(2); badge=="Balanced"==`result.balanced`; every DR/CR==engine cents; strip shows source+coverage+note verbatim. (real ad_seed accts 219/220, real invoice GT 100.70.)
- **§POSTED-GATE** `role=103 isshowacct=N → visible=N reason=role-not-accounting dom-rows=0 leak=N` AND
  `role=102 allowOrgs=[50000] org=11 → visible=N reason=out-of-scope dom-rows=0 leak=N` — UI zero-leak holds at the DOM.
- **§POSTED-COVERAGE** `record=C_Invoice#999 source=none coverage=absent note="install local data first…"` — engine note rendered VERBATIM as empty state; panel never suppressed.
- **§POSTED-CTX** `org0-sentinel→allowOrgs="*" real-orgs→allowOrgs=[11,12]` — org-0 branch exercised via the real idmp_session `{id:0}` sentinel (NOT vacuous; ad_seed has no ad_org_id=0 row, noted); scoped path on REAL ad_org ids.
- **§POSTED-MOBILE** (the accordion lens §9, same `buildPostedVM` fold, NEW: `pillControls`/`mountAccordion`/`bindBack` in `accts_posted.js`): `acc=1 badge=Balanced cov=partial lines=2 pills=[install,share,verify] bindBack=ok` · gate `dom-lines=0 pills=[] leak=N` · absent `pills=[install]`. REUSES existing controls — `ad_ui.js .acc/.hd/.bd` accordion, double-tap drill, `ad_graph.js` popstate back-trap, `window.ICONS` pills (no new control scheme). Mobile Install/Migrate = delegate-to-machine + mirror (user-chosen); QR = pointer+hash to the operator's own host, pure-data payload (code resident) — `docs/DATA_ACQUISITION_ORCHESTRATION.md §0.1`.
Invariants honored: no `Date.now`/`Math.random` (grep clean); every read column aliased; Posted tab never gated; no `help_*`/`data-ad-table` edits. `node -c` clean both files.
**Deferred to GO (master §7/§8):** mount `openPosted` into `idempiere.html` (`#idmp-content` near `#idmp-tabstrip`)
+ pill in `pills.json`/`erp_pills.js`; branch off `origin/main` (current base `idmp-host-conformance` WRONG); bundle per master §8; sw bump. **NO deploy this session.**
HONEST scope note: this witness proves the RENDERER (verbatim + zero-leak + honest coverage). Account-RESOLUTION
fidelity (receivable/revenue/tax) is proven separately in `bim-compiler/scripts/poc_postings.js` §POSTED-READ; the
arrange here uses real suspense accts (219/220) only to exercise a real balanced fold — not an accounting claim.
