package com.bim.compiler.dsl;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.Vector3D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PhantomLayout — transient WMS Empty Storage record for one M_Locator during BOM resolution.
 *
 * <p>Mirrors the SAP/iDempiere WMS EmptyStorage concept: a locator (room zone) has
 * a fixed {@link #capacityMm()} along its {@link #hostAxis()}, and as children are
 * placed the filled extent grows and the {@link #nextAnchor()} pointer advances.
 *
 * <p>This object is <b>not persisted</b>. On completion, the caller writes a
 * spatial slot row (co_empty_space removed S74 — placement via M_BOM_Line dx/dy/dz).
 * WHERE concern lives in M_BOM_Line dx/dy/dz (MANIFESTO.md §Three Concerns).
 *
 * <p><b>Locator capacity conventions</b> (capacity_mm computation at createDraft() call time):
 * <ul>
 *   <li>NORTH_WALL / SOUTH_WALL → {@code room.max_x_mm - room.min_x_mm} (width along X)</li>
 *   <li>EAST_WALL  / WEST_WALL  → {@code room.max_y_mm - room.min_y_mm} (depth along Y)</li>
 *   <li>CENTRE → {@code Math.min(room_width_mm, room_depth_mm) / 2.0} (inner radius)</li>
 *   <li>FLOAT → explicit capacity from {@code m_attribute.dx/dy}</li>
 * </ul>
 *
 * <p>These conventions are implemented in {@code BOMTierResolver.initPhantomLayout()}.
 * They are stated here so that {@code createDraft()} always receives a fully-computed value
 * and never derives capacity internally.
 *
 * <p><b>What PhantomLayout does NOT check:</b>
 * <ul>
 *   <li>Depth clearance (front-to-back fit) — tracked separately via ad_assembly_manifest.</li>
 *   <li>Cross-locator collisions — each locator's PhantomLayout is independent.</li>
 *   <li>SURROUND layout strategy — radial placement; PhantomLayout is LINEAR only.</li>
 * </ul>
 *
 * <p><b>Usage pattern:</b>
 * <pre>
 *   // 1. Initialise for one locator (e.g. NORTH_WALL of LIVING_ROOM)
 *   PhantomLayout ph = PhantomLayout.forLocator(
 *       "LIVING_BOM_SH", "NORTH_WALL",
 *       6500.0,           // room width in mm
 *       nwCornerMeters,   // GPD origin: NW corner in world coordinates
 *       Vector3D.X_AXIS); // advance along +X (east)
 *
 *   // 2. Place fixed children in sequence order
 *   Place piano = Place.fixed("UPRIGHT_PIANO", "NORTH_WALL", 1, pianoBbox, ph.nextAnchor(), front, ph.hostAxis());
 *   ph.placeNext(piano);
 *
 *   Place buffer = Place.variance("SPACER_VAR", "NORTH_WALL", 2, ph.nextAnchor(), front, ph.hostAxis());
 *   ph.placeNext(buffer); // contributes 0mm; remainingMm() is the variance extent
 *
 *   Place sofa = Place.fixed("SOFA_3S", "NORTH_WALL", 3, sofaBbox, ph.nextAnchor(), front, ph.hostAxis());
 *   ph.placeNext(sofa);
 *
 *   // 3. Check GIC
 *   if (ph.isOverflow()) { throw new GICViolation(...); }
 *
 *   // 4. Write to wm_empty_storage_line (DocStatus CO)
 *   //    remainingMm() is the variance child's actual extent.
 * </pre>
 */
public final class PhantomLayout {

    private final String hostBomId;
    private final String locatorRef;
    private final double capacityMm;
    private final Vector3D hostAxis;
    private Point3D nextAnchor;
    private double filledMm;
    private final List<Place> places;

    private PhantomLayout(String hostBomId, String locatorRef,
                          double capacityMm, Point3D origin, Vector3D hostAxis) {
        this.hostBomId  = hostBomId;
        this.locatorRef = locatorRef;
        this.capacityMm = capacityMm;
        this.hostAxis   = hostAxis.normalize();
        this.nextAnchor = origin;
        this.filledMm   = 0.0;
        this.places     = new ArrayList<>();
    }

    /**
     * Create a PhantomLayout for a locator just starting BOM resolution.
     *
     * @param hostBomId   the BOM being resolved (e.g. "LIVING_BOM_SH")
     * @param locatorRef  zone label (NORTH_WALL, CENTRE, FLOAT…)
     * @param capacityMm  total locator extent along hostAxis in mm — caller must
     *                    compute from {@code ad_room_boundary} using the conventions
     *                    documented in this class's javadoc
     * @param origin      GPD start point (world meters) — locator origin corner
     * @param hostAxis    direction GPD advances (parallel to host wall; normalised)
     */
    public static PhantomLayout forLocator(String hostBomId, String locatorRef,
                                           double capacityMm, Point3D origin,
                                           Vector3D hostAxis) {
        return new PhantomLayout(hostBomId, locatorRef, capacityMm, origin, hostAxis);
    }

    // ── GPD walk ─────────────────────────────────────────────────────────────

    /**
     * Place the next child into this locator.
     *
     * <p>Advances {@link #nextAnchor()} and {@link #filledMm()} by the Place's
     * {@link Place#extentMm()}. Variance children (bbox=null) contribute 0mm —
     * the remaining capacity at locator completion is their effective extent.
     *
     * @param place the Place to append; must share this {@link #locatorRef()}
     */
    public void placeNext(Place place) {
        places.add(place);
        filledMm  += place.extentMm();
        nextAnchor = place.nextAnchor();
    }

    // ── State queries ─────────────────────────────────────────────────────────

    /** The BOM being resolved. */
    public String hostBomId()   { return hostBomId; }

    /** Zone label of this locator (NORTH_WALL, CENTRE, FLOAT…). */
    public String locatorRef()  { return locatorRef; }

    /** Total locator extent along hostAxis in mm. */
    public double capacityMm()  { return capacityMm; }

    /** Cumulative extent of placed fixed children in mm (variance children = 0). */
    public double filledMm()    { return filledMm; }

    /** Unit vector along which GPD advances (parallel to host wall). */
    public Vector3D hostAxis()  { return hostAxis; }

    /** Current GPD position in world meters — next available insertion point. */
    public Point3D nextAnchor() { return nextAnchor; }

    /**
     * Remaining locator capacity in mm.
     * <ul>
     *   <li>Positive → available space (variance child absorbs this)</li>
     *   <li>Zero     → exactly full</li>
     *   <li>Negative → GIC violation: fixed children exceed locator capacity</li>
     * </ul>
     */
    public double remainingMm() { return capacityMm - filledMm; }

    /** True when locator is exactly full or overflowed. */
    public boolean isFull()     { return remainingMm() <= 0; }

    /** True when fixed children exceed locator capacity — GIC violation. */
    public boolean isOverflow() { return remainingMm() < 0; }

    /** Fill fraction 0.0–1.0+. Values above 1.0 indicate overflow. */
    public double fillFraction() {
        return capacityMm > 0 ? filledMm / capacityMm : 0.0;
    }

    /** Immutable ordered view of all places registered via {@link #placeNext}. */
    public List<Place> places() { return Collections.unmodifiableList(places); }

    @Override
    public String toString() {
        return String.format(
            "PhantomLayout{bom=%s loc=%s cap=%.0fmm filled=%.0fmm rem=%.0fmm items=%d}",
            hostBomId, locatorRef, capacityMm, filledMm, remainingMm(), places.size());
    }
}
