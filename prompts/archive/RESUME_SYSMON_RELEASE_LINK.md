# ⚠ DO NOT REMOVE — Scope & Standing Rules (honour until ✅ DONE)

**Scope (ONE bounded task):** make the **System Monitor → Release** row tell the truth and link to provenance.
Today it reads **"(uncontrolled)"** even on a deployed build, and there is no link. The user wants it to
**reflect the last deployed version (e.g. `v752`) — "what is last done, ie yours" — with a link to the GitHub
release/commit page.** ERP app surface: `~/bim-ootb/erp/` (work in a `/tmp/wt-*` worktree off fresh
`origin/main`; the shared-tree hook blocks the checkout). **Log Mandate · Non-invent · Deterministic.**
**Deploy discipline:** bump `erp/sw.js` `CACHE_VERSION` + precache any new asset; verify live `sw.js` reports it.

---

## §DIAGNOSIS (measured 2026-06-24, live red1oon.github.io/bim-ootb/erp, sw v752)

**Why Release = "(uncontrolled)":** `system_monitor.js → swVersion()` reads ONLY
`navigator.serviceWorker.controller` and posts `{type:'GET_PRECACHE'}`. Right after a SW update the new
worker is *installed but not yet controlling the page* → `controller` is **null** → `swVersion()` returns
null → `gather()` sets `release = ver || '(uncontrolled)'`. (The SW *does* answer `GET_PRECACHE` with
`{version: CACHE_VERSION}`; the page just isn't controlled at open time. Confirmed on Firefox 151.) The
panel field is `panelHTML`'s `row('Release', '<b>'+esc(d.release)+'</b> · dictionary folded …')`.

**Link target reality:** **no GitHub Releases and no `vNNN` git tags exist** (`gh release list` empty,
`git tag -l 'v7*'` empty). The only provenance for a `vNNN` is the **squash-merge commit** whose message
carries "sw vNNN" (e.g. `v752` = `ec7ca0e` "… (#513)"). So "link to the GH release page" needs a decision.

## §FIX A — Release shows the real version (not "(uncontrolled)")
Make `swVersion()` robust to the post-update window. Pick the cheapest that works:
1. Prefer `navigator.serviceWorker.ready.then(reg => reg.active || reg.waiting || reg.installing)` and
   `postMessage` to THAT worker (not just `.controller`) over a MessageChannel; keep the 800 ms fallback.
2. **Belt-and-braces (recommended):** at deploy, stamp a tiny `erp/version.json`
   `{ "version":"v752", "sha":"ec7ca0e", "pr":513, "date":"2026-06-24" }`; `swVersion()` falls back to
   `fetch('version.json')` when the SW path yields null. `version.json` is the single source of truth the
   row reads — deterministic, no SW-timing race. (Add it to the deploy step + precache.)
- **`§`-log:** `§SYSMON-RELEASE version=v752 source=sw|version.json controlled=Y/N` — never "(uncontrolled)"
  on a build that actually shipped. Falsifier: a deployed build that still renders "(uncontrolled)" = FAIL.

## §FIX B — AUTO-CUT real GitHub Releases + link to them  ✅ DECISION LOCKED (user 2026-06-25: "make it auto ie cut real GH releases")
The Release row links to a real GitHub Release page `…/releases/tag/vNNN`, and the release is **cut
automatically** at deploy — no manual step. Build it so a new `vNNN` is impossible to ship without its release.
- **Auto-cut mechanism (make it a deploy step, not a human chore):** wherever the sw `CACHE_VERSION` bump
  lands on `main` (the squash-merge), a step tags + creates the release from that commit. Two viable homes —
  pick the one that fits the repo's deploy flow:
  1. **GitHub Action** on push-to-main: if `erp/sw.js` `CACHE_VERSION` changed since the last tag, run
     `git tag vNNN <sha> && gh release create vNNN --target <sha> --title "vNNN" --notes "<merge-commit subject + body>"`.
     Idempotent: skip if the tag already exists. This is the truest "auto".
  2. **In the deploy script** (`scripts/safe_gh_deploy.sh` or the repo's publish path): after a successful
     gh-pages publish, cut the release for the new `CACHE_VERSION`. (Read `prompts/DOCS_DEPLOY_POLICY.md` first —
     do NOT disturb the no-shrink seatbelt.)
- **The notes ARE the changelog:** populate the release body from the merge commit (subject + body) so the
  release page literally shows "what was last done, ie yours" (e.g. v752 = the 4 field-health widgets + D2).
- **Row link:** `swVersion()` yields `vNNN` → render `<a href="https://github.com/red1oon/bim-ootb/releases/tag/vNNN">vNNN</a>`.
  Stamp `erp/version.json {version, sha}` at deploy as the deterministic source for the row (see §FIX A.2).
- **Backfill:** cut releases for the already-shipped tags too (at least the current `v752 = ec7ca0e (#513)`)
  so the link isn't a 404 on first ship.
- **`§`-log:** `§SYSMON-RELEASE-LINK href=…/releases/tag/vNNN kind=release autocut=Y`. Falsifier: a deployed
  `vNNN` whose release page 404s = FAIL (the auto-cut didn't fire / wasn't backfilled).

## §FIX C (secondary, same surface) — Op-log DB widget reads "n/a no db handle" even logged in
`field_health.js db_size_gauge` folds `window.ERP.opDb`, but at monitor-open `ERP.opDb` is null (it's
lazy-init'd in `rule_fold.js ensureOpLog()` only when a fold/kanban write happens — see `_opDb = global.ERP.opDb`
at `erp/rule_fold.js:79`). FIND the live op-log handle for the current tenant and pass it (or call
`ensureOpLog` read-only) so the gauge shows real MB vs the 200 MB ceiling (the reassuring B1 optic). If
genuinely absent pre-fold, keep `n/a` (non-invent) but improve the detail string to say "no op-log yet —
post a doc to populate". **`§`-log:** `§MON-DB_SIZE_GAUGE value=<mb> …` shows a number once a tenant has ops.

## §FIX D — update the UserGuide (user req 2026-06-25: "also to update the UserGuide")
Document the System Monitor's new **Field health · paradigm vitals** widgets in **`docs/ERPUserGuide.md`**
(the end-user "what am I looking at" doc; same place §What-if / §Author 4D-5D were published). Add a
**§System Monitor** section:
- How to open it (login-card info panel → "System Monitor"; also in-session).
- The 4 widgets + what each tells the SysAdmin: **Field errors** (G2 — silent failures surfaced),
  **Durability** (A1 — never marks an unsynced op "safe"), **Op-log DB** (the in-memory ceiling, MB vs 200 MB),
  **Environment** (OPFS/IDB). One line on the classic-vs-paradigm reframe (no server → watch the paradigm's vitals).
- The Release row = the live deploy version, linking to its GitHub Release (after §FIX A/B land).
- **Figure:** use the user's live screenshot (`~/Pictures/Screenshots/Screenshot from 2026-06-25 00-37-39.png`
  — the FIELD HEALTH group). Copy into the docs assets dir the guide uses, reference it. Publish docs ONLY via
  `scripts/safe_gh_deploy.sh` (READ `prompts/DOCS_DEPLOY_POLICY.md` + `DOCS_DEPLOY_GUARD.md` — no bare gh-deploy).

## §DONE =
- Release row shows the real `vNNN` (proof: open the live monitor post-deploy → not "(uncontrolled)").
- The version links to a real GitHub Release `…/releases/tag/vNNN`, and releases are **auto-cut at deploy**
  (a new `vNNN` can't ship without its release; `v752` backfilled).
- ERPUserGuide §System Monitor published with the 4-widget explanation + the live figure.
- (Stretch) Op-log DB gauge shows MB for a logged-in tenant with ops.
- `field_health.js` here mirrors `bim-compiler/build/erp/system_monitor.js` (the witnessed engine) — keep in
  sync; the app copy is a rename (`ERP.FieldHealth`). System Monitor panel = `erp/system_monitor.js`
  (`window.SystemMonitor`), the row is in its `panelHTML`. Deploy via worktree + sw bump; verify live.

## Context pointers
- Landed this arc: bim-ootb PR #513 (sw v752) = the 4 field-health widgets + D2 default stamping.
  See `prompts/SYSTEM_MONITOR_WIDGETS.md` + memory `project_s1_discovery_spikes.md`.
- Monitor opened from the login-card info panel `#idmp-sysmon-link`; also reachable in-session.
