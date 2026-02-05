package com.abcbs.crrs.config;

import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.support.builder.CompositeItemWriterBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

import com.abcbs.crrs.entity.P09Batch;
import com.abcbs.crrs.jobs.P09370.P09370CheckpointHandler;
import com.abcbs.crrs.jobs.P09370.P09370ReportWriter;
import com.abcbs.crrs.repository.IP09BatchRepository;

@Configuration
public class P09370Config {

    private static final Logger log = LogManager.getLogger(P09370Config.class);

    @Bean(name = "p09370Step")
    public Step p09370Step(JobRepository repo, PlatformTransactionManager tx,
                           ItemReader<P09Batch> p09370Reader,
                           P09370ReportWriter p09370Writer) {
      return new StepBuilder("p09370Step", repo)
          .<P09Batch,P09Batch>chunk(2000, tx)
          .reader(p09370Reader)
          .writer(p09370Writer)
          .listener(p09370Writer)
          .build();
    }

    @Bean
    public Job p09370Job(JobRepository jobRepository,
                         @Qualifier("p09370Step") Step p09370Step) {  // ✅ explicitly specify
        return new JobBuilder("P09370Job", jobRepository)
                .start(p09370Step)
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<P09Batch> p09370Reader(IP09BatchRepository repo,
                                             P09370CheckpointHandler ckpt,
                                             @Value("#{jobParameters['pageSize'] ?: 2000}") int pageSize) {
        final String initialKey = ckpt.getCurrentKey();
        log.info("P09370 starting with CKPNT-KEY='{}'", initialKey);

        return new ItemReader<>() {
            String ck = initialKey;
            int pageIdx = 0;
            Iterator<P09Batch> buf = List.<P09Batch>of().iterator();

            @Override
            public P09Batch read() {
                while (true) {
                    if (buf.hasNext()) return buf.next();
                    Pageable pg = PageRequest.of(pageIdx, pageSize);
                    List<P09Batch> page = repo.fetchAfterKeyUnposted(ck, pg);
                    if (page.isEmpty()) return null;
                    buf = page.iterator();
                    pageIdx++;
                    P09Batch last = page.get(page.size() - 1);
                    ck = concatKey(last);
                }
            }

            private String concatKey(P09Batch b) {
                String pfx = nz(b.getBId().getBtBatchPrefix());
                String dt  = nz(b.getBId().getBtBatchDate());
                String sfx = nz(b.getBId().getBtBatchSuffix());
                String rt  = nz(b.getBId().getCrRefundType());
                return pfx + dt + sfx + rt;
            }
            private String nz(String s) { return s == null ? "" : s; }
        };
    }

    @Bean
    @StepScope
    public P09370CheckpointHandler p09370CheckpointHandler(
            @Value("#{jobParameters['inputFile']}") String checkpointFile) {
        return new P09370CheckpointHandler(checkpointFile);
    }

    @Bean
    @StepScope
    public P09370ReportWriter p09370Writer(
            P09370CheckpointHandler ckpt,
            @Value("#{jobParameters['outputFile']}") String reportPath) {
        return new P09370ReportWriter(reportPath, ckpt);
    }
}