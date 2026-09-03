#!/usr/bin/env node
// Implementing TM_S4_SHOPFLOOR_BUILD.md §STAGES E1 — Witness: W-SHOP-PERSIST (extends W-SHOP-ELEMENTS/SOURCE)
// PERSIST the deterministic shopfloor into the CANONICAL seed using the EXTRACTED libero CONVENTION — no invented
// model. Real homes (verified in ~/idempiere-dev-setup/idempiere/.../org/eevolution/model):
//   · S_Resource         — construction crews as Work-Center rows (table EXISTS; conventional cols)
//   · PP_Order           — one header per (phase×crew), linked C_Project_ID=990000 (table EXISTS)
//   · PP_Order_Node      — operations: SetupTime/Duration + DateStartSchedule↔DateStart (libero X_PP_Order_Node)
//   · PP_Order_Cost      — the per-order × M_CostElement split: CumulatedAmt per real cost element (X_PP_Order_Cost)
// ONLY the VALUES are generated (fixed seed, no random/no Date.now). Works on a COPY; --write swaps the canonical in
// only when every witness passes. Idempotent (re-run safe — clears the generated band first). Read the §-log.
'use strict';
const fs = require('fs');
const path = require('path');
const os = require('os');
const { execFileSync } = require('child_process');
const G = require('./gen_mfg_shopfloor.js');

const SEED  = process.argv[2] && !process.argv[2].startsWith('--') ? process.argv[2]
            : path.join(os.homedir(), 'bim-ootb/erp/ad_seed.db');
const WRITE = process.argv.includes('--write');
const WORK  = SEED + '.mfgbake';
const log = (t, m) => console.log(`§${t} ${m}`);
const sql = (db, q) => execFileSync('sqlite3', [db, q], { encoding: 'utf8' }).trim();
const q1  = (db, q) => sql(db, q).split('\n')[0];
const esc = s => `'${String(s).replace(/'/g, "''")}'`;

// generated id bands (deterministic)
const CREW_ID0 = 990100, PPO_ID0 = 9900000, NODE_ID0 = 9900000, COST_ID0 = 9901000;
const COSTEL = G.COST_ELEMENT;                                  // {material:100,labor:105,burden:50000,overhead:50001}
const SETUP_MIN = c => Math.round(G.SETUP[c] / 10);             // generated setup time (min), keyed on crew

fs.copyFileSync(SEED, WORK);
log('COPY', `${SEED} → ${WORK} (canonical untouched until --write + all-pass)`);

// ── extract the real anchors (no invention — pull from the seed) ──────────────────────────────────
const phases = sql(WORK, `SELECT Name,PlannedAmt,StartDate,EndDate FROM C_ProjectPhase
                          WHERE C_Project_ID=990000 AND PlannedAmt>0 ORDER BY SeqNo;`)
  .split('\n').map(l => { const [name, planned, start, end] = l.split('|'); return { name, planned: +planned, start, end }; });
const AD_CLIENT = q1(WORK, `SELECT AD_Client_ID FROM C_Project WHERE C_Project_ID=990000;`) || '0';
const AD_ORG    = q1(WORK, `SELECT AD_Org_ID    FROM C_Project WHERE C_Project_ID=990000;`) || '0';
const WC_TYPE   = q1(WORK, `SELECT S_ResourceType_ID FROM S_ResourceType WHERE Name LIKE '%Work Center%' LIMIT 1;`) || q1(WORK, `SELECT S_ResourceType_ID FROM S_ResourceType LIMIT 1;`);
const ACCTSCH   = q1(WORK, `SELECT C_AcctSchema_ID FROM C_AcctSchema LIMIT 1;`) || '0';
const COSTTYPE  = q1(WORK, `SELECT M_CostType_ID FROM M_CostType LIMIT 1;`) || '0';
log('ANCHOR', `client=${AD_CLIENT} org=${AD_ORG} wcType=${WC_TYPE} acctSch=${ACCTSCH} costType=${COSTTYPE}`);

const out = G.genShopfloor(phases);
const crews = [...new Set(out.orders.map(o => o.crew))].sort();
const crewId = {}; crews.forEach((c, i) => crewId[c] = CREW_ID0 + i);

// ── build the SQL (faithful columns; idempotent clear of the generated band) ──────────────────────
const S = [];
S.push(`PRAGMA foreign_keys=OFF;`);
// clear prior bake (re-run safe)
S.push(`DELETE FROM PP_Order      WHERE PP_Order_ID  >= ${PPO_ID0};`);
S.push(`DELETE FROM S_Resource    WHERE S_Resource_ID>= ${CREW_ID0};`);
S.push(`DROP TABLE IF EXISTS PP_Order_Node;`);
S.push(`DROP TABLE IF EXISTS PP_Order_Cost;`);
// faithful libero tables (cols + types from X_PP_Order_Node / X_PP_Order_Cost)
S.push(`CREATE TABLE PP_Order_Node (PP_Order_Node_ID INTEGER PRIMARY KEY, AD_Client_ID NUMERIC, AD_Org_ID NUMERIC,
  IsActive TEXT, Created TEXT, CreatedBy NUMERIC, Updated TEXT, UpdatedBy NUMERIC, PP_Order_ID NUMERIC,
  S_Resource_ID NUMERIC, Value TEXT, Name TEXT, Description TEXT, SetupTime NUMERIC, SetupTimeReal NUMERIC,
  Duration NUMERIC, DurationReal NUMERIC, QueuingTime NUMERIC, MovingTime NUMERIC, WaitingTime NUMERIC, Cost NUMERIC,
  Yield NUMERIC, QtyDelivered NUMERIC, DateStartSchedule TEXT, DateFinishSchedule TEXT, DateStart TEXT,
  DateFinish TEXT, DocStatus TEXT, IsGenerated TEXT, PP_Order_Node_UU TEXT);`);
S.push(`CREATE TABLE PP_Order_Cost (PP_Order_Cost_ID INTEGER PRIMARY KEY, AD_Client_ID NUMERIC, AD_Org_ID NUMERIC,
  IsActive TEXT, Created TEXT, CreatedBy NUMERIC, Updated TEXT, UpdatedBy NUMERIC, PP_Order_ID NUMERIC,
  M_CostElement_ID NUMERIC, M_CostType_ID NUMERIC, C_AcctSchema_ID NUMERIC, CostingMethod TEXT, M_Product_ID NUMERIC,
  CurrentCostPrice NUMERIC, CurrentQty NUMERIC, CumulatedAmt NUMERIC, CumulatedQty NUMERIC, IsGenerated TEXT,
  PP_Order_Cost_UU TEXT);`);

const NOW = phases[0].start;                                    // deterministic stamp (no Date.now) — phase-0 start
// crews → S_Resource (Work-Center rows; conventional cols; ismanufacturingresource='Y')
crews.forEach(c => S.push(`INSERT INTO S_Resource
  (S_Resource_ID,AD_Client_ID,AD_Org_ID,IsActive,Created,CreatedBy,Updated,UpdatedBy,Value,Name,
   S_ResourceType_ID,IsAvailable,IsManufacturingResource,ManufacturingResourceType,PercentUtilization)
  VALUES (${crewId[c]},${AD_CLIENT},${AD_ORG},'Y',${esc(NOW)},100,${esc(NOW)},100,${esc(c)},
   ${esc(c.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, m => m.toUpperCase()))},${WC_TYPE},'Y','Y','WC',100);`));

// orders → PP_Order ; nodes → PP_Order_Node ; split → PP_Order_Cost
const scenario = ph => /Substructure|Superstructure/.test(ph) ? 'ETO custom build' : 'MTO repetitive lot';
out.orders.forEach((o, i) => {
  const nd = out.nodes[i], sp = out.split[i];
  const laborAll = sp.labor + sp.setup;                        // setup MONEY folds into the Labor cost element
  S.push(`INSERT INTO PP_Order
    (PP_Order_ID,AD_Client_ID,AD_Org_ID,IsActive,Created,CreatedBy,Updated,UpdatedBy,DocumentNo,C_Project_ID,
     S_Resource_ID,QtyBatchSize,QtyOrdered,QtyEntered,DateStartSchedule,DateFinishSchedule,DocStatus,DocAction,
     ScheduleType,Description,Processed,IsSOTrx)
    VALUES (${o.id},${AD_CLIENT},${AD_ORG},'Y',${esc(NOW)},100,${esc(NOW)},100,${esc('MO-' + o.id)},990000,
     ${crewId[o.crew]},${o.qtybatchsize},${o.qtybatchsize},${o.qtybatchsize},${esc(o.dateStartSchedule)},
     ${esc(o.dateFinishSchedule)},'DR','CO','B',${esc(o.phase + ' — ' + o.crew + ' (' + scenario(o.phase) + ')')},'N','N');`);
  S.push(`INSERT INTO PP_Order_Node
    (PP_Order_Node_ID,AD_Client_ID,AD_Org_ID,IsActive,Created,CreatedBy,Updated,UpdatedBy,PP_Order_ID,S_Resource_ID,
     Value,Name,SetupTime,Duration,QueuingTime,MovingTime,WaitingTime,Cost,Yield,QtyDelivered,
     DateStartSchedule,DateFinishSchedule,DocStatus,IsGenerated)
    VALUES (${nd.id},${AD_CLIENT},${AD_ORG},'Y',${esc(NOW)},100,${esc(NOW)},100,${o.id},${crewId[o.crew]},
     ${esc('OP-' + nd.id)},${esc(o.crew + ' op')},${SETUP_MIN(o.crew)},${nd.duration},0,0,0,${sp.sum},100,0,
     ${esc(o.dateStartSchedule)},${esc(o.dateFinishSchedule)},'DR','Y');`);
  const buckets = [['material', sp.material, COSTEL.material], ['labor', laborAll, COSTEL.labor],
                   ['burden', sp.burden, COSTEL.burden], ['overhead', sp.overhead, COSTEL.overhead]];
  buckets.forEach(([nm, amt, ce], j) => S.push(`INSERT INTO PP_Order_Cost
    (PP_Order_Cost_ID,AD_Client_ID,AD_Org_ID,IsActive,Created,CreatedBy,Updated,UpdatedBy,PP_Order_ID,M_CostElement_ID,
     M_CostType_ID,C_AcctSchema_ID,CostingMethod,M_Product_ID,CurrentCostPrice,CurrentQty,CumulatedAmt,CumulatedQty,IsGenerated)
    VALUES (${COST_ID0 + i * 4 + j},${AD_CLIENT},${AD_ORG},'Y',${esc(NOW)},100,${esc(NOW)},100,${o.id},${ce},
     ${COSTTYPE},${ACCTSCH},'S',0,${(amt / o.qtybatchsize).toFixed(2)},${o.qtybatchsize},${amt},${o.qtybatchsize},'Y');`));
});
sql(WORK, S.join('\n'));
log('APPLY', `${crews.length} crews · ${out.orders.length} PP_Order · ${out.nodes.length} nodes · ${out.orders.length * 4} PP_Order_Cost rows`);

// ── WITNESS on the working copy (no browser) ──────────────────────────────────────────────────────
let pass = 0, fail = 0;
const ok = (c, m) => { (c ? pass++ : fail++); console.log(`  ${c ? 'PASS' : 'FAIL'} ${m}`); };

console.log('\n— W-SHOP-PERSIST —');
ok(+q1(WORK, `SELECT count(*) FROM PP_Order WHERE C_Project_ID=990000 AND PP_Order_ID>=${PPO_ID0};`) === out.orders.length,
   `${out.orders.length} PP_Orders linked to C_Project 990000 (Zoom-Across where-used)`);
// every order's resource resolves to a real (seeded) S_Resource
ok(+q1(WORK, `SELECT count(*) FROM PP_Order o WHERE o.PP_Order_ID>=${PPO_ID0}
      AND NOT EXISTS (SELECT 1 FROM S_Resource r WHERE r.S_Resource_ID=o.S_Resource_ID);`) === 0,
   `every PP_Order.S_Resource_ID resolves to a real S_Resource row`);
// every cost row maps to a real M_CostElement
ok(+q1(WORK, `SELECT count(*) FROM PP_Order_Cost c WHERE c.PP_Order_Cost_ID>=${COST_ID0}
      AND NOT EXISTS (SELECT 1 FROM M_CostElement e WHERE e.M_CostElement_ID=c.M_CostElement_ID);`) === 0,
   `every PP_Order_Cost.M_CostElement_ID resolves to a real M_CostElement row`);
// per-phase Σ CumulatedAmt == stored PlannedAmt to the rupiah (the falsifier)
console.log('  per-phase Σ PP_Order_Cost.CumulatedAmt == PlannedAmt:');
for (const ph of phases) {
  const s = +q1(WORK, `SELECT COALESCE(SUM(c.CumulatedAmt),0) FROM PP_Order_Cost c
     JOIN PP_Order o ON o.PP_Order_ID=c.PP_Order_ID
     WHERE o.PP_Order_ID>=${PPO_ID0} AND o.Description LIKE ${esc(ph.name + ' —%')};`);
  log('PERSIST', `${ph.name.padEnd(16)} Σ=${s} planned=${ph.planned} Δ=${s - ph.planned}`);
  ok(s === ph.planned, `${ph.name} persisted cost closes to the rupiah`);
}
// crews read as construction work-centers, not "Fertilizer Plant"
ok(q1(WORK, `SELECT Name FROM S_Resource WHERE S_Resource_ID=${crewId[crews[0]]};`).indexOf('Plant') === -1,
   `crews read as construction Work-Centers (e.g. "${q1(WORK, `SELECT Name FROM S_Resource WHERE S_Resource_ID=${crewId[crews[0]]};`)}")`);

console.log(`\n§RESULT W-SHOP-PERSIST  ${pass}/${pass + fail}  ${fail ? 'FAIL' : 'PASS'}`);
fs.writeFileSync(path.join(__dirname, 'bake_mfg_shopfloor.log'),
  `W-SHOP-PERSIST ${pass}/${pass + fail} ${fail ? 'FAIL' : 'PASS'}\n`);

if (fail) { fs.unlinkSync(WORK); log('ABORT', 'witness FAILED — canonical untouched, work copy discarded'); process.exit(1); }
if (WRITE) {
  fs.copyFileSync(SEED, SEED + '.prebake');
  fs.renameSync(WORK, SEED);
  log('WROTE', `${SEED} (backup at ${SEED}.prebake)`);
} else {
  fs.unlinkSync(WORK);
  log('DRY', 'all-pass — re-run with --write to persist into the canonical seed');
}
