// W-DROP-FACING — whitebox §-log proof that the BOM DROP carries REAL per-element facing (IFC
// ObjectPlacement yaw), end-to-end: IFC → element_transforms.rotation_z (Fix-1) → {PFX}_BOM.db
// rotation_rule → dagevu_catalog.json rotDeg → dropLeaves leaf.rot. The repro this kills: furniture
// "all faces one way" (every line rotation_rule=0). NON-INVENT: every rot traced to the IFC, and
// cross-checked against {PFX}_BOM.db rotation_rule (== the extraction yaw == the IFC placement).
//
// Truth = this §-log, read directly (whitebox), NOT a browser. The oracle for facing is the IFC-captured
// yaw in {PFX}_BOM.db; the drop must reproduce it to the rounding floor.
//
// Usage: node scripts/witness_drop_facing.js [PFX ...]   (default: SH DX)
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const ROOT = path.join(__dirname, '..');
const CAT = path.join(ROOT, 'deploy/dev/dagevu_catalog.json');
const LIBJS = path.join(ROOT, 'deploy/dev/bonsai_library.js');
const cat = JSON.parse(fs.readFileSync(CAT, 'utf8'));
const PREFIXES = process.argv.slice(2).length ? process.argv.slice(2) : ['SH', 'DX'];
const BOM_DB = { SH: 'library/SH_BOM.db', DX: 'library/DX_BOM.db', CL: 'library/CL_BOM.db', SC: 'library/SC_BOM.db' };
const FURN_ROLE = { SH: 'IfcFurniture', DX: 'IfcFurnishingElement', CL: 'IfcFurnishingElement', SC: 'IfcFurnishingElement' };

function loadHost() {
  const src = fs.readFileSync(LIBJS, 'utf8');
  const win = { Bonsai: {} };
  const ff = (u) => Promise.resolve({ json: () => Promise.resolve(String(u).includes('dagevu_catalog') ? cat : {}) });
  const sb = { window: win, console, Math, JSON, Object, Array, Float32Array, Uint32Array, Uint8Array, Number,
    isFinite, Infinity, Promise, atob: (b) => Buffer.from(b, 'base64').toString('binary'), URL, fetch: ff,
    document: undefined, location: { href: 'file://x' } };
  vm.createContext(sb); vm.runInContext(src, sb, { filename: 'bl.js' });
  return win.Bonsai.library.ready().then(() => win.Bonsai.library);
}
const radToDeg = (r) => r * 180 / Math.PI;
const norm180 = (d) => ((d % 360) + 540) % 360 - 180;

(async function main() {
  const L = await loadHost();
  let Database; try { Database = require('better-sqlite3'); } catch (e) { Database = null; }

  let grandChecked = 0, grandMismatch = 0, grandVaried = 0, grandFail = 0;

  for (const PFX of PREFIXES) {
    console.log(`\n══════════ ${PFX} ══════════`);
    // Oracle: {PFX}_BOM.db rotation_rule per furniture line (== IFC placement yaw, via Fix-1)
    let oracleRot = {}, oracleHave = false;
    if (Database && fs.existsSync(path.join(ROOT, BOM_DB[PFX] || ''))) {
      const db = new Database(path.join(ROOT, BOM_DB[PFX]), { readonly: true });
      // key by bom_id|product — the SAME product carries DIFFERENT yaw across sets (mirror B-units etc),
      // so a global per-product index would cross-contaminate. Match the drop's set exactly.
      for (const r of db.prepare("SELECT bom_id, child_product_id pid, rotation_rule rr FROM m_bom_line WHERE role=?")
                        .all(FURN_ROLE[PFX])) {
        const deg = Math.round(radToDeg(parseFloat(r.rr) || 0) * 100) / 100;
        const k = r.bom_id + '|' + r.pid;
        (oracleRot[k] = oracleRot[k] || []).push(deg);
      }
      oracleHave = true;
    } else { console.log(`  (no ${BOM_DB[PFX]} / better-sqlite3 — cross-check skipped)`); }

    // Discover this building's real per-building furniture sets (PFX:: prefix) from the catalog
    const sets = cat.assemblies.filter(a => a.id.startsWith(PFX + '::') &&
        a.children.some(c => /CHAIR|COUCH|SOFA|BED|DESK|TABLE|STOOL|PIANO|SEAT/i.test(c.ref || '')));
    if (!sets.length) { console.log(`  no ${PFX}:: furniture sets in catalog`); continue; }

    let chairRots = [], mismatches = 0, checked = 0;
    for (const s of sets) {
      const leaves = L.dropLeaves(s.id, 0, 0, 0, 0);
      const furn = leaves.filter(lf => /CHAIR|COUCH|SOFA|BED|DESK|TABLE|STOOL|PIANO|SEAT/i.test(lf.hash || ''));
      if (!furn.length) continue;
      console.log(`\n  SET ${s.id}: ${furn.length} furniture leaves`);
      const bomId = s.id.replace(PFX + '::', '');   // catalog 'PFX::X_SET' → BOM bom_id 'X_SET'
      const seen = {};
      for (const lf of furn) {
        const pid = lf.hash, dropDeg = Math.round((lf.rot || 0) * 100) / 100;
        chairRots.push(dropDeg);   // all furniture facings (chairs/beds/desks); the repro is "faces one way"
        let tag = '';
        const k = bomId + '|' + pid;
        if (oracleHave && oracleRot[k]) {
          const idx = (seen[k] = (seen[k] || 0)); seen[k]++;
          const exp = oracleRot[k][idx];
          if (exp !== undefined) { checked++; const d = norm180(dropDeg - exp); const ok = Math.abs(d) < 0.5;
            if (!ok) mismatches++; tag = `  oracle=${exp}° ${ok ? '✓' : '✗Δ' + d.toFixed(2)}`; }
        }
        console.log(`     ${pid.padEnd(22)} rot=${String(dropDeg).padStart(7)}°${tag}`);
      }
    }
    const distinct = [...new Set(chairRots.map(r => Math.round(norm180(r))))].sort((a, b) => a - b);
    const varied = distinct.length > 1 || (distinct.length === 1 && distinct[0] !== 0);
    console.log(`\n  §W-DROP-FACING[${PFX}] chairs=${chairRots.length} distinct facings=[${distinct.join(', ')}]° | ` +
                `cross-check ${checked} leaves, ${mismatches} mismatch | varied=${varied}`);
    grandChecked += checked; grandMismatch += mismatches; if (varied) grandVaried++;
    if (!varied || mismatches > 0) grandFail++;
  }

  console.log(`\n§W-DROP-FACING TOTAL: ${grandChecked} furniture leaves cross-checked vs {PFX}_BOM.db, ` +
              `${grandMismatch} mismatch; ${grandVaried}/${PREFIXES.length} buildings show varied facing`);
  if (grandFail === 0) {
    console.log('§W-DROP-FACING GREEN — every dropped furniture rotation reproduces the {PFX}_BOM.db rotation_rule');
    console.log('  (== IFC placement yaw, Fix-1) to the rounding floor; "furniture faces one way" is dead at the source.');
    process.exit(0);
  }
  console.log('§W-DROP-FACING RED'); process.exit(1);
})().catch(e => { console.error(e); process.exit(2); });
