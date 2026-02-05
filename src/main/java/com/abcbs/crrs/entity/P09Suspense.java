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
@Table(name="P09_SUSPENSE")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09Suspense {
	
	@EmbeddedId
	private SuspensePK spId;

    @Column(name = "BT_BATCH_PREFIX", length = 3, nullable = false)
    private String btBatchPrefix;

    @Column(name = "BT_BATCH_DATE", length = 6, nullable = false)
    private String btBatchDate;

    @Column(name = "BT_BATCH_SUFFIX", length = 2, nullable = false)
    private String btBatchSuffix;

    @Column(name = "CR_BANK_ACCT_NBR", length = 35, nullable = false)
    private String crBankAcctNbr;

    @Column(name = "CR_CHECK_NBR", length = 8, nullable = false)
    private String crCheckNbr;

    @Column(name = "CR_CHECK_DATE")
    private LocalDate crCheckDate;

    //[CR_CHECK_AMT] [numeric](11, 2) NOT NULL
    @Column(name = "CR_CHECK_AMT", precision = 11, scale = 2, nullable = false)
    private BigDecimal crCheckAmt;

    //[CR_CNTRLD_AMT] [numeric](11, 2) NOT NULL
    @Column(name = "CR_CNTRLD_AMT", precision = 11, scale = 2, nullable = false)
    private BigDecimal crCntrldAmt;

    @Column(name = "CR_RECEIPT_TYPE", length = 2, nullable = false)
    private String crReceiptType;

    @Column(name = "CR_REMITTOR_NAME", length = 36, nullable = false)
    private String crRemittorName;

    @Column(name = "CR_REMITTOR_TYPE", length = 1, nullable = false)
    private String crRemittorType;

    @Column(name = "CR_CLAIM_TYPE", length = 4, nullable = false)
    private String crClaimType;

    @Column(name = "CR_OPL_IND", length = 1, nullable = false)
    private String crOplInd;

    @Column(name = "CR_PATIENT_LNAME", length = 15, nullable = false)
    private String crPatientLname;

    @Column(name = "CR_PATIENT_FNAME", length = 11, nullable = false)
    private String crPatientFname;

    @Column(name = "CR_REASON_CODE", length = 4, nullable = false)
    private String crReasonCode;

    @Column(name = "CR_EOB_RA_IND", length = 1, nullable = false)
    private String crEobRaInd;

    @Column(name = "CR_OTHER_CORR", length = 21, nullable = false)
    private String crOtherCorr;

    @Column(name = "CR_PROVIDER_NBR", length = 9, nullable = false)
    private String crProviderNbr;

    @Column(name = "CR_MBR_ID_NBR", length = 12, nullable = false)
    private String crMbrIdNbr;

    @Column(name = "CR_TAX_ID_NBR", length = 9, nullable = false)
    private String crTaxIdNbr;

    @Column(name = "CR_VENDOR_NBR", length = 9, nullable = false)
    private String crVendorNbr;

    @Column(name = "CR_OTIS_NBR", length = 13, nullable = false)
    private String crOtisNbr;

    @Column(name = "CR_LETTER_DATE")
    private LocalDate crLetterDate;

    @Column(name = "CR_ACCTS_REC_DATE")
    private LocalDate crAcctsRecDate;

    @Column(name = "CR_ACCTS_REC_NBR", length = 9, nullable = false)
    private String crAcctsRecNbr;

    @Column(name = "CR_GL_ACCT_NBR", length = 12, nullable = false)
    private String crGlAcctNbr;

    @Column(name = "CR_CORP", length = 2, nullable = false)
    private String crCorp;

    @Column(name = "CR_LOCATION_NBR", length = 3, nullable = false)
    private String crLocationNbr;

    @Column(name = "CR_LOCATION_CLERK", length = 4, nullable = false)
    private String crLocationClerk;

    @Column(name = "CR_SECTION_CODE", length = 2, nullable = false)
    private String crSectionCode;

    @Column(name = "CR_CHK_ADDRESS_1", length = 36, nullable = false)
    private String crChkAddress1;

    @Column(name = "CR_CHK_ADDRESS_2", length = 36, nullable = false)
    private String crChkAddress2;

    @Column(name = "CR_CHK_CITY", length = 24, nullable = false)
    private String crChkCity;

    @Column(name = "CR_CHK_STATE", length = 2, nullable = false)
    private String crChkState;

    @Column(name = "CR_CHK_ZIP_5", length = 5, nullable = false)
    private String crChkZip5;

    @Column(name = "CR_CHK_ZIP_4", length = 4, nullable = false)
    private String crChkZip4;

    @Column(name = "CR_USER_ID", length = 7, nullable = false)
    private String crUserId;

    @Column(name = "CMT_COMMENT_TEXT", length = 65, nullable = false)
    private String cmtCommentText;

    @Column(name = "CR_GRP_NAME", length = 15, nullable = false)
    private String crGrpName;

    @Column(name = "CR_REMITTOR_TITLE", length = 3, nullable = false)
    private String crRemittorTitle;

    @Column(name = "NATIONAL_ID_NBR", length = 10, nullable = false)
    private String nationalIdNbr;


}
