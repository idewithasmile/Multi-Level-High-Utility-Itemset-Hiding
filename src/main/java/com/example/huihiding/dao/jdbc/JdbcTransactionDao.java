package com.example.huihiding.dao.jdbc;

import com.example.huihiding.config.DatabaseConfig;
import com.example.huihiding.dao.TransactionDao;
import com.example.huihiding.model.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Truy cap giao dich va item giao dich bang JDBC.
 */
public class JdbcTransactionDao implements TransactionDao {
    private final DatabaseConfig config;

    public JdbcTransactionDao(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public List<Transaction> loadAll() throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        try (Connection con = config.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT tid FROM transactions ORDER BY tid")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int tid = rs.getInt("tid");
                Transaction tx = new Transaction(tid);
                loadItems(con, tx);
                transactions.add(tx);
            }
        }
        return transactions;
    }

    private void loadItems(Connection con, Transaction tx) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT item, quantity FROM transaction_items WHERE tid = ?")) {
            ps.setInt(1, tx.getId());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                tx.setInternalUtility(rs.getString("item"), rs.getInt("quantity"));
            }
        }
    }

    @Override
    public void save(Transaction transaction) throws SQLException {
        try (Connection con = config.getConnection()) {
            con.setAutoCommit(false);
            // Su dung MERGE de chen/ cap nhat ca giao dich va cac item
            try (PreparedStatement insertTx = con.prepareStatement("MERGE INTO transactions (tid) KEY(tid) VALUES (?)");
                 PreparedStatement insertItem = con.prepareStatement("MERGE INTO transaction_items (tid, item, quantity) KEY(tid, item) VALUES (?,?,?)")) {
                insertTx.setInt(1, transaction.getId());
                insertTx.executeUpdate();
                insertItem.setInt(1, transaction.getId());
                for (var entry : transaction.getItemToQuantity().entrySet()) {
                    insertItem.setString(2, entry.getKey());
                    insertItem.setInt(3, entry.getValue());
                    insertItem.executeUpdate();
                }
                con.commit();
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    @Override
    public void clear() throws SQLException {
        try (Connection con = config.getConnection();
             PreparedStatement ps1 = con.prepareStatement("DELETE FROM transaction_items");
             PreparedStatement ps2 = con.prepareStatement("DELETE FROM transactions")) {
            ps1.executeUpdate();
            ps2.executeUpdate();
        }
    }
}
