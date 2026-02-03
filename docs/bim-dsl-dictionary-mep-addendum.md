# BIM DSL DICTIONARY - MEP SYSTEM ADDENDUM

**Version:** 2.2 (January 2025)  
**Status:** Addition to bim-dsl-dictionary.md  
**Purpose:** Define vocabulary for MEP System graphs (Phase 35+)

---

## Overview

This addendum extends the BIM DSL Dictionary with vocabulary for **MEP System Graphs**—the abstraction that proves MEP connectivity.

**Insert after Section 13 (MEP Dictionary) in the main document.**

---

## 14. SYSTEM (MEP Connectivity Graph)

Systems model MEP connectivity as directed graphs, enabling mathematical proof that all terminals connect to their source.

### Concept

```
SYSTEM
    │
    ├── systemId: Unique identifier
    ├── type: SystemType (what kind of system)
    │
    ├── nodes: []
    │   └── SystemNode (element participating in system)
    │
    └── edges: []
        └── SystemEdge (connection between nodes)
```

### Relationship to SPACE

MEP elements have **two relationships**:

1. **Spatial:** Element is contained in a SPACE (for BOM, validation)
2. **Systemic:** Element is a node in a SYSTEM (for connectivity proof)

```
SPACE "bilik_mandi" type:BATHROOM
    │
    ├── contains: toilet (spatial relationship)
    │
    └── toilet is TERMINAL in waste_system_1 (systemic relationship)
        └── has edge DRAINS_TO → riser → MH1
```

---

## 15. SystemType

Enumeration of MEP system types.

### Values

| SystemType | Description | Traversal Direction | Source Element |
|------------|-------------|---------------------|----------------|
| `PLUMBING_WASTE` | Drainage to septic/sewer | Terminal → Source | MH (Manhole) |
| `PLUMBING_VENT` | Vent to atmosphere | Terminal → Source | Vent Termination |
| `PLUMBING_SUPPLY` | Water supply (hot/cold) | Source → Terminal | Water Meter |
| `ELECTRICAL` | Power distribution | Source → Terminal | DB (Distribution Board) |
| `HVAC_SUPPLY` | Conditioned air supply | Source → Terminal | AHU |
| `HVAC_RETURN` | Return air | Terminal → Source | AHU |
| `FIRE_SUPPRESSION` | Sprinkler system | Source → Terminal | Fire Pump/Tank |

### Traversal Direction

- **Terminal → Source:** Systems where flow goes toward a collection point (drainage, venting)
- **Source → Terminal:** Systems where flow originates from a distribution point (supply, power)

This affects how `isConnected()` and `isComplete()` traverse the graph.

---

## 16. SystemNode

A node in the system graph representing an element's participation in the system.

### Attributes

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| nodeId | string | Yes | Unique identifier within system |
| elementGuid | string | No | Reference to elements_meta.guid (null for external) |
| role | NodeRole | Yes | Role in the system |
| name | string | Yes | Human-readable description |
| properties | map | No | System-specific properties |

### External Nodes

Nodes with `elementGuid = null` represent external elements not modeled in the building:

| External Node | System | Description |
|---------------|--------|-------------|
| MH1 | PLUMBING_WASTE | Manhole (connects to municipal sewer) |
| VENT_TERM | PLUMBING_VENT | Vent termination above roof |
| WATER_METER | PLUMBING_SUPPLY | Connection to municipal supply |
| UTILITY_METER | ELECTRICAL | Connection to power grid |

### Example

```json
{
  "nodeId": "toilet_bilik_mandi",
  "elementGuid": "abc-123-def",
  "role": "TERMINAL",
  "name": "toilet in bilik_mandi",
  "properties": {
    "fixture_type": "WC",
    "trap_size_mm": 100
  }
}
```

---

## 17. NodeRole

Enumeration of roles a node can play in a system.

### Values

| Role | Description | Examples |
|------|-------------|----------|
| `SOURCE` | Origin point of system (start for supply, end for drain) | MH, water meter, DB panel, vent termination, AHU |
| `DISTRIBUTION` | Mid-path element that routes flow | Riser, circuit, trunk line, main duct |
| `TERMINAL` | End-point that consumes/produces | Fixture, outlet, diffuser, sprinkler |
| `CONNECTOR` | Junction that joins paths | Fitting, tee, elbow, junction box |

### Role Distribution Rules

| System | Typical Structure |
|--------|-------------------|
| PLUMBING_WASTE | 1 SOURCE (MH) → N DISTRIBUTION (risers) → M TERMINALS (fixtures) |
| PLUMBING_SUPPLY | 1 SOURCE (meter) → N DISTRIBUTION (risers) → M TERMINALS (fixtures) |
| ELECTRICAL | 1 SOURCE (DB) → N DISTRIBUTION (circuits) → M TERMINALS (outlets) |

---

## 18. SystemEdge

A directed edge connecting two nodes in the system graph.

### Attributes

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| edgeId | string | Yes | Unique identifier |
| fromNodeId | string | Yes | Source node of edge |
| toNodeId | string | Yes | Target node of edge |
| type | EdgeType | Yes | Type of connection |
| properties | map | No | Connection properties |

### Properties by System

| System | Typical Properties |
|--------|-------------------|
| PLUMBING_WASTE | diameter_mm, slope_percent, material |
| PLUMBING_VENT | diameter_mm |
| PLUMBING_SUPPLY | diameter_mm, pressure_kpa, material, insulated |
| ELECTRICAL | cable_size_mm2, circuit_number, phase |

### Example

```json
{
  "edgeId": "edge_toilet_to_riser",
  "fromNodeId": "toilet_bilik_mandi",
  "toNodeId": "riser_bilik_mandi",
  "type": "DRAINS_TO",
  "properties": {
    "diameter_mm": 100,
    "slope_percent": 1.0
  }
}
```

---

## 19. EdgeType

Enumeration of connection types between nodes.

### Values

| EdgeType | Description | Flow Direction | Systems |
|----------|-------------|----------------|---------|
| `FEEDS` | Power/signal flow | from → to | ELECTRICAL |
| `DRAINS_TO` | Waste water flow | from → to | PLUMBING_WASTE |
| `VENTS_TO` | Vent air flow | from → to | PLUMBING_VENT |
| `SUPPLIES` | Water/air supply | from → to | PLUMBING_SUPPLY, HVAC |
| `RETURNS` | Return flow | from → to | HVAC_RETURN |
| `CONNECTS_VERTICAL` | Cross-storey stack connection | upper → lower | PLUMBING_WASTE (multi-storey) |

### Edge Direction Convention

Edges are always directed **from the element toward its destination**:

```
PLUMBING_WASTE: toilet --DRAINS_TO--> riser --DRAINS_TO--> MH
PLUMBING_SUPPLY: meter --SUPPLIES--> riser --SUPPLIES--> toilet
ELECTRICAL: DB --FEEDS--> circuit --FEEDS--> outlet
```

### Multi-Storey Vertical Connections (Phase 38)

For plumbing stacks spanning storeys, use `CONNECTS_VERTICAL`:

```
Upper storey:  toilet_bath_u --DRAINS_TO--> riser_bath_u
                                              │
                                              │ CONNECTS_VERTICAL
                                              ▼
Ground storey: toilet_bath_g --DRAINS_TO--> riser_bath_g --DRAINS_TO--> MH1
```

Properties for `CONNECTS_VERTICAL`:
- `stack_name`: Name of the plumbing stack (e.g., "plumbing_core")
- `from_level`: Storey level of upper riser
- `to_level`: Storey level of lower riser

---

## 20. Graph Operations

### isConnected()

Returns `true` if all terminals can reach/be reached from source.

```
For PLUMBING_WASTE (terminal → source):
  ∀ terminal ∈ TERMINALS: ∃ path from terminal to SOURCE

For PLUMBING_SUPPLY (source → terminal):
  ∀ terminal ∈ TERMINALS: ∃ path from SOURCE to terminal
```

### isComplete()

Returns `true` if `isConnected()` AND no orphaned terminals.

### getPath(from, to)

Returns list of nodeIds forming path between two nodes (BFS).

```
getPath("toilet_bilik_mandi", "MH1")
→ ["toilet_bilik_mandi", "riser_bilik_mandi", "MH1"]
```

### getOrphanedTerminals()

Returns list of TERMINAL nodes with no path to SOURCE.

```
If toilet has no edge to riser:
→ [SystemNode("toilet_bilik_mandi", ...)]
```

---

## 21. Database Schema

### mep_systems Table

```sql
CREATE TABLE mep_systems (
    system_id TEXT PRIMARY KEY,
    system_type TEXT NOT NULL,      -- SystemType enum value
    building_guid TEXT NOT NULL,
    is_connected INTEGER,           -- Boolean: 0 or 1
    is_complete INTEGER,            -- Boolean: 0 or 1
    node_count INTEGER,
    edge_count INTEGER
);
```

### system_nodes Table

```sql
CREATE TABLE system_nodes (
    node_id TEXT PRIMARY KEY,
    system_id TEXT NOT NULL REFERENCES mep_systems(system_id),
    element_guid TEXT,              -- NULL for external nodes
    role TEXT NOT NULL,             -- NodeRole enum value
    name TEXT,
    properties_json TEXT            -- JSON object
);
```

### system_edges Table

```sql
CREATE TABLE system_edges (
    edge_id TEXT PRIMARY KEY,
    system_id TEXT NOT NULL REFERENCES mep_systems(system_id),
    from_node_id TEXT NOT NULL REFERENCES system_nodes(node_id),
    to_node_id TEXT NOT NULL REFERENCES system_nodes(node_id),
    edge_type TEXT NOT NULL,        -- EdgeType enum value
    properties_json TEXT            -- JSON object
);

-- Indexes for graph traversal
CREATE INDEX idx_edges_from ON system_edges(from_node_id);
CREATE INDEX idx_edges_to ON system_edges(to_node_id);
```

---

## 22. Witness Claims

MEPSystem enables witness claims that prove connectivity.

### PLUMBING_WASTE_COMPLETE

**Statement:** All fixtures drain to manhole.

**Witness:**
```json
{
  "status": "PROVEN",
  "witness": {
    "system_id": "waste_system_1",
    "source": "Manhole 1",
    "terminal_count": 2,
    "all_drain_to_source": true,
    "orphaned_terminals": [],
    "drainage_paths": {
      "toilet in bilik_mandi": ["toilet_bilik_mandi", "riser_bilik_mandi", "MH1"],
      "sink in bilik_mandi": ["sink_bilik_mandi", "riser_bilik_mandi", "MH1"]
    }
  }
}
```

### PLUMBING_VENT_COMPLETE

**Statement:** All traps vent to atmosphere.

**Witness:**
```json
{
  "status": "PROVEN",
  "witness": {
    "system_id": "vent_system_1",
    "termination": "Vent Termination",
    "terminal_count": 2,
    "all_vent_to_atmosphere": true,
    "vent_paths": {
      "toilet trap in bilik_mandi": ["toilet_trap_bilik_mandi", "vent_bilik_mandi", "VENT_TERM"],
      "sink trap in bilik_mandi": ["sink_trap_bilik_mandi", "vent_bilik_mandi", "VENT_TERM"]
    }
  }
}
```

### PLUMBING_SUPPLY_COMPLETE

**Statement:** Water meter supplies all fixtures.

**Witness:**
```json
{
  "status": "PROVEN",
  "witness": {
    "system_id": "supply_system_1",
    "source": "Water Meter",
    "terminal_count": 2,
    "all_supplied_from_source": true,
    "supply_paths": {
      "toilet in bilik_mandi": ["WATER_METER", "supply_riser_bilik_mandi", "toilet_bilik_mandi"],
      "sink in bilik_mandi": ["WATER_METER", "supply_riser_bilik_mandi", "sink_bilik_mandi"]
    }
  }
}
```

### STOREYS_VERTICALLY_CONSISTENT (Phase 38)

**Statement:** Rooms with vertical constraints (above:, stack:) maintain proper alignment.

**Witness:**
```json
{
  "status": "PROVEN",
  "witness": {
    "vertical_constraints": {
      "total": 2,
      "valid": 2,
      "details": {
        "master": {"type": "above", "target": "living", "xy_aligned": true, "z_ordered": true}
      }
    },
    "stack_alignments": {
      "total": 1,
      "aligned": 1,
      "details": {
        "plumbing_core": {"aligned": true, "max_delta_x_m": 0.0, "max_delta_y_m": 0.0}
      }
    },
    "tolerance_m": 0.005,
    "violations": []
  }
}
```

### ELECTRICAL_CIRCUITS_COMPLETE (Future)

**Statement:** All outlets connected to distribution board.

---

## 23. Example: TB-LKTN Plumbing Systems

### Waste System Graph

```
                    ┌─────────────────────┐
                    │   MH1 (SOURCE)      │
                    │   External manhole  │
                    └──────────▲──────────┘
                               │
                               │ DRAINS_TO
                               │
                    ┌──────────┴──────────┐
                    │  riser_bilik_mandi  │
                    │  (DISTRIBUTION)     │
                    │  100mm waste stack  │
                    └──────────▲──────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              │ DRAINS_TO      │ DRAINS_TO      │
              │                │                │
    ┌─────────┴─────────┐  ┌───┴───────────┐
    │ toilet_bilik_mandi│  │sink_bilik_mandi│
    │  (TERMINAL)       │  │  (TERMINAL)    │
    └───────────────────┘  └────────────────┘
```

### Database Content

```sql
-- mep_systems
INSERT INTO mep_systems VALUES 
  ('waste_system_1', 'PLUMBING_WASTE', 'building_guid', 1, 1, 4, 3);

-- system_nodes  
INSERT INTO system_nodes VALUES
  ('MH1', 'waste_system_1', NULL, 'SOURCE', 'Manhole 1', '{}'),
  ('riser_bilik_mandi', 'waste_system_1', 'guid_riser', 'DISTRIBUTION', 'Waste Riser', '{"diameter_mm":100}'),
  ('toilet_bilik_mandi', 'waste_system_1', 'guid_toilet', 'TERMINAL', 'toilet in bilik_mandi', '{}'),
  ('sink_bilik_mandi', 'waste_system_1', 'guid_sink', 'TERMINAL', 'sink in bilik_mandi', '{}');

-- system_edges
INSERT INTO system_edges VALUES
  ('edge_riser_mh', 'waste_system_1', 'riser_bilik_mandi', 'MH1', 'DRAINS_TO', '{}'),
  ('edge_toilet_riser', 'waste_system_1', 'toilet_bilik_mandi', 'riser_bilik_mandi', 'DRAINS_TO', '{"diameter_mm":100}'),
  ('edge_sink_riser', 'waste_system_1', 'sink_bilik_mandi', 'riser_bilik_mandi', 'DRAINS_TO', '{"diameter_mm":40}');
```

---

## 24. Java Implementation Reference

### Package Structure

```
com.bim.compiler.system/
├── SystemType.java      # Enum
├── NodeRole.java        # Enum
├── EdgeType.java        # Enum
├── SystemNode.java      # Record
├── SystemEdge.java      # Record
└── MEPSystem.java       # Graph class with operations
```

### Key Class: MEPSystem

```java
public class MEPSystem {
    private final String systemId;
    private final SystemType type;
    private final List<SystemNode> nodes;
    private final List<SystemEdge> edges;
    
    // Queries
    public Optional<SystemNode> getSource();
    public List<SystemNode> getTerminals();
    public List<SystemEdge> getEdgesFrom(String nodeId);
    public List<SystemEdge> getEdgesTo(String nodeId);
    
    // Graph operations
    public boolean isConnected();
    public boolean isComplete();
    public List<String> getPath(String from, String to);
    public List<SystemNode> getOrphanedTerminals();
    public List<String> validate();
}
```

---

## 25. Extension Protocol

### Adding a New System Type

1. Add enum value to `SystemType.java`
2. Determine traversal direction (forward or backward)
3. Identify SOURCE element (external or in-model)
4. Define NodeRole for each element type
5. Define EdgeType for connections
6. Modify placer to build graph while placing elements
7. Add `*_COMPLETE` witness claim
8. Document in this addendum

### Adding a New Node Role

1. Add enum value to `NodeRole.java`
2. Document when to use it
3. Ensure graph operations handle it correctly

### Adding a New Edge Type

1. Add enum value to `EdgeType.java`
2. Document which systems use it
3. Define typical properties

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 2.2 | January 2025 | Added CONNECTS_VERTICAL for multi-storey stacks (Phase 38) |
| 2.1 | January 2025 | Initial MEP System addendum (Phase 35-37) |

---

## References

- Phase 35 specification: `phase-35-mep-system-graph-spec.md` (archived)
- Architecture evolution: `bim-compiler-architecture-evolution.md`
- Witness specification: `witness-system-specification.md`

---

*This addendum follows the dictionary principle: vocabulary as data, behavior derived from type. MEP systems extend the grammar without changing the core—they add a new relationship (systemic) alongside the existing spatial relationship.*
