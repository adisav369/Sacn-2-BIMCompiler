package com.bim.compiler.topologymaker;

import java.util.List;

/**
 * Immutable result of a topology batch process run.
 *
 * @param status        Final DocStatus after processing
 * @param roomsWritten  Number of ad_room_boundary rows inserted
 * @param bomsWritten   Number of m_bom rows inserted (FLOOR + UNIT)
 * @param violations    UBBL violations found (empty = all rooms compliant)
 * @param log           Processing trace messages in order
 */
public record TopologyResult(
        DocStatus status,
        int roomsWritten,
        int bomsWritten,
        List<String> violations,
        List<String> log
) {
    public TopologyResult {
        violations = List.copyOf(violations);
        log = List.copyOf(log);
    }

    /** True only when DocStatus is CO (completed without blocking violations). */
    public boolean isComplete() {
        return status == DocStatus.CO;
    }
}
