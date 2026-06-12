#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (POS_ENGINE_LANE.md §E-3 / POS_ADDON_SPEC.md §P-9.1). Read the log after every run.
// poc_img_sync.js — W-IMG-SYNC: the out-of-band COPY JOB — a second device that has synced the op log
//   shows THUMBS until the explicit copy job fills its folder with full-res blobs.
//
// ISSUES IT PROVES (named):
//   1. THE OP-LOG IS THE SYNC SPINE, BLOBS ARE NOT — device-A registers a product WITH a photo through
//      the witnessed §P-9 group (capped thumb + content key in the AD_Image op). Device-B receives ONLY
//      the ops. BEFORE the copy job, B's read path resolves tier=thumb (`§IMG interim=thumb`) — honest
//      interim, never broken, never fabricated full-res.
//   2. THE COPY JOB FILLS THE FOLDER — ImgStore.syncFromLog(ops, fetchBlob) walks the log's AD_Image
//      keys and fetches each absent blob (transport = explicit-export-import; relay-rails NAMED-DEFERRED,
//      see img_store.js header). AFTER: B's folder returns the full 1000B blob, tier=full.
//   3. IDEMPOTENT + HONEST-MISS — re-running the job copies 0 (skipped=1); a key fetchBlob cannot supply
//      is reported in `missing` and the thumb KEEPS rendering (§FALSIFIER — a copy job that fabricates
//      or errors-out on a missing blob would break the honest read path).
//
// HEADLESS HONESTY: "two devices" = two injected Map namespaces (the §E-3 contract); what headless
//   cannot show is the real camera JPEG + IDB persistence across page loads — named, not hidden.
// NON-INVENT: defaults extracted as in W-POS-REGISTER; payloads deterministic; keys = sha256(content).
// Implementing docs/POS_ADDON_SPEC.md §P-9.1 + POS_ENGINE_LANE.md §E-3 — Witness: W-IMG-SYNC
// Run: bash build/erp/run_witness.sh scripts/poc_img_sync.js   — then READ the log.
'use strict';
var path = require('path');
var crypto = require('crypto');
var Database = require('better-sqlite3');
var ImgStore = require(path.join(__dirname, '..', 'build', 'erp', 'img_store.js'));
var POS = require(path.join(__dirname, '..', 'build', 'erp', 'pos_core.js'));

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function lc(r) { if (!r) return r; var o = {}; for (var k in r) o[k.toLowerCase()] = r[k]; return o; }

var seed = new Database(path.join(__dirname, '..', 'build', 'erp', 'ad_seed_fullwidth.db'), { readonly: true });

// the same reference extraction W-POS-REGISTER proved (see poc_pos_register.js:53-92)
function modeOf(rows, col) {
  var c = {}; rows.forEach(function (r) { var v = r[col]; c[v] = (c[v] || 0) + 1; });
  var best = null; Object.keys(c).forEach(function (k) { if (best == null || c[k] > c[best]) best = k; });
  var n = Number(best); return (best !== 'null' && best !== '' && !isNaN(n) && String(n) === best) ? n : (best === 'null' ? null : best);
}
function registerDefaults(db, pos) {
  var tid = function (t) { return lc(db.prepare('SELECT ad_table_id FROM ad_table WHERE LOWER(tablename)=LOWER(?)').get(t)).ad_table_id; };
  var mand = db.prepare("SELECT columnname, defaultvalue FROM ad_column WHERE ad_table_id=? AND ismandatory='Y'").all(tid('M_Product')).map(lc);
  var AUDIT = { created: 1, createdby: 1, updated: 1, updatedby: 1 }, KEYED = { m_product_id: 1, name: 1, value: 1 };
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
  try { var sr = db.prepare("SELECT value FROM ad_sysconfig WHERE name='DEFAULT_ENTITYTYPE'").get(); if (sr) et = lc(sr).value; } catch (e) { /* no ad_sysconfig table → default arg */ }
  var seq = db.prepare('SELECT COALESCE(MAX(seqno),0)+10 s FROM c_poskey WHERE c_poskeylayout_id=?').get(pos.c_poskeylayout_id).s;
  return {
    dict: dict, product: product,
    price: { ad_client_id: modeOf(pricerows, 'ad_client_id'), ad_org_id: modeOf(pricerows, 'ad_org_id') },
    image: { ad_client_id: modeOf(imgrows, 'ad_client_id'), ad_org_id: modeOf(imgrows, 'ad_org_id'), entitytype: et },
    poskey: { ad_client_id: modeOf(keyrows, 'ad_client_id'), ad_org_id: modeOf(keyrows, 'ad_org_id'), qty: modeOf(keyrows, 'qty'), nextseqno: seq }
  };
}

(async function () {
  console.log('═══ W-IMG-SYNC — device-A registers w/ photo → ops sync to B → B shows THUMB → copy job → B shows FULL ═══\n');

  var pos = lc(seed.prepare('SELECT * FROM c_pos WHERE c_pos_id=100').get());
  var plv = lc(seed.prepare('SELECT m_pricelist_version_id v FROM m_pricelist_version WHERE m_pricelist_id=?').get(pos.m_pricelist_id));

  // ── DEVICE A: full-res photo (1000B fixture) + the §P-9 register group carrying its CAPPED thumb ──
  var fullRes = new Uint8Array(1000); for (var i = 0; i < 1000; i++) fullRes[i] = (i * 13) % 256;
  var key = 'sha256:' + crypto.createHash('sha256').update(fullRes).digest('hex');     // content address
  var thumbBytes = Buffer.from(fullRes.slice(0, 300));                                  // the downscaled stand-in (named fixture)
  var thumb = 'data:image/jpeg;base64,' + thumbBytes.toString('base64');

  var deviceA = ImgStore.createFolder({ adapter: new Map() });
  await deviceA.put(key, fullRes);                                                      // A's folder holds the full-res

  var prior = 0;
  var ctx = {
    pos: pos, priceListVersionId: plv.v,
    registerDefaults: function () { return registerDefaults(seed, pos); },
    productByBarcode: function (upc) { return lc(seed.prepare('SELECT m_product_id, name FROM m_product WHERE upc=?').get(upc)) || null; },
    priorCreateCount: function () { return prior; }
  };
  var g = POS.buildRegisterGroup(ctx, { stationId: pos.c_pos_id, barcode: '9555000011111', name: 'Sync Mug', price: '3.00', imageThumb: thumb, imageKey: key });
  verdict(g.ok === true && g.imageId != null, 'device-A: §P-9 register group carries the AD_Image op (capped thumb + content key)', 'imageId=' + g.imageId);
  var imgOp = g.ops.filter(function (o) { return o.table === 'AD_Image'; })[0];
  verdict(imgOp.fields.imageurl === key && POS.dataUrlBytes(imgOp.fields.binarydata) <= POS.IMAGE_CAP_BYTES,
    'the op names the CONTENT KEY; only the ≤cap thumb rides the log (the blob does NOT)', 'thumbBytes=' + POS.dataUrlBytes(imgOp.fields.binarydata));
  console.log('§IMG deviceA register key=' + key.slice(0, 20) + '… fullRes=1000B thumb=' + POS.dataUrlBytes(imgOp.fields.binarydata) + 'B (op-log carries thumb only)');

  // ── DEVICE B: ops synced (g.ops), folder EMPTY — the honest interim = THUMB ──
  var deviceB = ImgStore.createFolder({ adapter: new Map() });
  var thumbOf = function (k) {                                                          // B's ledger-thumb lookup (folded AD_Image ops)
    var op = g.ops.filter(function (o) { return o.table === 'AD_Image' && o.fields.imageurl === k; })[0];
    return op ? op.fields.binarydata : null;
  };
  var before = await ImgStore.resolveImage(deviceB, key, thumbOf);
  verdict(before.tier === 'thumb' && before.src === thumb,
    'BEFORE the copy job: B renders the LEDGER THUMB (folder empty — honest interim, not broken, not fabricated)', 'tier=' + before.tier);
  console.log('§IMG interim=thumb device=B key=' + key.slice(0, 20) + '… (ops synced, blobs not)');

  // ── THE COPY JOB: syncFromLog walks the log's AD_Image keys, fetchBlob = A's exported album ──
  var fetches = 0;
  function fetchBlob(k) { fetches++; return deviceA.get(k); }                            // explicit-export-import transport (named)
  var job = await ImgStore.syncFromLog(deviceB, g.ops, fetchBlob);
  verdict(job.copied === 1 && job.skipped === 0 && job.missing.length === 0,
    'copy job: 1 key named by the log → 1 blob fetched and filed', JSON.stringify(job));
  var after = await ImgStore.resolveImage(deviceB, key, thumbOf);
  var gotLen = (await deviceB.get(key)).length;
  verdict(after.tier === 'full' && gotLen === 1000, 'AFTER: B\'s folder returns the FULL-RES 1000B blob (tier=full)', 'bytes=' + gotLen);
  console.log('§IMG copied n=' + job.copied + ' device=B bytes=' + gotLen + ' tier=' + after.tier +
    ' transport=' + ImgStore.TRANSPORT);

  // ── idempotent re-run: nothing re-copied ──
  var again = await ImgStore.syncFromLog(deviceB, g.ops, fetchBlob);
  verdict(again.copied === 0 && again.skipped === 1, 'copy job idempotent: re-run copies 0, skips the present key', JSON.stringify(again));

  // ── §FALSIFIER: a key the transport cannot supply → reported missing, thumb keeps rendering ──
  var ghostOps = [{ op_type: 'CRUD_CREATE', table: 'AD_Image', fields: { ad_image_id: 1, imageurl: 'sha256:ghost', binarydata: thumb } }];
  var miss = await ImgStore.syncFromLog(deviceB, ghostOps, function () { return null; });
  var ghostRender = await ImgStore.resolveImage(deviceB, 'sha256:ghost', function () { return thumb; });
  verdict(miss.copied === 0 && miss.missing.length === 1 && miss.missing[0] === 'sha256:ghost' && ghostRender.tier === 'thumb',
    '§FALSIFIER unfetchable blob → reported missing, NEVER fabricated; the thumb keeps rendering', JSON.stringify(miss));
  console.log('§FALSIFIER img=sync missing=' + JSON.stringify(miss.missing) + ' render=' + ghostRender.tier + ' (must be thumb, not full/none)');

  seed.close();
  console.log('\n' + (fails === 0 ? '🟢 W-IMG-SYNC PASS' : '🔴 W-IMG-SYNC FAIL (' + fails + ')') +
    ' — the op-log carries thumb+key only; device-B is honest-thumb before the copy job, full-res 1000B after; the job is idempotent and reports (not fabricates) missing blobs.');
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) { console.error('FATAL', e); process.exit(1); });
