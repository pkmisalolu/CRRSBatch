package com.abcbs.crrs.jobs.P09376;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** COBOL LABEL-FILE: 109 characters per record */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabelRecord {
    private String labelLine109; // PIC X(109)
}
