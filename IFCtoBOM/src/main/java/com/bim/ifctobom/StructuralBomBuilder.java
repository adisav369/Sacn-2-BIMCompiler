package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.BuildingConfig;
import com.bim.ifctobom.ClassificationYaml.SpatialContainerConfig;
import com.bim.ifctobom.ExtractionReader.ExtractionElement;
import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.*;

/**
 * Creates structural BOMs from IFC extraction data.
 *
 * <p>Direct port of {@code RosettaStoneExtract.py extract_building()}:
 * <ul>
 *   <li>BUILDING BOM header with whole-building AABB</li>
 *   <li>FLOOR STR BOMs per storey with element BOM lines</li>
 *   <li>LBD offsets (dx/dy/dz) relative to floor LBD (§4 tack convention)</li>
 *   <li>TACK children linking floors to BUILDING BOM</li>
 * </ul>
 */
public class StructuralBomBuilder {

    /**
     * Build result containing counts and computed values.
     */
    public record BuildResult(
            int totalLines,
            double aabbWidthMm, double aabbDepthMm, double aabbHeightMm,
            List<String> floorBomIds
    ) {}

    /**
     * Build structural BOMs from extraction data.
     *
     * @param bomConn     writable connection to output BOM DB
     * @param extractionConn read-only connection to *_extracted.db (for rel_aggregates), may be null
     * @param config      building classification (Order identity)
     * @param containers  resolved spatial container configs (auto-discovered or YAML override)
     * @param storeyElements extraction elements grouped by container name
     * @param excludeByStorey element keys assigned to scope spaces (per container),
     *                        skipped from FLOOR STR BOMs. May be empty.
     * @return build result with counts
     */
    // Implementing BBC.md §2 + DISC_VALIDATION_DB_SRS §10.4.13 — IFC assembly BOMs
    // IfcRelAggregates defines parent-child structure, no heuristic grouping
    // MEP elements (IfcFlow*) excluded from structural BOM — handled by IFCtoERP DISC path.
    public static BuildResult build(Connection bomConn, Connection extractionConn,
                                    BuildingConfig config,
                                    Map<String, SpatialContainerConfig> containers,
                                    Map<String, List<ExtractionElement>> storeyElements,
                                    Map<String, Set<String>> excludeByStorey,
                                    CategoryLookup catLookup)
            throws SQLException {

        String prefix = config.prefix();
        String buildingBomId = config.buildingBomId();

        // Build MEP exclusion set from YAML disciplines (§10.4.4).
        // MEP elements belong to DISC path (IFCtoERP), not structural BOM.
        Set<String> mepClasses = new HashSet<>();
        if (config.disciplines() != null) {
            var mep = config.disciplines().get("MEP");
            if (mep != null && mep.ifcClasses() != null) {
                mepClasses.addAll(mep.ifcClasses());
            }
        }

        // ── Compute building origin (LBD corner) from all elements ───────────
        List<ExtractionElement> allElements = new ArrayList<>();
        storeyElements.values().forEach(allElements::addAll);

        if (allElements.isEmpty()) {
            return new BuildResult(0, 0, 0, 0, List.of());
        }

        double allMinX = allElements.stream().mapToDouble(ExtractionElement::minX).min().orElse(0);
        double allMinY = allElements.stream().mapToDouble(ExtractionElement::minY).min().orElse(0);
        double allMinZ = allElements.stream().mapToDouble(ExtractionElement::minZ).min().orElse(0);
        double allMaxX = allElements.stream().mapToDouble(ExtractionElement::maxX).max().orElse(0);
        double allMaxY = allElements.stream().mapToDouble(ExtractionElement::maxY).max().orElse(0);
        double allMaxZ = allElements.stream().mapToDouble(ExtractionElement::maxZ).max().orElse(0);

        double aabbW = (allMaxX - allMinX) * 1000;
        double aabbD = (allMaxY - allMinY) * 1000;
        double aabbH = (allMaxZ - allMinZ) * 1000;

        // ── Create BUILDING M_Product assembly stub ──────────────────────────
        ProductRegistrar.ensureAssemblyStub(bomConn, buildingBomId, "BUILDING");

        // ── Create BUILDING BOM header ───────────────────────────────────────
        insertBomHeader(bomConn, buildingBomId,
                prefix + " " + config.name(),
                "BUILDING", "BUILDING",
                config.docSubType(), config.productCategory(),
                aabbW, aabbD, aabbH,
                allMinX, allMinY, allMinZ, catLookup);

        // ── Load IFC assembly groups from rel_aggregates ─────────────────────
        // child_guid → parent_guid (only assemblies with ≥2 matched extraction children)
        Map<String, String> childToParent = new LinkedHashMap<>();
        Map<String, List<String>> parentToChildren = new LinkedHashMap<>();
        if (extractionConn != null) {
            try (Statement stmt = extractionConn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                    "SELECT ra.parent_guid, ra.child_guid "
                    + "FROM rel_aggregates ra "
                    + "JOIN elements_meta em ON em.guid = ra.child_guid "
                    + "ORDER BY ra.parent_guid")) {
                while (rs.next()) {
                    String parent = rs.getString(1);
                    String child = rs.getString(2);
                    parentToChildren.computeIfAbsent(parent, k -> new ArrayList<>()).add(child);
                }
            } catch (SQLException e) {
                // rel_aggregates may not exist — proceed without assemblies
            }
            // Only keep assemblies with ≥2 children (singletons stay flat)
            parentToChildren.entrySet().removeIf(e -> e.getValue().size() < 2);
            for (var entry : parentToChildren.entrySet()) {
                for (String child : entry.getValue()) {
                    childToParent.put(child, entry.getKey());
                }
            }
            if (!parentToChildren.isEmpty()) {
                BIMLogger.fine("STR", "IFC assemblies: {} groups, {} children",
                        parentToChildren.size(), childToParent.size());
            }
        }

        // ── Process each storey ──────────────────────────────────────────────
        int totalLines = 0;
        List<String> floorBomIds = new ArrayList<>();

        for (Map.Entry<String, SpatialContainerConfig> storeyEntry : containers.entrySet()) {
            String storeyName = storeyEntry.getKey();
            SpatialContainerConfig storeyInfo = storeyEntry.getValue();

            List<ExtractionElement> elems = storeyElements.get(storeyName);
            if (elems == null || elems.isEmpty()) continue;

            String floorBomId = prefix + "_" + storeyInfo.code() + "_STR";
            floorBomIds.add(floorBomId);

            // ── Compute floor AABB ───────────────────────────────────────────
            double fMinX = elems.stream().mapToDouble(ExtractionElement::minX).min().orElse(0);
            double fMaxX = elems.stream().mapToDouble(ExtractionElement::maxX).max().orElse(0);
            double fMinY = elems.stream().mapToDouble(ExtractionElement::minY).min().orElse(0);
            double fMaxY = elems.stream().mapToDouble(ExtractionElement::maxY).max().orElse(0);
            double fMinZ = elems.stream().mapToDouble(ExtractionElement::minZ).min().orElse(0);
            double fMaxZ = elems.stream().mapToDouble(ExtractionElement::maxZ).max().orElse(0);

            double floorAabbW = (fMaxX - fMinX) * 1000;
            double floorAabbD = (fMaxY - fMinY) * 1000;
            double floorAabbH = (fMaxZ - fMinZ) * 1000;

            // ── Create floor M_Product assembly stub ─────────────────────────
            ProductRegistrar.ensureAssemblyStub(bomConn, floorBomId, "FLOOR");

            // ── Create FLOOR STR BOM header ──────────────────────────────────
            insertBomHeader(bomConn, floorBomId,
                    prefix + " " + storeyName + " Structured",
                    "FLOOR", "STOREY",
                    null, null,  // floor BOMs don't carry doc type
                    floorAabbW, floorAabbD, floorAabbH,
                    0, 0, 0, catLookup);  // R16: child origin = 0; offset lives in MAKE line dx
            // Set m_product_category_id separately
            updateBomCategory(bomConn, floorBomId, storeyInfo.productCategory(), catLookup);

            // ── Insert element lines (skip scope-assigned elements) ──────────
            // FACTORIZE-v2: verb-compressed LEAF writes via VerbFactorizer.
            // Groups by product → VerbDetector cascade → factored or unfactored.
            // Small groups (<4) fall through to per-instance — backward compatible.
            Set<String> excluded = excludeByStorey.getOrDefault(storeyName, Set.of());
            List<ExtractionElement> floorElems = new ArrayList<>();
            int mepSkipped = 0;
            for (ExtractionElement e : elems) {
                if (!excluded.isEmpty()
                        && excluded.contains(ScopeBomBuilder.elementKey(e))) {
                    continue;  // assigned to a SET BOM
                }
                // MEP elements excluded from structural BOM — DISC path (IFCtoERP)
                if (!mepClasses.isEmpty() && mepClasses.contains(e.ifcClass())) {
                    mepSkipped++;
                    continue;
                }
                floorElems.add(e);
            }
            if (mepSkipped > 0) {
                BIMLogger.pattern("STR", "Storey '{}': {} MEP elements excluded → DISC path",
                    storeyName, mepSkipped);
            }

            // ── Partition into assembly groups vs flat elements ──────────────
            // Elements whose GUID is a child in rel_aggregates get grouped into
            // ASSEMBLY BOMs. Elements not in any assembly stay as flat leaves.
            Map<String, List<ExtractionElement>> assemblyGroups = new LinkedHashMap<>();
            List<ExtractionElement> flatElems = new ArrayList<>();
            for (ExtractionElement e : floorElems) {
                String parentGuid = (e.guid() != null) ? childToParent.get(e.guid()) : null;
                if (parentGuid != null) {
                    assemblyGroups.computeIfAbsent(parentGuid, k -> new ArrayList<>()).add(e);
                } else {
                    flatElems.add(e);
                }
            }

            // ── Create ASSEMBLY BOMs for IFC aggregates ─────────────────────
            int asmSeq = 10;
            int asmCount = 0;
            for (var asmEntry : assemblyGroups.entrySet()) {
                List<ExtractionElement> asmElems = asmEntry.getValue();
                if (asmElems.size() < 2) {
                    // Singleton in this storey — treat as flat
                    flatElems.addAll(asmElems);
                    continue;
                }
                asmCount++;
                String asmBomId = prefix + "_" + storeyInfo.code() + "_ASM_" + asmCount;

                // Compute assembly AABB
                double aMinX = asmElems.stream().mapToDouble(ExtractionElement::minX).min().orElse(0);
                double aMaxX = asmElems.stream().mapToDouble(ExtractionElement::maxX).max().orElse(0);
                double aMinY = asmElems.stream().mapToDouble(ExtractionElement::minY).min().orElse(0);
                double aMaxY = asmElems.stream().mapToDouble(ExtractionElement::maxY).max().orElse(0);
                double aMinZ = asmElems.stream().mapToDouble(ExtractionElement::minZ).min().orElse(0);
                double aMaxZ = asmElems.stream().mapToDouble(ExtractionElement::maxZ).max().orElse(0);

                double asmW = (aMaxX - aMinX) * 1000;
                double asmD = (aMaxY - aMinY) * 1000;
                double asmH = (aMaxZ - aMinZ) * 1000;

                ProductRegistrar.ensureAssemblyStub(bomConn, asmBomId, "ASSEMBLY");
                BomWriter.insertBom(bomConn, new BomWriter.BomRowBuilder(
                        asmBomId, prefix + " Assembly " + asmCount, "ASSEMBLY", "STOREY")
                        .aabb((int) asmW, (int) asmD, (int) asmH)
                        .build());

                // Insert LEAF children into assembly BOM
                VerbFactorizer.FactorResult asmFr = VerbFactorizer.factorize(
                        bomConn, asmBomId, asmElems, aMinX, aMinY, aMinZ, 10);
                totalLines += asmFr.linesWritten();

                // Add MAKE line from FLOOR → ASSEMBLY
                double asmDx = aMinX - fMinX;
                double asmDy = aMinY - fMinY;
                double asmDz = aMinZ - fMinZ;
                insertBomLine(bomConn, floorBomId, asmBomId,
                        "ASM_" + asmCount, asmSeq, "0",
                        asmDx, asmDy, asmDz,
                        0, 0, 0, null, null, 0, null, null, null);
                asmSeq += 10;
                totalLines++;  // the MAKE line itself

                BIMLogger.fine("STR", "{} ASM_{}: {} children → {}",
                        storeyName, asmCount, asmElems.size(), asmBomId);
            }

            // ── Insert flat (non-assembly) element lines ────────────────────
            // BBC.md §4 — write IFC GUIDs to m_bom_line_ma for traceability
            // 7-arg overload: writes MA rows, keeps element_ref as product name (RE path)
            VerbFactorizer.FactorResult fr = VerbFactorizer.factorize(
                    bomConn, floorBomId, flatElems, fMinX, fMinY, fMinZ,
                    asmSeq > 10 ? asmSeq : 10);
            totalLines += fr.linesWritten();
            if (fr.verbMatched() > 0) {
                BIMLogger.fine("STR", "{} STR: {} verb patterns ({} instances), {} unfactored",
                        storeyName, fr.verbMatched(), fr.verbInstances(), fr.unfactored());
            }

            // ── Add MAKE child to BUILDING BOM ───────────────────────────────
            // MAKE dx/dy/dz = floor-to-building offset (always >= 0)
            double makeDx = fMinX - allMinX;
            double makeDy = fMinY - allMinY;
            double makeDz = fMinZ - allMinZ;

            // Implementing DISC_VALIDATION_DB_SRS §10.4.10 — PATTERN logging channel
            BIMLogger.pattern("FLOOR", "Storey '{}' (code={}): {} elements, fMinZ={:.3f}m, makeDz={:.3f}m",
                    storeyName, storeyInfo.code(), elems.size(), fMinZ, makeDz);

            insertBomLine(bomConn, buildingBomId, floorBomId,
                    storeyInfo.role(), storeyInfo.seq(), "0",
                    makeDx, makeDy, makeDz,
                    0, 0, 0,  // MAKE children don't carry allocated size
                    null, null, 0, null, null, null);
        }

        return new BuildResult(totalLines, aabbW, aabbD, aabbH, floorBomIds);
    }

    // ── SQL helpers (delegated to BomWriter — BBC.md §2.1.9) ────────────────

    // Implementing DATA_MODEL.md §7 — M_Product_Category alignment
    private static void insertBomHeader(Connection conn, String bomId, String bomName,
                                        String bomType, String groupBy,
                                        String docSubType, String productCategory,
                                        double aabbW, double aabbD, double aabbH,
                                        double originX, double originY, double originZ,
                                        CategoryLookup catLookup)
            throws SQLException {
        int catId = catLookup.getId(productCategory);
        BomWriter.insertBom(conn, new BomWriter.BomRowBuilder(bomId, bomName, bomType, groupBy)
                .docSubType(docSubType)
                .productCategoryId(catId)
                .aabb((int) aabbW, (int) aabbD, (int) aabbH)
                .origin(originX, originY, originZ)
                .build());
    }

    private static void updateBomCategory(Connection conn, String bomId, String productCategory,
                                          CategoryLookup catLookup)
            throws SQLException {
        String sql = "UPDATE m_bom SET m_product_category_id = ? WHERE Value = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int catId = catLookup.getId(productCategory);
            if (catId > 0) stmt.setInt(1, catId);
            else stmt.setNull(1, java.sql.Types.INTEGER);
            stmt.setString(2, bomId);
            stmt.executeUpdate();
        }
    }

    private static void insertBomLine(Connection conn,
                                      String bomId, String childProductId,
                                      String role, int sequence, String rotationRule,
                                      double dx, double dy, double dz,
                                      double allocW, double allocD, double allocH,
                                      String storey, String elementRef, int ordinal,
                                      String orientation,
                                      String materialName, String materialRgba)
            throws SQLException {
        insertBomLine(conn, bomId, childProductId, role, sequence,
                rotationRule, dx, dy, dz, allocW, allocD, allocH,
                storey, elementRef, ordinal, orientation, materialName, materialRgba, 1);
    }

    private static void insertBomLine(Connection conn,
                                      String bomId, String childProductId,
                                      String role, int sequence, String rotationRule,
                                      double dx, double dy, double dz,
                                      double allocW, double allocD, double allocH,
                                      String storey, String elementRef, int ordinal,
                                      String orientation,
                                      String materialName, String materialRgba,
                                      int qty)
            throws SQLException {
        // CP-4 §4a: compute geometric classification from allocated dimensions
        String archetype = VerbFactorizer.classifyArchetype(allocW, allocD, allocH);
        String scaleBand = VerbFactorizer.classifyScaleBand(allocW, allocD, allocH);

        BomWriter.insertBomLine(conn, new BomWriter.BomLineRowBuilder(bomId, childProductId, role, sequence)
                .rotationRule(rotationRule)
                .offset(dx, dy, dz)
                .qty(qty)
                .alloc(allocW, allocD, allocH)
                .storey(storey).elementRef(elementRef).ordinal(ordinal)
                .orientation(orientation)
                .material(materialName, materialRgba)
                .shape(archetype, scaleBand)
                .build());
    }
}
