# Prior-Art Record — Event-Sourced Geometry as a Fold over a Signed Operation Log

> **Defensive publication / authorship record.** First published **2026-06-18** by the BIM OOTB project
> (red1oon). This page is a dated, public, enabling disclosure of the architecture described below so that
> it stands as prior art. It is the geometry-side companion to the **[Feature Comparison](FeatureComparison.md)**
> (BIM viewer) and **[Migrate & Compare (ERP)](MigrateComparisonPaper.md)** (the WASM event-sourced browser ERP).

## What is disclosed

A single **cryptographically hash-chained operation log** (an append-only, tamper-evident event log — "git
for data") serving as the **sole source of truth for both** (a) ERP transactional records **and** (b) BIM
**parametric geometry**, executed **entirely in the browser client** with no server, where **geometry is not
stored** but is a **deterministic fold** — a pure replay of the logged feature operations through a reused
boundary-representation (B-rep) geometry kernel.

In this architecture a parametric feature (e.g. a wall as an extruded profile, an opening as a boolean cut) is
recorded as **one signed row in the operation log**: an operation type plus its parameters. The rendered solid
is obtained by **replaying** that row through the kernel. Undo, redo, history scrubbing, branching, design
variants ("multiverse"), distributed sync, and audit are therefore **not separate features of the modeller** —
they are intrinsic properties of the log, shared identically with the ERP records that ride the same log.

## Precise scope of the claim (what is, and is NOT, claimed novel)

We **do not** claim first authorship of:

- **Event-sourced / replayable CAD architecture in general** — this is a recognised pattern (see the
  event-sourced collaborative-CAD literature) and is partially embodied by commercial cloud CAD.
- **Cloud parametric feature history with branch & merge** — shipped and **patented** by others
  (Onshape / PTC). Their implementation is server-authoritative cloud.
- **Browser-resident OCCT/B-rep CAD** — demonstrated by Chili3D and ifc5cad (both AGPL).
- **Model versioning / immutable audit trails of committed states** — demonstrated by Speckle.

We **record as prior art**, as of the date above, the **specific combination** none of the above embodies:

> A **single tamper-evident, hash-chained operation log** that is the **sole source of truth for both ERP
> records and BIM parametric geometry**, run **fully client-side**, in which **geometry is derived as a
> deterministic fold (replay) over the log rather than stored** — demonstrated by a working reference
> implementation (below).

## Feature comparison — where this sits among prior systems

| Capability | **BIM OOTB (this work)** | Onshape | Chili3D | ifc5cad | Speckle | Bonsai / IfcOpenShell |
|---|:--:|:--:|:--:|:--:|:--:|:--:|
| Parametric feature history | ✅ | ✅ | ✅ (in-memory undo) | ✅ (in-memory undo) | — | ✅ (in-memory undo) |
| Geometry **derived as a fold** (replay), not stored | ✅ | ✅ (server) | ❌ | ❌ | ❌ | ❌ |
| **Cryptographically signed / tamper-evident** log | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **One substrate for ERP records *and* geometry** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Runs **fully client-side** (no server) | ✅ | ❌ | ✅ | ✅ | partial | desktop |
| Branch / variant / distributed history | ✅ | ✅ (patented) | ❌ | ❌ | ✅ (versions) | ❌ |
| Reuses an open B-rep kernel | ✅ (OCCT) | ❌ (proprietary) | ✅ (OCCT) | ✅ (OCCT) | n/a | ✅ (OCCT) |

The distinguishing intersection — **signed log + geometry-as-fold + unified ERP+BIM substrate + fully
client-side** — is held by no prior system in the row above.

## Reference implementation (enabling disclosure)

The mechanism is demonstrated, not merely asserted. The reference reuses the open-source **occt-wasm** kernel
(OpenCASCADE compiled to WebAssembly) as a stateless pure function and our own signed operation-log engine as
the feature tree.

A feature is recorded as one log row, for example an extruded-area solid (the dominant BIM geometry form):

```
op_type    = "GEOM_EXTRUDE"
parameters = { "profile": { "w": 4, "h": 0.2 }, "dir": [0, 0, 3] }
```

Replaying that row builds a rectangular profile and extrudes it; an opening is a second row whose replay cuts a
boolean void from the wall. The witness (`W-KERNEL-FOLD`, 2026-06-18) records that replaying a serialised log
row reproduces **byte-identical** geometry, and that boolean openings fold deterministically:

```
init ok                                          (kernel loads in-browser)
live    tris=12  bbox=[0,0,0,4,0.2,3]  cs=1782029157     extrude → wall
replay                                 cs=1782029157  identical=Y   ← fold is deterministic
opening tris=32                        cs=3687298821  deterministic=Y  changedVsWall=Y
```

`cs` is a checksum of the tessellated vertex positions; identical checksum across an independent replay
establishes that the geometry is fully determined by the log row — i.e. geometry is a fold over the log.

### Engineering facts established by the reference

- The kernel is **single-threaded** (no `SharedArrayBuffer`), so it requires **no cross-origin isolation**
  (no COOP/COEP) and runs on ordinary static hosting.
- Geometry replay is **deterministic** for both extrusion and boolean operations under fixed tessellation.
- The reused kernel is embedded as a **separately-loadable WebAssembly module**, preserving the open-source
  kernel's licence terms while the surrounding application remains independently licensed.

## Provenance

This disclosure is timestamped by its publication to the project's public documentation site and by the
project's version-control history. It is intended to establish the date and authorship of the combination
described, and to serve as prior art against later claims to that combination.

*BIM OOTB — red1oon — first published 2026-06-18.*
