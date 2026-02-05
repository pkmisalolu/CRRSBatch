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
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

import com.abcbs.crrs.jobs.P09183.P09183InputRecord;
import com.abcbs.crrs.jobs.P09183.P09183OutputRecord;
import com.abcbs.crrs.jobs.P09183.P09183Processor;
import com.abcbs.crrs.jobs.P09183.P09183Writer;
import com.abcbs.crrs.listener.JobLoggingListener;

@Configuration
public class P09183Config {
	
	private static final Logger logger = LogManager.getLogger(P09183Config.class);
	
	@Autowired 
	private JobLoggingListener jobListener;

    @Bean 
    @StepScope
    public FlatFileItemReader<P09183InputRecord> p09183Reader(@Value("#{jobParameters['inputFile']}") String inputFile) {
    	logger.info("Creating p09183Reader for inputFile={}", inputFile);
        return new FlatFileItemReaderBuilder<P09183InputRecord>()
            .name("p09183Reader")
            .resource(new FileSystemResource(inputFile))
            .fixedLength()
            .columns(new Range(1,5), new Range(6,20), new Range(21,31))
            .names("formCount", "totalControlledAmount", "controlledAmtNumeric")
            .targetType(P09183InputRecord.class)
            .build();
    }
    
    @Bean
    public Step p09183Step(JobRepository jobRepository, PlatformTransactionManager txManager,
                          FlatFileItemReader<P09183InputRecord> reader, 
                          P09183Processor processor, P09183Writer writer) {
    	
    	logger.info("Building Step 'p09183Step' with chunk size 100");
    	
        return new StepBuilder("p09183Step", jobRepository)
            .<P09183InputRecord, P09183OutputRecord>chunk(2000, txManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .listener(jobListener)
            .build();
    }

    @Bean
    public Job p09183Job(JobRepository jobRepository, Step p09183Step) {
    	logger.info("Building Job 'p09183Job'");
        return new JobBuilder("p09183Job", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(p09183Step)
            .listener(jobListener)
            .build();
    }

}
