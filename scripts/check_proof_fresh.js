#!/usr/bin/env node
/*
 * check_proof_fresh.js — read the persisted drop-fidelity proof and report whether it STILL HOLDS, without
 * re-running the witness. This is the Log-Mandate answer to "did the test drift?": a proof is only valid for the
 * exact (catalog, oracle output.db) it ran against, so we re-hash those inputs and compare to what the proof
 * recorded. The drift that bit DX (catalog rebuilt 4h after the last 'confirmed', witness never re-run) becomes a
 * loud STALE here instead of a silent stale prose claim.
 *
 * Reads  logs/PROOF_drop_vs_compiler__*.json  (written by witness_drop_vs_compiler.js).
 * Exit 0 = every proof FRESH + GREEN; 1 = some STALE or RED; 2 = no proof on disk (never witnessed).
 *
 * ANTI-CHEAT: the proof carries its tolerance (pos_tol_mm) and oracle doctrine. If a future run loosens the
 * tolerance or swaps the oracle to make RED look GREEN, it shows up as a git diff in the sidecar — bypass is
 * auditable, never silent.
 */
'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const ROOT = path.join(__dirname, '..');
const LOGS = path.join(ROOT, 'logs');
const sha12 = (rel) => { try { return crypto.createHash('sha256').update(fs.readFileSync(path.join(ROOT, rel))).digest('hex').slice(0, 12); } catch { return 'MISSING'; } };

const files = fs.existsSync(LOGS) ? fs.readdirSync(LOGS).filter(f => /^PROOF_drop_vs_compiler__.*\.json$/.test(f)) : [];
if (!files.length) {
  console.log('§PROOF-FRESHNESS: NO sidecar on disk — the drop witness was never persisted. Run scripts/witness_drop_vs_compiler.js.');
  process.exit(2);
}

let bad = 0;
for (const f of files.sort()) {
  const p = JSON.parse(fs.readFileSync(path.join(LOGS, f), 'utf8'));
  const drift = ['catalog', 'oracle']
    .map(k => ({ k, rec: p.inputs[k], now: sha12(p.inputs[k].path) }))
    .filter(x => x.now !== x.rec.sha);
  const fresh = drift.length === 0;
  const state = !fresh ? 'STALE — input changed, RE-RUN' : (p.verdict === 'GREEN' ? 'FRESH ✓ GREEN' : 'FRESH — but verdict RED');
  console.log(`\n${f}`);
  console.log(`  proven ${p.stamp}  oracle=${p.inputs.oracle.path}  tol=${p.pos_tol_mm}mm  → ${state}`);
  (p.candidates || []).forEach(c => console.log(`    ${c.id.padEnd(20)} worst=${String(c.worst_mm).padStart(6)}mm  ${c.pass ? 'PASS' : 'FAIL'}`));
  drift.forEach(x => console.log(`    ⚠ ${x.k} CHANGED since proof: ${x.rec.path}  (proof ${x.rec.sha} ≠ current ${x.now})`));
  if (!fresh || p.verdict !== 'GREEN') bad++;
}
console.log(bad
  ? `\n§PROOF-FRESHNESS RED: ${bad}/${files.length} proof(s) STALE or RED — drop fidelity is NOT currently guaranteed; do NOT claim it's confirmed.`
  : `\n§PROOF-FRESHNESS GREEN: all ${files.length} proofs FRESH + GREEN against the current catalog + compiler output.db.`);
process.exit(bad ? 1 : 0);
