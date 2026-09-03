# S166 — Federation Search: Show Friendly Metadata Instead of Raw GUIDs

## DO NOT REMOVE
Scope: Improve federation NLP search result display to show element_name + storey + element_type
alongside GUID. Read the log after every run.

**Target files:**
- `federation/ui.py` — search result display (lines ~1638-1659)
- `federation/query_patterns.py` — FTS5 query templates (lines ~334-355)

**Live path:** `/home/red1/.config/blender/5.0/extensions/.local/lib/python3.11/site-packages/bonsai/bim/module/federation/`

You are a coder. One bounded task.

---

## Problem

Search results show raw GUIDs and bare `ifc_class` (e.g., "IfcWall", "IfcWindow").
At 1M elements, many share the same IFC class. Users can't distinguish between them
without friendly metadata.

## What the DB already has

`elements_meta` columns (all populated):
```
guid, discipline, ifc_class, element_name, element_type, storey, material_name, material_rgba
```

`elements_fts` (FTS5 virtual table) indexes:
```
guid, element_name, element_type, element_description, ifc_class, discipline, storey
```

### Sample data showing WHY element_name matters

| ifc_class | element_name | storey |
|-----------|-------------|--------|
| IfcWindow | Fixed: 5'0" x 8'0" | Exist Garage - Ground Level |
| IfcWall | Basic Wall: Interior - 123mm Partition | Level 5 |
| IfcBeam | Beam Planed Timber: 45x95 | VAN 4 |
| IfcPipeFitting | Elbow Reducing - Threaded - MI - Class 150 | Level 3 |
| IfcWindow | UMP27C1 | VANING 1 |

Without `element_name`, searching "IfcWindow" returns 50 identical rows.
With it, user sees "Fixed: 5'0" x 8'0" on Ground Level" vs "UMP27C1 on VANING 1".

## What to change

### 1. Search result display in UI (ui.py ~line 1638-1659)

Current format per result row:
```
GUID: T0_LTU_AHouse_1OqE...  |  IfcWall
```

Target format:
```
Basic Wall: Interior - 123mm Partition        IfcWall  |  Level 5
  GUID: T0_LTU_AHouse_1OqE...
```

Lead with `element_name` (truncated to 45 chars if needed).
Show `ifc_class` and `storey` on the same line.
GUID on a secondary line (smaller or dimmed if possible).

### 2. Query patterns (query_patterns.py)

Ensure all FTS5 queries SELECT `element_name, element_type, storey` alongside `guid, ifc_class`.
The existing FREETEXT_PATTERNS already do this — verify, don't break.

### 3. Result property items (prop.py)

If search results are stored as Blender CollectionProperty items, the item type
may need `element_name` and `storey` string properties added. Check how
`BIM_OT_execute_nlp_query` stores results and whether the UI reads from
properties or directly from query results.

## What NOT to change

- DO NOT touch blend_cache.py or any GN instancing code
- DO NOT modify the FTS5 table schema
- DO NOT change the query logic — only the DISPLAY of results
- DO NOT add new tables or columns to the database

## Impact assessment

- **Read-only change** — only affects how results render in the UI panel
- No database schema changes
- No operator logic changes
- No effect on GN instancing, R-tree preview, or cache loading
- Safe to deploy independently

## Test plan

1. Load any federation DB (SH or sandbox_1M)
2. Open NLP Query panel
3. Type "search for wall" — verify results show element_name + storey
4. Type "find IfcWindow" — verify each window is distinguishable
5. Click a result — verify camera fly-to still works
6. Export to CSV — verify new columns appear
