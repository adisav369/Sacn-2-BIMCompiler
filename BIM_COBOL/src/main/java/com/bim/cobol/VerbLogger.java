package com.bim.cobol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * Structured logger for BIM COBOL verb results.
 *
 * <p>Instead of each verb having ad-hoc print statements, the verb returns
 * a typed {@link VerbResult} and this logger formats it consistently.
 *
 * <p>Three output modes:
 * <ul>
 *   <li>{@link #COMPACT} — one-liner: pass/fail + verb + summary</li>
 *   <li>{@link #DETAIL} — summary + payload record fields</li>
 *   <li>{@link #JSON} — full JSON serialization</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   VerbLogger log = VerbLogger.detail(System.out::println);
 *   VerbResult&lt;?&gt; result = registry.dispatch(ctx, "CREATE BOM SY_TEST TYPE SET CATEGORY KT");
 *   log.log(result);
 * </pre>
 *
 * <p>The logger never throws — if payload introspection fails, it falls back
 * to the summary string.
 */
public class VerbLogger {

    /** Output verbosity. */
    public enum Mode { COMPACT, DETAIL, JSON }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Mode mode;
    private final Consumer<String> sink;

    private VerbLogger(Mode mode, Consumer<String> sink) {
        this.mode = mode;
        this.sink = sink;
    }

    // ── Factories ────────────────────────────────────────────────────────

    /** Compact logger: one line per result. */
    public static VerbLogger compact(Consumer<String> sink) {
        return new VerbLogger(Mode.COMPACT, sink);
    }

    /** Detail logger: summary + payload field dump. */
    public static VerbLogger detail(Consumer<String> sink) {
        return new VerbLogger(Mode.DETAIL, sink);
    }

    /** JSON logger: full serialization. */
    public static VerbLogger json(Consumer<String> sink) {
        return new VerbLogger(Mode.JSON, sink);
    }

    /** Custom mode + sink. */
    public static VerbLogger of(Mode mode, Consumer<String> sink) {
        return new VerbLogger(mode, sink);
    }

    // ── Logging ──────────────────────────────────────────────────────────

    /** Log a single verb result. */
    public void log(VerbResult<?> result) {
        if (result == null) {
            sink.accept("[NULL RESULT]");
            return;
        }

        switch (mode) {
            case COMPACT -> logCompact(result);
            case DETAIL  -> logDetail(result);
            case JSON    -> logJson(result);
        }
    }

    /** Log all results from a ScriptRunner report. */
    public void log(ScriptRunner.ScriptReport report) {
        for (VerbResult<?> r : report.results()) {
            log(r);
        }
        sink.accept(String.format("--- %d/%d passed (%d failed)",
            report.passCount(), report.totalLines(), report.failCount()));
    }

    // ── Format modes ─────────────────────────────────────────────────────

    private void logCompact(VerbResult<?> r) {
        sink.accept(String.format("[%s] %s: %s",
            r.pass() ? "OK" : "FAIL", r.verb(), r.summary()));
    }

    private void logDetail(VerbResult<?> r) {
        String tag = r.pass() ? "OK" : "FAIL";
        sink.accept(String.format("[%s] %s: %s", tag, r.verb(), r.summary()));

        Object payload = r.payload();
        if (payload == null) return;

        // Introspect record components
        if (payload.getClass().isRecord()) {
            RecordComponent[] components = payload.getClass().getRecordComponents();
            for (RecordComponent rc : components) {
                try {
                    Object value = rc.getAccessor().invoke(payload);
                    String formatted = formatValue(value);
                    sink.accept(String.format("  %-20s = %s", rc.getName(), formatted));
                } catch (Exception e) {
                    // Silently skip unreadable fields
                }
            }
        }
    }

    private void logJson(VerbResult<?> r) {
        sink.accept(GSON.toJson(r));
    }

    // ── Value formatting ─────────────────────────────────────────────────

    private static String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + s + "\"";
        if (value instanceof Double d) return String.format("%.6f", d);
        if (value instanceof Float f) return String.format("%.3f", f);
        if (value instanceof Collection<?> c) {
            if (c.isEmpty()) return "[]";
            if (c.size() <= 5) return c.toString();
            return "[" + c.size() + " items]";
        }
        return value.toString();
    }
}
