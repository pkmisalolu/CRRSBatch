package com.abcbs.crrs.jobs.P09352;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09352OutputVoucher implements Serializable {
    private static final long serialVersionUID = 1L;

    // WS-LAST-VOUCHER-NBR-PREFIX  PIC X(01)
    private String outputLastVoucherNbrPrefix;

    // WS-LAST-VOUCHER-NBR-SUFFIX  PIC 9(05)
    private String outputLastVoucherNbrSuffix;

    // WS-LAST-VOUCHER-NBR-FILLER  PIC X(74)
    private String outputLastVoucherNbrFiller;
}