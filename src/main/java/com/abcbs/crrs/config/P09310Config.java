package com.abcbs.crrs.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

import com.abcbs.crrs.entity.P09Batch;
import com.abcbs.crrs.jobs.P09310.P09310DeleteWriter;
import com.abcbs.crrs.jobs.P09310.P09310OutputRecord;
import com.abcbs.crrs.jobs.P09310.P09310ReportWriter;
import com.abcbs.crrs.listener.JobLoggingListener;

import jakarta.persistence.EntityManagerFactory;

@Configuration
public class P09310Config {
	
	@Autowired
	private JobLoggingListener jobListener;

	private static final Logger logger = LogManager.getLogger(P09310Config.class);
	private String val = "              ";

	//step 1
	@Bean
    @StepScope
    public Tasklet chkpCardTasklet(@Value("#{jobParameters['chkpFile']}") String chkpFile) {
		logger.info("Entered into Tasklet");
        return (contribution, chunkCtx) -> {
            try (var in = new FileSystemResource(chkpFile).getInputStream()) {
                byte[] buf = in.readAllBytes();
                String content = new String(buf);
                String first6 = content.length() >= 6 ? content.substring(0, 6) : content;

                if (!first6.matches("\\d+")) {
                    logger.error("***************************************");
                    logger.error("***    CHECKPOINT CONTROL CARD IS   ***");
                    logger.error("***    INVALID FOR PROGRAM P09310   ***");
                    logger.error("***---------------------------------***");
                    logger.error("***    CHECKPOINT CARD COUNT IS     ***");
                    logger.error("***    NON-NUMERIC                  ***");
                    logger.error("***---------------------------------***");
                    logger.error("***    CHECKPOINT CARD COUNT IS => {}", first6);
                    logger.error("***---------------------------------***");
                    logger.error("***       CORRECT AND RESUBMIT      ***");
                    logger.error("***************************************");

                    throw new IllegalStateException("Program P09310 terminated: invalid CHKP-CARD count");
                }
            }
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step chkpCardStep(JobRepository repo,
                             PlatformTransactionManager txManager,
                             Tasklet chkpCardTasklet) {
    	logger.info("Entered into Step 1");
        return new StepBuilder("chkpCardStep", repo)
                .tasklet(chkpCardTasklet, txManager)
                .build();
    }

	//step 2 starts from here
	@Bean
	@StepScope
	public JpaPagingItemReader<P09Batch> p09310BatchReader(EntityManagerFactory entityManagerFactory,
			@Value("#{jobParameters['checkpointKey']}") String checkpointKey) {
		
		logger.info("Entered into Reader "+checkpointKey);

		String jpql =
				"SELECT b FROM P09Batch b " +
						"WHERE CONCAT(b.bId.btBatchPrefix, b.bId.btBatchDate, b.bId.btBatchSuffix, b.bId.crRefundType) > :checkpointKey " +
						"AND b.btPostedInd = 'P' ORDER BY b.bId.btBatchPrefix, b.bId.btBatchDate, b.bId.btBatchSuffix, b.bId.crRefundType";

		return new JpaPagingItemReaderBuilder<P09Batch>()
				.name("p09310BatchReader")
				.entityManagerFactory(entityManagerFactory)
				.queryString(jpql)
				.parameterValues(Map.of("checkpointKey", val)) // 14 spaces default
				.pageSize(50)   // align with chunk size for efficiency
				.build();
	}
	
	@Bean
	@StepScope
	public CompositeItemWriter<List<P09310OutputRecord>> compositeWriter(
	        P09310ReportWriter reportWriter,
	        P09310DeleteWriter deleteWriter) {

	    logger.info("Entered into Composite writer");

	    CompositeItemWriter<List<P09310OutputRecord>> compositeWriter = new CompositeItemWriter<>();
	    compositeWriter.setDelegates(Arrays.asList(reportWriter, deleteWriter));

	    return compositeWriter;
	}
	


	@Bean
	public Step p09310Step(JobRepository jobRepository,
	                       PlatformTransactionManager txManager,
	                       JpaPagingItemReader<P09Batch> p09310Reader,
	                       ItemProcessor<P09Batch, List<P09310OutputRecord>> p09310Processor,
	                       CompositeItemWriter<List<P09310OutputRecord>> compositeWriter,
	                       P09310ReportWriter reportWriter,
	                       P09310DeleteWriter deleteWriter) {

	    logger.info("Entered into Step 2");
//	    CompositeItemWriter<List<P09310OutputRecord>> compositeWriter = new CompositeItemWriter<>();
//	    compositeWriter.setDelegates(Arrays.asList(reportWriter, deleteWriter));

	    return new StepBuilder("p09310Step", jobRepository)
	            .<P09Batch, List<P09310OutputRecord>>chunk(2000, txManager)
	            .reader(p09310Reader)
	            .processor(p09310Processor)
	            .writer(compositeWriter)
	            .listener(reportWriter)
	            .build();
	}
	
	@Bean
	public Job p09310Job(JobRepository jobRepository, @Qualifier("chkpCardStep") Step chkpCardStep, @Qualifier("p09310Step") Step p09310ReportStep) {
		logger.info("Entered into Job");
	    return new JobBuilder("p09310Job", jobRepository)
	    		.incrementer(new RunIdIncrementer())
	    		.listener(jobListener)
	            .start(chkpCardStep)
	            .next(p09310ReportStep)   
	            .build();
	}

}
