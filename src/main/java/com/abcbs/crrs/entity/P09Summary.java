/**
 * Developer: Dharani Parimella
 * Description: This class handles P09Summary entity with composite primary key
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
import lombok.NoArgsConstructor;

@Entity
@Table(name="P09_SUMMARY")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09Summary {
	
	@EmbeddedId
	private SummaryPK sId;
	
	@Column(name="SUM_PROCESSED_DATE", nullable = false)
	private LocalDate sumProcessedDate;
	
	@Column(name="SUM_BEGINNING_CNT", nullable = false)
	private Integer sumBeginningCnt;
	
	@Column(name="SUM_BEGINNING_AMT",  precision = 11, scale = 2, nullable = false)
	private BigDecimal sumBeginningAmt;
	
	@Column(name="SUM_ADDITIONS_CNT", nullable = false)
	private Integer sumAdditionsCnt;
	
	@Column(name="SUM_ADDITIONS_AMT", precision = 11, scale = 2, nullable = false)
	private BigDecimal sumAdditionsAmt;
	
	@Column(name="SUM_DELETIONS_CNT", nullable = false)
	private Integer sumDeletionsCnt;
	
	@Column(name="SUM_DELETIONS_AMT", precision = 11, scale = 2, nullable = false)
	private BigDecimal sumDeletionsAmt;
	
	@Column(name="SUM_ENDING_CNT", nullable = false)
	private Integer sumEndingCnt;
	
	@Column(name="SUM_ENDING_AMT", precision = 11, scale = 2, nullable = false)
	private BigDecimal sumEndingAmt;
	

}
