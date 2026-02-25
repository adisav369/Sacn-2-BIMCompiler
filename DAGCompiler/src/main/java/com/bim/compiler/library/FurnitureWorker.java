package com.bim.compiler.library;

import com.bim.compiler.contract.BundleWorker;
import com.bim.compiler.library.FurniturePlacer.FurnitureInstance;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 118B: First BundleWorker adapter — wraps FurniturePlacer pipeline.
 *
 * <p>Converts between BundleWorker contracts (RoomEnvelope, PlacedElement)
 * and existing FurniturePlacer/BOMTierResolver types. Zero behavior
 * change — same placement logic, same identity chain.
 *
 * <p>Identity chain preserved:
 * {@code FurnitureInstance.type().name()} → {@code PlacedElement.role()}
 * → downstream: {@code e.role().toLowerCase()} → same strings as before.
 */
public class FurnitureWorker implements BundleWorker {

    private final String bomId;
    private final FurniturePlacer placer;

    public FurnitureWorker(String bomId, ComponentLibrary library) {
        this.bomId = bomId;
        this.placer = new FurniturePlacer(library);
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

        // Delegate to existing pipeline — Phase G-1: pass ceilingZ for fixture support
        List<FurnitureInstance> instances;
        try {
            instances = placer.placeUniversalFurniture(
                room.minX(), room.minY(), room.maxX(), room.maxY(),
                ctx.storeyZ(), room.roomName(), room.roomType(),
                openings, bomId, ctx.ceilingZ());
        } catch (SQLException e) {
            System.err.println("[FurnitureWorker] Placement failed for " + room.roomName() + ": " + e.getMessage());
            return List.of();
        }

        // Convert FurnitureInstance → PlacedElement
        List<PlacedElement> result = new ArrayList<>(instances.size());
        for (FurnitureInstance fi : instances) {
            result.add(new PlacedElement(
                fi.name(),                              // component name
                "IfcFurniture",                         // IFC class
                fi.worldPosition().x(),
                fi.worldPosition().y(),
                fi.worldPosition().z(),
                fi.localBounds().width(),
                fi.localBounds().depth(),
                fi.localBounds().height(),
                fi.rotation(),
                fi.geometryHash(),
                fi.type().name(),                       // role = FurnitureType enum name
                bomId                                   // assemblyId
            ));
        }
        return result;
    }
}
