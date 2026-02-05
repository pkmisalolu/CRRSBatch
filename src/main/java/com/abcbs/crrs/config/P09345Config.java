package com.abcbs.crrs.config;

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

import com.abcbs.crrs.jobs.P09345.P09345FileWriter;
import com.abcbs.crrs.jobs.P09376.CheckPointRecord;
import com.abcbs.crrs.repository.IP09CashReceiptRepository;
import com.abcbs.crrs.repository.IP09ControlRepository;

@Configuration
public class P09345Config {

    private static final Logger log = LogManager.getLogger(P09345Config.class);

    @Bean
    public Job p09345Job(JobRepository repo, Step p09345Step) {
        log.info("Creating job: p09345Job");
        return new JobBuilder("p09345Job", repo)
                .incrementer(new RunIdIncrementer())
                .start(p09345Step)
                .build();
    }

    @Bean
    public Step p09345Step(JobRepository repo,
                           PlatformTransactionManager tx,
                           P09345FileWriter writer) {

        log.info("Creating step: p09345Step (tasklet)");
        return new StepBuilder("p09345Step", repo)
                .tasklet(writer, tx)
                .build();
    }

    /**
     * CHECKPOINT CARD READER
     * COBOL: CFS.P0910162.CHKPNT.P09345
     */
    @Bean
    @StepScope
    public FlatFileItemReader<CheckPointRecord> p09345CkpReader(
            @Value("#{jobParameters['Checkpoint_Card']}") String Checkpoint_Card) {

        log.info("P09345 reading control file from: {}", Checkpoint_Card);

        FixedLengthTokenizer tok = new FixedLengthTokenizer();
        tok.setColumns(new Range[]{
                new Range(1, 6),   // WS-CHKP-CARD-CNT
                new Range(7, 80)   // FILLER
        });
        tok.setNames("checkPointFrequency", "filler");

        BeanWrapperFieldSetMapper<CheckPointRecord> mapper =
                new BeanWrapperFieldSetMapper<>();
        mapper.setTargetType(CheckPointRecord.class);

        DefaultLineMapper<CheckPointRecord> lm =
                new DefaultLineMapper<>();
        lm.setLineTokenizer(tok);
        lm.setFieldSetMapper(mapper);

        FlatFileItemReader<CheckPointRecord> reader =
                new FlatFileItemReader<>();
        reader.setName("P09345ControlReader");
        reader.setResource(new FileSystemResource(Checkpoint_Card));
        reader.setLineMapper(lm);
        reader.setStrict(true);

        return reader;
    }

    /**
     * WRITER
     */
    @Bean
    @StepScope
    public P09345FileWriter p09345Writer(
            @Value("#{jobParameters['Corp_File']}") String Corp_File,
            @Value("#{jobParameters['Refund_Type_Card']}") String Refund_Type_Card,
            @Value("#{jobParameters['Checkpoint_Card']}") String Checkpoint_Card,
            @Value("#{jobParameters['P09345_Output']}") String P09345_Output,
            FlatFileItemReader<CheckPointRecord> p09345CkpReader,
            IP09CashReceiptRepository cashReceiptRepo , IP09ControlRepository controlRepo) {

        log.info("Instantiating P09345FileWriter");
        return new P09345FileWriter(
        		Corp_File,
                Refund_Type_Card,
                Checkpoint_Card,
                p09345CkpReader,
                cashReceiptRepo,
                controlRepo,
                P09345_Output
        );
    }
}
