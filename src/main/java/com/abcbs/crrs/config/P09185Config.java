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
import org.springframework.batch.item.support.ListItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

import com.abcbs.crrs.jobs.P09185.P09185InputRecord;
import com.abcbs.crrs.jobs.P09185.P09185StepListener;
import com.abcbs.crrs.listener.JobLoggingListener;

@Configuration
public class P09185Config {
	
	@Autowired
	private JobLoggingListener jobListener;
	
	 private static final Logger logger = LogManager.getLogger(P09185Config.class);

	    @Bean
	    @StepScope
	    public FlatFileItemReader<P09185InputRecord> p09185Reader(@Value("#{jobParameters['inputFile']}") String inputFile) {

	        logger.info("Initializing reader for file: {}", inputFile);
	        return new FlatFileItemReaderBuilder<P09185InputRecord>()
	                .name("p09185Reader")
	                .resource(new FileSystemResource(inputFile))
	                .fixedLength()
	                .columns(new Range(1, 37))   
	                .names("record")
	                .targetType(P09185InputRecord.class)
	                .build();
	    }
	    
	    @Bean
	    public Step p09185Step(JobRepository jobRepository, PlatformTransactionManager transactionManager, FlatFileItemReader<P09185InputRecord> reader) {

	        logger.info("Building step: p09185Step");
	        return new StepBuilder("p09185Step", jobRepository)
	                .<P09185InputRecord, String>chunk(2000, transactionManager)
	                .reader(reader)
	                .writer(new ListItemWriter<>())
	                .listener(new P09185StepListener())
	                .build();
	    }
	    
	    @Bean
	    public Job p09185Job(JobRepository jobRepository, @Qualifier("p09185Step") Step p09185Step) {
	        logger.info("Building job: p09185Job");
	        return new JobBuilder("p09185Job", jobRepository)
	                .incrementer(new RunIdIncrementer())
	                .listener(jobListener)
	                .start(p09185Step)
	                .build();
	    }


}
