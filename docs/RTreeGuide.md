# RTree Guide — City-Scale BIM Viewer
## From Install to 1 Million Elements in 5 Minutes

> **What you'll see:** A federated city of 786 buildings and 1,063,911 IFC
> elements streaming live into Blender. No files to open. No model to load.
> Just a database and a camera.

**This is Guide 1 of 4** in the BIM Compiler learning path:

| Guide | What you learn | Prerequisites |
|-------|---------------|---------------|
| **1. RTree Guide** (this) | View, search, stream IFC models at city scale | Blender + Bonsai |
| 2. BOMTree Guide | Compile BOMs, 4D-8D dimensional queries | RTree Guide |
| 3. Designer Guide | Create buildings from scratch via BIM Designer | BOMTree Guide |
| 4. 2D Layout Guide | Generate architectural drawings from compiled models | BOMTree Guide |

---

## What You Need

| Requirement | Where to get it | Time |
|-------------|-----------------|------|
| **Blender 4.2+** | [blender.org/download](https://www.blender.org/download/) | 2 min |
| **Bonsai addon** | [bonsai.community/download](https://bonsai.community/download) | 2 min |
| **RTree demo databases** | Setup script below | 3 min (1GB download) |

Total: **under 10 minutes**, including download time on a decent connection.

No server. No license. No account. Everything runs locally on your machine.

---

## Step 1 — Install Blender + Bonsai (skip if you have them)

1. Download and install **Blender 4.2+** from blender.org
2. Download the **Bonsai addon** (.zip) from bonsai.community
3. In Blender: **Edit > Preferences > Add-ons > Install** > select the Bonsai .zip
4. Enable the "Bonsai" addon (tick the checkbox)
5. Restart Blender

## Step 2 — Download the demo databases

Open a terminal and run:

```bash
curl -O https://raw.githubusercontent.com/red1oon/BIMCompiler/master/scripts/setup_bomtree_demo.sh
chmod +x setup_bomtree_demo.sh
./setup_bomtree_demo.sh
```

This downloads two SQLite files (~1GB total) to `~/.bim/City/`:

| File | Size | Contents |
|------|------|----------|
| `sandbox_1M_extracted.db` | 579 MB | 1,063,911 elements across 786 buildings — spatial index, transforms, materials |
| `component_library.db` | 456 MB | 124,591 unique tessellated meshes as binary BLOBs |

**Windows users:** run the script in Git Bash, WSL, or download the two
files manually from the URLs printed by the script.

## Step 3 — Preview the city

1. Open Blender (fresh scene)
2. Press **N** to open the side panel
3. Click the **BIM** tab
4. Find **Federation Database Path** and set it to:
   ```
   ~/.bim/City/sandbox_1M_extracted.db
   ```
5. Click **Preview**

**What you see (~5 seconds):** A 1.73 km x 2.48 km city appears as
coloured wireframe boxes — one box per discipline per building. Six
discipline colours: blue (ARC), cyan (STR), green (MEP), yellow (ELEC),
orange (FP), red (ACMV). Each building is a small cluster of nested
coloured boxes. Orbit is instant. Zero mesh. Zero RAM pressure.

> *"I can see the shape of every building in a 786-building city.
> The hospital is clearly bigger. The residential blocks are tiled.
> And it loaded in 5 seconds."*

---

## Step 4 — Start Direct Stream

Press **Ctrl+Shift+A**.

**What happens next — the boxes explode into real buildings:**

### First 3 seconds — the "wow" moment
The coloured boxes nearest to you dissolve as real geometry replaces them.
Walls, roof, facade, railings stream in largest-first. The abstract box
becomes a recognisable building — you can see the entrance canopy, the
wing layout, the window openings.

> *"The box just turned into a hospital."*

### Next 10 seconds
You're orbiting. Buildings within 300m render their exterior envelope.
Interior walls stay hidden — they're behind the facade, invisible from
orbit anyway. A 60,000-element hospital shows only ~850 exterior elements
from this distance. The rest of the city stays as coloured boxes,
waiting to stream when you fly closer.

### Fly inside
Move the camera through a wall or door opening. Interior partitions,
stairs, and columns stream automatically (Shell phase). Then MEP pipes,
electrical conduit, sprinklers appear (Detail phase). Each discipline
keeps its colour.

> *"I can see the HVAC ductwork running above the ceiling tiles."*

### 5 seconds idle — autopilot
Stop moving. The camera pans cinematically to the next unfinished building.
It prefers novel building types: if the city has 100 identical houses and
1 terminal, it visits the terminal first. The boxes ahead of you dissolve
into real buildings as you approach.

> *"It's giving me a tour of the city without me touching anything."*

### Sun button
Click the **Sun** button in the N-panel. Procedural sky, sun lamp with
shadows, ground plane, and EEVEE bloom appear instantly. The viewport is
now presentation-ready. Take a screenshot.

---

## Step 5 — Search and query

Type in the **search box** (N-panel > BIM tab):

| Try searching | What you'll see |
|---------------|-----------------|
| `IfcDoor` | Buildings ranked by door count. Click one to fly there. |
| `IfcWall` | Wall distribution across the city — which buildings have the most. |
| `Hospital` | All tiles of the Hospital type highlighted yellow. |
| `window` | Fuzzy match — finds IfcWindow elements across all buildings. |

Click a **building name** in the results to drill down:
- Discipline bars show ARC/STR/MEP/ELEC/FP breakdown
- Top 10 elements listed with fly-to buttons
- GUID copy to clipboard (one click)
- Storey filter — show only Level 3

Click a **discipline bar** (e.g. the MEP bar) to filter the viewport
to only MEP elements for that building.

> *"I just found every fire sprinkler in a 786-building city
> in under 2 seconds."*

---

## Step 6 — Understand what you're looking at

This is not a 3D model viewer. It's a **database query engine** with a
viewport attached.

```
Traditional viewer:              This system:
  Open model (wait...)             Open database (instant)
  Everything loaded                Nothing loaded
  Navigate what's there            Query what you want
  Search = scroll through list     Search = SQL in milliseconds
  Memory = size of model           Memory = size of what you asked for
```

**The two files you downloaded ARE the model.** There's no IFC file to open,
no conversion step, no import dialog. The IFC was already compiled into
a spatial database with an R-tree index. Every query is O(log n).

At 1,063,911 elements, a traditional viewer would take minutes to open
and struggle to navigate. This system opens in 13 seconds and streams
geometry on demand at 60fps.

---

## Step 7 — Try your own IFC

When you're comfortable with the reference buildings, bring your own.
One button. No command-line. No manual database setup.

1. In the N-panel, click **DROP IFC**
2. A file browser opens — select your `.ifc` file
3. That's it.

**What happens automatically behind the scenes:**

```
Your file: MyHotel.ifc
  │
  ▼  extractIFCtoDB_open.py (runs as background process)
  │
  ├─ ~/.bim/project/MyHotel_extracted.db     ← your building's spatial index
  └─ ~/.bim/project/component_library.db     ← your building's geometry BLOBs
  │
  ▼  auto-sets Federation Database Path
  │
  ▼  Preview loads automatically
  │
  ▼  Press Ctrl+Shift+A → your building streams
```

**What you see:**

- HUD shows: **EXTRACTING MyHotel...** with elapsed time
- After 30-60 seconds: **EXTRACTED — 58,201 elements**
- The DB path box updates to your new database
- Preview loads — your building appears as coloured boxes
- Press Ctrl+Shift+A — your building streams into full geometry

Your IFC file is now a queryable database. Same search, same drill-down,
same discipline colours as the reference buildings. Everything you learned
on the reference gallery works on your own building.

**Where your files live:**

```
~/.bim/
  ├─ City/                              # Reference gallery (read-only)
  │   ├─ sandbox_1M_extracted.db
  │   └─ component_library.db
  │
  └─ project/                           # Your own buildings
      ├─ MyHotel_extracted.db           # Created by DROP IFC
      ├─ component_library.db           # Your geometry BLOBs
      └─ MyHotel_cache.blend            # Auto-saved after first stream
```

Your files never mix with the reference gallery. Drop a second IFC and
it creates another `_extracted.db` alongside the first. Each building
is self-contained.

**Reopen next session:** The cache `.blend` is auto-saved after first full
stream. Next time you open Blender, your building loads instantly from cache
— no re-streaming unless the DB changed.

---

## What concerns this addresses

### "Will it handle my large models?"
You just streamed 1,063,911 elements across 786 buildings. The system
scales to 10M+ elements with no architectural changes. The database is
the ceiling, not the viewer.

### "How is it so fast?"
Three things:
1. **Geometry deduplication** — 1M elements compress to 124K unique meshes.
   `from_pydata()` runs 124K times, not 1M times.
2. **Spatial ordering** — largest elements stream first. Walls and slabs
   give you the building shape after 5% of elements are placed.
3. **No file format** — geometry is binary BLOBs in SQLite. Unpacking is
   `struct.unpack()` + `from_pydata()`. No parsing, no file I/O.

### "What about IFC compliance?"
The data comes from IFC files extracted via IfcOpenShell. Every element
retains its GUID, IFC class, discipline, storey, and material. The
extraction is lossless — you can trace any element back to the source IFC.

### "Do I need a powerful machine?"
A normal laptop is sufficient. The demo runs on Intel UHD integrated
graphics. Direct Stream is budget-capped — it never loads more than
200K elements into the viewport, automatically discarding distant buildings.

### "Is this free?"
Yes. Blender is free (GPL). Bonsai is free (GPL). The BIM Compiler
databases are hosted on Oracle Cloud Free Tier. No license, no subscription,
no account required.

### "What's the catch?"
Direct Stream re-tessellates each session — closing Blender means the
geometry must stream again on reopen. For daily-use projects, an auto-cache
mechanism (coming soon) will save a .blend cache after the first full stream,
giving instant reopen on subsequent sessions.

### "Can I use this for production?"
The viewer is production-ready for model review, coordination, and
querying. When you're ready for the next step — compiling BOMs and
running 4D-8D dimensional queries — see the **BOMTree Guide**.
For designing buildings from scratch, see the **Designer Guide**.

---

## Keyboard reference

| Key | Action |
|-----|--------|
| **N** | Toggle side panel |
| **Ctrl+Shift+A** | Start/stop Direct Stream |
| **Middle mouse** | Orbit |
| **Shift+Middle mouse** | Pan |
| **Scroll wheel** | Zoom |
| **Numpad 5** | Toggle perspective/orthographic |
| **Alt+Z** | Toggle X-ray (see through walls) |

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| "Set federation database path first" | Set the path in N-panel > BIM tab to `~/.bim/City/sandbox_1M_extracted.db` |
| "No component_library.db found" | Ensure `component_library.db` is in the same directory as `sandbox_1M_extracted.db` |
| Streaming is slow | Normal on first run — meshes are tessellated and cached in memory. Second building of the same type streams instantly. |
| Nothing appears after Ctrl+Shift+A | Check Blender console (Window > Toggle System Console) for `§DS_START` log lines |
| HUD not visible | Check that Progress HUD is enabled (it auto-enables on Direct Stream) |
| Viewport is dark | Click the Sun button, or switch viewport shading to Material Preview (Z key) |

---

## Architecture (for the curious)

```
IFC files (source)
  │
  ▼  extractIFCtoDB.py (once, offline)
  │
  ├─ sandbox_1M_extracted.db    ← spatial index + transforms + materials
  │    elements_rtree            (R-tree: O(log n) spatial query)
  │    elements_meta             (GUID, class, discipline, building, storey)
  │    element_instances         (geometry_hash → deduplication key)
  │    element_transforms        (position + rotation per element)
  │    surface_styles            (IFC material colours)
  │
  └─ component_library.db      ← tessellated geometry BLOBs
       component_geometries      (hash → vertices BLOB + faces BLOB — see [SQLite3D_Schema.md](SQLite3D_Schema.md))
       124,591 unique meshes     (shared across 1M elements)

Blender (live, interactive)
  │
  ├─ Preview (RTree GPU)       → 1M wireframe bboxes, zero mesh, instant orbit
  │
  └─ Direct Stream             → SQL query per tick
       → fetch geometry BLOBs  → struct.unpack()
       → mesh.from_pydata()    → link to collection
       → apply material        → Principled BSDF from IFC RGBA
       → apply transform       → Translation + Euler rotation
       → repeat every 100ms    → 500 elements per tick
```

No .blend files. No baking. No server. Two SQLite databases and Blender's
built-in Python API. That's the entire stack.

---

## Links

| Resource | URL |
|----------|-----|
| BIM Compiler docs | [red1oon.github.io/BIMCompiler](https://red1oon.github.io/BIMCompiler/) |
| GitHub repo | [github.com/red1oon/BIMCompiler](https://github.com/red1oon/BIMCompiler) |
| Bonsai addon | [bonsai.community](https://bonsai.community/) |
| Video demo | [YouTube — 1M elements](https://youtu.be/J2MP_q63BNU) |
| osARCH discussion | [community.osarch.org](https://community.osarch.org/) |

---

## What's Next

When you can comfortably navigate, search, and stream IFC models:

- **BOMTree Guide** — Learn how the compiler decomposes buildings into
  Bills of Materials. Run 4D scheduling, 5D costing, 6D carbon queries
  on any compiled building. See how geometry_hash links BOMs to meshes.

- **Designer Guide** — Create buildings from scratch inside Blender.
  Compose BOMs from the component library, compile, and stream your
  design — all without touching an IFC file.

- **2D Layout Guide** — Generate architectural floor plans, sections,
  and elevations from compiled models. Automated dimensioning and
  annotation.

---

*Built on Blender, Bonsai, IfcOpenShell, and SQLite.
All open source. All free. Compile once, query forever.*

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
