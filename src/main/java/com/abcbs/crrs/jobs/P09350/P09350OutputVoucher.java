/**
* @author Chandrakanth Gangalam
* Created on Feb 16, 2026
*/

package com.abcbs.crrs.jobs.P09350;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09350OutputVoucher implements Serializable {
    private static final long serialVersionUID = 1L;

    // WS-LAST-VOUCHER-NBR-PREFIX  PIC X(01)
    private String outputLastVoucherNbrPrefix;

    // WS-LAST-VOUCHER-NBR-SUFFIX  PIC 9(05)
    private String outputLastVoucherNbrSuffix;

    // WS-LAST-VOUCHER-NBR-FILLER  PIC X(74)
    private String outputLastVoucherNbrFiller;
}
