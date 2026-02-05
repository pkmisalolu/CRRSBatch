// src/main/java/com/abcbs/crrs/config/P09315Config.java
package com.abcbs.crrs.config;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import com.abcbs.crrs.jobs.P09315.P09315ReportTasklet;
import com.abcbs.crrs.jobs.P09315.P09315ReportWriter;
import com.abcbs.crrs.repository.IActivityRepository;

@Configuration
public class P09315Config {

	@Bean
	@StepScope
	public Tasklet p09315Tasklet(P09315ReportWriter writer, IActivityRepository repo,
			@Value("#{jobParameters['corpFile']}") String corpFile,
			@Value("#{jobParameters['outputFile']}") String outputFile) {
		return new P09315ReportTasklet(writer, repo, corpFile, outputFile);
	}

	@Bean
	public Step p09315ReportStep(JobRepository repo, PlatformTransactionManager tx, Tasklet p09315Tasklet) {
		return new StepBuilder("p09315ReportStep", repo).tasklet(p09315Tasklet, tx).build();
	}

	@Bean
	public Job p09315Job(JobRepository repo, Step p09315ReportStep) {
		return new JobBuilder("p09315Job", repo).incrementer(new RunIdIncrementer()).start(p09315ReportStep).build();
	}

}
