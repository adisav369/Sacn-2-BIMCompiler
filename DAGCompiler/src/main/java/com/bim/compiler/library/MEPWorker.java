/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.library;

import com.bim.compiler.contract.BundleWorker;
import com.bim.compiler.dsl.BOMRuleAD;
import com.bim.compiler.dsl.MEPBOMResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Phase B4: BundleWorker adapter for MEP ceiling gap-fill.
 *
 * <p>Wraps MEPBOMResolver as a BundleWorker. Resolves ceiling MEP requirements
 * (sprinklers, lights, diffusers, fans) from ad_space_type_mep_bom and returns
 * positioned PlacedElements. The caller applies gap-fill filtering (skip if
 * room already has that MEP type).
 *
 * <p>Same placement logic as StoreyCompiler.mepBomGapFill — zone centers with
 * quadrant offsets to avoid overlap.
 */
public class MEPWorker implements BundleWorker {

    private static final Set<String> EXEMPT_KEYWORDS = Set.of(
        "shaft", "riser", "void", "tnb", "pump", "genset", "tank", "machine");

    private static final double BIG_ROOM_AREA = 80.0;
    private static final double BIG_ROOM_MIN_DIM = 3.0;
    private static final double MIN_ROOM_AREA = 4.0;

    private final MEPBOMResolver resolver;
    private final String lightHash;
    private final double lightW, lightD, lightH;

    public MEPWorker(ComponentLibrary library) {
        this.resolver = new MEPBOMResolver();

        // Resolve light geometry once
        String hash = null;
        double w = 0.6, d = 0.6, h = 0.1;
        try {
            var lightDef = library.getComponentWithFallback("14W_Surface_LED", "guarantee");
            if (lightDef == null) lightDef = library.getComponentWithFallback("28W_Surface_LED", "guarantee");
            if (lightDef != null) {
                hash = lightDef.geometryHash();
                w = lightDef.localBounds().width();
                d = lightDef.localBounds().depth();
                h = lightDef.localBounds().height();
            }
        } catch (Exception ex) { /* best-effort library lookup — fallback to defaults */ }
        this.lightHash = hash;
        this.lightW = w;
        this.lightD = d;
        this.lightH = h;
    }

    @Override
    public String themeId() {
        return "MEP_CEILING_SET";
    }

    @Override
    public String anchorFace() {
        return "TOP";
    }

    @Override
    public List<PlacedElement> execute(RoomEnvelope room, PlacementContext ctx) {
        // Skip exempt rooms
        String nameLower = room.roomName().toLowerCase();
        for (String kw : EXEMPT_KEYWORDS) {
            if (nameLower.contains(kw)) return List.of();
        }

        double roomW = room.width();
        double roomD = room.depth();
        double area = roomW * roomD;
        if (area < MIN_ROOM_AREA) return List.of();

        // Resolve MEP requirements from BOM table
        var mepSets = resolver.resolveForRoom(room.roomType(), room.roomName(), area, roomW, roomD);
        if (mepSets.isEmpty()) return List.of();

        // Compute sprinklerZ from BOM metadata
        double storeyHeight = ctx.ceilingZ() - ctx.storeyZ() + 0.05;
        var headParams = BOMRuleAD.loadPlacementParams("FP_PIPE_ASSEMBLY", "HEAD");
        double sprinklerZ = headParams.resolveZ(ctx.storeyZ(), storeyHeight, ctx.slabThickness());

        // Light Z: just below slab
        double lightZ = (ctx.storeyZ() + storeyHeight) - 0.1;

        // Zone layout (big rooms get 2 zones)
        boolean bigRoom = area >= BIG_ROOM_AREA && roomW >= BIG_ROOM_MIN_DIM && roomD >= BIG_ROOM_MIN_DIM;
        int setCount = bigRoom ? 2 : 1;

        List<PlacedElement> result = new ArrayList<>();
        for (int setIdx = 0; setIdx < setCount; setIdx++) {
            // Calculate zone center
            double cx, cy;
            if (setCount == 1) {
                cx = (room.minX() + room.maxX()) / 2;
                cy = (room.minY() + room.maxY()) / 2;
            } else {
                if (roomD >= roomW) {
                    cx = (room.minX() + room.maxX()) / 2;
                    cy = room.minY() + roomD * (setIdx == 0 ? 0.25 : 0.75);
                } else {
                    cx = room.minX() + roomW * (setIdx == 0 ? 0.25 : 0.75);
                    cy = (room.minY() + room.maxY()) / 2;
                }
            }
            String suffix = "_" + (setIdx + 1);

            // Offset each MEP type to different quadrant to avoid overlap
            double ox = Math.min(0.6, roomW * 0.15);
            double oy = Math.min(0.6, roomD * 0.15);

            for (var mep : mepSets) {
                switch (mep.productId()) {
                    case "SPRINKLER" -> result.add(new PlacedElement(
                        room.roomName() + "_bom_sprinkler" + suffix, "IfcFlowTerminal",
                        cx - ox, cy + oy, sprinklerZ,
                        0, 0, 0, 0, null, "SPRINKLER", "MEP_CEILING_SET"));
                    case "LIGHT" -> result.add(new PlacedElement(
                        room.roomName() + "_bom_light" + suffix, "IfcFlowTerminal",
                        cx + ox, cy + oy, lightZ,
                        lightW, lightD, lightH, 0, lightHash, "LIGHT", "MEP_CEILING_SET"));
                    case "SUPPLY_DIFFUSER" -> result.add(new PlacedElement(
                        room.roomName() + "_bom_supply" + suffix, "IfcFlowTerminal",
                        cx + ox, cy - oy, ctx.ceilingZ(),
                        100, 0, 0, 0, null, "SUPPLY_DIFFUSER", "MEP_CEILING_SET"));
                    case "CEILING_FAN" -> result.add(new PlacedElement(
                        room.roomName() + "_bom_fan" + suffix, "IfcFlowTerminal",
                        cx - ox, cy - oy, ctx.ceilingZ(),
                        50, 0, 0, 0, null, "CEILING_FAN", "MEP_CEILING_SET"));
                }
            }
        }
        return result;
    }
}
