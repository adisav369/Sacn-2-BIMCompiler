-- Phase 122D: Profile-aware furniture slot dispatch
-- Adds profile column to ad_room_slot, creates CANTEEN_SET and LOBBY_SEAT_SET BOMs.
-- Idempotent: uses IF NOT EXISTS / INSERT OR IGNORE throughout.

-- Step 1: Recreate ad_room_slot with profile column and updated UNIQUE constraint
-- SQLite requires table recreation to change constraints
CREATE TABLE IF NOT EXISTS ad_room_slot_new (
    slot_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    room_type     TEXT NOT NULL,
    slot_name     TEXT NOT NULL,
    assembly_id   TEXT,
    version_range TEXT DEFAULT '[1.0,2.0)',
    slot_face     TEXT,
    slot_priority INTEGER DEFAULT 100,
    is_required   INTEGER DEFAULT 0,
    profile       TEXT DEFAULT NULL,
    UNIQUE(room_type, slot_name, profile)
);

-- Copy existing data (profile defaults to NULL for all existing rows)
INSERT OR IGNORE INTO ad_room_slot_new (slot_id, room_type, slot_name, assembly_id, version_range, slot_face, slot_priority, is_required, profile)
SELECT slot_id, room_type, slot_name, assembly_id, version_range, slot_face, slot_priority, is_required, NULL
FROM ad_room_slot;

DROP TABLE IF EXISTS ad_room_slot;
ALTER TABLE ad_room_slot_new RENAME TO ad_room_slot;

-- Step 2: Create CANTEEN_SET BOM
INSERT OR REPLACE INTO ad_bom (bom_id, bom_name, description, target_ifc_class, group_by, is_active)
VALUES ('CANTEEN_SET', 'Canteen Set', 'Canteen table + chairs for institutional dining', 'IfcElementAssembly', 'ROOM', 1);

-- CANTEEN_SET children: table + 4 chairs (like DINING_SET but with canteen table)
INSERT OR IGNORE INTO ad_bom_child (bom_id, child_ifc_class, role, sequence, is_active)
VALUES ('CANTEEN_SET', 'IfcFurniture', 'TABLE', 1, 1);

INSERT OR IGNORE INTO ad_bom_child (bom_id, child_ifc_class, role, sequence, is_active)
VALUES ('CANTEEN_SET', 'IfcFurniture', 'CHAIR_A', 2, 1);

INSERT OR IGNORE INTO ad_bom_child (bom_id, child_ifc_class, role, sequence, is_active)
VALUES ('CANTEEN_SET', 'IfcFurniture', 'CHAIR_B', 3, 1);

INSERT OR IGNORE INTO ad_bom_child (bom_id, child_ifc_class, role, sequence, is_active)
VALUES ('CANTEEN_SET', 'IfcFurniture', 'CHAIR_C', 4, 1);

INSERT OR IGNORE INTO ad_bom_child (bom_id, child_ifc_class, role, sequence, is_active)
VALUES ('CANTEEN_SET', 'IfcFurniture', 'CHAIR_D', 5, 1);

-- CANTEEN_SET child params: Canteen Table at center, chairs offset
-- TABLE — name_pattern + wall_rule
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'name_pattern', 'Canteen%Table%', 'STRING', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'TABLE';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'wall_rule', 'CENTER', 'STRING', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'TABLE';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dx', '0.0', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'TABLE';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dy', '0.0', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'TABLE';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dz', '0.0', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'TABLE';

-- CHAIR_A: dx=-0.45, dy=0.8 (facing table)
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'name_pattern', 'Dining_Chair', 'STRING', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_A';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dx', '-0.45', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_A';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dy', '0.8', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_A';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dz', '0.0', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_A';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'face_table', 'true', 'BOOLEAN', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_A';

-- CHAIR_B: dx=0.45, dy=0.8
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'name_pattern', 'Dining_Chair', 'STRING', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_B';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dx', '0.45', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_B';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dy', '0.8', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_B';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dz', '0.0', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_B';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'face_table', 'true', 'BOOLEAN', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_B';

-- CHAIR_C: dx=-0.45, dy=-0.8
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'name_pattern', 'Dining_Chair', 'STRING', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_C';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dx', '-0.45', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_C';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dy', '-0.8', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_C';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dz', '0.0', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_C';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'face_table', 'true', 'BOOLEAN', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_C';

-- CHAIR_D: dx=0.45, dy=-0.8
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'name_pattern', 'Dining_Chair', 'STRING', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_D';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dx', '0.45', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_D';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dy', '-0.8', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_D';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dz', '0.0', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_D';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'face_table', 'true', 'BOOLEAN', 1
FROM ad_bom_child WHERE bom_id = 'CANTEEN_SET' AND role = 'CHAIR_D';

-- Step 3: Create LOBBY_SEAT_SET BOM (seats only, no desks/monitors)
INSERT OR REPLACE INTO ad_bom (bom_id, bom_name, description, target_ifc_class, group_by, is_active)
VALUES ('LOBBY_SEAT_SET', 'Lobby Seat Set', 'Waiting room seats for lobby areas', 'IfcElementAssembly', 'ROOM', 1);

INSERT OR IGNORE INTO ad_bom_child (bom_id, child_ifc_class, role, sequence, child_name_pattern, is_active)
VALUES ('LOBBY_SEAT_SET', 'IfcFurniture', 'GUEST_SEAT', 1, 'Waiting_Room_Seat%', 1);

-- LOBBY_SEAT_SET child params
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'name_pattern', 'Waiting_Room_Seat%', 'STRING', 1
FROM ad_bom_child WHERE bom_id = 'LOBBY_SEAT_SET' AND role = 'GUEST_SEAT';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dx', '0.0', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'LOBBY_SEAT_SET' AND role = 'GUEST_SEAT';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dy', '0.0', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'LOBBY_SEAT_SET' AND role = 'GUEST_SEAT';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, is_active)
SELECT bom_child_id, 'dz', '0.0', 'DOUBLE', 1
FROM ad_bom_child WHERE bom_id = 'LOBBY_SEAT_SET' AND role = 'GUEST_SEAT';

-- Step 4: Profile-specific ad_room_slot entries
-- DINING rooms for Malaysian_Institutional → CANTEEN_SET (priority 5 beats generic priority 10)
INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, assembly_id, slot_face, slot_priority, is_required, profile)
VALUES ('DINING', 'FURNITURE', 'CANTEEN_SET', 'BACK', 5, 1, 'Malaysian_Institutional');

-- LOBBY rooms for Malaysian_Institutional → LOBBY_SEAT_SET (priority 5)
INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, assembly_id, slot_face, slot_priority, is_required, profile)
VALUES ('LOBBY', 'FURNITURE', 'LOBBY_SEAT_SET', 'BACK', 5, 1, 'Malaysian_Institutional');
