#!/usr/bin/env node
/**
 * poc_role_scoped_match.js — close the two remaining gaps on the GENERIC matcher:
 *   (1) AMBIGUITY  — >1 candidate line per partition (the real Detail⋈Detail hard case);
 *                    disambiguated by a pluggable ORDERING POLICY (FIFO/LIFO), not code.
 *   (2) ROLE SCOPE — role/org access NARROWS the matcher's partition (you can only match
 *                    within orgs your role sees) — access is a predicate ON the edge,
 *                    composing INTO the matcher, not a separate security engine (§0.8).
 *
 * Runs the SAME separated engine (scripts/erp_engine.js) under sql.js — the BROWSER binding
 * — to prove the core is DB-binding-agnostic (the better-sqlite3↔sql.js split was the other
 * separation debt). GardenWorld is too clean to test ambiguity, so we use a SYNTHETIC fixture;
 * the oracle here is the FIFO/LIFO SPECIFICATION (clearly flagged) — the real-data match was
 * already proven 18/18 against the iDempiere oracle in poc_sales_to_ship.js.
 *
 * Run: node scripts/poc_role_scoped_match.js 2>&1 | tee build/erp/poc_match.log
 */
'use strict';
var initSqlJs = require('sql.js');
var E = require('./erp_engine');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function pick(pairs) { return pairs.length ? pairs[0][1] : null; } // the receipt chosen for the invoice

(async function () {
  var SQL = await initSqlJs();
  var db = new SQL.Database();
  // minimal fixture: one BPartner(100), one product(1), AMBIGUOUS — two receipts of the
  // same product+qty, different org + date; one vendor invoice line.
  db.run(
    'CREATE TABLE m_inoutline(id INT, bp INT, org INT, m_product_id INT, qty REAL, mdate TEXT);' +
    'CREATE TABLE c_invoiceline(id INT, bp INT, org INT, m_product_id INT, qty REAL);' +
    "INSERT INTO m_inoutline VALUES (1,100,1000,1,10,'2026-01-01'),(2,100,2000,1,10,'2026-01-02');" +
    "INSERT INTO c_invoiceline VALUES (10,100,1000,1,10);");
  // host query fn (this is the sql.js binding the engine is agnostic to).
  function q(sql) { var r = db.exec(sql); if (!r.length) return []; return r[0].values.map(function (v) { var o = {}; r[0].columns.forEach(function (c, i) { o[c] = v[i]; }); return o; }); }

  var receipts = q('SELECT id, bp, org, m_product_id, qty, mdate FROM m_inoutline');
  var invoices = q('SELECT id, bp, org, m_product_id, qty FROM c_invoiceline');
  console.log('═══ POC — role-scoped, policy-tiebroken GENERIC matcher (engine on sql.js) ═══\n');
  console.log('Fixture: BPartner 100, product 1, qty 10 — receipts R1(org1000,Jan1) R2(org2000,Jan2); invoice I1(org1000). AMBIGUOUS.\n');

  var base = { idL: 'id', idR: 'id', qtyL: 'qty', qtyR: 'qty',
    partition: function (r) { return r.bp; }, orgOf: function (r) { return r.org; },
    dateOf: function (r) { return r.mdate; } };

  // ── (1) AMBIGUITY — ordering policy disambiguates, deterministically ────────
  console.log('── (1) AMBIGUITY: pluggable ordering policy picks the right candidate ──');
  var fifo = pick(E.match(invoices, receipts, Object.assign({}, base, { order: 'FIFO' })));
  var lifo = pick(E.match(invoices, receipts, Object.assign({}, base, { order: 'LIFO' })));
  verdict(fifo === 1, 'FIFO picks earliest receipt R1', 'chose receipt id=' + fifo + ' (spec: 1)');
  verdict(lifo === 2, 'LIFO picks latest receipt R2', 'chose receipt id=' + lifo + ' (spec: 2)');
  verdict(fifo !== lifo, 'policy is a real knob (FIFO≠LIFO, deterministic)', 'FIFO=' + fifo + ' LIFO=' + lifo);

  // ── (2) ROLE SCOPE — org access narrows the partition, overriding policy ────
  console.log('\n── (2) ROLE SCOPE: org-access narrows the matcher partition (§0.8) ──');
  // role sees ONLY org 1000. Even LIFO (which prefers R2@org2000) must pick R1@org1000,
  // because R2 is outside the role's visible partition.
  var scoped = pick(E.match(invoices, receipts, Object.assign({}, base, { order: 'LIFO', allowOrgs: new Set([1000]) })));
  verdict(scoped === 1, 'LIFO + role-scope(org 1000) picks R1 (R2 invisible)', 'chose receipt id=' + scoped + ' (spec: 1 — access overrides policy)');
  // and the same role, no policy, still cannot reach org 2000.
  var scopedNoPol = pick(E.match(invoices, receipts, Object.assign({}, base, { allowOrgs: new Set([2000]) })));
  // role sees only org 2000: invoice I1 is org 1000 -> invoice itself invisible -> no match.
  verdict(scopedNoPol === null, 'role-scope(org 2000) sees no in-scope invoice → no match', 'chose=' + scopedNoPol + ' (spec: null)');

  // ── (3) GUARD path under sql.js — a predicate rule evaluated by the engine ──
  console.log('\n── (3) GUARD: engine.evalGuard runs a predicate over the same sql.js db ──');
  var g = E.evalGuard(q, { form: 'sql', body: "SELECT count(*) n FROM m_inoutline WHERE bp=@bp@" }, { bp: 100 });
  verdict(g.ok && g.rows[0].n === 2, 'evalGuard resolves @bp@ + runs under sql.js', g.ok ? 'rows=' + g.rows[0].n : g.reason);

  db.close();
  console.log('\n═══ VERDICT ═══');
  console.log('§POCMATCH ' + (fails ? 'FAIL — ' + fails + ' red' : 'PASS — one engine: match + ordering-policy + role-scope + guard, all via opts/data'));
  console.log('§POCMATCH note: ambiguity oracle = FIFO/LIFO SPEC (synthetic); real-data match was 18/18 vs iDempiere oracle (poc_sales_to_ship)');
  process.exit(fails ? 1 : 0);
})();
