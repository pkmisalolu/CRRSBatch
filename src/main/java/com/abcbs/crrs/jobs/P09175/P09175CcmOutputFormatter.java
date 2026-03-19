package com.abcbs.crrs.jobs.P09175;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class P09175CcmOutputFormatter {

    private static final DateTimeFormatter MMDDYY_COMPACT =
            DateTimeFormatter.ofPattern("MMddyy");

    private P09175CcmOutputFormatter() {
    }

    public static String format(P09175SuspenseView record,
                                P09175BatchView batch,
                                String locationTo) {

        StringBuilder sb = new StringBuilder(467);

        // C-INFO
        sb.append('*');                                                     // C-ASTERISK          PIC X
        sb.append(firstCharOrBlank(batch.getRefundType()));                 // C-REFND-TYPE        PIC X
        sb.append(padRight(dateMmddyyCompact(record.getCntrlDate()), 6));   // C-CNTL-DT           PIC X(6)
        sb.append(padRight(record.getCntrlNbr(), 4));                       // C-CNTL-NBR          PIC X(4)
        sb.append('~');                                                     // C-TILDE-1           PIC X
        sb.append(padRight(record.getCheckNbr(), 14));                      // C-CHK-NBR           PIC X(14)

        sb.append('~');                                                     // C-TILDE-2           PIC X
        sb.append(padRight(record.getMbrIdNbr(), 20));                      // C-MEMBER-ID         PIC X(20)
        sb.append('~');                                                     // C-TILDE-3           PIC X
        sb.append("CCM");                                                   // C-CCM-TYPE          PIC X(3)
        sb.append('~');                                                     // C-TILDE-4           PIC X
        sb.append(resolveBarcodeLob(record.getLocationNbr()));              // C-BARCODE-LOB       PIC X(4)
        sb.append('~');                                                     // C-TILDE-5           PIC X
        sb.append(padRight(nz(record.getLocationNbr()).trim(), 3));         // C-BARCODE-LOC-NBR   PIC X(3)
        sb.append('*');                                                     // C-ASTERISK-2        PIC X

        sb.append(padRight(locationTo, 20));                                // C-BUS-LOCATION      PIC X(20)
        sb.append(padRight(buildLocationDisplay(record), 8));               // C-LOCATION-NBR      PIC X(8)
        sb.append(padRight(record.getOtisNbr(), 13));                       // C-OTIS-NBR          PIC X(13)
        sb.append(padRight(record.getSectionCode(), 2));                    // C-SECTION-CODE      PIC X(2)
        sb.append(padRight(batch.getRefundType(), 3));                      // C-REFUND-TYPE       PIC X(3)
        sb.append(padRight(dateMmddyySpaced(batch.getControlDate()), 10));  // C-CONTROL-DATE      PIC X(10)
        sb.append(padRight(record.getCntrlNbr(), 4));                       // C-CONTROL-NBR       PIC X(4)
        sb.append(padRight(batch.getStatus(), 6));                          // C-STATUS            PIC X(6)
        sb.append(padRight(dateMmddyySpaced(batch.getStatusDate()), 10));   // C-STATUS-DATE       PIC X(10)
        sb.append(padRight(record.getEobRaInd(), 1));                       // C-EOB-IND           PIC X
        sb.append(padRight(record.getReceiptType(), 2));                    // C-RECEIPT-TYPE      PIC X(2)
        sb.append(padRight(record.getRemittorName(), 36));                  // C-REMITTOR-NAME     PIC X(36)
        sb.append(padRight(remittorTitle4(record.getRemittorTitle()), 4));  // C-REMITTOR-TITLE    PIC X(4)
        sb.append(padRight(record.getRemittorType(), 1));                   // C-REMITTOR-TYPE     PIC X
        sb.append(padRight(record.getClaimType(), 4));                      // C-CLAIM-TYPE        PIC X(4)
        sb.append(padRight(record.getOplInd(), 1));                         // C-OPL-IND           PIC X
        sb.append(padRight(dateMmddyySpaced(record.getLetterDate()), 10));  // C-LETTER-DATE       PIC X(10)
        sb.append(padRight(record.getReasonCode(), 4));                     // C-REASON-CODE       PIC X(4)
        sb.append(padRight(record.getOtherCorr(), 21));                     // C-OTHER-CORR        PIC X(21)
        sb.append(padRight(record.getCommentText(), 65));                   // C-COMMENTS          PIC X(65)
        sb.append(padRight(record.getPatientFirst(), 11));                  // C-PATIENT-FNAME     PIC X(11)
        sb.append(padRight(record.getPatientLast(), 15));                   // C-PATIENT-LNAME     PIC X(15)
        sb.append(padRight(record.getChkAddress1(), 36));                   // C-ADDR1             PIC X(36)
        sb.append(padRight(record.getChkAddress2(), 36));                   // C-ADDR2             PIC X(36)
        sb.append(padRight(record.getChkCity(), 15));                       // C-CITY              PIC X(15)
        sb.append(padRight(record.getChkState(), 2));                       // C-STATE             PIC X(2)
        sb.append(padRight(buildZip(record.getChkZip5(), record.getChkZip4()), 10)); // C-ZIP PIC X(10)
        sb.append(padRight(dateMmddyySpaced(record.getCheckDate()), 10));   // C-CHECK-DATE        PIC X(10)
        sb.append(padRight(record.getCheckNbr(), 8));                       // C-CHECK-NBR         PIC X(8)
        sb.append(padLeftZeros15(record.getCheckAmt()));                    // C-CHECK-AMOUNT      PIC 9(15)
        sb.append(padLeft(displayMoney15(record.getControlledAmt()), 15));  // C-CONTROLLED-AMOUNT PIC X(15)
        sb.append(padRight(buildLocationCode(record), 7));                  // C-LOCATION-CODE     PIC X(7)

        return sb.toString();
    }

    private static String resolveBarcodeLob(String locationNbr) {
        String loc = nz(locationNbr).trim();
        return ("210".equals(loc) || "137".equals(loc)) ? "FEP " : "BCBS";
    }

    private static String buildLocationDisplay(P09175SuspenseView record) {
        String locNbr = padRight(nz(record.getLocationNbr()).trim(), 3);
        String locClerk = padRight(nz(record.getLocationClerk()).trim(), 4);
        return locNbr + " " + locClerk;
    }

    private static String buildLocationCode(P09175SuspenseView record) {
        return nz(record.getLocationNbr()).trim() + nz(record.getLocationClerk()).trim();
    }

    private static String buildZip(String zip5, String zip4) {
        String z5 = nz(zip5).trim();
        String z4 = nz(zip4).trim();

        if (z5.isEmpty() && z4.isEmpty()) {
            return "";
        }
        if (z4.isEmpty()) {
            return z5;
        }
        return z5 + z4;
    }

    private static String remittorTitle4(String title) {
        String t = nz(title).trim();
        if (t.isEmpty()) {
            return "";
        }
        return t;
    }

    private static String firstCharOrBlank(String value) {
        String v = nz(value).trim();
        return v.isEmpty() ? " " : String.valueOf(v.charAt(0));
    }

    private static String dateMmddyyCompact(LocalDate date) {
        return date == null ? "" : date.format(MMDDYY_COMPACT);
    }

    private static String dateMmddyySpaced(LocalDate date) {
        if (date == null) {
            return "";
        }
        return String.format("%02d  %02d  %02d",
                date.getMonthValue(),
                date.getDayOfMonth(),
                date.getYear() % 100);
    }

    private static String displayMoney15(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
        return "$" + v.toPlainString();
    }

    private static String padRight(String value, int len) {
        String v = nz(value);
        if (v.length() >= len) {
            return v.substring(0, len);
        }
        return v + " ".repeat(len - v.length());
    }

    private static String padLeft(String value, int len) {
        String v = nz(value);
        if (v.length() >= len) {
            return v.substring(v.length() - len);
        }
        return " ".repeat(len - v.length()) + v;
    }

    private static String padLeftZeros15(BigDecimal value) {
        if (value == null) {
            return "0".repeat(15);
        }

        String s = value.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString();

        if (s.length() >= 15) {
            return s.substring(s.length() - 15);
        }
        return "0".repeat(15 - s.length()) + s;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}