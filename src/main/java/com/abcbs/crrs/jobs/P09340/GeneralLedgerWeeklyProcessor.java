package com.abcbs.crrs.jobs.P09340;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.batch.item.ExecutionContext;

@Component
@StepScope
public class GeneralLedgerWeeklyProcessor implements ItemProcessor<P09340GlRecord, P09340GlRecord>, StepExecutionListener {

    private static final Logger log = LogManager.getLogger(GeneralLedgerWeeklyProcessor.class);

    private String corpCode;

    //private StepExecution stepExecution;

    

    @Override public void beforeStep(StepExecution se) {
        ExecutionContext jec = se.getJobExecution().getExecutionContext();
        P09340InputRecord rec = (P09340InputRecord) jec.get("p09340InputRecord");
        if (rec == null) throw new IllegalStateException("p09340InputRecord missing");
        this.corpCode = rec.getCorpNo();
    }

    @Override
    public P09340GlRecord process(P09340GlRecord gl) {
        if (matchesCorp(gl.getGlCorp(), corpCode)) {
            log.debug("GL record accepted: corp={} matches {}", gl.getGlCorp(), corpCode);
            return gl;
        }
        log.debug("GL record skipped: corp={} != {}", gl.getGlCorp(), corpCode);
        return null;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return stepExecution.getExitStatus();
    }

    private boolean matchesCorp(String glCorp, String wanted) {
        if (glCorp == null || wanted == null) return false;
        return glCorp.trim().equals(wanted.trim());
    }
}
