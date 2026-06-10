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
  // foldStatement — the generic PA_Report fold (W-PA-REPORT).
  // SPEC (docs/ReportingFold.md §4b edit is permission-blocked this session — the AUTHORITATIVE spec lives HERE
  //   as this header comment; mirror it back to the doc when writable):
  // Implementing ReportingFold.md §4b — Witness: W-PA-REPORT (scripts/poc_pa_report.js → build/erp/poc_pa_report.log)
  //
  //   A financial statement = a (lines × columns) matrix of signed journal sums, driven ENTIRELY by standard
  //   iDempiere PA_* metadata — no hardcoded statement. Generalises foldTrialBalance/foldPnL into one verb:
  //   a new statement is a pa_report ROW, not new code.
  //
  // PURE: host injects every row; no DB/clock/DOM; integer-cents via BigDecimal; runs under sql.js unchanged.
  //   foldStatement(report, lines, cols, sourcesByLine, factRows, accounts, periodWindows) -> {cells,lineOrder,colOrder}
  //   - report: one pa_report row — c_acctschema_id SCOPES the fold (seed: schema 101).
  //   - lines:  pa_reportline rows, SeqNo-ordered. linetype 'S'=segment, 'C'=calc (calculationtype
  //             A=add / S=subtract / R=range / P=percent over oper_1_id,oper_2_id line ids).
  //   - cols:   pa_reportcolumn rows, SeqNo-ordered. columntype 'C'=calc column (combined last), else relative.
  //             paamounttype in B/S/C/D/Q/R -> amount expr (== MReportColumn.getSelectClause, verbatim):
  //               B->acctBalance(acct,Dr,Cr) . S->Dr-Cr . C->Cr . D->Dr . Q->Qty . R->acctBalance(acct,Qty,0).
  //             A LINE's own paamounttype overrides the column's (FinReport.insertLine). postingtype filters facts.
  //   - sourcesByLine: { lineId:[{account_ids:[..LEAF ids..]}] } — host PRE-EXPANDS each pa_reportsource
  //             c_elementvalue_id to leaf accounts via the EV account tree (ad_treenode; MReportTree rule:
  //             a SUMMARY node -> all non-summary descendants, a leaf -> itself). Verb takes leaf id sets -> stays pure.
  //   - accounts: { id:{accounttype,accountsign} } — for the natural-sign rule.
  //   - periodWindows: { colId: Set<c_period_id> } — host resolves relativeperiod/paperiodtype over the calendar
  //             (bundle fact_acct has NO dateacct, only c_period_id; FinReportPeriod date windows -> c_period sets:
  //              'P'=target period . 'Y'=periods in target's fiscal year up to target . 'T'=all periods <= target).
  //
  // SIGN — natural balance (== live acctbalance() PL/pgSQL, re-verified): bal=dr-cr; if accountsign='N'
  //   (ALL 379 seed accounts confirmed 'N') then accounttype in (A,E) => Debit-natural (keep dr-cr) else
  //   Credit-natural (bal=cr-dr). SAME logic foldPnL already ships. Non-natural sign => name-defer (none in 100/101).
  //
  // THREE PASSES, one integer-cents fold, NO mid-fold rounding (signed half-cent trap) — HALF_UP scale-2 at OUTPUT:
  //   1. S-lines: per segment line x non-calc column, Sum amountExpr(col) over facts where account in leafSources(line)
  //      AND c_period_id in periodWindows[col] AND c_acctschema_id=report.c_acctschema_id AND (postingtype=col.pt if set).
  //   2. C-lines (calc tree, SeqNo order so earlier results visible — matches FinReport.doCalculations):
  //      A=cell[o1]+cell[o2] . S=cell[o1]-cell[o2] . R(range)=Sum lines with SeqNo BETWEEN o1.seq..o2.seq (inclusive,
  //      == getLineIDs) . P(percent)=cell[o1]/cell[o2]*100. Per column.
  //   3. Calc columns (columntype 'C'): combine columns via col.oper_1_id/oper_2_id+calculationtype, same A/S/R/P.
  // ════════════════════════════════════════════════════════════════════════

  // amtExpr — the per-row amount per PA AmountType (== MReportColumn.getSelectClause). Returns a BD. acct sign
  // applied only for B (acctBalance) and R (acctBalance over Qty). S/C/D/Q are raw, exactly as FinReport's SQL.
  function amtExpr(amt, r, sign) {
    switch (amt) {
      case 'B': return signedBalance(bd(r.amtacctdr).subtract(bd(r.amtacctcr)), sign);   // acctBalance(Dr,Cr)
      case 'S': return bd(r.amtacctdr).subtract(bd(r.amtacctcr));                         // Dr-Cr (accounted sign)
      case 'C': return bd(r.amtacctcr);                                                   // Cr only
      case 'D': return bd(r.amtacctdr);                                                   // Dr only
      case 'Q': return bd(r.qty);                                                         // Qty (accounted sign)
      case 'R': return signedBalance(bd(r.qty), sign);                                    // acctBalance(Qty,0)
      default:  return BD.ZERO;                                                           // unknown -> 0 (FinReport SEVERE)
    }
  }
  // signedBalance — flip dr-cr balance to cr-dr for credit-natural accounts (== acctbalance() PL/pgSQL).
  // sign = {accounttype, accountsign}. accountsign!=='N' name-deferred upstream; here 'N' -> A/E debit, else credit.
  function signedBalance(drMinusCr, sign) {
    if (!sign) return drMinusCr;                                  // unknown account -> raw dr-cr (== PL/pgSQL fallback)
    var as = sign.accountsign;
    if (as === 'N' || as == null) as = (sign.accounttype === 'A' || sign.accounttype === 'E') ? 'D' : 'C';
    return (as === 'C') ? drMinusCr.negate() : drMinusCr;        // credit-natural => cr-dr = -(dr-cr)
  }

  // foldStatement — see the spec block above. Returns { cells:{lineId:{colId:<money2 string>}}, lineOrder, colOrder,
  //   schema, foldsFrom }. Cells accumulate as BD, only money2()'d at output (one HALF_UP leaf).
  function foldStatement(report, lines, cols, sourcesByLine, factRows, accounts, periodWindows) {
    report = report || {}; lines = lines || []; cols = cols || []; sourcesByLine = sourcesByLine || {};
    factRows = factRows || []; accounts = accounts || {}; periodWindows = periodWindows || {};
    var schema = report.c_acctschema_id;
    var segCols = cols.filter(function (c) { return c.columntype !== 'C'; });   // non-calculation columns
    var calcCols = cols.filter(function (c) { return c.columntype === 'C'; });
    var factByAcct = {};                                                        // index facts by account (pure in-memory)
    factRows.forEach(function (r) {
      if (schema != null && r.c_acctschema_id != schema) return;                // scope to the report's acctschema
      (factByAcct[r.account_id] = factByAcct[r.account_id] || []).push(r);
    });
    var cellsBD = {};                                                            // {lineId:{colId:BD}}
    lines.forEach(function (l) { cellsBD[l.pa_reportline_id] = {}; });
    var lineById = {}; lines.forEach(function (l) { lineById[l.pa_reportline_id] = l; });

    // ── PASS 1: segment ('S') lines x segment columns ───────────────────────
    lines.forEach(function (l) {
      if (l.linetype !== 'S') return;
      var srcs = sourcesByLine[l.pa_reportline_id] || [];
      var acctSet = {};
      srcs.forEach(function (s) { (s.account_ids || []).forEach(function (a) { acctSet[a] = 1; }); });
      var lineAmt = (l.paamounttype != null && l.paamounttype !== '') ? l.paamounttype : null;  // line overrides column
      segCols.forEach(function (c) {
        var amt = lineAmt || c.paamounttype;
        var win = periodWindows[c.pa_reportcolumn_id];                           // Set (has .has) or null = all periods
        var pt = c.postingtype;
        var sum = BD.ZERO;
        Object.keys(acctSet).forEach(function (a) {
          var sign = accounts[a] || null;
          (factByAcct[a] || []).forEach(function (r) {
            if (win && !win.has(r.c_period_id)) return;
            if (pt != null && pt !== '' && r.postingtype != null && r.postingtype !== pt) return;
            sum = sum.add(amtExpr(amt, r, sign));                                 // EXACT, no mid-round
          });
        });
        cellsBD[l.pa_reportline_id][c.pa_reportcolumn_id] = sum;
      });
    });

    // ── PASS 2: calculation ('C') lines, in SeqNo order, per segment column ──
    function cellOr0(lid, cid) { var row = cellsBD[lid]; return (row && row[cid] != null) ? row[cid] : BD.ZERO; }
    lines.forEach(function (l) {
      if (l.linetype !== 'C') return;
      var ct = l.calculationtype, o1 = l.oper_1_id, o2 = l.oper_2_id;
      segCols.forEach(function (c) {
        var cid = c.pa_reportcolumn_id, v;
        if (ct === 'A') { v = cellOr0(o1, cid).add(cellOr0(o2, cid)); }
        else if (ct === 'S') { v = cellOr0(o1, cid).subtract(cellOr0(o2, cid)); }
        else if (ct === 'R') {                                                   // range: Sum lines SeqNo o1..o2 (incl)
          var s1 = lineById[o1] ? Number(lineById[o1].seqno) : null;
          var s2 = lineById[o2] ? Number(lineById[o2].seqno) : null;
          if (s1 != null && s2 != null) { var lo = Math.min(s1, s2), hi = Math.max(s1, s2);
            v = BD.ZERO;
            lines.forEach(function (rl) { var sq = Number(rl.seqno); if (sq >= lo && sq <= hi) v = v.add(cellOr0(rl.pa_reportline_id, cid)); });
          } else v = BD.ZERO;
        } else if (ct === 'P') {                                                 // percent: o1/o2*100 (0 denom -> 0)
          var d = cellOr0(o2, cid);
          v = d.isZero() ? BD.ZERO : cellOr0(o1, cid).divide(d, 10, BD.RoundingMode.HALF_UP).multiply(bd('100'));
        } else { v = BD.ZERO; }
        cellsBD[l.pa_reportline_id][cid] = v;
      });
    });

    // ── PASS 3: calculation columns (columntype 'C'), per line ──────────────
    // R (range) = INCLUSIVE sum of every column between oper_1 and oper_2 by COLUMN POSITION (== FinReport
    // doCalculations lines 992-997: COALESCE(Col_ii1,0)+COALESCE(Col_ii1+1,0)+…+COALESCE(Col_ii2,0), endpoints
    // swapped if reversed) — NOT the first operand. A=Col_o1+Col_o2 . S=Col_o1-Col_o2 . P=Col_o1/Col_o2*100.
    var colIdx = {}; cols.forEach(function (c, i) { colIdx[c.pa_reportcolumn_id] = i; });
    calcCols.forEach(function (c) {
      var cid = c.pa_reportcolumn_id, ct = c.calculationtype, o1 = c.oper_1_id, o2 = c.oper_2_id;
      lines.forEach(function (l) {
        var lid = l.pa_reportline_id, v;
        var a = (o1 != null) ? cellOr0(lid, o1) : BD.ZERO, b = (o2 != null) ? cellOr0(lid, o2) : BD.ZERO;
        if (ct === 'A') v = a.add(b);
        else if (ct === 'S') v = a.subtract(b);
        else if (ct === 'P') v = b.isZero() ? BD.ZERO : a.divide(b, 10, BD.RoundingMode.HALF_UP).multiply(bd('100'));
        else if (ct === 'R') {                                                  // inclusive column-position range
          var i1 = colIdx[o1], i2 = colIdx[o2];
          if (i1 == null || i2 == null) { v = a; }
          else { var lo = Math.min(i1, i2), hi = Math.max(i1, i2); v = BD.ZERO;
            for (var k = lo; k <= hi; k++) v = v.add(cellOr0(lid, cols[k].pa_reportcolumn_id)); }
        }
        else v = a;                                                            // unknown -> first operand
        cellsBD[lid][cid] = v;
      });
    });

    // ── OUTPUT: single money2 leaf (HALF_UP scale-2) — nothing rounded mid-fold ──
    var cells = {};
    lines.forEach(function (l) {
      var row = {}; var src = cellsBD[l.pa_reportline_id] || {};
      cols.forEach(function (c) { var b = src[c.pa_reportcolumn_id]; row[c.pa_reportcolumn_id] = (b != null) ? money2(b) : money2(BD.ZERO); });
      cells[l.pa_reportline_id] = row;
    });
    return {
      cells: cells,
      lineOrder: lines.map(function (l) { return l.pa_reportline_id; }),
      colOrder: cols.map(function (c) { return c.pa_reportcolumn_id; }),
      schema: schema, foldsFrom: 'fact_acct'
    };
  }

  var CORE = { REPORT_MAP: REPORT_MAP, foldReceipt: foldReceipt, foldTrialBalance: foldTrialBalance, foldPnL: foldPnL,
               foldStatement: foldStatement, amtExpr: amtExpr, signedBalance: signedBalance, round2: round2 };
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
        if (id == null) { renderUnsupported(db, key); return; }
        var header = rowsOf(db.exec('SELECT * FROM ' + map.key + ' WHERE ' + map.pk + '=' + id + ' LIMIT 1'))[0] || null;
        if (!header) { renderUnsupported(db, key); return; }
        var lines = [];
        try { lines = rowsOf(db.exec('SELECT * FROM ' + map.lineTable + ' WHERE ' + map.fk + '=' + id + ' ORDER BY line')); } catch (e) {}
        var names = { partner: nameOf(db, 'c_bpartner', 'c_bpartner_id', header.c_bpartner_id, 'name'), products: {} };
        lines.forEach(function (r) { var pid = r[map.fkProduct]; if (pid != null && names.products[pid] === undefined) names.products[pid] = nameOf(db, 'm_product', 'm_product_id', pid, 'name'); });
        var rec = CORE.foldReceipt(map, header, lines, names);
        render(db, rec);
        console.log('§REPORT-RECEIPT doc=' + fname(key) + '#' + rec.id + ' lines=' + rec.lines.length +
          ' subtotal=' + fmtN(rec.subtotal) + ' tax=' + fmtN(rec.tax) + ' total=' + fmtN(rec.total) +
          ' folds-from=' + rec.foldsFrom + ' handAuthored=0');
      } catch (er) { console.warn('§REPORT fold error', er && er.message); }
    });
  }

  // ════════════════════════════════════════════════════════════════════════
  // FINANCIAL STATEMENTS — bring W-PA-REPORT foldStatement into the browser (REPORTING_LANE Step 3).
  // The bundle now carries the PA_* definition + calendar + EV-tree (scripts/extract_pa_report.sh); the host
  // here replicates poc_pa_report.js's input-prep EXACTLY (reading from the bundle, not ad_full.db) and folds
  // via CORE.foldStatement — so what the panel renders == the cent-exact, oracle-diffed statement.
  // ════════════════════════════════════════════════════════════════════════
  var STMT_TREE = 101, STMT_CAL = 102;            // EV account tree + GardenWorld calendar (== the witness)

  function bundleHasStatements(db) { return hasTable(db, 'pa_report') && hasTable(db, 'fact_acct') && hasTable(db, 'ad_treenode'); }
  function hasTable(db, t) {
    try { var r = db.exec("SELECT 1 FROM sqlite_master WHERE type='table' AND name='" + t + "'"); return !!(r.length && r[0].values.length); }
    catch (e) { return false; }
  }
  // EV chart + leaf-expansion over the account tree (== MReportTree.getWhereClause: SUMMARY node -> non-summary leaves).
  function evModel(db) {
    var EV = {}; rowsOf(db.exec('SELECT c_elementvalue_id, value, accounttype, accountsign, issummary FROM c_elementvalue'))
      .forEach(function (r) { EV[r.c_elementvalue_id] = r; });
    var kids = {};
    rowsOf(db.exec('SELECT node_id, parent_id FROM ad_treenode WHERE ad_tree_id=' + STMT_TREE))
      .forEach(function (n) { (kids[n.parent_id] = kids[n.parent_id] || []).push(n.node_id); });
    function leaves(id) {
      var e = EV[id]; if (!e || e.issummary !== 'Y') return [id];
      var out = []; (function rec(x) { (kids[x] || []).forEach(function (c) { if (EV[c] && EV[c].issummary === 'Y') rec(c); else out.push(c); }); })(id);
      return out;
    }
    return { EV: EV, leaves: leaves };
  }
  // calendar period array + the report period. iDempiere's FinReport takes C_Period as a (mandatory) PARAMETER —
  // there is no engine default. We have no date picker yet, so we DERIVE a meaningful default DETERMINISTICALLY
  // from the data: the LATEST posted period of the MOST-ACTIVE fiscal year. "Latest fact period" (the naive
  // choice) is WRONG here — GardenWorld's journal spans 3 fiscal years and its latest period sits in the emptiest
  // one (8 of 300 rows), which would fold every YTD/Period column to zero (a vacuous Income Statement). Picking the
  // busiest year's last posted period makes Y/P/T windows actually cover the journal. No clock, no invention.
  function periodModel(db) {
    var periods = rowsOf(db.exec(
      "SELECT p.c_period_id id, p.name, p.startdate sd, p.enddate ed, p.c_year_id yr FROM c_period p " +
      "JOIN c_year y ON p.c_year_id=y.c_year_id WHERE y.c_calendar_id=" + STMT_CAL +
      " AND p.isactive='Y' AND p.periodtype='S' ORDER BY p.startdate"));
    var factCnt = {}; rowsOf(db.exec('SELECT c_period_id id, COUNT(*) n FROM fact_acct GROUP BY c_period_id'))
      .forEach(function (r) { factCnt[r.id] = r.n; });
    var yearFacts = {};                                                       // facts per fiscal year (over fact-bearing periods)
    periods.forEach(function (p) { if (factCnt[p.id]) yearFacts[p.yr] = (yearFacts[p.yr] || 0) + factCnt[p.id]; });
    var bestYr = null, bestN = -1;
    Object.keys(yearFacts).forEach(function (y) { if (yearFacts[y] > bestN) { bestN = yearFacts[y]; bestYr = Number(y); } });
    var reportIdx = -1;
    periods.forEach(function (p, i) { if (p.yr === bestYr && factCnt[p.id]) reportIdx = i; });  // latest posted period in that year
    if (reportIdx < 0) reportIdx = periods.length - 1;                        // no facts at all -> last calendar period
    return { periods: periods, reportIdx: reportIdx };
  }
  // resolve a column's (paperiodtype, relativeperiod) to a SET of c_period_id (== FinReportPeriod windows).
  function windowFor(pm, paperiodtype, relativeperiod) {
    var idx = pm.reportIdx + (relativeperiod || 0);
    var set = new Set();
    if (idx < 0 || idx >= pm.periods.length) return set;
    var tgt = pm.periods[idx];
    if (paperiodtype === 'P') set.add(tgt.id);
    else if (paperiodtype === 'Y') pm.periods.forEach(function (p) { if (p.yr === tgt.yr && p.sd <= tgt.ed && p.ed <= tgt.ed) set.add(p.id); });
    else if (paperiodtype === 'T') pm.periods.forEach(function (p) { if (p.ed <= tgt.ed) set.add(p.id); });
    else set.add(tgt.id);
    return set;
  }
  // assemble the foldStatement inputs for one pa_report, reading ONLY the bundle (the browser path).
  function statementInputs(db, reportId) {
    var report = rowsOf(db.exec('SELECT pa_report_id, name, pa_reportlineset_id, pa_reportcolumnset_id, c_acctschema_id, c_calendar_id FROM pa_report WHERE pa_report_id=' + (reportId | 0)))[0];
    if (!report) return null;
    var lines = rowsOf(db.exec("SELECT pa_reportline_id, seqno, name, linetype, calculationtype, oper_1_id, oper_2_id, paamounttype, paperiodtype FROM pa_reportline WHERE pa_reportlineset_id=" + report.pa_reportlineset_id + " AND isactive='Y' ORDER BY seqno"));
    var cols = rowsOf(db.exec("SELECT pa_reportcolumn_id, seqno, name, columntype, calculationtype, oper_1_id, oper_2_id, paamounttype, paperiodtype, relativeperiod, postingtype FROM pa_reportcolumn WHERE pa_reportcolumnset_id=" + report.pa_reportcolumnset_id + " AND isactive='Y' ORDER BY seqno"));
    var ev = evModel(db), pm = periodModel(db);
    var sourcesByLine = {};
    lines.forEach(function (l) {
      var srcs = rowsOf(db.exec("SELECT c_elementvalue_id ev FROM pa_reportsource WHERE pa_reportline_id=" + l.pa_reportline_id + " AND isactive='Y'"));
      sourcesByLine[l.pa_reportline_id] = srcs.map(function (s) { return { account_ids: ev.leaves(s.ev) }; });
    });
    var periodWindows = {};
    cols.forEach(function (c) { if (c.columntype !== 'C') periodWindows[c.pa_reportcolumn_id] = windowFor(pm, c.paperiodtype, c.relativeperiod); });
    var factRows = rowsOf(db.exec('SELECT account_id, amtacctdr, amtacctcr, qty, c_period_id, c_acctschema_id, postingtype FROM fact_acct'));
    var accounts = {};
    Object.keys(ev.EV).forEach(function (id) { accounts[id] = { accounttype: ev.EV[id].accounttype, accountsign: ev.EV[id].accountsign }; });
    return { report: report, lines: lines, cols: cols, sourcesByLine: sourcesByLine, factRows: factRows, accounts: accounts, periodWindows: periodWindows };
  }

  // statement — fold + render one pa_report into the panel.
  function statement(reportId) {
    if (typeof withBundle !== 'function') { console.warn('§REPORT no bundle'); return; }
    withBundle(function (db) {
      try {
        if (!bundleHasStatements(db)) { renderUnsupported(db, 'pa_report'); console.log('§STATEMENT bundle lacks pa_report/fact_acct/ad_treenode'); return; }
        var inp = statementInputs(db, reportId);
        if (!inp) { renderUnsupported(db, 'pa_report'); console.log('§STATEMENT report=' + reportId + ' not in bundle'); return; }
        var folded = CORE.foldStatement(inp.report, inp.lines, inp.cols, inp.sourcesByLine, inp.factRows, inp.accounts, inp.periodWindows);
        renderStatement(db, inp, folded);
        var seg = inp.lines.filter(function (l) { return l.linetype === 'S'; }).length;
        console.log('§STATEMENT report=' + inp.report.pa_report_id + ' "' + inp.report.name + '" lines=' + inp.lines.length +
          ' (S=' + seg + ') cols=' + inp.cols.length + ' cells=' + (inp.lines.length * inp.cols.length) +
          ' schema=' + folded.schema + ' foldsFrom=' + folded.foldsFrom + ' — every cell a re-sum of fact_acct, 0 invented');
      } catch (er) { console.warn('§STATEMENT fold error', er && er.message); }
    });
  }

  function fmtN(n) { return n == null ? 'n/a' : Number(n).toFixed(2); }
  function money(n) { return n == null ? '<span class=rpna>n/a</span>' : Number(n).toFixed(2); }

  function render(db, rec) {
    var fin = rec.financial;
    var h = '<span class=rpx title=close>✕</span>' +
      '<div class=rph><span class=rpglyph>▤</span> Receipt — ' + esc(fname(rec.key)) + ' #' + esc(rec.docno == null ? rec.id : rec.docno) + '</div>' +
      pickerHtml(db, null) +
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
    panel.innerHTML = h; panel.className = 'open'; wirePicker();
  }
  // ── statements picker bar (shown whenever the bundle carries pa_report) ──────
  var STMT_LIST = null;                                            // cached [{id,name}] per bundle
  function statementList(db) {
    if (STMT_LIST) return STMT_LIST;
    try { STMT_LIST = bundleHasStatements(db) ? rowsOf(db.exec('SELECT pa_report_id id, name FROM pa_report ORDER BY pa_report_id')) : []; }
    catch (e) { STMT_LIST = []; }
    return STMT_LIST;
  }
  function pickerHtml(db, activeId) {
    var list = statementList(db);
    if (!list.length) return '';
    var tabs = list.map(function (s) {
      return '<button class="rptab' + (s.id == activeId ? ' on' : '') + '" data-stmt="' + s.id + '">' + esc(s.name) + '</button>';
    }).join('');
    return '<div class=rppick><span class=rppicklbl>Financials</span>' + tabs + '</div>';
  }
  function wirePicker() {
    panel.querySelectorAll('.rptab').forEach(function (b) {
      b.addEventListener('click', function () { statement(b.getAttribute('data-stmt') | 0); });
    });
    var x = panel.querySelector('.rpx'); if (x) x.addEventListener('click', close);
  }

  // renderStatement — the (lines × columns) matrix, folded cent-exact from fact_acct.
  function renderStatement(db, inp, folded) {
    var cols = inp.cols, lines = inp.lines;
    var h = '<span class=rpx title=close>✕</span>' +
      '<div class=rph><span class=rpglyph>▤</span> ' + esc(inp.report.name) + '</div>' +
      pickerHtml(db, inp.report.pa_report_id) +
      '<table class=rptbl><thead><tr><th></th>' +
        cols.map(function (c) { return '<th class=rpr>' + esc(c.name) + '</th>'; }).join('') + '</tr></thead><tbody>';
    lines.forEach(function (l) {
      var calc = l.linetype === 'C';
      h += '<tr class="' + (calc ? 'rpcalc' : '') + '"><td>' + esc(l.name) + '</td>' +
        cols.map(function (c) {
          var v = (folded.cells[l.pa_reportline_id] || {})[c.pa_reportcolumn_id];
          return '<td class=rpr>' + (v == null ? '<span class=rpna>·</span>' : esc(Number(v).toFixed(2))) + '</td>';
        }).join('') + '</tr>';
    });
    h += '</tbody></table>' +
      '<div class=rpfoot>folded from the journal via PA_Report metadata — every cell is a re-sum of fact_acct (W-PA-REPORT, maxDiff=0c vs iDempiere), no value is hand-authored</div>';
    panel.innerHTML = h; panel.className = 'open'; wirePicker();
  }

  function renderUnsupported(db, key) {
    panel.innerHTML = '<span class=rpx title=close>✕</span><div class=rph><span class=rpglyph>▤</span> Receipt — ' + esc(fname(key)) + '</div>' +
      pickerHtml(db, null) +
      '<div class=rpfoot rpna>No header/line receipt for this document kind in the bundle. (Report folds documents that have a line table — orders, invoices, shipments.)</div>';
    panel.className = 'open'; wirePicker();
  }
  function close() { panel.className = ''; panel.innerHTML = ''; }

  // ── react to the ring's key-addressed Report intent (no import of crud_overlay — the bus is the seam) ──
  global.addEventListener('overlay:report', function (ev) { var d = ev && ev.detail; if (d && d.key) show(d.key); });
  // ── react to a financial-statement intent (a menu/launcher can dispatch this with a pa_report id) ──
  global.addEventListener('overlay:statement', function (ev) { var d = ev && ev.detail; if (d && d.reportId != null) statement(d.reportId | 0); });

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
      '#reportPanel .rpfoot{margin-top:11px;font-size:11px;color:#8a6f86;font-style:italic;line-height:1.4}' +
      '#reportPanel .rppick{display:flex;flex-wrap:wrap;align-items:center;gap:5px;margin:0 0 11px;padding-bottom:9px;border-bottom:1px solid #2a1f29}' +
      '#reportPanel .rppicklbl{font-size:10.5px;letter-spacing:.06em;text-transform:uppercase;color:#8a6f86;margin-right:2px}' +
      '#reportPanel .rptab{font:11.5px/1 system-ui;color:#c4a8c0;background:#1c1422;border:1px solid #3a2b38;border-radius:7px;padding:4px 8px;cursor:pointer}' +
      '#reportPanel .rptab:hover{border-color:#6a4a62;color:#fbeaf7}' +
      '#reportPanel .rptab.on{background:#3a2440;border-color:#9fdfe8;color:#bff0dd}' +
      '#reportPanel .rpcalc td{font-weight:600;color:#fbeaf7;border-top:1px solid #2a1f29}';
    document.head.appendChild(css);
  }

  global.__report = { show: show, statement: statement, core: CORE, panel: function () { return panel; }, close: close };
  console.log('§REPORT layer mounted (read face — ▤ Report)');
})(typeof window !== 'undefined' ? window : this);
