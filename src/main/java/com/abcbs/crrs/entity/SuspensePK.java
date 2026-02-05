package com.abcbs.crrs.entity;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Embeddable
@NoArgsConstructor
public class SuspensePK implements Serializable {
	
	@Column(name = "CR_REFUND_TYPE", length = 3, nullable = false)
    private String crRefundType;

    @Column(name = "CR_CNTRL_DATE", nullable = false)
    private LocalDate crCntrlDate;

    @Column(name = "CR_CNTRL_NBR", length = 4, nullable = false)
    private String crCntrlNbr;
}
