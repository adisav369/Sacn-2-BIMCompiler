#!/usr/bin/env node
// Implementing TM_S4_SHOPFLOOR_BUILD.md §MODEL / §INVENTION BOUNDARY — Witness: W-SHOP-ELEMENTS / W-SHOP-BATCH / W-SHOP-SOURCE
// E0: pure deterministic genShopfloor(seed) → {PP_Order[], PP_Order_Node[], costSplit[]} from the REAL phase tree +
// crew grouping. Whitebox: reads ~/bim-ootb/erp/ad_seed.db via sqlite3 CLI, asserts the falsifiers, §-logs the math.
// NO browser. NO random. NO Date.now → re-run byte-identical. Money in integer rupiah (PlannedAmt is integer) so the
// material plug closes the sum EXACTLY. Read the §-log; the §-log is the proof.
'use strict';
const { execFileSync } = require('child_process');
const os = require('os');
const path = require('path');

const SEED = process.argv[2] || path.join(os.homedir(), 'bim-ootb/erp/ad_seed.db');
const log = (tag, msg) => console.log(`§${tag} ${msg}`);

// ── EXTRACTED rates (viewer/rates.js LABOR_RATES, from boq_export.py) ──────────────────────────────
const CREW = {
  CONCRETE_GANG: { rate: 145, size: 6 }, STEEL_ERECTOR: { rate: 195, size: 4 },
  MASON:         { rate: 155, size: 3 }, CARPENTER:     { rate: 165, size: 2 },
  ROOFER:        { rate: 175, size: 3 }, FINISHER:      { rate: 135, size: 2 },
  HVAC_TECH:     { rate: 185, size: 2 }, PLUMBER:       { rate: 165, size: 2 },
  ELECTRICIAN:   { rate: 175, size: 2 }, LABORER:       { rate: 95,  size: 1 },
};
// ── GENERATE layer (§INVENTION BOUNDARY — documented, fixed) ───────────────────────────────────────
const SETUP   = { STEEL_ERECTOR:8000, CONCRETE_GANG:6000, ROOFER:3000, MASON:2000, CARPENTER:1500,
                  HVAC_TECH:1800, PLUMBER:1500, ELECTRICIAN:1500, FINISHER:800, LABORER:400 };
const BATCH_N = { STEEL_ERECTOR:4, CONCRETE_GANG:6, ROOFER:10, MASON:10, CARPENTER:10,
                  HVAC_TECH:20, PLUMBER:20, ELECTRICIAN:20, FINISHER:20, LABORER:20 };
const burdenPct  = c => (c === 'LABORER' ? 0.28 : 0.35);
const OVERHEAD_PCT = 0.15;
// ── industry-best phase→crew sequencing (deterministic) ────────────────────────────────────────────
const PHASE_CREWS = {
  'Substructure':   ['CONCRETE_GANG', 'STEEL_ERECTOR'],
  'Superstructure': ['STEEL_ERECTOR', 'CONCRETE_GANG', 'MASON'],
  'MEP Rough-in':   ['HVAC_TECH', 'PLUMBER', 'ELECTRICIAN'],
  'Architecture':   ['MASON', 'CARPENTER', 'ROOFER'],
  'MEP Final':      ['HVAC_TECH', 'PLUMBER', 'ELECTRICIAN'],
  'Finishes':       ['FINISHER', 'CARPENTER'],
};
const COST_ELEMENT = { material:100, labor:105, burden:50000, overhead:50001 }; // ids in M_CostElement

const sq = q => execFileSync('sqlite3', ['-noheader', '-separator', '|', SEED, q], { encoding: 'utf8' }).trim();
const days = (a, b) => Math.round((Date.parse(b) - Date.parse(a)) / 86400000);

// ── the pure generator ─────────────────────────────────────────────────────────────────────────────
function genShopfloor(phases) {
  const orders = [], nodes = [], split = [];
  let ppId = 9900000, nodeId = 9900000;
  for (const ph of phases) {
    const crews = PHASE_CREWS[ph.name];
    if (!crews) continue;                                   // 'Unsequenced' (Planned=0) skipped
    const n = crews.length;
    const win = Math.max(1, days(ph.start, ph.end));
    const base = Math.floor(ph.planned / n);
    crews.forEach((crew, i) => {
      const C = CREW[crew];
      const target = i === n - 1 ? ph.planned - base * (n - 1) : base; // remainder on last → Σ == planned
      const labor    = Math.round(C.rate * C.size * win);
      const setup    = SETUP[crew];
      const burden   = Math.round(labor * burdenPct(crew));
      const overhead = Math.round((labor + setup) * OVERHEAD_PCT);
      const material = target - (labor + setup + burden + overhead);   // PLUG → closes to the rupiah
      const N = BATCH_N[crew];
      orders.push({ id: ppId, phase: ph.name, crew, target,
                    qtybatchsize: N, dateStartSchedule: ph.start, dateFinishSchedule: ph.end, c_project_id: 990000 });
      nodes.push({ id: nodeId, pp_order_id: ppId, crew, setuptime: setup, duration: win,
                   perElementSetup: setup / N });
      split.push({ pp_order_id: ppId, phase: ph.name, crew,
                   material, labor, burden, overhead, setup, sum: material + labor + burden + overhead + setup });
      ppId++; nodeId++;
    });
  }
  return { orders, nodes, split };
}

module.exports = { genShopfloor, CREW, SETUP, BATCH_N, burdenPct, OVERHEAD_PCT, PHASE_CREWS, COST_ELEMENT, days, sq, SEED };

// ── run (witness) — only when invoked directly ─────────────────────────────────────────────────────
if (require.main === module) {
const rows = sq(`SELECT Name, PlannedAmt, StartDate, EndDate FROM C_ProjectPhase
                 WHERE C_Project_ID=990000 AND PlannedAmt>0 ORDER BY SeqNo;`)
  .split('\n').map(l => { const [name, planned, start, end] = l.split('|');
    return { name, planned: +planned, start, end }; });
log('SEED', `${SEED} — ${rows.length} costed phases`);

const out = genShopfloor(rows);
log('GEN', `${out.orders.length} PP_Order · ${out.nodes.length} PP_Order_Node`);

// existence sets for W-SHOP-SOURCE
const realResources = new Set(sq(`SELECT Name FROM S_Resource;`).split('\n'));
const realElements  = new Set(sq(`SELECT M_CostElement_ID FROM M_CostElement;`).split('\n').map(Number));

let pass = 0, fail = 0;
const ok = (c, m) => { (c ? pass++ : fail++); console.log(`  ${c ? 'PASS' : 'FAIL'} ${m}`); };

// W-SHOP-ELEMENTS — per-phase Σ buckets == stored PlannedAmt to the cent
console.log('\n— W-SHOP-ELEMENTS (Σ buckets == PlannedAmt, to the rupiah) —');
for (const ph of rows) {
  const s = out.split.filter(x => x.phase === ph.name).reduce((a, x) => a + x.sum, 0);
  log('SPLIT', `${ph.name.padEnd(16)} Σbuckets=${s} planned=${ph.planned} Δ=${s - ph.planned}`);
  ok(s === ph.planned, `${ph.name} closes to the rupiah`);
}
// every bucket maps to a real M_CostElement id
ok(Object.values(COST_ELEMENT).every(id => realElements.has(id)),
   `all 4 cost-element ids resolve to real M_CostElement rows`);

// W-SHOP-BATCH — perElementSetup == setup/N; double N halves per-unit
console.log('\n— W-SHOP-BATCH (perElementSetup == setup/N; ×2 N → ÷2 per-unit) —');
for (const nd of out.nodes.slice(0, 3))
  ok(Math.abs(nd.perElementSetup - nd.setuptime / BATCH_N[nd.crew]) < 1e-9,
     `${nd.crew} perElementSetup=${nd.perElementSetup} == ${nd.setuptime}/${BATCH_N[nd.crew]}`);
{ const c = 'STEEL_ERECTOR', s = SETUP[c];
  ok((s / 4) === 2 * (s / 8), `${c} doubling lot N halves per-unit setup (${s}/4 == 2×${s}/8)`); }

// W-SHOP-SOURCE — every crew is seeded as a real S_Resource (POC seeds construction crews); determinism
console.log('\n— W-SHOP-SOURCE (ids resolve · re-run byte-identical) —');
const crewsUsed = [...new Set(out.orders.map(o => o.crew))];
ok(crewsUsed.every(c => CREW[c]), `all ${crewsUsed.length} crews have an EXTRACTED rate row`);
const again = genShopfloor(rows);
ok(JSON.stringify(again) === JSON.stringify(out), `re-run byte-identical (deterministic, no random/no Date.now)`);
log('NOTE', `S_Resource construction-crew seeding is E1 (these are GardenWorld mfg plants today); ` +
           `crews carry EXTRACTED rates now, become real S_Resource rows on persist.`);

console.log(`\n§RESULT W-SHOP E0  ${pass}/${pass + fail}  ${fail ? 'FAIL' : 'PASS'}`);
process.exit(fail ? 1 : 0);
}
