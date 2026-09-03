// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// report_overlay.js — the Report verb (read face of the op-log) — prompts/CRUD_P_R_REPORT.md, R1.
// A THIRD peer overlay on the same keyed-hook mechanism as help_overlay.js / crud_overlay.js
// (UI_OVERLAY_GOVERNANCE.md): it attaches to glassbowl's bubbles BY KEY, listens on the key-addressed
// intent bus ('overlay:report'), and renders a RECEIPT for the focused document by FOLDING its header+
// lines from the bundle (glassbowl_data.db). PURE READ — no kernel write, no row mutation. Every amount
// is a fold over real rows; the layout carries no literal number (handAuthored=0). Reuses page globals:
// N/idx/withBundle/curChain/fname (best-effort, all typeof-guarded). Delete this file → page intact.
(function (global) {
  'use strict';

  // ════════════════════════════════════════════════════════════════════════
  // PURE CORE — no DOM, no DB. Exported for the headless §-witness harness (node).
  // ════════════════════════════════════════════════════════════════════════

  // REPORT_MAP — the iDempiere header/line CONVENTION as data (structure, not invented values):
  // header table → its line table + the financial columns. A new document = a new row here, never new
  // fold code (R3 later replaces this literal map with ad_printformat-driven layout). amount:null marks a
  // NON-FINANCIAL document (a shipment carries qty, not money) — folded honestly, never a fabricated total.
  var REPORT_MAP = {
    c_order:   { key: 'c_order',   pk: 'c_order_id',   lineTable: 'c_orderline',   fk: 'c_order_id',
                 fkProduct: 'm_product_id', amount: 'linenetamt', qty: 'qtyordered',  price: 'priceactual',
                 date: 'dateordered',  total: 'grandtotal' },
    c_invoice: { key: 'c_invoice', pk: 'c_invoice_id', lineTable: 'c_invoiceline', fk: 'c_invoice_id',
                 fkProduct: 'm_product_id', amount: 'linenetamt', qty: 'qtyinvoiced', price: 'priceactual',
                 date: 'dateinvoiced', total: 'grandtotal' },
    m_inout:   { key: 'm_inout',   pk: 'm_inout_id',   lineTable: 'm_inoutline',   fk: 'm_inout_id',
                 fkProduct: 'm_product_id', amount: null,        qty: 'movementqty', price: null,
                 date: 'movementdate', total: null }
  };

  function num(v) { return (v == null || v === '') ? null : Number(v); }
  function round2(n) { return Math.round((n + Number.EPSILON) * 100) / 100; }

  // foldReceipt — fold ONE document's receipt from its raw header row + raw line rows.
  // PURE: takes rows in, returns the folded receipt out. subtotal = Σ line amount; tax = total − subtotal
  // (only when both present); financial=false ⇒ no money columns (subtotal/tax stay null, honestly "n/a").
  // names = { partner:<str|null>, products:{ <id>:<name> } } — resolved by the caller from the bundle.
  function foldReceipt(map, header, lines, names) {
    if (!map || !header) return null;
    names = names || {};
    var fin = !!map.amount, prods = names.products || {};
    var outLines = (lines || []).map(function (r) {
      var pid = r[map.fkProduct];
      return {
        line: r.line, m_product_id: pid,
        name: (prods[pid] != null ? prods[pid] : (pid != null ? '#' + pid : '(no product)')),
        qty: num(r[map.qty]),
        price: map.price ? num(r[map.price]) : null,
        amount: fin ? num(r[map.amount]) : null
      };
    });
    var subtotal = fin ? round2(outLines.reduce(function (s, l) { return s + (l.amount || 0); }, 0)) : null;
    var total = map.total ? num(header[map.total]) : null;
    var tax = (subtotal != null && total != null) ? round2(total - subtotal) : null;
    return {
      key: map.key, id: header[map.pk], docno: header.documentno,
      date: (map.date && header[map.date] != null) ? String(header[map.date]).slice(0, 10) : null,
      partner: (names.partner != null ? names.partner
                 : (header.c_bpartner_id != null ? '#' + header.c_bpartner_id : null)),
      lines: outLines, subtotal: subtotal, tax: tax, total: total,
      financial: fin, foldsFrom: 'bundle'
    };
  }

  var CORE = { REPORT_MAP: REPORT_MAP, foldReceipt: foldReceipt, round2: round2 };
  if (typeof module !== 'undefined' && module.exports) { module.exports = CORE; return; }
  if (typeof document === 'undefined') return;

  // ════════════════════════════════════════════════════════════════════════
  // DOM OVERLAY — the receipt panel, driven by the ring's ▤ Report verb (via the intent bus).
  // ════════════════════════════════════════════════════════════════════════
  injectCss();
  var panel = document.createElement('div'); panel.id = 'reportPanel'; document.body.appendChild(panel);

  // ── bundle helpers (truth-bound: read real rows, never invent) ──────────────
  function hasCol(db, t, c) {
    try { var pr = db.exec('PRAGMA table_info(' + t + ')'); return pr.length && pr[0].values.some(function (v) { return String(v[1]).toLowerCase() === c; }); }
    catch (e) { return false; }
  }
  function rowsOf(res) {
    if (!res || !res.length) return [];
    return res[0].values.map(function (v) { var o = {}; res[0].columns.forEach(function (c, i) { o[c] = v[i]; }); return o; });
  }
  // litId — prefer the row in the currently-traced O2C chain (the lit instance), else the first row
  // (mirrors crud_overlay.getRecord so Report and the ring agree on which document is focused).
  function litId(db, map) {
    try { if (typeof curChain !== 'undefined' && curChain) { for (var i = 0; i < curChain.length; i++) if (curChain[i].table === map.key && curChain[i].id != null) return curChain[i].id; } } catch (e) {}
    try { var r = db.exec('SELECT ' + map.pk + ' FROM ' + map.key + ' ORDER BY ' + map.pk + ' LIMIT 1'); return (r.length && r[0].values.length) ? r[0].values[0][0] : null; }
    catch (e) { return null; }
  }
  function nameOf(db, table, pk, id, col) {
    try { if (!hasCol(db, table, col)) return null; var r = db.exec('SELECT ' + col + ' FROM ' + table + ' WHERE ' + pk + '=' + id + ' LIMIT 1'); return (r.length && r[0].values.length) ? r[0].values[0][0] : null; }
    catch (e) { return null; }
  }

  // show — fold + render the receipt for `key` (resolving the lit document id from the bundle).
  function show(key) {
    var map = CORE.REPORT_MAP[key];
    if (!map) { renderUnsupported(key); console.log('§REPORT verb key=' + key + ' supported=N (no header/line map)'); return; }
    if (typeof withBundle !== 'function') { console.warn('§REPORT no bundle'); return; }
    withBundle(function (db) {
      try {
        var id = litId(db, map);
        if (id == null) { renderUnsupported(key); return; }
        var header = rowsOf(db.exec('SELECT * FROM ' + map.key + ' WHERE ' + map.pk + '=' + id + ' LIMIT 1'))[0] || null;
        if (!header) { renderUnsupported(key); return; }
        var lines = [];
        try { lines = rowsOf(db.exec('SELECT * FROM ' + map.lineTable + ' WHERE ' + map.fk + '=' + id + ' ORDER BY line')); } catch (e) {}
        var names = { partner: nameOf(db, 'c_bpartner', 'c_bpartner_id', header.c_bpartner_id, 'name'), products: {} };
        lines.forEach(function (r) { var pid = r[map.fkProduct]; if (pid != null && names.products[pid] === undefined) names.products[pid] = nameOf(db, 'm_product', 'm_product_id', pid, 'name'); });
        var rec = CORE.foldReceipt(map, header, lines, names);
        render(rec);
        console.log('§REPORT-RECEIPT doc=' + fname(key) + '#' + rec.id + ' lines=' + rec.lines.length +
          ' subtotal=' + fmtN(rec.subtotal) + ' tax=' + fmtN(rec.tax) + ' total=' + fmtN(rec.total) +
          ' folds-from=' + rec.foldsFrom + ' handAuthored=0');
      } catch (er) { console.warn('§REPORT fold error', er && er.message); }
    });
  }

  function fmtN(n) { return n == null ? 'n/a' : Number(n).toFixed(2); }
  function money(n) { return n == null ? '<span class=rpna>n/a</span>' : Number(n).toFixed(2); }

  function render(rec) {
    var fin = rec.financial;
    var h = '<span class=rpx title=close>✕</span>' +
      '<div class=rph><span class=rpglyph>▤</span> Receipt — ' + esc(fname(rec.key)) + ' #' + esc(rec.docno == null ? rec.id : rec.docno) + '</div>' +
      '<div class=rpmeta>' + (rec.partner ? '<div><b>Partner</b> ' + esc(rec.partner) + '</div>' : '') +
        (rec.date ? '<div><b>Date</b> ' + esc(rec.date) + '</div>' : '') + '</div>' +
      '<table class=rptbl><thead><tr><th>#</th><th>Item</th><th class=rpr>Qty</th>' +
        (fin ? '<th class=rpr>Price</th><th class=rpr>Amount</th>' : '') + '</tr></thead><tbody>';
    rec.lines.forEach(function (l) {
      h += '<tr><td>' + esc(l.line) + '</td><td>' + esc(l.name) + '</td><td class=rpr>' + esc(l.qty == null ? '' : l.qty) + '</td>' +
        (fin ? '<td class=rpr>' + esc(l.price == null ? '' : Number(l.price).toFixed(2)) + '</td><td class=rpr>' + money(l.amount) + '</td>' : '') + '</tr>';
    });
    if (!rec.lines.length) h += '<tr><td colspan=' + (fin ? 5 : 3) + ' class=rpna>(no lines carried in the bundle)</td></tr>';
    h += '</tbody></table>';
    if (fin) {
      h += '<div class=rptot><div><span>Subtotal</span><span>' + money(rec.subtotal) + '</span></div>' +
        '<div><span>Tax</span><span>' + money(rec.tax) + '</span></div>' +
        '<div class=rpgrand><span>Total</span><span>' + money(rec.total) + '</span></div></div>';
    } else {
      h += '<div class=rptot rpnafin><div class=rpna>non-financial document — qty only, no money columns carried</div></div>';
    }
    h += '<div class=rpfoot>folded from the bundle — every amount is a re-sum of the rows, no value is hand-authored</div>';
    panel.innerHTML = h; panel.className = 'open';
    panel.querySelector('.rpx').addEventListener('click', close);
  }
  function renderUnsupported(key) {
    panel.innerHTML = '<span class=rpx title=close>✕</span><div class=rph><span class=rpglyph>▤</span> Receipt — ' + esc(fname(key)) + '</div>' +
      '<div class=rpfoot rpna>No header/line receipt for this document kind in the bundle. (Report folds documents that have a line table — orders, invoices, shipments.)</div>';
    panel.className = 'open';
    panel.querySelector('.rpx').addEventListener('click', close);
  }
  function close() { panel.className = ''; panel.innerHTML = ''; }

  // ── react to the ring's key-addressed Report intent (no import of crud_overlay — the bus is the seam) ──
  global.addEventListener('overlay:report', function (ev) { var d = ev && ev.detail; if (d && d.key) show(d.key); });

  function esc(s) { return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  function fname(k) { return (typeof global.fname === 'function') ? global.fname(k) : k; }

  function injectCss() {
    var css = document.createElement('style');
    css.textContent =
      '#reportPanel{position:fixed;z-index:76;right:14px;top:64px;width:min(380px,94vw);max-height:78vh;overflow:auto;' +
        'background:#120d16;border:1px solid #4a2f44;border-radius:12px;padding:14px 16px;color:#ecdcea;' +
        'font:13px/1.5 system-ui;box-shadow:0 10px 40px rgba(0,0,0,.65);display:none}' +
      '#reportPanel.open{display:block}' +
      '#reportPanel .rpx{position:absolute;right:11px;top:9px;color:#a07f99;cursor:pointer}' +
      '#reportPanel .rph{font-weight:600;font-size:15.5px;margin:0 22px 10px 0;color:#fbeaf7}' +
      '#reportPanel .rpglyph{color:#9fdfe8}' +
      '#reportPanel .rpmeta{display:flex;gap:16px;flex-wrap:wrap;font-size:12px;color:#c4a8c0;margin:0 0 10px}' +
      '#reportPanel .rpmeta b{color:#8a6f86;font-weight:600;margin-right:4px}' +
      '#reportPanel .rptbl{width:100%;border-collapse:collapse;font-size:12.5px}' +
      '#reportPanel .rptbl th{text-align:left;color:#8a6f86;font-weight:600;border-bottom:1px solid #3a2b38;padding:3px 6px}' +
      '#reportPanel .rptbl td{padding:3px 6px;border-bottom:1px solid #221826}' +
      '#reportPanel .rpr{text-align:right;font-variant-numeric:tabular-nums}' +
      '#reportPanel .rptot{margin-top:10px;font-size:13px}' +
      '#reportPanel .rptot>div{display:flex;justify-content:space-between;padding:2px 6px;font-variant-numeric:tabular-nums}' +
      '#reportPanel .rptot>div>span:first-child{color:#c4a8c0}' +
      '#reportPanel .rpgrand{border-top:1px solid #3a2b38;margin-top:3px;padding-top:5px;font-weight:600;color:#bff0dd}' +
      '#reportPanel .rpna{color:#8a6f86;font-style:italic}' +
      '#reportPanel .rpfoot{margin-top:11px;font-size:11px;color:#8a6f86;font-style:italic;line-height:1.4}';
    document.head.appendChild(css);
  }

  global.__report = { show: show, core: CORE, panel: function () { return panel; }, close: close };
  console.log('§REPORT layer mounted (read face — ▤ Report)');
})(typeof window !== 'undefined' ? window : this);
