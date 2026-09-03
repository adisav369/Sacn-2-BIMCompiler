# ⚠ DO NOT REMOVE — MANDATORY PREAMBLE
# Scope: Mobile/Desktop UX polish — 10 items across toolbar, clash, 2D, import, performance
# After every run: read the log before any conclusion. Exit code is not evidence.
# STATUS: IMPLEMENTED — deployed to ootb-dev, 2 open bugs remain
# RESUME: Read §OPEN BUGS below → fix → test → deploy dev

# S250 — Mobile/Desktop UX Polish

## Overview

Ten items improving mobile experience, desktop-only gating, clash report performance,
2D layout correctness, contributed IFC sharing, and general UX.

## Architecture Context

| File | Concern | Touches |
|------|---------|---------|
| `index.html` | Toolbar, mobile CSS, script tags | §1,2,4,5 |
| `measure.js` | Clash matrix, report, CSV export | §2,3 |
| `grid_views.js` | Ortho camera frustum (2D skewing) | §7 |
| `grid_overlay.js` | Bubble sprites, grid scene | §7 |
| `panels.js` | Swipe gesture → collapse toggle | §5 |
| `picking.js` | BBox highlight correlation | §10 |
| `import.js` | Import cards, Save/Export buttons | §8,9 |
| `main.js` | 2D button handler, mobile gating | §1 |
| `helpers.js` | Bug report FAB (moving to toolbar) | §4 |
| `streaming.js` / `scene.js` | Memory-heavy ops audit | §6 |

---

## §1 — 2D Desktop-Only

### Problem
2D grid overlay (floor plans, elevations) is too heavy for mobile — 10 modules, contour
slicing, dim chains. Mobile users don't need architectural drawings at site.

### Solution
Hide the 2D button on mobile. The `open2DPlans()` function already exists; just gate the icon.

### Implementation
1. In `index.html`, add class `desktop-only` to the 2D button
2. In the `@media (max-width: 600px)` block, add: `.desktop-only { display: none !important; }`
3. In `main.js`, `open2DPlans()` should early-return on mobile with a status message

### Test
- T_S250_01: 2D button has `desktop-only` class
- T_S250_02: CSS hides `.desktop-only` at ≤600px

---

## §2 — Clash Desktop-Only (Remove from Mobile Measure Panel)

### Problem
Clash matrix appears on long-press in measure mode. On mobile this fires accidentally
and the discipline matrix is too cramped on a phone screen. Mobile should only receive
a shareable clash link (deep-link) to view a specific clash, not run the full analysis.

### Solution
1. Gate `_showClashMatrix()` — early-return if mobile
2. In measure.js, the clash icon/trigger that appears below the measure long-press panel:
   skip rendering on mobile
3. Mobile CAN still open a `#clash=guidA~guidB` deep-link (item §3b below) and fly to
   that specific clash + show its info card. That code path stays.

### Implementation
- `measure.js` `_showClashMatrix()`: add `if (_isMobile) { log('§CLASH_MATRIX skip — mobile'); return; }`
- The existing deep-link handler in `main.js:191+` is untouched — mobile can receive links

### Test
- T_S250_03: `_showClashMatrix` guards with `_isMobile` check
- T_S250_04: Deep-link handler has no mobile guard (stays accessible)

---

## §3 — Clash Report: Fast Charts + Background CSV

### Problem
`_buildExportHtml()` renders 6 Chart.js charts AND a 200-row detail table in one pass.
The detail table forces the browser to iterate all clashes, build DOM strings, and
render — blocks UI for seconds on large buildings.

### Solution
Split into two phases:
1. **HTML report (fast):** Only aggregate queries → `COUNT(*)` per pair, per severity,
   per status. Charts render from these counts. No detail rows. Opens instantly.
2. **CSV export (background):** Full detail listing runs via `setTimeout` yield loop,
   builds CSV string in memory, triggers download when done. Status bar shows progress.

### Implementation

#### Phase 1: Light HTML report
- `_buildExportHtml()` skips the detail table section entirely
- Summary stat cards: already use aggregate counts (no change)
- Charts: already use `sevCounts`, `discPairCounts`, `classCounts` — these come from
  the async counting phase which uses `_countClashesRtree()` (single R-tree COUNT query
  per pair). No per-row iteration needed.
- Remove: the `rows.forEach(...)` loop that builds the detail table HTML (lines ~1424-1590)
- Add: a "Download CSV" button in the report header that calls `exportCSVBackground()`

#### Phase 2: Background CSV
- New function `A._exportCSVBackground()`:
  - Queries one discipline pair at a time (already have `_queryClashesPairRtree`)
  - Yields between pairs: `setTimeout(_nextPair, 8)`
  - Builds CSV lines in an array (no DOM)
  - On completion: `Blob` + `URL.createObjectURL` + auto-download
  - Status: `A.status.textContent = 'Exporting CSV... pair 3/12'`
- CSV columns: `#, Element A, Class A, Disc A, Element B, Class B, Disc B, Overlap (m), Severity, Status`

### Test
- T_S250_05: `_buildExportHtml` does NOT contain `#detail-table` or `<tbody>`
- T_S250_06: `_exportCSVBackground` exists and uses `setTimeout` yield pattern
- T_S250_07: CSV header has expected columns

---

## §4 — Help Button in Toolbar

### Problem
Bug report FAB at bottom-right (`#bug-fab`) obscures content, appears/disappears
unpredictably (idle timer), and blocks touch targets on mobile.

### Solution
Move help/bug-report to a permanent toolbar button. Remove the floating FAB.

### Implementation
1. In `index.html` toolbar div, add before the 🌐 Home button:
   ```html
   <button onclick="if(APP&&APP.reportBug)APP.reportBug();event.stopPropagation()"
           data-trl-title="ui_tt_help" title="Help / Report Bug"
           style="background:#444;color:#fff;border:1px solid #666;border-radius:4px;
                  cursor:pointer;font-size:22px;padding:2px;line-height:1">❓</button>
   ```
2. Remove or `display:none` the `#bug-fab` div
3. In `helpers.js`, remove the idle-timer FAB show/hide logic (lines ~310-329)
4. Keep `A.reportBug()` function unchanged

### Test
- T_S250_08: Toolbar contains help button with `reportBug` onclick
- T_S250_09: `#bug-fab` div has `display:none` or is removed
- T_S250_10: `helpers.js` has no idle-timer FAB code

---

## §5 — Collapsible ± Icon (Replace Swipe)

### Problem
Swipe-to-hide-panels is invisible (no affordance), fires accidentally during orbit/pan,
and conflicts with browser back-swipe. Users don't discover it.

### Solution
Replace horizontal swipe with a visible **±** toggle button. One tap hides all panels,
next tap restores them. Explicit, discoverable, no false triggers.

### Implementation
1. In `index.html`, add a fixed toggle button (mobile only):
   ```html
   <button id="panel-toggle-btn" onclick="toggleAllPanels()"
           style="display:none;position:fixed;bottom:16px;left:16px;z-index:100;
                  background:rgba(40,40,40,0.85);color:#4fc3f7;border:1px solid #666;
                  border-radius:50%;width:36px;height:36px;font-size:20px;cursor:pointer">
     −
   </button>
   ```
2. Mobile CSS (`@media max-width:600px`): `#panel-toggle-btn { display:block !important; }`
3. In `panels.js`:
   - Replace swipe event listeners with `toggleAllPanels()`:
     ```javascript
     window.toggleAllPanels = function() {
       panelsHidden = !panelsHidden;
       panelIds.forEach(function(pid) {
         var el = document.getElementById(pid);
         if (el) el.classList.toggle('swipe-hidden', panelsHidden);
       });
       var btn = document.getElementById('panel-toggle-btn');
       if (btn) btn.textContent = panelsHidden ? '+' : '−';
     };
     ```
   - Remove `touchstart` and `touchend` swipe listeners entirely

### Test
- T_S250_11: `#panel-toggle-btn` exists in HTML
- T_S250_12: `panels.js` has no `touchstart`/`touchend` swipe listeners
- T_S250_13: `toggleAllPanels` function exists

---

## §6 — Mobile Memory Audit

### Problem
Mobile Safari/Chrome have ~1GB memory ceiling. Heavy ops cause tab crashes.

### Audit targets (read code, profile, remove or defer)
1. **R-tree eager build** (`measure.js:78-162`): 5000-element batches on load.
   → **Defer** to first clash request, not auto-build.
2. **Geometry merge** (`streaming.js`): Creates large merged InstancedMesh buffers.
   → Already needed for rendering. But check: are disposed geometries GC'd?
3. **Canvas texture leak**: Each bubble/dim-label creates a canvas → texture.
   → Ensure `dispose()` on mode exit. Audit `grid_overlay.js` teardown.
4. **City mode** (`city.js`): Loads `city_index.db` (324KB) + parses 786 buildings.
   → On mobile, skip city mode unless explicitly requested.
5. **Contour slicing** (`section_cut.js`): Iterates all vertices per element.
   → Already gated behind 2D (desktop-only per §1). No mobile path.

### Implementation
- Add `_isMobile` check to R-tree auto-build → defer
- Audit `.dispose()` calls on all grid teardown paths
- City mode: don't auto-load on mobile (user can tap a building card to load)

### Test
- T_S250_14: R-tree build does not auto-start on mobile
- T_S250_15: Grid overlay teardown disposes all textures

---

## §7 — 2D Ortho Aspect Ratio Fix (Bubble Skewing)

### Problem
`grid_views.js:setupCameraForView()` computes ortho frustum from building dimensions
only (`halfW = bldW/2 * margin`, `halfH = bldH/2 * margin`). It ignores viewport
aspect ratio. On a wide screen (16:9), the building is stretched horizontally; on a
tall screen (9:16 phone), stretched vertically. Circular bubble sprites become ovals.

### Maths
Current (broken):
```
halfW = (dims[fw] / 2) * 1.2
halfH = (dims[fh] / 2) * 1.2
```

Fix — fit building in viewport while preserving aspect ratio:
```javascript
var viewportAspect = window.innerWidth / window.innerHeight;
var buildingAspect = halfW / halfH;
if (viewportAspect > buildingAspect) {
  // Wide viewport: expand halfW to match
  halfW = halfH * viewportAspect;
} else {
  // Tall viewport: expand halfH to match
  halfH = halfW / viewportAspect;
}
```

This ensures the ortho frustum matches the viewport aspect ratio. Building is
letterboxed (with margin), never stretched. Sprites stay round.

### Resize handler
On `window.resize`, recompute `halfW`/`halfH` with new aspect and update
`_orthoCamera.left/right/top/bottom` + `updateProjectionMatrix()`.

### Pre-render guard — "bubbles must be round" hard-break
Before rendering 2D mode, validate the ortho frustum. If aspect is wrong, **do not
render** — log error and abort. This catches the skewing before the user sees it.

```javascript
// In grid_views.js setupCameraForView(), after computing halfW/halfH:
var frustumAspect = halfW / halfH;
var viewportAspect = window.innerWidth / window.innerHeight;
if (Math.abs(frustumAspect - viewportAspect) > 0.01) {
  log('§GRID_VIEW ABORT — frustum aspect ' + frustumAspect.toFixed(3) +
      ' ≠ viewport ' + viewportAspect.toFixed(3) + ' — would skew');
  // DO NOT return the camera — caller gets null, no render
  return null;
}
```

Caller (`lockView`) checks for null and refuses to enter 2D mode if guard fires.

### Tests — maths proofs
- T_S250_16: Ortho frustum aspect matches viewport aspect (maths proof)
  ```
  Given: viewport 1920×1080, building 10m×8m (floor plan: fw=W=10, fh=D=8)
  halfW_building = 5 * 1.2 = 6.0
  halfH_building = 4 * 1.2 = 4.8
  buildingAspect = 6.0 / 4.8 = 1.25
  viewportAspect = 1920 / 1080 = 1.778
  1.778 > 1.25 → wide viewport → halfW = 4.8 * 1.778 = 8.533
  frustumAspect = 8.533 / 4.8 = 1.778 ✓ matches viewport
  ```
- T_S250_17: Sprite `scale.set(s, s, 1)` — X equals Y (uniform, always)
- T_S250_18: Resize handler recomputes ortho frustum
- T_S250_19: On viewport aspect 2:1, frustum aspect is 2:1 (not building aspect)
- T_S250_20_GUARD: Pre-render guard rejects mismatched aspect (returns null)
- T_S250_21_GUARD: `lockView` handles null camera (does not enter 2D mode)

---

## §8 — Contributed IFC Upload ("Contribute to Project")

### Problem
Users drop IFC files, import locally. No way to share back. Want a 3rd option on the
import card: "Contribute to Project" → uploads the extracted DB to a shared OCI folder.

### Solution — OCI Pre-Authenticated Request (PAR)
No server needed. Create a read-write PAR on the `bim-ootb-dev` bucket targeting prefix
`contributed/`. Embed the PAR URL in the app config. Browser `PUT`s directly to OCI.

### Implementation
1. **OCI setup** (manual, one-time): Create PAR on bucket `bim-ootb-dev` with:
   - Access type: `ObjectReadWrite`
   - Prefix: `contributed/`
   - Expiry: 90 days (rotate quarterly)
   - The PAR URL goes into `config.js` as `A.CONTRIBUTE_PAR`

2. **Import card** (`import.js`): Add "Contribute" button to card layout:
   ```html
   <button class="open-btn" data-contribute="KEY"
           style="flex:0;padding:6px 10px;background:rgba(76,175,80,0.15);
                  border-color:rgba(76,175,80,0.3);color:#4caf50;font-size:10px">
     ↑ Share
   </button>
   ```

3. **Upload logic** (`import.js`):
   ```javascript
   A.contributeBuilding = async function(key) {
     var record = await getImport(key);
     if (!record || !A.CONTRIBUTE_PAR) return;
     var meta = record.meta;
     var filename = (meta.filename || meta.name).replace(/\.[^.]+$/, '') + '_extracted.db';
     var blob = new Blob([record.data], { type: 'application/octet-stream' });
     var url = A.CONTRIBUTE_PAR + filename;
     A.status.textContent = 'Uploading to shared folder...';
     var resp = await fetch(url, { method: 'PUT', body: blob });
     if (resp.ok) {
       A.status.textContent = 'Contributed: ' + filename;
       // Also upload metadata JSON
       var metaBlob = new Blob([JSON.stringify({
         filename: filename,
         elements: meta.elementCount,
         disciplines: Object.keys(meta.disciplines || {}),
         date: new Date().toISOString(),
         timezone: Intl.DateTimeFormat().resolvedOptions().timeZone
       })], { type: 'application/json' });
       await fetch(A.CONTRIBUTE_PAR + filename + '.meta.json', { method: 'PUT', body: metaBlob });
     } else {
       A.status.textContent = 'Upload failed: ' + resp.status;
     }
   };
   ```

4. **Metadata**: timezone-based region (no IP lookup needed — simpler, no external API dependency):
   `Intl.DateTimeFormat().resolvedOptions().timeZone` → e.g. "Asia/Kuala_Lumpur"

### Test
- T_S250_22: Import card has "Share" button with `data-contribute` attribute
- T_S250_23: `contributeBuilding` function exists in `import.js`
- T_S250_24: Meta JSON includes filename, elements, disciplines, date, timezone

---

## §9 — Browse Contributed Folder (Landing Page)

### Problem
After users contribute, need a way to browse what's available.

### Solution
Add a "Community Projects" section to the landing page (`SYSNOVA/index.html`) that lists
contributed DBs with stats.

### Implementation
1. **List contributed files**: Fetch `contributed/` prefix listing via PAR (OCI list objects).
   Or: maintain a `contributed/index.json` that the upload step appends to.

   Simpler approach: upload step writes/updates `contributed/index.json`:
   ```json
   [
     { "filename": "Hospital_extracted.db", "elements": 4200,
       "disciplines": ["ARC","STR","MEP"], "date": "2026-05-07T10:30:00Z",
       "timezone": "Asia/Kuala_Lumpur" }
   ]
   ```

2. **Landing page section** (`SYSNOVA/index.html`):
   - Section title: "Community Projects"
   - Fetch `contributed/index.json` on page load
   - Render cards similar to archetype cards: filename, element count, discipline bars,
     date, region (derived from timezone)
   - Click → opens viewer with `?db=contributed/FILENAME`

3. **Stats display**: each card shows:
   - Project name (from filename)
   - Element count
   - Discipline breakdown (coloured bars)
   - Date contributed
   - Region (timezone → country/city lookup via simple mapping object, not API)

### Test
- T_S250_25: Landing page has "Community Projects" section
- T_S250_26: `contributed/index.json` schema has required fields

---

## §10 — BBox Selection Correlation Fix

### Problem
When selecting an element, the yellow highlight bbox doesn't align with the element's
actual position. The bbox wireframe appears at the wrong spot.

### Root cause (from `picking.js:240-276`)
The bbox SIZE comes from `element_transforms.bbox_x/y/z` (IFC coords) with axis swap
`IFC(x,y,z) → Three(x,z,y)`. But the POSITION comes from either:
- `hit.object.localToWorld(localCenter)` for merged meshes — uses geometry-local centre
- InstancedMesh matrix decomposition for instanced meshes

The geometry-local centre may differ from the DB `center_x/y/z` because:
1. Geometry is re-centred during extraction (vertices relative to local origin)
2. Instance matrix positions the geometry in world space
3. `localToWorld` on the geometry bbox centre gives the mesh-local midpoint, not the
   IFC element centre

### Fix
Use DB position (`element_transforms.center_x/y/z`) converted via `ifc2three()` as the
bbox centre, not the geometry-local centre:

```javascript
if (guid) {
  try {
    const posRow = A.dbQuery(
      'SELECT center_x, center_y, center_z, bbox_x, bbox_y, bbox_z FROM element_transforms WHERE guid = ?',
      [guid]
    );
    if (posRow.length && posRow[0][0] != null) {
      var tp = A.ifc2three(posRow[0][0], posRow[0][1], posRow[0][2]);
      hlLine.position.set(tp.x, tp.y, tp.z);  // DB centre → Three.js world
      hlSizeX = posRow[0][3]; hlSizeY = posRow[0][5]; hlSizeZ = posRow[0][4]; // axis swap
    }
  } catch(e) {}
}
```

This uses the authoritative IFC centre from the DB, not the geometry-local fallback.

### Test
- T_S250_27: picking.js queries `center_x, center_y, center_z` alongside bbox
- T_S250_28: highlight position uses `ifc2three()` conversion from DB centre
- T_S250_29: Axis swap is `IFC(x,y,z) → Three(x, z, y)` — verified in code

---

## §11 — QR Code Sharing

### Problem
Deep-links (`#clash=guidA~guidB&cam=x,y,z&tgt=...`) are long URLs. Copying and pasting
is clumsy on mobile. At site, workers need to scan a code to jump to the right view.

### Three use cases

#### A: Snag stamp — QR as visual proof of record
When a user snags a location (clash, issue, photo annotation), a QR billboard sprite
appears in the 3D scene at that exact IFC position. This is a **permanent visual stamp**
confirming the issue is recorded in the DB.

**Behaviour:**
- Snag/snap triggers → QR sprite placed at snag IFC coordinates
- QR encodes deep-link: `viewer.html?db=BUILDING#issue=ID&cam=x,y,z&tgt=tx,ty,tz`
- Sprite is a Three.js billboard (always faces camera), ~0.5m world-space size
- Stored in DB: new `issue_snags` table (see schema below)
- Persistent — all snag QR stamps visible when building loads. Walk around, see
  where issues are pinned. Tap a QR stamp → opens issue detail.
- Colour-coded border: red = open, yellow = reviewed, green = resolved

**DB schema:**
```sql
CREATE TABLE IF NOT EXISTS issue_snags (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  guid TEXT,                    -- element GUID (nullable — can snag empty space)
  ifc_x REAL, ifc_y REAL, ifc_z REAL,  -- IFC coordinates of snag point
  cam_x REAL, cam_y REAL, cam_z REAL,   -- camera position at snag time
  tgt_x REAL, tgt_y REAL, tgt_z REAL,   -- camera target at snag time
  label TEXT,                   -- user label (e.g. "COL-A3 clearance")
  status TEXT DEFAULT 'open',   -- open / reviewed / resolved
  deep_link TEXT,               -- full URL with hash
  qr_png BLOB,                 -- QR image as PNG blob
  created_at TEXT DEFAULT (datetime('now')),
  clash_pair TEXT               -- optional: "guidA~guidB" if from clash snag
);
```

**Scene rendering on load:**
```javascript
// On building load, query issue_snags, render QR stamps
A._renderSnagStamps = function() {
  var rows = A.dbQuery('SELECT id, ifc_x, ifc_y, ifc_z, label, status, deep_link FROM issue_snags');
  rows.forEach(function(r) {
    var pos = A.ifc2three(r[1], r[2], r[3]);
    var canvas = generateQR(r[6], 128);
    // Add coloured border based on status
    var borderColor = r[5] === 'resolved' ? '#4caf50' : r[5] === 'reviewed' ? '#ffeb3b' : '#f44336';
    addQRBorder(canvas, borderColor);
    var texture = new THREE.CanvasTexture(canvas);
    var mat = new THREE.SpriteMaterial({ map: texture, depthTest: false });
    var sprite = new THREE.Sprite(mat);
    sprite.position.set(pos.x, pos.y, pos.z);
    sprite.scale.set(0.5, 0.5, 1);
    sprite.renderOrder = 1002;
    sprite.userData = { snagId: r[0], deepLink: r[6], label: r[4] };
    A._snagGroup.add(sprite);
  });
};
```

#### B: Share with QR + link
When sharing a snag (email/WhatsApp/clipboard):
- Message contains the **clickable deep-link URL** (recipient taps to open)
- AND the **QR image** attached/embedded (recipient can show it to someone
  at site who scans with camera)
- Both encode the same URL — two paths to the same view

**NOT a replacement** for the URL — the URL is primary (clickable in chat).
The QR is supplementary for the screen→camera and paper→camera path.

#### C: Printed site spot QR sheet
Desktop user creates QR codes for specific locations (column junctions, MEP risers,
problem areas). Print sheet of QR labels. Stick them at site. Worker scans → sees
BIM model from that exact viewpoint.

**Flow:**
1. Desktop user navigates to a spot, clicks "Pin QR" (new toolbar action or right-click menu)
2. Creates an `issue_snags` record with label
3. QR stamp appears in scene (use case A)
4. "Print QR Sheet" button collects all snag stamps → renders A4 page with grid of
   QR cards: QR code, label, building name, date, status colour
5. Print → stick at site → workers scan → see BIM at that viewpoint

### Implementation — QR generation library
Use `qrcode-generator` (Kazuhiko Arase, MIT, 4KB minified, no dependencies):
```javascript
// No npm — single script tag:
// <script src="qrcode.min.js"></script>
// Or inline the 4KB source in helpers.js

function generateQR(url, size) {
  var qr = qrcode(0, 'M');  // auto type, medium error correction
  qr.addData(url);
  qr.make();
  // Returns canvas element
  var canvas = document.createElement('canvas');
  var cellSize = Math.floor(size / qr.getModuleCount());
  var totalSize = cellSize * qr.getModuleCount();
  canvas.width = totalSize;
  canvas.height = totalSize;
  var ctx = canvas.getContext('2d');
  for (var r = 0; r < qr.getModuleCount(); r++) {
    for (var c = 0; c < qr.getModuleCount(); c++) {
      ctx.fillStyle = qr.isDark(r, c) ? '#000000' : '#ffffff';
      ctx.fillRect(c * cellSize, r * cellSize, cellSize, cellSize);
    }
  }
  return canvas;
}
```

### QR share panel
```javascript
A.showQRShare = function(url, label) {
  var overlay = document.createElement('div');
  overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;' +
    'background:rgba(0,0,0,0.7);z-index:9999;display:flex;align-items:center;' +
    'justify-content:center;flex-direction:column;cursor:pointer';
  var canvas = generateQR(url, 280);
  canvas.style.cssText = 'border:12px solid white;border-radius:8px;cursor:pointer';
  // Click QR = open the link; scan QR = same URL on another device
  var link = document.createElement('a');
  link.href = url;
  link.target = '_blank';
  link.appendChild(canvas);
  overlay.appendChild(link);
  var lbl = document.createElement('div');
  lbl.style.cssText = 'color:white;margin-top:12px;font-size:14px;text-align:center';
  lbl.textContent = (label || 'Scan to share') + ' · Tap to open';
  overlay.appendChild(lbl);
  // Click background (not the QR link) = dismiss
  overlay.onclick = function(e) { if (e.target === overlay) overlay.remove(); };
  document.body.appendChild(overlay);
};
```

### Printable QR sheet
```javascript
A.printQRSheet = function(spots) {
  // spots = [{ url, label, building, date }]
  var html = '<html><head><style>' +
    'body{font-family:sans-serif;margin:20px}' +
    '.card{display:inline-block;width:180px;border:1px solid #ccc;' +
    'padding:12px;margin:8px;text-align:center;page-break-inside:avoid}' +
    '.card canvas{display:block;margin:0 auto 8px}' +
    '.label{font-weight:bold;font-size:12px}' +
    '.meta{font-size:9px;color:#666;margin-top:4px}' +
    '</style></head><body>';
  spots.forEach(function(s) {
    var qr = generateQR(s.url, 160);
    html += '<div class="card">' +
      '<canvas width="' + qr.width + '" height="' + qr.height + '"></canvas>' +
      '<div class="label">' + s.label + '</div>' +
      '<div class="meta">' + s.building + ' · ' + s.date + '</div></div>';
  });
  html += '</body></html>';
  var w = window.open('', '_blank');
  w.document.write(html);
  w.document.close();
  // Redraw QR canvases in the new window
  // (canvas elements don't transfer — regenerate in new context)
};
```

### Files
- `deploy/dev/qrcode.min.js` — 4KB library (or inline in helpers.js)
- `deploy/dev/helpers.js` — `generateQR()`, `showQRShare()`, `printQRSheet()`
- `deploy/dev/measure.js` — QR option in snag share
- `deploy/dev/index.html` — script tag for qrcode lib

### Test
- T_S250_30: `generateQR` returns a canvas with non-zero dimensions
- T_S250_31: QR URL includes `#issue=` or `#cam=` and `&tgt=` parameters
- T_S250_32: `issue_snags` table schema has all required columns (ifc_x/y/z, cam, tgt, label, status, deep_link, qr_png)
- T_S250_33: Snag stamp sprite uses status-based border colour (red/yellow/green)
- T_S250_34: `_renderSnagStamps` queries `issue_snags` and creates sprites
- T_S250_35: Share message contains both URL text and QR image
- T_S250_36: `printQRSheet` generates HTML with `.card` elements matching snag count
- T_S250_37: QR library is ≤10KB (no heavy dependencies)
- T_S250_38: Snag QR stamp is persistent — survives building reload (DB-backed)

---

## OPEN BUGS (next session)

### BUG-1: BBox highlight wrong position on element click — FIXED
**Root cause:** Three mesh types, only merged mesh was broken:
- **InstancedMesh / Individual Mesh**: geometry bbox + localToWorld/instanceMatrix → correct position & size.
- **Merged Mesh (mobile)**: geometry bbox covered the ENTIRE merge group (all elements in storey|disc|rgba bucket). Both position (group center) and size (group extent) were wrong for the individual element.
**Fix (picking.js):** When `hit.object.userData.isMerged`, query `element_transforms` for per-element `center_x/y/z` + `bbox_x/y/z`. Position via `ifc2three()`, size via IFC→Three axis swap (X,Z,Y). Fallback to `hit.point` with 0.3m box on DB miss.
**Bonus:** Individual Mesh now copies `hit.object.quaternion` to highlight (was missing rotation).
**Debug:** `§BBOX_DEBUG` logs on every pick — compares geometry-derived vs DB position with delta (Δ).
**Files:** `deploy/dev/picking.js` lines 265-350

### BUG-2: Panel click seeps through to canvas on mobile — FIXED
**Root cause:** Mobile touch events on overlapping panel divs propagate to `A.canvas` listeners.
**Fix (picking.js):** Added `e.target !== A.canvas` guard in both `pointerdown` (line 94) and `pointerup` picking handler (line 154). Logs `§PICK_GUARD` when blocked.
No `stopPropagation()` on panels (respects clash panel constraint). No HTML/CSS changes.
**Files:** `deploy/dev/picking.js` lines 93-160

---

## Session Plan (DONE — all items implemented)

Items are independent — can be done in any order. Suggested grouping:

| Session | Items | Theme |
|---------|-------|-------|
| A | §1, §2, §4, §5 | Toolbar + mobile UX (HTML/CSS/panels) |
| B | §3 | Clash report refactor (measure.js heavy) |
| C | §7, §10 | Geometry maths (ortho aspect + bbox position) |
| D | §6 | Memory audit (profiling + deferred init) |
| E | §8, §9 | Contributed folder (OCI PAR + landing page) |
| F | §11 | QR code sharing + printable site spots |

---

## Files Reference

| File | Items |
|------|-------|
| `deploy/dev/index.html` | §1, §2, §4, §5, §7, §11 |
| `deploy/dev/measure.js` | §2, §3, §11 |
| `deploy/dev/grid_views.js` | §7 |
| `deploy/dev/grid_overlay.js` | §7 |
| `deploy/dev/panels.js` | §5 |
| `deploy/dev/picking.js` | §10 |
| `deploy/dev/import.js` | §8 |
| `deploy/dev/helpers.js` | §4, §11 |
| `deploy/dev/main.js` | §1, §2 |
| `deploy/dev/streaming.js` | §6 |
| `deploy/dev/scene.js` | §6 |
| `deploy/dev/config.js` | §8 |
| `deploy/dev/qrcode.min.js` | §11 (NEW — 4KB QR lib) |
| `SYSNOVA/index.html` | §9 |
| `deploy/dev/clash_rules.json` | §3 (read only) |
