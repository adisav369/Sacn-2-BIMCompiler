# Chair-Facing Root Cause + Java Placement Port Ledger

# ⚠ DO NOT REMOVE — scope: why dropped buildings showed dining chairs "facing one way", traced by the
# RosettaStone method (reproduce → compare to the real extraction → white-box the drift → fix the SOURCE),
# and the standing account of what the JS drop engine ports from the Java compiler. Read the log after every run.
# Witnesses: W-ROTATION-ROSETTA-SH (facing vs real extraction) + W-DAGEVU-DROP (structural baseline). 2026-06-24.

## The headline (no invented angle)
The chairs faced one way because the **per-instance yaw was dropped during BOM factorization**, NOT because the
JS engine lacked facing logic. Found by comparing the reproduction to the real IFC extraction, fixed at the
source, and proven by a rotation RosettaStone going to **Δ = 0** — not by a hardcoded offset.

> An earlier attempt hardcoded `rot = bearing + 270°` (a "radial-seating verb"). That was **invented data** — it
> injects the answer into the reproduction, which defeats the round-trip (you can't detect drift once you've
> baked it in). It was reverted from dev and from production (bim-ootb #510, sw v718). The constant is gone.

## The method, step by step
1. **Black box (RosettaStone).** Position already round-trips at 0.000mm (`rosetta_canvas_sh.js`). The ground-truth
   extraction (`element_transforms`) stores **shared canonical meshes + per-instance centers** — repeated furniture
   shares one mesh (46 meshes / 55 elements), so rotation must be a per-instance field.
2. **Re-extract.** The current extractor `DAGCompiler/python/extractIFCtoDB.py` **does** capture yaw — it reads the
   IfcLocalPlacement chain → Euler `rot_z` → `element_transforms.rotation_z`. The stale rosetta DB predated that
   column. A fresh extraction of `Ifc4_SampleHouse.ifc` gives the real dining yaws: **{0, 180, 0, 180, 90, −90}**
   (two facing columns + two facing ends) — chairs that genuinely face their table.
3. **White box (where the drift enters).** The catalog drop flattened the 4 side chairs to rot=0. Traced through:
   catalog `rotDeg` ← BOM `rotation_rule` ← `VerbFactorizer`. The 4 side chairs were grouped into ONE
   `verb_ref=TILE:2:2` line, and `VerbFactorizer` stores only **`first.orientation()`** for a factored line
   (`VerbFactorizer.java:165`). `VerbDetector.detectTile` groups by **position only** — it never checks rotation —
   so a 2×2 of chairs at `{0,π,0,π}` collapsed to the first chair's 0, silently dropping the real π on the others.
4. **Fix the source (non-invent).** Added an **orientation-uniformity guard** in `VerbFactorizer.doFactorize`
   (mirrors the existing material-uniformity guard): a group whose per-instance yaw differs beyond tolerance is NOT
   factorized → it falls through to the per-instance path, which writes `e.orientation()` per element. The real
   captured yaw flows verbatim: extraction → BOM (4 separate lines, rot {0,0,180,180}) → catalog → drop.
5. **Prove (RosettaStone to zero).** `scripts/witness_rotation_rosetta_sh.js` matches every dropped furniture leaf
   to the real extraction element (same class + table-relative position) and compares yaw. **Δ-spread = 0°** — the
   reproduction reproduces the extraction's facing exactly, with no constant anywhere.

## Java → JS placement port status (unchanged truths)
| Java mechanism | JS status |
|---|---|
| `parseRotation(rotation_rule)` numeric radians (PlacementCollectorVisitor:1901) | ✅ ported — `expandAssembly` `rotDeg` |
| `rotationStack` cascade `cumRot+lineRot` (350,385) | ✅ ported — recursive `wrot = pr + ch.rotDeg` |
| MIRROR:X/Y/Z reflection (1428) | ✅ ported — `expandAssembly` mirror negation |
| `facingDirection(placement_rule)` — MEP **device** anchor table only (1019, §12g GAP-4) | n/a to the drop — fires only on generative devices with a `placement_rule`; the drop catalog has none. It never faces furniture (no table-ring case). |
| host-relative opening facing | ⚠ JS `_inheritHostRotation`: SH/DX openings carry `rotation_rule=0` + no host link, so Java renders them flat too; JS geometrically recovers the host wall and copies its yaw (declared enhancement, provenance-logged). |

## The one real gap that remains (honest)
The **standalone `SH_DINING_SET`** (a synthetic convenience template in `library/archive/BOM.db`, `rotation_rule=0`)
has **no real-building ground truth**, so its chairs cannot be RosettaStone-verified and still face one way if
dropped. It is logged informationally in W-DAGEVU-DROP, **not asserted**. Fixing it would mean either sourcing it
from a real building or deleting it — not inventing rotations.

## Artifacts
- Source fix: `IFCtoBOM/.../VerbFactorizer.java` (orientation-uniformity guard + `orientationsUniform`).
- Regenerated: `library/SH_BOM.db` (LFS-local; the SCRIPT path is the durable artifact), `dagevu_catalog.json`.
- Witness: `scripts/witness_rotation_rosetta_sh.js` (W-ROTATION-ROSETTA-SH, GREEN Δ=0 vs real extraction).
- Reverted: the `270°` radial verb (dev engine + bim-ootb #510, sw v718).
