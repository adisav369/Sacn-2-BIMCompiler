# DSL Extension Guide

How to add new constraint types to the BIM Compiler DSL.

## Architecture Overview

```
┌─────────────┐    ┌──────────────┐    ┌─────────────┐    ┌──────────────┐
│  DSL Text   │───▶│    Parser    │───▶│   Solver    │───▶│   Compiler   │
│             │    │              │    │             │    │              │
│ adjacent:   │    │ RoomDef with │    │ Choco CSP   │    │ Geometry +   │
│ exterior:   │    │ constraints  │    │ constraints │    │ Walls/Doors  │
└─────────────┘    └──────────────┘    └─────────────┘    └──────────────┘
```

## Data Flow Example: `adjacent:` Constraint

### Step 1: DSL Text → Parser

```
BEDROOM "master" size:4x4m {
    adjacent: ensuite
}
```

**File:** `BuildingParser.java`

```java
// Pattern to match
private static final Pattern ADJACENT_PATTERN = Pattern.compile(
    "(?<!not_)adjacent:\\s*(\\w+)"
);

// In parseRoom():
Matcher adjMatcher = ADJACENT_PATTERN.matcher(roomContent);
while (adjMatcher.find()) {
    adjacentTo.add(adjMatcher.group(1));
}
```

### Step 2: Parser → Definition Object

**File:** `BuildingDefinition.java`

```java
public record RoomDef(
    String type,
    String name,
    String gridPosition,
    double width,
    double depth,
    List<OpeningDef> openings,
    // ... MEP fields ...
    List<String> adjacentTo,      // ← Constraint stored here
    List<String> notAdjacentTo,
    String exteriorWall
) {
    public boolean needsSolverPlacement() {
        return gridPosition == null && hasConstraints();
    }
}
```

### Step 3: Definition → Solver Input

**File:** `BuildingCompiler.java`

```java
// Build constraints for solver
List<RoomConstraint> constraints = new ArrayList<>();
for (RoomDef room : storey.rooms()) {
    if (room.needsSolverPlacement()) {
        constraints.add(new RoomConstraint(
            room.name(),
            (int) Math.ceil(room.width()),
            (int) Math.ceil(room.depth()),
            room.adjacentTo(),       // ← Passed to solver
            room.notAdjacentTo(),
            room.exteriorWall()
        ));
    }
}
```

### Step 4: Solver → CSP Constraint

**File:** `SpaceSolver.java`

```java
// Adjacent constraint: rooms must share an edge
for (String adjRoom : constraint.adjacentTo()) {
    RoomVar other = roomVars.get(adjRoom);
    if (other != null) {
        // Option 1: r1.maxX == r2.minX (r1 left of r2)
        // Option 2: r2.maxX == r1.minX (r2 left of r1)
        // Option 3: r1.maxY == r2.minY (r1 below r2)
        // Option 4: r2.maxY == r1.minY (r2 below r1)

        model.or(
            model.and(
                model.arithm(rv.x, "+", rv.w, "=", other.x),
                model.arithm(rv.y, "<", other.y, "+", other.h),
                model.arithm(other.y, "<", rv.y, "+", rv.h)
            ),
            // ... other options
        ).post();
    }
}
```

### Step 5: Solver → Position

**File:** `SpaceSolver.java`

```java
public record SolvedLayout(
    boolean feasible,
    Map<String, GridPosition> positions,  // ← Output
    long solveTimeMs,
    String failureReason
) {}

// After solving:
positions.put(room.name(), new GridPosition(
    roomVar.x.getValue(),
    roomVar.y.getValue()
));
```

### Step 6: Position → Geometry

**File:** `BuildingCompiler.java`

```java
// Inject solved position back into definition
GridPosition pos = layout.positions().get(room.name());
String gridRef = SpaceSolver.toGridRef(pos);
resolvedRooms.add(room.withPosition(gridRef));

// Later, geometry generated from position
double roomMinX = coords[0];  // X from grid column
double roomMinY = coords[1];  // Y from grid row
```

---

## Adding a New Constraint Type

### Template: 6-Step Process

#### 1. Define Syntax

Decide on DSL syntax:
```
NEW_CONSTRAINT: value
```

#### 2. Add Parser Pattern

In `BuildingParser.java`:
```java
private static final Pattern NEW_CONSTRAINT_PATTERN = Pattern.compile(
    "new_constraint:\\s*(\\w+)"
);
```

#### 3. Add Field to RoomDef

In `BuildingDefinition.java`:
```java
public record RoomDef(
    // ... existing fields ...
    String newConstraintValue  // NEW
) {
    public boolean hasConstraints() {
        return !adjacentTo.isEmpty() ||
               !notAdjacentTo.isEmpty() ||
               exteriorWall != null ||
               newConstraintValue != null;  // NEW
    }
}
```

#### 4. Pass to Solver

In `BuildingCompiler.java`:
```java
constraints.add(new RoomConstraint(
    // ... existing params ...
    room.newConstraintValue()  // NEW
));
```

#### 5. Implement CSP Logic

In `SpaceSolver.java`:
```java
if (constraint.newConstraintValue() != null) {
    // Add Choco constraint
    model.arithm(...).post();
}
```

#### 6. Write Test

Create test file with DSL using new constraint, verify:
- Parser extracts value correctly
- Solver satisfies constraint
- Geometry is valid

---

## Constraint Categories

### Horizontal (Same Storey)

| Constraint | CSP Logic |
|------------|-----------|
| `adjacent:` | Shared edge (4 options) |
| `not_adjacent:` | Gap exists (no shared edge) |
| `exterior:` | Room edge = building edge |

### Vertical (Cross-Storey)

| Constraint | CSP Logic |
|------------|-----------|
| `aligns:` | Same X,Y as target room |
| `above:` | Same X,Y, storey = target + 1 |
| `below:` | Same X,Y, storey = target - 1 |

### Structural

| Constraint | CSP Logic |
|------------|-----------|
| `spans:` | Room covers multiple grid cells |
| `on_grid:` | Room corners on structural grid |

---

## Files to Modify

| File | Purpose |
|------|---------|
| `BuildingParser.java` | Add pattern, extract value |
| `BuildingDefinition.java` | Add field to RoomDef |
| `BuildingCompiler.java` | Pass to solver |
| `SpaceSolver.java` | Implement CSP logic |
| `*Test.java` | Verify constraint works |

---

## Testing Checklist

- [ ] Parser extracts constraint value
- [ ] Definition stores constraint
- [ ] Solver receives constraint
- [ ] CSP logic correct
- [ ] Solver finds valid solution
- [ ] Geometry mathematically verified
- [ ] IFC export successful
