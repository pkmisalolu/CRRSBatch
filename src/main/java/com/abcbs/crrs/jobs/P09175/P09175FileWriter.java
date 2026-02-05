package com.abcbs.crrs.jobs.P09175;

import com.abcbs.crrs.repository.IOptionRepository;
import com.abcbs.crrs.repository.IP09BatchRepository;
import com.abcbs.crrs.repository.IP09SuspenseRepository;
import com.abcbs.crrs.entity.P09Option;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;

@Log4j2
public class P09175FileWriter implements Tasklet {

    private final String reportPath;
    private final String flatPath;
    private final String controlTotalPath;

    private final IP09BatchRepository batchRepository;       // PX01
    private final IP09SuspenseRepository suspenseRepository; // PX02
    private final IOptionRepository optionRepository;        // V_P09_OPTION (COBOL 000291)

    public P09175FileWriter(String reportPath,
                            String flatPath,
                            String controlTotalPath,
                            IP09BatchRepository batchRepository,
                            IP09SuspenseRepository suspenseRepository,
                            IOptionRepository optionRepository) {
        this.reportPath = reportPath;
        this.flatPath = flatPath;
        this.controlTotalPath = controlTotalPath;
        this.batchRepository = batchRepository;
        this.suspenseRepository = suspenseRepository;
        this.optionRepository = optionRepository;
    }

    @Override
    public RepeatStatus execute(StepContribution contrib, ChunkContext ctx) throws Exception {

        log.info("P09175 started (PX01 → PX02 driven)");

        if (reportPath == null || flatPath == null || controlTotalPath == null) {
            throw new IllegalStateException("One or more output paths are null. reportPath=" + reportPath
                    + ", flatPath=" + flatPath + ", controlTotalPath=" + controlTotalPath);
        }

        BigDecimal totalControlled = BigDecimal.ZERO.setScale(2);
        int formCount = 0;

        try (BufferedWriter report = new BufferedWriter(new FileWriter(reportPath));
             BufferedWriter flat   = new BufferedWriter(new FileWriter(flatPath));
             BufferedWriter total  = new BufferedWriter(new FileWriter(controlTotalPath))) {

            /* ===============================
             * PX01 – Batch cursor
             * =============================== */
            List<P09175BatchView> batches = batchRepository.fetchPx01Cursor();

            for (P09175BatchView batch : batches) {

                /* ===============================
                 * PX02 – Suspense cursor
                 * =============================== */
                List<P09175SuspenseView> suspenseRows =
                        suspenseRepository.fetchPx02Cursor(
                                batch.getBatchPrefix(),
                                batch.getBatchDate(),
                                batch.getBatchSuffix(),
                                batch.getRefundType()
                        );

                for (P09175SuspenseView s : suspenseRows) {

                    // COBOL: Skip report for RET/SPO + IRS
                    if (("RET".equals(nz(batch.getRefundType()).trim())
                            || "SPO".equals(nz(batch.getRefundType()).trim()))
                            && "IRS".equals(nz(s.getReasonCode()).trim())) {

                        log.debug("Skipping report: refundType={}, reasonCode=IRS", batch.getRefundType());
                        continue;
                    }

                    BigDecimal amt = nzBd(s.getControlledAmt()).setScale(2, RoundingMode.HALF_UP);

                    // COBOL totals are only for printed forms
                    totalControlled = totalControlled.add(amt);
                    formCount++;

                    // Option table: build "TO:" description (COBOL 000280/000291)
                    String locationTo = getLocationTo(batch, s);

                    // Write report detail (COBOL DE-REPORT-RECORD-DETAIL)
                    writeReportDetail(report, batch, s, amt, locationTo);

                    // flat archival output
                    flat.write(P09175CcmOutputFormatter.format(s, batch));
                    flat.newLine();
                }

                // COBOL 000295-UPDATE-BATCH-RECORD: move posted indicator once per batch
				
                int rows =
                	    batchRepository.updatePostedIndicator(
                	        "P",                           // or 'T' per COBOL
                	        batch.getBatchSuffix(),
                	        batch.getBatchDate(),
                	        batch.getBatchPrefix(),
                	        batch.getRefundType()
                	    );

                	if (rows != 1) {
                	    throw new IllegalStateException(
                	        "P09175 batch update failed, rows=" + rows
                	    );
                	}
				 
            }

            /* ===============================
             * TOTALS PAGE (REPORT)
             * =============================== */
            writeTotalsPage(report, formCount, totalControlled);

            /* ===============================
             * CONTROL TOTAL FILE
             * =============================== */
            total.write(String.format("%05d        $%s%n", formCount, totalControlled.toPlainString()));
        }

        log.info("P09175 completed. Forms={}, Total={}", formCount, totalControlled);
        return RepeatStatus.FINISHED;
    }

    /* =====================================================
     * Option table (COBOL 000280-GET-LOCATION-DESC + 000291)
     * ===================================================== */
    private String getLocationTo(P09175BatchView batch, P09175SuspenseView s) {

        short recordType = 9; // COBOL: MOVE +9 TO W-RECORD-TYPE

        // COBOL special case:
        // IF CR-REFUND-TYPE = 'OFF' MOVE '130' to W-LOC-NBR and '0001' to W-LOC-NBR-1
        String refundType = nz(batch.getRefundType()).trim();
        String locNbr;
        String locClerk;

        if ("OFF".equals(refundType)) {
            locNbr = "130";
            locClerk = "0001";
        } else {
            locNbr = padRight(nz(s.getLocationNbr()).trim(), 3);
            locClerk = padRight(nz(s.getLocationClerk()).trim(), 4);
        }

        // COBOL W-LOCATION-NARR = W-LOC-NBR(3) + W-LOC-NBR-1(4) + 72 '%' characters
        // DB2 LIKE needs just prefix + %
        String likeValue = locNbr + locClerk + "%";

        List<P09Option> opts = optionRepository.findP09175Option(recordType, likeValue);
        if (opts == null || opts.isEmpty()) {
            // COBOL would CANCEL on SQL error; for "not found" you can keep blank to avoid crash
            log.warn("Option not found for recordType={}, like={}", recordType, likeValue);
            return ""; // DATA-0010 blank
        }

        // COBOL moves LC-FIELD-NARR to W-LOCATION-TO (the narrative description)
        P09Option o = opts.get(0);
        try {
            return nz(o.getOptFieldNarr()).trim();
        } catch (Exception e) {
            // if entity getter name differs in your model, adjust here
            log.warn("Unable to read optFieldNarr from P09Option; returning blank. {}", e.getMessage());
            return "";
        }
    }

    /* =====================================================
     * DE-REPORT-RECORD-DETAIL (COBOL LINE-0001 .. LINE-0177)
     * ===================================================== */
    private void writeReportDetail(BufferedWriter report,
                                   P09175BatchView batch,
                                   P09175SuspenseView r,
                                   BigDecimal controlledAmt,
                                   String locationTo) throws Exception {

        // ===== COBOL LINE-0001 (barcode line, 81 chars) =====
        // 1 + space + CCM-INFO(1+6+4+14) + claim(14) + contract(20) + doctype(5) + LOB(4) + LOC(3)
        String refundType3 = nz(batch.getRefundType()).trim(); // report uses suspense refund type in COBOL; here same as batch
        String barcodeRefund = refundType3.isEmpty() ? " " : refundType3.substring(0, 1);
        String barcodeCntlDt = mmddyyNoSpaces(r.getCntrlDate());       // W-CONTROL-DATE → BARCODE-CNTL-DT
        String barcodeCntlNbr = padRight(nz(r.getCntrlNbr()).trim(), 4);
        String barcodeChkNbr = padRight(nz(r.getCheckNbr()).trim(), 14);
        String barcodeClaimNo = padRight("", 14);                      // COBOL BARCODE-CLAIM-NO (blank in sample)
        String barcodeContract = padRight(nz(r.getMbrIdNbr()).trim(), 20); // BARCODE-CONTRACT-NO
        String barcodeDocType = padRight("CCM", 5);                    // BARCODE-DOCTYPE
        String barcodeLob = ("210".equals(nz(r.getLocationNbr()).trim()) || "137".equals(nz(r.getLocationNbr()).trim()))
                ? "FEP "
                : "BCBS";
        String barcodeLoc = padRight(nz(r.getLocationNbr()).trim(), 3); // BARCODE-LOC-NBR

        report.write("1 " + barcodeRefund + barcodeCntlDt + barcodeCntlNbr + barcodeChkNbr
                + barcodeClaimNo + barcodeContract + barcodeDocType + barcodeLob + barcodeLoc);
        report.newLine();

        // LINE-0006
        report.write("                                CHECK CONTROL MEMO");
        report.newLine();

        // LINE-0008 (TO + FROM)
        report.write("0TO: " + padRight(locationTo, 20) + "    FROM: CONTROLLER-CLAIMS REFUNDS    LOCATION: 1300001");
        report.newLine();

        // LINE-0013 (#:)
        report.write("  #: " + padRight(nz(r.getLocationNbr()).trim(), 3) + " " + padRight(nz(r.getLocationClerk()).trim(), 4));
        report.newLine();

        // LINE-0017 (REROUTED TO)
        report.write(" REROUTED TO: _________________________________________   DATE: _____/_____/_____");
        report.newLine();

        // LINE-0021 (OTIS + GCPS SEC)
        report.write(String.format("0OTIS #: %-13s%48sGCPS SEC: %-2s",
                padRight(nz(r.getOtisNbr()).trim(), 13),
                "",
                padRight(nz(r.getSectionCode()).trim(), 2)));
        report.newLine();

        // LINE-0026 (REFUND TYPE + CONTROL DATE + CONTROL #)
        report.write(String.format("0REFUND TYPE:  %-3s           CONTROL DATE: %s        CONTROL #: %-4s",
                padRight(refundType3, 3),
                mmddyyWithDoubleSpaces(r.getCntrlDate()),
                padRight(nz(r.getCntrlNbr()).trim(), 4)));
        report.newLine();

        // LINE-0035 (STATUS + STATUS DATE + EOB/RA)
        report.write(String.format("0STATUS: %-6s%14sSTATUS DATE: %s         EOB/RA ATTACHED: %-1s",
                padRight(nz(batch.getStatus()).trim(), 6),
                "",
                mmddyyWithDoubleSpaces(batch.getStatusDate()),
                padRight(nz(r.getEobRaInd()).trim(), 1)));
        report.newLine();

        // LINE-0044 (RECEIPT TYPE + REMITT NAME + TYPE) with title comma logic
        String remittTitle = nz(r.getRemittorTitle()).trim();
        String titlePart;
        if (remittTitle.isEmpty()
                || "OF1".equals(remittTitle) || "OF2".equals(remittTitle) || "OF3".equals(remittTitle)
                || "OF4".equals(remittTitle) || "OF5".equals(remittTitle)) {
            titlePart = "";
        } else {
            titlePart = "," + remittTitle;
        }

        // In COBOL, title comma+title is positioned after name; we’ll append in-name field area
        String remittName36 = padRight(nz(r.getRemittorName()).trim(), 36);
        if (!titlePart.isEmpty()) {
            // try to fit ",TITLE" into 36 by trimming name if needed
            String t = titlePart;
            int room = 36 - t.length();
            if (room < 0) room = 0;
            remittName36 = padRight(nz(r.getRemittorName()).trim(), room) + padRight(t, 36 - room);
        }

        report.write(String.format(" RECEIPT TYPE: %-2s   REMITT NAME: %-36s                TYPE: %-1s",
                padRight(nz(r.getReceiptType()).trim(), 2),
                remittName36,
                padRight(nz(r.getRemittorType()).trim(), 1)));
        report.newLine();

        // LINE-0051 (CLAIMS TYPE + OPL + LETTER DATE + REASON CODE)
        report.write(String.format(" CLAIMS TYPE: %-4s   OPL: %-1s   LETTER DATE: %-18sREASON CODE: %-4s",
                padRight(nz(r.getClaimType()).trim(), 4),
                padRight(nz(r.getOplInd()).trim(), 1),
                (r.getLetterDate() == null ? "" : mmddyyWithDoubleSpaces(r.getLetterDate())),
                padRight(nz(r.getReasonCode()).trim(), 4)));
        report.newLine();

        // LINE-0062 (OTHER CORRESPONDENCE)
        report.write(String.format("0OTHER CORRESPONDENCE REC'D: %-21s", padRight(nz(r.getOtherCorr()).trim(), 21)));
        report.newLine();

        // LINE-0067 (COMMENTS)
        report.write(String.format(" COMMENTS:  %-65s", padRight(nz(r.getCommentText()).trim(), 65)));
        report.newLine();

        // LINE-0070 (PATIENT NAME + ID #)
        report.write(String.format(" PATIENT NAME:  %-11s %-15s               ID #: %-12s",
                padRight(nz(r.getPatientFirst()).trim(), 11),
                padRight(nz(r.getPatientLast()).trim(), 15),
                padRight(nz(r.getMbrIdNbr()).trim(), 12)));
        report.newLine();

        // LINE-0076 (CHECK ADDRESSEE: remitt name + optional title)
        String addressee = nz(r.getRemittorName()).trim();
        if (!titlePart.isEmpty()) addressee = addressee + titlePart;
        report.write("0CHECK ADDRESSEE:  " + padRight(addressee, 36));
        report.newLine();

        // LINE-0079/0082 (addresses)
        report.write("       ADDRESS 1:  " + padRight(nz(r.getChkAddress1()).trim(), 36));
        report.newLine();
        report.write("       ADDRESS 2:  " + padRight(nz(r.getChkAddress2()).trim(), 36));
        report.newLine();

        // LINE-0085 (CITY/STATE/ZIP:)
        report.write(String.format(" CITY/STATE/ZIP:   %-15s %-2s %-5s %-4s",
                padRight(nz(r.getChkCity()).trim(), 15),
                padRight(nz(r.getChkState()).trim(), 2),
                padRight(nz(r.getChkZip5()).trim(), 5),
                padRight(nz(r.getChkZip4()).trim(), 4)));
        report.newLine();

        // LINE-0093 (CHECK DATE + CHECK NBR + CHECK AMOUNT)
        report.write(String.format("0CHECK DATE: %s    CHECK NBR: %-8s     CHECK AMOUNT:         $%s",
                mmddyyWithDoubleSpaces(r.getCheckDate()),
                padRight(nz(r.getCheckNbr()).trim(), 8),
                moneyMask(nzBd(r.getCheckAmt()))));
        report.newLine();

        // LINE-0102 (CONTROLLED AMOUNT)
        report.write(String.format("0   PLEASE WORK THIS CASH RECEIPT - CONTROLLED AMOUNT =========>          $%s",
                moneyMask(controlledAmt)));
        report.newLine();

        // LINE-0108
        report.write(" ===============================================================================");
        report.newLine();

        // LINE-0110
        report.write("                             FOR CLAIMS DIVISION USE");
        report.newLine();

        // LINE-0112/0116/0119/0122
        report.write("0REQUESTED ACTIONS:  RAA  REQUEST ACCEPT AMOUNT RECEIVED");
        report.newLine();
        report.write("                     RCK  REQUEST OVER REFUNDED CHECK");
        report.newLine();
        report.write("                     RRE  REQUEST REMAIL OF RETURNED CHECKS");
        report.newLine();
        report.write("                     OTH  OTHER ACTION REQUESTED-MAKE COMMENTS");
        report.newLine();

        // LINE-0125
        report.write("0");
        report.newLine();
        report.write("   ACT      ADJ. CLAIM NUMBER         ADJ DATE              AMOUNT          C");
        report.newLine();

        // LINE-0130..0158 (5 blank rows like COBOL)
        for (int i = 0; i < 5; i++) {
            report.write("0_______    _________________     _____/_____/_____     ______________    ______");
            report.newLine();
        }

        // LINE-0165
        report.write("0                                               TOTAL   ==============");
        report.newLine();

        // LINE-0167/0170/0172
        report.write("0COMMENTS:  __________________________________________________________");
        report.newLine();
        report.write("0_____________________________________________________________________");
        report.newLine();
        report.write("0_____________________________________________________________________");
        report.newLine();

        // LINE-0174 (forward-to code)
        String forwardTo = computeForwardTo(refundType3);
        report.write("0");
        report.newLine();
        report.write(" UPON COMPLETION FORWARD TO:  " + padRight(forwardTo, 7));
        report.newLine();
        report.write("0");
        report.newLine();
    }

    private String computeForwardTo(String refundType3) {
        // COBOL:
        // IF refundType='PER' 1350001
        // ELSE IF 'OTH' spaces
        // ELSE 1300001
        String rt = nz(refundType3).trim();
        if ("PER".equals(rt)) return "1350001";
        if ("OTH".equals(rt)) return "";
        return "1300001";
    }

    /* =====================================================
     * Totals page (COBOL LINE-0179..0197)
     * ===================================================== */
    private void writeTotalsPage(BufferedWriter report, int count, BigDecimal total) throws Exception {

        report.write("1                               CHECK CONTROL MEMO");
        report.newLine();
        report.write("                                    TOTALS PAGE");
        report.newLine();
        report.write("0");
        report.newLine();

        // COBOL DATA-0187 PIC Z(4)9 (width 5)
        report.write(String.format(" TOTAL NUMBER OF FORMS ==========>%7s",
                padLeft(String.valueOf(count), 5)));
        report.newLine();

        report.write("0");
        report.newLine();

        // COBOL DATA-0192 PIC $$$$,$$$,$$9.99-
        report.write(" TOTAL CONTROLLED AMOUNT ========>          $" + moneyMask(total));
        report.newLine();

        // LINE-0197 (spaces)
        for (int i = 0; i < 22; i++) {
            report.write("0");
            report.newLine();
        }

        report.write("0          **************************   END OF REPORT # P09175  ****************");
        report.newLine();
        report.write("0");
        report.newLine();
    }

    /* =====================
     * Helpers (COBOL-ish)
     * ===================== */
    private static String nz(String s) { return s == null ? "" : s; }

    private static BigDecimal nzBd(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static String padRight(String s, int len) {
        s = nz(s);
        if (s.length() >= len) return s.substring(0, len);
        return s + " ".repeat(len - s.length());
    }

    private static String padLeft(String s, int len) {
        s = nz(s);
        if (s.length() >= len) return s.substring(s.length() - len);
        return " ".repeat(len - s.length()) + s;
    }

    private static String mmddyyNoSpaces(LocalDate d) {
        if (d == null) return "000000";
        return String.format("%02d%02d%02d", d.getMonthValue(), d.getDayOfMonth(), d.getYear() % 100);
    }

    // COBOL prints "08  31  23" (two spaces between parts)
    private static String mmddyyWithDoubleSpaces(LocalDate d) {
        if (d == null) return "00  00  00";
        return String.format("%02d  %02d  %02d", d.getMonthValue(), d.getDayOfMonth(), d.getYear() % 100);
    }

    // Approximate COBOL $$$$,$$$,$$9.99 (right-aligned)
    private static String moneyMask(BigDecimal v) {
        v = nzBd(v).setScale(2, RoundingMode.HALF_UP);
        DecimalFormat df = new DecimalFormat("###,###,##0.00");
        String s = df.format(v);
        // COBOL field is 13 characters for "$$$$,$$$,$$9.99"
        // Your report already prints "$" before it in some lines; keep just number here
        return padLeft(s, 13);
    }
}