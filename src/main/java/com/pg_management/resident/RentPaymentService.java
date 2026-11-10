package com.pg_management.resident;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	public MonthlyRentPaymentResponse findMonthlyPayments(OwnerAccount owner, int year, int month) {
		LocalDate today = LocalDate.now();
		YearMonth selectedMonth = YearMonth.of(year, month);
		LocalDate monthStart = selectedMonth.atDay(1);
		LocalDate monthEnd = selectedMonth.atEndOfMonth().isAfter(today) ? today : selectedMonth.atEndOfMonth();
		List<Resident> residents = residentRepository.findAllByOwner_IdOrderByRoomNumberAscNameAsc(owner.getId());
		Map<Long, BigDecimal> collectedByResident = new HashMap<>();
		for (RentPayment payment : rentPaymentRepository.findAllByResident_Owner_IdAndPeriodYearAndPeriodMonth(
				owner.getId(), selectedMonth.getYear(), selectedMonth.getMonthValue())) {
			if (payment.isPaid()) {
				collectedByResident.put(payment.getResident().getId(),
						payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount());
			}
		}

		BigDecimal totalRent = BigDecimal.ZERO;
		BigDecimal totalCollected = BigDecimal.ZERO;
		BigDecimal totalRemaining = BigDecimal.ZERO;
		List<MonthlyRentPaymentResponse.ResidentPayment> monthlyPayments = residents.stream()
				.filter(resident -> !resident.getJoiningDate().isAfter(monthEnd))
				.filter(resident -> resident.getStatus() != ResidentStatus.VACATED
						|| resident.getVacatedDate() == null
								|| !resident.getVacatedDate().isBefore(monthStart))
				.map(resident -> {
					BigDecimal rent = resident.getRent();
					BigDecimal collected = collectedByResident.getOrDefault(resident.getId(), BigDecimal.ZERO);
					BigDecimal remaining = rent.subtract(collected).max(BigDecimal.ZERO);
					return new MonthlyRentPaymentResponse.ResidentPayment(
							resident.getId(),
							resident.getName(),
							resident.getRoomNumber(),
							resident.getSharing(),
							resident.getMobileNumber(),
							rent,
							collected,
							remaining);
				})
				.toList();
		for (MonthlyRentPaymentResponse.ResidentPayment payment : monthlyPayments) {
			totalRent = totalRent.add(payment.rent());
			totalCollected = totalCollected.add(payment.collected());
			totalRemaining = totalRemaining.add(payment.remaining());
		}
		return new MonthlyRentPaymentResponse(
				selectedMonth.getYear(),
				selectedMonth.getMonthValue(),
				totalRent,
				totalCollected,
				totalRemaining,
				monthlyPayments);
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

}
