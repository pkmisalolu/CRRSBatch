package com.abcbs.crrs.jobs.P09340;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09340GlRecord {
	
	private String p09dedsJulian;
    private String p09dedsHhmmss;      
	private String p09deds29;
	private String glP09dedsId;
    private String glRefundType;
    private String glControlDate;
    private String glControlNbr;
    private String glReceiptType;
    private String glBankAcctNbr;
    private String glAcctNbr;
    private String glActCode;
    private String glActAmt;
    private String glActDate;
    private String glReportMo;
    private String glReportYr;
    private String glPatientLn;
    private String glPatientFn;
    private String glMbrIdNbr;
    private String glXrefType;
    private String glXrefClaimNbr;
    private String glXrefDate;
    private String glCashRecBal;
    private String glCorp;
    
    //report Dt
    private String reportDt;
    private String reportMo;
    private String reportYr;
    
    private String fileSelect;
    private String outputType;
    
    private String transCode;
//    private String ftssAmt;
//    private String ftssAcct;
//    private String ftssSubAcct;
//    private String ftssCc;
//    private String ftssLob;
//    private String ftssCompany;
//    private String ftssPaidBy;
//    private String ftssNn;
//    private String ftssActMonth;
//    private String ftssActDay;
//    private String ftssAcctYr;
//    private String ftssSrcCode;
//    private String ftssDesc;
//    private String ftssDocId;
//    private String ftssOffsetId;
//    private String ftssDiFlag;
//    private String ftssMethOfAlloc;
//    private String ftssProject;
//    private String ftssTask;
//    private String ftssSubTask;
//    private String ftssFn;
//    private String ftssTransNbr;
//    private String ftssAdjMon;
//    private String ftssAdjYr;
//    private String ftssOp;
//    private String ftssPers;
//    private String ftssHours;
//    private String ftssActionCode;
    
    //corp codes
    private String corpCode;
    private String corpNo;
    
    //control file
    private String runFrequency;
    private String refundType;
//    private String select;

    
    
    

}
