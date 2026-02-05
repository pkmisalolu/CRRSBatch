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
            "CONTROL_TO_DATE"
    );


    // ==============================================================
    //  MAIN ENTRY POINT
    // ==============================================================

    public static String queryConverter(String sql) {

        String normalized = sql.trim().toUpperCase();

        if (normalized.startsWith("INSERT INTO BANK_RECON") ||
            normalized.startsWith("INSERT INTO CHECK_CONTROL") || 
            (normalized.startsWith("INSERT") && normalized.contains("BANK_RECON") ) ||
            (normalized.startsWith("INSERT") && normalized.contains("CHECK_CONTROL") )) {
            return handleInsert(sql);
        }

        if (normalized.startsWith("UPDATE BANK_RECON") ||
            normalized.startsWith("UPDATE CHECK_CONTROL") ||
            (normalized.startsWith("UPDATE") && normalized.contains("BANK_RECON") ) ||
            (normalized.startsWith("UPDATE") && normalized.contains("CHECK_CONTROL") )) {
            return handleUpdate(sql);
        }
        
        if (normalized.startsWith("DELETE FROM BANK_RECON") ||
        	    normalized.startsWith("DELETE FROM CHECK_CONTROL")||
        	    (normalized.startsWith("DELETE") && normalized.contains("BANK_RECON") ) ||
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
                "INSERT\\s+INTO\\s+([A-Z_]+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\((.*)\\)\\s*;?\\s*$",
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

}
        


