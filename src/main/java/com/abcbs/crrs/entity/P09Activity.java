/**
 * Developer: Dharani Parimella
 * Description: This class handles P09Activity entity with composite primary key
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
@Table(name="P09_ACTIVITY")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09Activity {
	
	@EmbeddedId
	private ActivityPK aId;
	
	@Column(name = "ACT_ACTIVITY_AMT", precision = 11, scale = 2)
	private BigDecimal actActivityAmt;
	
	@Column(name = "ACT_WORKING_BAL", precision = 11, scale = 2)
	private BigDecimal actWorkingBal;
	
	@Column(name = "ACT_XREF_TYPE", length = 2, nullable = false)
	private String actXrefType;
	
	@Column(name = "ACT_XREF_NBR", length = 20, nullable = false)
	private String actXrefNbr;
	
	@Column(name = "ACT_XREF_DATE")
	private LocalDate actXrefDate;
	
	@Column(name = "ACT_REPORT_DATE", length = 4, nullable = false)
	private String actReportDate;
	
	@Column(name = "ACT_USER_ID", length = 7, nullable = false)
	private String actUserId;
	
	@Column(name = "ACT_CMT_IND", length = 1, nullable = false)
	private String actCmtInd;
	
	@Column(name = "ACT_DAILY_IND", length = 1, nullable = false)
	private String actDailyInd;
	
	@Column(name = "ACT_PROCESSED_IND", length = 1, nullable = false)
	private String actProcessedInd;
	
	@Column(name = "ACT_ARRS_CODE", length = 2, nullable = false)
	private String actArrsCode;
}
