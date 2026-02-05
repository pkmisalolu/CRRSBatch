package com.abcbs.crrs.listener;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

@Component
public class JobLoggingListener implements JobExecutionListener  {

	private static final Logger logger = LogManager.getLogger(JobLoggingListener.class);

	@Override
	public void beforeJob(JobExecution jobExecution) {
		logger.info("Job started: {}", jobExecution.getJobInstance().getJobName());
		logger.info("Start Time: {}", jobExecution.getStartTime());
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		logger.info("Job finished with status: {}", jobExecution.getStatus());
		logger.info("End Time: {}", jobExecution.getEndTime());

		Long startTimeMillis = null;
		Long endTimeMillis = null;

		Object startTime = jobExecution.getStartTime();
		Object endTime = jobExecution.getEndTime();

		startTimeMillis = getTimeMillis((LocalDateTime) startTime);

		endTimeMillis = getTimeMillis((LocalDateTime) endTime);

		if (startTimeMillis != null && endTimeMillis != null) {
			long duration = endTimeMillis - startTimeMillis;
			logger.info("Total Time Taken (ms): {}", duration);
		} else {
			logger.warn("Could not compute total time taken due to missing start or end time.");
		}

		if (jobExecution.getStatus().isUnsuccessful()) {
			logger.error("Job failed: {}", jobExecution.getExitStatus().getExitCode());
		}
	}

	private Long getTimeMillis(LocalDateTime dateTime) {
		if (dateTime == null) {
			return null;
		}
		return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

}
