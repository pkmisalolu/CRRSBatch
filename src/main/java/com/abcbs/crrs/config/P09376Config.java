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

import com.abcbs.crrs.jobs.P09376.CheckPointRecord;
import com.abcbs.crrs.jobs.P09376.P09376FileWriter;
import com.abcbs.crrs.repository.IP09CashReceiptRepository;

@Configuration
public class P09376Config {

    private static final Logger log = LogManager.getLogger(P09376Config.class);

    @Bean
    public Job p09376Job(JobRepository repo, Step p09376Step) {
        log.info("Creating job: p09376Job");
        return new JobBuilder("p09376Job", repo)
                .incrementer(new RunIdIncrementer())
                .start(p09376Step)
                .build();
    }

    @Bean
    public Step p09376Step(JobRepository repo,
                           PlatformTransactionManager tx,
                           P09376FileWriter p09376Writer) {
        log.info("Creating step: p09376Step (as tasklet)");

        return new StepBuilder("p09376Step", repo)
                .tasklet(p09376Writer, tx)
                .build();
    }


    @Bean
    @StepScope
    public FlatFileItemReader<CheckPointRecord> P09376CkpReader(
            @Value("#{jobParameters['controlFile']}") String controlPath) {
        log.info("P09376 reading control file from: {}", controlPath);

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
        reader.setName("P09376ControlReader");
        reader.setResource(new FileSystemResource(controlPath));
        reader.setLineMapper(lm);
        reader.setStrict(true);
        return reader;
    }

    @Bean
    @StepScope
    public P09376FileWriter P09376Writer(
            @Value("#{jobParameters['label']}")   String labelPath,
//            @Value("#{jobParameters['label1']}")  String label1Path,
//            @Value("#{jobParameters['label2']}")  String label2Path,
//            @Value("#{jobParameters['label3']}")  String label3Path,
//            @Value("#{jobParameters['labelCnt']}")String labelCntPath,
            @Value("#{jobParameters['controlFile']}") String controlPath,
            FlatFileItemReader<CheckPointRecord> P09376CkpReader,
            IP09CashReceiptRepository cashReceiptRepo
    ) {
        log.info("Instantiating P09376FileWriter");
        return new P09376FileWriter(
                labelPath, controlPath, P09376CkpReader,
                cashReceiptRepo
        );
    }
}



