# BIM Designer — Browser Edition
> **Foundation:** [BIM_Designer.md](BIM_Designer.md) · [RTreeGuide.md](RTreeGuide.md) · [MANIFESTO.md](MANIFESTO.md) · [4D5DAnalysis.md](4D5DAnalysis.md)

<div class="bim-banner" markdown>
<b>BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.</b> One HTML file. Two SQLite DBs. Zero server. 126K elements streaming in your browser — no install, no conversion, no vendor lock-in. The DB IS the application.
</div>

**Version:** 0.1 (2026-04-20)
**Status:** SPEC — proven by S200 browser viewer prototype
**Depends on:** `deploy/rtree_browser_demo.html` (working prototype), `sandbox_1M_extracted.db`, `component_library.db`

<figure style="margin: 20px 0;">
<img src="../assets/images/OOTB.png" alt="BIM OOTB — Browser-native BIM viewer" style="width:100%; border:1px solid #ccc;"/>
<figcaption style="text-align:center; font-style:italic; color:#666; margin-top:8px;">BIM OOTB — 126K elements streaming in the browser. No install, no server, no conversion.</figcaption>
</figure>

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

### 1.1 What Changed — Honest Assessment

Individual components existed before. What's new is the **combination at scale**
and the elimination of server-side infrastructure.

| Component | Prior Art | What We Added |
|-----------|-----------|---------------|
| sql.js WASM | Existed since 2016. Could load large DBs if browser had RAM. | httpvfs range requests (production-ready 2024) eliminate full download — fetch only the SQLite pages needed. This is the enabling breakthrough. |
| Three.js BufferGeometry | Mature since 2015. Every web viewer uses it. | Nothing — we use it as-is. |
| DB-as-model | IFC.js (2020) parses IFC in WASM. Speckle (2019) serves objects via API. | Different architecture: we store **pre-tessellated BLOBs** in SQLite alongside metadata. No re-parsing, no API server. The browser queries the same DB for both geometry and properties. IFC.js re-parses geometry each time. Speckle requires a server. |
| Browser BLOB streaming | glTF/OBJ loading in browsers since 2015. IFC.js streams parsed geometry. | Streaming from **SQLite BLOBs** (not file formats) means geometry and metadata share one query layer. No export/import step — the BLOB is a column, not a file. |
| nD analytics (4D-8D) | SQL queries on element attributes — the operations themselves are standard relational algebra. | The novelty is the **template-driven engine** + **single-DB pipeline**: 5 dimensions from one `_extracted.db`, user-editable JSON templates, self-healing dependency chain, 8.7s at 1M elements. Commercial tools charge $60-180K/yr and silo each dimension behind separate vendors. |

**What's genuinely new:** the full pipeline — IFC → extracted DB → pre-tessellated BLOBs →
browser renders geometry AND runs analytics from the same SQL layer, with zero server
infrastructure. The components existed. The integration at 126K elements with 60fps did not.

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

### Phase 3: Analysis — nD Engine in the Browser
Query-driven overlays — same DB, richer SQL. The **nD engine** ([4D5DAnalysis.md](4D5DAnalysis.md))
already produces 4D-8D tables from JSON templates + `elements_meta`. Phase 3 ports this
to JavaScript (same SQL queries, same templates, same `_extracted.db` via sql.js).
No server — the browser IS the analytics engine.

- [ ] **nD engine (JS port)** — port `scripts/nD_engine.py` (~800 LOC) to browser JS
  Same JSON templates (`templates/*.json`), same SQL, same output tables.
  User drags custom `5D_rates.json` onto page → browser re-runs 5D with local rates.
  Self-healing: request 6D → auto-generates 5D if missing (same as Python engine).
- [ ] **BOQ/Cost overlay** — colour-code elements by cost band (5D)
  Query: `simple_qto` table (generated by browser nD engine or pre-baked in DB)
- [ ] **4D timeline** — slider scrubs schedule, elements appear/disappear
  Query: `construction_schedule` table → show/hide meshes by `start_date`
- [ ] **6D carbon heatmap** — colour intensity by embodied carbon per element
  Query: `carbon_audit` table
- [ ] **7D asset register** — click element → warranty, service interval, replacement year
  Query: `asset_register` table
- [ ] **8D safety overlay** — risk-level colour coding (LOW=green → VERY HIGH=red)
  Query: `hazard_register` table
- [ ] **Measurement tool** — pick two points, show distance
- [ ] **Section plane** — Three.js clipping plane, slider to cut through floors
- [ ] **Excel export** — SheetJS generates XLSX from SQL query results in-browser
  Same BOQ/schedule/carbon/asset/safety reports as `nD_engine.py --all`
- [ ] **Template editor** — HTML form to load/validate/save JSON templates (5D rates, 6D carbon, etc.)
  QS edits rates in browser, re-runs 5D, downloads Excel. Zero install.

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
| `internal/BIM_Designer_SRS.md` | 50 testable requirements (archived) | Phase 2-3 implements subset (viewer + inspector) |
| [RTreeGuide.md](RTreeGuide.md) | Blender viewer user guide | Browser edition gets its own guide |
| [MANIFESTO.md](MANIFESTO.md) | DB-as-model philosophy | Browser edition IS the proof |
| [4D5DAnalysis.md](4D5DAnalysis.md) | nD engine spec (4D-8D) | Phase 3 ports engine to JS — same templates, same SQL, browser-native |

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

### 6.0 Live Demo — Zero Install

Open in any browser (desktop or mobile):

| Link | What | Download |
|------|------|----------|
| [**BIM OOTB Demo**](https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb/o/index.html) | Duplex building — full download, auto fly-around | ~3 MB |
| [**BIM OOTB City**](https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/index.html) | 37 buildings, 1M elements — httpvfs range streaming | ~5-10 MB per building |

**Demo** loads the Duplex (~3MB), works on any phone or desktop.
**City** uses httpvfs (HTTP Range requests) to stream SQLite pages on demand —
no 1GB download. Click a building, only its data is fetched.

Both URLs work on desktop and mobile. Same viewer, same controls.

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
| 1 | Create OCI Object Storage bucket (public read) | **DONE** |
| 2 | Upload viewer HTML (with progress loader) | **DONE** |
| 3 | Upload per-building DBs (14 archetypes) | **DONE** |
| 4 | Auto-detect OCI base URL in HTML | **DONE** |
| 5 | Landing page with manifest (30 archetypes, 11.8KB) | **DONE** |
| 6 | CORS configuration for Range headers | **DONE** |
| 7 | httpvfs streaming for large buildings | **WIP** — speed issues on 579MB DB |
| 8 | Deploy Java compiler container for Phase 4 modeller | Phase 4 |

### 7.2 OCI Resource Plan

| Resource | Current (Always Free) | Phase 4 (Modeller) |
|----------|----------------------|-------------------|
| Object Storage | 3 buckets, ~1.5GB used of 20GB free | Same + compiled output DBs |
| Compute | None | 1 Container Instance (Java JAR) |
| Network | CDN for JS libs, OCI for DBs | + REST endpoint for compile |
| Cost/month | **$0 (Always Free tier)** | ~$20 (+ container hours) |
| Domain | OCI URL (working) | Recommended |

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

---

## 9. FAQ — Addressing Common Critiques

### 9.1 "Where's the property panel? Without it, this is just a pretty viewer."

**Already shipped in S200 (Phase 1).** Click any element → Info panel shows IFC class,
name, GUID, storey, discipline, and material. Storey filter isolates floors. Discipline
toggle shows/hides ARC, STR, MEP. All panels collapsible.

<figure style="margin: 20px 0;">
<img src="../assets/images/OOTBpanels.png" alt="BIM OOTB — Storey filter, discipline toggle, property inspector" style="width:100%; border:1px solid #ccc;"/>
<figcaption style="text-align:center; font-style:italic; color:#666; margin-top:8px;">Storey filter (bottom-left), discipline toggle, and element property inspector (bottom-right) — all shipped in S200.</figcaption>
</figure>

### 9.2 "What's the workflow for getting new IFC files into this system?"

Self-service pipeline, proven on 12+ buildings:

```bash
# One-command onboarding — zero code changes
./scripts/onboard_ifc.sh --prefix XX --type House --name 'My Building' --ifc path/to/model.ifc

# Or step-by-step:
python3 scripts/ifc_recon.py path/to/model.ifc           # 1. Recon (30s)
python3 scripts/extractIFCtoDB_open.py model.ifc out.db   # 2. Extract to DB
```

Full guide: [IFC Onboarding Runbook](IFC_ONBOARDING_RUNBOOK.md) (8 steps, self-service).
Platform setup: [Systems Installer Guide](SYSTEMS_INSTALLER_GUIDE.md) §1–2.

### 9.3 "1.1GB full download is a non-starter for mobile."

True for the full sandbox (37 buildings). Mitigations:

1. **Per-building split** — extract one building to its own DB. Duplex = ~2MB,
   Clinic = ~23MB, Hospital = ~68MB. See §2.3.
2. **httpvfs range streaming** — fetch SQLite pages on demand. 1-2s to first bbox.
   Implementation spec'd in §8.3, production deployment is the next technical milestone.
3. **Most real-world use is single-building** — a site supervisor reviews one building,
   not the whole city. Per-building DBs are the default deployment unit.

### 9.4 "Geometry dedup — wouldn't glTF instancing be more efficient?"

The DB already deduplicates at the `geometry_hash` level — same mesh BLOB stored once,
referenced N times by elements sharing that hash. The Three.js side currently creates
one `BufferGeometry` per element. Upgrading to `InstancedMesh` for repeated hashes
is a rendering optimization (~2x memory reduction for repetitive buildings like hospitals)
— it's an enhancement, not an architecture change. The instancing data is already in the DB.

### 9.5 "Schema changes require redeploying DBs. No versioning story."

The schema mirrors the IFC entity model, which moves slowly (IFC4 released 2013,
IFC4.3 released 2024). The `elements_meta` + `component_geometries` tables haven't
changed since the architecture stabilized. For nD analytics, the output tables
(`simple_qto`, `construction_schedule`, etc.) are generated on the fly by the nD engine
— they don't need to ship in the DB.

### 9.6 "Phase 4 is years away from Revit parity."

**Phase 4 is not trying to replace Revit.** Revit is a geometry modeller.
Phase 4 is a **parameter editor** that triggers compilation — grid drag, room resize,
BOM recipe swap, jurisdiction dropdown. The browser sends macro parameters, the Java
compiler does the spatial maths, the browser re-renders the result. See §4.2.

Different category, different audience: stakeholders, reviewers, site teams — not
CAD operators. For authoring-grade modelling, use [Bonsai/Blender](https://bonsaibim.org/).

### 9.7 "No collaboration story."

By design. This is a **review deliverable**, not a collaboration platform.
The workflow: author in Bonsai → compile → deploy URL → stakeholders review a frozen
snapshot. The URL includes building + camera position (deep-link hash). For multi-user
editing, the Phase 4 backend would accept Work Orders — conflict resolution via
last-write-wins on the compile endpoint (same as a CI pipeline). CRDT/OT is future scope,
spec'd in §4.6.

### 9.8 "The nD analytics (4D-8D) are speculative."

The nD engine is **working code** — `scripts/nD_engine.py`, proven at 1,063,563 elements
across 37 buildings + 1M-element sandbox city. 5 dimensions in 8.7 seconds.
Excel export (township-level BOQ with 30 archetype sheets) already ships.
See [4D5DAnalysis.md](4D5DAnalysis.md) for full results, test witnesses, and fleet report.

The browser port is ~800 lines of SQL queries + arithmetic + JSON template parsing.
Same templates, same DB, same output tables — just JavaScript instead of Python.
This is not speculative; it's a straightforward port of proven code.
