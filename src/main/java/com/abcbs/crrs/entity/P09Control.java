/**
 * Developer: Dharani Parimella
 * Description: This class handles P09Control entity with composite primary key
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
@Table(name="P09_CONTROL")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09Control {
	
	@EmbeddedId
	private ControlPK controlId;

	@Column(name = "CNTRL_TO_DATE", nullable = false)
	private LocalDate cntrlToDate;

	@Column(name = "CNTRL_RECEIPT_CNT", nullable = false)
	private Integer cntrlReceiptCnt;

	@Column(name = "CNTRL_RECEIPT_AMT", precision = 13, scale = 2, nullable = false)
	private BigDecimal cntrlReceiptAmt;

	@Column(name = "CNTRL_REFUND_NARR", length = 13, nullable = false)
	private String cntrlRefundNarr;


}
