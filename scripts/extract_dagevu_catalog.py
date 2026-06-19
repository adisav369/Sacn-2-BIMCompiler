#!/usr/bin/env python3
# extract_dagevu_catalog.py — EXTRACT a curated, practical INSERT catalog for the DAGeVu modeller from the
# BOM organizer (library/archive/BOM.db). NON-INVENT: every product/dim/ifc_class is read from BOM.db; nothing
# fabricated. prompts/MODELLER_BOM_CATALOG_SPEC.md. Output: a small JSON the modeller loads whole (no 220MB,
# no httpvfs) — LOD-200 box proxies come from M_Product w/d/h dims; real meshes (component_library.db) are a
# later range-load enhancement. User decree: "extract only good practical ones — furniture sets, wall-openings,
# structure (roof/walls/slab) — a good filter system + a cheat sheet of commonly popular sets."
import sqlite3, json, sys, os, base64, math, re
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


# ── Geometry resolution — REPLICATES the authoritative Java chain (the "Java was working perfectly" rule).
# The Java resolves furniture/fixture geometry via ComponentLibrary.getByName(namePattern)
# (DAGCompiler/src/main/java/com/bim/compiler/library/ComponentLibrary.java:38-88):
#     SELECT ... FROM component_definitions WHERE name LIKE '%'||pattern||'%'
#     ORDER BY CASE WHEN name=pattern THEN 0 ELSE 1 END,                       -- EXACT name first
#              (local_max_x-local_min_x)*(local_max_y-local_min_y) DESC        -- else LARGEST XY footprint
#     LIMIT 1
# i.e. exact-name match wins; otherwise the biggest-footprint substring match. This is THE resolver — it
# returns the correctly-named real part with its real bounds, NOT a nearest-volume same-class box. We index
# (name → row) once; get_by_name runs the identical ranking in Python. (match_hash, the old nearest-volume
# heuristic, is retained ONLY as a structural-product last resort where no usable name exists.)
def build_mesh_index():
    c = sqlite3.connect(LIB)
    ct = {r[0]: r[1] for r in c.execute('SELECT id, ifc_class FROM component_types')}
    defs = defaultdict(list)
    by_name = []                                          # [(name, gh, xy_footprint, ifc, w, d, h)]
    gh_fc = {}                                            # geometry_hash → face_count (for honest-box detection)
    for r in c.execute('SELECT type_id, name, geometry_hash, local_min_x, local_max_x, local_min_y, '
                        'local_max_y, local_min_z, local_max_z, face_count FROM component_definitions'):
        ic = ct.get(r[0])
        if not ic:
            continue
        ex = (r[4] - r[3]); ey = (r[6] - r[5]); ez = (r[8] - r[7])
        defs[ic].append((r[2], abs(ex * ey * ez), r[9] or 0))   # (geometry_hash, |volume|, face_count)
        gh_fc[r[2]] = max(gh_fc.get(r[2], 0), r[9] or 0)
        if r[1]:
            by_name.append((r[1], r[2], ex * ey, ic, round(ex, 4), round(ey, 4), round(ez, 4)))
    return c, defs, by_name, gh_fc


def get_by_name(by_name, pattern):
    """Port of Java ComponentLibrary.getByName (ComponentLibrary.java:38-88): WHERE name LIKE '%pattern%'
    ORDER BY (exact-name-first, then largest XY footprint), LIMIT 1. Returns
    (name, geometry_hash, ifc_class, w, d, h) or None. NON-INVENT: pure DB match on the pattern."""
    if not pattern:
        return None
    pl = pattern.lower()
    best = None  # (rank0_exact, -xy_footprint) → smallest tuple wins
    for name, gh, xy, ic, w, d, h in by_name:
        if pl in name.lower():
            key = (0 if name == pattern else 1, -xy)
            if best is None or key < best[0]:
                best = (key, (name, gh, ic, w, d, h))
    return best[1] if best else None


def match_hash(defs, ifc, w, d, h):
    # LAST-RESORT nearest-by-volume for structural products with no usable name pattern. Prefer a DETAILED
    # mesh — a degenerate 12-tri box only when no richer candidate exists for this ifc_class.
    cand = defs.get(ifc)
    if not cand:
        return None
    vol = (w or 0.1) * (d or 0.1) * (h or 0.1)
    detailed = [x for x in cand if x[2] > 12]
    pool = detailed if detailed else cand
    return min(pool, key=lambda x: abs(x[1] - vol))[0]


# ── Spatial placement ports (BOMTierResolver computeZoneAnchor + PhantomLayout GPD walk + LocalCoord rotation).
# No BOM db carries wall locators / openings / ad_room_boundary (verified by query 2026-06-19, see card §DATA
# AVAILABILITY) — the wall-anchored absolute layout was a RUNTIME computation against the building's real room,
# stored only in the cooked extracted.db, never in the BOM recipe. So a modeller drop cannot copy it. What we
# faithfully CAN do: synthesize a square envelope from the assembly aabb and run the cascade's LOGIC against it.
# These ports mirror DAGCompiler coordinate/LocalCoord.java + library/BOMTierResolver.java exactly.
_CARD_RAD = {'south': 0.0, 'north': math.pi, 'west': -math.pi / 2, 'east': math.pi / 2}


def card_to_rad(d):                                          # LocalCoord.cardinalToRadians (L108-116)
    return _CARD_RAD.get(d, 0.0)


_ABS_RULES = ('EW', 'NS')                                    # absolute axis rules — no wall context needed
_REL_RULES = ('FACE_INTO_ROOM', 'FACE_AWAY_FROM_WALL', 'FACE_OUTSIDE', 'PARALLEL_TO_WALL')  # host-relative


def resolve_symbolic_rot(label, wall_ctx):
    """Resolve a SYMBOLIC rotation_rule to radians. Two families:
    • ABSOLUTE axis rules (wall/plate run direction) — EW / NS. Semantics confirmed in
      WitnessBuilder.wallMountedFixture javadoc ("NS" = wall runs north-south along Y; "EW" = east-west along X).
      The Java orients walls via geometry construction, not a rotation; translated to our unit-mesh+rotation
      model: EW → 0° (mesh long axis stays X), NS → +90° (run along Y). No wall context needed.
    • HOST-RELATIVE facing rules — port of LocalCoord.resolveRotation (L80-92): FACE_INTO_ROOM /
      FACE_AWAY_FROM_WALL → face off that wall (Java treats both identically: back-to-wall = facing in);
      FACE_OUTSIDE → opposite (+π); PARALLEL_TO_WALL → +90°. Need a wall context.
    Returns 0.0 for an unknown label or a relative rule with no wall context (honest — not inventable)."""
    if label == 'EW':
        return 0.0
    if label == 'NS':
        return math.pi / 2
    if not wall_ctx:
        return 0.0
    if label in ('FACE_INTO_ROOM', 'FACE_AWAY_FROM_WALL'):
        return card_to_rad(wall_ctx)
    if label == 'FACE_OUTSIDE':
        return card_to_rad(wall_ctx) + math.pi
    if label == 'PARALLEL_TO_WALL':
        return card_to_rad(wall_ctx) + math.pi / 2
    return 0.0


def layout_assembly(asm, by_id, bom_meta):
    """ENVELOPE-RELATIVE placement for ONE assembly (faithful port of BOMTierResolver computeZoneAnchor +
    PhantomLayout GPD walk + LocalCoord.resolveRotation). Mutates each child's dx/dy/rotDeg in place and sets
    autoLayout/wall. Synthesize a square envelope from the assembly aabb (w×d) centred on the drop point; a set
    with NO authored offsets is backed to its LONGEST wall and GPD-walked along it (anchor advances by each
    child's extent; child backed off the wall by half its REAL depth) — so a bathroom's fixtures LINE a wall
    instead of floating in a bare +X row. Symbolic rotation_rule resolves against the established wall; a
    no-rule child of a wall-walk faces the wall (resolveWithGPD L548). HONEST CEILING: faithful to the recipe +
    the cascade's LOGIC, but the envelope is SYNTHESIZED (not the building's real room).
    ⚠ MUST run AFTER the leaf set is closed — else _extent/_depth fall back to defaults for leaf-closure
    products and the walk is NOT flush-backed by real dims (W-BOM-SPATIAL caught exactly this). Returns
    (wall_set, float_set, rot_resolved, rot_unresolved) increments. NON-INVENT: aabb / allocated_width_mm /
    product dims / rotation rules all read from the data."""
    children = asm['children']
    rot_resolved = rot_unresolved = 0

    def _extent(ch):
        if ch['isBom']:
            m = bom_meta.get(ch['ref'])
            w = (m[4] or 0) / 1000.0 if m else 0.0
            return w if w > 0.01 else 0.5                        # nested aabb width; degenerate/missing → 0.5 default
        if ch['_aw'] > 0.01:
            return ch['_aw']                                     # allocated slot width
        p = by_id.get(ch['ref'])
        return (p['w'] if p and p.get('w') else 0.5)            # else the product's own width

    def _depth(ch):
        p = by_id.get(ch['ref'])
        return (p.get('d') or 0.3) if p else 0.3

    def _resolve_child_rot(ch, wall_ctx):
        """Faithful to BOMTierResolver. Precedence: ABSOLUTE EW/NS (any wall) > HOST-RELATIVE FACE_*/PARALLEL
        vs wall_ctx (LocalCoord.resolveRotation; 0°+honest if no wall) > no-rule wall-walk default = the wall
        FACING (card_to_rad(wall), resolveWithGPD L548) > explicit non-zero numeric kept."""
        nonlocal rot_resolved, rot_unresolved
        label = ch.get('rotRule')                               # set only for SYMBOLIC rules (numeric → None)
        if label in _ABS_RULES:
            ch['rotDeg'] = round(resolve_symbolic_rot(label, None) * 180.0 / math.pi, 2); rot_resolved += 1
        elif label in _REL_RULES:
            if wall_ctx:
                ch['rotDeg'] = round(resolve_symbolic_rot(label, wall_ctx) * 180.0 / math.pi, 2); rot_resolved += 1
            else:
                ch['rotDeg'] = 0.0; rot_unresolved += 1         # no wall context — honest 0°, label retained
        elif wall_ctx and abs(ch['rotDeg']) < 0.001:
            ch['rotDeg'] = round(card_to_rad(wall_ctx) * 180.0 / math.pi, 2); rot_resolved += 1

    exts = [_extent(ch) for ch in children]
    W = asm['w'] or 0.0
    Dd = asm['d'] or 0.0
    if max(W, Dd) < 0.01:                                       # assembly carries no aabb → derive from children
        W = max(sum(exts), 0.5)
        Dd = max((_depth(ch) for ch in children), default=0.4)
    minX, minY = -W / 2.0, -Dd / 2.0

    wall_set = float_set = 0
    has_offsets = any(abs(ch['dx']) > 0.001 or abs(ch['dy']) > 0.001 for ch in children)
    if not has_offsets and len(children) > 1:
        along_x = W >= Dd                                       # walk along the longer axis
        wall = 'south' if along_x else 'west'                   # back the set to that (longest) wall
        span = sum(e + ch['_ms'] for e, ch in zip(exts, children)) - children[-1]['_ms']
        cursor = -span / 2.0                                    # centre the run on the drop point
        for ch, ext in zip(children, exts):
            halfD = _depth(ch) / 2.0
            along = cursor + ext / 2.0                          # centre = anchor + half-extent (Java cx=anchor+halfW)
            if along_x:
                ch['dx'] = round(along, 4)
                ch['dy'] = round(minY + halfD, 4)               # backed off the south wall by half depth
            else:
                ch['dy'] = round(along, 4)
                ch['dx'] = round(minX + halfD, 4)               # backed off the west wall by half depth
            cursor += ext + ch['_ms']                           # advance anchor by extent (+ min gap)
            _resolve_child_rot(ch, wall)
        asm['autoLayout'] = 'WALL_LINEAR'
        asm['wall'] = wall
        wall_set = 1
    else:
        # Authored-offset (or single-child) set: keep dx/dy verbatim. No wall established → host-relative FACE_*
        # rules left at 0°+label (honest); only absolute EW/NS resolve. Guessing a nearest wall = invention.
        for ch in children:
            _resolve_child_rot(ch, None)
        if has_offsets:
            float_set = 1
    for ch in children:                                         # drop transient layout inputs from the shipped JSON
        ch.pop('_aw', None); ch.pop('_ms', None)
    return wall_set, float_set, rot_resolved, rot_unresolved


# ── normalizeRole port (FurnitureWorker.java:100-117) → a canonical getByName PATTERN per BOM role. The Java
# normalizeRole switch remaps role tokens (TABLE→DINING_TABLE, CHAIR_*→DINING_CHAIR, DESK→WORKSTATION_DESK …);
# here we map each role to the human-readable library name that getByName resolves to a real, correctly-named,
# correctly-sized component_definitions part (every entry VERIFIED to resolve against component_library.db,
# see the §-audit in MODELLER_BOM_CATALOG_SPEC.md). archive/BOM.db's furniture sets put the real intent in
# `role` (the child_product_id is a bare IFC-class placeholder, child_name_pattern is NULL), so role → name is
# the only non-invent bridge to the generic 220MB library. Unmapped roles (generic FURNITURE, MEP/structural
# terminals with no concrete catalog part) fall through to an honest role-named box proxy.
ROLE_NAME = {
    # Dining / living furniture
    'TABLE': 'Dining_Table', 'DINING_TABLE': 'Dining_Table',
    'CHAIR': 'Dining_Chair', 'CHAIR_A': 'Dining_Chair', 'CHAIR_B': 'Dining_Chair', 'CHAIR_C': 'Dining_Chair',
    'CHAIR_D': 'Dining_Chair', 'CHAIR_E': 'Dining_Chair', 'CHAIR_F': 'Dining_Chair',
    'VISITOR_CHAIR_A': 'Dining_Chair', 'VISITOR_CHAIR_B': 'Dining_Chair',
    'USER_CHAIR': 'Dining_Chair', 'GUEST_SEAT': 'Dining_Chair', 'LOUNGE_CHAIR': 'Dining_Chair',
    'COFFEE_TABLE': 'Coffee_Table', 'SIDE_TABLE': 'Side_Table', 'SIDE_TABLE_A': 'Side_Table',
    'SIDE_TABLE_B': 'Side_Table', 'SIDE_TABLE_L': 'Side_Table', 'SIDE_TABLE_R': 'Side_Table',
    'SOFA': 'Sofa', 'SOFA_B': 'Sofa', 'PIANO': 'Piano', 'BED': 'Bed_King', 'WARDROBE': 'Wardrobe',
    # Workstation
    'DESK': 'Desk',
    # Kitchen / bath cabinetry
    'BASE_CABINET': 'Base_Cabinet', 'UPPER_CABINET': 'Upper_Cabinet', 'VANITY': 'Vanity_Cabinet',
    'VANITY_A': 'Vanity_Cabinet', 'VANITY_B': 'Vanity_Cabinet', 'COUNTER': 'Counter_Top',
    'TALL_CABINET': 'Tall_Cabinet',
    # Sanitary / fixtures — these DO resolve to real library parts (IfcFlowTerminal class) with sensible dims
    'TOILET': 'Toilet', 'FIXTURE': 'Toilet', 'SINK': 'Sink_Island', 'HAND_BIDET': 'Bidet', 'BIDET': 'Bidet',
    'FLOOR_TRAP': 'Floor_Trap',
    # MEP terminals with a clean library name
    'LIGHT': 'Light_Pendant', 'SPRINKLER': 'Sprinkler', 'SPRINKLER_HEAD': 'Sprinkler', 'HEAD': 'Sprinkler',
}


def main():
    b = sqlite3.connect(BOM)
    libc, defs, by_name, gh_fc = build_mesh_index()

    def resolve_geom(pattern, ifc, w, d, h):
        """Java-faithful geometry resolution: getByName(pattern) FIRST (exact-name → largest-XF substring),
        falling back to nearest-volume match_hash ONLY if the name doesn't resolve. Returns
        (gh, name, rw, rd, rh) where name/dims come from the matched real part when name-resolved, else
        (gh-or-None, None, None, None, None). NON-INVENT: pattern and dims are read from the data."""
        hit = get_by_name(by_name, pattern) if pattern else None
        if hit:
            return hit[1], hit[0], hit[3], hit[4], hit[5]      # (gh, name, w, d, h)
        return match_hash(defs, ifc, w, d, h), None, None, None, None

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
                # Java-faithful: the archive product_id IS the human-readable getByName pattern (Bed_King,
                # Dining_Chair, …) → exact/substring match on component_definitions.name. Fall back to
                # nearest-volume only when the id doesn't name a real part (some structural ids don't).
                gh, _rn, _rw, _rd, _rh = resolve_geom(pid, rifc, w, d, h)
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
    for r in b.execute('SELECT bom_id, child_product_id, role, sequence, dx, dy, dz, rotation_rule, '
                       'allocated_width_mm, min_space_mm, component_type '
                       'FROM m_bom_line WHERE COALESCE(is_active, 1)=1 ORDER BY bom_id, sequence'):
        lines_by_bom[r[0]].append(r)

    # ── Role-aware placeholder resolution — REPLICATES the Java geometry chain (BOMTierResolver →
    # PlacedFurniture(role,namePattern) → FurnitureWorker.normalizeRole → ComponentLibrary.getByName).
    # archive/BOM.db's furniture/MEP sets use a BARE IFC-CLASS as child_product_id (a placeholder); both its
    # child_name_pattern column AND the per-prefix XX_BOM.db's are 100% NULL (verified), so the ONLY identity
    # the data carries is `role` (TOILET/BED/CHAIR_A…). The Java's namePattern is likewise role-derived
    # (normalizeRole, FurnitureWorker.java:100), then getByName(namePattern) resolves the real part. We do the
    # SAME: ROLE_NAME[role] → get_by_name → the correctly-named, correctly-sized real component_definitions
    # part. Each DISTINCT role → a DISTINCT synthetic product (no collapse of a set's children to one mesh).
    # NON-INVENT: role is read from the data; ROLE_NAME patterns are verified getByName hits; dims come from
    # the matched real part (or an honest box for roles with no concrete library part, NAMED from the role).
    def _norm_role(role):
        """Strip an instance suffix (CHAIR_A / SINK_2 → CHAIR / SINK) only when the FULL role isn't itself a
        mapped key, so SIDE_TABLE_A keeps SIDE_TABLE etc. Returns the lookup key + a clean display label."""
        s = str(role or '').strip()
        if s in ROLE_NAME:
            return s
        base = re.sub(r'_(?:[A-Z]|\d+)$', '', s)
        return base if base in ROLE_NAME else s

    def _role_product(ifc, role):
        """A per-role synthetic product (distinct id per role) carrying the getByName-resolved real mesh+name+
        dims, or an honest role-named box when the role has no concrete library part."""
        rk = _norm_role(role)
        key = 'ROLE__' + (re.sub(r'[^A-Za-z0-9]+', '_', str(role or 'X')).strip('_').upper() or 'X')
        if key not in by_id:
            pat = ROLE_NAME.get(rk)
            hit = get_by_name(by_name, pat) if pat else None
            label = str(role or ifc).replace('_', ' ').title()
            if hit:                                     # getByName resolved → real mesh + real name + real dims
                name, gh, ric, rw, rd, rh = hit
                p = {'id': key, 'name': label, 'group': 'asm', 'cat': 'ASM', 'catLabel': 'Assembly part',
                     'ifc_class': ifc, 'w': round(abs(rw), 4), 'd': round(abs(rd), 4), 'h': round(abs(rh), 4),
                     'asmOnly': True, 'role': role, 'gh': gh, 'libName': name}
            else:                                       # no concrete library part — honest box, NAMED from role
                p = {'id': key, 'name': label, 'group': 'asm', 'cat': 'ASM', 'catLabel': 'Assembly part',
                     'ifc_class': ifc, 'w': 0.4, 'd': 0.4, 'h': 0.4, 'asmOnly': True, 'role': role}
                gh2 = match_hash(defs, ifc, 0.4, 0.4, 0.4)
                (p.__setitem__('gh', gh2) if gh2 else p.__setitem__('dimless', True))
            products.append(p); by_id[key] = p
        return key, ('gh' in by_id[key] and not by_id[key].get('dimless') and by_id[key].get('libName') is not None)

    role_resolved = role_proxy = phantom_skipped = 0
    rot_resolved = rot_unresolved = sets_wall = sets_float = 0
    assemblies, leaf_refs = [], set()
    for bid, rows in lines_by_bom.items():
        meta = bom_meta.get(bid)
        if not meta:
            continue
        children = []
        for (_, ref, role, seq, dx, dy, dz, rrule, aw_mm, ms_mm, ctype) in rows:
            if not ref:
                continue
            if str(ctype or '').upper() == 'PHANTOM' or ref == 'BUFFER':
                phantom_skipped += 1                    # layout spacer — skip (Java explodeBomTree parity)
                continue
            isbom = ref in bom_ids
            if not isbom and ref.startswith('Ifc'):     # bare IFC-class placeholder → resolve by role (Java way)
                ref, named = _role_product(ref, role)
                if named:
                    role_resolved += 1                  # role → getByName-resolved real part (mesh + name + dims)
                else:
                    role_proxy += 1                     # role with no concrete library part → honest named box
            rotDeg, rotRule = parse_rot(rrule)
            ch = {'ref': ref, 'role': role, 'seq': seq or 0, 'dx': round(dx or 0, 4),
                  'dy': round(dy or 0, 4), 'dz': round(dz or 0, 4), 'rotDeg': rotDeg, 'isBom': isbom,
                  '_aw': (aw_mm or 0) / 1000.0, '_ms': (ms_mm or 0) / 1000.0}   # transient: alloc width + min space (m)
            if rotRule:
                ch['rotRule'] = rotRule                          # symbolic rule retained for honesty
            children.append(ch)
            if not isbom:
                leaf_refs.add(ref)
        if not children:
            continue
        asm = {'id': bid, 'name': meta[1], 'level': meta[2], 'category': meta[3],
               'w': round((meta[4] or 0) / 1000.0, 3), 'd': round((meta[5] or 0) / 1000.0, 3),
               'h': round((meta[6] or 0) / 1000.0, 3), 'children': children}
        assemblies.append(asm)            # layout runs in PASS 2 (after the leaf set is closed — see below)

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
        # Java-faithful: getByName(product_id) FIRST (the id is the human-readable name), else nearest-volume.
        gh, _rn, _rw, _rd, _rh = resolve_geom(pid, rifc, w, d, h)
        p = {'id': pid, 'name': disp, 'group': 'asm', 'cat': cat or 'ASM', 'catLabel': catname.get(cat, 'Assembly part'),
             'ifc_class': rifc, 'w': round(w, 4), 'd': round(d, 4), 'h': round(h, 4), 'asmOnly': True}
        if dimless:
            p['dimless'] = True
        if gh:
            p['gh'] = gh
        products.append(p); by_id[pid] = p; added += 1

    # ── PASS 2: ENVELOPE-RELATIVE LAYOUT — run NOW that every leaf product's real dims are in by_id (the leaf
    # set is closed). Running this inside pass 1 fell back to default 0.3/0.5 dims for leaf-closure products →
    # the wall-walk was NOT flush-backed by real depth (W-BOM-SPATIAL caught all 36 such mismatches).
    for asm in assemblies:
        ws, fs, rr, ru = layout_assembly(asm, by_id, bom_meta)
        sets_wall += ws; sets_float += fs; rot_resolved += rr; rot_unresolved += ru

    # HONEST-BOX flag: a product whose getByName-resolved mesh is itself ≤12 faces means the library's BEST part
    # for it IS a box (e.g. Wardrobe — the only Wardrobe in component_library is 12 faces). Mark it so witnesses
    # don't fault an honest box as a missing detailed mesh (same honesty as the W-LOD300 honest-box justification).
    box_only = 0
    for p in products:
        if p.get('gh') and gh_fc.get(p['gh'], 0) <= 12:
            p['boxOnly'] = True; box_only += 1

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
    print('§DAGEVU-ROLERESOLVE phantomSkipped=%d roleResolvedToConcrete=%d perRoleProxies=%d' % (
        phantom_skipped, role_resolved, role_proxy))
    print('§DAGEVU-SPATIAL wallAnchoredSets=%d floatOffsetSets=%d rotSymbolicResolved=%d rotSymbolicUnresolvable=%d' % (
        sets_wall, sets_float, rot_resolved, rot_unresolved))
    print('§DAGEVU-BOXONLY honestBoxProducts=%d (library best part is a box ≤12 faces)' % box_only)
    print('§DAGEVU-GEOM out=%s geoms=%d rawBytes=%d (~%.2f MB)' % (GEOM_OUT, len(geoms), gbytes, gbytes / 1e6))


if __name__ == '__main__':
    main()
