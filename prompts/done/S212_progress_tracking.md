# S212 — Progress Tracking: Walk + Tap = Site Status

## Status: READY

## Goal
Walk through building, tap elements to mark status: installed / not started / defective. 4D schedule updates live. Site manager does a 10-minute walk → stakeholders see progress heatmap by storey.

## What Already Works
- Drive-Thru walk mode (S208)
- Element picking gives GUID, class, storey (picking.js)
- 4D/5D export with phase mapping (tools.js export4D5D)
- Storey/discipline filtering (panels.js)
- IndexedDB cache (scene.js)

## Spec

### Status Palette (during walk mode)
- Tap element → status picker appears: 🟢 Installed | 🟡 In Progress | 🔴 Not Started | ⚫ Defective
- Tap status → element color changes in 3D, saved to IndexedDB
- Quick mode: tap = cycle through statuses (no picker)

### Data Model (IndexedDB)
```
progress store:
  guid: TEXT PRIMARY KEY
  status: TEXT ('installed'|'in_progress'|'not_started'|'defective')
  updated: TEXT (ISO 8601)
  notes: TEXT
```

### Heatmap View
- Toggle in Tools: "Progress View"
- All elements colored by status (green/yellow/red/black)
- Storey summary: "Level 1: 82% installed, 3 defective"
- Export: Excel with per-element status + storey summary

### Files
- New `progress.js` — status capture, heatmap, persistence
- `picking.js` — status picker on element tap during walk
- `index2.html` — progress panel, heatmap toggle
- `tools.js` — progress export alongside 4D/5D

## Anti-Drift
- Status stored client-side only (IndexedDB). No server.
- Element colors via material override, not geometry changes.
- No orientation changes.
