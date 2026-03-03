package com.example.huihiding.dao.jdbc;

import com.example.huihiding.config.DatabaseConfig;
import com.example.huihiding.dao.SensitiveItemsetDao;
import com.example.huihiding.model.Itemset;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Truy cap bang sensitive_itemsets bang JDBC.
 */
public class JdbcSensitiveItemsetDao implements SensitiveItemsetDao {
    private final DatabaseConfig config;

    public JdbcSensitiveItemsetDao(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public List<Itemset> loadAll() throws SQLException {
        List<Itemset> list = new ArrayList<>();
        try (Connection con = config.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT name, items FROM sensitive_itemsets")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String name = rs.getString("name");
                String items = rs.getString("items");
                Set<String> set = parseItems(items);
                list.add(new Itemset(set, name));
            }
        }
        return list;
    }

    private Set<String> parseItems(String items) {
        // Tach chuoi "A,B,C" thanh tap item
        Set<String> result = new HashSet<>();
        if (items != null && !items.isBlank()) {
            for (String token : items.split(",")) {
                result.add(token.trim());
            }
        }
        return result;
    }

    @Override
    public void save(Itemset itemset) throws SQLException {
        try (Connection con = config.getConnection();
             PreparedStatement ps = con.prepareStatement("MERGE INTO sensitive_itemsets (name, items) KEY(name) VALUES (?, ?)");) {
            ps.setString(1, itemset.getLabel());
            String joined = itemset.getItems().stream().sorted().collect(Collectors.joining(","));
            ps.setString(2, joined);
            ps.executeUpdate();
        }
    }

    @Override
    public void clear() throws SQLException {
        try (Connection con = config.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM sensitive_itemsets")) {
            ps.executeUpdate();
        }
    }
}
