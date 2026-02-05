package com.abcbs.crrs.projections;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Precedence: Implement-interface over changing reader generics
public class P09305ActivityViewImpl implements P09305ActivityView {

	private final String actUserId;
	private final String actActivity;
	private final LocalDate crCntrlDate;
	private final String crCntrlNbr;
	private final String crRefundType;
	private final LocalDate actActivityDate;
	private final LocalDateTime actTimestamp;

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

    private final String crXrefNbr;      // <— xref
    private final LocalDate actXrefDate; // <— xref

	public P09305ActivityViewImpl(String actUserId, String actActivity, LocalDate crCntrlDate, String crCntrlNbr,
			String crRefundType, LocalDate actActivityDate, LocalDateTime actTimestamp,String crXrefNbr, LocalDate actXrefDate, BigDecimal crCntrldAmt,
			String crCheckNbr, BigDecimal crCheckAmt, String crReceiptType, String crClaimType, String crPatientLname,
			String crPatientFname, String crRemittorName, String crMbrIdNbr, String crReasonCode, String crGlAcctNbr,
			String crCorp

	) {
		this.actUserId = actUserId;
		this.actActivity = actActivity;
		this.crCntrlDate = crCntrlDate;
		this.crCntrlNbr = crCntrlNbr;
		this.crRefundType = crRefundType;
		this.actActivityDate = actActivityDate;
		this.actTimestamp = actTimestamp;
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

		this.crXrefNbr = crXrefNbr;
		this.actXrefDate = actXrefDate;
	}
	
	public String getCrXrefNbr() {
		return crXrefNbr;
	}
	public LocalDate getActXrefDate() {
		return actXrefDate;
	}

	// --- Interface getters ---
	@Override
	public String getActUserId() {
		return actUserId;
	}

	@Override
	public String getActActivity() {
		return actActivity;
	}

	@Override
	public LocalDate getCrCntrlDate() {
		return crCntrlDate;
	}

	@Override
	public String getCrCntrlNbr() {
		return crCntrlNbr;
	}

	@Override
	public String getCrRefundType() {
		return crRefundType;
	}

	@Override
	public LocalDate getActActivityDate() {
		return actActivityDate;
	}

	@Override
	public LocalDateTime getActTimestamp() {
		return actTimestamp;
	}

	@Override
	public BigDecimal getCrCntrldAmt() {
		return crCntrldAmt;
	}

	@Override
	public String getCrCheckNbr() {
		return crCheckNbr;
	}

	@Override
	public BigDecimal getCrCheckAmt() {
		return crCheckAmt;
	}

	@Override
	public String getCrReceiptType() {
		return crReceiptType;
	}

	@Override
	public String getCrClaimType() {
		return crClaimType;
	}

	@Override
	public String getCrPatientLname() {
		return crPatientLname;
	}

	@Override
	public String getCrPatientFname() {
		return crPatientFname;
	}

	@Override
	public String getCrRemittorName() {
		return crRemittorName;
	}

	@Override
	public String getCrMbrIdNbr() {
		return crMbrIdNbr;
	}

	@Override
	public String getCrReasonCode() {
		return crReasonCode;
	}

	@Override
	public String getCrGlAcctNbr() {
		return crGlAcctNbr;
	}

	@Override
	public String getCrCorp() {
		return crCorp;
	}


}
