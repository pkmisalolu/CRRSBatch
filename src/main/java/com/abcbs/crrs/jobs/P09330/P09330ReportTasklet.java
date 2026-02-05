package com.abcbs.crrs.jobs.P09330;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.abcbs.crrs.entity.P09CashReceipt;
import com.abcbs.crrs.repository.IP09CashReceiptRepository;

/**
 * Final P09330ReportTasklet — DB-only input.
 *
 * Responsibilities:
 *  - Derive control-date cutoffs (initializeControlDates)
 *  - Stream RET receipts by receipt-type (PX01 equivalent) and accumulate
 *  - Stream UND/OFF/SPO receipts (PX02/PX03/PX04) and accumulate
 *  - Keep global accumulators via ReportWriter.accumulate(...)
 *  - Maintain per-receiptType and per-refundType temporary subtotal maps,
 *    then pass those maps to ReportWriter.bufferReceiptTypeSubtotal / bufferRefundTypeSubtotal
 *    (so ReportWriter can print them in the correct spot when rendering pages).
 *
 * Requirements (ReportWriter):
 *  - reportWriter.open(), accumulate(...), bufferReceiptTypeSubtotal(...),
 *    bufferRefundTypeSubtotal(...), close(), safeClose()
 *
 * Usage: configured via P09330Config as a step-scoped bean.
 */
public class P09330ReportTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(P09330ReportTasklet.class);

    // fixed receipt types per program map (RET buckets)
    private final List<String> receiptTypes;

    // injected collaborators
    private final ReportWriter reportWriter;
    private final IP09CashReceiptRepository cashRepo;
    private final TransactionTemplate txTemplate;

    // control dates (populated in initializeControlDates)
    private LocalDate cntrlToDate;
    private LocalDate ctdMinus15, ctdMinus1Month, ctdMinus2Months, ctdMinus3Months, ctdMinus4Months;

    // Temporary per-group subtotal buffers maintained by the tasklet and then handed to the writer.
    // Structure: receiptType -> Category -> AgeBucket -> Subtotal(count, amount)
    private final Map<String, Map<ReportWriter.Category, EnumMap<AgeBucket, Subtotal>>> receiptTypeSubtotals = new LinkedHashMap<>();
    private final Map<String, Map<ReportWriter.Category, EnumMap<AgeBucket, Subtotal>>> refundTypeSubtotals = new LinkedHashMap<>();

    // Simple holder for temp subtotal accumulation.
    static class Subtotal {
        long count;
        BigDecimal amount = BigDecimal.ZERO;

        void add(long c, BigDecimal a) {
            count += c;
            if (a != null) amount = amount.add(a);
        }
    }

    public P09330ReportTasklet(ReportWriter reportWriter,
                               IP09CashReceiptRepository cashRepo,
                               TransactionTemplate txTemplate) {
        this.reportWriter = Objects.requireNonNull(reportWriter, "reportWriter");
        this.cashRepo = Objects.requireNonNull(cashRepo, "cashRepo");
        this.txTemplate = Objects.requireNonNull(txTemplate, "txTemplate");
        this.receiptTypes = List.of("FE", "HO", "AP", "MA", "MU", "GA", "GU");
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("P09330 execute() START (DB-only). Receipt types: {}", receiptTypes);
        try {
            initializeControlDates();        // set cntrlToDate and the derived cutoffs
            reportWriter.open();

            // Process RET by receipt type (PX01)
            processRetByReceiptType();

            // Process UND/OFF/SPO (PX02, PX03, PX04)
            processUndOffSpo();

            // Hand buffered subtotals to writer so it can print them in the proper place.
            // The writer is expected to store them and flush them when building pages.
            bufferAllSubtotalsToWriter();

            // Now close writer (which will render pages using the buffered subtotals).
            reportWriter.close();
            log.info("P09330 finished writing report");
            return RepeatStatus.FINISHED;
        } catch (Exception e) {
            log.error("P09330 failed", e);
            throw e;
        } finally {
            reportWriter.safeClose();
        }
    }

    // -------------------------
    // Initialization
    // -------------------------
    private void initializeControlDates() {
        // TODO: replace with DB fetch from V_P09_CONTROL for CNTRL_TO_DATE when available.
        // Current fallback uses today for deterministic runs — replace for production parity.
        this.cntrlToDate = LocalDate.now();
        LocalDate plus1 = cntrlToDate.plusDays(1);
        this.ctdMinus15 = cntrlToDate.minusDays(15);
        this.ctdMinus1Month = plus1.minusMonths(1);
        this.ctdMinus2Months = plus1.minusMonths(2);
        this.ctdMinus3Months = plus1.minusMonths(3);
        this.ctdMinus4Months = plus1.minusMonths(4);

        log.info("Control date cutoffs set: cntrlToDate={}, ctdMinus15={}, ctdMinus1Month={}, ctdMinus2Months={}, ctdMinus3Months={}, ctdMinus4Months={}",
                cntrlToDate, ctdMinus15, ctdMinus1Month, ctdMinus2Months, ctdMinus3Months, ctdMinus4Months);
    }

    // -------------------------
    // Main processing loops
    // -------------------------
    private void processRetByReceiptType() {
        for (String rType : receiptTypes) {
            log.info("Processing RET for receiptType={}", rType);
            // Ensure we have a per-receiptType map to accumulate subtotals in memory
            receiptTypeSubtotals.putIfAbsent(rType, emptyCategoryMap());

            txTemplate.execute(status -> {
                try (Stream<P09CashReceipt> rows = cashRepo.streamByReceiptTypeNative(rType, cntrlToDate)) {
                    rows.forEach(row -> {
                        processRowAndAccumulate(row, rType, null);
                    });
                } catch (RuntimeException re) {
                    log.error("Runtime error streaming RET {}: {}", rType, re.getMessage(), re);
                    throw re;
                } catch (Exception e) {
                    log.error("Checked exception streaming RET {}: {}", rType, e.getMessage(), e);
                    throw new RuntimeException(e);
                }
                return null;
            });

            // DO NOT write subtotals now — buffer them and let the writer print at the correct location.
            // The writer will be handed the receiptTypeSubtotals map later.
        }
    }

    private void processUndOffSpo() {
        for (String refund : List.of("UND", "OFF", "SPO")) {
            log.info("Processing refundType={}", refund);
            refundTypeSubtotals.putIfAbsent(refund, emptyCategoryMap());

            txTemplate.execute(status -> {
                try (Stream<P09CashReceipt> rows = cashRepo.streamByRefundTypeNative(refund, cntrlToDate)) {
                    rows.forEach(row -> {
                        processRowAndAccumulate(row, null, refund);
                    });
                } catch (RuntimeException re) {
                    log.error("Runtime error streaming refund {}: {}", refund, re.getMessage(), re);
                    throw re;
                } catch (Exception e) {
                    log.error("Checked exception streaming refund {}: {}", refund, e.getMessage(), e);
                    throw new RuntimeException(e);
                }
                return null;
            });

            // again: buffer only
        }
    }

    // -------------------------
    // Single-row processing (accumulation into global writer + temp per-group subtotal)
    // -------------------------
    private void processRowAndAccumulate(P09CashReceipt row, String currentReceiptType, String currentRefundType) {
        try {
            if (row == null) return;

            // Extract composite-key fields from embedded id 'crId' if present
            String refundType = null;
            LocalDate cntrlDate = null;
            String cntrlNbr = null;

            try {
                var id = row.getCrId();
                if (id != null) {
                    refundType = id.getCrRefundType();
                    cntrlDate = id.getCrCntrlDate();
                    cntrlNbr = id.getCrCntrlNbr();
                }
            } catch (Throwable t) {
                log.debug("Unable to read crId fields (will try fallbacks): {}", t.toString());
            }

            // Fallbacks: use entity-level fields where reasonable
            if (cntrlDate == null) {
                try { cntrlDate = row.getCrReceivedDate(); } catch (Throwable ignore) {}
            }
            if (refundType == null) {
                try { refundType = row.getCrRemIdType(); } catch (Throwable ignore) {}
            }
            if (cntrlNbr == null) {
                try { cntrlNbr = row.getCrCheckNbr(); } catch (Throwable ignore) {}
            }

            if (cntrlDate == null) {
                log.warn("Skipping row because control-date is missing: {}", safeId(row));
                return;
            }

            BigDecimal amount = BigDecimal.ZERO;
            try { if (row.getCrReceiptBal() != null) amount = row.getCrReceiptBal(); } catch (Throwable ignore) {}

            String receiptType = "";
            try { if (row.getCrReceiptType() != null) receiptType = row.getCrReceiptType().trim(); } catch (Throwable ignore) {}

            AgeBucket bucket = AgingCalculator.bucketFor(cntrlDate, cntrlToDate,
                    ctdMinus15, ctdMinus1Month, ctdMinus2Months, ctdMinus3Months, ctdMinus4Months);

            // Map receiptType -> main Category for the table
            ReportWriter.Category category = mapReceiptTypeToCategory(receiptType);

            // Global accumulation: add to the writer (keeps overall totals)
            reportWriter.accumulate(category, bucket, 1L, amount);
            reportWriter.accumulate(ReportWriter.Category.SUBTOTAL_RETURNED, bucket, 1L, amount);

            // Per-group accumulation: if we're processing by receiptType, update its temp map
            if (currentReceiptType != null) {
                addToTempSubtotal(receiptTypeSubtotals.get(currentReceiptType), category, bucket, 1L, amount);
            }

            // If processing by refund type, update that refund temp map
            if (currentRefundType != null) {
                addToTempSubtotal(refundTypeSubtotals.get(currentRefundType), category, bucket, 1L, amount);
            }

            // BAD_ADDRESS placeholder rule (COBOL must be transcribed for parity)
            try {
                String remDaily = row.getCrRemDailyInd();
                if (remDaily != null && remDaily.trim().equalsIgnoreCase("B")) {
                    reportWriter.accumulate(ReportWriter.Category.BAD_ADDRESS, bucket, 1L, amount);
                    if (currentReceiptType != null) addToTempSubtotal(receiptTypeSubtotals.get(currentReceiptType), ReportWriter.Category.BAD_ADDRESS, bucket, 1L, amount);
                    if (currentRefundType != null) addToTempSubtotal(refundTypeSubtotals.get(currentRefundType), ReportWriter.Category.BAD_ADDRESS, bucket, 1L, amount);
                }
            } catch (Throwable ignore) {}

            // OFFSET placeholder rule example
            try {
                if ("OFF".equalsIgnoreCase(refundType) || "OFF".equalsIgnoreCase(row.getCrReasonCode())) {
                    reportWriter.accumulate(ReportWriter.Category.OFFSET, bucket, 1L, amount);
                    if (currentReceiptType != null) addToTempSubtotal(receiptTypeSubtotals.get(currentReceiptType), ReportWriter.Category.OFFSET, bucket, 1L, amount);
                    if (currentRefundType != null) addToTempSubtotal(refundTypeSubtotals.get(currentRefundType), ReportWriter.Category.OFFSET, bucket, 1L, amount);
                }
            } catch (Throwable ignore) {}

            // STOP_PAYMENT / ARKANSAS_PAYMENT / TOTAL: placeholders until COBOL rules are supplied

        } catch (Exception e) {
            log.error("Exception processing row {}: {}", safeId(row), e.getMessage(), e);
        }
    }

    // -------------------------
    // Temp subtotal helpers
    // -------------------------
    private static Map<ReportWriter.Category, EnumMap<AgeBucket, Subtotal>> emptyCategoryMap() {
        Map<ReportWriter.Category, EnumMap<AgeBucket, Subtotal>> m = new LinkedHashMap<>();
        for (ReportWriter.Category c : ReportWriter.Category.values()) {
            EnumMap<AgeBucket, Subtotal> em = new EnumMap<>(AgeBucket.class);
            for (AgeBucket b : AgeBucket.values()) em.put(b, new Subtotal());
            m.put(c, em);
        }
        return m;
    }

    private void addToTempSubtotal(Map<ReportWriter.Category, EnumMap<AgeBucket, Subtotal>> map,
                                   ReportWriter.Category category,
                                   AgeBucket bucket,
                                   long cnt,
                                   BigDecimal amt) {
        if (map == null) return;
        EnumMap<AgeBucket, Subtotal> em = map.get(category);
        if (em == null) {
            em = new EnumMap<>(AgeBucket.class);
            for (AgeBucket b : AgeBucket.values()) em.put(b, new Subtotal());
            map.put(category, em);
        }
        Subtotal s = em.get(bucket);
        if (s == null) {
            s = new Subtotal();
            em.put(bucket, s);
        }
        s.add(cnt, amt == null ? BigDecimal.ZERO : amt);
    }

    // After all streaming is done, hand the buffered subtotal maps to the writer
    private void bufferAllSubtotalsToWriter() {
        // Buffer receipt-type subtotals
        for (Map.Entry<String, Map<ReportWriter.Category, EnumMap<AgeBucket, Subtotal>>> e : receiptTypeSubtotals.entrySet()) {
            String rType = e.getKey();
            var map = e.getValue();
            // Convert Subtotal -> writer-friendly primitive map representation that writer expects.
            // We will convert into Map<ReportWriter.Category, EnumMap<AgeBucket, CountAmountDTO>>
            // CountAmountDTO is a small structure to carry counts and amounts to the writer.
            Map<ReportWriter.Category, EnumMap<AgeBucket, ReportWriter.CountAmountDTO>> conv = new LinkedHashMap<>();
            for (var catEntry : map.entrySet()) {
                EnumMap<AgeBucket, ReportWriter.CountAmountDTO> em = new EnumMap<>(AgeBucket.class);
                for (AgeBucket b : AgeBucket.values()) {
                    Subtotal s = catEntry.getValue().get(b);
                    ReportWriter.CountAmountDTO dto = new ReportWriter.CountAmountDTO(s.count, s.amount);
                    em.put(b, dto);
                }
                conv.put(catEntry.getKey(), em);
            }
            // ReportWriter must provide this method to accept buffered totals.
            reportWriter.bufferReceiptTypeSubtotal(rType, conv);
        }

        // Buffer refund-type subtotals
        for (Map.Entry<String, Map<ReportWriter.Category, EnumMap<AgeBucket, Subtotal>>> e : refundTypeSubtotals.entrySet()) {
            String refund = e.getKey();
            var map = e.getValue();
            Map<ReportWriter.Category, EnumMap<AgeBucket, ReportWriter.CountAmountDTO>> conv = new LinkedHashMap<>();
            for (var catEntry : map.entrySet()) {
                EnumMap<AgeBucket, ReportWriter.CountAmountDTO> em = new EnumMap<>(AgeBucket.class);
                for (AgeBucket b : AgeBucket.values()) {
                    Subtotal s = catEntry.getValue().get(b);
                    ReportWriter.CountAmountDTO dto = new ReportWriter.CountAmountDTO(s.count, s.amount);
                    em.put(b, dto);
                }
                conv.put(catEntry.getKey(), em);
            }
            reportWriter.bufferRefundTypeSubtotal(refund, conv);
        }
    }

    // -------------------------
    // Mapping helpers, logging
    // -------------------------
    private ReportWriter.Category mapReceiptTypeToCategory(String receiptType) {
        if (receiptType == null) return ReportWriter.Category.TOTAL;
        return switch (receiptType.trim()) {
            case "FE" -> ReportWriter.Category.PHYSICIAN;
            case "HO" -> ReportWriter.Category.HOSPITAL;
            case "AP" -> ReportWriter.Category.MEDIPAK;
            case "MA" -> ReportWriter.Category.MEDIPAK;
            case "MU", "GA", "GU" -> ReportWriter.Category.GCPS;
            default -> ReportWriter.Category.TOTAL;
        };
    }

    private String safeId(P09CashReceipt row) {
        try {
            var id = row.getCrId();
            if (id == null) return "crId-null";
            String d = id.getCrCntrlDate() == null ? "null-date" : id.getCrCntrlDate().toString();
            String n = id.getCrCntrlNbr() == null ? "null-nbr" : id.getCrCntrlNbr();
            String r = id.getCrRefundType() == null ? "null-ref" : id.getCrRefundType();
            return d + "/" + n + "/" + r;
        } catch (Throwable t) {
            return "id-unavailable";
        }
    }

    // -------------------------
    // AgingCalculator (same approximation as before)
    // -------------------------
    static class AgingCalculator {
        public static AgeBucket bucketFor(LocalDate rowDate,
                                          LocalDate cntrlToDate,
                                          LocalDate ctdMinus15,
                                          LocalDate ctdMinus1Month,
                                          LocalDate ctdMinus2Months,
                                          LocalDate ctdMinus3Months,
                                          LocalDate ctdMinus4Months) {

            if (rowDate == null || cntrlToDate == null) return AgeBucket.OVER_4;

            // 0-15 days (newest)
            if (!rowDate.isBefore(ctdMinus15.plusDays(1)) && !rowDate.isAfter(cntrlToDate)) {
                return AgeBucket.D0_15;
            }
            // 16 days - 1 month
            if (!rowDate.isBefore(ctdMinus1Month.plusDays(1)) && rowDate.isBefore(ctdMinus15.plusDays(1))) {
                return AgeBucket.D16_1M;
            }
            // 1-2 months
            if (!rowDate.isBefore(ctdMinus2Months.plusDays(1)) && rowDate.isBefore(ctdMinus1Month.plusDays(1))) {
                return AgeBucket.M1_2;
            }
            // 2-3 months
            if (!rowDate.isBefore(ctdMinus3Months.plusDays(1)) && rowDate.isBefore(ctdMinus2Months.plusDays(1))) {
                return AgeBucket.M2_3;
            }
            // 3-4 months
            if (!rowDate.isBefore(ctdMinus4Months.plusDays(1)) && rowDate.isBefore(ctdMinus3Months.plusDays(1))) {
                return AgeBucket.M3_4;
            }
            // over 4 months
            return AgeBucket.OVER_4;
        }
    }
}
