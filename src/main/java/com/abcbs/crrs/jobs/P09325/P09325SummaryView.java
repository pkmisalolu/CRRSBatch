package com.abcbs.crrs.jobs.P09325;

import java.math.BigDecimal;

public interface P09325SummaryView {
	
	
	 String getArea();          // SUM_LOCATION_ID

	    BigDecimal getBeginAmt();  // SUM(SUM_BEGINNING_AMT)
	    long getBeginCnt();        // SUM(SUM_BEGINNING_CNT)

	    BigDecimal getRecvAmt();   // SUM(SUM_ADDITIONS_AMT)
	    long getRecvCnt();         // SUM(SUM_ADDITIONS_CNT)

	    BigDecimal getFwdAmt();    // SUM(SUM_DELETIONS_AMT)
	    long getFwdCnt();          // SUM(SUM_DELETIONS_CNT)

	    BigDecimal getEndAmt();    // SUM(SUM_ENDING_AMT)
	    long getEndCnt();          // SUM(SUM_ENDING_CNT)

}
