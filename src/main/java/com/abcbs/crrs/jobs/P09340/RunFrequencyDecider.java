package com.abcbs.crrs.jobs.P09340;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.stereotype.Component;

@Component
public class RunFrequencyDecider implements JobExecutionDecider {

    private static final Logger log = LogManager.getLogger(RunFrequencyDecider.class);

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        P09340InputRecord record = (P09340InputRecord)
                jobExecution.getExecutionContext().get("p09340InputRecord");

        if (record == null) {
            log.error("Missing input record in ExecutionContext");
            return new FlowExecutionStatus("FAILED");
        }

        String freq = record.getRunFrequency();
        log.info("RunFrequencyDecider → RUN-FREQUENCY={}", freq);

        return switch (freq) {
            case "W" -> new FlowExecutionStatus("WEEKLY");
            case " " -> new FlowExecutionStatus("MONTHLY");
            default -> new FlowExecutionStatus("MONTHLY");
        };
    }
}
