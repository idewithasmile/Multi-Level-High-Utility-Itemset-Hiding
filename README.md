# Multi-level High Utility Itemset Hiding (MLHProtector / FMLHProtector)

Java Maven project for PPUM on hierarchical transaction databases.

Project now supports:
- Running `MLHProtector` and `FMLHProtector`
- Closed-loop scientific evaluation with SPMF (`MLHUIMiner`)
- Automatic export to SPMF format with stable String→Integer mapping
- PPUM metrics report: HF, MC, AC (plus existing internal metrics)

---

## 0) Chạy nhanh (khuyên dùng)

Nếu bạn chỉ cần chạy chương trình ngay:

### Chạy GUI

```bash
mvn -q test-compile
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.ui.MainFrame
```

### Chạy benchmark 4 bộ dữ liệu + xuất CSV

```bash
mvn -q test-compile && java -Xmx20G -cp target/classes:target/test-classes com.example.huihiding.BenchmarkRunner
```

Sau khi chạy xong sẽ có file:

- `benchmark_results.csv`

### Vẽ biểu đồ từ CSV

```bash
python3 plot_results.py
```

Sinh ra:

- `hui_comparison.png`
- `execution_time.png`

---

## 1) Kiến trúc chính

- **Core models**: `HierarchicalDatabase`, `Transaction`, `Itemset`, `Taxonomy`
- **Sanitization**:
  - `MLHProtector`
  - `FMLHProtector`
- **SPMF integration**:
  - `SPMFDataExporter` (xuất DB/Taxonomy sang chuẩn SPMF, giữ ID mapping ổn định)
  - `EndToEndEvaluatorService` (pipeline đóng vòng trước/sau sanitize)
  - `SPMFValidatorService` (tiện ích xác thực/parse riêng)
- **Entry points**:
  - CLI: `App`
  - GUI: `DesktopApp`

---

## 2) Build nhanh

```bash
mvn clean test
mvn clean package
```

Nếu đang có test fail (ví dụ testcase ground-truth đang bật), lệnh `mvn clean package` sẽ dừng trước khi tạo JAR. Khi đó dùng:

```bash
mvn clean package -DskipTests
```

---

## 3) Chạy chương trình chính

### 3.1 Chạy demo mặc định (schema.sql)

```bash
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.App
```

Gợi ý ổn định hơn (tự lo classpath dependency):

```bash
mvn -DskipTests org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=com.example.huihiding.App
```

### 3.2 Chạy chế độ menu

```bash
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.App --menu
```

Hoặc:

```bash
mvn -DskipTests org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=com.example.huihiding.App -Dexec.args="--menu"
```

### 3.3 Chạy bằng file input/output

```bash
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.App --file input.txt output.txt
cat output.txt
```

Hoặc:

```bash
mvn -DskipTests org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=com.example.huihiding.App -Dexec.args="--file input.txt output.txt"
cat output.txt
```

### 3.4 Chạy GUI Swing

```bash
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.ui.DesktopApp
```

Hoặc:

```bash
mvn -DskipTests org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=com.example.huihiding.ui.DesktopApp
```

### 3.5 Chạy UI so sánh Before/After mới (MainFrame)

```bash
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.ui.MainFrame
```

Hoặc:

```bash
mvn -DskipTests org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=com.example.huihiding.ui.MainFrame
```

---

## 4) Closed-loop Evaluation với SPMF (mới)

## Mục tiêu

Thực hiện đúng luồng đánh giá khoa học:
1. Mine HUI trên DB gốc (SPMF)
2. Chạy `FMLHProtector` trong bộ nhớ
3. Mine HUI trên DB đã sanitize (SPMF)
4. So sánh tập itemset để tính HF/MC/AC

## Thành phần sử dụng

- `EndToEndEvaluatorService`: điều phối toàn bộ pipeline
- `SPMFDataExporter`: xuất dữ liệu SPMF với ánh xạ ID nhất quán giữa bản gốc và bản sanitize

## Chuẩn bị

1. Có file `spmf.jar` (bạn tự tải từ SPMF project).
2. Có file input nội bộ (ví dụ `input.txt`) theo format của project (`[PARAMETERS]`, `[EXTERNAL_UTILITIES]`, `[TAXONOMY]`, `[TRANSACTIONS]`, `[SENSITIVE_ITEMSETS]`).

## Chạy pipeline

```bash
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.service.EndToEndEvaluatorService <path/to/spmf.jar> <path/to/input.txt> [workDir]
```

Nếu build đang fail test, chạy trực tiếp bằng Maven exec:

```bash
mvn -DskipTests org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=com.example.huihiding.service.EndToEndEvaluatorService -Dexec.args="<path/to/spmf.jar> <path/to/input.txt> [workDir]"
```

Ví dụ:

```bash
java -cp target/hui-hiding-0.1.0-SNAPSHOT.jar com.example.huihiding.service.EndToEndEvaluatorService ./spmf.jar ./input.txt ./target/e2e-eval
```

## Các file sinh ra trong `workDir`

- `spmf_original_db.txt`
- `spmf_taxonomy.txt`
- `spmf_original_output.txt`
- `spmf_sanitized_db.txt`
- `spmf_sanitized_output.txt`

## Output report

Console sẽ in:
- Số lượng `Original HUIs`, `Sanitized HUIs`, `Sensitive HUIs`
- `HF (Hiding Failure)`
- `MC (Missing Cost)`
- `AC (Artificial Cost)`
- Tỷ lệ phần trăm cho từng chỉ số

---

## 5) Lưu ý kỹ thuật quan trọng

1. **Không hard-code expected transactions** khi đánh giá học thuật.
   Dùng closed-loop mining trước/sau sanitize là đúng phương pháp.

2. **ID mapping phải ổn định** giữa DB gốc và DB sanitize.
   Vì vậy pipeline dùng **cùng một instance** `SPMFDataExporter`.

3. **SPMF format yêu cầu số nguyên dương**.
   Exporter đã xử lý theo định dạng:
   `item1 item2:TU:u1 u2`

4. `EndToEndEvaluatorService` hiện parse input nội bộ bằng parser thật của hệ thống (App parser), để đảm bảo cùng lifecycle dữ liệu.

---

## 6) Các file chính đã cập nhật gần đây

- [src/main/java/com/example/huihiding/service/EndToEndEvaluatorService.java](src/main/java/com/example/huihiding/service/EndToEndEvaluatorService.java)
- [src/main/java/com/example/huihiding/service/SPMFDataExporter.java](src/main/java/com/example/huihiding/service/SPMFDataExporter.java)
- [src/main/java/com/example/huihiding/service/SPMFValidatorService.java](src/main/java/com/example/huihiding/service/SPMFValidatorService.java)
- [src/test/java/com/example/huihiding/ProtectorAlgorithmsTest.java](src/test/java/com/example/huihiding/ProtectorAlgorithmsTest.java)
- [src/test/java/com/example/huihiding/BenchmarkRunner.java](src/test/java/com/example/huihiding/BenchmarkRunner.java)
- [plot_results.py](plot_results.py)

---

## 7) BenchmarkRunner (4 dataset chuẩn)

`BenchmarkRunner` đã cấu hình sẵn:

- Retail: `data/retail_utility_spmf.txt` — threshold `450000`
- Mushroom: `data/mushroom_utility_SPMF.txt` — threshold `600000`
- Chainstore: `data/chainstore.txt` — threshold `1000000`
- Chess: `data/chess_utility_spmf.txt` — threshold `2050000`

Runner sẽ tự động:

1. Mine baseline HUI bằng SPMF
2. Chọn sensitive itemset có utility lớn nhất từ baseline output
3. Chạy `FMLHProtector`
4. Mine lại sau sanitize
5. Tính và ghi chỉ số vào `benchmark_results.csv`

Lệnh chạy:

```bash
mvn -q test-compile && java -Xmx20G -cp target/classes:target/test-classes com.example.huihiding.BenchmarkRunner
```

Lưu ý: benchmark có thể mất vài phút đến vài chục phút tùy máy/RAM.
