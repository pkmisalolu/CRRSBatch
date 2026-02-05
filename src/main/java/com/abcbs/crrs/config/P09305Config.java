package com.abcbs.crrs.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

import com.abcbs.crrs.jobs.P09305.CorpNameLoader;
import com.abcbs.crrs.jobs.P09305.P09305OutputRecord;
import com.abcbs.crrs.jobs.P09305.P09305Processor;
import com.abcbs.crrs.jobs.P09305.P09305ReportWriter;
import com.abcbs.crrs.projections.P09305ActivityView;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

@Configuration
public class P09305Config {

 private static final Logger logger = LogManager.getLogger(P09305Config.class);
 
 String corpNo = null; 
 

 @Bean
 @StepScope
 public Tasklet p09305ChkpTasklet(@Value("#{jobParameters['chkpFile']}") String chkpFile) {
     return (contribution, chunkCtx) -> {
         try (var in = new FileSystemResource(chkpFile).getInputStream()) {
             String s = new String(in.readAllBytes());
             String first6 = s.length() >= 6 ? s.substring(0, 6) : s;
             if (!first6.matches("\\d+")) {
                 logger.error("***************************************");
                 logger.error("***    CHECKPOINT CONTROL CARD IS   ***");
                 logger.error("***    INVALID FOR PROGRAM P09305   ***");
                 logger.error("***---------------------------------***");
                 logger.error("***    CHECKPOINT CARD COUNT IS     ***");
                 logger.error("***    NON-NUMERIC                  ***");
                 logger.error("***---------------------------------***");
                 logger.error("***    CHECKPOINT CARD COUNT IS => {}", first6);
                 logger.error("***---------------------------------***");
                 logger.error("***       CORRECT AND RESUBMIT      ***");
                 logger.error("***************************************");
                 throw new IllegalStateException("Program P09305 terminated: invalid CHKP-CARD count");
             }
             chunkCtx.getStepContext().getStepExecution().getJobExecution()
                     .getExecutionContext().put("p09305.frequency", Integer.parseInt(first6));
         }
         
         logger.info("P09305 The length of the input is more than 6");
         return RepeatStatus.FINISHED;
     };
 }
 
 
 @Bean
 @StepScope
 public Tasklet p09305CountProbe(EntityManager em,
         @Value("#{jobParameters['checkpointKey']}") String checkpointKey) {
     return (contrib, ctx) -> {
         Long cnt = em.createQuery(
             "SELECT COUNT(a) FROM P09Activity a JOIN P09CashReceipt b " +
             " ON a.id.crCntrlDate=b.id.crCntrlDate AND a.id.crCntrlNbr=b.id.crCntrlNbr AND a.id.crRefundType=b.id.crRefundType " +
             "WHERE b.crCorp=:corpNo AND a.actDailyInd='Y' AND a.id.actActivity<>'EST' AND " +
             " CONCAT(a.actUserId, a.id.actActivity," +
             "       FUNCTION('FORMAT', a.id.crCntrlDate, 'yyyy-MM-dd')," +
             "        a.id.crCntrlNbr, a.id.crRefundType," +
             "        FUNCTION('FORMAT', a.id.actActivityDate, 'yyyy-MM-dd')," +
             "        FUNCTION('FORMAT', a.id.actTimestamp, 'yyyy-MM-dd HH:mm:ss.fff')) > :checkpointKey", Long.class)
             .setParameter("corpNo", this.corpNo)
             .setParameter("checkpointKey", checkpointKey == null ? "" : checkpointKey)
             .getSingleResult();
         logger.info("P09305 count probe -> {} rows match filters"+ cnt);
         return RepeatStatus.FINISHED;
     };
 }
 
 @Bean
 public Step p09305ChkpStep(JobRepository repo, PlatformTransactionManager tx,
                            Tasklet p09305ChkpTasklet,
                            Tasklet p09305CountProbe) {
	 logger.info("P09305 Chkp Step started");
     return new StepBuilder("p09305ChkpStep", repo)
    		 .tasklet(p09305CountProbe, tx)
    		 .tasklet(p09305ChkpTasklet, tx)
             .build();
 }

 @Bean
 @StepScope
 public CorpNameLoader corpNameLoader(@Value("#{jobParameters['corpFile']}") String corpFile) throws IOException {
	 
	 this.corpNo = readCorp(corpFile).corpNo;
	 logger.info("P09305 corp Name Loader: " +corpFile);
     return new CorpNameLoader(corpFile);
 }

 @Bean
 @StepScope
 public JpaPagingItemReader<P09305ActivityView> p09305Reader(
         EntityManagerFactory emf,
         @Value("#{jobParameters['corpFile']}") String corpFile,
         @Value("#{jobParameters['checkpointKey']}") String checkpointKey) throws IOException {

	 String jpql =
			    "SELECT NEW com.abcbs.crrs.projections.P09305ActivityViewImpl(" +
			    "  a.actUserId, a.id.actActivity, a.id.crCntrlDate, a.id.crCntrlNbr, a.id.crRefundType," +
			    "  a.id.actActivityDate, a.id.actTimestamp," +
			    "  a.actXrefNbr, a.actXrefDate, " +
			    "  b.crCntrldAmt, b.crCheckNbr, b.crCheckAmt, b.crReceiptType, b.crClaimType," +
			    "  b.crPatientLname, b.crPatientFname, b.crRemittorName, b.crMbrIdNbr," +
			    "  b.crReasonCode, b.crGlAcctNbr, b.crCorp" +          // <-- note b.id.crCorp
			    ") " +
			    "FROM P09Activity a JOIN P09CashReceipt b " +
			    "  ON "+
			    "a.id.crCntrlDate = b.id.crCntrlDate " +
			    "AND a.id.crCntrlNbr  = b.id.crCntrlNbr  " +
			    "AND  a.id.crRefundType= b.id.crRefundType " +
			    "WHERE b.crCorp = :corpNo " +                         // <-- note b.id.crCorp
			    "  AND a.actDailyInd = 'Y' " +
			    "  AND a.id.actActivity <> 'EST' " +                     // <-- a.id.actActivity
			    "  AND CONCAT(" +
			    "       a.actUserId, a.id.actActivity," +
			    "       FUNCTION('FORMAT', a.id.crCntrlDate, 'yyyy-MM-dd')," +
			    "       a.id.crCntrlNbr, a.id.crRefundType," +
			    "       FUNCTION('FORMAT', a.id.actActivityDate, 'yyyy-MM-dd')," +
			    "       FUNCTION('FORMAT', a.id.actTimestamp, 'yyyy-MM-dd HH:mm:ss.fff')" +
			    "      ) > :checkpointKey " +
			    "ORDER BY a.actUserId, a.id.actActivity, a.id.crCntrlDate, a.id.crCntrlNbr," +
			    "         a.id.crRefundType, a.id.actActivityDate, a.id.actTimestamp, b.crCorp";
		 

	 logger.info("P09305 Jpa Paging Item Reader job started ");
	 
     return new JpaPagingItemReaderBuilder<P09305ActivityView>()
             .name("p09305Reader")
             .entityManagerFactory(emf)
             .queryString(jpql)
             .parameterValues(Map.of("corpNo", readCorp(corpFile).corpNo, "checkpointKey", checkpointKey == null ? "" : checkpointKey))
             .pageSize(100)
             .build();
     
 }

 public ItemProcessor<P09305ActivityView, P09305OutputRecord> p09305Processor() {
     return new P09305Processor();
 }
 
 @Bean
 @StepScope
 public P09305ReportWriter p09305Writer(
         @Value("#{jobParameters['outputFile']}") String outputFile,
         CorpNameLoader corpNameLoader) {
	 logger.info("p09305 report writer started ");
     return new P09305ReportWriter(outputFile, this.corpNo, corpNameLoader);
 }

 @Bean
 public Step p09305Step(JobRepository repo,
                        PlatformTransactionManager tx,
                        JpaPagingItemReader<P09305ActivityView> reader,
                        ItemProcessor<P09305ActivityView,P09305OutputRecord> proc,
                        ItemWriter<P09305OutputRecord> writer) {
	 logger.info("P09305 Step job started ");
     return new StepBuilder("p09305Step", repo)
             .<P09305ActivityView, P09305OutputRecord>chunk(2000, tx)
             .reader(reader)
             .processor(proc)
             .writer(writer)
             .listener((StepExecutionListener) writer) // for headers/footers
             .build();
 }

 @Bean
 public Job p09305Job(JobRepository repo, Step p09305ChkpStep, Step p09305Step) {
	 
	 logger.info("P09305 job started ");
     return new JobBuilder("p09305Job", repo)
             .incrementer(new RunIdIncrementer())
             .start(p09305ChkpStep)
             .next(p09305Step)
             .build();
 }
 
 
 
	private record CorpMeta(String corpNo, String corpName) {
	}

	private static CorpMeta readCorp(String r) throws IOException {
		try (var br = new BufferedReader(new InputStreamReader(new FileSystemResource(r).getInputStream(), StandardCharsets.UTF_8))) {
			String line = Optional.ofNullable(br.readLine()).orElse("").trim();
			String no = line.length() >= 2 ? line.substring(10, 12).trim() : line;
			String name = line.length() > 2 ? line.substring(2).trim() : "";
			return new CorpMeta(no, name);
		}
	}
}
