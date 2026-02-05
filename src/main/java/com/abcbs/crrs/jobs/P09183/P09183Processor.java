package com.abcbs.crrs.jobs.P09183;


import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.math.BigDecimal;

@Component
public class P09183Processor implements ItemProcessor<P09183InputRecord, P09183OutputRecord> {
    private static final Logger logger = LogManager.getLogger(P09183Processor.class);
    private long formTotal = 0;
    private BigDecimal amountTotal = BigDecimal.ZERO;

    @Override
    public P09183OutputRecord process(P09183InputRecord item) {
        try {
            formTotal += Integer.parseInt(item.getFormCount().trim());
            // Parse PIC 9(9)V99 (11 chars: 9 digits + implied 2 decimal places)
            String numStr = item.getControlledAmtNumeric().trim();
            BigDecimal amount = new BigDecimal(numStr).movePointLeft(2);
            amountTotal = amountTotal.add(amount);
            
            logger.debug("Processed: form={} amt={} → totals: {}/{}", item.getFormCount(), numStr, formTotal, amountTotal);
        } catch (NumberFormatException e) {
            logger.error("Invalid data in record: {}", item, e);
        }
        return new P09183OutputRecord(formTotal, amountTotal);
    }
}
