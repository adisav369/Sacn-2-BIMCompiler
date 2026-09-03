// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_genesis_resident.js — W-GENESIS-RESIDENT (GENESIS_RESIDENT_TENANT_SESSION.md §SPEC; SYSTEM_ADMIN_LANE §5 L1).
 *
 * ISSUE THIS PROVES: a tenant BORN from an op-log (genesis.js, W-GENESIS-MINIMAL) can be made a RESIDENT client of
 *   the live ad_seed — re-banded into a free client slot, merged into the SHARED dictionary DB, and (a) appear in
 *   SES.listClients so the login switcher lists it, (b) post its sales invoice to the cent FROM the resident DB,
 *   (c) leak ZERO rows into any other tenant. The crux is ID RE-BANDING with no collision (the one hard part).
 *
 * METHOD (NON-INVENT, §-log first — read the log, not the exit code):
 *   1. birthTenant({AcmeCo,USD}) → G1..G6, then append G7 = the $109 sample sales invoice (same one the wizard posts).
 *   2. Open a COPY of the REAL erp/ad_seed.db (resident = GardenWorld/11 only). Snapshot listClients + a GardenWorld
 *      row count + a GardenWorld PK as the 0-bleed baseline.
 *   3. nextClientId(db) → 17 (floor skips the 12-16 demo band). rebandGenesis(bundle,{clientId:17}).
 *   4. mergeGenesisInto(reband.groups, db) — column-intersect INSERT OR IGNORE into the resident CamelCase schema.
 *   5. ASSERT: listClients now lists AcmeCo(17) with ≥1 user · GardenWorld(11) row count + PK UNCHANGED (0 bleed) ·
 *      every re-banded row scoped ad_client_id=17 · the merged invoice posts 12110=109 / 41000=100 / 21610=9 balanced.
 *   6. Idempotent re-merge: a second mergeGenesisInto adds 0 net business rows (INSERT OR IGNORE + PK).
 *
 * ORACLE: idempiere_test default CoA — DR 12110 Receivable / CR 41000 Revenue / CR 21610 Tax-due (genesis_seed).
 */
'use strict';
var fs = require('fs');
var os = require('os');
var path = require('path');
var Database = require('better-sqlite3');
var G = require('../build/erp/genesis.js');
var R = require('./post_resolver');
var DP = require('./doc_poster');

var pass = 0, fail = 0;
function check(ok, msg, got) { (ok ? pass++ : fail++); console.log((ok ? '  ✓ ' : '  ✗ FAIL ') + msg + (got !== undefined ? '  → ' + got : '')); }

console.log('═══ W-GENESIS-RESIDENT — a born tenant becomes a RESIDENT client of the live ad_seed (login-able, posts to the cent, 0 bleed) ═══');
console.log('    birthTenant → rebandGenesis(17) → mergeGenesisInto(copy of REAL ad_seed.db) → listClients + post + scope + 0-bleed\n');

// ── locate the real resident seed (the one idempiere.html boots) ──────────────────────────────────────────────
var SEED_SRC = ['/home/red1/bim-ootb/erp/ad_seed.db', path.join(__dirname, '../../bim-ootb/erp/ad_seed.db')]
  .filter(function (p) { try { return fs.statSync(p).size > 1000000; } catch (e) { return false; } })[0];
if (!SEED_SRC) { console.log('  ✗ FAIL could not find a real ad_seed.db to merge into'); process.exit(1); }
var SEED_TMP = path.join(os.tmpdir(), 'wgenresident_ad_seed.db');
fs.copyFileSync(SEED_SRC, SEED_TMP);
console.log('§GENESIS-RESIDENT seed=' + SEED_SRC + ' copy=' + SEED_TMP);
var db = new Database(SEED_TMP);

// ── listClients (mirror SES.listClients exactly — same joins + IsActive gates) ────────────────────────────────
function listClients() {
  return db.prepare(
    'SELECT cl.AD_Client_ID AS id, cl.Name AS name, COUNT(DISTINCT ur.AD_User_ID) AS users ' +
    'FROM AD_Client cl ' +
    'JOIN AD_Role r ON r.AD_Client_ID = cl.AD_Client_ID AND r.IsActive = \'Y\' ' +
    'JOIN AD_User_Roles ur ON ur.AD_Role_ID = r.AD_Role_ID ' +
    'JOIN AD_User u ON u.AD_User_ID = ur.AD_User_ID AND u.IsActive = \'Y\' ' +
    'WHERE cl.IsActive = \'Y\' ' +
    'GROUP BY cl.AD_Client_ID, cl.Name ORDER BY cl.AD_Client_ID').all();
}

// ── 0-bleed baseline (GardenWorld / client 11) ────────────────────────────────────────────────────────────────
var before = listClients();
console.log('§GENESIS-RESIDENT before=[' + before.map(function (c) { return c.name + '(' + c.id + '):' + c.users; }).join(',') + ']');
var gwBpCount0 = db.prepare("SELECT COUNT(*) n FROM C_BPartner WHERE AD_Client_ID=11").get().n;
var gwEvCount0 = db.prepare("SELECT COUNT(*) n FROM C_ElementValue WHERE AD_Client_ID=11").get().n;
var gwAnyPk0 = db.prepare("SELECT MAX(C_BPartner_ID) m FROM C_BPartner WHERE AD_Client_ID=11").get().m;

// ── 1. BIRTH + G7 sample invoice ──────────────────────────────────────────────────────────────────────────────
var born = G.birthTenant({ clientName: 'AcmeCo', currencyId: 100, currencyPrecision: 2, adminUser: 'jdoe', dateAcct: '2024-01-15' });
var invId = 9000001;   // *_id > genesisBase → rebandGenesis bands it automatically (proven below)
var g7 = { seq: born.groups.length + 1, label: 'G7 sample invoice', ops: [
  { op: 'CREATE', table: 'c_invoice', row: { c_invoice_id: invId, c_bpartner_id: born.refs.bpartnerId, ad_client_id: born.refs.clientId, grandtotal: 109.00, issotrx: 'Y', ispaid: 'N', dateinvoiced: '2024-01-15', docstatus: 'CO' } },
  { op: 'CREATE', table: 'c_invoiceline', row: { c_invoice_id: invId, m_product_id: born.refs.productId, ad_client_id: born.refs.clientId, linenetamt: 100.00 } },
  { op: 'CREATE', table: 'c_invoicetax', row: { c_invoice_id: invId, c_tax_id: born.refs.taxId, ad_client_id: born.refs.clientId, taxamt: 9.00 } }
] };
var bundle = { input: born.input, groups: born.groups.concat([g7]), tip: born.tip, refs: born.refs };
console.log('§GENESIS-RESIDENT born client=AcmeCo groups=' + bundle.groups.length + ' (G1..G7) head=' + born.tip.slice(0, 12));

// ── 3. RE-BAND ────────────────────────────────────────────────────────────────────────────────────────────────
var newClient = G.nextClientId(db);
check(newClient === 17, 'nextClientId floors at 17 (skips the 12-16 demo band)', newClient);
var rb = G.rebandGenesis(bundle, { clientId: newClient });
console.log('§GENESIS-RESIDENT reband client=' + rb.clientId + ' bp=' + bundle.refs.bpartnerId + '→' + rb.refs.bpartnerId + ' inv=' + invId + '→' + rb.idMap[invId]);
check(rb.refs.bpartnerId === newClient * 100000 + (bundle.refs.bpartnerId - 1000000), 'data id banded to clientNum*100000', rb.refs.bpartnerId);
check(rb.idMap[invId] === newClient * 100000 + (invId - 1000000), 'G7 invoice pk re-banded automatically (*_id > base)', rb.idMap[invId]);

// re-banded ids must not collide with ANY resident id (sample the data tables)
var collide = 0;
['C_BPartner', 'C_ElementValue', 'M_Product', 'C_AcctSchema'].forEach(function (t) {
  var pk = t + '_ID';
  Object.keys(rb.idMap).forEach(function (k) {
    var v = rb.idMap[k]; if (v === newClient) return;
    try { if (db.prepare('SELECT 1 FROM ' + t + ' WHERE ' + pk + '=?').get(v)) collide++; } catch (e) {}
  });
});
check(collide === 0, 'no re-banded id collides with a resident PK (the crux)', collide + ' collisions');

// ── 4. MERGE into the resident DB ─────────────────────────────────────────────────────────────────────────────
var nMerged = G.mergeGenesisInto(rb.groups, db);
console.log('§GENESIS-RESIDENT merged rows=' + nMerged + ' into resident schema');
check(nMerged > 300, 'merged the born tenant (chart + masters + invoice) into the resident DB', nMerged + ' rows');

// ── 4b. GRANT the born admin role access to the shared System dictionary (else the menu is empty = not workable) ─
var winBefore = db.prepare("SELECT COUNT(*) n FROM AD_Window_Access WHERE AD_Role_ID=?").get(rb.refs.roleAdminId).n;
var nGrant = G.grantFullAccess(db, rb.refs.roleAdminId, newClient, rb.refs.orgId);
var winAfter = db.prepare("SELECT COUNT(*) n FROM AD_Window_Access WHERE AD_Role_ID=?").get(rb.refs.roleAdminId).n;
var sysWindows = db.prepare("SELECT COUNT(*) n FROM AD_Window WHERE IsActive='Y'").get().n;
console.log('§GENESIS-RESIDENT grant role=' + rb.refs.roleAdminId + ' windowAccess ' + winBefore + '→' + winAfter + ' (system windows=' + sysWindows + ')');
check(winAfter === sysWindows && winAfter > 300, 'born admin role granted access to the full shared dictionary (workable menu)', winAfter + ' windows');
var orgAcc = db.prepare("SELECT COUNT(*) n FROM AD_Role_OrgAccess WHERE AD_Role_ID=? AND AD_Org_ID=?").get(rb.refs.roleAdminId, rb.refs.orgId).n;
check(orgAcc === 1, 'born admin role has org access (can transact in its org)', orgAcc);

// ── 5a. listClients now lists the born tenant ─────────────────────────────────────────────────────────────────
var after = listClients();
console.log('§GENESIS-RESIDENT after=[' + after.map(function (c) { return c.name + '(' + c.id + '):' + c.users; }).join(',') + ']');
var acme = after.filter(function (c) { return c.id === newClient; })[0];
check(!!acme && /Acme/i.test(acme.name), 'login switcher would list the born tenant (SES.listClients)', acme ? acme.name + '(' + acme.id + ')' : 'ABSENT');
check(acme && acme.users >= 1, 'born tenant has a login-able user (role+user+user_roles, IsActive=Y injected)', acme && acme.users);

// ── 5b. 0-bleed into GardenWorld ──────────────────────────────────────────────────────────────────────────────
var gwBpCount1 = db.prepare("SELECT COUNT(*) n FROM C_BPartner WHERE AD_Client_ID=11").get().n;
var gwEvCount1 = db.prepare("SELECT COUNT(*) n FROM C_ElementValue WHERE AD_Client_ID=11").get().n;
var gwAnyPk1 = db.prepare("SELECT MAX(C_BPartner_ID) m FROM C_BPartner WHERE AD_Client_ID=11").get().m;
check(gwBpCount1 === gwBpCount0 && gwEvCount1 === gwEvCount0 && gwAnyPk1 === gwAnyPk0,
  '0 bleed: GardenWorld(11) row counts + PK unchanged', 'bp ' + gwBpCount0 + '→' + gwBpCount1 + ' ev ' + gwEvCount0 + '→' + gwEvCount1);
var gwUnchanged = before.filter(function (c) { return c.id === 11; })[0];
var gwAfter = after.filter(function (c) { return c.id === 11; })[0];
check(gwUnchanged && gwAfter && gwUnchanged.users === gwAfter.users, 'GardenWorld still listed, user count unchanged', gwAfter && gwAfter.users);

// ── 5c. every re-banded row scoped to the born client ─────────────────────────────────────────────────────────
var bpClient = db.prepare("SELECT AD_Client_ID c FROM C_BPartner WHERE C_BPartner_ID=?").get(rb.refs.bpartnerId);
var evScoped = db.prepare("SELECT COUNT(*) n FROM C_ElementValue WHERE AD_Client_ID=?").get(newClient).n;
check(bpClient && Number(bpClient.c) === newClient, 'born BP scoped to its own client', bpClient && bpClient.c);
check(evScoped >= 300, 'the full chart is scoped to the born client', evScoped + ' accounts @ client ' + newClient);

// ── 5d. the resident invoice posts to the cent (doc_poster over the merged DB) ────────────────────────────────
var gl = DP.derivePostings(db, { table: 'C_Invoice', id: rb.idMap[invId] }, rb.refs.acctSchemaId, R);
var byVal = {}; gl.lines.forEach(function (l) { byVal[l.value] = l; });
var rcv = byVal['12110'], rev = byVal['41000'], tax = byVal['21610'];
var cent = rcv && rcv.amtacctdr === 109 && rev && rev.amtacctcr === 100 && tax && tax.amtacctcr === 9;
console.log('§GENESIS-RESIDENT post basis=' + gl.basis + ' balanced=' + gl.balanced + ' absent=' + JSON.stringify(gl.absent)
  + ' DR12110=' + (rcv && rcv.amtacctdr) + ' CR41000=' + (rev && rev.amtacctcr) + ' CR21610=' + (tax && tax.amtacctcr));
check(gl.balanced && gl.absent.length === 0 && cent, 'resident born-tenant invoice posts == oracle, to the cent', 'cent=' + cent + ' balanced=' + gl.balanced);

// ── 6. idempotent re-merge ────────────────────────────────────────────────────────────────────────────────────
var bpCountPre = db.prepare("SELECT COUNT(*) n FROM C_BPartner WHERE AD_Client_ID=?").get(newClient).n;
G.mergeGenesisInto(rb.groups, db);
var bpCountPost = db.prepare("SELECT COUNT(*) n FROM C_BPartner WHERE AD_Client_ID=?").get(newClient).n;
check(bpCountPost === bpCountPre, 'idempotent re-merge adds 0 dup rows (INSERT OR IGNORE + PK)', bpCountPre + '→' + bpCountPost);

db.close();
console.log('\n═══ W-GENESIS-RESIDENT RESULT=' + (fail === 0 ? 'PASS' : 'FAIL') + '  (' + pass + ' pass / ' + fail + ' fail) ═══');
process.exit(fail === 0 ? 0 : 1);
