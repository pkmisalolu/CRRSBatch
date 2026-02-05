package com.abcbs.crrs.jobs.P09325;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SummaryReportWriter implements AutoCloseable {

    private BufferedWriter w;
    private static final int LINE_WIDTH = 133;
    private static final int PAGE_LEN = 55;

    private int page = 0, line = 0;

    private static final DecimalFormat AMT = new DecimalFormat("$###,###,##0.00");
    private static final DecimalFormat CNT = new DecimalFormat("###,###,##0");

    // Alignment pattern verified from COBOL output (p09325_summ_rpt.txt)
    private static final String GAP1 = "       ";  // 7 spaces between BEGIN and RECEIVED
    private static final String GAP2 = "        "; // 8 spaces between RECEIVED and FORWARD
    private static final String GAP3 = "        "; // 8 spaces between FORWARD and ENDING

    private static final String ROW_FMT =
        " %-3s %10s %15s" + GAP1 +
               "%10s %15s" + GAP2 +
               "%10s %15s" + GAP3 +
               "%10s %15s";

    public void open(String outPath, LocalDateTime ts) {
        try {
            w = Files.newBufferedWriter(Paths.get(outPath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeControllerSummary(List<P09325SummaryView> rows) throws IOException {
        long gBeg = 0, gRec = 0, gFwd = 0, gEnd = 0;
        BigDecimal gBegAmt = Z(), gRecAmt = Z(), gFwdAmt = Z(), gEndAmt = Z();

        header();
        write(" CONTROLLER SUMMARY:");
        write("      AREA                 BEGINNING INVENTORY            RECEIVED                 FORWARD                  ENDING INVENTORY");
        write("");

        for (P09325SummaryView r : rows) {
            ensureBody();
            write(String.format(
                ROW_FMT,
                r.getArea(),
                cnt10(r.getBeginCnt()), amt15(r.getBeginAmt()),
                cnt10(r.getRecvCnt()),  amt15(r.getRecvAmt()),
                cnt10(r.getFwdCnt()),   amt15(r.getFwdAmt()),
                cnt10(r.getEndCnt()),   amt15(r.getEndAmt())
            ));

            gBeg += r.getBeginCnt();  gBegAmt = gBegAmt.add(nvl(r.getBeginAmt()));
            gRec += r.getRecvCnt();   gRecAmt = gRecAmt.add(nvl(r.getRecvAmt()));
            gFwd += r.getFwdCnt();    gFwdAmt = gFwdAmt.add(nvl(r.getFwdAmt()));
            gEnd += r.getEndCnt();    gEndAmt = gEndAmt.add(nvl(r.getEndAmt()));
        }

        write("");
        write(String.format(
            ROW_FMT,
            "TOTAL",
            cnt10(gBeg),  amt15(gBegAmt),
            cnt10(gRec),  amt15(gRecAmt),
            cnt10(gFwd),  amt15(gFwdAmt),
            cnt10(gEnd),  amt15(gEndAmt)
        ));
    }

    public void noteUpdate(int n) throws IOException {
        write("");
        write(" SUMMARY ROLL-FORWARD UPDATED ROWS: " + n);
    }

    public void noteUpdateSkipped() throws IOException {
        write("");
        write(" SUMMARY ROLL-FORWARD SKIPPED");
    }

    public void closeB() throws IOException {
        footer("END OF P09325-B REPORT");
        w.close();
    }

    private void header() throws IOException {
        page++;
        line = 0;
        write(String.format(
            " REPORT #  P09325-B                             ARKANSAS BLUE CROSS AND BLUE SHIELD                                        PAGE     %d",
            page));
        String rd = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yy"));
        String rt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("H:mm:ss"));
        write(String.format(
            " RUN DATE: %s                              FINANCIAL ROUTING LOCATION REPORT                                 RUN TIME: %s",
            rd, rt));
        write("");
    }

    private void ensureBody() throws IOException {
        if (line >= PAGE_LEN) header();
    }

    private void footer(String msg) throws IOException {
        String border = repeat('*', 52) + "  " + msg + "  " + repeat('*', 54 - msg.length());
        write(border);
    }

    private static BigDecimal Z() { return BigDecimal.ZERO; }
    private static BigDecimal nvl(BigDecimal b) { return b == null ? Z() : b; }

    private static String cnt10(long n) {
        String s = CNT.format(n);
        return s.length() >= 10 ? s.substring(0, 10) : " ".repeat(10 - s.length()) + s;
    }

    private static String amt15(BigDecimal b) {
        String s = AMT.format(nvl(b));
        return s.length() >= 15 ? s.substring(0, 15) : " ".repeat(15 - s.length()) + s;
    }

    private void write(String s) throws IOException {
        if (s.length() > LINE_WIDTH)
            s = s.substring(0, LINE_WIDTH);
        else if (s.length() < LINE_WIDTH)
            s = s + " ".repeat(LINE_WIDTH - s.length());
        w.write(s);
        w.newLine();
        line++;
    }

    private static String repeat(char c, int n) { return String.valueOf(c).repeat(n); }

    @Override
    public void close() throws IOException { /* not used */ }
}
