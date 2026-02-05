package com.abcbs.crrs.jobs.P09183;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09183InputRecord {
	
	private String formCount;           
    private String totalControlledAmount; 
    private String controlledAmtNumeric; 

}
