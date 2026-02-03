package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Check 11: Witness Verification (Phase 42)
 * Reads the companion witness JSON file and verifies all claims.
 * This provides comprehensive validation using the compiler's witness system.
 */
public class WitnessVerificationCheck implements SanityCheck {

    @Override
    public String getId() { return "witness_verification"; }

    @Override
    public String getName() { return "Witness Verification"; }

    @Override
    public CheckResult execute(SanityModel model) {
        // Derive witness file path from DB path
        String dbPath = model.getDbPath();
        String witnessPath = dbPath.replace(".db", "_witness.json");

        Path witnessFile = Path.of(witnessPath);

        if (!Files.exists(witnessFile)) {
            return CheckResult.warning(getId(), getName())
                .summary("Witness file not found")
                .detail("Expected witness file at: " + witnessPath)
                .guidance("Generate witness file with the compiler")
                .data("witnessPath", witnessPath)
                .data("exists", false)
                .build();
        }

        try {
            String json = Files.readString(witnessFile);

            // Parse key metrics from JSON (simple regex, not full JSON parsing)
            int totalClaims = extractInt(json, "\"total_claims\":\\s*(\\d+)");
            int provenClaims = extractInt(json, "\"proven\":\\s*(\\d+)");
            int skippedClaims = extractInt(json, "\"skipped\":\\s*(\\d+)");
            int unprovableClaims = extractInt(json, "\"unprovable\":\\s*(\\d+)");
            String version = extractString(json, "\"compiler_version\":\\s*\"([^\"]+)\"");

            // Collect failed/skipped claim names
            List<String> issues = new ArrayList<>();

            // Pattern to find claims with status
            Pattern claimPattern = Pattern.compile(
                "\"(\\w+)\":\\s*\\{[^}]*\"status\":\\s*\"(SKIPPED|UNPROVABLE)\"");
            Matcher matcher = claimPattern.matcher(json);
            while (matcher.find()) {
                String claimName = matcher.group(1);
                String status = matcher.group(2);
                issues.add(claimName + " (" + status + ")");
            }

            if (totalClaims == 0) {
                return CheckResult.warning(getId(), getName())
                    .summary("Witness file has no claims")
                    .detail("Witness file exists but contains no claims")
                    .guidance("Check witness generation in compiler")
                    .data("witnessPath", witnessPath)
                    .build();
            }

            // Determine result based on proven claims
            double provenRatio = totalClaims > 0 ? (double) provenClaims / totalClaims : 0;

            if (provenClaims == totalClaims) {
                return CheckResult.pass(getId(), getName())
                    .summary(String.format("All %d witnesses PROVEN (v%s)", totalClaims, version))
                    .data("totalClaims", totalClaims)
                    .data("provenClaims", provenClaims)
                    .data("skippedClaims", skippedClaims)
                    .data("unprovableClaims", unprovableClaims)
                    .data("compilerVersion", version)
                    .build();
            }

            if (provenRatio >= 0.9) {  // 90%+ is passing
                return CheckResult.pass(getId(), getName())
                    .summary(String.format("%d/%d witnesses PROVEN (v%s)", provenClaims, totalClaims, version))
                    .detail("Non-proven claims: " + String.join(", ", issues))
                    .data("totalClaims", totalClaims)
                    .data("provenClaims", provenClaims)
                    .data("skippedClaims", skippedClaims)
                    .data("unprovableClaims", unprovableClaims)
                    .data("compilerVersion", version)
                    .data("issues", issues)
                    .build();
            }

            if (provenRatio >= 0.7) {  // 70-90% is warning
                return CheckResult.warning(getId(), getName())
                    .summary(String.format("%d/%d witnesses PROVEN (v%s)", provenClaims, totalClaims, version))
                    .detail("Non-proven claims: " + String.join(", ", issues))
                    .guidance("Review witness file for claim details")
                    .data("totalClaims", totalClaims)
                    .data("provenClaims", provenClaims)
                    .data("skippedClaims", skippedClaims)
                    .data("unprovableClaims", unprovableClaims)
                    .data("compilerVersion", version)
                    .data("issues", issues)
                    .build();
            }

            // Less than 70% is fail
            return CheckResult.fail(getId(), getName())
                .summary(String.format("Only %d/%d witnesses PROVEN (v%s)", provenClaims, totalClaims, version))
                .detail("Non-proven claims: " + String.join(", ", issues))
                .guidance("Review witness file for claim details and fix issues")
                .data("totalClaims", totalClaims)
                .data("provenClaims", provenClaims)
                .data("skippedClaims", skippedClaims)
                .data("unprovableClaims", unprovableClaims)
                .data("compilerVersion", version)
                .data("issues", issues)
                .build();

        } catch (IOException e) {
            return CheckResult.fail(getId(), getName())
                .summary("Failed to read witness file")
                .detail("Error: " + e.getMessage())
                .data("witnessPath", witnessPath)
                .build();
        }
    }

    private int extractInt(String json, String pattern) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(json);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    private String extractString(String json, String pattern) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return "unknown";
    }
}
