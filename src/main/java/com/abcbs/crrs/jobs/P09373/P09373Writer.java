package com.abcbs.crrs.jobs.P09373;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import com.abcbs.crrs.repository.IP09BatchRepository;
import com.abcbs.crrs.repository.IP09SuspenseRepository;

import jakarta.transaction.Transactional;

@Component
public class P09373Writer implements ItemWriter<P09373InputRecord> {

	private static final Logger log = LogManager.getLogger(P09373Writer.class);

	private final IP09BatchRepository batchRepository;
	private final IP09SuspenseRepository suspenseRepository;

	public P09373Writer(IP09BatchRepository repository, IP09SuspenseRepository suspenseRepository) {
		this.batchRepository = repository;
		this.suspenseRepository = suspenseRepository;
	}

	@Override
	@Transactional
	public void write(Chunk<? extends P09373InputRecord> chunk) {
		log.info("Starting write operation for {} records", chunk.size());
		// Track distinct keys we already deleted
		Set<String> seenKeys = new HashSet<>();
		int deletedCount = 0;
		int notFoundCount = 0;

		for (P09373InputRecord rec : chunk) {
			try {
				if (rec == null) {
					log.warn("Encountered null record in chunk. Skipping...");
					continue;
				}

				/*
				 * BatchPK pk = new BatchPK( Utility.safeTrimUpper(rec.getRefundType()),
				 * Utility.safeTrimUpper(rec.getBatchPrefix()),
				 * Utility.safeTrimUpper(rec.getBatchDate()),
				 * Utility.safeTrimUpper(rec.getBatchSuffix()) );
				 */
				String key = rec.getRefundType() + "|" + rec.getBatchPrefix() + "|" + rec.getBatchDate() + "|"
						+ rec.getBatchSuffix();
				log.debug("Processing record with key: {}", key);
				if (seenKeys.add(key)) { // first time we see this combination
					int n = suspenseRepository.deleteByBatchAndRefundSuspense(rec.getBatchPrefix(), rec.getBatchDate(),
							rec.getBatchSuffix(), rec.getRefundType());
					if (n > 0) {
						log.info("Deleted Suspense record with key: {}", key);
					} else {
						log.info("Record deletion failed with key: {}", key);
					}
					int n1 = batchRepository.deleteByBatchAndRefund(rec.getBatchPrefix(), rec.getBatchDate(),
							rec.getBatchSuffix(), rec.getRefundType());
					if (n1 > 0) {
						deletedCount++;
						log.info("Deleted batch record with key: {}", key);
					} else {
						notFoundCount++;
						log.info("Record deletion failed with key: {}", key);
					}

				}
				/*
				 * if (repository.existsById(pk)) { repository.deleteById(pk); deletedCount++;
				 * log.info("Deleted batch with key: {}", pk); } else { notFoundCount++;
				 * log.warn("No matching batch found for key: {}", pk); }
				 */
			} catch (Exception e) {
				log.error("Failed to process record: {}", rec, e);
				throw new RuntimeException("Error deleting batch record for input: " + rec, e);
			}
		}

		log.info("Completed write operation. Deleted: {}, Not Found: {}", deletedCount, notFoundCount);
	}

}
