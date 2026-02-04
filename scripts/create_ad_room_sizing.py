#!/usr/bin/env python3
"""
Phase 61: Create ad_room_sizing table for room dimension resolution.

Room sizing constraints:
- min_width: Code minimum (egress, accessibility)
- max_aspect: Maximum length:width ratio (avoid bowling alleys)
- preferred_aspect: Target aspect ratio (squarish preferred)
- min_area: Code minimum floor area
- area_per_occupant: For calculating occupancy-based sizing

Resolution algorithm:
1. Given target area and available bounds
2. Calculate width × depth satisfying all constraints
3. Return optimal dimensions or feedback if impossible
"""

import sqlite3
import os

DB_PATH = "database/authority_data.db"

def create_table(conn):
    """Create ad_room_sizing table."""
    conn.execute("DROP TABLE IF EXISTS ad_room_sizing")
    conn.execute("""
        CREATE TABLE ad_room_sizing (
            rule_id INTEGER PRIMARY KEY,
            space_type TEXT NOT NULL,
            min_area_m2 REAL,           -- Code minimum area
            max_area_m2 REAL,           -- Maximum practical area
            min_width_m REAL,           -- Code minimum width
            min_depth_m REAL,           -- Code minimum depth
            max_aspect_ratio REAL,      -- Max width:depth ratio (e.g., 2.0)
            preferred_aspect REAL,      -- Target aspect (1.0 = square)
            area_per_occupant_m2 REAL,  -- For occupancy calculation
            clearance_m REAL,           -- Required clearance from walls
            code_id TEXT,
            clause TEXT,
            notes TEXT
        )
    """)
    print("Created ad_room_sizing table")

def insert_rules(conn):
    """Insert room sizing rules from codes."""

    rules = [
        # =====================================================================
        # RESIDENTIAL - IBC/IRC
        # =====================================================================
        # Bedroom: min 70 sq ft (6.5m²), min 7ft (2.13m) any dimension
        ("BEDROOM", 6.5, 25.0, 2.13, 2.13, 2.0, 1.3, 9.3,  0.6,
         "IRC", "R304.1", "Habitable rooms min 70 sq ft, 7ft dimension"),

        # Living room: min 120 sq ft (11.1m²)
        ("LIVING", 11.1, 50.0, 3.0, 3.0, 2.0, 1.4, 4.6, 0.6,
         "IRC", "R304.2", "Living room min 120 sq ft"),

        # Kitchen: min 50 sq ft (4.6m²) - work triangle considerations
        ("KITCHEN", 4.6, 25.0, 1.8, 2.4, 2.5, 1.5, None, 0.9,
         "IRC", "R304.3", "Kitchen work triangle, min counter space"),

        # Bathroom: min 35 sq ft (3.25m²) for full bath
        ("BATHROOM", 3.25, 12.0, 1.5, 1.8, 2.0, 1.2, None, 0.6,
         "IPC", "405.3", "Min clearances for fixtures"),

        # =====================================================================
        # EDUCATIONAL - UBBL / IBC
        # =====================================================================
        # Classroom: 1.85m² per student (20 sq ft), min 46.5m² (500 sq ft)
        ("CLASSROOM", 46.5, 100.0, 6.0, 6.0, 1.8, 1.2, 1.85, 0.9,
         "UBBL", "Table 3", "Educational - 20 sq ft per occupant"),

        # Staffroom/Office: 4.6m² per occupant
        ("STAFFROOM", 18.0, 60.0, 3.0, 3.0, 2.0, 1.3, 4.6, 0.6,
         "UBBL", "Table 3", "Office - 50 sq ft per occupant"),

        ("OFFICE", 9.0, 40.0, 2.4, 2.4, 2.0, 1.3, 4.6, 0.6,
         "UBBL", "Table 3", "Office - 50 sq ft per occupant"),

        # =====================================================================
        # ASSEMBLY - UBBL / IBC
        # =====================================================================
        # Canteen: 1.4m² per occupant (15 sq ft) - standing
        ("CANTEEN", 30.0, 200.0, 4.0, 4.0, 2.5, 1.5, 1.4, 1.2,
         "UBBL", "Table 3", "Assembly with tables - 15 sq ft"),

        # Hall/Assembly: 0.65m² per occupant (7 sq ft) - concentrated seating
        ("HALL", 50.0, 500.0, 6.0, 8.0, 3.0, 1.8, 0.65, 1.5,
         "UBBL", "Table 3", "Assembly concentrated - 7 sq ft"),

        # Lobby: 0.28m² per occupant (3 sq ft) - standing
        ("LOBBY", 12.0, 100.0, 3.0, 3.0, 3.0, 1.5, 0.28, 1.2,
         "UBBL", "Table 3", "Lobby standing - 3 sq ft"),

        ("WAITING", 12.0, 60.0, 3.0, 3.0, 2.5, 1.4, 1.4, 0.9,
         "UBBL", "Table 3", "Waiting area with seating"),

        # =====================================================================
        # CIRCULATION - UBBL / IBC
        # =====================================================================
        # Corridor: min 1.1m width (44"), educational min 1.8m (6ft)
        ("CORRIDOR", None, None, 1.8, None, 10.0, None, None, 0.0,
         "UBBL", "166", "Educational corridor min 6ft width"),

        # Stair enclosure: min 2.4m × 2.4m for U-turn stair
        ("STAIR_ENCLOSURE", 5.76, 12.0, 2.4, 2.4, 1.5, 1.0, None, 0.0,
         "UBBL", "167", "Stair enclosure dimensions"),

        # =====================================================================
        # STORAGE / SERVICE
        # =====================================================================
        ("STORAGE", 2.0, 20.0, 1.2, 1.2, 3.0, 1.5, None, 0.3,
         None, None, "Storage - no specific code"),

        ("UTILITY", 4.0, 20.0, 1.8, 1.8, 2.0, 1.2, None, 0.6,
         None, None, "Utility room - equipment access"),
    ]

    conn.executemany("""
        INSERT INTO ad_room_sizing (
            space_type, min_area_m2, max_area_m2, min_width_m, min_depth_m,
            max_aspect_ratio, preferred_aspect, area_per_occupant_m2, clearance_m,
            code_id, clause, notes
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, rules)

    print(f"Inserted {len(rules)} room sizing rules")

def print_summary(conn):
    """Print summary of inserted rules."""
    print("\n=== Room Sizing Rules ===")

    cursor = conn.execute("""
        SELECT space_type, min_area_m2, min_width_m, max_aspect_ratio,
               preferred_aspect, area_per_occupant_m2, code_id
        FROM ad_room_sizing
        ORDER BY space_type
    """)

    print(f"{'Space':<15} {'MinArea':>8} {'MinW':>6} {'MaxAsp':>7} {'PrefAsp':>8} {'m²/occ':>7} {'Code':<8}")
    print("-" * 70)

    for row in cursor:
        min_area = f"{row[1]:.1f}" if row[1] else "-"
        min_w = f"{row[2]:.1f}" if row[2] else "-"
        max_asp = f"{row[3]:.1f}" if row[3] else "-"
        pref_asp = f"{row[4]:.1f}" if row[4] else "-"
        occ = f"{row[5]:.2f}" if row[5] else "-"
        code = row[6] or "-"
        print(f"{row[0]:<15} {min_area:>8} {min_w:>6} {max_asp:>7} {pref_asp:>8} {occ:>7} {code:<8}")

def main():
    os.makedirs("database", exist_ok=True)

    conn = sqlite3.connect(DB_PATH)
    try:
        create_table(conn)
        insert_rules(conn)
        conn.commit()
        print_summary(conn)
        print(f"\nDatabase: {DB_PATH}")
    finally:
        conn.close()

if __name__ == "__main__":
    main()
