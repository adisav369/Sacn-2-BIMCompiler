// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// Implementing prompts/IDEMPIERE_TOUR_GUIDE.md §Acceptance — Witness: W-TOUR-BIND (live host contract).
// NAMES the issue: does the DEPLOYED, UNFORKED help_overlay.js + help_idmp.js actually BIND to the REAL
// window.IdmpHost that record-panel published in bim-ootb/erp/idempiere.html — i.e. NeedHelp? builds a badge
// for a tagged O2C record and ShowMe drives the host's ACTUAL focus/trace/openTab (not a mock)?
// Method: extract the real IdmpHost block + its helpers + the inlined keymap FROM idempiere.html, run them in
// a DOM shim with a [data-ad-table=c_order][data-ad-record=#80001] row, then load the byte-identical deployed
// overlay files against that real host. §-log first → build/erp/tour_bind_witness.log. READ the log.
'use strict';
var fs = require('fs');
var vm = require('vm');
var path = require('path');
var OOTB = '/home/red1/bim-ootb/erp';
var SRC  = path.join(__dirname, '..', 'build', 'erp');

var lines = [], pass = 0, fail = 0;
function L(s) { lines.push(s); }
function ck(c, m) { if (c) { pass++; L('§TOUR-BIND PASS ' + m); } else { fail++; L('§TOUR-BIND FAIL ' + m); } }

var HTML   = fs.readFileSync(path.join(OOTB, 'idempiere.html'), 'utf8');
var OVSRC  = fs.readFileSync(path.join(OOTB, 'help_overlay.js'), 'utf8');     // the DEPLOYED artifacts
var ADSRC  = fs.readFileSync(path.join(OOTB, 'help_idmp.js'), 'utf8');
var MYMAP  = JSON.parse(fs.readFileSync(path.join(SRC, 'help_idmp_keymap.json'), 'utf8'));
var STORE  = JSON.parse(fs.readFileSync(path.join(SRC, 'help_ops.json'), 'utf8'));   // the keyed step store enable() fetches

// ── extract the REAL host snippet (helpers + window.IdmpHost) from idempiere.html ──
var hostStart = HTML.indexOf('function _adMatch(');
var hostEnd   = HTML.indexOf("§SEAM-FROZEN", hostStart);
hostEnd       = HTML.indexOf('\n', hostEnd) + 1;                              // include the SEAM-FROZEN log line
var HOSTSRC   = HTML.slice(hostStart, hostEnd);
ck(hostStart > 0 && /window\.IdmpHost\s*=/.test(HOSTSRC), 'extracted the real window.IdmpHost block from idempiere.html');

// ── extract the inlined keymap and assert it mirrors my source keymap VERBATIM ──
var mapTxt = HTML.slice(HTML.indexOf('__helpIdmpKeymap = {') + '__helpIdmpKeymap = '.length);
mapTxt = mapTxt.slice(0, mapTxt.indexOf('};') + 1);
var INLINE = vm.runInNewContext('(' + mapTxt + ')', {});
var mapKeys = Object.keys(MYMAP).filter(function (k) { return k !== '__meta'; });
var inlineMirrors = mapKeys.every(function (k) {
  var a = MYMAP[k] || {}, b = INLINE[k] || {};
  return b && a.window === b.window && a.table === b.table && a.coverage === b.coverage;
}) && Object.keys(INLINE).length === mapKeys.length;
ck(inlineMirrors, 'inlined __helpIdmpKeymap mirrors build/erp/help_idmp_keymap.json verbatim (5 docs + o2c)');

// ════════════════════════════════════════════════════════════════════════════
// DOM shim + chrome-internal shims, then run: real host snippet → overlay → adapter.
// ════════════════════════════════════════════════════════════════════════════
function makeEl(tag, attrs) {
  attrs = attrs || {};
  var el = {
    tagName: tag, id: '', className: '', _html: '', textContent: '', children: [], parentNode: null,
    style: {}, offsetWidth: 0, offsetHeight: 0, _attrs: attrs,
    set innerHTML(v) { this._html = v; }, get innerHTML() { return this._html; },
    appendChild: function (c) { c.parentNode = el; el.children.push(c); return c; },
    removeChild: function (c) { var i = el.children.indexOf(c); if (i >= 0) el.children.splice(i, 1); return c; },
    insertBefore: function (c) { c.parentNode = el; el.children.push(c); return c; },
    setAttribute: function (k, v) { el._attrs[k] = v; }, getAttribute: function (k) { return el._attrs[k] != null ? el._attrs[k] : null; },
    addEventListener: function () {}, removeEventListener: function () {},
    getBoundingClientRect: function () { return { left: 40, top: 60, width: 120, height: 24, right: 160, bottom: 84 }; },
    querySelector: function () { return makeEl('shim'); }, querySelectorAll: function () { return []; },
    classList: { _s: {}, add: function (c) { this._s[c] = 1; }, remove: function (c) { delete this._s[c]; },
                 toggle: function (c) { this._s[c] = !this._s[c]; }, contains: function (c) { return !!this._s[c]; } },
    closest: function () { return null; }
  };
  return el;
}

// the REAL tagged O2C row record-panel renders for Sales Order #80001:
var orderRow = makeEl('tr', { 'data-ad-table': 'c_order', 'data-ad-record': '#80001' });
var byId = {};
var doc = {
  head: makeEl('head'), body: makeEl('body'),
  createElement: function (t) { return makeEl(t); },
  getElementById: function (id) { if (!byId[id]) { byId[id] = makeEl('byid'); byId[id].id = id; } return byId[id]; },
  querySelectorAll: function (sel) { return sel === '[data-ad-table]' ? [orderRow] : []; },
  addEventListener: function () {}
};

var rec = { openWindow: [], status: [], focusTrace: [], tabSel: [] };
var sandbox = {
  console: { log: function () {}, warn: function () {}, error: function () {} },
  Math: Math, JSON: JSON, String: String, Object: Object, Array: Array, setTimeout: function () {},
  requestAnimationFrame: function () { return 1; }, cancelAnimationFrame: function () {},
  CustomEvent: function (n, o) { this.type = n; this.detail = o && o.detail; },
  document: doc, innerWidth: 1200, innerHeight: 800,
  addEventListener: function () {}, removeEventListener: function () {}, dispatchEvent: function () {},
  fetch: function () { return Promise.resolve({ json: function () { return Promise.resolve(STORE); } }); },
  // chrome internals the real IdmpHost block closes over (shimmed as recorders / fixtures):
  _winByName: { 'sales order': 1001, 'shipment (customer)': 1002, 'sales invoice': 1003, 'payment': 1004, 'allocation': 1005 },
  _activeTabIdx: 0, _viewMode: 'grid',
  _curWin: function () { return { tabs: [{ tableName: 'c_order', name: 'Data' }] }; },
  openWindow: function (wid, name) { rec.openWindow.push({ wid: wid, name: name }); },
  status: function (s) { rec.status.push(s); },
  renderActiveTab: function () {}, renderTabStrip: function () {}, renderToolbar: function () {}
};
sandbox.window = sandbox;

vm.runInNewContext(HOSTSRC, sandbox, { filename: 'idempiere.IdmpHost' });    // defines real window.IdmpHost
ck(typeof sandbox.IdmpHost === 'object' && typeof sandbox.IdmpHost.focus === 'function',
   'real IdmpHost published with focus/trace/openTab/has/locate');

// the real host's own probes, against the real tagged row:
ck(sandbox.IdmpHost.has('c_order') === true, 'IdmpHost.has(c_order)=true — host sees the tagged #80001 row');
ck(sandbox.IdmpHost.has('nope') === false, 'IdmpHost.has(absent)=false — honest, no fabricated element');
var loc = sandbox.IdmpHost.locate('c_order');
ck(loc && typeof loc.x === 'number' && loc.rRaw === 14, 'IdmpHost.locate(c_order) returns a screen rect for the badge');

// now load the DEPLOYED, UNFORKED overlay + adapter against this REAL host:
sandbox.__helpIdmpKeymap = INLINE;
vm.runInNewContext(OVSRC, sandbox, { filename: 'help_overlay.js' });
vm.runInNewContext(ADSRC, sandbox, { filename: 'help_idmp.js' });
var H = sandbox.__help;
ck(H && typeof H.init === 'function', 'deployed help_overlay.js exposed __help (mounted)');
var ad = H.adapter();
ck(ad.nav.has('c_order') === true && ad.nav.locate('c_order'), 'adapter NAV is bound to the REAL IdmpHost (has/locate route through it)');

// ShowMe a real O2C step → must drive the REAL host: openWindow(Sales Order) + trace + tab select.
H.showMe({ op: 'o2c', key: 'c_order', target: 'c_order', kind: 'process', tab: 'Data' });
ck(rec.openWindow.length === 1 && rec.openWindow[0].name === 'Sales Order',
   'ShowMe(c_order) drove the REAL IdmpHost.focus → openWindow("Sales Order") for #80001');
ck(sandbox.document.body.classList.contains('idmp-tracing'),
   'ShowMe drove the REAL IdmpHost.trace(true) (body.idmp-tracing set)');
L('§TOUR-BIND overlay=help_overlay host=idempiere forked=0 realHost=IdmpHost showme-nav=via-globals badge-for=c_order#80001');

// badges: NeedHelp? builds a numbered badge for has-keys via the real host.
H.enable();
setImmediate(function () { setImmediate(function () {
  var badges = (sandbox.document.getElementById('idmp-content').children || [])
    .concat(sandbox.document.body.children)
    .filter(function (c) { return c && c.className === 'help-q'; });
  ck(badges.length >= 1, 'NeedHelp? built a numbered badge for the tagged O2C record (live host): ' + badges.length);
  L('§TOUR-BIND badges=' + badges.length + ' (host.has gates badge creation against real [data-ad-table])');
  L('§TOUR-BIND SUMMARY pass=' + pass + ' fail=' + fail);
  fs.writeFileSync(path.join(SRC, 'tour_bind_witness.log'), lines.join('\n') + '\n');
  console.log(lines.join('\n'));
  process.exit(fail === 0 ? 0 : 1);
}); });
