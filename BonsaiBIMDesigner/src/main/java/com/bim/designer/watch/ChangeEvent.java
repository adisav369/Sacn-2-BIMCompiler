package com.bim.designer.watch;

import com.bim.designer.compile.ChangeSet;

/**
 * Notification that a source artifact has changed on disk.
 *
 * <p>Produced by {@link ArtifactWatcher}, consumed by the auto-recompile
 * loop in the server.
 *
 * @param path       the file that changed
 * @param changeType the detected change type
 * @param timestamp  when the change was detected (epoch millis)
 */
public record ChangeEvent(
        String path,
        ChangeSet.ChangeType changeType,
        long timestamp
) {}
