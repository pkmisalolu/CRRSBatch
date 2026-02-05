package com.abcbs.crrs.jobs.P09365;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class P09365Processor implements ItemProcessor<P09365InputGpIntrface, P09365OutputManualChks> {

    @Override
    public P09365OutputManualChks process(P09365InputGpIntrface in) {
    	P09365OutputManualChks out = new P09365OutputManualChks();

        // MOVE SPACES TO MANUAL-CHKS-REC.
        out.setMRefundType(rpad("", 3));
        out.setMFiller1(" ");
        out.setMCntrlDate(rpad("", 10));
        out.setMFiller2(" ");
        out.setMCntrlNbr(rpad("", 4));
        out.setMInvoiceDate(rpad("", 10));
        out.setMProviderNbr(rpad("", 9));
        out.setMPayeeName(rpad("", 36));
        out.setMPayeeAddr1(rpad("", 36));
        out.setMPayeeAddr2(rpad("", 36));
        out.setMPayeeCity(rpad("", 24));
        out.setMPayeeSt(rpad("", 2));
        out.setMPayeeZip1(rpad("", 5));
        out.setMPayeeZip2(rpad("", 4));
        out.setMInvoiceAmt(rpad("", 11));
        out.setMDesc(rpad("", 40));
        out.setMPayeeIdPrefix(rpad("", 4));
        out.setMPayeeIdType(rpad("", 1));
        out.setMCheckType(rpad("", 2));

        // MOVEs (exactly like COBOL)
        out.setMRefundType(rpad(rtrim(in.getORefundType()), 3));
        out.setMCntrlDate(rpad(rtrim(in.getOCntrlDate()), 10));
        out.setMCntrlNbr(rpad(rtrim(in.getOCntrlNbr()), 4));
        out.setMInvoiceDate(rpad(rtrim(in.getOInvoiceDate()), 10));
        out.setMProviderNbr(rpad(rtrim(in.getOProviderNbr()), 9));
        out.setMPayeeName(rpad(rtrim(in.getOPayeeName()), 36));
        out.setMPayeeAddr1(rpad(rtrim(in.getOPayeeAddr1()), 36));
        out.setMPayeeAddr2(rpad(rtrim(in.getOPayeeAddr2()), 36));
        out.setMPayeeCity(rpad(rtrim(in.getOPayeeCity()), 24));
        out.setMPayeeSt(rpad(rtrim(in.getOPayeeSt()), 2));
        out.setMPayeeZip1(rpad(rtrim(in.getOPayeeZip1()), 5));
        out.setMPayeeZip2(rpad(rtrim(in.getOPayeeZip2()), 4));

        // MOVE O-CHK-AMT TO M-INVOICE-AMT.
        out.setMInvoiceAmt(lpadDigits(in.getOChkAmt(), 11));

        // STRING O-MEMBER-ID DELIMITED BY ' '
        //        ' ' DELIMITED BY SIZE
        //        O-LNAME DELIMITED BY ' '
        //        ', ' DELIMITED BY SIZE
        //        O-FNAME DELIMITED BY ' '
        //   INTO M-DESC.
        String desc =
                firstToken(in.getOMemberId()) +
                " " +
                firstToken(in.getOLname()) +
                ", " +
                firstToken(in.getOFname());

        out.setMDesc(rpad(desc, 40));

        // MOVE 'CRRS' TO M-PAYEE-ID-PREFIX.
        out.setMPayeeIdPrefix("CRRS");

        // MOVE O-PAYEE-ID-TYPE TO M-PAYEE-ID-TYPE.
        out.setMPayeeIdType(rpad(rtrim(in.getOPayeeIdType()), 1));

        // MOVE O-CHK-TYPE TO M-CHECK-TYPE.
        out.setMCheckType(rpad(rtrim(in.getOChkType()), 2));

        return out;
    }

    private static String rtrim(String s) {
        if (s == null) return "";
        int i = s.length() - 1;
        while (i >= 0 && s.charAt(i) == ' ') i--;
        return s.substring(0, i + 1);
    }

    private static String firstToken(String s) {
        String t = rtrim(s);
        int idx = t.indexOf(' ');
        return (idx >= 0) ? t.substring(0, idx) : t;
    }

    private static String rpad(String s, int len) {
        if (s == null) s = "";
        if (s.length() >= len) return s.substring(0, len);
        return s + " ".repeat(len - s.length());
    }

    private static String lpadDigits(String s, int len) {
        if (s == null) s = "";
        s = s.replace(" ", "");
        if (s.length() >= len) return s.substring(s.length() - len);
        return "0".repeat(len - s.length()) + s;
    }
}