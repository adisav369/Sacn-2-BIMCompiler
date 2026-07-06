# ⚠ DO NOT REMOVE — Scope guard
# Scope: mount the GENERAL ShowMe/Help TOUR GUIDE (incl. the Order-to-Cash tour) on idempiere.html by
#        REUSING the glassbowl tour layer — help_overlay.js + help_ops.json + docs/HelpO2C.md. NO FORK.
#        This is a SEPARATE, concurrent concern (overlay-aspect lane) — NOT the chrome, NOT the engine,
#        NOT the migrate-install tour (that goes with login = MIGRATE_SHOWME_OVERLAY.md, another session).
# Authority specs (CONFORM, do not duplicate):
#   prompts/UI_OVERLAY_GOVERNANCE.md     — the GOVERNING law incl. §lane-separation + the HOST CONTRACT.
#   prompts/READSHOWME_DYNAMIC_SPEC.md   — the ShowMe/Help coach spec (D3 LANDED); docs/ReadMeShowMe.md.
#   docs/HelpO2C.md                      — the Order-to-Cash tour content (real order #80001 traced).
#   prompts/IDEMPIERE_RECORD_PANEL.md    — the FRONTEND lane that supplies the HOST CONTRACT (keys+globals).
#   docs/PLUGIN_ARCHITECTURE.md §13.7    — the read seam + source/coverage graceful-degrade.
# NON-NEGOTIABLE: spec-first; witness-led (each test NAMES the issue); §-log first (READ the log before
#   conclusions); non-invent (real steps/data from the keyed store + real instance, never fabricated).
#   READ-ONLY coach — the tour NEVER writes; it points, navigates, explains.
# DISCIPLINE: OVERLAY-ASPECT LANE — reuse help_overlay.js (do NOT fork it); attach BY KEY to elements the
#   FRONTEND tags; drive ShowMe via the host's EXPOSED GLOBALS (never reach into chrome internals). Do NOT
#   tag elements yourself (that's the frontend's host-conformance job) — consume the agreed key vocabulary.
#   EXPLICIT GO before deploy; rebase onto the erp/ folder home.

---

# iDempiere Tour Guide — reuse the glassbowl ShowMe/Help (incl. O2C), on idempiere chrome

## Why this session exists
The tour guide (numbered-bubble + "NeedHelp? → ShowMe" coach) is LANDED on glassbowl: `help_overlay.js`
(23KB), the keyed `help_ops.json` store, and `docs/HelpO2C.md` (Order-to-Cash, real order #80001 traced
end-to-end). idempiere.html has none of it. Per the "one engine, N renderers / one overlay, all chromes"
rule, the tour is REUSED, not rebuilt — same module, keyed to idempiere's tagged elements.

## Lane & contract (one of 3 concurrent lanes — UI_OVERLAY_GOVERNANCE §lane-separation)
- **This lane = overlay-aspect (Tour).** Read-only. Builds in PARALLEL with backend (engine) and frontend
  (idempiere chrome).
- **Depends ONLY on the host contract** the frontend (`IDEMPIERE_RECORD_PANEL.md`) provides: (1) the **key
  vocabulary** (AD element ids tagged on the DOM), (2) the **exposed nav/projection globals**
  (idempiere's equivalents of glassbowl's `setTrace/setFocus/openDossierTab`), (3) a **mount point**.
- **Coordinate up front:** agree the key vocabulary + the exposed-globals list with the frontend session.
  After that the lanes are free — integrate by key, not by editing each other's files.

## What is REUSED vs BUILT
- **REUSE (do not edit):** `help_overlay.js` (the coach behavior + ShowMe navigation), `help_ops.json` (the
  keyed step store), `docs/HelpO2C.md` (the O2C tour content + figures).
- **BUILD (thin):**
  1. **Lift the mount, don't fork** — `help_overlay.js` hardcodes `document.body`/glassbowl-bubble attach
     (~95-97); factor the mount + the nav-globals to host-supplied (`init({ host, nav })`). Same code now
     rides glassbowl AND idempiere (the fix benefits glassbowl too).
  2. **Map keys** — the O2C/help steps in `help_ops.json` are keyed by element id; confirm those keys match
     the frontend's tagged idempiere elements (the agreed vocabulary). A keyed step with no element, or a
     tagged element with no step, FAILS the drift gate (governance rule).
  3. **Drive ShowMe via the host globals** — the coach navigates by calling idempiere's exposed nav fns,
     never by touching chrome internals.
  4. **O2C tour** — surface `HelpO2C` on idempiere: NeedHelp? → numbered `?` bubble → ShowMe walks the real
     order #80001 chain. Reuse the content verbatim; only the host changes.
  5. **Graceful degrade (data-bearing steps)** — a step that shows real rows reads via the §13.7 read seam
     and honours `coverage` (`partial`+note / `absent`) — the tour explains "install local for full data",
     never blocks, never invents.

## Witnesses (§-log first)
- `§TOUR overlay=help_overlay host=idempiere forked=0 mounts=N keysMatched=Y` — the SAME glassbowl module
  rides idempiere (diff=0), keyed steps resolve to tagged elements.
- `§TOUR-O2C steps=N source=HelpO2C real-order=#80001 showme-nav=via-globals` — the O2C tour reused, driving
  ShowMe through the host's exposed globals.
- `§TOUR-ALIGN keyed-entries=N orphan-steps=0 orphan-elements=0` — the governance drift gate (no stale).
- `§SEAM ui-direct-oplog-access=0 tour-writes=0` — read-only coach; never touches kernel_ops, never writes.
- `§TOUR-DEGRADE step=<data-step> coverage=<complete|partial|absent>` — data-bearing steps degrade honestly.

## Acceptance
DONE when, in `erp/idempiere.html`: NeedHelp? lights the numbered bubbles, ShowMe walks the steps (incl.
the O2C tour on real order #80001) by driving the host's exposed nav globals, all rendered by the **unforked**
`help_overlay.js` keyed to the frontend's tagged elements (drift gate green), read-only, data-bearing steps
degrading gracefully. Then STOP. The migrate-install first-mile tour (with login) is a SEPARATE session
(`MIGRATE_SHOWME_OVERLAY.md`); other renderers are later slots; deploy is a separate GO.

> Note (cross-lane): the migrate dialog is now slated to gain a first-class **`install` pill** trigger
> (lens lane, `LENS_FAMILY.md` N4 / `MOBILE_CHAT_LENS.md` §Install pill) and a **dual-source selector**
> (iDempiere LIVE · Odoo planned, read+fold proven `4042fe85`). The O2C tour's graceful-degrade note
> "install local for full data" points at THAT dialog — keep the wording pointing to the install pill,
> not the generic help pill, once N4 lands. Still a separate session; nothing to build here.

## Guardrails
- Reuse `help_overlay.js`; NEVER fork/copy it. If a mount/global is glassbowl-specific, lift it to a host
  param so BOTH chromes share it.
- Conform to `UI_OVERLAY_GOVERNANCE`: keyed store + standalone overlay, attach by key, drift witness, opt-in
  (NeedHelp?), removable.
- READ-ONLY: the tour never writes; data shown is the real instance via the read seam, never fabricated.
- Do NOT tag elements or expose globals yourself — those are the frontend's host-conformance; consume the
  agreed vocabulary. Pin keys+globals with the frontend session up front.
- EXPLICIT GO before deploy; rebase onto the `erp/` folder home; read `bim-ootb/GH_DEPLOY.md` first.

## Status
KICKOFF (overlay-aspect / TourGuide lane), 2026-06-03. Concurrent with the backend (engine) and frontend
(`IDEMPIERE_RECORD_PANEL.md`) lanes; coupled only via the host contract (keys + globals) + the read seam.
Produces: the lifted `help_overlay` mount + the idempiere key-map + O2C tour wiring + a `# DONE` ledger
(claim ↔ §-line) + the run log. No fork, no engine edit, no chrome tagging (frontend's job), no deploy.

---

# DONE — overlay side (2026-06-03)  [run log: build/erp/tour_idempiere_witness.log]
Scope chosen with user: FULL overlay side now; live in-browser O2C-on-idempiere walk is the pending JOIN on
the frontend host contract (`IDEMPIERE_RECORD_PANEL.md`, not yet delivered — idempiere exposes no nav globals,
no AD-key tagging, no mount). Every claim below traces to a `§`-line in the run log; READ the log, not exit code.

| # | Claim | Artifact | §-line (build/erp/tour_idempiere_witness.log) |
|---|---|---|---|
| 1 | **No fork** — same `help_overlay.js`, pure COACH core byte-identical | `build/erp/help_overlay.js` (lift); W-HELP-COACH 21/21, W-HELP-NEXTGATE 11/11 still green | `§TOUR … forked=0 mounts=2 keysMatched=Y` + adapter-init-only PASS |
| 2 | **Mount lifted** — `init({host,nav})`; defaults reproduce glassbowl verbatim; idempiere re-parents to `#idmp-content` | `help_overlay.js` HOST/NAV adapter + `init`; `help_idmp.js` | `§TOUR … mounts=2`; `init() re-parents … host rebound` PASS |
| 3 | **Keys map + drift gate** — 6 steps ↔ 6 keymap entries, no stale | `build/erp/help_idmp_keymap.json` | `§TOUR-ALIGN keyed-entries=6 orphan-steps=0 orphan-elements=0` |
| 4 | **O2C reused** — 6 steps, op=o2c, HelpO2C.md source, real order #80001 | `help_ops.json` (reused) + `docs/HelpO2C.md` (reused) | `§TOUR-O2C steps=6 source=HelpO2C real-order=#80001 showme-nav=via-globals` |
| 5 | **ShowMe via host globals** — drives `IdmpHost.trace/focus/openTab`, never chrome internals | `help_idmp.js` nav adapter; DOM-shim wiring | `§TOUR … showme-nav=via-globals` + 4 IdmpHost.* PASS lines |
| 6 | **Read-only coach** — no write, no direct op-log access (static scan, overlay + adapter) | both files | `§SEAM ui-direct-oplog-access=0 tour-writes=0` |
| 7 | **Graceful degrade** — every data step declares honest coverage (partial until seam lands) | `help_idmp_keymap.json` | `§TOUR-DEGRADE step=<k> coverage=partial` (×5) |

**Reused, not edited:** `help_ops.json`, `docs/HelpO2C.md`. **Built:** the lift in `help_overlay.js`,
`build/erp/help_idmp.js` (init-only adapter, zero coach logic), `build/erp/help_idmp_keymap.json`,
`docs/TourGuideHostContract.md` (host-contract spec), `scripts/test_tour_idempiere.js` (W-TOUR-IDEMPIERE 24/24).
**Not done (out of scope / blocked):** no chrome tagging, no `window.IdmpHost` exposure (frontend's job), no
deploy. Deployed glassbowl copies (`docs/`, `site/`) intentionally untouched — the lift reaches live glassbowl
only on a deploy GO (the `forked=0` benefit applies to glassbowl then too).

**Pending JOIN (frontend lane must deliver the host contract — `docs/TourGuideHostContract.md §2-4`):**
expose `window.IdmpHost = { trace, focus, openTab, has, locate }`, tag the O2C-chain DOM by the AD keys
(`c_order/m_inout/c_invoice/c_payment/c_allocationline`), provide the `#idmp-content` mount, and include
`<script src=help_idmp_keymap.json>`-as-`window.__helpIdmpKeymap` + `help_overlay.js` + `help_idmp.js`. Then
NeedHelp? lights the numbered badges and ShowMe walks #80001 — no change to this lane's files.

---

# DONE — LIVE BIND verified (2026-06-03, on your GO)  [run log: build/erp/tour_bind_witness.log]
The frontend record-panel lane shipped the host contract on `bim-ootb/erp/idempiere.html` (06:45): `window.IdmpHost
= {trace,focus,openTab,has,locate}`, `data-ad-table/record/column` tagging at render, inlined `__helpIdmpKeymap`,
`#idmp-content` mount, the two scripts loaded, AND `§SEAM-FROZEN` logged. I bound + verified — NO chrome edits.

| # | Claim | §-line / evidence |
|---|---|---|
| 1 | **forked=0 (deployed)** — `help_overlay.js` + `help_idmp.js` in `bim-ootb/erp/` are BYTE-IDENTICAL to my `build/erp/` source | `diff -q` IDENTICAL ×2 |
| 2 | **keymap verbatim** — inlined `__helpIdmpKeymap` mirrors `help_idmp_keymap.json` (5 docs + o2c, window/table/coverage) | `§TOUR-BIND … inlined __helpIdmpKeymap mirrors … verbatim` |
| 3 | **binds to the REAL host** — extracted the actual `IdmpHost` from idempiere.html; adapter NAV routes `has`/`locate` through it | `§TOUR-BIND … adapter NAV is bound to the REAL IdmpHost` |
| 4 | **ShowMe drives real globals** — `ShowMe(c_order)` → real `IdmpHost.focus → openWindow("Sales Order")` for #80001 + `trace(true)` | `§TOUR-BIND … openWindow("Sales Order") … trace(true)` |
| 5 | **NeedHelp? lights the badge** — built a numbered badge for the tagged #80001 row, gated on the host's real `[data-ad-table]` | `§TOUR-BIND badges=1` |
| 6 | **honest has** — `IdmpHost.has(absent)=false`, no fabricated element | `§TOUR-BIND … has(absent)=false` |

**Witness:** `scripts/test_tour_idmp_bind.js` **W-TOUR-BIND 11/11** (binds my unforked overlay against the host's
ACTUAL `IdmpHost` code in a DOM shim — not a mock). Full suite green: W-HELP-COACH 21/21 · W-HELP-NEXTGATE 11/11 ·
W-TOUR-IDEMPIERE 24/24 · W-TOUR-BIND 11/11.

**Honestly NOT done (no tooling / out of scope):** no live-browser render proof — puppeteer/Playwright absent, and
idempiere.html needs SQLite-WASM + a server to paint. Binding is verified whitebox at the JS level against the real
host (the protocol's §-log-first primary); the pixel-level "badges visible on the rendered page" is the frontend's
to screenshot from its deploy pipeline. Live glassbowl (`docs/help_overlay.js`) is still pre-lift — it converges to
the unforked module on its own glassbowl deploy GO (the lift is behavior-diff=0, safe whenever). Coverage stays
`partial` per the backend contract (`readPostings` spec-not-built; `complete` = real-instance install, never POST).
