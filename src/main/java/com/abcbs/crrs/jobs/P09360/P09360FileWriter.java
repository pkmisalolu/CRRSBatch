package com.abcbs.crrs.jobs.P09360;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemWriter;

import com.abcbs.crrs.entity.ActivityPK;
import com.abcbs.crrs.entity.P09Activity;
import com.abcbs.crrs.repository.IActivityRepository;

public class P09360FileWriter implements ItemWriter<P09GlDedsRecord>, ItemStream {

	private static final Logger log = LogManager.getLogger(P09360FileWriter.class);
	private static final Charset CS = Charset.forName("ISO-8859-1");
	private static final int MAX_LINES_PER_PAGE = 55;

	private final String reportPath;
	private final IActivityRepository activityRepo;

	private BufferedWriter writer;
	private int currentLine = 0;
	private int currentPage = 0;
	private boolean dataWritten = false;

	// Break keys
	private String curRefundType = "";
	private String curRptKey = "";

	// Previous record fields for suppression logic
	private String prevRefundType = "";
	private int prevPage = -1; // track page number

	private String prevControlDate = "";
	private String prevControlNbr = "";
	private String prevActCode = "";
	private String prevActDate = "";
	private BigDecimal amt = BigDecimal.ZERO;

	// Suppression flags
	private boolean suppressRefundTypeField = false;
	private boolean suppressControlDateField = false;
	private boolean suppressControlNbrField = false;
	private boolean suppressActCodeField = false;
	private boolean suppressActDateField = false;
	private boolean suppressPatientNames = false;

	// Totals
	private int typeDateCount = 0;
	private BigDecimal typeDateTotal = BigDecimal.ZERO;
	private BigDecimal typeDateDebits = BigDecimal.ZERO;
	private BigDecimal typeDateCredits = BigDecimal.ZERO;

	private int typeCount = 0;
	private BigDecimal typeTotal = BigDecimal.ZERO;
	private BigDecimal typeDebits = BigDecimal.ZERO;
	private BigDecimal typeCredits = BigDecimal.ZERO;

	private int grandCount = 0;
	private BigDecimal grandTotal = BigDecimal.ZERO;
	private BigDecimal grandDebits = BigDecimal.ZERO;
	private BigDecimal grandCredits = BigDecimal.ZERO;

	public P09360FileWriter(String reportPath, IActivityRepository activityRepo) {
		this.reportPath = reportPath;
		this.activityRepo = activityRepo;
	}

	@Override
	public void open(ExecutionContext ctx) throws ItemStreamException {
		log.info("Opening report writer for path: {}", reportPath);
		try {
			File f = new File(reportPath);
			if (!f.getParentFile().exists()) {
				f.getParentFile().mkdirs();
			}
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), CS));
			log.info("Opened report: {}", f.getAbsolutePath());
			initialization();

		} catch (IOException e) {
			log.error("Failed to open report file for writing: {}", e.getMessage(), e);
			throw new ItemStreamException("Failed to open report", e);
		}
	}

	private void initialization() throws IOException {
		log.debug("Initializing report counters and amounts.");
		// Initialize all totals, flags, prev fields, break keys
		typeDateCount = 0;
		typeDateTotal = BigDecimal.ZERO;
		typeDateDebits = BigDecimal.ZERO;
		typeDateCredits = BigDecimal.ZERO;

		typeCount = 0;
		typeTotal = BigDecimal.ZERO;
		typeDebits = BigDecimal.ZERO;
		typeCredits = BigDecimal.ZERO;

		grandCount = 0;
		grandTotal = BigDecimal.ZERO;
		grandDebits = BigDecimal.ZERO;
		grandCredits = BigDecimal.ZERO;

		curRefundType = "";
		curRptKey = "";

		prevRefundType = "";
		prevPage = -1;

		prevControlDate = "";
		prevControlNbr = "";
		prevActCode = "";
		prevActDate = "";

		suppressRefundTypeField = false;
		suppressControlDateField = false;
		suppressControlNbrField = false;
		suppressActCodeField = false;
		suppressActDateField = false;
		suppressPatientNames = false;

		currentLine = 0;
		currentPage = 0;
		dataWritten = false;

	}

	@Override
	public void update(ExecutionContext ctx) {
	}

	@Override
	public void write(Chunk<? extends P09GlDedsRecord> chunk) throws Exception {

		log.debug("write() called with chunk size: {}", (chunk != null ? chunk.size() : "null"));
		try {
			// Step 3.1: if input file is empty
			if (chunk == null || chunk.isEmpty()) {
				log.warn("No input data – chunk is empty or null. Generating NO DATA section.");
				currentPage++;
				printHdgs(null);
				noDataLine();
				endOfReport();
				writer.close();
				writer = null; // Mark writer as closed
				return;
			}

			for (P09GlDedsRecord r : chunk) {
				// Step 3.2: print headers if first or page overflow
				if (!dataWritten || currentLine >= MAX_LINES_PER_PAGE) {
					log.debug("Starting new page: {}", currentPage);
					currentPage++;
					currentLine = 0;
					printHdgs(r);

				}
				processRecord(r);
			}
		} catch (Exception e) {
			log.error("Error while writing chunk: {}", e.getMessage(), e);
			throw e;
		}
	}

	@Override
	public void close() throws ItemStreamException {
		log.info("Closing report writer.");
		try {
			// Check if the writer is already closed
			if (writer == null) {
				log.warn("Writer already closed. Skipping close operations.");
				return;
			}

			if (!dataWritten) {
				log.warn("No data was written in report; writing NO DATA report.");
				currentPage++;
				printHdgs(null);
				noDataLine();
				endOfReport();
			} else {
				if (typeDateCount > 0) {
					log.info("Writing final type+date totals: refundType={}, reportDate={}", curRefundType, curRptKey);
					writeTypeAndDateTotal(curRefundType, curRptKey, typeDateCount, typeDateTotal, typeDateDebits,
							typeDateCredits);
				}
				if (typeCount > 0) {
					log.info("Writing final type totals for refundType={}", curRefundType);
					writeTypeTotal(curRefundType, typeCount, typeTotal, typeDebits, typeCredits);
				}
				currentPage++;
				log.info("Writing grand totals");
				writer.newLine();
				printHeaderA();
				printHeaderB();
				writeGrandTotals(grandCount, grandTotal, grandDebits, grandCredits);
				endOfReport();
			}

			writer.close();
			writer = null; // Mark writer as closed
			log.info("Report file closed successfully.");

		} catch (IOException e) {
			log.error("Error while closing writer: {}", e.getMessage(), e);
			throw new ItemStreamException("Error closing writer", e);
		}
	}

	private void processRecord(P09GlDedsRecord r) throws IOException {

		log.debug("Processing record: RefundType={}, ControlDate={}, ControlNbr={}, ActAmt={}", r.getGl_refund_type(),
				r.getGl_control_date(), r.getGl_control_nbr(), r.getGl_act_amt());
		try {
			dataWritten = true;
			log.info("Full Record: {}", r);
			// Build report date key
			String rptMo = (r.getGl_report_mo() != null ? r.getGl_report_mo().trim() : "");
			String rptYr = (r.getGl_report_yr() != null ? r.getGl_report_yr().trim() : "");
			String rptKey = rptMo + "/" + rptYr;

			boolean refundTypeChanged = !Objects.equals(curRefundType, r.getGl_refund_type());
			boolean rptDateChanged = !Objects.equals(curRptKey, rptKey);

			// Break on report‑date change (type+date totals)
			if (rptDateChanged && curRefundType != null && !curRefundType.isEmpty()) {
				log.info("Report date changed from {} to {}, writing type+date totals", curRptKey, rptKey);
				writeTypeAndDateTotal(curRefundType, curRptKey, typeDateCount, typeDateTotal, typeDateDebits,
						typeDateCredits);
				typeDateCount = 0;
				typeDateTotal = BigDecimal.ZERO;
				typeDateDebits = BigDecimal.ZERO;
				typeDateCredits = BigDecimal.ZERO;
				currentPage++;
				printHdgs(r);
			}

			// Break on refund type change (type totals)
			if (refundTypeChanged && curRefundType != null && !curRefundType.isEmpty()) {
				log.info("Refund type changed from {} to {}, writing type totals", curRefundType,
						r.getGl_refund_type());
				writeTypeAndDateTotal(curRefundType, curRptKey, typeDateCount, typeDateTotal, typeDateDebits,
						typeDateCredits);
				typeDateCount = 0;
				typeDateTotal = BigDecimal.ZERO;
				typeDateDebits = BigDecimal.ZERO;
				typeDateCredits = BigDecimal.ZERO;
				writeTypeTotal(curRefundType, typeCount, typeTotal, typeDebits, typeCredits);

				typeCount = 0;
				typeTotal = BigDecimal.ZERO;
				typeDebits = BigDecimal.ZERO;
				typeCredits = BigDecimal.ZERO;
				currentPage++;
				printHdgs(r);
			}

			/*
			 * // Suppression logic for both branches
			 * 
			 * // Suppression “5” logic: previous refund type + page match boolean
			 * prevRefundAndPageMatch = prevRefundType != null &&
			 * prevRefundType.equals(r.getGl_refund_type()) && prevPage == currentPage;
			 * 
			 * if (prevRefundAndPageMatch) { suppressRefundTypeField = true; log.
			 * debug("Suppressing refundType in detail line for record on page {}: prevRefundType matches"
			 * , currentPage); } else { suppressRefundTypeField = false; prevRefundType =
			 * r.getGl_refund_type(); prevPage = currentPage; }
			 * 
			 * // Suppression “6” logic: prev control date, nbr, act code, act date match
			 * boolean prevDetailMatch = prevControlDate != null &&
			 * prevControlDate.equals(r.getGl_control_date()) && prevControlNbr != null &&
			 * prevControlNbr.equals(r.getGl_control_nbr()) && prevActCode != null &&
			 * prevActCode.equals(r.getGl_act_code()) && prevActDate != null &&
			 * prevActDate.equals(r.getGl_act_date());
			 * 
			 * if (prevDetailMatch) { suppressControlDateField = true;
			 * suppressControlNbrField = true; suppressActCodeField = true;
			 * suppressActDateField = true; suppressPatientNames = true; log.debug(
			 * "Suppressing detail fields: controlDate, controlNbr, actCode, actDate, patient names for record"
			 * ); } else { suppressControlDateField = false; suppressControlNbrField =
			 * false; suppressActCodeField = false; suppressActDateField = false;
			 * suppressPatientNames = false;
			 * 
			 * prevControlDate = r.getGl_control_date(); prevControlNbr =
			 * r.getGl_control_nbr(); prevActCode = r.getGl_act_code(); prevActDate =
			 * r.getGl_act_date(); }
			 */
			// Branch on PER or non‑PER
			if ("PER".equals(r.getGl_refund_type())) {
				amt = toBigDecimalCents(r.getGl_act_amt());
				validationLogic(r.getGl_refund_type(), r.getGl_control_date(), r.getGl_control_nbr(),
						r.getGl_patient_ln(), r.getGl_patient_fn(), r.getGl_mbr_id_nbr(), r.getGl_act_code(),
						r.getGl_act_date(), amt, r.getGl_xref_type(), r.getGl_xref_date(), r.getGl_xref_claim_nbr());

			} else {
				List<P09Activity> acts = fetchActivitiesFor(r);
				 if (acts == null || acts.isEmpty()) {
			            log.debug("No matching activity found for control number: {}", r.getGl_control_nbr());
			            return; 
			        }


					for (P09Activity act : acts) {
						ActivityPK pk = act.getAId(); // composite key
						amt = act.getActActivityAmt().negate();
						validationLogic(r.getGl_refund_type(), pk.getCrCntrlDate().toString(), pk.getCrCntrlNbr(),
								r.getGl_patient_ln(), r.getGl_patient_fn(), r.getGl_mbr_id_nbr(), pk.getActActivity(),
								pk.getActActivityDate().toString(), amt, act.getActXrefType(),
								act.getActXrefDate() != null ? act.getActXrefDate().toString() : "",
								act.getActXrefNbr());
				}

			}

			log.info("act amount:" + amt);
			// Totals accumulation: same in both branches

			BigDecimal debit = BigDecimal.ZERO;
			BigDecimal credit = BigDecimal.ZERO;
			if (amt.compareTo(BigDecimal.ZERO) < 0) {
				credit = amt.abs();
			} else {
				debit = amt;
			}
			// log.info("Refund type amount {} ", amt);
			// accumulate for type + date
			typeDateCount++;
			typeDateTotal = typeDateTotal.add(amt);
			typeDateDebits = typeDateDebits.add(debit);
			typeDateCredits = typeDateCredits.add(credit);

			// accumulate for type
			typeCount++;
			typeTotal = typeTotal.add(amt);
			typeDebits = typeDebits.add(debit);
			typeCredits = typeCredits.add(credit);

			// accumulate grand
			grandCount++;
			grandTotal = grandTotal.add(amt);
			grandDebits = grandDebits.add(debit);
			grandCredits = grandCredits.add(credit);

			// Update break keys
			curRefundType = r.getGl_refund_type();
			curRptKey = rptKey;

		} catch (IOException e) {
			log.error("IOException in processRecord: {}", e.getMessage(), e);
			throw e;
		} catch (Exception e) {
			log.error("Unexpected exception in processRecord: {}", e.getMessage(), e);
			throw new IOException("Error in processRecord", e);
		}
	}

	// ===== COBOL 430000-RTRV-ACTIVITY + 420000-WRITE-RET-UND-RCD =====
	private List<P09Activity> fetchActivitiesFor(P09GlDedsRecord r) {

		try {
			LocalDate cntrlDate = parseDate(r.getGl_control_date());
			return activityRepo.findActivitiesForAcc(r.getGl_refund_type().trim(), cntrlDate,
					r.getGl_control_nbr().trim(), parseDate(r.getGl_act_date()));
		} catch (Exception e) {
			log.warn("Activity lookup failed for {}-{}-{} activityDate {} : {}", r.getGl_refund_type(),
					parseDate(r.getGl_control_date()), r.getGl_control_nbr(), parseDate(r.getGl_act_date()),
					e.toString());
			return Collections.emptyList();
		}
	}

	private void validationLogic(String refundType, String controlDate, String controlNbr, String patientLn,
			String patientFn, String mbrId, String actCode, String actDate, BigDecimal actAmt, String xrefType,
			String xrefDate, String xrefCalimNbr) {

		boolean prevRefundAndPageMatch = prevRefundType != null && prevRefundType.equals(refundType)
				&& prevPage == currentPage;

		if (prevRefundAndPageMatch) {
			suppressRefundTypeField = true;
			log.debug("Suppressing refundType in detail line for record on page {}: prevRefundType matches",
					currentPage);
		} else {
			suppressRefundTypeField = false;
			prevRefundType = refundType;
			prevPage = currentPage;
		}

		// Suppression “6” logic: prev control date, nbr, act code, act date match
		boolean prevDetailMatch = prevControlDate != null && prevControlDate.equals(controlDate)
				&& prevControlNbr != null && prevControlNbr.equals(controlNbr) && prevActCode != null
				&& prevActCode.equals(actCode) && prevActDate != null && prevActDate.equals(actDate);

		if (prevDetailMatch) {
			suppressControlDateField = true;
			suppressControlNbrField = true;
			suppressActCodeField = true;
			suppressActDateField = true;
			suppressPatientNames = true;
			log.debug("Suppressing detail fields: controlDate, controlNbr, actCode, actDate, patient names for record");
		} else {
			suppressControlDateField = false;
			suppressControlNbrField = false;
			suppressActCodeField = false;
			suppressActDateField = false;
			suppressPatientNames = false;

			prevControlDate = controlDate;
			prevControlNbr = controlNbr;
			prevActCode = actCode;
			prevActDate = actDate;
		}

		try {
			printDetail(refundType, controlDate, controlNbr, patientLn, patientFn, mbrId, actCode, actDate, actAmt,
					xrefType, xrefDate, xrefCalimNbr);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// After printing detail
		currentLine++;

	}

	private void printDetail(String refundType, String controlDate, String controlNbr, String patientLn,
			String patientFn, String mbrId, String actCode, String actDate, BigDecimal actAmt, String xrefType,
			String xrefDate, String xrefCalimNbr) throws IOException {
		// Format dates
		String ctrlDateFmt = formatDate(controlDate);
		String actDateFmt = formatDate(actDate);
		String xrefDateFmt = formatDate(xrefDate);

		// Amount formatting
		String amtStr = formatAmount(actAmt);

		// Prepare field values with suppression
		String refundTypeFld = suppressRefundTypeField ? "" : refundType;
		String controlDateFld = suppressControlDateField ? "" : ctrlDateFmt;
		String controlNbrFld = suppressControlNbrField ? "" : controlNbr;
		String patientLnFld = suppressPatientNames ? "" : patientLn;
		String patientFnFld = suppressPatientNames ? "" : patientFn;
		String memberIdFld = suppressPatientNames ? "" : mbrId;
		String actCodeFld = suppressActCodeField ? "" : actCode;
		String actDateFld = suppressActDateField ? "" : actDateFmt;

		// Print full or partial detail line
		// This aligns roughly with your sample:
		// REFUND TYPE, CONTROL DATE, CONTROL NUMBER, PATIENT LN, PATIENT FN, MEMBER ID,
		// CODE, ACT DATE, AMOUNT, XREF TYPE, XREF DATE, XREF NUMBER

		String line = String.format(" %-3s    %-8s  %-4s   %-14s %-11s  %-12s  %-3s  %-8s %16s  %-2s  %-8s %-20s%n",
				padRight(refundTypeFld, 3), padRight(controlDateFld, 8), padRight(controlNbrFld, 4),
				padRight(patientLnFld, 14), padRight(patientFnFld, 11), padRight(memberIdFld, 12),
				padRight(actCodeFld, 3), padRight(actDateFld, 8), padLeft(amtStr, 16), padRight(xrefType, 2),
				padRight(xrefDateFmt, 8), padRight(xrefCalimNbr, 20));
		try {
			writer.write(line);
			log.debug("Detail line written for member {} (refundType={}): {}", mbrId, refundType, line.trim());
		} catch (IOException e) {
			log.error("Failed to write detail line for record {}: {}", mbrId, e.getMessage(), e);
			throw e;
		}
	}

	private void printHdgs(P09GlDedsRecord r) throws IOException {
		try {
			printHeaderA();
			printHeaderB();
			String refundType = (r != null ? r.getGl_refund_type() : "");
			String reportDate = (r != null ? r.getGl_report_mo() + "/" + r.getGl_report_yr() : "");
			writer.write(String.format("REFUND TYPE: %-3s    REPORT DATE:  %5s%n", refundType, reportDate));
			writer.write(
					"REFUND       CONTROL                                   MEMBER        ACTIVITY         ACTIVITY             CROSS REFERENCE\n");
			writer.write(
					" TYPE     DATE   NUMBER       PATIENT'S NAME          ID NUMBER    CODE    DATE        AMOUNT    TYPE   DATE          NUMBER\n");
			writer.write(
					"------  -------- ------  --------------------------  ------------  ---- -------- ---------------- ---- -------- --------------------\n");
			currentLine = currentLine + 4;
		} catch (IOException e) {
			log.error("Error printing headers on page {}: {}", currentPage, e.getMessage(), e);
			throw e;
		}
	}

	private void printHeaderA() throws IOException {
		writer.write(String.format(
				"REPORT #  P09360-A                             ARKANSAS BLUE CROSS AND BLUE SHIELD%40sPAGE %5d%n", "",
				currentPage));
		currentLine++;
	}

	private void printHeaderB() throws IOException {
		String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yy"));
		String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
		writer.write(String.format(
				"RUN DATE: %s                               FEP  CRRS  JOURNAL ENTRY REPORT                                  RUN TIME: %s%n",
				dateStr, time));
		currentLine++;

	}

	private void noDataLine() throws IOException {
		try {
			writer.write(String.format("%-47s%-8s%-28s%n", "", "******  ", "NO  DATA  TO  REPORT  ******"));
			currentLine++;
			log.debug("Printed NO DATA line.");
		} catch (IOException e) {
			log.error("Error printing NO DATA line: {}", e.getMessage(), e);
			throw e;
		}
	}

	private void endOfReport() throws IOException {
		writer.newLine();
		writer.write(
				"****************************************************  END OF P09360-A REPORT  ******************************************************");
		currentLine = currentLine + 2;
	}

	private void writeTypeAndDateTotal(String refType, String rptDate, int cnt, BigDecimal total, BigDecimal deb,
			BigDecimal crd) throws IOException {
		log.info("Writing type+date total: RefundType={}, ReportDate={}, Count={}, Total={}, Debits={}, Credits={}",
				refType, rptDate, cnt, total, deb, crd);
		writer.newLine();
		writer.write(String.format("TOTAL FOR REFUND TYPE: %-3s      REPORT DATE:  %5s%n", refType, rptDate));
		writer.write(String.format("  C/R ACTIVITY    COUNT:  %5s                   AMOUNT:  %16s%n", formatCount(cnt),
				formatAmount(total)));
		writer.write(String.format("                  DEBITS: %15s         CREDITS: %16s%n", formatAmountNoSign(deb),
				formatAmount(crd)));
		currentLine = currentLine + 4;
	}

	private void writeTypeTotal(String refType, int cnt, BigDecimal total, BigDecimal deb, BigDecimal crd)
			throws IOException {
		log.info("Writing type total: RefundType={}, Count={}, Total={}, Debits={}, Credits={}", refType, cnt, total,
				deb, crd);
		writer.newLine();
		writer.write(String.format("TOTAL FOR REFUND TYPE: %-3s%n", refType));
		writer.write(String.format("  C/R ACTIVITY    COUNT:  %5s                   AMOUNT:  %16s%n", formatCount(cnt),
				formatAmount(total)));
		writer.write(String.format("                  DEBITS: %15s         CREDITS: %16s%n", formatAmountNoSign(deb),
				formatAmount(crd)));
		currentLine = currentLine + 4;
	}

	private void writeGrandTotals(int cnt, BigDecimal total, BigDecimal deb, BigDecimal crd) throws IOException {
		log.info("Writing grand totals: Count={}, Total={}, Debits={}, Credits={}", cnt, total, deb, crd);
		writer.newLine();
		writer.write(String.format("TOTAL C/R ACTIVITY     COUNT:  %6s                  AMOUNT: %16s%n",
				formatCount(cnt), formatAmount(total)));
		writer.newLine();
		writer.write(String.format(
				"                         TOTAL DEBITS:  %15s                        TOTAL CREDITS: %16s%n",
				formatAmountNoSign(deb), formatAmount(crd)));
		currentLine = currentLine + 4;
	}

	private String formatDate(String ccYYMMDD) {
		if (ccYYMMDD == null || ccYYMMDD.length() < 8) {
			return "";
		}
		// Expecting yyyyMMdd or yyyy-MM-dd or something close; adjust if your input is
		// different
		String year = ccYYMMDD.substring(0, 4);
		String month = ccYYMMDD.substring(5, 7);
		String day = ccYYMMDD.substring(8, 10);
		String yy = year.substring(2, 4);
		return month + "/" + day + "/" + yy;
	}

	/**
	 * Convert a numeric string (representing cents) into BigDecimal with 2
	 * decimals. Example: "1234" -> 12.34, "-567" -> -5.67
	 */
	public static BigDecimal toBigDecimalCents(String centsStr) {
		if (centsStr == null || centsStr.trim().isEmpty()) {
			return BigDecimal.ZERO;
		}

		try {
			long cents = Long.parseLong(centsStr.trim());
			return BigDecimal.valueOf(cents, 2); // scale = 2
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid numeric string for cents: '" + centsStr + "'", e);
		}
	}

	/**
	 * Format count (PIC Z(4)9) → suppress leading zeros Example: 00005 -> "5"
	 */
	public static String formatCount(long count) {
		return String.valueOf(count);
	}

	/**
	 * Format amount like PIC $$$$,$$$,$$9.99- Example: 1234 -> " 12.34" Example:
	 * -1234 -> " 12.34-"
	 */
	public static String formatAmount(BigDecimal amount) {
		DecimalFormat fmt = new DecimalFormat("$###,###,##0.00;$###,###,##0.00-");
		return fmt.format(amount);
	}

	/**
	 * Format amount like PIC $$$$,$$$,$$9.99 (no sign at end)
	 */
	public static String formatAmountNoSign(BigDecimal amount) {
		DecimalFormat fmt = new DecimalFormat("$###,###,##0.00");
		return fmt.format(amount.abs()); // drop sign
	}

	private String padRight(String s, int width) {
		if (s == null)
			s = "";
		if (s.length() >= width)
			return s.substring(0, width);
		return String.format("%-" + width + "s", s);
	}

	private String padLeft(String s, int width) {
		if (s == null)
			s = "";
		if (s.length() >= width)
			return s.substring(0, width);
		return String.format("%" + width + "s", s);
	}

	private LocalDate parseDate(String input) {
		try {
			return LocalDate.parse(input.trim());
		} catch (DateTimeParseException e) {
			// fallback or log error
			return LocalDate.now();
		}
	}

}
