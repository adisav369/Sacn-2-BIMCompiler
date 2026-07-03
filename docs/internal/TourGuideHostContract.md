# Tour Guide — Host Contract (the lifted `help_overlay.js` mount)

> Spec for `prompts/IDEMPIERE_TOUR_GUIDE.md` (overlay-aspect / TourGuide lane). Governs the **one small
> lift** that lets the SINGLE `help_overlay.js` ride BOTH glassbowl and idempiere — **no fork**. Conforms to
> `prompts/UI_OVERLAY_GOVERNANCE.md §Lane separation` (the host contract) + `READSHOWME_DYNAMIC_SPEC.md`.

## 1. The problem the lift solves
`help_overlay.js`'s **pure COACH core** (coachPlan/legalNext/isVeer/nextGate) is already chrome-agnostic and
headless-tested (W-HELP-COACH). Only its **DOM branch** is glassbowl-wired in three places:

| Coupling | Glassbowl-specific today | Lift target |
|---|---|---|
| **Mount** | `document.body.appendChild(wrap/card)` (L95-96) | host-supplied container |
| **Nav (drive ShowMe)** | `window.setTrace/setFocus/openDossierTab` | `nav.trace/focus/openTab` |
| **Locate (place badge/card)** | SVG projection globals `N/idx/project/px/py/k/radius` | `nav.has/locate` |

The lift factors these into one internal `ADAPTER` whose **defaults reproduce today's glassbowl behavior
verbatim** (an un-init'd page is behaviorally diff=0), overridable by a host via `window.__help.init(...)`.
The COACH core is **byte-identical** → W-HELP-COACH stays green → `forked=0` for the part that matters.

## 2. The host contract (`init({ host, nav, hostName })`)
A host calls `window.__help.init(...)` once, after the script + its globals are ready. The overlay needs:

- **`host`** — a DOM container the wrap (NeedHelp?) + card mount into (default `document.body`). Badges and
  the card are `position:fixed` (viewport coords) so the parent only scopes stacking/cleanup, never layout.
- **`nav`** — the navigation/projection interface (every fn optional; a missing fn no-ops, never throws):
  | fn | contract | glassbowl default | idempiere binding |
  |---|---|---|---|
  | `trace(on)` | enter/leave the record's flow view | `window.setTrace` | `IdmpHost.trace` |
  | `focus(key)` | bring the keyed record into view | `window.setFocus` | `IdmpHost.focus(key, keymap[key])` → `openWindow`+goto `#80001`-chain record |
  | `openTab(key, tab)` | switch the focused record's tab | `window.openDossierTab` | `IdmpHost.openTab` |
  | `has(key)` → bool | does this host KNOW this key (→ make a badge)? | `idx[key] != null` | `IdmpHost.has` (a `[data-ad-table=key]` exists) |
  | `locate(key)` → `{x,y,r,rRaw}\|null` | current screen pos, or null if off-screen | SVG projection | `IdmpHost.locate` (`getBoundingClientRect`) |
  | `jive(which,dir)` | optional audio cue (`help`/`nav`/`showme`) | `*Jive()` globals | no-op (idempiere has no audio layer) |

`has` ≠ `locate`: `has` means "this key exists in this chrome" (drives badge creation); `locate` may still
return `null` when that element is currently scrolled/zoomed off-screen (the loop hides the badge then).

## 3. What each lane owns (no cross-editing — UI_OVERLAY_GOVERNANCE §host contract)
- **This lane (TourGuide / overlay):** the lift, the idempiere **key-map** (`help_idmp_keymap.json`), and the
  **init-only adapter** (`help_idmp.js`) that calls `__help.init` with `window.IdmpHost.*`. Read-only. NO fork
  (the adapter contains **zero** coach logic), NO chrome tagging, NO globals exposing.
- **Frontend lane (`IDEMPIERE_RECORD_PANEL.md`):** tags idempiere DOM by AD key + exposes
  `window.IdmpHost = { trace, focus, openTab, has, locate }` + provides the `#idmp-content` mount. **Not yet
  delivered** — so the live in-browser O2C walk is the pending JOIN; everything overlay-side ships now.

## 4. The key vocabulary (agreed up front, pinned for the frontend lane)
`help_ops.json` already keys its O2C steps by AD table name; idempiere tags the same names. The map of the
real `#80001` Order-to-Cash chain (`docs/HelpO2C.md`):

| help key | step | idempiere window | coverage (degrade) |
|---|---|---|---|
| `o2c` | overview (no target) | — | n/a |
| `c_order` | Order #80001 | Sales Order | `partial` → `complete` when Fact_Acct lands |
| `m_inout` | Shipment | Shipment (Customer) | `partial` |
| `c_invoice` | Invoice $100.70 | Sales Invoice | `partial` |
| `c_payment` | Payment | Payment | `partial` |
| `c_allocationline` | Reconciled $98.50 | Allocation | `partial` |

**Graceful degrade (§13.7):** data-bearing steps read via the engine read seam and honour `coverage` —
`complete` (real rows) / `partial` (op-log only, + "install local for full data" note) / `absent` (honest
"install local first"). The tour **explains**, never blocks, never invents. Until the backend read seam +
real Fact_Acct land, every data step is honestly `partial`.

### 4a. Coverage-read contract — CONFIRMED by backend (§13.7, 2026-06-03); spec-not-built
The marker my data steps degrade on comes from the engine read seam, sampled **firewall-correctly**: the
overlay NEVER calls the engine — the record-panel calls `readPostings(recordRef, ctx)` and surfaces the
marker on the focused record; the Tour reads it **by key through the host** (same pattern as `IdmpHost`; a
"pure read fn" handed to the overlay is rejected — it would put an engine call inside the overlay).

```
readPostings(recordRef, ctx) -> { visible, posted, lines[], balanced, source, coverage, note, reason }
  source   = 'fact_acct' | 'oplog' | 'none'      // the Tour reads only { source, coverage, note }
  coverage = 'complete'  | 'partial' | 'absent'
```
- **`complete` ⟸ real Fact_Acct (S1 install→extract of a real instance's GL), NEVER from POST.** The §13.5
  POST verb yields `source:'oplog'` = `partial` at best. The demo `ad_seed.db` has `fact_acct` **absent**
  (0 rows) → `complete` is **unreachable from the seed**. So the honest copy is *"install your local data for
  full GL history"* — do NOT promise `complete` from a POST gesture.
- **Role vs data-absent are distinct** (only matters if a *posted* step is added): role-refused =
  `{ visible:false, reason:'role-not-accounting' }` → "your role doesn't show accounts"; data-absent =
  `{ visible:true, coverage:'absent', note:'install local data first' }` → "install local data first".
- **Status: spec-not-built.** `readPostings` has zero code (no `§POSTED-READ/GATE/COVERAGE` witnesses yet).
  The swap from the static keymap value to a live marker is gated on (i) engine ships `readPostings` (S2),
  (ii) record-panel calls it + exposes the keyed marker. **Until both land, keep `coverage:"partial"`
  hardcoded in `help_idmp_keymap.json`** — it is the *correct* conservative value for a POST-capable,
  unposted seed, not a placeholder bug.
- **Caveats for a future posted step:** (1) the browser POST path may be unwired (verb exists in the Node
  kernel `erp_kernel.js`, not seen in `kernel_ops.js`; §13.6 cent-gate unbuilt) — verify with the kernel
  lane before scripting a live POST step; (2) the bundled `fact_acct` is a **totals** extract (no
  `ad_table_id`/`record_id`) — it can't do a per-record GL fold for #80001; ask backend to re-extract with
  record-ref columns first.

## 5. Drift gate (governance rule)
Every keyed step MUST have a key-map entry and vice-versa: a step with no mapped element, or a mapped element
with no step, FAILS the build (`§TOUR-ALIGN orphan-steps=0 orphan-elements=0`).

## 6. Witnesses (§-log first — `scripts/test_tour_idempiere.js` → `build/erp/tour_idempiere_witness.log`)
- `§TOUR overlay=help_overlay host=idempiere forked=0 mounts=2 keysMatched=Y` — the SAME module rides
  idempiere via `init`; wrap+card mount into the host; keyed steps resolve to mapped elements.
- `§TOUR-O2C steps=6 source=HelpO2C real-order=#80001 showme-nav=via-globals` — O2C reused verbatim; ShowMe
  drives the host's `nav.*`, never chrome internals.
- `§TOUR-ALIGN keyed-entries=N orphan-steps=0 orphan-elements=0` — the drift gate.
- `§SEAM ui-direct-oplog-access=0 tour-writes=0` — read-only coach (static scan: no dispatch/commit/SQL).
- `§TOUR-DEGRADE step=<data-step> coverage=partial` — data steps degrade honestly until the seam lands.
