package com.abcbs.crrs.projections;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface P09310SuspenseView {

	String getBtBatchPrefix();
	String getBtBatchDate();
	String getBtBatchSuffix();
	LocalDate getCrCntrlDate();
	String getCrCntrlNbr();
	String getCrRefundType();
	BigDecimal getCrCntrldAmt();
	String getCrCheckNbr();
	LocalDate getCrCheckDate();
	BigDecimal getCrCheckAmt();
	String getCrReceiptType();
	String getCrClaimType();
	String getCrPatientLname();
	String getCrPatientFname();
	String getCrRemittorName();
	String getCrMbrIdNbr();
	String getCrReasonCode();
	String getCrGlAcctNbr();
	String getCrRemittorTitle();

}
