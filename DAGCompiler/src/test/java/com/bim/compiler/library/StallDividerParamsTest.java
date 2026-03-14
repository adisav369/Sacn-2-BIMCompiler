package com.bim.compiler.library;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-G1-5: Stall divider params are data-driven from BOM, not hardcoded.
 *
 * <p>Witness claim: TOILET_BLOCK_FIXTURES TOILET line carries
 * spacing=1.3, stall_divider_depth=1.2, stall_divider_height=1.8
 * in m_attribute; BOMTierResolver.getStallDividerParams() reads them.
 *
 * <p>Phase G-1 Step 5: Before this, StoreyCompiler hardcoded all three values.
 */
@DisplayName("W-G1-5 — Stall Divider Params: Data-Driven from BOM")
class StallDividerParamsTest {

    @Test
    @DisplayName("W-G1-5-A: BOMTreeLoader loads stall divider params for TOILET role")
    void toiletBomChildHasStallParams() throws Exception {
        var bomTree = BOMTreeLoader.load(System.getProperty("bom.db"));

        var node = bomTree.get("TOILET_BLOCK_FIXTURES");
        assertNotNull(node, "TOILET_BLOCK_FIXTURES BOM must exist");

        var toiletChild = node.children().stream()
            .filter(c -> "TOILET".equals(c.role()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No TOILET role child in TOILET_BLOCK_FIXTURES"));

        assertEquals(1.3,  toiletChild.paramDouble("spacing",              0.0), 1e-9,
            "spacing must be 1.3 from m_attribute");
        assertEquals(1.2,  toiletChild.paramDouble("stall_divider_depth",  0.0), 1e-9,
            "stall_divider_depth must be 1.2 from m_attribute");
        assertEquals(1.8,  toiletChild.paramDouble("stall_divider_height", 0.0), 1e-9,
            "stall_divider_height must be 1.8 from m_attribute");
    }

    @Test
    @DisplayName("W-G1-5-B: BOMTierResolver.getStallDividerParams returns correct values")
    void resolverExposesStallParams() {
        var resolver = new BOMTierResolver();
        var params = resolver.getStallDividerParams("TOILET_BLOCK_FIXTURES");

        assertNotNull(params, "StallDividerParams must not be null");
        assertEquals(1.3, params.spacing(),       1e-9, "spacing");
        assertEquals(1.2, params.dividerDepth(),  1e-9, "dividerDepth");
        assertEquals(1.8, params.stallHeight(),   1e-9, "stallHeight");
    }
}
