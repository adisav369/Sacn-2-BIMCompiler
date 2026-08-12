#!/usr/bin/env node
// probe_arch_start.js — STUDY ONLY, no fix (2026-08-12, bim-compiler
// prompts/4D_SCHEDULE_PERFECTION.md; user: "the ARCH starting first day part is too fast").
//
// ISSUE this probe exposes: on the DISPLAY timeline the movie actually plays (the shipped
// _twoTierRemap + _midairRepair output that kernel_ops is written from), WHEN does each phase
// first appear, and how much of the building lands in the opening slice of the film.
// If a large share of elements — in particular Architecture (ARCH) — starts at day ~0, the film's
// first day is doing work that a real programme spreads over months, which is what "too fast"
// looks like on screen.
//
// Reported per building, every number derived, none assumed:
//   §ARCH_PHASE   per phase: first start day, last end day, element count, at RAW / REMAP / REPAIR
//   §ARCH_FRONT   share of elements whose START lands in the first 1d / 1% / 5% / 10% of the span
//   §ARCH_D1      what actually starts on day <=1, by phase and class
//   §ARCH_AUTH    §PHASE_OVERLAP_BAND authored bar start day per phase (planner-facing artefact)
//   §ARCH_TIME    wall-clock ms per generation stage (observation only — perf lane is elsewhere)
//
// Command (from bim-compiler/):
//   VIEWER_DIR=~/bim-ootb/viewer BLD_DIR=~/bim-ootb/buildings node scripts/probe_arch_start.js
// To measure a specific revision rather than the working tree, export it first:
//   mkdir -p /tmp/vw && cd ~/bim-ootb && for f in schedule_gate.js schedule_author.js \
//     time_machine.js rates.js; do git show origin/main:viewer/$f > /tmp/vw/$f; done && \
//     mkdir -p /tmp/vw/rates && git show origin/main:viewer/rates/sequence_rules.json > /tmp/vw/rates/
// Read the log, not the exit code.
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const HOME = require('os').homedir();
const VIEWER_DIR = process.env.VIEWER_DIR || path.join(HOME, 'bim-ootb', 'viewer');
const SQLJS_DIR = process.env.SQLJS_DIR || path.join(HOME, 'bim-ootb', 'modeller', 'lib');
const initSqlJs = require(path.join(SQLJS_DIR, 'sql-wasm.js'));
const ScheduleGate = require(path.join(VIEWER_DIR, 'schedule_gate.js'));
const ScheduleAuthor = require(path.join(VIEWER_DIR, 'schedule_author.js'));
const tmSrc = fs.readFileSync(path.join(VIEWER_DIR, 'time_machine.js'), 'utf8');

function sliceFn(src, name, which) {
  let from = 0;
  for (let pass = 0; pass <= (which || 0); pass++) {
    const idx = src.indexOf('function ' + name + '(', from);
    if (idx < 0) throw new Error(name + ' #' + (which || 0) + ' not found');
    let depth = 0, i = idx, seenOpen = false;
    for (; i < src.length; i++) {
      if (src[i] === '{') { depth++; seenOpen = true; }
      else if (src[i] === '}') { depth--; if (seenOpen && depth === 0) break; }
    }
    if (pass === (which || 0)) return src.slice(idx, i + 1);
    from = i + 1;
  }
  throw new Error('unreachable');
}
// NOTE: time_machine.js at origin/main add18e5 defines _midairRepair TWICE (:4248 and :4391).
// JS function declarations hoist, so the LAST one wins at runtime — the browser runs #1.
// MR env selects which copy this probe runs: 0 = first (what the witnesses slice), 1 = last
// (what the browser executes). Default 1 = ship truth.
const MR = parseInt(process.env.MR || '1', 10);
const tierOrderLine = "var _TIER1_ORDER = ['Substructure', 'Superstructure', 'Architecture'];";
const sliced = [tierOrderLine,
  sliceFn(tmSrc, '_promoteRoofLoadPath'), sliceFn(tmSrc, '_buildXrayElements'),
  sliceFn(tmSrc, '_tier1Extents'), sliceFn(tmSrc, '_tier1Serialize'),
  sliceFn(tmSrc, '_tier1Protrusion'), sliceFn(tmSrc, '_tierAuditRegate'),
  sliceFn(tmSrc, '_twoTierRemap'), sliceFn(tmSrc, '_contactGraph'),
  sliceFn(tmSrc, '_midairAudit'), sliceFn(tmSrc, '_midairRepair', MR)].join('\n');

function loadRatesTable() {
  const txt = fs.readFileSync(path.join(VIEWER_DIR, 'rates.js'), 'utf8');
  const start = txt.indexOf('var RATES = {');
  const defIdx = txt.indexOf('var SEQUENCE_DEFAULT');
  return (new Function(txt.slice(start, txt.indexOf('};', defIdx) + 2) + '\n return RATES;'))();
}

const BLD_DIR = process.env.BLD_DIR || path.join(HOME, 'bim-ootb', 'buildings');
const DB_FILE = { LTU_AHouse: 'LTU_AHouse_meta.db' };
const BUILDINGS = (process.env.ONLY || 'Terminal,Hospital,Duplex,HHS_Office_Federated,Clinic,LTU_AHouse,JKR').split(',');
const D = 86400000;
const now = () => Number(process.hrtime.bigint() / 1000000n);

function extents(items, key) {
  const ext = {};
  items.forEach(it => {
    const x = ext[it.phase] || (ext[it.phase] = { minS: Infinity, maxE: -Infinity, n: 0 });
    if (it[key.s] < x.minS) x.minS = it[key.s];
    if (it[key.e] > x.maxE) x.maxE = it[key.e];
    x.n++;
  });
  return ext;
}
const PH_ORDER = ['Substructure', 'Superstructure', 'Architecture', 'MEP Rough-in', 'MEP Final', 'Finishes'];
function fmtExt(ext, base) {
  return Object.keys(ext).sort((a, b) => {
    const ia = PH_ORDER.indexOf(a), ib = PH_ORDER.indexOf(b);
    return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
  }).map(ph => ph + '=[' + ((ext[ph].minS - base) / D).toFixed(1) + '..' +
    ((ext[ph].maxE - base) / D).toFixed(1) + ']d n=' + ext[ph].n).join(' ');
}

(async () => {
  const SQL = await initSqlJs({ wasmBinary: fs.readFileSync(path.join(SQLJS_DIR, 'sql-wasm.wasm')) });
  const rulesJson = JSON.parse(fs.readFileSync(path.join(VIEWER_DIR, 'rates', 'sequence_rules.json'), 'utf8'));
  const SR = rulesJson.SEQUENCE_RULES, SD = rulesJson.SEQUENCE_DEFAULT, LR = rulesJson.LABOR_RATES;
  const NO = rulesJson.NAME_OVERRIDES || [];
  const RATES = loadRatesTable();
  console.log('§ARCH_PROBE midairRepairCopy=' + MR + ' (0=first def / witness-sliced, 1=last def / browser-executed)');

  for (const bld of BUILDINGS) {
    const dbPath = path.join(BLD_DIR, DB_FILE[bld] || (bld + '_extracted.db'));
    if (!fs.existsSync(dbPath)) { console.log('§ARCH ' + bld + ' fixture missing: ' + dbPath); continue; }
    const t = {};
    let t0 = now();
    const db = new SQL.Database(fs.readFileSync(dbPath));
    t.dbOpen = now() - t0;
    const sandbox = { console: { log: () => {}, warn: () => {} }, performance: { now: () => Date.now() },
      window: { SEQUENCE_RULES: SR, SEQUENCE_DEFAULT: SD, SEQUENCE_NAME_OVERRIDES: NO },
      ScheduleGate: ScheduleGate, Math: Math, A: () => ({ db: db }) };
    vm.createContext(sandbox);
    vm.runInContext(sliced + '\nthis.__bxe = _buildXrayElements; this.__remap = _twoTierRemap; this.__repair = _midairRepair;', sandbox);
    t0 = now();
    const els = sandbox.__bxe();
    t.xray = now() - t0;
    if (!els || !els.length) { console.log('§ARCH ' + bld + ' element build produced nothing'); db.close(); continue; }

    const nameOf = {};
    const nr = db.exec("SELECT guid, ifc_class, COALESCE(element_name,'') FROM elements_meta");
    if (nr.length) nr[0].values.forEach(v => { nameOf[v[0]] = v[2]; });

    t0 = now();
    const frag = ScheduleAuthor._classFragmentation(db, RATES);
    const lin = ScheduleAuthor._linearWeighting(db, RATES);
    const geoEls = els.filter(e => !(e.x0 === e.x1 && e.y0 === e.y1 && e.base_z === e.top_z));
    geoEls.forEach(e => {
      const rule = ScheduleAuthor.matchNameOverride(e.cls, nameOf[e.guid] || '', NO) || ScheduleAuthor.matchRule(e.cls, SR, SD);
      if (!e.phase) e.phase = rule.phase;
      e.resource = rule.resource || '_DEFAULT';
      const realQty = (frag.fragmented[e.cls] && frag.area[e.guid] != null) ? frag.area[e.guid] : null;
      const span = Math.max(e.x1 - e.x0, e.y1 - e.y0, e.top_z - e.base_z);
      const avgLen = lin.avgLength[e.cls];
      const lengthRatio = (realQty == null && span > 0 && avgLen > 0) ? span / avgLen : null;
      e.installSecs = ScheduleAuthor._installSecs(e.cls, rule, LR, realQty, lengthRatio);
    });
    t.classify = now() - t0;

    // ── authored bars (planner-facing): capture §PHASE_OVERLAP_BAND / §PHASE_DURATION lines ──
    const authLines = [];
    const realLog = console.log;
    t0 = now();
    try {
      console.log = (...a) => { const s = a.join(' '); if (/^§PHASE_(OVERLAP_BAND|DURATION)/.test(s)) authLines.push(s); };
      global.SEQUENCE_RULES = SR; global.SEQUENCE_DEFAULT = SD; global.LABOR_RATES = LR;
      global.SEQUENCE_NAME_OVERRIDES = NO; global.RATES = RATES;
      ScheduleAuthor.materializeDefault(db, SR, { laborRates: LR, rates: RATES, nameOverrides: NO, defaultRule: SD });
    } catch (err) { console.log = realLog; console.log('  §ARCH_AUTH ' + bld + ' materializeDefault threw: ' + err.message); }
    console.log = realLog;
    t.authored = now() - t0;
    db.close();

    const maxCrews = {};
    for (const rk in LR) if (LR[rk].max_crews) maxCrews[rk] = LR[rk].max_crews;

    const quiet = console.log; console.log = () => {};
    let sched;
    t0 = now();
    try { sched = ScheduleGate.computeSchedule(geoEls, 0, 1, maxCrews); } finally { console.log = quiet; }
    t.compute = now() - t0;

    const items = geoEls.map(e => ({ guid: e.guid, s: sched[e.guid].start, e: sched[e.guid].end,
      rawS: sched[e.guid].start, rawE: sched[e.guid].end,
      bz: e.base_z, tz: e.top_z, x0: e.x0, x1: e.x1, y0: e.y0, y1: e.y1, cls: e.cls, seq: e.seq, phase: e.phase }));

    // RAW generative extents
    const rawBase = Math.min.apply(null, items.map(i => i.rawS));
    const rawExt = extents(items, { s: 'rawS', e: 'rawE' });

    sandbox.console = { log: () => {}, warn: () => {} };
    sandbox.__items = items;
    t0 = now();
    vm.runInContext('this.__remap(this.__items);', sandbox);
    t.remap = now() - t0;
    const remapS = items.map(i => i.s), remapE = items.map(i => i.e);
    const remapBase = rawBase;   // ONE epoch for all three rows — remap/repair only ever shift later,
                                 // so a per-row min would silently re-zero the axis and fake an "earlier" move
    const remapExt = extents(items.map((it, i) => ({ phase: it.phase, s: remapS[i], e: remapE[i] })), { s: 's', e: 'e' });

    t0 = now();
    vm.runInContext('this.__repair(this.__items);', sandbox);
    t.repair = now() - t0;

    const dispMin = Math.min.apply(null, items.map(i => i.s));
    const base = rawBase;
    const endAll = Math.max.apply(null, items.map(i => i.e));
    const spanD = (endAll - base) / D;
    const dispExt = extents(items, { s: 's', e: 'e' });

    console.log('\n════ ' + bld + '  elements=' + items.length + '  displaySpan=' + spanD.toFixed(1) +
      'd  firstElementStartsDay=' + ((dispMin - rawBase) / D).toFixed(1) + ' (all rows share the RAW epoch)');
    console.log('  §ARCH_PHASE RAW    ' + fmtExt(rawExt, rawBase));
    console.log('  §ARCH_PHASE REMAP  ' + fmtExt(remapExt, remapBase));
    console.log('  §ARCH_PHASE REPAIR ' + fmtExt(dispExt, base));

    // ── front-loading: share of the model whose START lands in the opening slice ──
    const cuts = [{ lbl: 'day<=1', ms: base + 1 * D }, { lbl: 'day<=2', ms: base + 2 * D },
      { lbl: 'day<=7', ms: base + 7 * D },
      { lbl: '1%', ms: base + (endAll - base) * 0.01 }, { lbl: '5%', ms: base + (endAll - base) * 0.05 },
      { lbl: '10%', ms: base + (endAll - base) * 0.10 }, { lbl: '25%', ms: base + (endAll - base) * 0.25 }];
    const front = cuts.map(c => {
      const n = items.filter(it => it.s <= c.ms).length;
      return c.lbl + '=' + n + '(' + (100 * n / items.length).toFixed(1) + '%)';
    });
    console.log('  §ARCH_FRONT ' + bld + ' startedBy ' + front.join(' '));

    // ── §DAY_GAP (added 2026-08-12, user: "at Day 14 onwards nothing happens and when scrub
    // forward it jumps to Day 48 with construction resuming") ────────────────────────────────
    // Buckets every element's display START into 1%-of-span slices and reports the longest run of
    // slices with ZERO starts. Percent-of-span, deliberately: the browser maps this generated
    // timeline through ONE global affine into the captured project window (the user's Hospital run
    // shows 1186.3 generated days served as a 126-day film), and an affine preserves relative
    // position — so a dead stretch is the SAME percentage of the film either way, while raw days
    // are not comparable. Their report is day 14→48 of 126 = 11.1%→38.1% of the film.
    const NB = 100, hist = new Array(NB).fill(0), spanMs = Math.max(1, endAll - base);
    items.forEach(it => { hist[Math.min(NB - 1, Math.floor((it.s - base) / spanMs * NB))]++; });
    let bestLo = -1, bestN = 0, curLo = -1, curN = 0;
    for (let b = 0; b < NB; b++) {
      if (hist[b] === 0) { if (curN === 0) curLo = b; curN++; if (curN > bestN) { bestN = curN; bestLo = curLo; } }
      else curN = 0;
    }
    const dayOf = pct => (pct / 100 * spanD).toFixed(1);
    console.log('  §DAY_GAP ' + bld + ' longestEmptyRun=' + bestN + '%' +
      (bestN ? ' at ' + bestLo + '%..' + (bestLo + bestN) + '% of the film (raw day ' +
        dayOf(bestLo) + '..' + dayOf(bestLo + bestN) + ' of ' + spanD.toFixed(1) + ')' : '') +
      ' — a run of film with ZERO element starts');
    // The whole shape, not just the worst run: starts per 5% of film, so a reader can see where the
    // work actually sits without trusting the single max above.
    const per5 = [];
    for (let b = 0; b < NB; b += 5) per5.push(hist.slice(b, b + 5).reduce((a, c) => a + c, 0));
    console.log('  §DAY_GAP_HIST ' + bld + ' startsPer5%=[' + per5.join(',') + ']');

    // per phase: how much of THAT phase lands in its own first day / first 10% of the span
    PH_ORDER.forEach(ph => {
      const set = items.filter(it => it.phase === ph);
      if (!set.length) return;
      const ph0 = Math.min.apply(null, set.map(i => i.s));
      const inD1 = set.filter(it => it.s <= ph0 + 1 * D).length;
      const in10 = set.filter(it => it.s <= ph0 + (endAll - base) * 0.10).length;
      console.log('    §ARCH_PHASE_FRONT ' + bld + ' ' + ph + ' startsDay=' + ((ph0 - base) / D).toFixed(1) +
        ' ownFirstDay=' + inD1 + '/' + set.length + '(' + (100 * inD1 / set.length).toFixed(1) + '%)' +
        ' ownFirst10%ofFilm=' + in10 + '/' + set.length + '(' + (100 * in10 / set.length).toFixed(1) + '%)');
    });

    // ── what exactly is on screen in day <=1 ──
    const d1 = items.filter(it => it.s <= base + 1 * D);
    const byPh = {}, byCls = {};
    d1.forEach(it => { byPh[it.phase] = (byPh[it.phase] || 0) + 1; byCls[it.cls] = (byCls[it.cls] || 0) + 1; });
    const top = k => Object.keys(k).sort((a, b) => k[b] - k[a]).slice(0, 8).map(c => c + ':' + k[c]).join(' ');
    console.log('  §ARCH_D1 ' + bld + ' n=' + d1.length + ' phases{' + top(byPh) + '} classes{' + top(byCls) + '}');

    // ── authored (planner-facing) bar starts ──
    authLines.filter(l => /OVERLAP_BAND/.test(l)).forEach(l => console.log('  §ARCH_AUTH ' + bld + ' ' + l.replace('§PHASE_OVERLAP_BAND ', '')));

    console.log('  §ARCH_TIME ' + bld + ' dbOpen=' + t.dbOpen + 'ms xray=' + t.xray + 'ms classify=' + t.classify +
      'ms authoredBars=' + t.authored + 'ms computeSchedule=' + t.compute + 'ms twoTierRemap=' + t.remap +
      'ms midairRepair=' + t.repair + 'ms TOTAL=' + Object.keys(t).reduce((a, k) => a + t[k], 0) + 'ms');
  }
})();
