# Glassbowl Phase 2 — Lifecycle Chain + Right-Click Dossier (SPEC)

> **Status:** SPEC ONLY — to be built in a NEW session. Read-only (no editing yet; editing needs T3,
> the engine in the browser, `push=live`, explicit go). Built inside `bim-compiler`, served from this
> repo (same URL: https://red1oon.github.io/BIMCompiler/glassbowl.html). **Touches nothing in
> `bim-ootb`/live.** Prior art: `docs/GLASSBOWL.md` (Phase 1, the read-only map, shipped + proven).
>
> **PRIME RULE (unchanged):** EXTRACT OR COMPILE ONLY. Every node, edge, row, rule, and chain is
> read from `ad_full.db` / `erp_rules.db` / `kernel_ops` — never invented. A value with no source is
> a FINDING to log, not a guess.

## Why Phase 2 (the product thesis being proven)
Phase 1 proved the engine renders *itself* from data. Phase 2 proves the thing legacy iDempiere
structurally **cannot** do: **everything about an entity — its data, rules, validation, workflow,
access, accounting, and a real record's full lifecycle — in ONE place, no tab-switching, no second
login.** In iDempiere a power user opens 5–7 windows (and sometimes two logins) to assemble what one
right-click will show here. The §0.18b duality made literal: **Gardenworld** = the instance layer
(this invoice #200001), **Glassbowl** = the type layer (the `C_Invoice` cell) — Phase 2 lets you
move between them on one canvas.

## Interaction model (the redundant fixed panel goes away)
- **Left-click a bubble → a small FLOATING CARD anchored to it** — glanceable: friendly name, what
  the system does here, what it connects to. Follows the bubble; dismisses on click-away. Replaces
  the fixed right panel so the eye stays on the graph.
- **Right-click a bubble → the DOSSIER** — a larger movable/resizable panel, lazy tabs, "everything
  about this entity." The no-more-tab-switching payoff.
- **"Trace a record" (on a document bubble) → the LIFECYCLE CHAIN** (Phase 2a, below).
- God mode now: gate nothing (show all data/rules/orgs). Roles come later — and the access data is
  itself a dossier tab, so "who could see this" is visible to the god-mode user.

---

## Phase 2a — the LIFECYCLE CHAIN view (BUILD THIS FIRST: highest wow / lowest effort)
Pick a real GardenWorld document and light its **whole life** across the map as one connected path —
`Order → Shipment → Invoice → Payment → Allocation` — with a step-strip naming the actual documents.
This reuses lineage we already proved this session (`poc_longtail.js`, SO 101), so its oracle exists.

### Witness — W-LIFECYCLE
**Clicking "trace" on a document reconstructs a real record's full chain from data alone.** For sales
order **101** the chain is exactly the five documents `poc_longtail` drove (§0.19): `C_Order #80001 →
M_InOut #101 → C_Invoice #200001 → C_Payment #100 → C_AllocationLine (98.5)`. Proven when the lineage
extractor emits that ordered chain and the viewer lights exactly those type-bubbles + the derivation/
settlement edges between them, **0 hand-authored hops**. `§LIFECYCLE record=C_Order#101 hops=5
chain=[...] missing=0`. If a hop must be hand-coded, the claim fails (lineage is not in the data).

### The lineage path (extracted, deterministic — the derivation FK walk)
Forward-follow the document derivation edges (the green spine already classified in Phase 1):
1. `C_Order` —`c_order_id`→ `M_InOut` (shipment/receipt for that order)
2. `C_Order` —`c_order_id`→ `C_Invoice`
3. `C_Invoice` ←`c_invoice_id`— `C_Payment` (the payment that settles it)
4. `C_Payment` —`c_payment_id`→ `C_AllocationLine` (the applied amount, partial-aware)

Source of truth = `ad_full.db` rows (the static GardenWorld oracle, §0.12) for *existing* records;
for records *created in the engine*, the same chain is the `kernel_ops` lineage GUIDs
(`input_guids`/`output_guid`, §0.6) — the two must agree (a cross-check worth a §-log).

### Rendering
- Dim the whole map; highlight only the chain's nodes + connecting edges; subtle order→pay animation.
- A **step-strip** along the bottom: `Order #80001 ▸ Shipment #101 ▸ Invoice #200001 (100.70) ▸
  Payment #100 (98.50) ▸ Allocated 98.50` — each step the real document with its number/amount.
- Clicking a step opens that document's dossier (Phase 2b/c).
- A record picker (start with order 101; later a search box over real documents).

### Data sourcing
Phase 1 inlines only graph *metadata*. The chain needs *real rows* → load them in-browser via
`sql.js` + a small data bundle (a subset of `ad_full`/`ad_seed.db`), the EXACT pattern the ERP UI
already uses. Graph metadata stays inlined; rows + rule bodies lazy-load from the `.db` on first use.

---

## Phase 2b — the FLOATING CARD (left-click)
Replace the fixed right panel. Anchored near the bubble, lightweight, business language (reuse the
`FRIENDLY`/`LABEL`/`VERB` maps already in the viewer). Content: name · "what the system does here" ·
top connections · a "trace this record / open dossier" affordance.

## Phase 2c — the DOSSIER (right-click): the 5–7 iDempiere windows, fused
Lazy tabs, each EXTRACTED from data; each replaces an iDempiere window/tab:

| Dossier tab | Source (data) | iDempiere window it replaces |
|---|---|---|
| **Data** (real rows) | `ad_full` rows for the table, filterable | the document window itself |
| **Rules / Validation** | `erp_rules` `Validation` (raw SQL + plain-English gloss) | Validation Rule |
| **Callouts** | `erp_rules`/`handler_backlog` `callout` bound to fields | Callout (model) |
| **Workflow** | manifest `wfmc` transitions (DR→CO→VO…) drawn | Document Type / Workflow |
| **Access** | `ACCESS` rules (`document_action_access`) per role | Role / Document Action Access |
| **Columns** | `ad_column` (type, mandatory, default, FK target) | Table & Column |
| **Accounting** | `AccountingRule` (GL postings produced) | Accounting tab |

## Phase 2d+ — the high-value extras (backlog, prioritized)
1. **★ Reverse dependencies** — "what points TO this" (incoming FKs). One query over the 9,423 FKs;
   iDempiere has no easy equivalent.
2. **★ Audit / time-machine** — who/what/when, replayable, from `kernel_ops` lineage (§0.6, §18.8).
3. **★ Live rule-impact preview** — edit FIFO→LIFO, see affected matches flip *on the map* (the
   diff-oracle in-browser). **Needs the write loop → T3-gated; the read-only "preview without commit"
   is the bridge.**
4. **Status pulse** — counts by `DocStatus` per doctype, as bubble heat.
5. **Data-dictionary search** — type "tax" → jump to table/field/rule.
6. **Available reports** — housed `AD_PrintFormat` ("the set ones", §0.11).
7. **Plain-language labels** — `AD_Element` so the data view reads in business terms.

## Phase 2-viz — the ORBIT (pseudo-3D depth-plane view)
**The product framing (the user's, 2026-05-30):** *"like BIM looking at each discipline, then moving it
around (trackball), arranging the view."* In a BIM model you orbit the building to read its disciplines
(arch / struct / MEP) in depth. The same gesture, brought to ERP: the three spines ARE the ERP's
disciplines — lift them onto separate **depth planes** and orbit to read them. **Arranging the view** is
the new concept; "fresh eye candy iDempiere users will welcome," at canvas speed.

### The depth planes (deterministic, by spine role — EXTRACT, not invent)
Each bubble gets a `z` from its existing classification (no new data): **spine plane** `z=0` (the doc
flow Order→…→Allocation), **settlement plane** `z=+D` floating above (matching/reconciliation), **reference
shell** `z=−D` behind & dim (products/partners). `§ORBIT planes=3 spine=N settlement=M reference=K`.

### Witness — W-ORBIT
1. **At rest (yaw=pitch=0) the bowl is pixel-identical to the flat 2D layout** — orthographic head-on
   collapses the planes onto each other, so the static circle-seed layout the user likes *persists*
   untouched (projected `sx==x, sy==y`). No regression to any existing wiring check.
2. **Depth is assigned from data** — `§ORBIT` logs 3 non-empty planes from `node.settlement`/`node.kind`,
   0 hand-placed.
3. **The trackball orbits the camera; bubbles stay static in 3D** — dragging the bottom sphere changes
   yaw/pitch; each bubble's `x,y,z` are unchanged (you move your eye, not the model); the planes shear
   apart and depth-cue (size/opacity) grows with orbit amount. Reset restores the at-rest view.
   Cheap & fast: same lightweight SVG redraw (no engine, no raster-canvas swap), `createElementNS`.

Pure read, no T3 — a viewing affordance, parallel to (not blocking) the 2b/2c read roadmap below.

### The RECENT-ITEMS accordion (the activity / RecentChanges log)
The right info panel is a **stack of collapsible bars**, not a single overwriting inspector. Each look-up
*flows in* as the next bar (slide-in animation); **minimise** rolls it to a title-only bar that **stays**
(so the user can return); **swipe / ✕** dismisses it. This keeps a running **sense of activity** — and
it's not foreign: **iDempiere already has "Recent Items"** (and per-record change history). This is that,
made spatial & glanceable. Each bar with engine activity shows `⟐ the engine has tracked N runs here, kept
in the op-log` — the **real op-count** (kernel op-log depth, §0.6), extracted not invented. The honest next
step: feed the stack from actual `kernel_ops` events → a true **RecentChanges log** (the audit/time-machine,
Phase 2d-2). For now it logs the user's own look-ups; the bar shape & op-count are the seam to real events.

## Build order (each its own session + witness)
1. **2a Lifecycle chain** (W-LIFECYCLE) — START HERE. + the `sql.js` data bundle (the enabling step). ✅ DONE.
1b. **2-viz** (W-ORBIT) ✅ DONE — orbit depth-planes + trackball, the recent-items accordion log, click-to-focus
    filter, collapsible/resizable panel, ⓘ appendix. All pure read, "fresh eye candy."
2. **2b Floating card** (replace fixed panel) — and the **per-bubble action array**: left-click=focus+log (done),
   right-click=dossier/edit, double-click=expand/trace. "An array of actions & views on one screen."
3. **2c Dossier tabs** (W-DOSSIER) — Data + Rules first, then Workflow/Access/Columns/Accounting.
4. **2d extras** — reverse-deps + audit first (both pure reads, both gasp-worthy).
5. **Editing / impact-preview** — LAST, T3-gated (engine in browser, `push=live`, explicit go).

## Non-goals for Phase 2
- **No editing / no writes** (God-mode *viewing* only; editing is T3-gated, a later phase).
- **No `bim-ootb` / no live viewer touch.** Stays in `bim-compiler`, same Pages URL.
- **No invented data.** Missing rows/rules/postings are logged as findings (e.g. GL `fact_acct=0`
  is dataless per §0.19 — the Accounting tab must say "not posted in this dataset", never fake it).

## Files (planned for the build session)
- A lineage extractor (extend `system_explorer.js` or a new `scripts/lineage.js`) emitting sample
  record chains + the `sql.js` data bundle; §LIFECYCLE witness in `build/erp/`.
- Viewer additions: floating card, right-click dossier, chain highlight + step-strip.
- `deploy/dev/tests/test_glassbowl.js` extended: trace lights exactly the 5-doc chain for order 101;
  dossier tabs populate from data; right-click wired.

---

## Phase 2bcd — BUILD SPEC (this session, 2026-05-30) — "surface the REAL data"

> **User direction (2026-05-30, mid-session):** *"the map only shows FKs — find a way to surface the
> underlying data. The real data can surface in the accordion bars on the right panel. Make it friendlier:
> double-touch a bubble opens a blurb to choose actions — think what an ERP user wants to see. History?
> The bottom shows the trace."* So Phase 2b/2c/2d are reframed into ONE data-surfacing story, built in
> dependency order. All three are **pure-read, additive** (HTML/overlay only — NO new SVG circles/lines,
> so the 38 existing wiring checks stay green), every row/rule/op **extracted** from `glassbowl_data.db` /
> `ad_full.db` / `erp_rules.db` / `kernel_ops` — 0 hand-authored. No T3, no writes.

### Positioning lens (user, 2026-05-30) — "eye candy to pull the crowd, make them talk"
The audience is **glance-driven (visual bypassers)**; the eye candy's job is to **stop the scroll and hand them
a repeatable line**, not to inform. So design for **shareable frames** — each interaction (orbit shear, trace
lighting the chain, view-back stack rising over an input) should be a clean **screenshot/GIF "wow" on its own**;
they're the demo reel, not just features. **The extract-only rule is the secret weapon, not a constraint:** eye
candy is forgettable, but eye candy that is *all real* (drawn from the system's own data, 0 hand-authored) is
sticky — the talkable claim is *"that's not a mockup; the thing renders & explains itself, live."* The bypasser
sees the candy → the one who stops finds it's true → that person makes others talk. **Spectacle on the surface,
substance underneath** — prioritize the visually-striking moments, but never fake one (a faked frame collapses
the trust that makes it spread). This is WHY the read-only/extract discipline and the eye candy are the same bet.

### NORTH STAR (user, 2026-05-30) — "make job as play" (Mark Twain)
Twain: *work and play are the same act under differing conditions.* Glassbowl's purpose is to make mundane
ERP work feel like **play** — sensory, delightful, talkable. The eye candy, the **ear candy** (Task 5 audio),
the swipe gestures, the orbit, the view-back optics are all one bet: turn obligation into something a body
*wants* to do. This is the lens above the positioning lens — eye candy pulls the crowd; "work as play" is WHY
they stay and talk. Always paired with the extract-only discipline (play that's also *true* is what spreads).

### Task 5 — AUDIO jive ("ear candy"). Witness **W-AUDIO**
**User direction (prior session + 2026-05-30):** standard audio effects to add jive to mundane work. Subtle UI
sounds on interactions: bubble pick (soft tick), bar drop-in (whoosh), trace flow (a rising arpeggio across the
5 chain steps), dismiss (down-tick), dossier open (click), honest-note/error (muted thunk).
- **Tech:** **WebAudio synthesis** (oscillator + gain envelope) — **ZERO audio assets**, keeps the viewer
  self-contained + `file://`-safe (no external deps, no server — consistent with the whole Glassbowl build).
  A tiny synthesized soundbank; NO `.mp3`/`.wav`.
- **Mobile:** `AudioContext` must be created/resumed on the **first user gesture** (autoplay policy) — unlock on
  first pointerdown ([[feedback_mobile_events]]).
- **Respect the user:** a **mute toggle** (🔊/🔇) persisted in the scene `localStorage`; quiet + subtle; never
  block interaction on audio.
- **On-brand delight (extract angle):** map sound to DATA — trace pitch *rises per hop*; bar tone varies by
  spine colour; "tracked N runs" could tick softly. Ear candy that's *true*, mirroring the eye-candy bet.
- **Acceptance:** a persisted mute toggle exists; pick/trace schedule a WebAudio gain node (assert
  `AudioContext` created + a node scheduled); mute silences scheduling; first-gesture unlock works. Pure UI,
  no data writes, no T3. Runs as a sequential follow-on (same shared file) after Task 4.

### The 360° model (user's framing, 2026-05-30) — the canonical description
**The page is a 360° view of records.** The **bubbles are the type / SystemAdmin layer** — relationships
(FK graph) and rules (validation/workflow/columns), the *dictionary* an admin traces. The **right panel
(accordion bars + dossier) and the bottom strip are the GardenWorld instance layer** — the *real rows*
(invoice #200001 = $100.70) and a real document's journey. Legacy iDempiere splits these across **two
logins** (System vs GardenWorld client); Glassbowl fuses them onto one canvas: click the `C_Invoice` *cell*
(admin view) and its real invoices surface right there (operator view). **The trace is where the two layers
meet** — a type-level FK spine lit by one instance's journey. (Nuance: bubbles aren't admin-only — their
rules are exactly the guards/callouts an operator hits daily; it's "the rule and the record it governs, in
one frame," not "admin vs user.") This is the §0.18b duality made literal.

### Task 1 — REAL DATA in the accordion bars (the headline). Witness **W-DATA-BARS**
The map shows FK *structure*; the bars must show the *records*. On click, a bubble's recent-items bar
gains a **"the actual records"** section: a few REAL rows for that table, lazy-loaded from the bundle via
the existing `withBundle(GDB)` path (the same `glassbowl_data.db` the trace already uses). Show the
business-meaningful columns per table (e.g. `c_invoice` → documentno, grandtotal, docstatus; `c_order` →
documentno, grandtotal, dateordered). Tables **not** carried in the bundle (`m_matchpo`, `gl_journal`,
master tables) say honestly *"records not carried in this dataset"* — never faked (§Non-goals).
- **Acceptance:** click `c_invoice` → its bar shows ≥1 real row containing **200001** and **100.70**
  (the §0.19 invoice), pulled from the bundle, not inlined-invented. A non-bundle table's bar shows the
  honest "not carried" note. Gen-side `§DATA-BARS bundle-tables=N` lists which tables can surface rows;
  browser-side the bar's row count > 0 for `c_invoice`. Existing 38 checks unchanged.

### Task 2 — DOUBLE-TAP → ACTION BLURB (the friendlier per-bubble chooser). Witness **W-ACTIONS**
Double-click / double-tap a bubble → a small **anchored blurb** (follows the bubble, dismiss on click-away)
offering the actions **an ERP user wants** — each wired to a real behaviour:
`▸ Trace this flow` (lifecycle bubbles only — opens the chain + bottom strip) · `▦ View data` (surfaces the
records in its bar — Task 1) · `⟲ History` (opens the History view — Task 3) · `⊞ Full dossier` (Task 3).
This is the *per-bubble action array* made visible — replaces guessing with choosing. Single-click still
does focus + log + card (unchanged); double-tap is the new "what can I do here?" affordance.
**CRUD teaser (user, 2026-05-30):** the blurb also lists the write actions — `＋ New · ✎ Edit · 🗑 Delete`
— **greyed/disabled** with a tooltip *"editing — coming later (T3)"*. They show the future without enabling
it (no handler, no write); the read actions above them are live. This makes the full action array visible
while honouring the read-only gate — the same philosophy as the History tab's read-only undo preview.
- **Acceptance:** double-clicking a bubble opens `#blurb` anchored within ~bubble radius of its screen
  position, with ≥2 action buttons; clicking `▸ Trace` on `c_order` opens the strip (`#strip.open`);
  the blurb follows the bubble on orbit (its left/top change) and hides on background click. Lifecycle
  bubbles offer Trace; non-lifecycle bubbles omit it (offer View data / Dossier).

### Task 3 — DOSSIER + HISTORY (2c + the read-only 2d preview, fused). Witness **W-DOSSIER**
`⊞ Full dossier` (from the blurb, or right-click) opens a larger movable panel with **lazy tabs**, each
EXTRACTED: **Data** (more real rows from the bundle, filterable) · **Rules** (`erp_rules` Validation SQL
for the table + plain-English gloss) · **Columns** (`ad_column`: type, mandatory, FK target) · **History**
(*the read-only undo/redo preview*, decision: pure-read). History reads the REAL `kernel_ops` op-log
(inlined as `graph.oplog`, a small extracted sample) and renders it as a change-log; the most-recent ops
get a `↶ preview` control that **greys the row** and shows *"would reverse N tracked ops — read-only, not
enabled"* (N = real op-count, extracted). `↷` un-greys. **No writes, no kernel_ops mutation** — the bar
shape is the seam to the real write-loop later (T3). This is "what an ERP user wants: History."
- **Acceptance:** right-click (or blurb `⊞`) opens `#dossier` with ≥3 tabs; **Data** tab for `c_invoice`
  shows a real row (200001); **Rules** tab lists the table's validation rules and the count equals the
  gen-side inlined count (`§DOSSIER table=c_invoice rules=R columns=C`); **History** tab renders ≥1 real
  op from `graph.oplog` and a `↶` toggle adds `.reversed` (greyed) WITHOUT any localStorage/db write.
  Gen-side `§OPLOG ops=K sample=[...]` proves the op-log is real + non-empty.

### PARKED design fork (user, 2026-05-30) — "do the rules open up to be edited?"
The Rules tab shows validation SQL **read-only** for now. The user's instinct is right and already in the
grain of the data: **every `erp_rules` record ships `editable=1`** (§0.8 *capability-first*) — the rules
were always meant to be the **editing surface**, not just a viewer. So yes, the idea is that the Rules tab
(and the greyed CRUD teaser) **becomes the editor** in the T3 phase. The headline T3 payoff is already
specced as **§2d-3 live rule-impact preview**: edit FIFO→LIFO in a rule and watch the affected matches
**flip on the map** (the diff-oracle, in-browser). That is the "final big picture": Glassbowl stops being a
map of the engine and becomes the *console* you run the engine from — view → choose action → edit a rule →
see the consequence light up — all on one canvas, no tab-switching, no second login (the §0.18b duality).
**Decision deferred to a T3 session (write-loop, `push=live`, explicit go).** This session stays pure-read;
the read-only Rules tab + History preview + greyed CRUD are the *seam* that makes the edit step a small,
honest next move rather than a rewrite. Revisit "the final big picture" here when we open T3.

### Task 4 — SWIPE record picker at the trackball (mobile-first, less typing). Witness **W-SWIPE-PICKER**
**User direction (2026-05-30):** keep the experience finger-swipe, not typing. The record search (today a
typed `#recsearch` datalist) should instead appear **at the trackball as a scrollable list you swipe up/down**
and **tap** to pick — the same flick-gesture vocabulary as orbiting the ball. Standing principle: on mobile,
prefer **swipe/scroll pickers + large tap targets over text entry** ([[feedback_mobile_events]]).
- **Build:** a vertical, scrollable, swipeable list (`#recpick`, `overflow-y:auto; touch-action:pan-y`, large
  rows) anchored near `#ball` (bottom-right), populated from the SAME bundle orders (`populateRecs` already
  reads documentno + c_order_id → `recMap`). Each row shows the documentno (+ grandtotal if cheap). **Tap a
  row → trace THAT record** (`applyChain(walkBundle(GDB, oid))`) — zero typing. Appears with trace mode (like
  `#recwrap` today). **Augment, do not remove** the typed input + `doSearch`/`#reclist` (keep desktop fallback
  + the existing search checks green); the swipe list is the primary mobile affordance.
- **Acceptance:** in trace mode a scrollable `#recpick` shows N>1 real rows (incl. 80001), is vertically
  scrollable (touch pan-y), and tapping a non-seed row traces that record (strip updates to its documentno) —
  no keyboard. Existing record-search checks (datalist + `doSearch`) stay green.
- **Sequencing:** runs AFTER Tasks 1/3/2 (same shared file); its own §-log + test checks naming the issue.

### PARKED (T3 edit interaction) — the centered "view-back" input + depth-of-optics (user, 2026-05-30)
When typing IS needed, a **centered, beveled input bar** rises in the middle of the screen with the record's
**prior values stacked as bars ABOVE it, going back in time** ("more bars deep" = older changes) — the user's
**"depth of optics."** Beside the input, **mobile-keyboard icons** (backspace, ✕/clear). This replaces the
legacy *leave-and-dig-through-Change-Log + undo/redo* trip with an in-place **"view back"**: the edit point
grows up out of its own history. Same accordion metaphor turned inward — bars *across* the right panel = different
records looked at; bars *up over an input* = one field's life over time.
- **Real-data grounding (key):** the kernel commits a RICH op with **`before/after`** per change (§0.16) →
  the stacked prior values are **extracted from `kernel_ops` before/after**, NOT invented. Same op-log the
  History tab (Task 3) surfaces.
- **Read-only slice buildable now:** the **view-back stack** (prior values as bars, from op-log before/after)
  — the "see the past in place" half is pure-read.
- **T3 / parked (write):** the **input bar + keyboard icons committing a new value** — shown **greyed** (same
  discipline as the CRUD teaser): visible, inert, honest. Open with the write loop (`push=live`, explicit go).
- **On-screen keyboard (user, 2026-05-30):** on desktop the centered input can also launch a **clickable
  on-screen keyboard BELOW it**, so mouse users click keys — no physical typing. Same pointer-first philosophy
  as the mobile swipe-picker ([[feedback_mobile_events]]): touch swipes, mouse clicks, keyboard optional. One
  pointer-driven input model across devices. (Also T3 — it commits values — so greyed until the write loop.)
- Reinforces the [[project_erp_ad_ui]] deprecation: the legacy Change-Log window becomes a spatial in-place optic.

### Build order (sequential — one shared file `scripts/system_explorer.js`)
1 (data-in-bars) → 3 (dossier+history panel + `openDossier`/`openHistory` + `graph.oplog`/`node.dossier`)
→ 2 (action blurb, wires its buttons to trace / view-data / history / dossier). Each: spec cite, implement,
regenerate (`node scripts/system_explorer.js`), extend `test_glassbowl.js` with checks **naming the issue**,
run it green (all prior + new), report the §-logs. Final integration + copy to `docs/` + deploy = **explicit
go only** (commit to `full` + `mkdocs gh-deploy --force`, verify live with curl).
