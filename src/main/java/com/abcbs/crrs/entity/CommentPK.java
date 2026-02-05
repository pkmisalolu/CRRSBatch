package com.abcbs.crrs.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentPK implements Serializable {

	@Column(name = "CR_REFUND_TYPE", nullable = false, length = 3)
	private String crRefundType;

	@Column(name = "CR_CNTRL_DATE", nullable = false)
	private LocalDate crCntrlDate;

	@Column(name = "CR_CNTRL_NBR", nullable = false, length = 4)
	private String crCntrlNbr;

	@Column(name = "ACT_ACTIVITY_DATE", nullable = false)
	private LocalDate actActivityDate;

	@Column(name = "ACT_ACTIVITY", nullable = false, length = 3)
	private String actActivity;

	@Column(name = "ACT_TIMESTAMP", nullable = false)
	private LocalDateTime actTimestamp;

	@Column(name = "CMT_TIMESTAMP", nullable = false)
	private LocalDateTime cmtTimestamp;

}
