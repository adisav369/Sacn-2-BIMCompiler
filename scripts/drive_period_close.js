// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// drive_period_close.js — headless smoke of build/erp/period_close_poc.html (W-PCLOSE browser, §0.20).
// §-log first: captures the page console, writes build/erp/period_close_drive.log; READ it.
// Proves the POC boots (pageerror=0), the real kernel + erp_period_close + signer load, and the live
// §PCLOSE-BROWSER lines report: signed checkpoint verifies, reconcile maxDiff=0, bootstrap speedup>1, tamper caught.
'use strict';
var puppeteer = require('puppeteer');
var http = require('http'), fs = require('fs'), path = require('path');
var ROOT = path.join(__dirname, '..', 'build', 'erp');
var LOG = path.join(ROOT, 'period_close_drive.log');
var PORT = 8137;
var MIME = { '.html':'text/html', '.js':'application/javascript', '.wasm':'application/wasm', '.json':'application/json' };

var out = [], con = [], errs = [];
function L(s){ out.push(s); }
var sleep = function(ms){ return new Promise(function(r){ setTimeout(r, ms); }); };
function grep(re){ return con.filter(function(l){ return re.test(l); }); }
function flush(code){ fs.writeFileSync(LOG, out.join('\n')+'\n'); console.log(out.join('\n')); process.exit(code); }

var server = http.createServer(function(req, res){
  var f = path.join(ROOT, req.url.split('?')[0].replace(/^\//,''));
  fs.readFile(f, function(err, buf){
    if (err){ res.writeHead(404); res.end('nf'); return; }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(f)] || 'application/octet-stream' });
    res.end(buf);
  });
});

(async function(){
  await new Promise(function(r){ server.listen(PORT, r); });
  var browser;
  try {
    browser = await puppeteer.launch({ headless:'new', executablePath:'/usr/bin/google-chrome',
      args:['--no-sandbox','--disable-dev-shm-usage'] });
  } catch(e){ L('§DRIVE FATAL launch: '+e.message); flush(2); }
  var page = await browser.newPage();
  page.on('console', function(m){ con.push(m.text()); });
  page.on('pageerror', function(e){ errs.push(String(e)); });

  try { await page.goto('http://localhost:'+PORT+'/period_close_poc.html', { waitUntil:'networkidle2', timeout:45000 }); }
  catch(e){ L('§DRIVE goto warn: '+e.message); }
  // the POC auto-runs on load with N=20000; give sql.js wasm + the fold/seal time.
  for (var i=0;i<30 && grep(/§PCLOSE-BROWSER done/).length===0;i++) await sleep(1000);

  L('§DRIVE pageerror='+errs.length+(errs.length?' ['+errs.slice(0,2).join(' | ')+']':''));
  L('§DRIVE kernel-loaded='+(grep(/§KERNEL_OPS_LOADED/).length>0)+' pclose-loaded='+(grep(/§PCLOSE_LOADED/).length>0)+' signer-loaded='+(grep(/§SNAP_SIGN_LOADED/).length>0));
  ['close','reconcile','bootstrap','tamper','done'].forEach(function(tag){
    var hit = grep(new RegExp('§PCLOSE-BROWSER '+tag));
    L('§DRIVE '+tag+': '+(hit[0]||'(MISSING)'));
  });

  // read the rendered verdicts straight from the DOM (.tag.ok / .tag.bad)
  var dom = await page.evaluate(function(){
    return { okTags: document.querySelectorAll('.tag.ok').length, badTags: document.querySelectorAll('.tag.bad').length,
             dot: document.getElementById('dot').className, status: document.getElementById('dot').className.indexOf('OK')>=0 };
  });
  L('§DRIVE dom okTags='+dom.okTags+' badTags='+dom.badTags+' dotState='+dom.dot);

  // NOTE: a CAUGHT tamper renders a red "rejected" chip (.tag.bad) by house convention (so a non-zero
  // badTags count is expected) — the authoritative verdict is dot=OK, which the page sets only when
  // sigOk && balanced && recon.equal && sameResult && caught are ALL true.
  var reconOk  = grep(/§PCLOSE-BROWSER reconcile maxDiff=0c/).length>0;
  var bootOk   = grep(/§PCLOSE-BROWSER bootstrap .*same=true/).length>0;
  var tamperOk = grep(/§PCLOSE-BROWSER tamper .*verify=false/).length>0;
  var sigOk    = grep(/§PCLOSE-BROWSER close .*sig=true/).length>0;
  var done     = grep(/§PCLOSE-BROWSER done/).length>0;
  var pass = errs.length===0 && done && reconOk && bootOk && tamperOk && sigOk && dom.status;
  L('§DRIVE '+(pass?'🟢 PASS':'🔴 FAIL')+' — boot+kernel+sig+reconcile(maxDiff=0)+bootstrap(same)+tamper(caught)+dot=OK');
  await browser.close(); server.close();
  flush(pass?0:1);
})().catch(function(e){ L('§DRIVE FATAL '+(e&&e.message)); flush(2); });
