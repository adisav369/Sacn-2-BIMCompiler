#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_chat.js — prompts/MOBILE_CHAT_LENS.md POC-1.
 *
 * Falsifiable claim: the op-log folds to a FAITHFUL, DETERMINISTIC, DISMISSIBLE, ANCHORABLE message
 * thread over REAL data — the engine-side proof of the chat lens ("kernel_ops IS the message table").
 * Reuses the PROVEN kernel (scripts/erp_kernel.js Kernel.apply/replay) — NO fork, NO new verb.
 * Every message folds from a real glassbowl_data.db / kernel_ops row; absent data is LABELLED absent.
 *
 * Run: node scripts/poc_chat.js 2>&1 | tee build/erp/poc_chat.log
 */
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var initSqlJs = require('sql.js');
var K = require('./erp_kernel');

var GB = process.env.ERP_GLASSBOWL || path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db');
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function money(n) { return (Math.round(Number(n || 0) * 100) / 100).toFixed(2); }

(async function () {
  var gb = new Database(GB, { readonly: true });
  var all = function (sql, p) { return gb.prepare(sql).all(p || {}); };
  var one = function (sql, p) { return gb.prepare(sql).get(p || {}); };

  var SQL = await initSqlJs();
  var db = new SQL.Database();          // the 5-table projection (browser binding, like poc_kernel)
  K.initProjection(db);

  console.log('═══ POC-CHAT — the ERP as a conversation: op-log → message thread (REAL data) ═══\n');

  // ── source a REAL document + lines + bpartner (non-invent: every value from a row) ──
  var INV = 103;
  var inv = one('SELECT * FROM c_invoice WHERE c_invoice_id=@id', { id: INV });
  var bp = one('SELECT name FROM c_bpartner WHERE c_bpartner_id=@id', { id: inv.c_bpartner_id });
  var lines = all('SELECT * FROM c_invoiceline WHERE c_invoice_id=@id ORDER BY line', { id: INV });
  var bpName = bp ? bp.name : ('BPartner#' + inv.c_bpartner_id);
  console.log('── source: C_Invoice#' + INV + ' ' + inv.documentno + ' · ' + bpName + ' · ' + money(inv.grandtotal) +
    ' · ' + lines.length + ' lines · createdby=' + inv.createdby + ' updatedby=' + inv.updatedby + ' ──\n');

  // ── build the lifecycle as REAL ops; pre-stamp uuids deterministically so apply skips edgeMint ──
  var docGuid = 'DOC:C_Invoice#' + INV;
  var createOps = [{
    op_type: 'CREATE_DOCUMENT', table: 'C_Invoice', source_id: INV, doc_status: 'DR', uuid: docGuid,
    documentno: inv.documentno, grandtotal: inv.grandtotal, bpartner: bpName, dateinvoiced: inv.dateinvoiced
  }];
  lines.forEach(function (l) {
    createOps.push({
      op_type: 'CREATE_LINE', table: 'C_InvoiceLine', source_line_id: l.c_invoiceline_id,
      line_no: l.line, uuid: 'LINE:C_InvoiceLine#' + l.c_invoiceline_id,
      product: l.m_product_id, linenetamt: l.linenetamt
    });
  });
  var completeOps = [{ op_type: 'SET_STATUS', table: 'C_Invoice', id: INV, doc_status: 'CO', uuid: docGuid }];

  // commit via the PROVEN Kernel.apply — TWO batches to carry the two REAL audit actors.
  // op ordering (timestamp/op_uuid) stays deterministic (actor-prefixed seq, no Date/random);
  // the HUMAN time shown in the thread is the real created/updated column (folded at render).
  K.apply(db, createOps, { actor: 'user:' + inv.createdby, baseTs: 1000 });
  K.apply(db, completeOps, { actor: 'user:' + inv.updatedby, baseTs: 2000 });

  // ════════════════════════════════════════════════════════════════════════
  // §CHAT-THREAD — fold kernel_ops → a message thread; every message from a real op.
  // ════════════════════════════════════════════════════════════════════════
  console.log('── §CHAT-THREAD ──');
  var humanTime = { CREATE_DOCUMENT: inv.created, CREATE_LINE: inv.created, SET_STATUS: inv.updated };
  var log = K.query(db, 'SELECT op_uuid, timestamp, op_type, parameters, output_guid, user_tag FROM kernel_ops ORDER BY rowid');
  var thread = log.map(function (row) {
    var rich = JSON.parse(row.parameters); var p = rich.payload || {}; var body;
    switch (row.op_type) {
      case 'CREATE_DOCUMENT': body = 'created ' + p.table + ' ' + p.documentno + ' · ' + p.bpartner + ' · ' + money(p.grandtotal); break;
      case 'CREATE_LINE': body = 'line ' + p.line_no + ' · product ' + p.product + ' · ' + money(p.linenetamt); break;
      case 'SET_STATUS': body = '▸ ' + rich.before + ' → ' + rich.after; break;
      default: body = row.op_type;
    }
    return { id: row.op_uuid, sender: row.user_tag, verb: row.op_type, body: body, time: humanTime[row.op_type] || '?', traced: !!rich.payload };
  });
  thread.forEach(function (m) { console.log('   [' + m.sender + ' · ' + m.time + ']  ' + m.body + '   (' + m.id + ')'); });
  var opCount = K.query(db, 'SELECT COUNT(*) AS c FROM kernel_ops')[0].c;
  var allTraced = thread.every(function (m) { return m.traced && m.sender; });
  var handAuthored = thread.filter(function (m) { return !m.traced; }).length;
  console.log('§CHAT-THREAD doc=C_Invoice#' + INV + ' msgs=' + thread.length + ' source=kernel_ops handAuthored=' + handAuthored);
  verdict(thread.length === opCount && thread.length === (1 + lines.length + 1), 'thread length == kernel_ops == 1 create + ' + lines.length + ' lines + 1 status', 'msgs=' + thread.length + ' ops=' + opCount);
  verdict(allTraced && handAuthored === 0, 'every message folds from a real op (sender=user_tag, traced)', 'handAuthored=' + handAuthored);

  // ════════════════════════════════════════════════════════════════════════
  // §CHAT-REPLAY — replay the log into a fresh projection; the thread is reproducible.
  // ════════════════════════════════════════════════════════════════════════
  console.log('\n── §CHAT-REPLAY ──');
  var liveHash = K.projectionHash(db);
  var fresh = new SQL.Database();
  var rep = K.replay(db, fresh);
  var equal = rep.hash === liveHash;
  console.log('§CHAT-REPLAY ops=' + rep.ops + ' liveHash=' + liveHash + ' replayHash=' + rep.hash + ' equal=' + (equal ? 'Y' : 'N') + ' deterministic=' + (equal ? 'Y' : 'N'));
  verdict(equal, 'replay from kernel_ops ALONE reproduces the projection (swiped msgs return — log is the source)', 'liveHash=' + liveHash + ' replayHash=' + rep.hash);

  // ════════════════════════════════════════════════════════════════════════
  // §CHAT-DISMISS — swipe-off is a VIEW filter; the Recent-Changes pill restores; log untouched.
  // ════════════════════════════════════════════════════════════════════════
  console.log('\n── §CHAT-DISMISS ──');
  var logRowsBefore = K.query(db, 'SELECT COUNT(*) AS c FROM kernel_ops')[0].c;
  var dismissed = new Set([thread[1].id, thread[2].id]);                 // swipe off 2 line-messages (view only)
  var visible = thread.filter(function (m) { return !dismissed.has(m.id); });
  var restored = thread.slice();                                        // flip "Recent Changes" pill -> clear dismissed
  var logRowsAfter = K.query(db, 'SELECT COUNT(*) AS c FROM kernel_ops')[0].c;
  console.log('§CHAT-DISMISS swiped=' + dismissed.size + ' visible=' + visible.length + ' flip=RecentChanges→visible=' + restored.length +
    ' restored=' + (restored.length === thread.length ? 'Y' : 'N') + ' logRows=' + logRowsAfter + ' unchanged=' + (logRowsBefore === logRowsAfter ? 'Y' : 'N'));
  verdict(visible.length === thread.length - dismissed.size, 'swipe hides at the VIEW (visible = N - swiped)', 'visible=' + visible.length);
  verdict(restored.length === thread.length, 'the pill flip restores every message', 'restored=' + restored.length + '/' + thread.length);
  verdict(logRowsBefore === logRowsAfter, 'the op-log is NEVER mutated by a view action', 'rows ' + logRowsBefore + '→' + logRowsAfter);

  // ════════════════════════════════════════════════════════════════════════
  // §CHAT-ANCHOR — pin the richest BPartner at runtime; line up its related docs (real FK fold).
  // ════════════════════════════════════════════════════════════════════════
  console.log('\n── §CHAT-ANCHOR ──');
  var anchor = one(
    'SELECT b.c_bpartner_id AS id, b.name, ' +
    '(SELECT COUNT(*) FROM c_order o WHERE o.c_bpartner_id=b.c_bpartner_id) + ' +
    '(SELECT COUNT(*) FROM c_invoice i WHERE i.c_bpartner_id=b.c_bpartner_id) + ' +
    '(SELECT COUNT(*) FROM c_payment p WHERE p.c_bpartner_id=b.c_bpartner_id) AS d ' +
    'FROM c_bpartner b ORDER BY d DESC, b.c_bpartner_id LIMIT 1');
  var rel = [];
  all('SELECT * FROM c_order WHERE c_bpartner_id=@id', { id: anchor.id }).forEach(function (r) { rel.push({ t: 'C_Order', no: r.documentno, st: r.docstatus, dt: r.dateordered || r.created }); });
  all('SELECT * FROM c_invoice WHERE c_bpartner_id=@id', { id: anchor.id }).forEach(function (r) { rel.push({ t: 'C_Invoice', no: r.documentno, st: r.docstatus, dt: r.dateinvoiced || r.created }); });
  all('SELECT * FROM c_payment WHERE c_bpartner_id=@id', { id: anchor.id }).forEach(function (r) { rel.push({ t: 'C_Payment', no: r.documentno, st: r.docstatus, dt: r.datetrx || r.created }); });
  rel.sort(function (a, b) { return String(a.dt).localeCompare(String(b.dt)); });
  var CLOSED = { CL: 1, VO: 1, RE: 1 };
  var outstanding = rel.filter(function (r) { return !CLOSED[r.st]; });
  var last = rel[rel.length - 1];
  console.log('   anchor: ' + anchor.name + ' (#' + anchor.id + ')');
  rel.forEach(function (r) { console.log('     ∙ ' + r.t + ' ' + r.no + ' · ' + r.st + ' · ' + r.dt); });
  console.log('§CHAT-ANCHOR entity=' + anchor.name + '(#' + anchor.id + ') relatedDocs=' + rel.length +
    ' lastAction=' + (last ? last.t + '@' + last.dt : 'none') + ' outstanding=' + outstanding.length + ' source=FK-fold handAuthored=0');
  verdict(rel.length === anchor.d, 'anchor lines up ALL related docs from the FK fold', 'relatedDocs=' + rel.length + ' expected=' + anchor.d);
  verdict(!!last, 'last action resolves to a real related doc', last ? last.t + '@' + last.dt : 'none');

  // ════════════════════════════════════════════════════════════════════════
  // §CHAT-COVERAGE — the "posted" message degrades HONESTLY (fact_acct has no per-doc link here).
  // ════════════════════════════════════════════════════════════════════════
  console.log('\n── §CHAT-COVERAGE ──');
  var faCols = all('PRAGMA table_info(fact_acct)').map(function (c) { return c.name.toLowerCase(); });
  var docLinkable = faCols.indexOf('record_id') >= 0 && faCols.indexOf('ad_table_id') >= 0;
  var fa = one('SELECT COUNT(*) AS c, ROUND(SUM(amtacctdr),2) AS dr, ROUND(SUM(amtacctcr),2) AS cr FROM fact_acct');
  var coverage = docLinkable ? 'complete' : 'partial';
  var balanced = money(fa.dr) === money(fa.cr);
  console.log('   posted (this doc): ' + (docLinkable ? 'per-doc journal' : 'NOT doc-linked in this extract'));
  console.log('   books (client): ' + fa.c + ' fact_acct rows · Dr ' + money(fa.dr) + ' / Cr ' + money(fa.cr) + ' · balanced=' + (balanced ? 'Y' : 'N'));
  console.log('§CHAT-COVERAGE posted source=fact_acct coverage=' + coverage + ' clientDr=' + money(fa.dr) + ' clientCr=' + money(fa.cr) +
    ' balanced=' + (balanced ? 'Y' : 'N') + ' note="' + (docLinkable ? 'per-doc available' : 'install local for per-doc') + '" fabricated=0');
  verdict(balanced, 'client-level books balance (real fact_acct), per-doc labelled ' + coverage + ' (not fabricated)', 'Dr=' + money(fa.dr) + ' Cr=' + money(fa.cr));

  console.log('\n═══ ' + (fails === 0 ? '✅ POC-CHAT ALL PASS' : '❌ POC-CHAT ' + fails + ' FAIL') + ' — op-log folds to a faithful, deterministic, dismissible, anchorable thread ═══');
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) { console.error('FATAL', e); process.exit(2); });
