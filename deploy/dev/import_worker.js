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
      modelID = ifcApi.OpenModel(data);
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
      WebIFC.IFCWALL, WebIFC.IFCWALLSTANDARDCASE, WebIFC.IFCSLAB, WebIFC.IFCDOOR,
      WebIFC.IFCWINDOW, WebIFC.IFCROOF, WebIFC.IFCSTAIR, WebIFC.IFCSTAIRFLIGHT,
      WebIFC.IFCRAILING, WebIFC.IFCCOVERING, WebIFC.IFCCURTAINWALL, WebIFC.IFCPLATE,
      WebIFC.IFCFURNISHINGELEMENT, WebIFC.IFCBUILDINGELEMENTPROXY,
      WebIFC.IFCBEAM, WebIFC.IFCCOLUMN, WebIFC.IFCFOOTING, WebIFC.IFCMEMBER,
      WebIFC.IFCFLOWSEGMENT, WebIFC.IFCFLOWTERMINAL, WebIFC.IFCFLOWFITTING,
      WebIFC.IFCFLOWCONTROLLER, WebIFC.IFCFLOWMOVINGDEVICE,
      WebIFC.IFCPIPESEGMENT, WebIFC.IFCPIPEFITTING,
      WebIFC.IFCDUCTSEGMENT, WebIFC.IFCDUCTFITTING,
      WebIFC.IFCCABLESEGMENT, WebIFC.IFCCABLECARRIERSEGMENT,
      WebIFC.IFCLIGHTFIXTURE, WebIFC.IFCOUTLET,
      WebIFC.IFCSANITARYTERMINAL, WebIFC.IFCUNITARYEQUIPMENT,
      WebIFC.IFCDISTRIBUTIONFLOWELEMENT, WebIFC.IFCDISTRIBUTIONCONTROLELEMENT,
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
          const typeName = ifcApi.GetNameFromTypeCode(typeId) || 'IfcBuildingElement';
          const ifcClass = typeName.replace('IFC', 'Ifc');
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
    const geometries = []; // { guid, vertices: Float32Array, indices: Int32Array }
    const transforms = []; // { guid, matrix: Float64Array(16) }
    let geomDone = 0;
    const geomTotal = elements.length;

    for (const el of elements) {
      try {
        const flatMesh = ifcApi.GetFlatMesh(modelID, el.expressID);
        if (flatMesh.geometries.size() > 0) {
          // Take first geometry
          const geo = flatMesh.geometries.get(0);
          const meshData = ifcApi.GetGeometry(modelID, geo.geometryExpressID);
          const verts = ifcApi.GetVertexArray(meshData.GetVertexData(), meshData.GetVertexDataSize());
          const idx = ifcApi.GetIndexArray(meshData.GetIndexData(), meshData.GetIndexDataSize());

          // verts is interleaved: x,y,z,nx,ny,nz per vertex (6 floats each)
          // Extract only positions
          const vertCount = verts.length / 6;
          const positions = new Float32Array(vertCount * 3);
          for (let i = 0; i < vertCount; i++) {
            positions[i * 3] = verts[i * 6];
            positions[i * 3 + 1] = verts[i * 6 + 1];
            positions[i * 3 + 2] = verts[i * 6 + 2];
          }

          // Geometry hash = guid (each element has unique geometry in import)
          var geomHash = el.guid;

          geometries.push({
            guid: el.guid,
            geomHash: geomHash,
            vertices: positions.buffer,
            indices: new Int32Array(idx).buffer,
          });

          // Extract center + rotation from 4x4 matrix
          // Translation = columns 12,13,14 (row-major) or last column
          var m = geo.flatTransformation;
          transforms.push({
            guid: el.guid,
            cx: m[12], cy: m[13], cz: m[14],
            rx: 0, ry: 0, rz: 0,  // rotation extracted as euler would need decomposition — zero for now
          });
        }
      } catch(e) { /* skip elements without geometry */ }

      geomDone++;
      if (geomDone % 50 === 0 || geomDone === geomTotal) {
        const pct = 50 + Math.floor((geomDone / geomTotal) * 40);
        post('progress', pct, 'Tessellating ' + geomDone + '/' + geomTotal + '...');
      }
    }

    post('progress', 92, 'Building databases...');

    // Phase 5: Build sql.js databases (90-100%)
    // We send raw data back to main thread — it builds sql.js DBs there
    // (sql.js WASM can't run in all workers easily)
    const discCounts = {};
    for (const el of elements) {
      discCounts[el.discipline] = (discCounts[el.discipline] || 0) + 1;
    }

    const storeys = [...new Set(elements.map(e => e.storey))].sort();

    console.log('[S220] §GEOM_DONE elements=' + elements.length + ' withGeometry=' + geometries.length + ' skipped=' + (elements.length - geometries.length));
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
