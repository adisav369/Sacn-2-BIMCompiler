# ⚠ DO NOT REMOVE — Scope guard
# Scope: the GOVERNING spec for all browser-UI design in this project. Every UI concern (Help, CRUD,
#        Validation, Access, i18n, …) is a SEPARATED keyed overlay layer over tagged elements — the
#        Application Dictionary model, in the browser. Per-concern specs (READSHOWME, CRUD_OVERLAY, …)
#        are INSTANCES that conform to this. Spec-first; witness-led; §-log first; EXPLICIT GO to deploy.
# Status: POC / demo (nothing wired to production). This doc governs DESIGN, not a deployment.

---

# UI Overlay Governance — the Application Dictionary, in the browser

## Purpose
This is the doc that governs the UI design specs. It fixes ONE architecture so every new UI capability is a
keyed overlay layer, not new wiring. The per-concern specs conform to it:
- Help / ReadMe-ShowMe → `prompts/READSHOWME_DYNAMIC_SPEC.md` (published: docs/ReadMeShowMe.md) — LANDED (D3).
- CRUD ring-of-fire → `prompts/CRUD_OVERLAY.md` — SPEC.
- Validation, Types/ReadOnly/Defaults, Access, i18n, Analytics, Theming → layers catalogued below.

## What this is (prior art — it is not new)
The pattern is the convergence of well-named ideas; we name them so we exploit them deliberately:
- **Separation of the three layers** (structure / presentation / behavior) → *unobtrusive JavaScript*,
  *progressive enhancement*: behavior is a layer OVER the HTML, not baked in.
- **Behavior bound to the DOM by data-hooks** → *Stimulus.js* (Hotwire/Basecamp): `data-*` controllers.
- **Cross-cutting concerns externalized** → *Aspect-Oriented Programming*: Help/CRUD/Validation are aspects.
- **A visual layer anchored to elements** → *adorner layer* (WPF), *annotation layer* (Hypothes.is),
  *anchored positioning* (CSS Anchor Positioning + Popover API; Floating UI / Popper.js).
- **Guidance/automation overlaid on an app without editing it** → *coach marks* / *Digital Adoption Platforms*
  (WalkMe, Pendo, Appcues, Whatfix, Userpilot); libs Shepherd.js, Intro.js, Driver.js.
- **Per-element keyed metadata** → i18n *resource bundles / message catalogs* (gettext, Java ResourceBundle),
  and most precisely **iDempiere's Application Dictionary**: `_Trl`, `AD_Element.Help`, `AD_Column`, `AD_Val_Rule`.
We are rebuilding the AD's "one element, many externalized concerns" model in the browser, as overlay layers.

## The three invariants every UI layer obeys
1. **Tagged element = the hook.** Every governed on-screen element carries a STABLE key it already owns (a
   glassbowl bubble = its table id `c_invoice`; later an AD field = its column id). The key is the only contract
   between the renderer and the layers. No layer edits the renderer.
2. **Each concern is a keyed store + an overlay.** A concern (Help, CRUD, …) is a JSON store keyed by element id
   plus a standalone overlay module that reads it by key and attaches behavior. Stores are the single source;
   devs edit the store, never wire per feature. Overlays are independent and removable — deleting one leaves the
   page and the other layers intact.
3. **The tagged element exposes its OWN rules to each layer.** Per element, per layer, a metadata block the
   element "possesses" — its types, readonly, defaults, validation, help text, access. The layer renders/enforces
   from that block. This is the AD_Column/AD_Field/AD_Val_Rule/_Trl model, made keyed JSON.

## Lane separation & the host contract (why N sessions run CONCURRENTLY without collision)
The keyed-overlay model is also a **work-separation** model: it lets independent sessions build in parallel,
coupled ONLY by a thin contract — not by touching each other's files. Three lane kinds:

| Lane | Owns | Touches |
|---|---|---|
| **Backend (engine)** | op-log, verbs, fold, access, the `read`/`dispatch`/`readPostings` seam | `scripts/`, kernel |
| **Frontend (host/chrome)** | the renderer; **tags elements by key**, **exposes nav/projection globals**, provides mount points | `idempiere.html` / `glassbowl.html` |
| **Overlay aspects** (Help/Tour · CRUD · Report · Validation · …) | one keyed store + one standalone overlay each; read-only or op-log-only | `help_overlay.js`, `crud_overlay.js`, `report_overlay.js`, `*_ops.json` |

**The host contract — the ONLY thing an overlay needs from the frontend (agree it UP FRONT):**
1. **Key vocabulary** — the stable per-element keys (table/column/record ids) the host tags onto its DOM.
   Every overlay attaches by these. ONE tagging pass by the host serves ALL overlays.
2. **Exposed globals** — the host publishes the nav/projection fns overlays reuse (e.g. glassbowl's
   `setTrace/setFocus/openDossierTab` + projection accessors). An overlay calls these; it never reaches
   into the renderer's internals.
3. **Mount point** — the host gives each overlay a container; the overlay must accept it (`init({host})`),
   NOT hardcode `document.body`/glassbowl. This is the "lift the mount, don't fork" rule — the SAME
   overlay then rides glassbowl AND idempiere.

**Concurrency guarantee.** Because the contract is the only coupling, the host lane and each overlay-aspect
lane can be built by **separate, concurrent sessions**: the frontend tags + exposes globals; each overlay
session builds its store + module against those keys; backend fills data behind the `read` seam. They
integrate by key, not by editing one another. **Graceful degrade removes the data-block**: an overlay that
needs data the backend hasn't supplied renders the engine's `coverage` marker (`partial`+note / `absent`)
— it never blocks, never invents (§ the read seam / PLUGIN_ARCHITECTURE §13.7). The only up-front
coordination is pinning the **key vocabulary + the exposed-globals list**; after that the lanes are free.

## Element-kind dispatch (type-aware, every layer)
A layer's action is dispatched by the element's KIND, not one fixed behavior: bubble→its document/table;
text field→focus/inline-edit/highlight; list→open/pick; tab→switch; button→pulse; fk→resolve. The keyed entry
may declare `kind`, else the layer infers from the resolved DOM node. This is what lets the SAME store + overlay
work on glassbowl bubbles AND on `erp.html` AD fields/lists/tabs — the newbie's "where is it?" answered anywhere.

## The layer registry (the catalogue — each its own keyed store + overlay + witness)
| Layer | Keyed store | iDempiere AD analogue | Status |
|---|---|---|---|
| Help / ReadMe-ShowMe | `help_ops.json` | `AD_Element.Help`, coach-marks | LANDED (D3) |
| CRUD ring-of-fire | `crud_ops.json` | `AD_Table`/`AD_Window` actions | SPEC (CRUD_OVERLAY.md) |
| Validation | (in `crud_ops.json` `fields[].validation`) | `AD_Val_Rule`, "checks before saving" | SPEC |
| Types / ReadOnly / Defaults | (in `crud_ops.json` `fields[]`) | `AD_Column` (type, IsReadOnly, DefaultValue, Mandatory) | SPEC |
| Access / visibility | `access.json` (future) | `AD_Role`/`AD_Window_Access`/field display-logic | FUTURE |
| i18n / translation | `*_trl.json` (future) | `_Trl` tables | FUTURE |
| Analytics / telemetry | `track.json` (future) | (modern addition) | FUTURE |
| Theming | `theme.json` (future) | (modern addition) | FUTURE |
Validation/Types/ReadOnly/Defaults ride INSIDE the CRUD field metadata today (one store), but are conceptually
their own layers and may split out; the governance is the same either way.

## Governance rules (what conformance means)
- **One store per concern, keyed by element id; the store is the single source of truth.** A new feature is a
  new keyed entry (or a new keyed layer), never new renderer code.
- **Overlays are standalone modules** (like `help_overlay.js`): inject their own CSS, attach by key, reuse the
  page's exposed fns/projection globals, never edit the renderer. Independently removable.
- **Every layer is opt-in + dismissible where it adds chrome** (Help = NeedHelp?; CRUD = Edit-mode). Off = zero
  affordance, zero cost — the off-switch is the non-burden guarantee.
- **Drift is a gate.** Each layer ships a `§<LAYER> … aligned` witness: a tagged element with no keyed entry, or
  an entry pointing at a missing element, FAILS the build. Stale layers are caught, not shipped.
- **No silent shortcuts.** A layer never edits the DB/renderer directly. CRUD writes go through the kernel op-log
  (signed, reversible); if a layer needs a capability the engine lacks, that is an engine task, spec'd separately.
- **Truth-bound.** Where a layer demonstrates or renders data (ShowMe, CRUD form, figures), it replays the REAL
  instance — it cannot drift from truth. If it would, fix the data path, not the copy.

## Why this governs well (ops & maintenance)
- One keyed source per concern → no code↔content drift; doc-writers/analysts maintain stores, not engineers.
- Add-a-feature = add-a-row across ALL layers at once (a new bubble gets Help, CRUD, Validation the moment its
  keyed entries exist — the hook already exists).
- iDempiere-familiar: it is the AD a maintainer already runs (Help/_Trl/AD_Column/AD_Val_Rule), in the browser.
- New concerns compose: Help + CRUD + Validation + Access stack over one tag set, none entangled.

## Conformance checklist for any new UI design spec
1. Names its element keys (the hooks) and confirms they already exist on screen.
2. Defines its keyed store schema (what each element "possesses" for this concern).
3. Is a standalone overlay module (own CSS, attach-by-key, no renderer edit, removable).
4. Is opt-in/dismissible if it adds chrome; off by default if it mutates.
5. Dispatches by element kind (works on bubbles AND AD fields).
6. Ships a `§<LAYER> aligned` drift witness + a wiring witness; §-log first.
7. Routes any data mutation through the signed kernel op-log; never a direct write.
8. EXPLICIT GO before deploy; deploy the Glassbowl-way (copy the stores+modules into docs/).
