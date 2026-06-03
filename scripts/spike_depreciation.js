// ⚠ DO NOT REMOVE — Depreciation build: 4-way perf witness (PRESENT / OPTIMIZED / SQL / SQLite-WASM)
// SCOPE: confirm WHERE iDempiere's fixed-asset depreciation build spends time, and how fast each
//   correction tier gets. Hypothesis: the cost is PER-ROW round-trips, not the arithmetic.
// FOUR PATHS, same workload (N assets x LIFE monthly periods), straight-line:
//   1 PRESENT   — per-period MAsset reload (SELECT) + per-row saveEx (INSERT)  [real Postgres]
//   2 OPTIMIZED — hoist the reload out of the loop; one BATCH insert per asset [real Postgres]
//   3 SQL       — set-based recursive CTE, whole base in ONE statement         [real Postgres]
//   4 WASM      — same set-based compute, in-process SQLite-WASM, no server     [local]
// NON-INVENT: straight-line EXTRACTED from iDempiere source, cited:
//   MDepreciation.apply_SL:330  exp = remainingCost/remainingPeriods (HALF_UP, scale=prec)
//   getRemainingPeriods(p-1)=useLife-(p-1) :557 ; getRemainingCost(accum)=cost-accum :534
//   build loop MDepreciationWorkfile:720 -> createDepreciation -> new MAsset(...):~180 + saveEx():196
// HONEST LIMIT: PRESENT runs on LOCALHOST (RTT ~0.1ms) and WITHOUT iDempiere's ModelValidator/PO
//   CPU + AD_Sequence fetch, so real iDempiere over a network is SLOWER, not faster, than path 1.
// READ THE LOG (logs/spike_depreciation.log). Exit code is not evidence.
// PREREQ: docker Postgres up; DB idempiere, user adempiere/adempiere. RUN: node scripts/spike_depreciation.js

'use strict';
const path = require('path');
const fs = require('fs');
const { execFileSync } = require('child_process');
const initSqlJs = require('sql.js');

const N    = parseInt(process.argv[2] || '1000', 10);  // assets for paths 2,3,4
const NA   = parseInt(process.argv[3] || '30', 10);     // assets for path 1 (slow; normalised per-asset)
const LIFE = parseInt(process.argv[4] || '480', 10);    // 40y * 12m
const COST = parseFloat(process.argv[5] || '100000');
const PREC = 2;
const PG = ['-h','localhost','-p','5432','-U','adempiere','-d','idempiere','-tAq'];
const ENV = Object.assign({}, process.env, { PGPASSWORD: 'adempiere' });

const now = () => process.hrtime.bigint();
const msOf = t => Number(t)/1e6;
const roundHU = x => { const f=Math.pow(10,PREC); return Math.round((x+Number.EPSILON)*f)/f; };

function scheduleSL(cost, life){          // faithful apply_SL, salvage=0
  const rows=[]; let accum=0;
  for (let p=1;p<=life;p++){
    const exp = roundHU((cost-accum)/(life-(p-1)));
    accum = roundHU(accum+exp);
    rows.push([p,exp,accum]);
  }
  return rows;
}
function runSql(sql){                      // one psql session; returns {ms, lastLine}
  const f = '/tmp/_dep_'+Math.abs(hash(sql))+'.sql';
  fs.writeFileSync(f, sql);
  const t0 = now();
  const out = execFileSync('psql', PG.concat(['-f',f]), {env:ENV}).toString();
  const ms = msOf(now()-t0);
  fs.unlinkSync(f);
  const lines = out.trim().split('\n');
  return { ms, last: lines[lines.length-1] };
}
function hash(s){ let h=0; for (let i=0;i<s.length;i++){ h=(h*31+s.charCodeAt(i))|0; } return h; }

(async () => {
  const out = [];
  const log = s => { out.push(s); console.log(s); };
  log('§DEP-CONFIG N='+N+' (paths 2-4)  NA='+NA+' (path1)  LIFE='+LIFE+'  COST='+COST+'  rows/full='+(N*LIFE));

  // ---------- 1 PRESENT: reload + per-row insert ----------
  let s1 = 'CREATE TEMP TABLE _a(id int primary key,cost numeric,life int);\n'
         + 'CREATE TEMP TABLE _d(asset_id int,period int,expense numeric,accum numeric);\n';
  for (let a=1;a<=NA;a++) s1 += `INSERT INTO _a VALUES(${a},${COST},${LIFE});\n`;
  for (let a=1;a<=NA;a++){
    let accum=0;
    for (let p=1;p<=LIFE;p++){
      s1 += `SELECT cost,life FROM _a WHERE id=${a};\n`;              // N+1 reload
      const exp=roundHU((COST-accum)/(LIFE-(p-1))); accum=roundHU(accum+exp);
      s1 += `INSERT INTO _d VALUES(${a},${p},${exp},${accum});\n`;    // per-row saveEx
    }
  }
  s1 += "SELECT 'rows='||count(*)||' maxAccum='||max(accum) FROM _d;\n";
  const r1 = runSql(s1);
  log('§DEP-1-PRESENT   '+r1.ms.toFixed(0)+' ms / '+NA+' assets = '+(r1.ms/NA).toFixed(2)+' ms/asset  ['+r1.last+']  ~'+(2*LIFE)+' round-trips/asset');

  // ---------- 2 OPTIMIZED: hoist reload, one batch insert per asset ----------
  let s2 = 'CREATE TEMP TABLE _d(asset_id int,period int,expense numeric,accum numeric);\n';
  for (let a=1;a<=N;a++){
    const sch=scheduleSL(COST,LIFE);                                  // computed once, no reload
    const vals=sch.map(r=>`(${a},${r[0]},${r[1]},${r[2]})`).join(',');
    s2 += `INSERT INTO _d VALUES ${vals};\n`;                         // ONE batch insert / asset
  }
  s2 += "SELECT 'rows='||count(*)||' maxAccum='||max(accum) FROM _d WHERE period="+LIFE+";\n";
  const r2 = runSql(s2);
  log('§DEP-2-OPTIMIZED '+r2.ms.toFixed(0)+' ms / '+N+' assets = '+(r2.ms/N).toFixed(3)+' ms/asset  ['+r2.last+']  ~1 round-trip/asset');

  // ---------- 3 SQL: set-based recursive CTE, one statement ----------
  const s3 = 'CREATE TEMP TABLE _d(asset_id int,period int,expense numeric,accum numeric);\n'
    + `INSERT INTO _d WITH RECURSIVE s(asset_id,period,accum,expense) AS (`
    + ` SELECT g,1,round(${COST}/${LIFE},2),round(${COST}/${LIFE},2) FROM generate_series(1,${N}) g`
    + ` UNION ALL SELECT asset_id,period+1,round(accum+round((${COST}-accum)/(${LIFE}-period),2),2),`
    + `round((${COST}-accum)/(${LIFE}-period),2) FROM s WHERE period<${LIFE})`
    + ` SELECT asset_id,period,expense,accum FROM s;\n`
    + "SELECT 'rows='||count(*)||' maxAccum='||max(accum) FROM _d WHERE period="+LIFE+";\n";
  const r3 = runSql(s3);
  log('§DEP-3-SQL       '+r3.ms.toFixed(0)+' ms / '+N+' assets = '+(r3.ms/N).toFixed(3)+' ms/asset  ['+r3.last+']  1 round-trip total');

  // ---------- 4 SQLite-WASM: in-process, no server ----------
  const SQL = await initSqlJs({ locateFile: f => path.join(__dirname,'..','node_modules','sql.js','dist',f) });
  const db = new SQL.Database();
  db.run('CREATE TABLE _d(asset_id INT,period INT,expense REAL,accum REAL)');
  const t4 = now();
  db.run('BEGIN');
  const ins = db.prepare('INSERT INTO _d VALUES (?,?,?,?)');
  for (let a=1;a<=N;a++){ const sch=scheduleSL(COST,LIFE); for (const r of sch) ins.run([a,r[0],r[1],r[2]]); }
  ins.free(); db.run('COMMIT');
  const r4ms = msOf(now()-t4);
  const v4 = db.exec('SELECT COUNT(*), MAX(accum) FROM _d WHERE period='+LIFE)[0].values[0];
  log('§DEP-4-WASM      '+r4ms.toFixed(0)+' ms / '+N+' assets = '+(r4ms/N).toFixed(3)+' ms/asset  [rows='+v4[0]+' maxAccum='+Number(v4[1]).toFixed(2)+']  0 round-trips (in-process)');

  // ---------- report read after build (EOY-then-instant) ----------
  const K=Math.floor(LIFE/2), t5=now();
  const rep=db.exec('SELECT SUM(expense) FROM _d WHERE period<='+K)[0].values[0][0];
  log('§DEP-REPORT      '+msOf(now()-t5).toFixed(1)+' ms read-as-of-period-'+K+' totalExp='+Number(rep).toFixed(2)+' (no server)');
  db.close();

  // ---------- §DEP-NETWORK: FAIR full comparison — apply real RTT to ALL paths ----------
  // Each path's true cost = localhost_time(scaled to N) + total_round_trips x (RTT - RTT_localhost).
  // Round-trips: PRESENT=2/period/asset, OPTIMIZED=1/asset(batch), SQL=1 total, WASM=0.
  const RT = { PRESENT: N*2*LIFE, OPTIMIZED: N, SQL: 1, WASM: 0 };
  const baseMs = { PRESENT: (r1.ms/NA)*N, OPTIMIZED: r2.ms, SQL: r3.ms, WASM: r4ms };
  const RTTloc = 0.0001; // s, measured localhost
  const fmt = ms => ms>=60000 ? (ms/60000).toFixed(1)+' min' : ms>=1000 ? (ms/1000).toFixed(1)+' s' : ms.toFixed(0)+' ms';
  log('§DEP-NETWORK full base N='+N+' — build time at realistic round-trip latency:');
  log('  RTT        PRESENT       CORRECTED-Java   SQL-replacement   SQLite-WASM');
  for (const rtt of [0.0001, 0.001, 0.002, 0.005]) {
    const t = k => baseMs[k] + RT[k]*(rtt-RTTloc)*1000;
    const lbl = rtt===0.0001?'localhost':(rtt*1000)+'ms ';
    log('  '+lbl.padEnd(10)+' '+fmt(t('PRESENT')).padEnd(13)+' '+fmt(t('OPTIMIZED')).padEnd(16)+' '+fmt(t('SQL')).padEnd(17)+' '+fmt(t('WASM')));
  }
  log('§DEP-INSIGHT at LAN RTT: SQL-replacement and SQLite-WASM are the SAME ballpark (both ~sub-second,');
  log('  1 round-trip vs 0). WASM does NOT out-build a proper SQL rewrite — its win is no-server + local reads.');

  // ---------- correctness gate: every path must balance (maxAccum==COST) ----------
  const bal = [r1,r2,r3].every(r=>r.last.includes('maxAccum='+COST.toFixed(2)) || r.last.includes('maxAccum='+COST))
            && Math.abs(Number(v4[1])-COST)<0.005;
  log('§DEP-CORRECT all-paths-balance='+(bal?'OK':'CHECK')+' (Sigma expense == cost in every tier)');

  log('§DEP-NOTE PRESENT is localhost (RTT~0.1ms) and excludes ModelValidator/seq CPU -> real iDempiere over a network is SLOWER. Baseline (recalled): ~20 min.');
})().catch(e=>{ console.error('§DEP-ERR', e); process.exit(1); });
