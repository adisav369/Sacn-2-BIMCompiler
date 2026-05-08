/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
/**
 * print_sheet.js — Interactive A3 Print Preview (2D_025 D3)
 *
 * Implementing 2D_025 spec §D3 — Witness: W-PRINT
 *
 * Captures the current Three.js view and shows an interactive A3 preview:
 *   - Left panel: live A3 canvas (2480x1754px, CSS-scaled for display)
 *   - Right panel: editable text fields, contrast slider, corporate info
 *   - Title bar: cursor:grab, "Print Preview" label
 *   - Save PNG button downloads the full A3 canvas
 *   - Corporate details loaded from corporate.json
 *
 * API: PrintSheet.capture(APP)
 *
 * Log tags: §PRINT_SHEET
 */
var PrintSheet = (function() {
  'use strict';

  // A3 landscape at 150 DPI: 420mm x 297mm
  var A3_W = Math.round(420 * 150 / 25.4); // ~2480 px
  var A3_H = Math.round(297 * 150 / 25.4); // ~1754 px
  var MARGIN = 40; // px margin inside sheet

  var _corp = null; // cached corporate.json

  function log(msg) { console.log('[PrintSheet] ' + msg); }

  // ── Corporate JSON ────────────────────────────────────────────────

  /** Load corporate.json once and cache. Calls cb(corp). Falls back gracefully. */
  function loadCorporate(cb) {
    if (_corp) { cb(_corp); return; }
    fetch('corporate.json')
      .then(function(r) { return r.json(); })
      .then(function(d) { _corp = d; cb(_corp); log('§PRINT_SHEET corp loaded company=' + d.company); })
      .catch(function() {
        _corp = { company: 'BIM OOTB', logo_text: 'BIM OOTB', address: '', phone: '', email: '', website: '' };
        cb(_corp);
        log('§PRINT_SHEET corp fallback — corporate.json not found');
      });
  }

  // ── Building Info ─────────────────────────────────────────────────

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

    var view = (typeof GridViews !== 'undefined') ? GridViews.activeView() : null;
    if (view === 'floor') info.storey = 'Ground Floor';
    else if (view === 'floor1') info.storey = 'First Floor';
    else if (view) info.storey = view.charAt(0).toUpperCase() + view.slice(1) + ' Elevation';

    try {
      var envResult = A.db.exec(
        'SELECT MIN(center_x),MAX(center_x),MIN(center_y),MAX(center_y),MIN(center_z),MAX(center_z) FROM element_transforms'
      );
      if (envResult.length > 0) {
        var v = envResult[0].values[0];
        var w = v[1] - v[0], d = v[3] - v[2], h = v[5] - v[4];
        info.volume = w * d * h;
        info.floorArea = w * d;
      }
    } catch (e) { /* skip */ }

    try {
      var classResult = A.db.exec(
        'SELECT ifc_class,COUNT(*) as n FROM elements_meta GROUP BY ifc_class ORDER BY n DESC LIMIT 8'
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

  // ── Sheet Rendering ───────────────────────────────────────────────

  /** Draw title block at bottom of sheet. Returns top-Y of title block. */
  function drawTitleBlock(ctx, info, opts) {
    var corp = opts.corp || {};
    var tbH = 120;
    var tbY = A3_H - MARGIN - tbH;
    var tbX = MARGIN;
    var tbW = A3_W - MARGIN * 2;

    ctx.fillStyle = '#f8f8f8';
    ctx.fillRect(tbX, tbY, tbW, tbH);
    ctx.strokeStyle = '#000000';
    ctx.lineWidth = 2;
    ctx.strokeRect(tbX, tbY, tbW, tbH);

    // Vertical divider at 65%
    var divX = tbX + tbW * 0.65;
    ctx.beginPath();
    ctx.moveTo(divX, tbY); ctx.lineTo(divX, tbY + tbH);
    ctx.stroke();

    // Horizontal divider (stats row)
    var hDivY = tbY + 50;
    ctx.beginPath();
    ctx.moveTo(tbX, hDivY); ctx.lineTo(divX, hDivY);
    ctx.stroke();

    // Left cell — title
    ctx.fillStyle = '#000000';
    ctx.font = 'bold 22px sans-serif';
    ctx.fillText(opts.title || info.name, tbX + 12, tbY + 30);

    ctx.font = '14px sans-serif';
    ctx.fillStyle = '#333333';
    ctx.fillText(opts.subtitle || info.storey || 'Plan View', tbX + 12, tbY + 46);

    // Left cell — meta
    ctx.font = '12px sans-serif';
    ctx.fillStyle = '#555555';
    var dateStr = new Date().toISOString().slice(0, 10);
    var metaLine = 'Date: ' + dateStr;
    if (opts.drawnBy) metaLine += '  |  Drawn: ' + opts.drawnBy;
    if (opts.checkedBy) metaLine += '  Checked: ' + opts.checkedBy;
    ctx.fillText(metaLine, tbX + 12, tbY + 70);
    if (opts.notes) ctx.fillText('Notes: ' + opts.notes.slice(0, 80), tbX + 12, tbY + 86);

    // Stats row
    ctx.fillStyle = '#555555';
    ctx.fillText('Vol: ' + info.volume.toFixed(1) + ' m\u00B3', tbX + 12, tbY + 106);
    ctx.fillText('Floor: ' + info.floorArea.toFixed(1) + ' m\u00B2', tbX + 180, tbY + 106);
    ctx.fillText('Elements: ' + info.totalElements, tbX + 350, tbY + 106);

    // Right cell — corporate
    ctx.font = 'bold 18px sans-serif';
    ctx.fillStyle = '#000000';
    ctx.fillText(corp.company || corp.logo_text || 'BIM OOTB', divX + 12, tbY + 24);
    ctx.font = '11px sans-serif';
    ctx.fillStyle = '#444444';
    if (corp.address) ctx.fillText(corp.address.slice(0, 46), divX + 12, tbY + 42);
    if (corp.phone) ctx.fillText(corp.phone, divX + 12, tbY + 57);
    if (corp.email) ctx.fillText(corp.email, divX + 12, tbY + 71);
    if (corp.website) ctx.fillText(corp.website, divX + 12, tbY + 85);
    if (corp.registration) {
      ctx.font = '9px sans-serif'; ctx.fillStyle = '#888888';
      ctx.fillText(corp.registration, divX + 12, tbY + 100);
    }

    return tbY; // top of title block
  }

  /** Draw scale bar at bottom-left of viewport area */
  function drawScaleBar(ctx, vpW, vpX, cam, tbY) {
    if (!cam || !cam.isOrthographicCamera) return;
    var visW = (cam.right - cam.left) / (cam.zoom || 1);
    var steps = [0.5, 1, 2, 5, 10, 20, 50, 100];
    var targetM = visW * 0.15;
    var barM = steps.reduce(function(best, s) {
      return Math.abs(s - targetM) < Math.abs(best - targetM) ? s : best;
    }, steps[0]);
    var barPx = (barM / visW) * vpW;
    var sbX = vpX + 20;
    var sbY = tbY - 25;

    ctx.strokeStyle = '#000000'; ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(sbX, sbY); ctx.lineTo(sbX + barPx, sbY);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(sbX, sbY - 6); ctx.lineTo(sbX, sbY + 6);
    ctx.moveTo(sbX + barPx, sbY - 6); ctx.lineTo(sbX + barPx, sbY + 6);
    ctx.stroke();

    ctx.font = '11px sans-serif'; ctx.fillStyle = '#000000'; ctx.textAlign = 'center';
    ctx.fillText('0', sbX, sbY - 10);
    var barLabel = barM >= 1 ? barM.toFixed(0) + 'm' : (barM * 100).toFixed(0) + 'cm';
    ctx.fillText(barLabel, sbX + barPx, sbY - 10);
    ctx.textAlign = 'left';
  }

  /** Draw north arrow at top-right of viewport */
  function drawNorthArrow(ctx) {
    var nX = A3_W - MARGIN - 30;
    var nY = MARGIN + 50;
    var len = 30;

    ctx.strokeStyle = '#000000'; ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(nX, nY + len); ctx.lineTo(nX, nY);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(nX, nY); ctx.lineTo(nX - 8, nY + 12);
    ctx.moveTo(nX, nY); ctx.lineTo(nX + 8, nY + 12);
    ctx.stroke();

    ctx.font = 'bold 16px sans-serif'; ctx.fillStyle = '#000000';
    ctx.textAlign = 'center';
    ctx.fillText('N', nX, nY - 6);
    ctx.textAlign = 'left';
  }

  /**
   * Render the full A3 sheet onto ctx.
   * opts = { title, subtitle, notes, drawnBy, checkedBy, contrast (0-100), corp }
   */
  function drawSheet(ctx, cam, sceneImg, info, opts) {
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, A3_W, A3_H);

    ctx.strokeStyle = '#000000'; ctx.lineWidth = 3;
    ctx.strokeRect(MARGIN / 2, MARGIN / 2, A3_W - MARGIN, A3_H - MARGIN);

    var tbTop = drawTitleBlock(ctx, info, opts);

    // Viewport area
    var vpX = MARGIN, vpY = MARGIN;
    var vpW = A3_W - MARGIN * 2;
    var vpH = tbTop - MARGIN - 15;

    // Fit scene image (maintain aspect ratio)
    var srcAspect = sceneImg.width / sceneImg.height;
    var dstAspect = vpW / vpH;
    var drawW, drawH, drawX, drawY;
    if (srcAspect > dstAspect) {
      drawW = vpW; drawH = vpW / srcAspect;
      drawX = vpX; drawY = vpY + (vpH - drawH) / 2;
    } else {
      drawH = vpH; drawW = vpH * srcAspect;
      drawX = vpX + (vpW - drawW) / 2; drawY = vpY;
    }

    // Apply contrast/greyscale filter
    var contrast = opts.contrast || 0; // 0-100
    var greyPct = Math.round(contrast * 0.8);          // 0→0, 100→80%
    var contrastPct = 100 + Math.round(contrast * 0.2); // 100→120%
    ctx.filter = 'grayscale(' + greyPct + '%) contrast(' + contrastPct + '%)';
    ctx.drawImage(sceneImg, drawX, drawY, drawW, drawH);
    ctx.filter = 'none';

    ctx.strokeStyle = '#333333'; ctx.lineWidth = 1;
    ctx.strokeRect(drawX, drawY, drawW, drawH);

    drawScaleBar(ctx, drawW, drawX, cam, tbTop);
    drawNorthArrow(ctx);

    log('§PRINT_SHEET drawSheet contrast=' + contrast + ' vp=' + Math.round(drawW) + 'x' + Math.round(drawH));
  }

  // ── Interactive Preview ───────────────────────────────────────────

  function showPreview(A, sceneImg, info, corp) {
    var old = document.getElementById('print-preview-overlay');
    if (old) old.remove();

    // Working opts (mutated by fields/slider)
    var opts = {
      title: info.name,
      subtitle: info.storey || 'Plan View',
      notes: '',
      drawnBy: corp.drawn_by || '',
      checkedBy: corp.checked_by || '',
      contrast: 0,
      corp: corp
    };

    // ── Overlay container ─────────────────────────────────────────
    var overlay = document.createElement('div');
    overlay.id = 'print-preview-overlay';
    overlay.style.cssText =
      'position:fixed;top:0;left:0;width:100vw;height:100vh;' +
      'background:rgba(0,0,0,0.88);z-index:9999;overflow:auto;' +
      'display:flex;flex-direction:column;align-items:center;' +
      'padding:12px;box-sizing:border-box';

    // ── Title bar (cursor:grab for drag + "Print Preview" text) ──
    var titleBar = document.createElement('div');
    titleBar.style.cssText =
      'cursor:grab;color:#fff;font-size:13px;font-weight:bold;' +
      'padding:8px 12px;width:100%;max-width:1440px;display:flex;' +
      'align-items:center;gap:10px;background:rgba(255,255,255,0.08);' +
      'border-radius:6px 6px 0 0;margin-bottom:4px;box-sizing:border-box';

    var titleLabel = document.createElement('span');
    titleLabel.textContent = 'Print Preview \u2014 A3 Landscape';
    titleBar.appendChild(titleLabel);

    // Save PNG button
    var saveBtn = document.createElement('button');
    saveBtn.textContent = 'Save PNG';
    saveBtn.style.cssText =
      'background:#4fc3f7;color:#000;border:none;border-radius:4px;' +
      'padding:6px 16px;font-size:12px;font-weight:bold;cursor:pointer;margin-left:auto';

    // Close button
    var closeBtn = document.createElement('button');
    closeBtn.textContent = 'Close';
    closeBtn.style.cssText =
      'background:#666;color:#fff;border:none;border-radius:4px;' +
      'padding:6px 14px;font-size:12px;cursor:pointer';
    closeBtn.onclick = function() { overlay.remove(); };

    titleBar.appendChild(saveBtn);
    titleBar.appendChild(closeBtn);

    // ── Content row ───────────────────────────────────────────────
    var contentRow = document.createElement('div');
    contentRow.style.cssText =
      'display:flex;gap:12px;width:100%;max-width:1440px;align-items:flex-start';

    // ── Preview canvas (full A3 resolution, CSS-scaled) ───────────
    var previewCanvas = document.createElement('canvas');
    previewCanvas.width = A3_W;
    previewCanvas.height = A3_H;
    previewCanvas.style.cssText =
      'flex:1 1 auto;min-width:0;width:70%;max-height:calc(100vh - 120px);' +
      'object-fit:contain;border:2px solid #444;display:block';

    // ── Controls panel ────────────────────────────────────────────
    var ctrlPanel = document.createElement('div');
    ctrlPanel.style.cssText =
      'flex:0 0 250px;background:rgba(255,255,255,0.08);border-radius:6px;' +
      'padding:12px;color:#ddd;font-size:12px;box-sizing:border-box';

    var ctx = previewCanvas.getContext('2d');

    // ── Helper: make a text input field ──────────────────────────
    function makeField(labelText, value, key) {
      var row = document.createElement('div');
      row.style.cssText = 'margin-bottom:8px';
      var lbl = document.createElement('label');
      lbl.style.cssText = 'display:block;color:#aaa;font-size:11px;margin-bottom:2px';
      lbl.textContent = labelText;
      var inp = document.createElement('input');
      inp.type = 'text';
      inp.value = value || '';
      inp.style.cssText =
        'width:100%;background:#2a2a2a;border:1px solid #555;border-radius:3px;' +
        'color:#eee;padding:4px 6px;font-size:12px;box-sizing:border-box';
      inp.addEventListener('input', function() {
        opts[key] = inp.value;
        drawSheet(ctx, A.camera, sceneImg, info, opts);
        log('§PRINT_SHEET field ' + key + '=' + inp.value.slice(0, 20));
      });
      row.appendChild(lbl);
      row.appendChild(inp);
      return row;
    }

    ctrlPanel.appendChild(makeField('Title', opts.title, 'title'));
    ctrlPanel.appendChild(makeField('Subtitle', opts.subtitle, 'subtitle'));
    ctrlPanel.appendChild(makeField('Notes', opts.notes, 'notes'));
    ctrlPanel.appendChild(makeField('Drawn by', opts.drawnBy, 'drawnBy'));
    ctrlPanel.appendChild(makeField('Checked by', opts.checkedBy, 'checkedBy'));

    // Contrast slider
    var sliderRow = document.createElement('div');
    sliderRow.style.cssText = 'margin-bottom:12px';
    var sliderLbl = document.createElement('label');
    sliderLbl.style.cssText = 'display:block;color:#aaa;font-size:11px;margin-bottom:2px';
    sliderLbl.textContent = 'Contrast (0=colour \u2192 100=B&W)';
    var slider = document.createElement('input');
    slider.type = 'range';
    slider.min = '0'; slider.max = '100'; slider.value = '0';
    slider.style.cssText = 'width:100%;cursor:pointer';
    slider.addEventListener('input', function() {
      opts.contrast = parseInt(slider.value, 10);
      drawSheet(ctx, A.camera, sceneImg, info, opts);
      log('§PRINT_SHEET contrast=' + opts.contrast);
    });
    sliderRow.appendChild(sliderLbl);
    sliderRow.appendChild(slider);
    ctrlPanel.appendChild(sliderRow);

    // Corporate info display
    if (corp && corp.company) {
      var corpDiv = document.createElement('div');
      corpDiv.style.cssText =
        'margin-top:8px;padding:8px;background:rgba(0,0,0,0.3);' +
        'border-radius:4px;font-size:10px;color:#777;border:1px solid #333';
      corpDiv.innerHTML =
        '<b style="color:#999">' + corp.company + '</b><br>' +
        (corp.address ? corp.address + '<br>' : '') +
        (corp.website || '');
      ctrlPanel.appendChild(corpDiv);
    }

    contentRow.appendChild(previewCanvas);
    contentRow.appendChild(ctrlPanel);
    overlay.appendChild(titleBar);
    overlay.appendChild(contentRow);
    document.body.appendChild(overlay);

    // ── Draggable overlay via _makeDraggable ──────────────────────
    if (A._makeDraggable) {
      A._makeDraggable(overlay);
    } else {
      // Minimal inline drag on titleBar
      (function() {
        var dragging = false, ox = 0, oy = 0;
        titleBar.addEventListener('pointerdown', function(e) {
          dragging = true; ox = e.clientX; oy = e.clientY;
          titleBar.style.cursor = 'grabbing';
          e.stopPropagation();
        });
        document.addEventListener('pointermove', function(e) {
          if (!dragging) return;
          overlay.scrollLeft -= e.clientX - ox;
          overlay.scrollTop -= e.clientY - oy;
          ox = e.clientX; oy = e.clientY;
        });
        document.addEventListener('pointerup', function() {
          dragging = false;
          titleBar.style.cursor = 'grab';
        });
      })();
    }

    // ── Save PNG ──────────────────────────────────────────────────
    saveBtn.onclick = function() {
      var saveCanvas = document.createElement('canvas');
      saveCanvas.width = A3_W; saveCanvas.height = A3_H;
      var saveCtx = saveCanvas.getContext('2d');
      drawSheet(saveCtx, A.camera, sceneImg, info, opts);

      var viewName = opts.subtitle || info.storey || 'View';
      var fileName = 'BIM_OOTB_' + (opts.title || info.name).replace(/\s+/g, '_') + '_' +
        viewName.replace(/\s+/g, '_') + '_' +
        new Date().toISOString().slice(0, 10) + '.png';
      var link = document.createElement('a');
      link.download = fileName;
      link.href = saveCanvas.toDataURL('image/png');
      link.click();
      if (A.status) A.status.textContent = 'Saved: ' + fileName;
      log('§PRINT_SHEET save_png name=' + fileName + ' size=' + A3_W + 'x' + A3_H);
    };

    // ── Escape to close ───────────────────────────────────────────
    var escHandler = function(e) {
      if (e.key === 'Escape') { overlay.remove(); document.removeEventListener('keydown', escHandler); }
    };
    document.addEventListener('keydown', escHandler);

    // ── Initial draw ──────────────────────────────────────────────
    drawSheet(ctx, A.camera, sceneImg, info, opts);
    log('§PRINT_SHEET preview shown corp=' + (corp.company || 'none') +
        ' title=' + opts.title + ' view=' + opts.subtitle);
  }

  // ── Entry Point ───────────────────────────────────────────────────

  /**
   * Capture current Three.js view and open interactive A3 print preview.
   * @param {Object} A — APP object (needs renderer, canvas, camera, db, activeBuilding)
   */
  function capture(A) {
    log('§PRINT_SHEET start');
    A.renderer.render(A.scene, A.camera);
    var sceneDataURL = A.canvas.toDataURL('image/png');
    var info = queryBuildingInfo(A);

    loadCorporate(function(corp) {
      var img = new Image();
      img.onload = function() {
        showPreview(A, img, info, corp);
      };
      img.src = sceneDataURL;
    });
  }

  return { capture: capture };
})();
