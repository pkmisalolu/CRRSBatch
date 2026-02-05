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

import com.abcbs.crrs.jobs.P09320.P09320Record;
import com.abcbs.crrs.lock.P09TableLocker;
import com.abcbs.crrs.repository.IActivityRepository;
import com.abcbs.crrs.repository.IP09CashReceiptRepository;
import com.abcbs.crrs.repository.IP09ControlRepository;
import com.abcbs.crrs.jobs.P09320.P09320FileWriter;

@Configuration
public class P09320Config {

    private static final Logger log = LogManager.getLogger(P09320Config.class);

    @Bean
    public Job p09320Job(JobRepository jobRepository, Step p09320Step) {
        log.info("Configuring job: P09320Job");
        return new JobBuilder("P09320Job", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(p09320Step)
                .build();
    }

    @Bean
    public Step p09320Step(JobRepository jobRepository,
                           PlatformTransactionManager transactionManager,
                           FlatFileItemReader<P09320Record> p09320Reader,
                           P09320FileWriter p09320Writer) {
        log.info("Configuring step: P09320Step");
        return new StepBuilder("P09320Step", jobRepository)
                .<P09320Record, P09320Record>chunk(2000, transactionManager)
                .reader(p09320Reader)
                .writer(p09320Writer)
                .build();
    }

    @Bean
    @StepScope
    FlatFileItemReader<P09320Record> p09320Reader(
            @Value("#{jobParameters['input']}") String inputFile) {

        log.info("Initializing reader for file: {}", inputFile);
        File file = new File(inputFile);

        if (!file.exists() || !file.canRead()) {
            log.error("Input file does not exist or cannot be read: {}", inputFile);
            throw new IllegalArgumentException("Cannot read input file: " + inputFile);
        }

        try {
            FixedLengthTokenizer tokenizer = new FixedLengthTokenizer();
            tokenizer.setNames("controlId", "programName", "cardSeq", "filler1", "refundType", "filler2");
            tokenizer.setColumns(new Range[]{
                    new Range(1, 2),
                    new Range(3, 8),
                    new Range(9, 9),
                    new Range(10, 10),
                    new Range(11, 13),
                    new Range(14, 80)
            });

            DefaultLineMapper<P09320Record> lineMapper = new DefaultLineMapper<>();
            lineMapper.setLineTokenizer(tokenizer);
            lineMapper.setFieldSetMapper(new BeanWrapperFieldSetMapper<>() {{
                setTargetType(P09320Record.class);
            }});

            FlatFileItemReader<P09320Record> reader = new FlatFileItemReader<>();
            reader.setResource(new FileSystemResource(file));
            reader.setLineMapper(lineMapper);

            return reader;
        } catch (Exception e) {
            log.error("Error initializing FlatFileItemReader for file: {}", inputFile, e);
            throw new RuntimeException("Failed to initialize reader for input file", e);
        }
    }

    @Bean
    @StepScope
    public P09320FileWriter p09320FileWriter(
            @Value("#{jobParameters['reportOut']}") String outputFileName, 
            IActivityRepository activityRepository,
            IP09CashReceiptRepository receiptRepository,
            IP09ControlRepository controlRepository,P09TableLocker tableLocker
           ) {
        log.info("Initializing P09320FileWriter with output file: {}", outputFileName);
        return new P09320FileWriter(outputFileName, activityRepository, receiptRepository, controlRepository,  tableLocker);
    }
}
