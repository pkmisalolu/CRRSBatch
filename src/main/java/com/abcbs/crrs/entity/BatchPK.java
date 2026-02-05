package com.abcbs.crrs.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BatchPK implements Serializable {
	
	@Column(name = "CR_REFUND_TYPE", nullable = false, length = 3)
	private String crRefundType;
	
	@Column(name = "BT_BATCH_PREFIX", nullable = false, length = 3)
	private String btBatchPrefix;
	
	@Column(name = "BT_BATCH_DATE", nullable = false, length = 6)
	private String btBatchDate;
	
	@Column(name = "BT_BATCH_SUFFIX", nullable = false, length = 2)
	private String btBatchSuffix;

}
