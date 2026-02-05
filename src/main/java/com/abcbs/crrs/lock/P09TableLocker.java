package com.abcbs.crrs.lock;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class P09TableLocker {

    private final EntityManager entityManager;
    private final String dialect;

    public P09TableLocker(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.dialect = entityManager.getEntityManagerFactory()
                .getProperties()
                .getOrDefault("hibernate.dialect", "")
                .toString()
                .toLowerCase();

        log.info("P09TableLocker initialized. Detected DB dialect={}", dialect);
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================
    @Transactional
    public void lockP09Tables() {
        log.info("=== BEGIN TABLE LOCKING for P09 tables ===");

        if (isSqlServer()) {
            lockSqlServer();
        } else if (isOracle()) {
            lockOracle();
        } else if (isPostgres()) {
            lockPostgres();
        } else if (isMySql()) {
            lockMySql();
        } else {
            log.warn("Unknown dialect – using JPA pessimistic lock fallback.");
            lockJpaFallback();
        }

        log.info("=== TABLE LOCKING COMPLETE ===");
    }

    @Transactional
    public void unlockP09Tables() {
        log.info("=== BEGIN TABLE UNLOCK for P09 tables ===");

        if (isMySql()) {
            unlockMySql();
        } else {
            log.info("Unlock handled implicitly by COMMIT for this DB.");
        }

        log.info("=== TABLE UNLOCK COMPLETE ===");
    }

    // =========================================================================
    // DIALECT HELPERS
    // =========================================================================
    private boolean isSqlServer() { return dialect.contains("sqlserver"); }
    private boolean isOracle()    { return dialect.contains("oracle"); }
    private boolean isPostgres()  { return dialect.contains("postgres"); }
    private boolean isMySql()     { return dialect.contains("mysql"); }

    // =========================================================================
    // SQL SERVER LOCKING
    // =========================================================================
    private void lockSqlServer() {
        log.info("Locking using SQL Server TABLOCKX + HOLDLOCK");

        lockNative("SELECT TOP (1) 1 FROM P09_CASH_RECEIPT WITH (TABLOCKX, HOLDLOCK)");
        lockNative("SELECT TOP (1) 1 FROM P09_ACTIVITY     WITH (TABLOCKX, HOLDLOCK)");
        lockNative("SELECT TOP (1) 1 FROM P09_CONTROL      WITH (TABLOCKX, HOLDLOCK)");

        log.info("SQL Server locks acquired.");
    }

    // =========================================================================
    // ORACLE LOCKING
    // =========================================================================
    private void lockOracle() {
        log.info("Locking using Oracle LOCK TABLE");

        lockNative("LOCK TABLE P09_CASH_RECEIPT IN EXCLUSIVE MODE");
        lockNative("LOCK TABLE P09_ACTIVITY     IN EXCLUSIVE MODE");
        lockNative("LOCK TABLE P09_CONTROL      IN EXCLUSIVE MODE");

        log.info("Oracle locks acquired.");
    }

    // =========================================================================
    // POSTGRES LOCKING
    // =========================================================================
    private void lockPostgres() {
        log.info("Locking using PostgreSQL LOCK TABLE");

        lockNative("LOCK TABLE P09_CASH_RECEIPT IN EXCLUSIVE MODE");
        lockNative("LOCK TABLE P09_ACTIVITY     IN EXCLUSIVE MODE");
        lockNative("LOCK TABLE P09_CONTROL      IN EXCLUSIVE MODE");

        log.info("Postgres locks acquired.");
    }

    // =========================================================================
    // MYSQL LOCKING
    // =========================================================================
    private void lockMySql() {
        log.info("Locking using MySQL LOCK TABLES WRITE");

        lockNative("LOCK TABLES P09_CASH_RECEIPT WRITE, P09_ACTIVITY WRITE, P09_CONTROL WRITE");

        log.info("MySQL locks acquired.");
    }

    private void unlockMySql() {
        log.info("MySQL unlock using UNLOCK TABLES");
        lockNative("UNLOCK TABLES");
    }

    // =========================================================================
    // FALLBACK JPA PESSIMISTIC WRITE LOCK
    // =========================================================================
    private void lockJpaFallback() {
        log.info("Applying fallback PESSIMISTIC_WRITE locks");

        pessimisticLock("P09CashReceipt");
        pessimisticLock("P09Activity");
        pessimisticLock("P09Control");

        log.info("Fallback JPA pessimistic locks acquired.");
    }

    private void pessimisticLock(String entityName) {
        try {
            entityManager.createQuery("SELECT e FROM " + entityName + " e")
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .setMaxResults(1)
                    .getResultList();

            log.info("Locked entity: {}", entityName);
        } catch (Exception ex) {
            log.error("Failed to lock entity {}", entityName, ex);
        }
    }

    // =========================================================================
    // EXECUTE LOCK SQL (CORRECTED)
    // =========================================================================
    private void lockNative(String sql) {
        try {
            Query q = entityManager.createNativeQuery(sql);

            // Must NOT use executeUpdate() for SELECT
            // Hibernate requires getSingleResult() to run SELECT statements
            q.getSingleResult();

            log.debug("Executed lock SQL: {}", sql);
        } catch (Exception e) {
            log.error("Native SQL failed: {}", sql, e);
            throw new RuntimeException("Lock failed: " + e.getMessage(), e);
        }
    }
}
