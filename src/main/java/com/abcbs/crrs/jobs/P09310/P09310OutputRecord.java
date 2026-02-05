package com.abcbs.crrs.jobs.P09310;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09310OutputRecord {
	
	//batch fields
	private String crRefundType;
	private String btBatchPrefix;
	private String btBatchDate;
	private String btBatchSuffix;
	private short btBatchCnt;
	private BigDecimal btBatchAmt;
	private LocalDate btEntryDate;
	private String btPostedInd;
	
	//suspense fields
	private LocalDate crCntrlDate;
	private String crCntrlNbr;
	private BigDecimal crCntrldAmt;
	private String crCheckNbr;
	private LocalDate crCheckDate;
	private BigDecimal crCheckAmt;
	private String crReceiptType;
	private String crClaimType;
	private String crPatientLname;
	private String crPatientFname;
	private String crRemittorName;
	private String crMbrIdNbr;
	private String crReasonCode;
	private String crGlAcctNbr;
	private String crRemittorTitle;
	
	//spaces and slashes
	private String wsRefTypeSpace1;
	private String wsRefTypeSlash;
	private String wsRefTypeSpace2;
	private String wsBatchSpace1;
	private String wsBatchSpace2;
	private String wsPatNameDelim;
	
	//counters and total amounts
	private Integer totalCount;
	private BigDecimal totalAmount;
	private Integer totalPerCount;
	private BigDecimal totalPerAmount;
	private Integer totalRetCount;
	private BigDecimal totalRetAmount;
	private Integer totalUndCount;
	private BigDecimal totalUndAmount;
	private Integer totalOthCount;
	private BigDecimal totalOthAmount;
	private Integer totalOffCount;
	private BigDecimal totalOffAmount;
	private Integer totalSpoCount;
	private BigDecimal totalSpoAmount;
	private Integer totalApiCount;
	private BigDecimal totalApiAmount;
	
	//dead code
	private String groupName;

}
