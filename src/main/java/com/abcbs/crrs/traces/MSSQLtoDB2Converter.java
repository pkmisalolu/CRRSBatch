package com.abcbs.crrs.traces;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MSSQLtoDB2Converter {

    // ==============================================================
    //  LocalDate columns for BOTH TABLES
    // ==============================================================

    private static final Set<String> LOCAL_DATE_COLUMNS = Set.of(
            // bank_recon
            "CHECK_DATE",
            "CHECK_STATUS_DATE",
            "STALE_DATE",
            "TRANSFER_DATE",
            "INITIAL_CHECK_DATE",
            "PPA_DATE",
            "REISSUE_CHECK_DATE",
            "REPORT_DATE",
            "EFF_ENT_DATE",

            // check_control
            "CONTROL_FROM_DATE",
            "CONTROL_TO_DATE",
            
            //cash_receipt 
            "CR_CNTRL_DATE",
            "CR_RECEIVED_DATE",
            "CR_DEPOSIT_DATE",
            "CR_ENTRY_DATE",
            "CR_STATUS_DATE",
            "CR_CHECK_DATE",
            "CR_LETTER_DATE",
            "CR_ACCTS_REC_DATE",
            "CR_LOCATION_DATE",
            
            //ACTIVITY 
            "ACT_ACTIVITY_DATE",
            "ACT_XREF_DATE",
            "ACT_REPORT_DATE"
    );
    private static final Set<String> LOCAL_TIMESTAMP_COLUMNS = Set.of(
            "ACT_TIMESTAMP"
    );


    // ==============================================================
    //  MAIN ENTRY POINT
    // ==============================================================

    public static String queryConverter(String sql) {

        String normalized = sql.trim().toUpperCase();

        if (normalized.startsWith("INSERT INTO BANK_RECON") ||
            normalized.startsWith("INSERT INTO CHECK_CONTROL") || 
            normalized.startsWith("INSERT INTO P09_ACTIVITY") ||
            normalized.startsWith("INSERT INTO P09_CASH_RECEIPT") ||
            (normalized.startsWith("INSERT") && normalized.contains("BANK_RECON") ) ||
            (normalized.startsWith("INSERT") && normalized.contains("P09_ACTIVITY") ) ||
            (normalized.startsWith("INSERT") && normalized.contains("P09_CASH_RECEIPT") ) ||
            (normalized.startsWith("INSERT") && normalized.contains("CHECK_CONTROL") )) {
            return handleInsert(sql);
        }

        if (normalized.startsWith("UPDATE BANK_RECON") ||
            normalized.startsWith("UPDATE CHECK_CONTROL") ||
            normalized.startsWith("UPDATE P09_ACTIVITY") ||
            normalized.startsWith("UPDATE P09_CASH_RECEIPT") ||
            (normalized.startsWith("UPDATE") && normalized.contains("BANK_RECON") ) ||
            (normalized.startsWith("UPDATE") && normalized.contains("P09_ACTIVITY") ) ||
            (normalized.startsWith("UPDATE") && normalized.contains("P09_CASH_RECEIPT") ) ||
            (normalized.startsWith("UPDATE") && normalized.contains("CHECK_CONTROL") )) {
            return handleUpdate(sql);
        }
        
        if (normalized.startsWith("DELETE FROM BANK_RECON") ||
        	    normalized.startsWith("DELETE FROM CHECK_CONTROL")||
        	    normalized.startsWith("DELETE FROM P09_ACTIVITY")||
        	    normalized.startsWith("DELETE FROM P09_CASH_RECEIPT")||
        	    (normalized.startsWith("DELETE") && normalized.contains("BANK_RECON") ) ||
        	    (normalized.startsWith("DELETE") && normalized.contains("P09_ACTIVITY") ) ||
        	    (normalized.startsWith("DELETE") && normalized.contains("P09_CASH_RECEIPT") ) ||
                (normalized.startsWith("DELETE") && normalized.contains("CHECK_CONTROL") )) {
        	    return handleDelete(sql);
        }

        return sql;
    }


    // ==============================================================
    //  INSERT HANDLING
    // ==============================================================

    private static String handleInsert(String sql) {

        Pattern p = Pattern.compile(
                "INSERT\\s+INTO\\s+([A-Z0-9_]+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\((.*)\\)\\s*;?\\s*$",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher m = p.matcher(sql.trim());
        if (!m.find()) return sql;

        String table = m.group(1).trim();
        String[] columns = m.group(2).split("\\s*,\\s*");

        List<String> values = splitValues(m.group(3));

        if (values.size() != columns.length) {
            return sql;
        }

        for (int i = 0; i < columns.length; i++) {
            String col = columns[i].trim().toUpperCase();
            String val = values.get(i).trim();

            if (LOCAL_DATE_COLUMNS.contains(col)) {
                values.set(i, convertLiteralToDb2Date(val));
            }else if (LOCAL_TIMESTAMP_COLUMNS.contains(col)) {
                values.set(i, convertLiteralToDb2Timestamp(val));
            }
        }

        return "INSERT INTO " + table + " (" +
                String.join(", ", columns) +
                ") VALUES (" +
                String.join(", ", values) +
                ")";
    }



    // ==============================================================
    //  UPDATE HANDLING
    // ==============================================================

    private static String handleUpdate(String sql) {

        String result = sql;

        for (String col : LOCAL_DATE_COLUMNS) {

        	Pattern p = Pattern.compile(
    			    "(" +
    			        "(?:\\[?[A-Z0-9_]+\\]?\\.)?" +            
    			        "(?:\\[?" + Pattern.quote(col) + "\\]?)" +
    			        "\\s*(=|<=|>=|<|>)\\s*" +
    			    ")" +
    			    "(?:N)?" +                                   
    			    "'([^']+)'",                                 
    			    Pattern.CASE_INSENSITIVE
    			);

            Matcher m = p.matcher(result);
            StringBuffer sb = new StringBuffer();

            while (m.find()) {
                String prefix = m.group(1);
                String ts = m.group(3);
                String newVal = "'" + extractDateOnly(ts) + "'";
                m.appendReplacement(sb, prefix + newVal);
            }

            m.appendTail(sb);
            result = sb.toString();
        }
        for (String col : LOCAL_TIMESTAMP_COLUMNS) {
            result = replaceTimestampComparisons(result, col);
        }

        return result;
    }
    
    private static String handleDelete(String sql) {

    	String result = sql;

    	for (String col : LOCAL_DATE_COLUMNS) {

    		Pattern p = Pattern.compile(
    			    "(" +
    			        "(?:\\[?[A-Z0-9_]+\\]?\\.)?" +            
    			        "(?:\\[?" + Pattern.quote(col) + "\\]?)" +
    			        "\\s*(=|<=|>=|<|>)\\s*" +
    			    ")" +
    			    "(?:N)?" +                                   
    			    "'([^']+)'",                                 
    			    Pattern.CASE_INSENSITIVE
    			);

    	    Matcher m = p.matcher(result);
    	    StringBuffer sb = new StringBuffer();

    	    while (m.find()) {
    	        String prefix = m.group(1);   
    	        String ts = m.group(3);       // e.g. 2025-09-17T00:00:00+05:30

    	        String newVal = "'" + extractDateOnly(ts) + "'";

    	        m.appendReplacement(sb,
    	                Matcher.quoteReplacement(prefix + newVal));
    	    }

    	    m.appendTail(sb);
    	    result = sb.toString();
    	}
    	for (String col : LOCAL_TIMESTAMP_COLUMNS) {
            result = replaceTimestampComparisons(result, col);
        }
    	return result;
    }





    // ==============================================================
    //  LITERAL DATE CONVERSION
    // ==============================================================

    private static String convertLiteralToDb2Date(String literal) {
        if (literal == null) return literal;
        literal = literal.trim();

        if (!literal.startsWith("'") || literal.length() < 12)
            return literal;

        String inside = literal.substring(1, literal.length() - 1);

        return "'" + extractDateOnly(inside) + "'";
    }

    private static String extractDateOnly(String ts) {
        if (ts == null || ts.length() < 10) return ts;
        return ts.substring(0, 10);  // YYYY-MM-DD
    }


    // ==============================================================
    //  VALUE SPLITTER THAT RESPECTS QUOTES, TIMEZONES, COMMAS
    // ==============================================================

    private static List<String> splitValues(String raw) {

        List<String> list = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();

        for (char c : raw.toCharArray()) {

            if (c == '\'') {
                inQuotes = !inQuotes;
            }

            if (c == ',' && !inQuotes) {
                list.add(sb.toString());
                sb.setLength(0);
                continue;
            }

            sb.append(c);
        }

        list.add(sb.toString());
        return list;
    }
    private static String convertLiteralToDb2Timestamp(String literal) {

        if (literal == null) return literal;

        literal = literal.trim();

        if (!literal.startsWith("'") || !literal.endsWith("'")) {
            return literal;
        }

        String inside = literal.substring(1, literal.length() - 1).trim();

        inside = normalizeTimestamp(inside);

        return "'" + inside + "'";
    }
    private static String replaceTimestampComparisons(String sql, String col) {
        Pattern p = Pattern.compile(
                "(" +
                        "(?:\\[?[A-Z0-9_]+\\]?\\.)?" +
                        "(?:\\[?" + Pattern.quote(col) + "\\]?)" +
                        "\\s*(=|<=|>=|<|>)\\s*" +
                ")" +
                "(?:N)?" +
                "'([^']+)'",
                Pattern.CASE_INSENSITIVE
        );

        Matcher m = p.matcher(sql);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            String prefix = m.group(1);
            String val = m.group(3);
            String newVal = "'" + normalizeTimestamp(val) + "'";
            m.appendReplacement(sb, Matcher.quoteReplacement(prefix + newVal));
        }

        m.appendTail(sb);
        return sb.toString();
    }
    private static String normalizeTimestamp(String ts) {

        if (ts == null) return ts;

        String value = ts.trim();

        // Step 1: Replace T with space
        value = value.replace('T', ' ');

        // Step 2: Remove timezone (handles ALL cases)
        // Z
        // +5
        // -5
        // +05
        // -05
        // +5:30
        // -5:30
        // +05:30
        // -05:30
        value = value.replaceFirst("(Z|[+-]\\d{1,2}(:\\d{2})?)$", "");

        // Step 3: Normalize fraction to 6 digits
        if (value.contains(".")) {

            int dot = value.indexOf('.');
            String main = value.substring(0, dot);
            String fraction = value.substring(dot + 1);

            // remove any non-numeric garbage after fraction
            fraction = fraction.replaceAll("[^0-9].*$", "");

            if (fraction.length() > 6) {
                fraction = fraction.substring(0, 6);
            } else {
                while (fraction.length() < 6) {
                    fraction += "0";
                }
            }

            value = main + "." + fraction;

        } else {
            value = value + ".000000";
        }

        return value.trim();
    }
}
        


