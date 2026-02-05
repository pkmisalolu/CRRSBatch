package com.abcbs.crrs.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.abcbs.crrs.entity.P09Summary;
import com.abcbs.crrs.entity.SummaryPK;
import com.abcbs.crrs.jobs.P09325.P09325RoutingView;
import com.abcbs.crrs.jobs.P09325.P09325SummaryView;
import com.abcbs.crrs.jobs.P09325.P09325kHeaderView;

import jakarta.transaction.Transactional;

public interface IP09SummaryRepository extends JpaRepository<P09Summary, SummaryPK> {

	// SELECT (COBOL)
	@Query("""
			    SELECT s FROM P09Summary s
			    WHERE s.sId.sumLocationID = :locationId
			      AND s.sId.sumClerkId = :clerkId
			""")
	Optional<P09Summary> findSummary(String locationId, String clerkId);

	// UPDATE (COBOL)
	@Modifying
	@Query("""
			    UPDATE P09Summary s
			    SET s.sumDeletionsCnt = :sumDelCnt,
			        s.sumDeletionsAmt = :sumDelAmt,
			        s.sumEndingCnt    = :sumEndCnt,
			        s.sumEndingAmt    = :sumEndAmt
			    WHERE s.sId.sumLocationID = :locationId
			      AND s.sId.sumClerkId = :clerkId
			""")
	int updateSummary(Integer sumDelCnt, BigDecimal sumDelAmt, Integer sumEndCnt, BigDecimal sumEndAmt,
			String locationId, String clerkId);

	@Query("""
			    SELECT
			        s.sId.sumLocationID     AS area,
			        SUM(s.sumBeginningAmt) AS beginAmt,
			        SUM(s.sumBeginningCnt) AS beginCnt,
			        SUM(s.sumAdditionsAmt) AS recvAmt,
			        SUM(s.sumAdditionsCnt) AS recvCnt,
			        SUM(s.sumDeletionsAmt) AS fwdAmt,
			        SUM(s.sumDeletionsCnt) AS fwdCnt,
			        SUM(s.sumEndingAmt)    AS endAmt,
			        SUM(s.sumEndingCnt)    AS endCnt
			    FROM P09Summary s
			    GROUP BY s.sId.sumLocationID
			""")
	List<P09325SummaryView> fetchSummary();

	@Query("""
			    SELECT
			        s.sId.sumLocationID      AS area,
			        s.sId.sumClerkId        AS clerk,
			        s.sumBeginningCnt     AS beginCnt,
			        s.sumBeginningAmt     AS beginAmt,
			        s.sumAdditionsCnt     AS recvCnt,
			        s.sumAdditionsAmt     AS recvAmt,
			        s.sumDeletionsCnt     AS fwdCnt,
			        s.sumDeletionsAmt     AS fwdAmt,
			        s.sumEndingCnt        AS endCnt,
			        s.sumEndingAmt        AS endAmt
			    FROM P09Summary s
			    WHERE s.sId.sumLocationID  = :area
			      AND s.sId.sumClerkId    = :clerk
			""")
	Optional<P09325kHeaderView> fetchHeader(@Param("area") String area, @Param("clerk") String clerk);

	@Modifying
	@Transactional
	@Query("""
			    UPDATE P09Summary s
			       SET s.sumProcessedDate = CURRENT_DATE,
			           s.sumBeginningCnt  = s.sumEndingCnt,
			           s.sumBeginningAmt  = s.sumEndingAmt,
			           s.sumAdditionsCnt  = 0,
			           s.sumAdditionsAmt  = 0,
			           s.sumDeletionsCnt  = 0,
			           s.sumDeletionsAmt  = 0
			""")
	int rollSummary();

	@Query("""
			    SELECT
			        s.sId.sumLocationID      AS area,
			        SUM(s.sumBeginningCnt)  AS beginCnt,
			        SUM(s.sumBeginningAmt)  AS beginAmt,
			        SUM(s.sumAdditionsCnt)  AS recvCnt,
			        SUM(s.sumAdditionsAmt)  AS recvAmt,
			        SUM(s.sumDeletionsCnt)  AS fwdCnt,
			        SUM(s.sumDeletionsAmt)  AS fwdAmt,
			        SUM(s.sumEndingCnt)     AS endCnt,
			        SUM(s.sumEndingAmt)     AS endAmt
			    FROM P09Summary s
			    GROUP BY s.sId.sumLocationID
			""")
	List<P09325SummaryView> fetchControllerSummary();

	// composite key exactly matches ORDER BY (L1199-1216)
	public static String makeKey(P09325RoutingView r) {
		return String.join("|", nz(r.getArea()), nz(r.getClerk()),
				r.getRecvDate() == null ? "" : r.getRecvDate().toString().replace("-", ""), nz(r.getRefundType()),
				nz(r.getControlNbr()), "00000000" // control date placeholder if not available in fetch; include if
													// present.
		);
	}

	private static String nz(String s) {
		return s == null ? "" : s.trim();
	}
}