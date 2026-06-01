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
