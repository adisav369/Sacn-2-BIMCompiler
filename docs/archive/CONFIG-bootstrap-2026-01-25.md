# PATHS

REPO="/home/red1/IfcOpenShell"
BRANCH="feature/IFC4_DB"
FEDERATION_MODULE="$REPO/src/bonsai/bonsai/bim/module/federation"
PIPELINE_2D3D="$REPO/src/bonsai/scripts/2dto3d"
PDF2BLEND="$FEDERATION_MODULE/pdf2blend"

# Working Data (git-ignored)
WORK_DIR="$REPO/WORK_DIR"
DATABASES="$WORK_DIR/databases"
FEDERATED_DB="$DATABASES/enhanced_federation_GI.db"

# Quick verification
sqlite3 "$FEDERATED_DB" "SELECT discipline, COUNT(*) FROM elements_meta GROUP BY 1 ORDER BY 2 DESC;"
# ARC|35338, FP|6884, REB|2660, ACMV|1621, CW|1431, STR|1429, ELEC|1172, SP|979, LPG|209
