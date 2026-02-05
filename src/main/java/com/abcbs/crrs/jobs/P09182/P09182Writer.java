package com.abcbs.crrs.jobs.P09182;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.ExitStatus;

@Component
public class P09182Writer implements ItemWriter<P09182InputRecord>, StepExecutionListener {

    private static final Logger logger = LogManager.getLogger(P09182Writer.class);

    private int total = 0;
    private BufferedWriter writer;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        String outputFile = stepExecution.getJobParameters().getString("outputFile");
        logger.info("Initializing P09182Writer. Output file: {}", outputFile);

        try {
            writer = Files.newBufferedWriter(Paths.get(outputFile));
            logger.debug("Output file successfully opened: {}", outputFile);

        } catch (IOException e) {
            logger.error("Cannot open output file: {}", outputFile, e);
            throw new IllegalStateException("Cannot open output file", e);
        }
    }

    @Override
    public void write(Chunk<? extends P09182InputRecord> items) {
        logger.debug("Processing {} records in chunk", items.size());
        for (P09182InputRecord rec : items) {
            try {
                int count = Integer.parseInt(rec.getFormCount().trim());
                total += count;
                logger.debug("Adding formCount {} -> Running Total: {}", rec.getFormCount(), total);
            } catch (NumberFormatException e) {
                logger.error("Invalid number in formCount: {}", rec.getFormCount(), e);
            }
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        try {
            String finalTotal = String.format("%05d", total); // 5-char numeric
            writer.write(finalTotal);
            writer.newLine();
            writer.close();
            logger.info("Final total written: {}", finalTotal);
        } catch (IOException e) {
            logger.error("Failed writing output", e);
            return ExitStatus.FAILED;
        }
        return ExitStatus.COMPLETED;
    }
}

