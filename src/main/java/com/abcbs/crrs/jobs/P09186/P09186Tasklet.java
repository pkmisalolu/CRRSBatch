package com.abcbs.crrs.jobs.P09186;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.io.BufferedReader;
import java.io.FileReader;

public class P09186Tasklet implements Tasklet {

    private static final Logger log = LogManager.getLogger(P09186Tasklet.class);

    private final String inputFile;

    public P09186Tasklet(String inputFile) {
        this.inputFile = inputFile;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("P09186 started. Checking file: {}", inputFile);

        boolean empty = true;
        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
            if (br.readLine() != null) {
                empty = false;
            }
        }

        if (empty) {
            log.warn("File {} is empty. Returning code 55.", inputFile);
            contribution.setExitStatus(new org.springframework.batch.core.ExitStatus("55"));
            System.exit(55);  // matching COBOL RETURN-CODE
        } else {
            log.info("File {} is not empty. Returning code 0.", inputFile);
            contribution.setExitStatus(org.springframework.batch.core.ExitStatus.COMPLETED);
            System.exit(0);   // COBOL return 0
        }

        return RepeatStatus.FINISHED;
    }
}
