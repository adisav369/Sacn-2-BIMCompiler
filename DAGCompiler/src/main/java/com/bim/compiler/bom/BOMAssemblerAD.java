package com.bim.compiler.bom;

import com.bim.compiler.contract.IAssembler;
import com.bim.orm.ModelQuery;
import com.bim.ormsandbox.po.X_M_BOM;
import com.bim.ormsandbox.po.X_M_BOMLine;
import java.sql.*;
import java.util.*;

/**
 * AD-Driven BOM Assembler (Phase 81B)
 *
 * Reads BOM recipes from AD tables (m_bom, m_bom_line) and applies them
 * to create assembly groupings in the output database.
 *
 * iDempiere Pattern:
 * - m_bom: BOM header (like M_BOM) - defines assembly types
 * - m_bom_line: BOM lines (like M_BOM_Product) - defines what belongs
 *
 * Key concept: Items remain as individual objects. BOM is just the recipe
 * that describes how they group together.
 *
 * Nested BOMs: A child can reference another BOM (child_bom_id), enabling:
 *   WALL_PANEL
 *   ├── IfcMember[FRAME]
 *   ├── IfcPlate[CLADDING]
 *   └── DOOR_ASSEMBLY (nested BOM)
 *         ├── IfcDoor
 *         └── IfcDiscreteAccessory[HARDWARE]
 */
public class BOMAssemblerAD implements IAssembler {

    private final Connection targetConn;  // Output database
    private final Connection libraryConn; // AD rules

    private static final String LIBRARY_PATH = "library/BOM.db";

    // Cache for AD data
    private Map<String, BOMDef> bomCache = new HashMap<>();
    private Map<String, List<BOMChild>> childCache = new HashMap<>();

    public BOMAssemblerAD(Connection targetConn) throws SQLException {
        this.targetConn = targetConn;
        this.libraryConn = DriverManager.getConnection("jdbc:sqlite:" + LIBRARY_PATH);
        loadADData();
    }

    /**
     * Load all AD BOM data into cache.
     */
    private void loadADData() throws SQLException {
        // Load BOMs via DAO
        for (X_M_BOM po : new ModelQuery<>(libraryConn, X_M_BOM::new, X_M_BOM.Table_Name)
                .where("is_active = ?", 1).list()) {
            BOMDef bom = new BOMDef(
                po.getBomId(), po.getBomName(), po.getDescription(),
                po.getTargetIfcClass(), po.getGroupBy());
            bomCache.put(bom.bomId, bom);
        }

        // Load children via DAO
        for (X_M_BOMLine po : new ModelQuery<>(libraryConn, X_M_BOMLine::new, X_M_BOMLine.Table_Name)
                .where("is_active = ?", 1).orderBy("bom_id, sequence").list()) {
            String bomId = po.getBomId();
            BOMChild child = new BOMChild(
                bomId,
                po.getChildIfcClass(), po.getChildElementType(),
                po.getChildNamePattern(), po.getChildBomId(),
                po.getRole(), po.getSequence());
            childCache.computeIfAbsent(bomId, k -> new ArrayList<>()).add(child);
        }
    }

    /**
     * Apply all active BOM recipes.
     */
    public Result applyAllRecipes() throws SQLException {
        Result total = new Result(0, 0, 0);

        for (BOMDef bom : bomCache.values()) {
            Result result = applyRecipe(bom.bomId);
            total = total.add(result);
        }

        return total;
    }

    /**
     * IAssembler contract — delegates to applyAllRecipes().
     * [EXTRACTED: ARCHITECTURE.md §Contracts]
     */
    @Override
    public IAssembler.AssemblyOutcome assemble() throws SQLException {
        Result r = applyAllRecipes();
        List<String> msgs = List.of(
            "=== BOM Assembly Result ===",
            "  Recipes applied:     " + r.recipesApplied(),
            "  Assemblies created:  " + r.assembliesCreated(),
            "  Components linked:   " + r.componentsLinked()
        );
        return IAssembler.AssemblyOutcome.of(r.recipesApplied(), msgs);
    }

    /**
     * Apply a specific BOM recipe.
     */
    public Result applyRecipe(String bomId) throws SQLException {
        BOMDef bom = bomCache.get(bomId);
        if (bom == null) return new Result(0, 0, 0);

        List<BOMChild> children = childCache.get(bomId);
        if (children == null || children.isEmpty()) return new Result(0, 0, 0);

        int assembliesCreated = 0;
        int componentsLinked = 0;

        // Group elements based on group_by strategy
        Map<String, List<ElementInfo>> groups = groupElements(bom, children);

        for (var entry : groups.entrySet()) {
            String groupKey = entry.getKey();
            List<ElementInfo> elements = entry.getValue();

            if (elements.isEmpty()) continue;

            String assemblyGuid = bomId + "_" + sanitizeKey(groupKey);

            // Skip if already exists
            if (assemblyExists(assemblyGuid)) continue;

            // Create assembly
            createAssembly(assemblyGuid, bom, groupKey, elements);
            assembliesCreated++;

            // Link components with their roles
            int seq = 1;
            for (ElementInfo elem : elements) {
                String role = findRole(elem, children);
                linkComponent(assemblyGuid, elem.guid, role, seq++);
                componentsLinked++;
            }

            // Handle nested BOMs - find child assemblies and link them
            for (BOMChild child : children) {
                if (child.childBomId != null) {
                    // This child is a nested BOM reference
                    List<String> nestedAssemblies = findNestedAssemblies(child.childBomId, groupKey);
                    for (String nestedGuid : nestedAssemblies) {
                        linkComponent(assemblyGuid, nestedGuid, child.role, seq++);
                        componentsLinked++;
                    }
                }
            }
        }

        return new Result(assembliesCreated, componentsLinked, 1);
    }

    // =========================================================================
    // AD Data Structures
    // =========================================================================

    public record BOMDef(
        String bomId,
        String name,
        String description,
        String targetIfcClass,
        String groupBy
    ) {}

    public record BOMChild(
        String bomId,
        String childIfcClass,
        String childElementType,
        String childNamePattern,
        String childBomId,      // Nested BOM reference
        String role,
        int sequence
    ) {
        boolean isNestedBom() { return childBomId != null; }
        boolean isLeaf() { return childIfcClass != null; }
    }

    public record Result(int assembliesCreated, int componentsLinked, int recipesApplied) {
        public Result add(Result other) {
            return new Result(
                assembliesCreated + other.assembliesCreated,
                componentsLinked + other.componentsLinked,
                recipesApplied + other.recipesApplied
            );
        }

        public void print() {
            System.out.println("=== BOM Assembly Result ===");
            System.out.println("  Recipes applied:     " + recipesApplied);
            System.out.println("  Assemblies created:  " + assembliesCreated);
            System.out.println("  Components linked:   " + componentsLinked);
        }
    }

    private record ElementInfo(
        String guid, String ifcClass, String elementType,
        String elementName, String storey, String room,
        double minX, double maxX, double minY, double maxY, double minZ, double maxZ
    ) {}

    // =========================================================================
    // Grouping Logic
    // =========================================================================

    private Map<String, List<ElementInfo>> groupElements(BOMDef bom, List<BOMChild> children)
            throws SQLException {

        // Build IFC class filter from leaf children
        Set<String> ifcClasses = new HashSet<>();
        for (BOMChild child : children) {
            if (child.isLeaf()) {
                ifcClasses.add(child.childIfcClass);
            }
        }

        if (ifcClasses.isEmpty()) return Map.of();

        String classFilter = String.join("','", ifcClasses);

        Map<String, List<ElementInfo>> groups = new LinkedHashMap<>();

        try (Statement stmt = targetConn.createStatement();
             ResultSet rs = stmt.executeQuery(String.format("""
                 SELECT m.guid, m.ifc_class, m.element_type, m.element_name, m.storey,
                        COALESCE(s.name, '') as room,
                        r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
                 FROM elements_meta m
                 LEFT JOIN elements_rtree r ON m.id = r.id
                 LEFT JOIN spatial_structure s ON m.guid = s.guid
                 WHERE m.ifc_class IN ('%s')
                 """, classFilter))) {

            while (rs.next()) {
                ElementInfo elem = new ElementInfo(
                    rs.getString("guid"),
                    rs.getString("ifc_class"),
                    rs.getString("element_type"),
                    rs.getString("element_name"),
                    rs.getString("storey"),
                    rs.getString("room"),
                    rs.getDouble("minX"), rs.getDouble("maxX"),
                    rs.getDouble("minY"), rs.getDouble("maxY"),
                    rs.getDouble("minZ"), rs.getDouble("maxZ")
                );

                // Check if element matches any child rule
                if (!matchesAnyChild(elem, children)) continue;

                // Determine group key based on group_by
                String groupKey = switch (bom.groupBy) {
                    case "STOREY" -> elem.storey != null ? elem.storey : "UNKNOWN";
                    case "ROOM" -> !elem.room.isEmpty() ? elem.room : elem.storey;
                    case "ELEMENT_NAME" -> extractBaseName(elem);
                    case "PROXIMITY" -> elem.storey != null ? elem.storey : "UNKNOWN";
                    default -> "DEFAULT";
                };

                groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(elem);
            }
        }

        return groups;
    }

    private boolean matchesAnyChild(ElementInfo elem, List<BOMChild> children) {
        for (BOMChild child : children) {
            if (child.isLeaf() && matchesChild(elem, child)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesChild(ElementInfo elem, BOMChild child) {
        if (!elem.ifcClass.equals(child.childIfcClass)) return false;

        if (child.childElementType != null && !child.childElementType.isEmpty()) {
            if (elem.elementType == null || !elem.elementType.equals(child.childElementType)) {
                return false;
            }
        }

        if (child.childNamePattern != null && !child.childNamePattern.isEmpty()) {
            if (elem.elementName == null) return false;
            String pattern = child.childNamePattern.replace("%", ".*");
            if (!elem.elementName.matches("(?i)" + pattern)) return false;
        }

        return true;
    }

    private String findRole(ElementInfo elem, List<BOMChild> children) {
        for (BOMChild child : children) {
            if (child.isLeaf() && matchesChild(elem, child)) {
                return child.role;
            }
        }
        return "COMPONENT";
    }

    private String extractBaseName(ElementInfo elem) {
        String guid = elem.guid;

        // Extract base name patterns
        // "STAIRFLIGHT_STAIR_A_Ground" → "STAIR_A"
        // "DOOR_LIVING_NORTH_Ground" → "LIVING"

        if (guid.contains("STAIR_")) {
            int idx = guid.indexOf("STAIR_");
            String rest = guid.substring(idx);
            String[] parts = rest.split("_");
            if (parts.length >= 2) {
                return parts[0] + "_" + parts[1];
            }
        }

        // For doors/windows, extract room name
        if (guid.startsWith("DOOR_") || guid.startsWith("WINDOW_")) {
            String[] parts = guid.split("_");
            if (parts.length >= 2) {
                return parts[1];  // Room name
            }
        }

        return elem.storey != null ? elem.storey : "UNKNOWN";
    }

    // =========================================================================
    // Nested BOM Support
    // =========================================================================

    private List<String> findNestedAssemblies(String childBomId, String parentGroupKey)
            throws SQLException {
        List<String> assemblies = new ArrayList<>();

        // Find assemblies created by the child BOM that match the parent's group context
        String pattern = childBomId + "_%";

        try (PreparedStatement ps = targetConn.prepareStatement(
            "SELECT assembly_guid FROM element_assemblies WHERE assembly_guid LIKE ?")) {
            ps.setString(1, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String guid = rs.getString("assembly_guid");
                    // Check if this assembly is related to the parent group
                    // (e.g., same storey or room)
                    if (guid.contains(sanitizeKey(parentGroupKey)) ||
                        isInSameContext(guid, parentGroupKey)) {
                        assemblies.add(guid);
                    }
                }
            }
        }

        return assemblies;
    }

    private boolean isInSameContext(String assemblyGuid, String parentGroupKey) {
        // Check if assembly belongs to same storey/room context
        String sanitized = sanitizeKey(parentGroupKey);
        return assemblyGuid.toUpperCase().contains(sanitized.toUpperCase());
    }

    // =========================================================================
    // Database Operations
    // =========================================================================

    private boolean assemblyExists(String assemblyGuid) throws SQLException {
        try (PreparedStatement ps = targetConn.prepareStatement(
            "SELECT 1 FROM element_assemblies WHERE assembly_guid = ?")) {
            ps.setString(1, assemblyGuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void createAssembly(String assemblyGuid, BOMDef bom, String groupKey,
                                 List<ElementInfo> elements) throws SQLException {
        // Calculate bounds
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (ElementInfo e : elements) {
            if (e.minX != 0 || e.maxX != 0) {
                minX = Math.min(minX, e.minX); maxX = Math.max(maxX, e.maxX);
                minY = Math.min(minY, e.minY); maxY = Math.max(maxY, e.maxY);
                minZ = Math.min(minZ, e.minZ); maxZ = Math.max(maxZ, e.maxZ);
            }
        }

        double width = minX == Double.MAX_VALUE ? 0 : maxX - minX;
        double depth = minX == Double.MAX_VALUE ? 0 : maxY - minY;
        double height = minX == Double.MAX_VALUE ? 0 : maxZ - minZ;

        try (PreparedStatement ps = targetConn.prepareStatement("""
            INSERT OR IGNORE INTO element_assemblies
            (assembly_guid, assembly_type, ifc_class, name, total_width, total_depth, total_height, storey)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            ps.setString(1, assemblyGuid);
            ps.setString(2, bom.bomId);
            ps.setString(3, bom.targetIfcClass);
            ps.setString(4, bom.name + " - " + groupKey);
            ps.setDouble(5, width);
            ps.setDouble(6, depth);
            ps.setDouble(7, height);
            ps.setString(8, groupKey);
            ps.execute();
        }
    }

    private void linkComponent(String assemblyGuid, String componentGuid,
                                String role, int sequence) throws SQLException {
        try (PreparedStatement ps = targetConn.prepareStatement("""
            INSERT OR IGNORE INTO assembly_components
            (assembly_guid, component_guid, role, local_x, local_y, local_z, sequence)
            VALUES (?, ?, ?, 0, 0, 0, ?)
            """)) {
            ps.setString(1, assemblyGuid);
            ps.setString(2, componentGuid);
            ps.setString(3, role);
            ps.setInt(4, sequence);
            ps.execute();
        }
    }

    private String sanitizeKey(String key) {
        return key.toUpperCase().replace(" ", "_").replace("-", "_");
    }

    @Override
    public void close() throws Exception {
        if (libraryConn != null) libraryConn.close();
    }

    // =========================================================================
    // Main
    // =========================================================================

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: BOMAssemblerAD <database.db> [bom_id]");
            System.out.println("  Applies BOM recipes from AD tables");
            System.out.println("  If bom_id specified, applies only that recipe");
            return;
        }

        String dbPath = args[0];
        String specificBom = args.length > 1 ? args[1] : null;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            conn.setAutoCommit(false);

            BOMAssemblerAD assembler = new BOMAssemblerAD(conn);

            // Show available recipes
            System.out.println("AD BOM Recipes:");
            for (BOMDef bom : assembler.bomCache.values()) {
                List<BOMChild> children = assembler.childCache.get(bom.bomId);
                if (children == null) children = List.of();
                long leafCount = children.stream().filter(BOMChild::isLeaf).count();
                long nestedCount = children.stream().filter(BOMChild::isNestedBom).count();
                System.out.printf("  %s (group_by: %s) - %d leaf, %d nested%n",
                    bom.bomId, bom.groupBy, leafCount, nestedCount);
            }
            System.out.println();

            // Apply recipes
            Result result;
            if (specificBom != null) {
                System.out.println("Applying recipe: " + specificBom);
                result = assembler.applyRecipe(specificBom);
            } else {
                System.out.println("Applying all recipes...");
                result = assembler.applyAllRecipes();
            }

            result.print();
            conn.commit();
            assembler.close();
        }
    }
}
