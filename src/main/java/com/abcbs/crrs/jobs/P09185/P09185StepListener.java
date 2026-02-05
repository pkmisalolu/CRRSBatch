package com.abcbs.crrs.jobs.P09185;

import org.springframework.batch.core.StepExecutionListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.ExitStatus;

public class P09185StepListener implements StepExecutionListener {
	
	private static final Logger logger = LogManager.getLogger(P09185StepListener.class);

    @Override
    public void beforeStep(StepExecution stepExecution) {
        logger.info("Step {} started. Job Parameters: {}", stepExecution.getStepName(), stepExecution.getJobParameters());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        int readCount = (int) stepExecution.getReadCount();
        logger.info("Step {} finished. Records read: {}", stepExecution.getStepName(), readCount);

        if (readCount == 0) {
            logger.warn("Input file is empty. Setting RETURN-CODE = 55");
            return new ExitStatus("55");
        }

        logger.info("Input file is not empty. Setting RETURN-CODE = 0");
        return ExitStatus.COMPLETED;
    }

}
