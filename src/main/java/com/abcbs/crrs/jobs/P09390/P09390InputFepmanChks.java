package com.abcbs.crrs.jobs.P09390;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09390InputFepmanChks {
    private String refundType;
    private String filler1;
    private String cntrlDate;
    private String filler2;
    private String cntrlNbr;

    private String invMM;
    private String invSlash1;
    private String invDD;
    private String invSlash2;
    private String invCC;
    private String invYY;

    private String providerNbr;
    private String payeeName;
    private String addr1;
    private String addr2;
    private String city;
    private String state;
    private String zip1;
    private String zip2;
    private String invoiceAmt;
    private String desc;

    private String idPrefix;
    private String idType;
    private String checkType;

}
