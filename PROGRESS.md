# PROGRESS — Current Development State

**Last updated:** 2026-02-20
**Current phase:** Phase RM-10 COMPLETE — Strategic Pause
**Baseline:** ALL 4 BUILDINGS PASS via `mvn test` — SH 55, DX 1085, TB-LKTN 69, Terminal ~51088
**Tests:** 58 total (41 contract + 4 registry + 13 metadata)

---

## Phase History (Summary)

| Phase | Status | What |
|-------|--------|------|
| RM-1 | ✅ DONE | 5 relational tables + populated rules (SH 55, DX 1085) |
| RM-2 | ✅ DONE | RelationalResolver shadow validation (SH 100%, DX 100%) |
| RM-3 | ✅ DONE | PlacementAD cutover — RELATIONAL mode live |
| RM-4 | ✅ DONE | TB-LKTN 58→69 elements, LOD400 library, GeometryIntegrityChecker |
| RM-5 | DEFERRED | Flat table → computed cache (non-blocking, data still valid) |
| RM-6 | ✅ DONE | Plumbing system + CONNECTS_TO topology proofs |
| RM-7 | ✅ DONE | Visual defect audit: geometry_map storey, familyRef, P19-P21 |
| RM-8 | ✅ DONE | Registry-driven pipeline — one engine, N buildings from metadata |
| RM-9 | ✅ DONE | rotation_rule authority: 3-table contract, FixturePlacer hardened |
| RM-10 | ✅ DONE | Window depth-cap, GIC LOD_ check, furniture scaling, P22/P23 proofs |

---

## Phase RM-10 Detail (2026-02-20)

### Problems Solved
1. **Window opening-axis protrusion** — MeshBinder `isNS` detection was wrong for EW-orientation windows. Fixed via mesh-proximity comparison (`errNoRot` vs `errRot`). Wall-thickness axis (`wallThick`) now correctly `bboxW` when rotation applied, `bboxD` when not. `scaleY` capped to prevent frame protrusion through wall.

2. **GIC LOD_ blind spot** — GeometryIntegrityChecker previously only checked `GEO_` (parametric box) geometry. MeshBinder's world-coord `LOD_` geometry was entirely exempt. Fix: check both prefixes. `isParametric` renamed `isWorldCoords`.

3. **Furniture mesh overflow** — `MEPWriter.writeFixture()` called unscaled `transformAndWriteGeometry()` for `IfcFurniture`. Library mesh ≠ product dims → mesh overflowed bbox. Fix: `transformAndWriteGeometryScaled()` with computed `bbox/mesh` scale factors. Z aligned via `tz = z - bounds[4] * sZ`.

4. **P22 OPENING_MESH_IN_BBOX (CRITICAL)** — Post-write proof: reads vertex blobs from `base_geometries` for IfcDoor/IfcWindow, verifies no vertex exceeds rtree bounds by >1mm. Gates the build.

5. **P23 DRAIN_CORNER_ALIGNMENT (advisory)** — Post-write proof: checks orthogonal IfcFlowSegment pairs at same Z share a corner within 5mm. TB-LKTN: 6 violations (drain U-shape). Duplex: 364 advisory (MEP flow fittings, expected). Advisory only.

### Files Changed
- `MeshBinder.java` — isNS fix, wallThick axis, scaleY depth-cap, MIN_SCALE validation bypass
- `GeometryIntegrityChecker.java` — LOD_ blind spot fixed, isOpening flag added
- `MEPWriter.java` — furniture split-branch (scaled vs unscaled), `geoHash = null` init fix
- `PlacementProver.java` — P22/P23 added, `proveFromDB()` extended, `isCritical()` updated
- `DoorWindowLibraryMapper.java` — `PRAGMA foreign_keys = ON`
- `StairLibraryMapper.java` — `PRAGMA foreign_keys = ON`

### Migration Script
`migration/migration_DX_corner_windows.sql` — Duplex corner windows 1126-1133 (out-of-range FRACTION → ABSOLUTE)

### Verification
```
mvn test -pl DAGCompiler
Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## Strategic Pause — The Last Mile Problem

See `docs/LAST_MILE_PROBLEM.md` for full context.

**Root asymmetry**: Replication (SH/DX) works because reference IFC IS the oracle. Generation (TB-LKTN) fails because the compiler IS the authority — every coordinate must be computed from rules, with no reference to validate against.

**Proposed architecture** (not yet implemented):
- **ProvenElement** — PO.save() equivalent: every element at construction carries a `PlacementProof` sealed type that verifies itself at build time. No proof = no element.
- **CRD (Construction Rule Dictionary)** — `crd_rule` tables encoding UBBL/JKR placement rules (toilet back to wall, basin adjacent, bed headboard to wall, window sill 900mm, etc.). Replaces ad-hoc Java resolver logic.

**Decision before next session**: Do NOT add more ad-hoc TB-LKTN placement fixes. They don't compound — they regress. CRD must come first.

**Bridge strategy**: Mine Rosetta Stone buildings (SH/DX) for CRD seed data — their extracted placements are proven correct and can seed `crd_rule` rows.

---

## Known Debt (Explicit Thresholds)

| Item | Count | Severity | Gate? |
|------|-------|----------|-------|
| Terminal IfcReinforcingBar GIC failures | 8 | Advisory | No |
| Duplex P23 drain corners | 364 | Advisory (MEP flow fittings, not drains) | No |
| TB-LKTN P23 drain corners | 6 | Advisory (actual drain U-shape defect) | No |
| TB-LKTN furniture alignment | visible | Advisory | No |
| TB-LKTN roof porch | missing | Advisory | No |

---

## What's Next

**Phase RM-11: CRD Bootstrap** — before any more TB-LKTN generative work.

1. Design `crd_rule` schema (space_type, element_type, constraint_type, value, citation)
2. Mine SH/DX placements → seed `crd_rule` rows for bathroom, bedroom, kitchen, living
3. Implement `CRDValidator` as CompilerStage
4. Wire TB-LKTN elements through CRD before falling back to ad-hoc resolver

OR (if CRD deferred):

**Phase RM-11 alt: ProvenElement gate** — add mandatory `PlacementProof` to `BoundElement` construction path, with proof types: `WallAligned`, `FractionAlongWall`, `RoomCentroid`, `GridIntersection`, `BOMChildOffset`, `ExtractedReference`.

**Priority decision**: Read `docs/LAST_MILE_PROBLEM.md` section 6 + session assessment before choosing.
