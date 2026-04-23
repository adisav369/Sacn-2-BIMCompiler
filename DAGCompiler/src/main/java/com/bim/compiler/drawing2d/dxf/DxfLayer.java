package com.bim.compiler.drawing2d.dxf;

/**
 * DXF layer definition: name, color index, linetype.
 *
 * Color index follows AutoCAD ACI (1-255):
 *   1=red, 2=yellow, 3=green, 4=cyan, 5=blue, 6=magenta, 7=white/black,
 *   8=dark grey, 9=light grey, 250-255=greys.
 */
record DxfLayer(String name, int color, String linetype) {
}
