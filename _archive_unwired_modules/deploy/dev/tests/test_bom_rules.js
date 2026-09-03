/**
 * test_bom_rules.js — §S272 BOM Engine Phase 4: DiscRuleProvider tests
 * Tests: loadFromJSON, loadRules filtering, checkPlacement for each check_method
 */
var BomRules = require('../bom_engine/bom_rules.js');
var fs = require('fs');
var path = require('path');

var passed = 0, failed = 0;

function assert(cond, msg) {
  if (cond) { passed++; }
  else { failed++; console.log('  FAIL: ' + msg); }
}

// ── Load disc_rules.json ──────────────────────────────────────────────────

var jsonPath = path.join(__dirname, '..', 'rules', 'disc_rules.json');
var rawJson = JSON.parse(fs.readFileSync(jsonPath, 'utf8'));
var allRules = BomRules.loadFromJSON(rawJson);

console.log('§RULES loadFromJSON');
assert(allRules.length === 8, 'should load 8 seed rules, got ' + allRules.length);
assert(allRules[0].name === 'SPACE_MIN_AREA', 'first rule is SPACE_MIN_AREA');
assert(allRules[0].severity === 'BLOCK', 'SPACE_MIN_AREA severity=BLOCK');

// ── loadFromJSON edge cases ───────────────────────────────────────────────

console.log('§RULES loadFromJSON edge');
assert(BomRules.loadFromJSON(null).length === 0, 'null → empty');
assert(BomRules.loadFromJSON({}).length === 0, 'no rules key → empty');
assert(BomRules.loadFromJSON({rules: 'bad'}).length === 0, 'non-array → empty');

// ── loadRules filtering ───────────────────────────────────────────────────

console.log('§RULES loadRules filter');
// adOrgId=null → only org=0 (global) rules match (org filter: orgOk requires org=0 or matching id)
var globalRules = BomRules.loadRules(allRules, null, null);
var org0Count = allRules.filter(function(r) { return r.ad_org_id === 0; }).length;
assert(globalRules.length === org0Count, 'no org filter → only global (org=0) rules, got ' + globalRules.length);

var org0Only = BomRules.loadRules(allRules, 0, null);
assert(org0Only.length === org0Count, 'org=0 includes only org-0 rules, got ' + org0Only.length);

var org3Only = BomRules.loadRules(allRules, 3, null);
// org3 matches org=3 rules + org=0 (global) rules
assert(org3Only.length === allRules.length, 'org=3 includes global + org-3, got ' + org3Only.length);

var myOnly = BomRules.loadRules(allRules, null, 'MY');
var myCount = allRules.filter(function(r) { return r.ad_org_id === 0 && (!r.jurisdiction || r.jurisdiction === 'MY'); }).length;
assert(myOnly.length === myCount, 'MY jurisdiction filter, got ' + myOnly.length);

var intlOnly = BomRules.loadRules(allRules, null, 'INTL');
var intlCount = allRules.filter(function(r) { return r.ad_org_id === 0 && (!r.jurisdiction || r.jurisdiction === 'INTL'); }).length;
assert(intlOnly.length === intlCount, 'INTL jurisdiction filter, got ' + intlOnly.length);

// ── checkPlacement: MIN_AREA ──────────────────────────────────────────────

console.log('§RULES checkPlacement MIN_AREA');
var areaRules = BomRules.loadRules(allRules, 0, 'MY').filter(function(r) {
  return r.check_method === 'MIN_AREA';
});

// PASS: 4m x 3m = 12m² > 9.3m²
var res1 = BomRules.checkPlacement(
  { bomType: 'ROOM', fillAxis: 'x' },
  { x: 0, y: 0, z: 0, w: 4000, d: 3000, h: 2800 },
  [], areaRules
);
assert(res1.ok === true, 'ROOM 4x3m passes MIN_AREA');

// FAIL: 2m x 3m = 6m² < 9.3m²
var res2 = BomRules.checkPlacement(
  { bomType: 'ROOM', fillAxis: 'x' },
  { x: 0, y: 0, z: 0, w: 2000, d: 3000, h: 2800 },
  [], areaRules
);
assert(res2.ok === false, 'ROOM 2x3m fails MIN_AREA');
assert(res2.violations[0].rule === 'SPACE_MIN_AREA', 'violation rule name');
assert(res2.violations[0].severity === 'BLOCK', 'violation severity');

// Condition filter: non-ROOM/SET should pass
var res3 = BomRules.checkPlacement(
  { bomType: 'CORRIDOR', fillAxis: 'x' },
  { x: 0, y: 0, z: 0, w: 2000, d: 3000, h: 2800 },
  [], areaRules
);
assert(res3.ok === true, 'CORRIDOR skips MIN_AREA (condition mismatch)');

// ── checkPlacement: MIN_DIMENSION ─────────────────────────────────────────

console.log('§RULES checkPlacement MIN_DIMENSION');
var corridorRules = BomRules.loadRules(allRules, 0, 'MY').filter(function(r) {
  return r.check_method === 'MIN_DIMENSION' && r.condition === "bomType = 'CORRIDOR'";
});

// PASS: width 1500mm > 1200mm
var res4 = BomRules.checkPlacement(
  { bomType: 'CORRIDOR', fillAxis: 'x' },
  { x: 0, y: 0, z: 0, w: 1500, d: 2000, h: 2800 },
  [], corridorRules
);
assert(res4.ok === true, 'corridor 1500mm passes MIN_DIMENSION');

// FAIL: width 1000mm < 1200mm
var res5 = BomRules.checkPlacement(
  { bomType: 'CORRIDOR', fillAxis: 'x' },
  { x: 0, y: 0, z: 0, w: 1000, d: 2000, h: 2800 },
  [], corridorRules
);
assert(res5.ok === false, 'corridor 1000mm fails MIN_DIMENSION');
assert(res5.violations[0].ref === 'UBBL s.165(1)', 'violation ref correct');

// ── checkPlacement: DIMENSION_RANGE ───────────────────────────────────────

console.log('§RULES checkPlacement DIMENSION_RANGE');
var rangeRules = allRules.filter(function(r) { return r.check_method === 'DIMENSION_RANGE'; });

// PASS: h=2800 within [2600,6000]
var res6 = BomRules.checkPlacement(
  { bomType: 'ROOM', fillAxis: 'x' },
  { x: 0, y: 0, z: 0, w: 4000, d: 3000, h: 2800 },
  [], rangeRules
);
assert(res6.ok === true, 'h=2800 passes DIMENSION_RANGE');

// FAIL: h=2400 < 2600
var res7 = BomRules.checkPlacement(
  { bomType: 'ROOM', fillAxis: 'x' },
  { x: 0, y: 0, z: 0, w: 4000, d: 3000, h: 2400 },
  [], rangeRules
);
assert(res7.ok === false, 'h=2400 fails DIMENSION_RANGE (below min)');

// FAIL: h=7000 > 6000
var res8 = BomRules.checkPlacement(
  { bomType: 'ROOM', fillAxis: 'x' },
  { x: 0, y: 0, z: 0, w: 4000, d: 3000, h: 7000 },
  [], rangeRules
);
assert(res8.ok === false, 'h=7000 fails DIMENSION_RANGE (above max)');

// ── checkPlacement: MAX_DISTANCE (spacing) ────────────────────────────────

console.log('§RULES checkPlacement MAX_DISTANCE');
var spacingRules = allRules.filter(function(r) { return r.check_method === 'MAX_DISTANCE'; });

// PASS: 3 siblings spaced 2000mm apart (< 4600mm)
var siblings1 = [
  { x: 0, y: 0, z: 0, w: 200, d: 200, h: 200 },
  { x: 2200, y: 0, z: 0, w: 200, d: 200, h: 200 },
  { x: 4400, y: 0, z: 0, w: 200, d: 200, h: 200 }
];
var res9 = BomRules.checkPlacement(
  { bomType: 'FP', fillAxis: 'x' },
  { x: 0, y: 0, z: 0, w: 10000, d: 10000, h: 3000 },
  siblings1, spacingRules
);
assert(res9.ok === true, 'sprinklers 2000mm apart passes MAX_DISTANCE');

// FAIL: 2 siblings spaced 5000mm apart (> 4600mm)
var siblings2 = [
  { x: 0, y: 0, z: 0, w: 200, d: 200, h: 200 },
  { x: 5200, y: 0, z: 0, w: 200, d: 200, h: 200 }
];
var res10 = BomRules.checkPlacement(
  { bomType: 'FP', fillAxis: 'x' },
  { x: 0, y: 0, z: 0, w: 10000, d: 10000, h: 3000 },
  siblings2, spacingRules
);
assert(res10.ok === false, 'sprinklers 5000mm apart fails MAX_DISTANCE');

// ── checkPlacement: MIN_DISTANCE (clearance) ──────────────────────────────

console.log('§RULES checkPlacement MIN_DISTANCE');
var clearanceRules = allRules.filter(function(r) { return r.check_method === 'MIN_DISTANCE'; });

// PASS: clearance 2000mm > 1800mm
var siblings3 = [
  { x: 0, y: 0, z: 0, w: 200, d: 200, h: 200 },
  { x: 2200, y: 0, z: 0, w: 200, d: 200, h: 200 }
];
var res11 = BomRules.checkPlacement(
  { bomType: 'FP', fillAxis: 'x' },
  { x: 0, y: 0, z: 0, w: 10000, d: 10000, h: 3000 },
  siblings3, clearanceRules
);
assert(res11.ok === true, 'clearance 2000mm passes MIN_DISTANCE');

// FAIL: clearance 500mm < 1800mm
var siblings4 = [
  { x: 0, y: 0, z: 0, w: 200, d: 200, h: 200 },
  { x: 700, y: 0, z: 0, w: 200, d: 200, h: 200 }
];
var res12 = BomRules.checkPlacement(
  { bomType: 'FP', fillAxis: 'x' },
  { x: 0, y: 0, z: 0, w: 10000, d: 10000, h: 3000 },
  siblings4, clearanceRules
);
assert(res12.ok === false, 'clearance 500mm fails MIN_DISTANCE');

// ── checkPlacement: MAX_COVERAGE ──────────────────────────────────────────

console.log('§RULES checkPlacement MAX_COVERAGE');
var coverageRules = allRules.filter(function(r) { return r.check_method === 'MAX_COVERAGE'; });

// PASS: 100m² / 5 sprinklers = 20m² < 21m²
var siblings5 = [{},{},{},{},{}]; // 5 elements
var res13 = BomRules.checkPlacement(
  { bomType: 'FP', fillAxis: 'x' },
  { x: 0, y: 0, z: 0, w: 10000, d: 10000, h: 3000 },
  siblings5, coverageRules
);
assert(res13.ok === true, '20m²/element passes MAX_COVERAGE');

// FAIL: 100m² / 4 sprinklers = 25m² > 21m²
var siblings6 = [{},{},{},{}]; // 4 elements
var res14 = BomRules.checkPlacement(
  { bomType: 'FP', fillAxis: 'x' },
  { x: 0, y: 0, z: 0, w: 10000, d: 10000, h: 3000 },
  siblings6, coverageRules
);
assert(res14.ok === false, '25m²/element fails MAX_COVERAGE');

// ── Mixed rules — multiple violations ─────────────────────────────────────

console.log('§RULES mixed violations');
var mixedRules = BomRules.loadRules(allRules, 0, 'MY');
// Small room: fails both MIN_AREA and DIMENSION_RANGE(h)
var resMixed = BomRules.checkPlacement(
  { bomType: 'ROOM', fillAxis: 'x' },
  { x: 0, y: 0, z: 0, w: 2000, d: 2000, h: 2400 },
  [], mixedRules
);
assert(resMixed.ok === false, 'small room with low ceiling fails');
assert(resMixed.violations.length >= 2, 'at least 2 violations (area + height), got ' + resMixed.violations.length);

// ── Single sibling — spacing rules should pass ────────────────────────────

console.log('§RULES single sibling');
var res15 = BomRules.checkPlacement(
  { bomType: 'FP', fillAxis: 'x' },
  { x: 0, y: 0, z: 0, w: 10000, d: 10000, h: 3000 },
  [{ x: 0, y: 0, z: 0, w: 200, d: 200, h: 200 }],
  allRules
);
// Single sibling can't violate spacing rules (need >=2)
var spacingViols = res15.violations.filter(function(v) {
  return v.rule === 'FP_SPRINKLER_SPACING' || v.rule === 'FP_SPRINKLER_CLEARANCE';
});
assert(spacingViols.length === 0, 'single sibling has no spacing violations');

// ── Summary ───────────────────────────────────────────────────────────────

console.log('');
console.log('§RULES_SUMMARY ' + passed + ' passed, ' + failed + ' failed, ' + (passed + failed) + ' total');
if (failed) process.exit(1);
