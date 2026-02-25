package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Model (DAO) layer for {@code wm_empty_storage_line}.
 *
 * <p>Provides factory and query methods for the WMS Empty Storage instance model.
 * Each row represents the current fill state of one M_Locator within a room —
 * the spatial equivalent of an iDempiere WMS bin availability record.
 *
 * <p>Usage pattern during BOM resolution:
 * <pre>
 *   // 1. Create a Draft line when PhantomLayout starts
 *   M_WmEmptyStorageLine line = M_WmEmptyStorageLine.createDraft(
 *       conn, "Ifc4_SampleHouse", "Ground", "LIVING_ROOM", "NORTH_WALL",
 *       6500.0, 1620.0, 500.0, 0.0);
 *   line.save();
 *
 *   // 2. Update as children are placed (GPD advances)
 *   line.setFilledMm(5200.0);
 *   line.setRemainingMm(1300.0);
 *   line.setNextAnchorXMm(4750.0);
 *   line.save();
 *
 *   // 3. Complete when BOM expansion done
 *   M_WmEmptyStorageLine.complete(conn, line.getLineId());
 * </pre>
 *
 * <p>Query pattern for contractor / DSL availability check:
 * <pre>
 *   List&lt;M_WmEmptyStorageLine&gt; available =
 *       M_WmEmptyStorageLine.getAvailableLocators(conn, "Ifc4_SampleHouse", "LIVING_ROOM");
 *   // → shows all CO lines with remaining_mm > 0 for that room
 * </pre>
 */
@Deprecated(since = "Phase 3 — CO_EmptySpace", forRemoval = true)
public class M_WmEmptyStorageLine extends X_WmEmptyStorageLine {

    public M_WmEmptyStorageLine(Connection conn) { super(conn); }

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Create a new Draft line for a Locator starting BOM resolution.
     * Sets DocStatus=DR, filled=0, remaining=capacity.
     * nextAnchor seeded from the Locator's GPD origin (mm).
     */
    public static M_WmEmptyStorageLine createDraft(
            Connection conn,
            String buildingType, String storey, String roomName, String locatorRef,
            double capacityMm,
            double nextAnchorXMm, double nextAnchorYMm, double nextAnchorZMm)
            throws SQLException {
        M_WmEmptyStorageLine line = new M_WmEmptyStorageLine(conn);
        line.setBuildingType(buildingType);
        line.setStorey(storey);
        line.setRoomName(roomName);
        line.setLocatorRef(locatorRef);
        line.setCapacityMm(capacityMm);
        line.setFilledMm(0.0);
        line.setRemainingMm(capacityMm);
        line.setNextAnchorXMm(nextAnchorXMm);
        line.setNextAnchorYMm(nextAnchorYMm);
        line.setNextAnchorZMm(nextAnchorZMm);
        line.setDocStatus(DOCSTATUS_Draft);
        return line;
    }

    // ── DocStatus transitions ─────────────────────────────────────────────────

    /**
     * Advance GPD: record that a child of the given extent has been placed.
     * Updates filled_mm, remaining_mm, and nextAnchor in 3D (x, y, z).
     * Caller must call save() after.
     *
     * <p>For horizontal wall-parallel walks: pass unchanged z (same floor level).
     * For vertical stacking (RACK_*, bookshelf): pass currentZ + stride.
     * For ceiling-referenced elements: pass ceiling_z - stride (downward).
     */
    public void placeChild(double extentMm,
                           double newAnchorXMm, double newAnchorYMm, double newAnchorZMm) {
        setFilledMm(getFilledMm() + extentMm);
        setRemainingMm(getCapacityMm() - getFilledMm());
        setNextAnchorXMm(newAnchorXMm);
        setNextAnchorYMm(newAnchorYMm);
        setNextAnchorZMm(newAnchorZMm);
        setUpdated(java.time.Instant.now().toString());
    }

    /**
     * Transition DR → CO: BOM resolution complete for this Locator.
     * Caller must call save() after.
     */
    public void complete() {
        setDocStatus(DOCSTATUS_Complete);
        setUpdated(java.time.Instant.now().toString());
    }

    /**
     * Transition any status → VO: invalidate this record (recompile triggered).
     * Caller must call save() after.
     */
    public void voidLine() {
        setDocStatus(DOCSTATUS_Void);
        setUpdated(java.time.Instant.now().toString());
    }

    // ── Query methods ─────────────────────────────────────────────────────────

    /**
     * All Complete (CO) lines for a building — the full EmptyStorage overlay.
     * Used by contractor / DSL availability checks.
     */
    public static List<M_WmEmptyStorageLine> getForBuilding(
            Connection conn, String buildingType) throws SQLException {
        return new ModelQuery<>(conn, M_WmEmptyStorageLine::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .andWhere(COLUMNNAME_doc_status + " = ?", DOCSTATUS_Complete)
            .orderBy(COLUMNNAME_storey + ", " + COLUMNNAME_room_name
                     + ", " + COLUMNNAME_locator_ref)
            .list();
    }

    /**
     * Available (CO, remaining > 0) Locators for a specific room.
     * "Where can the next item go in this room?"
     */
    public static List<M_WmEmptyStorageLine> getAvailableLocators(
            Connection conn, String buildingType, String roomName) throws SQLException {
        return new ModelQuery<>(conn, M_WmEmptyStorageLine::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .andWhere(COLUMNNAME_room_name + " = ?", roomName)
            .andWhere(COLUMNNAME_doc_status + " = ?", DOCSTATUS_Complete)
            .andWhere(COLUMNNAME_remaining_mm + " > ?", 0.0)
            .orderBy(COLUMNNAME_remaining_mm + " DESC")
            .list();
    }

    /**
     * Specific Locator state for a room — used during BOM resolution to resume
     * an in-progress PhantomLayout or check final state.
     */
    public static Optional<M_WmEmptyStorageLine> getLocator(
            Connection conn, String buildingType, String roomName,
            String locatorRef, String docStatus) throws SQLException {
        return new ModelQuery<>(conn, M_WmEmptyStorageLine::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .andWhere(COLUMNNAME_room_name + " = ?", roomName)
            .andWhere(COLUMNNAME_locator_ref + " = ?", locatorRef)
            .andWhere(COLUMNNAME_doc_status + " = ?", docStatus)
            .first();
    }

    /**
     * Void all CO lines for a building — called at start of recompilation
     * to invalidate stale EmptyStorage state before fresh BOM resolution.
     */
    public static int voidForBuilding(Connection conn, String buildingType)
            throws SQLException {
        String sql = "UPDATE " + Table_Name
            + " SET " + COLUMNNAME_doc_status + " = '" + DOCSTATUS_Void + "'"
            + ", " + COLUMNNAME_updated + " = datetime('now')"
            + " WHERE " + COLUMNNAME_building_type + " = ?"
            + " AND "   + COLUMNNAME_doc_status + " != '" + DOCSTATUS_Void + "'";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingType);
            return ps.executeUpdate();
        }
    }

    /**
     * Overflow check: any Locator with remaining_mm < 0 is a GIC violation.
     * Fixed children exceed the Locator extent — BOM does not fit this room.
     */
    public static List<M_WmEmptyStorageLine> getOverflows(
            Connection conn, String buildingType) throws SQLException {
        return new ModelQuery<>(conn, M_WmEmptyStorageLine::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .andWhere(COLUMNNAME_remaining_mm + " < ?", 0.0)
            .andWhere(COLUMNNAME_doc_status + " != ?", DOCSTATUS_Void)
            .list();
    }
}
