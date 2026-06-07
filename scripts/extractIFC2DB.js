#!/usr/bin/env node
/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 *
 * extractIFC2DB.js — Node.js IFC extractor using web-ifc (same engine as browser Drop)
 * Replaces Python extractIFCtoDB_open.py with identical output.
 *
 * Usage:
 *   node scripts/extractIFC2DB.js --ifc path/to/file.ifc -o output.db
 *
 * Output: Single SQLite DB with all tables (same as browser Drop IFC save).
 * Viewer loads it as both extDb and libDb (same buffer, same as import_db_builder.js).
 */

const fs = require('fs');
const path = require('path');
const WebIFC = require('web-ifc');
const Database = require('better-sqlite3');

// ── Args ──
const args = process.argv.slice(2);
let ifcPath = null, outPath = null, discOverride = null;
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--ifc' && args[i+1]) ifcPath = args[++i];
  else if (args[i] === '-o' && args[i+1]) outPath = args[++i];
  else if (args[i] === '--disc' && args[i+1]) discOverride = args[++i];
}
if (!ifcPath || !outPath) {
  console.error('Usage: node scripts/extractIFC2DB.js --ifc <file.ifc> -o <output.db> [--disc HEAT]');
  process.exit(1);
}
const VALID_DISCS = ['ARC','STR','MEP','PLB','ACMV','ELEC','FP','VENT','HEAT','SAN','COOL','VOID','AIR','DUCT','HVAC','MECH','FIRE','SPR','GAS','LIFT','CONV','CIV','LAND','EXT','INT','CEIL','ROOF','SITE','DEMO'];
if (discOverride && !VALID_DISCS.includes(discOverride.toUpperCase())) {
  console.error(`Invalid --disc "${discOverride}". Valid: ${VALID_DISCS.join(', ')}`);
  process.exit(1);
}
if (discOverride) discOverride = discOverride.toUpperCase();
if (!fs.existsSync(ifcPath)) {
  console.error(`IFC file not found: ${ifcPath}`);
  process.exit(1);
}

// ── Discipline map (same as import_worker.js) ──
const DISC_MAP = {
  IfcWall:'ARC', IfcWallStandardCase:'ARC', IfcSlab:'ARC', IfcDoor:'ARC',
  IfcWindow:'ARC', IfcRoof:'ARC', IfcStair:'ARC', IfcStairFlight:'ARC',
  IfcRailing:'ARC', IfcCovering:'ARC', IfcCurtainWall:'ARC', IfcPlate:'ARC',
  IfcFurnishingElement:'ARC', IfcBuildingElementProxy:'ARC', IfcSpace:'ARC',
  IfcFurniture:'ARC', IfcSystemFurnitureElement:'ARC', IfcBuildingElementPart:'ARC',
  IfcRamp:'ARC', IfcRampFlight:'ARC', IfcTransportElement:'ARC',
  IfcBeam:'STR', IfcColumn:'STR', IfcFooting:'STR', IfcPile:'STR',
  IfcMember:'STR', IfcReinforcingBar:'STR', IfcReinforcingMesh:'STR',
  IfcTendon:'STR', IfcTendonAnchor:'STR',
  IfcCableSegment:'ELEC', IfcCableCarrierSegment:'ELEC', IfcCableCarrierFitting:'ELEC',
  IfcElectricAppliance:'ELEC', IfcLightFixture:'ELEC', IfcOutlet:'ELEC',
  IfcJunctionBox:'ELEC', IfcSwitchingDevice:'ELEC', IfcElectricDistributionBoard:'ELEC',
  IfcPipeSegment:'PLB', IfcPipeFitting:'PLB', IfcSanitaryTerminal:'PLB',
  IfcValve:'PLB', IfcWasteTerminal:'PLB', IfcStackTerminal:'PLB',
  IfcDuctSegment:'ACMV', IfcDuctFitting:'ACMV', IfcAirTerminal:'ACMV',
  IfcAirTerminalBox:'ACMV', IfcUnitaryEquipment:'ACMV', IfcCoil:'ACMV',
  IfcFan:'ACMV', IfcCompressor:'ACMV', IfcChiller:'ACMV',
  IfcFireSuppressionTerminal:'FP', IfcAlarm:'FP',
  IfcFlowSegment:'MEP', IfcFlowTerminal:'MEP', IfcFlowFitting:'MEP',
  IfcFlowController:'MEP', IfcFlowMovingDevice:'MEP', IfcFlowStorageDevice:'MEP',
  IfcFlowTreatmentDevice:'MEP', IfcEnergyConversionDevice:'MEP',
  IfcDistributionElement:'MEP', IfcDistributionFlowElement:'MEP',
  IfcDistributionControlElement:'MEP',
};

const CLASS_NAME_MAP = {};
for (const k of Object.keys(DISC_MAP)) CLASS_NAME_MAP[k.toUpperCase()] = k;
CLASS_NAME_MAP['IFCOPENINGELEMENT'] = 'IfcOpeningElement';
CLASS_NAME_MAP['IFCSITE'] = 'IfcSite';
CLASS_NAME_MAP['IFCGEOGRAPHICELEMENT'] = 'IfcGeographicElement';

function properClassName(typeCode) {
  const upper = typeCode.toUpperCase();
  return CLASS_NAME_MAP[upper] || ('Ifc' + typeCode.substring(3).charAt(0).toUpperCase() + typeCode.substring(4).toLowerCase());
}

// §FLOWTERM-REFINE: IfcFlowTerminal is the abstract supertype generic/IFC2x3 exporters emit for
// sprinklers, lights, diffusers AND sanitary fixtures — the flat "IfcFlowTerminal → MEP" rule buries
// them all in MEP (sprinklers never show under FP). Disambiguate the catch-all by element-name
// keyword to the discipline the specific subtype would carry. Deterministic; unmatched → MEP.
const FLOWTERM_NAME_DISC = [
  [['sprinkler','fire suppression','fire protection','firefight'], 'FP'],
  [['light','luminaire','lamp','downlight','spotlight'], 'ELEC'],
  [['receptacle','socket','outlet','switch','panelboard','panel board'], 'ELEC'],
  [['diffuser','grille','register','air terminal','supply air','return air','exhaust'], 'ACMV'],
  [['lavatory','water closet','urinal','sink','basin','shower','toilet','wc','sanitary','bidet','faucet','tap'], 'PLB'],
];
function classifyDisc(ifcClass, name) {
  if (ifcClass === 'IfcFlowTerminal' && name) {
    const low = String(name).toLowerCase();
    for (const [keys, disc] of FLOWTERM_NAME_DISC) if (keys.some(k => low.includes(k))) return disc;
  }
  return DISC_MAP[ifcClass] || 'ARC';
}

// ── FNV-1a hash (same as import_worker.js) ──
function fnvHash(buf) {
  let h = 0x811c9dc5;
  for (let i = 0; i < buf.length; i++) { h ^= buf[i]; h = Math.imul(h, 0x01000193); }
  let h2 = 0x6c62272e;
  for (let i = buf.length - 1; i >= 0; i--) { h2 ^= buf[i]; h2 = Math.imul(h2, 0x01000193); }
  return (h >>> 0).toString(16).padStart(8,'0') + (h2 >>> 0).toString(16).padStart(8,'0');
}

// ── Resolve material color from IFC material reference ──
function resolveMaterialColor(ifcApi, modelID, matExpressID) {
  try {
    const mat = ifcApi.GetLine(modelID, matExpressID);
    if (!mat) return null;

    // Direct IfcMaterial → check HasRepresentation
    if (mat.HasRepresentation) {
      for (let i = 0; i < mat.HasRepresentation.length; i++) {
        const c = extractColorFromRepresentation(ifcApi, modelID, mat.HasRepresentation[i].value);
        if (c) return c;
      }
    }

    // IfcMaterialLayerSetUsage → IfcMaterialLayerSet → layers
    if (mat.ForLayerSet) {
      return resolveMaterialColor(ifcApi, modelID, mat.ForLayerSet.value);
    }
    if (mat.MaterialLayers) {
      for (let i = 0; i < mat.MaterialLayers.length; i++) {
        try {
          const layer = ifcApi.GetLine(modelID, mat.MaterialLayers[i].value);
          if (layer.Material) {
            const c = resolveMaterialColor(ifcApi, modelID, layer.Material.value);
            if (c) return c;
          }
        } catch(e) {}
      }
    }

    // IfcMaterialConstituentSet → constituents
    if (mat.MaterialConstituents) {
      for (let i = 0; i < mat.MaterialConstituents.length; i++) {
        try {
          const constituent = ifcApi.GetLine(modelID, mat.MaterialConstituents[i].value);
          if (constituent.Material) {
            const c = resolveMaterialColor(ifcApi, modelID, constituent.Material.value);
            if (c) return c;
          }
        } catch(e) {}
      }
    }

    // IfcMaterialList → Materials
    if (mat.Materials) {
      for (let i = 0; i < mat.Materials.length; i++) {
        const c = resolveMaterialColor(ifcApi, modelID, mat.Materials[i].value);
        if (c) return c;
      }
    }
  } catch(e) {}
  return null;
}

function extractColorFromRepresentation(ifcApi, modelID, repExpressID) {
  try {
    const rep = ifcApi.GetLine(modelID, repExpressID);
    if (!rep || !rep.Representations) return null;
    for (let i = 0; i < rep.Representations.length; i++) {
      const styledRep = ifcApi.GetLine(modelID, rep.Representations[i].value);
      if (!styledRep || !styledRep.Items) continue;
      for (let j = 0; j < styledRep.Items.length; j++) {
        const styledItem = ifcApi.GetLine(modelID, styledRep.Items[j].value);
        if (!styledItem || !styledItem.Styles) continue;
        for (let k = 0; k < styledItem.Styles.length; k++) {
          const style = ifcApi.GetLine(modelID, styledItem.Styles[k].value);
          if (!style || !style.Styles) continue;
          for (let l = 0; l < style.Styles.length; l++) {
            const rendering = ifcApi.GetLine(modelID, style.Styles[l].value);
            if (rendering && rendering.SurfaceColour) {
              const rgb = ifcApi.GetLine(modelID, rendering.SurfaceColour.value);
              if (rgb && rgb.Red !== undefined) {
                const r = rgb.Red.value !== undefined ? rgb.Red.value : rgb.Red;
                const g = rgb.Green.value !== undefined ? rgb.Green.value : rgb.Green;
                const b = rgb.Blue.value !== undefined ? rgb.Blue.value : rgb.Blue;
                // Skip near-white (>0.95 all channels)
                if (r > 0.95 && g > 0.95 && b > 0.95) return null;
                return `${r.toFixed(3)},${g.toFixed(3)},${b.toFixed(3)},1.000`;
              }
            }
          }
        }
      }
    }
  } catch(e) {}
  return null;
}

// ── Main ──
async function main() {
  console.log(`§EXTRACT_START ifc=${ifcPath}`);
  const ifcData = new Uint8Array(fs.readFileSync(ifcPath));
  console.log(`§IFC_SIZE ${(ifcData.byteLength / 1024 / 1024).toFixed(1)}MB`);

  const ifcApi = new WebIFC.IfcAPI();
  await ifcApi.Init();
  console.log('§WASM_READY');

  const modelID = ifcApi.OpenModel(ifcData, {
    COORDINATE_TO_ORIGIN: false,
    USE_FAST_BOOLS: true,
    OPTIMIZE_PROFILES: true,
  });
  if (modelID < 0) { console.error('§PARSE_FAIL'); process.exit(1); }
  console.log(`§PARSE_OK modelID=${modelID}`);

  // ── Project name ──
  const buildingName = path.basename(ifcPath, '.ifc').replace(/\.ifc$/i, '');
  let projectName = buildingName;
  const projIds = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCPROJECT);
  if (projIds.size() > 0) {
    try {
      const proj = ifcApi.GetLine(modelID, projIds.get(0));
      if (proj.Name && proj.Name.value) projectName = proj.Name.value;
    } catch(e) {}
  }

  // ── Storeys ──
  const storeyMap = {};
  const storeyIds = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCBUILDINGSTOREY);
  for (let i = 0; i < storeyIds.size(); i++) {
    try {
      const s = ifcApi.GetLine(modelID, storeyIds.get(i));
      storeyMap[storeyIds.get(i)] = s.Name ? s.Name.value : 'Level ' + i;
    } catch(e) {}
  }

  // ── Containment (element → storey) ──
  const elementToStorey = {};
  const relIds = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCRELCONTAINEDINSPATIALSTRUCTURE);
  for (let i = 0; i < relIds.size(); i++) {
    try {
      const rel = ifcApi.GetLine(modelID, relIds.get(i));
      const storeyId = rel.RelatingStructure ? rel.RelatingStructure.value : null;
      const storeyName = storeyMap[storeyId] || 'Unknown';
      if (rel.RelatedElements) {
        for (let j = 0; j < rel.RelatedElements.length; j++) {
          elementToStorey[rel.RelatedElements[j].value] = storeyName;
        }
      }
    } catch(e) {}
  }

  // ── Collect elements ──
  const PRODUCT_TYPES = [
    WebIFC.IFCWALL, WebIFC.IFCWALLSTANDARDCASE, WebIFC.IFCSLAB, WebIFC.IFCDOOR,
    WebIFC.IFCWINDOW, WebIFC.IFCROOF, WebIFC.IFCSTAIR, WebIFC.IFCSTAIRFLIGHT,
    WebIFC.IFCRAILING, WebIFC.IFCCOVERING, WebIFC.IFCCURTAINWALL, WebIFC.IFCPLATE,
    WebIFC.IFCFURNISHINGELEMENT, WebIFC.IFCBUILDINGELEMENTPROXY,
    WebIFC.IFCFURNITURE, WebIFC.IFCSYSTEMFURNITUREELEMENT,
    WebIFC.IFCBUILDINGELEMENTPART, WebIFC.IFCRAMP, WebIFC.IFCRAMPFLIGHT,
    WebIFC.IFCTRANSPORTELEMENT,
    WebIFC.IFCBEAM, WebIFC.IFCCOLUMN, WebIFC.IFCFOOTING, WebIFC.IFCMEMBER,
    WebIFC.IFCPILE, WebIFC.IFCREINFORCINGBAR, WebIFC.IFCREINFORCINGMESH,
    WebIFC.IFCTENDON, WebIFC.IFCTENDONANCHOR,
    WebIFC.IFCFLOWSEGMENT, WebIFC.IFCFLOWTERMINAL, WebIFC.IFCFLOWFITTING,
    WebIFC.IFCFLOWCONTROLLER, WebIFC.IFCFLOWMOVINGDEVICE, WebIFC.IFCFLOWSTORAGEDEVICE,
    WebIFC.IFCFLOWTREATMENTDEVICE, WebIFC.IFCENERGYCONVERSIONDEVICE,
    WebIFC.IFCPIPESEGMENT, WebIFC.IFCPIPEFITTING,
    WebIFC.IFCDUCTSEGMENT, WebIFC.IFCDUCTFITTING,
    WebIFC.IFCCABLESEGMENT, WebIFC.IFCCABLECARRIERSEGMENT, WebIFC.IFCCABLECARRIERFITTING,
    WebIFC.IFCLIGHTFIXTURE, WebIFC.IFCOUTLET, WebIFC.IFCJUNCTIONBOX,
    WebIFC.IFCSWITCHINGDEVICE, WebIFC.IFCELECTRICDISTRIBUTIONBOARD,
    WebIFC.IFCELECTRICAPPLIANCE, WebIFC.IFCCONTROLLER,
    WebIFC.IFCSANITARYTERMINAL, WebIFC.IFCUNITARYEQUIPMENT,
    WebIFC.IFCVALVE, WebIFC.IFCWASTETERMINAL, WebIFC.IFCSTACKTERMINAL,
    WebIFC.IFCAIRTERMINAL, WebIFC.IFCAIRTERMINALBOX,
    WebIFC.IFCCOIL, WebIFC.IFCFAN, WebIFC.IFCCOMPRESSOR, WebIFC.IFCCHILLER,
    WebIFC.IFCFIRESUPPRESSIONTERMINAL, WebIFC.IFCALARM,
    WebIFC.IFCDISTRIBUTIONFLOWELEMENT, WebIFC.IFCDISTRIBUTIONCONTROLELEMENT,
    WebIFC.IFCDISTRIBUTIONELEMENT,
    WebIFC.IFCGEOGRAPHICELEMENT,
  ].filter(t => t !== undefined);

  const elements = [];
  const elementIds = new Set();
  for (const typeId of PRODUCT_TYPES) {
    const ids = ifcApi.GetLineIDsWithType(modelID, typeId);
    for (let i = 0; i < ids.size(); i++) {
      const id = ids.get(i);
      if (elementIds.has(id)) continue;
      elementIds.add(id);
      try {
        const el = ifcApi.GetLine(modelID, id);
        const typeName = ifcApi.GetNameFromTypeCode(typeId) || 'IFCBUILDINGELEMENT';
        const ifcClass = properClassName(typeName);
        elements.push({
          expressID: id,
          guid: el.GlobalId ? el.GlobalId.value : 'GUID_' + id,
          ifcClass,
          name: el.Name ? el.Name.value : ifcClass + '_' + id,
          storey: elementToStorey[id] || 'Unknown',
          discipline: discOverride || classifyDisc(ifcClass, el.Name ? el.Name.value : ''),  // §FLOWTERM-REFINE
          material: '',
          _bboxX: null, _bboxY: null, _bboxZ: null,
        });
      } catch(e) {}
    }
  }
  console.log(`§ELEMENTS count=${elements.length} storeys=${Object.keys(storeyMap).length}`);

  // ── Resolve real material colors from IFC (override white defaults) ──
  // Chain: IfcRelAssociatesMaterial → IfcMaterial(LayerSet|ConstituentSet) →
  //        IfcMaterialDefinitionRepresentation → IfcStyledItem → IfcSurfaceStyle →
  //        IfcSurfaceStyleRendering → SurfaceColour
  const elementMaterialColor = {}; // expressID → "r,g,b,a"
  try {
    const relMatIds = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCRELASSOCIATESMATERIAL);
    for (let i = 0; i < relMatIds.size(); i++) {
      try {
        const rel = ifcApi.GetLine(modelID, relMatIds.get(i));
        if (!rel.RelatedObjects || !rel.RelatingMaterial) continue;
        const matRef = rel.RelatingMaterial.value;
        const color = resolveMaterialColor(ifcApi, modelID, matRef);
        if (!color) continue;
        for (let j = 0; j < rel.RelatedObjects.length; j++) {
          elementMaterialColor[rel.RelatedObjects[j].value] = color;
        }
      } catch(e) {}
    }
  } catch(e) {}
  console.log(`§MATERIALS resolved=${Object.keys(elementMaterialColor).length} elements with IFC material color`);

  // ── Tessellate geometry (same logic as import_worker.js) ──
  const geometries = []; // { guid, geomHash, vertices: Buffer, indices: Buffer }
  const transforms = []; // { guid, cx, cy, cz, rx, ry, rz, bx, by, bz }
  let matCount = 0;

  for (let ei = 0; ei < elements.length; ei++) {
    const el = elements[ei];
    try {
      const flatMesh = ifcApi.GetFlatMesh(modelID, el.expressID);
      const allVerts = [], allIdx = [];
      let vertOffset = 0, bestColor = null;
      const geoCount = flatMesh.geometries.size();

      for (let gi = 0; gi < geoCount; gi++) {
        const geo = flatMesh.geometries.get(gi);
        const meshData = ifcApi.GetGeometry(modelID, geo.geometryExpressID);
        const vSize = meshData.GetVertexDataSize();
        const iSize = meshData.GetIndexDataSize();
        if (vSize === 0 || iSize === 0) continue;
        const verts = ifcApi.GetVertexArray(meshData.GetVertexData(), vSize);
        const idx = ifcApi.GetIndexArray(meshData.GetIndexData(), iSize);

        // Detect IfcBoundingBox (8 verts, 36 indices) — extract dims, skip from body
        if (verts.length / 6 === 8 && idx.length === 36) {
          const bxs = [], bys = [], bzs = [];
          for (let bvi = 0; bvi < 8; bvi++) {
            bxs.push(verts[bvi * 6]); bys.push(verts[bvi * 6 + 1]); bzs.push(verts[bvi * 6 + 2]);
          }
          el._bboxX = Math.max(...bxs) - Math.min(...bxs);
          el._bboxY = Math.max(...bys) - Math.min(...bys);
          el._bboxZ = Math.max(...bzs) - Math.min(...bzs);
          if (geoCount > 1) continue; // skip box, keep body
        }

        // Apply 4x4 transform, Y-up → Z-up
        const m = geo.flatTransformation;
        const vc = verts.length / 6;
        for (let vi = 0; vi < vc; vi++) {
          const lx = verts[vi*6], ly = verts[vi*6+1], lz = verts[vi*6+2];
          const wx = m[0]*lx + m[4]*ly + m[8]*lz + m[12];
          const wy = m[1]*lx + m[5]*ly + m[9]*lz + m[13];
          const wz = m[2]*lx + m[6]*ly + m[10]*lz + m[14];
          allVerts.push(wx, -wz, wy);
        }
        for (let ii = 0; ii < idx.length; ii++) allIdx.push(idx[ii] + vertOffset);
        vertOffset += vc;
        if (!bestColor && geo.color && geo.color.x !== undefined) bestColor = geo.color;
      }

      if (allVerts.length >= 9) {
        const vertCount = allVerts.length / 3;
        let sumX = 0, sumY = 0, sumZ = 0;
        for (let vi = 0; vi < vertCount; vi++) {
          sumX += allVerts[vi*3]; sumY += allVerts[vi*3+1]; sumZ += allVerts[vi*3+2];
        }
        const cx = sumX / vertCount, cy = sumY / vertCount, cz = sumZ / vertCount;

        // Re-center at origin
        const positions = new Float32Array(allVerts.length);
        for (let vi = 0; vi < vertCount; vi++) {
          positions[vi*3]   = allVerts[vi*3]   - cx;
          positions[vi*3+1] = allVerts[vi*3+1] - cy;
          positions[vi*3+2] = allVerts[vi*3+2] - cz;
        }

        // FNV-1a content hash (same as browser)
        const idxBuf = Buffer.from(new Int32Array(allIdx).buffer);
        const hashSrc = Buffer.concat([Buffer.from(positions.buffer), idxBuf]);
        const geomHash = fnvHash(hashSrc);

        geometries.push({ guid: el.guid, geomHash, vertices: Buffer.from(positions.buffer), indices: idxBuf });

        // Bbox fallback: compute from vertices if no IFC bbox
        if (!el._bboxX) {
          let minX=Infinity,maxX=-Infinity,minY=Infinity,maxY=-Infinity,minZ=Infinity,maxZ=-Infinity;
          for (let vi = 0; vi < vertCount; vi++) {
            const x=positions[vi*3],y=positions[vi*3+1],z=positions[vi*3+2];
            if(x<minX)minX=x;if(x>maxX)maxX=x;
            if(y<minY)minY=y;if(y>maxY)maxY=y;
            if(z<minZ)minZ=z;if(z>maxZ)maxZ=z;
          }
          el._bboxX = maxX-minX; el._bboxY = maxY-minY; el._bboxZ = maxZ-minZ;
        }

        transforms.push({ guid: el.guid, cx, cy, cz, rx:0, ry:0, rz:0,
          bx: el._bboxX, by: el._bboxY, bz: el._bboxZ });

        // Material: prefer IFC-resolved color, fall back to geometry color
        if (elementMaterialColor[el.expressID]) {
          el.material = elementMaterialColor[el.expressID];
          matCount++;
        } else if (bestColor) {
          let r = bestColor.x, g = bestColor.y, b = bestColor.z;
          if (r > 0.95 && g > 0.95 && b > 0.95) { r = 0.92; g = 0.90; b = 0.85; }
          el.material = `${r.toFixed(3)},${g.toFixed(3)},${b.toFixed(3)},${bestColor.w.toFixed(3)}`;
          matCount++;
        }
      }
    } catch(e) {
      // skip
    }
    if ((ei+1) % 1000 === 0) process.stdout.write(`\r  tessellating ${ei+1}/${elements.length}...`);
  }
  console.log(`\n§GEOM_DONE elements=${elements.length} geometries=${geometries.length} uniqueHashes=${new Set(geometries.map(g=>g.geomHash)).size} materials=${matCount}`);

  // ── Auto-scale heuristic (mm → m if coords > 500) ──
  let autoScale = 1.0;
  if (transforms.length > 0) {
    // Auto-scale heuristic: if element SPREAD > 500m, assume mm → /1000
    // (Don't use absolute coords — buildings can be at large grid offsets in metres)
    let minX=Infinity,maxX=-Infinity,minY=Infinity,maxY=-Infinity,minZ=Infinity,maxZ=-Infinity;
    for (const t of transforms) {
      if(t.cx<minX)minX=t.cx;if(t.cx>maxX)maxX=t.cx;
      if(t.cy<minY)minY=t.cy;if(t.cy>maxY)maxY=t.cy;
      if(t.cz<minZ)minZ=t.cz;if(t.cz>maxZ)maxZ=t.cz;
    }
    const spread = Math.max(maxX-minX, maxY-minY, maxZ-minZ);
    if (spread > 500) {
      autoScale = 0.001;
      for (const t of transforms) {
        t.cx *= 0.001; t.cy *= 0.001; t.cz *= 0.001;
        if (t.bx) { t.bx *= 0.001; t.by *= 0.001; t.bz *= 0.001; }
      }
      for (const g of geometries) {
        const vBuf = new Float32Array(g.vertices.buffer, g.vertices.byteOffset, g.vertices.byteLength / 4);
        for (let i = 0; i < vBuf.length; i++) vBuf[i] *= 0.001;
      }
    }
  }
  console.log(`§UNITS autoScale=${autoScale}`);

  // ── 4D extraction (if present) ──
  const schedules = [], tasks = [], taskSequences = [], taskElements = [];
  try {
    const schedIds = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCWORKSCHEDULE);
    for (let i = 0; i < schedIds.size(); i++) {
      const s = ifcApi.GetLine(modelID, schedIds.get(i));
      schedules.push({
        id: s.GlobalId ? s.GlobalId.value : 'SCHED_' + i,
        name: s.Name ? s.Name.value : 'Schedule ' + i,
        status: s.Status ? s.Status.value : null,
        created: s.CreationDate ? s.CreationDate.value : null,
      });
    }
  } catch(e) {}

  try {
    const taskIds = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCTASK);
    for (let i = 0; i < taskIds.size(); i++) {
      try {
        const t = ifcApi.GetLine(modelID, taskIds.get(i));
        const taskTime = t.TaskTime ? ifcApi.GetLine(modelID, t.TaskTime.value) : null;
        tasks.push({
          id: t.GlobalId ? t.GlobalId.value : 'TASK_' + i,
          name: t.Name ? t.Name.value : 'Task ' + i,
          start: taskTime && taskTime.ScheduleStart ? taskTime.ScheduleStart.value : null,
          finish: taskTime && taskTime.ScheduleFinish ? taskTime.ScheduleFinish.value : null,
          duration: taskTime && taskTime.ScheduleDuration ? parseFloat(taskTime.ScheduleDuration.value) || null : null,
          status: t.Status ? t.Status.value : null,
        });
      } catch(e) {}
    }
  } catch(e) {}

  try {
    const seqIds = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCRELSEQUENCE);
    for (let i = 0; i < seqIds.size(); i++) {
      try {
        const s = ifcApi.GetLine(modelID, seqIds.get(i));
        const pred = s.RelatingProcess ? ifcApi.GetLine(modelID, s.RelatingProcess.value) : null;
        const succ = s.RelatedProcess ? ifcApi.GetLine(modelID, s.RelatedProcess.value) : null;
        if (pred && succ) {
          taskSequences.push({
            predId: pred.GlobalId ? pred.GlobalId.value : null,
            succId: succ.GlobalId ? succ.GlobalId.value : null,
            type: s.SequenceType ? s.SequenceType.value : 'FINISH_START',
            lag: s.TimeLag ? parseFloat(s.TimeLag.value) || 0 : 0,
          });
        }
      } catch(e) {}
    }
  } catch(e) {}

  try {
    const assignIds = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCRELASSIGNSTOPROCESS);
    for (let i = 0; i < assignIds.size(); i++) {
      try {
        const a = ifcApi.GetLine(modelID, assignIds.get(i));
        const process = a.RelatingProcess ? ifcApi.GetLine(modelID, a.RelatingProcess.value) : null;
        if (process && process.GlobalId && a.RelatedObjects) {
          const taskId = process.GlobalId.value;
          for (let j = 0; j < a.RelatedObjects.length; j++) {
            try {
              const obj = ifcApi.GetLine(modelID, a.RelatedObjects[j].value);
              if (obj.GlobalId) taskElements.push({ taskId, guid: obj.GlobalId.value });
            } catch(e) {}
          }
        }
      } catch(e) {}
    }
  } catch(e) {}

  if (schedules.length || tasks.length) {
    console.log(`§4D_FOUND schedules=${schedules.length} tasks=${tasks.length} sequences=${taskSequences.length} taskElements=${taskElements.length}`);
  } else {
    console.log(`§4D_NONE no scheduling data in this IFC`);
  }

  ifcApi.CloseModel(modelID);

  // ── Write SQLite DB ──
  if (fs.existsSync(outPath)) fs.unlinkSync(outPath);
  const db = new Database(outPath);

  db.exec(`CREATE TABLE project_metadata (key TEXT PRIMARY KEY, value TEXT)`);
  db.exec(`CREATE TABLE elements_meta (guid TEXT PRIMARY KEY, ifc_class TEXT, element_name TEXT, storey TEXT, discipline TEXT, material_name TEXT, material_rgba TEXT, building TEXT)`);
  db.exec(`CREATE TABLE element_transforms (guid TEXT PRIMARY KEY, center_x REAL, center_y REAL, center_z REAL, rotation_x REAL, rotation_y REAL, rotation_z REAL, bbox_x REAL, bbox_y REAL, bbox_z REAL)`);
  db.exec(`CREATE TABLE element_instances (guid TEXT PRIMARY KEY, geometry_hash TEXT)`);
  db.exec(`CREATE TABLE component_geometries (geometry_hash TEXT PRIMARY KEY, vertices BLOB, faces BLOB, building TEXT)`);

  // 4D tables (empty if no scheduling data)
  db.exec(`CREATE TABLE schedules (schedule_id TEXT PRIMARY KEY, name TEXT, status TEXT, created_date TEXT)`);
  db.exec(`CREATE TABLE tasks (task_id TEXT PRIMARY KEY, schedule_id TEXT, name TEXT, start_date TEXT, finish_date TEXT, duration_days REAL, status TEXT)`);
  db.exec(`CREATE TABLE task_sequences (predecessor_id TEXT, successor_id TEXT, sequence_type TEXT, lag_days REAL DEFAULT 0, PRIMARY KEY (predecessor_id, successor_id))`);
  db.exec(`CREATE TABLE task_elements (task_id TEXT, guid TEXT, PRIMARY KEY (task_id, guid))`);

  // Insert metadata
  const metaStmt = db.prepare('INSERT INTO project_metadata VALUES (?,?)');
  metaStmt.run('project_name', projectName);
  metaStmt.run('import_date', new Date().toISOString());
  metaStmt.run('building_name', buildingName);
  metaStmt.run('source_file', path.basename(ifcPath));

  // Insert elements
  const elStmt = db.prepare('INSERT OR IGNORE INTO elements_meta VALUES (?,?,?,?,?,?,?,?)');
  for (const el of elements) {
    elStmt.run(el.guid, el.ifcClass, el.name, el.storey, el.discipline, null, el.material || null, buildingName);
  }

  // Insert transforms
  const trStmt = db.prepare('INSERT OR IGNORE INTO element_transforms VALUES (?,?,?,?,?,?,?,?,?,?)');
  for (const t of transforms) {
    trStmt.run(t.guid, t.cx, t.cy, t.cz, t.rx, t.ry, t.rz, t.bx, t.by, t.bz);
  }

  // Insert instances + geometry BLOBs (deduped)
  const instStmt = db.prepare('INSERT OR IGNORE INTO element_instances VALUES (?,?)');
  const geoStmt = db.prepare('INSERT OR IGNORE INTO component_geometries VALUES (?,?,?,?)');
  const seenHashes = new Set();
  for (const g of geometries) {
    instStmt.run(g.guid, g.geomHash);
    if (!seenHashes.has(g.geomHash)) {
      geoStmt.run(g.geomHash, g.vertices, g.indices, buildingName);
      seenHashes.add(g.geomHash);
    }
  }

  // Insert 4D data
  const schedStmt = db.prepare('INSERT OR IGNORE INTO schedules VALUES (?,?,?,?)');
  for (const s of schedules) schedStmt.run(s.id, s.name, s.status, s.created);

  const taskStmt = db.prepare('INSERT OR IGNORE INTO tasks VALUES (?,?,?,?,?,?,?)');
  for (const t of tasks) taskStmt.run(t.id, schedules[0]?.id || null, t.name, t.start, t.finish, t.duration, t.status);

  const seqStmt = db.prepare('INSERT OR IGNORE INTO task_sequences VALUES (?,?,?,?)');
  for (const s of taskSequences) seqStmt.run(s.predId, s.succId, s.type, s.lag);

  const teStmt = db.prepare('INSERT OR IGNORE INTO task_elements VALUES (?,?)');
  for (const te of taskElements) teStmt.run(te.taskId, te.guid);

  db.close();

  // ── Summary ──
  const fileSize = fs.statSync(outPath).size;
  console.log(`§DB_WRITTEN ${outPath} size=${(fileSize/1024/1024).toFixed(1)}MB`);
  console.log(`§SUMMARY elements=${elements.length} geometries=${geometries.length} uniqueHashes=${seenHashes.size} 4D_tasks=${tasks.length}`);
}

main().catch(e => { console.error('§FATAL', e.message); process.exit(1); });
