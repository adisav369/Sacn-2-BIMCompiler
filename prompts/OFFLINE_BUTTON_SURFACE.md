# ⚠ DO NOT REMOVE — Scope guard / RESUME CARD: surface the "Make available offline" button
# SCOPE: discoverability + clarity of the EXISTING offline-download affordance in the BIM viewer.
#   This is a UI surfacing task — the engine already exists and works. Do NOT rebuild the caching.
#   Shipping code in /home/red1/bim-ootb/viewer/ ONLY; worktree off origin/main → PR → CI → squash.
# DOCTRINE: §-log first, NON-INVENT, propose the placement/look before shipping (UI iteration —
#   wait for "ok/go" on the visual), bump viewer/sw.js CACHE_VERSION on any sw touch.
# Read first: prompts/UI_PAYLOAD_PERF.md (the parent card — Win #2 PRECACHE-TRIM is DONE, v640),
#   feedback_pill_icon_consistency (clean Lucide icons), feedback_wait_for_permission_ui.

## ▶ WHY THIS EXISTS NOW (context — 2026-06-11)
Win #2 (§PRECACHE-TRIM, viewer sw **v640**) stopped auto-precaching the ~8.9MB IFC/Excel giants on
install. Full offline for those features now comes from EXACTLY TWO paths:
  (1) cache-on-first-use (automatic — import an IFC once online → web-ifc sticks), and
  (2) the EXISTING "download for offline" button.
So path (2)'s discoverability suddenly matters more. Today it's nearly invisible.

## ▶ WHAT EXISTS TODAY (verify line numbers — they drift)
- **The button = a corner-triangle "dog-ear" badge** on the command palette, NOT a labelled control.
  `viewer/scene.js` ~L972–L1026: a 48px CSS triangle, top-right of the palette.
  - **Blue** (`#4fc3f7`) + download-arrow glyph + tooltip "Download · Run Offline" = not yet offline.
  - **Green** (`#4caf50`) + checkmark glyph + tooltip "Installed ✔" = done/offline-ready.
  - Click (blue) → `_startOfflineDownload()` (scene.js ~L1196) → MessageChannel `GET_PRECACHE` to
    sw.js → `_cacheAllAssets()` force-caches the FULL SHELL+DEFERRED set with a progress overlay +
    `§PWA_VERIFY` log. Public alias: `A.startOfflineDownload`.
- **The state** (`_pwaInstalled = _isStandalone || window._pwaAccepted`) only reflects PWA-install,
  NOT whether the deferred giants are actually cached. So "green ✔" can show while web-ifc is still
  un-cached → a user could believe they're fully offline when they're not.

## ▶ THE TASK (surface it — reuse the engine, don't rebuild)
1. **A clear, labelled affordance** in the viewer **Settings panel** (`panels.js _openSettingsPanel`,
   it already has a Storage section ~L1246 showing localStorage bytes). Add an "Offline" row:
     - status line: `Core app: cached ✓ · Full offline (IFC/Excel): not yet | ready`
     - a real button **"Make available offline"** → calls `A.startOfflineDownload()` (existing engine).
   Keep the corner badge too (or retire it — propose which); the Settings row is the discoverable home.
2. **Honest status** — base "Full offline: ready" on whether the DEFERRED_LIBS are actually in the
   Cache (not just PWA-install). Add a tiny `GET_OFFLINE_STATUS` message to viewer/sw.js that reports
   which of DEFERRED_LIBS are cached (sw already has the list), OR check `caches.match` client-side.
3. **Icon** — clean Lucide only (feedback_pill_icon_consistency): `download` for the action, `check`
   for ready. No unicode/emoji glyphs on our surface.

## ▶ WITNESS (name the issue)
`viewer/tests/poc_offline_surface.js` (whitebox + light Playwright for the DOM): assert the Settings
row renders, the button calls `A.startOfflineDownload`, and the status reflects DEFERRED_LIBS cache
state (seed one cached / one not → row reads "not yet"; cache all → "ready"). Emit
`§OFFLINE-SURFACE row=Y button=wired status=<core|full> ` + `§OFFLINE-SURFACE-RESULT PASS/FAIL`.

## ▶ DEPLOY
Worktree off fresh origin/main → propose the Settings-row look (one line) + wait for go → implement →
witness green → PR → CI → squash → bump viewer/sw.js CACHE_VERSION (if sw touched for GET_OFFLINE_STATUS)
→ verify live. ⛔ BLOCKED only if the user must choose: keep-vs-retire the corner badge.
