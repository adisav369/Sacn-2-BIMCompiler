# ⚠ DO NOT REMOVE — scope: port the Viewer Find panel's zoom-to-frame-selection camera behavior into the Modeller, read the log after every run

**Why this exists:** user asked whether selecting an element in the Modeller zooms/reframes the camera to
face it, "following Find Panel in Viewer" — i.e. port the EXACT mechanism the Viewer's Find panel already
uses to frame a result, not the different Auto-Pivot/precision-cam system (that's orbit-pivot-recenter on
`Q`, a separate, NOT-in-scope feature — do not build or reference it). Confirmed by direct code search
(2026-07-08, before writing this spec): zero camera-focus/zoom/frame code exists anywhere in
`modeller.html` today — this is a genuine port, not a tweak.

## Exact ground truth — the SOURCE mechanism (Viewer, `viewer/navigate_find.js`, do not modify this file)

```js
var _lerpId = 0, _lerpHooked = false;
function _lerpCam(center, dist) {
  if (!A.camera || !A.controls || typeof THREE === 'undefined') return;
  // §FLY-YIELD: the moment the user grabs the controls (OrbitControls 'start'), cancel any in-flight
  // fly — otherwise the lerp keeps writing target+position and fights the pull ("hits back").
  if (!_lerpHooked && A.controls.addEventListener) {
    A.controls.addEventListener('start', function() { _lerpId++; });
    _lerpHooked = true;
  }
  var myId = ++_lerpId;
  var end = center.clone().add(new THREE.Vector3(0.5, 0.5, 0.7).normalize().multiplyScalar(dist));
  var start = A.camera.position.clone();
  var t = 0;
  function anim() {
    if (myId !== _lerpId) return;
    t += 0.04; if (t > 1) t = 1;
    var e = 1 - Math.pow(1 - t, 3);
    A.camera.position.lerpVectors(start, end, e);
    A.controls.target.copy(center);
    A.controls.update();
    if (A.markDirty) A.markDirty();
    if (t < 1) requestAnimationFrame(anim);
  }
  anim();
}
function _zoomToBox(center, size, factor) {
  _lerpCam(center, Math.max(size.x, size.y, size.z) * (factor || 3) + 1);
}
function _fitDistForBox(size) {
  var dir = new THREE.Vector3(0.5, 0.5, 0.7).normalize();
  var fwd = dir.clone().negate();
  var right = new THREE.Vector3().crossVectors(fwd, new THREE.Vector3(0, 1, 0)).normalize();
  var up = new THREE.Vector3().crossVectors(right, fwd).normalize();
  var hx = size.x / 2, hy = size.y / 2, hz = size.z / 2, hW = 0, hH = 0, hD = 0, c = new THREE.Vector3();
  for (var sx = -1; sx <= 1; sx += 2) for (var sy = -1; sy <= 1; sy += 2) for (var sz = -1; sz <= 1; sz += 2) {
    c.set(sx * hx, sy * hy, sz * hz);
    hW = Math.max(hW, Math.abs(c.dot(right))); hH = Math.max(hH, Math.abs(c.dot(up))); hD = Math.max(hD, Math.abs(c.dot(fwd)));
  }
  var tanV = Math.tan((A.camera.fov || 50) * Math.PI / 360);
  var tanH = tanV * (A.camera.aspect || 1);
  return (Math.max(hH / tanV, hW / tanH) + hD * 0.3) * 1.03;
}
function _zoomToBoxFill(center, size, tag, mult) {
  if (!A.camera || !size) return false;
  var dist = _fitDistForBox(size) * (mult || 1);
  _lerpCam(center, dist);
  console.log('[RP-TB] §' + (tag || 'GROUP_ZOOM') + ' fill dist=' + dist.toFixed(1) + ' mult=' + (mult || 1) +
    ' size=' + size.x.toFixed(1) + 'x' + size.y.toFixed(1) + 'x' + size.z.toFixed(1));
  return true;
}
function _zoomToGroup(set) {
  var bb = _bboxOfGuids(set); if (!bb || !A.camera) return false;
  return _zoomToBoxFill(bb.center, bb.size, 'GROUP_ZOOM');
}
```
This is the proven pattern to port: a 0.3s eased camera-position lerp toward a point offset from the
target's center along a fixed iso-angle direction, at a distance computed to FILL the viewport (frustum-fit
via FOV/aspect against the box's projected extent, not a naive bounding-sphere fit — the code comments
explain why the naive version undersells the frame). `FLY-YIELD` (cancel on user grabbing OrbitControls) is
load-bearing — port it, don't drop it, or a user rotating mid-fly will fight the animation.

**⚠ Z-up vs Y-up — verify, do not blindly copy the offset vector.** `modeller.html` (~line 383) states "ONE
consistent Z-up convention for the whole 3D scene: OrbitControls then orbits LEVEL about +Z." The Viewer's
`_lerpCam`/`_fitDistForBox` use a fixed offset direction `(0.5, 0.5, 0.7)` and `up = (0,1,0)` in `_fitDistForBox`'s
right/up basis construction — these assume the VIEWER's own up-axis convention. Before porting, check what
axis convention `modeller.html`'s OWN camera/controls setup actually uses (Z-up per the comment) and adapt
the offset direction AND the `up` vector used in `_fitDistForBox`'s basis construction accordingly, so the
resulting frame is a sensible iso angle in the MODELLER's actual coordinate system, not a sideways/upside-down
frame from blindly copying Y-up constants into a Z-up scene. Verify empirically (screenshot or hand-check the
resulting camera position/orientation makes sense for a known element), don't assume the port is correct
just because it compiles.

## Ground truth — the TARGET (Modeller, `modeller/modeller.html`)

- Camera/controls already exist at module scope (~lines 388/395): `const camera = new THREE.PerspectiveCamera(55, ...)`,
  `const controls = new THREE.OrbitControls(camera, canvas)` — same shape as the Viewer's `A.camera`/`A.controls`,
  just local `const`s instead of namespaced on a global object. Reference them directly (this file doesn't need
  an `A.` indirection layer the way the Viewer's shared module does).
- Selection state already exists: `selectedIds` (a `Set` of feature ids, ~line 940), `selectedId` (primary/anchor,
  used by "every single-target path (cut/fillet/lod/frame)" per the comment at ~line 937 — note "frame" is
  already named there as a FUTURE consumer of the primary selection, consistent with this task), `selMeshes()`
  (~line 942, returns the actual THREE mesh objects for the current selection).
- Single-mesh bbox pattern already in use: `new THREE.Box3().setFromObject(selectedMesh)` (~line 478) — for
  MULTI-select, union `Box3`s across `selMeshes()`'s results the same way the Viewer's `_bboxOfGuids` unions
  across a GUID set (check `_bboxOfGuids` in `navigate_find.js` for the exact union-box shape to mirror, or
  just `.union()` each mesh's own `Box3` — THREE.js's own `Box3.union()` method is the standard tool here,
  don't hand-roll a bbox merge).
- The natural integration point is wherever the selection actually changes and already logs `§MODELLER select`
  (~lines 985-995, `setSelectionIds`) — call the new zoom-to-selection function from there, so it fires
  automatically on every real selection change (click, marquee, Outliner pick — anywhere `setSelectionIds` is
  the entry point), mirroring how Find's zoom fires automatically when a result gets selected/framed in the
  Viewer. This is the explicit behavior requested: "following Find Panel in Viewer" (automatic on select, not
  a separate manual trigger/hotkey).

## Task

1. Port `_lerpCam`, `_zoomToBox` (or just `_zoomToBoxFill`/`_fitDistForBox` if you only need the fill-frame
   variant — check whether the Modeller wants the simple `maxDim*factor` heuristic or the full frustum-fit;
   default to the frustum-fit version since it's the more correct/complete one and the simple one exists in
   the source only as a lighter-weight alternative, not because it's better) into `modeller.html`, adapted for
   the Z-up convention per the warning above, referencing the Modeller's own `camera`/`controls` consts
   directly (no `A.` namespace needed).
2. Add a bbox-from-current-selection helper (union `Box3` across `selMeshes()`), mirroring `_bboxOfGuids`'s
   shape but sourced from the Modeller's own selection mechanism, not GUIDs-in-a-set the Viewer's way.
3. Wire it into `setSelectionIds` (or wherever the actual selection-change entry point is — confirm the exact
   function, cite its line number in your report) so selecting ANY element(s) — single click, marquee
   multi-select, or a pick originating from the Outliner — triggers the same zoom-to-fill-frame behavior,
   automatically, matching Find's automatic behavior in the Viewer.
4. Skip the fly (no-op, do not error) when nothing is selected (empty `selectedIds`) — mirror the Viewer's own
   `if (!bb || !A.camera) return false;` guard shape.
5. Do NOT build Auto-Pivot, do NOT add a keybinding/toggle for this — the requested behavior is automatic-on-
   select only, matching Find's own always-automatic framing, not a sticky/manual mode.

## Verification required before reporting done

- Real Puppeteer interaction (mirror `e2e_harness.js`'s `t.open(key)` pattern used throughout this session) —
  open a resident (Duplex or SampleCastle), record the camera's starting position, click/select a SPECIFIC
  known element far from the current view, and assert via hand-derived checks: (a) the camera position
  actually changed toward the selected element's real bbox center (not just "some property fired") — compute
  the expected end position independently using the SAME frustum-fit math as the source (or a simplified but
  correct equivalent) and compare against the camera's final position within a reasonable tolerance, (b) the
  selected element is genuinely closer to filling the viewport after the fly than before (a real, measurable
  claim — e.g. compare the element's on-screen projected bbox size before/after, not just "camera moved"),
  (c) selecting a SECOND, different element re-triggers a new fly to the new target (not stuck on the first),
  (d) grabbing/rotating the OrbitControls mid-fly cancels the in-flight animation cleanly (the `FLY-YIELD`
  behavior) — a real interaction test, not just code inspection.
  Same numeric rigor as every other witness this session — hand-computed expected values, not eyeballed
  screenshots (screenshots for human skim only, never cited as the proof).
- Regression: confirm existing selection-dependent features still work correctly after this change (cut/
  fillet/lod/"frame" consumers of `selectedId` named in the code comment at ~line 937 — run whatever existing
  witnesses touch selection, e.g. sketch/circle/arc/tangent witnesses that rely on selection state, to confirm
  nothing broke).
- Name the new witness `modeller/tests/witness_e2e_zoom_to_selection.js`, `t.assert`/K-numbered convention.
- Do NOT deploy or push — commit on a fresh worktree branch cut from `origin/main`, suggest branch name
  `feat/modeller-zoom-to-selection`. Report back: exact diff, the Z-up adaptation you made and how you
  verified it produces a sensible frame (not just "it compiled"), full witness output, regression results.
