# S283 — PWA Offline Installer

# !! DO NOT REMOVE — read the log after every run !!
# Scope: Blue-corner install badge, offline-first package, kernel_ops resume, viral share
# Spec-first: this IS the spec. Implement section by section.
# STATUS: Phase 1 DONE — badge, download, install, update check, tests

## Thesis

The installed PWA is not a URL shortcut. It is an offline package: all JS,
the WASM engine, and the current building DB are force-cached before the
install prompt fires. The resulting home screen icon launches a standalone
app that works on a plane — zero network. A Home pill button is the backdoor
back to the online environment.

Every share URL is a potential installer. User A installs → shares → User B
opens the link → sees the blue triangle → installs → shares → viral loop.

Updates are explicit, not silent. The user long-presses the icon, taps
"Check Update", and the app verifies that GitHub Actions CI has passed on
the latest `main` commit before allowing the update. If CI is pending or
failed: "Not Ready, Try Later."

## Architecture

```
User opens viewer URL (GitHub Pages)
  → browses buildings, loads one into scene
  → sees blue triangle badge (top-right of Help panel)
  → hovers: "Download · Run Offline"
  → clicks: full download begins
     ├── all JS files → Cache API
     ├── sql-wasm.wasm → Cache API
     ├── Three.js ESM bundle → Cache API
     ├── current building DB → IndexedDB
     └── icons, manifest, sw.js → Cache API
  → progress bar reaches 100%
  → browser install prompt fires (Android/Chrome/Edge)
  → home screen icon appears

Next launch (offline):
  → standalone window, no browser chrome
  → sw.js serves all JS from Cache API
  → building DB loaded from IndexedDB
  → kernel_ops log replays → exact last state restored
  → Home pill (🏠) → opens GitHub Pages URL in system browser
  → no login, no "open project", no save file
```

## What Was Built

### Phase 1: DONE (commits 808f93c → 597b85d)

**Files changed:**
- `viewer.html` — `beforeinstallprompt` capture (early, top-level)
- `scene.js` — blue/green badge, download flow, CI-gated update, share
- `sw.js` — GET_PRECACHE + SKIP_WAITING message handlers, precache fix
- `panels.js` — Home pill standalone backdoor
- `manifest.webmanifest` — shortcuts, start_url fix to viewer.html
- `icons/icon-192.png` + `icon-512.png` — lightbulb-box icon
- `index.html` — landing page icon between BIM/ERP

**Tests:**
- 104 whitebox (test_s283_pwa_install.js)
- 9 Playwright (s283-pwa-install.spec.js) — CDP installability = 0 errors
- 127 S282b tests unbroken

### Badge States

| State | Color | Icon | Tooltip | Click action |
|---|---|---|---|---|
| Not installed | Blue #4fc3f7 | Download arrow | "Download · Run Offline" | Start offline download |
| Installed/standalone | Green #4caf50 | Checkmark | "Installed ✔" | Check Update |

### CI-Gated Update Flow

1. Gets local version from SW via MessageChannel
2. Fetches remote sw.js with `cache: 'no-store'` → parses CACHE_VERSION
3. If same version → "You are up to date"
4. If newer → calls GitHub Actions API (`status=success`)
5. If CI not green → **"Not Ready, Try Later"**
6. If CI green → fetches commit messages → shows changelog
7. User sees bullet list of changes + **OK** / **Cancel** buttons
8. OK → SKIP_WAITING + re-register SW + reload
9. Cancel / Esc → close overlay

### Known Constraints

**Chrome engagement heuristic:** `beforeinstallprompt` requires 2 visits
with 30s gap. First visit captures the listener but Chrome won't fire the
event until engagement threshold is met. The caching still works — user
gets offline capability immediately. The install prompt is a bonus on
subsequent visits.

**Firefox:** No PWA install support at all. SW caching works (offline in
tab), but no home screen icon, no standalone window. This is a Firefox
platform limitation since 2021.

**iOS:** No programmatic install. Guided 3-step overlay (Share → Add to
Home Screen → Add). Assets are pre-cached before the guide shows.

## What's Next (Phase 2+)

### TODO: Fix fallback messages
- Firefox: "Cached for offline use. Firefox doesn't support app install — use Chrome or Edge for home screen shortcut."
- Chrome no-prompt: "All files cached! Look for the install icon (⊕) in your address bar, or close and revisit in 30 seconds."
- Badge tooltip on Firefox: "Cache for Offline Use" instead of "Download · Run Offline"

### TODO: Long-press on green badge → Re-download
- Green click → Check Update (current)
- Green long-press → Re-download (for evicted storage or deleted app)
- Same for OS shortcut long-press

### TODO: State resume via kernel_ops (Phase 2)
- On standalone launch, replay kernel_ops from IndexedDB
- Fallback chain: kernel_ops → share URL params → cached building → offline notice
- Toast: "Resumed · N actions replayed"

### TODO: Viral share (Phase 4)
- Share URL = installer: recipient sees blue triangle
- Web Share API on mobile, clipboard fallback

### TODO: meta tag update
- `apple-mobile-web-app-capable` → `mobile-web-app-capable` (Chrome deprecation warning)

### TODO: favicon.ico
- 404 at root and viewer level — add a favicon (lightbulb-box icon resized)

## Platform Matrix

| Feature | Android Chrome | Desktop Chrome/Edge | iOS Safari | Firefox |
|---|---|---|---|---|
| Blue triangle badge | Yes | Yes | Yes | Yes |
| `beforeinstallprompt` | Yes | Yes | No | No |
| Standalone window | Yes | Yes | Yes | No (tab) |
| Offline JS (Cache API) | Yes | Yes | Yes | Yes |
| Offline DB (IndexedDB) | Yes | Yes | Yes (persist) | Yes |
| kernel_ops replay | Yes | Yes | Yes | Yes |
| Persistent storage | Always (PWA) | Always | Request | N/A |
| Long-press shortcuts | Yes | Yes | No | No |
| Web Share API | Yes | Yes | Yes | No |
| CI-gated update | Yes | Yes | Yes | Yes |

## Witness Claims

| ID | Claim | Proof |
|---|---|---|
| W-PWA-1 | Blue triangle visible on Help panel | §PWA_BADGE rendered color=#4fc3f7 |
| W-PWA-2 | Badge persists across Help opens | Playwright S283.7 |
| W-PWA-3 | All JS force-cached before install | §PWA_CACHE count + Playwright S283.3 |
| W-PWA-4 | Building DB cached to IndexedDB | §PWA_CACHE building=X |
| W-PWA-5 | Install prompt fires after cache | §PWA_INSTALL prompt captured (early) |
| W-PWA-6 | iOS guide overlay shown | §PWA_INSTALL ios_guide_shown |
| W-PWA-7 | persist() requested | §PWA_PERSIST granted=true/false |
| W-PWA-8 | Home pill opens online hub | §PWA_HOME opened |
| W-PWA-9 | Update checks CI green | §PWA_UPDATE ci=success |
| W-PWA-10 | Update blocked when CI not green | §PWA_UPDATE ci=no_success_runs |
| W-PWA-11 | Changelog shown with OK/Cancel | §PWA_UPDATE changelog=N items |
| W-PWA-12 | CDP installability = 0 errors | Playwright S283.8 |
| W-PWA-13 | Manifest detected by Chrome | Playwright S283.9 |
| W-PWA-14 | Share URL triggers share | §PWA_SHARE native/clipboard |

## Chrome WebGL Issue (User's Machine)

Not S283 related. Chrome has blacklisted the GPU driver on the dev machine:
```
GL_VENDOR = Disabled, GL_RENDERER = Disabled, Sandboxed = yes
```
Fix: `chrome://flags/#ignore-gpu-blocklist` → Enable → Relaunch.
Or check `chrome://gpu` to diagnose. This blocks ALL WebGL rendering,
not just BIM OOTB.
