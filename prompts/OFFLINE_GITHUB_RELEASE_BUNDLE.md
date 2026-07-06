# ✅ SESSION CLOSED 2026-07-05/06 — safe to recall cold, nothing left open for THIS file. Two outcomes:

## Outcome A — installer question: ⛔ SUPERSEDED, no build needed
See `prompts/FRONTEND_LANE_MASTER.md` §NEW BACKLOG "Desktop/mobile installer" for the resolved outcome. This
file's own §WHY RECON FIRST point 3 (why not PWA) turned out to be an unsourced assumption: a background-agent
recon found NO evidence anywhere (git log/PROGRESS.md/docs, both repos) that the existing PWA install
(`viewer/scene.js` §S283) was ever unreliable — it has a real passing `setOffline(true)` reload test. The
user's real want (icon on desktop/mobile, launches app-like) IS what Chromium's native PWA install already
does; the zip-bundle plan below was never built. Kept verbatim below as the record of the recon dialogue, not
as an active spec — do NOT build the zip/Electron/local-server path.

## Outcome B — a REAL bug surfaced along the way, FIXED + independently verified, pushed not merged
User-flagged: "when DBs are loaded in IndexedDB, sometimes it still [goes online]." Root cause: 3 call sites
bypassed the `sw.js` cache-first gateway and hit the network unconditionally even with data already cached —
- `viewer/sw.js`: `sfx.json` was hardcoded network-first ("during tuning" — a stale debug carve-out). Now
  precached like every other config file (`CACHE_VERSION` bumped v740→v741).
- `viewer/streaming.js` (single-DB size check + split-DB meta detect) and `viewer/city.js` (archetype split-DB
  detect): each fired an unconditional network `HEAD` probe before ever checking `A._checkCache()`. Now check
  cache first, only touch network on a genuine miss.

**Fix commit:** bim-ootb `cd36c07` on branch `lane/offline-gateway-leak-fix` (**pushed, NOT merged** — needs a
human merge decision, that's the one thing left outstanding from this whole session).

**Independently re-verified 2026-07-06 (not trusting the fix-session's own recap):** stood up a fresh local
server, ran `tests/witness/witness_offline_gateway_leak.js` myself from a clean checkout of `cd36c07` — real
exit 0, all 3 passes green (`PASS2_SFX_NETWORK_TOUCHED=false`, `PASS3_OFFLINE_RENDERED=true`). Diff reviewed
directly against `68c877e`: matches the claimed fix exactly, minimal (4 files: 3 fixes + 1 new witness), no
stray changes. `.github/workflows/ci.yml` confirmed untouched — an earlier scope-creep (wiring these offline
specs into CI) was self-caught after user pushback and correctly reverted; current branch has zero CI changes.

**Nothing left to verify or decide for Outcome B** — the only remaining action is merging `lane/offline-
gateway-leak-fix` into `main` whenever convenient.

# SESSION PROMPT — Offline-runnable GitHub Release bundle (recon + spec-refine FIRST, not a blind build)

```
# ⚠ DO NOT REMOVE
SCOPE: this REPLACES the earlier "Desktop installer — native Electron/Tauri" framing in
prompts/FRONTEND_LANE_MASTER.md's backlog entry — that framing was this assistant's inference from an
ambiguous question, not the user's real intent. The user's actual answer (2026-07-05 dialogue) is
SIMPLER than a native installer. Read §THE REAL ASK below before doing anything else; do not build
Electron/Tauri packaging, code-signing, or a native uninstaller — none of that is wanted. Read the log
after every run.
```

## §THE REAL ASK (user's own words, 2026-07-05 — do not re-interpret)
- The "installer" is a **fresh build downloaded from a GitHub Release** — a zip of the static app (HTML/JS/CSS
  + vendored libraries), not a native OS installer/uninstaller.
- Once downloaded, the zip can be **passed around offline** — USB stick, email attachment, etc. That's the whole
  offline-distribution story; no app-level install/uninstall flow is needed beyond "unzip it, run it, delete the
  folder to remove it."
- **Why not the existing PWA URL-based install:** convenient, but past attempts showed it does NOT reliably
  guarantee true offline operation (service worker + Cache API population still depends on getting that first
  bootstrap exactly right, and it has failed before). A downloaded zip sidesteps that whole class of failure —
  everything the app's OWN code needs is already on disk before it's ever run.
- **Scope boundary — what stays online:** the buildings landing page / building catalog remains a **remote
  resource**. This bundle is NOT trying to make building data (IFC corpora, sample buildings) available offline
  — users load a building while online, and the existing local save/IndexedDB behavior (already shipped) covers
  working with it afterward. Only the **app code itself** (JS/CSS/HTML, and whatever it currently pulls from a
  CDN at runtime) needs to be guaranteed present without a network fetch.
- "Installer is just all JS and CDN whatever stuff" — i.e., the deliverable is a **static asset bundle with every
  CDN-loaded dependency vendored in**, not a runtime or a packager.

## §WHY RECON FIRST (this is genuinely not settled — do not guess)
1. **Every CDN reference across the app needs a real inventory**, not an assumption: grep `viewer/`, `modeller/`,
   and any HBA/ERP HTML entry points for `<script src="https://...">`, dynamic `import("https://...")`, or CDN
   font/icon links. Likely candidates (verify, don't assume): three.js, sql.js/sqlite-wasm, any CDN-hosted font
   or icon set. Each one either needs a vendored local copy shipped in the zip, or a documented reason it's
   already local.
2. **Does `file://` actually work for this app once unzipped?** Modern browsers restrict `fetch()`/ES module
   imports from `file://` origins in ways that may break loading `.db`/`.wasm` assets or ES modules even with
   everything vendored locally. This needs a real test (unzip a build, disable network, open `index.html`
   directly, see what breaks) before deciding whether the deliverable is "just open the HTML file" or "unzip
   and run a tiny bundled local static server" (e.g., a one-line Node/Python server included in the zip with a
   README). Do not assume either answer — verify.
3. **Reuse the existing release pipeline, don't invent a new one.** `git log` shows a real release process
   already exists (`chore(main): release 1.10.0 (#663)`) — find it, understand what it currently produces, and
   extend it to also attach a zip artifact to the GitHub Release rather than building a parallel packaging path.
4. **No native uninstaller work.** Since this is a folder from a zip, removal = delete the folder. Do not build
   any uninstall tooling; if the recon turns up a reason one is actually needed, stop and ask — don't build it
   speculatively.

## STEPS
1. Recon: inventory every CDN dependency (script tag / dynamic import / font/icon link) across the app's real
   entry points. Log what's found, file:line for each.
2. Recon: test `file://`-origin load of a representative unzipped build with network disabled. Log exactly what
   breaks, if anything (module loading, wasm/db fetch, worker registration, etc.).
3. Decide the serving mechanism from real evidence (raw `file://` open vs. a bundled tiny local static server) —
   name the decision and why, don't default silently either way.
4. Find and extend the existing release pipeline to produce the zip artifact; vendor every CDN dependency found
   in step 1 into the bundle.
5. Verify end-to-end: download the actual GitHub Release zip artifact (not a local build), unzip it on a machine
   with networking disabled, run it, confirm the app loads and functions with no network calls for its own code
   (building-data load remains the one legitimate exception — confirm it fails GRACEFULLY, i.e. a clear message,
   not a silent broken state, when attempted offline).
6. `§` log evidence for every claim (CDN inventory, file:// test result, offline end-to-end run) — no exceptions.

## DONE WHEN
1. Every CDN dependency is either vendored into the bundle or explicitly documented as intentionally excluded.
2. The `file://`-vs-local-server question is answered with real evidence, not assumed.
3. A real GitHub Release zip artifact exists, was downloaded fresh, unzipped, and run with networking disabled —
   witnessed, not just "should work."
4. Building-data-load-while-offline fails with a clear user-facing message, not a silent break.
5. No native installer/uninstaller/Electron/Tauri work was done — if this session finds a real reason one is
   needed after all, STOP and bring that back as a new question, don't build it inline.

## WATCHDOG NOTE
Tracked from `prompts/FRONTEND_LANE_MASTER.md §NEW BACKLOG` (supersedes that entry's earlier "native installer"
framing — see the corrected pointer there). This spec exists because the assistant's first framing of "zero
network on first run" over-scoped to a native-app rebuild the user never asked for — a reminder that a
clarifying question's own multiple-choice options can smuggle in an assumption. Re-confirm §THE REAL ASK against
this file before building anything if there's ever doubt.
