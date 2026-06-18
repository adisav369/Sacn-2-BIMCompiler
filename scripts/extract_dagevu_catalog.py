#!/usr/bin/env python3
# extract_dagevu_catalog.py — EXTRACT a curated, practical INSERT catalog for the DAGeVu modeller from the
# BOM organizer (library/archive/BOM.db). NON-INVENT: every product/dim/ifc_class is read from BOM.db; nothing
# fabricated. prompts/MODELLER_BOM_CATALOG_SPEC.md. Output: a small JSON the modeller loads whole (no 220MB,
# no httpvfs) — LOD-200 box proxies come from M_Product w/d/h dims; real meshes (component_library.db) are a
# later range-load enhancement. User decree: "extract only good practical ones — furniture sets, wall-openings,
# structure (roof/walls/slab) — a good filter system + a cheat sheet of commonly popular sets."
import sqlite3, json, sys, os, base64, math
from collections import defaultdict

BOM = os.path.join(os.path.dirname(__file__), '..', 'library', 'archive', 'BOM.db')
LIB = os.path.join(os.path.dirname(__file__), '..', 'library', 'component_library.db')
OUT = sys.argv[1] if len(sys.argv) > 1 else 'dagevu_catalog.json'
GEOM_OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(OUT), 'dagevu_geometries.json')

# Practical GROUPS → the curated M_Product_Category IDs under each (the filter taxonomy).
GROUPS = [
    {'key': 'structure', 'label': 'Structure',
     'cats': ['IFC_WALL', 'IFC_SLAB', 'IFC_ROOF', 'IFC_BEAM', 'IFC_COLUMN', 'IFC_FOOTING',
              'IFC_COVERING', 'IFC_MEMBER', 'IFC_PLATE', 'IFC_RAILING', 'IFC_STAIR']},
    {'key': 'openings',  'label': 'Wall Openings', 'cats': ['IFC_DOOR', 'IFC_WINDOW']},
    {'key': 'furniture', 'label': 'Furniture',     'cats': ['IFC_FURNITURE', 'IFC_FURNISHINGELEMENT']},
]
MIN_DIM = 0.01   # drop degenerate placeholder dims (sets carry 0.001) — those are assemblies, handled separately.

# Cheat-sheet = commonly popular picks (by product_id), one-tap from any view. Ordered for the quick rail.
CHEAT = ['DOOR_D1', 'DOOR_D1_DOUBLE', 'WINDOW_CASEMENT_819', 'SKYLIGHT_1180',
         'WALL_EXT_BRICK_BLOCK', 'WALL_FOUNDATION_417', 'SLAB_GRADE_127', 'SLAB_FINISH_CERAMIC',
         'HIP_ROOF_MY', 'ROOF_SH_FLAT_4FELT', 'BEAM_W310X60', 'RAILING_HANDRAIL_900',
         'Bed_King', 'Bed_Queen', 'Dining_Table_With_Chairs', 'Base_Cabinet', 'Desk', 'Coffee_Table']


# ── Real-mesh match: index component_library.db component_definitions by ifc_class with bbox VOLUME + the
# geometry_hash, so each curated product picks the nearest-sized REAL extracted part (LOD-300/400 mesh).
def build_mesh_index():
    c = sqlite3.connect(LIB)
    ct = {r[0]: r[1] for r in c.execute('SELECT id, ifc_class FROM component_types')}
    defs = defaultdict(list)
    for r in c.execute('SELECT type_id, geometry_hash, local_min_x, local_max_x, local_min_y, local_max_y, '
                        'local_min_z, local_max_z, face_count FROM component_definitions'):
        ic = ct.get(r[0])
        if not ic:
            continue
        vol = (r[3] - r[2]) * (r[5] - r[4]) * (r[7] - r[6])
        defs[ic].append((r[1], abs(vol), r[8] or 0))      # (geometry_hash, |volume|, face_count)
    return c, defs


def match_hash(defs, ifc, w, d, h):
    # nearest-by-volume, but PREFER a DETAILED mesh — a degenerate 12-tri box only when no richer candidate
    # exists for this ifc_class (else a Bed/Door/Roof would match a box that merely happens to be nearest-sized).
    cand = defs.get(ifc)
    if not cand:
        return None
    vol = (w or 0.1) * (d or 0.1) * (h or 0.1)
    detailed = [x for x in cand if x[2] > 12]
    pool = detailed if detailed else cand
    return min(pool, key=lambda x: abs(x[1] - vol))[0]


def main():
    b = sqlite3.connect(BOM)
    libc, defs = build_mesh_index()
    catname = {r[0]: r[1] for r in b.execute('SELECT M_Product_Category_ID, Name FROM M_Product_Category')}
    catifc = {r[0]: r[1] for r in b.execute('SELECT M_Product_Category_ID, IFC_Class FROM M_Product_Category')}

    products, by_id = [], {}
    out_groups = []
    for g in GROUPS:
        gcats = []
        for cat in g['cats']:
            rows = b.execute(
                'SELECT product_id, COALESCE(Name, product_id), ifc_class, width, depth, height '
                'FROM M_Product WHERE M_Product_Category_ID=? AND COALESCE(is_active,1)=1 '
                'ORDER BY product_id', (cat,)).fetchall()
            items = []
            for pid, name, ifc, w, d, h in rows:
                w, d, h = (w or 0), (d or 0), (h or 0)
                if max(w, d, h) < MIN_DIM:      # placeholder/degenerate → skip (it's an assembly, not a box leaf)
                    continue
                # friendly display name from product_id when Name is null
                disp = name if name and name != pid else pid.replace('_', ' ').title()
                rifc = ifc or catifc.get(cat) or 'IfcBuildingElementProxy'
                gh = match_hash(defs, rifc, w, d, h)        # nearest real extracted mesh for this product
                p = {'id': pid, 'name': disp, 'group': g['key'], 'cat': cat,
                     'catLabel': catname.get(cat, cat), 'ifc_class': rifc,
                     'w': round(w, 4), 'd': round(d, 4), 'h': round(h, 4)}
                if gh:
                    p['gh'] = gh                            # geometry_hash → lazily range-loaded real mesh
                products.append(p); by_id[pid] = p; items.append(pid)
            if items:
                gcats.append({'cat': cat, 'label': catname.get(cat, cat),
                              'ifc_class': catifc.get(cat), 'count': len(items)})
        out_groups.append({'key': g['key'], 'label': g['label'], 'categories': gcats})

    cheat = [pid for pid in CHEAT if pid in by_id]   # only ship cheat picks that survived the filter

    # ── ASSEMBLIES (recursive BOM-INSERT, W-BOM-ASSEMBLY). m_bom = the 59 assemblies with children (BUILDING/
    # FLOOR/ROOM/SET); m_bom_line = the children. A child whose product_id ALSO matches a bom_id is a NESTED
    # assembly (recurse); otherwise it's a leaf product. NON-INVENT: offsets dx/dy/dz (meters) + rotation_rule
    # are read verbatim. rotation_rule numeric = RADIANS → degrees; symbolic (FACE_*/EW/NS/PARALLEL_TO_WALL) is
    # host-relative and not derivable at drop-time → applied as 0° with the label retained (honest).
    def parse_rot(rule):
        if rule is None:
            return 0.0, None
        s = str(rule).strip()
        try:
            return round(float(s) * 180.0 / math.pi, 2), None   # numeric = radians → degrees
        except ValueError:
            return 0.0, s                                        # symbolic → 0°, keep label

    bom_meta = {r[0]: r for r in b.execute(
        'SELECT bom_id, COALESCE(bom_name, bom_id), bom_type, bom_category, '
        'aabb_width_mm, aabb_depth_mm, aabb_height_mm FROM m_bom')}
    bom_ids = set(bom_meta)
    lines_by_bom = defaultdict(list)
    for r in b.execute('SELECT bom_id, child_product_id, role, sequence, dx, dy, dz, rotation_rule '
                       'FROM m_bom_line WHERE COALESCE(is_active, 1)=1 ORDER BY bom_id, sequence'):
        lines_by_bom[r[0]].append(r)

    assemblies, leaf_refs = [], set()
    for bid, rows in lines_by_bom.items():
        meta = bom_meta.get(bid)
        if not meta:
            continue
        children = []
        for (_, ref, role, seq, dx, dy, dz, rrule) in rows:
            if not ref:
                continue
            isbom = ref in bom_ids
            rotDeg, rotRule = parse_rot(rrule)
            ch = {'ref': ref, 'role': role, 'seq': seq or 0, 'dx': round(dx or 0, 4),
                  'dy': round(dy or 0, 4), 'dz': round(dz or 0, 4), 'rotDeg': rotDeg, 'isBom': isbom}
            if rotRule:
                ch['rotRule'] = rotRule                          # symbolic rule retained for honesty
            children.append(ch)
            if not isbom:
                leaf_refs.add(ref)
        if not children:
            continue
        assemblies.append({'id': bid, 'name': meta[1], 'level': meta[2], 'category': meta[3],
                           'w': round((meta[4] or 0) / 1000.0, 3), 'd': round((meta[5] or 0) / 1000.0, 3),
                           'h': round((meta[6] or 0) / 1000.0, 3), 'children': children})

    # CLOSE the leaf set: every leaf product an assembly places must resolve in the catalog (get/meshArrays),
    # even if it isn't in a browse category. Add the missing ones tagged asmOnly (+ nearest real mesh).
    added = 0
    for pid in sorted(leaf_refs):
        if pid in by_id:
            continue
        row = b.execute('SELECT product_id, COALESCE(Name, product_id), ifc_class, width, depth, height, '
                        'M_Product_Category_ID FROM M_Product WHERE product_id=?', (pid,)).fetchone()
        if not row:
            continue                                             # leaf with no product row → unrenderable, skip
        _, name, ifc, w, d, h, cat = row
        w, d, h = (w or 0), (d or 0), (h or 0)
        dimless = max(w, d, h) < MIN_DIM
        if dimless:                                              # dimless leaf → small proxy (the REAL gh mesh,
            w = d = h = 0.25                                     # if matched, is what actually renders)
        rifc = ifc or catifc.get(cat) or 'IfcBuildingElementProxy'
        disp = name if name and name != pid else pid.replace('_', ' ').title()
        gh = match_hash(defs, rifc, w, d, h)
        p = {'id': pid, 'name': disp, 'group': 'asm', 'cat': cat or 'ASM', 'catLabel': catname.get(cat, 'Assembly part'),
             'ifc_class': rifc, 'w': round(w, 4), 'd': round(d, 4), 'h': round(h, 4), 'asmOnly': True}
        if dimless:
            p['dimless'] = True
        if gh:
            p['gh'] = gh
        products.append(p); by_id[pid] = p; added += 1

    catalog = {
        'source': 'library/archive/BOM.db (M_Product_Category + M_Product + m_bom/m_bom_line)',
        'note': 'LOD-200 box proxies from w/d/h dims; real meshes via component_library.db range-load (later).',
        'groups': out_groups,
        'products': products,
        'cheatsheet': cheat,
        'assemblies': assemblies,
    }
    with open(OUT, 'w') as f:
        json.dump(catalog, f, separators=(',', ':'))

    # Emit the LAZY geometry store: hash -> {v,f} base64 (the SAME Float32-pos/Uint32-idx format the modeller
    # already decodes). Fetched on demand (first real-mesh insert), never at boot. Tiny for the curated set;
    # the SAME wiring scales to the full 220MB component_library.db by pointing the loader at it instead.
    hashes = sorted(set(p['gh'] for p in products if p.get('gh')))
    geoms, gbytes = {}, 0
    for gh in hashes:
        row = libc.execute('SELECT vertices, faces FROM component_geometries WHERE geometry_hash=?', (gh,)).fetchone()
        if not row or not row[0] or not row[1]:
            continue
        v, fbuf = bytes(row[0]), bytes(row[1])
        geoms[gh] = {'v': base64.b64encode(v).decode(), 'f': base64.b64encode(fbuf).decode()}
        gbytes += len(v) + len(fbuf)
    with open(GEOM_OUT, 'w') as f:
        json.dump(geoms, f, separators=(',', ':'))

    # SHOW summary (read this — the §-log discipline)
    matched = sum(1 for p in products if p.get('gh'))
    print('§DAGEVU-CATALOG out=%s products=%d matched_mesh=%d cheat=%d groups=%d' % (OUT, len(products), matched, len(cheat), len(out_groups)))
    for g in out_groups:
        print('  [%s] %s: %s' % (g['key'], g['label'],
              ', '.join('%s(%d)' % (c['label'], c['count']) for c in g['categories'])))
    print('  cheat-sheet: %s' % ', '.join(by_id[p]['name'] for p in cheat))
    lvl = defaultdict(int)
    for a in assemblies:
        lvl[a['level']] += 1
    print('§DAGEVU-ASSEMBLY assemblies=%d (%s) leafProductsAdded=%d totalProducts=%d' % (
        len(assemblies), ', '.join('%s:%d' % (k, lvl[k]) for k in sorted(lvl)), added, len(products)))
    print('§DAGEVU-GEOM out=%s geoms=%d rawBytes=%d (~%.2f MB)' % (GEOM_OUT, len(geoms), gbytes, gbytes / 1e6))


if __name__ == '__main__':
    main()
