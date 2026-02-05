package com.abcbs.crrs.traces;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

public class AfterCommitSqlFormatter implements MessageFormattingStrategy {

    // Tables to include in logging
    private static final List<String> TARGET_TABLES = List.of("bank_description", "check_control", "bank_recon");
    
    //private static final Logger logger = LogManager.getLogger("");
    private static final Logger audit = LogManager.getLogger("AUDIT_LOG");
    private static final Logger auditDb2 = LogManager.getLogger("AUDIT_DB2_LOG");
    String prevSql="";

    private static final ThreadLocal<List<String>> SQL_BUFFER =
            ThreadLocal.withInitial(ArrayList::new);

    private static final ThreadLocal<Boolean> SYNC_REGISTERED =
            ThreadLocal.withInitial(() -> false);

    
    @Override
    public String formatMessage(int connectionId, String now, long elapsed,
        String category, String prepared, String sql, String url) {
        
        if (sql == null || sql.trim().isEmpty() || prevSql.equals(sql)) return "";
        prevSql=sql;
        
        String normalized = sql.trim().toLowerCase();
        
        // DML filter first - return immediately to suppress ALL p6spy logging
        if (!(normalized.startsWith("insert") || normalized.startsWith("update") || 
              normalized.startsWith("delete"))) {
            return "";
        }
        
        // Target table filter - return immediately to suppress
        boolean matches = TARGET_TABLES.stream().anyMatch(normalized::contains);
        if (!matches) return "";
        
        // NOW safe to buffer (no p6spy logging will occur)
        SQL_BUFFER.get().add(sql.trim() + ";");
        // Register synchronization ONLY if active
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    List<String> queries = new ArrayList<>(SQL_BUFFER.get()); // Copy to avoid mutation
                    if (!queries.isEmpty()) {
                        queries.forEach(q -> {
                            String formatted = q.replaceAll("\\+(\\d{2})(\\d{2})", "+$1:$2");
                            
                            audit.info(" " + formatted);
                            formatted=MSSQLtoDB2Converter.queryConverter(formatted);
                            if(!(formatted.endsWith(";"))) {
                            	formatted=formatted+";";
                            }
                            auditDb2.info(formatted);
                        });
                        queries.removeAll(queries); // Clear instead of remove
                        SQL_BUFFER.get().clear();
                        SQL_BUFFER.remove();
                    }
                }
                
                @Override
                public void afterCompletion(int status) {
                    SQL_BUFFER.get().clear(); // Always clear on completion
                    SQL_BUFFER.remove();
                }
            });
        }
        
        return ""; // Suppress ALL immediate p6spy logging
    }



}