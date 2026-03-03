package com.example.huihiding.dao;

import com.example.huihiding.model.Taxonomy;
import java.sql.SQLException;

/**
 * Truy cap bang taxonomy (quan he parent-child).
 */
public interface TaxonomyDao {
    Taxonomy load() throws SQLException;
    void saveEdge(String parent, String child) throws SQLException;
    void clear() throws SQLException;
}
