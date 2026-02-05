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
@NoArgsConstructor
@Embeddable
public class BankReconPK implements Serializable {

	@Column(name = "BANK_ACCOUNT_NBR", length = 35, nullable = false)
	private String bankAccountNbr;
	
	@Column(name = "CHECK_NBR", length = 8, nullable = false)
	private String checkNbr;

	@Column(name = "CHECK_DATE", nullable = false)
	private LocalDate checkDate;
}
