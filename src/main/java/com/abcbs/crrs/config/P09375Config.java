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

import com.abcbs.crrs.jobs.P09375.CheckPointRecord;
import com.abcbs.crrs.jobs.P09375.P09375FileWriter;
import com.abcbs.crrs.repository.IP09CashReceiptRepository;

@Configuration
public class P09375Config {

    private static final Logger log = LogManager.getLogger(P09375Config.class);

    @Bean
    public Job p09375Job(JobRepository repo, Step p09375Step) {
        log.info("Creating job: P09375Job");
        return new JobBuilder("P09375Job", repo)
                .incrementer(new RunIdIncrementer())
                .start(p09375Step)
                .build();
    }

    @Bean
    public Step p09375Step(JobRepository repo,
                           PlatformTransactionManager tx,
                           P09375FileWriter p09375Writer) {
        log.info("Creating step: P09375Step (as tasklet)");

        return new StepBuilder("P09375Step", repo)
                .tasklet(p09375Writer, tx)
                .build();
    }


    @Bean
    @StepScope
    public FlatFileItemReader<CheckPointRecord> p09375CkpReader(
            @Value("#{jobParameters['controlFile']}") String controlPath) {
        log.info("P09375 reading control file from: {}", controlPath);

        FixedLengthTokenizer tok = new FixedLengthTokenizer();
        tok.setColumns(new Range[]{
                new Range(1, 6),   // CONTROL-FREQUENCY
                new Range(7, 80)   // FILLER
        });
        tok.setNames("checkPointFrequency", "filler");

        BeanWrapperFieldSetMapper<CheckPointRecord> mapper = new BeanWrapperFieldSetMapper<>();
        mapper.setTargetType(CheckPointRecord.class);

        DefaultLineMapper<CheckPointRecord> lm = new DefaultLineMapper<>();
        lm.setLineTokenizer(tok);
        lm.setFieldSetMapper(mapper);

        FlatFileItemReader<CheckPointRecord> reader = new FlatFileItemReader<>();
        reader.setName("P09375ControlReader");
        reader.setResource(new FileSystemResource(controlPath));
        reader.setLineMapper(lm);
        reader.setStrict(true);
        return reader;
    }

    @Bean
    @StepScope
    public P09375FileWriter p09375Writer(
            @Value("#{jobParameters['label']}")   String labelPath,
            @Value("#{jobParameters['label1']}")  String label1Path,
            @Value("#{jobParameters['label2']}")  String label2Path,
            @Value("#{jobParameters['label3']}")  String label3Path,
            @Value("#{jobParameters['labelCnt']}")String labelCntPath,
            @Value("#{jobParameters['controlFile']}") String controlPath,
            FlatFileItemReader<CheckPointRecord> p09375CkpReader,
            IP09CashReceiptRepository cashReceiptRepo
    ) {
        log.info("Instantiating P09375FileWriter");
        return new P09375FileWriter(
                labelPath, label1Path, label2Path, label3Path,
                labelCntPath, controlPath, p09375CkpReader,
                cashReceiptRepo
        );
    }
}



