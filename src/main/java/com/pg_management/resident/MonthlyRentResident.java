package com.pg_management.resident;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MonthlyRentResident(
		Long residentId,
		String name,
		String roomNumber,
		Integer sharing,
		String mobileNumber,
		BigDecimal rent,
		LocalDate joiningDate) {
}
