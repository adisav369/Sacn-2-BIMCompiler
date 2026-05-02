# BIM OOTB — Mobile & Browser Deployment
> **Foundation:** [BIM_Designer_Browser.md](BIM_Designer_Browser.md) · [4D5DAnalysis.md](4D5DAnalysis.md)

<div class="bim-banner" markdown>
<b>BIM in your pocket.</b> One HTML viewer. Two SQLite DBs per building. Works on desktop and mobile — same URL, same code. No server, no install, no account.
</div>

**Version:** 0.2 (2026-04-27)
**Status:** LIVE — 37 buildings streaming from OCI Object Storage
**Depends on:** `deploy/sandbox/index.html` + 18 JS modules, per-building DBs

---

## 1. Try It Now — Zero Install

Open any link below in your browser (desktop or phone):

| Link | What | Download | Mobile? |
|------|------|----------|---------|
| [**LIVE — Landing**](https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-live/o/index.html) | 37 buildings, 1M elements — pick a building to stream | ~5-10 MB/building | Yes |
| [**LTU A-House (126K)**](https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-live/o/sandbox/index.html?db=https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/buildings/LTU_AHouse_extracted.db&lib=https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/buildings/LTU_AHouse_library.db) | 126K elements — stress test, streams in seconds | ~68 MB | Yes |
| [**DEV — Landing**](https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-dev/o/index.html) | Dev/staging — latest changes under test | same | Yes |

**OCI Object Storage** (Malaysia West 2 Kulai). Always Free tier. No login required.

---

## 2. DIY Downloader — One-Click Self-Host

The fastest way: click **DIY Downloader** in the About box of the [live viewer](https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-live/o/index.html). It generates a platform-specific install script that checks prerequisites, downloads the viewer (~49MB), and starts a local server — all automatic.

Installs to `~/bim-ootb/` (Mac/Linux) or `C:\Users\{you}\bim-ootb\` (Windows).
Includes all templates, rates, 15 language packs. Buildings load from OCI cloud.
See [About — DIY Self-Host](https://red1oon.github.io/BIMCompiler/AboutMore/#diy-self-host) for full details.

## 3. Run It Locally — Manual (3 Steps)

You only need **Python** (built-in on Linux/Mac, easy install on Windows) and the repo.

```bash
# 1. Clone the repo
git clone https://github.com/red1oon/BIMCompiler.git
cd BIMCompiler

# 2. Start a local web server (Python's built-in, no install needed)
cd deploy
python3 -m http.server 8080

# 3. Open in your browser
# http://localhost:8080/sandbox/landing.html
```

That's it. The landing page lists all buildings. Click one — it loads from the `buildings/` folder.

**What's in the box:**

| Folder | Contents | Size |
|--------|----------|------|
| `deploy/sandbox/` | HTML viewer + 18 JS modules | ~200 KB |
| `deploy/buildings/` | 31 buildings × 2 DBs each (extracted + library) | ~2 GB total |
| `deploy/sandbox/boq_charts.html` | 4D/5D analytics dashboard | ~50 KB |

**Smallest building to test with:** Duplex (~3 MB total). SampleHouse is even smaller (~500 KB).

### 2.1 Requirements

| Requirement | Version | Check | Notes |
|-------------|---------|-------|-------|
| **Python 3** | 3.8+ | `python3 --version` | Only for local web server. Built into Linux/Mac. Windows: [python.org](https://www.python.org/downloads/) |
| **Browser** | Chrome 90+, Edge 90+, Safari 15+ | — | Firefox works but no voice commands |
| **Git** | any | `git --version` | To clone the repo |

**No Java, no Maven, no Node.js, no npm needed** — those are only for the compiler pipeline (processing your own IFC files). The viewer is pure HTML + JavaScript.

### 2.2 Windows / WSL Users

```powershell
# Windows PowerShell (no WSL needed):
git clone https://github.com/red1oon/BIMCompiler.git
cd BIMCompiler\deploy
python -m http.server 8080
# Open: http://localhost:8080/sandbox/landing.html

# Or WSL Ubuntu:
git clone https://github.com/red1oon/BIMCompiler.git
cd BIMCompiler/deploy
python3 -m http.server 8080
# Open same URL in Windows browser
```

### 2.3 Import Your Own Models

**IFC import** works on the live production viewer — drag and drop a `.ifc` file onto the landing page.

**Multi-format import** (OBJ, DAE, GLB/GLTF, FBX, 3DS) + **Classification Wizard** + **IFC export** are available on the **DEV landing** or locally via `deploy/dev/index.html`. These features pass all Playwright tests but have not yet been promoted to production.

---

## 3. Feature Status

What works today, what's under construction, and what's planned.

### 3.1 Production (Live on OCI) — Tested

These features are deployed to the **live OCI bucket** and work right now:

| Feature | Desktop | Mobile | Test Spec | Notes |
|---------|---------|--------|-----------|-------|
| **3D streaming from DB** | OK | OK | `01-viewer-load` | Per-building DBs, BLOB geometry → GPU |
| **Element picking** | OK | OK | `01-viewer-load` | Click/tap → IFC class, GUID, storey, discipline |
| **Storey filter** | OK | OK | `02-panels` | Show/hide by level |
| **Discipline filter** | OK | OK | `02-panels` | Toggle STR, ARC, ELEC, etc. |
| **X-Ray mode** | OK | OK | `01-viewer-load` | Alt+Z or toolbar button |
| **Screenshot** | OK | OK | `01-viewer-load` | Downloads PNG |
| **Fly-around** | OK | OK | `01-viewer-load` | Auto-orbit, click to stop |
| **Light/dark theme** | OK | OK | `01-viewer-load` | Toggle button |
| **NLP voice queries** | OK | OK | `04-nlp` | "count doors", "floor 1 walls", "total cost" |
| **4D/5D BOQ charts** | OK | OK | `05-charts` | Chart.js dashboards, cost pie, phase timeline |
| **Excel export (4D + 5D)** | OK | OK | `06-excel-export` | SheetJS workbooks with cost/schedule breakdown |
| **IFC import** | OK | OK | `07-import-ifc` | Drag-drop .ifc onto landing page (web-ifc WASM) |
| **Walk / Drive-Thru** | OK | OK | `03-walk-sitecam` | Tap to step, hold to glide, phone orientation steers |
| **Site Camera** | OK | OK | `03-walk-sitecam` | GPS, compass, photo capture |
| **Measure tool** | OK | OK | — | Point-to-point distance |
| **Snag-to-BIM** | OK | OK | — | Tap element → camera snap → auto-tag with GUID, storey, GPS → issue log → Excel export |
| **City mode** | OK | OK | — | Multi-building landing, click to stream |

### 3.2 Dev Only (Not Yet Promoted to Production)

These features work in `deploy/dev/` and pass Playwright tests, but are **not yet on the live OCI bucket**. You can test them locally or on the DEV landing.

| Feature | Desktop | Mobile | Test Spec | Notes |
|---------|---------|--------|-----------|-------|
| **OBJ/DAE/GLB/FBX/3DS import** | OK | OK | `07-import-mesh` | Multi-format with semantic enrichment. Dev only — needs `mesh_import_worker.js`, `semantic_enrichment.js`, `scene_to_db.js` |
| **Classification Wizard** | OK | OK | `11-wizard` | 4-step guided: orient → storeys → classify → summary. Dev only — needs `wizard.js` |
| **IFC export** | OK | OK | `12-ifc-export` | DB → .ifc STEP text download. Dev only — needs `ifc_export_worker.js` |
| **Drop → Wizard E2E** | OK | OK | `15-drop-zone-wizard` | Full pipeline: drop file → import → wizard → view |
| **Two-DB diff/variance** | OK | OK | `08-diff` | Compare building versions, highlight changes |
| **InstancedMesh perf** | OK | OK | `16-instanced-perf` | 85% draw call reduction (48K elements). Dev only — needs updated `streaming.js` |
| **Rates + Locale** | OK | OK | — | `rates.js` — CIDB Malaysia rates, 15 locales. Dev only. See [Localization Guide](Localization.md) |
| **Service Worker** | OK | OK | — | `sw.js` — offline cache for HTML/JS/WASM. Dev only |
| **Find & Navigate** | OK | **issues** | `17-find-navigate` | Search elements, waypoint navigation. Dev only — needs `navigate.js` |

### 3.3 Under Construction — Known Issues

| Feature | Issue | Status |
|---------|-------|--------|
| **Find & Navigate (mobile)** | Camera starts near target instead of main door; dropdown refresh lag; panel transparency | S233b — fixing |
| **Walk ↔ Site Camera cycle** | `closeSiteCamera` doesn't restore toolbar on desktop; storey panel empty on test DB | 3 fixme tests |
| **Site Camera button (mobile)** | Requires GPS + getUserMedia permissions — can't test in headless browser | 1 fixme test |
| **4D Excel download** | Not captured in headless Chromium (works in real browser) | 1 fixme test |
| **Wizard toggle UX** | Code written, not field-proven on OCI | S234 — KIV |

### 3.4 Planned — Not Yet Built

| Feature | What | Spec |
|---------|------|------|
| **AR overlay** | Phone camera + BIM wireframe overlay via WebXR | §8 below |
| **Space compliance** | Walk into room → auto-check area/door width vs code | §7.8 below |
| **Sun study** | Real-time shadow casting from GPS + date/time | §7.8 below |
| **Progress tracking** | Walk-through marking elements as installed/defective | §7.8 below |
| **Room QR handover** | QR per room → scan opens viewer zoomed to that room | §7.8 below |

---

## 4. Architecture — How It Works

No native app. No server. No API. The browser does everything.

```
┌────────────────────────────────────────────┐
│  Your Browser (Chrome / Safari / Edge)     │
│                                            │
│  index.html ─── loads 18 JS modules        │
│       │                                    │
│  sql.js (WASM) ── full SQLite in browser   │
│       │         SELECT vertices, faces     │
│       │         FROM component_geometries  │
│       ▼                                    │
│  Three.js ────── Float32Array → GPU        │
│                  renders 3D geometry       │
│                                            │
│  DB files loaded from:                     │
│    • OCI Object Storage (online)           │
│    • Local folder (localhost)              │
│    • IndexedDB cache (offline, after 1st)  │
└────────────────────────────────────────────┘
```

**Per-building DBs** (two files per building):

- `{Name}_extracted.db` — metadata: element GUIDs, IFC classes, storeys, transforms, materials
- `{Name}_library.db` — geometry: vertex/face BLOBs, indexed by geometry hash

**Service Worker** (`sw.js`): caches HTML, JS, WASM, and CDN assets for offline use. Currently dev-only — not yet on the live OCI bucket.

---

## 5. Mobile UX

The same viewer adapts to phone screens. No separate mobile app.

| Desktop | Mobile |
|---------|--------|
| Mouse orbit/pan/zoom | Touch: 1-finger orbit, 2-finger pan, pinch zoom |
| Right-click pan | 2-finger drag |
| Hover highlight | Long-press highlight |
| Alt+Z X-Ray | Toolbar button (same) |
| Keyboard shortcuts | Touch-only — no keyboard needed |

**Mobile detection:** `navigator.maxTouchPoints > 0 && screen.width < 1024`

**Mobile performance:** InstancedMesh reduces draw calls by 85% (48K elements: 7,150 → ~500 draws). Targets 60fps on mid-range phones.

---

## 6. Site Inspection — Phone as BIM Tool

The phone has sensors that desktop BIM tools cannot access. We use them all — from the same HTML page.

### 6.1 What Works vs Commercial Tools

| Feature | BIM 360 | Trimble | Dalux | Procore | **BIM OOTB** |
|---------|---------|---------|-------|---------|--------------|
| View model on phone | Yes | Yes | Yes | Yes | **Yes** |
| Offline viewing | Partial | Partial | No | No | **Yes (cached DB)** |
| GPS-tagged issues | Yes | Yes | Yes | Yes | **Yes** |
| nD analytics on device | No | No | No | No | **Yes (4D-8D)** |
| Excel BOQ on phone | No | No | No | No | **Yes (SheetJS)** |
| Storey/discipline filter | Limited | Limited | Yes | No | **Yes** |
| FOSS / no account | No | No | No | No | **Yes** |
| Works without internet | No | No | No | No | **Dev only (Service Worker not yet promoted)** |
| Cost per user | $50-100/mo | $30-80/mo | €40/mo | $$$$ | **Free** |

### 6.2 Web APIs Used — No Native Code

| Capability | Web API | Status |
|------------|---------|--------|
| **Camera** | `<input capture>` | Working |
| **GPS** | `navigator.geolocation` | Working |
| **Compass** | `DeviceOrientationEvent.alpha` | Working |
| **Offline storage** | `IndexedDB` | Working |
| **Service Worker cache** | `ServiceWorker` | Working |
| **Share** | `navigator.share()` | Working (Android Chrome, iOS Safari) |
| **Wake lock** | `navigator.wakeLock` | Working (Chrome — prevents screen timeout) |
| **Voice input** | `SpeechRecognition` | Working (Chrome, Safari — not Firefox) |
| **AR overlay** | `WebXR` | Planned |

### 6.3 Walk Mode & Indoor Navigation

**Drive-Thru mode** (working): Tap to step forward, hold to glide. Phone orientation steers camera. Works indoors, offline, no GPS required.

**Find & Navigate** (working on desktop, mobile issues): Search for any element → waypoint path → direction cues → arrival.

**Pedestrian Dead Reckoning** (planned): Step detection from accelerometer + compass heading + barometer floor detection. Snap to IfcSpace walkable route. Blue dot tracks position like Google Maps for buildings.

### 6.4 Site Inspection Workflow

```
Supervisor arrives on site
    │
    ▼
Opens BIM OOTB (cached, works offline)
    │
    ├── Taps building → geometry streams (or loads from cache)
    ├── Navigates to area of concern
    ├── Taps element → info panel shows IFC metadata
    │
    ▼
Long-press element → "Report Issue" action
    │
    ├── Camera snaps photo (rear camera)
    ├── GPS + compass captured
    ├── Element context auto-filled (GUID, class, storey, discipline)
    │
    ▼
Issue saved to local IndexedDB → exportable as Excel/PDF/JSON
```

### 6.5 "What's Behind This Wall?"

Tap any wall → SQL query finds MEP elements within 500mm of the wall plane → pipes, ducts, cables highlighted. No LiDAR. No AR glasses. Just SQL + raycaster + compass.

```sql
SELECT guid, ifc_class, discipline
FROM elements_meta JOIN element_transforms USING (guid)
WHERE discipline IN ('MEP','ELEC','PLB','ACMV','FP')
  AND storey = '{same_storey}'
  AND ABS((center_x - {wall_x}) * {wall_nx}
        + (center_y - {wall_y}) * {wall_ny}) < 0.5
```

---

## 7. Voice Commands

Speak short commands. The browser's built-in speech engine recognizes them — no server, no API key, no cost.

**Supported:** Chrome (Android/desktop), Safari (iOS/macOS), Edge. **Not supported:** Firefox.

### 7.1 Quick Reference

| Say this | What it does |
|----------|-------------|
| **count doors** | Count all doors |
| **count beams** | Count all beams |
| **floor one doors** | Doors on Level 1 |
| **floor two walls** | Walls on Level 2 |
| **total cost** | Total building cost |
| **cost of beams** | Cost of all beams |
| **show structure** | List structural elements |
| **show electrical** | List electrical elements |
| **find fire doors** | Free-text search |
| **go to Hospital** | Fly to named building |
| **floor one only** | Isolate Level 1 |
| **all floors** | Show all storeys |
| **x-ray on** | Transparency mode |
| **screenshot** | Save to Downloads |
| **fly around** | Start orbit animation |

### 7.2 Tips

- **2-4 words** — speech engines lose accuracy after 5 words
- Say **"floor"** not "level" (engines mishear "level" as "label")
- Say **"walls"** not "IfcWall" (plain English, not IFC jargon)
- Avoid acronyms: say **"mechanical"** not "MEP" or "HVAC"

### 7.3 How It Works

```
Phone mic → Web Speech API (browser-native, free)
         → Recognised text (e.g. "floor one doors")
         → NLP keyword matcher (no AI, no server)
         → SQL template → sql.js executes against loaded DB
         → Result toast: "12 doors on Level 1"
```

Latency: < 500ms from speech end to result. Speech recognition requires internet (audio sent to Google/Apple for processing). The NLP + SQL execution is fully offline.

---

## 8. Deploy Your Own Instance

Want to host buildings on your own cloud? The viewer is just static files — any web host with Range headers works.

### 8.1 Hosting Options

| Host | Range Headers | Free Tier | Best For |
|------|--------------|-----------|----------|
| **OCI Object Storage** | Yes | 10GB + 10TB/mo | Large DBs, always free |
| **Cloudflare R2** | Yes | 10GB + 10M reads/mo | No egress fees |
| **GitHub Pages** | No | 1GB repo | Small DBs only (Duplex, SampleHouse) |
| **Netlify** | Yes | 100GB/mo bandwidth | Easy deploy |
| **Self-hosted nginx** | Yes (default) | Your server | Full control |

### 8.2 OCI Object Storage Setup (Free Forever)

Oracle Cloud free tier: 10GB storage + 10TB outbound/month. Never expires.

```bash
# 1. Install OCI CLI
pip install oci-cli
oci setup config    # prompts for tenancy OCID, user OCID, region, key

# 2. Create public bucket
oci os bucket create \
  --compartment-id <COMPARTMENT_OCID> \
  --name my-bim-viewer \
  --public-access-type ObjectRead

# 3. Upload viewer + DBs
oci os object put --bucket-name my-bim-viewer --file deploy/sandbox/index.html \
  --content-type text/html --name index.html
oci os object put --bucket-name my-bim-viewer --file deploy/buildings/Duplex_extracted.db \
  --content-type application/octet-stream --name Duplex_extracted.db
# ... repeat for each JS module and DB file

# 4. Set CORS (required for sql.js to work cross-origin)
cat > /tmp/cors.json << 'EOF'
[{
  "allowed-origins": ["*"],
  "allowed-methods": ["GET", "HEAD"],
  "allowed-headers": ["Range", "Content-Type"],
  "expose-headers": ["Content-Range", "Content-Length", "Accept-Ranges"],
  "max-age-in-seconds": 3600
}]
EOF
oci os bucket update --name my-bim-viewer --cors-config file:///tmp/cors.json

# 5. Your viewer is live at:
# https://objectstorage.<region>.oraclecloud.com/n/<namespace>/b/my-bim-viewer/o/index.html
```

**Without CORS + Range headers, the viewer will fail to load DBs.** This is the most common setup mistake.

### 8.3 Cache Busting

After uploading changed JS files, users must hard-refresh (Ctrl+Shift+R). The `?v=N` query strings in `index.html` control browser caching — bump the version number when deploying updates.

---

## 9. Roadmap

### Phase A: Browser Viewer (DONE — production)

- [x] OCI deployment with per-building DBs
- [x] Landing page with building cards
- [x] 3D streaming, picking, filtering
- [x] NLP voice queries
- [x] 4D/5D charts + Excel export
- [x] IFC import (web-ifc WASM)
- [x] Snag-to-BIM (camera snap + element auto-tag + issue log)
- [x] Walk / Drive-Thru mode
- [x] Site Camera (GPS, compass, photo)
- [x] Wall X-ray ("What's behind this wall?")

### Phase A+ : Dev Complete, Pending Promotion

- [x] Multi-format import (OBJ, DAE, GLB, FBX, 3DS) — dev tested
- [x] Classification Wizard (4-step guided) — dev tested
- [x] IFC export from browser — dev tested
- [x] InstancedMesh performance (85% draw call reduction) — dev tested
- [x] Service Worker offline cache — dev tested
- [x] Rates + Locale (CIDB Malaysia, 15 locales) — dev tested
- [ ] Promote all above to production OCI bucket

### Phase B: Mobile Polish (IN PROGRESS — S232-S233)

- [x] Mobile-responsive layout
- [x] Touch controls (orbit, pan, pinch zoom)
- [x] Walk / Drive-Thru mode
- [x] Site Camera (GPS, compass, photo)
- [x] Find & Navigate (desktop)
- [ ] Find & Navigate mobile fixes (camera init, dropdown, panel transparency)
- [ ] Mobile merge (storey×disc grouping for GPU budget)
- [ ] Field-test Wizard toggle on OCI

### Phase C: Site Inspection (MOSTLY DONE)

- [x] Camera snap on long-press (attach photo to element)
- [x] GPS + compass heading on issue create
- [x] Issue list panel (view/edit/export)
- [x] Excel export of site report
- [ ] Background sync (upload when online) — planned

### Phase D: Smart Alerts (PLANNED)

| Step | Feature | Effort |
|------|---------|--------|
| 7 | Local notifications from nD tables (4D/7D/8D schedule, warranty, safety) | 2 hours |
| 8 | Wake lock during site walkthrough | 30 min |
| 9 | Share intent (native share sheet) | 1 hour |

### Phase E: AR Overlay (FUTURE)

| Step | Feature | Effort |
|------|---------|--------|
| 10 | WebXR session with camera feed | 4 hours |
| 11 | GPS + compass alignment of Three.js scene over real building | 8 hours |
| 12 | Tap-through-camera element picking | 4 hours |

### Killer Features for Architects (FUTURE)

All leverage the existing stack: phone sensors + BIM DB + Walk/Site mode.

1. **As-Built Overlay (AR)** — Camera feed + BIM wireframe aligned by GPS + compass + TrueNorth.
2. **Space Compliance** — Walk into room → auto-check area, door width, ceiling height vs code requirements. Red/green pass/fail.
3. **Sun Study** — GPS + date/time → real-time shadow casting on BIM matching actual sun.
4. **MEP Behind Walls (AR)** — Camera shows real wall → BIM overlays pipes and ducts behind it. (Non-AR wall x-ray already works — see §6.5.)
5. **Room QR Handover** — QR on each door → scan opens viewer zoomed to that room with finishes, MEP, snag history.
6. **Progress Tracking** — Walk through building, tap elements as installed/defective → 4D schedule updates live.

---

## 10. Files

### Production (`deploy/sandbox/` → live OCI bucket)

| File | Role |
|------|------|
| `index.html` | Modular browser viewer |
| `main.js` | Render loop orchestrator |
| `streaming.js` | Geometry streaming from DB |
| `panels.js` | Storey + discipline filter panels |
| `picking.js` | Element selection + walk/fly state |
| `nlp.js` | NLP voice query DSL |
| `scene.js` | Three.js scene setup |
| `tour.js` | Fly-around animation |
| `sitecam.js` | Site Camera (GPS, compass, photo) |
| `walk.js` | Walk/Drive-Thru, device orientation |
| `measure.js` | Measurement tool |
| `issues.js` | Issue log panel |
| `excel.js` | Excel export (SheetJS) |
| `import.js` | IFC import (web-ifc WASM) |
| `diff.js` | Two-DB variance overlay |
| `boq_charts.html` | 4D/5D analytics dashboard |
| `city.js` | Multi-building city mode |
| `config.js` | Viewer configuration |
| `loader.js` | DB loader + IndexedDB cache |

### Dev Only (`deploy/dev/` — not yet promoted)

| File | Role | Since |
|------|------|-------|
| `wizard.js` | Classification Wizard (4-step) | S229a |
| `navigate.js` | Find & Navigate wayfinding | S233b |
| `rates.js` | CIDB Malaysia rates + 15 locales | S225b |
| `sw.js` | Service Worker offline cache | S232 |
| `semantic_enrichment.js` | Auto-classify imported meshes | S228a |
| `scene_to_db.js` | Three.js scene → DB pipeline | S228a |
| `mesh_import_worker.js` | OBJ/DAE/GLB/FBX/3DS loader worker | S228d |
| `ifc_export_worker.js` | DB → IFC STEP text builder | S229b |

### Data

| Path | Contents |
|------|----------|
| `deploy/buildings/*.db` | 31 buildings × 2 DBs (extracted + library) |
| `docs/MOBILE_DEPLOY.md` | This spec |

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
