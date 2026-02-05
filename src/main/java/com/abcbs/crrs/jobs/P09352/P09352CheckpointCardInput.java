package com.abcbs.crrs.jobs.P09352;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09352CheckpointCardInput {
    private String count;     // 9(06)
    private String filler;    // X(74)
}