#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (POS_ENGINE_LANE.md §E-1 / POS_ADDON_SPEC.md §P-9). Read the log after every run.
// poc_pos_register.js — W-POS-REGISTER: the DIY register write-group (scan + snap + keyed price →
//   ONE signed commitGroup of CRUD_CREATE ops: M_Product + m_productprice + AD_Image + c_poskey).
//
// ISSUES IT PROVES (named):
//   1. DEFAULTS ARE EXTRACTED, NEVER INVENTED (§P-9.4) — every M_Product mandatory column comes from
//      AD_Column.defaultvalue where set, else the mode of the STATION LAYOUT's own products. The witness
//      logs the extraction (`§POS-REG defaults src=dictionary …`) and then proves COVERAGE: every
//      dictionary-mandatory column of all four tables is present in the op fields, the only omissions
//      being the audit stamps (Created/CreatedBy/Updated/UpdatedBy = SYSDATE/context — substrate-stamped,
//      the same omission the CRUD rails' cleanVals makes).
//   2. THE TILE RINGS THROUGH THE UNCHANGED §P-2 PATH — apply the group's ops to a writable copy of the
//      seed, then ring the NEW product via the SAME POSCore.ringLine + buildSaleGroup the lens already
//      uses: priceactual == the KEYED price (1.00), the sale group builds, both groups seal, chainOk=Y.
//   3. HONEST REFUSALS — no-barcode · over-cap image (the §P-9.1 ≤32KB op-log bound) · second register of
//      the same barcode = 'barcode-exists' handing back the existing product. NAMED DECISION (card §E-1):
//      real iDempiere does NOT enforce upc uniqueness, but §P-8 scan resolves barcode→ONE product to ring;
//      a duplicate would make scan resolution nondeterministic → PROPOSE-MERGE (refuse + hand back), not
//      allow-dup.
//
// NON-INVENT: station/products/dictionary rows = real ad_seed_fullwidth.db (cross-checked vs
//   origin/main:erp/ad_seed.db 2026-06-12). Barcode/name/price = the operator's keyed inputs (fixtures
//   here). imageThumb = a deterministic fixture payload (headless stand-in for a camera JPEG — named);
//   imageKey = sha256 of those bytes (content address, computed from content, no Date/random).
// Implementing docs/POS_ADDON_SPEC.md §P-9 — Witness: W-POS-REGISTER
// Run: bash build/erp/run_witness.sh scripts/poc_pos_register.js   — then READ the log.
'use strict';
var path = require('path');
var fs = require('fs');
var crypto = require('crypto');
var Database = require('better-sqlite3');
var initSqlJs = require('sql.js');
var POS = require(path.join(__dirname, '..', 'build', 'erp', 'pos_core.js'));

global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var KO = global.window.KernelOps;

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function lc(r) { if (!r) return r; var o = {}; for (var k in r) o[k.toLowerCase()] = r[k]; return o; }

// writable copy of the canonical seed — the apply target (the seed itself is NEVER mutated)
var SEED = path.join(__dirname, '..', 'build', 'erp', 'ad_seed_fullwidth.db');
var COPY = '/tmp/poc_pos_register_seed.db';
fs.copyFileSync(SEED, COPY);
var db = new Database(COPY);

// ── the EXTRACTION (the reference implementation the lens copies — every value from the seed) ──────
function modeOf(rows, col) {
  var c = {}; rows.forEach(function (r) { var v = r[col]; c[v] = (c[v] || 0) + 1; });
  var best = null; Object.keys(c).forEach(function (k) { if (best == null || c[k] > c[best]) best = k; });
  var n = Number(best); return (best !== 'null' && best !== '' && !isNaN(n) && String(n) === best) ? n : (best === 'null' ? null : best);
}
function registerDefaults(db, pos) {
  var tid = function (t) { return lc(db.prepare('SELECT ad_table_id FROM ad_table WHERE LOWER(tablename)=LOWER(?)').get(t)).ad_table_id; };
  var mand = db.prepare("SELECT columnname, defaultvalue FROM ad_column WHERE ad_table_id=? AND ismandatory='Y'").all(tid('M_Product')).map(lc);
  var AUDIT = { created: 1, createdby: 1, updated: 1, updatedby: 1 };
  var KEYED = { m_product_id: 1, name: 1, value: 1 };   // the operator's / allocator's own values
  // the station layout's own products — the §P-9.4 "rows the seed's own POS products use"
  var prows = db.prepare('SELECT * FROM m_product WHERE M_Product_ID IN (SELECT m_product_id FROM c_poskey WHERE c_poskeylayout_id=? AND m_product_id IS NOT NULL)').all(pos.c_poskeylayout_id).map(lc);
  var dict = {}, product = {};
  mand.forEach(function (c) {
    var col = c.columnname.toLowerCase();
    if (AUDIT[col] || KEYED[col]) return;
    var dv = c.defaultvalue;
    if (dv != null && dv !== 'SYSDATE' && dv.indexOf('@') < 0) {            // literal dictionary default (Y/N/I/0)
      dict[col] = (String(Number(dv)) === dv) ? Number(dv) : dv;
    } else {                                                                 // context (@#AD_Client_ID@) or no default
      product[col] = modeOf(prows, col);                                     // → the station products' own convention
    }
  });
  var pricerows = db.prepare('SELECT * FROM m_productprice WHERE M_PriceList_Version_ID=?').all(104).map(lc);
  var imgrows = db.prepare('SELECT * FROM ad_image').all().map(lc);
  var keyrows = db.prepare('SELECT * FROM c_poskey WHERE c_poskeylayout_id=?').all(pos.c_poskeylayout_id).map(lc);
  // entitytype: the dictionary default's non-sys branch = get_sysconfig('DEFAULT_ENTITYTYPE','U') — read the
  // sysconfig row if the seed has one, else the function's own default arg 'U'.
  var et = 'U';
  try { var sr = db.prepare("SELECT value FROM ad_sysconfig WHERE name='DEFAULT_ENTITYTYPE'").get(); if (sr) et = lc(sr).value; } catch (e) { /* no ad_sysconfig table → default arg */ }
  // nextseqno per the dictionary's OWN SeqNo default: MAX(SeqNo)+10 on the layout
  var seq = db.prepare('SELECT COALESCE(MAX(seqno),0)+10 s FROM c_poskey WHERE c_poskeylayout_id=?').get(pos.c_poskeylayout_id).s;
  return {
    dict: dict, product: product,
    price: { ad_client_id: modeOf(pricerows, 'ad_client_id'), ad_org_id: modeOf(pricerows, 'ad_org_id') },
    image: { ad_client_id: modeOf(imgrows, 'ad_client_id'), ad_org_id: modeOf(imgrows, 'ad_org_id'), entitytype: et },
    poskey: { ad_client_id: modeOf(keyrows, 'ad_client_id'), ad_org_id: modeOf(keyrows, 'ad_org_id'), qty: modeOf(keyrows, 'qty'), nextseqno: seq }
  };
}

(async function () {
  console.log('═══ W-POS-REGISTER — §P-9 DIY register: ONE signed group, defaults EXTRACTED, tile rings at the keyed price ═══\n');

  var pos = lc(db.prepare('SELECT * FROM c_pos WHERE c_pos_id=100').get());
  var plv = lc(db.prepare('SELECT m_pricelist_version_id v FROM m_pricelist_version WHERE m_pricelist_id=?').get(pos.m_pricelist_id));

  // the side op-log (sql.js, same as the page's) — registers + the follow-on sale both seal here
  var SQL = await initSqlJs();
  var opDb = new SQL.Database(); KO.ensureTable(opDb);
  function priorCreateCount() {
    var r = opDb.exec("SELECT COUNT(*) FROM kernel_ops WHERE op_type='CRUD_CREATE'");
    return (r[0] && Number(r[0].values[0][0])) || 0;
  }

  var ctx = {
    pos: pos,
    priceListVersionId: plv.v,
    registerDefaults: function () { return registerDefaults(db, pos); },
    productByBarcode: function (upc) { return lc(db.prepare('SELECT m_product_id, name FROM m_product WHERE upc=?').get(upc)) || null; },
    priorCreateCount: priorCreateCount
  };
  var d = ctx.registerDefaults();
  console.log('§POS-REG defaults src=dictionary cat=' + d.product.m_product_category_id + ' uom=' + d.product.c_uom_id +
    ' tax=' + d.product.c_taxcategory_id + ' client=' + d.product.ad_client_id + ' org=' + d.product.ad_org_id +
    ' dictLiterals=' + Object.keys(d.dict).length + ' stationCols=' + Object.keys(d.product).length +
    ' entitytype=' + d.image.entitytype + ' nextseqno=' + d.poskey.nextseqno + ' (MAX+10, the SeqNo dictionary default)');
  verdict(d.product.c_uom_id === 100 && d.product.c_taxcategory_id === 107 && d.dict.isactive === 'Y' && d.dict.producttype === 'I',
    'extraction: uom/tax from station products, IsActive/ProductType from AD_Column.defaultvalue', 'uom=100 tax=107 isactive=Y producttype=I');

  // ── the operator's inputs (keyed/scanned fixtures) + the §P-9.1 capped thumb ──
  var thumbBytes = Buffer.alloc(1200, 7);   // deterministic fixture payload — a headless stand-in for the camera JPEG (named)
  var thumb = 'data:image/jpeg;base64,' + thumbBytes.toString('base64');
  var imageKey = 'sha256:' + crypto.createHash('sha256').update(thumbBytes).digest('hex');
  var spec = { stationId: pos.c_pos_id, barcode: '9555001234567', name: 'Demo Mug', price: '1.00', imageThumb: thumb, imageKey: imageKey };

  var g = POS.buildRegisterGroup(ctx, spec);
  verdict(g.ok === true && g.ops.length === 4 && g.newVerbs.length === 0,
    'register group: 4 CRUD_CREATE ops (M_Product+price+AD_Image+c_poskey), newVerbs=[]',
    'ops=' + (g.ops || []).length + ' productId=' + g.productId + ' poskeyId=' + g.poskeyId);
  verdict(g.productId === 920001 && g.priceId === 920002 && g.imageId === 920003 && g.poskeyId === 920004,
    'deterministic PKs from priorCreateCount (the nextIds pattern, register band 920000)', 'ids=' + [g.productId, g.priceId, g.imageId, g.poskeyId].join(','));

  // ── 1. mandatory COVERAGE: every dictionary-mandatory col present, audit stamps the only omission ──
  var AUDIT = { created: 1, createdby: 1, updated: 1, updatedby: 1 };
  var opByTable = {}; g.ops.forEach(function (o) { opByTable[o.table.toLowerCase()] = o; });
  [['M_Product', 'm_product'], ['M_ProductPrice', 'm_productprice'], ['AD_Image', 'ad_image'], ['C_POSKey', 'c_poskey']].forEach(function (t) {
    var tid = lc(db.prepare('SELECT ad_table_id FROM ad_table WHERE LOWER(tablename)=LOWER(?)').get(t[0])).ad_table_id;
    var mand = db.prepare("SELECT columnname FROM ad_column WHERE ad_table_id=? AND ismandatory='Y'").all(tid).map(function (r) { return lc(r).columnname.toLowerCase(); });
    var fields = opByTable[t[1]].fields;
    var missing = mand.filter(function (c) { return !AUDIT[c] && !(c in fields); });
    verdict(missing.length === 0, t[0] + ': ALL dictionary-mandatory cols covered (audit stamps = the named omission)',
      missing.length ? 'MISSING ' + missing.join(',') : mand.length + ' mandatory, ' + Object.keys(fields).length + ' fields');
  });
  var pf = opByTable.m_product.fields;
  verdict(pf.upc === spec.barcode && pf.value === spec.name && pf.name === spec.name,
    'barcode → upc; Value = the human mnemonic (seed convention: barcode lives in UPC only)', 'upc=' + pf.upc + ' value=' + pf.value);
  var prf = opByTable.m_productprice.fields;
  verdict(prf.pricestd === '1.00' && prf.pricelist === '1.00' && prf.pricelimit === '1.00' && prf.m_pricelist_version_id === plv.v,
    'price row on the STATION\'s pricelist version at the KEYED price (all three price cols mandatory → keyed)',
    'plv=' + prf.m_pricelist_version_id + ' std=' + prf.pricestd);
  var imf = opByTable.ad_image.fields;
  verdict(imf.imageurl === imageKey && imf.binarydata === thumb && POS.dataUrlBytes(imf.binarydata) <= POS.IMAGE_CAP_BYTES,
    'AD_Image: imageurl=content key, binarydata=capped thumbnail (≤' + POS.IMAGE_CAP_BYTES + 'B decoded)',
    'bytes=' + POS.dataUrlBytes(imf.binarydata));
  var kf = opByTable.c_poskey.fields;
  verdict(kf.ad_image_id === g.imageId && kf.c_poskeylayout_id === pos.c_poskeylayout_id && kf.seqno === d.poskey.nextseqno,
    'c_poskey on the station layout, photo wired (ad_image_id), seqno per the dictionary MAX+10 rule',
    'seqno=' + kf.seqno + ' ad_image_id=' + kf.ad_image_id);

  // ── 2. commit = ONE signed group; then the tile RINGS through the UNCHANGED §P-2 path ──
  var res = await KO.commitGroup(opDb, g.ops.map(function (o) { return { op_type: o.op_type, params: o }; }), { baseTs: 1718000000000 });
  var chain = await KO.verifyChain(opDb);
  verdict(res.committed && res.ids.length === 4 && chain.ok, 'commitGroup: 4 ops sealed under ONE gid, chainOk=Y', 'gid=' + res.gid + ' sealed=' + res.sealed);
  console.log('§POS-REG commit gid=' + res.gid + ' ops=' + res.ids.length + ' chainOk=' + (chain.ok ? 'Y' : 'N'));

  // apply the CRUD_CREATE ops to the writable copy (the fold the substrate performs) — audit stamps land
  // at fold time (SYSDATE), exactly the omission named above.
  g.ops.forEach(function (o) {
    var cols = Object.keys(o.fields);
    db.prepare('INSERT INTO ' + o.table + ' (' + cols.join(',') + ') VALUES (' + cols.map(function () { return '?'; }).join(',') + ')')
      .run(cols.map(function (c) { return o.fields[c]; }));
  });
  var tile = lc(db.prepare('SELECT c_poskey_id, name, m_product_id, ad_image_id FROM c_poskey WHERE c_poskeylayout_id=? AND m_product_id=?').get(pos.c_poskeylayout_id, g.productId));
  verdict(!!tile && tile.c_poskey_id === g.poskeyId, 'the new TILE appears on the station layout (c_poskey row folded)', JSON.stringify(tile));

  // ring it — the SAME ctx shape + the SAME ringLine/buildSaleGroup the lens uses (§P-2 UNCHANGED)
  var priceStmt = db.prepare('SELECT pricestd FROM m_productprice WHERE m_pricelist_version_id=? AND m_product_id=?');
  var dt = lc(db.prepare('SELECT docsubtypeso FROM c_doctype WHERE c_doctype_id=?').get(pos.c_doctype_id));
  var saleCtx = {
    pos: pos,
    priceOf: function (pid) { return lc(priceStmt.get(plv.v, pid)) || null; },
    bomOf: function () { return []; },
    wrPolicy: dt.docsubtypeso === 'WR' ? { isautogenerateinout: 'Y', isautogenerateinvoice: 'Y' } : {}
  };
  var line = POS.ringLine(saleCtx, g.productId, 1);
  verdict(line.ok === true && line.priceactual === '1.00' && line.linenetamt === '1.00',
    'the new tile RINGS at the KEYED price through the UNCHANGED ringLine (sealed master row, no invention)',
    'priceactual=' + line.priceactual);
  var sale = POS.buildSaleGroup(saleCtx, [line], { orderId: 930001, inoutId: 930002, invoiceId: 930003, c_bpartner_id: 112 });
  verdict(sale.ok === true && sale.newVerbs.length === 0, 'buildSaleGroup on the registered product — §P-2 path UNCHANGED, newVerbs=[]', 'ops=' + (sale.ops || []).length);
  var saleRes = await KO.commitGroup(opDb, sale.ops.map(function (o) { return { op_type: o.op_type, params: o }; }), { baseTs: 1718000001000 });
  var chain2 = await KO.verifyChain(opDb);
  verdict(saleRes.committed && chain2.ok, 'register THEN sale: both groups sealed on one log, chainOk=Y', 'len=' + chain2.len);
  console.log('§POS-REG ring product=' + g.productId + ' priceactual=' + line.priceactual + ' sale gid=' + saleRes.gid + ' chainOk=' + (chain2.ok ? 'Y' : 'N'));

  // ── 3. HONEST REFUSALS (the card's falsifiers) ──
  var f1 = POS.buildRegisterGroup(ctx, { stationId: 100, barcode: '', name: 'X', price: '1.00' });
  verdict(f1.ok === false && f1.reason === 'no-barcode', '§FALSIFIER no-barcode → refused', 'reason=' + f1.reason);
  var fat = 'data:image/jpeg;base64,' + Buffer.alloc(40000, 7).toString('base64');
  var f2 = POS.buildRegisterGroup(ctx, { stationId: 100, barcode: '111', name: 'Fat', price: '1.00', imageThumb: fat });
  verdict(f2.ok === false && f2.reason === 'image-over-cap' && f2.bytes > f2.cap,
    '§FALSIFIER over-cap image (' + f2.bytes + 'B > ' + f2.cap + 'B) → refused (no fat ops in the chain)', 'reason=' + f2.reason);
  var f3 = POS.buildRegisterGroup(ctx, { stationId: 100, barcode: 'x', name: 'X', price: '' });
  verdict(f3.ok === false && f3.reason === 'price-not-keyed', '§FALSIFIER price not keyed → refused (1.00 is a USER default, never auto-invented)', 'reason=' + f3.reason);
  // duplicate barcode (the row is now folded into the copy) — the NAMED propose-merge decision
  var f4 = POS.buildRegisterGroup(ctx, spec);
  verdict(f4.ok === false && f4.reason === 'barcode-exists' && f4.existing && f4.existing.m_product_id === g.productId,
    '§FALSIFIER second register of the same barcode → honest collision: PROPOSE-MERGE (existing product handed back; ' +
    'upc uniqueness is NOT enforced in real iDempiere — named decision: §P-8 scan needs barcode→ONE product)',
    'existing=' + (f4.existing && f4.existing.m_product_id));
  console.log('§FALSIFIER pos=register no-barcode=' + f1.reason + ' over-cap=' + f2.reason + ' no-price=' + f3.reason + ' dup=' + f4.reason + ' existing=' + (f4.existing && f4.existing.m_product_id));

  db.close();
  console.log('\n' + (fails === 0 ? '🟢 W-POS-REGISTER PASS' : '🔴 W-POS-REGISTER FAIL (' + fails + ')') +
    ' — ONE signed group (defaults extracted from AD_Column + the station\'s own rows, audit stamps the only omission), ' +
    'deterministic PKs, the tile rings at the keyed price through the unchanged §P-2 path, and no-barcode/over-cap/duplicate all refuse honestly.');
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) { console.error('FATAL', e); process.exit(1); });
