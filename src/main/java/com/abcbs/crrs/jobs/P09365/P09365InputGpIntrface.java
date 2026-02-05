package com.abcbs.crrs.jobs.P09365;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09365InputGpIntrface {

    private String oRecdCode;     // 1
    private String oFiller1;      // 1 (FILLER)
    private String oVoucherNbr;   // 6
    private String oInvoiceDate;  // 10 (MM/DD/CCYY as text)
    private String oFiller2;      // 1 (FILLER)
    private String oTaxIdNbr;     // 9
    private String oPayeeIdType;  // 1
    private String oProviderNbr;  // 9
    private String oPayeeName;    // 30
    private String oPayeeAddr1;   // 35
    private String oPayeeAddr2;   // 35
    private String oPayeeCity;    // 20
    private String oPayeeSt;      // 2
    private String oPayeeZip1;    // 5
    private String oPayeeZip2;    // 4
    private String oFiller3;      // 1 (FILLER)
    private String oChkAmt;       // 11 (9(9)V99 stored as flat text digits)
    private String oNegInd;       // 1
    private String oChkNbr;       // 8
    private String oChkDate;      // 10
    private String oChkType;      // 2
    private String oBankAcctNbr;  // 8
    private String oNationalId;   // 10
    private String oRefundType;   // 3
    private String oCntrlDate;    // 10
    private String oCntrlNbr;     // 4
    private String oMemberId;     // 12
    private String oLname;        // 15
    private String oFname;        // 11
}
