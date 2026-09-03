#!/usr/bin/env node
/* test_persist_slice.js — §B-2 / Phase 2 slice B (disposable-host persistence) — Witness: W-PERSIST-SLICE
 *
 * Implementing prompts/BACKEND_SUBSTRATE_LANE.md §B-2 (= prompts/ERP_SUBSTRATE_INTEGRATION.md Phase 2
 * candidate B). The user picked slice A first (period-close in-app, PR #197); this is slice B.
 *
 * THE SLICE (from the spec): "the app's op-log spills to a SIGNED replica the user OWNS, recoverable on
 * a fresh device." Concretely: from the live ERP surface, EXPORT+SIGN the op-log snapshot to a user
 * channel; on a FRESH load, FETCH+REPLAY+VERIFY (recompute tip == signed tip). Witness whitebox first.
 *
 * Why this is NOT already W-REPLICA: the frozen W-REPLICA witness (test_kernel_replica.js) proves the
 * read-replica/disposable-host story on the POC's SYNTHETIC ops (CREATE/COMPLETE/PAY) signed by the
 * pinned DEMO controller key. Slice B is the REAL product story and reuses TWO Phase-0/1 results:
 *   - the LIVE app's real double-entry POST ops (the §INTEG-POSTINGS shape), and
 *   - the APP signer erp/erp_signer.js (§INTEG-SIGNER-REUSE) — the key the USER owns, custody at the
 *     edge — NOT erp_snapshot_sign's pinned demo key.
 * It composes the FROZEN substrate (erp_replica_client.replayAndVerify for the fetch+replay, the
 * canonical kernel for determinism, erp_period_close.foldBalances for the books) — no engine re-open.
 *
 * Issue PROVED/DISPROVED — can a ZERO-STATE fresh device, holding ONLY the user's signed snapshot + the
 * user's PUBLIC key (carried out-of-band), reconstruct (a) the EXACT chain tip and (b) the EXACT books?
 * Falsifier: if recovery drifts, computedTip != signedTip (S2) or balances != live (S3, maxDiff>0c).
 * A tamperer with no private key cannot forge a snapshot the fresh device accepts (S5).
 *
 * Read the log (exit code is not evidence). Determinism: all ts are recorded INPUTS (no Date.now()),
 * balances are INTEGER CENTS.
 *
 * Run: node scripts/test_persist_slice.js 2>&1 | tee build/erp/test_persist_slice.log
 */
'use strict';
if (typeof global.crypto === 'undefined') { global.crypto = require('crypto').webcrypto; }
var path = require('path'), fs = require('fs'), http = require('http');
var initSqlJs = require('sql.js');

var KERNEL = path.join(process.env.HOME, 'bim-compiler', 'build', 'erp', 'kernel_ops.js'); // reconciled canonical
var SIGNER = path.join(process.env.HOME, 'bim-ootb', 'erp', 'erp_signer.js');               // the REAL app signer
var PC     = require(path.join(__dirname, '..', 'build', 'erp', 'erp_period_close.js'));
var RC     = require(path.join(__dirname, '..', 'build', 'erp', 'erp_replica_client.js'));   // FROZEN read side

function loadGlobalModule(p) { global.window = global.window || {}; delete require.cache[require.resolve(p)]; require(p); }
function freshKernel() { delete require.cache[require.resolve(KERNEL)]; require(KERNEL); return global.window.KernelOps; }

function cents(n) { return Math.round(Number(n || 0) * 100); }

var fail = 0;
function check(name, cond, detail) {
  console.log((cond ? '   🟢 ' : '   🔴 ') + name + (detail ? ' — ' + detail : ''));
  if (!cond) fail++;
}

// ── the LIVE op stream the app emits (real double-entry POST ops — the §INTEG-POSTINGS shape) ────────
function postOp(table, id, lines) { return { table: table, id: id, lines: lines }; }
var LIVE = [
  { type: 'CREATE_DOCUMENT', params: { table: 'C_Invoice', source_id: 5001, doc_status: 'CO' } }, // no lines
  { type: 'POST', params: postOp('C_Invoice', 109, [
      { account_id: '101', role: 'AR',      amtacctdr: 434.13, amtacctcr: 0 },
      { account_id: '400', role: 'Revenue', amtacctdr: 0,      amtacctcr: 434.13 } ]) },
  { type: 'POST', params: postOp('C_Payment', 77, [
      { account_id: '108', role: 'Cash', amtacctdr: 434.13, amtacctcr: 0 },
      { account_id: '101', role: 'AR',   amtacctdr: 0,      amtacctcr: 434.13 } ]) },
  { type: 'POST', params: postOp('C_Invoice', 110, [
      { account_id: '101', role: 'AR',      amtacctdr: 1200.50, amtacctcr: 0 },
      { account_id: '400', role: 'Revenue', amtacctdr: 0,       amtacctcr: 1200.50 } ]) },
  { type: 'POST', params: postOp('C_Payment', 78, [
      { account_id: '108', role: 'Cash', amtacctdr: 1200.50, amtacctcr: 0 },
      { account_id: '101', role: 'AR',   amtacctdr: 0,       amtacctcr: 1200.50 } ]) },
];
// hand-computed expected books (net per account, cents) — the operator's real Fact_Acct.
function expectedBooks() {
  var b = {};
  LIVE.forEach(function (o) { if (o.type !== 'POST') return;
    o.params.lines.forEach(function (l) { b[l.account_id] = (b[l.account_id] || 0) + cents(l.amtacctdr) - cents(l.amtacctcr); }); });
  return b;
}

// EXPORT (the WRITE side of the slice) — select the sealed rows + sign the chain head with the user's
// app signer. Mirrors gen_replica_snapshot.js row-select, but signs with the APP signer, not the demo
// key. Kept inline (not a new module) so the UI lane chooses the export surface later — KISS.
async function exportSignedSnapshot(db, tip, len, appSigner) {
  var rows = db.exec('SELECT id, op_uuid, timestamp, op_type, parameters, input_guids, output_guid FROM kernel_ops ORDER BY id')[0].values
    .map(function (r) { return { seq: r[0], op_uuid: r[1], timestamp: r[2], op_type: r[3], parameters: r[4], input_guids: r[5], output_guid: r[6] }; });
  var sig = await appSigner.sign(tip);   // ECDSA P-256 over the tip hex (the W-SIGN scheme)
  return { schema: 'erp-replica/v1', len: len, tip: tip, sig: sig, alg: 'ES256', signed_by: 'edge:app-signer', ops: rows };
}

function serve(port, jsonStr) {
  var s = http.createServer(function (req, res) {
    if (req.url.indexOf('/relay_snapshot.json') === 0) { res.setHeader('Content-Type', 'application/json'); res.end(jsonStr); }
    else { res.statusCode = 404; res.end(); }
  });
  return new Promise(function (r) { s.listen(port, function () { r(s); }); });
}

(async function () {
  console.log('═══ §B-2 W-PERSIST-SLICE — disposable-host persistence: export+sign → fresh-device recover ═══\n');
  if (typeof fetch === 'undefined') { console.log('   🔴 need Node 18+ (global fetch)'); process.exit(1); }
  var SQL = await initSqlJs();

  // ── the user's edge key (the key they OWN) — reuse the REAL app signer, NOT the demo pinned key ────
  loadGlobalModule(SIGNER);
  var ErpSigner = global.window.ErpSigner;
  check('app signer module loaded (erp/erp_signer.js)', !!(ErpSigner && ErpSigner.makeSigner && ErpSigner.mintKeypair), 'reuse §INTEG-SIGNER-REUSE');
  var kp = await ErpSigner.mintKeypair();          // private key non-extractable (custody at the edge)
  var appSigner = ErpSigner.makeSigner(kp);
  // the SHAREABLE verifier the user carries out-of-band to a fresh device (public key only, JWK).
  var pubJwk = await crypto.subtle.exportKey('jwk', kp.publicKey);

  // ── A. LIVE device: commit the real op stream, seal, read live tip + live books ───────────────────
  var K = freshKernel();
  var dbLive = new SQL.Database();
  LIVE.forEach(function (o, i) { K.commitOp(dbLive, o.type, o.params, null, null, null, 9000 + i); }); // ts = recorded INPUT
  await K.sealChain(dbLive);
  var live = await K.verifyChain(dbLive);
  var liveBooks = PC.foldBalances(K.replayOps(dbLive), null).bal;
  console.log('§PERSIST-LIVE len=' + live.len + ' tip=' + (live.tip || '').slice(0, 16) + '… books=' + JSON.stringify(liveBooks));

  // ── B. EXPORT+SIGN the snapshot to the user's channel(s) ──────────────────────────────────────────
  var snap = await exportSignedSnapshot(dbLive, live.tip, live.len, appSigner);
  console.log('§PERSIST-EXPORT signed_by=' + snap.signed_by + ' ops=' + snap.ops.length + ' sig=' + snap.sig.slice(0, 12) + '…');
  check('S1 export non-empty + signed by the user\'s OWN key (verifies under own pubkey)',
        snap.ops.length === LIVE.length && (await appSigner.verify(snap.tip, snap.sig)),
        'ops=' + snap.ops.length + ' sigOK=' + (await appSigner.verify(snap.tip, snap.sig)));

  // publish the SAME blob to N interchangeable hosts the user owns (GH raw / OCI / here) + a down host.
  var pGH = 8190 + (process.pid % 50), pOCI = pGH + 1, pDOWN = pGH + 2;
  var sGH = await serve(pGH, JSON.stringify(snap));
  var sOCI = await serve(pOCI, JSON.stringify(snap));
  var base = 'http://localhost:';

  // ── C. FRESH DEVICE — zero state. Holds ONLY: the user's pubkey (out-of-band) + reachable host(s). ─
  //    Failover skips the down host, fetches the snapshot, replays through a FRESH kernel+db.
  var freshClient = RC.createReplicaClient([
    { name: 'DOWN', base: base + pDOWN },   // unreachable — proves disposable host
    { name: 'GH',   base: base + pGH },
    { name: 'OCI',  base: base + pOCI }
  ]);
  var picked = await freshClient.resolve();   // failover: first reachable
  check('S6 disposable host: failover skipped DOWN, fetched a reachable replica', picked.host === 'GH', 'picked=' + picked.host);

  var Kfresh = freshKernel();                  // a brand-new device: no prior op-log, no key store
  var dbFresh = new SQL.Database();
  var rec = await freshClient.replayAndVerify(dbFresh, Kfresh, picked.snapshot); // computes tip (ignore its demo-key sig field)
  check('S2 fresh-device recovery: recomputed tip === signed tip (exact chain reconstructed)',
        rec.matchesAdvertised && rec.computedTip === live.tip, 'computed=' + (rec.computedTip || '').slice(0, 12) + ' signed=' + (live.tip || '').slice(0, 12));

  var recBooks = PC.foldBalances(Kfresh.replayOps(dbFresh), null).bal;
  var bcmp = PC.balEqual(recBooks, liveBooks), ecmp = PC.balEqual(recBooks, expectedBooks());
  console.log('§PERSIST-RECOVER recoveredBooks=' + JSON.stringify(recBooks));
  check('S3 books recovered to the cent (recovered == live, maxDiff=0c)', bcmp.equal, 'maxDiff=' + bcmp.maxDiffCents + 'c');
  check('S3b recovered books == hand-computed expected (independent oracle)', ecmp.equal, 'maxDiff=' + ecmp.maxDiffCents + 'c');

  // ── D. OWNERSHIP — the fresh device trusts the snapshot ONLY via the user's pubkey (no private key) ─
  var verifierOnly = ErpSigner.makeSigner({ publicKey: await crypto.subtle.importKey('jwk', pubJwk, { name: 'ECDSA', namedCurve: 'P-256' }, true, ['verify']) });
  check('S4 ownership: signature verifies under the user\'s PUBLIC key alone (no private key present)',
        await verifierOnly.verify(picked.snapshot.tip, picked.snapshot.sig), 'pinned out-of-band pubkey');

  // ── E. FALSIFIER — a host serves a TAMPERED op AND recomputes the advertised tip to match. Only the
  //    signature catches it: the forger has no private key, so the old sig fails on the new tip. ──────
  console.log('\n§PERSIST-FORGE — tamperer rewrites an op + the advertised tip, but cannot re-sign');
  var forged = JSON.parse(JSON.stringify(snap));
  forged.ops[1].parameters = JSON.stringify({ table: 'C_Invoice', id: 109, lines: [
    { account_id: '101', role: 'AR', amtacctdr: 9999.99, amtacctcr: 0 },
    { account_id: '400', role: 'Revenue', amtacctdr: 0, amtacctcr: 9999.99 } ] }); // steal revenue
  // replay the forged ops through the FROZEN replica engine to get the forger's recomputed tip.
  var forgeClient = RC.createReplicaClient([{ name: 'EVIL', base: base + pGH }]);
  var Kforge = freshKernel(); var dbForge = new SQL.Database();
  var forgeRec = await forgeClient.replayAndVerify(dbForge, Kforge, forged);
  var forgeTip = forgeRec.computedTip;
  forged.tip = forgeTip;                                    // forger sets advertised = recomputed (recompute alone PASSES)
  var sigOnForgedTip = await verifierOnly.verify(forged.tip, forged.sig); // old sig over NEW tip — must fail
  check('S5 FALSIFIER: forger made tip self-consistent but the user-key signature REJECTS it',
        sigOnForgedTip === false && forgeTip !== live.tip, 'sigValidOnForgedTip=' + sigOnForgedTip + ' tipChanged=' + (forgeTip !== live.tip));

  await new Promise(function (r) { sGH.close(r); }); await new Promise(function (r) { sOCI.close(r); });
  console.log((fail === 0 ? '\n═══ 🟢 ALL PASS — op-log spills to a user-signed replica; a zero-state fresh device recovers the exact chain + books, and a forger without the user key is rejected ═══'
                          : '\n═══ 🔴 ' + fail + ' CHECK(S) FAILED ═══'));
  process.exit(fail === 0 ? 0 : 1);
})().catch(function (e) { console.error('FATAL', e && e.stack || e); process.exit(1); });
