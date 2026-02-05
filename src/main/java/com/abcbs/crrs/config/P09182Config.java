package com.abcbs.crrs.config;

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

import com.abcbs.crrs.jobs.P09182.P09182InputRecord;
import com.abcbs.crrs.jobs.P09182.P09182Writer;
import com.abcbs.crrs.listener.JobLoggingListener;

@Configuration
public class P09182Config {
	
	@Autowired
	private JobLoggingListener jobListener;
	
	@Bean
	@StepScope
	public FlatFileItemReader<P09182InputRecord> p09182Reader(
	        @Value("#{jobParameters['inputFile']}") String inputFile) {

	    return new FlatFileItemReaderBuilder<P09182InputRecord>()
	            .name("p09182Reader")
	            .resource(new FileSystemResource(inputFile))
	            .fixedLength()
	            .columns(new Range(1, 5), new Range(6, 20))
	            .names("formCount", "totalControlledAmount")
	            .targetType(P09182InputRecord.class)
	            .build();
	}
	
	@Bean
	public Step p09182Step(JobRepository jobRepository,
	                       PlatformTransactionManager transactionManager,
	                       FlatFileItemReader<P09182InputRecord> reader,
	                       P09182Writer writer) {

	    return new StepBuilder("p09182Step", jobRepository)
	            .<P09182InputRecord, P09182InputRecord>chunk(2000, transactionManager)
	            .reader(reader)
	            .writer(writer)
	            .listener(writer)
	            .build();
	}

	@Bean
	public Job p09182Job(JobRepository jobRepository, Step p09182Step) {
	    return new JobBuilder("p09182Job", jobRepository)
	    		.incrementer(new RunIdIncrementer())
	            .start(p09182Step)
	            .listener(jobListener)
	            .build();
	}

}
