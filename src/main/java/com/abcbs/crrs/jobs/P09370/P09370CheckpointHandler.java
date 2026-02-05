package com.abcbs.crrs.jobs.P09370;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * COBOL checkpoint:
 *
 * IF CKPNT-COUNTER > CKPNT-FREQUENCY
 *    PERFORM 889999-CHECK-POINT THRU 889999-CHECK-POINT-EXIT
 *    MOVE 1 TO CKPNT-COUNTER
 * ELSE
 *    ADD +1 TO CKPNT-COUNTER.
 *
 * 889999-CHECK-POINT moves:
 *    CR-REFUND-TYPE -> CKPNT-REFUND-TYPE
 *    BT-BATCH-PREFIX -> CKPNT-BATCH-PREFIX
 *    BT-BATCH-DATE   -> CKPNT-BATCH-DATE (YYMMDD)
 *    BT-BATCH-SUFFIX -> CKPNT-BATCH-SUFFIX
 * and persists CKPNT-KEY = <prefix><date><suffix><refundType> after the 6-digit frequency.
 *
 * Checkpoint file layout:
 *   0..5   : frequency (PIC 9(6)) e.g., 000005
 *   6..end : last CKPNT-KEY (optional)
 */
public class P09370CheckpointHandler {

    private static final Logger log = LogManager.getLogger(P09370CheckpointHandler.class);

    private final Path ckptPath;

    private int frequency = 0;              // CKPNT-FREQUENCY
    private int counter   = Integer.MAX_VALUE;
    private String key    = "";             // CKPNT-KEY (PPPYYMMDDSSRT)

    public P09370CheckpointHandler(String checkpointFile) {
        this.ckptPath = Path.of(Objects.requireNonNull(checkpointFile, "checkpointFile"));
        loadFrequencyAndKey();
    }

    public String getCurrentKey() {
        if (key == null || key.trim().isEmpty()) {
            return "0000000000000000";  // ensures reader fetches all records
        }
        return key;
    }

    /** COBOL block executed for each record. */
    public void maybeCheckpointAndBump(String refundType, String batchPrefix, String batchDateYYMMDD, String batchSuffix) {
        if (counter > frequency) {
            doCheckpoint(refundType, batchPrefix, batchDateYYMMDD, batchSuffix);
            counter = 1; // MOVE 1 TO CKPNT-COUNTER
        } else {
            counter = (counter == Integer.MAX_VALUE) ? 1 : counter + 1; // ADD +1
        }
    }

    /** 889999-CHECK-POINT: move fields into CKPNT-* and write persisted key. */
    private void doCheckpoint(String refundType, String batchPrefix, String batchDateYYMMDD, String batchSuffix) {
        String newKey = nz(batchPrefix) + nz(batchDateYYMMDD) + nz(batchSuffix) + nz(refundType);
        writeFrequencyAndKey(newKey);
        key = newKey;
        log.info("Checkpoint written. Frequency={}, Key='{}'", frequency, key);
    }

    private void loadFrequencyAndKey() {
        if (!Files.exists(ckptPath)) {
            throw new IllegalStateException(errorMsg("Checkpoint file not found: " + ckptPath));
        }
        try (BufferedReader br = new BufferedReader(new FileReader(ckptPath.toFile()))) {
            String line = br.readLine();
            if (line == null || line.length() < 6) {
                throw new IllegalStateException(errorMsg("Invalid checkpoint record (length < 6)"));
            }
            String freqStr = line.substring(0, 6);
            if (!freqStr.chars().allMatch(Character::isDigit)) {
                throw new IllegalStateException(errorMsg("Non-numeric frequency in checkpoint file"));
            }
            frequency = Integer.parseInt(freqStr);
            if (frequency <= 0) throw new IllegalStateException(errorMsg("Frequency must be > 0"));

            key = (line.length() > 6) ? line.substring(6).trim() : "0000000000000000";
            if (key.isEmpty()) key = "0000000000000000"; 
            counter = Integer.MAX_VALUE;
        } catch (Exception ex) {
            throw new IllegalStateException(errorMsg(ex.getMessage()), ex);
        }
    }

    private void writeFrequencyAndKey(String newKey) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(ckptPath.toFile(), false))) {
            String freq = String.format("%06d", frequency);
            bw.write(freq + (newKey == null ? "" : newKey));
            bw.flush();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to write checkpoint file " + ckptPath, ex);
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String errorMsg(String lead) {
        return (lead == null ? "" : lead) + """
                
                ** CHECK POINT/RESTART FILE ERROR **
                ** NO RECORDS WERE FOUND IN THE CHECK POINT FREQUENCY FILE; PLEASE INSERT RECORD
                ** RECORD LAYOUT:
                **    FREQUENCY - SIX DIGITS
                **    FILLER    - SPACES
                ** EXAMPLE: 000005
                """;
    }
}