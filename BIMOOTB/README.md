# BIM OOTB — Self-Hosted Setup

**Frictionless BIM. Two DBs. One browser. Zero install.**

Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.

---

## What You Get

A complete browser-based BIM viewer that runs from a single folder.
No server. No cloud account. No build tools. No npm.

| Feature | How |
|---|---|
| View any IFC building | Drag-and-drop `.ifc` file onto the page |
| Import other formats | Drop `.obj`, `.dae`, `.glb`, `.fbx`, `.stl`, `.3ds` |
| 3D navigation | Orbit, pan, zoom, walk mode, fly-to |
| Element picker | Click any element to see its IFC properties |
| Storey/discipline filter | Toggle floors and MEP/ARC/STR visibility |
| Section cut | Clip through the building at any plane |
| BOQ / Bill of Quantities | Auto-generated from the model, Excel export |
| 2D Plans | DXF viewer for architectural drawings |
| Variation orders | Compare two versions of the same building |
| X-ray mode | See through walls to inspect internals |
| Screenshot | One-click capture of the current view |
| Works offline | All libraries bundled locally — no CDN dependency |

---

## Quick Start (3 steps)

### 1. Download this folder

Download or clone the `deploy/dev/` folder to your machine.

### 2. Start the server

```bash
cd deploy/dev
./serve.sh
```

Or manually:

```bash
cd deploy/dev
python3 -m http.server 8080
```

### 3. Open your browser

Go to **http://localhost:8080**

Drag any `.ifc` file onto the page. Done.

---

## Folder Structure

```
deploy/dev/
├── index.html              Main viewer page
├── 2d.html                 2D DXF plan viewer
├── boq_charts.html         Bill of Quantities + charts
├── serve.sh                One-click local server
├── loader.js               Progressive library loader
├── streaming.js            DB BLOB → GPU streaming (the core innovation)
├── import_worker.js        IFC parser bridge (calls web-ifc)
├── import_db_builder.js    SQLite schema builder (10-table design)
├── import.js               Drop-zone UI and import orchestration
├── mesh_import_worker.js   OBJ/DAE/GLB/FBX/STL parser
├── picking.js              Click-to-identify from DB
├── navigate.js             Storey/discipline filter + search
├── section_cut.js          Clipping planes from DB geometry
├── elevation.js            2D elevation projection
├── scene_to_db.js          Browser edits → write back to DB
├── ifc_export_worker.js    DB → IFC STEP export (no server)
├── scene.js                Three.js scene setup
├── walk.js                 First-person walk mode
├── sitecam.js              GPS/compass mobile overlay
├── diff.js                 Variation order (design diff)
├── wizard.js               Guided project setup
├── sw.js                   Service worker (offline caching)
├── config.js               Viewer configuration
├── dxf-parser.js           DXF file parser (vendored, MIT)
├── locales/                15 language packs
├── dxf/                    DXF template files
└── lib/                    Third-party libraries (bundled)
    ├── NOTICES.md           License notices for all libraries
    ├── three.min.js         Three.js r128 (MIT)
    ├── OrbitControls.js     Three.js OrbitControls (MIT)
    ├── sql-wasm.js          sql.js 1.10.3 (MIT)
    ├── sql-wasm.wasm        SQLite WASM binary
    ├── web-ifc-api-iife.js  web-ifc 0.0.77 (MPL-2.0)
    ├── web-ifc.wasm         IFC parser WASM binary
    ├── xlsx.full.min.js     SheetJS (Apache-2.0)
    ├── exceljs.min.js       ExcelJS (MIT)
    ├── chart.umd.min.js     Chart.js (MIT)
    ├── FileSaver.min.js     FileSaver.js (MIT)
    ├── fflate.min.js        fflate compression (MIT)
    └── *Loader.js           Three.js format loaders (MIT)
```

---

## Using Pre-Built Buildings from OCI

The viewer can load pre-extracted buildings hosted on Oracle Cloud (OCI).
To point the viewer at a hosted building, use the URL parameter:

```
http://localhost:8080/?db=https://your-bucket.compat.objectstorage.region.oci.customer-oci.com/building_extracted.db
```

This gives you the best of both worlds:
- **Local viewer** — your customisation, your domain, offline-capable
- **Cloud buildings** — large pre-extracted DBs served from OCI

---

## Customisation

### Change the landing page
Edit `index.html` — it's plain HTML/CSS/JS, no framework.

### Add your own buildings
1. Drop an `.ifc` file on the viewer — it extracts to a `.db` file
2. Click **Save** to download the `.db`
3. Host the `.db` on your own server or OCI bucket
4. Link to it: `?db=https://your-server.com/your_building.db`

### Change viewer behaviour
Edit `config.js` — colours, default views, discipline mapping.

### Add your branding
Edit the HTML header, logo, and About panel in `index.html`.

---

## How It Works

```
IFC file (drag-drop)
    │
    ▼
web-ifc WASM (lib/)        ← parses IFC, tessellates geometry
    │
    ▼
import_worker.js            ← transforms, classifies, deduplicates (original code)
    │
    ▼
import_db_builder.js        ← builds 10-table SQLite DB (original code)
    │
    ▼
SQLite DB (in browser)      ← sql.js WASM runs SQL in the browser
    │
    ▼
streaming.js                ← queries DB, streams BLOBs to GPU (original code)
    │
    ▼
Three.js WebGL              ← renders in the browser, no server needed
```

**The innovation:** DB BLOBs ARE the GPU buffers. No intermediate format.
No server-side processing. No proprietary viewer. Two DBs, one browser.

---

## Requirements

- Python 3 (for `http.server` — any static file server works)
- A modern browser (Chrome, Firefox, Edge, Safari)
- That's it. No npm, no node, no build step, no cloud account.

---

## License

BIM OOTB viewer code is MIT licensed.
Third-party libraries in `lib/` retain their original licenses (see `lib/NOTICES.md`).

**Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>**
