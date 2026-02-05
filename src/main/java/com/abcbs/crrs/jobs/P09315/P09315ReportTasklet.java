// src/main/java/com/abcbs/crrs/jobs/P09315/P09315ReportTasklet.java
package com.abcbs.crrs.jobs.P09315;

import com.abcbs.crrs.repository.IActivityRepository;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

public class P09315ReportTasklet implements Tasklet {

	private final P09315ReportWriter writer;
	private final IActivityRepository repo;
	private final String corpFile;
	private final String outputFile;
	

	public P09315ReportTasklet(P09315ReportWriter writer, IActivityRepository repo, String corpFile,
			String outputFile) {
		this.writer = writer;
		this.repo = repo;
		this.corpFile = corpFile;
		this.outputFile = outputFile;

	}

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext context) throws Exception {

		repo.lockActivityTable();
		CorpMeta corp = readCorp(corpFile);
		writer.open(outputFile, corp.corpNo(), corp.corpName(), LocalDateTime.now());

		// refund type order (same across all sections)
		List<String> refundOrder = List.of("PER", "RET", "UND", "OTH", "OFF", "SPO", "API");

		// === 000100-ESTABLISHED ===
		List<ActivityAggView> established = repo.fetchEstablished(corp.corpNo());
		writer.writeEstablished(established, refundOrder);

//		// === 000200-MANUAL-RECON ===
		List<ActivityAggView> manualRecon = repo.fetchManualRecon(corp.corpNo());
		writer.writeManualRecon(manualRecon, List.of("ACC", "APP", "REM", "DEL", "LOG", "FRR", "PRR"), refundOrder);

//		// === 000300-SYSTEM-RECON ===
		List<ActivityAggView> systemRecon = repo.fetchSystemRecon(corp.corpNo());
		writer.writeSystemRecon(systemRecon, List.of("FR", "PR"), refundOrder);

		// === 000400-MANUAL-REQUEST ===
//		List<ActivityAgg> manualRequest = repo.fetchManualRequest(corp.corpNo());
		writer.writeManualRequest(systemRecon, List.of("RAA", "RAD", "RAR", "RCK", "OTH", "RRE", "PEN", "CAN", "MOD"),
				refundOrder);

		// === 000500-UPDATE-TABLE ===

			int n = repo.clearDailyFlag(corp.corpNo());
			writer.noteUpdate(n);
		

//		// === 000600-FINALIZATION ===
		writer.close();
		return RepeatStatus.FINISHED;
	}

	private record CorpMeta(String corpNo, String corpName) {
	}

	private static CorpMeta readCorp(String r) throws IOException {
		// Assume first line: first 2 chars = corp no; rest = name (lenient).
		try (var br = new BufferedReader(new InputStreamReader(new FileSystemResource(r).getInputStream(), StandardCharsets.UTF_8))) {
			String line = Optional.ofNullable(br.readLine()).orElse("").trim();
			String no = line.length() >= 2 ? line.substring(10, 12).trim() : line;
			String name = line.length() > 2 ? line.substring(2).trim() : "";
			return new CorpMeta(no, name);
		}
	}
}
