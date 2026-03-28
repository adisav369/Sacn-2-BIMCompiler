package com.bim.cobol.geometry;

import com.bim.orm.BIMLogger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry mapping discipline code → {@link DisciplineRouteBuilder}.
 *
 * <p>All 6 MEP disciplines are registered at class load time.
 * {@link RouteDocEvent} looks up the builder here when a discipline OrderLine
 * is activated during compilation.
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T2.1–T2.4
public final class DisciplineRouteRegistry {

    private static final String TAG = "ROUTE";

    private static final Map<String, DisciplineRouteBuilder> BUILDERS;

    static {
        Map<String, DisciplineRouteBuilder> m = new LinkedHashMap<>();
        register(m, new FpRouteBuilder());
        register(m, new ElecRouteBuilder());
        register(m, new CwRouteBuilder());
        register(m, new SpRouteBuilder());
        register(m, new AcmvRouteBuilder());
        register(m, new LpgRouteBuilder());
        BUILDERS = Collections.unmodifiableMap(m);
        BIMLogger.fine(TAG, "DisciplineRouteRegistry: {} builders registered", BUILDERS.size());
    }

    private DisciplineRouteRegistry() {}

    private static void register(Map<String, DisciplineRouteBuilder> m, DisciplineRouteBuilder b) {
        m.put(b.discipline(), b);
    }

    /**
     * Get the route builder for a discipline.
     * @param discipline discipline code (FP, ELEC, ACMV, CW, SP, LPG)
     * @return builder, or null if not an MEP discipline
     */
    public static DisciplineRouteBuilder get(String discipline) {
        return BUILDERS.get(discipline);
    }

    /** All registered builders (unmodifiable). */
    public static Map<String, DisciplineRouteBuilder> all() {
        return BUILDERS;
    }

    /** Number of registered builders (should be 6). */
    public static int size() {
        return BUILDERS.size();
    }
}
