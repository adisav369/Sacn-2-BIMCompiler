// sitecam.js — Site Camera (mobile site inspection), photo composite, markup
function setupSitecam(A) {
  A._camStream = null;
  A._camGpsPos = null;
  A._camPhotoBlob = null;
  A._camTimerIv = null;
  A._camBimSnapshot = null;
  A._camHeading = null;
  A._camOrientHandler = null;

  A._getCamBimInfo = function() {
    return {
      cls: document.getElementById('info-class')?.textContent || '—',
      name: document.getElementById('info-name')?.textContent || '—',
      guid: document.getElementById('info-guid')?.textContent || '—',
      building: document.getElementById('info-building')?.textContent || '—',
      storey: document.getElementById('info-storey')?.textContent || '—',
      disc: document.getElementById('info-disc')?.textContent || '—',
    };
  };

  A._formatGps = function(pos) {
    if (!pos) return 'GPS: unavailable';
    const lat = pos.coords.latitude;
    const lng = pos.coords.longitude;
    const ns = lat >= 0 ? 'N' : 'S';
    const ew = lng >= 0 ? 'E' : 'W';
    const acc = pos.coords.accuracy ? ` ±${Math.round(pos.coords.accuracy)}m` : '';
    return `${Math.abs(lat).toFixed(4)}°${ns}, ${Math.abs(lng).toFixed(4)}°${ew}${acc}`;
  };

  A._formatTimestamp = function() {
    const d = new Date();
    const tz = d.getTimezoneOffset();
    const sign = tz <= 0 ? '+' : '-';
    const tzH = String(Math.floor(Math.abs(tz)/60)).padStart(2,'0');
    const tzM = String(Math.abs(tz)%60).padStart(2,'0');
    return d.getFullYear() + '-' +
      String(d.getMonth()+1).padStart(2,'0') + '-' +
      String(d.getDate()).padStart(2,'0') + ' ' +
      String(d.getHours()).padStart(2,'0') + ':' +
      String(d.getMinutes()).padStart(2,'0') + ':' +
      String(d.getSeconds()).padStart(2,'0') + ' ' +
      sign + tzH + tzM;
  };

  A.openSiteCamera = async function() {
    const overlay = document.getElementById('site-cam-overlay');
    const video = document.getElementById('site-cam-video');

    // Snap BIM view for PiP — skip orbit manipulation during walk mode
    if (!A.walkModeActive) {
      if (A._camHeading != null || window._trueNorthAngle !== 0) {
        const heading = A._camHeading || 0;
        const modelAzimuth = (heading - (window._trueNorthAngle || 0)) * Math.PI / 180;
        const target = A.controls.target.clone();
        const dist = A.camera.position.distanceTo(target);
        A.camera.position.x = target.x + dist * Math.sin(modelAzimuth);
        A.camera.position.z = target.z + dist * Math.cos(modelAzimuth);
        A.camera.lookAt(target);
        A.controls.update();
      }
    }
    A.renderer.render(A.scene, A.camera);
    A._camBimSnapshot = A.canvas.toDataURL('image/png');

    const info = A._getCamBimInfo();
    document.getElementById('cam-class').textContent = info.cls;
    document.getElementById('cam-name').textContent = info.name;
    document.getElementById('cam-guid').textContent = info.guid;
    document.getElementById('cam-building').textContent = info.building;
    document.getElementById('cam-storey').textContent = info.storey;
    document.getElementById('cam-disc').textContent = info.disc;

    A._camGpsPos = null;
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        pos => { A._camGpsPos = pos; document.getElementById('site-cam-gps').textContent = A._formatGps(pos); },
        err => { document.getElementById('site-cam-gps').textContent = 'GPS: ' + err.message; },
        { enableHighAccuracy: true, timeout: 10000 }
      );
    } else {
      document.getElementById('site-cam-gps').textContent = 'GPS: not supported';
    }

    // Only add compass listener if NOT in walk mode (walk mode has its own orientation)
    A._camHeading = null;
    if (!A.walkModeActive) {
      A._camOrientHandler = (e) => {
        const h = e.webkitCompassHeading ?? (e.alpha != null ? (360 - e.alpha) % 360 : null);
        if (h != null) {
          A._camHeading = Math.round(h);
          const dirs = ['N','NE','E','SE','S','SW','W','NW'];
          const dir = dirs[Math.round(A._camHeading / 45) % 8];
          document.getElementById('site-cam-compass').textContent = `${A._camHeading}° ${dir}`;
        }
      };
      if (typeof DeviceOrientationEvent?.requestPermission === 'function') {
        DeviceOrientationEvent.requestPermission().then(r => {
          if (r === 'granted') window.addEventListener('deviceorientation', A._camOrientHandler, true);
        }).catch(() => {});
      } else {
        window.addEventListener('deviceorientation', A._camOrientHandler, true);
      }
    }

    A._camTimerIv = setInterval(() => {
      document.getElementById('site-cam-time').textContent = A._formatTimestamp();
    }, 1000);
    document.getElementById('site-cam-time').textContent = A._formatTimestamp();

    try {
      A._camStream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment', width: { ideal: 1920 }, height: { ideal: 1080 } }
      });
      video.srcObject = A._camStream;
      overlay.classList.add('active');
      // Hide toolbar elements that bleed through on mobile (use !important to beat CSS media queries)
      for (const id of ['walk-mode-btn','search-box','disc-panel','storey-panel','info-panel','hud','status']) {
        const el = document.getElementById(id);
        if (el) { el.dataset.camHid = el.style.cssText; el.style.setProperty('display', 'none', 'important'); }
      }
      // Push history state so phone back button closes camera instead of leaving page
      history.pushState({ siteCam: true }, '');
      console.log('[S204] §CAMERA opened');
    } catch (err) {
      A.status.textContent = 'Camera: ' + err.message;
      console.log('[S204] §CAMERA_ERR', err.message);
      A.closeSiteCamera();
    }
  };

  // Phone back button closes camera/preview instead of navigating away
  window.addEventListener('popstate', (e) => {
    if (document.getElementById('site-cam-preview').classList.contains('active')) {
      A.closeSitePreview();
    } else if (document.getElementById('site-cam-overlay').classList.contains('active')) {
      A.closeSiteCamera();
    }
  });

  A.closeSiteCamera = function() {
    if (A._camStream) {
      A._camStream.getTracks().forEach(t => t.stop());
      A._camStream = null;
    }
    if (A._camTimerIv) { clearInterval(A._camTimerIv); A._camTimerIv = null; }
    if (A._camOrientHandler) { window.removeEventListener('deviceorientation', A._camOrientHandler, true); A._camOrientHandler = null; }
    document.getElementById('site-cam-overlay').classList.remove('active');
    document.getElementById('site-cam-video').srcObject = null;
    // Restore hidden toolbar elements
    for (const id of ['walk-mode-btn','search-box','disc-panel','storey-panel','info-panel','hud','status']) {
      const el = document.getElementById(id);
      if (el && el.dataset.camHid !== undefined) { el.style.cssText = el.dataset.camHid; delete el.dataset.camHid; }
    }
  };

  A.snapSitePhoto = function() {
    const video = document.getElementById('site-cam-video');
    const info = A._getCamBimInfo();
    const gpsText = A._formatGps(A._camGpsPos);
    const timeText = A._formatTimestamp();
    const dirs = ['N','NE','E','SE','S','SW','W','NW'];
    const compassText = A._camHeading != null ? `${A._camHeading}° ${dirs[Math.round(A._camHeading / 45) % 8]}` : null;

    // Auto-save to punch list if element is selected (Snag-to-BIM)
    // Save metadata now; photo blob added after composite in _compositePhoto
    if (info.guid && info.guid !== '—') {
      A._pendingSnagInfo = info;
    }

    const bimImg = new Image();
    bimImg.onload = () => A._compositePhoto(video, info, gpsText, timeText, compassText, bimImg);
    bimImg.onerror = () => A._compositePhoto(video, info, gpsText, timeText, compassText, null);
    if (A._camBimSnapshot) {
      bimImg.src = A._camBimSnapshot;
    } else {
      A._compositePhoto(video, info, gpsText, timeText, compassText, null);
    }

    const flash = document.createElement('div');
    flash.style.cssText = 'position:fixed;top:0;left:0;width:100vw;height:100vh;background:white;opacity:0.7;z-index:3000;pointer-events:none';
    document.body.appendChild(flash);
    setTimeout(() => { flash.style.opacity = '0'; flash.style.transition = 'opacity 0.3s'; }, 50);
    setTimeout(() => document.body.removeChild(flash), 400);
  };

  A._drawMiniQR = function(ctx, text, x, y, size) {
    const qrImg = new Image();
    qrImg.crossOrigin = 'anonymous';
    qrImg.onload = () => {
      ctx.fillStyle = '#fff';
      ctx.fillRect(x - 2, y - 2, size + 4, size + 4);
      ctx.drawImage(qrImg, x, y, size, size);
    };
    const encoded = encodeURIComponent(text);
    qrImg.src = `https://api.qrserver.com/v1/create-qr-code/?size=100x100&data=${encoded}`;
    qrImg.onerror = () => {};
  };

  A._compositePhoto = function(video, info, gpsText, timeText, compassText, bimImg) {
    const c = document.createElement('canvas');
    c.width = video.videoWidth || 1280;
    c.height = video.videoHeight || 720;
    const ctx = c.getContext('2d');

    ctx.drawImage(video, 0, 0, c.width, c.height);

    const barH = 56;
    ctx.fillStyle = 'rgba(0,0,0,0.65)';
    ctx.fillRect(0, 0, c.width, barH);
    ctx.font = '13px "Segoe UI", sans-serif';
    ctx.fillStyle = '#4fc3f7';
    ctx.fillText(`${info.cls}`, 10, 18);
    ctx.fillStyle = '#fff';
    ctx.fillText(`${info.name}`, 10 + ctx.measureText(info.cls + '  ').width, 18);
    ctx.fillStyle = '#888';
    ctx.font = '11px monospace';
    ctx.fillText(`GUID: ${info.guid}`, 10, 34);
    ctx.fillStyle = '#ccc';
    ctx.fillText(`${info.building} / ${info.storey} / ${info.disc}`, 10, 50);

    if (bimImg && bimImg.width > 0) {
      const pipW = Math.round(c.width * 0.35);
      const pipH = Math.round(pipW * (bimImg.height / bimImg.width));
      const pipX = c.width - pipW - 8;
      const pipY = barH + 8;
      ctx.fillStyle = 'rgba(0,0,0,0.7)';
      ctx.fillRect(pipX - 3, pipY - 3, pipW + 6, pipH + 6);
      ctx.drawImage(bimImg, pipX, pipY, pipW, pipH);
      ctx.fillStyle = 'rgba(0,0,0,0.6)';
      ctx.fillRect(pipX, pipY + pipH - 18, pipW, 18);
      ctx.fillStyle = '#4fc3f7';
      ctx.font = '10px sans-serif';
      ctx.fillText('BIM Model View', pipX + 4, pipY + pipH - 5);
    }

    const footH = compassText ? 42 : 28;
    ctx.fillStyle = 'rgba(0,0,0,0.65)';
    ctx.fillRect(0, c.height - footH, c.width, footH);
    ctx.font = '12px monospace';
    if (compassText) {
      ctx.fillStyle = '#ffff00';
      ctx.fillText(`Bearing: ${compassText}`, 10, c.height - 26);
    }
    ctx.fillStyle = '#00ff00';
    ctx.fillText(gpsText, 10, c.height - 10);

    ctx.fillStyle = '#00ff00';
    const tw = ctx.measureText(timeText).width;
    ctx.fillText(timeText, c.width - tw - 10, c.height - 10);

    if (info.guid && info.guid !== '—') {
      const qrUrl = `${location.origin}${location.pathname}?guid=${info.guid}`;
      A._drawMiniQR(ctx, qrUrl, 4, c.height - footH - 54, 50);
    }

    ctx.fillStyle = 'rgba(255,255,255,0.3)';
    ctx.font = '10px sans-serif';
    const wm = 'BIM OOTB — Site Inspection';
    ctx.fillText(wm, (c.width - ctx.measureText(wm).width) / 2, c.height - 10);

    const mc = document.getElementById('site-cam-markup');
    mc.width = c.width;
    mc.height = c.height;
    const mctx = mc.getContext('2d');
    mctx.drawImage(c, 0, 0);
    A._markupBaseImage = c;
    A._markupStrokes = [];
    A._camPhotoBlob = null;
    document.getElementById('site-cam-preview').classList.add('active');
    history.pushState({ sitePreview: true }, '');

    // Snag-to-BIM: save with photo now that canvas is drawn
    if (A._pendingSnagInfo) {
      const snagInfo = A._pendingSnagInfo;
      A._pendingSnagInfo = null;
      mc.toBlob(async (blob) => {
        if (!blob) return;
        try {
          const issue = {
            jpeg_blob: blob,
            gps_lat: A._camGpsPos ? A._camGpsPos.coords.latitude : null,
            gps_lng: A._camGpsPos ? A._camGpsPos.coords.longitude : null,
            gps_accuracy: A._camGpsPos ? A._camGpsPos.coords.accuracy : null,
            compass_heading: A._camHeading,
            timestamp: new Date().toISOString(),
            element_guid: snagInfo.guid || '',
            element_class: snagInfo.cls || '',
            element_name: snagInfo.name || '',
            building: snagInfo.building || '',
            storey: snagInfo.storey || '',
            discipline: snagInfo.disc || '',
            status: 'open',
            notes: ''
          };
          const db = await A._openIssuesDB();
          const tx = db.transaction('issues', 'readwrite');
          tx.objectStore('issues').add(issue);
          await new Promise((res, rej) => { tx.oncomplete = res; tx.onerror = rej; });
          db.close();
          A.status.textContent = `📸 Snag saved: ${snagInfo.cls} @ ${snagInfo.storey}`;
          console.log('[S209] §SNAG saved', snagInfo.cls, snagInfo.storey);
        } catch(err) { console.error('[S209] §SNAG_ERR', err); }
      }, 'image/jpeg', 0.85);
    }
    A._initMarkupListeners(mc);
    console.log(`[S204] §SNAP ${info.cls} GPS:${gpsText} ${timeText} BIM:${bimImg ? 'YES' : 'NO'}`);
  };

  // Markup state
  A._markupTool = 'arrow';
  A._markupColor = '#ff0000';
  A._markupBaseImage = null;
  A._markupStrokes = [];
  A._markupActive = false;
  A._markupStart = null;
  A._markupListenersSet = false;

  A.setMarkupTool = function(tool) {
    A._markupTool = tool;
    document.querySelectorAll('.markup-btn').forEach(b => {
      b.style.background = b.dataset.tool === tool ? '#ff4444' : '#444';
    });
    if (tool === 'text') {
      const mc = document.getElementById('site-cam-markup');
      // Inline input instead of prompt() — prompt is unreliable on mobile overlays
      let inp = document.getElementById('markup-text-input');
      if (!inp) {
        inp = document.createElement('input');
        inp.id = 'markup-text-input';
        inp.type = 'text';
        inp.placeholder = 'Type text, then tap canvas';
        inp.style.cssText = 'position:fixed;top:8px;left:50%;transform:translateX(-50%);z-index:9999;padding:8px 12px;font-size:16px;background:#222;color:#fff;border:2px solid #4fc3f7;border-radius:6px;width:70%;text-align:center;';
        document.body.appendChild(inp);
        // Tap canvas to place text
        const placeText = (e) => {
          const text = inp.value.trim();
          if (!text) return;
          const p = A._canvasCoords(mc, e);
          A._markupStrokes.push({ tool: 'text', color: A._markupColor, text, x: p.x, y: p.y });
          A._redrawMarkup();
          inp.value = '';
          inp.remove();
          mc.removeEventListener('pointerdown', placeText);
        };
        mc.addEventListener('pointerdown', placeText);
      }
      inp.focus();
    }
  };

  A.setMarkupColor = function(color) {
    A._markupColor = color;
    document.querySelectorAll('[id^="mc-"]').forEach(b => {
      b.style.borderColor = b.style.backgroundColor === color ? '#fff' : '#333';
    });
  };

  A.undoMarkup = function() {
    A._markupStrokes.pop();
    A._redrawMarkup();
  };

  A._redrawMarkup = function() {
    const mc = document.getElementById('site-cam-markup');
    const ctx = mc.getContext('2d');
    ctx.drawImage(A._markupBaseImage, 0, 0);
    A._markupStrokes.forEach(s => A._drawStroke(ctx, s, mc));
  };

  A._drawStroke = function(ctx, s, mc) {
    ctx.strokeStyle = s.color;
    ctx.fillStyle = s.color;
    ctx.lineWidth = 3;
    ctx.lineCap = 'round';
    if (s.tool === 'arrow' && s.points && s.points.length === 2) {
      const [a, b] = s.points;
      ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.stroke();
      const angle = Math.atan2(b.y - a.y, b.x - a.x);
      const headLen = 15;
      ctx.beginPath();
      ctx.moveTo(b.x, b.y);
      ctx.lineTo(b.x - headLen * Math.cos(angle - 0.4), b.y - headLen * Math.sin(angle - 0.4));
      ctx.lineTo(b.x - headLen * Math.cos(angle + 0.4), b.y - headLen * Math.sin(angle + 0.4));
      ctx.closePath(); ctx.fill();
    } else if (s.tool === 'circle' && s.points && s.points.length === 2) {
      const [a, b] = s.points;
      const r = Math.sqrt((b.x-a.x)**2 + (b.y-a.y)**2);
      ctx.beginPath(); ctx.arc(a.x, a.y, r, 0, Math.PI * 2); ctx.stroke();
    } else if (s.tool === 'freehand' && s.points && s.points.length > 1) {
      ctx.beginPath(); ctx.moveTo(s.points[0].x, s.points[0].y);
      s.points.forEach(p => ctx.lineTo(p.x, p.y));
      ctx.stroke();
    } else if (s.tool === 'text' && s.text) {
      ctx.font = 'bold 18px sans-serif';
      ctx.fillStyle = s.color;
      const m = ctx.measureText(s.text);
      ctx.fillStyle = 'rgba(0,0,0,0.6)';
      ctx.fillRect(s.x - 2, s.y - 16, m.width + 4, 20);
      ctx.fillStyle = s.color;
      ctx.fillText(s.text, s.x, s.y);
    }
  };

  A._canvasCoords = function(mc, e) {
    const rect = mc.getBoundingClientRect();
    const touch = e.touches ? e.touches[0] : e;
    return {
      x: (touch.clientX - rect.left) * (mc.width / rect.width),
      y: (touch.clientY - rect.top) * (mc.height / rect.height)
    };
  };

  A._initMarkupListeners = function(mc) {
    if (A._markupListenersSet) return;
    A._markupListenersSet = true;
    let currentStroke = null;

    function onStart(e) {
      e.preventDefault();
      const p = A._canvasCoords(mc, e);
      A._markupActive = true;
      if (A._markupTool === 'text') return;
      currentStroke = { tool: A._markupTool, color: A._markupColor, points: [p] };
    }
    function onMove(e) {
      if (!A._markupActive || !currentStroke) return;
      e.preventDefault();
      const p = A._canvasCoords(mc, e);
      if (A._markupTool === 'freehand') {
        currentStroke.points.push(p);
      } else {
        currentStroke.points[1] = p;
      }
      A._redrawMarkup();
      A._drawStroke(mc.getContext('2d'), currentStroke, mc);
    }
    function onEnd(e) {
      if (!A._markupActive || !currentStroke) { A._markupActive = false; return; }
      A._markupActive = false;
      if (currentStroke.points.length >= 2 || A._markupTool === 'freehand') {
        A._markupStrokes.push(currentStroke);
      }
      currentStroke = null;
      A._redrawMarkup();
    }

    mc.addEventListener('pointerdown', onStart);
    mc.addEventListener('pointermove', onMove);
    mc.addEventListener('pointerup', onEnd);
    mc.addEventListener('pointercancel', onEnd);
  };

  A._getMarkupBlob = function() {
    return new Promise(resolve => {
      const mc = document.getElementById('site-cam-markup');
      mc.toBlob(resolve, 'image/jpeg', 0.92);
    });
  };

  A.closeSitePreview = function() {
    document.getElementById('site-cam-preview').classList.remove('active');
    A._camPhotoBlob = null;
    A._markupListenersSet = false;
  };

  A.shareSitePhoto = async function() {
    const blob = await A._getMarkupBlob();
    if (!blob) return;
    const info = A._getCamBimInfo();
    const gpsText = A._formatGps(A._camGpsPos);
    const timeText = A._formatTimestamp();
    const title = `BIM Site Photo — ${info.cls} @ ${info.storey}`;
    const mapsLink = A._camGpsPos ? `https://maps.google.com/?q=${A._camGpsPos.coords.latitude},${A._camGpsPos.coords.longitude}` : '';
    const bearingText = A._camHeading != null ? `Bearing: ${A._camHeading}° ${['N','NE','E','SE','S','SW','W','NW'][Math.round(A._camHeading/45)%8]}` : '';
    const text = `${info.building} / ${info.storey} / ${info.cls}\n${info.name}\nGUID: ${info.guid}\nGPS: ${gpsText}${bearingText ? '\n' + bearingText : ''}\n${mapsLink}\n${timeText}`;
    const fileName = `BIM_Site_${info.building}_${timeText.replace(/[: ]/g,'-')}.jpg`;

    if (navigator.share) {
      try {
        const file = new File([blob], fileName, { type: 'image/jpeg' });
        await navigator.share({ files: [file], title, text });
        console.log('[S204] §SHARE via Web Share API');
        A._saveIssueToLog();
        A.closeSitePreview();
        A.closeSiteCamera();
        return;
      } catch (err) {
        if (err.name === 'AbortError') return;
        console.log('[S204] §SHARE_FALLBACK', err.message);
      }
    }
    const waText = encodeURIComponent(title + '\n' + text);
    window.open(`https://wa.me/?text=${waText}`, '_blank');
    A._saveIssueToLog();
    console.log('[S204] §SHARE via wa.me fallback');
  };

  A.downloadSitePhoto = async function() {
    const blob = await A._getMarkupBlob();
    if (!blob) return;
    const info = A._getCamBimInfo();
    const link = document.createElement('a');
    link.download = `BIM_Site_${info.building}_${A._formatTimestamp().replace(/[: ]/g,'-')}.jpg`;
    link.href = URL.createObjectURL(blob);
    link.click();
    A._saveIssueToLog();
    console.log('[S204] §DOWNLOAD with markup');
  };
}
