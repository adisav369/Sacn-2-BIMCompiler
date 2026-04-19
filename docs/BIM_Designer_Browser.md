# BIM Designer — Browser Edition
> **Foundation:** [BIM_Designer.md](BIM_Designer.md) · [BIM_Designer_SRS.md](BIM_Designer_SRS.md) · [RTreeGuide.md](RTreeGuide.md) · [MANIFESTO.md](MANIFESTO.md)

<div class="bim-banner" markdown>
<b>BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.</b> One HTML file. Two SQLite DBs. Zero server. 126K elements streaming in your browser — no install, no conversion, no vendor lock-in. The DB IS the application.
</div>

**Version:** 0.1 (2026-04-20)
**Status:** SPEC — proven by S200 browser viewer prototype
**Depends on:** `deploy/rtree_browser_demo.html` (working prototype), `sandbox_1M_extracted.db`, `component_library.db`

---

## 1. What Changed — The S200 Proof

S200 proved that a single HTML file + two static DBs + sql.js (WASM SQLite)
renders IFC buildings in the browser with:
- Correct rotation, transparency, original IFC material colours
- Streaming from BLOB geometry (no intermediate format)
- 126K elements (LTU AHouse) renders to completion
- X-Ray mode (Alt+Z), trackpad orbit/pan, flat shading
- No server, no API, no backend

This breaks the client bottleneck. Every Bonsai feature that reads from the DB
(not Blender-specific) can be replicated in the browser. The DB schema IS the API.

### 1.0 The Technology Stack — WebAssembly (WASM)

The enabling technology is **WebAssembly (WASM)** — a binary instruction format
that runs native-speed code inside the browser sandbox. What matters for us:

**sql.js** compiles the entire SQLite C library (~250K lines) to WASM. The browser
downloads a ~1MB `.wasm` file from CDN, and from that point it has a full SQL
engine running locally — the same SQLite that powers every smartphone on earth.
When we call `db.exec("SELECT vertices FROM component_geometries WHERE ...")`,
that query runs inside the browser tab at near-native speed. No server round-trip.

**Three.js** is the 3D rendering library. It wraps WebGL (the browser's GPU API)
and gives us `BufferGeometry` — the ability to hand raw vertex/face arrays to the
GPU. When we unpack a BLOB from SQLite into a `Float32Array`, Three.js renders it
in microseconds. The GPU doesn't care where the data came from.

**The pipeline in the browser:**
```
sql.js (WASM)                    Three.js (WebGL/GPU)
     │                                │
     │  SELECT vertices, faces        │
     │  FROM component_geometries     │
     │  WHERE geometry_hash = ?       │
     ▼                                │
  Uint8Array (BLOB)                   │
     │                                │
     │  new Float32Array(blob)        │
     │  swap axes: IFC→Three.js       │
     ▼                                │
  Float32Array (positions)  ─────────▶ BufferGeometry
                                       │
                                       ▼
                                    GPU renders mesh
```

No intermediate file format. No glTF. No OBJ. No server-side conversion.
The BLOB in the DB IS the mesh. The browser IS the renderer.

### 1.1 What Was Not Possible 3 Years Ago

| Component | 2023 State | 2026 State |
|-----------|-----------|-----------|
| sql.js WASM | Existed but slow, 100MB limit practical | Stable, handles 500MB+ DBs |
| Three.js BufferGeometry | Existed | Same — mature |
| DB-as-model architecture | Did not exist | Our innovation — IFC→DB→BLOB→mesh |
| Browser BLOB streaming | Fetch+parse possible but no one did it from SQLite | Proven at 126K elements |
| httpvfs (range requests) | Experimental | Production-ready, enables true streaming |

The technology was there. The architecture was not.

---

## 2. Deployment Architecture

### 2.1 Static File Deployment (OCI / Any CDN)

```
┌─────────────────────────────────────┐
│  OCI Object Storage (public bucket) │
│                                     │
│  rtree_browser_demo.html  (150KB)   │
│  sandbox_1M_extracted.db  (579MB)   │
│  component_library.db     (456MB)   │
└──────────────┬──────────────────────┘
               │  HTTP GET (static files)
               ▼
┌─────────────────────────────────────┐
│  Browser (any modern browser)       │
│                                     │
│  sql.js (WASM) ← CDN               │
│  Three.js      ← CDN               │
│  OrbitControls  ← CDN              │
│                                     │
│  DB loaded in WASM → SQL queries    │
│  BLOBs → BufferGeometry → render   │
└─────────────────────────────────────┘
```

**No server. No API. No Docker. No backend. Just files.**

### 2.2 Two Loading Modes

| Mode | How | Initial Wait | Best For |
|------|-----|-------------|----------|
| **Full download** | Browser fetches entire DB | Duplex ~1s, Hospital ~11s, AHouse ~25s (50Mbps) | Repeated use, offline review |
| **Range streaming** (httpvfs) | Fetch SQLite pages on demand | 1-2s to first bbox, 5-20s to first building geometry | One-time review, large models |

Both modes use the same HTML file. Toggle via config flag.

### 2.3 Per-Building Split (Optional)

Extract each building to its own DB for faster targeted download:

| Building | Elements | Unique Meshes | Est. Size |
|----------|----------|---------------|-----------|
| Duplex | 1,169 | 650 | ~2 MB |
| Jesse | 676 | 335 | ~1 MB |
| Clinic | 16,480 | 7,654 | ~23 MB |
| Terminal | 48,428 | 7,150 | ~21 MB |
| Hospital | 63,917 | 23,045 | ~68 MB |
| LTU AHouse | 125,698 | 51,392 | ~153 MB |

---

## 3. Feature Roadmap — What Goes In

### Phase 1: Viewer + Inspector (S200 — DONE)

- [x] Building bbox wireframes (city overview)
- [x] Click building → stream geometry from BLOBs
- [x] Per-element rotation (Euler XYZ)
- [x] Material transparency (alpha from material_rgba)
- [x] IFC material colours (discipline fallback for NULL)
- [x] Flat shading (crisp BIM edges)
- [x] Building list panel (clickable cards, sorted by size, filter input)
- [x] Fly-to building
- [x] HUD: building name, streaming progress bar, element flicker
- [x] Trackpad + mouse orbit/pan/zoom (Shift+drag = pan)
- [x] Collapsible panels (−/+ toggle)
- [x] Element picker (click → class, name, GUID, storey, discipline, material)
- [x] Yellow bbox highlight on selected element
- [x] Hover highlight (emissive glow + pointer cursor)
- [x] Discipline toggle (show/hide per discipline, strikethrough when OFF)
- [x] Storey filter (isolate floors, works with discipline filter)
- [x] X-Ray mode (Alt+Z, 15% opacity)
- [x] Fly-around rendered buildings (orbit + auto-transition between buildings)
- [x] Screenshot (camera icon, white flash, downloads PNG)
- [x] Fullscreen (icon + F11)
- [x] Light/dark theme (white background, bboxes hidden for print)
- [x] URL deep-link (hash encodes building + camera position)
- [x] Stream pause/resume (switch buildings, resume where left off)
- [x] Unrestricted orbit (full 360° top-to-bottom)

### Phase 2: Next

- [ ] **Property panel** — selected element's full attribute set from DB
- [ ] **Multi-building streaming** — stream nearest on camera move, shred distant

### Phase 3: Analysis
Query-driven overlays — same DB, richer SQL:

- [ ] **BOQ/Cost overlay** — colour-code elements by cost band (5D)
  Query: join elements_meta → BOM → cost tables
- [ ] **4D timeline** — slider scrubs schedule, elements appear/disappear
  Query: nD engine output tables
- [ ] **Measurement tool** — pick two points, show distance
- [ ] **Section plane** — Three.js clipping plane, slider to cut through floors
- [ ] **Excel export** — SheetJS generates XLSX from SQL query results
  Same BOQ/schedule reports as Bonsai's nD engine

### Phase 4: Composer (future — preserves BIM Designer vision)
This is where the original BIM Designer's building composition returns.
**This is the only phase that needs a backend.**
Phases 1-3 are 100% client-side, zero server.

- [ ] **Grid editor** — drag grid lines on 2D plan overlay, snap-to-grid
- [ ] **Room editor** — drag room bboxes within grid cells
- [ ] **BOM browser** — collapsible tree panel from BOM hierarchy in DB
- [ ] **Product picker** — query component_library for alternative products
- [ ] **Route Walker config** — choose MEP routing strategy, the server generates pipes/ducts
- [ ] **Compile trigger** — send Work Order to backend, receive compiled DB, re-render

---

## 4. Phase 4 Deep Dive — The Modeller Bridge

### 4.1 Why It's Feasible

The compiler is already a pure function:

```
Work Order (JSON) → Java compiler → output.db → browser renders
```

The BIM Designer spec (§11.4) already defines the wire protocol:
```json
→ {"action":"compile","buildingId":"SampleHouse","bomDbPath":"..."}
← {"success":true,"elementCount":58,"compileTimeMs":847,"outputDbPath":"..."}
```

The browser just needs to:
1. Collect parameters (grid, rooms, MEP strategy) into a Work Order
2. POST it to the compiler
3. Receive the compiled DB (or a delta)
4. Re-render with the same streaming pipeline we already have

### 4.2 What the Browser Edits (Macro Level Only)

The browser is NOT a geometry modeller. It edits **parameters** that the compiler
turns into geometry. This is the BIM BOM philosophy — the GUI is a parameter
chooser that triggers compilation.

| Browser UI Control | What It Edits | Compiler Produces |
|--------------------|---------------|-------------------|
| Grid drag | C_OrderLine grid dimensions | Walls, columns at grid intersections |
| Room resize | C_OrderLine bounds (A1-B2) | Floor slabs, room boundaries |
| Room type dropdown | BOM recipe selection | Furniture, fixtures, finishes |
| Storey spinner | Number of storeys | Full floor duplication + stairs |
| Jurisdiction dropdown | AD_Val_Rule set | Compliance validation (UBBL etc.) |
| Route Walker toggle | MEP routing strategy | Pipes, ducts, cables (BIM COBOL verbs) |
| Product swap | M_Product_ID in BOM line | Different door/window/fitting |

**Key insight:** None of these require the browser to understand geometry.
The browser sends macro parameters. The Java compiler does the spatial maths.
The browser re-renders the result.

### 4.3 OCI Architecture for Modeller

```
┌─────────────────────────────────────────────────┐
│  Browser (same HTML + Three.js + sql.js)        │
│                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │
│  │ Viewer   │  │ Inspector│  │ Composer     │  │
│  │ (Phase 1)│  │ (Phase 2)│  │ (Phase 4)    │  │
│  │ renders  │  │ queries  │  │ edits params │  │
│  └──────────┘  └──────────┘  └──────┬───────┘  │
│                                      │ POST     │
└──────────────────────────────────────┼──────────┘
                                       │
                          ┌────────────▼───────────┐
                          │  OCI Container Instance │
                          │  (or Functions/Lambda)  │
                          │                         │
                          │  Java compiler (jar)    │
                          │  - receives Work Order  │
                          │  - compiles to output.db│
                          │  - returns DB or delta  │
                          │                         │
                          │  Cost: ~$0.01/compile   │
                          │  Time: SH=0.8s, TE=30s  │
                          └────────────┬────────────┘
                                       │
                          ┌────────────▼────────────┐
                          │  OCI Object Storage     │
                          │  (static files)         │
                          │                         │
                          │  component_library.db   │
                          │  compiled output DBs    │
                          │  HTML viewer             │
                          └─────────────────────────┘
```

### 4.4 The Bridge — Is It a Hindrance?

**No, and here's why:**

| Concern | Answer |
|---------|--------|
| Latency | SH compiles in <1s. Even TE at 30s is acceptable for a "redesign" action — user edits, clicks Compile, waits, sees result. Not real-time, but responsive. |
| Cost | OCI Container Instances: ~$0.01/compile. Serverless Functions: ~$0.001/compile. No idle cost if using Functions. |
| Complexity | One JAR file, one HTTP endpoint. The existing TCP protocol (§11.4) just wraps in HTTP POST. ~50 lines of servlet code. |
| DB transfer | Compiled output.db for SH = ~2MB. Even TE = ~50MB. Download + re-render = seconds. |
| Offline? | Phases 1-3 work offline. Phase 4 needs network for compile. Acceptable — editing implies saving. |

**The only real constraint:** the Java compiler needs `component_library.db` and
`BOM.db` on the server side. These are already static files — deploy them alongside
the JAR. No database server, no connection pool, just SQLite files.

### 4.5 Edit→Compile→View Loop

```
User drags grid line     →  browser updates Work Order JSON (local, instant)
User clicks [Compile]    →  POST Work Order to OCI endpoint (~1s for SH)
Server compiles           →  returns output.db URL (OCI Object Storage)
Browser fetches output.db →  clears scene, re-streams geometry (~2s for SH)
Total round-trip: ~3-5s for a small house
```

Compare this to Revit: change a wall → regenerate → wait 10-30s for the viewport
to catch up. Our loop is competitive, and the user gets a full BOM + BOQ + 4D
schedule with every compile — not just geometry.

### 4.6 What NOT to Put in the Browser Modeller

| Feature | Why Not | Where Instead |
|---------|---------|---------------|
| Vertex-level editing | Not a mesh editor | Bonsai / Blender |
| Custom BOM authoring | Complex tree editing | Desktop BOM editor |
| IFC import/export | Heavy parsing | Server-side extraction pipeline |
| Multi-user collaboration | Needs conflict resolution | Future phase — OT/CRDT on Work Orders |
| Undo history >1 level | Memory cost | Single undo = revert to previous Work Order |

### 4.7 2D Integration

The 2D Layout pipeline (`2D_Layout/`) produces floor plans from the same compiled DB.
In the browser:

- **2D overlay** — render 2D plan as SVG/Canvas overlay on the 3D view
- **Grid editing** — user drags on 2D plan, sees 3D update after compile
- **Dimension display** — room dimensions from DB, rendered as 2D annotations
- **Toggle** — button to switch between 3D perspective and 2D plan view

The 2D→3D link is the same DB. No separate 2D file — query `element_transforms`
filtered by storey, project to XZ plane, draw walls as lines.

---

## 4. What NOT to Put In (Performance Guards)

| Feature | Why Not | Alternative |
|---------|---------|-------------|
| Real-time shadows | GPU expensive, no value for review | Ambient + hemisphere light |
| Physics / collision | Not a game engine | Visual overlap is fine for review |
| Full IFC schema editor | Scope creep | Edit in Bonsai, view in browser |
| Modifier stacks / GN | Blender-specific | Pre-tessellated BLOBs only |
| Animation / keyframes | Not a timeline tool | 4D = show/hide by schedule |
| Texture maps | Large downloads, marginal value | Flat colour from material_rgba |
| Post-processing (SSAO, bloom) | GPU budget | Clean flat rendering is sufficient |

**Performance contract:** 60fps orbit with up to 50K rendered meshes.
Above 50K, implement shred-on-distance (same as Bonsai Direct Stream).

---

## 5. Relation to Existing Specs

| Spec | Role | Browser Edition Touch |
|------|------|----------------------|
| [BIM_Designer.md](BIM_Designer.md) | Architecture vision (Blender) | Phase 4 adapts composition UX to browser |
| [BIM_Designer_SRS.md](BIM_Designer_SRS.md) | 50 testable requirements | Phase 2-3 implements subset (viewer + inspector) |
| [RTreeGuide.md](RTreeGuide.md) | Blender viewer user guide | Browser edition gets its own guide |
| [MANIFESTO.md](MANIFESTO.md) | DB-as-model philosophy | Browser edition IS the proof |
| [4D5DAnalysis.md](4D5DAnalysis.md) | nD engine spec | Phase 3 queries same tables |

### 5.1 Does This Replace the Blender BIM Designer?

No. They serve different audiences:

| | Blender BIM Designer | Browser Edition |
|---|---|---|
| **Audience** | BIM authors, designers | Stakeholders, reviewers, site teams |
| **Capability** | Full composition + compilation | View + query + analyse |
| **Install** | Blender + addon | Just a URL |
| **Offline** | Yes | Yes (after download) |
| **Edit** | Yes (Work Orders, BOM) | Phase 4 only |
| **Backend** | Java compiler | None (Phases 1-3) |

---

## 6. Quick Start

### 6.1 Local Setup (3 steps)

```bash
# 1. Go to deploy folder
cd deploy

# 2. Start local server (no venv, no install — built-in Python)
python3 -m http.server 8080

# 3. Open in browser
# http://localhost:8080/rtree_browser_demo.html
```

That's it. Two DB files must be in the same `deploy/` folder:
- `sandbox_1M_extracted.db` (579MB) — building data
- `component_library.db` (456MB) — geometry BLOBs

### 6.2 Controls

| Input | Action |
|-------|--------|
| **Drag** | Orbit camera |
| **Shift + Drag** | Pan camera |
| **Right-click drag** | Pan camera |
| **Scroll / Pinch** | Zoom |
| **Click** element | Identify — shows class, name, GUID, storey, material |
| **Alt+Z** | X-Ray toggle |
| **F11** | Fullscreen toggle |

### 6.3 Toolbar Buttons (top-right panel)

| Button | Action |
|--------|--------|
| **Clear** | Remove all streamed meshes |
| **X-Ray** | Toggle 15% transparency on all elements |
| 📷 | Screenshot (saves PNG to Downloads/) |
| ⛶ | Fullscreen |
| ☆ / ☾ | Light/dark theme (white bg hides bboxes for print) |
| ✈ | Fly around rendered buildings |

### 6.4 Panels

| Panel | Position | Shows |
|-------|----------|-------|
| **BIM OOTB** | Top-left | Buildings, elements, streaming progress bar, element flicker |
| **Tools** | Top-right | Filter, buttons, building list (clickable cards) |
| **Info** | Bottom-right | Clicked element metadata (class, GUID, storey, disc, material) |
| **Storeys** | Bottom-left | Floor filter — click to isolate one storey |
| **Disciplines** | Bottom-left | Discipline toggle — click to show/hide ARC, STR, MEP etc. |
| **Status** | Bottom-centre | Current streaming state |

All panels collapse with **−/+**.

### 6.5 Workflow

1. Open the URL → city of bounding boxes appears
2. Click a building in the list (right panel) → flies to it, streams geometry
3. Watch the progress bar and element flicker as it renders
4. Click any element → Info panel shows IFC metadata
5. Filter by storey or discipline (bottom-left panels)
6. Alt+Z for X-Ray, ☆ for white background
7. ✈ to fly around all rendered buildings
8. 📷 for screenshot
9. Switch to another building → first one pauses, click back to resume
10. Share the URL (includes building + camera position in hash)

---

## 7. Action Roadmap — OCI Deployment

### 7.1 Deployment Steps

| Step | Action | Status |
|------|--------|--------|
| 1 | Create OCI Object Storage bucket (public read) | TODO |
| 2 | Upload `rtree_browser_demo.html` → `bim_designer.html` | TODO |
| 3 | Upload `sandbox_1M_extracted.db` | TODO |
| 4 | Upload `component_library.db` | TODO |
| 5 | Update DB_URL / LIB_URL in HTML to OCI Object Storage URLs | TODO |
| 6 | Test public URL — verify city view loads | TODO |
| 7 | Add httpvfs for streaming (avoid full download) | Phase 2 |
| 8 | Deploy Java compiler container for Phase 4 modeller | Phase 4 |

### 7.2 OCI Resource Plan

| Resource | Phase 1-3 (Viewer) | Phase 4 (Modeller) |
|----------|-------------------|-------------------|
| Object Storage | 1 bucket, ~1.1GB | Same + compiled output DBs |
| Compute | None | 1 Container Instance (Java JAR) |
| Network | CDN for static files | + REST endpoint for compile |
| Cost/month | ~$5 (storage + egress) | ~$20 (+ container hours) |
| Domain | Optional (OCI URL works) | Recommended |

### 7.3 Per-Building Deployment (Alternative)

For clients who only need one building:

```bash
# Extract single building to standalone DB
scripts/extract_building.sh T0_Hospital > deploy/Hospital_extracted.db
# Upload: bim_designer.html + Hospital_extracted.db + component_library.db
# Total: ~70MB instead of 1.1GB
```

### 7.4 Deliverables Checklist

- [x] Browser viewer with streaming geometry (S200)
- [x] Rotation, transparency, original IFC materials
- [x] X-Ray mode (Alt+Z), wireframe toggle
- [x] Click-to-identify (double-click → element metadata)
- [x] Storey isolator (floor filter buttons)
- [x] Collapsible panels (−/+ toggle)
- [x] Trackpad + mouse orbit/pan/zoom
- [x] HUD with building name, streaming progress
- [ ] httpvfs streaming (no full download)
- [ ] OCI bucket setup + public URL
- [ ] Per-building DB extraction script
- [ ] BIM Designer rename (rtree_browser_demo → bim_designer)
- [ ] Java compiler container for Phase 4

---

## 8. Implementation Notes

### 8.1 File Structure
```
deploy/
  rtree_browser_demo.html   ← current prototype (rename to bim_designer.html)
  sandbox_1M_extracted.db   ← building data
  component_library.db      ← geometry BLOBs
  OCI_UPLOAD.md             ← deployment guide
```

### 8.2 Key Technical Decisions

1. **No build step.** Single HTML file, CDN dependencies. No npm, no webpack, no React.
   Rationale: matches the DB-as-model simplicity. One file = one deployment unit.

2. **sql.js over REST.** The browser IS the database client. No API layer to maintain.
   Tradeoff: large initial download. Mitigated by httpvfs range requests.

3. **Three.js r128 (stable).** Not latest — proven, small, well-documented.
   OrbitControls included. No module bundler needed.

4. **BufferGeometry from BLOBs.** Same pipeline as Blender's `from_pydata()`.
   Vertex swap: IFC (x,y,z) → Three.js (x,z,-y). Rotation: Euler (rx,rz,-ry).

### 8.3 httpvfs Integration (Phase 2 prerequisite)

```javascript
// Replace full-download init with:
import { createDbWorker } from "sql.js-httpvfs";
const worker = await createDbWorker(
  [{ from: "jsonconfig", configUrl: "/db-config.json" }],
  "/sql.js-httpvfs/sqlite.worker.js",
  "/sql.js-httpvfs/sql-wasm.wasm"
);
// Then use worker.db.exec() same as current db.exec()
```

Pre-build config: `create_lazyfile.sh sandbox_1M_extracted.db > db-config.json`
Upload config + DB to same OCI bucket. No other changes needed.
