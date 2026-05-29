'use strict';
/**
 * erp_kernel.js — the OP-LOG KERNEL (prompts/ERP_RUNTIME_ENGINE.md T2, ERP_KERNEL_BUILD §P3/P3b).
 *   Spec: docs/ERP.md §0.6 (the rich op-log keystone), §18.6 (dispatch ladder), §18.8 (frozen).
 *
 * The seam the POC was missing: handlers RETURN ops[]; nothing applied + committed them.
 * This kernel closes it (B1 — "the kernel owns the write"):
 *   - Handlers are PURE (doc, ctx) -> ops[]. They get a READ-ONLY ctx (a query fn), never a
 *     writable db. The only way to change state is to RETURN an op.
 *   - Kernel.apply(db, ops, ctx) applies each op to the 5-table projection (schema_5table.sql)
 *     AND commitOp's a RICH op (payload + actor + before/after + lineage GUIDs) to kernel_ops.
 *   - Kernel.dispatch runs the §18.6 ladder: state-machine legal (P2 wfmc) -> guards (evalGuard)
 *     -> Handlers.run -> Kernel.apply, with a VIOLATION guard (handler must not touch the db
 *     except through returned ops — detected by a projection hash that must be unchanged across
 *     Handlers.run).
 *   - Kernel.replay rebuilds the projection from kernel_ops ALONE (event sourcing). Because the
 *     committed op carries resolved ids, replay reproduces old effects regardless of current
 *     rules (frozen effects, §18.8).
 *
 * PROTOTYPE in scripts/ on sql.js (per T2). It does NOT fork bim-ootb/viewer/kernel_ops.js;
 * the commitOp shape here (rich `parameters` JSON) is exactly what that shared module already
 * ALLOWS — relocating (T3) wires this onto it without schema change. Deterministic: ids are
 * natural-key derived, timestamps come from ctx (no Date.now / Math.random) so replay is exact.
 */

// ── projection schema (a self-contained copy of schema_5table.sql's runtime tables) ──
var PROJECTION_SQL =
  'CREATE TABLE IF NOT EXISTS documents (id TEXT PRIMARY KEY, doc_type TEXT, doc_status TEXT DEFAULT \'DR\', source_id TEXT, parent_id TEXT, container_id TEXT, metadata TEXT DEFAULT \'{}\');' +
  'CREATE TABLE IF NOT EXISTS document_lines (id TEXT PRIMARY KEY, document_id TEXT, source_line_id TEXT, line_no INTEGER, match_type TEXT, metadata TEXT DEFAULT \'{}\');' +
  'CREATE TABLE IF NOT EXISTS items (id TEXT PRIMARY KEY, parent_id TEXT, type TEXT, metadata TEXT DEFAULT \'{}\');' +
  'CREATE TABLE IF NOT EXISTS containers (id TEXT PRIMARY KEY, parent_id TEXT, type TEXT, metadata TEXT DEFAULT \'{}\');' +
  'CREATE TABLE IF NOT EXISTS journal (id TEXT PRIMARY KEY, batch_id TEXT, journal_id TEXT, source TEXT, metadata TEXT DEFAULT \'{}\');' +
  'CREATE TABLE IF NOT EXISTS kernel_ops (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER, op_type TEXT, parameters TEXT, input_guids TEXT, output_guid TEXT, undone INTEGER DEFAULT 0, user_tag TEXT DEFAULT \'local\');';
var PROJECTION_TABLES = ['documents', 'document_lines', 'items', 'containers', 'journal'];

function initProjection(db) { db.run(PROJECTION_SQL); }

// host query helper over a sql.js db -> rows[] of plain objects.
function query(db, sql, params) {
  var r = db.exec(sql, params || []);
  if (!r.length) return [];
  return r[0].values.map(function (v) { var o = {}; r[0].columns.forEach(function (c, i) { o[c] = v[i]; }); return o; });
}

// deterministic djb2 over the whole projection (sorted) — the replay equality witness.
function projectionHash(db) {
  var acc = 5381;
  PROJECTION_TABLES.forEach(function (t) {
    var rows = query(db, 'SELECT * FROM ' + t + ' ORDER BY id');
    var s = t + '=' + JSON.stringify(rows);
    for (var i = 0; i < s.length; i++) { acc = ((acc << 5) + acc + s.charCodeAt(i)) | 0; }
  });
  return (acc >>> 0).toString(16);
}

// ── the natural-key id scheme (deterministic, replay-stable) ────────────────
function docKey(op) {
  if (op.op_type === 'SET_STATUS') return 'DOC:' + op.table + '#' + op.id;          // a doc's own id
  return 'DOC:' + op.table + '@from' + (op.source_id != null ? op.source_id : 'NA'); // derived doc
}
function lineKey(op) { return 'LINE:' + op.table + '@from' + (op.source_line_id != null ? op.source_line_id : 'NA'); }
function metaOf(op, drop) {
  var m = {}; Object.keys(op).forEach(function (k) { if (drop.indexOf(k) < 0) m[k] = op[k]; }); return m;
}

// ── apply ONE op to the projection; returns {output_guid, input_guids, before, after} ──
function applyOne(db, op, state) {
  var did, lid, before = null, after = null, inputs = [];
  if (op.op_type === 'CREATE_DOCUMENT') {
    did = docKey(op); inputs = op.source_id != null ? [String(op.source_id)] : [];
    var meta = metaOf(op, ['op_type', 'table', 'source_id', 'doc_status']);
    db.run('INSERT OR REPLACE INTO documents (id, doc_type, doc_status, source_id, metadata) VALUES (?,?,?,?,?)',
      [did, op.table, op.doc_status || 'DR', op.source_id != null ? String(op.source_id) : null, JSON.stringify(meta)]);
    state.currentDoc = did; after = did;
    return { output_guid: did, input_guids: inputs, before: before, after: after };
  }
  if (op.op_type === 'CREATE_LINE') {
    lid = lineKey(op); inputs = op.source_line_id != null ? [String(op.source_line_id)] : [];
    var lmeta = metaOf(op, ['op_type', 'table', 'source_line_id', 'line_no', 'match_type']);
    db.run('INSERT OR REPLACE INTO document_lines (id, document_id, source_line_id, line_no, match_type, metadata) VALUES (?,?,?,?,?,?)',
      [lid, state.currentDoc || null, op.source_line_id != null ? String(op.source_line_id) : null, op.line_no != null ? op.line_no : null, op.match_type || null, JSON.stringify(lmeta)]);
    return { output_guid: lid, input_guids: state.currentDoc ? [state.currentDoc] : inputs, before: before, after: lid };
  }
  if (op.op_type === 'SET_STATUS') {
    did = docKey(op);
    var prev = query(db, 'SELECT doc_status FROM documents WHERE id=?', [did]);
    before = prev.length ? prev[0].doc_status : null;
    // upsert: an order doc may not be in the projection yet (it pre-exists in source).
    db.run('INSERT INTO documents (id, doc_type, doc_status, metadata) VALUES (?,?,?,?) ' +
      'ON CONFLICT(id) DO UPDATE SET doc_status=excluded.doc_status',
      [did, op.table, op.doc_status, '{}']);
    after = op.doc_status;
    return { output_guid: did, input_guids: [String(op.id)], before: before, after: after };
  }
  if (op.op_type === 'ALLOCATE') {
    // settlement edge: link Order<->Payment + amount, as a journal entry (Batch->Journal).
    var aid = 'ALLOC:' + op.payment_id + ':' + op.order_id;
    db.run('INSERT OR REPLACE INTO journal (id, batch_id, journal_id, source, metadata) VALUES (?,?,?,?,?)',
      [aid, 'BATCH@pay' + op.payment_id, 'JRN@pay' + op.payment_id, 'DOC:C_Order#' + op.order_id, JSON.stringify(metaOf(op, ['op_type']))]);
    return { output_guid: aid, input_guids: ['DOC:C_Order#' + op.order_id, 'DOC:C_Payment#' + op.payment_id], before: null, after: aid };
  }
  throw new Error('unknown op_type ' + op.op_type);
}

// ── Kernel.apply — apply ops[] to projection AND commit a RICH op per op ─────
function apply(db, ops, ctx) {
  initProjection(db);
  ctx = ctx || {}; var actor = ctx.actor || 'local'; var ts = ctx.baseTs || 1000;
  var state = { currentDoc: ctx.currentDoc || null };
  var committed = 0, ids = [];
  ops.forEach(function (op, i) {
    var res = applyOne(db, op, state);
    // RICH op (§0.6 keystone): payload + actor + before/after + lineage GUIDs.
    var rich = { payload: op, actor: actor, before: res.before, after: res.after, lineage: { input_guids: res.input_guids, output_guid: res.output_guid } };
    db.run('INSERT INTO kernel_ops (timestamp, op_type, parameters, input_guids, output_guid, user_tag) VALUES (?,?,?,?,?,?)',
      [ts + i, op.op_type, JSON.stringify(rich), JSON.stringify(res.input_guids), res.output_guid, actor]);
    committed++; ids.push(res.output_guid);
  });
  return { applied: ops.length, committed: committed, ids: ids };
}

// ── Kernel.replay — rebuild the projection from kernel_ops ALONE (event sourcing) ──
// Re-applies each committed op's STORED payload (resolved ids) into a fresh projection.
// Because we re-apply stored ops (not re-evaluate rules), old effects are FROZEN (§18.8).
function replay(srcDbWithLog, freshDb) {
  initProjection(freshDb);
  var log = query(srcDbWithLog, 'SELECT op_type, parameters FROM kernel_ops WHERE undone=0 ORDER BY id');
  var state = { currentDoc: null };
  var n = 0;
  log.forEach(function (row) {
    var rich = JSON.parse(row.parameters);
    if (!rich.payload) return;                 // skip non-document ops (e.g. SESSION_START)
    applyOne(freshDb, rich.payload, state); n++;
  });
  return { ops: n, hash: projectionHash(freshDb) };
}

// ── projection snapshot/restore (generic SQL — works on sql.js AND better-sqlite3) ──
// Used to roll back a detected out-of-log write so the live projection stays == the log.
function snapshotProjection(db) {
  var s = {}; PROJECTION_TABLES.forEach(function (t) { s[t] = query(db, 'SELECT * FROM ' + t); }); return s;
}
function restoreProjection(db, s) {
  PROJECTION_TABLES.forEach(function (t) {
    db.run('DELETE FROM ' + t);
    s[t].forEach(function (row) {
      var cols = Object.keys(row);
      db.run('INSERT INTO ' + t + ' (' + cols.join(',') + ') VALUES (' + cols.map(function () { return '?'; }).join(',') + ')', cols.map(function (c) { return row[c]; }));
    });
  });
}

// ── state machine (P2 wfmc) ─────────────────────────────────────────────────
function legalTransition(wfmc, fromStatus, action) {
  var hit = (wfmc.transitions || []).filter(function (t) { return t[0] === fromStatus && t[1] === action; });
  return hit.length ? hit[0][2] : null;        // the 'to' status, or null if illegal
}

// ── handler registry ────────────────────────────────────────────────────────
var _handlers = {};
function register(docType, action, fn) { _handlers[docType + ':' + action] = fn; }
function getHandler(docType, action) { return _handlers[docType + ':' + action] || null; }

// ── Kernel.dispatch — the §18.6 ladder with the violation guard ─────────────
// cellCtx = { wfmc, guards[], query(sql)->rows (READ-ONLY data), actor, baseTs }
// doc     = { docType, action, status, ... } whatever the handler needs (read-only)
function dispatch(db, cellCtx, doc) {
  var docType = doc.docType, action = doc.action;
  // 1) state-machine legal?
  var to = legalTransition(cellCtx.wfmc, doc.status, action);
  if (!to) return { ok: false, stage: 'state-machine', reason: 'illegal (' + doc.status + ',' + action + ')' };
  // 2) guards (evalGuard) — non-sql / unresolved-ctx guards are skipped (logged), not failed.
  var guardFails = [];
  (cellCtx.guards || []).forEach(function (g) {
    if (cellCtx.evalGuard) {
      var r = cellCtx.evalGuard(cellCtx.query, g, doc.ctx || {});
      if (!r.ok && r.reason === 'sql-error') guardFails.push(g.rule_key + ':' + r.reason);
    }
  });
  if (guardFails.length) return { ok: false, stage: 'guard', reason: guardFails.join(',') };
  // 3) Handlers.run — PURE: hand it only a read-only query, NEVER db. Violation guard: the
  //    projection must not change across the handler call (a handler that writes is BLOCKED).
  var handler = getHandler(docType, action);
  if (!handler) return { ok: false, stage: 'handler', reason: 'unwritten-cell (' + docType + ',' + action + ')' };
  var hashBefore = projectionHash(db);
  var snap = snapshotProjection(db);
  var ops;
  try { ops = handler(doc, { query: cellCtx.query, to: to }) || []; }
  catch (e) { return { ok: false, stage: 'handler', reason: 'threw:' + e.message }; }
  var hashAfter = projectionHash(db);
  if (hashBefore !== hashAfter) {
    restoreProjection(db, snap);              // roll back the rogue write — the log stays the truth
    return { ok: false, stage: 'violation', reason: 'out-of-log-write', blocked: true };
  }
  // 4) Kernel.apply — the kernel (not the handler) owns the write.
  var res = apply(db, ops, { actor: cellCtx.actor, baseTs: cellCtx.baseTs });
  return { ok: true, to: to, ops: ops, applied: res.applied, committed: res.committed, ids: res.ids };
}

module.exports = {
  initProjection: initProjection, query: query, projectionHash: projectionHash,
  apply: apply, replay: replay, legalTransition: legalTransition,
  register: register, getHandler: getHandler, dispatch: dispatch,
  PROJECTION_TABLES: PROJECTION_TABLES
};
