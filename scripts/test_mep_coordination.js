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

// 2. who-yields
var y1 = C.yields('DRAIN', 'DWATER'); chk('water yields to drain', y1.yields === 'DWATER' && y1.holds === 'DRAIN', y1.yields);
var y2 = C.yields('ACMV', 'ELEC');   chk('elec yields to duct',   y2.yields === 'ELEC',  y2.yields);
chk('priority is PENDING (not yet enforced)', y1.status === 'PENDING', y1.status);

// 3. min separation — VERIFIED rows
chk('data↔power 50mm',      C.minSeparation('DATA', 'ELEC').mm === 50, C.minSeparation('DATA','ELEC'));
chk('data↔power VERIFIED',  C.minSeparation('DATA', 'ELEC').status === 'VERIFIED');
chk('sprinkler↔struct 50',  C.minSeparation('FP', 'STRUCT').mm === 50);
chk('elec↔water 25 PENDING',C.minSeparation('ELEC','DWATER').status === 'PENDING', C.minSeparation('ELEC','DWATER'));

// 4. duct depress move is the cited VERIFIED resolution
var mv = C.resolveMove('ACMV'); chk('duct depress ≤30% VERIFIED', mv.move === 'depress' && mv.maxFraction === 0.30 && mv.status === 'VERIFIED', mv);
chk('non-duct reroutes', C.resolveMove('ELEC').move === 'reroute');

// 5. arbitrate enforce gate: data↔power is fully verified-sep but priority PENDING → NOT enforced yet
var a1 = C.arbitrate('DATA', 'ELEC');
chk('data/power not enforced (priority pending)', a1.enforce === false, { enforce: a1.enforce, sep: a1.minSepMm });
chk('arbitrate carries provenance notes', !!a1.notes.priority && !!a1.notes.separation, a1.notes);

console.log('\n  §MEP-COORD RESULT ' + (fail === 0 ? 'ALL PASS' : fail + ' FAIL'));
process.exit(fail === 0 ? 0 : 1);
