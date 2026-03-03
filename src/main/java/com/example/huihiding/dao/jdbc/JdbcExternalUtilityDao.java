package com.example.huihiding.dao.jdbc;

import com.example.huihiding.config.DatabaseConfig;
import com.example.huihiding.dao.ExternalUtilityDao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Truy cap bang external_utilities qua JDBC.
 */
public class JdbcExternalUtilityDao implements ExternalUtilityDao {
    private final DatabaseConfig config;

    public JdbcExternalUtilityDao(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public Map<String, Double> loadAll() throws SQLException {
        Map<String, Double> utils = new HashMap<>();
        try (Connection con = config.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT item, eu FROM external_utilities")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                utils.put(rs.getString("item"), rs.getDouble("eu"));
            }
        }
        return utils;
    }

    @Override
    public void save(String item, double externalUtility) throws SQLException {
        try (Connection con = config.getConnection();
             PreparedStatement ps = con.prepareStatement("MERGE INTO external_utilities (item, eu) KEY(item) VALUES (?, ?)");) {
            ps.setString(1, item);
            ps.setDouble(2, externalUtility);
            ps.executeUpdate();
        }
    }

    @Override
    public void clear() throws SQLException {
        try (Connection con = config.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM external_utilities")) {
            ps.executeUpdate();
        }
    }
}
