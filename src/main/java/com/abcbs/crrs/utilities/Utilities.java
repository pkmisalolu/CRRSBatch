package com.abcbs.crrs.utilities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Utilities {

	//checking each character in a string is numeric or not, if it numeric returns true
	public static boolean onlyDigits(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (!Character.isDigit(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	//converting String date to LocalDate
	public static LocalDate parseDate(String dateStr) {
		int month = Integer.parseInt(dateStr.substring(0, 2));
		int day = Integer.parseInt(dateStr.substring(2, 4));
		int yearSuffix = Integer.parseInt(dateStr.substring(4, 6));

		int year;
		if (yearSuffix < 60) {
			year = 2000 + yearSuffix;
		} else {
			year = 1900 + yearSuffix;
		}

		return LocalDate.of(year, month, day);
	}

	
	
	public static BigDecimal convertStringToBigDecimal(String input) {
        // First convert string to BigDecimal as is
        BigDecimal value = new BigDecimal(input);

        BigDecimal scaledValue = value.movePointLeft(2);

        return scaledValue;
    }
	
	public static String spaceIfBlank(String s) {
	    return (s == null || s.isBlank()) ? " " : s;
	}

	public static String julianDate() {
	    LocalDate today = LocalDate.now();
	    String year = String.format("%02d", today.getYear() % 100); // Last 2 digits of year
	    String dayOfYear = String.format("%03d", today.getDayOfYear()); // Day of year (001-366)
	    return year + dayOfYear;
	}
	
	public static String julianTime() {
	    LocalTime now = LocalTime.now();
	    String base = now.format(DateTimeFormatter.ofPattern("HHmmss")); // HHMMSS
	    int tenth = now.getNano() / 100_000_000; // 0–9 (tenth of second)
	    return base + tenth;
	}


}
