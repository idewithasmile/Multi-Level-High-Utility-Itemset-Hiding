package com.example.huihiding.service;

import com.example.huihiding.dao.ExternalUtilityDao;
import com.example.huihiding.dao.TaxonomyDao;
import com.example.huihiding.dao.TransactionDao;
import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Taxonomy;
import com.example.huihiding.model.Transaction;

import javax.swing.JOptionPane;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

/**
 * Nap du lieu tu DAO len cac cau truc trong bo nho (taxonomy, external utility, transactions).
 */
public class DatabaseLoaderService {
    private final TransactionDao transactionDao;
    private final TaxonomyDao taxonomyDao;
    private final ExternalUtilityDao externalUtilityDao;

    public DatabaseLoaderService(TransactionDao transactionDao, TaxonomyDao taxonomyDao, ExternalUtilityDao externalUtilityDao) {
        this.transactionDao = transactionDao;
        this.taxonomyDao = taxonomyDao;
        this.externalUtilityDao = externalUtilityDao;
    }

    public HierarchicalDatabase load(double threshold) throws SQLException {
        HierarchicalDatabase db = new HierarchicalDatabase();
        // Nap taxonomy
        Taxonomy loadedTaxonomy = taxonomyDao.load();
        db.setTaxonomy(loadedTaxonomy != null ? loadedTaxonomy : new Taxonomy());
        // Nap external utility (eu)
        externalUtilityDao.loadAll().forEach(db::setExternalUtility);
        // Nap giao dich
        for (Transaction t : transactionDao.loadAll()) {
            db.addTransaction(t);
        }
        // Thiet lap nguong utility toi thieu
        db.setMinUtilityThreshold(threshold);
        return db;
    }

    /**
     * Load database from official SPMF MLHUIM files.
     * Transaction format per line: item1 item2 ...:TU:u1 u2 ...
     * Taxonomy format per line: child,parent
     */
    public HierarchicalDatabase loadSpmf(Path transactionPath,
                                         Path taxonomyPath,
                                         int threshold) throws IOException {
        try {
            SPMFDatabaseLoader loader = new SPMFDatabaseLoader();
            return loader.load(transactionPath, taxonomyPath, threshold);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                    null,
                    "Failed to load SPMF data:\n" + e.getMessage(),
                    "Load Error",
                    JOptionPane.ERROR_MESSAGE
            );
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to load SPMF data", e);
        }
    }
}
