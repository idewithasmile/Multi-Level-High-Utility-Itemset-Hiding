# Multi-level High Utility Itemset Hiding (MLH / FMLH)

A Java 17 + Maven project for **Privacy-Preserving Utility Mining (PPUM)** on hierarchical transaction databases.

This repository provides:
- `MLHProtector` and `FMLHProtector` implementations
- Closed-loop scientific evaluation with **SPMF (MLHUIMiner)**
- Swing-based UI for before/after comparison
- Benchmark pipeline and CSV/chart outputs

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Build](#build)
- [Run](#run)
  - [MainFrame UI (recommended)](#mainframe-ui-recommended)
  - [CLI App](#cli-app)
  - [End-to-End Evaluator](#end-to-end-evaluator)
- [Benchmark](#benchmark)
- [Input Formats](#input-formats)
- [Metrics](#metrics)
- [Troubleshooting](#troubleshooting)
- [Current Benchmark Profile](#current-benchmark-profile)

---

## Overview

The system targets PPUM in a strict closed-loop process:

1. Mine HUIs from the original database (SPMF)
2. Sanitize database with `FMLHProtector`
3. Mine HUIs from sanitized database (SPMF)
4. Compute evaluation metrics (`HF`, `MC`, `AC`)

---

## Architecture

Core modules:

- `src/main/java/com/example/huihiding/model`
  - `HierarchicalDatabase`, `Transaction`, `Itemset`, `Taxonomy`
- `src/main/java/com/example/huihiding/service`
  - `MLHProtector`, `FMLHProtector`
  - `SPMFDataExporter`
  - `EndToEndEvaluatorService`
  - `SPMFDatabaseLoader`, `DatabaseLoaderService`
- `src/main/java/com/example/huihiding/ui`
  - `MainFrame`, `DesktopApp`
- `src/test/java/com/example/huihiding`
  - `BenchmarkRunner`

Generated evaluation artifacts are written under:

- `target/e2e-ui/`

---

## Requirements

- JDK 17
- Maven 3.8+
- `spmf.jar` available at project root:

```text
<project-root>/spmf.jar
```

---

## Build

Compile (main + test classes):

```bash
mvn -q test-compile
```

Package JAR:

```bash
mvn clean package -DskipTests
```

---

## Run

### MainFrame UI (recommended)

```bash
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.ui.MainFrame
```

### CLI App

Default demo:

```bash
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.App
```

Menu mode:

```bash
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.App --menu
```

Input/output mode:

```bash
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.App --file input.txt output.txt
```

### End-to-End Evaluator

```bash
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar \
  com.example.huihiding.service.EndToEndEvaluatorService \
  ./spmf.jar ./input.txt ./target/e2e-eval
```

---

## Benchmark

Run benchmark:

```bash
mvn -q test-compile && java -Xmx20G -cp target/classes:target/test-classes com.example.huihiding.BenchmarkRunner
```

Output:

- `benchmark_results.csv`

Generate charts:

```bash
python3 plot_results.py
```

Outputs:

- `hui_comparison.png`
- `execution_time.png`

---

## Input Formats

### Internal project format (`input.txt`)

Typical sections:

- `[PARAMETERS]`
- `[EXTERNAL_UTILITIES]`
- `[TAXONOMY]`
- `[TRANSACTIONS]` or `[TRANSACTIONS_SPMF]`
- `[SENSITIVE_ITEMSETS]` or `[SENSITIVE_ITEMSETS_SPMF]`

### SPMF utility format

Transaction line:

```text
item1 item2 ... : TU : u1 u2 ...
```

Taxonomy line:

```text
child,parent
```

---

## Metrics

- **HF (Hiding Failure):** sensitive HUIs that remain after sanitization
- **MC (Missing Cost):** non-sensitive HUIs lost after sanitization
- **AC (Artificial Cost):** new HUIs introduced by sanitization

Lower is better for all three metrics.

---

## Troubleshooting

### `spmf.jar` missing

Place `spmf.jar` at project root.

### Interrupted or hanging runs

Stop stale Java processes:

```bash
killall -9 java || true
```

### Need clean evaluator outputs

```bash
rm -f target/e2e-ui/*_output.txt
```

### Build blocked by tests

Use:

```bash
mvn clean package -DskipTests
```

---

## Current Benchmark Profile

`BenchmarkRunner` is currently configured for a stable audit profile:

- Dataset: `data/retail_utility_spmf.txt`
- Threshold: `450000`
- Sensitive itemset (fixed): `9806 10805`

Other dense datasets are intentionally commented in benchmark config due to runtime/hardware trade-offs.

---

## License

This project includes and interoperates with SPMF components/datasets in the repository. Please follow the corresponding license files shipped in the project tree.
