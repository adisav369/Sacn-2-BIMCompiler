# Test Organization

## Canonical Tests (tests/canonical/)

**Regression anchors** - These are the gold standard tests that prove the compiler works correctly:

- `TBLKTNEndToEndTest.java` - Single-storey Malaysian residential house
- `SchoolEndToEndTest.java` - 2-storey institutional building (MASONRY construction)
- `TBLKTN2SEndToEndTest.java` - 2-storey residential house

Each test:
- Embeds the exact DSL input (version controlled)
- Validates expected witness counts (regression detection)
- Writes to federated DB schema (Bonsai-compatible)

**Usage:**
```bash
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.SchoolEndToEndTest" -q
```

## Development Tests (tests/archive/development/)

Historical development and exploration tests. Archived for reference but not part of the regression suite.

## Generic CLI

For compiling any DSL file dynamically:

```bash
mvn exec:java -Dexec.mainClass="com.bim.compiler.cli.BuildingCompilerCLI" \
  -Dexec.args="examples/MyBuilding.bim output/mybuilding.db"
```

See `src/main/java/com/bim/compiler/cli/BuildingCompilerCLI.java`
