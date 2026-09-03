# ⚠ DO NOT REMOVE — DELEGATED SESSION: "BIM ⇄ ERP round-trip — RETHINK the handoff (message-first, not DB-overlay)"
# Scope: review the ORIGINAL intent of the viewer⇄iDempiere seam, then decide whether the "viewer pushes a
#   Project Order → click the link → it appears in iDempiere" round-trip should keep the OPFS DB-overlay
#   handoff or move to a message-passed, transient (never-cached) handoff. This card is a DECISION + (if
#   approved) a refactor. NON-INVENT. Witness-led (whitebox §-log first, Playwright only for wiring). Read the
#   log after every run. GO before deploy. Surface = bim-ootb/viewer + bim-ootb/erp.
#
# DO NOT confuse with sibling cards: BIM_EMBED_WINDOW_SESSION.md (the embed/highlight seam, B0–B5) is the
#   PARENT; this card is the round-trip/handoff slice that the live bug exposed. Keep edits additive; the
#   in-place-CRUD session + others share idempiere.html.

## §WHY THIS CARD (the trigger — user report, 2026-06-19)
Live symptom: viewer Find › ERP push now shows the green "open ↗" deep-link (re-landed PR #395), but clicking
it opens iDempiere and **the Project Order does NOT appear**. User intuition (correct): "ERP side may have
broken records that block it — clear the DB." User's deeper question: *"should we rethink the approach — not
introduce machinery in each page, but send messages to the other side to just react (e.g. highlight)?"*

## §ROOT CAUSE (proven this session — W-BIM-ROUNDTRIP headless)
The round-trip CODE is correct — a FRESH browser round-trips fine: `§BIM_OVERLAY rows=6` (C_Project 990000,
client 11), `§IDEMPIERE-DEEPLINK record=990000 landed idx=2 of 3`. The LIVE failure is **stale ERP browser
state**, caused by a design smell:
  · The viewer push writes the folded order to OPFS `bim_analysis/bim_project_orders.db` (PK band ≥ 990000).
  · `erp/bim_orders_overlay.js apply()` overlays that band onto the live ad_seed.db at boot with **INSERT OR
    IGNORE** (keyed on PK).
  · BUT `erp/idempiere.html` PERSISTS the *mutated* db back into the idb seed cache `ad_seed_v16` on several
    paths — **line 1001** (general persist), **701** (shard install), **742** (genesis). That bakes the
    overlaid C_Project 990000 INTO the cache.
  · Next boot loads the polluted cache → the overlay's INSERT OR IGNORE sees 990000 already present → **skips
    the new push** → the ERP shows the OLD/broken order (or nothing). proj_fold always allocates PK 990000
    (band floor), so every push collides with the cached one.
Deploy caveat also seen: the viewer often runs as the GH-Pages page with the building DB streamed from the OCI
bucket — same-origin for the page, so OPFS + the relative `../erp/` deep-link are fine. (If the viewer PAGE
itself were ever served from OCI, `../erp/idempiere.html` 404s and OPFS is cross-origin — out of scope, note it.)

## §ORIGINAL INTENT (what the seam was meant to be — cite, don't re-derive)
  · `prompts/BIM_EMBED_WINDOW_SESSION.md §THE SEAM` (lines ~41-57): **host-agnostic iframe + a small postMessage
    contract `{load, ready, highlight, focusRecord}` — "Embed via iframe + a small postMessage contract rather
    than inlining one app's stack into the other."** i.e. MESSAGE-FIRST was always the doctrine.
  · `docs/BIMtoERP.md §B` write-path: the viewer push writes OPFS `bim_project_orders.db`; the ERP overlays the
    delta at boot (`bim_orders_overlay.js`, witness `tests/poc_bim_overlay.js`). This DB-file handoff predates
    the embed seam and is the heavier, stateful path that now bites.
  · B3 (LIVE, PR #369) already does cross-highlight purely by postMessage (ERP↔iframe `{bim:highlight}` /
    `{bim:focusRecord}`) — NO DB. That is the model to extend.
The TENSION to resolve: ephemeral *reactions* are message-passed (good, B3); the *persistent record* round-trip
uses a DB-file overlay that gets cached and goes stale.

## §THE RETHINK (the decision this card owes)
Principle the user is pushing (agree): **don't grow per-page machinery; pass messages and let the other side
react.** Apply it precisely:
  · EPHEMERAL react (highlight element / focus row / show a transient drawer) → **message only, no store.**
    Already done in B3 — extend, never add a DB for these.
  · PERSISTENT record (the pushed Project Order must render in iDempiere's native C_Project window) → iDempiere
    renders from its store, so the rows MUST exist in the ERP's in-memory db. A message can CARRY them, but the
    ERP must materialize them. **The fix is to keep those rows TRANSIENT — never persist the BIM band into the
    seed cache** (that persistence is the whole bug). Two candidate handoffs:
      (A) keep OPFS-read-once, but (i) reconcile the band each boot [the band-fix below] AND (ii) STOP baking
          BIM rows into `ad_seed_v16` (exclude PK ≥ 990000 from every `idbPut('ad_seed_v16', db.export())`).
      (B) drop OPFS entirely: viewer push → **BroadcastChannel('bim_*')** (same-origin; the viewer already runs
          a BroadcastChannel for 4D — `viewer/main.js`) carries the folded order; the ERP applies it IN MEMORY
          only and lands on it. Removes OPFS + cache-overlay + the bug class. Wrinkle: the deep-link opens the
          ERP in a NEW TAB *after* the push, so a late subscriber misses a one-shot broadcast → need a tiny
          handshake (ERP boots → `postMessage/BroadcastChannel {bim:erpReady}` → viewer replies with the order).
RECOMMENDATION (author's lean, for the new session to confirm with the user): **(c) ship the band-fix now as a
safety net, then do (B) the message-based transient round-trip** and retire the OPFS/cache-overlay path. (B) is
the cleanest expression of the user's message-first principle; (A-ii) is the minimum that stops the bleeding if
(B) is deferred. Whichever: the invariant is **the BIM band is never persisted into ad_seed_v16.**

## §CURRENT STATE — DO NOT REDO
  · B3 cross-highlight ✅ LIVE (PR #369, erp sw v715 / viewer v668; W-BIM-HIGHLIGHT 15/15) — message-based, keep.
  · Find › ERP deep-link ✅ RE-LANDED LIVE (PR #395, navigate_find?v=39) — was built on stale unmerged branch
    `feat/find-erp-deeplink` (8e20226), re-applied. The link itself is correct; it surfaces `r.projectId`.
  · Push status + audio ✅ LIVE (PR #401, navigate_find?v=40, viewer sw v670; sfx erp_pushed/erp_reject).
  · `bim_orders_overlay.js` + the idb-cache persist (idempiere.html 701/742/1001) are the SMELL to fix.
  · WITNESS written this session: `erp/tests/poc_bim_roundtrip_live.js` (W-BIM-ROUNDTRIP) — full one-origin
    round-trip (shared OPFS): viewer push → ERP deep-link → asserts overlay rows + deeplink-landed. Proved the
    code path correct (fresh) and is the regression harness for whichever handoff is chosen. Stage a small
    building (`SampleHouse_extracted.db`) into `viewer/buildings/` to run it.
  · BAND-FIX (staged this session in worktree /tmp/wt-rt, NOT committed/deployed — re-apply if keeping OPFS):
    `bim_orders_overlay.js` add `clearBimBand(dstDb, tables)` (DELETE per-table WHERE pk ≥ BIM_BASE via _pk),
    call it in `apply()` before `overlayRows` (gated on OPFS present), log `§BIM_OVERLAY rows=N cleared=M`.
    Makes the LATEST push win. (Full diff was captured in the session transcript.)
  · A `?resetdb=1` escape hatch (clear `ad_seed_v16` + OPFS bim store, then fresh fetch) was discussed, not
    built — the user's literal "clear DB" ask; cheap, add if (A) chosen.

## §PLAN (R→E→V — confirm the decision FIRST, then build)
  R0 — CONFIRM with the user: (A) band-fix only, (B) message round-trip, or (c) both [recommended]. One question,
       no menu. Re-read original intent (§ above) so the choice is grounded, not re-litigated.
  R1 — REVIEW: read `docs/BIMtoERP.md §B`, `bim_orders_overlay.js`, idempiere.html persist sites (701/742/1001),
       `viewer/main.js` BroadcastChannel('bim_4d') idiom, `viewer/proj_fold.js` (PK 990000 band). Confirm where
       BIM rows leak into the cache.
  E — IMPLEMENT the chosen path. INVARIANT (both paths): BIM band (PK ≥ 990000) is NEVER written into
       `ad_seed_v16` — exclude it from every `db.export()`-to-cache, OR keep it in a side store re-applied each
       boot. If (B): viewer broadcasts the fold; ERP applies in-memory + lands; add the ready-handshake.
  V — WITNESS W-BIM-ROUNDTRIP (extend `poc_bim_roundtrip_live.js`): (1) push → open deep-link → record appears;
       (2) push AGAIN with a DIFFERENT scope/building → the deep-link shows the NEW order, not the stale one
       (the exact regression that bit live); (3) reload the ERP → the seed cache contains NO BIM band (PK ≥
       990000) → proves rows stay transient. 0 pageerrors. Then deploy (clean /tmp/wt-* off origin/main, sw
       bump + KEEP-BOTH precache, auto-merge, verify it lands).

## §REVERT-SAFETY (user Q 2026-06-19: "is reverting all we did easy? won't it impact present Project Order creation?")
SHORT ANSWER: **revert is easy AND does NOT touch Project Order CREATION.** The creation path — viewer `> ERP`
→ `viewer/proj_fold.js foldProjectOrder` → C_Project tree → OPFS `bim_project_orders.db` persist → ERP boot
overlay (`bim_orders_overlay.js`) — is ALL PRE-EXISTING (PR #316 wiring, #349 finance). **This session changed
NONE of it.** Everything we shipped is ADDITIVE surfacing/feedback layered on top:
  · PR #369 (B3) — cross-highlight contract: `viewer/main.js` (message listener + `_bimHighlight`/`_bimPostFocus`),
    `viewer/picking.js` (one emit line, EMBEDDED only), `viewer/viewer.html` (`.bim-embedded` CSS only),
    `erp/bim_panel.js` (post/isOpen/onFocusRecord), `erp/idempiere.html` (helpers `_bimClassForRow`/
    `_bimOnFocusRecord`/`_bimMaybeHighlight` + a `_bimMaybeHighlight(tab,rec)` PREFIX on two row-click handlers
    + boot wiring + `__bimClassProbe`). All additive — removing them restores the prior row-click verbatim.
  · PR #395 — Find›ERP deep-link: `viewer/navigate_find.js` (`#find-erp-open` anchor + CSS + `_bimClassForRow`?
    no — the `elErpOpen` href set in `_pushToErp`'s persist `.then`). Pure surfacing of `r.projectId`; the push
    itself is untouched.
  · PR #401 — push status + audio: `viewer/navigate_find.js` (`_pushSfx`/`_pushReject` wrappers around the
    EXISTING status assignments) + `viewer/sfx.json` (2 added rows) + sw bumps.
  · NOT deployed: the `clearBimBand` band-fix (staged only).
WHAT A REVERT REMOVES (creation unaffected): the green "open ↗" link, the push audio/status cues, the 3D↔ERP
cross-highlight. The viewer still creates the Project Order exactly as before; iDempiere still overlays it at
boot exactly as before (incl. the stale-cache bug — reverting does NOT fix that; it just removes our additions).
HOW TO REVERT CLEANLY (do NOT blind `git revert` the squashes — `idempiere.html` is a shared conflict-magnet
now touched by #366/#396/#406): revert SURGICALLY per feature by grepping the markers and deleting the additive
hunks — `§B3`/`_bim*`/`__bimClassProbe`/`bim-embedded`/`find-erp-open`/`_pushSfx`/`erp_pushed`/`erp_reject`.
KEEP one thing even on revert: the `_bimClassForRow` `SELECT value AS v` column-alias is a generic sql.js
correctness fix, harmless and unrelated to the round-trip — no need to undo it (and it only matters if B3 stays).
DECISION FOR THE NEW SESSION: confirm with the user whether the rethink should (a) build forward on the current
additions, or (b) revert the additions first then rebuild message-first. Author's lean: build forward — the
additions are clean and the only real defect is the cache-persistence smell (§ROOT CAUSE), which is independent.

## §STARTUP READS
  · this card · `prompts/BIM_EMBED_WINDOW_SESSION.md §THE SEAM` + §B3 + §B5-PLAN · `docs/BIMtoERP.md §B` ·
    `erp/bim_orders_overlay.js` · `erp/idempiere.html` (boot 760-835, deeplink 1080, persist 701/742/1001) ·
    `viewer/navigate_find.js` `_pushToErp`/`_ensureErpDb`/`_persistErpDb` · `viewer/main.js` BroadcastChannel ·
    `erp/tests/poc_bim_roundtrip_live.js`. Oracle for AD facts: docker exec postgres psql -U adempiere -d idempiere_test.

## §STRIP-1 SPEC (2026-06-19 — user GO: "strip that BIM tab / embed out, check no other impact left")
DECISION (user, this session): the BIM-in-iDempiere EMBED (the "Project Order BIM tab" — 3D model docked
inside the ERP via "Open Model" + row↔3D cross-highlight, B0–B5) is NOT wanted. Retire it. KEEP the Find→ERP
Project-Order link untouched (separate; OPFS round-trip + its stale-cache bug left as-is for now). KEEP the
cross-tab zoom (Connect 'selection') — that is the chosen go-forward seam.
REMOVE (ERP side, self-contained — touches nothing kept):
  · erp/bim_embed.js + erp/bim_panel.js (whole files).
  · erp/idempiere.html: script tags (498–500); boot ensureSeedBimSets (794–796); boot BimPanel.onFocusRecord
    (797–799); the function block _mountBimAffordance/_bimClassForRow/_bimMaybeHighlight/_bimOnFocusRecord/
    _bimFlashRow/__bimClassProbe (1693–1781); the `_bimMaybeHighlight(tab,rec);` prefix on the two row-click
    handlers (1877, 1930); the `_mountBimAffordance(tab);` call in refreshForm (2714).
  · erp/sw.js: drop bim_embed.js + bim_panel.js from PRECACHE_ASSETS; bump CACHE_VERSION v719→v720.
NO-IMPACT CHECK: nothing in erp/ references BimPanel/BimEmbed/_bim*Affordance after the cut (system_tenant.js
only NAMES BimEmbed in a comment — not a dep). Viewer-side EMBEDDED support (?embedded=true chromeless mode,
the host→iframe bim:highlight listener, picking.js emit, viewer.html .bim-embedded CSS) goes INERT once nothing
sends ?embedded=true (only BimPanel.open did) — left for STRIP-2 because it is entangled with the kept zoom core
(_bimHighlight) and is cleaned together with the zoom rewire.

## §FINAL-DESIGN (user decree 2026-06-19 — the go-forward, supersedes the OPFS round-trip ambition)
Cross-surface comms = TWO incumbent mechanisms, identity-only, nothing persisted:
  · COLD (target not open): a Zoom LINK launches/serves notice to the Viewer (URL carries the item id) — same
    trick Find→ERP used, reversed.
  · WARM (both open): a shared BroadcastChannel correlation — whatever either side TOUCHES highlights the
    corresponding item in the other. Bidirectional & symmetric:
      - click an issue/line in the ERP Project Order → that element is HIGHLIGHTED in the Viewer.
      - select an item in the Viewer → the matching Project line (if one exists) comes into FOCUS in the ERP.
This is the incumbent Connect 'selection' bus (BroadcastChannel('connect:v1')) + the URL cold-launch. User cap:
"serves good enough optics, do not overwhelm further — diminishing returns." So: this and no more.

## §SCENARIO-MAP (2026-06-19 — the original embed intents B0–B5, re-tackled in the new framework)
Two SEPARATE windows (Viewer, iDempiere), each a full app, talking by message. WARM = both open →
BroadcastChannel correlation (touch here → twin lights there). COLD = other not open → a URL zoom-link
opens/focuses it on that item. Only an item-ID travels; nothing stored, nothing fused.
  · See the model for a Project Order (was: viewer docked in iDempiere) → a "View in 3D ↗" link opens the
    FULL Viewer tab on that building. Better optics, zero embed code.
  · Cross-highlight (was B3, line↔3D) → THE NEW CORE: click a line/issue in ERP → 3D lights it; pick in 3D →
    focus the owning line in ERP. Across two tabs, over the channel. Bidirectional.
  · Generalize to any model-bearing record (Restaurant/WH/Farm/Factory/Asset, AD-declared not window-id) → KEPT,
    but it lights the LINK not a panel. The "does this record point to a model?" AD detector is the one small
    piece the strip removed that the new path wants back (lightweight: light a link, not mount a panel).
  · Direction-neutral (viewer-in-ERP AND ERP-in-viewer) → PURER: no host/guest, two peers, each shouts+links the
    other. = the user's "multi-lateral".
  · Work-scope (act only on owned lines, others read-only, prices redacted) → DISSOLVES into each tab's own
    rules: the ERP tab is real iDempiere — native role/client-org gating + field-level price hide already do it.
  · Action camera (offset zoom so the floating panel doesn't hide the element) → GONE, problem no longer exists
    (no panel over the canvas).
  · PO data drawers (supplier/due-date/storage in the embed) → Viewer Find already shows indicative cost; deep PO
    detail lives in the ERP tab (focus the element → ERP navigates to its PO record). No drawers to build.
Useful added scenarios: (1) issue triage round-trip (ERP problem-line → 3D zoom; 3D clash → focus line to
action); (2) Find→cost→line; (3) Modeller⇄Viewer authoring sync; (4) two-monitor split (OS does the window
juggling the panel faked); (5) shareable deep-link (paste a zoom-link into chat / an iDmp note → lands there).

## §LIFETIME (user-agreed 2026-06-19 — the comm's scope + teardown)
  1. IDENTITY-GATED, NOT SCOPE-GATED: the correlation matches on GUID / product IFC-class, so it fires only when
     a genuine twin exists — roaming beyond the launched Order (e.g. onto a Sales Order line that shares a
     building element) STILL correlates (a feature); anything with no counterpart silently no-ops. No scope wall,
     no per-message filter machinery. The boundary is built into the data: a generic order with no BIM-linked
     product never correlates.
  2. DELIBERATE OFF = the toggle ("zoom is on"). One click → both sides quiet, even with both tabs open.
  3. AUTO-TEARDOWN = a tab closing (free): the BroadcastChannel lives inside each document and dies with it; the
     surviving tab's shouts reach no one = harmless silence. The user's "stop when any tab is closed" is how it
     already works — no teardown code.
  4. PRESENCE DOT (nicety): reuse the existing Connect ping/ACK heartbeat — partner stops answering → dot goes
     grey, so "the other side is gone" is VISIBLE rather than clicks mysteriously doing nothing. Indicator only,
     not a teardown mechanism.

## §TM-VARIANCE (user scenario 2026-06-19 — schedule variance in Time Machine; a DEDICATED 4D function, NOT the comm)
Self-contained Time Machine (4D) feature — the cross-tab comm is only how it FINDS the order; the variance view
is pure Time Machine. Add ONE icon to Time Machine that pulls budget vs actual from the Project Order IF AVAILABLE
(gated on order-present — no order → icon inert, honest). The 3D scene animates the ACTUAL only. Layout = pick
whichever reads easiest: (a) two gantt/resource blocks side-by-side (budget | actual) + a bottom frame = variance;
(b) all three side-by-side (budget | actual | variance); (c) a single "mash" block with variance overlaid as
indicators on all. OPEN DATA QUESTION to pin when building (NON-INVENT, no fabricated dates): baseline/budget =
the order's planned Phase/Task dates (C_ProjectPhase/C_ProjectTask); ACTUAL-progress source must be identified
from real records before building — do not synthesize. Relates to [[project_time_machine]] + [[project_4d_capture]].

## §NOTE
prompts/ is gitignored (local) — this card does not collide with other sessions' git work. The user's framing
is the north star: **minimal per-page machinery; the seam is a message; persistent data is materialized
transiently, never cached.**
