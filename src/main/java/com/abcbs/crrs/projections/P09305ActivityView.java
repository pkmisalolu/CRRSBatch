package com.abcbs.crrs.projections;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface P09305ActivityView {
    String getActUserId();
    String getActActivity();
    LocalDate getCrCntrlDate();
    String getCrCntrlNbr();
    String getCrRefundType();
    LocalDate getActActivityDate();
    LocalDateTime getActTimestamp();

    BigDecimal getCrCntrldAmt();
    String getCrCheckNbr();
    BigDecimal getCrCheckAmt();
    String getCrReceiptType();
    String getCrClaimType();
    String getCrPatientLname();
    String getCrPatientFname();
    String getCrRemittorName();
    String getCrMbrIdNbr();
    String getCrReasonCode();
    String getCrGlAcctNbr();
    String getCrCorp();
    String getCrXrefNbr();
    LocalDate getActXrefDate();
}

