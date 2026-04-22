#!/usr/bin/env node
// BIM OOTB — Full test suite
// Run: node deploy/sandbox/test_all.js
// Checks: syntax, wiring, z-index, OCI live, walk math

'use strict';
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const DIR = path.join(__dirname);
let pass = 0, fail = 0;

function ok(label, cond, detail) {
  if (cond) { pass++; console.log(`  ✓ ${label}`); }
  else { fail++; console.log(`  ✗ ${label}  ${detail || ''}`); }
}

// ═══ 1. Syntax ═══
console.log('\n═══ 1. JS Syntax ═══');
const jsFiles = fs.readdirSync(DIR).filter(f => f.endsWith('.js') && !f.startsWith('test_'));
for (const f of jsFiles) {
  try { execSync(`node --check "${path.join(DIR, f)}"`, { stdio: 'pipe' }); ok(f, true); }
  catch(e) { ok(f, false, e.stderr?.toString().trim()); }
}

// ═══ 2. Script tags match files ═══
console.log('\n═══ 2. Script Tags → Files ═══');
const html = fs.readFileSync(path.join(DIR, 'index.html'), 'utf8');
const scriptTags = (html.match(/<script src="([^"]+)"/g) || [])
  .map(s => s.match(/src="([^"]+)"/)[1].replace(/\?.*/, ''));
for (const src of scriptTags) {
  ok(src, fs.existsSync(path.join(DIR, src)));
}

// ═══ 3. Module wiring ═══
console.log('\n═══ 3. Module Wiring ═══');
const mainJs = fs.readFileSync(path.join(DIR, 'main.js'), 'utf8');
const setupCalls = (mainJs.match(/setup\w+\(APP\)/g) || []).map(s => s.replace('(APP)', ''));
for (const setup of setupCalls) {
  let found = false;
  for (const f of jsFiles) {
    const src = fs.readFileSync(path.join(DIR, f), 'utf8');
    if (src.includes(`function ${setup}(`)) { ok(`${setup} → ${f}`, true); found = true; break; }
  }
  if (!found) ok(setup, false, 'NOT FOUND in any JS');
}

// ═══ 4. Window exports vs onclick ═══
console.log('\n═══ 4. onclick → window exports ═══');
const onclickFns = [...new Set((html.match(/onclick="(\w+)\(/g) || []).map(s => s.match(/onclick="(\w+)/)[1]))];
for (const fn of onclickFns) {
  if (fn === 'document' || fn === 'event') continue;
  ok(fn, mainJs.includes(`window.${fn}`), 'not in window exports');
}

// ═══ 5. Z-index overlap audit ═══
console.log('\n═══ 5. Z-Index Overlap Audit ═══');
const zMap = {};
const zRegex = /([#.\w\-\[\]= ]+)\s*\{[^}]*z-index\s*:\s*(\d+)/g;
let m;
while ((m = zRegex.exec(html)) !== null) {
  const selector = m[1].trim().substring(0, 30);
  const z = parseInt(m[2]);
  if (!zMap[z]) zMap[z] = [];
  zMap[z].push(selector);
}
// Panels that MUST NOT share z-index with toolbar buttons
const panelSelectors = ['issues-panel', 'walk-anchor-prompt'];
const toolbarZ = zMap[20] || []; // toolbar is typically z=20
for (const panel of panelSelectors) {
  const panelZ = Object.entries(zMap).find(([z, sels]) => sels.some(s => s.includes(panel)));
  if (panelZ) {
    const z = parseInt(panelZ[0]);
    const sharedWithToolbar = zMap[z]?.some(s => !s.includes(panel) && !s.includes('prompt'));
    ok(`#${panel} (z=${z}) above toolbar (z=20)`, z > 20, `z=${z} overlaps toolbar`);
  }
}
// Report all overlaps
const overlaps = Object.entries(zMap).filter(([z, sels]) => sels.length > 1 && parseInt(z) >= 15);
for (const [z, sels] of overlaps) {
  console.log(`  ⚠ z=${z}: ${sels.join(' | ')}`);
}

// ═══ 6. No stale references ═══
console.log('\n═══ 6. No Stale References ═══');
const appFiles = jsFiles.filter(f => !f.startsWith('test_') && !f.startsWith('walk_math'));
const allSrc = appFiles.map(f => fs.readFileSync(path.join(DIR, f), 'utf8')).join('\n') + html;
ok('no index2.html references', !allSrc.includes('index2.html'));
ok('no landing2.html references', !allSrc.includes('landing2.html'));
const monolith = 'rtree_browser' + '_demo';  // split to avoid self-match
ok('no monolith references', !allSrc.includes(monolith));

// ═══ 7. Walk math ═══
console.log('\n═══ 7. Walk Math (summary) ═══');
try {
  const out = execSync(`node "${path.join(DIR, 'walk_math_test.js')}"`, { stdio: 'pipe' }).toString();
  const passMatch = out.match(/(\d+)\/(\d+) passed/);
  if (passMatch) {
    ok(`walk math ${passMatch[1]}/${passMatch[2]}`, passMatch[1] === passMatch[2]);
  }
} catch(e) { ok('walk math', false, 'test threw error'); }

// ═══ 8. OCI Live ═══
console.log('\n═══ 8. OCI Live ═══');
const BASE = 'https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb/o';
const deployedFiles = jsFiles.filter(f => !f.startsWith('test_') && !f.startsWith('walk_math'));
const checkFiles = ['index.html', ...deployedFiles];
for (const f of checkFiles) {
  try {
    const out = execSync(`curl -sI "${BASE}/${f}" -o /dev/null -w "%{http_code}"`, { stdio: 'pipe', timeout: 10000 }).toString().trim();
    ok(`${f} → ${out}`, out === '200');
  } catch(e) { ok(f, false, 'curl failed'); }
}

// ═══ SUMMARY ═══
console.log(`\n═══ SUMMARY: ${pass}/${pass + fail} passed, ${fail} failed ═══\n`);
process.exit(fail > 0 ? 1 : 0);
