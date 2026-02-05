package com.abcbs.crrs.jobs.P09305;

import java.math.BigDecimal;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.abcbs.crrs.config.P09305Config;
import com.abcbs.crrs.projections.P09305ActivityView;

@Component
public class P09305Processor implements ItemProcessor<P09305ActivityView, P09305OutputRecord> {

    private String lastClerk=null, lastDate=null, lastNbr=null, lastType=null;
    private int checkpointCounter=0;
    private static final Logger log = LogManager.getLogger(P09305Config.class);

    @Override
    public P09305OutputRecord process(P09305ActivityView v) {
        P09305OutputRecord r = new P09305OutputRecord();

        // control fields
        String clerk = v.getActUserId();
        String cdate = v.getCrCntrlDate() == null ? "" : v.getCrCntrlDate().toString();
        String cnbr  = v.getCrCntrlNbr();
        String rtype = v.getCrRefundType();

        boolean sameKey =
                Objects.equals(clerk, lastClerk) &&
                Objects.equals(cdate, lastDate) &&
                Objects.equals(cnbr, lastNbr) &&
                Objects.equals(rtype, lastType);

        r.setClerkId(clerk);
        
        r.setCrCntrlDate(sameKey ? null : v.getCrCntrlDate());
        r.setCrCntrlNbr(sameKey ? ""   : cnbr);
        r.setCrRefundType(sameKey ? "" : rtype);
        r.setCrCntrldAmt(sameKey ? null : v.getCrCntrldAmt());
        

        // details
        r.setActActivity(v.getActActivity());
        r.setActActivityDate(v.getActActivityDate());
        r.setActTimestamp(v.getActTimestamp());
        r.setCrCheckNbr(v.getCrCheckNbr());
        r.setCrCheckAmt(v.getCrCheckAmt());
        r.setCrReceiptType(v.getCrReceiptType());
        r.setCrClaimType(v.getCrClaimType());
        r.setCrPatientLname(v.getCrPatientLname());
        r.setCrPatientFname(v.getCrPatientFname());
        r.setCrRemittorName(v.getCrRemittorName());
        r.setCrMbrIdNbr(v.getCrMbrIdNbr());
        r.setCrReasonCode(v.getCrReasonCode());
        r.setCrGlAcctNbr(v.getCrGlAcctNbr());
        r.setCorp(v.getCrCorp());
        r.setActXrefNumber(v.getCrXrefNbr());
        r.setActXrefDate(v.getActXrefDate());

        // checkpoint counter (writer uses frequency from ExecutionContext)
        checkpointCounter++;
        r.setCheckpointCounter(checkpointCounter);

        // move last key ONLY if we printed visible control values
        lastClerk = clerk;
        lastDate  = cdate;
        lastNbr   = cnbr;
        lastType  = rtype;

        // numeric safety
        if (r.getCrCntrldAmt() == null) r.setCrCntrldAmt(BigDecimal.ZERO);

        //System.out.println("P09305 Output Record -> {} rows match filters"+ r);
        
        return r;
    }
}
