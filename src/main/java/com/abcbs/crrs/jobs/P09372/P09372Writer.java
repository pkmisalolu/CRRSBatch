package com.abcbs.crrs.jobs.P09372;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.LocalDate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemWriter;

import com.abcbs.crrs.entity.P09CashReceipt;
import com.abcbs.crrs.entity.P09Suspense;
import com.abcbs.crrs.repository.IP09CashReceiptRepository;
import com.abcbs.crrs.repository.IP09SuspenseRepository;

/**
 * COBOL PROGRAM : P09372 PURPOSE : Offset letters + suspense/delete output
 *
 */
public class P09372Writer implements ItemWriter<P09CashReceipt>, ItemStream {
	private static final Logger log = LogManager.getLogger(P09372Writer.class);

	private static final int RECORD_LEN = 80;
	private BufferedWriter letterFile;
	private BufferedWriter letterXmlFile;
	private BufferedWriter deleteFile;
	private BufferedWriter suspenseFile;
	private BufferedWriter letterCntFile;

	private final String letterFileName;
	private final String letterXmlFileName;
	private final String deleteFileName;
	private final String suspenseFileName;
	private final String letterCntFileName;
	private boolean closed = false;

	private final IP09CashReceiptRepository cashRepo;
	private final IP09SuspenseRepository suspenseRepo;

	private int wsLetterCount = 0;
	private boolean wsSkipLetter;

	private static final String BLANK_LINE = pad("", 80);

	private static final String LETTER_OF1 = " <of1_letter>";
	private static final String LETTER_OF2 = " <of2_letter>";
	private static final String LETTER_OF3 = " <of3_letter>";
	private static final String LETTER_OF4 = " <of4_letter>";
	private static final String LETTER_OF5 = " <of5_letter>";

	private static final String LETTER_PARAGRAPH = " <paragraph>";
	private static final String LETTER_PARAGRAPH_END = " </paragraph>";
	private static final String LETTER_PARAGRAPH2 = " <paragraph2>";
	private static final String LETTER_PARAGRAPH_END2 = " </paragraph2>";

	private static final String[] MONTH_NAMES = { "JANUARY  ", "FEBRUARY ", "MARCH    ", "APRIL    ", "MAY      ",
			"JUNE     ", "JULY     ", "AUGUST   ", "SEPTEMBER", "OCTOBER  ", "NOVEMBER ", "DECEMBER " };

	private static final String LETTER_LINE_5 = "Therefore, we are returning your " + "voided check."
			+ "                                 ";

	private static final String LETTER_LINE_6 = "<closing>" + "Thank You" + "</closing>"
			+ "                                                   ";
	private static final String LETTER_LINE_7 = "Supervisor, Payments/Claims Refunds"
			+ "                                            ";
	private static final String LETTER_LINE_8 = "Corporate Accounting"
			+ "                                                           ";
	private static final String LETTER_LINE_9 = "Arkansas Blue Cross and Blue Shield"
			+ "                                            ";
	private static final String LETTER_LINE_10 = "Sherry Cole"
			+ "                                                                    ";
	private static final String LETTER_LINE_11 = "501.399.3952"
			+ "                                                                   ";
	private String L_LETTER_REASON;

	static final String LETTER_REASON_1 = "Our research indicates that your "
			+ "overpayment will be satisfied through our" + "     ";

	static final String LETTER_REASON_1A = "automatic offset process."
			+ "                                                      ";

	static final String LETTER_REASON_2 = "Our research indicates that your "
			+ "overpayment was offset prior to receiving" + " your ";

	static final String LETTER_REASON_2A = "refund."
			+ "                                                                         ";

	static final String LETTER_REASON_3 = "Our research indicates that your "
			+ "overpayment has already been received.";

	private static final String LETTER_REASON_4 = "Our research indicates that your payment was"
			+ " sent to Arkansas Blue Cross Blue ";

	private static final String LETTER_REASON_4A = "Shield in error.";

	private static final String LETTER_REASON_4B = "Please refer to your Personal Health Stateme"
			+ "nt; the attached payment may need ";

	private static final String LETTER_REASON_4C = "to be sent to your provider of service.";

	private static final String LETTER_REASON_5 = "Our research indicates that your overpayment"
			+ " was sent to the incorrect Blue   ";

	private static final String LETTER_REASON_5A = "Cross Plan.     ";

	private static final String LETTER_REASON_5B = "Please forward overpayment to correct"
			+ " plan at the address below:               ";

	private static final String LETTER_REASON_6 = "If you have any questions or need "
			+ "additional information, please contact ";

	private static final String LETTER_REASON_6A = "Arkansas Blue Cross Blue Shield.";

	private static final String LETTER_REASON_6B = "your provider.";
	private static final String L_DEAR_LINE = "Please find enclosed your check in the amount of";

	private static final String L_DEAR_LINE_2 = "Please find enclosed your voided check in the ";

	private static final String L_DEAR_LINE_2A = "amount of";

	private static final String PERIOD = ".";

	private static final String PAYEE_LINE_2_P = padRight("0Dear Sir or Madam:", 80);

	private static final String PAYEE_LINE_3_P = padRight("0Dear Payee:", 80);

	private static final String PAYEE_LINE_2 = padRight(" <greeting>Dear Sir or Madam:</greeting>", 80);

	private static final String PAYEE_LINE_3 = padRight(" <greeting>Dear Payee:</greeting>", 80);

	private static String L_DATE_P;
	private static String L_DATE_1_P;

	private static String L_DATE;
	private static String L_DATE_1;
	private static String L_PAYEE_NAME_1_P;

	private static String L_PAYEE_ADDRESS_P;
	private static String L_PAYEE_ADDRESS_2_P;

	private static String L_ADDRESS_P;
	private static String L_PATIENT_NAME_P;
	private static String L_CHECK_NBR_P;

	public P09372Writer(String letterFile, String letterXml, String deleteFile, String suspenseFile,
			String letterCntFile, IP09CashReceiptRepository cashRepo, IP09SuspenseRepository suspenseRepo) {
		this.letterFileName = letterFile;
		this.letterXmlFileName = letterXml;
		this.deleteFileName = deleteFile;
		this.suspenseFileName = suspenseFile;
		this.letterCntFileName = letterCntFile;
		this.cashRepo = cashRepo;
		this.suspenseRepo = suspenseRepo;

		log.info("P09372Writer initialized with files: letter={}, xml={}, delete={}, suspense={}, cnt={}", letterFile,
				letterXml, deleteFile, suspenseFile, letterCntFile);
	}

	@Override
	public void open(ExecutionContext ctx) throws ItemStreamException {
		log.info("Opening writers for job execution context: {}", ctx);
		try {
			letterFile = Files.newBufferedWriter(Path.of(letterFileName));
			letterXmlFile = Files.newBufferedWriter(Path.of(letterXmlFileName));
			deleteFile = Files.newBufferedWriter(Path.of(deleteFileName));
			suspenseFile = Files.newBufferedWriter(Path.of(suspenseFileName));
			letterCntFile = Files.newBufferedWriter(Path.of(letterCntFileName));
			log.debug("Successfully opened all output files");
		} catch (IOException e) {
			log.error("Failed to open output files", e);
			throw new ItemStreamException("OPEN failed", e);
		}
	}

	@Override
	public void write(Chunk<? extends P09CashReceipt> chunk) throws Exception {

		log.info("Writing chunk of size {}", chunk.size());
		for (P09CashReceipt cash : chunk) {
			log.debug("Processing cash receipt: id={}, checkNbr={}, amount={}", cash.getCrId(), cash.getCrCheckNbr(),
					cash.getCrCheckAmt());

			wsSkipLetter = false;

			createLetter(letterFile, letterXmlFile, cash);

			writeXml(letterXmlFile, endLetterTag(cash.getCrRemittorTitle()));
			writeBlank(letterXmlFile);
			wsLetterCount++;
			log.debug("Letter count incremented to {}", wsLetterCount);

			if (!wsSkipLetter) {
				updateOffsetCode(cash);
				processSuspenseAndDelete(cash);
				log.debug("Processed suspense/delete for receipt {}", cash.getCrId());

			}
		}
	}

	private void createLetter(BufferedWriter letterWriter, BufferedWriter xmlWriter, P09CashReceipt cash)
			throws IOException {
		log.info("Creating letter for remittorTitle={}, remittorName={}", cash.getCrRemittorTitle(),
				cash.getCrRemittorName());

		switch (cash.getCrRemittorTitle()) {
		case "OF1" -> {
			writeXml(xmlWriter, LETTER_OF1);
			writeBlank(xmlWriter);
		}
		case "OF2" -> {
			writeXml(xmlWriter, LETTER_OF2);
			writeBlank(xmlWriter);
		}
		case "OF3" -> {
			writeXml(xmlWriter, LETTER_OF3);
			writeBlank(xmlWriter);
		}
		case "OF4" -> {
			writeXml(xmlWriter, LETTER_OF4);
			writeBlank(xmlWriter);
		}
		case "OF5" -> {
			writeXml(xmlWriter, LETTER_OF5);
			writeBlank(xmlWriter);
		}
		}

		LocalDate d = LocalDate.now();
		log.debug("Using current system date: {}", d);

		String month = MONTH_NAMES[d.getMonthValue() - 1].trim();
		int day = d.getDayOfMonth();
		int year = d.getYear();

		String date;
		date = month + " " + day + ", " + year;

		L_DATE_P = pad(date, 18);
		L_DATE_1_P = pad("1" + L_DATE_P, 80);

		writeLetter(letterWriter, L_DATE_1_P);

		L_DATE = pad(date, 18);
		L_DATE_1 = pad(" <date>" + L_DATE + "</date>", 80);
		writeXml(xmlWriter, L_DATE_1);

		writeBlank(xmlWriter);
		writeBlank(xmlWriter);

		L_PAYEE_NAME_1_P = pad(cash.getCrRemittorName(), 36);
		writeLetter(letterWriter, pad("0" + L_PAYEE_NAME_1_P, 80));
		writeXml(xmlWriter, pad(" <name>" + L_PAYEE_NAME_1_P + "</name>", 80));

		if (!isBlank(cash.getCrRemAddress1())) {
			L_PAYEE_ADDRESS_P = pad(cash.getCrRemAddress1(), 36);
			writeLetter(letterWriter, pad(" " + L_PAYEE_ADDRESS_P, 80));
			writeXml(xmlWriter, pad(" <addr1>" + L_PAYEE_ADDRESS_P + "</addr1>", 80));
		} else if (!isBlank(cash.getCrChkAddress1())) {
			L_PAYEE_ADDRESS_P = pad(cash.getCrChkAddress1(), 36);
			writeLetter(letterWriter, pad(" " + L_PAYEE_ADDRESS_P, 80));
			writeXml(xmlWriter, pad(" <addr1>" + L_PAYEE_ADDRESS_P + "</addr1>", 80));
		}

		if (!isBlank(cash.getCrRemAddress2())) {
			L_PAYEE_ADDRESS_2_P = pad(cash.getCrRemAddress2(), 36);
			writeLetter(letterWriter, pad(" " + L_PAYEE_ADDRESS_2_P, 80));
			writeXml(xmlWriter, pad(" <addr2>" + L_PAYEE_ADDRESS_2_P + "</addr2>", 80));
		} else if (isBlank(cash.getCrRemAddress1()) && isBlank(cash.getCrRemAddress2())
				&& !isBlank(cash.getCrChkAddress2())) {
			L_PAYEE_ADDRESS_2_P = pad(cash.getCrChkAddress2(), 36);
			writeLetter(letterWriter, pad(" " + L_PAYEE_ADDRESS_2_P, 80));
			writeXml(xmlWriter, pad(" <addr2>" + L_PAYEE_ADDRESS_2_P + "</addr2>", 80));
		}

		String city = isBlank(cash.getCrRemCity()) ? cash.getCrChkCity() : cash.getCrRemCity();
		String state = isBlank(cash.getCrRemState()) ? cash.getCrChkState() : cash.getCrRemState();
		String zip = isBlank(cash.getCrRemZip5()) ? cash.getCrChkZip5() : cash.getCrRemZip5();
		String cityP = pad(city, 24);
		char pos24 = cityP.charAt(23); // L-24
		char pos23 = cityP.charAt(22); // L-23
		String L_ST_P = pad(state, 2); // PIC X(2)
		String L_ZIP_P = pad(zip, 5); // PIC X(5)

		if (pos24 == ' ' && pos23 != ' ') {

			String L_CITY_P = cityP; // PIC X(24)
			writeBlank(letterWriter);
			writeLetter(letterWriter, pad(" " + cityP + " " + L_ST_P + " " + L_ZIP_P, 80));
;
			writeXml(xmlWriter, pad(" <addr3>" + L_CITY_P + " " + L_ST_P + " " + L_ZIP_P + "</addr3>", 80));

		} else {
			String address = cobolDelimitedByTwoSpaces(cityP) + " " + L_ST_P + " " + L_ZIP_P;
			L_ADDRESS_P = pad(address, 35);
			writeBlank(letterWriter);
			writeLetter(letterWriter, pad(" " + L_ADDRESS_P, 80));

			writeXml(xmlWriter, pad(" <addr3>" + L_ADDRESS_P + "</addr3>", 80));
		}
		/*
		 * L_ADDRESS_P = pad(city + " " + state + " " + zip, 35);
		 * writeLetter(letterWriter, pad(" " + L_ADDRESS_P, 80)); writeXml(xmlWriter,
		 * pad(" <addr3>" + L_ADDRESS_P + "</addr3>", 80));
		 */

		writeBlank(xmlWriter);
		writeBlank(xmlWriter);

		if ("OF4".equals(cash.getCrRemittorTitle()) || "OF5".equals(cash.getCrRemittorTitle())) {
			writeLetter(letterWriter, PAYEE_LINE_3_P);
			writeXml(xmlWriter, PAYEE_LINE_3);
		} else {
			writeLetter(letterWriter, PAYEE_LINE_2_P);
			writeXml(letterWriter, PAYEE_LINE_2);
		}

		writeBlank(xmlWriter);

		String patient = cash.getCrPatientFname().trim() + " " + cash.getCrPatientLname().trim();

		L_PATIENT_NAME_P = transformPatient(patient);
		writeBlank(xmlWriter);
		writeBlank(xmlWriter);

		writeLetter(letterWriter, pad("0" + "RE: " + L_PATIENT_NAME_P, 80));
		writeXml(xmlWriter, pad(" <patient>RE: " + pad(patient, 25) + "</patient>", 80));

		L_CHECK_NBR_P = pad(cash.getCrCheckNbr(), 8);

		writeLetter(letterWriter, pad("     Check # " + L_CHECK_NBR_P, 80));
		writeXml(xmlWriter, pad(" <checknbr>    Check # " + L_CHECK_NBR_P + "</checknbr>", 80));

		writeBlank(xmlWriter);
		writeXml(xmlWriter, LETTER_PARAGRAPH);

		if ("OF4".equals(cash.getCrRemittorTitle()) || "OF5".equals(cash.getCrRemittorTitle())) {

			createDearLine2(letterWriter, xmlWriter, cash.getCrCheckAmt());

		} else {

			createDearLine1(letterWriter, xmlWriter, cash.getCrCheckAmt());
		}

		switch (cash.getCrRemittorTitle()) {

		case "OF1" -> {
			L_LETTER_REASON = LETTER_REASON_1;

			writeBlank(xmlWriter);
			writeBlank(xmlWriter);

			writeLetter(letterWriter, "0" + L_LETTER_REASON);
			writeXml(xmlWriter, buildLetterLine4());

			L_LETTER_REASON = LETTER_REASON_1A;

			writeLetter(letterWriter, " " + L_LETTER_REASON);
			writeXml(xmlWriter, buildLetterLine4());

			createLetter2(letterWriter, xmlWriter);
		}

		case "OF2" -> {
			L_LETTER_REASON = LETTER_REASON_2;

			writeBlank(xmlWriter);
			writeBlank(xmlWriter);

			writeLetter(letterWriter, "0" + L_LETTER_REASON);
			writeXml(xmlWriter, buildLetterLine4());

			L_LETTER_REASON = LETTER_REASON_2A;

			writeLetter(letterWriter, " " + L_LETTER_REASON);
			writeXml(xmlWriter, buildLetterLine4());

			createLetter2(letterWriter, xmlWriter);
		}

		case "OF3" -> {
			L_LETTER_REASON = LETTER_REASON_3;

			writeBlank(xmlWriter);
			writeBlank(xmlWriter);

			writeLetter(letterWriter, "0" + L_LETTER_REASON);
			writeXml(xmlWriter, buildLetterLine4());

			createLetter2(letterWriter, xmlWriter);
		}

		case "OF4" -> createLetter3(letterWriter, xmlWriter);

		case "OF5" -> createLetter4(letterWriter, xmlWriter, cash);
		}

		writeXml(xmlWriter, LETTER_PARAGRAPH_END2);
		log.info("Letter creation completed for receipt {}", cash.getCrId());

	}

	private void createDearLine1(BufferedWriter letterWriter, BufferedWriter xmlWriter, BigDecimal bigDecimal) {
		log.debug("Entering createDearLine1 with checkAmount={} ", bigDecimal);
		try {

			String amt = extractAmountByCtr(bigDecimal);

			String money = L_DEAR_LINE + " " + amt + PERIOD;

			String letterLine3 = buildLetterLine3(money);

			writeBlank(xmlWriter);
			writeBlank(xmlWriter);

			writeLetter(letterWriter, "0" + L_DEAR_LINE + " " + amt + PERIOD);

			writeXml(xmlWriter, letterLine3);
			log.info("Successfully created DearLine1 for amount={}", amt);
		} catch (Exception e) {
			log.error("Unexpected error in createDearLine1 for checkAmount={}", bigDecimal, e);
			throw new RuntimeException("Unexpected error in createDearLine1", e);
		}

	}

	private void createDearLine2(BufferedWriter letterWriter, BufferedWriter xmlWriter, BigDecimal bigDecimal) {
		log.debug("Entering createDearLine2 with checkAmount={}", bigDecimal);
		try {

			String amt = extractAmountByCtr(bigDecimal);

			String money = L_DEAR_LINE_2 + L_DEAR_LINE_2A + " " + amt + PERIOD;

			String letterLine3 = buildLetterLine3(money);

			writeBlank(xmlWriter);
			writeBlank(xmlWriter);

			writeLetter(letterWriter, "0" + L_DEAR_LINE_2 + L_DEAR_LINE_2A + " " + amt + PERIOD);

			writeXml(xmlWriter, letterLine3);
			log.info("Successfully created DearLine2 for amount={}", amt);
		} catch (Exception e) {
			log.error("Unexpected error in createDearLine2 for checkAmount={} ", bigDecimal, e);
			throw new RuntimeException("Unexpected error in createDearLine2", e);
		}

	}

	private void createLetter2(BufferedWriter letterWriter, BufferedWriter xmlWriter) {
		log.debug("Entering createLetter2");
		try {

			writeXml(xmlWriter, BLANK_LINE);
			writeXml(xmlWriter, BLANK_LINE);


			writeLetter(letterWriter, "0" + LETTER_LINE_5);
			writeXml(xmlWriter, " " + LETTER_LINE_5);

			writeLetter(letterWriter, LETTER_PARAGRAPH_END);
			writeXml(xmlWriter, LETTER_PARAGRAPH_END);

			writeXml(xmlWriter, BLANK_LINE);
			writeXml(xmlWriter, BLANK_LINE);
			writeXml(xmlWriter, BLANK_LINE);

			writeLetter(letterWriter, "-" + LETTER_LINE_6);
			writeXml(xmlWriter, " " + LETTER_LINE_6);

			writeXml(xmlWriter, BLANK_LINE);
			writeXml(xmlWriter, BLANK_LINE);
			writeXml(xmlWriter, BLANK_LINE);

			writeXml(xmlWriter, LETTER_PARAGRAPH2);

			writeLetter(letterWriter, "-" + LETTER_LINE_10);
			writeXml(xmlWriter, " " + LETTER_LINE_10);

			writeLetter(letterWriter, " " + LETTER_LINE_7);
			writeXml(xmlWriter, " " + LETTER_LINE_7);

			writeLetter(letterWriter, " " + LETTER_LINE_8);
			writeXml(xmlWriter, " " + LETTER_LINE_8);

			writeLetter(letterWriter, " " + LETTER_LINE_9);
			writeXml(xmlWriter, " " + LETTER_LINE_9);

			writeLetter(letterWriter, " " + LETTER_LINE_11);
			writeXml(xmlWriter, " "+LETTER_LINE_11);
			log.info("Successfully created Letter2 block");
		} catch (Exception e) {
			log.error("Unexpected error in createLetter2", e);
			throw new RuntimeException("Unexpected error in createLetter2", e);
		}

	}

	private void createLetter3(BufferedWriter letterWriter, BufferedWriter xmlWriter) {
		log.debug("Entering createLetter3");
		try {

			writeBlank(xmlWriter);
			writeBlank(xmlWriter);

			String line4 = buildLetterLine4(LETTER_REASON_4);
			writeLetter(letterWriter, "0" + LETTER_REASON_4);
			writeXml(xmlWriter, line4);

			line4 = buildLetterLine4(LETTER_REASON_4A);
			writeLetter(letterWriter, " " + LETTER_REASON_4A);
			writeXml(xmlWriter, line4);

			writeBlank(xmlWriter);
			writeBlank(xmlWriter);

			line4 = buildLetterLine4(LETTER_REASON_4B);
			writeLetter(letterWriter, "0" + LETTER_REASON_4B);
			writeXml(xmlWriter, line4);

			line4 = buildLetterLine4(LETTER_REASON_4C);
			writeLetter(letterWriter, " " + LETTER_REASON_4C);
			writeXml(xmlWriter, line4);

			writeBlank(xmlWriter);
			writeBlank(xmlWriter);

			line4 = buildLetterLine4(LETTER_REASON_6);
			writeLetter(letterWriter, "0" + LETTER_REASON_6);
			writeXml(xmlWriter, line4);

			line4 = buildLetterLine4(LETTER_REASON_6B);
			writeLetter(letterWriter, " " + LETTER_REASON_6B);
			writeXml(xmlWriter, line4);

			writeBlank(xmlWriter);
			writeBlank(xmlWriter);
			writeBlank(xmlWriter);
			writeLetter(letterWriter, LETTER_PARAGRAPH_END);
			writeXml(xmlWriter, LETTER_PARAGRAPH_END);

			writeLetter(letterWriter, "-" + LETTER_LINE_6);
			writeXml(xmlWriter, " " + LETTER_LINE_6);

			writeBlank(xmlWriter);
			writeBlank(xmlWriter);
			writeBlank(xmlWriter);
			writeXml(xmlWriter, LETTER_PARAGRAPH2);

			writeLetter(letterWriter, "-" + LETTER_LINE_10);
			writeXml(xmlWriter, " " + LETTER_LINE_10);

			writeLetter(letterWriter, " " + LETTER_LINE_7);
			writeXml(xmlWriter, " " + LETTER_LINE_7);

			writeLetter(letterWriter, " " + LETTER_LINE_8);
			writeXml(xmlWriter, " " + LETTER_LINE_8);

			writeLetter(letterWriter, " " + LETTER_LINE_9);
			writeXml(xmlWriter, " " + LETTER_LINE_9);

			writeLetter(letterWriter, " " + LETTER_LINE_11);
			writeXml(xmlWriter, " " + LETTER_LINE_11);
			log.info("Successfully created Letter3 block");
		} catch (Exception e) {
			log.error("Unexpected error in createLetter3", e);
			throw new RuntimeException("Unexpected error in createLetter3", e);
		}
	}

	private void createLetter4(BufferedWriter letterWriter, BufferedWriter xmlWriter, P09CashReceipt cash) {
		log.debug("Entering createLetter4 for receipt id={}", cash.getCrId());
		try {
			P09Suspense suspense = retrieveSuspense(cash);

			if (suspense == null) {
				log.warn("No suspense record found for receipt id={}", cash.getCrId());
				wsSkipLetter = true;
				writeBlank(xmlWriter);
				return;
			}
			wsSkipLetter = false;

			String planName = "";
			String planAddress = "";
			String planCityStZip = "";

			String comment = suspense.getCmtCommentText();
			if (comment != null) {
				String[] parts = comment.split(",", -1);
				if (parts.length > 0)
					planName = parts[0].trim();
				if (parts.length > 1)
					planAddress = parts[1].trim();
				if (parts.length > 2)
					planCityStZip = parts[2].trim();
			}

			writeLetter(letterWriter, " " + LETTER_REASON_5);
			writeXml(xmlWriter, buildLetterLine4(LETTER_REASON_5));

			writeLetter(letterWriter, " " + LETTER_REASON_5A);
			writeXml(xmlWriter, buildLetterLine4(LETTER_REASON_5A));

			writeLetter(letterWriter, "0" + LETTER_REASON_5B);
			writeXml(xmlWriter, buildLetterLine4(LETTER_REASON_5B));

			writeLetter(letterWriter, buildLetterLine4WithValue(planName, 30));
			writeXml(xmlWriter, buildLetterLine4WithValue(planName, 30));

			writeLetter(letterWriter, buildLetterLine4WithValue(planAddress, 25));
			writeXml(xmlWriter, buildLetterLine4WithValue(planAddress, 25));

			writeLetter(letterWriter, buildLetterLine4WithValue(planCityStZip, 35));
			writeXml(xmlWriter, buildLetterLine4WithValue(planCityStZip, 35));

			writeBlank(xmlWriter);
			writeBlank(xmlWriter);

			writeLetter(xmlWriter, "0" + LETTER_REASON_6);
			writeXml(xmlWriter, buildLetterLine4(LETTER_REASON_6));

			writeLetter(letterWriter, " " + LETTER_REASON_6A);
			writeXml(xmlWriter, buildLetterLine4(LETTER_REASON_6A));

			writeBlank(xmlWriter);
			writeBlank(xmlWriter);
			writeBlank(xmlWriter);
			writeLetter(letterWriter, LETTER_PARAGRAPH_END);
			writeXml(xmlWriter, LETTER_PARAGRAPH_END);

			writeLetter(letterWriter, "-" + LETTER_LINE_6);
			writeXml(xmlWriter, " " + LETTER_LINE_6);

			writeBlank(xmlWriter);
			writeBlank(xmlWriter);
			writeBlank(xmlWriter);
			writeXml(xmlWriter, LETTER_PARAGRAPH2);

			writeLetter(letterWriter, "-" + LETTER_LINE_10);
			writeXml(xmlWriter, " " + LETTER_LINE_10);

			writeLetter(letterWriter, " "+ LETTER_LINE_7);
			writeXml(xmlWriter, " " + LETTER_LINE_7);

			writeLetter(letterWriter, " " + LETTER_LINE_8);
			writeXml(xmlWriter, " " + LETTER_LINE_8);

			writeLetter(letterWriter, " " + LETTER_LINE_9);
			writeXml(xmlWriter, " " + LETTER_LINE_9);

			writeLetter(letterWriter, " " + LETTER_LINE_11);
			writeXml(xmlWriter, " " + LETTER_LINE_11);
			log.info("Successfully created Letter4 block for receipt id={}", cash.getCrId());
		} catch (Exception e) {
			log.error("Unexpected error in createLetter4 for receipt id={}", cash.getCrId(), e);
			throw new RuntimeException("Unexpected error in createLetter4", e);
		}
	}

	private P09Suspense retrieveSuspense(P09CashReceipt cash) {
		log.debug("Retrieving suspense for refundType={}, cntrlDate={}, cntrlNbr={}", cash.getCrId().getCrRefundType(),
				cash.getCrId().getCrCntrlDate(), cash.getCrId().getCrCntrlNbr());
		try {

			return suspenseRepo.findSuspense(cash.getCrId().getCrRefundType(), cash.getCrId().getCrCntrlDate(),
					cash.getCrId().getCrCntrlNbr());
		} catch (Exception e) {
			log.error("Error retrieving suspense for receipt id={}", cash.getCrId(), e);
			throw new RuntimeException("Suspense retrieval failed", e);
		}

	}

	private void processSuspenseAndDelete(P09CashReceipt cash) throws IOException {

		String refundType = cash.getCrId().getCrRefundType();
		LocalDate cntrlDate = cash.getCrId().getCrCntrlDate();
		String cntrlNbr = cash.getCrId().getCrCntrlNbr();

		log.info("240000-GET-DELETE-FIELDS start → refundType='{}'(len={}), cntrlDate='{}', cntrlNbr='{}'(len={})",
				refundType, refundType == null ? 0 : refundType.length(), cntrlDate, cntrlNbr,
				cntrlNbr == null ? 0 : cntrlNbr.length());

		P09Suspense s = suspenseRepo.findSuspense(refundType, cntrlDate, cntrlNbr);

		if (s == null) {

			log.info("******************************************");
			log.info("**  NO SUSPENSE RECORD FOUND FOR DELETE  **");
			log.info("**  REFUND TYPE  = {} (len={})", refundType, refundType == null ? 0 : refundType.length());
			log.info("**  CNTRL DATE   = {}", cntrlDate);
			log.info("**  CNTRL NBR    = {} (len={})", cntrlNbr, cntrlNbr == null ? 0 : cntrlNbr.length());
			log.info("******************************************");

			return;
		}
		writeSuspenseFile(s);
		writeDeleteFile(s);

		log.info("GET-DELETE-FIELDS completed OK");
	}

	private void writeSuspenseFile(P09Suspense s) throws IOException {
		log.debug("Entering writeSuspenseFile for suspense id={}", s.getSpId());
		try {
			String record = SuspenseFormatter.format(s);
			log.info("WRITE-SUSPENSE-FILE length={}", record.length());
			suspenseFile.write(record);
			suspenseFile.newLine();
			log.debug("Suspense record written successfully");
		} catch (IOException e) {
			log.error("ERROR writing suspense file record", e);
			throw new RuntimeException("WRITE-SUSPENSE-FILE FAILED", e);
		}

	}

	private void writeDeleteFile(P09Suspense s) throws IOException {
		log.debug("Entering writeDeleteFile for suspense id={}", s.getSpId());
		try {
			String record = formatDeleteRecord(s);
			log.info("WRITE-DELETE-FILE length={}", record.length());
			deleteFile.write(record);
			deleteFile.newLine();
			log.debug("Delete record written successfully");
		} catch (IOException e) {
			log.error("ERROR writing delete file record", e);
			throw new RuntimeException("WRITE-DELETE-FILE FAILED", e);
		}

	}

	private String formatDeleteRecord(P09Suspense s) {

		String value = s.getSpId().getCrRefundType() + s.getBtBatchPrefix() + s.getBtBatchDate() + s.getBtBatchSuffix();

		log.info("DELETE-FILE value='{}' len={}", value, value.length());

		return value;
	}

	private void updateOffsetCode(P09CashReceipt cash) {

		String refundType = cash.getCrId().getCrRefundType();
		LocalDate cntrlDate = cash.getCrId().getCrCntrlDate();
		String cntrlNbr = cash.getCrId().getCrCntrlNbr();

		log.info("UPDATE-OFFSET-CODE input → refundType='{}' (len={}), cntrlDate='{}', cntrlNbr='{}' (len={})",
				refundType, refundType == null ? 0 : refundType.length(), cntrlDate, cntrlNbr,
				cntrlNbr == null ? 0 : cntrlNbr.length());

		int rows = cashRepo.clearRemittorTitle(refundType, cntrlDate, cntrlNbr);

		log.info("UPDATE-OFFSET-CODE rowsUpdated={}", rows);

		if (rows != 1) {
			log.error("UPDATE-OFFSET-CODE FAILED ❌");
			throw new RuntimeException("UPDATE-OFFSET-CODE failed, rows=" + rows);
		}
	}

	private void writeLetter(BufferedWriter w, String line) {
		try {
			w.write(pad(line, RECORD_LEN));
			w.newLine();
		} catch (IOException e) {
			log.error("ERROR writing LETTER record", e);
			throw new RuntimeException("LETTER WRITE FAILED", e);
		}
	}

	private void writeXml(BufferedWriter w, String line) {
		try {
			w.write(pad(line, RECORD_LEN));
			w.newLine();
		} catch (IOException e) {
			log.error("ERROR writing LETTER XML record", e);
			throw new RuntimeException("LETTER XML WRITE FAILED", e);
		}
	}

	private static void writeBlank(BufferedWriter writer) throws IOException {
		writer.write(BLANK_LINE);
		writer.newLine();
	}

	private String buildLetterLine4(String reason) {
		return pad(" " + reason, 80);
	}

	private String buildLetterLine4WithValue(String value, int valueLength) {
		String content = pad("               " + value, 79);
		return pad(" " + content, 80);
	}


	private String extractAmountByCtr(BigDecimal bigDecimal) {
		if (bigDecimal == null) {
			return "$0.00";
		}

		DecimalFormat df = new DecimalFormat("$###,###,##0.00");
		String amt = df.format(bigDecimal);

		return amt.strip();
	}

	private String buildLetterLine3(String moneyText) {
		String line = " " + moneyText;
		return pad(line, 80);
	}

	private String buildLetterLine4() {
		return pad(" " + L_LETTER_REASON, 80);
	}

	private static String pad(String s, int len) {
		if (s == null)
			s = "";
		if (s.length() >= len)
			return s.substring(0, len);
		return String.format("%-" + len + "s", s);
	}

	private String endLetterTag(String of) {
		return " </" + of.toLowerCase() + "_letter>";
	}

	private static String padRight(String s, int len) {
		if (s.length() >= len) {
			return s.substring(0, len);
		}
		return s + " ".repeat(len - s.length());
	}

	private static String padLeftZeros(String s, int len) {
		if (s == null) {
			return "0".repeat(len);
		}
		if (s.length() >= len) {
			return s.substring(0, len);
		}
		return "0".repeat(len - s.length()) + s;
	}

	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	@Override
	public synchronized void close() throws ItemStreamException {

		if (closed) {
			log.debug("close() already executed, skipping");
			return;
		}

		log.info("Closing P09372Writer resources");
		closed = true;

		if (letterCntFile != null) {
			try {
				letterCntFile.write(padLeftZeros(String.valueOf(wsLetterCount), 5));
				letterCntFile.newLine();
				letterCntFile.flush();
				log.info("Letter count {} written successfully", wsLetterCount);
			} catch (IOException e) {
				log.error("Failed to write letter count", e);
			}
		}

		safeClose(letterFile, "letterFile");
		safeClose(letterXmlFile, "letterXmlFile");
		safeClose(deleteFile, "deleteFile");
		safeClose(suspenseFile, "suspenseFile");
		safeClose(letterCntFile, "letterCntFile");

		log.info("P09372Writer closed successfully");
	}

	private void safeClose(BufferedWriter writer, String name) {
		if (writer != null) {
			try {
				writer.close();
				log.debug("{} closed", name);
			} catch (IOException e) {
				log.warn("Error closing {}", name, e);
			}
		}
	}

	public static String transformPatient(String patient) {
		// Step 1: Pad to 24 characters (like PIC X(24))
		String padded = pad(patient, 24);

		// Step 2: Replace all occurrences of x'000000' with x'404040'
		// In Java, \u0000 is the null character. We'll replace triple nulls with triple
		// spaces.
		String cleaned = padded.replace("\u0000\u0000\u0000", "   ");

		return cleaned;
	}

	private String cobolDelimitedByTwoSpaces(String value) {
		if (value == null) {
			return "";
		}

		int idx = value.indexOf("  "); 
		if (idx >= 0) {
			return value.substring(0, idx);
		}

		return value;
	}

	@Override
	public void update(ExecutionContext ctx) {
		// not used (COBOL does not checkpoint)
	}
}
