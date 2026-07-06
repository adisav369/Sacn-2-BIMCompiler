# S246 — Clash Snag: Snap + Annotate + Share from Clash Fly-To

## Status: SPEC

## Goal
When zoomed into a clash (fly-to), a **Snag** button appears. Tap it → camera snaps the viewport (or phone camera for site comparison) → draw annotation lines on the image → auto-tagged with clash metadata (both elements, discipline pair, severity, storey, tolerance) → share via native share sheet (WhatsApp, Telegram, email) or copy deep-link URL. Zero server. Zero login.

## What Already Works (as of S245e)
- Clash DLOD mode: bbox wireframe cloud replaces full scene during clash analysis (measure.js)
- R-tree accelerated clash queries: O(n log N) via `_queryClashesPairRtree()` — fast on mobile
- Clash fly-to with red/blue overlap meshes + bbox wireframes (measure.js `_flyToClash`)
- Matrix shows storey scope in title ("Clash Matrix — Level X" or "Whole Building")
- Auto-enter DLOD on matrix open, auto-exit on matrix close
- Proximity LOD: real meshes loaded near camera target via R-tree (`_updateClashLOD`)
- Snag-to-BIM flow: camera open, snap, IndexedDB store, Excel export (S209)
- Site Camera: photo + overlay + share (sitecam.js)
- Element picking gives GUID, ifc_class, storey, discipline
- Clash row data: element_a, element_b, discipline_pair, overlap_mm, severity, storey
- Working source: `deploy/dev/measure.js` (NOT symlink, own file)
- Deploy: `deploy/dev/*.js` → OCI `bim-ootb-live` `sandbox/*.js`

## The Killer Loop
```
Clash Matrix → Cell Click → Clash Row → Fly-To →
  📸 Snag → Snap viewport / phone camera →
  Draw lines (annotate) → Auto-tag clash metadata →
  Share (deep-link + image) → Recipient opens URL →
  Sees 3D clash in browser, no install
```

## Spec

### Flow
1. User clicks a clash row → camera flies to overlap zone (existing)
2. **Long-press** on clash row → enters snag flow (no extra button — keeps UI clean)
   - Double-tap remains as status change (existing)
3. Snag triggered → two options:
   - **Screen** — captures current WebGL viewport as PNG (clash meshes visible)
   - **Camera** — opens rear camera (site verification: is the pipe really there?)
4. Canvas overlay appears with the captured image:
   - Draw tool: freehand red lines (finger/mouse) for circling the defect
   - Undo button (last stroke)
   - Metadata overlay (bottom strip): discipline pair, severity, storey, tolerance, both element names
5. Tap **Share** → native Web Share API with:
   - Image (annotated PNG)
   - Deep-link URL encoding clash state (see below)
   - Text: "Clash: {disc_a} vs {disc_b} — {element_a_name} / {element_b_name} — Storey {N} — {overlap_mm}mm"
6. Tap **Save** → stored to IndexedDB (same `snags` store as S209, extended fields)
7. Tap **Cancel** → discard

### Deep-Link URL
```
?clash={element_a_guid}|{element_b_guid}&storey={N}&cam={x},{y},{z}&tgt={tx},{ty},{tz}&tol={mm}
```
Recipient opens link → viewer loads → auto-flies to that camera position → highlights the two elements in red/blue → shows clash info bar. No login, no file transfer.

### Open Graph Preview (for chat apps)
When generating the share, embed a `<meta og:image>` in the link preview by encoding the annotated PNG as a data-URI in a minimal HTML wrapper (self-contained, no server). Fallback: just attach the image to the share payload.

### Data Model (IndexedDB extension to S209 snags store)
```
Additional fields for clash snags:
  type: 'clash' (vs 'defect' for S209 snags)
  element_b_guid: TEXT (second element)
  element_b_class: TEXT
  element_b_name: TEXT
  discipline_pair: TEXT (e.g., 'MEP|ARC')
  overlap_mm: REAL
  severity: TEXT ('hard'|'soft'|'clearance')
  tolerance_mm: REAL
  camera_pos: TEXT (JSON: {x,y,z})
  camera_target: TEXT (JSON: {x,y,z})
  annotation: BLOB (annotated PNG)
  deep_link: TEXT (generated URL)
```

### Files to Modify
- `deploy/dev/measure.js` — add Snag button to clash fly-to info bar, generate deep-link
- `deploy/dev/snag.js` (or extend S209's snag logic) — screen capture, annotation canvas, share
- `deploy/dev/sitecam.js` — reuse camera open/snap for "Camera" option
- `deploy/dev/index.html` — annotation overlay CSS, draw canvas
- `deploy/dev/loader.js` — parse `?clash=` URL param on load, trigger auto-fly-to

### Key Decisions
- Reuse S209 IndexedDB store — add `type` field to distinguish clash snags from defect snags
- Annotation is simple freehand red lines — no text boxes, no shapes (keep mobile-friendly)
- Deep-link encodes camera + elements + tolerance — everything needed to reconstruct the view
- Web Share API with fallback to clipboard copy (desktop browsers without share sheet)
- No server dependency — image travels with the share payload, not hosted

### Why This Wins
- Navisworks: find clash → screenshot → open email → type context → attach → wait days
- BIM OOTB: find clash → tap Snag → circle it → share → recipient sees 3D in 10 seconds
- The coordination loop collapses from **days to minutes**
- Works on site: foreman receives link, opens on phone, confirms or disputes immediately
- The annotated image IS the RFI — no separate form, no platform, no login

## Anti-Drift
- Do NOT modify clash query logic in measure.js (SQL, matrix, storey scoping)
- Do NOT change existing S209 snag flow — extend, don't replace
- Do NOT add server endpoints — everything client-side
- Do NOT modify walk mode or orientation code
- Canvas annotation must not interfere with Three.js render loop (overlay div, not same canvas)

## Acceptance
- Clash fly-to shows Snag button
- Screen capture includes red/blue clash meshes
- Freehand annotation works on mobile (touch) and desktop (mouse)
- Share produces image + deep-link + text description
- Deep-link opens on fresh browser and reconstructs the clash view
- Saved clash snags appear in snag list alongside S209 defect snags
- Export to Excel includes clash-specific columns
