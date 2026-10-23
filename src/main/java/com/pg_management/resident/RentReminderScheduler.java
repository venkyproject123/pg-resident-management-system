package com.pg_management.resident;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.pg_management.security.OwnerAccount;
import com.pg_management.security.OwnerAccountRepository;
import com.pg_management.whatsapp.WhatsAppCloudApiClient;

import jakarta.transaction.Transactional;

@Component
public class RentReminderScheduler {

	private static final Logger LOGGER = LoggerFactory.getLogger(RentReminderScheduler.class);

	private final OwnerAccountRepository ownerAccountRepository;
	private final ResidentRepository residentRepository;
	private final RentPaymentRepository rentPaymentRepository;
	private final WhatsAppCloudApiClient whatsAppClient;

	public RentReminderScheduler(
			OwnerAccountRepository ownerAccountRepository,
			ResidentRepository residentRepository,
			RentPaymentRepository rentPaymentRepository,
			WhatsAppCloudApiClient whatsAppClient) {
		this.ownerAccountRepository = ownerAccountRepository;
		this.residentRepository = residentRepository;
		this.rentPaymentRepository = rentPaymentRepository;
		this.whatsAppClient = whatsAppClient;
	}

	@Scheduled(cron = "${app.rent-reminders.cron:0 0 9 * * *}", zone = "${app.rent-reminders.zone:Asia/Kolkata}")
	@Transactional
	public void sendDueDateReminders() {
		LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
		YearMonth period = YearMonth.from(today);
		for (OwnerAccount owner : ownerAccountRepository.findAll()) {
			for (Resident resident : residentRepository.findByOwner_IdAndStatusOrderByRoomNumberAscNameAsc(
					owner.getId(), ResidentStatus.ACTIVE)) {
				int dueDay = Math.min(resident.getJoiningDate().getDayOfMonth(), period.lengthOfMonth());
				if (today.isBefore(resident.getJoiningDate()) || today.getDayOfMonth() != dueDay) {
					continue;
				}
				sendReminderIfUnpaid(owner, resident, period);
			}
		}
	}

	private void sendReminderIfUnpaid(OwnerAccount owner, Resident resident, YearMonth period) {
		RentPayment payment = rentPaymentRepository
				.findByResidentAndPeriodYearAndPeriodMonth(resident, period.getYear(), period.getMonthValue())
				.orElseGet(() -> new RentPayment(resident, period.getYear(), period.getMonthValue()));
		if (payment.isPaid() || payment.getReminderSentAt() != null) {
			return;
		}
		if (resident.getMobileNumber() == null || resident.getMobileNumber().isBlank()) {
			LOGGER.warn("Rent reminder not sent for resident id {}: no mobile number", resident.getId());
			return;
		}
		try {
			whatsAppClient.sendRentReminder(
					resident.getMobileNumber(),
					owner.getPgName(),
					resident.getName(),
					period.getMonth().toString() + " " + period.getYear(),
					resident.getRent().toPlainString());
			payment.setReminderSentAt(Instant.now());
			rentPaymentRepository.save(payment);
		} catch (RestClientException | IllegalStateException | ResponseStatusException exception) {
			LOGGER.error("WhatsApp rent reminder failed for resident id {}", resident.getId(), exception);
		}
	}
}
