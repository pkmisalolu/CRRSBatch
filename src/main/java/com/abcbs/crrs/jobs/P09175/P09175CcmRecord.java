package com.abcbs.crrs.jobs.P09175;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class P09175CcmRecord {

    private String formId;
    private String checkNumber;
    private String memberId;
    private String docType;
    private String plan;
    private String region;
    private String toDept;
    private String controlFrequency;
    private String refundType;
    private String controlDate;
    private String controlId;
    private String status;
    private String statusDate;
    private String eobAttached;
    private String receiptType;
    private String remittName;
    private String providerType;
    private String claimsType;
    private String opl;
    private String letterDate;
    private String reasonCode;
    private String comments;
    private String patientFirst;
    private String patientLast;
    private String address1;
    private String address2;
    private String city;
    private String state;
    private String zip;
    private String checkDate;
    private String checkNumberDup;
    private BigDecimal checkAmount;
    private BigDecimal controlledAmount;
    private String forwardDept;
}
