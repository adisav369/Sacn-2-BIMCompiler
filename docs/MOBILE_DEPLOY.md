# BIM OOTB — Mobile & Cloud Deployment
> **Foundation:** [BIM_Designer_Browser.md](BIM_Designer_Browser.md) · [4D5DAnalysis.md](4D5DAnalysis.md)

<div class="bim-banner" markdown>
<b>BIM in your pocket.</b> Same HTML viewer, wrapped as an Android app. Per-building DBs from OCI Object Storage. Works offline after first load.
</div>

**Version:** 0.1 (2026-04-20)
**Status:** SPEC — OCI deployed, live URLs below
**Depends on:** `deploy/sandbox/index.html` + 15 JS modules, OCI Object Storage bucket

---

## 0. Live URLs — Try Now

| Link | What | Download | Mobile? |
|------|------|----------|---------|
| [**BIM OOTB Demo**](https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb/o/index.html) | Duplex building — full download, auto fly-around | ~3 MB | Yes |
| [**BIM OOTB City**](https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/index.html) | 37 buildings, 1M elements — httpvfs streaming | ~5-10 MB/building | Yes |

**OCI Object Storage** (Malaysia West 2 Kulai). Always Free tier. Bucket: `bim-ootb` (demo), `bim-ootb-full` (city).
CORS enabled. Range headers supported (required for httpvfs).

---

## 0.1 Dev Environment

**Dev bucket:** `bim-ootb-dev` — separate OCI bucket for testing changes before production.

| Environment | Bucket | Landing | Purpose |
|-------------|--------|---------|---------|
| **Production** | `bim-ootb-full` | `landing.html` → `index.html` | Live users |
| **Dev** | `bim-ootb-dev` | `landing2.html` → `index.html` | Test changes |
| **Duplex demo** | `bim-ootb` | viewer direct | Standalone demo |

**Dev URL:** `https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-dev/o/index.html`

**Local dev files:** `deploy/dev/` — only changed files. Unchanged modules copied from `deploy/sandbox/`.
Deploy commands: see `deploy/OCI_UPLOAD.md` §Dev Environment.

---

## 1. Architecture — No Native Code

The browser viewer already works on mobile Chrome/Safari. The APK is a thin wrapper.

```
┌───────────────────────────────────────────────────┐
│  Android APK (TWA or Capacitor WebView)           │
│                                                   │
│  ┌─────────────────────────────────────────────┐  │
│  │  WebView                                    │  │
│  │                                             │  │
│  │  sandbox/index.html (bundled or OCI)   │  │
│  │  sql.js WASM ← CDN                         │  │
│  │  Three.js    ← CDN                         │  │
│  │                                             │  │
│  │  DB loaded from:                            │  │
│  │    • OCI Object Storage (online)            │  │
│  │    • Local cache (offline, after 1st load)  │  │
│  └─────────────────────────────────────────────┘  │
│                                                   │
│  Native shell: splash screen, app icon, status    │
│  bar colour, share intent, file picker            │
└───────────────────────────────────────────────────┘
```

**No Java/Kotlin. No native 3D. No React Native.** Just the same HTML+JS viewer
in a WebView with app-store distribution.

---

## 2. Three Paths to APK

| Path | Tool | Effort | Best For |
|------|------|--------|----------|
| **TWA** (Trusted Web Activity) | Bubblewrap CLI | 1 hour | If OCI URL is live (PWA manifest needed) |
| **Capacitor** | Ionic Capacitor | 2-3 hours | Offline-first, file picker, local DB cache |
| **PWA** (no APK) | Browser "Add to Home Screen" | 0 minutes | Instant, no Play Store, works today |

**Recommended: Capacitor** — gives offline cache, file picker (open local `.db`),
and Play Store listing. Falls back to PWA if user doesn't want to install.

### 2.1 Capacitor Setup (One-Time)

```bash
# 1. Install Capacitor
npm init -y
npm install @capacitor/core @capacitor/cli @capacitor/android

# 2. Init project
npx cap init "BIM OOTB" com.bimcompiler.ootb --web-dir deploy

# 3. Add Android platform
npx cap add android

# 4. Copy web assets (HTML + local DBs if bundling)
npx cap copy android

# 5. Open in Android Studio → Build APK
npx cap open android
```

The `deploy/` folder IS the web app. No build step needed.

### 2.2 PWA Manifest (Required for TWA, Optional for Capacitor)

Add to `deploy/manifest.json`:
```json
{
  "name": "BIM OOTB",
  "short_name": "BIM OOTB",
  "description": "Frictionless BIM. Two DBs. One browser. Zero install.",
  "start_url": "/rtree_browser_demo.html",
  "display": "standalone",
  "background_color": "#1a1a2e",
  "theme_color": "#1a1a2e",
  "icons": [
    { "src": "icon-192.png", "sizes": "192x192", "type": "image/png" },
    { "src": "icon-512.png", "sizes": "512x512", "type": "image/png" }
  ]
}
```

Add to HTML `<head>`:
```html
<link rel="manifest" href="manifest.json">
<meta name="theme-color" content="#1a1a2e">
```

### 2.3 Offline Strategy

| Asset | Size | Strategy |
|-------|------|----------|
| HTML + JS (viewer) | ~150KB | Bundle in APK |
| sql.js WASM | ~1MB | Cache on first load (Service Worker) |
| Three.js + OrbitControls | ~600KB | Cache on first load |
| Per-building DB | 2-68MB | Download on demand, cache locally |
| component_library.db | 456MB | **httpvfs only** — never full download on mobile |

**Critical:** Mobile must use **httpvfs** for the geometry library (456MB full download
is impractical on mobile). Per-building extracted DBs (2-68MB) can be fully cached.

---

## 3. OCI Object Storage — Setup Guide

### 3.1 Prerequisites

1. Oracle Cloud account (free tier includes 10GB Object Storage + 10TB outbound/month)
2. OCI CLI installed (`pip install oci-cli && oci setup config`)

### 3.2 Free Tier — What You Get

| Resource | Free Tier Limit | Our Usage |
|----------|----------------|-----------|
| Object Storage | 10 GB (standard), 20 GB (infrequent) | Per-building DBs fit in 10GB |
| Outbound data | 10 TB/month | ~10,000 full Hospital loads/month |
| Requests | 50,000 API calls/month | Plenty for httpvfs range requests |
| Always Free | Yes — never expires | No credit card charges |

**Note:** The full sandbox (579MB + 456MB = 1.03GB) exceeds free tier.
Deploy per-building DBs instead — all 6 buildings fit in ~300MB.

### 3.3 Setup Steps

```bash
# 1. Install OCI CLI
pip install oci-cli
oci setup config    # → prompts for tenancy OCID, user OCID, region, key

# 2. Create compartment (or use root)
oci iam compartment create --name bim-ootb --description "BIM OOTB static hosting"

# 3. Create public bucket
oci os bucket create \
  --compartment-id <COMPARTMENT_OCID> \
  --name bim-ootb \
  --public-access-type ObjectRead

# 4. Upload files
oci os object put --bucket-name bim-ootb --file deploy/rtree_browser_demo.html \
  --content-type text/html --name index.html
oci os object put --bucket-name bim-ootb --file deploy/Hospital_extracted.db \
  --content-type application/octet-stream --name Hospital_extracted.db

# 5. Get public URL
# Format: https://objectstorage.<region>.oraclecloud.com/n/<namespace>/b/bim-ootb/o/index.html
oci os ns get   # → prints namespace
```

### 3.4 CORS Configuration (Required for sql.js + httpvfs)

```bash
# Create cors.json
cat > /tmp/cors.json << 'EOF'
[{
  "allowed-origins": ["*"],
  "allowed-methods": ["GET", "HEAD"],
  "allowed-headers": ["Range", "Content-Type"],
  "expose-headers": ["Content-Range", "Content-Length", "Accept-Ranges"],
  "max-age-in-seconds": 3600
}]
EOF

# Apply to bucket
oci os bucket update --name bim-ootb --cors-config file:///tmp/cors.json
```

**Without CORS + Range headers, httpvfs will fail.** This is the most common setup mistake.

### 3.5 Alternative: GitHub Pages (Simpler, Size-Limited)

For small buildings (Duplex ~2MB, Jesse ~1MB), GitHub Pages works:

```bash
# Already deployed at:
# https://red1oon.github.io/BIMCompiler/

# For DB hosting, use GitHub Releases (2GB per file limit):
gh release create v0.1 deploy/Hospital_extracted.db --title "Hospital DB"
```

GitHub Pages doesn't support Range headers → no httpvfs. Fine for small DBs.

### 3.6 Alternative: Any Static Host

The viewer is just static files. Any host that supports Range headers works:

| Host | Range Headers | Free Tier | Notes |
|------|--------------|-----------|-------|
| **OCI Object Storage** | Yes | 10GB + 10TB/mo | Best for large DBs |
| **Cloudflare R2** | Yes | 10GB + 10M reads/mo | No egress fees |
| **GitHub Pages** | No | 1GB repo | Small DBs only |
| **Netlify** | Yes | 100GB/mo bandwidth | Easy deploy |
| **Vercel** | Yes | 100GB/mo bandwidth | Easy deploy |
| **Self-hosted nginx** | Yes (default) | Your server | Full control |

---

## 4. Deployment Matrix

| Scenario | DB Source | Viewer | Total Download |
|----------|----------|--------|---------------|
| **Demo (single building)** | Per-building DB from OCI | HTML from OCI | 2-68MB |
| **Site review (offline)** | Cached locally after first load | Bundled in APK | 0 after cache |
| **City overview** | httpvfs range requests from OCI | HTML from OCI | ~10MB initial |
| **QS takeoff** | Per-building DB + nD templates | HTML + SheetJS | ~70MB + Excel out |

---

## 5. Mobile UX Adaptations

| Desktop Feature | Mobile Adaptation |
|-----------------|-------------------|
| Mouse orbit/pan/zoom | Touch: 1-finger orbit, 2-finger pan, pinch zoom (OrbitControls handles this) |
| Right-click pan | 2-finger drag |
| Hover highlight | Long-press highlight (no hover on touch) |
| Alt+Z X-Ray | Toolbar button (already exists) |
| Keyboard shortcuts | Remove — touch-only |
| Collapsible panels | Same — already responsive |
| Screenshot | Share intent (native Android share sheet) |

### 5.1 Viewport Sizing

```css
/* Already in viewer — verify on mobile */
@media (max-width: 768px) {
  #tools-panel { width: 100%; bottom: 0; top: auto; }
  #info-panel  { width: 100%; font-size: 12px; }
}
```

The current viewer may need minor CSS tweaks for phone-width screens.
Panels should stack vertically on narrow viewports.

---

## 6. Site Inspection — Phone-Native BIM

This is where BIM OOTB surpasses commercial field apps. The phone has sensors
that desktop BIM tools cannot access. We use all of them — from a single HTML page,
no native code.

### 6.1 Competitive Landscape

| Feature | BIM 360 | Trimble | Dalux | Procore | **BIM OOTB** |
|---------|---------|---------|-------|---------|--------------|
| View model on phone | Yes | Yes | Yes | Yes | **Yes** |
| Offline viewing | Partial (pre-sync) | Partial | No | No | **Yes (cached DB)** |
| GPS-tagged issues | Yes | Yes | Yes | Yes | **Yes** |
| Camera snap on element | Yes | No | Yes | Yes | **Yes** |
| nD analytics on device | No | No | No | No | **Yes (4D-8D)** |
| Excel BOQ on phone | No | No | No | No | **Yes (SheetJS)** |
| Storey/discipline filter | Limited | Limited | Yes | No | **Yes** |
| FOSS / no account | No | No | No | No | **Yes** |
| Works without internet | No | No | No | No | **Yes (after cache)** |
| AR overlay | BIM 360 (add-on) | Trimble SiteVision ($$$) | No | No | **Planned (WebXR)** |
| Cost per user | $50-100/mo | $30-80/mo | €40/mo | $$$$ | **Free** |

**What they all lack:** the model + cost + schedule + carbon + safety in one app,
offline, free. They silo each dimension behind separate subscriptions.

### 6.2 Site Inspection Workflow

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
    ├── 📷 Camera snaps photo (rear camera, auto-attached)
    ├── 📍 GPS captured (lat/lon/accuracy)
    ├── 🕐 Timestamp recorded
    ├── 📱 Device info (orientation, compass heading)
    ├── 🏗️ Element context auto-filled:
    │     GUID, IFC class, storey, discipline, building
    │
    ▼
Issue saved to local IndexedDB
    │
    ├── Offline: queued for sync
    ├── Online: POST to endpoint (or export as JSON/CSV)
    │
    ▼
Issue log exportable as:
    ├── CSV/Excel (SheetJS, same as BOQ)
    ├── PDF report (with photo thumbnails)
    └── JSON (machine-readable, API-friendly)
```

### 6.3 Issue Report Data Model

Each issue captures everything the phone knows:

```javascript
{
  // Element context (auto-filled from BIM model)
  guid: "2O2Fr$t4X7Zf8NOew3FLOH",
  ifc_class: "IfcWallStandardCase",
  element_name: "Basic Wall:Generic - 200mm:1234",
  building: "T0_Hospital",
  storey: "Level 2",
  discipline: "ARC",

  // Phone sensors (auto-captured)
  gps: { lat: 3.1390, lon: 101.6869, accuracy_m: 4.2 },
  timestamp: "2026-04-20T14:32:07+08:00",
  compass_heading: 127,          // degrees from north
  device_orientation: { alpha: 127, beta: 45, gamma: 0 },

  // User input
  photo: "data:image/jpeg;base64,...",  // or Blob reference
  severity: "major",                    // minor | major | critical | safety
  category: "defect",                   // defect | progress | rfi | safety | punch
  notes: "Crack in wall above door frame, ~30cm length",

  // Metadata
  inspector: "Ahmad",                   // saved in app settings
  status: "open",                       // open | in-progress | resolved | closed
  synced: false                         // true after upload
}
```

### 6.4 Web APIs Used — No Native Code

| Capability | Web API | Browser Support |
|------------|---------|-----------------|
| **Camera** | `MediaDevices.getUserMedia()` or `<input capture>` | All modern browsers |
| **GPS** | `navigator.geolocation.getCurrentPosition()` | All modern browsers |
| **Compass** | `DeviceOrientationEvent.alpha` | Android Chrome, iOS Safari (with permission) |
| **Offline storage** | `IndexedDB` | All modern browsers, no size limit (with user grant) |
| **Background sync** | `ServiceWorker` + `SyncManager` | Chrome, Edge (progressive enhancement) |
| **Vibration** | `navigator.vibrate()` | Android (already using for long-press) |
| **Share** | `navigator.share()` | Android Chrome, iOS Safari — native share sheet |
| **Notifications** | `Notification API` + Service Worker push | All (permission required) |
| **Wake lock** | `navigator.wakeLock.request('screen')` | Chrome — prevents screen timeout during walkthrough |

### 6.5 Notification Use Cases

| Trigger | Notification | Source |
|---------|-------------|--------|
| Issue assigned to you | "New issue on Level 3 — IfcDoor crack" | Push from sync endpoint |
| Issue resolved | "Issue #47 marked resolved by Ahmad" | Push |
| Schedule milestone today | "Phase 2 MEP Rough-in starts today (4D)" | Local, from `construction_schedule` table |
| Warranty expiring | "IfcChiller warranty expires in 30 days (7D)" | Local, from `asset_register` table |
| Safety hazard on today's task | "HIGH risk: IfcStair work requires harness (8D)" | Local, from `hazard_register` table |

**The nD tables power the notifications.** No separate notification database —
the same `construction_schedule`, `asset_register`, and `hazard_register` tables
that drive the browser analytics also drive mobile alerts.

### 6.6 Photo + GPS + Element = Site Report

The killer feature no vendor offers as FOSS:

```
┌─────────────────────────────────────────┐
│  SITE INSPECTION REPORT                 │
│  Generated: 2026-04-20 14:45 +08:00     │
│  Inspector: Ahmad                       │
│  Building: T0_Hospital                  │
│  GPS: 3.1390°N, 101.6869°E (±4.2m)    │
├─────────────────────────────────────────┤
│                                         │
│  Issue #1 — MAJOR DEFECT               │
│  Element: Basic Wall (GUID: 2O2Fr...)  │
│  Storey: Level 2 | Discipline: ARC     │
│  Photo: [thumbnail]                     │
│  Notes: Crack above door, ~30cm        │
│  Compass: 127° (SE-facing wall)        │
│                                         │
│  Issue #2 — PROGRESS                   │
│  Element: IfcSlab (GUID: 3K8Xw...)    │
│  Storey: Level 3 | Discipline: STR     │
│  Photo: [thumbnail]                     │
│  Notes: Formwork complete, ready pour  │
│                                         │
├─────────────────────────────────────────┤
│  Summary: 2 issues (1 defect, 1 prog)  │
│  Export: [Excel] [PDF] [JSON]           │
└─────────────────────────────────────────┘
```

Exported via SheetJS (Excel) or jsPDF (PDF) — entirely on-device, works offline.

### 6.7 AR Overlay (Future — WebXR)

The phone camera shows the real building. The BIM model overlays as wireframe,
aligned by GPS + compass + device orientation.

```
Phone camera (real world)
    │
    ├── GPS → coarse position (±5m)
    ├── Compass → building orientation
    ├── Device orientation → camera angle
    │
    ▼
Three.js scene overlaid on camera feed (WebXR API)
    │
    ├── Elements semi-transparent over real walls
    ├── Hidden MEP pipes visible through walls (X-Ray mode)
    ├── Tap real wall → element info panel
    └── "What's behind this wall?" — show pipes, ducts, cables
```

**WebXR is supported in Chrome Android since 2020.** The Three.js scene we already
have plugs directly into `navigator.xr.requestSession('immersive-ar')`.
GPS alignment is the hard part — but even coarse alignment (building-level) is
useful for "which storey am I on?" and "which discipline is behind this wall?"

### 6.8 What We Ship That Nobody Else Does

| Capability | Why It Matters | Vendor Cost |
|------------|---------------|-------------|
| **Offline nD on phone** | QS generates BOQ on-site without internet | $10-50K/yr (CostX mobile) |
| **GPS + GUID issue log** | Defect linked to exact BIM element, not just a pin on a floorplan | $50-100/user/mo (BIM 360) |
| **Camera + element auto-context** | Photo automatically tagged with IFC class, storey, discipline | Dalux €40/mo, Procore $$$$ |
| **4D schedule alerts** | Phone notifies "MEP rough-in starts today on Level 3" | Synchro $5-15K/yr |
| **7D warranty alerts** | Phone notifies "chiller warranty expires in 30 days" | Maximo $20-100K/yr |
| **8D safety alerts** | Phone notifies "HIGH risk work today — harness required" | Manual HSE spreadsheets |
| **Excel/PDF export on phone** | Supervisor generates report on-site, emails it before leaving | All vendors charge for reporting |
| **AR pipe visibility** | "What MEP is behind this wall?" from phone camera | Trimble SiteVision $10K+ |
| **FOSS, no account** | No vendor lock-in, no subscription, no data on someone else's server | **Free forever** |

---

## 7. Roadmap

### Phase A: Browser on Phone (today)

| Step | Action | Effort | Depends On |
|------|--------|--------|------------|
| 1 | Register OCI account (free tier) | 15 min | Nothing |
| 2 | Install OCI CLI, create bucket with CORS | 30 min | Step 1 |
| 3 | Upload per-building DBs + HTML viewer | 15 min | Step 2 |
| 4 | Test public URL in mobile Chrome | 5 min | Step 3 |

### Phase B: Installable App

| Step | Action | Effort | Depends On |
|------|--------|--------|------------|
| 5 | Add PWA manifest + service worker to HTML | 1 hour | Step 3 |
| 6 | Capacitor APK build | 2 hours | Step 5, Android Studio |
| 7 | httpvfs integration (replace full download) | 4 hours | Step 3 |
| 8 | F-Droid / Play Store listing | 1 hour | Step 6 |

### Phase C: Site Inspection

| Step | Action | Effort | Depends On |
|------|--------|--------|------------|
| 9 | Camera snap on long-press (attach photo to element) | 2 hours | Step 6 |
| 10 | GPS capture + compass heading on issue create | 1 hour | Step 9 |
| 11 | IndexedDB issue store (offline-first) | 2 hours | Step 9 |
| 12 | Issue list panel (view/edit/export) | 2 hours | Step 11 |
| 13 | Excel/PDF export of site report | 2 hours | Step 12 |
| 14 | Background sync (upload when online) | 2 hours | Step 11 |

### Phase D: Smart Alerts

| Step | Action | Effort | Depends On |
|------|--------|--------|------------|
| 15 | Local notifications from nD tables (4D/7D/8D) | 2 hours | Step 5 |
| 16 | Wake lock during site walkthrough | 30 min | Step 6 |
| 17 | Share intent (native Android share sheet) | 1 hour | Step 6 |

### Phase E: AR Overlay (Future)

| Step | Action | Effort | Depends On |
|------|--------|--------|------------|
| 18 | WebXR session with camera feed | 4 hours | Step 6 |
| 19 | GPS + compass alignment of Three.js scene | 8 hours | Step 18 |
| 20 | Tap-through-camera element picking | 4 hours | Step 19 |

**Phase A can be done today.** Phase B in a weekend. Phase C is the differentiator.

### Phase F: BIM Walk Mode — Indoor Navigation (Future)

Turn the phone into a **building navigation device**. The supervisor sees a blue dot
moving through the 3D model as they walk the site — Google Maps for buildings.

#### F.1 The Problem

No indoor navigation exists for construction sites. GPS works outdoors (±3-5m) but
fails indoors (±10-15m). Commercial solutions (Trimble SiteVision) require $10K+
proprietary hardware. Nobody offers FOSS BIM navigation.

#### F.2 Sensor Fusion — What the Phone Already Has

| Sensor | Web API | What It Gives | Accuracy |
|--------|---------|---------------|----------|
| **GPS** | `navigator.geolocation` | Absolute position (lat/lng) | ±3-5m outdoor, ±10-15m indoor |
| **Compass** | `DeviceOrientationEvent.alpha` | Heading (degrees from north) | ±5° |
| **Accelerometer** | `Sensor API: Accelerometer` | Step detection | ~95% step accuracy |
| **Gyroscope** | `Sensor API: Gyroscope` | Turn detection (rotation rate) | ±2° per turn |
| **Barometer** | `Sensor API: AbsoluteOrientationSensor` | Altitude changes (floor detection) | ±0.5m (one storey = 3m) |
| **Step counter** | `Sensor API: StepCounter` | Cumulative steps | Built into Android |

**Smartwatch bonus:** Wrist-mounted accelerometer gives cleaner step data than
pocket phone. Web Bluetooth API reads watch sensors. Not required — phone-only works.

#### F.3 Pedestrian Dead Reckoning (PDR)

The core algorithm — well-researched in academia, never applied to BIM:

```
1. ANCHOR: User taps "I'm here" at known location (main entrance)
   → Maps phone GPS to IFC coordinate
   → Sets initial position in model

2. STEP DETECT: Accelerometer peak detection
   → Each step = 0.7m (calibrated to user stride)
   → Direction = compass heading

3. POSITION UPDATE:
   new_x = old_x + step_length × sin(heading)
   new_y = old_y + step_length × cos(heading)

4. FLOOR DETECT: Barometer altitude change
   → ΔAltitude > 2.5m = storey change
   → Snap to nearest IfcSlab level

5. SNAP TO SPACE: Constrain to walkable route
   → Query: nearest IfcSpace to raw position
   → Snap blue dot to room centroid
   → Like Google Maps snaps to road
```

#### F.4 Walkable Route Graph

The IFC spatial structure IS the navigation graph:

```
IfcSpace (room)  ←→  IfcDoor (connection)  ←→  IfcSpace (adjacent room)
                                    │
                              IfcStair (vertical connection)
                                    │
                              IfcSpace (room on next storey)
```

```sql
-- Build navigation graph from IFC data already in the DB
-- Rooms = nodes
SELECT DISTINCT storey, guid, element_name, center_x, center_y, center_z
FROM elements_meta JOIN element_transforms USING (guid)
WHERE ifc_class = 'IfcSpace';

-- Doors = edges (connect adjacent rooms)
SELECT guid, element_name, center_x, center_y, center_z, storey
FROM elements_meta JOIN element_transforms USING (guid)
WHERE ifc_class IN ('IfcDoor', 'IfcStair', 'IfcStairFlight');
```

Route snapping: raw GPS/PDR position → nearest node in graph → blue dot stays on
walkable path. Drift is corrected every time the user passes through a door
(choke point = position correction opportunity).

#### F.5 TrueNorth Alignment

Already extracted (S204): `project_metadata.true_north_angle` gives the rotation
between IFC grid Y and geographic north. This aligns the compass heading with the
model coordinate system:

```
model_heading = phone_compass - true_north_angle
step_dx = step_length × sin(model_heading)
step_dy = step_length × cos(model_heading)
```

No manual rotation calibration needed — the IFC tells us.

#### F.6 Walk Mode UX

```
Supervisor arrives at building entrance
    │
    ▼
Taps "Walk Mode" → blue dot appears at entrance
    │
    ├── Walks through building → dot moves in real-time
    ├── Camera follows dot (first-person or overhead)
    ├── Current room name shown in HUD
    ├── Floor auto-detected from barometer
    │
    ├── Taps wall → "What's behind this wall?"
    │   └── MEP elements within 500mm highlighted
    │
    ├── Taps "Site" → camera snap with exact model position
    │   └── Photo tagged with room name, not just GPS
    │
    └── Walks past issue location → notification
        └── "Issue #3 (crack in wall) is 2m to your left"
```

#### F.6b Step Detection — Shake to Walk (S207b)

Implemented: `DeviceMotion` accelerometer detects vertical phone bounce
(walking cadence ~2Hz). Each step advances camera 0.6m forward in look
direction. Mimics the real-world experience shown in the screenshots above —
phone in hand, walking through building, model moves with you.

- Threshold: 4.0 m/s² vertical delta triggers a step
- Cooldown: 300ms between steps (prevents double-counting)
- Direction: camera's XZ forward vector (stays on same floor level)
- Status HUD: "Walk Mode: N steps"
- No GPS required — works indoors, offline, any phone with accelerometer

#### F.7 What's Behind This Wall

Walk Mode enables the killer query. When the supervisor taps a wall:

```sql
-- Find MEP elements behind/inside the selected wall
-- wall_x, wall_y, wall_normal from raycaster hit
SELECT m.guid, m.ifc_class, m.element_name, m.discipline,
       t.center_x, t.center_y, t.center_z
FROM elements_meta m
JOIN element_transforms t ON m.guid = t.guid
WHERE m.discipline IN ('MEP', 'ELEC', 'PLB', 'ACMV', 'FP', 'HVAC')
  AND m.storey = '{same_storey}'
  AND ABS((t.center_x - {wall_x}) * {wall_nx}
        + (t.center_y - {wall_y}) * {wall_ny}) < 0.5  -- within 500mm of wall plane
```

Result: pipes, ducts, cables highlighted in the model. The PiP snapshot shows them.
The supervisor sees what's behind the wall without opening it.

No LiDAR. No AR glasses. Just SQL + raycaster + compass.

#### F.6c Drive-Thru Mode (S208)

Replaced shake-to-walk with tap-to-drive. Phone orientation steers, screen tap drives.

- **Tap** = one step forward (0.6m in camera look direction)
- **Hold** = continuous glide, accelerates after 1.5s (2x speed)
- Blue ▶ button at bottom center, 80px circle
- Shake-to-walk still works as fallback (accelerometer)
- Status: "Drive-Thru: 12 steps (7.2m)"

Why: shake detection has false triggers (turning phone, gestures). Tap is deliberate,
precise, works on any phone. Hold-to-glide gives smooth movement for presentations.

#### F.6d Fly-Thru (planned — same pattern as Drive-Thru)

Now that device orientation + camera is solved (S208), fly-through becomes possible:

**Concept: drone on rails.** Camera auto-advances along a computed path (storey by
storey, room by room). Phone orientation controls where you LOOK while the path
controls where you GO. Like a theme park ride through the building.

Path computation from DB:
```sql
-- Build fly-through path: entrance → rooms on each storey → up → next storey
SELECT DISTINCT m.storey, MIN(t.center_z) as floor_z,
       AVG(t.center_x) as cx, AVG(t.center_y) as cy
FROM elements_meta m
JOIN element_transforms t ON m.guid = t.guid
WHERE m.ifc_class LIKE 'IfcSpace%'
GROUP BY m.storey ORDER BY floor_z
```

Implementation:
1. `walkOrientTick()` drives look direction (already working)
2. Auto-advance position along waypoints (like `advanceWalkStep` but on a timer)
3. Speed controlled by hold (Drive-Thru button) or auto (presentation mode)
4. Storey transitions: smooth camera rise between floors
5. No `controls.update()` — same S208 pattern

The key insight from S208: **OrbitControls and DeviceOrientation are mutually exclusive.**
Any fly-through on mobile must NOT call `controls.update()`. Position moves via direct
`camera.position` manipulation, orientation via `walkOrientTick()` quaternion.

#### F.10 Roadmap — Killer Features for Architects

All leverage the existing stack: phone sensors + BIM DB in browser + Walk/Site mode.

**1. Snag-to-BIM** — Tap element during walk → phone camera opens → snap defect photo.
Auto-tags with element GUID, storey, discipline, GPS. Generates punch list with photos
linked to exact BIM elements. Export to Excel. Contractors get clickable 3D snag locations.

**2. As-Built Overlay (AR)** — Camera feed + BIM model overlay using GPS + compass +
TrueNorth alignment (already implemented). Architect sees where actual construction
doesn't match the BIM. Browser-only AR, no app install.

**3. Space Compliance Checker** — Walk into a room, phone auto-detects which IfcSpace
you're in (GPS + storey + nearest space polygon). Instantly shows: room area vs code
requirement, door width vs minimum, ceiling height check. Red/green pass/fail.

**4. Sun Study on Site** — Phone knows GPS + date + time. Calculate sun position, show
shadow casting on BIM in real-time matching the actual sun. "Will this corridor get light
at 3pm in December?" answered on-site, instantly.

**5. MEP Behind Walls — Live AR** — Extend wall X-ray (already working) with camera feed.
Hold phone at real wall → camera shows real wall → BIM overlays pipes and ducts behind it.
Plumber doesn't drill into a cable tray.

**6. Room Handover QR** — Generate QR code per room. Stick on door during construction.
Anyone scans → viewer opens zoomed to that room with finishes, MEP, dimensions, snag
history. Facilities management starts day one.

**7. Progress Tracking** — Walk through building, tap elements as "installed" / "not started" /
"defective". 4D schedule updates live. Site manager does a 10-minute walk → stakeholders
see progress heatmap by storey.

#### F.8 Competitive Landscape

| Feature | Trimble SiteVision | Google Indoor Maps | Apple Indoor Maps | **BIM OOTB Walk** |
|---------|-------------------|-------------------|-------------------|-------------------|
| Indoor navigation | Yes (RTK GPS) | WiFi fingerprint | BLE beacons | PDR + IFC snap |
| BIM model overlay | Yes | No | No | **Yes** |
| Hardware required | $10K+ device | WiFi AP mapping | BLE beacon install | **Phone only** |
| MEP behind wall | No | No | No | **Yes** |
| Works on construction site | Yes | No (needs WiFi) | No (needs beacons) | **Yes** |
| Offline | Yes | No | No | **Yes** |
| Cost | $10K+ | Free but limited | Free but limited | **Free** |
| FOSS | No | No | No | **Yes** |

#### F.9 Implementation Steps

| Step | Action | Effort | Depends On |
|------|--------|--------|------------|
| 21 | Anchor point UI ("I'm here" at known location) | 2 hours | Phase A |
| 22 | GPS → IFC coordinate mapping with TrueNorth | 2 hours | Step 21 |
| 23 | Blue dot in Three.js scene, camera follows | 1 hour | Step 22 |
| 24 | Step detection from accelerometer | 4 hours | Step 23 |
| 25 | PDR position update (step + heading) | 2 hours | Step 24 |
| 26 | Barometer floor detection | 2 hours | Step 25 |
| 27 | Snap to nearest IfcSpace (walkable route) | 3 hours | Step 25 |
| 28 | "What's behind this wall" query + highlight | 3 hours | Step 27 |
| 29 | Smartwatch sensor via Web Bluetooth (optional) | 4 hours | Step 24 |
| 30 | Drift correction at door choke points | 4 hours | Step 27 |

**Steps 21-23 can be prototyped in a day.** GPS-only blue dot with compass rotation.
Steps 24-27 add PDR for indoor accuracy. Steps 28-30 are the differentiators.

---

## 8. Voice Query Script — Rehearsal Guide

BIM OOTB supports voice commands via the browser's built-in speech engine (Web Speech API).
No server, no API key, no cost. Works on Chrome (Android/desktop) and Safari (iOS).

**Design principle:** Short byte commands, not sentences. Two to four words.
Words chosen for phonetic clarity — no homophones, no jargon the engine confuses.

### 8.1 Valid Voice Commands

Speak any command below. The engine hears the words, the NLP DSL matches the pattern.

#### Counting

| Say this | What it does | Recognised as |
|----------|-------------|---------------|
| **count doors** | Count all doors | COUNT IfcDoor |
| **count beams** | Count all beams | COUNT IfcBeam |
| **count walls** | Count all walls | COUNT IfcWall |
| **count lights** | Count all lights | COUNT IfcLightFixture |
| **count columns** | Count all columns | COUNT IfcColumn |
| **count windows** | Count all windows | COUNT IfcWindow |
| **count pipes** | Count all pipes | COUNT IfcPipeSegment |
| **count ducts** | Count all ducts | COUNT IfcDuctSegment |

#### Counting by Floor

| Say this | What it does |
|----------|-------------|
| **floor one doors** | Doors on Level 1 |
| **floor two beams** | Beams on Level 2 |
| **floor three walls** | Walls on Level 3 |
| **ground floor lights** | Lights on ground floor |
| **roof level pipes** | Pipes on roof level |

> **Tip:** Say "floor" not "level" — speech engines hear "floor" more reliably than "level"
> (which can be misheard as "label" or "legal").

#### Cost and Quantities

| Say this | What it does |
|----------|-------------|
| **total cost** | Total building cost |
| **cost of beams** | Cost of all beams |
| **total area** | Total floor area |
| **total length pipes** | Sum pipe lengths |
| **floor area** | Slab area total |

#### Disciplines

| Say this | What it does |
|----------|-------------|
| **show structure** | List structural elements |
| **show electrical** | List electrical elements |
| **show plumbing** | List plumbing elements |
| **show fire** | List fire protection |
| **what disciplines** | List all disciplines |

#### Search

| Say this | What it does |
|----------|-------------|
| **find fire doors** | Free-text search |
| **search concrete** | Free-text search |

#### Navigation (viewer actions, not just queries)

| Say this | What it does |
|----------|-------------|
| **go to Hospital** | Fly to named building (city mode) |
| **go to Duplex** | Fly to named building |
| **floor one only** | Isolate Level 1 (hide others) |
| **floor two only** | Isolate Level 2 |
| **all floors** | Show all storeys |
| **hide structure** | Toggle off STR discipline |
| **show structure** | Toggle on STR discipline |
| **hide electrical** | Toggle off ELEC |
| **x-ray on** | Transparency mode |
| **x-ray off** | Restore opacity |
| **wireframe** | Toggle wireframe |
| **screenshot** | Save screenshot to Downloads |
| **fly around** | Start orbit animation |
| **section cut** | Toggle Y-axis section |
| **clear** | Dismiss search results + highlights |

> **Scope note:** Navigation commands are planned for S211 Phase 2. Query commands
> (counting, cost, search) work now. Navigation commands will reuse the same mic +
> NLP bar — no separate UI.

### 8.2 Words to Avoid (misheard by speech engines)

| Avoid | Problem | Say instead |
|-------|---------|-------------|
| "level" | Misheard as "label", "legal" | **"floor"** |
| "storey" | Misheard as "story" (narrative) | **"floor"** |
| "HVAC" | Acronym — engine spells it out | **"air con"** or **"mechanical"** |
| "MEP" | Acronym — engine hears "map" | **"mechanical"** or discipline name |
| "IfcWall" | Technical — engine won't match | **"walls"** (plain English) |
| "qty" | Abbreviation | **"count"** or **"total"** |
| Long sentences | Engine loses accuracy after 5 words | **2-4 word bytes** |

### 8.3 How It Works

```
Phone mic → Web Speech API (browser-native, free)
         → Recognised text (e.g. "floor one doors")
         → NLP intent classifier (keyword match, no AI)
         → SQL template (e.g. SELECT ... WHERE storey LIKE '%1%' AND ifc_class LIKE '%door%')
         → sql.js executes against loaded DB
         → Result toast: "12 doors on Level 1" + [Show in 3D]
```

**Latency:** < 500ms from speech end to result (speech recognition ~1s, SQL ~10ms, render ~50ms).

**Supported browsers:**
- Chrome 33+ (Android, desktop) — `webkitSpeechRecognition`
- Safari 14.1+ (iOS, macOS) — `SpeechRecognition`
- Edge 79+ (desktop) — `SpeechRecognition`
- Firefox: NOT supported (no Web Speech API). Text input only.

**Offline:** Speech recognition requires internet (audio sent to Google/Apple servers for processing).
The NLP + SQL execution is fully offline. If speech unavailable, type the same commands.

### 8.4 Rehearsal — Try These First

New users: practise these five commands to build confidence. Each is two words, phonetically clear.

1. **"count doors"** — instant result, proves the system works
2. **"floor one walls"** — adds floor filter, shows progressive complexity
3. **"total cost"** — switches to cost intent, different result format
4. **"show structure"** — discipline filter, highlights in 3D
5. **"find fire doors"** — free-text search, most flexible

After these five, you can combine any scope + target from the tables above.

---

## 9. Files

| File | Role |
|------|------|
| `deploy/sandbox/index.html` | Modular browser viewer (16 modules) |
| `deploy/sandbox/nlp.js` | NLP query DSL + voice input (S211, to be created) |
| `deploy/sandbox/walk.js` | Walk/Drive-Thru mode, device orientation, wall X-ray |
| `deploy/sandbox/main.js` | Render loop with walkOrientTick |
| `deploy/sandbox/tour.js` | Fly-around, cinematic tour engine |
| `deploy/manifest.json` | PWA manifest (to be created) |
| `deploy/sw.js` | Service worker for offline cache (to be created) |
| `docs/MOBILE_DEPLOY.md` | This spec |
