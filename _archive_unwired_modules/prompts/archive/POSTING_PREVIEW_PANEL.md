# ⚠ DO NOT REMOVE — Posting-Preview drawer (the UI bridge that makes the FOLD work VISIBLE)
# SCOPE: a READ-ONLY "Posting preview" drawer on a document panel that, on a DocAction (Complete first),
#   runs the FROZEN fold verbs against the LOADED db and renders the to-the-cent journal the action WOULD
#   generate — BEFORE/at Complete, WITHOUT writing the db. This is the parked UI bridge from FOLD_MODEL_LOGIC.md:
#   the engine is already proven oracle-equivalent headlessly; this connects it to the screen.
# THIN FIRST SLICE: ONE panel (C_Order / Sales Order) · ONE action (Complete). Reverse/Void preview and the
#   USD↔EUR schema toggle are NAMED follow-ups in §8 — do NOT build them in the first wire-up.
# NON-NEGOTIABLE:
#   - EXTRACT/COMPILE, never invent. The journal comes from the fold verbs over REAL db rows, not hand-authored.
#   - CONSUME the frozen seams — NEVER fork `erp_engine.js` / `post_resolver.js` / `accts_posted.js`. The only
#     new code is a thin orchestration seam + the trigger wiring.
#   - PURE PREVIEW: the seam runs the fold in memory and returns a VM; it NEVER calls commitGroup / writes a row.
#     Prove db is byte-unchanged (§PREVIEW-NOWRITE).
#   - SEPARATION holds (docs/ERP_BACKEND_SEPARATION.md): declaration→data, interpreter→engine, log→fold. The
#     fold verbs EMIT the prospective ops; the panel is a PURE renderer; the seam is orchestration only.
#   - Witness-led, §-log FIRST (READ the log before any conclusion). Playwright = wiring only, never value.
#   - bim-ootb UI work → hook-blocked: edit in a `/tmp/wt-*` worktree. Deploy is GO-gated (§9): sw.js bump,
#     branch off fresh `origin/main`, smoke + fetch-back. Honour until the # DONE appendix proves every claim.

---

## ⓘ SCOPE-VERIFICATION (anchors checked against live code 2026-06-10 — APPLY these errata; they override the prose below)
The design is sound and the VM contract is exact, but FOUR cited APIs/paths were checked against the real
source and need correction. The implementer MUST use these; the §2/§3/§5 prose was written from memory.

**Verified CORRECT (consume as written):** `crud_overlay.js:99 docActionOutcome` / `:130` "not a row write" /
`:134 base.oracle` (all exact) · the VM shape `{visible,posted,lines[],balanced,source,coverage,note,reason}` is
VERBATIM what `erp_postings.js:65 readPostings` returns · each line = `{account_id,value,name,amtacctdr,amtacctcr}`
(`erp_postings.js:94`, read 1:1 by `accts_posted.js buildPostedVM`) · the gate verdicts (`reason:'role-not-accounting'`,
`'out-of-scope'`, absent→`coverage:'absent'`) are real · `erp_engine.js`/`post_resolver.js` are NOT in `bim-ootb/erp/`
(confirms "node-only, unloaded in the live html").

**ERRATA (the prose is wrong here — use these):**
1. **`post_resolver.resolve` signature** — §2/§3 imply `resolve(token, ctx)`. ACTUAL (`post_resolver.js:60`, and every
   FOLD witness): **`resolve(db, token, masterId, acctschema)`** → returns `{acct, element, fallback, token}`; take
   `res.element.id` for the natural account (see `poc_fold_complete.js:56` `nat(R.resolve(db,'{BPartner.Receivable}',
   bpId, SCHEMA))`). The seam calls it positionally with the loaded db handle, NOT a ctx object.
2. **The C_Order Complete verb is `completeOrder`, not `completeInvoice`** — `erp_engine.completeOrder(order, lines,
   {isautogenerateinout:'Y', isautogenerateinvoice:'Y'})` (`poc_fold_complete.js:114`). `completeInvoice` is the
   standalone C_Invoice action = a §8 follow-up. The first slice mirrors `poc_fold_complete.js` end-to-end.
3. **Source-of-truth paths** — `erp_engine.js`/`post_resolver.js` live ONLY in `scripts/` (bim-compiler); the app
   modules (`accts_posted.js`/`crud_overlay.js`/`erp_postings.js`) live ONLY in `bim-ootb/erp/`. **Neither is in
   `build/erp/`.** So: UMD-tail `scripts/{erp_engine,post_resolver}.js` then copy those two into `bim-ootb/erp/`; and
   author the new `erp_preview.js` DIRECTLY in `bim-ootb/erp/` (its sibling `accts_posted.js` has no `build/erp`
   source either). Drop every "`build/erp/erp_preview.js` source" / "build/erp is the SoT, UMD-copy from there"
   line — it misapplies the engine-module rule (which is for `build/erp/ad_*.js`, a different family).
4. **The `isshowacct` gate is in `erp_postings.js:71` (readPostings), not `accts_posted.js`** — `accts_posted.buildCtx`
   gives the ctx, but the role refusal lives in `erp_postings`. For §PREVIEW-GATE, reuse `erp_postings`'s gate (or
   call its gate path) so the refusal verdict is the SAME one the Accts-Posted panel uses — do not re-author it.

**DATA REALITY — decide before building (verified 2026-06-10):** `glassbowl_data.db` has **NO draft order** — all 8
`C_Order` rows are `CO` (7) / `CL` (1); none are `DR`. So "press Complete on a real draft SO" has no subject in the
oracle db. Pick ONE, state it in §1/§6, don't discover it mid-build:
  (a) **Preview a COMPLETED order's re-derivation** — run the same `completeOrder` fold over a CO order and render
      the journal it WOULD have produced; cross-check == `poc_fold_complete` (which already folds these CO orders).
      Honest framing: "what Complete produces for this order," trigger gated on draft-OR-demo. Simplest, no synthesis.
  (b) **Sandbox-clone one CO order to DR in memory** (flip `docstatus` on the in-memory row only, never the db —
      keeps §PREVIEW-NOWRITE) and preview that. Closer to the real gesture but adds a synthesis step to defend as
      non-invent (the clone is a real order's real lines; only its status is toggled for the dry-run).
Recommend (a) for the thin first slice — it reuses the `poc_fold_complete` cross-check verbatim with zero synthesis.

**One reinforcement (already implied by §6, make it load-bearing):** §PREVIEW-COMPLETE can only equal
`poc_fold_complete.js` if the seam runs over the SAME db that witness uses (`glassbowl_data.db`, client 11, schema
101) — the app's normally-loaded AD db need not carry that draft order + acct config. The witness must run the seam
over `glassbowl_data.db` and STATE it; matching "the loaded db" is only valid when the loaded db IS that one.

---

## 0. WHY THIS / COORDINATION (verified 2026-06-10)
The FOLD lane (`prompts/FOLD_MODEL_LOGIC.md`, FROZEN at `14df1408`) made the engine **capable** of driving these
panel interactions to the cent — `W-FOLD-COMPLETE` (Order→Ship→Invoice→GL), `W-FOLD-REVERSE` (negating journal
nets to zero), the two FX folds (USD↔EUR repost). It does **not** connect them to any screen — `erp_engine.js` /
`post_resolver.js` are node-only and unloaded in the live html; pressing Complete today only paints `DR→IP→CO`
(`crud_overlay.js:130` "doc state machine, **not a row write**"). This prompt is the connection.
- **DISJOINT from FOLD** (frozen, theirs) and from `AD_BEHAVIOR_HANDOFF.md` (the *declarative* wirings —
  DisplayLogic/access/process). This is the *imperative/posting* axis made visible. Different lane, different files.
- **Shared-file watch:** UMD tails added to `scripts/erp_engine.js` + `scripts/post_resolver.js` are ADDITIVE
  (trailing `window.*` export, zero logic change — node tests stay green); `build/erp/` is the ERP source-of-truth
  (`feedback_erp_source_of_truth.md`) → edit there, UMD-copy to `bim-ootb/erp/`, NEVER blind-copy. `sw.js` bump on
  deploy (conflict magnet — higher version, keep both precache notes).

## 1. ISSUE + WITNESS (each test NAMES its issue)
| Witness | Issue it proves |
|---------|-----------------|
| **§PREVIEW-COMPLETE** (rendered) | Complete on a REAL draft Sales Order → the rendered journal == the SAME lines the headless `poc_fold_complete.js` oracle produces (per account, side, INTEGER CENTS, `maxDiff=0c`). The screen shows the proven fold, not a re-derivation. |
| **§PREVIEW-NOWRITE** | the preview is pure: after rendering, the loaded db is BYTE-UNCHANGED (row counts + a content hash of the doc/line/fact tables identical pre/post). No commitGroup, no persist. |
| **§PREVIEW-DATA** | when the loaded db lacks the draft order or the acct-config tables `post_resolver` needs, the drawer renders an HONEST "preview needs GardenWorld data" empty state (mirrors `accts_posted` coverage:absent) — it NEVER invents numbers or silently shows zeros. |
| **§PREVIEW-GATE** | a role without the accounting view (reuse `ad_role.isshowacct` / the posted gate) → ZERO account rows in the DOM (UI zero-leak), honest refusal. |
| **§PREVIEW-FALSIFIER** | drop/corrupt one derived line → `balanced=false` is SHOWN (badge flips) — proves the panel reflects the real fold, not a hard-coded "Balanced". |

## 2. CONTRACTS CONSUMED (FROZEN — do NOT rebuild or fork)
- `scripts/erp_engine.js` (`module.exports`): `buildDoc`, `explodeBOM`, `movementSign`, `qtyOnHand`,
  `reversePosting`, `completeInvoice`, … — PURE (host injects `query`/`bomOf`). The Complete fan-out verbs.
- `scripts/post_resolver.js`: `resolve(token, ctx)` / `elementOf` / `TOKENS` — token→natural-account resolution
  (`{Product.Revenue}` `{BPartner.Receivable}` `{Product.Cogs}` …) over the acct-config tables.
- `bim-ootb/erp/accts_posted.js`: `buildPostedVM(result)` + `mountAccordion`/`renderPosted` — the VM + renderer.
  The preview result is the SAME VM shape `{ visible, posted, lines[], balanced, source, coverage, note, reason }`
  with `lines[] = {account_id, value, name, amtacctdr, amtacctcr}` — so the renderer needs NO change, only a new
  `source:'preview'` label + a "PREVIEW — not yet posted" strip so it reads as prospective, not booked.

## 3. NEW SEAM (the only real new logic — thin orchestration)
`bim-ootb/erp/erp_preview.js` (source: `build/erp/erp_preview.js`; UMD tail `window.ERPPreview` + `module.exports`):
```
previewDocAction({ recordRef, action, schema }, dbs) -> VM   // action='Complete' (slice); recordRef={table:'C_Order', id}
```
- Loads the draft doc + lines from the live db (alias every column — sql.js returns DECLARED case; unaliased →
  `undefined→NaN→silent unbalanced`, the `accts_posted` §8 trap).
- Runs the FROZEN fold for `action` in memory: Complete → `buildDoc` fan-out (Ship/Invoice per `C_DocType` config)
  → `post_resolver.resolve` for each line → aggregate by (account, side) in INTEGER CENTS. NO db write.
- Returns the `buildPostedVM`-shaped object (`source:'preview'`, `coverage` = complete | partial | absent by
  whether all config resolved). Missing data → `posted:false, coverage:'absent', note:'preview needs … data'`.
- Reuse the `accts_posted` gate for `visible`/`reason` (role-not-accounting / out-of-scope) — same `buildCtx`.

## 4. TRIGGER WIRING (reuse the existing hook — do NOT fork crud_overlay)
`crud_overlay.js:99 docActionOutcome` already returns the DocAction outcome and carries a `base.oracle` slot
(`:134`). Wire the E3 path: on a `process` (DocAction) verb for `C_Order`+Complete, call
`window.ERPPreview.previewDocAction(...)` and mount its VM via `accts_posted.mountAccordion` into a drawer on the
panel (near `#idmp-content`). The status bar still paints `DR→IP→CO` (unchanged); the drawer ADDS the journal
preview. The op-log / actual posting is OUT of scope here — this is preview-only (E3-real-write is a later lane).

## 5. FILES (NEW / ADDITIVE only — no logic edits to frozen seams)
- `build/erp/erp_preview.js` (+ UMD-copy `bim-ootb/erp/erp_preview.js`) — the §3 seam. NEW.
- `scripts/erp_engine.js`, `scripts/post_resolver.js` — APPEND a UMD tail (`if (typeof window!=='undefined')
  window.ErpEngine=…/window.PostResolver=…`). ADDITIVE; node `module.exports` untouched; re-run the FOLD witness
  set to prove byte-behaviour unchanged. UMD-copy the resulting files to `bim-ootb/erp/`.
- `bim-ootb/erp/tests/poc_posting_preview.js` (+ `.log`) — the witness (§6).
- `bim-ootb/erp/crud_overlay.js` — MINIMAL trigger hunk only (call the seam + mount the drawer); no verb change.
- **NOT touched in the build session:** the live mount into `idempiere.html` chrome + pill registration — GO-gated (§9).

## 6. WITNESS DESIGN (`poc_posting_preview.js` → `tests/poc_posting_preview.log`)
Node + sql.js + a DOM shim (mirror `poc_accts_posted.js`). Drive the REAL local `erp_preview.previewDocAction`:
- Use the db that actually carries a completable GardenWorld order + acct config — `glassbowl_data.db` (client 11,
  the FOLD oracle db) or whichever loaded db has it; the witness must STATE which (no silent substitution).
- **§PREVIEW-COMPLETE**: a real draft `C_Order` → render → assert the DOM `.posted-line` rows, each DR/CR cent,
  and the natural accounts == the lines `scripts/poc_fold_complete.js` emits for the same order (cross-check the
  two logs; `maxDiff=0c`). Balanced badge == VM.balanced.
- **§PREVIEW-NOWRITE**: hash the doc/line/fact tables before+after `previewDocAction`; assert identical.
- **§PREVIEW-DATA**: a db missing the acct-config → `coverage:absent`, empty-state note rendered, ZERO invented rows.
- **§PREVIEW-GATE**: role `isshowacct=N` → DOM `.posted-line` count === 0, refusal text names the reason (leak=N).
- **§PREVIEW-FALSIFIER**: corrupt one resolved line → badge shows "Unbalanced"; assert the DOM reflects it.
- Log each `§` VERBATIM with the numbers; exit non-zero on any fail. READ the log before concluding.

## 7. INVARIANTS HONORED
- Pure preview: no `commitGroup`, no row write, no `Date.now`/`Math.random` (grep clean) — deterministic.
- Alias every read column. Integer cents throughout (no raw JS Number money — `feedback_numbers_via_bigdecimal`).
- Frozen seams consumed byte-identical (`diff -q` the UMD copies vs `build/erp`/`scripts` source; FOLD witness set
  re-run green after the UMD tail). Panel never re-gates / re-balances / re-folds — renders the VM verbatim.
- The drawer reads "PREVIEW — not yet posted" so it is never mistaken for a booked journal.

## 8. NAMED FOLLOW-UPS (NOT the first slice — each its own session, same rails)
1. **Reverse/Void preview** — `action='Reverse'` → `erp_engine.reversePosting` over the doc's forward derive →
   show the negating journal + a "nets to zero" badge (`W-FOLD-REVERSE`). Same VM, same renderer.
2. **Schema toggle USD↔EUR** — a control on the drawer reposts the same doc through acctschema 200000 via the FX
   folds (`W-FOLD-ALLOC-FX` / per-leg `round(AmtSource×rate, HALF_UP)` in BigInt) — same order, other currency.
3. **Other doc panels** — C_Invoice, C_Payment, M_Inventory (the panels whose folds are already oracle-equivalent).
4. **E3 real write** — promote preview → actual posting via `kernel_ops.commitGroup` (a separate, heavier lane;
   needs the op-log persist + verifyChain path — explicitly OUT of this preview-only prompt).

## 9. DEFERRED TO EXPLICIT GO (deploy)
Mount the drawer into `idempiere.html` chrome + register the pill (`pills.json`/`erp_pills.js`); bundle + sw.js
bump; branch off FRESH `origin/main`; smoke the URLs + fetch-back + confirm the script loads. All GO-gated; no
deploy in the build/witness session.

## 10. STOP CONDITION
`poc_posting_preview.js` exits 0 with all five `§` lines proven; the SO panel's Complete renders the to-the-cent
fold (witnessed == `poc_fold_complete`); db byte-unchanged; seams un-forked; UI mount/deploy parked for GO.
If a step needs a user fact that can't be EXTRACTED → `⛔ BLOCKED: <the one question>` and move on.

---

## # DONE (2026-06-10 — Phases 1+3 built + witnessed headless; NOT deployed; UI mount GO-gated §9)

Built across two phases, each `§`-proven; every claim below traces to a re-runnable log line.

### Phase 1 — Gap-A closed: the per-document poster is now a SHIPPED, oracle-anchored verb (NOT witness-local)
- **`scripts/doc_poster.js`** — `derivePostings(db, recordRef, schema, R)`, EXTRACTED VERBATIM from
  `poc_fold_complete.deriveInvoice` (W-FOLD-COMPLETE). PURE, db-agnostic (`.prepare().get/.all` — better-sqlite3
  OR the sql.js facade). C_Order → its generated invoice manifest (oracle path); true-draft (no invoice) →
  projected order manifest (`basis='order'`, **labeled no-oracle**). Shipment COGS leg = §8 (data-gated, NOT here).
- **`scripts/poc_doc_poster.js` → `build/erp/poc_doc_poster.log` (exit 0):**
  - `§DOC-POSTER id=100/101/102 maxDiff=0c(EQUIVALENT)` — **3/4 == `fact_acct(318)` to the cent**, ΣDR=ΣCR=Y.
  - `§DOC-POSTER id=108 maxDiff=4039c(AMT-DRIFT)` — the SAME post-posting drift `poc_fold_complete`/`poc_post_harden`
    name (doc 109; audit §A verifies it as a real dated edit, not an excuse). Named, not failed.
  - `§FALSIFIER drop-receivable-DR maxDiff=5035c` — identical to `poc_fold_complete`'s falsifier (load-bearing).
- **`scripts/post_resolver.js`** — ADDITIVE UMD tail (`window.PostResolver`, same exports). **6 FOLD witnesses
  re-run green** (poc_fold_complete, poc_post_harden, poc_invoice_post_ap, poc_matchinv, poc_alloc_post,
  poc_movement — all exit 0). Behaviour unchanged.
- **Audit tie-in (F-TIER-1):** W-DOC-POSTER is **Class A** (fold vs independent product `fact_acct`) — the audit's
  SOLID tier; it inherits W-FOLD-COMPLETE/W-POST-HARDEN's SOLID verdict. NOT a declarative/config-readback claim.

### Phase 3 — the Posting-Preview seam + render witness (browser sql.js path), all reuse-not-fork
- **`bim-ootb/erp/erp_preview.js`** (worktree `/tmp/wt-preview`) — `previewDocAction(recordRef, ctx, dbs)` +
  `openPreview` + a **sql.js→better-sqlite3 facade** (positional/named/scalar params). Gate MIRRORS
  `erp_postings.js:71-85` verbatim (same SQL, reasons, verdict). Derives via `window.DocPoster`, resolves via
  `window.PostResolver`, RENDERS via `window.AcctsPosted.buildPostedVM/mount` (the SAME renderer the Accts-Posted
  panel uses — zero new render code). Returns `source:'preview'`; PURE (no commitGroup/write).
- **`bim-ootb/erp/tests/poc_posting_preview.js` → `tests/poc_posting_preview.log` (exit 0, all 5 §):**
  - `§PREVIEW-COMPLETE doc=C_Order id=100 docno=80000 invoice=100 rows=3 maxDiff=0c vs fact_acct(318) balanced=Y
    coverage=complete source=preview class=A` — real accounts rendered (518 AR 50.35 DR / 596 Tax 2.85 CR /
    758 Revenue 47.50 CR); DOM `.posted-line`==3; badge "Balanced" verbatim.
  - `§PREVIEW-NOWRITE db_bytes=393216 unchanged=Y commitGroup=none` — `glassDb.export()` byte-identical pre/post.
  - `§PREVIEW-DATA record=C_Order#999999 posted=N coverage=absent dom-rows=0 note="preview needs GardenWorld data…"`
    — honest absent, ZERO invented rows.
  - `§PREVIEW-GATE role=103 reason=role-not-accounting dom-rows=0 leak=N ; role=102 allowOrgs=[50000] org=11
    reason=out-of-scope dom-rows=0 leak=N` — UI zero-leak holds (gate from real ad_seed `isshowacct`).
  - `§PREVIEW-FALSIFIER mutation=drop-receivable-DR balanced=N badge=Unbalanced coverage=partial` — the panel
    reflects the real fold (not a hard-coded "Balanced").
- **Hygiene:** `node -c` clean (all 4 files); no `Date.now`/`Math.random` (grep clean); UMD copies `diff -q`
  byte-identical to `scripts/` source; fixture = full `build/erp/glassbowl_data.db` (393216) copied to
  `erp/tests/fixtures/` (the stale bim-ootb `erp/glassbowl_data.db` 131072 was NOT used).

### Multi-db / data facts (the honest gates — same family as §3 seed blocks)
- Gate reads `ad_role` from **`ad_seed.db`**; derive reads order+config+`fact_acct` from **`glassbowl_data.db`** —
  `dbs={adQ, docDb}` carries the two sources (Gap-F). LIVE value on a single loaded db needs that db to carry the
  order + acct-config; otherwise the drawer shows `coverage:absent` honestly (the seed/reshard lane's job, NOT this).

### Phase 4 — deploy UNBLOCKED (the data-gate solved headlessly; deploy itself still GO-gated)
- **Data-gate analysis (the binding precondition):** the live default `ad_seed.db` carries the preview tables but its
  DOCUMENTS lack the bpartner/product→account linkage → only `{Tax.Due}` resolves → drawer would render 1 unbalanced
  line. The to-the-cent demo needs `glassbowl_data.db`'s resolvable config. (Also observed: a parallel reshard session
  made `ad_seed.db`'s `ad_role` lazy/absent mid-session — sourced roles from stable `build/erp/ad_full.db` instead.)
- **Unblock = a single self-contained `preview_demo.db`** (`/tmp/wt-preview/erp/tests/fixtures/`, 397312 B) = a copy of
  `glassbowl_data.db` (real GardenWorld docs + acct config + `fact_acct` self-check) + a minimal real `ad_role`
  (gate cols `ad_role_id`/`isshowacct` copied from `ad_full.db`: 102=Y/103=N/…). NON-INVENT (both halves real data).
- **Witness `tests/poc_preview_demo.js` → `tests/poc_preview_demo.log` (exit 0, all 5 §)** — the SAME 5 witnesses run
  with `adQ` AND `docDb` both = the single `preview_demo.db`: §PREVIEW-COMPLETE `maxDiff=0c vs fact_acct(318)` class=A,
  NOWRITE byte-unchanged, DATA absent, GATE leak=N, FALSIFIER Unbalanced. Proves the **live single-db `?db=preview_demo.db`
  load path** lights the drawer up to the cent. Deploy is now mount+ship, no data blocker.

### Phase 5 — DEPLOYED + made AD-STANDARD (2026-06-10, PRs #232 sw v625, #235 sw v628, both LIVE)
- **#232 (v625):** the Posting-Preview pill went LIVE on `idempiere.html` (sibling lens to Accts-Posted,
  `openPreviewFor`). LIVE-SMOKE PASS.
- **Governance correction → #235 (v628) §AD-GATE:** review flagged that iDempiere's accounting view is the
  AD `Posted` Button FIELD (`AD_Column 'Posted'`, `AD_Reference=28`), which exists ONLY on posting documents —
  not a global bar pill. So the pill now **obeys the AD rule**: `pills_idmp.json` tags preview
  `showWhen:"posting-doc"`; `idmp_pills.js` honours it (build + `setDocContext()`) via host predicate
  `window.IdmpPillDocGate()`; `idempiere.html` `_isPostingDoc(table)` = AD lookup (table carries the `Posted`
  column?), cached, set on every tab open. The pill surfaces ONLY where AD defines the Posted field
  (Invoice/Shipment/Payment/Journal…) — AD-driven, per-document, exactly where iDempiere shows its Posted button.
  Witness `poc_idmp_pills §A-WITNESS-6 gateWorks=true` (off w/o doc, on with a posting doc). Regression green.
- **Open follow-up (the truly-standard end-state, NOT done):** render the `Posted` field itself as the Button it is
  (general ref-28 rendering in the record form) and hang the accounting view off THAT field — posted→actual
  `Fact_Acct`, not-posted→preview — one contextual control like iDempiere, retiring the separate pill. Touches the
  shared field-render path; scoped read-only first (DocAction-as-button = the write path, separate). See §8.

### DEFERRED (named, NOT this session)
- **GO-gated deploy (§9):** ship `preview_demo.db` to `bim-ootb/erp/`; mount `openPreview` into `idempiere.html`/
  `crud_overlay.js` E3 hook + pill; sw.js bump; branch off FRESH `origin/main`; smoke + fetch-back. No deploy this session.
- **§8 follow-ups:** shipment COGS/Inventory leg (cost data named-deferred) · true-draft synthesize-then-derive
  (no oracle) · Reverse/Void preview (`reversePosting`) · USD↔EUR schema toggle (FX folds) · other doc panels.
- **Matrix re-verdict** of the live-UI axis stays held under the UI-unpark lane (do NOT bank from here).
