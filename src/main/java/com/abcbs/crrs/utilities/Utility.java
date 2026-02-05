package com.abcbs.crrs.utilities;

import java.math.BigDecimal;

public class Utility {
	public static String ccFromYy(String yy) {
		int y = 0;
		try {
			y = Integer.parseInt(yy);
		} catch (Exception ignore) {
		}
		return (y < 60) ? "20" : "19";
	}

	public static String safe(String s) {
		return (s == null) ? "" : s;
	}

	public static String fixLen(String s, int len) {
		if (s == null)
			s = "";
		if (s.length() == len)
			return s;
		if (s.length() > len)
			return s.substring(0, len);
		StringBuilder b = new StringBuilder(len).append(s);
		while (b.length() < len)
			b.append(' ');
		return b.toString();
	}

	public static String fixLenLeft(String s, int len) {
		if (s == null)
			s = "";
		if (s.length() == len)
			return s;
		if (s.length() > len)
			return s.substring(0, len);
		while (s.length() < len)
			s = ' ' + s;
		return s;
	}

	public static String leftPadDigits(String s, int len) {
		if (s == null)
			s = "";
		s = s.replaceAll("\\D", "");
		if (s.length() > len)
			return s.substring(s.length() - len);
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < len - s.length(); i++)
			b.append('0');
		return b.append(s).toString();
	}

	public static String zeroPadDigits(String s, int len) {
		return leftPadDigits(s, len);
	}

	public static boolean isZeroAmount(String amt) {
		if (amt == null)
			return true;
		String t = amt.trim().replaceAll("\\D", "");
		return t.isEmpty() || t.chars().allMatch(c -> c == '0');
	}
	
	  /** Unpack COMP-3 signed numeric of given total digits and scale (implied decimals). */
    public static BigDecimal unpack(byte[] src, int offset, int byteLen, int totalDigits, int scale) {
        StringBuilder digits = new StringBuilder(totalDigits + 1);
        for (int i = 0; i < byteLen; i++) {
            int b = src[offset + i] & 0xFF;
            int hi = (b >> 4) & 0x0F;
            int lo = b & 0x0F;

            if (i < byteLen - 1) {
                digits.append(hi).append(lo);
            } else {
                // last byte hi is digit; low nibble is sign (C/F pos, D neg)
                digits.append(hi);
                boolean negative = (lo == 0x0D);
                BigDecimal v = new BigDecimal(digits.toString()).movePointLeft(scale);
                return negative ? v.negate() : v;
            }
        }
        return BigDecimal.ZERO;
    }

    /** Pack BigDecimal into COMP-3 (used only if you ever need to write packed). */
    public static void pack(BigDecimal value, byte[] dest, int offset, int byteLen, int totalDigits, int scale) {
        if (value == null) value = BigDecimal.ZERO;
        boolean negative = value.signum() < 0;
        String digits = value.abs().setScale(scale).movePointRight(scale).toPlainString();

        // Left pad or trim to totalDigits
        if (digits.length() > totalDigits) {
            digits = digits.substring(digits.length() - totalDigits);
        } else {
            digits = "0".repeat(totalDigits - digits.length()) + digits;
        }

        int di = 0;
        for (int i = 0; i < byteLen - 1; i++) {
            int hi = digits.charAt(di++) - '0';
            int lo = digits.charAt(di++) - '0';
            dest[offset + i] = (byte)((hi << 4) | lo);
        }
        int last = digits.charAt(di++) - '0';
        int signNibble = negative ? 0x0D : 0x0C;
        dest[offset + byteLen - 1] = (byte)((last << 4) | signNibble);
    }
    
    public static String safeTrimUpper(String val) {
        return (val == null) ? "" : val.trim().toUpperCase();
    }


}
