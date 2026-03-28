# BIM Designer — Installer Specification
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>One download, five minutes, working BIM Designer.</b> Solo or multi-user — no server setup, no database configuration, no IT department. Local mode works offline. Cloud mode at bomtree.io gives you collaboration, shared catalogs, and client handoff with zero infrastructure.
</div>

**Version:** 2.0 (2026-03-29) — added Oracle Cloud deployment (§10), context-sensitive help (§11)
**Status:** SPEC — Phase A after multi-server stability, Phase B for July beta

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

## 10. Oracle Cloud Deployment

### 10.1 Architecture — Cloud Databases, Local Viewport

All databases live in the cloud. The user's machine runs Blender + Bonsai
as a viewport. BIMController on the user's machine talks to the cloud
databases via the BackOffice API. Compilation runs server-side.

```
User's machine                         Oracle Cloud
┌──────────────────────┐               ┌──────────────────────┐
│ Blender + Bonsai     │               │ ERP.db (shared)      │
│ Federation addon     │   ◄── API ──► │ component_library.db │
│ BIMController        │               │ {PREFIX}_BOM.db      │
│                      │               │ output.db            │
│ Local only:          │               │ BackOffice server    │
│  .blend scene        │               │ Compilation pipeline │
│  viewport cache      │               └──────────────────────┘
└──────────────────────┘
```

**Cloud holds everything:** ERP.db, component_library.db, BOM.db, output.db.
Always up to date — when we update products, rules, or compliance packs,
every user gets them immediately.

**User's choice — save to cloud or local:**
- **Cloud save** (default): project persists in user's cloud workspace.
  Accessible from any machine. Shared with collaborators.
- **Local save**: user exports their **Order set** (C_Order + C_OrderLine)
  to their machine. That's it — the user's entire design is an order.
  Everything else (products, rules, BOMs) is shared infrastructure.

This is the magic: a building design is an iDempiere order. Portable,
lightweight, versionable. The user's work product is a handful of rows,
not gigabytes of geometry.

**Local only:** .blend scene file (Blender native), viewport cache.

### 10.2 Free Tier — Oracle Cloud Always Free

Oracle Cloud Infrastructure offers an [Always Free tier](https://www.oracle.com/cloud/free/)
with compute + storage sufficient for the beta:

**Phase 1 (beta): ARM VM + SQLite**

| Resource | Free Allocation | Our Use |
|----------|----------------|---------|
| ARM Ampere A1 | 4 OCPUs, 24 GB RAM | BackOffice + compilation pipeline + SQLite DBs |
| Block storage | 200 GB | All databases + revolving user workspaces |
| Object storage | 10 GB | IFC file staging, archived workspaces |
| Network | 10 TB/month | API + catalog sync |

Familiar stack, zero migration. Good enough for early users.

**Phase 2 (scale): Autonomous Database**

When multi-user concurrency outgrows SQLite's single-writer lock,
migrate to Autonomous DB (2 free instances, 20 GB each). The port
is mechanical — iDempiere PK migration (S86-S88) already aligned
the schema to Oracle conventions. Remaining work: `TEXT` → `VARCHAR2`,
`AUTOINCREMENT` → `GENERATED AS IDENTITY`, SQLite pragmas removed.
ORDS (REST API) and APEX (admin UI) come free with ADB.

**Revolving pool model:** Free-tier users get a temporary workspace.
Workspaces idle >30 days are archived to object storage. User can
restore on next login (cold start ~10s). Active users keep their
workspace warm.

### 10.3 User Tiers and Pricing

#### Identity Domain Strategy

| Users | OCI Identity Domain | Cost to Us |
|-------|-------------------|------------|
| 0–2,000 | Free (included with tenancy) | $0 |
| 2,000+ | External (B2C, self-registration) | ~$0.02–0.05/user/month |

Free domain slots are recycled: inactive users (90 days no login)
are archived and deleted to free slots. Users can re-register and
restore their Order set from object storage anytime.

#### Recycling (Free Tier Slot Management)

```
Weekly cron:
  1. SCIM API → list all users with lastSuccessfulLoginDate
  2. Filter: last login > 90 days ago
  3. Email: "Your BOMTree workspace expires in 7 days — log in to keep it"
  4. After 7 days, no login → archive Order set to object storage → DELETE user
  5. Slot freed. User re-registers anytime, Order restored from archive.
```

#### Four Tiers

| Tier | Price | What User Gets | Infrastructure |
|------|-------|---------------|----------------|
| **Free** | $0 | Revolving workspace (90 days idle = archived), shared catalog, 1 active project | Free Identity Domain (2,000 recycled slots) |
| **Pro** | $5/month | Persistent workspace, unlimited projects, snapshot history, priority compile | External Identity Domain (~$0.03/user to Oracle) |
| **Team** | $15/month | Pro + 5 collaborators on same project, shared order sets | External Identity Domain + multi-user BackOffice |
| **Enterprise** | Custom | Dedicated tenancy, Oracle DB, private cloud, SLA | User gets own OCI tenancy, pays Oracle directly |

#### Revenue Model

| Tier | User Pays | We Pay Oracle | Our Margin |
|------|-----------|--------------|------------|
| Free | $0 | $0 (Free Tier) | $0 (acquisition) |
| Pro | $5/month | ~$0.03/user identity + ~$0.50 storage | ~$4.47/user/month |
| Team | $15/month | ~$0.15 identity + ~$2 storage/compute | ~$12.85/team/month |
| Enterprise | Custom | Referral — user pays Oracle directly | Referral benefit |

**Payment collection:** Stripe or Paddle for Pro/Team. Enterprise goes
direct to Oracle. The free tier never goes away — it's the acquisition
funnel. The tool itself is open source. Value is in the managed platform.

### 10.4 Market Position

BOMTree would be the **first BIM tool on Oracle Cloud Marketplace** that
extracts recursive BOMs from IFC files. Current landscape (as of 2026-03):

| Category | Existing Products | BOMTree Differentiator |
|----------|------------------|----------------------|
| BIM on OCI Marketplace | 2 viewers (BIMvision, TeamSystem) | First BOM/ERP tool |
| IFC → recursive BOM | Nobody ships this | Building → floor → room → component tree |
| BIM-to-ERP order lines | CADTALK (flat BOQ → JDE only) | Recursive BOM → iDempiere order lines |
| Open-source BIM on Oracle | None | First |

**Go-to-market path:**
1. Register `bomtree.org` — FOSS identity, docs, community
2. Register `bomtree.io` — product/cloud URL (users see `bomtree.io`, never "OCI")
3. OPN Member registration (free)
4. OCI Marketplace listing (free tier image)
5. Oracle for Startups application ($500 credits + 70% discount)
6. Oracle ACE Associate self-nomination (blog + GitHub + OCI)
7. Oracle C&E integration (Primavera API connector → scheduling feed)

### 10.5 Launch Strategy

**Free until end of 2026.** All tiers free during launch period.
Users sign up at `bomtree.io`, work for free, build dependency.
Pricing activates January 2027.

| Phase | Period | What Users See |
|-------|--------|---------------|
| **Beta** | July–September 2026 | `bomtree.io` — invite-only, 100 users |
| **Launch** | October–December 2026 | `bomtree.io` — open signup, all features free |
| **Monetize** | January 2027 | Free tier stays free. Pro/Team tiers start billing |

Users never see "Oracle Cloud" or "OCI". They see:
- `bomtree.io` — the product URL (cloud workspace, login, API)
- `bomtree.org` — the FOSS project (docs, source, community)
- `bomtree.io/login` — branded BOMTree login page (OCI Identity behind the scenes)
- `bomtree.io/workspace/{username}` — their workspace

Oracle is the invisible infrastructure, like AWS is invisible behind Netflix.

### 10.6 Market Sizing

At $10/month, BOMTree undercuts every commercial BIM collaboration tool:

| Tool | Price/user/month |
|------|-----------------|
| Autodesk ACC | $85–175 |
| BIMcollab | $16–35 |
| Bluebeam Cloud | $18 |
| Trimble Connect | $13–20 |
| **BOMTree** | **$10** |

**Target audience:** Small firms (1–10 people) — 1.1M+ firms globally,
priced out of Autodesk. BIM mandates forcing adoption regardless.

**ASEAN home court:** Malaysia (75K CIDB firms, mandate since 2024),
Singapore (5K firms, mandate since 2015), Indonesia (200K firms),
Thailand (50K firms). $332/month Autodesk is prohibitive for ASEAN.

| Scenario | Free Users | Convert | Paying | MRR |
|----------|-----------|---------|--------|-----|
| Year 1 — MY only | 5,000 | 5% | 250 | $2,500 |
| Year 2 — ASEAN | 50,000 | 7% | 3,500 | $35,000 |
| Year 3 — Global | 200,000 | 8% | 16,000 | $160,000 |

**Cost per user:** ~$0.50/month (Oracle Free Tier + minimal overflow).
**Margin at $10/month:** ~95%. Open source + free infrastructure = moat.

### 10.7 User Identity and Access

**OCI Identity Domains** (free with Always Free tier) provides:

- **User registration + login** — self-service signup with email verification
- **OAuth2 / OpenID Connect** — standard auth for BIMController ↔ BackOffice API
- **Social login** — Google, GitHub, SAML federation
- **MFA** — TOTP, push notification, SMS
- **Up to 2,000 users** on the free tier (Oracle Identity Domain)
- **Custom login page** — branded for BOMTree

The BackOffice API authenticates via OCI Identity Domain tokens.
No custom auth code needed — Oracle handles registration, login,
password reset, MFA.

**Custom URL:** OCI supports custom domain names via:
- **Load Balancer** (free tier includes 1 flexible LB) → point `bomtree.io` to it
- **DNS Zone** (free, 1 zone) → manage `bomtree.io` DNS records in OCI
- **SSL certificate** via OCI Certificates (free, auto-renew with Let's Encrypt)

User access URL: `https://bomtree.io` → OCI Load Balancer → BackOffice API.
Each user gets: `https://bomtree.io/workspace/{username}` (routed by BackOffice).

### 10.8 Why Cloud — Local vs OCI

Both modes use the same installer. The user picks at first connect.

| Need | Local | Cloud (OCI) |
|------|-------|-------------|
| Compile my building | Yes | Yes |
| Work offline / airgapped sites | Yes | No |
| Share project with engineer/contractor | No | Yes — send link |
| Compliance rules always current | Manual update | Auto — we push, everyone gets |
| Product catalog grows (new LODs, manufacturers) | Re-download | Always current |
| Collaborate (architect + MEP + structural) | No — SQLite single-writer | Multi-user, concurrent |
| Client handoff ("approve this BOM") | Export + email | `bomtree.io/project/xyz/approve` |
| Version history / rollback | DIY (git) | Built-in snapshots |
| Access from any machine | Only your laptop | Log in anywhere |

**Local** = solo practitioner, developer, offline construction sites.
**Cloud** = the moment you need to share work with someone else.

### 10.9 Installer Flow — Dual Mode

```
Step 1: Download BIMDesigner installer (existing §4 flow)
Step 2: Run installer → installs Bonsai + Federation addon + BIMController
Step 3: Open Blender → BIM Designer panel appears
Step 4: Click "Connect"
        ┌─────────────────────────────────────────┐
        │  How do you want to work?                │
        │                                          │
        │  [☁ BOMTree Cloud]    [💻 Local]          │
        │                                          │
        │  Cloud: collaborate, share, always        │
        │  updated. Free account at bomtree.io.     │
        │                                          │
        │  Local: offline, private, self-managed.   │
        │  Databases on your machine.               │
        └─────────────────────────────────────────┘

Cloud path:
  → Browser opens bomtree.io/register
  → User signs up (email or Google/GitHub)
  → OAuth2 token returned to addon
  → "List Buildings" → shared catalog
  → "Create New" → Order saved to cloud

Local path:
  → Server auto-starts on localhost:9876
  → Local SQLite DBs in ~/.bim-designer/data/
  → Same UI, same pipeline, fully offline
```

User can switch anytime:
- **Cloud → Local:** Export Order set (C_Order + C_OrderLine) to local file
- **Local → Cloud:** Upload Order set to bomtree.io workspace

### 10.10 Payment Processing

OCI handles infrastructure, not billing. A merchant-of-record service
collects subscription fees, handles tax compliance (GST/VAT/sales tax),
and pays us net. We never touch tax filings.

**Recommended: Paddle** (or Lemon Squeezy as alternative).

| Concern | Who Handles It |
|---------|---------------|
| Subscription billing ($10/month) | Paddle |
| Payment methods (card, PayPal, local) | Paddle |
| GST (Malaysia), GST (Singapore), VAT (EU), sales tax (US) | Paddle |
| Invoices + receipts | Paddle |
| Refunds + chargebacks | Paddle |
| Payout to us | Paddle (monthly, net of fees) |
| User tier upgrade in BackOffice | Our webhook handler |

**Fee:** ~5% + $0.50/transaction. On a $10/month subscription:
Paddle takes ~$1.00, we receive ~$9.00/user/month.

**Flow:**

```
User in Bonsai → clicks "Upgrade to Pro"
  → Browser opens bomtree.io/pricing
  → Paddle checkout overlay ($10/month)
  → User pays (card / PayPal / local methods)
  → Paddle webhook → BackOffice API: POST /tier/upgrade
  → BackOffice: set user.tier = PRO
  → User's workspace upgraded immediately

Cancellation:
  → User cancels in bomtree.io/account or Paddle portal
  → Paddle webhook → BackOffice API: POST /tier/downgrade
  → At billing period end: workspace reverts to Free tier
  → Order sets preserved (read-only until re-subscribe or export)
```

**During free launch (July–December 2026):** Paddle not needed.
All users get Pro features free. Paddle integration activates
January 2027 when monetization starts.

---

## 11. Context-Sensitive Help

### 11.1 Help Key → docs site

Press **F1** (or Help button) anywhere in the BIM Designer panel.
The addon opens the user's browser to the correct section of
`bomtree.org/docs/` based on the active context.

### 11.2 Help Map

Each panel/tool registers a help URL suffix:

| Context | URL Suffix | Target Doc |
|---------|-----------|------------|
| BOM tree panel | `BOMBasedCompilation/#2-compilation-model` | BBC §2 |
| Order lines panel | `DocAction_SRS/#1-processit-lifecycle` | DocAction §1 |
| Compile button | `BIM_Designer_UserGuide/#compile` | User Guide §Compile |
| Validation results | `DocValidate/#0-spatial-regulatory-symbiosis` | DocValidate §0 |
| Forge parameters | `FORGE_SUITE_SRS/` | Forge Suite |
| Building list | `BIM_Designer_UserGuide/#list-buildings` | User Guide §List |
| Create New dialog | `GENERATIVE_HOUSE_SRS/` | Generative House |
| Back Office | `BackOfficeUserGuide/` | BackOffice Guide |
| Assembly layers | `ASSEMBLY_BUILDER_SRS/` | Assembly Builder |
| No specific context | `BIM_Designer_UserGuide/` | User Guide (landing) |

### 11.3 Implementation

```python
# In panel.py — each panel class defines HELP_URL
class BIM_PT_bom_tree(bpy.types.Panel):
    HELP_URL = "BOMBasedCompilation/#2-compilation-model"

# In operator.py — F1 handler
class BIM_OT_help(bpy.types.Operator):
    bl_idname = "bim.help"
    bl_label = "Help"

    def execute(self, context):
        base = "https://bomtree.org/docs/"
        # Find active panel's HELP_URL
        panel = get_active_bim_panel(context)
        suffix = getattr(panel, 'HELP_URL', 'BIM_Designer_UserGuide/')
        import webbrowser
        webbrowser.open(base + suffix)
        return {'FINISHED'}
```

Blender keybinding: `F1` → `bim.help` (registered in addon `__init__.py`).

### 11.4 URL Resolution

The docs site is built with mkdocs-material. Section anchors are
auto-generated from headings. The help map uses these anchors directly.
If a section is renamed, the help map entry updates in the next release.

No server-side search needed — direct deep links only.

---

## 12. Implementation Priority

**Phase A — Local installer (existing §4-§9)**

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

**Phase B — Oracle Cloud (§10)**

| Step | What | Depends on |
|------|------|-----------|
| 9 | BackOffice on Oracle Cloud Free Tier (ARM A1) | Phase A Step 6 |
| 10 | Cloud API endpoint for BIMController | Step 9 |
| 11 | User workspace provisioning (free revolving pool) | Step 9 |
| 12 | Order export/import (local save of C_Order + C_OrderLine) | Step 10 |
| 13 | Paid tier handoff to Oracle billing | Step 11 |

**Phase C — Context-Sensitive Help (§11)**

| Step | What | Depends on |
|------|------|-----------|
| 14 | HELP_URL registry on all panel classes | Nothing |
| 15 | F1 keybinding + `bim.help` operator | Step 14 |
| 16 | Verify all deep-link anchors against docs site | Docs site deployed |
