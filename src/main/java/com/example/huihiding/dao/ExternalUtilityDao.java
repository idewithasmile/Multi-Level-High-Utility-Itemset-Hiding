package com.example.huihiding.dao;

import java.sql.SQLException;
import java.util.Map;

/**
 * Truy cap bang external_utilities (eu cua tung item).
 */
public interface ExternalUtilityDao {
    Map<String, Double> loadAll() throws SQLException;
    void save(String item, double externalUtility) throws SQLException;
    void clear() throws SQLException;
}
