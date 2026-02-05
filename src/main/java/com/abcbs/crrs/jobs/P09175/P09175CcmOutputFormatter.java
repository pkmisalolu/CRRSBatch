package com.abcbs.crrs.jobs.P09175;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class P09175CcmOutputFormatter {

    private static final DateTimeFormatter MMDDYY =
            DateTimeFormatter.ofPattern("MMddyy");

    private static String padRight(String v, int len) {
        if (v == null) v = "";
        return String.format("%-" + len + "s", v);
    }

    private static String padLeftZeros(BigDecimal v, int len) {
        if (v == null) return "0".repeat(len);
        // if field is PIC 9(15) cents, movePointRight(2) is correct; otherwise remove it
        String s = v.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toPlainString();
        return String.format("%" + len + "s", s).replace(' ', '0');
    }

    private static String safeRefundType1(String refundType) {
        if (refundType == null) return " ";
        refundType = refundType.trim();
        return refundType.isEmpty() ? " " : String.valueOf(refundType.charAt(0));
    }

    private static String dateIso(LocalDate d) {
        // 10 chars like "2026-01-29" or blank
        return d == null ? "" : d.toString();
    }

    private static String dateMmddyy(LocalDate d) {
        // 6 chars like "083123" (COBOL barcode style). If null, use blanks (or "000000" if you prefer)
        return d == null ? "" : d.format(MMDDYY);
    }

    /**
     * EXACT CCM-REC layout
     */
    public static String format(P09175SuspenseView r, P09175BatchView s) {

        StringBuilder sb = new StringBuilder(400);

        sb.append('*');                                               // C-ASTERISK
        sb.append(padRight(safeRefundType1(s.getRefundType()), 1));   // C-REFND-TYPE (1)

        sb.append(padRight(dateMmddyy(r.getCntrlDate()), 6));          // control date MMDDYY (6)
        sb.append(padRight(r.getCntrlNbr(), 4));
        sb.append('~');                                               // C-TILDE-1
        sb.append(padRight(r.getCheckNbr(), 14));

        sb.append('~');                                               // C-TILDE-2
        sb.append(padRight(r.getMbrIdNbr(), 20));
        sb.append('~');                                               // C-TILDE-3
        sb.append("CCM");                                             // C-CCM-TYPE
        sb.append('~');                                               // C-TILDE-4
        sb.append("BCBS");                                            // C-BARCODE-LOB
        sb.append('~');                                               // C-TILDE-5
        sb.append(padRight(r.getLocationNbr(), 3));

        sb.append('*');                                               // C-ASTERISK-2
        sb.append(padRight(r.getLocationNbr(), 20));
        sb.append(padRight(r.getLocationNbr(), 8));
        sb.append(padRight(r.getOtisNbr(), 13));
        sb.append(padRight(r.getSectionCode(), 2));
        sb.append(padRight(s.getRefundType(), 3));
        sb.append(padRight(dateIso(s.getControlDate()), 10));
        sb.append(padRight(r.getCntrlNbr(), 4));
        sb.append(padRight(s.getStatus(), 6));
        sb.append(padRight(dateIso(s.getStatusDate()), 10));
        sb.append(padRight(r.getEobRaInd(), 1));
        sb.append(padRight(r.getReceiptType(), 2));
        sb.append(padRight(r.getRemittorName(), 36));
        sb.append(padRight(r.getRemittorTitle(), 4));
        sb.append(padRight(r.getRemittorType(), 1));
        sb.append(padRight(r.getClaimType(), 4));
        sb.append(padRight(r.getOplInd(), 1));

        // ✅ FIX: letter date can be null
        sb.append(padRight(dateIso(r.getLetterDate()), 10));

        sb.append(padRight(r.getReasonCode(), 4));
        sb.append(padRight(r.getOtherCorr(), 21));
        sb.append(padRight(r.getCommentText(), 65));
        sb.append(padRight(r.getPatientFirst(), 11));
        sb.append(padRight(r.getPatientLast(), 15));
        sb.append(padRight(r.getChkAddress1(), 36));
        sb.append(padRight(r.getChkAddress2(), 36));
        sb.append(padRight(r.getChkCity(), 15));
        sb.append(padRight(r.getChkState(), 2));
        sb.append(padRight(r.getChkZip5(), 10));

        // ✅ FIX: check date can be null
        sb.append(padRight(dateIso(r.getCheckDate()), 10));

        sb.append(padRight(r.getCheckNbr(), 8));
        sb.append(padLeftZeros(r.getCheckAmt(), 15));                 // PIC 9(15)
        sb.append(padRight(r.getControlledAmt() == null ? "" : r.getControlledAmt().toPlainString(), 15));
        sb.append(padRight((r.getLocationNbr() == null ? "" : r.getLocationNbr())
                + (r.getLocationClerk() == null ? "" : r.getLocationClerk()), 7));

        return sb.toString();
    }

    private P09175CcmOutputFormatter() {}
}