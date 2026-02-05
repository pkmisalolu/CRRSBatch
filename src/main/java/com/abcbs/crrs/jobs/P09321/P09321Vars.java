package com.abcbs.crrs.jobs.P09321;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09321Vars {

    private String refundType;

    // ===== 210000 EST =====
    public long wsCrUnreqCnt = 0;
    public long wsCrReqCnt   = 0;
    public long wsTotAddCnt  = 0;

    public BigDecimal wsCrUnreqAmt = BigDecimal.ZERO;
    public BigDecimal wsCrReqAmt   = BigDecimal.ZERO;
    public BigDecimal wsTotAddAmt  = BigDecimal.ZERO;

    // ===== 220000 RESOLVED =====
    public long wsTotResolvedCnt = 0;
    public BigDecimal wsTotResolvedAmt = BigDecimal.ZERO;

    public Map<String,Long> actCnt = new HashMap<>();
    public Map<String,BigDecimal> actAmt = new HashMap<>();

    public Map<String, Long> resUnreqCnt = new HashMap<>();
    public Map<String, Long> resReqCnt   = new HashMap<>();
    public Map<String, Long> resTotCnt   = new HashMap<>();

    public Map<String, BigDecimal> resUnreqAmt = new HashMap<>();
    public Map<String, BigDecimal> resReqAmt   = new HashMap<>();
    public Map<String, BigDecimal> resTotAmt   = new HashMap<>();

    public long resTotUnreqCnt = 0;
    public long resTotReqCnt   = 0;

    public BigDecimal resTotUnreqAmt = BigDecimal.ZERO;
    public BigDecimal resTotReqAmt   = BigDecimal.ZERO;

    // ===== CONTROL RECORD =====
    public LocalDate ctd;
    public LocalDate ctdMinus15;
    public LocalDate ctdMinus1;
    public LocalDate ctdMinus2;
    public LocalDate ctdMinus3;
    public LocalDate ctdMinus4;

    public long ctlReceiptCnt = 0;
    public BigDecimal ctlReceiptAmt = BigDecimal.ZERO;
    public String ctlNarr = "";

    // ===== 230000 ENDING BAL =====
    public long wsCrEndBalCnt = 0;
    public BigDecimal wsCrEndBalAmt = BigDecimal.ZERO;

    // ===== 241000 AGING BUCKETS (MATCH WRITER EXACTLY) =====
    public long end00_15Cnt = 0;
    public long end16_1Cnt  = 0;
    public long end1_2Cnt   = 0;
    public long end2_3Cnt   = 0;
    public long end3_4Cnt   = 0;
    public long endOver4Cnt = 0;

    public BigDecimal end00_15Amt = BigDecimal.ZERO;
    public BigDecimal end16_1Amt  = BigDecimal.ZERO;
    public BigDecimal end1_2Amt   = BigDecimal.ZERO;
    public BigDecimal end2_3Amt   = BigDecimal.ZERO;
    public BigDecimal end3_4Amt   = BigDecimal.ZERO;
    public BigDecimal endOver4Amt = BigDecimal.ZERO;

    // ===== 243000 OPEN/PENDED =====
    public static class OpenBucket {
        public long reqCnt = 0;
        public long unreqCnt = 0;
        public long totCnt = 0;

        public BigDecimal reqAmt = BigDecimal.ZERO;
        public BigDecimal unreqAmt = BigDecimal.ZERO;
        public BigDecimal totAmt = BigDecimal.ZERO;
    }

    public Map<String,OpenBucket> openBuckets = new HashMap<>();

    // ===== 250000 OPEN TOTALS =====
    public long opnTotReqCnt = 0;
    public long opnTotUnreqCnt = 0;
    public long opnTotTotCnt = 0;

    public BigDecimal opnTotReqAmt = BigDecimal.ZERO;
    public BigDecimal opnTotUnreqAmt = BigDecimal.ZERO;
    public BigDecimal opnTotTotAmt = BigDecimal.ZERO;

    // ===== 260000 AGING TOTALS =====
    public long agingTotCnt = 0;
    public BigDecimal agingTotAmt = BigDecimal.ZERO;

    // ===== 270000 RESOLVED TOTALS =====
    public long resTotTotCnt = 0;
    public BigDecimal resTotTotAmt = BigDecimal.ZERO;
    
 // ===== 280000 Monthly Accrual =====
    public BigDecimal wsEndSuspBalAmt = BigDecimal.ZERO;
    public BigDecimal wsAbcbsShareAmt = BigDecimal.ZERO;
    public BigDecimal wsMedipakAmt    = BigDecimal.ZERO;
    public BigDecimal wsOtherAmt      = BigDecimal.ZERO;
    public BigDecimal wsEndGlBalAmt   = BigDecimal.ZERO;
    
    public String rptMm;   // WS-RPT-MM
    public String rptYy;   // WS-RPT-YY
    public String rptDate; // WS-RPT-DATE combined MMYY if needed
    
    private String corpNo;
    private String corpCode;

    
    
}
