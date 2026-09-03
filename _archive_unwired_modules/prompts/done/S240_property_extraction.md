/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */

# ⚠ DO NOT REMOVE — Scope guard
# Scope: Extend import_worker.js to extract Psets, quantities,
#        4D tasks, 5D cost items, and IfcSpace from IFC files
#        that contain this data. Zero cost for IFCs that do not.
# Read the log after every run. Exit code is not evidence.
# Spec-first: implement only what is described in a § section below.

---

# S240 — Property + 4D/5D Extraction in Browser Importer

## Context

`import_worker.js` already extracts geometry, storeys, discipline,
GUID, name, and material from IFC files via web-ifc@0.0.77.

All required type constants are confirmed present in web-ifc:
- `WebIFC.IFCPROPERTYSET`, `IFCPROPERTYSINGLEVALUE`, `IFCRELDEFINESBYPROPERTIES`
- `WebIFC.IFCELEMENTQUANTITY`, `IFCQUANTITYAREA/VOLUME/LENGTH`
- `WebIFC.IFCTASK`, `IFCTASKTIME`, `IFCRELASSIGNSTASKS`, `IFCRELSEQUENCE`
- `WebIFC.IFCCOSTSCHEDULE`, `IFCCOSTITEM`, `IFCRELSCHEDULESCOSTITEMS`
- `WebIFC.IFCSPACE`, `IFCRELSPACEBOUNDARY`

Key rule: `GetLineIDsWithType()` returns empty list instantly if the type
does not exist in the file. Zero cost for IFCs without this data.

---

## §1. DB Schema Extensions

Add to the sql.js `output.db` build in `main.js` (or wherever DBs are created):

```sql
-- § SCHEMA_PROPERTIES
CREATE TABLE IF NOT EXISTS element_properties (
    guid        TEXT NOT NULL,
    pset_name   TEXT NOT NULL,  -- Pset_WallCommon, Custom_FireRating etc
    prop_name   TEXT NOT NULL,  -- IsExternal, FireRating, LoadBearing
    prop_value  TEXT,           -- serialised value (always string)
    prop_type   TEXT            -- IfcLabel, IfcBoolean, IfcReal, IfcLengthMeasure
);
CREATE INDEX IF NOT EXISTS idx_ep_guid ON element_properties(guid);
CREATE INDEX IF NOT EXISTS idx_ep_pset ON element_properties(pset_name, prop_name);

-- § SCHEMA_QUANTITIES
CREATE TABLE IF NOT EXISTS element_quantities (
    guid        TEXT NOT NULL,
    qset_name   TEXT NOT NULL,  -- BaseQuantities
    qty_name    TEXT NOT NULL,  -- NetSideArea, GrossVolume, Length
    qty_value   REAL,
    qty_unit    TEXT            -- m2, m3, m
);
CREATE INDEX IF NOT EXISTS idx_eq_guid ON element_quantities(guid);

-- § SCHEMA_SPACES
CREATE TABLE IF NOT EXISTS spaces (
    guid        TEXT PRIMARY KEY,
    name        TEXT,           -- 'Bedroom 1', 'Living Room'
    long_name   TEXT,           -- full label
    storey      TEXT,
    area        REAL,           -- NetFloorArea m2
    volume      REAL            -- NetVolume m3
);

-- § SCHEMA_TASKS (4D)
CREATE TABLE IF NOT EXISTS tasks (
    task_guid       TEXT PRIMARY KEY,
    name            TEXT,
    description     TEXT,
    status          TEXT,       -- NOTSTARTED, STARTED, COMPLETED
    schedule_start  TEXT,       -- ISO date
    schedule_finish TEXT,       -- ISO date
    actual_start    TEXT,
    actual_finish   TEXT,
    duration        TEXT        -- P5D = 5 days (ISO 8601 duration)
);

CREATE TABLE IF NOT EXISTS task_elements (
    task_guid   TEXT NOT NULL,
    guid        TEXT NOT NULL   -- element assigned to this task
);
CREATE INDEX IF NOT EXISTS idx_te_task ON task_elements(task_guid);
CREATE INDEX IF NOT EXISTS idx_te_elem ON task_elements(guid);

CREATE TABLE IF NOT EXISTS task_sequences (
    pred_guid   TEXT NOT NULL,  -- predecessor task
    succ_guid   TEXT NOT NULL,  -- successor task
    seq_type    TEXT            -- FINISH_START, START_START, FINISH_FINISH
);

-- § SCHEMA_COST (5D)
CREATE TABLE IF NOT EXISTS cost_items (
    cost_guid   TEXT PRIMARY KEY,
    name        TEXT,
    description TEXT,
    total_cost  REAL,
    currency    TEXT            -- USD, MYR, BDT
);

CREATE TABLE IF NOT EXISTS cost_elements (
    cost_guid   TEXT NOT NULL,
    guid        TEXT NOT NULL   -- element assigned to this cost item
);
CREATE INDEX IF NOT EXISTS idx_ce_cost ON cost_elements(cost_guid);
CREATE INDEX IF NOT EXISTS idx_ce_elem ON cost_elements(guid);
```

---

## §2. Extraction Code — import_worker.js additions

Add after Phase 3 (spatial structure extraction), before Phase 4 (tessellation).
All blocks are guarded — skip silently if type not present.

### §2.1 Property Sets

```javascript
// § EXTRACT_PSETS
// Map: expressID → [{pset_name, prop_name, prop_value, prop_type}]
const elementProps = {}; // guid → array of {pset_name, prop_name, prop_value, prop_type}

try {
  const psetRels = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCRELDEFINESBYPROPERTIES);
  for (let i = 0; i < psetRels.size(); i++) {
    try {
      const rel    = ifcApi.GetLine(modelID, psetRels.get(i));
      const psetId = rel.RelatingPropertyDefinition ? rel.RelatingPropertyDefinition.value : null;
      if (!psetId) continue;
      const pset = ifcApi.GetLine(modelID, psetId);
      const psetName = pset.Name ? pset.Name.value : 'UnknownPset';

      // Collect props
      const props = pset.HasProperties || pset.Quantities || [];
      const extracted = [];
      for (const propRef of props) {
        try {
          const prop = ifcApi.GetLine(modelID, propRef.value);
          const pName = prop.Name ? prop.Name.value : null;
          if (!pName) continue;
          let pValue = null, pType = null;
          if (prop.NominalValue) {
            pValue = String(prop.NominalValue.value);
            pType  = prop.NominalValue.type || 'IfcLabel';
          } else if (prop.LengthValue !== undefined) {
            pValue = String(prop.LengthValue); pType = 'IfcLengthMeasure';
          } else if (prop.AreaValue !== undefined) {
            pValue = String(prop.AreaValue); pType = 'IfcAreaMeasure';
          } else if (prop.VolumeValue !== undefined) {
            pValue = String(prop.VolumeValue); pType = 'IfcVolumeMeasure';
          } else if (prop.CountValue !== undefined) {
            pValue = String(prop.CountValue); pType = 'IfcCountMeasure';
          }
          if (pValue !== null) extracted.push({ pset_name: psetName, prop_name: pName, prop_value: pValue, prop_type: pType });
        } catch(e) { /* skip bad prop */ }
      }
      if (extracted.length === 0) continue;

      // Map to elements
      const related = rel.RelatedObjects || [];
      for (const elRef of related) {
        const elId = elRef.value;
        if (!elementProps[elId]) elementProps[elId] = [];
        elementProps[elId].push(...extracted);
      }
    } catch(e) { /* skip bad rel */ }
  }
  console.log('[S240] §PSETS_DONE elementCount=' + Object.keys(elementProps).length);
} catch(e) {
  console.log('[S240] §PSETS_SKIP no property sets: ' + (e.message || e));
}
```

### §2.2 IfcSpace extraction

```javascript
// § EXTRACT_SPACES
const spaceList = []; // { guid, name, long_name, storey, area, volume }
try {
  const spaceIds = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCSPACE);
  for (let i = 0; i < spaceIds.size(); i++) {
    try {
      const sp = ifcApi.GetLine(modelID, spaceIds.get(i));
      const guid = sp.GlobalId ? sp.GlobalId.value : 'SPACE_' + i;
      spaceList.push({
        guid:      guid,
        name:      sp.Name ? sp.Name.value : 'Space',
        long_name: sp.LongName ? sp.LongName.value : '',
        storey:    elementToStorey[spaceIds.get(i)] || 'Unknown',
        area:      null,   // filled from BaseQuantities below
        volume:    null,
      });
    } catch(e) { /* skip */ }
  }
  console.log('[S240] §SPACES_DONE count=' + spaceList.length);
} catch(e) {
  console.log('[S240] §SPACES_SKIP: ' + (e.message || e));
}
```

### §2.3 4D Tasks

```javascript
// § EXTRACT_4D
const taskList = [];
const taskElementMap = {}; // task_guid → [element_guid]
const taskSequences  = [];
try {
  const taskIds = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCTASK);
  for (let i = 0; i < taskIds.size(); i++) {
    try {
      const task = ifcApi.GetLine(modelID, taskIds.get(i));
      const tguid = task.GlobalId ? task.GlobalId.value : 'TASK_' + i;
      let schedStart = null, schedFinish = null, duration = null, status = null;
      if (task.TaskTime) {
        try {
          const tt = ifcApi.GetLine(modelID, task.TaskTime.value);
          schedStart  = tt.ScheduleStart  ? tt.ScheduleStart.value  : null;
          schedFinish = tt.ScheduleFinish ? tt.ScheduleFinish.value : null;
          duration    = tt.ScheduleDuration ? tt.ScheduleDuration.value : null;
          status      = tt.IsMilestone && tt.IsMilestone.value ? 'MILESTONE' : null;
        } catch(e) { /* no task time */ }
      }
      status = task.Status ? task.Status.value : (status || 'NOTSTARTED');
      taskList.push({
        task_guid:       tguid,
        name:            task.Name ? task.Name.value : 'Task ' + i,
        description:     task.Description ? task.Description.value : '',
        status:          status,
        schedule_start:  schedStart,
        schedule_finish: schedFinish,
        duration:        duration,
      });
    } catch(e) { /* skip */ }
  }

  // Element assignments
  const assignIds = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCRELASSIGNSTASKS);
  for (let i = 0; i < assignIds.size(); i++) {
    try {
      const rel = ifcApi.GetLine(modelID, assignIds.get(i));
      const taskRef = rel.RelatingControl ? rel.RelatingControl.value : null;
      if (!taskRef) continue;
      const taskLine = ifcApi.GetLine(modelID, taskRef);
      const tguid = taskLine.GlobalId ? taskLine.GlobalId.value : null;
      if (!tguid) continue;
      if (!taskElementMap[tguid]) taskElementMap[tguid] = [];
      for (const elRef of (rel.RelatedObjects || [])) {
        const el = ifcApi.GetLine(modelID, elRef.value);
        const eguid = el.GlobalId ? el.GlobalId.value : null;
        if (eguid) taskElementMap[tguid].push(eguid);
      }
    } catch(e) { /* skip */ }
  }

  // Sequences (dependencies)
  const seqIds = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCRELSEQUENCE);
  for (let i = 0; i < seqIds.size(); i++) {
    try {
      const seq = ifcApi.GetLine(modelID, seqIds.get(i));
      const pred = ifcApi.GetLine(modelID, seq.RelatingProcess.value);
      const succ = ifcApi.GetLine(modelID, seq.RelatedProcess.value);
      taskSequences.push({
        pred_guid: pred.GlobalId ? pred.GlobalId.value : null,
        succ_guid: succ.GlobalId ? succ.GlobalId.value : null,
        seq_type:  seq.SequenceType ? seq.SequenceType.value : 'FINISH_START',
      });
    } catch(e) { /* skip */ }
  }

  console.log('[S240] §4D_DONE tasks=' + taskList.length + ' assignments=' + Object.values(taskElementMap).flat().length + ' sequences=' + taskSequences.length);
} catch(e) {
  console.log('[S240] §4D_SKIP no tasks: ' + (e.message || e));
}
```

### §2.4 5D Cost

```javascript
// § EXTRACT_5D
const costItems = [];
const costElementMap = {}; // cost_guid → [element_guid]
try {
  const costIds = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCCOSTITEM);
  for (let i = 0; i < costIds.size(); i++) {
    try {
      const ci = ifcApi.GetLine(modelID, costIds.get(i));
      const cguid = ci.GlobalId ? ci.GlobalId.value : 'COST_' + i;
      let totalCost = null, currency = null;
      if (ci.CostValues && ci.CostValues.length > 0) {
        try {
          const cv = ifcApi.GetLine(modelID, ci.CostValues[0].value);
          totalCost = cv.AppliedValue ? cv.AppliedValue.value : null;
          currency  = cv.UnitBasis   ? cv.UnitBasis.value    : null;
        } catch(e) { /* no value */ }
      }
      costItems.push({
        cost_guid:   cguid,
        name:        ci.Name ? ci.Name.value : 'CostItem ' + i,
        description: ci.Description ? ci.Description.value : '',
        total_cost:  totalCost,
        currency:    currency,
      });
    } catch(e) { /* skip */ }
  }

  const costRelIds = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCRELSCHEDULESCOSTITEMS);
  for (let i = 0; i < costRelIds.size(); i++) {
    try {
      const rel = ifcApi.GetLine(modelID, costRelIds.get(i));
      const ci  = ifcApi.GetLine(modelID, rel.RelatingControl.value);
      const cguid = ci.GlobalId ? ci.GlobalId.value : null;
      if (!cguid) continue;
      if (!costElementMap[cguid]) costElementMap[cguid] = [];
      for (const elRef of (rel.RelatedObjects || [])) {
        const el = ifcApi.GetLine(modelID, elRef.value);
        const eguid = el.GlobalId ? el.GlobalId.value : null;
        if (eguid) costElementMap[cguid].push(eguid);
      }
    } catch(e) { /* skip */ }
  }
  console.log('[S240] §5D_DONE costItems=' + costItems.length);
} catch(e) {
  console.log('[S240] §5D_SKIP no cost items: ' + (e.message || e));
}
```

---

## §3. Pass extracted data back to main thread

Add to the `result` object in import_worker.js:

```javascript
const result = {
  type: 'done',
  meta: { ... },      // existing
  elements: elements, // existing
  geometries: geometries, // existing
  transforms: transforms, // existing
  // NEW:
  elementProps: elementProps,   // expressID → [{pset_name, prop_name, prop_value, prop_type}]
  spaces:       spaceList,
  tasks:        taskList,
  taskElements: taskElementMap,
  taskSequences: taskSequences,
  costItems:    costItems,
  costElements: costElementMap,
};
```

---

## §4. DB write in main thread

In the sql.js DB builder (wherever `INSERT INTO elements` is called), add:

```javascript
// § DB_WRITE_PROPERTIES
if (result.elementProps) {
  for (const [expId, props] of Object.entries(result.elementProps)) {
    // Find guid for this expressID from elements array
    const el = result.elements.find(e => e.expressID === parseInt(expId));
    if (!el) continue;
    for (const p of props) {
      db.run('INSERT INTO element_properties VALUES (?,?,?,?,?)',
        [el.guid, p.pset_name, p.prop_name, p.prop_value, p.prop_type]);
    }
  }
}

// § DB_WRITE_SPACES
for (const sp of (result.spaces || [])) {
  db.run('INSERT INTO spaces VALUES (?,?,?,?,?,?)',
    [sp.guid, sp.name, sp.long_name, sp.storey, sp.area, sp.volume]);
}

// § DB_WRITE_TASKS
for (const t of (result.tasks || [])) {
  db.run('INSERT INTO tasks VALUES (?,?,?,?,?,?,?,?,?)',
    [t.task_guid, t.name, t.description, t.status,
     t.schedule_start, t.schedule_finish, null, null, t.duration]);
}
for (const [tguid, eguids] of Object.entries(result.taskElements || {})) {
  for (const eguid of eguids)
    db.run('INSERT INTO task_elements VALUES (?,?)', [tguid, eguid]);
}
for (const seq of (result.taskSequences || [])) {
  db.run('INSERT INTO task_sequences VALUES (?,?,?)',
    [seq.pred_guid, seq.succ_guid, seq.seq_type]);
}

// § DB_WRITE_COST
for (const ci of (result.costItems || [])) {
  db.run('INSERT INTO cost_items VALUES (?,?,?,?,?)',
    [ci.cost_guid, ci.name, ci.description, ci.total_cost, ci.currency]);
}
for (const [cguid, eguids] of Object.entries(result.costElements || {})) {
  for (const eguid of eguids)
    db.run('INSERT INTO cost_elements VALUES (?,?)', [cguid, eguid]);
}
```

---

## §5. UI — conditional panels

Only show 4D/5D panels if the DB has data:

```javascript
// § UI_SHOW_4D
const taskCount = db.exec('SELECT COUNT(*) FROM tasks')[0]?.values[0][0] || 0;
if (taskCount > 0) show4DPanel();  // Gantt timeline

const costCount = db.exec('SELECT COUNT(*) FROM cost_items')[0]?.values[0][0] || 0;
if (costCount > 0) show5DPanel();  // Cost breakdown

const spaceCount = db.exec('SELECT COUNT(*) FROM spaces')[0]?.values[0][0] || 0;
if (spaceCount > 0) showSpacesPanel(); // Room list
```

---

## §6. Verification

§-log lines to confirm extraction worked:

```
[S240] §PSETS_DONE elementCount=N
[S240] §SPACES_DONE count=N
[S240] §4D_DONE tasks=N assignments=N sequences=N
[S240] §5D_DONE costItems=N
[S240] §PSETS_SKIP no property sets (IFC has none — expected for many files)
```

Test IFC files known to have 4D/5D data:
- IFC files from Revit with schedules exported
- IFC4 files with IfcTask and IfcWorkSchedule
- BIM files from Navisworks export

For IFCs without this data all §_SKIP lines should appear — zero cost confirmed.

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
