# BIM Designer — Installer Specification
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [SystemContract](SystemContract.md) · [TestArchitecture](TestArchitecture.md)

**Version:** 1.0 (2026-03-20)
**Status:** SPEC — to be implemented after multi-server service is stable

---

## 1. Goal

A non-technical user downloads one package, installs it, and has a working
BIM Designer in Blender within 5 minutes. No terminal commands. No Java
installation. No database setup.

---

## 2. What the User Sees

```
1. Download BIMDesigner-1.0-linux.tar.gz  (or .exe / .dmg)
2. Run installer
3. Open Blender
4. BIM Designer panel appears in Properties → Scene
5. Click "Connect" — server starts automatically
6. Click "List Buildings" — sample buildings appear
7. Click "Create New" — first building in 3 minutes
```

---

## 3. Package Contents

```
BIMDesigner/
├── jre/                          # Bundled Java 17 runtime (~50 MB)
├── server/
│   ├── bim-designer-server.jar   # Fat JAR (all modules merged)
│   └── start-server.sh (.bat)    # Launch script (uses bundled JRE)
├── data/
│   ├── component_library.db      # Product catalog (800 products)
│   ├── ERP.db        # 63 validation rules
│   ├── SH_BOM.db                 # Sample House BOM (demo)
│   └── DM_BOM.db                 # DemoHouse generative BOM
├── addon/
│   └── bonsai_bim_designer/      # Blender addon (Python)
│       ├── __init__.py
│       ├── client.py
│       ├── props.py
│       ├── operator.py
│       ├── panel.py
│       ├── design_bbox.py
│       └── db_loader.py
├── install.sh (.bat)             # Installer script
└── README.txt                    # Quick start (5 lines)
```

**Estimated size:** ~100 MB compressed (JRE dominates).

---

## 4. Installer Steps

### 4.1 Linux / macOS (`install.sh`)

```bash
#!/bin/bash
INSTALL_DIR="$HOME/.bim-designer"

echo "Installing BIM Designer to $INSTALL_DIR..."
mkdir -p "$INSTALL_DIR"
cp -r jre server data "$INSTALL_DIR/"

# Auto-detect Blender addon path
BLENDER_ADDON=$(find "$HOME/.config/blender" -maxdepth 2 -name "scripts" 2>/dev/null | head -1)/addons
if [ -z "$BLENDER_ADDON" ] || [ ! -d "$BLENDER_ADDON" ]; then
    BLENDER_ADDON="$HOME/.config/blender/4.0/scripts/addons"
    mkdir -p "$BLENDER_ADDON"
fi

cp -r addon/bonsai_bim_designer "$BLENDER_ADDON/"

# Patch addon to know where server + data live
echo "BIM_DESIGNER_HOME = '$INSTALL_DIR'" > \
    "$BLENDER_ADDON/bonsai_bim_designer/config.py"

echo "Done. Open Blender → Edit → Preferences → Add-ons → enable 'BIM Designer'"
```

### 4.2 Windows (`install.bat`)

Same logic, paths adjusted for `%APPDATA%\Blender Foundation\Blender\`.

---

## 5. Server Auto-Start

The Blender addon's Connect button should:

1. Check if server is already running (try TCP 9876)
2. If not: spawn `start-server.sh` as a background process
3. Wait up to 5 seconds for port to become available
4. Connect

```python
# In operator.py connect handler:
import subprocess, socket, time

def ensure_server():
    """Start server if not running."""
    try:
        s = socket.create_connection(("127.0.0.1", 9876), timeout=1)
        s.close()
        return True  # Already running
    except ConnectionRefusedError:
        pass

    home = get_bim_designer_home()
    subprocess.Popen(
        [f"{home}/jre/bin/java", "-jar", f"{home}/server/bim-designer-server.jar",
         "--data-dir", f"{home}/data"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
    )

    for _ in range(50):  # Wait up to 5 seconds
        time.sleep(0.1)
        try:
            s = socket.create_connection(("127.0.0.1", 9876), timeout=0.5)
            s.close()
            return True
        except ConnectionRefusedError:
            continue
    return False
```

---

## 6. Fat JAR Build

Maven assembly plugin merges all modules into one executable JAR:

```xml
<!-- In BonsaiBIMDesigner/pom.xml -->
<plugin>
    <artifactId>maven-assembly-plugin</artifactId>
    <configuration>
        <archive>
            <manifest>
                <mainClass>com.bim.designer.api.DesignerServer</mainClass>
            </manifest>
        </archive>
        <descriptorRefs>
            <descriptorRef>jar-with-dependencies</descriptorRef>
        </descriptorRefs>
    </configuration>
</plugin>
```

Build: `mvn package -pl BonsaiBIMDesigner -DskipTests`

---

## 7. Data Directory Configuration

The server needs to know where databases live. Options:

| Method | How |
|--------|-----|
| `--data-dir /path` | CLI argument (installer default) |
| `BIM_DATA_DIR` env var | Environment variable |
| `./data/` | Relative to JAR (fallback) |

The addon passes `--data-dir` when auto-starting the server.

---

## 8. Multi-User Server Mode

For the back-office multi-user deployment:

```bash
# Server mode: binds to network (not just localhost)
java -jar bim-designer-server.jar \
    --data-dir /shared/bim-data \
    --host 0.0.0.0 \
    --port 9876 \
    --multi-user
```

Multiple Bonsai clients connect to the same server. The ChangelogDAO
tracks per-user edits. SQLite write serialization handled by the
service layer.

---

## 9. First-Run Experience

After installation, the user should see:

1. **Connect** → green indicator (server auto-started)
2. **List Buildings** → "Sample House (55 elements)" appears
3. **Compile** → "Compiled: 55 elements in 847ms"
4. **Create New** → dialog with building type, jurisdiction, rooms
5. **Generate** → coloured bboxes appear in viewport

**Target: working building in under 3 minutes from first launch.**

---

## 10. Implementation Priority

| Step | What | Depends on |
|------|------|-----------|
| 1 | Fat JAR build (maven-assembly-plugin) | Nothing |
| 2 | `--data-dir` CLI argument in DesignerServer | Nothing |
| 3 | Auto-start in Python addon | Step 1, 2 |
| 4 | `install.sh` / `install.bat` | Step 1 |
| 5 | Bundled JRE (jlink / adoptium download) | Step 4 |
| 6 | Multi-user `--host 0.0.0.0` mode | Multi-server service |
| 7 | Windows .exe wrapper (launch4j / jpackage) | Step 5 |
| 8 | macOS .dmg | Step 5 |
