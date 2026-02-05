package com.abcbs.crrs.jobs.P09315;



import java.math.BigDecimal;
import java.util.List;

/**
 * Projection for aggregated activity totals.
 * Used by fetchEstablished().
 */
public interface ActivityAggView {

    String getRefundType();

    String getActivity();
  
    long getCount();
    
    BigDecimal getAmount();
    
    
    public static final ActivityAggView ZERO = new ActivityAggView() {
        @Override public long getCount() { return 0L; }
        @Override public BigDecimal getAmount() { return BigDecimal.ZERO; }
        @Override public String getActivity() { return ""; }
        @Override public String getRefundType() { return ""; }
    };
    
 
}