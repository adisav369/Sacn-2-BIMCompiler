"""
Bonsai BIM Designer — Blender addon for the BIM Intent Compiler.

Item A of the IfcOpenShell Federation Suite.

This addon is a thin parameter layer over the Java compiler engine:
- 5 chooser panels (bpy.types.Panel) for building configuration
- TCP client to the Java DesignerServer (ndjson protocol)
- db_loader reads output.db → Blender mesh objects
- Thread-safe callbacks via bpy.app.timers for async compile notifications

The addon does NOT create geometry. The compiler does. The addon edits
BOM parameters and triggers batch compilation — the same way iDempiere
processes a document: Draft → Process → Complete.
"""

bl_info = {
    "name": "BIM Designer",
    "author": "BIM Compiler Project",
    "version": (0, 1, 0),
    "blender": (3, 6, 0),
    "location": "View3D > Sidebar > BIM Designer",
    "description": "Compiler-driven BIM design via the BIM Intent Compiler",
    "category": "3D View",
}


def register():
    """Register addon classes with Blender."""
    # Deferred imports to avoid issues when running outside Blender
    from . import operator, panel, props
    props.register()
    operator.register()
    panel.register()


def unregister():
    """Unregister addon classes from Blender."""
    from . import operator, panel, props
    panel.unregister()
    operator.unregister()
    props.unregister()
