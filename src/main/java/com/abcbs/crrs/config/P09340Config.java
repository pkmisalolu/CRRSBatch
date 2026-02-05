package com.abcbs.crrs.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.abcbs.crrs.jobs.P09340.CorpControlFileReaderTasklet;
import com.abcbs.crrs.jobs.P09340.GeneralLedgerMonthlyProcessor;
import com.abcbs.crrs.jobs.P09340.GeneralLedgerReader;
import com.abcbs.crrs.jobs.P09340.GeneralLedgerWeeklyProcessor;
import com.abcbs.crrs.jobs.P09340.P09340GlRecord;
import com.abcbs.crrs.jobs.P09340.P09340MonthlyReportAndFileWriter;
import com.abcbs.crrs.jobs.P09340.P09340WeeklyReportAndFileWriter;
import com.abcbs.crrs.jobs.P09340.RunFrequencyDecider;
import com.abcbs.crrs.listener.JobLoggingListener;

@Configuration
public class P09340Config {
	
	@Autowired
	private JobLoggingListener jobListener;
	
	private static final Logger logger = LogManager.getLogger(P09310Config.class);
	
	@Bean
	public Step readCorpControlStep(JobRepository repo,
	                                PlatformTransactionManager tx,
	                                CorpControlFileReaderTasklet tasklet) {
	    return new StepBuilder("readCorpControlStep", repo)
	            .tasklet(tasklet, tx)
	            .build();
	}
	
	@Bean
	public Step weeklyGLStep(JobRepository repo,
	                         PlatformTransactionManager tx,
	                         GeneralLedgerReader reader,                      
	                         GeneralLedgerWeeklyProcessor processor,          
	                         P09340WeeklyReportAndFileWriter writer) {        
	    return new StepBuilder("weeklyGLStep", repo)
	            .<P09340GlRecord, P09340GlRecord>chunk(2000, tx)               
	            .reader(reader)
	            .processor(processor)
	            .writer(writer)
	            .listener(writer)
	            .build();
	}
	
	@Bean
	public Step monthlyGLStep(JobRepository repo,
	                          PlatformTransactionManager tx,
	                          GeneralLedgerReader reader,
	                          GeneralLedgerMonthlyProcessor processor,
	                          P09340MonthlyReportAndFileWriter writer) {
	    return new StepBuilder("monthlyGLStep", repo)
	    		.<P09340GlRecord, P09340GlRecord>chunk(2000, tx)
	            .reader(reader)
	            .processor(processor)
	            .writer(writer)
	            .build();
	}

	@Bean
	public Job p09340Job(JobRepository jobRepository,
	                     @Qualifier("readCorpControlStep") Step readCorpControlStep,
	                     RunFrequencyDecider decider,
	                      @Qualifier("weeklyGLStep") Step weeklyGLStep
	                      ,@Qualifier("monthlyGLStep") Step monthlyGLStep
	                      ) {

	    return new JobBuilder("p09340Job", jobRepository)
	    		.incrementer(new RunIdIncrementer())
	            .start(readCorpControlStep)
	            .next(decider)
	                .from(decider).on("WEEKLY").to(weeklyGLStep)
	                .from(decider).on("MONTHLY").to(monthlyGLStep)
	                .from(decider).on("INVALID").fail()
	            .end()
	            .listener(jobListener)
	            .build();
	}
	
	

}
