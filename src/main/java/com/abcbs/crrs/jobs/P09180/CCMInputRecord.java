package com.abcbs.crrs.jobs.P09180;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CCMInputRecord {

    private String cInfo;               // 1-27
    private String cTilde2;             // 28
    private String cMemberId;           // 29-48
    private String cTilde3;             // 49
    private String cCcmType;            // 50-52
    private String cTilde4;             // 53
    private String cBarcodeLob;          // 54-57
    private String cTilde5;             // 58
    private String cBarcodeLocNbr;       // 59-61
    private String cAsterisk2;           // 62
    private String cBusLocation;         // 63-82
    private String cLocationNbr;         // 83-90
    private String cOtisNbr;             // 91-103
    private String cSectionCode;         // 104-105
    private String cRefundType;          // 106-108
    private String cControlDate;         // 109-118
    private String cControlNbr;          // 119-122
    private String cStatus;              // 123-128
    private String cStatusDate;          // 129-138
    private String cEobInd;              // 139
    private String cReceiptType;         // 140-141
    private String cRemittorName;        // 142-177
    private String cRemittorTitle;       // 178-181
    private String cRemittorType;        // 182
    private String cClaimType;           // 183-186
    private String cOplInd;              // 187
    private String cLetterDate;          // 188-197
    private String cReasonCode;          // 198-201
    private String cOtherCorr;           // 202-222
    private String cComments;            // 223-287
    private String cPatientFname;        // 288-298
    private String cPatientLname;        // 299-313
    private String cAddr1;               // 314-349
    private String cAddr2;               // 350-385
    private String cCity;                // 386-400
    private String cState;               // 401-402
    private String cZip;                 // 403-412
    private String cCheckDate;           // 413-422
    private String cCheckNbr;            // 423-430
    private String cCheckAmount;         // 431-445
    private String cControlledAmount;    // 446-460
    private String cLocationCode;        // 461-467
}
