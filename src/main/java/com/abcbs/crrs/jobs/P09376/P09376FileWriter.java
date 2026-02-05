package com.abcbs.crrs.jobs.P09376;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.repeat.RepeatStatus;

import com.abcbs.crrs.repository.IP09CashReceiptRepository;

/**
 * Single-file writer for P09376: writes only the single combined LABEL file.
 * Constructor simplified to accept only the paths/resources actually used.
 */
public class P09376FileWriter implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(P09376FileWriter.class);

    // ===== Injected IO (only what we need)
    private final String labelPath;
    private final String controlPath;
    private final FlatFileItemReader<CheckPointRecord> controlReader;
    private final IP09CashReceiptRepository cashRepo;

    // ===== Single output file
    private BufferedWriter label;

    // ===== Work state
    private int ckpntFrequency = 0;
    private int ckpntCounter = 0;

    private List<P09376DailyRemittanceView> buffer = new ArrayList<>();
    private int bufferIndex = 0;
    private String checkpointKey = "";
    
 // add imports: java.time.LocalDate;
    private LocalDate lastDate = LocalDate.of(1900,1,1);
    private String lastNbr = "";
    private String lastType = "";
    // defensive previous-first-row
    private LocalDate prevFirstDate = null;
    private String prevFirstNbr = null;
    private String prevFirstType = null;


    private long totalWrote = 0;
    private long totalExceptions = 0;
    private boolean opened = false;

    private int labelRoundRobinCounter = 1;
    private static final String C_LINE_UP_LINE;
    private static final String C_BLANK_LINE;

    // 3-up buffers (preserve names for parity with original algorithm)
    private String bufRefund1, bufRefund2, bufRefund3;
    private String bufCtrl1 = null, bufCtrl2 = null, bufCtrl3 = null;
    private String bufW11 = null, bufW12 = null, bufW13 = null;
    private String bufW21 = null, bufW22 = null, bufW23 = null;
    private String bufW31 = null, bufW32 = null, bufW33 = null;
    private String bufW41 = null, bufW42 = null, bufW43 = null;

    static {
        StringBuilder sb = new StringBuilder(" ");
        sb.append("X".repeat(30)).append(" ".repeat(6))
          .append("X".repeat(30)).append(" ".repeat(6))
          .append("X".repeat(30)).append(" ".repeat(6));
        C_LINE_UP_LINE = sb.toString();
        C_BLANK_LINE = " " + " ".repeat(108);
    }

    /**
     * Simplified constructor: only the parameters required by the new single-file writer.
     *
     * @param labelPath      path to the single combined output label file
     * @param controlPath    control file path parameter (used by the reader)
     * @param controlReader  injected step-scoped FlatFileItemReader for the control file
     * @param cashRepo       repository used to fetch and update remittance records
     */
    public P09376FileWriter(String labelPath,
                            String controlPath,
                            FlatFileItemReader<CheckPointRecord> controlReader,
                            IP09CashReceiptRepository cashRepo) {
        log.info("Constructor: labelPath={}, controlPath={}", labelPath, controlPath);
        this.labelPath = labelPath;
        this.controlPath = controlPath;
        this.controlReader = controlReader;
        this.cashRepo = cashRepo;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("Tasklet execute() START");
        manualOpen(new ExecutionContext());
        log.debug("After open(): buffer.size={}, checkpointKey={}", buffer.size(), checkpointKey);

        while (bufferIndex < buffer.size()) {
            P09376DailyRemittanceView view = buffer.get(bufferIndex++);
            log.debug("Loop: bufferIndex={} view={}", bufferIndex, view);
            try {
                processRecord(view);
            } catch (Exception e) {
                totalExceptions++;
                log.error("Exception processing record {}: {}", view, e.getMessage(), e);
            }

            ckpntCounter++;
            if (ckpntFrequency > 0 && ckpntCounter >= ckpntFrequency) {
                log.debug("Checkpoint threshold reached: ckpntCounter={}, ckpntFrequency={}", ckpntCounter, ckpntFrequency);
                setCheckpointFromView(view);
                fetchNextDBChunk();
                ckpntCounter = 0;
                bufferIndex = 0;
                log.debug("After fetchNextDBChunk(): buffer.size={}, new checkpointKey={}", buffer.size());
            }
        }

        manualClose();
        log.info("Tasklet execute() END, totalWrote={}, totalExceptions={}", totalWrote, totalExceptions);
        return RepeatStatus.FINISHED;
    }

    public void manualOpen(ExecutionContext ctx) throws ItemStreamException {
        try {
            log.info("Opening P09376FileWriter (single label): labelPath={}", labelPath);
            proc000100Initialize();
            log.info("Checkpoint frequency set to {}", ckpntFrequency);

            // Initial DB fetch
            fetchNextDBChunk();
            log.debug("Initial fetchNextDBChunk(): buffer.size={}, checkpointKey={}", buffer.size(), checkpointKey);

            opened = true;
        } catch (Exception e) {
            log.error("Initialization error", e);
            throw new ItemStreamException("P09376 failed", e);
        }
    }

    public void manualClose() throws ItemStreamException {
        log.info("Closing P09376FileWriter, labelRoundRobinCounter={}", labelRoundRobinCounter);

        try {
            // If there's a partially filled cycle, write it to keep output consistent
            if (labelRoundRobinCounter != 1) {
                writeCombinedLabelBlock();
            }
            proc000250UpdateRemailInd();
            proc000300Finalization();
        } catch (Exception e) {
            log.error("Finalization error", e);
            throw new ItemStreamException("P09376 close failed", e);
        } finally {
            safeClose(label);
            opened = false;
            log.info("Closed P09376FileWriter successfully");
        }
    }

    private void proc000100Initialize() throws Exception {
        log.info("000100-INITIALIZE: opening label file and reading control frequency.");

        // open single main label file
        label = newBuffered(labelPath);
        log.debug("Label writer initialized");

        // Write the header in label file: pattern block (preserve original)
        for (int rep = 0; rep < 3; rep++) {
            for (int i = 0; i < 5; i++) {
                label.write(C_LINE_UP_LINE);
                label.newLine();
            }
            if (rep != 2) {
                label.write(C_BLANK_LINE);
                label.newLine();
            }
        }
        log.debug("Header block written in label file");

        // Read checkpoint frequency via injected reader
        if (controlPath == null || controlPath.isBlank()) {
            log.warn("No control file; checkpoint disabled.");
            ckpntFrequency = 0;
        } else {
            controlReader.open(new ExecutionContext());
            CheckPointRecord rec = controlReader.read();
            controlReader.close();
            if (rec == null) {
                proc888999InvalidFile("CHECK-POINT file empty");
            } else {
                String freq = rec.getCheckPointFrequency() == null ? "" : rec.getCheckPointFrequency().trim();
                if (freq.chars().allMatch(Character::isDigit)) {
                    ckpntFrequency = freq.isEmpty() ? 0 : Integer.parseInt(freq);
                } else {
                    proc888999InvalidFile("Non-numeric CONTROL-FREQUENCY: '" + freq + "'");
                }
            }
        }
        log.info("Checkpoint frequency: {}", ckpntFrequency);
    }

    private void processRecord(P09376DailyRemittanceView view) throws IOException {
        log.debug("processRecord START for view: {}", view);

        String w1 = isBlank(view.getCrRemAddressee()) ? view.getCrRemittorName() : view.getCrRemAddressee();
        String w2, w3, city, state, zip5, zip4;

        if (isBlank(view.getCrRemAddress1())) {
            // use CHK address
            w2 = view.getCrChkAddress1();
            w3 = view.getCrChkAddress2();
            city = view.getCrChkCity();
            state = view.getCrChkState();
            zip5 = view.getCrChkZip5();
            zip4 = view.getCrChkZip4();
            log.debug("Using CHK address: {}, {}, {}, {}", w2, w3, city, state);
        } else {
            // use REM address
            w2 = view.getCrRemAddress1();
            w3 = view.getCrRemAddress2();
            city = view.getCrRemCity();
            state = view.getCrRemState();
            zip5 = view.getCrRemZip5();
            zip4 = view.getCrRemZip4();
            log.debug("Using REM address: {}, {}, {}, {}", w2, w3, city, state);
        }

        // Build W4 label city/state/zip
        String w4 = buildFixedCityStateZip(city, state, zip5, zip4);

        String wRefundType = view.getCrRefundType();
        String wControlDate = view.getCrCntrlDate().format(DateTimeFormatter.ofPattern("MM/dd/yy"));
        String wControlNbr = view.getCrCntrlNbr();

        if (isBlank(w2)) {
            w2 = w3;
            w3 = w4;
            w4 = "";
        } else if (isBlank(w3)) {
            w3 = w4;
            w4 = "";
        }

        // Three-slot buffering logic preserved; when slot 3 filled -> write combined block
        switch (labelRoundRobinCounter) {
            case 1:
                bufRefund1 = wRefundType;
                bufCtrl1 = padRight(wControlDate, 18) + padRight(wControlNbr, 18);
                bufW11 = w1;
                bufW21 = w2;
                bufW31 = w3;
                bufW41 = w4;
                break;
            case 2:
                bufRefund2 = wRefundType;
                bufCtrl2 = padRight(wControlDate, 15) + padRight(wControlNbr, 15);
                bufW12 = w1;
                bufW22 = w2;
                bufW32 = w3;
                bufW42 = w4;
                break;
            case 3:
                bufRefund3 = wRefundType;
                bufCtrl3 = padRight(wControlDate, 15) + padRight(wControlNbr, 15);
                bufW13 = w1;
                bufW23 = w2;
                bufW33 = w3;
                bufW43 = w4;
                // full 3-up -> write combined block to the single label file
                writeCombinedLabelBlock();
                // clear buffers
                bufRefund1 = bufRefund2 = bufRefund3 = "";
                bufCtrl1 = bufCtrl2 = bufCtrl3 = "";
                bufW11 = bufW12 = bufW13 = "";
                bufW21 = bufW22 = bufW23 = "";
                bufW31 = bufW32 = bufW33 = "";
                bufW41 = bufW42 = bufW43 = "";
                break;
            default:
                // fallback: treat as slot1
                bufRefund1 = wRefundType;
                bufCtrl1 = padRight(wControlDate, 18) + padRight(wControlNbr, 18);
                bufW11 = w1;
                bufW21 = w2;
                bufW31 = w3;
                bufW41 = w4;
                break;
        }

        labelRoundRobinCounter++;
        if (labelRoundRobinCounter > 3) labelRoundRobinCounter = 1;
    }

    private void writeCombinedLabelBlock() throws IOException {
        log.info("Writing combined label block for refunds: {},{},{}", bufRefund1, bufRefund2, bufRefund3);
        try {
            label.write(buildLabelRecordLine("", "", ""));
            label.newLine();
            label.write(buildLabelRecordLine("", "", ""));
            label.newLine();
            label.write(buildLabelRecordLine(bufRefund1, bufRefund2, bufRefund3));
            label.newLine();
            label.write(buildLabelRecordLine(bufCtrl1, bufCtrl2, bufCtrl3));
            label.newLine();
            label.write(buildLabelRecordLine(bufW11, bufW12, bufW13));
            label.newLine();
            label.write(buildLabelRecordLine("", "", ""));
            label.newLine();
            label.write(buildLabelRecordLine("", "", ""));
            label.newLine();
            label.write(buildLabelRecordLine("", "", ""));
            label.newLine();
            label.write(buildLabelRecordLine(bufW11, bufW12, bufW13));
            label.newLine();
            label.write(buildLabelRecordLine(bufW21, bufW22, bufW23));
            label.newLine();
            label.write(buildLabelRecordLine(bufW31, bufW32, bufW33));
            label.newLine();
            label.write(buildLabelRecordLine(bufW41, bufW42, bufW43));
            label.newLine();
            label.write(buildLabelRecordLine("", "", ""));
            label.newLine();
            totalWrote++;
        } catch (IOException e) {
            log.error("Error writing combined label block", e);
            throw e;
        }
    }

    private void proc000250UpdateRemailInd() {
        try {
            int updated = cashRepo.clearDailyRemittanceFlag();
            if (updated > 0) {
                log.info("Remail indicator reset for {} records", updated);
            } else {
                log.warn("Remail indicator update attempted, but no records were affected.");
            }
        } catch (Exception e) {
            totalExceptions++;
            log.error("Failed to update remail ind, err={}", e.getMessage(), e);
        }
    }

    private void proc888999InvalidFile(String msg) {
        String full = "<<<<ERROR>>>> " + msg;
        log.error(full);
        throw new IllegalStateException(full);
    }

//    private void fetchNextDBChunk() {
//        log.debug("fetchNextDBChunk START with checkpointKey = '{}'", checkpointKey);
//        buffer = cashRepo.findDailyRemittances(checkpointKey);
//        bufferIndex = 0;
//        log.debug("fetchNextDBChunk: fetched {} records", buffer.size());
//    }
    
    
    private void fetchNextDBChunk() {
        log.debug("fetchNextDBChunk START with lastDate={}, lastNbr={}, lastType={}", lastDate, lastNbr, lastType);
        List<P09376DailyRemittanceView> fetched = cashRepo.findDailyRemittancesAfter(lastDate, lastNbr, lastType);

        if (fetched == null || fetched.isEmpty()) {
            buffer = new ArrayList<>();
            bufferIndex = 0;
            log.info("No more records returned by DB (fetched 0). Stopping processing.");
            return;
        }

        // defensive: log first/last keys
        P09376DailyRemittanceView first = fetched.get(0);
        P09376DailyRemittanceView last  = fetched.get(fetched.size()-1);
        log.info("Fetched {} records. firstKey={}{}{}, lastKey={}{}{}", 
            fetched.size(),
            first.getCrCntrlDate(), "|", first.getCrCntrlNbr(), "|", first.getCrRefundType(),
            last.getCrCntrlDate(), "|", last.getCrCntrlNbr(), "|", last.getCrRefundType()
        );

        // If the first returned row equals the previous batch's first row => suspicious repeat -> stop
        if (prevFirstDate != null && prevFirstDate.equals(first.getCrCntrlDate())
            && prevFirstNbr != null && prevFirstNbr.equals(first.getCrCntrlNbr())
            && prevFirstType != null && prevFirstType.equals(first.getCrRefundType())) {
            log.warn("DB returned a batch whose first record equals the previous batch's first record -> stopping to avoid infinite loop");
            buffer = new ArrayList<>();
            bufferIndex = 0;
            return;
        }

        // set prevFirst to current first for next guard
        prevFirstDate = first.getCrCntrlDate();
        prevFirstNbr  = first.getCrCntrlNbr();
        prevFirstType = first.getCrRefundType();

        buffer = fetched;
        bufferIndex = 0;
        log.debug("fetchNextDBChunk: fetched {} records", buffer.size());
    }

    

//    private String buildCheckpointKeyFromView(DailyRemittanceView v) {
//        return v.getCrCntrlDate().toString().replace("-", "") + v.getCrCntrlNbr() + v.getCrRefundType();
//    }

 // replace buildCheckpointKeyFromView(view) with:
    private void setCheckpointFromView(P09376DailyRemittanceView v) {
        this.lastDate = v.getCrCntrlDate();
        this.lastNbr  = v.getCrCntrlNbr();
        this.lastType = v.getCrRefundType();
        log.debug("Checkpoint updated to lastDate={}, lastNbr={}, lastType={}", lastDate, lastNbr, lastType);
    }


    
    
    private void proc000300Finalization() throws IOException {
        if (!opened) {
            log.warn("Finalization called but writer was not opened");
            return;
        }
        try {
            if (label != null) label.flush();
            log.info("P09376 finalized: wrote={}, exceptions={}", totalWrote, totalExceptions);
        } catch (IOException e) {
            log.error("IOException in finalization", e);
            throw e;
        }
    }

    private static BufferedWriter newBuffered(String path) throws IOException {
        File f = new File(path);
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, false), StandardCharsets.US_ASCII));
    }

    private static void safeClose(Closeable c) {
        try {
            if (c != null) c.close();
        } catch (Exception ignore) {
        }
    }

    private String buildLabelRecordLine(String l1, String l2, String l3) {
        if (l1 == null) l1 = "";
        if (l2 == null) l2 = "";
        if (l3 == null) l3 = "";
        l1 = l1.length() > 36 ? l1.substring(0, 36) : String.format("%-36s", l1);
        l2 = l2.length() > 36 ? l2.substring(0, 36) : String.format("%-36s", l2);
        l3 = l3.length() > 36 ? l3.substring(0, 36) : String.format("%-36s", l3);
        return " " + l1 + l2 + l3;
    }

    private String buildFixedCityStateZip(String city, String state, String zip5, String zip4) {
        return padRight(city, 15) + "  " + padRight(state, 2) + "  " + padRight(zip5, 5) + "-" + padRight(zip4, 4);
    }

    private String padRight(String value, int length) {
        if (value == null) value = "";
        return String.format("%-" + length + "s", value);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
