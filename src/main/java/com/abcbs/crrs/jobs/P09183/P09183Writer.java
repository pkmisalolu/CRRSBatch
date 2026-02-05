package com.abcbs.crrs.jobs.P09183;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.math.BigDecimal;

@Component
public class P09183Writer implements ItemWriter<P09183OutputRecord>, StepExecutionListener {
    private static final Logger logger = LogManager.getLogger(P09183Writer.class);
    private BufferedWriter writer;
    private static final DecimalFormat AMT_FORMAT =new DecimalFormat("###,###,##0.00");

    @Override
    public void beforeStep(StepExecution stepExecution) {
        String outputFile = stepExecution.getJobParameters().getString("outputFile");
        logger.info("Initializing P09183Writer. Output file: {}", outputFile);
        try {
            writer = Files.newBufferedWriter(Paths.get(outputFile));
        } catch (IOException e) {
            logger.error("Cannot open output file: {}", outputFile, e);
            throw new IllegalStateException("Cannot open output file", e);
        }
    }

    @Override
    public void write(Chunk<? extends P09183OutputRecord> items) throws Exception {
    	logger.debug("Entering write() with {} summary item(s)", items.size());

        if (items.isEmpty()) {
        	logger.debug("Chunk is empty, nothing to write");
            return;
        }

        P09183OutputRecord finalSummary = null;
        for (P09183OutputRecord s : items) {
            finalSummary = s;
        }

        try {
            String formTotalStr = String.format("%05d", finalSummary.getFormTotal());

            String formattedAmt = "$" + AMT_FORMAT.format(finalSummary.getAmountTotal());
            String paddedAmt = String.format("%15s", formattedAmt);

            String outputLine = formTotalStr + paddedAmt; // single 20-char line

            logger.info("Writing final trailer line: '{}'", outputLine);
            writer.write(outputLine);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed writing output", e);
        }
    }


    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return ExitStatus.COMPLETED;
    }
}
