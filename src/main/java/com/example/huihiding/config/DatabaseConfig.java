package com.example.huihiding.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Cau hinh ket noi JDBC toi H2 (duong dan mac dinh data/huihiding).
 */
public class DatabaseConfig {
    private final String url;
    private final String user;
    private final String password;

    public DatabaseConfig() {
        this("jdbc:h2:./data/huihiding;AUTO_SERVER=TRUE", "sa", "");
    }

    public DatabaseConfig(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
