package com.abcbs.crrs.projections;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface IP09340ControlView {
	
	String getCntrlRefundType();
    LocalDate getCntrlFromDate();
    LocalDate getCntrlToDate();
    Integer getCntrlReceiptCnt();
    BigDecimal getCntrlReceiptAmt();
    String getCntrlRefundNarr();

}
