#!/usr/bin/env python3
"""
compile_rooms.py — COMPILE rooms from wall/door enclosure (deterministic, not invented).

Per storey: rasterize wall + door footprints into a 2D plan grid, flood-fill the exterior
from the border, and treat each connected pocket of free space that the exterior cannot reach
as a ROOM (enclosed by walls). Output = spatial_structure IfcSpace rows (guid/name/parent +
center_x/y/z, size_x/y/z) + rel_contained_in_space (elements whose XY centre falls in a room).

This is COMPILE, not invent: every room is a region enclosed by REAL wall geometry. guid/name
are deterministic labels for the computed cell. Geometry tables are never touched.

Usage:
  compile_rooms.py <db>            # DRY: print detected rooms per storey, write nothing
  compile_rooms.py <db> --write    # inject spatial_structure + rel_contained_in_space
"""
import sqlite3, sys, math

RES = 0.20          # grid cell size (m)
MIN_AREA = 4.0      # m^2 — drop slivers / wall cavities
MAX_AREA_ABS = 150.0  # m^2 — drop exterior-leak blobs (a real room is rarely bigger)
MAX_AREA_FRAC = 0.92  # also drop anything ~the whole storey plan
SEAL = 2            # dilate walls this many cells (×RES) to close hairline corner/door gaps
WALL_LIKE = ("IfcWall%", "IfcDoor%", "IfcCurtainWall%", "IfcColumn%", "IfcWindow%")
# §STAIR-EXCLUDE: a stairwell is a wall-enclosed pocket, so the flood-fill flags it as a "room".
# It is circulation, NOT a room. Reject any compiled pocket that a stair footprint substantially
# overlaps. IfcStair% LIKE also covers IfcStairFlight. (User: "staircase is also marked as room".)
STAIR_LIKE = ("IfcStair%", "IfcRamp%")
STAIR_OVERLAP_REJECT = 0.35   # drop a pocket if a stair footprint covers ≥35% of its area
# §DOOR-RESCUE (abstract rule, not a fitted band): the definition of "room" is architectural, not a
# size threshold — an enclosed pocket is a room IFF it has a DOOR (how a person enters/exits it). A
# wall cavity, duct or structural void never has one. MIN_AREA alone is a blunt proxy for this that
# only works by accident when small rooms happen to be rare; it wrongly drops real small rooms
# (toilets, risers, store/utility closets) that a strict area cutoff can't tell apart from noise.
# So below MIN_AREA, door presence — not a second area number picked by eyeballing any one building —
# is the actual test. Two supporting checks are geometry-derived, not observed-data-fitted:
#   - the adjacency buffer is each DOOR's OWN extracted footprint (half its real leaf/frame span) plus
#     one grid cell of rasterization slack — self-scaling to whatever doors this building actually has,
#     never a fixed metre guess;
#   - NOISE_FLOOR_DIM rejects a pocket whose rect is narrower than a few grid cells in EITHER axis —
#     that is a property of the flood-fill's own resolution (a 1-2 cell sliver is rasterization noise
#     by construction, regardless of which building produced it), not a threshold tuned to this data.
NOISE_FLOOR_DIM = 3 * RES   # m — a pocket narrower than this in x OR y is a grid artefact, not a room
DOOR_BUFFER_SLACK = RES     # m — rasterization slack added on top of each door's own real footprint
# §DOOR-NOT-ROOM: a door that leads to a SHAFT, not a habitable room, must not be used as the
# §DOOR-RESCUE "this pocket is a room" signal — same shape of problem as §STAIR-EXCLUDE (a real,
# correctly-classified element that still isn't evidence of a room). Found on real data (SampleCastle):
# 28 IfcDoor rows named 'liftdeur' (Dutch: lift/elevator door), width 0.5m — real doors, but 2 of them
# were rescuing actual elevator-shaft fragments as fake "rooms". Name-keyword match (multi-language,
# reviewed against real extractions, same discipline as NONHAB_TYPES) — not a width cutoff, since a
# lift door's width alone isn't reliably distinct from a narrow single-leaf door's.
NON_ROOM_DOOR_NAMES = ("liftdeur", "lift", "elevator", "aufzug", "fahrstuhl", "hoist")
def _is_room_door(name):
    n = (name or "").lower()
    return not any(k in n for k in NON_ROOM_DOOR_NAMES)
# §7 ROOM WELL-FORMEDNESS (ROOM_INJECTION_HYBRID.md §7, 2026-07-11 — user doctrine: "a room must be
# well formed, fully enclosed, has door"; failures become SUSPECT_* rows for a later review feature,
# never silently different geometry). Both factors are SELF-SCALING to the building's own extracted
# doors (same discipline as §DOOR-RESCUE's per-door buffer) — no fixed metres:
#   §WALL-VERT: IfcCurtainWall parents carry NO transform (verified HHS/Hospital/Clinic/Garage —
#     center_x NULL on all of them), so curtain walls rasterized as NOTHING and HHS's flood-fill
#     structurally failed. The real geometry is in the children: IfcMember (mullions) + IfcPlate
#     (glazing). Blanket inclusion is wrong (Terminal: 33,324 FLAT "Metal Deck" IfcPlate; Clinic:
#     stair-part IfcMember) — include a member/plate iff VERTICAL: bbox_z >= VERT_FACTOR × the
#     building's median real door height. Buildings with no doors skip inclusion (= old behavior).
VERT_FACTOR = 0.5
CW_CHILD_CLASSES = ("IfcMember", "IfcPlate")
#   §ROOM-FORM: openM = unsealed perimeter metres (boundary contacts that exit to free space without
#     meeting a raw wall within the dilation band; 3-wide probe so curved/diagonal wall stair-steps
#     don't read open). A room may legitimately have a doorless archway or two — more unsealed edge
#     than OPEN_PERIM_FACTOR × median door width is not "fully enclosed" → SUSPECT_OPEN. No adjacent
#     door at all → SUSPECT_NO_DOOR (voids/shafts/light-wells).
OPEN_PERIM_FACTOR = 2.0
# §MULTI-RECT (ROOM_INJECTION_HYBRID.md §8, 2026-07-11): ONE inscribed rectangle under-covers a
# non-rectangular room (measured single-rect coverage down to 0.23 on real Hospital/Clinic/Terminal
# rooms — the "doesn't fully form the inner room space" gap the user saw). A confirmed room is now
# a SET of non-overlapping rectangles carved from its (seal-band-recovered) region by a repeated
# constrained maximal-rectangle scan. All three knobs are grid-derived, not tuned:
#   RECT_COVER_TARGET: stop once this fraction of the region is covered — the remainder past 0.95
#     is stair-step fringe smaller than the noise floor (measured across all 8 buildings).
#   sub-rect minimum dimension = NOISE_FLOOR_DIM (the existing grid-resolution floor): a rect
#     thinner than 3 cells in either axis is rasterization fringe, not room space.
#   MAX_SUBRECTS: pure safety bound (measured: no real room needed >5).
# SUSPECT rooms stay single-rect (decomposition is for confirmed rooms only — orthogonal to §ROOM-FORM).
RECT_COVER_TARGET = 0.95
MAX_SUBRECTS = 8

def _median(vals):
    s = sorted(vals)
    return s[len(s) // 2] if s else 0.0

def door_stats(c):
    """Building-level medians of real door width/height — the self-scaling anchors for
    §WALL-VERT / §ROOM-FORM. Width = max(bbox_x, bbox_y) (leaf+frame plan span)."""
    rows = c.execute(
        "SELECT COALESCE(t.bbox_x,0), COALESCE(t.bbox_y,0), COALESCE(t.bbox_z,0) "
        "FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid "
        "WHERE m.ifc_class LIKE 'IfcDoor%' AND m.discipline='ARC' AND t.center_x IS NOT NULL").fetchall()
    ws = [max(bx, by_) for bx, by_, bz in rows if max(bx, by_) > 0]
    hs = [bz for bx, by_, bz in rows if bz > 0]
    return _median(ws), _median(hs)

def storey_z_anchors(c):
    """§STOREY-Z: per-storey mean center_z of EXPLICITLY-assigned real walls — the anchor used to
    reassign 'Unknown'-storey wall-like elements + doors to their actual floor (HHS: all 716
    vertical curtain children carry storey 'Unknown'; their z clusters match Level 1/2/3 exactly)."""
    rows = c.execute(
        "SELECT m.storey, t.center_z FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid "
        "WHERE m.ifc_class LIKE 'IfcWall%' AND m.discipline='ARC' AND t.center_x IS NOT NULL "
        "AND m.storey IS NOT NULL AND m.storey <> 'Unknown'").fetchall()
    acc = {}
    for st, cz in rows:
        acc.setdefault(st, []).append(cz)
    return {st: sum(v) / len(v) for st, v in acc.items()}

def _assign_by_z(st, cz, anchors, anchor_names):
    if st and st != "Unknown":
        return st
    if not anchor_names:
        return "Unknown"
    best, bd = None, float("inf")
    for a in anchor_names:  # sorted order = deterministic tie-break
        d = abs(cz - anchors[a])
        if d < bd:
            bd = d; best = a
    return best
# §APPROX: these rooms are COMPILED from wall enclosure (flood-fill), NOT extracted IfcSpace.
# Validated ~5/21 recall on ground-truth Duplex → treat as APPROXIMATE. Labelled '≈' + COMPILED.

def storey_walls(c, vert_min=0.0, anchors=None):
    # §DISC-ARC: room enclosure is an ARCHITECTURAL concept — discipline='ARC' on every element
    # query here, not just ifc_class LIKE. WalkerDoctrine.md: "discipline is a WHERE column."
    # Real gap found (2026-07-11): a raw multi-discipline extract (deploy/buildings/*_extracted.db,
    # not ARC-only stripped) carries STR-discipline IfcColumn/IfcWallStandardCase/IfcMember/IfcPlate
    # rows that also match WALL_LIKE/CW_CHILD_CLASSES ifc_class patterns — structural framing, not
    # room-enclosing walls — and without this filter they silently pollute the raster.
    cond = " OR ".join("m.ifc_class LIKE ?" for _ in WALL_LIKE)
    rows = c.execute(
        f"SELECT m.storey, t.center_x,t.center_y,t.center_z, t.bbox_x,t.bbox_y,t.bbox_z "
        f"FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid "
        f"WHERE ({cond}) AND m.discipline='ARC' AND t.center_x IS NOT NULL", WALL_LIKE).fetchall()
    # §WALL-VERT: curtain-wall children (IfcMember/IfcPlate) that stand wall-height — the enclosure
    # the bare WALL_LIKE query misses because IfcCurtainWall parents have no transform of their own.
    if vert_min > 0:
        ph = ",".join("?" for _ in CW_CHILD_CLASSES)
        rows = rows + c.execute(
            f"SELECT m.storey, t.center_x,t.center_y,t.center_z, t.bbox_x,t.bbox_y,t.bbox_z "
            f"FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid "
            f"WHERE m.ifc_class IN ({ph}) AND m.discipline='ARC' AND t.center_x IS NOT NULL AND t.bbox_z >= ?",
            CW_CHILD_CLASSES + (vert_min,)).fetchall()
    anchors = anchors or {}
    anchor_names = sorted(anchors)
    by = {}
    for st, cx, cy, cz, bx, by_, bz in rows:
        st = _assign_by_z(st or "Unknown", cz, anchors, anchor_names)  # §STOREY-Z
        by.setdefault(st, []).append((cx, cy, cz, bx, by_, bz))
    return by

def storey_stairs(c):
    """Per-storey stair/ramp footprints (cx,cy,bx,by) — circulation cores to exclude from rooms."""
    cond = " OR ".join("m.ifc_class LIKE ?" for _ in STAIR_LIKE)
    rows = c.execute(
        f"SELECT m.storey, t.center_x,t.center_y, t.bbox_x,t.bbox_y "
        f"FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid "
        f"WHERE ({cond}) AND m.discipline='ARC' AND t.center_x IS NOT NULL", STAIR_LIKE).fetchall()
    by = {}
    for st, cx, cy, bx, by_ in rows:
        by.setdefault(st or "Unknown", []).append((cx, cy, bx, by_))
    return by

def storey_doors(c, anchors=None):
    """Per-storey door (cx,cy,bx,by) — the §DOOR-RESCUE clue for genuine small rooms. Each door's
    OWN real footprint is carried through so adjacency self-scales to that door, not a guessed metre.
    §STOREY-Z applies here too: an 'Unknown'-storey door is reassigned to its z-nearest real floor."""
    rows = c.execute(
        "SELECT m.storey, m.element_name, t.center_x,t.center_y, t.center_z, COALESCE(t.bbox_x,0), COALESCE(t.bbox_y,0) "
        "FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid "
        "WHERE m.ifc_class LIKE 'IfcDoor%' AND m.discipline='ARC' AND t.center_x IS NOT NULL").fetchall()
    anchors = anchors or {}
    anchor_names = sorted(anchors)
    by = {}
    for st, name, cx, cy, cz, bx, by_ in rows:
        if not _is_room_door(name): continue  # §DOOR-NOT-ROOM: lift/elevator doors aren't room evidence
        st = _assign_by_z(st or "Unknown", cz if cz is not None else 0.0, anchors, anchor_names)
        by.setdefault(st, []).append((cx, cy, bx, by_))
    return by

def _door_adjacent(rx0, ry0, rx1, ry1, doors):
    for dx, dy, dbx, dby in doors:
        buf = max(dbx, dby) / 2 + DOOR_BUFFER_SLACK  # this door's own span, not a fixed guess
        if rx0 - buf <= dx <= rx1 + buf and ry0 - buf <= dy <= ry1 + buf:
            return True
    return False

def _stair_overlap_frac(rx0, ry0, rx1, ry1, stairs):
    """Largest fraction of room rect [rx0,ry0,rx1,ry1] covered by any single stair footprint."""
    room_area = max(1e-6, (rx1 - rx0) * (ry1 - ry0))
    best = 0.0
    for scx, scy, sbx, sby in stairs:
        sx0, sx1 = scx - sbx / 2, scx + sbx / 2
        sy0, sy1 = scy - sby / 2, scy + sby / 2
        ox = max(0.0, min(rx1, sx1) - max(rx0, sx0))
        oy = max(0.0, min(ry1, sy1) - max(ry0, sy0))
        best = max(best, (ox * oy) / room_area)
    return best

def _rasterize(walls, nx, ny, xs0, ys0):
    """Flat bytearray raster (k = i*ny + j — identical indexing to the JS port's Uint8Array)."""
    def ix(x): return min(nx - 1, max(0, int((x - xs0) / RES)))
    def iy(y): return min(ny - 1, max(0, int((y - ys0) / RES)))
    raw = bytearray(nx * ny)
    for cx, cy, cz, bx, by_, bz in walls:
        i0, i1 = ix(cx - bx / 2), ix(cx + bx / 2)
        j0, j1 = iy(cy - by_ / 2), iy(cy + by_ / 2)
        for i in range(i0, i1 + 1):
            base = i * ny
            for j in range(j0, j1 + 1):
                raw[base + j] = 1
    return raw

def _dilate(blocked, nx, ny, seal):
    b = blocked
    for _ in range(seal):
        d = bytearray(nx * ny)
        for i in range(nx):
            for j in range(ny):
                k = i * ny + j
                v = b[k]
                if not v and i > 0 and b[k - ny]: v = 1
                if not v and i < nx - 1 and b[k + ny]: v = 1
                if not v and j > 0 and b[k - 1]: v = 1
                if not v and j < ny - 1 and b[k + 1]: v = 1
                d[k] = v
        b = d
    return b

def _open_perimeter_m(cells, in_set, raw, dil, nx, ny, seal_steps):
    """§ROOM-FORM: metres of the region's boundary NOT backed by a raw wall. Each boundary contact
    (cell face, RES metres each) marches outward through the dilation band (<= seal_steps+1 cells);
    3-wide probe (straight + both perpendicular neighbors) so stair-stepped curved/diagonal walls
    read as wall, not open. A contact that exits to free space without meeting raw wall is open."""
    open_c = 0
    for k in cells:
        i = k // ny; j = k % ny
        for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            a, b = i + di, j + dj
            if a < 0 or a >= nx or b < 0 or b >= ny:
                open_c += 1; continue
            if in_set[a * ny + b]:
                continue
            pi, pj = dj, di
            hit_wall = False
            for s in range(seal_steps + 1):
                aa, bb = i + di * (1 + s), j + dj * (1 + s)
                if aa < 0 or aa >= nx or bb < 0 or bb >= ny: break
                kk = aa * ny + bb
                hit = raw[kk]
                if not hit:
                    la, lb = aa + pi, bb + pj
                    if 0 <= la < nx and 0 <= lb < ny and raw[la * ny + lb]: hit = 1
                if not hit:
                    ra, rb = aa - pi, bb - pj
                    if 0 <= ra < nx and 0 <= rb < ny and raw[ra * ny + rb]: hit = 1
                if hit:
                    hit_wall = True; break
                if not dil[kk]: break  # re-entered free space without meeting raw wall
            if not hit_wall:
                open_c += 1
    return open_c * RES

def _inscribed_rect(in_set, ny, mni, mxi, mnj, mxj):
    """§RECT-HONESTY: largest axis-aligned rectangle fully inside the claimed cells (maximal-rectangle
    histogram scan; deterministic scan order + strict '>' so ties resolve identically in both ports).
    Returns (i0, i1, j0, j1) in grid indices."""
    w = mxi - mni + 1; h = mxj - mnj + 1
    hist = [0] * h
    best_area = 0; bi0 = mni; bi1 = mni; bj0 = mnj; bj1 = mnj
    for i in range(w):
        for j in range(h):
            hist[j] = hist[j] + 1 if in_set[(mni + i) * ny + (mnj + j)] else 0
        stk = []
        for j in range(h + 1):
            cur = hist[j] if j < h else 0
            while stk and hist[stk[-1]] >= cur:
                top = stk.pop()
                height = hist[top]
                left = stk[-1] + 1 if stk else 0
                area = height * (j - left)
                if area > best_area:
                    best_area = area
                    bi0 = mni + i - height + 1; bi1 = mni + i
                    bj0 = mnj + left; bj1 = mnj + j - 1
            stk.append(j)
    return bi0, bi1, bj0, bj1

def _grow_region(cells, in_set, raw, dil, nx, ny, steps):
    """§MULTI-RECT: recover the SEAL erosion — grow the region up to `steps` layers into cells that
    are raw-free but dilation-blocked (the band between the region and its real walls). Never grows
    into other free space (exterior / another pocket), so every grown cell is this room's own floor.
    Mutates in_set; returns (added_cells, mni, mxi, mnj, mxj) with bounds covering the growth."""
    frontier = cells
    added = []
    mni = mxi = cells[0] // ny; mnj = mxj = cells[0] % ny
    for k in cells:
        i, j = k // ny, k % ny
        mni = min(mni, i); mxi = max(mxi, i); mnj = min(mnj, j); mxj = max(mxj, j)
    for _ in range(steps):
        nxt = []
        for k in frontier:
            i, j = k // ny, k % ny
            for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                a, b = i + di, j + dj
                if 0 <= a < nx and 0 <= b < ny:
                    kk = a * ny + b
                    if not in_set[kk] and not raw[kk] and dil[kk]:
                        in_set[kk] = 1; nxt.append(kk); added.append(kk)
                        mni = min(mni, a); mxi = max(mxi, a); mnj = min(mnj, b); mxj = max(mxj, b)
        frontier = nxt
    return added, mni, mxi, mnj, mxj

def _inscribed_rect_min(in_set, ny, mni, mxi, mnj, mxj, min_cells):
    """§MULTI-RECT: constrained maximal rectangle — both dims >= min_cells (the NOISE_FLOOR in
    cells; a thinner rect is rasterization fringe, not room space). None if no such rect exists.
    Same deterministic scan order / strict '>' tie-break as _inscribed_rect."""
    w = mxi - mni + 1; h = mxj - mnj + 1
    hist = [0] * h
    best_area = 0; best = None
    for i in range(w):
        for j in range(h):
            hist[j] = hist[j] + 1 if in_set[(mni + i) * ny + (mnj + j)] else 0
        stk = []
        for j in range(h + 1):
            cur = hist[j] if j < h else 0
            while stk and hist[stk[-1]] >= cur:
                top = stk.pop()
                height = hist[top]
                left = stk[-1] + 1 if stk else 0
                width = j - left
                if height >= min_cells and width >= min_cells:
                    area = height * width
                    if area > best_area:
                        best_area = area
                        best = (mni + i - height + 1, mni + i, mnj + left, mnj + j - 1)
            stk.append(j)
    return best

def _decompose_region(in_set, ny, mni, mxi, mnj, mxj, total_cells, single):
    """§MULTI-RECT: carve the region into non-overlapping rectangles — repeated constrained
    maximal-rectangle scan, stopping at RECT_COVER_TARGET coverage / MAX_SUBRECTS / no rect left
    above the noise floor. `single` (SUSPECT rooms) emits the first rect only. Clears carved cells
    from in_set (caller resets the full region afterwards). Falls back to the unconstrained
    single rect when the region is too small/thin for a 3x3 (door-rescued slivers).
    Returns (rects, covered_cells)."""
    min_cells = int(round(NOISE_FLOOR_DIM / RES))
    rects = []
    covered = 0
    for _ in range(MAX_SUBRECTS):
        r = _inscribed_rect_min(in_set, ny, mni, mxi, mnj, mxj, min_cells)
        if r is None:
            break
        i0, i1, j0, j1 = r
        rects.append(r)
        for i in range(i0, i1 + 1):
            base = i * ny
            for j in range(j0, j1 + 1):
                in_set[base + j] = 0
        covered += (i1 - i0 + 1) * (j1 - j0 + 1)
        if single:
            break
        if covered >= RECT_COVER_TARGET * total_cells:
            break
    if not rects:
        r = _inscribed_rect(in_set, ny, mni, mxi, mnj, mxj)
        rects.append(r)
        covered = (r[1] - r[0] + 1) * (r[3] - r[2] + 1)
    return rects, covered

def _classify(has_door, open_m, door_w_med):
    """§ROOM-FORM: user doctrine 'a room must be well formed, fully enclosed, has door'.
    Returns None (well-formed) / 'NO_DOOR' / 'OPEN'. door_w_med <= 0 (no real doors in the
    building) → openM test is skipped (nothing to derive the limit from; such pockets are
    already SUSPECT_NO_DOOR)."""
    if not has_door:
        return "NO_DOOR"
    if door_w_med > 0 and open_m > OPEN_PERIM_FACTOR * door_w_med:
        return "OPEN"
    return None

def flood_rooms(walls, stairs=None, doors=None, door_w_med=0.0):
    stairs = stairs or []
    doors = doors or []
    xs0 = min(w[0] - w[3] / 2 for w in walls); xs1 = max(w[0] + w[3] / 2 for w in walls)
    ys0 = min(w[1] - w[4] / 2 for w in walls); ys1 = max(w[1] + w[4] / 2 for w in walls)
    pad = RES * 2
    xs0 -= pad; ys0 -= pad; xs1 += pad; ys1 += pad
    nx = max(4, int(math.ceil((xs1 - xs0) / RES)))
    ny = max(4, int(math.ceil((ys1 - ys0) / RES)))
    raw = _rasterize(walls, nx, ny, xs0, ys0)
    # Morphological close: dilate walls SEAL cells to seal hairline corner/door-jamb gaps so the
    # exterior flood can't leak into a room through a 1–2 cell crack (it still leaves real ~1m
    # doorways open — by design those connect rooms, handled by the area filter / per-room split).
    dil = _dilate(raw, nx, ny, SEAL) if SEAL > 0 else raw
    free = bytearray(0 if dil[m] else 1 for m in range(nx * ny))
    # exterior flood from border free cells (4-connectivity, iterative stack)
    ext = bytearray(nx * ny)
    stack = []
    for i in range(nx):
        for j in (0, ny - 1):
            k = i * ny + j
            if free[k] and not ext[k]: ext[k] = 1; stack.append(k)
    for j in range(ny):
        for i in (0, nx - 1):
            k = i * ny + j
            if free[k] and not ext[k]: ext[k] = 1; stack.append(k)
    while stack:
        k0 = stack.pop()
        i, j = k0 // ny, k0 % ny
        for di, dj in ((1,0),(-1,0),(0,1),(0,-1)):
            a, b = i + di, j + dj
            if 0 <= a < nx and 0 <= b < ny:
                k = a * ny + b
                if free[k] and not ext[k]: ext[k] = 1; stack.append(k)
    enclosed = bytearray(1 if free[m] and not ext[m] else 0 for m in range(nx * ny))
    # connected components on enclosed
    rooms = []
    seen = bytearray(nx * ny)
    in_set = bytearray(nx * ny)
    cell_area = RES * RES
    plan_area = nx * ny * cell_area
    cz = sum(w[2] for w in walls) / len(walls); bz = sum(w[5] for w in walls) / len(walls)
    for si in range(nx):
        for sj in range(ny):
            sk = si * ny + sj
            if not enclosed[sk] or seen[sk]: continue
            comp = []; st = [sk]; seen[sk] = 1
            mni = mxi = si; mnj = mxj = sj
            while st:
                k = st.pop(); comp.append(k)
                i, j = k // ny, k % ny
                mni = min(mni, i); mxi = max(mxi, i); mnj = min(mnj, j); mxj = max(mxj, j)
                for di, dj in ((1,0),(-1,0),(0,1),(0,-1)):
                    a, b = i + di, j + dj
                    if 0 <= a < nx and 0 <= b < ny:
                        kk = a * ny + b
                        if enclosed[kk] and not seen[kk]:
                            seen[kk] = 1; st.append(kk)
            area = len(comp) * cell_area
            if area > MAX_AREA_ABS or area > plan_area * MAX_AREA_FRAC: continue
            wx0 = xs0 + mni * RES; wx1 = xs0 + (mxi + 1) * RES
            wy0 = ys0 + mnj * RES; wy1 = ys0 + (mxj + 1) * RES
            # §DOOR-RESCUE (abstract test, applies uniformly — not a size band): a pocket is a room if
            # it is big enough to obviously be one on its own (area >= MIN_AREA, the original rule,
            # unchanged for the common case) OR it has a real door AND isn't a bare rasterization sliver
            # (NOISE_FLOOR_DIM, a grid-resolution property, not a fitted area number).
            door_rescued = False
            has_door = _door_adjacent(wx0, wy0, wx1, wy1, doors)
            if area < MIN_AREA:
                dims_ok = (wx1 - wx0) >= NOISE_FLOOR_DIM and (wy1 - wy0) >= NOISE_FLOOR_DIM
                if not (dims_ok and has_door):
                    continue
                door_rescued = True
            # §STAIR-EXCLUDE: a stair/ramp footprint covering this pocket → it's a circulation
            # shaft, not a room. Drop it (the lens was showing staircases as rooms).
            sf = _stair_overlap_frac(wx0, wy0, wx1, wy1, stairs)
            if sf >= STAIR_OVERLAP_REJECT:
                print(f"    skip stair-shaft pocket area={round(area)} stair_overlap={sf:.0%}"); continue
            # §ROOM-FORM + §RECT-HONESTY + §MULTI-RECT (ROOM_INJECTION_HYBRID.md §7/§8)
            for k in comp: in_set[k] = 1
            open_m = _open_perimeter_m(comp, in_set, raw, dil, nx, ny, SEAL)
            suspect = _classify(has_door, open_m, door_w_med)
            grown, gmni, gmxi, gmnj, gmxj = _grow_region(comp, in_set, raw, dil, nx, ny, SEAL)
            total_cells = len(comp) + len(grown)
            grects, covered = _decompose_region(in_set, ny, gmni, gmxi, gmnj, gmxj, total_cells,
                                                bool(suspect))
            for k in comp: in_set[k] = 0
            for k in grown: in_set[k] = 0
            rects = []
            for (ri0, ri1, rj0, rj1) in grects:
                rx0 = xs0 + ri0 * RES; rx1 = xs0 + (ri1 + 1) * RES
                ry0 = ys0 + rj0 * RES; ry1 = ys0 + (rj1 + 1) * RES
                rects.append({"cx": (rx0 + rx1) / 2, "cy": (ry0 + ry1) / 2,
                              "sx": rx1 - rx0, "sy": ry1 - ry0})
            r0 = grects[0]
            cover1 = ((r0[1] - r0[0] + 1) * (r0[3] - r0[2] + 1)) / total_cells
            rooms.append({
                "cx": rects[0]["cx"], "cy": rects[0]["cy"], "cz": cz,
                "sx": rects[0]["sx"], "sy": rects[0]["sy"], "sz": max(bz, 2.0), "area": area,
                "door_rescued": door_rescued, "open_m": open_m, "suspect": suspect,
                "rects": rects, "cover1": cover1, "cover_n": covered / total_cells})
    return rooms

# §DOOR-PARTITION: on some real buildings (HHS confirmed) wall-enclosure flood-fill structurally
# can't find rooms — most of the floor floods as one exterior-reachable blob regardless of area/door
# filtering, because the walls that would divide individual rooms simply aren't in this extraction.
# The gate for "walls can't do this, fall back" is the DIRECT, abstract test the user named: compare
# what flood-fill (with door-rescue already applied) actually found against how many real doors this
# storey has — every door leads to a room, so a storey whose flood-fill result is a small fraction of
# its door count has failed, full stop, regardless of which building it is. Measured before picking
# the ratio: HHS's floors find 0-11% of their door count via flood-fill; every other building's
# working floors find 25-100%+ (Garage's sparsest working floor: 5 rooms / 8 doors = 62%; Hospital's
# sparsest: 1 room / 5 doors = 20%) — DOOR_SHORTFALL_RATIO=0.15 sits below every working floor's own
# ratio and above every one of HHS's, so it never overrides an already-functioning floor (verified:
# Garage's genuine 5-room floor and Hospital's genuine 1-room floor both correctly keep flood-fill's
# result, not door-partition's coarser one). Where flood-fill DOES fail this test, partition the
# storey's free space by NEAREST DOOR (multi-source BFS through real free cells, real walls still
# block) — each door claims whatever space no other door reaches first, same as how a real occupant
# would experience the floor from that door. Fully derived from real door + wall positions, still
# deterministic and reproducible; a different compile technique for where enclosure-based compiling
# structurally cannot work, not an invention.
DOOR_SHORTFALL_RATIO = 0.15  # flood-fill finding fewer rooms than this fraction of doors = has failed

def partition_by_doors(walls, doors, stairs, door_w_med=0.0):
    if not doors: return []
    xs0 = min(w[0] - w[3] / 2 for w in walls); xs1 = max(w[0] + w[3] / 2 for w in walls)
    ys0 = min(w[1] - w[4] / 2 for w in walls); ys1 = max(w[1] + w[4] / 2 for w in walls)
    pad = RES * 2; xs0 -= pad; ys0 -= pad; xs1 += pad; ys1 += pad
    nx = max(4, int(math.ceil((xs1 - xs0) / RES))); ny = max(4, int(math.ceil((ys1 - ys0) / RES)))
    raw = _rasterize(walls, nx, ny, xs0, ys0)
    def ix(x): return min(nx - 1, max(0, int((x - xs0) / RES)))
    def iy(y): return min(ny - 1, max(0, int((y - ys0) / RES)))
    free = bytearray(0 if raw[m] else 1 for m in range(nx * ny))
    cz = sum(w[2] for w in walls) / len(walls); bz = sum(w[5] for w in walls) / len(walls)

    owner = [-1] * (nx * ny)
    queue = []; head = 0
    for di, (dcx, dcy, dbx, dby) in enumerate(doors):
        ci, cj = ix(dcx), iy(dcy)
        seed = None
        for r in range(7):  # expand outward (~1.4m) to find a free cell to seed this door from
            for da in range(-r, r + 1):
                if seed is not None: break
                for db in range(-r, r + 1):
                    if max(abs(da), abs(db)) != r: continue
                    a, b = ci + da, cj + db
                    if 0 <= a < nx and 0 <= b < ny:
                        k = a * ny + b
                        if free[k] and owner[k] == -1:
                            seed = k; break
            if seed is not None: break
        if seed is None: continue
        owner[seed] = di; queue.append(seed)

    while head < len(queue):
        k0 = queue[head]; head += 1
        i, j = k0 // ny, k0 % ny
        for di_, dj_ in ((1,0),(-1,0),(0,1),(0,-1)):
            a, b = i + di_, j + dj_
            if 0 <= a < nx and 0 <= b < ny:
                k = a * ny + b
                if free[k] and owner[k] == -1:
                    owner[k] = owner[k0]; queue.append(k)

    by_owner = {}
    for k in range(nx * ny):
        o = owner[k]
        if o == -1: continue
        by_owner.setdefault(o, []).append(k)

    cell_area = RES * RES; plan_area = nx * ny * cell_area
    in_set = bytearray(nx * ny)
    rooms = []
    for di in range(len(doors)):
        cells = by_owner.get(di)
        if not cells: continue
        area = len(cells) * cell_area
        mni = mnj = None
        for k in cells:
            i, j = k // ny, k % ny
            if mni is None:
                mni = mxi = i; mnj = mxj = j
            else:
                mni = min(mni, i); mxi = max(mxi, i); mnj = min(mnj, j); mxj = max(mxj, j)
        wx0 = xs0 + mni * RES; wx1 = xs0 + (mxi + 1) * RES
        wy0 = ys0 + mnj * RES; wy1 = ys0 + (mxj + 1) * RES
        if (wx1 - wx0) < NOISE_FLOOR_DIM or (wy1 - wy0) < NOISE_FLOOR_DIM: continue
        if area > MAX_AREA_ABS or area > plan_area * MAX_AREA_FRAC: continue
        if _stair_overlap_frac(wx0, wy0, wx1, wy1, stairs) >= STAIR_OVERLAP_REJECT: continue
        # §ROOM-FORM + §RECT-HONESTY + §MULTI-RECT (ROOM_INJECTION_HYBRID.md §7/§8). No dilation on
        # this path → seal_steps=0 for the openM march, no seal band to grow back into.
        for k in cells: in_set[k] = 1
        open_m = _open_perimeter_m(cells, in_set, raw, raw, nx, ny, 0)
        has_door = _door_adjacent(wx0, wy0, wx1, wy1, doors)
        suspect = _classify(has_door, open_m, door_w_med)
        grects, covered = _decompose_region(in_set, ny, mni, mxi, mnj, mxj, len(cells), bool(suspect))
        for k in cells: in_set[k] = 0
        rects = []
        for (ri0, ri1, rj0, rj1) in grects:
            rx0 = xs0 + ri0 * RES; rx1 = xs0 + (ri1 + 1) * RES
            ry0 = ys0 + rj0 * RES; ry1 = ys0 + (rj1 + 1) * RES
            rects.append({"cx": (rx0 + rx1) / 2, "cy": (ry0 + ry1) / 2,
                          "sx": rx1 - rx0, "sy": ry1 - ry0})
        r0 = grects[0]
        cover1 = ((r0[1] - r0[0] + 1) * (r0[3] - r0[2] + 1)) / len(cells)
        rooms.append({"cx": rects[0]["cx"], "cy": rects[0]["cy"], "cz": cz,
                      "sx": rects[0]["sx"], "sy": rects[0]["sy"], "sz": max(bz, 2.0), "area": area,
                      "door_rescued": False, "door_partitioned": True, "open_m": open_m,
                      "suspect": suspect, "rects": rects, "cover1": cover1,
                      "cover_n": covered / len(cells)})
    return rooms

def main():
    if len(sys.argv) < 2:
        print(__doc__); return
    db = sys.argv[1]; write = "--write" in sys.argv
    con = sqlite3.connect(db); c = con.cursor()
    # storey guid map (for parent_guid)
    st_guid = {}
    try:
        for g, n in c.execute("SELECT guid, name FROM spatial_structure WHERE type='IfcBuildingStorey'").fetchall():
            st_guid[n] = g
    except Exception:
        pass
    # §7 self-scaling anchors: this building's own median door width/height (§ROOM-FORM/§WALL-VERT)
    # + per-storey wall-z anchors (§STOREY-Z).
    door_w_med, door_h_med = door_stats(c)
    vert_min = VERT_FACTOR * door_h_med if door_h_med > 0 else 0.0
    anchors = storey_z_anchors(c)
    by = storey_walls(c, vert_min, anchors)
    stairs_by = storey_stairs(c)
    doors_by = storey_doors(c, anchors)
    # §STAIR-EXCLUDE: stair storey is often 'Unknown'/unassigned in the extract, and a stair is a
    # CONTINUOUS vertical shaft anyway — so test every room pocket against the UNION of all stair
    # footprints by XY (not per-storey). A staircase at an XY is circulation on whatever floor it cuts.
    all_stairs = [s for lst in stairs_by.values() for s in lst]
    total = 0; door_rescued_total = 0; door_partition_total = 0; allrooms = []; st_z = {}
    for st in sorted(by):
        ws = by[st]
        if len(ws) < 3:
            print(f"  storey {st!r}: walls={len(ws)} (too few — skip)"); continue
        doors = doors_by.get(st, [])
        rooms_flood = flood_rooms(ws, all_stairs, doors, door_w_med)
        # §DOOR-PARTITION gate: flood-fill found far fewer rooms than this storey has real doors —
        # it has structurally failed here, fall back to nearest-door partitioning (never overrides
        # an already-working floor — see the ratio derivation above).
        if doors and len(rooms_flood) < DOOR_SHORTFALL_RATIO * len(doors):
            rooms = partition_by_doors(ws, doors, all_stairs, door_w_med)
            method = f"door-partition (flood-fill only found {len(rooms_flood)}/{len(doors)} doors)"
        else:
            rooms = rooms_flood
            method = "flood-fill"
        total += len(rooms)
        rescued = sum(1 for r in rooms if r.get('door_rescued'))
        partitioned = sum(1 for r in rooms if r.get('door_partitioned'))
        suspects = sum(1 for r in rooms if r.get('suspect'))
        door_rescued_total += rescued; door_partition_total += partitioned
        st_z[st] = sum(w[2] for w in ws) / len(ws)  # storey z = mean wall centre-z
        print(f"  storey {st!r}: walls={len(ws)} doors={len(doors)} [{method}] → rooms={len(rooms)} "
              f"(door_rescued={rescued} door_partitioned={partitioned} suspect={suspects})  areas={[round(r['area']) for r in rooms]}")
        for k, r in enumerate(rooms):
            r["storey"] = st; r["guid"] = f"RM_{st}_{k+1}".replace(" ", "_")
            # §APPROX: '≈' marks the room as compiled/approximate in the lens label; '⚠' marks a
            # §ROOM-FORM SUSPECT (kept visible for the future review feature, never silently dropped).
            # parent_guid → a compiled storey row (created below) so the Room lens groups per floor.
            mark = "⚠" if r.get("suspect") else "≈"
            r["name"] = f"{mark} {st} R{k+1}"; r["parent"] = st_guid.get(st) or ("STC_" + st).replace(" ", "_")
            allrooms.append(r)
    suspect_total = sum(1 for r in allrooms if r.get('suspect'))
    print(f"TOTAL compiled rooms = {total} (door_rescued={door_rescued_total} door_partitioned={door_partition_total} suspect={suspect_total})")
    if not write:
        print("(dry run — pass --write to inject)"); return
    # ensure spatial_structure has bbox columns
    cols = [r[1] for r in c.execute("PRAGMA table_info(spatial_structure)").fetchall()] if \
        c.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='spatial_structure'").fetchall() else None
    if cols is None:
        c.execute("""CREATE TABLE spatial_structure (guid TEXT, type TEXT, name TEXT, parent_guid TEXT,
                     object_type TEXT, predefined_type TEXT, center_x REAL, center_y REAL, center_z REAL,
                     size_x REAL, size_y REAL, size_z REAL)"""); cols = []
    def _addcol(table, col, typ):
        try: c.execute(f"ALTER TABLE {table} ADD COLUMN {col} {typ}")
        except sqlite3.OperationalError: pass  # already exists — fine
    for col in ("center_x","center_y","center_z","size_x","size_y","size_z"):
        _addcol("spatial_structure", col, "REAL")
    for col in ("object_type","predefined_type","room_guid"):
        _addcol("spatial_structure", col, "TEXT")
    # §APPROX: compiled storey rows (only where the DB has none) so the Room lens can group
    # rooms per floor via parent_guid → IfcBuildingStorey.name. Idempotent on the STC_ prefix.
    c.execute("DELETE FROM spatial_structure WHERE type='IfcBuildingStorey' AND guid LIKE 'STC_%'")
    for st in sorted(st_z):
        if not any(r["storey"] == st for r in allrooms):
            continue
        c.execute("INSERT INTO spatial_structure (guid,type,name,parent_guid,object_type,"
                  "predefined_type,center_z) VALUES (?,?,?,?,?,?,?)",
                  (("STC_" + st).replace(" ", "_"), "IfcBuildingStorey", st, None, "COMPILED", None, st_z[st]))
    # remove any prior compiled rooms (idempotent)
    c.execute("DELETE FROM spatial_structure WHERE type='IfcSpace' AND guid LIKE 'RM_%'")
    for r in allrooms:
        # predefined_type distinguishes which compile technique found each room (wall-enclosure vs
        # §DOOR-RESCUE small room vs §DOOR-PARTITION) for traceability — object_type stays 'COMPILED'
        # either way (the tag spacesOf()'s exclusion filter and every tag-purity check key on).
        # §ROOM-FORM: SUSPECT_* overrides — the room failed "well formed, fully enclosed, has door"
        # and is carried as a review candidate, not as a trusted room.
        ptype = ("SUSPECT_" + r["suspect"]) if r.get("suspect") else \
                "INTERNAL_DOORPART" if r.get("door_partitioned") else \
                "INTERNAL_SMALL" if r.get("door_rescued") else "INTERNAL"
        # §MULTI-RECT: one row per sub-rect, ALL sharing room_guid (= the primary rect's guid) and
        # the same name/type — N rects, ONE logical room. Sub-rect guids get a letter suffix
        # (RM_..._5, RM_..._5b, RM_..._5c) so 'RM\_%' patterns keep matching every row.
        for ri, rc in enumerate(r.get("rects") or [{"cx": r["cx"], "cy": r["cy"], "sx": r["sx"], "sy": r["sy"]}]):
            g = r["guid"] if ri == 0 else r["guid"] + chr(ord('a') + ri)
            c.execute("INSERT INTO spatial_structure (guid,type,name,parent_guid,object_type,predefined_type,"
                      "center_x,center_y,center_z,size_x,size_y,size_z,room_guid) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                      (g, "IfcSpace", r["name"], r["parent"], "COMPILED", ptype,
                       rc["cx"], rc["cy"], r["cz"], rc["sx"], rc["sy"], r["sz"], r["guid"]))
    # rel_contained_in_space: elements whose XY centre falls inside a room (compiled)
    if not c.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='rel_contained_in_space'").fetchall():
        c.execute("CREATE TABLE rel_contained_in_space (space_guid TEXT, element_guid TEXT)")
    c.execute("DELETE FROM rel_contained_in_space WHERE space_guid LIKE 'RM_%'")
    els = c.execute("SELECT m.guid, m.storey, t.center_x, t.center_y FROM elements_meta m "
                    "JOIN element_transforms t ON t.guid=m.guid WHERE t.center_x IS NOT NULL").fetchall()
    rel = 0
    byst = {}
    # §ROOM-FORM: SUSPECT rooms get no element containment — an unreviewed corridor/void must not
    # capture elements away from real rooms.
    for r in allrooms:
        if r.get("suspect"): continue
        byst.setdefault(r["storey"], []).append(r)
    for g, st, ex, ey in els:
        for r in byst.get(st, []):
            # §MULTI-RECT: contained iff inside ANY of the room's rects; the rel row keys the
            # LOGICAL room guid so downstream still sees one room, not N.
            hit = False
            for rc in (r.get("rects") or [r]):
                if abs(ex - rc["cx"]) <= rc["sx"]/2 and abs(ey - rc["cy"]) <= rc["sy"]/2:
                    hit = True; break
            if hit:
                c.execute("INSERT INTO rel_contained_in_space (space_guid, element_guid) VALUES (?,?)", (r["guid"], g)); rel += 1
                break
    con.commit()
    rect_rows = sum(len(r.get("rects") or [None]) for r in allrooms)
    print(f"WROTE {len(allrooms)} rooms as {rect_rows} IfcSpace rect rows + {rel} rel_contained_in_space rows")

if __name__ == "__main__":
    main()
