# ⚠ DO NOT REMOVE
# Scope: S199 — Install script + demo video for community onboarding
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: NEW

## Context

osarch community feedback (discussion/3169): zoomer asks "does it run on my
machine?" steverugi asks "make a video, less acronyms." Both want to try it
but the entry ramp is too steep. S192 specs the addon packaging and onboarding
pipeline. This session creates the install script to test against and the
demo video script to record.

## Part A — Install Script

Test on a clean machine (or fresh Blender install). The script must produce
a working federation addon with Direct Stream capability.

### Prerequisites

- Blender 4.0+ with Bonsai addon installed
- Python 3.10+ (Blender's bundled Python is sufficient)
- No Java required for viewer path (Java only for BOM compilation)
- Platform: Linux, Windows, macOS ARM all supported (SQLite + Blender = cross-platform)

### Install Steps (what the script automates)

```bash
# 1. Package the federation addon as ZIP
python3 scripts/build_addon_zip.py
# Output: federation_addon.zip

# 2. Install into Blender
# User does: Preferences → Install Addon → select federation_addon.zip → enable
# OR headless:
blender --python-expr "import bpy; bpy.ops.preferences.addon_install(filepath='federation_addon.zip')"
```

### Test Matrix

| Step | Action | Expected | Log proof |
|------|--------|----------|-----------|
| 1 | Install addon ZIP in Blender | N-panel shows Federation tab | Visual |
| 2 | Set DB path to a pre-extracted DB | Path accepted, no error | Console |
| 3 | Press RTree | Bounding boxes appear in <1s per 100K elements | §RTREE_LOAD |
| 4 | Press Ctrl+Shift+A (Direct Stream) | Shell streams in 2-3s | §DS_START → §DS_ENVELOPE_DONE |
| 5 | Toggle discipline bar | Elements filter instantly | Visual |
| 6 | Press Sun | Sky + shadows appear | Visual |
| 7 | Run nD (BOQ button) | Excel file opens with quantities | §ND_EXPORT |

### Own-IFC Test (requires S192c --library-db)

| Step | Action | Expected | Log proof |
|------|--------|----------|-----------|
| 1 | Click DROP IFC | File browser opens | Visual |
| 2 | Select .ifc file | HUD: EXTRACTING... | §DROP_IFC → §EXTRACT_START |
| 3 | Wait for extraction | HUD: EXTRACTED, two .db files created | §EXTRACT_DONE |
| 4 | Auto-stream starts | Building appears, shell first | §DS_START |

### Gallery Test (requires S192e OCI fetch)

| Step | Action | Expected | Log proof |
|------|--------|----------|-----------|
| 1 | Open addon (no DB set) | Gallery panel shows reference buildings | §MANIFEST_LOAD |
| 2 | Click Hospital | Download starts | §FETCH_START |
| 3 | Download completes | Auto-stream starts | §FETCH_DONE → §DS_START |

---

## Part B — Demo Video Script

**Duration:** 90 seconds max. No acronyms. No jargon. Show the car driving.

### Shot 1 — Install (15s)

```
[Screen: Blender Preferences → Install Addon]
Narration: "Install the addon. 30 seconds."
[Click ZIP. Enable. N-panel appears with Federation tab.]
```

### Shot 2 — Gallery or DROP IFC (15s)

```
[Screen: N-panel Federation tab]
Option A (Gallery): "Reference buildings are pre-loaded. Pick one."
  [Click Hospital. Building streams from orbit.]
Option B (Own IFC): "Drop your own IFC file."
  [Click DROP IFC. Select file. HUD: EXTRACTING... DONE.]
```

### Shot 3 — Streaming (20s)

```
[Screen: Building streaming — shell appears in 2-3 seconds]
Narration: "Your building. Streaming from a database. No waiting."
[Fly inside. Interior partitions + MEP appear automatically.]
Narration: "Fly inside. Every wall, every pipe, every sprinkler."
```

### Shot 4 — Disciplines (10s)

```
[Screen: Click discipline bars in N-panel]
Narration: "Toggle disciplines. One click."
[ARC off → structure visible. ELEC on → conduit routes appear.]
```

### Shot 5 — Sun (5s)

```
[Screen: Click Sun button]
Narration: "Presentation ready."
[Sky + shadows appear. Screenshot-worthy viewport.]
```

### Shot 6 — BOQ (15s)

```
[Screen: Click BOQ/nD button]
Narration: "Bill of Quantities. Costed. Scheduled. Carbon footprint."
[Excel opens showing BOQ sheet with quantities and costs.]
Narration: "From the same database. Zero manual takeoff."
```

### Shot 7 — Scale (10s)

```
[Screen: Switch to sandbox_1M.db — city view from orbit]
Narration: "One million elements. Two files. No server. Free."
[Fly through city. Buildings stream as camera approaches.]
Narration: "Runs on your laptop."
```

### Closing (5s)

```
[Screen: osarch thread URL or GitHub link]
Narration: "Open source. Try it today."
```

---

## What NOT to show or say in the video

- Java, Maven, ERP.db, BOM compilation
- Tack offsets, iDempiere, YAML classification
- ad_sysconfig, C_Order, M_Product
- "BOM walk", "compilation pipeline", "factored recipe"
- Any terminal/command-line (except briefly for install if needed)
- Any error states or debugging

The video is "watch this work." The engine spec is for developers who ask later.

## Dependencies

- S192c (--library-db flag) — needed for own-IFC path
- S192f (DROP IFC operator) — needed for own-IFC path
- S192g (build_addon_zip.py) — needed for install script
- S192e (OCI gallery) — needed for gallery path (optional for first video)

Minimum viable video: install addon + set DB path manually + Direct Stream +
disciplines + Sun + BOQ. No DROP IFC needed if using pre-extracted DBs.

## Exit Criteria

- [ ] Install script tested on clean Blender (Linux)
- [ ] Install script tested on clean Blender (Windows)
- [ ] All 7 test matrix steps PASS
- [ ] Video recorded (90s max)
- [ ] Video posted to osarch thread (discussion/3169)
- [ ] No jargon in narration — checked by non-technical reviewer
