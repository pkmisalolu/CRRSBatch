package com.abcbs.crrs.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.abcbs.crrs.jobs.P09186.P09186Tasklet;

@Configuration
public class P09186Config {

	private static final Logger log = LogManager.getLogger(P09186Config.class);

	@Bean
	public Job p09186Job(JobRepository jobRepository, Step p09186Step) {
		log.info("Creating job: P09186Job");
		return new JobBuilder("P09186Job", jobRepository).start(p09186Step).build();
	}

	@Bean
	public Step p09186Step(JobRepository jobRepository, PlatformTransactionManager txManager,
			P09186Tasklet p09186Tasklet) {
		log.info("Creating step: P09186Step");
		return new StepBuilder("P09186Step", jobRepository).tasklet(p09186Tasklet, txManager).build();
	}

	@Bean
	@StepScope
	public P09186Tasklet p09186Tasklet(@Value("#{jobParameters['inputFile']}") String inputFile) {
		log.info("Configured input file: {}", inputFile);
		return new P09186Tasklet(inputFile);
	}
}
