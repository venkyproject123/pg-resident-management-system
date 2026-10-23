package com.pg_management.resident;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pg_management.security.OwnerAccount;
import com.pg_management.security.OwnerAccountRepository;
import com.pg_management.whatsapp.WhatsAppCloudApiClient;

@ExtendWith(MockitoExtension.class)
class RentReminderSchedulerTests {

	@Mock
	private OwnerAccountRepository ownerAccountRepository;
	@Mock
	private ResidentRepository residentRepository;
	@Mock
	private RentPaymentRepository rentPaymentRepository;
	@Mock
	private WhatsAppCloudApiClient whatsAppClient;

	@Test
	void sendsReminderOnResidentsJoiningDateInsteadOfOwnerDueDay() {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
		YearMonth period = YearMonth.from(today);
		OwnerAccount owner = new OwnerAccount("owner", "hash", "Sample PG", "+919876543210");
		Resident resident = new Resident(owner, "Resident", "Father", "9876543210", "1",
				BigDecimal.valueOf(5000), BigDecimal.ZERO, today, "Address", "Company");
		when(ownerAccountRepository.findAll()).thenReturn(List.of(owner));
		when(residentRepository.findByOwner_IdAndStatusOrderByRoomNumberAscNameAsc(
				isNull(), eq(ResidentStatus.ACTIVE))).thenReturn(List.of(resident));
		when(rentPaymentRepository.findByResidentAndPeriodYearAndPeriodMonth(
				resident, period.getYear(), period.getMonthValue())).thenReturn(Optional.empty());

		new RentReminderScheduler(ownerAccountRepository, residentRepository, rentPaymentRepository, whatsAppClient)
				.sendDueDateReminders();

		verify(whatsAppClient).sendRentReminder("9876543210", "Sample PG", "Resident",
				period.getMonth().toString() + " " + period.getYear(), "5000");
		verify(rentPaymentRepository).save(any(RentPayment.class));
	}
}
