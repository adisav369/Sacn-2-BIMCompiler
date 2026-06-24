# Java → JS Placement / Facing Port Ledger

# ⚠ DO NOT REMOVE — scope: every rotation/facing/placement mechanism in the Java compiler
# (`PlacementCollectorVisitor.java`) and whether the JS drop engine (`deploy/dev/bonsai_library.js`,
# the DAGeVu canvas placer) ports it. Read the log after every run. Honour until DONE.
# Witness: `scripts/witness_dagevu_drop.js` (W-DAGEVU-DROP). Live 2026-06-24.

This file answers the standing question — *"why doesn't our JS do what the Java did perfectly, and where are we
NOT?"* — with a line-by-line accounting. The honest headline:

> The Java compiler is **not** a universal facing oracle. For furniture it faithfully renders whatever
> `rotation_rule` the BOM carries — nothing more. Where that field is `0`/empty (the archive synthetic dining set,
> the stale building seats), **Java places the chairs facing one way too.** So the "chairs face one way" bug is a
> **data / missing-verb** gap that Java shares — not a piece of Java facing logic we failed to port.

## Mechanisms in `PlacementCollectorVisitor.java`

| # | Java mechanism (file:line) | What it does | JS status |
|---|---|---|---|
| 1 | `parseRotation(MBOMLine)` (1901-1914) | BOM line `rotation_rule` numeric string → radians; `MIRROR:*`→0; non-numeric→0 (logged) | ✅ **PORTED** — `expandAssembly` reads each child's `rotDeg` (= `parse_rot(rotation_rule)`), same fallback semantics |
| 2 | `rotationStack` cascade (350, 385-387) `newCumRot = cumRot + lineRot`, pushed per sub-assembly | Parent rotation accumulates onto children's facing **and** rotates their (dx,dy), recursively | ✅ **PORTED** — `expandAssembly` `wrot = (pr + ch.rotDeg)`, recurses with it; (dx,dy) rotated by `cumRot` |
| 3 | MIRROR:X/Y/Z (1428-1430, parseMirror 1916+) | `MIRROR:X ≡ rot=π`, negates child (dx,dy), propagates to sub-tree | ✅ **PORTED** — `expandAssembly` `pm` mirror axis negates dx,dy and inherits to children |
| 4 | `facingDirection(placementRule)` (1019-1038, §12g GAP-4) | Generative **device** anchor face → yaw: `WALL_BACK/COUNTER_BACK→0`, `WALL_ENTRY→π`, side `WALL_*/COUNTER_*→−π/2`, CEILING/FLOOR→0 | ✅ **PORTED (faithful, DORMANT)** — `_facingDirection()` 1:1 in degrees. The post-compile drop catalog carries **no** `placement_rule` on its leaves, so there is nothing to act on today; kept verbatim for the day anchor-ruled leaves are dropped |
| 5 | `standoffOffset(hostSurface)` (1040+) | Device sits 5mm off wall / 50mm pendant gap / 0 on floor | ❌ **NOT PORTED** — sub-cm cosmetic standoff for generative MEP only; not visible at drop LOD. *Deliberately skipped.* |
| 6 | `MEPDevicePlacer` / END_JOIN / collision shifts (767, 471-, ROOM_DONE 1014) | Compile-time generative MEP routing + clash resolution; calls #4 | ❌ **NOT PORTED** — the drop consumes **post-compile** catalog geometry (devices already placed); generative routing is a compile concern, not a drop concern. *Deliberately out of scope.* |

## JS-BEYOND-JAVA (enhancements — Java does NOT do these; declared, not hidden)

| Mechanism | Why it exists | Honest status |
|---|---|---|
| `_inheritHostRotation` (openings) | SH/DX BOM dropped the host link (`host_element_ref` 100% empty) **and** opening `rotation_rule=0` → Java renders doors/windows **flat at rot=0**. JS geometrically recovers the embedding wall at placement time and copies its rotation (EN BLOC). | ⚠ **ENHANCEMENT** — closes a gap **Java also has** (Java would place them flat). Geometric, provenance-logged, unresolved openings kept honest at rot=0. |
| `_faceRingChairs` (radial seating verb) | Java has **no seat facer**; it renders `rotation_rule`, which is `0` for the archive dining set and stale building seats → chairs face one way in Java too. PRIME RULE: *compute positions via verbs, never invent.* | ⚠ **ENHANCEMENT** — a deterministic construction verb: `rot = bearing(table→seat) + 270°`. The **270° offset is PINNED from the building's own correctly-faced seats** (those carrying real IFC yaw all show `Δ = rot − bearing = 270°`); the verb reproduces them to ~0.003° (idempotent), proving the offset is the real mesh convention, not a guess. Only a genuine ring (≥3 chairs near one table) is faced; lone armchairs keep their own facing. |

## The data truth (why #1-#3 being ported is not enough on its own)

`SH_DINING_SET` assembly lines (deploy/dev/dagevu_catalog.json) — **every child `rotDeg=0`**:
```
ref=ROLE__TABLE…  rotDeg=0 ;  ref=ROLE__CHAIR_A…F  rotDeg=0   (orientation empty)
```
A perfect port of #1-#3 renders this exactly as Java does: all seats at rot=0, all facing one way. The fix is the
missing **verb** (#`_faceRingChairs`), supplying facing the data lacks — derived from real positions + a constant
extracted from real correct data. This is the principled, non-invent path, not a heuristic patch.

## Witness

`W-DAGEVU-DROP` (`scripts/witness_dagevu_drop.js`) now computes the **sharp, convention-free facing invariant** —
a seated ring faces its table iff `Δ = rot − bearing(table→seat)` is constant (small circular spread). It went
**RED 18/20** before the verb (SH_DINING_SET spread 156.7°, building dining 249.3°) and **GREEN 20/20** after
(both spreads 0°). The old tautological "every leaf has a finite rotation" check and the self-excusing
"`{0}°` is the canonical template" prose were deleted — they were the GIGO that let an all-one-way set pass.
