// excel.js — Excel export for issues/punch list
function setupExcel(A) {

  A.exportIssuesExcel = async function() {
    if (typeof XLSX === 'undefined') {
      A.status.textContent = 'SheetJS not loaded';
      return;
    }
    A.status.textContent = 'Exporting issues...';
    try {
      const issues = await A._getAllIssues();
      if (issues.length === 0) {
        A.status.textContent = 'No issues to export';
        return;
      }
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
      const wbout = XLSX.write(wb, { bookType: 'xlsx', type: 'array' });
      const file = new File([wbout], fname, { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        await navigator.share({ files: [file], title: fname });
      } else {
        // Desktop fallback
        XLSX.writeFile(wb, fname);
      }
      A.status.textContent = `Exported ${issues.length} issues`;
      console.log('[S209] §EXCEL exported', issues.length, 'issues');
    } catch(err) {
      A.status.textContent = 'Export error: ' + err.message;
      console.error('[S209] §EXCEL_ERR', err);
    }
  };
}
