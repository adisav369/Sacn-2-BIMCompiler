# Pipeline Audit — Name-Sniffing & Hardcoded Bypasses

**Audit Date:** 2026-02-27
**Scope:** Live compiler pipeline (`CompilationPipeline → BuildingParser → BuildingCompiler → StoreyCompiler`)
**Method:** Traced all call chains from `CompilationPipeline.execute()` to verify which patterns are live vs dead code.

## Live Pipeline Path

```
CompilationPipeline
  → BuildingParser.parse(dslContent)
  → BuildingCompiler.compileWithValidation(definition)
    → StoreyCompiler (per storey)
      → BOMTierResolver.resolveForRoom()
      → RelationalResolver.loadRules() / resolve()
      → FurnitureWorker
      → MEPWriter / BuildingWriter
  → PlacementProver (validation)
```

## Dead Code (NOT in live pipeline)

| File | Status | Evidence |
|---|---|---|
| `DSLParser.java` | DEAD | Only called by `BIMCompiler.java`, which has zero callers |
| `BIMCompiler.java` | DEAD | `grep BIMCompiler. → No matches` in live code |
| `PreCompiler.java` | DEAD (live path) | Only called via `BuildingCompiler.compileFromManifest()`, which is only called from `BOMCompileTest` |
| `AutoFitter.java` | DEAD (live path) | Only reached via PreCompiler → LayoutResolver chain |
| `LayoutResolver.java` | DEAD (live path) | Same — test-only path via `compileFromManifest()` |
| `RoomRequirements.java` | DEAD | Only consumed by PreCompiler |
| `RoomType.fromKeyword()` | DEAD (live path) | Only called by DSLParser (dead) and referenced in comments |
| `RoomType.WallRule` | LIVE (inner enum) | Used by `SpaceTypeRegistry.getWallRuleEnum()` and `WallGenerator` |

**Verdict:** `RoomType.java` as a name-sniffing enum is dead in the live pipeline. `SpaceTypeRegistry` (DB-driven via `ad_space_type`) replaced it. The `WallRule` inner enum is still referenced but should migrate to `ad_space_type.wall_rule` string directly.

## LIVE Violations — Hardcoded Data in Active Pipeline

### CRITICAL: Raw JDBC bypassing DAO

| File:Line | Code | Should Be |
|---|---|---|
| `BuildingRegistry.java:72-131` | Raw JDBC `SELECT * FROM c_order` | `ModelQuery<X_C_Order>` |
| `RelationalResolver.java:106` | String concatenation SQL (no `?`) | PreparedStatement with `?` placeholder |

### CRITICAL: Layer boundary breach

| File:Line | Code | Issue |
|---|---|---|
| `RelationalResolver.java:624` | `"IfcFurnishingElement"` hardcoded in resolver | IFC types belong in WRITER layer, not RESOLVER |

### MODERATE: Hardcoded role/type dispatch (should come from BOM metadata)

| File:Line | Sniffs For | Should Use |
|---|---|---|
| `CompilationPipeline.java:323` | `role.contains("LEVEL") \|\| role.contains("GROUND_FLOOR")` | `m_bom.bom_type` field (UNIT/FLOOR/ROOM/SET/ITEM) |
| `StoreyCompiler.java:728,830,2086` | `ctx.isGround ? "FOUNDATION" : "FLOOR"` | `m_bom_line.role` from BOM tree — the BOM already knows what role a slab has |
| `StoreyCompiler.java:196` | `!"RESIDENTIAL".equals(regEntry.type())` | `c_order` field or `ad_sysconfig` flag for ducted HVAC |
| `StoreyCompiler.java:147,161` | `"FLOOR"` / `"FOUNDATION"` as `SlabSpecAD` lookup keys | BOM child role, not hardcoded strings |
| `BuildingWriter.java:507,896` | `"FOUNDATION"`, `"FLOOR"`, `IfcSlab→"FLOOR"` | Writer should receive role from upstream BOM data |
| `ElementPersistence.java:131` | `guid.startsWith("SLAB_") \|\| guid.startsWith("FLOOR_")` | Discipline field on the element record |
| `BuildingParser.java:390` | `between.contains("UNIT ") \|\| contains("SHARED") \|\| contains("ROOF ")` | BOM structural metadata |

### MODERATE: Magic numbers (should be `ad_sysconfig`)

| File:Line | Value | Purpose |
|---|---|---|
| `BOMTierResolver.java:36` | `BIG_ROOM_AREA = 80.0` | Large room threshold |
| `BOMTierResolver.java:37` | `BIG_ROOM_MIN_DIM = 3.0` | Min dimension for big rooms |
| `BOMTierResolver.java:38` | `WALL_OFFSET = 0.5` | Default wall offset (m) |
| `BOMTierResolver.java:39` | `DEFAULT_CEILING_HEIGHT = 3.0` | Ceiling height fallback |
| `BOMTierResolver.java:156` | `area < 2.0` | Minimum room area skip |
| `BOMTierResolver.java:562` | `areaPerSet = 13.0` | Furniture density |
| `RelationalResolver.java:486-488` | `0.5, 0.5, 1.0` | Fallback product dimensions |

### LOW: Wall locator string literals

| File:Line | Strings | Note |
|---|---|---|
| `BOMTierResolver.java:433-791` | `"NORTH_WALL"`, `"SOUTH_WALL"`, `"EAST_WALL"`, `"WEST_WALL"`, `"OPPOSITE_WORK"`, `"CENTER"` | Semantic constants — stable set, but should eventually come from `ad_ref_list` |

## Summary

| Severity | Count | Theme |
|---|---|---|
| CRITICAL | 3 | Raw JDBC bypass, SQL injection risk, IFC in resolver |
| MODERATE | 14 | Hardcoded roles/types, magic numbers |
| LOW | 6 | Wall locator strings |
| **Dead code** | **7 files** | DSLParser, BIMCompiler, PreCompiler, AutoFitter, LayoutResolver, RoomRequirements, RoomType (name-sniffing) |

## Recommended Cleanup Priority

1. **Delete dead code** — DSLParser, BIMCompiler, PreCompiler, AutoFitter, LayoutResolver, RoomRequirements. Safe to remove (no live callers). RoomType.java can be reduced to just the `WallRule` enum or deleted if `SpaceTypeRegistry` handles wall rules.
2. **Fix SQL injection** — `RelationalResolver.java:106` string concatenation → PreparedStatement
3. **Migrate BuildingRegistry to DAO** — `ModelQuery<X_C_Order>` replaces raw JDBC
4. **Remove IFC from resolver** — `RelationalResolver.java:624` move `IfcFurnishingElement` to caller
5. **Extract magic numbers to `ad_sysconfig`** — BOMTierResolver thresholds
6. **Replace role string-sniffing** — `CompilationPipeline.isRoomContent()` and `StoreyCompiler` slab role should read from BOM metadata
