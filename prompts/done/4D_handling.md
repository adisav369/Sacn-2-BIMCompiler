# ⚠ DO NOT REMOVE — Scope: 4D scheduling data extraction, DB storage, and IFC export. Read the log after every run.

# 4D Handling — Extract, Store, Export

## Status: SPEC

---

## Goal

1. Extract 4D scheduling data from IFC during import (if present)
2. Store in DB alongside geometry — accessible by viewer for timeline visualization
3. Allow writing 4D data BACK to IFC property sets in DB, exportable to IFC for other apps

---

## IFC 4D Entities

| IFC Entity | Purpose |
|---|---|
| IfcWorkPlan | Top-level schedule container |
| IfcWorkSchedule | Named schedule (baseline, revised, etc.) |
| IfcTask | Activity with start/finish dates, duration |
| IfcRelSequence | Predecessor/successor links between tasks |
| IfcRelAssignsToProcess | Links tasks → building elements |
| IfcTaskTime | Duration, start, finish, float |

---

## Phase 1: Extraction (read from IFC → DB)

### During import (web-ifc / Node.js):
```
For each IfcWorkSchedule:
  → store schedule metadata (name, status, creation date)

For each IfcTask:
  → store task_id, name, start_date, finish_date, duration, status
  → follow IfcRelSequence → store predecessor/successor links
  → follow IfcRelAssignsToProcess → store task ↔ element GUID mapping
```

### DB Schema (new tables in extracted DB):

```sql
CREATE TABLE IF NOT EXISTS schedules (
    schedule_id TEXT PRIMARY KEY,
    name TEXT,
    status TEXT,
    created_date TEXT
);

CREATE TABLE IF NOT EXISTS tasks (
    task_id TEXT PRIMARY KEY,
    schedule_id TEXT,
    name TEXT,
    start_date TEXT,
    finish_date TEXT,
    duration_days REAL,
    status TEXT,
    priority INTEGER,
    FOREIGN KEY (schedule_id) REFERENCES schedules(schedule_id)
);

CREATE TABLE IF NOT EXISTS task_sequences (
    predecessor_id TEXT,
    successor_id TEXT,
    sequence_type TEXT,
    lag_days REAL DEFAULT 0,
    PRIMARY KEY (predecessor_id, successor_id)
);

CREATE TABLE IF NOT EXISTS task_elements (
    task_id TEXT,
    guid TEXT,
    PRIMARY KEY (task_id, guid)
);
```

### Behaviour when IFC has no 4D data:
- Tables created but empty. Zero impact. Viewer skips timeline panel.

---

## Phase 2: Write-back (DB → IFC property sets → IFC export)

### Use case:
User creates/edits schedule in BIM OOTB viewer (or external tool writes to DB).
Data stored in DB. On export, written back as IFC entities.

### DB → IFC mapping:
| DB table | IFC entity written |
|---|---|
| `schedules` | IfcWorkSchedule |
| `tasks` | IfcTask + IfcTaskTime |
| `task_sequences` | IfcRelSequence |
| `task_elements` | IfcRelAssignsToProcess |

### Export flow:
```
1. Read tasks + sequences + element links from DB
2. Create IfcWorkSchedule container
3. For each task → create IfcTask + IfcTaskTime
4. For each sequence → create IfcRelSequence
5. For each task-element link → create IfcRelAssignsToProcess
6. Write to IFC file (web-ifc CreateModel + WriteLine)
```

### Round-trip guarantee:
- Import IFC with 4D → DB has schedule → Export → new IFC has same 4D data
- Create schedule in viewer → DB → Export → IFC has the new schedule
- Other apps (MS Project, Primavera, Asta) can read the exported IFC schedule

---

## Phase 3: Viewer integration (existing nD engine)

The existing nD engine (`scripts/nD_engine.py`, `docs/4D5DAnalysis.md`) already handles 4D visualization with template-driven timeline. The DB tables above feed directly into it:

```sql
-- Query for timeline: which elements appear at which date
SELECT t.start_date, t.finish_date, te.guid, t.name
FROM tasks t
JOIN task_elements te ON t.task_id = te.task_id
ORDER BY t.start_date
```

Viewer colors elements by construction phase, animates timeline scrubber.

---

## Anti-Drift

- Do NOT invent schedule data — only extract what the IFC contains
- Do NOT require 4D data for the viewer to function — graceful empty tables
- Do NOT modify geometry tables when handling 4D — separate concerns
- Round-trip: exported IFC must reproduce the same DB when re-imported

---

## Files

| File | Role |
|---|---|
| `deploy/dev/import_worker.js` | Extract 4D entities during browser Drop |
| `scripts/extractIFC2DB.js` | Node.js batch extractor (new — replaces Python) |
| `deploy/dev/import_db_builder.js` | Create 4D tables in DB |
| `deploy/dev/ifc_export_worker.js` | Write 4D back to IFC on export |
| `docs/4D5DAnalysis.md` | Existing nD analysis spec |
