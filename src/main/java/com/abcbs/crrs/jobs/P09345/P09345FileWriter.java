package com.abcbs.crrs.jobs.P09345;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import com.abcbs.crrs.jobs.P09376.CheckPointRecord;
import com.abcbs.crrs.repository.IP09CashReceiptRepository;
import com.abcbs.crrs.repository.IP09ControlRepository;

/**
 * Java port of COBOL program P09345.
 *
 * REPORT: CARRYOVER REPORT OF OPEN CASH RECEIPTS (P09345-A) - Driven by
 * refund-type control file - DB2 cursor rewritten as JPQL (findP09345Carryover)
 * - Checkpoint control file used for frequency only
 */
public class P09345FileWriter implements Tasklet {

	private static final Logger log = LoggerFactory.getLogger(P09345FileWriter.class);

	private static final int RECORD_LEN = 133;

	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd/yy");
	private static final DateTimeFormatter RUN_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

	private static final java.text.NumberFormat MONEY_FMT = new java.text.DecimalFormat("$#,##0.00");



	/* ===== Injected ===== */
	private final String Corp_File;
	private final String Refund_Type_Card;
	private final String Checkpoint_Card;
	private final FlatFileItemReader<CheckPointRecord> controlReader;
	private final IP09CashReceiptRepository cashRepo;
	private final String OutputFile;

	private final IP09ControlRepository controlRepo;

	/* ===== IO ===== */
	private BufferedWriter out;

	/* ===== Checkpoint control ===== */
	private int checkpointFrequency = 0;
	private int checkpointCounter = 0;

	// COBOL lexicographic cursor keys
	private LocalDate lastDate;
	private String lastNbr;
	private String lastType;

	/* ===== Paging ===== */
	private int pageNo = 1;
	private int lineNo = 0;

	/* ===== Totals ===== */
	private long refundReceiptCnt;
	private long refundCheckCnt;
	private BigDecimal refundCtrlAmt;
	private BigDecimal refundBalAmt;

	private long grandReceiptCnt;
	private long grandCheckCnt;
	private BigDecimal grandCtrlAmt = BigDecimal.ZERO;
	private BigDecimal grandBalAmt = BigDecimal.ZERO;

	public P09345FileWriter(String Corp_File, String Refund_Type_Card, String Checkpoint_Card,
			FlatFileItemReader<CheckPointRecord> controlReader, IP09CashReceiptRepository cashRepo,
			IP09ControlRepository controlRepo, String OutputFile) {

		this.Corp_File = Corp_File;
		this.Refund_Type_Card = Refund_Type_Card;
		this.Checkpoint_Card = Checkpoint_Card;
		this.controlReader = controlReader;
		this.cashRepo = cashRepo;
		this.controlRepo = controlRepo;
		this.OutputFile = OutputFile;
	}

	/* ========================================================= */

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

		readCheckpointFrequency();

		out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(OutputFile), StandardCharsets.US_ASCII));

		try (BufferedReader refundReader = new BufferedReader(
				new InputStreamReader(new FileInputStream(Refund_Type_Card), StandardCharsets.US_ASCII))) {

			String refundCard;

			while ((refundCard = refundReader.readLine()) != null) {

				String refundType = refundCard.substring(10, 13).trim();

				List<P09ControlView> refundTypeValues = fetchNextDbChunk(refundType);

				List<String> refundTypesList = refundTypeValues.stream().map(P09ControlView::getRefundType).distinct()
						.toList();

				if (refundTypesList.contains("PER")) {

					refundTypeMethodWay(refundType);

				} else {
					for (String refundValue : refundTypesList) {
						refundTypeMethodWay(refundValue);
					}
				}
			}

		}

		printGrandTotals();
		printEndOfReport();

		out.flush();
		out.close();

		return RepeatStatus.FINISHED;
	}

	private void refundTypeMethodWay(String refundType) throws IOException {

		if (pageNo > 1) {
			pageNo++;
		}

		resetRefundTotals();
		resetCheckpointKeys();

		printPageHeader(refundType);
		printColumnHeaders();

		Resource corpResource = new FileSystemResource(Corp_File);
		CorpMeta corpMeta = readCorp(corpResource);

		List<CarryoverView> rows = cashRepo.fetchCashReceiptCursor(corpMeta.corpNo, refundType, LocalDate.now());

		if (rows.isEmpty()) {
			writeCentered("*****   N O     O P E N        C A S H     R E C E I P T S     *****");
		} else {
			for (CarryoverView v : rows) {
				writeDetail(v);
				accumulateRefundTotals(v);
				advanceCheckpoint(v);
			}
		}

		printRefundTotals(refundType);

		rollupGrandTotals();

		writeLine("");
		writeLine("");
	}

	/* ================= CHECKPOINT ================= */

	private void readCheckpointFrequency() throws Exception {
		if (Checkpoint_Card == null || Checkpoint_Card.isBlank()) {
			checkpointFrequency = 0;
			return;
		}

		controlReader.open(new ExecutionContext());
		CheckPointRecord rec = controlReader.read();
		controlReader.close();

		if (rec == null || rec.getCheckPointFrequency() == null)
			throw new ItemStreamException("CHECK-POINT FILE EMPTY");

		checkpointFrequency = Integer.parseInt(rec.getCheckPointFrequency().trim());

		log.info("Checkpoint frequency={}", checkpointFrequency);
	}

	private void resetCheckpointKeys() {
		lastDate = LocalDate.of(1900, 1, 1);
		lastNbr = "0000";
		lastType = "";
		checkpointCounter = 0;
	}

	private void advanceCheckpoint(CarryoverView v) {
		lastDate = v.getCntrlDate();
		lastNbr = v.getCheckNbr();
		lastType = v.getRefundType();
	}

	/* ================= TOTALS ================= */

	private void resetRefundTotals() {
		refundReceiptCnt = 0;
		refundCheckCnt = 0;
		refundCtrlAmt = BigDecimal.ZERO;
		refundBalAmt = BigDecimal.ZERO;
	}

	private void accumulateRefundTotals(CarryoverView v) {
		refundReceiptCnt++;
		refundCheckCnt++;
		if (v.getCheckAmt() != null)
			refundCtrlAmt = refundCtrlAmt.add(v.getCheckAmt());
		if (v.getReceiptBal() != null)
			refundBalAmt = refundBalAmt.add(v.getReceiptBal());
	}

	private void rollupGrandTotals() {
		grandReceiptCnt += refundReceiptCnt;
		grandCheckCnt += refundCheckCnt;
		grandCtrlAmt = grandCtrlAmt.add(refundCtrlAmt);
		grandBalAmt = grandBalAmt.add(refundBalAmt);
	}

	/* ================= REPORT OUTPUT ================= */

	private void printPageHeader(String refundType) throws IOException {

		writeLine(pad("REPORT #  P09345-A", 47) + pad("ARKANSAS BLUE CROSS AND BLUE SHIELD", 75) + pad("PAGE", 9)
				+ String.valueOf(pageNo));
		writeLine(pad("RUN DATE: " + LocalDate.now(), 46) + pad("CARRYOVER REPORT OF OPEN CASH RECEIPTS", 68)
				+ pad("RUN TIME: " + LocalTime.now().format(RUN_TIME_FMT), 33));

		if (refundType != null && !refundType.isBlank()) {

			writeLine(pad("CORP " + "04 - ABCBS MEDIPAK ADVANTAGE", 44) + pad("ENDING BALANCE AS OF", 27)
					+ LocalDate.now());
			writeLine(pad("", 47) + "FOR REFUND TYPE: " + refundType + " - " + activityLabel(refundType));
		}
	}

	private void printColumnHeaders() throws IOException {
		writeLine(
				"  C/R CONTROL      CONTROL      CASH RECEIPT   CHECK    CHECK      CHECK                     PATIENT NAME               REAS  LETTER");
		writeLine(
				"   DATE   NBR      AMOUNT          BALANCE     NUMBER   DATE       AMOUNT        REMITTOR  LAST     FIRST   MEMBER ID   CODE   DATE");
		writeLine(
				"-------- ---- --------------- --------------- -------- -------- --------------- -------- -------- ------- ------------ ---- --------");
		writeLine("");
	}

	private void writeDetail(CarryoverView v) throws IOException {

		writeLine(pad(v.getCntrlDate().format(DATE_FMT), 8) + " " + pad(v.getControlNbr().toString(), 4) + " "
				+ padLeft(formatCurrency(v.getControlAmt()), 15) + " " + padLeft(formatCurrency(v.getReceiptBal()), 15)
				+ " " + padLeft(v.getCheckNbr(), 8) + " " + pad(v.getCntrlDate().format(DATE_FMT), 8) + " "
				+ padLeft(formatCurrency(v.getCheckAmt()), 15) + " " + pad(v.getRemittor(), 8) + " "
				+ pad(v.getPatientLast(), 8) + " " + pad(v.getPatientFirst(), 7) + " " + pad(v.getPatientId(), 12) + " "
				+ pad(v.getReasonCode(), 4) + " "
				+ pad(v.getLetterDate() == null ? "" : v.getLetterDate().format(DATE_FMT), 8));

	}

	private void printRefundTotals(String refundType) throws IOException {
		writeLine("");
		writeLine("");
		writeLine(pad("", 73) + pad("CONTROLLED", 15) + pad("CASH RECEIPT", 15));

		writeLine(pad("", 62) + pad("NUMBER", 13) + pad("AMOUNT", 15) + pad("BALANCE AMOUNT", 15));
		String line = pad("", 8) + "TOTAL  CASH RECEIPT RECORDS FOR REFUND TYPE - " + refundType + pad("", 9)
				+ padLeft(String.valueOf(refundReceiptCnt), 2) + padLeft(formatCurrency(refundCtrlAmt), 15)
				+ padLeft(formatCurrency(refundBalAmt), 15);
		writeLine(pad(line, RECORD_LEN));

		writeLine("");

		String line2 = pad("", 8) + "TOTAL  CHECK        RECORDS FOR REFUND TYPE - " + refundType + pad("", 9)
				+ padLeft(String.valueOf(refundReceiptCnt), 2);
		writeLine(pad(line2, RECORD_LEN));
	}

	private void printGrandTotals() throws IOException {

		pageNo++;
		printPageHeader("");

		writeLine("");
		writeLine(pad("", 14) + pad("", 73) + pad("CONTROLLED", 15) + pad("CASH RECEIPT", 15));

		writeLine(pad("", 14) + pad("", 62) + pad("NUMBER", 13) + pad("AMOUNT", 11) + pad("BALANCE AMOUNT", 11));

		String line = pad("", 8) + "G R A N D    T O T A L    OF ALL CASH RECEIPT RECORDS IS" + pad("", 16)
				+ padLeft(String.valueOf(grandReceiptCnt), 2) + padLeft(formatCurrency(grandCtrlAmt), 15)
				+ padLeft(formatCurrency(grandBalAmt), 15);
		writeLine(pad(line, RECORD_LEN));
		writeLine("");
		String line2 = pad("", 8) + "G R A N D    T O T A L    OF ALL    CHECK RECORDS     IS" + pad("", 16)
				+ padLeft(String.valueOf(grandReceiptCnt), 2);
		writeLine(pad(line2, RECORD_LEN));
	}

	private void printEndOfReport() throws IOException {
		writeLine("");
		writeLine("");
		writeLine("");
		writeLine("");
		writeLine("");
		writeLine("");
		writeLine("");
		writeLine("");
		writeLine("");
		writeLine("");
		writeLine(
				"****************************************************  END OF P09345-A REPORT  ******************************************************");
	}

	/* ================= UTIL ================= */

	private void writeLine(String s) throws IOException {
		out.write(pad(s, RECORD_LEN));
		out.newLine();
		lineNo++;
	}

	private void writeCentered(String s) throws IOException {
		int pad = (RECORD_LEN - s.length()) / 2;
		writeLine(" ".repeat(Math.max(0, pad)) + s);
	}

	private static String pad(String s, int len) {
		if (s == null)
			s = "";
		return s.length() >= len ? s.substring(0, len) : String.format("%-" + len + "s", s);
	}

	private static String padAmt(BigDecimal b, int len) {
		if (b == null)
			return pad("", len);
		return String.format("%" + len + "s", b.setScale(2).toPlainString());
	}

	private String formatCurrency(BigDecimal amount) {
		if (amount == null) {
			return "";
		}
		return MONEY_FMT.format(amount);
	}

	private static String padLeft(String s, int len) {
		if (s == null)
			s = "";
		return s.length() >= len ? s.substring(0, len) : String.format("%" + len + "s", s);
	}

	private record CorpMeta(String corpNo, String corpName) {
	}

	private static CorpMeta readCorp(Resource r) throws IOException {
		// Assume first line: first 2 chars = corp no; rest = name (lenient).
		try (var br = new BufferedReader(new InputStreamReader(r.getInputStream(), StandardCharsets.UTF_8))) {
			String line = Optional.ofNullable(br.readLine()).orElse("").trim();
			String no = line.length() >= 2 ? line.substring(10, 12).trim() : line;
			String name = line.length() > 2 ? line.substring(2).trim() : "";
			return new CorpMeta(no, name);
		}
	}

	private String activityLabel(String code) {
		return switch (code) {
		// Refund / receipt types
		case "PER" -> "PERSONAL";
		case "OFF" -> "OFFSET";
		case "UND" -> "UNDELIVERABLE";

		default -> code == null || code.isBlank() ? " " : code;
		};
	}

	private List<P09ControlView> fetchNextDbChunk(String refundType) {

		List<P09ControlView> rows = Optional.ofNullable(controlRepo.findCarryoverByRefundType(refundType))
				.orElseGet(List::of);

		if (log.isDebugEnabled()) {
			log.debug("Fetched {} control rows for refundType={}", rows.size(), refundType);
		}

		return rows;
	}

}
