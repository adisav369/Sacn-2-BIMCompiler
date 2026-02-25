package com.bim.compiler.coordinate;

/**
 * Sealed coordinate type hierarchy — prevents LocalCoord / StoreyCoord / WorldCoord conflation.
 *
 * <p>Three distinct spaces, each with a dedicated type:
 * <ul>
 *   <li>{@link LocalCoord} — BOM child offset relative to its parent BOM anchor
 *       (dx, dy, dz, rotation from m_attribute)</li>
 *   <li>{@link StoreyCoord} — wall anchor in storey/room space
 *       (output of computeZoneAnchor; also the recursive parent for nested BOMs)</li>
 *   <li>{@link WorldCoord} — final absolute world position ready to write to element_instances
 *       (only constructable via LocalCoord.toWorld() or StoreyCoord.asWorld())</li>
 * </ul>
 *
 * <p>Accumulation chain: {@code LocalCoord.toWorld(StoreyCoord) → WorldCoord}.
 * For nested BOMs: {@code StoreyCoord.fromWorld(WorldCoord)} promotes child world position
 * to the new parent anchor before recursing.
 *
 * <p>WATCHDOG — D8 ArchUnit gate (DriftGuardTest):
 * No class outside {@code ..coordinate..} may call {@code new WorldCoord(...)}.
 * Callers must go through the factory methods. This prevents the R7-class regression
 * where storey Z and local Z were conflated by direct coordinate construction.
 *
 * @see LocalCoord
 * @see StoreyCoord
 * @see WorldCoord
 */
public sealed interface Coordinate permits LocalCoord, StoreyCoord, WorldCoord {}
