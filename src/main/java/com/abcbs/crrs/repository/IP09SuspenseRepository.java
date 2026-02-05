package com.abcbs.crrs.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.abcbs.crrs.entity.P09Suspense;
import com.abcbs.crrs.entity.SuspensePK;
import com.abcbs.crrs.jobs.P09175.P09175SuspenseView;
import com.abcbs.crrs.projections.P09310SuspenseView;

public interface IP09SuspenseRepository extends JpaRepository<P09Suspense, SuspensePK> {

	// **************************************P09310**********************************************
	@Query("SELECT s.btBatchPrefix as btBatchPrefix, s.btBatchDate as btBatchDate, s.btBatchSuffix as btBatchSuffix, s.spId.crCntrlDate as crCntrlDate, "
			+ "       s.spId.crCntrlNbr as crCntrlNbr, s.spId.crRefundType as crRefundType, s.crCntrldAmt as crCntrldAmt, s.crCheckNbr as crCheckNbr, "
			+ "       s.crCheckDate as crCheckDate, s.crCheckAmt as crCheckAmt, s.crReceiptType as crReceiptType, s.crClaimType as crClaimType, "
			+ "       s.crPatientLname as crPatientLname, s.crPatientFname as crPatientFname, s.crRemittorName as crRemittorName, "
			+ "       s.crMbrIdNbr as crMbrIdNbr, s.crReasonCode as crReasonCode, s.crGlAcctNbr as crGlAcctNbr, s.crRemittorTitle as crRemittorTitle "
			+ "FROM P09Suspense s WHERE s.btBatchPrefix = :batchPrefix AND s.btBatchDate   = :batchDate AND s.btBatchSuffix = :batchSuffix "
			+ "  AND s.spId.crRefundType  = :refundType ORDER BY s.spId.crCntrlDate, s.spId.crCntrlNbr, s.spId.crRefundType")
	public List<P09310SuspenseView> findSuspenseSubset(@Param("batchPrefix") String batchPrefix,
			@Param("batchDate") String batchDate, @Param("batchSuffix") String batchSuffix,
			@Param("refundType") String refundType);

	@Query("""
			    SELECT s
			    FROM P09Suspense s
			    WHERE s.spId.crRefundType = :refundType
			      AND s.spId.crCntrlDate  = :cntrlDate
			      AND s.spId.crCntrlNbr   = :cntrlNbr
			""")
	P09Suspense findSuspense(@Param("refundType") String refundType, @Param("cntrlDate") LocalDate cntrlDate,
			@Param("cntrlNbr") String cntrlNbr);

	@Query("""
			SELECT
			    s.spId.crCntrlDate     AS cntrlDate,
			    s.spId.crCntrlNbr      AS cntrlNbr,
			    s.crBankAcctNbr        AS bankAcctNbr,
			    s.crCheckNbr           AS checkNbr,
			    s.crCheckDate          AS checkDate,
			    s.crCheckAmt           AS checkAmt,
			    s.crCntrldAmt          AS controlledAmt,
			    s.crReceiptType        AS receiptType,
			    s.crRemittorName       AS remittorName,
			    s.crRemittorType       AS remittorType,
			    s.crClaimType          AS claimType,
			    s.crOplInd             AS oplInd,
			    s.crPatientLname       AS patientLast,
			    s.crPatientFname       AS patientFirst,
			    s.crReasonCode         AS reasonCode,
			    s.crEobRaInd           AS eobRaInd,
			    s.crOtherCorr          AS otherCorr,
			    s.crProviderNbr        AS providerNbr,
			    s.crMbrIdNbr           AS mbrIdNbr,
			    s.crTaxIdNbr           AS taxIdNbr,
			    s.crVendorNbr          AS vendorNbr,
			    s.crOtisNbr            AS otisNbr,
			    s.crLetterDate         AS letterDate,
			    s.crAcctsRecDate       AS acctsRecDate,
			    s.crAcctsRecNbr        AS acctsRecNbr,
			    s.crCorp               AS corp,
			    s.crLocationNbr        AS locationNbr,
			    s.crLocationClerk      AS locationClerk,
			    s.crSectionCode        AS sectionCode,
			    s.crChkAddress1        AS chkAddress1,
			    s.crChkAddress2        AS chkAddress2,
			    s.crChkCity            AS chkCity,
			    s.crChkState           AS chkState,
			    s.crChkZip5            AS chkZip5,
			    s.crChkZip4            AS chkZip4,
			    s.crUserId             AS userId,
			    s.cmtCommentText       AS commentText,
			    s.crRemittorTitle      AS remittorTitle,
			    s.nationalIdNbr        AS nationalIdNbr
			FROM P09Suspense s
			WHERE
			    s.btBatchPrefix = :batchPrefix
			AND s.btBatchDate   = :batchDate
			AND s.btBatchSuffix = :batchSuffix
			AND s.spId.crRefundType = :refundType
			ORDER BY
			    s.spId.crCntrlDate,
			    s.spId.crCntrlNbr
			""")
	List<P09175SuspenseView> fetchPx02Cursor(@Param("batchPrefix") String batchPrefix,
			@Param("batchDate") String batchDate, @Param("batchSuffix") String batchSuffix,
			@Param("refundType") String refundType);

}
