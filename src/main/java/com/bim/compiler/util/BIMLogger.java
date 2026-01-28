package com.bim.compiler.util;

import com.bim.compiler.geometry.Point3D;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logging utility for BIM Compiler.
 * Provides structured logging for debugging placement and verification.
 */
public class BIMLogger {

    public enum Level { DEBUG, INFO, WARN, ERROR }

    private static Level currentLevel = Level.INFO;
    private static PrintWriter logFile = null;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Initialize logging with file output.
     */
    public static void init(String logPath, Level level) {
        currentLevel = level;
        try {
            logFile = new PrintWriter(new FileWriter(logPath, true));
        } catch (IOException e) {
            System.err.println("Warning: Could not open log file: " + logPath);
        }
    }

    /**
     * Set logging level.
     */
    public static void setLevel(Level level) {
        currentLevel = level;
    }

    /**
     * Close log file.
     */
    public static void close() {
        if (logFile != null) {
            logFile.close();
            logFile = null;
        }
    }

    // =========================================================================
    // Standard logging methods
    // =========================================================================

    public static void debug(String component, String format, Object... args) {
        log(Level.DEBUG, component, format, args);
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
    // Specialized logging for BIM operations
    // =========================================================================

    /**
     * Log a placement operation.
     */
    public static void placement(String componentType, String id,
                                  Point3D position, double rotation) {
        debug("PLACEMENT",
            "{} {} at ({:.3f}, {:.3f}, {:.3f}) rotation={:.1f}°",
            componentType, id,
            position.x(), position.y(), position.z(),
            Math.toDegrees(rotation));
    }

    /**
     * Log a verification result.
     */
    public static void verification(String test, boolean passed,
                                     double expected, double actual) {
        String status = passed ? "PASS" : "FAIL";
        info("VERIFY", "[{}] {}: expected={:.3f}, actual={:.3f}, delta={:.3f}",
            status, test, expected, actual, Math.abs(expected - actual));
    }

    /**
     * Log a factory routing decision.
     */
    public static void factoryRoute(String factoryType, String specId, String reason) {
        debug("FACTORY", "Routing {} to {} ({})", specId, factoryType, reason);
    }

    /**
     * Log geometry extraction.
     */
    public static void extraction(String ifcClass, int count, String details) {
        debug("EXTRACTION", "{}: {} components ({})", ifcClass, count, details);
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private static void log(Level level, String component, String format, Object... args) {
        if (level.ordinal() < currentLevel.ordinal()) {
            return;
        }

        // Format message
        String message = formatMessage(format, args);

        // Build log line
        String timestamp = LocalDateTime.now().format(TIME_FMT);
        String line = String.format("%s [%-5s] %-12s %s",
            timestamp, level, component, message);

        // Output
        if (level.ordinal() >= Level.WARN.ordinal()) {
            System.err.println(line);
        } else if (currentLevel == Level.DEBUG) {
            System.out.println(line);
        }

        if (logFile != null) {
            logFile.println(line);
            logFile.flush();
        }
    }

    private static String formatMessage(String format, Object[] args) {
        if (args == null || args.length == 0) {
            return format;
        }

        // Simple {} replacement (not full SLF4J style, but close)
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
                            // Format specifier like {:.3f}
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
