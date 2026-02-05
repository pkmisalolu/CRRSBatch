package com.abcbs.crrs.jobs.P09310;

import java.math.BigDecimal;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.abcbs.crrs.entity.P09Batch;
import com.abcbs.crrs.projections.P09310SuspenseView;
import com.abcbs.crrs.repository.IP09SuspenseRepository;

@Component
public class P09310Processor implements ItemProcessor<P09Batch, List<P09310OutputRecord>> {

	private static final Logger logger = LogManager.getLogger(P09310Processor.class);

	@Autowired
	private IP09SuspenseRepository suspenseRepo;

	//counts and amounts
	private int wsTotalCnt;
	private BigDecimal wsTotalAmount;
	private int wsTotalPerCount;
	private BigDecimal wsTotalPerAmount;
	private int wsTotalRetCount;
	private BigDecimal wsTotalRetAmount;
	private int wsTotalUndCount;
	private BigDecimal wsTotalUndAmount;
	private int wsTotalOthCount;
	private BigDecimal wsTotalOthAmount;
	private int wsTotalOffCount;
	private BigDecimal wsTotalOffAmount;
	private int wsTotalSpoCount;
	private BigDecimal wsTotalSpoAmount;
	private int wsTotalApiCount;
	private BigDecimal wsTotalApiAmount;

	@Override
	public List<P09310OutputRecord> process(P09Batch batch) {

		logger.debug("Processing batch record with refundType={}, prefix={}, date={}, suffix={}", batch.getBId().getCrRefundType(), batch.getBId().getBtBatchPrefix(),
				batch.getBId().getBtBatchDate(), batch.getBId().getBtBatchSuffix());


		List<P09310SuspenseView> list = suspenseRepo.findSuspenseSubset(batch.getBId().getBtBatchPrefix(), batch.getBId().getBtBatchDate(), batch.getBId().getBtBatchSuffix(),
				batch.getBId().getCrRefundType());

		List<P09310OutputRecord> results = new java.util.ArrayList<>();

		for (P09310SuspenseView view : list) {
			logger.debug("Records fetched from Suspense table");
			P09310OutputRecord out = new P09310OutputRecord();

			out.setWsRefTypeSpace1(" ");
			out.setWsRefTypeSlash("/");
			out.setWsRefTypeSpace2(" ");
			out.setWsBatchSpace1(" ");
			out.setWsBatchSpace2(" ");

			// batch fields
			out.setCrRefundType(batch.getBId().getCrRefundType());
			out.setBtBatchPrefix(batch.getBId().getBtBatchPrefix());
			out.setBtBatchDate(batch.getBId().getBtBatchDate());
			out.setBtBatchSuffix(batch.getBId().getBtBatchSuffix());
			out.setBtBatchCnt(batch.getBtBatchCnt());
			out.setBtBatchAmt(batch.getBtBatchAmt());
			out.setBtEntryDate(batch.getBtEntryDate());
			out.setBtPostedInd(batch.getBtPostedInd());

			// suspense fields
			out.setCrCntrlDate(view.getCrCntrlDate());
			out.setCrCheckDate(view.getCrCheckDate());
			out.setCrPatientLname(view.getCrPatientLname());
			out.setCrPatientFname(view.getCrPatientFname());
			out.setWsPatNameDelim(
					(isBlank(view.getCrPatientLname()) && isBlank(view.getCrPatientFname())) ? " " : ","
					);
			out.setCrRemittorName(view.getCrRemittorName());
			out.setCrCntrlNbr(view.getCrCntrlNbr());
			out.setCrCntrldAmt(view.getCrCntrldAmt());
			out.setCrCheckNbr(view.getCrCheckNbr());
			out.setCrCheckAmt(view.getCrCheckAmt());
			out.setCrReceiptType(view.getCrReceiptType());
			out.setCrClaimType(view.getCrClaimType());
			out.setCrMbrIdNbr(view.getCrMbrIdNbr());
			out.setCrReasonCode(view.getCrReasonCode());
			out.setCrGlAcctNbr(view.getCrGlAcctNbr());
			out.setCrRemittorTitle(view.getCrRemittorTitle());

			out.setGroupName("         ");


			//performing calculations
			String refundType = out.getCrRefundType();

			if(wsTotalAmount==null)
				wsTotalAmount = new BigDecimal(0);
			if(wsTotalPerAmount==null)
				wsTotalPerAmount = new BigDecimal(0);
			if(wsTotalRetAmount==null)
				wsTotalRetAmount = new BigDecimal(0);
			if(wsTotalUndAmount==null)
				wsTotalUndAmount = new BigDecimal(0);
			if(wsTotalOthAmount==null)
				wsTotalOthAmount = new BigDecimal(0);
			if(wsTotalOffAmount==null)
				wsTotalOffAmount = new BigDecimal(0);
			if(wsTotalSpoAmount==null)
				wsTotalSpoAmount = new BigDecimal(0);
			if(wsTotalApiAmount==null)
				wsTotalApiAmount = new BigDecimal(0);


			wsTotalCnt = wsTotalCnt+1;
			wsTotalAmount = wsTotalAmount.add(out.getCrCntrldAmt());

			if ("PER".equalsIgnoreCase(refundType)) {
				wsTotalPerCount = wsTotalPerCount + 1;
				wsTotalPerAmount = wsTotalPerAmount.add(out.getCrCntrldAmt());
			} else if ("RET".equalsIgnoreCase(refundType)) {
				wsTotalRetCount = wsTotalRetCount + 1;
				wsTotalRetAmount = wsTotalRetAmount.add(out.getCrCntrldAmt());
			} else if ("UND".equalsIgnoreCase(refundType)) {
				wsTotalUndCount = wsTotalUndCount + 1;
				wsTotalUndAmount = wsTotalUndAmount.add(out.getCrCntrldAmt());
			} else if ("OTH".equalsIgnoreCase(refundType)) {
				wsTotalOthCount = wsTotalOthCount + 1;
				wsTotalOthAmount = wsTotalOthAmount.add(out.getCrCntrldAmt());
			} else if ("OFF".equalsIgnoreCase(refundType)) {
				wsTotalOffCount = wsTotalOffCount + 1;
				wsTotalOffAmount = wsTotalOffAmount.add(out.getCrCntrldAmt());
			} else if ("SPO".equalsIgnoreCase(refundType)) {
				wsTotalSpoCount = wsTotalSpoCount + 1;
				wsTotalSpoAmount = wsTotalSpoAmount.add(out.getCrCntrldAmt());
			} else if ("API".equalsIgnoreCase(refundType)) {
				wsTotalApiCount = wsTotalApiCount + 1;
				wsTotalApiAmount = wsTotalApiAmount.add(out.getCrCntrldAmt());
			}

			//copying above values to output
			out.setTotalCount(wsTotalCnt);
			out.setTotalAmount(wsTotalAmount);

			out.setTotalPerCount(wsTotalPerCount);
			out.setTotalPerAmount(wsTotalPerAmount);

			out.setTotalRetCount(wsTotalRetCount);
			out.setTotalRetAmount(wsTotalRetAmount);

			out.setTotalUndCount(wsTotalUndCount);
			out.setTotalUndAmount(wsTotalUndAmount);

			out.setTotalOthCount(wsTotalOthCount);
			out.setTotalOthAmount(wsTotalOthAmount);

			out.setTotalOffCount(wsTotalOffCount);
			out.setTotalOffAmount(wsTotalOffAmount);

			out.setTotalSpoCount(wsTotalSpoCount);
			out.setTotalSpoAmount(wsTotalSpoAmount);

			out.setTotalApiCount(wsTotalApiCount);
			out.setTotalApiAmount(wsTotalApiAmount);

			out.setGroupName("         ");


			results.add(out);
		}
		return results;
	}
	
	private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
