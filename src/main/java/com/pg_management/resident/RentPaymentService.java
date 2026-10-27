package com.pg_management.resident;

import java.time.LocalDate;
import java.util.List;

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
