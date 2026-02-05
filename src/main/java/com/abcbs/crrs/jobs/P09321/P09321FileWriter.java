package com.abcbs.crrs.jobs.P09321;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemWriter;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

import com.abcbs.crrs.entity.ControlPK;
import com.abcbs.crrs.entity.P09Activity;
import com.abcbs.crrs.entity.P09CashReceipt;
import com.abcbs.crrs.entity.P09Control;
import com.abcbs.crrs.lock.P09TableLocker;
import com.abcbs.crrs.repository.IActivityRepository;
import com.abcbs.crrs.repository.IP09CashReceiptRepository;
import com.abcbs.crrs.repository.IP09ControlRepository;

public class P09321FileWriter implements ItemWriter<P09321Record>, ItemStream {

	private static final Logger log = LogManager.getLogger(P09321FileWriter.class);

	private BufferedWriter reportWriter;

	private final IActivityRepository activityRepository;
	private final IP09CashReceiptRepository receiptRepository;
	private final IP09ControlRepository controlRepository;
	private final String corpFile;
	private final String outputFileName;

	private final P09TableLocker tableLocker;

	public P09321FileWriter(String corpFile, String reportPath, IActivityRepository activityRepository,
			IP09CashReceiptRepository receiptRepository, IP09ControlRepository controlRepository,
			P09TableLocker tableLocker) {
		this.corpFile = corpFile;
		this.outputFileName = reportPath;
		this.activityRepository = activityRepository;
		this.receiptRepository = receiptRepository;
		this.controlRepository = controlRepository;
		this.tableLocker = tableLocker;
	}

	// ItemStream lifecycle
	@Override
	public void open(ExecutionContext ctx) throws ItemStreamException {
		try {
			log.info("Opening report file for write: {}", outputFileName);
			reportWriter = Files.newBufferedWriter(Paths.get(outputFileName));
		} catch (IOException e) {
			log.error("Failed to open report file '{}'", outputFileName, e);
			throw new ItemStreamException("Failed to open report file: " + outputFileName, e);
		}
	}

	@Override
	public void update(ExecutionContext ctx) {
		// no-op
	}

	@Override
	public void close() throws ItemStreamException {
		try {
			if (reportWriter != null) {
				reportWriter.close();
				log.info("Closed report file: {}", outputFileName);
			}
		} catch (IOException e) {
			log.error("Failed to close report file '{}'", outputFileName, e);
			throw new ItemStreamException(e);
		}
	}

	// ItemWriter
	@Override
	public void write(Chunk<? extends P09321Record> items) throws Exception {
		for (P09321Record input : items) {
			try {
				log.debug("Processing input record for refundType={}", input.getRefundType());
				executeJob(input);
			} catch (Exception e) {
				log.error("Error while processing record for refundType={}: {}", input.getRefundType(), e.getMessage(),
						e);
				// Rethrow so the batch will mark step/job failed; caller can decide retry
				// behavior
				throw e;
			}
		}
		if (reportWriter != null) {
			try {
				reportWriter.flush();
			} catch (IOException e) {
				log.warn("Failed to flush reportWriter", e);
			}
		}
	}

	// Main COBOL-equivalent flow
	@Transactional
	private void executeJob(P09321Record input) throws Exception {
		log.info("=== START P09321 job for refundType: {} ===", input.getRefundType());
		P09321Vars ws = new P09321Vars();
		initOpenBuckets(ws); // prepare 01..24 + 99 open buckets
		tableLocker.lockP09Tables();
		initialization(input, ws);

		getControlRecord(ws);
		getCrEstCntsAmts(ws);
		getResolvedCr(ws);
		calcCrEndingBal(ws);
		getAllOpenPendedCr(ws);
		calcOpenTotals(ws);
		calcTotalAging(ws);
		calcResolvedTotals(ws);

		if ("PER".equals(ws.getRefundType())) {
			calcMonthlyAccrual(ws);
		}

		generateDetailLines(ws);
		finalizeControlRecord(ws);
		tableLocker.unlockP09Tables();
		log.info("=== END P09321 job for refundType: {} ===", input.getRefundType());
	}

	private void initialization(P09321Record input, P09321Vars ws) {
		String refundType = input.getRefundType() == null ? "" : input.getRefundType().trim();
		if (!isValidRefundType(refundType)) {
			log.error("*** INVALID REFUND TYPE ON CONTROL CARD: {} ***", refundType);
			throw new IllegalArgumentException("Invalid refund type: " + refundType);
		}
		ws.setRefundType(refundType);
		// Load CORP-FILE exactly like COBOL
		loadCorpFile(ws);
		log.debug("Initialization complete. refundType={}", refundType);
	}

	private boolean isValidRefundType(String refundType) {
		return refundType != null && switch (refundType.trim()) {
		case "PER", "RET", "UND", "OTH", "OFF", "SPO", "API" -> true;
		default -> false;
		};
	}

	private void loadCorpFile(P09321Vars ws) {

		ws.setCorpNo(null);
		ws.setCorpCode(null);

		try {
			List<String> lines = Files.readAllLines(Paths.get(corpFile));

			if (lines.isEmpty()) {
				throw new RuntimeException("CORP-FILE is empty. Expected 1 record of length 80.");
			}

			String line = lines.get(0);

			if (line.length() != 80) {
				throw new RuntimeException(
						"Invalid CORP-FILE record length = " + line.length() + ". Expected exact length = 80.");
			}

			// Extract fields exactly like COBOL
			String corpNo = line.substring(10, 12); // columns 11–12 (0-based 10–11)
			corpNo = corpNo.trim();

			switch (corpNo) {
			case "01":
				ws.setCorpNo("01");
				ws.setCorpCode("CORP 01 - BCBS");
				break;

			case "04":
				ws.setCorpNo("04");
				ws.setCorpCode("CORP 04 - ABCBS MEDIPAK ADVANTAGE");
				break;

			case "03":
				ws.setCorpNo("03");
				ws.setCorpCode("CORP 03 - HMO PARTNERS");
				break;

			default:
				throw new RuntimeException("Unknown CORP-NO: '" + corpNo + "'");
			}

		} catch (IOException e) {
			throw new RuntimeException("Failed to read CORP-FILE: " + corpFile, e);
		}
	}

	private void getControlRecord(P09321Vars ws) {
		String refundType = ws.getRefundType();
		log.debug("Fetching open control record for refundType={}", refundType);
		P09Control control;
		try {
			control = controlRepository.findOpenControl(refundType)
					.orElseThrow(() -> new IllegalStateException("P09_CONTROL not found for refundType=" + refundType));
		} catch (DataAccessException dae) {
			log.error("DB error fetching P09_CONTROL for refundType={}", refundType, dae);
			throw dae;
		} catch (RuntimeException re) {
			log.error("Unexpected error fetching P09_CONTROL for refundType={}", refundType, re);
			throw re;
		}

		LocalDate ctd = control.getCntrlToDate();
		ws.ctd = ctd;
		ws.ctdMinus15 = ctd.minusDays(15);
		ws.ctdMinus1 = ctd.plusDays(1).minusMonths(1);
		ws.ctdMinus2 = ctd.plusDays(1).minusMonths(2);
		ws.ctdMinus3 = ctd.plusDays(1).minusMonths(3);
		ws.ctdMinus4 = ctd.plusDays(1).minusMonths(4);

		ws.rptMm = String.format("%02d", ctd.getMonthValue());
		ws.rptYy = String.format("%02d", ctd.getYear() % 100);
		ws.rptDate = ws.rptMm + ws.rptYy;

		ws.ctlReceiptCnt = control.getCntrlReceiptCnt() == null ? 0L : control.getCntrlReceiptCnt();
		ws.ctlReceiptAmt = control.getCntrlReceiptAmt() == null ? BigDecimal.ZERO : control.getCntrlReceiptAmt();
		ws.ctlNarr = control.getCntrlRefundNarr() == null ? "" : control.getCntrlRefundNarr();
		log.info("Control record loaded for refundType={}: toDate={}, receiptCnt={}, receiptAmt={}", refundType, ws.ctd,
				ws.ctlReceiptCnt, ws.ctlReceiptAmt);
	}

	private void getCrEstCntsAmts(P09321Vars ws) {
		String toDate = ws.rptDate;
		log.debug("Querying EST activity/receipts for refundType={} toDate={}", ws.getRefundType(), toDate);
		List<Object[]> rows;
		try {
			rows = activityRepository.findActivityReceiptRecordsWithCorp(ws.getCorpNo(), ws.getRefundType(),
					List.of("EST"), toDate);
		} catch (DataAccessException dae) {
			log.error("DB error while querying EST records for refundType={}", ws.getRefundType(), dae);
			throw dae;
		}

		for (Object[] row : rows) {
			P09Activity act = (P09Activity) row[0];
			P09CashReceipt rec = (P09CashReceipt) row[1];
			BigDecimal amt = act.getActActivityAmt() == null ? BigDecimal.ZERO : act.getActActivityAmt();

			if (rec.getCrLetterDate() == null) {
				ws.wsCrUnreqCnt++;
				ws.wsCrUnreqAmt = ws.wsCrUnreqAmt.add(amt);
			} else {
				ws.wsCrReqCnt++;
				ws.wsCrReqAmt = ws.wsCrReqAmt.add(amt);
			}

			ws.wsTotAddCnt++;
			ws.wsTotAddAmt = ws.wsTotAddAmt.add(amt);
			log.info("EST counts/amounts: reqCnt={}, unreqCnt={}, totCnt={}, reqAmt={}, unreqAmt={}, totAmt={}",
					ws.wsCrReqCnt, ws.wsCrUnreqCnt, ws.wsTotAddCnt, ws.wsCrReqAmt, ws.wsCrUnreqAmt, ws.wsTotAddAmt);
		}
	}

	private void getResolvedCr(P09321Vars ws) {
		String toDate = ws.rptDate;
		log.debug("Querying resolved activities for refundType={} toDate={}", ws.getRefundType(), toDate);

		List<Object[]> rows;
		try {
			rows = activityRepository.findActivityReceiptRecordsWithCorpOrdered(ws.getCorpNo(), ws.getRefundType(),
					List.of("ACC", "APP", "REM", "DEL", "LOG", "FR ", "PR "), toDate);
		} catch (DataAccessException dae) {
			log.error("DB error while querying resolved activity/receipts for refundType={}", ws.getRefundType(), dae);
			throw dae;
		}

		String prevType = "";
		LocalDate prevDate = null;
		String prevNbr = "";

		for (Object[] row : rows) {
			P09Activity act = (P09Activity) row[0];
			P09CashReceipt rec = (P09CashReceipt) row[1];

			String currType = act.getAId().getCrRefundType();
			LocalDate currDate = act.getAId().getCrCntrlDate();
			String currNbr = act.getAId().getCrCntrlNbr();

			int wsCount;
			if (currType.equals(prevType) && currDate.equals(prevDate) && currNbr.equals(prevNbr)) {
				wsCount = 0;
			} else {
				prevType = currType;
				prevDate = currDate;
				prevNbr = currNbr;
				wsCount = (rec.getCrReceiptBal() == null || rec.getCrReceiptBal().compareTo(BigDecimal.ZERO) == 0) ? 1
						: 0;
			}

			BigDecimal amt = act.getActActivityAmt() == null ? BigDecimal.ZERO : act.getActActivityAmt();
			String actType = act.getAId().getActActivity() == null ? "" : act.getAId().getActActivity().trim();

			ws.actCnt.put(actType, ws.actCnt.getOrDefault(actType, 0L) + wsCount);
			ws.actAmt.put(actType, ws.actAmt.getOrDefault(actType, BigDecimal.ZERO).add(amt));

			ws.wsTotResolvedCnt += wsCount;
			ws.wsTotResolvedAmt = ws.wsTotResolvedAmt.add(amt);

			evalClaimType(ws, rec, wsCount, amt);
		}
		log.info("Resolved totals: resolvedCnt={}, resolvedAmt={}", ws.wsTotResolvedCnt, ws.wsTotResolvedAmt);
	}

	private void evalClaimType(P09321Vars ws, P09CashReceipt rec, int wsCount, BigDecimal amt) {
		String raw = rec.getCrClaimType() == null ? "99" : rec.getCrClaimType().trim();
		String key = P09321FileWriter.CLAIM_MAP.getOrDefault(raw, "99");
		boolean unreq = (rec.getCrLetterDate() == null);

		if (unreq) {
			ws.resUnreqCnt.put(key, ws.resUnreqCnt.getOrDefault(key, 0L) + wsCount);
			ws.resTotCnt.put(key, ws.resTotCnt.getOrDefault(key, 0L) + wsCount);
			ws.resUnreqAmt.put(key, ws.resUnreqAmt.getOrDefault(key, BigDecimal.ZERO).add(amt));
			ws.resTotAmt.put(key, ws.resTotAmt.getOrDefault(key, BigDecimal.ZERO).add(amt));

			ws.resTotUnreqCnt += wsCount;
			ws.resTotUnreqAmt = ws.resTotUnreqAmt.add(amt);
		} else {
			ws.resReqCnt.put(key, ws.resReqCnt.getOrDefault(key, 0L) + wsCount);
			ws.resTotCnt.put(key, ws.resTotCnt.getOrDefault(key, 0L) + wsCount);
			ws.resReqAmt.put(key, ws.resReqAmt.getOrDefault(key, BigDecimal.ZERO).add(amt));
			ws.resTotAmt.put(key, ws.resTotAmt.getOrDefault(key, BigDecimal.ZERO).add(amt));

			ws.resTotReqCnt += wsCount;
			ws.resTotReqAmt = ws.resTotReqAmt.add(amt);
		}
	}

	private void calcCrEndingBal(P09321Vars ws) {
		long ctlCnt = ws.ctlReceiptCnt;
		BigDecimal ctlAmt = ws.ctlReceiptAmt == null ? BigDecimal.ZERO : ws.ctlReceiptAmt;

		ws.wsCrEndBalCnt = ctlCnt;
		ws.wsCrEndBalAmt = ctlAmt;

		ws.wsCrEndBalCnt += ws.wsTotAddCnt;
		ws.wsCrEndBalAmt = ws.wsCrEndBalAmt.add(ws.wsTotAddAmt);

		ws.wsCrEndBalCnt -= ws.wsTotResolvedCnt;
		ws.wsCrEndBalAmt = ws.wsCrEndBalAmt.subtract(ws.wsTotResolvedAmt);
		log.debug("Calculated ending balance cnt={}, amt={}", ws.wsCrEndBalCnt, ws.wsCrEndBalAmt);
	}

	private void getAllOpenPendedCr(P09321Vars ws) {
		LocalDate toDate = ws.ctd;
		log.debug("Querying open/pended receipts for refundType={} toDate={}", ws.getRefundType(), toDate);

		List<P09CashReceipt> recs;
		try {
			recs = receiptRepository.findOpenOrPendedReceiptswithCorp(ws.getCorpNo(), ws.getRefundType(), toDate);
		} catch (DataAccessException dae) {
			log.error("DB error while querying open/pended receipts for refundType={}", ws.getRefundType(), dae);
			throw dae;
		}

		for (P09CashReceipt r : recs) {

			LocalDate recDate = r.getCrId().getCrCntrlDate();
			BigDecimal bal = r.getCrReceiptBal() == null ? BigDecimal.ZERO : r.getCrReceiptBal();

			if ((recDate.equals(ws.ctd) || recDate.isAfter(ws.ctdMinus15)) || recDate.isEqual(ws.ctdMinus15)) {
				ws.end00_15Cnt++;
				ws.end00_15Amt = ws.end00_15Amt.add(bal);
			}

			else if ((recDate.equals(ws.ctdMinus1) || (recDate.isAfter(ws.ctdMinus1)))
					|| recDate.isEqual(ws.ctdMinus1)) {

				ws.end16_1Cnt++;
				ws.end16_1Amt = ws.end16_1Amt.add(bal);
			}

			else if ((recDate.equals(ws.ctdMinus2) || recDate.isAfter(ws.ctdMinus2)) || recDate.isEqual(ws.ctdMinus2)) {
				ws.end1_2Cnt++;
				ws.end1_2Amt = ws.end1_2Amt.add(bal);
			}

			else if ((recDate.equals(ws.ctdMinus3) || recDate.isAfter(ws.ctdMinus3)) || recDate.isEqual(ws.ctdMinus3)) {
				ws.end2_3Cnt++;
				ws.end2_3Amt = ws.end2_3Amt.add(bal);
			}

			else if ((recDate.equals(ws.ctdMinus4) || recDate.isAfter(ws.ctdMinus4)) || recDate.isEqual(ws.ctdMinus4)) {
				ws.end3_4Cnt++;
				ws.end3_4Amt = ws.end3_4Amt.add(bal);
			}

			else {
				ws.endOver4Cnt++;
				ws.endOver4Amt = ws.endOver4Amt.add(bal);
			}

			String raw = r.getCrClaimType() == null ? "99" : r.getCrClaimType().trim();
			String key = P09321FileWriter.CLAIM_MAP.getOrDefault(raw, "99");
			P09321Vars.OpenBucket bucket = ws.openBuckets.get(key);
			if (bucket == null) {
				bucket = new P09321Vars.OpenBucket();
				ws.openBuckets.put(key, bucket);
			}

			boolean unreq = r.getCrLetterDate() == null;
			if (unreq) {
				bucket.unreqCnt++;
				bucket.unreqAmt = bucket.unreqAmt.add(bal);
			} else {
				bucket.reqCnt++;
				bucket.reqAmt = bucket.reqAmt.add(bal);
			}
			bucket.totCnt++;
			bucket.totAmt = bucket.totAmt.add(bal);
		}
		log.info("Fetched {} open/pended receipts; aging buckets populated", recs == null ? 0 : recs.size());
	}

	private void calcOpenTotals(P09321Vars ws) {
		long totReqCnt = 0, totUnreqCnt = 0, totTotCnt = 0;
		BigDecimal totReqAmt = BigDecimal.ZERO, totUnreqAmt = BigDecimal.ZERO, totTotAmt = BigDecimal.ZERO;

		for (Map.Entry<String, P09321Vars.OpenBucket> e : ws.openBuckets.entrySet()) {
			P09321Vars.OpenBucket b = e.getValue();
			totReqCnt += b.reqCnt;
			totUnreqCnt += b.unreqCnt;
			totTotCnt += b.totCnt;

			totReqAmt = totReqAmt.add(b.reqAmt);
			totUnreqAmt = totUnreqAmt.add(b.unreqAmt);
			totTotAmt = totTotAmt.add(b.totAmt);
		}

		ws.opnTotReqCnt = totReqCnt;
		ws.opnTotUnreqCnt = totUnreqCnt;
		ws.opnTotTotCnt = totTotCnt;

		ws.opnTotReqAmt = totReqAmt;
		ws.opnTotUnreqAmt = totUnreqAmt;
		ws.opnTotTotAmt = totTotAmt;
		log.debug("Open totals computed reqCnt={}, unreqCnt={}, totCnt={}, reqAmt={}, unreqAmt={}, totAmt={}",
				totReqCnt, totUnreqCnt, totTotCnt, totReqAmt, totUnreqAmt, totTotAmt);
	}

	private void calcTotalAging(P09321Vars ws) {
		long cnt = ws.end00_15Cnt + ws.end16_1Cnt + ws.end1_2Cnt + ws.end2_3Cnt + ws.end3_4Cnt + ws.endOver4Cnt;
		BigDecimal amt = ws.end00_15Amt.add(ws.end16_1Amt).add(ws.end1_2Amt).add(ws.end2_3Amt).add(ws.end3_4Amt)
				.add(ws.endOver4Amt);

		ws.agingTotCnt = cnt;
		ws.agingTotAmt = amt;
		log.debug("Aging totals cnt={}, amt={}", cnt, amt);
	}

	private void calcResolvedTotals(P09321Vars ws) {
		// resTotReqCnt and resTotUnreqCnt have been incremented inside evalClaimType
		ws.resTotTotCnt = ws.resTotReqCnt + ws.resTotUnreqCnt;
		ws.resTotTotAmt = ws.resTotReqAmt.add(ws.resTotUnreqAmt == null ? BigDecimal.ZERO : ws.resTotUnreqAmt);
		log.debug("Resolved totals computed totCnt={}, totAmt={}", ws.resTotTotCnt, ws.resTotTotAmt);
	}

	private void calcMonthlyAccrual(P09321Vars ws) {

		ws.wsEndSuspBalAmt = ws.opnTotTotAmt;

		// Branch A: CORP 03 or 04 -> only suspense balance
		if ("03".equals(ws.getCorpNo()) || "04".equals(ws.getCorpNo())) {
			// All other values become ZERO
			ws.wsAbcbsShareAmt = BigDecimal.ZERO.setScale(2);
			ws.wsMedipakAmt = BigDecimal.ZERO.setScale(2);
			ws.wsOtherAmt = BigDecimal.ZERO.setScale(2);
			ws.wsEndGlBalAmt = BigDecimal.ZERO.setScale(2);

			log.debug("Monthly accrual (CORP 03/04): only suspense balance={}", ws.wsEndSuspBalAmt);
			return;
		}

		ws.wsAbcbsShareAmt = ws.wsEndSuspBalAmt.multiply(new BigDecimal("0.78")).setScale(2,
				java.math.RoundingMode.HALF_UP);

		ws.wsMedipakAmt = ws.wsEndSuspBalAmt.multiply(new BigDecimal("0.078")).setScale(2,
				java.math.RoundingMode.HALF_UP);

		ws.wsOtherAmt = ws.wsAbcbsShareAmt.subtract(ws.wsMedipakAmt).setScale(2, java.math.RoundingMode.HALF_UP);

		ws.wsEndGlBalAmt = ws.wsEndSuspBalAmt.subtract(ws.wsAbcbsShareAmt).setScale(2, java.math.RoundingMode.HALF_UP);
		log.debug("Monthly accrual calculated: abcbs={}, medipak={}, other={}, endGl={}", ws.wsAbcbsShareAmt,
				ws.wsMedipakAmt, ws.wsOtherAmt, ws.wsEndGlBalAmt);
	}

	private void generateDetailLines(P09321Vars ws) throws IOException {
		log.info("Generating report lines. refundType={}, pageDate={}", ws.getRefundType(), ws.ctd);

		// helpers (keep consistent with COBOL)
		final DateTimeFormatter runDateFmt = DateTimeFormatter.ofPattern("MM/dd/yy");
		final DateTimeFormatter monthEndFmt = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH);
		final DecimalFormat amtFmt = new DecimalFormat("###,##0.00");
		amtFmt.setRoundingMode(RoundingMode.HALF_UP);

		java.util.function.Function<Long, String> fmtCount = (cnt) -> {
			String s = String.valueOf(cnt);
			// COBOL PIC ZZZ,ZZ9 -> align right width 7
			return padLeft(s, 7);
		};

		java.util.function.Function<java.math.BigDecimal, String> fmtAmt = (bd) -> {
			if (bd == null)
				bd = java.math.BigDecimal.ZERO;
			bd = bd.setScale(2, RoundingMode.HALF_UP);
			String s = amtFmt.format(bd);
			// $ + width 15 (including $), right aligned
			return padLeft("$" + s, 15);
		};

		LocalDate ctd = ws.ctd != null ? ws.ctd : LocalDate.now();
		LocalDate runDate = LocalDate.now();
		String monthEnding = ctd.withDayOfMonth(ctd.lengthOfMonth()).format(monthEndFmt).toUpperCase();

		int page = 1;
		java.time.LocalTime runTime = java.time.LocalTime.now();
		reportWriter.newLine();

		java.util.function.Consumer<Integer> writeHeader = (pg) -> {
			try {
				String rptNbr = "P09321-A";
				String company = "ARKANSAS BLUE CROSS AND BLUE SHIELD";
				String left1 = padRight("", 1) + padRight("REPORT #", 8) + "  " + padRight(rptNbr, 8) + padRight("", 29)
						+ padRight(company.substring(0, Math.min(company.length(), 28)), 28) + " "
						+ padRight("SHIELD", 6) + padRight("", 40) + padRight("PAGE", 4) + "  "
						+ padLeft(String.valueOf(pg), 4);
				reportWriter.write(left1);
				reportWriter.newLine();

				String line2 = padRight("", 1) + padRight("RUN DATE: " + runDate.format(runDateFmt), 18)
						+ padRight("", 29) + padRight("MONTHLY CASH RECEIPT CONTROL REPORT", 28) + " "
						+ padRight("REPORT", 6) + padRight("", 32) + padRight("RUN TIME:", 9) + " "
						+ runTime.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
				reportWriter.write(line2);
				reportWriter.newLine();

				String line3 = padRight("", 1) + padRight(ws.getCorpCode(), 33) + padRight("", 14)
						+ padRight("FOR REFUND TYPE: ", 17) + padRight(ws.getRefundType(), 3) + padRight(" - ", 3)
						+ padRight(ws.ctlNarr, 13);
				String line4 = padRight("", 48) + padRight("FOR MONTH ENDING ", 17) + padLeft(monthEnding, 19);
				reportWriter.write(line3);
				reportWriter.newLine();
				reportWriter.write(line4);
				reportWriter.newLine();
				reportWriter.newLine();
				reportWriter.newLine();
				reportWriter.newLine();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		};

		writeHeader.accept(page);

		reportWriter.newLine();

		reportWriter.write(padRight("", 26) + padRight("CASH RECEIPTS ADDED", 19) + padRight("", 11)
				+ padRight("COUNT", 5) + padRight("", 8) + padRight("AMOUNT", 6));
		reportWriter.newLine();

		reportWriter.write(padRight("", 31) + padRight("REQUESTED", 9) + padRight("", 15)
				+ fmtCount.apply(ws.wsCrReqCnt) + "  " + fmtAmt.apply(ws.wsCrReqAmt));
		reportWriter.newLine();

		reportWriter.write(padRight("", 31) + padRight("UNREQUESTED", 11) + padRight("", 13)
				+ fmtCount.apply(ws.wsCrUnreqCnt) + "  " + fmtAmt.apply(ws.wsCrUnreqAmt));
		reportWriter.newLine();
		reportWriter.newLine();

		reportWriter.write(
				padRight("", 26) + padRight("TOTAL ADDITIONS", 15) + padRight("", 2) + padRight(repeat('.', 41), 41)
						+ "   " + fmtCount.apply(ws.wsTotAddCnt) + "  " + fmtAmt.apply(ws.wsTotAddAmt));
		reportWriter.newLine();
		reportWriter.newLine();

		reportWriter.write(padRight("", 26) + padRight("RESOLVED CASH RECEIPTS:", 23));
		reportWriter.newLine();
		reportWriter.write(padRight("", 56) + padRight("COUNT", 5) + padRight("", 10) + padRight("AMOUNT", 6));
		reportWriter.newLine();

		Map<String, int[]> w = Map.of("DEL", new int[] { 8, 16 }, "ACC", new int[] { 17, 7 }, "ACA",
				new int[] { 17, 7 }, "APP", new int[] { 14, 10 }, "FR", new int[] { 12, 12 }, "PR", new int[] { 15, 9 },
				"REM", new int[] { 14, 10 }, "LOG", new int[] { 6, 18 });

		String[] resolvedOrder = { "DEL", "ACC", "APP", "FR", "PR", "REM", "LOG" };

		for (String key : resolvedOrder) {

			String lookup = key;
			if ("ACC".equals(key) && !ws.actCnt.containsKey("ACC"))
				lookup = "ACA";

			long cnt = ws.actCnt.getOrDefault(lookup, 0L);
			BigDecimal amt = ws.actAmt.getOrDefault(lookup, BigDecimal.ZERO);

			String label = RESOLVED_LABELS.get(lookup);

			int labelWidth = w.get(lookup)[0];
			int rightSpaces = w.get(lookup)[1];

			String line = padRight("", 31) + padRight(label, labelWidth) + padRight("", rightSpaces)
					+ fmtCount.apply(cnt) + "  " + fmtAmt.apply(amt);

			if (line.length() < 133) {
				line = padRight(line, 133);
			} else if (line.length() > 133) {
				line = line.substring(0, 133);
			}

			reportWriter.write(line);
			reportWriter.newLine();
		}

		reportWriter.write(
				padRight("", 26) + padRight("TOTAL RESOLVED", 14) + padRight("", 2) + padRight(repeat('.', 42), 42)
						+ "   " + fmtCount.apply(ws.wsTotResolvedCnt) + "  " + fmtAmt.apply(ws.wsTotResolvedAmt));
		reportWriter.newLine();
		reportWriter.newLine();

		if (ws.getRefundType().equals("PER")) {
			reportWriter.newLine();
			reportWriter.newLine();
			reportWriter.newLine();
			if ("03".equals(ws.getCorpNo()) || "04".equals(ws.getCorpNo())) {
				reportWriter.write(padRight("", 26) + padRight("ENDING SUSPENSE & GL BALANCE", 28) + padRight("", 2)
						+ fmtAmt.apply(ws.wsEndSuspBalAmt));
				reportWriter.newLine();

			} else {
				// MONTHLY ACCRUAL
				reportWriter.write(padRight("", 26) + padRight("MONTHLY ACCRUAL", 16));
				reportWriter.newLine();
				reportWriter.newLine();
				// ENDING SUSPENSE BALANCE
				reportWriter.write(padRight("", 26) + padRight("ENDING SUSPENSE BALANCE", 23) + padRight("", 2)
						+ fmtAmt.apply(ws.wsEndSuspBalAmt));
				reportWriter.newLine();

				// ABCBS SHARE @ 78%
				reportWriter.write(padRight("", 26) + padRight("ABCBS SHARE @ 78%", 17) + padRight("", 8)
						+ fmtAmt.apply(ws.wsAbcbsShareAmt));
				reportWriter.newLine();

				// TO MEDIPAK
				reportWriter.write(padRight("", 31) + padRight("TO MEDIPAK", 10) + padRight("", 28)
						+ fmtAmt.apply(ws.wsMedipakAmt));
				reportWriter.newLine();

				// TO OTHER
				reportWriter.write(
						padRight("", 31) + padRight("TO OTHER", 8) + padRight("", 30) + fmtAmt.apply(ws.wsOtherAmt));
				reportWriter.newLine();

				// ENDING GL BALANCE
				reportWriter.write(padRight("", 26) + padRight("ENDING GL BALANCE", 17) + padRight("", 8)
						+ fmtAmt.apply(ws.wsEndGlBalAmt));
				reportWriter.newLine();

				// underline =====================
				reportWriter.write(padRight("", 51) + padRight("===============", 15));
				reportWriter.newLine();
			}

		}

		page++;
		reportWriter.newLine();
		writeHeader.accept(page);

		reportWriter.write(padRight("", 1) + padRight("AGING OF ENDING BALANCE:", 24) + padRight("", 14)
				+ padRight("T O T A L", 9));
		reportWriter.newLine();
		reportWriter.write(padRight("", 35) + padLeft("COUNT", 5) + padLeft("", 7) + padLeft("AMOUNT", 6));
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.write(padRight("", 7) + padRight("0 DAYS - 15 DAYS", 16) + padRight("", 10)
				+ fmtCount.apply(ws.end00_15Cnt) + "  " + fmtAmt.apply(ws.end00_15Amt));
		reportWriter.newLine();
		reportWriter.write(padRight("", 6) + padRight("16 DAYS -  1 MONTH", 18) + padRight("", 9)
				+ fmtCount.apply(ws.end16_1Cnt) + "  " + fmtAmt.apply(ws.end16_1Amt));
		reportWriter.newLine();
		reportWriter.write(padRight("", 6) + padRight("OVER  1 -  2 MONTHS", 19) + padRight("", 8)
				+ fmtCount.apply(ws.end1_2Cnt) + "  " + fmtAmt.apply(ws.end1_2Amt));
		reportWriter.newLine();
		reportWriter.write(padRight("", 6) + padRight("OVER  2 -  3 MONTHS", 19) + padRight("", 8)
				+ fmtCount.apply(ws.end2_3Cnt) + "  " + fmtAmt.apply(ws.end2_3Amt));
		reportWriter.newLine();
		reportWriter.write(padRight("", 6) + padRight("OVER  3 -  4 MONTHS", 19) + padRight("", 8)
				+ fmtCount.apply(ws.end3_4Cnt) + "  " + fmtAmt.apply(ws.end3_4Amt));
		reportWriter.newLine();
		reportWriter.write(padRight("", 6) + padRight("OVER       4 MONTHS", 19) + padRight("", 8)
				+ fmtCount.apply(ws.endOver4Cnt) + "  " + fmtAmt.apply(ws.endOver4Amt));
		reportWriter.newLine();

		// AGING TOTAL
		reportWriter.write(padRight("", 17) + padRight("T O T A L", 9) + padRight("", 7)
				+ fmtCount.apply(ws.agingTotCnt) + "  " + fmtAmt.apply(ws.agingTotAmt));
		reportWriter.newLine();
		reportWriter.write(
				padRight("", 33) + padRight(repeat('=', 7), 7) + padRight("", 2) + padRight(repeat('=', 15), 15));
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.newLine();

		reportWriter.write(padRight("", 1) + padRight("ENDING BALANCE", 14) + padRight("", 15)
				+ padRight("R E Q U E S T E D", 17) + padRight("", 7) + padRight("U N R E Q U E S T E D", 21)
				+ padRight("", 10) + padRight("T O T A L", 9));
		reportWriter.newLine();

		reportWriter.write(padRight("", 3) + padRight("BY CLAIM TYPES", 14) + padRight("", 13) + padLeft("COUNT", 5)
				+ padLeft("", 7) + padLeft("AMOUNT", 6) + padLeft("", 10) + padLeft("COUNT", 5) + padLeft("", 7)
				+ padLeft("AMOUNT", 6) + padLeft("", 10) + padLeft("COUNT", 5) + padLeft("", 7) + padLeft("AMOUNT", 6));
		reportWriter.newLine();
		reportWriter.newLine();

		// claim codes order (matches COBOL order and the earlier code's labels)
		String[] claimCodes = { "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14",
				"15", "16", "17", "18", "19", "20", "21", "22", "23","24", "99" };

		// mapping from bucket key -> printed label (must match claimLabel in COBOL)
		Map<String, String> codeToLabel = Map.ofEntries(Map.entry("01", "BANK"),
				Map.entry("02", "CENTRAL CERTIFICATION"), Map.entry("03", "FEDERAL EMPLY PRGM"),
				Map.entry("04", "REGULAR GCPS CLAIM"), Map.entry("05", "MEDICARE CLAIM"),
				Map.entry("06", "MEDIPAK CLAIM"), Map.entry("07", "RECIPROCITY"), Map.entry("08", "SCHOOL GROUP CLAIM"),
				Map.entry("09", "ACCOUNTS PAYABLE"), Map.entry("10", "PHYSICIANS PAYABLE"), Map.entry("11", "OTHER"),
				Map.entry("12", "IRS BKUP WITHHODING"), Map.entry("13", "INTERPLAN TELEPROCESS"), Map.entry("14", "CS"),
				Map.entry("15", "HCPS"), Map.entry("16", "SEG"), Map.entry("17", "DENTAL"), Map.entry("18", "DRUG"),
				Map.entry("19", "MEDIPAK ADVANTAGE"), Map.entry("20", "MEDIPAK ADVANTAGE PPO"),
				Map.entry("21", "MEDIPAK ADVANTAGE PART D"), Map.entry("22", "PREMIUM REBATES"),
				Map.entry("23", "HMO PARTNERS"),Map.entry("24", "FEP POSTAL CLAIM"), Map.entry("99", "UNDEFINED"));

		for (String code : claimCodes) {
			String label = codeToLabel.getOrDefault(code, code).trim();
			P09321Vars.OpenBucket ob = ws.openBuckets.get(code);
			long reqCnt = ob != null ? ob.reqCnt : ws.resReqCnt.getOrDefault(code, 0L);
			java.math.BigDecimal reqAmt = ob != null ? ob.reqAmt
					: ws.resReqAmt.getOrDefault(code, java.math.BigDecimal.ZERO);
			long unreqCnt = ob != null ? ob.unreqCnt : ws.resUnreqCnt.getOrDefault(code, 0L);
			java.math.BigDecimal unreqAmt = ob != null ? ob.unreqAmt
					: ws.resUnreqAmt.getOrDefault(code, java.math.BigDecimal.ZERO);
			long totCnt = reqCnt + unreqCnt;
			java.math.BigDecimal totAmt = (reqAmt == null ? java.math.BigDecimal.ZERO : reqAmt)
					.add(unreqAmt == null ? java.math.BigDecimal.ZERO : unreqAmt);

			String row = padRight("   " + label, 28).substring(0, 28) + fmtCount.apply(reqCnt) + " "
					+ fmtAmt.apply(reqAmt) + padLeft("", 5) + fmtCount.apply(unreqCnt) + " " + fmtAmt.apply(unreqAmt)
					+ padLeft("", 5) + fmtCount.apply(totCnt) + " " + fmtAmt.apply(totAmt);
			reportWriter.write(row);
			reportWriter.newLine();
		}

		reportWriter.newLine();
		reportWriter.write(padRight("", 9) + padRight("T O T A L", 9) + padRight("", 10)
				+ fmtCount.apply(ws.opnTotReqCnt) + " " + fmtAmt.apply(ws.opnTotReqAmt) + padLeft("", 5)
				+ fmtCount.apply(ws.opnTotUnreqCnt) + " " + fmtAmt.apply(ws.opnTotUnreqAmt) + padLeft("", 5)
				+ fmtCount.apply(ws.opnTotTotCnt) + " " + fmtAmt.apply(ws.opnTotTotAmt));
		reportWriter.newLine();
		reportWriter.write(padRight("", 28) + padRight(repeat('=', 7), 7) + padRight("", 1)
				+ padRight(repeat('=', 15), 15) + padRight("", 5) + padRight(repeat('=', 7), 7) + padRight("", 1)
				+ padRight(repeat('=', 15), 15) + padRight("", 5) + padRight(repeat('=', 7), 7) + padRight("", 1)
				+ padRight(repeat('=', 15), 15));
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.newLine();

		page++;
		writeHeader.accept(page);

		reportWriter.write(padRight("", 1) + padRight("RESOLVED CASH RECEIPTS", 22) + padRight("", 7)
				+ padRight("R E Q U E S T E D", 17) + padRight("", 7) + padRight("U N R E Q U E S T E D", 21)
				+ padRight("", 10) + padRight("T O T A L", 9));
		reportWriter.newLine();

		reportWriter.write(padRight("", 3) + padRight("BY CLAIM TYPES", 14) + padRight("", 13) + padLeft("COUNT", 5)
				+ padLeft("", 7) + padLeft("AMOUNT", 6) + padLeft("", 10) + padLeft("COUNT", 5) + padLeft("", 7)
				+ padLeft("AMOUNT", 6) + padLeft("", 10) + padLeft("COUNT", 5) + padLeft("", 7) + padLeft("AMOUNT", 6));
		reportWriter.newLine();
		reportWriter.newLine();

		for (String code : claimCodes) {
			String label = codeToLabel.getOrDefault(code, code).trim();
			long reqCnt = ws.resReqCnt.getOrDefault(code, 0L);
			java.math.BigDecimal reqAmt = ws.resReqAmt.getOrDefault(code, java.math.BigDecimal.ZERO);
			long unreqCnt = ws.resUnreqCnt.getOrDefault(code, 0L);
			java.math.BigDecimal unreqAmt = ws.resUnreqAmt.getOrDefault(code, java.math.BigDecimal.ZERO);
			long totCnt = ws.resTotCnt.getOrDefault(code, 0L);
			java.math.BigDecimal totAmt = ws.resTotAmt.getOrDefault(code, java.math.BigDecimal.ZERO);

			String row = padRight("   " + label, 28).substring(0, 28) + fmtCount.apply(reqCnt) + " "
					+ fmtAmt.apply(reqAmt) + padLeft("", 5) + fmtCount.apply(unreqCnt) + " " + fmtAmt.apply(unreqAmt)
					+ padLeft("", 5) + fmtCount.apply(totCnt) + " " + fmtAmt.apply(totAmt);
			reportWriter.write(row);
			reportWriter.newLine();
		}

		reportWriter.newLine();
		reportWriter.write(padRight("", 9) + padRight("T O T A L", 9) + padRight("", 10)
				+ fmtCount.apply(ws.resTotReqCnt) + " " + fmtAmt.apply(ws.resTotReqAmt) + padLeft("", 5)
				+ fmtCount.apply(ws.resTotUnreqCnt) + " " + fmtAmt.apply(ws.resTotUnreqAmt) + padLeft("", 5)
				+ fmtCount.apply(ws.resTotTotCnt) + " " + fmtAmt.apply(ws.resTotTotAmt));
		reportWriter.newLine();
		reportWriter.write(padRight("", 28) + padRight(repeat('=', 7), 7) + padRight("", 1)
				+ padRight(repeat('=', 15), 15) + padRight("", 5) + padRight(repeat('=', 7), 7) + padRight("", 1)
				+ padRight(repeat('=', 15), 15) + padRight("", 5) + padRight(repeat('=', 7), 7) + padRight("", 1)
				+ padRight(repeat('=', 15), 15));
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.newLine();
		reportWriter.newLine();

		reportWriter.write(repeat('*', 59) + "    END OF REPORT P09321-A    " + repeat('*', 44));
		reportWriter.newLine();
		reportWriter.flush();
		log.info("Report generation complete for refundType={}", ws.getRefundType());
	}

	private void finalizeControlRecord(P09321Vars ws) {
		log.info("Finalizing control record for refundType={}", ws.getRefundType());
		LocalDate oldTo = ws.ctd;
		LocalDate newBegin = oldTo.plusDays(1);
		LocalDate newEnd = oldTo.plusDays(1).plusMonths(1).minusDays(1);

		try {
			// close existing open control(s)
			controlRepository.closeCurrentControl(ws.getRefundType());
			log.debug("Closed existing control for refundType={}", ws.getRefundType());
		} catch (DataAccessException dae) {
			log.error("DB error while closing current control for refundType={}", ws.getRefundType(), dae);
			throw dae;
		} catch (RuntimeException re) {
			log.error("Unexpected error while closing current control for refundType={}", ws.getRefundType(), re);
			throw re;
		}

		// create new control
		ControlPK newPk = new ControlPK(ws.getRefundType(), "O", newBegin);

		P09Control next = new P09Control();
		next.setControlId(newPk);
		next.setCntrlToDate(newEnd);
		next.setCntrlReceiptCnt((int) ws.wsCrEndBalCnt);
		next.setCntrlReceiptAmt(ws.wsCrEndBalAmt);
		next.setCntrlRefundNarr(ws.ctlNarr);

		try {
			controlRepository.save(next);
			log.info("Inserted new control record for refundType={} begin={} end={} cnt={} amt={}", ws.getRefundType(),
					newBegin, newEnd, ws.wsCrEndBalCnt, ws.wsCrEndBalAmt);
		} catch (DataAccessException dae) {
			log.error("DB error while saving new control for refundType={}", ws.getRefundType(), dae);
			throw dae;
		} catch (RuntimeException re) {
			log.error("Unexpected error while saving new control for refundType={}", ws.getRefundType(), re);
			throw re;
		}
	}

	private static String padRight(String s, int width) {
		if (s == null)
			s = "";
		if (s.length() >= width)
			return s.substring(0, width);
		StringBuilder sb = new StringBuilder(width);
		sb.append(s);
		for (int i = s.length(); i < width; i++)
			sb.append(' ');
		return sb.toString();
	}

	private static String padLeft(String s, int width) {
		if (s == null)
			s = "";
		if (s.length() >= width)
			return s.substring(s.length() - width);
		StringBuilder sb = new StringBuilder(width);
		for (int i = s.length(); i < width; i++)
			sb.append(' ');
		sb.append(s);
		return sb.toString();
	}

	private static String repeat(char c, int n) {
		StringBuilder sb = new StringBuilder(n);
		for (int i = 0; i < n; i++)
			sb.append(c);
		return sb.toString();
	}

	private void initOpenBuckets(P09321Vars ws) {
		// initialize 01..24 and 99
		for (int i = 1; i <= 24; i++) {
			ws.openBuckets.put(String.format("%02d", i), new P09321Vars.OpenBucket());
		}
		ws.openBuckets.put("99", new P09321Vars.OpenBucket());
	}

	// Claim map (COBOL claim names -> bucket code)
	private static final Map<String, String> CLAIM_MAP = Map.ofEntries(Map.entry("BANK", "01"), Map.entry("CC", "02"),
			Map.entry("FEP", "03"), Map.entry("GCPS", "04"), Map.entry("MED", "05"), Map.entry("MPAK", "06"),
			Map.entry("REC", "07"), Map.entry("SG", "08"), Map.entry("AP", "09"), Map.entry("PHYS", "10"),
			Map.entry("OTH", "11"), Map.entry("IRS", "12"), Map.entry("ITS", "13"), Map.entry("CS", "14"),
			Map.entry("HCPS", "15"), Map.entry("SEG", "16"), Map.entry("DENT", "17"), Map.entry("DRUG", "18"),
			Map.entry("MAPD", "19"), Map.entry("MAPO", "20"), Map.entry("PRTD", "21"), Map.entry("MLR ", "22"),
			Map.entry("HMOP", "23"),Map.entry("FEPP", "24"));

	// resolved activity labels (ensures ACC/ACA mismatch handled)
	private static final Map<String, String> RESOLVED_LABELS = Map.of("DEL", "DELETION", "ACC", "ACCEPTED/CANCELED",
			"ACA", "ACCEPTED/CANCELED", "APP", "APPLIED TO A/R", "FR", "FULL REISSUE", "PR", "PARTIAL REISSUE", "REM",
			"CHECK REMAILED", "LOG", "LOGOUT");

}
