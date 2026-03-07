/**
* @author Chandrakanth Gangalam
* Created on Feb 16, 2026
*/

package com.abcbs.crrs.jobs.P09350;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09350CorpCardInput {
    private String filler1;    // X(10)
    private String corpCode;   // X(02)
    private String filler2;    // X(68)
}
