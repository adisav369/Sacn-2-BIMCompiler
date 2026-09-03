"""
DB Loader — Thin AABB proxy loader for compiled BIM output.

Reads output.db (compiled building) and creates box-mesh proxies in Blender.
This is the SIMPLE loader: one box per element from rtree bounds.
Federation's full loader replaces this when tessellation is needed.
"""

import sqlite3
from typing import Dict, Optional, Tuple

try:
    import bpy
    import bmesh
    HAS_BPY = True
except ImportError:
    HAS_BPY = False

_LOADED_TAG = "bim_designer_loaded"
_material_cache: Dict[str, "bpy.types.Material"] = {}


def load_from_db(db_path: str, building_name: str = "Building") -> int:
    """Load all elements from output.db as AABB box proxies.

    Args:
        db_path: Path to output.db (compiled building).
        building_name: Root collection name.

    Returns:
        Number of elements loaded.
    """
    if not HAS_BPY:
        raise RuntimeError("db_loader requires Blender (bpy)")

    _material_cache.clear()

    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    rows = cur.execute(
        "SELECT m.id, m.guid, m.discipline, m.ifc_class, m.storey,"
        "       m.material_name, m.material_rgba, m.element_name,"
        "       r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ "
        "FROM elements_meta m "
        "JOIN elements_rtree r ON r.id = m.id"
    ).fetchall()
    conn.close()

    root_col = _get_or_create_collection(building_name, bpy.context.scene.collection)
    count = 0

    for row in rows:
        storey = row["storey"] or "Unknown"
        discipline = row["discipline"] or "GEN"

        storey_col = _get_or_create_collection(storey, root_col)
        disc_col = _get_or_create_collection(discipline, storey_col)

        obj = _create_box(
            name=row["element_name"] or row["guid"] or f"elem_{row['id']}",
            min_x=row["minX"], max_x=row["maxX"],
            min_y=row["minY"], max_y=row["maxY"],
            min_z=row["minZ"], max_z=row["maxZ"],
        )
        obj[_LOADED_TAG] = True

        mat = _get_or_create_material(row["material_name"], row["material_rgba"])
        if mat:
            obj.data.materials.append(mat)

        disc_col.objects.link(obj)
        count += 1

    return count


def clear_loaded():
    """Remove all objects created by this loader."""
    if not HAS_BPY:
        return
    to_remove = [obj for obj in bpy.data.objects if obj.get(_LOADED_TAG)]
    for obj in to_remove:
        mesh = obj.data if obj.type == "MESH" else None
        bpy.data.objects.remove(obj, do_unlink=True)
        if mesh and mesh.users == 0:
            bpy.data.meshes.remove(mesh)
    _material_cache.clear()


# ── internal helpers ──────────────────────────────────────────────


def _get_or_create_collection(name: str, parent) -> "bpy.types.Collection":
    for child in parent.children:
        if child.name == name:
            return child
    col = bpy.data.collections.new(name)
    parent.children.link(col)
    return col


def _create_box(name: str,
                min_x: float, max_x: float,
                min_y: float, max_y: float,
                min_z: float, max_z: float) -> "bpy.types.Object":
    """Create a box mesh from AABB bounds."""
    cx = (min_x + max_x) / 2.0
    cy = (min_y + max_y) / 2.0
    cz = (min_z + max_z) / 2.0
    sx = (max_x - min_x) / 2.0
    sy = (max_y - min_y) / 2.0
    sz = (max_z - min_z) / 2.0

    verts = [
        (cx - sx, cy - sy, cz - sz),
        (cx + sx, cy - sy, cz - sz),
        (cx + sx, cy + sy, cz - sz),
        (cx - sx, cy + sy, cz - sz),
        (cx - sx, cy - sy, cz + sz),
        (cx + sx, cy - sy, cz + sz),
        (cx + sx, cy + sy, cz + sz),
        (cx - sx, cy + sy, cz + sz),
    ]
    faces = [
        (0, 1, 2, 3),  # bottom
        (4, 5, 6, 7),  # top
        (0, 1, 5, 4),  # front
        (2, 3, 7, 6),  # back
        (0, 3, 7, 4),  # left
        (1, 2, 6, 5),  # right
    ]

    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.update()

    obj = bpy.data.objects.new(name, mesh)
    return obj


def _parse_rgba(rgba_str: Optional[str]) -> Optional[Tuple[float, float, float, float]]:
    """Parse '0.6,0.3,0.15,1.0' to (0.6, 0.3, 0.15, 1.0)."""
    if not rgba_str:
        return None
    try:
        parts = [float(x.strip()) for x in rgba_str.split(",")]
        if len(parts) == 4:
            return tuple(parts)
    except (ValueError, AttributeError):
        pass
    return None


def _get_or_create_material(mat_name: Optional[str],
                            rgba_str: Optional[str]) -> Optional["bpy.types.Material"]:
    """Return cached material or create a new one."""
    if not mat_name:
        return None
    if mat_name in _material_cache:
        return _material_cache[mat_name]

    mat = bpy.data.materials.new(name=mat_name)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")

    rgba = _parse_rgba(rgba_str)
    if rgba and bsdf:
        bsdf.inputs["Base Color"].default_value = rgba

    _material_cache[mat_name] = mat
    return mat
