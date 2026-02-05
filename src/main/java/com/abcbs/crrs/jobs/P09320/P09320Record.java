package com.abcbs.crrs.jobs.P09320;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents one record from REFUND-TYPE-CARD (80-character record)
 * COBOL layout reference:
 *   05  WS-CONTROL-ID        PIC X(02)
 *   05  WS-PROGRAM-NAME      PIC X(06)
 *   05  WS-CARD-SEQ          PIC X(01)
 *   05  WS-FILLER1           PIC X(01)
 *   05  WS-REFUND-TYPE-C     PIC X(03)
 *   05  WS-FILLER2           PIC X(67)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09320Record {
    private String controlId;       // positions 1–2
    private String programName;     // 3–8
    private String cardSeq;         // 9
    private String filler1;         // 10
    private String refundType;      // 11–13
    private String filler2;         // 14–80
}
