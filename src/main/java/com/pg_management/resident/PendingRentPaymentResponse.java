package com.pg_management.resident;

import java.time.LocalDate;

public record PendingRentPaymentResponse(
		String residentName,
		String roomNumber,
		Integer sharing,
		LocalDate rentDate,
		String mobileNumber) {
}
