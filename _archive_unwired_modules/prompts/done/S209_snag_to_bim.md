# S209 — Snag-to-BIM: Tap Element → Photo → Punch List

## Status: READY

## Goal
During Drive-Thru walk mode, tap any element → phone camera opens → snap defect photo → auto-tagged with BIM metadata → saved to punch list. Export to Excel.

## What Already Works
- Drive-Thru walk mode with device orientation (S208)
- Element picking via raycaster (picking.js) — tap gives GUID, ifc_class, storey, discipline
- Phone camera API (sitecam.js) — snap, preview, download, share
- Excel export (SheetJS loaded via CDN in loader.js)
- Wall X-ray during walk mode (walk.js handleWallXray)

## Spec

### Flow
1. User is in Drive-Thru mode, walking through building
2. Taps an element (wall, pipe, fitting, etc.)
3. Info panel shows element metadata (already works)
4. New button: **📸 Snag** appears in the info panel
5. Tap Snag → phone rear camera opens (fullscreen overlay)
6. Snap photo → preview with element metadata overlay:
   - GUID, IFC class, storey, discipline, element name
   - GPS coords (if available), timestamp
   - Building name
7. Tap **Save** → stored in browser IndexedDB (no server needed)
8. Tap **Cancel** → discard
9. Snag list accessible from Tools panel: **🐛 Snags (N)**
10. Each snag shows: thumbnail, element name, storey, timestamp, status (Open/Fixed)
11. Export button → Excel with columns: #, Photo (base64), GUID, Class, Storey, Discipline, Name, GPS, Date, Status, Notes

### Data Model (IndexedDB)
```
snags store:
  id: auto-increment
  guid: TEXT (element GUID)
  ifc_class: TEXT
  storey: TEXT
  discipline: TEXT
  element_name: TEXT
  building: TEXT
  photo: BLOB (JPEG from camera)
  thumbnail: BLOB (200px resize)
  gps_lat: REAL
  gps_lng: REAL
  timestamp: TEXT (ISO 8601)
  status: TEXT ('open'|'fixed'|'wontfix')
  notes: TEXT
```

### Files to Modify
- `walk.js` or new `snag.js` — snag capture logic
- `picking.js` — add Snag button to element info panel
- `index2.html` — snag list panel, CSS
- `sitecam.js` — reuse camera open/snap logic

### Key Decisions
- Store in IndexedDB (offline-first, no server)
- Photo stored as JPEG blob, thumbnail for list view
- Export to Excel includes base64 photos in cells (SheetJS supports this)
- Snag list persists across sessions (IndexedDB survives page reload)
- Each snag links to element GUID — tap snag in list → fly to element in 3D

## Anti-Drift
- Do NOT modify the orientation/quaternion code in walkOrientTick
- Do NOT add deviceorientation listeners
- Do NOT call controls.update() during walk mode
- Camera open/close must preserve walkModeActive state
