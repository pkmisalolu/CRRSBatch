/**
 * Developer: Dharani Parimella
 * Description: This class handles P09CashReceipt entity with composite primary key
 */
package com.abcbs.crrs.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Entity
@Table(name = "P09_CASH_RECEIPT")
@Data
//@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
public class P09CashReceipt {
	
	@EmbeddedId
	private CashReceiptPK crId;
	
	@Column(nullable = false)
	private LocalDate crReceivedDate;
	
	private LocalDate crDepositDate;
	
	@Column(nullable = false)
	private LocalDate crEntryDate;
	
	@Column(nullable = false)
	private LocalDate crStatusDate;
	
	@Column(length = 6, nullable = false)
	private String crStatusText;
	
	@Column(length = 3, nullable = false)
	private String crPendFinAct;
	
	@Column(length = 35, nullable = false)
	private String crBankAcctNbr;
	
	@Column(length = 8, nullable = false)
	private String crCheckNbr;
	
	private LocalDate crCheckDate;
	
	@Column(precision = 11, scale = 2, nullable = false)
	private BigDecimal crCheckAmt;
	
	@Column(precision = 11, scale = 2, nullable = false)
	private BigDecimal crCntrldAmt;
	
	@Column(precision = 11, scale = 2, nullable = false)
	private BigDecimal crReceiptBal;
	
	@Column(length = 2, nullable = false)
	private String crReceiptType;
	
	@Column(length = 36, nullable = false)
	private String crRemittorName;
	
	@Column(length = 1, nullable = false)
	private String crRemittorType;
	
	@Column(length = 4, nullable = false)
	private String crClaimType;
	
	@Column(length = 1, nullable = false)
	private String crOplInd;
	
	@Column(length = 15, nullable = false)
	private String crPatientLname;
	
	@Column(length = 11, nullable = false)
	private String crPatientFname;
	
	@Column(length = 4, nullable = false)
	private String crReasonCode;
	
	@Column(length = 1, nullable = false)
	private String crEobRaInd;
	
	@Column(length = 21, nullable = false)
	private String crOtherCorr;
	
	@Column(length = 9, nullable = false)
	private String crProviderNbr;
	
	@Column(length = 12, nullable = false)
	private String crMbrIdNbr;
	
	@Column(length = 9, nullable = false)
	private String crTaxIdNbr;
	
	@Column(length = 9, nullable = false)
	private String crVendorNbr;
	
	@Column(length = 13, nullable = false)
	private String crOtisNbr;
	
	private LocalDate crLetterDate;
	
	private LocalDate crAcctsRecDate;
	
	@Column(length = 9, nullable = false)
	private String crAcctsRecNbr;
	
	@Column(length = 12, nullable = false)
	private String crGlAcctNbr;
	
	@Column(length = 2, nullable = false)
	private String crCorp;
	
	@Column(length = 3, nullable = false)
	private String crLocationNbr;
	
	@Column(length = 4, nullable = false)
	private String crLocationClerk;
	
	@Column(nullable = false)
	private LocalDate crLocationDate;
	
	private String crSectionCode;
	
	@Column(name="CR_CHK_ADDRESS_1", nullable = false, length = 36)
	private String crChkAddress1;
	
	@Column(name="CR_CHK_ADDRESS_2", nullable = false, length = 36)
	private String crChkAddress2;
	
	@Column(length = 24, nullable = false)
	private String crChkCity;
	
	@Column(length = 2, nullable = false)
	private String crChkState;
	
	@Column(name="CR_CHK_ZIP_5", nullable = false, length = 5)
	private String crChkZip5;
	
	@Column(name="CR_CHK_ZIP_4", nullable = false, length = 4)
	private String crChkZip4;
	
	@Column(length = 1, nullable = false)
	private String crRemDailyInd;
	
	@Column(length = 1, nullable = false)
	private String crRemIdType;
	
	@Column(length = 9, nullable = false)
	private String crRemIdNbr;
	
	@Column(length = 9, nullable = false)
	private String crRemTaxIdNbr;
	
	@Column(length = 36, nullable = false)
	private String crRemAddressee;
	
	@Column(name="CR_REM_ADDRESS_1",length = 36, nullable = false)
	private String crRemAddress1;
	
	@Column(name="CR_REM_ADDRESS_2",length = 36, nullable = false)
	private String crRemAddress2;
	
	@Column(length = 24, nullable = false)
	private String crRemCity;
	
	@Column(length = 2, nullable = false)
	private String crRemState;
	
	@Column(name="CR_REM_ZIP_5",length = 5, nullable = false)
	private String crRemZip5;
	
	@Column(name="CR_REM_ZIP_4",length = 4, nullable = false)
	private String crRemZip4;
	
	@Column(length = 7, nullable = false)
	private String crUserId;
	
	@Column(length = 15, nullable = false)
	private String crGrpName;
	
	@Column(length = 3, nullable = false)
	private String crRemittorTitle;
	
	@Column(length = 10, nullable = false)
	private String nationalIdNbr;
	
	@Column(length = 10, nullable = false)
	private String crRemNationalId;
	
	@Column(length = 1, nullable = false)
	private String crExtractedInd;

}
