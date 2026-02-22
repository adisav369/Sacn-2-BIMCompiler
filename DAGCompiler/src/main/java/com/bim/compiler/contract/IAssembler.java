package com.bim.compiler.contract;

import java.sql.SQLException;
import java.util.List;

/**
 * Contract for BOM and structural assembly phases.
 * Isolates concrete bom-package assembler classes from the dsl layer.
 * [EXTRACTED: ARCHITECTURE.md §Contracts]
 */
public interface IAssembler extends AutoCloseable {

    /** Execute the assembly phase and return a printable summary. */
    AssemblyOutcome assemble() throws SQLException;

    /** Default no-op close — override if resources need explicit release. */
    @Override default void close() throws Exception {}

    record AssemblyOutcome(int applied, List<String> messages) {
        public void print() { messages.forEach(System.out::println); }
        public static AssemblyOutcome of(int applied, List<String> messages) {
            return new AssemblyOutcome(applied, messages);
        }
    }
}
