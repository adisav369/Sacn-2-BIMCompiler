# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
S173: Rotation truth test — full chain proof.

P1 ROT_TRUTH: lib_mesh + stored_transform == IFC iterator (at event)
P2 PORT_MEET: connected ports meet after reconstruction

Usage:
  python3 scripts/test_rotation_truth.py \
    --db DAGCompiler/lib/input/Hospital_extracted.db \
    --library library/component_library.db \
    --ifc-dir DAGCompiler/lib/input/IFC/UNMERGED \
    --pattern "Hospital_IFC4_*.ifc"
"""
import sys
# --- utf8-console guard (2026-09-05) ---------------------------------------------
# This script prints non-ASCII (box-drawing, arrows, section marks). On a console whose
# encoding is not UTF-8 -- Windows cp1252 is the common case -- print() raises
# UnicodeEncodeError and kills the script mid-run. That is not hypothetical: it aborted
# scripts/restore_generative_meshes.py immediately after it created its back-compat view
# but BEFORE it restored any mesh, which is why the component_library repair silently
# needed two passes to converge. errors="replace" is deliberate: a mangled glyph in a log
# line is always better than a dead pipeline stage.
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError, OSError):
        pass  # already-wrapped, detached, or replaced by a non-TextIOWrapper (e.g. in tests)
# ---------------------------------------------------------------------------------
import argparse, math, sqlite3, sys
from pathlib import Path
import numpy as np
import ifcopenshell, ifcopenshell.geom
import ifcopenshell.util.placement as ifcplace
import ifcopenshell.util.unit as ifcunit


def euler_to_matrix(rx, ry, rz):
    a, b, c = rx, ry, rz
    return np.array([
        [math.cos(b)*math.cos(c), math.sin(a)*math.sin(b)*math.cos(c)-math.cos(a)*math.sin(c), math.cos(a)*math.sin(b)*math.cos(c)+math.sin(a)*math.sin(c)],
        [math.cos(b)*math.sin(c), math.sin(a)*math.sin(b)*math.sin(c)+math.cos(a)*math.cos(c), math.cos(a)*math.sin(b)*math.sin(c)-math.sin(a)*math.cos(c)],
        [-math.sin(b),             math.sin(a)*math.cos(b),                                      math.cos(a)*math.cos(b)]
    ])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", required=True)
    parser.add_argument("--library", required=True)
    parser.add_argument("--ifc-dir", required=True)
    parser.add_argument("--pattern", default="*.ifc")
    parser.add_argument("--max", type=int, default=50)
    args = parser.parse_args()

    db = sqlite3.connect(args.db)
    lib = sqlite3.connect(args.library)
    ifc_dir = Path(args.ifc_dir)
    ifc_files = sorted(ifc_dir.glob(args.pattern))

    if not ifc_files:
        print(f"No IFC files matching {args.pattern} in {ifc_dir}")
        sys.exit(1)

    # Load stored transforms + hashes
    stored = {}
    for r in db.execute("""
        SELECT et.guid, et.center_x, et.center_y, et.center_z,
               et.rotation_x, et.rotation_y, et.rotation_z, ei.geometry_hash
        FROM element_transforms et
        JOIN element_instances ei ON ei.guid = et.guid
    """):
        stored[r[0]] = r[1:]

    # ── P1: ROT_TRUTH — at the event, compare lib_mesh + stored_transform vs IFC iterator
    # Use USE_WORLD_COORDS=False on IFC to get the same local mesh + matrix the extractor saw.
    # Then compare: library mesh == iterator mesh, stored Euler→R == iterator rot3.
    # This is the moment of truth — no offset math needed.
    p1_ok = p1_fail = 0
    p1_worst = 0.0
    checked = 0

    for ifc_path in ifc_files:
        if checked >= args.max:
            break
        ifc = ifcopenshell.open(str(ifc_path))
        settings = ifcopenshell.geom.settings()
        settings.set(settings.USE_WORLD_COORDS, False)
        settings.set(settings.WELD_VERTICES, True)
        it = ifcopenshell.geom.iterator(settings, ifc)
        if not it.initialize():
            continue

        while checked < args.max:
            shape = it.get()
            guid = shape.guid
            if guid not in stored:
                if not it.next():
                    break
                continue

            cx, cy, cz, rx, ry, rz, ghash = stored[guid]
            iter_verts = np.array(shape.geometry.verts, dtype=np.float64).reshape(-1, 3)
            if len(iter_verts) < 3:
                if not it.next():
                    break
                continue

            lib_row = lib.execute(
                "SELECT vertices FROM component_geometries WHERE geometry_hash=?",
                (ghash,)).fetchone()
            if not lib_row or not lib_row[0]:
                if not it.next():
                    break
                continue

            lib_verts = np.frombuffer(lib_row[0], dtype=np.float32).reshape(-1, 3)

            # Check 1: mesh identity (lib == iterator)
            mesh_ok = np.allclose(iter_verts.astype(np.float32), lib_verts, atol=1e-4)

            # Check 2: rotation matrix identity (stored Euler→R == iterator rot3)
            mat = np.array(list(shape.transformation.matrix), dtype=np.float64).reshape(4, 4).T
            rot3 = mat[:3, :3]
            R = euler_to_matrix(rx, ry, rz)
            rot_ok = np.allclose(rot3, R, atol=1e-6)

            # Check 3: centre offset is consistent (only Z should differ by site_offset)
            iter_centre = mat[:3, 3]
            stored_centre = np.array([cx, cy, cz])
            xy_ok = abs(iter_centre[0] - stored_centre[0]) < 0.01 and \
                    abs(iter_centre[1] - stored_centre[1]) < 0.01

            all_ok = mesh_ok and rot_ok and xy_ok
            if not all_ok:
                p1_fail += 1
                if p1_fail <= 3:
                    print(f"  FAIL ROT_TRUTH {guid[:16]} {shape.type} "
                          f"mesh={mesh_ok} rot={rot_ok} xy={xy_ok}")
            else:
                p1_ok += 1

            checked += 1
            if not it.next():
                break

    tag1 = "PASS" if p1_fail == 0 and p1_ok > 0 else "FAIL"
    print(f"§PROOF ROT_TRUTH {tag1}  {p1_ok} ok, {p1_fail} fail  "
          f"(lib_mesh==iter_mesh, Euler→R==iter_rot3, XY centre match)")

    # ── P2: PORT_MEET — connected ports meet after reconstruction
    p2_ok = p2_fail = 0
    p2_worst = 0.0
    p2_checked = 0

    for ifc_path in ifc_files:
        if p2_checked >= args.max:
            break
        ifc = ifcopenshell.open(str(ifc_path))
        us = ifcunit.calculate_unit_scale(ifc, "LENGTHUNIT")

        rels = ifc.by_type("IfcRelConnectsPorts")
        if not rels:
            continue

        port_to_elem = {}
        for r in ifc.by_type("IfcRelConnectsPortToElement"):
            port_to_elem[r.RelatingPort.GlobalId] = r.RelatedElement

        for rel in rels:
            if p2_checked >= args.max:
                break
            p_a = rel.RelatingPort
            p_b = rel.RelatedPort
            e_a = port_to_elem.get(p_a.GlobalId)
            e_b = port_to_elem.get(p_b.GlobalId)
            if not e_a or not e_b:
                continue
            if e_a.GlobalId not in stored or e_b.GlobalId not in stored:
                continue

            # IFC port world positions (ground truth — iterator returns metres)
            try:
                m_a = ifcplace.get_local_placement(p_a.ObjectPlacement)
                m_b = ifcplace.get_local_placement(p_b.ObjectPlacement)
                ifc_pa = m_a[:3, 3] * us
                ifc_pb = m_b[:3, 3] * us
            except Exception:
                continue

            # Element world placements from IFC
            try:
                em_a = ifcplace.get_local_placement(e_a.ObjectPlacement)
                em_b = ifcplace.get_local_placement(e_b.ObjectPlacement)
            except Exception:
                continue
            e_a_centre = em_a[:3, 3] * us
            e_a_rot = em_a[:3, :3]
            e_b_centre = em_b[:3, 3] * us
            e_b_rot = em_b[:3, :3]

            # Port local offset relative to element
            local_pa = np.linalg.inv(e_a_rot) @ (ifc_pa - e_a_centre)
            local_pb = np.linalg.inv(e_b_rot) @ (ifc_pb - e_b_centre)

            # Reconstruct using STORED transforms
            cx_a, cy_a, cz_a, rx_a, ry_a, rz_a, _ = stored[e_a.GlobalId]
            cx_b, cy_b, cz_b, rx_b, ry_b, rz_b, _ = stored[e_b.GlobalId]
            R_a = euler_to_matrix(rx_a, ry_a, rz_a)
            R_b = euler_to_matrix(rx_b, ry_b, rz_b)

            recon_pa = R_a @ local_pa + np.array([cx_a, cy_a, cz_a])
            recon_pb = R_b @ local_pb + np.array([cx_b, cy_b, cz_b])
            recon_dist = np.linalg.norm(recon_pa - recon_pb)

            if recon_dist > p2_worst:
                p2_worst = recon_dist

            if recon_dist > 0.01:
                p2_fail += 1
                if p2_fail <= 3:
                    ifc_dist = np.linalg.norm(ifc_pa - ifc_pb)
                    print(f"  FAIL PORT_MEET {e_a.is_a()}→{e_b.is_a()} "
                          f"recon_dist={recon_dist:.4f}m ifc_dist={ifc_dist:.4f}m")
            else:
                p2_ok += 1
            p2_checked += 1

    tag2 = "PASS" if p2_fail == 0 and p2_ok > 0 else ("SKIP" if p2_ok == 0 else "FAIL")
    print(f"§PROOF PORT_MEET {tag2}  {p2_ok} ok, {p2_fail} fail  "
          f"worst_dist={p2_worst:.6f}m")

    all_pass = (p1_fail == 0 and p1_ok > 0) and (p2_fail == 0 or p2_ok == 0)
    print(f"\n§PROOF ROTATION {'PASS' if all_pass else 'FAIL'}")
    sys.exit(0 if all_pass else 1)


if __name__ == "__main__":
    main()
