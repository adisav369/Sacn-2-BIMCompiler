package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for the LOD product geometry library.
 *
 * <p>Reads from {@code lod_product_geometry} view in component_library.db,
 * which joins {@code M_Product_Image} (product → mesh mapping + orientation) with
 * {@code LOD_Object} (canonical mesh blobs).
 *
 * <p>This is a <b>read-only</b> PO — the library is populated by migration
 * scripts, not by the pipeline. Use for geometry lookup during compilation.
 *
 * <p>The {M_Product_Image; LOD_Object} pair model:
 * <ul>
 *   <li><b>M_Product_Image:</b> M_Product_ID → geometry_hash + orientation
 *       (up_axis, forward_axis, attachment_face)</li>
 *   <li><b>LOD_Object:</b> geometry_hash → mesh blob
 *       (vertices, faces, normals, vertex_count, face_count)</li>
 * </ul>
 *
 * <p>Table (view): {@code lod_product_geometry}
 * <pre>
 *   M_Product_ID    TEXT PRIMARY KEY   (FK → BOM.db M_Product.product_id)
 *   up_axis         TEXT NOT NULL      ('Z' — canonical up direction)
 *   forward_axis    TEXT NOT NULL      ('Y' — canonical forward direction)
 *   attachment_face TEXT NOT NULL      ('CENTER', 'BOTTOM', 'TOP', 'SIDE')
 *   geometry_hash   TEXT NOT NULL      (content-addressable mesh key)
 *   vertex_count    INTEGER NOT NULL
 *   face_count      INTEGER NOT NULL
 *   vertices        BLOB NOT NULL      (float32[3 * vertex_count])
 *   faces           BLOB NOT NULL      (int32[3 * face_count])
 *   normals         BLOB               (float32[3 * vertex_count], may be NULL)
 * </pre>
 */
public class X_LOD_Library extends BasePO {

    public static final String Table_Name                       = "lod_product_geometry";
    public static final String COLUMNNAME_M_Product_ID          = "M_Product_ID";
    public static final String COLUMNNAME_up_axis               = "up_axis";
    public static final String COLUMNNAME_forward_axis          = "forward_axis";
    public static final String COLUMNNAME_attachment_face        = "attachment_face";
    public static final String COLUMNNAME_geometry_hash         = "geometry_hash";
    public static final String COLUMNNAME_vertex_count          = "vertex_count";
    public static final String COLUMNNAME_face_count            = "face_count";
    public static final String COLUMNNAME_vertices              = "vertices";
    public static final String COLUMNNAME_faces                 = "faces";
    public static final String COLUMNNAME_normals               = "normals";

    public X_LOD_Library(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_M_Product_ID; }

    // --- Identity ---
    public String getProductId()       { return get_ValueAsString(COLUMNNAME_M_Product_ID); }

    // --- Orientation (intrinsic product properties) ---
    public String getUpAxis()          { return get_ValueAsString(COLUMNNAME_up_axis); }
    public String getForwardAxis()     { return get_ValueAsString(COLUMNNAME_forward_axis); }
    public String getAttachmentFace()  { return get_ValueAsString(COLUMNNAME_attachment_face); }

    // --- Geometry reference ---
    public String getGeometryHash()    { return get_ValueAsString(COLUMNNAME_geometry_hash); }
    public int    getVertexCount()     { return get_ValueAsInt(COLUMNNAME_vertex_count); }
    public int    getFaceCount()       { return get_ValueAsInt(COLUMNNAME_face_count); }

    // --- Mesh blobs (accessed via raw column value, cast by caller) ---
    // BasePO stores column values as Object; blobs come through as byte[]
    // from JDBC ResultSet.getObject(). Caller casts.
    public Object getVerticesRaw()     { return get_Value(COLUMNNAME_vertices); }
    public Object getFacesRaw()        { return get_Value(COLUMNNAME_faces); }
    public Object getNormalsRaw()      { return get_Value(COLUMNNAME_normals); }
}
