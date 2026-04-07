package com.bim.compiler.bom.walker;

import com.bim.compiler.dsl.PlacementLoader;
import com.bim.compiler.topology.Discipline;
import com.bim.orm.BIMLogger;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;
import com.bim.ormsandbox.po.MProduct;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    /** Mirror axis through the sub-assembly stack ("" = no mirror, "X"/"Y"/"Z" = reflection).
     *  S145: MIRROR:X negates only the mirror-axis offset, cross-axes unchanged.
     *  See docs/DuplexAnalysis.md §Rotation Center Proof. */
    private final Deque<String> mirrorAxisStack = new ArrayDeque<>();

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

    /** GEO proof records — one per placed element instance. Implementing S144 §Task 2 — Witness: W-GEO-PROOF */
    private final List<GeoProofRecord> proofRecords = new ArrayList<>();

    /** Parent AABB dimensions stack (width, depth, height in metres). Pushed on sub-assembly enter. */
    private final Deque<double[]> parentAABBStack = new ArrayDeque<>();

    /** Current parent BOM stack — tracks the innermost MAKE ancestor for each leaf. */
    private final Deque<String> parentBomIdStack = new ArrayDeque<>();

    /** Count of sub-assemblies entered (onSubAssembly calls, depth > 0). */
    private int subAssemblyCount = 0;

    /** MEP BOM depth counter — >0 means we're inside an MEP recipe (nested walk). §6.12.2: MEP pieces exempt from LMP. */
    private int mepBomDepth = 0;

    /** Verb expansion counters for FINE logging. */
    private int placeCount = 0;
    private int clusterCount = 0;
    private int tileCount = 0;
    private int routeCount = 0;
    private int frameCount = 0;
    private int sprayCount = 0;
    private int placeDeviceCount = 0;
    private int otherVerbCount = 0;

    private int ordinalCounter = 0;

    // Implementing EYES_SRS.md §P05/§P06 — Witness: W-TE-PROOF
    // Tracks same-class centroid (mm-rounded) collision count for position jitter.
    private final Map<String, Integer> positionJitterCount = new HashMap<>();

    /** Shim matcher for MEP tack origin resolution (§6.12.2). */
    private ShimMatcher shimMatcher;

    /** ERP.db connection for generative MEP placement (ad_space_type_mep_bom + ad_placement_offset).
     *  Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 — Witness: W-DEVICE-PLACE */
    private Connection erpConn;

    /** MEP order coverage level: 99=standard, 0=max, N=budget cap. Default 99. */
    private int mepOrderQty = 99;

    /** Count of generative MEP placements (shims + devices) emitted during this walk.
     *  Used by G1-COUNT to adjust expected element count. */
    private int generativeDeviceCount = 0;

    /** Generative devices that breached their room AABB. */
    private int generativeBreachCount = 0;

    /** Per-device-type counts for generative MEP summary. */
    private final Map<String, Integer> generativeDeviceCounts = new HashMap<>();

    /** Per-room generative placement log — room BOM ID → device count. */
    private final Map<String, Integer> generativeRoomCounts = new HashMap<>();

    /** §12g hardened summary counters — accumulated across all rooms. */
    private int totalCollisionShifts = 0;
    private int totalCollisionConflicts = 0;
    private int totalArcSnaps = 0;
    private int totalArcMisses = 0;
    private int totalNarrowRooms = 0;

    // §12g GAP-1+6: Pending generative room context — captured at onSubAssembly,
    // consumed at onSubAssemblyComplete AFTER furniture children are walked.
    // This allows furniture collision checks against already-placed siblings.
    private PendingGenerativeRoom pendingGenRoom = null;

    /** Furniture placements index: room BOM ID → start index in placements list.
     *  Set when a generative room enters; furniture added during child walk falls after this index. */
    private int furnitureStartIndex = -1;

    /** Captured room context for deferred generative placement (§12g GAP-1+6). */
    private record PendingGenerativeRoom(
        String childBomId, String spaceType, String storey,
        double[] anchor, double[] roomAabb,
        double rw, double rd, double rh,
        double cumRotation
    ) {}

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

    /**
     * Set ERP.db connection for generative MEP device placement.
     * When set, the walker reads ad_space_type_mep_bom + ad_placement_offset
     * to synthesize MEP device placements in rooms with matching capabilities.
     */
    public void setErpConn(Connection erpConn) {
        this.erpConn = erpConn;
    }

    /**
     * Set MEP order coverage level.
     * @param qty 99=standard (default), 0=max fill, N=budget cap
     */
    public void setMepOrderQty(int qty) {
        this.mepOrderQty = qty;
    }

    /** Count of generative MEP devices placed during this walk. */
    public int getGenerativeDeviceCount() {
        return generativeDeviceCount;
    }

    /** Emit generative MEP summary to INFO log — call after walk completes. */
    public void emitGenerativeSummary() {
        if (generativeDeviceCount == 0) {
            BIMLogger.info("GENERATIVE", "SUMMARY: 0 generative devices (no rooms with MEP schedules)");
            return;
        }
        // Use WARN level so SUMMARY survives Maven -q (which suppresses INFO stdout)
        BIMLogger.warn("GENERATIVE",
            "SUMMARY {} devices {} rooms orderQty={} breaches={} collisionShifts={} collisionConflicts={} arcSnaps={} arcMisses={} narrowRooms={}",
            generativeDeviceCount, generativeRoomCounts.size(), mepOrderQty,
            generativeBreachCount, totalCollisionShifts, totalCollisionConflicts,
            totalArcSnaps, totalArcMisses, totalNarrowRooms);
        // Per-device breakdown sorted by count descending
        generativeDeviceCounts.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .forEach(e -> BIMLogger.info("GENERATIVE", "  {} × {}",
                e.getKey(), e.getValue()));
        // Per-room breakdown
        generativeRoomCounts.forEach((room, count) ->
            BIMLogger.info("GENERATIVE", "  room {} → {} devices", room, count));
        // §12g Diagnostic: flag actionable issues for next session
        if (totalCollisionConflicts > 0) {
            BIMLogger.warn("GENERATIVE",
                "DIAGNOSTIC: {} devices could not avoid furniture — wall zone selection needs wider search or alternative walls",
                totalCollisionConflicts);
        }
        if (generativeBreachCount > 0) {
            BIMLogger.warn("GENERATIVE",
                "DIAGNOSTIC: {} devices breach room AABB — likely Z-offset metadata assumes floor-relative coords but room anchor is building-relative. Check ad_placement_offset z_offset values vs actual room height.",
                generativeBreachCount);
        }
        if (totalArcMisses > 0) {
            BIMLogger.warn("GENERATIVE",
                "DIAGNOSTIC: {} rooms have no ARC ceiling in BOM — ShimMatcher found no CEILING/Covering elements. Check if room is under a shared structural BOM (DUPLEX_SINGLE_UNIT_STD) rather than per-storey BOM.",
                totalArcMisses);
        }
        if (totalNarrowRooms > 0) {
            BIMLogger.warn("GENERATIVE",
                "DIAGNOSTIC: {} rooms have SET BOM AABB < 1m (furniture extent, not room footprint). Collision check skipped. Fix: extract IfcSpace room footprint into SET BOM header.",
                totalNarrowRooms);
        }
    }

    public List<PlacementLoader.Placement> getPlacements() {
        return List.copyOf(placements);
    }

    /** GEO proof records collected during walk — one per placed element instance. */
    public List<GeoProofRecord> getProofRecords() {
        return List.copyOf(proofRecords);
    }

    /** Number of sub-assemblies entered during the walk (onSubAssembly events at depth > 0). */
    public int getSubAssemblyCount() {
        return subAssemblyCount;
    }

    /** Verb breakdown string for FINE logging. */
    public String getVerbBreakdown() {
        return String.format("%d PLACE, %d CLUSTER, %d TILE, %d ROUTE, %d FRAME, %d SPRAY, %d PLACE_DEVICE, %d other",
                placeCount, clusterCount, tileCount, routeCount, frameCount, sprayCount, placeDeviceCount, otherVerbCount);
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
                    String cbt = childBom.getBomType();
                    if (cbt != null && cbt.startsWith("MEP") && childBom.getProductCategory() != null) {
                        mepBomDepth++;
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

        // Apply cumulative mirror reflection OR rotation to line offsets
        // S145 fix: MIRROR:X is rot=π about party wall — negate both X and Y
        String cumMirror = mirrorAxisStack.isEmpty() ? "" : mirrorAxisStack.peek();
        double cumRot = rotationStack.isEmpty() ? 0.0 : rotationStack.peek();
        if (!cumMirror.isEmpty()) {
            double rx = lineDx, ry = lineDy;
            switch (cumMirror) {
                case "X" -> { rx = -lineDx; ry = -lineDy; }
                case "Y" -> { rx = -lineDx; ry = -lineDy; }
            }
            if (BIMLogger.geoMatch(childBomId != null ? childBomId : "ROOT")) {
                BIMLogger.geo("TACK", "  MIRROR:{}: line=({:.4f},{:.4f}) → reflected=({:.4f},{:.4f})",
                    cumMirror, line != null ? line.getDx() : 0.0, line != null ? line.getDy() : 0.0, rx, ry);
            }
            lineDx = rx;
            lineDy = ry;
        } else if (cumRot != 0.0) {
            double cos = Math.cos(cumRot);
            double sin = Math.sin(cumRot);
            double rx = lineDx * cos - lineDy * sin;
            double ry = lineDx * sin + lineDy * cos;
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

        // Propagate mirror axis: inherit from parent, or set from this line's rule
        String lineMirror = parseMirrorAxis(line);
        mirrorAxisStack.push(lineMirror != null ? lineMirror : (!cumMirror.isEmpty() ? cumMirror : ""));

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

        // S144: Push parent AABB for envelope containment checks on leaf elements.
        // Use M_Product dims (metres) when available; (0,0,0) means "unknown envelope".
        MProduct subProduct = ctx.product();
        if (subProduct != null && subProduct.getWidth() > 0) {
            parentAABBStack.push(new double[]{subProduct.getWidth(), subProduct.getDepth(), subProduct.getHeight()});
        } else {
            parentAABBStack.push(new double[]{0, 0, 0});  // unknown — envelope check will report UNKNOWN
        }

        // ── S150: Generative MEP device placement ───────────────────
        // When entering a SET BOM whose product category maps to a space type
        // with MEP schedules, synthesize device placements from metadata.
        // §12g GAP-1+6: Capture room context for deferred generative placement.
        // Generative MEP placement is deferred to onSubAssemblyComplete so that
        // furniture children are walked FIRST — enabling furniture collision checks.
        // Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 — Witness: W-DEVICE-PLACE
        if (erpConn != null && childBomId != null) {
            try {
                MBOM childBom = MBOM.get(bomConn, childBomId);
                if (childBom != null && "SET".equals(childBom.getBomType())
                        && childBom.getProductCategory() != null) {
                    String spaceType = MEPDevicePlacer.resolveSpaceType(erpConn, childBom.getProductCategory());
                    if (spaceType != null) {
                        double[] anchor = anchorStack.peek().clone();
                        // §12g diagnostic: trace anchor accumulation for ceiling Z analysis
                        BIMLogger.info("GENERATIVE",
                            "ANCHOR_TRACE {} stackDepth={} anchor=({:.4f},{:.4f},{:.4f}) worldOrigin=({:.4f},{:.4f},{:.4f})",
                            childBomId, anchorStack.size(), anchor[0], anchor[1], anchor[2],
                            worldOrigin[0], worldOrigin[1], worldOrigin[2]);
                        double rw = childBom.getAabbWidthMm() / 1000.0;
                        double rd = childBom.getAabbDepthMm() / 1000.0;
                        double rh = childBom.getAabbHeightMm() / 1000.0;
                        if (rw <= 0 || rd <= 0 || rh <= 0) {
                            double[] dims = parentAABBStack.peek();
                            rw = dims[0]; rd = dims[1]; rh = dims[2];
                        }
                        // S151 Bug 1 fix: bomAABB height is furniture extent, not room height.
                        // §12g GAP-2: Use actual ARC ceiling Z when available, metadata as fallback.
                        if (SpaceScheduleDAO.needsCeilingOverride((int)(rh * 1000))) {
                            double ceilingH = SpaceScheduleDAO.getCeilingHeightM(erpConn, spaceType);
                            BIMLogger.info("GENERATIVE",
                                "CEILING_OVERRIDE {} bomAABB_h={:.0f}mm < 2400mm → using metadata ceiling={:.0f}mm",
                                childBomId, rh * 1000, ceilingH * 1000);
                            rh = ceilingH;
                        }
                        if (rw > 0.01 && rd > 0.01 && rh > 0.01) {
                            double[] roomAabb = {
                                anchor[0], anchor[0] + rw,
                                anchor[1], anchor[1] + rd,
                                anchor[2], anchor[2] + rh
                            };
                            String storey = storeyStack.isEmpty() ? "Unknown" : storeyStack.peek();
                            double genCumRot = rotationStack.isEmpty() ? 0.0 : rotationStack.peek();
                            // Capture context — defer placement to onSubAssemblyComplete
                            pendingGenRoom = new PendingGenerativeRoom(
                                childBomId, spaceType, storey, anchor, roomAabb, rw, rd, rh, genCumRot);
                            furnitureStartIndex = placements.size();
                            BIMLogger.info("GENERATIVE",
                                "ROOM_ENTER {} type={} storey={} anchor=({:.4f},{:.4f},{:.4f}) room=[{:.3f},{:.3f},{:.3f}]→[{:.3f},{:.3f},{:.3f}]m — deferring MEP to after furniture walk",
                                childBomId, spaceType, storey,
                                anchor[0], anchor[1], anchor[2],
                                roomAabb[0], roomAabb[2], roomAabb[4],
                                roomAabb[1], roomAabb[3], roomAabb[5]);
                        }
                    }
                }
            } catch (SQLException e) {
                BIMLogger.warn("COMPILE", "GenerativeMEP capture for {} — {}", childBomId, e.getMessage());
            }
        }
    }

    @Override
    public void onSubAssemblyComplete(BOMWalker.NodeContext ctx) {
        // §12g GAP-1+6: Execute deferred generative MEP placement BEFORE stack pops.
        // Furniture children have been walked — their AABBs are now in placements list.
        if (pendingGenRoom != null) {
            PendingGenerativeRoom room = pendingGenRoom;
            pendingGenRoom = null;
            try {
                placeGenerativeDevices(room);
            } catch (SQLException e) {
                BIMLogger.warn("COMPILE", "GenerativeMEP deferred for {} — {}", room.childBomId(), e.getMessage());
            }
        }

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
        if (!mirrorAxisStack.isEmpty()) {
            mirrorAxisStack.pop();
        }
        if (!unitPrefixStack.isEmpty()) {
            unitPrefixStack.pop();
        }
        if (!parentAABBStack.isEmpty()) {
            parentAABBStack.pop();
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
                // §6.12.2: Pop MEP depth counter on leave
                String cbtLeave = childBom != null ? childBom.getBomType() : null;
                if (cbtLeave != null && cbtLeave.startsWith("MEP")) {
                    if (mepBomDepth > 0) mepBomDepth--;
                    if (!disciplineStack.isEmpty()) disciplineStack.pop();
                }
            } catch (SQLException ex) {
                // Best effort — storey/discipline tracking is informational
            }
        }
    }

    /**
     * Deferred generative MEP device placement — runs AFTER furniture children are walked.
     * §12g GAP-1: furniture AABBs are now available for collision checks.
     * §12g GAP-2: queries compiled ARC ceiling Z when available.
     * §12g GAP-3: uses ShimMatcher for surface snapping (if loaded).
     * Implementing DISC_VALIDATION_DB_SRS.md §12a-§12g — Witness: W-SHIM-DEVICE
     */
    private void placeGenerativeDevices(PendingGenerativeRoom room) throws SQLException {
        double[] roomAabb = room.roomAabb();

        // §12g GAP-2: Try to resolve actual ceiling Z from compiled ARC output.
        // The metadata default (2700mm residential) may not match the actual gypsum board Z.
        double resolvedCeilingZ = roomAabb[5]; // default: room AABB maxZ
        if (shimMatcher != null) {
            // ShimMatcher has ARC hosts loaded — find IfcCovering in this room's column
            Double arcCeilingZ = shimMatcher.findCeilingZ(
                roomAabb[0], roomAabb[1], roomAabb[2], roomAabb[3], roomAabb[4]);
            if (arcCeilingZ != null) {
                double oldZ = resolvedCeilingZ;
                resolvedCeilingZ = arcCeilingZ;
                // Rebuild room AABB with actual ceiling Z
                roomAabb = new double[]{
                    roomAabb[0], roomAabb[1],
                    roomAabb[2], roomAabb[3],
                    roomAabb[4], resolvedCeilingZ
                };
                totalArcSnaps++;
                BIMLogger.info("GENERATIVE",
                    "CEILING_ARC {} arcZ={:.3f}m metadataZ={:.3f}m delta={:.1f}mm — using ARC surface",
                    room.childBomId(), resolvedCeilingZ, oldZ, (resolvedCeilingZ - oldZ) * 1000);
            } else {
                totalArcMisses++;
                BIMLogger.info("GENERATIVE",
                    "CEILING_ARC {} — no ARC IfcCovering found in room column, using metadata Z={:.3f}m",
                    room.childBomId(), resolvedCeilingZ);
            }
        }

        // §12g GAP-1: Collect furniture AABBs from placements added during child walk
        List<double[]> furnitureBoxes = new ArrayList<>();
        if (furnitureStartIndex >= 0) {
            for (int i = furnitureStartIndex; i < placements.size(); i++) {
                PlacementLoader.Placement fp = placements.get(i);
                furnitureBoxes.add(new double[]{
                    fp.minX(), fp.maxX(), fp.minY(), fp.maxY(), fp.minZ(), fp.maxZ()
                });
            }
        }
        furnitureStartIndex = -1;

        // §12g GAP-7: Detect rooms where AABB is furniture extent, not room footprint.
        // When width or depth < 1m, collision check is meaningless — skip it.
        double roomW = roomAabb[1] - roomAabb[0];
        double roomD = roomAabb[3] - roomAabb[2];
        boolean roomTooNarrow = roomW < 1.0 || roomD < 1.0;
        if (roomTooNarrow) {
            totalNarrowRooms++;
            BIMLogger.warn("GENERATIVE",
                "ROOM_NARROW {} dims=({:.3f}×{:.3f})m — SET BOM AABB is furniture extent, not room footprint. Skipping collision check.",
                room.childBomId(), roomW, roomD);
            furnitureBoxes.clear(); // disable collision check
        }

        List<MEPDevicePlacer.DevicePlacement> devices =
            MEPDevicePlacer.placeDevices(erpConn, room.spaceType(), roomAabb, mepOrderQty, room.childBomId());

        BIMLogger.info("GENERATIVE",
            "ROOM_PLACE {} type={} storey={} devices={} furniture={} roomZ=[{:.3f}→{:.3f}]",
            room.childBomId(), room.spaceType(), room.storey(),
            devices.size(), furnitureBoxes.size(), roomAabb[4], roomAabb[5]);

        int collisionShifts = 0;
        int collisionConflicts = 0;
        // Track per-position device count to offset co-located devices (avoid P05 same-centroid).
        // Key: rounded position string, Value: count at that position.
        Map<String, Integer> positionCount = new HashMap<>();

        for (MEPDevicePlacer.DevicePlacement dp : devices) {
            Discipline disc = resolveDeviceDiscipline(erpConn, dp.deviceId(), dp.anchorEnd());

            // §12g GAP-3: Snap to ARC host surface via ShimMatcher if available
            double[] pos = dp.position().clone();
            String snapVerdict = "RAW";
            if (shimMatcher != null) {
                String hostClass = dp.hostSurface() != null ? dp.hostSurface() : "IfcCovering";
                Double adjZ = shimMatcher.matchHostZ(hostClass, dp.placementRule(), pos[0], pos[1], pos[2],
                    roomAabb[0], roomAabb[1], roomAabb[2], roomAabb[3]);
                if (adjZ != null) {
                    double oldZ = pos[2];
                    pos[2] = adjZ;
                    snapVerdict = String.format("SNAP dz=%.1fmm", (adjZ - oldZ) * 1000);
                }
            }

            // §12e: Furniture collision check — shift along wall if overlapping
            double hw = 0.05, hd = 0.05, hh = 0.05;
            double[] prodDims = SpaceScheduleDAO.getProductDimensions(erpConn, dp.deviceId());
            if (prodDims != null) {
                hw = prodDims[0] / 2.0; hd = prodDims[1] / 2.0; hh = prodDims[2] / 2.0;
            }
            double[] deviceBox = {
                pos[0] - hw, pos[0] + hw, pos[1] - hd, pos[1] + hd, pos[2] - hh, pos[2] + hh
            };
            boolean collides = false;
            for (double[] fb : furnitureBoxes) {
                if (aabbOverlap(deviceBox, fb)) {
                    collides = true;
                    break;
                }
            }
            if (collides) {
                // Try shifting in 4 directions: +X, -X, +Y, -Y (room-axis-aligned)
                double shiftStep = hw * 2 + 0.1; // device width + 100mm clearance
                boolean resolved = false;
                double[][] directions = {{1,0},{-1,0},{0,1},{0,-1}};
                outer:
                for (double[] dir : directions) {
                    for (int attempt = 1; attempt <= 5; attempt++) {
                        double shift = shiftStep * attempt;
                        double tryX = pos[0] + dir[0] * shift;
                        double tryY = pos[1] + dir[1] * shift;
                        // Bounds check — must stay inside room
                        if (tryX - hw < roomAabb[0] || tryX + hw > roomAabb[1]) continue;
                        if (tryY - hd < roomAabb[2] || tryY + hd > roomAabb[3]) continue;
                        double[] tryBox = {
                            tryX - hw, tryX + hw, tryY - hd, tryY + hd, pos[2] - hh, pos[2] + hh
                        };
                        boolean stillCollides = false;
                        for (double[] fb : furnitureBoxes) {
                            if (aabbOverlap(tryBox, fb)) { stillCollides = true; break; }
                        }
                        if (!stillCollides) {
                            BIMLogger.info("GENERATIVE",
                                "  COLLISION_SHIFT {} from ({:.3f},{:.3f}) → ({:.3f},{:.3f}) dir=({:.0f},{:.0f}) attempt={}",
                                dp.deviceId(), pos[0], pos[1], tryX, tryY, dir[0], dir[1], attempt);
                            pos[0] = tryX; pos[1] = tryY;
                            collisionShifts++;
                            resolved = true;
                            break outer;
                        }
                    }
                }
                if (!resolved) {
                    // Last resort: try opposite wall (flip to other side of room)
                    double flipX = roomAabb[0] + roomAabb[1] - pos[0]; // mirror X within room
                    double flipY = roomAabb[2] + roomAabb[3] - pos[1]; // mirror Y within room
                    double[][] flipCandidates = {{flipX, pos[1]}, {pos[0], flipY}, {flipX, flipY}};
                    for (double[] fc : flipCandidates) {
                        if (fc[0] - hw < roomAabb[0] || fc[0] + hw > roomAabb[1]) continue;
                        if (fc[1] - hd < roomAabb[2] || fc[1] + hd > roomAabb[3]) continue;
                        double[] flipBox = {
                            fc[0] - hw, fc[0] + hw, fc[1] - hd, fc[1] + hd, pos[2] - hh, pos[2] + hh
                        };
                        boolean flipCollides = false;
                        for (double[] fb : furnitureBoxes) {
                            if (aabbOverlap(flipBox, fb)) { flipCollides = true; break; }
                        }
                        if (!flipCollides) {
                            BIMLogger.info("GENERATIVE",
                                "  COLLISION_FLIP {} from ({:.3f},{:.3f}) → ({:.3f},{:.3f}) — opposite wall",
                                dp.deviceId(), pos[0], pos[1], fc[0], fc[1]);
                            pos[0] = fc[0]; pos[1] = fc[1];
                            collisionShifts++;
                            resolved = true;
                            break;
                        }
                    }
                }
                if (!resolved) {
                    // Log which furniture it collides with for next-session diagnosis
                    for (double[] fb : furnitureBoxes) {
                        if (aabbOverlap(deviceBox, fb)) {
                            BIMLogger.warn("GENERATIVE",
                                "  COLLISION_DETAIL {} at ({:.3f},{:.3f},{:.3f}) overlaps furniture [{:.3f}→{:.3f},{:.3f}→{:.3f},{:.3f}→{:.3f}]",
                                dp.deviceId(), pos[0], pos[1], pos[2],
                                fb[0], fb[1], fb[2], fb[3], fb[4], fb[5]);
                            break; // log first collision only
                        }
                    }
                    collisionConflicts++;
                    BIMLogger.warn("GENERATIVE",
                        "  COLLISION_CONFLICT {} at ({:.3f},{:.3f},{:.3f}) — no free zone in 4 dirs + flip",
                        dp.deviceId(), pos[0], pos[1], pos[2]);
                }
            }

            // Containment check — §12g GAP-8: 5mm Z tolerance for floor snap rounding
            boolean inX = pos[0] >= roomAabb[0] && pos[0] <= roomAabb[1];
            boolean inY = pos[1] >= roomAabb[2] && pos[1] <= roomAabb[3];
            boolean inZ = pos[2] >= roomAabb[4] - 0.005 && pos[2] <= roomAabb[5] + 0.005;
            String verdict = (inX && inY && inZ) ? "IN" : "OUT";
            if (!inX || !inY || !inZ) {
                generativeBreachCount++;
                BIMLogger.warn("GENERATIVE",
                    "  BREACH {} pos=({:.3f},{:.3f},{:.3f}) room=[{:.3f}→{:.3f}, {:.3f}→{:.3f}, {:.3f}→{:.3f}] X={} Y={} Z={}",
                    dp.deviceId(), pos[0], pos[1], pos[2],
                    roomAabb[0], roomAabb[1], roomAabb[2], roomAabb[3], roomAabb[4], roomAabb[5],
                    inX ? "OK" : "OUT", inY ? "OK" : "OUT", inZ ? "OK" : "OUT");
            }

            // §12a.4-6: Facing direction + standoff offset
            double facing = facingDirection(dp.placementRule());
            double standoff = standoffOffset(dp.hostSurface());

            BIMLogger.info("GENERATIVE",
                "  PLACE {} rule={} anchor={} disc={} pos=({:.3f},{:.3f},{:.3f}) {} {} facing={:.3f}rad standoff={:.3f}m source=ad_placement_offset.{}",
                dp.deviceId(), dp.placementRule(), dp.anchorEnd(), disc,
                pos[0], pos[1], pos[2], verdict, snapVerdict, facing, standoff, dp.placementRule());

            if (prodDims != null) {
                BIMLogger.fine("GENERATIVE",
                    "  AABB {} dims=({:.3f},{:.3f},{:.3f})m from M_Product",
                    dp.deviceId(), prodDims[0], prodDims[1], prodDims[2]);
            }

            // §12a.4: Create phantom SHIM on target surface — IfcVirtualElement, not rendered.
            // Shim carries host IFC class + surface info; device is child of shim.
            String hostIfc = hostIfcClass(dp.hostSurface());
            String shimDiscMount = (disc != null ? disc.name() : "MEP") + "_"
                + hostIfc + "_"
                + (dp.hostSurface() != null ? dp.hostSurface() : "WALL") + "_SHIM";

            // §12a.4c + §12a.5a: Shim ON wall surface, device offset along facing axis
            double[] shimDev = shimAndDevicePositions(
                dp.placementRule(), dp.hostSurface(), pos, roomAabb, standoff);
            double shimX = shimDev[0], shimY = shimDev[1], shimZ = shimDev[2];
            double devX = shimDev[3], devY = shimDev[4], devZ = shimDev[5];

            // Multiple devices sharing the same placement_rule get identical positions.
            // Offset co-located devices by 100mm along X to avoid P05 same-centroid.
            String posKey = String.format("%.2f_%.2f_%.2f", devX, devY, devZ);
            int posIdx = positionCount.merge(posKey, 1, Integer::sum) - 1;
            if (posIdx > 0) {
                double colocOffset = posIdx * 0.100;
                shimX += colocOffset; devX += colocOffset;
            }

            String shimRef = room.childBomId() + "_SHIM_" + dp.deviceId() + "_" + (++ordinalCounter);
            PlacementLoader.Placement shim = new PlacementLoader.Placement(
                buildingType,
                room.storey(),
                "IfcVirtualElement",
                shimRef,
                ordinalCounter,
                shimX - 0.001, shimX + 0.001,  // phantom: 2mm cube at wall surface
                shimY - 0.001, shimY + 0.001,
                shimZ - 0.001, shimZ + 0.001,
                null,
                disc,
                null, null,
                shimDiscMount,       // familyRef = "SP_IfcWallStandardCase_WALL_SHIM" etc.
                shimDiscMount,       // productId = same
                ElementIdentity.generated(shimRef, ""),
                facing               // shim carries the wall normal as rotationZ
            );
            placements.add(shim);
            // Shim is phantom (IfcVirtualElement) — NOT written to output DB, NOT counted in G1-COUNT.

            String deviceRef = room.childBomId() + "_" + dp.deviceId() + "_" + (++ordinalCounter);
            PlacementLoader.Placement p = new PlacementLoader.Placement(
                buildingType,
                room.storey(),
                "IfcFlowTerminal",
                deviceRef,
                ordinalCounter,
                devX - hw, devX + hw,
                devY - hd, devY + hd,
                devZ - hh, devZ + hh,
                null,
                disc,
                null, null,
                dp.deviceId(),
                dp.deviceId(),
                ElementIdentity.generated(deviceRef, ""),
                facing
            );
            placements.add(p);
            generativeDeviceCount++;
            placeDeviceCount++;
            generativeDeviceCounts.merge(dp.deviceId(), 1, Integer::sum);

            // GeoProofRecord for generative devices (uses device position, not shim position)
            {
                // Use ARC-adjusted room AABB dimensions (not original PendingGenerativeRoom dims)
                double[] roomDims = {roomAabb[1] - roomAabb[0], roomAabb[3] - roomAabb[2], roomAabb[5] - roomAabb[4]};
                boolean lmpOk = inX && inY && inZ;
                boolean envOk = (devX + hw) <= roomAabb[1] + 0.001
                             && (devY + hd) <= roomAabb[3] + 0.001
                             && (devZ + hh) <= roomAabb[5] + 0.001;
                String chain = room.childBomId() + "→SHIM→" + dp.deviceId();
                proofRecords.add(new GeoProofRecord(
                    p.elementRef(), dp.deviceId(), chain,
                    null,
                    new double[]{roomAabb[0], roomAabb[2], roomAabb[4]},
                    facing,
                    new double[]{devX - roomAabb[0], devY - roomAabb[2], devZ - roomAabb[4]},
                    new double[]{devX - roomAabb[0], devY - roomAabb[2], devZ - roomAabb[4]},
                    new double[]{devX, devY, devZ},
                    new double[]{devX - hw, devY - hd, devZ - hh},
                    new double[]{hw, hd, hh},
                    roomDims,
                    false,
                    lmpOk, envOk
                ));
            }
        }
        if (!devices.isEmpty()) {
            generativeRoomCounts.put(room.childBomId(), devices.size());
        }
        totalCollisionShifts += collisionShifts;
        totalCollisionConflicts += collisionConflicts;
        // §12g hardened summary per room — one line, all diagnostics
        BIMLogger.info("GENERATIVE",
            "ROOM_DONE {} type={} devices={} shifts={} conflicts={} breaches={} ceilingZ={:.3f}m",
            room.childBomId(), room.spaceType(), devices.size(),
            collisionShifts, collisionConflicts, generativeBreachCount, resolvedCeilingZ);
    }

    // §12a.4: Facing direction from placement_rule (§12g GAP-4 table).
    // Returns rotationZ in radians: 0 = faces -Y (into room), π = faces +Y, π/2 = faces -X, -π/2 = faces +X.
    // CEILING_* and FLOOR_* always 0 (pendant/upright, no horizontal rotation).
    // Implementing DISC_VALIDATION_DB_SRS.md §12g GAP-4 — Witness: W-SHIM-DEVICE
    private static double facingDirection(String placementRule) {
        if (placementRule == null) return 0.0;
        // yRef=MAX rules (back wall): face -Y into room = 0
        // yRef=MIN rules (entry wall): face +Y into room = π
        // xRef=MIN rules (side wall): face +X into room = -π/2
        boolean isMaxY = "WALL_BACK".equals(placementRule)
                      || "WALL_COOKER".equals(placementRule)
                      || "COUNTER_BACK".equals(placementRule);
        boolean isMinY = "WALL_ENTRY".equals(placementRule);
        if (isMaxY) return 0.0;
        if (isMinY) return Math.PI;
        if (placementRule.startsWith("WALL_") || placementRule.startsWith("COUNTER_")) {
            return -Math.PI / 2;  // xRef=MIN wall: face +X into room
        }
        return 0.0; // CEILING_*, FLOOR_*, AUTO
    }

    // §12a.4-5: Standoff offset (metres) from host surface.
    // WALL devices: 5mm (device sits just off the wall).
    // CEILING devices: 50mm (pendant hang gap).
    // FLOOR devices: 0mm (sitting on surface).
    private static double standoffOffset(String hostSurface) {
        if (hostSurface == null) return 0.005;
        return switch (hostSurface) {
            case "WALL"    -> 0.005;
            case "CEILING" -> 0.050;
            case "FLOOR"   -> 0.0;
            default        -> 0.005;
        };
    }

    // §12a.4a: Map host surface to IFC class for shim familyRef.
    // Implementing DISC_VALIDATION_DB_SRS.md §12a.4a — Witness: W-SHIM-DEVICE
    private static String hostIfcClass(String hostSurface) {
        if (hostSurface == null) return "IfcWallStandardCase";
        return switch (hostSurface) {
            case "WALL"    -> "IfcWallStandardCase";
            case "CEILING" -> "IfcCovering";
            case "FLOOR"   -> "IfcSlab";
            default        -> "IfcWallStandardCase";
        };
    }

    // §12a.4c: Compute shim position ON the wall/ceiling/floor surface.
    // §12a.5a: Compute device position offset from shim along facing direction.
    // Uses hostSurface + ad_placement_offset xRef/yRef convention to determine which
    // room boundary the shim sits on. roomAabb = [minX, maxX, minY, maxY, minZ, maxZ].
    // Returns [shimX, shimY, shimZ, devX, devY, devZ].
    // Implementing DISC_VALIDATION_DB_SRS.md §12a.4c, §12a.5a — Witness: W-SHIM-DEVICE
    //
    // Wall mapping (from ad_placement_offset xRef/yRef):
    //   yRef=MAX → back wall at roomMaxY:  WALL_BACK, WALL_COOKER, COUNTER_BACK
    //   yRef=MIN → entry wall at roomMinY: WALL_ENTRY
    //   xRef=MIN → side wall at roomMinX:  WALL_SIDE, WALL_SINK, WALL_FLOOR, WALL_HIGH, WALL_SPACED, COUNTER_SINK
    //   CEILING  → ceiling at roomMaxZ
    //   FLOOR    → floor at roomMinZ
    private static double[] shimAndDevicePositions(String placementRule, String hostSurface,
                                                    double[] pos, double[] roomAabb, double standoff) {
        double shimX = pos[0], shimY = pos[1], shimZ = pos[2];
        double devX = pos[0], devY = pos[1], devZ = pos[2];

        if ("CEILING".equals(hostSurface)) {
            shimZ = roomAabb[5];               // shim ON ceiling
            devZ = shimZ - standoff;           // pendant hangs below
        } else if ("FLOOR".equals(hostSurface)) {
            shimZ = roomAabb[4];               // shim ON floor
            devZ = shimZ + standoff;           // device on surface (standoff=0)
        } else if (placementRule != null && placementRule.startsWith("WALL_") || placementRule != null && placementRule.startsWith("COUNTER_")) {
            // Determine which wall from the xRef/yRef convention in ad_placement_offset.
            // yRef=MAX rules: device is on the back (maxY) wall.
            // yRef=MIN rules: device is on the entry (minY) wall.
            // xRef=MIN rules: device is on the side (minX) wall.
            boolean isMaxY = "WALL_BACK".equals(placementRule)
                          || "WALL_COOKER".equals(placementRule)
                          || "COUNTER_BACK".equals(placementRule);
            boolean isMinY = "WALL_ENTRY".equals(placementRule);

            if (isMaxY) {
                shimY = roomAabb[3];           // shim ON maxY wall
                devY = shimY - standoff;       // device offset into room (-Y)
            } else if (isMinY) {
                shimY = roomAabb[2];           // shim ON minY wall
                devY = shimY + standoff;       // device offset into room (+Y)
            } else {
                // All other WALL_*/COUNTER_* rules: xRef=MIN → minX wall
                shimX = roomAabb[0];           // shim ON minX wall
                devX = shimX + standoff;       // device offset into room (+X)
            }
        }
        // AUTO/unknown: keep pos as-is (center placement, no wall snap)
        return new double[]{shimX, shimY, shimZ, devX, devY, devZ};
    }

    /** AABB overlap test (6-element arrays: minX,maxX,minY,maxY,minZ,maxZ). */
    private static boolean aabbOverlap(double[] a, double[] b) {
        return a[0] < b[1] && a[1] > b[0]   // X overlap
            && a[2] < b[3] && a[3] > b[2]   // Y overlap
            && a[4] < b[5] && a[5] > b[4];  // Z overlap
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

        // Apply cumulative mirror reflection OR rotation to leaf offsets
        double leafDx = line.getDx();
        double leafDy = line.getDy();
        double leafDz = line.getDz();
        String cumMirror = mirrorAxisStack.isEmpty() ? "" : mirrorAxisStack.peek();
        double cumRot = rotationStack.isEmpty() ? 0.0 : rotationStack.peek();
        if (!cumMirror.isEmpty()) {
            double rx = leafDx, ry = leafDy;
            switch (cumMirror) {
                case "X" -> { rx = -leafDx; ry = -leafDy; }
                case "Y" -> { rx = -leafDx; ry = -leafDy; }
            }
            if (BIMLogger.geoMatch(line.getChildProductId())) {
                BIMLogger.geo("TACK", "  MIRROR:{}: leaf=({:.4f},{:.4f}) → reflected=({:.4f},{:.4f})",
                    cumMirror, line.getDx(), line.getDy(), rx, ry);
            }
            leafDx = rx;
            leafDy = ry;
        } else if (cumRot != 0.0) {
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
        // §6.12.2 §6: variable-length pieces are ONE instance at the specified length.
        // InterimWorkshop already recomputed half-extents above; qty=2345mm means 1 piece, not 2345 pieces.
        // Guard: qty_type=FIXED pieces keep their qty (a tee fitting with qty=2 means 2 fittings).
        int qty = (line.isLengthBased() && "VARIABLE".equals(line.getQtyType())) ? 1 : line.getQty();
        String verbRef = line.getVerbRef();
        double[][] offsets = expandVerb(verbRef, qty, leafDx, leafDy, leafDz);

        // Track verb usage for FINE logging
        if (verbRef == null || verbRef.isEmpty()) { placeCount++; }
        else if (verbRef.startsWith("CLUSTER:")) { clusterCount++; }
        else if (verbRef.startsWith("TILE:"))    { tileCount++; }
        else if (verbRef.startsWith("ROUTE:"))   { routeCount++; }
        else if (verbRef.startsWith("FRAME:"))   { frameCount++; }
        else if (verbRef.startsWith("SPRAY:"))   { sprayCount++; }
        else if (verbRef.startsWith("PLACE_DEVICE:")) { placeDeviceCount++; }
        else { otherVerbCount++; }

        // CP-1: Load MA (Material Allocation) GUIDs for identity-based matching.
        // For mirrored compositions: shared BOM line has MA qi=0 (A-side) and qi=1 (B-side).
        // Unit prefix "B_" → offset qi by 1 to pick B-side GUIDs.
        int maQiOffset = "B_".equals(currentUnitPrefix()) ? 1 : 0;
        String[] maGuids = loadMaGuids(line.getBomId(), line.getSequence(), qty, maQiOffset);

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
            // S145 fix: MIRROR:X is rot=π — flip both X and Y half-extents
            String leafMirror = mirrorAxisStack.isEmpty() ? "" : mirrorAxisStack.peek();
            double xySign = leafMirror.isEmpty() ? 1.0 : -1.0;
            double cx = anchor[0] + offsets[qi][0] + xySign * iHalfW;
            double cy = anchor[1] + offsets[qi][1] + xySign * iHalfD;
            double cz = anchor[2] + offsets[qi][2] + iHalfH;

            // Log 3+5: LEAF — proves centroid was computed by tack accumulation + shows GUID
            // (elementRef resolved below, so we log after GUID resolution)

            // Element identity: CP-1 MA guid > line ref > generated.
            // Implementing BBC.md §4.3 — Witness: W-GUID-1
            // MA (Material Allocation) carries per-instance IFC GUIDs for SpatialDiff.
            // Guard: only accept valid IFC GloballyUniqueId (22 chars, base64url+'$').
            // Invalid format = product name leaked into MA → reject, fall through.
            ElementIdentity identity;
            if (maGuids != null && qi < maGuids.length && maGuids[qi] != null
                    && IFC_GUID.matcher(maGuids[qi]).matches()) {
                identity = ElementIdentity.fromMA(maGuids[qi], unitPrefix);
            } else {
                String lineRef = line.getElementRef();
                if (lineRef != null && !lineRef.isEmpty() && IFC_GUID.matcher(lineRef).matches()) {
                    identity = ElementIdentity.fromLineRef(lineRef, unitPrefix);
                } else if (lineRef != null && !lineRef.isEmpty() && qty > 1) {
                    identity = ElementIdentity.generated(lineRef + ":" + qi, unitPrefix);
                } else if (lineRef != null && !lineRef.isEmpty()) {
                    identity = ElementIdentity.generated(lineRef, unitPrefix);
                } else {
                    identity = ElementIdentity.generated(
                        (product != null ? product.getProductId() : productId) + ":" + (++ordinalCounter),
                        unitPrefix);
                }
            }
            String elementRef = identity.prefixedRef();

            // S147: Log GUID resolution path for traceability diagnosis
            BIMLogger.fine("TACK", "GUID {} → {} base={} (ma={}, lineRef={}, qty={})",
                identity.guidSource(), elementRef, identity.baseGuid(),
                (maGuids != null && qi < maGuids.length) ? maGuids[qi] : "null",
                line.getElementRef(), qty);

            // Log 3+5: LEAF — tack math proof with transform state and actual AABB
            double minX = Math.min(cx - iHalfW, cx + iHalfW);
            double maxX = Math.max(cx - iHalfW, cx + iHalfW);
            double minY = Math.min(cy - iHalfD, cy + iHalfD);
            double maxY = Math.max(cy - iHalfD, cy + iHalfD);
            double minZ = cz - iHalfH;
            double maxZ = cz + iHalfH;
            if (BIMLogger.geoMatch(productId)) {
                BIMLogger.geo("TACK", "LEAF  {} guid={} {} anchor=({:.4f},{:.4f},{:.4f}) offset=({:.4f},{:.4f},{:.4f}) half=({:.4f},{:.4f},{:.4f}) sign={} → AABB X=[{:.3f},{:.3f}] Y=[{:.3f},{:.3f}] Z=[{:.3f},{:.3f}]",
                    productId, elementRef,
                    leafMirror.isEmpty() ? "IDENTITY" : "MIRROR:" + leafMirror,
                    anchor[0], anchor[1], anchor[2],
                    offsets[qi][0], offsets[qi][1], offsets[qi][2],
                    iHalfW, iHalfD, iHalfH,
                    leafMirror.isEmpty() ? "+1" : "-1",
                    minX, maxX, minY, maxY, minZ, maxZ);
                // Log 3b: CHAIN — full ancestor path at LEAF (proves provenance)
                BIMLogger.geo("TACK", "  CHAIN {}: {}", productId,
                    formatAncestorChain(parentBomIdStack, productId));
                // Log 3c: DIMS — explicit W×D×H in mm (proves no truncation/swap)
                BIMLogger.geo("TACK", "  DIMS  {}: {:.0f}×{:.0f}×{:.0f} mm (alloc={:.0f}×{:.0f}×{:.0f})",
                    productId,
                    iHalfW * 2000, iHalfD * 2000, iHalfH * 2000,
                    line.getAllocatedWidthMmExact(), line.getAllocatedDepthMmExact(), line.getAllocatedHeightMmExact());
                // Log 3d: CONTAIN — is LEAF inside parent AABB? (mirror-aware)
                double cumRotForCheck = rotationStack.isEmpty() ? 0.0 : rotationStack.peek();
                logContainmentCheck(productId, minX, minY, minZ, maxX, maxY, maxZ, anchor, cumRotForCheck, leafMirror);
            }

            // S144: Build GeoProofRecord — structured proof chain per element
            {
                double[] outLBD = {minX, minY, minZ};
                double[] outCentroid = {cx, cy, cz};
                double[] rawOffset = {line.getDx(), line.getDy(), line.getDz()};
                double[] rotOffset = {offsets[qi][0], offsets[qi][1], offsets[qi][2]};
                double[] halfExts = {iHalfW, iHalfD, iHalfH};
                double[] pAnchor = {anchor[0], anchor[1], anchor[2]};
                double[] pAABB = parentAABBStack.isEmpty() ? null : parentAABBStack.peek();
                double cumRotVal = rotationStack.isEmpty() ? 0.0 : rotationStack.peek();
                String chain = formatAncestorChain(parentBomIdStack, productId);

                // LMP check: outputLBD >= parentAnchor in parent's local frame.
                // Apply inverse rotation to offset before checking (same as logContainmentCheck).
                double dx0 = outLBD[0] - pAnchor[0];
                double dy0 = outLBD[1] - pAnchor[1];
                double dz0 = outLBD[2] - pAnchor[2];
                double localDx0 = dx0, localDy0 = dy0;
                if (Math.abs(cumRotVal) > 0.01) {
                    double cosInv = Math.cos(-cumRotVal);
                    double sinInv = Math.sin(-cumRotVal);
                    localDx0 = dx0 * cosInv - dy0 * sinInv;
                    localDy0 = dx0 * sinInv + dy0 * cosInv;
                }
                // §6.12.2: MEP pieces route both directions from shim — negative offsets are valid.
                // Exempt from LMP containment; envelope check still applies.
                // Detection: mepBomDepth>0 (nested walk) OR owning BOM is MEP/MEP_RECIPE (flat walk).
                String ownerBomType = ctx.bom() != null ? ctx.bom().getBomType() : null;
                boolean isMep = mepBomDepth > 0
                        || (ownerBomType != null && ownerBomType.startsWith("MEP"));
                boolean lmp = isMep
                        || (localDx0 >= -0.001 && localDy0 >= -0.001 && dz0 >= -0.001);

                // Envelope check: childMAX <= parentMAX (using actual min/max, mirror-safe)
                boolean envelope;
                if (pAABB == null || (pAABB[0] == 0 && pAABB[1] == 0 && pAABB[2] == 0)) {
                    envelope = true;  // unknown parent dims — can't check, assume OK
                } else {
                    double childMaxX = maxX;
                    double childMaxY = maxY;
                    double childMaxZ = maxZ;
                    double parentMaxX = pAnchor[0] + pAABB[0];
                    double parentMaxY = pAnchor[1] + pAABB[1];
                    double parentMaxZ = pAnchor[2] + pAABB[2];
                    envelope = childMaxX <= parentMaxX + 0.001
                            && childMaxY <= parentMaxY + 0.001
                            && childMaxZ <= parentMaxZ + 0.001;
                }

                proofRecords.add(new GeoProofRecord(
                    elementRef, productId, chain,
                    null,  // inputLBD — populated by proof stage (§6.12.1 isolation)
                    pAnchor, cumRotVal, rawOffset, rotOffset,
                    outCentroid, outLBD, halfExts,
                    pAABB != null ? new double[]{pAABB[0], pAABB[1], pAABB[2]} : null,
                    false,  // roundTrip — populated by proof stage
                    lmp, envelope
                ));
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

            // S147: Combine explicit rotation + mirror-equivalent rotation for LOD binding.
            // MIRROR:X = rot=π (S145). The AABB is computed via sign negation (mirrorAxisStack),
            // but the LOD mesh needs the actual rotation angle to render correctly.
            double cumRotVal = rotationStack.isEmpty() ? 0.0 : rotationStack.peek();
            if (!leafMirror.isEmpty()) {
                cumRotVal += Math.PI;  // MIRROR:X ≡ rot=π for LOD mesh orientation
            }
            PlacementLoader.Placement p = new PlacementLoader.Placement(
                buildingType,
                storey,
                ifcClass,
                elementRef,
                ordinal,
                minX, maxX,
                minY, maxY,
                jitteredCz - iHalfH, jitteredCz + iHalfH,
                line.getOrientation(),
                resolveDiscipline(ifcClass, ctx.discipline()),
                materialName,
                materialRgba,
                line.getElementRef() != null ? line.getElementRef() : productId,  // familyRef — raw IFC name for fidelity; abstract productId for GENERATIVE
                productId,
                identity,  // BBC.md §4.3 — ElementIdentity for output DB traceability
                cumRotVal  // S147: cumulative rotation from BOM tree for LOD binding
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
        } else if (verbRef.startsWith("LINE:")) {
            return expandLine(verbRef, qty, originDx, originDy, originDz);
        } else if (verbRef.startsWith("LINE_MULTI:")) {
            return expandLineMulti(verbRef, qty, originDx, originDy, originDz);
        } else if (verbRef.startsWith("PLACE_DEVICE:")) {
            // PLACE_DEVICE:rule — position is computed by MEPDevicePlacer from room AABB + offset.
            // When this verb appears on an explicit BOM line (not generative), the position is
            // already encoded as origin — the verb is a marker, not a geometry expander.
            // Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 — Witness: W-DEVICE-PLACE
            double[][] result = new double[qty][3];
            for (int i = 0; i < qty; i++) {
                result[i] = new double[]{originDx, originDy, originDz};
            }
            return result;
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
     * LINE:axis:pos1,pos2,...,posN — explicit positions along one axis.
     * Example: LINE:X:0.012,1.772,2.772,3.772 → 4 positions along X.
     * Y and Z from origin (or the non-specified axes).
     * Implementing BBC.md §4.3 — S147 DX furniture verb.
     */
    private static double[][] expandLine(String verbRef, int qty,
                                         double originDx, double originDy, double originDz) {
        // LINE:X:0.012,1.772,2.772,3.772
        String data = verbRef.substring(5);  // skip "LINE:"
        int colonIdx = data.indexOf(':');
        String axis = data.substring(0, colonIdx);
        String[] posStrs = data.substring(colonIdx + 1).split(",");
        int n = posStrs.length;
        double[][] result = new double[n][3];
        for (int i = 0; i < n; i++) {
            double pos = Double.parseDouble(posStrs[i].trim());
            result[i] = switch (axis) {
                case "X" -> new double[]{pos, originDy, originDz};
                case "Y" -> new double[]{originDx, pos, originDz};
                case "Z" -> new double[]{originDx, originDy, pos};
                default -> new double[]{originDx, originDy, originDz};
            };
        }
        return result;
    }

    /**
     * LINE_MULTI:axis:pos1,...;axis:pos1,... — multiple groups of explicit positions.
     * Example: LINE_MULTI:X:0.76,1.76,2.76,3.76;X:0.00,1.00,2.00,3.76
     *   → 4+4=8 positions, first group along X, second group along X.
     * Implementing BBC.md §4.3 — S147 DX furniture verb.
     */
    private static double[][] expandLineMulti(String verbRef, int qty,
                                              double originDx, double originDy, double originDz) {
        // LINE_MULTI:X:0.76,1.76;X:0.00,1.00
        String data = verbRef.substring(11);  // skip "LINE_MULTI:"
        String[] groups = data.split(";");
        // Count total positions
        java.util.List<double[]> all = new java.util.ArrayList<>();
        for (String group : groups) {
            int colonIdx = group.indexOf(':');
            String axis = group.substring(0, colonIdx);
            String[] posStrs = group.substring(colonIdx + 1).split(",");
            for (String posStr : posStrs) {
                double pos = Double.parseDouble(posStr.trim());
                all.add(switch (axis) {
                    case "X" -> new double[]{pos, originDy, originDz};
                    case "Y" -> new double[]{originDx, pos, originDz};
                    case "Z" -> new double[]{originDx, originDy, pos};
                    default -> new double[]{originDx, originDy, originDz};
                });
            }
        }
        return all.toArray(new double[0][]);
    }

    /**
     * CP-1: Load Material Allocation GUIDs for a BOM line.
     * m_bom_line_ma stores per-instance IFC GUIDs (iDempiere M_InOutLineMA pattern).
     * Returns null if no MA rows exist (SH/DX or old BOM DBs).
     */
    private String[] loadMaGuids(String bomId, int sequence, int qty, int qiOffset) {
        try (java.sql.PreparedStatement ps = bomConn.prepareStatement(
                "SELECT qi, guid FROM m_bom_line_ma WHERE bom_id = ? AND sequence = ? ORDER BY qi")) {
            ps.setString(1, bomId);
            ps.setInt(2, sequence);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                String[] guids = new String[qty];
                boolean hasAny = false;
                while (rs.next()) {
                    int qi = rs.getInt(1);
                    // For mirrored units: qiOffset shifts into B-side range (qi=1 → slot 0)
                    int slot = qi - qiOffset;
                    if (slot >= 0 && slot < qty) {
                        guids[slot] = rs.getString(2);
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

    /** Log AABB containment: is the placed LEAF inside the parent anchor region?
     *  Mirror-aware: under MIRROR:X (rot=π), child extends negatively from anchor,
     *  so containment uses abs(offset) — distance from anchor matters, not direction. */
    private void logContainmentCheck(String productId,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double[] parentAnchor, double cumRot, String mirror) {
        boolean mirrored = !mirror.isEmpty();
        // Under mirror: child can extend either direction from anchor.
        // Check that the child AABB overlaps the parent region.
        // Without mirror: LBD must be >= parent anchor (child extends positively).
        double dx, dy, dz;
        if (mirrored) {
            // For rot=π: the child's max corner should be near the anchor,
            // and the min corner extends away. Use absolute offset from anchor.
            dx = Math.min(Math.abs(minX - parentAnchor[0]), Math.abs(maxX - parentAnchor[0]));
            dy = Math.min(Math.abs(minY - parentAnchor[1]), Math.abs(maxY - parentAnchor[1]));
            dz = minZ - parentAnchor[2];
        } else {
            dx = minX - parentAnchor[0];
            dy = minY - parentAnchor[1];
            dz = minZ - parentAnchor[2];
        }
        // Apply inverse rotation to get offset in parent's local frame
        double localDx = dx, localDy = dy;
        boolean rotated = Math.abs(cumRot) > 0.01;
        if (rotated && !mirrored) {
            double cos = Math.cos(-cumRot);
            double sin = Math.sin(-cumRot);
            localDx = dx * cos - dy * sin;
            localDy = dx * sin + dy * cos;
        }
        boolean contained = localDx >= -0.001 && localDy >= -0.001 && dz >= -0.001;
        String tag = mirrored ? " [MIRROR:" + mirror + "]" : (rotated ? " [ROT=" + String.format("%.2f", cumRot) + "]" : "");
        if (contained) {
            BIMLogger.geo("TACK", "  CONTAIN {}: OK dist=({:.4f},{:.4f},{:.4f})m from parent{}",
                productId, localDx, localDy, dz, tag);
        } else {
            BIMLogger.geo("TACK", "  CONTAIN {}: OVERSHOOT dist=({:.4f},{:.4f},{:.4f})m — LMP candidate{}",
                productId, localDx, localDy, dz, tag);
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
     * Supports numeric values (e.g. "0", "3.141592653589793"), MIRROR:X/Y/Z, and null/empty → 0.
     * MIRROR rules return 0.0 radians — reflection handled separately via mirrorAxisStack.
     */
    private static double parseRotation(MBOMLine line) {
        if (line == null) return 0.0;
        String rule = line.getRotationRule();
        if (rule == null || rule.isEmpty() || "0".equals(rule)) return 0.0;
        if (rule.startsWith("MIRROR:")) return 0.0;
        try {
            return Double.parseDouble(rule);
        } catch (NumberFormatException e) {
            // Implementing AUDIT_20260402.txt §2 Gap B — silent rotation fallback made visible
            String productRef = (line != null) ? String.valueOf(line.getBomLineId()) : "?";
            BIMLogger.geo("TACK", "ROT_PARSE_ERR {} rule='{}' — fallback 0.0rad", productRef, rule);
            return 0.0;
        }
    }

    /** Parse mirror axis from rotation_rule. Returns "X"/"Y"/"Z" for MIRROR:X/Y/Z, null otherwise. */
    private static String parseMirrorAxis(MBOMLine line) {
        if (line == null) return null;
        String rule = line.getRotationRule();
        if (rule != null && rule.startsWith("MIRROR:")) return rule.substring(7);
        return null;
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

    /**
     * Resolve Discipline from MEP anchor type.
     * Reads connects_to from ad_assembly_connector for accurate discipline.
     * Fallback: anchorEnd mapping (RISER→CW, STACK→SP, PANEL→ELEC).
     * Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 §12d — Witness: W-DISC-RESOLVE
     */
    private static Discipline resolveDeviceDiscipline(Connection erpConn, String deviceId, String anchorEnd) {
        // Primary: resolve from ad_assembly_connector.connects_to (DV047)
        if (erpConn != null && deviceId != null) {
            try (PreparedStatement ps = erpConn.prepareStatement(
                    "SELECT connects_to FROM ad_assembly_connector WHERE assembly_id = ? LIMIT 1")) {
                ps.setString(1, deviceId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String connectsTo = rs.getString(1);
                        if (connectsTo != null) {
                            return switch (connectsTo) {
                                case "ELEC_CONDUIT" -> Discipline.ELEC;
                                case "WATER_RISER" -> Discipline.CW;
                                case "PLUMBING_STACK" -> Discipline.SP;
                                case "FP_MAIN" -> Discipline.FP;
                                case "ACMV_DUCT" -> Discipline.ACMV;
                                default -> Discipline.ELEC;
                            };
                        }
                    }
                }
            } catch (SQLException ignored) {
                // Table may not exist — fall through to anchorEnd
            }
        }
        // Fallback: anchorEnd mapping
        if (anchorEnd == null) return Discipline.ELEC;
        return switch (anchorEnd) {
            case "RISER" -> Discipline.CW;
            case "STACK" -> Discipline.SP;
            case "PANEL" -> Discipline.ELEC;
            default -> Discipline.ELEC;
        };
    }

    // Implementing AUDIT_20260402.txt §4 — emitGeoSummary removed.
    // GEO = white-box only. Black-box correctness is owned by ExtractedGeometryTruthTest T3-ARC.
    // Do not add extraction-DB joins here. Forensic route confirmation via bim.geo.debug=true
    // is sufficient for DISC device positioning review.
}
