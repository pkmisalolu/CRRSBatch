package com.abcbs.crrs.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class JobLauncherRunner implements CommandLineRunner {

	@Autowired
	private JobLauncher jobLauncher;

	@Autowired
	private Job p09186Job;

	@Autowired
	private Job p09373Job;

	@Autowired
	private Job p09360Job;

	@Autowired
	private Job p09375Job;

	@Autowired
	private Job p09320Job;

	@Autowired
	private Job p09321Job;

	@Autowired
	private JobExplorer jobExplorer;
	@Autowired
	@Qualifier("p09185Job")
	private Job p09185Job;
	@Autowired
	@Qualifier("p09182Job")
	private Job p09182Job;
	@Autowired
	@Qualifier("p09310Job")
	private Job p09310Job;
	@Autowired
	@Qualifier("p09340Job")
	private Job p09340Job;

	@Autowired
	@Qualifier("p09390Job")
	private Job p09390Job;

	@Autowired
	@Qualifier("p09352Job")
	private Job p09352Job;

	@Autowired
	@Qualifier("p09305Job")
	private Job p09305Job;

	@Autowired

	@Qualifier("p09315Job")
	private Job p09315Job;

	@Autowired

	@Qualifier("p09325Job")
	private Job p09325Job;

	@Autowired
	@Qualifier("p09376Job")
	private Job p09376Job;

	@Autowired
	@Qualifier("p09330Job")
	private Job p09330Job;

	@Autowired
	@Qualifier("p09370Job")
	private Job p09370Job;

	@Autowired
	@Qualifier("p09183Job")
	private Job p09183Job;

	@Autowired
	private Job p09180Job;
	@Autowired
	@Qualifier("p09181Job")
	private Job p09181Job;

	@Autowired
	@Qualifier("p09365Job")
	private Job p09365Job;

	@Autowired
	@Qualifier("p09345Job")
	private Job p09345Job;

	@Autowired
	private Job P09372Job;

	@Autowired
	@Qualifier("p09175Job")
	private Job p09175Job;

	private static final Logger logger = LogManager.getLogger(JobLauncherRunner.class);

	@Override
	public void run(String... args) throws Exception {
		logger.info("Entered into run");
		JobExecution jobExecution;
		String jobName;

		if (args.length > 0) {
			jobName = args[0];
			if ("P09186".equals(jobName)) {
				logger.info("Requested job: P09186");

				if (args.length < 2) {
					logger.error("P09186 requires parameters: inputFile");
					throw new IllegalArgumentException("Missing parameters for P09186");
				}

				String inputFile = args[1];

				JobParameters jobParameters = new JobParametersBuilder().addString("inputFile", inputFile)
						.addLong("run.id", System.currentTimeMillis()).toJobParameters();

				jobExecution = jobLauncher.run(p09186Job, jobParameters);
				logger.info("Job {} finished with status: {}", jobName, jobExecution.getStatus());

			} else if ("P09373".equals(jobName)) {
				logger.info("Requested job: P09373");

				if (args.length < 2) {
					logger.error("P09373 requires parameters: inputFile");
					throw new IllegalArgumentException("Missing parameters for P09373");
				}

				String inputFile = args[1];

				JobParameters jobParameters = new JobParametersBuilder().addString("inputFile", inputFile)
						.addLong("run.id", System.currentTimeMillis()).toJobParameters();

				jobExecution = jobLauncher.run(p09373Job, jobParameters);
				logger.info("Job {} finished with status: {}", jobName, jobExecution.getStatus());

			} else if ("P09360".equals(jobName)) {
				logger.info("Requested job: {}", jobName);

				if (args.length < 2) {
					logger.error("P09360 requires: glInput reportOut");
					throw new IllegalArgumentException("Missing parameters for P09360");
				}
				String glInput = args[1];
				String reportOut = args[2];

				JobParameters params = new JobParametersBuilder().addString("glInput", glInput)
						.addString("reportOut", reportOut).addLong("run.id", System.currentTimeMillis())
						.toJobParameters();

				JobExecution exec = jobLauncher.run(p09360Job, params);
				logger.info("Job {} finished with status {}", jobName, exec.getStatus());
			} else if ("P09375".equals(jobName)) {
				logger.info("Requested job: {}", jobName);

				if (args.length < 7) {
					logger.error("P09375 requires: label label1 label2 label3 labelCnt controlFile");
					throw new IllegalArgumentException("Missing parameters for P09375");
				}
				String controlFile = args[1];
				String label = args[2];
				String label1 = args[3];
				String label2 = args[4];
				String label3 = args[5];
				String labelCnt = args[6];

				JobParameters params = new JobParametersBuilder().addString("label", label).addString("label1", label1)
						.addString("label2", label2).addString("label3", label3).addString("labelCnt", labelCnt)
						.addString("controlFile", controlFile).addLong("run.id", System.currentTimeMillis())
						.toJobParameters();

				JobExecution exec = jobLauncher.run(p09375Job, params);
				logger.info("Job {} finished with status {}", jobName, exec.getStatus());
			} else if ("P09320".equals(jobName)) {
				logger.info("Requested job: {}", jobName);

				if (args.length < 3) {
					logger.error("P09320 requires: input reportOut");
					throw new IllegalArgumentException("Missing parameters for P09320");
				}
				String input = args[1];
				String reportOut = args[2];

				JobParameters params = new JobParametersBuilder().addString("input", input)
						.addString("reportOut", reportOut).addLong("run.id", System.currentTimeMillis())
						.toJobParameters();

				JobExecution exec = jobLauncher.run(p09320Job, params);
				logger.info("Job {} finished with status {}", jobName, exec.getStatus());
			} else if ("P09321".equals(jobName)) {
				logger.info("Requested job: {}", jobName);

				if (args.length < 4) {
					logger.error("P09321 requires: input reportOut");
					throw new IllegalArgumentException("Missing parameters for P09321");
				}
				String input = args[1];
				String corpFile = args[2];
				String reportOut = args[3];

				JobParameters params = new JobParametersBuilder().addString("input", input)
						.addString("corpFile", corpFile).addString("reportOut", reportOut)
						.addLong("run.id", System.currentTimeMillis()).toJobParameters();

				JobExecution exec = jobLauncher.run(p09321Job, params);
				logger.info("Job {} finished with status {}", jobName, exec.getStatus());
			} else if ("P09185".equals(jobName)) {
				logger.info("Requested for P09185");
				String param1 = null;
				if (args.length >= 2) {
					param1 = args[1];
					// Build job parameters
					JobParameters jobParameters = new JobParametersBuilder().addString("inputFile", param1)
							.addLong("run.id", System.currentTimeMillis()).toJobParameters();
					// Launch the job with the parameters
					jobExecution = jobLauncher.run(p09185Job, jobParameters);

					String exitCode = jobExecution.getExitStatus().getExitCode();
					logger.info("Job finished with ExitStatus = {}", exitCode);

					if ("55".equals(exitCode)) {
						logger.warn("Exiting with RETURN-CODE 55");
						System.exit(55);
					} else {
						logger.info("Exiting with RETURN-CODE 0 (COMPLETED)");
						System.exit(0);
					}
				} else {
					logger.error("Insuffient number of parameters");
					throw new IllegalArgumentException("INSUFFICIENT NO. OF PARAMS");
				}

			} else if ("P09182".equals(jobName)) {
				logger.info("Requested for P09182");
				String param1 = null;
				String param2 = null;
				if (args.length > 2) {
					param1 = args[1];
					param2 = args[2];
					// Build job parameters
					JobParameters jobParameters = new JobParametersBuilder().addString("inputFile", param1)
							.addString("outputFile", param2).addLong("run.id", System.currentTimeMillis())
							.toJobParameters();
					// Launch the job with the parameters
					jobExecution = jobLauncher.run(p09182Job, jobParameters);
				} else {
					logger.error("Insuffient number of parameters");
					throw new IllegalArgumentException("INSUFFICIENT NO. OF PARAMS");
				}
			} else if ("P09310".equals(jobName)) {
				logger.info("Requested for P09310");
				String param1 = null;
				String param2 = null;
				if (args.length > 2) {
					param1 = args[1];
					param2 = args[2];
					// Build job parameters
					JobParameters jobParameters = new JobParametersBuilder().addString("chkpFile", param1)
							.addString("outputFile", param2).addLong("run.id", System.currentTimeMillis())
							.toJobParameters();
					// Launch the job with the parameters
					jobExecution = jobLauncher.run(p09310Job, jobParameters);
				} else {
					logger.error("Insuffient number of parameters");
					throw new IllegalArgumentException("INSUFFICIENT NO. OF PARAMS");
				}
			} else if ("P09340".equals(jobName)) {
				logger.info("Requested for P09310");
				String param1 = null;
				String param2 = null;
				String param3 = null;
				String param4 = null;
				String param5 = null;
				String param6 = null;
				if (args.length > 2) {
					param1 = args[1];
					param2 = args[2];
					param3 = args[3];
					param4 = args[4];
					param5 = args[5];
					param6 = args[6];
					// Build job parameters
					JobParameters jobParameters = new JobParametersBuilder().addString("corpFile", param1)
							.addString("controlFile", param2).addString("glFile", param3)
							.addString("reportFile", param4).addString("matchedOutFile", param5)
							.addString("accountFile", param6).addLong("run.id", System.currentTimeMillis())
							.toJobParameters();
					// Launch the job with the parameters
					jobExecution = jobLauncher.run(p09340Job, jobParameters);
				} else {
					logger.error("Insuffient number of parameters");
					throw new IllegalArgumentException("INSUFFICIENT NO. OF PARAMS");
				}
			} else if ("P09390".equals(jobName)) {
				logger.info("Requested for P09390");

				if (args.length >= 4) {

					String newVendorFile = args[1];
					String fepmanFile = args[2];
					String outputFile = args[3];

					JobParameters jobParameters = new JobParametersBuilder().addString("newVendorFile", newVendorFile)
							.addString("fepmanFile", fepmanFile).addString("outputFepVendor", outputFile)
							.addLong("run.id", System.currentTimeMillis()).toJobParameters();

					jobExecution = jobLauncher.run(p09390Job, jobParameters);

				} else {
					logger.error("Insufficient number of parameters for P09390");
					throw new IllegalArgumentException("INSUFFICIENT NO. OF PARAMS FOR P09390");
				}
			} else if ("P09352".equals(jobName)) {
			    logger.info("Requested for P09352");
			    if (args.length >= 11) {

			        String jobType = args[1];
			        String datePicker = args[2];
			        String corpCardFile    = args[3];
			        String checkpointFile  = args[4];
			        String ivoucherFile    = args[5];

			        String xapntrfcPath    = args[6];
			        String xp09Path        = args[7];
			        String xp07Path        = args[8];
			        String xvoucherPath    = args[9];
			        String reportPath      = args[10];
			        JobParameters jobParameters = new JobParametersBuilder()
			                .addString("jobType", jobType)
			                .addString("datePicker", datePicker)
			                .addString("corpCardFile", corpCardFile)
			                .addString("checkpointFile", checkpointFile)
			                .addString("ivoucherFile", ivoucherFile)
			                .addString("xapntrfcPath", xapntrfcPath)
			                .addString("xp09Path", xp09Path)
			                .addString("xp07Path", xp07Path)
			                .addString("xvoucherPath", xvoucherPath)
			                .addString("reportPath", reportPath)
			                .addLong("run.id", System.currentTimeMillis())
			                .toJobParameters();

			        jobExecution = jobLauncher.run(p09352Job, jobParameters);
			        logger.info("Job {} finished with status: {}", jobName, jobExecution.getStatus());

			    } else {
			        logger.error("Insufficient number of parameters for P09352");
			        throw new IllegalArgumentException(
			                "P09352 requires parameters: controlCardFile corpCardFile checkpointFile ivoucherFile xapntrfcPath xp09Path xp07Path xvoucherPath reportPath");
			    }
			} else if ("P09325".equals(jobName)) {
				if (args.length < 3) {
					logger.error(
							"Usage: java -jar crrs.jar P09325 <checkpointFile> <outputA> <outputB> [Y|N] [checkpointKey]");
					System.exit(1);
				}
				String chkpFile = args[1]; // Control / checkpoint file
				String outA = args[2]; // Routing report
				String outB = args[3]; // Summary report
				String ckptKey = ""; // Optional restart key
				logger.info("Checkpoint File: " + chkpFile);
				logger.info("Output A (Routing): " + outA);
				logger.info("Output B (Summary): " + outB);
				logger.info("Checkpoint Key: " + (ckptKey.isEmpty() ? "(none)" : ckptKey));
				logger.info("---------------------------------------------------");
				JobParameters params = new JobParametersBuilder().addString("chkpFile", chkpFile)
						.addString("outA", outA).addString("outB", outB).addString("checkpointKey", ckptKey)
						.addLong("run.id", System.currentTimeMillis()).toJobParameters();
				try {
					jobExecution = jobLauncher.run(p09325Job, params);
					logger.info("JobExecution ID: " + jobExecution.getId());
					logger.info("Status: " + jobExecution.getStatus());
					logger.info("Exit Status: " + jobExecution.getExitStatus());
				} catch (JobExecutionAlreadyRunningException | JobRestartException | JobInstanceAlreadyCompleteException
						| JobParametersInvalidException e) {
					logger.error(" Job failed to start: " + e.getMessage());
					e.printStackTrace();
					System.exit(1);
				}
			} else if ("P09315".equals(jobName)) {
				String corpFile = args[1];
				String output = args[2];
				JobParameters params = new JobParametersBuilder().addString("corpFile", corpFile)
						.addString("outputFile", output).addLong("run.id", System.currentTimeMillis())
						.toJobParameters();
				jobExecution = jobLauncher.run(p09315Job, params);

			} else if ("P09305".equals(args[0])) {

				String chkpFile = args[1];
				String corpFile = args[2];
				String output = args[3];
				String checkpointKey = (args.length > 4) ? args[4] : ""; // seed (optional)

				logger.info("Checkpoint File: " + chkpFile);
				logger.info("corpFile : " + corpFile);
				logger.info("Output File: " + output);

				logger.info("checkpoin tKey: " + checkpointKey);

				logger.info("---------------------------------------------------");

				JobParameters params = new JobParametersBuilder().addString("chkpFile", chkpFile)
						.addString("corpFile", corpFile).addString("outputFile", output)
						.addString("checkpointKey", checkpointKey).addLong("run.id", System.currentTimeMillis())
						.toJobParameters();

				JobExecution exec = jobLauncher.run(p09305Job, params);
				if (!exec.getExitStatus().equals(ExitStatus.COMPLETED)) {
					throw new IllegalStateException("P09305 failed: " + exec.getExitStatus());
				}
			} else if ("P09376".equals(jobName)) {
				logger.info("Requested job: {}", jobName);

				if (args.length < 3) {
					logger.error("P09376 requires: label label1 label2 label3 labelCnt controlFile");
					throw new IllegalArgumentException("Missing parameters for P09376");
				}
				String controlFile = args[1];
				String label = args[2];

				JobParameters params = new JobParametersBuilder().addString("label", label)
						.addString("controlFile", controlFile).addLong("run.id", System.currentTimeMillis())
						.toJobParameters();

				JobExecution exec = jobLauncher.run(p09376Job, params);
				logger.info("Job {} finished with status {}", jobName, exec.getStatus());
			} else if ("P09330".equals(jobName)) {
				logger.info("Requested job: {}", jobName);

				if (args.length < 2) {
					logger.error("P09330 requires: jobName outputFile");
					throw new IllegalArgumentException("Missing parameters for P09330");
				}

				String outputFile = args.length > 0 ? args[1] : null;
				JobParameters params = new JobParametersBuilder().addString("outputFile", outputFile)
						.addLong("run.id", System.currentTimeMillis()).toJobParameters();

				logger.info("Launching P09330 with outputFile={}", outputFile);
				JobExecution exec = jobLauncher.run(p09330Job, params);
				logger.info("Job {} finished with status {}", jobName, exec.getStatus());
			} else if ("P09345".equals(jobName)) {

				logger.info("Requested job: {}", jobName);

				if (args == null || args.length < 5) {
					logger.error("P09345 requires: Corp_File Refund_Type_Card controlFile runIdLabel");
					throw new IllegalArgumentException("Missing parameters for P09345");
				}
				String Corp_File = args[1];
				String Refund_Type_Card = args[2];
				String Checkpoint_Card = args[3];
				String P09345_Output = args[4];

				logger.info("Requested job: P09345, Corp_File={}, Refund_Type_Card={}, controlFile={}", Corp_File,
						Refund_Type_Card, Checkpoint_Card);

				JobExecution exec = jobLauncher.run(p09345Job,
						new JobParametersBuilder().addString("Corp_File", Corp_File)
								.addString("Refund_Type_Card", Refund_Type_Card)
								.addString("Checkpoint_Card", Checkpoint_Card).addString("P09345_Output", P09345_Output)
								.addLong("run.id", System.currentTimeMillis()).toJobParameters());

				logger.info("Job P09345 finished with status {}", exec.getStatus());
			} else if ("P09180".equals(jobName)) {
				logger.info("Requested job: {}", jobName);

				if (args.length < 4) {
					logger.error("P09180 requires: input reportOut");
					throw new IllegalArgumentException("Missing parameters for P09180");
				}
				String ccmFile = args[1];
				String ccmTotals = args[2];
				String ccmXmlFile = args[3];

				JobParameters params = new JobParametersBuilder().addString("ccmFile", ccmFile)
						.addString("ccmTotals", ccmTotals).addString("ccmXmlFile", ccmXmlFile)
						.addLong("run.id", System.currentTimeMillis()).toJobParameters();

				JobExecution exec = jobLauncher.run(p09180Job, params);
				logger.info("Job {} finished with status {}", jobName, exec.getStatus());
			} else if ("P09181".equals(jobName)) {
				logger.info("Requested job: P09181");
				String inputFile = args[1];
				String outputFile = args[2];
				JobParameters jobParameters = new JobParametersBuilder().addString("inputFile", inputFile)
						.addString("outputFile", outputFile).addLong("run.id", System.currentTimeMillis())
						.toJobParameters();
				jobExecution = jobLauncher.run(p09181Job, jobParameters);

				logger.info("Job {} finished with status: {}", jobName, jobExecution.getStatus());
			} else if ("P09365".equals(jobName)) {
				logger.info("Requested for P09365");

				if (args.length >= 3) {

					String inputFile = args[1];
					String outputFile = args[2];

					JobParameters jobParameters = new JobParametersBuilder().addString("inputFile", inputFile)
							.addString("outputFile", outputFile).addLong("run.id", System.currentTimeMillis())
							.toJobParameters();

					jobExecution = jobLauncher.run(p09365Job, jobParameters);

				} else {
					logger.error("Insufficient number of parameters for P09365");
					throw new IllegalArgumentException("INSUFFICIENT NO. OF PARAMS FOR P09365");
				}
			} else if ("P09372".equals(jobName)) {
				logger.info("Requested job: {}", jobName);

				if (args.length < 6) {
					logger.error("P09372 requires: 5 Output files");
					throw new IllegalArgumentException("Missing parameters for P09372");
				}
				String letterFile = args[1];
				String letterXml = args[2];
				String deleteFile = args[3];
				String suspenseFile = args[4];
				String letterCntFile = args[5];

				JobParameters params = new JobParametersBuilder().addString("letterFile", letterFile)
						.addString("letterXml", letterXml).addString("deleteFile", deleteFile)
						.addString("suspenseFile", suspenseFile).addString("letterCntFile", letterCntFile)
						.addLong("run.id", System.currentTimeMillis()).toJobParameters();

				JobExecution exec = jobLauncher.run(P09372Job, params);
				logger.info("Job {} finished with status {}", jobName, exec.getStatus());
			} else if ("P09175".equals(jobName)) {
				logger.info("Requested for P09175");

				if (args.length >= 4) {

					String P09175_ReportOutput = args[1];
					String P09175_CcmOutput = args[2];
					String P09175_ControlTotal = args[3];

					JobParameters jobParameters = new JobParametersBuilder()
							.addString("P09175_ReportOutput", P09175_ReportOutput)
							.addString("P09175_CcmOutput", P09175_CcmOutput)
							.addString("P09175_ControlTotal", P09175_ControlTotal)
							.addLong("run.id", System.currentTimeMillis()).toJobParameters();

					jobExecution = jobLauncher.run(p09175Job, jobParameters);

				} else {
					logger.error("Insufficient number of parameters for P09175");
					throw new IllegalArgumentException("INSUFFICIENT NO. OF PARAMS FOR P09175");
				}
			} else if ("P09370".equals(jobName)) {
				logger.info("Requested for P09370");
				if (args.length >= 3) {
					String param1 = args[1];
					String param2 = args[2];
					JobParameters jobParameters = new JobParametersBuilder().addString("inputFile", param1)
							.addString("outputFile", param2).addLong("run.id", System.currentTimeMillis())
							.toJobParameters();
					jobExecution = jobLauncher.run(p09370Job, jobParameters);
				} else {
					logger.error("Insufficient number of parameters");
					throw new IllegalArgumentException("INSUFFICIENT NO. OF PARAMS");
				}
			} else if ("P09183".equals(jobName)) {
				logger.info("Requested for P09183");
				String param1 = null;
				String param2 = null;
				if (args.length > 2) {
					param1 = args[1];
					param2 = args[2];
					// Build job parameters
					JobParameters jobParameters = new JobParametersBuilder().addString("inputFile", param1)
							.addString("outputFile", param2).addLong("run.id", System.currentTimeMillis())
							.toJobParameters();
					// Launch the job with the parameters
					jobExecution = jobLauncher.run(p09183Job, jobParameters);
				} else {
					logger.error("Insuffient number of parameters");
					throw new IllegalArgumentException("INSUFFICIENT NO. OF PARAMS");
				}
			} else {
				logger.error("Job has not found");
				throw new IllegalArgumentException("JOB NOT FOUND");
			}
		}
	}
}
