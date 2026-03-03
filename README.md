# Multi-level High Utility Itemset Hiding (MLHProtector / FMLHProtector)

Java Maven implementation that follows the paper in *Multi-level high utility itemset hiding* (attached PDF). The project loads a hierarchical transactional database, applies MLHProtector and FMLHProtector to hide sensitive high-utility itemsets, and reports standard PPUM metrics.

## Architecture
- **Model**: taxonomy, transactions, itemsets, in-memory hierarchical DB.
- **DAO (JDBC + H2)**: taxonomy, external utilities, transactions, sensitive itemsets.
- **Services**: schema seeding, database loading, algorithms (MLH/FMLH), metric calculator.
- **Controller**: orchestrates runs and metric evaluation.
- **CLI**: `App` runs both algorithms on sample data.

## Getting started
1. Build and test:
   ```bash
   mvn clean test
   ```
2. Run the sample CLI:
   ```bash
   mvn clean package
   java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.App
   ```
   Output prints HF/MC/AC/DSS/DUS/IUS for both algorithms using the sample dataset in `src/main/resources/schema.sql` (threshold ξ = 88).

3. Run interactive menu:
   ```bash
   java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.App --menu
   ```
   - Option 1: run embedded demo from `schema.sql`
   - Option 2: run by file input `input.txt` and export report to `output.txt`

4. Run directly with file mode:
   ```bash
   java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.App --file input.txt output.txt
   ```
   Report file includes:
   - Metrics for MLH/FMLH
   - Sanitized transactions
   - PASS/FAIL comparison against expected outputs (if sections `EXPECTED_MLH`, `EXPECTED_FMLH` are present in input file)

5. Run Desktop GUI (Java Swing):
   ```bash
   java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.ui.DesktopApp
   ```
   GUI gồm:
   - Menu `File -> Import Dataset` để nạp `input.txt`
   - Bảng hiển thị Database gốc, Taxonomy, External Utility, Sensitive Itemsets
   - Nút chạy `MLHProtector` / `FMLHProtector`
   - Khu vực console hiển thị metrics và thời gian chạy

## Customizing
- Edit `src/main/resources/schema.sql` to change taxonomy, utilities, transactions, and sensitive itemsets.
- Edit `input.txt` to run paper-style fixed dataset and expected-output validation via file mode.
- Adjust `MIN_THRESHOLD` in `com.example.huihiding.App` to change the minimum utility threshold.
- `HierarchicalDatabase.discoverHighUtilityItemsets` is a naive enumerator (suitable for small demos); replace with a proper HUIM miner for larger datasets.

## Notes
- The sanitization algorithms reduce item internal utilities instead of deleting transactions, keeping sanitized data usable.
- Metrics follow the paper: HF, MC, AC, DSS, DUS, IUS.

## Tổng hợp cách chạy chương trình

1) Build và test toàn bộ:
```bash
mvn clean test
```

2) Chạy bản CLI mặc định (dữ liệu từ schema.sql):
```bash
mvn clean package
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.App
```

3) Chạy CLI có menu tương tác:
```bash
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.App --menu
```

4) Chạy bằng file input và xuất kết quả ra output:
```bash
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.App --file input.txt output.txt
cat output.txt
```

5) Chạy giao diện Desktop Swing:
```bash
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.ui.DesktopApp
```

6) Nếu chưa có file jar hoặc jar chưa mới, package lại rồi chạy:
```bash
mvn clean package
```
