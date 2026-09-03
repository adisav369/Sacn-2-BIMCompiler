#!/usr/bin/env node
// measure_localhost_bench.js — W-IDMP-LOCALHOST (storage-primitive leg)
// Bake the REAL same-machine sql.js-vs-Postgres numbers (already measured in build/erp/bench_oplog_pg.log
// on this box) into build/erp/bench_localhost.json, so the bench_suite "Single-station" card renders
// MEASURED data — not estimates. Also stamps this machine's spec (for the "measured here" badge) and the
// network-RTT model the [ON NETWORK] <> [LOCALHOST] toggle layers on top of the server side.
//
// NON-INVENT: storage numbers are parsed from the measured log. The iDempiere APP-layer op (callouts +
// ModelValidator + posting) is NOT measured here — it needs a booted local iDempiere — so it is emitted
// with status:'pending' and timed LIVE by the page only when a local iDempiere REST actually answers.
// Cold-start / JVM memory are iDempiere's DOCUMENTED figures, labelled measured:false.
// Run: node scripts/measure_localhost_bench.js
const fs = require('fs');
const os = require('os');
const path = require('path');

const REPO = '/home/red1/bim-compiler';
const LOG = path.join(REPO, 'build/erp/bench_oplog_pg.log');
const OUT = path.join(REPO, 'build/erp/bench_localhost.json');

function num(re, txt) { const m = txt.match(re); return m ? parseFloat(m[1]) : null; }

let log = '';
try { log = fs.readFileSync(LOG, 'utf8'); } catch (e) { console.error('missing ' + LOG); process.exit(1); }

// parse the measured §BENCH lines
const sqljs_per_op   = num(/sqljs[^\n]*per_op_ms=([\d.]+)/, log);
const pg_onetxn_per_op = num(/postgres engine=[^\n]*mode=one-txn[^\n]*per_op_ms=([\d.]+)/, log);
const pg_percommit   = num(/postgres mode=per-commit[^\n]*avg_ms=([\d.]+)/, log);
const opsN           = num(/N=(\d+) ops/, log) || 1000;

const cpu = (os.cpus()[0] || {}).model || 'unknown CPU';
const machine = {
  cpu: cpu.replace(/\s+/g, ' ').trim(),
  cores: os.cpus().length,
  mem_gb: +(os.totalmem() / 1073741824).toFixed(0),
  platform: os.platform() + ' ' + os.release(),
  label: cpu.split('@')[0].trim() + ' · ' + os.cpus().length + 'c · ' + Math.round(os.totalmem() / 1073741824) + 'GB',
};

const facts = {
  witness: 'W-IDMP-LOCALHOST',
  scope: 'storage primitive MEASURED on this box; app-layer pending live iDempiere',
  source_log: 'build/erp/bench_oplog_pg.log',
  machine,
  ops_n: opsN,
  // ── MEASURED: the storage primitive (1000 ops, one atomic commit) ──
  storage: {
    note: opsN + ' ops, one atomic group commit — storage only (no callouts/posting)',
    ours:  { engine: 'sql.js (browser, in-mem, +sha256 chain)', per_op_ms: sqljs_per_op, network: false, measured: true },
    them:  { engine: 'PostgreSQL 15 (durable WAL+fsync, ACID)', per_op_ms_onetxn: pg_onetxn_per_op,
             per_op_ms_durable: pg_percommit, server: true, measured: true },
    teaches: 'On one box with no network, durable Postgres is FASTER per write than sql.js+hash-chain — ' +
             'the server is genuinely good at storage. We trade that speed for zero-server + a signed log. ' +
             'This is where our lead is smallest; we say so.',
  },
  // ── network model the toggle layers onto the SERVER side only (ours has no server) ──
  rtt_presets_ms: { LAN: 1, office: 20, cloud: 80, branchWAN: 200 },
  rtt_default_ms: 20,
  network_teaches: 'Add a round-trip per server op. Our column does not move — we have no server to reach. ' +
                   'That flat line is the point: the network cost is entirely the server’s.',
  // ── NOT measured here (honest) ──
  app_layer: { status: 'pending', op: 'create + complete an order (callouts + ModelValidator + posting)',
               note: 'timed LIVE by the page when a local iDempiere REST answers; not booted at bake time' },
  cold_start: { ours_s_note: 'open tab + fetch ~27MB seed = seconds', them_s_min: 120, them_s_max: 300,
                them_measured: false, source: 'documented iDempiere JVM+OSGi+PG warm-up' },
  memory: { them_mb: 512, them_measured: false, source: 'documented JVM heap baseline before first user' },
};

fs.writeFileSync(OUT, JSON.stringify(facts, null, 2));

console.log('\n═══ W-IDMP-LOCALHOST — same-machine storage primitive (measured) ═══\n');
console.log('  §LOCALHOST machine=' + machine.label);
console.log('  §LOCALHOST storage ours_sqljs=' + sqljs_per_op + 'ms/op  them_pg_onetxn=' + pg_onetxn_per_op +
            'ms/op  them_pg_durable=' + pg_percommit + 'ms/op  (N=' + opsN + ')');
console.log('  §LOCALHOST honest: Postgres faster locally (' + (sqljs_per_op / pg_percommit).toFixed(0) +
            '× on durable per-commit) — named, not hidden');
console.log('  §LOCALHOST app-layer=pending (needs booted iDempiere; page times it live if reachable)');
console.log('  §LOCALHOST wrote ' + OUT + '\n');
