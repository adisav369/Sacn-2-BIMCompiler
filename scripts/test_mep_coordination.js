// W-MEP-COORD — whitebox §-log proof of CoordinationHandler rule lookups (docs/MEP_COORDINATION_RULESET.md).
// Proves: priority ordering, who-yields, min-separation, the duct-depress move, and the VERIFIED/PENDING
// enforce gate. No building/canvas needed — pure rule logic.
'use strict';
var C = require('../deploy/dev/mep_coordination.js');
var fail = 0;
function chk(name, cond, got) { console.log('  ' + (cond ? 'PASS' : 'FAIL') + ' §MEP-COORD ' + name + (got !== undefined ? ' = ' + JSON.stringify(got) : '')); if (!cond) fail++; }

// 1. priority: gravity drainage holds over pressurised water and over flexible cable tray
chk('drain<water', C.priorityRank('DRAIN') < C.priorityRank('DWATER'));
chk('duct<elec',   C.priorityRank('ACMV')  < C.priorityRank('ELEC'));
chk('struct-first', C.priorityRank('STRUCT') === 0);

// 2. who-yields — scoped verification (run wsog87r4b): DRAIN-holds + STRUCT-holds are VERIFIED;
//    the largest/rigid ladder among ACMV/FP/DWATER/ELEC stays PENDING (specific sequence REFUTED).
var y1 = C.yields('DRAIN', 'DWATER'); chk('water yields to drain', y1.yields === 'DWATER' && y1.holds === 'DRAIN', y1.yields);
var y2 = C.yields('ACMV', 'ELEC');   chk('elec yields to duct',   y2.yields === 'ELEC',  y2.yields);
chk('DRAIN-holds VERIFIED (gravity fixed-fall)', y1.status === 'VERIFIED', y1.status);
chk('STRUCT-holds VERIFIED (immovable)', C.yields('STRUCT', 'FP').status === 'VERIFIED', C.yields('STRUCT','FP').status);
chk('ACMV/ELEC ladder PENDING (sequence refuted)', y2.status === 'PENDING', y2.status);

// 3. min separation — VERIFIED rows (AS/NZS 3000 seps promoted from PENDING; ceiling-void hot 300mm stays advisory)
chk('data↔power 50mm',      C.minSeparation('DATA', 'ELEC').mm === 50, C.minSeparation('DATA','ELEC'));
chk('data↔power VERIFIED',  C.minSeparation('DATA', 'ELEC').status === 'VERIFIED');
chk('sprinkler↔struct 50',  C.minSeparation('FP', 'STRUCT').mm === 50);
chk('elec↔water 25 VERIFIED', C.minSeparation('ELEC','DWATER').status === 'VERIFIED', C.minSeparation('ELEC','DWATER'));
chk('elec↔gas 25 VERIFIED',   C.minSeparation('ELEC','GAS').status === 'VERIFIED', C.minSeparation('ELEC','GAS'));
chk('duct↔tray 300 advisory', C.minSeparation('ACMV','ELEC').status === 'PENDING', C.minSeparation('ACMV','ELEC'));

// 4. duct depress move is the cited VERIFIED resolution
var mv = C.resolveMove('ACMV'); chk('duct depress ≤30% VERIFIED', mv.move === 'depress' && mv.maxFraction === 0.30 && mv.status === 'VERIFIED', mv);
chk('non-duct reroutes', C.resolveMove('ELEC').move === 'reroute');

// 5. arbitrate enforce gate.
//   FP↔STRUCT: STRUCT-holds VERIFIED + 50mm VERIFIED → ENFORCED (the marquee real-clearance case).
var aFP = C.arbitrate('FP', 'STRUCT');
chk('sprinkler/structure ENFORCED (both verified)', aFP.enforce === true && aFP.holds === 'STRUCT' && aFP.minSepMm === 50, { enforce: aFP.enforce, holds: aFP.holds, sep: aFP.minSepMm });
//   data↔power: sep VERIFIED but who-yields PENDING (both flexible, ladder unverified) → NOT enforced.
var a1 = C.arbitrate('DATA', 'ELEC');
chk('data/power not enforced (who-yields pending)', a1.enforce === false, { enforce: a1.enforce, sep: a1.minSepMm });
//   ACMV↔ELEC: BOTH pending → advisory.
chk('duct/tray advisory (both pending)', C.arbitrate('ACMV', 'ELEC').enforce === false);
chk('arbitrate carries provenance notes', !!a1.notes.priority && !!a1.notes.separation, a1.notes);

console.log('\n  §MEP-COORD RESULT ' + (fail === 0 ? 'ALL PASS' : fail + ' FAIL'));
process.exit(fail === 0 ? 0 : 1);
