package com.abcbs.crrs.jobs.P09365;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09365OutputManualChks {

    private String mRefundType;      // 3
    private String mFiller1;         // 1 (FILLER after refund type)
    private String mCntrlDate;       // 10
    private String mFiller2;         // 1 (FILLER after cntrl date)
    private String mCntrlNbr;        // 4
    private String mInvoiceDate;     // 10
    private String mProviderNbr;     // 9
    private String mPayeeName;       // 36
    private String mPayeeAddr1;      // 36
    private String mPayeeAddr2;      // 36
    private String mPayeeCity;       // 24
    private String mPayeeSt;         // 2
    private String mPayeeZip1;       // 5
    private String mPayeeZip2;       // 4
    private String mInvoiceAmt;      // 11
    private String mDesc;            // 40
    private String mPayeeIdPrefix;   // 4  ('CRRS')
    private String mPayeeIdType;     // 1
    private String mCheckType;       // 2
}
