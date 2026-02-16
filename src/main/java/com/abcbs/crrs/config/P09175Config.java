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

import com.abcbs.crrs.jobs.P09175.P09175FileWriter;
import com.abcbs.crrs.repository.IP09BatchRepository;
import com.abcbs.crrs.repository.IP09SuspenseRepository;
import com.abcbs.crrs.repository.IOptionRepository;   // ✅ ADD

@Configuration
public class P09175Config {

    private static final Logger log =
            LogManager.getLogger(P09175Config.class);

    @Bean
    public Job p09175Job(JobRepository repo, Step p09175Step) {

        log.info("Creating job: P09175Job");

        return new JobBuilder("P09175Job", repo)
                .incrementer(new RunIdIncrementer())
                .start(p09175Step)
                .build();
    }

    @Bean
    public Step p09175Step(JobRepository repo,
                           PlatformTransactionManager tx,
                           P09175FileWriter p09175Writer) {

        log.info("Creating step: P09175Step (tasklet)");

        return new StepBuilder("P09175Step", repo)
                .tasklet(p09175Writer, tx)
                .build();
    }

    /* =========================================================
     * TASKLET WRITER (PX01/PX02 driven)
     * ========================================================= */
    @Bean
    @StepScope
    public P09175FileWriter p09175Writer(
            @Value("#{jobParameters['P09175_ReportOutput']}") String reportPath,
            @Value("#{jobParameters['P09175_CcmOutput']}")     String flatPath,
            @Value("#{jobParameters['P09175_ControlTotal']}")  String totalPath,
            IP09BatchRepository batchRepository,
            IP09SuspenseRepository suspenseRepository,
            IOptionRepository optionRepository) {              

        log.info("Instantiating P09175FileWriter");

        return new P09175FileWriter(
                reportPath,
                flatPath,
                totalPath,
                batchRepository,
                suspenseRepository,
                optionRepository                                   
        );
    }
}