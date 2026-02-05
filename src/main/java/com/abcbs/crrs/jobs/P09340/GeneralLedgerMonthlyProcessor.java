package com.abcbs.crrs.jobs.P09340;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.abcbs.crrs.projections.IP09340ControlView;
import com.abcbs.crrs.repository.IP09ControlRepository;

@Component
public class GeneralLedgerMonthlyProcessor  implements ItemProcessor<P09340GlRecord, P09340GlRecord>{

	@Autowired
	private IP09ControlRepository controlRepo;

	private P09340InputRecord inputRec;
	private boolean ledgerEndFlag = false;

	private String holdAccountNbr;

	private static final Logger logger = LogManager.getLogger(GeneralLedgerWeeklyProcessor.class);

	@BeforeStep
	public void beforeStep(StepExecution stepExecution) {
		this.inputRec = (P09340InputRecord)
				stepExecution.getJobExecution().getExecutionContext().get("p09340InputRecord");
	}

	@Override
	public P09340GlRecord process(P09340GlRecord item) throws Exception {

		IP09340ControlView rec = controlRepo.findControlRecord(inputRec.getRefundType(), "O");
		if (rec == null) {
			logger.error("ERROR IN READING DCLV-P09-CONTROL");
			logger.error("IN METHOD: getControlRecord()");
			logger.error("FOR REFUND TYPE => {}", inputRec.getRefundType());

			throw new IllegalStateException("Missing control record for refund type: " + inputRec.getRefundType());
		}
		
		if (item == null) {
			logger.info("GENERAL LEDGER file is empty or EOF reached — setting ledgerEndFlag = true");
		}

		item.setReportMo(String.valueOf(rec.getCntrlToDate().getMonthValue()));
		item.setReportDt(String.valueOf(rec.getCntrlToDate().getDayOfMonth()));
		item.setReportYr(String.valueOf(rec.getCntrlToDate().getYear()));
		String mon = String.format("%02d", Integer.parseInt(item.getReportMo()));
		String day   = String.format("%02d", Integer.parseInt(item.getReportDt()));

		inputRec.setReportDate(mon+"/"+day+"/"+item.getReportYr());
		
		// 1. CORP mismatch — skip record
		if (!nz(item.getGlCorp()).equals(nz(inputRec.getCorpNo()))) {
			logger.debug("Skipping record: GL-CORP [{}] != W-CORP-NO [{}]", item.getGlCorp(), inputRec.getCorpNo());
			return null;
		}

		String fileSelect = "PER".equalsIgnoreCase(item.getGlRefundType()) ? "A" : "B";
		String month = String.format("%02d", Integer.parseInt(item.getReportMo()));
		String year = item.getReportYr().substring(2);
		item.setFileSelect(fileSelect);
		//doubt select
		if (fileSelect.equals(inputRec.getSelect()) &&  (item.getGlReportMo().equals(month)&&item.getGlReportYr().equals(year))) {
			logger.debug("Skipping record: FILE_SELECT [{}] != [{}] or REPORT_DATE [{}] != [{}]", fileSelect, inputRec.getSelect(), item.getGlReportMo(), item.getGlReportYr());
			item.setOutputType("");
			//return null;
		}else {
			item.setOutputType("GENERAL_LEDGER");  // eligible for report
			logger.debug("Record matched for report: type={}, date={}", fileSelect, item.getOutputType());
			return item;
		}

		if (item.getGlAcctNbr().equals(holdAccountNbr)) {

			String acctNo = nz(item.getGlAcctNbr()).trim();
			if (acctNo.length() >= 4) {
				String prefix = acctNo.substring(0, 4);
				if (prefix.equals("1041") || prefix.equals("1082") ||
						prefix.equals("2800") || prefix.equals("1271")) {
					//write to AccountFile
				}else {
					holdAccountNbr = item.getGlAcctNbr();
				}
			}
		}else {
			holdAccountNbr = item.getGlAcctNbr();
		}

		return item;
	}

	@AfterStep
	public ExitStatus afterStep(StepExecution stepExecution) {
		stepExecution.getExecutionContext().put("ledgerEndFlag", ledgerEndFlag);
		logger.info("Stored ledgerEndFlag={} in ExecutionContext", ledgerEndFlag);
		return ExitStatus.COMPLETED;
	}

	private static String nz(String s) {
		return s == null ? "" : s.trim();
	}
	

}
