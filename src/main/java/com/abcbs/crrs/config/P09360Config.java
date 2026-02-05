package com.abcbs.crrs.config;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
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

import com.abcbs.crrs.jobs.P09360.P09360FileWriter;
import com.abcbs.crrs.jobs.P09360.P09GlDedsRecord;
import com.abcbs.crrs.repository.IActivityRepository;

@Configuration
public class P09360Config {

    private static final Logger log = LogManager.getLogger(P09360Config.class);

    @Bean
    public Job p09360Job(JobRepository repo, Step p09360Step) {
        return new JobBuilder("P09360Job", repo)
                .incrementer(new RunIdIncrementer())
                .start(p09360Step)
                .build();
    }

    @Bean
    public Step p09360Step(JobRepository repo,
                           PlatformTransactionManager tx,
                           FlatFileItemReader<P09GlDedsRecord> p09360Reader,
                           P09360FileWriter p09360Writer) {
        return new StepBuilder("P09360Step", repo)
                .<P09GlDedsRecord, P09GlDedsRecord>chunk(2000, tx)
                .reader(p09360Reader)
                .writer(p09360Writer)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<P09GlDedsRecord> p09360Reader(
            @Value("#{jobParameters['glInput']}") String glInputPath) {

        log.info("Initializing reader for file: {}", glInputPath);

        FixedLengthTokenizer tokenizer = new FixedLengthTokenizer();
        tokenizer.setNames(
                "p09deds_julian",
                "p09deds_hhmmsss",
                "filler",
                "gl_p09deds_id",
                "gl_refund_type",
                "gl_control_date",
                "gl_control_nbr",
                "gl_receipt_type",
                "gl_bank_acct_nbr",
                "gl_acct_nbr",
                "gl_act_code",
                "gl_act_amt",
                "gl_act_date",
                "gl_report_mo",
                "gl_report_yr",
                "gl_patient_ln",
                "gl_patient_fn",
                "gl_mbr_id_nbr",
                "gl_xref_type",
                "gl_xref_claim_nbr",
                "gl_xref_date",
                "gl_cash_rec_bal",
                "gl_corp",
                "gl_filler"
        );

        tokenizer.setColumns(
                new Range(1, 5),
                new Range(6, 12),
                new Range(13, 17),
                new Range(18, 19),
                new Range(20, 22),
                new Range(23, 32),
                new Range(33, 36),
                new Range(37, 38),
                new Range(39, 73),
                new Range(74, 85),
                new Range(86, 88),
                new Range(89, 100),
                new Range(101, 110),
                new Range(111, 112),
                new Range(113, 114),
                new Range(115, 129),
                new Range(130, 140),
                new Range(141, 152),
                new Range(153, 154),
                new Range(155, 174),
                new Range(175, 184),
                new Range(185, 196),
                new Range(197, 198),
                new Range(199, 206)
        );

        DefaultLineMapper<P09GlDedsRecord> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);

        BeanWrapperFieldSetMapper<P09GlDedsRecord> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(P09GlDedsRecord.class);
        lineMapper.setFieldSetMapper(fieldSetMapper);

        FlatFileItemReader<P09GlDedsRecord> reader = new FlatFileItemReader<>();
        reader.setResource(new FileSystemResource(new File(glInputPath)));
        reader.setLineMapper(lineMapper);
        return reader;
    }

    @Bean
    @StepScope
    public P09360FileWriter p09360Writer(
            @Value("#{jobParameters['reportOut']}") String reportOut,
            IActivityRepository activityRepository) {
        return new P09360FileWriter(reportOut, activityRepository);
    }
}
