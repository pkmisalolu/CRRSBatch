package com.abcbs.crrs.jobs.P09360;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Auto-generated from COBOL P09-GL-DEDS-REC */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09GlDedsRecord {
    private String p09deds_julian;         // P09DEDS-JULIAN
    private String p09deds_hhmmsss;        // P09DEDS-HHMMSSS
    private String filler;                 // FILLER
    private String gl_p09deds_id;          // GL-P09DEDS-ID
    private String gl_refund_type;         // GL-REFUND-TYPE
    private String gl_control_date;        // GL-CONTROL-DATE
    private String gl_control_nbr;         // GL-CONTROL-NBR
    private String gl_receipt_type;        // GL-RECEIPT-TYPE
    private String gl_bank_acct_nbr;       // GL-BANK-ACCT-NBR
    private String gl_acct_nbr;            // GL-ACCT-NBR
    private String gl_act_code;            // GL-ACT-CODE
    private String gl_act_amt;             // GL-ACT-AMT
    private String gl_act_date;            // GL-ACT-DATE
    private String gl_report_mo;           // GL-REPORT-MO
    private String gl_report_yr;           // GL-REPORT-YR
    private String gl_patient_ln;          // GL-PATIENT-LN
    private String gl_patient_fn;          // GL-PATIENT-FN
    private String gl_mbr_id_nbr;          // GL-MBR-ID-NBR
    private String gl_xref_type;           // GL-XREF-TYPE
    private String gl_xref_claim_nbr;      // GL-XREF-CLAIM-NBR
    private String gl_xref_date;           // GL-XREF-DATE
    private String gl_cash_rec_bal;        // GL-CASH-REC-BAL
    private String gl_corp;                // GL-CORP
    private String gl_filler;              // GL-FILLER
}
