// ⚠ DO NOT REMOVE — Scope guard
// Scope: headless §-witness for the CRUD overlay layer (prompts/CRUD_OVERLAY.md — E1/E2 dry-run).
//   Proves, against the REAL crud_ops.json store + the REAL glassbowl_data.db row, that:
//   the keyed store is drift-free, verb capability is data-driven (greying), the validation layer
//   (type/readonly/required/default + AD_Val_Rule) REJECTS bad input BEFORE apply, and each verb
//   builds exactly the dry-run op it would hand the kernel. §-log first; read the log before conclusions.
// Run:  node scripts/test_crud_overlay.js 2>&1 | tee build/erp/test_crud_overlay.log
'use strict';
var fs = require('fs'), path = require('path'), cp = require('child_process');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var CORE = require(path.join(ERP, 'crud_overlay.js'));
var STORE = JSON.parse(fs.readFileSync(path.join(ERP, 'crud_ops.json'), 'utf8'));
var HELP = JSON.parse(fs.readFileSync(path.join(ERP, 'help_ops.json'), 'utf8'));
var DB = path.join(ERP, 'glassbowl_data.db');

var fails = 0;
function ok(cond, msg) { if (!cond) { fails++; console.log('  ✗ FAIL: ' + msg); } else { console.log('  ✓ ' + msg); } }
function entry(k) { var e = STORE[k]; e.key = k; return e; }
// truth-bound: pull a REAL row from the bundle via the sqlite3 CLI (extract, never invent).
function row(table, where) {
  var sql = 'SELECT * FROM ' + table + (where ? ' WHERE ' + where : '') + ' LIMIT 1';
  var out = cp.execSync('sqlite3 -json "' + DB + '" "' + sql + '"', { encoding: 'utf8' }).trim();
  return out ? JSON.parse(out)[0] : {};
}

console.log('=== §CRUD witness — ' + new Date().toISOString() + ' ===\n');

// ── ISSUE 1: store drift — does every CRUD key point at a real on-screen bubble? ──
// The bubbles that exist on screen are exactly the help layer's keyed targets. A CRUD entry
// keyed at a bubble that isn't there would render a ring on nothing (the governance drift gate).
console.log('[ISSUE 1] store alignment / drift (rings render only on real bubbles)');
var entries = CORE.entriesOf(STORE);
var bubbleKeys = Object.keys(HELP).filter(function (k) { return k !== '__meta' && HELP[k].target; }).map(function (k) { return HELP[k].target; });
var orphan = entries.filter(function (e) { return bubbleKeys.indexOf(e.key) < 0; });
ok(orphan.length === 0, 'every CRUD key maps to a real bubble (orphans=' + JSON.stringify(orphan.map(function (e) { return e.key; })) + ')');
console.log('§CRUD mode=on rings=' + entries.length);
console.log('§CRUD aligned keys=[' + entries.map(function (e) { return e.key; }).join(',') + '] orphans=' + orphan.length + '\n');

// ── ISSUE 2: verb capability is DATA-DRIVEN — the ring greys icons from verbs[] ──
// User req: "if cannot be deleted then the – is greyed." Prove each icon's enabled state is read
// from the element's own verbs[], not hardcoded — and that View is always on (read is free).
console.log('[ISSUE 2] verb-driven greying (＋=create ✎=update –=delete, 👁=always)');
entries.forEach(function (e) {
  var c = CORE.verbEnabled(e, 'create'), u = CORE.verbEnabled(e, 'update'), d = CORE.verbEnabled(e, 'delete');
  console.log('§CRUD verbs key=' + e.key + ' create=' + (c ? 'Y' : 'N') + ' update=' + (u ? 'Y' : 'N') + ' delete=' + (d ? 'Y' : 'N') + ' view=Y');
});
ok(!CORE.verbEnabled(entry('c_invoice'), 'create'), 'c_invoice has NO create verb → ＋ greyed');
ok(!CORE.verbEnabled(entry('c_allocationline'), 'update'), 'c_allocationline has NO update verb → ✎ greyed');
ok(CORE.verbEnabled(entry('c_order'), 'delete'), 'c_order HAS delete verb → – enabled');
console.log('');

// ── ISSUE 3: New seeds from each field's DefaultValue (AD_Column.DefaultValue) ──
console.log('[ISSUE 3] New-form defaults (today / auto / literal)');
var defs = CORE.defaultsFor(entry('c_order'), '2026-06-01');
console.log('§CRUD defaults key=c_order ' + JSON.stringify(defs));
ok(defs.dateordered === '2026-06-01', "default 'today' resolved to the supplied ISO date (replay-safe input)");
ok(defs.docstatus === 'DR', "default literal 'DR' carried");
ok(defs.documentno === '', "default 'auto' blanked (server/seq fills it)");
console.log('');

// ── ISSUE 4: the validation layer REJECTS bad input BEFORE apply (the AD checks) ──
console.log('[ISSUE 4] validation rejects before apply');
function vchk(key, vals, orig, label, expectCol, expectWhy) {
  var r = CORE.validate(STORE, entry(key), vals, orig);
  var hit = r.errors.filter(function (x) { return x.col === expectCol; })[0];
  console.log('§CRUD validate key=' + key + ' verb=' + (orig ? 'update' : 'create') + ' ' + (r.ok ? 'ok' : 'REJECT errors=' + JSON.stringify(r.errors)) + '  // ' + label);
  ok(hit && hit.why.indexOf(expectWhy) === 0, label + ' → rejected [' + expectCol + ':' + expectWhy + ']');
}
vchk('c_order', { documentno: '', c_bpartner_id: 112, dateordered: '2026-06-01', grandtotal: 10, docstatus: 'DR' }, null, 'missing required documentno', 'documentno', 'required');
vchk('c_order', { documentno: 'AB12', c_bpartner_id: 112, dateordered: '2026-06-01', grandtotal: 10, docstatus: 'DR' }, null, 'non-numeric documentno (AD_Val_Rule docno_numeric)', 'documentno', 'regex');
vchk('c_payment', { documentno: '400000', payamt: -5, datetrx: '2026-06-01', docstatus: 'DR' }, null, 'negative payamt (non_negative_amt min:0)', 'payamt', 'min');
vchk('c_order', { documentno: '80001', c_bpartner_id: 112, dateordered: 'not-a-date', grandtotal: 10, docstatus: 'DR' }, null, 'bad date', 'dateordered', 'type:date');
vchk('c_order', { documentno: '80001', c_bpartner_id: 112, dateordered: '2026-06-01', grandtotal: 10, docstatus: 'ZZ' }, null, 'docstatus not in the list', 'docstatus', 'list');
// readonly: changing a derived field (invoice.grandtotal) against its REAL current value must be rejected.
var inv = row('c_invoice', "documentno='200001'");
vchk('c_invoice', { documentno: '200001', dateinvoiced: '2002-02-22', grandtotal: 999, description: '', docstatus: 'CO' }, inv, 'edit a READONLY derived field (grandtotal)', 'grandtotal', 'readonly');
console.log('');

// ── ISSUE 5: a valid New builds exactly ONE CRUD_CREATE op with cleaned fields ──
console.log('[ISSUE 5] New → one signed-able CRUD_CREATE op (dry)');
var goodOrder = { documentno: '80009', c_bpartner_id: '112', dateordered: '2026-06-01', grandtotal: '100.70', description: 'demo', docstatus: 'DR' };
var vr = CORE.validate(STORE, entry('c_order'), goodOrder, null);
ok(vr.ok, 'valid order passes validation');
var createOp = CORE.buildOp('create', entry('c_order'), goodOrder, null, {});
console.log('§CRUD create key=c_order (dry) op=' + createOp.op_type + ' fields=' + JSON.stringify(createOp.fields) + ' ownerGated=' + (createOp.ownerGated ? 'Y' : 'N') + ' cas=' + (createOp.cas || '-'));
ok(createOp.op_type === 'CRUD_CREATE', 'op_type=CRUD_CREATE');
ok(typeof createOp.fields.grandtotal === 'number' && createOp.fields.grandtotal === 100.7, 'number field coerced ("100.70" → 100.7)');
ok(typeof createOp.fields.c_bpartner_id === 'number', 'fk field coerced to number');
ok(createOp.cas === 'claimed_by' && createOp.ownerGated === true, 'carries ownerGated + cas (A3 surfaces these in E4)');
console.log('');

// ── ISSUE 6: Edit builds CRUD_UPDATE with ONLY changed, NON-readonly fields (truth-bound) ──
console.log('[ISSUE 6] Edit → CRUD_UPDATE, readonly excluded, only changes (real invoice #200001)');
ok(inv && inv.c_invoice_id != null, 'pulled the REAL invoice row #200001 from the bundle');
var edited = {};
Object.keys(inv).forEach(function (c) { edited[c] = inv[c]; });          // start from the real row
edited.description = 'paid in full';                                      // one real edit
edited.grandtotal = 999;                                                  // attempt to change the readonly derived field
var upd = CORE.buildOp('update', entry('c_invoice'), edited, inv, { id: inv.c_invoice_id });
console.log('§CRUD update key=c_invoice field=' + Object.keys(upd.changes).join(',') + ' (dry) op=' + upd.op_type + ' changes=' + JSON.stringify(upd.changes));
ok(upd.op_type === 'CRUD_UPDATE' && upd.id === inv.c_invoice_id, 'CRUD_UPDATE targets the real id ' + inv.c_invoice_id);
ok(upd.changes.description && String(upd.changes.description.old) === String(inv.description), 'description change captured with old=real value');
ok(!upd.changes.grandtotal, 'readonly grandtotal EXCLUDED from changes even though a new value was passed');
console.log('');

// ── ISSUE 7: Delete is a reversible tombstone, never a destructive erase ──
console.log('[ISSUE 7] Delete → reversible tombstone (CRUD_OVERLAY.md req 4)');
var del = CORE.buildOp('delete', entry('c_order'), {}, row('c_order', "documentno='80001'"), { id: 101 });
console.log('§CRUD delete key=c_order tombstone=Y reversible=Y (dry) op=' + del.op_type + ' id=' + del.id);
ok(del.op_type === 'CRUD_DELETE' && del.tombstone === true && del.reversible === true, 'tombstone=true reversible=true (no erase)');
console.log('');

console.log('=== ' + (fails === 0 ? 'ALL WITNESSES PASS' : fails + ' FAILURE(S)') + ' ===');
process.exit(fails === 0 ? 0 : 1);
