package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.BuildingConfig;
import com.bim.ifctobom.ClassificationYaml.SpatialContainerConfig;
import com.bim.ifctobom.ClassificationYaml.StaticChildConfig;
import com.bim.ifctobom.ScopeBomBuilder.SetBomInfo;

import java.sql.*;
import java.util.List;
import java.util.Map;

/**
 * Links parent BOMs to child BOMs with tack offsets (BBC.md §4).
 *
 * <p>Generic parent→child hierarchy builder. Creates MAKE lines linking
 * any parent BOM to its children with correct LBD-to-LBD offsets.
 * No level-specific logic — works for BUILDING→FLOOR, FLOOR→SET, etc.
 *
 * <p>Also adds static_children (slabs, roof) to the BUILDING BOM.
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.13 — IFC-driven extraction
public class BomHierarchyBuilder {

    /**
     * Build parent→child hierarchy from IFC-discovered child BOMs + static children.
     *
     * <p>For each storey with IFC-discovered child BOMs: creates an intermediate
     * parent BOM, links children with correct offsets, and links the
     * intermediate parent to the BUILDING BOM.
     *
     * @param bomConn           writable connection to output BOM DB
     * @param config            building classification (for prefix, storeys, static_children)
     * @param storeyLbdWorld    storey world LBD positions: storeyName → [x,y,z] metres
     * @param childLbdPositions child BOM world LBD positions: bomId → [x,y,z] metres
     * @param bldgMinX          building world LBD X (metres)
     * @param bldgMinY          building world LBD Y (metres)
     * @param bldgMinZ          building world LBD Z (metres)
     * @param catLookup         category lookup for BOM headers
     * @param childBomsByStorey child BOMs grouped by storey (from ScopeBomBuilder)
     * @return number of BOM lines created
     */
    public static int build(Connection bomConn, BuildingConfig config,
                            Map<String, SpatialContainerConfig> containers,
                            Map<String, double[]> storeyLbdWorld,
                            Map<String, double[]> childLbdPositions,
                            double bldgMinX, double bldgMinY, double bldgMinZ,
                            CategoryLookup catLookup,
                            Map<String, List<SetBomInfo>> childBomsByStorey) throws SQLException {
        String buildingBomId = config.buildingBomId();
        String prefix = config.prefix();
        int lineCount = 0;

        // ── Parent→child links from IFC-discovered BOMs ─────────────────────
        if (childBomsByStorey != null && !childBomsByStorey.isEmpty()) {
            for (var entry : childBomsByStorey.entrySet()) {
                String storeyName = entry.getKey();
                List<SetBomInfo> children = entry.getValue();

                var storey = containers.get(storeyName);
                if (storey == null) continue;

                // Intermediate parent BOM for this storey's children
                String parentBomId = prefix + "_ROOM_" + storey.code();

                ProductRegistrar.ensureAssemblyStub(bomConn, parentBomId, "FLOOR");
                int catId = catLookup.getId(storey.productCategory());
                BomWriter.insertBom(bomConn, new BomWriter.BomRowBuilder(
                        parentBomId, prefix + " " + storeyName, "FLOOR", "ROOM")
                        .productCategoryId(catId)
                        .build());

                // Storey world LBD — for computing parent→child offsets
                double[] storeyLbd = storeyLbdWorld.get(storeyName);

                // Link each child: offset = childLBD - storeyLBD (§4 tack convention)
                for (SetBomInfo child : children) {
                    double[] childLbd = childLbdPositions.get(child.setBomId());
                    double dx = 0, dy = 0, dz = 0;
                    if (childLbd != null && storeyLbd != null) {
                        dx = childLbd[0] - storeyLbd[0];
                        dy = childLbd[1] - storeyLbd[1];
                        dz = childLbd[2] - storeyLbd[2];
                    }
                    BomWriter.insertBomLine(bomConn, new BomWriter.BomLineRowBuilder(
                            parentBomId, child.setBomId(), child.role(), child.seq())
                            .offset(dx, dy, dz)
                            .build());
                    lineCount++;
                }

                // Link intermediate parent to BUILDING: offset = storeyLBD - buildingLBD
                double bldgDx = 0, bldgDy = 0, bldgDz = 0;
                if (storeyLbd != null) {
                    bldgDx = storeyLbd[0] - bldgMinX;
                    bldgDy = storeyLbd[1] - bldgMinY;
                    bldgDz = storeyLbd[2] - bldgMinZ;
                }
                // Implementing BBC.md §2.2.1 — Witness: W-DX-LEAF-FIX
                // DX_ROOM_L1/L2 are sub-BOMs with children; MAKE, not LEAF
                BomWriter.insertBomLine(bomConn, new BomWriter.BomLineRowBuilder(
                        buildingBomId, parentBomId, "ROOM_" + storey.code(), storey.seq() + 5)
                        .componentType("MAKE")
                        .offset(bldgDx, bldgDy, bldgDz)
                        .build());
                lineCount++;
            }
        }

        // ── Static children (slabs, roof, etc.) ─────────────────────────────
        for (StaticChildConfig sc : config.staticChildren()) {
            ProductRegistrar.ensureAssemblyStub(bomConn, sc.childProductId(), "ASSEMBLY");
            BomWriter.insertBom(bomConn, new BomWriter.BomRowBuilder(
                    sc.childProductId(), sc.childProductId(), "ASSEMBLY", sc.role())
                    .orIgnore()
                    .build());
            BomWriter.insertBomLine(bomConn, new BomWriter.BomLineRowBuilder(
                    buildingBomId, sc.childProductId(), sc.role(), sc.seq())
                    .componentType("MAKE")
                    .offset(0.0, 0.0, sc.dz())
                    .build());
            lineCount++;
        }

        return lineCount;
    }
}
