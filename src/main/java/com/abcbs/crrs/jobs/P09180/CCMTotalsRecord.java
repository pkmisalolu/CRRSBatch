package com.abcbs.crrs.jobs.P09180;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CCMTotalsRecord {
    private String formCount;                // X(5)
    private String totalControlledAmount;    // X(15)
}



