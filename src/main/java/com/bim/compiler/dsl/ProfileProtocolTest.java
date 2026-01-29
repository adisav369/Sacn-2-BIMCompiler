package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingDefinition.*;
import com.bim.compiler.dsl.ProtocolValidator.*;

/**
 * Phase 28: PROFILE/PROTOCOL Test
 *
 * Verifies:
 * 1. Profile parsing (profile:"Malaysian_Residential")
 * 2. Protocol parsing (protocol:"Residential_Single_Storey")
 * 3. LOD parsing (lod:300)
 * 4. Profile validation (space type allowed/excluded)
 * 5. Protocol validation (required/excluded spaces)
 * 6. TB-LKTN full validation
 */
public class ProfileProtocolTest {

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("PROFILE/PROTOCOL TEST (Phase 28)");
        System.out.println("=".repeat(70));

        int testsPassed = 0;
        int testsTotal = 0;

        // 1. Profile parsing
        System.out.println("\n1. PROFILE PARSING");
        System.out.println("-".repeat(70));
        testsTotal++;

        String dsl1 = """
            BUILDING "test"
                profile: "Malaysian_Residential"
                protocol: "Residential_Single_Storey"
                lod: 300
            {
                STOREY "Ground" level:0 height:2.8m {
                    BEDROOM "bilik" at:A1 size:4x4m {}
                }
                ROOF pitch:25deg
            }
            """;

        BuildingDefinition def1 = BuildingParser.parse(dsl1);
        System.out.println("  Profile: " + def1.profile());
        System.out.println("  Protocol: " + def1.protocol());
        System.out.println("  LOD: " + def1.lod());

        boolean parseOk = "Malaysian_Residential".equals(def1.profile()) &&
                         "Residential_Single_Storey".equals(def1.protocol()) &&
                         def1.lod() == 300;
        if (parseOk) {
            System.out.println("[PASS] Profile/Protocol/LOD parsing");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Parsing incorrect");
        }

        // 2. Available profiles
        System.out.println("\n2. AVAILABLE PROFILES");
        System.out.println("-".repeat(70));
        testsTotal++;

        System.out.println("Registered profiles:");
        for (String name : ProfileRegistry.getProfileNames()) {
            var profile = ProfileRegistry.getProfile(name);
            System.out.printf("  %s (%s)%n", name, profile.validationCode());
        }

        if (ProfileRegistry.hasProfile("Malaysian_Residential") &&
            ProfileRegistry.hasProfile("US_Residential_IRC")) {
            System.out.println("[PASS] Profiles registered");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Missing profiles");
        }

        // 3. Available protocols
        System.out.println("\n3. AVAILABLE PROTOCOLS");
        System.out.println("-".repeat(70));
        testsTotal++;

        System.out.println("Registered protocols:");
        for (String name : ProtocolValidator.getProtocolNames()) {
            var protocol = ProtocolValidator.getProtocol(name);
            System.out.printf("  %s - %s%n", name, protocol.description());
        }

        if (ProtocolValidator.hasProtocol("Residential_Single_Storey") &&
            ProtocolValidator.hasProtocol("Commercial_Office")) {
            System.out.println("[PASS] Protocols registered");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Missing protocols");
        }

        // 4. Profile validation - allowed type
        System.out.println("\n4. PROFILE VALIDATION (ALLOWED TYPE)");
        System.out.println("-".repeat(70));
        testsTotal++;

        var profile = ProfileRegistry.getProfile("Malaysian_Residential");
        boolean allowsBedroom = profile.allowsSpaceType("BEDROOM");
        boolean allowsAnjung = profile.allowsSpaceType("ANJUNG");
        boolean allowsBilik = profile.allowsSpaceType("BILIK");

        System.out.println("  Malaysian_Residential allows:");
        System.out.println("    BEDROOM: " + allowsBedroom);
        System.out.println("    ANJUNG: " + allowsAnjung);
        System.out.println("    BILIK: " + allowsBilik);

        if (allowsBedroom && allowsAnjung && allowsBilik) {
            System.out.println("[PASS] Space types allowed");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Space type validation incorrect");
        }

        // 5. Profile validation - excluded type
        System.out.println("\n5. PROFILE VALIDATION (EXCLUDED TYPE)");
        System.out.println("-".repeat(70));
        testsTotal++;

        var commercialProfile = ProfileRegistry.getProfile("Commercial_IBC");
        boolean excludesBedroom = !commercialProfile.allowsSpaceType("BEDROOM");
        boolean excludesKitchen = !commercialProfile.allowsSpaceType("KITCHEN");

        System.out.println("  Commercial_IBC excludes:");
        System.out.println("    BEDROOM: " + excludesBedroom);
        System.out.println("    KITCHEN: " + excludesKitchen);

        if (excludesBedroom && excludesKitchen) {
            System.out.println("[PASS] Excluded types work");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Excluded type validation incorrect");
        }

        // 6. Protocol validation - valid building
        System.out.println("\n6. PROTOCOL VALIDATION (VALID BUILDING)");
        System.out.println("-".repeat(70));
        testsTotal++;

        String dsl2 = """
            BUILDING "valid_house"
                profile: "Malaysian_Residential"
                protocol: "Residential_Single_Storey"
            {
                GRID {
                    axes: A, B, C / 1, 2, 3
                    spacing: 4.0, 4.0 / 4.0, 4.0
                }

                STOREY "Ground" level:0 height:2.8m {
                    BEDROOM "bed1" bounds:A1-B2 {
                        opens_to: living
                    }
                    BEDROOM "bed2" bounds:B1-C2 {
                        opens_to: living
                    }
                    BATHROOM "bath" bounds:A2-B3 {
                        opens_to: living
                    }
                    OPEN_PLAN "living" bounds:B2-C3 {
                        zones: LIVING, KITCHEN
                    }
                }

                ROOF pitch:25deg
            }
            """;

        BuildingDefinition def2 = BuildingParser.parse(dsl2);
        ValidationResult result2 = ProtocolValidator.validate(def2, "Residential_Single_Storey");

        System.out.println("  Valid: " + result2.valid());
        System.out.println("  Space counts: " + result2.spaceCounts());
        if (!result2.errors().isEmpty()) {
            System.out.println("  Errors: " + result2.errors());
        }
        if (!result2.warnings().isEmpty()) {
            System.out.println("  Warnings: " + result2.warnings());
        }

        if (result2.valid()) {
            System.out.println("[PASS] Valid building passes protocol");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Valid building should pass");
        }

        // 7. Protocol validation - missing required space
        System.out.println("\n7. PROTOCOL VALIDATION (MISSING REQUIRED)");
        System.out.println("-".repeat(70));
        testsTotal++;

        String dsl3 = """
            BUILDING "incomplete"
                protocol: "Residential_Single_Storey"
            {
                STOREY "Ground" level:0 height:2.8m {
                    BEDROOM "bed1" at:A1 size:4x4m {}
                }
                ROOF pitch:25deg
            }
            """;

        BuildingDefinition def3 = BuildingParser.parse(dsl3);
        ValidationResult result3 = ProtocolValidator.validate(def3, "Residential_Single_Storey");

        System.out.println("  Valid: " + result3.valid());
        System.out.println("  Errors: " + result3.errors());

        if (!result3.valid() && result3.errors().stream().anyMatch(e -> e.contains("BATHROOM"))) {
            System.out.println("[PASS] Missing required detected");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Should detect missing BATHROOM");
        }

        // 8. Protocol validation - excluded space present
        System.out.println("\n8. PROTOCOL VALIDATION (EXCLUDED PRESENT)");
        System.out.println("-".repeat(70));
        testsTotal++;

        String dsl4 = """
            BUILDING "mixed_use"
                protocol: "Commercial_Office"
            {
                STOREY "Ground" level:0 height:3.0m {
                    LOBBY "main" at:A1 size:10x10m {}
                    CORRIDOR "hall" at:B1 size:2x10m {}
                    RESTROOM "wc" at:C1 size:4x4m {}
                    STAIR "stair1" at:D1 width:1.2m to:"Upper"
                    STAIR "stair2" at:E1 width:1.2m to:"Upper"
                    BEDROOM "illegal" at:F1 size:4x4m {}
                }
                ROOF pitch:5deg
            }
            """;

        BuildingDefinition def4 = BuildingParser.parse(dsl4);
        ValidationResult result4 = ProtocolValidator.validate(def4, "Commercial_Office");

        System.out.println("  Valid: " + result4.valid());
        System.out.println("  Errors: " + result4.errors());

        if (!result4.valid() && result4.errors().stream().anyMatch(e -> e.contains("BEDROOM"))) {
            System.out.println("[PASS] Excluded type detected");
            testsPassed++;
        } else {
            System.out.println("[FAIL] Should detect excluded BEDROOM");
        }

        // 9. TB-LKTN full validation
        System.out.println("\n9. TB-LKTN FULL VALIDATION");
        System.out.println("-".repeat(70));
        testsTotal++;

        String dsl5 = """
            BUILDING "TB-LKTN"
                profile: "Malaysian_Residential"
                protocol: "Residential_Single_Storey"
                lod: 300
            {
                GRID {
                    axes: A, B, C, D, E / 1, 2, 3, 4, 5
                    spacing: 1.3, 3.1, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
                }

                STOREY "Ground" level:0 height:2.8m {
                    PORCH "anjung" bounds:C1-D2 {
                        exterior: south
                    }

                    OPEN_PLAN "common" bounds:B2-D5 {
                        zones: LIVING, DINING, KITCHEN
                        exterior: south
                        exterior: north
                    }

                    BEDROOM "bilik_utama" bounds:A2-C3 {
                        exterior: west
                        opens_to: common
                    }

                    BATHROOM "bilik_mandi" bounds:A3-B4 {
                        exterior: west
                        opens_to: common
                    }

                    BATHROOM "tandas" bounds:A4-B5 {
                        exterior: west
                        opens_to: common
                    }

                    BEDROOM "bilik_2" bounds:D2-E3 {
                        exterior: east
                        opens_to: common
                    }

                    BEDROOM "bilik_3" bounds:D3-E5 {
                        exterior: east
                        opens_to: common
                    }
                }

                ROOF pitch:25deg overhang:600mm
            }
            """;

        BuildingDefinition def5 = BuildingParser.parse(dsl5);

        // Profile validation
        var profileErrors = ProfileRegistry.validateAgainstProfile(def5, def5.profile());

        // Protocol validation
        ValidationResult result5 = ProtocolValidator.validate(def5, def5.protocol());

        System.out.println("Building: " + def5.name());
        System.out.println("Profile: " + def5.profile());
        System.out.println("Protocol: " + def5.protocol());
        System.out.println("LOD: " + def5.lod());
        System.out.println("\nProfile validation:");
        if (profileErrors.isEmpty()) {
            System.out.println("  All space types allowed ✓");
        } else {
            for (String err : profileErrors) {
                System.out.println("  ERROR: " + err);
            }
        }

        System.out.println("\nProtocol validation:");
        System.out.println("  Space counts: " + result5.spaceCounts());
        if (result5.valid()) {
            System.out.println("  All requirements met ✓");
        } else {
            for (String err : result5.errors()) {
                System.out.println("  ERROR: " + err);
            }
        }
        for (String warn : result5.warnings()) {
            System.out.println("  WARNING: " + warn);
        }

        if (profileErrors.isEmpty() && result5.valid()) {
            System.out.println("[PASS] TB-LKTN validates");
            testsPassed++;
        } else {
            System.out.println("[FAIL] TB-LKTN should validate");
        }

        // 10. LOD levels
        System.out.println("\n10. LOD LEVELS");
        System.out.println("-".repeat(70));
        testsTotal++;

        System.out.println("LOD Level Definitions:");
        System.out.println("  LOD 100 - Conceptual (area, type only)");
        System.out.println("  LOD 200 - Approximate (dimensions, positions)");
        System.out.println("  LOD 300 - Precise (exact geometry, openings)");
        System.out.println("  LOD 350 - Coordination (MEP, structure)");
        System.out.println("  LOD 400 - Fabrication (products, shop drawings)");
        System.out.println("  LOD 500 - As-Built (verified field conditions)");

        System.out.println("\nTB-LKTN LOD: " + def5.lod());
        if (def5.lod() == 300) {
            System.out.println("  → Precise geometry level");
            System.out.println("[PASS] LOD level correct");
            testsPassed++;
        } else {
            System.out.println("[FAIL] LOD should be 300");
        }

        // SUMMARY
        System.out.println("\n" + "=".repeat(70));
        System.out.println("TEST SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("Tests passed: %d/%d%n", testsPassed, testsTotal);

        System.out.println("\nValidation Chain:");
        System.out.println("  1. Parse DSL with profile/protocol/lod");
        System.out.println("  2. ProfileRegistry.validateAgainstProfile()");
        System.out.println("     → Space types allowed/excluded");
        System.out.println("  3. ProtocolValidator.validate()");
        System.out.println("     → Required/excluded spaces");
        System.out.println("     → Min/max counts");
        System.out.println("  4. LOD determines content requirements");

        if (testsPassed == testsTotal) {
            System.out.println("\n[SUCCESS] PROFILE/PROTOCOL implementation complete!");
        } else {
            System.out.println("\n[PARTIAL] Some tests failed - review above");
        }
    }
}
