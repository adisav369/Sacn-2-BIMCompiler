// issues.js — Issue Log (IndexedDB persistence), export to Excel
function setupIssues(A) {

  A._openIssuesDB = function() {
    return new Promise((resolve, reject) => {
      const req = indexedDB.open('bim_ootb_issues', 2);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains('issues')) {
          db.createObjectStore('issues', { keyPath: 'id', autoIncrement: true });
        }
      };
      req.onblocked = () => { console.warn('[S209] IDB blocked — close other tabs'); reject(new Error('DB blocked by another tab')); };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  };

  A._saveIssueToLog = async function() {
    try {
      const blob = await A._getMarkupBlob();
      if (!blob) return;
      const info = A._getCamBimInfo();
      const issue = {
        jpeg_blob: blob,
        gps_lat: A._camGpsPos ? A._camGpsPos.coords.latitude : null,
        gps_lng: A._camGpsPos ? A._camGpsPos.coords.longitude : null,
        gps_accuracy: A._camGpsPos ? A._camGpsPos.coords.accuracy : null,
        compass_heading: A._camHeading,
        timestamp: new Date().toISOString(),
        element_guid: info.guid || '',
        element_class: info.cls || '',
        element_name: info.name || '',
        building: info.building || '',
        storey: info.storey || '',
        discipline: info.disc || '',
        notes: ''
      };
      const db = await A._openIssuesDB();
      const tx = db.transaction('issues', 'readwrite');
      tx.objectStore('issues').add(issue);
      await new Promise((resolve, reject) => { tx.oncomplete = resolve; tx.onerror = reject; });
      db.close();
      console.log('[S205] Issue saved to log', issue.element_class, issue.storey);
    } catch (err) {
      console.error('[S205] Failed to save issue', err);
    }
  };

  A._getAllIssues = async function() {
    const db = await A._openIssuesDB();
    const tx = db.transaction('issues', 'readonly');
    const store = tx.objectStore('issues');
    return new Promise((resolve, reject) => {
      const req = store.getAll();
      req.onsuccess = () => { db.close(); resolve(req.result); };
      req.onerror = () => { db.close(); reject(req.error); };
    });
  };

  A._blobToThumbUrl = function(blob) {
    return new Promise(resolve => {
      const img = new Image();
      img.onload = () => {
        const w = 100, h = Math.round(img.height * (100 / img.width));
        const c = document.createElement('canvas');
        c.width = w; c.height = h;
        c.getContext('2d').drawImage(img, 0, 0, w, h);
        URL.revokeObjectURL(img.src);
        resolve(c.toDataURL('image/jpeg', 0.7));
      };
      img.src = URL.createObjectURL(blob);
    });
  };

  A._formatIssueGps = function(lat, lng, acc) {
    if (lat == null || lng == null) return 'N/A';
    const ns = lat >= 0 ? 'N' : 'S';
    const ew = lng >= 0 ? 'E' : 'W';
    const accStr = acc ? ' \u00b1' + Math.round(acc) + 'm' : '';
    return Math.abs(lat).toFixed(4) + '\u00b0' + ns + ', ' + Math.abs(lng).toFixed(4) + '\u00b0' + ew + accStr;
  };

  A._renderIssueList = async function() {
    const list = document.getElementById('issues-list');
    const detail = document.getElementById('issue-detail-view');
    detail.classList.remove('active');
    list.style.display = '';
    const issues = await A._getAllIssues();
    if (issues.length === 0) {
      list.innerHTML = '<div class="issues-empty">No issues logged yet.<br>Use Site Camera to snap and save photos.</div>';
      return;
    }
    list.innerHTML = '';
    for (const iss of issues.reverse()) {
      const status = iss.status || 'open';
      const statusIcon = status === 'fixed' ? '✅' : '🔴';
      const card = document.createElement('div');
      card.className = 'issue-card';
      card.onclick = () => A._showIssueDetail(iss);
      const thumbUrl = iss.jpeg_blob ? await A._blobToThumbUrl(iss.jpeg_blob) : '';
      const ts = iss.timestamp ? new Date(iss.timestamp).toLocaleString() : '';
      const gps = A._formatIssueGps(iss.gps_lat, iss.gps_lng, iss.gps_accuracy);
      card.innerHTML =
        (thumbUrl ? '<img src="' + thumbUrl + '">' : '') +
        '<div class="issue-meta">' +
          '<div class="issue-class">' + statusIcon + ' ' + (iss.element_class || '-') + '</div>' +
          '<div>' + (iss.element_name || '-') + '</div>' +
          '<div>' + (iss.storey || '-') + ' / ' + (iss.discipline || '-') + '</div>' +
          '<div class="issue-ts">' + ts + '</div>' +
        '</div>';
      list.appendChild(card);
    }
  };

  A._showIssueDetail = async function(iss) {
    const list = document.getElementById('issues-list');
    const detail = document.getElementById('issue-detail-view');
    list.style.display = 'none';
    detail.classList.add('active');
    const imgEl = document.getElementById('issue-detail-img');
    if (iss.jpeg_blob) {
      imgEl.src = URL.createObjectURL(iss.jpeg_blob);
    } else {
      imgEl.src = '';
    }
    document.getElementById('issue-d-class').textContent = iss.element_class || '-';
    document.getElementById('issue-d-name').textContent = iss.element_name || '-';
    document.getElementById('issue-d-guid').textContent = iss.element_guid || '-';
    document.getElementById('issue-d-building').textContent = iss.building || '-';
    document.getElementById('issue-d-storey').textContent = iss.storey || '-';
    document.getElementById('issue-d-disc').textContent = iss.discipline || '-';
    document.getElementById('issue-d-gps').textContent = A._formatIssueGps(iss.gps_lat, iss.gps_lng, iss.gps_accuracy);
    document.getElementById('issue-d-compass').textContent = iss.compass_heading != null ? iss.compass_heading + '\u00b0' : '-';
    document.getElementById('issue-d-time').textContent = iss.timestamp ? new Date(iss.timestamp).toLocaleString() : '-';
    document.getElementById('issue-d-notes').textContent = iss.notes || '-';

    // Status toggle button
    const statusBtn = document.getElementById('issue-d-status-btn');
    const status = iss.status || 'open';
    statusBtn.textContent = status === 'fixed' ? '✅ Fixed — tap to reopen' : '🔴 Open — tap to mark Fixed';
    statusBtn.style.background = status === 'fixed' ? '#2e7d32' : '#c62828';
    statusBtn.onclick = async () => {
      const newStatus = (iss.status || 'open') === 'open' ? 'fixed' : 'open';
      iss.status = newStatus;
      try {
        const db = await A._openIssuesDB();
        const tx = db.transaction('issues', 'readwrite');
        tx.objectStore('issues').put(iss);
        await new Promise((res, rej) => { tx.oncomplete = res; tx.onerror = rej; });
        db.close();
        statusBtn.textContent = newStatus === 'fixed' ? '✅ Fixed — tap to reopen' : '🔴 Open — tap to mark Fixed';
        statusBtn.style.background = newStatus === 'fixed' ? '#2e7d32' : '#c62828';
        console.log('[S209] §STATUS', iss.id, newStatus);
      } catch(err) { console.error('[S209] §STATUS_ERR', err); }
    };
  };

  A._issueBackToList = function() {
    document.getElementById('issue-detail-view').classList.remove('active');
    document.getElementById('issues-list').style.display = '';
  };

  A.toggleIssues = function() {
    const panel = document.getElementById('issues-panel');
    if (panel.classList.contains('active')) {
      panel.classList.remove('active');
    } else {
      panel.classList.add('active');
      A._renderIssueList();
    }
  };

  A.exportIssuesExcel = async function() {
    if (typeof XLSX === 'undefined') { A.status.textContent = 'SheetJS not loaded'; return; }
    A.status.textContent = 'Exporting issues...';
    try {
    const issues = await A._getAllIssues();
    if (issues.length === 0) { alert('No issues to export'); return; }
    const rows = issues.map(iss => ({
      'ID': iss.id,
      'Status': (iss.status || 'open').toUpperCase(),
      'Timestamp': iss.timestamp ? new Date(iss.timestamp).toLocaleString() : '',
      'Building': iss.building || '',
      'Storey': iss.storey || '',
      'Discipline': iss.discipline || '',
      'IFC Class': iss.element_class || '',
      'Element Name': iss.element_name || '',
      'GUID': iss.element_guid || '',
      'GPS Lat': iss.gps_lat != null ? iss.gps_lat : '',
      'GPS Lng': iss.gps_lng != null ? iss.gps_lng : '',
      'Compass': iss.compass_heading != null ? iss.compass_heading : '',
      'Notes': iss.notes || ''
    }));
    const ws = XLSX.utils.json_to_sheet(rows);
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'Issues');
    const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const fname = 'BIM_Issues_' + ts + '.xlsx';
    // Generate Excel and download without navigating away
    const wbout = XLSX.write(wb, { bookType: 'xlsx', type: 'array' });
    const blob = new Blob([wbout], { type: 'application/octet-stream' });
    const url = URL.createObjectURL(blob);
    // Open in new tab — avoids any page navigation on current tab
    window.open(url, '_blank');
    A.status.textContent = `Exported ${issues.length} issues — check downloads`;
    setTimeout(() => URL.revokeObjectURL(url), 5000);
    console.log('[S205] Exported', issues.length, 'issues to Excel');
    } catch(err) { alert('Export error: ' + err.message); console.error('[S209] §EXCEL_ERR', err); }
  };

  A.clearAllIssues = async function() {
    if (!confirm('Delete all logged issues? This cannot be undone.')) return;
    const db = await A._openIssuesDB();
    const tx = db.transaction('issues', 'readwrite');
    tx.objectStore('issues').clear();
    await new Promise((resolve, reject) => { tx.oncomplete = resolve; tx.onerror = reject; });
    db.close();
    A._renderIssueList();
    console.log('[S205] All issues cleared');
  };
}
