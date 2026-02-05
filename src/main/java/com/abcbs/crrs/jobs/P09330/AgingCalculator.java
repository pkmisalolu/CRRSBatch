package com.abcbs.crrs.jobs.P09330;

import java.time.LocalDate;



public final class AgingCalculator {
    private AgingCalculator() {}

    public static AgeBucket bucketFor(LocalDate itemDate,
                                      LocalDate cntrlToDate,
                                      LocalDate ctdMinus15,
                                      LocalDate ctdMinus1Month,
                                      LocalDate ctdMinus2Months,
                                      LocalDate ctdMinus3Months,
                                      LocalDate ctdMinus4Months) {
        if (itemDate == null) return AgeBucket.OVER_4;
        // Note: exact inclusivity must follow COBOL; this is a best-effort mapping.
        if (!itemDate.isBefore(ctdMinus15)) return AgeBucket.D0_15;
        if (!itemDate.isBefore(ctdMinus1Month)) return AgeBucket.D16_1M;
        if (!itemDate.isBefore(ctdMinus2Months)) return AgeBucket.M1_2;
        if (!itemDate.isBefore(ctdMinus3Months)) return AgeBucket.M2_3;
        if (!itemDate.isBefore(ctdMinus4Months)) return AgeBucket.M3_4;
        return AgeBucket.OVER_4;
    }
}
