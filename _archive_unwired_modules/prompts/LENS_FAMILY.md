# ⚠ DO NOT REMOVE — Scope guard / resume card
# Scope: the LENS-FAMILY lane — the frontend-host LENSES (chat · feed · kanban · desktop command-centre ·
#        grid · classic) over the ONE owned model (AD dictionary + data + signed op-log), PLUS the
#        cross-cutting concurrent-lanes coordination I authored. ONE of 3 concurrent lane kinds
#        (prompts/UI_OVERLAY_GOVERNANCE.md §Lane separation). A LENS is a HOST (tags by key · exposes
#        nav globals · mounts); CRUD/Report/Tour/chart are OVERLAY-ASPECTS that ride every lens by key.
# NON-NEGOTIABLE: spec-first; witness-led (each test NAMES the issue it proves); §-log first (READ the log
#        before any conclusion); deterministic/non-invent (every rendered value a fold; absent→"absent");
#        REUSE engine via ENGINE_CONTRACT + overlays unforked; EXPLICIT GO before any deploy.
# Read first: docs/CONCURRENT_LANES_ROADMAP.md (the MAP — 3 lanes/2 seams) · docs/ENGINE_CONTRACT.md +
#        prompts/UI_OVERLAY_GOVERNANCE.md §Lane separation (the two seam authorities) ·
#        prompts/MOBILE_CHAT_LENS.md + docs/DESKTOP_LENS_SPEC.md (the lens specs) · memory [[project_lens_family]].

---

## ▶ START HERE (resume) — state as of 2026-06-03 · LANE-3 FLEET + STEP-0 SEAM#2 **LIVE**; finishing the UI next

**Thesis (the north star):** the UI was never the product — the model (op-log) is the asset; a UI is a
cheap, swappable **lens** (a fold). Consolidation, NOT N switchable skins: one "better iDempiere all-round"
that absorbs Odoo's *restraint* (Pareto), closest hill first. Killer surprise = install→extract their
on-prem Postgres (read-only; doubles as the Pareto oracle). Phone = act (chat/feed); big-screen =
orchestrate/comprehend/verify. Full reasoning + decisions: memory [[project_lens_family]].

**DONE (engine lane, headless, REAL data via glassbowl_data.db, ALL PASS — READ the logs):**
- `scripts/poc_chat.js` → `build/erp/poc_chat.log`: op-log → faithful/deterministic/dismissible/anchorable
  thread (`§CHAT-THREAD/REPLAY/DISMISS/ANCHOR/COVERAGE`). Caveat: sender shows `user:0` (real createdby=0/System in seed).
- `scripts/poc_kanban.js` → `build/erp/poc_kanban.log`: Kanban = `doc_status` fold; **SEND==DRAG==VERB**
  byte-identical op+hash (`§KANBAN-FOLD/SEND-EQ-DRAG/KANBAN-LEGALITY`). Caveat: board sparse (seed all-CO).

**SPECS hardened (UNFROZEN):** `prompts/MOBILE_CHAT_LENS.md` (chat/feed · pills-as-flips · SSO ·
contact-import/share-out killer with HONEST API reality · sensors/intent-detection future waves) ·
`docs/DESKTOP_LENS_SPEC.md` (command-centre→drill into improved-iDempiere; **Odoo pain→antidote→witness
P1–P12, every pain web-SOURCED**; §3b finer pains collapse to roots + 2 domain checks: reconciliation =
the settlement-matcher's home turf, identifier-display) · `docs/CONCURRENT_LANES_ROADMAP.md` (3 lanes/2 seams).

**3 concurrent lanes LIVE** (UI_OVERLAY_GOVERNANCE §Lane separation): backend = sharding/streaming;
frontend-host = `IDEMPIERE_RECORD_PANEL.md` (supplies the host contract once, serves CRUD+Report+Tour);
overlay = ShowMe Tour `prompts/IDEMPIERE_TOUR_GUIDE.md`. **This lane = the LENS chromes (hosts).**

## STEP 0 — shared first move (before building far)
Ratify BOTH seams with the other sessions: (1) ENGINE_CONTRACT 5 calls + `ctx` + `DataSource.readRecords`;
(2) the host contract = key vocabulary + exposed-globals list + mount point. Witness `§SEAM-FROZEN` ×2.
After that, no shared file is co-edited; integrate by key + by `read`.

> ▎ **Don't author your own exposed-globals — adopt the host contract `IDEMPIERE_RECORD_PANEL` freezes at
> STEP 0.** One joint freeze, not two, or the Tour binds to the wrong host. `IDEMPIERE_RECORD_PANEL` is the
> designated OWNER of the host contract ("supplies the host contract once"); the record-panel and Tour
> sessions are already aligned (record-panel owns it, Tour consumes it). **This lens lane is the loose end:
> it DEFERS — it does not mint `send`/`dispatch`/nav globals of its own.** Every lens chrome leaves those as
> `// TODO(STEP-0):` and binds to the frozen names afterwards, integrating by key + by `read`.

### STEP-0 detection (on disk) — UPDATE 2026-06-03: **seam #2 FROZEN on idempiere.html** (this session, not yet deployed)
`window.IdmpHost` exposed + O2C `data-ad-table/record` tagging + `#idmp-content` mount + unforked help scripts +
inlined keymap (zero drift) all landed on `idempiere.html`; witness `erp/tests/poc_idmp_host.js` §SEAM-FROZEN
31/31, Tour still 24/24, detection grep GREEN. **Seam #1 (write `dispatch`/`ctx`) still TODO pending engine C0**
— so the lens chromes' nav binds but their writes (kanban drag / chat send) stay inert. (Prior state below.)

#### Prior state — as of earlier 2026-06-03: NOT FROZEN
The help-layer falsifier (run from repo root):
`grep -rE "SEAM-FROZEN|window\.(Idmp|Erp|Host)[A-Za-z]*\s*=|data-ad-(table|record)" bim-ootb/erp/idempiere.html bim-ootb/erp/tests/*.log`
Three artifacts MUST appear; today **all three are absent** (idempiere.html is untagged, no host global, no mount):
1. **Exposed-globals object — the frozen name is `window.IdmpHost = {trace,focus,openTab,has,locate}`** — ALREADY
   pinned by the TourGuide lane (`docs/TourGuideHostContract.md` §2/§44; `help_idmp.js` consumes it; `§TOUR
   showme-nav=via-globals` 24/24). **ADOPT it — do NOT mint a new name.**
2. **AD-key tagging on the O2C-chain DOM** — `[data-ad-table=<key>]` on c_order/m_inout/c_invoice/c_payment/c_allocationline.
3. **Mount point** — `#idmp-content`.
Owner = `IDEMPIERE_RECORD_PANEL.md` (frontend lane), **"Not yet delivered"** — the lens lane is parked behind it.

> ⚠ **Two reconciliations before STEP 0 runs:**
> (a) **Witness-name:** this card gates on `§SEAM-FROZEN`, but RECORD_PANEL's witness list
> (`§REUSE/§SEAM/§POSTED/§DOCACTION`) does NOT emit `§SEAM-FROZEN` — so the freeze must explicitly emit that
> token, or the lens grep above never goes green.
> (b) **The lens lane needs BOTH seams; Tour needs only one.** `IdmpHost` is the read-only NAV contract
> (trace/focus/openTab/has/locate — no write path; `§SEAM tour-writes=0`). The lens chromes MUTATE
> (kanban drag → `SET_STATUS`, chat send → `SET_STATUS`), so they ALSO need the `ENGINE_CONTRACT` write seam
> `dispatch(intent,ctx)` (seam #1). Lens blocker = seam #1 **AND** seam #2 (`IdmpHost`).
> Seam #1's **engine half is PROVEN** (backend: W-CRUD-WRITELOOP 11/11, I4 replay-stable, verify, manifest) —
> the kernel is NOT the gap. What's unbuilt: **C0**, the single engine-side 5-call wrapper (`ENGINE_CONTRACT §6`,
> ENGINE lane builds it — record-panel CONSUMES, does not build) + the host injecting the real `dispatch` and a
> populated `ctx` {actor,pubKey,roleId,allowOrgs} (the lens already calls `opts.dispatch(intent, ctx||{})`, ctx empty today).

### ADOPTION MAP — each `TODO(STEP-0)` stub → frozen artifact (one pass when the freeze lands)
| Lens file | `// TODO(STEP-0)` stub | Binds to |
|---|---|---|
| `chat_lens.js` | nav `openThread` / open-record | `IdmpHost.focus(key, keymap[key])` + `openTab(key,tab)` + `trace(on)`; key = AD table (`c_invoice`…) |
| `chat_lens.js/.html` | `send=dispatch`; op-build → `read(kernel_ops,ctx)`; anchor → `read(relatedDocs,ctx)` | `ENGINE_CONTRACT` seam #1 (`dispatch`/`read` + `ctx`) |
| `kanban_lens.js` | mount `opts.dispatch`/`opts.ctx`; card-open | `ENGINE_CONTRACT` `dispatch`/`ctx` (drag→`SET_STATUS`) + `IdmpHost.openTab` |
| `feed_fold.js` | nav/dispatch (tap → open thread / fire verb) | `IdmpHost.focus`/`openTab` (open) + `ENGINE_CONTRACT` `dispatch` (verb); `rank` consumes `read(…,ctx)` |
| `user_names.js` | (none — pure fold) | F1 consumes `window.UserNames.resolveTag` by key (already wired) |
| all chromes | mount container | host `#idmp-content` (or host-supplied sub-container via `mount({host})`) |
| all view-models | already KEY by AD ids (table/column/record) | bind to host's `[data-ad-table=<key>]` tagging |

## FREEZE GATE — before ANY desktop implementation
A **live-Odoo diff-oracle** pass: stand up / hit an Odoo instance, observe P1–P12 for real (clean-room,
non-invent), refresh `DESKTOP_LENS_SPEC §3/§3b`. The specs are hardened but UNFROZEN until this runs.

## SHIPPED THIS ARC (2026-06-03 — LIVE on gh-pages, witnessed; READ the logs)
- **Lane-3 chrome fleet DEPLOYED (PR #92):** `chat_lens` · `kanban_lens` · `feed_fold` · `user_names`, all
  §-witnessed (`erp/tests/poc_*`), F1 adversarial-reviewed + nits fixed. Live demo pages over mock `glassbowl_data.db`.
- **STEP-0 seam #2 (host contract) FROZEN + DEPLOYED (PR #93):** `idempiere.html` exposes `window.IdmpHost`
  {trace,focus,openTab,has,locate} + `data-ad-table/record` O2C tagging + `#idmp-content` mount + unforked
  `help_overlay`/`help_idmp` + inline keymap. Witness `erp/tests/poc_idmp_host.js` **§SEAM-FROZEN 31/31**; Tour
  `scripts/test_tour_idempiere.js` **24/24**. Tour O2C nav + lens nav now bind by key.

## STILL PENDING (the inert edges — each names its gate)
- **Seam #1 (writes) INERT:** kanban-drag / chat-send don't commit. Needs (a) backend **C0** 5-call wrapper
  (`prompts/BACKEND_LANE_S2.md` Task 1 — specced, unbuilt) **+** (b) host injection of real `dispatch` + populated
  `ctx` at the `// TODO(STEP-0 seam#1)` in `idempiere.html` (record-panel/host, ours). Build C0 → wire host → writes live.
- **Live ShowMe render UNVERIFIED:** §-witnesses proved the plumbing, NOT the browser visual. RISK: the overlay
  mounts into `#idmp-content`, which `renderBody()` clears (`innerHTML=''`, `idempiere.html:628`) on tab switch —
  may wipe NeedHelp?/badges (consider mounting on `document.body`). Run a live Playwright check before claiming it shows.

## NEXT bounded builds — **FINISH THE UI** (pick ONE; spec-first; each names its witness; EXPLICIT GO to deploy)
- **N0 — Verify what shipped (do first):** live browser pass on `…/erp/idempiere.html` — does NeedHelp? render?
  click ShowMe → open Sales Order → badges or honest "not in seed"? Confirm/repair the `#idmp-content` innerHTML-wipe risk.
- **N1 — Chat messenger polish (mobile — the headline):** `chat_lens` functional → full WhatsApp/Telegram surface:
  **pills-as-PILL-ICONS** via the `pill_builder.js` / `pills.json` registry (DATA, not bespoke chrome), swipe gestures +
  send/receive audio + pending→confirmed transitions, wire `window.UserNames` (kill `user:0`) + `window.FeedFold` (inbox)
  BY KEY. Spec `prompts/MOBILE_CHAT_LENS.md` §Controls-ARE-pills + §Delight. Witness `§CHAT-MOBILE-*`.
- **N2 — Reformed Desktop lens:** command-centre → drill into improved-iDempiere (H0 host contract → F3 command-centre +
  chart overlay → F4 grid/form). Spec `docs/DESKTOP_LENS_SPEC.md` (Odoo pain P1–P12 antidotes). ⚠ GATE: the **FREEZE GATE**
  above (live-Odoo diff-oracle pass) runs FIRST, or the user explicitly lifts it — do not skip it silently.
- **N3 — Flip writes live (after backend C0):** wire host `dispatch`+`ctx` → verify kanban drag / chat send commit a real
  signed op (`§*-WRITE` round-trip). Closes the inert edge.
- **N4 — Install pill → migrate dialog (the cold-start door):** add a first-class **`install` pill** to the registry
  (`pills.json`/`pill_builder.js` — DATA, not bespoke chrome) that opens the EXISTING migrate dialog (`erp/migrate_showme.js`,
  owned by `prompts/MIGRATE_SHOWME_OVERLAY.md` — do NOT fork it) with a **source selector: iDempiere PG/Docker (LIVE) ·
  Odoo PG (honest-planned — read+fold PROVEN `4042fe85`, master extractor not built)**. Lens lane adds ONLY the pill trigger +
  source-selector UI, by key, unforked; the dialog + the Odoo master extractor stay with the overlay/backend owners. Today the
  dialog launches from the generic help pill — N4 gives it its own door. Witness `§INSTALL-PILL present=Y registry=pills.json
  opens=migrate-dialog` + `§INSTALL-SOURCE options=[idempiere:live,odoo:planned] selected=<s>` (each test NAMES its issue).

## ▶ AGENT LAUNCH (intra-lane workers — NOT new lanes; this stays the 3rd lane)
Operational cap = 3 concurrent lanes (the two others + this). This lane parallelizes via worktree-isolated
AGENTS, all owned by lane 3, all editing ONLY lane-3 files. **Launching agents does NOT add a lane.**
- **Scope = UNBLOCKED work only** (engine-proven by the POCs): **F1** chat chrome · **F2** kanban chrome ·
  **feed-fold** (thread-list-as-inbox push) · **user:0-names** (fold `AD_User` names into the chat sender).
  HELD: desktop F3–F6 (until the live-Odoo freeze gate) · anything that DEFINES exposed-globals (until STEP 0).
- **Each agent (`isolation: 'worktree'`):**
  - EDIT ONLY `bim-ootb/erp/<new lens files>` + `bim-compiler/scripts/poc_*.js`.
  - NEVER touch backend (`scripts/erp_kernel.js`, DataSource, shard gen), overlays (`*_overlay.js`,
    `*_ops.json`), other lanes' specs (`IDEMPIERE_RECORD_PANEL.md` / `IDEMPIERE_TOUR_GUIDE.md`), or any seam doc.
  - READ-ONLY context: `docs/ENGINE_CONTRACT.md`, `prompts/UI_OVERLAY_GOVERNANCE.md §Lane separation`,
    `docs/CONCURRENT_LANES_ROADMAP.md`, this card, `build/erp/poc_chat.log` + `poc_kanban.log`.
  - KEY by AD ids (table/column/record) — the natural vocabulary; do NOT invent exposed-globals (leave a
    marked TODO until STEP 0 pins them with the host lane).
  - Produce a `§`-witness under `build/erp/`; NO deploy (EXPLICIT GO). Integrate by key; merge worktrees
    into lane 3 only — never edit another worker's chrome.
- **To fire (new session):** open THIS card and say **`launch`** (run the lane-3 worker fleet on the scope
  above) — or **`STEP 0 first`** to pin exposed-globals with the host lane before any chrome agent binds nav.

## DISCIPLINE
A lens is a HOST — it tags + exposes + mounts; it does NOT rebuild CRUD/Report/Tour/chart (those ride by
key, unforked — reskin = new chrome + same overlay). Graceful-degrade (`coverage`) removes the data-block.
§-log under build/erp; READ before concluding. No deploy without GO. Edit ERP code in `bim-ootb/erp/` directly.
