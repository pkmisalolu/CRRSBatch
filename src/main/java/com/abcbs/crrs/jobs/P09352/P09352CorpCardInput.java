package com.abcbs.crrs.jobs.P09352;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09352CorpCardInput {
    private String filler1;    // X(10)
    private String corpCode;   // X(02)
    private String filler2;    // X(68)
}
