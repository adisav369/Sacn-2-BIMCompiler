"""
GPU bbox benchmark: draw 1M bounding boxes with ZERO bpy.data.objects.
Run: blender --background --python scripts/stress_gpu_bbox.py

Reads elements_rtree from sandbox_1M.db, builds GPU batches, measures
time to first frame. No Blender objects created at all.
"""
import bpy
import gpu
import sqlite3
import time
import struct
import resource
from gpu_extras.batch import batch_for_shader
from pathlib import Path

DB = Path(__file__).parent / "sandbox_1M.db"

DISC_COLORS = {
    'ARC': (0.7, 0.7, 0.7, 0.6),
    'STR': (0.5, 0.5, 0.5, 0.6),
    'ACMV': (0.3, 0.7, 1.0, 0.6),
    'ELEC': (1.0, 0.9, 0.2, 0.6),
    'FP': (1.0, 0.2, 0.2, 0.6),
    'PLB': (0.0, 0.4, 1.0, 0.6),
    'HEAT': (0.8, 0.4, 0.0, 0.6),
    'HVAC': (0.2, 0.8, 0.6, 0.6),
    'MEP': (0.6, 0.3, 0.8, 0.6),
    'SAN': (0.0, 0.6, 0.3, 0.6),
    'VENT': (0.4, 0.8, 0.4, 0.6),
    'VOID': (0.3, 0.3, 0.3, 0.3),
}
DEFAULT_COLOR = (0.6, 0.6, 0.6, 0.5)

def ram_mb():
    return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024

def bbox_edges(x0, x1, y0, y1, z0, z1):
    """8 vertices, 12 edges as line pairs = 24 vertices."""
    v = [
        (x0,y0,z0),(x1,y0,z0),(x1,y1,z0),(x0,y1,z0),
        (x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1),
    ]
    edges = [(0,1),(1,2),(2,3),(3,0),(4,5),(5,6),(6,7),(7,4),(0,4),(1,5),(2,6),(3,7)]
    return [v[a] for a, b in edges for a, b in [(a, b)]] + [v[b] for a, b in edges]

# ── Query DB ─────────────────────────────────────────────────────
print(f"\n{'='*60}")
print("GPU BBOX BENCHMARK — zero Blender objects")
print(f"{'='*60}")

t0 = time.perf_counter()
conn = sqlite3.connect(str(DB))
rows = conn.execute("""
    SELECT m.discipline, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
    FROM elements_meta m
    JOIN elements_rtree r ON m.id = r.id
""").fetchall()
conn.close()
t_query = time.perf_counter() - t0
print(f"DB query: {len(rows):,} elements in {t_query:.2f}s — RAM {ram_mb():.0f} MB")

# ── Build GPU batches by discipline ──────────────────────────────
t1 = time.perf_counter()
verts_by_disc = {}
for disc, x0, x1, y0, y1, z0, z1 in rows:
    if disc not in verts_by_disc:
        verts_by_disc[disc] = []
    v = [
        (x0,y0,z0),(x1,y0,z0), (x1,y0,z0),(x1,y1,z0),
        (x1,y1,z0),(x0,y1,z0), (x0,y1,z0),(x0,y0,z0),
        (x0,y0,z1),(x1,y0,z1), (x1,y0,z1),(x1,y1,z1),
        (x1,y1,z1),(x0,y1,z1), (x0,y1,z1),(x0,y0,z1),
        (x0,y0,z0),(x0,y0,z1), (x1,y0,z0),(x1,y0,z1),
        (x1,y1,z0),(x1,y1,z1), (x0,y1,z0),(x0,y1,z1),
    ]
    verts_by_disc[disc].extend(v)

t_group = time.perf_counter() - t1
print(f"Grouped into {len(verts_by_disc)} disciplines in {t_group:.2f}s — RAM {ram_mb():.0f} MB")

t2 = time.perf_counter()
shader = gpu.shader.from_builtin('UNIFORM_COLOR')
batches = {}
total_verts = 0
for disc, verts in verts_by_disc.items():
    batch = batch_for_shader(shader, 'LINES', {"pos": verts})
    batches[disc] = batch
    total_verts += len(verts)
    print(f"  {disc}: {len(verts)//24:,} bboxes — {len(verts):,} vertices")

t_batch = time.perf_counter() - t2
total_time = time.perf_counter() - t0

print(f"\n{'='*60}")
print(f"RESULT")
print(f"{'='*60}")
print(f"Elements:     {len(rows):,}")
print(f"GPU vertices: {total_verts:,}")
print(f"Blender objects created: 0")
print(f"DB query:     {t_query:.2f}s")
print(f"Grouping:     {t_group:.2f}s")
print(f"GPU batches:  {t_batch:.2f}s")
print(f"TOTAL:        {total_time:.2f}s")
print(f"RAM:          {ram_mb():.0f} MB")
print(f"{'='*60}")
