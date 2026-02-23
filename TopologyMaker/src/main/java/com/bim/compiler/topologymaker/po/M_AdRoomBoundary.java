package com.bim.compiler.topologymaker.po;

import com.bim.compiler.topologymaker.RoomCell;

import java.sql.Connection;

/**
 * Model + business logic layer for {@code ad_room_boundary}.
 *
 * <p>Enforces the THREE-TABLE AUTHORITY rule at the object level:
 * {@code coordinate_frame} must always be {@code DERIVED_MM} for rows written
 * by TopologyMaker. {@link #beforeSave(boolean)} rejects any other value.
 *
 * <p>Adds:
 * <ul>
 *   <li>{@link #fromCell(Connection, String, String, RoomCell)} — factory for room cells</li>
 *   <li>{@link #beforeSave(boolean)} — DERIVED_MM guard + dimension sanity</li>
 *   <li>{@link #areaMm2()} — floor area helper</li>
 * </ul>
 */
public class M_AdRoomBoundary extends X_AdRoomBoundary {

    /** Forced coordinate frame — TopologyMaker only writes DERIVED_MM rows. */
    private static final String COORDINATE_FRAME = "DERIVED_MM";

    private static final String STOREY_LABEL = "Ground Floor";

    public M_AdRoomBoundary(Connection conn) { super(conn); }

    /**
     * Factory: create an unsaved boundary from a RoomCell.
     *
     * <p>Sets all required columns including the enforced {@code coordinate_frame}.
     * The UNIQUE(building_type, storey, room_name) constraint is handled by
     * BasePO's INSERT OR IGNORE — duplicate cells are silently skipped.
     *
     * @param conn         Open connection (caller manages lifecycle)
     * @param buildingType Value for building_type column (= orderId)
     * @param typologyId   Recorded in extracted_from as "TOPOLOGY_MAKER:{typologyId}"
     * @param cell         Room cell from GridStrategy
     * @return Unsaved M_AdRoomBoundary with all columns set
     */
    public static M_AdRoomBoundary fromCell(Connection conn,
                                            String buildingType,
                                            String typologyId,
                                            RoomCell cell) {
        M_AdRoomBoundary b = new M_AdRoomBoundary(conn);
        b.setBuildingType(buildingType);
        b.setStorey(STOREY_LABEL);
        b.setRoomName(cell.zoneName());
        b.setRoomType(cell.roomType());
        // Grid labels: mm values as strings (no abstract grid defined for strip layout)
        b.setGridMinX(String.valueOf(cell.minXMm()));
        b.setGridMaxX(String.valueOf(cell.maxXMm()));
        b.setGridMinY(String.valueOf(cell.minYMm()));
        b.setGridMaxY(String.valueOf(cell.maxYMm()));
        b.setMinXMm(cell.minXMm());
        b.setMaxXMm(cell.maxXMm());
        b.setMinYMm(cell.minYMm());
        b.setMaxYMm(cell.maxYMm());
        b.setCoordinateFrame(COORDINATE_FRAME);          // enforced here, not in caller
        b.setExtractedFrom("TOPOLOGY_MAKER:" + typologyId);
        b.setIsActive(true);
        return b;
    }

    @Override
    protected void beforeSave(boolean newRecord) {
        // THREE-TABLE AUTHORITY: coordinate_frame must be DERIVED_MM
        if (!COORDINATE_FRAME.equals(getCoordinateFrame()))
            throw new IllegalStateException(
                "M_AdRoomBoundary coordinate_frame must be DERIVED_MM, got: "
                + getCoordinateFrame());
        if (getMaxXMm() <= getMinXMm())
            throw new IllegalStateException(
                "maxXMm (" + getMaxXMm() + ") must be > minXMm (" + getMinXMm() + ")");
        if (getMaxYMm() <= getMinYMm())
            throw new IllegalStateException(
                "maxYMm (" + getMaxYMm() + ") must be > minYMm (" + getMinYMm() + ")");
    }

    /** Floor area in mm². Both dimensions in mm → result in mm². */
    public long areaMm2() {
        return (long)(getMaxXMm() - getMinXMm()) * (getMaxYMm() - getMinYMm());
    }
}
