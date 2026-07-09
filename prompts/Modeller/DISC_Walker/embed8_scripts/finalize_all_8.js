'use strict';
const fs = require('fs');
const initSqlJs = require('/home/red1/bim-compiler/node_modules/sql.js');

function eulerXYZ_toQuat(ex,ey,ez){const c1=Math.cos(ex/2),c2=Math.cos(ey/2),c3=Math.cos(ez/2),s1=Math.sin(ex/2),s2=Math.sin(ey/2),s3=Math.sin(ez/2);
  return {x:s1*c2*c3+c1*s2*s3,y:c1*s2*c3-s1*c2*s3,z:c1*c2*s3+s1*s2*c3,w:c1*c2*c3-s1*s2*s3};}
function applyQuat(v,q){const{x:vx,y:vy,z:vz}=v,{x:qx,y:qy,z:qz,w:qw}=q;
  const h=2*(qy*vz-qz*vy),l=2*(qz*vx-qx*vz),c=2*(qx*vy-qy*vx);
  return {x:vx+qw*h+qy*c-qz*l,y:vy+qw*l+qz*h-qx*c,z:vz+qw*c+qx*l-qy*h};}
function multiplyQuat(a,b){return {x:a.x*b.w+a.w*b.x+a.y*b.z-a.z*b.y, y:a.y*b.w+a.w*b.y+a.z*b.x-a.x*b.z,
  z:a.z*b.w+a.w*b.z+a.x*b.y-a.y*b.x, w:a.w*b.w-a.x*b.x-a.y*b.y-a.z*b.z};}
function quatToMat(q){const{x,y,z,w}=q,x2=x+x,y2=y+y,z2=z+z,xx=x*x2,xy=x*y2,xz=x*z2,yy=y*y2,yz=y*z2,zz=z*z2,wx=w*x2,wy=w*y2,wz=w*z2;
  return {m0:1-(yy+zz),m1:xy+wz,m2:xz-wy,m4:xy-wz,m5:1-(xx+zz),m6:yz+wx,m8:xz+wy,m9:yz-wx,m10:1-(xx+yy)};}
function matToEulerXYZ(m){const a=m.m8,l=m.m9,d=m.m10,n=m.m4,r=m.m0,u=m.m6,h=m.m5;
  const clamp=(v,lo,hi)=>Math.max(lo,Math.min(hi,v)); const ey=Math.asin(clamp(a,-1,1)); let ex,ez;
  if(Math.abs(a)<0.9999999){ex=Math.atan2(-l,d);ez=Math.atan2(-n,r);} else {ex=Math.atan2(u,h);ez=0;}
  return {ex,ey,ez};}
function toF32(b){return new Float32Array(b.buffer,b.byteOffset,b.byteLength/4);}
function sizeOf(p){let xn=Infinity,xm=-Infinity,yn=Infinity,ym=-Infinity,zn=Infinity,zm=-Infinity;
  for(let i=0;i<p.length;i+=3){const x=p[i],y=p[i+1],z=p[i+2];if(x<xn)xn=x;if(x>xm)xm=x;if(y<yn)yn=y;if(y>ym)ym=y;if(z<zn)zn=z;if(z>zm)zm=z;}
  return [xm-xn,ym-yn,zm-zn];}
const CANDS=[['identity',eulerXYZ_toQuat(0,0,0)],
  ['Y+90',eulerXYZ_toQuat(0,Math.PI/2,0)],['Y180',eulerXYZ_toQuat(0,Math.PI,0)],['Y-90',eulerXYZ_toQuat(0,-Math.PI/2,0)],
  ['X+90',eulerXYZ_toQuat(Math.PI/2,0,0)],['X180',eulerXYZ_toQuat(Math.PI,0,0)],['X-90',eulerXYZ_toQuat(-Math.PI/2,0,0)],
  ['Z+90',eulerXYZ_toQuat(0,0,Math.PI/2)],['Z180',eulerXYZ_toQuat(0,0,Math.PI)],['Z-90',eulerXYZ_toQuat(0,0,-Math.PI/2)]];

function unionFind() {
  const parent = new Map();
  const find = x => { if(!parent.has(x))parent.set(x,x); while(parent.get(x)!==x){parent.set(x,parent.get(parent.get(x)));x=parent.get(x);} return x; };
  const union = (a,b) => { const ra=find(a),rb=find(b); if(ra!==rb) parent.set(ra,rb); };
  return { parent, find, union };
}

// consolidate ONE building's in-memory sql.js db: true-dup collapse + rotation collapse + orphan removal.
// Mutates `db` in place. Returns stats.
function consolidateBuilding(db, label) {
  const rows = db.exec("SELECT geometry_hash, vertices, faces, length(vertices) vl, length(faces) fl FROM component_geometries")[0];
  if (!rows) return { label, before: 0, after: 0 };
  const byHash = new Map();
  rows.values.forEach(([hash,v,f,vl,fl]) => byHash.set(hash, {hash, size:sizeOf(toF32(v)), vBlob:v, fBlob:f, vl, fl}));
  const beforeCount = byHash.size;

  // group by (vlen,flen)
  const groups = new Map();
  for (const row of byHash.values()) { const key=row.vl+':'+row.fl; if(!groups.has(key))groups.set(key,[]); groups.get(key).push(row); }
  const multi = [...groups.values()].filter(g=>g.length>1);

  const TOL = 0.001;
  const { union, find, parent } = unionFind();
  const rotationEdge = new Map(); // "a|b" -> quat used (a->b)
  for (const members of multi) {
    for (let i=0;i<members.length;i++) for (let j=i+1;j<members.length;j++) {
      const a=members[i], b=members[j];
      const rawSame = Math.abs(a.size[0]-b.size[0])<=TOL && Math.abs(a.size[1]-b.size[1])<=TOL && Math.abs(a.size[2]-b.size[2])<=TOL;
      const vA=toF32(a.vBlob), vB=toF32(b.vBlob);
      if (vA.length !== vB.length) continue;
      if (rawSame) {
        // true dup check: identity rotation match (apply_mesh_dedup.js's own method, reproduced)
        let sumSq=0; for(let k=0;k<vA.length;k+=3){const dx=vA[k]-vB[k],dy=vA[k+1]-vB[k+1],dz=vA[k+2]-vB[k+2];sumSq+=dx*dx+dy*dy+dz*dz;}
        if (Math.sqrt(sumSq/(vA.length/3)) < 0.001) union(a.hash,b.hash);
        continue;
      }
      const sa=[...a.size].sort((x,y)=>x-y), sb=[...b.size].sort((x,y)=>x-y);
      if (!(Math.abs(sa[0]-sb[0])<=TOL && Math.abs(sa[1]-sb[1])<=TOL && Math.abs(sa[2]-sb[2])<=TOL)) continue;
      let bestRms=Infinity;
      for (const [,q] of CANDS) {
        let sumSq=0;
        for (let k=0;k<vA.length;k+=3){const p=applyQuat({x:vA[k],y:vA[k+1],z:vA[k+2]},q);const dx=p.x-vB[k],dy=p.y-vB[k+1],dz=p.z-vB[k+2];sumSq+=dx*dx+dy*dy+dz*dz;}
        const rms=Math.sqrt(sumSq/(vA.length/3)); if(rms<bestRms) bestRms=rms;
      }
      if (bestRms < 0.001) union(a.hash, b.hash);
    }
  }

  const compMembers = new Map();
  for (const hash of parent.keys()) { const root=find(hash); if(!compMembers.has(root))compMembers.set(root,[]); compMembers.get(root).push(hash); }
  const realGroups = [...compMembers.values()].filter(g=>g.length>1);

  let repointed = 0, deleted = 0;
  for (const members of realGroups) {
    const sorted = [...members].sort();
    const canonical = sorted[0];
    const canonV = toF32(byHash.get(canonical).vBlob);
    for (let i = 1; i < sorted.length; i++) {
      const h = sorted[i];
      const memberV = toF32(byHash.get(h).vBlob);
      if (memberV.length !== canonV.length) continue;
      let best = null;
      for (const [name, q] of CANDS) {
        let sumSq = 0;
        for (let k = 0; k < canonV.length; k += 3) {
          const p = applyQuat({x:canonV[k],y:canonV[k+1],z:canonV[k+2]}, q);
          const dx=p.x-memberV[k],dy=p.y-memberV[k+1],dz=p.z-memberV[k+2];
          sumSq += dx*dx+dy*dy+dz*dz;
        }
        const rms = Math.sqrt(sumSq/(canonV.length/3));
        if (!best || rms < best.rms) best = { name, q, rms };
      }
      if (best.rms >= 0.001) continue; // direct-verify gate — conservative, same as Terminal

      const instRows = db.exec('SELECT ei.guid, et.rotation_x, et.rotation_y, et.rotation_z FROM element_instances ei JOIN element_transforms et ON ei.guid=et.guid WHERE ei.geometry_hash=?', [h]);
      if (instRows.length) {
        for (const [guid, rx, ry, rz] of instRows[0].values) {
          const oldQ = eulerXYZ_toQuat(rx||0, rz||0, -(ry||0));
          const newQ = multiplyQuat(oldQ, best.q);
          const m = quatToMat(newQ);
          const { ex, ey, ez } = matToEulerXYZ(m);
          db.run('UPDATE element_instances SET geometry_hash=? WHERE guid=?', [canonical, guid]);
          db.run('UPDATE element_transforms SET rotation_x=?, rotation_y=?, rotation_z=? WHERE guid=?', [ex, -ez, ey, guid]);
          repointed++;
        }
      }
      db.run('DELETE FROM component_geometries WHERE geometry_hash=?', [h]);
      deleted++;
    }
  }

  // orphan removal (whatever's left with 0 references)
  const referenced = new Set(db.exec('SELECT DISTINCT geometry_hash FROM element_instances WHERE geometry_hash IS NOT NULL')[0].values.map(v=>v[0]));
  const remainingHashes = db.exec('SELECT geometry_hash FROM component_geometries')[0].values.map(v=>v[0]);
  let orphansDeleted = 0;
  for (const h of remainingHashes) { if (!referenced.has(h)) { db.run('DELETE FROM component_geometries WHERE geometry_hash=?', [h]); orphansDeleted++; } }

  db.run('VACUUM');
  const afterCount = db.exec('SELECT COUNT(*) FROM component_geometries')[0].values[0][0];
  console.log('§FINALIZE ' + label + ' before_rows=' + beforeCount + ' rot_repointed_elements=' + repointed +
    ' rot_deleted_rows=' + deleted + ' orphans_deleted=' + orphansDeleted + ' after_rows=' + afterCount);
  return { label, before: beforeCount, after: afterCount, repointed, deleted, orphansDeleted };
}

const SCRATCH = '/tmp/claude-1000/-home-red1-bim-compiler/05a18b83-c49c-404d-872a-460f5c924747/scratchpad/embed8';
const HOME = '/home/red1/bim-ootb/modeller';

(async () => {
  const SQL = await initSqlJs();
  const buildings = [
    { name: 'SampleHouse', src: SCRATCH + '/SampleHouse_all.db' },
    { name: 'Duplex',      src: SCRATCH + '/Duplex_all.db' },
    { name: 'HHS',         src: SCRATCH + '/HHS_all.db' },
    { name: 'Clinic',      src: SCRATCH + '/Clinic_all.db' },
    { name: 'Garage',      src: SCRATCH + '/Garage_all.db' },
    { name: 'Hospital',    src: SCRATCH + '/Hospital_all.db' },
    { name: 'SampleCastle',src: HOME + '/SampleCastle_ARC_extracted.db' },
  ];

  const results = [];
  const metaDbs = {}, geoRowsByBuilding = {};

  for (const b of buildings) {
    const buf = fs.readFileSync(b.src);
    const db = new SQL.Database(new Uint8Array(buf));
    const stat = consolidateBuilding(db, b.name);
    results.push(stat);

    // split into meta (no component_geometries) + collect geo rows for the shared mesh.db
    const metaDb = new SQL.Database();
    const tables = db.exec("SELECT name, sql FROM sqlite_master WHERE type='table' AND name != 'component_geometries' AND name != 'sqlite_sequence'")[0];
    tables.values.forEach(([name, sql]) => {
      metaDb.run(sql);
      const rowsR = db.exec('SELECT * FROM ' + name);
      if (rowsR.length && rowsR[0].values.length) {
        const cols = rowsR[0].columns, ph = cols.map(()=>'?').join(',');
        const stmt = metaDb.prepare('INSERT INTO ' + name + ' VALUES (' + ph + ')');
        rowsR[0].values.forEach(v => stmt.run(v)); stmt.free();
      }
    });
    metaDbs[b.name] = metaDb;

    const geoR = db.exec('SELECT * FROM component_geometries');
    geoRowsByBuilding[b.name] = geoR.length ? geoR[0] : { columns: ['geometry_hash','vertices','faces','building'], values: [] };
    db.close();
  }

  // Terminal is already finalized (rotation + orphan applied) — load its FINAL files directly.
  const termMeta = new SQL.Database(new Uint8Array(fs.readFileSync(SCRATCH + '/Terminal_meta_FINAL.db')));
  const termGeoDb = new SQL.Database(new Uint8Array(fs.readFileSync(SCRATCH + '/Terminal_geo_FINAL.db')));
  metaDbs['Terminal'] = termMeta;
  const termGeoR = termGeoDb.exec('SELECT * FROM component_geometries')[0];
  geoRowsByBuilding['Terminal'] = termGeoR;
  results.push({ label: 'Terminal', after: termGeoR.values.length, note: '(already finalized: rotation+orphan applied earlier)' });

  // ── Build the ONE shared mesh.db, deduping identical hashes ACROSS buildings too ──
  const sharedMesh = new SQL.Database();
  sharedMesh.run('CREATE TABLE component_geometries (geometry_hash TEXT PRIMARY KEY, vertices BLOB, faces BLOB, building TEXT)');
  let totalInsertedAttempts = 0, crossBuildingDupsSkipped = 0;
  const seen = new Set();
  for (const [name, geoR] of Object.entries(geoRowsByBuilding)) {
    const stmt = sharedMesh.prepare('INSERT OR IGNORE INTO component_geometries VALUES (?,?,?,?)');
    for (const row of geoR.values) {
      totalInsertedAttempts++;
      const hash = row[0];
      if (seen.has(hash)) { crossBuildingDupsSkipped++; continue; }
      seen.add(hash);
      stmt.run(row);
    }
    stmt.free();
  }
  sharedMesh.run('VACUUM');
  const meshRows = sharedMesh.exec('SELECT COUNT(*) FROM component_geometries')[0].values[0][0];
  console.log('§MESHDB total_attempts=' + totalInsertedAttempts + ' cross_building_dups_skipped=' + crossBuildingDupsSkipped + ' final_rows=' + meshRows);

  fs.writeFileSync(SCRATCH + '/mesh_FINAL.db', Buffer.from(sharedMesh.export()));
  const meshSize = fs.statSync(SCRATCH + '/mesh_FINAL.db').size;

  // write each building's final meta db
  let totalMetaSize = 0;
  const metaSizes = {};
  for (const [name, mdb] of Object.entries(metaDbs)) {
    const outPath = SCRATCH + '/' + name + '_ARC_FINAL.db';
    fs.writeFileSync(outPath, Buffer.from(mdb.export()));
    const sz = fs.statSync(outPath).size;
    metaSizes[name] = sz;
    totalMetaSize += sz;
    mdb.close();
  }

  console.log('\n=== FINAL SIZE TABLE ===');
  for (const [name, sz] of Object.entries(metaSizes)) {
    console.log('  ' + name.padEnd(14) + (sz/1024/1024).toFixed(2) + 'MB');
  }
  console.log('  ' + 'mesh.db (shared)'.padEnd(14) + (meshSize/1024/1024).toFixed(2) + 'MB');
  console.log('  ' + '-'.repeat(30));
  const grandTotal = totalMetaSize + meshSize;
  console.log('  TOTAL EMBEDDED SIZE: ' + (grandTotal/1024/1024).toFixed(2) + 'MB');

  sharedMesh.close(); termGeoDb.close();
})().catch(e => { console.error('FATAL', e); process.exit(1); });
