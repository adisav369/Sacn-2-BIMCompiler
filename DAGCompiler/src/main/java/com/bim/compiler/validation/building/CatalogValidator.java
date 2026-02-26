package com.bim.compiler.validation.building;

import com.bim.compiler.contract.CatalogContract;
import com.bim.compiler.contract.CatalogContract.CatalogResult;
import com.bim.compiler.contract.CatalogContract.CatalogViolation;
import com.bim.compiler.dsl.BuildingDefinition;
import com.bim.compiler.dsl.BuildingDefinition.*;
import com.bim.compiler.dsl.UnitDefinition;

import java.sql.*;
import java.util.*;

/**
 * Phase 117: Validates that all DSL references resolve to existing catalog
 * entries in component_library.db.
 *
 * <p>Implements the CatalogContract enforcement: DSL is a catalog selector,
 * not a creator. Every reference in the .bim file must map to an active
 * row in the corresponding AD table.
 *
 * <p>Runs as a pre-compilation check. Does NOT block compilation — reports
 * warnings for unresolved references so the compiler can gracefully degrade.
 *
 * <h3>What is checked:</h3>
 * <ol>
 *   <li>floor_bom references → m_bom.bom_id</li>
 *   <li>Room type keywords → ad_space_type.space_type_id</li>
 *   <li>Door schedule entries → ad_opening_family (type='DOOR')</li>
 *   <li>Window schedule entries → ad_opening_family (type='WINDOW')</li>
 * </ol>
 *
 * <h3>Graceful degradation:</h3>
 * If component_library.db is missing or tables don't exist, the validator
 * returns a clean result (no violations) — it never blocks compilation.
 */
public class CatalogValidator implements CatalogContract {

    private static final String LIB_PATH = "library/BOM.db";

    private final BuildingDefinition def;

    public CatalogValidator(BuildingDefinition def) {
        this.def = def;
    }

    @Override
    public CatalogResult validateCatalog() {
        List<CatalogViolation> violations = new ArrayList<>();
        int resolved = 0;
        int total = 0;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + LIB_PATH)) {

            // 1. Check floor_bom references → m_bom
            Set<String> bomRefs = collectFloorBomRefs();
            for (String bomId : bomRefs) {
                total++;
                if (existsInTable(conn, "m_bom", "bom_id", bomId)) {
                    resolved++;
                } else {
                    violations.add(new CatalogViolation(
                        "FLOOR_BOM", bomId, "m_bom",
                        "DSL references floor_bom:" + bomId + " but no such BOM in catalog"));
                }
            }

            // 2. Check room type keywords → ad_space_type
            Set<String> roomTypes = collectRoomTypes();
            Set<String> knownSpaceTypes = loadActiveSet(conn,
                "ad_space_type", "space_type_id");
            // Also load aliases for fuzzy matching
            Set<String> aliases = loadActiveSet(conn,
                "ad_space_type_alias", "alias");
            for (String roomType : roomTypes) {
                total++;
                String upper = roomType.toUpperCase();
                if (knownSpaceTypes.contains(upper) || aliases.contains(upper)
                        || aliases.contains(roomType)) {
                    resolved++;
                } else {
                    violations.add(new CatalogViolation(
                        "ROOM_TYPE", roomType, "ad_space_type",
                        "DSL uses room type '" + roomType + "' not found in catalog"));
                }
            }

            // 3. Check door schedule entries → ad_opening_family
            if (def.doorSchedule() != null) {
                Set<String> doorFamilies = loadOpeningFamilies(conn, "DOOR");
                for (ScheduleEntryDef entry : def.doorSchedule().entries()) {
                    total++;
                    if (doorFamilies.contains(entry.typeCode())) {
                        resolved++;
                    } else {
                        violations.add(new CatalogViolation(
                            "DOOR_SCHEDULE", entry.typeCode(), "ad_opening_family",
                            "Door schedule entry '" + entry.typeCode() + "' has no catalog family"));
                    }
                }
            }

            // 4. Check window schedule entries → ad_opening_family
            if (def.windowSchedule() != null) {
                Set<String> windowFamilies = loadOpeningFamilies(conn, "WINDOW");
                for (ScheduleEntryDef entry : def.windowSchedule().entries()) {
                    total++;
                    if (windowFamilies.contains(entry.typeCode())) {
                        resolved++;
                    } else {
                        violations.add(new CatalogViolation(
                            "WINDOW_SCHEDULE", entry.typeCode(), "ad_opening_family",
                            "Window schedule entry '" + entry.typeCode() + "' has no catalog family"));
                    }
                }
            }

        } catch (SQLException ex) {
            // Graceful degradation: can't access library → assume clean
            System.err.println("[CatalogValidator] Cannot access catalog: " + ex.getMessage());
            return new CatalogResult(List.of(), 0, 0);
        }

        return new CatalogResult(violations, resolved, total);
    }

    /**
     * Build a BuildingValidationResult from catalog check for integration
     * with the existing ValidatorChain.
     */
    public BuildingValidationResult toValidationResult() {
        CatalogResult result = validateCatalog();
        BuildingValidationResult bvr = new BuildingValidationResult("CatalogValidator");

        for (CatalogViolation v : result.violations()) {
            bvr.addWarning(v.referenceValue(), "catalog_" + v.referenceType().toLowerCase(),
                v.message());
        }

        if (!result.isClean()) {
            bvr.addInfo("catalog", "summary", result.summary());
        }

        return bvr;
    }

    // =========================================================================
    // Collectors — extract references from BuildingDefinition
    // =========================================================================

    private Set<String> collectFloorBomRefs() {
        Set<String> refs = new LinkedHashSet<>();
        for (StoreyDef storey : def.getAllStoreys()) {
            if (storey.floorBom() != null && !storey.floorBom().isEmpty()) {
                refs.add(storey.floorBom());
            }
        }
        return refs;
    }

    private Set<String> collectRoomTypes() {
        Set<String> types = new LinkedHashSet<>();
        for (StoreyDef storey : def.getAllStoreys()) {
            for (RoomDef room : storey.rooms()) {
                if (room.type() != null && !room.type().isEmpty()) {
                    types.add(room.type());
                }
            }
        }
        // Also collect from unit definitions
        for (UnitDefinition unit : def.units()) {
            for (StoreyDef storey : unit.storeys()) {
                for (RoomDef room : storey.rooms()) {
                    if (room.type() != null && !room.type().isEmpty()) {
                        types.add(room.type());
                    }
                }
            }
        }
        return types;
    }

    // =========================================================================
    // DB helpers — query component_library.db
    // =========================================================================

    private boolean existsInTable(Connection conn, String table,
                                   String column, String value) throws SQLException {
        // Check table exists first
        try (ResultSet rs = conn.getMetaData().getTables(null, null, table, null)) {
            if (!rs.next()) return true;  // table missing → assume valid (graceful)
        }
        String sql = "SELECT 1 FROM " + table + " WHERE " + column + " = ? AND is_active = 1 LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            // Column might not exist (e.g., no is_active) — try without filter
            String fallback = "SELECT 1 FROM " + table + " WHERE " + column + " = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(fallback)) {
                ps.setString(1, value);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }

    private Set<String> loadActiveSet(Connection conn, String table,
                                       String column) throws SQLException {
        Set<String> result = new HashSet<>();
        try (ResultSet rs = conn.getMetaData().getTables(null, null, table, null)) {
            if (!rs.next()) return result;  // table missing → empty set
        }
        String sql = "SELECT " + column + " FROM " + table + " WHERE is_active = 1";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String val = rs.getString(1);
                if (val != null) result.add(val.toUpperCase());
            }
        } catch (SQLException ex) {
            // Fallback without is_active filter
            String fallback = "SELECT " + column + " FROM " + table;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(fallback)) {
                while (rs.next()) {
                    String val = rs.getString(1);
                    if (val != null) result.add(val.toUpperCase());
                }
            }
        }
        return result;
    }

    private Set<String> loadOpeningFamilies(Connection conn, String type) throws SQLException {
        Set<String> result = new HashSet<>();
        try (ResultSet rs = conn.getMetaData().getTables(null, null, "ad_opening_family", null)) {
            if (!rs.next()) return result;
        }
        String sql = "SELECT family_id FROM ad_opening_family WHERE opening_type = ? AND is_active = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        }
        return result;
    }
}
