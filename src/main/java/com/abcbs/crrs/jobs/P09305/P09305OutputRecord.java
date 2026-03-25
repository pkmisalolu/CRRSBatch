package com.abcbs.crrs.jobs.P09305;
// package com.abcbs.crrs.jobs.P09305;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class P09305OutputRecord {
    // control/display
    private String actUserId;
    private String actActivity;
    private LocalDate crCntrlDate;
    private String crCntrlNbr;
    private String crRefundType;
    private LocalDate actActivityDate;
    private LocalDateTime actTimestamp;
    
    
    private String crXrefNbr;
    private LocalDate actXrefDate;
    
    
    private BigDecimal crCntrldAmt;  
    private String crCheckNbr;
    
    
    
    
    private BigDecimal actActivityAmt;
    private BigDecimal actWorkingBal;
    private BigDecimal crCheckAmt;
    private String crReceiptType;
    private String crClaimType;
    private String crPatientLname;
    private String crPatientFname;
    private String crRemittorName;
    private String crMbrIdNbr;
    private String crReasonCode;
    private String crGlAcctNbr;
    private String corp;

    private BigDecimal crReceiptBal;

    // for checkpointing frequency
    private int checkpointCounter;
}
