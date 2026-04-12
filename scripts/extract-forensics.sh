#!/bin/bash

# BIM Intent Compiler - Forensic Evidence Extractor
# Run from project root: bash scripts/extract-forensics.sh

OUTPUT_FILE="IFC-to-BOM-forensics.txt"
echo "BIM INTENT COMPILER - FORENSIC EVIDENCE" > $OUTPUT_FILE
echo "Generated: $(date)" >> $OUTPUT_FILE
echo "========================================" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# SECTION 1: The Source Oracle
echo "=== SECTION 1: THE SOURCE ORACLE (IfcOpenShell Output) ===" >> $OUTPUT_FILE
echo "File: scripts/construction_manifest.yaml - Building declarations" >> $OUTPUT_FILE
echo "----------------------------------------------------------------" >> $OUTPUT_FILE
grep -A 15 "SampleHouse:" scripts/construction_manifest.yaml | head -20 >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
grep -A 10 "Duplex:" scripts/construction_manifest.yaml | head -15 >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# SECTION 2: Reading from Oracle
echo "=== SECTION 2: READING FROM ORACLE (component_library.db) ===" >> $OUTPUT_FILE
echo "File: scripts/RosettaStoneExtract.py - SQL that reads IFC data" >> $OUTPUT_FILE
echo "----------------------------------------------------------------" >> $OUTPUT_FILE
grep -A 15 "FROM I_Element_Extraction" scripts/RosettaStoneExtract.py | head -25 >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# SECTION 3: Building Origin (Nothing Up My Sleeve)
echo "=== SECTION 3: BUILDING ORIGIN - Aggregated from Actual Elements ===" >> $OUTPUT_FILE
echo "File: scripts/RosettaStoneExtract.py - Building origin computation" >> $OUTPUT_FILE
echo "----------------------------------------------------------------" >> $OUTPUT_FILE
grep -A 10 "all_min_x = min" scripts/RosettaStoneExtract.py | head -15 >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# SECTION 4: The Tack Offset (CRITICAL - The Heart of Non-Drift)
echo "=== SECTION 4: THE TACK OFFSET - Critical Arithmetic ===" >> $OUTPUT_FILE
echo "File: scripts/RosettaStoneExtract.py - Centroid minus floor origin" >> $OUTPUT_FILE
echo "----------------------------------------------------------------" >> $OUTPUT_FILE
sed -n '155,200p' scripts/RosettaStoneExtract.py >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# SECTION 5: Dimension Preservation
echo "=== SECTION 5: DIMENSION PRESERVATION - Size from Bounding Box ===" >> $OUTPUT_FILE
echo "File: scripts/RosettaStoneExtract.py - Converting to mm" >> $OUTPUT_FILE
echo "----------------------------------------------------------------" >> $OUTPUT_FILE
grep -A 5 "alloc_w = (max_x - min_x)" scripts/RosettaStoneExtract.py | head -10 >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# SECTION 6: The INSERT - Committing to *_BOM.db
echo "=== SECTION 6: THE INSERT - Writing to {PREFIX}_BOM.db ===" >> $OUTPUT_FILE
echo "File: scripts/RosettaStoneExtract.py - INSERT INTO m_bom_line" >> $OUTPUT_FILE
echo "----------------------------------------------------------------" >> $OUTPUT_FILE
sed -n '180,200p' scripts/RosettaStoneExtract.py >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# SECTION 7: EntityType Enforcement (Read-Only Protection)
echo "=== SECTION 7: ENTITYTYPE ENFORCEMENT - Dictionary Records Read-Only ===" >> $OUTPUT_FILE
echo "File: ORMSandbox/src/main/java/com/bim/ormsandbox/po/MBOM.java - beforeSave guard" >> $OUTPUT_FILE
echo "----------------------------------------------------------------" >> $OUTPUT_FILE
grep -A 8 "EntityType guard" ORMSandbox/src/main/java/com/bim/ormsandbox/po/MBOM.java >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# SECTION 8: The Fresh Output Guarantee
echo "=== SECTION 8: FRESH OUTPUT GUARANTEE - output.db deleted each compile ===" >> $OUTPUT_FILE
echo "File: DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java" >> $OUTPUT_FILE
echo "----------------------------------------------------------------" >> $OUTPUT_FILE
grep -B 2 -A 5 "outputFile.delete()" DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# SECTION 9: The 9-Stage Pipeline
echo "=== SECTION 9: THE 9-STAGE PIPELINE ===" >> $OUTPUT_FILE
echo "File: DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java" >> $OUTPUT_FILE
echo "----------------------------------------------------------------" >> $OUTPUT_FILE
sed -n '51,61p' DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# SECTION 10: NO FALLBACK Gate
echo "=== SECTION 10: NO FALLBACK GATE - Every BUY Must Have Geometry ===" >> $OUTPUT_FILE
echo "File: DAGCompiler/src/main/java/com/bim/compiler/dsl/MetadataValidator.java" >> $OUTPUT_FILE
echo "----------------------------------------------------------------" >> $OUTPUT_FILE
grep -A 20 "NO FALLBACK" DAGCompiler/src/main/java/com/bim/compiler/dsl/MetadataValidator.java | head -25 >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# SECTION 11: Verification Gates (G1-G5)
echo "=== SECTION 11: VERIFICATION GATES - RosettaStoneGateTest ===" >> $OUTPUT_FILE
echo "File: DAGCompiler/src/test/java/com/bim/compiler/contract/RosettaStoneGateTest.java" >> $OUTPUT_FILE
echo "----------------------------------------------------------------" >> $OUTPUT_FILE
sed -n '25,35p' DAGCompiler/src/test/java/com/bim/compiler/contract/RosettaStoneGateTest.java >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
echo "--- G1-COUNT (ref count == compiled count) ---" >> $OUTPUT_FILE
grep -A 10 "private void runG1" DAGCompiler/src/test/java/com/bim/compiler/contract/RosettaStoneGateTest.java | head -15 >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# SECTION 12: Cross-Database Integrity (D-1 Check)
echo "=== SECTION 12: CROSS-DATABASE INTEGRITY - D-1 Orphan Check ===" >> $OUTPUT_FILE
echo "File: DAGCompiler/src/test/java/com/bim/compiler/contract/DataIntegrityTest.java" >> $OUTPUT_FILE
echo "----------------------------------------------------------------" >> $OUTPUT_FILE
sed -n '76,92p' DAGCompiler/src/test/java/com/bim/compiler/contract/DataIntegrityTest.java >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# SECTION 13: BOM Tree Loader - 3-Table Authority
echo "=== SECTION 13: BOM TREE LOADER - 3-Table Authority Rule ===" >> $OUTPUT_FILE
echo "File: DAGCompiler/src/main/java/com/bim/compiler/library/BOMTreeLoader.java" >> $OUTPUT_FILE
echo "----------------------------------------------------------------" >> $OUTPUT_FILE
sed -n '170,205p' DAGCompiler/src/main/java/com/bim/compiler/library/BOMTreeLoader.java >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# SECTION 14: Live Data from prepared BOM.db (temporary — sourced from {PREFIX}_BOM.db)
echo "=== SECTION 14: LIVE DATA FROM BOM.db (prepared working copy) ===" >> $OUTPUT_FILE
echo "----------------------------------------------------------------" >> $OUTPUT_FILE
echo "--- BUILDING BOMs (AABB from extraction, not hardcoded) ---" >> $OUTPUT_FILE
sqlite3 library/_SH_compile.db "SELECT bom_id, bom_type, doc_sub_type, aabb_width_mm, aabb_depth_mm, aabb_height_mm FROM m_bom WHERE bom_type='BUILDING' ORDER BY bom_id;" >> $OUTPUT_FILE 2>&1
echo "" >> $OUTPUT_FILE
echo "--- Extraction element counts per floor BOM ---" >> $OUTPUT_FILE
sqlite3 library/_SH_compile.db "SELECT bom_id, COUNT(*) as elements FROM m_bom_line WHERE entity_type='D' AND bom_id LIKE '%_STR' GROUP BY bom_id ORDER BY COUNT(*) DESC LIMIT 10;" >> $OUTPUT_FILE 2>&1
echo "" >> $OUTPUT_FILE
echo "--- Sample tack offsets (SH Ground Floor, first 5 elements) ---" >> $OUTPUT_FILE
sqlite3 -header library/_SH_compile.db "SELECT child_product_id, role, dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm FROM m_bom_line WHERE bom_id='SH_GF_STR' LIMIT 5;" >> $OUTPUT_FILE 2>&1
echo "" >> $OUTPUT_FILE
echo "--- EntityType distribution (D=Dictionary, U=User) ---" >> $OUTPUT_FILE
sqlite3 library/_SH_compile.db "SELECT entity_type, COUNT(*) FROM m_bom_line GROUP BY entity_type;" >> $OUTPUT_FILE 2>&1
echo "" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# SECTION 15: G1-COUNT Live Verification
echo "=== SECTION 15: G1-COUNT LIVE VERIFICATION ===" >> $OUTPUT_FILE
echo "----------------------------------------------------------------" >> $OUTPUT_FILE
REF_SH=$(sqlite3 DAGCompiler/lib/input/SampleHouse_extracted.db "SELECT COUNT(*) FROM elements_meta;" 2>&1)
OUT_SH=$(sqlite3 DAGCompiler/lib/output/samplehouse.db "SELECT COUNT(*) FROM elements_meta;" 2>&1)
echo "SH reference count:  $REF_SH" >> $OUTPUT_FILE
echo "SH compiled count:   $OUT_SH" >> $OUTPUT_FILE
echo "SH delta:            $((OUT_SH - REF_SH))" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
REF_DX=$(sqlite3 DAGCompiler/lib/input/Duplex_extracted.db "SELECT COUNT(*) FROM elements_meta;" 2>&1)
OUT_DX=$(sqlite3 DAGCompiler/lib/output/duplex.db "SELECT COUNT(*) FROM elements_meta;" 2>&1)
echo "DX reference count:  $REF_DX" >> $OUTPUT_FILE
echo "DX compiled count:   $OUT_DX" >> $OUTPUT_FILE
echo "DX delta:            $((OUT_DX - REF_DX))" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# SECTION 16: Seal Verification
echo "=== SECTION 16: SEAL INTEGRITY ===" >> $OUTPUT_FILE
echo "----------------------------------------------------------------" >> $OUTPUT_FILE
if [ -f "scripts/verify_test_seal.sh" ]; then
    bash scripts/verify_test_seal.sh 2>&1 | tail -5 >> $OUTPUT_FILE
else
    echo "Seal script not found" >> $OUTPUT_FILE
fi
echo "" >> $OUTPUT_FILE

echo "========================================" >> $OUTPUT_FILE
echo "END OF FORENSIC EVIDENCE" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

echo "Extraction complete. File: $OUTPUT_FILE"
echo "Total lines: $(wc -l < $OUTPUT_FILE)"
