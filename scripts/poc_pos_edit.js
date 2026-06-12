#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (POS_ENGINE_LANE.md §E-2 / POS_ADDON_SPEC.md §P-10). Read the log after every run.
// poc_pos_edit.js — W-POS-EDIT: edit-a-product rides the SAME signed write path as a sale
//   (CRUD_UPDATE ops through kernel_ops.commitGroup — the #268 CRUD-rails discipline).
//
// ISSUES IT PROVES (named):
//   1. CHANGED COLS ONLY — a price edit 1.00→2.50 emits ONE CRUD_UPDATE on the station's m_productprice
//      row carrying exactly the price cols whose value changed ({old,new} diffs); a name edit updates
//      M_Product.name + the tile's own c_poskey.name; a photo swap updates AD_Image under the SAME
//      §P-9.1 cap. No column the operator did not change appears in any diff.
//   2. THE NEXT RING REFLECTS X — after the edit folds, the UNCHANGED §P-2 ringLine returns the NEW
//      sealed master price (2.50), to the cent. The edit is master-data truth, not lens memory.
//   3. NO-OP SUPPRESSION (§FALSIFIER) — an edit that changes NOTHING emits ZERO ops (the #268
//      no-op-suppression rule: a save with 0 changed columns commits nothing).
//
// NON-INVENT: the edited product is the W-POS-REGISTER DIY product (registered at 1.00 on the real
//   seed copy); every old value read from the folded row, every new value the operator's keyed input.
// Implementing docs/POS_ADDON_SPEC.md §P-10 — Witness: W-POS-EDIT
// Run: bash build/erp/run_witness.sh scripts/poc_pos_edit.js   — then READ the log.
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

var SEED = path.join(__dirname, '..', 'build', 'erp', 'ad_seed_fullwidth.db');
var COPY = '/tmp/poc_pos_edit_seed.db';
fs.copyFileSync(SEED, COPY);
var db = new Database(COPY);

// the same extraction as poc_pos_register (the reference implementation — kept identical on purpose)
function modeOf(rows, col) {
  var c = {}; rows.forEach(function (r) { var v = r[col]; c[v] = (c[v] || 0) + 1; });
  var best = null; Object.keys(c).forEach(function (k) { if (best == null || c[k] > c[best]) best = k; });
  var n = Number(best); return (best !== 'null' && best !== '' && !isNaN(n) && String(n) === best) ? n : (best === 'null' ? null : best);
}
function registerDefaults(db, pos) {
  var tid = function (t) { return lc(db.prepare('SELECT ad_table_id FROM ad_table WHERE LOWER(tablename)=LOWER(?)').get(t)).ad_table_id; };
  var mand = db.prepare("SELECT columnname, defaultvalue FROM ad_column WHERE ad_table_id=? AND ismandatory='Y'").all(tid('M_Product')).map(lc);
  var AUDIT = { created: 1, createdby: 1, updated: 1, updatedby: 1 };
  var KEYED = { m_product_id: 1, name: 1, value: 1 };
  var prows = db.prepare('SELECT * FROM m_product WHERE M_Product_ID IN (SELECT m_product_id FROM c_poskey WHERE c_poskeylayout_id=? AND m_product_id IS NOT NULL)').all(pos.c_poskeylayout_id).map(lc);
  var dict = {}, product = {};
  mand.forEach(function (c) {
    var col = c.columnname.toLowerCase();
    if (AUDIT[col] || KEYED[col]) return;
    var dv = c.defaultvalue;
    if (dv != null && dv !== 'SYSDATE' && dv.indexOf('@') < 0) dict[col] = (String(Number(dv)) === dv) ? Number(dv) : dv;
    else product[col] = modeOf(prows, col);
  });
  var pricerows = db.prepare('SELECT * FROM m_productprice WHERE M_PriceList_Version_ID=?').all(104).map(lc);
  var imgrows = db.prepare('SELECT * FROM ad_image').all().map(lc);
  var keyrows = db.prepare('SELECT * FROM c_poskey WHERE c_poskeylayout_id=?').all(pos.c_poskeylayout_id).map(lc);
  var et = 'U';
  try { var sr = db.prepare("SELECT value FROM ad_sysconfig WHERE name='DEFAULT_ENTITYTYPE'").get(); if (sr) et = lc(sr).value; } catch (e) {}
  var seq = db.prepare('SELECT COALESCE(MAX(seqno),0)+10 s FROM c_poskey WHERE c_poskeylayout_id=?').get(pos.c_poskeylayout_id).s;
  return {
    dict: dict, product: product,
    price: { ad_client_id: modeOf(pricerows, 'ad_client_id'), ad_org_id: modeOf(pricerows, 'ad_org_id') },
    image: { ad_client_id: modeOf(imgrows, 'ad_client_id'), ad_org_id: modeOf(imgrows, 'ad_org_id'), entitytype: et },
    poskey: { ad_client_id: modeOf(keyrows, 'ad_client_id'), ad_org_id: modeOf(keyrows, 'ad_org_id'), qty: modeOf(keyrows, 'qty'), nextseqno: seq }
  };
}

(async function () {
  console.log('═══ W-POS-EDIT — §P-10 edit-a-product: signed UPDATE ops, changed cols only, the next ring reflects X ═══\n');

  var pos = lc(db.prepare('SELECT * FROM c_pos WHERE c_pos_id=100').get());
  var plv = lc(db.prepare('SELECT m_pricelist_version_id v FROM m_pricelist_version WHERE m_pricelist_id=?').get(pos.m_pricelist_id));

  var SQL = await initSqlJs();
  var opDb = new SQL.Database(); KO.ensureTable(opDb);
  function priorCreateCount() {
    var r = opDb.exec("SELECT COUNT(*) FROM kernel_ops WHERE op_type='CRUD_CREATE'");
    return (r[0] && Number(r[0].values[0][0])) || 0;
  }
  function commit(ops, ts) {
    return KO.commitGroup(opDb, ops.map(function (o) { return { op_type: o.op_type, params: o }; }), { baseTs: ts });
  }
  // fold helpers: apply CRUD ops to the writable copy (what the substrate's fold does)
  function apply(ops) {
    ops.forEach(function (o) {
      if (o.op_type === 'CRUD_CREATE') {
        var cols = Object.keys(o.fields);
        db.prepare('INSERT INTO ' + o.table + ' (' + cols.join(',') + ') VALUES (' + cols.map(function () { return '?'; }).join(',') + ')')
          .run(cols.map(function (c) { return o.fields[c]; }));
      } else if (o.op_type === 'CRUD_UPDATE') {
        var cs = Object.keys(o.changes);
        var pk = o.table.toLowerCase() === 'm_productprice' ? 'm_productprice_id' : o.table.toLowerCase() + '_id';
        db.prepare('UPDATE ' + o.table + ' SET ' + cs.map(function (c) { return c + '=?'; }).join(',') + ' WHERE ' + pk + '=?')
          .run(cs.map(function (c) { return o.changes[c].new; }).concat([o.id]));
      }
    });
  }

  // ── fixture: the W-POS-REGISTER DIY product, registered at 1.00 with a photo ──
  var regCtx = {
    pos: pos, priceListVersionId: plv.v,
    registerDefaults: function () { return registerDefaults(db, pos); },
    productByBarcode: function (upc) { return lc(db.prepare('SELECT m_product_id, name FROM m_product WHERE upc=?').get(upc)) || null; },
    priorCreateCount: priorCreateCount
  };
  var thumbBytes = Buffer.alloc(1200, 7);
  var thumb = 'data:image/jpeg;base64,' + thumbBytes.toString('base64');
  var reg = POS.buildRegisterGroup(regCtx, {
    stationId: pos.c_pos_id, barcode: '9555001234567', name: 'Demo Mug', price: '1.00',
    imageThumb: thumb, imageKey: 'sha256:' + crypto.createHash('sha256').update(thumbBytes).digest('hex')
  });
  await commit(reg.ops, 1718000000000); apply(reg.ops);
  verdict(reg.ok === true, 'fixture: DIY product registered at 1.00 (W-POS-REGISTER path) and folded', 'productId=' + reg.productId);

  // ── the edit ctx (reads the FOLDED rows — old values are the tip truth, never lens memory) ──
  var editCtx = {
    productById: function (pid) { return lc(db.prepare('SELECT m_product_id, name, upc FROM m_product WHERE m_product_id=?').get(pid)) || null; },
    priceRowOf: function (pid) { return lc(db.prepare('SELECT m_productprice_id, pricestd, pricelist, pricelimit FROM m_productprice WHERE m_pricelist_version_id=? AND m_product_id=?').get(plv.v, pid)) || null; },
    poskeyOf: function (pid) { return lc(db.prepare('SELECT c_poskey_id, name, ad_image_id FROM c_poskey WHERE c_poskeylayout_id=? AND m_product_id=?').get(pos.c_poskeylayout_id, pid)) || null; },
    imageById: function (iid) { return lc(db.prepare('SELECT ad_image_id, name, imageurl FROM ad_image WHERE ad_image_id=?').get(iid)) || null; },
    registerDefaults: function () { return registerDefaults(db, pos); },
    priorCreateCount: priorCreateCount
  };

  // ── 1. price edit 1.00 → 2.50: ONE CRUD_UPDATE, exactly the price cols, {old,new} honest ──
  var e1 = POS.buildEditGroup(editCtx, { productId: reg.productId, price: '2.50' });
  verdict(e1.ok === true && e1.ops.length === 1 && e1.ops[0].op_type === 'CRUD_UPDATE' && e1.ops[0].table === 'M_ProductPrice',
    'price edit → ONE CRUD_UPDATE on the station price row (no other table touched)', 'ops=' + e1.ops.length);
  var ch = e1.ops[0].changes;
  // old = the row's STORED value verbatim (sqlite REAL coerces '1.00' → 1) — honest, compared numerically
  verdict(Object.keys(ch).sort().join(',') === 'pricelimit,pricelist,pricestd' &&
    Number(ch.pricestd.old) === 1 && ch.pricestd.new === '2.50',
    'diff carries EXACTLY the changed price cols with honest {old:1(.00), new:2.50}', JSON.stringify(ch.pricestd));
  var r1 = await commit(e1.ops, 1718000001000); apply(e1.ops);
  var chain1 = await KO.verifyChain(opDb);
  verdict(r1.committed && chain1.ok, 'price edit committed as a signed group, chainOk=Y', 'gid=' + r1.gid);
  console.log('§POS-EDIT price 1.00→2.50 cols=' + Object.keys(ch).join(',') + ' gid=' + r1.gid + ' chainOk=' + (chain1.ok ? 'Y' : 'N'));

  // 2. THE NEXT RING REFLECTS X — the unchanged §P-2 ringLine reads the new sealed master
  var priceStmt = db.prepare('SELECT pricestd FROM m_productprice WHERE m_pricelist_version_id=? AND m_product_id=?');
  var saleCtx = { pos: pos, priceOf: function (pid) { return lc(priceStmt.get(plv.v, pid)) || null; }, bomOf: function () { return []; } };
  var line = POS.ringLine(saleCtx, reg.productId, 1);
  verdict(line.ok === true && line.priceactual === '2.50', 'the NEXT RING reflects X: ringLine returns 2.50 (sealed master, not lens memory)', 'priceactual=' + line.priceactual);
  console.log('§POS-EDIT ring-after-edit product=' + reg.productId + ' priceactual=' + line.priceactual);

  // ── 3. name edit → M_Product.name + the tile's own label ──
  var e2 = POS.buildEditGroup(editCtx, { productId: reg.productId, name: 'Demo Mug XL' });
  var tables = e2.ops.map(function (o) { return o.table; }).sort().join(',');
  verdict(e2.ok === true && e2.ops.length === 2 && tables === 'C_POSKey,M_Product',
    'name edit → M_Product.name + c_poskey.name (the tile label), nothing else', 'tables=' + tables);
  await commit(e2.ops, 1718000002000); apply(e2.ops);
  var nm = lc(db.prepare('SELECT name FROM m_product WHERE m_product_id=?').get(reg.productId));
  verdict(nm.name === 'Demo Mug XL', 'name folded: product reads Demo Mug XL', 'name=' + nm.name);
  console.log('§POS-EDIT name=' + nm.name + ' tables=' + tables);

  // ── 4. photo swap (cap enforced): AD_Image binarydata+imageurl change under the §P-9.1 bound ──
  var thumb2Bytes = Buffer.alloc(1800, 9);
  var thumb2 = 'data:image/jpeg;base64,' + thumb2Bytes.toString('base64');
  var key2 = 'sha256:' + crypto.createHash('sha256').update(thumb2Bytes).digest('hex');
  var e3 = POS.buildEditGroup(editCtx, { productId: reg.productId, imageThumb: thumb2, imageKey: key2 });
  verdict(e3.ok === true && e3.ops.length === 1 && e3.ops[0].table === 'AD_Image' && e3.ops[0].id === reg.imageId &&
    e3.ops[0].changes.binarydata.new === thumb2 && e3.ops[0].changes.imageurl.new === key2,
    'photo swap → ONE CRUD_UPDATE on the EXISTING AD_Image row (new thumb + new content key)', 'id=' + e3.ops[0].id);
  await commit(e3.ops, 1718000003000); apply(e3.ops);
  var fat = 'data:image/jpeg;base64,' + Buffer.alloc(40000, 9).toString('base64');
  var e4 = POS.buildEditGroup(editCtx, { productId: reg.productId, imageThumb: fat });
  verdict(e4.ok === false && e4.reason === 'image-over-cap', '§FALSIFIER over-cap photo swap → refused (same ≤32KB op-log bound as register)', 'reason=' + e4.reason);
  console.log('§POS-EDIT photo-swap id=' + reg.imageId + ' bytes=' + POS.dataUrlBytes(thumb2) + ' over-cap=' + e4.reason);

  // ── 5. §FALSIFIER: a no-change edit emits NO ops (the #268 no-op-suppression rule) ──
  var e5 = POS.buildEditGroup(editCtx, { productId: reg.productId, name: 'Demo Mug XL', price: '2.50' });
  verdict(e5.ok === true && e5.noop === true && e5.ops.length === 0,
    '§FALSIFIER no-change edit (same name, same price) → ZERO ops, nothing committed', 'noop=' + e5.noop + ' ops=' + e5.ops.length);
  console.log('§FALSIFIER pos=edit no-change ops=' + e5.ops.length + ' noop=' + e5.noop + ' (must be 0/true)');

  // unknown product = honest refusal (no invented target row)
  var e6 = POS.buildEditGroup(editCtx, { productId: 999999, price: '2.00' });
  verdict(e6.ok === false && e6.reason === 'no-such-product', 'unknown product → no-such-product refusal', 'reason=' + e6.reason);

  var chainF = await KO.verifyChain(opDb);
  verdict(chainF.ok, 'whole log (register + 3 edits) verifies end-to-end, chainOk=Y', 'len=' + chainF.len);
  console.log('§POS-EDIT chainOk=' + (chainF.ok ? 'Y' : 'N') + ' len=' + chainF.len);

  db.close();
  console.log('\n' + (fails === 0 ? '🟢 W-POS-EDIT PASS' : '🔴 W-POS-EDIT FAIL (' + fails + ')') +
    ' — price 1.00→2.50 emits exactly the changed cols and the next ring reads 2.50; name edit updates product+tile; ' +
    'photo swap rides the same cap; a no-change edit emits NO ops.');
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) { console.error('FATAL', e); process.exit(1); });
