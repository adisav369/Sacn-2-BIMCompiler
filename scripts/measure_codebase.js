#!/usr/bin/env node
// measure_codebase.js — W-CODEBASE-FACTS
// Make the bench_suite "less code / fewer tables" claims DYNAMIC + MEASURED on BOTH sides, so they
// respond to growing code instead of drifting from a frozen number. Counts real source on disk:
//   OURS  = the deployed Fold-Engine ERP app  (bim-ootb/erp/*.js, minus vendored libs)
//   THEM  = iDempiere Java application source  (~/idempiere-dev-setup/idempiere/**/*.java)
// Emits build/erp/bench_facts.json (+ syncs to bim-ootb/erp/) — bench_suite.html renders from it.
// NON-INVENT: every number is counted here; the iDempiere table count (925) is its published figure,
// labelled as documented. Re-run on each deploy → the ratio self-updates (and shrinks as we add code;
// that is the honest trade — more coverage, smaller multiple).
// Run: node scripts/measure_codebase.js
const fs = require('fs');
const path = require('path');

const REPO = '/home/red1/bim-compiler';
const ERP_DIR = '/home/red1/bim-ootb/erp';                 // the DEPLOYED engine = the fair "our app"
const ERP_SRC = path.join(REPO, 'build/erp');              // source-of-truth (for table-schema scan)
const IDMP = '/home/red1/idempiere-dev-setup/idempiere';
const VENDORED = /(^|\/)(sql-wasm|chart\.umd|chart\.min)|\.min\.js$/i;

function loc(file) { try { return fs.readFileSync(file, 'utf8').split('\n').length; } catch (e) { return 0; } }
function bytes(file) { try { return fs.statSync(file).size; } catch (e) { return 0; } }
const mb = b => +(b / 1048576).toFixed(2);

// ── OURS: deployed ERP engine JS ──────────────────────────────────────────────
let ourFiles = [];
try {
  ourFiles = fs.readdirSync(ERP_DIR).filter(f => f.endsWith('.js') && !VENDORED.test(f)).sort();
} catch (e) { console.error('ERP_DIR missing: ' + ERP_DIR); }
const ourFileLoc = ourFiles.map(f => ({ file: f, loc: loc(path.join(ERP_DIR, f)), bytes: bytes(path.join(ERP_DIR, f)) }));
const ourLoc = ourFileLoc.reduce((s, x) => s + x.loc, 0);
const ourBytes = ourFileLoc.reduce((s, x) => s + x.bytes, 0);

// ── OURS: distinct relations (CREATE TABLE across the engine source) ───────────
const tableSet = new Set();
try {
  for (const f of fs.readdirSync(ERP_SRC).filter(f => f.endsWith('.js'))) {
    const txt = fs.readFileSync(path.join(ERP_SRC, f), 'utf8');
    const re = /CREATE TABLE (?:IF NOT EXISTS )?["'`]?([A-Za-z_][A-Za-z0-9_]*)/gi;
    let m; while ((m = re.exec(txt))) tableSet.add(m[1].toLowerCase());
  }
} catch (e) {}
const ourTables = tableSet.size;

// ── THEM: iDempiere Java LOC (walk the tree) ──────────────────────────────────
function walkJava(dir, acc) {
  let entries; try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { return; }
  for (const e of entries) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walkJava(p, acc);
    else if (e.name.endsWith('.java')) { acc.count++; acc.loc += loc(p); acc.bytes += bytes(p); }
  }
}
const them = { count: 0, loc: 0, bytes: 0 };
walkJava(IDMP, them);

const facts = {
  measured_at_note: 'stamped by deploy wrapper; node has no stable clock here',
  ours: { name: 'Fold-Engine ERP (browser)', lang: 'JS', loc: ourLoc, bytes: ourBytes, mb: mb(ourBytes),
          files: ourFiles.length, tables: ourTables, source: 'bim-ootb/erp/*.js (minus vendored)', measured: true },
  them: { name: 'iDempiere', lang: 'Java', loc: them.loc, bytes: them.bytes, mb: mb(them.bytes), files: them.count,
          tables: 925, tables_source: 'published iDempiere AD_Table count (documented)',
          loc_source: '~/idempiere-dev-setup/idempiere/**/*.java', measured: true },
  ratios: {
    loc: +(them.loc / Math.max(ourLoc, 1)).toFixed(1),
    bytes: +(them.bytes / Math.max(ourBytes, 1)).toFixed(1),
    tables: +(925 / Math.max(ourTables, 1)).toFixed(1),
  },
  file_list: ourFileLoc,   // page can live-recount these same-origin → responds to growing code with no rebuild
  witness: 'W-CODEBASE-FACTS',
};

const out = path.join(ERP_SRC, 'bench_facts.json');
fs.writeFileSync(out, JSON.stringify(facts, null, 2));
try { fs.copyFileSync(out, path.join(ERP_DIR, 'bench_facts.json')); } catch (e) {}

console.log('\n═══ W-CODEBASE-FACTS — dynamic benchmark facts (both sides measured) ═══\n');
console.log('  §CODEBASE ours_loc=' + ourLoc + ' size=' + mb(ourBytes) + 'MB files=' + ourFiles.length + ' tables=' + ourTables + '  (' + facts.ours.source + ')');
console.log('  §CODEBASE them_loc=' + them.loc + ' size=' + mb(them.bytes) + 'MB files=' + them.count + ' tables=925(documented)  (iDempiere *.java)');
console.log('  §CODEBASE ratio_loc=' + facts.ratios.loc + '×  ratio_bytes=' + facts.ratios.bytes + '×  ratio_tables=' + facts.ratios.tables + '×');
console.log('  §CODEBASE wrote ' + out + ' (+ synced to ' + ERP_DIR + ')');
console.log('\n  Re-run on deploy → numbers track the real code. Multiple shrinks as coverage grows (honest).\n');
