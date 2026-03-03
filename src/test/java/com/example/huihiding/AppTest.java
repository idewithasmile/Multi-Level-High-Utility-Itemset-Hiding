package com.example.huihiding;

import com.example.huihiding.metrics.MetricCalculator;
import com.example.huihiding.metrics.MetricsResult;
import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Itemset;
import com.example.huihiding.model.Taxonomy;
import com.example.huihiding.model.Transaction;
import com.example.huihiding.service.FMLHProtector;
import com.example.huihiding.service.MLHProtector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {
    // Smoke test: dam bao main chay khong nem exception
    @Test
    void mainRuns() {
        assertDoesNotThrow(() -> App.main(new String[]{}));
    }

    @Test
    void metricsWithPaperInput_shouldReflectRealSanitization_notAllOnes() {
        HierarchicalDatabase original = createPaperLikeDatabase();
        List<Itemset> sensitive = createSensitiveGeneralizedItemsets();

        HierarchicalDatabase mlhDb = new MLHProtector(original, sensitive).sanitize();
        HierarchicalDatabase fmlhDb = new FMLHProtector(original, sensitive).sanitize();

        List<Itemset> baselineHigh = original.discoverHighUtilityItemsets(5);
        List<Itemset> mlhHigh = mlhDb.discoverHighUtilityItemsets(5);
        List<Itemset> fmlhHigh = fmlhDb.discoverHighUtilityItemsets(5);

        MetricCalculator metricCalculator = new MetricCalculator();
        MetricsResult mlhMetrics = metricCalculator.evaluate(original, mlhDb, sensitive, baselineHigh, mlhHigh);
        MetricsResult fmlhMetrics = metricCalculator.evaluate(original, fmlhDb, sensitive, baselineHigh, fmlhHigh);

        // Neu da sanitize that su, DUS se giam (<1) do tong utility DB thay doi
        assertTrue(mlhMetrics.getDus() < 1.0d, "MLH DUS dang 1.0, can kiem tra lai input/processing");
        assertTrue(fmlhMetrics.getDus() < 1.0d, "FMLH DUS dang 1.0, can kiem tra lai input/processing");

        // Va khong duoc am hoac >1
        assertTrue(mlhMetrics.getDus() >= 0.0d && mlhMetrics.getDus() <= 1.0d);
        assertTrue(fmlhMetrics.getDus() >= 0.0d && fmlhMetrics.getDus() <= 1.0d);
    }

    private static HierarchicalDatabase createPaperLikeDatabase() {
        HierarchicalDatabase db = new HierarchicalDatabase();
        db.setMinUtilityThreshold(88);

        db.setExternalUtility("A", 2);
        db.setExternalUtility("B", 1);
        db.setExternalUtility("C", 2);
        db.setExternalUtility("D", 1);
        db.setExternalUtility("E", 2);

        Taxonomy t = new Taxonomy();
        t.addEdge("All", "Y");
        t.addEdge("All", "Z");
        t.addEdge("Y", "X");
        t.addEdge("Y", "C");
        t.addEdge("X", "A");
        t.addEdge("X", "B");
        t.addEdge("Z", "D");
        t.addEdge("Z", "E");
        db.setTaxonomy(t);

        addTx(db, 1, mapOf("A", 3, "C", 7));
        addTx(db, 2, mapOf("A", 7, "C", 2, "D", 7, "E", 5));
        addTx(db, 3, mapOf("A", 8, "B", 3, "C", 6, "D", 8, "E", 9));
        addTx(db, 4, mapOf("B", 4, "C", 5, "D", 3, "E", 7));
        addTx(db, 5, mapOf("A", 9, "D", 8));
        addTx(db, 6, mapOf("A", 3, "C", 4, "E", 4));
        addTx(db, 7, mapOf("B", 6, "C", 7, "E", 6));
        addTx(db, 8, mapOf("C", 2, "D", 4));
        addTx(db, 9, mapOf("C", 8, "D", 3, "E", 7));
        addTx(db, 10, mapOf("A", 4, "C", 7, "D", 9));

        return db;
    }

    private static List<Itemset> createSensitiveGeneralizedItemsets() {
        List<Itemset> sensitive = new ArrayList<>();
        sensitive.add(new Itemset(Set.of("X", "E", "D", "C"), "XEDC"));
        sensitive.add(new Itemset(Set.of("X", "E"), "XE"));
        sensitive.add(new Itemset(Set.of("Z"), "Z"));
        return sensitive;
    }

    private static void addTx(HierarchicalDatabase db, int id, Map<String, Integer> items) {
        Transaction tx = new Transaction(id);
        items.forEach(tx::setInternalUtility);
        db.addTransaction(tx);
    }

    private static Map<String, Integer> mapOf(Object... kv) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return map;
    }
}
