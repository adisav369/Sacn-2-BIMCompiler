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
  function round2(n) { return Math.round((n + Number.EPSILON) * 100) / 100; }  // non-money use only (qty/price display)

  // ── exact money (feedback_numbers_via_bigdecimal · ENGINE_FULL_ERP_ISSUES.md §I-L) ──────────────
  // Every monetary accumulation in the folds below goes through BigDecimal (== java.math.BigDecimal, proven),
  // NOT raw Number+round2. raw round2 = Math.round (rounds .5 toward +inf) diverges from Java HALF_UP
  // (away-from-zero) on negative half-cents, masks TB imbalances, and drops cents past 2^53c — all MEASURED.
  // Source values arrive from SQLite as Number|string; route through String so BigDecimal.of never sees a
  // non-integer float (it throws by contract). money2() is the single DISPLAY leaf (re-enters Number; this is
  // a PURE-READ fold, nothing here is hashed). BigDecimal is loaded before this file in glassbowl.html; under
  // node the witness requires it.
  var BD = (typeof BigDecimal !== 'undefined') ? BigDecimal
         : (typeof require === 'function' ? require('./bigdecimal.js') : null);
  function bd(v) { return (v == null || v === '') ? BD.ZERO : BD.of(String(v)); }       // null/'' → exact 0
  // money2 — the single money LEAF: exact 2dp HALF_UP, carried as a STRING so no value re-enters Number even
  // past 2^53 cents (render formats via Number(x).toFixed(2), which is fine on a 2dp string). Never lossy.
  function money2(b) { return b == null ? null : b.setScale(2, BD.RoundingMode.HALF_UP).toString(); }

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
    // EXACT: subtotal = Σ line amount, tax = total − subtotal — all BigDecimal off the RAW rows (never the
    // display Number), one money2 leaf each. tax-as-remainder is exactly where negative half-cents bite.
    var subtotalB = fin ? (lines || []).reduce(function (acc, r) { return acc.add(bd(r[map.amount])); }, BD.ZERO) : null;
    var totalB = map.total ? bd(header[map.total]) : null;
    var taxB = (subtotalB != null && totalB != null) ? totalB.subtract(subtotalB) : null;
    var subtotal = money2(subtotalB), total = (totalB != null ? money2(totalB) : null), tax = money2(taxB);
    return {
      key: map.key, id: header[map.pk], docno: header.documentno,
      date: (map.date && header[map.date] != null) ? String(header[map.date]).slice(0, 10) : null,
      partner: (names.partner != null ? names.partner
                 : (header.c_bpartner_id != null ? '#' + header.c_bpartner_id : null)),
      lines: outLines, subtotal: subtotal, tax: tax, total: total,
      financial: fin, foldsFrom: 'bundle'
    };
  }

  // foldTrialBalance — fold the posted journal into a Trial Balance (R2). PURE: group fact_acct rows by
  // account, sum Dr/Cr, join account meta (value/name/type from c_elementvalue). The proof is that ΣDr==ΣCr
  // to the cent — a structural property of double-entry, re-derived here, never asserted. accounts = {id:{value,name,accounttype}}.
  function foldTrialBalance(factRows, accounts) {
    accounts = accounts || {};
    var by = {};
    (factRows || []).forEach(function (r) {
      var a = r.account_id; if (!by[a]) by[a] = { account_id: a, dr: BD.ZERO, cr: BD.ZERO };
      by[a].dr = by[a].dr.add(bd(r.amtacctdr)); by[a].cr = by[a].cr.add(bd(r.amtacctcr));   // EXACT
    });
    var totDrB = BD.ZERO, totCrB = BD.ZERO;
    var lines = Object.keys(by).map(function (a) {
      var b = by[a], m = accounts[a] || {};
      totDrB = totDrB.add(b.dr); totCrB = totCrB.add(b.cr);
      return { account_id: +a, value: m.value != null ? m.value : ('#' + a), name: m.name != null ? m.name : null,
               type: m.accounttype != null ? m.accounttype : null,
               dr: money2(b.dr), cr: money2(b.cr), balance: money2(b.dr.subtract(b.cr)) };  // balance: signed → HALF_UP exact
    }).sort(function (x, y) { return String(x.value).localeCompare(String(y.value)); });
    // balanced is EXACT (isZero), never a float compare — a 3rd-decimal credit can no longer mask an imbalance.
    var diffB = totDrB.subtract(totCrB);
    var diffC = Number(diffB.multiply(bd('100')).setScale(0, BD.RoundingMode.HALF_UP).toString());
    return { lines: lines, totalDr: money2(totDrB), totalCr: money2(totCrB), balanced: diffB.isZero(),
             maxDiffCents: diffC, rows: (factRows || []).length, foldsFrom: 'fact_acct' };
  }

  // foldPnL — fold the P&L from the same journal: revenue accounts (type R, natural credit) and expense
  // accounts (type E, natural debit). netIncome = revenue − expense. Asset/Liability/Equity (A/L/O) excluded.
  function foldPnL(factRows, accounts) {
    accounts = accounts || {};
    var by = {};
    (factRows || []).forEach(function (r) {
      var m = accounts[r.account_id] || {}, t = m.accounttype;
      if (t !== 'R' && t !== 'E') return;
      var a = r.account_id; if (!by[a]) by[a] = { account_id: a, type: t, value: m.value, name: m.name, net: BD.ZERO };
      // net is a SIGNED fold (cr−dr for revenue, dr−cr for expense) — exactly the half-cent trap. EXACT.
      by[a].net = (t === 'R') ? by[a].net.add(bd(r.amtacctcr)).subtract(bd(r.amtacctdr))
                              : by[a].net.add(bd(r.amtacctdr)).subtract(bd(r.amtacctcr));
    });
    var revenueB = BD.ZERO, expenseB = BD.ZERO;
    var lines = Object.keys(by).map(function (a) {
      var b = by[a]; if (b.type === 'R') revenueB = revenueB.add(b.net); else expenseB = expenseB.add(b.net);
      return { account_id: b.account_id, type: b.type, value: b.value, name: b.name, net: money2(b.net) };
    }).sort(function (x, y) { return String(x.value).localeCompare(String(y.value)); });
    return { lines: lines, revenue: money2(revenueB), expense: money2(expenseB),
             netIncome: money2(revenueB.subtract(expenseB)), foldsFrom: 'fact_acct' };
  }

  // ════════════════════════════════════════════════════════════════════════
  // R5 — the SIGNED, SELF-VERIFYING receipt (§RPT-SEND · CRUD_P_R_REPORT_SPEC §6).
  // The receipt stops being view-only HTML: it carries the SIGNED OP-LOG CHAIN so a recipient can
  // REPLAY it and verify the chain hash (chainOk) with no server and no trust in the sender. This is
  // HolyGrail condition (3) made consumer-visible — the op-log's tamper-evidence LEAVES the app.
  //
  // NON-INVENT / HONESTY BOUNDARY (SPEC §6.1, §I-K/§13.6): the payload attests "the recorded, signed
  // op-chain (tamper-evident)" — NEVER "the books posted". Completed ≠ posted; GL posting is delegated
  // install-side. The `attests` field and the verify-panel copy say exactly that, no GL over-claim.
  //
  // NO FORKED VERB: canonicalOp is the PRODUCTION kernel_ops `_canonical` form, verbatim
  //   (id|timestamp|op_type|parameters|input_guids|output_guid); op_hash = SHA-256(prev_hash|canonical);
  //   sig attests OVER op_hash (not in the hash → chain byte-identical across devices). The browser pulls
  //   ALREADY-SEALED rows from kernel_ops (sealChain/setSigner did the work); the witness reuses the same
  //   canonical + the poc_sign ECDSA primitives. Hashing/verify are INJECTED (host = node crypto OR
  //   browser crypto.subtle) so this CORE stays pure and runs in both — exactly the _signer pattern.
  var GENESIS = '0'.repeat(64);
  // canonicalOp — MUST equal kernel_ops.js _canonical byte-for-byte (the chain the live engine sealed).
  function canonicalOp(op) {
    return op.id + '|' + op.timestamp + '|' + op.op_type + '|' +
           (op.parameters || '') + '|' + (op.input_guids || '') + '|' + (op.output_guid || '');
  }

  // buildSignedReceipt — assemble the §RPT-SEND payload: the folded rec + the signed op-chain.
  // signedOps = the already-sealed kernel_ops rows (id/timestamp/op_type/parameters/input_guids/
  // output_guid/prev_hash/op_hash/sig), pubKeyHex = the issuer SPKI hex (null ⇒ W-CHAIN-only). PURE:
  // copies rows, never re-hashes/re-signs (the engine owns that). tip = last op_hash.
  function buildSignedReceipt(rec, signedOps, pubKeyHex) {
    var ops = (signedOps || []).map(function (o) {
      return { id: o.id, timestamp: o.timestamp, op_type: o.op_type, parameters: o.parameters,
               input_guids: o.input_guids != null ? o.input_guids : null,
               output_guid: o.output_guid != null ? o.output_guid : null,
               prev_hash: o.prev_hash, op_hash: o.op_hash, sig: o.sig != null ? o.sig : null };
    });
    return { v: 1, kind: 'erp-signed-receipt', rec: rec,
      chain: { alg: 'SHA-256', sigAlg: 'ECDSA-P256', genesis: GENESIS,
               pubKey: pubKeyHex != null ? pubKeyHex : null,
               tip: ops.length ? ops[ops.length - 1].op_hash : GENESIS, ops: ops },
      attests: 'the recorded, signed op-chain (tamper-evident) — NOT a GL posting' };
  }

  // verifySignedReceipt — INDEPENDENT, server-free replay of the embedded chain (SPEC §6.3). Re-walks
  // the ops, recomputes each op_hash from (prev|canonical), checks the prev_hash link, and (if pubKey
  // present) the signature — exactly as kernel_ops.verifyChain / poc_chain / poc_sign. Returns
  // {chainOk, tip, len, brokeAt?, why?, tipMatches, recBoundOk}. host = { sha256(str)->hex,
  // verifySig(hashHex,sigHex,pubKeyHex)->bool } injected by the caller (node crypto / browser subtle).
  // ASYNC because crypto.subtle is async; node crypto is wrapped to a resolved Promise by the host.
  async function verifySignedReceipt(payload, host) {
    if (!payload || payload.kind !== 'erp-signed-receipt' || !payload.chain)
      return { chainOk: false, why: 'not-a-signed-receipt', tip: GENESIS, len: 0 };
    var ch = payload.chain, ops = ch.ops || [], prev = ch.genesis || GENESIS, pub = ch.pubKey || null;
    for (var i = 0; i < ops.length; i++) {
      var op = ops[i];
      if (op.prev_hash !== prev) return { chainOk: false, brokeAt: op.id, why: 'prev_hash link', tip: prev, len: i };
      var h = await host.sha256(prev + '|' + canonicalOp(op));
      if (h !== op.op_hash) return { chainOk: false, brokeAt: op.id, why: 'payload altered', tip: prev, len: i };
      if (pub) {
        var sigOk = await host.verifySig(op.op_hash, op.sig, pub);
        if (!sigOk) return { chainOk: false, brokeAt: op.id, why: 'signature', tip: prev, len: i };
      }
      prev = h;
    }
    var tipMatches = (prev === (ch.tip || GENESIS));
    // recBoundOk — the rec is the HUMAN face; the chain is the TRUTH. Re-derive the bound doc id from the
    // signed ops (a CREATE_DOCUMENT/SET_STATUS whose parameters carry the doc the rec folds) and compare
    // the DISPLAYED docno — never trust the rendered number. Best-effort: only asserts when a bound op
    // exists; absence is honest (recBound=null), not a fabricated pass.
    var recBound = null;
    if (payload.rec && payload.rec.docno != null) {
      var want = String(payload.rec.docno);
      var bound = ops.some(function (o) { return (o.parameters || '').indexOf(want) >= 0; });
      recBound = bound;
    }
    return { chainOk: true, tip: prev, len: ops.length, tipMatches: tipMatches,
             recBoundOk: recBound, attests: payload.attests };
  }

  var CORE = { REPORT_MAP: REPORT_MAP, foldReceipt: foldReceipt, foldTrialBalance: foldTrialBalance, foldPnL: foldPnL, round2: round2,
    // R5 §RPT-SEND — signed/self-verifying receipt (pure, host-injected crypto; reused by DOM + witness).
    GENESIS: GENESIS, canonicalOp: canonicalOp, buildSignedReceipt: buildSignedReceipt, verifySignedReceipt: verifySignedReceipt };
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

  // ── R4 after-the-receipt output — Print / Share / Save, edge-only, server-free (§RPT-OUT, CRUD_P_R_REPORT_SPEC).
  // All three serialize the ALREADY-FOLDED rec (no re-query, no invented value); the receipt is the same fold.
  function receiptText(rec) {
    var fin = rec.financial, L = [];
    L.push('Receipt — ' + fname(rec.key) + ' #' + (rec.docno == null ? rec.id : rec.docno));
    if (rec.partner) L.push('Partner: ' + rec.partner);
    if (rec.date) L.push('Date: ' + rec.date);
    L.push('');
    rec.lines.forEach(function (l) {
      L.push('  ' + l.line + '. ' + l.name + '   qty ' + (l.qty == null ? '-' : l.qty) +
        (fin ? ('  @ ' + (l.price == null ? '-' : Number(l.price).toFixed(2)) +
          '  = ' + (l.amount == null ? 'n/a' : Number(l.amount).toFixed(2))) : ''));
    });
    if (!rec.lines.length) L.push('  (no lines carried in the bundle)');
    L.push('');
    if (fin) {
      L.push('Subtotal: ' + (rec.subtotal == null ? 'n/a' : Number(rec.subtotal).toFixed(2)));
      L.push('Tax:      ' + (rec.tax == null ? 'n/a' : Number(rec.tax).toFixed(2)));
      L.push('Total:    ' + (rec.total == null ? 'n/a' : Number(rec.total).toFixed(2)));
    } else { L.push('non-financial document — qty only, no money columns carried'); }
    L.push('');
    L.push('folded from the bundle — every amount is a re-sum of the rows, no value is hand-authored');
    return L.join('\n');
  }
  function receiptHtml(rec) {
    return '<!doctype html><meta charset=utf-8><title>Receipt ' +
      esc(rec.docno == null ? rec.id : rec.docno) + '</title>' +
      '<style>body{font:13px/1.5 system-ui;margin:24px;color:#111}h1{font-size:16px}' +
      'table{border-collapse:collapse;width:100%;margin:8px 0}th,td{border-bottom:1px solid #ccc;padding:4px 6px;text-align:left}' +
      '.r{text-align:right;font-variant-numeric:tabular-nums}.foot{color:#666;font-style:italic;font-size:11px;margin-top:10px}</style>' +
      '<pre style="font:13px/1.5 ui-monospace,monospace">' + esc(receiptText(rec)) + '</pre>';
  }
  function _filename(rec) { return 'receipt_' + String(fname(rec.key)).replace(/\W+/g, '_') + '_' + (rec.docno == null ? rec.id : rec.docno) + '.html'; }

  // ── R5 §RPT-SEND — the SIGNED, self-verifying receipt (CRUD_P_R_REPORT_SPEC §6) ───────────────
  // Browser crypto host injected into CORE.verifySignedReceipt: sha256 via crypto.subtle, ECDSA-P256
  // verify by importing the embedded SPKI pubKey hex. No fork — same canonical/verify as kernel_ops.
  function _hex2buf(h) { var a = new Uint8Array(h.length / 2); for (var i = 0; i < a.length; i++) a[i] = parseInt(h.substr(i * 2, 2), 16); return a.buffer; }
  function _buf2hex(b) { return Array.from(new Uint8Array(b)).map(function (x) { return x.toString(16).padStart(2, '0'); }).join(''); }
  function _browserHost() {
    return {
      sha256: function (str) {
        return crypto.subtle.digest('SHA-256', new TextEncoder().encode(str)).then(_buf2hex);
      },
      verifySig: function (hashHex, sigHex, pubHex) {
        if (!sigHex || !pubHex) return Promise.resolve(false);
        return crypto.subtle.importKey('spki', _hex2buf(pubHex), { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify'])
          .then(function (key) { return crypto.subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, key, _hex2buf(sigHex), new TextEncoder().encode(hashHex)); })
          .catch(function () { return false; });
      }
    };
  }

  // _pullSignedOps — read the ALREADY-SEALED rows from the signed sidecar op-log (crud_overlay owns it;
  // the documented seam is window.__crud.withSidecar — NO direct coupling, NO re-seal here). Returns the
  // ops carrying prev_hash/op_hash/sig. pubKey: the sidecar is W-CHAIN-only today (no setSigner → sig=null,
  // pubKey=null) — HONEST: still tamper-evident via the hash chain; if a signer is ever set, sig/pubKey
  // flow through unchanged. cb(payload|null).
  function _pullSignedOps(rec, cb) {
    if (!global.__crud || typeof global.__crud.withSidecar !== 'function') { cb(null); return; }
    global.__crud.withSidecar(function (db) {
      if (!db) { cb(null); return; }
      try {
        var res = db.exec('SELECT id,timestamp,op_type,parameters,input_guids,output_guid,prev_hash,op_hash,sig FROM kernel_ops WHERE op_hash IS NOT NULL ORDER BY id');
        var ops = rowsOf(res);
        if (!ops.length) { cb(null); return; }
        var pub = (typeof global.__erpPubKeyHex === 'string') ? global.__erpPubKeyHex : null;  // set iff a signer was installed
        cb(CORE.buildSignedReceipt(rec, ops, pub));
      } catch (e) { console.warn('§RPT-SEND pull error', e && e.message); cb(null); }
    });
  }

  // signedReceiptHtml — the SELF-CONTAINED deliverable: the human receipt + the signed chain embedded as
  // JSON + an INLINE re-verifier so opening the file re-checks chainOk with NO server / NO original db.
  function signedReceiptHtml(payload) {
    var json = JSON.stringify(payload);
    var safe = json.replace(/</g, '\\u003c');   // never let payload break out of the <script> block
    return '<!doctype html><meta charset=utf-8><title>Signed Receipt ' + esc(payload.rec.docno == null ? payload.rec.id : payload.rec.docno) + '</title>' +
      '<style>body{font:13px/1.5 system-ui;margin:24px;color:#111}#vf{padding:8px 12px;border-radius:8px;margin:10px 0;font-weight:600}' +
      '.ok{background:#e6f7ec;color:#0a6b2e;border:1px solid #0a6b2e}.bad{background:#fdecec;color:#a11;border:1px solid #a11}' +
      '.foot{color:#666;font-style:italic;font-size:11px;margin-top:10px}pre{font:13px/1.5 ui-monospace,monospace}</style>' +
      '<h2>Signed receipt — ' + esc(fname(payload.rec.key)) + ' #' + esc(payload.rec.docno == null ? payload.rec.id : payload.rec.docno) + '</h2>' +
      '<div id=vf>Verifying the signed op-chain…</div>' +
      '<pre>' + esc(receiptText(payload.rec)) + '</pre>' +
      '<div class=foot>This receipt carries its signed op-chain. Verify replays the chain locally and checks the ' +
      'tamper-evident hash' + (payload.chain.pubKey ? ' + signature' : '') + ' — it attests the RECORDED ops, ' +
      'NOT that the books were posted (GL posting is delegated install-side).</div>' +
      '<script type="application/erp-receipt+json" id=erpReceipt>' + safe + '</' + 'script>' +
      '<script>(' + _selfVerifierSrc() + ')();</' + 'script>';
  }
  // _selfVerifierSrc — the inline verifier shipped INSIDE the receipt HTML. Self-contained: re-implements
  // the SAME canonical + hash/sig check (no external load), parses the embedded JSON, paints chainOk.
  function _selfVerifierSrc() {
    return "async function(){function cn(o){return o.id+'|'+o.timestamp+'|'+o.op_type+'|'+(o.parameters||'')+'|'+(o.input_guids||'')+'|'+(o.output_guid||'');}" +
      "function h2b(h){var a=new Uint8Array(h.length/2);for(var i=0;i<a.length;i++)a[i]=parseInt(h.substr(i*2,2),16);return a.buffer;}" +
      "function b2h(b){return Array.from(new Uint8Array(b)).map(function(x){return x.toString(16).padStart(2,'0');}).join('');}" +
      "async function sha(s){return b2h(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s)));}" +
      "var el=document.getElementById('vf');var P;try{P=JSON.parse(document.getElementById('erpReceipt').textContent);}catch(e){el.className='bad';el.textContent='Cannot read embedded receipt.';return;}" +
      "var ch=P.chain,ops=ch.ops||[],prev=ch.genesis,pub=ch.pubKey,key=null;" +
      "if(pub){try{key=await crypto.subtle.importKey('spki',h2b(pub),{name:'ECDSA',namedCurve:'P-256'},false,['verify']);}catch(e){}}" +
      "for(var i=0;i<ops.length;i++){var op=ops[i];if(op.prev_hash!==prev){el.className='bad';el.textContent='TAMPERED — chain link broken at op '+op.id;return;}" +
      "var hh=await sha(prev+'|'+cn(op));if(hh!==op.op_hash){el.className='bad';el.textContent='TAMPERED — op '+op.id+' altered (hash mismatch).';return;}" +
      "if(key){var sv=await crypto.subtle.verify({name:'ECDSA',hash:'SHA-256'},key,h2b(op.sig),new TextEncoder().encode(op.op_hash));if(!sv){el.className='bad';el.textContent='TAMPERED — bad signature at op '+op.id+'.';return;}}prev=hh;}" +
      "var tipOk=prev===ch.tip;el.className=tipOk?'ok':'bad';el.textContent=(tipOk?'\\u2713 VERIFIED':'TIP MISMATCH')+' — '+ops.length+' ops, chain'+(pub?'+signature':'')+' intact, tip '+prev.slice(0,12)+'\\u2026 ('+P.attests+').';}";
  }

  // _verifyInPanel — the in-app Verify affordance: pull the signed payload, replay it via CORE
  // (the INDEPENDENT verifier), paint chainOk/tip honestly. §RPT-SEND-VERIFY.
  function _verifyInPanel(rec) {
    var slot = panel.querySelector('.rpvf'); if (!slot) return;
    slot.textContent = 'Verifying signed op-chain…'; slot.className = 'rpvf';
    _pullSignedOps(rec, function (payload) {
      if (!payload) { slot.className = 'rpvf rpna'; slot.textContent = 'No signed op-chain yet for this document — Process it first to record + sign an op (then the receipt is verifiable).'; console.log('§RPT-SEND verify key=' + rec.key + ' no-chain'); return; }
      CORE.verifySignedReceipt(payload, _browserHost()).then(function (v) {
        if (v.chainOk && v.tipMatches) { slot.className = 'rpvf rpok'; slot.textContent = '✓ Verified — ' + payload.chain.ops.length + ' ops, chain' + (payload.chain.pubKey ? '+signature' : '') + ' intact, tip ' + String(v.tip).slice(0, 12) + '… (attests the recorded op-chain, not a GL posting).'; }
        else { slot.className = 'rpvf rpbad'; slot.textContent = 'TAMPERED — ' + (v.why || 'mismatch') + (v.brokeAt != null ? ' at op ' + v.brokeAt : ''); }
        console.log('§RPT-SEND verify key=' + rec.key + ' chainOk=' + v.chainOk + ' tipMatches=' + v.tipMatches + ' tip=' + String(v.tip).slice(0, 12) + ' ops=' + payload.chain.ops.length + ' signed=' + (payload.chain.pubKey ? 'Y' : 'N(W-CHAIN-only)') + ' recBoundOk=' + v.recBoundOk + (v.why ? ' why=' + v.why : ''));
      });
    });
  }
  // _sendSigned — Share/Save the SIGNED receipt (the signed HTML), not the view-only one. §RPT-SEND.
  function _sendSigned(rec, mode) {
    _pullSignedOps(rec, function (payload) {
      if (!payload) { console.log('§RPT-SEND ' + mode + ' key=' + rec.key + ' no-chain → fell back to unsigned receipt'); var html0 = receiptHtml(rec); _deliver(html0, _filename(rec), receiptText(rec), mode, false); return; }
      var html = signedReceiptHtml(payload), fn = _filename(rec).replace(/\.html$/, '.signed.html');
      var txt = receiptText(rec) + '\n\n[signed op-chain attached — ' + payload.chain.ops.length + ' ops, tip ' + String(payload.chain.tip).slice(0, 12) + '…; open the file to re-verify offline]';
      _deliver(html, fn, txt, mode, true);
      console.log('§RPT-SEND ' + mode + ' key=' + rec.key + ' signed=Y ops=' + payload.chain.ops.length + ' tip=' + String(payload.chain.tip).slice(0, 12) + ' file=' + fn);
    });
  }
  function _deliver(html, fn, txt, mode, signed) {
    if (mode === 'save') {
      var blob = new Blob([html], { type: 'text/html' }), url = URL.createObjectURL(blob), aEl = document.createElement('a');
      aEl.href = url; aEl.download = fn; document.body.appendChild(aEl); aEl.click();
      setTimeout(function () { aEl.remove(); URL.revokeObjectURL(url); }, 500);
      console.log('§RPT-OUT save=blob signed=' + (signed ? 'Y' : 'N') + ' file=' + fn);
    } else {  // share
      try {
        if (navigator.canShare && typeof File !== 'undefined') {
          var file = new File([html], fn, { type: 'text/html' });
          if (navigator.canShare({ files: [file] })) { navigator.share({ files: [file], title: 'Signed Receipt', text: txt }).catch(function () {}); console.log('§RPT-OUT share=files signed=' + (signed ? 'Y' : 'N') + ' key=' + fn); return; }
        }
        if (navigator.share) { navigator.share({ title: 'Signed Receipt', text: txt }).catch(function () {}); console.log('§RPT-OUT share=text signed=' + (signed ? 'Y' : 'N')); }
        else if (navigator.clipboard) { navigator.clipboard.writeText(txt); console.log('§RPT-OUT share=clipboard signed=' + (signed ? 'Y' : 'N')); }
        else { console.log('§RPT-OUT share=unsupported'); }
      } catch (e) { console.warn('§RPT-OUT share error', e && e.message); }
    }
  }

  function _wireOut(rec) {
    var bar = panel.querySelector('.rpact'); if (!bar) return;
    bar.addEventListener('click', function (ev) {
      var b = ev.target.closest('button[data-a]'); if (!b) return;
      var a = b.dataset.a;
      if (a === 'verify') { _verifyInPanel(rec); return; }        // R5: replay + verify the signed op-chain
      if (a === 'share')  { _sendSigned(rec, 'share'); return; }  // R5: deliver the SIGNED receipt
      if (a === 'save')   { _sendSigned(rec, 'save'); return; }   // R5: deliver the SIGNED receipt
      if (a === 'print') {                                        // R4: local print of the receipt (view-only)
        var ifr = document.createElement('iframe');
        ifr.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0';
        document.body.appendChild(ifr);
        var d = ifr.contentWindow.document; d.open(); d.write(receiptHtml(rec)); d.close();
        ifr.contentWindow.focus(); ifr.contentWindow.print();
        setTimeout(function () { ifr.remove(); }, 1000);
        console.log('§RPT-OUT print key=' + rec.key + ' doc=' + (rec.docno == null ? rec.id : rec.docno));
      }
    });
  }

  function _outBar() {
    return '<div class=rpact><button class=rpb data-a=verify title="Replay + verify the signed op-chain">Verify</button>' +
      '<button class=rpb data-a=print title="Print receipt">Print</button>' +
      '<button class=rpb data-a=share title="Share signed receipt">Share</button>' +
      '<button class=rpb data-a=save title="Save signed, self-verifying receipt">Save</button></div>';
  }

  function render(rec) {
    var fin = rec.financial;
    var h = '<span class=rpx title=close>✕</span>' + _outBar() +
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
    h += '<div class="rpvf rpidle">Verify replays this document’s signed op-chain locally (no server) — it attests the recorded ops, not a GL posting.</div>';
    panel.innerHTML = h; panel.className = 'open';
    panel.querySelector('.rpx').addEventListener('click', close);
    _wireOut(rec);   // R4 Print + R5 Verify / signed Share / signed Save
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
      '#reportPanel .rpact{position:absolute;right:30px;top:8px;display:flex;gap:5px}' +
      '#reportPanel .rpb{background:#241a23;border:1px solid #4a2f44;color:#ecdcea;border-radius:7px;' +
        'font:11px/1 system-ui;padding:5px 9px;cursor:pointer}' +
      '#reportPanel .rpb:hover{background:#33232f;color:#fbeaf7}' +
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
      '#reportPanel .rpfoot{margin-top:11px;font-size:11px;color:#8a6f86;font-style:italic;line-height:1.4}' +
      '#reportPanel .rpvf{margin-top:10px;font-size:11.5px;line-height:1.45;border-radius:8px;padding:7px 9px}' +
      '#reportPanel .rpvf.rpidle{color:#8a6f86;font-style:italic;background:transparent;padding:7px 0}' +
      '#reportPanel .rpvf.rpok{background:#10301f;border:1px solid #1f7a47;color:#bff0dd;font-style:normal}' +
      '#reportPanel .rpvf.rpbad{background:#3a1414;border:1px solid #8a2a2a;color:#ffb4b4;font-style:normal;font-weight:600}';
    document.head.appendChild(css);
  }

  global.__report = { show: show, core: CORE, panel: function () { return panel; }, close: close,
    // §DEBUG — whitebox accessors for the R4/R5 witnesses (render a fold; build/verify/serialize the signed receipt)
    _test: { render: render, receiptText: receiptText, receiptHtml: receiptHtml,
             signedReceiptHtml: signedReceiptHtml, browserHost: _browserHost,
             buildSignedReceipt: CORE.buildSignedReceipt, verifySignedReceipt: CORE.verifySignedReceipt } };
  console.log('§REPORT layer mounted (read face — ▤ Report)');
})(typeof window !== 'undefined' ? window : this);
