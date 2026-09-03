/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
// share.js — Unified Share Sheet (lazy-loaded on first Share click)
// Save as IFC / Save as DB / Contribute to OOTB + WhatsApp / Email / Copy Link
// See docs/EnterpriseAuthentication.md for security architecture.

(function(A) {
  'use strict';

  // ── CSS injected once ──
  var styleInjected = false;
  function injectStyle() {
    if (styleInjected) return;
    styleInjected = true;
    var s = document.createElement('style');
    s.textContent =
      '.share-overlay{position:fixed;top:0;left:0;width:100vw;height:100vh;background:rgba(0,0,0,0.7);z-index:10000;display:flex;align-items:center;justify-content:center}' +
      '.share-sheet{background:#1e1e1e;border:1px solid #444;border-radius:12px;padding:0;max-width:380px;width:90%;color:#eee;font-family:system-ui,sans-serif;overflow:hidden}' +
      '.share-header{display:flex;justify-content:space-between;align-items:center;padding:16px 20px 12px;border-bottom:1px solid #333}' +
      '.share-header h3{margin:0;font-size:15px;color:#fff}' +
      '.share-close{background:none;border:none;color:#888;font-size:18px;cursor:pointer;padding:4px 8px}' +
      '.share-close:hover{color:#fff}' +
      '.share-section{padding:8px 12px}' +
      '.share-section-label{font-size:10px;text-transform:uppercase;color:#666;letter-spacing:1px;padding:8px 8px 4px;margin:0}' +
      '.share-btn{display:flex;align-items:center;gap:12px;width:100%;padding:12px 16px;background:transparent;border:none;color:#ddd;font-size:13px;cursor:pointer;border-radius:8px;text-align:left}' +
      '.share-btn:hover{background:rgba(255,255,255,0.06)}' +
      '.share-btn .share-icon{width:28px;height:28px;border-radius:6px;display:flex;align-items:center;justify-content:center;font-size:14px;flex-shrink:0}' +
      '.share-btn .share-label{flex:1}' +
      '.share-btn .share-sublabel{font-size:11px;color:#888;display:block}' +
      '.share-divider{border:none;border-top:1px solid #333;margin:0}' +
      '.share-send-section{opacity:0.4;pointer-events:none;transition:opacity 0.3s}' +
      '.share-send-section.active{opacity:1;pointer-events:auto}' +
      '.share-status{padding:8px 20px 12px;font-size:11px;color:#888;text-align:center;min-height:20px}';
    document.head.appendChild(s);
  }

  // ── DB buffer helper — reads versioned or legacy ──
  function getDbBuffer(record) {
    if (record.versions && record.versions.length > 0) {
      return record.versions[record.latestVersion || 0].db;
    }
    return record.extractedDb;
  }

  // ── DB Integrity Validation (same as contribute.js) ──
  function validateDB(dbBytes) {
    try {
      var SQL_inst = new (window.SQL || window._SQL_CACHED).Database(new Uint8Array(dbBytes));
      var tables = SQL_inst.exec("SELECT name FROM sqlite_master WHERE type='table'");
      if (!tables.length) { SQL_inst.close(); return { valid: false, reason: 'No tables found' }; }
      var tableNames = tables[0].values.map(function(r) { return r[0]; });
      var required = ['meshes', 'elements', 'building'];
      var missing = required.filter(function(t) { return tableNames.indexOf(t) === -1; });
      if (missing.length > 0) { SQL_inst.close(); return { valid: false, reason: 'Missing tables: ' + missing.join(', ') }; }

      var sample = SQL_inst.exec("SELECT vertices FROM meshes LIMIT 3");
      if (sample.length && sample[0].values.length > 0) {
        for (var i = 0; i < sample[0].values.length; i++) {
          var blob = sample[0].values[i][0];
          if (blob && blob.byteLength % 4 !== 0) {
            SQL_inst.close();
            return { valid: false, reason: 'Mesh BLOB not aligned to Float32Array' };
          }
        }
      }

      var bldCount = SQL_inst.exec("SELECT COUNT(*) FROM building");
      if (!bldCount.length || bldCount[0].values[0][0] === 0) {
        SQL_inst.close();
        return { valid: false, reason: 'No building record found' };
      }

      if (dbBytes.byteLength > 200 * 1024 * 1024) {
        SQL_inst.close();
        return { valid: false, reason: 'File exceeds 200MB limit' };
      }

      SQL_inst.close();
      return { valid: true };
    } catch (e) {
      return { valid: false, reason: 'DB open failed: ' + e.message };
    }
  }

  // ── Save as DB (download) ──
  function saveAsDB(record, key, statusEl) {
    var dbBuf = getDbBuffer(record);
    if (!dbBuf) { statusEl.textContent = 'No DB data'; return; }
    var filename = (record.meta.filename || record.meta.name || key).replace(/\.[^.]+$/, '') + '_extracted.db';
    var blob = new Blob([dbBuf], { type: 'application/octet-stream' });
    var link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = filename;
    link.click();
    URL.revokeObjectURL(link.href);
    statusEl.textContent = 'Saved: ' + filename;
    console.log('§SHARE saveAsDB file=' + filename + ' size=' + (dbBuf.byteLength / 1024).toFixed(0) + 'KB');
  }

  // ── Save as IFC (delegates to existing exportIFC) ──
  function saveAsIFC(key, statusEl) {
    statusEl.textContent = 'Exporting IFC...';
    if (A.exportIFC) {
      A.exportIFC(key);
      statusEl.textContent = 'IFC export started';
    } else {
      statusEl.textContent = 'IFC export not available';
    }
  }

  // ── Contribute to OOTB Gallery ──
  async function contributeToOOTB(record, key, statusEl, sendSection) {
    if (!A.CONTRIBUTE_PAR) {
      statusEl.textContent = 'Contribute not configured (no PAR URL)';
      console.log('§SHARE contribute skip — no CONTRIBUTE_PAR');
      return null;
    }

    var dbBuf = getDbBuffer(record);
    if (!dbBuf) { statusEl.textContent = 'No DB data'; return null; }

    var meta = record.meta || {};
    var filename = (meta.filename || meta.name || key).replace(/\.[^.]+$/, '') + '_extracted.db';

    // Validate DB integrity
    statusEl.textContent = 'Validating DB integrity...';
    console.log('§SHARE validating key=' + key);
    var check = validateDB(dbBuf);
    if (!check.valid) {
      console.log('§SHARE_REJECT reason=' + check.reason);
      statusEl.textContent = 'Rejected: ' + check.reason;
      alert('This database did not pass integrity validation:\n\n' + check.reason +
            '\n\nOnly databases produced by the BIM OOTB extraction pipeline can be contributed.');
      return null;
    }
    console.log('§SHARE_VALID key=' + key);

    // Upload
    statusEl.textContent = 'Uploading to gallery...';
    var blob = new Blob([dbBuf], { type: 'application/octet-stream' });
    var url = A.CONTRIBUTE_PAR + filename;

    try {
      var resp = await fetch(url, { method: 'PUT', body: blob });
      if (!resp.ok) {
        statusEl.textContent = 'Upload failed: ' + resp.status;
        console.log('§SHARE contribute fail status=' + resp.status);
        return null;
      }

      console.log('§SHARE contribute ok file=' + filename + ' size=' + (dbBuf.byteLength / 1024).toFixed(0) + 'KB');

      // Upload metadata
      var metaBlob = new Blob([JSON.stringify({
        filename: filename,
        elements: meta.elementCount,
        disciplines: Object.keys(meta.disciplines || {}),
        date: new Date().toISOString(),
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone
      })], { type: 'application/json' });
      await fetch(A.CONTRIBUTE_PAR + filename + '.meta.json', { method: 'PUT', body: metaBlob });
      console.log('§SHARE meta ok file=' + filename + '.meta.json');

      // Update index.json
      var indexUrl = A.CONTRIBUTE_PAR + 'index.json';
      var existing = [];
      try {
        var idxResp = await fetch(indexUrl);
        if (idxResp.ok) existing = await idxResp.json();
      } catch(e) { console.log('§SHARE index.json fetch skip — ' + e.message); }
      existing.push({
        filename: filename,
        elements: meta.elementCount,
        disciplines: Object.keys(meta.disciplines || {}),
        date: new Date().toISOString(),
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone
      });
      var idxBlob = new Blob([JSON.stringify(existing)], { type: 'application/json' });
      await fetch(indexUrl, { method: 'PUT', body: idxBlob });
      console.log('§SHARE index.json updated entries=' + existing.length);

      // Build shareable URL
      var buildingsBase = A.CONTRIBUTE_PAR.replace(/contributed\/$/, 'buildings/');
      var viewerBase = A.CONTRIBUTE_PAR.replace(/contributed\/$/, 'sandbox/index.html');
      var shareUrl = viewerBase + '?db=' + encodeURIComponent(buildingsBase + filename);

      statusEl.textContent = 'Contributed! Share the link below.';

      // Activate send section
      if (sendSection) {
        sendSection.classList.add('active');
        sendSection.dataset.shareUrl = shareUrl;
        sendSection.dataset.buildingName = meta.filename || meta.name || key;
      }

      return shareUrl;
    } catch(e) {
      statusEl.textContent = 'Upload error: ' + e.message;
      console.log('§SHARE contribute error ' + e.message);
      return null;
    }
  }

  // ── Send to channels ──
  function copyLink(url, statusEl) {
    navigator.clipboard.writeText(url).then(function() {
      statusEl.textContent = 'Link copied!';
      console.log('§SHARE copyLink url=' + url);
    }).catch(function() {
      // Fallback for older browsers
      var ta = document.createElement('textarea');
      ta.value = url;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
      statusEl.textContent = 'Link copied!';
    });
  }

  function sendWhatsApp(url, buildingName) {
    var msg = 'View this building in your browser (no install):\n' + url;
    window.open('https://wa.me/?text=' + encodeURIComponent(msg), '_blank');
    console.log('§SHARE whatsapp building=' + buildingName);
  }

  function sendEmail(url, buildingName) {
    var subject = 'BIM Model: ' + buildingName;
    var body = 'View this building in your browser — no install, no login:\n\n' + url;
    window.open('mailto:?subject=' + encodeURIComponent(subject) + '&body=' + encodeURIComponent(body));
    console.log('§SHARE email building=' + buildingName);
  }

  // ── Share Sheet UI ──
  A.openShareSheet = async function(key) {
    injectStyle();

    var record = await A._getImport(key);
    if (!record) { alert('Building not found in storage'); return; }

    var meta = record.meta || {};
    var displayName = (meta.filename || meta.name || key).replace(/\.[^.]+$/, '');

    // Build overlay
    var overlay = document.createElement('div');
    overlay.className = 'share-overlay';

    var sheet = document.createElement('div');
    sheet.className = 'share-sheet';

    sheet.innerHTML =
      '<div class="share-header">' +
        '<h3>Share: ' + displayName + '</h3>' +
        '<button class="share-close" data-share-close>&times;</button>' +
      '</div>' +

      // Save section
      '<p class="share-section-label">Save as</p>' +
      '<div class="share-section">' +
        '<button class="share-btn" data-share-ifc>' +
          '<span class="share-icon" style="background:rgba(79,195,247,0.15);color:#4fc3f7">IFC</span>' +
          '<span class="share-label">IFC File<span class="share-sublabel">Industry Foundation Classes (.ifc)</span></span>' +
        '</button>' +
        '<button class="share-btn" data-share-db>' +
          '<span class="share-icon" style="background:rgba(156,39,176,0.15);color:#ce93d8">DB</span>' +
          '<span class="share-label">SQLite Database<span class="share-sublabel">Extracted geometry + metadata (.db)</span></span>' +
        '</button>' +
        '<button class="share-btn" data-share-ootb>' +
          '<span class="share-icon" style="background:rgba(76,175,80,0.15);color:#4caf50">&#9729;</span>' +
          '<span class="share-label">Contribute to OOTB Gallery<span class="share-sublabel">Save &amp; upload — anyone can view via link</span></span>' +
        '</button>' +
      '</div>' +

      '<hr class="share-divider">' +

      // Send section (greyed out until contribute succeeds)
      '<p class="share-section-label">Send to</p>' +
      '<div class="share-section share-send-section" data-send-section>' +
        '<button class="share-btn" data-share-copy>' +
          '<span class="share-icon" style="background:rgba(255,255,255,0.08);color:#aaa">&#128279;</span>' +
          '<span class="share-label">Copy Link</span>' +
        '</button>' +
        '<button class="share-btn" data-share-wa>' +
          '<span class="share-icon" style="background:rgba(37,211,102,0.15);color:#25d366">&#9993;</span>' +
          '<span class="share-label">WhatsApp</span>' +
        '</button>' +
        '<button class="share-btn" data-share-email>' +
          '<span class="share-icon" style="background:rgba(66,133,244,0.15);color:#4285f4">&#9993;</span>' +
          '<span class="share-label">Email</span>' +
        '</button>' +
      '</div>' +

      '<div class="share-status" data-share-status></div>';

    overlay.appendChild(sheet);
    document.body.appendChild(overlay);

    // References
    var statusEl = sheet.querySelector('[data-share-status]');
    var sendSection = sheet.querySelector('[data-send-section]');

    // Check if building is already on OCI (has a viewer URL)
    var existingUrl = null;
    var dbParam = new URLSearchParams(location.search).get('db');
    if (dbParam && dbParam.startsWith('http')) {
      existingUrl = location.href.split('#')[0];
      sendSection.classList.add('active');
      sendSection.dataset.shareUrl = existingUrl;
      sendSection.dataset.buildingName = displayName;
      statusEl.textContent = 'This building is already online — share it below.';
    }

    // Close handlers
    function close() { if (overlay.parentNode) document.body.removeChild(overlay); }
    sheet.querySelector('[data-share-close]').onclick = close;
    overlay.onclick = function(e) { if (e.target === overlay) close(); };

    // Save as IFC
    sheet.querySelector('[data-share-ifc]').onclick = function() {
      saveAsIFC(key, statusEl);
    };

    // Save as DB
    sheet.querySelector('[data-share-db]').onclick = function() {
      saveAsDB(record, key, statusEl);
    };

    // Contribute to OOTB
    sheet.querySelector('[data-share-ootb]').onclick = async function() {
      var url = await contributeToOOTB(record, key, statusEl, sendSection);
      if (url) existingUrl = url;
    };

    // Send buttons
    sheet.querySelector('[data-share-copy]').onclick = function() {
      var url = sendSection.dataset.shareUrl;
      if (url) copyLink(url, statusEl);
    };
    sheet.querySelector('[data-share-wa]').onclick = function() {
      var url = sendSection.dataset.shareUrl;
      var name = sendSection.dataset.buildingName || displayName;
      if (url) sendWhatsApp(url, name);
    };
    sheet.querySelector('[data-share-email]').onclick = function() {
      var url = sendSection.dataset.shareUrl;
      var name = sendSection.dataset.buildingName || displayName;
      if (url) sendEmail(url, name);
    };
  };

  console.log('§SHARE_LOADED share.js lazy-loaded');
})(window.A || (window.A = {}));
