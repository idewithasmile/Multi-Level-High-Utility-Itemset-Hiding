package com.example.huihiding.dao;

import com.example.huihiding.model.Transaction;
import java.sql.SQLException;
import java.util.List;

/**
 * Truy cap bang transactions va transaction_items.
 */
public interface TransactionDao {
    List<Transaction> loadAll() throws SQLException;
    void save(Transaction transaction) throws SQLException;
    void clear() throws SQLException;
}
