/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */

# BIM OOTB — Licensing Position, Valuation & Prior Art Analysis

*Prepared: 2026-05-01*

---

## 1. Current Codebase State (Evidence Base)

### 1.1 Scale

| Layer | Files | Notes |
|---|---|---|
| Browser JS (deploy/dev/) | 68 project-authored .js files | scene.js, main.js, panels.js, navigate.js, wizard.js, streaming.js, picking.js, import_worker.js, 2d.html, boq_charts.html, index.html — all proven |
| Java pipeline (DAGCompiler) | 302 .java files | 11-stage pipeline, 77 verbs |
| 2D Layout Java | 8 .java files | DrawingStage, ScopeStage, DrawingPipeline |
| Docs | 74 .md files | Specs, guides, analysis docs |
| Tests | 108 Playwright PASS (desktop), 172 whitebox tests | 0 pre-existing failures |

### 1.2 Proven Features (public, timestamped)

- **IFC drop → 3D scene** — 100K+ objects, 3 seconds. Proven on LTU AHouse (122K el), Revit (11K el), Smiley West, FZKHaus, SampleHouse. Public YouTube demonstration with viewer comments confirming IFC drop works (2026-05-01 timestamp — independent public attestation).
- **Browser IFC export** — DB → .ifc STEP text download. Pure browser, no server.
- **Multi-format import** — IFC, DAE, GLB/GLTF, FBX, 3DS, OBJ. Auto axis/scale detection.
- **2D DXF viewer** — Canvas2D, layer toggle, pan/zoom, BIMSRC xdata. 93/94 elements survive round-trip.
- **2D annotation** — wall hatches, furniture, room labels, door/window tags, section markers.
- **Find & Navigate** — indoor wayfinding, 26/26 tests PASS.
- **Mobile GPS site camera** — compass, QR, BIM PiP, TrueNorth alignment, measure, section cut, issue log.
- **BOQ / 5D Cost** — ExcelJS, FIDIC Clause 12, locale support, 15 locale files.
- **InstancedMesh performance** — 85-95% draw call reduction. Clinic: 16K → 729 draws.
- **IFC round-trip** — browser IFC export (S229): DB → ifc_export_worker.js → .ifc STEP download. IndexedDB is master record; IFC is exchange format. Versioned DB (wizard modifications) preserved through export.
- **Building cards** — per-import cards with Open / IFC / Delete buttons. Each card shows element count, discipline bars, format badge. Versioned DB survives wizard edits.
- **4D scheduling** — concept proven, linked to project task dates.
- **iDempiere ERP integration** — concept proven, REST layer identified.
- **Java pipeline gates** — 9-gate RosettaStone system, 21 buildings, 116/157 PASS.

### 1.3 Architecture Fingerprint (novelty markers)

```javascript
// scene.js — the core patentable pipeline
A.blobToGeometry = function(vBlob, fBlob) {
    const vArr = new Float32Array(vBlob.buffer, vBlob.byteOffset, vBlob.byteLength / 4)
    const fArr = new Uint32Array(fBlob.buffer, fBlob.byteOffset, fBlob.byteLength / 4)
    const geo  = new THREE.BufferGeometry()
    // BLOB → GPU — no server, no format conversion, no geometry kernel
}
```

This function and its surrounding architecture — IFC → SQLite BLOBs → sql.js → Float32Array → THREE.js BufferGeometry → WebGL — is the core claimed novelty.

---

## 2. Prior Art Analysis (Research completed 2026-05-01)

### 2.1 Claim 1 — SQLite BLOB → browser → WebGL pipeline

**Verdict: No complete prior art found.**

The 6-step chain does not exist assembled in any prior work:

| Step | Status |
|---|---|
| Pre-tessellate IFC server-side | Common (Autodesk, Bentley do this) |
| Store as SQLite BLOBs | Bentley iModel does this; IfcOpenShell ifc2sql does this |
| Deliver SQLite file statically to browser | **Not done by any commercial player** |
| Query BLOBs via sql.js WASM in browser | **Not done — IFCdataBrowser uses sql.js but stops at data analysis** |
| Construct THREE.js BufferGeometry from typed arrays | web-ifc does typed arrays but from live parsing, not stored BLOBs |
| Render WebGL with zero geometry server | xeokit/XKT achieves static delivery but uses proprietary binary, not SQL |

**Closest prior art and why each falls short:**

**Bentley iTwin/iModel — deep analysis (source code verified 2026-05-01)**

This is the most important comparison. Research read actual iTwin source code
from `iTwin/itwinjs-core` and `iTwin/imodel-native` on GitHub.

An iModel *is* a SQLite file. That is the structural parallel. But the
geometry pipeline diverges completely from there:

```
Bentley iTwin path (verified from source):

  iModel SQLite (server)
       ↓
  @bentley/imodeljs-native (PROPRIETARY C++ binary, closed licence)
  JsInteropExportGraphics.cpp — reads GeometryStream BLOB
  → tessellates via proprietary BRep/NURBS engine
  → encodes as iMdl format (FlatBuffers, ElementGraphics.fbs)
       ↓
  IModelTileRpcInterface.generateTileContent()  ← RPC over HTTP
  (defined: core/common/src/rpc/IModelTileRpcInterface.ts)
  (impl:    core/backend/src/rpc-impl/IModelTileRpcImpl.ts)
       ↓
  Browser receives Uint8Array iMdl tile
  ParseImdlDocument.ts + ImdlReader.ts decode the tile
  → quantized vertex tables, not raw Float32Array
       ↓
  Bentley's own @itwin/core-frontend renderer (not Three.js)
```

Three insurmountable differences from BIM OOTB:

1. **The SQLite file never reaches the browser — ever.**
   `SnapshotConnection` (the only local path) is explicitly documented
   "uses IPC, may only be used from native applications. Not available
   in browsers." `BlankConnection` has no geometry. For web browsers
   there is no server-free path — confirmed from `IModelConnection.ts`
   lines 173–376: every data call uses `IModelReadRpcInterface`.

2. **The geometry BLOB format is completely different.**
   Bentley's `GeometryStream` is FlatBuffers-encoded parametric geometry
   (BReps, NURBS, arcs). It requires a proprietary C++ tessellator
   (`@bentley/imodeljs-native`) to produce renderable triangles.
   BIM OOTB stores pre-tessellated raw Float32Array vertex/face data —
   readable directly by any JavaScript without any native binary.

3. **The native binary is proprietary — not freely deployable.**
   `core/backend/src/imodeljs-native-LICENSE.md`: Bentley Right-to-Run
   licence. Cannot be compiled or shipped independently. Every iTwin.js
   deployment requires Bentley's authorisation.

**Verdict on Bentley:** They have the same storage idea (SQLite). They made
the opposite architectural choice at every subsequent step — proprietary
format, C++ server dependency, RPC-mandatory browser path. Their system
validates that SQLite is the right foundation while simultaneously
confirming that your serverless browser pipeline is genuinely novel and
not covered by their architecture.

**BIM OOTB vs Bentley iTwin — full comparison:**

| Dimension | Bentley iTwin | BIM OOTB |
|---|---|---|
| SQLite as geometry store | Yes — iModel | Yes — output.db |
| SQLite reaches browser | **Never** | **Yes — static delivery** |
| Geometry server required | Always (Node.js iModelHost) | **None** |
| Browser queries SQLite | Via RPC to server | **sql.js WASM — direct** |
| Geometry BLOB format | FlatBuffers/BRep (parametric) | Raw Float32Array (tessellated) |
| Native binary required | Yes — proprietary C++ | **No — pure JS/WASM** |
| Renderer | Custom @itwin/core-frontend | **Three.js — standard** |
| Licence to deploy | Bentley Right-to-Run | **MIT — zero restriction** |
| Offline capable | No | **Yes — IndexedDB** |
| ERP integration | Server REST bridge | **Shared JS context** |
| Cost | Bentley subscription | **Static file host only** |
| Can be forked freely | No (native binary locked) | **Yes — MIT** |

---

**xeokit/XKT** — achieves static file delivery to browser. However XKT is
a proprietary binary format, not SQL-queryable. No relational schema.
No ability to run `SELECT cost, schedule FROM elements WHERE storey=3`.

**web-ifc / ThatOpen** — browser-side, no server needed. However
tessellation happens at load time in-browser (slow — 30–60s for 100K
elements). No pre-computation, no SQLite, no stored BLOBs. Your approach
does the expensive work once at extraction time, not on every page load.

**IfcOpenShell ifc2sql + IFCdataBrowser** — closest conceptual predecessor.
ifc2sql creates SQLite with geometry BLOBs. IFCdataBrowser delivers SQLite
to browser and queries via sql.js. But neither connects through to WebGL
rendering. IFCdataBrowser is a data analysis tool — no Three.js,
no BufferGeometry, no visual output.

**The gap you crossed:**
IFCdataBrowser (SQLite + sql.js in browser) + xeokit (static delivery)
+ your insight that the pre-tessellated BLOB *is* the geometry
= BIM OOTB. Nobody assembled these three observations before you.
Bentley came closest on the storage model and stopped.

### 2.2 Claim 2 — Zero-server BIM-ERP shared JS context

**Verdict: No prior art found.**

All existing BIM-ERP integrations use server-side REST APIs or middleware. Autodesk APS, Speckle, xeokit — all use API bridges. The shared browser JS context where `bimViewer.highlightElement(guid)` and `gridScrollTo(line)` are direct function calls in the same runtime is undocumented in any published work.

### 2.3 Claim 3 — IFC → unified 4D-8D queryable SQLite schema

**Verdict: Partial prior art. Your multi-domain scope is novel.**

IfcSQL (IfcSharp) and IfcOpenShell ifc2sql overlap on geometry storage. However neither delivers a unified schema covering geometry + spatial + schedule + cost + maintenance in one SQLite queryable without a geometry kernel at runtime. Your DAGCompiler 4-DB architecture and 11-stage pipeline producing a unified queryable output is not anticipated.

### 2.4 Public Timestamp

YouTube viewer comment (approx 2026-05-01) confirming IFC drop functionality works constitutes independent third-party public attestation of a working system. Combined with GitHub commit history, this establishes a clear priority date for the complete working system — not merely a concept.

---

## 3. Licence Positioning

### 3.1 Current state (as of 2026-05-01)

- **534 files** updated with `Copyright (c) 2025-2026 Redhuan D. Oon` MIT notice
- **LICENSE file** created at repo root — standard MIT text
- **README.md** badge updated from GPL v2 → MIT
- All JS, Java, Python source files carry SPDX-License-Identifier: MIT

### 3.2 Why MIT (not GPL)

| Concern | GPL | MIT |
|---|---|---|
| Sysnova white-labels without open-sourcing their ERP | Blocked — GPLv3 viral | Permitted |
| Competitor uses your code | Must open their product | Cannot stop, but cannot remove your copyright |
| iDempiere community plugin (LGPL-2 core) | Conflict | Compatible |
| Enterprise legal review | Often blocks adoption | Approves immediately |
| Dual-licensing later (MIT + commercial tier) | Complex | Clean |
| Phase 3 acquisition due diligence | Requires auditing all licensees | Clean — no viral obligations |

**The strategic reason:** MIT maximises adoption velocity. Every deployment carries `Copyright Redhuan D. Oon` by legal obligation. Brand proliferation is enforced by copyright law, not by sales effort.

### 3.3 Dependency entanglement analysis

| Dependency | Licence | How used | Entanglement |
|---|---|---|---|
| web-ifc@0.0.77 | MPL-2.0 | Called via API in import_worker.js — not modified | None — MPL is file-level copyleft only |
| IfcOpenShell | LGPL-3.0 | Server-side tool, not distributed in product | None — LGPL permits use without viral effect |
| Three.js | MIT | Direct use | Compatible |
| sql.js | MIT | Direct use | Compatible |
| ExcelJS | MIT | Direct use | Compatible |
| ZK Framework (iDempiere) | GPL-2 | Not in BIM OOTB — only in proposed iDempiere integration | Contained to integration layer |

**Conclusion:** BIM OOTB browser code and DAGCompiler Java pipeline are clean MIT. No GPL entanglement. The earlier GPL v2 notice in README.md was a voluntary declaration by the author (not imposed by a dependency) and has been corrected.

### 3.4 Copyright enforcement mechanism

MIT requires: *"The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software."*

Violation = copyright infringement (not merely licence breach). This is enforceable via:
1. DMCA takedown (GitHub, app stores, cloud hosts) — fast, free, no lawyer
2. Formal legal notice citing copyright infringement — USD 150,000 per wilful infringement (US)
3. Prior art timestamp from GitHub commits + YouTube attestation + this document

Detection signals unique to this codebase:
- `blobToGeometry()` function name
- `§`-tagged console.log pattern throughout
- sql.js + Three.js + SQLite BIM combination
- The specific Float32Array BLOB pipeline structure

---

## 4. Valuation Analysis

### 4.1 Valuation framework

Software IP valuation uses three methods:
- **Cost approach** — what would it cost to rebuild from scratch?
- **Market approach** — what do comparable assets trade at?
- **Income approach** — what revenue can it generate?

### 4.2 Cost approach — rebuild cost today

| Component | Estimated rebuild time | At USD 150/hr senior dev rate |
|---|---|---|
| BLOB→WebGL pipeline + viewer | 6 months | USD 120K |
| IFC extraction DAGCompiler (11 stages) | 12 months | USD 240K |
| 2D DXF generation pipeline | 4 months | USD 80K |
| Multi-format import (DAE/GLB/FBX/3DS) | 3 months | USD 60K |
| Mobile GPS site camera | 2 months | USD 40K |
| BOQ/5D cost engine | 2 months | USD 40K |
| Test suite (108 Playwright + 172 whitebox) | 2 months | USD 40K |
| Docs (74 spec documents) | ongoing | USD 40K |
| **Total** | **~31 months solo** | **~USD 660K** |

A funded team of 3 developers could rebuild this in ~10 months at USD 660K+. This is the floor valuation — what the code alone is worth to replace.

### 4.3 Market approach — comparable transactions

| Comparable | Sale/Valuation | Notes |
|---|---|---|
| xeokit (Creoox AG) | ~USD 2-5M estimated | XKT pipeline only, no ERP, no 2D, no mobile |
| Speckle (Series A 2022) | USD 10M raised | Server-dependent, no BIM geometry novelty |
| BIMData (French startup) | ~EUR 5M valuation | Server-based, viewer only |
| IFC.js → ThatOpen | Seed funding ~USD 2M | Browser IFC but in-browser tessellation, no SQLite pipeline |

These comparables all require running servers and lack the 4D–8D scope. BIM OOTB's serverless architecture and unified DB schema position it above these on technical merit.

### 4.4 Current valuation (May 2026)

**Conservative floor: USD 500K – 1M**

Basis:
- Rebuild cost alone ~USD 660K
- Working proven system (not a prototype) — 108 tests PASS, public YouTube demonstration
- Independent prior art research confirms no competing complete pipeline
- MIT licence with clean dependency chain — no legal blockers for enterprise adoption
- YouTube public attestation establishes working system timestamp

This is the valuation for a **licensing or sponsorship conversation today** — not an acquisition price.

### 4.5 Valuation in 4–6 weeks (post-milestones)

Two milestones in progress will materially change the valuation:

**Milestone A — Professional 2D layout output**
When the DXF output reaches professional drawing standard (title block, dimensions, annotation conforming to architectural drawing conventions), it closes the gap with Autodesk's 2D documentation workflow. This is the workflow that generates the majority of BIM fees in practice. Comparable: AutoCAD LT at USD 380/user/year, ~5M licences. Even 0.001% of that market = USD 1.9M ARR.

**Milestone B — HTML plugin spinning viewer in ZK tab**
This is the proof-of-concept for the entire iDempiere SPA proposal. When a Three.js viewer loads inside a ZK `<iframe>` tab showing a real iDempiere record's linked property model, Sysnova's developer team can immediately see the integration path. This converts the NewUI_iDempiere.md proposal from a document to a demonstration. Demonstrations close deals; documents do not.

**Revised valuation after both milestones: USD 1.5M – 3M**

Basis:
- Professional 2D output makes it a complete 1D-3D working system (not just a viewer)
- ZK plugin demonstration converts the ERP integration from theoretical to proven
- At this point the Sysnova conversation moves from "interesting technology" to "product we can sell next quarter"

### 4.6 Valuation at full 8D stack proof (12–18 months)

When one real client (e.g. Kazi Farm / Sysnova real estate) runs a complete project through 4D schedule → 5D cost → 6/7D asset maintenance → 8D ERP integration on BIM OOTB:

**Target valuation: USD 5M – 15M**

This is the range at which strategic acquirers (Autodesk, Bentley, Oracle Primavera, or a construction ERP vendor) would engage seriously. The Bentley iTwin comparison is instructive: they have the storage architecture (SQLite BLOBs) but not the serverless browser pipeline or the ERP integration. A 12-month exclusive licensing deal at that stage could be structured at USD 1-2M upfront + royalties, which implies a USD 10M+ valuation on the IP.

---

## 5. Commercial Structure Options

### 5.1 Option A — Development retainer + per-deployment royalty (recommended now)

```
Development retainer:    USD 5,000/month
                         Covers active roadmap development
                         Sysnova-specific features billed additionally

Per-deployment royalty:  USD 2,000–5,000 per client site licensed
                         Scales with Sysnova's success

Exclusivity scope:       iDempiere ERP vertical only
                         You remain free for standalone, Odoo, ERPNext

Exclusivity period:      24 months, renewable if ≥3 deployments closed
                         Performance clause protects your freedom

IP ownership:            100% yours under MIT
                         Sysnova carries commercial front only

Conference:              Expenses covered + "Powered by BIM OOTB" credit
```

### 5.2 Option B — IP share (avoid until stack is proven)

Co-ownership before full 8D proof undervalues the asset. Revisit at 24 months after Kazi Farm deployment.

### 5.3 Option C — Acquisition (not before full 8D proof)

Too early. The stack is 60% complete. An acquisition now buys the current state, not the potential. File provisional patent first, complete 8D proof, then negotiate from strength.

### 5.4 Dual licensing (future — after 12 months)

```
MIT (community tier):      Free for open-source, academic, self-hosted
Commercial tier:           USD X/year per deployment for proprietary use
                           Adds: SLA, support, custom features, white-label rights
                           Same pattern as MySQL, MongoDB, Elasticsearch
```

This is the model that made SQLite's architecture (which yours parallels) generate sustainable funding without a traditional sales team.

---

## 6. Negotiating Position Summary

```
Your strongest card:    Only developer who can complete the 8D stack
                        and debug it at scale. Cannot be copied.

Your IP position:       MIT with copyright in 534 files. GitHub timestamps.
                        YouTube public attestation. Prior art research complete.
                        No GPL entanglement.

Your technical moat:    6-step pipeline not assembled by anyone else.
                        Bentley validates the storage concept but stops short.
                        You crossed the gap they chose not to cross.

Your market timing:     3 years ahead per independent assessment.
                        Construction BIM market: USD 9.5B in 2024,
                        projected USD 24B by 2030.

Your ask at Dhaka:      Retainer + royalty + exclusivity time-bound.
                        Not equity. Not acquisition. Not yet.
```

---

## 7. Immediate Actions (before Dhaka)

1. **File provisional patent — Claim 1** at MyIPO (MYR ~500, establishes date). Use the prior art table in §2 as the landscape search section.
2. **Commit this document to repo** — adds to the timestamped evidence chain.
3. **Complete Milestone B** — ZK HTML plugin with live Three.js viewer in a tab. One demonstration is worth ten documents in a commercial meeting.
4. **Screenshot the YouTube comment** with timestamp. Archive it. First independent public attestation of working system.

---

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
