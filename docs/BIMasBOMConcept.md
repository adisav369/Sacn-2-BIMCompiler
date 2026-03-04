# BIM as BOM — Dimension Model

*Category + Owner + SpaceSize: three orthogonal dimensions on M_BOM*

> **Governing principle:** A BOM product (M_Product) is neutral. Its relationship to the
> order (BIM, via C_DocType.DocSubType) determines ownership. Its category (M_BomCategory)
> determines function. Its SpaceSize (AABB on M_BOM_Line) determines fit.
> Name-based coupling (`SH_LIVING_SET`) violates this — the refactor eliminates it.
>
> **§11.37 migration:** `m_bom.c_bpartner` → `m_bom.doc_sub_type`. `C_BPartner` in iDempiere
> = the customer/vendor. Pattern scoping now via `C_DocType.DocSubType` (SH/DX/TB/TE/ST).

---

## §1. ERD — Flattened for BIM

In iDempiere, M_Product sits between M_BOM and everything else (category, vendor,
pricing, inventory). In a BIM compiler, there is no purchasing or inventory lifecycle.
**M_Product is flattened into M_BOM.** A leaf item is simply an M_BOM with no
M_BOM_Line children.

```
M_BomCategory ──────┐
                     ▼
C_DocType ────► M_BOM ──► M_BOM_Line ──► M_BOM (child, recursive)
(WHO=DocSubType) (= M_Product + M_BOM merged)
                     ▲
BIM ─────────────────┘  ──► BIMLine ──────► PP_Order_Node ──► PP_Order_NodeProduct
(= C_Order)          │      (WHAT)          (HOW)             (params)
                     │                         │
                     └──► CO_EmptySpaceLine ◄──┘
                          (WHERE = S_Resource, spatial workstation)
```

**Three-concern separation (PP_ model):**
- **C_OrderLine** = WHAT to build (order topics: element identity + BOM entry point)
- **PP_Order_Node** = HOW to produce (iDempiere name: verb invocation targeting S_Resource/ESLine)
- **CO_EmptySpaceLine** = WHERE it goes (S_Resource: spatial workstation with capacity)
- **M_Product** = WITH WHAT dimensions (catalog product master)

| iDempiere | BIM Table | Actual table | Purpose |
|-----------|-----------|--------------|---------|
| M_Product_Category | **M_BomCategory** | `M_BomCategory` | Functional type: LI, BD, KT, FR, ST, etc. |
| M_Product + M_BOM | **M_BOM** | `m_bom` | Product + assembly merged — one table |
| M_BOM_Line | **M_BOM_Line** | `m_bom_line` | Parent→child + placement offsets + SpaceSize |
| M_Attribute | **M_Attribute** | `m_attribute` | Product-level attributes (ports, clearances, UBBL) |
| C_DocType | **C_DocType** | `C_DocType` | DocBaseType (RE/CO/IN) + DocSubType (SH/DX/TB/TE/ST). Replaces c_bpartner scoping (§11.37). |
| C_BPartner | **C_BPartner** | `C_BPartner` | iDempiere: customer/vendor. Reserved for future real business partners. |
| C_Order | **BIM** (Construction Order) | `c_order` | The building work order (scoped by C_DocType.DocSubType) |
| C_OrderLine | **BIMLine** (WHAT) | `c_orderline` | Order topics — element identity + family_ref |
| PP_Order_Node | **PP_Order_Node** (HOW) | `PP_Order_Node` | Production operation targeting S_Resource (same iDempiere name) |
| PP_Order_NodeProduct | **PP_Order_NodeProduct** | `PP_Order_NodeProduct` | Structured verb parameters (same iDempiere name) |
| S_Resource | **ESLine** (WHERE) | `co_empty_space_line` | Spatial workstation with capacity |
| M_Product.Weight/Volume | **SpaceSize** | `m_bom_line.space_*_mm` | 3D AABB = the spatial UOM |

**Why flatten?** In iDempiere, M_Product serves purchasing, inventory, pricing — concerns
absent from a BIM compiler. The M_BOM IS the product. `IsBOM(Y/N)` becomes implicit:
if an M_BOM has M_BOM_Line children, it is a BOM parent. If not, it is a leaf item.
The recursive link `M_BOM_Line.child → M_BOM` replaces the M_Product intermediary.

---

## §2. Three Dimensions of an M_BOM

### 2.1 BOMCategory — M_BomCategory (WHAT type of assembly)

Functional category. Determines WHAT the BOM contains.

| Code | Name | BOM Level | Example |
|------|------|-----------|---------|
| `LI` | Living | ROOM | Piano + Sofa arrangement + buffers |
| `BD` | Bedroom | ROOM | Bed + SideTables + Wardrobe + buffers |
| `KT` | Kitchen | ROOM | Cabinets + Counter + Sink |
| `BT` | Bathroom | ROOM | Toilet + Basin + Shower |
| `DN` | Dining | ROOM | Table + Chairs |
| `FR` | Furniture | SET/ITEM | Individual piece at ~4th BOM layer |
| `L1` | Level 1 | FLOOR | Ground floor assembly |
| `L2` | Level 2 | FLOOR | Upper floor assembly |
| `ST` | Space | any | Buffer/empty space (variable AABB) |
| `UN` | Unit | UNIT | Complete building unit |

**Current `SH_LIVING_SET` becomes:** `BOMCategory='LI'`, named by its dimensions
(field-AABB convention), owned by SH.

**Naming convention:** BOM names describe design/dimensions, not the building.
Example: `LIVING_4645x3308` (field name + AABB). The user sees category LI + owner SH
and knows the identity without encoding it into the name.

### 2.2 C_BPartner — Construction Building Pattern (WHO)

The construction building pattern. Stored on `m_bom.C_BPartner`.

| Code | Meaning |
|------|---------|
| `SH` | Ifc4_SampleHouse vendor |
| `DX` | Ifc2x3_Duplex vendor |
| `TB` | TB-LKTN (Citizen Home) vendor |
| `TE` | Terminal vendor |

**Scoping rule:** A BIM (building order) has a `C_BPartner`. It can only reference
M_BOMs WHERE `C_BPartner = building.C_BPartner` OR `C_BPartner IS NULL` (generic).
This replaces the old `BOMCategory='SH'` which conflated owner with category.

**iDempiere parallel:**
- `ad_building` (BIM = C_Order) → has a main `C_BPartner` (the contractor/designer)
- `m_bom_line` (M_BOM_Line) → references child M_BOM + its Category
- `m_bom.C_BPartner` (C_BPartner on M_Product) → who designed/supplied this BOM
- A BIM selects M_BOMs WHERE `C_BPartner = BIM.C_BPartner` (or NULL for generic)

### 2.3 SpaceSize — UOM/Qty (HOW MUCH space this BOM occupies)

Mandatory dimension on every M_BOM_Line. Stored as full 3D AABB.

```
m_bom_line (= M_BOM_Line):
    space_width_mm   INTEGER   -- X extent in mm
    space_depth_mm   INTEGER   -- Y extent in mm
    space_height_mm  INTEGER   -- Z extent in mm
```

**Two kinds of SpaceSize:**

| Kind | LOD-dependent | Source | Example |
|------|---------------|--------|---------|
| **Fixed** | Y | From IFC geometry (ad_product_dim) | Sofa: 2000×800×450mm |
| **Variable** | N | Buffer/spacer — absorbs remaining space | Buffer: computed at resolve time |

- Fixed items (LOD=Y) have `space_*_mm` = `ad_product_dim.width/depth/height * 1000`
  (read-only, derived from geometry)
- Buffer items (`BOMCategory='ST'`) have variable SpaceSize — they fill whatever
  the parent has left after fixed children are subtracted

---

## §3. Buffer Space as M_BOM_Line Children — Part of the BOM Construct

### 3.1 Buffers are integral to BOM.db

Buffer children (BOMCategory='ST') are **explicit M_BOM_Line records in BOM.db**.
They are not computed at compile time. They are not inferred from gaps. They are part
of the BOM construct — as real as the Piano or the Sofa.

A room SET BOM (e.g. `SH_LIVING_SET` with parent width=9069mm) has M_BOM_Lines in
interleaved sequence — fixed items alternate with filler elements:

```
seq 10: Piano         (fixed, width=1371)
seq 20: BUFFER        (filler, width=2049)     ← between Piano and Sofa
seq 30: Sofa          (fixed, width=2000)
seq 40: BUFFER        (filler, width=2049)     ← between Sofa and Sofa_B
seq 50: Sofa_B        (fixed, width=1600)
         TOTAL: 1371 + 2049 + 2000 + 2049 + 1600 = 9069 ✓
```

N fixed items produce **N−1 interstitial fillers**, one between each consecutive pair.
Fillers are created by `Filler.fill()` (DAO) or `TopologyWriter.fillBuffers()` (JDBC).

Without filler children, `Parent.SpaceSize != SUM(children.SpaceSize)`.
The BOM construct is **incomplete without its fillers**, just as a bill of materials
is incomplete without its spacers and gaskets. Fillers are real IFC elements —
they ensure no strewn furniture and confirm every arrangement as ground truth.

**When this BOM is copied to C_OrderLine.BOM.BOMLine, buffers transfer verbatim.**
The BOM tab on C_OrderLine is a complete copy — fixed items, sub-BOMs, AND buffer
children with their SpaceSize. All relationships, all spatial info, intact as
reference. The compiler reads this complete construct, not BOM.db directly.

### 3.2 Buffer does NOT travel with the child

When TB-LKTN compiler looks for a LoveSofa set to fit a smaller room, it finds the
set by SpaceSize comparison. **The sofa set does not carry its buffer space.**
Buffer belongs to the PARENT's M_BOM_Line, not the child's.

The chooser falls through to lesser SpaceSize items when exact fit isn't available.
SpaceSize comparison is the selection mechanism.

### 3.3 The arrangement is the parent's concern

Children (Piano, LoveSofa, Dining) each occupy their own fitting space within the
parent. Their relative arrangement (dx/dy offsets, wall rules) is declared by
the parent's M_BOM_Lines — the parent's bill of materials.

Buffer children fill the gaps. Different parents can have different buffer sizes
for the same fixed children.

---

## §4. Critical Invariant — Axis Model

**Axis semantics** (strip packing model):

| Axis | Aggregation | Meaning |
|------|-------------|---------|
| Width (`space_width_mm`) | **SUM** | Strip packing along host wall — items + fillers tile the parent |
| Depth (`space_depth_mm`) | **MAX** | Clearance into room — deepest child defines the envelope |
| Height (`space_height_mm`) | **MAX** | Clearance vertical — tallest child defines the envelope |

```
Width:  parent.space_width_mm  == SUM(child.space_width_mm)   -- must equal
Depth:  MAX(child.space_depth_mm)  <= parent.space_depth_mm   -- must fit
Height: MAX(child.space_height_mm) <= parent.space_height_mm  -- must fit
```

**This must hold at every BOM level.** If it fails on ANY axis at any
level, the spatial model is broken. This is the **W-SPACESIZE-1** witness gate.

This invariant is verified in BOM.db — it is a property of the assembly design,
not of any particular construction. When the BOM is copied to C_OrderLine.BOM.BOMLine,
the invariant transfers intact because all children (including fillers) copy verbatim.

For variable (filler) children, only width is distributed:
`filler.space_width_mm = (parent.space_width_mm - SUM(fixed_children.space_width_mm)) / N_fillers`

Fillers have `space_depth_mm = 0`, `space_height_mm = 0` — depth and height are
clearance axes, not additive. The filler is a width-only bounding box.

---

## §5. Schema Changes

### 5.1 M_BomCategory — new lookup table (= M_Product_Category)

```sql
CREATE TABLE M_BomCategory (
    M_BomCategory_ID TEXT PRIMARY KEY,
    Name             TEXT NOT NULL,
    Description      TEXT,
    IsActive         INTEGER DEFAULT 1
);

INSERT INTO M_BomCategory VALUES ('LI', 'Living',    'Living room settings', 1);
INSERT INTO M_BomCategory VALUES ('BD', 'Bedroom',   'Bedroom settings', 1);
INSERT INTO M_BomCategory VALUES ('KT', 'Kitchen',   'Kitchen settings', 1);
INSERT INTO M_BomCategory VALUES ('BT', 'Bathroom',  'Bathroom/toilet settings', 1);
INSERT INTO M_BomCategory VALUES ('DN', 'Dining',    'Dining settings', 1);
INSERT INTO M_BomCategory VALUES ('FR', 'Furniture', 'Leaf furniture items (~4th BOM layer)', 1);
INSERT INTO M_BomCategory VALUES ('ST', 'Space',     'Buffer/empty space (variable AABB)', 1);
INSERT INTO M_BomCategory VALUES ('L1', 'Level 1',   'Ground floor assembly', 1);
INSERT INTO M_BomCategory VALUES ('L2', 'Level 2',   'Upper floor assembly', 1);
INSERT INTO M_BomCategory VALUES ('UN', 'Unit',      'Complete building unit', 1);
```

### 5.2 m_bom (= M_BOM) — add C_BPartner, repurpose BOMCategory

```sql
ALTER TABLE m_bom ADD COLUMN C_BPartner TEXT DEFAULT NULL;
-- BOMCategory: repurpose from building code (SH/DX/TB) to functional category (LI/BD/KT/FR/ST/...)
-- FK: REFERENCES M_BomCategory(M_BomCategory_ID)
```

### 5.3 m_bom_line (= M_BOM_Line) — add SpaceSize + qty columns

```sql
ALTER TABLE m_bom_line ADD COLUMN space_width_mm  INTEGER DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN space_depth_mm  INTEGER DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN space_height_mm INTEGER DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN qty             INTEGER DEFAULT 1;
```

SpaceSize is full 3D AABB for ALL children including buffers:
- Fixed children (LOD=Y): derived from `ad_product_dim.width/depth/height * 1000`
- Buffer children (M_BomCategory='ST'): computed as parent minus fixed children

**qty (factorization):** A BOM is a recipe — "3 eggs" not "egg, egg, egg". The qty column
expresses "N of the same type" in a single BOM line, following iDempiere's
`C_OrderLine.QtyOrdered` pattern. Defaults to 1 (backwards-compatible with SH/DX
residential BOMs where each line is one item). Terminal (Stone 3) sets qty > 1 to factor
out repetition: 33,324 roof plates with only 14 unique sizes become ~90 BOM lines instead
of 33,324. At compile time, each qty-expanded instance becomes a separate c_orderline row
carrying its unique position. The BOM is the factored form; the order lines are the
expanded form. See `TheRosettaStoneStrategy.txt §FACTORIZED BOM MODEL` for evidence and
the full factorization rationale.

### 5.4 C_Order (Construction Order) — add C_BPartner

```sql
ALTER TABLE c_order ADD COLUMN C_BPartner TEXT DEFAULT NULL;
```

---

## §6. Data Migration (SH Example)

### Before:
```
m_bom: SH_LIVING_SET  BOMCategory='SH'  bom_level='SET'
```

### After:
```
M_BOM:  LIVING_4645x3308  BOMCategory='LI'  C_BPartner='SH'  bom_level='ROOM'
  M_BOM_Lines:
    Piano          BOMCategory='FR'  space=1500×600×1200  (fixed, LOD=Y)
    Sofa_Set       BOMCategory='FR'  space=2000×800×450   (fixed, sub-BOM)
    Loveseat       BOMCategory='FR'  space=1600×800×450   (fixed, LOD=Y)
    Buffer_NW      BOMCategory='ST'  space=variable       (absorbs remainder)
    Buffer_NE      BOMCategory='ST'  space=variable       (absorbs remainder)

  INVARIANT: 4645mm (width) = Piano.w + Sofa.w + Loveseat.w + Buffer_NW.w + Buffer_NE.w
```

---

## §7. Verification Gates

- **W-SPACESIZE-1**: For every BOM parent, `parent.SpaceSize == SUM(children.SpaceSize)`.
  SQL query across all active M_BOMs. Zero violations = PASS.
- **W-OWNER-1**: No BIM references an M_BOM with a different `C_BPartner`
  (unless C_BPartner IS NULL = generic).
- **W-CATEGORY-1**: `BOMCategory` is always a functional code (LI/BD/KT/etc.),
  never a building code (SH/DX/TB).
- Existing gate: `./scripts/run_tests.sh` — baseline must hold.

---

## §8. Naming Convention

BOM IDs follow module-prefix discipline, mapping to iDempiere's layered convention:

| Layer | iDempiere | BIM Table | Example ID |
|-------|-----------|-----------|------------|
| Building order | C_Order | **BIM** (Construction Order) | `Ifc4_SampleHouse` |
| Order line | C_OrderLine | **BIMLine** (Construction Order Details) | placement instance |
| Assembly category | M_Product_Category | **M_BomCategory** | `LI`, `BD`, `KT`, `FR`, `ST` |
| Assembly (product+BOM) | M_Product + M_BOM | **M_BOM** (`m_bom`) | `LIVING_4645x3308` |
| Assembly child | M_BOM_Line | **M_BOM_Line** (`m_bom_line`) | seq 1: Piano, seq 2: Sofa |
| Vendor/designer | C_BPartner | **C_BPartner** | `SH`, `DX`, `TB`, `TE` |
| Spatial UOM | M_Product.Weight/Volume | **SpaceSize** (`space_*_mm`) | 1500×600×1200 |

**BOM names describe design, not ownership.** `LIVING_4645x3308` not `SH_LIVING_SET`.
Ownership is the `C_BPartner` column. Category is the `BOMCategory` FK.
Three orthogonal dimensions, no name coupling.

---

## §9. The Recursive M_BOM Link

The flattened model makes recursion explicit. Every physical layer — slab, floor
contents, roof — is a child. Nothing implied.

```
M_BOM: UNIT_DUPLEX_STD (BOMCategory='UN', C_BPartner='DX')
│
├── M_BOM_Line seq=1 → M_BOM: FLOOR_SLAB_GF       (BOMCategory='SL', leaf — ground slab)
├── M_BOM_Line seq=2 → M_BOM: FLOOR_DX_L1_STD     (BOMCategory='L1', has children ↓)
│   ├── M_BOM_Line seq=1 → M_BOM: LIVING_SET       (BOMCategory='LI', has children ↓)
│   │   ├── M_BOM_Line seq=1 → M_BOM: Piano        (BOMCategory='FR', leaf)
│   │   ├── M_BOM_Line seq=2 → M_BOM: SOFA_AREA    (BOMCategory='FR', has children ↓)
│   │   │   ├── M_BOM_Line seq=1 → M_BOM: Sofa_3Seat      (leaf)
│   │   │   ├── M_BOM_Line seq=2 → M_BOM: Coffee_Table     (leaf)
│   │   │   └── M_BOM_Line seq=3 → M_BOM: Side_Table_Pair  (leaf)
│   │   ├── M_BOM_Line seq=3 → M_BOM: Loveseat     (BOMCategory='FR', leaf)
│   │   ├── M_BOM_Line seq=4 → M_BOM: Buffer_NW    (BOMCategory='ST', variable)
│   │   └── M_BOM_Line seq=5 → M_BOM: Buffer_NE    (BOMCategory='ST', variable)
│   ├── M_BOM_Line seq=2 → M_BOM: DINING_SET       (BOMCategory='DN')
│   ├── M_BOM_Line seq=3 → M_BOM: KITCHEN_SET      (BOMCategory='KT')
│   └── M_BOM_Line seq=4 → M_BOM: TOILET_FIXTURES  (BOMCategory='BT')
├── M_BOM_Line seq=3 → M_BOM: FLOOR_SLAB_L2       (BOMCategory='SL', leaf — upper slab)
├── M_BOM_Line seq=4 → M_BOM: FLOOR_DX_L2_STD     (BOMCategory='L2', has children ↓)
│   └── (bedrooms, bathroom, study — same pattern as L1)
└── M_BOM_Line seq=5 → M_BOM: ROOF_ASSEMBLY       (BOMCategory='RF', has children ↓)
    ├── M_BOM_Line seq=1 → M_BOM: ROOF_STRUCTURE   (leaf — trusses/rafters)
    └── M_BOM_Line seq=2 → M_BOM: ROOF_COVERING    (leaf — tiles/membrane)
```

No `IsBOM` flag needed. Presence of M_BOM_Line children IS the flag.
This maps directly to `m_bom` → `m_bom_line.child_bom_id → m_bom` (existing FK).

The unit IS: slab + floor contents + slab + floor contents + roof. Five top-level
children. Each floor has rooms. Each room has furniture sets. Each set has items
and buffers. The recursion bottoms out at leaf M_BOMs (no M_BOM_Line children).

---

## §10. Relationship to Existing Architecture

- **PREFAB_ARCHITECTURE.md** — the 6-level assembly hierarchy (Level -1 through Level 4)
  maps directly to M_BOM recursion depth. Each level is an M_BOM whose M_BOM_Lines
  reference child M_BOMs at the level below. SpaceSize replaces implicit sizing.
- **RELATIONAL_PLACEMENT_SPEC.md** — C_OrderLine placement rules
  remain unchanged. The BIMLine selects an M_BOM; placement is the BIMLine's concern.
- **Three-Table Authority Rule** — `ad_product_dim` (intrinsic geometry), `m_bom_line`
  (M_BOM_Line: placement + SpaceSize), `m_attribute` (M_Attribute: ports, clearances).
  SpaceSize lives on M_BOM_Line alongside dx/dy/dz — same table, same concern (child placement).
- **BOMCascadeResolver** (§9 of PREFAB_ARCHITECTURE.md) — walks M_BOM → M_BOM_Line
  recursively. SpaceSize enables the invariant check at each level during resolution.
