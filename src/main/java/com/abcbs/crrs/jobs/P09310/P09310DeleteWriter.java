package com.abcbs.crrs.jobs.P09310;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.abcbs.crrs.repository.IP09BatchRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@Component
public class P09310DeleteWriter implements ItemWriter<List<P09310OutputRecord>> {
	
	@PersistenceContext
    private EntityManager em;

	@Autowired
    private IP09BatchRepository repo;
    
    private static final Logger logger = LogManager.getLogger(P09310DeleteWriter.class);

    @Override
    @Transactional
    public void write(Chunk<? extends List<P09310OutputRecord>> chunk) throws Exception {
        logger.info("DeleteWriter invoked with {} groups", chunk.size());

        // Track distinct keys we already deleted
        Set<String> seenKeys = new HashSet<>();

        for (List<P09310OutputRecord> group : chunk) {
            if (group == null || group.isEmpty()) {
                continue;
            }

            for (P09310OutputRecord rec : group) {
                String refundType = rec.getCrRefundType();
                if ("OFF".equalsIgnoreCase(refundType)) {
                    continue; // skip OFF
                }

                String key = refundType + "|" 
                           + rec.getBtBatchPrefix() + "|" 
                           + rec.getBtBatchDate() + "|" 
                           + rec.getBtBatchSuffix();
                
                if (seenKeys.add(key)) { // first time we see this combination
                    int n = repo.deleteByBatchAndRefund(
                        rec.getBtBatchPrefix(),
                        rec.getBtBatchDate(),
                        rec.getBtBatchSuffix(),
                        refundType
                    );
                    if(n>0) {
                    	logger.info("Deleted one suspense record for group {} / {} {} {}",
                                refundType,
                                rec.getBtBatchPrefix(),
                                rec.getBtBatchDate(),
                                rec.getBtBatchSuffix()
                            );
                    }else {
                    	logger.info("Record deletion failed for group {} / {} {} {}",
                                refundType,
                                rec.getBtBatchPrefix(),
                                rec.getBtBatchDate(),
                                rec.getBtBatchSuffix()
                            );
                    }
                    
                }
            }
        }
    }


}
