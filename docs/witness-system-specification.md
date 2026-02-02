# WITNESS SYSTEM SPECIFICATION

## Purpose

The Witness System provides **proof by construction** for the BIM Compiler. Instead of post-hoc verification asking "is this geometry correct?", the compiler generates **witnesses** - concrete evidence that proves each claim about the building.

The Sanity Checker then becomes a **Witness Verifier** - it doesn't re-derive proofs, it simply checks that the provided witnesses are valid.

---

## Design Principles

| Principle | Meaning |
|-----------|---------|
| **Non-intrusive** | Witness generation is a parallel output, not embedded in core compilation |
| **Non-blocking** | Compilation succeeds even if witness generation fails |
| **Simple data** | Witnesses are plain JSON - lists, paths, values |
| **Independently verifiable** | Verifier uses only the witness file and the .db file |
| **Compiler proves its work** | Burden of proof is on generator, not checker |

---

## Architecture

```
                    ┌─────────────────────┐
                    │    DSL Parser       │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  BuildingCompiler   │
                    │  (existing)         │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
     ┌────────────────┐ ┌────────────┐ ┌────────────────┐
     │ building.db    │ │ witness.json│ │ building.ifc   │
     │ (geometry)     │ │ (proofs)    │ │ (optional)     │
     └────────────────┘ └──────┬─────┘ └────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  Witness Verifier   │
                    │  (independent tool) │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  Verification Report│
                    │  PASS / FAIL        │
                    └─────────────────────┘
```

---

## Claims and Witnesses

### Claim Registry

Each claim has:
- **ID**: Unique identifier
- **Statement**: What is being proven
- **Witness Type**: Structure of the proof
- **Verification Method**: How to check the witness

| Claim ID | Statement | Witness Type | Verification |
|----------|-----------|--------------|--------------|
| `FOUNDATION_GROUNDED` | Foundation is at ground level | Single Z value | Check Z = 0 ± tolerance |
| `ALL_ROOMS_REACHABLE` | Every room accessible from entry | Path list | Walk each path, confirm doors |
| `ENTRY_EXISTS` | Building has entry from exterior | Single path | Confirm path starts at EXTERIOR |
| `WINDOWS_ON_EXTERIOR` | All windows on exterior walls | Wall classification list | Check each window's wall is EXTERIOR |
| `ROOF_COVERS_ALL` | Roof covers entire footprint | Corner containment list | Check each corner inside roof |
| `ROOMS_ENCLOSED` | Each room forms closed polygon | Vertex loops | Check each loop is closed |
| `NO_ROOM_OVERLAP` | Rooms don't illegally overlap | Separation証明 | Check bbox separation or valid adjacency |
| `ROOMS_IN_ENVELOPE` | All rooms inside building envelope | Containment list | Check each room bbox inside envelope |
| `ELECTRICAL_IN_SPACES` | Electrical elements in room bounds | Containment list | Check element position in room bbox |
| `FIXTURES_ATTACHED_TO_HOSTS` | Fixtures attached to surfaces | Gap measurements | Check gap ≤ tolerance |
| `PLUMBING_PIPES_VALID` | Pipe dimensions correct | Dimension list | Check diameter ≥ minimum |
| `PLUMBING_WASTE_COMPLETE` | All fixtures drain to MH | Graph connectivity | Path exists fixture → MH |
| `PLUMBING_VENT_COMPLETE` | All traps vent to atmosphere | Graph connectivity | Path exists trap → vent termination |
| `PLUMBING_SUPPLY_COMPLETE` | All fixtures supplied | Graph connectivity | Path exists meter → fixture |
| `STOREYS_VERTICALLY_CONSISTENT` | Stack alignment across storeys | Alignment check | XY delta ≤ 5mm |
| `ALL_OUTLETS_ON_CIRCUIT` | All outlets on circuits from DB | Graph connectivity | Path exists DB → circuit → outlet |

---

## Witness File Format

### Schema

```json
{
  "version": "1.0",
  "building": "<building_name>",
  "generated": "<ISO timestamp>",
  "compiler_version": "<version>",
  
  "claims": {
    "<CLAIM_ID>": {
      "status": "PROVEN | UNPROVABLE | SKIPPED",
      "witness": { ... },
      "notes": "optional explanation"
    }
  },
  
  "summary": {
    "total_claims": 15,
    "proven": 14,
    "unprovable": 0,
    "skipped": 1
  }
}
```

### Witness Structures by Claim

#### FOUNDATION_GROUNDED

**Convention:** Foundation TOP surface = Z=0 (floor level). Foundation depth extends below.

```json
{
  "status": "PROVEN",
  "witness": {
    "foundation_id": "foundation_001",
    "top_z": 0.0,
    "bottom_z": -0.15,
    "depth": 0.15,
    "tolerance": 0.005,
    "note": "SLAB_ON_GRADE: top surface at floor level Z=0"
  }
}
```

**Verification:** Check `top_z == 0.0 ± tolerance`, not bottom_z.

#### ENTRY_EXISTS

**Convention:** Entry door must be on wall tagged EXTERIOR, connecting EXTERIOR to an interior space.

```json
{
  "status": "PROVEN",
  "witness": {
    "path": ["EXTERIOR", "common"],
    "door": {
      "id": "D1",
      "type": "900x2100",
      "wall": "south",
      "wall_type": "EXTERIOR",
      "wall_id": "wall_common_south"
    },
    "note": "D1 on south exterior wall of OPEN_PLAN common"
  }
}
```

**Verification:** 
1. Door exists in DB
2. Door's wall is tagged EXTERIOR
3. Door connects EXTERIOR to named interior space

**Compiler requirement:** Must tag walls as EXTERIOR/INTERIOR during generation.

#### ALL_ROOMS_REACHABLE

**Convention:** Every space (including PORCH, GARAGE) must have a path from entry. Spaces with `opens_to: EXTERIOR` (like PORCH) are reachable by definition but must still connect to main house.

```json
{
  "status": "PROVEN",
  "witness": {
    "entry_point": "common",
    "entry_door": "D1",
    "paths": {
      "bilik_utama": {
        "path": ["common", "bilik_utama"],
        "via": "D2"
      },
      "bilik_mandi": {
        "path": ["common", "bilik_mandi"],
        "via": "D3"
      },
      "tandas": {
        "path": ["common", "tandas"],
        "via": "D3"
      },
      "bilik_2": {
        "path": ["common", "bilik_2"],
        "via": "D2"
      },
      "bilik_3": {
        "path": ["common", "bilik_3"],
        "via": "D2"
      },
      "anjung": {
        "path": ["common", "anjung"],
        "via": "D1",
        "note": "PORCH accessed via main entry"
      }
    },
    "unreachable": []
  }
}
```

**Verification:**
1. Every space in building appears in paths OR unreachable
2. Each path's door exists and connects the named spaces
3. `unreachable` list must be empty for PASS

**Compiler requirement:** Must include ALL spaces, not just rooms. PORCH/GARAGE omission = witness generation failure.

#### WINDOWS_ON_EXTERIOR

```json
{
  "status": "PROVEN",
  "witness": {
    "windows": [
      {
        "id": "W1_bilik_utama",
        "room": "bilik_utama",
        "wall_direction": "west",
        "wall_type": "EXTERIOR",
        "valid": true
      },
      {
        "id": "W1_common",
        "room": "common", 
        "wall_direction": "south",
        "wall_type": "EXTERIOR",
        "valid": true
      }
    ],
    "violations": []
  }
}
```

#### ROOF_COVERS_ALL

```json
{
  "status": "PROVEN",
  "witness": {
    "method": "corner_containment",
    "roof_polygon": [
      [-0.6, -0.6], [10.5, -0.6], [10.5, 9.1], [-0.6, 9.1]
    ],
    "rooms": {
      "common": {
        "corners": [[1.3, 2.3], [6.8, 2.3], [6.8, 8.5], [1.3, 8.5]],
        "all_inside": true
      },
      "bilik_utama": {
        "corners": [[0, 2.3], [3.1, 2.3], [3.1, 5.4], [0, 5.4]],
        "all_inside": true
      }
    },
    "uncovered": []
  }
}
```

#### ROOMS_ENCLOSED

```json
{
  "status": "PROVEN",
  "witness": {
    "rooms": {
      "bilik_utama": {
        "wall_loop": ["wall_north", "wall_east", "wall_south", "wall_west"],
        "closed": true,
        "euler_characteristic": 2
      },
      "common": {
        "wall_loop": ["wall_south", "wall_east", "wall_north", "wall_west"],
        "closed": true,
        "euler_characteristic": 2,
        "note": "OPEN_PLAN - perimeter only"
      }
    }
  }
}
```

#### ROOMS_IN_ENVELOPE

```json
{
  "status": "PROVEN",
  "witness": {
    "envelope_bbox": {
      "min": [0, 0, 0],
      "max": [9.9, 8.5, 2.8]
    },
    "rooms": {
      "bilik_utama": {
        "bbox": {"min": [0, 2.3, 0], "max": [3.1, 5.4, 2.8]},
        "inside": true
      },
      "common": {
        "bbox": {"min": [1.3, 2.3, 0], "max": [6.8, 8.5, 2.8]},
        "inside": true
      }
    },
    "violations": []
  }
}
```

---

## Complete Example: TB-LKTN Witness File

```json
{
  "version": "1.0",
  "building": "TB-LKTN",
  "generated": "2025-01-30T15:30:00Z",
  "compiler_version": "0.30.0",
  
  "claims": {
    "FOUNDATION_GROUNDED": {
      "status": "PROVEN",
      "witness": {
        "foundation_id": "foundation_001",
        "z_value": 0.0,
        "tolerance": 0.005
      }
    },
    
    "ENTRY_EXISTS": {
      "status": "PROVEN",
      "witness": {
        "path": ["EXTERIOR", "common"],
        "door": {
          "id": "D1",
          "type": "900x2100",
          "wall": "south",
          "wall_type": "EXTERIOR"
        }
      }
    },
    
    "ALL_ROOMS_REACHABLE": {
      "status": "PROVEN",
      "witness": {
        "entry_point": "common",
        "paths": {
          "bilik_utama": {"path": ["common", "bilik_utama"], "via": "D2"},
          "bilik_mandi": {"path": ["common", "bilik_mandi"], "via": "D3"},
          "tandas": {"path": ["common", "tandas"], "via": "D3"},
          "bilik_2": {"path": ["common", "bilik_2"], "via": "D2"},
          "bilik_3": {"path": ["common", "bilik_3"], "via": "D2"}
        },
        "unreachable": []
      }
    },
    
    "WINDOWS_ON_EXTERIOR": {
      "status": "PROVEN",
      "witness": {
        "windows": [
          {"id": "W1_common", "room": "common", "wall_direction": "south", "wall_type": "EXTERIOR", "valid": true},
          {"id": "W1_bilik_utama", "room": "bilik_utama", "wall_direction": "west", "wall_type": "EXTERIOR", "valid": true},
          {"id": "W3_bilik_mandi", "room": "bilik_mandi", "wall_direction": "west", "wall_type": "EXTERIOR", "valid": true},
          {"id": "W3_tandas", "room": "tandas", "wall_direction": "west", "wall_type": "EXTERIOR", "valid": true},
          {"id": "W1_bilik_2", "room": "bilik_2", "wall_direction": "east", "wall_type": "EXTERIOR", "valid": true},
          {"id": "W1_bilik_3", "room": "bilik_3", "wall_direction": "east", "wall_type": "EXTERIOR", "valid": true}
        ],
        "violations": []
      }
    },
    
    "ROOF_COVERS_ALL": {
      "status": "PROVEN",
      "witness": {
        "method": "corner_containment",
        "roof_polygon": [[-0.6, -0.6], [10.5, -0.6], [10.5, 9.1], [-0.6, 9.1]],
        "rooms": {
          "common": {"corners": [[1.3, 2.3], [6.8, 2.3], [6.8, 8.5], [1.3, 8.5]], "all_inside": true},
          "bilik_utama": {"corners": [[0, 2.3], [3.1, 2.3], [3.1, 5.4], [0, 5.4]], "all_inside": true},
          "bilik_mandi": {"corners": [[0, 5.4], [1.3, 5.4], [1.3, 6.9], [0, 6.9]], "all_inside": true},
          "tandas": {"corners": [[0, 6.9], [1.3, 6.9], [1.3, 8.5], [0, 8.5]], "all_inside": true},
          "bilik_2": {"corners": [[6.8, 2.3], [9.9, 2.3], [9.9, 5.4], [6.8, 5.4]], "all_inside": true},
          "bilik_3": {"corners": [[6.8, 5.4], [9.9, 5.4], [9.9, 8.5], [6.8, 8.5]], "all_inside": true}
        },
        "uncovered": []
      }
    },
    
    "ROOMS_ENCLOSED": {
      "status": "PROVEN",
      "witness": {
        "rooms": {
          "bilik_utama": {"wall_count": 4, "closed": true, "euler": 2},
          "bilik_mandi": {"wall_count": 4, "closed": true, "euler": 2},
          "tandas": {"wall_count": 4, "closed": true, "euler": 2},
          "bilik_2": {"wall_count": 4, "closed": true, "euler": 2},
          "bilik_3": {"wall_count": 4, "closed": true, "euler": 2},
          "common": {"wall_count": 4, "closed": true, "euler": 2, "note": "OPEN_PLAN perimeter"}
        }
      }
    },
    
    "ROOMS_IN_ENVELOPE": {
      "status": "PROVEN",
      "witness": {
        "envelope_bbox": {"min": [0, 0, 0], "max": [9.9, 8.5, 2.8]},
        "rooms": {
          "common": {"inside": true},
          "bilik_utama": {"inside": true},
          "bilik_mandi": {"inside": true},
          "tandas": {"inside": true},
          "bilik_2": {"inside": true},
          "bilik_3": {"inside": true}
        },
        "violations": []
      }
    }
  },
  
  "summary": {
    "total_claims": 7,
    "proven": 7,
    "unprovable": 0,
    "skipped": 0
  }
}
```

---

## Implementation Plan

### Phase W1: Witness Generator (Compiler Side)

**Location:** `src/main/java/com/bim/compiler/witness/`

```
witness/
├── WitnessGenerator.java       # Main coordinator
├── WitnessWriter.java          # JSON output
├── claims/
│   ├── Claim.java              # Base interface
│   ├── FoundationClaim.java
│   ├── EntryClaim.java
│   ├── ReachabilityClaim.java
│   ├── WindowPlacementClaim.java
│   ├── RoofCoverageClaim.java
│   ├── EnclosureClaim.java
│   └── ContainmentClaim.java
└── WitnessReport.java          # Data structure
```

**Integration Point:**

```java
// In BuildingCompiler.java or BuildingWriter.java
public void compile(BuildingDefinition def) {
    // ... existing compilation ...
    
    BuildingSpec spec = /* compiled result */;
    
    // NEW: Generate witnesses (non-blocking)
    try {
        WitnessGenerator witness = new WitnessGenerator(spec);
        WitnessReport report = witness.generateAll();
        WitnessWriter.write(report, outputDir.resolve("witness.json"));
    } catch (Exception e) {
        log.warn("Witness generation failed (non-blocking): {}", e.getMessage());
        // Compilation continues - witnesses are optional
    }
}
```

### Phase W2: Witness Verifier (Independent Tool)

**Location:** `tools/witness-verifier/`

```
witness-verifier/
├── WitnessVerifier.java        # Main entry point
├── verifiers/
│   ├── ClaimVerifier.java      # Base interface
│   ├── FoundationVerifier.java
│   ├── EntryVerifier.java
│   ├── ReachabilityVerifier.java
│   ├── WindowVerifier.java
│   ├── RoofVerifier.java
│   ├── EnclosureVerifier.java
│   └── ContainmentVerifier.java
├── VerificationReport.java
└── WitnessVerifierTest.java
```

**Key Principle:** Verifier reads ONLY:
1. `witness.json` - the claims and proofs
2. `building.db` - to confirm witness data matches reality

**Verifier does NOT:**
- Re-derive paths
- Re-calculate containment
- Re-analyze geometry

It only **checks that the witness is true**.

### Phase W3: Integration with Sanity Checker

The Phase 0 Sanity Checker becomes a **two-stage** system:

```
Stage 1: Witness Verification (if witness.json exists)
         Fast, checks proofs
         
Stage 2: Independent Sanity Checks (if no witness OR witness fails)
         Slower, derives from geometry
```

```java
public SanityReport check(Path dbPath) {
    Path witnessPath = dbPath.resolveSibling("witness.json");
    
    if (Files.exists(witnessPath)) {
        // Stage 1: Verify witnesses
        WitnessVerifier verifier = new WitnessVerifier(witnessPath, dbPath);
        VerificationReport witnessReport = verifier.verify();
        
        if (witnessReport.allPassed()) {
            return SanityReport.fromWitness(witnessReport);
        }
        
        // Witness failed - fall through to Stage 2
        log.warn("Witness verification failed, running independent checks");
    }
    
    // Stage 2: Independent sanity checks (existing logic)
    return runIndependentChecks(dbPath);
}
```

---

## Verification Logic Examples

### FoundationVerifier

```java
public class FoundationVerifier implements ClaimVerifier {
    
    @Override
    public VerificationResult verify(JsonObject witness, Database db) {
        double zValue = witness.get("z_value").getAsDouble();
        double tolerance = witness.get("tolerance").getAsDouble();
        String foundationId = witness.get("foundation_id").getAsString();
        
        // Check 1: Foundation exists in DB
        Element foundation = db.findElement(foundationId);
        if (foundation == null) {
            return VerificationResult.fail("Foundation not found in DB");
        }
        
        // Check 2: Witness Z matches DB Z
        double dbZ = foundation.getBbox().getMinZ();
        if (Math.abs(dbZ - zValue) > tolerance) {
            return VerificationResult.fail(
                "Witness Z=%f but DB Z=%f", zValue, dbZ);
        }
        
        // Check 3: Z is at ground level
        if (Math.abs(zValue) > tolerance) {
            return VerificationResult.fail(
                "Foundation Z=%f, should be 0", zValue);
        }
        
        return VerificationResult.pass();
    }
}
```

### ReachabilityVerifier

```java
public class ReachabilityVerifier implements ClaimVerifier {
    
    @Override
    public VerificationResult verify(JsonObject witness, Database db) {
        JsonObject paths = witness.getAsJsonObject("paths");
        List<String> failures = new ArrayList<>();
        
        for (String roomName : paths.keySet()) {
            JsonObject pathInfo = paths.getAsJsonObject(roomName);
            JsonArray path = pathInfo.getAsJsonArray("path");
            String doorId = pathInfo.get("via").getAsString();
            
            // Check: Door exists and connects the rooms
            Element door = db.findElement(doorId);
            if (door == null) {
                failures.add(roomName + ": door " + doorId + " not found");
                continue;
            }
            
            String fromRoom = path.get(0).getAsString();
            String toRoom = path.get(1).getAsString();
            
            if (!door.connects(fromRoom, toRoom)) {
                failures.add(roomName + ": door " + doorId + 
                    " doesn't connect " + fromRoom + " to " + toRoom);
            }
        }
        
        if (failures.isEmpty()) {
            return VerificationResult.pass();
        } else {
            return VerificationResult.fail(String.join("; ", failures));
        }
    }
}
```

### RoofCoverageVerifier

```java
public class RoofCoverageVerifier implements ClaimVerifier {
    
    @Override
    public VerificationResult verify(JsonObject witness, Database db) {
        // Get roof polygon from witness
        JsonArray roofPoly = witness.getAsJsonArray("roof_polygon");
        Polygon2D roof = parsePolygon(roofPoly);
        
        // Verify each room's corners
        JsonObject rooms = witness.getAsJsonObject("rooms");
        List<String> failures = new ArrayList<>();
        
        for (String roomName : rooms.keySet()) {
            JsonObject roomInfo = rooms.getAsJsonObject(roomName);
            JsonArray corners = roomInfo.getAsJsonArray("corners");
            boolean claimedInside = roomInfo.get("all_inside").getAsBoolean();
            
            // Verify claim matches reality
            boolean actuallyInside = true;
            for (JsonElement corner : corners) {
                Point2D pt = parsePoint(corner);
                if (!roof.contains(pt)) {
                    actuallyInside = false;
                    break;
                }
            }
            
            if (claimedInside != actuallyInside) {
                failures.add(roomName + ": witness claims inside=" + 
                    claimedInside + " but actual=" + actuallyInside);
            }
        }
        
        if (failures.isEmpty()) {
            return VerificationResult.pass();
        } else {
            return VerificationResult.fail(String.join("; ", failures));
        }
    }
}
```

---

## Output: Verification Report

```
╔══════════════════════════════════════════════════════════════╗
║              WITNESS VERIFICATION REPORT                     ║
╠══════════════════════════════════════════════════════════════╣
║ Building: TB-LKTN                                            ║
║ Witness:  output/tb_lktn_witness.json                        ║
║ Database: output/tb_lktn.db                                  ║
║ Date:     2025-01-30 15:45:00                                ║
╚══════════════════════════════════════════════════════════════╝

[1/7] FOUNDATION_GROUNDED
      Witness: z_value = 0.0
      ✓ VERIFIED: Foundation at ground level

[2/7] ENTRY_EXISTS  
      Witness: EXTERIOR → common via D1
      ✓ VERIFIED: Entry door D1 exists on exterior wall

[3/7] ALL_ROOMS_REACHABLE
      Witness: 6 paths from entry
      ✓ VERIFIED: All rooms reachable
        - bilik_utama via D2 ✓
        - bilik_mandi via D3 ✓
        - tandas via D3 ✓
        - bilik_2 via D2 ✓
        - bilik_3 via D2 ✓

[4/7] WINDOWS_ON_EXTERIOR
      Witness: 6 windows, all on exterior walls
      ✓ VERIFIED: All windows on exterior

[5/7] ROOF_COVERS_ALL
      Witness: 6 rooms, all corners inside roof
      ✓ VERIFIED: Roof covers entire building

[6/7] ROOMS_ENCLOSED
      Witness: 6 rooms, all closed polygons
      ✓ VERIFIED: All rooms properly enclosed

[7/7] ROOMS_IN_ENVELOPE
      Witness: 6 rooms, all inside envelope
      ✓ VERIFIED: All rooms inside building envelope

══════════════════════════════════════════════════════════════
VERDICT: ✓ ALL WITNESSES VERIFIED (7/7)

The compiler's proofs are valid.
This building is mathematically sound.
══════════════════════════════════════════════════════════════
```

---

## Failure Report Example

```
╔══════════════════════════════════════════════════════════════╗
║              WITNESS VERIFICATION REPORT                     ║
╠══════════════════════════════════════════════════════════════╣
║ Building: broken_house                                       ║
║ Witness:  output/broken_house_witness.json                   ║
║ Database: output/broken_house.db                             ║
╚══════════════════════════════════════════════════════════════╝

[1/7] FOUNDATION_GROUNDED
      Witness: z_value = 0.0
      ✓ VERIFIED

[2/7] ENTRY_EXISTS  
      Witness: EXTERIOR → living via D1
      ✗ FAILED: Door D1 not found in database
      
      The compiler claimed an entry door exists, but it doesn't.
      This indicates a bug in the compiler, not just invalid geometry.

[3/7] ALL_ROOMS_REACHABLE
      Witness: 4 paths from entry
      ✗ FAILED: 
        - ensuite: door D5 doesn't connect living to ensuite
        
      The compiler claimed a path exists, but the door doesn't
      actually connect these rooms.

══════════════════════════════════════════════════════════════
VERDICT: ✗ WITNESS VERIFICATION FAILED (5/7)

2 claims could not be verified.
Either the witness is wrong (compiler bug) or the geometry is wrong.

Recommendation: Run independent sanity checks to determine root cause.
══════════════════════════════════════════════════════════════
```

---

## Definition of Done

### Phase W1: Witness Generator
- [ ] WitnessGenerator.java creates witnesses for all 7 claims
- [ ] WitnessWriter.java outputs valid JSON
- [ ] Integration point in BuildingCompiler (non-blocking)
- [ ] TB-LKTN generates complete witness file
- [ ] Unit tests for each claim generator

### Phase W2: Witness Verifier
- [ ] Independent tool (no compiler imports)
- [ ] Verifiers for all 7 claims
- [ ] Reads only witness.json and .db
- [ ] Clear pass/fail report
- [ ] Test with valid and invalid witnesses

### Phase W3: Integration
- [ ] Sanity Checker uses witness verification first
- [ ] Falls back to independent checks if no witness
- [ ] Combined report format
- [ ] End-to-end test: DSL → compile → witness → verify

---

## Future Extensions

| Extension | Description |
|-----------|-------------|
| **More claims** | Add claims as failure modes are discovered |
| **Witness signing** | Cryptographic proof that witness came from specific compiler version |
| **Partial witnesses** | Generate witnesses for what's provable, skip complex claims |
| **Witness diff** | Compare witnesses between versions to detect regressions |

---

## Lessons Learned: Sanity Checker Findings (January 2025)

The Phase 0 Sanity Checker ran against TB-LKTN and found real issues that informed this specification:

### Finding 1: Foundation 150mm Below Z=0

**Sanity Checker detected:** Foundation minZ = -0.15, expected Z=0

**Root cause:** Ambiguous convention - is Z=0 the top or bottom of foundation?

**Resolution:** Convention established: **Foundation TOP = Z=0** (floor level). Witness now includes both `top_z` and `bottom_z` with explicit note.

### Finding 2: No Entry Door on Perimeter

**Sanity Checker detected:** D1 exists but not recognized as entry door

**Root cause:** Walls not tagged as EXTERIOR/INTERIOR during compilation. Checker couldn't determine if door's wall was on building perimeter.

**Resolution:** 
1. Compiler must tag walls with type (EXTERIOR, INTERIOR, PARTY)
2. Witness must include `wall_type` and `wall_id` for entry door
3. Verification confirms door is on EXTERIOR wall

### Finding 3: Porch (Anjung) Not Connected

**Sanity Checker detected:** PORCH space has no door connection

**Root cause:** DSL authoring error - `opens_to:` missing for porch. Protocol validation didn't catch it.

**Resolution:**
1. `ALL_ROOMS_REACHABLE` witness must include ALL spaces, not just rooms
2. If space missing from paths, witness generation fails (not silent omission)
3. Protocol should enforce: `porch_if_present: { opens_to: interior_space }`

### Key Insight

> The Sanity Checker finds issues. The Witness System **prevents** them by forcing the compiler to make explicit claims that can be verified.

Issues found by Sanity Checker → inform Witness claims → compiler must prove these claims → issues prevented at source.

---

## Future Enhancements (Roadmap)

### WITNESS-FUTURE-001: Hash Provenance

**Status:** TODO
**Priority:** Medium
**Added:** 2026-02-02

**Purpose:** Add cryptographic proof that output came only from declared input through declared code — no interference possible.

**Current state:**
```
Witness records:
  - Claim statuses (PROVEN, SKIPPED, UNPROVABLE)
  - Evidence data
  - Timestamp

Missing:
  - Input file hash
  - Output file hash
  - Compiler version
```

**Enhanced state:**
```json
{
    "building": "TB-LKTN",
    "provenance": {
        "input_hash": "sha256:abc123...",
        "output_hash": "sha256:def456...",
        "code_version": "git:7bc4db7",
        "compiler_jar_hash": "sha256:789xyz...",
        "timestamp": "2026-02-02T21:30:00Z"
    },
    "claims": { ... }
}
```

**Implementation tasks:**
```
TODO: HASH-001 — Add SHA-256 hashing utility
TODO: HASH-002 — Hash input .bim file before compilation
TODO: HASH-003 — Hash output .db file after writing
TODO: HASH-004 — Record git commit or JAR hash of compiler
TODO: HASH-005 — Add provenance section to witness JSON
TODO: HASH-006 — Verification command (bim-verify)
```

**Verification command:**
```bash
bim-verify output/tb_lktn_witness.json

Output:
  Input hash:  MATCH (tb_lktn.bim unchanged)
  Output hash: MATCH (tb_lktn.db not tampered)
  Code version: 7bc4db7 (reproducible)

  VERDICT: Output is authentic product of recorded input + code
```

**Related practices:**
- Reproducible builds (Debian, Tor Browser)
- SLSA Framework (Google supply-chain security)
- Content-addressable storage (Git, Docker)

---

### WITNESS-FUTURE-002: Witness Signing

**Status:** TODO
**Priority:** Low
**Added:** 2026-02-02

**Purpose:** Cryptographically sign witness files to prove they came from a specific compiler instance.

**Use case:** Third-party auditor can verify witness was generated by trusted compiler, not manually fabricated.

**Implementation:**
```
TODO: SIGN-001 — Generate compiler keypair
TODO: SIGN-002 — Sign witness JSON with private key
TODO: SIGN-003 — Embed signature in witness file
TODO: SIGN-004 — Verification with public key
```

---

### WITNESS-FUTURE-003: Witness Diff / Regression Detection

**Status:** TODO
**Priority:** Medium
**Added:** 2026-02-02

**Purpose:** Compare witnesses between versions to detect regressions.

**Use case:**
```bash
bim-witness-diff v1.0/tb_lktn_witness.json v1.1/tb_lktn_witness.json

Output:
  BEDROOM_WINDOW: PROVEN → PROVEN (unchanged)
  MEP_NO_STRUCTURAL_CLASH: PROVEN → UNPROVABLE (REGRESSION!)
    Evidence: clashes_found 0 → 3
```

**Implementation:**
```
TODO: DIFF-001 — Witness comparison utility
TODO: DIFF-002 — Highlight regressions (PROVEN → UNPROVABLE)
TODO: DIFF-003 — Track evidence value changes
TODO: DIFF-004 — CI integration (fail on regression)
```

---

### WITNESS-FUTURE-004: LINTEL_AT_HEAD_HEIGHT Claim

**Status:** TODO (identified in Phase 50)
**Priority:** High
**Added:** 2026-02-02

**Purpose:** Verify every lintel's minZ equals opening head height ± tolerance.

**Background:** Phase 50 discovered window lintels were placed at Z=1.2m (window height) instead of Z=2.1m (sill + window). The bug was caught by MEP_NO_STRUCTURAL_CLASH only because switches happened to be nearby. A dedicated claim would catch this directly.

**Implementation:**
```
TODO: LINTEL-001 — Collect all lintels with their Z positions
TODO: LINTEL-002 — Collect all openings with head heights
TODO: LINTEL-003 — Match lintel to opening
TODO: LINTEL-004 — Verify lintel.minZ ≈ opening.headHeight (±50mm tolerance)
TODO: LINTEL-005 — Evidence: list of lintel-opening pairs with Z values
```

---

### WITNESS-FUTURE-005: BEAM_SPAN_LIMIT Claim

**Status:** TODO (identified in Phase 50)
**Priority:** Medium
**Added:** 2026-02-02

**Purpose:** Verify beam spans are within construction type limits.

**Background:** StructuralPlacer places beams for large-span rooms, but doesn't verify the spans are within structural limits for the construction type (e.g., 8m for steel, 6m for concrete).

**Implementation:**
```
TODO: BEAM-001 — Collect all beams with their spans
TODO: BEAM-002 — Get construction type from BuildingDefinition
TODO: BEAM-003 — Lookup max span for construction type
TODO: BEAM-004 — Verify each beam span ≤ limit
TODO: BEAM-005 — Evidence: beam list with spans and limits
```

---

*Specification v1.2 - February 2026 (Updated for Phase 50 + Hash Provenance roadmap)*
*"The compiler proves its work. The verifier checks the proofs."*
