package com.abcbs.crrs.jobs.P09325;

import java.math.BigDecimal;

public interface P09325kHeaderView {

    String getArea();      // SUM_LOCATION_ID
    String getClerk();     // SUM_CLERK_ID

    long getBeginCnt();
    BigDecimal getBeginAmt();

    long getRecvCnt();
    BigDecimal getRecvAmt();

    long getFwdCnt();
    BigDecimal getFwdAmt();

    long getEndCnt();
    BigDecimal getEndAmt();
}
