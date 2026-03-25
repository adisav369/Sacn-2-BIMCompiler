# BIM Back Office — User Guide
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

**Version:** 2.0 (2026-03-20, session 39d)
**Module:** `BIMBackOffice` (`com.bim.backoffice`)

---

## What Is the Back Office?

The BIM Back Office is the **multi-user, multi-project** ERP server for
managing construction projects. While each designer works in Blender on one
building at a time, the Back Office sees everything: all projects, all users,
all reports — simultaneously.

| | Bonsai (BIM Designer) | Back Office Server |
|---|---|---|
| **Users** | Single user per Blender instance | Multiple concurrent users |
| **Scope** | One project at a time | All projects simultaneously |
| **Protocol** | TCP 9876 (ndjson) | HTTP 9877 (JSON REST) |
| **View** | 3D viewport + design tools | Dashboard + reports + admin |
| **Access** | Blender addon | Browser / curl / any HTTP client |

---

## 1. Starting the Server

### Quick Start

```bash
cd /home/red1/bim-compiler

# Option 1: Run directly via Maven
mvn exec:java -pl BIMBackOffice \
  -Dexec.mainClass="com.bim.backoffice.server.BackOfficeServer" \
  -Dexec.args="library 9877"

# Option 2: From the command line (after build)
java -cp BIMBackOffice/target/classes:... \
  com.bim.backoffice.server.BackOfficeServer library 9877
```

The server starts on **port 9877** and prints available endpoints:

```
BackOfficeServer running on http://localhost:9877
Endpoints:
  GET /api/portfolio
  GET /api/kanban
  GET /api/bsc
  GET /api/cost?id=SH
  GET /api/schedule?id=SH&start=2026-04-01
  GET /api/carbon?id=SH
  GET /api/maintenance?id=SH
  GET /api/health
Press Ctrl+C to stop.
```

### Verify It Works

```bash
curl http://localhost:9877/api/health
```

Expected:
```json
{
  "status": "UP",
  "service": "BIMBackOffice",
  "port": 9877,
  "libraryDir": "library"
}
```

### Two Servers, Two Protocols

| Server | Port | Protocol | Purpose |
|--------|------|----------|---------|
| DesignerServer | 9876 | TCP ndjson | Blender addon (single-user design) |
| BackOfficeServer | 9877 | HTTP JSON | Multi-user dashboard/reports |

Both run simultaneously. The Bonsai client connects to port 9876 for design.
The web dashboard / CLI connects to port 9877 for reports and portfolio.

---

## 2. User Sessions

The Back Office tracks who's online and what they're editing.

### Log In

```bash
curl -X POST http://localhost:9877/api/login \
  -d '{"userId": "alice", "displayName": "Alice Tan"}'
```

Response:
```json
{
  "token": "a1b2c3d4-...",
  "userId": "alice",
  "displayName": "Alice Tan"
}
```

Save the `token` — include it as `X-Session-Token` header in subsequent requests.
Sessions expire after **30 minutes** of inactivity.

### Who's Online

```bash
curl http://localhost:9877/api/sessions
```

Response:
```json
{
  "activeSessions": [
    {"userId": "alice", "displayName": "Alice Tan", "activeBuilding": "SH", "lastAccess": "2026-03-20T14:32:00Z"},
    {"userId": "bob",   "displayName": "Bob Lee",   "activeBuilding": "TE", "lastAccess": "2026-03-20T14:30:00Z"}
  ],
  "count": 2
}
```

### Conflict Detection

When two users edit the same building, the server detects the conflict.
The `whoIsEditing` check runs automatically — if Alice and Bob both set
their active building to "SH", each sees the other as a concurrent editor.
The audit trail (ChangelogDAO) records per-user changes for conflict resolution.

### Write Serialization (SQLite Safety)

SQLite allows many readers but only one writer per database file. The
SessionManager provides per-database write locks:

- Reads are uncontended — each request gets its own connection
- Writes are serialized per database file via `ReentrantLock`
- WAL mode enabled for maximum read concurrency
- `busy_timeout=5000ms` prevents immediate write failures

---

## 3. Portfolio Dashboard

### All Projects at a Glance

```bash
curl http://localhost:9877/api/portfolio
```

Returns every building in the `library/` directory with aggregated metrics:

```json
{
  "totalProjects": 8,
  "totalCostRm": 15420000.00,
  "totalCarbonKg": 482000.00,
  "projects": [
    {"name": "Sample House",     "prefix": "SH", "elements": 55,    "costRm": 245000},
    {"name": "FZK Haus",         "prefix": "FK", "elements": 82,    "costRm": 312000},
    {"name": "AC11 Institute",   "prefix": "IN", "elements": 699,   "costRm": 1890000},
    {"name": "Duplex",           "prefix": "DX", "elements": 1099,  "costRm": 2340000},
    {"name": "Airport Terminal",  "prefix": "TE", "elements": 48428, "costRm": 9200000}
  ]
}
```

### Kanban Board

```bash
curl http://localhost:9877/api/kanban
```

Projects organized by construction status:

| Column | DocStatus | Meaning |
|--------|-----------|---------|
| **Backlog** | Draft (DR) | Not yet started |
| **In Progress** | In Progress (IP) | Under construction |
| **Review** | Complete (CO) | Awaiting approval |
| **Complete** | Approved (AP) | Handed over |

### Balanced Scorecard

```bash
curl http://localhost:9877/api/bsc
```

Four perspectives with 3+ metrics each:

| Perspective | Example Metrics |
|-------------|----------------|
| **Financial** | Total cost, cost per m², budget variance |
| **Client** | On-time delivery %, defect rate, satisfaction |
| **Process** | Compilation time, rule compliance %, element reuse |
| **Learning** | Product catalog growth, new jurisdictions, automation % |

---

## 4. Reports (4D–7D)

### 4D Schedule — Construction Sequence

```bash
curl "http://localhost:9877/api/schedule?id=SH&start=2026-04-01"
```

Returns Gantt tasks ordered by CIDB construction phase:

```json
{
  "buildingId": "SH",
  "projectStartDate": "2026-04-01",
  "projectFinishDate": "2026-07-15",
  "totalTasks": 12,
  "totalDays": 105,
  "tasks": [
    {"phase": "SUBSTRUCTURE", "name": "Foundation slab", "startDay": 1, "durationDays": 14},
    {"phase": "SUPERSTRUCTURE", "name": "External walls", "startDay": 15, "durationDays": 21},
    ...
  ]
}
```

### 5D Cost — BOM Cost Breakdown

```bash
curl "http://localhost:9877/api/cost?id=SH"
```

Three-component cost: material + labour + equipment per discipline:

```json
{
  "buildingId": "SH",
  "grandTotal": 245000.00,
  "materialTotal": 147000.00,
  "laborTotal": 73500.00,
  "equipmentTotal": 24500.00,
  "lines": [
    {"discipline": "STR", "product": "Concrete C30", "qty": 12, "material": 4800, "labor": 2400, "equipment": 800},
    {"discipline": "ARC", "product": "Brick Wall 200mm", "qty": 45, "material": 9000, "labor": 6750, "equipment": 0},
    ...
  ]
}
```

### 6D Carbon — Embodied Carbon Footprint

```bash
curl "http://localhost:9877/api/carbon?id=SH"
```

```json
{
  "buildingId": "SH",
  "totalCarbonKg": 48200.0,
  "carbonIntensity": 285.0,
  "unit": "kgCO2e/m²",
  "topContributors": [
    {"material": "Concrete", "carbonKg": 28920, "percentage": 60.0},
    {"material": "Steel", "carbonKg": 9640, "percentage": 20.0}
  ]
}
```

### 7D Maintenance — Facility Management

```bash
curl "http://localhost:9877/api/maintenance?id=SH"
```

Asset register with replacement intervals for handover:

```json
{
  "buildingId": "SH",
  "totalAssets": 55,
  "nextMaintenanceDate": "2027-04-01",
  "items": [
    {"asset": "Fire Sprinkler Head", "qty": 12, "intervalMonths": 12, "lifespanYears": 15},
    {"asset": "HVAC Unit", "qty": 2, "intervalMonths": 6, "lifespanYears": 20}
  ]
}
```

---

## 5. Print Configurator

### What It Does

When you compile a building, the output database contains many tables.
The Print Configurator lets you choose which tables to include in a
print/export — like a report template.

### Table Categories

| Category | Tables | Default |
|----------|--------|---------|
| SUMMARY | building_summary | Yes |
| SPATIAL | elements_meta, element_instances, spatial_structure | elements_meta only |
| QUANTITY | simple_qto, area_by_storey, area_by_type, room_areas | simple_qto, area_by_storey |
| ORDER | c_order, c_orderline, PP_Order_Node | c_order, c_orderline |
| GEOMETRY | base_geometries, surface_styles, material_layers | No |

### Workflow

1. **Discover** — scan output DB for available tables
2. **Tick** — check which tables to include (defaults pre-ticked)
3. **Save** — name your format ("Cost Report", "Full Technical", "Client Summary")
4. **Apply** — select format when generating reports

Multiple formats per building. Accessible from Bonsai via the bridge
(single-project scope) or from the Back Office (multi-project scope).

---

## 6. Audit Trail

Every design change is logged with full provenance:

```
Timestamp            User     Action    Entity         Field      Old → New
────────────────────────────────────────────────────────────────────────────
2026-03-20 14:32     alice    MOVE      ROOM_LI_01     dx         0 → 500
2026-03-20 14:33     alice    RESIZE    ROOM_KT_01     maxX       4000 → 4500
2026-03-20 14:35     bob      ADD       ROOM_BD_02     —          — → created
```

Supports:
- **Undo**: Revert the last N changes
- **Conflict detection**: Multiple users see each other's edits in real time
- **Compliance**: Full provenance for regulatory submissions (who changed what, when)

---

## 7. Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Clients                                        │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ Bonsai       │  │ Web Dashboard│  │ CLI / curl / scripts   │ │
│  │ (Blender)    │  │ (browser)    │  │ (automation)           │ │
│  │ TCP 9876     │  │ HTTP 9877    │  │ HTTP 9877              │ │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬─────────────┘ │
└─────────┼─────────────────┼──────────────────────┼──────────────┘
          │                 │                      │
┌─────────▼─────────┐  ┌───▼──────────────────────▼──────────────┐
│ DesignerServer    │  │ BackOfficeServer (HTTP)                  │
│ (TCP, single-user)│  │ SessionManager (multi-user, write locks) │
│ port 9876         │  │ port 9877                                │
└─────────┬─────────┘  └───┬──────────────────────────────────────┘
          │                │
          └────────┬───────┘
                   │
┌──────────────────▼──────────────────────────────────────────────┐
│  BIMBackOffice (shared logic layer)                              │
│                                                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐         │
│  │ PrintConf│ │ ReportDAO│ │Portfolio │ │ ChangelogDAO│         │
│  │igurator  │ │ 4D-7D    │ │DAO      │ │ audit trail │         │
│  └──────────┘ └──────────┘ └──────────┘ └────────────┘         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐         │
│  │ CostDAO  │ │ Schedule │ │ Carbon   │ │ FacilityMgt│         │
│  │ 5D       │ │ DAO 4D   │ │ DAO 6D   │ │ DAO 7D     │         │
│  └──────────┘ └──────────┘ └──────────┘ └────────────┘         │
└─────────────────────────────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────────┐
│  Databases (shared by both servers)                              │
│                                                                  │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────────┐ │
│  │ component_   │ │ {PREFIX}_    │ │ ERP.db       │ │
│  │ library.db   │ │ BOM.db ×N   │ │ (63 rules)               │ │
│  │ (800 prods)  │ │ (per bldg)  │ │                          │ │
│  └──────────────┘ └──────────────┘ └──────────────────────────┘ │
│  ┌──────────────┐ ┌──────────────┐                              │
│  │ output.db   │                                                │
│  │ (compiled)  │                                                │
│  └──────────────┘                                               │
└─────────────────────────────────────────────────────────────────┘
```

---

## 8. API Reference

### Authentication

| Endpoint | Method | Body | Returns |
|----------|--------|------|---------|
| `/api/login` | POST | `{"userId":"alice","displayName":"Alice Tan"}` | `{token, userId, displayName}` |
| `/api/sessions` | GET | — | `{activeSessions[], count}` |

### Portfolio (multi-project)

| Endpoint | Method | Returns |
|----------|--------|---------|
| `/api/portfolio` | GET | All projects with cost/carbon/element counts |
| `/api/kanban` | GET | Projects as Kanban cards (Backlog/IP/Review/Complete) |
| `/api/bsc` | GET | Balanced scorecard (4 perspectives × metrics) |

### Per-Project Reports

| Endpoint | Method | Params | Returns |
|----------|--------|--------|---------|
| `/api/cost?id=SH` | GET | `id` (building prefix) | 5D cost breakdown (3-component) |
| `/api/schedule?id=SH&start=2026-04-01` | GET | `id`, `start` (optional) | 4D Gantt schedule |
| `/api/carbon?id=SH` | GET | `id` | 6D carbon footprint |
| `/api/maintenance?id=SH` | GET | `id` | 7D maintenance schedule |

### System

| Endpoint | Method | Returns |
|----------|--------|---------|
| `/api/health` | GET | `{status:"UP", service, port, libraryDir}` |

**Building ID prefixes:** SH (Sample House), FK (FZK Haus), IN (AC11 Institute),
DX (Duplex), TE (Terminal), DM (DemoHouse), BR (Bridge), RD (Road), RL (Rail).

---

## 9. Database Split

| Database | Back Office concern | Bonsai concern |
|----------|-------------------|----------------|
| `{PREFIX}_BOM.db` | Portfolio scan, report source, cost/carbon rollup | Active project BOM |
| `component_library.db` | Product costs, carbon per unit, lifespan data | Product browsing |
| `output.db` | Compiled geometry, placement results | Compile output |
| `ERP.db` | Compliance matrix, rule queries | Placement validation |

---

## 10. Test Coverage

| Test class | Tests | What |
|------------|-------|------|
| `PrintConfigTest` | 5 | Discover, defaults, save/load, list, update |
| `BackOfficeServerTest` | 10 | Health, portfolio, kanban, BSC, cost, schedule, login, sessions, conflicts, write locks |
| **Total** | **15** | **15/15 GREEN** |

Run: `mvn test -pl BIMBackOffice`

---

*Related docs:
[BACK_OFFICE_SRS.md](BACK_OFFICE_SRS.md) (SRS) |
[BIM_Designer_UserGuide.md](BIM_Designer_UserGuide.md) (Bonsai client guide) |
[INSTALLER_SPEC.md](INSTALLER_SPEC.md) (installer packaging) |
[CORE_SRS.md](CORE_SRS.md) (report engine spec) |
[MANIFESTO.md](MANIFESTO.md) (iDempiere mapping)*
