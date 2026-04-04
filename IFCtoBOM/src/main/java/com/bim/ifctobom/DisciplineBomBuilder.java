package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.BuildingConfig;
import com.bim.ifctobom.ClassificationYaml.SpatialContainerConfig;
import com.bim.ifctobom.ExtractionReader.ExtractionElement;
import com.bim.ifctobom.StructuralBomBuilder.BuildResult;
import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.*;

/**
 * Creates flat BOMs for CO (Commercial) buildings.
 *
 * <p>Hierarchy: BUILDING → FLOOR → LEAF (each line carries discipline via grouping)
 * <ul>
 *   <li>BUILDING BOM — whole-building AABB, m_product_category_id=CO</li>
 *   <li>FLOOR BOMs — one per storey (7 for TE)</li>
 *   <li>LEAF lines — factored recipe lines with verb_ref + qty, grouped by discipline</li>
 * </ul>
 *
 * <p>Discipline assignment is authoritative from I_Element_Extraction.discipline
 * (populated from federated model metadata). The YAML disciplines section
 * declares what disciplines exist; it does NOT assign elements.
 *
 * <p>Offset chain (R16): BUILDING_origin + MAKE_dx + LEAF_dx = element LBD.
 * Only BUILDING carries world origin; FLOOR origins are (0,0,0).
 * All dx/dy/dz are LBD-to-LBD (BOMBasedCompilation.md §4). Centroid =
 * LBD + (width/2, depth/2, height/2) — recovered at compile time.
 *
 * <h3>FACTORIZE-v1 F-2: Verb Pattern Compression (2026-03-17)</h3>
 * <p>Elements are grouped by (discipline, child_product_id). For each group,
 * VerbDetector runs the cascade: TILE > ROUTE > FRAME > SPRAY. If a pattern
 * matches, one recipe line is written with verb_ref + qty=N + origin dx/dy/dz.
 * Unmatched groups fall through to per-instance lines (qty=1, no verb_ref).
 *
 * <p><b>Non-Disturbance:</b> SH/DX groups are too small for pattern detection
 * (MIN_GROUP=4) and fall through to unfactored writes — identical output,
 * no regression. TE groups with 33K+ elements compress to verb recipes.
 *
 * <p><b>Guard:</b> BomValidator checkExtractionReconciliation uses SUM(qty)
 * to verify total instances match extraction count.
 */
public class DisciplineBomBuilder {

    /**
     * Build flat BOMs for a CO building (BUILDING → FLOOR → LEAF).
     * Returns the same {@link BuildResult} as {@link StructuralBomBuilder}
     * for pipeline compatibility.
     */
    public static BuildResult build(Connection bomConn, BuildingConfig config,
                                    Map<String, SpatialContainerConfig> containers,
                                    Map<String, List<ExtractionElement>> storeyElements,
                                    CategoryLookup catLookup)
            throws SQLException {

        String prefix = config.prefix();
        String buildingBomId = config.buildingBomId();

        // ── Compute building AABB from all elements ───────────────────────
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

        // ── Create BUILDING ───────────────────────────────────────────────
        ProductRegistrar.ensureAssemblyStub(bomConn, buildingBomId, "BUILDING");
        insertBomHeader(bomConn, buildingBomId,
                prefix + " " + config.name(),
                "BUILDING", "BUILDING",
                config.docSubType(), config.productCategory(),
                aabbW, aabbD, aabbH,
                allMinX, allMinY, allMinZ, catLookup);

        // ── Process each storey ───────────────────────────────────────────
        int totalLines = 0;
        List<String> floorBomIds = new ArrayList<>();
        Map<String, Integer> mepCounts = new LinkedHashMap<>();

        for (Map.Entry<String, SpatialContainerConfig> storeyEntry : containers.entrySet()) {
            String storeyName = storeyEntry.getKey();
            SpatialContainerConfig storeyInfo = storeyEntry.getValue();

            List<ExtractionElement> elems = storeyElements.get(storeyName);
            if (elems == null || elems.isEmpty()) continue;

            String floorBomId = prefix + "_" + storeyInfo.code();
            floorBomIds.add(floorBomId);

            // ── Compute floor AABB ────────────────────────────────────────
            double fMinX = elems.stream().mapToDouble(ExtractionElement::minX).min().orElse(0);
            double fMaxX = elems.stream().mapToDouble(ExtractionElement::maxX).max().orElse(0);
            double fMinY = elems.stream().mapToDouble(ExtractionElement::minY).min().orElse(0);
            double fMaxY = elems.stream().mapToDouble(ExtractionElement::maxY).max().orElse(0);
            double fMinZ = elems.stream().mapToDouble(ExtractionElement::minZ).min().orElse(0);
            double fMaxZ = elems.stream().mapToDouble(ExtractionElement::maxZ).max().orElse(0);

            double floorW = (fMaxX - fMinX) * 1000;
            double floorD = (fMaxY - fMinY) * 1000;
            double floorH = (fMaxZ - fMinZ) * 1000;

            // ── Create FLOOR BOM ──────────────────────────────────────────
            ProductRegistrar.ensureAssemblyStub(bomConn, floorBomId, "FLOOR");
            insertBomHeader(bomConn, floorBomId,
                    prefix + " " + storeyName,
                    "FLOOR", "STOREY",
                    null, storeyInfo.productCategory(),
                    floorW, floorD, floorH,
                    0, 0, 0, catLookup);  // R16: child origin = 0; offset lives in MAKE line dx

            // ── Group elements by discipline ──────────────────────────────
            Map<String, List<ExtractionElement>> byDiscipline = new LinkedHashMap<>();
            for (ExtractionElement e : elems) {
                String disc = e.discipline() != null ? e.discipline() : "ARC";
                byDiscipline.computeIfAbsent(disc, k -> new ArrayList<>()).add(e);
            }

            // ── Discipline separation (§10.4.6.1): ARC+STR to BOM, MEP to ad_sysconfig ──
            // Implementing DISC_VALIDATION_DB_SRS.md §10.4.6.1 — Witness: W-DISC-SEP-1
            // BOM = spatial structure (ARC+STR+REB). MEP elements counted and deferred
            // to Callout — positions come from RouteBuilders, not IFC tack chain.
            int discSeq = 100;
            for (Map.Entry<String, List<ExtractionElement>> discEntry : byDiscipline.entrySet()) {
                String discCode = discEntry.getKey();
                List<ExtractionElement> discElems = discEntry.getValue();
                int adOrgId = resolveAdOrgId(discCode);

                if (isMep(adOrgId)) {
                    // MEP: count to ad_sysconfig + spatial BOM with shim first line
                    // Implementing DISC_VALIDATION_DB_SRS.md §6.12.2 — Witness: W-J3-MEP-BOM
                    mepCounts.merge(discCode, discElems.size(), Integer::sum);

                    // ── Create discipline sub-BOM: shim IS the BOM parent ──
                    String discBomId = floorBomId + "_" + discCode;

                    // Compute discipline AABB within this storey
                    double dMinX = discElems.stream().mapToDouble(ExtractionElement::minX).min().orElse(fMinX);
                    double dMinY = discElems.stream().mapToDouble(ExtractionElement::minY).min().orElse(fMinY);
                    double dMinZ = discElems.stream().mapToDouble(ExtractionElement::minZ).min().orElse(fMinZ);
                    double dMaxX = discElems.stream().mapToDouble(ExtractionElement::maxX).max().orElse(fMaxX);
                    double dMaxY = discElems.stream().mapToDouble(ExtractionElement::maxY).max().orElse(fMaxY);
                    double dMaxZ = discElems.stream().mapToDouble(ExtractionElement::maxZ).max().orElse(fMaxZ);
                    double dW = (dMaxX - dMinX) * 1000;
                    double dD = (dMaxY - dMinY) * 1000;
                    double dH = (dMaxZ - dMinZ) * 1000;

                    // Create discipline sub-BOM header — the BOM IS the shim parent
                    // §6.12.2 fix: shim is M_BOM parent, not sibling line item
                    ProductRegistrar.ensureAssemblyStub(bomConn, discBomId, "MEP_ASSEMBLY");
                    String[] shimProps = resolveShimProperties(discCode);
                    BomWriter.BomRowBuilder bomBuilder = new BomWriter.BomRowBuilder(
                            discBomId, prefix + " " + storeyName + " " + discCode,
                            "MEP", "DISCIPLINE")
                            .productCategoryId(catLookup.getId(discCode))
                            .aabb((int) dW, (int) dD, (int) dH);
                    if (shimProps != null) {
                        bomBuilder.shim(shimProps[0], shimProps[1], Double.parseDouble(shimProps[2]));
                        BIMLogger.geo("SHIM", "BOM_SHIM {} host={} mount={} offset={}mm disc={} storey={}",
                                discBomId, shimProps[0], shimProps[1], shimProps[2],
                                discCode, storeyName);
                    }
                    BomWriter.insertBom(bomConn, bomBuilder.build());

                    // Children: extracted MEP elements as factorized LEAF lines
                    VerbFactorizer.FactorResult fr = VerbFactorizer.factorize(
                            bomConn, discBomId, discElems, dMinX, dMinY, dMinZ,
                            10, true, adOrgId);
                    totalLines += fr.linesWritten();
                    BIMLogger.geo("MEP", "BOM {}: {} lines ({} verb, {} unfact) disc={} storey={}",
                            discBomId, fr.linesWritten(),
                            fr.verbMatched(), fr.unfactored(), discCode, storeyName);

                    // MAKE child: FLOOR → discipline sub-BOM
                    double mepDx = dMinX - fMinX;
                    double mepDy = dMinY - fMinY;
                    double mepDz = dMinZ - fMinZ;
                    insertBomLine(bomConn, floorBomId, discBomId,
                            "MEP_" + discCode, discSeq, "0",
                            mepDx, mepDy, mepDz,
                            0, 0, 0,
                            storeyName, null, 0, null, null, null);
                    discSeq += 10;
                    continue;
                }

                // ARC/STR/REB: write to BOM as spatial structure
                // Implementing DISC_VALIDATION_DB_SRS.md §10.4.1 — Witness: W-TACK-1
                VerbFactorizer.FactorResult fr = VerbFactorizer.factorize(
                        bomConn, floorBomId, discElems, fMinX, fMinY, fMinZ, discSeq, true, adOrgId);
                totalLines += fr.linesWritten();
                discSeq = discSeq + fr.linesWritten() * 10 + 10;
                if (fr.verbMatched() > 0 || fr.unfactored() > 0) {
                    System.out.printf("  [verb] %s/%s: %d verb patterns (%d instances), %d unfactored%n",
                            storeyName, discCode, fr.verbMatched(), fr.verbInstances(), fr.unfactored());
                }
            }

            // ── MAKE child: BUILDING → FLOOR ──────────────────────────────
            double makeDx = fMinX - allMinX;
            double makeDy = fMinY - allMinY;
            double makeDz = fMinZ - allMinZ;

            BIMLogger.pattern("FLOOR", "Container '{}' (code={}): {} elements, fMinZ={:.3f}m, makeDz={:.3f}m",
                    storeyName, storeyInfo.code(), elems.size(), fMinZ, makeDz);

            insertBomLine(bomConn, buildingBomId, floorBomId,
                    storeyInfo.role(), storeyInfo.seq(), "0",
                    makeDx, makeDy, makeDz,
                    0, 0, 0,
                    null, null, 0, null, null, null);
        }

        // ── Write MEP counts to ad_sysconfig (§10.4.6.1 Class A) ─────────
        // Implementing DISC_VALIDATION_DB_SRS.md §10.4.6.1 — Witness: W-DISC-SEP-1
        if (!mepCounts.isEmpty()) {
            String upsert = "INSERT OR REPLACE INTO ad_sysconfig "
                    + "(config_key, config_value, description) VALUES (?, ?, ?)";
            try (PreparedStatement ps = bomConn.prepareStatement(upsert)) {
                for (Map.Entry<String, Integer> mc : mepCounts.entrySet()) {
                    String key = "MEP_" + mc.getKey() + "_COUNT";
                    ps.setString(1, key);
                    ps.setString(2, String.valueOf(mc.getValue()));
                    ps.setString(3, "MEP element count from extraction (discipline separation)");
                    ps.executeUpdate();
                    BIMLogger.info("EXTRACTION", "ad_sysconfig: {}={}", key, mc.getValue());
                }
            }
        }

        return new BuildResult(totalLines, aabbW, aabbD, aabbH, floorBomIds);
    }

    /** MEP disciplines: AD_Org_ID 3-8 (FP, ELEC, ACMV, CW, SP, LPG). */
    private static boolean isMep(int adOrgId) {
        return adOrgId >= 3 && adOrgId <= 8;
    }

    /**
     * Resolve shim properties for a discipline.
     * §6.12.2 fix: shim is the M_BOM parent — these properties go on the BOM header.
     *
     * @return [hostIfcClass, mount, offsetMm] or null if no shim defined
     */
    private static String[] resolveShimProperties(String discCode) {
        return switch (discCode.toUpperCase()) {
            case "FP"   -> new String[]{"IfcCovering", "BOTTOM", "5"};
            case "ELEC" -> new String[]{"IfcCovering", "BOTTOM", "5"};
            case "ACMV" -> new String[]{"IfcCovering", "BOTTOM", "5"};
            case "CW"   -> new String[]{"IfcCovering", "BOTTOM", "5"};
            case "SP"   -> new String[]{"IfcSlab",     "TOP",    "0"};
            case "LPG"  -> new String[]{"IfcWall",     "SIDE",   "0"};
            default      -> null;
        };
    }

    // ── SQL helpers (delegated to BomWriter — BBC.md §2.1.9) ────────────────

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

    /**
     * Insert a MAKE BOM line (no qty/verb_ref — structural hierarchy only).
     */
    private static void insertBomLine(Connection conn,
                                      String bomId, String childProductId,
                                      String role, int sequence, String rotationRule,
                                      double dx, double dy, double dz,
                                      double allocW, double allocD, double allocH,
                                      String storey, String elementRef, int ordinal,
                                      String orientation,
                                      String materialName, String materialRgba)
            throws SQLException {
        // CP-4 §4a: compute geometric classification from allocated dimensions
        String archetype = VerbFactorizer.classifyArchetype(allocW, allocD, allocH);
        String scaleBand = VerbFactorizer.classifyScaleBand(allocW, allocD, allocH);

        BomWriter.insertBomLine(conn, new BomWriter.BomLineRowBuilder(bomId, childProductId, role, sequence)
                .rotationRule(rotationRule)
                .offset(dx, dy, dz)
                .alloc(allocW, allocD, allocH)
                .storey(storey).elementRef(elementRef).ordinal(ordinal)
                .orientation(orientation)
                .material(materialName, materialRgba)
                .shape(archetype, scaleBand)
                .build());
    }

    /**
     * Resolve discipline code to AD_Org_ID. Mirrors Discipline.java enum values
     * without cross-module dependency. Values match ERP.db AD_Org (DV013).
     */
    private static int resolveAdOrgId(String discCode) {
        if (discCode == null) return 0;
        return switch (discCode.toUpperCase()) {
            case "ARC"  -> 1;
            case "STR"  -> 2;
            case "FP"   -> 3;
            case "ELEC" -> 4;
            case "ACMV" -> 5;
            case "CW"   -> 6;
            case "SP"   -> 7;
            case "LPG"  -> 8;
            case "REB"  -> 9;
            default     -> 0;
        };
    }
}
