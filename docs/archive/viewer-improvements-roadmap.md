# BIM Viewer Improvements Roadmap

**Version:** 1.0
**Date:** 2026-02-03
**Status:** Working Draft
**File:** `viewer/index.html`

---

## 1. Current State Assessment

### 1.1 What Works

| Feature | Status | Notes |
|---------|--------|-------|
| Model loading (drag-drop/browse) | ✓ Working | .glb/.gltf support |
| Basic 3D view | ✓ Working | Google model-viewer component |
| Zoom buttons (+/-/Reset) | ✓ Working | Added after trackpad issues |
| Auto-rotate | ✓ Working | Can be toggled |
| Building title display | ✓ Working | Extracted from glTF root node |
| DSL structure tree | ✓ Working | Shows storey/room hierarchy |
| Element count stats | ✓ Working | Footer display |

### 1.2 What Doesn't Work / Needs Improvement

| Feature | Issue | Priority |
|---------|-------|----------|
| **Discipline toggles** | Buttons exist but visibility not changing | HIGH |
| **Storey filter** | Same issue — buttons don't filter | HIGH |
| **Trackpad zoom** | Two-finger zoom inconsistent | MEDIUM |
| **Element selection** | Click on element doesn't show details | MEDIUM |
| **Witness display** | Section exists but not populated | LOW |
| **AR mode** | Button exists but not functional | LOW |

---

## 2. Priority Fixes

### 2.1 Discipline Toggle Fix (HIGH)

**Problem:** `model.traverse()` finds discipline group nodes but setting `.visible = false` doesn't hide children.

**Root Cause:** Three.js visibility inheritance vs model-viewer rendering pipeline.

**Solution Options:**

```javascript
// Option A: Set visible recursively on all descendants
function setGroupVisibility(node, visible) {
    node.visible = visible;
    node.children.forEach(child => setGroupVisibility(child, visible));
}

// Option B: Use material opacity instead
function setGroupVisibility(node, visible) {
    node.traverse(child => {
        if (child.material) {
            child.material.transparent = true;
            child.material.opacity = visible ? 1.0 : 0.0;
            child.material.needsUpdate = true;
        }
    });
}

// Option C: Move to display:none via CSS layers (requires glTF restructure)
```

**Recommended:** Option A with explicit recursion and `viewer.requestUpdate()`.

### 2.2 Storey Filter Fix (HIGH)

**Problem:** Storey info stored in `node.userData.storey` but filtering logic conflicts with discipline filtering.

**Root Cause:** Both filters apply AND logic, but visibility set independently.

**Solution:**

```javascript
async function updateVisibility() {
    const activeLayers = [...document.querySelectorAll('.layer-toggle button.active')]
        .map(b => b.dataset.layer);
    const activeStorey = document.querySelector('.storey-select button.active').dataset.storey;

    const model = viewer.model;
    if (!model) return;

    model.traverse((node) => {
        // Skip non-mesh nodes
        if (!node.isMesh) return;

        // Get discipline from parent group or userData
        const discipline = getDiscipline(node);
        const storey = node.userData?.storey || getStoreyFromParent(node);

        // Both conditions must pass
        const discVisible = activeLayers.includes(discipline);
        const storeyVisible = (activeStorey === 'all') || (storey === activeStorey);

        node.visible = discVisible && storeyVisible;
    });

    viewer.requestUpdate();
}

function getDiscipline(node) {
    // Walk up to find discipline group
    let current = node;
    while (current) {
        if (['ARCH', 'STRUCT', 'ELEC', 'PLUMB'].includes(current.name)) {
            return current.name;
        }
        current = current.parent;
    }
    return 'ARCH'; // default
}
```

### 2.3 Trackpad/Mouse Navigation (MEDIUM)

**Problem:** Two-finger zoom on trackpad doesn't work reliably.

**Current Workaround:** Added explicit +/- buttons.

**Better Solution:** Configure model-viewer orbit controls properly:

```html
<model-viewer
    camera-controls
    touch-action="pan-y"
    orbit-sensitivity="1"
    zoom-sensitivity="0.5"
    interaction-prompt="none"
    ...
>
```

**Additional Controls to Add:**

| Control | Action | Implementation |
|---------|--------|----------------|
| Scroll wheel | Zoom | Already works via camera-controls |
| Right-click drag | Pan | `touch-action="pan-y"` |
| Middle-click drag | Rotate | Already works |
| Double-click | Focus on element | Custom event handler |
| Shift + scroll | Fine zoom | Custom modifier key handling |

---

## 3. New Features

### 3.1 Element Selection & Inspection (MEDIUM)

**Goal:** Click on element → show details in sidebar.

```javascript
viewer.addEventListener('click', async (event) => {
    // Ray cast to find clicked element
    const rect = viewer.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width;
    const y = (event.clientY - rect.top) / rect.height;

    // model-viewer provides intersection method
    const hit = viewer.positionAndNormalFromPoint(x, y);
    if (!hit) return;

    // Find clicked mesh
    const model = viewer.model;
    const intersected = findIntersectedMesh(model, hit.position);

    if (intersected && intersected.userData) {
        showElementInfo(intersected.userData);
        highlightElement(intersected);
    }
});

function showElementInfo(data) {
    document.getElementById('element-card').innerHTML = `
        <div class="element-name">${data.name || 'Unknown'}</div>
        <div class="element-type">${data.ifc_class || ''}</div>
        <div class="element-detail">GUID: ${data.guid}</div>
        <div class="element-detail">Storey: ${data.storey}</div>
        <div class="element-detail">Discipline: ${data.discipline}</div>
    `;
}

function highlightElement(mesh) {
    // Store original material
    mesh._originalMaterial = mesh.material.clone();

    // Apply highlight
    mesh.material.emissive.setHex(0xe94560);
    mesh.material.emissiveIntensity = 0.3;

    // Remove highlight on next click or timeout
    setTimeout(() => {
        mesh.material.copy(mesh._originalMaterial);
    }, 3000);
}
```

### 3.2 Discipline Filtering UI Improvements (MEDIUM)

**Current:** Simple toggle buttons
**Improved:** Visual feedback with color coding

```html
<div class="layer-toggle">
    <button class="active" data-layer="ARCH" style="--layer-color: #f5f5dc;">
        <span class="color-dot"></span> Architecture
    </button>
    <button class="active" data-layer="STRUCT" style="--layer-color: #9e9e9e;">
        <span class="color-dot"></span> Structure
    </button>
    <button class="active" data-layer="ELEC" style="--layer-color: #ffc107;">
        <span class="color-dot"></span> Electrical
    </button>
    <button class="active" data-layer="PLUMB" style="--layer-color: #2196f3;">
        <span class="color-dot"></span> Plumbing
    </button>
</div>

<style>
.layer-toggle button {
    display: flex;
    align-items: center;
    gap: 6px;
}
.color-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--layer-color);
}
.layer-toggle button:not(.active) {
    opacity: 0.4;
}
.layer-toggle button:not(.active) .color-dot {
    background: #555;
}
</style>
```

### 3.3 Isolation Mode (HIGH)

**Goal:** "Solo" a discipline or storey to view in isolation.

```javascript
// Double-click to isolate
document.querySelectorAll('.layer-toggle button').forEach(btn => {
    btn.addEventListener('dblclick', () => {
        const layer = btn.dataset.layer;
        // Deactivate all, activate only this one
        document.querySelectorAll('.layer-toggle button').forEach(b => {
            b.classList.remove('active');
        });
        btn.classList.add('active');
        updateVisibility();
    });
});

// Add "Show All" button
<button onclick="showAllLayers()">Show All</button>

function showAllLayers() {
    document.querySelectorAll('.layer-toggle button').forEach(b => {
        b.classList.add('active');
    });
    updateVisibility();
}
```

### 3.4 Measurement Tool (LOW)

**Goal:** Click two points → show distance.

```javascript
let measurePoints = [];

function startMeasure() {
    measurePoints = [];
    document.body.style.cursor = 'crosshair';
    viewer.addEventListener('click', measureClick);
}

function measureClick(event) {
    const hit = getHitPoint(event);
    if (!hit) return;

    measurePoints.push(hit);

    if (measurePoints.length === 2) {
        const distance = measurePoints[0].distanceTo(measurePoints[1]);
        showMeasurement(distance);
        document.body.style.cursor = 'default';
        viewer.removeEventListener('click', measureClick);
    }
}
```

### 3.5 Section Cut / Clipping Plane (LOW)

**Goal:** Slice model to see interior.

```javascript
// Uses Three.js clipping planes
function addClippingPlane(axis, position) {
    const plane = new THREE.Plane(
        new THREE.Vector3(axis === 'x' ? 1 : 0, axis === 'y' ? 1 : 0, axis === 'z' ? 1 : 0),
        -position
    );

    viewer.model.traverse(node => {
        if (node.material) {
            node.material.clippingPlanes = [plane];
            node.material.clipShadows = true;
        }
    });

    // Need to enable clipping in renderer
    // Note: model-viewer may not expose renderer directly
}
```

---

## 4. Data Requirements

### 4.1 glTF Export Enhancements

For viewer features to work, `export_to_gltf.py` must provide:

| Data | Current | Needed | Location |
|------|---------|--------|----------|
| Discipline | ✓ In node hierarchy | - | Node name |
| Storey | ✓ In extras | - | `node.extras.storey` |
| GUID | ✓ In extras | - | `node.extras.guid` |
| IFC Class | ✓ In extras | - | `node.extras.ifc_class` |
| Room name | ✗ Missing | Add | `node.extras.room` |
| Wall type | ✗ Missing | Add | `node.extras.wall_type` |
| Fire rating | ✗ Missing | Add | `node.extras.fire_rating` |

### 4.2 Witness Data Integration

Load witness JSON alongside glTF:

```javascript
async function loadWitnessData(baseName) {
    try {
        const response = await fetch(`output/${baseName}_witness.json`);
        const witness = await response.json();

        document.getElementById('witness-count').textContent =
            witness.claims.filter(c => c.status === 'PROVEN').length;

        // Populate witness badges
        const container = document.getElementById('witness-badges');
        container.innerHTML = witness.claims.map(c => `
            <span class="witness-badge ${c.status.toLowerCase()}">
                ${c.claim_name}: ${c.status}
            </span>
        `).join('');

        document.getElementById('witness-section').style.display = 'block';
    } catch (e) {
        console.log('No witness file found');
    }
}
```

---

## 5. Keyboard Shortcuts

| Key | Action | Implementation |
|-----|--------|----------------|
| `1` | Toggle Architecture | `toggleLayer('ARCH')` |
| `2` | Toggle Structure | `toggleLayer('STRUCT')` |
| `3` | Toggle Electrical | `toggleLayer('ELEC')` |
| `4` | Toggle Plumbing | `toggleLayer('PLUMB')` |
| `A` | Show All | `showAllLayers()` |
| `H` | Hide All | `hideAllLayers()` |
| `R` | Reset View | `resetView()` |
| `F` | Fit to View | `fitToView()` |
| `M` | Toggle Measure | `startMeasure()` |
| `Esc` | Cancel operation | `cancelCurrentOp()` |

```javascript
document.addEventListener('keydown', (e) => {
    if (e.target.tagName === 'INPUT') return; // Don't capture when typing

    switch(e.key) {
        case '1': toggleLayer('ARCH'); break;
        case '2': toggleLayer('STRUCT'); break;
        case '3': toggleLayer('ELEC'); break;
        case '4': toggleLayer('PLUMB'); break;
        case 'a': case 'A': showAllLayers(); break;
        case 'r': case 'R': resetView(); break;
        case 'Escape': cancelCurrentOp(); break;
    }
});
```

---

## 6. Implementation Priority

### Phase 1: Core Fixes (Immediate)
1. ✗ Fix discipline toggle visibility
2. ✗ Fix storey filter visibility
3. ✗ Add recursive visibility helper

### Phase 2: Usability (Next)
1. ✗ Element click selection
2. ✗ Element info display
3. ✗ Keyboard shortcuts
4. ✗ Isolation mode (double-click)

### Phase 3: Advanced (Future)
1. ✗ Measurement tool
2. ✗ Section cut / clipping
3. ✗ Witness data integration
4. ✗ AR mode functional

---

## 7. Testing Checklist

| Test | Steps | Expected |
|------|-------|----------|
| Load model | Drop tb_lktn.glb | Model displays, title shows |
| Discipline toggle | Click "Structure" | Columns/beams hide |
| Storey filter | Click "Ground" | Only ground floor visible |
| Zoom | Click +/- buttons | Zoom in/out smoothly |
| Trackpad zoom | Two-finger pinch | Zoom works |
| Element select | Click on wall | Info panel shows details |
| Reset view | Click Reset | Returns to default orbit |

---

## 8. Known Limitations

| Limitation | Reason | Workaround |
|------------|--------|------------|
| No IFC export | model-viewer is view-only | Use export_to_ifc.py separately |
| Large model performance | WebGL memory limits | Consider LOD or decimation |
| No edit capability | Viewer, not editor | Use DSL for changes |
| Limited material control | glTF material fixed | Re-export with different materials |

---

*Document created 2026-02-03 for BIM Intent Compiler viewer improvements.*
