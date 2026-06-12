#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (POS_ENGINE_LANE.md §E-3 / POS_ADDON_SPEC.md §P-9.1). Read the log after every run.
// poc_img_folder.js — W-IMG-FOLDER: the device-local images FOLDER (content-addressed put/get/has,
//   folder-create idempotent).
//
// ISSUES IT PROVES (named):
//   1. THE FOLDER IS CONTENT-ADDRESSED AND HONEST — put(key, bytes) → has(key)=true, get(key) returns
//      the EXACT bytes (length + content verified); an absent key returns null (never a fabricated
//      blob, never an exception the render path would mask).
//   2. CREATE IS IDEMPOTENT — createFolder() twice over the same backing = the SAME folder: a key put
//      through the first handle is visible through the second (re-opening the album does not wipe it).
//   3. THE HEADLESS ADAPTER IS THE BROWSER SURFACE — the injected-Map folder exposes exactly the
//      put/get/has the IDB folder exposes (one read path in the lens, two backings); window absent →
//      Map-backed by definition (the §E-3 injection contract).
//
// NON-INVENT: payloads are deterministic fixture bytes (named); keys are content addresses computed
//   from those bytes (sha256) — no Date.now/Math.random anywhere.
// Implementing docs/POS_ADDON_SPEC.md §P-9.1 + POS_ENGINE_LANE.md §E-3 — Witness: W-IMG-FOLDER
// Run: bash build/erp/run_witness.sh scripts/poc_img_folder.js   — then READ the log.
'use strict';
var path = require('path');
var crypto = require('crypto');
var ImgStore = require(path.join(__dirname, '..', 'build', 'erp', 'img_store.js'));

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

(async function () {
  console.log('═══ W-IMG-FOLDER — the images folder: content-addressed put/get/has, create idempotent ═══\n');

  // deterministic 100-byte fixture (a stand-in for a full-res JPEG, named) + its content address
  var bytes = new Uint8Array(100); for (var i = 0; i < 100; i++) bytes[i] = (i * 7) % 256;
  var keyA = 'sha256:' + crypto.createHash('sha256').update(bytes).digest('hex');

  // ── the headless folder = injected Map (the §E-3 adapter contract; window absent → Map-backed) ──
  var backing = new Map();
  var folder = ImgStore.createFolder({ adapter: backing });
  verdict(folder.kind === 'map' && typeof folder.put === 'function' && typeof folder.get === 'function' && typeof folder.has === 'function',
    'createFolder({adapter:Map}) → Map-backed folder with the put/get/has surface (same as the IDB folder)', 'kind=' + folder.kind);

  // ── put → has → get the exact bytes back ──
  await folder.put(keyA, bytes);
  var have = await folder.has(keyA);
  var got = await folder.get(keyA);
  verdict(have === true, 'has(key)=true after put', 'key=' + keyA.slice(0, 20) + '…');
  verdict(got != null && got.length === 100, 'get(key) returns the payload (100B in → ' + (got && got.length) + 'B out)', 'len=' + (got && got.length));
  var same = got != null && got.length === bytes.length && Array.prototype.every.call(got, function (b, i) { return b === bytes[i]; });
  verdict(same, 'bytes round-trip EXACTLY (content verified, not just length)', 'sha-recheck=' + ('sha256:' + crypto.createHash('sha256').update(Buffer.from(got)).digest('hex') === keyA));
  console.log('§IMG folder put/get key=' + keyA.slice(0, 20) + '… bytes=100 roundtrip=exact');

  // ── absent key → null (the honest read path: the lens falls back to the ledger thumb) ──
  var keyB = 'sha256:' + crypto.createHash('sha256').update(Buffer.from('other-content')).digest('hex');
  var miss = await folder.get(keyB);
  var missHas = await folder.has(keyB);
  verdict(miss === null && missHas === false, 'absent key → get=null, has=false (placeholder/thumb fallback, never fabricated)', 'key-B miss=null');
  console.log('§IMG folder miss key-B get=null has=false');

  // ── idempotent create: a second handle over the SAME backing sees the same content ──
  var folder2 = ImgStore.createFolder({ adapter: backing });
  var still = await folder2.get(keyA);
  verdict(still != null && still.length === 100, 'createFolder twice (same backing) = the SAME folder — key survives re-open (idempotent, no wipe)', 'len=' + (still && still.length));
  // and the module-default folder (no opts, headless) is itself stable across calls
  await ImgStore.put('default-key', bytes);
  var defHas = await ImgStore.has('default-key');
  var defAgain = ImgStore.createFolder();
  var defStill = await defAgain.get('default-key');
  verdict(defHas === true && defStill != null, 'module-default folder (window absent → Map) is stable across createFolder() calls', 'has=' + defHas);
  console.log('§IMG folder idempotent reopen=ok default-folder=stable');

  // ── the read path tiering (resolveImage): full → thumb → none ──
  var r1 = await ImgStore.resolveImage(folder, keyA, function () { return 'THUMB'; });
  var r2 = await ImgStore.resolveImage(folder, keyB, function () { return 'THUMB'; });
  var r3 = await ImgStore.resolveImage(folder, keyB, function () { return null; });
  verdict(r1.tier === 'full' && r2.tier === 'thumb' && r3.tier === 'none',
    'resolveImage tiers honestly: present→full, absent-with-ledger-thumb→thumb, neither→none', r1.tier + '/' + r2.tier + '/' + r3.tier);
  console.log('§IMG resolve tiers full/thumb/none = ' + r1.tier + '/' + r2.tier + '/' + r3.tier);

  console.log('\n' + (fails === 0 ? '🟢 W-IMG-FOLDER PASS' : '🔴 W-IMG-FOLDER FAIL (' + fails + ')') +
    ' — content-addressed put/get/has round-trips exact bytes, absent keys return null, folder-create is idempotent, and the read path tiers full→thumb→none.');
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) { console.error('FATAL', e); process.exit(1); });
