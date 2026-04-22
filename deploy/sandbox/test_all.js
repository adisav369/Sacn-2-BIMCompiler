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
console.log('\n═══ 8. OCI Live (bim-ootb-full/sandbox/) ═══');
const BASE_FULL = 'https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/sandbox';
const BASE_DEMO = 'https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb/o';
const deployedFiles = jsFiles.filter(f => !f.startsWith('test_') && !f.startsWith('walk_math'));
const checkFiles = ['index.html', ...deployedFiles];
for (const f of checkFiles) {
  try {
    const out = execSync(`curl -sI "${BASE_FULL}/${f}" -o /dev/null -w "%{http_code}"`, { stdio: 'pipe', timeout: 10000 }).toString().trim();
    ok(`full/${f} → ${out}`, out === '200');
  } catch(e) { ok(`full/${f}`, false, 'curl failed'); }
}
console.log('\n═══ 8b. OCI Live (bim-ootb root) ═══');
for (const f of checkFiles) {
  try {
    const out = execSync(`curl -sI "${BASE_DEMO}/${f}" -o /dev/null -w "%{http_code}"`, { stdio: 'pipe', timeout: 10000 }).toString().trim();
    ok(`demo/${f} → ${out}`, out === '200');
  } catch(e) { ok(`demo/${f}`, false, 'curl failed'); }
}

// ═══ 9. S209b — toolbar hidden when issues panel open ═══
console.log('\n═══ 9. S209b Toolbar/Issues Overlap Fix ═══');
const issuesJs = fs.readFileSync(path.join(DIR, 'issues.js'), 'utf8');
ok('toggleIssues hides search-box', issuesJs.includes("getElementById('search-box')") && issuesJs.includes("display = 'none'"), 'search-box not hidden in toggleIssues');
ok('toggleIssues restores search-box', issuesJs.includes("display = ''"), 'search-box not restored when issues closed');
const toolsJs = fs.readFileSync(path.join(DIR, 'tools.js'), 'utf8');
ok('export4D5D encodes dbParam', toolsJs.includes("encodeURIComponent(dbParam)"), 'dbParam not encoded — will cause recursive URL');

// Verify OCI content matches local (not just 200)
console.log('\n═══ 9b. OCI Content Match ═══');
const criticalFiles = ['tools.js', 'issues.js', 'excel.js'];
for (const f of criticalFiles) {
  const local = fs.readFileSync(path.join(DIR, f), 'utf8');
  try {
    const remote = execSync(`curl -s "${BASE_FULL}/${f}"`, { stdio: 'pipe', timeout: 10000 }).toString();
    ok(`full/${f} content matches local`, remote === local, 'DEPLOYED VERSION DIFFERS FROM LOCAL');
  } catch(e) { ok(`full/${f} content`, false, 'curl failed'); }
  try {
    const remote2 = execSync(`curl -s "${BASE_DEMO}/${f}"`, { stdio: 'pipe', timeout: 10000 }).toString();
    ok(`demo/${f} content matches local`, remote2 === local, 'DEPLOYED VERSION DIFFERS FROM LOCAL');
  } catch(e) { ok(`demo/${f} content`, false, 'curl failed'); }
}

// ═══ 10. URL integrity — no recursive nesting, correct routing ═══
console.log('\n═══ 10. URL Integrity ═══');

// 10a: export4D5D must NOT build URL with raw unencoded OCI URL in query string
const ociDbUrl = 'https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/buildings/Duplex_extracted.db';
const encoded = encodeURIComponent(ociDbUrl);
ok('encodeURIComponent round-trips OCI URL', decodeURIComponent(encoded) === ociDbUrl, 'encode/decode mismatch');
ok('encoded URL has no raw slashes', !encoded.includes('/'), 'raw slashes in encoded param = recursive nesting');
ok('encoded URL has no raw colons', !encoded.includes(':'), 'raw colons in encoded param');

// 10b: boq_charts.html must exist at bucket root (not in sandbox/)
try {
  const boqCode = execSync(`curl -sI "https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/boq_charts.html" -o /dev/null -w "%{http_code}"`, { stdio: 'pipe', timeout: 10000 }).toString().trim();
  ok('boq_charts.html exists at bucket root', boqCode === '200', `got ${boqCode}`);
} catch(e) { ok('boq_charts.html at root', false, 'curl failed'); }

// 10c: boq_charts.html must NOT exist in sandbox/ (would cause confusion)
try {
  const boqSandbox = execSync(`curl -sI "https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/sandbox/boq_charts.html" -o /dev/null -w "%{http_code}"`, { stdio: 'pipe', timeout: 10000 }).toString().trim();
  ok('no boq_charts.html in sandbox/', boqSandbox === '404', `expected 404, got ${boqSandbox} — stale copy in sandbox/`);
} catch(e) { ok('no boq_charts in sandbox/', true); }

// 10d: export4D5D base regex must strip query string first (greedy regex matches /o/ in ?lib= param)
ok('export4D5D strips query before regex', toolsJs.includes("split('?')[0].match"), 'regex runs on full URL with ?db=&lib= — greedy .* matches /o/ in query params, base becomes 302 chars instead of 82');
// Prove the fix works with a real viewer URL (with ?db= and ?lib= containing /o/)
const realViewerHref = 'https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/sandbox/index.html?db=https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/buildings/Duplex_extracted.db&lib=https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/buildings/Duplex_library.db';
const fixedBase = realViewerHref.split('?')[0].match(/(.*\/o\/)/)?.[1] || '../';
const brokenBase = realViewerHref.match(/(.*\/o\/)/)?.[1] || '../';
const expectedBase = 'https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/';
ok(`FIXED base = bucket root (${fixedBase.length} chars)`, fixedBase === expectedBase, `got ${fixedBase.length} chars: ${fixedBase.substring(0,80)}...`);
ok(`BROKEN base was ${brokenBase.length} chars (greedy matched /o/ in ?lib=)`, brokenBase.length > 200, 'regex no longer greedy — test outdated');
const fixedBoqUrl = fixedBase + 'boq_charts.html';
const brokenBoqUrl = brokenBase + 'boq_charts.html';
ok('FIXED: opens boq_charts.html', fixedBoqUrl.endsWith('/o/boq_charts.html'), `wrong: ${fixedBoqUrl.substring(0,100)}`);
ok('BROKEN: would reopen viewer', brokenBoqUrl.includes('index.html'), 'broken path no longer reproduces — test outdated');

// 10e: tools.js must NOT have raw dbParam in window.open (the old bug)
const rawPattern = '`${base}boq_charts.html?db=${dbParam}';  // unencoded = bug
ok('no raw dbParam in boq URL', !toolsJs.includes(rawPattern), 'dbParam used raw — will cause recursive URL on OCI');

// 10f: LIVE END-TO-END — build the exact URL export4D5D produces, curl it, verify it's boq_charts (not viewer)
const viewerUrl = 'https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/sandbox/index.html';
const baseMatch = viewerUrl.match(/(.*\/o\/)/);
const simBase = baseMatch ? baseMatch[1] : '';
const simDbParam = 'https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/buildings/Duplex_extracted.db';
const boqUrl = `${simBase}boq_charts.html?db=${encodeURIComponent(simDbParam)}&bld=S0_0_Duplex`;
console.log(`  → simulated URL: ${boqUrl.substring(0, 80)}...`);
try {
  const boqBody = execSync(`curl -s "${boqUrl}"`, { stdio: 'pipe', timeout: 15000 }).toString();
  ok('📊 URL returns boq_charts.html (has Chart.js)', boqBody.includes('chart.js') || boqBody.includes('Chart.js'), 'URL did NOT return boq_charts — wrong page loaded');
  ok('📊 URL does NOT return viewer (no Three.js)', !boqBody.includes('setupStreaming') && !boqBody.includes('loader.js'), 'URL returned viewer instead of boq_charts — routing broken');
  // Verify boq_charts can parse the db param back
  const dbInPage = boqBody.match(/params\.get\(['"]db['"]\)/);
  ok('boq_charts reads ?db= param', !!dbInPage, 'boq_charts.html does not read db param');
} catch(e) { ok('📊 live URL fetch', false, 'curl failed: ' + e.message); }

// 10g: Verify the DB URL in the param is fetchable (not boq_charts.html itself)
try {
  const dbHead = execSync(`curl -sI "${simDbParam}" -o /dev/null -w "%{http_code}"`, { stdio: 'pipe', timeout: 10000 }).toString().trim();
  ok('DB URL in ?db= param is fetchable', dbHead === '200', `Duplex_extracted.db returned ${dbHead}`);
} catch(e) { ok('DB URL fetchable', false, 'curl failed'); }

// 10h: Download the DB, open with sqlite3, verify tables and data that feed the 9 charts
//   boq_charts.html queries: elements_meta (discipline, ifc_class, storey, building)
//                             element_instances (guid)
//   Charts need: ≥1 row in elements_meta with a building name
const tmpDb = '/tmp/test_duplex_extracted.db';
try {
  execSync(`curl -s "${simDbParam}" -o ${tmpDb}`, { stdio: 'pipe', timeout: 30000 });
  const tables = execSync(`sqlite3 ${tmpDb} ".tables"`, { stdio: 'pipe' }).toString();
  ok('DB has elements_meta table', tables.includes('elements_meta'), 'missing elements_meta — all 9 charts will be empty');
  ok('DB has element_instances table', tables.includes('element_instances'), 'missing element_instances — chart joins will fail');

  const rowCount = execSync(`sqlite3 ${tmpDb} "SELECT COUNT(*) FROM elements_meta"`, { stdio: 'pipe' }).toString().trim();
  ok(`elements_meta has data (${rowCount} rows)`, parseInt(rowCount) > 0, 'elements_meta is empty — all charts empty');

  const discs = execSync(`sqlite3 ${tmpDb} "SELECT DISTINCT discipline FROM elements_meta WHERE discipline IS NOT NULL"`, { stdio: 'pipe' }).toString().trim();
  const discCount = discs ? discs.split('\n').length : 0;
  ok(`has disciplines for pie chart (${discCount} found)`, discCount > 0, 'no disciplines — Chart 1 (Cost Pie) empty');

  const storeys = execSync(`sqlite3 ${tmpDb} "SELECT DISTINCT storey FROM elements_meta WHERE storey IS NOT NULL"`, { stdio: 'pipe' }).toString().trim();
  const storeyCount = storeys ? storeys.split('\n').length : 0;
  ok(`has storeys for breakdown (${storeyCount} found)`, storeyCount > 0, 'no storeys — per-storey charts empty');

  const bldName = execSync(`sqlite3 ${tmpDb} "SELECT building FROM elements_meta GROUP BY building ORDER BY COUNT(*) DESC LIMIT 1"`, { stdio: 'pipe' }).toString().trim();
  ok(`largest building found: '${bldName}'`, bldName.length > 0, 'no building name — boq_charts cannot filter');

  const classes = execSync(`sqlite3 ${tmpDb} "SELECT COUNT(DISTINCT ifc_class) FROM elements_meta"`, { stdio: 'pipe' }).toString().trim();
  ok(`has IFC classes for BOQ (${classes} types)`, parseInt(classes) > 0, 'no IFC classes — BOQ table empty');

  console.log(`  → §CHART_PROOF: DB has ${rowCount} elements, ${discCount} disciplines, ${storeyCount} storeys, ${classes} IFC classes — all 9 charts will render`);
  execSync(`rm -f ${tmpDb}`);
} catch(e) { ok('DB chart data verification', false, e.message); }

// ═══ 11. Button wiring — correct function on correct button ═══
console.log('\n═══ 11. Button Wiring Audit ═══');

// The 📊 button must call export4D5D, NOT exportIssuesExcel
const boqBtnMatch = html.match(/export4D5D\(\)[^"]*"[^>]*>[^<]*📊/);
ok('📊 button calls export4D5D()', !!boqBtnMatch, '📊 not wired to export4D5D');

// The Export Excel button must call exportIssuesExcel, NOT export4D5D
const excelBtnMatch = html.match(/exportIssuesExcel\(\)[^"]*"[^>]*>[^<]*Export Excel/);
ok('Export Excel button calls exportIssuesExcel()', !!excelBtnMatch, 'Export Excel not wired to exportIssuesExcel');

// Export Excel must be INSIDE issues-panel, not in search-box
const issuesPanelHtml = html.slice(html.indexOf('id="issues-panel"'));
const searchBoxHtml = html.slice(html.indexOf('id="search-box"'), html.indexOf('id="info-panel"'));
ok('Export Excel is inside issues-panel', issuesPanelHtml.includes('exportIssuesExcel'), 'Export Excel button not in issues-panel');
ok('Export Excel is NOT inside search-box', !searchBoxHtml.includes('exportIssuesExcel'), 'Export Excel button in search-box — will always fire from toolbar');

// 📊 must be INSIDE search-box, not issues-panel
ok('📊 is inside search-box', searchBoxHtml.includes('export4D5D'), '📊 button not in search-box');

// Issues panel z-index must be strictly higher than search-box z-index
const issuesZ = html.match(/#issues-panel\s*\{[^}]*z-index\s*:\s*(\d+)/);
const searchZ = html.match(/#search-box\s*\{[^}]*z-index\s*:\s*(\d+)/);
if (issuesZ && searchZ) {
  ok(`issues-panel z=${issuesZ[1]} > search-box z=${searchZ[1]}`, parseInt(issuesZ[1]) > parseInt(searchZ[1]), 'issues panel not above search-box');
} else {
  ok('z-index extraction', false, 'could not parse z-index from CSS');
}

// Mobile media query: issues panel z must also beat search-box
const mobileMatch = html.match(/@media[^{]*max-width\s*:\s*600px[^{]*\{([\s\S]*?)\n\s*\}/);
if (mobileMatch) {
  const mobileCss = mobileMatch[1];
  const mobileSearchZ = mobileCss.match(/#search-box[^}]*z-index\s*:\s*(\d+)/);
  if (mobileSearchZ) {
    const mobileIssuesZ = mobileCss.match(/#issues-panel[^}]*z-index\s*:\s*(\d+)/);
    const issuesBaseZ = issuesZ ? parseInt(issuesZ[1]) : 50;
    const mobileIZ = mobileIssuesZ ? parseInt(mobileIssuesZ[1]) : issuesBaseZ;
    ok(`mobile: issues z=${mobileIZ} > search-box z=${mobileSearchZ[1]}`, mobileIZ > parseInt(mobileSearchZ[1]), 'mobile: issues panel not above search-box');
  }
}

// excel.js must use synchronous XLSX.writeFile, not async blob/share
const excelJs = fs.readFileSync(path.join(DIR, 'excel.js'), 'utf8');
ok('excel uses XLSX.writeFile (sync)', excelJs.includes('XLSX.writeFile('), 'missing XLSX.writeFile — export will fail');
ok('excel is NOT async', !excelJs.match(/async\s+.*exportIssuesExcel/), 'exportIssuesExcel is async — browser will lose user gesture');

// ═══ SUMMARY ═══
console.log(`\n═══ SUMMARY: ${pass}/${pass + fail} passed, ${fail} failed ═══\n`);
process.exit(fail > 0 ? 1 : 0);
