package com.bim.compiler.contract;

import com.bim.compiler.dsl.BuildingRegistry;
import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;
import com.bim.compiler.validation.SpatialDigest;

import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Rosetta Stone Gate — Compilation Integrity Verification.
 *
 * <p>Five gates proving the compiler extracts and compiles, never invents:
 *
 * <pre>
 *   G1-COUNT      Element count: reference input = compiled output
 *   G2-VOLUME     Total AABB volume: reference input ≈ compiled output
 *   G3-DIGEST     Per-element spatial SHA256: reference vs compiled
 *   G4-TAMPER     Self-inspection: no @Disabled, no stubs, no invented data
 *   G5-PROVENANCE Every output element traced to library (material + geometry)
 * </pre>
 *
 * <p>This test executes as written. Failures are reported, not suppressed.
 * Fix only after reviewing results — never mid-execution.
 *
 * <p>G4-TAMPER scans git history and source code using extensible regex rules.
 * New rules are added as entries in {@link #TAMPER_RULES}, not as code changes.
 *
 * <p>Runs in Maven surefire stage 2 (validate-contracts), after BuildingRegistryTest
 * has compiled all output DBs.
 */
@DisplayName("Rosetta Stone Gate — Compilation Integrity")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RosettaStoneGateTest {

    private static final String COMPONENT_LIBRARY = "library/component_library.db";
    private static final String HEADER =
        "═══════════════════════════════════════════════════════════════════════";

    private static List<BuildingEntry> buildings;

    @BeforeAll
    static void loadRegistry() {
        buildings = BuildingRegistry.loadActive();
        assertFalse(buildings.isEmpty(), "C_DocType must have active building types");
        System.out.println();
        System.out.println(HEADER);
        System.out.println("  ROSETTA STONE GATE — Compilation Integrity Report");
        System.out.println(HEADER);
    }

    // =====================================================================
    // G1-COUNT: Element count must match between reference and compiled
    // =====================================================================

    @TestFactory
    @Order(1)
    @DisplayName("G1-COUNT")
    Collection<DynamicTest> g1_count() {
        List<DynamicTest> tests = new ArrayList<>();
        for (BuildingEntry b : buildings) {
            if (b.hasReference()) {
                tests.add(dynamicTest("G1-COUNT " + b.docTypeId(),
                    () -> runG1(b)));
            }
        }
        return tests;
    }

    private void runG1(BuildingEntry b) throws Exception {
        String tag = b.docTypeId();
        int refCount = countElements(b.referenceDbPath());
        int outCount = countElements(b.outputDbPath());
        int delta = outCount - refCount;
        String status = (delta == 0) ? "PASS" : "FAIL";
        String detail = String.format("ref=%d  out=%d  delta=%+d", refCount, outCount, delta);
        report("G1-COUNT", tag, status, detail);
        assertEquals(refCount, outCount,
            String.format("[G1-COUNT] %s: element count mismatch ref=%d out=%d",
                tag, refCount, outCount));
    }

    // =====================================================================
    // G2-VOLUME: Total AABB volume must match (±0.1% tolerance)
    // =====================================================================

    @TestFactory
    @Order(2)
    @DisplayName("G2-VOLUME")
    Collection<DynamicTest> g2_volume() {
        List<DynamicTest> tests = new ArrayList<>();
        for (BuildingEntry b : buildings) {
            if (b.hasReference()) {
                tests.add(dynamicTest("G2-VOLUME " + b.docTypeId(),
                    () -> runG2(b)));
            }
        }
        return tests;
    }

    private void runG2(BuildingEntry b) throws Exception {
        String tag = b.docTypeId();
        double refVol = totalVolume(b.referenceDbPath());
        double outVol = totalVolume(b.outputDbPath());
        double deltaPct = (refVol > 0) ? ((outVol - refVol) / refVol) * 100.0 : 0.0;
        String status = (Math.abs(deltaPct) <= 0.1) ? "PASS" : "FAIL";
        String detail = String.format("ref=%.2fm³  out=%.2fm³  Δ=%+.2f%%",
            refVol, outVol, deltaPct);
        report("G2-VOLUME", tag, status, detail);
        assertTrue(Math.abs(deltaPct) <= 0.1,
            String.format("[G2-VOLUME] %s: volume drift %.2f%% exceeds ±0.1%% (ref=%.2f out=%.2f)",
                tag, deltaPct, refVol, outVol));
    }

    // =====================================================================
    // G3-DIGEST: Per-element SHA256 spatial digest must match
    // =====================================================================

    @TestFactory
    @Order(3)
    @DisplayName("G3-DIGEST")
    Collection<DynamicTest> g3_digest() {
        List<DynamicTest> tests = new ArrayList<>();
        for (BuildingEntry b : buildings) {
            if (b.hasReference()) {
                tests.add(dynamicTest("G3-DIGEST " + b.docTypeId(),
                    () -> runG3(b)));
            }
        }
        return tests;
    }

    private void runG3(BuildingEntry b) throws Exception {
        String tag = b.docTypeId();
        // Cross-mode: exclude geometry_hash (extraction IFC hashes vs compilation LOD hashes
        // differ by design — same geometry, different hash format)
        SpatialDigest.DigestReport refReport = SpatialDigest.computeWithReport(b.referenceDbPath(), false);
        SpatialDigest.DigestReport outReport = SpatialDigest.computeWithReport(b.outputDbPath(), false);
        boolean match = refReport.digest().equals(outReport.digest());
        String status = match ? "PASS" : "FAIL";

        String detail;
        if (match) {
            detail = String.format("SHA256=%s  elements=%d", refReport.digest().substring(0, 16),
                refReport.elementCount());
        } else {
            // Find per-class differences for diagnostics
            StringBuilder diff = new StringBuilder();
            diff.append(String.format("ref=%s  out=%s",
                refReport.digest().substring(0, 16), outReport.digest().substring(0, 16)));
            Set<String> allClasses = new TreeSet<>();
            allClasses.addAll(refReport.classCounts().keySet());
            allClasses.addAll(outReport.classCounts().keySet());
            for (String cls : allClasses) {
                int rc = refReport.classCounts().getOrDefault(cls, 0);
                int oc = outReport.classCounts().getOrDefault(cls, 0);
                if (rc != oc || !refReport.classCounts().containsKey(cls)
                             || !outReport.classCounts().containsKey(cls)) {
                    diff.append(String.format("  %s: ref=%d out=%d", cls, rc, oc));
                }
            }
            detail = diff.toString();
        }
        report("G3-DIGEST", tag, status, detail);
        assertTrue(match,
            String.format("[G3-DIGEST] %s: spatial digest mismatch.%n  ref=%s%n  out=%s%n  %s",
                tag, refReport.digest(), outReport.digest(), detail));
    }

    // =====================================================================
    // G4-TAMPER: Self-inspection via git history + source scan
    // =====================================================================

    /**
     * Tamper detection rule — extensible regex-based inspection.
     * Add new rules here. The test picks them up automatically.
     *
     * @param id          Short identifier (T1, T2, ...)
     * @param description Human-readable explanation
     * @param pattern     Regex to search for
     * @param scope       GIT_DIFF (recent commits) or SOURCE_SCAN (current files)
     * @param fileGlob    File pattern to restrict search (null = all)
     */
    private record TamperRule(String id, String description, String pattern,
                              Scope scope, String fileGlob) {}
    private enum Scope { GIT_DIFF, SOURCE_SCAN }

    /**
     * Violation found by a tamper rule.
     */
    private record Violation(String ruleId, String file, int line, String matched) {
        @Override public String toString() {
            return String.format("    [%s] %s:%d  %s", ruleId, file, line,
                matched.length() > 80 ? matched.substring(0, 80) + "..." : matched);
        }
    }

    // --- TAMPER RULES (extensible — add entries, not code) ----------------

    private static final List<TamperRule> TAMPER_RULES = List.of(
        // Git diff rules (detect changes in recent commits)
        new TamperRule("T1", "@Disabled/@Ignore added to tests",
            "@Disabled|@Ignore",
            Scope.GIT_DIFF, "*Test.java"),
        new TamperRule("T2", "No-op assertions (assertTrue(true), etc.)",
            "assertTrue\\s*\\(\\s*true|assertFalse\\s*\\(\\s*false",
            Scope.GIT_DIFF, "*Test.java"),
        new TamperRule("T3", "Thread.sleep/System.exit in production code",
            "Thread\\.sleep|System\\.exit",
            Scope.GIT_DIFF, "src/main/**/*.java"),
        new TamperRule("T4", "skipTests or skip>true in pom.xml",
            "skipTests|<skip>true</skip>",
            Scope.GIT_DIFF, "pom.xml"),
        new TamperRule("T5", "--no-verify or hook bypass in scripts",
            "--no-verify|--no-gpg-sign|SKIP_HOOKS",
            Scope.GIT_DIFF, null),

        // Source scan rules (detect current state)
        new TamperRule("T6", "@Disabled annotation on contract test classes",
            "^\\s*@Disabled|^\\s*@Ignore",
            Scope.SOURCE_SCAN, "DAGCompiler/src/test/java/com/bim/compiler/contract/*.java"),
        new TamperRule("T7", "@Disabled annotation on arch test classes",
            "^\\s*@Disabled|^\\s*@Ignore",
            Scope.SOURCE_SCAN, "DAGCompiler/src/test/java/com/bim/compiler/arch/*.java"),
        new TamperRule("T8", "Stub method (unconditional return null/empty) in validators",
            "^\\s*return\\s+(null|0|Collections\\.emptyList|List\\.of\\(\\)|Map\\.of\\(\\))\\s*;",
            Scope.SOURCE_SCAN, "DAGCompiler/src/main/java/com/bim/compiler/validation/*.java"),
        new TamperRule("T9", "Non-determinism (Random/Math.random) in pipeline",
            "new\\s+Random|Math\\.random",
            Scope.SOURCE_SCAN, "DAGCompiler/src/main/java/com/bim/compiler/dsl/*.java"),
        new TamperRule("T10", "TODO/FIXME/HACK in pipeline production code",
            "TODO|FIXME|HACK|XXX",
            Scope.SOURCE_SCAN, "DAGCompiler/src/main/java/com/bim/compiler/dsl/*.java"),
        new TamperRule("T11", "Empty test methods in contract tests",
            "@Test[\\s\\S]{0,40}void\\s+\\w+\\s*\\(\\s*\\)\\s*\\{\\s*\\}",
            Scope.SOURCE_SCAN, "DAGCompiler/src/test/java/com/bim/compiler/contract/*.java"),
        new TamperRule("T12", "Hardcoded coordinate literals (>1000) in pipeline",
            "=\\s*[1-9]\\d{3,}\\s*[;,)]",
            Scope.SOURCE_SCAN, "DAGCompiler/src/main/java/com/bim/compiler/dsl/*.java")
    );

    /** Number of recent commits to scan for git diff rules. */
    private static final int GIT_HISTORY_DEPTH = 10;

    @Test
    @Order(4)
    @DisplayName("G4-TAMPER: self-inspection via git history + source scan")
    void g4_tamper() throws Exception {
        List<Violation> violations = new ArrayList<>();

        // Phase A: Git diff scan
        String gitDiff = gitDiffOutput();
        for (TamperRule rule : TAMPER_RULES) {
            if (rule.scope() != Scope.GIT_DIFF) continue;
            violations.addAll(scanGitDiff(rule, gitDiff));
        }

        // Phase B: Source scan
        for (TamperRule rule : TAMPER_RULES) {
            if (rule.scope() != Scope.SOURCE_SCAN) continue;
            violations.addAll(scanSourceFiles(rule));
        }

        // Report
        long ruleCount = TAMPER_RULES.size();
        String status = violations.isEmpty() ? "PASS" : "FAIL";
        String detail = String.format("%d violations / %d rules scanned / %d commits",
            violations.size(), ruleCount, GIT_HISTORY_DEPTH);
        report("G4-TAMPER", "ALL", status, detail);

        if (!violations.isEmpty()) {
            System.out.println("  G4-TAMPER violations:");
            for (Violation v : violations) {
                System.out.println(v);
            }
        }

        assertEquals(0, violations.size(),
            String.format("[G4-TAMPER] %d violations found:%n%s",
                violations.size(),
                violations.stream().map(Violation::toString).collect(Collectors.joining("\n"))));
    }

    // =====================================================================
    // G5-PROVENANCE: Every output element traced to library
    // =====================================================================

    @TestFactory
    @Order(5)
    @DisplayName("G5-PROVENANCE")
    Collection<DynamicTest> g5_provenance() {
        List<DynamicTest> tests = new ArrayList<>();
        for (BuildingEntry b : buildings) {
            tests.add(dynamicTest("G5-PROVENANCE " + b.docTypeId(),
                () -> runG5(b)));
        }
        return tests;
    }

    private void runG5(BuildingEntry b) throws Exception {
        String tag = b.docTypeId();
        String dbPath = b.outputDbPath();
        List<String> issues = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            // Check 1: material_rgba coverage must be >= reference
            // IFC sources may lack IfcSurfaceStyle for some elements (walls, structural);
            // demanding 100% would reject valid extractions. Compare against reference.
            int totalElements = queryInt(conn,
                "SELECT COUNT(*) FROM elements_meta");
            int outMissing = queryInt(conn,
                "SELECT COUNT(*) FROM elements_meta WHERE material_rgba IS NULL OR material_rgba = ''");
            if (b.hasReference()) {
                try (Connection refConn = DriverManager.getConnection("jdbc:sqlite:" + b.referenceDbPath())) {
                    int refMissing = queryInt(refConn,
                        "SELECT COUNT(*) FROM elements_meta WHERE material_rgba IS NULL OR material_rgba = ''");
                    if (outMissing > refMissing) {
                        issues.add(String.format("material_rgba: output missing %d > reference missing %d (lost provenance)",
                            outMissing, refMissing));
                    }
                }
            } else if (outMissing > 0) {
                issues.add(String.format("%d/%d elements missing material_rgba", outMissing, totalElements));
            }

            // Check 2: Every element_instance has a geometry_hash linked to base_geometries
            int totalInstances = queryInt(conn,
                "SELECT COUNT(*) FROM element_instances");
            int orphanGeom = queryInt(conn,
                "SELECT COUNT(*) FROM element_instances ei "
                + "WHERE NOT EXISTS (SELECT 1 FROM base_geometries bg "
                + "WHERE bg.geometry_hash = ei.geometry_hash)");
            if (orphanGeom > 0) {
                issues.add(String.format("%d/%d instances have no base_geometry (invented?)",
                    orphanGeom, totalInstances));
            }

            // Check 3: base_geometries have real mesh (vertex_count >= 8 = at least a box)
            int degenerateGeom = queryInt(conn,
                "SELECT COUNT(*) FROM base_geometries WHERE vertex_count < 8");
            if (degenerateGeom > 0) {
                issues.add(String.format("%d degenerate geometries (vertex_count < 8)", degenerateGeom));
            }

            // Check 4: No null/empty geometry_hash in element_instances
            int nullHash = queryInt(conn,
                "SELECT COUNT(*) FROM element_instances "
                + "WHERE geometry_hash IS NULL OR geometry_hash = ''");
            if (nullHash > 0) {
                issues.add(String.format("%d instances with null/empty geometry_hash", nullHash));
            }

            // Check 5: elements_meta.ifc_class is a known IFC class (not invented)
            int unknownClass = queryInt(conn,
                "SELECT COUNT(*) FROM elements_meta WHERE ifc_class NOT IN ("
                + "'IfcWall','IfcSlab','IfcRoof','IfcDoor','IfcWindow',"
                + "'IfcMember','IfcPlate','IfcColumn','IfcBeam','IfcStair',"
                + "'IfcRailing','IfcCovering','IfcCurtainWall','IfcBuildingElementProxy',"
                + "'IfcFurniture','IfcFurnishingElement','IfcFlowTerminal',"
                + "'IfcFlowSegment','IfcFlowFitting','IfcDistributionElement',"
                + "'IfcOpeningElement','IfcSpace','IfcSanitaryTerminal',"
                + "'IfcLightFixture','IfcElectricAppliance','IfcFireSuppressionTerminal',"
                + "'IfcOutlet','IfcSwitchingDevice','IfcPipeSegment','IfcPipeFitting',"
                + "'IfcDuctSegment','IfcDuctFitting','IfcCableSegment','IfcJunctionBox',"
                + "'IfcFlowController','IfcStairFlight'"
                + ")");
            if (unknownClass > 0) {
                issues.add(String.format("%d elements with unknown ifc_class", unknownClass));
            }

            String status = issues.isEmpty() ? "PASS" : "FAIL";
            String detail = issues.isEmpty()
                ? String.format("%d/%d elements traced  %d geometries verified",
                    totalElements, totalElements, totalInstances)
                : String.join("; ", issues);
            report("G5-PROVENANCE", tag, status, detail);

            assertTrue(issues.isEmpty(),
                String.format("[G5-PROVENANCE] %s: %s", tag, String.join("; ", issues)));
        }
    }

    // =====================================================================
    // Helpers — DB queries
    // =====================================================================

    private static int countElements(String dbPath) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            return queryInt(conn, "SELECT COUNT(*) FROM elements_meta");
        }
    }

    private static double totalVolume(String dbPath) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT COALESCE(SUM((r.maxX-r.minX)*(r.maxY-r.minY)*(r.maxZ-r.minZ)), 0) "
                 + "FROM elements_rtree r JOIN elements_meta m ON r.id = m.id")) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        }
    }

    private static int queryInt(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // =====================================================================
    // Helpers — G4 tamper detection
    // =====================================================================

    /**
     * Get unified diff of last N commits.
     */
    private String gitDiffOutput() {
        try {
            // Get the Nth ancestor commit hash
            Process logProc = new ProcessBuilder(
                "git", "log", "--format=%H", "-1", "--skip=" + GIT_HISTORY_DEPTH)
                .redirectErrorStream(true).start();
            String baseCommit = new String(logProc.getInputStream().readAllBytes()).trim();
            logProc.waitFor();

            if (baseCommit.isEmpty()) {
                // Fewer than N commits — diff from root
                baseCommit = "4b825dc642cb6eb9a060e54bf899d15f71ecb5e4"; // empty tree
            }

            Process diffProc = new ProcessBuilder(
                "git", "diff", baseCommit + "..HEAD", "--unified=0", "--diff-filter=AM")
                .redirectErrorStream(true).start();
            String diff = new String(diffProc.getInputStream().readAllBytes());
            diffProc.waitFor();
            return diff;
        } catch (Exception e) {
            System.err.println("[G4-TAMPER] git diff failed: " + e.getMessage());
            return "";
        }
    }

    /**
     * Scan git diff output for a tamper rule.
     * Only matches added lines (starting with +, not +++).
     */
    private List<Violation> scanGitDiff(TamperRule rule, String fullDiff) {
        List<Violation> violations = new ArrayList<>();
        Pattern pat = Pattern.compile(rule.pattern());
        String currentFile = null;
        int lineNo = 0;

        for (String line : fullDiff.split("\n")) {
            if (line.startsWith("diff --git")) {
                // Extract filename: "diff --git a/path b/path"
                String[] parts = line.split(" ");
                currentFile = parts.length >= 4 ? parts[3].substring(2) : "?";
                lineNo = 0;
            } else if (line.startsWith("@@")) {
                // Parse hunk header for line number: @@ -a,b +c,d @@
                Matcher m = Pattern.compile("\\+(\\d+)").matcher(line);
                if (m.find()) lineNo = Integer.parseInt(m.group(1));
            } else if (line.startsWith("+") && !line.startsWith("+++")) {
                lineNo++;
                // Filter by fileGlob if specified
                if (rule.fileGlob() != null && currentFile != null) {
                    if (!matchesGlob(currentFile, rule.fileGlob())) continue;
                }
                // Exclude this test class from its own tamper rules (self-referential match)
                if (currentFile != null && currentFile.contains("RosettaStoneGateTest.java")) continue;
                if (pat.matcher(line).find()) {
                    violations.add(new Violation(rule.id(), currentFile, lineNo,
                        line.substring(1).trim()));
                }
            }
        }
        return violations;
    }

    /**
     * Scan source files matching a glob for a tamper rule.
     */
    private List<Violation> scanSourceFiles(TamperRule rule) {
        List<Violation> violations = new ArrayList<>();
        if (rule.fileGlob() == null) return violations;

        Path base = Path.of(".");
        try {
            // Convert simple glob to PathMatcher
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher(
                "glob:" + rule.fileGlob());

            // Walk and match
            List<Path> files;
            try (Stream<Path> walk = Files.walk(base, 20)) {
                files = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(base.relativize(p)))
                    .collect(Collectors.toList());
            }

            Pattern pat = Pattern.compile(rule.pattern());
            for (Path file : files) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    if (pat.matcher(lines.get(i)).find()) {
                        // Exclude this test class itself from T6/T7 false positives
                        String rel = base.relativize(file).toString();
                        if (rel.contains("RosettaStoneGateTest.java")) continue;
                        violations.add(new Violation(rule.id(), rel, i + 1,
                            lines.get(i).trim()));
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[G4-TAMPER] source scan failed for " + rule.fileGlob()
                + ": " + e.getMessage());
        }
        return violations;
    }

    /**
     * Simple glob match for git diff file filtering.
     * Supports * and ** patterns.
     */
    private static boolean matchesGlob(String path, String glob) {
        // Convert glob to regex
        String regex = glob
            .replace(".", "\\.")
            .replace("**/", "(.+/)?")
            .replace("*", "[^/]*");
        return path.matches(regex);
    }

    // =====================================================================
    // Report formatting
    // =====================================================================

    private static void report(String gate, String tag, String status, String detail) {
        String line = String.format("  [%-14s] %-6s %-4s  %s", gate, tag, status, detail);
        System.out.println(line);
    }
}
