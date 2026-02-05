package com.abcbs.crrs.entity;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CashReceiptPK implements Serializable{
	
	private String crRefundType;
	private LocalDate crCntrlDate;
	private String crCntrlNbr;

}
