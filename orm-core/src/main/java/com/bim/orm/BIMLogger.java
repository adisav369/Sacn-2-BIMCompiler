package com.bim.orm;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Logging utility for BIM Compiler — iDempiere-style levelled logger.
 *
 * <p>Levels (coarsest → finest): ERROR, WARN, INFO, FINE, DEBUG.
 * Console always shows INFO+. File captures everything at or above the
 * configured level. Each pipeline run auto-creates a timestamped log file
 * in {@code logs/} so results are trackable without human visual inspection.
 *
 * <p>Lives in orm-core so every module (IFCtoBOM, DAGCompiler, BIM_COBOL,
 * BonsaiBIMDesigner) can import it.
 *
 * <p>Usage:
 * <pre>
 *   BIMLogger.initForRun("SH_enbloc");              // auto-timestamped file
 *   BIMLogger.setLevel(BIMLogger.Level.FINE);        // optional — default INFO
 *   BIMLogger.info("PIPELINE", "Step {} started", 1);
 *   BIMLogger.fine("PROVER",  "P05 proof: {} elements, {} violations", n, v);
 *   BIMLogger.close();
 * </pre>
 *
 * <p>Log file format (tab-separated, grep-friendly):
 * <pre>
 *   2026-03-19 14:32:01.234 [INFO ] PIPELINE     STEP 1: MetadataValidator — 55 elements
 *   2026-03-19 14:32:01.567 [FINE ] PROVER       [PROVEN] P05 — IfcDoor:23 — no duplicate
 *   2026-03-19 14:32:02.001 [WARN ] QA           [FAIL] countReconciliation — delta=3
 * </pre>
 */
public class BIMLogger {

    /** Log levels ordered coarsest → finest (higher ordinal = finer). */
    public enum Level { ERROR, WARN, INFO, FINE, DEBUG }

    private static Level currentLevel = resolveInitialLevel();
    private static PrintWriter logFile = null;
    private static String logFilePath = null;

    // ── GEO debug mode — dedicated channel for tack chain logging (BBC.md §4) ──
    // Implementing BBC.md §4 — FINE tack chain logging + IFC GUID traceability
    // Each TACK LEAF line proves: (A) tack math ran, (B) IFC GUID preserved
    // GEO is permanently ON — it IS the Last Mile Problem proof (LMP §7)
    private static final boolean GEO_ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("bim.geo.debug",
            readBimProperty("bim.geo.debug", "true")));
    private static final String GEO_FILTER =
        System.getProperty("bim.geo.filter",
            readBimProperty("bim.geo.filter", null));

    // ── PATTERN debug mode — storey/container assignment logging ──
    // Implementing DISC_VALIDATION_DB_SRS §10.4.10 — PATTERN logging channel
    // EYES_SRS §4.7 — extraction-side diagnostics (default OFF)
    private static final boolean PATTERN_ENABLED =
        "true".equalsIgnoreCase(System.getProperty("bim.pattern.debug",
            readBimProperty("bim.pattern.debug", "false")));

    /**
     * Resolve initial level — iDempiere convention: external properties, not hardcoded.
     *
     * <p>Priority (highest wins):
     * <ol>
     *   <li>{@code -Dbim.log.level} system property (CLI override)</li>
     *   <li>{@code BIM.properties} file in working directory</li>
     *   <li>Default: INFO</li>
     * </ol>
     */
    private static Level resolveInitialLevel() {
        // 1. System property (CLI override, highest priority)
        String prop = System.getProperty("bim.log.level");
        if (prop != null && !prop.isEmpty() && !prop.startsWith("$")) {
            try { return Level.valueOf(prop.toUpperCase()); }
            catch (IllegalArgumentException ignored) { /* fall through */ }
        }
        // 2. BIM.properties file in working directory
        Path propsFile = Paths.get("BIM.properties");
        if (Files.exists(propsFile)) {
            try (InputStream in = Files.newInputStream(propsFile)) {
                Properties props = new Properties();
                props.load(in);
                String fileProp = props.getProperty("bim.log.level");
                if (fileProp != null && !fileProp.isEmpty()) {
                    try { return Level.valueOf(fileProp.trim().toUpperCase()); }
                    catch (IllegalArgumentException ignored) { /* fall through */ }
                }
            } catch (IOException ignored) { /* file unreadable — fall through */ }
        }
        // 3. Default
        return Level.INFO;
    }
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // =========================================================================
    // Initialization
    // =========================================================================

    /**
     * Auto-initialize for a pipeline run. Creates {@code logs/pipeline_{tag}_{timestamp}.log}.
     * Safe to call multiple times — closes previous file first.
     */
    public static void initForRun(String tag) {
        close();
        // Re-resolve level from system property (may be set after class init)
        currentLevel = resolveInitialLevel();
        String timestamp = LocalDateTime.now().format(FILE_FMT);
        String filename = String.format("pipeline_%s_%s.log", tag, timestamp);
        Path logDir = Paths.get("logs");
        try {
            Files.createDirectories(logDir);
            logFilePath = logDir.resolve(filename).toString();
            logFile = new PrintWriter(new FileWriter(logFilePath, StandardCharsets.UTF_8, false));
            logFile.println("# BIM Compiler Pipeline Log");
            logFile.printf("# Run:   %s%n", tag);
            logFile.printf("# Start: %s%n", LocalDateTime.now().format(TIME_FMT));
            logFile.printf("# Level: %s%n", currentLevel);
            logFile.println("#");
            logFile.flush();
        } catch (IOException e) {
            System.err.println("[BIMLogger] Could not open log file: " + filename);
        }
    }

    /**
     * Initialize with explicit path (backwards-compatible).
     */
    public static void init(String logPath, Level level) {
        close();
        currentLevel = level;
        logFilePath = logPath;
        try {
            logFile = new PrintWriter(new FileWriter(logPath, StandardCharsets.UTF_8, true));
        } catch (IOException e) {
            System.err.println("[BIMLogger] Could not open log file: " + logPath);
        }
    }

    /** Set the minimum level for both console and file. */
    public static void setLevel(Level level) {
        currentLevel = level;
    }

    /** Return the current log file path, or null if not initialized. */
    public static String getLogFilePath() {
        return logFilePath;
    }

    /** Close the log file. Safe to call when not initialized. */
    public static void close() {
        if (logFile != null) {
            logFile.printf("# End: %s%n", LocalDateTime.now().format(TIME_FMT));
            logFile.close();
            logFile = null;
            logFilePath = null;
        }
    }

    // =========================================================================
    // Standard logging methods — iDempiere-style levels
    // =========================================================================

    public static void debug(String component, String format, Object... args) {
        log(Level.DEBUG, component, format, args);
    }

    /** FINE — detailed operational trace (verb expansion, per-element proofs). */
    public static void fine(String component, String format, Object... args) {
        log(Level.FINE, component, format, args);
    }

    public static void info(String component, String format, Object... args) {
        log(Level.INFO, component, format, args);
    }

    public static void warn(String component, String format, Object... args) {
        log(Level.WARN, component, format, args);
    }

    public static void error(String component, String format, Object... args) {
        log(Level.ERROR, component, format, args);
    }

    // =========================================================================
    // GEO debug mode — tack chain logging (BBC.md §4)
    // =========================================================================

    /**
     * Log a GEO message — emits ONLY when {@code bim.geo.debug=true}.
     * Independent of the general log level (INFO/FINE). Goes to file only.
     */
    public static void geo(String component, String format, Object... args) {
        if (!GEO_ENABLED) return;
        String message = formatMessage(format, args);
        String timestamp = LocalDateTime.now().format(TIME_FMT);
        String line = String.format("%s [GEO  ] %-12s %s",
            timestamp, component, message);
        // GEO goes to file only (never console)
        if (logFile != null) {
            logFile.println(line);
            logFile.flush();
        }
    }

    /**
     * Check if GEO mode is active and the given productId matches the filter.
     * Filter controls logging only — never skip a placement.
     */
    public static boolean geoMatch(String productId) {
        return GEO_ENABLED && (GEO_FILTER == null || GEO_FILTER.isEmpty()
                || (productId != null && productId.contains(GEO_FILTER)));
    }

    // =========================================================================
    // PATTERN debug mode — storey/container assignment logging
    // =========================================================================

    /**
     * Log a PATTERN message — emits ONLY when {@code bim.pattern.debug=true}.
     * Independent of the general log level (INFO/FINE). Goes to file only.
     * Used for extraction-side diagnostics: container Z-ordering, storey assignment.
     */
    public static void pattern(String component, String format, Object... args) {
        if (!PATTERN_ENABLED) return;
        String message = formatMessage(format, args);
        String timestamp = LocalDateTime.now().format(TIME_FMT);
        String line = String.format("%s [PATRN] %-12s %s",
            timestamp, component, message);
        // PATTERN goes to file only (never console)
        if (logFile != null) {
            logFile.println(line);
            logFile.flush();
        }
    }

    // =========================================================================
    // Specialized logging for BIM operations
    // =========================================================================

    /** Log a pipeline stage start/end with element counts. */
    public static void stage(int step, String name, String detail) {
        info("PIPELINE", "STEP {}: {} — {}", step, name, detail);
    }

    /** Log a gate verdict (G1-G6). */
    public static void gate(String gateId, String tag, String status, String detail) {
        Level level = "FAIL".equals(status) ? Level.ERROR : Level.INFO;
        log(level, "GATE", "[{} ] {} {}  {}", gateId, tag, status, detail);
    }

    /** Log a proof result from PlacementProver. */
    public static void proof(String proofId, String status, String element, String evidence) {
        Level level = "VIOLATED".equals(status) ? Level.WARN : Level.FINE;
        log(level, "PROVER", "[{}] {} — {} — {}", status, proofId, element, evidence);
    }

    /** Log a BomValidator check result. */
    public static void qaCheck(String checkName, boolean passed, String detail) {
        if (passed) {
            info("QA", "[PASS] {} — {}", checkName, detail);
        } else {
            warn("QA", "[FAIL] {} — {}", checkName, detail);
        }
    }

    /** Log a verification result. */
    public static void verification(String test, boolean passed,
                                     double expected, double actual) {
        String status = passed ? "PASS" : "FAIL";
        info("VERIFY", "[{}] {}: expected={:.3f}, actual={:.3f}, delta={:.3f}",
            status, test, expected, actual, Math.abs(expected - actual));
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private static void log(Level level, String component, String format, Object... args) {
        if (level.ordinal() > currentLevel.ordinal()) {
            return;  // finer than configured level — skip
        }

        String message = formatMessage(format, args);
        String timestamp = LocalDateTime.now().format(TIME_FMT);
        String line = String.format("%s [%-5s] %-12s %s",
            timestamp, level, component, message);

        // Console: always show WARN+ and INFO (skip FINE/DEBUG on console)
        if (level.ordinal() <= Level.WARN.ordinal()) {
            System.err.println(line);
        } else if (level.ordinal() <= Level.INFO.ordinal()) {
            System.out.println(line);
        }
        // FINE and DEBUG go to file only (quiet console)

        // File: everything at or above configured level
        if (logFile != null) {
            logFile.println(line);
            logFile.flush();
        }
    }

    /**
     * Read a property from BIM.properties file in working directory.
     * Returns defaultValue if file doesn't exist or property is absent.
     */
    private static String readBimProperty(String key, String defaultValue) {
        Path propsFile = Paths.get("BIM.properties");
        if (Files.exists(propsFile)) {
            try (InputStream in = Files.newInputStream(propsFile)) {
                Properties props = new Properties();
                props.load(in);
                String val = props.getProperty(key);
                if (val != null && !val.isEmpty()) return val.trim();
            } catch (IOException ignored) { /* fall through */ }
        }
        return defaultValue;
    }

    private static String formatMessage(String format, Object[] args) {
        if (args == null || args.length == 0) {
            return format;
        }

        StringBuilder sb = new StringBuilder();
        int argIndex = 0;
        int i = 0;
        while (i < format.length()) {
            if (i < format.length() - 1 && format.charAt(i) == '{') {
                int end = format.indexOf('}', i);
                if (end > i) {
                    String specifier = format.substring(i + 1, end);
                    if (argIndex < args.length) {
                        Object arg = args[argIndex++];
                        if (specifier.isEmpty()) {
                            sb.append(arg);
                        } else if (specifier.startsWith(":")) {
                            String javaFormat = "%" + specifier.substring(1);
                            try {
                                sb.append(String.format(javaFormat, arg));
                            } catch (Exception e) {
                                sb.append(arg);
                            }
                        } else {
                            sb.append(arg);
                        }
                    } else {
                        sb.append("{}");
                    }
                    i = end + 1;
                    continue;
                }
            }
            sb.append(format.charAt(i));
            i++;
        }
        return sb.toString();
    }
}
