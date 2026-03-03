package com.example.huihiding.service;

import com.example.huihiding.dao.ExternalUtilityDao;
import com.example.huihiding.dao.TaxonomyDao;
import com.example.huihiding.dao.TransactionDao;
import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Transaction;
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
        db.setTaxonomy(taxonomyDao.load());
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
}
