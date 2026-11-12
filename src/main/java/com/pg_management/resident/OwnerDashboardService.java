package com.pg_management.resident;

import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pg_management.security.OwnerAccount;

@Service
@Transactional(readOnly = true)
public class OwnerDashboardService {

	private final ResidentRepository residentRepository;
	private final RoomService roomService;
	private final RentPaymentService rentPaymentService;

	public OwnerDashboardService(
			ResidentRepository residentRepository,
			RoomService roomService,
			RentPaymentService rentPaymentService) {
		this.residentRepository = residentRepository;
		this.roomService = roomService;
		this.rentPaymentService = rentPaymentService;
	}

	public OwnerDashboardSummary findSummary(OwnerAccount owner, int year, int month) {
		long totalResidents = residentRepository.countByOwner_Id(owner.getId());
		long activeResidents = residentRepository.countByOwner_IdAndStatus(owner.getId(), ResidentStatus.ACTIVE);
		long vacatedResidents = residentRepository.countByOwner_IdAndStatus(owner.getId(), ResidentStatus.VACATED);
		List<RoomResponse> rooms = roomService.findAll(owner);
		long totalBeds = rooms.stream().map(RoomResponse::sharing)
				.filter(sharing -> sharing != null)
				.mapToLong(Integer::longValue)
				.sum();
		long occupiedBeds = rooms.stream().mapToLong(RoomResponse::occupied).sum();
		long availableBeds = rooms.stream().mapToLong(RoomResponse::availableBeds).sum();
		RentPaymentService.RentTotals rentTotals = rentPaymentService.findMonthlyTotals(owner, year, month);
		String firstPaymentPeriod = residentRepository.findEarliestJoiningDateByOwnerId(owner.getId())
				.map(date -> YearMonth.from(date).toString())
				.orElse(null);

		return new OwnerDashboardSummary(
				totalResidents,
				activeResidents,
				vacatedResidents,
				rooms.size(),
				totalBeds,
				occupiedBeds,
				availableBeds,
				rentTotals.pendingAmount(),
				rentTotals.collectedAmount(),
				firstPaymentPeriod,
				rooms);
	}
}
