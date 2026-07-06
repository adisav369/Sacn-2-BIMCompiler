# ⚠ DO NOT REMOVE — Lane-3 LENS launch (UI-finish phase). Read `prompts/LENS_FAMILY.md` ▶START HERE first
# (the resume card: what shipped LIVE, what's still inert, the gates). Scope-first; witness-led; §-log first;
# deterministic/non-invent; EXPLICIT GO before any deploy. This is lane 3 of 3 (operational cap — agents are
# intra-lane workers, not new lanes; EDIT ONLY `bim-ootb/erp/<lens files>` + `bim-compiler/scripts/poc_*.js`).

Read `prompts/LENS_FAMILY.md` ▶START HERE, then launch the **UI-finish** fleet on the UNBLOCKED scope below.
Say **`launch`** to fire it, or **`N1`/`N2`/`N0` only** to run a single build.

## Already SHIPPED LIVE (do NOT rebuild — see LENS_FAMILY §SHIPPED THIS ARC)
- Lane-3 chrome fleet (PR #92): `chat_lens` · `kanban_lens` · `feed_fold` · `user_names` (§-witnessed, reviewed).
- STEP-0 seam #2 host contract (PR #93): `idempiere.html` `window.IdmpHost` + `data-ad-*` tagging + help scripts;
  `§SEAM-FROZEN 31/31`, Tour `24/24`. Nav binds by key.

## THE FLEET (UNBLOCKED scope only)
- **N0 — verify-what-shipped (LEAD, do first):** a live browser pass (Playwright) on
  `https://red1oon.github.io/bim-ootb/erp/idempiere.html` — does NeedHelp? render · click ShowMe · open Sales Order ·
  badges or honest "not in seed"? Repair the `#idmp-content` innerHTML-wipe risk (`renderBody()` clears it on tab
  switch → mount the overlay on `document.body` if it wipes). Witness `§SHOWME-LIVE` + a screenshot. This is wiring
  verification — Playwright is the right tool here (the visual render IS the claim).
- **N1 — chat messenger polish (mobile, the headline):** take `chat_lens` from functional → the full
  WhatsApp/Telegram surface. **Pills-as-PILL-ICONS via the `pill_builder.js` / `pills.json` registry** (pills are DATA,
  never bespoke chrome — `[[feedback]]` non-invent); swipe gestures + send/receive audio + pending→confirmed
  transitions; wire `window.UserNames` (kill `user:0`) + `window.FeedFold` (inbox) BY KEY. Spec `prompts/MOBILE_CHAT_LENS.md`
  §Controls-ARE-pills + §Delight. Build → adversarial review (a 2nd agent checks chrome vs spec + mobile reflow).
  Witness `§CHAT-MOBILE-*` (each test NAMES its issue); §-log first.
- **N2 — reformed Desktop lens:** command-centre → drill into improved-iDempiere (H0 host contract → F3 command-centre
  + chart overlay → F4 grid/form). Spec `docs/DESKTOP_LENS_SPEC.md` (Odoo pain→antidote→witness P1–P12).
  **⚠ HELD behind the FREEZE GATE** (live-Odoo diff-oracle pass, LENS_FAMILY §FREEZE GATE) — run that gate FIRST,
  or get an EXPLICIT user lift. Do not start desktop chrome before the gate clears.

## Rules (non-negotiable)
- Each worker `isolation: 'worktree'`; EDIT ONLY `bim-ootb/erp/<new lens files>` + `bim-compiler/scripts/poc_*.js`.
- NEVER edit (READ-ONLY): backend (`scripts/erp_kernel.js`, `kernel_ops.js`, DataSource, shard gen), overlays
  (`*_overlay.js`, `*_ops.json`, the Tour `help_*` — consume by key), other lanes' specs
  (`IDEMPIERE_RECORD_PANEL.md`, `IDEMPIERE_TOUR_GUIDE.md`, `BACKEND_LANE_S2.md`), seam/common docs
  (`ENGINE_CONTRACT.md`, `UI_OVERLAY_GOVERNANCE.md`, `CONCURRENT_LANES_ROADMAP.md`, `TourGuideHostContract.md`).
- KEY by AD ids. ADOPT the frozen `window.IdmpHost` (do NOT mint a rival global). Writes stay `// TODO(STEP-0 seam#1)`
  until backend **C0** lands + the host injects `dispatch`/`ctx` (N3, blocked — `prompts/BACKEND_LANE_S2.md` Task 1).
- Each worker produces a `§`-witness under `build/erp/` (or `bim-ootb/erp/tests/`) and READs the log before concluding.
  NO deploy (EXPLICIT GO). Integrate by key; merge into lane 3 only — never edit another worker's chrome.

## HELD, do not build
- N3 writes-live (until backend C0). · Desktop F3–F6 (until the FREEZE GATE). · Anything that DEFINES exposed-globals
  (the host contract is FROZEN as `IdmpHost` — adopt it, never re-author).
