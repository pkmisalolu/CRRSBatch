package com.abcbs.crrs.entity;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ControlPK implements Serializable {
	
	@Column(name = "CNTRL_REFUND_TYPE", length = 3, nullable = false)
	private String cntrlRefundType;

	@Column(name = "CNTRL_OPEN_IND", length = 1, nullable = false)
	private String cntrlOpenInd;
	
	@Column(name = "CNTRL_FROM_DATE", nullable = false)
	private LocalDate cntrlFromDate;
}
