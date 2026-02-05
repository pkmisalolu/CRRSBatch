package com.abcbs.crrs.config;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import com.abcbs.crrs.jobs.P09390.P09390InputNewVendor;
import com.abcbs.crrs.jobs.P09390.P09390InputFepmanChks;
import com.abcbs.crrs.jobs.P09390.P09390OutputFepVendor;
import com.abcbs.crrs.jobs.P09390.P09390Processor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class P09390Config {

    // ============================================================
    // NEW VENDOR READER
    // ============================================================
    @Bean
    @StepScope
    public FlatFileItemReader<P09390InputNewVendor> newVendorReader(
            @Value("#{jobParameters['newVendorFile']}") String file) {

        return new FlatFileItemReaderBuilder<P09390InputNewVendor>()
                .name("p09390NewVendorReader")
                .resource(new FileSystemResource(file))
                .lineMapper((line, num) -> {
                    P09390InputNewVendor r = new P09390InputNewVendor();
                    r.setVendorNbr(sub(line,0,9));
                    r.setFiller1(sub(line,9,11));
                    r.setVendorName(sub(line,11,47));
                    r.setFiller2(sub(line,47,80));
                    return r;
                })
                .strict(true)
                .build();
    }

    // ============================================================
    // FEPMAN CHKS READER
    // ============================================================
    @Bean
    @StepScope
    public FlatFileItemReader<P09390InputFepmanChks> fepmanReader(
            @Value("#{jobParameters['fepmanFile']}") String file) {

        return new FlatFileItemReaderBuilder<P09390InputFepmanChks>()
                .name("p09390FepmanReader")
                .resource(new FileSystemResource(file))
                .lineMapper((line, num) -> {
                    P09390InputFepmanChks r = new P09390InputFepmanChks();
                    r.setRefundType(sub(line,0,3));
                    r.setFiller1(sub(line,3,4));
                    r.setCntrlDate(sub(line,4,14));       // PIC X(10) from file
                    r.setFiller2(sub(line,14,15));
                    r.setCntrlNbr(sub(line,15,19));
                    r.setInvMM(sub(line,19,21));
                    r.setInvSlash1(sub(line,21,22));
                    r.setInvDD(sub(line,22,24));
                    r.setInvSlash2(sub(line,24,25));
                    r.setInvCC(sub(line,25,27));
                    r.setInvYY(sub(line,27,29));
                    r.setProviderNbr(sub(line,29,38));
                    r.setPayeeName(sub(line,38,74));
                    r.setAddr1(sub(line,74,110));
                    r.setAddr2(sub(line,110,146));
                    r.setCity(sub(line,146,170));
                    r.setState(sub(line,170,172));
                    r.setZip1(sub(line,172,177));
                    r.setZip2(sub(line,177,181));
                    r.setInvoiceAmt(sub(line,181,192));
                    r.setDesc(sub(line,192,232));
                    r.setIdPrefix(sub(line,232,236));
                    r.setIdType(sub(line,236,237));
                    r.setCheckType(sub(line,237,239));
                    return r;
                })
                .strict(true)
                .build();
    }

    private static String sub(String s, int start, int end) {
        if (s == null || s.length() <= start) return "";
        return s.substring(start, Math.min(end, s.length()));
    }

    // ============================================================
    // IN-MEMORY VENDOR TABLE MAPS
    // ============================================================
    private final Map<String, String> vendorByNbr = new ConcurrentHashMap<>();
    private final Map<String, String> vendorByName = new ConcurrentHashMap<>();

    // ============================================================
    // LOAD NEW VENDOR TABLE INTO MAP
    // ============================================================
    @Bean
    public ItemWriter<P09390InputNewVendor> newVendorTableWriter() {
        return items -> {
            for (P09390InputNewVendor v : items) {
                String nbr = v.getVendorNbr().trim();
                String name = v.getVendorName().trim().toUpperCase();

                if (!nbr.isEmpty()) vendorByNbr.put(nbr, name);
                if (!name.isEmpty() && !vendorByName.containsKey(name))
                    vendorByName.put(name, nbr);
            }
        };
    }

    // ============================================================
    // PROCESSOR
    // ============================================================
    @Bean
    public P09390Processor p09390Processor() {
        return new P09390Processor(vendorByNbr, vendorByName);
    }

    // ============================================================
    // OUTPUT WRITER — EXACT 247-BYTE COBOL FORMAT
    // ============================================================
    @Bean
    @StepScope
    public FlatFileItemWriter<P09390OutputFepVendor> p09390Writer(
            @Value("#{jobParameters['outputFepVendor']}") String file) {

    	String tab = "\t";    // real COBOL tab

        return new FlatFileItemWriterBuilder<P09390OutputFepVendor>()
                .name("p09390Writer")
                .resource(new FileSystemResource(file))
                .lineAggregator(r -> {

                    StringBuilder sb = new StringBuilder(260);

                    // Correct COBOL MM/DD/YY
                    String cntrl = formatCobolDate(r.getCntrlDate());

                    sb.append(pad(r.getProviderNbr(), 9)).append(tab);
                    sb.append(pad(r.getName(), 36)).append(tab);
                    sb.append(pad(r.getAddr1(), 36)).append(tab);
                    sb.append(pad(r.getAddr2(), 36)).append(tab);
                    sb.append(pad(r.getCity(), 24)).append(tab);
                    sb.append(pad(r.getState(), 2)).append(tab);
                    sb.append(pad(r.getZip(), 5)).append(tab);
                    sb.append(pad(r.getInvoiceDate(), 10)).append(tab);

                    sb.append(pad(r.getRefundType(), 3)).append(" ");
                    sb.append(pad(cntrl, 8)).append(" ");
                    sb.append(pad(r.getCntrlNbr(), 4)).append(tab);

                    sb.append(zeroPad(r.getInvoiceAmt(), 11)).append(tab);
                    sb.append(pad(r.getDesc(), 40)).append(tab);

                    sb.append(pad(r.getCrrs(), 4));
                    sb.append(pad(r.getPayeeIdType(), 1)).append(tab);

                    sb.append(pad(r.getCheckType(), 2));
                    sb.append("  "); // filler X(2)

                    String line = sb.toString();

                    // ensure EXACT 247 bytes
                    if (line.length() > 247) return line.substring(0,247);
                    if (line.length() < 247) return line + " ".repeat(247 - line.length());

                    return line;
                })
                .shouldDeleteIfExists(true)
                .append(false)
                .build();
    }

    private static String pad(String s, int len) {
        if (s == null) s = "";
        if (s.length() >= len) return s.substring(0, len);
        return String.format("%-" + len + "s", s);
    }

    private static String zeroPad(String s, int len) {
        if (s == null) s = "";
        String dig = s.replaceAll("[^0-9]", "");
        if (dig.isEmpty()) dig = "0";
        if (dig.length() >= len) return dig.substring(0, len);
        return "0".repeat(len - dig.length()) + dig;
    }

    /**
     * Accept either:
     * - already formatted MM/DD/YY (length 8, contains '/')
     * - ISO-like yyyy-MM-dd (length >=10 with dashes)
     * - compact yyyyMMdd (length 8 digits)
     * Returns MM/DD/YY or eight spaces if cannot parse.
     */
    private static String formatCobolDate(String s) {
        if (s == null) return "        ";

        s = s.trim();
        if (s.isEmpty()) return "        ";

        // already in MM/DD/YY
        if (s.length() >= 8 && s.charAt(2) == '/' && s.charAt(5) == '/') {
            // ensure it's exactly 8 chars (MM/DD/YY)
            return s.substring(0,8);
        }

        // yyyy-MM-dd or yyyy-M-d
        String digitsAndSep = s.replaceAll("[^0-9\\-]", "");
        if (digitsAndSep.length() >= 10 && digitsAndSep.charAt(4) == '-' && digitsAndSep.charAt(7) == '-') {
            String yyyy = digitsAndSep.substring(0,4);
            String mm = digitsAndSep.substring(5,7);
            String dd = digitsAndSep.substring(8,10);
            return mm + "/" + dd + "/" + yyyy.substring(2,4);
        }

        // compact yyyyMMdd (8 digits)
        String digits = s.replaceAll("\\D", "");
        if (digits.length() >= 8) {
            String yyyy = digits.substring(0,4);
            String mm = digits.substring(4,6);
            String dd = digits.substring(6,8);
            return mm + "/" + dd + "/" + yyyy.substring(2,4);
        }

        // cannot parse -> return 8 spaces (COBOL empty date)
        return "        ";
    }

    // ============================================================
    // STEP 1 — LOAD VENDOR TABLE
    // ============================================================
    @Bean(name = "p09390LoadTableStep")
    public Step p09390LoadTableStep(JobRepository repo,
                                    FlatFileItemReader<P09390InputNewVendor> newVendorReader,
                                    ItemWriter<P09390InputNewVendor> newVendorTableWriter) {

        return new StepBuilder("p09390LoadTableStep", repo)
                .<P09390InputNewVendor, P09390InputNewVendor>chunk(2000,
                        new ResourcelessTransactionManager())
                .reader(newVendorReader)
                .writer(newVendorTableWriter)
                .build();
    }

    // ============================================================
    // STEP 2 — PROCESS FEPMAN → FEPVENDOR
    // ============================================================
    @Bean(name = "p09390ProcessStep")
    public Step p09390ProcessStep(JobRepository repo,
                                  FlatFileItemReader<P09390InputFepmanChks> fepmanReader,
                                  P09390Processor p09390Processor,
                                  FlatFileItemWriter<P09390OutputFepVendor> p09390Writer) {

        return new StepBuilder("p09390ProcessStep", repo)
                .<P09390InputFepmanChks, P09390OutputFepVendor>chunk(2000,
                        new ResourcelessTransactionManager())
                .reader(fepmanReader)
                .processor(p09390Processor)
                .writer(p09390Writer)
                .build();
    }

    // ============================================================
    // JOB
    // ============================================================
    @Bean
    public Job p09390Job(JobRepository repo,
                         @Qualifier("p09390LoadTableStep") Step loadStep,
                         @Qualifier("p09390ProcessStep") Step processStep) {

        return new JobBuilder("p09390Job", repo)
                .incrementer(new RunIdIncrementer())
                .start(loadStep)
                .next(processStep)
                .build();
    }
}