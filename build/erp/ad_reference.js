// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ad_reference.js — AD_Ref_Table FK-membership + AD_Column.ValueFormat engine (W-REFERENCE). Two small
// reference/format legs of the coverage matrix: (1) AD_Ref_Table — a Table/TableDir reference defines its FK
// table + key column; FK validation = does a candidate id EXIST in that table (the membership query GridField
// does for a TableDir lookup). (2) AD_Column.ValueFormat — the VFormat input mask (DisplayType.VFormat),
// validate+transform a value against the mask. Self-contained, no kernel dep.
//
// SEAM (docs/ERP_BACKEND_SEPARATION.md §1): a reference/lookup concern — it answers "is this FK id real?" and
// "does this value fit the field mask?". Read-only membership + a pure format check; no derive/post/route.
//
// EXTRACT, DON'T INVENT: the FK table + key column come from ad_ref_table (joined to ad_table/ad_column); the
// mask comes from ad_column.vformat. Membership is a real query; an absent id is reported false, never coerced.
// Determinism: read-only + pure string mask, no Date/random. Implementing ERP_COVERAGE_MATRIX.md
// §AD_Ref_Table / §AD_Column·ValueFormat (ranked GAP #10) — Witness: W-REFERENCE
(function (global) {
  'use strict';

  // readRefTable(db, referenceId) — resolve a Table reference to its FK table + key column.
  function readRefTable(db, referenceId) {
    var r = db.prepare(
      'SELECT tb.tablename AS fktable, kc.columnname AS keycol FROM ad_ref_table rt ' +
      'JOIN ad_table tb ON tb.ad_table_id=rt.ad_table_id ' +
      'JOIN ad_column kc ON kc.ad_column_id=rt.ad_key WHERE rt.ad_reference_id=?').get(referenceId);
    return r ? { fkTable: r.fktable.toLowerCase(), keyCol: r.keycol } : null;
  }

  // fkExists(db, referenceId, id) — FK membership: is `id` a real row of the reference's FK table?
  function fkExists(db, referenceId, id) {
    var rt = readRefTable(db, referenceId);
    if (!rt) return { ok: false, deferred: 'no-ref-table', referenceId: referenceId };
    var hit = db.prepare('SELECT 1 AS m FROM ' + rt.fkTable + ' WHERE ' + rt.keyCol + '=?').get(id);
    return { ok: true, referenceId: referenceId, fkTable: rt.fkTable, keyCol: rt.keyCol, id: id, exists: !!hit };
  }

  // ── VFormat: per-char input mask (port of iDempiere DisplayType.VFormat / VString) ──────────────────────
  //   0 digit(0-9) required · 9 digit optional · L letter→UPPER · l letter→lower · o letter no-conv ·
  //   A letter|digit→UPPER · a letter|digit→lower · c letter|digit no-conv · _ literal space.
  function charOk(mask, ch) {
    switch (mask) {
      case '0': case '9': return /[0-9]/.test(ch);
      case 'L': case 'l': case 'o': return /[A-Za-z]/.test(ch);
      case 'A': case 'a': case 'c': return /[A-Za-z0-9]/.test(ch);
      case '_': return ch === ' ';
      default: return ch === mask;            // literal
    }
  }
  function charXform(mask, ch) {
    if (mask === 'L' || mask === 'A') return ch.toUpperCase();
    if (mask === 'l' || mask === 'a') return ch.toLowerCase();
    return ch;
  }
  // applyVFormat(value, mask) — validate+transform. Optional positions (9) may be absent at end.
  function applyVFormat(value, mask) {
    var v = String(value == null ? '' : value), m = String(mask || ''), out = '', vi = 0;
    for (var i = 0; i < m.length; i++) {
      var mc = m[i], ch = v[vi];
      var optional = (mc === '9');
      if (ch == null) { if (optional) continue; return { ok: false, reason: 'too-short', mask: m, value: v }; }
      if (!charOk(mc, ch)) {
        if (optional) continue;               // optional digit absent → skip mask, keep char for next mask
        return { ok: false, reason: 'char ' + vi + " '" + ch + "' fails mask '" + mc + "'", mask: m, value: v };
      }
      out += charXform(mc, ch); vi++;
    }
    if (vi < v.length) return { ok: false, reason: 'too-long', mask: m, value: v };
    return { ok: true, mask: m, value: v, formatted: out };
  }

  function coverageScan(db) {
    return {
      refTables: db.prepare('SELECT COUNT(*) AS n FROM ad_ref_table').get().n,
      colsWithVFormat: db.prepare("SELECT COUNT(*) AS n FROM ad_column WHERE vformat IS NOT NULL AND vformat<>''").get().n
    };
  }

  var API = { readRefTable: readRefTable, fkExists: fkExists, applyVFormat: applyVFormat, charOk: charOk, coverageScan: coverageScan };
  if (typeof module !== 'undefined' && module.exports) module.exports = API;
  if (typeof window !== 'undefined') window.AdReference = API; else if (global) global.AdReference = API;
})(typeof globalThis !== 'undefined' ? globalThis : this);
