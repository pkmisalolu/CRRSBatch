package com.abcbs.crrs.jobs.P09376;

import java.time.LocalDate;

public interface P09376DailyRemittanceView {
    String getCrRefundType();
    LocalDate getCrCntrlDate();
    String getCrCntrlNbr();
    String getCrRemDailyInd();
    String getCrRemIdType();
    String getCrRemIdNbr();
    String getCrRemittorName();
    String getCrRemAddressee();
    String getCrRemAddress1();
    String getCrRemAddress2();
    String getCrRemCity();
    String getCrRemState();
    String getCrRemZip5();
    String getCrRemZip4();
    String getCrChkAddress1();
    String getCrChkAddress2();
    String getCrChkCity();
    String getCrChkState();
    String getCrChkZip5();
    String getCrChkZip4();
    String getCrUserId();
}

