-- Phase 118C: Align ad_room_slot FURNITURE slots with ad_space_type_furniture
-- Idempotent — safe to re-run

-- Fix OFFICE: WORKSTATION_SET → ROOM_FURNITURE (matches ad_space_type_furniture)
UPDATE ad_room_slot
   SET assembly_id = 'ROOM_FURNITURE'
 WHERE room_type = 'OFFICE'
   AND slot_name = 'FURNITURE'
   AND assembly_id = 'WORKSTATION_SET';

-- Add missing FURNITURE slots for room types that have bomId in ad_space_type_furniture
INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, assembly_id, version_range, slot_face, slot_priority, is_required)
VALUES ('LOBBY',     'FURNITURE', 'ROOM_FURNITURE', '[1.0,2.0)', 'BACK', 10, 1);

INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, assembly_id, version_range, slot_face, slot_priority, is_required)
VALUES ('OPEN_PLAN', 'FURNITURE', 'ROOM_FURNITURE', '[1.0,2.0)', 'BACK', 10, 1);

INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, assembly_id, version_range, slot_face, slot_priority, is_required)
VALUES ('STAFFROOM', 'FURNITURE', 'ROOM_FURNITURE', '[1.0,2.0)', 'BACK', 10, 1);
