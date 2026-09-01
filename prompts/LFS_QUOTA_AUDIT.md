# ⚠ DO NOT REMOVE — SCOPE

**Why this file exists.** The user hit GitHub's LFS gigabyte limit *again* after the 2026-08-19
release-archive fix, and asked: *"i still get GH's LFS gigabyte limit which means something is still
taking up lots of quota. Check and advice."* This is the measurement that answered it, plus the
cleanup that was authorised and applied. **Every number here was measured on 2026-09-01, not
estimated.** Read the log/commands before re-deriving anything.

---

## §1 THE MEASUREMENT — where the quota actually goes

Method: enumerate every blob reachable from **remote** refs, keep the ones small enough to be an LFS
pointer, parse `oid`/`size` out of each pointer, dedupe by oid. That is exactly the set GitHub
stores and bills.

```bash
git rev-list --remotes --objects \
 | git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' \
 | awk '$1=="blob" && $3<300 {print $2}' \
 | git cat-file --batch --buffer \
 | awk '/^oid sha256:/{o=$2} /^size /{if(o){print o,$2;o=""}}' | sort -u
```

| repo | unique LFS objects on remote refs | storage |
|---|---|---|
| **bim-compiler** | 303 | **8.53 GB** |
| bim-ootb | 6 | 0.78 GB |
| | | **≈ 9.3 GB against a 1 GB free quota** |

⚠ The GitHub LFS quota is **per ACCOUNT**, not per repo — the two repos share one 1 GB allowance
(and one 1 GB/month bandwidth allowance). So bim-ootb being "clean" was never the whole picture.

### Where bim-compiler's 8.53 GB is
| path | unique bytes | note |
|---|---|---|
| `library/component_library.db` | **5.76 GB — 68 % of the total** | **32 versions × ~180 MB.** One file. Every commit that touched it pushed a whole new blob; LFS stores each one forever. |
| `deploy/dev/buildings/*` | 1.45 GB | LTU_AHouse + Terminal `extracted`/`geo`/`meta`. **All six verified live on OCI** (HTTP 200) — a pure duplicate of what the viewer already fetches. |
| `backup/db_snapshot_20260323_014819/*` | 1.19 GB | A backup directory committed into git. Long since removed from HEAD — **still billed**. |
| everything else | ~0.13 GB | |

### The cause
`bim-compiler/.gitattributes` carried a blanket **`*.db filter=lfs`** rule. It pulled **59 DBs
(1.79 GB) into HEAD** and directly contradicted `CLAUDE.md`'s DB POLICY, which bans binary `.db`
commits *outright and unconditionally, regardless of LFS quota status*. The policy was written; the
already-tracked files were never removed, and the rule kept catching new ones.

---

## §2 CLEARED OF BLAME — the bim-ootb daily release tarballs

The 2026-08-19 `export-ignore` guard in `bim-ootb/.gitattributes` **works**. Verified without
spending more bandwidth, by comparing GitHub's tarball against a local `git archive` of the same tag:

| | bytes |
|---|---|
| GitHub `v1.57.0` source tarball | 95,036,194 |
| local `git archive --format=tar.gz v1.57.0` | 95,040,305 |
| `mesh.db` / `*_geo.db` entries inside the archive | **0** |

The two agree to 0.004 %, and the LFS paths are absent — so the archive carries **no LFS content**
and downloads of it bill **no LFS bandwidth**. The 95 MB is ordinary non-LFS binaries committed
straight into bim-ootb: `buildings/HHS_Office_Federated_extracted.db` 72 MB,
`modeller/Ifc4_Revit_extracted.db` 38 MB, `erp/ad_seed.db` 26 MB, `modeller/Terminal_meta.db` 18 MB,
`modeller/lib/kernel/occt-wasm.wasm` 21 MB. Regular egress, not LFS — a separate (smaller) concern.

**Do not re-open the release-cron theory.** It is measured closed.

---

## §3 ⛔ THE THING TO UNDERSTAND BEFORE ANY CLEANUP

**Deleting files from HEAD frees nothing.** GitHub bills every LFS object *ever pushed*, keeps it
after the file leaves HEAD (the 1.19 GB `backup/` snapshot proves this — gone from HEAD for months,
still counted), and offers **no self-serve purge**. Only a history rewrite plus a GitHub Support
ticket actually reclaims storage.

So the work splits in two, and only the first half is cheap:
1. **Stop the growth** — remove the LFS rules and untrack the DBs. Done, §4.
2. **Reclaim the 8.53 GB** — history rewrite + support ticket, or pay. Not done, §5.

---

## §4 ✅ APPLIED 2026-09-01 (user authorised: *"OK"*) — growth stopped

1. **`.gitattributes` rewritten** — all three LFS rules removed (`*.db`,
   `library/component_library.db`, `DAGCompiler/lib/input/Hospital_extracted.db`), replaced by a
   comment block stating the ban and pointing here.
2. **59 DBs untracked** with `git rm --cached` — **files kept on disk**, verified 59/59 still
   present after the operation. Nothing was deleted from the working tree.
3. **`.gitignore`**: repo-wide `*.db` / `*.db-wal` / `*.db-shm`, with `!migration/*.sql` and
   `!**/patches/*.sql` kept explicit — the SQL patch path is how DB content is *supposed* to travel.

Safety checks run before committing:
- `actions/checkout@v4` in `.github/workflows/ci.yml` defaults to `lfs: false` and no workflow reads
  any of the 59 paths → **CI unaffected**.
- The six largest (`LTU_AHouse_extracted/geo/meta`, `Terminal_extracted/geo/meta`) all return
  **HTTP 200 from OCI** `…/b/bim-ootb/o/buildings/` → nothing becomes unreachable.
- 11 other tracked `.db` paths are **0 bytes** (empty placeholders) and were left tracked; a
  `.gitignore` entry never untracks an already-tracked file, so they are unaffected either way.

**Effect: HEAD goes from 1.79 GB of LFS content to 0. The historical 8.53 GB is untouched.**

---

## §5 ⛔ OPEN — reclaiming the 8.53 GB. Needs a decision, not more measurement

| option | what it costs | what it gets back |
|---|---|---|
| **(a) pay** — one $5/month data pack | $5/mo, zero risk, zero downtime | 50 GB storage + 50 GB bandwidth; the 8.53 GB simply fits |
| **(b) rewrite** — `git lfs migrate export --everything`, force-push every ref, then a GitHub Support ticket to purge the orphaned objects | force-push across a repo with **1411 commits ahead of master on the live branch**, many worktrees, and concurrent sessions; every clone/worktree must be re-cloned | the full 8.53 GB, eventually |

**Recommendation: (a) now that §4 has stopped the growth.** (b) is the highest-risk operation this
project has ever contemplated — a force-push of all refs while other terminals hold worktrees on the
same repo — and it still is not self-service: the purge needs Support either way. Revisit (b) only
if the repo must genuinely become small, and only from a quiet moment with every worktree accounted
for (`git worktree list`, `ahead`/`dirty` both 0).

**Secondary, unrelated to LFS:** bim-ootb ships 95 MB source tarballs *daily* (§2) because real
binaries are committed to it directly. That is ordinary bandwidth, currently unmetered against the
LFS quota, and out of scope here — but the same DB POLICY applies to it and the same `.gitignore`
treatment would shrink every future archive.
