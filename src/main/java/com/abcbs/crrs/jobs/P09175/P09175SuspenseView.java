package com.abcbs.crrs.jobs.P09175;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface P09175SuspenseView {

    LocalDate getCntrlDate();
    String    getCntrlNbr();
    String    getBankAcctNbr();
    String    getCheckNbr();
    LocalDate getCheckDate();
    BigDecimal getCheckAmt();
    BigDecimal getControlledAmt();
    String    getReceiptType();
    String    getRemittorName();
    String    getRemittorType();
    String    getClaimType();
    String    getOplInd();
    String    getPatientLast();
    String    getPatientFirst();
    String    getReasonCode();
    String    getEobRaInd();
    String    getOtherCorr();
    String    getProviderNbr();
    String    getMbrIdNbr();
    String    getTaxIdNbr();
    String    getVendorNbr();
    String    getOtisNbr();
    LocalDate getLetterDate();
    LocalDate getAcctsRecDate();
    String    getAcctsRecNbr();
    String    getCorp();
    String    getLocationNbr();
    String    getLocationClerk();
    String    getSectionCode();
    String    getChkAddress1();
    String    getChkAddress2();
    String    getChkCity();
    String    getChkState();
    String    getChkZip5();
    String    getChkZip4();
    String    getUserId();
    String    getCommentText();
    String    getRemittorTitle();
    String    getNationalIdNbr();
}
