package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.BuildingConfig;
import com.bim.ifctobom.ClassificationYaml.FloorRoomConfig;
import com.bim.ifctobom.ClassificationYaml.SpaceConfig;
import com.bim.ifctobom.ExtractionReader.ExtractionElement;

import java.sql.*;
import java.util.*;

/**
 * Assigns extraction elements to scope spaces (SET BOMs) via centroid containment.
 *
 * <p>For each storey with {@code floor_rooms} in the classification YAML:
 * <ol>
 *   <li>Tests each element's centroid against scope boxes defined by
 *       {@code origin_m} + {@code aabb_mm}</li>
 *   <li>Creates SET BOM header ({@code bom_type=SET, group_by=ROOM})</li>
 *   <li>Creates M_Product assembly stub for the SET BOM ID</li>
 *   <li>Inserts assigned elements as leaf children with LBD offsets
 *       relative to SET BOM LBD (BOMBasedCompilation.md §4)</li>
 *   <li>Computes SET BOM AABB from assigned elements</li>
 * </ol>
 *
 * <p>Returns assigned element refs so {@link StructuralBomBuilder} can exclude them.
 */
public class ScopeBomBuilder {

    /**
     * Result of scope space assignment.
     */
    public record ScopeResult(
            /** Element refs assigned to scope spaces, keyed by storey name. */
            Map<String, Set<String>> excludeByStorey,
            /** Total leaf lines inserted across all SET BOMs. */
            int totalSetLines,
            /** SET BOM IDs created. */
            List<String> setBomIds,
            /** SET BOM LBD positions (world coords): templateBom → [minX, minY, minZ]. */
            Map<String, double[]> setLbdPositions,
            /** PHANTOM lines written (spatial availability index for Click-to-Place). */
            int phantomLines
    ) {}

    /**
     * Run scope space assignment for all storeys with floor_rooms.
     *
     * @param bomConn        writable connection to output BOM DB
     * @param extractionConn read-only connection to *_extracted.db (for IFC spatial containment)
     * @param config         building classification from YAML
     * @param storeyElements extraction elements grouped by storey
     * @return scope result with exclude sets and counts
     */
    // Implementing DISC_VALIDATION_DB_SRS §10.4.13 — IFC-driven extraction
    // IfcRelContainedInSpatialStructure replaces YAML scope boxes
    public static ScopeResult build(Connection bomConn, Connection extractionConn,
                                    BuildingConfig config,
                                    Map<String, List<ExtractionElement>> storeyElements,
                                    CategoryLookup catLookup)
            throws SQLException {

        Map<String, Set<String>> excludeByStorey = new LinkedHashMap<>();
        int totalSetLines = 0;
        List<String> setBomIds = new ArrayList<>();
        Map<String, double[]> setLbdPositions = new LinkedHashMap<>();
        int phantomLines = 0;

        for (var floorEntry : config.floorRooms().entrySet()) {
            String storeyName = floorEntry.getKey();
            FloorRoomConfig fr = floorEntry.getValue();

            List<ExtractionElement> elems = storeyElements.get(storeyName);
            if (elems == null || elems.isEmpty()) continue;

            Set<String> excludedRefs = new LinkedHashSet<>();

            for (SpaceConfig space : fr.spaces()) {
                // ── Containment: IFC spatial (ifc_space) or scope box fallback ──
                List<ExtractionElement> assigned;

                if (space.hasIfcSpace()) {
                    // IFC containment: read rel_contained_in_space from extraction DB
                    assigned = loadElementsInSpace(extractionConn, space.ifcSpace(),
                            elems, excludedRefs);
                    if (assigned.isEmpty()) {
                        // IFC space exists but no elements contained — create empty SET
                        createEmptySetBom(bomConn, space, catLookup);
                        setBomIds.add(space.templateBom());
                        System.out.printf("  [IFC] space='%s' → 0 elements (empty SET)%n",
                                space.ifcSpace());
                        continue;
                    }
                    System.out.printf("  [IFC] space='%s' → %d elements%n",
                            space.ifcSpace(), assigned.size());
                } else if (space.hasScopeBox()) {
                    // Fallback: scope box containment (existing logic)
                    assigned = filterByScopeBox(elems, space, excludedRefs);
                    if (assigned.isEmpty()) {
                        createEmptySetBom(bomConn, space, catLookup);
                        setBomIds.add(space.templateBom());
                        continue;
                    }
                } else {
                    // No containment mechanism — create empty SET BOM
                    createEmptySetBom(bomConn, space, catLookup);
                    setBomIds.add(space.templateBom());
                    continue;
                }

                // Mark as excluded from structural BOM
                for (ExtractionElement e : assigned) {
                    excludedRefs.add(elementKey(e));
                }

                // Compute SET AABB from assigned element extents
                double minX = assigned.stream().mapToDouble(ExtractionElement::minX).min().orElse(0);
                double maxX = assigned.stream().mapToDouble(ExtractionElement::maxX).max().orElse(0);
                double minY = assigned.stream().mapToDouble(ExtractionElement::minY).min().orElse(0);
                double maxY = assigned.stream().mapToDouble(ExtractionElement::maxY).max().orElse(0);
                double minZ = assigned.stream().mapToDouble(ExtractionElement::minZ).min().orElse(0);
                double maxZ = assigned.stream().mapToDouble(ExtractionElement::maxZ).max().orElse(0);

                double setAabbW = (maxX - minX) * 1000;
                double setAabbD = (maxY - minY) * 1000;
                double setAabbH = (maxZ - minZ) * 1000;

                // Record SET LBD position for FloorRoomBomBuilder (§4 tack convention)
                setLbdPositions.put(space.templateBom(), new double[]{minX, minY, minZ});

                // Create M_Product assembly stub
                ProductRegistrar.ensureAssemblyStub(bomConn, space.templateBom(), "SET");

                // Create SET BOM header
                insertSetBomHeader(bomConn, space, setAabbW, setAabbD, setAabbH, catLookup);
                setBomIds.add(space.templateBom());

                // Insert leaf children with LBD offset relative to SET BOM LBD (§4 tack convention)
                // setMinX/Y/Z = SET AABB minimum corner (computed above at lines 116-121)
                // NOT scope box origin (ox,oy,oz) — that is a containment filter only (§4.1)
                // FACTORIZE-v2: verb-compressed LEAF writes via VerbFactorizer.
                // BBC.md §4 — write IFC GUIDs to m_bom_line_ma for traceability
                // 7-arg overload: writes MA rows, keeps element_ref as product name (RE path)
                VerbFactorizer.FactorResult vfr = VerbFactorizer.factorize(
                        bomConn, space.templateBom(), assigned, minX, minY, minZ, 10);
                totalSetLines += vfr.linesWritten();

                // ── PHANTOM: spatial availability index (BBC.md §4.2) ─────────
                // INNER envelope = YAML aabb_mm (architect's intended room dims).
                // OUTER envelope = computed from assigned elements (setAabbW/D/H).
                // PHANTOM = remaining INNER volume after children are subtracted.
                // Per-axis: PHANTOM_dim = max(0, INNER_dim - OUTER_dim).
                // SAP empty storage bin principle: bin capacity = inner dims,
                // PHANTOM = remaining capacity after placed elements.
                double innerW = space.aabbW();   // YAML — INNER (mm)
                double innerD = space.aabbD();
                double innerH = space.aabbH();
                double phantomW = Math.max(0, innerW - setAabbW);
                double phantomD = Math.max(0, innerD - setAabbD);
                double phantomH = Math.max(0, innerH - setAabbH);
                if (phantomW > 0 || phantomD > 0 || phantomH > 0) {
                    int phantomSeq = vfr.linesWritten() * 10 + 10 + 10; // after last leaf
                    insertPhantomLine(bomConn, space.templateBom(), phantomSeq,
                            phantomW, phantomD, phantomH);
                    phantomLines++;
                    totalSetLines++;
                }
            }

            excludeByStorey.put(storeyName, excludedRefs);
        }

        return new ScopeResult(excludeByStorey, totalSetLines, setBomIds, setLbdPositions, phantomLines);
    }

    /**
     * Unique key for an element — storey + element_ref + ordinal.
     * Matches the natural key in I_Element_Extraction.
     */
    static String elementKey(ExtractionElement e) {
        return e.storey() + "|" + e.elementRef() + "|" + e.ordinal();
    }

    // ── Containment helpers ─────────────────────────────────────────────────

    /**
     * IFC containment: load elements assigned to an IfcSpace via
     * rel_contained_in_space in the extraction DB.
     *
     * <p>Matches extraction elements by GUID against the spatial containment
     * table. Falls back to empty list if extraction DB has no spatial data.
     */
    // Implementing DISC_VALIDATION_DB_SRS §10.4.13 — IFC-driven extraction
    private static List<ExtractionElement> loadElementsInSpace(
            Connection extractionConn, String ifcSpaceName,
            List<ExtractionElement> elems, Set<String> excludedRefs) throws SQLException {
        // Load GUIDs contained in this IfcSpace
        Set<String> containedGuids = new LinkedHashSet<>();
        try (PreparedStatement ps = extractionConn.prepareStatement(
                "SELECT rc.element_guid FROM rel_contained_in_space rc "
                + "JOIN spatial_structure ss ON rc.space_guid = ss.guid "
                + "WHERE ss.name = ?")) {
            ps.setString(1, ifcSpaceName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    containedGuids.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            // Table may not exist (old extraction DB) — fall back to empty
            return List.of();
        }

        // Match against extraction elements by GUID
        List<ExtractionElement> assigned = new ArrayList<>();
        for (ExtractionElement e : elems) {
            if (excludedRefs.contains(elementKey(e))) continue;
            if (e.guid() != null && containedGuids.contains(e.guid())) {
                assigned.add(e);
            }
        }
        return assigned;
    }

    /**
     * Scope box containment: filter elements whose centroid falls inside
     * the YAML-defined rectangular volume. Original logic, now a helper.
     */
    private static List<ExtractionElement> filterByScopeBox(
            List<ExtractionElement> elems, SpaceConfig space,
            Set<String> excludedRefs) {
        double ox = space.originM()[0];
        double oy = space.originM()[1];
        double oz = space.originM()[2];
        double sx = space.aabbW() / 1000.0;
        double sy = space.aabbD() / 1000.0;
        double sz = space.aabbH() / 1000.0;

        List<ExtractionElement> assigned = new ArrayList<>();
        for (ExtractionElement e : elems) {
            if (excludedRefs.contains(elementKey(e))) continue;
            double cx = e.centroidX();
            double cy = e.centroidY();
            double cz = e.centroidZ();
            if (cx >= ox && cx <= ox + sx
                    && cy >= oy && cy <= oy + sy
                    && cz >= oz && cz <= oz + sz) {
                assigned.add(e);
            }
        }
        return assigned;
    }

    // ── SQL helpers (delegated to BomWriter — BBC.md §2.1.9) ────────────────

    // Implementing BBC.md §4.2 — Witness: W-AABB-QUAL-1, W-PHANTOM-1
    private static void insertSetBomHeader(Connection conn, SpaceConfig space,
                                           double aabbW, double aabbD, double aabbH,
                                           CategoryLookup catLookup)
            throws SQLException {
        int catId = catLookup.getId(space.role());
        BomWriter.insertBom(conn, new BomWriter.BomRowBuilder(
                space.templateBom(), space.name() + " SET", "SET", "ROOM")
                .productCategoryId(catId)
                .aabb((int) aabbW, (int) aabbD, (int) aabbH)
                .build());
    }

    private static void createEmptySetBom(Connection conn, SpaceConfig space,
                                          CategoryLookup catLookup)
            throws SQLException {
        ProductRegistrar.ensureAssemblyStub(conn, space.templateBom(), "SET");
        // Empty SET = full INNER volume available (YAML aabb_mm).
        String aabbQual = space.hasScopeBox() ? "INNER" : "OUTER";
        int catId = catLookup.getId(space.role());
        BomWriter.insertBom(conn, new BomWriter.BomRowBuilder(
                space.templateBom(), space.name() + " SET", "SET", "ROOM")
                .productCategoryId(catId)
                .aabb(space.hasScopeBox() ? space.aabbW() : 0,
                      space.hasScopeBox() ? space.aabbD() : 0,
                      space.hasScopeBox() ? space.aabbH() : 0)
                .aabbQualifier(aabbQual)
                .build());
    }

    /**
     * Insert a PHANTOM line — spatial availability index (BBC.md §4.2).
     * Represents remaining INNER volume after children's OUTER extents are subtracted.
     * BOMWalker dispatches to onPhantom() → no output element produced.
     * Click-to-Place queries PHANTOMs for instant "where can I place this?" answers.
     */
    private static void insertPhantomLine(Connection conn, String bomId,
                                           int sequence,
                                           double phantomW, double phantomD, double phantomH)
            throws SQLException {
        BomWriter.insertBomLine(conn, new BomWriter.BomLineRowBuilder(
                bomId, "PHANTOM_" + bomId, "BUFFER", sequence)
                .componentType("PHANTOM")
                .fitPriority(99)
                .alloc(phantomW, phantomD, phantomH)
                .build());
    }

    private static void insertLeafLine(Connection conn, String bomId,
                                      ExtractionElement e, int seq,
                                      double dx, double dy, double dz)
            throws SQLException {
        // CP-4 §4a: compute geometric classification from element dimensions
        String archetype = VerbFactorizer.classifyArchetype(e.widthMm(), e.depthMm(), e.heightMm());
        String scaleBand = VerbFactorizer.classifyScaleBand(e.widthMm(), e.depthMm(), e.heightMm());

        BomWriter.insertBomLine(conn, new BomWriter.BomLineRowBuilder(bomId, e.mProductId(), e.ifcClass(), seq)
                .rotationRule(e.orientation() != null ? e.orientation() : "0")
                .offset(dx, dy, dz)
                .alloc(e.widthMm(), e.depthMm(), e.heightMm())
                .storey(e.storey()).elementRef(e.elementRef()).ordinal(e.ordinal())
                .orientation(e.orientation())
                .material(e.materialName(), e.materialRgba())
                .shape(archetype, scaleBand)
                .hostElementRef(e.hostElementRef())
                .build());
    }
}
