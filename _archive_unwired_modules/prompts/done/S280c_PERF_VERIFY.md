# ⚠ DO NOT REMOVE — S280c Perf Verification
# Scope: verify rendering performance after system reboot
# Read the log after every run.

## Context

S280c deployed 2026-05-27 (commit 47a69ee on bim-ootb main). Fixes:
- threshold ===1 (was ≤5, inflated VRAM)
- consolidation removed (was blocking 3-10s)
- render gate removed (was S280b scope drift)
- MergedMesh fallback when WEBGL_multi_draw absent

Root cause of sluggishness: NVIDIA driver 595.71 installed May 26 but system never rebooted (kernel 6.17.0-22, needs 6.17.0-29). Firefox fell back to Intel iGPU → no multi_draw → 83K draw calls on LTU.

## After Reboot — Verification Steps

### Step 1: Confirm NVIDIA active
```
nvidia-smi --query-gpu=name,driver_version --format=csv,noheader
```
Expected: `NVIDIA GeForce RTX 4060 Max-Q, 595.71.05`

### Step 2: Load LTU in browser, check console
Expected logs:
```
§S280c_MULTI_DRAW YES — BatchedMesh optimal
§MAIN_JS v34 loaded
§S280c_PERF_REPORT ──────────────────────────────
§S280c  BatchedMesh: ~2500 objects, ~87K slots
§S280c  InstancedMesh: ~13600 objects, ~35K instances
§S280c  TOTAL draw calls: ~16K
```

If `§S280c_MULTI_DRAW NO` → NVIDIA still not active. Check Firefox `about:support` → Graphics.

### Step 3: Fly smoothness test
1. Load LTU (should be §CACHE_HIT for all 3 files now)
2. Press L → cinematic fly during bbox phase → should be smooth
3. Wait for streaming to complete
4. Drag/orbit loaded scene → should be smooth
5. Storey filter (Find panel) → should be instant

### Step 4: Mobile test (phone or DevTools mobile emulation)
Console should show:
```
§S280c_MULTI_DRAW NO — fallback to MergedMesh
```
MergedMesh = ~200 draws for singles. Total ~13.6K. Smooth on mobile GPU.

### Step 5: If still sluggish after reboot + NVIDIA confirmed

Check:
- `about:config` → `webgl.disabled` must be `false`
- `about:config` → `gfx.webrender.all` should be `true`
- Kill stale Claude CLI processes: `pkill -f 'claude' && pgrep claude`
- Check swap: `free -h` — swap should be near 0 after fresh boot

## DONE criteria
- §S280c_MULTI_DRAW YES in console
- LTU fly smooth during bbox AND after streaming
- Hospital drag smooth
- No freeze after streaming completes
- Mobile: MergedMesh active, orbit smooth

## What NOT to change
- Do NOT re-add render gate (was scope drift)
- Do NOT change threshold from ===1
- Do NOT add consolidation back
- Do NOT mix perf changes into UI sessions (separation of concern)
