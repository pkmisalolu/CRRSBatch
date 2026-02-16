package com.abcbs.crrs.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.abcbs.crrs.entity.BatchPK;
import com.abcbs.crrs.entity.P09Batch;
import com.abcbs.crrs.jobs.P09175.P09175BatchView;

import jakarta.transaction.Transactional;

public interface IP09BatchRepository extends JpaRepository<P09Batch, BatchPK> {

	@Query(value = """
			SELECT
			  CR_REFUND_TYPE,
			  BT_BATCH_PREFIX,
			  BT_BATCH_DATE,
			  BT_BATCH_SUFFIX,
			  BT_BATCH_CNT,
			  BT_BATCH_AMT,
			  BT_CONTROL_DATE,
			  BT_RECEIVED_DATE,
			  BT_DEPOSIT_DATE,
			  BT_ENTRY_DATE,
			  BT_STATUS_DATE,
			  BT_STATUS,
			  BT_POSTED_IND
			FROM P09_BATCH
			WHERE CONCAT(CONCAT(CONCAT(BT_BATCH_PREFIX, BT_BATCH_DATE), BT_BATCH_SUFFIX), CR_REFUND_TYPE) > :ck
			  AND BT_POSTED_IND = ' '
			ORDER BY BT_BATCH_PREFIX, BT_BATCH_DATE, BT_BATCH_SUFFIX, CR_REFUND_TYPE
			""", nativeQuery = true)
			List<P09Batch> fetchAfterKeyUnposted(@Param("ck") String ck, Pageable page);

	// ************************P09310**************************************************************

	@Modifying
	@Transactional
	@Query("DELETE FROM P09Batch b WHERE b.bId.btBatchPrefix = :prefix AND b.bId.btBatchDate = :date AND b.bId.btBatchSuffix = :suffix "
			+ "AND b.bId.crRefundType = :refundType")
	public int deleteByBatchAndRefund(@Param("prefix") String prefix, @Param("date") String date,
			@Param("suffix") String suffix, @Param("refundType") String refundType);

	@Query("""
			SELECT
			    b.bId.crRefundType    AS refundType,
			    b.bId.btBatchPrefix   AS batchPrefix,
			    b.bId.btBatchDate     AS batchDate,
			    b.bId.btBatchSuffix   AS batchSuffix,
			    b.btBatchCnt      AS batchCnt,
			    b.btBatchAmt      AS batchAmt,
			    b.btControlDate   AS controlDate,
			    b.btReceivedDate  AS receivedDate,
			    b.btDepositDate   AS depositDate,
			    b.btEntryDate     AS entryDate,
			    b.btStatusDate    AS statusDate,
			    b.btStatus        AS status
			FROM P09Batch b
			WHERE
			    b.btPostedInd = 'T'
			ORDER BY
			    b.bId.crRefundType,
			    b.bId.btBatchPrefix,
			    b.bId.btBatchDate,
			    b.bId.btBatchSuffix
			""")
	List<P09175BatchView> fetchPx01Cursor();

	@Modifying
	@Transactional
	@Query("""
			UPDATE P09Batch b
			   SET b.btPostedInd = :postedInd
			 WHERE b.bId.btBatchSuffix = :batchSuffix
			   AND b.bId.btBatchDate   = :batchDate
			   AND b.bId.btBatchPrefix = :batchPrefix
			   AND b.bId.crRefundType  = :refundType
			""")
	int updatePostedIndicator(@Param("postedInd") String postedInd, @Param("batchSuffix") String batchSuffix,
			@Param("batchDate") String batchDate, @Param("batchPrefix") String batchPrefix,
			@Param("refundType") String refundType);

}
