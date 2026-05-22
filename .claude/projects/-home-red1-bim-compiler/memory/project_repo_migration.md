---
name: Repo migration DONE
description: bim-ootb repo live at red1oon.github.io/bim-ootb — viewer/ path, no sandbox, git push = deploy
type: project
---

**DONE 2026-05-23.** New repo `red1oon/bim-ootb` is live.

**Structure:** `index.html` (landing), `viewer/viewer.html` (3D viewer), `viewer/*.js` (modules). 25MB, 400 files.

**URL:** `https://red1oon.github.io/bim-ootb/`

**Deploy:** edit in `/home/red1/bim-ootb/` → `git push` → live. No OCI for code. Bump `viewer/sw.js` CACHE_VERSION on each push.

**OCI:** Only `bim-ootb` bucket remains (building DBs). All code buckets (live, dev, full, backup, live2) are legacy.

**Source sync:** Changes should go to `bim-ootb/viewer/` first, then copy back to `bim-compiler/deploy/dev/` until fully deprecated.

**How to apply:** All future browser work targets `bim-ootb` repo. `bim-compiler` keeps Java source + docs as archive.
