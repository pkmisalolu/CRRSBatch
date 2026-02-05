package com.abcbs.crrs.jobs.P09305;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.io.BufferedWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * P09305-A report writer (matches p09305_rpt.txt format).
 * Lines-per-page, widths, headers, and totals conform to sample output.
 */
public class P09305ReportWriter implements ItemWriter<P09305OutputRecord>, StepExecutionListener {

    private static final Logger log = LogManager.getLogger(P09305ReportWriter.class);

    private final String outputFile;
    private final String corpNo;
    private final CorpNameLoader corpNameLoader;

    private BufferedWriter out;
    private int pageNo = 0, lineOnPage = 0;

    private String currentClerk = null;
    private boolean totalsMode = false;

    // Per-clerk totals
    private long clerkCnt = 0;
    private BigDecimal clerkActAmt = BigDecimal.ZERO;
    private BigDecimal clerkWorkBal = BigDecimal.ZERO;
    private BigDecimal clerkCashBal = BigDecimal.ZERO;

    // Grand totals (by refund type + overall)
    private long grandCnt = 0;
    private final Map<String, Tot> grandByRefund = new LinkedHashMap<>();

    // Checkpoint frequency + key
    private int frequency = 0; // from ExecutionContext
    private String lastCheckpointKey = "";

    // ---- Formatting constants (fit sample) ----
    private static final int LINES_PER_PAGE = 56;
    private static final int LINE_LEN = 133; // set to 132 if that is your shop width
    private static final DecimalFormat AMT = new DecimalFormat("$###,###,##0.00");
    private static final DecimalFormat CNT = new DecimalFormat("###,###,##0");

    // ---- Grand-totals column pinning ----
    private static final int COUNT_WIDTH = 12;
    private static final int AMOUNT_WIDTH = 14;
    private static final int LABEL_WIDTH = 16; // fits "ARK PYMT INIT"
    private static final String GRAND_PREFIX =
            "        G R A N D    T O T A L    OF ALL RECEIPT ACTIVITY FOR REFUND TYPE ";

    public P09305ReportWriter(String outputFile, String corpNo, CorpNameLoader corpNameLoader) {
        this.outputFile = outputFile;
        this.corpNo = corpNo;
        this.corpNameLoader = corpNameLoader;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        try {
            out = Files.newBufferedWriter(Paths.get(outputFile));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to open P09305 report file", e);
        }
        var ctx = stepExecution.getJobExecution().getExecutionContext();
        frequency = ctx.containsKey("p09305.frequency") ? ctx.getInt("p09305.frequency") : 0;
    }

    @Override
    public void write(Chunk<? extends P09305OutputRecord> chunk) throws Exception {
        for (P09305OutputRecord r : chunk) {
            if (currentClerk == null || !Objects.equals(currentClerk, r.getClerkId()) || wouldOverflowForDetail()) {
                if (currentClerk != null) writeClerkTotals();
                startNewDetailPage(r.getClerkId());
                currentClerk = r.getClerkId();
                resetClerkTotals();
            }
            writeDetail(r);

            // accumulate per-clerk + grand
            clerkCnt++;
            grandCnt++;
            clerkActAmt = clerkActAmt.add(nz(r.getActivityAmount()));
            clerkWorkBal = nz(r.getWorkingBalance());   // last seen balance per line (ending bal on page)
            clerkCashBal = nz(r.getCashReceiptsBalance());

            if (r.getCrCntrldAmt() != null) {
                grandByRefund.computeIfAbsent(blank(r.getCrRefundType()), k -> new Tot())
                        .add(r.getCrCntrldAmt());
            }

            if (frequency > 0 && r.getCheckpointCounter() % frequency == 0) {
                lastCheckpointKey = buildKey(r);
            }
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        try {
            if (currentClerk != null) {
                writeClerkTotals();
            }
            // Grand Totals page
            startNewGrandTotalsPage();
            writeGrandTotalsByRefundType();
            writeFixed(""); // spacer
            writeOverallGrandTotals();

            writeFooter();
            out.close();

            if (!lastCheckpointKey.isEmpty()) {
                stepExecution.getJobExecution().getExecutionContext()
                        .put("p09305.lastCheckpointKey", lastCheckpointKey);
            }
            return ExitStatus.COMPLETED;
        } catch (Exception e) {
            log.error("P09305 report writing failed", e);
            return ExitStatus.FAILED;
        }
    }

    // ---------------- rendering ----------------

    private void startNewDetailPage(String clerkId) throws Exception {
    	totalsMode = false;  
        pageNo++;
        lineOnPage = 0;
        writeHeader(clerkId);
        writeReceiptActivityHeader();
    }

    private void startNewGrandTotalsPage() throws Exception {
    	totalsMode = true;
        pageNo++;
        lineOnPage = 0;
        writeHeader(null);
    }

    private boolean wouldOverflowForDetail() {
        return lineOnPage + 5 >= LINES_PER_PAGE;
    }

    // === HEADER (fixed zones) ===
    private void writeHeader(String clerkId) throws Exception {
        final String corpName = corpNameLoader.corpName(corpNo);
        final String runDate  = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yy"));
        final String runTime  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("H:mm:ss"));

        // Line 1
        String left1   = " REPORT #  P09305-A";
        String middle1 = corpName;
        String right1  = "PAGE     " + String.valueOf(pageNo);
        writeFixed(composeThree(left1, middle1, right1, LINE_LEN));

        // Line 2
        String left2   = " RUN DATE: " + runDate;
        String middle2 = "FINANCIAL ACTIVITY REPORT";
        String right2  = "RUN TIME: " + runTime;
        writeFixed(composeThree(left2, middle2, right2, LINE_LEN));

        if (!totalsMode) {
            // detail pages: show CORP line and CLERK line (blank or with id)
            writeFixed(" CORP " + pad(corpNo, 2) + " - " + "BCBS");
            writeFixed("");
            writeFixed(" CLERK ID:" + (clerkId != null && !clerkId.isEmpty() ? " " + clerkId : ""));
        } else {
            // totals page: no CORP, no CLERK line; just extra vertical space
            writeFixed("");
            writeFixed("");
        }
    }


    private void writeReceiptActivityHeader() throws Exception {
        writeFixed(pad("", 58) + "RECEIPT ACTIVITY");
        writeFixed("  CONTROL  CNTL  REF      CONTROL      ACTIVITY              XREF                       ACTIVITY        WORKING       CASH RECEIPTS");
        writeFixed("   DATE      #   TYPE     AMOUNT         DATE    ACT        NUMBER          XREF DATE    AMOUNT         BALANCE         BALANCE");
        writeFixed(" --------  ----  ---- --------------   --------  ---  --------------------  --------- --------------- --------------- ---------------");
    }

    private void writeDetail(P09305OutputRecord r) throws Exception {
        String ctrlDate = r.getCrCntrlDate() == null ? "        " : r.getCrCntrlDate().format(DateTimeFormatter.ofPattern("MM/dd/yy"));
        String cnbr = blank(r.getCrCntrlNbr());
        String rtype = blank(r.getCrRefundType());
        String ctrlAmt = r.getCrCntrldAmt() == null ? "" : right(AMT.format(r.getCrCntrldAmt()), 14);
        String actDate = r.getActActivityDate() == null ? "        " : r.getActActivityDate().format(DateTimeFormatter.ofPattern("MM/dd/yy"));
        String act = pad(blank(r.getActActivity()), 3);
        String xref = pad(blank(r.getActXrefNumber()), 20);
        String xrefDate = r.getActXrefDate() == null ? "         " : r.getActXrefDate().format(DateTimeFormatter.ofPattern("MM/dd/yy"));
        String actAmt = right(AMT.format(nz(r.getActivityAmount())), 15);
        String workBal = right(AMT.format(nz(r.getWorkingBalance())), 15);
        String cashBal = right(AMT.format(nz(r.getCashReceiptsBalance())), 15);

        String line = String.format(" %8s  %-4s  %-4s %14s   %8s  %s  %-20s  %9s %15s %15s %15s",
                ctrlDate, cnbr, rtype, ctrlAmt, actDate, act, xref, xrefDate, actAmt, workBal, cashBal);

        writeFixed(line);
    }

    private void writeClerkTotals() throws Exception {
        writeFixed("");
        writeFixed(String.format(" TOTAL RECEIPT ACTIVITY FOR CLERK: %s %38s %10s %16s %16s",
                blank(currentClerk),
                right(CNT.format(clerkCnt), 10),
                right(AMT.format(clerkActAmt), 10),
                right(AMT.format(clerkWorkBal), 15),
                right(AMT.format(clerkCashBal), 15)
        ));
    }

    // === GRAND TOTALS (fixed columns) ===

    private int countCol()  { return LINE_LEN - (AMOUNT_WIDTH + 1 + COUNT_WIDTH); } // one space between count and amount header
    private int amountCol() { return LINE_LEN - AMOUNT_WIDTH; }

    private void writeGrandHeader() throws Exception {
        char[] buf = spaces(LINE_LEN);
        putAt(buf, countCol(), "NUMBER");
        putAt(buf, amountCol(), "AMOUNT");
        writeFixed(new String(buf));
    }

    private void writeGrandTotalsByRefundType() throws Exception {
        List<Rt> order = List.of(
                new Rt("PER", "PERSONALS"),
                new Rt("RET", "RETURNS"),
                new Rt("UND", "UNDELIVERABLES"),
                new Rt("OTH", "OTHERS"),
                new Rt("OFF", "OFFSET"),
                new Rt("SPO", "STOP PAYMENT"),
                new Rt("API", "ARK PYMT INIT")
        );
        Set<String> printed = new HashSet<>();

        writeFixed("");
        writeGrandHeader();

        for (Rt rt : order) {
            Tot t = grandByRefund.getOrDefault(rt.code, new Tot());
            writeGrandLine(rt.label, t.count, t.amount);
            printed.add(rt.code);
        }
        for (var e : grandByRefund.entrySet()) {
            if (printed.contains(e.getKey())) continue;
            Tot t = e.getValue();
            writeGrandLine(fallbackLabel(e.getKey()), t.count, t.amount);
        }
    }

    private void writeGrandLine(String label, long count, BigDecimal amount) throws Exception {
        char[] buf = spaces(LINE_LEN);

        // Left phrase + right-justified label in fixed field, then "  IS"
        putAt(buf, 0, GRAND_PREFIX);
        String lbl = right(label, LABEL_WIDTH);
        putAt(buf, GRAND_PREFIX.length(), lbl);
        putAt(buf, GRAND_PREFIX.length() + LABEL_WIDTH, "  IS");

        // Right columns (aligned with header)
        putAt(buf, countCol(),  right(CNT.format(count), COUNT_WIDTH));
        putAt(buf, amountCol(), right(AMT.format(nz(amount)), AMOUNT_WIDTH));

        writeFixed(new String(buf));
    }

    private void writeOverallGrandTotals() throws Exception {
        long totalCount = grandByRefund.values().stream().mapToLong(t -> t.count).sum();
        BigDecimal totalAmt = grandByRefund.values().stream().map(t -> t.amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        writeFixed("");
        writeGrandHeader();

        char[] buf = spaces(LINE_LEN);
        String phrase = "G R A N D    T O T A L     OF ALL RECEIPT ACTIVITY IS";
        int phraseStart = 38; // visual anchor; adjust if LINE_LEN=132 or different form
        if (phraseStart + phrase.length() >= countCol()) phraseStart = 2;
        putAt(buf, phraseStart, phrase);

        putAt(buf, countCol(),  right(CNT.format(totalCount), COUNT_WIDTH));
        putAt(buf, amountCol(), right(AMT.format(totalAmt), AMOUNT_WIDTH));

        writeFixed(new String(buf));
    }

    private void writeFooter() throws Exception {
        writeFixed(repeat('*', LINE_LEN));
        String msg = "END OF P09305-A REPORT";
        int side = (LINE_LEN - msg.length() - 4) / 2;
        String line = "**" + " ".repeat(side) + msg + " ".repeat(LINE_LEN - msg.length() - side - 4) + "**";
        writeFixed(line);
        writeFixed(repeat('*', LINE_LEN));
    }

    private void resetClerkTotals() {
        clerkCnt = 0;
        clerkActAmt = BigDecimal.ZERO;
        clerkWorkBal = BigDecimal.ZERO;
        clerkCashBal = BigDecimal.ZERO;
    }

    // ---------------- helpers ----------------

    private void writeFixed(String s) throws Exception {
        if (lineOnPage >= LINES_PER_PAGE) {
            startNewDetailPage(currentClerk);
        }
        if (s.length() > LINE_LEN) s = s.substring(0, LINE_LEN);
        out.write(pad(s, LINE_LEN));
        out.newLine();
        lineOnPage++;
    }

    private String buildKey(P09305OutputRecord r) {
        return (blank(r.getClerkId())
                + blank(r.getActActivity())
                + r.getCrCntrlDate()
                + blank(r.getCrCntrlNbr())
                + blank(r.getCrRefundType())
                + r.getActActivityDate()
                + r.getActTimestamp());
    }

    // zone & buffer utilities
    private static String composeThree(String left, String middle, String right, int width) {
        char[] buf = spaces(width);

        // Right
        right = truncate(right, width);
        int rStart = Math.max(0, width - right.length());
        putAt(buf, rStart, right);

        // Left
        int leftMax = Math.max(0, rStart - 1);
        left = truncate(left, leftMax);
        putAt(buf, 0, left);

        // Middle between left end and right start
        int midAvailStart = (left.isEmpty()) ? 0 : left.length() + 1;
        int midAvailEnd = Math.max(midAvailStart, rStart - 1);
        int room = Math.max(0, midAvailEnd - midAvailStart + 1);
        middle = truncate(middle, room);
        int midStart = midAvailStart + Math.max(0, (room - middle.length()) / 2);
        putAt(buf, midStart, middle);

        return new String(buf);
    }

    private static char[] spaces(int n) { char[] b = new char[n]; Arrays.fill(b, ' '); return b; }
    private static void putAt(char[] buf, int start, String s) {
        if (s == null || start >= buf.length) return;
        int len = Math.min(s.length(), Math.max(0, buf.length - start));
        if (len > 0) s.getChars(0, len, buf, Math.max(0, start));
    }
    private static String truncate(String s, int max){ if (s==null) return ""; return (s.length() <= max) ? s : s.substring(0, max); }

    private static String pad(String s, int n) { if (s == null) s = ""; return s.length() >= n ? s.substring(0, n) : s + " ".repeat(n - s.length()); }
    private static String right(String s, int w) { if (s == null) s = ""; return s.length() >= w ? s.substring(s.length() - w) : " ".repeat(w - s.length()) + s; }
    private static String blank(String s) { return s == null ? "" : s; }
    private static BigDecimal nz(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }
    private static String repeat(char c, int n){ return String.valueOf(c).repeat(n); }

    private static final class Tot {
        long count = 0; BigDecimal amount = BigDecimal.ZERO;
        void add(BigDecimal amt){ count++; amount = amount.add(amt == null ? BigDecimal.ZERO : amt); }
    }
    private record Rt(String code, String label){}

    private static String fallbackLabel(String refund){
        Map<String,String> map = Map.of(
                "PER", "PERSONALS",
                "RET", "RETURNS",
                "UND", "UNDELIVERABLES",
                "OTH", "OTHERS",
                "OFF", "OFFSET",
                "SPO", "STOP PAYMENT",
                "API", "ARK PYMT INIT"
        );
        return map.getOrDefault(blank(refund), blank(refund));
    }
}
