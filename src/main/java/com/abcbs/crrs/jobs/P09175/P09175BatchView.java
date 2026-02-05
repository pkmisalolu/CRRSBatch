package com.abcbs.crrs.jobs.P09175;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface P09175BatchView {

    String     getRefundType();
    String     getBatchPrefix();
    String 	   getBatchDate();
    String     getBatchSuffix();
    Integer    getBatchCnt();
    BigDecimal getBatchAmt();
    LocalDate  getControlDate();
    LocalDate  getReceivedDate();
    LocalDate  getDepositDate();
    LocalDate  getEntryDate();
    LocalDate  getStatusDate();
    String     getStatus();
}
