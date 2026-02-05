package com.abcbs.crrs.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.abcbs.crrs.entity.ActivityPK;
import com.abcbs.crrs.entity.P09Activity;
import com.abcbs.crrs.jobs.P09315.ActivityAggView;

public interface IActivityRepository extends JpaRepository<P09Activity, ActivityPK> {
	// in IActivityRepository
	@Query("""
			   SELECT a FROM P09Activity a
			   WHERE a.aId.crRefundType = :refundType
			     AND a.aId.crCntrlDate = :cntrlDate
			     AND a.aId.crCntrlNbr = :cntrlNbr
			     AND a.aId.actActivity = 'ACC'
			     AND a.aId.actActivityDate = :activityDate
			   ORDER BY a.aId.crRefundType,
			            a.actXrefDate,
			            a.actXrefNbr,
			            a.aId.crCntrlDate,
			            a.aId.crCntrlNbr
			""")
	List<P09Activity> findActivitiesForAcc(@Param("refundType") String refundType,
			@Param("cntrlDate") LocalDate cntrlDate, @Param("cntrlNbr") String cntrlNbr,
			@Param("activityDate") LocalDate activityDate);

	@Query("""
			    SELECT a, b
			    FROM P09Activity a
			    JOIN P09CashReceipt b
			      ON a.aId.crRefundType = b.crId.crRefundType
			     AND a.aId.crCntrlDate  = b.crId.crCntrlDate
			     AND a.aId.crCntrlNbr   = b.crId.crCntrlNbr
			    WHERE a.aId.crRefundType = :refundType
			      AND  a.aId.actActivity IN :activities
			      AND a.actReportDate = :reportDate
			""")
	List<Object[]> findActivityReceiptRecords(@Param("refundType") String refundType,
			@Param("activities") List<String> activities, @Param("reportDate") String reportDate);

	@Query("""
			    SELECT a, b
			    FROM P09Activity a
			    JOIN P09CashReceipt b
			      ON a.aId.crRefundType = b.crId.crRefundType
			     AND a.aId.crCntrlDate  = b.crId.crCntrlDate
			     AND a.aId.crCntrlNbr   = b.crId.crCntrlNbr
			    WHERE a.aId.crRefundType = :refundType
			      AND  a.aId.actActivity IN :activities
			      AND a.actReportDate = :reportDate
			    ORDER BY a.aId.crRefundType, a.aId.crCntrlDate, a.aId.crCntrlNbr, a.aId.actTimestamp DESC
			""")
	List<Object[]> findActivityReceiptRecordsOrdered(@Param("refundType") String refundType,
			@Param("activities") List<String> activities, @Param("reportDate") String reportDate);

	@Query("""
			    SELECT a, b
			    FROM P09Activity a
			    JOIN P09CashReceipt b
			      ON a.aId.crRefundType = b.crId.crRefundType
			     AND a.aId.crCntrlDate  = b.crId.crCntrlDate
			     AND a.aId.crCntrlNbr   = b.crId.crCntrlNbr
			    WHERE b.crCorp = :corpNo
			      AND a.aId.crRefundType = :refundType
			      AND a.aId.actActivity  IN :activities
			      AND a.actReportDate = :reportDate
			""")
	List<Object[]> findActivityReceiptRecordsWithCorp(@Param("corpNo") String corpNo,
			@Param("refundType") String refundType, @Param("activities") List<String> activities,
			@Param("reportDate") String reportDate);

	@Query("""
			    SELECT a, b
			    FROM P09Activity a
			    JOIN P09CashReceipt b
			      ON a.aId.crRefundType = b.crId.crRefundType
			     AND a.aId.crCntrlDate  = b.crId.crCntrlDate
			     AND a.aId.crCntrlNbr   = b.crId.crCntrlNbr
			    WHERE b.crCorp = :corpNo
			      AND a.aId.crRefundType = :refundType
			      AND a.aId.actActivity IN :activities
			      AND a.actReportDate = :reportDate
			    ORDER BY a.aId.crRefundType, a.aId.crCntrlDate, a.aId.crCntrlNbr, a.aId.actTimestamp DESC
			""")
	List<Object[]> findActivityReceiptRecordsWithCorpOrdered(@Param("corpNo") String corpNo,
			@Param("refundType") String refundType, @Param("activities") List<String> activities,
			@Param("reportDate") String reportDate);

	// ================================================================
	// PX03 SELECT — JPQL
	// ================================================================
	@Query("""
			SELECT a FROM P09Activity a
			WHERE a.aId.crRefundType = :refundType
			  AND a.aId.crCntrlDate = :cntrlDate
			  AND a.aId.crCntrlNbr = :cntrlNbr
			  AND (a.aId.actActivity = 'PRR' OR a.aId.actActivity = 'FRR')
			  AND a.actProcessedInd = ' '
			ORDER BY a.aId.crRefundType,
			         a.aId.crCntrlDate,
			         a.aId.crCntrlNbr,
			         a.actWorkingBal,
			         a.aId.actActivityDate,
			         a.aId.actActivity
			""")
	List<P09Activity> fetchPendingActivities(@Param("refundType") String refundType,
			@Param("cntrlDate") LocalDate cntrlDate, @Param("cntrlNbr") String cntrlNbr);

	// ================================================================
	// PX03 UPDATE — JPQL
	// ================================================================
	@Modifying
	@Transactional
	@Query("""
			UPDATE P09Activity a
			SET a.actProcessedInd = :processedInd
			WHERE a.aId.crRefundType = :refundType
			  AND a.aId.crCntrlDate = :cntrlDate
			  AND a.aId.crCntrlNbr = :cntrlNbr
			  AND a.aId.actActivityDate = :activityDate
			  AND a.aId.actActivity = :activity
			  AND a.aId.actTimestamp = :actTimestamp
			""")
	int updateProcessedInd(@Param("processedInd") String processedInd, @Param("refundType") String refundType,
			@Param("cntrlDate") LocalDate cntrlDate, @Param("cntrlNbr") String cntrlNbr,
			@Param("activityDate") LocalDate activityDate, @Param("activity") String activity,
			@Param("actTimestamp") LocalDateTime actTimestamp);

	@Query("""
			  SELECT
			      a.aId.crRefundType AS refundType,
			      a.aId.actActivity  AS activity,
			      COUNT(a)       AS count,
			      SUM(a.actActivityAmt) AS amount
			  FROM P09Activity a
			  JOIN P09CashReceipt b
			    ON a.aId.crRefundType = b.crId.crRefundType
						AND a.aId.crCntrlDate  = b.crId.crCntrlDate
				           AND a.aId.crCntrlNbr   = b.crId.crCntrlNbr
			  WHERE b.crCorp = :corp
			  AND a.actDailyInd = 'Y'
					AND a.aId.actActivity IN ('ACC','APP','REM','DEL','LOG','FRR','PRR')
			  GROUP BY a.aId.crRefundType,a.aId.actActivity
			  ORDER BY a.aId.actActivity, a.aId.crRefundType
			""")
	List<ActivityAggView> fetchEstablished(@Param("corp") String corp);

	@Query("""
			    SELECT
			        a.aId.crRefundType        AS refundType,
			        a.aId.actActivity         AS activity,
			        COUNT(a)              AS count,
			        SUM(a.actActivityAmt) AS amount
			    FROM P09Activity a
			    JOIN P09CashReceipt b
			      ON a.aId.crRefundType = b.crId.crRefundType

			    WHERE b.crCorp = :corp
			      AND a.actDailyInd = 'Y'
			      AND a.aId.actActivity IN ('ACC','APP','REM','DEL','LOG','FRR','PRR')
			    GROUP BY a.aId.crRefundType, a.aId.actActivity
			    ORDER BY a.aId.actActivity, a.aId.crRefundType
			""")
	List<ActivityAggView> fetchManualRecon(@Param("corp") String corp);

	@Query("""
			    SELECT
			        a.aId.crRefundType         AS refundType,
			        TRIM(a.aId.actActivity)   AS activity,
			        COUNT(a)              AS count,
			        SUM(a.actActivityAmt) AS amount
			    FROM P09Activity a
			    JOIN P09CashReceipt b
			      ON a.aId.crRefundType = b.crId.crRefundType
			     AND a.aId.crCntrlDate  = b.crId.crCntrlDate
			     AND a.aId.crCntrlNbr    = b.crId.crCntrlNbr
			    WHERE b.crCorp = :corp
			      AND a.actDailyInd = 'Y'
			      AND TRIM(a.aId.actActivity) IN ('FR','PR')
			    GROUP BY a.aId.crRefundType, TRIM(a.aId.actActivity)
			    ORDER BY TRIM(a.aId.actActivity), a.aId.crRefundType
			""")
	List<ActivityAggView> fetchSystemRecon(@Param("corp") String corp);

	@Modifying
	@Transactional
	@Query("""
			    UPDATE P09Activity a
			       SET a.actDailyInd = ' '
			     WHERE a.actDailyInd = 'Y'
			       AND EXISTS (
			            SELECT 1
			              FROM P09CashReceipt b
			             WHERE b.crCorp = :corp
			               AND b.crId.crRefundType = a.aId.crRefundType
			               AND b.crId.crCntrlDate  = a.aId.crCntrlDate
			               AND b.crId.crCntrlNbr   = a.aId.crCntrlNbr
			       )
			""")
	int clearDailyFlag(@Param("corp") String corp);

	@Query("SELECT 1 FROM P09Activity a")
	Integer lockActivityTable();
}
