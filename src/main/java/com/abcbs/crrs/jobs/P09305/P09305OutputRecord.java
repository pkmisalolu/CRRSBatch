package com.abcbs.crrs.jobs.P09305;
// package com.abcbs.crrs.jobs.P09305;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class P09305OutputRecord {
    // control/display
    private String clerkId;
    private LocalDate crCntrlDate;
    private String crCntrlNbr;
    private String crRefundType;
    private BigDecimal crCntrldAmt;

    // detail
    private String actActivity;
    private LocalDate actActivityDate;
    private LocalDateTime actTimestamp;
    private BigDecimal activityAmount;
    private BigDecimal workingBalance;
    private BigDecimal cashReceiptsBalance;

    private String crCheckNbr;
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
    
    
    
    private String actXrefNumber;
    private LocalDate actXrefDate;

    // for checkpointing frequency
    private int checkpointCounter;
}
