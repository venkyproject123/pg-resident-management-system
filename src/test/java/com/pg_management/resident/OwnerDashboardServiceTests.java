package com.pg_management.resident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pg_management.security.OwnerAccount;

@ExtendWith(MockitoExtension.class)
class OwnerDashboardServiceTests {

	@Mock
	private ResidentRepository residentRepository;
	@Mock
	private RoomService roomService;
	@Mock
	private RentPaymentService rentPaymentService;

	@Test
	void dashboardSummaryUsesOwnerScopedResidentRoomAndRentData() {
		OwnerAccount owner = mock(OwnerAccount.class);
		when(owner.getId()).thenReturn(4L);
		when(residentRepository.countByOwner_Id(4L)).thenReturn(10L);
		when(residentRepository.countByOwner_IdAndStatus(4L, ResidentStatus.ACTIVE)).thenReturn(8L);
		when(residentRepository.countByOwner_IdAndStatus(4L, ResidentStatus.VACATED)).thenReturn(2L);
		when(roomService.findAll(owner)).thenReturn(List.of(
				new RoomResponse("101", 3, 2, 2, 1, true),
				new RoomResponse("102", 2, 1, 1, 1, true)));
		when(rentPaymentService.findMonthlyTotals(owner, 2026, 11))
				.thenReturn(new RentPaymentService.RentTotals(new BigDecimal("4000.00"), new BigDecimal("8000.00")));
		when(residentRepository.findEarliestJoiningDateByOwnerId(4L))
				.thenReturn(Optional.of(LocalDate.of(2026, 1, 15)));

		OwnerDashboardSummary summary = new OwnerDashboardService(
				residentRepository, roomService, rentPaymentService).findSummary(owner, 2026, 11);

		assertEquals(10, summary.totalResidents());
		assertEquals(8, summary.activeResidents());
		assertEquals(2, summary.vacatedResidents());
		assertEquals(2, summary.totalRooms());
		assertEquals(5, summary.totalBeds());
		assertEquals(3, summary.occupiedBeds());
		assertEquals(2, summary.availableBeds());
		assertEquals(new BigDecimal("4000.00"), summary.pendingRentAmount());
		assertEquals(new BigDecimal("8000.00"), summary.monthlyRentCollection());
		assertEquals("2026-01", summary.firstPaymentPeriod());
	}
}
