# ⚠ DO NOT REMOVE — Scope guard
# Scope: the ReadMe/ShowMe DYNAMIC help-tour on the MAIN working page (glassbowl.html), generated from
#        (doc structure × live data), NOT hand-wired. Spec-first; witness-led; §-log first. Read the log.
# Supersedes the hand-authored linear tour in build/erp/TourERP.html (C0/C1 scaffold) — that proved the
# data path; this spec is the real design. Audio + §VIEWLOG-debounce already landed in glassbowl.html.

---

# ReadMe / ShowMe — Dynamic Help-Tour (spec, 2026-06-01)

## North star
The newbie's question is always **"where is it?"** — they can read *what* to do but cannot *find* the control.
So: a newcomer presses **?**, reads **what a thing is** (ReadMe = detail), then presses **ShowMe** and the page
**takes them to it and does it** — highlights the field, opens the list, traces the flow (req 11). ShowMe
answers "where is it?", which plain docs never can. It lives ON the main working page, is opt-in, fully
dismissible, and — critically — is **generated from data + the keyed doc**, so adding a paragraph or a data hop
extends the help with **no code change**. It cannot drift from truth: ShowMe replays the real instance.

## The ten requirements (from the design conversation, verbatim intent)
1. **? trigger — a "NeedHelp?" checkbox, top-right (iDempiere help-toggle pattern).** Unchecked by default
   (off = non-burdensome). Checking it turns ON the `?` tooltip-assistants: a `?` appears on every element that
   has a ReadMe tag (req 9). This is the iDempiere "show help" toggle, not a generic pill. (R: "NeedHelp? check
   box at top right which was to show a panel … perhaps this be a better approach"; "presses a ? … or it appears")
2. **? → ReadMe → ShowMe.** Press `?`/select a thing → a card shows **HTML explaining what it is** (ReadMe);
   the card has a **▶ ShowMe** that "leads back and does it for the user" (drives the page). (R: the core flow)
3. **On the main working HTML, not an orphan page.** The tour is a layer IN `glassbowl.html` (the page it
   drives), not a separate `TourERP.html`. (R: "called from the main working HTMLs right?")
4. **ReadMe is arranged in logical steps for standard ops, so they sync.** The help doc is ordered: per
   standard operation (O2C, P2P, GL…), an ordered sequence of paragraphs. (R: "arranged in logical steps … so they synch")
5. **Each Next ↔ next paragraph; ShowMe shows in the open tab.** Tour "Next" advances to the next ReadMe
   paragraph; that paragraph's ShowMe demonstrates in the actual open tab/panel. (R: "each next correlate to
   its next para. Its ShowMe goes back to the open tab to show")
6. **ShowMe = direct steps, light on detail (detail is in ReadMe).** The in-page step prompt is terse — just
   enough to drive — because the explanation lives in the ReadMe. (R: "more direct steps without much details")
7. **Dynamic, not concretely wired.** Steps are GENERATED, not hand-authored: zip the ReadMe doc's ordered
   step-units against the live data lineage; each step's ShowMe action is DERIVED from the data, not coded
   per step. (R: "if we can make it dynamic.. then we need not wire it concretely")
8. **Distinct audio per action.** Each action has its own cue. (R: "certain sounds for certain actions… more
   diff clips") — LANDED: `navJive`(Back/Next) · `helpJive`(? open) · `showmeJive`(ShowMe go) · `toggleJive`
   (legend) · `tabJive`(dossier tab) + enriched bar/dismiss/dossier motifs. §-witness: no pageerror, jives present.
9. **A `?` per tagged ReadMe answer (the `_TRL` analogy).** Help content is a KEYED store — keyed to an element
   the way iDempiere `_Trl`/`AD_Element.Help` keys translation/help to a record. Wherever a ReadMe tag exists
   for an on-screen element, a `?` assistant appears (when NeedHelp? is checked). (R: "like how _TRL is in
   iDempiere"; "each time there is a tag answer in the ReadMe, it can have tool tip assistant ?")
10. **Devs write ReadMe only; the HTML has hooks that correlate.** Correlation is by KEY, not hand-wiring:
   on-screen elements already carry a stable key (every bubble has its table id; a dossier tab has its name),
   and the ReadMe store is keyed by exactly those. A dev adds/edits a help entry; the `?`, the card, and the
   ShowMe step appear automatically — no per-feature tour code. (R: "devs just write such ReadMe and the HTML
   has hooks to corelate")
11. **ShowMe is type-aware — it goes to the element and performs the matching affordance.** The driver
   dispatches by element KIND, not one fixed action: text field → focus + highlight; list/dropdown → open it;
   tab → switch to it; bubble → trace/focus it; button → pulse it. The keyed entry may hint `kind`/`tab`, else
   the driver infers from the resolved DOM element. This is what makes the SAME keyed-help mechanism work on
   `erp.html` AD fields/lists/tabs later, not only glassbowl bubbles. (R: "wonder if ShowMe goes to the field,
   or if text field, highlighted, if list, list is open")

## The dynamic model — how it is generated, not wired (req 7, the keystone)
Two ordered sequences are ZIPPED at runtime; their order is the single source of truth:
- **Data spine (already present):** `walkBundle(GDB, seed)` returns the ordered O2C chain
  `[c_order, m_inout, c_invoice, c_payment, c_allocationline]` (witnessed: `§TOUR walk … agree=Y`,
  docnos `[80001,$100.70,$98.50]`, 0 invented). The chain order = the step order. A new hop in the data ⇒ a
  new step, automatically.
- **Doc spine (ReadMe):** an ordered list of step-units per operation, each `{ opKey, stepKey, title, paraHTML,
  readmeAnchor }`. Parsed from a STRUCTURED help source (see "ReadMe source" below) — NOT inlined per step.
- **Correlation by KEY (req 10), not by hand-wiring.** Each on-screen element carries a stable key it already
  has (a bubble = its table id `c_invoice`; a dossier tab = its name `Data`). The ReadMe store is keyed by those
  same keys (`help_ops.json["c_invoice"] = {title, paraHTML, readmeAnchor, op, ordinal}`). The `?` for an element
  is "does a ReadMe key exist for it." The ShowMe `drive()` is DERIVED from the key: `setTrace(true);
  setFocus(key); openDossierTab(key, entry.tab?)`. No per-step code; add a keyed entry ⇒ a new `?` + step.
- **Ordering for Next/Back** comes from the data spine (the lineage hop order) intersected with the keys that
  have ReadMe entries — so "Next" walks the real op sequence and its paragraph in lockstep (req 5/6); the terse
  prompt is derived (`"Show the {friendly} — #{documentno}"`), the detail is the keyed paragraph.
- **Generalises beyond O2C:** other operations (P2P, GL) supply their own seed + `LIN.steps`-style declarative
  walk + their doc step-units; the same zip builds their tour. Nothing O2C-specific is hard-coded.

## Does this make ops & maintenance easier? (the user's question — assessment)
Yes, materially, for five concrete reasons — with the honest caveats:
1. **One keyed source, no code↔doc drift.** Help text, the `?` assistants, the guided ShowMe, and (the `_TRL`
   parallel) future translations all read ONE keyed store. The tour cannot silently rot: ShowMe replays the
   REAL instance, so a wrong paragraph is *visibly* wrong next to the live data.
2. **Add-a-feature = add-a-row, not edit-the-tour.** A new document/bubble gets a `?`, a card, and a tour step
   the moment a dev writes its keyed ReadMe entry — because the HTML hook (the element's id) already exists.
   Zero tour code touched. New verticals (P2P, GL) ship their keyed entries + a declarative walk; same engine.
3. **Doc-writers maintain it, not engineers.** The skill is "write a help paragraph keyed to `c_invoice`," not
   "code a step driver." Lowers who can keep it current — analysts/writers, the people who know the ops.
4. **iDempiere-familiar mental model.** It is the `_Trl`/`AD_Element.Help` + "show help" toggle pattern ERP
   maintainers already run. Nothing new to learn operationally.
5. **Drift is a GATE, not a surprise.** The `§READSHOWME zip=aligned` witness fails CI if a hook has no keyed
   entry (or an entry points at a missing element) — so the build *catches* stale help instead of shipping it.

Honest caveats (and the mitigation):
- Keys must stay in sync with element ids → the alignment witness enforces it mechanically (req-7 witness).
- Prefer a STRUCTURED keyed store (`help_ops.json`, Option A) over scraping live mkdocs HTML (Option B):
  parsing rendered docs is brittle to doc edits; a keyed store is the maintainable choice and keeps ShowMe terse.
- ShowMe can only demonstrate what the page can already drive (`setTrace/setFocus/openDossierTab`); a step that
  needs an action the page can't do yet is a real code task, not a ReadMe row. That boundary is the honest line.

## ReadMe source (req 4) — DECIDED: Option A, structured `help_ops.json` (user, 2026-06-01)
A keyed JSON store: `key (element id) → {op, ordinal, target, title, paraHTML, readmeAnchor, tab?, shots?}`.
Devs edit ONE file; the page hooks read it by key (every bubble already = its table id). The TOUR reads it;
never inlined per step. (Rejected Option B = scraping rendered mkdocs — brittle to doc edits, ShowMe can't
stay terse.)

**ReadMe content grows with screenshots (req: "later I put screenshots").** `paraHTML` is rich HTML — detail
now, images later, no code change. The generator pre-seeds **figure placeholders that ARE authoring advice**:
a token like `[[FIG 1.1: screenshot of the Invoice Data tab]]` renders as a dashed placeholder box reading
"Figure 1.1 — put screenshot of the Invoice Data tab" so the author knows exactly what to capture; dropping a
real image (set `shots:[{fig:"1.1", src:"…png"}]` or inline `<img>`) replaces the placeholder. Until then the
help still works (text + ShowMe); the figure box is a to-do the author can see. (R: "u put Figure 1.1 [Put
screenshot of ..] <-- to advice me")

## Generalization — overlays as the unit of separation (CRUD too)
The keyed-hook overlay is not Help-specific; it is a clean way to attach ANY capability to ANY element bubble
without wiring it into the page. The **CRUD "ring of fire"** (New/Edit/Delete — today the greyed T3 teaser in
the blurb) becomes its OWN overlay on the same mechanism: keyed by element id, dispatched by element kind,
deployable to any bubble. Benefits the user named: good separation of concerns, easy maintenance, deploy-to-any
-element. So the page grows a small set of peer overlays — Help/ReadMe-ShowMe, CRUD ring, (later) translations —
each reading its own keyed store, none entangled with the renderer. (R: "the ring of fire CRUD can also be an
overlay so there is good separation, easy maintain deploy to any element bubble"). CRUD stays T3-parked
(read-only first); this only fixes its ARCHITECTURE as an overlay when it lands.

## Surfaces to reuse (no new renderer; all present in glassbowl.html)
`setTrace`/`applyChain`/`walkBundle`(via GDB)/`setFocus`/`openDossier`/`openDossierTab` (window-exposed);
projection globals `N/idx/project/px/py/k/radius` for anchored tooltips (verified top-level); the dossier
`Data|Rules|Columns|History` tabs are the "open tab" ShowMe shows in (req 5). Audio: `showmeJive`/`navJive`/
`helpJive`. ReadMe "Read more" → live mkdocs anchor (the §13 grep method, verified at C3).

## Witnesses (headless §-log first; Playwright wiring only)
- `§HELP mode=on badges=K` (help-mode toggles, K helpable bubbles).
- `§READSHOWME ops=[o2c,…] o2c.steps=N docSteps=N dataHops=N zip=aligned` (req 7: counts MUST match; a
  mismatch = the spines drifted).
- `§READSHOWME step=i op=o2c para=<anchor> drive=focus:<table> docno=<real>` per step (req 5/6).
- `§SHOWME op=o2c drove=[setTrace,setFocus:c_invoice,tab:Data] invented=0` (req 2/5; terse drive derived).
- Reuse the landed `§TOUR walk … agree=Y` as the data-spine proof; `deploy/dev/tests/test_tour.js` extends.

## Already landed this session (do not redo)
- `§VIEWLOG` orbit-debounce in `glassbowl.html` (record on REST, 350ms trailing; discrete actions immediate).
  Witnessed: 20-move orbit drag ⇒ delta=1 entry (was ~20).
- Audio palette (req 8) in `glassbowl.html`: motif helper + 5 new cues + enriched bar/dismiss/dossier; wired
  `toggleJive`(legend) + `tabJive`(dossier tab). Boots clean (pageerror=0).
- Data-spine proof (`test_tour.js`, `build/erp/tour_witness.log`): O2C chain walks to real docnos, agree=Y.

## Build order (each names its witness; nothing deploys without EXPLICIT GO)
- **D1 — ReadMe source.** Author `help_ops.json` (O2C first) from the docs; witness `§READSHOWME ops/steps`.
- **D2 — Dynamic generator.** Zip doc-steps ⟂ data-hops into runtime STEPS; witness `zip=aligned`.
- **D3 — ? help mode + ReadMe card + ShowMe.** In `glassbowl.html`: `?` pill → badges → card (paraHTML +
  Read-more + ▶ ShowMe) → derived drive in the open tab; audio wired. Witness `§HELP`/`§SHOWME`.
- **D4 — Playwright wiring + visual.** pill→badge→card→ShowMe drives the trace/tab; serve + eyeball.
- **D5 — Deploy (Glassbowl-way, EXPLICIT GO):** copy `build/erp/{glassbowl.html,glassbowl_data.db,sqljs/}`
  → `docs/`, commit `full`, `mkdocs gh-deploy --force`. Retire `TourERP.html` (folded into glassbowl.html).

---

## Next session — initial help-panel tweaks (2026-06-01, SPEC handoff — no code yet)

Carried verbatim-intent from the design conversation; **defer implementation to a fresh
session** (user: refine "as I see how to tweak them"). Scope = `build/erp/help_overlay.js`
(→ `docs/help_overlay.js`) on `glassbowl.html`, **plus** the standalone help readme
(`READSHOWME_DYNAMIC_SPEC.html`) — *make all three consistent on every item below.*

- **T1 — Show corresponding numbering.** The initial popup help panel must display the
  step's number matching the doc/section numbering (the `§`-tag), so panel, ReadMe
  paragraph, and timeline tag all read the *same* index. (Extends req 4/5 — the doc/data
  spines already zip; surface the index in the panel chrome.)
- **T2 — Draggable.** The popup help panel must be draggable. Reuse the landed
  draggable-panel pattern (capture-after-move, fold + no-stick — see the Settings-panel fix
  in MEMORY), not a fresh handler.
- **T3 — Icon-only controls.** `Read | Show | Next` become **icons only, no text** — as
  minimal as possible (Read = detail glyph, Show = ▶/eye, Next = →/⏭). Keep accessible
  labels (`aria-label`/`title`), drop visible text.
- **T4 — Timeline-tag semantics.** Pressing **Next *triggers*** the timeline tag — it
  advances the step **and** records it (op-log / `§VIEWLOG`). **ShowMe *repeats*** the
  current step's drive and **does NOT** trigger the timeline tag (it is a replay, not an
  advance). This is the one behavioural rule to get right.
- **T5 — README consistency.** Apply the same numbering + icon scheme to
  `READSHOWME_DYNAMIC_SPEC.html` so the on-page help and the standalone readme match.

**Witnesses (next session):** `§HELP panel drag=ok num=<§tag>` · `§READSHOWME next→timeline tag+1`
· `§SHOWME repeat timeline=unchanged`. Deploy only after EXPLICIT GO, Glassbowl-way (§D5).

---

## ShowMe as coach — one verb, gated by docstatus + edit-mode (design, 2026-06-01)

Carried verbatim-intent from the design conversation. **SPEC ONLY — no code.** This settles what
ShowMe *is* when the page grows a Process/DocAction capability (the missing iDempiere verb: the
state machine that transitions `DR → CO`, distinct from CRUD which only writes a row). It does not
implement Process; it fixes the guide's contract so it stays cheap and cannot drift from truth.

### The question that started it (R, verbatim)
> "when ShowMe, do we really mock an execution or indicate it is teared down at the end?"
> "ShowMe on an existing doc vs a New in progress where the user needs guidance… intent is to do
>  it anyway, then ShowMe does it and the status shows processing what lines are created etc as per
>  iDempiere's logic… seamless… reuse as a constant coach."

### Resolution — ShowMe is ONE verb, never a mock
ShowMe is a single behaviour — **"advance this document's lifecycle, and point at the work as it
happens"** — and the only thing that varies is the doc's current `docstatus` (the `{DR,IP,CO,CL}`
field already sitting in `crud_ops.json __meta`):
- **Existing/historical instance (e.g. #80001, already `CO`):** no legal next action. "Advance"
  degenerates to *reveal the button that already did this + point at the result already in the
  data*. It reads, because the fold already happened. **Nothing is mocked** (a mock `CO` would be
  invented data — forbidden by the prime directive) and **nothing is torn down** (nothing was
  mutated). There is simply nothing to execute.
- **New / in-progress draft (`DR`/`IP`), user intends to advance:** ShowMe pulses the real Process
  button; the user's gesture fires it; the **status bar updates as a consequence of the real
  Process layer running** — docstatus flips, derived lines appear — and ShowMe points: "note the
  status." Reversal (if wanted) = a **real signed kernel reverse op** (W-CHAIN), never a UI reset.

So: **no mock, ever; teardown only ever means a real kernel reverse op.** The historical path reads;
the live path does-for-real-and-points. Same verb, gated by docstatus.

### The cost dissolves — the guide does NOT own Process logic (R)
> "the status bar is consequence of the process, with or without a guide. The guide just show the
>  steps, user must infer. Guide… says 'Note status', highlights the status bar, and Next means
>  user acknowledges."

The status bar — docstatus, lines created, posting — is produced by the **Process/DocAction layer**,
which must exist *regardless of the tour*. The guide never produces those lines and never needs to
know how they are derived. Therefore the guide carries **zero** DocAction cost. The earlier worry
("ShowMe must replicate iDempiere posting") was a conflation: that correctness belongs to the
Process layer and is verified by its **own oracle — the derived lines must match #80001's real
lines** — not by the guide.

### Guide vocabulary — the whole contract (domain-free, key-addressed)
The guide dispatches by element KIND (extends req 11) and emits **key-addressed intents** only
(governance invariant: neither overlay imports the other). Its entire vocabulary:
- `reveal <key>` — bring the element into view / focus it
- `pulse <key>` — pulse a button (e.g. the Process/DocAction button) — **reveal only, never auto-fire**
- `highlight <key> + note` — point at a consequence the page already shows; **new kind: `statusbar`**
- `await` — Next = the user acknowledges; advance to the next step (and trigger the timeline tag, §T4)

No posting logic, no state machine, no branching on outcome. A `pulse` may surface a *verb* (Process)
but the user's gesture, not the guide, fires it (Edit-mode gate below).

### Invariant — the guide asserts NOTHING
The guide highlights **whatever the live status bar actually says** and lets the user infer. If
Process completed, the bar reads `CO` and the user reads success; if it errored, the bar says so and
"note the status" still points at the truth. The guide never claims "it worked." This is the prime
directive in the UX: **the guide narrates, the data speaks, nothing is invented.**

### Edit-mode gate (the "constant coach")
Advancing a live draft = real kernel writes, so it is only available with **Edit-mode ON** (off by
default). With Edit-mode OFF the *same* guide code degrades gracefully to reveal-and-explain on
whatever is lit. One codebase, gated by the mode that already exists. This is what makes the tour
and a persistent working coach the same thing (R: "reuse as a constant coach").

### Error path — borrow the SINGLE ErrorReport class, do not re-implement (R)
> "guide has simplest of script or role to go thru. If anything outside it, ie process hitting own
>  error, guide uses canned response 'ErrorReport raised. Submit.' borrowing from BIM-OOTB
>  ErrorReport." … "since we sitting on the same codebase, we should borrow and call it from a
>  single class."

The guide's script is strictly linear. The moment reality diverges (Process raises its own error),
the guide does **not** branch into error handling — it halts and shows one canned step:
**"Process raised an error — ErrorReport ready. Submit."** pointing at the toast.

- **The mechanism already exists and is global:** `bim-ootb/viewer/error_reporter.js`
  (`setupErrorReporter(A)`) installs an uncaught-error catcher that raises a bottom toast with a
  **Report** button routing to `A.reportBug()` / `A._doReportBug` (GitHub/Email). On a Process throw
  the toast fires on its own; the guide's canned line merely *acknowledges and points at it*, then
  the guide is done (it does not pretend to continue).
- **SINGLE CLASS, borrowed and called — not copied (the new directive).** Glassbowl/bim-compiler and
  bim-ootb sit on one workspace, so the fallback **calls the one `error_reporter.js`/`reportBug`
  class**, never a divergent fork — same discipline as single-DB and the one `whitebox_regression.js`.
  HONEST BOUNDARY: glassbowl deploys to `docs/` via `mkdocs gh-deploy` separately from
  `bim-ootb/viewer/`; "single class" means **one source file is the canonical ErrorReport, deployed
  to both surfaces — not two copies that drift.** Factoring `error_reporter.js` into that shared,
  callable form is a precondition of this fallback (a real code task, flagged here, not assumed).

### The Guide is a surface-agnostic overlay — one engine, per-surface script (R, 2026-06-01)
R: "all along, the Help Guide is a separate overlay, reads and shows" · "once matured, reuse it for
the BIM side" · "with different sequence script of course." This is the through-line of the whole
design and the reason every prior decision points the same way:
- **Separate overlay, reads + shows, owns no logic.** The guide never holds Process/DocAction/render
  logic; it `reveal/pulse/highlight/await`s over key-addressed elements and asserts nothing. Because
  it carries no domain logic, it is **not coupled to any one surface**.
- **One engine, parameterised by a sequence script.** What differs per surface is ONLY the keyed
  store + ordered walk (the `help_ops.json`-style script). The engine, the four-verb vocabulary, the
  assert-nothing invariant, Next-gated-on-success, the veer/IP/error handling — all identical. This
  is req 7 (generated, not wired) and req 11 (type-aware by element KIND) generalised one step past
  ERP: the SAME mechanism that drives glassbowl bubbles and `erp.html` AD fields drives BIM elements.
- **ERP today → BIM later.** Maturity path: prove the overlay on the ERP/glassbowl surface first;
  then reuse it on the BIM viewer with **a different sequence script** keyed to BIM's existing
  element ids (storey / discipline / IFC entity / Find / panel — they already carry stable keys),
  walking a BIM operation instead of O2C. No engine change — a new keyed store + a new walk.
- **Third consumer foreseen — Gravity (R, 2026-06-01):** R: "I foresee Gravity will use the same
  overlay of both Guide and CRUDP." The gravity view (`glassbowl_gravity.html`) becomes a THIRD
  consumer of the same keyed-hook overlay engine — Guide (read+show) and CRUDP (CRUD+Process) are
  peers, and Gravity attaches the *same* overlays to its gravity-ranked bubbles BY KEY (a gravity
  cell already carries its table id). No engine change — Gravity supplies its own seed/walk; the
  overlays it loads (Help and/or CRUDP) react to its keys exactly as on glassbowl. Confirms the
  overlay layer is the unit of separation across all three surfaces. (Not built in the O2C cut;
  recorded as the reuse target.)
- **HONEST BOUNDARY (unchanged from §caveats):** the guide can only demonstrate what a surface
  already exposes as drivable (glassbowl: `setTrace/setFocus/openDossierTab`; BIM: its equivalent
  window-exposed `flyTo/isolate/setStoreyVisible`-style surfaces). BIM reuse therefore needs (a) a
  BIM keyed store, (b) a BIM declarative walk, (c) BIM's drive-surfaces window-exposed — same three
  ingredients glassbowl already has, nothing engine-level. A step needing an action the BIM page
  cannot yet drive is a real code task, not a script row.

### Scope NOW — O2C script only; ProcessBatch is the named next extension (R, 2026-06-01)
DECIDED. The first cut ships **exactly one sequence script: O2C** (the already-witnessed
`[c_order, m_inout, c_invoice, c_payment, c_allocationline]` chain). No P2P, no GL, no BIM script in
the first cut — they are proven *reachable* by the surface-agnostic model above, but **not built
yet**. Do one operation completely before breadth (avoids the partial-everything trap).
- **Next extension = ProcessBatch.** Same engine, a different sequence script that walks a batch
  operation (process many docs at once) rather than the single O2C chain. It is an *added keyed
  store + walk*, not an engine change — the maturity path already established.
- R's framing: **"if done well this is already a leap in all of ERP experience."** The bar for the
  O2C cut is therefore *done well* (the witnesses below all green, asserts=0, drift-gate aligned),
  not *done broad*. Breadth (P2P/GL/ProcessBatch/BIM) follows only after the O2C cut proves the model.

### Fork status after this conversation
- **#1 Process = Real DocAction layer** — stays its OWN track, *not* dragged in by the coach (the
  guide is blind to it). Its acceptance witness: **derived lines on a re-built #80001 match the
  traced #80001 lines** (built-in oracle, prime-directive-clean).
- **#2 Key-addressed intent** — confirmed; now carries one more kind (`statusbar` → highlight) and
  may carry a *verb* (pulse-Process), still user-gestured, never auto-fired.
- **#3 Monochrome alignment** — unchanged (CRUD ring re-aligns to the locked Help card chrome).

### Two off-script responses — error vs veer (the guide stays dumb either way)
The guide's script is strictly linear; it never branches into recovery logic. It has exactly two
ways to leave the path, by KIND of divergence:
- **Error (something broke):** Process threw → hand to the single ErrorReport class; canned
  "ErrorReport raised. Submit."; the guide is done (§ error path above).
- **Veer (user chose elsewhere):** DECIDED 2026-06-01 — **suspend, do NOT kill Help.** The guide
  yields; it has no standing to force a return (it asserts nothing). Specifically:
  - detect: an action whose key ∉ {current step, its legal Next} on the same key-addressed bus;
  - `driver.suspend()` — stop asserting order; **NeedHelp? stays ON** (badges live — a veer is
    often exactly when help is wanted; killing badges at peak confusion is hostile, and an
    accidental mis-click must not nuke the session);
  - if the veered-to element has a ReadMe key, surface *its* card (meet them where they landed);
  - preserve the current step number (§T1) + a quiet Resume affordance;
  - a veer is **not** a Next → it triggers **NO timeline tag / §VIEWLOG** (consistent with §T4);
  - disengagement is the **user's** gesture only (uncheck NeedHelp? / ✕ the card) — never auto-off.
  (Rejected: auto-off both driver + checkbox — loses ambient help at peak need, mis-click ends the
  whole session. The user owns the single off-switch; the tour pauses, it does not die.)

### Next is gated on SUCCESS — three post-Process outcomes (R, 2026-06-01)
DECIDED. After a Process gesture in **New-draft mode**, the guide does NOT advance on the click — it
advances only when the **live status reaches the step's expected success status**. Three outcomes,
all read the same dumb way (point at the status bar, assert nothing), but distinct in meaning:
- **`CO` (reached target) = success** → Next is allowed to proceed (and triggers the timeline tag, §T4).
- **`IP` (In Progress, unsatisfied condition) = legitimate business non-completion** → the guide
  **re-highlights the status it encountered** — it "did not obtain a success to proceed next" — and
  **holds**: Next does NOT proceed, **no timeline tag** (not an advance). The user must satisfy the
  missing condition and retry. This is NOT an error (no exception) and NOT a veer (no off-path
  action) — it is the document's own state machine reporting it cannot complete yet. `IP` is already
  in the `docstatus` enum `{DR,IP,CO,CL}` (`crud_ops.json __meta`).
- **exception thrown = something broke** → canned "ErrorReport raised. Submit." (§ error path).
The guide never needs to know *why* the condition was unsatisfied — that is Process/iDempiere logic.
It only compares live-status vs expected-success and either proceeds or re-highlights. Same
`highlight statusbar` + assert-nothing contract, doing double duty. (On a completed `CO` doc there is
no gesture and no gating — see retrospective-trace below.)

### Completed doc — ShowMe is a retrospective trace (R, partial — tail of sentence pending)
On an already-`CO` instance (e.g. #80001) ShowMe **merely traces through, showing how it has gone** —
a read-only walk of the already-folded path, step by step; nothing advances because it is done.
(R's sentence trailed off — "…how it has gone and ___"; the tail is NOT captured here, awaiting the
rest before specifying any further completed-doc behaviour. Do NOT invent it.)

### An abandoned new-doc rests in DRAFT — the final answer to "teared down?"
DECIDED 2026-06-01 (R: "unfinished new doc can be in draft mode, no issue"). If the user veers away
mid-build of a new document, that doc simply **stays `DR`** — a legal resting state (the default in
`crud_ops.json __meta`; every entry starts `DR`). No cleanup, no forced completion, **no teardown**:
the half-built doc is a real signed draft at rest, and the user returns to exactly it. This closes the
original question end to end: **no mock, no teardown** — an incomplete build is just a draft.

### Class placement — ERP module folder beside BIM, single canonical source (R, 2026-06-01)
R: "put the classes in ERP own folder next to BIM" + "borrow and call from a single class, same
codebase." Resolution, honouring the two-deploy-target reality:
- The guide overlay, CRUD ring overlay, Process/DocAction layer, and the shared ErrorReport callable
  are ERP-concern peers (the "overlays as the unit of separation" model) → they live in **one ERP
  module folder beside the BIM rendering files**, not scattered into `deploy/dev` or inlined per page.
- **Canonical home = `bim-ootb/viewer/erp/`** (ERP runtime already lives ONLY in `bim-ootb/viewer/`).
  Glassbowl (`bim-compiler/build/erp/` → `docs/` via mkdocs) **deploy-copies the shared classes from
  there** — exactly as it already copies `sqljs/` + `glassbowl_data.db`. ONE source, copied to each
  surface; no divergent fork (same discipline as single-DB, one `whitebox_regression.js`).
- **Precondition (real code task, flagged not assumed):** `error_reporter.js` currently binds the `A`
  app object (`A.reportBug`/`A._doReportBug`). To be callable from both surfaces it must first be
  factored into a self-contained class in `bim-ootb/viewer/erp/`. That factor-out is the natural
  FIRST bounded task when GO is given (both the coach fallback and glassbowl-proper then share it).

### Witnesses (when built; §-log first, EXPLICIT GO before any deploy)
- `§SHOWME verb=advance docstatus=<DR|CO> gate=<edit-on|read> drove=[reveal,pulse,highlight:statusbar] invented=0`
- `§SHOWME asserts=0` — guide emits no outcome claim; only highlights the live status bar value.
- `§PROCESS oracle docno=80001 derivedLines==tracedLines` — Process layer's own oracle (track #1).
- `§SHOWME error→ErrorReport canned="…Submit." class=single source=error_reporter.js fork=0` —
  fallback calls the one class, no copy.
- `§SHOWME veer→suspend needhelp=on tag=unchanged resume=ok` — veer pauses, badges stay, no timeline tag.
- `§SHOWME abandon→draft docstatus=DR teardown=0` — abandoned build rests as a real draft, nothing unwound.

---

## §P4 addendum — CONTEXT-GATED ShowMe on the iDempiere surface (LIVE, bim-ootb sw v610, PR #214, 2026-06-09)

Spec for `prompts/FRONT_DOOR_PILL_FINISH.md §P4-1/§P4-2`. The shared overlay (`help_overlay.js`) is unchanged in
spirit (still data-generated, opt-in, drift-proof) but gains ONE additive host extension so a host can vary the
ShowMe CONTENT by app stage **without forking the module** — same pattern as the existing `init({host,nav})` lift.

**The bug it fixes.** On `idempiere.html` the ShowMe pill "gave nothing" at the front door: the default tour
(`help_ops.json`, the O2C steps `o2c/c_order/…`) targets IN-CLIENT AD-window keys that don't exist pre-login, so
`buildBadges()` made 0 badges AND the overview card (z-71) opened BEHIND the `#idmp-login` z-120 overlay. A test
that passed (toggle+lit) while the feature showed nothing is not a test — see the strengthened witness below.

**The extension — `window.__help.setOps(store)`** (additive; callers that never invoke it are diff=0):
- `store` = a keyed `help_ops`-shaped object → the overlay builds steps/badges from it on the next `enable()`.
- `store = null|undefined` → RESTORE the default (fetch `help_ops.json`).
- If ShowMe is currently ON, `setOps` rebuilds live (`disable()`+`enable()`).
- §-log: `§HELP setOps store=custom/<n> | default`.

**Host contract additions (idempiere, in `idempiere.html` — NOT in the shared module):**
1. **Stage gate.** `_driveShowMeOps()` sets `setOps(pre-client ? ONBOARD_HELP_OPS : null)`. It is called BEFORE
   `enable()` (in the ShowMe toggle) AND on every `_syncPillStage()` (so content switches if ShowMe is open across
   a login/logout). DECISION: ShowMe is the **login GUIDE** at the front door (it does NOT auto-fire).
2. **Onboarding store.** `ONBOARD_HELP_OPS` = overview steps (target=null → no badge needed, no AD nav attempted):
   Welcome · Sign in · Bring data in (the ⋯ Install/Migrate pills) · Read/Compare. Pure narration of the real
   front door (non-invent).
3. **Above-overlay z-index.** Where a host shows a full-screen login/modal overlay, it MUST raise `#helpCard`
   (and `.help-q`) above it (idempiere: `z-index:130/129` in page CSS) or the card opens behind the overlay.
   In-client restores the default AD tour (`help_ops.json`) unchanged.

**Witnesses (LIVE — `erp/tests/poc_pill_mobile.js`):**
- `§P4-1-ONBOARD {stage:pre-client, open:true, onScreen:true, onTop:true, title:"Welcome…", steps:4}` — the card
  is OPEN, on-screen, and ON TOP (`elementFromPoint` hits it, above the login). This is the test that passes ONLY
  if the feature is actually VISIBLE (it asserts a visible outcome, not just toggle+lit).
- `§P4-2-INCLIENT {n:6, hasO2c:true, hasOrder:true}` — `setOps(null)` restores the default O2C tour (gate is
  bidirectional).
- `§HELP setOps store=custom/4` (pre-client) / `store=default` (in-client).
