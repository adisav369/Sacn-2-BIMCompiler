package com.bim.designer.protocol;

/**
 * Async push message from server to Bonsai addon.
 *
 * <p>Sent after ArtifactWatcher detects a change + auto-recompile completes.
 * Python addon receives via {@code threading.Thread}, dispatches to
 * Blender via {@code bpy.app.timers}.
 *
 * @param type         message type (COMPILE_COMPLETE, ERROR, INFO)
 * @param buildingId   which building was affected
 * @param outputDbPath path to the fresh output.db
 * @param elementCount number of elements in the output
 * @param message      human-readable message (for errors/info)
 */
public record StatusMessage(
        String type,
        String buildingId,
        String outputDbPath,
        int elementCount,
        String message
) {
    public static StatusMessage compileComplete(String buildingId,
                                                 String outputDbPath, int elementCount) {
        return new StatusMessage("COMPILE_COMPLETE", buildingId,
                outputDbPath, elementCount, null);
    }

    public static StatusMessage error(String message) {
        return new StatusMessage("ERROR", null, null, 0, message);
    }

    public static StatusMessage info(String message) {
        return new StatusMessage("INFO", null, null, 0, message);
    }
}
