package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.BuildingConfig;
import com.bim.ifctobom.ExtractionReader.ExtractionElement;

import java.sql.*;
import java.util.*;

/**
 * Assigns extraction elements to SET BOMs via IFC spatial containment.
 *
 * <p>Implementing DISC_VALIDATION_DB_SRS §10.4.13 — IFC-driven extraction.
 * IfcRelContainedInSpatialStructure is the sole source for room assignment.
 * No YAML scope boxes, no YAML floor_rooms iteration.
 *
 * <ol>
 *   <li>Queries extraction DB for IfcSpaces per storey
 *       ({@code spatial_structure} + {@code rel_contained_in_space})</li>
 *   <li>For each IfcSpace with elements: creates SET BOM, assigns elements</li>
 *   <li>SET BOM AABB computed from assigned element extents</li>
 *   <li>Template BOM ID auto-generated: {PREFIX}_{SPACE_NAME}_SET</li>
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
            int phantomLines,
            /** SET BOMs grouped by storey: storeyName → list of (setBomId, spaceName, role). */
            Map<String, List<SetBomInfo>> setBomsByStorey
    ) {}

    /** Info about an auto-discovered SET BOM for FloorRoomBomBuilder. */
    public record SetBomInfo(String setBomId, String spaceName, String role, int seq) {}

    /**
     * IFC space discovered from extraction DB.
     */
    private record IfcSpaceInfo(String spaceName, String storeyName, Set<String> elementGuids) {}

    /**
     * Run IFC-driven scope space assignment.
     *
     * <p>Auto-discovers IfcSpaces from the extraction DB. No YAML input needed
     * for room assignment — the IFC file is the sole spatial source.
     *
     * @param bomConn        writable connection to output BOM DB
     * @param extractionConn read-only connection to *_extracted.db
     * @param prefix         building prefix (e.g. "SH", "DX") for BOM ID generation
     * @param storeyElements extraction elements grouped by storey
     * @param catLookup      category lookup for BOM headers
     * @return scope result with exclude sets and counts
     */
    // Implementing DISC_VALIDATION_DB_SRS §10.4.13 — IFC-driven extraction
    // IfcRelContainedInSpatialStructure is the sole source. No YAML scope boxes.
    public static ScopeResult build(Connection bomConn, Connection extractionConn,
                                    BuildingConfig config,
                                    Map<String, List<ExtractionElement>> storeyElements,
                                    CategoryLookup catLookup)
            throws SQLException {

        String prefix = config.prefix();

        Map<String, Set<String>> excludeByStorey = new LinkedHashMap<>();
        int totalSetLines = 0;
        List<String> setBomIds = new ArrayList<>();
        Map<String, double[]> setLbdPositions = new LinkedHashMap<>();
        int phantomLines = 0;
        Map<String, List<SetBomInfo>> setBomsByStorey = new LinkedHashMap<>();

        // Auto-discover IfcSpaces from extraction DB
        List<IfcSpaceInfo> ifcSpaces = discoverIfcSpaces(extractionConn);
        if (ifcSpaces.isEmpty()) {
            // No IFC spatial data — return empty result, all elements go to structural
            return new ScopeResult(excludeByStorey, 0, setBomIds, setLbdPositions, 0, setBomsByStorey);
        }

        for (IfcSpaceInfo space : ifcSpaces) {
            String storeyName = space.storeyName();
            List<ExtractionElement> elems = storeyElements.get(storeyName);
            if (elems == null || elems.isEmpty()) continue;

            Set<String> excludedRefs = excludeByStorey.computeIfAbsent(
                    storeyName, k -> new LinkedHashSet<>());

            // Match extraction elements by GUID against IFC containment
            List<ExtractionElement> assigned = new ArrayList<>();
            for (ExtractionElement e : elems) {
                if (excludedRefs.contains(elementKey(e))) continue;
                if (e.guid() != null && space.elementGuids().contains(e.guid())) {
                    assigned.add(e);
                }
            }

            if (assigned.isEmpty()) continue;

            // Mark as excluded from structural BOM
            for (ExtractionElement e : assigned) {
                excludedRefs.add(elementKey(e));
            }

            // Auto-generate SET BOM ID from prefix + space name
            String setBomId = generateSetBomId(prefix, space.spaceName());

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
            setLbdPositions.put(setBomId, new double[]{minX, minY, minZ});

            // Create M_Product assembly stub
            ProductRegistrar.ensureAssemblyStub(bomConn, setBomId, "SET");

            // Create SET BOM header — AABB from element extents (no YAML dims)
            String role = deriveRole(space.spaceName());
            int catId = catLookup.getId(role);
            BomWriter.insertBom(bomConn, new BomWriter.BomRowBuilder(
                    setBomId, space.spaceName() + " SET", "SET", "ROOM")
                    .productCategoryId(catId)
                    .aabb((int) setAabbW, (int) setAabbD, (int) setAabbH)
                    .build());
            setBomIds.add(setBomId);

            // Insert leaf children with LBD offset relative to SET BOM LBD (§4 tack convention)
            // BBC.md §4 — write IFC GUIDs to m_bom_line_ma for traceability
            VerbFactorizer.FactorResult vfr = VerbFactorizer.factorize(
                    bomConn, setBomId, assigned, minX, minY, minZ, 10);
            totalSetLines += vfr.linesWritten();

            // Track for FloorRoomBomBuilder hierarchy
            int seq = setBomsByStorey.getOrDefault(storeyName, List.of()).size() * 10 + 10;
            setBomsByStorey.computeIfAbsent(storeyName, k -> new ArrayList<>())
                    .add(new SetBomInfo(setBomId, space.spaceName(), role, seq));

            System.out.printf("  [IFC] storey='%s' space='%s' → %d elements → %s%n",
                    storeyName, space.spaceName(), assigned.size(), setBomId);
        }

        return new ScopeResult(excludeByStorey, totalSetLines, setBomIds, setLbdPositions, phantomLines, setBomsByStorey);
    }

    /**
     * Unique key for an element — storey + element_ref + ordinal.
     * Matches the natural key in I_Element_Extraction.
     */
    static String elementKey(ExtractionElement e) {
        return e.storey() + "|" + e.elementRef() + "|" + e.ordinal();
    }

    // ── IFC discovery ─────────────────────────────────────────────────────

    /**
     * Discover IfcSpaces from the extraction DB.
     * Queries spatial_structure for IfcSpace entries, joins to
     * rel_contained_in_space for element GUIDs, and resolves parent
     * storey name via parent_guid → IfcBuildingStorey.
     */
    private static List<IfcSpaceInfo> discoverIfcSpaces(Connection extractionConn)
            throws SQLException {
        if (extractionConn == null) return List.of();

        List<IfcSpaceInfo> spaces = new ArrayList<>();
        try {
            // Load storey GUIDs → names
            Map<String, String> storeyByGuid = new LinkedHashMap<>();
            try (Statement stmt = extractionConn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                    "SELECT guid, name FROM spatial_structure WHERE type = 'IfcBuildingStorey'")) {
                while (rs.next()) {
                    storeyByGuid.put(rs.getString(1), rs.getString(2));
                }
            }

            // Load IfcSpaces with their parent storey and contained element GUIDs
            try (Statement stmt = extractionConn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                    "SELECT ss.name, ss.parent_guid, rc.element_guid "
                    + "FROM spatial_structure ss "
                    + "JOIN rel_contained_in_space rc ON rc.space_guid = ss.guid "
                    + "WHERE ss.type = 'IfcSpace' "
                    + "ORDER BY ss.name")) {
                Map<String, Set<String>> guidsBySpace = new LinkedHashMap<>();
                Map<String, String> parentBySpace = new LinkedHashMap<>();
                while (rs.next()) {
                    String spaceName = rs.getString(1);
                    String parentGuid = rs.getString(2);
                    String elementGuid = rs.getString(3);
                    guidsBySpace.computeIfAbsent(spaceName, k -> new LinkedHashSet<>()).add(elementGuid);
                    parentBySpace.putIfAbsent(spaceName, parentGuid);
                }

                for (var entry : guidsBySpace.entrySet()) {
                    String spaceName = entry.getKey();
                    String parentGuid = parentBySpace.get(spaceName);
                    String storeyName = storeyByGuid.getOrDefault(parentGuid, "Unknown");
                    spaces.add(new IfcSpaceInfo(spaceName, storeyName, entry.getValue()));
                }
            }
        } catch (SQLException e) {
            // Tables may not exist — return empty, all elements go to structural
            return List.of();
        }
        return spaces;
    }

    /**
     * Generate SET BOM ID from prefix and IFC space name.
     * Sanitizes the space name: uppercase, replace non-alphanumeric with underscore.
     */
    private static String generateSetBomId(String prefix, String spaceName) {
        String sanitized = spaceName.toUpperCase()
                .replaceAll("[^A-Z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return prefix + "_" + sanitized + "_SET";
    }

    /**
     * Derive a role from the IFC space name. Best-effort mapping
     * from common space naming conventions.
     */
    private static String deriveRole(String spaceName) {
        String upper = spaceName.toUpperCase();
        if (upper.contains("LIVING")) return "LIVING";
        if (upper.contains("BED")) return "BEDROOM";
        if (upper.contains("KITCHEN")) return "KITCHEN";
        if (upper.contains("BATH") || upper.contains("TOILET") || upper.contains("WC")) return "BATHROOM";
        if (upper.contains("DINING")) return "DINING";
        if (upper.contains("HALL") || upper.contains("ENTRANCE") || upper.contains("LOBBY")) return "HALL";
        if (upper.contains("GARAGE")) return "GARAGE";
        if (upper.contains("OFFICE") || upper.contains("STUDY")) return "OFFICE";
        if (upper.contains("ROOF")) return "ROOF";
        // Default: use sanitized space name as role
        return spaceName.toUpperCase().replaceAll("[^A-Z0-9]", "_").replaceAll("_+", "_");
    }
}
