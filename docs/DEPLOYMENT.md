# BIM BackOffice — Deployment Guide
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>Build, run, deploy — local, LAN, or Docker.</b> Quick start commands and WAN deployment procedures for the BackOffice server.
</div>

## Quick Start (Local / LAN)

```bash
# 1. Build
mvn package -q -DskipTests

# 2. Run
java -cp "BIMBackOffice/target/bim-backoffice-1.0-SNAPSHOT.jar:BIMBackOffice/target/dependency/*" \
     com.bim.backoffice.server.BackOfficeServer library 9877

# 3. Test
curl http://localhost:9877/api/health
curl http://localhost:9877/api/portfolio
```

## WAN Deployment (Docker)

### Prerequisites
- Docker + Docker Compose
- A server with ports 80/443 open

### Steps

```bash
# 1. Generate TLS certificates (self-signed for testing)
./deploy/generate-certs.sh

# 2. Set a session signing secret (optional — auto-generated if omitted)
export BIM_SESSION_SECRET="your-secret-here"

# 3. Launch
docker-compose up -d

# 4. Verify
curl -k https://your-server/api/health
```

### Production TLS (Let's Encrypt)

```bash
# On the server:
sudo certbot certonly --standalone -d your-domain.com

# Copy certs:
cp /etc/letsencrypt/live/your-domain.com/fullchain.pem deploy/certs/server.crt
cp /etc/letsencrypt/live/your-domain.com/privkey.pem deploy/certs/server.key

# Restart nginx:
docker-compose restart nginx
```

## Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `BIM_SESSION_SECRET` | (random per JVM) | HMAC-SHA256 key for session token signing |
| `BIM_LIBRARY_DIR` | `./library` | Path to directory containing `*_BOM.db` and `component_library.db` |

## Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/health` | GET | Server status |
| `/api/login` | POST | Create session (`{"userId":"...", "displayName":"..."}`) |
| `/api/sessions` | GET | Who's online |
| `/api/portfolio` | GET | All projects overview |
| `/api/kanban` | GET | Kanban board by DocStatus |
| `/api/bsc` | GET | Balanced scorecard |
| `/api/cost?id=SH` | GET | 5D cost breakdown |
| `/api/schedule?id=SH` | GET | 4D construction schedule |
| `/api/carbon?id=SH` | GET | 6D carbon footprint |
| `/api/maintenance?id=SH` | GET | 7D maintenance schedule |

All endpoints return JSON. Session token via `X-Session-Token` header.

## Architecture

```
Internet → nginx (443/TLS) → BackOfficeServer (9877/HTTP) → SQLite DBs
                                     ↓
                              SessionManager (HMAC tokens, per-DB write locks)
```

- **nginx** terminates TLS, proxies to Java backend
- **BackOfficeServer** handles all API logic, no framework dependency
- **SessionManager** signs tokens with HMAC-SHA256, serializes writes per DB file
- **SQLite WAL mode** allows concurrent reads alongside serialized writes

## Security

- Session tokens are HMAC-SHA256 signed — cannot be forged without the secret
- Constant-time signature comparison prevents timing attacks
- Unsigned (legacy) UUID tokens accepted for backward compatibility in local/test use
- CORS headers configured for browser-based clients
- TLS via nginx reverse proxy (not in Java — keeps the server simple)

## Scaling Notes

This deployment handles 3-10 concurrent users on a single server. For larger teams:

| Concern | Current | Future |
|---------|---------|--------|
| Sessions | In-memory (single JVM) | Redis-backed (BO-5) |
| Database | SQLite (single-writer) | PostgreSQL (BO-6) |
| Auth | Token-based (no roles) | AD_Role + AD_User (BO-5) |
| Export | JSON only | PDF/CSV/XLSX (BO-4) |

The DAO abstraction isolates all database access — swapping SQLite for PostgreSQL
changes the connection factory, not the business logic.

## Pipeline Script Architecture

The pipeline has three independent lifecycle phases. Each phase is a separate
subscript, callable alone or via the fleet orchestrator.

### Phases

| Phase | Script | Input | Output | When to run |
|-------|--------|-------|--------|-------------|
| **Extract** | `extract_bom.sh <prefix>` | IFC source + classify YAML | `library/{PREFIX}_BOM.db` | IFC source changed OR extraction logic changed |
| **Populate** | `populate_geometry.sh <prefix>` | `*_extracted.db` | `component_library.db` rows | Already smart — skips if populated |
| **Compile** | `compile.sh <prefix>` | `*_BOM.db` + `component_library.db` | `output.db` | Every time (fast — reads BOM, writes output) |

### Re-extract triggers

The BOM.db is stale when either the **source** or the **logic** changes:

| Trigger | What changed | How to detect |
|---------|-------------|---------------|
| IFC re-export | New IFC file from Revit/ArchiCAD | SHA-256 of IFC source file ≠ hash stored in `ad_sysconfig` |
| Extraction code | Java in `IFCtoBOM/src/main/**` | `git diff --name-only HEAD~1` includes IFCtoBOM paths |
| Classify YAML | `classify_{prefix}.yaml` modified | File mtime or git diff |
| Migration SQL | `migration/DV*` applied | Migration version > BOM.db version stamp |

`extract_bom.sh` checks these triggers and skips if unchanged. The integrity
hash already exists in `ad_sysconfig` — extend it to include source IFC hash
and extraction code hash (e.g. `git rev-parse HEAD:IFCtoBOM/`).

### Orchestrator

`run_RosettaStones.sh` becomes a thin loop that calls the three subscripts per
building. For fleet runs, it parallelises extraction (CPU-bound) and serialises
compilation (writes to shared `output.db`).

```
run_RosettaStones.sh [classify_XX.yaml]
  for each building:
    extract_bom.sh <prefix>       # skips if BOM.db current
    populate_geometry.sh <prefix>  # skips if already populated
    compile.sh <prefix>            # always runs (fast)
    gate_test.sh <prefix>          # G1-G6 + contract tests
```

### Dev workflow

```bash
# Changed extraction logic → re-extract one building
./scripts/extract_bom.sh te

# Changed pipeline code → compile only (BOM.db unchanged)
./scripts/compile.sh te

# Full fleet CI
./scripts/run_RosettaStones.sh
```

## Git Operations

### .gitignore — Large Files
- `library/component_library.db` (~433MB) — gitignored, regenerable via `--populate`
- `backup/` — gitignored. NEVER commit database snapshots (1.5GB+, GitHub rejects >100MB)
- `.venv/` — Python virtual environment, gitignored
- `.claude/` — user-local, untracked
- `IFC_research_files/bim_whale_samples/BasicHouse.ifc` — 50MB, under limit but watch for more

### Git LFS
- `component_library.db` was tracked via git-lfs (Phase 109B, history rewritten)
- File is now in `.gitignore` — NEVER force-add without LFS

### Migration Discipline
- ALL schema changes MUST be idempotent SQL in `migration/` (append-only)
- Use `INSERT OR IGNORE` / `INSERT OR REPLACE` / `CREATE TABLE IF NOT EXISTS`
- Write migrations AS YOU MAKE changes, not after
- After any migration, regen schema snapshots (`library/schema_snapshot_bom.sql`)

### Commit Discipline
- Commit at each phase boundary
- `git add <specific files>` not `git add -A` (prevents accidental binary commits)
- Commit message format: `[TAG] summary`
- Seal: re-seal via `scripts/verify_test_seal.sh` when sealed files change

### Incident Log
- **S60-S2:** `backup/` (1.5GB) accidentally committed → `git filter-repo --invert-paths --path backup/` to strip from history. Added to `.gitignore`.
