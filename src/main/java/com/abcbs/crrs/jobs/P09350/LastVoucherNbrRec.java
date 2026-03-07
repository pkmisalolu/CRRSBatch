/**
* @author Chandrakanth Gangalam
* Created on Feb 10, 2026
*/

package com.abcbs.crrs.jobs.P09350;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LastVoucherNbrRec implements Serializable{
	private static final long serialVersionUID = 1L;
	private String prefix = "Z";
    private int suffix;
    private String filler = "";
}
