package com.abcbs.crrs.jobs.P09376;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** COBOL CHECK-POINT-FILE: total 80 characters */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckPointRecord {
    private String checkPointFrequency;  // PIC 9(6)
    private String filler;               // PIC X(74)
}