package com.abcbs.crrs.jobs.P09365;

import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.Range;

public final class P09365LineMapper {

    private P09365LineMapper() {}

    public static LineMapper<P09365InputGpIntrface> gpIntrfaceLineMapper() {
        DefaultLineMapper<P09365InputGpIntrface> lm = new DefaultLineMapper<>();
        lm.setLineTokenizer(tokenizer275());
        lm.setFieldSetMapper(mapper());
        return lm;
    }

    private static FixedLengthTokenizer tokenizer275() {
        FixedLengthTokenizer t = new FixedLengthTokenizer();
        t.setStrict(false);

        t.setNames(
                "oRecdCode",
                "oFiller1",
                "oVoucherNbr",
                "oInvoiceDate",
                "oFiller2",
                "oTaxIdNbr",
                "oPayeeIdType",
                "oProviderNbr",
                "oPayeeName",
                "oPayeeAddr1",
                "oPayeeAddr2",
                "oPayeeCity",
                "oPayeeSt",
                "oPayeeZip1",
                "oPayeeZip2",
                "oFiller3",
                "oChkAmt",
                "oNegInd",
                "oChkNbr",
                "oChkDate",
                "oChkType",
                "oBankAcctNbr",
                "oNationalId",
                "oRefundType",
                "oCntrlDate",
                "oCntrlNbr",
                "oMemberId",
                "oLname",
                "oFname"
        );

        // 1-based inclusive ranges totaling 275 chars
        t.setColumns(
                r(1, 1),
                r(2, 2),
                r(3, 8),
                r(9, 18),
                r(19, 19),
                r(20, 28),
                r(29, 29),
                r(30, 38),
                r(39, 68),
                r(69, 103),
                r(104, 138),
                r(139, 158),
                r(159, 160),
                r(161, 165),
                r(166, 169),
                r(170, 170),
                r(171, 181),
                r(182, 182),
                r(183, 190),
                r(191, 200),
                r(201, 202),
                r(203, 210),
                r(211, 220),
                r(221, 223),
                r(224, 233),
                r(234, 237),
                r(238, 249),
                r(250, 264),
                r(265, 275)
        );

        return t;
    }

    private static FieldSetMapper<P09365InputGpIntrface> mapper() {
        return new FieldSetMapper<>() {
            @Override
            public P09365InputGpIntrface mapFieldSet(FieldSet fs) {
            	P09365InputGpIntrface r = new P09365InputGpIntrface();
                r.setORecdCode(fs.readString("oRecdCode"));
                r.setOFiller1(fs.readString("oFiller1"));
                r.setOVoucherNbr(fs.readString("oVoucherNbr"));
                r.setOInvoiceDate(fs.readString("oInvoiceDate"));
                r.setOFiller2(fs.readString("oFiller2"));
                r.setOTaxIdNbr(fs.readString("oTaxIdNbr"));
                r.setOPayeeIdType(fs.readString("oPayeeIdType"));
                r.setOProviderNbr(fs.readString("oProviderNbr"));
                r.setOPayeeName(fs.readString("oPayeeName"));
                r.setOPayeeAddr1(fs.readString("oPayeeAddr1"));
                r.setOPayeeAddr2(fs.readString("oPayeeAddr2"));
                r.setOPayeeCity(fs.readString("oPayeeCity"));
                r.setOPayeeSt(fs.readString("oPayeeSt"));
                r.setOPayeeZip1(fs.readString("oPayeeZip1"));
                r.setOPayeeZip2(fs.readString("oPayeeZip2"));
                r.setOFiller3(fs.readString("oFiller3"));
                r.setOChkAmt(fs.readString("oChkAmt"));
                r.setONegInd(fs.readString("oNegInd"));
                r.setOChkNbr(fs.readString("oChkNbr"));
                r.setOChkDate(fs.readString("oChkDate"));
                r.setOChkType(fs.readString("oChkType"));
                r.setOBankAcctNbr(fs.readString("oBankAcctNbr"));
                r.setONationalId(fs.readString("oNationalId"));
                r.setORefundType(fs.readString("oRefundType"));
                r.setOCntrlDate(fs.readString("oCntrlDate"));
                r.setOCntrlNbr(fs.readString("oCntrlNbr"));
                r.setOMemberId(fs.readString("oMemberId"));
                r.setOLname(fs.readString("oLname"));
                r.setOFname(fs.readString("oFname"));
                return r;
            }
        };
    }

    private static Range r(int min, int max) {
        return new Range(min, max);
    }
}