package com.bim.ifctobom;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * Generates skeleton {@code classify_XX.yaml} and {@code dsl_XX.bim} files
 * for onboarding a new IFC building into the pipeline.
 *
 * <p>If a reference extraction DB exists ({@code DAGCompiler/lib/input/{type}_extracted.db}),
 * auto-detects storey names, element counts, and building envelope from the DB.
 *
 * <p>Usage:
 * <pre>
 *   mvn exec:java -pl IFCtoBOM \
 *       -Dexec.mainClass="com.bim.ifctobom.NewBuildingGenerator" \
 *       -Dexec.args="--prefix XX --type BuildingType --name 'Human Name'" -q
 * </pre>
 *
 * <p>See {@code docs/IFC_ONBOARDING_RUNBOOK.md} for the full onboarding process.
 *
 * @see ClassificationYaml
 */
public class NewBuildingGenerator {

    private static final Path RESOURCES = Path.of("IFCtoBOM/src/main/resources");
    private static final Path INPUT_DIR = Path.of("DAGCompiler/lib/input");

    /** Storey info extracted from reference DB. */
    record StoreyInfo(String name, int elementCount) {}

    public static void main(String[] args) throws Exception {
        String prefix = null;
        String buildingType = null;
        String humanName = null;
        String docBase = "RE";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--prefix" -> prefix = args[++i].toUpperCase();
                case "--type"   -> buildingType = args[++i];
                case "--name"   -> humanName = args[++i];
                case "--base"   -> docBase = args[++i].toUpperCase();
                case "-h", "--help" -> { printUsage(); return; }
                default -> {
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(1);
                }
            }
        }

        if (prefix == null || buildingType == null || humanName == null) {
            System.err.println("Missing required options: --prefix, --type, --name");
            printUsage();
            System.exit(1);
        }

        String prefixLower = prefix.toLowerCase();
        Path yamlFile = RESOURCES.resolve("classify_" + prefixLower + ".yaml");
        Path dslFile  = RESOURCES.resolve("dsl_" + prefixLower + ".bim");

        // Auto-detect from reference DB if available
        Path refDb = INPUT_DIR.resolve(buildingType + "_extracted.db");
        List<StoreyInfo> storeys = Collections.emptyList();
        int elementCount = 0;
        double envelopeX = 10.0, envelopeY = 5.0;

        if (Files.exists(refDb)) {
            System.out.printf("Found reference DB: %s%n", refDb);
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + refDb)) {
                storeys = queryStoreys(conn);
                elementCount = queryElementCount(conn);
                double[] envelope = queryEnvelope(conn);
                if (envelope != null) {
                    envelopeX = Math.round(envelope[0] * 10.0) / 10.0;
                    envelopeY = Math.round(envelope[1] * 10.0) / 10.0;
                }
            }
            System.out.printf("  Element count: %d%n", elementCount);
            System.out.println("  Storeys found:");
            for (StoreyInfo s : storeys) {
                System.out.printf("    - %s (%d elements)%n", s.name(), s.elementCount());
            }
        } else {
            System.out.printf("NOTE: Reference DB not found at %s%n", refDb);
            System.out.println("      Run extract.py first (Step 1 of onboarding runbook).");
            System.out.println("      Generating skeleton with placeholder storeys.");
        }

        // Generate YAML
        if (Files.exists(yamlFile)) {
            System.out.printf("WARNING: %s already exists. Skipping YAML generation.%n", yamlFile);
        } else {
            writeYaml(yamlFile, prefix, buildingType, humanName, docBase, prefixLower, storeys);
            System.out.printf("Created: %s%n", yamlFile);
        }

        // Generate DSL
        if (Files.exists(dslFile)) {
            System.out.printf("WARNING: %s already exists. Skipping DSL generation.%n", dslFile);
        } else {
            writeDsl(dslFile, buildingType, humanName, storeys, envelopeX, envelopeY);
            System.out.printf("Created: %s%n", dslFile);
        }

        // Print next steps
        System.out.println();
        System.out.println("=== Next Steps ===");
        System.out.println();
        if (!Files.exists(refDb)) {
            System.out.printf("1. Extract reference DB:%n");
            System.out.printf("   python3 tools/extract.py --to reference <source.ifc> \\%n");
            System.out.printf("       -o %s%n", refDb);
            System.out.println();
            System.out.println("2. Re-run this tool to auto-populate storeys from extracted data");
            System.out.println();
        }
        System.out.println("Edit the generated files, then:");
        System.out.println("  - Add to scripts/construction_manifest.yaml");
        System.out.println("  - Add to GATE_SCOPE in BuildingRegistryTest + RosettaStoneGateTest");
        System.out.printf("  - Run: ./scripts/run_RosettaStones.sh classify_%s.yaml%n", prefixLower);
        System.out.println();
        System.out.println("Full guide: docs/IFC_ONBOARDING_RUNBOOK.md");
    }

    // ── YAML generation ──────────────────────────────────────────────────

    private static void writeYaml(Path file, String prefix, String buildingType,
                                   String humanName, String docBase, String prefixLower,
                                   List<StoreyInfo> storeys) throws IOException {
        var sb = new StringBuilder();
        sb.append("# ── YAML = ONLY HUMAN-CRAFTED ARTIFACT ──────────────────────────────\n");
        sb.append("# ").append(prefix).append(": ").append(humanName).append('\n');
        sb.append("# See docs/WorkOrderGuide.md for the markup rules and field dictionary.\n");
        sb.append("# See docs/IFC_ONBOARDING_RUNBOOK.md for the onboarding process.\n");
        sb.append("# ────────────────────────────────────────────────────────────────────\n");
        sb.append("schema_version: 1\n\n");
        sb.append("building:\n");
        sb.append("  building_type: ").append(buildingType).append('\n');
        sb.append("  prefix: ").append(prefix).append('\n');
        sb.append("  building_bom_id: BUILDING_").append(prefix).append("_STD\n");
        sb.append("  doc_sub_type: ").append(prefix).append('\n');
        sb.append("  product_category: ").append(docBase).append('\n');
        sb.append("  name: ").append(humanName).append('\n');
        sb.append("  dsl_file: dsl_").append(prefixLower).append(".bim\n\n");
        sb.append("  storeys:\n");

        if (storeys.isEmpty()) {
            sb.append("    # TODO: Replace with actual storey names from extracted DB\n");
            sb.append("    # Run: sqlite3 DAGCompiler/lib/input/{Type}_extracted.db \\\n");
            sb.append("    #      \"SELECT storey, COUNT(*) FROM elements_meta GROUP BY storey\"\n");
            sb.append("    \"Ground Floor\": { code: GF, product_category: GF, role: GROUND_FLOOR, seq: 1010 }\n");
            sb.append("    \"Roof\":         { code: RF, product_category: RF, role: ROOF,         seq: 1020 }\n");
        } else {
            int seqNo = 1010;
            for (StoreyInfo s : storeys) {
                String code = inferCode(s.name());
                String role = inferRole(s.name());
                sb.append(String.format("    \"%s\": { code: %s, product_category: %s, role: %s, seq: %d }  # %d elements%n",
                        s.name(), code, code, role, seqNo, s.elementCount()));
                seqNo += 10;
            }
        }

        sb.append('\n');
        sb.append("  # floor_rooms:           # uncomment if building has named rooms with AABB scope\n");
        sb.append("  #   \"Ground Floor\":\n");
        sb.append("  #     bom_id: FLOOR_").append(prefix).append("_GF_STD\n");
        sb.append("  #     product_category: GF\n");
        sb.append("  #     spaces:\n");
        sb.append("  #       - { name: ROOM_NAME, template_bom: ").append(prefix).append("_ROOM_SET, role: LIVING, seq: 10,\n");
        sb.append("  #           aabb_mm: [width, depth, height], origin_m: [x, y, z] }\n\n");
        sb.append("  # static_children:       # uncomment for fixed assemblies (slabs, roof structures)\n");
        sb.append("  #   - { child_product_id: FLOOR_SLAB_GF, role: GROUND_SLAB, seq: 5, dz: 0.0 }\n\n");
        sb.append("  # composition: null      # uncomment and configure for mirrored/paired buildings\n");

        Files.writeString(file, sb.toString());
    }

    // ── DSL generation ───────────────────────────────────────────────────

    private static void writeDsl(Path file, String buildingType, String humanName,
                                  List<StoreyInfo> storeys,
                                  double envelopeX, double envelopeY) throws IOException {
        var sb = new StringBuilder();
        sb.append("// ").append(buildingType).append(" — Rosetta Stone\n");
        sb.append("// ").append(humanName).append('\n');
        sb.append("// Reference: DAGCompiler/lib/input/").append(buildingType).append("_extracted.db\n");
        sb.append("//\n");
        sb.append("// TODO: Fill in grid spacing from building envelope query:\n");
        sb.append("//   sqlite3 DAGCompiler/lib/input/").append(buildingType)
                .append("_extracted.db \\\n");
        sb.append("//     \"SELECT ROUND(MAX(maxX)-MIN(minX),1), ")
                .append("ROUND(MAX(maxY)-MIN(minY),1) FROM elements_rtree\"\n\n");
        sb.append("BUILDING \"").append(buildingType)
                .append("\" type:SINGLE_UNIT profile:\"Default\" {\n\n");
        sb.append("    GRID {\n");
        sb.append("        axes: A, B / 1, 2\n");
        sb.append(String.format("        spacing: %.1f / %.1f         // TODO: from envelope query%n",
                envelopeX, envelopeY));
        sb.append("    }\n\n");

        if (storeys.isEmpty()) {
            sb.append("    STOREY \"Ground Floor\" level:0 height:3.0m {\n");
            sb.append("        // TODO: Add rooms and elements\n");
            sb.append("    }\n\n");
        } else {
            int level = 0;
            for (StoreyInfo s : storeys) {
                sb.append(String.format("    STOREY \"%s\" level:%d height:3.0m {%n",
                        s.name(), level));
                sb.append(String.format("        // TODO: Add rooms and elements (%d elements)%n",
                        s.elementCount()));
                sb.append("    }\n\n");
                level++;
            }
        }

        sb.append("}\n");
        Files.writeString(file, sb.toString());
    }

    // ── Reference DB queries ─────────────────────────────────────────────

    private static List<StoreyInfo> queryStoreys(Connection conn) throws SQLException {
        var list = new ArrayList<StoreyInfo>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT storey, COUNT(*) FROM elements_meta " +
                     "GROUP BY storey ORDER BY MIN(id)")) {
            while (rs.next()) {
                list.add(new StoreyInfo(rs.getString(1), rs.getInt(2)));
            }
        }
        return list;
    }

    private static int queryElementCount(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM elements_meta")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Returns [xSpan, ySpan] in metres, or null if no data. */
    private static double[] queryEnvelope(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT MAX(maxX)-MIN(minX), MAX(maxY)-MIN(minY) FROM elements_rtree")) {
            if (rs.next()) {
                double x = rs.getDouble(1);
                double y = rs.getDouble(2);
                if (x > 0 && y > 0) return new double[]{x, y};
            }
        }
        return null;
    }

    // ── Storey name → code/role inference ────────────────────────────────

    private static final Map<String, String> CODE_MAP = Map.ofEntries(
            Map.entry("ground floor", "GF"),
            Map.entry("erdgeschoss", "GF"),
            Map.entry("level 1", "L1"),
            Map.entry("1. obergeschoss", "L1"),
            Map.entry("level 2", "L2"),
            Map.entry("2. obergeschoss", "L2"),
            Map.entry("level 3", "L3"),
            Map.entry("level 4", "L4"),
            Map.entry("roof", "ROOF"),
            Map.entry("dachgeschoss", "ROOF"),
            Map.entry("basement", "KE"),
            Map.entry("keller", "KE"),
            Map.entry("foundation", "FDN"),
            Map.entry("t/fdn", "FDN"),
            Map.entry("unknown", "MISC")
    );

    static String inferCode(String storeyName) {
        String lower = storeyName.toLowerCase();
        String mapped = CODE_MAP.get(lower);
        if (mapped != null) return mapped;
        // Fallback: first 4 chars, uppercase, spaces → underscore
        String code = storeyName.length() <= 4 ? storeyName : storeyName.substring(0, 4);
        return code.toUpperCase().replace(' ', '_');
    }

    static String inferRole(String storeyName) {
        String lower = storeyName.toLowerCase();
        if (lower.contains("ground") || lower.contains("erdgeschoss") || lower.equals("level 0") || lower.equals("gf"))
            return "GROUND_FLOOR";
        if (lower.contains("roof") || lower.contains("dach"))
            return "ROOF";
        if (lower.contains("basement") || lower.contains("keller") || lower.contains("fdn") || lower.contains("found"))
            return "FOUNDATION";
        if (lower.equals("unknown"))
            return "MISC";
        return "UPPER_FLOOR";
    }

    private static void printUsage() {
        System.err.println("Usage: NewBuildingGenerator --prefix XX --type BuildingType --name \"Human Name\" [--base RE|CO]");
        System.err.println();
        System.err.println("Generates skeleton classify_XX.yaml and dsl_XX.bim in IFCtoBOM/src/main/resources/");
        System.err.println("See docs/IFC_ONBOARDING_RUNBOOK.md for the full onboarding process.");
    }
}
