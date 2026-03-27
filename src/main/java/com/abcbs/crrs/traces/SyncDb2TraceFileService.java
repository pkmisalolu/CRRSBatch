package com.abcbs.crrs.traces;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SyncDb2TraceFileService {

    private static final Path LOG_DIR = Paths.get("/isfinpay/finpayapps/CRRS/batch/logs/");
    private static final Path TRACING_DIR = Paths.get("/isfinpay/finpayapps/tracing/");
	//private static final Path LOG_DIR = Paths.get("logs/");
    //private static final Path TRACING_DIR = Paths.get("tracing/");
    private static final Path ACTIVE_FILE = LOG_DIR.resolve("queriesDb2.txt");

    private static final DateTimeFormatter ARCHIVE_TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private BufferedWriter writer;

    // Singleton instance
    private static final SyncDb2TraceFileService INSTANCE =
            new SyncDb2TraceFileService();

    private SyncDb2TraceFileService() {
        try {
            openWriter();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize queriesDb2 writer", e);
        }
    }

    public static SyncDb2TraceFileService getInstance() {
        return INSTANCE;
    }

    /**
     * Writes user activity to active file
     */
    public synchronized void writeActivity(String activity) {

        try {

            ensureWriterOpen();

            writer.write(activity == null ? "" : activity);
            writer.newLine();
            writer.flush();

        } catch (IOException e) {
            throw new RuntimeException("Error writing to queriesDb2.txt", e);
        }
    }

    /**
     * Rotates file (called by scheduler)
     */
    public synchronized void rotateFile() {

        try {

            closeWriter();

            if (Files.exists(ACTIVE_FILE) && Files.size(ACTIVE_FILE) > 0) {

                String archiveFileName = "queriesDb2_CRRSbatch_"
                        + LocalDateTime.now().format(ARCHIVE_TS_FORMAT)
                        + ".txt";

                Path archivedFile = TRACING_DIR.resolve(archiveFileName);

                Files.move(ACTIVE_FILE,
                        archivedFile,
                        StandardCopyOption.REPLACE_EXISTING);
            }

            openWriter();

        } catch (IOException e) {
            throw new RuntimeException("Error rotating queriesDb2 file", e);
        }
    }

    /**
     * Ensures writer is open
     */
    private void ensureWriterOpen() throws IOException {

        if (writer == null) {
            openWriter();
        }

    }

    /**
     * Opens active file writer
     */
    private void openWriter() throws IOException {

        writer = Files.newBufferedWriter(
                ACTIVE_FILE,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );

    }

    /**
     * Closes current writer
     */
    private void closeWriter() throws IOException {

        if (writer != null) {

            writer.flush();
            writer.close();
            writer = null;

        }

    }

}