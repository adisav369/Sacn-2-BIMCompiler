# Gap 4 (Partial): LPG Wall Thickness Bug + Insulation BOM Child

**Spec:** DISC_VALIDATION_DB_SRS §10.4.12 Gap 4
**Prereq:** P120 DONE (standard citation map).

You are a coder for bim-compiler. Two bounded tasks.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Wall thickness comes from ARC data. Insulation is a BOM child product. No invention.

## Read first

1. `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.12 Gap 4 — the spec table
3. `BIM_COBOL/src/main/java/com/bim/cobol/route/LpgRouteBuilder.java` — the bug: calls `slabThickness()` for wall penetration
4. `BIM_COBOL/src/main/java/com/bim/cobol/geometry/SqlBuildingGeometry.java` — `slabThickness()` and what queries are available
5. `BIM_COBOL/src/main/java/com/bim/cobol/geometry/BuildingGeometry.java` — interface

## Task 1 — LPG Wall Thickness Bug

### Problem

`LpgRouteBuilder` calls `slabThickness()` when computing wall penetration sleeve length. `slabThickness()` returns structural slab thickness — wrong value for a wall penetration. Should query wall thickness.

### Fix

Add `wallThickness(String floorRef)` to BuildingGeometry interface. Implement in SqlBuildingGeometry:
- Query c_orderline for LEAF elements with `family_ref LIKE '%WALL%'` and `Discipline = 'ARC'` on the given floor
- Return average wall thickness (W dimension from AABB)
- Fallback: 200mm (standard concrete block wall)

Update LpgRouteBuilder to call `wallThickness()` for PENETRATE ops through walls.

## Task 2 — Insulation BOM Child

### Problem

Pipe and duct segments have no insulation. In practice, every MEP segment has insulation wrapping — thickness depends on discipline and medium temperature.

### Fix

Add insulation as a BOM child of pipe/duct segments in the RouteResult. Each segment gets an insulation child product with:
- `product_ref`: derived from discipline (e.g., `INSULATION_FP_25`, `INSULATION_ACMV_50`)
- `thickness_mm`: from `ad_sysconfig` per discipline (key: `INSULATION_THICKNESS_<DISC>`)
- `length_mm`: same as parent segment
- `qty`: 1 per segment

Default insulation thickness if no ad_sysconfig entry:

| Discipline | Default thickness | Rationale |
|-----------|------------------|-----------|
| FP | 25mm | Fire-rated pipe wrap |
| ACMV | 50mm | Thermal duct insulation |
| CW | 25mm | Condensation prevention |
| SP | 0mm (none) | Waste pipes typically uninsulated |
| ELEC | 0mm (none) | Cable trays uninsulated |
| LPG | 25mm | Gas pipe protection |

Do NOT create insulation M_Product entries — just add the product_ref string to RouteResult. Product creation is a separate ERP.db seed task.

## Gate

Run TE:
```bash
./scripts/run_RosettaStones.sh classify_te.yaml
```
- FINE log: LPG penetrations use wall thickness (not slab thickness)
- FINE log: insulation child products on FP/ACMV/CW/LPG segments
- system_nodes count increases (insulation nodes added)
- TE gate: no regression

Run SH:
```bash
./scripts/run_RosettaStones.sh classify_sh.yaml
```
- SH 7/7+ PASS (no regression)

## What NOT to do

- Do NOT modify CrawlRouter or CrawlOps
- Do NOT create M_Product entries for insulation (just product_ref strings)
- Do NOT modify existing migration files
- Do NOT add insulation to ARC/STR elements — MEP only
- **All logging via BIMLogger — no System.out.println**

## Spec citation

```java
// Implementing DISC_VALIDATION_DB_SRS §10.4.12 Gap 4 — LPG wall thickness + insulation BOM child
// LPG: wallThickness() replaces slabThickness() for PENETRATE ops
// Insulation: BOM child product per discipline from ad_sysconfig
```

## Commit

```bash
git add BIM_COBOL/src/main/java/com/bim/cobol/geometry/BuildingGeometry.java \
        BIM_COBOL/src/main/java/com/bim/cobol/geometry/SqlBuildingGeometry.java \
        BIM_COBOL/src/main/java/com/bim/cobol/route/LpgRouteBuilder.java \
        BIM_COBOL/src/main/java/com/bim/cobol/RouteExecutorImpl.java \
        PROGRESS.md
git commit -m "[S100-p121] LPG wall thickness fix + insulation BOM child per discipline"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- LPG: wall thickness value used (vs old slab thickness)
- Insulation: which disciplines got insulation children? Thickness values?
- system_nodes count before/after
- SH 7/7, TE gate
- Any surprises — document, do NOT fix

---
