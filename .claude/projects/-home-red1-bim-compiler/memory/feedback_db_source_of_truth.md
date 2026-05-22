---
name: DB single source of truth
description: deploy/buildings/ is canonical for building DBs, OCI is deployed copy, never upload from backup
type: feedback
---

**deploy/buildings/ is the ONLY source for building DB uploads.** OCI `bim-ootb/buildings/` is the deployed copy. They must match.

**Why:** On May 22, a session uploaded 5 DBs from a March 2023 backup snapshot — old 5-column schema, no bbox_x. This overwrote the correct Node.js-extracted DBs. The fix required merging good schema + BOM data and re-uploading. The merged DBs were then copied back to deploy/buildings/ to establish single source of truth.

**How to apply:** NEVER upload from `backup/`, `input/`, or ad-hoc locations. ALWAYS from `deploy/buildings/`. If re-extracting a building, the result must have both bbox columns AND BOM tables before uploading. Git repo (bim-ootb) has no DBs — .gitignore excludes them.
