package com.abcbs.crrs.jobs.P09373;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POJO for DELETE-RECS (14 bytes).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09373InputRecord {
    private String refundType;   // X(3)
    private String batchPrefix;  // X(3)
    private String batchDate;    // X(6)
    private String batchSuffix;  // X(2)

    public String getBatchId() {
        return batchPrefix + batchDate + batchSuffix; // COBOL equivalent concatenation
    }
}
