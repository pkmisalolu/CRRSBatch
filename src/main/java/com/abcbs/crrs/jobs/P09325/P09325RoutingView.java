package com.abcbs.crrs.jobs.P09325;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface P09325RoutingView {

    String getClerk();
    String getArea();

    LocalDate getCntrlDate();
    String getControlNbr();
    String getRefundType();

    BigDecimal getControlAmt();

    String getPatientLast();
    String getPatientFirst();
    String getPatientId();

    String getRemittor();
    String getReasonCode();
    String getReceiptType();
    String getCheckNbr();

    String getStatusText();
    LocalDate getRecvDate();
    LocalDate getLocationDate();
}
