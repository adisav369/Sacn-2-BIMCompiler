# ⚠ DO NOT REMOVE — Scope guard
# Scope: the UI CRUD "ring of fire" as a SEPARATED keyed overlay (sibling to help_overlay.js), wiring
#        New/Edit/Delete on any element bubble to the EXISTING kernel engine (no new engine). This is the
#        T3 (editing) phase, and it is exactly Phase B's "invoke the A3 guards on real document flows".
# Spec-first; witness-led (each task names the issue it proves); §-log first, Playwright for wiring only.
# Log Mandate: save every run to a log, READ the log before conclusions. EXPLICIT GO before any deploy.
# Read first: prompts/READSHOWME_DYNAMIC_SPEC.md (the overlay pattern this mirrors) + docs/ERP.md +
#             docs/DistributedERP.md §0/§5/§7 (op-log, single-writer, identity-as-input).

---

# CRUD Overlay — the "ring of fire" as a keyed behavior layer (spec)

## Why now (the engine already exists — this only adds a UI layer)
The write engine is built and proven; CRUD is making it reachable from the UI, not new plumbing:
- **Apply + verbs:** `scripts/erp_kernel.js` `apply(op)` with CREATE/ALLOCATE/MATCH, identity as a recorded
  input (`op_uuid`/`edgeMint`, G-IDENTITY — `docs/ERP.md §0.21`). New ops are appended, never recomputed.
- **Signed, tamper-evident op-log:** `bim-ootb/viewer/kernel_ops.js` `commitOp` → `sealChain` (prev_hash/
  op_hash + `setSigner` W-SIGN, LIVE since Phase A); `verifyChain` proves the chain. Every CRUD write is ONE op.
- **Single-writer + CAS guards:** `bim-ootb/viewer/erp_replay.js` (owner-gate + compare-and-set, Phase A A3) —
  LOADED but not yet INVOKED. CRUD is where they get invoked on a real document flow (this prompt = Phase B).
- **Read-only seam already in the UI:** glassbowl's dossier **History** tab renders the real op-log with a
  read-only ↶ reversal *preview* ("would reverse N ops — read-only, not enabled"); the blurb shows the greyed
  ring-of-fire teaser (＋New ✎Edit 🗑Delete). This overlay turns that seam live.

## The pattern (mirror help_overlay.js — separation is the point)
A second peer overlay on the SAME keyed-hook mechanism (the "powerful pattern": one element key, a stack of
independent keyed concern-layers — Help, now CRUD, later i18n/Validation/Access). It edits NO renderer.
- **`crud_overlay.js`** (standalone module, like `help_overlay.js`): injects its own CSS, an **Edit-mode**
  toggle, the ring-of-fire affordance per element, and the edit form. Keyed by the element id (every bubble =
  its table id; later an AD field = its column id). Dispatched by element **kind** (req 11 of the help spec):
  bubble→the document/table form; text field→inline edit; list→pick; etc.
- **`crud_ops.json`** (keyed `_TRL`-style store, sibling to `help_ops.json`): per element key, what CRUD is
  permitted and how. Each tagged element **exposes itself to the CRUD layer and possesses its OWN rules** — a
  per-field metadata block that IS the iDempiere `AD_Column`/`AD_Field`/`AD_Val_Rule` model:
  `{ key → {op, verbs:[create,update,delete], ownerGated:true, cas?:"<col>",
     fields:[{ col, label, type:"string|number|date|list|yesno|fk",
               readonly:false, required:false, default:<val|expr>,
               validation:{ min?, max?, regex?, valRule?:"<named rule>" }, ref?:"<fk table>" }] } }`.
  The CRUD overlay renders the form, enforces type/readonly/required/default, and runs the **validation layer**
  BEFORE `apply()` — exactly the AD's "checks before saving" (already surfaced read-only in the dossier Rules
  tab). Devs edit ONE file; the overlay reads it by key. No per-feature wiring. Validation, types, readonly,
  defaults are themselves a keyed concern-layer (see the governing doc, `prompts/UI_OVERLAY_GOVERNANCE.md`).

## Requirements (carry the design language already established)
1. **Edit-mode toggle, top-right** (peer to NeedHelp?) — OFF by default; CRUD mutates, so it is gated behind an
   explicit "Edit mode" check. Off = zero affordances, zero risk.
2. **Ring-of-fire anchored to the element** — when Edit-mode is on, ＋/✎/🗑 ring appears on each keyed bubble
   (positioned like the `?` badge, riding pan/zoom via the same projection globals). Click → the edit form.
3. **Every change is a signed op** — ＋New/✎Edit/🗑Delete call `kernel.apply()` → `commitOp`/`sealChain`; after
   each, `verifyChain` MUST still pass. No direct DB write; the op-log is the source of truth (the fold).
4. **Local-first, no server** — the write lands in the local op-log; the projection re-folds and the UI updates
   immediately; sync/merge is later and authority-free (DistributedERP §0/§3). Delete = an inverse/tombstone op,
   never a destructive erase — reversible by the same log (the History ↶ becomes real undo).
5. **Owner-gate + CAS (invoke A3)** — writes pass through `erp_replay`'s owner-gate (G-SINGLE-WRITER: a non-owner
   op is rejected on replay) and the one entitlement op-class uses set-if-unset CAS. This is Phase B's actual
   surfacing of the Phase-A guards.
6. **Type-aware editing** — `kind` drives the affordance: bubble→form of its `fields`; text→inline; list→picker;
   (the same dispatch that lets this work on `erp.html` AD fields later, not just glassbowl bubbles).
7. **Audit-visible** — a CRUD write shows up in the History tab instantly (it IS an op); the ↶ preview becomes a
   real, signed reversal. Help's ShowMe can later demonstrate a CRUD step ("Show me how to raise an invoice").
8. **Separation/maintenance** — `crud_overlay.js` + `crud_ops.json` are independent of the renderer and of
   `help_overlay.js`; deleting either leaves the page intact (the overlay-layer guarantee).

## Witnesses (headless §-log first; Playwright wiring only)
- `§CRUD mode=on rings=K` (Edit-mode toggles, K editable bubbles from `crud_ops.json`).
- `§CRUD create key=c_order op_uuid=<u> chainLen=N+1 verify=ok` — a New appends ONE op, chain still verifies.
- `§CRUD update key=c_invoice field=grandtotal old=…→new=… op=UPDATE verify=ok projectionRefolded=Y`.
- `§CRUD delete key=… tombstone=Y reversible=Y verify=ok` — delete is a reversible op, not an erase.
- `§CRUD owner-gate key=… nonOwner=rejected` and `§CRUD cas key=… setIfUnset=ok|conflict` (invoke A3).
- Reuse `§VERIFY_LEDGER ok=true` after a batch of CRUD ops (the W-CHAIN proof end-to-end).

## Build order (each names its witness; EXPLICIT GO before deploy)
- **E1 — `crud_ops.json`** (O2C tables first: which fields editable, which verbs, ownerGated/CAS). Witness `§CRUD rings`.
- **E2 — `crud_overlay.js`**: Edit-mode toggle + ring-of-fire + form, keyed/kind-dispatched; NO writes yet
  (dry-run logs the op it WOULD apply). Witness `§CRUD create … (dry)`.
- **E3 — wire to the engine**: `apply`→`commitOp`/`sealChain`; `verifyChain` after each; projection re-fold +
  History refresh. Witness `§CRUD … verify=ok projectionRefolded=Y`.
- **E4 — invoke A3 guards**: owner-gate + CAS on the write path (Phase B). Witness `§CRUD owner-gate/cas`.
- **E5 — Playwright wiring + visual**: Edit-mode→ring→form→save→History shows the op; serve + eyeball.
- **E6 — Deploy (Glassbowl-way, EXPLICIT GO)**: copy `build/erp/{glassbowl.html,help_overlay.js,help_ops.json,
  crud_overlay.js,crud_ops.json,figs/,glassbowl_data.db,sqljs/}` → `docs/`; `mkdocs gh-deploy --force`.

## Guardrails
- It is all demo/POC; nothing is wired to production. T3 (editing) was parked — this is its careful, signed,
  reversible first cut, gated behind Edit-mode.
- No new engine: reuse `kernel.apply`/`commitOp`/`sealChain`/`verifyChain`/`erp_replay`. If a write needs a
  capability the kernel lacks, that is a kernel task (spec it), NOT a silent UI shortcut.
- Never a destructive DB write — every change is a signed, reversible op in the log (the fold is the truth).
- Read the log after every run; non-invent; cite spec/file:line before code.

## §Process — the DocAction verb (the missing concern; READSHOWME_DYNAMIC_SPEC §ShowMe-as-coach)
CRUD writes a row; **Process runs the document's state machine** (iDempiere DocAction: DR→Complete→CO,
also Void/Close/Reverse). Its legal action depends on the current `docstatus`, and it has cross-document
side effects (Complete an invoice → posts; the allocation step = the kernel MATCH op). It is a DISTINCT
verb, not CRUD — a new entry in `verbVocab`, even though its metadata rides inside `crud_ops.json` the way
Validation does. This section adds it; the live state machine wiring stays E3 (the `applyOp` dry-run seam).

- **`verbVocab` gains `process`.** Per-entry `verbs[]` gains `process` ONLY for documents that have a
  lifecycle. NON-INVENT basis (verified against source, 2026-06-01):
  - a document is processable ⇔ it has a `docstatus` column in `glassbowl_data.db` AND a real `completeIt()`
    CO cell in the embedded `G` graph. Verified: `c_order` (docstatus ✓, 4 CO cells, `MOrder.completeIt()`),
    `m_inout` (✓, 2, `MInOut.completeIt()`), `c_invoice` (✓, 2, `MInvoice.completeIt()`), `c_payment`
    (✓, 2, `MPayment.completeIt()`).
  - **`c_allocationline` is Process N/A** — it has **no `docstatus` column** and **no `completeIt()` cell**;
    it is a reconciliation line (child of `C_AllocationHdr`), not an independently completable document.
    This matches iDempiere. It keeps `verbs:[create,delete]`, no `process`.
- **`docAction` descriptor** per processable entry — the data that drives the dry-run deterministically:
  `docAction: { action:"CO", from:"DR", to:"CO", requires:["<col>", …] }`. `requires` = the mandatory cols
  that must be non-empty for Complete to succeed (extracted from the entry's existing `fields[].required`).
- **Three post-Process outcomes (the guide reads these off the live status bar — READSHOWME §Next-gated):**
  - all `requires` cols non-empty → docstatus = **`CO`** (success) → the step may advance.
  - a `requires` col empty/unmet → docstatus = **`IP`** (In Progress — a legitimate business non-completion,
    NOT an error) → hold; the guide re-highlights the status it found.
  - an exception → the global **ErrorReport** catcher owns it (canned "ErrorReport raised. Submit."); the
    overlay does not re-implement error handling.
- **`#docStatusBar`** — a self-injected fixed status element (peer chrome, like the Edit-mode pill) that
  reflects the lit / last-processed document's `docstatus` (DR/IP/CO/CL). This is the `statusbar`
  element-kind TARGET the Help guide highlights ("note the status"). Self-contained, injected by the overlay.
- **Pure-core + dry-run:** `CORE.buildOp('process', …)` → `op_type:'DOC_ACTION'`, `{from, to, action}` with
  `to` derived deterministically from `requires` (no `Date.now`/`Math.random` — replay-safe, the existing
  CORE contract). `applyOp` logs `§CRUD process key=… action=CO from=DR to=<CO|IP> (dry) op=DOC_ACTION
  outcome=<success|in-progress>`. E3 swaps the dry-run body for the real DocAction (kernel MATCH/Complete).
- **E3 acceptance oracle:** a re-built #80001's derived lines/amounts MUST match the traced #80001 (the
  built-in oracle, prime-directive-clean). Flagged for E3; the E2 cut only proves the verb + gating + status.
- **AD generalization (R, 2026-06-01: "digging deeper into AD territory, abstract well so this engine can
  encounter all generic models"):** `process`/`docAction` are kept as DATA in the keyed store, exactly as
  `AD_Workflow`/DocAction would be — so a NEW model becomes new rows (a `docstatus` + `completeIt` cell +
  a `docAction` descriptor), never new verb code. The overlay dispatches by the generic shape, not by O2C.
  Same principle as fields=AD_Column, validation=AD_Val_Rule: the engine stays model-agnostic.

## Mount & Coordination (for the Help-overlay session — the ONE shared surface)
The Help overlay and the CRUD overlay are **independent peer layers** on the same keyed-hook mechanism
(UI_OVERLAY_GOVERNANCE.md). `crud_overlay.js` has **zero** reference to `help_overlay.js` or its internals;
it reads its own `crud_ops.json`, attaches by the same bubble keys, injects its own CSS. Delete either file and
the page + the other layer stay intact. So in CODE they do not intertwine — there is exactly **one** shared line:
- **The mount.** A single append-only `<script>` tag in `glassbowl.html`, placed **right after**
  `<script src="help_overlay.js"></script>`:
  ```html
  <!-- CRUD ring-of-fire overlay — peer behavior layer, keyed to bubbles (CRUD_OVERLAY.md E2, dry-run) -->
  <script src="crud_overlay.js"></script>
  ```
- **It is inert.** Edit-mode is OFF by default → the layer mounts and does nothing (zero affordance, zero write)
  until a user opts in. Loading it cannot affect the Help repair.
- **Applied LAST, re-read fresh.** Whoever is the *final* writer of `glassbowl.html` adds this line, after
  re-reading the file fresh (the Help session is committing live; never edit `glassbowl.html` mid-repair).
  Default plan: the CRUD side adds it in its own tiny commit during E3, once the Help repair is quiescent. If
  the Help session is the last to touch `glassbowl.html`, it may append the line itself — it is safe and inert.
- **No prompt merge.** Keep the two prompts/sessions separate (one bounded task each). This note is the entire
  contract between them; nothing else needs coordinating.

## Status (this session — E1/E2 landed, headless-proven, NOT yet mounted)
- `build/erp/crud_ops.json` (E1) + `build/erp/crud_overlay.js` (E2, dry-run) written; standalone, uncommitted.
- `scripts/test_crud_overlay.js` → `build/erp/test_crud_overlay.log`: 7-issue §-witness ALL PASS (rings/drift,
  verb-driven greying, defaults, validation-rejects-before-apply incl. readonly, CRUD_CREATE/UPDATE/DELETE dry).
- `glassbowl.html` deliberately **untouched** (Help repair in flight). Mount + E3/E4/E5/E6 are next sessions.

---

## CORRELATION — engine decisions this prompt must honour (added 2026-06-03)
Cross-checked against **`prompts/ENGINE_FULL_ERP_ISSUES.md`** (the engine-issue gate) so the cast write path and
the issue list stay on the same page. Two **factual errors** in the text above need review; three **decisions**
must be carried before E3 wires real writes:

**FIX (stale/false references — verified 2026-06-03):**
- `bim-ootb/viewer/kernel_ops.js` does **not exist** — the signed kernel is **`site/kernel_ops.js`** (mirrored
  to `build/erp/kernel_ops.js`). Update every mention.
- `bim-ootb/viewer/erp_replay.js` does **not exist anywhere** in the tree. The "owner-gate + CAS (A3 guards),
  LOADED but not yet INVOKED" claim (Why-now bullet 3 + Req 5) is **unverified** — there is no such module.
  Before Req 5 ("invoke A3"), the guard must actually be located or built; do not cite it as present.

**CARRY (decisions from ENGINE_FULL_ERP_ISSUES.md):**
- **§I-D (op-log scale).** Req 3 calls `commitOp → sealChain → verifyChain` on **every** write. Confirmed:
  `commitOp` re-seals the **whole** log each op (`site/kernel_ops.js:93`+`:137`) → **O(n²)** over a session.
  Fine at hundreds; **log the ceiling** and/or land the cheap incremental seal (seal-from-last-tip) before
  writes scale. Don't let it grow silently. (FRONTEND_LANE_MASTER.md §6 line 60 already accepts this — match it.)
- **§I-B + §I-C (New/DocNo + defaults).** `New` is **live** (`site/crud_overlay.js:112`, seeds
  `AD_Column.DefaultValue`) — NOT honest-disabled. Honest on **identity** for a standalone op-log (edge-mint
  UUID = op input, §6.1 non-invent); **not** honest into an operator's numbered iDempiere (no `AD_Sequence`
  DocNo). **AND New is not "a valid doc" until I-C:** declarative defaults are captured, but **procedural
  callout-derived** values are I-C. **Couple I-B→I-C** — don't present New as done on identity alone.
- **§I-K (op-group atomicity).** Req 3 ("**every change is ONE op**") is right for a field edit but **wrong for a
  document action** — `Complete = DOC_ACTION+SHIP+INVOICE+Dr-AR+Cr-Rev`, all-or-none (ERP.md §18.8, witnessed
  `poc_showstopper.js §SHOW-ATOMIC`). The write loop must commit a **DOC_ACTION as an op-group** (whole-or-none),
  not N independent ops. Amend Req 3 before wiring document actions.
- **§I-A (durability).** Line 85 says "all demo/POC" — good; make it explicit per the doctrine: browser writes
  are **not** the system of record (DistributedERP §Truth-3 / §5.2b). Don't let a demo Save read as a durable
  real transaction. Add `docs/DistributedERP.md §6.1` to the "Read first" list (the ID problem behind I-B).
