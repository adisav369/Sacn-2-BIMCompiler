# S246 — Rule-Based Code Compliance Checker

## Context
Same architecture as S245b clash detection: JSON rules → DB queries → visual indicators → Excel report.
Reuses matrix UI, glass panels, sphere indicators, draggable panels.
Requires R-tree from S245c for spatial rules (distance, clearance).

## Architecture

```
compliance_rules.json → SQL queries → sphere matrix → detail list → Excel report
```

Same pattern as clash detection but rules check **code requirements** not **element overlaps**.

## compliance_rules.json

```json
{
  "rule_categories": [
    {
      "name": "Accessibility",
      "rules": [
        {
          "id": "ACC-01",
          "name": "Door width ≥ 900mm",
          "query": "SELECT guid, element_name, bbox_x, bbox_y FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid WHERE m.ifc_class IN ('IfcDoor') AND MIN(t.bbox_x, t.bbox_y) < 0.9",
          "severity": "hard",
          "standard": "IBC 1010.1.1 / MS 1184"
        },
        {
          "id": "ACC-02",
          "name": "Corridor width ≥ 1200mm",
          "query": "SELECT guid, element_name, bbox_x, bbox_y FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid WHERE m.ifc_class = 'IfcSpace' AND m.element_name LIKE '%corridor%' AND MIN(t.bbox_x, t.bbox_y) < 1.2",
          "severity": "hard",
          "standard": "IBC 1020.2 / UBBL 1984"
        }
      ]
    },
    {
      "name": "Fire Safety",
      "rules": [
        {
          "id": "FIRE-01",
          "name": "Every storey has ≥ 2 exits",
          "query": "SELECT storey, COUNT(*) AS exits FROM elements_meta WHERE ifc_class = 'IfcDoor' AND element_name LIKE '%exit%' GROUP BY storey HAVING exits < 2",
          "severity": "hard",
          "standard": "IBC 1006.2"
        },
        {
          "id": "FIRE-02",
          "name": "Max travel distance to exit ≤ 30m",
          "type": "spatial",
          "note": "Requires R-tree. Query: find IfcSpaces where nearest IfcDoor(exit) > 30m",
          "severity": "hard",
          "standard": "IBC 1017.1 / UBBL 166"
        }
      ]
    },
    {
      "name": "Spatial Requirements",
      "rules": [
        {
          "id": "SPACE-01",
          "name": "Ceiling height ≥ 2700mm",
          "query": "SELECT guid, element_name, storey, bbox_z FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid WHERE m.ifc_class = 'IfcSpace' AND t.bbox_z < 2.7",
          "severity": "soft",
          "standard": "IBC 1003.2 / UBBL 41"
        },
        {
          "id": "SPACE-02",
          "name": "Room has at least one window",
          "query": "SELECT m.guid, m.element_name, m.storey FROM elements_meta m WHERE m.ifc_class = 'IfcSpace' AND m.guid NOT IN (SELECT DISTINCT m2.guid FROM elements_meta m2 JOIN elements_meta w ON w.storey = m2.storey AND w.ifc_class = 'IfcWindow' WHERE m2.ifc_class = 'IfcSpace')",
          "severity": "soft",
          "standard": "IBC 1205.2"
        }
      ]
    },
    {
      "name": "Structural",
      "rules": [
        {
          "id": "STR-01",
          "name": "Every storey has columns or walls",
          "query": "SELECT storey FROM (SELECT DISTINCT storey FROM elements_meta) s WHERE storey NOT IN (SELECT DISTINCT storey FROM elements_meta WHERE ifc_class IN ('IfcColumn','IfcWall','IfcWallStandardCase'))",
          "severity": "hard",
          "standard": "Basic structural integrity"
        }
      ]
    },
    {
      "name": "MEP Coordination",
      "rules": [
        {
          "id": "MEP-01",
          "name": "Every storey with plumbing has floor drains",
          "query": "SELECT storey FROM elements_meta WHERE discipline = 'PLB' AND storey NOT IN (SELECT storey FROM elements_meta WHERE ifc_class = 'IfcFlowTerminal' AND element_name LIKE '%drain%') GROUP BY storey",
          "severity": "soft",
          "standard": "IPC 412.1"
        }
      ]
    }
  ],
  "standards": {
    "IBC": "International Building Code 2021",
    "UBBL": "Uniform Building By-Laws 1984 (Malaysia)",
    "MS 1184": "Malaysian Standard — Accessibility",
    "IPC": "International Plumbing Code"
  }
}
```

## UI Flow

### Entry point
New button or sub-tab within Measure tool: ✓ (checkmark icon).
Or: add to the info card below CLASHES — `COMPLIANCE ●` sphere.

### Compliance Matrix
Rows = rule categories (Accessibility, Fire Safety, Spatial, Structural, MEP).
Columns = storeys.
Cells = spheres (green = pass, red = fail, orange = warning).

Same glass panel, draggable, close X. Click a cell → detail list of violations.

### Detail list
Same pattern as clash list:
- Brief rows: rule ID, element name, value vs required
- Click row → fly to element + highlight
- Status toggle: Open → Acknowledged → Fixed → Waived

### Excel export
Sheet 1: Compliance Report (violations + assignment template)
Sheet 2: Rules Applied (which rules, standards, thresholds)

## Implementation

1. `compliance_rules.json` — rules file (user-editable, project-specific)
2. `measure.js` — add compliance checker (same section as clash)
3. Rules are pure SQL — executed via `A.dbQuery()`, no new engine needed
4. Spatial rules (distance) use R-tree from S245c
5. Reuse: glass panels, spheres, draggable, Excel export, status toggle

## What this beats

| Feature | Solibri | **BIM OOTB** |
|---------|---------|-------------|
| Cost | $3,400/yr | Free |
| Install | Desktop | Browser |
| Custom rules | GUI editor | JSON file |
| Results | Report | In-viewer + Excel |
| Mobile | No | Yes |
| Standards | Pre-loaded | User-configurable |
| Combined with clash | Separate | Same tool |

## Dependencies
- S245c R-tree (for spatial rules like travel distance)
- Existing: elements_meta, element_transforms, dbQuery, glass UI, Excel export
