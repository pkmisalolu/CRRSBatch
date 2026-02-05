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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RoutingReportWriter implements AutoCloseable {
    private BufferedWriter w;
    private static final int LINE = 133;
    private static final int TITLE_COL = 48; // title must start at 1-based col 48

    private int page = 0, line = 0;
    private String curArea, curClerk;
    private long secCnt = 0;
    private BigDecimal secAmt = BigDecimal.ZERO;

    private static final DecimalFormat AMT = new DecimalFormat("$###,###,##0.00");
    private static final DecimalFormat CNT = new DecimalFormat("###,###,##0");
	private static final Logger LOG = LogManager.getLogger(RoutingReportWriter.class);

    public void open(String out, LocalDateTime now) {
        try {
        	
        	LOG.info("It start writing the file");
            w = Files.newBufferedWriter(Paths.get(out));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void startSection(String area, String clerk, P09325kHeaderView hdr) throws IOException {
    	
    	LOG.info("It is in the start Section method");
        if (curArea != null) {
            write("");
            write(String.format("  %-8s %10s %15s", "TOTAL", CNT.format(secCnt), padAmt(secAmt, 12)));
            secCnt = 0;
            secAmt = BigDecimal.ZERO;
        }
        curArea = area;
        curClerk = clerk;
        newPageHeader(area, clerk, hdr);
    }

    public void writeDetail(P09325RoutingView r) throws IOException {
        ensureBody();

        String last   = padRight(nz(r.getPatientLast()), 10);
        String first  = padRight(nz(r.getPatientFirst()), 8);
        String id     = padRight(nz(r.getPatientId()), 12);
        String remit  = padRight(nz(r.getRemittor()), 18);
        String amt    = padAmt(nvl(r.getControlAmt()), 12);
        String nbr    = right(nz(r.getControlNbr()), 6);
        String chkNbr = right(nz(r.getCheckNbr()), 10);
        String days   = right(nzi(r.getStatusText()), 5);
        String loc    = right(nz(r.getClerk()), 3);
        String house  = right(nz(r.getArea()), 4);

        // Matches expected headings layout:

        write(String.format(
            "  %-3s   %8s %6s  %12s  %-10s %-8s %-12s  %-18s  %-3s  %-5s %10s  %-6s  %3s  %4s",
            nz(r.getRefundType()),              // REFUND TYPE
            date8(r.getRecvDate()),             // DATE
            nbr,                             // NUMBER (tight 6)
            amt,                             // AMOUNT (12, right)
            last,                            // LAST (10)
            first,                           // FIRST (8)
            id,                              // ID NUMBER (12)
            remit,                           // REMITTOR (18)
            nz(r.getReasonCode()),              // REAS CODE (3)
            nz(r.getReceiptType()),               // RECPT TYPE
            chkNbr,                          // CHECK NUMBER (10, right)
            nz(r.getStatusText()),                  // STATUS (<=6)
            loc,                             // LOC (3, right)
            house                            // HOUSE (4, right)
        ));

        secCnt++;
        secAmt = secAmt.add(nvl(r.getControlAmt()));
    }

    public void noteCheckpoint(String key, long cnt) throws IOException {
    	//System.out.println("-- CHECKPOINT--");
    }

    public void finishSectionTotals() throws IOException {
        if (curArea != null) {
            write("");
            write(String.format(" %-8s %10s %15s", "TOTAL", CNT.format(secCnt), padAmt(secAmt, 12)));
        }
    }

    public void closeA() throws IOException {
        footer("END OF P09325-A REPORT");
        w.close();
    }

    // ===== Header builder (exact column control) =====
    private void newPageHeader(String area, String clerk, P09325kHeaderView hdr) throws IOException {
        page++;
        line = 0;

        // Line 1: REPORT #/ID ... [title at col 48] ... PAGE (flush-right, with 6-wide page number)
        final String title = "ARKANSAS BLUE CROSS AND BLUE SHIELD";
        final String pre   = String.format(" %-9s %-8s", "REPORT #", "P09325-A");
        final String right1 = String.format(" PAGE %6d", page);  // -> " PAGE     1"

        String left1 = padToCol(pre, TITLE_COL) + title;
        int gap1 = LINE - left1.length() - right1.length();
        if (gap1 < 0) { // trim title just enough to fit
            int keep = Math.max(0, title.length() + gap1);
            left1 = padToCol(pre, TITLE_COL) + title.substring(0, keep);
            gap1 = LINE - left1.length() - right1.length();
        }
        write(left1 + " ".repeat(Math.max(0, gap1)) + right1);

        // Line 2: RUN DATE ... REPORT TITLE ... RUN TIME (right-aligned, HH for leading zero)
        String runDate = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yy"));
        String runTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String left2   = " RUN DATE: " + runDate + "                              " // 30 spaces
                       + "FINANCIAL ROUTING LOCATION REPORT";
        String right2  = " RUN TIME: " + runTime;
        int gap2 = LINE - left2.length() - right2.length();
        if (gap2 < 0) gap2 = 0;
        write(left2 + " ".repeat(gap2) + right2);

        write(""); // blank line

        // Line 3: AREA / CLERK (match sample spacing)
        write(String.format(" AREA:  %3s   CLERK:  %4s", area, clerk));

        write(""); // blank line

        // Line 4: header numbers if provided, widths tuned to sample
        if (hdr != null) {
            write(String.format(
                " BEG INV: %6s %14s   REC'D: %6s %14s  FOR: %6s %14s  END INV: %6s %14s",
                CNT.format(hdr.getBeginCnt()), padAmt(hdr.getBeginAmt(), 14),
                CNT.format(hdr.getRecvCnt()),   padAmt(hdr.getRecvAmt(), 14),
                CNT.format(hdr.getFwdCnt()),   padAmt(hdr.getFwdAmt(), 14),
                CNT.format(hdr.getEndCnt()),   padAmt(hdr.getEndAmt(), 14)
            ));
        }

        write(""); // blank line

        // Lines 5-6: headings exactly as sample
        write(" REFUND     CONTROL        CONTROL            PATIENT                                     REAS   RECPT    CHECK             DAYS IN");
        write("  TYPE    DATE  NUMBER     AMOUNT        LAST      FIRST    ID NUMBER         REMITTOR    CODE   TYPE    NUMBER   STATUS   LOC  HOUSE");

        write(""); // blank line before details
    }

    private void ensureBody() throws IOException {
        if (line >= 55) newPageHeader(curArea, curClerk, null);
    }

    private void footer(String msg) throws IOException {
        write(repeat('*', LINE));
        write(center(msg, LINE, '*'));
        write(repeat('*', LINE));
    }

    // ===== Helpers =====
    private static String padAmt(BigDecimal b, int width) {
        String s = AMT.format(nvl(b));
        return s.length() >= width ? s.substring(0, width) : " ".repeat(width - s.length()) + s;
    }

    private static String date8(LocalDate d) {
        return d == null ? "        " : d.format(DateTimeFormatter.ofPattern("MM/dd/yy"));
    }

    private static BigDecimal nvl(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }

    private static String nz(String s) { return s == null ? "" : s.trim(); }

    private static String nzi(String i) { return i == null ? "" : i.toString(); }

    private static String padRight(String s, int n) {
        if (s == null) s = "";
        return s.length() >= n ? s.substring(0, n) : s + " ".repeat(n - s.length());
    }

    private static String right(String s, int w) {
        if (s == null) s = "";
        return s.length() >= w ? s.substring(s.length() - w) : " ".repeat(w - s.length()) + s;
    }

    private static String center(String s, int width, char fill) {
        int pad = Math.max(0, (width - s.length() - 4) / 2);
        return "**" + " ".repeat(pad) + s + " ".repeat(width - s.length() - pad - 4) + "**";
    }

    private static String padToCol(String s, int targetCol1Based) {
        int need = Math.max(0, targetCol1Based - 1 - s.length());
        return s + " ".repeat(need);
    }

    private void write(String s) throws IOException {
        if (s.length() > LINE)       s = s.substring(0, LINE);
        else if (s.length() < LINE)  s = s + " ".repeat(LINE - s.length());
        w.write(s);
        w.newLine();
        line++;
    }

    private static String repeat(char c, int n) { return String.valueOf(c).repeat(n); }

    @Override public void close() throws IOException { /* not used */ }
}
