package com.abcbs.crrs.jobs.P09352;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.abcbs.crrs.entity.BankRecon;
import com.abcbs.crrs.entity.CashReceiptPK;
import com.abcbs.crrs.entity.CheckControl;
import com.abcbs.crrs.entity.CheckControlPK;
import com.abcbs.crrs.entity.P09Activity;
import com.abcbs.crrs.entity.P09CashReceipt;
import com.abcbs.crrs.entity.P09Option;
import com.abcbs.crrs.entity.P09Summary;
import com.abcbs.crrs.entity.SummaryPK;
import com.abcbs.crrs.entity.ActivityPK;
import com.abcbs.crrs.repository.IActivityRepository;
import com.abcbs.crrs.repository.IBankReconRepository;
import com.abcbs.crrs.repository.ICheckControlRepository;
import com.abcbs.crrs.repository.IOptionRepository;
import com.abcbs.crrs.repository.IP09CashReceiptRepository;
import com.abcbs.crrs.repository.IP09ControlRepository;
import com.abcbs.crrs.repository.IP09SummaryRepository;

@Component("p09352Processor")
@StepScope
public class P09352Processor implements ItemProcessor<P09352ControlCardInput, P09352OutputWrapper>, StepExecutionListener {

    private static final Logger log = LogManager.getLogger(P09352Processor.class);

    // ============================================================
    // NOTE: Do not store large/non-serializable objects in ExecutionContext.
    // These lists hold output records for the current run.
    // ============================================================
    private final List<P09352ApInterfaceOutput> outApRecords = new ArrayList<>();
    private final List<P09352XP09DedsOutput> outGlRecords = new ArrayList<>();
    private final List<P09352XP07DedsOutput> outOccsRecords = new ArrayList<>();

    // ---- ExecutionContext keys (keep stable, COBOL-friendly names) ----
    private static final String KEY_ISSUED_CHK_TABLE = "ISSUED_CHK_TABLE";
    private static final String KEY_T_CR_REFUND_TYPE_TABLE = "T_CR_REFUND_TYPE_TABLE";
    private static final String KEY_CURRENT_CASH_RECEIPT = "CURRENT_CASH_RECEIPT";
    private static final String KEY_CURRENT_ACTIVITY = "CURRENT_ACTIVITY";
    private static final String KEY_CURRENT_ACTIVITIES = "CURRENT_ACTIVITIES";
    private static final String KEY_CURRENT_CHECK_CONTROL = "CURRENT_CHECK_CONTROL";
    private static final String KEY_CURRENT_CASH_RECEIPT_ID = "CURRENT_CASH_RECEIPT_ID";
    private static final String KEY_CURRENT_ACTIVITY_ID     = "CURRENT_ACTIVITY_ID";
    private static final String KEY_CURRENT_CHECK_CONTROL_ID = "CURRENT_CHECK_CONTROL_ID";

    // 215000 outputs used later
    private static final String KEY_WS_PER_RPT_DATE_MMYY = "WS_PER_RPT_DATE_MMYY";
    private static final String KEY_WS_UND_RPT_DATE_MMYY = "WS_UND_RPT_DATE_MMYY";
    private static final String KEY_WS_OFF_RPT_DATE_MMYY = "WS_OFF_RPT_DATE_MMYY";
    private static final String KEY_WS_RET_RPT_DATE_MMYY = "WS_RET_RPT_DATE_MMYY";
    private static final String KEY_WS_SPO_RPT_DATE_MMYY = "WS_SPO_RPT_DATE_MMYY";

    private static final String KEY_WS_RET_RPT_DATE_YYMM = "WS_RET_RPT_DATE_YYMM";
    private static final String KEY_WS_UND_RPT_DATE_YYMM = "WS_UND_RPT_DATE_YYMM";
    private static final String KEY_WS_SPO_RPT_DATE_YYMM = "WS_SPO_RPT_DATE_YYMM";

    private static final String KEY_WS_RET_CONTROL_TO_DATE = "WS_RET_CONTROL_TO_DATE";
    private static final String KEY_WS_UND_CONTROL_TO_DATE = "WS_UND_CONTROL_TO_DATE";
    private static final String KEY_WS_SPO_CONTROL_TO_DATE = "WS_SPO_CONTROL_TO_DATE";

    @Autowired
    private IP09SummaryRepository p09SummaryRepository;

    @Autowired
    @Qualifier("corpCardReader")
    private FlatFileItemReader<P09352CorpCardInput> corpCardReader;

    @Autowired
    @Qualifier("checkpointReader")
    private FlatFileItemReader<P09352CheckpointCardInput> checkpointReader;

    @Autowired
    @Qualifier("inputVoucherReader")
    private FlatFileItemReader<P09352InputVoucher> inputVoucherReader;

    @Autowired
    private IOptionRepository optionRepository;

    @Autowired
    private IP09CashReceiptRepository cashReceiptRepository;

    @Autowired
    private IP09ControlRepository p09ControlRepository;

    @Autowired
    private IActivityRepository activityRepository;
    
    @Autowired
    private IBankReconRepository bankReconRepository;

    @Autowired
    private ICheckControlRepository checkControlRepository;

    @Autowired
    private P09352ReportWriter reportWriter;

    private StepExecution stepExecution;

    @Value("#{jobParameters['jobType']}")
    private String jobType;

    @Value("#{jobParameters['datePicker']}")
    private String datePicker;
    
    // ACCEPT WS-C2-TIME-OF-DAY FROM TIME
    private String wsFormattedHH;
    private String wsFormattedMM;
    private String wsFormattedSS;

    // ACCEPT WS-C2-CURRENT-DATE FROM DATE
    private String wsCurrentYY;
    private String wsCurrentMM;
    private String wsCurrentDD;

    // DB2 current date parts
    private String wsDb2CurrCentury;
    private String wsDb2CurrYear;
    private String wsDb2CurrMonth;
    private String wsDb2CurrDay;

    // CONTROL-CARD compare date parts
    private String wsDb2Century;
    private String wsDb2Year;
    private String wsDb2Month;
    private String wsDb2Day;
    private String wsDb2ControlCardDate; // ISO CCYY-MM-DD

    private String wsCorp;
    private int wsChkpCardCnt;

    private String wsLastVoucherNbrPrefix;
    private int wsLastVoucherNbrSuffix;

    private boolean tpGoBackFlg;
    private boolean resetPositionFlg;

    private int tTblPntr;

    /**
     * COBOL TABLE ENTRY for 480000-BLD-ISSUED-CHK-RCD.
     * Stored in ExecutionContext for later paragraphs (500000+).
     */
    private static class IssuedChkRow implements Serializable {
        private static final long serialVersionUID = 1L;

        private ActivityPK sourceActivityId;

        public ActivityPK getSourceActivityId() {
            return sourceActivityId;
        }
        public void setSourceActivityId(ActivityPK sourceActivityId) {
            this.sourceActivityId = sourceActivityId;
        }
        
        String crRefundType;
        LocalDate crCntrlDate;
        String crCntrlNbr;
        String crRemDailyInd;
        
        LocalDate crStatusDate;
        String crStatusText;
        String crPendFinAct;
        String crReasonCode;

        String crBankAcctNbr;
        String crCheckNbr;
        LocalDate crCheckDate;

        BigDecimal crCheckAmt;
        BigDecimal crCntrldAmt;
        BigDecimal crReceiptBal;
        BigDecimal newCrReceiptBal;

        String crReceiptType;

        String crRemittorName;
        String crRemittorTitle;
        String crRemittorType;

        String crPatientLname;
        String crPatientFname;

        String crProviderNbr;
        String nationalIdNbr;
        String crMbrIdNbr;
        String crTaxIdNbr;
        String crVendorNbr;

        String ap1099Cd;
        String crGlAcctNbr;

        String crLocationNbr;
        String crLocationClerk;

        String crChkAddress1;
        String crChkAddress2;
        String crChkCity;
        String crChkState;
        String crChkZip5;
        String crChkZip4;

        String crRemIdType;
        String crRemNationalId;
        String crRemIdNbr;
        String crRemTaxIdNbr;

        String crRemAddressee;
        String crRemAddress1;
        String crRemAddress2;
        String crRemCity;
        String crRemState;
        String crRemZip5;
        String crRemZip4;

        String crUserId;
        String crCorp;

        // Activity fields
        String actActivity;
        LocalDate actActivityDate;
        BigDecimal actActivityAmt;
        LocalDateTime actTimestamp;
        String actXrefType;
        String actXrefNbr;
        LocalDate actXrefDate;
        String actReportDate;
        String actUserId;
        String actProcessedInd;
        String actDailyInd;

        // OCCS hold fields
        String occsFileIndicator;
        String occsCheckStatus;
        LocalDate occsDbRptDate;
        LocalDate occsCalcRptDate;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;

        // MAIN-SECTION flags
        tpGoBackFlg = false;
        resetPositionFlg = true;

        ExecutionContext ec = stepExecution.getExecutionContext();

        // =========================================================
        // COBOL WORKING-STORAGE INITIALIZATION (ONCE PER STEP)
        // =========================================================
        outApRecords.clear();
        outGlRecords.clear();
        outOccsRecords.clear();

        ec.putInt("checksIssued", 0);
    }

    
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        try {
            // COBOL prints the summary page + END banner at end of run.
            // In Java, ensure it is always written on successful completion.
            if (stepExecution != null
                    && stepExecution.getExitStatus() != null
                    && ExitStatus.COMPLETED.getExitCode().equals(stepExecution.getExitStatus().getExitCode())) {

                if (reportWriter != null) {
                    // if no refund-type section printed, COBOL still shows summary (all zeros)
                    reportWriter.writeFinalSummaryAndEndBanner();
                }
            }
        } catch (Exception ignore) {
            // don't fail the step just because report summary failed; file close still happens
        } finally {
            try { if (reportWriter != null) reportWriter.closeQuietly(); } catch (Exception ignore) {}
        }
        return stepExecution.getExitStatus();
    }

 // ============================================================
 // 100000-INITIALIZATION
 // ============================================================
    private void perform100000Initialization() {
        final ExecutionContext ec = stepExecution.getExecutionContext();

        // ACCEPT TIME
        LocalTime nowTime = LocalTime.now();
        wsFormattedHH = String.format("%02d", nowTime.getHour());
        wsFormattedMM = String.format("%02d", nowTime.getMinute());
        wsFormattedSS = String.format("%02d", nowTime.getSecond());

        // ACCEPT DATE
        LocalDate nowDate = LocalDate.now();
        wsCurrentYY = String.format("%02d", nowDate.getYear() % 100);
        wsCurrentMM = String.format("%02d", nowDate.getMonthValue());
        wsCurrentDD = String.format("%02d", nowDate.getDayOfMonth());

        wsDb2CurrMonth = wsCurrentMM;
        wsDb2CurrDay   = wsCurrentDD;
        wsDb2CurrYear  = wsCurrentYY;
        wsDb2CurrCentury = (wsDb2CurrYear.compareTo("80") > 0) ? "19" : "20";

        // Stable run date/time for report headings
        ec.putString("WS_RUN_DATE_MMDDYY", nowDate.format(DateTimeFormatter.ofPattern("MM/dd/yy")));
        ec.putString("WS_RUN_TIME_HHMMSS", nowTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        // Batch id components
        ec.putString("WS_AP_BATCH_MM", wsCurrentMM);
        ec.putString("WS_AP_BATCH_DD", wsCurrentDD);
        ec.putString("WS_AP_BATCH_ID", wsCurrentMM + wsCurrentDD + "Z");

        // Report header constants
        reportWriter.openIfNeeded(stepExecution);
        ec.putString("REPORT_NUMBER", "P09352-A");
        ec.putString("REPORT_COMPANY_NAME", "ARKANSAS BLUE CROSS AND BLUE SHIELD");
        ec.putString("REPORT_TITLE_LINE2", "FULL AND PARTIAL A/P CHECK REQUESTS");

        // READ CORP-CARD
        P09352CorpCardInput corp = readOne(corpCardReader, "CORP-CARD");
        String corpCodeFromCard = (corp != null ? nvl(corp.getCorpCode()).trim() : "");
        ec.putString("WS_CORP_CODE", corpCodeFromCard);

        // DB2 option lookup (record type 10)
        short wsRecordType = 10;
        String wsLikeValue = corpCodeFromCard + "%";

        List<P09Option> opts;
        try {
            opts = optionRepository.findOptions(wsRecordType, wsLikeValue);
        } catch (Exception ex) {
            dbAbendLikeCobol("DB-OBTAIN", ex);
            throw cancel("DB error reading P09_OPTION (findOptions)");
        }

        if (opts == null || opts.isEmpty()) {
            log.error("ERROR IN READING DCLV-P09-OPTION (no rows) LIKE={}", wsLikeValue);
            logCobolDbErrorDisplays("DB-OBTAIN", null);
            throw cancel("ERROR IN READING DCLV-P09-OPTION (no row found)");
        }

        wsCorp = nvl(opts.get(0).getOptFieldNarr()).trim();
        // COBOL report prints corporation name without repeating the code prefix.
        // Option table often stores like "02 - ARKANSAS ..."; strip leading "<code> - " if present.
        String wsCorpCode = corpCodeFromCard;
        String corpPrefix = wsCorpCode + " -";
        if (wsCorp.startsWith(corpPrefix)) {
            wsCorp = wsCorp.substring(corpPrefix.length()).trim();
        }
        ec.putString("WS_CORP", wsCorp);

        // DB2 current date parts + convenience LocalDate
        ec.putString("WS_DB2_CURR_CENTURY", wsDb2CurrCentury);
        ec.putString("WS_DB2_CURR_YEAR", wsDb2CurrYear);
        ec.putString("WS_DB2_CURR_MONTH", wsDb2CurrMonth);
        ec.putString("WS_DB2_CURR_DAY", wsDb2CurrDay);
        ec.put("WS_DB2_CURRENT_DATE", nowDate);

        // READ CHKP-CARD
        P09352CheckpointCardInput chkp = readOne(checkpointReader, "CHKP-CARD");
        if (chkp == null || !isNumeric(nvl(chkp.getCount()).trim())) {
            cobolDisplayCheckpointInvalid(chkp);
            throw cancel("CHECKPOINT CONTROL CARD IS INVALID FOR PROGRAM P09352");
        }
        wsChkpCardCnt = Integer.parseInt(chkp.getCount().trim());
        ec.putInt("WS_CHKP_CARD_CNT", wsChkpCardCnt);

        // READ INPUT-VOUCHER-NBR
        P09352InputVoucher voucher = readOne(inputVoucherReader, "INPUT-VOUCHER-NBR");
        if (voucher == null) {
            cobolDisplayVoucherInvalid(null);
            throw cancel("VOUCHER-NBR CONTROL CARD IS INVALID FOR PROGRAM P09352 (missing)");
        }

        wsLastVoucherNbrPrefix = nvl(voucher.getInputLastVoucherNbrPrefix()).trim();
        wsLastVoucherNbrSuffix = voucher.getInputLastVoucherNbrSuffix();

        if (wsLastVoucherNbrSuffix < 0) {
            cobolDisplayVoucherInvalid(String.valueOf(wsLastVoucherNbrSuffix));
            throw cancel("LAST VOUCHER NUMBER SUFFIX IS NON-NUMERIC/INVALID");
        }

        ec.putString("WS_LAST_VOUCHER_NBR_PREFIX", wsLastVoucherNbrPrefix);
        ec.putInt("WS_LAST_VOUCHER_NBR_SUFFIX", wsLastVoucherNbrSuffix);

        // ============================================================
        // 110000-INIT-ISSUED-CHK-TBL  (real 700-entry table)
        // ============================================================
        @SuppressWarnings("unchecked")
        List<IssuedChkRow> issuedChkRows = (List<IssuedChkRow>) ec.get(KEY_ISSUED_CHK_TABLE);

        if (issuedChkRows == null) {
            issuedChkRows = new ArrayList<>(700);
            ec.put(KEY_ISSUED_CHK_TABLE, issuedChkRows);
        } else {
            issuedChkRows.clear();
        }

        // COBOL table is 700 entries; blank is represented as null (or empty row)
        for (int i = 0; i < 700; i++) {
            issuedChkRows.add(null);
        }

        // Pointer starts at 1 for "next store"
        tTblPntr = 1;
        ec.putInt("T_TBL_PNTR", tTblPntr);

        // Last real row stored
        ec.putInt("T_TBL_PNTR_MAX", 0);

        // COBOL ends init loop with ptr past end (701)
        ec.putInt("T_TBL_PNTR_AFTER_INIT", 701);

        // Reset per-run counters/flags
        ec.putInt("WS_REFUND_TYPES_PROCESSED", 0);
        ec.put("P09352_ALREADY_RAN", Boolean.FALSE);

        ec.put("TP_GOBACK_FLG", tpGoBackFlg);
        ec.put("RESET_POSITION_FLG", resetPositionFlg);

        log.info("100000-INITIALIZATION complete. WS_CORP_CODE={}, WS_CORP={}, CHKP_CNT={}, LAST_VOUCHER={}{}",
                corpCodeFromCard, wsCorp, wsChkpCardCnt, wsLastVoucherNbrPrefix, wsLastVoucherNbrSuffix);
    }

    // ============================================================
    // PROCESS: control card drives the run
    // ============================================================
    @Override
    public P09352OutputWrapper process(P09352ControlCardInput cc) {
    	String[] mdy = splitToMMDDYY(datePicker);  // [MM, DD, YY]
    	String compareMm = mdy[0];
    	String compareDd = mdy[1];
    	String compareYy = mdy[2];

        final ExecutionContext ec = stepExecution.getExecutionContext();

        // =========================================================
        // ✅ Run 100000-INITIALIZATION ONCE (AFTER readers are OPEN)
        // =========================================================
        Boolean initDone = (Boolean) ec.get("P09352_INIT_DONE");
        if (!Boolean.TRUE.equals(initDone)) {
            perform100000Initialization();          // reads CORP/CHKP/VOUCHER safely here
            ec.put("P09352_INIT_DONE", Boolean.TRUE);
        }

        // =========================================================
        // Run once per StepExecution (your existing guard)
        // =========================================================
        Boolean alreadyRan = (Boolean) ec.get("P09352_ALREADY_RAN");
        if (Boolean.TRUE.equals(alreadyRan)) return null;
        ec.put("P09352_ALREADY_RAN", Boolean.TRUE);

        // CONTROL CARD DATE LOGIC (your 210000 compare date pieces)
        wsDb2Month = nvl(compareMm).trim();
        wsDb2Day   = nvl(compareDd).trim();
        wsDb2Year  = nvl(compareYy).trim();
        wsDb2Century = (wsDb2Year.compareTo("80") > 0) ? "19" : "20";

        wsDb2ControlCardDate =
                wsDb2Century + wsDb2Year + "-" + pad2(wsDb2Month) + "-" + pad2(wsDb2Day);

        
        // --- COBOL alignment: use CONTROL-CARD "compare date" as the report RUN DATE
        // and to build AP BATCH (MMDDZ). This makes the header match the COBOL report.
        wsCurrentMM = pad2(wsDb2Month);
        wsCurrentDD = pad2(wsDb2Day);
        wsCurrentYY = wsDb2Year;
        ec.putString("WS_RUN_DATE_MMDDYY", wsCurrentMM + "/" + wsCurrentDD + "/" + wsCurrentYY);
        ec.putString("WS_AP_BATCH_ID", wsCurrentMM + wsCurrentDD + "Z");
        ec.putString("WS_DB2_CONTROL_CARD_DATE", wsDb2ControlCardDate);
        ec.putString("WS_DB2_COMPARE_MM", wsDb2Month);
        ec.putString("WS_DB2_COMPARE_DD", wsDb2Day);
        ec.putString("WS_DB2_COMPARE_YY", wsDb2Year);
        ec.putString("WS_DB2_COMPARE_CENTURY", wsDb2Century);
        ec.putString("WS_CONTROL_CARD_RUN_TYPE_IND", nvl(jobType));
        // Run COBOL logic (all refund types)
        perform200000ProcessRefundType(ec);

        // Return wrapper (your existing logic)
        @SuppressWarnings("unchecked")
        List<P09352ApInterfaceOutput> apAll = outApRecords;
        @SuppressWarnings("unchecked")
        List<P09352XP09DedsOutput> glAll = outGlRecords;
        @SuppressWarnings("unchecked")
        List<P09352XP07DedsOutput> occsAll = outOccsRecords;
        P09352OutputWrapper out = new P09352OutputWrapper();
        out.setApRecords(apAll == null ? List.of() : new ArrayList<>(apAll));
        out.setGlRecords(glAll == null ? List.of() : new ArrayList<>(glAll));
        out.setOccsRecords(occsAll == null ? List.of() : new ArrayList<>(occsAll));

        if (apAll != null) apAll.clear();
        if (glAll != null) glAll.clear();
        if (occsAll != null) occsAll.clear();

        return out;
    }

 private void perform200000ProcessRefundType(ExecutionContext ec) {

	    ec.putInt("T_TBL_PNTR", 1);
	    ec.putString("WS_REFUND_TYPE", "SPO");

	    boolean finalRpt = perform210000GetCntrlRcd(ec);
	    if (!finalRpt) return;

	    // After 210000/220000/410000/480000 ran, MAX must be set
	    int max = ec.containsKey("T_TBL_PNTR_MAX") ? ec.getInt("T_TBL_PNTR_MAX") : 0;

	    ec.putInt("T_TBL_PNTR", 1);

	    while (true) {
	        int ptr = ec.getInt("T_TBL_PNTR");
	        if (ptr > max) break;

	        perform500000UpdtCashRecActOccs(ec);
	        ec.putInt("T_TBL_PNTR", ptr + 1);
	    }
	}

    // ================================================================
    // 210000-GET-CNTRL-RCD (control cursor, drives cash receipt loop)
    // ================================================================
    private boolean perform210000GetCntrlRcd(ExecutionContext ec) {
        final String refundType = nvl(ec.getString("WS_REFUND_TYPE"));

        try {
            final List<IP09ControlRepository.P09ControlProjection> rows =
                    p09ControlRepository.fetchControlRows(refundType);

            if (rows == null || rows.isEmpty()) {
                ec.put("FINAL_RPT", Boolean.FALSE);
                return false;
            }

            int refundTypesProcessed = ec.containsKey("WS_REFUND_TYPES_PROCESSED")
                    ? ec.getInt("WS_REFUND_TYPES_PROCESSED") : 0;
            final int chkpCardCnt = ec.containsKey("WS_CHKP_CARD_CNT")
                    ? ec.getInt("WS_CHKP_CARD_CNT") : Integer.MAX_VALUE;

            for (IP09ControlRepository.P09ControlProjection row : rows) {
                if (row == null) continue;
                log.info("210000: refundType={} controlRows={}", refundType, (rows==null?0:rows.size()));
                ec.putString("WS_CR_REFUND_TYPE", nvl(row.getCntrlRefundType()));
                ec.putString("WS_CR_REFUND_NARR", nvl(row.getCntrlRefundNarr()));

                perform215000CalcCrrsRptDate(ec, row.getCntrlRefundType(), row.getCntrlToDate());

                // 400000 heading routine: delegate to writer
                reportWriter.headingRoutineCobolStyle(ec);

                // 220000 cash receipt loop for this refund type
                perform220000CashRecReadloop(ec, row.getCntrlRefundType());
                perform900000DetermineIfNoData(ec);
                perform470000ZeroOutAccumulators(ec);
                refundTypesProcessed++;
                ec.putInt("WS_REFUND_TYPES_PROCESSED", refundTypesProcessed);
                if (refundTypesProcessed >= chkpCardCnt) break;
            }

            ec.put("FINAL_RPT", Boolean.TRUE);
            return true;

        } catch (Exception ex) {
            log.error("210000-GET-CNTRL-RCD failed for refundType={}", refundType, ex);
            throw new IllegalStateException("210000-GET-CNTRL-RCD failed", ex);
        }
    }

 // ============================================================
 // 900000 - DETERMINE-IF-NO-DATA  (COBOL PG274100)
 // ============================================================
 private void perform900000DetermineIfNoData(ExecutionContext ec) {
     // COBOL uses CNTRL-REFUND-TYPE (the control row’s refund type)
     String rt = ecStr(ec,"WS_CR_REFUND_TYPE"); // e.g. PER/RET/UND/OTH/OFF/SPO

     int rqstdCnt;

     if ("PER".equals(rt)) {
         rqstdCnt = ec.containsKey("WS_GR_TOT_PER_CHK_RQSTD_CNT") ? ec.getInt("WS_GR_TOT_PER_CHK_RQSTD_CNT") : 0;
         if (rqstdCnt == 0) performDeNoDataFound(ec);

     } else if ("RET".equals(rt)) {
         rqstdCnt = ec.containsKey("WS_GR_TOT_RET_CHK_RQSTD_CNT") ? ec.getInt("WS_GR_TOT_RET_CHK_RQSTD_CNT") : 0;
         if (rqstdCnt == 0) performDeNoDataFound(ec);

     } else if ("UND".equals(rt)) {
         rqstdCnt = ec.containsKey("WS_GR_TOT_UND_CHK_RQSTD_CNT") ? ec.getInt("WS_GR_TOT_UND_CHK_RQSTD_CNT") : 0;
         if (rqstdCnt == 0) performDeNoDataFound(ec);

     } else if ("OTH".equals(rt)) {
         rqstdCnt = ec.containsKey("WS_GR_TOT_OTH_CHK_RQSTD_CNT") ? ec.getInt("WS_GR_TOT_OTH_CHK_RQSTD_CNT") : 0;
         if (rqstdCnt == 0) performDeNoDataFound(ec);

     } else if ("OFF".equals(rt)) {
         rqstdCnt = ec.containsKey("WS_GR_TOT_OFF_CHK_RQSTD_CNT") ? ec.getInt("WS_GR_TOT_OFF_CHK_RQSTD_CNT") : 0;
         if (rqstdCnt == 0) performDeNoDataFound(ec);

     } else if ("SPO".equals(rt)) {
         rqstdCnt = ec.containsKey("WS_GR_TOT_SPO_CHK_RQSTD_CNT") ? ec.getInt("WS_GR_TOT_SPO_CHK_RQSTD_CNT") : 0;
         if (rqstdCnt == 0) performDeNoDataFound(ec);
     }
 }
 
     //COBOL: PERFORM DE-NO-DATA-FOUND THRU ...-EXIT
    private void performDeNoDataFound(ExecutionContext ec) {
         reportWriter.writeNoDataFoundLine(ec);
    }
    // ============================================================
    // 215000 - CALC-CRRS-RPT-DATE
    // ============================================================
    private void perform215000CalcCrrsRptDate(ExecutionContext ec, String cntrlRefundType, LocalDate cntrlToDate) {
        if (cntrlToDate == null) return;

        int mm = cntrlToDate.getMonthValue();
        int year = cntrlToDate.getYear();
        int cc = year / 100;
        int yy = year % 100;

        int mmYY = (mm * 100) + yy;     // COBOL: WS-*-RPT-DATE-MMYY
        int yyMM = (yy * 100) + mm;     // COBOL: WS-*-RPT-DATE-YYMM

        String rt = nvl(cntrlRefundType).trim();

        if ("PER".equals(rt) || "OTH".equals(rt)) {
            ec.putInt(KEY_WS_PER_RPT_DATE_MMYY, mmYY);
            ec.putInt("WS_PER_RPT_DATE_YYMM", yyMM);
            ec.putInt("WS_PER_RPT_CC", cc); // COBOL stores CC as part of YYMM structure; we keep it as separate key
            return;
        }

        if ("RET".equals(rt)) {
            ec.put(KEY_WS_RET_CONTROL_TO_DATE, cntrlToDate);
            ec.putInt(KEY_WS_RET_RPT_DATE_MMYY, mmYY);
            ec.putInt(KEY_WS_RET_RPT_DATE_YYMM, yyMM);
            ec.putInt("WS_RET_RPT_CC", cc);
            return;
        }

        if ("UND".equals(rt)) {
            ec.put(KEY_WS_UND_CONTROL_TO_DATE, cntrlToDate);
            ec.putInt(KEY_WS_UND_RPT_DATE_MMYY, mmYY);
            ec.putInt(KEY_WS_UND_RPT_DATE_YYMM, yyMM);
            ec.putInt("WS_UND_RPT_CC", cc);
            return;
        }

        if ("OFF".equals(rt)) {
            ec.put("WS_OFF_CONTROL_TO_DATE", cntrlToDate); // make a KEY constant if you want consistency
            ec.putInt(KEY_WS_OFF_RPT_DATE_MMYY, mmYY);
            ec.putInt("WS_OFF_RPT_DATE_YYMM", yyMM);
            ec.putInt("WS_OFF_RPT_CC", cc);
            return;
        }

        if ("SPO".equals(rt)) {
            ec.put(KEY_WS_SPO_CONTROL_TO_DATE, cntrlToDate);
            ec.putInt(KEY_WS_SPO_RPT_DATE_MMYY, mmYY);
            ec.putInt(KEY_WS_SPO_RPT_DATE_YYMM, yyMM);
            ec.putInt("WS_SPO_RPT_CC", cc);
        }
    }

 // ============================================================
 // 220000 - CASH-REC-READLOOP
 // ============================================================
 private void perform220000CashRecReadloop(ExecutionContext ec, String refundType) {

     // COBOL: WS-DB2-CONTROL-CARD-DATE comes from CONTROL-CARD compare date
     LocalDate controlCardDate = parseControlCardDate(ec.getString("WS_DB2_CONTROL_CARD_DATE"));

     // COBOL: CARD-CORP
     String corpCode = nvl(ec.getString("WS_CORP_CODE")).trim();

     List<P09CashReceipt> receipts =
             cashReceiptRepository.findPendingCashReceipts(refundType, controlCardDate, corpCode);
     log.info("220000: refundType={} corpCode={} controlCardDate={} cashReceipts={}",
    	        refundType, corpCode, controlCardDate, (receipts==null?0:receipts.size()));
     // COBOL prints heading before details (your writer does that)
     reportWriter.startRefundTypeSection(
             refundType,
             nvl(ec.getString("WS_CR_REFUND_NARR")),
             mapCategory(refundType)
     );

     for (P09CashReceipt cr : receipts) {

         // COBOL: MOVE SPACES / MOVE +0
         ec.putString("WS_HOLD_AP_1099_CD", "");
         ec.putString("WS_HOLD_OCCS_FILE_IND", "");
         ec.putString("WS_HOLD_OCCS_CHECK_STATUS", "");
         ec.put("WS_HOLD_OCCS_DB_RPT_DATE", null);
         ec.put("WS_HOLD_OCCS_CALC_RPT_DATE", null);
         ec.putInt("WS_ACT_RCDS_RTRVD", 0);

         ec.put(KEY_CURRENT_CASH_RECEIPT_ID, cr.getCrId());

         // COBOL paragraphs
         perform230000RtrvActivity(ec);
         perform410000PrintDetailInfo(ec);
     }

     reportWriter.endRefundTypeSection();
 }

    private static LocalDate parseControlCardDate(String raw) {
	    String s = (raw == null) ? "" : raw.trim();
	    if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;

	    // Accept both formats that your job uses
	    if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
	        return LocalDate.parse(s, DateTimeFormatter.ISO_DATE);
	    }
	    if (s.matches("\\d{2}/\\d{2}/\\d{2}")) {
	        return LocalDate.parse(s, DateTimeFormatter.ofPattern("MM/dd/yy"));
	    }

	    throw new IllegalArgumentException("Invalid WS_DB2_CONTROL_CARD_DATE: " + s);
	}
    
 // ============================================================
 // 230000 - RTRV-ACTIVITY  (COBOL PX03)
 // ============================================================
    private void perform230000RtrvActivity(ExecutionContext ec) {

        ec.putInt("WS_ACT_RCDS_RTRVD", 0);
        ec.put(KEY_CURRENT_ACTIVITIES, List.of());
        ec.put(KEY_CURRENT_ACTIVITY, null);
        ec.put(KEY_CURRENT_ACTIVITY_ID, null);
        ec.put("WS_ACTIVITY_IDS", List.of()); // ✅ NEW

        P09CashReceipt cr = currentCashReceipt(ec);
        if (cr == null) return;

        String refundType = cr.getCrId().getCrRefundType();
        LocalDate cntrlDate = cr.getCrId().getCrCntrlDate();
        String cntrlNbr = cr.getCrId().getCrCntrlNbr();

        List<P09Activity> acts = activityRepository.fetchPendingActivities(refundType, cntrlDate, cntrlNbr);

        int cnt = (acts == null) ? 0 : acts.size();
        ec.putInt("WS_ACT_RCDS_RTRVD", cnt);

        // ✅ store IDs so we can pick the one we actually used
        List<ActivityPK> ids = new ArrayList<>();
        if (acts != null) {
            for (P09Activity a : acts) {
                if (a != null && a.getAId() != null) ids.add(a.getAId());
            }
        }
        ec.put("WS_ACTIVITY_IDS", ids);

        // COBOL logic later assumes "current activity" exists when count==1
        ec.put(KEY_CURRENT_ACTIVITY_ID, ids.isEmpty() ? null : ids.get(0));
    }

   //============================================================
   //410000 - PRINT-DETAIL-INFO
   //============================================================
    private void perform410000PrintDetailInfo(ExecutionContext ec) {

        CashReceiptPK crId = (CashReceiptPK) ec.get(KEY_CURRENT_CASH_RECEIPT_ID);
        P09CashReceipt cr = (crId == null) ? null : cashReceiptRepository.findById(crId).orElse(null);

        ActivityPK actId = (ActivityPK) ec.get(KEY_CURRENT_ACTIVITY_ID);
        P09Activity act = (actId == null) ? null : activityRepository.findById(actId).orElse(null);

        // NOTE: Do NOT store JPA entities in ExecutionContext (must be Serializable).
        // We rely on the *_ID keys and re-fetch entities when needed.

        ec.putString("WS_ERROR_MSG", "");

        if (cr == null) return;

        LocalDate cntrlDate = cr.getCrId().getCrCntrlDate();
        String formattedCntrlDate =
                (cntrlDate == null) ? "" : cntrlDate.format(DateTimeFormatter.ofPattern("MM/dd/yy"));
        ec.putString("WS_FORMATTED_CNTRL_DATE", formattedCntrlDate);

        perform420000DeterminePayeeInfo(ec);

        ec.putString("WS_FR_PR", "FRR".equals(cr.getCrPendFinAct()) ? "FR" : "PR");
        ec.putString("WS_ORIG_TYPE", safeStr(cr.getCrReceiptType()));
        ec.putString("WS_USER_ID", (act != null) ? safeStr(act.getActUserId()) : "");

        perform430000CheckForErrors(ec);
        writeDetailLine(ec);

        perform470000ZeroOutAccumulators(ec);
        perform440000CalcRqstdCntsAmts(ec);

        String err = ecStr(ec, "WS_ERROR_MSG");
        if (err != null && !err.isBlank()) {
            perform450000CalcErrorCntsAmts(ec);
        } else {
            perform460000CalcIssueCntsAmts(ec);
            log.info("410000: refundType={} cntrlNbr={} errorMsg='{}' actCnt={} checkAmt={} -> {}",
                    cr.getCrId().getCrRefundType(),
                    cr.getCrId().getCrCntrlNbr(),
                    ecStr(ec,"WS_ERROR_MSG"),
                    ec.containsKey("WS_ACT_RCDS_RTRVD") ? ec.getInt("WS_ACT_RCDS_RTRVD") : -1,
                    ec.get("WS_CHECK_AMT"),
                    (ecStr(ec,"WS_ERROR_MSG").isBlank() ? "WILL_480000" : "SKIP_480000")
            );
            perform480000BldIssuedChkRcd(ec);
        }
    }

    private void writeDetailLine(ExecutionContext ec) {
    	P09CashReceipt cr = currentCashReceipt(ec);
        if (cr == null) return;

        BigDecimal checkAmt = ec.containsKey("WS_CHECK_AMT") ? (BigDecimal) ec.get("WS_CHECK_AMT"): BigDecimal.ZERO;

        reportWriter.writeDetailFromFields(
                ec.getString("WS_FORMATTED_CNTRL_DATE"),
                safeStr(cr.getCrId().getCrCntrlNbr()),
                checkAmt,
                ec.getString("WS_PAYEE_NAME"),
                ec.getString("WS_PAYEE_TYPE"),
                ec.getString("WS_PAYEE_ID_NBR"),
                ec.getString("WS_PAYEE_TIN"),
                ec.getString("WS_FR_PR"),
                ec.getString("WS_ORIG_TYPE"),
                safeStr(cr.getCrCheckNbr()),
                ec.getString("WS_USER_ID"),
                ec.getString("WS_ERROR_MSG")
        );

        // requested always increments
        reportWriter.addRequested(checkAmt);

        // issued/error increments based on error message
        String err = ec.getString("WS_ERROR_MSG");
        if (err != null && !err.isBlank()) reportWriter.addError(checkAmt);
        else reportWriter.addIssued(checkAmt);
    }

    // ============================================================
    // 420000 - DETERMINE-PAYEE-INFO
    // ============================================================
    private void perform420000DeterminePayeeInfo(ExecutionContext ec) {
    	P09CashReceipt cr = currentCashReceipt(ec);
        if (cr == null) return;

        String remIdType = safeStr(cr.getCrRemIdType());
        if (!remIdType.isBlank()) {
            ec.putString("WS_PAYEE_TYPE", remIdType);

            String remIdNbr = safeStr(cr.getCrRemIdNbr());
            if (remIdNbr.isBlank()) ec.putString("WS_PAYEE_ID_NBR", safeStr(cr.getCrRemNationalId()));
            else ec.putString("WS_PAYEE_ID_NBR", remIdNbr);

            if ("V".equals(remIdType)) {
                String v = remIdNbr.isBlank() ? safeStr(cr.getCrRemNationalId()) : remIdNbr;
                ec.putString("WS_VENDOR_NBR_FROM_REM", v);
                // PERFORM 910000-READ-VENDOR (not included here)
            }
        } else {
            String remType = safeStr(cr.getCrRemittorType());
            ec.putString("WS_PAYEE_TYPE", remType);

            if ("M".equals(remType)) {
                ec.putString("WS_PAYEE_ID_NBR", safeStr(cr.getCrMbrIdNbr()));
            } else if ("P".equals(remType)) {
                String provider = safeStr(cr.getCrProviderNbr());
                if (provider.isBlank()) ec.putString("WS_PAYEE_ID_NBR", safeStr(cr.getNationalIdNbr()));
                else ec.putString("WS_PAYEE_ID_NBR", provider);
            } else if ("T".equals(remType)) {
                ec.putString("WS_PAYEE_ID_NBR", safeStr(cr.getCrTaxIdNbr()));
            } else if ("V".equals(remType)) {
                ec.putString("WS_PAYEE_ID_NBR", safeStr(cr.getCrVendorNbr()));
            }
        }

        String addressee = safeStr(cr.getCrRemAddressee());
        if (addressee.isBlank()) ec.putString("WS_PAYEE_NAME", safeStr(cr.getCrRemittorName()));
        else ec.putString("WS_PAYEE_NAME", addressee);

        String remTax = safeStr(cr.getCrRemTaxIdNbr());
        if (remTax.isBlank()) ec.putString("WS_PAYEE_TIN", safeStr(cr.getCrTaxIdNbr()));
        else ec.putString("WS_PAYEE_TIN", remTax);

        // vendor -> blank TIN
        if ("V".equals(ec.getString("WS_PAYEE_TYPE"))) {
            ec.putString("WS_PAYEE_TIN", "");
        }
    }

    // ============================================================
    // 430000 - CHECK-FOR-ERRORS
    // ============================================================
    private void perform430000CheckForErrors(ExecutionContext ec) {
        P09CashReceipt cr = currentCashReceipt(ec);
        P09Activity act   = currentActivity(ec);
        if (cr == null) return;

        // COBOL: uses WS-ACT-RCDS-RTRVD
        int actCnt = ec.containsKey("WS_ACT_RCDS_RTRVD") ? ec.getInt("WS_ACT_RCDS_RTRVD") : 0;

        // COBOL: ACT-ACTIVITY-AMT, CR-RECEIPT-BAL
        BigDecimal actAmt = (act != null && act.getActActivityAmt() != null)
                ? act.getActActivityAmt()
                : BigDecimal.ZERO;

        BigDecimal receiptBal = (cr.getCrReceiptBal() != null)
                ? cr.getCrReceiptBal()
                : BigDecimal.ZERO;
        if (actCnt == 0) {
            ec.put("WS_CHECK_AMT", BigDecimal.ZERO);
            ec.putString("WS_ERROR_MSG", "NO ACTIVITY RCD EXISTS   "); // PIC X(25)
            return;
        } else if (actCnt == 1) {
            ec.put("WS_CHECK_AMT", actAmt);
        } else {
            ec.put("WS_CHECK_AMT", actAmt);
            ec.putString("WS_ERROR_MSG", "DUPLICATE ACTIVITY RCDS  "); // PIC X(25)
            return;
        }

        if (receiptBal.subtract(actAmt).compareTo(BigDecimal.ZERO) < 0) {
            ec.putString("WS_ERROR_MSG", "CASH RECEIPT BAL LT ZERO "); // PIC X(25)
            return;
        }

        String rt = safeStr(cr.getCrId().getCrRefundType());
        if (("UND".equals(rt) || "OFF".equals(rt))
                && receiptBal.subtract(actAmt).compareTo(BigDecimal.ZERO) != 0) {
            ec.putString("WS_ERROR_MSG", "CASH RECEIPT BAL NOT ZERO"); // PIC X(25)
            return;
        }

        if ("RET".equals(rt) || "UND".equals(rt) || "OFF".equals(rt) || "SPO".equals(rt)) {
            String gl = safeStr(cr.getCrGlAcctNbr());

            boolean spaces = gl.isBlank();
            boolean zeroes = !spaces && gl.chars().allMatch(ch -> ch == '0');

            if (spaces || zeroes) {
                ec.putString("WS_ERROR_MSG", "MISSING/INVALID GL ACCT# "); // PIC X(25)
                return;
            }
        }

        if ("RET".equals(rt) || "UND".equals(rt) || "SPO".equals(rt)) {
            perform490000RtrvBankReconRcd(ec);
        }
    }

    // ============================================================
    // 440000 - CALC-RQSTD-CNTS-AMTS
    // ============================================================
    private void perform440000CalcRqstdCntsAmts(ExecutionContext ec) {
    	P09CashReceipt cr = currentCashReceipt(ec);
        if (cr == null) return;
        BigDecimal checkAmt = ec.containsKey("WS_CHECK_AMT")? (BigDecimal) ec.get("WS_CHECK_AMT") : BigDecimal.ZERO;

        incInt(ec, "WS_TOT_CHK_RQSTD_CNT", 1);
        addBd(ec, "WS_TOT_CHK_RQSTD_AMT", checkAmt);

        String rt = cr.getCrId().getCrRefundType();
        addByRefund(ec, rt, "RQSTD", checkAmt);
    }

    // ============================================================
    // 450000 - CALC-ERROR-CNTS-AMTS
    // ============================================================
    private void perform450000CalcErrorCntsAmts(ExecutionContext ec) {
    	P09CashReceipt cr = currentCashReceipt(ec);
        if (cr == null) return;
        BigDecimal checkAmt = ec.containsKey("WS_CHECK_AMT")? (BigDecimal) ec.get("WS_CHECK_AMT") : BigDecimal.ZERO;

        incInt(ec, "WS_TOT_CHK_ERROR_CNT", 1);
        addBd(ec, "WS_TOT_CHK_ERROR_AMT", checkAmt);

        String rt = cr.getCrId().getCrRefundType();
        addByRefund(ec, rt, "ERROR", checkAmt);
    }

    // ============================================================
    // 460000 - CALC-ISSUE-CNTS-AMTS
    // ============================================================
    private void perform460000CalcIssueCntsAmts(ExecutionContext ec) {
    	P09CashReceipt cr = currentCashReceipt(ec);
        if (cr == null) return;
        BigDecimal checkAmt = ec.containsKey("WS_CHECK_AMT")? (BigDecimal) ec.get("WS_CHECK_AMT") : BigDecimal.ZERO;

        incInt(ec, "WS_TOT_CHK_ISSUE_CNT", 1);
        addBd(ec, "WS_TOT_CHK_ISSUE_AMT", checkAmt);

        String rt = cr.getCrId().getCrRefundType();
        addByRefund(ec, rt, "ISSUE", checkAmt);
    }

    // ============================================================
    // 470000 - ZERO-OUT-ACCUMULATORS
    // ============================================================
    private void perform470000ZeroOutAccumulators(ExecutionContext ec) {
    	P09CashReceipt cr = currentCashReceipt(ec);
        if (cr == null) return;

        String cntrlRefundType = cr.getCrId().getCrRefundType();
        String prev = ecStr(ec,"WS_PREV_REFUND_TYPE");
        if (Objects.equals(prev, cntrlRefundType)) return;

        ec.putString("WS_PREV_REFUND_TYPE", cntrlRefundType);
        ec.putInt("WS_TOT_CHK_RQSTD_CNT", 0);
        ec.put("WS_TOT_CHK_RQSTD_AMT", BigDecimal.ZERO);
        ec.putInt("WS_TOT_CHK_ERROR_CNT", 0);
        ec.put("WS_TOT_CHK_ERROR_AMT", BigDecimal.ZERO);
        ec.putInt("WS_TOT_CHK_ISSUE_CNT", 0);
        ec.put("WS_TOT_CHK_ISSUE_AMT", BigDecimal.ZERO);
    }

 // ============================================================
 // 480000 - BLD-ISSUED-CHK-RCD   (COBOL: MOVE row TO TABLE(T-TBL-PNTR))
 // ============================================================
    private void perform480000BldIssuedChkRcd(ExecutionContext ec) {

        P09CashReceipt cr = currentCashReceipt(ec);
        P09Activity act   = currentActivity(ec);
        if (cr == null) return;

        int ptr = ec.containsKey("T_TBL_PNTR") ? ec.getInt("T_TBL_PNTR") : 1;
        if (ptr < 1 || ptr > 700) {
            throw new IllegalStateException("T_TBL_PNTR out of range in 480000: " + ptr);
        }

        @SuppressWarnings("unchecked")
        List<IssuedChkRow> issuedChkRows = (List<IssuedChkRow>) ec.get(KEY_ISSUED_CHK_TABLE);

        // Ensure table has 700 slots (nulls are fine)
        if (issuedChkRows == null) {
            issuedChkRows = new ArrayList<>(700);
            for (int i = 0; i < 700; i++) issuedChkRows.add(null);
            ec.put(KEY_ISSUED_CHK_TABLE, issuedChkRows);
        } else {
            while (issuedChkRows.size() < 700) issuedChkRows.add(null);
        }

        IssuedChkRow row = new IssuedChkRow();

        row.setSourceActivityId(act == null ? null : act.getAId());

        row.crRefundType   = cr.getCrId().getCrRefundType();
        row.crCntrlDate    = cr.getCrId().getCrCntrlDate();
        row.crCntrlNbr     = cr.getCrId().getCrCntrlNbr();
        row.crRemDailyInd  = cr.getCrRemDailyInd();

        row.crStatusDate   = cr.getCrStatusDate();
        row.crStatusText   = cr.getCrStatusText();
        row.crPendFinAct   = cr.getCrPendFinAct();
        row.crReasonCode   = cr.getCrReasonCode();

        row.crBankAcctNbr  = cr.getCrBankAcctNbr();
        row.crCheckNbr     = cr.getCrCheckNbr();
        row.crCheckDate    = cr.getCrCheckDate();

        row.crCheckAmt     = nvlBd(cr.getCrCheckAmt());
        row.crCntrldAmt    = nvlBd(cr.getCrCntrldAmt());
        row.crReceiptBal   = nvlBd(cr.getCrReceiptBal());

        BigDecimal actAmt = (act != null) ? nvlBd(act.getActActivityAmt()) : BigDecimal.ZERO;
        row.newCrReceiptBal = row.crReceiptBal.subtract(actAmt);

        row.crReceiptType  = cr.getCrReceiptType();

        row.crRemittorName  = cr.getCrRemittorName();
        row.crRemittorTitle = cr.getCrRemittorTitle();
        row.crRemittorType  = cr.getCrRemittorType();

        row.crPatientLname = cr.getCrPatientLname();
        row.crPatientFname = cr.getCrPatientFname();

        row.crProviderNbr  = cr.getCrProviderNbr();
        row.nationalIdNbr  = cr.getNationalIdNbr();
        row.crMbrIdNbr     = cr.getCrMbrIdNbr();
        row.crTaxIdNbr     = cr.getCrTaxIdNbr();
        row.crVendorNbr    = cr.getCrVendorNbr();

        row.ap1099Cd       = ec.getString("WS_HOLD_AP_1099_CD");
        row.crGlAcctNbr    = cr.getCrGlAcctNbr();

        row.crLocationNbr   = cr.getCrLocationNbr();
        row.crLocationClerk = cr.getCrLocationClerk();

        row.crChkAddress1 = cr.getCrChkAddress1();
        row.crChkAddress2 = cr.getCrChkAddress2();
        row.crChkCity     = cr.getCrChkCity();
        row.crChkState    = cr.getCrChkState();
        row.crChkZip5     = cr.getCrChkZip5();
        row.crChkZip4     = cr.getCrChkZip4();

        row.crRemIdType       = cr.getCrRemIdType();
        row.crRemNationalId   = cr.getCrRemNationalId();
        row.crRemIdNbr        = cr.getCrRemIdNbr();
        row.crRemTaxIdNbr     = cr.getCrRemTaxIdNbr();

        row.crRemAddressee = cr.getCrRemAddressee();
        row.crRemAddress1  = cr.getCrRemAddress1();
        row.crRemAddress2  = cr.getCrRemAddress2();
        row.crRemCity      = cr.getCrRemCity();
        row.crRemState     = cr.getCrRemState();
        row.crRemZip5      = cr.getCrRemZip5();
        row.crRemZip4      = cr.getCrRemZip4();

        row.crUserId = cr.getCrUserId();
        row.crCorp   = cr.getCrCorp();

        if (act != null && act.getAId() != null) {
            row.actActivity      = act.getAId().getActActivity();
            row.actActivityDate  = act.getAId().getActActivityDate();
            row.actActivityAmt   = act.getActActivityAmt();
            row.actTimestamp     = act.getAId().getActTimestamp();
            row.actXrefType      = act.getActXrefType();
            row.actXrefNbr       = act.getActXrefNbr();
            row.actXrefDate      = act.getActXrefDate();
            row.actReportDate    = act.getActReportDate();
            row.actUserId        = act.getActUserId();
            row.actProcessedInd  = act.getActProcessedInd();
            row.actDailyInd      = act.getActDailyInd();
        }

        row.occsFileIndicator = ec.getString("WS_HOLD_OCCS_FILE_IND");
        row.occsCheckStatus   = ec.getString("WS_HOLD_OCCS_CHECK_STATUS");
        row.occsDbRptDate     = (LocalDate) ec.get("WS_HOLD_OCCS_DB_RPT_DATE");
        row.occsCalcRptDate   = (LocalDate) ec.get("WS_HOLD_OCCS_CALC_RPT_DATE");

        // ✅ Store at ptr
        issuedChkRows.set(ptr - 1, row);

        // ✅ Update MAX so 500000 loop knows how many real rows exist
        int max = ec.containsKey("T_TBL_PNTR_MAX") ? ec.getInt("T_TBL_PNTR_MAX") : 0;
        if (ptr > max) ec.putInt("T_TBL_PNTR_MAX", ptr);

        // ✅ Advance pointer like COBOL after MOVE row TO TABLE(ptr)
        ec.putInt("T_TBL_PNTR", ptr + 1);

        // Keep refund-type table aligned if needed
        @SuppressWarnings("unchecked")
        List<String> rtTable = (List<String>) ec.get(KEY_T_CR_REFUND_TYPE_TABLE);
        if (rtTable == null) {
            rtTable = new ArrayList<>(700);
            for (int i = 0; i < 700; i++) rtTable.add("");
            ec.put(KEY_T_CR_REFUND_TYPE_TABLE, rtTable);
        } else {
            while (rtTable.size() < 700) rtTable.add("");
        }
        rtTable.set(ptr - 1, row.crRefundType);

        log.info("480000 stored issued row ptr={} refundType={} cntrlNbr={} (MAX={})",
                ptr, row.crRefundType, row.crCntrlNbr, ec.getInt("T_TBL_PNTR_MAX"));
    }


    // ============================================================
    // 490000 - RTRV-BANK-RECON-RCD
    // ============================================================
    private void perform490000RtrvBankReconRcd(ExecutionContext ec) {
    	P09CashReceipt cr = currentCashReceipt(ec);
        if (cr == null) return;

        Optional<BankRecon> brOpt = bankReconRepository.findBankReconRecord(
                cr.getCrBankAcctNbr(), cr.getCrCheckNbr(), cr.getCrCheckDate());

        if (brOpt.isPresent()) {
            BankRecon br = brOpt.get();
            ec.putString("WS_HOLD_OCCS_FILE_IND", safeStr(br.getFileIndicator()));
            ec.putString("WS_HOLD_OCCS_CHECK_STATUS", safeStr(br.getCheckStatusCode()));
            ec.put("WS_HOLD_OCCS_DB_RPT_DATE", br.getReportDate());

            // 495000
            perform495000RtrvCheckCntrl(ec);
        } else {
            ec.putString("WS_ERROR_MSG", "NO OCCS RCD");
        }
    }

    // ============================================================
    // 495000 - RTRV-CHECK-CNTRL
    // ============================================================
    private void perform495000RtrvCheckCntrl(ExecutionContext ec) {
    	P09CashReceipt cr = currentCashReceipt(ec);
        String fileInd = ec.getString("WS_HOLD_OCCS_FILE_IND");
        if (cr == null || fileInd == null) return;

        Optional<CheckControl> ccOpt = checkControlRepository.findOpenCheckControl(fileInd, cr.getCrBankAcctNbr());
        if (ccOpt.isPresent()) {
            CheckControl cc = ccOpt.get();

            // DO NOT store entity in EC (not serializable)
            ec.put(KEY_CURRENT_CHECK_CONTROL_ID, cc.getCcId());

            // Optional: remove any old value if it was stored earlier
            ec.remove(KEY_CURRENT_CHECK_CONTROL);

            perform495500CalcOccsRptDate(ec);
        } else {
            throw new IllegalStateException("ERROR IN READING CHECK_CONTROL in 495000-RTRV-CHECK-CNTRL");
        }
    }

    // ============================================================
    // 495500 - CALC-OCCS-RPT-DATE
    // ============================================================
    private void perform495500CalcOccsRptDate(ExecutionContext ec) {
    	P09CashReceipt cr = currentCashReceipt(ec);
    	CheckControl cc = currentCheckControl(ec);
        if (cr == null || cc == null) return;

        LocalDate controlToDate = cc.getControlToDate();
        if (controlToDate == null) return;

        String cntrlRefundType = cr.getCrId().getCrRefundType();

        // Compare YYMM based on 215000 results
        int occsYymm = yymmNum(controlToDate);

        if ("RET".equals(cntrlRefundType)) {
            int retYymm = ec.containsKey(KEY_WS_RET_RPT_DATE_YYMM) ? ec.getInt(KEY_WS_RET_RPT_DATE_YYMM) : 0;
            if (occsYymm <= retYymm) {
                ec.put("WS_HOLD_OCCS_CALC_RPT_DATE", ec.get(KEY_WS_RET_CONTROL_TO_DATE));
            } else {
                ec.putString("WS_ERROR_MSG", "OCCS GT CRRS");
            }
        } else if ("UND".equals(cntrlRefundType)) {
            int undYymm = ec.containsKey(KEY_WS_UND_RPT_DATE_YYMM) ? ec.getInt(KEY_WS_UND_RPT_DATE_YYMM) : 0;
            if (occsYymm <= undYymm) {
                ec.put("WS_HOLD_OCCS_CALC_RPT_DATE", ec.get(KEY_WS_UND_CONTROL_TO_DATE));
            } else {
                ec.putString("WS_ERROR_MSG", "OCCS GT CRRS");
            }
        } else if ("SPO".equals(cntrlRefundType)) {
            int spoYymm = ec.containsKey(KEY_WS_SPO_RPT_DATE_YYMM) ? ec.getInt(KEY_WS_SPO_RPT_DATE_YYMM) : 0;
            if (occsYymm <= spoYymm) {
                ec.put("WS_HOLD_OCCS_CALC_RPT_DATE", ec.get(KEY_WS_SPO_CONTROL_TO_DATE));
            } else {
                ec.putString("WS_ERROR_MSG", "OCCS GT CRRS");
            }
        }
    }

 // ================================================================
 // 500000-UPDT-CASH-REC-ACT-OCCS (COBOL: process TABLE(T-TBL-PNTR))
 // ================================================================
    private void perform500000UpdtCashRecActOccs(ExecutionContext ec) {

        int ptr = ec.getInt("T_TBL_PNTR");
        if (ptr < 1 || ptr > 700) return;

        @SuppressWarnings("unchecked")
        List<IssuedChkRow> issuedChkRows = (List<IssuedChkRow>) ec.get(KEY_ISSUED_CHK_TABLE);
        if (issuedChkRows == null || ptr > issuedChkRows.size()) return;

        IssuedChkRow r = issuedChkRows.get(ptr - 1);

        // ✅ COBOL: blank table entry -> NEXT SENTENCE (do nothing)
        if (r == null || isBlank(r.crRefundType)) {
            log.debug("[500000] Blank issued row at ptr={}, skipping", ptr);
            return;
        }

        ActivityPK actId = r.getSourceActivityId();
        P09Activity act = (actId == null) ? null : activityRepository.findById(actId).orElse(null);

        if (act == null) {
            throw new IllegalStateException("[500000] IssuedChkRow missing source P09Activity at ptr=" + ptr);
        }

        // 510000 — UPDATE PX03
        perform510000UpdtPrrFrrActRcd(act);

        // 520000 — UPDATE CASH RECEIPT
        perform520000UpdtCashRecRcd(r, ec);

        // 530000/531000/532000 — OCCS update + XP07 build
        P09352OutputWrapper wrapperForOccs = new P09352OutputWrapper();
        LocalDate checkStatusDate = perform530000UpdtOccsRcd(r, ec, wrapperForOccs);

        // 540000 — INSERT CAN ACTIVITY
        perform540000InsrtCanActRcd(r, ec, checkStatusDate);

        // 550000 — INSERT PR/FR ACTIVITY
        perform550000InsrtPrFrActRcd(r, ec);

        // 560000 — XP09 GL records
        List<P09352XP09DedsOutput> gl = perform560000CreateGlDedsRcds(r, ec);

        // 570000 — AP interface record
        P09352ApInterfaceOutput ap = perform570000CreateApInterface(r, ec);

        @SuppressWarnings("unchecked")
        List<P09352ApInterfaceOutput> apAll = outApRecords;
        @SuppressWarnings("unchecked")
        List<P09352XP09DedsOutput> glAll = outGlRecords;
        @SuppressWarnings("unchecked")
        List<P09352XP07DedsOutput> occsAll = outOccsRecords;
        // ensure they exist
        if (apAll == null) { /* should never happen */ }
        if (glAll == null) { /* should never happen */ }
        if (occsAll == null) { /* should never happen */ }

     // ---- Accumulate outputs (COBOL WRITE) ----
        if (ap != null) {
            apAll.add(ap);
        }

        if (gl != null && !gl.isEmpty()) {
            glAll.addAll(gl);
        }

        if (wrapperForOccs.getOccsRecords() != null &&
            !wrapperForOccs.getOccsRecords().isEmpty()) {
            occsAll.addAll(wrapperForOccs.getOccsRecords());
        }
        // needed by voucher step
        incInt(ec, "checksIssued", 1);
    }

 // ================================================================
 // 510000 - UPDATE PRR / FRR ACTIVITY (COBOL DB-MODIFY PX03)
 // ================================================================
 private void perform510000UpdtPrrFrrActRcd(P09Activity act) {
     try {
         if (act == null || act.getAId() == null) {
             throw new IllegalStateException("[510000] Activity row is NULL");
         }

         ActivityPK id = act.getAId();

         int updated = activityRepository.updateProcessedInd(
        	        "P",                               // COBOL sets processed flag
                 id.getCrRefundType(),
                 id.getCrCntrlDate(),
                 id.getCrCntrlNbr(),
                 id.getActActivityDate(),
                 id.getActActivity(),
                 id.getActTimestamp()
         );

         if (updated != 1) {
             throw new IllegalStateException(
                 "[510000] ERROR IN MODIFYING V_P09_ACTIVITY: expected 1 row updated but got " + updated
                 + " for key refundType=" + id.getCrRefundType()
                 + ", cntrlDate=" + id.getCrCntrlDate()
                 + ", cntrlNbr=" + id.getCrCntrlNbr()
                 + ", actDate=" + id.getActActivityDate()
                 + ", actActivity=" + id.getActActivity()
                 + ", actTimestamp=" + id.getActTimestamp()
             );
         }

     } catch (Exception e) {
         throw new IllegalStateException(
                 "[510000] Error updating PRR/FRR activity processed indicator", e
         );
     }
 }

 // 520000 - update cash receipt + move rem info + summary
    private void perform520000UpdtCashRecRcd(IssuedChkRow r, ExecutionContext ec) {
        try {
            // NOTE: COBOL does not SELECT here; it UPDATEs using host vars.
            // We still create a local object so your existing 522500 method stays the same.
            P09CashReceipt cr = new P09CashReceipt();

         // --- SDE.04314 logic ---
         // Important: this should be the *existing cash receipt* CR_REM_DAILY_IND
         String remDailyInd = nullToSpace(r.crRemDailyInd);
         if (!("Y".equals(remDailyInd) || "I".equals(remDailyInd))) {
             remDailyInd = " ";
         }
         cr.setCrRemDailyInd(remDailyInd);

         // receipt bal move
         cr.setCrReceiptBal(nvlBd(r.newCrReceiptBal));

         // status text + FSU.01029
         if (BigDecimal.ZERO.compareTo(cr.getCrReceiptBal()) == 0) {
             cr.setCrStatusText(padRight("CLOSED", 6)); // if COBOL field is 6
             if (("RET".equals(r.crRefundType) || "SPO".equals(r.crRefundType)) && "IRS".equals(r.crReasonCode)) {
                 cr.setCrRemDailyInd("I");
             }
         } else {
             cr.setCrStatusText("OPEN  ");
         }

            // pend fin act
            if ("PRR".equals(r.actActivity)) cr.setCrPendFinAct("PR ");
            else cr.setCrPendFinAct("FR ");

            // status date
            cr.setCrStatusDate(wsDb2CurrentDate(ec)); // MUST return LocalDate in your codebase

            // PERFORM 522500-MOVE-REM-INFO
            perform522500MoveRemInfo(r, cr);

            // COBOL: EXEC SQL UPDATE V_P09_CASH_RECEIPT ...
            int rows = cashReceiptRepository.updateCashReceiptRecord(
            	    cr.getCrReceiptBal(),
            	    nullToSpace(cr.getCrStatusText()),
            	    cr.getCrStatusDate(),
            	    nullToSpace(cr.getCrPendFinAct()),
            	    nullToSpace(cr.getCrRemDailyInd()),
            	    nullToSpace(cr.getCrRemIdType()),
            	    nullToSpace(cr.getCrRemIdNbr()),
            	    nullToSpace(cr.getCrRemNationalId()),
            	    nullToSpace(cr.getCrRemTaxIdNbr()),
            	    nullToSpace(cr.getCrRemAddressee()),
            	    nullToSpace(cr.getCrRemAddress1()),
            	    nullToSpace(cr.getCrRemAddress2()),
            	    nullToSpace(cr.getCrRemCity()),
            	    nullToSpace(cr.getCrRemState()),
            	    nullToSpace(cr.getCrRemZip5()),
            	    nullToSpace(cr.getCrRemZip4()),
            	    r.crRefundType, r.crCntrlDate, r.crCntrlNbr
            	);

            if (rows != 1) {
                throw new IllegalStateException(
                        "ERROR IN MODIFYING DCLV-P09-CASH-RECEIPT IN PARAGRAPH 520000-UPDT-CASH-REC-RCD " +
                        "(rowsUpdated=" + rows + ") key=" + r.crRefundType + "/" + r.crCntrlDate + "/" + r.crCntrlNbr
                );
            }

            // COBOL: IF CR-RECEIPT-BAL = +0 PERFORM 525000-UPDT-SUMMARY
            if (BigDecimal.ZERO.compareTo(nvlBd(cr.getCrReceiptBal())) == 0) {
            	perform525000UpdtSummary(r);
            }

        } catch (Exception e) {
            throw new IllegalStateException("[520000] Error updating cash receipt", e);
        }
    }

    private void perform522500MoveRemInfo(IssuedChkRow r, P09CashReceipt cr) {

        // CR-REM-ID-TYPE
        if (isBlank(r.crRemIdType)) cr.setCrRemIdType(nullToSpace(r.crRemittorType));
        else cr.setCrRemIdType(nullToSpace(r.crRemIdType));

        // IF BOTH rem-id-nbr AND rem-national-id are spaces -> use remittor-type logic
        if (isBlank(r.crRemIdNbr) && isBlank(r.crRemNationalId)) {

            String remType = nullToSpace(r.crRemittorType);

            if ("M".equals(remType)) {
                cr.setCrRemIdNbr(nullToSpace(r.crMbrIdNbr));
                cr.setCrRemNationalId(" "); // keep non-null like COBOL
            } else if ("P".equals(remType)) {
                if (isBlank(r.crProviderNbr)) {
                    cr.setCrRemNationalId(nullToSpace(r.nationalIdNbr));
                    // IMPORTANT: COBOL does NOT explicitly blank CR-REM-ID-NBR here
                    // So don't force it unless your record isn't otherwise initialized.
                    // cr.setCrRemIdNbr(" ");
                } else {
                    cr.setCrRemIdNbr(nullToSpace(r.crProviderNbr));
                    cr.setCrRemNationalId(" ");
                }
            } else if ("T".equals(remType)) {
                cr.setCrRemIdNbr(nullToSpace(r.crTaxIdNbr));
                cr.setCrRemNationalId(" ");
            } else if ("V".equals(remType)) {
                cr.setCrRemIdNbr(nullToSpace(r.crVendorNbr));
                cr.setCrRemNationalId(" ");
            }

        } else {
            // MOVE T-CR-REM-ID-NBR / MOVE T-CR-REM-NATIONAL-ID
            cr.setCrRemIdNbr(nullToSpace(r.crRemIdNbr));
            cr.setCrRemNationalId(nullToSpace(r.crRemNationalId));
        }

        // CR-REM-TAX-ID-NBR
        if (isBlank(r.crRemTaxIdNbr)) cr.setCrRemTaxIdNbr(nullToSpace(r.crTaxIdNbr));
        else cr.setCrRemTaxIdNbr(nullToSpace(r.crRemTaxIdNbr));

        // CR-REM-ADDRESSEE
        if (isBlank(r.crRemAddressee)) cr.setCrRemAddressee(nullToSpace(r.crRemittorName));
        else cr.setCrRemAddressee(nullToSpace(r.crRemAddressee));

        // ADDRESS 1/2, CITY/STATE/ZIP
        if (isBlank(r.crRemAddress1)) cr.setCrRemAddress1(nullToSpace(r.crChkAddress1));
        else cr.setCrRemAddress1(nullToSpace(r.crRemAddress1));

        if (isBlank(r.crRemAddress2)) cr.setCrRemAddress2(nullToSpace(r.crChkAddress2));
        else cr.setCrRemAddress2(nullToSpace(r.crRemAddress2));

        cr.setCrRemCity(isBlank(r.crRemCity) ? nullToSpace(r.crChkCity) : nullToSpace(r.crRemCity));
        cr.setCrRemState(isBlank(r.crRemState) ? nullToSpace(r.crChkState) : nullToSpace(r.crRemState));
        cr.setCrRemZip5(isBlank(r.crRemZip5) ? nullToSpace(r.crChkZip5) : nullToSpace(r.crRemZip5));
        cr.setCrRemZip4(isBlank(r.crRemZip4) ? nullToSpace(r.crChkZip4) : nullToSpace(r.crRemZip4));

        // PR E.02718
        if ("V".equals(cr.getCrRemIdType())) cr.setCrRemTaxIdNbr(" ");
    }

    @Transactional
    private void perform525000UpdtSummary(IssuedChkRow r) {

        final String locationId = nullToSpace(r.crLocationNbr).trim();
        final String clerkId    = nullToSpace(r.crLocationClerk).trim();

        if (locationId.isBlank() || clerkId.isBlank()) {
            // If your COBOL always has these, you can throw; otherwise skip safely.
            throw new IllegalStateException("525000 missing location/clerk. locationId=" + locationId + ", clerkId=" + clerkId);
        }

        P09Summary s = p09SummaryRepository.findSummary(locationId, clerkId).orElse(null);

        if (s == null) {
            // INSERT a new summary row with zeros
            P09Summary ins = new P09Summary();
            SummaryPK pk = new SummaryPK();
            pk.setSumLocationID(locationId);
            pk.setSumClerkId(clerkId);
            ins.setSId(pk);

            ins.setSumDeletionsCnt(0);
            ins.setSumDeletionsAmt(BigDecimal.ZERO);
            ins.setSumEndingCnt(0);
            ins.setSumEndingAmt(BigDecimal.ZERO);

            p09SummaryRepository.save(ins);

            s = ins;
        }

        Integer newDelCnt = nvlInt(s.getSumDeletionsCnt()) + 1;
        BigDecimal newDelAmt = nvl(s.getSumDeletionsAmt()).add(nvl(r.crCntrldAmt));

        Integer newEndCnt = nvlInt(s.getSumEndingCnt()) - 1;
        BigDecimal newEndAmt = nvl(s.getSumEndingAmt()).subtract(nvl(r.crCntrldAmt));

        int updated = p09SummaryRepository.updateSummary(
                newDelCnt, newDelAmt,
                newEndCnt, newEndAmt,
                locationId, clerkId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                "ERROR IN MODIFYING P09_SUMMARY for locationId=" + locationId + ", clerkId=" + clerkId
                + " IN PARAGRAPH 525000-UPDT-SUMMARY (updated=" + updated + ")"
            );
        }
    }

private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }


    private LocalDate perform530000UpdtOccsRcd(IssuedChkRow r, ExecutionContext ec, P09352OutputWrapper wrapper) {
        if ("PER".equals(r.crRefundType) || "OTH".equals(r.crRefundType) || "OFF".equals(r.crRefundType)) return null;

        final String checkStatusCode =
                "SPO".equals(r.crRefundType) ? "SR" :
                "PRR".equals(r.actActivity)  ? "PR" : "FR";

        final String statusSourceCode = "SR";

        final boolean stDaily = "OS".equals(r.occsCheckStatus) || "SD".equals(r.occsCheckStatus) || "TS".equals(r.occsCheckStatus);
        final String stDailyFlag = stDaily ? "Y" : "N";

        LocalDate checkStatusDate;
        LocalDate reportDate;

        if (stDaily) {
            OccsDates d = perform531000DetermineOccsDates(r, ec);
            checkStatusDate = d.checkStatusDate();
            reportDate = d.reportDate();
        } else {
            checkStatusDate = r.actActivityDate;
            reportDate = r.occsDbRptDate;
        }

        int rows = bankReconRepository.updateBankReconRecord(
                checkStatusCode,
                checkStatusDate,
                statusSourceCode,
                stDailyFlag,
                reportDate,
                r.occsFileIndicator,
                r.crBankAcctNbr,
                r.crCheckNbr,
                r.crCheckDate
        );

        if (rows != 1) {
            throw new IllegalStateException(
                    "ERROR IN MODIFYING DCLV-BANK-RECON IN PARAGRAPH 530000-UPDT-OCCS-RCD " +
                    "(rowsUpdated=" + rows + ") key=" + r.occsFileIndicator + "/" + r.crBankAcctNbr + "/" +
                    r.crCheckNbr + "/" + r.crCheckDate
            );
        }

        // Build XP07 records (532000) and add to wrapper (as you already do)
        P09CashReceipt cr = cashReceiptRepository
                .findById(new CashReceiptPK(r.crRefundType, r.crCntrlDate, r.crCntrlNbr))
                .orElseThrow(() -> new IllegalStateException(
                        "CashReceipt not found for 532000 build key=" + r.crRefundType + "/" + r.crCntrlDate + "/" + r.crCntrlNbr
                ));

        List<P09352XP07DedsOutput> deds = perform532000CreateOccsDedsRcd(
                r, ec,
                cr,
                checkStatusCode,
                checkStatusDate,
                statusSourceCode,
                stDailyFlag,
                reportDate
        );

        if (deds != null && !deds.isEmpty()) {
            if (wrapper.getOccsRecords() == null) wrapper.setOccsRecords(new ArrayList<>());
            wrapper.getOccsRecords().addAll(deds);
        }

        return checkStatusDate; // ✅ critical for 540000
    }

    // Small value object for 531000 result (use record if Java 16+)
    private static class OccsDates {
        private final LocalDate checkStatusDate;
        private final LocalDate reportDate;
        OccsDates(LocalDate c, LocalDate r) { this.checkStatusDate = c; this.reportDate = r; }
        LocalDate checkStatusDate() { return checkStatusDate; }
        LocalDate reportDate() { return reportDate; }
    }
    
    private OccsDates perform531000DetermineOccsDates(IssuedChkRow r, ExecutionContext ec) {

        // COBOL WS-OCCS-RPT-DATE-YYMM should be numeric-ish; keep as String compare.
        String occsRptYYMM = ecGetString(ec, "WS_OCCS_RPT_DATE_YYMM", "");
        String refundType = nullToSpace(r.crRefundType);

        String targetRptYYMM;
        LocalDate controlToDate;

        if ("RET".equals(refundType)) {
            // stored by 215000 as int -> read as int and format to 4-digit YYMM string
            int retYyMm = ecGetInt(ec, KEY_WS_RET_RPT_DATE_YYMM, 0);
            targetRptYYMM = String.format("%04d", retYyMm);
            controlToDate = (LocalDate) ec.get(KEY_WS_RET_CONTROL_TO_DATE);

        } else if ("SPO".equals(refundType)) {
            int spoYyMm = ecGetInt(ec, KEY_WS_SPO_RPT_DATE_YYMM, 0);
            targetRptYYMM = String.format("%04d", spoYyMm);
            controlToDate = (LocalDate) ec.get(KEY_WS_SPO_CONTROL_TO_DATE);

        } else {
            int undYyMm = ecGetInt(ec, KEY_WS_UND_RPT_DATE_YYMM, 0);
            targetRptYYMM = String.format("%04d", undYyMm);
            controlToDate = (LocalDate) ec.get(KEY_WS_UND_CONTROL_TO_DATE);
        }

        // Default path: statusDate=activityDate, reportDate=controlToDate
        LocalDate checkStatusDate = r.actActivityDate;
        LocalDate reportDate = controlToDate;

        // IF WS-OCCS-RPT-DATE-YYMM = target YYMM
        if (!isBlank(occsRptYYMM) && occsRptYYMM.equals(targetRptYYMM)) {

            // Build activity YYMM
            String actYYMM = yymm(r.actActivityDate); // e.g. "2512"

            // IF WS-ACT-DATE-YYMM > WS-OCCS-RPT-DATE-YYMM
            if (!isBlank(actYYMM) && actYYMM.compareTo(occsRptYYMM) > 0) {
                LocalDate calc = (r.occsCalcRptDate != null) ? r.occsCalcRptDate : r.actActivityDate;
                checkStatusDate = calc;
                reportDate = calc;
            } else {
                checkStatusDate = r.actActivityDate;
                reportDate = controlToDate;
            }
        } else {
            checkStatusDate = r.actActivityDate;
            reportDate = controlToDate;
        }

        return new OccsDates(checkStatusDate, reportDate);
    }

    private String yymm(LocalDate d) {
        if (d == null) return "";
        int yy = d.getYear() % 100;
        int mm = d.getMonthValue();
        return String.format("%02d%02d", yy, mm);
    }

 // ================================================================
 // 532000 - CREATE OCCS DEDS RCD (XP07)
 // COBOL: MOVE SPACES TO I-P07DEDS-RECORD then populate fields
 // ================================================================
    private List<P09352XP07DedsOutput> perform532000CreateOccsDedsRcd(
            IssuedChkRow r,
            ExecutionContext ec,
            P09CashReceipt cr,                 // holds CR-REM-* after 522500
            String checkStatusCode,            // CHECK-STATUS-CODE
            LocalDate checkStatusDate,         // CHECK-STATUS-DATE
            String statusSourceCode,           // STATUS-SOURCE-CODE
            String stDailyFlag,                // ST-DAILY ('Y'/'N')
            LocalDate reportDate               // REPORT-DATE
    ) {
     P09352XP07DedsOutput o = new P09352XP07DedsOutput();

     // ------------------------------------------------------------
     // MOVE SPACES TO I-P07DEDS-RECORD  (explicitly set blanks)
     // ------------------------------------------------------------
     o.setP07Filler1("     ");
     o.setP07ChkFiller("  ");
     o.setP07Title("   ");
     o.setP07FillerFinal(padRight("", 55));

     // ------------------------------------------------------------
     // PERFORM 561500-CALC-JULIAN-DATE + MOVE TIME
     // ------------------------------------------------------------
     o.setP07JulianDate(perform561500CalcJulianDate(ec));           // BigDecimal YYDDD
     o.setP07TimeHhmmsss(BigDecimal.valueOf(calcHhMmSss()));        // BigDecimal HHMMSSS

     o.setP07ActionId("02");
     String bankFull = nullToSpace(r.crBankAcctNbr);
     o.setP07BatchNbr(padRight(nullToSpace(ecGetString(ec, "WS_DEDS_BATCH_NBR", "")), 6));

     // ------------------------------------------------------------
     // MOVE T-OCCS-FILE-INDICATOR TO I-P07DEDS-FILE
     // ------------------------------------------------------------
     o.setP07FileCode(padRight(nullToSpace(r.occsFileIndicator), 1));

     // ------------------------------------------------------------
     // MOVE WS-BANK-ACCT-1 TO I-P07DEDS-ACCT  (first char, padded to 35)
     // ------------------------------------------------------------
     String bank1 = bankFull.isEmpty() ? "" : bankFull.substring(0, 1);
     o.setP07AccountNbr(padRight(bank1, 35));

     // ------------------------------------------------------------
     // MOVE check fields
     // ------------------------------------------------------------
     o.setP07ChkNbr(padRight(nullToSpace(r.crCheckNbr), 6));        // after 2-char filler
     o.setP07ChkDate(formatMMddyyyy(r.crCheckDate));
     o.setP07ChkAmt(nvlBd(r.crCheckAmt));

     // ------------------------------------------------------------
     // MOVE CR-REM-* fields (from cash receipt after 522500)
     // ------------------------------------------------------------
     o.setP07PayeeId(padRight(nullToSpace(cr.getCrRemIdNbr()), 9));
     o.setP07Npi(padRight(nullToSpace(cr.getCrRemNationalId()), 10));
     o.setP07PayeeName(padRight(nullToSpace(cr.getCrRemAddressee()), 36));

     o.setP07Addr1(padRight(nullToSpace(cr.getCrRemAddress1()), 36));
     o.setP07Addr2(padRight(nullToSpace(cr.getCrRemAddress2()), 36));
     o.setP07City(padRight(nullToSpace(cr.getCrRemCity()), 24));
     o.setP07State(padRight(nullToSpace(cr.getCrRemState()), 2));
     o.setP07Zip5(padRight(nullToSpace(cr.getCrRemZip5()), 5));
     o.setP07Zip4(padRight(nullToSpace(cr.getCrRemZip4()), 4));

     // ------------------------------------------------------------
     // MOVE CHECK-ORIGIN, receipt type, status, dates, source
     // ------------------------------------------------------------
     o.setP07ChkOrigin(resolveChkOrigin(cr));   // already 1-char (E/M)
     o.setP07ChkType(padRight(nullToSpace(r.crReceiptType), 2));
     o.setP07ChkStatus(padRight(nullToSpace(checkStatusCode), 2));
     o.setP07ChkStatusDate(formatMMddyyyy(checkStatusDate));
     o.setP07StatusSource(padRight(nullToSpace(statusSourceCode), 2));

     // ------------------------------------------------------------
     // MOVE 'N' TO OS-DAILY, MOVE ST-DAILY
     // ------------------------------------------------------------
     o.setP07OsDaily("N");
     o.setP07StDaily(padRight(nullToSpace(stDailyFlag), 1));

     // ------------------------------------------------------------
     // NF fields: COBOL MOVE -1  (these are Integer in your entity)
     // ------------------------------------------------------------
     Integer minusOne = Integer.valueOf(-1);

     o.setP07SdDailyNf(minusOne);
     o.setP07TsDailyNf(minusOne);
     o.setP07StaleDateNf(minusOne);
     o.setP07StaleOriginNf(minusOne);
     o.setP07TransferDateNf(minusOne);
     o.setP07TransferOriginNf(minusOne);
     o.setP07ReissueAcctNbrNf(minusOne);
     o.setP07ReissueChkNbrNf(minusOne);
     o.setP07ReissueChkDateNf(minusOne);
     o.setP07ReissueChkTypeNf(minusOne);

     o.setP07ReportDate(formatMMddyyyy(reportDate));

     o.setP07InitialAcctNbrNf(minusOne);
     o.setP07InitialChkNbrNf(minusOne);
     o.setP07InitialChkDateNf(minusOne);
     o.setP07InitialChkTypeNf(minusOne);
     o.setP07PpaDateNf(minusOne);

     o.setP07PayeeIdType(padRight(nullToSpace(cr.getCrRemIdType()), 1));
     o.setP07PayeeIdTypeNf(minusOne);

     o.setP07TaxIdNbr(padRight(nullToSpace(cr.getCrRemTaxIdNbr()), 9));
     o.setP07TaxIdNbrNf(minusOne);

     return List.of(o);
 }
    
 // 540000 - insert CAN activity when OCCS status indicates cancel (COBOL DB-STORE) using JPA save()
    private void perform540000InsrtCanActRcd(IssuedChkRow r, ExecutionContext ec, LocalDate checkStatusDate) {

        if ("PER".equals(r.crRefundType) || "OTH".equals(r.crRefundType) || "OFF".equals(r.crRefundType)) return;
        if (!("OS".equals(r.occsCheckStatus) || "SD".equals(r.occsCheckStatus) || "TS".equals(r.occsCheckStatus))) return;

        LocalDate actActivityDate = wsDb2CurrentDate(ec);
        LocalDate xrefDate = (checkStatusDate != null) ? checkStatusDate : actActivityDate;

        String reportDate = reportDateForRefundTypeCobol540(r.crRefundType, ec); // must exist for RET/UND/OFF/SPO
        if (isBlank(reportDate)) {
            throw new IllegalStateException("540000: missing reportDate MMYY for refundType=" + r.crRefundType);
        }

        P09Activity a = new P09Activity();
        ActivityPK pk = new ActivityPK();
        pk.setCrRefundType(r.crRefundType);
        pk.setCrCntrlDate(r.crCntrlDate);
        pk.setCrCntrlNbr(r.crCntrlNbr);
        pk.setActActivityDate(actActivityDate);
        pk.setActActivity("CAN");
        pk.setActTimestamp(LocalDateTime.now()); // required by your PK

        a.setAId(pk);

        a.setActActivityAmt(null);        // IND = -1
        a.setActWorkingBal(null);         // IND = -1

        a.setActXrefType("CK");
        a.setActXrefNbr(padRight(nullToSpace(r.crCheckNbr), 20)); // no trim
        a.setActXrefDate(xrefDate);

        a.setActReportDate(padRight(reportDate, 4));
        a.setActUserId(padRight("   SYST", 7));
        a.setActCmtInd(" ");
        a.setActDailyInd("Y");
        a.setActProcessedInd(" ");

        a.setActArrsCode("  "); // only because DB says NOT NULL

        activityRepository.save(a);
    }

 // 550000 - insert PR/FR activity (COBOL DB-STORE) using JPA save()
    private void perform550000InsrtPrFrActRcd(IssuedChkRow r, ExecutionContext ec) {

        LocalDate actActivityDate = wsDb2CurrentDate(ec);
        String activity = "PRR".equals(r.actActivity) ? "PR " : "FR ";

        String reportDate = reportDateForRefundTypeCobol550(r.crRefundType, ec);
        if (isBlank(reportDate)) {
            throw new IllegalStateException("550000: missing reportDate MMYY for refundType=" + r.crRefundType);
        }

        P09Activity a = new P09Activity();
        ActivityPK pk = new ActivityPK();
        pk.setCrRefundType(r.crRefundType);
        pk.setCrCntrlDate(r.crCntrlDate);
        pk.setCrCntrlNbr(r.crCntrlNbr);
        pk.setActActivityDate(actActivityDate);
        pk.setActActivity(activity);
        pk.setActTimestamp(LocalDateTime.now());

        a.setAId(pk);

        a.setActActivityAmt(nvlBd(r.actActivityAmt));
        a.setActWorkingBal(nvlBd(r.newCrReceiptBal));

        a.setActXrefType(" ");
        a.setActXrefNbr(padRight(" ", 20));
        a.setActXrefDate(null);

        a.setActReportDate(padRight(reportDate, 4));
        a.setActUserId(padRight("   SYST", 7));
        a.setActCmtInd(" ");
        a.setActDailyInd("Y");
        a.setActProcessedInd("P");

        a.setActArrsCode("  "); // because DB says NOT NULL

        activityRepository.save(a);
    }
    
 // ================================================================
 // 560000-CREATE-GL-DEDS-RCDS  (XP09 output records)
 // ================================================================
 private List<P09352XP09DedsOutput> perform560000CreateGlDedsRcds(IssuedChkRow r, ExecutionContext ec) {

     // MOVE SPACES TO P09-GL-DEDS-REC.
     List<P09352XP09DedsOutput> out = new ArrayList<>();

     // PERFORM 561000-FIXED-GL-INFO
     P09352XP09DedsOutput base = perform561000FixedGlInfo(r, ec);

     // PERFORM 565000-CRT-ACT-DBT-GL-RCD
     out.add(perform565000CrtActDbtGlRcd(r, base));

     // PERFORM 566000-CRT-ACT-CRDT-GL-RCD
     out.add(perform566000CrtActCrdtGlRcd(r, base));

     // IF refund type PER OR OTH -> EXIT
     if ("PER".equals(r.crRefundType) || "OTH".equals(r.crRefundType)) {
         return out;
     }

     // MOVE 'CAN' TO GL-ACT-CODE.
     // (COBOL sets GL-ACT-CODE to CAN before choosing OS/SD/TS)
     // In your Java output layout GL-ACT-CODE is X(3) but COBOL uses 'CAN' as the value here.
     // We'll set it on the cloned records below.
     if ("OS".equals(r.occsCheckStatus)) {
         out.addAll(perform567000CrtOccsOsGlRcd(r, base, ec));
     } else if ("SD".equals(r.occsCheckStatus)) {
         out.addAll(perform568000CrtOccsSdGlRcd(r, base));
     } else if ("TS".equals(r.occsCheckStatus)) {
         out.addAll(perform569000CrtOccsTsGlRcd(r, base));
     }

     return out;
 }

 // ================================================================
 // 561000-FIXED-GL-INFO
 // ================================================================
 private P09352XP09DedsOutput perform561000FixedGlInfo(IssuedChkRow r, ExecutionContext ec) {

     // MOVE WS-TIME-HHMMSSS TO P09DEDS-HHMMSSS.
     int hhmmsss = calcHhMmSss();

     P09352XP09DedsOutput gl = new P09352XP09DedsOutput();

     // 05 GL-P09DEDS-DATE-TIME (julian + time)
     gl.setGlJulianDate(perform561500CalcJulianDate(ec));
     gl.setGlTimeHhmmsss(BigDecimal.valueOf(hhmmsss));

     // 05 FILLER PIC X(5)
     gl.setGlFiller1("     ");

     // MOVE '04' TO GL-P09DEDS-ID
     gl.setGlRecordId("04");

     // MOVE T-CR-REFUND-TYPE ... etc
     gl.setGlRefundType(safeStr(r.crRefundType));
     gl.setGlControlDate(formatIso(r.crCntrlDate));
     gl.setGlControlNbr(safeStr(r.crCntrlNbr));
     gl.setGlReceiptType(safeStr(r.crReceiptType));
     gl.setGlBankAcctNbr(safeStr(r.crBankAcctNbr));

     // IF T-ACT-ACTIVITY = 'PRR' MOVE 'PR ' ELSE 'FR '
     if ("PRR".equals(r.actActivity)) gl.setGlActCode("PR ");
     else gl.setGlActCode("FR ");

     // MOVE ACT-ACTIVITY-DATE TO GL-ACT-DATE
     gl.setGlActDate(formatIso(r.actActivityDate));

     // MOVE ACT-REPORT-DATE TO GL-REPORT-DATE (MMYY)
     // Your writer expects MO + YR as separate 2-char fields.
     String mmyy = safeStr(r.actReportDate).trim(); // expected "MMYY"
     if (mmyy.length() >= 4) {
         gl.setGlReportMo(mmyy.substring(0, 2));
         gl.setGlReportYr(mmyy.substring(2, 4));
     } else {
         gl.setGlReportMo("  ");
         gl.setGlReportYr("  ");
     }

     gl.setGlPatientLn(padRight(safeStr(r.crPatientLname), 15));
     gl.setGlPatientFn(padRight(safeStr(r.crPatientFname), 11));
     gl.setGlMemberId(padRight(safeStr(r.crMbrIdNbr), 12));

     gl.setGlXrefType(padRight(safeStr(r.actXrefType), 2));
     gl.setGlXrefClaimNbr(padRight(safeStr(r.actXrefNbr), 20));
     gl.setGlXrefDate(formatIso(r.actXrefDate));

     // MOVE T-NEW-CR-RECEIPT-BAL TO GL-CASH-REC-BAL
     gl.setGlCashRecBal(nvlBd(r.newCrReceiptBal));

     // MOVE T-CORP TO GL-CORP
     gl.setGlCorp(padRight(safeStr(r.crCorp), 2));

     // 05 GL-FILLER PIC X(8)
     gl.setGlFiller2("        ");

     return gl;
 }

 // ================================================================
 // 561500-CALC-JULIAN-DATE
 // COBOL: convert "current date" to YYDDD style numeric
 // ================================================================
 private BigDecimal perform561500CalcJulianDate(ExecutionContext ec) {
	    LocalDate d = wsDb2CurrentDate(ec); // run date like COBOL ACCEPT DATE
	    int yy = d.getYear() % 100;
	    int ddd = d.getDayOfYear();
	    return BigDecimal.valueOf(yy * 1000 + ddd); // YYDDD
	}
 
    private LocalDate wsDb2CurrentDate(ExecutionContext ec) {
	    Object v = (ec != null) ? ec.get("WS_DB2_CURRENT_DATE") : null;
	    if (v instanceof LocalDate ld) return ld;
	    return LocalDate.now();
	}
 // ================================================================
 // 565000-CRT-ACT-DBT-GL-RCD
 // ================================================================
 private P09352XP09DedsOutput perform565000CrtActDbtGlRcd(IssuedChkRow r, P09352XP09DedsOutput base) {

     P09352XP09DedsOutput gl = cloneGl(base);

     // IF refund type PER and corp 01 -> 2800..., else -> 2812...
     if ("PER".equals(r.crRefundType)) {
    	    if ("01".equals(r.crCorp)) gl.setGlAcctNbr("280000000000");
    	    else gl.setGlAcctNbr("281200000000");
    	} else {
    	    gl.setGlAcctNbr(padRight(safeStr(r.crGlAcctNbr), 12));
     }

     gl.setGlActAmt(nvlBd(r.actActivityAmt));
     return gl;
 }

 // ================================================================
 // 566000-CRT-ACT-CRDT-GL-RCD
 // ================================================================
 private P09352XP09DedsOutput perform566000CrtActCrdtGlRcd(IssuedChkRow r, P09352XP09DedsOutput base) {

     P09352XP09DedsOutput gl = cloneGl(base);

     // IF receipt type in FS/FB/FH/FF/FC/FD/FV => 277100000000 else 10953
     if (isFsFamily(r.crReceiptType)) gl.setGlAcctNbr("277100000000");
     else gl.setGlAcctNbr(padRight("10953", 12));

     // COMPUTE GL-ACT-AMT = (activityAmt * -1)
     gl.setGlActAmt(nvlBd(r.actActivityAmt).negate());
     return gl;
 }

 // ================================================================
 // 567000-CRT-OCCS-OS-GL-RCD  (creates TWO records: debit+credit)
 // ================================================================
 private List<P09352XP09DedsOutput> perform567000CrtOccsOsGlRcd(IssuedChkRow r, P09352XP09DedsOutput base, ExecutionContext ec) {

     // MOVE bank acct -> WS-BANK-ACCT, MOVE WS-BANK-ACCT-1 -> LIKE value, FIND option row
     String occsDebitAcct = perform567500FindOccsBankGlAcct(r, ec);

     BigDecimal amt = nvlBd(r.crCheckAmt);

     // debit
     P09352XP09DedsOutput debit = cloneGl(base);
     debit.setGlActCode("CAN");
     debit.setGlAcctNbr(padRight(occsDebitAcct, 12));
     debit.setGlActAmt(amt);

     // credit
     P09352XP09DedsOutput credit = cloneGl(base);
     credit.setGlActCode("CAN");
     credit.setGlAcctNbr(padRight(safeStr(r.crGlAcctNbr), 12));
     credit.setGlActAmt(amt.negate());

     return List.of(debit, credit);
 }

 // ================================================================
 // 567500-FIND-OCCS-BANK-GL-RCD (V_P09_OPTION lookup)
 // COBOL: OPT_RECORD_TYPE = WS-RECORD-TYPE AND OPT_FIELD_NARR LIKE :WS-LIKE-VALUE
 // ================================================================
 private String perform567500FindOccsBankGlAcct(IssuedChkRow r, ExecutionContext ec) {
	    final short recordType = 11;

	    final String bankAcct = safeStr(r.crBankAcctNbr);
	    if (bankAcct.isBlank()) {
	        throw new IllegalStateException("567500: blank bank acct, cannot derive LIKE value");
	    }

	    String corpCode = safeStr(ec.getString("WS_CORP_CODE", ""));
	    corpCode = corpCode.trim();
	    final List<String> likeCandidates = new ArrayList<>();

	    if (!corpCode.isBlank()) {
	        likeCandidates.add(corpCode + "%");
	        likeCandidates.add(corpCode + bankAcct.substring(0, Math.min(2, bankAcct.length())) + "%");
	    }
	    likeCandidates.add(bankAcct.substring(0, Math.min(2, bankAcct.length())) + "%");
	    likeCandidates.add(bankAcct.substring(0, 1) + "%"); 

	    for (String likeValue : likeCandidates) {
	        List<P09Option> opts = optionRepository.findOptions(recordType, likeValue);
	        if (opts != null && !opts.isEmpty()) {
	            P09Option opt = opts.get(0);
	            String glAcct = safeStr(opt.getOptFieldName());
	            if (!glAcct.isBlank()) {
	                return glAcct;
	            }
	        }
	    }

	    // Still not found -> COBOL-style abend with full debug context
	    throw new IllegalStateException(
	        "ERROR IN READING V_P09_OPTION (567500-FIND-OCCS-BANK-GL-RCD) " +
	        "recordType=" + recordType +
	        ", corpCode=" + corpCode +
	        ", bankAcct=" + bankAcct +
	        ", triedLikes=" + likeCandidates
	    );
	}

 // ================================================================
 // 568000-CRT-OCCS-SD-GL-RCD (two records)
 // ================================================================
 private List<P09352XP09DedsOutput> perform568000CrtOccsSdGlRcd(IssuedChkRow r, P09352XP09DedsOutput base) {

     BigDecimal amt = nvlBd(r.crCheckAmt);

     // debit: MOVE '127500000000'
     P09352XP09DedsOutput debit = cloneGl(base);
     debit.setGlActCode("CAN");
     debit.setGlAcctNbr("127500000000");
     debit.setGlActAmt(amt);

     // credit: MOVE CR-GL-ACCT-NBR, AMT * -1
     P09352XP09DedsOutput credit = cloneGl(base);
     credit.setGlActCode("CAN");
     credit.setGlAcctNbr(padRight(safeStr(r.crGlAcctNbr), 12));
     credit.setGlActAmt(amt.negate());

     return List.of(debit, credit);
 }

 // ================================================================
 // 569000-CRT-OCCS-TS-GL-RCD (two records)
 // ================================================================
 private List<P09352XP09DedsOutput> perform569000CrtOccsTsGlRcd(IssuedChkRow r, P09352XP09DedsOutput base) {

     BigDecimal amt = nvlBd(r.crCheckAmt);

     // debit: MOVE '274900000000'
     P09352XP09DedsOutput debit = cloneGl(base);
     debit.setGlActCode("CAN");
     debit.setGlAcctNbr("274900000000");
     debit.setGlActAmt(amt);

     // credit: MOVE CR-GL-ACCT-NBR, AMT * -1
     P09352XP09DedsOutput credit = cloneGl(base);
     credit.setGlActCode("CAN");
     credit.setGlAcctNbr(padRight(safeStr(r.crGlAcctNbr), 12));
     credit.setGlActAmt(amt.negate());

     return List.of(debit, credit);
 }

//================================================================
//570000-CREATE-AP-INTRFACE  (XAPNTRFC output record)
//================================================================
private P09352ApInterfaceOutput perform570000CreateApInterface(IssuedChkRow r, ExecutionContext ec) {

  // MOVE SPACES TO AP-INTRFACE-OUT-REC.
  P09352ApInterfaceOutput ap = new P09352ApInterfaceOutput();
  ap.setORecdCode(" ");      // will be set to '1' below
  ap.setFiller1(" ");
  ap.setOVoucherNbr(padRight("", 6));
  ap.setOInvoiceMm("  ");
  ap.setOSlash1("/");
  ap.setOInvoiceDd("  ");
  ap.setOSlash2("/");
  ap.setOInvoiceCc("  ");
  ap.setOInvoiceYy("  ");
  ap.setFiller2(" ");
  ap.setOTaxIdNbr(padRight("", 9));
  ap.setOPayeeIdType(" ");
  ap.setOProviderNbr(padRight("", 9));
  ap.setOPayeeName(padRight("", 30));
  ap.setOPayeeAddr1(padRight("", 35));
  ap.setOPayeeAddr2(padRight("", 35));
  ap.setOPayeeCity(padRight("", 20));
  ap.setOPayeeSt(padRight("", 2));
  ap.setOPayeeZip1(padRight("", 5));
  ap.setOPayeeZip2(padRight("", 4));
  ap.setFiller3(" ");
  ap.setOChkAmt(BigDecimal.ZERO);
  ap.setONegInd(" ");
  ap.setOChkNbr(padRight("", 8));
  ap.setOChkDate(padRight("", 10));
  ap.setOChkType(padRight("", 2));
  ap.setOBankAcctNbr(padRight("", 8));
  ap.setONationalId(padRight("", 10));
  ap.setORefundType(padRight("", 3));
  ap.setOCntrlDate(padRight("", 10));
  ap.setOCntrlNbr(padRight("", 4));
  ap.setOMemberId(padRight("", 12));
  ap.setOLname(padRight("", 15));
  ap.setOFname(padRight("", 11));

  // ADD +1 TO WS_LAST_VOUCHER_NBR-SUFFIX.
  int suffix = ec.containsKey("WS_LAST_VOUCHER_NBR_SUFFIX")
          ? ec.getInt("WS_LAST_VOUCHER_NBR_SUFFIX")
          : 0;
  suffix = suffix + 1;
  ec.putInt("WS_LAST_VOUCHER_NBR_SUFFIX", suffix);

  // ADD T-ACT-ACTIVITY-AMT TO WS-AP-BATCH-TOTAL-AMT.
  BigDecimal batchTotal = ec.containsKey("WS_AP_BATCH_TOTAL_AMT")
          ? (BigDecimal) ec.get("WS_AP_BATCH_TOTAL_AMT")
          : BigDecimal.ZERO;
  batchTotal = batchTotal.add(nvlBd(r.actActivityAmt));
  ec.put("WS_AP_BATCH_TOTAL_AMT", batchTotal);

  // MOVE '1' TO O-RECD-CODE.
  ap.setORecdCode("1");

  // MOVE WS-LAST-VOUCHER-NBR TO O-VOUCHER-NBR.
  // COBOL WS-LAST-VOUCHER-NBR is prefix 'Z' + 5-digit suffix; AP field is X(6)
  int suffix1 = ec.containsKey("WS_LAST_VOUCHER_NBR_SUFFIX") ? ec.getInt("WS_LAST_VOUCHER_NBR_SUFFIX") : 0;
  ec.putInt("WS_LAST_VOUCHER_NBR_SUFFIX", suffix1); // no-op write, keeps code shape

  String lastVoucherNbr = ec.getString("WS_LAST_VOUCHER_NBR", "");
  String prefix = !isBlank(lastVoucherNbr) ? lastVoucherNbr.substring(0, 1)
          : ec.getString("WS_LAST_VOUCHER_NBR_PREFIX", "Z");

  lastVoucherNbr = prefix + String.format("%05d", suffix1);
  ec.putString("WS_LAST_VOUCHER_NBR", lastVoucherNbr);

  ap.setOVoucherNbr(padRight(lastVoucherNbr, 6));

  // IF T-CR-REM-ADDRESSEE = SPACES
  //    MOVE T-CR-REMITTOR-NAME TO O-PAYEE-NAME
  // ELSE MOVE T-CR-REM-ADDRESSEE TO O-PAYEE-NAME
  if (isBlank(safeStr(r.crRemAddressee))) {
      ap.setOPayeeName(padRight(safeStr(r.crRemittorName), 30));
  } else {
      ap.setOPayeeName(padRight(safeStr(r.crRemAddressee), 30));
  }

  // MOVE T-CR-REMITTOR-TYPE TO O-PAYEE-ID-TYPE.
  ap.setOPayeeIdType(padRight(safeStr(r.crRemittorType), 1));

  // MOVE T-CR-RECEIPT-TYPE TO O-CHK-TYPE.
  ap.setOChkType(padRight(safeStr(r.crReceiptType), 2));

  // MOVE T-CR-CHECK-DATE TO O-CHK-DATE.
  ap.setOChkDate(padRight(formatMMddyyyy(r.crCheckDate), 10));

  // MOVE T-CR-CHECK-NBR TO O-CHK-NBR.
  ap.setOChkNbr(padRight(safeStr(r.crCheckNbr), 8));

  // MOVE T-CR-BANK-ACCT-NBR TO WS-BANK-ACCT; MOVE WS-BANK-ACCT-8 TO O-BANK-ACCT-NBR.
  // -> take last 8 characters (rightmost 8)
  String bankAcct = safeStr(r.crBankAcctNbr);
  ap.setOBankAcctNbr(padRight(right(bankAcct, 8), 8));

  // MOVE provider/national id
  ap.setOProviderNbr(padRight(safeStr(r.crProviderNbr), 9));
  ap.setONationalId(padRight(safeStr(r.nationalIdNbr), 10));

  // MOVE refund/cntrl/member/name fields
  ap.setORefundType(padRight(safeStr(r.crRefundType), 3));
  ap.setOCntrlDate(padRight(formatMMddyyyy(r.crCntrlDate), 10));
  ap.setOCntrlNbr(padRight(safeStr(r.crCntrlNbr), 4));
  ap.setOMemberId(padRight(safeStr(r.crMbrIdNbr), 12));
  ap.setOLname(padRight(safeStr(r.crPatientLname), 15));
  ap.setOFname(padRight(safeStr(r.crPatientFname), 11));

  // MOVE WS-C2-MM/DD/YY TO O-INVOICE-MM/DD/YY and slashes
  // (WS-C2-xx usually is "current date" parts)
  // Prefer control-card date if you store it in EC; else LocalDate.now()
  LocalDate inv = resolveControlCardOrToday(ec);
  String mm = String.format("%02d", inv.getMonthValue());
  String dd = String.format("%02d", inv.getDayOfMonth());
  String yy = String.format("%02d", inv.getYear() % 100);

  ap.setOInvoiceMm(mm);
  ap.setOInvoiceDd(dd);
  ap.setOInvoiceYy(yy);
  ap.setOSlash1("/");
  ap.setOSlash2("/");

  // IF O-INVOICE-YY > '80' MOVE '19' ELSE MOVE '20'
  ap.setOInvoiceCc(yy.compareTo("80") > 0 ? "19" : "20");

  // MOVE T-ACT-ACTIVITY-AMT TO O-CHK-AMT.
  BigDecimal amt = nvlBd(r.actActivityAmt);
  ap.setOChkAmt(amt.abs());                 // unsigned 9(9)V99
  ap.setONegInd(amt.signum() >= 0 ? " " : "-");

  // MOVE tax id + payee id type again (COBOL does it twice)
  ap.setOTaxIdNbr(padRight(safeStr(r.crTaxIdNbr), 9));
  ap.setOPayeeIdType(padRight(safeStr(r.crRemittorType), 1));

  // IF rem address 1 = SPACES -> use CHECK address fields else REM fields
  if (isBlank(safeStr(r.crRemAddress1))) {
      ap.setOPayeeAddr1(padRight(safeStr(r.crChkAddress1), 35));
      ap.setOPayeeAddr2(padRight(safeStr(r.crChkAddress2), 35));
      ap.setOPayeeCity(padRight(safeStr(r.crChkCity), 20));
      ap.setOPayeeSt(padRight(safeStr(r.crChkState), 2));
      ap.setOPayeeZip1(padRight(safeStr(r.crChkZip5), 5));
      ap.setOPayeeZip2(padRight(safeStr(r.crChkZip4), 4));
  } else {
      ap.setOPayeeAddr1(padRight(safeStr(r.crRemAddress1), 35));
      ap.setOPayeeAddr2(padRight(safeStr(r.crRemAddress2), 35));
      ap.setOPayeeCity(padRight(safeStr(r.crRemCity), 20));
      ap.setOPayeeSt(padRight(safeStr(r.crRemState), 2));
      ap.setOPayeeZip1(padRight(safeStr(r.crRemZip5), 5));
      ap.setOPayeeZip2(padRight(safeStr(r.crRemZip4), 4));
  }

  // WRITE AP-INTRFACE-OUT-REC.
  return ap;
}

 // ================================================================
 // helpers
 // ================================================================

private static String ecStr(ExecutionContext ec, String key) {
    Object v = (ec == null) ? null : ec.get(key);
    return v == null ? "" : v.toString();
}

private String right(String s, int n) {
    if (s == null) return "";
    if (s.length() <= n) return s;
    return s.substring(s.length() - n);
}

private String formatMMddyyyy(LocalDate d) {
    if (d == null) return "";
    return d.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy"));
}

private LocalDate resolveControlCardOrToday(ExecutionContext ec) {
    try {
        String iso = ec.getString("WS_DB2_CONTROL_CARD_DATE", null); // you used this already
        if (iso != null && !iso.isBlank()) return LocalDate.parse(iso);
    } catch (Exception ignore) {}
    return LocalDate.now();
}
 private boolean isFsFamily(String receiptType) {
     if (receiptType == null) return false;
     return switch (receiptType) {
         case "FS", "FB", "FH", "FF", "FC", "FD", "FV" -> true;
         default -> false;
     };
 }

 private int calcHhMmSss() {
     // COBOL is PIC S9(7) = HHMMSSS (hours, minutes, milliseconds)
     var t = java.time.LocalTime.now();
     int hh = t.getHour();
     int mm = t.getMinute();
     int ms = t.getNano() / 1_000_000; // 0..999
     return hh * 100000 + mm * 1000 + ms; // HH(2) MM(2) SSS(3) => 7 digits max
 }

    private String formatIso(LocalDate d) {
	    return (d == null) ? "          " : d.format(DateTimeFormatter.ISO_DATE); // "yyyy-MM-dd"
	}

 private String padRight(String s, int len) {
     if (s == null) s = "";
     if (s.length() >= len) return s.substring(0, len);
     return s + " ".repeat(len - s.length());
 }

 private P09352XP09DedsOutput cloneGl(P09352XP09DedsOutput src) {
     P09352XP09DedsOutput d = new P09352XP09DedsOutput();
     d.setGlJulianDate(src.getGlJulianDate());
     d.setGlTimeHhmmsss(src.getGlTimeHhmmsss());
     d.setGlFiller1(src.getGlFiller1());
     d.setGlRecordId(src.getGlRecordId());
     d.setGlRefundType(src.getGlRefundType());
     d.setGlControlDate(src.getGlControlDate());
     d.setGlControlNbr(src.getGlControlNbr());
     d.setGlReceiptType(src.getGlReceiptType());
     d.setGlBankAcctNbr(src.getGlBankAcctNbr());
     d.setGlAcctNbr(src.getGlAcctNbr());
     d.setGlActCode(src.getGlActCode());
     d.setGlActAmt(src.getGlActAmt());
     d.setGlActDate(src.getGlActDate());
     d.setGlReportMo(src.getGlReportMo());
     d.setGlReportYr(src.getGlReportYr());
     d.setGlPatientLn(src.getGlPatientLn());
     d.setGlPatientFn(src.getGlPatientFn());
     d.setGlMemberId(src.getGlMemberId());
     d.setGlXrefType(src.getGlXrefType());
     d.setGlXrefClaimNbr(src.getGlXrefClaimNbr());
     d.setGlXrefDate(src.getGlXrefDate());
     d.setGlCashRecBal(src.getGlCashRecBal());
     d.setGlCorp(src.getGlCorp());
     d.setGlFiller2(src.getGlFiller2());
     return d;
 }
    
    // ------------------------------------------------
    // Helpers
    // ------------------------------------------------
 // COBOL 540000: report date only for RET/UND/OFF/SPO (PER/OTH excluded by earlier return)
    private String reportDateForRefundTypeCobol540(String refundType, ExecutionContext ec) {
        String rt = safeStr(refundType);
        if ("RET".equals(rt)) return String.valueOf(ec.getInt(KEY_WS_RET_RPT_DATE_MMYY));
        if ("UND".equals(rt)) return String.valueOf(ec.getInt(KEY_WS_UND_RPT_DATE_MMYY));
        if ("OFF".equals(rt)) return String.valueOf(ec.getInt(KEY_WS_OFF_RPT_DATE_MMYY));
        if ("SPO".equals(rt)) return String.valueOf(ec.getInt(KEY_WS_SPO_RPT_DATE_MMYY));
        return null;
    }

    // COBOL 550000: PER + OTH use WS-PER-RPT-DATE-MMYY, others use their own
    private String reportDateForRefundTypeCobol550(String refundType, ExecutionContext ec) {
        String rt = safeStr(refundType);
        if ("PER".equals(rt) || "OTH".equals(rt)) return String.valueOf(ec.getInt(KEY_WS_PER_RPT_DATE_MMYY));
        if ("RET".equals(rt)) return String.valueOf(ec.getInt(KEY_WS_RET_RPT_DATE_MMYY));
        if ("UND".equals(rt)) return String.valueOf(ec.getInt(KEY_WS_UND_RPT_DATE_MMYY));
        if ("OFF".equals(rt)) return String.valueOf(ec.getInt(KEY_WS_OFF_RPT_DATE_MMYY));
        if ("SPO".equals(rt)) return String.valueOf(ec.getInt(KEY_WS_SPO_RPT_DATE_MMYY));
        return null;
    }

    private static int yymmNum(LocalDate d) {
        if (d == null) return 0;
        int yy = d.getYear() % 100;
        int mm = d.getMonthValue();
        return (yy * 100) + mm;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nullToSpace(String s) {
        return s == null ? " " : s;
    }

    private static String safeStr(String s) {
        return s == null ? "" : s.trim();
    }

    private static BigDecimal nvlBd(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
    }

    private static Integer nvlInt(Integer v) {
        return v == null ? 0 : v;
    }

    private static void incInt(ExecutionContext ec, String key, int delta) {
        int v = ec.containsKey(key) ? ec.getInt(key) : 0;
        ec.putInt(key, v + delta);
    }

    private static void addBd(ExecutionContext ec, String key, BigDecimal delta) {
        BigDecimal v = ec.containsKey(key)
                ? (BigDecimal) ec.get(key)
                : BigDecimal.ZERO;

        ec.put(key, v.add(delta == null ? BigDecimal.ZERO : delta));
    }

    private static void addByRefund(ExecutionContext ec, String refundType, String bucket, BigDecimal checkAmt) {
        String rt = safeStr(refundType);

        incInt(ec, "WS_GR_TOT_" + rt + "_CHK_" + bucket + "_CNT", 1);
        addBd(ec, "WS_GR_TOT_" + rt + "_CHK_" + bucket + "_AMT", checkAmt);

        incInt(ec, "WS_GRAND_TOT_CHK_" + bucket + "_CNT", 1);
        addBd(ec, "WS_GRAND_TOT_CHK_" + bucket + "_AMT", checkAmt);
    }

    private <T> T readOne(FlatFileItemReader<T> reader, String logicalName) {
        try {
            // DO NOT open here if the framework already opened it.
            // Just read one line.
            return reader.read();
        } catch (Exception ex) {
            log.error("Error reading {}: {}", logicalName, ex.getMessage(), ex);
            throw cancel("I/O error reading " + logicalName);
        }
    }

    private RuntimeException cancel(String message) {
        return new IllegalStateException(message);
    }

    private boolean isNumeric(String s) {
        if (s == null || s.isBlank()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    
    private String resolveChkOrigin(P09CashReceipt cr) {
        return "Y".equals(cr.getCrExtractedInd()) ? "E" : "M";
    }
    
    private String nvl(String s) {
        return (s == null) ? "" : s;
    }

    private static String[] splitToMMDDYY(String datePicker) {
        if (datePicker == null) {
            throw new IllegalArgumentException("datePicker jobParameter is missing");
        }

        String v = datePicker.trim();
        String digits = v.replaceAll("[^0-9]", "");

        if (digits.length() == 6) {
            // MMDDYY
            return new String[] { digits.substring(0,2), digits.substring(2,4), digits.substring(4,6) };
        }

        if (digits.length() == 8) {
            // MMDDYYYY or YYYYMMDD
            int first4 = Integer.parseInt(digits.substring(0,4));
            if (first4 >= 1900) {
                // YYYYMMDD
                String yyyy = digits.substring(0,4);
                return new String[] { digits.substring(4,6), digits.substring(6,8), yyyy.substring(2,4) };
            } else {
                // MMDDYYYY
                String yyyy = digits.substring(4,8);
                return new String[] { digits.substring(0,2), digits.substring(2,4), yyyy.substring(2,4) };
            }
        }

        throw new IllegalArgumentException(
            "Unsupported datePicker format: '" + datePicker + "'. Expected MMDDYY, MM/DD/YY, MMDDYYYY, YYYYMMDD, or YYYY-MM-DD."
        );
    }
    
    private String pad2(String s) {
        String t = nvl(s).trim();
        if (t.length() == 1) return "0" + t;
        if (t.length() == 0) return "00";
        return t.substring(0, Math.min(2, t.length()));
    }

    private void dbAbendLikeCobol(String dbCommand, Exception ex) {
        log.error("APS PGM  = P09352");
        log.error("PARAGRAPH= 9ST-ERRBAT-ERROR-PARAGRAPH");
        log.error("DB COMMAND: {}", dbCommand);
        if (ex != null) log.error("EXCEPTION: {}", ex.getMessage(), ex);
    }

    private void logCobolDbErrorDisplays(String dbCommand, Integer sqlCode) {
        log.error("APS PGM  = P09352");
        log.error("PARAGRAPH= 9ST-ERRBAT-ERROR-PARAGRAPH");
        log.error("DB COMMAND: {}", dbCommand);
        if (sqlCode != null) log.error("SQLCODE: {}", sqlCode);
    }

    private void cobolDisplayCheckpointInvalid(P09352CheckpointCardInput chkp) {
        String cnt = (chkp == null) ? "NULL" : Objects.toString(chkp.getCount(), "NULL");
        log.error("***************************************");
        log.error("***    CHECKPOINT CONTROL CARD IS   ***");
        log.error("***    INVALID FOR PROGRAM P09352   ***");
        log.error("***---------------------------------***");
        log.error("***    CHECKPOINT CARD COUNT IS     ***");
        log.error("***    NON-NUMERIC                  ***");
        log.error("***---------------------------------***");
        log.error("***    CHECKPOINT CARD COUNT IS => {}", cnt);
        log.error("***---------------------------------***");
        log.error("***       CORRECT AND RESUBMIT      ***");
        log.error("***************************************");
    }

    private void cobolDisplayVoucherInvalid(String suffix) {
        String sfx = (suffix == null) ? "NULL" : suffix;
        log.error("***************************************");
        log.error("***    VOUCHER-NBR CONTROL CARD IS  ***");
        log.error("***    INVALID FOR PROGRAM P09352   ***");
        log.error("***---------------------------------***");
        log.error("***    LAST VOUCHER NUMBER SUFFIX   ***");
        log.error("***    IS NON-NUMERIC               ***");
        log.error("***---------------------------------***");
        log.error("***    LAST VOUCHER-NBR SUFFIX IS => {}", sfx);
        log.error("***---------------------------------***");
        log.error("***       CORRECT AND RESUBMIT      ***");
        log.error("***************************************");
    }
    
    private P09CashReceipt currentCashReceipt(ExecutionContext ec) {
        // Backward compatible: if someone still stored entity, use it
        Object v = ec.get(KEY_CURRENT_CASH_RECEIPT);
        if (v instanceof P09CashReceipt cr) return cr;

        CashReceiptPK id = (CashReceiptPK) ec.get(KEY_CURRENT_CASH_RECEIPT_ID);
        if (id == null) return null;

        return cashReceiptRepository.findById(id).orElse(null);
    }

    private P09Activity currentActivity(ExecutionContext ec) {
        Object v = ec.get(KEY_CURRENT_ACTIVITY);
        if (v instanceof P09Activity act) return act;

        ActivityPK id = (ActivityPK) ec.get(KEY_CURRENT_ACTIVITY_ID);
        if (id == null) return null;

        return activityRepository.findById(id).orElse(null);
    }
    
    private CheckControl currentCheckControl(ExecutionContext ec) {
        Object v = ec.get(KEY_CURRENT_CHECK_CONTROL);
        if (v instanceof CheckControl cc) return cc;

        CheckControlPK idObj = (CheckControlPK) ec.get(KEY_CURRENT_CHECK_CONTROL_ID);
        if (idObj == null) return null;
        return checkControlRepository.findById(idObj).orElse(null);
    }
    
 // --- ExecutionContext safe getters (NO removal, additive) ---
    private static String ecGetString(ExecutionContext ec, String key, String def) {
        if (ec == null) return def;
        Object v = ec.get(key);
        if (v == null) return def;
        if (v instanceof String s) return s;
        if (v instanceof Integer i) return String.valueOf(i);
        if (v instanceof Long l) return String.valueOf(l);
        return String.valueOf(v);
    }

    private static int ecGetInt(ExecutionContext ec, String key, int def) {
        if (ec == null) return def;
        if (!ec.containsKey(key)) return def;
        try {
            return ec.getInt(key);
        } catch (Exception e) {
            Object v = ec.get(key);
            if (v instanceof Number n) return n.intValue();
            if (v instanceof String s && !s.isBlank()) return Integer.parseInt(s.trim());
            return def;
        }
    }
    
    private static P09352ReportWriter.RefundCategory mapCategory(String refundType) {
        if ("PER".equals(refundType)) return P09352ReportWriter.RefundCategory.PERSONAL;
        if ("RET".equals(refundType)) return P09352ReportWriter.RefundCategory.RETURNS;
        if ("UND".equals(refundType)) return P09352ReportWriter.RefundCategory.UNDELIVERABLE;
        if ("OTH".equals(refundType)) return P09352ReportWriter.RefundCategory.OTHER;
        if ("OFF".equals(refundType)) return P09352ReportWriter.RefundCategory.OFFSET;
        if ("SPO".equals(refundType)) return P09352ReportWriter.RefundCategory.STOP_PAYMENT;
        return P09352ReportWriter.RefundCategory.OTHER;
    }

}
