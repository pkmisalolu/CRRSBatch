package com.abcbs.crrs.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.abcbs.crrs.entity.CashReceiptPK;
import com.abcbs.crrs.entity.P09CashReceipt;
import com.abcbs.crrs.jobs.P09325.P09325RoutingView;
import com.abcbs.crrs.jobs.P09345.CarryoverView;
import com.abcbs.crrs.jobs.P09375.DailyRemittanceView;
import com.abcbs.crrs.jobs.P09376.P09376DailyRemittanceView;

import jakarta.transaction.Transactional;

public interface IP09CashReceiptRepository extends JpaRepository<P09CashReceipt, CashReceiptPK> {
	@Modifying
	@Transactional
	@Query("UPDATE P09CashReceipt c SET c.crRemDailyInd = ' '")
	int clearDailyRemittanceFlag();

	@Query("SELECT c.crId.crRefundType AS crRefundType, c.crId.crCntrlDate AS crCntrlDate, c.crId.crCntrlNbr AS crCntrlNbr, "
			+ "c.crRemDailyInd AS crRemDailyInd, c.crRemIdType AS crRemIdType, c.crRemIdNbr AS crRemIdNbr, c.crRemittorName AS crRemittorName, "
			+ "c.crRemAddressee AS crRemAddressee, c.crRemAddress1 AS crRemAddress1, c.crRemAddress2 AS crRemAddress2, "
			+ "c.crRemCity AS crRemCity, c.crRemState AS crRemState, c.crRemZip5 AS crRemZip5, c.crRemZip4 AS crRemZip4, "
			+ "c.crChkAddress1 AS crChkAddress1, c.crChkAddress2 AS crChkAddress2, c.crChkCity AS crChkCity, c.crChkState AS crChkState, "
			+ "c.crChkZip5 AS crChkZip5, c.crChkZip4 AS crChkZip4, c.crUserId AS crUserId " + "FROM P09CashReceipt c "
			+ "WHERE CONCAT(FUNCTION('FORMAT', c.crId.crCntrlDate, 'yyyyMMdd'), c.crId.crCntrlNbr, c.crId.crRefundType) > :checkpointKey "
			+ "AND c.crRemDailyInd = 'Y' "
			+ "ORDER BY c.crUserId, c.crId.crRefundType, c.crId.crCntrlDate, c.crId.crCntrlNbr")
	List<DailyRemittanceView> findDailyRemittances(@Param("checkpointKey") String checkpointKey);

	@Query("""
			    SELECT r
			    FROM P09CashReceipt r
			    WHERE r.crId.crRefundType = :refundType
			      AND (r.crStatusText = 'OPEN' OR r.crStatusText = 'PENDED')
			      AND r.crId.crCntrlDate <= :toDate
			""")
	List<P09CashReceipt> findOpenOrPendedReceipts(@Param("refundType") String refundType,
			@Param("toDate") LocalDate toDate);

	@Query("""
			    SELECT r
			    FROM P09CashReceipt r
			    WHERE r.crCorp = :corpNo
			      AND r.crId.crRefundType = :refundType
			      AND (r.crStatusText = 'OPEN' OR r.crStatusText = 'PENDED')
			      AND r.crId.crCntrlDate <= :toDate
			""")
	List<P09CashReceipt> findOpenOrPendedReceiptswithCorp(@Param("corpNo") String corpNo,
			@Param("refundType") String refundType, @Param("toDate") LocalDate toDate);

	// PX02 - fetch pending cash receipts (COBOL WHERE conditions)
	@Query("""
			    SELECT c
			    FROM P09CashReceipt c
			    WHERE c.crId.crRefundType = :refundType
			      AND c.crStatusDate <= :statusDate
			      AND c.crStatusText = 'PENDED'
			      AND (c.crPendFinAct = 'PRR' OR c.crPendFinAct = 'FRR')
			      AND (c.crClaimType = 'FEP ' OR c.crClaimType = 'FEPP')
			      AND c.crCorp = :corp
			    ORDER BY c.crId.crRefundType, c.crId.crCntrlDate, c.crId.crCntrlNbr
			""")
	List<P09CashReceipt> findPendingCashReceipts(String refundType, LocalDate statusDate, String corp);

	// DB-MODIFY V_P09_CASH_RECEIPT (COBOL UPDATE block)
	@Modifying
	@Query("""
			    UPDATE P09CashReceipt c
			    SET c.crReceiptBal = :receiptBal,
			        c.crStatusText = :statusText,
			        c.crStatusDate = :statusDate,
			        c.crPendFinAct = :pendFinAct,
			        c.crRemDailyInd = :remDailyInd,
			        c.crRemIdType = :remIdType,
			        c.crRemIdNbr = :remIdNbr,
			        c.crRemNationalId = :remNationalId,
			        c.crRemTaxIdNbr = :remTaxIdNbr,
			        c.crRemAddressee = :remAddressee,
			        c.crRemAddress1 = :remAddress1,
			        c.crRemAddress2 = :remAddress2,
			        c.crRemCity = :remCity,
			        c.crRemState = :remState,
			        c.crRemZip5 = :remZip5,
			        c.crRemZip4 = :remZip4
			    WHERE c.crId.crRefundType = :refundType
			      AND c.crId.crCntrlDate = :cntrlDate
			      AND c.crId.crCntrlNbr = :cntrlNbr
			""")
	int updateCashReceiptRecord(BigDecimal receiptBal, String statusText, LocalDate statusDate, String pendFinAct,
			String remDailyInd, String remIdType, String remIdNbr, String remNationalId, String remTaxIdNbr,
			String remAddressee, String remAddress1, String remAddress2, String remCity, String remState,
			String remZip5, String remZip4, String refundType, LocalDate cntrlDate, String cntrlNbr);

	/**
	 * Stream RET rows for a given receipt type up to the cutoff date. Caller must
	 * ensure the returned Stream is closed (try-with-resources) and a transactional
	 * context remains open while consuming the stream.
	 */
	@Transactional
	@Query("SELECT c " + "FROM P09CashReceipt c " + "WHERE c.crId.crRefundType = 'RET' "
			+ " AND c.crReceiptType = :rType "
			+ " AND (TRIM(c.crStatusText) = 'OPEN' OR TRIM(c.crStatusText) = 'PENDED') "
			+ " AND c.crId.crCntrlDate <= :cutoff "
			+ "ORDER BY c.crId.crRefundType, c.crReceiptType, c.crId.crCntrlDate, c.crId.crCntrlNbr")
	Stream<P09CashReceipt> streamByReceiptTypeNative(@Param("rType") String rType, @Param("cutoff") LocalDate cutoff);

	/**
	 * Stream rows for a given refund type (UND/OFF/SPO) up to the cutoff date.
	 */
	@Transactional
	@Query("SELECT c " + "FROM P09CashReceipt c " + "WHERE c.crId.crRefundType = :refundType "
			+ " AND (TRIM(c.crStatusText) = 'OPEN' OR TRIM(c.crStatusText) = 'PENDED') "
			+ " AND c.crId.crCntrlDate <= :cutoff "
			+ "ORDER BY c.crId.crRefundType, c.crReceiptType, c.crId.crCntrlDate, c.crId.crCntrlNbr")

	Stream<P09CashReceipt> streamByRefundTypeNative(@Param("refundType") String refundType,
			@Param("cutoff") LocalDate cutoff);

	/**
	 * JPQL returns projection matching DailyRemittanceView. IMPORTANT: aliases must
	 * exactly match getters on DailyRemittanceView (e.g. getCrRefundType(),
	 * getCrCntrlDate(), getCrCntrlNbr()).
	 */

	@Query("SELECT " + " c.crId.crRefundType AS crRefundType, " + " c.crId.crCntrlDate AS crCntrlDate, "
			+ " c.crId.crCntrlNbr AS crCntrlNbr, " + " c.crRemDailyInd AS crRemDailyInd,"
			+ " c.crRemIdType AS crRemIdType, " + " c.crRemIdNbr AS crRemIdNbr, "
			+ " c.crRemittorName AS crRemittorName," + " c.crRemAddressee AS crRemAddressee,"
			+ " c.crRemAddress1 AS crRemAddress1, " + " c.crRemAddress2 AS crRemAddress2, "
			+ " c.crRemCity AS crRemCity, " + " c.crRemState AS crRemState, " + " c.crRemZip5 AS crRemZip5, "
			+ " c.crRemZip4 AS crRemZip4, " + " c.crChkAddress1 AS crChkAddress1, "
			+ " c.crChkAddress2 AS crChkAddress2, " + " c.crChkCity AS crChkCity, " + " c.crChkState AS crChkState, "
			+ " c.crChkZip5 AS crChkZip5, " + " c.crChkZip4 AS crChkZip4, " + " c.crUserId AS crUserId "
			+ "FROM P09CashReceipt c " + "WHERE ( " + " (c.Id.crCntrlDate > :lastDate) "
			+ " OR (c.Id.crCntrlDate = :lastDate AND c.Id.crCntrlNbr > :lastNbr) "
			+ " OR (c.Id.crCntrlDate = :lastDate AND c.Id.crCntrlNbr = :lastNbr AND c.Id.crRefundType > :lastType) "
			+ " ) " + // <<< GROUPED ORs
			" AND c.crRemDailyInd = 'I' " + " AND c.Id.crRefundType = 'RET' " + " AND c.crReasonCode = 'IRS' "
			+ "ORDER BY c.Id.crCntrlDate, c.Id.crCntrlNbr, c.Id.crRefundType")
	List<P09376DailyRemittanceView> findDailyRemittancesAfter(@Param("lastDate") LocalDate lastDate,
			@Param("lastNbr") String lastNbr, @Param("lastType") String lastType);
	@Query("""
			SELECT
			    c.crLocationClerk  AS clerk,
			    c.crLocationNbr    AS area,
			    c.crId.crCntrlDate      AS cntrlDate,
			    c.crId.crCntrlNbr       AS controlNbr,
			    c.crId.crRefundType     AS refundType,
			    c.crCntrldAmt      AS controlAmt,
			    c.crPatientLname   AS patientLast,
			    c.crPatientFname   AS patientFirst,
			    c.crMbrIdNbr       AS patientId,
			    c.crRemittorName   AS remittor,
			    c.crReasonCode     AS reasonCode,
			    c.crReceiptType    AS receiptType,
			    c.crCheckNbr       AS checkNbr,
			    c.crStatusText     AS statusText,
			    c.crReceivedDate   AS recvDate,
			    c.crLocationDate   AS locationDate
			FROM P09CashReceipt c
			WHERE
			    CONCAT(
			        c.crLocationNbr,
			        c.crLocationClerk,
			        FUNCTION('FORMAT', c.crReceivedDate, 'yyyy-MM-dd'),
			        c.crId.crRefundType,
			        c.crId.crCntrlNbr,
			        FUNCTION('FORMAT', c.crId.crCntrlDate, 'yyyy-MM-dd')
			    ) > :checkpointKey
			AND c.crStatusText IN ('OPEN','PENDED')
			ORDER BY
			    c.crLocationNbr,
			    c.crLocationClerk,
			    c.crReceivedDate,
			    c.crId.crRefundType,
			    c.crId.crCntrlNbr,
			    c.crId.crCntrlDate
			""")
	List<P09325RoutingView> fetchRoutingPage(@Param("checkpointKey") String checkpointKey);

	@Query("""
			SELECT
			    c.crId.crRefundType     AS refundType,
			    c.crId.crCntrlDate      AS cntrlDate,
			    c.crId.crCntrlNbr       AS controlNbr,
			    c.crCntrldAmt           AS controlAmt,
			    c.crReceiptBal          AS receiptBal,
			    c.crCheckNbr            AS checkNbr,
			    c.crCheckDate           AS checkDate,
			    c.crCheckAmt            AS checkAmt,
			    c.crRemittorName        AS remittor,
			    c.crPatientLname        AS patientLast,
			    c.crPatientFname        AS patientFirst,
			    c.crMbrIdNbr            AS patientId,
			    c.crReasonCode          AS reasonCode,
			    c.crLetterDate          AS letterDate,
			    c.crCorp                AS corp
			FROM P09CashReceipt c
			WHERE
			    c.crCorp = :corpNo
			AND c.crId.crRefundType = :refundType
			AND c.crStatusText IN ('OPEN', 'PENDED')
			AND c.crId.crCntrlDate <= :toDate
			ORDER BY
			    c.crId.crCntrlDate,
			    c.crId.crCntrlNbr
			""")
	List<CarryoverView> fetchCashReceiptCursor(@Param("corpNo") String corpNo, @Param("refundType") String refundType,
			@Param("toDate") LocalDate toDate);
	
	@Query("""
		    SELECT r
		    FROM P09CashReceipt r
		    WHERE r.crId.crRefundType = 'OFF'
		      AND r.crRemittorTitle IN ('OF1','OF2','OF3','OF4','OF5')
		""")
		Page<P09CashReceipt> findOffCashReceipts(Pageable pageable);


	@Modifying
	@Transactional
	@Query("""
			    UPDATE P09CashReceipt c
			       SET c.crRemittorTitle = ' '
			     WHERE c.crId.crRefundType = :refundType
			       AND c.crId.crCntrlDate  = :cntrlDate
			       AND c.crId.crCntrlNbr   = :cntrlNbr
			""")
	int clearRemittorTitle(@Param("refundType") String refundType, @Param("cntrlDate") LocalDate cntrlDate,
			@Param("cntrlNbr") String cntrlNbr);

}
