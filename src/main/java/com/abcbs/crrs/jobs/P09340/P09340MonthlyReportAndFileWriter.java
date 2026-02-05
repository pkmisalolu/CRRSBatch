package com.abcbs.crrs.jobs.P09340;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.abcbs.crrs.projections.IP09340ControlView;
import com.abcbs.crrs.repository.IP09ControlRepository;
import com.abcbs.crrs.utilities.Utilities;

@Component
@StepScope
public class P09340MonthlyReportAndFileWriter implements ItemWriter<P09340GlRecord>, ItemStream, StepExecutionListener {

	private static final Logger logger = LogManager.getLogger(P09340MonthlyReportAndFileWriter.class);

	private static final int PAGE_LIMIT = 60, HEADER_END = 8, FIRST_DETAIL = 9, LAST_DETAIL = 55;
	private final Path reportPath, dataOutPath;
	private final Path accountOutPath;
	private P09340InputRecord ctrl;

	private BufferedWriter rpt, out, accountOut;
	private final List<P09340GlRecord> buffer = new ArrayList<>();

	private int page = 0, line = PAGE_LIMIT;
	private boolean anyDetailWritten = false;

	private long grandCount = 0L;
	private long grandResolved=0L;
	private BigDecimal grandDebits = BigDecimal.ZERO, grandCredits = BigDecimal.ZERO;

	private String currentRefundType = "";
	private String currentAcctNbr = "";
	private String reportDt="";
	
	@Autowired
	private IP09ControlRepository controlRepo;

	public P09340MonthlyReportAndFileWriter(
			@Value("#{jobParameters['reportFile']}") String reportFile,
			@Value("#{jobParameters['matchedOutFile']}") String matchedOutFile,
			@Value("#{jobParameters['accountFile']}") String accountOutFile) {
		this.reportPath = Path.of(reportFile);
		this.dataOutPath = Path.of(matchedOutFile);
		this.accountOutPath = Path.of(accountOutFile);
	}

	@Override
	public void beforeStep(StepExecution stepExecution) {
		logger.info("Starting P09340 report generation...");
		ExecutionContext jec = stepExecution.getJobExecution().getExecutionContext();
		this.ctrl = (P09340InputRecord) jec.get("p09340InputRecord");
		if (this.ctrl == null)
			throw new IllegalStateException("p09340InputRecord not in jobExecutionContext");
		
		logger.debug("Loaded control record: {}", ctrl);
	}

	@Override
	public void open(org.springframework.batch.item.ExecutionContext ec) {
		try {
			if (reportPath.getParent() != null) Files.createDirectories(reportPath.getParent());
			if (dataOutPath.getParent() != null) Files.createDirectories(dataOutPath.getParent());
			rpt = Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			out = Files.newBufferedWriter(dataOutPath, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			accountOut = Files.newBufferedWriter(accountOutPath, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			
			IP09340ControlView rec = controlRepo.findControlRecord(ctrl.getRefundType(), "O");
			if (rec != null) {
				String yr = String.valueOf(rec.getCntrlToDate().getYear());
				String mon = String.format("%02d", Integer.parseInt(String.valueOf(rec.getCntrlToDate().getMonthValue())));
				String day   = String.format("%02d", Integer.parseInt(String.valueOf(rec.getCntrlToDate().getDayOfMonth())));

				reportDt = mon+"/"+day+"/"+yr;
			}
			
			logger.info("Opened account file: {}", accountOutPath);
			logger.info("Opened report file: {} and data file: {}", reportPath, dataOutPath);
		} catch (IOException e) {
			logger.error("Error initializing writers", e);
			throw new ItemStreamException(e);
		}
	}

	@Override public void update(org.springframework.batch.item.ExecutionContext ec) {}
	@Override public void close() {
		logger.info("Closing P09340 writer and finalizing report...");
		try {
			long gResolved = 0L;
			long tResolved = 0L;
			grandResolved = 0L;

			buffer.sort(Comparator
					.comparing((P09340GlRecord r) -> nz(r.getGlRefundType()).trim())
					.thenComparing(r -> nz(r.getGlAcctNbr()).trim()));

			String curAcct = null, curRft = null;

			long gCount = 0L;                          // per (refund,acct)
			BigDecimal gDebits = BigDecimal.ZERO;
			BigDecimal gCredits = BigDecimal.ZERO;

			long tCount = 0L;                          // per refund type
			BigDecimal tDebits = BigDecimal.ZERO;
			BigDecimal tCredits = BigDecimal.ZERO;

			for (int i = 0; i < buffer.size(); i++) {
				P09340GlRecord r = buffer.get(i);
				
				if ("GENERAL_LEDGER".equalsIgnoreCase(nz(r.getOutputType()))) {
			        out.write(serializeFixedLength(r));
			        out.newLine();
			        // do NOT update counters, do NOT print to report, do NOT write to account file
			        continue;
			      }
				
				String acct = nz(r.getGlAcctNbr()).trim();
				String rft  = nz(r.getGlRefundType()).trim();

				String month = String.format("%02d", Integer.parseInt(r.getReportMo()));
				String day   = String.format("%02d", Integer.parseInt(r.getReportDt()));

				reportDt = month+"/"+day+"/"+r.getReportYr().substring(2);

				boolean newAcct = (curAcct == null) || !curAcct.equals(acct) || !curRft.equals(rft);
				boolean newRefund = (curRft == null) || !curRft.equals(rft);

				// ---- new refund type ----
				if (newRefund && curRft != null) {                    logger.debug("Refund type changed: {} → {}", curRft, rft);
				logger.debug("Refund type changed: {} → {}", curRft, rft);
				printGroupTotals(curRft, curAcct, gCount, gDebits, gCredits, gResolved);
				printRefundTypeTotals(curRft, tDebits, tCredits, tCount, tResolved);

				// Reset both refund and group counters including resolved
				tDebits = BigDecimal.ZERO;
				tCredits = BigDecimal.ZERO;
				tCount = 0L;
				tResolved = 0L;

				gDebits = BigDecimal.ZERO;
				gCredits = BigDecimal.ZERO;
				gCount = 0L;
				gResolved = 0L;

				currentRefundType = rft;
				currentAcctNbr = acct;
				newPage();
				}

				// ---- new account under same refund type ----
				if (newAcct && !newRefund && curAcct != null) {
					printGroupTotals(curRft, curAcct, gCount, gDebits, gCredits, gResolved);

					// Reset group counters including resolved
					gDebits = BigDecimal.ZERO;
					gCredits = BigDecimal.ZERO;
					gCount = 0L;
					gResolved = 0L;

					currentRefundType = rft;
					currentAcctNbr = acct;
					newPage();
				}
				// ---- set current context ----
				currentRefundType = rft;
				currentAcctNbr = acct;
				curRft = rft;
				curAcct = acct;

				// ---- print detail ----
				if (!"GENERAL_LEDGER".equalsIgnoreCase(r.getOutputType())) {
					if (line > LAST_DETAIL) newPage();
					rpt.write(formatDetailLine(r));
					rpt.newLine();
					line++;
					anyDetailWritten = true;
				}

				// ---- accumulate ----
				BigDecimal amt = parseAmount(r.getGlActAmt());
				BigDecimal abs = amt.abs();
				if (amt.signum() < 0) {
					gCredits = gCredits.add(abs);
					tCredits = tCredits.add(abs);
					grandCredits = grandCredits.add(abs);
				} else {
					gDebits = gDebits.add(abs);
					tDebits = tDebits.add(abs);
					grandDebits = grandDebits.add(abs);
				}
				gCount++; tCount++; grandCount++;

				// Check resolved count: glCashRecBal == 0
				String bal = nz(r.getGlCashRecBal()).trim().replace(",", "").replace("$", "");
				if (bal.isEmpty()) bal = "0";
				try {
					BigDecimal balVal = new BigDecimal(bal);
					if (balVal.compareTo(BigDecimal.ZERO) == 0) {
						gResolved++;
						tResolved++;
						grandResolved++;
					}
				} catch (NumberFormatException ignore) {
					logger.warn("Invalid balance format for record: {}", r.getGlCashRecBal());
				}

				out.write(serializeFixedLength(r));
				out.newLine();

				// ---- account file logic ----
				String acctNo = nz(r.getGlAcctNbr()).trim();
				if (!"GENERAL_LEDGER".equalsIgnoreCase(r.getOutputType()) && acctNo.length() >= 4) {
					if (acctNo.length() >= 4) {
						String prefix = acctNo.substring(0, 4);
						if (prefix.equals("1041") || prefix.equals("1082") ||
								prefix.equals("2800") || prefix.equals("1271")) {

							accountOut.write(serializeFixedLength(r));
							accountOut.newLine();
							logger.debug("Record written to accountOutFile for acct prefix {}", prefix);
						}
					}
				}
			}

			// ---- flush last refund type ----
			if (curAcct != null) {
				printGroupTotals(curRft, curAcct, gCount, gDebits, gCredits, gResolved);
			}
			if (curRft != null) {
				printRefundTypeTotals(curRft, tDebits, tCredits, tCount, tResolved);
			}

			// reset after final flush (for clarity)
			gResolved = 0L;
			tResolved = 0L;


			// flush last group
			if (curAcct != null) {
				gCount = 0L;
				gDebits = BigDecimal.ZERO;
				gCredits = BigDecimal.ZERO;
				//printGroupTotals(curRft, curAcct, gCount, gDebits, gCredits);
			}

			if (!anyDetailWritten) {
				//ensureHeader();
				printHeaderForTotals();
				rpt.newLine();
				rpt.newLine();
				rpt.newLine();
				rpt.newLine();
				rpt.write(spaces(33) + stars(6) + "  DATA IN FILE DID NOT MATCH REPORT CRITERIA " + stars(6));
				rpt.newLine();
			} else {
				newTotalsPage();
			}

			rpt.flush(); rpt.close();
			out.flush(); out.close();

			if (accountOut != null) {
				accountOut.flush();
				accountOut.close();
				logger.info("Account file successfully generated: {}", accountOutPath);
			}
			logger.info("Report successfully generated: {}", reportPath);
			logger.info("Data file successfully generated: {}", dataOutPath);
			logger.info("Grand Totals -> Count: {}, Debits: {}, Credits: {}, Resolved: {}",
					grandCount, grandDebits, grandCredits, grandResolved);
		} catch (IOException e) {
			logger.error("Error closing P09340 writer", e);
			throw new ItemStreamException(e);
		}
	}
	private String serializeFixedLength(P09340GlRecord r) {
		StringBuilder b = new StringBuilder(198);
		b.append(fx(r.getP09dedsJulian(), 5));
		b.append(fx(r.getP09dedsHhmmss(), 7));
//		b.append(fx(Utilities.julianDate(), 5));
//		b.append(fx(Utilities.julianTime(), 7));
		b.append(fx(r.getP09deds29(), 5));                            
		b.append(fx(r.getGlP09dedsId(), 2));
		b.append(fx(r.getGlRefundType(), 3));
		b.append(fx(r.getGlControlDate(), 10));
		b.append(fx(r.getGlControlNbr(), 4));
		b.append(fx(r.getGlReceiptType(), 2));
		b.append(fx(r.getGlBankAcctNbr(), 35));
		b.append(fx(r.getGlAcctNbr(), 12));
		b.append(fx(r.getGlActCode(), 3));
		b.append(fixAmt(r.getGlActAmt(), 12));
		b.append(fx(r.getGlActDate(), 10));
		b.append(fx(r.getGlReportMo(), 2));
		b.append(fx(r.getGlReportYr(), 2));
		b.append(fx(r.getGlPatientLn(), 15));
		b.append(fx(r.getGlPatientFn(), 11));
		b.append(fx(r.getGlMbrIdNbr(), 12));
		b.append(fx(r.getGlXrefType(), 2));
		b.append(fx(r.getGlXrefClaimNbr(), 20));
		b.append(fx(r.getGlXrefDate(), 10));
		b.append(fixAmt(r.getGlCashRecBal(), 12));
		b.append(fx(r.getGlCorp(), 2));
		b.append("        "); 
		return b.toString();
	}


	@Override
	public void write(Chunk<? extends P09340GlRecord> chunk) {
		logger.debug("Received chunk of {} records", chunk.size());
		buffer.addAll(chunk.getItems());
	}

	@Override public ExitStatus afterStep(StepExecution stepExecution) { 
		logger.info("P09340 writer step completed with status: {}", stepExecution.getExitStatus());

		return stepExecution.getExitStatus(); 
	}

	private void ensureHeader() throws IOException {
		if (line <= LAST_DETAIL) return;
		newPage();
	}

	private void newPage() throws IOException {
		page++; line = 0;
		printHeader();
		line = HEADER_END;
		while (line < FIRST_DETAIL - 1) { rpt.newLine(); line++; }
		line++;
	}
	private void newPageFinal(boolean total) throws IOException {
		page++; line = 0;
		printHeaderForTotals();
		line = HEADER_END;
		while (line < FIRST_DETAIL - 1) { rpt.newLine(); line++; }
		line++;
	}

	private void printHeader() throws IOException {
		String reportName = "P09340-A";
		String runDate = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yy"));
		String runTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
		String corpLine = corpTitle(nz(ctrl != null ? ctrl.getCorpNo() : ""));

		rpt.write(padRight(" REPORT #", 9)); rpt.write("  ");
		rpt.write(padRight(reportName, 8));
		rpt.write(padRight("", 29));
		rpt.write("ARKANSAS BLUE CROSS AND BLUE SHIELD");
		rpt.write(padRight("", 40));
		rpt.write("PAGE  " + padLeft(Integer.toString(page), 4));
		rpt.newLine();

		rpt.write(" RUN DATE: " + padRight(runDate, 8));
		rpt.write(padRight("", 28));
		rpt.write("DETAIL GENERAL  LEDGER");
		rpt.write(" ACCOUNT REPORT");
		rpt.write(padRight("", 31));
		rpt.write("RUN TIME: " + padRight(runTime, 8));
		rpt.newLine();

		rpt.write(" "+padRight(corpLine, 33));
		rpt.write(padRight("", 26));
		rpt.write(padRight("AS OF: "+reportDt, 15));
		rpt.newLine();

		rpt.write(" REFUND TYPE: ");
		rpt.write(padLeft(currentRefundType, 4));
		rpt.write("  ACCOUNT NUMBER:  ");
		rpt.write(padRight(fmtAcct(currentAcctNbr), 14));
		rpt.newLine();
		rpt.write(" REFUND     CONTROL        ACTIVITY         ACTIVITY                                    MEMBER            CROSS REFERENCE");
		rpt.newLine();
		rpt.write("  TYPE   DATE    NUMBER  CODE    DATE        AMOUNT          PATIENT   'S NAME        ID NUMBER        DATE        NUMBER");
		rpt.newLine();
		rpt.write(padRight("", 132));
		rpt.newLine();

	}
	private void printHeaderForTotals() throws IOException { 	
		String reportName = "P09340-A";
		String runDate = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yy"));
		String runTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
		String corpLine = corpTitle(nz(ctrl != null ? ctrl.getCorpNo() : ""));

		rpt.write(padRight(" REPORT #", 9)); rpt.write("  ");
		rpt.write(padRight(reportName, 8));
		rpt.write(padRight("", 29));
		rpt.write("ARKANSAS BLUE CROSS AND BLUE SHIELD");
		rpt.write(padRight("", 40));
		rpt.write("PAGE  " + padLeft(Integer.toString(page), 4));
		rpt.newLine();

		rpt.write(" RUN DATE: " + padRight(runDate, 8));
		rpt.write(padRight("", 28));
		rpt.write("DETAIL GENERAL  LEDGER");
		rpt.write(" ACCOUNT REPORT");
		rpt.write(padRight("", 31));
		rpt.write("RUN TIME: " + padRight(runTime, 8));
		rpt.newLine();

		rpt.write(" "+padRight(corpLine, 33));
		rpt.write(padRight("", 26));
		rpt.write(padRight("AS OF: "+reportDt, 15));
		rpt.newLine();


	}

	private void printGroupTotals(String refundType, String acctNbr,
			long count, BigDecimal debits, BigDecimal credits, long resolved) throws IOException {
		if (line + 3 > PAGE_LIMIT) newPage();

		String acctFmt = fmtAcct(acctNbr);
		BigDecimal net = debits.subtract(credits);

		rpt.newLine();
		rpt.write(" TOTAL FOR REFUND TYPE: " + padRight(refundType, 3));
		rpt.write("   ACCOUNT NUMBER:  " + padRight(acctFmt, 14));
		rpt.newLine();

		rpt.write("   C/R ACTIVITY    COUNT: " + padRight(zCount(count), 6));
		//rpt.write("                    AMOUNT: " + padLeft(fmtAmount(net), 15));
		if (fmtAmount(net).endsWith("-")) {
		    rpt.write("                    AMOUNT: " + padLeft(fmtAmount(net), 16));
		} else {
		    rpt.write("                    AMOUNT: " + padLeft(fmtAmount(net), 15));
		}
		rpt.write("                   RESOLVED COUNT: " + padLeft(zCount(resolved), 6));
		rpt.newLine();

		rpt.write("                   DEBITS: " + padLeft(fmtAmount(debits), 14));
		String creditAmt = fmtAmount(credits.negate());
		if (credits.compareTo(BigDecimal.ZERO) != 0) {
			rpt.write("           CREDITS:" + padLeft(fmtAmount(credits.negate()), 16));
		}else {
			rpt.write("           CREDITS:" + padLeft(fmtAmount(credits.negate()), 15));
		}
		rpt.newLine();

		rpt.newLine();
		line += 4;
	}

	private void newTotalsPage() throws IOException {
		BigDecimal netTotal = grandDebits.subtract(grandCredits);
		newPageFinal(true);
		rpt.newLine();
		rpt.write(" TOTAL C/R ACTIVITY     COUNT:  " + padLeft(zCount(grandCount), 6));
		rpt.write("                    AMOUNT: " + padLeft(fmtAmount(netTotal), 15));
		rpt.write("                   RESOLVED COUNT: " + padLeft(zCount(grandResolved), 6));
		rpt.newLine();
		rpt.newLine();

		rpt.write(padRight("", 26));
		rpt.write("TOTAL DEBITS:  " + padLeft(fmtAmount(grandDebits), 14));
		rpt.write(padRight("", 24));
		rpt.write("TOTAL CREDITS: " + padLeft(fmtAmount(grandCredits.negate()), 15));
		rpt.newLine();
		rpt.newLine();
		rpt.newLine();
		rpt.newLine();
		rpt.newLine();
		rpt.newLine();
		rpt.newLine();
		rpt.newLine();
		rpt.newLine();
		rpt.newLine();
		rpt.newLine();
		rpt.newLine();
		rpt.newLine();
		rpt.newLine();
		rpt.newLine();

		rpt.write(stars(52) + "  END OF P09340-A REPORT  " + stars(54));
		rpt.newLine();
		line += 3;
	}

	// ----------------- Formatting helpers -----------------
	private static String nz(String s) { return s == null ? "" : s; }
	private static String padRight(String s, int len) {
		String v = nz(s); return v.length() >= len ? v.substring(0, len) : v + " ".repeat(len - v.length());
	}
	private static String padLeft(String s, int len) {
		String v = nz(s); return v.length() >= len ? v.substring(v.length() - len) : " ".repeat(len - v.length()) + v;
	}
	private static String spaces(int n) { return " ".repeat(Math.max(0, n)); }
	private static String stars(int n)  { return "*".repeat(Math.max(0, n)); }
	private static String zCount(long n){ return Long.toString(n); }

	private static String fmtAcct(String acct) {
		String raw = nz(acct).replaceAll("[^0-9A-Za-z]", "");
		if (raw.length() < 12) {
	        raw = String.format("%-12s", raw);
	    }
		if (raw.length() == 12) return raw.substring(0,4) + "-" + raw.substring(4,8) + "-" + raw.substring(8,12);
		return nz(acct).trim();
	}

	private String formatDetailLine(P09340GlRecord r) {
		String refundType = nz(r.getGlRefundType());
		String controlDate = toMDY8(nz(r.getGlControlDate()));
		String controlNbr  = nz(r.getGlControlNbr());
		String actCode     = nz(r.getGlActCode());
		String actDate     = toMDY8(nz(r.getGlActDate()));
		String amount      = normalizeAmountPrint(nz(r.getGlActAmt()));
		String patientLn   = fix(nz(r.getGlPatientLn()), 14);
		String patientFn   = fix(nz(r.getGlPatientFn()), 11);
		String memberId    = fix(nz(r.getGlMbrIdNbr()), 12);
		String xrefDate    = toMDY8(nz(r.getGlXrefDate()));
		String xrefNbr     = fix(nz(r.getGlXrefClaimNbr()), 20);
		String amt = null;
		if(fmtAmount(parseAmount(amount)).contains("-")) {
			//sign = "-";
			amt = padLeft(fmtAmount(parseAmount(amount)),15);
		}else {
			amt = padLeft(fmtAmount(parseAmount(amount)),14)+" ";
			//sign=" ";
		}

		StringBuilder sb = new StringBuilder(132);
		sb.append("  ");
		sb.append(fix(refundType, 3)).append("  ");
		sb.append(fix(controlDate, 8)).append("   ");
		sb.append(fix(controlNbr, 4)).append("   ");
		sb.append(fix(actCode, 3)).append("  ");
		sb.append(fix(actDate, 8)).append("  ");
		sb.append(amt).append("  ");
		sb.append(fix(patientLn, 14)).append(" ");
		sb.append(fix(patientFn, 11)).append("  ");
		sb.append(fix(memberId, 12)).append("    ");
		sb.append(fix(xrefDate, 8)).append("   ");
		sb.append(fix(xrefNbr, 20));
		return sb.toString();
	}

	private static BigDecimal parseAmount(String raw) {
		String v = nz(raw).trim().replace(",", "");
		if (v.isEmpty()) return BigDecimal.ZERO;

		boolean credit = v.startsWith("-") || v.endsWith("-");
		v = v.replace("-", "");

		BigDecimal amt;
		try {
			amt = new BigDecimal(v).movePointLeft(2); 
		} catch (Exception e) {
			amt = BigDecimal.ZERO;
		}

		return credit ? amt.negate() : amt;
	}

	private static String normalizeAmountPrint(String raw) {
		String v = nz(raw).trim().replace(",", "");
		if (v.isEmpty()) return "0.00";
		boolean credit = v.endsWith("-");
		if (credit)
			return moneyFmt(new BigDecimal(v.substring(0, v.length()-1))) + "-";
		return moneyFmt(new BigDecimal(v));
	}

	private static String moneyFmt(BigDecimal n) {
		return new DecimalFormat("###,###,##0.00").format(n);
	}

	private static String fmtAmount(BigDecimal value) {
		if (value == null) value = BigDecimal.ZERO;
		DecimalFormat df = new DecimalFormat("$#,##0.00");
		String formatted = df.format(value.abs());
		return formatted + (value.signum() < 0 ? "-" : "");
	}

	private static String corpTitle(String corpNo) {
		String c = nz(corpNo).trim();
		return switch (c) {
		case "01" -> "CORP 01 - BCBS                   ";
		case "03" -> "CORP 03 - HMO PARTNERS           ";
		case "04" -> "CORP 04 - ABCBS MEDIPAK ADVANTAGE";
		default   -> ("CORP " + fx(c, 2) + " - " + fx("", 28));
		};
	}

	private static String fx(String s, int len) {
		String v = nz(s);
		if (v.length() == len) return v;
		if (v.length() > len) return v.substring(0, len);
		return v + " ".repeat(len - v.length());
	}

	private static String fix(String s, int len) { return fx(s, len); }

	private static String toMDY8(String raw) {
		String r = nz(raw).trim();
		try {
			String y, m, d;
			if (r.length() == 10 && r.charAt(4) == '-' && r.charAt(7) == '-') {
				y = r.substring(2,4); m = r.substring(5,7); d = r.substring(8,10);
			} else if (r.length() == 8) {
				y = r.substring(2,4); m = r.substring(4,6); d = r.substring(6,8);
			} else {
				return fx(r, 8);
			}
			return m + "/" + d + "/" + y;
		} catch (Exception e) {
			return fx(r, 8);
		}
	}
	private void printRefundTypeTotals(String refundType,
			BigDecimal debits,
			BigDecimal credits,
			long count,  long resolved) throws IOException {
		if (line + 4 > PAGE_LIMIT) newPage();
		BigDecimal net = debits.subtract(credits);

		rpt.newLine();
		rpt.write( " TOTAL FOR REFUND TYPE: " + padRight(refundType, 3));
		rpt.newLine();
		rpt.write("   C/R ACTIVITY    COUNT: " + padLeft(zCount(count), 6));
		//rpt.write("                    AMOUNT: " + padLeft(fmtAmount(net), 15));
		if (fmtAmount(net).endsWith("-")) {
		    rpt.write("                    AMOUNT: " + padLeft(fmtAmount(net), 16));
		} else {
		    rpt.write("                    AMOUNT: " + padLeft(fmtAmount(net), 15));
		}
		rpt.write("                   RESOLVED COUNT: " + padLeft(zCount(resolved), 6));
		rpt.newLine();
		rpt.write("                   DEBITS: " + padLeft(fmtAmount(debits), 14));
		//rpt.write("           CREDITS:" + padLeft(fmtAmount(credits.negate()), 15));
		if (credits.compareTo(BigDecimal.ZERO) != 0) {
			rpt.write("           CREDITS:" + padLeft(fmtAmount(credits.negate()), 16));
		}else {
			rpt.write("           CREDITS:" + padLeft(fmtAmount(credits.negate()), 15));
		}

		rpt.newLine();
		rpt.newLine();
		line += 5;
	}
	private static String fixAmt(String s, int len) {
	    String v = nz(s);

	    // If already exact length, return AS-IS (do NOT trim or pad)
	    if (v.length() == len) {
	        return v;
	    }

	    // If longer, truncate
	    if (v.length() > len) {
	        return v.substring(0, len);
	    }

	    // If shorter, RIGHT-PAD with spaces
	    return " ".repeat(len - v.length())+v;
	}
}
