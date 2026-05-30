#!/usr/bin/env node
/**
 * system_explorer.js — GLASSBOWL generator (docs/GLASSBOWL.md, witness W-GLASSBOWL).
 *   The engine as DATA: it renders ITSELF. Every node/edge/annotation is EXTRACTED from
 *   ad_full.db + erp_rules.db + kernel_ops — ZERO hand-authored structure (that is the claim).
 *   Spec: docs/ERP.md §0.14 (everything is a predicate on an edge), §0.7 (self-graphing),
 *   §0.13 (the two spines), §14 (op-log gravity). Read-only MVP; touches nothing in bim-ootb/live.
 *
 *   Layer 1 = FK graph (classified by spine).  Layer 2 = written cells (verbs/guards/policy/
 *   matcher, authoritative from a diff_oracle run).  Layer 3 = gravity hot/cold + the 155-cell
 *   handler_backlog as dim ghosts.
 *
 * Emits: build/erp/system_graph.json (the data) + build/erp/glassbowl.html (self-contained viewer,
 *   graph inlined, opens via file://). §-logs every count as the W-GLASSBOWL witness.
 *
 * Run: node scripts/system_explorer.js 2>&1 | tee build/erp/system_explorer.log
 */
'use strict';
var path = require('path');
var fs = require('fs');
var Database = require('better-sqlite3');
var initSqlJs = require('sql.js');
var DO = require('./diff_oracle');

var AD = process.env.ERP_AD_FULL || path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var RULES = process.env.ERP_RULES_OUT || path.join(__dirname, '..', 'build', 'erp', 'erp_rules.db');
var OUT_DIR = path.join(__dirname, '..', 'build', 'erp');
var OUT_JSON = path.join(OUT_DIR, 'system_graph.json');
var OUT_HTML = path.join(OUT_DIR, 'glassbowl.html');
var OUT_BUNDLE = path.join(OUT_DIR, 'glassbowl_data.db');                       // the sql.js data bundle (§2a)
var LIB_SRC = path.join(__dirname, '..', 'deploy', 'dev', 'lib');              // sql-wasm.js + sql-wasm.wasm source
var LIB_OUT = path.join(OUT_DIR, 'sqljs');                                     // served dir — NOT 'lib/' (.gitignore'd as Maven deps)

// the lifecycle document world — the SUBSET of tables the data bundle carries (real rows for the
// trace + the later dossier). Tiny (≈180 rows); whole tables copied, so the bundle is self-sufficient.
var BUNDLE_TABLES = ['c_order', 'c_orderline', 'm_inout', 'm_inoutline', 'c_invoice', 'c_invoiceline', 'c_payment', 'c_allocationhdr', 'c_allocationline', 'c_bpartner', 'm_product'];
var LINEAGE_SEED = 101;   // sales order 101 — the §0.19 chain (W-LIFECYCLE acceptance record)

// §14 op-type weights — activity, not row count, is gravity (same as gravity_seed.js).
var WEIGHT = { SET_STATUS: 2.0, CREATE_DOCUMENT: 1.0, CREATE_LINE: 1.0, MATCH: 1.5, ALLOCATE: 1.5 };

// the CORE document world (stated DATA, not magic): the tables the engine operates on. Scoping to
// these keeps the bowl about the ENGINE, not a 925-table schema dump (docs/GLASSBOWL.md out-of-scope).
var CORE = ['c_order', 'c_orderline', 'm_inout', 'm_inoutline', 'c_invoice', 'c_invoiceline', 'c_payment', 'c_allocationhdr', 'c_allocationline', 'm_matchpo', 'm_matchinv', 'gl_journal', 'gl_journalline'];
var SETTLEMENT = ['m_matchpo', 'm_matchinv', 'c_allocationhdr', 'c_allocationline'];
var LIFECYCLE = ['c_order', 'c_orderline', 'm_inout', 'm_inoutline', 'c_invoice', 'c_invoiceline', 'c_payment'];

(async function () {
  console.log('═══ GLASSBOWL — the engine renders itself from data (W-GLASSBOWL) ═══\n');
  var ad = new Database(AD, { readonly: true });
  var er = new Database(RULES, { readonly: true });
  var SQL = await initSqlJs();
  var hand = 0;   // counter: anything hand-authored. MUST stay 0 (the claim).

  // ── name→ad_table_id, and the FK-target resolver (ad_ref_table else strip _ID) ──
  var tables = {};
  ad.prepare("SELECT ad_table_id, lower(tablename) tn FROM ad_table").all().forEach(function (r) { tables[r.tn] = r.ad_table_id; tables['#' + r.ad_table_id] = r.tn; });
  var refTable = {};
  ad.prepare("SELECT ad_reference_id, ad_table_id FROM ad_ref_table").all().forEach(function (r) { refTable[r.ad_reference_id] = r.ad_table_id; });
  function resolveTarget(col, refValId) {
    if (refValId != null && refTable[refValId] != null) return tables['#' + refTable[refValId]] || null;   // ad_ref_table path
    var m = /^(.*)_id$/i.exec(col); if (!m) return null;
    var t = m[1].toLowerCase(); return tables[t] != null ? t : null;                                       // strip _ID path
  }

  // ── Layer 1: FK edges out of the core doc world, classified by spine (the §0.13 rule) ──
  console.log('── Layer 1: FK graph (extracted from ad_column/ad_ref_table) ──');
  var edges = [], nodeSet = {};
  CORE.forEach(function (tn) { nodeSet[tn] = { id: tn, kind: 'doc', settlement: SETTLEMENT.indexOf(tn) >= 0 }; });
  CORE.forEach(function (tn) {
    var tid = tables[tn]; if (tid == null) return;
    ad.prepare("SELECT columnname, ad_reference_id, ad_reference_value_id, isparent FROM ad_column WHERE ad_table_id=? AND ad_reference_id IN (18,19,30)").all(tid).forEach(function (c) {
      var target = resolveTarget(c.columnname, c.ad_reference_value_id);
      if (!target || target === tn) return;
      var kind;
      if (c.isparent === 'Y') kind = 'containment';
      else if (SETTLEMENT.indexOf(tn) >= 0 || SETTLEMENT.indexOf(target) >= 0) kind = 'settlement';
      else if (LIFECYCLE.indexOf(tn) >= 0 && LIFECYCLE.indexOf(target) >= 0) kind = 'derivation';
      else kind = 'reference';
      edges.push({ from: tn, to: target, via: c.columnname, kind: kind });
      if (!nodeSet[target]) nodeSet[target] = { id: target, kind: CORE.indexOf(target) >= 0 ? 'doc' : 'master', settlement: SETTLEMENT.indexOf(target) >= 0 };
    });
  });
  var spineCounts = { containment: 0, derivation: 0, settlement: 0, reference: 0 };
  edges.forEach(function (e) { spineCounts[e.kind]++; });
  console.log('   §GLASS fk-edges=' + edges.length + ' (hand-authored=' + hand + ') spines containment=' + spineCounts.containment + ' derivation=' + spineCounts.derivation + ' settlement=' + spineCounts.settlement + ' reference=' + spineCounts.reference);

  // ── Layer 2: written cells (authoritative engine facts from a diff_oracle run) ──
  console.log('\n── Layer 2: written cells (verbs/matcher from diff_oracle) + guard/policy from erp_rules ──');
  var collected = [];
  var r = await DO.run({ shareDb: true, collect: collected });
  // guard count + policy flags per cell, from erp_rules (data).
  function guardCount(baseTable) { try { return er.prepare("SELECT COUNT(*) n FROM rules WHERE event_type='Validation' AND binding LIKE ?").get(baseTable + '.%').n; } catch (e) { return 0; } }
  var docTypeIdByTable = {};   // for a quick DOCPOLICY peek (sales/purchase doctypes present)
  collected.forEach(function (c) {
    var base = c.docType.toLowerCase();
    c.baseTable = base; c.guards = guardCount(base);
    var node = nodeSet[base]; if (node) { node.cells = node.cells || []; node.cells.push(c); }
  });
  collected.forEach(function (c) { console.log('   §GLASS cell=' + c.cell + ' verbs=[' + c.verbs.join(',') + '] matcher=' + (c.matcher ? 'Y' : 'N') + ' guards=' + c.guards + ' ops=' + c.ops + (c.dataless ? ' (dataless)' : '')); });

  // ── Layer 3a: gravity (kernel_ops use, §14-weighted) joined to the cells ──
  console.log('\n── Layer 3: gravity (kernel_ops) + cold backlog (handler_backlog) ──');
  var grav = {};
  if (r.db) {
    r.query("SELECT user_tag cell, op_type, COUNT(*) hits FROM kernel_ops WHERE user_tag LIKE '(%' GROUP BY user_tag, op_type").forEach(function (row) {
      var w = (WEIGHT[row.op_type] != null ? WEIGHT[row.op_type] : 0.2) * row.hits;
      var g = grav[row.cell] || (grav[row.cell] = { weight: 0, hits: 0 }); g.weight += w; g.hits += row.hits;
    });
  }
  collected.forEach(function (c) { c.gravity = grav[c.cell] ? grav[c.cell].weight : 0; c.gravHits = grav[c.cell] ? grav[c.cell].hits : 0; });
  // roll cell gravity up to its table node (for bubble sizing).
  Object.keys(nodeSet).forEach(function (k) { var n = nodeSet[k]; n.gravity = (n.cells || []).reduce(function (s, c) { return s + c.gravity; }, 0); });
  var topCell = collected.slice().filter(function (c) { return c.gravity > 0; }).sort(function (a, b) { return b.gravity - a.gravity; })[0];
  console.log('   §GLASS gravity-top=' + (topCell ? topCell.cell + ' weight=' + topCell.gravity.toFixed(1) : 'none'));

  // ── Layer 3b: the cold long tail — 155 unwritten cells (the gravity backlog, dim ghosts) ──
  var cold = er.prepare("SELECT cell, kind, oracle_class, oracle_method, gravity_rank, binding_count, notes FROM handler_backlog WHERE status='unwritten' ORDER BY gravity_rank DESC, cell").all();
  var coldByKind = {}; cold.forEach(function (c) { coldByKind[c.kind] = (coldByKind[c.kind] || 0) + 1; });
  console.log('   §GLASS cold-backlog=' + cold.length + ' by-kind=' + JSON.stringify(coldByKind));

  // ── Layer 4: LINEAGE — a real record's whole life, FK-walked from data ────────
  //   Implementing docs/GLASSBOWL_DOSSIER.md §2a — Witness: W-LIFECYCLE. The chain is the §0.19
  //   derivation/settlement walk; every hop follows a FK column that MUST exist in the extracted
  //   graph (asserted → 0 hand-authored hops) and every row is read from ad_full (never invented).
  console.log('\n── Layer 4: lineage chain (FK walk for sales order ' + LINEAGE_SEED + ') ──');
  var STEPS = lineageSteps(LINEAGE_SEED);
  var validHops = STEPS.filter(function (s) { return s.via; }).filter(function (s) { return edgeExists(edges, s.table, s.parent, s.via); }).length;
  var totalHops = STEPS.filter(function (s) { return s.via; }).length;
  if (validHops !== totalHops) hand += (totalHops - validHops);   // a hop whose FK is NOT in the graph would be hand-authored
  var adWalk = runWalk(STEPS, function (sql) { var r = ad.prepare(sql).get(); return r || null; });
  var chainStr = adWalk.chain.map(function (c) { return cap(c.table) + '#' + c.id + (c.documentno != null ? '(' + c.documentno + ')' : '') + (c.amount != null ? '[$' + c.amount + ']' : ''); }).join(' → ');
  console.log('   §LIFECYCLE record=C_Order#' + LINEAGE_SEED + ' hops=' + adWalk.chain.length + ' chain=[' + chainStr + '] missing=' + adWalk.missing + ' fk-hops-validated=' + validHops + '/' + totalHops);
  // declarative walk (parameterizable by ANY seed order) — the browser re-runs these against the
  // bundle to trace a SEARCHED record. Mirrors lineageSteps; dep='seed' uses the chosen order id.
  var STEP_DECL = [
    { table: 'c_order',          friendly: 'Order',      dep: 'seed',      base: 'SELECT c_order_id AS id, documentno, grandtotal AS amount FROM c_order WHERE c_order_id=', order: '' },
    { table: 'm_inout',          friendly: 'Shipment',   dep: 'c_order',   base: 'SELECT m_inout_id AS id, documentno, NULL AS amount FROM m_inout WHERE c_order_id=', order: ' ORDER BY m_inout_id LIMIT 1' },
    { table: 'c_invoice',        friendly: 'Invoice',    dep: 'c_order',   base: 'SELECT c_invoice_id AS id, documentno, grandtotal AS amount FROM c_invoice WHERE c_order_id=', order: ' ORDER BY c_invoice_id LIMIT 1' },
    { table: 'c_payment',        friendly: 'Payment',    dep: 'c_invoice', base: 'SELECT c_payment_id AS id, documentno, payamt AS amount FROM c_payment WHERE c_invoice_id=', order: ' ORDER BY c_payment_id LIMIT 1' },
    { table: 'c_allocationline', friendly: 'Allocation', dep: 'c_payment', base: 'SELECT c_allocationline_id AS id, NULL AS documentno, amount FROM c_allocationline WHERE c_payment_id=', order: ' ORDER BY c_allocationline_id LIMIT 1' }
  ];
  var lineage = {
    seed: LINEAGE_SEED, record: 'C_Order#' + LINEAGE_SEED, hops: adWalk.chain.length, missing: adWalk.missing,
    chain: adWalk.chain, walk: adWalk.walkSQL,                  // walk = resolved per-table SQL (re-run in the browser against the bundle)
    steps: STEP_DECL,                                           // declarative steps for in-browser search/trace of any order
    litNodes: adWalk.chain.map(function (c) { return c.table; })
  };

  // ── the sql.js DATA BUNDLE (§2a "enabling step"): a small subset the viewer loads in-browser ──
  console.log('\n── data bundle: sql.js subset (' + BUNDLE_TABLES.length + ' lifecycle tables) ──');
  var bundle = buildBundle(ad, SQL);
  fs.writeFileSync(OUT_BUNDLE, Buffer.from(bundle.export()));
  // PROOF: re-walk the BUNDLE ALONE (via sql.js) and require the identical chain — the bundle is
  //        sufficient & correct, independent of the browser (the deterministic data-bundle witness).
  var bundleWalk = runWalk(STEPS, sqljsFirst(bundle));
  var bundleStr = bundleWalk.chain.map(function (c) { return cap(c.table) + '#' + c.id; }).join(' → ');
  var bundleAgree = adWalk.chain.every(function (c, i) { return String(c.id) === String(bundleWalk.chain[i].id); }) && bundleWalk.missing === 0;
  if (!bundleAgree) hand++;   // a bundle that can't reproduce the chain is a FINDING, not a pass
  var bundleBytes = fs.statSync(OUT_BUNDLE).size;
  console.log('   §LIFECYCLE-BUNDLE file=' + path.relative(path.join(__dirname, '..'), OUT_BUNDLE) + ' bytes=' + bundleBytes + ' rows=' + bundle.rowTotal + ' chain=[' + bundleStr + '] agree=' + (bundleAgree ? 'Y' : 'N'));
  // §2bcd Task 1 W-DATA-BARS: which tables can surface REAL rows in their accordion bar (= the bundle).
  console.log('   §DATA-BARS bundle-tables=' + BUNDLE_TABLES.length + ' tables=[' + BUNDLE_TABLES.join(',') + ']');
  bundle.close();
  // copy the sql.js runtime next to the bundle so the viewer can load it (Pages + headless test).
  copyLibAssets();

  // ── assemble + emit ──────────────────────────────────────────────────────────
  var nodes = Object.keys(nodeSet).map(function (k) { return nodeSet[k]; });

  // ── Layer 5: ORBIT depth — the 3 spines lifted onto depth planes (GLASSBOWL_DOSSIER §2-viz, W-ORBIT) ──
  //   "like BIM disciplines, orbited." z is DERIVED from each node's existing classification (extract,
  //   not invent): spine docs z=0, settlement floats +D, reference shell behind −D. 0 hand-placed.
  console.log('\n── Layer 5: orbit depth (3 spine-planes, deterministic from classification) ──');
  var ZD = 220, planes = { spine: 0, settlement: 0, reference: 0 }, si = 0, ri = 0;
  // z-JITTER gives each plane THICKNESS (a shell, not a flat sheet) so orbiting separates the many
  // reference bubbles in depth instead of bunching them edge-on. Deterministic (index-based); and
  // invisible at rest (head-on projection ignores z) — the flat layout is unchanged.
  nodes.forEach(function (n) {
    if (n.settlement) { n.z = ZD + ((si++ % 5) - 2) * 26; planes.settlement++; }
    else if (n.kind === 'master') { n.z = -ZD + ((ri++ % 9) - 4) * 34; planes.reference++; }
    else { n.z = 0; planes.spine++; }
  });
  var orbitOk = planes.spine > 0 && planes.settlement > 0 && planes.reference > 0;
  console.log('   §ORBIT planes=3 spine=' + planes.spine + ' settlement=' + planes.settlement + ' reference=' + planes.reference + ' depth=' + ZD + ' (hand-placed=0)');

  // ── Layer 6: DOSSIER metadata (GLASSBOWL_DOSSIER.md §2bcd Task 3, Witness: W-DOSSIER) ──
  //   Per node, EXTRACT (never invent): its ad_column list (type/mandatory/FK target), its validation
  //   rule count + a small sample of rule bodies from erp_rules. Surfaced read-only in the right-click
  //   dossier (Columns / Rules tabs). The Data tab loads real rows from the bundle in-browser; History
  //   reads graph.oplog (below). 0 hand-authored — counts trace to ad_full / erp_rules.
  console.log('\n── Layer 6: dossier metadata (ad_column + erp_rules validation) per node ──');
  // friendly type label for the common ad_reference_id values (else show the raw id) — a display aid only.
  var REFTYPE = { 10: 'String', 11: 'Integer', 12: 'Amount', 13: 'ID', 14: 'Text', 15: 'Date', 16: 'DateTime', 17: 'List', 18: 'Table', 19: 'TableDir', 20: 'Yes/No', 21: 'Location', 22: 'Number', 23: 'Binary', 24: 'Time', 25: 'Account', 28: 'Button', 29: 'Quantity', 30: 'Search', 37: 'Costs+Prices' };
  function dossierFor(tn) {
    var tid = tables[tn]; if (tid == null) return null;
    var cols = [];
    try {
      ad.prepare("SELECT columnname, ad_reference_id, ismandatory, ad_reference_value_id FROM ad_column WHERE ad_table_id=? ORDER BY columnname LIMIT 30").all(tid).forEach(function (c) {
        cols.push({ name: c.columnname, type: REFTYPE[c.ad_reference_id] || ('ref#' + c.ad_reference_id), mandatory: c.ismandatory === 'Y', fk: resolveTarget(c.columnname, c.ad_reference_value_id) });
      });
    } catch (e) { /* table without ad_column rows — honest empty list */ }
    var rulesRows = [];
    try {
      er.prepare("SELECT binding, body FROM rules WHERE event_type='Validation' AND binding LIKE ?").all(tn + '.%').forEach(function (rr) {
        rulesRows.push({ binding: rr.binding, body: (rr.body || '').slice(0, 120) });
      });
    } catch (e) { /* no rules recorded — honest 0 */ }
    return { columns: cols, ruleCount: guardCount(tn), rules: rulesRows };
  }
  // attach dossier to every CORE/master node (the bubbles a user right-clicks).
  nodes.forEach(function (n) { n.dossier = dossierFor(n.id); });
  var invDoss = nodeSet['c_invoice'] ? nodeSet['c_invoice'].dossier : null;
  console.log('   §DOSSIER table=c_invoice rules=' + (invDoss ? invDoss.ruleCount : 0) + ' columns=' + (invDoss ? invDoss.columns.length : 0));

  // ── graph.oplog: a small REAL sample of the kernel_ops op-log (History tab). EXTRACT from the
  //   diff_oracle runtime (it HAS kernel_ops, see Layer 3a). Read the real columns, take the last ~12. ──
  var oplog = [];
  if (r.db) {
    var opCols = r.query("PRAGMA table_info(kernel_ops)").map(function (c) { return c.name; });   // introspect real columns
    r.query("SELECT id, op_type, user_tag, output_guid FROM kernel_ops ORDER BY id DESC LIMIT 12").forEach(function (row) {
      oplog.push({ id: row.id, op: row.op_type, cell: row.user_tag, out: row.output_guid });
    });
    console.log('   §OPLOG ops=' + oplog.length + ' cols=[' + opCols.join(',') + '] sample=' + (oplog.length ? JSON.stringify(oplog[0]) : 'NONE'));
  } else {
    console.log('   §OPLOG ops=0 sample=NONE (no runtime db)');
  }
  if (oplog.length === 0) hand++;   // an empty op-log is a FINDING (kernel_ops should be populated), not a pass

  var graph = {
    meta: { generated: 'system_explorer.js', witness: 'W-GLASSBOWL', handAuthored: hand, core: CORE, settlement: SETTLEMENT },
    nodes: nodes, edges: edges, spines: spineCounts,
    cells: collected, cold: cold, coldByKind: coldByKind,
    gravityTop: topCell ? { cell: topCell.cell, weight: topCell.gravity } : null,
    lineage: lineage,                                           // §2a W-LIFECYCLE — the traced record's chain (inlined, file://-safe)
    orbit: { depth: ZD, planes: planes },                       // §2-viz W-ORBIT — depth-plane metadata (z is inlined per node)
    // Implementing GLASSBOWL_DOSSIER.md §2bcd Task 1 — Witness: W-DATA-BARS. The whitelist of tables
    // whose REAL rows the viewer can surface in a bar (= the bundle subset). Any other table is honestly
    // "records not carried in this dataset" — no fake rows (extract-only). 0 hand-authored.
    bundleTables: BUNDLE_TABLES,
    // Implementing GLASSBOWL_DOSSIER.md §2bcd Task 3 — Witness: W-DOSSIER. The real kernel_ops op-log
    // sample (History tab change-log). Per-node dossier (columns/rules) is inlined as node.dossier above.
    oplog: oplog
  };
  fs.writeFileSync(OUT_JSON, JSON.stringify(graph, null, 1));
  var htmlOut = renderHtml(graph);
  fs.writeFileSync(OUT_HTML, htmlOut);
  console.log('\n   §GLASS emit ' + path.relative(path.join(__dirname, '..'), OUT_JSON) + ' (nodes=' + nodes.length + ' edges=' + edges.length + ') + ' + path.relative(path.join(__dirname, '..'), OUT_HTML));
  // §2bcd Tasks 4/5/6 — confirm the ADDITIVE viewer affordances are wired into the emitted HTML (pure-read
  //   overlays; no new graph data, so the FK-graph counts are untouched). Witnesses W-SWIPE-PICKER/W-AUDIO/W-QR-INPUT.
  var swipeOk = /id="recpick"/.test(htmlOut) && /buildRecPick/.test(htmlOut);
  var audioOk = /AudioContext/.test(htmlOut) && /id="muteBtn"/.test(htmlOut) && /function beep/.test(htmlOut);
  var qrOk = /BarcodeDetector/.test(htmlOut) && /id="qrbtn"/.test(htmlOut) && /function qrLookup/.test(htmlOut);
  console.log('   §SWIPE-PICKER wired=' + (swipeOk ? 'Y' : 'N') + ' (swipe record list at the trackball, augments the typed search)');
  console.log('   §AUDIO wired=' + (audioOk ? 'Y' : 'N') + ' (WebAudio synth + persisted mute toggle, 0 audio assets)');
  console.log('   §QR-INPUT wired=' + (qrOk ? 'Y' : 'N') + ' (BarcodeDetector scan->trace, honest unsupported fallback, read-only slice)');
  // mobile affordances: the yellow border #phandle (tap to slide the panel), the About close (✕), the mobile media query.
  var mobileOk = /id="phandle"/.test(htmlOut) && /function togglePanel/.test(htmlOut) && /class="aboutx"/.test(htmlOut) && /max-width:760px/.test(htmlOut) && /function isMobile/.test(htmlOut);
  console.log('   §MOBILE wired=' + (mobileOk ? 'Y' : 'N') + ' (yellow panel handle + About ✕ close + viewport-bounded About + mobile auto-collapse)');
  if (!(swipeOk && audioOk && qrOk && mobileOk)) hand++;   // a missing affordance is a FINDING (the viewer didn’t emit it), not a pass

  ad.close(); er.close();
  console.log('\n═══ VERDICT ═══');
  var lineageOk = adWalk.missing === 0 && adWalk.chain.length === 5 && bundleAgree && validHops === totalHops;
  var ok = hand === 0 && edges.length > 0 && nodes.length > 0 && collected.length > 0 && cold.length > 0 && r.fails === 0 && lineageOk && orbitOk;
  console.log('§GLASSBOWL ' + (ok ? 'PASS — engine rendered from DATA ALONE (0 hand-authored nodes/edges); FK graph + ' + collected.length + ' cells + ' + cold.length + ' cold backlog + lineage chain (5 hops, bundle reproduces)' : 'FAIL — hand=' + hand + ' edges=' + edges.length + ' cells=' + collected.length + ' diffFails=' + r.fails + ' lineageOk=' + lineageOk));
  console.log('§LIFECYCLE ' + (lineageOk ? 'PASS — W-LIFECYCLE: order ' + LINEAGE_SEED + ' chain reconstructed from data (' + adWalk.chain.length + ' hops, 0 hand-authored, bundle agrees)' : 'FAIL — missing=' + adWalk.missing + ' hops=' + adWalk.chain.length + ' bundleAgree=' + bundleAgree + ' fkHops=' + validHops + '/' + totalHops));
  console.log('§ORBIT ' + (orbitOk ? 'PASS — W-ORBIT: 3 depth planes from data (spine=' + planes.spine + ' settlement=' + planes.settlement + ' reference=' + planes.reference + ', 0 hand-placed)' : 'FAIL — planes spine=' + planes.spine + ' settlement=' + planes.settlement + ' reference=' + planes.reference));
  process.exit(ok ? 0 : 1);
})();

// ════════════════════════════════════════════════════════════════════════════════
//  LINEAGE (§2a) — the derivation/settlement FK walk, expressed as DATA, not a hop stack
// ════════════════════════════════════════════════════════════════════════════════
function cap(t) { return t.split('_').map(function (s) { return s.charAt(0).toUpperCase() + s.slice(1); }).join('_'); }
function edgeExists(edges, from, to, via) { return edges.some(function (e) { return e.from === from && e.to === to && e.via.toLowerCase() === via.toLowerCase(); }); }

// the §0.19 chain as an ordered, data-validated walk. Each step (a) names a real FK column so the
// hop is the EXTRACTED spine (asserted via edgeExists), and (b) carries the SQL that fetches its
// row keyed off a prior result — so the SAME definition drives the Node walk AND the bundle re-walk.
function lineageSteps(seed) {
  return [
    { table: 'c_order',          friendly: 'Order',      parent: null,        via: null,
      sql: function () { return 'SELECT c_order_id AS id, documentno, grandtotal AS amount FROM c_order WHERE c_order_id=' + seed; } },
    { table: 'm_inout',          friendly: 'Shipment',   parent: 'c_order',   via: 'c_order_id',
      sql: function (g) { return g.c_order ? 'SELECT m_inout_id AS id, documentno, NULL AS amount FROM m_inout WHERE c_order_id=' + g.c_order.id + ' ORDER BY m_inout_id LIMIT 1' : null; } },
    { table: 'c_invoice',        friendly: 'Invoice',    parent: 'c_order',   via: 'c_order_id',
      sql: function (g) { return g.c_order ? 'SELECT c_invoice_id AS id, documentno, grandtotal AS amount FROM c_invoice WHERE c_order_id=' + g.c_order.id + ' ORDER BY c_invoice_id LIMIT 1' : null; } },
    { table: 'c_payment',        friendly: 'Payment',    parent: 'c_invoice', via: 'c_invoice_id',
      sql: function (g) { return g.c_invoice ? 'SELECT c_payment_id AS id, documentno, payamt AS amount FROM c_payment WHERE c_invoice_id=' + g.c_invoice.id + ' ORDER BY c_payment_id LIMIT 1' : null; } },
    { table: 'c_allocationline', friendly: 'Allocation', parent: 'c_payment', via: 'c_payment_id',
      sql: function (g) { return g.c_payment ? 'SELECT c_allocationline_id AS id, NULL AS documentno, amount FROM c_allocationline WHERE c_payment_id=' + g.c_payment.id + ' ORDER BY c_allocationline_id LIMIT 1' : null; } }
  ];
}

// run the walk against ANY store: first(sql)->rowObj|null. Returns the chain + the resolved SQL map.
function runWalk(steps, first) {
  var got = {}, chain = [], walkSQL = {}, missing = 0;
  steps.forEach(function (s) {
    var sql = s.sql(got);
    walkSQL[s.table] = sql;
    var row = sql ? first(sql) : null;
    if (!row) { missing++; chain.push({ table: s.table, friendly: s.friendly, id: null, documentno: null, amount: null }); }
    else { got[s.table] = row; chain.push({ table: s.table, friendly: s.friendly, id: row.id, documentno: row.documentno == null ? null : row.documentno, amount: row.amount == null ? null : row.amount }); }
  });
  return { chain: chain, missing: missing, walkSQL: walkSQL };
}

// a first()-er over a sql.js Database (used to re-walk the bundle for the §LIFECYCLE-BUNDLE proof).
function sqljsFirst(db) {
  return function (sql) {
    var res = db.exec(sql);
    if (!res.length || !res[0].values.length) return null;
    var cols = res[0].columns, v = res[0].values[0], o = {};
    cols.forEach(function (c, i) { o[c] = v[i]; });
    return o;
  };
}

// build the data bundle: copy the lifecycle tables whole (tiny) into a fresh sql.js db. Generic —
// schema read from PRAGMA, every row copied verbatim (BLOBs → Uint8Array). EXTRACT, never invent.
function buildBundle(ad, SQL) {
  var bdb = new SQL.Database();
  var rowTotal = 0;
  BUNDLE_TABLES.forEach(function (t) {
    var info = ad.prepare('PRAGMA table_info(' + t + ')').all();
    if (!info.length) return;
    var cols = info.map(function (c) { return c.name; });
    bdb.run('CREATE TABLE ' + t + ' (' + cols.map(function (c) { return '"' + c + '"'; }).join(',') + ')');
    var rows = ad.prepare('SELECT ' + cols.map(function (c) { return '"' + c + '"'; }).join(',') + ' FROM ' + t).all();
    var stmt = bdb.prepare('INSERT INTO ' + t + ' VALUES (' + cols.map(function () { return '?'; }).join(',') + ')');
    rows.forEach(function (r) {
      stmt.run(cols.map(function (c) { var v = r[c]; if (v === undefined) return null; if (Buffer.isBuffer(v)) return new Uint8Array(v); return v; }));
    });
    stmt.free();
    rowTotal += rows.length;
  });
  bdb.rowTotal = rowTotal;
  return bdb;
}

// copy the sql.js runtime (glue + wasm) next to the bundle so the browser can lazy-load it.
function copyLibAssets() {
  try {
    if (!fs.existsSync(LIB_OUT)) fs.mkdirSync(LIB_OUT, { recursive: true });
    ['sql-wasm.js', 'sql-wasm.wasm'].forEach(function (f) {
      var src = path.join(LIB_SRC, f);
      if (fs.existsSync(src)) fs.copyFileSync(src, path.join(LIB_OUT, f));
      else console.log('   §GLASS WARN sql.js asset missing: ' + src);
    });
  } catch (e) { console.log('   §GLASS WARN copyLibAssets: ' + e.message); }
}

// ── the self-contained viewer (graph inlined; deterministic force layout; no deps, file://-safe) ──
function renderHtml(graph) {
  var data = JSON.stringify(graph);
  return '<!doctype html><html><head><meta charset="utf-8"><title>Glassbowl — your business, mapped</title>\n' +
'<meta name="viewport" content="width=device-width,initial-scale=1">\n' +
'<style>\n' +
'  :root{--bg:#0c0f14;--panel:#141a22;--ink:#e7edf3;--dim:#5a6b7a;--line:#222c38;}\n' +
'  *{box-sizing:border-box;font-family:-apple-system,Segoe UI,Roboto,sans-serif}\n' +
'  body{margin:0;background:var(--bg);color:var(--ink);overflow:hidden}\n' +
'  #wrap{display:flex;height:100vh}\n' +
'  #stage{flex:1;position:relative;overflow:hidden}\n' +
'  #svg{touch-action:none;cursor:grab} #svg.panning{cursor:grabbing}\n' +
'  #panel{width:var(--pw,340px);background:var(--panel);border-left:1px solid var(--line);padding:16px;overflow:auto;transition:width .18s ease,padding .18s ease}\n' +
'  #wrap.collapsed #panel{width:0;padding:0;border-left:none;overflow:hidden}\n' +
'  #wrap.resizing #panel{transition:none}\n' +
'  #pgrip{flex:0 0 6px;cursor:ew-resize;background:transparent} #pgrip:hover,#wrap.resizing #pgrip{background:#2b3744}\n' +
'  #wrap.collapsed #pgrip{display:none}\n' +
'  /* mobile-friendly panel handle: a small yellow indicator midway at the panel border \\u2014 tap to slide open/closed.\n' +
'     Lives at the stage right edge, so it tracks the border whether the panel is open or collapsed. */\n' +
'  #phandle{position:absolute;top:50%;right:0;transform:translateY(-50%);width:17px;height:58px;background:#ffd479;border-radius:9px 0 0 9px;cursor:pointer;z-index:16;display:flex;align-items:center;justify-content:center;color:#0c0f14;font-size:12px;font-weight:700;box-shadow:-2px 0 9px rgba(0,0,0,.45);touch-action:manipulation;user-select:none}\n' +
'  #phandle:hover{background:#ffe1a3}\n' +
'  h1{font-size:15px;margin:0 0 2px} .sub{color:var(--dim);font-size:11px;margin-bottom:14px}\n' +
'  .appx{float:right;font-size:14px;color:var(--dim);cursor:pointer;font-weight:400;line-height:1} .appx:hover{color:#8aa}\n' +
'  .legend{position:absolute;top:12px;left:12px;background:rgba(20,26,34,.92);border:1px solid var(--line);border-radius:8px;padding:10px 12px;font-size:12px}\n' +
'  .legend label{display:block;cursor:pointer;margin:3px 0;user-select:none}\n' +
'  .legend .sw{display:inline-block;width:11px;height:11px;border-radius:3px;margin-right:7px;vertical-align:-1px}\n' +
'  .legend .ttl{margin-bottom:6px;color:#8aa;font-weight:600}\n' +
'  .cold{position:absolute;bottom:12px;left:12px;background:rgba(20,26,34,.92);border:1px solid var(--line);border-radius:8px;padding:9px 12px;font-size:12px;max-width:280px}\n' +
'  .cold b{color:#c8923a} .pill{font-size:10px;color:var(--dim)}\n' +
'  .ctl{position:absolute;top:12px;right:12px;display:flex;gap:6px}\n' +
'  .ctl button{background:rgba(20,26,34,.95);border:1px solid var(--line);color:var(--ink);border-radius:7px;padding:6px 11px;font-size:12px;cursor:pointer}\n' +
'  .ctl button:hover{border-color:#3a5a7a}\n' +
'  #about{position:absolute;top:54px;right:12px;width:330px;max-height:calc(100vh - 80px);overflow:auto;background:rgba(16,22,30,.98);border:1px solid var(--line);border-radius:10px;padding:16px 18px;font-size:13px;line-height:1.5;display:none;box-shadow:0 8px 40px rgba(0,0,0,.5)}\n' +
'  #about.open{display:block} #about h2{font-size:14px;margin:0 0 8px} #about h3{font-size:12px;margin:14px 0 4px;color:#8aa}\n' +
'  #about p{margin:6px 0;color:#c7d2dc} #about .sw{display:inline-block;width:11px;height:11px;border-radius:3px;margin-right:6px;vertical-align:-1px}\n' +
'  #about .em{color:#e7edf3}\n' +
'  #about .aboutx{position:absolute;top:9px;right:12px;cursor:pointer;color:#5a6b7a;font-weight:700;font-size:17px;line-height:1;padding:2px} #about .aboutx:hover{color:#e7edf3}\n' +
'  /* mobile: bound the \\u201cWhat am I looking at\\u201d panel to the viewport (8px margins) so its bottom is never out of frame; scrolls inside. */\n' +
'  @media (max-width:760px){\n' +
'    #about{top:8px;left:8px;right:8px;bottom:8px;width:auto;max-height:none;overflow:auto;-webkit-overflow-scrolling:touch}\n' +
'    #panel{width:var(--pw,86vw)}\n' +
'  }\n' +
'  text{fill:var(--ink);font-size:11px;pointer-events:none;user-select:none}\n' +
'  .node{cursor:grab} .node:hover circle{stroke:#fff;stroke-width:2px}\n' +
'  .k{color:var(--dim);font-size:11px;text-transform:uppercase;letter-spacing:.06em;margin:14px 0 4px}\n' +
'  .row{font-size:13px;padding:3px 0;border-bottom:1px solid var(--line)}\n' +
'  .tag{display:inline-block;background:#1d2630;border-radius:4px;padding:1px 6px;font-size:11px;margin:2px 4px 2px 0}\n' +
'  .y{color:#4caf7d} .n{color:var(--dim)}\n' +
'  .ctl button.active{border-color:#ffd479;color:#ffd479}\n' +
'  #strip{position:absolute;bottom:12px;left:50%;transform:translateX(-50%);display:none;align-items:center;gap:8px;background:rgba(16,22,30,.97);border:1px solid #3a4a5a;border-radius:10px;padding:9px 12px;font-size:12px;max-width:calc(100% - 40px);overflow-x:auto;box-shadow:0 8px 40px rgba(0,0,0,.5)}\n' +
'  #strip.open{display:flex}\n' +
'  #strip .step{cursor:pointer;padding:4px 9px;border-radius:7px;background:#19222c;border:1px solid #2b3744;white-space:nowrap}\n' +
'  #strip .step:hover{border-color:#ffd479} #strip .step b{color:#ffd479} #strip .step .amt{color:#4caf7d}\n' +
'  #strip .arr{color:#5a6b7a} #strip .ttl{color:#8aa;font-weight:600;margin-right:2px}\n' +
'  #strip .xx{cursor:pointer;color:#5a6b7a;margin-left:4px;font-weight:700;font-size:13px} #strip .xx:hover{color:#e7edf3}\n' +
'  @keyframes flow{to{stroke-dashoffset:-16}} .litedge{stroke-dasharray:6 6;animation:flow .8s linear infinite}\n' +
'  #recwrap{position:absolute;bottom:54px;left:50%;transform:translateX(-50%);display:none}\n' +
'  #recwrap.open{display:block}\n' +
'  #recsearch{background:rgba(16,22,30,.97);border:1px solid #3a5a7a;border-radius:8px;color:var(--ink);padding:6px 11px;font-size:12px;width:240px;outline:none;box-shadow:0 8px 40px rgba(0,0,0,.45)}\n' +
'  #recsearch::placeholder{color:#5a6b7a}\n' +
'  /* §2bcd Task 6 W-QR-INPUT: the no-typing camera affordance + the honest fallback overlay. */\n' +
'  #qrbtn{margin-left:7px;background:rgba(16,22,30,.97);border:1px solid #3a5a7a;border-radius:8px;color:#ffd479;padding:6px 11px;font-size:12px;cursor:pointer;vertical-align:middle} #qrbtn:hover{border-color:#ffd479}\n' +
'  #qrcam{position:absolute;bottom:96px;left:50%;transform:translateX(-50%);display:none;flex-direction:column;width:300px;max-width:calc(100% - 40px);background:rgba(12,16,22,.99);border:1px solid #3a4a5a;border-radius:12px;box-shadow:0 10px 48px rgba(0,0,0,.6);overflow:hidden;z-index:22}\n' +
'  #qrcam.open{display:flex} #qrcam .qrh{display:flex;align-items:center;gap:8px;padding:8px 11px;background:#121823;border-bottom:1px solid var(--line);font-size:12px} #qrcam .qrh b{flex:1;color:#e7edf3}\n' +
'  #qrcam .qrx{cursor:pointer;color:var(--dim);font-weight:700;font-size:14px;padding:0 3px} #qrcam .qrx:hover{color:#e2574c}\n' +
'  #qrvid{width:100%;height:200px;object-fit:cover;background:#05080c;display:none} #qrcam.live #qrvid{display:block}\n' +
'  #qrstatus{padding:9px 12px;font-size:12px;color:#c7d2dc;line-height:1.4} #qrstatus .qok{color:#4caf7d} #qrstatus .qbad{color:#e2a04c}\n' +
'  /* §2bcd Task 4 W-SWIPE-PICKER: the swipeable, scrollable record list at the trackball \\u2014 no typing. */\n' +
'  #recpick{position:absolute;bottom:140px;right:14px;width:190px;max-height:232px;overflow-y:auto;-webkit-overflow-scrolling:touch;touch-action:pan-y;display:none;flex-direction:column;gap:4px;background:rgba(16,22,30,.97);border:1px solid #3a5a7a;border-radius:11px;padding:7px;box-shadow:0 8px 40px rgba(0,0,0,.5);z-index:18}\n' +
'  #recpick.open{display:flex}\n' +
'  #recpick .rph{color:#8aa;font-size:9px;text-transform:uppercase;letter-spacing:.05em;padding:2px 4px 6px;position:sticky;top:0;background:rgba(16,22,30,.99)}\n' +
'  #recpick .rpr{display:flex;align-items:center;gap:8px;padding:9px 9px;border-radius:8px;background:#19222c;border:1px solid #2b3744;cursor:pointer;min-height:42px;touch-action:manipulation}\n' +
'  #recpick .rpr:hover,#recpick .rpr.sel{border-color:#ffd479} #recpick .rpr.sel{background:#1d2937}\n' +
'  #recpick .rpr b{flex:1;font-size:13px;color:#e7edf3} #recpick .rpr .rpa{color:#9fe0bd;font-size:11px}\n' +
'  #ball{position:absolute;bottom:64px;right:20px;width:62px;height:62px;border-radius:50%;cursor:grab;touch-action:none;background:radial-gradient(circle at 34% 30%,#4a6075,#1a2330 72%,#0c1118);border:1px solid #3a4a5a;box-shadow:0 6px 22px rgba(0,0,0,.55),inset 0 2px 7px rgba(255,255,255,.09)}\n' +
'  #ball:active{cursor:grabbing} #ball::after{content:"orbit";position:absolute;bottom:-15px;left:0;right:0;text-align:center;font-size:9px;color:#5a6b7a;letter-spacing:.1em}\n' +
'  #balldot{position:absolute;width:9px;height:9px;border-radius:50%;background:#ffd479;left:50%;top:50%;transform:translate(-50%,-50%);box-shadow:0 0 7px #ffd479;pointer-events:none}\n' +
'  #summary{margin-bottom:4px}\n' +
'  .rhdr{color:var(--dim);font-size:10px;text-transform:uppercase;letter-spacing:.06em;margin:16px 0 6px;line-height:1.4}\n' +
'  #stack{display:flex;flex-direction:column;gap:6px}\n' +
'  @keyframes barin{from{opacity:0;transform:translateX(-16px)}to{opacity:1;transform:none}}\n' +
'  .bar{background:#10161e;border:1px solid var(--line);border-radius:8px;overflow:hidden;touch-action:pan-y;animation:barin .22s ease}\n' +
'  .bar.col{opacity:.78} .bar.col .bh b{color:var(--dim)}\n' +
'  .bar .bh{display:flex;align-items:center;gap:7px;padding:7px 9px;cursor:pointer;user-select:none}\n' +
'  .bar .bh b{flex:1;font-size:13px} .bar .car{color:var(--dim);font-size:10px;transition:transform .15s} .bar.col .car{transform:rotate(-90deg)}\n' +
'  .bar .bx{color:var(--dim);cursor:pointer;font-weight:700;font-size:13px;padding:0 2px} .bar .bx:hover{color:#e2574c}\n' +
'  .bar .bb{padding:2px 10px 9px} .bar.col .bb{display:none}\n' +
'  .bar .trk{color:#c8923a;font-size:11px;margin-top:6px}\n' +
'  .recrows{margin-top:7px;border-top:1px solid var(--line);padding-top:6px}\n' +
'  .recrows .rk{color:var(--dim);font-size:10px;text-transform:uppercase;letter-spacing:.05em;margin-bottom:4px}\n' +
'  .recrows table{width:100%;border-collapse:collapse;font-size:11px}\n' +
'  .recrows td{padding:2px 6px 2px 0;color:#cdd6df;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:120px}\n' +
'  .recrows td.amt{color:#9fe0bd;text-align:right} .recrows tr+tr td{border-top:1px solid #161e28}\n' +
'  .recrows .rn{color:var(--dim);font-style:italic}\n' +
'  .tracebtn{margin-top:8px;background:#19222c;border:1px solid #3a5a7a;color:#ffd479;border-radius:7px;padding:5px 10px;font-size:12px;cursor:pointer} .tracebtn:hover{border-color:#ffd479}\n' +
'  /* §2bcd Task 3 W-DOSSIER: the right-click dossier panel (movable, lazy tabs Data|Rules|Columns|History). */\n' +
'  #dossier{position:absolute;top:70px;left:70px;width:440px;max-width:calc(100% - 40px);max-height:calc(100vh - 110px);display:none;flex-direction:column;background:rgba(16,22,30,.99);border:1px solid #3a4a5a;border-radius:11px;box-shadow:0 10px 48px rgba(0,0,0,.6);overflow:hidden;z-index:20}\n' +
'  #dossier.open{display:flex}\n' +
'  #dossier .dh{display:flex;align-items:center;gap:8px;padding:10px 13px;cursor:move;user-select:none;background:#121823;border-bottom:1px solid var(--line)}\n' +
'  #dossier .dh b{flex:1;font-size:14px} #dossier .dh .dpill{color:var(--dim);font-size:11px}\n' +
'  #dossier .dx{cursor:pointer;color:var(--dim);font-weight:700;font-size:15px;padding:0 3px} #dossier .dx:hover{color:#e2574c}\n' +
'  #dossier .dtabs{display:flex;gap:2px;padding:7px 10px 0;background:#121823;border-bottom:1px solid var(--line)}\n' +
'  #dossier .dtab{background:transparent;border:1px solid transparent;border-bottom:none;color:var(--dim);border-radius:7px 7px 0 0;padding:6px 12px;font-size:12px;cursor:pointer}\n' +
'  #dossier .dtab:hover{color:#cdd6df} #dossier .dtab.active{color:#ffd479;background:#0c1118;border-color:var(--line)}\n' +
'  #dossier .dbody{padding:12px 14px;overflow:auto;font-size:12px;line-height:1.5}\n' +
'  #dossier .dlead{color:#8aa;font-size:11px;margin-bottom:8px}\n' +
'  #dossier table.dt{width:100%;border-collapse:collapse;font-size:11px}\n' +
'  #dossier table.dt td,#dossier table.dt th{padding:3px 7px 3px 0;text-align:left;color:#cdd6df;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:130px}\n' +
'  #dossier table.dt th{color:var(--dim);font-weight:600;text-transform:uppercase;font-size:9px;letter-spacing:.05em;border-bottom:1px solid var(--line)}\n' +
'  #dossier table.dt tr+tr td{border-top:1px solid #161e28}\n' +
'  #dossier .rule{border-left:3px solid #4caf7d;padding:5px 9px;margin:6px 0;background:#10161e;border-radius:0 6px 6px 0}\n' +
'  #dossier .rule .rb{color:#8aa;font-size:11px;margin-bottom:3px} #dossier .rule code{color:#9fe0bd;font-family:ui-monospace,Menlo,monospace;font-size:11px;word-break:break-all;white-space:pre-wrap}\n' +
'  #dossier .col{padding:3px 0;border-bottom:1px solid #161e28} #dossier .col .cn{color:#e7edf3} #dossier .col .ct{color:var(--dim)} #dossier .col .cm{color:#e2a04c} #dossier .col .cfk{color:#6aa9ff}\n' +
'  #dossier .ophist{display:flex;flex-direction:column;gap:4px}\n' +
'  #dossier .oprow{display:flex;align-items:center;gap:8px;padding:5px 8px;background:#10161e;border:1px solid var(--line);border-radius:7px}\n' +
'  #dossier .oprow.reversed{opacity:.42;text-decoration:line-through} #dossier .oprow.reversed .opnote{text-decoration:none;display:block}\n' +
'  #dossier .oprow .opt{color:#ffd479;font-size:11px} #dossier .oprow .opc{color:#8aa;font-size:11px;flex:1} #dossier .oprow .opo{color:var(--dim);font-size:10px}\n' +
'  #dossier .oprow .oprev{cursor:pointer;color:#5a6b7a;font-size:13px;padding:0 3px} #dossier .oprow .oprev:hover{color:#ffd479}\n' +
'  #dossier .opnote{display:none;color:#c8923a;font-size:10px;margin-left:6px} #dossier .none{color:var(--dim);font-style:italic}\n' +
'  /* §2bcd Task 2 W-ACTIONS: the double-tap ACTION BLURB \\u2014 a small anchored chooser that follows the bubble. */\n' +
'  #blurb{position:absolute;display:none;flex-direction:column;min-width:168px;max-width:230px;background:rgba(16,22,30,.99);border:1px solid #3a4a5a;border-radius:10px;box-shadow:0 8px 34px rgba(0,0,0,.55);padding:9px 9px 8px;font-size:12px;z-index:25;transform:translate(-50%,14px)}\n' +
'  #blurb.open{display:flex} #blurb .bt{font-size:13px;font-weight:600;color:#e7edf3;margin:0 2px 6px;display:flex;align-items:center;gap:6px}\n' +
'  #blurb .bt .bn{flex:1} #blurb .bt .bp{color:var(--dim);font-size:10px;font-weight:400} #blurb .bx2{cursor:pointer;color:var(--dim);font-weight:700;font-size:13px;padding:0 2px} #blurb .bx2:hover{color:#e7edf3}\n' +
'  #blurb .act{display:block;width:100%;text-align:left;background:#19222c;border:1px solid #2b3744;color:#cdd6df;border-radius:7px;padding:6px 9px;font-size:12px;cursor:pointer;margin:3px 0}\n' +
'  #blurb .act:hover{border-color:#ffd479;color:#ffd479}\n' +
'  #blurb .crudk{color:var(--dim);font-size:9px;text-transform:uppercase;letter-spacing:.06em;margin:7px 2px 3px}\n' +
'  #blurb .act.crud.disabled{cursor:not-allowed;opacity:.42;color:#5a6b7a;border-style:dashed;pointer-events:none} #blurb .act.crud.disabled:hover{border-color:#2b3744;color:#5a6b7a}\n' +
'</style></head><body><div id="wrap">\n' +
'<div id="stage"><svg id="svg" width="100%" height="100%"></svg>\n' +
'  <div class="legend" id="legend"></div>\n' +
'  <div class="cold" id="coldbox"></div>\n' +
'  <div class="ctl"><button id="traceBtn">▸ Trace a record</button><button id="untangleBtn" title="spread bunched lines apart \\u2014 angular declutter (bubbles stay movable)">&#10022; Untangle</button><button id="muteBtn" title="mute / unmute the interface sounds">&#128266;</button><button id="resetBtn">⤢ Reset view</button><button id="panelToggle" title="hide / show the info panel">⟩</button></div>\n' +
'  <div id="strip"></div>\n' +
'  <div id="recwrap"><input id="recsearch" list="reclist" placeholder="search a record &mdash; e.g. 80001" autocomplete="off"><datalist id="reclist"></datalist><button id="qrbtn" title="scan a record&rsquo;s QR &mdash; no typing">&#9635; Scan QR</button></div>\n' +
'  <div id="recpick"></div>\n' +
'  <div id="qrcam"><div class="qrh"><b>Scan a record&rsquo;s QR</b><span class="qrx" id="qrx" title="close">&#10005;</span></div><video id="qrvid" playsinline muted></video><div id="qrstatus"></div></div>\n' +
'  <div id="ball" title="drag to orbit \\u2014 arrange the view like a BIM model"><div id="balldot"></div></div>\n' +
'  <div id="about"></div>\n' +
'  <div id="dossier"><div class="dh"><b id="dossierTitle">Dossier</b> <span class="dpill" id="dossierPill"></span><span class="dx" id="dossierX" title="close">\\u2715</span></div>\n' +
'    <div class="dtabs" id="dossierTabs"></div><div class="dbody" id="dossierBody"></div></div>\n' +
'  <div id="blurb"></div>\n' +
'  <div id="phandle" title="show / hide the info panel">&#10217;</div>\n' +
'</div>\n' +
'<div id="pgrip" title="drag to resize the panel"></div>\n' +
'<div id="panel"><h1>Glassbowl <span id="aboutBtn" class="appx" title="how to read this">ⓘ</span></h1><div class="sub">a live map of how your documents connect &amp; flow — built from the system itself. Click a bubble.</div>\n' +
'  <div id="summary"><div class="k">the links on this map</div><div id="spines"></div>\n' +
'  <div class="k">what the system runs</div><div id="cellcount"></div></div>\n' +
'  <div class="rhdr" id="recentHdr" style="display:none">recent items &mdash; like iDempiere&rsquo;s, but spatial &amp; glanceable. the engine keeps every step (op-log). tap to minimise &middot; swipe or &times; to dismiss</div>\n' +
'  <div id="stack"></div>\n' +
'</div></div>\n' +
'<script>\nvar G=' + data + ';\n' + VIEWER_JS + '\n</script></body></html>';
}

var VIEWER_JS = [
'var COLOR={containment:"#6aa9ff",derivation:"#4caf7d",settlement:"#e2574c",reference:"#5a6b7a"};',
'// business-user labels (the engine internals translated to what an operator sees).',
'var LABEL={containment:"document & its line items",derivation:"sales flow (order\\u2192ship\\u2192invoice\\u2192pay)",settlement:"matching & reconciliation",reference:"reference data (products, partners\\u2026)"};',
'var FRIENDLY={c_order:"Order",c_orderline:"Order line",m_inout:"Shipment / Receipt",m_inoutline:"Shipment line",c_invoice:"Invoice",c_invoiceline:"Invoice line",c_payment:"Payment",c_allocationhdr:"Payment applied",c_allocationline:"Payment applied",m_matchpo:"Order\\u2194Receipt match",m_matchinv:"Receipt\\u2194Invoice match",gl_journal:"Accounting journal",gl_journalline:"Journal line",c_bpartner:"Customer / Supplier",m_product:"Product"};',
'function fname(id){return FRIENDLY[id]||id;}',
'var VERB={createShipment:"creates a shipment/receipt",createInvoice:"creates an invoice",completeOrder:"completes the order",setStatus:"completes the document",allocate:"applies a payment to an invoice"};',
'var svg=document.getElementById("svg"),W=svg.clientWidth||window.innerWidth-340,H=svg.clientHeight||window.innerHeight;',
'var show={containment:1,derivation:1,settlement:1,reference:1};',
'var SVGNS="http://www.w3.org/2000/svg";',
'function el(tag,a){var e=document.createElementNS(SVGNS,tag);for(var key in a)e.setAttribute(key,a[key]);return e;}',
'var vp=el("g",{id:"vp"});svg.appendChild(vp);   // viewport group: pan/zoom transform live here',
'var px=0,py=0,k=1;function applyT(){vp.setAttribute("transform","translate("+px+","+py+") scale("+k+")");}',
'// deterministic init positions on a circle by index (no Math.random — replay/refresh stable).',
'var N=G.nodes,E=G.edges,idx={};N.forEach(function(n,i){idx[n.id]=i;var a=i/N.length*6.283;n.x=W/2+Math.cos(a)*Math.min(W,H)*0.32;n.y=H/2+Math.sin(a)*Math.min(W,H)*0.32;});',
'var maxG=Math.max.apply(null,N.map(function(n){return n.gravity||0}).concat([1]));',
'function radius(n){var base=n.kind==="master"?7:13;return base+18*Math.sqrt((n.gravity||0)/maxG);}',
'// tiny deterministic force layout: repulsion + spring + centering, fixed iterations.',
'for(var it=0;it<320;it++){',
' for(var i=0;i<N.length;i++){var a=N[i];a.fx=(W/2-a.x)*0.002;a.fy=(H/2-a.y)*0.002;',
'  for(var j=0;j<N.length;j++){if(i===j)continue;var b=N[j],dx=a.x-b.x,dy=a.y-b.y,d2=dx*dx+dy*dy+0.01,d=Math.sqrt(d2);var f=2400/d2;a.fx+=dx/d*f;a.fy+=dy/d*f;}}',
' E.forEach(function(e){var a=N[idx[e.from]],b=N[idx[e.to]];if(!a||!b)return;var dx=b.x-a.x,dy=b.y-a.y,d=Math.sqrt(dx*dx+dy*dy)+0.01,kk=(d-(e.kind==="reference"?240:120))*0.01;a.fx+=dx/d*kk;a.fy+=dy/d*kk;b.fx-=dx/d*kk;b.fy-=dy/d*kk;});',
' N.forEach(function(n){n.x+=Math.max(-8,Math.min(8,n.fx));n.y+=Math.max(-8,Math.min(8,n.fy));});}',
'N.forEach(function(n){n.hx=n.x;n.hy=n.y;});   // remember each bubble\\u2019s HOME so Reset can truly re-home them',
'// ── ORBIT (GLASSBOWL_DOSSIER \\u00a72-viz, W-ORBIT): the 3 spines are depth planes; the trackball moves the eye ──',
'// z is read from the data (n.z, assigned in the generator from spine role — NOT placed here).',
'var yaw=0,pitch=0;window.__yaw=0;window.__pitch=0;  // the trackball ORBITS only (bubbles stay movable); decluttering is the Untangle routine.',
'var CX=N.reduce(function(s,n){return s+n.x;},0)/N.length,CY=N.reduce(function(s,n){return s+n.y;},0)/N.length;',
'var ZMAX=Math.max(1,Math.max.apply(null,N.map(function(n){return Math.abs(n.z||0);})));',
'// ── UNTANGLE routine (line-density mitigation by ANGULAR RESOLUTION): at each node, edges with too-small',
'//    angles between them are BUNCHED \\u2014 rotate the neighbours apart around the node toward even angular',
'//    spacing, iterate, damp. Mutates real x,y so bubbles STAY MOVABLE; Reset re-homes. The trackball only orbits.',
'var UADJ={};E.forEach(function(e){(UADJ[e.from]=UADJ[e.from]||[]).push(e.to);(UADJ[e.to]=UADJ[e.to]||[]).push(e.from);});',
'function untangleStep(){var dx={},dy={};',
' N.forEach(function(n){var nb=UADJ[n.id]||[];if(nb.length<2)return;',
'  var arr=nb.map(function(id){var m=N[idx[id]];return {id:id,a:Math.atan2(m.y-n.y,m.x-n.x),d:Math.max(8,Math.hypot(m.x-n.x,m.y-n.y))};}).sort(function(p,q){return p.a-q.a;});',
'  var k=arr.length,ideal=2*Math.PI/k;',
'  for(var i=0;i<k;i++){var j=(i+1)%k,gap=arr[j].a-arr[i].a;if(j===0)gap+=2*Math.PI;',
'   if(gap<ideal){var push=Math.min(0.12,(ideal-gap)*0.12),pi=arr[i],pj=arr[j];',
'    dx[pi.id]=(dx[pi.id]||0)+(n.x+pi.d*Math.cos(pi.a-push)-N[idx[pi.id]].x);dy[pi.id]=(dy[pi.id]||0)+(n.y+pi.d*Math.sin(pi.a-push)-N[idx[pi.id]].y);',
'    dx[pj.id]=(dx[pj.id]||0)+(n.x+pj.d*Math.cos(pj.a+push)-N[idx[pj.id]].x);dy[pj.id]=(dy[pj.id]||0)+(n.y+pj.d*Math.sin(pj.a+push)-N[idx[pj.id]].y);}}});',
' E.forEach(function(e){var a=N[idx[e.from]],b=N[idx[e.to]];if(!a||!b)return;var ddx=b.x-a.x,ddy=b.y-a.y,d=Math.hypot(ddx,ddy)+0.01,L=(e.kind==="reference"?210:125),f=(d-L)*0.05;dx[a.id]=(dx[a.id]||0)+ddx/d*f;dy[a.id]=(dy[a.id]||0)+ddy/d*f;dx[b.id]=(dx[b.id]||0)-ddx/d*f;dy[b.id]=(dy[b.id]||0)-ddy/d*f;});',  // edge springs keep it COMPACT (no fly-out)
' N.forEach(function(n){dx[n.id]=(dx[n.id]||0)+(CX-n.x)*0.004;dy[n.id]=(dy[n.id]||0)+(CY-n.y)*0.004;});',  // gentle centering
' var moved=0;N.forEach(function(n){if(dx[n.id]){var d=Math.max(-10,Math.min(10,dx[n.id]));n.x+=d;moved+=Math.abs(d);}if(dy[n.id]){var e2=Math.max(-10,Math.min(10,dy[n.id]));n.y+=e2;moved+=Math.abs(e2);}});return moved;}',
'// AVG of each node\\u2019s SMALLEST incident-edge gap (radians) \\u2014 the bunching metric (small = lines bunched).',
'function avgMinAngle(){var sum=0,cnt=0;N.forEach(function(n){var nb=UADJ[n.id]||[];if(nb.length<2)return;var ang=nb.map(function(id){var m=N[idx[id]];return Math.atan2(m.y-n.y,m.x-n.x);}).sort(function(a,b){return a-b;});var lo=2*Math.PI;for(var i=0;i<ang.length;i++){var j=(i+1)%ang.length,g=ang[j]-ang[i];if(j===0)g+=2*Math.PI;if(g<lo)lo=g;}sum+=lo;cnt++;});return cnt?sum/cnt:0;}',
'window.avgMinAngle=avgMinAngle;window.UADJ=UADJ;',
'function untangle(){var fr=0;(function pass(){for(var r=0;r<4;r++)untangleStep();draw();if(++fr<34)requestAnimationFrame(pass);else{N.forEach(function(n){n.hx=n.x;n.hy=n.y;});console.log("\\u00a7UNTANGLE avgMinAngle="+(avgMinAngle()*180/Math.PI).toFixed(1)+"deg");}})();}',
'window.untangle=untangle;',
'function orbitAmt(){return Math.min(1,Math.abs(Math.sin(yaw))+Math.abs(Math.sin(pitch)));}',
'// orthographic projection (yaw about Y, then pitch about X). At yaw=pitch=0 it is the IDENTITY on x,y —',
'// the planes collapse head-on, so the at-rest bowl is PIXEL-IDENTICAL to the flat static layout.',
'function project(n){var ax=n.x-CX,ay=n.y-CY,az=(n.z||0);var cY=Math.cos(yaw),sY=Math.sin(yaw),cP=Math.cos(pitch),sP=Math.sin(pitch);var X1=ax*cY+az*sY,Z1=-ax*sY+az*cY,Y2=ay*cP-Z1*sP,Z2=ay*sP+Z1*cP;n.sx=CX+X1;n.sy=CY+Y2;n.depth=Z2;}',
'// inverse: recover a node\\u2019s plane (x,y) from a desired screen point + its fixed z (keeps bubble-drag working under orbit).',
'function unproject(sx,sy,az){var cY=Math.cos(yaw),sY=Math.sin(yaw),cP=Math.cos(pitch),sP=Math.sin(pitch);if(Math.abs(cY)<0.15||Math.abs(cP)<0.15)return null;var ax=((sx-CX)-az*sY)/cY;var Z1=-ax*sY+az*cY;var ay=((sy-CY)+Z1*sP)/cP;return {x:CX+ax,y:CY+ay};}',
'// build SVG via createElementNS (NOT innerHTML — innerHTML-built SVG fails to lay out headless).',
'// ── FOCUS filter: click a bubble \\u2192 show only its own links (Order \\u2192 its Order lines\\u2026); bg-click clears ──',
'var focusId=null;var dossierTab="Data";  /* the dossier STICKS to the last-open tab across bubbles */',
'function edgeFocus(e){return e.from===focusId||e.to===focusId;}',
'function inFocus(id){return id===focusId||E.some(function(e){return (e.from===focusId&&e.to===id)||(e.to===focusId&&e.from===id);});}',
'function setFocus(id){focusId=(focusId===id?null:id);draw();}',
'function draw(){N.forEach(project);while(vp.firstChild)vp.removeChild(vp.firstChild);var oa=orbitAmt(),fo=focusId&&!traceMode;',
' E.forEach(function(e){if(!show[e.kind])return;var a=N[idx[e.from]],b=N[idx[e.to]];if(!a||!b)return;var lo=litEdge(e),ef=fo&&edgeFocus(e),op,sw,col=lo?"#ffd479":COLOR[e.kind];if(traceMode){op=lo?0.95:0.04;sw=lo?3:0.8;}else if(fo){op=ef?0.85:0.035;sw=ef?2:0.6;if(ef)col="#ffd479";}else{op=e.kind==="reference"?0.18:0.5;sw=e.kind==="reference"?1:1.6;}var ln=el("line",{x1:a.sx,y1:a.sy,x2:b.sx,y2:b.sy,stroke:col,"stroke-opacity":op,"stroke-width":sw});if(lo)ln.setAttribute("class","litedge");vp.appendChild(ln);});',
' var order=N.slice().sort(function(a,b){return a.depth-b.depth;});',   // painter: far plane (low depth) drawn first
' order.forEach(function(n){var vis=E.some(function(e){return show[e.kind]&&(e.from===n.id||e.to===n.id)})||(n.cells&&n.cells.length);if(!vis&&n.kind==="master")return;var col=n.settlement?"#e2574c":n.kind==="master"?"#2b3744":(n.gravity>0?"#4caf7d":"#3a4a5a");',
'  var sc=Math.max(0.6,Math.min(1.5,1+(n.depth/ZMAX)*0.45*oa)),r=radius(n)*sc;',   // depth size cue, gated by orbit amount (0 at rest)
'  var far=Math.max(0,(ZMAX-n.depth)/(2*ZMAX)),dop=1-far*0.6*oa;',                   // far plane dims, also gated → 1 at rest
'  var g=el("g",{"class":"node"});(function(node){g.addEventListener("pointerdown",function(ev){startDragNode(ev,node);});g.addEventListener("click",function(){if(!moved)pick(node.id);});g.addEventListener("contextmenu",function(ev){ev.preventDefault();openDossier(node.id);});g.addEventListener("dblclick",function(ev){ev.preventDefault();ev.stopPropagation();openBlurb(node.id);});})(n);',
'  var dim=(traceMode&&!litNode(n.id))||(fo&&!inFocus(n.id)),hi=litNode(n.id)||(fo&&n.id===focusId),fop=dim?0.12:(traceMode?1:dop);',
'  g.appendChild(el("circle",{cx:n.sx,cy:n.sy,r:r,fill:col,"fill-opacity":fop,stroke:hi?"#ffd479":"#0c0f14","stroke-width":hi?3:1.5}));',
'  var t=el("text",{x:n.sx,y:(n.sy+r+11),"text-anchor":"middle","fill-opacity":(dim?0.18:(traceMode?1:dop))});t.textContent=fname(n.id);g.appendChild(t);vp.appendChild(g);});if(typeof positionBlurb==="function")positionBlurb();}',
'// ── interaction: drag a bubble, pan the background, scroll to zoom ──',
'function world(ev){var rc=svg.getBoundingClientRect();return {x:(ev.clientX-rc.left-px)/k,y:(ev.clientY-rc.top-py)/k};}',
'var drag=null,panning=null,moved=false;',
'// bubble-drag anchors in SCREEN space then inverse-projects back to the node\\u2019s plane (identity at rest).',
'function startDragNode(ev,node){ev.stopPropagation();project(node);var w=world(ev);drag={node:node,sx0:node.sx,sy0:node.sy,w0x:w.x,w0y:w.y,az:(node.z||0)};moved=false;try{svg.setPointerCapture(ev.pointerId);}catch(e){}}',
'svg.addEventListener("pointerdown",function(ev){if(drag)return;var rc=svg.getBoundingClientRect();panning={sx:ev.clientX-rc.left-px,sy:ev.clientY-rc.top-py};moved=false;svg.classList.add("panning");try{svg.setPointerCapture(ev.pointerId);}catch(e){}});',
'svg.addEventListener("pointermove",function(ev){if(drag){var w=world(ev);var p=unproject(drag.sx0+(w.x-drag.w0x),drag.sy0+(w.y-drag.w0y),drag.az);if(p){drag.node.x=p.x;drag.node.y=p.y;}moved=true;draw();}else if(panning){var rc=svg.getBoundingClientRect();px=ev.clientX-rc.left-panning.sx;py=ev.clientY-rc.top-panning.sy;moved=true;applyT();}});',
'function endPtr(){drag=null;panning=null;svg.classList.remove("panning");}',
'svg.addEventListener("pointerup",endPtr);svg.addEventListener("pointercancel",endPtr);',
'svg.addEventListener("click",function(ev){if((ev.target===svg||ev.target.id==="vp")&&!moved)clearFocus();});',
'svg.addEventListener("wheel",function(ev){ev.preventDefault();var rc=svg.getBoundingClientRect();var mx=ev.clientX-rc.left,my=ev.clientY-rc.top;var f=ev.deltaY<0?1.12:0.89;var nk=Math.max(0.25,Math.min(6,k*f));px=mx-(mx-px)*(nk/k);py=my-(my-py)*(nk/k);k=nk;applyT();},{passive:false});',
'// ── the trackball: grab the sphere to ORBIT the view (bubbles stay static in 3D; only the eye moves) ──',
'var ball=document.getElementById("ball"),balldot=document.getElementById("balldot"),orbiting=null;',
'function updateBall(){balldot.style.left=(50+Math.sin(yaw)*28)+"%";balldot.style.top=(50-Math.sin(pitch)*28)+"%";}',
'function setOrbit(y,p){yaw=y;pitch=Math.max(-1.3,Math.min(1.3,p));window.__yaw=yaw;window.__pitch=pitch;updateBall();draw();}',
'ball.addEventListener("pointerdown",function(ev){ev.preventDefault();ev.stopPropagation();orbiting={x:ev.clientX,y:ev.clientY,y0:yaw,p0:pitch};try{ball.setPointerCapture(ev.pointerId);}catch(e){}});',
'ball.addEventListener("pointermove",function(ev){if(!orbiting)return;setOrbit(orbiting.y0+(ev.clientX-orbiting.x)*0.012,orbiting.p0+(ev.clientY-orbiting.y)*0.012);});',
'function endOrbit(){orbiting=null;}ball.addEventListener("pointerup",endOrbit);ball.addEventListener("pointercancel",endOrbit);',
'window.setOrbit=setOrbit;updateBall();',
'document.getElementById("resetBtn").addEventListener("click",function(){px=0;py=0;k=1;yaw=0;pitch=0;focusId=null;window.__yaw=0;window.__pitch=0;N.forEach(function(n){n.x=n.hx;n.y=n.hy;});updateBall();applyT();draw();});',
'document.getElementById("untangleBtn").addEventListener("click",function(){untangle();});',
'// collapse the right info panel \\u2192 the graph takes the full canvas (positions are absolute, no re-layout needed).',
'// driven by BOTH the toolbar \\u27e9 button AND the mobile-friendly yellow #phandle at the panel border.',
'function isMobile(){try{return window.matchMedia("(max-width:760px)").matches||window.innerWidth<760;}catch(e){return window.innerWidth<760;}}',
'function syncPanelUI(c){var pt=document.getElementById("panelToggle");if(pt){pt.textContent=c?"\\u27e8":"\\u27e9";pt.title=(c?"show":"hide")+" the info panel";}var ph=document.getElementById("phandle");if(ph){ph.innerHTML=c?"&#10216;":"&#10217;";ph.title=(c?"show":"hide")+" the info panel";}}',
'function setPanelCollapsed(c){document.getElementById("wrap").classList.toggle("collapsed",c);syncPanelUI(c);}',
'function togglePanel(){setPanelCollapsed(!document.getElementById("wrap").classList.contains("collapsed"));}',
'document.getElementById("panelToggle").addEventListener("click",togglePanel);',
'(function(){var ph=document.getElementById("phandle");if(ph)ph.addEventListener("click",function(ev){ev.stopPropagation();togglePanel();});})();',
'window.togglePanel=togglePanel;window.setPanelCollapsed=setPanelCollapsed;',
'// drag the gripper to RESIZE the info panel (width \\u2192 a CSS var, clamped). Composes with collapse.',
'(function(){var grip=document.getElementById("pgrip"),WR=document.getElementById("wrap"),PAN=document.getElementById("panel"),rz=null;',
' grip.addEventListener("pointerdown",function(ev){ev.preventDefault();rz={x:ev.clientX,w:PAN.getBoundingClientRect().width};WR.classList.add("resizing");try{grip.setPointerCapture(ev.pointerId);}catch(e){}});',
' grip.addEventListener("pointermove",function(ev){if(!rz)return;var nw=Math.max(240,Math.min(640,rz.w-(ev.clientX-rz.x)));document.documentElement.style.setProperty("--pw",nw+"px");window.__pw=nw;});',
' function ez(){rz=null;WR.classList.remove("resizing");}grip.addEventListener("pointerup",ez);grip.addEventListener("pointercancel",ez);})();',
'// ── inspector (right panel), in plain business language ──',
'function pick(id){var n=N[idx[id]];pickJive(n);var h=\'<div class=k>this is</div><div class=row><b>\'+fname(id)+\'</b> <span class=pill>\'+id+\'</span></div>\';',
' var ce=E.filter(function(e){return e.from===id&&show[e.kind]});if(ce.length){h+=\'<div class=k>connects to</div>\';ce.slice(0,40).forEach(function(e){h+=\'<div class=row><span class=tag style="border-left:3px solid \'+COLOR[e.kind]+\'">\'+LABEL[e.kind].split(" (")[0]+\'</span>\'+fname(e.to)+\'</div>\';});}',
' if(n.cells&&n.cells.length){h+=\'<div class=k>what the system does here</div>\';n.cells.forEach(function(c){var acts=c.verbs.map(function(v){return VERB[v]||v;});h+=\'<div class=row>\'+(acts.length?acts.map(function(a){return \'<span class=tag>\'+a+\'</span>\'}).join(""):\'<span class=pill>completes the document</span>\')+\'<br><span class=pill>needs matching/reconciliation: </span><span class=\'+(c.matcher?"y":"n")+\'>\'+(c.matcher?"yes":"no")+\'</span><br><span class=pill>checks before saving: \'+c.guards+\' \\u00b7 times used: \'+c.ops+\'</span></div>\';});}else if(n.kind==="master"){h+=\'<div class=k>what the system does here</div><div class=row class=n>a reference list — looked up by the documents, no workflow of its own</div>\';}',
' if(LIN&&LIN.litNodes&&LIN.litNodes.indexOf(id)>=0)h+=\'<button class=tracebtn onclick="window.setTrace(true)">\\u25b8 trace this record\\u2019s flow</button>\';',
' var runs=(n.cells||[]).reduce(function(s,c){return s+(c.ops||0);},0);focusId=id;pushBar(id,fname(id),h,runs);injectRecs(id);draw();}',
'// ── Task 1 (GLASSBOWL_DOSSIER \\u00a72bcd, W-DATA-BARS): surface the REAL rows in the clicked bubble\\u2019s bar.',
'//    The map shows FK structure; this shows the actual records. Bundle tables \\u2192 lazy-load via withBundle',
'//    (the same glassbowl_data.db the trace uses) and inject \\u223c3 real rows. Non-bundle tables \\u2192 the honest',
'//    \\u201cnot carried\\u201d note (never faked). Columns are chosen from the table\\u2019s PRAGMA \\u2014 extract, not hardcode. ──',
'var BUNDLE_T=(G.bundleTables||[]),DATA_N=3;',
'function barFor(id){return STACK.querySelector(\'[data-id="\'+id+\'"]\');}',
'// pick business-meaningful columns from the actual PRAGMA: an identity (documentno|name), an amount, a status.',
'function recCols(db,t){var cols=[];try{var pr=db.exec("PRAGMA table_info("+t+")");if(pr.length)pr[0].values.forEach(function(v){cols.push(String(v[1]).toLowerCase());});}catch(e){return null;}if(!cols.length)return null;var have=function(c){return cols.indexOf(c)>=0;},pick=[];',
' if(have("documentno"))pick.push({c:"documentno",cls:""});else if(have("name"))pick.push({c:"name",cls:""});else if(have("value"))pick.push({c:"value",cls:""});',
' ["grandtotal","payamt","amount","linenetamt","priceactual"].some(function(c){if(have(c)){pick.push({c:c,cls:"amt"});return true;}return false;});',
' if(have("docstatus"))pick.push({c:"docstatus",cls:""});else if(have("isactive"))pick.push({c:"isactive",cls:""});',
' return pick.length?pick:null;}',
'function injectRecs(id){var bar=barFor(id);if(!bar)return;var bb=bar.querySelector(".bb");if(!bb||bb.querySelector(".recrows"))return;',
' var box=document.createElement("div");box.className="recrows";bb.appendChild(box);',
' if(BUNDLE_T.indexOf(id)<0){thunkJive();box.innerHTML=\'<div class=rk>the actual records</div><div class=rn>records not carried in this dataset</div>\';return;}',
' box.innerHTML=\'<div class=rk>the actual records</div><div class=rn>loading\\u2026</div>\';',
' withBundle(function(db){var cur=barFor(id);if(!cur)return;var b2=cur.querySelector(".recrows");if(!b2)return;var cols=recCols(db,id);',
'  if(!cols){b2.innerHTML=\'<div class=rk>the actual records</div><div class=rn>records not carried in this dataset</div>\';return;}',
'  var sel=cols.map(function(o){return o.c;}).join(", "),res;try{res=db.exec("SELECT "+sel+" FROM "+id+" LIMIT "+DATA_N);}catch(e){b2.innerHTML=\'<div class=rk>the actual records</div><div class=rn>records not carried in this dataset</div>\';return;}',
'  if(!res.length||!res[0].values.length){b2.innerHTML=\'<div class=rk>the actual records</div><div class=rn>no rows</div>\';return;}',
'  var rc=res[0].values.length,html=\'<div class=rk>the actual records (\'+rc+\' shown)</div><table>\';res[0].values.forEach(function(v){html+="<tr>";cols.forEach(function(o,i){var raw=v[i],val;if(raw==null)val="\\u2014";else if(o.cls==="amt"&&!isNaN(Number(raw)))val=Number(raw).toFixed(2);else val=String(raw);html+=\'<td\'+(o.cls?\' class="\'+o.cls+\'"\':"")+\' title="\'+val.replace(/"/g,"&quot;")+\'">\'+val+"</td>";});html+="</tr>";});html+="</table>";b2.innerHTML=html;b2.setAttribute("data-rows",rc);});}',
'function clearFocus(){if(typeof hideBlurb==="function")hideBlurb();if(focusId){focusId=null;draw();}}',
'// ── the RECENT stack: each look-up drops a collapsible bar (accordion); swipe or \\u2715 to dismiss. The',
'//    \\u201ctracked N runs\\u201d line is the real op-count (kernel op-log depth, §0.6) — extracted, not invented. ──',
'var STACK=document.getElementById("stack"),RH=document.getElementById("recentHdr"),RECN=10;',
'function removeBar(b){dismissJive();b.style.transition="transform .15s,opacity .15s";b.style.transform="translateX(60px)";b.style.opacity=0;setTimeout(function(){if(b.parentNode)b.parentNode.removeChild(b);if(!STACK.children.length)RH.style.display="none";},150);}',
'function addSwipe(b){var sx=0,on=false;b.addEventListener("pointerdown",function(e){if(e.target.classList.contains("bx"))return;sx=e.clientX;on=true;});b.addEventListener("pointermove",function(e){if(!on)return;var dx=e.clientX-sx;if(dx>0)b.style.transform="translateX("+dx+"px)";});b.addEventListener("pointerup",function(e){if(!on)return;on=false;if(e.clientX-sx>60)removeBar(b);else b.style.transform="";});b.addEventListener("pointercancel",function(){on=false;b.style.transform="";});}',
'function pushBar(id,title,bodyHtml,runs){barJive();var ex=STACK.querySelector(\'[data-id="\'+id+\'"]\');if(ex)ex.parentNode.removeChild(ex);Array.prototype.forEach.call(STACK.children,function(c){c.classList.add("col");});',
' var b=document.createElement("div");b.className="bar";b.setAttribute("data-id",id);',
' var trk=runs>0?\'<div class=trk>\\u27d0 the engine has tracked \'+runs+\' run\'+(runs==1?"":"s")+\' here, kept in the op-log</div>\':"";',
' b.innerHTML=\'<div class=bh><span class=car>\\u25be</span><b>\'+title+\'</b><span class=bx title=dismiss>\\u2715</span></div><div class=bb>\'+bodyHtml+trk+\'</div>\';',
' b.querySelector(".bh").addEventListener("click",function(ev){if(ev.target.classList.contains("bx"))return;b.classList.toggle("col");});',
' b.querySelector(".bx").addEventListener("click",function(){removeBar(b);});',
' addSwipe(b);STACK.insertBefore(b,STACK.firstChild);RH.style.display="block";',
' while(STACK.children.length>RECN)STACK.removeChild(STACK.lastChild);}',
'// ── legend (business labels) ──',
'var lg=document.getElementById("legend"),lh="<div class=ttl>the links &mdash; click to hide</div>";["derivation","settlement","containment","reference"].forEach(function(kk){lh+=\'<label><input type=checkbox checked onchange="show.\'+kk+\'=this.checked;draw()"><span class=sw style="background:\'+COLOR[kk]+\'"></span>\'+LABEL[kk]+\' (\'+G.spines[kk]+\')</label>\';});lg.innerHTML=lh;',
'// ── "not switched on yet" badge ──',
'document.getElementById("coldbox").innerHTML=\'<b>\'+G.cold.length+\' features not switched on yet</b><br><span class=pill>the whole platform is already here, dormant — it turns on the moment you use it. Nothing to install.</span>\';',
'// ── right-panel summary ──',
'document.getElementById("spines").innerHTML=["derivation","settlement","containment","reference"].map(function(kk){return \'<div class=row><span class=sw style="display:inline-block;width:11px;height:11px;border-radius:3px;margin-right:7px;background:\'+COLOR[kk]+\'"></span>\'+LABEL[kk].split(" (")[0]+\' <b>\'+G.spines[kk]+\'</b></div>\'}).join("");',
'var gt=G.gravityTop?G.gravityTop.cell.replace(/[()]/g,"").split(",")[0].toLowerCase():"";',
'document.getElementById("cellcount").innerHTML=\'<div class=row>\'+G.cells.length+\' active workflows \\u00b7 busiest is <b>\'+(gt?fname(gt):"-")+\'</b></div>\';',
'// ── About panel (aimed at a business / ERP user) ──',
'var ABOUT=\'<h2>What am I looking at?</h2><p>A live map of how your business documents connect and flow \\u2014 orders, shipments, invoices, payments \\u2014 drawn automatically from the system itself.</p><h3>The bubbles</h3><p>Each bubble is something your business tracks. <span class=em>Bigger means used more.</span> Click a bubble to see what it links to and what the system does there. Drag bubbles to tidy them up, scroll to zoom, drag the empty background to move around.</p><h3>The coloured links</h3><p><span class=sw style="background:#4caf7d"></span><span class=em>Sales flow</span> \\u2014 an order becomes a shipment, then an invoice, then a payment. This is most of your day.</p><p><span class=sw style="background:#e2574c"></span><span class=em>Matching &amp; reconciliation</span> \\u2014 checking that what was ordered, received, billed and paid all agree. The careful part a bookkeeper double-checks.</p><p><span class=sw style="background:#6aa9ff"></span><span class=em>Document &amp; its lines</span> \\u2014 an invoice and the products listed on it.</p><p><span class=sw style="background:#5a6b7a"></span><span class=em>Reference data</span> \\u2014 links to your lists of products, customers and suppliers.</p><h3>Why it matters</h3><p>The green flow is the easy everyday path. The red links are where money is reconciled \\u2014 where mistakes cost you. The map shows at a glance where the real work concentrates.</p><h3>\\u201cNot switched on yet\\u201d</h3><p>The full platform is already here, most of it dormant. Features light up the moment you use them \\u2014 nothing to install or configure up front.</p>\';',
'document.getElementById("about").innerHTML=\'<span class="aboutx" id="aboutX" title="close">\\u2715</span>\'+ABOUT;',
'document.getElementById("aboutBtn").addEventListener("click",function(){document.getElementById("about").classList.toggle("open");});',
'(function(){var ax=document.getElementById("aboutX");if(ax)ax.addEventListener("click",function(){document.getElementById("about").classList.remove("open");});})();',
'// ── Phase 2a: LINEAGE TRACE (GLASSBOWL_DOSSIER \\u00a72a, W-LIFECYCLE) — light a real record\\u2019s whole life ──',
'var LIN=G.lineage||null,traceMode=false,litN={},xchecked=false,GDB=null,recMap={},curChain=(LIN?LIN.chain:[]);',
'function rebuildLit(){litN={};curChain.forEach(function(c){if(c.id!=null)litN[c.table]=1;});}rebuildLit();',
'function litNode(id){return traceMode&&!!litN[id];}',
'function litEdge(e){return traceMode&&litN[e.from]&&litN[e.to]&&(e.kind==="derivation"||e.kind==="settlement");}',
'function fmtAmt(a){return a==null?"":" <span class=amt>("+Number(a).toFixed(2)+")</span>";}',
'function buildStrip(){var s=document.getElementById("strip");if(!curChain.length){s.innerHTML="<span class=pill>no record traced</span>";return;}var h="<span class=ttl>this record\\u2019s life:</span>";curChain.forEach(function(c,i){if(i)h+=\'<span class=arr>\\u25b8</span>\';var who=c.id==null?"\\u2014":(c.documentno!=null?"#"+c.documentno:"#"+c.id);h+=\'<span class=step data-t="\'+c.table+\'"><b>\'+(c.friendly||c.table)+\'</b> \'+who+fmtAmt(c.amount)+\'</span>\';});h+=\'<span class=xx id=stripX title="exit trace">\\u2715</span>\';s.innerHTML=h;Array.prototype.forEach.call(s.querySelectorAll(".step"),function(elm){elm.addEventListener("click",function(){pick(elm.getAttribute("data-t"));});});document.getElementById("stripX").addEventListener("click",function(){setTrace(false);});}',
'function applyChain(ch){curChain=ch;rebuildLit();buildStrip();draw();}',
'function setTrace(on){if(on)focusId=null;traceMode=on;document.getElementById("strip").classList.toggle("open",on);var rw=document.getElementById("recwrap");if(rw)rw.classList.toggle("open",on);var rp=document.getElementById("recpick");if(rp)rp.classList.toggle("open",on);if(!on)qrClose();document.getElementById("traceBtn").classList.toggle("active",on);if(on){if(!curChain.length&&LIN)curChain=LIN.chain;rebuildLit();buildStrip();traceJive();}draw();if(on)ensureXcheck();}',
'document.getElementById("traceBtn").addEventListener("click",function(){setTrace(!traceMode);});',
'// load the sql.js bundle ONCE, retain it (GDB) for the xcheck AND the record search.',
'function withBundle(cb){if(GDB){cb(GDB);return;}try{var sc=document.createElement("script");sc.src="sqljs/sql-wasm.js";sc.onload=function(){if(!window.initSqlJs){xcDone("skip=no-initSqlJs");return;}window.initSqlJs({locateFile:function(f){return "sqljs/"+f;}}).then(function(SQL){return fetch("glassbowl_data.db").then(function(r){return r.arrayBuffer();}).then(function(buf){GDB=new SQL.Database(new Uint8Array(buf));cb(GDB);});}).catch(function(e){xcDone("error="+e.message);});};sc.onerror=function(){xcDone("skip=no-sqljs-asset");};document.head.appendChild(sc);}catch(e){xcDone("error="+e.message);}}',
'// walk the bundle for ANY seed order, using the declarative LIN.steps (dep=seed|prior table) — extract.',
'function walkBundle(db,seed){var got={},chain=[];LIN.steps.forEach(function(s){var pid=s.dep==="seed"?seed:(got[s.dep]?got[s.dep].id:null),row=null;if(pid!=null){var res=db.exec(s.base+pid+s.order);if(res.length&&res[0].values.length){var cols=res[0].columns,v=res[0].values[0],o={};cols.forEach(function(c,i){o[c]=v[i];});row=o;}}if(row){got[s.table]=row;chain.push({table:s.table,friendly:s.friendly,id:row.id,documentno:row.documentno==null?null:row.documentno,amount:row.amount==null?null:row.amount});}else chain.push({table:s.table,friendly:s.friendly,id:null,documentno:null,amount:null});});return chain;}',
'function xcDone(msg){console.log("\\u00a7LIFECYCLE-XCHECK "+msg);window.__xchecked=true;}',
'function ensureXcheck(){if(xchecked||!LIN||!LIN.steps)return;xchecked=true;withBundle(function(db){var bw=walkBundle(db,LIN.seed),ok=true,parts=[];LIN.chain.forEach(function(c,i){if(String(bw[i].id)!==String(c.id))ok=false;parts.push(c.table+"#"+bw[i].id);});xcDone("record="+LIN.record+" bundle=[ "+parts.join(" \\u2192 ")+" ] agree="+(ok?"Y":"N"));populateRecs(db);if(pendingSeed!=null){applyChain(walkBundle(db,pendingSeed));pendingSeed=null;}});}',
'// the SEARCH control: a datalist of real orders (documentno) from the bundle; pick \\u2192 trace it in-browser.',
'function populateRecs(db){try{var dl=document.getElementById("reclist");if(!dl||dl.childElementCount)return;var res=db.exec("SELECT documentno, c_order_id, grandtotal FROM c_order ORDER BY documentno");if(!res.length)return;var rows=[];res[0].values.forEach(function(v){var dn=String(v[0]);recMap[dn]=v[1];var o=document.createElement("option");o.value=dn;dl.appendChild(o);rows.push({dn:dn,oid:v[1],amt:v[2]});});window.__recs=Object.keys(recMap).length;buildRecPick(rows);}catch(e){}}',
'function doSearch(){var inp=document.getElementById("recsearch"),dn=inp.value.trim();if(!dn)return;var oid=recMap[dn];if(oid==null||!GDB){inp.style.borderColor="#e2574c";return;}inp.style.borderColor="";applyChain(walkBundle(GDB,oid));}',
'(function(){var inp=document.getElementById("recsearch");if(inp){inp.addEventListener("change",doSearch);inp.addEventListener("keydown",function(e){if(e.key==="Enter")doSearch();});}})();',
'window.setTrace=setTrace;window.applyChain=applyChain;window.doSearch=doSearch;',
'// ── Task 3 (GLASSBOWL_DOSSIER \\u00a72bcd, W-DOSSIER): the right-click DOSSIER — the 5\\u20137 iDempiere',
'//    windows fused into one movable panel with lazy tabs Data | Rules | Columns | History. Every tab is',
'//    EXTRACTED: Data = real bundle rows; Rules = node.dossier.rules (erp_rules Validation); Columns =',
'//    node.dossier.columns (ad_column); History = G.oplog (real kernel_ops). History\\u2019s \\u21b6 preview is',
'//    PURE-READ (className toggle only — no localStorage, no db write); the read-only undo seam to T3. ──',
'var DOSS=document.getElementById("dossier"),DTITLE=document.getElementById("dossierTitle"),DPILL=document.getElementById("dossierPill"),DTABS=document.getElementById("dossierTabs"),DBODY=document.getElementById("dossierBody");',
'var OPLOG=(G.oplog||[]),dossierId=null,dossierRendered={};',
'function esc(s){return String(s==null?"":s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;");}',
'// count the real ops tracked for a cell: prefer the node\\u2019s diff_oracle cell.ops, else count it in the op-log.',
'function opCountFor(id){var n=N[idx[id]];if(n&&n.cells&&n.cells.length){var s=n.cells.reduce(function(a,c){return a+(c.ops||0);},0);if(s>0)return s;}var cap=id.toUpperCase().replace(/_/g,""),hits=0;OPLOG.forEach(function(o){if(o.cell&&o.cell.toUpperCase().replace(/[(),_]/g,"").indexOf(cap)>=0)hits++;});return hits;}',
'function openDossier(id){if(idx[id]==null)return;dossierJive();dossierId=id;dossierRendered={};DTITLE.textContent=fname(id);DPILL.textContent=id;DOSS.classList.add("open");',
' DTABS.innerHTML="";["Data","Rules","Columns","History"].forEach(function(name,i){var b=document.createElement("button");b.className="dtab"+(name===dossierTab?" active":"");b.textContent=name;b.addEventListener("click",function(){selectTab(name,b);});DTABS.appendChild(b);});',
' var _ti=["Data","Rules","Columns","History"].indexOf(dossierTab);if(_ti<0)_ti=0;selectTab(dossierTab,DTABS.children[_ti]);}',
'function selectTab(name,btn){dossierTab=name;Array.prototype.forEach.call(DTABS.children,function(b){b.classList.toggle("active",b===btn);});DBODY.innerHTML="";renderTab(name);}',
'function renderTab(name){var id=dossierId,n=N[idx[id]],doss=(n&&n.dossier)||{columns:[],ruleCount:0,rules:[]};',
' if(name==="Data")return renderDataTab(id);',
' if(name==="Rules")return renderRulesTab(doss);',
' if(name==="Columns")return renderColumnsTab(doss);',
' if(name==="History")return renderHistoryTab(id);}',
'function renderDataTab(id){DBODY.innerHTML=\'<div class=dlead>the actual records for this document \\u2014 the GardenWorld instance layer.</div>\';',
' if(BUNDLE_T.indexOf(id)<0){DBODY.innerHTML+=\'<div class=none>records not carried in this dataset</div>\';return;}',
' var load=document.createElement("div");load.className="none";load.textContent="loading\\u2026";DBODY.appendChild(load);',
' withBundle(function(db){if(dossierId!==id)return;var cols=recCols(db,id);if(!cols){DBODY.innerHTML=\'<div class=none>records not carried in this dataset</div>\';return;}var sel=cols.map(function(o){return o.c;}).join(", "),res;try{res=db.exec("SELECT "+sel+" FROM "+id+" LIMIT 20");}catch(e){DBODY.innerHTML=\'<div class=none>records not carried in this dataset</div>\';return;}',
'  if(!res.length||!res[0].values.length){DBODY.innerHTML=\'<div class=none>no rows</div>\';return;}var cs=res[0].columns,h=\'<table class=dt data-rows="\'+res[0].values.length+\'"><tr>\';cs.forEach(function(c){h+="<th>"+esc(c)+"</th>";});h+="</tr>";res[0].values.forEach(function(v){h+="<tr>";cols.forEach(function(o,i){var raw=v[i],val=raw==null?"\\u2014":(o.cls==="amt"&&!isNaN(Number(raw))?Number(raw).toFixed(2):String(raw));h+=\'<td title="\'+esc(val)+\'">\'+esc(val)+"</td>";});h+="</tr>";});h+="</table>";DBODY.innerHTML=\'<div class=dlead>the actual records for this document.</div>\'+h;});}',
'function renderRulesTab(doss){if(!doss.ruleCount){DBODY.innerHTML=\'<div class=none>no validation rules recorded for this table</div>\';return;}',
' var h=\'<div class=dlead>the checks the system runs before saving (\'+doss.ruleCount+\' validation rule\'+(doss.ruleCount==1?"":"s")+\') \\u2014 read-only.</div>\';',
' (doss.rules||[]).forEach(function(rr){h+=\'<div class=rule><div class=rb>\'+esc(rr.binding)+\'</div><code>\'+esc(rr.body)+\'</code></div>\';});DBODY.innerHTML=h;}',
'function renderColumnsTab(doss){var cols=doss.columns||[];if(!cols.length){DBODY.innerHTML=\'<div class=none>no columns recorded for this table</div>\';return;}',
' var h=\'<div class=dlead>the fields on this document (\'+cols.length+\' shown) \\u2014 from the data dictionary.</div>\';',
' cols.forEach(function(c){h+=\'<div class=col><span class=cn>\'+esc(c.name)+\'</span> <span class=ct>\'+esc(c.type)+\'</span>\'+(c.mandatory?\' <span class=cm>required</span>\':"")+(c.fk?\' <span class=cfk>\\u2192 \'+esc(fname(c.fk))+\'</span>\':"")+\'</div>\';});DBODY.innerHTML=h;}',
'function renderHistoryTab(id){var n=opCountFor(id);',
' var h=\'<div class=dlead>what the engine did \\u2014 the real op-log (most recent first). \\u21b6 previews a reversal (read-only, not enabled).</div>\';',
' if(!OPLOG.length){DBODY.innerHTML=h+\'<div class=none>no engine activity recorded</div>\';return;}',
' h+=\'<div class=ophist>\';OPLOG.forEach(function(o,i){var rev=i<3;h+=\'<div class=oprow data-i="\'+i+\'"><span class=opt>\'+esc(o.op)+\'</span><span class=opc>\'+esc(o.cell)+\'</span><span class=opo>\'+esc(o.out)+\'</span>\'+(rev?\'<span class=oprev title="preview reversal">\\u21b6</span>\':"")+\'<span class=opnote></span></div>\';});h+="</div>";DBODY.innerHTML=h;',
' Array.prototype.forEach.call(DBODY.querySelectorAll(".oprow"),function(row){var rev=row.querySelector(".oprev");if(!rev)return;rev.addEventListener("click",function(){',
'  if(row.classList.contains("reversed")){row.classList.remove("reversed");rev.textContent="\\u21b6";rev.title="preview reversal";row.querySelector(".opnote").textContent="";}',
'  else{row.classList.add("reversed");rev.textContent="\\u21b7";rev.title="undo preview";row.querySelector(".opnote").textContent="would reverse "+n+" tracked op"+(n==1?"":"s")+" \\u2014 read-only, not enabled";}',
' });});}',
'document.getElementById("dossierX").addEventListener("click",function(){DOSS.classList.remove("open");dossierId=null;});',
'// drag the dossier by its header (simple left/top move). No layout dependency \\u2014 it floats over the stage.',
'(function(){var dh=DOSS.querySelector(".dh"),dr=null;dh.addEventListener("pointerdown",function(ev){if(ev.target.classList.contains("dx"))return;ev.preventDefault();var rc=DOSS.getBoundingClientRect();dr={x:ev.clientX,y:ev.clientY,l:rc.left,t:rc.top};try{dh.setPointerCapture(ev.pointerId);}catch(e){}});',
' dh.addEventListener("pointermove",function(ev){if(!dr)return;DOSS.style.left=Math.max(0,dr.l+(ev.clientX-dr.x))+"px";DOSS.style.top=Math.max(0,dr.t+(ev.clientY-dr.y))+"px";});',
' function de(){dr=null;}dh.addEventListener("pointerup",de);dh.addEventListener("pointercancel",de);})();',
'window.openDossier=openDossier;',
'// open the dossier on a NAMED tab (e.g. History) \\u2014 used by the action blurb\\u2019s \\u27f2 History button.',
'function openDossierTab(id,tabName){openDossier(id);if(!tabName)return;var btn=null;Array.prototype.forEach.call(DTABS.children,function(b){if(b.textContent===tabName)btn=b;});if(btn)selectTab(tabName,btn);}',
'window.openDossierTab=openDossierTab;',
'// ── Task 2 (GLASSBOWL_DOSSIER \\u00a72bcd, W-ACTIONS): the DOUBLE-TAP ACTION BLURB \\u2014 the friendly per-bubble',
'//    chooser. Double-click a bubble \\u2192 a small anchored blurb of REAL actions: \\u25b8 Trace (lifecycle bubbles',
'//    only) \\u00b7 \\u25a6 View data (Task 1) \\u00b7 \\u27f2 History / \\u229e Full dossier (Task 3). Plus a CRUD teaser (\\uff0b/\\u270e/\\ud83d\\uddd1)',
'//    rendered GREYED + inert (no handler, no write) \\u2014 the read-only T3 seam. Single-click is UNCHANGED. ──',
'var BLURB=document.getElementById("blurb"),blurbId=null;',
'// lifecycle bubbles = the chain tables (LIN.litNodes for order 101) \\u2014 only these offer Trace. EXTRACT, not hardcode.',
'var LIFE_T=(LIN&&LIN.litNodes)?LIN.litNodes.slice():[];',
'function isLifecycle(id){return LIFE_T.indexOf(id)>=0;}',
'function blurbAct(label,fn){var b=document.createElement("button");b.className="act";b.textContent=label;b.addEventListener("click",function(ev){ev.stopPropagation();fn();});return b;}',
'function openBlurb(id){if(idx[id]==null)return;pickJive(N[idx[id]]);blurbId=id;BLURB.innerHTML="";',
' var hd=document.createElement("div");hd.className="bt";hd.innerHTML=\'<span class=bn>\'+fname(id)+\'</span><span class=bp>\'+id+\'</span><span class=bx2 title=close>\\u2715</span>\';BLURB.appendChild(hd);',
' hd.querySelector(".bx2").addEventListener("click",function(ev){ev.stopPropagation();hideBlurb();});',
' if(isLifecycle(id))BLURB.appendChild(blurbAct("\\u25b8 Trace this flow",function(){window.setTrace(true);}));',
' BLURB.appendChild(blurbAct("\\u25a6 View data",function(){pick(id);var bar=barFor(id);if(bar)bar.scrollIntoView({block:"nearest"});}));',
' BLURB.appendChild(blurbAct("\\u27f2 History",function(){openDossierTab(id,"History");}));',
' BLURB.appendChild(blurbAct("\\u229e Full dossier",function(){openDossier(id);}));',
' var ck=document.createElement("div");ck.className="crudk";ck.textContent="editing \\u2014 coming later (T3)";BLURB.appendChild(ck);',
' ["\\uff0b New","\\u270e Edit","\\ud83d\\uddd1 Delete"].forEach(function(lbl){var c=document.createElement("button");c.className="act crud disabled";c.textContent=lbl;c.title="editing \\u2014 coming later (T3)";BLURB.appendChild(c);});',
' BLURB.classList.add("open");positionBlurb();}',
'function hideBlurb(){if(blurbId){blurbId=null;BLURB.classList.remove("open");}}',
'// follow the bubble: place the blurb at its PROJECTED screen point, transformed by the viewport (px,py,k) \\u2014',
'// exactly the transform the circles ride (translate(px,py) scale(k)). Called from draw() after project().',
'function positionBlurb(){if(!blurbId){return;}var n=N[idx[blurbId]];if(!n){return;}project(n);var sc=Math.max(0.6,Math.min(1.5,1+(n.depth/ZMAX)*0.45*orbitAmt())),r=radius(n)*sc;BLURB.style.left=(px+n.sx*k)+"px";BLURB.style.top=(py+(n.sy+r)*k)+"px";}',
'window.openBlurb=openBlurb;window.hideBlurb=hideBlurb;',
'// ── Task 4 (GLASSBOWL_DOSSIER \\u00a72bcd, W-SWIPE-PICKER): the swipe/scroll record list at the trackball.',
'//    The typed #recsearch stays (desktop fallback); THIS is the mobile-first, no-typing PRIMARY affordance \\u2014 a',
'//    scrollable list of REAL bundle orders (documentno + grandtotal). Flick up/down, TAP a row to trace that',
'//    record (applyChain(walkBundle(GDB,oid))). Rows EXTRACTED from the bundle via populateRecs \\u2014 0 invented. ──',
'function buildRecPick(rows){var rp=document.getElementById("recpick");if(!rp||rp.childElementCount||!rows||!rows.length)return;',
' rp.innerHTML=\'<div class=rph>tap a record \\u00b7 swipe to scroll</div>\';',
' rows.forEach(function(r){var row=document.createElement("div");row.className="rpr";row.setAttribute("data-oid",r.oid);',
'  var amt=(r.amt!=null&&!isNaN(Number(r.amt)))?\'<span class=rpa>\'+Number(r.amt).toFixed(2)+\'</span>\':"";',
'  row.innerHTML=\'<b>#\'+r.dn+\'</b>\'+amt;',
'  row.addEventListener("click",function(){if(!GDB)return;Array.prototype.forEach.call(rp.querySelectorAll(".rpr"),function(x){x.classList.remove("sel");});row.classList.add("sel");if(!traceMode)setTrace(true);applyChain(walkBundle(GDB,r.oid));traceJive();});',
'  rp.appendChild(row);});',
' console.log("\\u00a7SWIPE-PICKER rows="+rows.length+" seed="+(rows.some(function(r){return String(r.dn)==="80001";})?"80001":"?")+" scrollable="+(rp.scrollHeight>rp.clientHeight?"Y":"maybe"));}',
'window.buildRecPick=buildRecPick;',
'// ── Task 5 (GLASSBOWL_DOSSIER \\u00a72bcd, W-AUDIO): WebAudio \\u201cear candy\\u201d \\u2014 synthesized, ZERO assets,',
'//    file://-safe. Subtle UI sounds; AudioContext unlocked on first gesture (autoplay policy); a persisted mute',
'//    toggle. Pitch is mapped to DATA where cheap (pick tone by spine colour, trace arpeggio rises ONE note per real',
'//    hop) \\u2014 ear candy that is *true*, mirroring the eye-candy bet. Never blocks interaction. ──',
'var AUDIO={ctx:null,muted:false,n:0};window.__audio=AUDIO;',
'function audioUnlock(){try{if(!AUDIO.ctx){var AC=window.AudioContext||window.webkitAudioContext;if(!AC)return;AUDIO.ctx=new AC();window.__audioCtx=AUDIO.ctx;console.log("\\u00a7AUDIO unlocked state="+AUDIO.ctx.state+" muted="+AUDIO.muted);}if(AUDIO.ctx.state==="suspended")AUDIO.ctx.resume();}catch(e){}}',
'function beep(freq,dur,type,vol){if(AUDIO.muted||!AUDIO.ctx)return;try{var t=AUDIO.ctx.currentTime,o=AUDIO.ctx.createOscillator(),g=AUDIO.ctx.createGain();o.type=type||"sine";o.frequency.setValueAtTime(freq,t);g.gain.setValueAtTime(0.0001,t);g.gain.linearRampToValueAtTime(vol||0.05,t+0.012);g.gain.exponentialRampToValueAtTime(0.0006,t+(dur||0.12));o.connect(g);g.connect(AUDIO.ctx.destination);o.start(t);o.stop(t+(dur||0.12)+0.02);AUDIO.n++;window.__audioScheduled=AUDIO.n;}catch(e){}}',
'function sweep(f0,f1,dur,vol){if(AUDIO.muted||!AUDIO.ctx)return;try{var t=AUDIO.ctx.currentTime,o=AUDIO.ctx.createOscillator(),g=AUDIO.ctx.createGain();o.type="sawtooth";o.frequency.setValueAtTime(f0,t);o.frequency.exponentialRampToValueAtTime(Math.max(1,f1),t+dur);g.gain.setValueAtTime(0.0001,t);g.gain.linearRampToValueAtTime(vol||0.03,t+0.02);g.gain.exponentialRampToValueAtTime(0.0006,t+dur);o.connect(g);g.connect(AUDIO.ctx.destination);o.start(t);o.stop(t+dur+0.02);AUDIO.n++;window.__audioScheduled=AUDIO.n;}catch(e){}}',
'// pick tone keyed to the bubble\\u2019s spine colour (settlement tenser/higher, reference lower) \\u2014 sound from data.',
'function pickJive(n){if(!n)return beep(660,0.07,"sine",0.04);var f=n.settlement?740:(n.kind==="master"?392:(n.gravity>0?587:494));beep(f,0.07,"sine",0.04);}',
'function barJive(){sweep(220,520,0.18,0.03);}',
'function dismissJive(){beep(247,0.1,"sine",0.04);}',
'function dossierJive(){beep(880,0.05,"square",0.03);}',
'function thunkJive(){beep(120,0.16,"sine",0.05);}',
'// trace = a rising arpeggio, ONE note per real (non-empty) hop in the chain (pitch rises per hop) \\u2014 chain length IS the data.',
'function traceJive(){if(AUDIO.muted||!AUDIO.ctx)return;var ch=curChain||[];var lit=ch.filter(function(c){return c.id!=null;}).length||5;for(var i=0;i<lit;i++){(function(k){setTimeout(function(){beep(330*Math.pow(1.155,k),0.15,"triangle",0.04);},k*95);})(i);}}',
'["pointerdown","keydown","touchstart"].forEach(function(ev){window.addEventListener(ev,audioUnlock);});',
'// the mute toggle (\\ud83d\\udd0a / \\ud83d\\udd07), persisted in the scene localStorage \\u2014 respect the user, quiet + subtle.',
'var muteBtn=document.getElementById("muteBtn");',
'function applyMuteLabel(){if(muteBtn){muteBtn.textContent=AUDIO.muted?"\\ud83d\\udd07":"\\ud83d\\udd0a";muteBtn.classList.toggle("active",AUDIO.muted);}}',
'function toggleMute(){AUDIO.muted=!AUDIO.muted;applyMuteLabel();if(!AUDIO.muted){audioUnlock();beep(523,0.06,"sine",0.04);}save();}',
'if(muteBtn)muteBtn.addEventListener("click",function(ev){ev.stopPropagation();toggleMute();});',
'applyMuteLabel();window.toggleMute=toggleMute;',
'// ── Task 6 (GLASSBOWL_DOSSIER \\u00a72bcd, W-QR-INPUT): scan a QR carrying a record id/documentno \\u2192 trace/open',
'//    that record. Native BarcodeDetector + getUserMedia, ZERO external libs; honest \\u201cnot supported\\u201d fallback;',
'//    the stream is stopped when the panel closes. READ-ONLY slice (scan\\u2192trace); scan-to-FILL-an-edit-value is T3. ──',
'var QR={stream:null,detector:null,raf:0,scanning:false};window.__qr=QR;',
'function qrSupported(){return ("BarcodeDetector" in window)&&!!(navigator.mediaDevices&&navigator.mediaDevices.getUserMedia);}',
'// the lookup (pure-read): a QR payload = an order documentno OR a numeric order id \\u2192 walk + trace it. EXTRACT.',
'function qrLookup(payload){if(payload==null)return false;var s=String(payload).trim();if(!s||!GDB)return false;var oid=recMap[s];if(oid==null&&/^\\d+$/.test(s)){var probe=walkBundle(GDB,s);if(probe[0]&&probe[0].id!=null)oid=s;}if(oid==null)return false;if(!traceMode)setTrace(true);applyChain(walkBundle(GDB,oid));traceJive();return true;}',
'window.qrLookup=qrLookup;',
'var QSTAT=document.getElementById("qrstatus"),QCAM=document.getElementById("qrcam"),QVID=document.getElementById("qrvid");',
'function qrStatus(html){if(QSTAT)QSTAT.innerHTML=html;}',
'function qrClose(){QR.scanning=false;if(QR.raf){cancelAnimationFrame(QR.raf);QR.raf=0;}try{if(QR.stream){QR.stream.getTracks().forEach(function(t){t.stop();});}}catch(e){}QR.stream=null;if(QVID)QVID.srcObject=null;if(QCAM){QCAM.classList.remove("open");QCAM.classList.remove("live");}}',
'window.qrClose=qrClose;',
'function qrTick(){if(!QR.scanning||!QR.detector||!QVID)return;QR.detector.detect(QVID).then(function(codes){if(codes&&codes.length){var raw=codes[0].rawValue||"";if(qrLookup(raw)){qrStatus(\'<span class=qok>\\u2713 found #\'+esc(raw)+\' \\u2014 tracing\\u2026</span>\');dossierJive();setTimeout(qrClose,650);return;}else qrStatus(\'<span class=qbad>scanned \\u201c\'+esc(raw)+\'\\u201d \\u2014 no matching record in this dataset</span>\');}if(QR.scanning)QR.raf=requestAnimationFrame(qrTick);}).catch(function(){if(QR.scanning)QR.raf=requestAnimationFrame(qrTick);});}',
'function qrOpen(){if(!QCAM)return;QCAM.classList.add("open");QCAM.classList.remove("live");',
' if(!qrSupported()){qrStatus(\'<span class=qbad>QR scan not supported on this browser.</span><br><span class=pill>use the swipe list or the search box instead \\u2014 same record, no camera needed.</span>\');console.log("\\u00a7QR-INPUT open supported=N (honest fallback shown)");return;}',
' qrStatus("requesting the camera\\u2026");try{QR.detector=new window.BarcodeDetector({formats:["qr_code"]});}catch(e){QR.detector=null;}',
' navigator.mediaDevices.getUserMedia({video:{facingMode:"environment"}}).then(function(stream){QR.stream=stream;QVID.srcObject=stream;QVID.play&&QVID.play();QCAM.classList.add("live");qrStatus("point the camera at a record\\u2019s QR\\u2026");QR.scanning=true;console.log("\\u00a7QR-INPUT open supported=Y scanning=Y");QR.raf=requestAnimationFrame(qrTick);}).catch(function(err){qrStatus(\'<span class=qbad>camera unavailable \\u2014 \'+esc(err&&err.name||"denied")+\'.</span><br><span class=pill>permission needed; the swipe list works without it.</span>\');console.log("\\u00a7QR-INPUT open supported=Y camera=denied");});}',
'(function(){var qb=document.getElementById("qrbtn"),qx=document.getElementById("qrx");if(qb)qb.addEventListener("click",function(ev){ev.stopPropagation();audioUnlock();dossierJive();qrOpen();});if(qx)qx.addEventListener("click",function(ev){ev.stopPropagation();qrClose();});})();',
'window.qrOpen=qrOpen;',
'console.log("\\u00a7QR-INPUT supported="+(qrSupported()?"Y":"N"));',
'// ── SCENE PERSISTENCE: the screen survives a hard refresh, so you continue exactly where you left',
'//    off \\u2014 no re-login, no hunting \\u201crecent changes\\u201d. Saved on unload, restored on load (localStorage). ──',
'var pendingSeed=null,SKEY="glassbowl.scene";',
'function save(){try{var bars=[];Array.prototype.forEach.call(STACK.children,function(b){bars.push({id:b.getAttribute("data-id"),col:b.classList.contains("col")});});localStorage.setItem(SKEY,JSON.stringify({yaw:yaw,pitch:pitch,px:px,py:py,k:k,pw:(window.__pw||null),col:document.getElementById("wrap").classList.contains("collapsed"),focus:focusId,trace:traceMode,seed:(traceMode&&curChain[0]?curChain[0].id:null),mute:AUDIO.muted,pos:N.map(function(n){return [Math.round(n.x),Math.round(n.y)];}),bars:bars}));}catch(e){}}',
'function loadState(){try{var s=JSON.parse(localStorage.getItem(SKEY)||"null");if(!s)return;if(s.pos&&s.pos.length===N.length)N.forEach(function(n,i){n.x=s.pos[i][0];n.y=s.pos[i][1];});if(s.pw){document.documentElement.style.setProperty("--pw",s.pw+"px");window.__pw=s.pw;}if(s.col){document.getElementById("wrap").classList.add("collapsed");var pt=document.getElementById("panelToggle");if(pt)pt.textContent="\\u27e8";}if(s.bars&&s.bars.length){for(var i=s.bars.length-1;i>=0;i--){if(idx[s.bars[i].id]!=null)pick(s.bars[i].id);}Array.prototype.forEach.call(STACK.children,function(b){var f=null;s.bars.forEach(function(x){if(x.id===b.getAttribute("data-id"))f=x;});if(f&&f.col)b.classList.add("col");else b.classList.remove("col");});}yaw=s.yaw||0;pitch=s.pitch||0;px=s.px||0;py=s.py||0;k=s.k||1;window.__yaw=yaw;window.__pitch=pitch;focusId=s.focus||null;if(s.mute){AUDIO.muted=true;applyMuteLabel();}window.__restored=true;updateBall();if(s.trace){pendingSeed=(s.seed!=null&&LIN&&String(s.seed)!==String(LIN.seed))?s.seed:null;setTrace(true);}}catch(e){}}',
'window.addEventListener("pagehide",save);window.addEventListener("beforeunload",save);',
'loadState();applyT();draw();',
'// mobile-first: on a fresh load (no restored scene) start with the panel COLLAPSED \\u2014 the graph gets the small',
'//   screen and the yellow #phandle invites a tap to slide it open. A restored scene keeps the user\\u2019s last state.',
'if(!window.__restored&&isMobile())setPanelCollapsed(true);else syncPanelUI(document.getElementById("wrap").classList.contains("collapsed"));'
].join('\n');
