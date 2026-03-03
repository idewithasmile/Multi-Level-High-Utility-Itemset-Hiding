package com.example.huihiding.dao.jdbc;

import com.example.huihiding.config.DatabaseConfig;
import com.example.huihiding.dao.TaxonomyDao;
import com.example.huihiding.model.Taxonomy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Truy cap bang taxonomy (parent-child) qua JDBC.
 */
public class JdbcTaxonomyDao implements TaxonomyDao {
    private final DatabaseConfig config;

    public JdbcTaxonomyDao(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public Taxonomy load() throws SQLException {
        Taxonomy taxonomy = new Taxonomy();
        try (Connection con = config.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT parent, child FROM taxonomy")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                taxonomy.addEdge(rs.getString("parent"), rs.getString("child"));
            }
        }
        return taxonomy;
    }

    @Override
    public void saveEdge(String parent, String child) throws SQLException {
        try (Connection con = config.getConnection();
             PreparedStatement ps = con.prepareStatement("MERGE INTO taxonomy (parent, child) KEY(parent, child) VALUES (?, ?)");) {
            ps.setString(1, parent);
            ps.setString(2, child);
            ps.executeUpdate();
        }
    }

    @Override
    public void clear() throws SQLException {
        try (Connection con = config.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM taxonomy")) {
            ps.executeUpdate();
        }
    }
}
