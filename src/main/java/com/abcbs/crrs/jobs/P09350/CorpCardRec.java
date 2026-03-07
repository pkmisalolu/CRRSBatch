/**
* @author Chandrakanth Gangalam
* Created on Jan 3, 2026
*/
//M O D I F I C A T I O N    L O G
//Chandrakanth 03-Jan-2026: Coverted COBOL to java.

package com.abcbs.crrs.jobs.P09350;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CorpCardRec {
	private String filler1;    // X(10)
    private String corpCode;   // X(02)
    private String filler2;    // X(68)
}