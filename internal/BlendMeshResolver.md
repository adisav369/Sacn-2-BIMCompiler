# BlendMeshResolver — Build-Time Mesh Canonicalisation

## Purpose

Ensure every `element_instances.geometry_hash` in a compiled DB (sandbox, output)
points to a canonical, high-quality mesh in `library.blend` — before the loader ever
sees it. Resolution happens once at build time, not on every frame.

## Principle

```
*_extracted.db   (raw, immutable — never written to)
      ↓
build step: merge all buildings → sandbox_1M.db
      ↓
BlendMeshResolver runs once
  reads rules from component_library.db → geometry_hash_redirect
  UPDATE element_instances SET geometry_hash = canonical_hash
  WHERE geometry_hash IN (deprecated set)
      ↓
sandbox_1M.db — all canonical hashes, ready for runtime
      ↓
loader: hash → library.blend lookup — direct, no rules, no overhead
```

## Rule Source

`component_library.db` table `geometry_hash_redirect`:

| deprecated_hash | canonical_hash | sub_type | note |
|---|---|---|---|
| bae71afd973eed3a | 0d509e532be0f5f2 | pendent | Revit 72-vert → JKR Terminal |
| bd5df7dd600f7582 | 0d509e532be0f5f2 | pendent | Revit 72-vert → JKR Terminal |
| ... | ... | ... | ... |

Rules are managed by `tools/geometry_redirect.py` (admin CLI, interactive).

## Logging Contract

Every resolver run must emit §PROOF lines to stdout so the Log Mandate is satisfied:

```
§RESOLVER START  caller=<script>  db=<filename>  rules=N
§RESOLVER RULE   deprecated=bae71afd  canonical=0d509e53  sub_type=pendent  rows=1318
§RESOLVER RULE   deprecated=bd5df7dd  canonical=0d509e53  sub_type=pendent  rows=36
§RESOLVER SKIP   rule=a11f25b4  no matching rows in this DB
§RESOLVER DONE   db=<filename>  rules_applied=N  rows_updated=N  elapsed=0.03s
```

If `geometry_hash_redirect` table is absent or empty — log and return 0 (not an error).
If a rule updates 0 rows — log as SKIP (rule doesn't apply to this DB, normal).

## Resolver Logic (build_sandbox_1M.py integration)

After merging all `*_extracted.db` into sandbox:

```python
def apply_mesh_redirects(sandbox_conn, library_db_path):
    """Apply geometry_hash_redirect rules to element_instances in sandbox."""
    lib = sqlite3.connect(library_db_path)
    rules = lib.execute(
        "SELECT deprecated_hash, canonical_hash FROM geometry_hash_redirect"
    ).fetchall()
    lib.close()

    if not rules:
        return 0

    total = 0
    for deprecated, canonical in rules:
        cur = sandbox_conn.execute(
            "UPDATE element_instances SET geometry_hash = ? WHERE geometry_hash = ?",
            (canonical, deprecated))
        if cur.rowcount:
            print(f"§PROOF RESOLVE {deprecated[:16]} → {canonical[:16]}  rows={cur.rowcount}")
            total += cur.rowcount
    return total
```

Call after merge, before sandbox is sealed:
```python
resolved = apply_mesh_redirects(sandbox_conn, LIBRARY_DB)
print(f"§PROOF RESOLVER_DONE  {resolved} element_instances updated")
```

## Scope — Stateless, Portable

The resolver is a single stateless function:
```python
apply_mesh_redirects(conn, library_db_path)
```
Any DB with an `element_instances` table is a valid target. Drop it anywhere:

| Call site | When | Effect |
|---|---|---|
| After IFC extraction | primary — fixes bad hash at entry point | `*_extracted.db` resolved immediately |
| After sandbox merge | safety net — catches anything missed | `sandbox_1M.db` canonical by redundancy |
| After output.db compile | final guard | compile output always clean |
| Any future federated DB | automatic | same function, zero changes |

IFC files are never touched. Everything downstream is pipeline output — fair game.
The rules live in one place (`component_library.db`). The function is portable.

## Runtime Impact

Zero. Loader reads canonical hash directly from sandbox, looks it up in `library.blend`
by name. No redirect table consulted at runtime. No extra DB open. No rule evaluation.

## Candidate Items for Resolution

**Rule before redirecting any element type:**
> Low vertex count alone is not sufficient. The hashes must be the **same product type**
> with different mesh quality — not different products that happen to differ in complexity.
> A BIM coordinator must confirm "Building X uses the same product as Terminal, just
> poorly modelled" before a redirect is created.

Sprinkler redirect is valid: pendent=pendent, upright=upright, same JKR family across buildings.

Elements scanned (2026-04-13) and their status:

| Element | Verdict | Reason |
|---|---|---|
| **Sprinkler** | DONE ✓ | Same JKR product, clear quality gap |
| **Damper** | SKIP | Hospital=round balancing, Terminal=Ruskin CD60GS rectangular — different products |
| **AHU** | SKIP | Different sizes per project — Terminal AHU ≠ Hospital AHU |
| **Diffuser** | SKIP | Terminal itself has 8-vert diffusers; not all Terminal = best |
| **Valve** | SKIP | 60 types — gate/butterfly/check all different shapes |
| **Water closet** | SKIP | Terminal has no WC mesh |
| **Urinal** | SKIP | HITOS has best mesh (765v), not Terminal |
| **Pump** | SKIP | WBDG has best (4671v), Terminal only 474v |

Revisit when BIM coordinator confirms product equivalence across buildings.
Run `tools/geometry_redirect.py --element-filter "damper"` to re-analyse at any time.

## Rule Expansion

`geometry_hash_redirect` is Rule Type 1 (exact hash match). Future rule types:

| Type | Criteria | Example |
|---|---|---|
| 1 | Exact hash match | bae71afd → 0d509e532be0f5f2 |
| 2 | Category + quality | FP + pendent + vertex_count < 500 → canonical |
| 3 | Dims match | sprinkler + 15mm orifice → standard_15mm_hash |

Types 2–3 require resolver to join `elements_meta` at build time. Type 1 is sufficient now.

## Relationship to AD_Client

Each element carries `elements_meta.building` (interim `AD_Client.Value`). The resolver
operates across ALL buildings in the sandbox — building ownership is never changed,
only the geometry_hash pointer. Hospital FP elements remain Hospital FP after resolution.

## Files

| File | Role |
|---|---|
| `tools/geometry_redirect.py` | Admin CLI — manages rules in component_library.db |
| `scripts/build_sandbox_1M.py` | Calls `apply_mesh_redirects()` after merge |
| `library/component_library.db` | Rule store (`geometry_hash_redirect` table) |
| `internal/DLOD_SPEC.md §15` | Notes the lod_manager BLOB violation (pending fix) |

## Pending

- [ ] Add `apply_mesh_redirects()` call to `build_sandbox_1M.py`
- [ ] Revert the `fetch_blobs_from_library` redirect patch in `lod_manager.py`
      (it will be dead code once sandbox is pre-resolved)
- [ ] When lod_manager switches from BLOBs to `library.blend` link=True,
      apply redirect before hash lookup in both lod_manager and S180 operator
      (interim only — unnecessary once sandbox build step lands)
