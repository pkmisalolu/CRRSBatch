package com.abcbs.crrs.jobs.P09345;
import java.time.LocalDate;

//---------------------------------------------------------------------
// PX02 equivalent: fetch carryover cash receipts for a given corp & refund-type up to cutoff date.
// Projection only returns fields needed for P09345 fixed-width reporting (matches COBOL SELECT list).
// ORDER BY maps COBOL's ORDER BY 02,03 -> crCntrlDate, crCntrlNbr
// ---------------------------------------------------------------------

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Projection used by P09345 PX02 cursor (carryover report).
 * Getter names MUST match JPQL aliases exactly.
 */
public interface CarryoverView {

	 /* === CONTROL IDENTIFIERS === */
    String getRefundType();          // CR_REFUND_TYPE
    LocalDate getCntrlDate();         // CR_CNTRL_DATE
    Long getControlNbr();             // CR_CNTRL_NBR

    /* === AMOUNTS === */
    BigDecimal getControlAmt();       // CR_CNTRLD_AMT
    BigDecimal getReceiptBal();       // CR_RECEIPT_BAL
    BigDecimal getCheckAmt();          // CR_CHECK_AMT

    /* === CHECK INFO === */
    String getCheckNbr();             // CR_CHECK_NBR
    LocalDate getCheckDate();          // CR_CHECK_DATE

    /* === PARTY INFO === */
    String getRemittor();             // CR_REMITTOR_NAME
    String getPatientLast();          // CR_PATIENT_LNAME
    String getPatientFirst();         // CR_PATIENT_FNAME
    String getPatientId();            // CR_MBR_ID_NBR

    /* === STATUS / REASON === */
    String getReasonCode();           // CR_REASON_CODE
    LocalDate getLetterDate();        // CR_LETTER_DATE

    /* === CORPORATE === */
    String getCorp();                 // CR_CORP
}
