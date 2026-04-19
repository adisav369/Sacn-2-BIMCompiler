# ⚠ DO NOT REMOVE
# Scope: S200 — RTree Preview speed: building-level bboxes for city, element bboxes on drill-in
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: TODO

## Problem

RTree Preview takes ~13s for 1M elements. It queries every element's bbox
from the rtree and creates 1M wireframe boxes for GPU rendering. Most are
invisible from city orbit — only the building silhouettes matter.

## Solution: Two-level bbox loading

### Level 0 — City view (on Preview click)

Query ONE bbox per building (786 rows instead of 1M):

```sql
SELECT m.building,
       MIN(r.minX), MAX(r.maxX), MIN(r.minY), MAX(r.maxY),
       MIN(r.minZ), MAX(r.maxZ),
       COUNT(*)
FROM elements_rtree r
JOIN elements_meta m ON r.id = m.rowid
GROUP BY m.building
```

786 wireframe boxes. Sub-1-second. Search, building list, fly-to all work
from building_centres (already bootstrapped by Direct Stream).

### Level 1 — Building drill-in (on click/fly-to)

When user clicks a building or Direct Stream activates one, load that
building's element bboxes:

```sql
SELECT r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
       m.guid, m.discipline
FROM elements_rtree r
JOIN elements_meta m ON r.id = m.rowid
WHERE m.building = ?
```

One building = 1K-60K elements. Takes 0.5-3s. GPU batches created per
discipline. Search within building works. Click-to-identify works.

### Single-building DBs

When DB has no `building` column (single building), skip Level 0 — load
all element bboxes directly (existing behavior). Only multi-building DBs
get the two-level optimization.

### What changes

- `bbox_visualization.py`: new `_create_building_level_batches()` function
  that creates one wireframe box per building. Replaces `_create_gpu_batches()`
  for initial load.
- `bbox_visualization.py`: `_expand_building_bboxes(building)` loads element
  bboxes for one building on demand. Called by fly-to, click, Direct Stream.
- `operator.py`: `FedRTreePreview.execute()` calls building-level first.
- Search: building-level search works on building_centres. Element search
  triggers expand for matched buildings.

### Exit criteria

1. Preview on sandbox_1M.db: < 2 seconds (was 13s)
2. Click building → element bboxes load in < 3s
3. Single-building DB: no regression — all elements load on Preview
4. Search "window" → buildings listed instantly, element bboxes expand on click

### Performance estimate

| Step | Rows | Est. time |
|------|------|-----------|
| Building bboxes (786) | 786 | < 0.5s |
| Expand Hospital (64K) | 64,000 | ~2s |
| Expand SampleHouse (65) | 65 | < 0.1s |
| Search building names | 786 string matches | instant |
