# 2D-to-BIM COMPILER — MASTER CONTROL DOCUMENT

## PRIME RULE

**EXTRACT, DON'T IMAGINE.**

---

## REFERENCE DOCUMENTS

- **Design Rationale:** `docs/ARCHIVE_intent_compiler_method.md` (READ-ONLY)
- **Original Discussion:** https://gitlab.com/red1org/hongkong/-/issues/69
- **Federated Model DB:** Source of truth for extraction

This rule governs ALL phases. Place it in `~/bim-compiler/claude.md` as the only file Claude Code auto-reads.

---

## PIPELINE OVERVIEW

```
BOOTSTRAP (this session)
    │
    ▼
PHASE 0: Model Archaeology — Extract patterns from federated DB
    │
    ▼ [HUMAN CHECKPOINT]
PHASE 1: Topology Dictionary — Java interfaces from extracted types
    │
    ▼ [HUMAN CHECKPOINT]
PHASE 2: Immutable Value Objects — Geometry types (Point3D, etc.)
    │
    ▼ [HUMAN CHECKPOINT]
PHASE 3: Validation Contracts — Deterministic validators
    │
    ▼ [HUMAN CHECKPOINT]
PHASE 4: Builders — One object type at a time, per DAG order
    │
    ▼ [HUMAN CHECKPOINT per builder]
COMPLETE
```

**No phase proceeds without human checkpoint approval.**

---

# BOOTSTRAP

## B1. Clone Repository

GitHub account: `red1oon`

**ASK USER:**
- Which repository contains Federation modules?
- Which branch?

```bash
cd ~
git clone [user-confirmed-url]
cd [repo-name] && git checkout [user-confirmed-branch]
```

**CHECKPOINT B1: Confirm repo cloned and branch checked out.**

---

## B2. Locate Source Data

User copies from another machine:

**ASK USER:**
- Path to federated SQLite DB?
- Path to merged IFC file?
- Path to working bake/load scripts? (read-only reference)

Verify:
```bash
sqlite3 "[DB_PATH]" "SELECT COUNT(*) FROM elements;"  # expect ~49000
ls -la "[IFC_PATH]"
ls "[BAKE_SCRIPTS_PATH]"
```

**CHECKPOINT B2: All three paths verified.**

---

## B3. Create Project Structure

```bash
mkdir -p ~/bim-compiler/{docs,sessions}
mkdir -p ~/bim-compiler/src/main/java/com/bim/compiler/{topology,model,validation,geometry}
mkdir -p ~/bim-compiler/src/test/java/com/bim/compiler/topology
cd ~/bim-compiler
git init
```

---

## B4. Create claude.md

```bash
cat > ~/bim-compiler/claude.md << 'EOF'
# PRIME RULE

**EXTRACT, DON'T IMAGINE.**

Query the federated model DB. Copy patterns you find. Never invent.

---

**When lost:** `cat SESSION_STATE.md` then query DB.

**After token refresh:** Read SESSION_STATE.md → state phase → continue.
EOF
```

---

## B5. Create SESSION_STATE.md

```bash
cat > ~/bim-compiler/SESSION_STATE.md << 'EOF'
# SESSION STATE

## Current Phase
BOOTSTRAP

## Environment
- Repo: [path]
- Branch: [branch]  
- DB: [path]
- IFC: [path]
- Bake scripts: [path]

## Phase Progress
- [ ] BOOTSTRAP complete
- [ ] PHASE 0 complete
- [ ] PHASE 1 complete
- [ ] PHASE 2 complete
- [ ] PHASE 3 complete
- [ ] PHASE 4 in progress: [which builder]

## Last Action
[timestamp] — [action]

## Next Action
[specific next step]

## Extracted Data (Phase 0)
### Object Types
[pending]

### Relationships  
[pending]

## Blocking Issues
[none]

---
UPDATED: [timestamp]
EOF
```

**Update this file after EVERY significant action.**

---

## B6. Create CONFIG.md

```bash
cat > ~/bim-compiler/CONFIG.md << 'EOF'
# PATHS

REPO="[from B1]"
BRANCH="[from B1]"
FEDERATED_DB="[from B2]"
MERGED_IFC="[from B2]"
BAKE_SCRIPTS="[from B2]"

# Quick verification
sqlite3 "$FEDERATED_DB" "SELECT ifc_class, COUNT(*) FROM elements GROUP BY 1 ORDER BY 2 DESC LIMIT 10;"
EOF
```

**CHECKPOINT B6: BOOTSTRAP complete. Proceed to PHASE 0.**

---

# PHASE 0: MODEL ARCHAEOLOGY

## Objective
Extract actual types, properties, and relationships from federated DB. These become the Java interfaces — no invention.

---

## 0.1 Extract Object Types

```sql
SELECT 
    ifc_class,
    COUNT(*) as count
FROM elements
GROUP BY ifc_class
ORDER BY count DESC;
```

**Record in SESSION_STATE.md under "Object Types".**

This list becomes `BIMObjectType` enum — exactly these types, no more.

---

## 0.2 Extract Schema

```sql
.schema elements
.schema [other relevant tables]
```

**Record column names — these become interface getters.**

---

## 0.3 Extract Relationships

```sql
-- Adjust based on actual schema
SELECT 
    e1.ifc_class as child_type,
    e2.ifc_class as parent_type,
    COUNT(*) as occurrences
FROM elements e1
JOIN elements e2 ON e1.parent_id = e2.id
GROUP BY child_type, parent_type
ORDER BY occurrences DESC;
```

**Record in SESSION_STATE.md under "Relationships".**

This becomes `TopologyRules` — exactly these relationships, no assumptions.

---

## 0.4 Sample Each Type

For top 10 types by count:
```sql
SELECT * FROM elements WHERE ifc_class = '[TYPE]' LIMIT 2;
```

**Note which columns are populated vs NULL for each type.**

---

## 0.5 Study Bake Scripts (Read-Only)

```bash
grep -n "IfcWall\|IfcSlab\|IfcColumn" [BAKE_SCRIPTS]/*.py | head -50
```

**Note how existing code accesses each type — copy this pattern.**

---

## Phase 0 Completion Checklist

- [ ] All object types enumerated from DB (0.1)
- [ ] Schema documented (0.2)
- [ ] Relationships extracted from DB (0.3)
- [ ] Top types sampled (0.4)
- [ ] Bake script patterns noted (0.5)
- [ ] SESSION_STATE.md updated with all findings

**HUMAN CHECKPOINT: Review extracted data before Phase 1.**

---

# PHASE 1: TOPOLOGY DICTIONARY (Java)

## Objective
Translate Phase 0 findings into Java interfaces.

---

## 1.1 Create pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.bim</groupId>
    <artifactId>bim-compiler</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    
    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

---

## 1.2 Create BIMObjectType Enum

**From Phase 0.1 results only:**

```java
// src/main/java/com/bim/compiler/topology/BIMObjectType.java
public enum BIMObjectType {
    // ONLY types found in DB query 0.1
    // Example (replace with actual):
    IFC_WALL,
    IFC_SLAB,
    IFC_COLUMN,
    // ... etc from your extraction
}
```

---

## 1.3 Create DependencyRule

**From Phase 0.3 results only:**

```java
// src/main/java/com/bim/compiler/topology/DependencyRule.java
public record DependencyRule(
    BIMObjectType dependent,
    BIMObjectType dependency,
    DependencyType type,
    String description
) {}

public enum DependencyType {
    MUST_EXIST_FIRST,
    MUST_HOST,
    MUST_BE_CONTAINED_BY,
    MUST_CONNECT_TO
}
```

---

## 1.4 Create TopologyRules

**From Phase 0.3 results only:**

```java
// src/main/java/com/bim/compiler/topology/TopologyRules.java
public class TopologyRules {
    public static final List<DependencyRule> RULES = List.of(
        // ONLY relationships found in DB query 0.3
        // new DependencyRule(CHILD, PARENT, TYPE, "description"),
    );
}
```

---

## 1.5 Create Interfaces Per Type

**From Phase 0.2 and 0.4 — actual columns become getters:**

```java
// src/main/java/com/bim/compiler/model/IWall.java
/**
 * Contract for Wall objects.
 * 
 * DEPENDENCIES: (from Phase 0.3)
 * PROPERTIES: (from Phase 0.4 — actual DB columns)
 */
public interface IWall extends IBIMObject {
    // Getters matching actual DB columns
    // NOT imagined properties
}
```

---

## 1.6 Verify Compilation

```bash
mvn compile
```

Must pass before checkpoint.

---

## Phase 1 Completion Checklist

- [ ] BIMObjectType matches DB extraction exactly
- [ ] TopologyRules matches DB relationships exactly
- [ ] Every type has interface with actual DB columns as getters
- [ ] `mvn compile` passes
- [ ] SESSION_STATE.md updated

**HUMAN CHECKPOINT: Verify interfaces match DB reality.**

---

# PHASE 2: IMMUTABLE VALUE OBJECTS

## Objective
Geometry types that bake scripts use.

---

## 2.1 Study Bake Script Geometry

```bash
grep -n "Point\|Vector\|BoundingBox\|coordinates" [BAKE_SCRIPTS]/*.py
```

**Extract exactly what geometry types exist.**

---

## 2.2 Create Matching Java Types

```java
// Only types found in bake scripts
public record Point3D(double x, double y, double z) {}
public record BoundingBox(Point3D min, Point3D max) {}
// etc.
```

**These are immutable — no setters.**

---

## Phase 2 Completion Checklist

- [ ] Geometry types match bake script usage
- [ ] All immutable (records)
- [ ] `mvn compile` passes

**HUMAN CHECKPOINT**

---

# PHASE 3: VALIDATION CONTRACTS

## Objective
Deterministic validators — no AI, no fuzzy matching.

---

## 3.1 Create Validators Per Rule

For each DependencyRule in TopologyRules:

```java
public class WallValidator implements Validator<IWall> {
    @Override
    public List<ValidationError> validate(IWall wall, BIMModel model) {
        List<ValidationError> errors = new ArrayList<>();
        
        // From TopologyRules — wall must have floor
        if (wall.getHostFloor() == null) {
            errors.add(new ValidationError("Wall has no host floor"));
        }
        
        // Geometry checks — deterministic
        if (wall.getBounds().min().z() < wall.getHostFloor().getSurfaceZ()) {
            errors.add(new ValidationError("Wall below floor surface"));
        }
        
        return errors;
    }
}
```

---

## 3.2 Create Validator Tests

```java
@Test
void wallWithoutFloorFails() {
    IWall wall = new TestWall(null, ...);
    List<ValidationError> errors = validator.validate(wall, model);
    assertThat(errors).hasSize(1);
}
```

---

## Phase 3 Completion Checklist

- [ ] Every DependencyRule has validator
- [ ] Every validator has test
- [ ] `mvn test` passes
- [ ] NO AI or fuzzy matching anywhere

**HUMAN CHECKPOINT**

---

# PHASE 4: BUILDERS

## Objective
One builder at a time, in DAG order.

---

## Order (from TopologyRules)

Build in dependency order — roots first:
1. Foundation (no dependencies)
2. Slab (depends on Foundation)
3. Floor (depends on Slab)
4. Wall (depends on Floor)
5. ... continue per your DAG

---

## Per Builder

1. Create builder class
2. Create builder test
3. Run validation
4. **HUMAN CHECKPOINT before next builder**

```java
public class WallBuilder {
    public IWall build(WallSpec spec, IFloor hostFloor) {
        // hostFloor is required parameter — can't forget it
        // Validation runs automatically
    }
}
```

---

## Phase 4 Completion Checklist

- [ ] Builder for [Type] complete
- [ ] Tests pass
- [ ] Validation passes
- [ ] **HUMAN CHECKPOINT**
- [ ] Proceed to next builder

---

# SESSION RECOVERY

When token refreshes:

```
Read ~/bim-compiler/SESSION_STATE.md
Read ~/bim-compiler/claude.md

State:
1. Current phase
2. Last completed action  
3. Next action

Continue from there.
```

---

# FORBIDDEN (ALL PHASES)

❌ Invent types not in DB
❌ Invent relationships not in DB
❌ Invent properties not in DB columns
❌ Use AI for validation
❌ Skip human checkpoints
❌ "Improve" on existing bake script patterns
❌ Add "flexibility" or "future-proofing"

---

# WHEN LOST (ALL PHASES)

```bash
cat ~/bim-compiler/SESSION_STATE.md
sqlite3 [DB] "SELECT ifc_class, COUNT(*) FROM elements GROUP BY 1 LIMIT 10;"
```

Describe what you see. Copy patterns. **EXTRACT, DON'T IMAGINE.**
