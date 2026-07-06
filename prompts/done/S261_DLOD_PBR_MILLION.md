# ⚠ DO NOT REMOVE — Read the log after every run

## S261: DLOD + PBR Materials — Path to 1 Million Elements

### Context
S260d delivered the cinematic drone system (progressive storyboard, predetermined camera arcs,
opening establishing shot, signature scene concept). The viewer handles 63K elements (Hospital)
but materials look grey/washed out with shadows on. Scaling to 1M elements requires DLOD.

Two problems to solve in this session:
1. **Grey materials** — switch from MeshPhongMaterial to MeshStandardMaterial (PBR) + fix lighting
2. **DLOD re-enablement** — `dlod.js` exists (276 lines) but is disabled since S246. Must work
   with BatchedMesh + InstancedMesh + Time Machine + 1M elements

### PROBLEM 1: Grey Washed-Out Materials

#### Root Cause (proven in S260d analysis)
Three things compound into grey:

| Layer | Current | Problem |
|-------|---------|---------|
| Material | `MeshPhongMaterial(flatShading:true, shininess:30)` | Blinn-Phong doesn't respond to env maps properly. Everything looks like plastic. |
| Ambient | `AmbientLight(0x606080, 0.8)` | Grey-blue fills shadow areas with grey |
| Hemisphere | `sky=0x8888cc, ground=0x444422, intensity=0.5` | Desaturated sky + muddy ground = grey shadow fill |
| Near-white taming | `r *= 0.82` when RGB > 0.85 | Pulls white walls/ceilings to grey. Over-aggressive. |
| Tone mapping | ACESFilmic at 1.15 exposure | ACES compresses highlights, desaturates midtones |

#### Fix Spec

**A. Switch `_getMaterial` to MeshStandardMaterial (PBR)**
File: `deploy/dev/streaming.js` line ~267-273

```javascript
// BEFORE
const opts = { color: new THREE.Color(r, g, b), flatShading: true };
opts.shininess = SHININESS_MAP[ifcClass] || 30;
opts.reflectivity = REFLECTIVITY_MAP[ifcClass] || 0.1;
const mat = new THREE.MeshPhongMaterial(opts);

// AFTER — PBR with roughness/metalness per IFC class
const opts = { color: new THREE.Color(r, g, b), flatShading: false };
opts.roughness = ROUGHNESS_MAP[ifcClass] || 0.7;
opts.metalness = METALNESS_MAP[ifcClass] || 0.05;
if (A._envMap) opts.envMap = A._envMap;
opts.envMapIntensity = 0.3;
const mat = new THREE.MeshStandardMaterial(opts);
```

Replace SHININESS_MAP + REFLECTIVITY_MAP with:
```javascript
var ROUGHNESS_MAP = {
  IfcBeam: 0.4, IfcColumn: 0.4, IfcMember: 0.4, IfcPlate: 0.3,  // steel — smooth
  IfcSlab: 0.85, IfcFooting: 0.9, IfcPile: 0.9, IfcWall: 0.8,   // concrete — rough
  IfcWallStandardCase: 0.75, IfcCurtainWall: 0.1,                  // glass — very smooth
  IfcWindow: 0.05, IfcDoor: 0.6,                                   // glass/wood
  IfcRailing: 0.35, IfcStair: 0.7, IfcRoof: 0.75,                  // metal/tile
  IfcDuct: 0.45, IfcPipe: 0.35, IfcCableCarrier: 0.5,              // MEP metal
  IfcFurniture: 0.6, IfcFlowTerminal: 0.4, IfcLightFixture: 0.25   // fixtures
};
var METALNESS_MAP = {
  IfcBeam: 0.6, IfcColumn: 0.5, IfcMember: 0.6, IfcPlate: 0.7,
  IfcCurtainWall: 0.1, IfcWindow: 0.0,
  IfcDuct: 0.4, IfcPipe: 0.5, IfcRailing: 0.5,
  IfcLightFixture: 0.3, IfcFlowTerminal: 0.3
};
```

**B. Fix lighting** in `deploy/dev/scene.js` lines 72-83:
```javascript
const ambient = new THREE.AmbientLight(0xffffff, 0.35);  // neutral white, lower
const hemi = new THREE.HemisphereLight(0xb0c4de, 0x8b7355, 0.6);  // steel blue sky, warm earth ground
```

**C. Gentler near-white taming** in `deploy/dev/streaming.js` line 266:
```javascript
if (r > 0.85 && g > 0.85 && b > 0.85) { r *= 0.92; g *= 0.92; b *= 0.92; }
```

**D. Class-based color fallback** for buildings with no IFC colors (LTU etc):
When `rgba` is null/default (0.7,0.7,0.7), assign by IFC class:
```javascript
var CLASS_COLOR_FALLBACK = {
  IfcSlab: '0.78,0.76,0.72',      // warm concrete
  IfcWall: '0.88,0.85,0.80',      // off-white plaster
  IfcColumn: '0.65,0.67,0.70',    // steel grey-blue
  IfcBeam: '0.60,0.62,0.65',      // darker steel
  IfcDoor: '0.55,0.40,0.25',      // wood brown
  IfcWindow: '0.75,0.85,0.90',    // glass blue tint
  IfcPipe: '0.50,0.55,0.60',      // galvanized
  IfcDuct: '0.60,0.62,0.58',      // sheet metal
  IfcFurniture: '0.70,0.55,0.40', // wood/fabric
  IfcRoof: '0.50,0.45,0.40',      // dark tile
};
```

**E. Performance gate:** After switching, run Hospital (63K) and measure FPS.
If < 30fps on desktop: add `flatShading: true` back (saves ~15% GPU).
Log: `§PBR_FPS frames=N avg=Xfps`.

### PROBLEM 2: DLOD for 1M Elements

#### Current State
- `deploy/dev/dlod.js` — 276 lines, frustum + storey culling
- Disabled in `streaming.js:304` (commented out) since S246
- Reason: "storey culling causes visible pop-in artifacts"
- BatchedMesh (S260) changed the mesh architecture — dlod.js predates this
- BVH (three-mesh-bvh v0.7.8) installed but only used for raycasting, not culling

#### Architecture for 1M Elements — setGeometryAt Swapping

**The key insight:** Three.js r160 `BatchedMesh.setGeometryAt(slotId, geometryId)` allows
swapping geometry per slot without recreating the mesh. Each slot can flip between a bbox
(8-vert box) and real geometry per frame.

**Two LOD tiers (no LOD1 — skip intermediate complexity):**
| Tier | Distance | Geometry | How |
|------|----------|----------|-----|
| LOD0 (bbox) | ALL elements | 8-vert box from DB `bbox_x/y/z` | Loads instantly — all 1M visible as boxes |
| LOD2 (full) | < 30m from camera | Real geometry blob | `setGeometryAt(slotId, realGeoId)` swap |

**Flow:**
1. **Initial load:** ALL 1M elements load as bbox (8 vertices each). Instant. No blob fetch.
   Position/scale from `element_transforms.center_x/y/z + bbox_x/y/z`.
2. **Camera moves:** Evaluate which slots are within 30m of camera.
3. **Promote (bbox→full):** Fetch geometry blob, add to BatchedMesh via `addGeometry()`,
   then `setGeometryAt(slotId, realGeoId)`. Slot now shows full detail.
4. **Demote (full→bbox):** When camera moves away, `setGeometryAt(slotId, bboxGeoId)`.
   The real geometry stays in the BatchedMesh geometry pool (no re-fetch if camera returns).
5. **Budget:** Cap at ~50K full-geometry slots. Beyond that, oldest-promoted demotes first (LRU).

**Per BatchedMesh:**
- One shared `bboxGeoId` = the box geometry added once via `addGeometry(boxGeo)`
- Per slot: `realGeoId` stored in `_batchMeta[meshId][slotIdx].realGeoId` (null until promoted)
- Swap: `bm.setGeometryAt(slotId, promoted ? realGeoId : bboxGeoId)`

**Why this works for 1M:**
- 1M × 8 verts = 8M vertices for bbox pass. BatchedMesh handles this (~100 draw calls).
- Only ~5K-10K elements get full geometry at any time (near camera).
- No full-geometry load at startup — progressive, on demand.
- `setGeometryAt` is O(1) per slot — no mesh recreation.

**Key design decisions:**

1. **Frustum culling:** Three.js per-object (`frustumCulled=true`).
   For BatchedMesh: per-slot via `setVisibleAt(slotId, false)` for off-frustum slots.

2. **Storey culling:** Show only N storeys around orbit target. Already in dlod.js.
   Fix: smooth transition (fade opacity) instead of hard pop-in.

3. **Time Machine cooperation:** TM already hides future elements via `setVisibleAt`.
   DLOD respects TM visibility — only promote visible slots.

#### Implementation Steps

1. **Re-enable dlod.js** — uncomment line 304 in streaming.js
2. **Update for BatchedMesh** — dlod.js currently traverses individual meshes.
   Need to handle `_batchMeta` (per-slot visibility) and `_instanceMeta`.
3. **Keep bboxes for far elements** — modify `_clearBboxPlaceholders` to only clear
   elements within LOD0 range. Far elements keep their bbox representation.
4. **Camera-distance streaming** — already exists (`sorted by camera distance` in
   streaming.js). Extend: when camera moves significantly, re-evaluate which elements
   need full geometry vs bbox.
5. **Progressive promotion** — as camera approaches a bbox cluster, load its full
   geometry in the background (rAF chunks). Bbox → full mesh fade transition.
6. **Memory budget** — cap full-geometry elements at ~100K. Beyond that, everything
   stays bbox until camera approaches. Track: `§DLOD_BUDGET full=N bbox=N total=N`.

#### Test Targets
- Hospital (63K) — baseline, must maintain current FPS
- Terminal (48K) — federated model with MEP
- Synthetic 500K — duplicate Hospital elements 8× with offset positions
- Synthetic 1M — duplicate 16× (test OOM, draw calls, streaming time)

#### Whitebox
```
§DLOD_ENABLE elements=N lod0=N lod2=N
§DLOD_TICK ms=N promoted=N demoted=N frustumCulled=N
§DLOD_BUDGET full=N bbox=N total=N gpuMB=N
§PBR_FPS frames=N avg=Nfps
```

### Files to Modify
- `deploy/dev/streaming.js` — _getMaterial PBR switch, bbox retention, LOD streaming
- `deploy/dev/scene.js` — lighting fix (ambient, hemisphere)
- `deploy/dev/dlod.js` — update for BatchedMesh/InstancedMesh, LOD tier logic
- `deploy/dev/time_machine.js` — DLOD cooperation flag
- `deploy/dev/index.html` — dlod.js script tag (may already exist)

### Order of Work
1. Fix grey materials (PBR + lighting) — verify on Hospital with shadows on
2. Re-enable dlod.js with BatchedMesh support
3. Test on Hospital — FPS must stay ≥ 30
4. Test bbox retention for far elements
5. Synthetic 500K test
6. If all pass → deploy to ootb-dev

### Deploy Checklist
- Bump `?v=` for streaming.js, scene.js, dlod.js, time_machine.js
- Bump sw.js CACHE_VERSION
- Clear IDB cache (material cache keys changed)
- Test with shadows on AND off
