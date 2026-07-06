# S171 — MappingSource Tessellation Dedup

## DO NOT REMOVE
Scope: Skip redundant tessellation for elements sharing the same IfcMappedItem
MappingSource. Tessellate once per unique shape, copy+transform for instances.
Read the log after every run.

You are a coder. One bounded task.

---

## The Problem

IfcOpenShell tessellates every element independently, even when hundreds share
the same shape definition (IfcMappedItem → MappingSource). Hospital: 31,939
mapped instances from only 2,523 unique sources (12.7x reuse). Each tessellation
takes ~50ms of OpenCASCADE compute. That's 25 minutes of redundant work.

## Evidence (Hospital)

```
IfcMappedItem instances: 31,939
Unique MappingSources:    2,523
Reuse ratio:              12.7x
Skippable tessellations: 29,416 (92%)
```

## Architecture

### Pre-scan phase (new, ~1 second)

Before the main extraction loop, scan all elements for IfcMappedItem references:

```python
# Group elements by MappingSource id
# key = MappingSource entity id
# value = list of (element, instance_transform)
mapping_groups = {}
for elem in all_elements:
    rep = elem.Representation
    if rep:
        for sub_rep in rep.Representations:
            for item in sub_rep.Items:
                if item.is_a('IfcMappedItem'):
                    src_id = item.MappingSource.id()
                    mapping_groups.setdefault(src_id, []).append(
                        (elem, item.MappingTarget))
```

### Fast path in tessellation loop

```python
# Cache: MappingSource id -> (local_verts, faces)
tessellation_cache = {}

for element in elements_to_extract:
    mapped_source_id = get_mapping_source_id(element)

    if mapped_source_id and mapped_source_id in tessellation_cache:
        # FAST PATH: copy cached mesh, apply instance transform
        base_verts, faces = tessellation_cache[mapped_source_id]
        instance_transform = get_instance_transform(element)
        verts = apply_transform(base_verts, instance_transform)
        # ... hash, store as usual
    else:
        # SLOW PATH: full tessellation (existing code, unchanged)
        verts, faces = tessellate_with_ifcopenshell(element)
        if mapped_source_id:
            tessellation_cache[mapped_source_id] = (verts, faces)
        # ... hash, store as usual
```

### Transform handling

IfcMappedItem has a MappingTarget (IfcCartesianTransformationOperator) which
defines how the source shape is placed for this instance: translation, rotation,
scale, and optionally mirror.

The extraction already computes per-instance world placement (center_x/y/z,
rotation_x/y/z). With USE_WORLD_COORDS=False (S168), vertices are in local
coords relative to the element's placement. So the MappingTarget transform
is already factored into the local coords by IfcOpenShell's geometry iterator.

Key insight: if two elements share the same MappingSource, their LOCAL vertices
(with USE_WORLD_COORDS=False) should be identical — the MappingTarget only
affects world placement, which we store separately. So the "fast path" is
simply: copy the vertices as-is, no transform math needed.

Validation: compare geometry_hash of fast-path result vs slow-path result
for a sample. If they match, the dedup is correct.

## Fallback

If fast-path hash differs from what slow-path would produce (detected by
spot-checking N elements), fall through to slow path for that MappingSource
group. Log as §DEDUP_MISMATCH.

## Logging

```
§DEDUP_SCAN    31,939 IfcMappedItem, 2,523 unique sources (12.7x reuse)
§DEDUP_SKIP    29,416 tessellations skipped (92%)
§DEDUP_VERIFY  100/100 spot-checks matched (fast=slow)
§DEDUP_MISMATCH src_id=1234: fast hash differs, falling back to slow path
§DEDUP_TIME    tessellation: 142.3s (was ~1400s without dedup, 9.8x speedup)
§DEDUP_TIME    fast_path: 8.2s for 29,416 copies (0.28ms/copy)
§DEDUP_TIME    slow_path: 134.1s for 2,523 unique tessellations (53ms/tess)
§DEDUP_TIME    total extraction: 185.4s (was ~1800s, 9.7x speedup)
```

### Timing instrumentation

Track and report:
- `t_scan` — pre-scan phase duration
- `t_slow` — cumulative time in slow path (real tessellations)
- `t_fast` — cumulative time in fast path (cache copies)
- `n_slow` / `n_fast` — count of slow vs fast path invocations
- `speedup` — estimated: `(n_slow + n_fast) * avg_slow_time / (n_slow * avg_slow_time + t_fast)`
- Print all as `§DEDUP_TIME` lines in the summary block

## Files to edit

- EDIT: `DAGCompiler/python/extractIFCtoDB.py` — add pre-scan + fast path
- No other files affected

## DO NOT

- Change the output format of _extracted.db or component_library.db
- Change geometry_hash computation
- Skip elements that don't have IfcMappedItem (they go through slow path as today)
- Modify any Blender/federation code — this is extraction only
