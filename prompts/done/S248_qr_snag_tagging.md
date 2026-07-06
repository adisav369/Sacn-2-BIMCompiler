# S248 — QR Decal Site Tagging System

# ⚠ DO NOT REMOVE
# Scope: QR code decals on 3D surfaces + structured issue queue + shareable links.
# Read the log after every run.

## Status: READY

## Goal
Tap an element → QR code decal appears on its 3D surface → issue added to queue → shareable link generated (same pattern as clash deep-link). The QR encodes the link. Anyone scanning the QR (or tapping it in-viewer) sees the exact issue.

## Spec: `docs/SITE_TAGGING_SRS.md` §2–§3

## ⚠ SAFETY — DO NOT BREAK EXISTING WORKFLOW
- Walk mode, snag-to-BIM, clash detection, matrix, fly-to, deep-link share, review status, HTML export are ALL WORKING IN PRODUCTION.
- Read `docs/CLASH_DETECTION.md` and `docs/MOBILE_DEPLOY.md` before touching anything.
- This is a NEW module (`tagging.js`) — do NOT modify `measure.js` or `walk.js` except to add hooks where clearly needed.
- Existing picking/raycaster in `picking.js` must not be disrupted — QR decals get their own raycaster group checked first, fall through to element picking if no QR hit.
- Test existing Walk + Clash + Snag flow end-to-end after changes.

## Prerequisites
- Vendor `qrcode-generator` (MIT, ~4KB minified) as `deploy/dev/qr-lib.min.js`
  Source: https://github.com/nickg/qr-code-generator or kazuhikoarase original
- THREE.DecalGeometry — available in Three.js examples (already using Three.js)

## What Already Works
- Element picking via raycaster (`picking.js`) — tap gives GUID, ifc_class, storey, hit point, face normal
- Clash deep-link share pattern (`measure.js` §3.7) — URL with `?db=...&cam=...&tgt=...`
- Walk mode with GPS (`walk.js`)
- Camera snap (`sitecam.js`)
- Issue log concept in snag-to-BIM (`S209`)
- localStorage for clash review statuses

## Implementation

### Phase 1 — Issue Data Model + Queue (no QR yet)
1. Create `deploy/dev/tagging.js`
2. Issue record structure per `SITE_TAGGING_SRS.md` §6.1
3. localStorage keyed by `bim_ootb_issues_{building_name}`
4. Issue types: Defect (red), Non-Compliance (orange), Safety (magenta), Incomplete (yellow), Clearance (cyan)
5. Mobile user ID from localStorage (prompt once, persist)
6. Issues panel in toolbar — list all issues, tap to fly-to
7. Issue status cycle: Open → OK / Fail / Cancelled (same UX as clash review)

### Phase 2 — QR Decal on 3D Surface
1. Load `qr-lib.min.js`
2. On issue creation: generate QR encoding shareable URL
3. Create canvas texture from QR data
4. Place as `THREE.DecalGeometry` on element mesh at hit point + face normal
5. Decal size ~0.3m, depth 0.1m, with `polygonOffset` to avoid z-fighting
6. QR decals stored in separate `THREE.Group` for raycaster priority
7. Visual states: white (open), green+tick (OK), red+X (fail), grey+strikethrough (cancelled)

### Phase 3 — Shareable Link + Share Dialog
1. Generate URL same pattern as clash share: `?db=...&cam=...&tgt=...&snag=GUID&storey=...`
2. On issue creation: show share dialog — [Copy Link] [Copy QR Image] [WhatsApp] [Email]
3. Long-press QR in-viewer: [Share] [Edit] [OK] [Fail] [Cancel]
4. QR can be saved as PNG for printing (same code rendered to downloadable canvas)

### Phase 4 — Review Mode (Fly-Through All)
1. [Review All] button in issues panel
2. Auto-fly to each issue in sequence
3. User taps: OK / Fail / Skip / Remove
4. Auto-advances to next
5. Summary at end: "12 OK, 3 Fail, 2 Removed, 5 Skipped"
6. Export to Excel (HTML table) sorted by status, with cost weight from `rates.js`
7. Share export via WhatsApp/Email

## Files
- `deploy/dev/tagging.js` — NEW: issue queue, QR generation, decal placement, review mode
- `deploy/dev/qr-lib.min.js` — NEW: vendored QR generator
- `deploy/dev/index.html` — add `<script src="tagging.js">` and toolbar icon
- `deploy/dev/picking.js` — add QR raycaster group priority check (minimal change)

## Witnesses
- `§TAG_CREATE` — issue created, GUID, type, user_id
- `§TAG_QR_DECAL` — QR decal placed at (x,y,z) on element GUID
- `§TAG_SHARE` — shareable link generated
- `§TAG_REVIEW` — review mode: issue status changed
- `§TAG_EXPORT` — Excel export with N issues

## Test
1. Load any building → tap element → issue dialog opens → fill type → Tag It
2. QR appears on element surface — visible from multiple angles
3. Tap QR → info popup shows issue details
4. Long-press QR → action menu works (Share/Edit/OK/Fail/Cancel)
5. Share → copy link → open in new tab → viewer loads at same position
6. Review All → flies through issues → OK/Fail/Remove works
7. Export → Excel shows all issues with status and cost
8. Existing Walk + Clash + Snag flow still works unchanged
