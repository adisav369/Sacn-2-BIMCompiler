# BIM Back Office — Software Requirements Specification
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [ConstructionAsERP](ConstructionAsERP.md) · [TestArchitecture](TestArchitecture.md)

**Version:** 1.0
**Date:** 2026-03-20
**Module:** `BIMBackOffice` (`com.bim.backoffice`)

---

## 0. Architecture

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  Bonsai (Blender) │────▶│  BonsaiBIM       │────▶│  BIMBackOffice   │
│  Python addon     │     │  Designer        │     │  Java ERP layer  │
│  single client    │     │  bridge + UI     │     │  multi-user/proj │
└──────────────────┘     └──────────────────┘     └──────────────────┘
                                                          │
                          ┌───────────────────────────────┼───────────────┐
                          ▼                               ▼               ▼
                   {PREFIX}_BOM.db              component_library.db   work_output.db
                   (per-building BOM)           (product catalog)      (design state)
```

**BIMBackOffice** = ERP back-end logic layer (iDempiere pattern).
- **Multi-user, multi-project** server view (vs Bonsai's single-user client)
- Report engine (4D-7D), print configurator, portfolio analysis
- Called from Bonsai via BlenderBridge (single-project scope)
- Called from back-office UI / web dashboard (multi-project scope)
- Called from CI/CD / batch scripts (automation scope)

**BonsaiBIMDesigner** = Blender addon bridge (single-client view).
- Depends on BIMBackOffice for report/print logic
- Owns: DesignerDAO, WorkOutputDAO, PlacementValidator, BlenderBridge
- Does NOT own: report generation, print config, portfolio rollup

---

## 1. Print Configurator (AD_PrintFormat)

### 1.1 Purpose

Users select which output database tables/views to include when generating
a print/export from a compiled building. This is the iDempiere AD_PrintFormat
+ AD_PrintFormatItem pattern adapted for BIM output databases.

### 1.2 Schema

```sql
AD_PrintFormat (format_id PK, building_id, format_name, is_default, created, updated)
AD_PrintFormatItem (format_id FK, table_name, sequence, is_active, display_name)
```

### 1.3 Operations

| Operation | Method | Input | Output |
|-----------|--------|-------|--------|
| Discover | `PrintConfig.discoverOutputTables(outputConn)` | Compiled output DB | List<PrintItem> categorized |
| Save | `PrintConfig.saveFormat(configConn, format)` | Format + selected tables | Persisted |
| Load | `PrintConfig.loadFormat(configConn, formatId)` | Format ID | PrintFormat |
| List | `PrintConfig.listFormats(configConn, buildingId)` | Building ID | All formats |

### 1.4 Table Categories

| Category | Tables | Default included |
|----------|--------|-----------------|
| SUMMARY | building_summary | Yes |
| SPATIAL | elements_meta, element_instances, spatial_structure | elements_meta only |
| QUANTITY | simple_qto, area_by_storey, area_by_type, room_areas | simple_qto, area_by_storey |
| ORDER | c_order, c_orderline, PP_Order_Node | c_order, c_orderline |
| GEOMETRY | base_geometries, surface_styles, material_layers | No |
| OTHER | remaining tables | No |

### 1.5 Witnesses

| Witness | What | Status |
|---------|------|--------|
| W-PRINT-DISCOVER | discoverOutputTables returns categorized items, filters rtree | PASS |
| W-PRINT-DEFAULTS | Default items = summary + quantities + order | PASS |
| W-PRINT-SAVE-LOAD | Save and reload a print format | PASS |
| W-PRINT-LIST | List formats per building | PASS |
| W-PRINT-UPDATE | Update replaces items (not appends) | PASS |

---

## 2. Report Engine (4D–7D)

### 2.1 Migrated DAOs

These DAOs moved from `com.bim.designer.dao` → `com.bim.backoffice.dao`:

| DAO | Concern | iDempiere parallel |
|-----|---------|-------------------|
| ReportDAO | Interface: 8 methods, 11 record types | AD_Process parameter signatures |
| CostDAO | 5D: BOM cost rollup (material + labor + equipment) | M_CostDetail |
| ScheduleDAO | 4D: Construction sequence as Gantt tasks | PP_Order workflow |
| SustainabilityDAO | 6D: Embodied carbon per product × qty | M_Product extended |
| FacilityMgmtDAO | 7D: Asset register + maintenance scheduling | COBie handover |
| PortfolioDAO | Cross-project: portfolio, Kanban, balanced scorecard | Multi-org rollup |
| ChangelogDAO | Audit trail: change history with undo | AD_ChangeLog |

### 2.2 Shared Model

`com.bim.backoffice.model.DesignBBox` — domain record for BOM spatial elements.
Used by both back-office (reports) and designer (Blender viewport).

---

## 3. Multi-Project View (Portfolio)

The back-office provides portfolio-level analysis that the single-client
Bonsai addon cannot: scanning all `{PREFIX}_BOM.db` files, aggregating
cost/carbon/schedule across projects.

| View | Method | Output |
|------|--------|--------|
| All projects | `PortfolioDAO.portfolio()` | ProjectRow[] |
| Kanban board | `PortfolioDAO.kanban()` | KanbanCard[] (by DocStatus) |
| Balanced scorecard | `PortfolioDAO.balancedScorecard()` | 4 perspectives × metrics |

---

## 4. HTTP Server (BackOfficeServer)

### 4.1 Implementation — DONE (session 39)

JDK `com.sun.net.httpserver` — no framework dependency. Port **9877** (configurable).
4-thread pool. JSON responses via Gson. CORS enabled (`Access-Control-Allow-Origin: *`).

### 4.2 Endpoints

| Endpoint | Method | Auth | Returns |
|----------|--------|------|---------|
| `/api/login` | POST | — | Session token |
| `/api/sessions` | GET | — | Active users list |
| `/api/portfolio` | GET | — | All projects with cost/carbon |
| `/api/kanban` | GET | — | Kanban cards by DocStatus |
| `/api/bsc` | GET | — | Balanced scorecard (4 perspectives) |
| `/api/cost?id=XX` | GET | — | 5D cost breakdown (3-component) |
| `/api/schedule?id=XX` | GET | — | 4D Gantt schedule |
| `/api/carbon?id=XX` | GET | — | 6D carbon footprint |
| `/api/maintenance?id=XX` | GET | — | 7D maintenance schedule |
| `/api/health` | GET | — | Server status |

### 4.3 Witnesses

| Witness | What | Status |
|---------|------|--------|
| W-BO-SERVER-1 | /api/health returns UP | PASS |
| W-BO-SERVER-2 | /api/portfolio returns projects with cost | PASS |
| W-BO-SERVER-3 | /api/kanban returns status-grouped cards | PASS |
| W-BO-SERVER-4 | /api/bsc returns 4 perspectives | PASS |
| W-BO-SERVER-5 | /api/cost?id=SH returns 3-component cost | PASS |
| W-BO-SERVER-6 | /api/schedule?id=SH returns Gantt tasks | PASS |

---

## 5. Session Management (SessionManager)

### 5.1 Implementation — DONE (session 39)

Token-based sessions with 30-minute timeout. ConcurrentHashMap for thread safety.
Per-database write serialization via ReentrantLock. WAL mode for read concurrency.

### 5.2 Features

| Feature | How |
|---------|-----|
| Login | POST /api/login → UUID token |
| Who's online | GET /api/sessions → active users list |
| Active building | `setActiveBuilding(token, id)` for conflict tracking |
| Conflict detection | `whoIsEditing(buildingId, excludeToken)` → other editors |
| Write locks | `getWriteLock(dbFileName)` → ReentrantLock per DB file |
| Read connections | `openReadConnection()` with `PRAGMA query_only=1` |
| Write connections | `getWriteConnection()` with WAL mode + busy_timeout |

### 5.3 Witnesses

| Witness | What | Status |
|---------|------|--------|
| W-BO-SESSION-1 | POST /api/login creates session with token | PASS |
| W-BO-SESSION-2 | /api/sessions shows active users | PASS |
| W-BO-SESSION-3 | Detects concurrent editing of same building | PASS |
| W-BO-SESSION-4 | Per-DB write lock: same DB = same lock, different DB = different lock | PASS |

---

## 6. Future Phases

| Phase | Feature | iDempiere parallel | Status |
|-------|---------|-------------------|--------|
| BO-1 | Print configurator (AD_PrintFormat) | AD_PrintFormat + AD_PrintFormatItem | **DONE** |
| BO-2 | HTTP server + session management | AD_Session + AD_Service | **DONE** |
| BO-2b | WAN deployment: HMAC token signing, TLS proxy, Docker | AD_Session security | **DONE** |
| BO-3 | AD_Process: report execution queue | AD_Process + AD_PInstance | planned |
| BO-4 | PDF/CSV export engine | AD_Archive | planned |
| BO-5 | Role-based access (viewer/editor/admin) | AD_Role + AD_User | planned |
| BO-6 | Web dashboard UI (HTML/JS) | — | planned |
| BO-7 | Installer packaging (fat JAR + bundled JRE) | — | spec written |

---

## 5a. WAN Deployment (BO-2b) — DONE

### 5a.1 Token Security

Session tokens are HMAC-SHA256 signed. Format: `{sessionId}.{signature}`.

- Signing key: `BIM_SESSION_SECRET` env var, or auto-generated per JVM
- Verification: constant-time comparison (prevents timing attacks)
- Backward-compatible: unsigned UUID tokens accepted for local/test use

### 5a.2 TLS Termination

nginx reverse proxy handles HTTPS. Java server stays plain HTTP internally.

```
Internet → nginx:443 (TLS) → BackOfficeServer:9877 (HTTP)
```

### 5a.3 Docker Deployment

```bash
./deploy/generate-certs.sh   # self-signed (or use Let's Encrypt)
docker-compose up -d          # starts backoffice + nginx
```

Files: `docker-compose.yml`, `Dockerfile`, `deploy/nginx.conf`, `deploy/generate-certs.sh`

Full guide: `docs/DEPLOYMENT.md`

### 5a.4 Witnesses

| Witness | What | Status |
|---------|------|--------|
| W-BO-WAN-1 | HMAC-signed token accepted by getSession() | PASS |
| W-BO-WAN-2 | Forged token (wrong signature) rejected | PASS |
| W-BO-WAN-3 | Unsigned UUID token accepted (backward compat) | PASS |
| W-BO-WAN-4 | CORS preflight (OPTIONS) returns 204 with headers | PASS |

---

## 5. Dependency Graph

```
BIMBackOffice
  ├── orm-core (BIMLogger, base ORM)
  ├── sqlite-jdbc
  └── gson

BonsaiBIMDesigner
  ├── BIMBackOffice  ← NEW dependency
  ├── dag-compiler
  ├── bim-cobol
  ├── orm-sandbox
  ├── sqlite-jdbc
  └── gson
```
