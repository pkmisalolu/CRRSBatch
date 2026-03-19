package com.abcbs.crrs.jobs.P09175;

import com.abcbs.crrs.entity.P09Option;
import com.abcbs.crrs.repository.IOptionRepository;
import com.abcbs.crrs.repository.IP09BatchRepository;
import com.abcbs.crrs.repository.IP09SuspenseRepository;
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

    private static final String POSTED_IND_TO_PROCESS = "T";
    private static final String POSTED_IND_PROCESSED = "P";

    private final String reportPath;
    private final String flatPath;
    private final String controlTotalPath;

    private final IP09BatchRepository batchRepository;
    private final IP09SuspenseRepository suspenseRepository;
    private final IOptionRepository optionRepository;

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
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        String currentCheckpointKey = initialCheckpointKey();

        log.info("P09175 started (PX01 → PX02 driven), initialCheckpointKey={}", displayCheckpointKey(currentCheckpointKey));

        validatePaths();

        BigDecimal totalControlled = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int formCount = 0;

        try (BufferedWriter report = new BufferedWriter(new FileWriter(reportPath));
             BufferedWriter flat = new BufferedWriter(new FileWriter(flatPath));
             BufferedWriter total = new BufferedWriter(new FileWriter(controlTotalPath))) {

            while (true) {
                List<P09175BatchView> batches = performPx01BatchCursor(currentCheckpointKey);
                if (batches == null || batches.isEmpty()) {
                    break;
                }

                for (P09175BatchView batch : batches) {
                    BatchTotals batchTotals = processSingleBatch(batch, report, flat);
                    formCount += batchTotals.formCount();
                    totalControlled = totalControlled.add(batchTotals.totalControlled());

                    currentCheckpointKey = buildCheckpointKey(batch);
                    log.debug("W-CHKPNT-KEY updated to {}", currentCheckpointKey);

                    performUpdateBatchRecord(batch);
                }
            }

            writeTotalsPage(report, formCount, totalControlled);
            total.write(buildControlTotalRecord(formCount, totalControlled));
            total.newLine();
        }

        log.info("P09175 completed. Forms={}, Total={}, lastCheckpointKey={}",
                formCount, totalControlled, displayCheckpointKey(currentCheckpointKey));

        return RepeatStatus.FINISHED;
    }

    private void validatePaths() {
        if (isBlank(reportPath) || isBlank(flatPath) || isBlank(controlTotalPath)) {
            throw new IllegalStateException(
                    "One or more output paths are blank/null. reportPath=" + reportPath
                            + ", flatPath=" + flatPath
                            + ", controlTotalPath=" + controlTotalPath
            );
        }
    }

    private List<P09175BatchView> performPx01BatchCursor(String currentCheckpointKey) {
        return batchRepository.fetchPx01Cursor(currentCheckpointKey);
    }

    private String initialCheckpointKey() {
        return "\u0000".repeat(14);
    }

    private String displayCheckpointKey(String checkpointKey) {
        if (checkpointKey == null || checkpointKey.chars().allMatch(ch -> ch == 0)) {
            return "<LOW-VALUES>";
        }
        if (checkpointKey.trim().isEmpty()) {
            return "<SPACES>";
        }
        return checkpointKey;
    }

    private String buildCheckpointKey(P09175BatchView batch) {
        return padRight(nz(batch.getRefundType()), 3)
             + padRight(nz(batch.getBatchPrefix()), 3)
             + padRight(nz(batch.getBatchDate()), 6)
             + padRight(nz(batch.getBatchSuffix()), 2);
    }

    private BatchTotals processSingleBatch(P09175BatchView batch,
                                           BufferedWriter report,
                                           BufferedWriter flat) throws Exception {
        BigDecimal batchControlled = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int batchFormCount = 0;

        List<P09175SuspenseView> suspenseRows = suspenseRepository.fetchPx02Cursor(
                batch.getBatchPrefix(),
                batch.getBatchDate(),
                batch.getBatchSuffix(),
                batch.getRefundType()
        );

        for (P09175SuspenseView suspense : suspenseRows) {
            if (shouldSkipDetail(batch, suspense)) {
                log.debug("Skipping detail for refundType={}, reasonCode={}",
                        nz(batch.getRefundType()).trim(),
                        nz(suspense.getReasonCode()).trim());
                continue;
            }

            BigDecimal controlledAmt = nzBd(suspense.getControlledAmt()).setScale(2, RoundingMode.HALF_UP);
            String locationTo = getLocationTo(batch, suspense);

            writeReportDetail(report, batch, suspense, controlledAmt, locationTo);

            flat.write(P09175CcmOutputFormatter.format(suspense, batch, locationTo));
            flat.newLine();

            batchControlled = batchControlled.add(controlledAmt);
            batchFormCount++;
        }

        return new BatchTotals(batchFormCount, batchControlled);
    }

    private boolean shouldSkipDetail(P09175BatchView batch, P09175SuspenseView suspense) {
        String refundType = nz(batch.getRefundType()).trim();
        String reasonCode = nz(suspense.getReasonCode()).trim();
        return ("RET".equals(refundType) || "SPO".equals(refundType)) && "IRS".equals(reasonCode);
    }

    private void performUpdateBatchRecord(P09175BatchView batch) {
        int rows = batchRepository.updatePostedIndicator(
                POSTED_IND_PROCESSED,
                batch.getBatchSuffix(),
                batch.getBatchDate(),
                batch.getBatchPrefix(),
                batch.getRefundType(),
                POSTED_IND_TO_PROCESS
        );

        if (rows != 1) {
            throw new IllegalStateException(
                    "P09175 batch update failed. rows=" + rows
                            + ", refundType=" + batch.getRefundType()
                            + ", batchPrefix=" + batch.getBatchPrefix()
                            + ", batchDate=" + batch.getBatchDate()
                            + ", batchSuffix=" + batch.getBatchSuffix()
            );
        }
    }

    private String getLocationTo(P09175BatchView batch, P09175SuspenseView suspense) {
        short recordType = 9;
        String refundType = nz(batch.getRefundType()).trim();

        String locNbr;
        String locClerk;

        if ("OFF".equals(refundType)) {
            locNbr = "130";
            locClerk = "0001";
        } else {
            locNbr = padRight(nz(suspense.getLocationNbr()).trim(), 3);
            locClerk = padRight(nz(suspense.getLocationClerk()).trim(), 4);
        }

        String likeValue = locNbr + locClerk + "%";
        List<P09Option> options = optionRepository.findP09175Option(recordType, likeValue);

        if (options == null || options.isEmpty()) {
            log.warn("Option not found for recordType={}, like={}", recordType, likeValue);
            return "";
        }

        P09Option option = options.get(0);
        try {
            return nz(option.getOptFieldNarr()).trim();
        } catch (Exception ex) {
            log.warn("Unable to read optFieldNarr from P09Option; returning blank. {}", ex.getMessage());
            return "";
        }
    }

    private void writeReportDetail(BufferedWriter report,
                                   P09175BatchView batch,
                                   P09175SuspenseView record,
                                   BigDecimal controlledAmt,
                                   String locationTo) throws Exception {

        String refundType3 = nz(batch.getRefundType()).trim();
        String barcodeRefund = refundType3.isEmpty() ? " " : refundType3.substring(0, 1);
        String barcodeCntlDt = mmddyyNoSpaces(record.getCntrlDate());
        String barcodeCntlNbr = padRight(nz(record.getCntrlNbr()).trim(), 4);
        String barcodeChkNbr = padRight(nz(record.getCheckNbr()).trim(), 14);
        String barcodeClaimNo = padRight("", 14);
        String barcodeContract = padRight(nz(record.getMbrIdNbr()).trim(), 20);
        String barcodeDocType = padRight("CCM", 5);
        String barcodeLob = ("210".equals(nz(record.getLocationNbr()).trim())
                || "137".equals(nz(record.getLocationNbr()).trim()))
                ? "FEP "
                : "BCBS";
        String barcodeLoc = padRight(nz(record.getLocationNbr()).trim(), 3);

        report.write("1 " + barcodeRefund + barcodeCntlDt + barcodeCntlNbr + barcodeChkNbr
                + barcodeClaimNo + barcodeContract + barcodeDocType + barcodeLob + barcodeLoc);
        report.newLine();

        report.write("                                CHECK CONTROL MEMO");
        report.newLine();

        report.write("0TO: " + padRight(locationTo, 20) + "    FROM: CONTROLLER-CLAIMS REFUNDS    LOCATION: 1300001");
        report.newLine();

        report.write("  #: " + padRight(nz(record.getLocationNbr()).trim(), 3) + " " + padRight(nz(record.getLocationClerk()).trim(), 4));
        report.newLine();

        report.write(" REROUTED TO: _________________________________________   DATE: _____/_____/_____");
        report.newLine();

        report.write(String.format("0OTIS #: %-13s%48sGCPS SEC: %-2s",
                padRight(nz(record.getOtisNbr()).trim(), 13),
                "",
                padRight(nz(record.getSectionCode()).trim(), 2)));
        report.newLine();

        report.write(String.format("0REFUND TYPE:  %-3s           CONTROL DATE: %s        CONTROL #: %-4s",
                padRight(refundType3, 3),
                mmddyyWithDoubleSpaces(record.getCntrlDate()),
                padRight(nz(record.getCntrlNbr()).trim(), 4)));
        report.newLine();

        report.write(String.format("0STATUS: %-6s%14sSTATUS DATE: %s         EOB/RA ATTACHED: %-1s",
                padRight(nz(batch.getStatus()).trim(), 6),
                "",
                mmddyyWithDoubleSpaces(batch.getStatusDate()),
                padRight(nz(record.getEobRaInd()).trim(), 1)));
        report.newLine();

        String remittTitle = nz(record.getRemittorTitle()).trim();
        String titlePart;
        if (remittTitle.isEmpty()
                || "OF1".equals(remittTitle) || "OF2".equals(remittTitle) || "OF3".equals(remittTitle)
                || "OF4".equals(remittTitle) || "OF5".equals(remittTitle)) {
            titlePart = "";
        } else {
            titlePart = "," + remittTitle;
        }

        String remittName36 = padRight(nz(record.getRemittorName()).trim(), 36);
        if (!titlePart.isEmpty()) {
            int room = Math.max(0, 36 - titlePart.length());
            remittName36 = padRight(nz(record.getRemittorName()).trim(), room) + padRight(titlePart, 36 - room);
        }

        report.write(String.format(" RECEIPT TYPE: %-2s   REMITT NAME: %-36s                TYPE: %-1s",
                padRight(nz(record.getReceiptType()).trim(), 2),
                remittName36,
                padRight(nz(record.getRemittorType()).trim(), 1)));
        report.newLine();

        report.write(String.format(" CLAIMS TYPE: %-4s   OPL: %-1s   LETTER DATE: %-18sREASON CODE: %-4s",
                padRight(nz(record.getClaimType()).trim(), 4),
                padRight(nz(record.getOplInd()).trim(), 1),
                (record.getLetterDate() == null ? "" : mmddyyWithDoubleSpaces(record.getLetterDate())),
                padRight(nz(record.getReasonCode()).trim(), 4)));
        report.newLine();

        report.write(String.format("0OTHER CORRESPONDENCE REC'D: %-21s", padRight(nz(record.getOtherCorr()).trim(), 21)));
        report.newLine();

        report.write(String.format(" COMMENTS:  %-65s", padRight(nz(record.getCommentText()).trim(), 65)));
        report.newLine();

        report.write(String.format(" PATIENT NAME:  %-11s %-15s               ID #: %-12s",
                padRight(nz(record.getPatientFirst()).trim(), 11),
                padRight(nz(record.getPatientLast()).trim(), 15),
                padRight(nz(record.getMbrIdNbr()).trim(), 12)));
        report.newLine();

        String addressee = nz(record.getRemittorName()).trim();
        if (!titlePart.isEmpty()) {
            addressee = addressee + titlePart;
        }
        report.write("0CHECK ADDRESSEE:  " + padRight(addressee, 36));
        report.newLine();

        report.write("       ADDRESS 1:  " + padRight(nz(record.getChkAddress1()).trim(), 36));
        report.newLine();

        report.write("       ADDRESS 2:  " + padRight(nz(record.getChkAddress2()).trim(), 36));
        report.newLine();

        report.write(String.format(" CITY/STATE/ZIP:   %-15s %-2s %-5s %-4s",
                padRight(nz(record.getChkCity()).trim(), 15),
                padRight(nz(record.getChkState()).trim(), 2),
                padRight(nz(record.getChkZip5()).trim(), 5),
                padRight(nz(record.getChkZip4()).trim(), 4)));
        report.newLine();

        report.write(String.format("0CHECK DATE: %s    CHECK NBR: %-8s     CHECK AMOUNT:         $%s",
                mmddyyWithDoubleSpaces(record.getCheckDate()),
                padRight(nz(record.getCheckNbr()).trim(), 8),
                moneyMask(nzBd(record.getCheckAmt()))));
        report.newLine();

        report.write(String.format("0   PLEASE WORK THIS CASH RECEIPT - CONTROLLED AMOUNT =========>          $%s",
                moneyMask(controlledAmt)));
        report.newLine();

        report.write(" ===============================================================================");
        report.newLine();

        report.write("                             FOR CLAIMS DIVISION USE");
        report.newLine();

        report.write("0REQUESTED ACTIONS:  RAA  REQUEST ACCEPT AMOUNT RECEIVED");
        report.newLine();
        report.write("                     RCK  REQUEST OVER REFUNDED CHECK");
        report.newLine();
        report.write("                     RRE  REQUEST REMAIL OF RETURNED CHECKS");
        report.newLine();
        report.write("                     OTH  OTHER ACTION REQUESTED-MAKE COMMENTS");
        report.newLine();

        report.write("0");
        report.newLine();
        report.write("   ACT      ADJ. CLAIM NUMBER         ADJ DATE              AMOUNT          C");
        report.newLine();

        for (int i = 0; i < 5; i++) {
            report.write("0_______    _________________     _____/_____/_____     ______________    ______");
            report.newLine();
        }

        report.write("0                                               TOTAL   ==============");
        report.newLine();

        report.write("0COMMENTS:  __________________________________________________________");
        report.newLine();
        report.write("0_____________________________________________________________________");
        report.newLine();
        report.write("0_____________________________________________________________________");
        report.newLine();

        String forwardTo = computeForwardTo(refundType3);
        report.write("0");
        report.newLine();
        report.write(" UPON COMPLETION FORWARD TO:  " + padRight(forwardTo, 7));
        report.newLine();
        report.write("0");
        report.newLine();
    }

    private String computeForwardTo(String refundType3) {
        String rt = nz(refundType3).trim();
        if ("PER".equals(rt)) {
            return "1350001";
        }
        if ("OTH".equals(rt)) {
            return "";
        }
        return "1300001";
    }

    private void writeTotalsPage(BufferedWriter report, int count, BigDecimal total) throws Exception {
        report.write("1                               CHECK CONTROL MEMO");
        report.newLine();
        report.write("                                    TOTALS PAGE");
        report.newLine();
        report.write("0");
        report.newLine();

        report.write(String.format(" TOTAL NUMBER OF FORMS ==========>%7s",
                padLeft(String.valueOf(count), 5)));
        report.newLine();

        report.write("0");
        report.newLine();

        report.write(" TOTAL CONTROLLED AMOUNT ========>          $" + moneyMask(total));
        report.newLine();

        for (int i = 0; i < 22; i++) {
            report.write("0");
            report.newLine();
        }

        report.write("0          **************************   END OF REPORT # P09175  ****************");
        report.newLine();
        report.write("0");
        report.newLine();
    }

    private static String buildControlTotalRecord(int formCount, BigDecimal totalControlled) {
        BigDecimal amount = nzBd(totalControlled).setScale(2, RoundingMode.HALF_UP);

        String formCountField = String.format("%05d", formCount);
        String formattedAmountField = padLeft("$" + amount.toPlainString(), 15);

        String noFormatField = amount.movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString();
        noFormatField = String.format("%011d", Long.parseLong(noFormatField));

        return formCountField + formattedAmountField + noFormatField;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static BigDecimal nzBd(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String padRight(String value, int len) {
        String v = nz(value);
        if (v.length() >= len) {
            return v.substring(0, len);
        }
        return v + " ".repeat(len - v.length());
    }

    private static String padLeft(String value, int len) {
        String v = nz(value);
        if (v.length() >= len) {
            return v.substring(v.length() - len);
        }
        return " ".repeat(len - v.length()) + v;
    }

    private static String mmddyyNoSpaces(LocalDate date) {
        if (date == null) {
            return "000000";
        }
        return String.format("%02d%02d%02d",
                date.getMonthValue(),
                date.getDayOfMonth(),
                date.getYear() % 100);
    }

    private static String mmddyyWithDoubleSpaces(LocalDate date) {
        if (date == null) {
            return "00  00  00";
        }
        return String.format("%02d  %02d  %02d",
                date.getMonthValue(),
                date.getDayOfMonth(),
                date.getYear() % 100);
    }

    private static String moneyMask(BigDecimal value) {
        BigDecimal v = nzBd(value).setScale(2, RoundingMode.HALF_UP);
        DecimalFormat df = new DecimalFormat("###,###,##0.00");
        String s = df.format(v);
        return padLeft(s, 13);
    }

    private record BatchTotals(int formCount, BigDecimal totalControlled) {
    }
}