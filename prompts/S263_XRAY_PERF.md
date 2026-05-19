# ⚠ DO NOT REMOVE — Scope: S263 X-Ray Performance for Large Buildings
# Read the log after every run. No inventions.

# S263: X-Ray Toggle Performance Optimization

## Problem

X-ray toggle (Alt+Z) on LTU (122K elements, ~15K draw calls) takes 2-5 seconds to respond. The user sees a frozen frame while materials update.

Current implementation (`deploy/dev/tools.js:80-110`):
```js
A.collectMeshes(o => o.isMesh).forEach(obj => {
    const mat = obj.material;
    mat.transparent = true;
    mat.opacity = 0.3;
    mat.side = THREE.DoubleSide;
    mat.needsUpdate = true;  // <-- triggers shader recompilation per material
});
```

**Cost analysis:**
- `collectMeshes` traverses entire scene graph
- Each `mat.needsUpdate = true` forces WebGL shader recompilation
- 15K meshes × shader recompile = multi-second freeze
- Most meshes share materials (via `_matCache` in streaming.js) — but each gets `needsUpdate`

## Root Cause

Materials are cached by `rgbaStr + ifcClass` in `_matCache`. Many meshes share the same material instance. But `toggleXray` traverses ALL meshes and sets `needsUpdate` on each — redundantly recompiling the same shared material hundreds of times.

## Fix: Operate on Materials, Not Meshes

### Approach 1: Iterate `_matCache` Instead of Scene

`_matCache` has ~200-400 unique materials vs 15K meshes. Toggle transparency on the cache:

```js
A.toggleXray = function() {
    A.xrayOn = !A.xrayOn;
    for (var key in A._matCache) {
        var mat = A._matCache[key];
        if (A.xrayOn) {
            if (mat.userData.origOpacity === undefined) mat.userData.origOpacity = mat.opacity;
            if (mat.userData.origTransparent === undefined) mat.userData.origTransparent = mat.transparent;
            if (mat.userData.origSide === undefined) mat.userData.origSide = mat.side;
            mat.transparent = true;
            mat.opacity = 0.3;
            mat.side = THREE.DoubleSide;
        } else {
            if (mat.userData.origOpacity !== undefined) {
                mat.opacity = mat.userData.origOpacity;
                mat.transparent = mat.userData.origTransparent;
                mat.side = mat.userData.origSide;
                delete mat.userData.origOpacity;
                delete mat.userData.origTransparent;
                delete mat.userData.origSide;
            }
        }
        mat.needsUpdate = true;
    }
    if (A.markDirty) A.markDirty();
};
```

**Expected speedup:** 15K → ~300 material updates. 50x fewer `needsUpdate` calls.

### Approach 2: DLOD Cooperation (LTU only)

For 100K+ buildings with DLOD active, x-ray only needs to update **visible** meshes. DLOD already tracks which elements are frustum-visible (`vis=86K` out of 122K in the logs). Off-screen meshes don't need material updates until they become visible.

- On x-ray toggle: update materials in `_matCache` (covers all future renders)
- Skip `needsUpdate` on meshes that are `obj.visible = false`
- When DLOD makes a mesh visible later, the shared material is already in x-ray state

### Approach 3: Uniform-Based Opacity (Future)

Instead of changing material properties (which triggers shader recompile), use a global uniform:
```js
// In _getMaterial: add a shared uniform
var xrayUniform = { value: 0.0 };
mat.onBeforeCompile = (shader) => {
    shader.uniforms.uXray = xrayUniform;
    shader.fragmentShader = shader.fragmentShader.replace(
        'gl_FragColor = vec4( outgoingLight, diffuseColor.a );',
        'gl_FragColor = vec4( outgoingLight, mix(diffuseColor.a, 0.3, uXray.value) );'
    );
};
// Toggle: just flip the uniform, zero recompiles
xrayUniform.value = A.xrayOn ? 1.0 : 0.0;
```

This is the ideal solution — zero material updates, zero shader recompiles, instant toggle. But `onBeforeCompile` with r160 + BatchedMesh needs testing.

## Verification

**§-tagged logs:**
- `§XRAY_TOGGLE ms=N mats=N meshes=N` — time and count for toggle
- Compare before/after: expect ms to drop from 2000-5000 to <100

**Test buildings:** LTU (122K), Terminal (48K), Clinic (16K)

## Files to Modify

| File | Change |
|------|--------|
| `deploy/dev/tools.js` | Replace mesh traversal with `_matCache` iteration |
| `deploy/dev/streaming.js` | Ensure `_matCache` is accessible to tools.js |

## Priority

Approach 1 first (minimal change, big impact). Approach 3 if Approach 1 is still laggy.
