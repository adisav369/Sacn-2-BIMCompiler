package com.bim.compiler.bom.walker;

import com.bim.compiler.dsl.PlacementLoader;
import com.bim.compiler.topology.Discipline;
import com.bim.orm.BIMLogger;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;
import com.bim.ormsandbox.po.MProduct;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

    /** Discipline from SET-level BOMs (m_product_category_id → Discipline enum).
     *  Overrides deriveDiscipline() which loses extraction context (e.g. IfcSlab → STR always). */
    // Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 5-6 — Witness: W-DV-DISC-ORG
    private final Deque<Discipline> disciplineStack = new ArrayDeque<>();

    /** Collected placements from leaf nodes. */
    private final List<PlacementLoader.Placement> placements = new ArrayList<>();

    /** Current parent BOM stack — tracks the innermost MAKE ancestor for each leaf. */
    private final Deque<String> parentBomIdStack = new ArrayDeque<>();

    /** Count of sub-assemblies entered (onSubAssembly calls, depth > 0). */
    private int subAssemblyCount = 0;

    /** Verb expansion counters for FINE logging. */
    private int placeCount = 0;
    private int clusterCount = 0;
    private int tileCount = 0;
    private int routeCount = 0;
    private int frameCount = 0;
    private int sprayCount = 0;
    private int otherVerbCount = 0;

    private int ordinalCounter = 0;

    // Implementing EYES_SRS.md §P05/§P06 — Witness: W-TE-PROOF
    // Tracks same-class centroid (mm-rounded) collision count for position jitter.
    private final Map<String, Integer> positionJitterCount = new HashMap<>();

    /** Shim matcher for MEP tack origin resolution (§6.12.2). */
    private ShimMatcher shimMatcher;

    /** IFC GloballyUniqueId: exactly 22 chars from base64url + '$'. */
    private static final Pattern IFC_GUID = Pattern.compile("^[0-9A-Za-z_$]{22}$");

    public PlacementCollectorVisitor(Connection bomConn, String buildingType) {
        this(bomConn, buildingType, new double[]{0, 0, 0});
    }

    public PlacementCollectorVisitor(Connection bomConn, String buildingType, double[] worldOrigin) {
        this.bomConn = bomConn;
        this.buildingType = buildingType;
        this.worldOrigin = worldOrigin;
    }

    /**
     * Set the shim matcher for MEP host surface resolution.
     * Must be called before walk begins.
     */
    public void setShimMatcher(ShimMatcher matcher) {
        this.shimMatcher = matcher;
    }

    public List<PlacementLoader.Placement> getPlacements() {
        return List.copyOf(placements);
    }

    /** Number of sub-assemblies entered during the walk (onSubAssembly events at depth > 0). */
    public int getSubAssemblyCount() {
        return subAssemblyCount;
    }

    /** Verb breakdown string for FINE logging. */
    public String getVerbBreakdown() {
        return String.format("%d PLACE, %d CLUSTER, %d TILE, %d ROUTE, %d FRAME, %d SPRAY, %d other",
                placeCount, clusterCount, tileCount, routeCount, frameCount, sprayCount, otherVerbCount);
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

                    // Track discipline from SET-level BOMs (m_product_category_id → Discipline enum)
                    if ("SET".equals(childBom.getBomType()) && childBom.getProductCategory() != null) {
                        Discipline setDisc = Discipline.fromString(childBom.getProductCategory());
                        if (setDisc != null) disciplineStack.push(setDisc);
                    }

                    // Track discipline from MEP sub-BOMs (§6.12.2 shim+joint piece walk)
                    // Implementing DISC_VALIDATION_DB_SRS.md §6.12.2 — Witness: W-J4-MEP-WALK
                    if ("MEP".equals(childBom.getBomType()) && childBom.getProductCategory() != null) {
                        Discipline mepDisc = Discipline.fromString(childBom.getProductCategory());
                        if (mepDisc != null) {
                            disciplineStack.push(mepDisc);
                        }
                        // §6.12.2: Shim is M_BOM parent — log shim entry with host properties
                        if (childBom.isShim()) {
                            BIMLogger.geo("SHIM", "SHIM_BOM {} disc={} host={} mount={} offset={}mm depth={}",
                                    childBomId, mepDisc != null ? mepDisc.name() : "?",
                                    childBom.getHostIfcClass(), childBom.getMount(),
                                    childBom.getOffsetMm(), ctx.level());
                        } else {
                            BIMLogger.geo("SHIM", "MEP_ENTER bom={} disc={} depth={}",
                                    childBomId, mepDisc != null ? mepDisc.name() : "?", ctx.level());
                        }
                    }
                }
            } catch (SQLException e) {
                System.err.printf("[PlacementCollector] Failed to load BOM %s: %s%n",
                    childBomId, e.getMessage());
                BIMLogger.warn("COMPILE", "BomLoadFailure for {} — {}", childBomId, e.getMessage());
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
            // Log 4: Rotation applied — proves rotation was not skipped
            if (BIMLogger.geoMatch(childBomId != null ? childBomId : "ROOT")) {
                BIMLogger.geo("TACK", "  ROT {:.4f}rad: line=({:.4f},{:.4f}) → rotated=({:.4f},{:.4f})",
                    cumRot, line != null ? line.getDx() : 0.0, line != null ? line.getDy() : 0.0, rx, ry);
            }
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

        // Log 1: ENTER — proves anchor accumulation executed
        if (BIMLogger.geoMatch(childBomId != null ? childBomId : "ROOT")) {
            BIMLogger.geo("TACK", "ENTER {} depth={}: parent=({:.4f},{:.4f},{:.4f}) + line=({:.4f},{:.4f},{:.4f}) + bomOrigin=({:.4f},{:.4f},{:.4f}) → anchor=({:.4f},{:.4f},{:.4f})",
                childBomId != null ? childBomId : "ROOT",
                ctx.level(),
                parent[0], parent[1], parent[2],
                lineDx, lineDy, lineDz,
                bomOriginX, bomOriginY, bomOriginZ,
                newAnchor[0], newAnchor[1], newAnchor[2]);
            // Log 1b: CHAIN — full ancestor path for auditor (proves no missing transform)
            BIMLogger.geo("TACK", "  CHAIN depth={}: {}",
                ctx.level(), formatAncestorChain(parentBomIdStack, childBomId));
        }

        // Track parent BOM for sibling-only GEO pairs
        parentBomIdStack.push(childBomId != null ? childBomId : "ROOT");
    }

    @Override
    public void onSubAssemblyComplete(BOMWalker.NodeContext ctx) {
        // Pop parent BOM tracker
        if (!parentBomIdStack.isEmpty()) parentBomIdStack.pop();

        // Log 2: EXIT — proves stack unwind happened
        if (!anchorStack.isEmpty()) {
            MBOMLine exitLine = ctx.line();
            String exitId = (exitLine != null) ? exitLine.getChildProductId() : "ROOT";
            if (BIMLogger.geoMatch(exitId != null ? exitId : "ROOT")) {
                BIMLogger.geo("TACK", "EXIT  {} depth={}",
                    exitId != null ? exitId : "ROOT", ctx.level());
            }
        }
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
                        && childBom.getProductCategory() != null
                        && Discipline.fromString(childBom.getProductCategory()) != null) {
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
    //
    // GEO = white-box only. Black-box correctness is owned by ExtractedGeometryTruthTest T3-ARC.
    // Do not add extraction-DB joins here. Forensic route confirmation via bim.geo.debug=true
    // is sufficient for DISC device positioning review.
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
            // Log 4b: Leaf rotation — proves rotation applied to leaf offset
            if (BIMLogger.geoMatch(line.getChildProductId())) {
                BIMLogger.geo("TACK", "  ROT {:.4f}rad: leaf=({:.4f},{:.4f}) → rotated=({:.4f},{:.4f})",
                    cumRot, line.getDx(), line.getDy(), rx, ry);
            }
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
                BIMLogger.warn("COMPILE", "MetadataMissing for element_ref={} — no product and no allocated dims",
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

        // §6.12.2: UOM-driven InterimWorkshop for variable-length pieces
        // UOM is the signal — no CUT verb needed (Compiere BOMQty+C_UOM_ID convention).
        // Dimensions BEFORE positions: this block runs before expandVerb().
        // Rotation is AFTER recompute — half-extents are in local frame; rotation transforms to world frame.
        // Guard: qty_type=FIXED (tee, elbow, terminal) → workshop rejected, tack i/o is predetermined.
        // Implementing DISC_VALIDATION_DB_SRS.md §6.12.2 §6 — Witness: W-WORKSHOP-1
        if (line.isLengthBased()) {
            if (!"VARIABLE".equals(line.getQtyType())) {
                BIMLogger.geo("WORKSHOP", "REJECT {} qty_type={} — using library dimensions",
                        productId, line.getQtyType());
            } else {
                String fwdAxis = resolveForwardAxis(product);
                BIMLogger.geo("WORKSHOP", "ENTER {} uom={} qty={} qty_type={} fwd_axis={}",
                        productId, line.getUomId(), line.getQty(), line.getQtyType(), fwdAxis);
                double targetM = InterimWorkshop.qtyToMetres(line.getQty(), line.getUomId());
                double[] trimmed = InterimWorkshop.recompute(product, fwdAxis, targetM);
                if (trimmed != null) {
                    BIMLogger.geo("WORKSHOP", "RECOMPUTE {} axis={} original=({:.4f},{:.4f},{:.4f}) → target={:.3f}m → half=({:.4f},{:.4f},{:.4f})",
                            productId, fwdAxis, halfW, halfD, halfH, targetM, trimmed[0], trimmed[1], trimmed[2]);
                    halfW = trimmed[0];
                    halfD = trimmed[1];
                    halfH = trimmed[2];
                    BIMLogger.geo("WORKSHOP", "TACK_IO {} inlet=({:.4f},{:.4f},{:.4f}) outlet_offset={:.4f}m along {}",
                            productId, anchor[0], anchor[1], anchor[2], targetM, fwdAxis);
                }
            }
        }

        // ── Verb expansion: compute per-instance positions ────────────
        int qty = line.getQty();
        String verbRef = line.getVerbRef();
        double[][] offsets = expandVerb(verbRef, qty, leafDx, leafDy, leafDz);

        // Track verb usage for FINE logging
        if (verbRef == null || verbRef.isEmpty()) { placeCount++; }
        else if (verbRef.startsWith("CLUSTER:")) { clusterCount++; }
        else if (verbRef.startsWith("TILE:"))    { tileCount++; }
        else if (verbRef.startsWith("ROUTE:"))   { routeCount++; }
        else if (verbRef.startsWith("FRAME:"))   { frameCount++; }
        else if (verbRef.startsWith("SPRAY:"))   { sprayCount++; }
        else { otherVerbCount++; }

        // CP-1: Load MA (Material Allocation) GUIDs for identity-based matching
        String[] maGuids = loadMaGuids(line.getBomId(), line.getSequence(), qty);

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

            // Log 3+5: LEAF — proves centroid was computed by tack accumulation + shows GUID
            // (elementRef resolved below, so we log after GUID resolution)

            // Element ref: CP-1 MA guid > line ref > generated.
            // MA (Material Allocation) carries per-instance IFC GUIDs for SpatialDiff.
            // Guard: only accept valid IFC GloballyUniqueId (22 chars, base64url+'$').
            // Invalid format = product name leaked into MA → reject, fall through.
            String elementRef = null;
            if (maGuids != null && qi < maGuids.length && maGuids[qi] != null
                    && IFC_GUID.matcher(maGuids[qi]).matches()) {
                elementRef = maGuids[qi];
            }
            if (elementRef == null) {
                elementRef = line.getElementRef();
                if (elementRef == null || elementRef.isEmpty()) {
                    elementRef = (product != null ? product.getProductId() : productId)
                        + ":" + (++ordinalCounter);
                } else if (qty > 1) {
                    elementRef = elementRef + ":" + qi;
                }
            }

            // Apply unit prefix for mirrored compositions (makes GUIDs unique per unit)
            if (!unitPrefix.isEmpty()) {
                elementRef = unitPrefix + elementRef;
            }

            // Log 3+5: LEAF with GUID — proves tack math ran and IFC GUID preserved
            if (BIMLogger.geoMatch(productId)) {
                BIMLogger.geo("TACK", "LEAF  {} guid={} anchor=({:.4f},{:.4f},{:.4f}) + offset=({:.4f},{:.4f},{:.4f}) + half=({:.4f},{:.4f},{:.4f}) → centroid=({:.4f},{:.4f},{:.4f}) LBD=({:.4f},{:.4f},{:.4f})",
                    productId, elementRef,
                    anchor[0], anchor[1], anchor[2],
                    offsets[qi][0], offsets[qi][1], offsets[qi][2],
                    iHalfW, iHalfD, iHalfH,
                    cx, cy, cz,
                    cx - iHalfW, cy - iHalfD, cz - iHalfH);
                // Log 3b: CHAIN — full ancestor path at LEAF (proves provenance)
                BIMLogger.geo("TACK", "  CHAIN {}: {}", productId,
                    formatAncestorChain(parentBomIdStack, productId));
                // Log 3c: DIMS — explicit W×D×H in mm (proves no truncation/swap)
                BIMLogger.geo("TACK", "  DIMS  {}: {:.0f}×{:.0f}×{:.0f} mm (alloc={:.0f}×{:.0f}×{:.0f})",
                    productId,
                    iHalfW * 2000, iHalfD * 2000, iHalfH * 2000,
                    line.getAllocatedWidthMmExact(), line.getAllocatedDepthMmExact(), line.getAllocatedHeightMmExact());
                // Log 3d: CONTAIN — is LEAF inside parent AABB? (proves no LMP drift)
                logContainmentCheck(productId, cx, cy, cz, iHalfW, iHalfD, iHalfH, anchor);
            }

            // Ordinal: always sequential — ensures GUID uniqueness across
            // mixed qty=1 (stored ordinal) and qty>1 (verb-expanded) lines.
            // Stored ordinal from BOM is a recipe identifier, not placement ID.
            int ordinal = ++ordinalCounter;

            // Implementing EYES_SRS.md §P05/§P06 — Witness: W-TE-PROOF
            // Position jitter: if a same-class element already occupies this centroid (within 1mm),
            // offset Z by 2mm × collision index (2mm > P05 1mm threshold, avoids FP boundary).
            // Position disambiguation — not position invention.
            String posKey = ifcClass + "|"
                + Math.round(cx * 1000) + "|"
                + Math.round(cy * 1000) + "|"
                + Math.round(cz * 1000);
            int jitterIndex = positionJitterCount.merge(posKey, 1, Integer::sum) - 1;
            double jitteredCz = cz + jitterIndex * 0.002;
            if (jitterIndex > 0) {
                BIMLogger.geo("JITTER", "P05/P06 disambig {} class={} cz={:.4f} → {:.4f} (+{}mm)",
                    elementRef, ifcClass, cz, jitteredCz, jitterIndex * 2);
            }

            PlacementLoader.Placement p = new PlacementLoader.Placement(
                buildingType,
                storey,
                ifcClass,
                elementRef,
                ordinal,
                cx - iHalfW, cx + iHalfW,
                cy - iHalfD, cy + iHalfD,
                jitteredCz - iHalfH, jitteredCz + iHalfH,
                line.getOrientation(),
                resolveDiscipline(ifcClass, ctx.discipline()),
                materialName,
                materialRgba,
                line.getElementRef() != null ? line.getElementRef() : productId,  // familyRef — raw IFC name for fidelity; abstract productId for GENERATIVE
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
        // Implementing AUDIT_20260402.txt §2 Gap A — verb unknown must appear in TACK channel
        BIMLogger.warn("COMPILE", "UnknownVerbRef prefix: {} — using origin", verbRef);
        BIMLogger.geo("TACK", "VERB_UNKNOWN {} prefix='{}' qty={} — origin fallback ({:.4f},{:.4f},{:.4f})",
            verbRef, verbRef, qty, originDx, originDy, originDz);
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

    /** FRAME:x1,x2,...|y1,y2,...|halfW,halfD → cartesian product of gridlines (floor-relative).
     *  LBD offsets are stored directly (clustered from minX/minY), so no half-extent
     *  conversion is needed. The embedded halfW,halfD are informational only — the
     *  caller uses BOM-line dimensions for centroid recovery (standard tack convention). */
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
     *  Format: CLUSTER:dx,dy,dz,w,d,h[,guid];dx,dy,dz,w,d,h[,guid];...
     *  Per-instance dimensions (w,d,h in metres) enable accurate G2-VOLUME.
     *  Optional 7th field (guid) is the IFC extraction GUID — see extractClusterGuids(). */
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
            // vals.length == 7: guid in vals[6] — parsed by extractClusterGuids()
        }
        return result;
    }

    /**
     * CP-1: Load Material Allocation GUIDs for a BOM line.
     * m_bom_line_ma stores per-instance IFC GUIDs (iDempiere M_InOutLineMA pattern).
     * Returns null if no MA rows exist (SH/DX or old BOM DBs).
     */
    private String[] loadMaGuids(String bomId, int sequence, int qty) {
        try (java.sql.PreparedStatement ps = bomConn.prepareStatement(
                "SELECT qi, guid FROM m_bom_line_ma WHERE bom_id = ? AND sequence = ? ORDER BY qi")) {
            ps.setString(1, bomId);
            ps.setInt(2, sequence);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                String[] guids = new String[qty];
                boolean hasAny = false;
                while (rs.next()) {
                    int qi = rs.getInt(1);
                    if (qi >= 0 && qi < qty) {
                        guids[qi] = rs.getString(2);
                        hasAny = true;
                    }
                }
                return hasAny ? guids : null;
            }
        } catch (java.sql.SQLException e) {
            // Table may not exist (old BOM DB) — return null → fallback to line element_ref
            return null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    /**
     * Resolve IFC class from BOM line + product.
     *
     * <p>Priority: line.role (if IFC-prefixed, EXTRACTED convention) →
     * product.ifc_class → child_product_id (if IFC-prefixed) → "Unknown".
     */
    // ── GEO helpers: LMP foolproof logging ────────────────────────────────

    /** Format full ancestor chain: BUILDING→FLOOR→ROOM→LEAF for audit trail. */
    private String formatAncestorChain(Deque<String> parentStack, String current) {
        StringBuilder sb = new StringBuilder();
        // parentBomIdStack has ancestors bottom-up, iterate in order
        for (String ancestor : parentStack) {
            sb.append(ancestor).append("→");
        }
        sb.append(current);
        return sb.toString();
    }

    /** Log AABB containment: is the placed LEAF inside the parent anchor region? */
    private void logContainmentCheck(String productId,
            double cx, double cy, double cz,
            double halfW, double halfD, double halfH,
            double[] parentAnchor) {
        // LEAF LBD (min corner)
        double lx = cx - halfW, ly = cy - halfD, lz = cz - halfH;
        // Check: LEAF LBD should be >= parent anchor (elements don't precede their parent origin)
        // Negative offset from parent = element placed before parent origin = potential LMP
        double dx = lx - parentAnchor[0];
        double dy = ly - parentAnchor[1];
        double dz = lz - parentAnchor[2];
        boolean contained = dx >= -0.001 && dy >= -0.001 && dz >= -0.001;
        if (contained) {
            BIMLogger.geo("TACK", "  CONTAIN {}: OK offset=({:.4f},{:.4f},{:.4f})m from parent",
                productId, dx, dy, dz);
        } else {
            BIMLogger.geo("TACK", "  CONTAIN {}: OVERSHOOT offset=({:.4f},{:.4f},{:.4f})m — LMP candidate",
                productId, dx, dy, dz);
        }
    }

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
        // Fall back to m_product_category_id
        String cat = floorBom.getProductCategory();
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
     * Resolve the forward axis for a product from M_Product.forward_axis.
     *
     * <p>forward_axis is populated by IFCtoERP from component_library.db at extraction
     * time (migration J4_002). Used by InterimWorkshop to determine which half-extent
     * is overridden when a BOM line carries a length-based UOM (MM/M).
     *
     * @param product M_Product for this BOM line, or null
     * @return "X", "Y", or "Z"; defaults to "Y" (library convention for straight pipe/duct)
     */
    private static String resolveForwardAxis(MProduct product) {
        if (product == null) return "Y";
        String fa = product.getForwardAxis();
        return (fa != null && !fa.isEmpty()) ? fa : "Y";
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
            // Implementing AUDIT_20260402.txt §2 Gap B — silent rotation fallback made visible
            String productRef = (line != null) ? String.valueOf(line.getBomLineId()) : "?";
            BIMLogger.geo("TACK", "ROT_PARSE_ERR {} rule='{}' — fallback 0.0rad", productRef, rule);
            return 0.0;
        }
    }

    /**
     * Resolve discipline: priority order:
     * <ol>
     *   <li>C_OrderLine.Discipline enum (from OrderLineWalker — authoritative when non-null, non-ARC)</li>
     *   <li>disciplineStack (from SET-level BOM m_product_category_id → Discipline enum)</li>
     *   <li>deriveDiscipline() (static IFC class → Discipline enum, extraction fallback)</li>
     * </ol>
     *
     * @param ifcClass     IFC class of the leaf element
     * @param olDiscipline Discipline enum from C_OrderLine (null when using BOMWalker path)
     */
    // Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 5-6 — Witness: W-DV-DISC-ORG
    private Discipline resolveDiscipline(String ifcClass, Discipline olDiscipline) {
        // OrderLine discipline is authoritative when explicitly set to non-default
        if (olDiscipline != null && olDiscipline != Discipline.ARC) {
            return olDiscipline;
        }
        if (!disciplineStack.isEmpty()) {
            return disciplineStack.peek();
        }
        return deriveDiscipline(ifcClass);
    }

    /**
     * Derive discipline from product category — extraction-only fallback when
     * BOM hierarchy lacks discipline. Returns Discipline enum.
     *
     * <p>CP-4 semantic half: uses ProductCategory.deriveDiscipline() instead of
     * switching on IFC class strings. The primary path is still the disciplineStack.
     */
    // Implementing BBC.md §2.2.1 — Witness: W-PRODUCT-CATEGORY
    private static Discipline deriveDiscipline(String ifcClass) {
        String text = com.bim.compiler.validation.ProductCategory.deriveDiscipline(
            com.bim.compiler.validation.ProductCategory.resolve(ifcClass));
        Discipline d = Discipline.fromString(text);
        return d != null ? d : Discipline.ARC;
    }

    // Implementing AUDIT_20260402.txt §4 — emitGeoSummary removed.
    // GEO = white-box only. Black-box correctness is owned by ExtractedGeometryTruthTest T3-ARC.
    // Do not add extraction-DB joins here. Forensic route confirmation via bim.geo.debug=true
    // is sufficient for DISC device positioning review.
}
