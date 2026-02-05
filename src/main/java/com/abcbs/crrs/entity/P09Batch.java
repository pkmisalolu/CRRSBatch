/**
 * Developer: Dharani Parimella
 * Description: This class handles P09Batch entity with composite primary key
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
@Table(name="P09_BATCH")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09Batch {
	
	@EmbeddedId
	private BatchPK bId;
	
	//BT_BATCH_CNT                   SMALLINT NOT NULL
	@Column(name = "BT_BATCH_CNT", nullable = false)
	private short btBatchCnt;
	
	@Column(name = "BT_BATCH_AMT", nullable = false, precision = 11, scale = 2)
	private BigDecimal btBatchAmt;
	
	@Column(name = "BT_CONTROL_DATE", nullable = false)
	private LocalDate btControlDate;
	
	@Column(name = "BT_RECEIVED_DATE", nullable = false)
	private LocalDate btReceivedDate;
	
	@Column(name = "BT_DEPOSIT_DATE")
	private LocalDate btDepositDate;
	
	@Column(name = "BT_ENTRY_DATE", nullable = false)
	private LocalDate btEntryDate;
	
	@Column(name = "BT_STATUS_DATE", nullable = false)
	private LocalDate btStatusDate;
	
	@Column(name = "BT_STATUS", nullable = false, length = 6)
	private String btStatus;
	
	@Column(name = "BT_POSTED_IND", nullable = false, length = 1)
	private String btPostedInd;

}
