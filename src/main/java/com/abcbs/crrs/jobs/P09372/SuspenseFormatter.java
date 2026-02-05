package com.abcbs.crrs.jobs.P09372;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.abcbs.crrs.entity.P09Suspense;

public final class SuspenseFormatter {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private SuspenseFormatter() {}

    public static String format(P09Suspense s) {

        StringBuilder sb = new StringBuilder(518);

        sb.append(pad(s.getBtBatchPrefix(), 3));
        sb.append(pad(s.getBtBatchDate(), 6));
        sb.append(pad(s.getBtBatchSuffix(), 2));
        sb.append(pad(s.getSpId().getCrRefundType(), 3));

        sb.append(s.getSpId().getCrCntrlDate());

        sb.append(pad(s.getSpId().getCrCntrlNbr(), 4));
        sb.append(pad(s.getCrBankAcctNbr(), 35));
        sb.append(pad(s.getCrCheckNbr(), 8));

        appendDateWithNF(sb, s.getCrCheckDate());
        appendAmountWithSign(sb, s.getCrCheckAmt());
        appendAmountWithSign(sb, s.getCrCntrldAmt());

        sb.append(pad(s.getCrReceiptType(), 2));
        sb.append(pad(s.getCrRemittorName(), 36));
        sb.append(pad(s.getCrRemittorType(), 1));
        sb.append(pad(s.getCrClaimType(), 4));
        sb.append(pad(s.getCrOplInd(), 1)); 
        sb.append(pad(s.getCrPatientLname(), 15));
        sb.append(pad(s.getCrPatientFname(), 11));
        sb.append(pad(s.getCrReasonCode(), 4));
        sb.append(pad(s.getCrEobRaInd(), 1));
        sb.append(pad(s.getCrOtherCorr(), 21)); 
        sb.append(pad(s.getCrProviderNbr(), 9));
        sb.append(pad(s.getCrMbrIdNbr(), 12));
        sb.append(pad(s.getCrTaxIdNbr(), 9));
        sb.append(pad(s.getCrVendorNbr(), 9)); 
        sb.append(pad(s.getCrOtisNbr(), 13));

        appendDateWithNF(sb, s.getCrLetterDate());
        appendDateWithNF(sb, s.getCrAcctsRecDate());

        sb.append(pad(s.getCrAcctsRecNbr(), 9));
        sb.append(pad(s.getCrGlAcctNbr(), 12));
        sb.append(pad(s.getCrCorp(), 2));
        sb.append(pad(s.getCrLocationNbr(), 3));
        sb.append(pad(s.getCrLocationClerk(), 4)); 
        sb.append(pad(s.getCrSectionCode(), 2));

        sb.append(pad(s.getCrChkAddress1(), 36));
        sb.append(pad(s.getCrChkAddress2(), 36));
        sb.append(pad(s.getCrChkCity(), 24));
        sb.append(pad(s.getCrChkState(), 2)); 
        sb.append(pad(s.getCrChkZip5(), 5));
        sb.append(pad(s.getCrChkZip4(), 4));

        sb.append(pad(s.getCrUserId(), 7));
        sb.append(pad(s.getCmtCommentText(), 65));
        sb.append(pad(s.getCrGrpName(), 15)); 
        sb.append(pad(s.getCrRemittorTitle(), 3)); 
        sb.append(pad(s.getNationalIdNbr(), 10));

        if (sb.length() != 518) {
            throw new IllegalStateException(
                    "OUTREC length = " + sb.length());
        }

        return sb.toString();
    }

    /* ========================= HELPERS ========================= */

    private static void appendDateWithNF(StringBuilder sb, LocalDate d) {
        if (d == null) {
            sb.append(nul(10)); // DATE
            sb.append("-");         // NF-IND
            sb.append("1");         // NF
        } else {
            sb.append(d.format(DATE_FMT));
            sb.append(" ");         // NF-IND
            sb.append("0");         // NF
        }
    }

    private static void appendAmountWithSign(StringBuilder sb, BigDecimal amt) {

        if (amt == null) {
            sb.append(" ");
            sb.append("00000000000");
            return;
        }

        sb.append(amt.signum() < 0 ? "-" : " ");
        BigDecimal scaled = amt.abs().movePointRight(2);
        String value = String.format("%011d", scaled.longValueExact());

        sb.append(value);
    }
    private static String nul(int len) {
        return "\0".repeat(len);
    }


    private static String pad(String v, int len) {
        if (v == null) v = "";
        return v.length() >= len
                ? v.substring(0, len)
                : String.format("%-" + len + "s", v);
    }
}
