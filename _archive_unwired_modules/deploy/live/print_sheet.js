/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
/**
 * print_sheet.js — A3 Landscape Print Sheet for 2D Plan/Elevation Views
 *
 * Lazy-loaded from Photo button when in 2D grid mode.
 * Captures the current Three.js view, composites a title block with
 * building name, storey, date, scale bar, north arrow, and Info Card
 * data (element counts, volume, area) — all in B&W/grey for clean printing.
 *
 * API: PrintSheet.capture(APP)  — renders A3 canvas and triggers download
 *
 * Log tags: §PRINT_SHEET
 */
var PrintSheet = (function() {
  'use strict';

  // A3 landscape at 150 DPI: 420mm x 297mm
  var A3_W = Math.round(420 * 150 / 25.4); // ~2480 px
  var A3_H = Math.round(297 * 150 / 25.4); // ~1754 px
  var MARGIN = 40; // px margin inside sheet

  function log(msg) { console.log('[PrintSheet] ' + msg); }

  /** Query building info from DB for the Info Card */
  function queryBuildingInfo(A) {
    var info = {
      name: A.activeBuilding || 'Unknown Building',
      storey: '',
      volume: 0,
      floorArea: 0,
      classCounts: [],
      totalElements: 0
    };
    if (!A.db) return info;

    // Current view storey
    var view = (typeof GridViews !== 'undefined') ? GridViews.activeView() : null;
    if (view === 'floor') info.storey = 'Ground Floor';
    else if (view === 'floor1') info.storey = 'First Floor';
    else if (view) info.storey = view.charAt(0).toUpperCase() + view.slice(1) + ' Elevation';

    // Building envelope for volume/area
    try {
      var envResult = A.db.exec(
        "SELECT MIN(center_x), MAX(center_x), MIN(center_y), MAX(center_y), " +
        "MIN(center_z), MAX(center_z) FROM element_transforms"
      );
      if (envResult.length > 0) {
        var v = envResult[0].values[0];
        var w = v[1] - v[0], d = v[3] - v[2], h = v[5] - v[4];
        info.volume = w * d * h;
        info.floorArea = w * d;
      }
    } catch (e) { /* skip */ }

    // Class counts
    try {
      var classResult = A.db.exec(
        "SELECT ifc_class, COUNT(*) as n FROM elements_meta " +
        "GROUP BY ifc_class ORDER BY n DESC LIMIT 8"
      );
      if (classResult.length > 0) {
        var rows = classResult[0].values;
        for (var i = 0; i < rows.length; i++) {
          info.classCounts.push({ cls: rows[i][0], count: rows[i][1] });
          info.totalElements += rows[i][1];
        }
      }
    } catch (e) { /* skip */ }

    return info;
  }

  /** Draw the title block panel at bottom of sheet */
  function drawTitleBlock(ctx, info, sheetW, sheetH) {
    var tbH = 120;       // title block height
    var tbY = sheetH - MARGIN - tbH;
    var tbX = MARGIN;
    var tbW = sheetW - MARGIN * 2;

    // Background
    ctx.fillStyle = '#f8f8f8';
    ctx.fillRect(tbX, tbY, tbW, tbH);

    // Border
    ctx.strokeStyle = '#000000';
    ctx.lineWidth = 2;
    ctx.strokeRect(tbX, tbY, tbW, tbH);

    // Vertical divider at 65%
    var divX = tbX + tbW * 0.65;
    ctx.beginPath();
    ctx.moveTo(divX, tbY);
    ctx.lineTo(divX, tbY + tbH);
    ctx.stroke();

    // Horizontal divider
    var hDivY = tbY + 50;
    ctx.beginPath();
    ctx.moveTo(tbX, hDivY);
    ctx.lineTo(divX, hDivY);
    ctx.stroke();

    // Building name (large)
    ctx.fillStyle = '#000000';
    ctx.font = 'bold 22px sans-serif';
    ctx.fillText(info.name, tbX + 12, tbY + 30);

    // Storey / view name
    ctx.font = '14px sans-serif';
    ctx.fillStyle = '#333333';
    ctx.fillText(info.storey || 'Plan View', tbX + 12, tbY + 46);

    // Date
    ctx.font = '12px sans-serif';
    ctx.fillStyle = '#555555';
    var dateStr = new Date().toISOString().slice(0, 10);
    ctx.fillText('Date: ' + dateStr, tbX + 12, tbY + 70);

    // Volume + area
    ctx.fillText('Vol: ' + info.volume.toFixed(1) + ' m\u00B3', tbX + 12, tbY + 88);
    ctx.fillText('Floor: ' + info.floorArea.toFixed(1) + ' m\u00B2', tbX + 180, tbY + 88);

    // Total elements
    ctx.fillText('Elements: ' + info.totalElements, tbX + 12, tbY + 106);

    // Right panel: BIM OOTB branding
    ctx.font = 'bold 18px sans-serif';
    ctx.fillStyle = '#000000';
    ctx.fillText('BIM OOTB', divX + 12, tbY + 30);

    ctx.font = '11px sans-serif';
    ctx.fillStyle = '#666666';
    ctx.fillText('Frictionless BIM', divX + 12, tbY + 48);
    ctx.fillText('Two DBs. One browser. Zero install.', divX + 12, tbY + 64);

    // Class counts (right column)
    var ccY = tbY + 82;
    ctx.font = '10px monospace';
    ctx.fillStyle = '#333333';
    var maxCC = Math.min(info.classCounts.length, 4);
    for (var i = 0; i < maxCC; i++) {
      var cc = info.classCounts[i];
      var shortName = cc.cls.replace('Ifc', '');
      ctx.fillText(shortName + ': ' + cc.count, divX + 12, ccY + i * 13);
    }

    return tbY; // return top of title block for viewport calc
  }

  /** Draw scale bar at bottom-left of viewport area */
  function drawScaleBar(ctx, viewportW, cam, tbY) {
    if (!cam || !cam.isOrthographicCamera) return;
    var visW = (cam.right - cam.left) / (cam.zoom || 1);
    // Choose round bar length
    var steps = [0.5, 1, 2, 5, 10, 20, 50, 100];
    var targetFrac = 0.15; // 15% of viewport width
    var targetM = visW * targetFrac;
    var barM = steps.reduce(function(best, s) {
      return Math.abs(s - targetM) < Math.abs(best - targetM) ? s : best;
    }, steps[0]);

    var barPx = (barM / visW) * viewportW;
    var sbX = MARGIN + 20;
    var sbY = tbY - 25;

    ctx.strokeStyle = '#000000';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(sbX, sbY);
    ctx.lineTo(sbX + barPx, sbY);
    ctx.stroke();

    // Ticks
    ctx.beginPath();
    ctx.moveTo(sbX, sbY - 6);
    ctx.lineTo(sbX, sbY + 6);
    ctx.moveTo(sbX + barPx, sbY - 6);
    ctx.lineTo(sbX + barPx, sbY + 6);
    ctx.stroke();

    // Labels
    ctx.font = '11px sans-serif';
    ctx.fillStyle = '#000000';
    ctx.textAlign = 'center';
    ctx.fillText('0', sbX, sbY - 10);
    var label = barM >= 1 ? barM.toFixed(0) + 'm' : (barM * 100).toFixed(0) + 'cm';
    ctx.fillText(label, sbX + barPx, sbY - 10);
    ctx.textAlign = 'left'; // reset
  }

  /** Draw north arrow at top-right of viewport area */
  function drawNorthArrow(ctx, sheetW) {
    var nX = sheetW - MARGIN - 30;
    var nY = MARGIN + 50;
    var len = 30;

    ctx.strokeStyle = '#000000';
    ctx.lineWidth = 2;
    // Shaft
    ctx.beginPath();
    ctx.moveTo(nX, nY + len);
    ctx.lineTo(nX, nY);
    ctx.stroke();
    // Arrowhead
    ctx.beginPath();
    ctx.moveTo(nX, nY);
    ctx.lineTo(nX - 8, nY + 12);
    ctx.moveTo(nX, nY);
    ctx.lineTo(nX + 8, nY + 12);
    ctx.stroke();
    // N
    ctx.font = 'bold 16px sans-serif';
    ctx.fillStyle = '#000000';
    ctx.textAlign = 'center';
    ctx.fillText('N', nX, nY - 6);
    ctx.textAlign = 'left';
  }

  /** Main capture function — composites everything onto an A3 canvas */
  function capture(A) {
    log('§PRINT_SHEET start');

    // 1. Render the 3D scene to capture current view
    A.renderer.render(A.scene, A.camera);
    var sceneDataURL = A.canvas.toDataURL('image/png');

    // 2. Query building info
    var info = queryBuildingInfo(A);

    // 3. Create A3 canvas
    var canvas = document.createElement('canvas');
    canvas.width = A3_W;
    canvas.height = A3_H;
    var ctx = canvas.getContext('2d');

    // White background
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, A3_W, A3_H);

    // Border
    ctx.strokeStyle = '#000000';
    ctx.lineWidth = 3;
    ctx.strokeRect(MARGIN / 2, MARGIN / 2, A3_W - MARGIN, A3_H - MARGIN);

    // 4. Draw title block (returns top Y for viewport area)
    var tbTop = drawTitleBlock(ctx, info, A3_W, A3_H);

    // 5. Load scene image and composite into viewport area
    var img = new Image();
    img.onload = function() {
      // Viewport area: from margin to title block top, with margins
      var vpX = MARGIN;
      var vpY = MARGIN;
      var vpW = A3_W - MARGIN * 2;
      var vpH = tbTop - MARGIN - 15; // 15px gap above title block

      // Draw scene image fitted into viewport (maintain aspect ratio)
      var srcAspect = img.width / img.height;
      var dstAspect = vpW / vpH;
      var drawW, drawH, drawX, drawY;
      if (srcAspect > dstAspect) {
        drawW = vpW;
        drawH = vpW / srcAspect;
        drawX = vpX;
        drawY = vpY + (vpH - drawH) / 2;
      } else {
        drawH = vpH;
        drawW = vpH * srcAspect;
        drawX = vpX + (vpW - drawW) / 2;
        drawY = vpY;
      }

      // Apply greyscale filter for B&W print
      ctx.filter = 'grayscale(80%) contrast(120%)';
      ctx.drawImage(img, drawX, drawY, drawW, drawH);
      ctx.filter = 'none';

      // Viewport border
      ctx.strokeStyle = '#333333';
      ctx.lineWidth = 1;
      ctx.strokeRect(drawX, drawY, drawW, drawH);

      // 6. Scale bar
      drawScaleBar(ctx, drawW, A.camera, tbTop);

      // 7. North arrow
      drawNorthArrow(ctx, A3_W);

      // 8. Show preview overlay
      var viewName = info.storey || 'View';
      var fileName = 'BIM_OOTB_' + info.name.replace(/\s+/g, '_') + '_' +
        viewName.replace(/\s+/g, '_') + '_' +
        new Date().toISOString().slice(0, 10) + '.png';
      var dataURL = canvas.toDataURL('image/png');
      showPreview(A, dataURL, fileName, info);
      log('§PRINT_SHEET preview name=' + info.name + ' view=' + viewName +
          ' size=' + A3_W + 'x' + A3_H);
    };
    img.src = sceneDataURL;
  }

  /** Full-screen preview overlay with Save / Close buttons */
  function showPreview(A, dataURL, fileName, info) {
    // Remove any existing preview
    var old = document.getElementById('print-preview-overlay');
    if (old) old.remove();

    var overlay = document.createElement('div');
    overlay.id = 'print-preview-overlay';
    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100vw;height:100vh;' +
      'background:rgba(0,0,0,0.85);z-index:9999;display:flex;flex-direction:column;' +
      'align-items:center;justify-content:center;padding:20px;box-sizing:border-box';

    // Header bar
    var header = document.createElement('div');
    header.style.cssText = 'display:flex;gap:12px;margin-bottom:12px;align-items:center';

    var title = document.createElement('span');
    title.style.cssText = 'color:#fff;font-size:14px;font-weight:bold';
    title.textContent = 'A3 Print Preview — ' + info.name + ' / ' + (info.storey || 'View');
    header.appendChild(title);

    // Save button
    var saveBtn = document.createElement('button');
    saveBtn.textContent = 'Save PNG';
    saveBtn.style.cssText = 'background:#4fc3f7;color:#000;border:none;border-radius:4px;' +
      'padding:8px 20px;font-size:13px;font-weight:bold;cursor:pointer';
    saveBtn.onclick = function() {
      var link = document.createElement('a');
      link.download = fileName;
      link.href = dataURL;
      link.click();
      if (A.status) A.status.textContent = 'Print sheet saved — ' + fileName;
    };
    header.appendChild(saveBtn);

    // Close button
    var closeBtn = document.createElement('button');
    closeBtn.textContent = 'Close';
    closeBtn.style.cssText = 'background:#666;color:#fff;border:none;border-radius:4px;' +
      'padding:8px 16px;font-size:13px;cursor:pointer';
    closeBtn.onclick = function() { overlay.remove(); };
    header.appendChild(closeBtn);

    overlay.appendChild(header);

    // Preview image — fitted to screen
    var img = document.createElement('img');
    img.src = dataURL;
    img.style.cssText = 'max-width:95vw;max-height:calc(100vh - 80px);border:2px solid #444;' +
      'box-shadow:0 4px 24px rgba(0,0,0,0.5);object-fit:contain';
    overlay.appendChild(img);

    // Click outside image or press Escape to close
    overlay.addEventListener('pointerup', function(e) {
      if (e.target === overlay) overlay.remove();
    });
    var escHandler = function(e) {
      if (e.key === 'Escape') { overlay.remove(); document.removeEventListener('keydown', escHandler); }
    };
    document.addEventListener('keydown', escHandler);

    document.body.appendChild(overlay);
    log('§PRINT_SHEET preview shown');
  }

  return { capture: capture };
})();
