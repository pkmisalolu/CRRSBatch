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
public class CheckControlPK implements Serializable {
	
	@Column(name = "BANK_ACCOUNT_NBR", length = 35, nullable = false)
	private String bankAccountNbr;

	@Column(name = "FILE_INDICATOR", length = 1, nullable = false)
	private String fileIndicator;

	@Column(name = "OPEN_INDICATOR", length = 1, nullable = false)
	private String openIndicator;

	@Column(name = "CONTROL_FROM_DATE", nullable = false)
	private LocalDate controlFromDate;
}
