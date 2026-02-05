package com.abcbs.crrs.config;

import com.abcbs.crrs.jobs.P09365.P09365InputGpIntrface;
import com.abcbs.crrs.jobs.P09365.P09365LineMapper;
import com.abcbs.crrs.jobs.P09365.P09365OutputManualChks;
import com.abcbs.crrs.jobs.P09365.P09365Processor;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;

import org.springframework.batch.core.configuration.annotation.StepScope;   // ✅ IMPORTANT
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.transform.LineAggregator;
import org.springframework.batch.item.file.separator.DefaultRecordSeparatorPolicy;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class P09365Config {

    @Bean(name = "p09365Job")
    public Job p09365Job(JobRepository jobRepository,
                         @Qualifier("p09365Step") Step p09365Step) {
        return new JobBuilder("p09365Job", jobRepository)
                .start(p09365Step)
                .build();
    }

    @Bean(name = "p09365Step")
    public Step p09365Step(JobRepository jobRepository,
                           PlatformTransactionManager transactionManager,
                           @Qualifier("p09365Reader") FlatFileItemReader<P09365InputGpIntrface> reader,
                           P09365Processor processor,
                           @Qualifier("p09365Writer") FlatFileItemWriter<P09365OutputManualChks> writer) {

        return new StepBuilder("p09365Step", jobRepository)
                .<P09365InputGpIntrface, P09365OutputManualChks>chunk(1000, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener(p09365EmptyFileListener())
                .build();
    }

    // ✅ MUST be StepScope because it uses jobParameters
    @Bean(name = "p09365Reader")
    @StepScope
    public FlatFileItemReader<P09365InputGpIntrface> p09365Reader(
            @Value("#{jobParameters['inputFile']}") String inputFile) {

        FlatFileItemReader<P09365InputGpIntrface> r = new FlatFileItemReader<>();
        r.setResource(new FileSystemResource(inputFile));
        r.setRecordSeparatorPolicy(new DefaultRecordSeparatorPolicy()); // ✅
        r.setLineMapper(P09365LineMapper.gpIntrfaceLineMapper());
        return r;
    }

    // ✅ MUST be StepScope because it uses jobParameters
    @Bean(name = "p09365Writer")
    @StepScope
    public FlatFileItemWriter<P09365OutputManualChks> p09365Writer(
            @Value("#{jobParameters['outputFile']}") String outputFile) {

        FlatFileItemWriter<P09365OutputManualChks> w = new FlatFileItemWriter<>();
        w.setResource(new FileSystemResource(outputFile));
        w.setLineAggregator(p09365ManualChksAggregator245());
        w.setShouldDeleteIfExists(true); // optional but usually desired
        return w;
    }

    @Bean
    public LineAggregator<P09365OutputManualChks> p09365ManualChksAggregator245() {
        return item -> {
            StringBuilder sb = new StringBuilder(300);

            sb.append(rpad(item.getMRefundType(), 3));
            sb.append(rpad(item.getMFiller1(), 1));
            sb.append(rpad(item.getMCntrlDate(), 10));
            sb.append(rpad(item.getMFiller2(), 1));
            sb.append(rpad(item.getMCntrlNbr(), 4));

            sb.append(rpad(item.getMInvoiceDate(), 10));
            sb.append(rpad(item.getMProviderNbr(), 9));
            sb.append(rpad(item.getMPayeeName(), 36));
            sb.append(rpad(item.getMPayeeAddr1(), 36));
            sb.append(rpad(item.getMPayeeAddr2(), 36));
            sb.append(rpad(item.getMPayeeCity(), 24));
            sb.append(rpad(item.getMPayeeSt(), 2));
            sb.append(rpad(item.getMPayeeZip1(), 5));
            sb.append(rpad(item.getMPayeeZip2(), 4));
            sb.append(lpadDigits(item.getMInvoiceAmt(), 11));
            sb.append(rpad(item.getMDesc(), 40));
            sb.append(rpad(item.getMPayeeIdPrefix(), 4));
            sb.append(rpad(item.getMPayeeIdType(), 1));
            sb.append(rpad(item.getMCheckType(), 2));

            String base = sb.toString();
            if (base.length() > 245) return base.substring(0, 245);
            if (base.length() < 245) return base + " ".repeat(245 - base.length());
            return base;
        };
    }

    @Bean
    public StepExecutionListener p09365EmptyFileListener() {
        return new StepExecutionListener() {
            @Override
            public void beforeStep(StepExecution stepExecution) { }

            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                if (stepExecution.getReadCount() == 0) {
                    System.out.println("*****************************");
                    System.out.println("*  --- PROGRAM P09365 ---   *");
                    System.out.println("*                           *");
                    System.out.println("* FILE EMPTY                *");
                    System.out.println("*                           *");
                    System.out.println("* DDNAME: IGPINTRF          *");
                    System.out.println("*****************************");

                    stepExecution.setTerminateOnly();
                    return ExitStatus.FAILED;
                }
                return stepExecution.getExitStatus();
            }
        };
    }

    private static String rpad(String s, int len) {
        if (s == null) s = "";
        if (s.length() >= len) return s.substring(0, len);
        return s + " ".repeat(len - s.length());
    }

    private static String lpadDigits(String s, int len) {
        if (s == null) s = "";
        s = s.replace(" ", "");
        if (s.length() >= len) return s.substring(s.length() - len);
        return "0".repeat(len - s.length()) + s;
    }
}