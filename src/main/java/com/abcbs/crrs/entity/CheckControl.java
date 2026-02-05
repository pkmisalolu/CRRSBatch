/**
 * Developer: Dharani Parimella
 * Description: This class handles CheckControl entity with composite primary key
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
@Table(name="CHECK_CONTROL")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CheckControl {

	@EmbeddedId
	private CheckControlPK ccId;

	@Column(name = "CONTROL_TO_DATE", nullable = false)
	private LocalDate controlToDate;

	@Column(name = "PREVIOUS_BALANCE", precision = 11, scale = 2, nullable = false)
	private BigDecimal previousBalance;

	@Column(name = "PREVIOUS_COUNT", nullable = false)
	private Integer previousCount;

}
