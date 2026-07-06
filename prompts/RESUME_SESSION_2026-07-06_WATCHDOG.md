# ⚠ DO NOT REMOVE — Session resume: watchdog role, 2026-07-06 (supersedes
`prompts/archive/RESUME_SESSION_2026-07-04_WATCHDOG_CLOSE_PM.md`)

**Token conservation is the priority right now.** Read this doc, then go straight to `§NEXT SESSION'S JOB`.
Don't re-derive anything already settled below — the canonical files cited already have the citations.

## §NEXT SESSION'S JOB (the actual ask)
User runs several sessions concurrently across terminals, shuts down and resumes cold, and reports each one
back manually when it's done. **Your job is admin/housekeeping so the user stays free to manage project
vision** — not to build features yourself unless explicitly assigned. Concretely:
1. When the user says a session "is back" / "returned" / pastes its recap, verify the claims independently
   (real commit exists, diff matches, witness actually runs/passes) — don't trust the recap alone. This project
   has caught real false-"already fixed" claims before.
2. Update the ONE canonical `RESUME_<Feature>.md` for that topic with a dated section + git commit link — never
   spawn a new file for a topic that already has one (see `feedback_prompt_file_organization.md` in memory).
3. **Only do step 2 when the user reports it back — not because you noticed via `git fetch` that a branch
   merged while checking something else.** Caught myself jumping ahead on this exact mistake today.
4. Keep `PROGRESS.md`'s `## OPEN → 🔀 CURRENTLY JUGGLED` section current — that's the board the user actually
   uses to avoid repeating status out loud.

## §CLOSED THIS SESSION (2026-07-06)
| What | Where | Verification |
|---|---|---|
| Offline-gateway cache leak (3 sites hit network despite cached data) | bim-ootb `c5ffc08`/#666 | Independently re-ran `witness_offline_gateway_leak.js` myself from a clean checkout, exit 0, all 3 passes green |
| Desktop/mobile installer question | `prompts/OFFLINE_GITHUB_RELEASE_BUNDLE.md` (superseded, kept as record) | Native PWA install already sufficient — confirmed no evidence it was ever unreliable |
| Terminal-scale sweep (grid-tint 163x, STR-race, autosave IDB fallback) | bim-ootb #665 | 6/6 hardened findings independently re-verified fresh, not trusted from recap |
| Unit Class outline box (rendered outside building) | bim-ootb `14154c8`/#668 | Root cause = missing `A.ifc2three` transform, exactly matched a hypothesis I wrote earlier same day; witness extended with a real alignment check |
| HBA sensor/camera POV UX (2-state toggle, closer zoom, IoT jitter) | bim-ootb `a872078`/#671 | Commit message + witnesses (`witness_p10b.js` 67/67, `witness_iot_pov_live.js`) checked, both exist on `main` |
| Pill rail → 4 real drawers (Visual FX/Camera-View/Navigate/Inspect) + 6 isActive bugs + Alt-Z/X 3-state cycle | bim-ootb `409a445`→`e433ac4`/#667→#669, plus a bim-compiler prompts-file gotcha fixed (`7993d34e2`) | Commits + `FRONTEND_LANE_MASTER.md` entry verified real and correctly self-housekept by that session |
| World History undo-spawns-dots (real bug, found live-driving not from a code read) | bim-ootb `a1c56af`/squash `d6bfb80`/#670 | Root cause (fork-don't-wipe gate only checks true tip) + `witness_undo_dot_spawn.js` 8/8, both confirmed on `main` |
| **Camera facing-vectors — SOLVED via maths, not left to manual eyeballing** | `prompts/Viewer/HBA/RESUME_HR_BIM_ASSET.md` § 2026-07-06 | User correctly pushed back on "needs a human" — attempt 1 (sparse room proximity, only 14 rooms) genuinely failed, but attempt 2 (500 real wall/slab/column elements' per-storey mass centroid, door-vs-centroid side test) gave a clean, internally-consistent answer for all 6 doors. Vectors computed and written into the canonical file, ready to wire into `iot.js CAMERAS` + build the actual POV-assume-flight. |

## §STILL OPEN
- **Modeller World History wiring** — RE-CONFIRMED still zero-built as of this session (4-step handoff from
  2026-06-27 never picked up). `prompts/RESUME_WORLD_HISTORY_DEDUP_RESTORE.md` § 2026-07-06. Genuinely
  unstarted work — worth its own session.
- World History Part 2's bomb-clear path and tap-vs-long-press behavior — not re-exercised live this round,
  code looks intact on read only.
- Camera-POV-assume-flight itself — the facing vectors are now solved; wiring them + building the actual
  flight (position at door, orient along facing) is normal, unblocked execution work, not yet done.
- Pill Drawer's "Find box appears on its own" bug — tracked via a new `§FIND_VIS_TRACE` observer, not yet
  reproduced.
- Everything in `PROGRESS.md §OPEN` below the juggled board (UBBL gate, Kernel T4+T5, Modeller onboarding,
  DV_*_rules.sql blocked question, PBR/SSAO, ARC occupancy drift) — unchanged, not touched this session.

## §PROCESS NOTES worth repeating (see `feedback_prompt_file_organization.md` + `feedback_specific_session_naming.md` in memory for full detail)
- One canonical `RESUME_<Feature>.md` per topic. No new files for a topic that already has one. No
  pointer/stub files either — that middle layer was invented, never asked for, and is its own clutter.
- Name new topics specifically (`Viewer/HBA/RESUME_HR_BIM_ASSET.md`), never a broad umbrella ("`_LANE_MASTER`"-style).
- Compact a finished dated section to one-line status + git commit link, not a bare link alone (commit
  messages are usually detailed here, but squash-merges can silently drop that — keep one summary line as
  insurance).
- **Never delete a `prompts/*.md` file without checking it isn't already assigned to a running session** —
  a real near-miss this session (recreated from context, no lasting harm, but don't repeat it).
- Housekeeping triggers on the user's report, not on discovering a merge via `git fetch` while checking
  something else.
- `prompts/` is blanket-`.gitignore`'d in bim-compiler (leftover from an old migration) — a file can sit
  edited on disk ALL session and never actually reach git unless force-added. Worth remembering when a file's
  content seems to have vanished between sessions — check `git status`/`git log -- <file>` before assuming
  loss; it may just never have been committed.
