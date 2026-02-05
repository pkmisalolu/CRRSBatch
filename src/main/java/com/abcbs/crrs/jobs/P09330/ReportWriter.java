package com.abcbs.crrs.jobs.P09330;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic ReportWriter for P09330 matching the uploaded sample (p09330_report.txt).
 * - Fixed-width templates copied from sample
 * - Accepts buffered per-receipt and per-refund subtotals
 * - Writes pages in the same order as sample (page1 with receipt subtotals before GRAND TOTAL,
 *   page2 with refund subtotals before GRAND TOTAL, footer)
 *
 * Usage:
 *  - new ReportWriter(path)
 *  - reportWriter.open()
 *  - reportWriter.accumulate(...) during streaming (global totals)
 *  - reportWriter.bufferReceiptTypeSubtotal(...) and/or bufferRefundTypeSubtotal(...) (tasklet provides maps)
 *  - reportWriter.close()  <-- writes pages and flushes buffered subtotals
 */
public class ReportWriter {

    private static final DateTimeFormatter RUN_DATE_FMT = DateTimeFormatter.ofPattern("MM/dd/yy", Locale.ENGLISH);
    private static final DateTimeFormatter CNTRL_DATE_FMT = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH);

    private final String path;
    private BufferedWriter out;
    private boolean opened = false;

    // main accumulators used to render the main page and grand totals
    private final Map<Category, EnumMap<AgeBucket, CountAmount>> data = new LinkedHashMap<>();

    // buffered subtotals provided by the tasklet (converted to writer-friendly DTOs)
    private final Map<String, Map<Category, EnumMap<AgeBucket, CountAmountDTO>>> bufferedReceiptSubtotals = new LinkedHashMap<>();
    private final Map<String, Map<Category, EnumMap<AgeBucket, CountAmountDTO>>> bufferedRefundSubtotals  = new LinkedHashMap<>();

    // Decimal formatter used to match "$#,###.##"
    private static final DecimalFormat AMT_FMT;
    static {
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.ENGLISH);
        s.setGroupingSeparator(',');
        AMT_FMT = new DecimalFormat("#,##0.00", s);
    }

    // categories in the order they appear in the sample (left to right)
    public enum Category {
        PHYSICIAN, HOSPITAL, MEDIPAK, GCPS, SUBTOTAL_RETURNED, BAD_ADDRESS, OFFSET,
        STOP_PAYMENT, ARKANSAS_PAYMENT, TOTAL
    }

    public ReportWriter(String outputPath) {
        this.path = (outputPath == null || outputPath.isBlank()) ? "P09330-A.txt" : outputPath;
        for (Category c : Category.values()) {
            EnumMap<AgeBucket, CountAmount> m = new EnumMap<>(AgeBucket.class);
            for (AgeBucket b : AgeBucket.values()) m.put(b, new CountAmount());
            data.put(c, m);
        }
    }

    // ---------------- public API ----------------

    public synchronized void open() throws Exception {
        if (opened) return;
        File f = new File(path);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, false), StandardCharsets.US_ASCII));
        opened = true;
    }

    /**
     * Accumulate counts and amounts into the category + bucket.
     */
    public synchronized void accumulate(Category category, AgeBucket bucket, long countDelta, BigDecimal amountDelta) {
        EnumMap<AgeBucket, CountAmount> m = data.get(category);
        if (m == null) {
            m = new EnumMap<>(AgeBucket.class);
            for (AgeBucket b : AgeBucket.values()) m.put(b, new CountAmount());
            data.put(category, m);
        }
        CountAmount ca = m.get(bucket);
        if (ca == null) {
            ca = new CountAmount();
            m.put(bucket, ca);
        }
        ca.count += countDelta;
        if (amountDelta != null) ca.amount = ca.amount.add(amountDelta);
    }

    /**
     * Buffer subtotals provided by the tasklet for a receipt-type (tasklet builds this map).
     * Map<Category, EnumMap<AgeBucket, CountAmountDTO>> -- order is kept (LinkedHashMap).
     */
    public synchronized void bufferReceiptTypeSubtotal(String receiptType, Map<Category, EnumMap<AgeBucket, CountAmountDTO>> subtotalMap) {
        if (receiptType == null || subtotalMap == null) return;
        bufferedReceiptSubtotals.put(receiptType, subtotalMap);
    }

    /**
     * Buffer subtotals provided by the tasklet for a refund-type.
     */
    public synchronized void bufferRefundTypeSubtotal(String refundType, Map<Category, EnumMap<AgeBucket, CountAmountDTO>> subtotalMap) {
        if (refundType == null || subtotalMap == null) return;
        bufferedRefundSubtotals.put(refundType, subtotalMap);
    }

  

    public synchronized void close() throws Exception {
        if (!opened) return;
        try {
            writePage1();           // writes main matrix and flushes bufferedReceiptSubtotals before GRAND TOTAL
            writePage2();           // writes refunds page and flushes bufferedRefundSubtotals before GRAND TOTAL
            writeFooter();
            out.flush();
        } finally {
            try { out.close(); } catch (Exception ignore) {}
            opened = false;
        }
    }

    public synchronized void safeClose() {
        try { close(); } catch (Exception ignored) {}
    }

    // ---------------- internal data structures & helpers ----------------

    private static class CountAmount {
        long count = 0;
        BigDecimal amount = BigDecimal.ZERO;
    }

    public static final class CountAmountDTO {
        public final long count;
        public final BigDecimal amount;
        public CountAmountDTO(long count, BigDecimal amount) {
            this.count = count;
            this.amount = amount == null ? BigDecimal.ZERO : amount;
        }
    }

//    private String fmtCount(long cnt) {
//        return String.format("%4d", cnt);
//    }
    
    private String fmtCount(long cnt) {
        // right-aligned in 3 positions to match sample spacing
        return String.format("%2d", cnt);
    }

    // Format amount as "$#,###.00" then when placed into fixed field we use String.format("%12s", amtStr)
    private String fmtAmount(BigDecimal amt) {
        if (amt == null) amt = BigDecimal.ZERO;
        return "$" + AMT_FMT.format(amt);
    }

    private static String padRight(String s, int w) {
        if (s == null) s = "";
        if (s.length() >= w) return s.substring(0, w);
        return s + " ".repeat(w - s.length());
    }

    private String bucketLabelShort(AgeBucket b) {
        return switch (b) {
            case D0_15 -> " 0 - 15 DAYS";
            case D16_1M -> " 16 DAYS - 1 MONTH";
            case M1_2 -> " 1-2 MONTHS ";
            case M2_3 -> " 2-3 MONTHS ";
            case M3_4 -> " 3-4 MONTHS ";
            case OVER_4 -> " OV 4 MONTHS";
        };
    }

    // copy/paste of header/footer templates derived from the uploaded sample
    private void writePage1() throws Exception {
        // header lines (match sample spacing)
        out.write(" REPORT #  P09330-A                                        EXHIBIT 9-A                                                     PAGE     1");
        out.newLine();
        String runDate = LocalDate.now().format(RUN_DATE_FMT);
        String runTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        out.write(String.format(" RUN DATE: %s                                RETURNED BCBS CHECKS (BY AGE)                                   RUN TIME: %s",
                runDate, runTime));
        out.newLine();
        out.write("                                           CASH CONTROL REPORT AS OF    " + LocalDate.now().minusMonths(1).format(CNTRL_DATE_FMT));
        out.newLine();
        out.newLine();

        out.write(" ____________________________________________________________________________________________________________________________________");
        out.newLine();
        out.write("            |                |                 |                 |                 |                |               |");
        out.newLine();
        out.write("            |                |                 |                 |                 |    SUBTOTAL    |               |");
        out.newLine();
        out.write("            |   PHYSICIAN    |  HOSPITAL/      |                 |                 |    RETURNED    |     BAD       |   OFFSET");
        out.newLine();
        out.write("            |   PAYABLE *    |  ACCTS PAYABLE  |     MEDIPAK     |      GCPS       |     CHECKS     |   ADDRESS **  |               ");
        out.newLine();
        out.write("            |                |                 |                 |                 |                |               |");
        out.newLine();
        out.write("            | CNT    AMOUNT  | CNT    AMOUNT   | CNT    AMOUNT   | CNT    AMOUNT   | CNT    AMOUNT  | CNT    AMOUNT | CNT    AMOUNT");
        out.newLine();
        out.write(" -----------|----------------|-----------------|-----------------|-----------------|----------------|---------------|----------------");
        out.newLine();
        out.write("            |                |                 |                 |                 |                |               |");
        out.newLine();

        // For each bucket print the three-line block as in sample
        for (AgeBucket b : AgeBucket.values()) {
            // line with hospital/medipak/gcps
        	out.write(String.format(" %s|                | %2s %11s H| %2s %11s A| %2s %11s A|                |               |",
        	        padRight(bucketLabelShort(b), 11),
        	        fmtCount(data.get(Category.HOSPITAL).get(b).count), String.format("%11s", fmtAmount(data.get(Category.HOSPITAL).get(b).amount)),
        	        fmtCount(data.get(Category.MEDIPAK).get(b).count), String.format("%11s", fmtAmount(data.get(Category.MEDIPAK).get(b).amount)),
        	        fmtCount(data.get(Category.GCPS).get(b).count), String.format("%11s", fmtAmount(data.get(Category.GCPS).get(b).amount))
        	));
            out.newLine();

            // line with physician and subtotals/bad/offset
            out.write(String.format("            | %2s %11s | %2s %11s A| %2s %11s U| %2s %11s U| %2s %11s | %2s %11s| %2s %11s",
                    fmtCount(data.get(Category.PHYSICIAN).get(b).count), String.format("%11s", fmtAmount(data.get(Category.PHYSICIAN).get(b).amount)),
                    fmtCount(data.get(Category.HOSPITAL).get(b).count), String.format("%11s", fmtAmount(data.get(Category.HOSPITAL).get(b).amount)),
                    fmtCount(data.get(Category.MEDIPAK).get(b).count), String.format("%11s", fmtAmount(data.get(Category.MEDIPAK).get(b).amount)),
                    fmtCount(data.get(Category.GCPS).get(b).count), String.format("%11s", fmtAmount(data.get(Category.GCPS).get(b).amount)),
                    fmtCount(data.get(Category.SUBTOTAL_RETURNED).get(b).count), String.format("%11s", fmtAmount(data.get(Category.SUBTOTAL_RETURNED).get(b).amount)),
                    fmtCount(data.get(Category.BAD_ADDRESS).get(b).count), String.format("%11s", fmtAmount(data.get(Category.BAD_ADDRESS).get(b).amount)),
                    fmtCount(data.get(Category.OFFSET).get(b).count), String.format("%11s", fmtAmount(data.get(Category.OFFSET).get(b).amount))
            ));
            out.newLine();

            // two spacer lines as in sample
            out.write("            |                |                 |                 |                 |                |               |");
            out.newLine();
            out.write("            |                |                 |                 |                 |                |               |");
            out.newLine();
        }

        // bottom separator and grand total block (sums across buckets)
        out.write(" -----------|----------------|-----------------|-----------------|-----------------|----------------|---------------|----------------");
        out.newLine();
        out.write("            |                |                 |                 |                 |                |               |");
        out.newLine();

        // GRAND TOTAL: sum across buckets
        out.write(String.format(" GRAND TOTAL| %2s %12s| %2s %12s | %2s %12s | %2s %12s | %2s %12s| %2s %11s| %2s %12s",
                fmtCount(sumCount(Category.PHYSICIAN)), String.format("%12s", fmtAmount(sumAmount(Category.PHYSICIAN))),
                fmtCount(sumCount(Category.HOSPITAL)),  String.format("%12s", fmtAmount(sumAmount(Category.HOSPITAL))),
                fmtCount(sumCount(Category.MEDIPAK)),   String.format("%12s", fmtAmount(sumAmount(Category.MEDIPAK))),
                fmtCount(sumCount(Category.GCPS)),      String.format("%12s", fmtAmount(sumAmount(Category.GCPS))),
                fmtCount(sumCount(Category.SUBTOTAL_RETURNED)),  String.format("%12s", fmtAmount(sumAmount(Category.SUBTOTAL_RETURNED))),
                fmtCount(sumCount(Category.BAD_ADDRESS)), String.format("%11s", fmtAmount(sumAmount(Category.BAD_ADDRESS))),
                fmtCount(sumCount(Category.OFFSET)), String.format("%12s", fmtAmount(sumAmount(Category.OFFSET)))
        ));
        out.newLine();
        out.write("            |                |                 |                 |                 |                |               |");
        out.newLine();
        out.write(" ====================================================================================================================================");
        out.newLine();
        out.newLine();
    }

    private void writePage2() throws Exception {
        out.write(" REPORT #  P09330-A                                        EXHIBIT 9-A                                                     PAGE     2");
        out.newLine();
        String runDate = LocalDate.now().format(RUN_DATE_FMT);
        String runTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        out.write(String.format(" RUN DATE: %s                                RETURNED BCBS CHECKS (BY AGE)                                   RUN TIME: %s", runDate, runTime));
        out.newLine();
        out.write("                                           CASH CONTROL REPORT AS OF    " + LocalDate.now().minusMonths(1).format(CNTRL_DATE_FMT));
        out.newLine();
        out.newLine();
        out.write(" _______________________________________________________________________");
        out.newLine();
        out.write("            |                   |                   |                   |");
        out.newLine();
        out.write("            |   STOP PAYMENT    |  ARKANSAS PAYMENT |     T O T A L     |");
        out.newLine();
        out.write("            |                   |      INIATIVE     |                   |");
        out.newLine();
        out.write("            |                   |                   |                   |");
        out.newLine();
        out.write("            | CNT    AMOUNT     |  CNT    AMOUNT    |   CNT    AMOUNT   |");
        out.newLine();
        out.write(" -----------|-------------------|-------------------|-------------------|");
        out.newLine();

        for (AgeBucket b : AgeBucket.values()) {
            out.write("            |                   |                   |                   |");
            out.newLine();
            out.write(String.format(" %s|                   |                   |                   |", padRight(bucketLabelShort(b), 11)));
            out.newLine();
            out.write(String.format("            | %2s %14s | %2s %14s | %2s %14s |",
                    fmtCount(data.get(Category.STOP_PAYMENT).get(b).count), String.format("%14s", fmtAmount(data.get(Category.STOP_PAYMENT).get(b).amount)),
                    fmtCount(data.get(Category.ARKANSAS_PAYMENT).get(b).count), String.format("%14s", fmtAmount(data.get(Category.ARKANSAS_PAYMENT).get(b).amount)),
                    fmtCount(data.get(Category.TOTAL).get(b).count), String.format("%14s", fmtAmount(data.get(Category.TOTAL).get(b).amount))
            ));
            out.newLine();
            out.write("            |                   |                   |                   |");
            out.newLine();
        }

        out.write(" -----------|-------------------|-------------------|-------------------|");
        out.newLine();
        out.write("            |                   |                   |                   |");
        out.newLine();
        out.write(String.format(" GRAND TOTAL| %2s %14s | %2s %14s | %2s %14s |",
                fmtCount(sumCount(Category.STOP_PAYMENT)), String.format("%14s", fmtAmount(sumAmount(Category.STOP_PAYMENT))),
                fmtCount(sumCount(Category.ARKANSAS_PAYMENT)), String.format("%14s", fmtAmount(sumAmount(Category.ARKANSAS_PAYMENT))),
                fmtCount(sumCount(Category.TOTAL)), String.format("%11s", fmtAmount(sumAmount(Category.TOTAL)))
        ));
        out.newLine();
        out.write("            |                   |                   |                   |");
        out.newLine();
        out.write(" ====================================================================================================================================");
        out.newLine();
        out.newLine();
    }

    // Write buffered receipt subtotals using the same fixed templates as sample
    private void writeBufferedReceiptSubtotalBlock(String receiptType, Map<Category, EnumMap<AgeBucket, CountAmountDTO>> map) throws Exception {
        out.write(String.format(" SUBTOTAL FOR RECEIPT-TYPE: %s", receiptType));
        out.newLine();
        out.write(" ---------------------------------------------------------------------------------------------------------------------------------");
        out.newLine();
        out.write(String.format("%-11s | %-18s | %-18s | %-18s | %-18s | %-16s | %-14s |", "", "PHYSICIAN", "HOSPITAL/ACCTS", "MEDIPAK", "GCPS", "SUBTOTAL", "BAD/ADDR"));
        out.newLine();
        out.write(" ---------------------------------------------------------------------------------------------------------------------------------");
        out.newLine();

        for (AgeBucket b : AgeBucket.values()) {
            CountAmountDTO ph = safeDto(map, Category.PHYSICIAN, b);
            CountAmountDTO ho = safeDto(map, Category.HOSPITAL, b);
            CountAmountDTO ma = safeDto(map, Category.MEDIPAK, b);
            CountAmountDTO gc = safeDto(map, Category.GCPS, b);
            CountAmountDTO sub = safeDto(map, Category.SUBTOTAL_RETURNED, b);
            CountAmountDTO bad = safeDto(map, Category.BAD_ADDRESS, b);
            CountAmountDTO off = safeDto(map, Category.OFFSET, b);

            out.write(String.format(" %s | %4d %12s | %4d %12s | %4d %12s | %4d %12s | %4d %12s | %4d %12s |",
                    padRight(bucketLabelShort(b), 11),
                    ph.count, String.format("%12s", fmtAmount(ph.amount)),
                    ho.count, String.format("%12s", fmtAmount(ho.amount)),
                    ma.count, String.format("%12s", fmtAmount(ma.amount)),
                    gc.count, String.format("%12s", fmtAmount(gc.amount)),
                    sub.count, String.format("%12s", fmtAmount(sub.amount)),
                    bad.count, String.format("%12s", fmtAmount(bad.amount))
            ));
            out.newLine();
        }
        out.write(" ---------------------------------------------------------------------------------------------------------------------------------");
        out.newLine();
        out.newLine();
    }


    private CountAmountDTO safeDto(Map<Category, EnumMap<AgeBucket, CountAmountDTO>> map, Category c, AgeBucket b) {
        if (map == null) return new CountAmountDTO(0, BigDecimal.ZERO);
        var em = map.get(c);
        if (em == null) return new CountAmountDTO(0, BigDecimal.ZERO);
        var dto = em.get(b);
        if (dto == null) return new CountAmountDTO(0, BigDecimal.ZERO);
        return dto;
    }

    private Map<Category, EnumMap<AgeBucket, CountAmountDTO>> snapshotFromData() {
        Map<Category, EnumMap<AgeBucket, CountAmountDTO>> outMap = new LinkedHashMap<>();
        for (Map.Entry<Category, EnumMap<AgeBucket, CountAmount>> e : data.entrySet()) {
            EnumMap<AgeBucket, CountAmountDTO> em = new EnumMap<>(AgeBucket.class);
            for (AgeBucket b : AgeBucket.values()) {
                CountAmount ca = e.getValue().get(b);
                em.put(b, new CountAmountDTO(ca.count, ca.amount));
            }
            outMap.put(e.getKey(), em);
        }
        return outMap;
    }

    private long sumCount(Category c) {
        long s = 0;
        for (AgeBucket b : AgeBucket.values()) s += data.get(c).get(b).count;
        return s;
    }

    private BigDecimal sumAmount(Category c) {
        BigDecimal s = BigDecimal.ZERO;
        for (AgeBucket b : AgeBucket.values()) s = s.add(data.get(c).get(b).amount);
        return s;
    }
    
    // ---------------- Footer (explicitly provided to fix "writeFooter() undefined" error) ----------------
    private void writeFooter() throws Exception {
        out.write("\n   THE ABOVE DATA REPRESENTS THE TOTAL BLUE CROSS AND BLUE SHIELD RETURNED CHECKS UNDER CONTROL AS OF    "
                + LocalDate.now().minusMonths(1).format(CNTRL_DATE_FMT) + ".");
        out.newLine();
        out.write("       (INCLUDED IN THIS REPORT ARE THE CHECKS RETURNED TO OUR OFFICE DUE TO INSUFFICIENT MAILING ADDRESSES.)");
        out.newLine();
        out.newLine();
        out.write("         * - INCLUDES ALL MANUAL CHECKS");
        out.newLine();
        out.write("        ** - INCLUDES PHYSICIAN PAYABLE, HOSPITAL, ACCOUNTS PAYABLE, MEDIPAK AND GCPS");
        out.newLine();
        out.newLine();
        out.write("   THESE CHECKS WILL BE RE-MAILED, CANCELLED, OR RE-ISSUED.");
        out.newLine();
        out.newLine();
        out.write("\n ****************************************************  END OF P09330-A REPORT  ******************************************************");
        out.newLine();
    }

    // --- end of class ---
}
