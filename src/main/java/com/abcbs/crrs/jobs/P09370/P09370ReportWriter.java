package com.abcbs.crrs.jobs.P09370;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemWriter;

import com.abcbs.crrs.entity.P09Batch;

public class P09370ReportWriter implements ItemWriter<P09Batch>, ItemStream {

    private static final Logger log = LogManager.getLogger(P09370ReportWriter.class);

    private static final int LINES_PER_PAGE = 55;
    private static final DecimalFormat USD =
            new DecimalFormat("$#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM/dd/yy");

    private final String reportPath;
    private final P09370CheckpointHandler ckpt;

    private BufferedWriter out;
    private int page = 0;
    private int lineOnPage = 0;

    public P09370ReportWriter(String reportPath, P09370CheckpointHandler ckpt) {
        this.reportPath = reportPath;
        this.ckpt = ckpt;
    }

    @BeforeStep
    public void before(StepExecution stepExecution) { }

    @AfterStep
    public ExitStatus after(StepExecution stepExecution) {
        try { close(); } catch (ItemStreamException e) { log.error("Close failed", e); }
        return stepExecution.getExitStatus();
    }

    @Override
    public void open(org.springframework.batch.item.ExecutionContext ctx) throws ItemStreamException {
        try {
            out = new BufferedWriter(new FileWriter(reportPath));
            newPage();
        } catch (IOException e) {
            throw new ItemStreamException(e);
        }
    }

    @Override public void update(org.springframework.batch.item.ExecutionContext ctx) { }

    @Override
    public void close() throws ItemStreamException {
        if (out == null) return;
        try {
            out.newLine();
            out.write("****************************************************  END OF P09370-A REPORT  ******************************************************");
            out.newLine();
            out.flush();
            out.close();
        } catch (IOException e) {
            throw new ItemStreamException(e);
        } finally {
            out = null;
        }
    }

    @Override
    public void write(Chunk<? extends P09Batch> chunk) throws Exception {
        for (P09Batch r : chunk) {
            if (lineOnPage >= LINES_PER_PAGE) newPage();

            final String refundType  = fixLen(nz(r.getBId().getCrRefundType()), 3);
            final String controlDate = fmtDate(r.getBtControlDate());
            final String batchNum    = buildBatchNumber(nz(r.getBId().getBtBatchPrefix()),
                                                        nz(r.getBId().getBtBatchDate()),
                                                        nz(r.getBId().getBtBatchSuffix()));
            final String amount      = fmtAmount(r.getBtBatchAmt());
            final String count       = fmtCount(r.getBtBatchCnt());  // <-- works for Short/Integer
            final String recvDate    = fmtDate(r.getBtReceivedDate());

            out.write(String.format("  %-3s      %-8s    %-13s  %15s  %5s     %-8s%n",
                    refundType, controlDate, batchNum, amount, count, recvDate));
            lineOnPage++;

            ckpt.maybeCheckpointAndBump(
                    r.getBId().getCrRefundType(),
                    r.getBId().getBtBatchPrefix(),
                    nz(r.getBId().getBtBatchDate()),
                    r.getBId().getBtBatchSuffix());
        }
    }

    private void newPage() throws IOException {
        page++;
        lineOnPage = 0;
        printHeader();
    }

    private void printHeader() throws IOException {
        final String date = LocalDate.now().format(MDY);
        final String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        out.write(String.format(
                "REPORT #  P09370-A                             ARKANSAS BLUE CROSS AND BLUE SHIELD                                 PAGE: %8d%n",
                page));
        out.write(String.format(
                "RUN DATE: %s                                 UNPOSTED CASH RECEIPT RECORDS                               RUN TIME: %s%n",
                date, time));
        out.write(" \n");
        out.write("REFUND    CONTROL         BATCH         BATCH        BATCH    RECEIVED\n");
        out.write("  TYPE       DATE         NUMBER        AMOUNT        COUNT      DATE\n");
        out.write("------    --------    -------------  ---------------  -----     --------\n");
        lineOnPage = 5;
    }

    // ---- formatting helpers ----

    private static String fmtDate(java.time.LocalDate d) {
        return d == null ? "        " : d.format(MDY);
    }

    private static String buildBatchNumber(String prefix, String yymmdd6, String suffix) {
        return (fixLen(prefix, 3) + " " + fmtYYMMDD6(yymmdd6) + " " + fixLen(suffix, 2)).trim();
    }

    private static String fmtYYMMDD6(String yymmdd) {
        if (yymmdd == null || yymmdd.length() < 6) return "      ";
        String yy = yymmdd.substring(0, 2);
        String mm = yymmdd.substring(2, 4);
        String dd = yymmdd.substring(4, 6);
        return mm + dd + yy;
    }

    private static String fmtAmount(BigDecimal amt) {
        BigDecimal v = (amt == null) ? BigDecimal.ZERO : amt;
        synchronized (USD) {
            return String.format("%15s", USD.format(v));
        }
    }

    // ACCEPTS Short/Integer/etc.
    private static String fmtCount(Number cnt) {
        int v = (cnt == null) ? 0 : cnt.intValue();
        return String.format("%05d", v);
    }

    private static String fixLen(String s, int n) {
        String v = s == null ? "" : s;
        if (v.length() >= n) return v.substring(0, n);
        return v + " ".repeat(n - v.length());
    }

    private static String nz(String s) { return s == null ? "" : s; }
}