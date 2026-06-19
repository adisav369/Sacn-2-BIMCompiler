# BIM OOTB — Browser Viewer User Guide

> **Foundation:** [BIM_Designer_Browser.md](BIM_Designer_Browser.md) · [BIM_2D_Guide.md](BIM_2D_Guide.md) · [CLASH_DETECTION.md](CLASH_DETECTION.md) · [4D5DAnalysis.md](4D5DAnalysis.md)

---

## Quick Start — your first building

**Zero install.** Everything runs client-side in any browser; download a building once and it is
cached in IndexedDB, so the second visit is instant. Works on desktop and mobile.

You enter the viewer through the **buildings page** — *not* `viewer/viewer.html` directly (that bare
URL opens an unrelated warehouse view). From the [Matrix front door](USER_GUIDE.md) pick the **BIM**
door, or go straight to it:

[**→ Buildings page (gallery.html)**](https://red1oon.github.io/bim-ootb/gallery.html)

![The buildings page — drop a file in the centre zone, or pick a ready-made building from My Buildings / Community Projects](assets/buildings_page.png)

**Three ways in — pick one:**

1. **Pick a ready-made building.** Scroll the **My Buildings** / **Community Projects** gallery and
   tap a card (SampleHouse, Duplex, Clinic, Terminal, Hospital, …). It downloads that building's DB
   (0.5–177 MB), flies to it, and streams the geometry in the 3D viewer.
2. **Drop your own IFC.** Drag an `.ifc` / `.obj` / `.dae` / `.glb` file onto the centre **"Drop IFC
   or 3D file here"** zone (or tap it to browse). The file is parsed in-browser and opens in the viewer.
3. **Add to an existing project.** When you drop a file that matches a building already loaded, a
   prompt offers **Merge** (combine disciplines — `Enter`) or **New** (a fresh building — `Escape`);
   two versions of the same building can be opened side-by-side with **Compare Versions**.

Once the viewer opens:

1. Watch the progress bar and element flicker as geometry streams in.
2. **Click any element** → the Info panel shows its IFC class, name, GUID, storey, discipline, material.
3. **Filter** by storey or discipline (bottom-left panels) to isolate part of the model.
4. **Alt+Z** for X-Ray, **☆** for white background, **✈** to fly around, **📷** for a screenshot.
5. Open another building → the first pauses; tap back to resume.

Full navigation, panels, and the keyboard cheat-sheet are below.

---

## Run it on your own machine

**Local setup (3 steps):**

```bash
# 1. Go to deploy folder
cd deploy

# 2. Start local server
python3 -m http.server 8080

# 3. Open the buildings page in your browser
# http://localhost:8080/gallery.html
```

Per-building DBs must be in `deploy/buildings/`:
- `{Name}_extracted.db` — metadata + transforms
- `{Name}_library.db` — geometry BLOBs (vertices + faces)

Then open `http://localhost:8080/gallery.html` and use the same three ways in as above.

**DB sizes:**

| Building | Elements | Download (ext+lib) |
|----------|----------|--------------------|
| SampleHouse | 65 | 0.5 MB |
| Duplex | 1,169 | 2.8 MB |
| Clinic | 16,480 | 31 MB |
| Terminal | 48,428 | 59 MB |
| Hospital | 63,917 | 88 MB |
| LTU AHouse | 125,698 | 177 MB |

---

## 3D Navigation

| Input | Action |
|-------|--------|
| **Drag** | Orbit camera |
| **Shift + Drag** | Pan camera |
| **Right-click drag** | Pan camera |
| **Scroll / Pinch** | Zoom |
| **Click** element | Identify — shows IFC class, name, GUID, storey, material |
| **Alt+Z** | X-Ray toggle (15% opacity) |
| **F11** | Fullscreen toggle |

**Toolbar buttons (top-right panel):**

| Button | Action |
|--------|--------|
| **Clear** | Remove all streamed meshes |
| **X-Ray** | Toggle 15% transparency on all elements |
| 📷 | Screenshot (saves PNG to Downloads/) |
| ⛶ | Fullscreen |
| ☆ / ☾ | Light/dark theme |
| ✈ | Fly around rendered buildings |

**Panels:**

| Panel | Position | Shows |
|-------|----------|-------|
| **BIM OOTB** | Top-left | Buildings, streaming progress, element flicker |
| **Tools** | Top-right | Filter, buttons, building list |
| **Info** | Bottom-right | Clicked element metadata (class, GUID, storey, disc, material) |
| **Storeys** | Bottom-left | Floor filter — click to isolate one storey |
| **Disciplines** | Bottom-left | Discipline toggle — show/hide ARC, STR, MEP etc. |
| **Status** | Bottom-centre | Current streaming state |

All panels collapse with **−/+**.

---

## Viewer Features

**All browsers (desktop + mobile):**

- 3D orbit, pan, zoom (mouse or touch)
- Click any element → IFC class, GUID, storey, discipline, material
- Fly-tour — auto-orbits rendered buildings, click to stop
- Indoor walk-through — follows IfcSpace/door graph through the building
- X-Ray mode (Alt+Z) — transparent view, see structure through walls
- Measure tool — tap two points, get distance in metres
- Section cut — horizontal clip plane, slider to cut through floors
- Storey filter — isolate a single floor
- Discipline toggle — show/hide ARC, STR, MEP, ELEC, etc.
- Screenshot — saves current view as PNG
- Deep-link URL — camera + building state encoded in hash, shareable
- IndexedDB cache — download once, instant on revisit
- City mode — 786 building bboxes, click to download + stream on demand

**Mobile-only (touch-optimised):**

- Site Camera — phone camera with GPS + compass + timestamp overlay
- BIM picture-in-picture — 3D snapshot composited into the photo
- Markup tools — arrow, circle, freehand draw, text annotations
- Share → WhatsApp (with BIM context baked into image)
- GPS Walk Mode — blue dot tracks position in the model
- Wall X-Ray — tap a wall in Walk Mode to see MEP behind it
- Issue log — capture site issues with photo + GPS + classification, export to Excel

---

## 2D Plans

The 2D floor-plan viewer layers section-cut plans over the 3D model.

→ [2D Plans Guide](BIM_2D_Guide.md)

---

## BOM Compiler DSL Reference

The compiler turns a YAML-like DSL into a verified 3D SQLite building. It is the **[Java pipeline](SourceCodeGuide.md)** — separate from the browser viewer above.

### Overall Structure

```
BUILDING "<name>" [type:<template>] [profile:"<profile>"] [compliance:<mode>] {
    GRID { ... }
    STOREY "<name>" level:<z> height:<h> {
        <room declarations>
    }
    ROOF pitch:<degrees> [overhang:<mm>]
}
```

The DSL is a **catalog selector** — it declares *what* the user wants, not *how* to build it. Room sizes come from the grid, wall types from the profile, furniture from BOM recipes. No coordinates, no geometry.

### GRID Definition

```
GRID {
    axes: A, B, C, D, E / 1, 2, 3, 4, 5
    spacing: 1.3, 3.1, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
}
```

Grid axes define structural column lines. Spacings are in meters between consecutive axes.
Room bounds reference grid intersections (e.g., `bounds:A2-C3`).

### Room / Space Declaration

```
BEDROOM "master" bounds:A2-C3 { ... }
BATHROOM "bath" bounds:B1-C2 { ... }
OPEN_PLAN "common" bounds:B2-D5 { zones: LIVING, DINING, KITCHEN }
```

### Constraint Keywords

| Keyword | Effect | Example |
|---------|--------|---------|
| `exterior: <direction>` | Room has exterior wall on this face | `exterior: south` |
| `adjacent: <room>` | Shares wall with named room (generates door) | `adjacent: living` |
| `opens_to: <room>` | Opens to open-plan area (generates door) | `opens_to: common` |
| `stack: <name>` | Named plumbing stack (groups wet areas) | `stack: plumbing` |
| `compliance: <mode>` | Fire protection compliance mode | `compliance: AUTO_FP` |
| `zones: <list>` | Functional zones within OPEN_PLAN | `zones: LIVING, DINING` |

### OPEN_PLAN

Combines multiple functional zones without internal walls:

```
OPEN_PLAN "common" bounds:B2-D5 {
    zones: LIVING, DINING, KITCHEN
    exterior: south
}
```

### Door and Window Specifications

```
DOOR type:D1 size:900x2100 wall:south
WINDOW type:W1 size:1800x1000 wall:west
```

When type codes are omitted, the compiler resolves families from the profile and room context.

### CORE Block (Vertical Circulation)

```
CORE "main_core" bounds:D3-E4 {
    STAIR width:1.2m
    LIFT capacity:8
    SHAFT type:MEP
}
```

---

### Available Room Types

#### Habitable Spaces

| Type | Aliases | Min Area | Furniture |
|------|---------|----------|-----------|
| BEDROOM | BED, BILIK_TIDUR, BILIK_UTAMA | 6.5 m² | Bed, side table |
| KITCHEN | DAPUR | 4.6 m² | Counter, sink |
| LIVING | RUANG_TAMU | 6.5 m² | Sofa, table |
| DINING | RUANG_MAKAN | 6.5 m² | Table, chairs |
| OFFICE | PEJABAT | 9.0 m² | Desk, chair |
| CLASSROOM | BILIK_DARJAH | 46.5 m² | Desks, sprinklers |
| CANTEEN | KANTIN | 30.0 m² | Tables, sprinklers |

#### Service Spaces

| Type | Aliases | Min Area | Fixtures |
|------|---------|----------|----------|
| BATHROOM | BILIK_MANDI | 2.5 m² | Toilet, sink, exhaust |
| TOILET_BLOCK | TANDAS | 2.5 m² | Toilet, sink |
| STORAGE | STOR | 0 m² | — |

#### Circulation Spaces

| Type | Aliases | Min Width |
|------|---------|-----------|
| CORRIDOR | HALL | 1.8m (educational) |
| LOBBY | FOYER | 3.0m |
| WAITING | — | 3.0m |

#### Combined Spaces

| Type | Description |
|------|-------------|
| OPEN_PLAN | Combined zones — no internal walls between zones |
| PORCH | Covered entry — partial walls (exterior open) |

---

### BOM Resolution

The compiler calculates element quantities from room type, area, and building code rules.

```
ROOM "kantin" [CANTEEN] 84.0m²
    7x pendent         ceil(84.0m² / 12.1) = 7 (NFPA_13 8.6.2.1)
    2x Supply Diffuser ceil(CFM / 600) = 2 (ASHRAE_62_1)
    6x Downlight       ceil(84.0m² x 200 lux / 3000 lm) = 6 (MS_1525)
    9x Canteen Table   ceil(occupancy / 4) = 9 (IBC 1004.5)
```

| Rule | Formula | Code Reference |
|------|---------|----------------|
| PER_AREA | `ceil(area / base)` | NFPA 13 (sprinklers) |
| PER_LUX | `ceil(area x lux / lumens)` | MS 1525 (lighting) |
| PER_CFM | `ceil(cfm / base)` | ASHRAE 62.1 (diffusers) |
| PER_OCCUPANT | `ceil(occupancy / base)` | IBC (furniture) |
| FIXED | `base` | IPC 403.1 (fixtures) |

New BOM recipes require only SQL — no Java changes:

```sql
INSERT INTO m_bom (bom_id, m_product_category_id, is_active)
VALUES ('STUDY_DESK_SET', 'FR', 1);

INSERT INTO m_bom_line (bom_id, role, child_name_pattern, sequence, is_active)
VALUES ('STUDY_DESK_SET', 'DESK', 'Desk%', 1, 1);
```

---

### Profiles and Building Codes

| Profile | Tradition | Wall Thickness | Storey Height |
|---------|-----------|---------------|---------------|
| `UK_Residential` | UK brick-cavity | 290mm | 3.3m |
| `US_Residential` | US frame-with-siding | 417mm | 2.4m |
| `Malaysian_Residential` | MY brick-plaster | 150mm | 2.8m |
| `Malaysian_Institutional` | MY institutional | 150mm | 4.0m |

All metadata lookups are profile-aware: Pass 1 matches the specific profile; Pass 2 falls back to generic rules (profile = NULL).

**Vocabulary mapping (Malaysian):**

| Input | Maps To |
|-------|---------|
| BILIK_UTAMA | MASTER_BEDROOM |
| BILIK_TIDUR | BEDROOM |
| DAPUR | KITCHEN |
| RUANG_TAMU | LIVING |
| BILIK_MANDI | BATHROOM |
| KANTIN | CANTEEN |

---

### Fire Protection

| Trigger | Threshold | Action |
|---------|-----------|--------|
| Building height | > 18m | Sprinklers required |
| Floor area | > 1000m² | Sprinklers required |
| Occupancy | Assembly, High-rise | Sprinklers required |

| Mode | Behavior |
|------|----------|
| (none) | No automatic fire protection |
| AUTO_FP | Generate sprinklers when triggered |
| FULL_COMPLIANCE | All code requirements enforced |

When sprinklers are generated, the pipe network is created:
```
RISER (100mm) -- MAIN (65mm) -- BRANCH (25mm) --> SPRINKLER_HEAD
```
Pipe sizing follows NFPA 13 (Light Hazard).

---

### Output Formats

**SQLite schema:**

| Table | Purpose |
|-------|---------|
| `spatial_structure` | Building hierarchy (Project → Site → Building → Storey) |
| `elements_meta` | Element metadata: guid, ifc_class, name, storey, discipline, material_name, material_rgba |
| `elements_rtree` | Spatial index: id, minX, maxX, minY, maxY, minZ, maxZ |
| `element_transforms` | Per-element world position (center_x/y/z) |
| `surface_styles` | Material rendering properties |
| `assembly_components` | BOM parent-child relationships |
| `mep_systems` | MEP system definitions |
| `system_nodes` / `system_edges` | MEP system graph |
| `simple_qto` | Quantity takeoff (area, volume, length) |

**Build commands:**

```bash
# Compile all 4 buildings
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.SampleHouseEndToEndTest" -q
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNDuplexEndToEndTest" -q
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.TerminalEndToEndTest" -q
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q

# Spatial fidelity check
python3 DAGCompiler/python/spatial_checker.py \
  DAGCompiler/lib/output/samplehouse.db \
  DAGCompiler/lib/input/SampleHouse_extracted.db \
  --discipline ARC
```

---

## Keyboard & Mouse Cheat-Sheet

*Source: BIM_Designer_UserGuide.md §15 — Browser Viewer*

| Key / Gesture | Action |
|---------------|--------|
| **G** | Toggle grid overlay on/off |
| **GF / L1** buttons | Lock to floor plan (section cut + door arcs + opening labels) |
| **Front / Back / Left / Right** | Elevation views |
| **Roof** | Roof plan view |
| **Unlock** (lock icon) | Return to free 3D orbit |
| **Long-press** (~0.4s) on grid line | Start grid drag — 3D planes appear |
| **Drag** after long-press | Move grid line — cost panel updates live |
| **Release** after drag | Commit GRID_MOVE to `kernel_ops` log |
| **Ctrl+Z** | Undo last grid move |
| **Ctrl+Shift+Z** or **Ctrl+Y** | Redo undone grid move |
| **Scissors slider** | Move cut plane — grids recompute at new elevation |
| **Save cut** | Save current section as named view |
| **Alt+Z** | Toggle X-ray mode |
| **F11** | Toggle fullscreen |
| **F5 / reload** | Grid positions restored from `kernel_ops` log — no save needed |

---

## Further Reading

| Doc | What |
|-----|------|
| [ERPUserGuide.md](ERPUserGuide.md) | iDempiere browser ERP — login → install → POS → reporting |
| [BackOfficeUserGuide.md](BackOfficeUserGuide.md) | Server-side portfolio + reports (Java pipeline) |
| [BIM_2D_Guide.md](BIM_2D_Guide.md) | 2D floor-plan viewer |
| [CLASH_DETECTION.md](CLASH_DETECTION.md) | Clash detection engine |
| [4D5DAnalysis.md](4D5DAnalysis.md) | nD analytics (4D–8D) |
| [IFC_ONBOARDING_RUNBOOK.md](IFC_ONBOARDING_RUNBOOK.md) | Onboard your own IFC file |
| [SourceCodeGuide.md](SourceCodeGuide.md) | Developer onboarding, pipeline internals |

---

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
