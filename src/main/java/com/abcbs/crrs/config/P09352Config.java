package com.abcbs.crrs.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.ExecutionContextPromotionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.LineAggregator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import com.abcbs.crrs.jobs.P09352.P09352ApInterfaceOutput;
import com.abcbs.crrs.jobs.P09352.P09352CheckpointCardInput;
import com.abcbs.crrs.jobs.P09352.P09352ControlCardInput;
import com.abcbs.crrs.jobs.P09352.P09352CorpCardInput;
import com.abcbs.crrs.jobs.P09352.P09352InputVoucher;
import com.abcbs.crrs.jobs.P09352.P09352LineMappers;
import com.abcbs.crrs.jobs.P09352.P09352OutputVoucher;
import com.abcbs.crrs.jobs.P09352.P09352OutputWrapper;
import com.abcbs.crrs.jobs.P09352.P09352Processor;
import com.abcbs.crrs.jobs.P09352.P09352ReportWriter;
import com.abcbs.crrs.jobs.P09352.P09352XP07DedsOutput;
import com.abcbs.crrs.jobs.P09352.P09352XP09DedsOutput;

@Configuration
public class P09352Config {

    // ============================================================
    // JOB
    // ============================================================

    @Bean
    public Job p09352Job(JobRepository jobRepository,
                         @Qualifier("p09352ProcessStep") Step p09352ProcessStep,
                         @Qualifier("p09352VoucherStep") Step p09352VoucherStep) {

        return new JobBuilder("P09352Job", jobRepository)
                .start(p09352ProcessStep)
                .next(p09352VoucherStep)
                .build();
    }

    // ============================================================
    // PROMOTION LISTENER: Step EC -> Job EC (so next step can read)
    // ============================================================

    @Bean
    public ExecutionContextPromotionListener p09352PromotionListener() {
        ExecutionContextPromotionListener l = new ExecutionContextPromotionListener();
        l.setKeys(new String[] {
                "WS_LAST_VOUCHER_NBR_PREFIX",
                "WS_LAST_VOUCHER_NBR_SUFFIX",
                "checksIssued",

                "WS_RUN_DATE_MMDDYY",
                "WS_RUN_TIME_HHMMSS",
                "WS_CORP_CODE",
                "WS_CORP",
                "WS_DB2_CONTROL_CARD_DATE",
                "WS_AP_BATCH_ID"
        });
        return l;
    }

    // ============================================================
    // STEPS
    // ============================================================

    @Bean(name = "p09352ProcessStep")
    public Step p09352ProcessStep(JobRepository repo,
                                  PlatformTransactionManager transactionManager,
                                  @Qualifier("controlCardReader") org.springframework.batch.item.ItemReader<P09352ControlCardInput> controlReader,
                                  @Qualifier("corpCardReader") FlatFileItemReader<P09352CorpCardInput> corpReader,
                                  @Qualifier("checkpointReader") FlatFileItemReader<P09352CheckpointCardInput> checkpointReader,
                                  @Qualifier("inputVoucherReader") FlatFileItemReader<P09352InputVoucher> inputVoucherReader,
                                  P09352Processor processor,
                                  P09352ReportWriter reportWriter,
                                  @Qualifier("apWriter") FlatFileItemWriter<P09352ApInterfaceOutput> apWriter,
                                  @Qualifier("glWriter") FlatFileItemWriter<P09352XP09DedsOutput> glWriter,
                                  @Qualifier("occsWriter") FlatFileItemWriter<P09352XP07DedsOutput> occsWriter,
                                  ExecutionContextPromotionListener p09352PromotionListener) {

    	return new StepBuilder("p09352ProcessStep", repo)
    		    .listener(reportWriter)              // keep ONE
    		    .listener(p09352PromotionListener)
    		    .listener(processor)                 // keep only if processor implements StepExecutionListener
    		    .tasklet((contribution, chunkContext) -> {

    		        var stepExecution = chunkContext.getStepContext().getStepExecution();
    		        var stepEc = stepExecution.getExecutionContext();

    		        // ✅ ensure report writer is opened with step EC
    		        reportWriter.openIfNeeded(stepExecution);
    		        corpReader.open(stepEc);
    		        checkpointReader.open(stepEc);
    		        inputVoucherReader.open(stepEc);

    		        apWriter.open(stepEc);
    		        glWriter.open(stepEc);
    		        occsWriter.open(stepEc);

    		        try {
    		            P09352ControlCardInput ctrl = controlReader.read();

    		            P09352OutputWrapper out = processor.process(ctrl);

    		            int apCnt = (out == null || out.getApRecords() == null) ? 0 : out.getApRecords().size();
    		            int glCnt = (out == null || out.getGlRecords() == null) ? 0 : out.getGlRecords().size();
    		            int ocCnt = (out == null || out.getOccsRecords() == null) ? 0 : out.getOccsRecords().size();

    		            if (apCnt > 0) apWriter.write(new Chunk<>(out.getApRecords()));
    		            if (glCnt > 0) glWriter.write(new Chunk<>(out.getGlRecords()));
    		            if (ocCnt > 0) occsWriter.write(new Chunk<>(out.getOccsRecords()));

    		            return org.springframework.batch.repeat.RepeatStatus.FINISHED;
    		        } finally {
    		            try { occsWriter.close(); } catch (Exception ignore) {}
    		            try { glWriter.close(); } catch (Exception ignore) {}
    		            try { apWriter.close(); } catch (Exception ignore) {}

    		            try { inputVoucherReader.close(); } catch (Exception ignore) {}
    		            try { checkpointReader.close(); } catch (Exception ignore) {}
    		            try { corpReader.close(); } catch (Exception ignore) {}    		           
    		        }
    		    }, transactionManager)
    		    .build();
    }

    @Bean(name = "p09352VoucherStep")
    public Step p09352VoucherStep(JobRepository repo,
                                  PlatformTransactionManager transactionManager,
                                  @Qualifier("voucherWriter") FlatFileItemWriter<P09352OutputVoucher> voucherWriter) {

    	return new StepBuilder("p09352VoucherStep", repo)
    		    .tasklet((contribution, chunkContext) -> {

    		        var stepExecution = chunkContext.getStepContext().getStepExecution();
    		        var stepEc = stepExecution.getExecutionContext();
    		        var jobEc = stepExecution.getJobExecution().getExecutionContext();

    		        String prefix = jobEc.getString("WS_LAST_VOUCHER_NBR_PREFIX", " ");
    		        int suffixStart = jobEc.getInt("WS_LAST_VOUCHER_NBR_SUFFIX", 0);
    		        int checksIssued = jobEc.getInt("checksIssued", 0);

    		        int suffixOut = suffixStart + checksIssued;

    		        P09352OutputVoucher out = new P09352OutputVoucher();
    		        out.setOutputLastVoucherNbrPrefix(prefix);
    		        out.setOutputLastVoucherNbrSuffix(String.format("%05d", suffixOut));
    		        out.setOutputLastVoucherNbrFiller(padRight("", 74));

    		        voucherWriter.open(stepEc);
    		        try {
    		            voucherWriter.write(new Chunk<>(List.of(out)));
    		        } finally {
    		            voucherWriter.close();
    		        }  

    		        return org.springframework.batch.repeat.RepeatStatus.FINISHED;
    		    }, transactionManager)
    		    .build();
    }

    // ============================================================
    // READERS
    // ============================================================

    @Bean(name = "controlCardReader")
    @org.springframework.batch.core.configuration.annotation.StepScope
    public org.springframework.batch.item.ItemReader<P09352ControlCardInput> controlCardReader() {

        // One dummy record so step runs once; processor will split datePicker itself
        return new org.springframework.batch.item.support.ListItemReader<>(
                java.util.Collections.singletonList(new P09352ControlCardInput())
        );
    }

    @Bean(name = "corpCardReader")
    @org.springframework.batch.core.configuration.annotation.StepScope
    public FlatFileItemReader<P09352CorpCardInput> corpCardReader(
            @Value("#{jobParameters['corpCardFile']}") String corpCardFile) {

        return new FlatFileItemReaderBuilder<P09352CorpCardInput>()
                .name("corpCardReader")
                .resource(new FileSystemResource(corpCardFile))
                .lineMapper(P09352LineMappers.corpCardLineMapper())
                .build();
    }

    @Bean(name = "checkpointReader")
    @org.springframework.batch.core.configuration.annotation.StepScope
    public FlatFileItemReader<P09352CheckpointCardInput> checkpointReader(
            @Value("#{jobParameters['checkpointFile']}") String checkpointFile) {

        return new FlatFileItemReaderBuilder<P09352CheckpointCardInput>()
                .name("checkpointReader")
                .resource(new FileSystemResource(checkpointFile))
                .lineMapper(P09352LineMappers.checkpointLineMapper())
                .build();
    }

    @Bean(name = "inputVoucherReader")
    @org.springframework.batch.core.configuration.annotation.StepScope
    public FlatFileItemReader<P09352InputVoucher> inputVoucherReader(
            @Value("#{jobParameters['ivoucherFile']}") String ivoucherFile) {

        return new FlatFileItemReaderBuilder<P09352InputVoucher>()
                .name("inputVoucherReader")
                .resource(new FileSystemResource(ivoucherFile))
                .lineMapper(P09352LineMappers.inputVoucherLineMapper())
                .build();
    }

    // ============================================================
    // WRITERS (AP / GL / OCCS / VOUCHER)
    // ============================================================

    /**
     * COBOL AP-INTRFACE-OUT is 275 characters. This writer builds exactly 275 chars:
     * - Invoice date MM/DD/CCYY (uses oInvoiceCc + oInvoiceYy)
     * - oChkAmt is PIC 9(9)V99 => 11 digits, implied decimal, NO dot.
     */
    @Bean(name = "apWriter")
    @org.springframework.batch.core.configuration.annotation.StepScope
    public FlatFileItemWriter<P09352ApInterfaceOutput> apWriter(
            @Value("#{jobParameters['xapntrfcPath']}") String xapntrfcPath) {

        LineAggregator<P09352ApInterfaceOutput> agg = item -> {
            String invoiceDate =
                    fix(item.getOInvoiceMm(), 2) +
                    "/" +
                    fix(item.getOInvoiceDd(), 2) +
                    "/" +
                    fix(item.getOInvoiceCc(), 2) +
                    fix(item.getOInvoiceYy(), 2); // total 10

            String chkAmt11 = pic9v99(item.getOChkAmt(), 11);

            String line =
                    fix(item.getORecdCode(), 1) +
                    fix(item.getFiller1(), 1) +
                    fix(item.getOVoucherNbr(), 6) +
                    invoiceDate +
                    fix(item.getFiller2(), 1) +
                    fix(item.getOTaxIdNbr(), 9) +
                    fix(item.getOPayeeIdType(), 1) +
                    fix(item.getOProviderNbr(), 9) +
                    fix(item.getOPayeeName(), 30) +
                    fix(item.getOPayeeAddr1(), 35) +
                    fix(item.getOPayeeAddr2(), 35) +
                    fix(item.getOPayeeCity(), 20) +
                    fix(item.getOPayeeSt(), 2) +
                    fix(item.getOPayeeZip1(), 5) +
                    fix(item.getOPayeeZip2(), 4) +
                    fix(item.getFiller3(), 1) +
                    chkAmt11 +
                    fix(item.getONegInd(), 1) +
                    fix(item.getOChkNbr(), 8) +
                    fix(item.getOChkDate(), 10) +
                    fix(item.getOChkType(), 2) +
                    fix(item.getOBankAcctNbr(), 8) +
                    fix(item.getONationalId(), 10) +
                    fix(item.getORefundType(), 3) +
                    fix(item.getOCntrlDate(), 10) +
                    fix(item.getOCntrlNbr(), 4) +
                    fix(item.getOMemberId(), 12) +
                    fix(item.getOLname(), 15) +
                    fix(item.getOFname(), 11);

            // enforce exact 275
            return fix(line, 275);
        };

        return new FlatFileItemWriterBuilder<P09352ApInterfaceOutput>()
                .name("apWriter")
                .resource(new FileSystemResource(xapntrfcPath))
                .shouldDeleteIfExists(true)
                .lineSeparator("\n")
                .lineAggregator(agg)
                .build();
    }

    @Bean(name = "glWriter")
    @org.springframework.batch.core.configuration.annotation.StepScope
    public FlatFileItemWriter<P09352XP09DedsOutput> glWriter(
            @Value("#{jobParameters['xp09Path']}") String xp09Path) {

        // Leaving your existing field widths, but safer “new file per run”
        org.springframework.batch.item.file.transform.FormatterLineAggregator<P09352XP09DedsOutput> agg =
                new org.springframework.batch.item.file.transform.FormatterLineAggregator<>();

        agg.setFormat(
                "%05d" + "%07d" + "%-5s" +
                "%-2s" + "%-3s" + "%-10s" + "%-4s" + "%-2s" +
                "%-35s" + "%-12s" + "%-3s" +
                "%-13s" +
                "%-10s" + "%-2s" + "%-2s" +
                "%-15s" + "%-11s" + "%-12s" +
                "%-2s" + "%-20s" + "%-10s" +
                "%-13s" +
                "%-2s" + "%-8s"
        );

        agg.setFieldExtractor(item -> new Object[] {
                item.getGlJulianDate() == null ? 0 : item.getGlJulianDate().intValue(),
                item.getGlTimeHhmmsss() == null ? 0 : item.getGlTimeHhmmsss().intValue(),
                fix(item.getGlFiller1(), 5),

                fix(item.getGlRecordId(), 2),
                fix(item.getGlRefundType(), 3),
                fix(item.getGlControlDate(), 10),
                fix(item.getGlControlNbr(), 4),
                fix(item.getGlReceiptType(), 2),

                fix(item.getGlBankAcctNbr(), 35),
                fix(item.getGlAcctNbr(), 12),
                fix(item.getGlActCode(), 3),

                picS9v99AsText(item.getGlActAmt(), 13),
                fix(item.getGlActDate(), 10),
                fix(item.getGlReportMo(), 2),
                fix(item.getGlReportYr(), 2),

                fix(item.getGlPatientLn(), 15),
                fix(item.getGlPatientFn(), 11),
                fix(item.getGlMemberId(), 12),

                fix(item.getGlXrefType(), 2),
                fix(item.getGlXrefClaimNbr(), 20),
                fix(item.getGlXrefDate(), 10),

                picS9v99AsText(item.getGlCashRecBal(), 13),
                fix(item.getGlCorp(), 2),
                fix(item.getGlFiller2(), 8)
        });

        return new FlatFileItemWriterBuilder<P09352XP09DedsOutput>()
                .name("glWriter")
                .resource(new FileSystemResource(xp09Path))
                .shouldDeleteIfExists(true)
                .lineSeparator("\n")
                .lineAggregator(agg)
                .build();
    }

    @Bean(name = "occsWriter")
    @org.springframework.batch.core.configuration.annotation.StepScope
    public FlatFileItemWriter<P09352XP07DedsOutput> occsWriter(
            @Value("#{jobParameters['xp07Path']}") String xp07Path) {

        org.springframework.batch.item.file.transform.FormatterLineAggregator<P09352XP07DedsOutput> agg =
                new org.springframework.batch.item.file.transform.FormatterLineAggregator<>();

        agg.setFormat(
        		"%05d" + "%07d" + "%-5s" +
                "%-2s" + "%-6s" + "%-1s" +
                "%-35s" +
                "%-2s" + "%-6s" +
                "%-10s" + "%-13s" +      // AMT AS STRING
                "%-9s" + "%-10s" + "%-36s" + "%-3s" +
                "%-36s" + "%-36s" + "%-24s" + "%-2s" +
                "%-5s" + "%-4s" +
                "%-1s" + "%-2s" + "%-2s" + "%-10s" + "%-2s" +
                "%-1s" + "%-1s" + "%-1s" + "%2d" +
                "%-1s" + "%2d" +
                "%-10s" + "%2d" +
                "%-1s" + "%2d" +
                "%-10s" + "%2d" +
                "%-1s" + "%2d" +
                "%-35s" + "%2d" +
                "%-8s" + "%2d" +
                "%-10s" + "%2d" +
                "%-2s" + "%2d" +
                "%-10s" +
                "%-35s" + "%2d" +
                "%-8s" + "%2d" +
                "%-10s" + "%2d" +
                "%-2s" + "%2d" +
                "%-10s" + "%2d" +
                "%-1s" + "%2d" +
                "%-9s" + "%2d" +
                "%-55s"
        );

        agg.setFieldExtractor(item -> new Object[] {
                item.getP07JulianDate() == null ? 0 : item.getP07JulianDate().intValue(),
                item.getP07TimeHhmmsss() == null ? 0 : item.getP07TimeHhmmsss().intValue(),
                fix(item.getP07Filler1(), 5),

                fix(item.getP07ActionId(), 2),
                fix(item.getP07BatchNbr(), 6),
                fix(item.getP07FileCode(), 1),

                fix(item.getP07AccountNbr(), 35),

                fix(item.getP07ChkFiller(), 2),
                fix(item.getP07ChkNbr(), 6),

                fix(item.getP07ChkDate(), 10),
                picS9v99AsText(item.getP07ChkAmt(), 13),

                fix(item.getP07PayeeId(), 9),
                fix(item.getP07Npi(), 10),
                fix(item.getP07PayeeName(), 36),
                fix(item.getP07Title(), 3),

                fix(item.getP07Addr1(), 36),
                fix(item.getP07Addr2(), 36),
                fix(item.getP07City(), 24),
                fix(item.getP07State(), 2),

                fix(item.getP07Zip5(), 5),
                fix(item.getP07Zip4(), 4),

                fix(item.getP07ChkOrigin(), 1),
                fix(item.getP07ChkType(), 2),
                fix(item.getP07ChkStatus(), 2),
                fix(item.getP07ChkStatusDate(), 10),
                fix(item.getP07StatusSource(), 2),

                fix(item.getP07OsDaily(), 1),
                fix(item.getP07StDaily(), 1),
                fix(item.getP07SdDaily(), 1),
                item.getP07SdDailyNf() == null ? -1 : item.getP07SdDailyNf(),

                fix(item.getP07TsDaily(), 1),
                item.getP07TsDailyNf() == null ? -1 : item.getP07TsDailyNf(),

                fix(item.getP07StaleDate(), 10),
                item.getP07StaleDateNf() == null ? -1 : item.getP07StaleDateNf(),

                fix(item.getP07StaleOrigin(), 1),
                item.getP07StaleOriginNf() == null ? -1 : item.getP07StaleOriginNf(),

                fix(item.getP07TransferDate(), 10),
                item.getP07TransferDateNf() == null ? -1 : item.getP07TransferDateNf(),

                fix(item.getP07TransferOrigin(), 1),
                item.getP07TransferOriginNf() == null ? -1 : item.getP07TransferOriginNf(),

                fix(item.getP07ReissueAcctNbr(), 35),
                item.getP07ReissueAcctNbrNf() == null ? -1 : item.getP07ReissueAcctNbrNf(),

                fix(item.getP07ReissueChkNbr(), 8),
                item.getP07ReissueChkNbrNf() == null ? -1 : item.getP07ReissueChkNbrNf(),

                fix(item.getP07ReissueChkDate(), 10),
                item.getP07ReissueChkDateNf() == null ? -1 : item.getP07ReissueChkDateNf(),

                fix(item.getP07ReissueChkType(), 2),
                item.getP07ReissueChkTypeNf() == null ? -1 : item.getP07ReissueChkTypeNf(),

                fix(item.getP07ReportDate(), 10),

                fix(item.getP07InitialAcctNbr(), 35),
                item.getP07InitialAcctNbrNf() == null ? -1 : item.getP07InitialAcctNbrNf(),

                fix(item.getP07InitialChkNbr(), 8),
                item.getP07InitialChkNbrNf() == null ? -1 : item.getP07InitialChkNbrNf(),

                fix(item.getP07InitialChkDate(), 10),
                item.getP07InitialChkDateNf() == null ? -1 : item.getP07InitialChkDateNf(),

                fix(item.getP07InitialChkType(), 2),
                item.getP07InitialChkTypeNf() == null ? -1 : item.getP07InitialChkTypeNf(),

                fix(item.getP07PpaDate(), 10),
                item.getP07PpaDateNf() == null ? -1 : item.getP07PpaDateNf(),

                fix(item.getP07PayeeIdType(), 1),
                item.getP07PayeeIdTypeNf() == null ? -1 : item.getP07PayeeIdTypeNf(),

                fix(item.getP07TaxIdNbr(), 9),
                item.getP07TaxIdNbrNf() == null ? -1 : item.getP07TaxIdNbrNf(),

                fix(item.getP07FillerFinal(), 55)
        });

        return new FlatFileItemWriterBuilder<P09352XP07DedsOutput>()
                .name("occsWriter")
                .resource(new FileSystemResource(xp07Path))
                .shouldDeleteIfExists(true)
                .lineSeparator("\n")
                .lineAggregator(agg)
                .build();
    }

    @Bean(name = "voucherWriter")
    @org.springframework.batch.core.configuration.annotation.StepScope
    public FlatFileItemWriter<P09352OutputVoucher> voucherWriter(
            @Value("#{jobParameters['xvoucherPath']}") String xvoucherPath) {

        LineAggregator<P09352OutputVoucher> agg = item ->
                fix(item.getOutputLastVoucherNbrPrefix(), 1) +
                fix(item.getOutputLastVoucherNbrSuffix(), 5) +
                fix(item.getOutputLastVoucherNbrFiller(), 74);

        return new FlatFileItemWriterBuilder<P09352OutputVoucher>()
                .name("voucherWriter")
                .resource(new FileSystemResource(xvoucherPath))
                .shouldDeleteIfExists(true)
                .lineSeparator("\n")
                .lineAggregator(agg)
                .build();
    }

    // ============================
    // helpers (COBOL-style)
    // ============================

    private static String fix(String s, int len) {
        if (s == null) s = "";
        if (s.length() > len) return s.substring(0, len);
        return padRight(s, len);
    }

    private static String padRight(String s, int len) {
        if (s == null) s = "";
        if (s.length() >= len) return s;
        return s + " ".repeat(len - s.length());
    }

    /**
     * PIC 9(9)V99 => implied decimal, no dot, zero-filled.
     * len should be 11 for 9(9)V99.
     */
    private static String pic9v99(BigDecimal amt, int len) {
        if (amt == null) amt = BigDecimal.ZERO;
        amt = amt.setScale(2, RoundingMode.DOWN);
        BigDecimal cents = amt.movePointRight(2);
        String digits = cents.abs().toPlainString(); // should be integer now
        digits = digits.replace(".", "");
        if (digits.startsWith("-")) digits = digits.substring(1);
        if (digits.length() > len) digits = digits.substring(digits.length() - len);
        return "0".repeat(Math.max(0, len - digits.length())) + digits;
    }

    /**
     * Your XP07/XP09 amounts are stored in BigDecimal but written as text in your output files.
     * This writes implied-decimal cents as a fixed width numeric string (no dot), left padded with zeros,
     * and with a leading '-' if negative (still fixed width).
     */
    private static String picS9v99AsText(BigDecimal amt, int len) {
        if (amt == null) amt = BigDecimal.ZERO;
        boolean neg = amt.signum() < 0;
        String digits = pic9v99(amt, len); // start with unsigned digits
        if (!neg) return digits;
        // force leading '-' while preserving total width
        if (len <= 0) return "";
        return "-" + digits.substring(1);
    }
}