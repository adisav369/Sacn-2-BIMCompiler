# BIM Intent Compiler — Systems Installer Guide
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [ConstructionAsERP](ConstructionAsERP.md) · [TestArchitecture](TestArchitecture.md)

**Audience:** Systems administrators, DevOps engineers, and developers setting up the full BIM Compiler platform from source.

**For end-user desktop installation**, see [INSTALLER_SPEC.md](INSTALLER_SPEC.md).
**For WAN/Docker deployment**, see [DEPLOYMENT.md](DEPLOYMENT.md).

---

## 1. Prerequisites

| Component | Version | Purpose | Check |
|-----------|---------|---------|-------|
| Java (JDK) | 17+ | Compiler, servers, all backend logic | `java --version` |
| Maven | 3.8+ | Build system, dependency management | `mvn --version` |
| SQLite | 3.40+ | Database engine (bundled via JDBC, CLI for inspection) | `sqlite3 --version` |
| Python | 3.10+ | IFC extraction (IfcOpenShell), Blender addon | `python3 --version` |
| Blender | 4.0+ | 3D viewport (BIM Designer GUI) | `blender --version` |
| Git | 2.30+ | Source control, LFS for binary assets | `git --version` |
| Docker | 24+ | WAN deployment (optional) | `docker --version` |
| IfcOpenShell | 0.7+ | IFC file parsing and extraction | `python3 -c "import ifcopenshell"` |

**Tested on:** Ubuntu 24.04 (Linux 6.17), OpenJDK 17.0.18, Maven 3.8.7, Blender 5.1.0.

---

## 2. Clone and Build

```bash
# 2.1 Clone the repository
git clone git@github.com:red1oon/BIMCompiler.git
cd BIMCompiler

# 2.2 Compile all 9 modules
mvn compile -q

# 2.3 Verify — run the full test gate
./scripts/run_tests.sh
```

**Expected output:** ~382 PASS, ~94 intentional RED (pre-existing calibration items — see `scripts/run_tests.sh` header for breakdown).

**Module build order** (Maven reactor handles this automatically):

```
orm-core          → base ORM, BIMLogger
ORMSandbox        → DAO smoke tests
DAGCompiler       → 9-stage compilation pipeline
2D_Layout         → floor plan generation
TopologyMaker     → grid strategy, PO lifecycle
BIM_COBOL         → 63 domain verbs, witness engine
IFCtoBOM          → IFC extraction to BOM database
BIMBackOffice     → ERP reporting, sessions, portfolio
BonsaiBIMDesigner → GUI server, validation, assembly
```

---

## 3. Database Files

The `library/` directory contains all SQLite databases. These ship with the repo.

### 3.1 Core Databases (required)

| File | Size | Purpose |
|------|------|---------|
| `component_library.db` | ~5 MB | Master product catalog: 608 products, 23.9K geometries, thermal properties |
| `disc_validation.db` | ~1 MB | Discipline validation rules, IFC class mapping, MEP metadata (21 tables) |
| `validation.db` | ~100 KB | Compliance thresholds and verdicts |

### 3.2 Per-Building BOM Databases (one per building)

| File | Building | Elements | Purpose |
|------|----------|----------|---------|
| `SH_BOM.db` | Sample House | 55 | Hello-world residential |
| `FK_BOM.db` | FZK Haus | 82 | Small residential |
| `DX_BOM.db` | Duplex | 1,099 | Multi-unit residential |
| `TE_BOM.db` | Terminal | 48,428 | Commercial airport terminal |
| `BR_BOM.db` | Bridge | 48 | Infrastructure |
| `RD_BOM.db` | Road | 53 | Infrastructure |
| `RL_BOM.db` | Rail | 73 | Infrastructure |
| `IN_BOM.db` | Infrastructure (combined) | 174 | Multi-facility infrastructure |
| `DM_BOM.db` | DemoHouse | — | Generative template |

### 3.3 Working Databases (generated at runtime)

| File | Created By | Purpose |
|------|-----------|---------|
| `work_*.db` | BonsaiBIMDesigner | Design variants, C_Order snapshots |
| `_*_compile.db` | DAGCompiler | Temporary compile output (per run) |
| `output_template.db` | Pipeline | Schema template for fresh output DBs |

### 3.4 Inspecting Databases

```bash
# List tables
sqlite3 library/SH_BOM.db ".tables"

# Check BOM hierarchy
sqlite3 library/SH_BOM.db "SELECT bom_type, name, bom_category FROM m_bom"

# Check product catalog
sqlite3 library/component_library.db "SELECT COUNT(*) FROM M_Product"

# Check validation rules
sqlite3 library/disc_validation.db "SELECT COUNT(*) FROM AD_Val_Rule"
```

---

## 4. Running the Servers

### 4.1 BIM Designer Server (TCP, port 9876)

For Blender addon clients — single-user design mode:

```bash
mvn exec:java -pl BonsaiBIMDesigner \
    -Dexec.mainClass="com.bim.designer.api.DesignerServer" \
    -Dexec.args="library 9876" -q
```

Or from compiled classes:

```bash
java -cp "BonsaiBIMDesigner/target/classes:BIMBackOffice/target/classes:DAGCompiler/target/classes:BIM_COBOL/target/classes:IFCtoBOM/target/classes:orm-core/target/classes:$(mvn -q dependency:build-classpath -pl BonsaiBIMDesigner -Dmdep.outputFile=/dev/stdout)" \
    com.bim.designer.api.DesignerServer library 9876
```

**Protocol:** ndjson over TCP. One JSON object per line, newline-delimited.

### 4.2 Back Office Server (HTTP, port 9877)

For multi-project ERP reporting, portfolio, 4D-7D queries:

```bash
java -cp "BIMBackOffice/target/classes:orm-core/target/classes:$(mvn -q dependency:build-classpath -pl BIMBackOffice -Dmdep.outputFile=/dev/stdout)" \
    com.bim.backoffice.server.BackOfficeServer library 9877
```

**Verify:**

```bash
curl http://localhost:9877/api/health
curl http://localhost:9877/api/portfolio
curl http://localhost:9877/api/cost?id=SH
```

### 4.3 Both Servers (typical setup)

Run in separate terminals or background:

```bash
# Terminal 1: Designer (for Blender)
mvn exec:java -pl BonsaiBIMDesigner \
    -Dexec.mainClass="com.bim.designer.api.DesignerServer" \
    -Dexec.args="library 9876" -q &

# Terminal 2: Back Office (for browser/API clients)
java -cp "BIMBackOffice/target/classes:orm-core/target/classes:$(mvn -q dependency:build-classpath -pl BIMBackOffice -Dmdep.outputFile=/dev/stdout)" \
    com.bim.backoffice.server.BackOfficeServer library 9877 &
```

---

## 5. Running the Compilation Pipeline

### 5.1 Rosetta Stone Buildings (full pipeline)

```bash
# Compile all Rosetta Stone buildings (SH, DX, TE, infrastructure)
./scripts/run_RosettaStones.sh classify_sh.yaml    # Sample House
./scripts/run_RosettaStones.sh classify_dx.yaml    # Duplex
./scripts/run_RosettaStones.sh classify_te.yaml    # Terminal
./scripts/run_RosettaStones.sh classify_in.yaml    # Infrastructure
```

### 5.2 IFCtoBOM Extraction (from IFC files)

To extract a new building from IFC into a BOM database:

```bash
# Step 1: Create classification YAML (see docs/YAMLGuide.md)
# Step 2: Run extraction pipeline
mvn exec:java -pl IFCtoBOM \
    -Dexec.mainClass="com.bim.ifctobom.IFCtoBOMMain" \
    -Dexec.args="path/to/classify_XX.yaml" -q
```

Output: `library/XX_BOM.db` (per-building BOM dictionary).

### 5.3 DAGCompiler (compile BOM to output)

```bash
mvn exec:java -pl DAGCompiler \
    -Dexec.mainClass="com.bim.compiler.CompilationPipeline" \
    -Dbom.db="library/SH_BOM.db" -q
```

---

## 6. Blender Integration

### 6.1 Federation Module (IFC spatial database)

The Federation module lives in a separate repo:

```bash
# Clone (if not already present)
git clone -b feature/IFC4_DB git@github.com:red1oon/IfcOpenShell.git ~/IfcOpenShell

# The addon path:
# ~/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/
```

### 6.2 Blender Addon Setup

1. Open Blender → Edit → Preferences → Add-ons
2. Install from file: point to the Bonsai addon directory
3. Enable "Bonsai BIM" addon
4. The Federation module loads automatically as a submodule

### 6.3 Connecting Blender to the Design Server

```python
# In Blender's Python console or addon:
import socket, json

sock = socket.create_connection(("127.0.0.1", 9876))
request = json.dumps({"action": "listBuildings"}) + "\n"
sock.sendall(request.encode())
response = sock.makefile().readline()
print(json.loads(response))
```

---

## 7. Test Suites

### 7.1 Full Gate

```bash
./scripts/run_tests.sh          # All suites
```

### 7.2 Individual Modules

```bash
./scripts/run_tests.sh dag      # DAGCompiler (pipeline + gates)
./scripts/run_tests.sh orm      # ORMSandbox (DAO layer)
./scripts/run_tests.sh topology # TopologyMaker (grid strategy)
./scripts/run_tests.sh cobol    # BIM_COBOL (verb witnesses)
```

### 7.3 BonsaiBIMDesigner (248 tests)

```bash
mvn test -pl BonsaiBIMDesigner 2>&1 | tail -5
```

### 7.4 BIMBackOffice (14 tests)

```bash
mvn test -pl BIMBackOffice 2>&1 | tail -5
```

### 7.5 Tamper Seal Verification

```bash
./scripts/verify_test_seal.sh
```

Checks SHA256 fingerprints of 68 critical files. If any have changed since the last seal ceremony, the seal breaks.

---

## 8. WAN Deployment (Docker)

See [DEPLOYMENT.md](DEPLOYMENT.md) for full instructions. Quick summary:

```bash
# Generate TLS certificates
./deploy/generate-certs.sh

# Set session signing secret
export BIM_SESSION_SECRET="your-secret-here"

# Launch
docker-compose up -d

# Verify
curl -k https://your-server/api/health
```

**Architecture:**

```
Internet → nginx:443 (TLS) → BackOfficeServer:9877 (HTTP) → SQLite DBs
                                      ↓
                               SessionManager
                               (HMAC tokens, per-DB write locks, WAL mode)
```

---

## 9. Migration Scripts

SQL migrations are in `migration/` — **append-only, never modify existing**.

| Migration | Purpose |
|-----------|---------|
| `ASM001_material_thermal.sql` | Thermal conductivity table for U-value calculation |
| `ASM003_ac11_materials.sql` | AC11 material properties |
| `DV006b_infra_bridge_rules_fix.sql` | Bridge validation rules fix |
| `DV007_infra_road_rules.sql` | Road discipline rules (10 rules) |
| `DV008_infra_rail_rules.sql` | Rail discipline rules (7 rules) |

**Running a migration:**

```bash
sqlite3 library/disc_validation.db < migration/DV007_infra_road_rules.sql
```

---

## 10. Directory Structure

```
BIMCompiler/
├── orm-core/              # Base ORM, BIMLogger, shared utilities
├── ORMSandbox/            # DAO smoke tests, BuildingInspector
├── DAGCompiler/           # 9-stage compilation pipeline (G1-G6 gates)
├── 2D_Layout/             # Floor plan generation
├── TopologyMaker/         # Grid strategy, production order lifecycle
├── BIM_COBOL/             # 63 domain verbs, witness engine
├── IFCtoBOM/              # IFC extraction → BOM database pipeline
├── BIMBackOffice/         # ERP reporting, sessions, portfolio, 4D-7D
├── BonsaiBIMDesigner/     # GUI server, validation, assembly, placement
├── library/               # SQLite databases (product catalog, BOMs)
├── migration/             # SQL migration scripts (append-only)
├── scripts/               # Build, test, and audit shell scripts
├── deploy/                # nginx config, TLS cert generator
├── docs/                  # All specifications and analysis documents
├── docker-compose.yml     # WAN deployment
├── Dockerfile             # Multi-stage build
└── pom.xml                # Parent POM (9 modules)
```

---

## 11. Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| `mvn compile` fails | Missing JDK 17 | Install OpenJDK 17: `sudo apt install openjdk-17-jdk` |
| `sqlite3: command not found` | SQLite CLI not installed | `sudo apt install sqlite3` |
| Port 9876/9877 in use | Server already running | `lsof -i :9876` then kill the process |
| `ClassNotFoundException` | Incomplete build | `mvn compile -q` (compile all modules first) |
| Test seal BROKEN | Files changed since last seal | Expected during development; run `./scripts/verify_test_seal.sh` to see which files |
| `No such database: XX_BOM.db` | BOM not extracted yet | Run `./scripts/run_RosettaStones.sh classify_xx.yaml` |
| IfcOpenShell import error | Python package missing | `pip install ifcopenshell` or use conda |
| Blender addon not showing | Addon not enabled | Blender → Preferences → Add-ons → search "Bonsai" → enable |
| CORS errors in browser | Direct access without proxy | Use nginx proxy (see DEPLOYMENT.md) or add `--cors` flag |
| `HMAC signing failed` | JDK crypto issue | Ensure standard JDK (not headless-minimal) |

---

## 12. Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `BIM_SESSION_SECRET` | (random per JVM) | HMAC-SHA256 key for session token signing |
| `BIM_LIBRARY_DIR` | `./library` | Path to database directory |
| `BIM_LOG_LEVEL` | `INFO` | Logging level (DEBUG, INFO, WARN, ERROR) |

---

## 13. Verification Checklist

After installation, verify each layer:

```bash
# 1. Build compiles
mvn compile -q && echo "BUILD OK"

# 2. Databases accessible
sqlite3 library/component_library.db "SELECT COUNT(*) FROM M_Product"
# Expected: 608+

# 3. Tests pass
mvn test -pl BIMBackOffice -Dtest="BackOfficeServerTest" 2>&1 | grep "Tests run"
# Expected: Tests run: 14, Failures: 0

# 4. Designer server starts
timeout 5 mvn exec:java -pl BonsaiBIMDesigner \
    -Dexec.mainClass="com.bim.designer.api.DesignerServer" \
    -Dexec.args="library 9876" -q &
sleep 2 && echo '{"action":"listBuildings"}' | nc -w2 localhost 9876
kill %1 2>/dev/null

# 5. Back Office server starts
curl -s http://localhost:9877/api/health 2>/dev/null | grep -q "UP" && echo "BACKOFFICE OK"

# 6. Rosetta Stone compiles
./scripts/run_RosettaStones.sh classify_sh.yaml 2>&1 | tail -3
```

---

*For the project overview paper, see [BIMERPPaper.md](BIMERPPaper.md).*
*For the end-user installer specification, see [INSTALLER_SPEC.md](INSTALLER_SPEC.md).*
*For the WAN/Docker deployment guide, see [DEPLOYMENT.md](DEPLOYMENT.md).*
