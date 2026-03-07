/**
* @author Chandrakanth Gangalam
* Created on Jan 3, 2026
*/
//M O D I F I C A T I O N    L O G
//Chandrakanth 03-Jan-2026: Coverted COBOL to java.

package com.abcbs.crrs.jobs.P09350;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OccsDedsFileOutRec implements Serializable{
	private static final long serialVersionUID = 1L;
    private String record; // 500 chars
}
