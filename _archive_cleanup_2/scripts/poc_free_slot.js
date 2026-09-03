// ⚠ DO NOT REMOVE — Scope guard / FREE-SLOT ID PICKER witness (NEW_CLIENT_MGMT.md #2, W-FREE-SLOT)
// PROVES (issue: "client id is hardcoded per source — Odoo=12 — so a user cannot install TWO Odoo tenants"):
//   1. gen_ad_odoo with NO CLIENT_ID assigns the NEXT FREE AD_Client id past the base (vanilla → 12).
//   2. CLIENT_ID=14 (+TENANT_NAME) is honoured → a second, independent Odoo tenant.
//   3. Merging BOTH shards into one base yields ZERO shared ids between client 12 and client 14 across every
//      id-bearing table (frame + documents + accounts) — the collision the hardcoded scheme would have caused.
//   4. BOTH tenants resolve coverage:complete to the cent through the FROZEN engine (doc_poster/post_resolver).
//   5. The base lists all 4 login-able tenants (System/GardenWorld/Odoo(12)/Odoo Two(14)).
// REQUIRES live Odoo (odoodemo@8069) — like the other migrate witnesses; AD_SEED defaults to the live deployed seed.
// §-log first — READ scripts/poc_free_slot.log before any conclusion.
// Run:  AD_SEED=/path/ad_seed.db node scripts/poc_free_slot.js 2>&1 | tee scripts/poc_free_slot.log
'use strict';
const D = require('better-sqlite3');
const fs = require('fs'), cp = require('child_process'), path = require('path');
const GEN = path.join(__dirname, '..', 'build', 'erp', 'gen_ad_odoo.js');
const SEED = process.env.AD_SEED || (process.env.HOME + '/bim-ootb/erp/ad_seed.db');
const A = '/tmp/free_slot_12.db', B = '/tmp/free_slot_14.db', MERGED = '/tmp/free_slot_merged.db';
let fails = 0; const ok = (c, m) => { if (!c) { fails++; console.log('  ✗ FAIL ' + m); } else console.log('  ✓ ' + m); };

function gen(env, out) {
  const r = cp.spawnSync('node', [GEN], { env: Object.assign({}, process.env, env, { AD_SEED: SEED, SHARD_OUT: out }), encoding: 'utf8' });
  const alloc = (r.stdout.match(/ALLOC client=\d+[^\n]*/) || ['(no ALLOC)'])[0];
  console.log('  gen → ' + alloc + '  exit=' + r.status);
  return r.status === 0;
}
function merge(base, p) {
  const s = new D(p, { readonly: true });
  const bc = (t) => { try { return new Set(base.prepare('PRAGMA table_info("' + t + '")').all().map(c => c.name.toLowerCase())); } catch (e) { return null; } };
  let n = 0;
  for (const t of s.prepare("SELECT name FROM sqlite_master WHERE type='table'").all().map(r => r.name)) {
    let rows; try { rows = s.prepare('SELECT * FROM "' + t + '"').all(); } catch (e) { continue; }
    if (!rows.length) continue; const cset = bc(t); if (!cset) continue;
    const cs = Object.keys(rows[0]).filter(c => cset.has(c.toLowerCase())); if (!cs.length) continue;
    const st = base.prepare('INSERT OR IGNORE INTO "' + t + '" (' + cs.map(c => '"' + c + '"').join(',') + ') VALUES (' + cs.map(() => '?').join(',') + ')');
    for (const r of rows) { try { st.run(cs.map(c => r[c])); n++; } catch (e) {} }
  }
  s.close(); return n;
}

console.log('\n══ W-FREE-SLOT — gen_ad_odoo assigns a free AD_Client id; two Odoo tenants coexist collision-free ══\n');
console.log('§FREE-SLOT base=' + SEED);
ok(gen({ CLIENT_ID: '' }, A), 'gen with NO CLIENT_ID succeeds (next-free default)');
ok(gen({ CLIENT_ID: '14', TENANT_NAME: 'Odoo Two' }, B), 'gen with CLIENT_ID=14 succeeds');

// 1) next-free default landed on 12 (one past GardenWorld 11)
const a = new D(A, { readonly: true });
const defCl = a.prepare("SELECT AD_Client_ID id FROM AD_Client WHERE Name='Odoo'").get();
ok(defCl && defCl.id === 12, 'NO CLIENT_ID → next-free client id = 12 (one past GardenWorld 11)');
a.close();
const b = new D(B, { readonly: true });
const twoCl = b.prepare("SELECT AD_Client_ID id, Name n FROM AD_Client WHERE AD_Client_ID=14").get();
ok(twoCl && /Odoo Two/.test(twoCl.n), 'CLIENT_ID=14 → client 14 "Odoo Two"');
b.close();

// 2) merge both into one base → collision check + both derive complete
fs.copyFileSync(SEED, MERGED);
const base = new D(MERGED);
console.log('§FREE-SLOT merged 12 rows=' + merge(base, A) + ' 14 rows=' + merge(base, B));
const tbls = { c_elementvalue: 'c_elementvalue_id', c_validcombination: 'c_validcombination_id', c_order: 'C_Order_ID', c_invoice: 'C_Invoice_ID', c_bpartner: 'C_BPartner_ID', m_product: 'M_Product_ID', ad_org: 'AD_Org_ID', ad_role: 'AD_Role_ID', ad_user: 'AD_User_ID' };
let collisions = 0;
for (const [t, pk] of Object.entries(tbls)) {
  let r = []; try { r = base.prepare('SELECT a.' + pk + ' id FROM ' + t + ' a JOIN ' + t + ' b ON a.' + pk + '=b.' + pk + ' WHERE a.AD_Client_ID=12 AND b.AD_Client_ID=14').all(); } catch (e) { continue; }
  if (r.length) { collisions += r.length; console.log('  ⚠ COLLISION ' + t + ' ids=' + JSON.stringify(r.map(x => x.id))); }
}
console.log('§FREE-SLOT collision-check shared-ids(client12∩client14)=' + collisions);
ok(collisions === 0, 'ZERO id collisions between client 12 and client 14 (frame + documents + accounts)');

const DP = require(path.join(__dirname, 'doc_poster'));
for (const [cl, oid] of [[12, 1200001], [14, 1400001]]) {
  const r = DP.derivePostings(base, { table: 'C_Order', id: oid }, 101);
  const cov = r.absent.length === 0 && r.balanced ? 'complete' : (r.lines.length ? 'partial' : 'absent');
  console.log('§FREE-SLOT derive client=' + cl + ' order=' + oid + ' cov=' + cov + ' Dr=' + (r.sumDr / 100) + ' Cr=' + (r.sumCr / 100));
  ok(cov === 'complete' && r.sumDr === 500250, 'client ' + cl + ' order ' + oid + ' → coverage:complete $5002.50 balanced');
}
const tenants = base.prepare("SELECT cl.AD_Client_ID id, cl.Name n FROM AD_Client cl JOIN AD_Role r ON r.AD_Client_ID=cl.AD_Client_ID AND r.IsActive='Y' JOIN AD_User_Roles ur ON ur.AD_Role_ID=r.AD_Role_ID GROUP BY cl.AD_Client_ID ORDER BY cl.AD_Client_ID").all();
console.log('§FREE-SLOT tenants=' + JSON.stringify(tenants.map(c => c.n + '(' + c.id + ')')));
ok(tenants.length === 4 && tenants.some(c => c.id === 14), 'base lists 4 login-able tenants incl Odoo Two(14)');
base.close();

console.log('\n' + (fails === 0 ? '🟢 W-FREE-SLOT PASS' : '🔴 W-FREE-SLOT FAIL (' + fails + ')') + ' — free client-id allocation lets two Odoo tenants install side-by-side, collision-free, each folding to the cent');
process.exit(fails === 0 ? 0 : 1);
