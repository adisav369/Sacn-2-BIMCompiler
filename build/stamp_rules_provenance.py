#!/usr/bin/env python3
"""
stamp_rules_provenance.py — stamp a `rules_meta` provenance table into a mined
rules DB so a STALE modeller copy is detectable (the §DATA-LOCALITY guard: every
modeller/*.db gets its own copy built from bim-compiler source + stamped
provenance, and dwInit's §-log prints it). NON-INVENT — values are read from the
DB content + git, not typed by hand.

Usage:
  python3 build/stamp_rules_provenance.py <rules.db> <standard> <built_from> [building_class]
    standard      = e.g. "residential" | "large-complex"
    built_from    = e.g. "Ifc2x3_Duplex_Federated.ifc via bake_duplex_rules.py"
    building_class = e.g. "Duplex,SampleHouse,SampleCastle" (optional)
"""
import hashlib, os, sqlite3, subprocess, sys, datetime


def git_sha():
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"],
            cwd=os.path.dirname(os.path.abspath(__file__))).decode().strip()
    except Exception:
        return "unknown"


def content_sha(con):
    """deterministic content hash over the rule rows (detects drift independent of git)."""
    h = hashlib.sha256()
    for t in ("rule_placement", "rule_space_bom", "rule_routing",
              "rule_place_order", "rule_avoidance"):
        try:
            for row in con.execute(f"SELECT * FROM {t} ORDER BY 1,2,3"):
                h.update(repr(row).encode())
        except sqlite3.OperationalError:
            pass
    return h.hexdigest()[:12]


def count(con, t):
    try:
        return con.execute(f"SELECT count(*) FROM {t}").fetchone()[0]
    except sqlite3.OperationalError:
        return 0


def clearance_summary(con):
    try:
        rows = con.execute(
            "SELECT disc_a,disc_b,min_clear_m FROM rule_avoidance ORDER BY min_clear_m").fetchall()
        return "; ".join(f"{a}|{b}={c:.3f}m" for a, b, c in rows[:6])
    except sqlite3.OperationalError:
        return ""


def main():
    if len(sys.argv) < 4:
        sys.exit(__doc__)
    db, standard, built_from = sys.argv[1], sys.argv[2], sys.argv[3]
    bclass = sys.argv[4] if len(sys.argv) > 4 else ""
    con = sqlite3.connect(db)
    csha = content_sha(con)
    built_at = datetime.datetime.now().replace(microsecond=0).isoformat()
    gsha = git_sha()
    version = f"{datetime.date.today().isoformat()}+{csha}"
    meta = {
        "standard": standard,
        "version": version,
        "content_sha": csha,
        "built_from": built_from,
        "source_db": os.path.basename(db),
        "generator": "bim-compiler/build (bake + remine)",
        "git_sha": gsha,
        "built_at": built_at,
        "building_class": bclass,
        "n_placement": str(count(con, "rule_placement")),
        "n_routing": str(count(con, "rule_routing")),
        "n_avoidance": str(count(con, "rule_avoidance")),
        "clearance_summary": clearance_summary(con),
        "provenance_note": "NON-INVENT: rows traced to measured src_guids; see project_dx_mep_*",
    }
    con.executescript(
        "DROP TABLE IF EXISTS rules_meta;"
        "CREATE TABLE rules_meta(key TEXT PRIMARY KEY, value TEXT);")
    con.executemany("INSERT INTO rules_meta VALUES (?,?)", list(meta.items()))
    con.commit()
    con.close()
    print(f"§STAMP {os.path.basename(db)} standard={standard} version={version}")
    for k, v in meta.items():
        print(f"  {k:18s} {v}")


if __name__ == "__main__":
    main()
