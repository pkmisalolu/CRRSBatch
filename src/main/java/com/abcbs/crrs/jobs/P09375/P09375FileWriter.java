package com.abcbs.crrs.jobs.P09375;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.repeat.RepeatStatus;

import com.abcbs.crrs.repository.IP09CashReceiptRepository;

/**
 * Java port of COBOL P09375 (writer side). - Outputs LABEL, LABEL1, LABEL2,
 * LABEL3 wrapped with <mailer>...</mailer> and each record with
 * <label>...</label>. - LABELCNT is a plain counts file. - All business edits &
 * flow preserved, procedures -> methods 1:1. - All fields handled as String
 * (including fillers).
 */

public class P09375FileWriter implements Tasklet{

	private static final Logger log = LogManager.getLogger(P09375FileWriter.class);

	// ===== Injected IO =====
	private final String labelPath;
	private final String label1Path;
	private final String label2Path;
	private final String label3Path;
	private final String labelCntPath;

	private final String controlPath;
	private final FlatFileItemReader<CheckPointRecord> controlReader;
	private final IP09CashReceiptRepository cashRepo;

	// ===== Files =====
	private BufferedWriter label;
	private BufferedWriter label1;
	private BufferedWriter label2;
	private BufferedWriter label3;
	private BufferedWriter labelCnt;

	// ===== WS =====
	private int ckpntFrequency = 0;
	private int ckpntCounter = 0;

	// Internal buffer state
	private List<DailyRemittanceView> buffer = new ArrayList<>();
	private int bufferIndex = 0;
	private String checkpointKey = "";

	private long totalWrote = 0;
	private long totalExceptions = 0;
	private boolean opened = false;

	private int labelRoundRobinCounter = 1;
	private static final String C_LINE_UP_LINE;
	private static final String C_BLANK_LINE;

	// Declare buffers for this cycle of 3
	
	String bufRefund1, bufRefund2, bufRefund3;
	String bufCtrl1 = null, bufCtrl2 = null, bufCtrl3 = null;
	String bufW11 = null, bufW12 = null, bufW13 = null;
	String bufW21 = null, bufW22 = null, bufW23 = null;
	String bufW31 = null, bufW32 = null, bufW33 = null;
	String bufW41 = null, bufW42 = null, bufW43 = null;

	static {
		StringBuilder sb = new StringBuilder(" ");
		sb.append("X".repeat(30)).append(" ".repeat(6)).append("X".repeat(30)).append(" ".repeat(6))
				.append("X".repeat(30)).append(" ".repeat(6));
		C_LINE_UP_LINE = sb.toString();

		C_BLANK_LINE = " " + " ".repeat(108);
	}

	public P09375FileWriter(String labelPath, String label1Path, String label2Path, String label3Path,
			String labelCntPath, String controlPath, FlatFileItemReader<CheckPointRecord> controlReader,
			IP09CashReceiptRepository cashRepo) {
		log.info("Constructor: labelPath={}, labelCntPath={}, controlPath={}", labelPath, labelCntPath, controlPath);
		this.labelPath = labelPath;
		this.label1Path = label1Path;
		this.label2Path = label2Path;
		this.label3Path = label3Path;
		this.labelCntPath = labelCntPath;
		this.controlPath = controlPath;
		this.controlReader = controlReader;
		this.cashRepo = cashRepo;
	}

	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
		log.info("Tasklet execute() START");
		manualOpen(new ExecutionContext());
		log.debug("After open(): buffer.size={}, checkpointKey={}", buffer.size(), checkpointKey);

		while (bufferIndex < buffer.size()) {
			DailyRemittanceView view = buffer.get(bufferIndex++);
			log.debug("Loop: bufferIndex={} view={}", bufferIndex, view);
			processRecord(view);

			ckpntCounter++;
			if (ckpntFrequency > 0 && ckpntCounter >= ckpntFrequency) {
				log.debug("Checkpoint threshold reached: ckpntCounter={}, ckpntFrequency={}", ckpntCounter,
						ckpntFrequency);
				log.info("buffer size {}", buffer.size());
				checkpointKey = buildCheckpointKeyFromView(view);
				fetchNextDBChunk();
				ckpntCounter = 0;
				bufferIndex = 0;
				log.debug("After fetchNextDBChunk(): buffer.size={}, new checkpointKey={}", buffer.size(),
						checkpointKey);
			}
		}

		manualClose();
		log.info("Tasklet execute() END, totalWrote={}, totalExceptions={}", totalWrote, totalExceptions);
		return RepeatStatus.FINISHED;
	}

	public void manualOpen(ExecutionContext ctx) throws ItemStreamException {
		try {
			log.info("Opening P09375FileWriter, labelPath={}, label1={}, label2={}, label3={}, labelCnt={}", labelPath,
					label1Path, label2Path, label3Path, labelCntPath);
			proc000100Initialize();
			log.info("Checkpoint frequency set to {}", ckpntFrequency);

			// Initial DB fetch
			fetchNextDBChunk();
			log.debug("Initial fetchNextDBChunk(): buffer.size={}, checkpointKey={}", buffer.size(), checkpointKey);

			opened = true;
		} catch (Exception e) {
			log.error("Initialization error", e);
			throw new ItemStreamException("P09375 failed", e);
		}
	}

	public void manualClose() throws ItemStreamException {
		log.info("Closing P09375FileWriter, labelRoundRobinCounter={}", labelRoundRobinCounter);

		try {
			if (labelRoundRobinCounter != 1) {
				writeCombinedLabelBlock();
			}
			proc000250UpdateRemailInd();
			log.debug("Writing label count file");
			proc000300Finalization();
		} catch (Exception e) {
			log.error("Finalization error", e);
			throw new ItemStreamException("P09375 close failed", e);
		} finally {
			safeClose(label);
			safeClose(label1);
			safeClose(label2);
			safeClose(label3);
			safeClose(labelCnt);
			opened = false;
			log.info("Closed P09375FileWriter successfully");
		}
	}

	private void proc000100Initialize() throws Exception {
		log.info("000100-INITIALIZE: opening report files and reading control frequency.");

		// open files
		label = newBuffered(labelPath);
		label1 = newBuffered(label1Path);
		label2 = newBuffered(label2Path);
		label3 = newBuffered(label3Path);
		labelCnt = newBuffered(labelCntPath);
		log.debug("Writers initialized");

		// Write the header in label file: pattern block
		for (int rep = 0; rep < 3; rep++) {
			for (int i = 0; i < 5; i++) {
				label.write(C_LINE_UP_LINE);
				label.newLine();
			}
			if( rep != 2) {
			label.write(C_BLANK_LINE);
			label.newLine();
			}
		}
		log.debug("Header block written in label file");
		// Read checkpoint frequency
		if (controlPath == null || controlPath.isBlank()) {
			log.warn("No control file; checkpoint disabled.");
			ckpntFrequency = 0;
		} else {
			controlReader.open(new ExecutionContext());
			CheckPointRecord rec = controlReader.read();
			if (rec == null) {
				proc888999InvalidFile("CHECK-POINT file empty");
			} else {
				String freq = rec.getCheckPointFrequency() == null ? "" : rec.getCheckPointFrequency().trim();
				if (freq.chars().allMatch(Character::isDigit)) {
					ckpntFrequency = freq.isEmpty() ? 0 : Integer.parseInt(freq);
				} else {
					proc888999InvalidFile("Non-numeric CONTROL-FREQUENCY: '" + freq + "'");
				}
			}
			controlReader.close();
		}
		log.info("Checkpoint frequency: {}", ckpntFrequency);
	}

	private void processRecord(DailyRemittanceView view) throws IOException {
		log.debug("processRecord START for view: {}", view);
		String w1 = isBlank(view.getCrRemAddressee()) ? view.getCrRemittorName() : view.getCrRemAddressee();
		log.debug("Computed w1 = '{}'", w1);
		String  bufCtr="" ,w11 = "";
		String w2, w3, city, state, zip5, zip4;
		if (isBlank(view.getCrRemAddress1())) {
			// use CHK address
			w2 = view.getCrChkAddress1();
			w3 = view.getCrChkAddress2();
			city = view.getCrChkCity();
			state = view.getCrChkState();
			zip5 = view.getCrChkZip5();
			zip4 = view.getCrChkZip4();
			log.debug("Using CHK address: {}, {}, {}, {}", w2, w3, city, state);
		} else {
			// use REM address
			w2 = view.getCrRemAddress1();
			w3 = view.getCrRemAddress2();
			city = view.getCrRemCity();
			state = view.getCrRemState();
			zip5 = view.getCrRemZip5();
			zip4 = view.getCrRemZip4();
			log.debug("Using REM address: {}, {}, {}, {}", w2, w3, city, state);
		}

		// Build W4 label city/state/zip
		String w4 = buildFixedCityStateZip(city, state, zip5, zip4);

		String wRefundType = view.getCrRefundType();
		String wControlDate = view.getCrCntrlDate().format(DateTimeFormatter.ofPattern("MM/dd/yy"));
		String wControlNbr = view.getCrCntrlNbr();

		if (isBlank(w2)) {
			w2 = w3;
			w3 = w4;
			w4 = "";
		} else if (isBlank(w3)) {
			w3 = w4;
			w4 = "";
		}

		BufferedWriter targetWriter;

		switch (labelRoundRobinCounter) {
		case 1:
			targetWriter = label1;
			w11 = w1;
			bufRefund1 = wRefundType;
			bufCtr= padRight(wControlDate ,18)+ padRight(wControlNbr,18);
			bufCtrl1 = bufCtr;
			bufW11 = w1;
			bufW21 = w2;
			bufW31 = w3;
			bufW41 = w4;
			log.debug("Round 1 assignment -> bufRefund1={}, bufCtrl1={}, bufW11={}, bufW21={}, bufW31={}, bufW41={}",
					bufRefund1, bufCtrl1, bufW11, bufW21, bufW31, bufW41);
			break;
		case 2:
			targetWriter = label2;
			w11 = padRight(wControlDate,15) + padRight(wControlNbr,15);
			bufCtr=w11;
			bufRefund2 = wRefundType;
			bufCtrl2 =w11;
			bufW12 = w1;
			bufW22 = w2;
			bufW32 = w3;
			bufW42 = w4;
			log.debug("Round 2 assignment -> bufRefund2={}, bufCtrl2={}, bufW12={}, bufW22={}, bufW32={}, bufW42={}",
					bufRefund2, bufCtrl2, bufW12, bufW22, bufW32, bufW42);
			break;
		case 3:
			targetWriter = label3;
			w11 = padRight(wControlDate,15) + padRight(wControlNbr,15);
			bufCtr=w11;
			bufRefund3 = wRefundType;
			bufCtrl3 = w11;
			bufW13 = w1;
			bufW23 = w2;
			bufW33 = w3;
			bufW43 = w4;
			writeCombinedLabelBlock();
			bufRefund1 = bufRefund2 = bufRefund3 = "";
			bufCtrl1 = bufCtrl2 = bufCtrl3 = "";
			bufW11 = bufW12 = bufW13 = "";
			bufW21 = bufW22 = bufW23 = "";
			bufW31 = bufW32 = bufW33 = "";
			bufW41 = bufW42 = bufW43 = "";
			log.debug("Buffers cleared after combined block write");
			break;
		default:
			targetWriter = label1;
			log.warn("Unexpected labelRoundRobinCounter {}, defaulting to label1", labelRoundRobinCounter);
		}

		// WRITE-MAILER
		if (!isBlank(wRefundType)) {
			writeMailer(targetWriter, wRefundType, bufCtr, w11);
		}

		// WRITE-LABEL
		if (!isBlank(w1)) {
			writeLabel(targetWriter, w1, w2, w3, w4);
		}

		labelRoundRobinCounter++;
		if (labelRoundRobinCounter > 3)
			labelRoundRobinCounter = 1;
	}

	private void writeMailer(BufferedWriter label1, String refundType, String cdatecnbr, String w1) throws IOException {

		log.debug("writeMailer called: refundType={}, cdatecnbr={}, w1={}", refundType, cdatecnbr, w1);
		writeFixedLine(label1, "");
		writeFixedLineTags(label1, "<mailer>");
		writeFixedLine(label1, refundType);
		writeFixedLine(label1, cdatecnbr);
		writeFixedLine(label1, w1);
		writeFixedLine(label1, "");
		writeFixedLineTags(label1, "</mailer>");
		totalWrote++;
	}

	private void writeLabel(BufferedWriter label1, String w1, String w2, String w3, String w4) throws IOException {

		log.debug("writeLabel called: w1={}, w2={}, w3={}, w4={}", w1, w2, w3, w4);
		writeFixedLine(label1, "");
		writeFixedLineTags(label1, "<label>");
		writeFixedLine(label1, w1);
		writeFixedLine(label1, w2);
		writeFixedLine(label1, w3);
		writeFixedLine(label1, w4);
		writeFixedLine(label1, "");
		writeFixedLineTags(label1, "</label>");
		totalWrote++;
	}

	private void writeCombinedLabelBlock() throws IOException {
		log.info("Writing combined label block for buffers: refund1={}, refund2={}, refund3={}", bufRefund1, bufRefund2,
				bufRefund3);
		try {
			label.write(buildLabelRecordLine("", "", ""));
			label.newLine();
			label.write(buildLabelRecordLine("", "", ""));
			label.newLine();
			label.write(buildLabelRecordLine(bufRefund1, bufRefund2, bufRefund3));
			label.newLine();
			label.write(buildLabelRecordLine(bufCtrl1, bufCtrl2, bufCtrl3));
			label.newLine();
			label.write(buildLabelRecordLine(bufW11, bufW12, bufW13));
			label.newLine();
			label.write(buildLabelRecordLine("", "", ""));
			label.newLine();
			label.write(buildLabelRecordLine("", "", ""));
			label.newLine();
			label.write(buildLabelRecordLine("", "", ""));
			label.newLine();
			label.write(buildLabelRecordLine(bufW11, bufW12, bufW13));
			label.newLine();
			label.write(buildLabelRecordLine(bufW21, bufW22, bufW23));
			label.newLine();
			label.write(buildLabelRecordLine(bufW31, bufW32, bufW33));
			label.newLine();
			label.write(buildLabelRecordLine(bufW41, bufW42, bufW43));
			label.newLine();
			label.write(buildLabelRecordLine("", "", ""));
			label.newLine();
		} catch (IOException e) {
			log.error("Error writing combined label block", e);
			throw e;
		}
	}

	private void writeFixedLine(BufferedWriter writer, String content) throws IOException {
		if (content == null)
			content = "";
		if (content.length() > 36) {
			content = content.substring(0, 36);
		} else {
			content = String.format("%-36s", content);
		}
		writer.write(" " + content);
		writer.newLine();
	}
	private void writeFixedLineTags(BufferedWriter writer, String content) throws IOException {
		if (content == null)
			content = "";
		if (content.length() > 37) {
			content = content.substring(0, 37);
		} else {
			content = String.format("%-37s", content);
		}
		writer.write(content);
		writer.newLine();
	}
	private String buildFixedCityStateZip(String city, String state, String zip5, String zip4) {
	    return padRight(city, 15) + "  " +
	           padRight(state, 2) + "  " +
	           padRight(zip5, 5) + "-" +
	           padRight(zip4, 4);
	}

	private String padRight(String value, int length) {
	    if (value == null) value = "";
	    return String.format("%-" + length + "s", value);
	}

	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private void proc000250UpdateRemailInd() {
		try {
			int updated = cashRepo.clearDailyRemittanceFlag();
			if (updated > 0) {
				log.info("Remail indicator reset for {} records", updated);
			} else {
				log.warn("Remail indicator update attempted, but no records were affected.");
			}
		} catch (Exception e) {
			totalExceptions++;
			log.error("Failed to update remail ind, err={}", e.getMessage(), e);
		}
	}

	private void proc888999InvalidFile(String msg) {
		String full = "<<<<ERROR>>>> " + msg;
		log.error(full);
		throw new IllegalStateException(full);
	}

	private void fetchNextDBChunk() {
		log.debug("fetchNextDBChunk START with checkpointKey = '{}'", checkpointKey);
		buffer = cashRepo.findDailyRemittances(checkpointKey);
		bufferIndex = 0;
		log.debug("fetchNextDBChunk: fetched {} records", buffer.size());
	}

	private String buildCheckpointKeyFromView(DailyRemittanceView v) {
		return v.getCrCntrlDate().toString().replace("-", "") + v.getCrCntrlNbr() + v.getCrRefundType();
	}

	private void proc000300Finalization() throws IOException {
		if (!opened) {
			log.warn("Finalization called but writer was not opened");
			return;
		}
		try {
			String countStr = String.format("%05d", totalWrote);
			String labelCntRec = countStr + "     ";

			labelCnt.write(labelCntRec);
			labelCnt.newLine();
			log.debug("Written label count record: {}", labelCntRec);

			// Final flush
			if (label1 != null)
				label1.flush();
			if (label2 != null)
				label2.flush();
			if (label3 != null)
				label3.flush();
			if (labelCnt != null)
				labelCnt.flush();

			log.info("P09375 finalized: wrote={}, exceptions={}", totalWrote, totalExceptions);
		} catch (IOException e) {
			log.error("IOException in finalization", e);
			throw e;
		}
	}

	private static BufferedWriter newBuffered(String path) throws IOException {
		File f = new File(path);
		File parent = f.getParentFile();
		if (parent != null)
			parent.mkdirs();
		return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, false), StandardCharsets.US_ASCII));
	}

	private static void safeClose(Closeable c) {
		try {
			if (c != null)
				c.close();
		} catch (Exception ignore) {
		}
	}

	private String buildLabelRecordLine(String l1, String l2, String l3) {
		if (l1 == null)
			l1 = "";
		if (l2 == null)
			l2 = "";
		if (l3 == null)
			l3 = "";

		// Ensure exactly 36 characters for each column (pad or truncate)
		l1 = l1.length() > 36 ? l1.substring(0, 36) : String.format("%-36s", l1);
		l2 = l2.length() > 36 ? l2.substring(0, 36) : String.format("%-36s", l2);
		l3 = l3.length() > 36 ? l3.substring(0, 36) : String.format("%-36s", l3);

		return " " + l1 + l2 + l3;
	}

}
