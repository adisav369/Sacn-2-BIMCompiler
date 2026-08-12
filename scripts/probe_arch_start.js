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

const OPTIONAL = { _zoneIndexBuild: 1, _zoneIndex: 1, _zoneOf: 1 };
function sliceFn(src, name, which) {
  let from = 0;
  for (let pass = 0; pass <= (which || 0); pass++) {
    const idx = src.indexOf('function ' + name + '(', from);
    if (idx < 0) { if (OPTIONAL[name]) return null; throw new Error(name + ' #' + (which || 0) + ' not found'); }
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
// §ZONE_INDEX (bim-ootb #1313): _buildXrayElements now reads the shared memoized zone index, so
// the probe must slice it too. sliceFn returns null on an older tmSrc that predates it, and
// filter(Boolean) below drops it — so this file still measures pre-#1313 revisions unchanged.
const zoneParts = [sliceFn(tmSrc, '_zoneIndexBuild'), sliceFn(tmSrc, '_zoneIndex')].filter(Boolean);
// §TIER_SERIAL_BY_ZONE's zone key helper — optional the same way, so pre-change revisions still run.
const zoneOfPart = sliceFn(tmSrc, '_zoneOf') || '';
const sliced = [tierOrderLine,
  (zoneParts.length === 2 ? 'var _zoneMemo = [];' : ''), zoneParts[0] || '', zoneParts[1] || '',
  zoneOfPart,
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
      ScheduleGate: ScheduleGate, Math: Math, RegExp: RegExp, Object: Object, Infinity: Infinity,
      A: () => ({ db: db, activeBuilding: bld, _metaGen: 0 }) };
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

    // §CREW_AUTOSCALE (bim-ootb, injectGantt): mirrors the shipped formula so this probe measures
    // the SAME schedule the viewer generates. crews(T) = max(base, ceil(workDays(T)/projectDays)),
    // workDays from installSecs/28800, projectDays from the existing §4D length. CREWS=base env
    // reverts to the pre-autoscale table for an A/B.
    const crewWorkDays = {};
    geoEls.forEach(e => { const r = e.resource || '_DEFAULT';
      crewWorkDays[r] = (crewWorkDays[r] || 0) + (e.installSecs || 0) / 28800; });
    let totSecs = 0; geoEls.forEach(e => { totSecs += e.installSecs || 0; });
    const projDays = Math.max(10, Math.ceil(totSecs * 1000 / 86400000));
    const maxCrews = {}, crewNote = [];
    for (const rk in LR) {
      if (!LR[rk].max_crews && LR[rk].max_crews_fixed == null) continue;
      const base = LR[rk].max_crews || 0, wd = crewWorkDays[rk] || 0;
      const need = Math.ceil(wd / projDays);
      const use = (LR[rk].max_crews_fixed != null) ? LR[rk].max_crews_fixed
                : (process.env.CREWS === 'base' ? base : Math.max(base, need));
      maxCrews[rk] = use;
      if (use !== base) crewNote.push(rk + ' ' + base + '->' + use + '(wd=' + wd.toFixed(0) + ')');
    }
    console.log('  §CREW_AUTOSCALE ' + bld + ' projectDays=' + projDays + ' scaled: ' +
      (crewNote.length ? crewNote.join(' ') : 'none — baseline table already sufficient'));

    const quiet = console.log; console.log = () => {};
    let sched;
    t0 = now();
    try { sched = ScheduleGate.computeSchedule(geoEls, 0, 1, maxCrews); } finally { console.log = quiet; }
    t.compute = now() - t0;

    const items = geoEls.map(e => ({ guid: e.guid, s: sched[e.guid].start, e: sched[e.guid].end,
      rawS: sched[e.guid].start, rawE: sched[e.guid].end,
      bz: e.base_z, tz: e.top_z, x0: e.x0, x1: e.x1, y0: e.y0, y1: e.y1, cls: e.cls, seq: e.seq, phase: e.phase,
      storey: e.storey }));   // §TIER_SERIAL_BY_ZONE: storey comes from _buildXrayElements' median-Z banding — derived from the model, never configured

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

    // ── §TIER_SERIAL_BY_ZONE (2026-08-12) — SIMULATION ONLY, shipped code untouched ──────────
    // §TIER_SERIAL makes the Tier-1 backbone (Substructure→Superstructure→Architecture) strictly
    // serial GLOBALLY: all Superstructure everywhere finishes before any Architecture starts. That
    // is what inflates the programme 1.7-3.7x over RAW and creates the dead air. Its own header
    // cites the user ruling it implements — "separate unrelated disciplines can run parallel
    // thereafter if construction practice permits" — and real practice starts walls on level 1
    // while level 7 is still framing.
    // This measures what happens if the SAME shipped remap runs per DERIVED STOREY BAND instead of
    // once globally. Nothing is hardcoded and nothing is per-building: the band key is the storey
    // already assigned by _buildXrayElements' median-Z ranking, straight out of the IFC. A model
    // with one storey collapses to exactly today's behaviour.
    const bandOf = {};
    items.forEach(it => { bandOf[it.storey || '_UNKNOWN'] = 1; });
    const bandKeys = Object.keys(bandOf);
    const simItems = items.map(it => ({ guid: it.guid, s: it.rawS, e: it.rawE,
      bz: it.bz, tz: it.tz, x0: it.x0, x1: it.x1, y0: it.y0, y1: it.y1,
      cls: it.cls, seq: it.seq, phase: it.phase, storey: it.storey }));
    const byBand = {};
    simItems.forEach(it => { (byBand[it.storey || '_UNKNOWN'] ||= []).push(it); });
    for (const bk of bandKeys) {
      sandbox.__band = byBand[bk];
      if (!sandbox.__band || !sandbox.__band.length) continue;
      vm.runInContext('this.__remap(this.__band); this.__repair(this.__band);', sandbox);
    }
    const simEnd = Math.max.apply(null, simItems.map(i => i.e));
    const simSpanD = (simEnd - base) / D;
    const simOcc = {};
    simItems.forEach(it => {
      const x = simOcc[it.phase] || (simOcc[it.phase] = { w: 0, s: Infinity, e: -Infinity, n: 0 });
      x.w += (it.e - it.s) / D; x.n++;
      if (it.s < x.s) x.s = it.s;
      if (it.e > x.e) x.e = it.e;
    });
    const simSpanMs = Math.max(1, simEnd - base);
    const simHist = new Array(100).fill(0);
    simItems.forEach(it => { simHist[Math.min(99, Math.floor((it.s - base) / simSpanMs * 100))]++; });
    let simBest = 0, simCur = 0;
    for (let b = 0; b < 100; b++) { if (simHist[b] === 0) { simCur++; if (simCur > simBest) simBest = simCur; } else simCur = 0; }
    console.log('  §TIER_SERIAL_BY_ZONE ' + bld + ' bands=' + bandKeys.length +
      ' spanD ' + spanD.toFixed(1) + ' → ' + simSpanD.toFixed(1) +
      ' (' + (simSpanD / spanD).toFixed(2) + 'x, RAW was ' + ((Math.max.apply(null, items.map(i => i.rawE)) - rawBase) / D).toFixed(1) + ')' +
      ' longestEmptyRun ' + '→' + simBest + '%' +
      ' occ=' + Object.keys(simOcc).sort((a, b2) => {
        const ia = PH_ORDER.indexOf(a), ib = PH_ORDER.indexOf(b2);
        return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
      }).map(ph => { const x = simOcc[ph], w = (x.e - x.s) / D;
        return ph.slice(0, 6) + '=' + (w > 0 ? (100 * x.w / w).toFixed(0) : '0') + '%'; }).join(' '));

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

    // ── §DAY_GAP_WIP (2026-08-12) — the measurement §DAY_GAP was MISSING, and the one that decides
    // whether the "dead air" is a schedule defect or an honest crew-limited grind ─────────────────
    // §DAY_GAP counts STARTS only. A band with zero starts is not the same as a band with no work:
    // time_machine.js:1544 records that the real frontier on Hospital is "only ~7 elements at a time
    // (crew-cap)", so a long pole being ground out by a capped crew produces exactly zero starts
    // while being a perfectly truthful programme. The two cases demand OPPOSITE fixes:
    //   WIP > 0 through the gap  → the schedule is honest; re-timing starts to fill the window
    //                              would fabricate dates to fix a VIEWING complaint. Do not.
    //   WIP = 0 through the gap  → genuinely nothing is happening; that is an artificial hole in
    //                              the gating, and the fix belongs in the gate, not in a stretch.
    // Same buckets, same items, no new data — an element is in progress in bucket b if it started
    // at or before the bucket's midpoint and had not yet ended.
    // ⚠ MEASURED BY OVERLAP, NOT BY SAMPLING — the first cut of this probe sampled ONE instant per
    // band (`it.s <= t && it.e > t` at the band midpoint) and reported minWIP=0/maxWIP=0 everywhere,
    // which read as "every dead band is genuinely idle". That was a SAMPLING ARTEFACT, not a
    // finding: on Hospital a 1% band is 11.7 days while p50 element duration is 0.015d, so a single
    // instant has a ~0.1% chance of landing on any given element. With true concurrency near 1, an
    // instantaneous sample returns 0 most of the time by luck alone. Correct measure = total
    // element-days of work falling INSIDE the band ÷ band length = mean concurrency over the band.
    const wip = new Array(NB).fill(0);
    const bandMs = spanMs / NB;
    for (const it of items) {
      const b0 = Math.max(0, Math.min(NB - 1, Math.floor((it.s - base) / spanMs * NB)));
      const b1 = Math.max(0, Math.min(NB - 1, Math.floor((it.e - base) / spanMs * NB)));
      for (let b = b0; b <= b1; b++) {
        const lo = base + b * bandMs, hi = lo + bandMs;
        const ov = Math.min(it.e, hi) - Math.max(it.s, lo);
        if (ov > 0) wip[b] += ov / bandMs;   // fraction of the band this element occupies
      }
    }
    const wip5 = [];
    for (let b = 0; b < NB; b += 5) wip5.push((wip.slice(b, b + 5).reduce((a, c) => a + c, 0) / 5).toFixed(1));
    console.log('  §DAY_GAP_WIP ' + bld + ' meanInProgressPer5%=[' + wip5.join(',') + ']');
    // ── §DAY_GAP_DUR — why WIP is ~0 everywhere, not just in the gaps ─────────────────────────
    // If mean in-progress is 0-3 on a 63k-element building, elements are not "being built slowly in
    // a long window" — they are POINT EVENTS. Measure the duration distribution directly and state
    // it as a share of the programme, because that is the number that decides whether the gap is a
    // placement problem (fix by moving starts) or a duration problem (fix by giving work real time).
    const durD = items.map(it => (it.e - it.s) / D).sort((a, b) => a - b);
    const sumD = durD.reduce((a, c) => a + c, 0);
    const q = f => durD[Math.min(durD.length - 1, Math.floor(durD.length * f))];
    console.log('  §DAY_GAP_DUR ' + bld + ' n=' + durD.length +
      ' meanDur=' + (sumD / durD.length).toFixed(3) + 'd' +
      ' p50=' + q(0.5).toFixed(3) + 'd p90=' + q(0.9).toFixed(3) + 'd max=' + durD[durD.length - 1].toFixed(2) + 'd' +
      ' spanD=' + spanD.toFixed(1) +
      ' sumWorkDays=' + sumD.toFixed(1) +
      ' occupancy=' + (100 * sumD / spanD).toFixed(1) + '%' +
      ' (occupancy = mean elements in progress if work were spread evenly over the whole programme)');

    // ── §DAY_GAP_PHASE_OCC — the number that decides WHOSE window is wrong ────────────────────
    // Global occupancy (~100%) is dominated by the huge late phases; it hides the early ones. Per
    // phase: work-days inside that phase vs the width of the window it was given. A phase at ~100%
    // is honestly full. A phase at single-digit % has been handed a window tens of times longer
    // than its own work content — and THAT is what a viewer sees as dead air.
    const occPh = {};
    items.forEach(it => {
      const x = occPh[it.phase] || (occPh[it.phase] = { w: 0, s: Infinity, e: -Infinity, n: 0 });
      x.w += (it.e - it.s) / D; x.n++;
      if (it.s < x.s) x.s = it.s;
      if (it.e > x.e) x.e = it.e;
    });
    console.log('  §DAY_GAP_PHASE_OCC ' + bld + ' ' + Object.keys(occPh).sort((a, b) => {
      const ia = PH_ORDER.indexOf(a), ib = PH_ORDER.indexOf(b);
      return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
    }).map(ph => {
      const x = occPh[ph], winD = (x.e - x.s) / D;
      return ph + '=' + (winD > 0 ? (100 * x.w / winD).toFixed(1) : '0.0') + '%' +
        '(work=' + x.w.toFixed(1) + 'd win=' + winD.toFixed(1) + 'd n=' + x.n + ')';
    }).join(' '));

    // ── §DAY_GAP_TAIL — is a phase's empty window caused by a FEW straggler elements? ─────────
    // Superstructure's Hospital window moved 306.6→486.0 between REMAP and REPAIR while its work
    // content stayed 116d. If a phase's last half of elapsed time holds only a handful of elements,
    // the window is being dragged by a thin tail, NOT by the tier-serialization contract — which
    // matters enormously, because a tail is fixable without touching the user-confirmed strictly-
    // serial guarantee, and the contract is not.
    console.log('  §DAY_GAP_TAIL ' + bld + ' ' + Object.keys(occPh).sort((a, b) => {
      const ia = PH_ORDER.indexOf(a), ib = PH_ORDER.indexOf(b);
      return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
    }).map(ph => {
      const set = items.filter(it => it.phase === ph);
      if (!set.length) return null;
      const s0 = Math.min.apply(null, set.map(i => i.s)), e1 = Math.max.apply(null, set.map(i => i.e));
      const mid = s0 + (e1 - s0) / 2;
      const late = set.filter(i => i.s >= mid).length;
      // when did 95% of this phase's elements have started, as a % of its own window?
      const ss = set.map(i => i.s).sort((a, b) => a - b);
      const p95 = ss[Math.min(ss.length - 1, Math.floor(ss.length * 0.95))];
      const p95pct = (e1 > s0) ? (100 * (p95 - s0) / (e1 - s0)) : 0;
      return ph.slice(0, 6) + ': lastHalfOfWindow=' + late + '/' + set.length +
        '(' + (100 * late / set.length).toFixed(1) + '%) 95%startedBy=' + p95pct.toFixed(0) + '%ofWindow';
    }).filter(Boolean).join(' | '));

    // ── §DAY_GAP_TAIL_WHO — name the stragglers, don't just count them ────────────────────────
    // If ~13 of 2,603 elements drag a phase's window across 238 days, those 13 are either honestly
    // late (a real dependency) or artefacts of a single bad support edge. Either way they are
    // identifiable, and they are the cheapest thing in this whole study to act on. Reported for the
    // worst offender phase per building: how far each straggler sits past the phase's 95% mark.
    (() => {
      let worst = null;
      Object.keys(occPh).forEach(ph => {
        const set = items.filter(it => it.phase === ph);
        if (set.length < 50) return;
        const s0 = Math.min.apply(null, set.map(i => i.s)), e1 = Math.max.apply(null, set.map(i => i.e));
        const ss = set.map(i => i.s).sort((a, b) => a - b);
        const p95 = ss[Math.floor(ss.length * 0.95)];
        const tailDays = (e1 - p95) / D, tailN = set.filter(i => i.s > p95).length;
        if (!worst || tailDays > worst.tailDays) worst = { ph, set, p95, e1, tailDays, tailN, s0 };
      });
      if (!worst) return;
      const late = worst.set.filter(i => i.s > worst.p95)
        .sort((a, b) => b.s - a.s).slice(0, 8);
      const byCls = {};
      worst.set.filter(i => i.s > worst.p95).forEach(i => { byCls[i.cls] = (byCls[i.cls] || 0) + 1; });
      console.log('  §DAY_GAP_TAIL_WHO ' + bld + ' worstPhase="' + worst.ph + '" tail=' + worst.tailN +
        ' elements spanning ' + worst.tailDays.toFixed(1) + 'd past its own 95% mark' +
        ' | classes=' + Object.keys(byCls).sort((a, b) => byCls[b] - byCls[a])
          .slice(0, 6).map(c => c + '×' + byCls[c]).join(',') +
        ' | latest=' + late.slice(0, 4).map(i =>
          i.cls + '@day' + ((i.s - base) / D).toFixed(0) + '/bz' + i.bz.toFixed(1)).join(' '));
    })();

    // Focused readout over the dead run §DAY_GAP just found, plus the zero-start bands generally.
    const deadBands = [];
    for (let b = 0; b < NB; b++) if (hist[b] === 0) deadBands.push(b);
    const wipInDead = deadBands.map(b => wip[b]);
    const zeroWipDead = wipInDead.filter(w => w < 0.005).length;   // <0.5% of a band occupied = idle
    console.log('  §DAY_GAP_WIP ' + bld + ' zeroStartBands=' + deadBands.length +
      ' ofWhichAlsoZeroWork=' + zeroWipDead +
      ' minWIP=' + (wipInDead.length ? Math.min.apply(null, wipInDead).toFixed(2) : 'n/a') +
      ' maxWIP=' + (wipInDead.length ? Math.max.apply(null, wipInDead).toFixed(2) : 'n/a') +
      ' → ' + (deadBands.length === 0 ? 'no dead bands'
        : zeroWipDead === 0 ? 'EVERY zero-start band still has work in progress — crew-limited grind, NOT a hole'
        : zeroWipDead === deadBands.length ? 'EVERY zero-start band is also zero-work — a real hole in the gating'
        : 'MIXED — ' + zeroWipDead + ' of ' + deadBands.length + ' zero-start bands are genuinely idle'));

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
