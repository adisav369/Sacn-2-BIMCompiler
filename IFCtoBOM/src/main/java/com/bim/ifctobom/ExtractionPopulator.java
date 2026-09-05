package com.bim.ifctobom;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Populates {@code I_Element_Extraction} in component_library.db from a
 * Rosetta Stone reference DB ({@code *_extracted.db}).
 *
 * <h3>DETERMINISTIC — no invention</h3>
 * <p>Every value written comes from the reference DB or is derived by
 * a deterministic function of that data. No human-crafted IDs, no manual
 * migration SQL. The only human-crafted artifact is the classification YAML.
 *
 * <p>Replaces:
 * <ul>
 *   <li>{@code tools/placement_extractor.py} — Python extraction (now Java)</li>
 *   <li>{@code migration/migration_P02_SH_product_link.sql} — manual M_Product_ID
 *       mapping (now automatic: M_Product_ID = element_ref)</li>
 * </ul>
 *
 * <h3>Data chain</h3>
 * <pre>
 *   reference DB (elements_meta + elements_rtree)
 *     → I_Element_Extraction (component_library.db)
 *       → M_Product_ID = element_ref  (deterministic — no invention)
 *         → child_product_id on BOM LEAF lines
 *           → BOMWalker resolves geometry at compile time
 * </pre>
 *
 * <p>Convention: reference DB path = {@code DAGCompiler/lib/input/{building_type}_extracted.db}
 */
public class ExtractionPopulator {

    /**
     * Populate I_Element_Extraction from reference DB for a building type.
     * Deletes existing rows for this building type first (idempotent).
     *
     * @param compConn     writable connection to component_library.db
     * @param buildingType building type identifier (e.g., "Duplex")
     * @return number of rows inserted
     */
    /**
     * Extract elements from reference DB, enrich in memory, fill geometry gaps.
     *
     * <p>R13: Returns enriched elements in-memory. No I_Element_Extraction table
     * is written anywhere — the extraction DB is source truth (read-only), and
     * component_library.db is product catalog only.
     *
     * <p>Only geometry gaps (component_geometries, I_Geometry_Map) are written
     * to component_library.db — that's product data, not spatial instances.
     *
     * @return enriched elements grouped by storey (active only, rebar excluded)
     */
    public static Map<String, List<ExtractionReader.ExtractionElement>> populate(
            Connection compConn, String buildingType) throws SQLException {
        return populate(compConn, null, buildingType);
    }

    /**
     * Populate with abstract product resolution via alias lookup in ERP.db.
     *
     * @param compConn  writable connection to component_library.db
     * @param discConn  read connection to ERP.db (for ad_element_product_alias); null = fallback naming
     * @param buildingType building type identifier
     */
    public static Map<String, List<ExtractionReader.ExtractionElement>> populate(
            Connection compConn, Connection discConn, String buildingType) throws SQLException {
        Path refDb = Path.of("DAGCompiler/lib/input", buildingType + "_extracted.db");
        if (!Files.exists(refDb) || refDb.toFile().length() == 0) {
            System.err.printf("  [ExtractionPopulator] Reference DB missing or empty: %s%n", refDb);
            System.err.printf("  [ExtractionPopulator] To create it: python3 tools/extract.py --to reference <IFC_FILE> -o %s%n", refDb);
            System.err.printf("  [ExtractionPopulator] For multi-discipline IFC: merge first with tools/federation_preprocessor.py (see docs/RevitAnalysis.md)%n");
            return Map.of();
        }
        return populate(compConn, discConn, buildingType, refDb);
    }

    /**
     * Extract from a specific reference DB.
     */
    public static Map<String, List<ExtractionReader.ExtractionElement>> populate(
            Connection compConn, Connection discConn, String buildingType, Path refDb) throws SQLException {

        // Read elements from reference DB (source truth — read-only)
        List<RawElement> rawElements = readReferenceDb(refDb);
        if (rawElements.isEmpty()) {
            System.err.printf("  [ExtractionPopulator] No elements in %s%n", refDb);
            return Map.of();
        }

        // Load abstract product resolver from ERP.db alias table (DV034)
        ProductResolver resolver = (discConn != null)
                ? ProductResolver.load(discConn) : null;

        // R21: Read opening→host mappings from reference DB
        Map<String, String> fillsHostMap = readFillsHost(refDb);

        // Derive enriched rows in memory (element_ref, ordinal, M_Product_ID, orientation)
        List<ExtractionRow> rows = deriveRows(rawElements, buildingType, fillsHostMap, resolver);

        // Filter: exclude REBAR (Bonsai addon, not main construction)
        rows = rows.stream()
                .filter(r -> !"REB".equals(r.discipline))
                .collect(Collectors.toList());

        // Fill geometry gaps in component_library.db (product catalog — legitimate)
        int geoInserted = fillGeometryGaps(compConn, rows, buildingType, refDb);
        // The gap fill above only covers element_refs ABSENT from I_Geometry_Map. Rows that
        // are already present but point at a geometry_hash missing from component_geometries
        // are invisible to it, and MetadataValidator rejects the whole compile for them.
        repairDanglingGeometry(compConn, buildingType, refDb);

        // C8: Per-instance GUID geometry entries for per-instance mesh diversity.
        // When CP-1 MA provides GUIDs as element_refs, MeshBinder can resolve
        // per-instance geometry via I_Geometry_Map GUID entries.
        int guidGeo = fillGuidGeometryEntries(compConn, buildingType, refDb);

        // Convert to ExtractionReader.ExtractionElement, group by storey
        Map<String, List<ExtractionReader.ExtractionElement>> result = new LinkedHashMap<>();
        int nullProductCount = 0;
        for (ExtractionRow r : rows) {
            ExtractionReader.ExtractionElement e = new ExtractionReader.ExtractionElement(
                    r.storey, r.ifcClass, r.elementRef, r.ordinal,
                    r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
                    r.orientation, r.discipline, r.materialName(), r.materialRgba(),
                    r.mProductId, r.guid, r.hostElementRef);
            if (e.mProductId() == null || e.mProductId().isBlank()) nullProductCount++;
            result.computeIfAbsent(r.storey, k -> new ArrayList<>()).add(e);
        }

        // GUARD: NULL M_Product_ID = broken data chain
        if (nullProductCount > 0) {
            int total = rows.size();
            throw new SQLException(String.format(
                    "[FAIL] %d/%d elements have NULL M_Product_ID for %s — pipeline aborted.",
                    nullProductCount, total, buildingType));
        }

        System.out.printf("  [ExtractionPopulator] %s: %d elements → %d distinct products, %d geometry gaps filled%n",
                buildingType, rows.size(),
                rows.stream().map(r -> r.mProductId).distinct().count(),
                geoInserted);

        return result;
    }

    // ── Internal types ──────────────────────────────────────────────────────

    record RawElement(   // package-private: the FACING_GATE_V2 witness constructs these
            String ifcClass, String elementName, String storey, String discipline,
            double minX, double maxX, double minY, double maxY, double minZ, double maxZ,
            String materialName, String materialRgba, String guid,
            // Fix-1: captured IFC ObjectPlacement yaw (radians). null = NO captured placement → uncaptured
            // front → hard-fail. A captured 0.0 is a VALID front (genuinely facing 0), NOT a fallback.
            Double rotationZ
    ) {
        /** Backward-compat ctor (no captured placement). The shipped W-FACING-GATE witness uses this 13-arg
         *  form → rotationZ=null → hard-fails exactly as that witness asserts. */
        RawElement(String ifcClass, String elementName, String storey, String discipline,
                   double minX, double maxX, double minY, double maxY, double minZ, double maxZ,
                   String materialName, String materialRgba, String guid) {
            this(ifcClass, elementName, storey, discipline,
                 minX, maxX, minY, maxY, minZ, maxZ, materialName, materialRgba, guid, null);
        }
    }

    private record ExtractionRow(
            String buildingType, String storey, String ifcClass,
            String elementRef, int ordinal,
            double minX, double maxX, double minY, double maxY, double minZ, double maxZ,
            String orientation, String discipline,
            String materialName, String materialRgba,
            String mProductId, String guid,
            String hostElementRef  // R21: element_ref of host wall/slab (NULL for non-openings)
    ) {}

    // ── Read reference DB ───────────────────────────────────────────────────

    static List<RawElement> readReferenceDb(Path refDb) throws SQLException {   // package-private: W-FACING-CAPTURE drives it
        List<RawElement> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + refDb)) {
            // Fix-1: only trust rotation_z from the CURRENT extractor — gated by element_transforms
            // existing AND carrying transform_source (stale world-coords DBs lack that column, and their
            // rotation_z=0 is GIGO). When absent, rotationZ stays null → fronted elements hard-fail.
            boolean haveTransforms = hasTransformSource(conn);
            String sql = haveTransforms ? """
                    SELECT m.ifc_class, m.element_name, m.storey, m.discipline,
                           r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
                           m.material_name, m.material_rgba, m.guid,
                           CASE WHEN t.transform_source IS NOT NULL THEN t.rotation_z ELSE NULL END AS rot_z
                    FROM elements_meta m
                    JOIN elements_rtree r ON m.id = r.id
                    LEFT JOIN element_transforms t ON m.guid = t.guid
                    ORDER BY m.ifc_class, m.storey, r.minX, r.minY, r.minZ
                    """ : """
                    SELECT m.ifc_class, m.element_name, m.storey, m.discipline,
                           r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
                           m.material_name, m.material_rgba, m.guid,
                           NULL AS rot_z
                    FROM elements_meta m
                    JOIN elements_rtree r ON m.id = r.id
                    ORDER BY m.ifc_class, m.storey, r.minX, r.minY, r.minZ
                    """;
            // Extraction-side exact-duplicate filter — Witness: W-RM-DEDUP
            // Implementing PRIME RULE §EXTRACT — identical position+dims = source model error, keep one.
            Set<String> seen = new HashSet<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String ifcClass  = rs.getString(1);
                    String elemName  = rs.getString(2);
                    double minX = rs.getDouble(5), maxX = rs.getDouble(6);
                    double minY = rs.getDouble(7), maxY = rs.getDouble(8);
                    double minZ = rs.getDouble(9), maxZ = rs.getDouble(10);
                    String dedupKey = ifcClass + "|" + elemName + "|"
                            + Math.round(minX * 1000) + "|" + Math.round(maxX * 1000) + "|"
                            + Math.round(minY * 1000) + "|" + Math.round(maxY * 1000) + "|"
                            + Math.round(minZ * 1000) + "|" + Math.round(maxZ * 1000);
                    if (!seen.add(dedupKey)) {
                        double cx = (minX + maxX) / 2, cy = (minY + maxY) / 2, cz = (minZ + maxZ) / 2;
                        System.err.printf("  [ExtractionPopulator] DEDUP: skipping exact-duplicate %s %s at (%.3f,%.3f,%.3f)%n",
                                ifcClass, elemName, cx, cy, cz);
                        continue;
                    }
                    double rotZ = rs.getDouble(14);
                    Double rotationZ = rs.wasNull() ? null : rotZ;   // null = uncaptured → hard-fail
                    result.add(new RawElement(
                            ifcClass, elemName,
                            rs.getString(3), rs.getString(4),
                            minX, maxX, minY, maxY, minZ, maxZ,
                            rs.getString(11), rs.getString(12),
                            rs.getString(13), rotationZ
                    ));
                }
            }
        }
        return result;
    }

    /** Fix-1 freshness gate: true iff element_transforms exists AND carries the transform_source column
     *  (the marker the current S172 extractor stamps). Older world-coords DBs (rotation_z=0, bbox_* cols)
     *  return false → no rotation is trusted → fronted elements hard-fail rather than pass on stale zeros. */
    private static boolean hasTransformSource(Connection conn) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, "element_transforms", "transform_source")) {
            return rs.next();
        }
    }

    // ── R21: Read opening→host map ─────────────────────────────────────────

    /**
     * Read rel_fills_host table from reference DB.
     * Returns map: element_guid (door/window) → host_guid (wall/slab).
     * Empty map if table doesn't exist (pre-R21 reference DBs).
     */
    private static Map<String, String> readFillsHost(Path refDb) throws SQLException {
        Map<String, String> result = new HashMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + refDb)) {
            // Check if table exists (backward compat with pre-R21 reference DBs)
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "rel_fills_host", null)) {
                if (!rs.next()) return result;
            }
            // §PATHB: rel_fills_host maps opening→host with the filling (door/window) GUID alongside.
            // R21 wants filling→host (the door/window's host wall), so select filling_guid where present.
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT filling_guid, host_guid FROM rel_fills_host WHERE filling_guid IS NOT NULL")) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        if (!result.isEmpty()) {
            System.out.printf("  [ExtractionPopulator] R21: %d opening→host mappings loaded%n", result.size());
        }
        return result;
    }

    // ── Derive extraction rows ──────────────────────────────────────────────

    private static List<ExtractionRow> deriveRows(List<RawElement> raw, String buildingType,
                                                     Map<String, String> fillsHostMap,
                                                     ProductResolver resolver) {
        // R21: Build GUID→element_ref map for host resolution
        // First pass: derive element_ref for every element (needed to resolve host GUIDs)
        Map<String, String> guidToElementRef = new HashMap<>();
        for (RawElement e : raw) {
            if (e.guid() != null) {
                guidToElementRef.put(e.guid(), deriveElementRef(e.elementName(), e.ifcClass()));
            }
        }

        // Group by (ifc_class, storey) for ordinal assignment
        Map<String, List<RawElement>> groups = new LinkedHashMap<>();
        for (RawElement e : raw) {
            String storey = e.storey() != null ? e.storey() : "Unknown";
            // TE: federated model has NULL storeys — resolve by Z-centroid band
            if ("Terminal".equals(buildingType) && "Unknown".equals(storey)) {
                storey = resolveStoreyByZBand(e);
            }
            String key = e.ifcClass() + "|" + storey;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        List<ExtractionRow> result = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            String ifcClass = parts[0];
            String storey = parts[1];
            List<RawElement> elems = entry.getValue();

            // Already sorted by position (ORDER BY in SQL)
            int ordinal = 1;
            for (RawElement e : elems) {
                String elementRef = deriveElementRef(e.elementName(), e.ifcClass());
                // ABSTRACT orientation: captured world yaw for every class (v2), or v1 run-axis bucket if gated off.
                String orientation = FACING_GATE_V2
                        ? classifyOrientationV2(e, ifcClass, elementRef)   // captured-yaw, class-agnostic
                        : classifyOrientation(e, ifcClass);               // v1 EW/NS bucket (retained, tested)
                String discipline = e.discipline() != null ? e.discipline() : "ARC";
                // M_Product_ID: abstract catalog name via alias cascade, or element_ref fallback
                // Implementing BBC.md §9 Data Flywheel — Witness: W-PRODUCT-ABSTRACT
                // Fix-1: PRODUCT NAMING uses the v1 run-axis bucket (EW/NS/POINT), NOT the facing.
                // `orientation` now carries captured facing radians (→ rotation_rule); feeding that to the
                // resolver would rename WALL_EW → WALL_3.1416 and drift RosettaStone. Decouple the two.
                String mProductId;
                if (resolver != null) {
                    double w = e.maxX() - e.minX();
                    double d = e.maxY() - e.minY();
                    double h = e.maxZ() - e.minZ();
                    String runAxis = classifyOrientation(e, ifcClass);   // v1 bucket — naming only
                    mProductId = resolver.resolve(elementRef, ifcClass, runAxis, w, d, h);
                } else {
                    mProductId = elementRef;  // backward compat: no resolver = raw names
                }

                // R21: Resolve host element_ref from GUID chain
                String hostElementRef = null;
                if (e.guid() != null) {
                    String hostGuid = fillsHostMap.get(e.guid());
                    if (hostGuid != null) {
                        hostElementRef = guidToElementRef.get(hostGuid);
                    }
                }

                result.add(new ExtractionRow(
                        buildingType, storey, ifcClass, elementRef, ordinal,
                        e.minX(), e.maxX(), e.minY(), e.maxY(), e.minZ(), e.maxZ(),
                        orientation, discipline,
                        e.materialName(), e.materialRgba(),
                        mProductId, e.guid(), hostElementRef
                ));
                ordinal++;
            }
        }
        return result;
    }

    /**
     * Derive element_ref from IFC element name.
     * Strips the trailing instance ID (e.g., ":285330") and normalises.
     * Mirrors the logic from placement_extractor.py._derive_element_ref().
     */
    static String deriveElementRef(String name, String ifcClass) {
        if (name == null || name.isBlank()) return ifcClass;

        String[] parts = name.split(":");
        if (parts.length >= 2) {
            String last = parts[parts.length - 1].strip();
            // Strip if last segment is purely numeric (instance ID)
            if (!last.isEmpty() && last.chars().allMatch(Character::isDigit)) {
                parts = Arrays.copyOf(parts, parts.length - 1);
            }
        }
        String ref = String.join(":", parts).strip();

        // Truncate very long names
        if (ref.length() > 100) {
            ref = ref.substring(0, 97) + "...";
        }
        return ref.isEmpty() ? ifcClass : ref;
    }

    /**
     * Classify wall orientation from bounding box dimensions.
     * Returns "EW", "NS", "POINT", or null for non-wall elements.
     */
    private static String classifyOrientation(RawElement e, String ifcClass) {
        if (!"IfcWall".equals(ifcClass) && !"IfcPlate".equals(ifcClass)
                && !"IfcWallStandardCase".equals(ifcClass)) {
            return null;
        }
        double dx = e.maxX() - e.minX();
        double dy = e.maxY() - e.minY();
        if (dx > dy * 3) return "EW";
        if (dy > dx * 3) return "NS";
        return "POINT";
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FACING GATE v2 (WIDENED) — GIGO. Any element with a FRONT MUST carry a REAL captured
    // front, or the build HARD-FAILS. No silent fallback to "0" — that v1 gap let a chair (and
    // a 180°-flipped wall) reach the BOM with the wrong/absent face. v1 (classifyOrientation,
    // above) is retained verbatim; v2 replaces its semantics:
    //
    //   • WALLS/PLATES have a front too — the room-side (inward) vs outside. The AABB EW/NS
    //     proxy gives the run-axis but CANNOT tell inward from outward, so under v2 it NO LONGER
    //     counts as "has orientation". The real front comes from the IFC material-layer-set
    //     direction (LayerSetDirection/DirectionSense) or the space boundary (which side the
    //     IfcSpace sits on) — captured in Fix-1.
    //   • DIRECTIONAL FURNITURE (chair/sofa/desk/toilet/...) needs its look-direction.
    //   • Plan-symmetric / up-only objects (round table, square slab, column) have NO horizontal
    //     front — their YAW is free, so a front is N/A HERE (returns null, not a fake "0").
    //
    // WHY this is the SOURCE fix: a real front is captured NOWHERE today — element placement
    // rotation is 0 for every SH/DX element, and every component_definitions row is schema-default
    // up_axis=Z/forward_axis=Y/default_rotation=0. v1 quietly wrote rotation_rule="0". v2 refuses.
    //
    // SCOPE NOTE: this gate governs the horizontal FRONT (→ rotation_rule on the BOM line). The
    // UP frame (a round table's "top must be up") lives in a DIFFERENT column —
    // component_definitions.up_axis — and is enforced by the component-library gate (Fix-1b),
    // not here. Fix-1 fills FRONT_SOURCE below from real IFC/mesh data.
    // ──────────────────────────────────────────────────────────────────────────

    /** Toggle the v2 facing gate. ON = hard-fail any element with a front but no captured front. */
    public static volatile boolean FACING_GATE_V2 = true;

    /** Thrown when an element with a front would reach the BOM with no captured front (GIGO hard fail). */
    public static final class FacingNotCapturedException extends RuntimeException {
        public FacingNotCapturedException(String msg) { super(msg); }
    }

    // DOCTRINE (SPATIAL_DEPENDENCY_GRAPH.md §DOCTRINE / RESUME_GIGO_FACING_TEST): orientation is ABSTRACT —
    // there is NO "does this class have a front?" question and NO role-token whitelist. Every element's facing
    // IS its captured world yaw, full stop. The old hasFront(Wall/Plate/Furniture+DIRECTIONAL_ROLE_TOKENS) made
    // each new element type (a bridge girder, a shopfloor jig) need a new rule — fatal for generality. DELETED.
    // The data-driven replacement is the single question "is a real transform captured?" (FRONT_SOURCE below).

    /** Source of an element's REAL captured front, or null if not captured. NON-INVENT — never
     *  synthesise. Fix-1: the real front is the IFC ObjectPlacement YAW captured into
     *  element_transforms.rotation_z (stamped transform_source='ifc_extract' by the current extractor),
     *  threaded onto RawElement.rotationZ. Emitted as a literal radian string — LocalCoord.resolveRotation
     *  parses it straight into rotation_rule. A captured 0.0 is a VALID front (genuinely facing 0); only a
     *  MISSING placement (rotationZ==null) hard-fails. Witnesses may override this to inject a front. */
    static java.util.function.Function<RawElement, String> FRONT_SOURCE =
            e -> e.rotationZ() == null ? null : String.valueOf(e.rotationZ());

    /**
     * v2 orientation classifier — ABSTRACT, class-agnostic. Every element's facing IS its captured
     * world yaw (rotation_z, stamped transform_source='ifc_extract'), regardless of IFC class or role.
     * No hasFront whitelist, no DIRECTIONAL_ROLE_TOKENS. The one question is "is a real transform
     * captured?": captured → use it (a captured 0.0 is a VALID front, genuinely facing 0); uncaptured
     * → null + honest log (NEVER a fabricated 0, NEVER a hard fail — an unfaced element is honest data,
     * not an error). This is what lets it work for a bridge girder or a shopfloor jig with zero new rules.
     */
    static String classifyOrientationV2(RawElement e, String ifcClass, String elementRef) {
        String front = FRONT_SOURCE.apply(e);
        if (front == null || front.isBlank()) {
            // uncaptured placement yaw → no horizontal front recorded (honest null, logged — not a fake 0)
            System.out.printf("  [ExtractionPopulator] §ORIENT-UNCAPTURED class=%s ref=%s guid=%s "
                    + "— rotation_z null → orientation N/A (no fabricated 0)%n", ifcClass, elementRef, e.guid());
            return null;
        }
        return front;   // captured world yaw → rotation_rule (LocalCoord.resolveRotation parses radians)
    }

    /**
     * Resolve storey from Z-centroid for buildings with missing storey data.
     * Thresholds from TE_001_storey_normalisation.sql.
     */
    private static String resolveStoreyByZBand(RawElement e) {
        double zCentroid = (e.minZ() + e.maxZ()) / 2.0;
        if (zCentroid < 0.0)  return "Foundation";
        if (zCentroid < 4.5)  return "Ground Floor";
        if (zCentroid < 8.0)  return "Level 1";
        if (zCentroid < 11.5) return "Level 2";
        if (zCentroid < 15.0) return "Level 3";
        if (zCentroid < 18.0) return "Level 4";
        return "Roof";
    }

    // deactivateRebar REMOVED (R13) — rebar filtered in-memory, no DB UPDATE

    // ── Geometry map ─────────────────────────────────────────────────────────

    /**
     * Fill geometry gaps: for element_refs in extraction that have no geometry
     * in I_Geometry_Map, import the actual geometry from the reference DB.
     *
     * <p>This imports both the mesh blob (into component_geometries) and the
     * element→geometry link (into I_Geometry_Map). Deterministic — geometry
     * comes from the reference DB, not invented.
     */
    private static int fillGeometryGaps(Connection compConn, List<ExtractionRow> rows,
                                         String buildingType, Path refDb)
            throws SQLException {
        // Find element_refs that exist in extraction but NOT in I_Geometry_Map
        Set<String> existingRefs = new HashSet<>();
        try (PreparedStatement stmt = compConn.prepareStatement(
                "SELECT DISTINCT element_ref FROM I_Geometry_Map WHERE building_type = ?")) {
            stmt.setString(1, buildingType);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) existingRefs.add(rs.getString(1));
            }
        }

        // Find missing refs and their ifc_class
        Map<String, String> missingRefToClass = new LinkedHashMap<>();
        for (ExtractionRow r : rows) {
            if (!existingRefs.contains(r.elementRef)) {
                missingRefToClass.putIfAbsent(r.elementRef, r.ifcClass);
            }
        }
        if (missingRefToClass.isEmpty()) return 0;

        // Read geometry from reference DB for missing element_refs
        // Map: element_ref → geometry_hash (first instance)
        Map<String, String> refToGeoHash = new LinkedHashMap<>();
        Set<String> neededHashes = new HashSet<>();

        try (Connection refConn = DriverManager.getConnection("jdbc:sqlite:" + refDb)) {
            String sql = """
                    SELECT m.element_name, m.ifc_class, ei.geometry_hash
                    FROM elements_meta m
                    JOIN element_instances ei ON m.guid = ei.guid
                    WHERE ei.geometry_hash IS NOT NULL
                    """;
            try (Statement stmt = refConn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    String cls = rs.getString(2);
                    String geoHash = rs.getString(3);
                    String ref = deriveElementRef(name, cls);
                    if (missingRefToClass.containsKey(ref)) {
                        refToGeoHash.putIfAbsent(ref, geoHash);
                        neededHashes.add(geoHash);
                    }
                }
            }

            // Import missing geometry blobs into component_geometries
            if (!neededHashes.isEmpty()) {
                // Find which hashes already exist
                Set<String> existingHashes = new HashSet<>();
                try (Statement stmt = compConn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT geometry_hash FROM component_geometries")) {
                    while (rs.next()) existingHashes.add(rs.getString(1));
                }

                Set<String> toImport = new HashSet<>(neededHashes);
                toImport.removeAll(existingHashes);

                if (!toImport.isEmpty()) {
                    String selectSql = "SELECT geometry_hash, vertices, faces, vertex_count, face_count "
                            + "FROM base_geometries WHERE geometry_hash = ?";
                    String insertSql = """
                            INSERT OR IGNORE INTO component_geometries
                            (geometry_hash, vertices, faces, normals, vertex_count, face_count)
                            VALUES (?, ?, ?, NULL, ?, ?)
                            """;
                    try (PreparedStatement selStmt = refConn.prepareStatement(selectSql);
                         PreparedStatement insStmt = compConn.prepareStatement(insertSql)) {
                        for (String hash : toImport) {
                            selStmt.setString(1, hash);
                            try (ResultSet rs = selStmt.executeQuery()) {
                                if (rs.next()) {
                                    byte[] verts = rs.getBytes(2);
                                    byte[] faces = rs.getBytes(3);
                                    // S168: NULL BLOBs = hash-only mode, skip import
                                    if (verts == null || faces == null) continue;
                                    insStmt.setString(1, rs.getString(1));
                                    insStmt.setBytes(2, verts);
                                    insStmt.setBytes(3, faces);
                                    insStmt.setInt(4, rs.getInt(4));
                                    insStmt.setInt(5, rs.getInt(5));
                                    insStmt.executeUpdate();
                                }
                            }
                        }
                    }
                    System.out.printf("  [ExtractionPopulator] Imported %d geometry blobs from reference DB%n",
                            toImport.size());
                }
            }
        }

        // Insert I_Geometry_Map entries for missing refs
        String insertGeo = """
                INSERT OR IGNORE INTO I_Geometry_Map
                (building_type, element_ref, ifc_class, geometry_hash, source, provenance)
                VALUES (?, ?, ?, ?, ?, 'EXTRACTED')
                """;
        int count = 0;
        try (PreparedStatement stmt = compConn.prepareStatement(insertGeo)) {
            for (var entry : missingRefToClass.entrySet()) {
                String geoHash = refToGeoHash.get(entry.getKey());
                if (geoHash == null) {
                    System.err.printf("  [ExtractionPopulator] No geometry in reference DB for %s (%s)%n",
                            entry.getKey(), entry.getValue());
                    continue;
                }
                stmt.setString(1, buildingType);
                stmt.setString(2, entry.getKey());
                stmt.setString(3, entry.getValue());
                stmt.setString(4, geoHash);
                stmt.setString(5, "[EXTRACTED: " + buildingType + "]");
                stmt.executeUpdate();
                count++;
            }
        }
        if (count > 0) {
            System.out.printf("  [ExtractionPopulator] Filled %d I_Geometry_Map gaps from reference DB%n", count);
        }
        return count;
    }

    // R13: ensureExtractionTable, deleteExisting, insertRows, deactivateRebar DELETED.
    // I_Element_Extraction no longer persisted. Enrichment is in-memory only.

    // ── C8: Per-instance GUID geometry entries ──────────────────────────────

    /**
     * Write GUID-keyed I_Geometry_Map entries for per-instance geometry diversity.
     *
     * <p>The product-level I_Geometry_Map stores one geometry_hash per product type
     * (element_ref = product name, ordinal = NULL). When an IFC model has per-instance
     * mesh variation (e.g., 236 windows → 183 unique geometries), the product-level
     * entry collapses this diversity.
     *
     * <p>This method writes GUID-keyed entries: element_ref = IFC GUID, geometry_hash
     * = that instance's actual mesh. During compilation, CP-1 MA provides GUIDs as
     * element_refs — MeshBinder resolves per-instance geometry via these entries.
     *
     * @see MeshBinder#bind — Step 1b per-instance override
     * @see VerbFactorizer — CP-1 MA GUID threading
     */
    private static int fillGuidGeometryEntries(Connection compConn, String buildingType,
                                                Path refDb) throws SQLException {
        // Skip if refDb doesn't exist
        if (refDb == null || !java.nio.file.Files.exists(refDb)) return 0;

        // Check which GUIDs already have I_Geometry_Map entries
        Set<String> existingGuids = new HashSet<>();
        try (PreparedStatement stmt = compConn.prepareStatement(
                "SELECT element_ref FROM I_Geometry_Map WHERE building_type = ? AND source LIKE '%guid%'")) {
            stmt.setString(1, buildingType);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) existingGuids.add(rs.getString(1));
            }
        }

        int count = 0;
        String insertSql = """
                INSERT OR IGNORE INTO I_Geometry_Map
                (building_type, element_ref, ifc_class, geometry_hash, source, provenance)
                VALUES (?, ?, ?, ?, ?, 'EXTRACTED')
                """;

        try (Connection refConn = DriverManager.getConnection("jdbc:sqlite:" + refDb);
             PreparedStatement ins = compConn.prepareStatement(insertSql)) {

            // Read all (guid, ifc_class, geometry_hash) from extraction
            String sql = """
                    SELECT m.guid, m.ifc_class, ei.geometry_hash
                    FROM elements_meta m
                    JOIN element_instances ei ON m.guid = ei.guid
                    WHERE m.guid IS NOT NULL AND ei.geometry_hash IS NOT NULL
                      AND ei.geometry_hash NOT LIKE 'GEO_%'
                    """;
            try (Statement stmt = refConn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String guid = rs.getString(1);
                    if (existingGuids.contains(guid)) continue;

                    String cls = rs.getString(2);
                    String geoHash = rs.getString(3);

                    // Import geometry blob if missing from component_geometries
                    ensureGeometryBlob(compConn, refConn, geoHash);

                    ins.setString(1, buildingType);
                    ins.setString(2, guid);
                    ins.setString(3, cls);
                    ins.setString(4, geoHash);
                    ins.setString(5, "[EXTRACTED: " + buildingType + " guid]");
                    ins.executeUpdate();
                    count++;
                }
            }
        }

        if (count > 0) {
            System.out.printf("  [ExtractionPopulator] %s: %d GUID geometry entries for C8 diversity%n",
                    buildingType, count);
        }
        return count;
    }

    /**
     * Ensure a geometry blob exists in component_geometries. If missing, import
     * from the reference DB's base_geometries table.
     *
     * <p>S168: When extraction used --library mode, base_geometries has NULL BLOBs
     * (hash-only) and the geometry is already in component_library.db. In that case
     * this method is a no-op.
     */
    /**
     * Repair I_Geometry_Map rows that already exist but whose {@code geometry_hash} has no
     * matching row in {@code component_geometries}.
     *
     * <p>Why this is separate from {@link #fillGeometryGaps}: that method is a GAP fill — it
     * only looks at element_refs which are absent from I_Geometry_Map, imports blobs for
     * those, and inserts the missing rows. A row that is already present is never revisited,
     * so a dangling geometry_hash on an existing row is repaired by nothing. Sample House
     * shipped in exactly that state: all 90 of its I_Geometry_Map rows (51 distinct hashes)
     * pointed at component_geometries entries that were never imported, and
     * MetadataValidator failed the entire compile with "90 dangling refs".
     *
     * <p>Extract-only, never invent: every blob is copied verbatim from the building's own
     * reference extraction, matched on an exact geometry_hash. A hash the reference DB does
     * not carry is left dangling and reported, not synthesised. Reuses the same
     * {@link #ensureGeometryBlob} importer the other paths use, so hash-only (S168 NULL
     * BLOB) extractions remain a no-op here too.
     *
     * @return number of geometry blobs imported
     */
    private static int repairDanglingGeometry(Connection compConn, String buildingType,
                                              Path refDb) throws SQLException {
        if (refDb == null || !Files.exists(refDb)) return 0;

        List<String> dangling = new ArrayList<>();
        String findSql = """
                SELECT DISTINCT g.geometry_hash
                FROM I_Geometry_Map g
                LEFT JOIN component_geometries cg ON cg.geometry_hash = g.geometry_hash
                WHERE g.building_type = ?
                  AND g.geometry_hash IS NOT NULL
                  AND cg.geometry_hash IS NULL
                """;
        try (PreparedStatement stmt = compConn.prepareStatement(findSql)) {
            stmt.setString(1, buildingType);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) dangling.add(rs.getString(1));
            }
        }
        if (dangling.isEmpty()) return 0;

        int imported = 0;
        try (Connection refConn = DriverManager.getConnection("jdbc:sqlite:" + refDb)) {
            for (String hash : dangling) {
                ensureGeometryBlob(compConn, refConn, hash);
                imported++;
            }
        }

        // Re-check rather than trusting the import count: ensureGeometryBlob is deliberately
        // silent when the reference DB has no blob for a hash, and an unrepaired hash still
        // fails the compile downstream, so it must be reported here, not discovered there.
        int stillDangling = 0;
        try (PreparedStatement stmt = compConn.prepareStatement(
                "SELECT COUNT(DISTINCT g.geometry_hash) FROM I_Geometry_Map g "
                + "LEFT JOIN component_geometries cg ON cg.geometry_hash = g.geometry_hash "
                + "WHERE g.building_type = ? AND g.geometry_hash IS NOT NULL "
                + "AND cg.geometry_hash IS NULL")) {
            stmt.setString(1, buildingType);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) stillDangling = rs.getInt(1);
            }
        }
        System.out.printf("  [ExtractionPopulator] Repaired %d dangling geometry ref(s) from "
                + "reference DB (%d still unresolved)%n",
                dangling.size() - stillDangling, stillDangling);
        if (stillDangling > 0) {
            System.err.printf("  [ExtractionPopulator] WARNING: %d geometry_hash value(s) for %s "
                    + "are in I_Geometry_Map but in neither component_geometries nor %s — "
                    + "compile will reject them%n", stillDangling, buildingType, refDb);
        }
        return imported;
    }

    private static void ensureGeometryBlob(Connection compConn, Connection refConn,
                                            String geoHash) throws SQLException {
        // Check if already exists in library
        try (PreparedStatement check = compConn.prepareStatement(
                "SELECT 1 FROM component_geometries WHERE geometry_hash = ?")) {
            check.setString(1, geoHash);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) return; // already exists (S168 or prior import)
            }
        }

        // Import from reference DB (legacy path — _extracted.db has BLOBs)
        try (PreparedStatement sel = refConn.prepareStatement(
                "SELECT geometry_hash, vertices, faces, vertex_count, face_count FROM base_geometries WHERE geometry_hash = ?");
             PreparedStatement ins = compConn.prepareStatement(
                "INSERT OR IGNORE INTO component_geometries (geometry_hash, vertices, faces, normals, vertex_count, face_count) VALUES (?, ?, ?, NULL, ?, ?)")) {
            sel.setString(1, geoHash);
            try (ResultSet rs = sel.executeQuery()) {
                if (rs.next()) {
                    byte[] vertices = rs.getBytes(2);
                    byte[] faces = rs.getBytes(3);
                    // S168: NULL BLOBs = hash-only mode, geometry already in library
                    if (vertices == null || faces == null) return;
                    ins.setString(1, rs.getString(1));
                    ins.setBytes(2, vertices);
                    ins.setBytes(3, faces);
                    ins.setInt(4, rs.getInt(4));
                    ins.setInt(5, rs.getInt(5));
                    ins.executeUpdate();
                }
            }
        }
    }
}
