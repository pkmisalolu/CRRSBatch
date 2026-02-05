package com.abcbs.crrs.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name="BANK_RECON")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BankRecon {
	
	@EmbeddedId
	private BankReconPK brId;

	@Column(name = "FILE_INDICATOR", length = 1, nullable = false)
	private String fileIndicator;

	@Column(name = "CHECK_AMOUNT", precision = 11, scale = 2, nullable = false)
	private BigDecimal checkAmount;

	@Column(name = "PAYEE_ID_NBR", length = 9, nullable = false)
	private String payeeIdNbr;

	@Column(name = "PAYEE_NAME", length = 36, nullable = false)
	private String payeeName;

	@Column(name = "PAYEE_ADDRESS_1", length = 36, nullable = false)
	private String payeeAddress1;

	@Column(name = "PAYEE_ADDRESS_2", length = 36, nullable = false)
	private String payeeAddress2;

	@Column(name = "PAYEE_CITY", length = 24, nullable = false)
	private String payeeCity;

	@Column(name = "PAYEE_STATE", length = 2, nullable = false)
	private String payeeState;

	@Column(name = "PAYEE_ZIPCODE_1", length = 5, nullable = false)
	private String payeeZipcode1;

	@Column(name = "PAYEE_ZIPCODE_2", length = 4, nullable = false)
	private String payeeZipcode2;

	@Column(name = "CHECK_ORIGIN", length = 1, nullable = false)
	private String checkOrigin;

	@Column(name = "CHECK_TYPE", length = 2, nullable = false)
	private String checkType;

	@Column(name = "CHECK_STATUS_CODE", length = 2, nullable = false)
	private String checkStatusCode;

	@Column(name = "CHECK_STATUS_DATE", nullable = false)
	private LocalDate checkStatusDate;

	@Column(name = "STATUS_SOURCE_CODE", length = 2, nullable = false)
	private String statusSourceCode;

	@Column(name = "OS_DAILY", length = 1, nullable = false)
	private String osDaily;

	@Column(name = "ST_DAILY", length = 1, nullable = false)
	private String stDaily;

	@Column(name = "SD_DAILY", length = 1)
	private String sdDaily;

	@Column(name = "TS_DAILY", length = 1)
	private String tsDaily;

	@Column(name = "STALE_DATE")
	private LocalDate staleDate;

	@Column(name = "STALE_ORIGIN", length = 1)
	private String staleOrigin;

	@Column(name = "TRANSFER_DATE")
	private LocalDate transferDate;

	@Column(name = "TRANSFER_ORIGIN", length = 1)
	private String transferOrigin;

	@Column(name = "REISSUE_ACCT_NBR", length = 35)
	private String reissueAcctNbr;

	@Column(name = "REISSUE_CHECK_NBR", length = 8)
	private String reissueCheckNbr;

	@Column(name = "REISSUE_CHECK_DATE")
	private LocalDate reissueCheckDate;

	@Column(name = "REISSUE_CHECK_TYPE", length = 2)
	private String reissueCheckType;

	@Column(name = "REPORT_DATE", nullable = false)
	private LocalDate reportDate;

	@Column(name = "INITIAL_ACCT_NBR", length = 35)
	private String initialAcctNbr;

	@Column(name = "INITIAL_CHECK_NBR", length = 8)
	private String initialCheckNbr;

	@Column(name = "INITIAL_CHECK_DATE")
	private LocalDate initialCheckDate;

	@Column(name = "INITIAL_CHECK_TYPE", length = 2)
	private String initialCheckType;

	@Column(name = "PPA_DATE")
	private LocalDate ppaDate;

	@Column(name = "PAYEE_ID_TYPE", length = 1)
	private String payeeIdType;

	@Column(name = "TAX_ID_NBR", length = 9)
	private String taxIdNbr;

	//[CHECK_INTEREST_AMT] [numeric](9, 2) NOT NULL
	@Column(name = "CHECK_INTEREST_AMT", precision = 9, scale = 2, nullable = false)
	private BigDecimal checkInterestAmt;

	@Column(name = "EFF_ENT_DATE", nullable = false)
	private LocalDate effEntDate;

	@Column(name = "ROUTE_NBR", length = 12, nullable = false)
	private String routeNbr;

	@Column(name = "PROV_ACCT_NBR", length = 35, nullable = false)
	private String provAcctNbr;

	@Column(name = "PROV_ACCT_TYPE", length = 3, nullable = false)
	private String provAcctType;

	@Column(name = "PAYEE_TITLE", length = 3, nullable = false)
	private String payeeTitle;
	
	@Column(name = "NATIONAL_ID_NBR", length = 10, nullable = false)
	private String nationalIdNbr;

}
