package com.abcbs.crrs.jobs.P09315;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * Projection for aggregated activity totals. Used by fetchEstablished().
 */
public interface ActivityAggView {

	String getRefundType();

	String getActivity();

	Date cntrlDate();

	String cntrlNbr();

	long getCount();

	BigDecimal getAmount();

	String crCorp();

	public static final ActivityAggView ZERO = new ActivityAggView() {

		@Override
		public long getCount() {
			return 0L;
		}

		@Override
		public BigDecimal getAmount() {
			return BigDecimal.ZERO;
		}

		@Override
		public String getActivity() {
			return "";
		}

		@Override
		public String getRefundType() {
			return "";
		}

		@Override
		public String cntrlNbr() {
			return "";
		}

		@Override
		public String crCorp() {
			return "";
		}

		@Override
		public Date cntrlDate() {
			// TODO Auto-generated method stub
			return new Date(0);
		}

	};

}