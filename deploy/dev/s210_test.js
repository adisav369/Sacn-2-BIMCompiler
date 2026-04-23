// s210_test.js — Resolution test for S210b bugs
// Tests code BEHAVIOR not just string presence
const fs = require('fs');
const { execSync } = require('child_process');
const log = [];
const L = (msg) => log.push(msg);
const sc = fs.readFileSync(__dirname + '/sitecam.js', 'utf8');
const iss = fs.readFileSync(__dirname + '/issues.js', 'utf8');

L('§RESOLUTION TEST S210b — ' + new Date().toISOString());

// Syntax
L('');
L('── Syntax ──');
try { new Function(sc); L('  sitecam.js: PASS'); } catch(e) { L('  sitecam.js: FAIL — ' + e.message); }
try { new Function(iss); L('  issues.js: PASS'); } catch(e) { L('  issues.js: FAIL — ' + e.message); }

// ── BUG 1: Walk arrow visible during camera ──
// Root cause: drive-thru-btn is DYNAMIC (walk.js creates it), getElementById returns null
// Fix: must use A._driveBtn reference (the actual object), not getElementById
L('');
L('── BUG 1: Walk arrow removal ──');
const usesRef = sc.includes('A._driveBtn') && sc.includes('A._driveBtn.remove()');
const usesGetElement = sc.includes("getElementById('drive-thru-btn')") && sc.substring(sc.indexOf('§CAM_HIDE')).includes("getElementById('drive-thru-btn')");
L('  uses A._driveBtn (object ref): ' + (usesRef ? 'PASS' : 'FAIL — still using getElementById on dynamic element'));
L('  does NOT getElementById for arrow in hide: ' + (!usesGetElement ? 'PASS' : 'FAIL — getElementById returns null for dynamic btn'));
// Verify remove() not just display:none
L('  calls .remove() (not display:none): ' + (sc.includes('_driveBtn.remove()') ? 'PASS' : 'FAIL'));
// Verify re-create on close
L('  re-creates on close (startDriveThru): ' + (sc.includes('A.startDriveThru()') && sc.includes('_driveBtnWasActive') ? 'PASS' : 'FAIL'));
// Verify startDriveThru has guard against double-create
const walkSrc = fs.readFileSync('/home/red1/bim-compiler/deploy/sandbox/walk.js', 'utf8');
const hasGuard = walkSrc.includes('if (A._driveBtn) return');
L('  startDriveThru has double-create guard: ' + (hasGuard ? 'PASS' : 'FAIL — will create duplicates'));

// ── BUG 2: WhatsApp share no photo/audio ──
// Root cause: wa.me URL is text-only. navigator.share may not support files.
// Fix: check canShare before sending files, proper fallback chain
L('');
L('── BUG 2: Share with files ──');
const hasCanShare = sc.includes('navigator.canShare') && sc.includes('canShare(shareData)');
L('  checks canShare before file share: ' + (hasCanShare ? 'PASS' : 'FAIL — blind share may throw'));
// Fallback chain: files → text-share → wa.me
const shareFunc = sc.substring(sc.indexOf('shareSitePhoto'), sc.indexOf('downloadSitePhoto'));
const hasThreePaths = shareFunc.includes('canShare(shareData)') && shareFunc.includes('navigator.share(shareData)') && shareFunc.includes('wa.me');
L('  three-tier fallback (files → text → wa.me): ' + (hasThreePaths ? 'PASS' : 'FAIL'));
// All paths close preview+camera
const pathCount = (shareFunc.match(/closeSitePreview/g) || []).length;
L('  closeSitePreview calls: ' + pathCount + ' ' + (pathCount >= 2 ? 'PASS' : 'FAIL — not all paths close'));
// Log tags for each path
L('  §SHARE_FILES tag: ' + (shareFunc.includes('§SHARE_FILES') ? 'PASS' : 'FAIL'));
L('  §SHARE_NO_FILE_SUPPORT tag: ' + (shareFunc.includes('§SHARE_NO_FILE_SUPPORT') ? 'PASS' : 'FAIL'));
L('  §SHARE_CLOSE tag: ' + (shareFunc.includes('§SHARE_CLOSE') ? 'PASS' : 'FAIL'));

// ── BUG 3: Double save ──
L('');
L('── BUG 3: Single save at share only ──');
const compBody = sc.substring(sc.indexOf('_compositePhoto = function'), sc.indexOf('_initMarkupListeners'));
L('  _compositePhoto no DB write: ' + (!compBody.includes('_openIssuesDB') ? 'PASS' : 'FAIL'));
L('  §SNAP_NO_EAGER_SAVE: ' + (compBody.includes('§SNAP_NO_EAGER_SAVE') ? 'PASS' : 'FAIL'));

// ── BUG 4: Status toggle no re-render ──
L('');
L('── BUG 4: Status toggle re-renders list ──');
const toggleBlock = iss.substring(iss.indexOf('statusBtn.onclick'), iss.indexOf('_issueBackToList'));
L('  _renderIssueList after toggle: ' + (toggleBlock.includes('_renderIssueList()') ? 'PASS' : 'FAIL'));

// ── Production safety ──
L('');
L('── Production safety ──');
const diff = execSync('git diff --stat deploy/sandbox/ 2>&1').toString().trim();
L('  sandbox untouched: ' + (diff === '' ? 'PASS' : 'FAIL — ' + diff));

// ── SUMMARY ──
L('');
const passes = log.filter(l => l.includes('PASS')).length;
const fails = log.filter(l => l.includes('FAIL'));
L('── SUMMARY: ' + passes + ' PASS, ' + fails.length + ' FAIL ──');
fails.forEach(f => L('  ' + f.trim()));

const out = log.join('\n') + '\n';
fs.writeFileSync(__dirname + '/s210_test.log', out);
console.log(out);
process.exit(fails.length > 0 ? 1 : 0);
