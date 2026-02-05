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
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

import com.abcbs.crrs.jobs.P09325.P09325Tasklet;
import com.abcbs.crrs.jobs.P09325.RoutingReportWriter;
import com.abcbs.crrs.jobs.P09325.SummaryReportWriter;
import com.abcbs.crrs.repository.IP09CashReceiptRepository;
import com.abcbs.crrs.repository.IP09SummaryRepository;

@Configuration
public class P09325Config {
	
	private static final Logger LOG = LogManager.getLogger(P09325Config.class);

	@Bean
	@StepScope
	public RoutingReportWriter routingReportWriter() {
		return new RoutingReportWriter();
	}

	@Bean
	@StepScope
	public SummaryReportWriter summaryReportWriter() {
		return new SummaryReportWriter();
	}

	@Bean
	@StepScope
	public Tasklet p09325Tasklet(IP09CashReceiptRepository cashReceiptrepo,IP09SummaryRepository summaryRepo, RoutingReportWriter writerA, SummaryReportWriter writerB,
			@Value("#{jobParameters['chkpFile']}") String chkpFile, @Value("#{jobParameters['outA']}") String outA,
			@Value("#{jobParameters['outB']}") String outB,
			
			@Value("#{jobParameters['checkpointKey'] ?: ''}") String checkpointKey) {
		LOG.info("Entered into P09325Tasklet");
		return new P09325Tasklet(cashReceiptrepo, summaryRepo, writerA, writerB, new FileSystemResource(chkpFile), outA, outB,  checkpointKey);
	}

	@Bean
	public Step p09325Step(JobRepository repo, PlatformTransactionManager tx, Tasklet p09325Tasklet) {
		LOG.info("Entered into P09325 Step");
		return new StepBuilder("p09325Step", repo).tasklet(p09325Tasklet, tx).build();
	}

	@Bean
	public Job p09325Job(JobRepository repo, Step p09325Step) {
		LOG.info("Entered into P09325 Job");
		return new JobBuilder("p09325Job", repo).incrementer(new RunIdIncrementer()).start(p09325Step).build();
	}
}
