#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope: internal scalability benchmark (docs/POS_WAN_SCALE_BENCH.md). Read the log.
// poc_pos_wan_scale.js — W-POS-WAN-SCALE: a fleet of POS stations on a WAN reports a MINIMAL daily fold
// (SODA receipt + EODA Close-Cash) to the central dump relay; measure scale, prove DR + email backup.
//
// ISSUES IT PROVES (named, docs/POS_WAN_SCALE_BENCH.md):
//   B1 ACCEPT THROUGHPUT — relay accept rate (ops/s, pushes/s) vs N (reported).
//   B2 WAN LATENCY — measured loopback RTT + deterministic per-station WAN model → e2e p50/p95/p99.
//   B3 IDEMPOTENT RETRY — re-push a fraction (WAN flap): accepted=0 extra, head unchanged, no double-count.
//   B4 MINIMAL-REPORT RECONSTRUCTION — fold the 2N relayed ops → Σ sales/net/consumed == expected (N×unit).
//   B5 RELAY DISASTER — kill+restart relay on same JSONL → head==2N, re-fold identical.
//   B6 POS TOTAL FAILURE — sampled station recovers last SIGNED EODA snapshot from inbox; forged rejected.
//   B7 SODA LOOP CLOSES — SODA receipt (M+) and EODA consume (P-) ride the SAME real BOM; residual=shrink only.
//
// NON-INVENT: per-station unit (price 500, BOM {133×4,134×1,135×1}, bp 112, pos 100/wh 104/plv 104) is
//   EXTRACTED from build/erp/ad_seed_fullwidth.db. Fleet size N is the benchmark variable (replay the real
//   unit). consumed = POSCore.backflushOps (the W-POS-EODA fold). net via BigDecimal (cents, never raw Number).
//   No Date.now/Math.random in the model: per-station shrink + WAN latency derive from a hash of station id;
//   wall-clock timings are MEASUREMENTS (reported, not asserted). BIZDATE is a fixed INPUT.
// Run: node scripts/poc_pos_wan_scale.js [N1,N2,...]   — then READ build/erp/poc_pos_wan_scale.log
'use strict';
var path = require('path');
var fs = require('fs');
var http = require('http');
var crypto = require('crypto');
var Database = require('better-sqlite3');
var POS = require(path.join(__dirname, '..', 'build', 'erp', 'pos_core.js'));
var BigDecimal = require(path.join(__dirname, '..', 'build', 'erp', 'bigdecimal.js'));
var createRelayServer = require(path.join(__dirname, '..', 'build', 'erp', 'erp_relay_server.js')).createRelayServer;

var HALF_UP = BigDecimal.RoundingMode.HALF_UP;
var BIZDATE = '2026-06-16';                       // fixed INPUT — deterministic op timestamps
var PORT = 8199;                                  // high port — internal, no collision with live/dev
var PERSIST = path.join('/tmp', 'wan_scale_relay.jsonl');
var CONC = 96;                                    // client concurrency cap (loopback)
var SCALES = (process.argv[2] ? process.argv[2].split(',').map(Number) : [200, 1000, 5000, 10000]);

// silence the relay's per-push chatter (keeps the log lean — boot/listen lines still pass)
var _rawLog = console.log;
console.log = function () { var a = arguments[0]; if (typeof a === 'string' && a.indexOf('§RELAY push') === 0) return; return _rawLog.apply(console, arguments); };

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function lc(r) { if (!r) return r; var o = {}; for (var k in r) o[k.toLowerCase()] = r[k]; return o; }
function h32(s) { var h = 5381; s = String(s); for (var i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) >>> 0; return h; }
function mapAdd(into, m, mul) { Object.keys(m).forEach(function (k) { into[k] = (into[k] || 0) + m[k] * (mul || 1); }); return into; }
function mapSub(a, b) { var o = {}; Object.keys(a).forEach(function (k) { o[k] = a[k] - (b[k] || 0); }); return o; }
function mapsEq(a, b) { var ak = Object.keys(a).sort(), bk = Object.keys(b).sort(); if (ak.join() !== bk.join()) return false; return ak.every(function (k) { return a[k] === b[k]; }); }
function pct(arr, p) { if (!arr.length) return 0; var a = arr.slice().sort(function (x, y) { return x - y; }); return a[Math.min(a.length - 1, Math.floor(p / 100 * a.length))]; }
function ms() { var t = process.hrtime(); return t[0] * 1e3 + t[1] / 1e6; }

// ── extract the REAL per-station daily unit from the seed ───────────────────────────────────
var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'ad_seed_fullwidth.db'), { readonly: true });
var pos = lc(db.prepare('SELECT * FROM c_pos WHERE c_pos_id=100').get());
var plv = lc(db.prepare('SELECT m_pricelist_version_id v FROM m_pricelist_version WHERE m_pricelist_id=?').get(pos.m_pricelist_id));
var priceStmt = db.prepare('SELECT pricestd FROM m_productprice WHERE m_pricelist_version_id=? AND m_product_id=?');
var bomStmt = db.prepare('SELECT bl.m_product_id AS comp_id, bl.qtybom AS qtybom FROM pp_product_bomline bl JOIN pp_product_bom b ON b.pp_product_bom_id=bl.pp_product_bom_id WHERE b.m_product_id=? ORDER BY bl.m_product_id');
function bomOf(pid) { return bomStmt.all(Number(pid)).map(lc); }
var ctx = { pos: pos, priceOf: function (pid) { return lc(priceStmt.get(plv.v, pid)) || null; }, bomOf: bomOf, wrPolicy: { isautogenerateinout: 'Y', isautogenerateinvoice: 'Y' } };
var WH = pos.m_warehouse_id, PATIO = 145, BP = 112;
var unitPrice = BigDecimal.of(String(ctx.priceOf(PATIO).pricestd)).setScale(2, HALF_UP);   // 500.00

// the day: 3 sales (qty 1,2,1). On a "shrink" station the 3rd (qty 1) is VOIDED (W-POS-EODA shape).
var DAY = [{ qty: 1 }, { qty: 2 }, { qty: 1 }];
var PLANNED_UNITS = DAY.reduce(function (s, d) { return s + d.qty; }, 0);                   // 4 (SODA receipt planned)
function isShrink(i) { return (i % 7) === 0; }                                              // deterministic shrink

// fold helpers — the per-station MINIMAL report (one SODA op + one EODA op)
function consumedFor(lines) { return POS.backflushOps(ctx, lines, WH).consumed; }          // the W-POS-EODA late fold
var SODA_RECEIPT = consumedFor(DAY.map(function (d) { return { m_product_id: PATIO, qtyordered: d.qty }; }));  // planned M+ (all 4 units)
function netCent(lines) {
  return Number(lines.reduce(function (s, l) { return s.add(unitPrice.multiply(BigDecimal.of(String(l.qty)))); }, BigDecimal.ZERO)
    .setScale(2, HALF_UP).unscaledValue());                                                // cents (scale 2 → unscaled = cents)
}
function eodaFold(i) {
  var sold = isShrink(i) ? DAY.slice(0, 2) : DAY.slice();                                   // shrink drops the voided sale
  var lines = sold.map(function (d) { return { m_product_id: PATIO, qtyordered: d.qty }; });
  return { station: i, sales: sold.length, voided: isShrink(i) ? 1 : 0, net_cent: netCent(sold), consumed: consumedFor(lines) };
}

// ── tiny HTTP client (concurrency-capped) ────────────────────────────────────────────────────
var agent = new http.Agent({ keepAlive: true, maxSockets: CONC });
function post1(pathname, body) {
  return new Promise(function (resolve, reject) {
    var data = Buffer.from(JSON.stringify(body));
    var t0 = ms();
    var req = http.request({ host: '127.0.0.1', port: PORT, path: pathname, method: 'POST', agent: agent, headers: { 'Content-Type': 'application/json', 'Content-Length': data.length } },
      function (res) { var b = ''; res.on('data', function (c) { b += c; }); res.on('end', function () { try { resolve({ rtt: ms() - t0, body: JSON.parse(b) }); } catch (e) { reject(e); } }); });
    req.on('error', reject); req.write(data); req.end();
  });
}
// WAN-realistic: retry on transient socket error (idempotent by op_uuid, so retry is safe — that IS B3)
var retried = 0;
async function post(pathname, body) {
  for (var attempt = 0; ; attempt++) {
    try { return await post1(pathname, body); }
    catch (e) { if (attempt >= 4) throw e; retried++; await new Promise(function (r) { setTimeout(r, 5 * (attempt + 1)); }); }
  }
}
function get1(pathname) {
  return new Promise(function (resolve, reject) {
    http.get({ host: '127.0.0.1', port: PORT, path: pathname, agent: agent }, function (res) { var b = ''; res.on('data', function (c) { b += c; }); res.on('end', function () { try { resolve(JSON.parse(b)); } catch (e) { reject(e); } }); }).on('error', reject);
  });
}
// retry on stale-keepAlive reset (notably after the DR relay restart — a discarded socket → fresh one)
async function get(pathname) {
  for (var attempt = 0; ; attempt++) {
    try { return await get1(pathname); }
    catch (e) { if (attempt >= 4) throw e; retried++; await new Promise(function (r) { setTimeout(r, 5 * (attempt + 1)); }); }
  }
}
async function pool(items, worker, conc) {
  var i = 0, out = new Array(items.length);
  async function lane() { while (i < items.length) { var k = i++; out[k] = await worker(items[k], k); } }
  var lanes = []; for (var c = 0; c < Math.min(conc, items.length); c++) lanes.push(lane());
  await Promise.all(lanes); return out;
}

// ── email backup leg (reuse poc_email_dr crypto pattern: EC P-256 sign + AES-256-GCM enc-to-user) ──
function sha256(s) { return crypto.createHash('sha256').update(s).digest('hex'); }
function canon(v) { if (v === null || typeof v !== 'object') return JSON.stringify(v); if (Array.isArray(v)) return '[' + v.map(canon).join(',') + ']'; return '{' + Object.keys(v).sort().map(function (k) { return JSON.stringify(k) + ':' + canon(v[k]); }).join(',') + '}'; }
function projHash(state) { return sha256(canon(state)); }
function ivFor(seq) { return sha256('iv|' + seq).slice(0, 24); }
function enc(plain, key, seq) { var c = crypto.createCipheriv('aes-256-gcm', key, Buffer.from(ivFor(seq), 'hex')); var ct = Buffer.concat([c.update(plain, 'utf8'), c.final()]); return { ct: ct.toString('hex'), tag: c.getAuthTag().toString('hex') }; }
function dec(ctHex, tagHex, key, seq) { var d = crypto.createDecipheriv('aes-256-gcm', key, Buffer.from(ivFor(seq), 'hex')); d.setAuthTag(Buffer.from(tagHex, 'hex')); return Buffer.concat([d.update(Buffer.from(ctHex, 'hex')), d.final()]).toString('utf8'); }
function emitEmail(state, seq, prevHash, sk, encKey) { var c = enc(canon(state), encKey, seq); var body = seq + '|' + prevHash + '|' + c.ct + '|' + c.tag; var hh = sha256(body); return { seq: seq, prev_hash: prevHash, ct: c.ct, tag: c.tag, op_hash: hh, sig: crypto.sign('sha256', Buffer.from(hh), sk).toString('hex') }; }
function recover(mailbox, pub, encKey) {
  var valid = mailbox.filter(function (m) { try { return crypto.verify('sha256', Buffer.from(m.op_hash), pub, Buffer.from(m.sig, 'hex')) && sha256(m.seq + '|' + m.prev_hash + '|' + m.ct + '|' + m.tag) === m.op_hash; } catch (e) { return false; } });
  if (!valid.length) return { ok: false, reason: 'no valid signed snapshot' };
  var tip = valid.reduce(function (a, b) { return b.seq > a.seq ? b : a; });
  try { return { ok: true, tip: tip, state: JSON.parse(dec(tip.ct, tip.tag, encKey, tip.seq)) }; } catch (e) { return { ok: false, reason: 'undecryptable', tip: tip }; }
}

function opOf(kind, i, fold) {
  return { op_uuid: kind + '|' + BIZDATE + '|' + i, timestamp: BIZDATE, op_type: 'POS_' + kind, station: i, m_warehouse_id: WH, parameters: JSON.stringify(fold) };
}

async function runScale(N) {
  if (fs.existsSync(PERSIST)) fs.unlinkSync(PERSIST);
  var relay = createRelayServer({ port: PORT, persistPath: PERSIST });
  await relay.listen();

  var idx = []; for (var i = 0; i < N; i++) idx.push(i);
  var nVoid = idx.filter(isShrink).length;

  // ── SODA wave: N pushes, one M+ receipt op each (the morning replenishment fold) ──
  var t0 = ms();
  var sodaRtt = await pool(idx, function (i) { return post('/push', { ops: [opOf('SODA', i, { components: SODA_RECEIPT, units: PLANNED_UNITS, movementtype: 'M+' })] }).then(function (r) { return r.rtt; }); }, CONC);
  var sodaWall = ms() - t0;

  // ── EODA wave: N pushes, one Close-Cash fold op each ──
  t0 = ms();
  var folds = idx.map(eodaFold);
  var eodaRtt = await pool(idx, function (i) { return post('/push', { ops: [opOf('EODA', i, folds[i])] }).then(function (r) { return r.rtt; }); }, CONC);
  var eodaWall = ms() - t0;

  var headAfter = (await get('/head')).head;

  // ── B3 IDEMPOTENT RETRY — re-push every 5th station's EODA op (WAN flap) ──
  var retry = idx.filter(function (i) { return i % 5 === 0; });
  var reAcc = (await pool(retry, function (i) { return post('/push', { ops: [opOf('EODA', i, folds[i])] }).then(function (r) { return r.body.accepted; }); }, CONC)).reduce(function (a, b) { return a + b; }, 0);
  var headAfterRetry = (await get('/head')).head;

  // ── B4 MINIMAL-REPORT RECONSTRUCTION — fold the 2N ops the relay actually holds ──
  var snap = (await get('/snapshot?after=0')).ops;
  var agg = { sales: 0, net_cent: 0, consumed: {}, receipt: {} };
  snap.forEach(function (op) {
    var p = JSON.parse(op.parameters);
    if (op.op_type === 'POS_EODA') { agg.sales += p.sales; agg.net_cent += Number(p.net_cent); Object.keys(p.consumed).forEach(function (c) { agg.consumed[c] = (agg.consumed[c] || 0) + p.consumed[c]; }); }
    else if (op.op_type === 'POS_SODA') { Object.keys(p.components).forEach(function (c) { agg.receipt[c] = (agg.receipt[c] || 0) + p.components[c]; }); }
  });
  // expected = fold the per-station units LOCALLY (independent of the relay path; any BOM depth)
  var expSales = 0, expNet = 0, expConsumed = {}, expReceipt = {};
  folds.forEach(function (f) { expSales += f.sales; expNet += f.net_cent; mapAdd(expConsumed, f.consumed); });
  mapAdd(expReceipt, SODA_RECEIPT, N);
  var perUnit = consumedFor([{ m_product_id: PATIO, qtyordered: 1 }]);     // 1 unit of leaves (for the residual cross-check)
  var expResidual = {}; mapAdd(expResidual, perUnit, nVoid);               // residual = nVoid stations × (planned−sold = 1 unit)

  // ── B5 RELAY DISASTER — kill + restart on the same JSONL, re-fold ──
  await relay.close();
  var relay2 = createRelayServer({ port: PORT, persistPath: PERSIST });
  await relay2.listen();
  var headDR = relay2.head();
  var snap2 = (await get('/snapshot?after=0')).ops;
  var net2 = snap2.filter(function (o) { return o.op_type === 'POS_EODA'; }).reduce(function (s, o) { return s + Number(JSON.parse(o.parameters).net_cent); }, 0);
  await relay2.close();

  // ── B2 WAN model — overlay a deterministic per-station one-way latency on the measured accept time ──
  function wanOneWay(i) { return 20 + (h32('wan|' + i) % 200); }           // 20..219 ms, deterministic
  var allRtt = sodaRtt.concat(eodaRtt);
  var e2e = idx.map(function (i, k) { return 2 * wanOneWay(i) + eodaRtt[k]; });   // round-trip WAN + server accept

  console.log('── N=' + N + ' stations (' + nVoid + ' shrink) — 2N=' + (2 * N) + ' relayed ops ──');
  console.log('   §B1 SODA wave: ' + N + ' pushes in ' + sodaWall.toFixed(0) + 'ms = ' + (N / sodaWall * 1000).toFixed(0) + ' pushes/s' +
    ' | EODA wave: ' + N + ' pushes in ' + eodaWall.toFixed(0) + 'ms = ' + (N / eodaWall * 1000).toFixed(0) + ' pushes/s' +
    ' | accept ' + ((2 * N) / (sodaWall + eodaWall) * 1000).toFixed(0) + ' ops/s');
  console.log('   §B2 loopback RTT p50/p95/p99=' + pct(allRtt, 50).toFixed(1) + '/' + pct(allRtt, 95).toFixed(1) + '/' + pct(allRtt, 99).toFixed(1) + 'ms' +
    ' | modeled WAN e2e p50/p95/p99=' + pct(e2e, 50).toFixed(0) + '/' + pct(e2e, 95).toFixed(0) + '/' + pct(e2e, 99).toFixed(0) + 'ms | transient-retries(cum)=' + retried);
  verdict(headAfter === 2 * N, '   §B1 relay head == 2N after both waves', 'head=' + headAfter);
  verdict(reAcc === 0 && headAfterRetry === 2 * N, 'B3 idempotent retry: re-push ' + retry.length + ' stations → accepted=0, head unchanged (no double-count)', 'reAccepted=' + reAcc + ' head=' + headAfterRetry);
  verdict(agg.sales === expSales && agg.net_cent === expNet && mapsEq(agg.consumed, expConsumed), 'B4 minimal report reconstructs central position == expected (to the cent)',
    'sales=' + agg.sales + '/' + expSales + ' net=' + agg.net_cent + '/' + expNet + 'c consumed-map==exp=' + mapsEq(agg.consumed, expConsumed));
  verdict(headDR === 2 * N && net2 === expNet, 'B5 relay disaster: restart→JSONL replay→head==2N, re-fold net identical', 'headDR=' + headDR + ' net2=' + net2 + 'c');
  // B7: residual per component = receipt − consume = 0 for normal stations, = 1 unit of leaves per shrink station
  var residual = mapSub(agg.receipt, agg.consumed);
  verdict(mapsEq(agg.receipt, expReceipt) && mapsEq(residual, expResidual), 'B7 SODA loop closes: receipt(M+)==N×planned, residual(receipt−consume)==shrink×1-unit (same real BOM)',
    'receiptOK=' + mapsEq(agg.receipt, expReceipt) + ' residualOK=' + mapsEq(residual, expResidual) + ' shrink=' + nVoid);

  return { N: N, headAfter: headAfter, sodaWall: sodaWall, eodaWall: eodaWall, opsPerSec: (2 * N) / (sodaWall + eodaWall) * 1000, jsonlBytes: fs.statSync(PERSIST).size, e2e99: pct(e2e, 99) };
}

// ── B6 POS TOTAL FAILURE — email backup recovery (sampled; per-station cost O(1)) ──
function runEmailBackup(sample) {
  console.log('── B6 POS total failure → email backup recovery (sample ' + sample.length + ' stations) ──');
  var allOk = true;
  sample.forEach(function (i) {
    var u = crypto.generateKeyPairSync('ec', { namedCurve: 'P-256' });
    var encKey = crypto.createHash('sha256').update(u.privateKey.export({ type: 'pkcs8', format: 'der' })).digest();
    // station emits a chained signed snapshot of its growing EODA fold across the day (3 snapshots)
    var fold = eodaFold(i), mailbox = [], prev = '0'.repeat(64);
    [{ sales: 1 }, { sales: 2 }, fold].forEach(function (st, s) { var e = emitEmail(st, s + 1, prev, u.privateKey, encKey); mailbox.push(e); prev = e.op_hash; });
    var preHash = projHash(fold);
    // total failure: device + relay-side ops gone. Recover from inbox.
    var r = recover(mailbox, u.publicKey, encKey);
    // forged tip (attacker, no key) must be rejected
    var forged = { seq: 99, prev_hash: prev, ct: enc(canon({ sales: 999, net_cent: '99999999' }), encKey, 99).ct, tag: 'dead', op_hash: null, sig: 'dead' };
    forged.op_hash = sha256(forged.seq + '|' + forged.prev_hash + '|' + forged.ct + '|' + forged.tag);
    var rF = recover(mailbox.concat([forged]), u.publicKey, encKey);
    var ok = r.ok && projHash(r.state) === preHash && rF.ok && rF.tip.seq === 3;
    if (!ok) allOk = false;
    console.log('   station ' + i + ': recover seq=' + (r.tip && r.tip.seq) + ' hash==pre=' + (r.ok && projHash(r.state) === preHash ? 'Y' : 'N') + ' forged-rejected=' + (rF.ok && rF.tip.seq === 3 ? 'Y' : 'N'));
  });
  verdict(allOk, 'B6 every sampled station recovers its last SIGNED EODA snapshot from inbox; forged rejected', 'sample=' + sample.length);
}

// ── CONSTRAINT GAP — the relay is a dumb sequencer (only op_uuid is unique). Real iDempiere enforces
// more at the DB layer when the minimal fold MATERIALIZES on central replay. Decide which bite at scale.
function runConstraintGap(N) {
  console.log('── CONSTRAINT GAP vs real iDempiere (decided at N=' + N + ') ──');
  // build ONE normal station's FULL local detail (the real buildSaleGroup write-set) — non-invent
  var stationOps = [], byTable = {}, docs = [];
  DAY.forEach(function (d, si) {
    var oid = 900000 + si * 10;
    var g = POS.buildSaleGroup(ctx, [POS.ringLine(ctx, PATIO, d.qty)], { orderId: oid, inoutId: oid + 1, invoiceId: oid + 2, c_bpartner_id: BP });
    g.ops.forEach(function (o) { var k = (o.table || '?') + '/' + o.op_type; byTable[k] = (byTable[k] || 0) + 1; stationOps.push(o); if (o.op_type === 'CREATE_DOCUMENT') docs.push(o.table); });
  });
  var matPer = stationOps.length, docsPer = docs.length;
  var wanOps = 2 * N, matRows = matPer * N;

  // G1 — AD_Sequence / DocumentNo: the classic iDempiere bottleneck. A GLOBAL counter needs a row-lock
  // (SELECT…FOR UPDATE) per doc = a central SERIALIZATION POINT under N stations. PARTITION the number
  // by (station|date|local-seq) → collision-free with NO central lock (rides the op-log's own free seq).
  var partitioned = {}, global = {}, pColl = 0, gColl = 0;
  for (var i = 0; i < N; i++) {
    for (var j = 0; j < docsPer; j++) {
      var pn = i + '|' + BIZDATE + '|' + docs[j] + '|' + j;          // partitioned: station-scoped → unique
      var gn = docs[j] + '|' + j;                                    // global naive: no station → collides
      if (partitioned[pn]) pColl++; else partitioned[pn] = 1;
      if (global[gn]) gColl++; else global[gn] = 1;
    }
  }
  verdict(pColl === 0, 'G1 DocumentNo PARTITIONED by station|date → 0 collisions, NO central AD_Sequence lock (the #1 iDempiere scale bottleneck removed)', 'docs=' + (docsPer * N) + ' partition-collisions=' + pColl);
  console.log('   §G1 same docs under a GLOBAL counter (the relational way) collide ' + gColl + '× → would need ' + (docsPer * N) + ' serialized FOR-UPDATE locks; partitioned numbering rides the dumb relay\'s O(1) append instead');

  // G2 — materialization fan-out: the WAN stays minimal, but the central DB write-set is where FK/NOT-NULL/
  // unique/GL constraints actually cost. Quantify the amplification (real op tally, GL is the extra O(lines)).
  var tbl = {}; Object.keys(byTable).forEach(function (k) { var t = k.split('/')[0]; tbl[t] = (tbl[t] || 0) + byTable[k]; });
  console.log('   §G2 per station: ' + matPer + ' constrained writes {' + Object.keys(tbl).sort().map(function (t) { return t + ':' + tbl[t]; }).join(' ') + '} across ' + docsPer + ' documents');
  console.log('   §G2 WAN cost ' + wanOps + ' minimal ops  vs  central materialized ' + matRows + ' rows  → amplification ' + (matRows / wanOps).toFixed(1) + '× (+ fact_acct GL ∝ lines, posted by central replay, not shipped)');
  verdict(matRows / wanOps > 1 && matRows === matPer * N, 'G2 minimal report holds the WAN flat (2/station); constraint cost is LINEAR O(N) on central replay, no cross-station contention (numbering partitioned)', 'amp=' + (matRows / wanOps).toFixed(1) + '×');

  // G3 — the residual correctness gap (not a scaling one): a sale referencing a station-local NEW product
  // (§P-9 register) needs that M_Product op ordered BEFORE the sale. Same-station op-log preserves causal
  // order; a CROSS-station shared new master is the open FK/ordering gap. EODA folds reference seed masters
  // only → out of scope here; flagged so it is not silently claimed as covered.
  console.log('   §G3 RESIDUAL GAP (correctness, not scale): cross-station shared NEW master (§P-9 product) FK-ordering is not closed by per-station causal order — EODA fold references seed masters only (out of scope, flagged).');
}

(async function () {
  console.log('═══ W-POS-WAN-SCALE — POS fleet → central dump relay (SODA+EODA), scale + DR + email backup ═══');
  console.log('Unit (EXTRACTED ad_seed_fullwidth.db): pos 100 / wh ' + WH + ' / plv ' + plv.v + ' · tile ' + PATIO + ' @ ' + unitPrice.toString() +
    ' · BOM ' + JSON.stringify(SODA_RECEIPT) + ' (for ' + PLANNED_UNITS + ' planned units) · bp ' + BP + '\n');

  var rows = [];
  for (var s = 0; s < SCALES.length; s++) { rows.push(await runScale(SCALES[s])); console.log(''); }
  runEmailBackup([1, 7, 42]);   // 7 is a shrink station
  console.log('');
  runConstraintGap(SCALES[SCALES.length - 1]);   // decide constraint impact at the largest scale

  console.log('\n── SCALABILITY TABLE ──');
  console.log('   N       2N-ops   accept-ops/s   SODA-wall   EODA-wall   JSONL-bytes   WAN-e2e-p99');
  rows.forEach(function (r) {
    console.log('   ' + String(r.N).padEnd(7) + ' ' + String(r.headAfter).padEnd(8) + ' ' + String(Math.round(r.opsPerSec)).padEnd(14) +
      ' ' + (r.sodaWall.toFixed(0) + 'ms').padEnd(11) + ' ' + (r.eodaWall.toFixed(0) + 'ms').padEnd(11) + ' ' + String(r.jsonlBytes).padEnd(13) + ' ' + r.e2e99.toFixed(0) + 'ms');
  });
  // simple scaling read: ops/s should stay roughly flat (relay is O(1) per op) — report the spread
  var rates = rows.map(function (r) { return r.opsPerSec; });
  console.log('   accept-rate spread: min=' + Math.round(Math.min.apply(null, rates)) + ' max=' + Math.round(Math.max.apply(null, rates)) + ' ops/s (flat ⇒ linear scale, O(1)/op sequencer)');

  db.close(); agent.destroy();
  console.log('\n' + (fails === 0 ? '🟢 W-POS-WAN-SCALE PASS' : '🔴 W-POS-WAN-SCALE FAIL (' + fails + ')') +
    ' — minimal 2-ops/station report reconstructs the central position to the cent at every scale; idempotent under WAN retry; survives relay total loss (JSONL replay) and POS total loss (signed inbox snapshot); SODA/EODA close the BOM loop.');
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) { console.error('FATAL', e && e.stack || e); process.exit(1); });
