package com.abcbs.crrs.jobs.P09180;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class XmlInput {

    private String ccmInfo;
    private String memberId;
    private String ccmType;
    private String lob;
    private String busLocation;
    private String locationNbr;
    private String otisNbr;
    private String sectionCode;
    private String refundType;
    private String controlDate;
    private String controlNbr;
    private String statusCode;
    private String statusDate;
    private String eobInd;
    private String receiptType;
    private String remittorName;
    private String remittorTitle;
    private String remittorType;
    private String claimType;
    private String oplInd;
    private String letterDate;
    private String reasonCode;
    private String otherCorr;
    private String comments;
    private String patientFname;
    private String patientLname;
    private String addr1;
    private String addr2;
    private String city;
    private String state;
    private String zip;
    private String checkDate;
    private String checkNbr;
    private String checkAmount;
    private String controlledAmount;
    private String locationCode;
    private String formCount;
    private String totalControlledAmount;
}



