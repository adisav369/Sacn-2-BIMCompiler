"""
BIM Designer Properties
-----------------------
Blender property groups for BIM Designer addon state.
"""

from __future__ import annotations

import bpy
from bpy.types import PropertyGroup
from bpy.props import (
    StringProperty,
    BoolProperty,
    IntProperty,
    FloatProperty,
    EnumProperty,
    PointerProperty,
)
from typing import TYPE_CHECKING


class BIMDesignerProperties(PropertyGroup):
    """Properties for the BIM Designer addon (Item A of Federation Suite)."""

    # -- Connection ----------------------------------------------------------
    server_host: StringProperty(
        name="Host",
        description="DesignerServer hostname",
        default="127.0.0.1",
    )

    server_port: IntProperty(
        name="Port",
        description="DesignerServer TCP port",
        default=9876,
        min=1024,
        max=65535,
    )

    is_connected: BoolProperty(
        name="Connected",
        description="Whether the client is connected to the DesignerServer",
        default=False,
    )

    # -- Building selection --------------------------------------------------
    building_id: StringProperty(
        name="Building ID",
        description="Active building identifier (e.g. Ifc4_SampleHouse)",
    )

    bom_db_path: StringProperty(
        name="BOM DB Path",
        description="Path to the per-building compile database",
        subtype='FILE_PATH',
    )

    output_db_path: StringProperty(
        name="Output DB",
        description="Path to the compiled output database",
        subtype='FILE_PATH',
    )

    # -- Create New (generative building) ------------------------------------
    building_name: StringProperty(
        name="Building Name",
        description="Name for the new building",
    )

    building_type: EnumProperty(
        name="Building Type",
        description="Type of building to generate",
        items=[
            ('DETACHED', 'Detached House', 'Single detached dwelling'),
            ('SEMI_D', 'Semi-Detached', 'Semi-detached dwelling'),
            ('TERRACE', 'Terrace House', 'Terrace / row house'),
            ('APARTMENT', 'Apartment', 'Multi-storey apartment unit'),
        ],
        default='DETACHED',
    )

    jurisdiction: EnumProperty(
        name="Jurisdiction",
        description="Building code jurisdiction",
        items=[
            ('MY', 'Malaysia (UBBL)', ''),
            ('US', 'USA (IRC)', ''),
            ('UK', 'UK (NDSS)', ''),
            ('AU', 'Australia (NCC)', ''),
            ('SG', 'Singapore (BCA)', ''),
        ],
        default='MY',
    )

    site_width_mm: FloatProperty(
        name="Site Width",
        description="Site width in millimetres",
        default=9000,
        min=3000,
        max=50000,
    )

    site_depth_mm: FloatProperty(
        name="Site Depth",
        description="Site depth in millimetres",
        default=7000,
        min=3000,
        max=50000,
    )

    num_bedrooms: IntProperty(
        name="Bedrooms",
        description="Number of bedrooms",
        default=2,
        min=1,
        max=6,
    )

    num_bathrooms: IntProperty(
        name="Bathrooms",
        description="Number of bathrooms",
        default=1,
        min=1,
        max=4,
    )

    # -- Verb console --------------------------------------------------------
    verb_line: StringProperty(
        name="Verb",
        description="BIM COBOL verb line to execute (e.g. PLACE WALL ...)",
    )

    verb_result: StringProperty(
        name="Last Result",
        description="Result of the last verb execution",
    )

    # -- Status --------------------------------------------------------------
    compile_status: StringProperty(
        name="Status",
        description="Status message from the last operation",
    )

    last_element_count: IntProperty(
        name="Element Count",
        description="Number of elements in last compilation",
        default=0,
        min=0,
    )

    if TYPE_CHECKING:
        server_host: str
        server_port: int
        is_connected: bool
        building_id: str
        bom_db_path: str
        output_db_path: str
        building_name: str
        building_type: str
        jurisdiction: str
        site_width_mm: float
        site_depth_mm: float
        num_bedrooms: int
        num_bathrooms: int
        verb_line: str
        verb_result: str
        compile_status: str
        last_element_count: int


def register():
    bpy.utils.register_class(BIMDesignerProperties)
    bpy.types.Scene.BIMDesignerProperties = PointerProperty(
        type=BIMDesignerProperties
    )


def unregister():
    del bpy.types.Scene.BIMDesignerProperties
    bpy.utils.unregister_class(BIMDesignerProperties)
