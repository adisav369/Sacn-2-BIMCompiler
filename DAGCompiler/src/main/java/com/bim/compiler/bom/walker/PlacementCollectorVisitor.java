package com.bim.compiler.bom.walker;

import com.bim.compiler.dsl.PlacementLoader;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;
import com.bim.ormsandbox.po.MProduct;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * BOMVisitor that walks a BOM hierarchy and collects
 * {@link PlacementLoader.Placement} records from BUY leaves.
 *
 * <p>Used by both compilation paths:
 * <ul>
 *   <li><b>EN-BLOC</b> (EB_ BOMs, HelloWorld POC) — BOM lines already tacked,
 *       takes each as-is. Proves data correctness.</li>
 *   <li><b>WALK THRU</b> (WT_ BOMs, production path) — recalculates by tacking
 *       through each BOM layer (UNIT → FLOOR → SET → BUY).</li>
 * </ul>
 *
 * <p>Both produce the same result when the data stack is consistent.
 * Accumulates world coordinates through the tack convention (§3.4):
 * each level's origin + line dx/dy/dz offsets summed at BUY leaves.
 *
 * <p>IFC class resolution priority:
 * <ol>
 *   <li>line.role — authoritative for EB BOMs (role = IFC class name)</li>
 *   <li>product.ifc_class — authoritative for structured BOMs</li>
 *   <li>child_product_id starting with "Ifc" — last resort</li>
 * </ol>
 */
public class PlacementCollectorVisitor implements BOMVisitor {

    private final Connection bomConn;
    private final String buildingType;

    /** World origin for the building — computed from I_Element_Extraction at compile time.
     *  BOM.db origin is always (0,0,0); world position lives here (compile-time data). */
    private final double[] worldOrigin;

    /** Accumulated world anchors through the MAKE stack. */
    private final Deque<double[]> anchorStack = new ArrayDeque<>();

    /** Storey names inferred from FLOOR-level BOMs in the hierarchy. */
    private final Deque<String> storeyStack = new ArrayDeque<>();

    /** Collected placements from BUY leaves. */
    private final List<PlacementLoader.Placement> placements = new ArrayList<>();

    /** Count of sub-assemblies entered (onMake calls, depth > 0). */
    private int subAssemblyCount = 0;

    private int ordinalCounter = 0;

    public PlacementCollectorVisitor(Connection bomConn, String buildingType) {
        this(bomConn, buildingType, new double[]{0, 0, 0});
    }

    public PlacementCollectorVisitor(Connection bomConn, String buildingType, double[] worldOrigin) {
        this.bomConn = bomConn;
        this.buildingType = buildingType;
        this.worldOrigin = worldOrigin;
    }

    public List<PlacementLoader.Placement> getPlacements() {
        return List.copyOf(placements);
    }

    /** Number of sub-assemblies entered during the walk (onMake events at depth > 0). */
    public int getSubAssemblyCount() {
        return subAssemblyCount;
    }

    // ── BOMVisitor events ─────────────────────────────────────────

    @Override
    public void onMake(BOMWalker.NodeContext ctx) {
        if (ctx.level() >= 0) subAssemblyCount++;
        MBOMLine line = ctx.line();

        // Determine this MAKE node's BOM origin
        String childBomId = (line != null) ? line.getChildProductId() : null;
        double bomOriginX = 0, bomOriginY = 0, bomOriginZ = 0;

        if (childBomId != null) {
            // Load the child BOM to get its origin
            try {
                MBOM childBom = MBOM.get(bomConn, childBomId);
                if (childBom != null) {
                    bomOriginX = childBom.getOriginX();
                    bomOriginY = childBom.getOriginY();
                    bomOriginZ = childBom.getOriginZ();

                    // Track storey from FLOOR-level BOMs
                    if ("FLOOR".equals(childBom.getBomLevel()) && line.getRole() != null) {
                        storeyStack.push(inferStoreyName(line.getRole(), childBom));
                    }
                }
            } catch (SQLException e) {
                System.err.printf("[PlacementCollector] Failed to load BOM %s: %s%n",
                    childBomId, e.getMessage());
            }
        } else if (ctx.bom() != null) {
            // Synthetic root (walkSelf): use world origin from I_Element_Extraction
            // (compile-time data, not stored in BOM.db — BOM.db origin is always 0,0,0)
            bomOriginX = worldOrigin[0];
            bomOriginY = worldOrigin[1];
            bomOriginZ = worldOrigin[2];

            if ("FLOOR".equals(ctx.bom().getBomLevel())) {
                storeyStack.push(inferStoreyName(null, ctx.bom()));
            }
        }

        // Line offset (parent-relative, tack convention §3.4)
        double lineDx = (line != null) ? line.getDx() : 0;
        double lineDy = (line != null) ? line.getDy() : 0;
        double lineDz = (line != null) ? line.getDz() : 0;

        // New anchor = parent anchor + line offset + child BOM origin
        double[] parent = anchorStack.isEmpty() ? new double[]{0, 0, 0} : anchorStack.peek();
        double[] newAnchor = {
            parent[0] + lineDx + bomOriginX,
            parent[1] + lineDy + bomOriginY,
            parent[2] + lineDz + bomOriginZ
        };
        anchorStack.push(newAnchor);
    }

    @Override
    public void onMakeComplete(BOMWalker.NodeContext ctx) {
        if (!anchorStack.isEmpty()) {
            anchorStack.pop();
        }
        // Pop storey if this was a FLOOR-level BOM
        MBOMLine line = ctx.line();
        if (line != null && line.getChildProductId() != null) {
            try {
                MBOM childBom = MBOM.get(bomConn, line.getChildProductId());
                if (childBom != null && "FLOOR".equals(childBom.getBomLevel())) {
                    if (!storeyStack.isEmpty()) storeyStack.pop();
                }
            } catch (SQLException ignored) {
                // Best effort — storey tracking is informational
            }
        }
    }

    @Override
    public void onBuy(BOMWalker.NodeContext ctx) {
        MBOMLine line = ctx.line();
        if (line == null) return;

        double[] anchor = anchorStack.isEmpty() ? new double[]{0, 0, 0} : anchorStack.peek();

        // World center = accumulated anchor + this line's offset
        double cx = anchor[0] + line.getDx();
        double cy = anchor[1] + line.getDy();
        double cz = anchor[2] + line.getDz();

        // AABB half-extents: from allocated_*_mm on the line, or M_Product dims
        // Use Exact (double) getters — int getters truncate sub-mm REAL values
        double halfW, halfD, halfH;
        if (line.getAllocatedWidthMmExact() > 0) {
            halfW = line.getAllocatedWidthMmExact() / 2000.0;
            halfD = line.getAllocatedDepthMmExact() / 2000.0;
            halfH = line.getAllocatedHeightMmExact() / 2000.0;
        } else {
            // Fall back to M_Product intrinsic dimensions (in metres)
            MProduct product = ctx.product();
            if (product != null) {
                halfW = product.getWidth() / 2.0;
                halfD = product.getDepth() / 2.0;
                halfH = product.getHeight() / 2.0;
            } else {
                System.err.printf("[PlacementCollector] BUY leaf %s has no product and no allocated dims — skipping%n",
                    line.getChildProductId());
                return;
            }
        }

        // Resolve IFC class: line.role (EXTRACTED convention) > product.ifc_class > fallback
        String ifcClass = resolveIfcClass(line, ctx.product());

        // Storey: from line (EXTRACTED), or from FLOOR ancestor
        String storey = line.getStorey();
        if (storey == null || storey.isEmpty()) {
            storey = storeyStack.isEmpty() ? "Unknown" : storeyStack.peek();
        }

        // Material: from BOM line only (no M_Product fallback).
        // The BOM line is the instance-level authority for material.
        // EXTRACTED BOMs: backfilled from IFC extraction — matches reference.
        // STRUCTURED BOMs: backfilled from extraction or set by designer.
        // M_Product material is a catalog attribute, not an instance attribute;
        // falling back to it would introduce material_rgba not in the reference DB.
        MProduct product = ctx.product();
        String materialName = line.getMaterialName();
        String materialRgba = line.getMaterialRgba();

        // Element ref: from line, or generate from product + ordinal
        String elementRef = line.getElementRef();
        if (elementRef == null || elementRef.isEmpty()) {
            elementRef = (product != null ? product.getProductId() : line.getChildProductId())
                + ":" + (++ordinalCounter);
        }

        // Product ID
        String productId = line.getChildProductId();

        PlacementLoader.Placement p = new PlacementLoader.Placement(
            buildingType,
            storey,
            ifcClass,
            elementRef,
            line.getOrdinal() > 0 ? line.getOrdinal() : ordinalCounter,
            cx - halfW, cx + halfW,
            cy - halfD, cy + halfD,
            cz - halfH, cz + halfH,
            line.getOrientation(),
            deriveDiscipline(ifcClass),
            materialName,
            materialRgba,
            null,  // familyRef
            productId
        );
        placements.add(p);
    }

    @Override
    public void onPhantom(BOMWalker.NodeContext ctx) {
        // PHANTOM = buffer/spacer — no output element, no placement, no geometry.
        //
        // PHANTOMs exist in BOM.db to fully tile the parent AABB (packed-box
        // principle). They absorb the gap between real content (BUY children)
        // and the parent's room envelope. At compile time they are stripped —
        // like removing foam packaging when furniture is placed on the floor.
        //
        // The tack coordinate (dx/dy/dz) on the PHANTOM line IS consumed by
        // the walker's coordinate accumulation, but no output record is created.
    }

    // ── Helpers ───────────────────────────────────────────────────

    /**
     * Resolve IFC class from BOM line + product.
     *
     * <p>Priority: line.role (if IFC-prefixed, EXTRACTED convention) →
     * product.ifc_class → child_product_id (if IFC-prefixed) → "Unknown".
     */
    private static String resolveIfcClass(MBOMLine line, MProduct product) {
        // 1. line.role — authoritative for EXTRACTED BOMs (stores IFC class name)
        String role = line.getRole();
        if (role != null && role.startsWith("Ifc")) {
            return role;
        }

        // 2. product.ifc_class — authoritative for structured BOMs
        if (product != null && product.getIfcClass() != null) {
            return product.getIfcClass();
        }

        // 3. child_product_id starting with "Ifc" — last resort
        String cpid = line.getChildProductId();
        if (cpid != null && cpid.startsWith("Ifc")) {
            return cpid;
        }

        return "Unknown";
    }

    /**
     * Infer a storey display name from a FLOOR BOM's role or name.
     */
    private static String inferStoreyName(String role, MBOM floorBom) {
        if (role != null && !role.isEmpty()) {
            return switch (role) {
                case "GROUND_FLOOR" -> "Ground Floor";
                case "GROUND_SLAB" -> "Ground Floor";
                case "LEVEL_1", "L1" -> "Level 1";
                case "LEVEL_2", "L2" -> "Level 2";
                case "ROOF" -> "Roof";
                default -> role.replace('_', ' ');
            };
        }
        // Fall back to bom_category
        String cat = floorBom.getBomCategory();
        if (cat != null) {
            return switch (cat) {
                case "GF" -> "Ground Floor";
                case "L1" -> "Level 1";
                case "L2" -> "Level 2";
                case "RF" -> "Roof";
                case "SL" -> "Ground Floor";
                default -> cat;
            };
        }
        return "Unknown";
    }

    private static String deriveDiscipline(String ifcClass) {
        if (ifcClass == null) return "ARC";
        return switch (ifcClass) {
            case "IfcBeam", "IfcMember", "IfcPlate", "IfcSlab",
                 "IfcStairFlight", "IfcRailing" -> "STR";
            case "IfcFlowController", "IfcFlowFitting",
                 "IfcFlowSegment", "IfcFlowTerminal" -> "MEP";
            default -> "ARC";
        };
    }
}
