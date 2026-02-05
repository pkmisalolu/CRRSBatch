package com.abcbs.crrs.jobs.P09310;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
public class P09310ReportWriter implements ItemWriter<List<P09310OutputRecord>>, StepExecutionListener {

	private static final Logger logger = LogManager.getLogger(P09310ReportWriter.class);

	private BufferedWriter writer;
	private static final int LINE_LEN = 133;
	private static final int LINES_PER_PAGE = 55;
	private int pageNo = 0;
	private int lineOnPage = 0;

	private final List<String> subtotalLines = new ArrayList<>();
	private final Map<String, Totals> refundTypeTotals = new LinkedHashMap<>();
	private static final Map<String, String> REFUND_TYPE_LITERALS = Map.of(
			"PER", "  PERSONALS   ",
			"RET", "   RETURNS    ",
			"UND", "UNDELIVERABLES",
			"OTH", "    OTHER     ",
			"OFF", "    OFFSET    ",
			"SPO", " STOP PAYMENT ",
			"API", "ARK PYMT INIT "
			);
	
	private static final List<String> REFUND_TYPE_ORDER = Arrays.asList(
		    "PER",   // PERSONALS
		    "RET",   // RETURNS
		    "UND",   // UNDELIVERABLES
		    "OTH",   // OTHER
		    "OFF",   // OFFSET
		    "SPO",   // STOP PAYMENT
		    "API"    // ARK PYMT INIT
		);

	// control break
	private String prevControlKey = null;

	// per-group subtotal
	private long subCnt = 0;
	private BigDecimal subAmt = BigDecimal.ZERO;

	// grand total
	private long grandCnt = 0;
	private BigDecimal grandAmt = BigDecimal.ZERO;

	// money and count formats
	private static final DecimalFormat AMT_FMT = new DecimalFormat("$###,###,##0.00");
	private static final DecimalFormat COUNT_FMT = new DecimalFormat("###,###,##0");

	@Override
	public void beforeStep(StepExecution stepExecution) {
		final String outputFile = stepExecution.getJobParameters().getString("outputFile");
		try {
			writer = Files.newBufferedWriter(Paths.get(outputFile));
			logger.info("Opened report file: {}", outputFile);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot open report file", e);
		}
	}

	@Override
	public void write(Chunk<? extends List<P09310OutputRecord>> items) throws Exception {
		for (List<P09310OutputRecord> recList : items) {
	        for (P09310OutputRecord rec : recList) {
				final String key = controlKey(rec);

				if (prevControlKey != null && !prevControlKey.equals(key)) {
					 logger.info("Group change detected. Writing subtotal for previous group: {}", prevControlKey);
					writePageSubtotal(prevControlKey, subCnt, subAmt);
					resetSubtotals();
					startNewPage(true, rec);   // new page for new refund type
				}

				// first record of first group
				if (prevControlKey == null) {
					logger.info("Starting first group: {}", key);
					startNewPage(true, rec);
				}

				writeDetail(rec);
				subCnt++;
				subAmt = subAmt.add(nvl(rec.getCrCntrldAmt()));
				grandCnt++;
				grandAmt = grandAmt.add(nvl(rec.getCrCntrldAmt()));

				// accumulate by refund type
				String refType = safe(rec.getCrRefundType());
				refundTypeTotals.computeIfAbsent(refType, k -> new Totals())
				.add(nvl(rec.getCrCntrldAmt()));
				prevControlKey = key;
			}
		}
}


@Override
public ExitStatus afterStep(StepExecution stepExecution) {
	try {
		if (prevControlKey != null) {
			writePageSubtotal(prevControlKey, subCnt, subAmt);
		}

		 logger.info("Writing Grand Totals Section");
		startNewPage(false, null);

		for (String subtotal : subtotalLines) {
			writeFixed("");
			writeFixed(subtotal);
		}

		writeFixed("");
		writeFixed(totalsHeaderLine());
		for (String type : REFUND_TYPE_ORDER) {
			Totals t = refundTypeTotals.getOrDefault(type, new Totals());
			writeFixed(formatGrandLine(type, t.count, t.amount));
		}
		writeFixed("");
		writeFixed(totalsHeaderLine());
		writeFixed(formatGrandTotal(grandCnt, grandAmt));
		writeFooter();

		writer.close();
		logger.info("Report writing completed.");
	} catch (IOException e) {
		logger.error("Report writing failed", e);
		return ExitStatus.FAILED;
	}
	return ExitStatus.COMPLETED;
}

private void writeDetail(P09310OutputRecord r) throws IOException {
	StringBuilder sb = new StringBuilder(133);

	sb.append(' ').append(padRight(date8(r.getCrCntrlDate()), 8)).append(' ')
	.append(padRight(safe(r.getCrCntrlNbr()), 4)).append(' ')
	.append(padRight(safe(r.getCrRefundType()), 3)).append(' ')
	.append(amt15(r.getCrCntrldAmt())).append(' ')
	.append(padRight(safe(r.getCrCheckNbr()), 8)).append(' ')
	.append(amt15(r.getCrCheckAmt())).append(' ')
	.append(padRight(safe(r.getCrClaimType()), 4)).append(' ')
	.append(patientName(r.getCrPatientLname(), r.getCrPatientFname(), 16)).append(' ')
	.append(padRight(safe(r.getCrRemittorName()), 10)).append(' ')
	.append(padRight(safe(r.getCrMbrIdNbr()), 12)).append(' ')
	.append(padRight(safe(r.getCrReasonCode()), 4)).append(' ')
	.append(padRight(safe(r.getCrGlAcctNbr()), 12)).append(' ')
	.append(padRight(safe(r.getGroupName()), 8));

	writeFixed(sb.toString());
}

private void startNewPage(boolean detailPage, P09310OutputRecord rec) throws IOException {
	pageNo++;
	lineOnPage = 0;
	if (detailPage) {
		writeHeader(rec);
	} else {
		writeDetailHeader(rec);  
	}
}

private void ensureBodyLine() throws IOException {
	if (lineOnPage >= LINES_PER_PAGE) {
		writePageSubtotal(prevControlKey, subCnt, subAmt);
		startNewPage(true, null);
	}
}

private void writeHeader(P09310OutputRecord rec) throws IOException {
	final LocalDateTime now = LocalDateTime.now();
	final String runDate = now.format(DateTimeFormatter.ofPattern("MM/dd/yy"));
	final String runTime = now.format(DateTimeFormatter.ofPattern("H:mm:ss"));

	writeFixed("");
	StringBuilder sb = new StringBuilder(133);
	sb.append(" ");
	sb.append(padRight("REPORT #", 8));            
	sb.append("  ");                               
	sb.append(padRight("P09310-A", 8));            
	sb.append(" ".repeat(29));                     
	sb.append(padRight("ARKANSAS BLUE CROSS AND BLUE", 28)); 
	sb.append(" ");                                
	sb.append(padRight("SHIELD", 6));              
	sb.append(" ".repeat(40));                     
	sb.append(padRight("PAGE", 4));                
	sb.append("  ");                               
	sb.append(String.format("%4d", pageNo));       

	writeFixed(sb.toString());

	writeFixed(String.format(
			"%-9s %s%34s%-25s%37s%-9s %s",
			" RUN DATE:", runDate, "", "FINANCIAL ACTIVITY REPORT", "", "RUN TIME:", runTime));

	writeFixed(String.format("%40sRECEIPTS ENTERED FOR ENTRY DATE %s","", formatEntryDate1(LocalDate.now())));

	if (rec != null) {
		writeFixed(String.format(
				"%44sREFUND TYPE/BATCH NBR - %s / %s %s %s",
				"",
				safe(rec.getCrRefundType()),
				safe(rec.getBtBatchPrefix()),
				safe(rec.getBtBatchDate()),
				safe(rec.getBtBatchSuffix())
				));
	} else {
		writeFixed(String.format("%45sREFUND TYPE/BATCH NBR -", ""));
	}

	writeFixed("  C/R CONTROL  REF     CONTROL      CHECK     CHECK         CLM        PATIENT                            REAS               GROUP ");

	writeFixed("   DATE   NBR  TYPE    AMOUNT      NUMBER    AMOUNT         TYPE LAST, FIRST NAME  REMITTOR   MEMBER ID   CODE GL ACCT #     NAME   ");

	writeFixed(" -------- ---- --- --------------- -------- --------------- ---- ---------------- ---------- ------------ ---- ------------ --------- ");

}
private void writeDetailHeader(P09310OutputRecord rec) throws IOException {
	final LocalDateTime now = LocalDateTime.now();
	final String runDate = now.format(DateTimeFormatter.ofPattern("MM/dd/yy"));
	final String runTime = now.format(DateTimeFormatter.ofPattern("H:mm:ss"));

	writeFixed("");
	StringBuilder sb = new StringBuilder(133);
	sb.append(" ");
	sb.append(padRight("REPORT #", 8));            
	sb.append("  ");                               
	sb.append(padRight("P09310-A", 8));            
	sb.append(" ".repeat(29));                     
	sb.append(padRight("ARKANSAS BLUE CROSS AND BLUE", 28)); 
	sb.append(" ");                                
	sb.append(padRight("SHIELD", 6));              
	sb.append(" ".repeat(40));                     
	sb.append(padRight("PAGE", 4));                
	sb.append("  ");                               
	sb.append(String.format("%4d", pageNo));       

	writeFixed(sb.toString());

	writeFixed(String.format(
			"%-9s %s%34s%-25s%37s%-9s %s",
			" RUN DATE:", runDate, "", "FINANCIAL ACTIVITY REPORT", "", "RUN TIME:", runTime));
}

private void writePageSubtotal(String controlKey, long count, BigDecimal amount) throws IOException {
	if (controlKey == null) return;
    logger.info("Writing subtotal for group: {} | Count: {} | Amount: {}", controlKey, count, amount);

	String suffix=null;
	if(controlKey.substring(16).trim().length()!=2)
		suffix = "0"+ controlKey.substring(16).trim();
	else
		suffix=controlKey.substring(16);
	writeFixed(""); // blank spacer
	writeFixed(padRight("", 87)  + "NUMBER"  + padRight("", 7)   + "AMOUNT");
	writeFixed(String.format(
			"       TOTAL RECEIPTS ESTABLISHED FOR REFUND TYPE/BATCH NBR - %-19s   %9s   %15s",
			padRight(controlKey.substring(0, 3)+" "+"/"+" "+controlKey.substring(4, 7)+" "+controlKey.substring(8, 14)+" "+suffix, 19),
			formatCount(count,9),
			amt15s(amount)
			));
}

private String formatGrandTotal(long count, BigDecimal amount) {
	return String.format(
			"%38s%-29s %-27s %1s%9s%2s%15s",
			"",                                    
			"G R A N D    T O T A L     OF",       
			"ALL RECEIPTS ESTABLISHED IS",         
			"",                                    
			formatCount(count,9),               
			"",                                    
			amt15s(amount)                         
			);
}

private static String patientName(String lastName, String firstName, int totalLen) {
    String ln = safe(lastName);
    String fn = safe(firstName);

    // 10 bytes reserved for last name
    String last = padRight(ln, 10).substring(0, 10);

    // comma always
    String comma = ",";

    // remaining space for first name
    int fnLen = totalLen - (10 + 1);
    String first = padRight(fn, fnLen).substring(0, fnLen);

    return last + comma + first;
}


private void resetSubtotals() {
	subCnt = 0;
	subAmt = BigDecimal.ZERO;
}

private String controlKey(P09310OutputRecord r) {
    return safe(r.getCrRefundType()) + "|" 
         + safe(r.getBtBatchPrefix()) + "|" 
         + safe(r.getBtBatchDate()) + "|" 
         + safe(r.getBtBatchSuffix());
}

private void writeFixed(String s) throws IOException {
	ensureBodyLine();
	if (s.length() > LINE_LEN) s = s.substring(0, LINE_LEN);
	else if (s.length() < LINE_LEN) s = padRight(s, LINE_LEN);
	writer.write(s);
	writer.newLine();
	lineOnPage++;
}

private static String padRight(String s, int len) {
	if (s == null) s = "";
	if (s.length() >= len) return s.substring(0, len);
	StringBuilder b = new StringBuilder(len);
	b.append(s);
	while (b.length() < len) b.append(' ');
	return b.toString();
}
private String formatGrandLine(String refundType, long count, BigDecimal amount) {
	String literal = REFUND_TYPE_LITERALS.getOrDefault(refundType, padRight(refundType, 14));

	return String.format(
			"        %-28s %-28s %-11s %-14s IS  %9s  %15s",
			"G R A N D    T O T A L    OF",
			"ALL RECEIPTS ESTABLISHED FOR",
			"REFUND TYPE",
			literal,
			formatCount(count,9),
			amt15s(amount)
			);
}

private static String formatEntryDate1(LocalDate d) {
    if (d == null) return "           "; // spaces if blank
    // Full month name padded/truncated to 10 chars
    String month = padRight(d.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.US).toUpperCase(), 10);
    // Day without leading zero
    int day = d.getDayOfMonth();
    int year = d.getYear();
    return String.format("%s %d, %d", month, day, year);
}

private void writeFooter() throws IOException {
	writeFixed(repeat('*', 133));
	String msg = "END OF P09310-A REPORT";
	int totalLen = 133;
	int side = (totalLen - msg.length() - 4) / 2;
	String line = "**" + " ".repeat(side) + msg + " ".repeat(totalLen - msg.length() - side - 4) + "**";
	writeFixed(line);
	writeFixed(repeat('*', 133));
}

private String totalsHeaderLine() {
	return padRight("", 100) + "NUMBER" + padRight("", 9) + "AMOUNT";
}

private static String repeat(char c, int n) {
	return String.valueOf(c).repeat(n);
}

private static String date8(LocalDate d) {
	if (d == null) return "        ";
	return d.format(DateTimeFormatter.ofPattern("MM/dd/yy"));
}

private static String rightAlign(String s, int width) {
    if (s == null) s = "";
    if (s.length() > width) return s.substring(s.length() - width); // trim left if too long
    return " ".repeat(width - s.length()) + s;
}

private static String formatCount(long count, int width) {
    return rightAlign(COUNT_FMT.format(count), width);
}

private static String safe(String s) { return s == null ? "" : s; }

private static BigDecimal nvl(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }

private static String amt15(BigDecimal b) {
	String num = AMT_FMT.format(nvl(b));
	if (num.length() >= 15) return num.substring(0, 15);
	StringBuilder sb = new StringBuilder(15);
	for (int i = 0; i < 15 - num.length(); i++) sb.append(' ');
	sb.append(num);
	return sb.toString();
}

private static String amt15s(BigDecimal b) {
	return amt15(b);
}

private static final class Totals {
	long count = 0;
	BigDecimal amount = BigDecimal.ZERO;
	void add(BigDecimal amt) { count++; amount = amount.add(amt); }
}
}
