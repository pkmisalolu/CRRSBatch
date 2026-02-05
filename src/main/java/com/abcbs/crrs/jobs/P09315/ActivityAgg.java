package com.abcbs.crrs.jobs.P09315;

import java.math.BigDecimal;

public record ActivityAgg(String refundType, String activity, long count, BigDecimal amount) {
}
