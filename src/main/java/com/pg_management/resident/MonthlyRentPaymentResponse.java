package com.pg_management.resident;

import java.math.BigDecimal;
import java.util.List;

public record MonthlyRentPaymentResponse(
		int year,
		int month,
		BigDecimal totalRent,
		BigDecimal totalCollected,
		BigDecimal totalRemaining,
		List<ResidentPayment> residents) {

	public record ResidentPayment(
			Long residentId,
			String residentName,
			String roomNumber,
			Integer sharing,
			String mobileNumber,
			BigDecimal rent,
			BigDecimal collected,
			BigDecimal remaining,
			java.time.LocalDate joiningDate) {

		public ResidentPayment(
				Long residentId,
				String residentName,
				String roomNumber,
				Integer sharing,
				String mobileNumber,
				BigDecimal rent,
				BigDecimal collected,
				BigDecimal remaining) {
			this(residentId, residentName, roomNumber, sharing, mobileNumber, rent, collected, remaining, null);
		}
	}
}
