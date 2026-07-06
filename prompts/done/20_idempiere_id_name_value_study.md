# DONE — iDempiere _ID/Name/Value conformance study
> Commit: (study only — findings in docs/ID_NAME_VALUE_STUDY.md, Tier 1 implemented in 5f94b0ee [S83-tier1])

Impact study — no code changes. Report findings only.

iDempiere convention: every table has three identity columns:
- `_ID` (INTEGER PRIMARY KEY) — surrogate key, referenced by other tables as `_FK`
- `Name` (TEXT NOT NULL) — human-readable label
- `Value` (TEXT NOT NULL) — SearchKey, unique business identifier

Our tables are inconsistent — some have _ID only, some use name as PK,
some lack Value/SearchKey entirely.

Tasks:

1. Query all tables in BOM.db, ERP.db, and output.db. For each table, report
   which of the three columns (_ID, Name, Value) exist and which are missing.
   Present as a table: `| DB | Table | Has _ID | Has Name | Has Value | Current PK |`

2. Identify tables that use non-integer PKs (e.g. TEXT name as PK) —
   these are the most divergent from iDempiere convention.

3. For tables missing Name and/or Value, assess the impact of adding them:
   - Which Java PO classes need new fields?
   - Which INSERT statements in Java source need extra columns?
   - Which migrations would be needed (ALTER TABLE ADD COLUMN)?
   - Which test assertions reference column counts or schema shape?

4. Flag any FK references that use name-based joins instead of _ID-based joins —
   these should eventually migrate to integer FK.

5. Estimate total scope: how many tables, Java files, and migrations affected?

Write findings to docs/ID_NAME_VALUE_STUDY.md. Do NOT create migrations or
edit Java files. This is analysis only.

Reference: https://wiki.idempiere.org/en/Columns#Standard_Columns

## Watchdog Review
Watchdog review of docs/ID_NAME_VALUE_STUDY.md:
- All 5 tasks completed. 120 tables inventoried across 5 DBs.
- 4 tables fully conformant (AD_Org, M_AttributeInstance, PP_Order_NodeProduct, AD_Val_Rule_Param)
- ~70 tables use TEXT PK (most divergent)
- ~40 TEXT-to-TEXT FK refs identified
- 3-tier migration plan proposed: Tier 1 (quick wins, ~30 ALTERs), Tier 2 (core ERP, HIGH risk, 50+ Java files), Tier 3 (ad_* config tables, medium risk)
- Study is thorough and credible. No code changes made. Ready for user decision on migration scope.
