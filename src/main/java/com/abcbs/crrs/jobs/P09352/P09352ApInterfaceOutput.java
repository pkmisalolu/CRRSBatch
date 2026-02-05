package com.abcbs.crrs.jobs.P09352;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09352ApInterfaceOutput implements Serializable {
    private static final long serialVersionUID = 1L;

    private String oRecdCode;          // X
    private String filler1;            // X

    private String oVoucherNbr;        // X(6)

    // Invoice date fields
    private String oInvoiceMm;         // X(2)
    private String oSlash1;            // X  value '/'
    private String oInvoiceDd;         // X(2)
    private String oSlash2;            // X  value '/'
    private String oInvoiceCc;         // X(2)
    private String oInvoiceYy;         // X(2)

    private String filler2;            // X

    private String oTaxIdNbr;          // X(9)
    private String oPayeeIdType;       // X
    private String oProviderNbr;       // X(9)

    private String oPayeeName;         // X(30)
    private String oPayeeAddr1;        // X(35)
    private String oPayeeAddr2;        // X(35)
    private String oPayeeCity;         // X(20)
    private String oPayeeSt;           // X(2)
    private String oPayeeZip1;         // X(5)
    private String oPayeeZip2;         // X(4)

    private String filler3;            // X

    private BigDecimal oChkAmt;        // 9(9)V99
    private String oNegInd;            // X
    private String oChkNbr;            // X(8)
    private String oChkDate;           // X(10)
    private String oChkType;           // X(2)
    private String oBankAcctNbr;       // X(8)

    private String oNationalId;        // X(10)
    private String oRefundType;        // X(3)
    private String oCntrlDate;         // X(10)
    private String oCntrlNbr;          // X(4)

    private String oMemberId;          // X(12)
    private String oLname;             // X(15)
    private String oFname;             // X(11)

    private String filler4;            // X  <-- missing field added
}