package com.abcbs.crrs.jobs.P09390;

import org.springframework.batch.item.file.LineMapper;

public class P09390LineMappers {

    // -------------------------------------------
    // New Vendor Line Mapper
    // -------------------------------------------
    public static class NewVendorLineMapper implements LineMapper<P09390InputNewVendor> {

        @Override
        public P09390InputNewVendor mapLine(String line, int lineNumber) {

            P09390InputNewVendor r = new P09390InputNewVendor();

            r.setVendorNbr(line.substring(0, 9));
            r.setFiller1(line.substring(9, 11));
            r.setVendorName(line.substring(11, 47));
            r.setFiller2(line.substring(47, 80));

            return r;
        }
    }

    // -------------------------------------------
    // FEPMAN CHKS Line Mapper
    // -------------------------------------------
    public static class FepmanChksLineMapper implements LineMapper<P09390InputFepmanChks> {

        @Override
        public P09390InputFepmanChks mapLine(String line, int lineNumber) {

            P09390InputFepmanChks r = new P09390InputFepmanChks();

            r.setRefundType(line.substring(0, 3));
            r.setFiller1(line.substring(3, 4));
            r.setCntrlDate(line.substring(4, 14));
            r.setFiller2(line.substring(14, 15));
            r.setCntrlNbr(line.substring(15, 19));

            r.setInvMM(line.substring(19, 21));
            r.setInvSlash1(line.substring(21, 22));
            r.setInvDD(line.substring(22, 24));
            r.setInvSlash2(line.substring(24, 25));
            r.setInvCC(line.substring(25, 27));
            r.setInvYY(line.substring(27, 29));

            r.setProviderNbr(line.substring(29, 38));
            r.setPayeeName(line.substring(38, 74));
            r.setAddr1(line.substring(74, 110));
            r.setAddr2(line.substring(110, 146));
            r.setCity(line.substring(146, 170));
            r.setState(line.substring(170, 172));
            r.setZip1(line.substring(172, 177));
            r.setZip2(line.substring(177, 181));
            r.setInvoiceAmt(line.substring(181, 192));
            r.setDesc(line.substring(192, 232));

            r.setIdPrefix(line.substring(232, 236));
            r.setIdType(line.substring(236, 237));
            r.setCheckType(line.substring(237, 239));

            return r;
        }
    }
}
