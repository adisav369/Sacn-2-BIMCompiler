// poc_wh_live_pages.js — LIVE Pages verify for the spatial-walk deep-link (PR #270; db moved IN-REPO
// by PR #272 and the OCI duplicate DELETED 2026-06-12c — this witness now pins the GH Pages URL,
// the SAME relative path the shipped WH pill uses).
// Proves: the LIVE GH Pages viewer + the in-repo warehouse db deep-link renders
// (no §BBOX_KEEP / §BLOB_MISS = no-cubes gate) and the data-gated §WH PILL lights gate=on.
var puppeteer = require('puppeteer');
var DB = '../buildings/warehouse_gardenworld.db';
var URL = 'https://red1oon.github.io/bim-ootb/viewer/viewer.html?db=' + encodeURIComponent(DB);
(async function () {
  var browser = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox', '--disable-dev-shm-usage'] });
  var page = await browser.newPage();
  var lines = [];
  page.on('console', function (m) { var t = m.text(); if (/§/.test(t)) lines.push(t); });
  await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await new Promise(function (ok) { setTimeout(ok, 25000); });
  var has = function (s) { return lines.some(function (l) { return l.indexOf(s) >= 0; }); };
  console.log('§LIVE_URL ' + URL);
  lines.forEach(function (l) { console.log('   | ' + l); });
  var fails = 0;
  function verdict(ok, label) { if (!ok) fails++; console.log((ok ? '🟢 ' : '🔴 ') + label); }
  verdict(has('§BBOX_CLEARED'), '§BBOX_CLEARED seen (real geometry replaced bboxes)');
  verdict(!has('§BBOX_KEEP'), 'no §BBOX_KEEP (no cubes served)');
  verdict(!has('§BLOB_MISS'), 'no §BLOB_MISS');
  verdict(has('§WH PILL gate=on'), '§WH PILL gate=on on the LIVE page (locator-GUID bins from the COMMON-bucket db)');
  console.log(fails === 0 ? '§W-WH-LIVE-PAGES PASS' : '§W-WH-LIVE-PAGES FAIL fails=' + fails);
  await browser.close();
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) { console.error('§ERROR ' + e); process.exit(1); });
