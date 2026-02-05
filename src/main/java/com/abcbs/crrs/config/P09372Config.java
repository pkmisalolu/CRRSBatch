package com.abcbs.crrs.config;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import com.abcbs.crrs.entity.P09CashReceipt;
import com.abcbs.crrs.jobs.P09372.P09372Writer;
import com.abcbs.crrs.repository.IP09CashReceiptRepository;
import com.abcbs.crrs.repository.IP09SuspenseRepository;

@Configuration
@EnableBatchProcessing
public class P09372Config {

	private static final Logger log = LogManager.getLogger(P09372Config.class);

	@Bean
	public Job P09372Job(JobRepository jobRepository, Step P09372Step) {
		log.info("Initializing Job: P09372Job");
		return new JobBuilder("P09372Job", jobRepository).start(P09372Step).build();
	}

	@Bean
	public Step P09372Step(JobRepository jobRepository, PlatformTransactionManager txManager,
			ItemReader<P09CashReceipt> cashReceiptReader, P09372Writer writer) {
		log.info("Configuring Step: P09372Step with chunk size 2000");
		return new StepBuilder("P09372Step", jobRepository).<P09CashReceipt, P09CashReceipt>chunk(2000, txManager)
				.reader(cashReceiptReader).writer(writer).build();
	}

	@Bean
	public ItemReader<P09CashReceipt> cashReceiptReader(IP09CashReceiptRepository repository) {
		log.info("Creating ItemReader for P09CashReceipt using repository method: findOffCashReceipts");
		return new RepositoryItemReaderBuilder<P09CashReceipt>().name("cashReceiptReader").repository(repository)
				.methodName("findOffCashReceipts").pageSize(100).sorts(Map.of("crId.crRefundType", Sort.Direction.ASC,
						"crId.crCntrlDate", Sort.Direction.ASC, "crId.crCntrlNbr", Sort.Direction.ASC))
				.build();
	}

	@Bean
	@StepScope
	public P09372Writer P09372Writer(@Value("#{jobParameters['letterFile']}") String letterFile,
			@Value("#{jobParameters['letterXml']}") String letterXml,
			@Value("#{jobParameters['deleteFile']}") String deleteFile,
			@Value("#{jobParameters['suspenseFile']}") String suspenseFile,
			@Value("#{jobParameters['letterCntFile']}") String letterCntFile, IP09CashReceiptRepository cashRepo,
			IP09SuspenseRepository suspenseRepo) {

		log.info(
				"Instantiating P09372Writer with parameters: letterFile={}, letterXml={}, deleteFile={}, suspenseFile={}, letterCntFile={}",
				letterFile, letterXml, deleteFile, suspenseFile, letterCntFile);

		return new P09372Writer(letterFile, letterXml, deleteFile, suspenseFile, letterCntFile, cashRepo, suspenseRepo);
	}
}