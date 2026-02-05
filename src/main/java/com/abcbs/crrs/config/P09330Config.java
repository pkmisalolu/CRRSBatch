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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.abcbs.crrs.jobs.P09330.P09330ReportTasklet;
import com.abcbs.crrs.jobs.P09330.ReportWriter;
import com.abcbs.crrs.repository.IP09CashReceiptRepository;

@Configuration
public class P09330Config {

    private static final Logger log = LogManager.getLogger(P09330Config.class);

    @Bean
    public Job p09330Job(JobRepository repo, Step p09330Step) {
        log.info("Creating job: p09330Job");
        return new JobBuilder("p09330Job", repo)
                .incrementer(new RunIdIncrementer())
                .start(p09330Step)
                .build();
    }

    @Bean
    public Step p09330Step(
            JobRepository repo,
            PlatformTransactionManager tx,
            P09330ReportTasklet p09330Tasklet) {

        log.info("Creating step: p09330Step (tasklet)");

        return new StepBuilder("p09330Step", repo)
                .tasklet(p09330Tasklet, tx)
                .build();
    }

    /**
     * Tasklet only needs:
     *   - outputFile path
     *   - repository
     *   - tx template for streaming DB cursors
     */
    @Bean
    @StepScope
    public P09330ReportTasklet p09330Tasklet(
            @Value("#{jobParameters['outputFile']}") String outputFile,
            IP09CashReceiptRepository cashRepo,
            PlatformTransactionManager platformTransactionManager) {

        log.info("Instantiating P09330ReportTasklet outputFile={}", outputFile);

        ReportWriter reportWriter = new ReportWriter(outputFile);

        TransactionTemplate txTemplate = new TransactionTemplate(platformTransactionManager);
        txTemplate.setReadOnly(true);

        return new P09330ReportTasklet(
                reportWriter,
                cashRepo,
                txTemplate
        );
    }
}
