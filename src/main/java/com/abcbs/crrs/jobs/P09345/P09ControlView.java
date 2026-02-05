package com.abcbs.crrs.jobs.P09345;

import java.time.LocalDate;

public interface P09ControlView {

	
    String getRefundType();      // CNTRL_REFUND_TYPE
    LocalDate getToDate();       // CNTRL_TO_DATE
    String getRefundNarr();      // CNTRL_REFUND_NARR
}
