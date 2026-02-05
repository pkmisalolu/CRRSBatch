package com.abcbs.crrs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Embeddable
@NoArgsConstructor
public class SummaryPK {
	
	@Column(name="SUM_LOCATION_ID", length = 3, nullable = false)
	private String sumLocationID;
	
	@Column(name="SUM_CLERK_ID", length=4, nullable = false)
	private String sumClerkId;

}
