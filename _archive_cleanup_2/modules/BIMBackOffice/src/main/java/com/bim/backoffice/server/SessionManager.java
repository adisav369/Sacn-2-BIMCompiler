package com.bim.backoffice.server;

import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Multi-user session manager with per-database write serialization.
 *
 * <p>Handles:
 * <ul>
 *   <li>Session creation/lookup/expiry (token-based)</li>
 *   <li>Per-database write locks (SQLite single-writer constraint)</li>
 *   <li>Connection pooling per BOM database file</li>
 * </ul>
 *
 * <p>SQLite allows concurrent readers but only one writer. This manager
 * serializes writes per database file via {@link ReentrantLock}. Reads
 * are uncontended — each request gets its own read connection.
 *
 * // Implementing BACK_OFFICE_SRS.md §4 — Witness: W-BO-SESSION-1
 */
public class SessionManager {

    private static final String TAG = "SessionManager";
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
    private static final String HMAC_ALGO = "HmacSHA256";

    // ── Token signing (WAN security) ─────────────────────────────────

    private final byte[] signingKey;

    /** Default constructor — generates random signing key (secure per JVM lifetime). */
    public SessionManager() {
        this(UUID.randomUUID().toString());
    }

    /**
     * Constructor with explicit secret — use for deployments where the key
     * must survive restarts (load from env var or config file).
     * Set via BIM_SESSION_SECRET environment variable or pass directly.
     */
    public SessionManager(String secret) {
        this.signingKey = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Sign a session ID with HMAC-SHA256. Token format: {sessionId}.{signature}
     * Prevents token forgery over WAN — attacker cannot create valid tokens
     * without knowing the signing key.
     */
    String signToken(String sessionId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGO));
            String sig = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(sessionId.getBytes(
                            java.nio.charset.StandardCharsets.UTF_8)));
            return sessionId + "." + sig;
        } catch (Exception e) {
            throw new RuntimeException("HMAC signing failed", e);
        }
    }

    /**
     * Verify and extract session ID from a signed token.
     * Returns null if signature is invalid (tampered or forged).
     */
    String verifyToken(String token) {
        if (token == null) return null;
        int dot = token.lastIndexOf('.');
        if (dot < 0) {
            // Backward-compatible: accept unsigned UUID tokens (local/test use)
            return token;
        }
        String sessionId = token.substring(0, dot);
        String expected = signToken(sessionId);
        // Constant-time comparison to prevent timing attacks
        if (token.length() != expected.length()) return null;
        int diff = 0;
        for (int i = 0; i < token.length(); i++) {
            diff |= token.charAt(i) ^ expected.charAt(i);
        }
        return diff == 0 ? sessionId : null;
    }

    // ── Session tracking ────────────────────────────────────────────

    public record Session(String sessionId, String userId, String displayName,
                          Instant created, Instant lastAccess,
                          String activeBuildingId) {}

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * Create a new session for a user. Returns HMAC-signed session token.
     */
    public String createSession(String userId, String displayName) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new Session(sessionId, userId, displayName,
                Instant.now(), Instant.now(), null));
        String signedToken = signToken(sessionId);
        BIMLogger.info(TAG, "Session created: {} for user {}", sessionId.substring(0, 8), userId);
        return signedToken;
    }

    /**
     * Look up and refresh a session. Verifies token signature first.
     * Returns null if expired, unknown, or signature invalid.
     */
    public Session getSession(String token) {
        String sessionId = verifyToken(token);
        if (sessionId == null) {
            BIMLogger.warn(TAG, "Invalid token signature");
            return null;
        }

        Session s = sessions.get(sessionId);
        if (s == null) return null;

        // Check expiry
        if (Instant.now().toEpochMilli() - s.lastAccess.toEpochMilli() > SESSION_TIMEOUT_MS) {
            sessions.remove(sessionId);
            BIMLogger.info(TAG, "Session expired: {}", sessionId.substring(0, 8));
            return null;
        }

        // Refresh last access
        Session refreshed = new Session(s.sessionId, s.userId, s.displayName,
                s.created, Instant.now(), s.activeBuildingId);
        sessions.put(sessionId, refreshed);
        return refreshed;
    }

    /**
     * Set the active building for a session (for conflict detection).
     * Accepts both signed tokens and raw session IDs (backward-compatible).
     */
    public void setActiveBuilding(String token, String buildingId) {
        String sessionId = verifyToken(token);
        if (sessionId == null) sessionId = token; // fallback for direct sessionId use
        Session s = sessions.get(sessionId);
        if (s != null) {
            sessions.put(sessionId, new Session(s.sessionId, s.userId, s.displayName,
                    s.created, Instant.now(), buildingId));
        }
    }

    /**
     * List all active sessions — for "who's online" display.
     */
    public java.util.List<Session> activeSessions() {
        long now = Instant.now().toEpochMilli();
        return sessions.values().stream()
                .filter(s -> now - s.lastAccess.toEpochMilli() < SESSION_TIMEOUT_MS)
                .toList();
    }

    /**
     * Check if another user is editing the same building (conflict warning).
     */
    public java.util.List<Session> whoIsEditing(String buildingId, String excludeToken) {
        return activeSessions().stream()
                .filter(s -> buildingId.equals(s.activeBuildingId))
                .filter(s -> !s.sessionId.equals(excludeToken))
                .toList();
    }

    public void removeSession(String token) {
        sessions.remove(token);
    }

    // ── Per-database write serialization ────────────────────────────

    private final Map<String, ReentrantLock> writeLocks = new ConcurrentHashMap<>();
    private final Map<String, Connection> writeConns = new ConcurrentHashMap<>();

    /**
     * Get the write lock for a database file. Creates lock on first access.
     * Caller must lock/unlock manually:
     * <pre>
     *   ReentrantLock lock = sessionMgr.getWriteLock("SH_BOM.db");
     *   lock.lock();
     *   try { ... write ... } finally { lock.unlock(); }
     * </pre>
     */
    public ReentrantLock getWriteLock(String dbFileName) {
        return writeLocks.computeIfAbsent(dbFileName, k -> new ReentrantLock());
    }

    /**
     * Get or create a shared write connection for a database.
     * All writes to the same DB go through one connection (SQLite constraint).
     * Reads use separate connections.
     */
    public Connection getWriteConnection(String libraryDir, String dbFileName) throws SQLException {
        return writeConns.computeIfAbsent(dbFileName, k -> {
            try {
                Connection c = DriverManager.getConnection(
                        "jdbc:sqlite:" + libraryDir + "/" + dbFileName);
                c.createStatement().execute("PRAGMA journal_mode=WAL");
                c.createStatement().execute("PRAGMA busy_timeout=5000");
                BIMLogger.info(TAG, "Write connection opened: {}", dbFileName);
                return c;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to open write connection: " + dbFileName, e);
            }
        });
    }

    /**
     * Open a read-only connection (no lock needed — SQLite WAL allows concurrent reads).
     */
    public Connection openReadConnection(String libraryDir, String dbFileName) throws SQLException {
        Connection c = DriverManager.getConnection(
                "jdbc:sqlite:" + libraryDir + "/" + dbFileName);
        c.createStatement().execute("PRAGMA query_only=1");
        return c;
    }

    /**
     * Close all managed connections. Call on server shutdown.
     */
    public void closeAll() {
        for (Connection c : writeConns.values()) {
            try { c.close(); } catch (SQLException ignored) {}
        }
        writeConns.clear();
        sessions.clear();
    }
}
