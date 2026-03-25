package com.abcbs.crrs.projections;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class P09305ActivityViewImpl implements P09305ActivityView {

    // ===== Activity (A) =====
    private final String actUserId;
    private final String actActivity;
    private final LocalDate crCntrlDate;
    private final String crCntrlNbr;
    private final String crRefundType;
    private final LocalDate actActivityDate;
    private final LocalDateTime actTimestamp;

    private final String crXrefNbr;
    private final LocalDate actXrefDate;
    private final BigDecimal actActivityAmt;
    private final BigDecimal actWorkingBal;

    // ===== CashReceipt (B) =====
    private final BigDecimal crCntrldAmt;
    private final String crCheckNbr;
    private final BigDecimal crCheckAmt;
    private final String crReceiptType;
    private final String crClaimType;
    private final String crPatientLname;
    private final String crPatientFname;
    private final String crRemittorName;
    private final String crMbrIdNbr;
    private final String crReasonCode;
    private final String crGlAcctNbr;
    private final String crCorp;
    private final BigDecimal crReceiptBal;

    // ===== Constructor (MUST MATCH JPQL ORDER EXACTLY) =====
    public P09305ActivityViewImpl(
            String actUserId,
            String actActivity,
            LocalDate crCntrlDate,
            String crCntrlNbr,
            String crRefundType,
            LocalDate actActivityDate,
            LocalDateTime actTimestamp,

            String crXrefNbr,
            LocalDate actXrefDate,

            BigDecimal actActivityAmt,
            BigDecimal actWorkingBal,

            BigDecimal crCntrldAmt,
            String crCheckNbr,
            BigDecimal crCheckAmt,
            String crReceiptType,
            String crClaimType,
            String crPatientLname,
            String crPatientFname,
            String crRemittorName,
            String crMbrIdNbr,
            String crReasonCode,
            String crGlAcctNbr,
            String crCorp,
            BigDecimal crReceiptBal
    ) {
        this.actUserId = actUserId;
        this.actActivity = actActivity;
        this.crCntrlDate = crCntrlDate;
        this.crCntrlNbr = crCntrlNbr;
        this.crRefundType = crRefundType;
        this.actActivityDate = actActivityDate;
        this.actTimestamp = actTimestamp;

        this.crXrefNbr = crXrefNbr;
        this.actXrefDate = actXrefDate;

        this.actActivityAmt = actActivityAmt;
        this.actWorkingBal = actWorkingBal;

        this.crCntrldAmt = crCntrldAmt;
        this.crCheckNbr = crCheckNbr;
        this.crCheckAmt = crCheckAmt;
        this.crReceiptType = crReceiptType;
        this.crClaimType = crClaimType;
        this.crPatientLname = crPatientLname;
        this.crPatientFname = crPatientFname;
        this.crRemittorName = crRemittorName;
        this.crMbrIdNbr = crMbrIdNbr;
        this.crReasonCode = crReasonCode;
        this.crGlAcctNbr = crGlAcctNbr;
        this.crCorp = crCorp;
        this.crReceiptBal = crReceiptBal;
    }

    // ===== Getters =====

    public String getActUserId() { return actUserId; }
    public String getActActivity() { return actActivity; }
    public LocalDate getCrCntrlDate() { return crCntrlDate; }
    public String getCrCntrlNbr() { return crCntrlNbr; }
    public String getCrRefundType() { return crRefundType; }
    public LocalDate getActActivityDate() { return actActivityDate; }
    public LocalDateTime getActTimestamp() { return actTimestamp; }

    public String getCrXrefNbr() { return crXrefNbr; }
    public LocalDate getActXrefDate() { return actXrefDate; }

    public BigDecimal getActActivityAmt() { return actActivityAmt; }
    public BigDecimal getActWorkingBal() { return actWorkingBal; }

    public BigDecimal getCrCntrldAmt() { return crCntrldAmt; }
    public String getCrCheckNbr() { return crCheckNbr; }
    public BigDecimal getCrCheckAmt() { return crCheckAmt; }
    public String getCrReceiptType() { return crReceiptType; }
    public String getCrClaimType() { return crClaimType; }
    public String getCrPatientLname() { return crPatientLname; }
    public String getCrPatientFname() { return crPatientFname; }
    public String getCrRemittorName() { return crRemittorName; }
    public String getCrMbrIdNbr() { return crMbrIdNbr; }
    public String getCrReasonCode() { return crReasonCode; }
    public String getCrGlAcctNbr() { return crGlAcctNbr; }
    public String getCrCorp() { return crCorp; }
    public BigDecimal getCrReceiptBal() { return crReceiptBal; }
}