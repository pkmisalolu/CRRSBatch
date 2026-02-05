package com.abcbs.crrs.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.abcbs.crrs.entity.ControlPK;
import com.abcbs.crrs.entity.P09Control;
import com.abcbs.crrs.jobs.P09345.P09ControlView;
import com.abcbs.crrs.projections.IP09340ControlView;

public interface IP09ControlRepository extends JpaRepository<P09Control, ControlPK> {

	@Query("""
			SELECT c
			FROM P09Control c
			WHERE c.controlId.cntrlRefundType = :refundType
			  AND c.controlId.cntrlOpenInd = 'O'
			""")
	Optional<P09Control> findOpenControl(@Param("refundType") String refundType);

	@Modifying
	@Query("""
			UPDATE P09Control c
			SET c.controlId.cntrlOpenInd = 'C'
			WHERE c.controlId.cntrlRefundType = :refundType
			  AND c.controlId.cntrlOpenInd = 'O'
			""")
	void closeCurrentControl(@Param("refundType") String refundType);

	// *********************************P09340********************************************************
	@Query("""
			    SELECT c.controlId.cntrlRefundType AS cntrlRefundType,
			           c.controlId.cntrlFromDate   AS cntrlFromDate,
			           c.cntrlToDate     AS cntrlToDate,
			           c.cntrlReceiptCnt AS cntrlReceiptCnt,
			           c.cntrlReceiptAmt AS cntrlReceiptAmt,
			           c.cntrlRefundNarr AS cntrlRefundNarr
			    FROM P09Control c
			    WHERE c.controlId.cntrlRefundType = :refundType
			      AND c.controlId.cntrlOpenInd = :openInd
			""")
	public IP09340ControlView findControlRecord(@Param("refundType") String refundType,
			@Param("openInd") String openInd);

	@Query("""
			    SELECT c.controlId.cntrlRefundType AS cntrlRefundType,
			           c.cntrlToDate AS cntrlToDate,
			           c.cntrlRefundNarr AS cntrlRefundNarr
			    FROM P09Control c
			    WHERE c.controlId.cntrlRefundType = :refundType
			      AND c.controlId.cntrlOpenInd = 'O'
			    ORDER BY c.controlId.cntrlRefundType
			""")
	List<P09ControlProjection> fetchControlRows(String refundType);

	public interface P09ControlProjection {
		String getCntrlRefundType();

		LocalDate getCntrlToDate();

		String getCntrlRefundNarr();
	}

	@Query("""
			SELECT
			    c.controlId.cntrlRefundType   AS refundType,
			    c.cntrlToDate       AS toDate,
			    c.cntrlRefundNarr   AS refundNarr
			FROM P09Control c
			WHERE
			    c.controlId.cntrlRefundType > :chkRefundType
			AND c.controlId.cntrlRefundType <> 'PER'
			AND c.controlId.cntrlOpenInd = 'O'
			ORDER BY
			    c.controlId.cntrlRefundType
			""")
	List<P09ControlView> findCarryoverByRefundType(@Param("chkRefundType") String chkRefundType);

}
