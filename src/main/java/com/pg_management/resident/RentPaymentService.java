package com.pg_management.resident;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.pg_management.resident.dto.RentPaymentRequest;
import com.pg_management.resident.dto.RentPaymentResponse;
import com.pg_management.security.OwnerAccount;

@Service
@Transactional
public class RentPaymentService {

	private final ResidentRepository residentRepository;
	private final RentPaymentRepository rentPaymentRepository;

	public RentPaymentService(
			ResidentRepository residentRepository,
			RentPaymentRepository rentPaymentRepository) {
		this.residentRepository = residentRepository;
		this.rentPaymentRepository = rentPaymentRepository;
	}

	@Transactional(readOnly = true)
	public List<RentPaymentResponse> findYear(OwnerAccount owner, Long residentId, int year) {
		Resident resident = getResident(owner, residentId);
		return rentPaymentRepository.findAllByResidentAndPeriodYearOrderByPeriodMonthAsc(resident, year)
				.stream()
				.filter(payment -> !LocalDate.of(year, payment.getPeriodMonth(), 1)
						.isBefore(resident.getJoiningDate().withDayOfMonth(1)))
				.map(RentPaymentResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<PendingRentPaymentResponse> findPendingThroughToday(OwnerAccount owner) {
		LocalDate today = LocalDate.now();
		List<Resident> residents = residentRepository.findAllByOwner_IdOrderByRoomNumberAscNameAsc(owner.getId());
		if (residents.isEmpty()) {
			return List.of();
		}

		int firstYear = residents.stream()
				.map(Resident::getJoiningDate)
				.mapToInt(LocalDate::getYear)
				.min()
				.orElse(today.getYear());
		Set<PaymentPeriod> paidPeriods = new HashSet<>();
		for (RentPayment payment : rentPaymentRepository.findAllByResident_Owner_IdAndPeriodYearBetween(
				owner.getId(), firstYear, today.getYear())) {
			if (payment.isPaid()) {
				paidPeriods.add(new PaymentPeriod(payment.getResident().getId(),
						payment.getPeriodYear(), payment.getPeriodMonth()));
			}
		}

		List<PendingRentPaymentResponse> pending = new ArrayList<>();
		for (Resident resident : residents) {
			LocalDate cutoff = resident.getStatus() == ResidentStatus.VACATED && resident.getVacatedDate() != null
					? resident.getVacatedDate().isBefore(today) ? resident.getVacatedDate() : today
					: today;
			LocalDate joiningDate = resident.getJoiningDate();
			if (joiningDate.isAfter(cutoff)) {
				continue;
			}

			YearMonth period = YearMonth.from(joiningDate);
			YearMonth lastPeriod = YearMonth.from(cutoff);
			while (!period.isAfter(lastPeriod)) {
				LocalDate rentDate = period.atDay(Math.min(joiningDate.getDayOfMonth(), period.lengthOfMonth()));
				if (!rentDate.isAfter(cutoff)
						&& !paidPeriods.contains(new PaymentPeriod(resident.getId(), period.getYear(), period.getMonthValue()))) {
					pending.add(new PendingRentPaymentResponse(
							resident.getName(),
							resident.getRoomNumber(),
							resident.getSharing(),
							rentDate,
							resident.getMobileNumber()));
				}
				period = period.plusMonths(1);
			}
		}
		return pending.stream()
				.sorted(java.util.Comparator.comparing(PendingRentPaymentResponse::rentDate)
						.thenComparing(PendingRentPaymentResponse::roomNumber, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(PendingRentPaymentResponse::residentName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public RentPaymentResponse recordPaid(
			OwnerAccount owner,
			Long residentId,
			int year,
			int month,
			RentPaymentRequest request) {
		Resident resident = getResident(owner, residentId);
		if (LocalDate.of(year, month, 1).isBefore(resident.getJoiningDate().withDayOfMonth(1))) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "Rent cannot be recorded before the resident's joining month");
		}
		RentPayment payment = rentPaymentRepository.findByResidentAndPeriodYearAndPeriodMonth(resident, year, month)
				.orElseGet(() -> new RentPayment(resident, year, month));
		boolean wasAlreadyPaid = payment.isPaid();
		payment.markPaid(request.amount(), request.paidDate(), request.method());
		rentPaymentRepository.saveAndFlush(payment);

		return RentPaymentResponse.from(payment, wasAlreadyPaid);
	}

	private Resident getResident(OwnerAccount owner, Long residentId) {
		return residentRepository.findByIdAndOwner_Id(residentId, owner.getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resident not found"));
	}

	private record PaymentPeriod(Long residentId, int year, int month) {
	}

}
