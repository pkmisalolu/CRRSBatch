package com.abcbs.crrs.config;

import com.abcbs.crrs.jobs.P09180.CCMInputRecord;
import com.abcbs.crrs.jobs.P09180.P09180FileWriter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableBatchProcessing
public class P09180Config {
	private static final Logger log = LogManager.getLogger(P09180Config.class);
    // =========================================================
    // JOB
    // =========================================================
    @Bean
    public Job p09180Job(JobRepository jobRepository, Step p09180Step) {
    	log.info("Creating job: P09180Job");
        return new JobBuilder("P09180Job", jobRepository)
                .start(p09180Step)
                .build();
    }

    // =========================================================
    // STEP
    // NOTE:
    //   - Input  = CCMInputRecord
    //   - Output = CCMInputRecord (NO processor)
    // =========================================================
    @Bean
    public Step p09180Step(JobRepository jobRepository,
                           PlatformTransactionManager txManager,
                           FlatFileItemReader<CCMInputRecord> ccmReader,
                           P09180FileWriter writer) {
    	log.info("Creating step: P09186Step");
        return new StepBuilder("P09180Step", jobRepository)
                .<CCMInputRecord, CCMInputRecord>chunk(2000, txManager)
                .reader(ccmReader)
                .writer(writer)
                .build();
    }

    // =========================================================
    // INPUT FILE READER (467-BYTE FIXED LENGTH – COBOL CCM-FILE)
    // =========================================================
    @Bean
    @StepScope
    public FlatFileItemReader<CCMInputRecord> ccmReader(
            @Value("#{jobParameters['ccmFile']}") String inputFile) {
    	  log.info("Initializing reader for file: {}", inputFile);
        FixedLengthTokenizer t = new FixedLengthTokenizer();
        t.setColumns(new Range[]{
            new Range(1,27),   new Range(28,28),  new Range(29,48),  new Range(49,49),
            new Range(50,52),  new Range(53,53),  new Range(54,57),  new Range(58,58),
            new Range(59,61),  new Range(62,62),  new Range(63,82),
            new Range(83,90),  new Range(91,103), new Range(104,105),
            new Range(106,108),new Range(109,118),new Range(119,122),
            new Range(123,128),new Range(129,138),new Range(139,139),
            new Range(140,141),new Range(142,177),new Range(178,181),
            new Range(182,182),new Range(183,186),new Range(187,187),
            new Range(188,197),new Range(198,201),new Range(202,222),
            new Range(223,287),new Range(288,298),new Range(299,313),
            new Range(314,349),new Range(350,385),new Range(386,400),
            new Range(401,402),new Range(403,412),new Range(413,422),
            new Range(423,430),new Range(431,445),new Range(446,460),
            new Range(461,467)
        });

        t.setNames(
            "cInfo","cTilde2","cMemberId","cTilde3","cCcmType","cTilde4",
            "cBarcodeLob","cTilde5","cBarcodeLocNbr","cAsterisk2",
            "cBusLocation","cLocationNbr","cOtisNbr","cSectionCode",
            "cRefundType","cControlDate","cControlNbr","cStatus",
            "cStatusDate","cEobInd","cReceiptType","cRemittorName",
            "cRemittorTitle","cRemittorType","cClaimType","cOplInd",
            "cLetterDate","cReasonCode","cOtherCorr","cComments",
            "cPatientFname","cPatientLname","cAddr1","cAddr2","cCity",
            "cState","cZip","cCheckDate","cCheckNbr","cCheckAmount",
            "cControlledAmount","cLocationCode"
        );

        DefaultLineMapper<CCMInputRecord> lm = new DefaultLineMapper<>();
        BeanWrapperFieldSetMapper<CCMInputRecord> fm =
                new BeanWrapperFieldSetMapper<>();
        fm.setTargetType(CCMInputRecord.class);

        lm.setLineTokenizer(t);
        lm.setFieldSetMapper(fm);

        FlatFileItemReader<CCMInputRecord> reader =
                new FlatFileItemReader<>();
        reader.setResource(new FileSystemResource(inputFile));
        reader.setLineMapper(lm);

        return reader;
    }

    // =========================================================
    // WRITER (HANDLES TOTALS + XML GENERATION)
    // =========================================================
    @Bean
    @StepScope
    public P09180FileWriter writer(
            @Value("#{jobParameters['ccmTotals']}") String totalsFile,
            @Value("#{jobParameters['ccmXmlFile']}") String xmlOutput) {
    	 log.info("Initializing P09180FileWriter with output file: {}", xmlOutput);
        return new P09180FileWriter(totalsFile, xmlOutput);
    }
}
