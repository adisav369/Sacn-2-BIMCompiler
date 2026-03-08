package com.bim.compiler.bom.walker;

import com.bim.ormsandbox.po.MBOM;

import java.sql.*;
import java.util.*;

/**
 * NORM-3a Phase B: Visitor that creates element_assemblies + assembly_components.
 *
 * <p>Ports {@link com.bim.compiler.bom.BOMAssemblerAD} logic to the {@link BOMVisitor} pattern.
 * When used via {@link BOMWalker#walkSelf(String, List, String)}, it creates the same
 * assemblies as BOMAssemblerAD.applyAllRecipes() — one assembly per (BOM, group) pair.
 *
 * <h2>Assembly stack model</h2>
 * <ul>
 *   <li>{@code onMake}: push new AssemblyContext (bomId = ctx.childProductId() or ctx.bomId())</li>
 *   <li>{@code onBuy}: query elements_meta matching product ifc_class + child rules; add to top context</li>
 *   <li>{@code onMakeComplete}: pop top context, group elements, write assemblies</li>
 *   <li>{@code flush}: write batch to outputConn</li>
 * </ul>
 *
 * <h2>Phase B parallel run</h2>
 * <p>INSERT OR IGNORE on both tables ensures safe parallel operation alongside
 * the existing {@code BOMAssemblerAD} path. No data is overwritten.
 */
public class AssemblyStructureVisitor implements BOMVisitor {

    private final Connection outputConn;  // output.db
    private final Connection bomConn;     // BOM.db (for BOM defs)

    // Stack of assembly contexts — onMake pushes, onMakeComplete pops
    private final Deque<AssemblyContext> stack = new ArrayDeque<>();

    // Pending writes accumulated from the walk (deferred to flush())
    private final List<PendingAssembly> pendingAssemblies = new ArrayList<>();
    private final List<PendingComponent> pendingComponents = new ArrayList<>();

    // Statistics
    private int assembliesWritten = 0;
    private int componentsWritten = 0;

    public AssemblyStructureVisitor(Connection outputConn, Connection bomConn) {
        this.outputConn = outputConn;
        this.bomConn = bomConn;
    }

    // ── Internal records ──────────────────────────────────────────────────────

    /** One entry in the assembly context stack, corresponding to a MAKE BOM being walked. */
    private static class AssemblyContext {
        final String bomId;         // the BOM this context represents
        final String bomName;       // for assembly name: "BED_SET"
        final String groupBy;       // STOREY / ROOM / ELEMENT_NAME / DEFAULT
        final String targetIfcClass; // for element_assemblies.ifc_class

        // BUY children encountered during this BOM's subtree walk
        final List<ChildRule> childRules = new ArrayList<>();
        // Elements matched against child rules
        final List<ElementInfo> elements = new ArrayList<>();

        AssemblyContext(String bomId, String bomName, String groupBy, String targetIfcClass) {
            this.bomId = bomId;
            this.bomName = bomName;
            this.groupBy = groupBy != null ? groupBy : "DEFAULT";
            this.targetIfcClass = targetIfcClass;
        }
    }

    /** Rule from a BOM line's BUY child — used to match elements_meta rows. */
    private record ChildRule(
        String role,
        String ifcClass,        // from M_Product.ifc_class
        String elementType,     // from m_bom_line.child_element_type
        String namePattern      // from m_bom_line.child_name_pattern (LIKE → regex)
    ) {}

    /** One row from elements_meta query. */
    private record ElementInfo(
        String guid, String ifcClass, String elementType,
        String elementName, String storey, String room,
        double minX, double maxX, double minY, double maxY, double minZ, double maxZ
    ) {}

    /** Accumulated pending write for element_assemblies. */
    private record PendingAssembly(
        String assemblyGuid, String assemblyType, String ifcClass, String name,
        double width, double depth, double height, String storey
    ) {}

    /** Accumulated pending write for assembly_components. */
    private record PendingComponent(
        String assemblyGuid, String componentGuid, String role, int sequence
    ) {}

    // ── BOMVisitor events ────────────────────────────────────────────────────

    @Override
    public void onMake(BOMWalker.NodeContext ctx) {
        // ctx.bom() is the OWNING BOM; for walkSelf root calls, ctx.bom() IS the root BOM.
        // ctx.childProductId() is the child BOM's ID when this is a nested MAKE line.
        String bomId = (ctx.line() != null) ? ctx.childProductId() : ctx.bomId();
        if (bomId == null) return;

        // Load BOM definition for this assembly
        try {
            MBOM bom = new MBOM(bomConn);
            if (!bom.load(bomId)) {
                // BOM not found — still push minimal context so stack stays balanced
                stack.push(new AssemblyContext(bomId, bomId, "DEFAULT", null));
                return;
            }
            stack.push(new AssemblyContext(
                bomId,
                bom.getBomName() != null ? bom.getBomName() : bomId,
                bom.getGroupBy(),
                bom.getTargetIfcClass()
            ));
        } catch (SQLException e) {
            System.err.printf("[AssemblyStructureVisitor] Failed to load BOM %s: %s%n",
                bomId, e.getMessage());
            stack.push(new AssemblyContext(bomId, bomId, "DEFAULT", null));
        }
    }

    @Override
    public void onMakeComplete(BOMWalker.NodeContext ctx) {
        if (stack.isEmpty()) {
            System.err.println("[AssemblyStructureVisitor] onMakeComplete with empty stack — bug");
            return;
        }
        AssemblyContext ac = stack.pop();
        if (ac.childRules.isEmpty()) return; // no BUY children — nothing to assemble

        // Query elements_meta for all child IFC classes
        try {
            queryElements(ac);
        } catch (SQLException e) {
            System.err.printf("[AssemblyStructureVisitor] element query failed for %s: %s%n",
                ac.bomId, e.getMessage());
        }

        if (ac.elements.isEmpty()) return;

        // Group and schedule assembly writes
        Map<String, List<ElementInfo>> groups = groupElements(ac);
        for (var entry : groups.entrySet()) {
            String groupKey = entry.getKey();
            List<ElementInfo> elems = entry.getValue();
            if (elems.isEmpty()) continue;

            String assemblyGuid = ac.bomId + "_" + sanitize(groupKey);
            scheduleAssembly(assemblyGuid, ac, groupKey, elems);
        }
    }

    @Override
    public void onBuy(BOMWalker.NodeContext ctx) {
        if (stack.isEmpty()) return; // root-level BUY with no MAKE context — ignore

        AssemblyContext ac = stack.peek();
        // Get IFC class from M_Product
        String ifcClass = ctx.product() != null ? ctx.product().getIfcClass() : null;
        if (ifcClass == null || ifcClass.isBlank()) return; // product has no IFC class — skip

        String elementType = ctx.line() != null ? ctx.line().getChildElementType() : null;
        String namePattern = ctx.line() != null ? ctx.line().getChildNamePattern() : null;
        String role = ctx.role() != null ? ctx.role() : "COMPONENT";

        ac.childRules.add(new ChildRule(role, ifcClass, elementType, namePattern));
    }

    @Override
    public void onPhantom(BOMWalker.NodeContext ctx) {
        // PHANTOM nodes don't contribute to assembly structure — no-op
    }

    @Override
    public void flush(Connection conn) throws SQLException {
        // Write all accumulated assemblies and components
        for (PendingAssembly pa : pendingAssemblies) {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR IGNORE INTO element_assemblies
                (assembly_guid, assembly_type, ifc_class, name, total_width, total_depth, total_height, storey)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
                ps.setString(1, pa.assemblyGuid());
                ps.setString(2, pa.assemblyType());
                ps.setString(3, pa.ifcClass());
                ps.setString(4, pa.name());
                ps.setDouble(5, pa.width());
                ps.setDouble(6, pa.depth());
                ps.setDouble(7, pa.height());
                ps.setString(8, pa.storey());
                ps.execute();
                assembliesWritten++;
            }
        }

        for (PendingComponent pc : pendingComponents) {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR IGNORE INTO assembly_components
                (assembly_guid, component_guid, role, local_x, local_y, local_z, sequence)
                VALUES (?, ?, ?, 0, 0, 0, ?)
                """)) {
                ps.setString(1, pc.assemblyGuid());
                ps.setString(2, pc.componentGuid());
                ps.setString(3, pc.role());
                ps.setInt(4, pc.sequence());
                ps.execute();
                componentsWritten++;
            }
        }

        pendingAssemblies.clear();
        pendingComponents.clear();
        System.out.printf("[AssemblyStructureVisitor] flush: %d assemblies, %d components%n",
            assembliesWritten, componentsWritten);
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public int getAssembliesWritten() { return assembliesWritten; }
    public int getComponentsWritten() { return componentsWritten; }

    /** Reset stats + pending state (for reuse across multiple buildings). */
    public void reset() {
        stack.clear();
        pendingAssemblies.clear();
        pendingComponents.clear();
        assembliesWritten = 0;
        componentsWritten = 0;
    }

    // ── Internal element matching ─────────────────────────────────────────────

    private void queryElements(AssemblyContext ac) throws SQLException {
        // Collect distinct IFC classes from child rules
        Set<String> ifcClasses = new LinkedHashSet<>();
        for (ChildRule cr : ac.childRules) {
            if (cr.ifcClass() != null) ifcClasses.add(cr.ifcClass());
        }
        if (ifcClasses.isEmpty()) return;

        String classFilter = String.join("','", ifcClasses);
        try (Statement stmt = outputConn.createStatement();
             ResultSet rs = stmt.executeQuery(String.format("""
                 SELECT m.guid, m.ifc_class, m.element_type, m.element_name, m.storey,
                        COALESCE(s.name, '') as room,
                        COALESCE(r.minX,0) as minX, COALESCE(r.maxX,0) as maxX,
                        COALESCE(r.minY,0) as minY, COALESCE(r.maxY,0) as maxY,
                        COALESCE(r.minZ,0) as minZ, COALESCE(r.maxZ,0) as maxZ
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
                if (matchesAnyRule(elem, ac.childRules)) {
                    ac.elements.add(elem);
                }
            }
        }
    }

    private boolean matchesAnyRule(ElementInfo elem, List<ChildRule> rules) {
        for (ChildRule cr : rules) {
            if (matchesRule(elem, cr)) return true;
        }
        return false;
    }

    private boolean matchesRule(ElementInfo elem, ChildRule cr) {
        if (!elem.ifcClass().equals(cr.ifcClass())) return false;

        if (cr.elementType() != null && !cr.elementType().isBlank()) {
            if (elem.elementType() == null || !elem.elementType().equals(cr.elementType()))
                return false;
        }

        if (cr.namePattern() != null && !cr.namePattern().isBlank()) {
            if (elem.elementName() == null) return false;
            String regex = cr.namePattern().replace("%", ".*");
            if (!elem.elementName().matches("(?i)" + regex)) return false;
        }

        return true;
    }

    private String findRole(ElementInfo elem, List<ChildRule> rules) {
        for (ChildRule cr : rules) {
            if (matchesRule(elem, cr)) return cr.role();
        }
        return "COMPONENT";
    }

    // ── Grouping ──────────────────────────────────────────────────────────────

    private Map<String, List<ElementInfo>> groupElements(AssemblyContext ac) {
        Map<String, List<ElementInfo>> groups = new LinkedHashMap<>();
        for (ElementInfo elem : ac.elements) {
            String groupKey = switch (ac.groupBy) {
                case "STOREY"       -> elem.storey() != null ? elem.storey() : "UNKNOWN";
                case "ROOM"         -> !elem.room().isEmpty() ? elem.room() : elem.storey();
                case "ELEMENT_NAME" -> extractBaseName(elem);
                case "PROXIMITY"    -> elem.storey() != null ? elem.storey() : "UNKNOWN";
                default             -> "DEFAULT";
            };
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(elem);
        }
        return groups;
    }

    private String extractBaseName(ElementInfo elem) {
        String guid = elem.guid();
        if (guid.contains("STAIR_")) {
            int idx = guid.indexOf("STAIR_");
            String[] parts = guid.substring(idx).split("_");
            if (parts.length >= 2) return parts[0] + "_" + parts[1];
        }
        if (guid.startsWith("DOOR_") || guid.startsWith("WINDOW_")) {
            String[] parts = guid.split("_");
            if (parts.length >= 2) return parts[1];
        }
        return elem.storey() != null ? elem.storey() : "UNKNOWN";
    }

    // ── Pending write scheduling ──────────────────────────────────────────────

    private void scheduleAssembly(String assemblyGuid, AssemblyContext ac, String groupKey,
                                   List<ElementInfo> elems) {
        // Compute AABB
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (ElementInfo e : elems) {
            if (e.minX() != 0 || e.maxX() != 0) {
                minX = Math.min(minX, e.minX()); maxX = Math.max(maxX, e.maxX());
                minY = Math.min(minY, e.minY()); maxY = Math.max(maxY, e.maxY());
                minZ = Math.min(minZ, e.minZ()); maxZ = Math.max(maxZ, e.maxZ());
            }
        }
        double w = minX == Double.MAX_VALUE ? 0 : maxX - minX;
        double d = minX == Double.MAX_VALUE ? 0 : maxY - minY;
        double h = minX == Double.MAX_VALUE ? 0 : maxZ - minZ;

        pendingAssemblies.add(new PendingAssembly(
            assemblyGuid, ac.bomId, ac.targetIfcClass,
            ac.bomName + " - " + groupKey, w, d, h, groupKey));

        int seq = 1;
        for (ElementInfo elem : elems) {
            String role = findRole(elem, ac.childRules);
            pendingComponents.add(new PendingComponent(assemblyGuid, elem.guid(), role, seq++));
        }
    }

    private static String sanitize(String key) {
        return key.toUpperCase().replace(" ", "_").replace("-", "_");
    }
}
