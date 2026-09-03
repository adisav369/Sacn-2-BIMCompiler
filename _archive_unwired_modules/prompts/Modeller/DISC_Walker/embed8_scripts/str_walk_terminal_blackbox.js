// str_walk_terminal_blackbox.js — BLACK-BOX STR walk test on Terminal's consolidated ARC-only substrate.
// Per user directive: never peek at any full/ground-truth extraction DURING the walk — only the ARC-only
// Terminal_ARC.db + shared mesh.db + terminal_rules.db (measured STR rule_placement rows) drive this.
'use strict';
const fs = require('fs');
const initSqlJs = require('/home/red1/bim-compiler/node_modules/sql.js');
const DW = require('/home/red1/bim-compiler/build/disc_walker.js');

const SCRATCH = '/tmp/claude-1000/-home-red1-bim-compiler/05a18b83-c49c-404d-872a-460f5c924747/scratchpad/embed8';

(async () => {
  const SQL = await initSqlJs();
  const bdb = new SQL.Database(new Uint8Array(fs.readFileSync(SCRATCH + '/Terminal_meta_FINAL.db')));
  const geoDb = new SQL.Database(new Uint8Array(fs.readFileSync(SCRATCH + '/mesh_FINAL.db')));
  const rdb = new SQL.Database(new Uint8Array(fs.readFileSync('/home/red1/bim-compiler/build/terminal_rules.db')));
  DW.dwOpen(rdb);

  console.log('=== BLACK-BOX STR WALK — Terminal ARC-only substrate (no ground-truth peeking) ===');
  const result = DW.dwWalk('STR', bdb, 'Terminal', { geoDb: geoDb });

  if (result.refused) {
    console.log('§STR-WALK REFUSED:', result.reason);
    return;
  }

  console.log('§STR-WALK placed=' + result.placed);
  const byClass = {};
  (result.placements || []).forEach(p => { byClass[p.ifc_class] = (byClass[p.ifc_class] || 0) + 1; });
  console.log('§STR-WALK byClass=' + JSON.stringify(byClass));

  const byStorey = {};
  (result.placements || []).forEach(p => { byStorey[p.storey] = (byStorey[p.storey] || 0) + 1; });
  console.log('§STR-WALK byStorey=' + JSON.stringify(byStorey));

  // Real ARC envelope from the substrate itself (black-box — no other source).
  const arcRows = bdb.exec("SELECT t.center_x, t.center_y FROM element_transforms t")[0];
  let xmin=Infinity,xmax=-Infinity,ymin=Infinity,ymax=-Infinity;
  arcRows.values.forEach(([x,y]) => { if(x<xmin)xmin=x; if(x>xmax)xmax=x; if(y<ymin)ymin=y; if(y>ymax)ymax=y; });
  const margin = 0.5;
  let outside = 0;
  (result.placements || []).forEach(p => {
    if (p.x < xmin-margin || p.x > xmax+margin || p.y < ymin-margin || p.y > ymax+margin) outside++;
  });
  console.log('§STR-WALK envelope x=[' + xmin.toFixed(2) + ',' + xmax.toFixed(2) + '] y=[' + ymin.toFixed(2) + ',' + ymax.toFixed(2) + ']');
  console.log('§STR-WALK outside_envelope=' + outside + '/' + result.placed);

  // Sample a few real placements
  (result.placements || []).slice(0, 5).forEach(p => {
    console.log('  sample: ' + p.ifc_class + ' storey=' + p.storey + ' x=' + p.x.toFixed(2) + ' y=' + p.y.toFixed(2) + ' z=' + p.z.toFixed(2) + ' prov=' + p.prov);
  });

  bdb.close(); geoDb.close(); rdb.close();
})().catch(e => { console.error('FATAL', e); process.exit(1); });
