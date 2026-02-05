package com.abcbs.crrs.jobs.P09352;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@StepScope
public class P09352ReportWriter implements StepExecutionListener {

    public enum RefundCategory {
        PERSONAL("PERSONAL"),
        RETURNS("RETURNS"),
        UNDELIVERABLE("UNDELIVERABLE"),
        OTHER("OTHER"),
        OFFSET("OFFSET"),
        STOP_PAYMENT("STOP PAYMENT");

        private final String label;
        RefundCategory(String label) { this.label = label; }
        public String label() { return label; }
    }

    private static final class Totals {
        int count = 0;
        BigDecimal amount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        void add(BigDecimal amt) {
            count++;
            amount = amount.add(nvlMoney(amt));
        }
        void add(int cnt, BigDecimal amt) {
            count += cnt;
            amount = amount.add(nvlMoney(amt));
        }
    }

    // injected (ONLY runtime job parameter)
    private final String reportFileOrPath;

    public P09352ReportWriter(
    	    @Value("#{jobParameters['reportFile'] ?: jobParameters['reportPath']}") String reportFileOrPath
    ) {
        this.reportFileOrPath = reportFileOrPath;
    }

    private BufferedWriter bw;
    private ExecutionContext ctx;

    private boolean opened = false;

    private boolean headerWritten = false;
    private boolean finalSummaryWritten = false;
    private int pageNumber = 0;
    private int lineOnPage = 0;
    private static final int MAX_LINES_PER_PAGE = 66;

    private String currentRefundTypeCode = "";
    private RefundCategory currentCategory;

    private Totals sectionRequested = new Totals();
    private Totals sectionError     = new Totals();
    private Totals sectionIssued    = new Totals();

    private final Map<RefundCategory, Totals> requestedByCat = new EnumMap<>(RefundCategory.class);
    private final Map<RefundCategory, Totals> errorByCat     = new EnumMap<>(RefundCategory.class);
    private final Map<RefundCategory, Totals> issuedByCat    = new EnumMap<>(RefundCategory.class);

    @Override
    public void beforeStep(StepExecution stepExecution) {
        openIfNeeded(stepExecution);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        try {
            // COBOL prints the final summary page and end banner at normal completion.
            if (opened && headerWritten && !finalSummaryWritten
                    && stepExecution.getExitStatus() != null
                    && ExitStatus.COMPLETED.getExitCode().equals(stepExecution.getExitStatus().getExitCode())) {
                writeFinalSummaryAndEndBanner();
                finalSummaryWritten = true;
            }
        } catch (Exception ignore) {
            // keep going; still close to flush report
        } finally {
            closeQuietly();
        }
        return stepExecution.getExitStatus();
    }

    /**
     * SAFE/IDEMPOTENT: You can call from Processor before you start writing sections.
     * Does NOT accept header context. Header reads ExecutionContext dynamically.
     */
    public void openIfNeeded(StepExecution stepExecution) {
        if (opened) return;

        this.ctx = stepExecution.getExecutionContext();

        if (!StringUtils.hasText(reportFileOrPath)) {
            throw new IllegalStateException("Missing job parameter: reportPath (or reportFile)");
        }

        try {
            Path p = Path.of(reportFileOrPath);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            bw = Files.newBufferedWriter(
                    p,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException ioe) {
            throw new IllegalStateException("Unable to open report file: " + reportFileOrPath, ioe);
        }

        for (RefundCategory c : RefundCategory.values()) {
            requestedByCat.put(c, new Totals());
            errorByCat.put(c, new Totals());
            issuedByCat.put(c, new Totals());
        }

        opened = true;
    }

    public void closeQuietly() {
        if (!opened) return;
        try { if (bw != null) bw.flush(); } catch (Exception ignore) {}
        try { if (bw != null) bw.close(); } catch (Exception ignore) {}
        opened = false;
    }

    // ============================================================
    // Public API (called from processor)
    // ============================================================

    /**
     * COBOL 400000-HEADING-ROUTINE placeholder.
     * In this Java writer, "heading routine" is effectively a no-op beyond ensuring a page header.
     * The processor controls when to start sections.
     */
    public void headingRoutineCobolStyle(ExecutionContext ec) {
        this.ctx = ec;
        ensureHeader();
    }

    public void startRefundTypeSection(String refundTypeCode, String refundTypeDesc, RefundCategory category) {
        ensureHeader();
        // If processor mistakenly calls "start" repeatedly for the same refund type, do not re-print the section header.
        if (refundTypeCode != null && refundTypeCode.equals(this.currentRefundTypeCode) && sectionRequested != null) {
            return;
        }
        ensureSpace(6);

        this.currentRefundTypeCode = safe(refundTypeCode);
        this.currentCategory = category;

        sectionRequested = new Totals();
        sectionError = new Totals();
        sectionIssued = new Totals();

        writeln(line132("REFUND TYPE: " + safe(refundTypeCode) + " - " + safe(refundTypeDesc)));
        writeln(line132("  C/R CONTROL      CHECK                               PAYEE                             ORIG  CHECK   RQSTD"));
        writeln(line132("   DATE   NBR      AMOUNT          PAYEE NAME          TYPE  PAYEE ID #  PAYEE TIN FR/PR TYPE  NUMBER   BY      ERRORS"));
        writeln(line132("-------- ---- --------------- ----------------------- ---- ------------ --------- ----- ---- -------- ---- -------------------------"));
        writeln(line132(" "));
    }

    public void writeDetailFromFields(
            String crControlDateMMDDYY,
            String crControlNbr,
            BigDecimal checkAmount,
            String payeeName,
            String payeeType,
            String payeeId,
            String payeeTin,
            String frPr,
            String typeCode,
            String origCheckNumber,
            String rqstdBy,
            String errorsText
    ) {
        ensureHeader();
        ensureSpace(1);

        String line =
                rpad(safe(crControlDateMMDDYY), 8) + " " +
                rpad(safe(crControlNbr), 4) + " " +
                lpad(formatMoney(checkAmount), 15) + " " +
                rpad(safe(payeeName), 23) + " " +
                rpad(safe(payeeType), 4) + " " +
                rpad(safe(payeeId), 12) + " " +
                rpad(safe(payeeTin), 9) + " " +
                rpad(safe(frPr), 5) + " " +
                rpad(safe(typeCode), 4) + " " +
                rpad(safe(origCheckNumber), 8) + " " +
                rpad(safe(rqstdBy), 4) + " " +
                rpad(safe(errorsText), 25);

        writeln(line132(line));
    }

    public void addRequested(BigDecimal amt) {
        sectionRequested.add(amt);
        if (currentCategory != null) requestedByCat.get(currentCategory).add(amt);
    }

    public void addError(BigDecimal amt) {
        sectionError.add(amt);
        if (currentCategory != null) errorByCat.get(currentCategory).add(amt);
    }

    public void addIssued(BigDecimal amt) {
        sectionIssued.add(amt);
        if (currentCategory != null) issuedByCat.get(currentCategory).add(amt);
    }

    public void endRefundTypeSection() {
        ensureHeader();
        ensureSpace(12);

        writeln(line132(" "));
        writeln(line132(" "));
        writeln(line132(rpad("", 75) + "CHECK"));
        writeln(line132(rpad("", 60) + rpad("NUMBER", 12) + rpad("", 3) + "AMOUNT"));

        String rt = safe(currentRefundTypeCode);

        writeln(line132(String.format(
                "        TOTAL  CHECKS REQUESTED    FOR REFUND TYPE - %-3s%10d%14s",
                rt, sectionRequested.count, fmtMoney(sectionRequested.amount)
        )));
        writeln(line132(String.format(
                "        TOTAL  CHECKS IN ERROR     FOR REFUND TYPE - %-3s%10d%14s",
                rt, sectionError.count, fmtMoney(sectionError.amount)
        )));
        writeln(line132(String.format(
                "        TOTAL  CHECKS TO BE ISSUED FOR REFUND TYPE - %-3s%10d%14s",
                rt, sectionIssued.count, fmtMoney(sectionIssued.amount)
        )));

        writeln(line132(" "));
        writeln(line132(" "));
    }

    public void writeFinalSummaryAndEndBanner() {
        newPage(); // summary starts new page like sample
        ensureSpace(50);

        writeln(line132(" "));
        writeln(line132(" "));
        writeln(line132(rpad("", 81) + "CHECK"));

        writeSummaryBlock("SUMMARY    OF ALL   CHECKS  REQUESTED:", requestedByCat,
                "G R A N D    TOTAL OF ALL CHECKS  REQUESTED");

        writeln(line132(" "));
        writeln(line132(" "));
        writeln(line132(" "));

        writeSummaryBlock("SUMMARY    OF ALL   CHECKS  IN ERROR:", errorByCat,
                "G R A N D    TOTAL OF ALL CHECKS  IN ERROR");

        writeln(line132(" "));
        writeln(line132(" "));
        writeln(line132(" "));

        writeSummaryBlock("SUMMARY    OF ALL   CHECKS TO BE ISSUED:", issuedByCat,
                "G R A N D    TOTAL OF ALL CHECKS TO BE ISSUED");

        writeln(line132(" "));
        writeln(line132(repeat("*", 52) + "  END OF " + getReportNumber() + " REPORT  " + repeat("*", 54)));
    }

    // ============================================================
    // Header / paging (layout like sample)
    // ============================================================

    private void ensureHeader() {
        if (!headerWritten) {
            newPage();
            headerWritten = true;
        }
    }

    private void newPage() {
        pageNumber++;
        lineOnPage = 0;

        String reportNumber = getReportNumber();
        String companyName  = getCompanyName();

        String corpCode = safe(getEcString("WS_CORP_CODE"));
        String corpName = normalizeCorpName(corpCode, getEcString("WS_CORP"));
        String reqDate  = fmtControlCardDateForHeader(ctx.getString("WS_DB2_CONTROL_CARD_DATE"));
        String apBatch  = safe(ctx.getString("WS_AP_BATCH_ID"));

        String runDate = getRunDateFromECOrNow();
        String runTime = getRunTimeFromECOrNow();

        // ------------------------------------------------------------
        // PAGE HEADER (132 chars) - match legacy layout
        // ------------------------------------------------------------
        String left1  = "REPORT #  " + reportNumber;
        String mid1   = companyName;
        String right1 = String.format("PAGE%6d", pageNumber);

        // COBOL-like: left at col 1, company around col 38, PAGE ends at col 132
        writeln(fixed132(left1, 1, mid1, 38, right1, 132));

        String left2  = "RUN DATE: " + runDate;
        String mid2   = "FULL AND PARTIAL A/P CHECK REQUESTS";
        String right2 = "RUN TIME: " + runTime;

        // COBOL-like: left at col 1, title at col 38, RUN TIME ends at col 132
        writeln(fixed132(left2, 1, mid2, 38, right2, 132));

        if (pageNumber == 1) {
            // e.g. "CORPORATION: 01     ARKANSAS BLUE CROSS BLUE SH FOR REQUESTS 11/26/25 AND EARLIER"
            String corpLine = "CORPORATION: " + corpCode + "     " + corpName + " FOR REQUESTS " + reqDate + " AND EARLIER";
            writeln(line132(corpLine));
        } else {
            writeln(line132(lpad("FOR REQUESTS " + reqDate + " AND EARLIER", 49)));
        }

        writeln(line132(lpad("AP BATCH " + apBatch, 71)));
        
        String fr = "F I N A L    REPORT";
        writeln(line132(
                rpad("", 20) + rpad(fr, 30) +
                rpad("", 15) + rpad(fr, 30) +
                rpad("", 15) + rpad(fr, 30)
        ));
    }

    private String normalizeCorpName(String corpCode, String corpName) {
        String code = safe(corpCode);
        String name = corpName == null ? "" : corpName.trim();

        // If WS_CORP already includes the corp code prefix (e.g. "02 ARKANSAS ..."), strip it.
        if (StringUtils.hasText(code) && StringUtils.hasText(name)) {
            String p1 = code + " -";
            String p2 = code + "-";
            String p3 = code + " ";
            if (name.startsWith(p1)) name = name.substring(p1.length()).trim();
            else if (name.startsWith(p2)) name = name.substring(p2.length()).trim();
            else if (name.startsWith(p3)) name = name.substring(p3.length()).trim();
        }
        return name;
    }
    
    private void ensureSpace(int linesNeeded) {
        if (pageNumber == 0) newPage();
        if (lineOnPage + linesNeeded > MAX_LINES_PER_PAGE) {
            writeln(line132(" "));
            newPage();
        }
    }

    private String getEcString(String key) {
        if (ctx == null) return "";
        Object v = ctx.get(key);     // NOT getString
        return v == null ? "" : v.toString();
    }
    
    // ============================================================
    // Summary blocks
    // ============================================================

    private void writeSummaryBlock(String title, Map<RefundCategory, Totals> byCat, String grandLabelLine) {
        Totals grand = new Totals();

        writeln(line132("              " + rpad(title, 44) + "        NUMBER         AMOUNT"));

        writeSummaryLine(byCat, grand, RefundCategory.PERSONAL);
        writeSummaryLine(byCat, grand, RefundCategory.RETURNS);
        writeSummaryLine(byCat, grand, RefundCategory.UNDELIVERABLE);
        writeSummaryLine(byCat, grand, RefundCategory.OTHER);
        writeSummaryLine(byCat, grand, RefundCategory.OFFSET);
        writeSummaryLine(byCat, grand, RefundCategory.STOP_PAYMENT);

        writeln(line132(String.format(
                "              %-48s%10d%14s",
                grandLabelLine,
                grand.count,
                fmtMoney(grand.amount)
        )));
    }

    private void writeSummaryLine(Map<RefundCategory, Totals> byCat, Totals grand, RefundCategory cat) {
        Totals t = byCat.get(cat);
        if (t == null) t = new Totals();
        grand.add(t.count, t.amount);

        writeln(line132(String.format(
                "%40s%-22s%10d%14s",
                "",
                rpad(cat.label(), 22),
                t.count,
                fmtMoney(t.amount)
        )));
    }

    // ============================================================
    // Formatting helpers (changed per suggestion)
    // ============================================================

    private String getReportNumber() {
        if (ctx == null) return "P09352-A";

        Object v = ctx.get("REPORT_NUMBER");   // NOT getString
        if (v == null) return "P09352-A";

        String s = v.toString().trim();
        return StringUtils.hasText(s) ? s : "P09352-A";
    }

    private String getCompanyName() {
        // ✅ no runtime job parameter, comes from program/EC
        String ec = safe(ctx.getString("REPORT_COMPANY_NAME"));
        return StringUtils.hasText(ec) ? ec : "ARKANSAS BLUE CROSS AND BLUE SHIELD";
    }

    private String getRunDateFromECOrNow() {
        String ec = safe(ctx.getString("WS_RUN_DATE_MMDDYY"));
        if (StringUtils.hasText(ec)) return ec;
        return LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yy"));
    }

    private String getRunTimeFromECOrNow() {
        String ec = safe(ctx.getString("WS_RUN_TIME_HHMMSS"));
        if (StringUtils.hasText(ec)) return ec;
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    private static String fmtControlCardDateForHeader(String raw) {
        String s = safe(raw);
        if (!StringUtils.hasText(s)) return "  /  /  ";
        if (s.matches("\\d{2}/\\d{2}/\\d{2}")) return s;
        if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            LocalDate d = LocalDate.parse(s, DateTimeFormatter.ISO_DATE);
            return d.format(DateTimeFormatter.ofPattern("MM/dd/yy"));
        }
        return s;
    }

    private static String formatMoney(BigDecimal amt) {
        BigDecimal a = nvlMoney(amt).setScale(2, RoundingMode.HALF_UP);
        return "$" + a.toPlainString();
    }

    private static String fmtMoney(BigDecimal amt) {
        return formatMoney(amt);
    }

    private static BigDecimal nvlMoney(BigDecimal amt) {
        return amt == null ? BigDecimal.ZERO : amt;
    }

    private static String safe(String s) {
        if (s == null) return "";
        String t = s.trim();
        return "null".equalsIgnoreCase(t) ? "" : t;
    }

    private static String rpad(String s, int n) {
        String t = safe(s);
        if (t.length() >= n) return t.substring(0, n);
        return t + " ".repeat(n - t.length());
    }

    private static String lpad(String s, int n) {
        String t = safe(s);
        if (t.length() >= n) return t.substring(t.length() - n);
        return " ".repeat(n - t.length()) + t;
    }

    private static String repeat(String ch, int n) {
        if (n <= 0) return "";
        return ch.repeat(n);
    }

    private static String line132(String s) {
        String t = (s == null) ? "" : s;
        if (t.length() == 132) return t;
        if (t.length() > 132) return t.substring(0, 132);
        return t + " ".repeat(132 - t.length());
    }

    public void writeNoDataFoundLine(ExecutionContext ec) {
        this.ctx = ec;
        ensureHeader();
        ensureSpace(1);
        writeln(line132("                         ** NO DATA FOUND **"));
    }

    
    private static String fixed132(String left, int leftCol1, String mid, int midCol1, String right, int rightEndCol1) {
        final int LEN = 132;
        char[] line = new char[LEN];
        java.util.Arrays.fill(line, ' ');

        if (left != null && !left.isEmpty()) {
            int start = Math.max(0, leftCol1 - 1);
            int n = Math.min(left.length(), LEN - start);
            left.getChars(0, n, line, start);
        }

        if (mid != null && !mid.isEmpty()) {
            int start = Math.max(0, midCol1 - 1);
            int n = Math.min(mid.length(), LEN - start);
            mid.getChars(0, n, line, start);
        }

        if (right != null && !right.isEmpty()) {
            int end = Math.min(LEN, rightEndCol1);         // 1-based end column (inclusive)
            int n = Math.min(right.length(), end);
            int start = Math.max(0, end - n);
            right.getChars(0, n, line, start);
        }

        return new String(line);
    }
    
    private void writeln(String s132) {
        try {
            bw.write(line132(s132));
            bw.newLine();
            lineOnPage++;
        } catch (IOException ioe) {
            throw new IllegalStateException("Report write failed", ioe);
        }
    }
}