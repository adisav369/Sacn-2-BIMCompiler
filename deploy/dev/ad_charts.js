// ad_charts.js — Implementing ERP_AD_UI.md §7 — Witness: W-ERP-ADUI
// Cross-table SQL → Canvas chart (bar, pie). Overlay panel.
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
(function () {
  'use strict';

  // ── Pre-built queries per window ───────────────────────────────────

  var PREBUILT = {
    'C_Project': [
      { label: 'Status Distribution', sql: "SELECT ProjectCategory, COUNT(*) as cnt FROM C_Project GROUP BY ProjectCategory ORDER BY cnt DESC" },
      { label: 'Budget vs Actual', sql: "SELECT Name, PlannedAmt, CommittedAmt FROM C_Project WHERE PlannedAmt > 0 ORDER BY PlannedAmt DESC LIMIT 10" }
    ],
    'M_Product': [
      { label: 'Products by Type', sql: "SELECT ProductType, COUNT(*) as cnt FROM M_Product GROUP BY ProductType ORDER BY cnt DESC" },
      { label: 'Price Distribution', sql: "SELECT Name, COALESCE(C_UOM_ID, 0) as uom FROM M_Product ORDER BY Name LIMIT 20" }
    ],
    'C_BPartner': [
      { label: 'Customer vs Vendor', sql: "SELECT CASE WHEN IsCustomer='Y' THEN 'Customer' ELSE 'Vendor' END as type, COUNT(*) as cnt FROM C_BPartner GROUP BY type" },
      { label: 'By Group', sql: "SELECT C_BP_Group_ID, COUNT(*) as cnt FROM C_BPartner GROUP BY C_BP_Group_ID ORDER BY cnt DESC" }
    ]
  };

  /**
   * Run a SQL query and return { columns, rows }.
   */
  function runQuery(db, sql) {
    console.log('§AD_CHARTS runQuery sql=' + sql.substring(0, 60));
    try {
      var r = db.exec(sql);
      if (!r.length) return { columns: [], rows: [] };
      return { columns: r[0].columns, rows: r[0].values };
    } catch (e) {
      console.log('§AD_CHARTS runQuery ERROR ' + e.message);
      return { columns: [], rows: [], error: e.message };
    }
  }

  /**
   * Draw a bar chart on a canvas element.
   * @param {HTMLCanvasElement} canvas
   * @param {Array} labels
   * @param {Array} values
   * @param {string} title
   */
  var PALETTE = ['#6c9fff', '#ff9f43', '#54d9a8', '#ff6b6b', '#a78bfa',
                  '#38d9d9', '#ffd93d', '#ff85a2', '#7bed9f', '#ff7043'];

  /**
   * Draw a horizontal bar chart — better for long labels.
   */
  function drawBarChart(canvas, labels, values, title) {
    var ctx = canvas.getContext('2d');
    var W = canvas.width, H = canvas.height;
    var dpr = (typeof window !== 'undefined' && window.devicePixelRatio) || 1;
    // High-DPI support
    if (canvas.dataset.scaled !== '1' && dpr > 1) {
      canvas.width = W * dpr;
      canvas.height = H * dpr;
      canvas.style.width = W + 'px';
      canvas.style.height = H + 'px';
      ctx.scale(dpr, dpr);
      canvas.dataset.scaled = '1';
    }
    ctx.clearRect(0, 0, W, H);

    var maxVal = Math.max.apply(null, values.concat([1]));
    var n = Math.min(labels.length, 12);
    var barH = Math.max(Math.floor((H - 36) / Math.max(n, 1)) - 4, 14);
    var labelW = Math.min(W * 0.3, 100); // left column for labels
    var chartW = W - labelW - 50;

    // Title
    ctx.fillStyle = '#e8e8ed';
    ctx.font = 'bold 12px system-ui';
    ctx.fillText(title || '', 8, 16);

    for (var i = 0; i < n; i++) {
      var barW = Math.round((values[i] / maxVal) * chartW);
      var y = 28 + i * (barH + 4);

      // Label (left)
      ctx.fillStyle = '#999';
      ctx.font = '11px system-ui';
      ctx.textAlign = 'right';
      var lbl = String(labels[i] || '').substring(0, 14);
      ctx.fillText(lbl, labelW - 6, y + barH * 0.7);

      // Bar with rounded ends
      ctx.fillStyle = PALETTE[i % PALETTE.length];
      _roundRect(ctx, labelW, y, Math.max(barW, 4), barH, 3);

      // Value (right of bar)
      ctx.fillStyle = '#ccc';
      ctx.font = '11px system-ui';
      ctx.textAlign = 'left';
      ctx.fillText(String(values[i]), labelW + barW + 6, y + barH * 0.7);
    }
    ctx.textAlign = 'left'; // reset

    console.log('§AD_CHARTS drawBarChart title=' + title + ' bars=' + n);
  }

  function _roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
    ctx.fill();
  }

  /**
   * Draw a pie chart on a canvas element.
   * @param {HTMLCanvasElement} canvas
   * @param {Array} labels
   * @param {Array} values
   * @param {string} title
   */
  function drawPieChart(canvas, labels, values, title) {
    var ctx = canvas.getContext('2d');
    var W = canvas.width, H = canvas.height;
    ctx.clearRect(0, 0, W, H);

    var total = values.reduce(function (a, b) { return a + b; }, 0);
    if (total === 0) return;

    var colours = PALETTE;
    var cx = W * 0.35, cy = H * 0.55, radius = Math.min(cx, cy) - 10;
    var startAngle = -Math.PI / 2;

    // Title
    ctx.fillStyle = '#eee';
    ctx.font = '13px system-ui';
    ctx.fillText(title || '', 10, 18);

    for (var i = 0; i < labels.length && i < 10; i++) {
      var sliceAngle = (values[i] / total) * 2 * Math.PI;
      ctx.beginPath();
      ctx.moveTo(cx, cy);
      ctx.arc(cx, cy, radius, startAngle, startAngle + sliceAngle);
      ctx.closePath();
      ctx.fillStyle = colours[i % colours.length];
      ctx.fill();

      // Legend on right side
      var lx = W * 0.72, ly = 34 + i * 16;
      ctx.fillStyle = colours[i % colours.length];
      ctx.fillRect(lx, ly - 8, 10, 10);
      ctx.fillStyle = '#ccc';
      ctx.font = '10px system-ui';
      var pct = Math.round((values[i] / total) * 100);
      ctx.fillText(String(labels[i] || '').substring(0, 14) + ' ' + pct + '%', lx + 14, ly);

      startAngle += sliceAngle;
    }

    console.log('§AD_CHARTS drawPieChart title=' + title + ' slices=' + labels.length);
  }

  /**
   * Get prebuilt queries for a table name.
   */
  function getPrebuilt(tableName) {
    return PREBUILT[tableName] || [];
  }

  /**
   * Render chart overlay into a container element.
   * @param {Element} containerEl
   * @param {Object}  db
   * @param {string}  tableName
   */
  function renderOverlay(containerEl, db, tableName) {
    console.log('§AD_CHARTS renderOverlay table=' + tableName);
    containerEl.innerHTML = '';
    containerEl.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:48px;' +
      'background:rgba(20,20,20,0.97);z-index:50;overflow-y:auto;padding:16px;';

    // Close button
    var closeBtn = document.createElement('button');
    closeBtn.textContent = '\u2715 Close';
    closeBtn.style.cssText = 'position:absolute;top:8px;right:12px;background:none;' +
      'border:1px solid #555;color:#ccc;padding:6px 14px;border-radius:6px;' +
      'font-size:13px;cursor:pointer;min-height:44px;';
    closeBtn.addEventListener('pointerup', function () {
      containerEl.style.display = 'none';
    });
    containerEl.appendChild(closeBtn);

    // Title
    var h = document.createElement('div');
    h.textContent = 'Charts — ' + tableName;
    h.style.cssText = 'color:#4fc3f7;font-size:16px;font-weight:bold;margin-bottom:16px;';
    containerEl.appendChild(h);

    // Prebuilt queries
    var prebuilt = getPrebuilt(tableName);
    for (var i = 0; i < prebuilt.length; i++) {
      _addChart(containerEl, db, prebuilt[i].label, prebuilt[i].sql);
    }

    // Custom SQL input
    var sqlLabel = document.createElement('div');
    sqlLabel.textContent = 'Custom SQL';
    sqlLabel.style.cssText = 'color:#888;font-size:12px;margin-top:16px;margin-bottom:4px;';
    containerEl.appendChild(sqlLabel);

    var sqlInput = document.createElement('textarea');
    sqlInput.style.cssText = 'width:100%;height:60px;background:#2a2a2a;color:#eee;' +
      'border:1px solid #444;border-radius:6px;padding:8px;font-size:12px;' +
      'font-family:monospace;resize:vertical;';
    sqlInput.placeholder = 'SELECT category, COUNT(*) FROM ...';
    containerEl.appendChild(sqlInput);

    var runBtn = document.createElement('button');
    runBtn.textContent = 'Run Query';
    runBtn.style.cssText = 'margin-top:6px;padding:8px 16px;background:none;' +
      'border:1px solid #4fc3f7;color:#4fc3f7;border-radius:6px;font-size:13px;' +
      'cursor:pointer;min-height:44px;';
    var customCanvas = document.createElement('canvas');
    customCanvas.width = 360;
    customCanvas.height = 200;
    customCanvas.style.cssText = 'display:block;margin-top:8px;';
    runBtn.addEventListener('pointerup', function () {
      var sql = sqlInput.value.trim();
      if (!sql) return;
      var result = runQuery(db, sql);
      if (result.error) {
        customCanvas.getContext('2d').clearRect(0, 0, 360, 200);
        customCanvas.getContext('2d').fillStyle = '#f44336';
        customCanvas.getContext('2d').fillText('Error: ' + result.error, 10, 30);
        return;
      }
      if (result.rows.length && result.columns.length >= 2) {
        var labels = result.rows.map(function (r) { return r[0]; });
        var values = result.rows.map(function (r) { return Number(r[1]) || 0; });
        drawBarChart(customCanvas, labels, values, 'Custom');
      }
    });
    containerEl.appendChild(runBtn);
    containerEl.appendChild(customCanvas);

    containerEl.style.display = 'block';
  }

  function _addChart(containerEl, db, label, sql) {
    var result = runQuery(db, sql);
    if (!result.rows.length || result.columns.length < 2) return;

    var canvas = document.createElement('canvas');
    canvas.width = 360;
    canvas.height = 200;
    canvas.style.cssText = 'display:block;margin-bottom:12px;';
    containerEl.appendChild(canvas);

    var labels = result.rows.map(function (r) { return r[0]; });
    var values = result.rows.map(function (r) { return Number(r[1]) || 0; });
    drawBarChart(canvas, labels, values, label);
  }

  // ── Public API ─────────────────────────────────────────────────────

  var ADCharts = {
    runQuery:      runQuery,
    drawBarChart:  drawBarChart,
    drawPieChart:  drawPieChart,
    getPrebuilt:   getPrebuilt,
    renderOverlay: renderOverlay
  };

  if (typeof window !== 'undefined') window.ADCharts = ADCharts;
  if (typeof module !== 'undefined' && module.exports) module.exports = ADCharts;

  console.log('§AD_CHARTS_LOADED v1');
})();
