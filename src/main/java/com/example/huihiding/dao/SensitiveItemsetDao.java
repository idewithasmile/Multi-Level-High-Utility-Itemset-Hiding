package com.example.huihiding.dao;

import com.example.huihiding.model.Itemset;
import java.sql.SQLException;
import java.util.List;

/**
 * Truy cap bang sensitive_itemsets luu cac tap nhay cam.
 */
public interface SensitiveItemsetDao {
    List<Itemset> loadAll() throws SQLException;
    void save(Itemset itemset) throws SQLException;
    void clear() throws SQLException;
}
