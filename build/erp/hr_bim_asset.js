// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// hr_bim_asset.js — Implementing prompts/RESUME_HR_BIM_ASSET.md
//   §PILLAR-4 (one generic periodic RUN) · §BINDING (non-invent geometry) · §SPATIAL-VIEW (lens data)
//   §CROSS-APP (one signed op-log threads Viewer/ERP/HR).
// Witness: scripts/witness_hr_bim_asset.js  (§HBA P-one-engine / P-tenancy-term / P-tenancy-gl / P-bind / P-chain)
//
// ALPHA / DEMONSTRATOR. Every output row carries WATERMARK. Demo values only — no statutory figure asserted.
// EXTRACT OR COMPILE ONLY: the unit/asset guid is a REAL guid extracted from the building; a non-matching guid
// is honestly shown un-linked, never a fabricated binding. No Date.now / Math.random — deterministic + replayable.
(function () {
  'use strict';

  var WATERMARK = 'CONTOH — TIDAK RASMI / SAMPLE — NOT OFFICIAL';

  // Demo chart of accounts (operate-phase). Real numbers, not asserted as any entity's actual COA.
  var ACCT = {
    AR: 1200, ACCRUED: 2100, WAGES_PAYABLE: 2200,
    RENT_INCOME: 4100, STRATA_INCOME: 4200,
    WAGE_EXPENSE: 5100, MAINT_EXPENSE: 5300
  };

  // ── period helpers — a period key is 'YYYY-MM'; comparison is lexicographic (== numeric for fixed-width). ──
  function ym2num(p) { var a = String(p).split('-'); return (Number(a[0]) * 12) + Number(a[1]); }
  function round2(n) { return Math.round((Number(n) + Number.EPSILON) * 100) / 100; }
  // inclusive term test; null start = open-from-beginning, null end = open-ended.
  function inTerm(period, start, end) {
    if (start && period < start) return false;
    if (end && period > end) return false;
    return true;
  }

  // ── PROFILES — config, NOT code forks. The only per-profile logic is `select` (which obligations live this
  //    period); everything downstream (line → journal → signed group) is the ONE shared engine. ──
  var PROFILES = {
    PAYROLL: {
      key: 'PAYROLL', name: 'Payroll', cashDir: 'OUT', dr: ACCT.WAGE_EXPENSE, cr: ACCT.WAGES_PAYABLE,
      select: function (seed, period) {
        return (seed.employees || [])
          .filter(function (e) { return inTerm(period, e.hired, e.left); })
          .map(function (e) { return { party: e.party, amount: round2(e.gross), bindGuid: null, meta: { kind: 'payslip' } }; });
      }
    },
    RENTRUN: {                                       // tenancy = the payroll engine inverted (cash IN, AR)
      key: 'RENTRUN', name: 'Rent run', cashDir: 'IN', dr: ACCT.AR, cr: ACCT.RENT_INCOME,
      select: function (seed, period) {
        return (seed.leases || [])
          .filter(function (l) { return inTerm(period, l.term_start, l.term_end); })   // §HBA P-tenancy-term
          .map(function (l) { return { party: l.tenant, amount: round2(l.rent), bindGuid: l.unit_guid, meta: { kind: 'rent', unit_guid: l.unit_guid } }; });
      }
    },
    STRATA: {
      key: 'STRATA', name: 'Strata', cashDir: 'IN', dr: ACCT.AR, cr: ACCT.STRATA_INCOME,
      select: function (seed, period) {
        return (seed.owners || [])
          .map(function (o) { return { party: o.party, amount: round2(o.levy), bindGuid: o.unit_guid, meta: { kind: 'strata', unit_guid: o.unit_guid, share: o.share } }; });
      }
    },
    MAINTENANCE: {
      key: 'MAINTENANCE', name: 'Maintenance', cashDir: 'OUT', dr: ACCT.MAINT_EXPENSE, cr: ACCT.ACCRUED,
      select: function (seed, period) {
        return (seed.assets || [])
          .filter(function (a) { return a.next_due && a.next_due <= period; })          // due / overdue this period
          .map(function (a) { return { party: a.vendor || 'PM-INHOUSE', amount: round2(a.pm_cost), bindGuid: a.bim_guid, meta: { kind: 'pm', bim_guid: a.bim_guid, next_due: a.next_due } }; });
      }
    }
  };

  // ── §PILLAR-4 — the ONE generic periodic RUN. (period × parties × element-rules) → balanced lines → legs. ──
  function runPeriod(seed, profileKey, period) {
    var prof = PROFILES[profileKey];
    if (!prof) throw new Error('unknown profile: ' + profileKey);
    var obligations = prof.select(seed, period);
    var lines = obligations.map(function (ob, i) {
      return {
        line_no: i + 1, profile: prof.key, period: period, party: ob.party, amount: ob.amount,
        drAccount: prof.dr, crAccount: prof.cr, bindGuid: ob.bindGuid || null, meta: ob.meta, watermark: WATERMARK
      };
    });
    // journal — each line is a balanced double-entry pair by construction; the run is balanced ⇔ ΣDr == ΣCr.
    var legs = [];
    lines.forEach(function (l) {
      legs.push({ account: l.drAccount, amtacctdr: l.amount, amtacctcr: 0, party: l.party, line_no: l.line_no, bindGuid: l.bindGuid });
      legs.push({ account: l.crAccount, amtacctdr: 0, amtacctcr: l.amount, party: l.party, line_no: l.line_no, bindGuid: l.bindGuid });
    });
    var sumDr = round2(legs.reduce(function (s, g) { return s + g.amtacctdr; }, 0));
    var sumCr = round2(legs.reduce(function (s, g) { return s + g.amtacctcr; }, 0));
    return {
      profile: prof.key, name: prof.name, cashDir: prof.cashDir, period: period, lines: lines,
      journal: { legs: legs, sumDr: sumDr, sumCr: sumCr, balanced: sumDr === sumCr }, watermark: WATERMARK
    };
  }

  // ── §BINDING — non-invent join of records against the building's REAL guid set. ──
  // buildingIndex: { has(guid)->bool, storeyOf(guid)->string|null }.  guidOf reads unit_guid|bim_guid|bindGuid.
  function guidOf(r) { return r.unit_guid || r.bim_guid || r.bindGuid || null; }
  function bindUnits(records, buildingIndex) {
    return (records || []).map(function (r) {
      var g = guidOf(r);
      var bound = !!g && buildingIndex.has(g);                    // join HIT → real; MISS → honestly un-linked
      return Object.assign({}, r, { bound: bound, anchor: bound ? g : null, storey: bound ? buildingIndex.storeyOf(g) : null });
    });
  }

  // ── §SPATIAL-VIEW — pure lens data the viewer renders (no business logic in the UI). ──
  function spatialView(seed, buildingIndex, period, opts) {
    var horizon = (opts && opts.horizonMonths != null) ? opts.horizonMonths : 3;
    var units = (seed.leases || []).map(function (l) {
      var bound = buildingIndex.has(l.unit_guid);
      var status;
      if (!bound) status = 'unlinked';                           // never tinted — honest
      else if (!inTerm(period, l.term_start, l.term_end)) status = 'vacant';
      else {
        var d = l.term_end ? (ym2num(l.term_end) - ym2num(period)) : 999;
        status = (d >= 0 && d <= horizon) ? 'expiring' : 'occupied';
      }
      return { guid: l.unit_guid, status: status, bound: bound, storey: bound ? buildingIndex.storeyOf(l.unit_guid) : null, watermark: WATERMARK };
    });
    // population-density dots per real IfcBuildingStorey: occupied (incl. expiring) over total bound units.
    var byStorey = {};
    units.forEach(function (u) {
      if (!u.bound) return;
      var k = u.storey || 'Unknown';
      byStorey[k] = byStorey[k] || { storey: k, occupied: 0, total: 0 };
      byStorey[k].total++;
      if (u.status === 'occupied' || u.status === 'expiring') byStorey[k].occupied++;
    });
    var storeys = Object.keys(byStorey).map(function (k) {
      var s = byStorey[k]; s.density = s.total ? round2(s.occupied / s.total) : 0; return s;
    });
    var assets = (seed.assets || []).map(function (a) {
      var bound = buildingIndex.has(a.bim_guid);
      var state = !a.next_due ? 'ok' : (a.next_due < period ? 'overdue' : (a.next_due === period ? 'due' : 'ok'));
      return { guid: a.bim_guid, state: state, bound: bound, storey: bound ? buildingIndex.storeyOf(a.bim_guid) : null, watermark: WATERMARK };
    });
    return { units: units, storeys: storeys, assets: assets, watermark: WATERMARK };
  }

  // ── §CROSS-APP / §PILLAR-4 commit — fold the whole run as ONE signed op-group on the shared kernel op-log. ──
  // Each HBA_RUN_LINE carries input_guids=[bound geometry guid] (BIM WHERE) + params.party (ERP/HR WHO).
  // KO = window.KernelOps (commitGroup/verifyChain). Returns the commitGroup verdict.
  function commitRun(KO, db, run, groupMeta) {
    var ops = [{
      op_type: 'HBA_RUN',
      params: { profile: run.profile, period: run.period, lines: run.lines.length, cashDir: run.cashDir,
                sumDr: run.journal.sumDr, sumCr: run.journal.sumCr, balanced: run.journal.balanced, watermark: run.watermark }
    }];
    run.lines.forEach(function (l) {
      ops.push({
        op_type: 'HBA_RUN_LINE',
        params: { profile: l.profile, period: l.period, line_no: l.line_no, party: l.party, amount: l.amount,
                  dr: l.drAccount, cr: l.crAccount, bound: !!l.bindGuid, watermark: l.watermark },
        inputGuids: l.bindGuid ? [l.bindGuid] : null
      });
    });
    return KO.commitGroup(db, ops, groupMeta || {});             // async → caller awaits
  }

  // ── demo seed — built over REAL guids fed by the witness (non-invent) + ONE synthetic ABSENT guid so the
  //    honest-un-linked path is exercised every run. realGuids: string[] (≥8). period semantics anchored to '2026-06'. ──
  var SYNTHETIC_ABSENT = 'HBA-SYNTHETIC-ABSENT-GUID-not-in-model';
  function demoSeed(realGuids) {
    var g = realGuids || [];
    return {
      leases: [
        { tenant: 'Tenant-A', unit_guid: g[0], rent: 2500, term_start: '2026-01', term_end: '2026-12' }, // in-term, occupied
        { tenant: 'Tenant-B', unit_guid: g[1], rent: 1800, term_start: '2025-01', term_end: '2026-06' }, // in-term, EXPIRING this month
        { tenant: 'Tenant-C', unit_guid: g[2], rent: 3200, term_start: '2026-09', term_end: '2027-09' }, // FUTURE → out-of-term
        { tenant: 'Tenant-D', unit_guid: SYNTHETIC_ABSENT, rent: 999, term_start: '2026-01', term_end: '2026-12' } // in-term but UNBOUND
      ],
      employees: [
        { party: 'Emp-1', gross: 5000, hired: '2020-01', left: null },     // active
        { party: 'Emp-2', gross: 4000, hired: '2026-07', left: null }      // hired NEXT month → not active in 2026-06
      ],
      owners: [
        { party: 'Owner-1', unit_guid: g[3], levy: 350, share: 0.25 },
        { party: 'Owner-2', unit_guid: g[4], levy: 350, share: 0.25 }
      ],
      assets: [
        { bim_guid: g[5], pm_cost: 400, pm_cycle: 'monthly', next_due: '2026-05', vendor: 'Acme-PM' },   // OVERDUE
        { bim_guid: g[6], pm_cost: 250, pm_cycle: 'monthly', next_due: '2026-06', vendor: 'Acme-PM' },   // DUE this period
        { bim_guid: g[7], pm_cost: 100, pm_cycle: 'yearly',  next_due: '2026-12', vendor: 'Acme-PM' }    // future → ok, skipped
      ]
    };
  }

  var HRBIMAsset = {
    WATERMARK: WATERMARK, ACCT: ACCT, PROFILES: PROFILES, SYNTHETIC_ABSENT: SYNTHETIC_ABSENT,
    runPeriod: runPeriod, bindUnits: bindUnits, spatialView: spatialView, commitRun: commitRun,
    demoSeed: demoSeed, inTerm: inTerm, ym2num: ym2num, round2: round2
  };
  if (typeof window !== 'undefined') window.HRBIMAsset = HRBIMAsset;
  if (typeof module !== 'undefined' && module.exports) module.exports = HRBIMAsset;
  if (typeof console !== 'undefined' && typeof window === 'undefined') { /* node: quiet load */ }
})();
