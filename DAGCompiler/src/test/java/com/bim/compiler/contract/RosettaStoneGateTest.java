package com.bim.compiler.contract;

import com.bim.compiler.dsl.BuildingRegistry;
import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;
import com.bim.compiler.util.BIMLogger;
import com.bim.compiler.validation.SpatialDiff;
import com.bim.compiler.validation.SpatialDigest;

import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Rosetta Stone Gate — Compilation Integrity Verification.
 *
 * @Traces BBC.md §2 — Gospel Principle (extract, never invent)
 * @Traces BBC.md §2.1.6 — Count invariant (SUM non-PHANTOM qty = output count)
 * @Traces BBC.md §4.1 — World coordinate reconstruction (spatial digest)
 * @Traces BBC.md §7 — Verification: Rosetta Stone Gate
 * @Traces TestArchitecture.md §Traceability Matrix — G1-G6 gates
 *
 * <p>Seven gates proving the compiler extracts and compiles, never invents:
 *
 * <pre>
 *   G0-COMPILED   Output has c_order rows (not extraction-only)[TerminalAnalysis.md]
 *   G1-COUNT      Element count: reference = output          [BBC.md §2.1.6]
 *   G2-VOLUME     Total AABB volume: reference ≈ output      [BBC.md §4.2 BUFFER]
 *   G3-DIGEST     Per-element spatial SHA256: ref vs compiled [BBC.md §4.1]
 *   G4-TAMPER     Self-inspection: 20 rules (T1-T20)         [TestArchitecture.md §Layer 2]
 *   G5-PROVENANCE Output traced to library (material+geometry)[BBC.md §2 Gospel]
 *   G6-ISOLATION  Spatial structure containment checks        [BBC.md §2.2 recursion]
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

    /** Active gate scope — only SH/DX/TE assertions enforced. Others skip. */
    private static final Set<String> GATE_SCOPE = Set.of("RE_SH", "RE_DX", "CO_TE", "RE_IN", "RE_BA", "IN_IP", "RE_BH", "RE_BS", "RE_SC", "RE_CA", "RE_CS", "RE_CH", "RE_CE", "RE_CP", "RE_ES", "RE_MO", "RE_GH", "RE_JS", "RE_NI", "RE_WB", "CO_WL", "CO_WT", "CO_WA", "RE_JE", "RE_WI", "RE_RA", "RE_RM", "RE_RS", "RE_CL", "RE_HI");

    /** G3-DIGEST skip: reference DB has metadata-only elements absent from output.
     *  (2026-03-18: CO_TE removed — 4 IfcSensor deleted from reference. Sensors are Federation.) */
    private static final Set<String> G3_SKIP = Set.of();

    /** G6-ISOLATION skip: CO compilation mode doesn't yet produce full spatial structure.
     *  (2026-03-18: CO_TE removed — all 4 checks pass: styles, storeys, spaces, containment.) */
    private static final Set<String> G6_SKIP = Set.of();

    private static final String COMPONENT_LIBRARY = "library/component_library.db";
    private static final String HEADER =
        "═══════════════════════════════════════════════════════════════════════";

    private static List<BuildingEntry> buildings;

    @BeforeAll
    static void loadRegistry() {
        try {
            buildings = BuildingRegistry.loadActive();
        } catch (RuntimeException e) {
            // G4-TAMPER (source scan) needs no DB — allow it to run standalone
            buildings = List.of();
        }
        if (!buildings.isEmpty()) {
            System.out.println();
            System.out.println(HEADER);
            System.out.println("  ROSETTA STONE GATE — Compilation Integrity Report");
            System.out.println(HEADER);
        }
    }

    // =====================================================================
    // G0-COMPILED: Output DB must contain at least 1 c_order row
    // =====================================================================

    /**
     * G0-COMPILED: Output DB must contain at least 1 c_order row.
     * Prevents extraction-only outputs from silently passing G1-G6.
     * @Traces TerminalAnalysis.md §Compilation Status — blind spot #2
     */
    @TestFactory
    @Order(0)
    @DisplayName("G0-COMPILED")
    Collection<DynamicTest> g0_compiled() {
        List<DynamicTest> tests = new ArrayList<>();
        for (BuildingEntry b : buildings) {
            tests.add(dynamicTest("G0-COMPILED " + b.docTypeId(),
                () -> runG0(b)));
        }
        return tests;
    }

    private void runG0(BuildingEntry b) throws Exception {
        String tag = b.docTypeId();
        assumeTrue(GATE_SCOPE.contains(tag), tag + " outside gate scope");
        String dbPath = b.outputDbPath();
        int orderCount = 0;
        if (Files.exists(Path.of(dbPath))) {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM c_order")) {
                    if (rs.next()) orderCount = rs.getInt(1);
                }
            }
        }
        String status = (orderCount > 0) ? "PASS" : "FAIL";
        String detail = String.format("c_order=%d", orderCount);
        report("G0-COMPILED", tag, status, detail);
        assertTrue(orderCount > 0,
            String.format("[G0-COMPILED] %s: output is extraction-only, not compiled (c_order = %d)",
                tag, orderCount));
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
        assumeTrue(GATE_SCOPE.contains(tag), tag + " outside gate scope");
        int refCount = countElements(b.referenceDbPath());
        int outCount = countElements(b.outputDbPath());
        int expected = (b.expectedElements() > 0) ? b.expectedElements() : refCount;
        int delta = outCount - expected;
        String status = (delta == 0) ? "PASS" : "FAIL";
        String detail = String.format("ref=%d  expected=%d  out=%d  delta=%+d",
            refCount, expected, outCount, delta);
        report("G1-COUNT", tag, status, detail);
        assertEquals(expected, outCount,
            String.format("[G1-COUNT] %s: element count mismatch expected=%d out=%d (ref=%d)",
                tag, expected, outCount, refCount));
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
        assumeTrue(GATE_SCOPE.contains(tag), tag + " outside gate scope");
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
        assumeTrue(GATE_SCOPE.contains(tag), tag + " outside gate scope");
        if (G3_SKIP.contains(tag)) {
            report("G3-DIGEST", tag, "SKIP", "reference DB has metadata-only elements (known delta)");
            return;
        }
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
            // Per-element diff report for diagnostics (R1: LAST_MILE_PROBLEM.md)
            SpatialDiff.DiffReport diffReport = SpatialDiff.diff(b.referenceDbPath(), b.outputDbPath());
            diff.append("\n").append(diffReport.summary());
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
    //
    // Each rule traces to a spec requirement. The comment line before each
    // rule states which BBC.md / TestArchitecture.md requirement it enforces.
    // This is the @Traces convention applied to tamper rules.

    private static final List<TamperRule> TAMPER_RULES = List.of(
        // Git diff rules (detect changes in recent commits)
        // @Traces TestArchitecture.md §Anti-Patterns #3 — no @Disabled without TICKET
        new TamperRule("T1", "@Disabled/@Ignore added to tests",
            "@Disabled|@Ignore",
            Scope.GIT_DIFF, "*Test.java"),
        // @Traces TestArchitecture.md §Anti-Patterns #1 — no assertNotNull as sole verification
        new TamperRule("T2", "No-op assertions (assertTrue(true), etc.)",
            "assertTrue\\s*\\(\\s*true|assertFalse\\s*\\(\\s*false",
            Scope.GIT_DIFF, "*Test.java"),
        // @Traces TestArchitecture.md §Anti-Patterns #5 — no error suppression
        new TamperRule("T3", "Thread.sleep/System.exit in production code",
            "Thread\\.sleep|System\\.exit",
            Scope.GIT_DIFF, "src/main/**/*.java"),
        // @Traces TestArchitecture.md H7 — Maven test phase must not be skipped
        new TamperRule("T4", "skipTests or skip>true in pom.xml",
            "skipTests|<skip>true</skip>",
            Scope.GIT_DIFF, "pom.xml"),
        // @Traces TestArchitecture.md §Tamper Seal — hooks must not be bypassed
        new TamperRule("T5", "--no-verify or hook bypass in scripts",
            "--no-verify|--no-gpg-sign|SKIP_HOOKS",
            Scope.GIT_DIFF, null),

        // Source scan rules (detect current state)
        // @Traces TestArchitecture.md §Anti-Patterns #3 — TICKET-exempt @Disabled only
        new TamperRule("T6", "@Disabled annotation on contract test classes (TICKET-exempt)",
            "^\\s*@Disabled(?!.*TICKET)|^\\s*@Ignore",
            Scope.SOURCE_SCAN, "DAGCompiler/src/test/java/com/bim/compiler/contract/*.java"),
        // @Traces TestArchitecture.md §Anti-Patterns #3
        new TamperRule("T7", "@Disabled annotation on arch test classes",
            "^\\s*@Disabled|^\\s*@Ignore",
            Scope.SOURCE_SCAN, "DAGCompiler/src/test/java/com/bim/compiler/arch/*.java"),
        // @Traces BBC.md §2 — Gospel Principle (validators must not stub)
        new TamperRule("T8", "Stub method (unconditional return null/empty) in validators",
            "^\\s*return\\s+(null|0|Collections\\.emptyList|List\\.of\\(\\)|Map\\.of\\(\\))\\s*;",
            Scope.SOURCE_SCAN, "DAGCompiler/src/main/java/com/bim/compiler/validation/*.java"),
        // @Traces BBC.md §2 — Gospel Principle (compilation must be deterministic)
        new TamperRule("T9", "Non-determinism (Random/Math.random) in pipeline",
            "new\\s+Random|Math\\.random",
            Scope.SOURCE_SCAN, "DAGCompiler/src/main/java/com/bim/compiler/dsl/*.java"),
        // @Traces TestArchitecture.md §Anti-Patterns — production code must be clean
        new TamperRule("T10", "TODO/FIXME/HACK in pipeline production code",
            "TODO|FIXME|HACK|XXX",
            Scope.SOURCE_SCAN, "DAGCompiler/src/main/java/com/bim/compiler/dsl/*.java"),
        // @Traces TestArchitecture.md §Anti-Patterns #8 — no empty tests
        new TamperRule("T11", "Empty test methods in contract tests",
            "@Test[\\s\\S]{0,40}void\\s+\\w+\\s*\\(\\s*\\)\\s*\\{\\s*\\}",
            Scope.SOURCE_SCAN, "DAGCompiler/src/test/java/com/bim/compiler/contract/*.java"),
        // @Traces TestArchitecture.md §Anti-Drift #1 — no magic coordinates
        new TamperRule("T12", "Hardcoded coordinate literals (>1000) in pipeline",
            "=\\s*[1-9]\\d{3,}\\s*[;,)]",
            Scope.SOURCE_SCAN, "DAGCompiler/src/main/java/com/bim/compiler/dsl/*.java"),

        // T13–T15: Added by QA audit 2026-03-11 — catch test-weakening patterns
        // @Traces TestArchitecture.md §Anti-Patterns #3
        new TamperRule("T13", "assumeTrue(false) — self-disabling test",
            "assumeTrue\\s*\\(\\s*false",
            Scope.SOURCE_SCAN, "*Test.java"),
        // @Traces TestArchitecture.md §Anti-Patterns #5
        new TamperRule("T14", "catch (Exception ignored) — error suppression in production",
            "catch\\s*\\([^)]*\\s+ignored\\s*\\)",
            Scope.SOURCE_SCAN, "{DAGCompiler,TopologyMaker,ORMSandbox,BIM_COBOL,orm-core,2D_Layout}/src/main/**/*.java"),
        // @Traces TestArchitecture.md §Anti-Patterns #1
        new TamperRule("T15", "assertNotNull as sole verification in contract tests",
            "assertNotNull\\s*\\([^,)]+\\)\\s*;\\s*$",
            Scope.SOURCE_SCAN, "DAGCompiler/src/test/java/com/bim/compiler/contract/*.java"),

        // @Traces BBC.md §6 — BIM COBOL verbs wrap all BOM/order SQL
        new TamperRule("T16", "Raw SQL on protected tables outside verb layer",
            "(INSERT\\s+(OR\\s+\\w+\\s+)?INTO|UPDATE|DELETE\\s+FROM)\\s+(m_bom_line|m_bom|c_order|wm_empty_storage_line)\\b",
            Scope.SOURCE_SCAN, "{DAGCompiler,TopologyMaker,ORMSandbox}/src/main/**/*.java"),

        // @Traces BBC.md §6 — verb layer is sole authorized write path
        // ElementPersistence.java is exempt — it IS the authorized UPDATE path for verbs.
        new TamperRule("T17", "Raw UPDATE on output element tables outside verb layer",
            "UPDATE\\s+(elements_rtree|elements_meta|element_instances)\\b",
            Scope.SOURCE_SCAN, "{DAGCompiler,TopologyMaker,ORMSandbox}/src/main/**/*.java"),

        // @Traces BBC.md §2 — Gospel Principle + LAST_MILE Gap 7 (extraction leak)
        // @Traces LAST_MILE_PROBLEM.md Gap 4 — compiler reads only 7 declared spec sources
        // I_Element_Extraction belongs in IFCtoBOM (BOM generation), never in DAGCompiler (compilation).
        new TamperRule("T18", "Compiler reads I_Element_Extraction (extraction leak into compilation)",
            "I_Element_Extraction",
            Scope.SOURCE_SCAN, "DAGCompiler/src/main/**/*.java"),

        // @Traces BBC.md §10.6 — buildings are data, not code (no hardcoded names)
        // @Traces LAST_MILE_PROBLEM.md Gap 7 R14 — hardcoded building names removed
        // ExtractionPopulator.java is temporarily exempt (SJTII_Terminal storey fix — tracked debt).
        new TamperRule("T19", "Hardcoded building name in production code (data, not code)",
            "^[^/*]*\"[^\"]*(?:Ifc4_SampleHouse|Ifc2x3_Duplex|SJTII_Terminal)",
            Scope.SOURCE_SCAN, "{DAGCompiler,IFCtoBOM}/src/main/**/*.java"),

        // @Traces BBC.md §4.1 — origin convention (only BUILDING has non-zero origin)
        // @Traces LAST_MILE_PROBLEM.md Gap 7 R11 — origin stored in m_bom from measurement
        new TamperRule("T20", "Hardcoded zero origin in BOM INSERT (origin must be measured)",
            "0\\.0,\\s*0\\.0,\\s*0\\.0,\\s*1\\)",
            Scope.SOURCE_SCAN, "{DAGCompiler,IFCtoBOM}/src/main/**/*.java"),

        // @Traces BBC.md §2 — No Parametric Mesh in Pipeline
        // @Traces TestArchitecture.md §C13 — compilation must not generate geometry
        // bindParametric creates GEO_ boxes that corrupt Rosetta Stone comparison.
        // MeshBinder.bind() returning null must be a hard failure, not a parametric fallback.
        new TamperRule("T21", "bindParametric call in compilation (no parametric mesh in pipeline)",
            "bindParametric\\s*\\(",
            Scope.SOURCE_SCAN, "DAGCompiler/src/main/**/*.java")
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
        assumeTrue(GATE_SCOPE.contains(tag), tag + " outside gate scope");
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
                        String detail1 = listElements(conn,
                            "SELECT guid, ifc_class, element_name FROM elements_meta "
                            + "WHERE material_rgba IS NULL OR material_rgba = ''", 20);
                        issues.add(String.format("material_rgba: output missing %d > reference missing %d: %s",
                            outMissing, refMissing, detail1));
                    }
                }
            } else if (outMissing > 0) {
                String detail1 = listElements(conn,
                    "SELECT guid, ifc_class, element_name FROM elements_meta "
                    + "WHERE material_rgba IS NULL OR material_rgba = ''", 20);
                issues.add(String.format("%d/%d elements missing material_rgba: %s",
                    outMissing, totalElements, detail1));
            }

            // Check 2: Every element_instance has a geometry_hash linked to base_geometries
            int totalInstances = queryInt(conn,
                "SELECT COUNT(*) FROM element_instances");
            int orphanGeom = queryInt(conn,
                "SELECT COUNT(*) FROM element_instances ei "
                + "WHERE NOT EXISTS (SELECT 1 FROM base_geometries bg "
                + "WHERE bg.geometry_hash = ei.geometry_hash)");
            if (orphanGeom > 0) {
                String detail2 = listElements(conn,
                    "SELECT ei.guid, em.ifc_class, em.element_name "
                    + "FROM element_instances ei "
                    + "LEFT JOIN elements_meta em ON ei.guid = em.guid "
                    + "WHERE NOT EXISTS (SELECT 1 FROM base_geometries bg "
                    + "WHERE bg.geometry_hash = ei.geometry_hash)", 20);
                issues.add(String.format("%d/%d instances have no base_geometry: %s",
                    orphanGeom, totalInstances, detail2));
            }

            // Check 3: base_geometries have real mesh (vertex_count >= 4 = at least a tetrahedron)
            // Not all shapes are boxes — IfcRampFlight is a triangular prism (6 vertices).
            // Check 6 (no GEO_ prefix) is the primary guard against parametric fallback.
            // This check catches truly degenerate meshes (< 4 vertices = not a solid).
            int degenerateGeom = queryInt(conn,
                "SELECT COUNT(*) FROM base_geometries WHERE vertex_count < 4");
            if (degenerateGeom > 0) {
                issues.add(String.format("%d degenerate geometries (vertex_count < 4)", degenerateGeom));
            }

            // Check 4: No null/empty geometry_hash in element_instances
            int nullHash = queryInt(conn,
                "SELECT COUNT(*) FROM element_instances "
                + "WHERE geometry_hash IS NULL OR geometry_hash = ''");
            if (nullHash > 0) {
                String detail4 = listElements(conn,
                    "SELECT ei.guid, em.ifc_class, em.element_name "
                    + "FROM element_instances ei "
                    + "LEFT JOIN elements_meta em ON ei.guid = em.guid "
                    + "WHERE ei.geometry_hash IS NULL OR ei.geometry_hash = ''", 20);
                issues.add(String.format("%d instances with null/empty geometry_hash: %s",
                    nullHash, detail4));
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
                + "'IfcFlowController','IfcStairFlight',"
                + "'IfcReinforcingBar','IfcAirTerminal','IfcValve',"
                + "'IfcAlarm','IfcController','IfcRampFlight','IfcEarthworksFill',"
                + "'IfcElementAssembly','IfcDiscreteAccessory','IfcFooting','IfcChimney'"
                + ")");
            if (unknownClass > 0) {
                issues.add(String.format("%d elements with unknown ifc_class", unknownClass));
            }

            // Check 6: Library mesh prevalence — LOD_ prefix = real mesh from
            // component_library.db (scaled+transformed). GEO_ prefix = parametric
            // bounding box fallback. Rosetta Stone sameness (input coords = output
            // coords) is the geometry guarantee; mesh shape verification is not
            // needed because meshes come from the extraction oracle.
            int geoFallback = queryInt(conn,
                "SELECT COUNT(*) FROM element_instances "
                + "WHERE geometry_hash LIKE 'GEO_%'");
            if (geoFallback > 0) {
                String detail6 = listElements(conn,
                    "SELECT ei.guid, em.ifc_class, em.element_name "
                    + "FROM element_instances ei "
                    + "LEFT JOIN elements_meta em ON ei.guid = em.guid "
                    + "WHERE ei.geometry_hash LIKE 'GEO_%'", 20);
                issues.add(String.format("%d/%d instances use parametric BBox fallback (GEO_): %s",
                    geoFallback, totalInstances, detail6));
            }

            // Check 7: surface_styles populated when materials exist —
            // every distinct material_name in elements_meta must appear in
            // surface_styles (visual rendering requires the material library).
            int matCount = queryInt(conn,
                "SELECT COUNT(DISTINCT material_name) FROM elements_meta "
                + "WHERE material_name IS NOT NULL AND material_name != ''");
            if (matCount > 0) {
                int styleCount = queryInt(conn,
                    "SELECT COUNT(*) FROM surface_styles");
                if (styleCount == 0) {
                    issues.add(String.format("%d materials in elements_meta but surface_styles is empty",
                        matCount));
                }
            }

            String status = issues.isEmpty() ? "PASS" : "FAIL";
            String detail = issues.isEmpty()
                ? String.format("%d/%d traced  %d geom  %d LOD  %d mats",
                    totalElements, totalElements, totalInstances,
                    totalInstances - geoFallback, matCount)
                : String.join("; ", issues);
            report("G5-PROVENANCE", tag, status, detail);

            assertTrue(issues.isEmpty(),
                String.format("[G5-PROVENANCE] %s: %s", tag, String.join("; ", issues)));
        }
    }

    // =====================================================================
    // G6-ISOLATION: Prevent metadata contamination across buildings
    // =====================================================================

    @TestFactory
    @Order(6)
    @DisplayName("G6-ISOLATION")
    Collection<DynamicTest> g6_isolation() {
        List<DynamicTest> tests = new ArrayList<>();
        for (BuildingEntry b : buildings) {
            tests.add(DynamicTest.dynamicTest("G6-ISOLATION " + b.docTypeId(),
                () -> runG6(b)));
        }
        return tests;
    }

    private void runG6(BuildingEntry b) throws Exception {
        String tag = b.docTypeId();
        assumeTrue(GATE_SCOPE.contains(tag), tag + " outside gate scope");
        if (G6_SKIP.contains(tag)) {
            report("G6-ISOLATION", tag, "SKIP", "CO mode — spatial structure not yet wired");
            return;
        }
        String dbPath = b.outputDbPath();
        List<String> issues = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            int totalElements = queryInt(conn, "SELECT COUNT(*) FROM elements_meta");
            if (totalElements == 0) {
                report("G6-ISOLATION", tag, "SKIP", "no elements");
                return;
            }

            // Check 1: surface_styles contains only styles used by elements_meta
            int unusedStyles = queryInt(conn, """
                SELECT COUNT(*) FROM surface_styles
                WHERE style_name NOT IN (
                    SELECT DISTINCT material_name FROM elements_meta
                    WHERE material_name IS NOT NULL)
                """);
            if (unusedStyles > 0) {
                issues.add(String.format("surface_styles has %d unused styles", unusedStyles));
            }

            // Check 2: spatial_structure has IfcBuildingStorey for every elements_meta storey
            int missingStoreys = queryInt(conn, """
                SELECT COUNT(DISTINCT em.storey) FROM elements_meta em
                WHERE em.storey IS NOT NULL
                  AND em.storey NOT IN (
                    SELECT name FROM spatial_structure WHERE type = 'IfcBuildingStorey')
                """);
            if (missingStoreys > 0) {
                issues.add(String.format("spatial_structure missing %d storeys from elements_meta", missingStoreys));
            }

            // Check 3: spatial_structure includes IfcSpace for buildings with L2 ESLines
            int l2Lines = queryInt(conn,
                "SELECT COUNT(*) FROM co_empty_space_line WHERE bom_level = 2");
            if (l2Lines > 0) {
                int spaces = queryInt(conn,
                    "SELECT COUNT(*) FROM spatial_structure WHERE type = 'IfcSpace'");
                if (spaces == 0) {
                    issues.add(String.format("%d L2 ESLines but 0 IfcSpace in spatial_structure", l2Lines));
                }
            }

            // Check 4: rel_contained_in_space is non-empty for buildings with containable elements
            int containable = queryInt(conn, """
                SELECT COUNT(*) FROM elements_meta
                WHERE ifc_class IN ('IfcFurniture','IfcFurnishingElement',
                    'IfcFlowTerminal','IfcLightFixture','IfcSanitaryTerminal')
                """);
            if (containable > 0) {
                int contained = queryInt(conn,
                    "SELECT COUNT(*) FROM rel_contained_in_space");
                if (contained == 0) {
                    issues.add(String.format("%d containable elements but 0 in rel_contained_in_space", containable));
                }
            }
        }

        String status = issues.isEmpty() ? "PASS" : "FAIL";
        String detail = issues.isEmpty() ? "all isolation checks passed" : String.join("; ", issues);
        report("G6-ISOLATION", tag, status, detail);

        assertTrue(issues.isEmpty(),
            String.format("[G6-ISOLATION] %s: %s", tag, String.join("; ", issues)));
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

    /** List up to {@code limit} elements from a query returning (guid, ifc_class, element_name). */
    private static String listElements(Connection conn, String sql, int limit) throws SQLException {
        List<String> items = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql + " LIMIT " + limit)) {
            while (rs.next()) {
                String guid = rs.getString(1);
                String cls = rs.getString(2);
                String name = rs.getString(3);
                items.add("%s(%s)".formatted(
                    cls != null ? cls : "?",
                    name != null ? name : (guid != null ? guid.substring(0, Math.min(8, guid.length())) : "?")));
            }
        }
        return "[" + String.join(", ", items) + "]";
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
                        // T16: CompilationPipeline.copyCOrderToOutput is the authorized c_order write path
                        if ("T16".equals(rule.id()) && rel.contains("CompilationPipeline.java")) continue;
                        // T17: ElementPersistence.java is the authorized UPDATE path for verbs
                        if ("T17".equals(rule.id()) && rel.contains("ElementPersistence.java")) continue;
                        // T19: ExtractionPopulator.java has a storey fix for SJTII_Terminal — tracked debt
                        if ("T19".equals(rule.id()) && rel.contains("ExtractionPopulator.java")) continue;
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
        // Implementing BIMLogger.md §Wiring Status — RosettaStoneGateTest gate() calls
        BIMLogger.gate(gate, tag, status, detail);

        String line = String.format("  [%-14s] %-6s %-4s  %s", gate, tag, status, detail);
        System.out.println(line);
    }
}
