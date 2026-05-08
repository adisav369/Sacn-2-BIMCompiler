// cost_panel.js — Implementing 2D_029 §5 — Witness: W-2D29
// Live BOQ panel: spatial query scoped to current grid positions.
// Refreshes on grid drag; shows element counts, areas, volumes.
(function () {
  'use strict';

  var panel = null;

  function createPanel() {
    panel = document.createElement('div');
    panel.id = 'costPanel';
    panel.style.cssText =
      'position:fixed; bottom:60px; right:12px; width:280px; ' +
      'max-height:320px; overflow-y:auto; background:rgba(30,30,30,0.92); ' +
      'color:#eee; font:11px/1.4 monospace; padding:10px; border-radius:6px; ' +
      'pointer-events:auto; z-index:800; display:none;';
    // Close button
    var closeBtn = document.createElement('span');
    closeBtn.textContent = '\u2715';
    closeBtn.style.cssText =
      'position:absolute; top:4px; right:8px; cursor:pointer; font-size:14px; ' +
      'color:#888; z-index:801;';
    closeBtn.title = 'Close cost panel';
    closeBtn.addEventListener('pointerup', function () { hide(); });
    panel.style.position = 'fixed';  // ensure absolute child works
    panel.appendChild(closeBtn);
    document.body.appendChild(panel);
    return panel;
  }

  /**
   * Refresh cost panel with BOQ scoped to current grid bounding box.
   * @param {Object} APP      - viewer app (needs .db)
   * @param {Object} gridData - { xLines: [{position}], yLines: [{position}] }
   */
  function refresh(APP, gridData) {
    if (!APP || !APP.db || !gridData) return;
    if (!panel) createPanel();

    var xs = gridData.xLines.map(function (l) { return l.position; });
    var ys = gridData.yLines.map(function (l) { return l.position; });
    if (!xs.length || !ys.length) {
      panel.innerHTML = '<i>No grid lines detected</i>';
      panel.style.display = 'block';
      return;
    }
    var x1 = Math.min.apply(null, xs), x2 = Math.max.apply(null, xs);
    var y1 = Math.min.apply(null, ys), y2 = Math.max.apply(null, ys);

    var sql =
      'SELECT m.ifc_class, COUNT(*) AS qty, ' +
      'ROUND(SUM(t.bbox_x * t.bbox_y), 2) AS area_m2, ' +
      'ROUND(SUM(t.bbox_x * t.bbox_y * t.bbox_z), 3) AS vol_m3 ' +
      'FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid ' +
      'WHERE t.center_x BETWEEN ? AND ? AND t.center_y BETWEEN ? AND ? ' +
      'GROUP BY m.ifc_class ORDER BY vol_m3 DESC';

    var r;
    try { r = APP.db.exec(sql, [x1, x2, y1, y2]); }
    catch (e) { panel.innerHTML = '<i>Query error: ' + e.message + '</i>'; panel.style.display = 'block'; return; }

    if (!r.length || !r[0].values.length) {
      panel.innerHTML = '<i>No elements in grid scope</i>';
      panel.style.display = 'block';
      return;
    }

    var totalQty = 0, totalArea = 0, totalVol = 0;
    var html = '<b>Bay Scope</b> X[' + x1.toFixed(1) + '\u2013' + x2.toFixed(1) +
               '] Y[' + y1.toFixed(1) + '\u2013' + y2.toFixed(1) + ']<br><br>';
    html += '<table style="width:100%;border-collapse:collapse">' +
            '<tr><th style="text-align:left">Class</th><th>Qty</th><th>Area&nbsp;m\u00B2</th><th>Vol&nbsp;m\u00B3</th></tr>';
    r[0].values.forEach(function (row) {
      var cls = (row[0] || '').replace('Ifc', '');
      html += '<tr><td>' + cls + '</td><td style="text-align:right">' + row[1] +
              '</td><td style="text-align:right">' + (row[2] || 0) +
              '</td><td style="text-align:right">' + (row[3] || 0) + '</td></tr>';
      totalQty  += (row[1] || 0);
      totalArea += (row[2] || 0);
      totalVol  += (row[3] || 0);
    });
    html += '<tr style="border-top:1px solid #666"><td><b>Total</b></td>' +
            '<td style="text-align:right"><b>' + totalQty + '</b></td>' +
            '<td style="text-align:right"><b>' + totalArea.toFixed(2) + '</b></td>' +
            '<td style="text-align:right"><b>' + totalVol.toFixed(3) + '</b></td></tr>';
    html += '</table>';

    console.log('§GRID_3D_BOQ elements=' + totalQty +
                ' area=' + totalArea.toFixed(2) + ' vol=' + totalVol.toFixed(3));

    panel.innerHTML = html;
    panel.style.display = 'block';
  }

  function hide() {
    if (panel) panel.style.display = 'none';
  }

  function isVisible() {
    return panel && panel.style.display !== 'none';
  }

  window.CostPanel = { refresh: refresh, hide: hide, isVisible: isVisible };
})();
