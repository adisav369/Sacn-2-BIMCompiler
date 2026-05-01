# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
dxf_read_positions.py — Read GUID→centroid map from a DXF file with BIMGUID xdata.

Called by DXFSyncVerb.java via ProcessBuilder.
Outputs JSON: {"guid": [centroid_x_m, centroid_y_m], ...}

Spec: 2D_007 §Task 2 — DXF ARC roundtrip
"""

import ezdxf
import json
import sys


def read_positions(dxf_path: str) -> dict:
    """Extract GUID→centroid map from DXF LWPOLYLINE entities with BIMGUID xdata.

    Returns dict of {guid: [cx_metres, cy_metres]}.
    DXF coordinates are in model-space mm; output is in world metres.
    """
    doc = ezdxf.readfile(dxf_path)
    result = {}
    for e in doc.modelspace():
        if e.dxftype() != 'LWPOLYLINE':
            continue
        try:
            xdata = e.get_xdata('BIMGUID')
        except Exception:
            continue
        if not xdata:
            continue
        guid = None
        for tag in xdata:
            if tag[0] == 1000:  # DXF group code 1000 = string
                guid = tag[1]
                break
        if guid is None:
            continue

        pts = list(e.get_points(format='xy'))
        if not pts:
            continue

        # Centroid: average of all vertices, mm → metres
        cx = sum(p[0] for p in pts) / len(pts) / 1000.0
        cy = sum(p[1] for p in pts) / len(pts) / 1000.0

        # If multiple polylines share one GUID (multi-contour wall),
        # average their centroids
        if guid in result:
            old_cx, old_cy, old_n = result[guid][0], result[guid][1], result[guid][2]
            n = old_n + 1
            result[guid] = [(old_cx * old_n + cx) / n,
                            (old_cy * old_n + cy) / n, n]
        else:
            result[guid] = [cx, cy, 1]

    # Strip the count field for output
    return {g: [v[0], v[1]] for g, v in result.items()}


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: dxf_read_positions.py <file.dxf>", file=sys.stderr)
        sys.exit(1)
    positions = read_positions(sys.argv[1])
    print(json.dumps(positions))
