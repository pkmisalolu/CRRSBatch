package com.abcbs.crrs.jobs.P09315;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.abcbs.crrs.config.JobLauncherRunner;
//import com.abcbs.crrs.repository.P09315JdbcRepository.ActivityAgg;

@Component
@Scope("step") // step-scoped since it manages per-run state
public class P09315ReportWriter implements Closeable {
	private BufferedWriter w;
	private int page = 0;
	private String corpNo, corpName;
	private static final int LINE_LEN = 133;
	private static final DecimalFormat AMT = new DecimalFormat("$###,###,##0.00",
			DecimalFormatSymbols.getInstance(Locale.US));
	private static final DecimalFormat CNT = new DecimalFormat("###,###,##0",
			DecimalFormatSymbols.getInstance(Locale.US));
	private static final Logger logger = LogManager.getLogger(P09315ReportWriter.class);

	public void open(String out, String corpNo, String corpName, LocalDateTime runTs) {
		this.corpNo = corpNo;
		this.corpName = corpName;
		try {
			w = Files.newBufferedWriter(Paths.get(out));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		
	}

//	 --- Section 1: MANUAL/SYSTEM ESTABLISHED (page 1 top) ---
	public void writeEstablished(List<ActivityAggView> rows, List<String> refundOrder) throws IOException {
		nextPage(); // Page 1 header
		headerBanner();
		fixed("                                 " + padRight("MANUAL ESTABLISHED", 27) + "  " // 3 spaces gap
				+ padRight("SYSTEM ESTABLISHED", 30));
		fixed(String.format(" %-21s%16s%13s%10s%13s", "", "#", "AMOUNT", "#", "AMOUNT"));
		blank();
		// Map (refund, side) -> agg
		Map<String, ActivityAggView> m = new HashMap<>();
		for (ActivityAggView a : rows)
			m.put(a.getRefundType() + "|" + a.getActivity(), a);
		// Row order & labels must match the sample page 1
		List<String> labels = List.of("PERSONAL CHECKS", "RETURNED CHECKS", "RETURNED/UNDELIVERABLES", "OTH    C/R",
				"OFFSET", "STOP PAYMENT", "ARKANSAS PYMT INITIATIVE");
		for (int i = 0; i < refundOrder.size(); i++) {
			String rt = refundOrder.get(i);
			String label = labels.get(i);
			ActivityAggView man = m.get(rt + "|ACC");
	        ActivityAggView sys = m.get(rt + "|APP");
	        
	        long manCnt = man == null ? 0 : man.getCount();
	        BigDecimal manAmt = man == null ? BigDecimal.ZERO : man.getAmount();
	        
	        long sysCnt = sys == null ? 0 : sys.getCount();
	        BigDecimal sysAmt = sys == null ? BigDecimal.ZERO : sys.getAmount();
	        
	        fixed(String.format(
	                " %-24s %12s %11s%11s %11s",
	                label,
	                CNT.format(manCnt),
	                AMT.format(manAmt),
	                CNT.format(sysCnt),
	                AMT.format(sysAmt)
	        ));
		}
		
		// Totals line (MAN/SYS totals shown on sample)
		// :contentReference[oaicite:23]{index=23}
		long manCnt = 0, sysCnt = 0;
		BigDecimal manAmt = BigDecimal.ZERO, sysAmt = BigDecimal.ZERO;
		  for (ActivityAggView a : rows) {
		        if ("MAN".equals(a.getActivity())) {
		            manCnt += a.getCount();
		            manAmt = manAmt.add(a.getAmount());
		        } else if ("SYS".equals(a.getActivity())) {
		            sysCnt += a.getCount();
		            sysAmt = sysAmt.add(a.getAmount());
		        }
		    }

		fixed(String.format(" %24s %12s %11s %10s%12s", "TOTALS", CNT.format(manCnt), AMT.format(manAmt),
				CNT.format(sysCnt), AMT.format(sysAmt)));
		blank();
		blank();
		blank();
	}


	public void writeManualRecon(List<ActivityAggView> rows, List<String> actsOrder, List<String> refundOrder)
			throws IOException {
		
		matrixBlock("MANUAL RECON ACTIVITY", rows, actsOrder, List.of("PER", "RET", "UND", "OTH"),
				List.of("PERSONAL", "RETURNS", "UNDELIVERABLE", "OTHER"));

		matrixBlock("MANUAL RECON ACTIVITY", rows, actsOrder, List.of("OFF", "SPO", "API", "TOT"),
				List.of("OFFSET", "STOP PAYMENT", "ARK INIT PYMT", "TOTAL PROCESSED"));
	}

	public void writeSystemRecon(List<ActivityAggView> rows, List<String> actsOrder, List<String> refundOrder)
			throws IOException {
		nextPage();
		headerBanner(); // page-2 banner from sample :contentReference[oaicite:26]{index=26}
		blank();
		matrixBlock("SYSTEM RECON ACTIVITY", rows, actsOrder, List.of("PER", "RET", "UND", "OTH"),
				List.of("PERSONAL", "RETURNS", "UNDELIVERABLE", "OTHER"));
		matrixBlock("SYSTEM RECON ACTIVITY", rows, actsOrder, List.of("OFF", "SPO", "API", "TOT"),
				List.of("OFFSET", "STOP PAYMENT", "ARK INIT PYMT", "TOTAL PROCESSED"));
	}

	public void writeManualRequest(List<ActivityAggView> rows, List<String> actsOrder, List<String> refundOrder)
			throws IOException {
		nextPage();
		headerBanner(); // page-3 banner from sample :contentReference[oaicite:27]{index=27}
		matrixBlock("MANUAL REQUESTED ACTIVITY", rows, actsOrder, List.of("PER", "RET", "UND", "OTH"),
				List.of("PERSONAL", "RETURNS", "UNDELIVERABLE", "OTHER"));
		matrixBlock("MANUAL REQUESTED ACTIVITY", rows, actsOrder, List.of("OFF", "SPO", "API", "TOT"),
				List.of("OFFSET", "STOP PAYMENT", "ARK INIT PYMT", "TOTAL PROCESSED"));
	}

	public void noteUpdate(int affected) throws IOException {
		fixed("UPDATED ACT_DAILY_IND rows: " + affected);
	}


	@Override
	public void close() throws IOException {

		blank();
		blank();
		blank();
		blank();
		blank();
		blank();
		blank();
		fixed(String.format(" ****************************************************  END OF P09315-A REPORT  ***********"
				+ "*******************************************"));
		w.close();
	}

	// data-row format (kept as-is; fits exactly 133 chars)
	private static final String ROW_FMT4 = " %-28s %11s %13s %11s %13s %11s %13s %11s %13s";

	// helper: center text within a fixed width using spaces
	private static String centerInWidth(String s, int width) {
		if (s == null)
			s = "";
		if (s.length() >= width)
			return s.substring(0, width);
		int pad = (width - s.length()) / 2;
		int right = width - s.length() - pad;
		return " ".repeat(pad) + s + " ".repeat(right);
	}

	private void matrixBlock(String title, List<ActivityAggView> rows, List<String> actsOrder, List<String> colsCodes,
			List<String> colsLabels) throws IOException {

		// --- Header captions aligned to SAME columns as data ---
		// label area (28) + 4 blocks of 26 = 133 total (we add the leading space same
		// as ROW_FMT4)
		  String hdr = " "  // leading space like data rows
	               + padRight(title, 28)
	               + "     " + centerInWidth(colsLabels.get(0), 24)  // +2 spaces
	               + "   " + centerInWidth(colsLabels.get(1), 24)
	               + "   " + centerInWidth(colsLabels.get(2), 24)
	               + "" + centerInWidth(colsLabels.get(3), 24);
		fixed(hdr);

		// --- Numeric header aligned with data columns ---
		fixed(String.format(ROW_FMT4, "", "#", "AMOUNT", "#", "AMOUNT", "#", "AMOUNT", "#", "AMOUNT"));
		blank();

		// --- rest of your method unchanged ---
		Map<String, ActivityAggView> m = new HashMap<>();
		for (ActivityAggView a : rows)
			m.put(a.getActivity() + "|" + a.getRefundType(), a);

		long[] totalCnt = new long[4];
		BigDecimal[] totalAmt = { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO };

		for (String act : actsOrder) {
			String lbl = fit28(activityLabel(act));
			String[] c = new String[4];
			String[] a = new String[4];

			for (int i = 0; i < 4; i++) {
				 ActivityAggView ag =
			                m.getOrDefault(act + "|" + colsCodes.get(i), ActivityAggView.ZERO);
				
				totalCnt[i] += ag.getCount();
				totalAmt[i] = totalAmt[i].add(ag.getAmount());
				c[i] = CNT.format(ag.getCount());
				a[i] = AMT.format(ag.getAmount());
			}

			fixed(String.format(ROW_FMT4, lbl, c[0], a[0], c[1], a[1], c[2], a[2], c[3], a[3]));
		}

		String totLabel = title.contains("RECON") ? "TOT " + title
				: (title.contains("REQUEST") ? "TOT MANUAL REQUESTED ACTIVITY" : "TOTALS");

		fixed(String.format(ROW_FMT4, fit28(totLabel), CNT.format(totalCnt[0]), AMT.format(totalAmt[0]),
				CNT.format(totalCnt[1]), AMT.format(totalAmt[1]), CNT.format(totalCnt[2]), AMT.format(totalAmt[2]),
				CNT.format(totalCnt[3]), AMT.format(totalAmt[3])));

		if (title.contains("SYSTEM RECON")) {
			fixed(String.format(ROW_FMT4, fit28("TOTAL RECON ACTIVITY"), CNT.format(totalCnt[0]),
					AMT.format(totalAmt[0]), CNT.format(totalCnt[1]), AMT.format(totalAmt[1]), CNT.format(totalCnt[2]),
					AMT.format(totalAmt[2]), CNT.format(totalCnt[3]), AMT.format(totalAmt[3])));
		}

		blank();
		blank();
		blank();
	}

	private static String fit28(String s) {
		if (s == null)
			return "";
		if (s.length() <= 28)
			return s;
		// prefer semantic shortening
		String compressed = s.replace("REQUESTED", "REQUEST");
		if (compressed.length() <= 28)
			return compressed;
		// hard trim fallback
		return compressed.substring(0, 28);
	}

	private String activityLabel(String code) {
		return switch (code) {
		// Manual Recon (sample wording) :contentReference[oaicite:29]{index=29}
		case "ACC" -> "ACCEPTED MONEY";
		case "APP" -> "APPLIED TO A/R";
		case "REM" -> "CHECK REMAILED";
		case "DEL" -> "DELETED C/R";
		case "LOG" -> "LOGOUT TO SP ACCT";
		case "FRR" -> "FULL REISSUE REQUEST";
		case "PRR" -> "PARTIAL REISSUE REQUEST";

		// System Recon headings use “FULL REISSUE / PARTIAL REISSUE” already in sample
		// page-2 :contentReference[oaicite:30]{index=30}
		case "FR" -> "FULL REISSUE";
		case "PR" -> "PARTIAL REISSUE";
		case "TRA" -> "TOTAL RECON ACTIVITY";
//		case "TRAC" -> "TOTAL RECON ACTIVITY";

		// Manual Requested (sample wording) :contentReference[oaicite:31]{index=31}
		case "RAA" -> "REQUEST ACCEPT AMOUNT";
		case "RAD" -> "REQUEST ADJUSTMENT";
		case "RAR" -> "REQUEST APPLICATION";
		case "RCK" -> "REQUEST CHECK";
		case "OTH" -> "REQUEST OTHER ACTION";
		case "RRE" -> "REQUEST REMAIL OF CHECK";
		case "PEN" -> "PENDED FOR ERROR";
		case "CAN" -> "RET CHECK CANCELLED";
		case "MOD" -> "MODIFIED";
		default -> code;
		};
	}

	private void nextPage() throws IOException {
		page++;
		if (page > 1) {
			/* page break */ }
	}

	private void headerBanner() throws IOException {
		fixed(String.format(" REPORT #  P09315-A%63sPAGE%6s",
				"                             ARKANSAS BLUE CROSS AND BLUE SHIELD                                        ",
				page)); // :contentReference[oaicite:32]{index=32}
		String runDate = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yy"));
		String runTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("H:mm:ss"));
		fixed(String.format(" RUN DATE: %s%29sSUMMARY OF DAILY FINANCIAL ACTIVITY%32sRUN TIME: %s", runDate, "", "",
				runTime)); // :contentReference[oaicite:33]{index=33}

		String corpNameSplit[] = corpName.split("          ");
		fixed(String.format(" %s", corpNameSplit[1])); // :contentReference[oaicite:34]{index=34}
	}

	private void fixed(String s) throws IOException {
		if (s.length() > LINE_LEN)
			s = s.substring(0, LINE_LEN);
		else if (s.length() < LINE_LEN)
			s = s + " ".repeat(LINE_LEN - s.length());
		w.write(s);
		w.newLine();
	}

	private void blank() throws IOException {
		fixed("");
	}

	private static String padRight(String s, int n) {
		if (s == null)
			s = "";
		return s.length() >= n ? s.substring(0, n) : s + " ".repeat(n - s.length());
	}

	private static String repeat(char c, int n) {
		return String.valueOf(c).repeat(n);
	}

	private static String center(String s, int width, char fill) {
		int pad = Math.max(0, (width - s.length() - 4) / 2);
		return "**" + " ".repeat(pad) + s + " ".repeat(width - s.length() - pad - 4) + "**";
	}
}
