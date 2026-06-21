// mep_coordination.js — CoordinationHandler: the rules-driven arbiter for MEP route deconfliction.
//
// The NEW RouteWalker sub-handler (see docs/MEP_COORDINATION_RULESET.md). When RouteHandler proposes a
// path and SpaceHandler reports a clash — vs a wall OR vs another discipline's route — this decides
// WHO HOLDS, WHO YIELDS, by HOW MUCH (min separation), and the resolution MOVE.
//
// NON-INVENT: every rule carries {status, source}. status ∈ VERIFIED | PENDING | REFUTED.
//   - VERIFIED (3-0 deep-research vote, cited): ENFORCED.
//   - PENDING  (gathered from a real source but verification abstained on session limit, reset 4:20pm):
//              ADVISORY only — logged as a warning, NOT enforced, until re-verified.
//   Source rows mirror docs/MEP_COORDINATION_RULESET.md exactly. Do not add a rule without a source.
'use strict';
(function (root) {

  // Disciplines (our codes). DRAIN = gravity soil/waste/storm; DWATER = pressurised cold/hot water;
  // FP = fire sprinkler; ACMV = HVAC duct + chilled water; ELEC = cable tray/conduit/busbar;
  // DATA = ELV/comms; GAS = fuel gas / LPG; STRUCT = ARC/STR member (wall/slab/beam).
  var DISC = ['DRAIN', 'ACMV', 'FP', 'DWATER', 'GAS', 'ELEC', 'DATA', 'STRUCT'];

  // §1/§2 ROUTING PRIORITY — lower rank is laid first and HOLDS its path; higher rank YIELDS.
  // 🟡 PENDING (gravity-before-pressure, largest-before-smallest, rigid-before-flexible). projul / ccceng.
  var PRIORITY = {
    STRUCT: 0,            // fixed — never moves
    DRAIN:  1,            // gravity, fixed fall — holds
    ACMV:   2,            // largest service (duct)
    FP:     3,            // sprinkler mains
    DWATER: 3,            // pressurised pipe
    GAS:    3,
    ELEC:   4,            // cable tray / conduit — flexible, yields
    DATA:   4
  };
  var PRIORITY_STATUS = 'PENDING';
  var PRIORITY_SRC = 'projul MEP coord guide; ccceng ceiling-void memo (docs/MEP_COORDINATION_RULESET.md §1/§2)';

  // §3 MIN SEPARATION (mm) per unordered discipline pair. Each: [mm, status, source].
  function key(a, b) { return [a, b].sort().join('|'); }
  var SEP = {};
  function sep(a, b, mm, status, src) { SEP[key(a, b)] = { mm: mm, status: status, source: src }; }
  sep('DATA', 'ELEC', 50, 'VERIFIED', 'BS 6701 / NEC 800.52 (CommScope TP-106296)');   // <600V
  sep('FP', 'STRUCT', 50, 'VERIFIED', 'NFPA 13 §18.4.9 (clearance from non-supporting structure)');
  sep('ELEC', 'DWATER', 25, 'PENDING', 'AS/NZS 3000:2018 Cl 3.9.8.4 (ccceng)');
  sep('ELEC', 'GAS',    25, 'PENDING', 'AS/NZS 3000 / engineerfix electrical-gas distance');
  sep('ACMV', 'ELEC',  300, 'PENDING', 'ccceng cable-tray vs hot/duct service memo');   // tray↔hot/duct band

  var DEFAULT_SEP = { mm: 25, status: 'PENDING', source: 'fallback default — no cited pair rule; re-verify' };

  // ── API ────────────────────────────────────────────────────────────────────
  var CoordinationHandler = {
    disciplines: DISC,

    priorityRank: function (d) { return PRIORITY[d] != null ? PRIORITY[d] : 99; },

    // who yields when a and b clash → returns { holds, yields, status, source }
    yields: function (a, b) {
      var ra = this.priorityRank(a), rb = this.priorityRank(b);
      var holds = ra <= rb ? a : b, yieldsD = ra <= rb ? b : a;
      return { holds: holds, yields: yieldsD, status: PRIORITY_STATUS, source: PRIORITY_SRC };
    },

    // required min separation (mm) between two services
    minSeparation: function (a, b) {
      return SEP[key(a, b)] || DEFAULT_SEP;
    },

    // §3 resolution MOVE for the yielding service. Duct has a cited move: depress ≤30% height (no width
    // increase) to pass under an obstruction before rerouting. ✅ VERIFIED (SMACNA Duct Design).
    resolveMove: function (yieldDisc) {
      if (yieldDisc === 'ACMV') {
        return { move: 'depress', maxFraction: 0.30, status: 'VERIFIED',
                 source: 'SMACNA Duct Design (depress ≤30% height, loss coeff 0.24–0.35)' };
      }
      return { move: 'reroute', status: PRIORITY_STATUS, source: PRIORITY_SRC };
    },

    // full arbitration for a clashing pair: who yields, by how much, how — with enforce flag.
    arbitrate: function (a, b) {
      var y = this.yields(a, b);
      var s = this.minSeparation(a, b);
      var mv = this.resolveMove(y.yields);
      // ENFORCE only when both the yield-decision AND the separation are VERIFIED; else advisory.
      var enforce = (y.status === 'VERIFIED') && (s.status === 'VERIFIED');
      return {
        holds: y.holds, yields: y.yields, minSepMm: s.mm, move: mv.move,
        enforce: enforce,
        notes: {
          priority: y.status + ' (' + y.source + ')',
          separation: s.status + ' (' + s.source + ')',
          move: mv.status + ' (' + mv.source + ')'
        }
      };
    }
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = CoordinationHandler;
  if (root) root.CoordinationHandler = CoordinationHandler;
})(typeof window !== 'undefined' ? window : null);
