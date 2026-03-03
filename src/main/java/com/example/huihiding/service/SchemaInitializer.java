package com.example.huihiding.service;

import com.example.huihiding.config.DatabaseConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Doc va thi hanh schema.sql trong resources de khoi tao co so du lieu H2 kem du lieu mau.
 */
public class SchemaInitializer {
    private final DatabaseConfig config;

    public SchemaInitializer(DatabaseConfig config) {
        this.config = config;
    }

    public void initialize() throws SQLException, IOException {
        // Mo ket noi H2 va thuc thi tung cau lenh trong schema.sql
        try (Connection con = config.getConnection(); Statement stmt = con.createStatement()) {
            executeSqlResource(stmt, "/schema.sql");
        }
    }

    private void executeSqlResource(Statement stmt, String resourcePath) throws IOException, SQLException {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder command = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                        continue;
                    }
                    command.append(trimmed).append(' ');
                    if (trimmed.endsWith(";")) {
                        // Gap dau ; thi thuc thi cau SQL vua gom
                        String sql = command.toString();
                        stmt.execute(sql);
                        command.setLength(0);
                    }
                }
                // Thuc thi cau lenh cuoi neu khong co dau ;
                if (command.length() > 0) {
                    stmt.execute(command.toString());
                }
            }
        }
    }
}
