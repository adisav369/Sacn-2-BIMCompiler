#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
'use strict';
/**
 * gen_ad.js — AD-Gen from Dictionary (the installer STRUCTURE core).
 *   Implementing docs/AD_GEN_FROM_DICTIONARY_SPEC.md §3 — Witness: §AD-GEN
 *
 * Fold a foreign ERP adapter's SCHEMA_MAP (the cross-ERP data dictionary) → generate the iDempiere
 * Application Dictionary rows (ad_table · ad_column · ad_window · ad_tab · ad_field · ad_menu ·
 * ad_treenodemm + the minimal ad_reference/ad_ref_list type set) → load into an ad_seed-shaped SQLite
 * so the EXISTING renderer draws the foreign ERP's tables as navigable screens with ZERO renderer change.
 *
 * NON-INVENT (spec §4): every AD row traces to a dictionary entry. No screen is hand-built. Un-typed
 * columns (key_fields-only entries the expander defaults to String) are COUNTED (untyped=K), never hidden.
 * DETERMINISTIC (spec §0.21/§5): IDs are minted from a fixed per-ERP base + sequence; created/updated use
 * a FIXED timestamp — NO Date.now / Math.random — so re-running is byte-stable (rerunA==rerunB).
 *
 * Output (spec §4): SEPARATE seeds (deploy/dev/<erp>_ad_seed.db), never overwriting ad_seed.db; live untouched.
 * Run:  node scripts/gen_ad.js 2>&1 | tee build/erp/ad_gen.log
 */

const fs = require('fs');
const path = require('path');
const Database = require('better-sqlite3');
const { ErrorReport } = require('./error_report.js');

const ROOT = path.resolve(__dirname, '..');
const FIXED_TS = '2026-06-03 00:00:00';   // deterministic audit stamp — spec §5 byte-stable
const SYS = 0;                            // createdby/updatedby = System

// Standard iDempiere AD_Reference IDs (observed in build/erp/ad_seed_demo.db) — generated columns bind to
// THESE so a real renderer resolves the display type. ref name → AD_Reference_ID. (spec §3 "seed ONCE")
const REF = { String: 10, Integer: 11, Amount: 12, Date: 15, List: 17, TableDir: 19, YesNo: 20, Quantity: 29 };
const REF_ROWS = [
  [10, 'String', 'D'], [11, 'Integer', 'D'], [12, 'Amount', 'D'], [15, 'Date', 'D'],
  [17, 'List', 'L'], [19, 'Table Direct', 'T'], [20, 'Yes-No', 'L'], [29, 'Quantity', 'D'],
]; // [id, name, validationType]

// ── the expander (spec §1 back-compat): key_fields[] → typed columns[], untyped flagged ────────────────
function expandColumns(entry) {
  if (Array.isArray(entry.columns) && entry.columns.length) {
    return entry.columns.map(c => ({
      name: c.name, ref: REF[c.ref] ? c.ref : 'String', len: c.len || 0,
      key: !!c.key, identifier: !!c.identifier, mandatory: !!c.mandatory || !!c.key,
      label: c.label || c.name, untyped: !!c.untyped, inferred: !!c.inferred,
    }));
  }
  const kf = entry.key_fields || [];
  return kf.map((name, i) => ({                 // no declared type → String default + untyped debt
    name, ref: 'String', len: 60, key: (i === 0), identifier: (i === 0),
    mandatory: (i === 0), label: name, untyped: true, inferred: false,
  }));
}

/**
 * genAD — PURE: (erpSpec) → { rows, counts }. No DB writes inside (spec §3: "handler returns ops").
 * erpSpec = { erp, base, groups:[{ label, map }] }
 *   base   = fixed high ID offset for this ERP (e.g. SAP 9_100_000) — IDs never collide across ERPs.
 */
// ── line→header FK linking (spec §2/§10c) — a LINE table nests as TabLevel 1 under its HEADER's window ──
function isLine(name, entry) {
  const n = String(name).toLowerCase(), dt = String(entry.doc_type || '').toLowerCase();
  return entry.bridge === 'document_lines' || /line$|item$|_lines$|_line$|booking|supplement/.test(n) || /line$/.test(dt);
}
function linkHeaders(map) {
  const norm = s => s.toLowerCase().replace(/^\/dmo\//, '').replace(/[^a-z0-9]/g, '');
  const tables = Object.keys(map).filter(t => !(map[t].skip || (map[t].bridge && /^\(/.test(map[t].bridge))));
  const keyCol = {};
  for (const t of tables) { const k = expandColumns(map[t]).find(c => c.key); keyCol[t] = k ? k.name.toLowerCase() : null; }
  for (const t of tables) {
    const entry = map[t];
    delete entry._parent; delete entry._link;     // idempotent — recompute clean each call (rerun-stable)
    if (!isLine(t, entry)) continue;
    const cols = expandColumns(entry);
    const root = norm(t).replace(/(line|item|lines|supplement|booking)s?$/, '');   // c_orderline → c_order
    let parent = null, link = null;
    // priority 1: columns that equal a HEADER's key (the FK up to the header) — composite line keys allowed.
    // Gather ALL candidates, then prefer the one sharing the longest name-prefix (disambiguates SAP's reused
    // VBELN doc-number: VBAP→VBAK, LIPS→LIKP, VBRP→VBRK by prefix, not first-match).
    const cands = [];
    for (const c of cols) {
      const cn = c.name.toLowerCase();
      for (const H of tables) {
        if (H === t || isLine(H, map[H])) continue;
        if ((keyCol[H] && cn === keyCol[H]) || cn === norm(H) + '_id' || cn === norm(H).replace(/s$/, '') + '_id') cands.push({ H, link: c.name });
      }
    }
    if (cands.length) {
      const pfx = (a, b) => { let i = 0; while (i < a.length && i < b.length && a[i] === b[i]) i++; return i; };
      cands.sort((x, y) => pfx(norm(t), norm(y.H)) - pfx(norm(t), norm(x.H)));
      parent = cands[0].H; link = cands[0].link;
    }
    // priority 2: name-root match (c_orderline shares root with c_order)
    if (!parent) for (const H of tables) {
      if (H === t || isLine(H, map[H])) continue;
      if (norm(H) === root || norm(H).replace(/s$/, '') === root) { parent = H; link = keyCol[H] || null; break; }
    }
    if (parent && parent !== t) { entry._parent = parent; entry._link = link; }
  }
}

function genAD(erpSpec) {
  const { erp, base, groups } = erpSpec;
  const rows = {
    ad_reference: REF_ROWS.map(r => r), ad_ref_list: [],
    ad_table: [], ad_column: [], ad_window: [], ad_tab: [], ad_field: [], ad_menu: [], ad_treenodemm: [],
  };
  const counts = { tables: 0, columns: 0, windows: 0, tabs: 0, fields: 0, menu: 0, untyped: 0, inferred: 0, skipped: 0, nested: 0 };
  const TREE = base + 1, ROOT_MENU = base + 60000;
  rows.ad_menu.push([ROOT_MENU, erp, 'Generated from ' + erp + ' dictionary', 'Y', null, null]);
  rows.ad_treenodemm.push([TREE, ROOT_MENU, 0, 0]); counts.menu++;

  let tableSeq = 0, winSeq = 0, tabSeq = 0, leafSeq = 0, ci = 0, gi = 0, treeSeq = 10;
  const cleanOf = n => n.replace(/^\/DMO\//, '').replace(/[^A-Za-z0-9_]/g, '_');
  const labelOf = (n, e) => e.note ? (e.note[0].toUpperCase() + e.note.slice(1)) : cleanOf(n);
  const emitTable = (name, entry, WINDOW) => {
    const TABLE = base + 100000 + (tableSeq++);
    rows.ad_table.push([TABLE, labelOf(name, entry), entry.doc_type || '', cleanOf(name), WINDOW]);
    counts.tables++; return TABLE;
  };
  const emitColsFields = (entry, TABLE, TAB) => {
    expandColumns(entry).forEach((c, i) => {
      const COL = base + 500000 + ci, FIELD = base + 600000 + ci; ci++;
      rows.ad_column.push([COL, TABLE, c.name, c.label, REF[c.ref], c.len, c.mandatory ? 'Y' : 'N', c.key ? 'Y' : 'N', c.identifier ? 'Y' : 'N']);
      rows.ad_field.push([FIELD, TAB, COL, c.label, i * 10 + 10, 'Y', c.mandatory ? 'Y' : 'N']);
      counts.columns++; counts.fields++;
      if (c.untyped) counts.untyped++;
      if (c.inferred) counts.inferred++;
    });
  };

  for (const group of groups) {
    linkHeaders(group.map);
    const GROUP_MENU = base + 61000 + gi;
    rows.ad_menu.push([GROUP_MENU, group.label, 'Module group', 'Y', null, null]);
    rows.ad_treenodemm.push([TREE, GROUP_MENU, ROOT_MENU, treeSeq]); treeSeq += 10; counts.menu++;
    let menuLeafSeq = 10;

    const all = Object.entries(group.map);
    const live = all.filter(([, e]) => !(e.skip || (e.bridge && /^\(/.test(e.bridge))));
    counts.skipped += all.length - live.length;
    const liveNames = new Set(live.map(([t]) => t));
    const winByTable = {};   // header table → { WINDOW, tabSeqNo }

    // PASS 1 — headers + standalone tables: own window + tab(level 0) + menu leaf
    for (const [t, entry] of live) {
      if (entry._parent && liveNames.has(entry._parent)) continue;       // a child → pass 2
      const WINDOW = base + 200000 + (winSeq++), TAB = base + 300000 + (tabSeq++), LEAF = base + 400000 + (leafSeq++);
      const TABLE = emitTable(t, entry, WINDOW);
      rows.ad_window.push([WINDOW, labelOf(t, entry), entry.doc_type || '', 'M']); counts.windows++;
      rows.ad_tab.push([TAB, WINDOW, labelOf(t, entry), TABLE, 0, 10]); counts.tabs++;
      rows.ad_menu.push([LEAF, labelOf(t, entry), cleanOf(t), 'N', 'W', WINDOW]);
      rows.ad_treenodemm.push([TREE, LEAF, GROUP_MENU, menuLeafSeq]); menuLeafSeq += 10; counts.menu++;
      emitColsFields(entry, TABLE, TAB);
      winByTable[t] = { WINDOW, tabSeqNo: 20 };
    }
    // PASS 2 — line/child tables: tab(level 1) under the parent's window, NO own window/menu leaf
    for (const [t, entry] of live) {
      if (!(entry._parent && liveNames.has(entry._parent))) continue;
      const pw = winByTable[entry._parent];
      const TAB = base + 300000 + (tabSeq++);
      const TABLE = emitTable(t, entry, pw.WINDOW);
      rows.ad_tab.push([TAB, pw.WINDOW, labelOf(t, entry), TABLE, 1, pw.tabSeqNo]); pw.tabSeqNo += 10; counts.tabs++; counts.nested++;
      emitColsFields(entry, TABLE, TAB);
    }
    gi++;
  }
  return { rows, counts };
}

// ── loader (spec §3: "a thin loader writes the rows" — separated from genAD) ───────────────────────────
function writeSeed(outPath, rows) {
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  if (fs.existsSync(outPath)) fs.unlinkSync(outPath);
  const db = new Database(outPath);
  const AUDIT = '0,0,\'Y\',\'' + FIXED_TS + '\',' + SYS + ',\'' + FIXED_TS + '\',' + SYS; // client,org,isactive,created,createdby,updated,updatedby
  db.exec(`
    CREATE TABLE ad_reference (ad_reference_id INTEGER PRIMARY KEY, ad_client_id INT, ad_org_id INT, isactive TEXT, created TEXT, createdby INT, updated TEXT, updatedby INT, name TEXT, description TEXT, validationtype TEXT);
    CREATE TABLE ad_ref_list (ad_ref_list_id INTEGER PRIMARY KEY, ad_client_id INT, ad_org_id INT, isactive TEXT, created TEXT, createdby INT, updated TEXT, updatedby INT, ad_reference_id INT, value TEXT, name TEXT);
    CREATE TABLE ad_table (ad_table_id INTEGER PRIMARY KEY, ad_client_id INT, ad_org_id INT, isactive TEXT, created TEXT, createdby INT, updated TEXT, updatedby INT, name TEXT, description TEXT, tablename TEXT, ad_window_id INT);
    CREATE TABLE ad_column (ad_column_id INTEGER PRIMARY KEY, ad_client_id INT, ad_org_id INT, isactive TEXT, created TEXT, createdby INT, updated TEXT, updatedby INT, ad_table_id INT, columnname TEXT, name TEXT, ad_reference_id INT, fieldlength INT, ismandatory TEXT, iskey TEXT, isidentifier TEXT);
    CREATE TABLE ad_window (ad_window_id INTEGER PRIMARY KEY, ad_client_id INT, ad_org_id INT, isactive TEXT, created TEXT, createdby INT, updated TEXT, updatedby INT, name TEXT, description TEXT, windowtype TEXT);
    CREATE TABLE ad_tab (ad_tab_id INTEGER PRIMARY KEY, ad_client_id INT, ad_org_id INT, isactive TEXT, created TEXT, createdby INT, updated TEXT, updatedby INT, ad_window_id INT, name TEXT, ad_table_id INT, tablevel INT, seqno INT);
    CREATE TABLE ad_field (ad_field_id INTEGER PRIMARY KEY, ad_client_id INT, ad_org_id INT, isactive TEXT, created TEXT, createdby INT, updated TEXT, updatedby INT, ad_tab_id INT, ad_column_id INT, name TEXT, seqno INT, isdisplayed TEXT, ismandatory TEXT);
    CREATE TABLE ad_menu (ad_menu_id INTEGER PRIMARY KEY, ad_client_id INT, ad_org_id INT, isactive TEXT, created TEXT, createdby INT, updated TEXT, updatedby INT, name TEXT, description TEXT, issummary TEXT, action TEXT, ad_window_id INT);
    CREATE TABLE ad_treenodemm (ad_tree_id INT, node_id INT, parent_id INT, seqno INT, ad_client_id INT, ad_org_id INT, isactive TEXT, created TEXT, createdby INT, updated TEXT, updatedby INT);
  `);
  const ins = {
    ad_reference: db.prepare(`INSERT INTO ad_reference (ad_reference_id,ad_client_id,ad_org_id,isactive,created,createdby,updated,updatedby,name,validationtype) VALUES (?,${AUDIT},?,?)`),
    ad_ref_list: db.prepare(`INSERT INTO ad_ref_list VALUES (?,${AUDIT},?,?,?)`),
    ad_table: db.prepare(`INSERT INTO ad_table VALUES (?,${AUDIT},?,?,?,?)`),
    ad_column: db.prepare(`INSERT INTO ad_column VALUES (?,${AUDIT},?,?,?,?,?,?,?,?)`),
    ad_window: db.prepare(`INSERT INTO ad_window VALUES (?,${AUDIT},?,?,?)`),
    ad_tab: db.prepare(`INSERT INTO ad_tab VALUES (?,${AUDIT},?,?,?,?,?)`),
    ad_field: db.prepare(`INSERT INTO ad_field VALUES (?,${AUDIT},?,?,?,?,?,?)`),
    ad_menu: db.prepare(`INSERT INTO ad_menu VALUES (?,${AUDIT},?,?,?,?,?)`),
    ad_treenodemm: db.prepare(`INSERT INTO ad_treenodemm VALUES (?,?,?,?,0,0,'Y','${FIXED_TS}',${SYS},'${FIXED_TS}',${SYS})`),
  };
  const tx = db.transaction(() => {
    for (const t of Object.keys(ins)) for (const r of rows[t]) ins[t].run(...r);
  });
  tx();
  db.close();
}

// ── type DEDUCTION ────────────────────────────────────────────────────────────────────────────────────
// From a DB the type is DECLARED (deterministic, inferred=false); an unknown declared type → String+untyped.
function inferRefFromSql(sqlType, colName) {
  const t = String(sqlType || '').toLowerCase(), n = String(colName || '').toLowerCase();
  const m = t.match(/\((\d+)/); const len = m ? parseInt(m[1], 10) : 0;
  let ref, known = true;
  if (/int/.test(t)) ref = 'Integer';
  else if (/char|clob|text|string|uuid|varchar/.test(t)) ref = 'String';
  else if (/date|time/.test(t)) ref = 'Date';
  else if (/bool/.test(t)) ref = 'YesNo';
  else if (/real|floa|doub|num|dec|money/.test(t)) ref = /qty|quant|menge|kwmeng|lfimg|fkimg|movement/.test(n) ? 'Quantity' : 'Amount';
  else { ref = 'String'; known = false; }   // unknown declared type → String, flagged untyped (visible)
  return { ref, len, untyped: !known, inferred: false };
}
// From Excel the type is SAMPLED — MAJORITY-vote ("how majority data will fulfil"): the dominant type wins,
// outliers are reported. Heuristic (inferred=true); below threshold → String+untyped. Never silent (§10a).
function inferRefFromSamples(values, colName) {
  const vs = values.filter(v => v != null && String(v).trim() !== '').map(v => String(v).trim());
  const n = String(colName || '').toLowerCase();
  if (!vs.length) return { ref: 'String', len: 60, untyped: true, inferred: true, why: 'all-blank', outliers: 0, n: 0 };
  let nBool = 0, nInt = 0, nNum = 0, nDate = 0;
  for (const v of vs) {
    if (/^(y|n|yes|no|true|false)$/i.test(v)) nBool++;
    else if (/^-?\d+$/.test(v)) { nInt++; nNum++; }
    else if (/^-?\d+(\.\d+)?$/.test(v)) nNum++;
    else if (/[\/\-]/.test(v) && !isNaN(Date.parse(v))) nDate++;
  }
  const N = vs.length, TH = 0.6;                          // ≥60% of the data must fulfil the type
  let ref, untyped = false, fit;
  if (nBool / N >= TH) { ref = 'YesNo'; fit = nBool; }
  else if (nDate / N >= TH) { ref = 'Date'; fit = nDate; }
  else if (nInt / N >= TH) { ref = /qty|quant|count|age|^no$|num/.test(n) ? 'Quantity' : 'Integer'; fit = nInt; }
  else if (nNum / N >= TH) { ref = 'Amount'; fit = nNum; }
  else { ref = 'String'; untyped = true; fit = N; }       // no majority type → String, flagged
  const outliers = N - fit;                               // values that DON'T fit the majority type
  const len = ref === 'String' ? Math.min(Math.max(...vs.map(v => v.length), 10), 255) : 0;
  const why = ref === 'String' ? 'no-majority' : (ref.toLowerCase() + ' ' + fit + '/' + N);
  return { ref, len, untyped, inferred: true, why, outliers, n: N };
}

// ── positive ENTITY identification — id which table is BPartner / Products / Orders (the majority of data) ──
const ENTITY = [
  { doc: 'C_BPartner', rx: /partner|customer|client|vendor|supplier|contact|bpartner|res.?partner|kunnr|debtor|account.?holder/ },
  { doc: 'M_Product',  rx: /product|item|material|article|^sku|matnr|merchandise|goods/ },
  { doc: 'C_Order',    rx: /sales?.?order|purchase.?order|^orders?$|c_order|vbak|sale.?order|salesorder|po.?header|so.?header/ },
  { doc: 'C_Invoice',  rx: /invoice|billing|^bill|c_invoice|vbrk|account.?move|debit.?note/ },
  { doc: 'C_Payment',  rx: /payment|receipt|c_payment|remittance/ },
];
function classifyEntity(name) {
  const n = String(name || '').toLowerCase();
  for (const e of ENTITY) if (e.rx.test(n)) return e.doc;
  return '';
}

// ── PROVIDERS — each emits an erpSpec for genAD, feeding an ErrorReport with detected rubbish ───────────
function providerFromSqlite(dbPath, opts, report) {
  opts = opts || {};
  const db = new Database(dbPath, { readonly: true });
  const tables = db.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").all().map(r => r.name);
  const map = {};
  for (const t of tables) {
    const info = db.prepare(`PRAGMA table_info("${t}")`).all();
    if (!info.length) { report.error('zero-column-table', t, 'table has no columns — skipped'); continue; }
    const fks = db.prepare(`PRAGMA foreign_key_list("${t}")`).all();
    const fkCols = new Set(fks.map(f => String(f.from).toLowerCase()));
    const seen = new Set();
    const columns = info.map(c => {
      const low = String(c.name).toLowerCase();
      if (seen.has(low)) report.rubbish('dup-column', t + '.' + c.name, 'duplicate column name');
      seen.add(low);
      const declaredFk = fkCols.has(low);
      const nameFk = !declaredFk && /_id$/.test(low) && low !== 'ad_client_id' && low !== 'ad_org_id';
      const r = inferRefFromSql(c.type, c.name);
      if (r.untyped) report.warn('undeducible-type', t + '.' + c.name, 'unknown SQL type "' + (c.type || '') + '" → String');
      if (nameFk) report.rubbish('inferred-fk', t + '.' + c.name, 'no declared FK, name ends _id → assumed Table Direct');
      return {
        name: c.name, ref: declaredFk ? 'TableDir' : (nameFk ? 'TableDir' : r.ref), len: r.len,
        key: c.pk > 0, identifier: c.pk === 1, label: c.name, untyped: r.untyped, inferred: nameFk,
      };
    });
    map[t] = { doc_type: '', columns };
  }
  db.close();
  const erp = opts.erp || path.basename(dbPath).replace(/\.[^.]+$/, '');
  return { erp, base: opts.base || 9300000, out: opts.out || ('deploy/dev/' + erp.toLowerCase() + '_ad_seed.db'),
    groups: [{ label: erp + ' — introspected', map }] };
}

function providerFromExcel(xlsxPath, opts, report) {
  opts = opts || {};
  const XLSX = require('xlsx');
  const wb = XLSX.readFile(xlsxPath);
  const map = {};
  for (const sheet of wb.SheetNames) {
    const rows = XLSX.utils.sheet_to_json(wb.Sheets[sheet], { header: 1, blankrows: false });
    if (!rows.length) { report.rubbish('empty-sheet', sheet, 'sheet has no rows — skipped'); continue; }
    const rawHdr = (rows[0] || []).map(h => (h == null ? '' : String(h).trim()));
    const headers = rawHdr.filter(Boolean);
    if (!headers.length) { report.error('no-header', sheet, 'first row is blank — cannot deduce columns, skipped'); continue; }
    if (rawHdr.some(h => !h)) report.rubbish('blank-header', sheet, 'some header cells blank — those columns dropped');
    const body = rows.slice(1);
    if (!body.length) report.rubbish('header-only', sheet, 'header present but zero data rows');
    const seen = new Set();
    const columns = headers.map((h, ci) => {
      const low = h.toLowerCase();
      if (seen.has(low)) report.rubbish('dup-header', sheet + '.' + h, 'duplicate header');
      seen.add(low);
      const samples = body.slice(0, 50).map(r => r[ci]);
      const nonBlank = samples.filter(v => v != null && String(v).trim() !== '').length;
      if (body.length && nonBlank === 0) report.rubbish('all-null', sheet + '.' + h, 'every sampled value blank');
      const inf = inferRefFromSamples(samples, h);
      if (inf.untyped) report.rubbish('mixed-type', sheet + '.' + h, 'no majority type (' + inf.why + ') → String');
      else if (inf.outliers > 0) report.rubbish('outlier-values', sheet + '.' + h, inf.outliers + '/' + inf.n + " don't fit " + inf.ref + ' (majority kept)');
      return { name: h, ref: inf.ref, len: inf.len, key: ci === 0 || /(^|_)id$/i.test(h), identifier: ci === 0, label: h, untyped: inf.untyped, inferred: inf.inferred };
    });
    map[sheet] = { doc_type: '', columns };
  }
  const erp = opts.erp || path.basename(xlsxPath).replace(/\.[^.]+$/, '');
  return { erp, base: opts.base || 9400000, out: opts.out || ('deploy/dev/' + erp.toLowerCase() + '_ad_seed.db'),
    groups: [{ label: path.basename(xlsxPath) + ' — sheets', map }] };
}

// ── positive ROLE classification — id the identifier, the amounts, the key, the entity (spec §10c) ──────
function classifyRoles(spec, report) {
  const ID_PRIORITY = [/^name$/, /name$/, /^documentno$/, /document.?no/, /^value$/, /^code$/, /title/, /description/];
  const AMT_RX = /grandtotal|^total|nettotal|net.?amt|net.?total|amount|^amt$|price|linenetamt|totallines|credit.?limit|fee/;
  const lines = [];
  for (const g of spec.groups) for (const [t, entry] of Object.entries(g.map)) {
    const cols = entry.columns || []; if (!cols.length) continue;
    // ENTITY — id which table is BPartner / Products / Orders (only if the source didn't already declare it)
    if (!entry.doc_type) { const ent = classifyEntity(t); if (ent) entry.doc_type = ent; }
    // IDENTIFIER — positive: the human-readable record label (replaces the weak positional guess)
    let idIdx = -1;
    for (const rx of ID_PRIORITY) { idIdx = cols.findIndex(c => rx.test(c.name.toLowerCase()) && !c.key); if (idIdx >= 0) break; }
    if (idIdx < 0) idIdx = cols.findIndex(c => c.ref === 'String' && !c.key);
    if (idIdx >= 0) { cols.forEach((c, i) => { c.identifier = (i === idIdx); }); cols[idIdx].inferred = true; }
    // AMOUNTS — positive: the headline figures (totals / reporting / the master extractor)
    const amts = cols.filter(c => c.ref === 'Amount' && AMT_RX.test(c.name.toLowerCase())).map(c => c.name);
    // KEY — PK is certain; with no PK, positively pick <table>_id / *_id and flag it (the user should know)
    let keyName = (cols.find(c => c.key) || {}).name || null;
    if (!keyName) {
      const tn = t.toLowerCase().replace(/[^a-z0-9_]/g, '');
      let k = cols.findIndex(c => new RegExp('^' + tn + '_id$').test(c.name.toLowerCase()));
      if (k < 0) k = cols.findIndex(c => /_id$/.test(c.name.toLowerCase()));
      if (k >= 0) { cols[k].key = true; keyName = cols[k].name; report.rubbish('positive-key', t + '.' + keyName, 'no PK — *_id chosen as key'); }
    }
    lines.push(`§AD-GEN-ROLE table=${t} entity=${entry.doc_type || '-'} identifier=${idIdx >= 0 ? cols[idIdx].name : '-'} amounts=[${amts.join(',')}] key=${keyName || '-'}`);
  }
  const CAP = 20;
  lines.slice(0, CAP).forEach(l => console.log(l));
  if (lines.length > CAP) console.log(`§AD-GEN-ROLE (+${lines.length - CAP} more tables — full list in artifact)`);
}

// ── genAndWrite — one (spec → seed + witnesses), shared by adapters and providers ──────────────────────
function genAndWrite(spec, source) {
  const a = genAD(spec), b = genAD(spec);     // determinism check (spec §5): twice, compare canonical JSON
  const det = JSON.stringify(a.rows) === JSON.stringify(b.rows);
  const c = a.counts;
  writeSeed(path.join(ROOT, spec.out), a.rows);
  console.log(`§AD-GEN source=${source} erp=${spec.erp} tables=${c.tables} columns=${c.columns} windows=${c.windows} ` +
    `tabs=${c.tabs} fields=${c.fields} menu=${c.menu} nested=${c.nested} from-dictionary=Y handAuthored=0 inferred=${c.inferred} ` +
    `untyped=${c.untyped} skipped=${c.skipped} → ${spec.out}`);
  console.log(`§AD-GEN erp=${spec.erp} ids deterministic rerunA==rerunB=${det ? 'Y' : 'N'}`);
  const db = new Database(path.join(ROOT, spec.out), { readonly: true });
  const got = {
    table: db.prepare('SELECT COUNT(*) n FROM ad_table').get().n,
    column: db.prepare('SELECT COUNT(*) n FROM ad_column').get().n,
    field: db.prepare('SELECT COUNT(*) n FROM ad_field').get().n,
    menu: db.prepare('SELECT COUNT(*) n FROM ad_menu').get().n,
    refResolved: db.prepare('SELECT COUNT(*) n FROM ad_column WHERE ad_reference_id IN (' + Object.values(REF).join(',') + ')').get().n,
  };
  db.close();
  const ok = got.table === c.tables && got.column === c.columns && got.field === c.fields && got.menu === c.menu;
  console.log(`§AD-GEN erp=${spec.erp} loaded table=${got.table} column=${got.column} field=${got.field} ` +
    `menu=${got.menu} refResolved=${got.refResolved}/${got.column} counts-match=${ok ? 'Y' : 'N'}`);
  return { counts: c, deterministic: det, loadOk: ok };
}

// ── CLI ───────────────────────────────────────────────────────────────────────────────────────────────
function arg(flag) { const i = process.argv.indexOf(flag); return i >= 0 ? process.argv[i + 1] : null; }

function run() {
  const fromDb = arg('--from-db'), fromXlsx = arg('--from-xlsx');
  if (fromDb || fromXlsx) {
    const report = new ErrorReport(fromDb ? 'db:' + fromDb : 'xlsx:' + fromXlsx);
    const opts = { erp: arg('--erp'), base: arg('--base') ? parseInt(arg('--base'), 10) : null, out: arg('--out') };
    const spec = fromDb ? providerFromSqlite(fromDb, opts, report) : providerFromExcel(fromXlsx, opts, report);
    classifyRoles(spec, report);                              // positive id: entity + identifier + amounts + key
    genAndWrite(spec, report.source);
    report.printFindings();                                   // every trapped smell, as a §-line
    report.summarize(path.join(ROOT, 'build/erp/ad_gen_report.json'));
    console.log('§AD-GEN DONE — provider import complete (went through; rubbish reported above).');
    return;
  }
  // default: the bundled foreign adapters (iDempiere already has native AD)
  const sap = require('./sap_adapter.js'), odoo = require('./odoo_adapter.js');
  const ERPS = [
    { erp: 'SAP', base: 9100000, out: 'deploy/dev/sap_ad_seed.db', groups: [
        { label: 'SAP — Core (SD/FI/MM)', map: sap.SCHEMA_MAP },
        { label: 'SAP — Flight (/DMO/)', map: sap.SCHEMA_MAP_FLIGHT } ] },
    { erp: 'Odoo', base: 9200000, out: 'deploy/dev/odoo_ad_seed.db', groups: [
        { label: 'Odoo — O2C/P2P', map: odoo.SCHEMA_MAP } ] },
  ];
  for (const e of ERPS) genAndWrite(e, 'adapter');
  console.log('§AD-GEN DONE — STRUCTURE seeds generated; render proof (§AD-RENDER, T4) is the next step.');
}

if (require.main === module) run();
module.exports = { genAD, expandColumns, REF, inferRefFromSql, inferRefFromSamples, providerFromSqlite, providerFromExcel };
