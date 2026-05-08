# Walk/Site Tagging System — QR Decals, Issue Heatmap, Track & Trace

> **Foundation:** [MOBILE_DEPLOY.md](MOBILE_DEPLOY.md) · [CLASH_DETECTION.md](CLASH_DETECTION.md) · [BIM_Designer_Browser.md](BIM_Designer_Browser.md)

<div class="bim-banner" markdown>
<b>Snap. Tag. Share. Review. Close.</b> QR codes that live on 3D surfaces — navigable, shareable, printable. A building-wide issue heatmap. Fly-through review with one-tap OK/Fail/Remove. No server, no app install, no training.
</div>

**Version:** 0.1 (2026-05-05)
**Status:** SPEC — extends existing Walk/Snag and Clash Share infrastructure
**Depends on:** `walk.js`, `measure.js` (clash share pattern), `rates.js`, Three.js DecalGeometry, QR generator lib (~4KB)
**Prior art:** None found. Existing BIM+QR solutions require physical codes on real sites + AR hardware. No tool places navigable QR decals inside a 3D model viewer.

---

## 1. The Problem

Site inspection today:
1. Walk the floor, spot a defect
2. Take a photo on your phone
3. Open a spreadsheet, type a description, guess the location
4. Email the spreadsheet to the coordinator
5. Coordinator can't find it in the model — asks "which floor? which pipe?"
6. Issue gets lost between tools

**This system:** Tap the element → QR appears on its surface → share QR (or tap it later) → issue tracked, located, reviewable, exportable. Zero ambiguity.

---

## 2. Core Concepts

### 2.1 QR Decal

A QR code rendered as a **texture decal** (`THREE.DecalGeometry`) on the surface of a 3D element. The QR encodes a shareable deep-link URL:

```
?db=...&cam=x,y,z&tgt=x,y,z&snag=GUID&storey=Floor3&user=MobileID
```

Visual states:
| State | Appearance | Meaning |
|-------|-----------|---------|
| Open | White QR on black | New issue — unreviewed |
| Fail | Red QR with ✗ overlay | Confirmed defect |
| OK | Green QR with ✓ overlay | Passed inspection |
| Cancelled | Grey QR with ✗ strikethrough | Removed from active list |

### 2.2 Issue Types (colour-coded in heatmap)

| Type | Colour | Default? | Description |
|------|--------|----------|-------------|
| **Defect** | Red | Yes (default) | Workmanship error, damage, incorrect installation |
| **Non-Compliance** | Orange | Standard | Does not match design/spec/code requirement |
| **Safety** | Magenta | Standard | Hazard requiring immediate attention |
| **Incomplete** | Yellow | Standard | Work not finished, element missing or partial |
| **Clearance** | Cyan | Standard | Insufficient access space, maintenance gap |
| **Aesthetic** | Purple | Optional | Finish, alignment, visual quality |
| **Service** | Blue | Optional | MEP operational issue (leak, noise, vibration) |
| **Custom** | User-defined | Optional | Project-specific category |

### 2.3 Mobile User ID

Each device registers a `user_id` (device name or entered alias) stored in `localStorage`. Every issue carries:
- `user_id` — who created it
- `timestamp` — when
- `guid` — which element
- `storey` — which floor
- `type` — issue category
- `notes` — freeform text (optional)
- `photo` — camera snap (base64 in localStorage, optional)

---

## 3. Workflow

### 3.1 Create Issue (Walk/Site Mode)

```
User navigates building (Walk or free camera)
    ↓
Spots issue → taps element (or long-press in Walk mode)
    ↓
Issue dialog opens:
  - Type: [Defect ▾]  (dropdown, colour-coded)
  - Notes: [___________]  (optional text)
  - 📷 Snap (optional camera capture)
  - [Tag It]
    ↓
QR decal placed on element surface (DecalGeometry)
    ↓
Shareable link generated (same pattern as clash share)
    ↓
Issue added to queue (localStorage)
    ↓
Share dialog: [Copy Link] [Copy QR] [WhatsApp] [Email]
```

### 3.2 Interact with Existing QR (Navigate Mode)

```
User navigates building → sees QR decal on a surface
    ↓
Tap QR → info popup (type, user, date, notes)
    ↓
Long-press QR → action menu:
  - [Share] — copy link or QR image
  - [Edit] — change type, add notes
  - [OK ✓] — mark passed
  - [Fail ✗] — mark confirmed defect
  - [Cancel] — remove from active list (grey + strikethrough)
```

### 3.3 Review Mode (Fly-Through All)

```
Issues panel → [Review All] button
    ↓
Camera flies to first QR/issue
    ↓
User taps: [OK ✓] | [Fail ✗] | [Skip →] | [Remove 🗑]
    ↓
Auto-flies to next issue
    ↓
... until all reviewed
    ↓
Summary: "12 OK, 3 Fail, 2 Removed, 5 Skipped"
    ↓
[Export] → Excel with audit columns
```

### 3.4 Share via QR (instead of long URL)

Instead of copying a long URL, user can:
- **Show QR** — display full-screen QR on their phone → other person scans it
- **Save QR image** — download as PNG for printing or embedding in reports
- **Print for site** — same QR code printed and physically stuck on the real wall/pipe

The physical QR and the digital QR are **identical** — scan either one → same deep-link → viewer opens at that exact element.

---

## 4. Issue Heatmap — Building-Wide Overlay

### 4.1 Trigger

Issues panel shows a **red sphere** (same pattern as clash matrix sphere). Click it → building-wide heatmap overlay activates.

### 4.2 Heatmap Rendering

Colour each element/storey by issue density and severity:

| Density | Colour |
|---------|--------|
| 0 issues | No overlay (original material) |
| 1–2 issues | Light yellow |
| 3–5 issues | Orange |
| 6+ issues | Deep red |

### 4.3 Breakdown (like clash matrix)

Clicking the heatmap sphere opens a **matrix panel** breaking down by:

| Axis | Values |
|------|--------|
| Rows | Storeys |
| Columns | Issue types (Defect, Non-Compliance, Safety, ...) |
| Cell value | Count |
| Cell colour | Severity gradient |

Click any cell → filters the issue list to that storey + type → fly-to first.

### 4.4 Cost-Weighted Sort

Each issue inherits a **cost weight** from the tagged element's IFC class:

```javascript
weight = RATES[element.ifc_class].rate || RATES_DEFAULT.rate;
```

Sort options in the issues panel:
- **Cost** (highest rate first) — expensive elements get attention first
- **Type** (Safety → Non-Compliance → Defect → ... ) — severity hierarchy
- **Storey** (top-down or bottom-up)
- **Date** (newest or oldest first)
- **User** (group by who tagged it)

Elements with no rate entry in `rates.js` sort to the bottom (RATES_DEFAULT = 500).

---

## 5. Cost-Weighted Clash Sorting (Extension to measure.js)

For the existing clash detection system, add cost awareness:

```javascript
// For each clash pair, compute cost weight
var weightA = (RATES[elA.ifc_class] || RATES_DEFAULT).rate;
var weightB = (RATES[elB.ifc_class] || RATES_DEFAULT).rate;
var pairWeight = weightA + weightB;
```

Sort clash list by `pairWeight` descending. A clash between IfcColumn ($1,250) + IfcEnergyConversionDevice ($8,500) = weight 9,750 → top of list. Two IfcPipeSegments ($48 + $48) = weight 96 → bottom.

Add sort toggle to clash panel: **[Cost ↓] [Severity] [Storey]**

---

## 6. Data Model

### 6.1 Issue Record (localStorage)

```javascript
{
  id: 'issue_' + Date.now(),
  guid: 'IFC_GUID_of_element',
  ifc_class: 'IfcPipeSegment',
  storey: 'Level 2',
  type: 'defect',           // defect|non_compliance|safety|incomplete|clearance|aesthetic|service|custom
  status: 'open',           // open|ok|fail|cancelled
  notes: 'Pipe joint leaking at flange',
  photo: null,              // base64 or null
  user_id: 'Ahmad_iPhone',
  timestamp: '2026-05-05T09:23:00Z',
  cost_weight: 48.5,        // from RATES lookup
  cam: { x: -12.5, y: 3.2, z: 8.1 },
  tgt: { x: -14.0, y: 3.0, z: 7.5 }
}
```

### 6.2 Storage

- **Active issues:** `localStorage` key `bim_ootb_issues_{building_name}`
- **Export:** Excel (HTML table) with columns: ID, GUID, Class, Storey, Type, Status, Notes, User, Date, Cost
- **QR payload:** Shareable URL (same as clash share pattern)

---

## 7. Excel Export + Share

### 7.1 Export Format

| ID | Element | Class | Storey | Type | Status | Notes | User | Date | Cost (RM) |
|----|---------|-------|--------|------|--------|-------|------|------|-----------|
| 001 | 0Gj... | IfcPipeSegment | Level 2 | Defect | Fail | Joint leak | Ahmad | 2026-05-05 | 48.50 |
| 002 | 1Hk... | IfcColumn | Level 3 | Safety | OK | Crack checked | Siti | 2026-05-05 | 1,250.00 |

Sorted by: Status (Fail → Open → OK → Cancelled), then Cost descending.

### 7.2 Share Flow

Export → Excel HTML opens in new tab → user taps:
- **WhatsApp** (share link or file)
- **Email** (attach)
- **CSV download** (for back-office import to iDempiere)

### 7.3 Audit Trail

Cancelled issues remain in export (greyed out) with cancellation timestamp and user. Nothing is deleted — full audit trail for compliance.

---

## 8. QR Technical Implementation

### 8.1 QR Generation

Library: `qrcode-generator` (Kazuhiko Arase, MIT, ~4KB minified)

```javascript
var qr = qrcode(0, 'M');  // Auto version, medium error correction
qr.addData(shareableURL);
qr.make();
var dataURL = qr.createDataURL(4);  // 4px per module
```

### 8.2 Decal Placement (Three.js)

```javascript
// Place QR texture on element surface at camera hit point
var decalGeom = new THREE.DecalGeometry(
  targetMesh,         // the element mesh
  hitPoint,           // raycaster intersection point
  hitNormal,          // surface normal at hit
  new THREE.Vector3(0.3, 0.3, 0.1)  // decal size (metres)
);
var decalMat = new THREE.MeshBasicMaterial({
  map: qrTexture,
  transparent: true,
  depthWrite: false,
  polygonOffset: true,
  polygonOffsetFactor: -4
});
var decalMesh = new THREE.Mesh(decalGeom, decalMat);
scene.add(decalMesh);
```

### 8.3 QR Interaction (Raycaster)

QR decals are added to a separate raycaster group. On tap/click:
- Check QR group first (higher priority than element picking)
- If QR hit → show issue popup
- If long-press → show action menu

### 8.4 Printable QR

Same QR data URL rendered to a printable template:
- QR code centred
- Element name + storey + date below
- Building name + project header
- Print at standard label size (50mm × 50mm)

---

## 9. Relationship to Existing Systems

| Existing Feature | How Site Tagging Extends It |
|---|---|
| Clash share (§3.7 deep-link) | Same URL pattern, now for issues too |
| Walk mode (GPS + compass) | Issue tagging during walk — context-aware |
| Issue log (camera snap + auto-tag) | Now persists as QR decal + structured record |
| Clash review (Reviewed/Resolved/Accepted) | Same OK/Fail/Cancel pattern for issues |
| Excel export (clash report) | Same export pattern for issue audit |
| Rates table (`rates.js`) | Cost-weighted sort for both clashes and issues |
| iDempiere integration | CSV export → ERP import for workflow/assignment |

---

## 10. Files (Planned)

| File | Role |
|------|------|
| `deploy/dev/tagging.js` | Issue creation, QR generation, decal placement, issue queue |
| `deploy/dev/heatmap.js` | Building-wide issue overlay, matrix panel |
| `deploy/dev/qr-lib.min.js` | QR code generator (vendored, MIT, ~4KB) |
| `docs/SITE_TAGGING_SRS.md` | This spec |

---

## 11. No Prior Art

Existing BIM + QR approaches (searched May 2026):

| Approach | Limitation |
|---|---|
| Physical QR on real walls (QRTRAC, BIM Holoview) | Requires printing, sticking, AR hardware to scan |
| AR marker-based overlay (MDPI 2023 paper) | Requires camera + VSLAM + native app |
| BIM defect tracking (ScienceDirect 2024) | Server-based, complex setup, no in-model visualization |

**None place QR codes as navigable decals inside a browser-based 3D viewer.** This system makes the QR both the visual marker and the shareable payload — one artifact serving as: 3D indicator, shareable link, printable label, issue anchor, and audit record.

---

## 12. Competitive Position vs Navisworks

| Navisworks Limitation | This System |
|---|---|
| Review-only — can't tag issues in the model | Tag directly on element surface |
| Export HTML report, lose 3D context | QR links back to exact 3D view |
| No mobile/offline | PWA, same URL, works offline |
| No cost priority | Cost-weighted sort from RATES |
| Manual spreadsheet tracking | Structured issue queue, auto-export |
| No visual markers in model | QR decals visible during navigation |
| $3,570/year | Free |
