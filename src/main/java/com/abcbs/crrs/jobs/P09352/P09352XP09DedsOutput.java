package com.abcbs.crrs.jobs.P09352;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09352XP09DedsOutput implements Serializable {
    private static final long serialVersionUID = 1L;

    private BigDecimal glJulianDate;         // S9(5) COMP-3
    private BigDecimal glTimeHhmmsss;        // S9(7) COMP-3

    private String glFiller1;                // X(5)

    private String glRecordId;               // X(2)
    private String glRefundType;             // X(3)
    private String glControlDate;            // X(10)
    private String glControlNbr;             // X(4)
    private String glReceiptType;            // X(2)

    private String glBankAcctNbr;            // X(35)
    private String glAcctNbr;                // X(12)
    private String glActCode;                // X(3)
    private BigDecimal glActAmt;             // S9(9)V99 COMP-3
    private String glActDate;                // X(10)

    private String glReportMo;               // X(2)
    private String glReportYr;               // X(2)

    private String glPatientLn;              // X(15)
    private String glPatientFn;              // X(11)
    private String glMemberId;               // X(12)

    private String glXrefType;               // X(2)
    private String glXrefClaimNbr;           // X(20)
    private String glXrefDate;               // X(10)

    private BigDecimal glCashRecBal;         // S9(9)V99 COMP-3
    private String glCorp;                   // X(2)

    private String glFiller2;                // X(8)
}