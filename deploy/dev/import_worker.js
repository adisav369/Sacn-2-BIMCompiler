/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
// import_worker.js — Web Worker: parse IFC via web-ifc, extract to sql.js DBs
// Runs off main thread to avoid UI freeze.
// Input:  postMessage({ arrayBuffer, filename })
// Output: postMessage({ type: 'progress', pct, phase }) or { type: 'done', extracted, library, meta }

console.log('[S220] §WORKER_START loading web-ifc from CDN...');
importScripts('https://unpkg.com/web-ifc@0.0.77/web-ifc-api-iife.js');
console.log('[S220] §WORKER_LOADED web-ifc IIFE loaded, WebIFC=' + typeof WebIFC);

// Discipline classification (same as Python pipeline)
const DISC_MAP = {
  // ARC
  IfcWall: 'ARC', IfcWallStandardCase: 'ARC', IfcSlab: 'ARC', IfcDoor: 'ARC',
  IfcWindow: 'ARC', IfcRoof: 'ARC', IfcStair: 'ARC', IfcStairFlight: 'ARC',
  IfcRailing: 'ARC', IfcCovering: 'ARC', IfcCurtainWall: 'ARC', IfcPlate: 'ARC',
  IfcFurnishingElement: 'ARC', IfcBuildingElementProxy: 'ARC', IfcSpace: 'ARC',
  IfcFurniture: 'ARC', IfcSystemFurnitureElement: 'ARC', IfcBuildingElementPart: 'ARC',
  IfcRamp: 'ARC', IfcRampFlight: 'ARC', IfcTransportElement: 'ARC',
  // STR
  IfcBeam: 'STR', IfcColumn: 'STR', IfcFooting: 'STR', IfcPile: 'STR',
  IfcMember: 'STR', IfcReinforcingBar: 'STR', IfcReinforcingMesh: 'STR',
  IfcTendon: 'STR', IfcTendonAnchor: 'STR',
  // ELEC
  IfcCableSegment: 'ELEC', IfcCableCarrierSegment: 'ELEC', IfcCableCarrierFitting: 'ELEC',
  IfcElectricAppliance: 'ELEC', IfcLightFixture: 'ELEC', IfcOutlet: 'ELEC',
  IfcJunctionBox: 'ELEC', IfcSwitchingDevice: 'ELEC', IfcElectricDistributionBoard: 'ELEC',
  // PLB
  IfcPipeSegment: 'PLB', IfcPipeFitting: 'PLB', IfcSanitaryTerminal: 'PLB',
  IfcValve: 'PLB', IfcWasteTerminal: 'PLB', IfcStackTerminal: 'PLB',
  // ACMV
  IfcDuctSegment: 'ACMV', IfcDuctFitting: 'ACMV', IfcAirTerminal: 'ACMV',
  IfcAirTerminalBox: 'ACMV', IfcUnitaryEquipment: 'ACMV', IfcCoil: 'ACMV',
  IfcFan: 'ACMV', IfcCompressor: 'ACMV', IfcChiller: 'ACMV',
  // FP
  IfcFireSuppressionTerminal: 'FP', IfcAlarm: 'FP',
  // MEP generic
  IfcFlowSegment: 'MEP', IfcFlowTerminal: 'MEP', IfcFlowFitting: 'MEP',
  IfcFlowController: 'MEP', IfcFlowMovingDevice: 'MEP', IfcFlowStorageDevice: 'MEP',
  IfcFlowTreatmentDevice: 'MEP', IfcEnergyConversionDevice: 'MEP',
  IfcDistributionElement: 'MEP', IfcDistributionFlowElement: 'MEP',
  IfcDistributionControlElement: 'MEP',
};

// Reverse lookup: IFCWALLSTANDARDCASE → IfcWallStandardCase (from DISC_MAP keys)
const CLASS_NAME_MAP = {};
for (var k in DISC_MAP) { CLASS_NAME_MAP[k.toUpperCase()] = k; }
// Add extras not in DISC_MAP
CLASS_NAME_MAP['IFCOPENINGELEMENT'] = 'IfcOpeningElement';
CLASS_NAME_MAP['IFCSITE'] = 'IfcSite';
CLASS_NAME_MAP['IFCGEOGRAPHICELEMENT'] = 'IfcGeographicElement';

function properClassName(typeCode) {
  var upper = typeCode.toUpperCase();
  return CLASS_NAME_MAP[upper] || ('Ifc' + typeCode.substring(3).charAt(0).toUpperCase() + typeCode.substring(4).toLowerCase());
}

function classifyDisc(ifcClass) {
  return DISC_MAP[ifcClass] || 'ARC';
}

self.onmessage = async function(e) {
  const { arrayBuffer, filename } = e.data;
  try {
    // Phase 1: Initialize web-ifc (10%)
    post('progress', 5, 'Initializing parser...');
    const ifcApi = new WebIFC.IfcAPI();
    console.log('[S220] §WASM_INIT starting with CDN locateFile...');
    await ifcApi.Init(function(path) {
      var resolved = 'https://unpkg.com/web-ifc@0.0.77/' + path;
      console.log('[S220] §WASM_LOCATE ' + path + ' → ' + resolved);
      return resolved;
    }, true);
    console.log('[S220] §WASM_INIT done');
    post('progress', 10, 'Parsing IFC...');

    // Phase 2: Parse IFC (10-30%)
    const data = new Uint8Array(arrayBuffer);
    console.log('[S220] §PARSE_START size=' + (data.byteLength / 1024 / 1024).toFixed(1) + 'MB');
    var modelID;
    try {
      modelID = ifcApi.OpenModel(data, {
        COORDINATE_TO_ORIGIN: false,
        USE_FAST_BOOLS: true,       // subtract IfcOpeningElement from walls
        OPTIMIZE_PROFILES: true,
      });
    } catch(parseErr) {
      var msg = String(parseErr.message || parseErr);
      console.log('[S220] §PARSE_FAIL ' + msg);
      if (msg.includes('Unsupported Schema')) {
        var schema = msg.match(/Schema[:\s]*([\w.]+)/);
        self.postMessage({ type: 'error', message: 'Unsupported IFC version' + (schema ? ' (' + schema[1] + ')' : '') + '. Supported: IFC2x3, IFC4, IFC4x3.' });
      } else {
        self.postMessage({ type: 'error', message: 'Failed to parse IFC: ' + msg });
      }
      return;
    }
    console.log('[S220] §PARSE_OK modelID=' + modelID);
    if (modelID < 0) {
      console.log('[S220] §PARSE_FAIL modelID=' + modelID + ' (unsupported schema?)');
      self.postMessage({ type: 'error', message: 'Failed to parse IFC. Check schema version — supported: IFC2x3, IFC4, IFC4x3.' });
      return;
    }
    // Unit scaling applied AFTER tessellation via heuristic (web-ifc is inconsistent)
    post('progress', 30, 'Extracting elements...');

    // Phase 3: Extract spatial structure + elements (30-70%)
    const lines = ifcApi.GetAllLines(modelID);
    const totalLines = lines.size();
    console.log('[S220] §EXTRACT_START totalLines=' + totalLines);

    // Get project info
    const projectLines = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCPROJECT);
    let projectName = filename.replace(/\.ifc$/i, '');
    if (projectLines.size() > 0) {
      try {
        const proj = ifcApi.GetLine(modelID, projectLines.get(0));
        if (proj.Name && proj.Name.value) projectName = proj.Name.value;
      } catch(e) { /* use filename */ }
    }

    // Get storeys
    const storeyMap = {}; // expressID → storey name
    const storeyLines = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCBUILDINGSTOREY);
    for (let i = 0; i < storeyLines.size(); i++) {
      try {
        const s = ifcApi.GetLine(modelID, storeyLines.get(i));
        storeyMap[storeyLines.get(i)] = s.Name ? s.Name.value : 'Level ' + i;
      } catch(e) { /* skip */ }
    }

    // Get containment (element → storey)
    const elementToStorey = {};
    const relLines = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCRELCONTAINEDINSPATIALSTRUCTURE);
    for (let i = 0; i < relLines.size(); i++) {
      try {
        const rel = ifcApi.GetLine(modelID, relLines.get(i));
        const storeyId = rel.RelatingStructure ? rel.RelatingStructure.value : null;
        const storeyName = storeyMap[storeyId] || 'Unknown';
        if (rel.RelatedElements) {
          for (let j = 0; j < rel.RelatedElements.length; j++) {
            const elId = rel.RelatedElements[j].value;
            elementToStorey[elId] = storeyName;
          }
        }
      } catch(e) { /* skip */ }
    }

    // Collect product types to extract
    const PRODUCT_TYPES = [
      // ARC
      WebIFC.IFCWALL, WebIFC.IFCWALLSTANDARDCASE, WebIFC.IFCSLAB, WebIFC.IFCDOOR,
      WebIFC.IFCWINDOW, WebIFC.IFCROOF, WebIFC.IFCSTAIR, WebIFC.IFCSTAIRFLIGHT,
      WebIFC.IFCRAILING, WebIFC.IFCCOVERING, WebIFC.IFCCURTAINWALL, WebIFC.IFCPLATE,
      WebIFC.IFCFURNISHINGELEMENT, WebIFC.IFCBUILDINGELEMENTPROXY,
      WebIFC.IFCFURNITURE, WebIFC.IFCSYSTEMFURNITUREELEMENT,
      WebIFC.IFCBUILDINGELEMENTPART, WebIFC.IFCRAMP, WebIFC.IFCRAMPFLIGHT,
      WebIFC.IFCTRANSPORTELEMENT,
      // STR
      WebIFC.IFCBEAM, WebIFC.IFCCOLUMN, WebIFC.IFCFOOTING, WebIFC.IFCMEMBER,
      WebIFC.IFCPILE, WebIFC.IFCREINFORCINGBAR, WebIFC.IFCREINFORCINGMESH,
      WebIFC.IFCTENDON, WebIFC.IFCTENDONANCHOR,
      // MEP
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
      // INFRA (IFC4x3)
      WebIFC.IFCGEOGRAPHICELEMENT,
      // Note: IfcSpace + IfcSite excluded — render as solid boxes/terrain, obscure model
    ];

    // Filter out undefined types (some IFC versions don't have all)
    const validTypes = PRODUCT_TYPES.filter(t => t !== undefined);

    // Collect all elements
    const elements = [];
    const elementIds = new Set();
    for (const typeId of validTypes) {
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
            ifcClass: ifcClass,
            name: el.Name ? el.Name.value : ifcClass + '_' + id,
            storey: elementToStorey[id] || 'Unknown',
            discipline: classifyDisc(ifcClass),
            material: '',
          });
        } catch(e) { /* skip unreadable */ }
      }
    }

    console.log('[S220] §ELEMENTS_FOUND count=' + elements.length + ' storeys=' + Object.keys(storeyMap).length);
    post('progress', 50, 'Tessellating ' + elements.length + ' elements...');

    // Phase 4: Tessellate geometry (50-90%)
    // Same pipeline as Java: apply 4x4 transform → compute centroid → re-center at origin
    // Viewer expects: library vertices centered at origin, center_x/y/z = world position
    const geometries = []; // { guid, geomHash, vertices: ArrayBuffer, indices: ArrayBuffer }
    const transforms = []; // { guid, cx, cy, cz, rx, ry, rz }
    let geomDone = 0;
    const geomTotal = elements.length;
    let matCount = 0;

    for (const el of elements) {
      try {
        const flatMesh = ifcApi.GetFlatMesh(modelID, el.expressID);
        // Try all geometries in flatMesh, merge vertices
        var allVerts = [], allIdx = [], vertOffset = 0;
        var bestColor = null;
        for (let gi = 0; gi < flatMesh.geometries.size(); gi++) {
          var geo = flatMesh.geometries.get(gi);
          var meshData = ifcApi.GetGeometry(modelID, geo.geometryExpressID);
          var vSize = meshData.GetVertexDataSize();
          var iSize = meshData.GetIndexDataSize();
          if (vSize === 0 || iSize === 0) continue;
          var verts = ifcApi.GetVertexArray(meshData.GetVertexData(), vSize);
          var idx = ifcApi.GetIndexArray(meshData.GetIndexData(), iSize);
          var m = geo.flatTransformation;
          var vc = verts.length / 6;
          // Transform vertices: web-ifc Y-up → IFC Z-up
          for (var vi = 0; vi < vc; vi++) {
            var lx = verts[vi * 6], ly = verts[vi * 6 + 1], lz = verts[vi * 6 + 2];
            var wx = m[0]*lx + m[4]*ly + m[8]*lz  + m[12];
            var wy = m[1]*lx + m[5]*ly + m[9]*lz  + m[13];
            var wz = m[2]*lx + m[6]*ly + m[10]*lz + m[14];
            allVerts.push(wx, -wz, wy);
          }
          // Offset indices for merged geometry
          for (var ii = 0; ii < idx.length; ii++) {
            allIdx.push(idx[ii] + vertOffset);
          }
          vertOffset += vc;
          if (!bestColor && geo.color && geo.color.x !== undefined) bestColor = geo.color;
        }
        if (allVerts.length >= 9) {  // at least 3 vertices (1 triangle)
          var vertCount = allVerts.length / 3;
          // Compute centroid
          var sumX = 0, sumY = 0, sumZ = 0;
          for (var vi = 0; vi < vertCount; vi++) {
            sumX += allVerts[vi * 3];
            sumY += allVerts[vi * 3 + 1];
            sumZ += allVerts[vi * 3 + 2];
          }
          var cx = sumX / vertCount, cy = sumY / vertCount, cz = sumZ / vertCount;
          // Re-center at origin
          var positions = new Float32Array(allVerts.length);
          for (var vi = 0; vi < vertCount; vi++) {
            positions[vi * 3]     = allVerts[vi * 3]     - cx;
            positions[vi * 3 + 1] = allVerts[vi * 3 + 1] - cy;
            positions[vi * 3 + 2] = allVerts[vi * 3 + 2] - cz;
          }
          geometries.push({
            guid: el.guid,
            geomHash: el.guid,
            vertices: positions.buffer,
            indices: new Int32Array(allIdx).buffer,
          });
          transforms.push({ guid: el.guid, cx: cx, cy: cy, cz: cz, rx: 0, ry: 0, rz: 0 });
          if (bestColor) {
            el.material = bestColor.x.toFixed(3) + ',' + bestColor.y.toFixed(3) + ',' + bestColor.z.toFixed(3) + ',' + bestColor.w.toFixed(3);
            matCount++;
          }
        }
      } catch(e) {
        console.log('[S220] §GEOM_SKIP guid=' + el.guid + ' class=' + el.ifcClass + ' err=' + (e.message || e));
      }

      geomDone++;
      if (geomDone % 50 === 0 || geomDone === geomTotal) {
        const pct = 50 + Math.floor((geomDone / geomTotal) * 40);
        post('progress', pct, 'Tessellating ' + geomDone + '/' + geomTotal + '...');
      }
    }

    const skipped = elements.length - geometries.length;
    console.log('[S220] §GEOM_SUMMARY elements=' + elements.length + ' geometries=' + geometries.length + ' skipped=' + skipped + ' materials=' + matCount);

    post('progress', 92, 'Building databases...');

    // Phase 5: Build sql.js databases (90-100%)
    // We send raw data back to main thread — it builds sql.js DBs there
    // (sql.js WASM can't run in all workers easily)
    const discCounts = {};
    for (const el of elements) {
      discCounts[el.discipline] = (discCounts[el.discipline] || 0) + 1;
    }

    const storeys = [...new Set(elements.map(e => e.storey))].sort();

    // Post-hoc unit heuristic: if bounding box > 500m in any axis, assume mm → divide by 1000
    var autoScale = 1.0;
    if (transforms.length > 0) {
      var maxCoord = 0;
      for (var ti = 0; ti < transforms.length; ti++) {
        maxCoord = Math.max(maxCoord, Math.abs(transforms[ti].cx), Math.abs(transforms[ti].cy), Math.abs(transforms[ti].cz));
      }
      if (maxCoord > 500) {
        autoScale = 0.001;
        for (var ti = 0; ti < transforms.length; ti++) {
          transforms[ti].cx *= 0.001;
          transforms[ti].cy *= 0.001;
          transforms[ti].cz *= 0.001;
        }
        // Also scale library vertices
        for (var gi = 0; gi < geometries.length; gi++) {
          var vBuf = new Float32Array(geometries[gi].vertices);
          for (var vi = 0; vi < vBuf.length; vi++) vBuf[vi] *= 0.001;
          geometries[gi].vertices = vBuf.buffer;
        }
      }
    }
    console.log('[S220] §UNITS autoScale=' + autoScale + (autoScale !== 1.0 ? ' (mm→m heuristic)' : ' (already metres)'));
    console.log('[S220] §GEOM_DONE elements=' + elements.length + ' withGeometry=' + geometries.length + ' skipped=' + (elements.length - geometries.length) + ' withMaterial=' + matCount);
    post('progress', 95, 'Packaging...');

    const result = {
      type: 'done',
      meta: {
        name: projectName,
        filename: filename,
        elementCount: elements.length,
        geomCount: geometries.length,
        disciplines: discCounts,
        storeys: storeys,
      },
      elements: elements,
      geometries: geometries,
      transforms: transforms,
    };

    // Transfer array buffers for zero-copy
    const transferables = [];
    for (const g of geometries) {
      transferables.push(g.vertices, g.indices);
    }

    post('progress', 100, 'Done');
    self.postMessage(result, transferables);

    ifcApi.CloseModel(modelID);
  } catch(err) {
    console.log('[S220] §IMPORT_FATAL ' + (err.message || String(err)));
    console.log('[S220] §IMPORT_STACK ' + (err.stack || 'no stack'));
    self.postMessage({ type: 'error', message: err.message || String(err) });
  }
};

function post(type, pct, phase) {
  self.postMessage({ type: type, pct: pct, phase: phase });
}
