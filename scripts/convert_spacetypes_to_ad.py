#!/usr/bin/env python3
"""
Convert spacetypes.yaml to AD Space Type Tables

Phase 1 of Metadata-Driven Architecture:
Reads config/spacetypes.yaml and populates ad_space_type* tables
in library/BOM.db.

Source: config/spacetypes.yaml (RESEARCHED from IRC/UBBL/MS 1184)
Target: library/BOM.db

Usage:
    python scripts/convert_spacetypes_to_ad.py

Expected output: 38 space types transferred (per spacetypes.yaml count)

Author: BIM Compiler Project
"""

import sqlite3
import yaml
import json
import os
import sys
from typing import Dict, Any, List, Optional

# Paths relative to project root
DB_PATH = "library/BOM.db"
YAML_PATH = "config/spacetypes.yaml"


def load_yaml(yaml_path: str) -> Dict[str, Any]:
    """Load spacetypes.yaml file."""
    with open(yaml_path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)


def convert_space_type(name: str, props: Dict[str, Any]) -> Dict[str, Any]:
    """Convert a single space type from YAML format to AD table format."""
    validation = props.get('validation', {})
    structural = props.get('structural', {})

    return {
        'space_type_id': name,
        'category': props.get('category', 'UNKNOWN'),
        'omniclass_code': props.get('omniclass', '13-00 00 00'),
        'wall_rule': props.get('wall_rule', 'AS_REQUIRED'),
        'is_sleeping_room': 1 if props.get('is_sleeping_room', False) else 0,
        'is_open_plan': 1 if props.get('is_open_plan', False) else 0,
        'is_exterior': 1 if props.get('is_exterior', False) else 0,
        'min_area': validation.get('min_area', 0),
        'min_dimension': validation.get('min_dimension', 0),
        'requires_window': 1 if validation.get('requires_window', False) else 0,
        'requires_egress': 1 if validation.get('requires_egress', False) else 0,
        'structural_grid': 1 if structural.get('structural_grid', False) else 0,
        'beam_max_span': structural.get('beam_max_span', 8.0),
        'code_reference': None,  # Could be enhanced to extract from YAML comments
        'is_active': 1
    }


def convert_aliases(name: str, props: Dict[str, Any]) -> List[Dict[str, str]]:
    """Extract aliases for a space type."""
    aliases = props.get('aliases', [])
    return [{'alias': alias.upper(), 'space_type_id': name} for alias in aliases]


def convert_mep(name: str, props: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    """Convert MEP configuration for a space type."""
    mep = props.get('mep')
    if not mep:
        return None

    plumbing = mep.get('plumbing', {})
    electrical = mep.get('electrical', {})
    hvac = mep.get('hvac', {})

    # Convert plumbing fixtures list to JSON string
    fixtures = plumbing.get('fixtures', [])
    fixtures_json = json.dumps(fixtures) if fixtures else None

    return {
        'space_type_id': name,
        'plumbing_fixtures': fixtures_json,
        'requires_stack': 1 if plumbing.get('requires_stack', False) else 0,
        'requires_vent': 1 if plumbing.get('requires_vent', False) else 0,
        'water_supply': plumbing.get('water_supply', 'none'),
        'light_points': electrical.get('light_points', 0),
        'power_points': electrical.get('power_points', 0),
        'switch_points': electrical.get('switch_points', 0),
        'requires_dedicated': 1 if electrical.get('requires_dedicated', False) else 0,
        'circuit_type': electrical.get('circuit_type', 'general'),
        'requires_exhaust': 1 if hvac.get('requires_exhaust', False) else 0,
        'exhaust_cfm': hvac.get('exhaust_cfm', 0),
        'allows_aircon': 1 if hvac.get('allows_aircon', False) else 0,
        'ventilation_type': hvac.get('ventilation_type', 'natural')
    }


def clear_existing_data(conn: sqlite3.Connection) -> None:
    """Clear existing data from AD tables (for clean re-import)."""
    cursor = conn.cursor()
    cursor.execute("DELETE FROM ad_space_type_mep")
    cursor.execute("DELETE FROM ad_space_type_alias")
    cursor.execute("DELETE FROM ad_space_type")
    conn.commit()
    print("[Convert] Cleared existing AD data")


def insert_space_type(cursor: sqlite3.Cursor, data: Dict[str, Any]) -> None:
    """Insert a single space type record."""
    cursor.execute("""
        INSERT INTO ad_space_type (
            space_type_id, category, omniclass_code, wall_rule,
            is_sleeping_room, is_open_plan, is_exterior,
            min_area, min_dimension, requires_window, requires_egress,
            structural_grid, beam_max_span, code_reference, is_active
        ) VALUES (
            :space_type_id, :category, :omniclass_code, :wall_rule,
            :is_sleeping_room, :is_open_plan, :is_exterior,
            :min_area, :min_dimension, :requires_window, :requires_egress,
            :structural_grid, :beam_max_span, :code_reference, :is_active
        )
    """, data)


def insert_alias(cursor: sqlite3.Cursor, data: Dict[str, str]) -> None:
    """Insert a single alias record."""
    cursor.execute("""
        INSERT INTO ad_space_type_alias (alias, space_type_id)
        VALUES (:alias, :space_type_id)
    """, data)


def insert_mep(cursor: sqlite3.Cursor, data: Dict[str, Any]) -> None:
    """Insert a single MEP record."""
    cursor.execute("""
        INSERT INTO ad_space_type_mep (
            space_type_id, plumbing_fixtures, requires_stack, requires_vent,
            water_supply, light_points, power_points, switch_points,
            requires_dedicated, circuit_type, requires_exhaust, exhaust_cfm,
            allows_aircon, ventilation_type
        ) VALUES (
            :space_type_id, :plumbing_fixtures, :requires_stack, :requires_vent,
            :water_supply, :light_points, :power_points, :switch_points,
            :requires_dedicated, :circuit_type, :requires_exhaust, :exhaust_cfm,
            :allows_aircon, :ventilation_type
        )
    """, data)


def convert_and_insert(conn: sqlite3.Connection, yaml_data: Dict[str, Any]) -> Dict[str, int]:
    """Convert all space types and insert into database."""
    cursor = conn.cursor()
    stats = {'space_types': 0, 'aliases': 0, 'mep_configs': 0}

    for name, props in yaml_data.items():
        if not isinstance(props, dict):
            print(f"[WARNING] Skipping non-dict entry: {name}")
            continue

        # Insert space type
        space_type_data = convert_space_type(name, props)
        insert_space_type(cursor, space_type_data)
        stats['space_types'] += 1

        # Insert aliases
        for alias_data in convert_aliases(name, props):
            try:
                insert_alias(cursor, alias_data)
                stats['aliases'] += 1
            except sqlite3.IntegrityError as e:
                print(f"[WARNING] Duplicate alias '{alias_data['alias']}': {e}")

        # Insert MEP config if present
        mep_data = convert_mep(name, props)
        if mep_data:
            insert_mep(cursor, mep_data)
            stats['mep_configs'] += 1

    conn.commit()
    return stats


def verify_conversion(conn: sqlite3.Connection) -> Dict[str, int]:
    """Verify the conversion by counting records."""
    cursor = conn.cursor()

    cursor.execute("SELECT COUNT(*) FROM ad_space_type")
    space_types = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(*) FROM ad_space_type_alias")
    aliases = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(*) FROM ad_space_type_mep")
    mep_configs = cursor.fetchone()[0]

    return {
        'space_types': space_types,
        'aliases': aliases,
        'mep_configs': mep_configs
    }


def print_sample(conn: sqlite3.Connection, limit: int = 5) -> None:
    """Print sample records for verification."""
    cursor = conn.cursor()

    print("\n[Sample] Space Types:")
    cursor.execute("""
        SELECT space_type_id, category, wall_rule, min_area
        FROM ad_space_type LIMIT ?
    """, (limit,))
    for row in cursor.fetchall():
        print(f"  {row[0]}: {row[1]}, {row[2]}, min_area={row[3]}")

    print("\n[Sample] Aliases:")
    cursor.execute("""
        SELECT alias, space_type_id FROM ad_space_type_alias LIMIT ?
    """, (limit,))
    for row in cursor.fetchall():
        print(f"  {row[0]} -> {row[1]}")

    print("\n[Sample] MEP Configs:")
    cursor.execute("""
        SELECT space_type_id, light_points, power_points, circuit_type
        FROM ad_space_type_mep LIMIT ?
    """, (limit,))
    for row in cursor.fetchall():
        print(f"  {row[0]}: lights={row[1]}, power={row[2]}, circuit={row[3]}")


def main():
    """Main entry point."""
    # Find project root
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    db_path = os.path.join(project_root, DB_PATH)
    yaml_path = os.path.join(project_root, YAML_PATH)

    # Verify files exist
    if not os.path.exists(db_path):
        print(f"[ERROR] Database not found: {db_path}")
        print("[ERROR] Run create_ad_space_type_schema.py first")
        sys.exit(1)

    if not os.path.exists(yaml_path):
        print(f"[ERROR] YAML not found: {yaml_path}")
        sys.exit(1)

    print(f"[Convert] Loading YAML: {yaml_path}")
    yaml_data = load_yaml(yaml_path)
    print(f"[Convert] Found {len(yaml_data)} space types in YAML")

    print(f"[Convert] Opening database: {db_path}")

    try:
        conn = sqlite3.connect(db_path)

        # Clear and re-import
        clear_existing_data(conn)

        # Convert and insert
        stats = convert_and_insert(conn, yaml_data)
        print(f"[Convert] Inserted: {stats['space_types']} space types, "
              f"{stats['aliases']} aliases, {stats['mep_configs']} MEP configs")

        # Verify
        verify_stats = verify_conversion(conn)
        print(f"[Verify] Database contains: {verify_stats['space_types']} space types, "
              f"{verify_stats['aliases']} aliases, {verify_stats['mep_configs']} MEP configs")

        # Print sample
        print_sample(conn)

        # Final validation
        if verify_stats['space_types'] >= 20:  # Expect at least 20 space types
            print(f"\n[SUCCESS] Conversion complete: {verify_stats['space_types']} space types")
        else:
            print(f"\n[WARNING] Only {verify_stats['space_types']} space types (expected ~38)")

    except sqlite3.Error as e:
        print(f"[ERROR] SQLite error: {e}")
        sys.exit(1)
    except yaml.YAMLError as e:
        print(f"[ERROR] YAML parse error: {e}")
        sys.exit(1)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
