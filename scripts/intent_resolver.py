#!/usr/bin/env python3
"""
Phase 20: Intent Resolver

Parses natural language building descriptions into JSON specs.
Uses spaCy for NLP — no LLM in critical path.

Usage:
    python intent_resolver.py "3 bedroom house with ensuite" output/spec.json
"""

import json
import re
import sys
from pathlib import Path

import spacy

# Load spaCy model
nlp = spacy.load("en_core_web_sm")

# IRC 2021 minimum room sizes (meters)
IRC_MIN_SIZES = {
    "living": (3.0, 3.4),      # 3.0m min dimension, ~11.2m² min
    "bedroom": (2.4, 3.0),     # 2.4m min dimension, 7.0m² min
    "master": (3.0, 3.5),      # Larger for master
    "kitchen": (2.4, 2.7),     # Functional minimum
    "bathroom": (1.5, 2.4),    # 1.5m min, ~3.7m² min
    "ensuite": (1.5, 2.0),     # Smaller than main bath
    "dining": (2.7, 3.0),      # 8m² min
    "study": (2.1, 2.4),       # 5m² min
    "laundry": (1.8, 2.0),     # Functional
    "garage": (3.0, 6.0),      # Single car
    "corridor": (1.0, 2.0),    # Min 900mm width
}

# Default sizes (comfortable, not minimum)
DEFAULT_SIZES = {
    "living": (5.0, 4.0),
    "bedroom": (3.5, 3.5),
    "master": (4.0, 4.0),
    "kitchen": (3.0, 3.0),
    "bathroom": (2.5, 2.5),
    "ensuite": (2.0, 2.5),
    "dining": (3.5, 3.0),
    "study": (3.0, 2.5),
    "laundry": (2.0, 2.0),
    "garage": (6.0, 3.0),
    "corridor": (1.2, 4.0),
}

# Size modifiers
SIZE_MODIFIERS = {
    "large": 1.3,
    "big": 1.3,
    "spacious": 1.25,
    "generous": 1.2,
    "compact": 0.85,
    "small": 0.8,
    "cozy": 0.9,
    "tiny": 0.7,
}

# Building types
BUILDING_TYPES = {
    "house": {"storeys": 1, "rooms_base": ["living", "kitchen"]},
    "townhouse": {"storeys": 2, "rooms_base": ["living", "kitchen"]},
    "apartment": {"storeys": 1, "rooms_base": ["living", "kitchen"]},
    "flat": {"storeys": 1, "rooms_base": ["living", "kitchen"]},
    "unit": {"storeys": 1, "rooms_base": ["living", "kitchen"]},
    "studio": {"storeys": 1, "rooms_base": ["living"]},  # Combined living/kitchen
    "granny flat": {"storeys": 1, "rooms_base": ["living", "kitchen"]},
    "duplex": {"storeys": 2, "rooms_base": ["living", "kitchen"]},
}


def extract_bedroom_count(text):
    """Extract number of bedrooms from text."""
    # Pattern: "3 bedroom", "three bedroom", "3-bedroom", "3 bed"
    patterns = [
        r"(\d+)\s*[-]?\s*bed(?:room)?s?",
        r"(one|two|three|four|five|six)\s*[-]?\s*bed(?:room)?s?",
    ]

    word_to_num = {
        "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6
    }

    for pattern in patterns:
        match = re.search(pattern, text.lower())
        if match:
            val = match.group(1)
            if val.isdigit():
                return int(val)
            return word_to_num.get(val, 1)

    return 1  # Default: 1 bedroom


def extract_bathroom_count(text):
    """Extract number of bathrooms from text."""
    patterns = [
        r"(\d+)\s*[-]?\s*bath(?:room)?s?",
        r"(one|two|three)\s*[-]?\s*bath(?:room)?s?",
    ]

    word_to_num = {"one": 1, "two": 2, "three": 3}

    for pattern in patterns:
        match = re.search(pattern, text.lower())
        if match:
            val = match.group(1)
            if val.isdigit():
                return int(val)
            return word_to_num.get(val, 1)

    # Default based on bedrooms
    bedrooms = extract_bedroom_count(text)
    if bedrooms >= 4:
        return 2
    return 1


def extract_building_type(text):
    """Extract building type from text."""
    text_lower = text.lower()

    # Sort by length descending to match longer types first (townhouse before house)
    sorted_types = sorted(BUILDING_TYPES.items(), key=lambda x: len(x[0]), reverse=True)

    for btype, config in sorted_types:
        if btype in text_lower:
            return btype, config

    # Default to house
    return "house", BUILDING_TYPES["house"]


def extract_modifiers(text):
    """Extract size and feature modifiers."""
    text_lower = text.lower()
    modifiers = {
        "size_factor": 1.0,
        "open_plan": False,
        "ensuite": False,
        "garage": False,
        "study": False,
        "laundry": False,
    }

    # Size modifiers
    for word, factor in SIZE_MODIFIERS.items():
        if word in text_lower:
            modifiers["size_factor"] = factor
            break

    # Feature modifiers
    if "open plan" in text_lower or "open-plan" in text_lower:
        modifiers["open_plan"] = True

    if "ensuite" in text_lower or "en-suite" in text_lower or "en suite" in text_lower:
        modifiers["ensuite"] = True

    if "garage" in text_lower:
        modifiers["garage"] = True

    if "study" in text_lower or "office" in text_lower:
        modifiers["study"] = True

    if "laundry" in text_lower:
        modifiers["laundry"] = True

    return modifiers


def generate_room_list(bedrooms, bathrooms, building_type, modifiers):
    """Generate list of rooms with sizes and constraints."""
    rooms = []
    size_factor = modifiers["size_factor"]

    # Living room
    w, d = DEFAULT_SIZES["living"]
    rooms.append({
        "type": "LIVING",
        "name": "living",
        "width": round(w * size_factor, 1),
        "depth": round(d * size_factor, 1),
        "constraints": {"exterior": "south"},
    })

    # Kitchen (unless studio)
    if building_type != "studio":
        w, d = DEFAULT_SIZES["kitchen"]
        kitchen_constraints = {"adjacent": ["living"]} if modifiers["open_plan"] else {}
        rooms.append({
            "type": "KITCHEN",
            "name": "kitchen",
            "width": round(w * size_factor, 1),
            "depth": round(d * size_factor, 1),
            "constraints": kitchen_constraints,
        })

    # Bedrooms
    for i in range(bedrooms):
        is_master = (i == 0)
        name = "master" if is_master else f"bedroom{i}"
        room_type = "master" if is_master else "bedroom"
        w, d = DEFAULT_SIZES[room_type]

        constraints = {}
        if is_master and modifiers["ensuite"]:
            constraints["adjacent"] = ["ensuite"]

        rooms.append({
            "type": "BEDROOM",
            "name": name,
            "width": round(w * size_factor, 1),
            "depth": round(d * size_factor, 1),
            "constraints": constraints,
        })

    # Ensuite (if requested and has master)
    if modifiers["ensuite"] and bedrooms > 0:
        w, d = DEFAULT_SIZES["ensuite"]
        rooms.append({
            "type": "BATHROOM",
            "name": "ensuite",
            "width": round(w * size_factor, 1),
            "depth": round(d * size_factor, 1),
            "constraints": {"adjacent": ["master"]},
        })
        # Reduce main bathroom count
        bathrooms = max(1, bathrooms - 1) if bathrooms > 1 else bathrooms

    # Bathrooms
    for i in range(bathrooms):
        name = "bathroom" if i == 0 else f"bathroom{i+1}"
        w, d = DEFAULT_SIZES["bathroom"]
        rooms.append({
            "type": "BATHROOM",
            "name": name,
            "width": round(w * size_factor, 1),
            "depth": round(d * size_factor, 1),
            "constraints": {},
        })

    # Optional rooms
    if modifiers["study"]:
        w, d = DEFAULT_SIZES["study"]
        rooms.append({
            "type": "STUDY",
            "name": "study",
            "width": round(w * size_factor, 1),
            "depth": round(d * size_factor, 1),
            "constraints": {},
        })

    if modifiers["laundry"]:
        w, d = DEFAULT_SIZES["laundry"]
        rooms.append({
            "type": "LAUNDRY",
            "name": "laundry",
            "width": round(w * size_factor, 1),
            "depth": round(d * size_factor, 1),
            "constraints": {},
        })

    if modifiers["garage"]:
        w, d = DEFAULT_SIZES["garage"]
        rooms.append({
            "type": "GARAGE",
            "name": "garage",
            "width": w,  # Don't scale garage
            "depth": d,
            "constraints": {"exterior": "west"},
        })

    return rooms


def distribute_to_storeys(rooms, storeys, building_type):
    """Distribute rooms across storeys for multi-storey buildings."""
    if storeys == 1:
        return [{"level": 0, "name": "Ground", "rooms": rooms}]

    # Two-storey distribution
    ground_rooms = []
    upper_rooms = []

    for room in rooms:
        room_type = room["type"]

        # Ground floor: living, kitchen, dining, garage, laundry
        if room_type in ["LIVING", "KITCHEN", "DINING", "GARAGE", "LAUNDRY"]:
            ground_rooms.append(room)
        # Upper floor: bedrooms, bathrooms, study
        elif room_type in ["BEDROOM", "BATHROOM", "STUDY"]:
            upper_rooms.append(room)
        else:
            # Default to ground
            ground_rooms.append(room)

    # Add vertical constraints for bathrooms
    ground_baths = [r for r in ground_rooms if r["type"] == "BATHROOM"]
    upper_baths = [r for r in upper_rooms if r["type"] == "BATHROOM"]

    if ground_baths and upper_baths:
        # Stack bathrooms for plumbing efficiency
        for i, bath in enumerate(upper_baths):
            if i < len(ground_baths):
                bath["constraints"]["stack"] = "plumbing"
                ground_baths[i]["constraints"]["stack"] = "plumbing"

    # Add above: constraints for rooms aligned with ground floor
    for upper in upper_rooms:
        if upper["type"] == "BEDROOM":
            # First bedroom above living
            if upper["name"] == "master":
                upper["constraints"]["above"] = "living"

    return [
        {"level": 0, "name": "Ground", "rooms": ground_rooms},
        {"level": 1, "name": "Upper", "rooms": upper_rooms},
    ]


def resolve_intent(text):
    """Main function: parse natural language to JSON spec."""
    # Extract components
    bedrooms = extract_bedroom_count(text)
    bathrooms = extract_bathroom_count(text)
    building_type, config = extract_building_type(text)
    modifiers = extract_modifiers(text)

    # Generate rooms
    rooms = generate_room_list(bedrooms, bathrooms, building_type, modifiers)

    # Distribute to storeys
    storeys = distribute_to_storeys(rooms, config["storeys"], building_type)

    # Build spec
    spec = {
        "intent": text,
        "building_type": building_type,
        "bedrooms": bedrooms,
        "bathrooms": bathrooms,
        "modifiers": modifiers,
        "storeys": storeys,
        "roof": {"pitch": 15},
    }

    return spec


def main():
    if len(sys.argv) < 2:
        print("Usage: python intent_resolver.py \"intent text\" [output.json]")
        print("\nExamples:")
        print('  python intent_resolver.py "3 bedroom house"')
        print('  python intent_resolver.py "2 bedroom apartment with ensuite" spec.json')
        sys.exit(1)

    intent = sys.argv[1]
    output_path = sys.argv[2] if len(sys.argv) > 2 else None

    print(f"Intent: {intent}")
    print("-" * 60)

    spec = resolve_intent(intent)

    print(f"Building type: {spec['building_type']}")
    print(f"Bedrooms: {spec['bedrooms']}")
    print(f"Bathrooms: {spec['bathrooms']}")
    print(f"Size factor: {spec['modifiers']['size_factor']}")
    print(f"Open plan: {spec['modifiers']['open_plan']}")
    print(f"Ensuite: {spec['modifiers']['ensuite']}")
    print(f"Storeys: {len(spec['storeys'])}")

    total_rooms = sum(len(s["rooms"]) for s in spec["storeys"])
    print(f"Total rooms: {total_rooms}")

    print("\nRoom list:")
    for storey in spec["storeys"]:
        print(f"  {storey['name']} (level {storey['level']}):")
        for room in storey["rooms"]:
            constraints = room.get("constraints", {})
            constraint_str = ""
            if constraints:
                parts = []
                if "adjacent" in constraints:
                    parts.append(f"adjacent:{constraints['adjacent']}")
                if "exterior" in constraints:
                    parts.append(f"exterior:{constraints['exterior']}")
                if "above" in constraints:
                    parts.append(f"above:{constraints['above']}")
                if "stack" in constraints:
                    parts.append(f"stack:{constraints['stack']}")
                constraint_str = f" [{', '.join(parts)}]"
            print(f"    {room['type']} \"{room['name']}\" {room['width']}x{room['depth']}m{constraint_str}")

    if output_path:
        Path(output_path).parent.mkdir(parents=True, exist_ok=True)
        with open(output_path, "w") as f:
            json.dump(spec, f, indent=2)
        print(f"\nSpec written to: {output_path}")
    else:
        print("\nJSON spec:")
        print(json.dumps(spec, indent=2))


if __name__ == "__main__":
    main()
