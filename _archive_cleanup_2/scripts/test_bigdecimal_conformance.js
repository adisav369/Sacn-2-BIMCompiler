// ⚠ DO NOT REMOVE — proves site/bigdecimal.js == java.math.BigDecimal (the guarantee, witnessed not asserted).
// PROVES: every golden vector from real Java BigDecimal (scripts/golden_bigdecimal.jsonl) reproduces EXACTLY
//   in our JS BigDecimal. A single mismatch = the JS class diverges from Java -> FAIL (non-invent: fix the class).
// READ THE LOG. RUN: node scripts/test_bigdecimal_conformance.js  (regen golden first if battery changed)
'use strict';
const fs = require('fs');
const path = require('path');
const BigDecimal = require('../build/erp/bigdecimal.js');  // tracked source (site/ is the gitignored serving mirror)

const file = path.join(__dirname, 'golden_bigdecimal.jsonl');
const lines = fs.readFileSync(file, 'utf8').trim().split('\n');
let pass = 0, fail = 0; const fails = [];

for (const line of lines) {
  const v = JSON.parse(line);
  let got;
  try {
    const a = BigDecimal.of(v.a);
    switch (v.op) {
      case 'add':       got = a.add(v.b).toString(); break;
      case 'subtract':  got = a.subtract(v.b).toString(); break;
      case 'multiply':  got = a.multiply(v.b).toString(); break;
      case 'divide':    got = a.divide(v.b, v.scale, v.rm).toString(); break;
      case 'setScale':  got = a.setScale(v.scale, v.rm).toString(); break;
      case 'compareTo': got = String(a.compareTo(v.b)); break;
      default: got = 'ERR:unknown op ' + v.op;
    }
  } catch (e) { got = 'ERR:' + e.message; }
  if (got === String(v.r)) pass++; else { fail++; fails.push(Object.assign({ got }, v)); }
}

console.log('§BD-CONFORM pass=' + pass + '/' + (pass + fail) +
  (fail ? '  FAIL=' + fail + ' (JS DIVERGES from java.math.BigDecimal)' : '  (JS == java.math.BigDecimal, exact)'));
fails.slice(0, 20).forEach(f => console.log('  MISMATCH op=' + f.op + ' a=' + f.a + ' b=' + (f.b||'') +
  ' scale=' + (f.scale!=null?f.scale:'') + ' rm=' + (f.rm||'') + ' want=' + f.r + ' got=' + f.got));

// spotlight the float trap this class exists to prevent (1.005 = the canonical V8 case)
const floaty = '1.005';
const bd = BigDecimal.of(floaty).setScale(2, 'HALF_UP').toString();
const raw = (Math.round(Number(floaty) * 100) / 100).toFixed(2);
console.log('§BD-FLOATTRAP "' + floaty + '".setScale(2,HALF_UP): BigDecimal=' + bd +
  '  rawJSfloat=' + raw + '  -> ' + (bd === raw ? 'same' : 'DIVERGE (raw float loses a cent; Java BigDecimal=1.01)'));

process.exit(fail ? 1 : 0);
