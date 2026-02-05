package com.abcbs.crrs.jobs.P09352;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09352ControlCardInput {
    private String cntrlId;        // X(02)
    private String pgmId;          // X(06)
    private String seqNbr;         // X(01)
    private String filler1;        // X(02)
    private String runTypeInd;     // X(01)
    private String filler2;        // X(01)
    private String compareMm;      // 9(02)
    private String compareDd;      // 9(02)
    private String compareYy;      // 9(02)
    private String filler3;        // X(61)
}