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

  // ── D4: Corporate.json (lazy-loaded, cached) ─────────────────────
  var _corpCache = null;
  function loadCorporate(cb) {
    if (_corpCache) { cb(_corpCache); return; }
    fetch('corporate.json').then(function(r) { return r.json(); }).then(function(data) {
      _corpCache = data;
      cb(data);
    }).catch(function() {
      _corpCache = { company: 'BIM OOTB', address: '', phone: '', email: '', website: 'bim-ootb.com',
                     logo_text: 'BIM OOTB', registration: '', drawn_by: '', checked_by: '' };
      cb(_corpCache);
    });
  }

  // ── D3: Title block with corporate details ────────────────────────
  function drawTitleBlockCorp(ctx, info, corp, overrides, sheetW, sheetH) {
    var tbH = 150;
    var tbY = sheetH - MARGIN - tbH;
    var tbX = MARGIN;
    var tbW = sheetW - MARGIN * 2;

    ctx.fillStyle = '#f8f8f8';
    ctx.fillRect(tbX, tbY, tbW, tbH);
    ctx.strokeStyle = '#000';
    ctx.lineWidth = 2;
    ctx.strokeRect(tbX, tbY, tbW, tbH);

    // Dividers
    var divX = tbX + tbW * 0.55;
    ctx.beginPath(); ctx.moveTo(divX, tbY); ctx.lineTo(divX, tbY + tbH); ctx.stroke();
    var hDiv1 = tbY + 40;
    ctx.beginPath(); ctx.moveTo(tbX, hDiv1); ctx.lineTo(divX, hDiv1); ctx.stroke();
    var hDiv2 = tbY + 80;
    ctx.beginPath(); ctx.moveTo(tbX, hDiv2); ctx.lineTo(divX, hDiv2); ctx.stroke();
    var hDiv3 = tbY + 120;
    ctx.beginPath(); ctx.moveTo(tbX, hDiv3); ctx.lineTo(tbX + tbW, hDiv3); ctx.stroke();

    // Left panel
    ctx.fillStyle = '#000';
    ctx.font = 'bold 18px sans-serif';
    ctx.fillText(overrides.title || info.name, tbX + 10, tbY + 26);
    ctx.font = '12px sans-serif';
    ctx.fillStyle = '#333';
    ctx.fillText(overrides.subtitle || info.storey || 'Plan View', tbX + 10, tbY + 58);
    ctx.fillText('Notes: ' + (overrides.notes || ''), tbX + 10, tbY + 76);
    ctx.font = '11px sans-serif';
    ctx.fillStyle = '#555';
    ctx.fillText('Drawn: ' + (overrides.drawnBy || corp.drawn_by || ''), tbX + 10, tbY + 100);
    ctx.fillText('Checked: ' + (overrides.checkedBy || corp.checked_by || ''), tbX + 180, tbY + 100);
    ctx.fillText('Date: ' + new Date().toISOString().slice(0, 10), tbX + 10, tbY + 116);
    ctx.fillText('Scale: ' + (overrides.scale || 'Auto'), tbX + 180, tbY + 116);

    // Right panel — corporate
    ctx.font = 'bold 16px sans-serif';
    ctx.fillStyle = '#000';
    ctx.fillText(corp.logo_text || corp.company, divX + 10, tbY + 24);
    ctx.font = '10px sans-serif';
    ctx.fillStyle = '#444';
    var corpLines = [corp.company, corp.address, corp.phone, corp.email, corp.website, corp.registration];
    var cy = tbY + 42;
    for (var ci = 0; ci < corpLines.length; ci++) {
      if (corpLines[ci]) { ctx.fillText(corpLines[ci], divX + 10, cy); cy += 14; }
    }

    // Bottom strip — element counts
    ctx.font = '10px monospace';
    ctx.fillStyle = '#333';
    var strip = 'Vol: ' + info.volume.toFixed(1) + 'm³  Floor: ' + info.floorArea.toFixed(1) + 'm²  Elements: ' + info.totalElements;
    ctx.fillText(strip, tbX + 10, tbY + 138);
    var ccX = tbX + 350;
    for (var ci2 = 0; ci2 < Math.min(info.classCounts.length, 6); ci2++) {
      var cc = info.classCounts[ci2];
      ctx.fillText(cc.cls.replace('Ifc','') + ':' + cc.count, ccX, tbY + 138);
      ccX += 80;
    }

    return tbY;
  }

  // ── D3: Full interactive preview panel ───────────────────────────
  /** D3: Interactive preview — editable fields, contrast slider, draggable */
  function showPreview(A, sceneDataURL, fileName, info) {
    var old = document.getElementById('print-preview-overlay');
    if (old) old.remove();

    loadCorporate(function(corp) {
      // Editable field state
      var fields = {
        title: info.name,
        subtitle: info.storey || 'Plan View',
        notes: '',
        drawnBy: corp.drawn_by || '',
        checkedBy: corp.checked_by || '',
        scale: 'Auto',
        contrast: 80  // greyscale % (0=colour, 100=full B&W)
      };

      // Outer overlay (dark backdrop)
      var overlay = document.createElement('div');
      overlay.id = 'print-preview-overlay';
      overlay.style.cssText = 'position:fixed;top:0;left:0;width:100vw;height:100vh;' +
        'background:rgba(0,0,0,0.80);z-index:9999;overflow:auto';

      // Draggable panel
      var panel = document.createElement('div');
      panel.style.cssText = 'position:absolute;top:20px;left:50%;transform:translateX(-50%);' +
        'background:#1a1a2e;border:1px solid #4fc3f7;border-radius:8px;padding:0;' +
        'display:flex;flex-direction:column;min-width:340px;max-width:98vw;box-shadow:0 8px 32px rgba(0,0,0,0.7)';

      // Title bar (drag handle)
      var titleBar = document.createElement('div');
      titleBar.style.cssText = 'background:#0d3b5e;padding:8px 14px;border-radius:8px 8px 0 0;' +
        'display:flex;justify-content:space-between;align-items:center;cursor:grab;user-select:none';
      titleBar.innerHTML = '<span style="color:#4fc3f7;font-weight:bold;font-size:13px">A3 Print Preview — ' +
        info.name + '</span>';

      // Action buttons in title bar
      var btnRow = document.createElement('div');
      btnRow.style.cssText = 'display:flex;gap:8px';

      var savePngBtn = document.createElement('button');
      savePngBtn.textContent = 'Save PNG';
      savePngBtn.style.cssText = 'background:#4fc3f7;color:#000;border:none;border-radius:4px;padding:5px 14px;font-size:12px;font-weight:bold;cursor:pointer';

      var closeBtn = document.createElement('button');
      closeBtn.textContent = 'Close';
      closeBtn.style.cssText = 'background:#666;color:#fff;border:none;border-radius:4px;padding:5px 12px;font-size:12px;cursor:pointer';
      closeBtn.onclick = function() { overlay.remove(); };

      btnRow.appendChild(savePngBtn);
      btnRow.appendChild(closeBtn);
      titleBar.appendChild(btnRow);
      panel.appendChild(titleBar);

      // Body: preview image left, fields right
      var body = document.createElement('div');
      body.style.cssText = 'display:flex;gap:0;padding:12px;flex-wrap:wrap';

      // Preview image container
      var imgWrap = document.createElement('div');
      imgWrap.style.cssText = 'flex:1 1 400px;min-width:300px;display:flex;align-items:flex-start;justify-content:center';
      var previewImg = document.createElement('img');
      previewImg.style.cssText = 'max-width:100%;max-height:70vh;border:1px solid #444;object-fit:contain';
      imgWrap.appendChild(previewImg);
      body.appendChild(imgWrap);

      // Fields panel
      var fieldsDiv = document.createElement('div');
      fieldsDiv.style.cssText = 'flex:0 0 220px;padding:0 0 0 14px;display:flex;flex-direction:column;gap:6px';

      function field(label, key, placeholder) {
        var wrap = document.createElement('div');
        var lbl = document.createElement('label');
        lbl.style.cssText = 'color:#aaa;font-size:10px;display:block';
        lbl.textContent = label;
        var inp = document.createElement('input');
        inp.type = 'text';
        inp.value = fields[key];
        inp.placeholder = placeholder || '';
        inp.style.cssText = 'width:100%;background:#0d2137;color:#fff;border:1px solid #345;border-radius:3px;padding:4px 6px;font-size:11px;box-sizing:border-box';
        inp.addEventListener('input', function() { fields[key] = inp.value; renderPreview(); });
        wrap.appendChild(lbl);
        wrap.appendChild(inp);
        return wrap;
      }

      fieldsDiv.appendChild(field('Drawing Title', 'title'));
      fieldsDiv.appendChild(field('Subtitle / View', 'subtitle'));
      fieldsDiv.appendChild(field('Notes', 'notes', 'Construction notes...'));
      fieldsDiv.appendChild(field('Drawn By', 'drawnBy'));
      fieldsDiv.appendChild(field('Checked By', 'checkedBy'));

      // Contrast slider
      var sliderWrap = document.createElement('div');
      var sliderLbl = document.createElement('label');
      sliderLbl.style.cssText = 'color:#aaa;font-size:10px;display:block';
      sliderLbl.textContent = 'B&W Contrast: ' + fields.contrast + '%';
      var slider = document.createElement('input');
      slider.type = 'range'; slider.min = 0; slider.max = 100; slider.value = fields.contrast;
      slider.style.cssText = 'width:100%';
      slider.addEventListener('input', function() {
        fields.contrast = parseInt(slider.value);
        sliderLbl.textContent = 'B&W Contrast: ' + fields.contrast + '%';
        // CSS filter on preview for live feedback (no re-render)
        previewImg.style.filter = 'grayscale(' + fields.contrast + '%) contrast(' + (100 + fields.contrast * 0.4) + '%)';
      });
      sliderWrap.appendChild(sliderLbl);
      sliderWrap.appendChild(slider);
      fieldsDiv.appendChild(sliderWrap);

      body.appendChild(fieldsDiv);
      panel.appendChild(body);
      overlay.appendChild(panel);
      document.body.appendChild(overlay);

      // Make panel draggable
      (function() {
        var dragging = false, ox = 0, oy = 0;
        titleBar.addEventListener('pointerdown', function(e) {
          dragging = true; titleBar.style.cursor = 'grabbing';
          var r = panel.getBoundingClientRect();
          ox = e.clientX - r.left; oy = e.clientY - r.top;
          panel.style.transform = 'none';
          panel.style.left = r.left + 'px'; panel.style.top = r.top + 'px';
        });
        document.addEventListener('pointermove', function(e) {
          if (!dragging) return;
          panel.style.left = (e.clientX - ox) + 'px';
          panel.style.top  = (e.clientY - oy) + 'px';
        });
        document.addEventListener('pointerup', function() { dragging = false; titleBar.style.cursor = 'grab'; });
      })();

      // Render the A3 canvas and update preview image
      function renderPreview() {
        var canvas = document.createElement('canvas');
        canvas.width = A3_W; canvas.height = A3_H;
        var ctx = canvas.getContext('2d');
        ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, A3_W, A3_H);
        ctx.strokeStyle = '#000'; ctx.lineWidth = 3;
        ctx.strokeRect(MARGIN/2, MARGIN/2, A3_W - MARGIN, A3_H - MARGIN);

        var tbTop = drawTitleBlockCorp(ctx, info, corp, fields, A3_W, A3_H);

        var img2 = new Image();
        img2.onload = function() {
          var vpX = MARGIN, vpY = MARGIN;
          var vpW = A3_W - MARGIN * 2, vpH = tbTop - MARGIN - 15;
          var srcA = img2.width / img2.height, dstA = vpW / vpH;
          var dW, dH, dX, dY;
          if (srcA > dstA) { dW = vpW; dH = vpW / srcA; dX = vpX; dY = vpY + (vpH - dH) / 2; }
          else              { dH = vpH; dW = vpH * srcA; dX = vpX + (vpW - dW) / 2; dY = vpY; }

          var gs = fields.contrast;
          ctx.filter = 'grayscale(' + gs + '%) contrast(' + (100 + gs * 0.4) + '%)';
          ctx.drawImage(img2, dX, dY, dW, dH);
          ctx.filter = 'none';
          ctx.strokeStyle = '#333'; ctx.lineWidth = 1;
          ctx.strokeRect(dX, dY, dW, dH);

          drawScaleBar(ctx, dW, A.camera, tbTop);
          drawNorthArrow(ctx, A3_W);

          var dataURL = canvas.toDataURL('image/png');
          previewImg.src = dataURL;
          previewImg.style.filter = ''; // reset CSS filter — baked into canvas
          log('§PRINT_PREVIEW rendered contrast=' + gs + ' fields=6');

          // Wire Save PNG to this render
          savePngBtn.onclick = function() {
            var link = document.createElement('a'); link.download = fileName; link.href = dataURL; link.click();
            if (A.status) A.status.textContent = 'Saved: ' + fileName;
          };
        };
        img2.src = sceneDataURL;
      }

      renderPreview();

      // Escape to close
      var esc = function(e) { if (e.key === 'Escape') { overlay.remove(); document.removeEventListener('keydown', esc); } };
      document.addEventListener('keydown', esc);

      log('§PRINT_SHEET preview shown W-PRINT-1');
    });
  }

  return { capture: capture };
})();
