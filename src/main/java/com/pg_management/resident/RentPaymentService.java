package com.pg_management.resident;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
		MonthlyRentData data = loadMonthlyRentData(owner, year, month);
		BigDecimal totalRent = BigDecimal.ZERO;
		BigDecimal totalCollected = BigDecimal.ZERO;
		BigDecimal totalRemaining = BigDecimal.ZERO;
		List<MonthlyRentPaymentResponse.ResidentPayment> monthlyPayments = data.residents().stream()
				.map(resident -> {
					BigDecimal rent = resident.rent();
					BigDecimal collected = data.collectedByResident()
							.getOrDefault(resident.residentId(), BigDecimal.ZERO);
					BigDecimal remaining = rent.subtract(collected).max(BigDecimal.ZERO);
					return new MonthlyRentPaymentResponse.ResidentPayment(
							resident.residentId(),
							resident.name(),
							resident.roomNumber(),
							resident.sharing(),
							resident.mobileNumber(),
							rent,
							collected,
							remaining,
							resident.joiningDate());
				})
				.toList();
		for (MonthlyRentPaymentResponse.ResidentPayment payment : monthlyPayments) {
			totalRent = totalRent.add(payment.rent());
			totalCollected = totalCollected.add(payment.collected());
			totalRemaining = totalRemaining.add(payment.remaining());
		}
		return new MonthlyRentPaymentResponse(
				year, month, totalRent, totalCollected, totalRemaining, monthlyPayments);
	}

	@Transactional(readOnly = true)
	public RentTotals findMonthlyTotals(OwnerAccount owner, int year, int month) {
		MonthlyRentData data = loadMonthlyRentData(owner, year, month);
		BigDecimal totalCollected = BigDecimal.ZERO;
		BigDecimal totalRemaining = BigDecimal.ZERO;
		for (MonthlyRentResident resident : data.residents()) {
			BigDecimal rent = resident.rent();
			BigDecimal collected = data.collectedByResident()
					.getOrDefault(resident.residentId(), BigDecimal.ZERO);
			totalCollected = totalCollected.add(collected);
			totalRemaining = totalRemaining.add(rent.subtract(collected).max(BigDecimal.ZERO));
		}
		return new RentTotals(totalRemaining, totalCollected);
	}

	private MonthlyRentData loadMonthlyRentData(OwnerAccount owner, int year, int month) {
		LocalDate today = LocalDate.now();
		YearMonth selectedMonth = YearMonth.of(year, month);
		LocalDate monthStart = selectedMonth.atDay(1);
		LocalDate monthEnd = selectedMonth.atEndOfMonth().isAfter(today) ? today : selectedMonth.atEndOfMonth();
		List<MonthlyRentResident> residents = residentRepository.findMonthlyRentResidents(
				owner.getId(), monthStart, monthEnd, ResidentStatus.VACATED);
		Map<Long, BigDecimal> collectedByResident = new HashMap<>();
		for (MonthlyRentPaymentAmount payment : rentPaymentRepository.findAmountsByOwnerAndPeriod(
				owner.getId(), selectedMonth.getYear(), selectedMonth.getMonthValue())) {
			if (payment.paid()) {
				collectedByResident.put(payment.residentId(),
						payment.amount() == null ? BigDecimal.ZERO : payment.amount());
			}
		}
		return new MonthlyRentData(residents, collectedByResident);
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

	public record RentTotals(BigDecimal pendingAmount, BigDecimal collectedAmount) {
	}

	private record MonthlyRentData(
			List<MonthlyRentResident> residents,
			Map<Long, BigDecimal> collectedByResident) {
	}
}
