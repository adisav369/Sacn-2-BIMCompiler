# BIM Intent Compiler

**Construction is manufacturing. A building IS its Bill of Materials.**

A metadata-driven, deterministic compiler that reads BOM data and produces 3D building coordinates — the same thing an ERP system does when it explodes a manufacturing BOM into work orders. Every output element traces to a library input. Nothing is invented. No AI inside. Pure arithmetic.

| | |
|---|---|
| **35 buildings** compiled (48,428 elements largest) | **64 verbs**, 2,475 products |
| **6 mathematical gates** prove every output | **392 tests**, all GREEN |
| **ERP-native** data model ([iDempiere](https://idempiere.org/)) | **Blender/Bonsai** live GUI |

## Quick Start

```bash
# Prerequisites: Java 17+, Maven 3.8+, SQLite3
git clone https://github.com/red1oon/BIMCompiler.git && cd bim-compiler

mvn compile -q                                        # Compile all modules
./scripts/run_RosettaStones.sh classify_sh.yaml       # Compile Sample House + verify gates
./scripts/run_tests.sh                                # Full test gate (392 tests)
```

## Documentation

Browse the full documentation site — 50 specs with search, dark mode, and sidebar navigation:

```bash
./scripts/serve_docs.sh                 # http://localhost:8000
```

Also available online: **https://red1oon.github.io/BIMCompiler/**

## How It Works

```
IFC file → extract → classify.yaml → IFCtoBOM → BOM.db → compile → output.db → gates
           (once)    (human intent)    (once)     (recipe)   (repeat)  (elements)   (proof)
```

## Project Structure

```
bim-compiler/
├── DAGCompiler/           # 9-stage compilation pipeline (G1-G6 gates)
├── BIM_COBOL/             # 64 domain verbs, witness engine
├── BIMEyes/               # Geometric comprehension: 28 shape proofs
├── IFCtoBOM/              # IFC extraction → BOM database pipeline
├── BonsaiBIMDesigner/     # GUI server + validation (TCP :9876)
├── BIMBackOffice/         # ERP reporting + portfolio (HTTP :9877)
├── orm-core/              # Base ORM, BIMLogger, shared utilities
├── library/               # SQLite databases (product catalog, BOMs)
├── migration/             # SQL migration scripts (append-only)
├── scripts/               # Build, test, docs, and audit scripts
└── docs/                  # 50 specifications (mkdocs site source)
```

## About

**Redhuan D. Oon** ([red1](mailto:red1org@gmail.com)) — Kuala Lumpur, Malaysia.
Led [ADempiere](https://www.adempierebr.com/User:Red1) (2006), paved the way for
[iDempiere](https://idempiere.org/) (2010), authored
*[Open Source ERP](https://www.amazon.com/Open-Source-ERP-Redhuan-Oon/dp/9673490228)*
(Pearson Malaysia, 2010). Two decades of ERP manufacturing BOM expertise applied to construction.

**License:** Code: GPL v2. Documentation: CC BY-SA 4.0.

Copyright (c) 2026 Redhuan D. Oon. All rights reserved.
