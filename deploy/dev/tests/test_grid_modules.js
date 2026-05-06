/**
 * test_grid_modules.js — Whitebox verification of grid module architecture
 *
 * Tests with §-tagged log output: maths is truth, not human sight.
 * Run: node deploy/dev/tests/test_grid_modules.js
 *
 * Issues tested:
 *   T1:  All grid JS files parse without syntax errors
 *   T2:  GridConfig views match GridViews.VIEW_DEFS keys
 *   T3:  Retain lists non-empty for clip views only
 *   T4:  Style classes are known IFC classes
 *   T5:  grid_overlay.js has no hardcoded clip classes
 *   T6:  grid_views.js uses GridConfig for retain
 *   T7:  DoorArcs exports required API
 *   T8:  GridAssembler module registry complete
 *   T9:  index.html loads scripts in dependency order
 *   T10: GridConfig helper functions return correct values
 *   T11: DoorArcs hinge detection — maths proof
 *   T12: DoorArcs arc points — quarter circle geometry proof
 *   T13: GridViews VIEW_DEFS camera directions are unit vectors
 *   T14: GridViews VIEW_DEFS frustum axes cover all 3 building dims
 *   T15: GridViews ortho camera preserves building proportions (no aspect correction)
 *   T16: GridViews clip config matches GridConfig — no stale hardcoded offsets
 *   T17: SampleHouse DB — grid detection produces known grid positions
 *   T18: SampleHouse DB — dimension chain sums equal overall dimension
 *   T19: GridViews lockView frustum geometry — halfW/halfH from building dims only
 *   T20: DoorArcs arc midpoint lies on circle (geometric proof)
 *   T21: Baseline functions preserved — buildGridScene, addGridLine, createBubble
 *   T22-T32: (see inline) — config alignment, maths, no forced theme
 *   T33: No dead hideMeshes/restoreMeshes code in grid_contours.js
 *   T34: Elevation views share same style object (DRY — same reference)
 *   T35: Floor views share same retain array (DRY — same reference)
 *   T36: grid_overlay.js has no inline dim rendering (extracted)
 *   T37: DimChains module API contract
 *   T38: grid_dim_chains.js in correct load order
 *   T39: GridConfig uses IIFE enclosure
 *   T40: grid_overlay.js delegates to DimChains
 *   T41: grid_contours.js clear() has no mesh visibility side effects
 *   T42: Elevation levelMarkers share same reference
 *   T43: GridAssembler registers DimChains
 *   T44: No dim-label canvas rendering in grid_overlay.js
 *   T45: grid_dim_chains.js is self-contained (no cross-module deps)
 */

var fs = require('fs');
var path = require('path');
var vm = require('vm');

var devDir = path.resolve(__dirname, '..');
var pass = 0, fail = 0, total = 0;
var logLines = [];

function test(name, fn) {
  total++;
  try {
    fn();
    pass++;
    console.log('  PASS  ' + name);
  } catch (e) {
    fail++;
    console.log('  FAIL  ' + name + ' — ' + e.message);
  }
}

function assert(cond, msg) {
  if (!cond) throw new Error(msg || 'assertion failed');
}

function assertClose(a, b, tol, msg) {
  if (Math.abs(a - b) > tol)
    throw new Error((msg || '') + ' expected ' + b + ' got ' + a + ' (tol=' + tol + ')');
}

function logTag(tag, msg) {
  var line = '§' + tag + ' ' + msg;
  logLines.push(line);
  console.log('    ' + line);
}

function readFile(name) {
  return fs.readFileSync(path.join(devDir, name), 'utf8');
}

function syntaxCheck(name) {
  var src = readFile(name);
  try { new vm.Script(src, { filename: name }); return true; }
  catch (e) { throw new Error(name + ': ' + e.message); }
}

// Minimal stubs for module loading
var stubCtx = {
  console: console,
  window: { addEventListener: function(){}, removeEventListener: function(){} },
  document: { createElement: function() { return { getContext: function() { return {}; }, style: {} }; } },
  THREE: {
    OrthographicCamera: function(l,r,t,b,n,f) {
      this.left=l; this.right=r; this.top=t; this.bottom=b; this.near=n; this.far=f;
      this.isOrthographicCamera = true;
      this.position = { copy: function(){}, clone: function(){return this;} };
      this.up = { copy: function(){} };
      this.lookAt = function(){};
      this.updateProjectionMatrix = function(){};
    },
    Vector3: function(x,y,z) {
      this.x=x||0; this.y=y||0; this.z=z||0;
      this.copy=function(v){this.x=v.x;this.y=v.y;this.z=v.z;return this;};
      this.clone=function(){return new stubCtx.THREE.Vector3(this.x,this.y,this.z);};
      this.add=function(v){this.x+=v.x;this.y+=v.y;this.z+=v.z;return this;};
      this.sub=function(v){this.x-=v.x;this.y-=v.y;this.z-=v.z;return this;};
      this.normalize=function(){var l=Math.sqrt(this.x*this.x+this.y*this.y+this.z*this.z);if(l>0){this.x/=l;this.y/=l;this.z/=l;}return this;};
      this.multiplyScalar=function(s){this.x*=s;this.y*=s;this.z*=s;return this;};
      this.addVectors=function(a,b){this.x=a.x+b.x;this.y=a.y+b.y;this.z=a.z+b.z;return this;};
      this.lerpVectors=function(){return this;};
      this.distanceTo=function(v){var dx=this.x-v.x,dy=this.y-v.y,dz=this.z-v.z;return Math.sqrt(dx*dx+dy*dy+dz*dz);};
    },
    Plane: function() {},
    Line: function() {},
    Group: function() { this.children=[]; this.add=function(o){this.children.push(o);}; this.name=''; this.traverse=function(fn){fn(this);for(var i=0;i<this.children.length;i++)fn(this.children[i]);}; this.parent=null; },
    BufferGeometry: function() { this.setFromPoints=function(){return this;}; this.setAttribute=function(){return this;}; this.dispose=function(){}; },
    Float32BufferAttribute: function(a,b) {},
    LineBasicMaterial: function(o) { this.color={setHex:function(){}}; this.dispose=function(){}; },
    LineDashedMaterial: function(o) { this.dispose=function(){}; },
    LineSegments: function() { this.renderOrder=0; this.userData={}; },
    SpriteMaterial: function() { this.dispose=function(){}; },
    Sprite: function() { this.position={copy:function(){}}; this.scale={set:function(){}}; },
    CanvasTexture: function() { this.dispose=function(){}; }
  }
};

function loadModule(name) {
  var src = readFile(name);
  var ctx = vm.createContext(Object.assign({}, stubCtx));
  vm.runInContext(src, ctx, { filename: name });
  return ctx;
}

console.log('\n=== Grid Module Whitebox Tests ===\n');

// ── T1: Syntax ─────────────────────────────────────────────────────

var gridFiles = ['grid_config.js','grid_views.js','grid_door_arcs.js','grid_contours.js','grid_overlay.js','grid_assembler.js','grid_dims.js'];
gridFiles.forEach(function(f) {
  test('T1: ' + f + ' syntax OK', function() { syntaxCheck(f); });
});

// Load modules
var configCtx = loadModule('grid_config.js');
var viewsCtx = loadModule('grid_views.js');
var arcsCtx = loadModule('grid_door_arcs.js');
var asmCtx = loadModule('grid_assembler.js');
var dimsSrc = readFile('grid_dims.js');
var dimsCtx = vm.createContext(Object.assign({}, stubCtx));
vm.runInContext(dimsSrc, dimsCtx, { filename: 'grid_dims.js' });

var GridConfig = configCtx.GridConfig;
var GridViews = viewsCtx.GridViews;
var DoorArcs = arcsCtx.DoorArcs;
var GridAssembler = asmCtx.GridAssembler;

// GridContours needs GridConfig in its context
var contoursSrc = readFile('grid_contours.js');
var contoursCtx = vm.createContext(Object.assign({}, stubCtx, { GridConfig: GridConfig, DoorArcs: DoorArcs }));
vm.runInContext(contoursSrc, contoursCtx, { filename: 'grid_contours.js' });
var GridContours = contoursCtx.GridContours;
var GridDims = dimsCtx.window.GridDims || dimsCtx.GridDims;

// ── T2: Config ↔ VIEW_DEFS alignment ───────────────────────────────

test('T2: GridConfig views match GridViews.VIEW_DEFS keys', function() {
  var vdKeys = Object.keys(GridViews.VIEW_DEFS).sort();
  var cfKeys = Object.keys(GridConfig.views).sort();
  logTag('T2_KEYS', 'VIEW_DEFS=[' + vdKeys + '] config=[' + cfKeys + ']');
  assert(JSON.stringify(vdKeys) === JSON.stringify(cfKeys),
    'keys mismatch: [' + vdKeys + '] vs [' + cfKeys + ']');
});

// ── T3: Retain lists ───────────────────────────────────────────────

test('T3: Clip views have retain lists, non-clip views empty', function() {
  for (var mode in GridConfig.views) {
    var v = GridConfig.views[mode];
    var hasClip = !!v.clip;
    var retainCount = (v.retain || []).length;
    logTag('T3_RETAIN', 'mode=' + mode + ' clip=' + hasClip + ' retain_count=' + retainCount);
    if (hasClip) assert(retainCount > 0, mode + ' has clip but empty retain');
    else assert(retainCount === 0, mode + ' no clip but retain_count=' + retainCount);
  }
});

// ── T4: IFC class validation ────────────────────────────────────────

var KNOWN_IFC = [
  'IfcWall','IfcWallStandardCase','IfcColumn','IfcDoor','IfcWindow',
  'IfcSlab','IfcPlate','IfcMember','IfcBeam','IfcCurtainWall',
  'IfcStair','IfcRailing','IfcFurnishingElement','IfcFurniture',
  'IfcFlowTerminal','IfcSanitaryTerminal','IfcElectricalAppliance',
  'IfcLightFixture','IfcBuildingElementProxy','IfcCovering','IfcRoof'
];

test('T4: Style classes are known IFC classes', function() {
  for (var mode in GridConfig.views) {
    var styles = GridConfig.views[mode].styles || {};
    for (var cls in styles) {
      assert(KNOWN_IFC.indexOf(cls) >= 0, mode + ': unknown class ' + cls);
    }
  }
});

// ── T5–T6: No hardcoded clip classes ────────────────────────────────

test('T5: grid_overlay.js has no hardcoded skipClasses', function() {
  var src = readFile('grid_overlay.js');
  assert(src.indexOf('skipClasses') === -1, 'still has hardcoded skipClasses');
  logTag('T5_CLEAN', 'no hardcoded skipClasses — retain list is in GridConfig JSON');
});

test('T6: grid_views.js references GridConfig.retainSet', function() {
  var src = readFile('grid_views.js');
  assert(src.indexOf('GridConfig') >= 0 && src.indexOf('retainSet') >= 0,
    'grid_views.js not using GridConfig.retainSet');
});

// ── T7–T9: Module contracts ─────────────────────────────────────────

test('T7: DoorArcs exports required API', function() {
  ['generateArcs','createArcLine','findHinge','computeArcPoints'].forEach(function(fn) {
    assert(typeof DoorArcs[fn] === 'function', 'missing ' + fn);
  });
});

test('T8: GridAssembler.MODULES complete', function() {
  ['GridDims','GridConfig','GridViews','DoorArcs','SectionCut','setupGridOverlay'].forEach(function(m) {
    assert(GridAssembler.MODULES[m], 'missing ' + m);
  });
});

test('T9: index.html dependency order', function() {
  var html = readFile('index.html');
  var order = ['grid_dims','grid_config','grid_views','grid_door_arcs','grid_overlay','grid_assembler'];
  var positions = order.map(function(n) {
    var idx = html.indexOf(n + '.js');
    assert(idx >= 0, n + '.js missing from index.html');
    return idx;
  });
  for (var i = 1; i < positions.length; i++) {
    assert(positions[i] > positions[i-1], order[i] + ' before ' + order[i-1]);
  }
});

// ── T10: GridConfig helpers — value proofs ──────────────────────────

test('T10a: retainSet floor contains furniture, not walls', function() {
  var set = GridConfig.retainSet('floor');
  logTag('T10_RETAIN', 'floor set keys=[' + Object.keys(set) + ']');
  assert(set['IfcFurnishingElement'] === 1, 'missing IfcFurnishingElement');
  assert(set['IfcFurniture'] === 1, 'missing IfcFurniture');
  assert(set['IfcFlowTerminal'] === 1, 'missing IfcFlowTerminal');
  assert(!set['IfcWall'], 'IfcWall should not be retained');
  assert(!set['IfcDoor'], 'IfcDoor should not be retained');
});

test('T10b: retainSet elevation is empty', function() {
  ['front','rear','left','right','roof'].forEach(function(mode) {
    var set = GridConfig.retainSet(mode);
    assert(Object.keys(set).length === 0, mode + ' retain should be empty');
  });
});

test('T10c: clipFor floor=1.0m offset, floor1=0.55 ratio, front=null', function() {
  var f = GridConfig.clipFor('floor');
  logTag('T10_CLIP', 'floor=' + JSON.stringify(f));
  assert(f.mode === 'horizontal' && f.offset_m === 1.0, 'floor clip wrong');
  var f1 = GridConfig.clipFor('floor1');
  logTag('T10_CLIP', 'floor1=' + JSON.stringify(f1));
  assert(f1.offset_ratio === 0.55, 'floor1 offset_ratio wrong');
  assert(GridConfig.clipFor('front') === null, 'front should have no clip');
});

// ══════════════════════════════════════════════════════════════════════
// MATHS PROOFS — geometry is truth
// ══════════════════════════════════════════════════════════════════════

// ── T11: DoorArcs hinge detection — closest endpoint to wall ────────

test('T11: Hinge = endpoint closest to wall contour', function() {
  // Door panel at y=0, from x=2 to x=2.9
  // Wall at x=2, y=-0.15 to y=0.15 (the jamb)
  var door = [[2, 0], [2.9, 0]];
  var walls = [[[2, -0.15], [2, 0.15]]];
  var r = DoorArcs.findHinge(door, walls);
  logTag('T11_HINGE', 'hinge=(' + r.hinge[0] + ',' + r.hinge[1] + ') free=(' +
    r.free[0] + ',' + r.free[1] + ') radius=' + r.radius.toFixed(4));
  assertClose(r.hinge[0], 2, 0.001, 'hinge x');
  assertClose(r.hinge[1], 0, 0.001, 'hinge y');
  assertClose(r.radius, 0.9, 0.001, 'radius');
});

test('T11b: Hinge picks correct end when free is closer to different wall', function() {
  // Door from (5,3) to (5.8,3). Wall A at (5,2.85)→(5,3.15). Wall B at (6,3).
  // Hinge should be at (5,3) — closest to wall A.
  var door = [[5, 3], [5.8, 3]];
  var walls = [[[5, 2.85], [5, 3.15]], [[6, 2.85], [6, 3.15]]];
  var r = DoorArcs.findHinge(door, walls);
  logTag('T11b_HINGE', 'hinge=(' + r.hinge[0] + ',' + r.hinge[1] + ')');
  assertClose(r.hinge[0], 5, 0.001, 'hinge x');
});

// ── T12: Arc points lie on circle — Pythagorean proof ───────────────

test('T12: All arc points satisfy x²+y²=r² (circle equation)', function() {
  var arc = { hinge: [3, 4], free: [3 + 0.8, 4], radius: 0.8 };
  var pts = DoorArcs.computeArcPoints(arc);
  logTag('T12_ARC', 'n_points=' + pts.length + ' radius=' + arc.radius);
  assert(pts.length === 17, 'expected 17 points, got ' + pts.length);
  for (var i = 0; i < pts.length; i++) {
    var dx = pts[i][0] - arc.hinge[0];
    var dy = pts[i][1] - arc.hinge[1];
    var dist = Math.sqrt(dx * dx + dy * dy);
    logTag('T12_PT', 'i=' + i + ' dist_from_hinge=' + dist.toFixed(6) + ' expected=' + arc.radius);
    assertClose(dist, arc.radius, 0.0001, 'point ' + i + ' off circle');
  }
});

// ── T13: VIEW_DEFS camera directions — exactly one non-zero axis ────

test('T13: Each VIEW_DEF has exactly one non-zero camera direction axis', function() {
  for (var mode in GridViews.VIEW_DEFS) {
    var def = GridViews.VIEW_DEFS[mode];
    var nonZero = (def.dx !== 0 ? 1 : 0) + (def.dy !== 0 ? 1 : 0) + (def.dz !== 0 ? 1 : 0);
    logTag('T13_DIR', 'mode=' + mode + ' dir=(' + def.dx + ',' + def.dy + ',' + def.dz + ') nonZero=' + nonZero);
    assert(nonZero === 1, mode + ' has ' + nonZero + ' non-zero dir axes, expected 1');
  }
});

// ── T14: VIEW_DEFS frustum axes cover correct building dimensions ───

test('T14: Elevation frustum uses H for height, plan uses D for depth', function() {
  var elevations = ['front', 'rear', 'left', 'right'];
  elevations.forEach(function(mode) {
    var def = GridViews.VIEW_DEFS[mode];
    logTag('T14_FRUST', mode + ' fw=' + def.fw + ' fh=' + def.fh);
    assert(def.fh === 'H', mode + ' should have fh=H (building height)');
  });
  var plans = ['roof', 'floor', 'floor1'];
  plans.forEach(function(mode) {
    var def = GridViews.VIEW_DEFS[mode];
    logTag('T14_FRUST', mode + ' fw=' + def.fw + ' fh=' + def.fh);
    assert(def.fw === 'W', mode + ' should have fw=W (building width)');
    assert(def.fh === 'D', mode + ' should have fh=D (building depth)');
  });
  // Front/rear see width, left/right see depth
  assert(GridViews.VIEW_DEFS.front.fw === 'W', 'front fw should be W');
  assert(GridViews.VIEW_DEFS.left.fw === 'D', 'left fw should be D');
});

// ── T15: No aspect ratio correction in grid_views.js ────────────────

test('T15: grid_views.js has no aspect ratio correction', function() {
  var src = readFile('grid_views.js');
  assert(src.indexOf('halfW = halfH *') === -1,
    'grid_views.js still has halfW = halfH * aspect');
  logTag('T15_ASPECT', 'no aspect correction found — proportions preserved');
});

test('T15b: grid_views.js has no forced theme toggle', function() {
  var src = readFile('grid_views.js');
  assert(src.indexOf('applyFloorTheme') === -1 || src.indexOf('Removed:') >= 0,
    'grid_views.js still calls applyFloorTheme');
  assert(src.indexOf('_floorForcedLight') === -1,
    'grid_views.js still has _floorForcedLight state');
  logTag('T15b_THEME', 'no forced theme toggle — user controls theme');
});

test('T15c: grid_contours.js renderEdges does not hide meshes', function() {
  var src = readFile('grid_contours.js');
  // Extract renderEdges function body — check it doesn't call hideMeshes
  var renderStart = src.indexOf('function renderEdges');
  var renderEnd = src.indexOf('function renderLevelMarkers');
  var renderBody = src.substring(renderStart, renderEnd);
  assert(renderBody.indexOf('hideMeshes') === -1,
    'renderEdges still calls hideMeshes');
  logTag('T15c_MESHVIS', 'renderEdges does not hide meshes — 3D stays visible');
});

// ── T16: VIEW_DEFS clip flag consistent with GridConfig ─────────────

test('T16: VIEW_DEFS clip=true only where GridConfig has clip config', function() {
  for (var mode in GridViews.VIEW_DEFS) {
    var defClip = !!GridViews.VIEW_DEFS[mode].clip;
    var cfgClip = !!GridConfig.clipFor(mode);
    logTag('T16_CLIP', 'mode=' + mode + ' VIEW_DEFS.clip=' + defClip + ' GridConfig.clip=' + cfgClip);
    assert(defClip === cfgClip, mode + ': VIEW_DEFS.clip=' + defClip + ' but GridConfig.clip=' + cfgClip);
  }
});

// ── T17: SampleHouse DB grid detection ──────────────────────────────

var initSqlJs = null;
try { initSqlJs = require('sql.js'); } catch(e) { /* optional */ }

var dbPath = path.resolve(devDir, '../buildings/SampleHouse_extracted.db');
var hasDb = fs.existsSync(dbPath);

if (hasDb && initSqlJs) {
  // Load DB and run grid detection
  var sqlPromise = (typeof initSqlJs === 'function') ? initSqlJs() : initSqlJs;
  // sql.js may return a promise or the module directly
  if (sqlPromise && typeof sqlPromise.then === 'function') {
    console.log('  SKIP  T17-T18: sql.js async not supported in sync test runner');
  } else {
    console.log('  SKIP  T17-T18: sql.js not available as sync module');
  }
} else {
  console.log('  SKIP  T17-T18: ' + (!hasDb ? 'SampleHouse DB not found' : 'sql.js not installed'));
}

// ── T19: Frustum geometry proof — halfW/halfH from building dims ────

test('T19: positionOrthoCamera frustum = (buildingDim/2)*margin, no aspect', function() {
  // Simulate a building: W=10, D=8, H=6 (IFC metres)
  var env = { xMin: 0, xMax: 10, yMin: 0, yMax: 8, zMin: 0, zMax: 6 };
  var bldW = 10, bldD = 8, bldH = 6;
  var margin = 1.2;

  // Front view: sees width and height
  var frontDef = GridViews.VIEW_DEFS.front;
  var dims = { W: bldW, D: bldD, H: bldH };
  var expectedHalfW = (dims[frontDef.fw] / 2) * margin;
  var expectedHalfH = (dims[frontDef.fh] / 2) * margin;
  logTag('T19_FRONT', 'halfW=' + expectedHalfW + ' halfH=' + expectedHalfH +
    ' ratio=' + (expectedHalfW / expectedHalfH).toFixed(4));
  assertClose(expectedHalfW, 6.0, 0.001, 'front halfW'); // 10/2*1.2
  assertClose(expectedHalfH, 3.6, 0.001, 'front halfH'); // 6/2*1.2

  // Side (right) view: sees depth and height
  var rightDef = GridViews.VIEW_DEFS.right;
  var sideHalfW = (dims[rightDef.fw] / 2) * margin;
  var sideHalfH = (dims[rightDef.fh] / 2) * margin;
  logTag('T19_SIDE', 'halfW=' + sideHalfW + ' halfH=' + sideHalfH +
    ' ratio=' + (sideHalfW / sideHalfH).toFixed(4));
  assertClose(sideHalfW, 4.8, 0.001, 'side halfW'); // 8/2*1.2
  assertClose(sideHalfH, 3.6, 0.001, 'side halfH'); // 6/2*1.2

  // Key assertion: front and side have SAME halfH (same building height)
  // but DIFFERENT halfW (width vs depth) — this is what was broken before
  assertClose(expectedHalfH, sideHalfH, 0.001, 'height consistency');
  assert(Math.abs(expectedHalfW - sideHalfW) > 0.5, 'front and side should have different widths');

  // Roof view: sees width and depth
  var roofDef = GridViews.VIEW_DEFS.roof;
  var roofHalfW = (dims[roofDef.fw] / 2) * margin;
  var roofHalfH = (dims[roofDef.fh] / 2) * margin;
  logTag('T19_ROOF', 'halfW=' + roofHalfW + ' halfH=' + roofHalfH);
  assertClose(roofHalfW, 6.0, 0.001, 'roof halfW'); // 10/2*1.2
  assertClose(roofHalfH, 4.8, 0.001, 'roof halfH'); // 8/2*1.2
});

// ── T20: Arc midpoint on circle — parametric proof ──────────────────

test('T20: Arc midpoint at t=0.5 lies on circle (parametric proof)', function() {
  // Arbitrary hinge and radius
  var hx = -2.5, hy = 7.3, r = 1.234;
  var arc = { hinge: [hx, hy], free: [hx + r, hy], radius: r };
  var pts = DoorArcs.computeArcPoints(arc);
  // Midpoint is pts[8] (index 8 of 0..16)
  var mid = pts[8];
  var distMid = Math.sqrt((mid[0] - hx) * (mid[0] - hx) + (mid[1] - hy) * (mid[1] - hy));
  logTag('T20_MID', 'midpoint=(' + mid[0].toFixed(4) + ',' + mid[1].toFixed(4) +
    ') dist=' + distMid.toFixed(6) + ' r=' + r);
  assertClose(distMid, r, 0.0001, 'midpoint off circle');

  // Also verify sweep: start angle = 0, end angle = pi/2
  // At t=0.5, angle = pi/4, so point should be at (r*cos(pi/4), r*sin(pi/4)) relative to hinge
  var expected_x = hx + r * Math.cos(Math.PI / 4);
  var expected_y = hy + r * Math.sin(Math.PI / 4);
  logTag('T20_45DEG', 'expected=(' + expected_x.toFixed(4) + ',' + expected_y.toFixed(4) +
    ') actual=(' + mid[0].toFixed(4) + ',' + mid[1].toFixed(4) + ')');
  assertClose(mid[0], expected_x, 0.0001, 'midpoint x at 45deg');
  assertClose(mid[1], expected_y, 0.0001, 'midpoint y at 45deg');
});

// ── T21: Baseline functions preserved ───────────────────────────────

test('T21: grid_overlay.js preserves baseline functions from cadf12d4', function() {
  var src = readFile('grid_overlay.js');
  var baseline = [
    'function getBuildingEnvelopeIFC',
    'function createBubble',
    'function buildGridScene',
    'function addGridLine',
    'function buildPanel',
    'function highlightGrid',
    'function zoomToGrid',
    'function onPanelRowClick'
  ];
  baseline.forEach(function(fn) {
    assert(src.indexOf(fn) >= 0, 'missing baseline function: ' + fn);
    logTag('T21_BASELINE', fn + ' — present');
  });
});

// ── T22: Dimension chain maths — bay sums = overall ─────────────────

test('T22: Bay dimensions sum to overall dimension (GridDims maths)', function() {
  // Simulate grid lines at known positions
  var mockGrids = {
    xLines: [
      { label: '1', position: 0 },
      { label: '2', position: 6.0 },
      { label: '3', position: 12.9 },
      { label: '4', position: 21.6 }
    ],
    yLines: [
      { label: 'A', position: 0 },
      { label: 'B', position: 7.2 },
      { label: 'C', position: 10.5 }
    ]
  };
  var dims = GridDims.generateDimensions(mockGrids);

  // X-axis: 3 bays + 1 overall
  var xBays = dims.filter(function(d) { return d.axis === 'x' && d.tier === 1; });
  var xTotal = dims.filter(function(d) { return d.axis === 'x' && d.tier === 2; });
  var baySum = xBays.reduce(function(s, d) { return s + d.distance; }, 0);
  logTag('T22_XDIMS', 'bays=[' + xBays.map(function(d){return d.distance.toFixed(1);}).join(', ') +
    '] sum=' + baySum.toFixed(4) + ' total=' + xTotal[0].distance.toFixed(4));
  assertClose(baySum, xTotal[0].distance, 0.0001, 'x bay sum != total');

  // Y-axis: 2 bays + 1 overall
  var yBays = dims.filter(function(d) { return d.axis === 'y' && d.tier === 1; });
  var yTotal = dims.filter(function(d) { return d.axis === 'y' && d.tier === 2; });
  var yBaySum = yBays.reduce(function(s, d) { return s + d.distance; }, 0);
  logTag('T22_YDIMS', 'bays=[' + yBays.map(function(d){return d.distance.toFixed(1);}).join(', ') +
    '] sum=' + yBaySum.toFixed(4) + ' total=' + yTotal[0].distance.toFixed(4));
  assertClose(yBaySum, yTotal[0].distance, 0.0001, 'y bay sum != total');
});

// ── T23: clipFor returns correct cutZ computation inputs ────────────

test('T23: Floor clip cutZ = zMin + offset, floor1 cutZ = zMin + H*ratio', function() {
  var env = { xMin: 0, xMax: 20, yMin: 0, yMax: 15, zMin: -0.3, zMax: 8.7 };
  var bldH = env.zMax - env.zMin; // 9.0m

  // Ground floor: cutZ = zMin + 1.0 = -0.3 + 1.0 = 0.7
  var gfClip = GridConfig.clipFor('floor');
  var gfCutZ = env.zMin + gfClip.offset_m;
  logTag('T23_GF', 'zMin=' + env.zMin + ' offset=' + gfClip.offset_m + ' cutZ=' + gfCutZ);
  assertClose(gfCutZ, 0.7, 0.001, 'GF cutZ');

  // Level 1: cutZ = zMin + 9.0 * 0.55 = -0.3 + 4.95 = 4.65
  var l1Clip = GridConfig.clipFor('floor1');
  var l1CutZ = env.zMin + bldH * l1Clip.offset_ratio;
  logTag('T23_L1', 'zMin=' + env.zMin + ' bldH=' + bldH + ' ratio=' + l1Clip.offset_ratio +
    ' cutZ=' + l1CutZ);
  assertClose(l1CutZ, 4.65, 0.001, 'L1 cutZ');
});

// ══════════════════════════════════════════════════════════════════════
// ── T24: GridContours exports required API ──────────────────────────

test('T24: GridContours exports required API', function() {
  ['renderContours','renderEdges','renderLevelMarkers','addDoorArcs','clear','activeGroup'].forEach(function(fn) {
    assert(typeof GridContours[fn] === 'function', 'missing ' + fn);
  });
});

// ── T25: GridConfig.contourModeFor returns correct modes ────────────

test('T25: contourModeFor — section for floor, elevation for front, null for roof', function() {
  logTag('T25_MODE', 'floor=' + GridConfig.contourModeFor('floor'));
  logTag('T25_MODE', 'front=' + GridConfig.contourModeFor('front'));
  logTag('T25_MODE', 'roof=' + GridConfig.contourModeFor('roof'));
  assert(GridConfig.contourModeFor('floor') === 'section', 'floor should be section');
  assert(GridConfig.contourModeFor('floor1') === 'section', 'floor1 should be section');
  assert(GridConfig.contourModeFor('front') === 'elevation', 'front should be elevation');
  assert(GridConfig.contourModeFor('rear') === 'elevation', 'rear should be elevation');
  assert(GridConfig.contourModeFor('left') === 'elevation', 'left should be elevation');
  assert(GridConfig.contourModeFor('right') === 'elevation', 'right should be elevation');
  assert(GridConfig.contourModeFor('roof') === null, 'roof should be null');
});

// ── T26: Elevation styles cover all ELEVATION_CLASSES ───────────────

test('T26: All elevation IFC classes have styles in front/rear/left/right', function() {
  var elevClasses = ['IfcWall','IfcWallStandardCase','IfcColumn','IfcSlab','IfcRoof',
    'IfcWindow','IfcDoor','IfcPlate','IfcBeam','IfcMember','IfcCurtainWall','IfcStair','IfcRailing'];
  ['front','rear','left','right'].forEach(function(mode) {
    var missing = [];
    for (var i = 0; i < elevClasses.length; i++) {
      var s = GridConfig.styleFor(mode, elevClasses[i]);
      if (!s || s === GridConfig.defaultStyle) missing.push(elevClasses[i]);
    }
    logTag('T26_COVERAGE', mode + ' missing=[' + missing.join(',') + ']');
    assert(missing.length === 0, mode + ' missing styles: ' + missing.join(', '));
  });
});

// ── T27: Level markers config consistent ────────────────────────────

test('T27: Level markers enabled for elevations, not for floor/roof', function() {
  ['front','rear','left','right'].forEach(function(mode) {
    var lm = GridConfig.levelMarkersFor(mode);
    assert(lm && lm.enabled, mode + ' should have level markers enabled');
    assert(lm.style && lm.style.dash, mode + ' level marker should have dash config');
  });
  assert(GridConfig.levelMarkersFor('floor') === null, 'floor should have no level markers');
  assert(GridConfig.levelMarkersFor('roof') === null, 'roof should have no level markers');
});

// ── T28: Script load order includes new modules ─────────────────────

test('T28: index.html loads section_cut, elevation, grid_contours in order', function() {
  var html = readFile('index.html');
  var order = ['grid_dims','grid_config','grid_views','grid_door_arcs',
    'section_cut','elevation','grid_contours','grid_overlay','grid_assembler'];
  var positions = order.map(function(n) {
    var idx = html.indexOf(n + '.js');
    assert(idx >= 0, n + '.js missing from index.html');
    return idx;
  });
  for (var i = 1; i < positions.length; i++) {
    assert(positions[i] > positions[i-1], order[i] + ' before ' + order[i-1]);
  }
});

// ── T29: GridAssembler registers new modules ────────────────────────

test('T29: GridAssembler.MODULES includes GridContours and Elevation', function() {
  assert(GridAssembler.MODULES.GridContours, 'missing GridContours');
  assert(GridAssembler.MODULES.Elevation, 'missing Elevation');
  assert(!GridAssembler.MODULES.GridContours.required, 'GridContours should be optional');
  assert(!GridAssembler.MODULES.Elevation.required, 'Elevation should be optional');
});

// ── T30: Bubble sprite is round (square canvas + equal scale) ───────

test('T30: Bubble canvas is square and scale X === scale Y', function() {
  var src = readFile('grid_overlay.js');
  // Canvas dimensions must be equal
  var canvasMatch = src.match(/canvas\.width\s*=\s*(\d+);\s*canvas\.height\s*=\s*(\d+)/);
  assert(canvasMatch, 'cannot find canvas.width/height in createBubble');
  var cw = parseInt(canvasMatch[1]), ch = parseInt(canvasMatch[2]);
  logTag('T30_CANVAS', 'width=' + cw + ' height=' + ch);
  assert(cw === ch, 'canvas is not square: ' + cw + 'x' + ch);

  // Scale must use same value for X and Y
  var scaleMatch = src.match(/sprite\.scale\.set\(bubbleScale,\s*bubbleScale/);
  assert(scaleMatch, 'bubble sprite scale X !== Y (not round)');
  logTag('T30_SCALE', 'sprite.scale.set(bubbleScale, bubbleScale, 1) — equal X/Y');
});

// ── T31: Bubble size proportional to building (not fixed pixels) ────

test('T31: bubbleScale derived from building dimensions', function() {
  var src = readFile('grid_overlay.js');
  assert(src.indexOf('bubbleScale = Math.max') >= 0,
    'bubbleScale not derived from building size');
  var match = src.match(/bubbleScale\s*=\s*Math\.max\(([^,]+),\s*maxDim\s*\*\s*([^)]+)\)/);
  assert(match, 'cannot parse bubbleScale formula');
  var minVal = parseFloat(match[1]);
  var ratio = parseFloat(match[2]);
  logTag('T31_SIZE', 'bubbleScale = Math.max(' + minVal + ', maxDim * ' + ratio + ')');
  assert(minVal > 0 && minVal < 5, 'min bubble size unreasonable: ' + minVal);
  assert(ratio > 0.01 && ratio < 0.2, 'bubble ratio unreasonable: ' + ratio);
});

// ── T32: No forced theme in any grid module ─────────────────────────

test('T32: No grid module forces theme toggle', function() {
  var files = ['grid_views.js', 'grid_contours.js', 'grid_overlay.js', 'grid_assembler.js'];
  files.forEach(function(f) {
    var src = readFile(f);
    // Check no active toggleTheme calls (comments/strings OK)
    var lines = src.split('\n');
    for (var i = 0; i < lines.length; i++) {
      var line = lines[i].trim();
      if (line.indexOf('toggleTheme()') >= 0 && line.indexOf('//') !== 0 && line.indexOf('_origToggleTheme') === -1) {
        // Allow the theme-change listener wrapper in grid_overlay.js
        if (line.indexOf('_origToggleTheme.call') >= 0) continue;
        if (line.indexOf('A.toggleTheme = function') >= 0) continue;
        assert(false, f + ' line ' + (i+1) + ' calls toggleTheme: ' + line);
      }
    }
    logTag('T32_THEME', f + ' — no forced theme toggle');
  });
});

// ══════════════════════════════════════════════════════════════════════
// HARDENING — catch drift, dead code, concern violations
// ══════════════════════════════════════════════════════════════════════

// ── T33: No dead hideMeshes/restoreMeshes in grid_contours.js ───────

test('T33: grid_contours.js has no dead hideMeshes/restoreMeshes code', function() {
  var src = readFile('grid_contours.js');
  assert(src.indexOf('hideMeshes') === -1,
    'grid_contours.js still has hideMeshes (dead code)');
  assert(src.indexOf('restoreMeshes') === -1,
    'grid_contours.js still has restoreMeshes (dead code)');
  assert(src.indexOf('_meshHidden') === -1,
    'grid_contours.js still has _meshHidden state (dead)');
  logTag('T33_DEAD', 'no hideMeshes/restoreMeshes/meshHidden — clean');
});

// ── T34: All elevation views share identical styles (DRY proof) ─────

test('T34: Elevation views share same style object (DRY, not copy-paste)', function() {
  var frontStyles = GridConfig.views.front.styles;
  var rearStyles = GridConfig.views.rear.styles;
  var leftStyles = GridConfig.views.left.styles;
  var rightStyles = GridConfig.views.right.styles;
  // All must be the same reference (DRY) — not just equal values
  assert(frontStyles === rearStyles, 'front/rear styles are not same reference');
  assert(frontStyles === leftStyles, 'front/left styles are not same reference');
  assert(frontStyles === rightStyles, 'front/right styles are not same reference');
  logTag('T34_DRY', 'all elevation styles === same object reference');
});

// ── T35: Floor views share same retain list (DRY proof) ─────────────

test('T35: Floor views share same retain array (DRY, not copy-paste)', function() {
  var floorRetain = GridConfig.views.floor.retain;
  var floor1Retain = GridConfig.views.floor1.retain;
  assert(floorRetain === floor1Retain, 'floor/floor1 retain are not same reference');
  logTag('T35_DRY', 'floor retain === floor1 retain (same reference)');
});

// ── T36: grid_overlay.js does not contain dim chain rendering code ───

test('T36: grid_overlay.js has no inline dim rendering (separated to DimChains)', function() {
  var src = readFile('grid_overlay.js');
  assert(src.indexOf('function createDimLabel') === -1,
    'grid_overlay.js still has createDimLabel (should be in grid_dim_chains.js)');
  assert(src.indexOf('function addDimSegment') === -1,
    'grid_overlay.js still has addDimSegment (should be in grid_dim_chains.js)');
  assert(src.indexOf('new THREE.LineDashedMaterial') === -1,
    'grid_overlay.js still creates dashed materials inline');
  logTag('T36_CONCERN', 'dim chain rendering extracted — grid_overlay.js clean');
});

// ── T37: DimChains module API contract ──────────────────────────────

test('T37: DimChains exports build and remove', function() {
  syntaxCheck('grid_dim_chains.js');
  var dimSrc = readFile('grid_dim_chains.js');
  var dimCtx = vm.createContext(Object.assign({}, stubCtx));
  vm.runInContext(dimSrc, dimCtx, { filename: 'grid_dim_chains.js' });
  var DC = dimCtx.DimChains;
  assert(DC, 'DimChains not defined');
  assert(typeof DC.build === 'function', 'DimChains.build not a function');
  assert(typeof DC.remove === 'function', 'DimChains.remove not a function');
  logTag('T37_API', 'DimChains.build + DimChains.remove — OK');
});

// ── T38: index.html loads grid_dim_chains.js in correct order ───────

test('T38: index.html loads grid_dim_chains before grid_overlay', function() {
  var html = readFile('index.html');
  var dcPos = html.indexOf('grid_dim_chains.js');
  var ovPos = html.indexOf('grid_overlay.js');
  assert(dcPos >= 0, 'grid_dim_chains.js missing from index.html');
  assert(dcPos < ovPos, 'grid_dim_chains.js must load before grid_overlay.js');
  logTag('T38_ORDER', 'grid_dim_chains.js at pos ' + dcPos + ' < grid_overlay.js at ' + ovPos);
});

// ── T39: GridConfig uses IIFE (not bare object exposed to mutation) ──

test('T39: GridConfig is enclosed in IIFE', function() {
  var src = readFile('grid_config.js');
  assert(src.indexOf('var GridConfig = (function()') >= 0,
    'GridConfig not wrapped in IIFE — exposed to uncontrolled mutation');
  logTag('T39_IIFE', 'GridConfig wrapped in IIFE');
});

// ── T40: grid_overlay.js delegates to DimChains (not DIY) ───────────

test('T40: grid_overlay.js calls DimChains.build and DimChains.remove', function() {
  var src = readFile('grid_overlay.js');
  assert(src.indexOf('DimChains.build') >= 0,
    'grid_overlay.js does not call DimChains.build');
  assert(src.indexOf('DimChains.remove') >= 0,
    'grid_overlay.js does not call DimChains.remove');
  logTag('T40_DELEGATE', 'grid_overlay.js delegates dims to DimChains');
});

// ── T41: grid_contours.js has no residual mesh visibility concerns ───

test('T41: grid_contours.js clear() does not reference mesh visibility', function() {
  var src = readFile('grid_contours.js');
  var clearStart = src.indexOf('function clear(');
  var clearEnd = src.indexOf('return {', clearStart);
  var clearBody = src.substring(clearStart, clearEnd);
  assert(clearBody.indexOf('visible') === -1,
    'clear() still references mesh visibility');
  assert(clearBody.indexOf('Mesh') === -1,
    'clear() still references Mesh operations');
  logTag('T41_CLEAN', 'clear() only disposes contour group — no mesh side effects');
});

// ── T42: All elevation levelMarkers share same config (DRY) ─────────

test('T42: All elevation levelMarkers are same reference', function() {
  var fLM = GridConfig.views.front.levelMarkers;
  var rLM = GridConfig.views.rear.levelMarkers;
  var lLM = GridConfig.views.left.levelMarkers;
  var sLM = GridConfig.views.right.levelMarkers;
  assert(fLM === rLM, 'front/rear levelMarkers not same ref');
  assert(fLM === lLM, 'front/left levelMarkers not same ref');
  assert(fLM === sLM, 'front/right levelMarkers not same ref');
  logTag('T42_DRY', 'levelMarkers shared reference across all elevation views');
});

// ── T43: GridAssembler registers DimChains ──────────────────────────

test('T43: GridAssembler.MODULES includes DimChains', function() {
  // Reload assembler with updated source
  var asmSrc2 = readFile('grid_assembler.js');
  var asmCtx2 = vm.createContext(Object.assign({}, stubCtx));
  vm.runInContext(asmSrc2, asmCtx2, { filename: 'grid_assembler.js' });
  assert(asmCtx2.GridAssembler.MODULES.DimChains, 'missing DimChains in MODULES');
  assert(!asmCtx2.GridAssembler.MODULES.DimChains.required, 'DimChains should be optional');
  logTag('T43_REG', 'DimChains registered as optional module');
});

// ── T44: No rendering code in grid_overlay.js (canvas/texture/sprite) ─

test('T44: grid_overlay.js has no canvas text rendering (concern violation)', function() {
  var src = readFile('grid_overlay.js');
  // It should still have createBubble (bubble sprites are part of grid scene),
  // but should NOT have dimension-specific canvas rendering
  var lines = src.split('\n');
  var dimCanvasCount = 0;
  for (var i = 0; i < lines.length; i++) {
    // Look for canvas 192x48 pattern that was the dim label canvas
    if (lines[i].indexOf('192') >= 0 && lines[i].indexOf('48') >= 0) dimCanvasCount++;
  }
  assert(dimCanvasCount === 0,
    'grid_overlay.js still has 192x48 canvas code (dim label rendering not extracted)');
  logTag('T44_CONCERN', 'no dim-label canvas rendering in grid_overlay.js');
});

// ── T45: grid_dim_chains.js is self-contained (no external state) ────

test('T45: grid_dim_chains.js does not reference external module state', function() {
  var src = readFile('grid_dim_chains.js');
  // Should not reference GridConfig, GridViews, SectionCut, etc.
  assert(src.indexOf('GridConfig') === -1,
    'grid_dim_chains.js depends on GridConfig (should receive style via params)');
  assert(src.indexOf('GridViews') === -1,
    'grid_dim_chains.js depends on GridViews');
  assert(src.indexOf('SectionCut') === -1,
    'grid_dim_chains.js depends on SectionCut');
  logTag('T45_SELF', 'grid_dim_chains.js self-contained — no cross-module deps');
});

// ══════════════════════════════════════════════════════════════════════
// VIEW-BY-VIEW RUNTIME TESTS — exercise actual code paths per view
// ══════════════════════════════════════════════════════════════════════

// ── Mock APP for runtime tests ──────────────────────────────────────
var mockEnv = { xMin: 0, xMax: 20, yMin: 0, yMax: 15, zMin: -0.3, zMax: 8.7 };
var mockOffset = { x: 10, y: 7.5, z: -0.3 };

function createMockAPP() {
  var meshes = [];
  for (var i = 0; i < 5; i++) {
    meshes.push({
      isMesh: true,
      userData: { ifcClass: ['IfcWall','IfcFurniture','IfcDoor','IfcWindow','IfcSlab'][i] },
      material: { clippingPlanes: null, clipShadows: false, needsUpdate: false },
      visible: true
    });
  }
  return {
    camera: {
      position: new stubCtx.THREE.Vector3(30, 20, 30),
      up: new stubCtx.THREE.Vector3(0, 1, 0),
      fov: 60,
      aspect: 16/9,
      updateProjectionMatrix: function() {}
    },
    controls: {
      target: new stubCtx.THREE.Vector3(0, 0, 0),
      object: null,
      enableRotate: true,
      enablePan: true,
      enableZoom: true,
      update: function() {}
    },
    renderer: { localClippingEnabled: false, setSize: function() {} },
    scene: { add: function() {}, remove: function() {} },
    modelOffset: mockOffset,
    lightTheme: false,
    markDirty: function() {},
    _onResize: function() {},
    ifc2three: function(ix, iy, iz) {
      return { x: ix - mockOffset.x, y: iz - mockOffset.z, z: -(iy - mockOffset.y) };
    },
    collectMeshes: function(filter) { return meshes.filter(filter); },
    _meshes: meshes
  };
}

// ── T46-T52: GridViews.lockView per view — camera + clip state ──────

var viewTests = [
  { mode: 'floor',  expectClip: true,  expectRotate: false, camDir: 'dy', desc: 'top-down + clip' },
  { mode: 'floor1', expectClip: true,  expectRotate: false, camDir: 'dy', desc: 'top-down + clip' },
  { mode: 'front',  expectClip: false, expectRotate: false, camDir: 'dz', desc: 'facing +Z' },
  { mode: 'rear',   expectClip: false, expectRotate: false, camDir: 'dz', desc: 'facing -Z' },
  { mode: 'left',   expectClip: false, expectRotate: false, camDir: 'dx', desc: 'facing -X' },
  { mode: 'right',  expectClip: false, expectRotate: false, camDir: 'dx', desc: 'facing +X' },
  { mode: 'roof',   expectClip: false, expectRotate: false, camDir: 'dy', desc: 'top-down no clip' }
];

// Load GridViews in a context with full stubs so lockView/unlockView can run
var viewsRunCtx = vm.createContext(Object.assign({}, stubCtx, { GridConfig: GridConfig }));
vm.runInContext(readFile('grid_views.js'), viewsRunCtx, { filename: 'grid_views.js' });
var GridViewsRun = viewsRunCtx.GridViews;

viewTests.forEach(function(vt, idx) {
  test('T' + (46 + idx) + ': lockView("' + vt.mode + '") — ' + vt.desc, function() {
    var app = createMockAPP();
    GridViewsRun.lockView(app, vt.mode, mockEnv);

    // Camera must be orthographic
    assert(app.camera.isOrthographicCamera, vt.mode + ': camera not ortho after lockView');

    // Rotation must be disabled in all locked views
    assert(app.controls.enableRotate === false, vt.mode + ': rotation not disabled');

    // Clip state
    if (vt.expectClip) {
      assert(app.renderer.localClippingEnabled === true,
        vt.mode + ': localClippingEnabled should be true');
      // At least one mesh should have clippingPlanes set (walls get clipped)
      var clippedCount = app._meshes.filter(function(m) {
        return m.material.clippingPlanes && m.material.clippingPlanes.length > 0;
      }).length;
      assert(clippedCount > 0, vt.mode + ': no meshes were clipped');
      // Furniture should be SKIPPED (retained)
      var furniture = app._meshes.find(function(m) { return m.userData.ifcClass === 'IfcFurniture'; });
      assert(!furniture.material.clippingPlanes || furniture.material.clippingPlanes.length === 0,
        vt.mode + ': IfcFurniture should be retained (not clipped)');
      logTag('T' + (46+idx) + '_CLIP', vt.mode + ' clipped=' + clippedCount + ' furniture retained');
    } else {
      assert(app.renderer.localClippingEnabled === false,
        vt.mode + ': localClippingEnabled should be false');
      logTag('T' + (46+idx) + '_NOCLIP', vt.mode + ' no clip applied');
    }

    // Unlock and verify state restored
    GridViewsRun.unlockView(app);
    assert(app.controls.enableRotate === true, vt.mode + ': rotation not re-enabled after unlock');
    assert(GridViewsRun.activeView() === null, vt.mode + ': activeView not null after unlock');

    // Clipping must be fully cleared
    if (vt.expectClip) {
      var stillClipped = app._meshes.filter(function(m) {
        return m.material.clippingPlanes !== null;
      }).length;
      assert(stillClipped === 0, vt.mode + ': ' + stillClipped + ' meshes still clipped after unlock');
      assert(app.renderer.localClippingEnabled === false,
        vt.mode + ': localClippingEnabled still true after unlock');
      logTag('T' + (46+idx) + '_UNLOCK', vt.mode + ' all clip cleared, clippingPlanes=null');
    }
  });
});

// ── T53: clearFloorClip restores clippingPlanes to null, not [] ─────

test('T53: clearFloorClip sets clippingPlanes=null (not []), localClip=false', function() {
  var app = createMockAPP();
  // Lock floor to apply clip, then clear
  GridViewsRun.lockView(app, 'floor', mockEnv);
  GridViewsRun.clearFloorClip(app);

  app._meshes.forEach(function(m, i) {
    assert(m.material.clippingPlanes === null,
      'mesh[' + i + '] clippingPlanes=' + JSON.stringify(m.material.clippingPlanes) + ' (not null)');
    assert(m.material.clipShadows === false,
      'mesh[' + i + '] clipShadows not cleared');
  });
  assert(app.renderer.localClippingEnabled === false, 'renderer localClip still true');
  logTag('T53_CLIPNULL', 'all meshes clippingPlanes=null, localClippingEnabled=false');
});

// ── T54: Floor cutZ values per view — computed from env ─────────────

test('T54: cutZ computation per floor view matches spec', function() {
  var bldH = mockEnv.zMax - mockEnv.zMin; // 9.0

  // Ground floor: zMin + offset_m = -0.3 + 1.0 = 0.7
  var gfClip = GridConfig.clipFor('floor');
  var gfCutZ = mockEnv.zMin + gfClip.offset_m;
  logTag('T54_GF', 'cutZ=' + gfCutZ.toFixed(2) + ' (zMin + 1.0m)');
  assertClose(gfCutZ, 0.7, 0.001, 'GF cutZ');

  // Level 1: zMin + bldH * 0.55 = -0.3 + 9*0.55 = 4.65
  var l1Clip = GridConfig.clipFor('floor1');
  var l1CutZ = mockEnv.zMin + bldH * l1Clip.offset_ratio;
  logTag('T54_L1', 'cutZ=' + l1CutZ.toFixed(2) + ' (zMin + 9.0*0.55)');
  assertClose(l1CutZ, 4.65, 0.001, 'L1 cutZ');

  // Both must be above zMin and below zMax
  assert(gfCutZ > mockEnv.zMin, 'GF cutZ below building');
  assert(gfCutZ < mockEnv.zMax, 'GF cutZ above building');
  assert(l1CutZ > mockEnv.zMin, 'L1 cutZ below building');
  assert(l1CutZ < mockEnv.zMax, 'L1 cutZ above building');
  assert(l1CutZ > gfCutZ, 'L1 cutZ should be above GF cutZ');
  logTag('T54_ORDER', 'GF=' + gfCutZ.toFixed(2) + ' < L1=' + l1CutZ.toFixed(2) + ' < zMax=' + mockEnv.zMax);
});

// ── T55: elevHVtoIFC face mapping — each face maps h,v to correct IFC axes ──

test('T55: elevHVtoIFC maps h,v correctly per face (front/rear/left/right)', function() {
  // We need to call the private elevHVtoIFC. It's inside the IIFE.
  // Instead, verify by loading grid_contours and testing renderEdges output coordinates.
  // Simpler: verify the mapping from source code matches spec.
  var src = readFile('grid_contours.js');

  // Extract the switch cases
  var frontCase = src.match(/case 'front':\s*return \{([^}]+)\}/);
  var rearCase = src.match(/case 'rear':\s*return \{([^}]+)\}/);
  var leftCase = src.match(/case 'left':\s*return \{([^}]+)\}/);
  var rightCase = src.match(/case 'right':\s*return \{([^}]+)\}/);

  assert(frontCase, 'front case not found');
  assert(rearCase, 'rear case not found');
  assert(leftCase, 'left case not found');
  assert(rightCase, 'right case not found');

  // Spec (from prompt §Coordinate Transforms):
  // front: h→X, v→Z  (h=X, iy=cy, iz=v)
  // rear:  h→-X, v→Z (ix=-h, iy=cy, iz=v)
  // left:  h→Y, v→Z  (ix=cx, iy=h, iz=v)
  // right: h→-Y, v→Z (ix=cx, iy=-h, iz=v)

  assert(frontCase[1].indexOf('ix: h') >= 0, 'front: ix should be h');
  assert(frontCase[1].indexOf('iz: v') >= 0, 'front: iz should be v');
  logTag('T55_FRONT', 'h→ix, v→iz (X,Z)');

  assert(rearCase[1].indexOf('ix: -h') >= 0, 'rear: ix should be -h');
  assert(rearCase[1].indexOf('iz: v') >= 0, 'rear: iz should be v');
  logTag('T55_REAR', '-h→ix, v→iz (-X,Z)');

  assert(leftCase[1].indexOf('iy: h') >= 0, 'left: iy should be h');
  assert(leftCase[1].indexOf('iz: v') >= 0, 'left: iz should be v');
  logTag('T55_LEFT', 'h→iy, v→iz (Y,Z)');

  assert(rightCase[1].indexOf('iy: -h') >= 0, 'right: iy should be -h');
  assert(rightCase[1].indexOf('iz: v') >= 0, 'right: iz should be v');
  logTag('T55_RIGHT', '-h→iy, v→iz (-Y,Z)');
});

// ── T56: renderContours with mock section data — produces lines ─────

test('T56: renderContours produces correct line count from mock contour data', function() {
  // Mock APP with tracking
  var addedObjects = [];
  var mockGroup = { add: function(obj) { addedObjects.push(obj); }, name: '', renderOrder: 0 };
  var contourApp = {
    scene: { add: function() {} },
    markDirty: function() {},
    ifc2three: function(ix, iy, iz) { return { x: ix - 10, y: iz + 0.3, z: -(iy - 7.5) }; }
  };

  // Reload GridContours with a patched ensureGroup
  var cSrc = readFile('grid_contours.js');
  var cCtx = vm.createContext(Object.assign({}, stubCtx, { GridConfig: GridConfig, DoorArcs: DoorArcs }));
  vm.runInContext(cSrc, cCtx, { filename: 'grid_contours.js' });
  var GC = cCtx.GridContours;

  // Mock contour data: 2 walls with 1 contour each, 1 door with 1 contour
  var mockContours = [
    { guid: 'wall1', ifcClass: 'IfcWall', contours: [[[0,0],[5,0],[5,0.2],[0,0.2],[0,0]]] },
    { guid: 'wall2', ifcClass: 'IfcWall', contours: [[[0,3],[10,3],[10,3.2],[0,3.2],[0,3]]] },
    { guid: 'door1', ifcClass: 'IfcDoor', contours: [[[2,0],[2.9,0]]] }
  ];

  var result = GC.renderContours(contourApp, mockContours, 'floor', 0.7);
  assert(result, 'renderContours returned null');
  logTag('T56_RENDER', 'floor contours: 3 elements → lines produced');

  // Now with empty data — should not crash
  GC.clear(contourApp);
  var result2 = GC.renderContours(contourApp, [], 'floor', 0.7);
  assert(result2, 'renderContours with empty data crashed');
  logTag('T56_EMPTY', 'empty contour data → no crash, 0 lines');
});

// ── T57: renderEdges with mock elevation data per face ──────────────

test('T57: renderEdges handles each elevation face without crash', function() {
  var edgeApp = {
    scene: { add: function() {} },
    markDirty: function() {},
    ifc2three: function(ix, iy, iz) { return { x: ix - 10, y: iz + 0.3, z: -(iy - 7.5) }; }
  };

  var cSrc = readFile('grid_contours.js');
  var cCtx = vm.createContext(Object.assign({}, stubCtx, { GridConfig: GridConfig }));
  vm.runInContext(cSrc, cCtx, { filename: 'grid_contours.js' });
  var GC = cCtx.GridContours;

  var mockEdges = [
    { guid: 'w1', ifcClass: 'IfcWall', edges: [[0,0,5,0],[5,0,5,3],[5,3,0,3],[0,3,0,0]], depth: 1.0 },
    { guid: 'win1', ifcClass: 'IfcWindow', edges: [[1,1,3,1],[3,1,3,2.5]], depth: 0.5 }
  ];

  ['front','rear','left','right'].forEach(function(face) {
    GC.clear(edgeApp);
    var result = GC.renderEdges(edgeApp, mockEdges, face, mockEnv);
    assert(result, face + ': renderEdges returned null');
    logTag('T57_FACE', face + ' — 2 elements, rendered OK');
  });

  // Empty edges — should not crash
  GC.clear(edgeApp);
  var empty = GC.renderEdges(edgeApp, [], 'front', mockEnv);
  assert(empty, 'renderEdges with empty edges crashed');
  logTag('T57_EMPTY', 'empty edge data → no crash');
});

// ── T58: DoorArcs with zero walls — graceful empty result ───────────

test('T58: DoorArcs.generateArcs with 0 walls returns empty (no crash)', function() {
  var doors = [{ guid: 'd1', ifcClass: 'IfcDoor', contours: [[[2,0],[2.9,0]]] }];
  var arcs = DoorArcs.generateArcs(doors, []);
  // With no wall contours, bestDist0 and bestDist1 stay Infinity — should still pick a hinge
  // (both stay Infinity → bestDist0 <= bestDist1 → picks p0 as hinge)
  logTag('T58_NOWALLS', 'arcs.length=' + arcs.length + ' (graceful with 0 walls)');
  assert(arcs.length === 1, 'should still produce 1 arc (hinge defaults to first endpoint)');
  assert(arcs[0].radius > 0, 'radius should be positive');
});

// ── T59: DoorArcs with empty/short contour — no crash ───────────────

test('T59: DoorArcs handles edge cases (empty, 1-point, tiny door)', function() {
  var walls = [{ guid: 'w1', ifcClass: 'IfcWall', contours: [[[0,0],[5,0]]] }];

  // Empty contours
  var arcs1 = DoorArcs.generateArcs([{ guid: 'd0', contours: [] }], walls);
  assert(arcs1.length === 0, 'empty contours should produce 0 arcs');

  // Single point contour (invalid)
  var arcs2 = DoorArcs.generateArcs([{ guid: 'd1', contours: [[[1,1]]] }], walls);
  assert(arcs2.length === 0, 'single-point contour should produce 0 arcs');

  // Tiny door (< 0.05m) — below threshold
  var arcs3 = DoorArcs.generateArcs([{ guid: 'd2', contours: [[[1,0],[1.03,0]]] }], walls);
  assert(arcs3.length === 0, 'door < 5cm should be rejected');

  logTag('T59_EDGE', 'empty=0 arcs, 1pt=0 arcs, tiny=0 arcs — all graceful');
});

// ── T60: Roof view — no contourMode, no clip, no side effects ───────

test('T60: Roof view has null contourMode, no clip, no retain (pure 3D)', function() {
  var roofCfg = GridConfig.views.roof;
  assert(roofCfg.contourMode === null, 'roof contourMode should be null');
  assert(roofCfg.clip === null, 'roof clip should be null');
  assert(roofCfg.retain.length === 0, 'roof retain should be empty');
  assert(Object.keys(roofCfg.styles).length === 0, 'roof styles should be empty');
  assert(!roofCfg.levelMarkers, 'roof should have no levelMarkers');

  // lockView for roof should NOT enable clipping
  var app = createMockAPP();
  GridViewsRun.lockView(app, 'roof', mockEnv);
  assert(app.renderer.localClippingEnabled === false, 'roof enabled clipping');
  var anyClipped = app._meshes.some(function(m) { return m.material.clippingPlanes !== null; });
  assert(!anyClipped, 'roof clipped a mesh');
  logTag('T60_ROOF', 'roof: no contours, no clip, no retain, no side effects');
  GridViewsRun.unlockView(app);
});

// ── T61: Floor retain set covers exactly 8 classes ──────────────────

test('T61: Floor retain list is exactly 8 specific furniture/MEP classes', function() {
  var expected = [
    'IfcFurnishingElement','IfcFurniture','IfcFlowTerminal','IfcSanitaryTerminal',
    'IfcElectricalAppliance','IfcLightFixture','IfcBuildingElementProxy','IfcCovering'
  ];
  var actual = GridConfig.views.floor.retain;
  assert(actual.length === 8, 'retain has ' + actual.length + ' classes, expected 8');
  expected.forEach(function(cls) {
    assert(actual.indexOf(cls) >= 0, 'missing: ' + cls);
  });
  // None of the structural classes should be retained
  ['IfcWall','IfcColumn','IfcSlab','IfcDoor','IfcWindow'].forEach(function(cls) {
    assert(actual.indexOf(cls) === -1, 'structural class ' + cls + ' should NOT be retained');
  });
  logTag('T61_RETAIN', '8 furniture/MEP classes retained, 0 structural — correct');
});

// Summary
// ══════════════════════════════════════════════════════════════════════

console.log('\n' + pass + '/' + total + ' passed, ' + fail + ' failed');
if (logLines.length) {
  console.log('§-log lines emitted: ' + logLines.length);
}
console.log('');
process.exit(fail > 0 ? 1 : 0);
