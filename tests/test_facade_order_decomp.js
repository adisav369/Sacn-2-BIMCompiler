#!/usr/bin/env node
/* ⚠ WITNESS — §STAGE B (bim-compiler prompts/RESUME_4D_TRUTH_AND_BE_HERE_WHEN.md, 2026-07-30).
 * Read that section before changing anything here.
 *
 * NAMES THE ISSUE it proves/disproves:
 *   "The 4D sequencer keys on ifc_class alone, so an IfcCurtainWall's IfcPlate glazing (seq 4) and
 *    IfcMember mullions (seq 3) are erected as Superstructure ~250 days before the wall they touch.
 *    Does the AUTHORED IFC decomposition edge (rel_aggregates) fix the order without touching any
 *    genuinely-structural plate?"
 *
 *   B3 §W-FACADE-ORDER   — must go RED (BEFORE) -> GREEN (AFTER) on Hospital and HHS.
 *   B4 §W-FACADE-NOFP    — Terminal's 33,324 'Metal Deck' IfcPlate have NO IfcCurtainWall parent, so
 *                          BEFORE and AFTER must be BIT-IDENTICAL. Disproves "this is a blanket
 *                          IfcPlate reclassification."
 *
 * THE RULE UNDER TEST — extraction, not invention. An element whose AUTHORED parent (IfcRelAggregates,
 * provenance ifc:recovered) is an IfcCurtainWall takes the sequence rule already published for
 * IfcCurtainWall in viewer/rates/sequence_rules.json (seq 7 / Architecture / CARPENTER). Nothing is
 * synthesised: the parent class is read from the IFC and the sequence is read from the existing rules
 * file. NO name regex — Hospital says 'System Panel:Glazed', HHS says 'Verglasung', and a mullion is
 * 'Rechteckiger Pfosten:6 x 15 mit Deckprofil'. The decomposition carries no language.
 *
 * ANALYSIS ONLY. This changes no viewer production file. It READS the deployed viewer/schedule_gate.js
 * and viewer/rates/sequence_rules.json so the numbers are the shipped gate's, not a re-implementation.
 * Element build replicated from viewer/time_machine.js injectGantt() — same contract as the Stage A
 * witness bim-ootb tests/test_host_order.js, from which the shared parts below are taken verbatim.
 *
 * USAGE
 *   node tests/test_facade_order_decomp.js
 *   FACADE_CASES='Hospital:/path/b.db:/path/rel.db,...' node tests/test_facade_order_decomp.js
 *   OOTB=/home/red1/bim-ootb   (where viewer/schedule_gate.js lives)
 *
 * The rel_aggregates sidecar is any DB carrying the table produced by
 * DAGCompiler/python/extractIFCtoDB.py §DECOMP. SKIPs (exit 0) when inputs are absent.
 *
 * Read the §-log lines. Exit code alone is not evidence.
 */
'use strict';
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const OOTB = process.env.OOTB || path.join(process.env.HOME || '/home/red1', 'bim-ootb');
const GATE = path.join(OOTB, 'viewer/schedule_gate.js');
const RULES = path.join(OOTB, 'viewer/rates/sequence_rules.json');
if (!fs.existsSync(GATE) || !fs.existsSync(RULES)) {
  console.log('§FACADE_DECOMP SKIP — no deployed viewer at ' + OOTB); process.exit(0);
}
const ScheduleGate = require(GATE);                    // the REAL deployed gate
const RATES = JSON.parse(fs.readFileSync(RULES, 'utf8'));
const SR = RATES.SEQUENCE_RULES, LR = RATES.LABOR_RATES, SD = RATES.SEQUENCE_DEFAULT;

const SEP = '~@~', CELL = 4, DAY = 86400000;
const day = ms => ms / DAY;

// ── injectGantt() replica (time_machine.js:3175-3298) ────────────────────────
function matchRule(cls) {
  if (!cls) return SD;
  let bestKey = null, bestLen = 0;
  for (const key in SR) if (cls.indexOf(key) >= 0 && key.length > bestLen) { bestKey = key; bestLen = key.length; }
  return bestKey ? SR[bestKey] : SD;
}
function getInstallSecs(cls) {
  const rule = matchRule(cls), resource = rule.resource;
  if (!resource || !LR[resource]) return 120;
  const labor = LR[resource];
  let bestPk = null, bestLen = 0;
  for (const pk in labor.productivity) if (cls.indexOf(pk) >= 0 && pk.length > bestLen) { bestPk = pk; bestLen = pk.length; }
  const prod = bestPk ? labor.productivity[bestPk] : 0;
  return prod > 0 ? Math.round(28800 / prod) : 120;
}
const sq = (db, sql) => execSync(`sqlite3 -noheader -separator '${SEP}' "${db}" "${sql}"`, { maxBuffer: 1 << 30 })
  .toString().trim();

function loadRows(db) {
  const t = sq(db,
    `SELECT m.guid, m.ifc_class, COALESCE(m.element_name,''), COALESCE(m.storey,''), ` +
    `COALESCE(t.center_z,0), COALESCE(t.bbox_z,0), COALESCE(t.center_x,0), COALESCE(t.center_y,0), ` +
    `COALESCE(t.bbox_x,0), COALESCE(t.bbox_y,0) FROM elements_meta m ` +
    `LEFT JOIN element_transforms t ON t.guid = m.guid ` +
    `WHERE m.ifc_class != 'IfcOpeningElement' ORDER BY 5,7,8;`);
  return t ? t.split('\n').map(l => l.split(SEP)).filter(a => a.length >= 10) : [];
}

/** child_guid -> parent ifc_class, RECOVERED from the authored IfcRelAggregates edge. */
function loadDecomp(relDb) {
  if (!relDb || !fs.existsSync(relDb)) return null;
  const has = sq(relDb, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='rel_aggregates';");
  if (has !== '1') return null;
  const t = sq(relDb, "SELECT child_guid, parent_class FROM rel_aggregates WHERE rel_type='aggregates' AND parent_class IS NOT NULL;");
  const m = new Map();
  if (t) t.split('\n').forEach(l => { const a = l.split(SEP); if (a.length >= 2 && !m.has(a[0])) m.set(a[0], a[1]); });
  return m;
}

// §STOREY-Z (time_machine.js:3218-3268) — always on; this is the production arm.
function build(rows, decomp) {
  const zv = {};
  rows.forEach(r => {
    const s = r[3] || '_UNKNOWN';
    if (s === '_UNKNOWN' || /^unknown$/i.test(s)) return;
    (zv[s] = zv[s] || []).push(+r[4] || 0);
  });
  const med = {};
  for (const k in zv) { const v = zv[k].sort((a, b) => a - b), i = Math.floor(v.length / 2); med[k] = v.length % 2 ? v[i] : (v[i - 1] + v[i]) / 2; }
  const names = Object.keys(med).sort((a, b) => med[a] - med[b]);
  let reassigned = 0, retagged = 0;
  const elements = rows.map(r => {
    const cls = r[1], raw = r[3] || '_UNKNOWN';
    const cz = +r[4] || 0, bz = +r[5] || 0, cx = +r[6] || 0, cy = +r[7] || 0, bx = +r[8] || 0, by = +r[9] || 0;
    let storey = raw;
    if ((raw === '_UNKNOWN' || /^unknown$/i.test(raw)) && names.length) {
      let best = names[0], bd = Infinity;
      for (let i = 0; i < names.length; i++) { const d = Math.abs(cz - med[names[i]]); if (d < bd) { bd = d; best = names[i]; } }
      storey = best; reassigned++;
    }
    // ── THE RULE UNDER TEST ──────────────────────────────────────────────────
    // AFTER arm only (decomp != null): if the AUTHORED parent is an IfcCurtainWall, sequence this
    // element as its parent's class does. Extraction (parent class from IFC) + the existing published
    // rule (sequence from sequence_rules.json). No name matching, no invented sequence number.
    let ruleCls = cls;
    if (decomp && decomp.get(r[0]) === 'IfcCurtainWall' && cls !== 'IfcCurtainWall') { ruleCls = 'IfcCurtainWall'; retagged++; }
    const rule = matchRule(ruleCls);
    let seq = rule.sequence, phase = rule.phase;
    if (/roof/i.test(storey) && cls === 'IfcSlab' && seq < 8) { seq = 8; phase = 'Architecture'; }
    return {
      guid: r[0], cls, name: r[2] || '', storey, cz,
      base_z: cz - bz / 2, top_z: cz + bz / 2,
      x0: cx - bx / 2, x1: cx + bx / 2, y0: cy - by / 2, y1: cy + by / 2,
      seq, phase, resource: rule.resource || '_DEFAULT', installSecs: getInstallSecs(ruleCls)
    };
  });
  elements.sort((a, b) => {
    const A = Math.floor(a.cz / 3), B = Math.floor(b.cz / 3);
    if (A !== B) return A - B;
    if (a.seq !== b.seq) return a.seq - b.seq;
    return a.cz - b.cz;
  });
  let totalSecs = 0; elements.forEach(e => { totalSecs += e.installSecs; });
  const rawMs = totalSecs * 1000, rawDays = rawMs / DAY;
  const scaleFactor = rawDays < 10 ? (10 * DAY) / rawMs : 1;
  const maxCrews = {};
  for (const r in LR) if (LR[r].max_crews) maxCrews[r] = LR[r].max_crews;
  return { elements, retagged, reassigned,
           sched: ScheduleGate.computeSchedule(elements, 0, scaleFactor, maxCrews),
           projectDays: Math.max(10, Math.ceil(rawDays * scaleFactor)) };
}

const cellsOf = e => {
  const o = [];
  for (let i = Math.floor(e.x0 / CELL); i <= Math.floor(e.x1 / CELL); i++)
    for (let j = Math.floor(e.y0 / CELL); j <= Math.floor(e.y1 / CELL); j++) o.push(i + ',' + j);
  return o;
};
const overlaps3d = (a, b, t) =>
  a.x0 <= b.x1 + t && a.x1 >= b.x0 - t && a.y0 <= b.y1 + t && a.y1 >= b.y0 - t &&
  a.base_z <= b.top_z + t && a.top_z >= b.base_z - t;
const isWall = e => e.cls === 'IfcWall' || e.cls === 'IfcWallStandardCase';

/** §W-FACADE-ORDER over a FIXED panel population, so BEFORE and AFTER compare like with like. */
function facade(arm, panelGuids, tag) {
  const { elements, sched } = arm;
  const walls = elements.filter(isWall);
  const panels = elements.filter(e => panelGuids.has(e.guid));
  if (!panels.length || !walls.length) {
    console.log('  §W-FACADE-ORDER[' + tag + '] SKIP panels=' + panels.length + ' walls=' + walls.length);
    return null;
  }
  const firstWall = Math.min.apply(null, walls.map(w => sched[w.guid].start));
  const before = panels.filter(g => sched[g.guid].start < firstWall).length;
  const grid = {};
  walls.forEach(w => cellsOf(w).forEach(c => (grid[c] = grid[c] || []).push(w)));
  let touching = 0, viol = 0, worst = 0; const ex = [];
  panels.forEach(g => {
    const seen = {}; let bad = null, hasOverlap = false;
    cellsOf(g).forEach(c => {
      const a = grid[c]; if (!a) return;
      for (let i = 0; i < a.length; i++) {
        const w = a[i];
        if (seen[w.guid]) continue; seen[w.guid] = 1;
        if (!overlaps3d(w, g, 0.25)) continue;       // MEASURED overlap; a shared 4m cell is not contact
        hasOverlap = true;
        const late = day(sched[w.guid].start) - day(sched[g.guid].start);
        if (late > 0 && (!bad || late > bad.late)) bad = { w, late };
      }
    });
    if (!hasOverlap) return;
    touching++;
    if (bad) {
      viol++; if (bad.late > worst) worst = bad.late;
      if (ex.length < 2) ex.push('panel ' + g.guid + ' @day' + day(sched[g.guid].start).toFixed(2) +
        ' vs touching ' + bad.w.cls + ' @day' + day(sched[bad.w.guid].start).toFixed(2) + ' late=' + bad.late.toFixed(1) + 'd');
    }
  });
  const r = { panels: panels.length, beforeAnyWall: before, touching, viol, worst,
              firstWallDay: day(firstWall), red: (before > 0 || viol > 0) };
  console.log('  §W-FACADE-ORDER[' + tag + '] panels=' + r.panels +
    ' earliestWallDay=' + r.firstWallDay.toFixed(2) +
    ' panelsBeforeAnyWall=' + before + '/' + r.panels +
    ' touchingPairs=' + touching + ' violations=' + viol + '/' + touching +
    ' worstLateDays=' + worst.toFixed(2) + (r.red ? '   RED' : '   GREEN'));
  ex.forEach(e => console.log('       e.g. ' + e));
  return r;
}

// ── cases ────────────────────────────────────────────────────────────────────
const BLD = path.join(OOTB, 'buildings');
const SCR = process.env.FACADE_REL_DIR || '';
const cases = (process.env.FACADE_CASES ||
  ['Hospital:' + path.join(BLD, 'Hospital_extracted.db') + ':' + path.join(SCR, 'Hospital_federated4.db'),
   'Terminal:' + path.join(BLD, 'Terminal_extracted.db') + ':' + path.join(SCR, 'terminal_rel.db'),
   'HHS_Office_Federated:' + path.join(BLD, 'HHS_Office_Federated_extracted.db') + ':' + path.join(SCR, 'hhs_rel.db')
  ].join(',')).split(',').map(s => s.split(':'));

let RED = 0, GREEN = 0, cases_run = 0;
cases.forEach(([label, db, relDb]) => {
  if (!fs.existsSync(db)) { console.log('§CASE ' + label + ' SKIP — no DB ' + db); return; }
  const decomp = loadDecomp(relDb);
  if (!decomp) { console.log('§CASE ' + label + ' SKIP — no rel_aggregates sidecar ' + relDb); return; }
  cases_run++;
  const rows = loadRows(db);

  // Population under assertion = the AUTHORED decomposition (locale-proof). Cross-checked below
  // against the name regex the Stage A witness had to use, so any divergence is a NUMBER.
  const cwChild = new Set();
  rows.forEach(r => { if (decomp.get(r[0]) === 'IfcCurtainWall' && (r[1] === 'IfcPlate' || r[1] === 'IfcMember')) cwChild.add(r[0]); });
  const plates = rows.filter(r => r[1] === 'IfcPlate');
  const nameGlaze = new Set(plates.filter(r => /glaz|glass|verglas|vitrage|vidrio/i.test(r[2] || '')).map(r => r[0]));
  const decompPlates = new Set(plates.filter(r => cwChild.has(r[0])).map(r => r[0]));
  const nameOnly = [...nameGlaze].filter(g => !decompPlates.has(g)).length;
  const decompOnly = [...decompPlates].filter(g => !nameGlaze.has(g)).length;

  console.log('\n§CASE ' + label + ' rows=' + rows.length + ' relEdges=' + decomp.size);
  console.log('  §DECOMP_POP IfcPlate=' + plates.length +
    ' curtainWallChildren(plate+member)=' + cwChild.size +
    '   platesByDecomp=' + decompPlates.size + ' platesByNameRegex=' + nameGlaze.size +
    ' nameOnly=' + nameOnly + ' decompOnly=' + decompOnly +
    (decompPlates.size === nameGlaze.size && !nameOnly && !decompOnly ? '  (agree)' : ''));

  const A = build(rows, null);                 // BEFORE — ifc_class only, today's shipped behaviour
  const B = build(rows, decomp);               // AFTER  — authored decomposition drives the sequence
  console.log('  §RETAG elements resequenced by decomposition = ' + B.retagged +
    '   projectDays before=' + A.projectDays + ' after=' + B.projectDays);

  if (!cwChild.size) {
    // B4 — no curtain-wall parent anywhere: the rule must be a strict no-op.
    const same = A.elements.every((e, i) => e.guid === B.elements[i].guid &&
      e.seq === B.elements[i].seq && A.sched[e.guid].start === B.sched[e.guid].start);
    console.log('  §W-FACADE-NOFP curtainWallChildren=0 retagged=' + B.retagged +
      ' scheduleIdentical=' + same + (same && B.retagged === 0 ? '   GREEN — no false positive' : '   RED'));
    if (same && B.retagged === 0) GREEN++; else RED++;
    // still report the panel order on the name population, for completeness
    if (nameGlaze.size) facade(A, nameGlaze, 'nameRegex/before');
    return;
  }

  const pop = new Set([...decompPlates]);
  const before = facade(A, pop, 'BEFORE');
  const after  = facade(B, pop, 'AFTER');
  if (before && after) {
    const fixed = before.red && !after.red;
    console.log('  §VERDICT[' + label + '] ' + (fixed ? 'RED -> GREEN  FIXED' : (after.red ? 'STILL RED' : 'was already GREEN')) +
      '   violations ' + before.viol + '/' + before.touching + ' -> ' + after.viol + '/' + after.touching +
      '   worstLateDays ' + before.worst.toFixed(2) + ' -> ' + after.worst.toFixed(2) +
      '   panelsBeforeAnyWall ' + before.beforeAnyWall + ' -> ' + after.beforeAnyWall);
    if (fixed) GREEN++; else RED++;
  }
});

console.log('\n§VERDICT ' + (RED ? 'RED' : 'GREEN') + ' — ' + GREEN + ' of ' + cases_run +
  ' cases behaved as specced. Spec: bim-compiler prompts/RESUME_4D_TRUTH_AND_BE_HERE_WHEN.md §STAGE B.');
process.exit(RED ? 1 : 0);
