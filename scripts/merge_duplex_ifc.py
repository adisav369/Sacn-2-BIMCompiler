#!/usr/bin/env python3
"""
Phase 119B: Merge Duplex Architecture + MEP IFC files into a single federated model.

Uses ifcopenshell.api project.append_asset to properly merge discipline-specific
IFC exports from the same Revit project. Both files share the same coordinate
origin and unit system (feet, converted to SI on load).

Input:
  - reference/residential/Ifc2x3_Duplex_Architecture.ifc  (ARC discipline)
  - reference/residential/Ifc2x3_Duplex_MEP.ifc           (MEP discipline)

Output:
  - reference/residential/Ifc2x3_Duplex_Federated.ifc
"""

import os
import sys
import ifcopenshell
import ifcopenshell.api

ARCH_PATH = "reference/residential/Ifc2x3_Duplex_Architecture.ifc"
MEP_PATH = "reference/residential/Ifc2x3_Duplex_MEP.ifc"
OUT_PATH = "reference/residential/Ifc2x3_Duplex_Federated.ifc"


def main():
    for path in (ARCH_PATH, MEP_PATH):
        if not os.path.exists(path):
            print(f"ERROR: {path} not found")
            sys.exit(1)

    print(f"Merging:")
    print(f"  Base:  {ARCH_PATH}")
    print(f"  Merge: {MEP_PATH}")
    print(f"  Out:   {OUT_PATH}")

    # Open both files
    arch = ifcopenshell.open(ARCH_PATH)
    mep = ifcopenshell.open(MEP_PATH)

    print(f"\n  Architecture entities: {len(list(arch))}")
    print(f"  MEP entities:          {len(list(mep))}")

    # Get the architecture building's storeys as merge targets
    arch_storeys = {s.Name: s for s in arch.by_type("IfcBuildingStorey")}
    mep_storeys = {s.Name: s for s in mep.by_type("IfcBuildingStorey")}
    print(f"\n  ARC storeys: {list(arch_storeys.keys())}")
    print(f"  MEP storeys: {list(mep_storeys.keys())}")

    # Collect MEP product elements to transfer
    mep_classes = [
        "IfcFlowSegment", "IfcFlowFitting", "IfcFlowTerminal",
        "IfcDistributionPort", "IfcFlowController",
    ]

    # Use append_asset to bring MEP elements into the architecture model
    # This preserves geometry representations and material associations
    mep_elements = []
    for cls in mep_classes:
        mep_elements.extend(mep.by_type(cls))

    print(f"\n  MEP elements to merge: {len(mep_elements)}")

    # Append each MEP element into the architecture file
    # ifcopenshell.api handles entity remapping
    count = 0
    for element in mep_elements:
        try:
            ifcopenshell.api.run("project.append_asset",
                                 arch, library=mep, element=element)
            count += 1
        except Exception as e:
            # Some elements may fail (ports, etc.) — skip
            pass

    print(f"  Merged: {count} elements")

    # Write federated output
    arch.write(OUT_PATH)
    print(f"\n  Written: {OUT_PATH}")

    # Verify
    merged = ifcopenshell.open(OUT_PATH)
    verify(merged)


def verify(merged):
    """Print element breakdown of federated model."""
    projects = merged.by_type("IfcProject")
    storeys = merged.by_type("IfcBuildingStorey")

    print(f"\nFederated model verification:")
    print(f"  IfcProject:        {len(projects)}")
    print(f"  IfcBuildingStorey: {len(storeys)}")

    # Element breakdown
    class_counts = {}
    for cls in ["IfcWall", "IfcWallStandardCase", "IfcDoor", "IfcWindow",
                "IfcSlab", "IfcRoof", "IfcFurnishingElement", "IfcRailing",
                "IfcStairFlight", "IfcColumn", "IfcBeam", "IfcMember",
                "IfcFlowSegment", "IfcFlowFitting", "IfcFlowTerminal",
                "IfcSpace"]:
        n = len(merged.by_type(cls))
        if n > 0:
            class_counts[cls] = n

    total = sum(class_counts.values())
    print(f"  Total product elements: {total}")
    for cls, n in sorted(class_counts.items()):
        print(f"    {cls:<25} {n:>4}")

    print(f"\nDone.")


if __name__ == "__main__":
    main()
