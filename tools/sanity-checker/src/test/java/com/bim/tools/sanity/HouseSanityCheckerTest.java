package com.bim.tools.sanity;

import com.bim.tools.sanity.report.CheckResult;
import com.bim.tools.sanity.report.SanityReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for House Sanity Checker.
 */
class HouseSanityCheckerTest {

    private static final String TB_LKTN_DB = "../../output/tb_lktn.db";

    /**
     * Test with the TB-LKTN reference building if available.
     */
    @Test
    @EnabledIf("tbLktnDbExists")
    void tbLktnShouldPassAllChecks() throws Exception {
        SanityReport report = HouseSanityChecker.check(TB_LKTN_DB);

        System.out.println(report.toConsoleString(true));

        // Should pass overall
        assertEquals(SanityReport.Verdict.PASS, report.getVerdict(),
            "TB-LKTN should be a valid house");

        // Should have no failures
        assertEquals(0, report.getFailCount(),
            "TB-LKTN should have no failures");

        // Should run all 7 checks
        assertEquals(7, report.getTotalCount(),
            "Should run all 7 checks");
    }

    /**
     * Test JSON output format.
     */
    @Test
    @EnabledIf("tbLktnDbExists")
    void jsonOutputShouldBeValid() throws Exception {
        SanityReport report = HouseSanityChecker.check(TB_LKTN_DB);
        String json = report.toJsonString();

        assertNotNull(json);
        assertTrue(json.contains("\"verdict\""));
        assertTrue(json.contains("\"checks\""));
        assertTrue(json.contains("\"summary\""));

        System.out.println("JSON Output:\n" + json);
    }

    /**
     * Test individual check results are populated.
     */
    @Test
    @EnabledIf("tbLktnDbExists")
    void checkResultsShouldHaveDetails() throws Exception {
        SanityReport report = HouseSanityChecker.check(TB_LKTN_DB);

        for (CheckResult result : report.getResults()) {
            assertNotNull(result.getCheckId(), "Check ID should not be null");
            assertNotNull(result.getCheckName(), "Check name should not be null");
            assertNotNull(result.getStatus(), "Status should not be null");
            assertNotNull(result.getSummary(), "Summary should not be null");
            assertFalse(result.getSummary().isEmpty(), "Summary should not be empty");
        }
    }

    /**
     * Condition method to check if TB-LKTN database exists.
     */
    static boolean tbLktnDbExists() {
        return Files.exists(Path.of(TB_LKTN_DB));
    }

    // --- Tests for specific failure scenarios would go here ---
    // These require test fixture databases which should be created
    // as part of integration testing

    /**
     * Verify check IDs are unique.
     */
    @Test
    void checkIdsShouldBeUnique() {
        var checks = java.util.List.of(
            new com.bim.tools.sanity.checks.FoundationCheck(),
            new com.bim.tools.sanity.checks.EntryDoorCheck(),
            new com.bim.tools.sanity.checks.WindowPlacementCheck(),
            new com.bim.tools.sanity.checks.RoomConnectivityCheck(),
            new com.bim.tools.sanity.checks.RoomProportionCheck(),
            new com.bim.tools.sanity.checks.RoofCoverageCheck(),
            new com.bim.tools.sanity.checks.EnvelopeContainmentCheck()
        );

        var ids = new java.util.HashSet<String>();
        for (var check : checks) {
            assertTrue(ids.add(check.getId()),
                "Duplicate check ID: " + check.getId());
        }

        assertEquals(7, ids.size(), "Should have 7 unique check IDs");
    }
}
