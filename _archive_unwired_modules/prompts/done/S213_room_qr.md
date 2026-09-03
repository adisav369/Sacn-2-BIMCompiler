# S213 — Room Handover QR: Scan Door → See Room

## Status: READY

## Goal
Generate a QR code per room. Stick on door during construction. Anyone scans → viewer opens zoomed to that room with finishes, MEP, dimensions, snag history. Facilities management starts day one.

## What Already Works
- Deep-link via URL params (config.js ?db=&lib=)
- Fly-to building (streaming.js flyTo)
- Storey/discipline filtering
- Element info panel

## Spec

### QR Generation (offline, from DB)
```sql
-- Generate one QR per IfcSpace
SELECT m.guid, m.element_name, m.storey,
       t.center_x, t.center_y, t.center_z
FROM elements_meta m
JOIN element_transforms t ON m.guid = t.guid
WHERE m.ifc_class IN ('IfcSpace')
```

Each QR encodes a URL:
```
https://.../index.html?db=...&room={guid}&cx={x}&cy={y}&cz={z}
```

### Viewer Behavior on ?room= param
1. Load DB as normal
2. Fly to room center coords
3. Highlight room boundary elements
4. Show room info panel: name, storey, area, finishes
5. List MEP in room, doors, windows
6. If Snag-to-BIM (S209) is implemented, show snag history for this room

### QR Output
- Generate as SVG or PNG (use qrcode.js from CDN)
- Batch export: one PDF page per room with QR + room name + storey
- Print and stick on doors

### Files
- New `qr.js` — QR generation, room deep-link handler
- `config.js` — parse ?room= param
- `streaming.js` — fly-to-room on load
- `index2.html` — room info panel

## Anti-Drift
- QR generation is offline tool, no walk mode interaction
- Deep-link handling is read-only — no DB writes
