/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.library;

import com.bim.compiler.contract.BundleWorker;
import com.bim.compiler.library.ComponentLibrary.ComponentDefinition;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase G-1 Step 3: BundleWorker adapter — calls BOMTierResolver directly.
 *
 * <p>Two-stage pipeline: BOMTierResolver.resolveForRoom() → PlacedElement.
 * No FurniturePlacer intermediary, no FurnitureType enum translation.
 *
 * <p>Identity chain:
 * {@code PlacedFurniture.role()} → {@code normalizeRole()} → {@code PlacedElement.role()}
 * → downstream: {@code e.role().toLowerCase()} → MEPWriter IFC class dispatch.
 */
public class FurnitureWorker implements BundleWorker {

    private final String bomId;
    private final ComponentLibrary library;
    private BOMTierResolver bomResolver;  // lazy-init

    public FurnitureWorker(String bomId, ComponentLibrary library) {
        this.bomId = bomId;
        this.library = library;
    }

    @Override
    public String themeId() {
        return bomId;
    }

    @Override
    public String anchorFace() {
        return "BACK";
    }

    @Override
    public List<PlacedElement> execute(RoomEnvelope room, PlacementContext ctx) {
        // Convert BundleWorker.OpeningInfo → BOMTierResolver.OpeningInfo
        List<BOMTierResolver.OpeningInfo> openings;
        if (room.openings() == null || room.openings().isEmpty()) {
            openings = List.of();
        } else {
            openings = room.openings().stream()
                .map(o -> new BOMTierResolver.OpeningInfo(o.type(), o.wall(), o.width()))
                .toList();
        }

        // Resolve directly — no FurniturePlacer intermediary
        if (bomResolver == null) bomResolver = new BOMTierResolver();
        List<BOMTierResolver.PlacedFurniture> bomResult = bomResolver.resolveForRoom(
            room.minX(), room.minY(), room.maxX(), room.maxY(),
            ctx.storeyZ(), room.roomName(), room.roomType(),
            openings, bomId, ctx.ceilingZ());

        // Convert PlacedFurniture → PlacedElement (library lookup for geometry)
        List<PlacedElement> result = new ArrayList<>(bomResult.size());
        for (var pf : bomResult) {
            if (pf.namePattern() == null) continue;

            ComponentDefinition def;
            try {
                def = library.getByName(pf.namePattern());
            } catch (SQLException e) {
                System.err.println("[FurnitureWorker] Library lookup failed for "
                    + pf.namePattern() + ": " + e.getMessage());
                continue;
            }
            if (def == null) continue;

            result.add(new PlacedElement(
                def.name(),
                "IfcFurniture",
                pf.x(), pf.y(), pf.z(),
                def.localBounds().width(),
                def.localBounds().depth(),
                def.localBounds().height(),
                pf.rotation(),
                def.geometryHash(),
                normalizeRole(pf.role()),
                bomId
            ));
        }
        return result;
    }

    /**
     * Normalize BOM child roles to downstream role strings.
     * Only genuine remaps are explicit; identity and fixture-param roles pass through.
     */
    private static String normalizeRole(String bomRole) {
        return switch (bomRole) {
            case "DESK" -> "WORKSTATION_DESK";
            case "USER_CHAIR" -> "WORKSTATION_CHAIR";
            case "MONITOR" -> "WORKSTATION_MONITOR";
            case "TABLE" -> "DINING_TABLE";
            case "CHAIR_A", "CHAIR_B", "CHAIR_C", "CHAIR_D", "CHAIR_E", "CHAIR_F",
                 "VISITOR_CHAIR_A", "VISITOR_CHAIR_B" -> "DINING_CHAIR";
            case "SIDE_TABLE_L", "SIDE_TABLE_R", "SIDE_TABLE_A", "SIDE_TABLE_B" -> "SIDE_TABLE";
            case "SOFA_B" -> "SOFA";
            case "BASE_CABINET", "BASE_CABINET_2", "BASE_CABINET_3", "BASE_CABINET_4",
                 "BASE_CABINET_5", "BASE_CABINET_6", "BASE_CABINET_7",
                 "UPPER_CABINET", "UPPER_CABINET_2", "UPPER_CABINET_3",
                 "VANITY_A", "VANITY_B" -> "CABINET";
            case "COUNTER" -> "COUNTER_TOP";
            default -> bomRole;  // pass-through: BED, SOFA, TOILET, SINK, EXHAUST_FAN, etc.
        };
    }
}
