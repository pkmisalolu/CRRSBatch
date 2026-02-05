package com.abcbs.crrs.jobs.P09340;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@StepScope
public class CorpControlFileReaderTasklet implements Tasklet {

	private static final Logger logger = LogManager.getLogger(CorpControlFileReaderTasklet.class);

	private final Resource corpRes;     
	private final Resource controlRes;


	public CorpControlFileReaderTasklet(@Value("#{jobParameters['corpFile']}") String corpFilePath, 
			@Value("#{jobParameters['controlFile']}") String controlFilePath) {
		this.corpRes = new FileSystemResource(corpFilePath);
		this.controlRes = new FileSystemResource(controlFilePath);
	}

	@Override
	public RepeatStatus execute(StepContribution sc, ChunkContext cc) throws Exception {
		logger.info("Reading control inputs for P09340. Corp File: {}, Control File: {}",
                corpRes.getFilename(), controlRes.getFilename());
		String corpLine    = firstLine(corpRes);
		String controlLine = firstLine(controlRes);

		P09340InputRecord rec = new P09340InputRecord();
		// parse according to your fixed positions
		rec.setCorpId(corpLine.substring(0, 2));
		rec.setCorpProgramId(corpLine.substring(2, 8));
		rec.setCorpSequence(corpLine.substring(8, 9));
		rec.setCorpNo(corpLine.substring(10, 12));
		rec.setCorpCode(corpLine.substring(22, 55));

		rec.setRunFrequency(controlLine.substring(14, 15)); // D/W/M
		rec.setRefundType(controlLine.substring(10,13));

		switch (rec.getCorpNo().trim()) {
		case "01":
			rec.setCorpCode("CORP 01 - BCBS                   ");
			rec.setCorpNo("01");
			break;
		case "03":
			rec.setCorpCode("CORP 03 - HMO PARTNERS           ");
			rec.setCorpNo("03");
			break;
		case "04":
			rec.setCorpCode("CORP 04 - ABCBS MEDIPAK ADVANTAGE");
			rec.setCorpNo("04");
			break;
		default:
			rec.setCorpCode("CORP " + rec.getCorpNo() + " - UNKNOWN         ");
			break;
		}//end switch

		if ("PER".equalsIgnoreCase(rec.getRefundType())) {
			rec.setSelect("A");
		} else {
			rec.setSelect("B");
		}
		logger.info("Parsed CorpNo: {} | RefundType: {} | Select Mode: {}",
                rec.getCorpNo(), rec.getRefundType(), rec.getSelect());
		
		cc.getStepContext().getStepExecution().getJobExecution().getExecutionContext().put("p09340InputRecord", rec);
		
        logger.info("P09340InputRecord stored in ExecutionContext.");

		return RepeatStatus.FINISHED;
	}
	private static String firstLine(Resource r) throws IOException {
		if (!r.exists()) {
            logger.error("Control resource missing: {}", r);

			throw new FileNotFoundException("Resource not found: " + r);
		}
		try (BufferedReader br = new BufferedReader(new InputStreamReader(r.getInputStream(), StandardCharsets.UTF_8))) {
			String line = br.readLine();
			if (line == null) {
                logger.error("Control resource is empty: {}", r);
				throw new EOFException("Empty resource: " + r);
			}
			return line;
		}
	}
}
