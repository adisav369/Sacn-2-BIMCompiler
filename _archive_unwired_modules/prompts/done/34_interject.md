## Course Correction — iDempiere _ID Convention

STOP. Read this before continuing.

The `_int` sidecar columns from Phase A+B were a transitional scaffold.
The goal is iDempiere convention, not a permanent dual-key schema.

### iDempiere convention (what we want)

```sql
CREATE TABLE C_DocType (
    C_DocType_ID    INTEGER PRIMARY KEY AUTOINCREMENT,
    Value           TEXT NOT NULL,   -- old TEXT PK becomes SearchKey
    Name            TEXT,            -- human-readable label
    ...
);
```

`_ID` IS the PK. `Value` holds the old text key. No `_int` suffix columns.

### What you're doing (wrong)

```sql
CREATE TABLE C_DocType (
    C_DocType_ID       TEXT PRIMARY KEY,   -- still TEXT PK
    Value              TEXT,
    C_DocType_ID_int   INTEGER,            -- sidecar, not the real PK
    ...
);
```

This keeps TEXT as PK and adds a useless integer sidecar. It does not
achieve iDempiere conformance.

### Apply to ALL 5 tables

| Table | PK must be | Value (SearchKey) holds |
|---|---|---|
| M_Product_Category | M_Product_Category_ID INTEGER PK | old text like 'RE', 'LIVING' |
| M_Product | M_Product_ID INTEGER PK | old product_id like 'WALL_EXT_150' |
| m_bom | M_BOM_ID INTEGER PK | old bom_id like 'BUILDING_SH_STD' |
| C_Order | C_Order_ID INTEGER PK | old text C_Order_ID |
| C_DocType | C_DocType_ID INTEGER PK | old text C_DocType_ID |

### What to fix now

1. Revert the `_int` sidecar DDL you just wrote in IFCtoBOMPipeline.java
2. Write the correct DDL: `_ID INTEGER PRIMARY KEY`, old text key → `Value`
3. Apply the same pattern to all 5 tables in that method
4. Update any migration SQL or shell workaround that uses `_int` columns
5. Continue with the rest of prompt 34

The rename-copy-drop SQLite pattern from Phase A+B migrations already shows
the right approach — look at `W014_m_product_int_pk.sql` for the template.
