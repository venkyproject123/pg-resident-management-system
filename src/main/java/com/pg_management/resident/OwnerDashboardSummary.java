package com.pg_management.resident;

import java.math.BigDecimal;
import java.util.List;

public record OwnerDashboardSummary(
		long totalResidents,
		long activeResidents,
		long vacatedResidents,
		long totalRooms,
		long totalBeds,
		long occupiedBeds,
		long availableBeds,
		BigDecimal pendingRentAmount,
		BigDecimal monthlyRentCollection,
		String firstPaymentPeriod,
		List<RoomResponse> rooms) {
}
