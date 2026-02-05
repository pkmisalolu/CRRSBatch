package com.abcbs.crrs.jobs.P09352;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09352InputVoucher {

    // WS-LAST-VOUCHER-NBR-PREFIX  PIC X(01)
    private String inputLastVoucherNbrPrefix;

    // WS-LAST-VOUCHER-NBR-SUFFIX  PIC 9(05)
    private int inputLastVoucherNbrSuffix;

    // WS-LAST-VOUCHER-NBR-FILLER  PIC X(74)
    private String inputLastVoucherNbrFiller;
}