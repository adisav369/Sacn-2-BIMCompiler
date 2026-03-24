package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.compiler.bom.walker.BOMWalker;
import com.bim.compiler.bom.walker.PlacementCollectorVisitor;
import com.bim.compiler.dsl.*;
import com.bim.compiler.library.ComponentLibrary;
import com.bim.ormsandbox.po.MBOM;

import java.sql.*;
import java.util.List;

/**
 * PLACE BOM &lt;doc_sub_type&gt; — the first emitting verb.
 *
 * <p>Walks the BUILDING BOM via BOMWalker + PlacementCollectorVisitor, binds each
 * placement to library geometry via MeshBinder, and writes elements to
 * output.db. This is the Phase F milestone: BIM COBOL verbs write elements,
 * not Java assembler code.
 *
 * <p>Reuses existing infrastructure wholesale:
 * <ul>
 *   <li>BOMWalker + PlacementCollectorVisitor (tack convention)</li>
 *   <li>MeshBinder.bind() (dimensional contract + scaling)</li>
 *   <li>ElementPersistence (DB writes)</li>
 *   <li>Same GUID generation logic as emitGlobalPlacementElements</li>
 * </ul>
 *
 * <p>Requires {@code VerbContext.outputConn()} to be non-null.
 *
 * <p>Grammar: {@code PLACE BOM SH} or {@code PLACE BOM DX}
 */
public class PlaceBomVerb implements Verb<PlaceBomVerb.PlaceBomPayload> {

    @Override
    public String keyword() { return "PLACE BOM"; }

    @Override
    public VerbResult<PlaceBomPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length == 0)
            return VerbResult.fail(keyword(), "usage: PLACE BOM <doc_sub_type>", null);

        String docSubType = args[0].toUpperCase();
        Connection bomConn = ctx.bomConn();
        Connection outputConn = ctx.outputConn();

        if (outputConn == null)
            return VerbResult.fail(keyword(),
                    "outputConn required — PLACE BOM is an emitting verb", null);

        // 1. Look up projectName from C_DocType
        String projectName = lookupProjectName(bomConn, docSubType);
        if (projectName == null)
            return VerbResult.fail(keyword(),
                    "No C_DocType for DocSubType=" + docSubType, null);

        // 2. Find the BUILDING BOM for this building type
        MBOM match = MBOM.getBuildingBom(bomConn, docSubType);
        if (match == null)
            return VerbResult.fail(keyword(),
                    "No BUILDING BOM for DocSubType=" + docSubType, null);

        // 3. Walk BOM → collect placements (M_Product from ERP.db)
        try (Connection compConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db")) {
            PlacementCollectorVisitor visitor = new PlacementCollectorVisitor(bomConn, projectName);
            BOMWalker walker = new BOMWalker(bomConn, compConn);
            walker.walkSelf(match.getBomId(), List.of(visitor), projectName);
            List<PlacementLoader.Placement> placements = visitor.getPlacements();

            if (placements.isEmpty())
                return VerbResult.fail(keyword(),
                        "No placements from BOM " + match.getBomId(), null);

            // 4. Open component library + create MeshBinder
            ComponentLibrary library;
            DoorWindowLibraryMapper libraryMapper;
            try {
                library = new ComponentLibrary("library/component_library.db");
                libraryMapper = new DoorWindowLibraryMapper();
            } catch (Exception e) {
                return VerbResult.fail(keyword(),
                        "Component library unavailable: " + e.getMessage(), null);
            }

            // 5. For each placement: generate GUID, bind mesh, write element
            int placed = 0;
            int errors = 0;

            try (library) {
                ElementPersistence ep = new ElementPersistence(outputConn);
                MeshBinder binder = new MeshBinder(library, libraryMapper, outputConn, ep, false);

                for (PlacementLoader.Placement p : placements) {
                    String guid = buildGuid(p);
                    String type = mapType(p.ifcClass());

                    try {
                        BoundElement be = binder.bind(p, guid, type);

                        if (be == null) {
                            // Fallback: BOM-generated furniture via familyRef
                            if (p.familyRef() != null) {
                                boolean resolved = resolveFamilyRef(p, guid, type,
                                        library, libraryMapper, ep, outputConn);
                                if (resolved) {
                                    placed++;
                                    continue;
                                }
                            }
                            // No geometry — fail loudly
                            System.err.printf("[PLACE BOM] No geometry for %s ref=%s family=%s%n",
                                    p.ifcClass(), p.elementRef(), p.familyRef());
                            errors++;
                            continue;
                        }

                        // Write bound element to output.db
                        writeBoundElement(ep, be);
                        placed++;

                    } catch (DimensionalContractViolation e) {
                        // C13: No parametric mesh in pipeline (BBC.md §2).
                        // Fix the library data, not parametric fallback.
                        System.err.printf("[PLACE BOM FAIL] %s %s: %s [fix library mesh or ASI]%n",
                                p.ifcClass(), p.elementRef(), e.getMessage());
                        errors++;
                    }
                }
            }

            PlaceBomPayload payload = new PlaceBomPayload(
                    match.getBomId(), docSubType, projectName,
                    placements.size(), placed, errors);

            if (errors > 0)
                return VerbResult.fail(keyword(),
                        String.format("PLACE BOM %s: %d placed, %d errors (of %d)",
                                docSubType, placed, errors, placements.size()),
                        payload);

            return VerbResult.ok(keyword(),
                    String.format("PLACE BOM %s: %d elements placed",
                            docSubType, placed),
                    payload);
        } // compConn auto-closed
    }

    // ── GUID generation (same logic as emitGlobalPlacementElements) ──

    private static String buildGuid(PlacementLoader.Placement p) {
        String discPrefix = switch (p.discipline()) {
            case "FP"   -> "FP_MD_";
            case "ELEC" -> "ELEC_MD_";
            case "ACMV" -> "ACMV_MD_";
            case "SP"   -> "SP_MD_";
            case "CW"   -> "CW_MD_";
            case "LPG"  -> "LPG_MD_";
            case "STR"  -> "STR_MD_";
            case "MEP"  -> "MEP_MD_";
            default -> "";  // ARC
        };
        String guidPrefix;
        if (!discPrefix.isEmpty()) {
            guidPrefix = discPrefix + p.ifcClass().replace("Ifc", "").toUpperCase() + "_";
        } else {
            guidPrefix = switch (p.ifcClass()) {
                case "IfcColumn" -> "COLUMN_MD_";
                case "IfcMember" -> "FRAME_MD_";
                case "IfcSlab"   -> "SLAB_MD_";
                default          -> "MD_" + p.ifcClass().replace("Ifc", "").toUpperCase() + "_";
            };
        }
        return guidPrefix + p.storey().replace(" ", "_").toUpperCase() + "_" + p.ordinal();
    }

    private static String mapType(String ifcClass) {
        return switch (ifcClass) {
            case "IfcSlab"  -> "FLOOR";
            case "IfcPlate" -> "CURTAIN_PANEL";
            default         -> ifcClass;
        };
    }

    // ── Write helpers ───────────────────────────────────────────────

    private static void writeBoundElement(ElementPersistence ep, BoundElement be)
            throws SQLException {
        String elementName = be.placement().familyRef() != null
                ? be.placement().familyRef()
                : be.elementRef();
        ep.writeElementMeta(be.guid(), be.ifcClass(), elementName, be.type(),
                be.storey(),
                be.placement().minX(), be.placement().maxX(),
                be.placement().minY(), be.placement().maxY(),
                be.placement().minZ(), be.placement().maxZ(),
                null, be.materialName(), be.materialRgba(),
                be.placement().elementRef());
        ep.writeInstance(be.guid(), be.geometryHash());
    }

    /**
     * Fallback for BOM-generated furniture: resolve LOD mesh from familyRef.
     * Same logic as the fallback path in emitGlobalPlacementElements.
     */
    private static boolean resolveFamilyRef(PlacementLoader.Placement p, String guid, String type,
                                            ComponentLibrary library, DoorWindowLibraryMapper mapper,
                                            ElementPersistence ep, Connection outputConn)
            throws SQLException {
        var cd = library.getByName(p.familyRef());
        if (cd == null) return false;

        double[] lb = mapper.getLocalBounds(cd.geometryHash());
        if (lb == null) return false;

        double meshW = lb[1] - lb[0];
        double meshD = lb[3] - lb[2];
        double meshH = lb[5] - lb[4];
        if (meshW <= 0.001 || meshD <= 0.001 || meshH <= 0.001) return false;

        double sX = (p.maxX() - p.minX()) / meshW;
        double sY = (p.maxY() - p.minY()) / meshD;
        double sZ = (p.maxZ() - p.minZ()) / meshH;
        double tx = p.minX() - lb[0] * sX;
        double ty = p.minY() - lb[2] * sY;
        double tz = p.minZ() - lb[4] * sZ;

        String geoHash = mapper.transformAndWriteGeometryScaled(
                outputConn, cd.geometryHash(), tx, ty, tz, 0.0, sX, sY, sZ);
        if (geoHash == null) return false;

        ep.writeElementMeta(guid, p.ifcClass(), p.familyRef(), type,
                p.storey(), p.minX(), p.maxX(), p.minY(), p.maxY(),
                p.minZ(), p.maxZ(), null, p.materialName(), p.materialRgba(),
                p.elementRef());
        ep.writeInstance(guid, geoHash);
        return true;
    }

    // ── Lookup ──────────────────────────────────────────────────────

    private String lookupProjectName(Connection conn, String docSubType) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                     "SELECT ProjectName FROM C_DocType WHERE DocSubType=?")) {
            ps.setString(1, docSubType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // ── Payload ─────────────────────────────────────────────────────

    public record PlaceBomPayload(
            String bomId,
            String docSubType,
            String projectName,
            int totalPlacements,
            int placed,
            int errors
    ) {}
}
