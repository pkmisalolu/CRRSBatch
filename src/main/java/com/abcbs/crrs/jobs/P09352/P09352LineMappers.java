package com.abcbs.crrs.jobs.P09352;

import java.math.BigDecimal;
import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.batch.item.file.transform.FieldSet;

public class P09352LineMappers {

    // =========================================================================
    //  CONTROL-CARD INPUT (80 bytes) — CORRECT FOR YOUR POJO
    // =========================================================================
    public static LineMapper<P09352ControlCardInput> controlCardLineMapper() {

        DefaultLineMapper<P09352ControlCardInput> lm = new DefaultLineMapper<>();
        FixedLengthTokenizer t = new FixedLengthTokenizer();
        t.setStrict(false);

        t.setNames(
           "cntrlId",
           "pgmId",
           "seqNbr",
           "filler1",
           "runTypeInd",
           "filler2",
           "compareMm",
           "compareDd",
           "compareYy",
           "filler3"
        );

        t.setColumns(
            new Range(1,2),    // cntrlId        X(02)
            new Range(3,8),    // pgmId          X(06)
            new Range(9,9),    // seqNbr         X(01)
            new Range(10,11),  // filler1        X(02)
            new Range(12,12),  // runTypeInd     X(01)
            new Range(13,13),  // filler2        X(01)
            new Range(14,15),  // compareMm     9(02)
            new Range(16,17),  // compareDd     9(02)
            new Range(18,19),  // compareYy     9(02)
            new Range(20,80)   // filler3       X(61)
        );

       lm.setLineTokenizer(t);

       lm.setFieldSetMapper(fs -> {
           P09352ControlCardInput o = new P09352ControlCardInput();

           o.setCntrlId(fs.readString("cntrlId"));
           o.setPgmId(fs.readString("pgmId"));
           o.setSeqNbr(fs.readString("seqNbr"));
           o.setFiller1(fs.readString("filler1"));
           o.setRunTypeInd(fs.readString("runTypeInd"));
           o.setFiller2(fs.readString("filler2"));
           o.setCompareMm(fs.readString("compareMm"));
           o.setCompareDd(fs.readString("compareDd"));
           o.setCompareYy(fs.readString("compareYy"));
           o.setFiller3(fs.readString("filler3"));

           return o;
        });

       return lm;
    }

   // =========================================================================
   //  CORP-CARD INPUT (80 bytes) — CORRECT FOR YOUR POJO
   // =========================================================================
   public static LineMapper<P09352CorpCardInput> corpCardLineMapper() {

       DefaultLineMapper<P09352CorpCardInput> lm = new DefaultLineMapper<>();
       FixedLengthTokenizer t = new FixedLengthTokenizer();
       t.setStrict(false);

       t.setNames(
           "filler1",
           "corpCode",
           "filler2"
       );

       t.setColumns(
           new Range(1,10),   // filler1   X(10)
           new Range(11,12),  // corpCode  X(02)
           new Range(13,80)   // filler2   X(68)
       );

       lm.setLineTokenizer(t);

       lm.setFieldSetMapper(fs -> {
           P09352CorpCardInput o = new P09352CorpCardInput();
           o.setFiller1(fs.readString("filler1"));
           o.setCorpCode(fs.readString("corpCode"));
           o.setFiller2(fs.readString("filler2"));
           return o;
       });

       return lm;
   }

   //=========================================================================
   // CHECKPOINT-CARD INPUT (80 bytes) — CORRECT FOR YOUR POJO
   //=========================================================================
   public static LineMapper<P09352CheckpointCardInput> checkpointLineMapper() {

       DefaultLineMapper<P09352CheckpointCardInput> lm = new DefaultLineMapper<>();
       FixedLengthTokenizer t = new FixedLengthTokenizer();
       t.setStrict(false);

       t.setNames(
           "count",
           "filler"
       );

       t.setColumns(
           new Range(1,6),    // count   9(06)
           new Range(7,80)    // filler  X(74)
       );

       lm.setLineTokenizer(t);

       lm.setFieldSetMapper(fs -> {
           P09352CheckpointCardInput o = new P09352CheckpointCardInput();
           o.setCount(fs.readString("count"));
           o.setFiller(fs.readString("filler"));
           return o;
       });

       return lm;
   }

    // =========================================================================
    //  INPUT-VOUCHER (IVOUCHER – 80 bytes)
    // =========================================================================
    public static LineMapper<P09352InputVoucher> inputVoucherLineMapper() {

        DefaultLineMapper<P09352InputVoucher> lm = new DefaultLineMapper<>();
        FixedLengthTokenizer t = new FixedLengthTokenizer();
        t.setStrict(false);

        t.setNames(
            "inputLastVoucherNbrPrefix",
            "inputLastVoucherNbrSuffix",
            "inputLastVoucherNbrFiller"
        );

        t.setColumns(
            new Range(1,1),
            new Range(2,6),
            new Range(7,80)
        );

        lm.setLineTokenizer(t);

        lm.setFieldSetMapper(fs -> {
            P09352InputVoucher o = new P09352InputVoucher();
            o.setInputLastVoucherNbrPrefix(fs.readString("inputLastVoucherNbrPrefix"));

            // ✅ FIX: suffix is numeric in your POJO (int/Integer) -> parse safely
            o.setInputLastVoucherNbrSuffix(toIntOrZero(fs, "inputLastVoucherNbrSuffix"));

            o.setInputLastVoucherNbrFiller(fs.readString("inputLastVoucherNbrFiller"));
            return o;
        });

        return lm;
    }

    // =========================================================================
    //  AP-INTERFACE-OUT (275 bytes)
    // =========================================================================
    public static LineMapper<P09352ApInterfaceOutput> apInterfaceLineMapper() {
        DefaultLineMapper<P09352ApInterfaceOutput> lm = new DefaultLineMapper<>();
        FixedLengthTokenizer t = new FixedLengthTokenizer();

        t.setStrict(false);

        t.setNames(
            "oRecdCode","filler1","oVoucherNbr",
            "oInvoiceMm","oSlash1","oInvoiceDd","oSlash2","oInvoiceCc","oInvoiceYy",
            "filler2","oTaxIdNbr","oPayeeIdType","oProviderNbr",
            "oPayeeName","oPayeeAddr1","oPayeeAddr2","oPayeeCity","oPayeeSt",
            "oPayeeZip1","oPayeeZip2","filler3",
            "oChkAmt","oNegInd","oChkNbr","oChkDate","oChkType","oBankAcctNbr",
            "oNationalId","oRefundType","oCntrlDate","oCntrlNbr",
            "oMemberId","oLname","oFname"
        );

        t.setColumns(
            new Range(1,1), new Range(2,2), new Range(3,8),
            new Range(9,10), new Range(11,11), new Range(12,13),
            new Range(14,14), new Range(15,16), new Range(17,18),
            new Range(19,19), new Range(20,28), new Range(29,29), new Range(30,38),
            new Range(39,68), new Range(69,103), new Range(104,138),
            new Range(139,158), new Range(159,160),
            new Range(161,165), new Range(166,169), new Range(170,170),
            new Range(171,181), new Range(182,182), new Range(183,190),
            new Range(191,200), new Range(201,202), new Range(203,210),
            new Range(211,220), new Range(221,223), new Range(224,233), new Range(234,237),
            new Range(238,249), new Range(250,264), new Range(265,275)
        );

        lm.setLineTokenizer(t);

        lm.setFieldSetMapper(fs -> {
            P09352ApInterfaceOutput o = new P09352ApInterfaceOutput();

            o.setORecdCode(fs.readString("oRecdCode"));
            o.setFiller1(fs.readString("filler1"));
            o.setOVoucherNbr(fs.readString("oVoucherNbr"));

            o.setOInvoiceMm(fs.readString("oInvoiceMm"));
            o.setOSlash1(fs.readString("oSlash1"));
            o.setOInvoiceDd(fs.readString("oInvoiceDd"));
            o.setOSlash2(fs.readString("oSlash2"));
            o.setOInvoiceCc(fs.readString("oInvoiceCc"));
            o.setOInvoiceYy(fs.readString("oInvoiceYy"));

            o.setFiller2(fs.readString("filler2"));
            o.setOTaxIdNbr(fs.readString("oTaxIdNbr"));
            o.setOPayeeIdType(fs.readString("oPayeeIdType"));
            o.setOProviderNbr(fs.readString("oProviderNbr"));

            o.setOPayeeName(fs.readString("oPayeeName"));
            o.setOPayeeAddr1(fs.readString("oPayeeAddr1"));
            o.setOPayeeAddr2(fs.readString("oPayeeAddr2"));
            o.setOPayeeCity(fs.readString("oPayeeCity"));
            o.setOPayeeSt(fs.readString("oPayeeSt"));

            o.setOPayeeZip1(fs.readString("oPayeeZip1"));
            o.setOPayeeZip2(fs.readString("oPayeeZip2"));
            o.setFiller3(fs.readString("filler3"));

            o.setOChkAmt(toBigDecimal(fs,"oChkAmt"));
            o.setONegInd(fs.readString("oNegInd"));
            o.setOChkNbr(fs.readString("oChkNbr"));
            o.setOChkDate(fs.readString("oChkDate"));
            o.setOChkType(fs.readString("oChkType"));
            o.setOBankAcctNbr(fs.readString("oBankAcctNbr"));

            o.setONationalId(fs.readString("oNationalId"));
            o.setORefundType(fs.readString("oRefundType"));
            o.setOCntrlDate(fs.readString("oCntrlDate"));
            o.setOCntrlNbr(fs.readString("oCntrlNbr"));

            o.setOMemberId(fs.readString("oMemberId"));
            o.setOLname(fs.readString("oLname"));
            o.setOFname(fs.readString("oFname"));

            return o;
        });

        return lm;
    }

    // =========================================================================
    //  XP07 (500 bytes, FULL COBOL RECORD)
    // =========================================================================
    public static LineMapper<P09352XP07DedsOutput> xp07LineMapper() {
        DefaultLineMapper<P09352XP07DedsOutput> lm = new DefaultLineMapper<>();
        FixedLengthTokenizer t = new FixedLengthTokenizer();
        t.setStrict(false);

        t.setNames(
            "p07JulianDate","p07TimeHhmmsss","p07Filler1",
            "p07ActionId","p07BatchNbr","p07FileCode","p07AccountNbr",
            "p07ChkFiller","p07ChkNbr",
            "p07ChkDate","p07ChkAmt",
            "p07PayeeId","p07Npi","p07PayeeName","p07Title",
            "p07Addr1","p07Addr2","p07City","p07State",
            "p07Zip5","p07Zip4",
            "p07ChkOrigin","p07ChkType","p07ChkStatus","p07ChkStatusDate","p07StatusSource",
            "p07OsDaily","p07StDaily","p07SdDaily","p07SdDailyNf",
            "p07TsDaily","p07TsDailyNf",
            "p07StaleDate","p07StaleDateNf","p07StaleOrigin","p07StaleOriginNf",
            "p07TransferDate","p07TransferDateNf","p07TransferOrigin","p07TransferOriginNf",
            "p07ReissueAcctNbr","p07ReissueAcctNbrNf","p07ReissueChkNbr","p07ReissueChkNbrNf",
            "p07ReissueChkDate","p07ReissueChkDateNf","p07ReissueChkType","p07ReissueChkTypeNf",
            "p07ReportDate",
            "p07InitialAcctNbr","p07InitialAcctNbrNf","p07InitialChkNbr","p07InitialChkNbrNf",
            "p07InitialChkDate","p07InitialChkDateNf","p07InitialChkType","p07InitialChkTypeNf",
            "p07PpaDate","p07PpaDateNf",
            "p07PayeeIdType","p07PayeeIdTypeNf",
            "p07TaxIdNbr","p07TaxIdNbrNf",
            "p07FillerFinal"
        );

        t.setColumns(
            new Range(1,3), new Range(4,7), new Range(8,12),
            new Range(13,14), new Range(15,20), new Range(21,21),
            new Range(22,56),
            new Range(57,58), new Range(59,64),
            new Range(65,74), new Range(75,85),
            new Range(86,94), new Range(95,104), new Range(105,140), new Range(141,143),
            new Range(144,179), new Range(180,215), new Range(216,239), new Range(240,241),
            new Range(242,246), new Range(247,250),
            new Range(251,251), new Range(252,253), new Range(254,255),
            new Range(256,265), new Range(266,267),
            new Range(268,268), new Range(269,269), new Range(270,270),
            new Range(271,271),
            new Range(272,272), new Range(273,273),
            new Range(274,283), new Range(284,284), new Range(285,285), new Range(286,286),
            new Range(287,296), new Range(297,297), new Range(298,298), new Range(299,299),
            new Range(300,334), new Range(335,335),
            new Range(336,343), new Range(344,344),
            new Range(345,354), new Range(355,355),
            new Range(356,357), new Range(358,358),
            new Range(359,368),
            new Range(369,403), new Range(404,404),
            new Range(405,412), new Range(413,413),
            new Range(414,423), new Range(424,424),
            new Range(425,426), new Range(427,427),
            new Range(428,437), new Range(438,438),
            new Range(439,439), new Range(440,440),
            new Range(441,449), new Range(450,450),
            new Range(451,500)
        );

        lm.setLineTokenizer(t);

        lm.setFieldSetMapper(fs -> {
            P09352XP07DedsOutput o = new P09352XP07DedsOutput();

            o.setP07JulianDate(toBigDecimal(fs,"p07JulianDate"));
            o.setP07TimeHhmmsss(toBigDecimal(fs,"p07TimeHhmmsss"));

            o.setP07Filler1(fs.readString("p07Filler1"));
            o.setP07ActionId(fs.readString("p07ActionId"));
            o.setP07BatchNbr(fs.readString("p07BatchNbr"));
            o.setP07FileCode(fs.readString("p07FileCode"));
            o.setP07AccountNbr(fs.readString("p07AccountNbr"));

            o.setP07ChkFiller(fs.readString("p07ChkFiller"));
            o.setP07ChkNbr(fs.readString("p07ChkNbr"));
            o.setP07ChkDate(fs.readString("p07ChkDate"));
            o.setP07ChkAmt(toBigDecimal(fs,"p07ChkAmt"));

            o.setP07PayeeId(fs.readString("p07PayeeId"));
            o.setP07Npi(fs.readString("p07Npi"));
            o.setP07PayeeName(fs.readString("p07PayeeName"));
            o.setP07Title(fs.readString("p07Title"));
            o.setP07Addr1(fs.readString("p07Addr1"));
            o.setP07Addr2(fs.readString("p07Addr2"));
            o.setP07City(fs.readString("p07City"));
            o.setP07State(fs.readString("p07State"));

            o.setP07Zip5(fs.readString("p07Zip5"));
            o.setP07Zip4(fs.readString("p07Zip4"));

            o.setP07ChkOrigin(fs.readString("p07ChkOrigin"));
            o.setP07ChkType(fs.readString("p07ChkType"));
            o.setP07ChkStatus(fs.readString("p07ChkStatus"));
            o.setP07ChkStatusDate(fs.readString("p07ChkStatusDate"));
            o.setP07StatusSource(fs.readString("p07StatusSource"));

            o.setP07OsDaily(fs.readString("p07OsDaily"));
            o.setP07StDaily(fs.readString("p07StDaily"));
            o.setP07SdDaily(fs.readString("p07SdDaily"));
            o.setP07SdDailyNf(toInteger(fs,"p07SdDailyNf"));
            o.setP07TsDaily(fs.readString("p07TsDaily"));
            o.setP07TsDailyNf(toInteger(fs,"p07TsDailyNf"));

            o.setP07StaleDate(fs.readString("p07StaleDate"));
            o.setP07StaleDateNf(toInteger(fs,"p07StaleDateNf"));
            o.setP07StaleOrigin(fs.readString("p07StaleOrigin"));
            o.setP07StaleOriginNf(toInteger(fs,"p07StaleOriginNf"));

            o.setP07TransferDate(fs.readString("p07TransferDate"));
            o.setP07TransferDateNf(toInteger(fs,"p07TransferDateNf"));
            o.setP07TransferOrigin(fs.readString("p07TransferOrigin"));
            o.setP07TransferOriginNf(toInteger(fs,"p07TransferOriginNf"));

            o.setP07ReissueAcctNbr(fs.readString("p07ReissueAcctNbr"));
            o.setP07ReissueAcctNbrNf(toInteger(fs,"p07ReissueAcctNbrNf"));
            o.setP07ReissueChkNbr(fs.readString("p07ReissueChkNbr"));
            o.setP07ReissueChkNbrNf(toInteger(fs,"p07ReissueChkNbrNf"));
            o.setP07ReissueChkDate(fs.readString("p07ReissueChkDate"));
            o.setP07ReissueChkDateNf(toInteger(fs,"p07ReissueChkDateNf"));
            o.setP07ReissueChkType(fs.readString("p07ReissueChkType"));
            o.setP07ReissueChkTypeNf(toInteger(fs,"p07ReissueChkTypeNf"));

            o.setP07ReportDate(fs.readString("p07ReportDate"));

            o.setP07InitialAcctNbr(fs.readString("p07InitialAcctNbr"));
            o.setP07InitialAcctNbrNf(toInteger(fs,"p07InitialAcctNbrNf"));
            o.setP07InitialChkNbr(fs.readString("p07InitialChkNbr"));
            o.setP07InitialChkNbrNf(toInteger(fs,"p07InitialChkNbrNf"));
            o.setP07InitialChkDate(fs.readString("p07InitialChkDate"));
            o.setP07InitialChkDateNf(toInteger(fs,"p07InitialChkDateNf"));
            o.setP07InitialChkType(fs.readString("p07InitialChkType"));
            o.setP07InitialChkTypeNf(toInteger(fs,"p07InitialChkTypeNf"));

            o.setP07PpaDate(fs.readString("p07PpaDate"));
            o.setP07PpaDateNf(toInteger(fs,"p07PpaDateNf"));

            o.setP07PayeeIdType(fs.readString("p07PayeeIdType"));
            o.setP07PayeeIdTypeNf(toInteger(fs,"p07PayeeIdTypeNf"));
            o.setP07TaxIdNbr(fs.readString("p07TaxIdNbr"));
            o.setP07TaxIdNbrNf(toInteger(fs,"p07TaxIdNbrNf"));

            o.setP07FillerFinal(fs.readString("p07FillerFinal"));

            return o;
        });

        return lm;
    }

    // =========================================================================
    //  XP09 (189 bytes)
    // =========================================================================
    public static LineMapper<P09352XP09DedsOutput> xp09LineMapper() {
        DefaultLineMapper<P09352XP09DedsOutput> lm = new DefaultLineMapper<>();
        FixedLengthTokenizer t = new FixedLengthTokenizer();
        t.setStrict(false);

        t.setNames(
            "glJulianDate","glTimeHhmmsss","glFiller1",
            "glRecordId","glRefundType","glControlDate","glControlNbr",
            "glReceiptType","glBankAcctNbr","glAcctNbr","glActCode",
            "glActAmt","glActDate","glReportMo","glReportYr",
            "glPatientLn","glPatientFn","glMemberId",
            "glXrefType","glXrefClaimNbr","glXrefDate",
            "glCashRecBal","glCorp","glFiller2"
        );

        t.setColumns(
            new Range(1,3),
            new Range(4,7),
            new Range(8,12),
            new Range(13,14),
            new Range(15,17),
            new Range(18,27),
            new Range(28,31),
            new Range(32,33),
            new Range(34,68),
            new Range(69,80),
            new Range(81,83),
            new Range(84,89),
            new Range(90,99),
            new Range(100,101),
            new Range(102,103),
            new Range(104,118),
            new Range(119,129),
            new Range(130,141),
            new Range(142,143),
            new Range(144,163),
            new Range(164,173),
            new Range(174,179),
            new Range(180,181),
            new Range(182,189)
        );

        lm.setLineTokenizer(t);

        lm.setFieldSetMapper(fs -> {
            P09352XP09DedsOutput o = new P09352XP09DedsOutput();

            o.setGlJulianDate(toBigDecimal(fs,"glJulianDate"));
            o.setGlTimeHhmmsss(toBigDecimal(fs,"glTimeHhmmsss"));
            o.setGlFiller1(fs.readString("glFiller1"));

            o.setGlRecordId(fs.readString("glRecordId"));
            o.setGlRefundType(fs.readString("glRefundType"));
            o.setGlControlDate(fs.readString("glControlDate"));
            o.setGlControlNbr(fs.readString("glControlNbr"));
            o.setGlReceiptType(fs.readString("glReceiptType"));

            o.setGlBankAcctNbr(fs.readString("glBankAcctNbr"));
            o.setGlAcctNbr(fs.readString("glAcctNbr"));
            o.setGlActCode(fs.readString("glActCode"));
            o.setGlActAmt(toBigDecimal(fs,"glActAmt"));
            o.setGlActDate(fs.readString("glActDate"));

            o.setGlReportMo(fs.readString("glReportMo"));
            o.setGlReportYr(fs.readString("glReportYr"));

            o.setGlPatientLn(fs.readString("glPatientLn"));
            o.setGlPatientFn(fs.readString("glPatientFn"));
            o.setGlMemberId(fs.readString("glMemberId"));

            o.setGlXrefType(fs.readString("glXrefType"));
            o.setGlXrefClaimNbr(fs.readString("glXrefClaimNbr"));
            o.setGlXrefDate(fs.readString("glXrefDate"));

            o.setGlCashRecBal(toBigDecimal(fs,"glCashRecBal"));
            o.setGlCorp(fs.readString("glCorp"));
            o.setGlFiller2(fs.readString("glFiller2"));

            return o;
        });

        return lm;
    }

 // =========================================================================
//  OUTPUT-VOUCHER (80 bytes)
// =========================================================================
public static LineMapper<P09352OutputVoucher> outputVoucherLineMapper() {
    DefaultLineMapper<P09352OutputVoucher> lm = new DefaultLineMapper<>();
    FixedLengthTokenizer t = new FixedLengthTokenizer();
    t.setStrict(false);

    t.setNames(
        "outputLastVoucherNbrPrefix",
        "outputLastVoucherNbrSuffix",
        "outputLastVoucherNbrFiller"
    );

    t.setColumns(
        new Range(1,1),   // Prefix  X(1)
        new Range(2,6),   // Suffix  9(5)  (keep as STRING in this POJO)
        new Range(7,80)   // Filler  X(74)
    );

    lm.setLineTokenizer(t);

    lm.setFieldSetMapper(fs -> {
        P09352OutputVoucher o = new P09352OutputVoucher();
        o.setOutputLastVoucherNbrPrefix(fs.readString("outputLastVoucherNbrPrefix"));
        o.setOutputLastVoucherNbrSuffix(fs.readString("outputLastVoucherNbrSuffix")); // ✅ FIX
        o.setOutputLastVoucherNbrFiller(fs.readString("outputLastVoucherNbrFiller"));
        return o;
    });

    return lm;
}

    // =========================================================================
    //  Utility
    // =========================================================================
    private static BigDecimal toBigDecimal(FieldSet fs, String field) {
        try {
            return fs.readBigDecimal(field);
        } catch (Exception e) {
            String v = fs.readString(field);
            if (v == null || v.trim().isEmpty()) return BigDecimal.ZERO;
            return new BigDecimal(v.trim());
        }
    }

    private static Integer toInteger(FieldSet fs, String field) {
        try {
            return fs.readInt(field);
        } catch (Exception e) {
            String v = fs.readString(field);
            if (v == null || v.trim().isEmpty()) return 0;
            return Integer.parseInt(v.trim());
        }
    }

    // ✅ specific helper for voucher suffix fields (COBOL PIC 9(...) often space-filled)
    private static int toIntOrZero(FieldSet fs, String field) {
        String v = fs.readString(field);
        if (v == null) return 0;
        v = v.trim();
        if (v.isEmpty()) return 0;
        // if you want to be extra strict, keep this; else remove and let parseInt throw
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Non-numeric value for " + field + ": [" + v + "]");
            }
        }
        return Integer.parseInt(v);
    }
}