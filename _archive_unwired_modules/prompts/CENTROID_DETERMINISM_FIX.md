<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# CENTROID DETERMINISM — element `center_x/y/z` is vertex-mean (non-deterministic); fix = bbox-center

```
# ⚠ DO NOT REMOVE
SCOPE: root-caused fix spec for the "browser-importer wall-transform parity (46 vs 54 rooms)" gap
in ROOM_INJECTION_CONSOLIDATED_REVIEW.md §GAPS. DIAGNOSIS COMPLETE + witnessed (2026-07-17). Fix
NOT yet implemented — it is a pipeline-wide data-model change (touches every extracted DB) and
needs its own fix+full-regression session, not a drive-by. PUSH PAUSE: when built, commit locally,
verify on localhost, no push/PR. DB rule: re-extracted binaries go via OCI, incremental fixes to
already-distributed DBs go via self-heal patch — never a .db in git. Read the log after every run.
```

## The bug (PRIME-DIRECTIVE violation — a core field is non-deterministic)
`elements_meta`/`element_transforms` `center_x/y/z` is computed as the **arithmetic MEAN of every
tessellated vertex** — in BOTH pipeline paths, byte-identical formula:
- **Extraction:** `scripts/extractIFC2DB.js:422-428` — `cx = sumX / vertCount` over all verts.
- **Browser import:** `~/bim-ootb/viewer/import_worker.js:515-524` — same `cx = sumX / vertCount`.

Vertex-mean depends on **triangulation density**, which is NOT a geometric invariant. web-ifc's
tessellation of boolean-subtracted / swept ARC/STR solids (slabs, coverings, walls-with-openings,
beams) is not bit-stable between the **browser wasm** and the **node wasm** build of web-ifc 0.0.77
(same engine, same OpenModel flags `COORDINATE_TO_ORIGIN:false, USE_FAST_BOOLS:true,
OPTIMIZE_PROFILES:true`). So the stored center drifts per element — proportional to element size —
while the **bbox (min/max) is bit-identical** (extent is an invariant; mean is not).

## Witness (decisive whitebox A/B — same `TerminalMerged.ifc`, offline extract vs browser Drop)
Joined by identical GUID across 4790 elements (diagnosis session 2026-07-17):
- center `dx` differs up to **±1.31 m**; **630/4790** elements > 0.01 m (356 in z).
- Divergent classes are exactly the big planar/swept ARC solids: IfcSlab (372, max 1.31 m),
  IfcCovering (53), **IfcWall (41)**. Small MEP/PLB/FP/ELEC components (stable tessellation) stay
  rigid at the constant tile offset.
- **`bbox_x/y/z` delta = 0** across ALL elements — only the vertex-mean center moves.
- Against the shipped `Terminal_extracted.db` (prefix-stripped join): **31 of 333 walls shift
  > 0.5 m** — enough to break room-walker wall-enclosure → **45 vs 54 rooms**.

This RETIRES the earlier "pure translation" reading (a statistical-aggregate artifact; the
disjoint-GUID appearance was just the `T0_Terminal_` sandbox prefix) AND the misdiagnosed
"walker coordinate-phase sensitivity" (that was a separate, already-fixed bug — see
`Viewer/FLY_TOUR_CORRIDOR_GRAPH.md` CORRECTION 2026-07-17; #832 §LOCAL-FRAME made the walker
14/14 translation-invariant, backported to `fable/meshdb-livewire` @ `c44ade97d`).

## The fix (small in code, wide in blast radius)
Replace vertex-mean center with **tessellation-invariant bbox-center = (min+max)/2** of the verts,
in BOTH files. The min/max are ALREADY computed for `bbox_x/y/z`, so it is a few lines each.
CRITICAL: the same bbox-center must be used as BOTH (a) the stored `center_*` AND (b) the geometry
re-centering origin (`positions[vi] = vert - center`), so world position `= center + (vert-center)
= vert` is preserved. Using bbox-center for storage but vertex-mean for re-centering would displace
all geometry — do both together.
- `scripts/extractIFC2DB.js:422-433` (compute bbox-center, store it, re-center by it)
- `~/bim-ootb/viewer/import_worker.js:515-528` (identical change — keep the two byte-parallel)

After: imported == extracted per element → room-walker gives the SAME count in both → imported
buildings fly.

## Why this is a dedicated fix+regression session, not a drive-by
- **Every extracted DB changes.** `center_*` shifts for every asymmetric element in every building;
  the extraction change only takes effect on RE-EXTRACTION. All `deploy/buildings/*_extracted.db`
  must be regenerated (via `extract_per_building.py` / the sandbox build), then redistributed per
  the DB rule (OCI for full binaries; self-heal patch for already-shipped ones).
- **Room counts may shift building-wide** — bbox-center vs mean differ for L-shaped/tapered walls.
  Full regression required: `build/witness_room_walker_parity.js` (py==js still holds — change
  both), the phase sweep (`phase_witness/`), and a per-building room-count before/after on the
  whole fleet (Duplex/Clinic/HHS/Hospital/Garage/Terminal/LTU_AHouse). Confirm imported==extracted
  on Terminal AND no unexplained count regressions elsewhere.
- **Anything relying on center-as-visual-centroid** (label placement, camera framing, walker
  §STOREY-Z which reads wall `center_z`) must be spot-checked — bbox-center-z differs from mean-z
  for vertically-asymmetric elements.
- **Architecture-scope:** this redefines a fundamental data-model field across the whole WHAT/HOW/
  WHERE pipeline — flag to the architect before shipping, don't self-authorize the semantic change.

## DONE WHEN
Both files use bbox-center (stored + re-center origin, byte-parallel); all `*_extracted.db`
re-extracted; `witness_room_walker_parity.js` green (py==js); a §-witnessed per-GUID A/B shows
imported center == extracted center (delta 0) on Terminal; imported Terminal compiles the same
room count as extracted (54==54) and flies; fleet room-count regression sweep shows no unexplained
change; redistribution done per DB rule. Architect sign-off on the center-semantic change recorded.
