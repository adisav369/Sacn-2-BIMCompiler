/**
 * W-BOM-GRAPH — the Modeller's bom-graph tab (DISC/ARC): ARC drops as a branching containment tree
 * Building → Storey → Room → Discipline → Class → element, DERIVED from the opened meta.db.
 *
 * Runs the SHIPPED engine (bom_tree.js seedFromDb) on each REAL resident meta.db and proves:
 *   C1 LOSSLESS — every element is exactly one leaf (leaf count == elements_meta count, no drop/dup)
 *   C2 HIERARCHY — single Building root; every storey node is a real elements_meta.storey value
 *   C3 ROOMS-REAL — buildings WITH IfcSpace (Duplex/Terminal): room nodes == IfcSpace names that contain
 *      elements; total elements under rooms == the rel_contained_in_space⋈IfcSpace mapping (non-invent)
 *   C4 ROOMS-ABSENT — buildings with NO IfcSpace (SampleHouse/SC): zero room nodes, tree still lossless
 *   C5 NON-INVENT — every class node is a real ifc_class; every room label is a real IfcSpace name
 *
 * Pass the viewer dir as argv[2] (default ~/bim-ootb/viewer). Reads the meta.db from deploy/buildings +
 * the live Terminal_meta. NETWORK-free.
 */
const path = require('path'), fs = require('fs');
const ROOT = path.resolve(__dirname, '..');
const VDIR = [process.argv[2], path.join(process.env.HOME || '', 'bim-ootb/viewer')].filter(Boolean).find(d => d && fs.existsSync(path.join(d, 'bom_tree.js')));
if (!VDIR) { console.error('no viewer dir with bom_tree.js (pass argv[2])'); process.exit(2); }
global.window = {};
const T = require(path.join(VDIR, 'bom_tree.js'));
const initSqlJs = require(path.join(ROOT, 'node_modules/sql.js'));

const RES = [
  { key: 'SampleHouse',    db: path.join(ROOT, 'deploy/buildings/SampleHouse_meta.db'),    rooms: false },
  { key: 'Duplex',         db: path.join(ROOT, 'deploy/buildings/Duplex_meta.db'),         rooms: true },
  { key: 'Schependomlaan', db: path.join(ROOT, 'deploy/buildings/Schependomlaan_meta.db'), rooms: false },
  { key: 'Terminal',       db: path.join(process.env.HOME || '', 'bim-ootb/viewer/buildings/Terminal_meta.db'), rooms: true }
].filter(r => fs.existsSync(r.db));

let PASS = 0, FAIL = 0;
const ok = (c, m) => { (c ? PASS++ : FAIL++); console.log('  ' + (c ? '✅' : '❌') + ' ' + m); return c; };
const one = (db, sql) => { const r = db.exec(sql); return r.length ? r[0].values[0][0] : 0; };
const col = (db, sql) => { const r = db.exec(sql); return r.length ? r[0].values.map(v => v[0]) : []; };

(async () => {
  const SQL = await initSqlJs();
  for (const res of RES) {
    const db = new SQL.Database(new Uint8Array(fs.readFileSync(res.db)));
    const tree = T.seedFromDb(db, { building: res.key });
    const kinds = {};
    Object.keys(tree.nodes).forEach(k => { const kd = tree.nodes[k].kind; kinds[kd] = (kinds[kd] || 0) + 1; });
    const nElems = one(db, "SELECT COUNT(*) FROM elements_meta");
    const leafN = T.leaves(tree).length;
    console.log('  · ' + res.key + ': storeys=' + (kinds.storey || 0) + ' rooms=' + (kinds.room || 0) +
      ' disc=' + (kinds.disc || 0) + ' class=' + (kinds.class || 0) + ' elements=' + (kinds.element || 0) + '/' + nElems);

    ok(leafN === nElems && (kinds.element || 0) === nElems, res.key + ' C1 LOSSLESS — ' + leafN + ' leaves == ' + nElems + ' elements_meta (no drop/dup)');

    const storeyLabels = Object.keys(tree.nodes).filter(k => tree.nodes[k].kind === 'storey').map(k => tree.nodes[k].label);
    const realStoreys = new Set(col(db, "SELECT DISTINCT COALESCE(storey,'Unknown') FROM elements_meta"));
    ok(tree.roots.length === 1 && tree.roots[0] === 'B' && storeyLabels.every(s => realStoreys.has(s)),
      res.key + ' C2 HIERARCHY — 1 Building root, ' + storeyLabels.length + ' storeys all real');

    const hasSpace = one(db, "SELECT COUNT(*) FROM sqlite_master WHERE name='spatial_structure'") &&
      one(db, "SELECT COUNT(*) FROM spatial_structure WHERE type='IfcSpace'");
    if (res.rooms && hasSpace) {
      const mappedElems = one(db, "SELECT COUNT(DISTINCT r.element_guid) FROM rel_contained_in_space r JOIN spatial_structure s ON s.guid=r.space_guid WHERE s.type='IfcSpace' AND s.name IS NOT NULL");
      const spaceNames = new Set(col(db, "SELECT DISTINCT name FROM spatial_structure WHERE type='IfcSpace' AND name IS NOT NULL"));
      const roomLabels = Object.keys(tree.nodes).filter(k => tree.nodes[k].kind === 'room').map(k => tree.nodes[k].label);
      // count element leaves that sit under a room node
      const underRoom = T.leaves(tree).filter(g => { let p = tree.nodes[g].parent; while (p) { if (tree.nodes[p].kind === 'room') return true; p = tree.nodes[p].parent; } return false; }).length;
      ok((kinds.room || 0) > 0 && roomLabels.every(r => spaceNames.has(r)) && underRoom === mappedElems,
        res.key + ' C3 ROOMS-REAL — ' + (kinds.room || 0) + ' rooms (all real IfcSpace), ' + underRoom + ' elements under rooms == ' + mappedElems + ' mapped');
    } else {
      ok((kinds.room || 0) === 0 && leafN === nElems,
        res.key + ' C4 ROOMS-ABSENT — no IfcSpace → 0 room nodes, tree still lossless (graceful)');
    }

    const classLabels = Object.keys(tree.nodes).filter(k => tree.nodes[k].kind === 'class').map(k => tree.nodes[k].label);
    const realClasses = new Set(col(db, "SELECT DISTINCT ifc_class FROM elements_meta"));
    ok(classLabels.every(c => realClasses.has(c) || c === 'Unknown'), res.key + ' C5 NON-INVENT — all ' + classLabels.length + ' class nodes are real ifc_class');
    db.close();
  }
  console.log('─'.repeat(48));
  console.log('W-BOM-GRAPH: ' + PASS + ' PASS / ' + FAIL + ' FAIL');
  process.exit(FAIL ? 1 : 0);
})().catch(e => { console.error('THREW', e); process.exit(1); });
