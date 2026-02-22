package com.bim.compiler.contract;

import com.bim.compiler.bom.BOMAssemblerAD;
import com.bim.compiler.bom.WallOpeningAssembler;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Factory — creates IAssembler instances from bom-package concrete classes.
 *
 * The contract package is the approved bridge between the dsl layer and
 * the bom package. This keeps the dsl layer free of direct bom dependencies.
 * [EXTRACTED: ARCHITECTURE.md §Contracts — A2 enforcement boundary]
 */
public final class AssemblerFactory {

    private AssemblerFactory() {}

    /** Create a BOM recipe assembler for the given target connection. */
    public static IAssembler bomAssembler(Connection conn) throws SQLException {
        return new BOMAssemblerAD(conn);
    }

    /** Create a wall-opening linker assembler for the given target connection. */
    public static IAssembler wallOpeningAssembler(Connection conn) {
        return new WallOpeningAssembler(conn);
    }
}
