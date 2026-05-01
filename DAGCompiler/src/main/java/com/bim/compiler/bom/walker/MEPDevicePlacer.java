/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.bom.walker;

// Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 — Witness: W-DEVICE-PLACE
// Generative MEP device placement: reads schedule metadata, computes positions
// from room AABB + offsets. The code never interprets device names.

import com.bim.compiler.dsl.PlacementLoader;
import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * PLACE_DEVICE verb — generative MEP device placement from schedule metadata.
 *
 * <p>Given a room AABB and its space type, reads the schedule
 * ({@code ad_space_type_mep_bom} + {@code ad_placement_offset}) via
 * {@link SpaceScheduleDAO} and computes device positions using pure maths.
 *
 * <p>The code sees only abstract tokens: SPACE → CAPABILITY → SCHEDULE → OFFSET → POSITION.
 * "SINK", "TOILET" etc. are metadata strings — the placer never interprets them.
 *
 * <p>GEO white-box logging at every step — any reviewer reads the log and knows
 * exactly why each device is at its position.
 *
 * @see SpaceScheduleDAO
 * @see PlacementCollectorVisitor
 */
public class MEPDevicePlacer {

    /** Result of placing one device instance. */
    public record DevicePlacement(
        String deviceId,        // abstract product token from schedule
        String placementRule,   // rule that determined position
        String anchorEnd,       // infrastructure endpoint (RISER, STACK, PANEL)
        double[] position,      // [x, y, z] in metres (world coordinates)
        String hostSurface,     // WALL, FLOOR, CEILING
        String standard         // building code provenance
    ) {}

    /**
     * Place all scheduled MEP devices in a room.
     *
     * <p>Flow: read schedule → for each entry, resolve qty → compute position(s)
     * → emit GEO log → return placements.
     *
     * @param erpConn    ERP.db connection (for schedule + offset tables)
     * @param spaceType  abstract space type (e.g. "BATHROOM")
     * @param roomAabb   room AABB [minX, maxX, minY, maxY, minZ, maxZ] in metres
     * @param orderQty   order coverage level (99=standard, 0=max, N=budget cap)
     * @param roomBomId  BOM ID of the room (for logging)
     * @return placed devices; empty list if no schedule or no capability
     */
    public static List<DevicePlacement> placeDevices(Connection erpConn,
                                                      String spaceType,
                                                      double[] roomAabb,
                                                      int orderQty,
                                                      String roomBomId)
            throws SQLException {

        List<SpaceScheduleDAO.ScheduleEntry> schedule =
            SpaceScheduleDAO.getSchedule(erpConn, spaceType);

        if (schedule.isEmpty()) {
            BIMLogger.fine("COMPILE", "PLACE_DEVICE {} — no schedule for space_type={}",
                roomBomId, spaceType);
            return List.of();
        }

        // Room floor area for per_area rules (width × depth in m²)
        double roomWidth  = roomAabb[1] - roomAabb[0];
        double roomDepth  = roomAabb[3] - roomAabb[2];
        double roomAreaM2 = roomWidth * roomDepth;

        BIMLogger.fine("GENERATIVE",
            "DEVICE_ENTER {} type={} aabb=[{:.3f},{:.3f},{:.3f},{:.3f},{:.3f},{:.3f}] area={:.1f}m² schedule={} orderQty={}",
            roomBomId, spaceType,
            roomAabb[0], roomAabb[1], roomAabb[2], roomAabb[3], roomAabb[4], roomAabb[5],
            roomAreaM2, schedule.size(), orderQty);

        List<DevicePlacement> result = new ArrayList<>();

        for (SpaceScheduleDAO.ScheduleEntry entry : schedule) {
            int qty = SpaceScheduleDAO.resolveQty(orderQty, entry, roomAreaM2);
            if (qty <= 0) continue;

            double[] basePos = SpaceScheduleDAO.computePosition(roomAabb, entry);

            // Log the abstract chain — WHY the device is at this position
            BIMLogger.geo("DEVICE_PLACE",
                "  device={} rule={} offset=({:.3f},{:.3f},{:.3f}) ref=({},{},{}) "
                + "→ position=({:.3f},{:.3f},{:.3f}) qty={} anchor={} source=ad_placement_offset.{}",
                entry.deviceId(), entry.placementRule(),
                entry.edgeX(), entry.edgeY(), entry.zOffset(),
                entry.xRef(), entry.yRef(), entry.zRule(),
                basePos[0], basePos[1], basePos[2],
                qty, entry.anchorEnd(), entry.placementRule());

            if (entry.standard() != null && !entry.standard().isEmpty()) {
                BIMLogger.geo("DEVICE_PLACE", "    standard={}", entry.standard());
            }

            // Place qty instances — for qty>1, space along the dominant axis
            for (int i = 0; i < qty; i++) {
                double[] pos;
                if (qty == 1) {
                    pos = basePos;
                } else {
                    // Multiple instances: distribute evenly along the room's longer axis
                    pos = distributeInstance(roomAabb, entry, basePos, i, qty);
                    BIMLogger.geo("DEVICE_PLACE",
                        "    instance {}/{} → ({:.3f},{:.3f},{:.3f})",
                        i + 1, qty, pos[0], pos[1], pos[2]);
                }

                result.add(new DevicePlacement(
                    entry.deviceId(),
                    entry.placementRule(),
                    entry.anchorEnd(),
                    pos,
                    entry.hostSurface(),
                    entry.standard()
                ));
            }
        }

        BIMLogger.geo("DEVICE_PLACE", "EXIT  space={} → {} devices placed", roomBomId, result.size());
        return result;
    }

    /**
     * Resolve space type from BOM product category.
     *
     * <p>M_Product_Category.Value maps directly to ad_space_type.Value
     * for room-level categories (BATHROOM, BEDROOM, KITCHEN, etc.).
     *
     * @param erpConn ERP.db connection
     * @param categoryValue product category Value (e.g. "BATHROOM")
     * @return space type Value if it exists and has a schedule, null otherwise
     */
    public static String resolveSpaceType(Connection erpConn, String categoryValue)
            throws SQLException {
        if (categoryValue == null || categoryValue.isEmpty()) return null;

        // Check if this category maps to an active space type
        String sql = "SELECT Value FROM ad_space_type WHERE Value = ? AND is_active = 1";
        try (PreparedStatement ps = erpConn.prepareStatement(sql)) {
            ps.setString(1, categoryValue);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    // ── Multi-instance distribution ─────────────────────────────────────────

    /**
     * Distribute multiple instances of the same device evenly along the room's
     * dominant axis, keeping the z and edge offset from the base position.
     *
     * <p>For WALL_SPACED outlets (qty=3 in a 4m room): positions at 1.0, 2.0, 3.0m
     * along the longer axis, maintaining the wall offset on the other axis.
     */
    private static double[] distributeInstance(double[] roomAabb,
                                               SpaceScheduleDAO.ScheduleEntry entry,
                                               double[] basePos,
                                               int index, int total) {
        double roomWidth  = roomAabb[1] - roomAabb[0];
        double roomDepth  = roomAabb[3] - roomAabb[2];

        // Distribute along the longer axis
        boolean alongX = roomWidth >= roomDepth;
        double axisMin = alongX ? roomAabb[0] : roomAabb[2];
        double axisLen = alongX ? roomWidth : roomDepth;

        // Evenly spaced: skip edge offset, divide interior
        double spacing = axisLen / (total + 1);
        double axisPos = axisMin + spacing * (index + 1);

        if (alongX) {
            return new double[]{axisPos, basePos[1], basePos[2]};
        } else {
            return new double[]{basePos[0], axisPos, basePos[2]};
        }
    }

    // ── Gap Listing ───────────────────────────────────────────────────────

    /** One gap — a scheduled device with no matching product. */
    public record DeviceGap(
        String spaceType,
        String deviceId,
        String placementRule,
        String anchorEnd,
        int qtyNormal,
        String suggestedInsert   // actionable SQL INSERT
    ) {}

    /**
     * Analyse the schedule for a space type and report gaps — scheduled devices
     * that have no matching M_Product in the component library.
     *
     * <p>Emits actionable INSERT statements so modellers can fill the gaps:
     * run this, paste the INSERTs, re-run.
     *
     * @param erpConn ERP.db connection (for schedule)
     * @param compConn component_library.db or ERP.db connection (for M_Product check)
     * @param spaceType space type to check
     * @return list of gaps with actionable INSERTs; empty list if fully satisfied
     */
    public static List<DeviceGap> analyseGaps(Connection erpConn, Connection compConn,
                                               String spaceType) throws SQLException {
        List<SpaceScheduleDAO.ScheduleEntry> schedule =
            SpaceScheduleDAO.getSchedule(erpConn, spaceType);

        List<DeviceGap> gaps = new ArrayList<>();
        for (SpaceScheduleDAO.ScheduleEntry entry : schedule) {
            // Check if a product exists for this device
            boolean exists;
            try (PreparedStatement ps = compConn.prepareStatement(
                    "SELECT 1 FROM M_Product WHERE product_id = ? OR Value = ?")) {
                ps.setString(1, entry.deviceId());
                ps.setString(2, entry.deviceId());
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (!exists) {
                String insert = String.format(
                    "INSERT INTO M_Product (product_id, Value, Name, product_type, is_active) "
                    + "VALUES ('%s', '%s', '%s device', 'FIXTURE', 1);",
                    entry.deviceId(), entry.deviceId(), entry.deviceId());

                gaps.add(new DeviceGap(
                    spaceType,
                    entry.deviceId(),
                    entry.placementRule(),
                    entry.anchorEnd(),
                    entry.qtyNormal(),
                    insert
                ));

                BIMLogger.warn("COMPILE",
                    "GAP: {} scheduled for {} (rule={}, anchor={}, qty={}) — no M_Product found",
                    entry.deviceId(), spaceType, entry.placementRule(),
                    entry.anchorEnd(), entry.qtyNormal());
            }
        }

        if (gaps.isEmpty()) {
            BIMLogger.fine("COMPILE", "GAP_ANALYSIS {}: all {} devices SATISFIED",
                spaceType, schedule.size());
        } else {
            BIMLogger.info("COMPILE", "GAP_ANALYSIS {}: {}/{} devices SATISFIED, {} GAPS",
                spaceType, schedule.size() - gaps.size(), schedule.size(), gaps.size());
            for (DeviceGap gap : gaps) {
                System.out.printf("[GAP] %s.%s rule=%s anchor=%s qty=%d%n  → %s%n",
                    gap.spaceType(), gap.deviceId(), gap.placementRule(),
                    gap.anchorEnd(), gap.qtyNormal(), gap.suggestedInsert());
            }
        }

        return gaps;
    }

    private MEPDevicePlacer() {} // static utility
}
