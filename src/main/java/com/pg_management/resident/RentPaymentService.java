package com.pg_management.resident;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.pg_management.resident.dto.RentPaymentRequest;
import com.pg_management.resident.dto.RentPaymentResponse;
import com.pg_management.security.OwnerAccount;
import com.pg_management.whatsapp.WhatsAppCloudApiClient;

@Service
@Transactional
public class RentPaymentService {

	private static final Logger LOGGER = LoggerFactory.getLogger(RentPaymentService.class);

	private final ResidentRepository residentRepository;
	private final RentPaymentRepository rentPaymentRepository;
	private final WhatsAppCloudApiClient whatsAppClient;

	public RentPaymentService(
			ResidentRepository residentRepository,
			RentPaymentRepository rentPaymentRepository,
			WhatsAppCloudApiClient whatsAppClient) {
		this.residentRepository = residentRepository;
		this.rentPaymentRepository = rentPaymentRepository;
		this.whatsAppClient = whatsAppClient;
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

		boolean notificationSent = false;
		if (wasAlreadyPaid) {
			return RentPaymentResponse.from(payment, false, true);
		} else if (resident.getMobileNumber() == null || resident.getMobileNumber().isBlank()) {
			LOGGER.warn("Rent payment recorded but WhatsApp confirmation skipped for resident id {}: no mobile number",
					resident.getId());
		} else {
			try {
				whatsAppClient.sendRentPaid(
						resident.getMobileNumber(),
						owner.getPgName(),
						resident.getName(),
						monthName(year, month),
						request.amount().toPlainString(),
						request.method().name().replace('_', ' '));
				notificationSent = true;
			} catch (RestClientException | IllegalStateException | ResponseStatusException exception) {
				LOGGER.error("Rent payment recorded but WhatsApp confirmation failed for resident id {}",
						resident.getId(), exception);
			}
		}
		return RentPaymentResponse.from(payment, notificationSent, false);
	}

	private Resident getResident(OwnerAccount owner, Long residentId) {
		return residentRepository.findByIdAndOwner_Id(residentId, owner.getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resident not found"));
	}

	private String monthName(int year, int month) {
		return LocalDate.of(year, month, 1).getMonth().getDisplayName(
				java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + year;
	}
}
