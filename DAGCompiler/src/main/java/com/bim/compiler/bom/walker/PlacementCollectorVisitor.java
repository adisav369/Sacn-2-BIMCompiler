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
 * {@link PlacementLoader.Placement} records from leaf nodes.
 *
 * <p>Used by both compilation paths:
 * <ul>
 *   <li><b>EN-BLOC</b> (EB_ BOMs, HelloWorld POC) — BOM lines already tacked,
 *       takes each as-is. Proves data correctness.</li>
 *   <li><b>WALK THRU</b> (WT_ BOMs, production path) — recalculates by tacking
 *       through each BOM layer (UNIT → FLOOR → SET → leaf).</li>
 * </ul>
 *
 * <p>Both produce the same result when the data stack is consistent.
 * Accumulates world coordinates through the tack convention (§3.4):
 * each level's origin + line dx/dy/dz offsets summed at leaf nodes.
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

    /** World origin for the building — read from m_bom.origin_x/y/z on the BUILDING BOM.
     *  Stored during BOM generation from extraction LBD corner. */
    private final double[] worldOrigin;

    /** Accumulated world anchors through the MAKE stack. */
    private final Deque<double[]> anchorStack = new ArrayDeque<>();

    /** Cumulative rotation (radians) through the sub-assembly stack. */
    private final Deque<Double> rotationStack = new ArrayDeque<>();

    /** Storey names inferred from FLOOR-level BOMs in the hierarchy. */
    private final Deque<String> storeyStack = new ArrayDeque<>();

    /** Unit role prefix for mirrored compositions (e.g. "A_", "B_"). Empty when not in a pair. */
    private final Deque<String> unitPrefixStack = new ArrayDeque<>();

    /** Discipline codes from SET-level BOMs (bom_category: ARC, STR, FP, ...).
     *  Overrides deriveDiscipline() which loses extraction context (e.g. IfcSlab → STR always). */
    private final Deque<String> disciplineStack = new ArrayDeque<>();

    /** Collected placements from leaf nodes. */
    private final List<PlacementLoader.Placement> placements = new ArrayList<>();

    /** Count of sub-assemblies entered (onSubAssembly calls, depth > 0). */
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

    /** Number of sub-assemblies entered during the walk (onSubAssembly events at depth > 0). */
    public int getSubAssemblyCount() {
        return subAssemblyCount;
    }

    // ── BOMVisitor events ─────────────────────────────────────────

    @Override
    public void onSubAssembly(BOMWalker.NodeContext ctx) {
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

                    // Track storey from FLOOR-level BOMs (bom_type, not bom_level)
                    if ("FLOOR".equals(childBom.getBomType()) && line.getRole() != null) {
                        storeyStack.push(inferStoreyName(line.getRole(), childBom));
                    }

                    // Track discipline from SET-level BOMs (bom_category = ARC, STR, FP, ...)
                    if ("SET".equals(childBom.getBomType()) && childBom.getBomCategory() != null) {
                        disciplineStack.push(childBom.getBomCategory());
                    }
                }
            } catch (SQLException e) {
                System.err.printf("[PlacementCollector] Failed to load BOM %s: %s%n",
                    childBomId, e.getMessage());
            }
        } else if (ctx.bom() != null) {
            // Synthetic root (walkSelf): use world origin from BUILDING m_bom.origin_x/y/z
            bomOriginX = worldOrigin[0];
            bomOriginY = worldOrigin[1];
            bomOriginZ = worldOrigin[2];

            if ("FLOOR".equals(ctx.bom().getBomType())) {
                storeyStack.push(inferStoreyName(null, ctx.bom()));
            }
        }

        // Line offset (parent-relative, tack convention §3.4)
        double lineDx = (line != null) ? line.getDx() : 0;
        double lineDy = (line != null) ? line.getDy() : 0;
        double lineDz = (line != null) ? line.getDz() : 0;

        // Apply cumulative rotation to line offsets (rotation from parent frame)
        double cumRot = rotationStack.isEmpty() ? 0.0 : rotationStack.peek();
        if (cumRot != 0.0) {
            double cos = Math.cos(cumRot);
            double sin = Math.sin(cumRot);
            double rx = lineDx * cos - lineDy * sin;
            double ry = lineDx * sin + lineDy * cos;
            lineDx = rx;
            lineDy = ry;
        }

        // Track unit prefix for mirrored compositions (UNIT_A → "A_", UNIT_B → "B_")
        String role = (line != null) ? line.getRole() : null;
        if (role != null && role.startsWith("UNIT_")) {
            unitPrefixStack.push(role.substring(5) + "_");  // "UNIT_A" → "A_"
        } else {
            unitPrefixStack.push("");  // no prefix change
        }

        // Accumulate this line's rotation_rule into cumulative rotation
        double lineRot = parseRotation(line);
        double newCumRot = cumRot + lineRot;
        rotationStack.push(newCumRot);

        // New anchor = parent anchor + rotated line offset + child BOM origin
        double[] parent = anchorStack.isEmpty() ? new double[]{0, 0, 0} : anchorStack.peek();
        double[] newAnchor = {
            parent[0] + lineDx + bomOriginX,
            parent[1] + lineDy + bomOriginY,
            parent[2] + lineDz + bomOriginZ
        };
        anchorStack.push(newAnchor);
    }

    @Override
    public void onSubAssemblyComplete(BOMWalker.NodeContext ctx) {
        if (!anchorStack.isEmpty()) {
            anchorStack.pop();
        }
        if (!rotationStack.isEmpty()) {
            rotationStack.pop();
        }
        if (!unitPrefixStack.isEmpty()) {
            unitPrefixStack.pop();
        }
        // Pop storey if this was a FLOOR-level BOM
        MBOMLine line = ctx.line();
        if (line != null && line.getChildProductId() != null) {
            try {
                MBOM childBom = MBOM.get(bomConn, line.getChildProductId());
                if (childBom != null && "FLOOR".equals(childBom.getBomType())) {
                    if (!storeyStack.isEmpty()) storeyStack.pop();
                }
                if (childBom != null && "SET".equals(childBom.getBomType())
                        && childBom.getBomCategory() != null) {
                    if (!disciplineStack.isEmpty()) disciplineStack.pop();
                }
            } catch (SQLException ex) {
                // Best effort — storey/discipline tracking is informational
            }
        }
    }

    // FACTORIZE-v1 F-2: verb expansion.
    // For qty=1 (unfactored, SH/DX): single iteration with line dx/dy/dz — identical.
    // For qty>1 + verb_ref (factored, TE): parse verb, compute per-instance positions.
    // BIM_COBOL verbs: TILE, ROUTE, FRAME, SPRAY.
    @Override
    public void onLeaf(BOMWalker.NodeContext ctx) {
        MBOMLine line = ctx.line();
        if (line == null) return;

        double[] anchor = anchorStack.isEmpty() ? new double[]{0, 0, 0} : anchorStack.peek();

        // Apply cumulative rotation to leaf offsets (origin for verb patterns)
        double leafDx = line.getDx();
        double leafDy = line.getDy();
        double leafDz = line.getDz();
        double cumRot = rotationStack.isEmpty() ? 0.0 : rotationStack.peek();
        if (cumRot != 0.0) {
            double cos = Math.cos(cumRot);
            double sin = Math.sin(cumRot);
            double rx = leafDx * cos - leafDy * sin;
            double ry = leafDx * sin + leafDy * cos;
            leafDx = rx;
            leafDy = ry;
        }

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
        MProduct product = ctx.product();
        String materialName = line.getMaterialName();
        String materialRgba = line.getMaterialRgba();

        // Product ID
        String productId = line.getChildProductId();

        // Unit prefix for mirrored compositions
        String unitPrefix = currentUnitPrefix();

        // ── Verb expansion: compute per-instance positions ────────────
        int qty = line.getQty();
        String verbRef = line.getVerbRef();
        double[][] offsets = expandVerb(verbRef, qty, leafDx, leafDy, leafDz);

        for (int qi = 0; qi < qty; qi++) {
            // Per-instance dimensions from CLUSTER verb_ref (6-value format)
            // override BOM line dimensions when available.
            double iHalfW = halfW, iHalfD = halfD, iHalfH = halfH;
            if (offsets[qi].length >= 6 && offsets[qi][3] > 0) {
                iHalfW = offsets[qi][3] / 2.0;  // per-instance width (m) → half
                iHalfD = offsets[qi][4] / 2.0;
                iHalfH = offsets[qi][5] / 2.0;
            }

            // BOM stores LBD offsets (§4 tack convention).
            // Add half-extents to recover centroid for Placement min/max computation.
            double cx = anchor[0] + offsets[qi][0] + iHalfW;
            double cy = anchor[1] + offsets[qi][1] + iHalfD;
            double cz = anchor[2] + offsets[qi][2] + iHalfH;

            // Element ref: from line, or generate from product + ordinal.
            // For qty>1: suffix with instance index to ensure uniqueness.
            String elementRef = line.getElementRef();
            if (elementRef == null || elementRef.isEmpty()) {
                elementRef = (product != null ? product.getProductId() : productId)
                    + ":" + (++ordinalCounter);
            } else if (qty > 1) {
                elementRef = elementRef + ":" + qi;
            }

            // Apply unit prefix for mirrored compositions (makes GUIDs unique per unit)
            if (!unitPrefix.isEmpty()) {
                elementRef = unitPrefix + elementRef;
            }

            // Ordinal: always sequential — ensures GUID uniqueness across
            // mixed qty=1 (stored ordinal) and qty>1 (verb-expanded) lines.
            // Stored ordinal from BOM is a recipe identifier, not placement ID.
            int ordinal = ++ordinalCounter;

            PlacementLoader.Placement p = new PlacementLoader.Placement(
                buildingType,
                storey,
                ifcClass,
                elementRef,
                ordinal,
                cx - iHalfW, cx + iHalfW,
                cy - iHalfD, cy + iHalfD,
                cz - iHalfH, cz + iHalfH,
                line.getOrientation(),
                resolveDiscipline(ifcClass),
                materialName,
                materialRgba,
                null,  // familyRef
                productId
            );
            placements.add(p);
        }
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

    // ── Verb expansion (BIM_COBOL verbs) ────────────────────────────

    /**
     * Expand a verb_ref into per-instance offsets (floor-relative metres).
     *
     * <p>For unfactored lines (verbRef=null, qty=1): returns the line's dx/dy/dz as-is.
     * For factored lines: parses the verb prefix and computes a grid/path of positions.
     *
     * @param verbRef verb pattern string (TILE:nx:ny:stepX:stepY, etc.) or null
     * @param qty number of instances to expand
     * @param originDx line dx (pattern origin, floor-relative)
     * @param originDy line dy
     * @param originDz line dz
     * @return array of [qty][3] offsets (each: dx, dy, dz in metres)
     */
    private static double[][] expandVerb(String verbRef, int qty,
                                         double originDx, double originDy, double originDz) {
        if (verbRef == null || verbRef.isEmpty()) {
            // Unfactored: all instances at same position (qty=1 for current data)
            double[][] result = new double[qty][3];
            for (int i = 0; i < qty; i++) {
                result[i] = new double[]{originDx, originDy, originDz};
            }
            return result;
        }

        if (verbRef.startsWith("TILE:")) {
            return expandTile(verbRef, originDx, originDy, originDz);
        } else if (verbRef.startsWith("ROUTE:")) {
            return expandRoute(verbRef, originDx, originDy, originDz);
        } else if (verbRef.startsWith("FRAME:")) {
            return expandFrame(verbRef, originDz);
        } else if (verbRef.startsWith("CLUSTER:")) {
            return expandCluster(verbRef, originDx, originDy, originDz);
        } else if (verbRef.startsWith("SPRAY:")) {
            // SPRAY uses same expansion as TILE (semi-regular grid)
            return expandSpray(verbRef, qty, originDx, originDy, originDz);
        }

        // Unknown verb — fall back to origin position for all instances
        System.err.printf("[PlacementCollector] Unknown verb_ref prefix: %s — using origin%n", verbRef);
        double[][] result = new double[qty][3];
        for (int i = 0; i < qty; i++) {
            result[i] = new double[]{originDx, originDy, originDz};
        }
        return result;
    }

    /** TILE:nx:ny:stepX:stepY → 2D grid from origin. */
    private static double[][] expandTile(String verbRef,
                                         double originDx, double originDy, double originDz) {
        String[] parts = verbRef.substring(5).split(":");
        int nx = Integer.parseInt(parts[0]);
        int ny = Integer.parseInt(parts[1]);
        double stepX = Double.parseDouble(parts[2]);
        double stepY = Double.parseDouble(parts[3]);

        double[][] result = new double[nx * ny][3];
        int idx = 0;
        for (int ix = 0; ix < nx; ix++) {
            for (int iy = 0; iy < ny; iy++) {
                result[idx++] = new double[]{
                    originDx + ix * stepX,
                    originDy + iy * stepY,
                    originDz
                };
            }
        }
        return result;
    }

    /** ROUTE:X:step:n|Y:step:n|... → axis-aligned legs from origin. */
    private static double[][] expandRoute(String verbRef,
                                          double originDx, double originDy, double originDz) {
        String[] legs = verbRef.substring(6).split("\\|");
        // First pass: count total instances
        int total = 0;
        for (String leg : legs) {
            String[] parts = leg.split(":");
            total += Integer.parseInt(parts[2]);
        }

        double[][] result = new double[total][3];
        int idx = 0;
        double curX = originDx;
        double curY = originDy;

        for (String leg : legs) {
            String[] parts = leg.split(":");
            char axis = parts[0].charAt(0);
            double step = Double.parseDouble(parts[1]);
            int count = Integer.parseInt(parts[2]);

            for (int i = 0; i < count; i++) {
                result[idx++] = new double[]{curX, curY, originDz};
                if (axis == 'X') curX += step;
                else curY += step;
            }
        }
        return result;
    }

    /** FRAME:x1,x2,...|y1,y2,... → cartesian product of gridlines (floor-relative). */
    private static double[][] expandFrame(String verbRef, double originDz) {
        String[] halves = verbRef.substring(6).split("\\|");
        String[] xStrs = halves[0].split(",");
        String[] yStrs = halves[1].split(",");

        double[] xLines = new double[xStrs.length];
        double[] yLines = new double[yStrs.length];
        for (int i = 0; i < xStrs.length; i++) xLines[i] = Double.parseDouble(xStrs[i]);
        for (int i = 0; i < yStrs.length; i++) yLines[i] = Double.parseDouble(yStrs[i]);

        double[][] result = new double[xLines.length * yLines.length][3];
        int idx = 0;
        for (double x : xLines) {
            for (double y : yLines) {
                result[idx++] = new double[]{x, y, originDz};
            }
        }
        return result;
    }

    /** SPRAY:stepX:stepY — same layout as TILE but positions pre-determined by origin/qty. */
    private static double[][] expandSpray(String verbRef, int qty,
                                          double originDx, double originDy, double originDz) {
        // SPRAY is a semi-regular grid — approximate as rectangular grid
        String[] parts = verbRef.substring(6).split(":");
        double stepX = Double.parseDouble(parts[0]);
        double stepY = Double.parseDouble(parts[1]);

        // Determine grid dimensions: nx * ny = qty, aspect ratio from steps
        int ny = Math.max(1, (int) Math.round(Math.sqrt((double) qty * stepX / stepY)));
        int nx = (qty + ny - 1) / ny;  // ceil division

        double[][] result = new double[qty][3];
        int idx = 0;
        for (int ix = 0; ix < nx && idx < qty; ix++) {
            for (int iy = 0; iy < ny && idx < qty; iy++) {
                result[idx++] = new double[]{
                    originDx + ix * stepX,
                    originDy + iy * stepY,
                    originDz
                };
            }
        }
        return result;
    }

    /** CLUSTER:dx1,dy1,dz1;dx2,dy2,dz2;... → exact per-instance offsets from origin. */
    /** Expand CLUSTER verb_ref. Returns double[N][6]: [dx, dy, dz, w, d, h] per instance.
     *  Format: CLUSTER:dx,dy,dz,w,d,h;dx,dy,dz,w,d,h;...
     *  Per-instance dimensions (w,d,h in metres) enable accurate G2-VOLUME. */
    private static double[][] expandCluster(String verbRef,
                                            double originDx, double originDy, double originDz) {
        String data = verbRef.substring(8);  // skip "CLUSTER:"
        String[] entries = data.split(";");
        double[][] result = new double[entries.length][6];
        for (int i = 0; i < entries.length; i++) {
            String[] vals = entries[i].split(",");
            result[i][0] = originDx + Double.parseDouble(vals[0]);
            result[i][1] = originDy + Double.parseDouble(vals[1]);
            result[i][2] = originDz + Double.parseDouble(vals[2]);
            if (vals.length >= 6) {
                result[i][3] = Double.parseDouble(vals[3]);  // width (m)
                result[i][4] = Double.parseDouble(vals[4]);  // depth (m)
                result[i][5] = Double.parseDouble(vals[5]);  // height (m)
            }
            // vals.length == 3: legacy format — dims stay 0.0, caller uses BOM line dims
        }
        return result;
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
                case "FOUNDATION" -> "Foundation";
                case "GROUND_FLOOR" -> "Ground Floor";
                case "GROUND_SLAB" -> "Ground Floor";
                case "LEVEL_1", "L1" -> "Level 1";
                case "LEVEL_2", "L2" -> "Level 2";
                case "LEVEL_3", "L3" -> "Level 3";
                case "LEVEL_4", "L4" -> "Level 4";
                case "ROOF" -> "Roof";
                default -> role.replace('_', ' ');
            };
        }
        // Fall back to bom_category
        String cat = floorBom.getBomCategory();
        if (cat != null) {
            return switch (cat) {
                case "FN" -> "Foundation";
                case "GF" -> "Ground Floor";
                case "L1" -> "Level 1";
                case "L2" -> "Level 2";
                case "L3" -> "Level 3";
                case "L4" -> "Level 4";
                case "RF" -> "Roof";
                case "SL" -> "Ground Floor";
                default -> cat;
            };
        }
        return "Unknown";
    }

    /**
     * Get the current unit prefix from the stack.
     * Returns the first non-empty prefix found (innermost UNIT_* ancestor).
     */
    private String currentUnitPrefix() {
        for (String prefix : unitPrefixStack) {
            if (!prefix.isEmpty()) return prefix;
        }
        return "";
    }

    /**
     * Parse rotation_rule from a BOM line as radians.
     * Supports numeric values (e.g. "0", "3.141592653589793") and null/empty → 0.
     */
    private static double parseRotation(MBOMLine line) {
        if (line == null) return 0.0;
        String rule = line.getRotationRule();
        if (rule == null || rule.isEmpty() || "0".equals(rule)) return 0.0;
        try {
            return Double.parseDouble(rule);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Resolve discipline: prefer BOM hierarchy context (disciplineStack) over
     * static IFC class mapping. The stack carries the authoritative discipline
     * from the parent SET BOM's bom_category (ARC, STR, FP, ACMV, etc.).
     * Falls back to deriveDiscipline() for SH/DX BOMs that don't have
     * discipline SET BOMs in their hierarchy.
     */
    private String resolveDiscipline(String ifcClass) {
        if (!disciplineStack.isEmpty()) {
            return disciplineStack.peek();
        }
        return deriveDiscipline(ifcClass);
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
