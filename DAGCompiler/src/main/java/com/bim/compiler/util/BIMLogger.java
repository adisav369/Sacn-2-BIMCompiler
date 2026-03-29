package com.bim.compiler.util;

import com.bim.compiler.geometry.Point3D;

/**
 * DAGCompiler-specific logging extensions.
 * Delegates all standard logging to the shared {@link com.bim.orm.BIMLogger} in orm-core.
 * Adds geometry-aware convenience methods that depend on DAGCompiler types (Point3D).
 */
public class BIMLogger {

    // Delegate type aliases for backward compatibility
    public enum Level {
        ERROR, WARN, INFO, FINE, DEBUG;

        com.bim.orm.BIMLogger.Level toShared() {
            return com.bim.orm.BIMLogger.Level.valueOf(this.name());
        }
    }

    // =========================================================================
    // Delegates to shared logger
    // =========================================================================

    public static void initForRun(String tag) { com.bim.orm.BIMLogger.initForRun(tag); }
    public static void init(String logPath, Level level) { com.bim.orm.BIMLogger.init(logPath, level.toShared()); }
    public static void setLevel(Level level) { com.bim.orm.BIMLogger.setLevel(level.toShared()); }
    public static String getLogFilePath() { return com.bim.orm.BIMLogger.getLogFilePath(); }
    public static void close() { com.bim.orm.BIMLogger.close(); }

    public static void debug(String c, String f, Object... a) { com.bim.orm.BIMLogger.debug(c, f, a); }
    public static void fine(String c, String f, Object... a) { com.bim.orm.BIMLogger.fine(c, f, a); }
    public static void info(String c, String f, Object... a) { com.bim.orm.BIMLogger.info(c, f, a); }
    public static void warn(String c, String f, Object... a) { com.bim.orm.BIMLogger.warn(c, f, a); }
    public static void error(String c, String f, Object... a) { com.bim.orm.BIMLogger.error(c, f, a); }

    public static void geo(String c, String f, Object... a) { com.bim.orm.BIMLogger.geo(c, f, a); }
    public static boolean geoMatch(String productId) { return com.bim.orm.BIMLogger.geoMatch(productId); }

    public static void stage(int step, String name, String detail) { com.bim.orm.BIMLogger.stage(step, name, detail); }
    public static void gate(String gId, String tag, String status, String detail) { com.bim.orm.BIMLogger.gate(gId, tag, status, detail); }
    public static void proof(String pId, String status, String el, String ev) { com.bim.orm.BIMLogger.proof(pId, status, el, ev); }
    public static void qaCheck(String name, boolean passed, String detail) { com.bim.orm.BIMLogger.qaCheck(name, passed, detail); }
    public static void verification(String test, boolean p, double exp, double act) { com.bim.orm.BIMLogger.verification(test, p, exp, act); }

    // =========================================================================
    // DAGCompiler-specific: geometry-aware methods needing Point3D
    // =========================================================================

    /** Log a placement operation with Point3D (DAGCompiler geometry type). */
    public static void placement(String componentType, String id,
                                  Point3D position, double rotation) {
        com.bim.orm.BIMLogger.fine("PLACEMENT",
            "{} {} at ({:.3f}, {:.3f}, {:.3f}) rotation={:.1f}°",
            componentType, id,
            position.x(), position.y(), position.z(),
            Math.toDegrees(rotation));
    }

    /** Log a factory routing decision. */
    public static void factoryRoute(String factoryType, String specId, String reason) {
        com.bim.orm.BIMLogger.fine("FACTORY", "Routing {} to {} ({})", specId, factoryType, reason);
    }

    /** Log geometry extraction. */
    public static void extraction(String ifcClass, int count, String details) {
        com.bim.orm.BIMLogger.fine("EXTRACTION", "{}: {} components ({})", ifcClass, count, details);
    }
}
