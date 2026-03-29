package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.BuildingConfig;
import com.bim.ifctobom.ClassificationYaml.FloorRoomConfig;
import com.bim.ifctobom.ClassificationYaml.SpaceConfig;
import com.bim.ifctobom.ClassificationYaml.StaticChildConfig;
import com.bim.ifctobom.ScopeBomBuilder.SetBomInfo;

import java.sql.*;
import java.util.List;
import java.util.Map;

/**
 * Creates FLOOR room BOMs — the FLOOR→SET hierarchy linking floors to rooms.
 *
 * <p>Two modes:
 * <ol>
 *   <li><b>IFC-driven</b> (§10.4.13): uses {@code setBomsByStorey} from
 *       {@link ScopeBomBuilder} — auto-discovered IfcSpaces, no YAML</li>
 *   <li><b>YAML-driven</b> (legacy): iterates {@code floor_rooms} from YAML</li>
 * </ol>
 *
 * <p>Also adds static_children (slabs, roof) to the BUILDING BOM.
 */
public class FloorRoomBomBuilder {

    /**
     * Build room BOMs and static children from classification YAML.
     *
     * @param bomConn          writable connection to output BOM DB
     * @param config           building classification
     * @param floorLbdWorld    floor world LBD positions: storeyName → [worldX,worldY,worldZ] in metres
     * @param setLbdPositions  SET BOM world LBD positions: templateBom → [minX,minY,minZ] in metres
     * @param bldgMinX         building world LBD X (metres)
     * @param bldgMinY         building world LBD Y (metres)
     * @param bldgMinZ         building world LBD Z (metres)
     * @return number of BOM lines created
     */
    public static int build(Connection bomConn, BuildingConfig config,
                            Map<String, double[]> floorLbdWorld,
                            Map<String, double[]> setLbdPositions,
                            double bldgMinX, double bldgMinY, double bldgMinZ,
                            CategoryLookup catLookup,
                            Map<String, List<SetBomInfo>> setBomsByStorey) throws SQLException {
        String buildingBomId = config.buildingBomId();
        String prefix = config.prefix();
        int lineCount = 0;

        // ── Floor room BOMs ──────────────────────────────────────────────────
        // IFC-driven: use setBomsByStorey from ScopeBomBuilder (auto-discovered IfcSpaces)
        // YAML-driven: fall back to config.floorRooms() if setBomsByStorey is empty
        boolean ifcDriven = setBomsByStorey != null && !setBomsByStorey.isEmpty();

        if (ifcDriven) {
            for (var entry : setBomsByStorey.entrySet()) {
                String storeyName = entry.getKey();
                List<SetBomInfo> sets = entry.getValue();

                var storey = config.storeys().get(storeyName);
                if (storey == null) continue;

                String floorBomId = prefix + "_ROOM_" + storey.code();

                ProductRegistrar.ensureAssemblyStub(bomConn, floorBomId, "FLOOR");
                insertBomHeader(bomConn, floorBomId,
                        prefix + " " + storeyName + " Rooms",
                        "FLOOR", "ROOM", storey.productCategory(), catLookup);

                double[] floorLbd = floorLbdWorld.get(storeyName);

                for (SetBomInfo set : sets) {
                    double[] setLbd = setLbdPositions.get(set.setBomId());
                    double spaceDx = 0, spaceDy = 0, spaceDz = 0;
                    if (setLbd != null && floorLbd != null) {
                        spaceDx = setLbd[0] - floorLbd[0];
                        spaceDy = setLbd[1] - floorLbd[1];
                        spaceDz = setLbd[2] - floorLbd[2];
                    }
                    insertSetLine(bomConn, floorBomId, set.setBomId(), set.role(), set.seq(),
                            spaceDx, spaceDy, spaceDz);
                    lineCount++;
                }

                double bldgDx = 0, bldgDy = 0, bldgDz = 0;
                if (floorLbd != null) {
                    bldgDx = floorLbd[0] - bldgMinX;
                    bldgDy = floorLbd[1] - bldgMinY;
                    bldgDz = floorLbd[2] - bldgMinZ;
                }
                insertBuildingChild(bomConn, buildingBomId, floorBomId,
                        "LEAF", "ROOM_" + storey.code(), storey.seq() + 5,
                        bldgDx, bldgDy, bldgDz);
                lineCount++;
            }
        } else {
            // Legacy YAML-driven path
            for (var floorEntry : config.floorRooms().entrySet()) {
                String storeyName = floorEntry.getKey();
                FloorRoomConfig fr = floorEntry.getValue();
                String floorBomId = fr.bomId();

                ProductRegistrar.ensureAssemblyStub(bomConn, floorBomId, "FLOOR");
                insertBomHeader(bomConn, floorBomId,
                        prefix + " " + storeyName + " Rooms",
                        "FLOOR", "ROOM", fr.productCategory(), catLookup);

                double[] floorLbd = floorLbdWorld.get(storeyName);

                for (SpaceConfig space : fr.spaces()) {
                    double[] setLbd = setLbdPositions.get(space.templateBom());
                    double spaceDx = 0, spaceDy = 0, spaceDz = 0;
                    if (setLbd != null && floorLbd != null) {
                        spaceDx = setLbd[0] - floorLbd[0];
                        spaceDy = setLbd[1] - floorLbd[1];
                        spaceDz = setLbd[2] - floorLbd[2];
                    }
                    insertSpaceLine(bomConn, floorBomId, space, spaceDx, spaceDy, spaceDz);
                    lineCount++;
                }

                var storey = config.storeys().get(storeyName);
                if (storey != null) {
                    double bldgDx = 0, bldgDy = 0, bldgDz = 0;
                    if (floorLbd != null) {
                        bldgDx = floorLbd[0] - bldgMinX;
                        bldgDy = floorLbd[1] - bldgMinY;
                        bldgDz = floorLbd[2] - bldgMinZ;
                    }
                    insertBuildingChild(bomConn, buildingBomId, floorBomId,
                            "LEAF", "ROOM_" + storey.code(), storey.seq() + 5,
                            bldgDx, bldgDy, bldgDz);
                    lineCount++;
                }
            }
        }

        // ── Static children ──────────────────────────────────────────────────
        for (StaticChildConfig sc : config.staticChildren()) {
            // Ensure product stub + empty BOM header exist so BomDropper
            // treats these as sub-assemblies (containers), not LEAFs.
            // P91 P-QTY fix: static children contribute 0 to LEAF qty.
            ProductRegistrar.ensureAssemblyStub(bomConn, sc.childProductId(), "ASSEMBLY");
            insertEmptyBomHeader(bomConn, sc.childProductId(), sc.role());

            insertStaticChild(bomConn, buildingBomId, sc);
            lineCount++;
        }

        return lineCount;
    }

    // ── SQL helpers (delegated to BomWriter — BBC.md §2.1.9) ────────────────

    // Implementing BBC.md §4.2 — Witness: W-AABB-QUAL-1
    private static void insertBomHeader(Connection conn, String bomId, String bomName,
                                        String bomType, String groupBy, String productCategory,
                                        CategoryLookup catLookup)
            throws SQLException {
        // FLOOR ROOM BOMs contain YAML-sourced room dimensions → INNER qualifier.
        int catId = catLookup.getId(productCategory);
        BomWriter.insertBom(conn, new BomWriter.BomRowBuilder(bomId, bomName, bomType, groupBy)
                .productCategoryId(catId)
                .aabbQualifier("INNER")
                .build());
    }

    private static void insertSpaceLine(Connection conn, String bomId, SpaceConfig space,
                                        double dx, double dy, double dz)
            throws SQLException {
        // CP-4 §4a: compute geometric classification from space AABB
        String archetype = VerbFactorizer.classifyArchetype(space.aabbW(), space.aabbD(), space.aabbH());
        String scaleBand = VerbFactorizer.classifyScaleBand(space.aabbW(), space.aabbD(), space.aabbH());

        BomWriter.insertBomLine(conn, new BomWriter.BomLineRowBuilder(
                bomId, space.templateBom(), space.role(), space.seq())
                .offset(dx, dy, dz)
                .alloc(space.aabbW(), space.aabbD(), space.aabbH())
                .shape(archetype, scaleBand)
                .build());
    }

    /** IFC-driven: insert SET child line under FLOOR BOM. */
    private static void insertSetLine(Connection conn, String floorBomId,
                                      String setBomId, String role, int seq,
                                      double dx, double dy, double dz) throws SQLException {
        BomWriter.insertBomLine(conn, new BomWriter.BomLineRowBuilder(
                floorBomId, setBomId, role, seq)
                .offset(dx, dy, dz)
                .build());
    }

    private static void insertBuildingChild(Connection conn, String buildingBomId,
                                            String childId, String componentType,
                                            String role, int seq,
                                            double dx, double dy, double dz) throws SQLException {
        BomWriter.insertBomLine(conn, new BomWriter.BomLineRowBuilder(
                buildingBomId, childId, role, seq)
                .componentType(componentType)
                .offset(dx, dy, dz)
                .build());
    }

    /**
     * Create an empty BOM header for a static child so BomDropper
     * recurses into it as a sub-assembly (producing a container c_orderline)
     * instead of treating it as a LEAF.
     */
    private static void insertEmptyBomHeader(Connection conn, String bomId, String role)
            throws SQLException {
        BomWriter.insertBom(conn, new BomWriter.BomRowBuilder(bomId, bomId, "ASSEMBLY", role)
                .orIgnore()
                .build());
    }

    private static void insertStaticChild(Connection conn, String buildingBomId,
                                          StaticChildConfig sc) throws SQLException {
        BomWriter.insertBomLine(conn, new BomWriter.BomLineRowBuilder(
                buildingBomId, sc.childProductId(), sc.role(), sc.seq())
                .componentType("MAKE")
                .offset(0.0, 0.0, sc.dz())
                .build());
    }
}
