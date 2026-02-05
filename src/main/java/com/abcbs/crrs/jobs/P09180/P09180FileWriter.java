package com.abcbs.crrs.jobs.P09180;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemWriter;

import com.abcbs.crrs.jobs.P09320.P09320FileWriter;

public class P09180FileWriter implements ItemWriter<CCMInputRecord>, ItemStream {
	private static final Logger log = LogManager.getLogger(P09180FileWriter.class);
	private BufferedWriter writer;

	// Totals (H- fields in COBOL)
	private String formCount;
	private String totalControlledAmount;

	private final String totalsFile;
	private final String outputFile;

	public P09180FileWriter(String totalsFile, String outputFile) {
		this.totalsFile = totalsFile;
		this.outputFile = outputFile;
	}

	// =========================================================
	// OPEN (COBOL: OPEN INPUT CCM-TOTALS / OUTPUT CCM-XML-FILE)
	// =========================================================
	@Override
	public void open(ExecutionContext ctx) throws ItemStreamException {
		try {
			readTotals(); // COBOL 300000-PROCESS-TOTAL
			log.info("Opening report file for write: {}", outputFile);
			writer = new BufferedWriter(new FileWriter(outputFile));
		} catch (Exception e) {
			log.error("Failed to open report file '{}'", outputFile, e);
			throw new ItemStreamException("Failed to open P09180 writer", e);
		}
	}

	// =========================================================
	// WRITE (COBOL: WRITE XML-REC FROM XML-OUTPUT)
	// =========================================================
	@Override
	public void write(Chunk<? extends CCMInputRecord> chunk) throws Exception {
		for (CCMInputRecord rec : chunk) {
			log.debug("Processing input record");
			String xml = buildXml(rec);
			writer.write(xml); // fixed-length
			writer.newLine();
		}
	}

	// =========================================================
	// READ TOTALS (COBOL: READ CCM-TOTALS)
	// =========================================================
	private void readTotals() throws IOException {
		log.debug("Reading CCM-TOTALS file");
		try (BufferedReader br = new BufferedReader(new FileReader(totalsFile))) {
			String line = br.readLine();
			if (line != null && line.length() >= 20) {
				formCount = line.substring(0, 5);
				totalControlledAmount = line.substring(5, 20);
			}
		}
	}

	// =========================================================
	// XML GENERATION (COBOL XML GENERATE)
	// =========================================================
	private String buildXml(CCMInputRecord x) {
		log.debug("Preparing XML GENERATION file");
		StringBuilder sb = new StringBuilder();

		sb.append("<_XML-INPUT>");

		tag(sb, "CCM-INFO", spaceIfEmpty(x.getCInfo()));
		tag(sb, "MEMBER-ID", spaceIfEmpty(x.getCTilde2()) + spaceIfEmpty(x.getCMemberId()));
		tag(sb, "CCM-TYPE", spaceIfEmpty(x.getCTilde3()) + spaceIfEmpty(x.getCCcmType()));
		tag(sb, "LOB", spaceIfEmpty(x.getCTilde4() + left(x.getCBarcodeLob(), 4) + x.getCTilde5()
				+ x.getCBarcodeLocNbr() + x.getCAsterisk2()));

		tag(sb, "BUS-LOCATION", spaceIfEmpty(x.getCBusLocation()));
		tag(sb, "LOCATION-NBR", spaceIfEmpty(x.getCLocationNbr()));
		tag(sb, "OTIS-NBR", spaceIfEmpty(x.getCOtisNbr()));
		tag(sb, "SECTION-CODE", spaceIfEmpty(x.getCSectionCode()));
		tag(sb, "REFUND-TYPE", spaceIfEmpty(x.getCRefundType()));
		tag(sb, "CONTROL-DATE", spaceIfEmpty(x.getCControlDate()));
		tag(sb, "CONTROL-NBR", spaceIfEmpty(x.getCControlNbr()));
		tag(sb, "STATUS-CODE", spaceIfEmpty(x.getCStatus()));
		tag(sb, "STATUS-DATE", spaceIfEmpty(x.getCStatusDate()));
		tag(sb, "EOB-IND", spaceIfEmpty(x.getCEobInd()));
		tag(sb, "RECEIPT-TYPE", spaceIfEmpty(x.getCReceiptType()));
		tag(sb, "REMITTOR-NAME", spaceIfEmpty(x.getCRemittorName()));
		tag(sb, "REMITTOR-TITLE", spaceIfEmpty(x.getCRemittorTitle()));
		tag(sb, "REMITTOR-TYPE", spaceIfEmpty(x.getCRemittorType()));
		tag(sb, "CLAIM-TYPE", spaceIfEmpty(x.getCClaimType()));
		tag(sb, "OPL-IND", spaceIfEmpty(x.getCOplInd()));
		tag(sb, "LETTER-DATE", spaceIfEmpty(x.getCLetterDate()));
		tag(sb, "REASON-CODE", spaceIfEmpty(x.getCReasonCode()));
		tag(sb, "OTHER-CORR", spaceIfEmpty(x.getCOtherCorr()));
		tag(sb, "COMMENTS", spaceIfEmpty(x.getCComments()));
		tag(sb, "PATIENT-FNAME", spaceIfEmpty(x.getCPatientFname()));
		tag(sb, "PATIENT-LNAME", spaceIfEmpty(x.getCPatientLname()));
		tag(sb, "ADDR1", spaceIfEmpty(x.getCAddr1()));
		tag(sb, "ADDR2", spaceIfEmpty(x.getCAddr2()));
		tag(sb, "CITY", spaceIfEmpty(x.getCCity()));
		tag(sb, "STATE", spaceIfEmpty(x.getCState()));
		tag(sb, "ZIP", spaceIfEmpty(x.getCZip()));
		tag(sb, "CHECK-DATE", spaceIfEmpty(x.getCCheckDate()));
		tag(sb, "CHECK-NBR", spaceIfEmpty(x.getCCheckNbr()));
		tag(sb, "CHECK-AMOUNT", spaceIfEmpty(x.getCCheckAmount()));
		tag(sb, "CONTROLLED-AMOUNT", right(spaceIfEmpty(x.getCControlledAmount()), 15));
		tag(sb, "LOCATION-CODE", spaceIfEmpty(x.getCLocationCode()));

		// Totals (from CCM-TOTALS file)
		tag(sb, "FORM-COUNT", formCount);
		tag(sb, "TOTAL-CONTROLLED-AMOUNT", totalControlledAmount);

		sb.append("</_XML-INPUT>");

		return sb.toString();
	}

	private String spaceIfEmpty(String v) {
		if (v == null || v.length() == 0) {
			return " ";
		}
		return v;
	}

	private static String right(String s, int w) {
		if (s == null)
			s = "";
		return s.length() >= w ? s.substring(s.length() - w) : " ".repeat(w - s.length()) + s;
	}

	private static String left(String s, int w) {
		if (s == null)
			s = "";
		return s.length() >= w ? s.substring(s.length() - w) : s + " ".repeat(w - s.length());
	}

	private void tag(StringBuilder sb, String tag, String value) {
		sb.append("<").append(tag).append(">");
		if (value != null)
			sb.append(value); // NO trim
		sb.append("</").append(tag).append(">");
	}

	// =========================================================
	// CLOSE (COBOL CLOSE FILES)
	// =========================================================
	@Override
	public void close() throws ItemStreamException {
		try {
			if (writer != null)
				writer.close();
			log.info("Closed report file: {}", outputFile);
		} catch (IOException e) {
			log.error("Failed to close report file '{}'", outputFile, e);
			throw new ItemStreamException("Error closing XML output file", e);
		}
	}

	@Override
	public void update(ExecutionContext ctx) {
		// no-op
	}
}
