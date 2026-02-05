package com.abcbs.crrs.jobs.P09340;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09340InputRecord implements Serializable {
    private static final long serialVersionUID = 1L;
	// From CORP-FILE
	private String corpId;
	private String corpProgramId;
	private String corpSequence;
	private String corpNo;
	private String corpCode;

	// From CONTROL-FILE
	private String controlId;
	private String cntrlProgramId;
	private String cntrlSequence;
	private String refundType;
	private String runFrequency;
	
	private String currentDate;
	private String transCode;
	private String reportDate;
    
    
    private String select;

}
