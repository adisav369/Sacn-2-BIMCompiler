package com.bim.designer.watch;

import com.bim.designer.compile.ChangeSet;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches source artifact files for modifications and emits
 * {@link ChangeEvent}s to registered listeners.
 *
 * <p>Uses file mtime polling (not OS file watchers) for portability.
 * Poll interval defaults to 2 seconds.
 *
 * <p>Watched paths and their change type mapping:
 * <ul>
 *   <li>{@code classify_*.yaml} → YAML</li>
 *   <li>{@code dsl_*.bim} → DSL</li>
 *   <li>{@code *.bimcobol} → BIMCOBOL</li>
 *   <li>{@code *_BOM.db} → BOM_HEADER (conservative)</li>
 *   <li>{@code component_library.db} → PRODUCT</li>
 * </ul>
 */
public class ArtifactWatcher implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ArtifactWatcher.class.getName());
    private static final long DEFAULT_POLL_MS = 2000;

    private final List<Consumer<ChangeEvent>> listeners = new CopyOnWriteArrayList<>();
    private final Map<Path, Long> lastModified = new HashMap<>();
    private final Map<Path, ChangeSet.ChangeType> watchedPaths = new LinkedHashMap<>();
    private final long pollIntervalMs;
    private volatile boolean running;
    private Thread pollThread;

    public ArtifactWatcher() {
        this(DEFAULT_POLL_MS);
    }

    public ArtifactWatcher(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    /** Register a path to watch with its change type. */
    public void watch(Path path, ChangeSet.ChangeType type) {
        watchedPaths.put(path, type);
        try {
            lastModified.put(path, Files.getLastModifiedTime(path).toMillis());
        } catch (IOException e) {
            lastModified.put(path, 0L);
        }
    }

    /** Register a listener for change events. */
    public void addListener(Consumer<ChangeEvent> listener) {
        listeners.add(listener);
    }

    /** Start polling on a daemon thread. */
    public void start() {
        running = true;
        pollThread = new Thread(this::pollLoop, "artifact-watcher");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    /** Stop polling. */
    public void stop() {
        running = false;
        if (pollThread != null) pollThread.interrupt();
    }

    @Override
    public void close() {
        stop();
    }

    private void pollLoop() {
        while (running) {
            try {
                Thread.sleep(pollIntervalMs);
                checkForChanges();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void checkForChanges() {
        for (var entry : watchedPaths.entrySet()) {
            Path path = entry.getKey();
            ChangeSet.ChangeType type = entry.getValue();
            try {
                long mtime = Files.getLastModifiedTime(path).toMillis();
                Long prev = lastModified.get(path);
                if (prev != null && mtime > prev) {
                    lastModified.put(path, mtime);
                    var event = new ChangeEvent(path.toString(), type, mtime);
                    for (var listener : listeners) {
                        try {
                            listener.accept(event);
                        } catch (Exception e) {
                            LOG.log(Level.WARNING, "Listener error", e);
                        }
                    }
                }
            } catch (IOException e) {
                // File may have been deleted/recreated — skip this cycle
            }
        }
    }
}
