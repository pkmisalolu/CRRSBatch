package com.abcbs.crrs.jobs.P09373;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import com.abcbs.crrs.entity.BatchPK;
import com.abcbs.crrs.repository.IP09BatchRepository;
import com.abcbs.crrs.utilities.Utility;

import jakarta.transaction.Transactional;

@Component
public class P09373Writer implements ItemWriter<P09373InputRecord> {

    private static final Logger log = LogManager.getLogger(P09373Writer.class);

    private final IP09BatchRepository repository;

    public P09373Writer(IP09BatchRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void write(Chunk<? extends P09373InputRecord> chunk) {
        log.info("Starting write operation for {} records", chunk.size());

        int deletedCount = 0;
        int notFoundCount = 0;

        for (P09373InputRecord rec : chunk) {
            try {
                if (rec == null) {
                    log.warn("Encountered null record in chunk. Skipping...");
                    continue;
                }

                BatchPK pk = new BatchPK(
                        Utility.safeTrimUpper(rec.getRefundType()),
                        Utility.safeTrimUpper(rec.getBatchPrefix()),
                        Utility.safeTrimUpper(rec.getBatchDate()),
                        Utility.safeTrimUpper(rec.getBatchSuffix())
                );

                log.debug("Processing record with key: {}", pk);

                if (repository.existsById(pk)) {
                    repository.deleteById(pk);
                    deletedCount++;
                    log.info("Deleted batch with key: {}", pk);
                } else {
                    notFoundCount++;
                    log.warn("No matching batch found for key: {}", pk);
                }

            } catch (Exception e) {
                log.error("Failed to process record: {}", rec, e);
                throw new RuntimeException("Error deleting batch record for input: " + rec, e);
            }
        }

        log.info("Completed write operation. Deleted: {}, Not Found: {}", deletedCount, notFoundCount);
    }
    
}
